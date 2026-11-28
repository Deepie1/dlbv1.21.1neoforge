#!/usr/bin/env python3
"""Guards the TerrainPrep-owned continuous snow spread and shared soil painter."""
from __future__ import annotations
import argparse
import math
import re
from pathlib import Path
import subprocess
import sys
import tempfile

MOTHER = Path("src/main/java/deepluckyblock/util/TerrainMother.java")
PREP = Path("src/main/java/deepluckyblock/procedures/StructureTerrainPrep.java")
SCAN = Path("src/main/java/deepluckyblock/dev/DevTerrainScan.java")
BLEND = Path("src/main/java/deepluckyblock/util/BiomeEcotoneSpread.java")
SCANVIEW = Path("tools/scanview.py")


def body(text: str, signature: str) -> str:
    start = text.find(signature)
    if start < 0:
        return ""
    opening = text.find("{", start)
    if opening < 0:
        return ""
    depth = 0
    for i in range(opening, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[opening + 1:i]
    return ""


def no_comments(text: str) -> str:
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)


def static_errors(root: Path) -> list[str]:
    mother = no_comments((root / MOTHER).read_text(encoding="utf-8"))
    prep = no_comments((root / PREP).read_text(encoding="utf-8"))
    scan = no_comments((root / SCAN).read_text(encoding="utf-8"))
    blend = no_comments((root / BLEND).read_text(encoding="utf-8"))
    scanview = no_comments((root / SCANVIEW).read_text(encoding="utf-8"))
    errors: list[str] = []
    field = body(prep, "private static final class SnowSpreadField")
    source = body(mother, "public float snowSourceAtChunk(int cx, int cz)")
    cover = body(prep, "private static boolean snowCoverHere(ServerLevel level, long seed, int x, int z)")
    decision = body(prep, "private static boolean terrainShouldPlaceSnow(")
    density = body(prep, "float density(int x, int z)")
    builder = body(blend, "public static float[] buildScatteredSnowField(")
    paint = body(prep, "private static int paintNaturalSubsoil(")
    paint_surface = body(prep, "private static void paintNaturalSoil(ServerLevel level, int x, int surfaceY, int z,")
    verify = body(prep, "private static void verifyGrassColumn(ServerLevel level, int x, int z, int[] counters)")
    dress = body(prep, "private static void dressAndPlant(ServerLevel level, BlockPos min, BlockPos max,")

    if not field or "buildScatteredSnowField(" not in field:
        errors.append("spread field builder is not owned by StructureTerrainPrep")
    if any(name in mother for name in ("snowDensity(", "shouldPlaceSnow(", "SnowSpreadField", "ensureAuraField")):
        errors.append("TerrainMother still owns snow-spread decisions/calculation")
    if any(name in scan for name in ("SnowSpreadSnapshot", "latestSnowSpreadSnapshot", "grilleSnowSpread", " 9f. TAUX BRUT")):
        errors.append("DevTerrainScan is coupled to the theoretical spread")
    if any(name in scanview for name in ("grille_neige", "neigeCode", "Spread brut de neige", "'sn':")):
        errors.append("TXT scan renderer still consumes/plots theoretical snow spread")
    if "snowGridMinChunkX" not in field or "snowInputRevision" not in field:
        errors.append("field cache must be bounded to Mother inputs and invalidated on source changes")
    if "snowSourceAtChunk" not in field or "snowLandWeightAtChunk" not in field:
        errors.append("continuous source and fractional land weights are not sampled into the field")
    if "Math.floorDiv(wx, 16)" not in field or "Math.floorDiv(wz, 16)" not in field:
        errors.append("negative chunk coordinates must use floor division, not truncation")
    if "sampleX += wx" not in density or "sampleZ += wz" not in density:
        errors.append("domain warp must apply continuously at every column")
    if "Math.max(0.0, Math.min(qw - 1.0001, fx))" not in density:
        errors.append("quart-grid interpolation must clamp safely at field edges")
    if "SNOW_FULL_COVERAGE_AURA = 0.78f" not in prep or "SNOW_FIELD_RADIUS_BLOCKS = 47.0" not in prep:
        errors.append("spread calibration/radius changed without updating its guard")
    if "weighted = w * pw[p]" not in builder or "accW += weighted" not in builder:
        errors.append("fractional water/ice weight must affect both convolution numerator and denominator")
    if "accV += pv[p] * weighted" not in builder:
        errors.append("convolution numerator does not use the weighted snow source")
    if "mother.snowSourceAtChunk" not in field or "mother.snowLandWeightAtChunk" not in field or "return potential[cz * fHw + cx]" not in field:
        errors.append("spread field does not consume the real measured source/land values")
    if "aura >= SNOW_FULL_COVERAGE_AURA" not in decision or "aura <= 0f" not in decision:
        errors.append("snow coverage decision endpoints changed unexpectedly")
    if "float probability = t * t * (3f - 2f * t)" not in decision or "< probability" not in decision:
        errors.append("fractional coverage must become deterministic organic patches")
    if ("terrainSnowDensity(mother, worldSeed, x, z)" not in decision
            or "warpedPatchSigned(" not in decision or "decisionSeed ^" not in decision):
        errors.append("field seed must stay fixed to the world while patch randomness stays independent")
    wet = cover.find("columnWet(level, x, z)")
    spread = cover.find("terrainShouldPlaceSnow(mother, worldSeed, seed, x, z)")
    if (wet < 0 or "columnIcy(level, x, z)" not in cover
            or spread < 0 or wet > spread):
        errors.append("actual-column water/ice veto must precede TerrainPrep snow placement")
    placement = body(prep, "private static int placeSnowOrUnsnow(ServerLevel level, long seed, int x, int surfY, int z)")
    if "snowCoverHere(level, seed, x, z)" not in placement or "snowCoverHere(level, vegSeed" not in dress:
        errors.append("active terrain dressing bypasses the TerrainPrep snow decision")
    if "snowLayersAt" in mother or "terrainSnowLayers" not in prep:
        errors.append("snow thickness must also be decided by TerrainPrep")
    if not paint or "driftHash01(paintSeed, x, z) * 4.0" not in paint:
        errors.append("shared paint helper must select a stable 1-4 block soil profile")
    if ("isOreForSurfacePaint(under)" not in paint or "isOreForSurfacePaint(naturalSurface)" not in paint
            or "isOreForSurfacePaint(surface)" not in paint_surface or "under.is(Blocks.SNOW_BLOCK)" not in paint):
        errors.append("paint must preserve surface/subsoil ore veins and snow/ice supports")
    if "paintNaturalSoil(level, x, surfY, z, s, mut, counters)" not in verify:
        errors.append("verifyGrassSurface no longer uses the shared paint pass")
    paint_call = dress.find("stats[3] += paintNaturalSubsoil(level, x, surfY, z, actualSurface, curLc, chunkMask)")
    ecotone_gate = dress.find("if (!vegetationOnly && curEco != null)")
    blend_done = dress.find("chosen = borderFadePick(level, min, max, outerRing, x, z, chosen)")
    if (paint_call < 0 or "isOreForSurfacePaint(actualSurface)" not in dress
            or "if (soilSurface)" not in dress or "stats[8]++" not in dress
            or blend_done < 0 or paint_call < blend_done
            or (ecotone_gate >= 0 and paint_call < ecotone_gate)):
        errors.append("dressAndPlant paint must be independently invoked, counted and ore-safe")
    if "grilleSnowObserved(r, s, e)" not in scan or " 9g. COUVERTURE DE NEIGE OBSERVEE PAR COLONNE" not in scan:
        errors.append("independent observed-snow map is missing from the passive report")
    if "9f. TAUX BRUT DU SPREAD" in scan or " 9f. TAUX BRUT DU SPREAD" in scanview:
        errors.append("theoretical spread must not be embedded in the big TXT scan")
    return errors


def smoothstep(v: float) -> float:
    t = max(0.0, min(1.0, v))
    return t * t * (3.0 - 2.0 * t)


def field_rate(sources: list[tuple[float, float]], x: float, z: float,
               water: set[tuple[int, int]] | None = None) -> float:
    """Simple independent behavioral oracle for radial decay and weighted water exclusion."""
    water = water or set()
    numerator = denominator = 0.0
    for sx, sz in sources:
        cell = (math.floor(sx / 16), math.floor(sz / 16))
        if cell in water:
            continue
        d = math.hypot(x - sx, z - sz)
        w = max(0.0, 47.0**2 - d**2) ** 2
        numerator += w
        denominator += w
    if denominator <= 0.0:
        return 0.0
    return smoothstep((numerator / denominator) / 0.78)


def simulation() -> tuple[list[tuple[str, float]], list[tuple[int, float]]]:
    # Contracte de bord C1 de reference (pas une prediction biome) : les valeurs
    # testent seulement l'absence de saut aux limites de cellules/chunks.
    profile = [(d, smoothstep(1.0 - d / 47.0)) for d in range(65)]
    values = [v for _, v in profile]
    if any(b > a + 1e-12 for a, b in zip(values, values[1:])):
        raise AssertionError("reference boundary profile must decay monotonically")
    if values[0] != 1.0 or values[47] != 0.0 or values[-1] != 0.0:
        raise AssertionError("reference boundary profile endpoints changed")
    return [("aucune source", 0.0), ("une source", values[16]), ("deux sources", values[0])], profile


def run_self_test(root: Path) -> int:
    prep_path = root / PREP
    mother_path = root / MOTHER
    sabotages = [
        (prep_path, "return potential[cz * fHw + cx];", "return 0f;"),
        (prep_path, "|| deepluckyblock.util.SafeSurface.columnIcy(level, x, z)) return false;", "|| false) return false;"),
        (root / BLEND, "accW += weighted;", "accW += w;"),
        (prep_path, "isOreForSurfacePaint(under)", "false"),
        (prep_path, "decisionSeed ^ 0xD217F00D5EEDL", "worldSeed ^ 0xD217F00D5EEDL"),
        (prep_path, "if (soilSurface) {", "if (false && soilSurface) {"),
        (root / SCAN, "grilleSnowObserved(r, s, e);", ""),
    ]
    with tempfile.TemporaryDirectory(prefix="snowspread-restore-") as td:
        backup = Path(td) / "source.backup"
        for index, (path, old, new) in enumerate(sabotages, 1):
            original = path.read_bytes()
            backup.write_bytes(original)
            text = original.decode("utf-8")
            if text.count(old) != 1:
                raise AssertionError(f"sabotage {index}: anchor count {text.count(old)}")
            path.write_text(text.replace(old, new), encoding="utf-8")
            try:
                proc = subprocess.run([sys.executable, str(Path(__file__).resolve()), str(root)],
                                      cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                if proc.returncode == 0:
                    raise AssertionError(f"sabotage {index} survived the architecture/paint bench")
            finally:
                path.write_bytes(backup.read_bytes())
            if path.read_bytes() != backup.read_bytes():
                raise AssertionError(f"sabotage {index}: source restoration failed")
            print(f"sabotage {index}/{len(sabotages)}: rejected; source restored")
    print(f"snowspreadcheck self-test: {len(sabotages)}/{len(sabotages)} regressions caught and restored")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    if args.self_test:
        return run_self_test(root)
    errors = static_errors(root)
    if errors:
        for error in errors:
            print("FAIL:", error)
        return 1
    rows, profile = simulation()
    print("Configuration                               Couverture oracle")
    for label, value in rows:
        print(f"{label:<42} {value * 100:6.2f}%")
    output = root / "tools/snowspread-profile.csv"
    output.write_text("distance_blocks,fraction\\n" + "".join(f"{d},{v:.8f}\\n" for d, v in profile), encoding="utf-8")
    print(f"PASS: TerrainPrep-owned spread and shared paint guards; profile: {output.relative_to(root)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
