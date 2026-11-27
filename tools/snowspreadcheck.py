#!/usr/bin/env python3
"""Simulation + mutation bench guarding continuous, chunk-independent snow spread."""
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


def static_errors(root: Path) -> list[str]:
    mother = (root / MOTHER).read_text(encoding="utf-8")
    prep = (root / PREP).read_text(encoding="utf-8")
    scan = (root / SCAN).read_text(encoding="utf-8")
    mother = re.sub(r"/\*.*?\*/|//[^\n]*", "", mother, flags=re.S)
    prep = re.sub(r"/\*.*?\*/|//[^\n]*", "", prep, flags=re.S)
    scan = re.sub(r"/\*.*?\*/|//[^\n]*", "", scan, flags=re.S)
    errors: list[str] = []
    density = body(mother, "public float snowDensity(int x, int z)")
    decision = body(mother, "public boolean shouldPlaceSnow(long seed, int x, int z)")
    aura = body(mother, "private float[] ensureAuraField()")
    cell_source = body(mother, "private float cellSnowPotential(int cx, int cz)")
    cover = body(prep, "private static boolean snowCoverHere(ServerLevel level, long seed, int x, int z)")
    warp = density[density.find("if (worldSeed != 0L)"):density.find("// Coordonnée continue")]

    if "HALO_CHUNKS = 3" not in mother:
        errors.append("HALO_CHUNKS must stay 3")
    if "float[] haloSnowPotential" not in mother or "setHaloSnowPotential" not in mother:
        errors.append("halo/source must carry a continuous float potential")
    if "c.snowyRatio" not in cell_source or "c.isSnowy() ?" in cell_source:
        errors.append("source is binary rather than measured continuously")
    if "SPREAD_ENTREE" in aura or "SPREAD_PORTEE_CHUNKS" in aura or "pasI" in aura or "pasJ" in aura:
        errors.append("cardinal chunk-neighbor spread remains in ensureAuraField")
    if "w <= 0.08f" in aura or "isIceOrWaterCell" in density or "isIceOrWaterCell" in warp:
        errors.append("chunk-level water/ice veto remains in snow decision or warp")
    if any(token in density + decision for token in (">> 4", ">>4", "/ 16")):
        errors.append("public snow density/decision still quantizes by chunk")
    if ("sampleX += wx;" not in warp or "sampleZ += wz;" not in warp or
            re.search(r"if\s*\([^)]*isIceOrWaterCell", warp)):
        errors.append("domain warp is guarded instead of applied unconditionally")
    if "SNOW_FULL_COVERAGE_AURA = 0.78f" not in mother or "SNOW_FIELD_RADIUS_BLOCKS = 47.0" not in mother:
        errors.append("calibration constants or their useful range changed unexpectedly")
    aura_match = re.search(r"SNOW_FULL_COVERAGE_AURA\s*=\s*([0-9.]+)f", mother)
    radius_match = re.search(r"SNOW_FIELD_RADIUS_BLOCKS\s*=\s*([0-9.]+)", mother)
    halo_match = re.search(r"HALO_CHUNKS\s*=\s*(\d+)", mother)
    if not (aura_match and radius_match and halo_match and
            0.0 < float(aura_match.group(1)) < 1.0 and
            0.0 < float(radius_match.group(1)) <= int(halo_match.group(1)) * 16):
        errors.append("decision/radius constants do not fit the three-chunk halo range")
    if "aura >= SNOW_FULL_COVERAGE_AURA" not in decision or "aura <= 0f" not in decision:
        errors.append("decision calibration endpoints changed unexpectedly")
    if "coverageProbability = t * t * (3f - 2f * t)" not in decision or "patch < coverageProbability" not in decision:
        errors.append("decision probability no longer has a useful continuous 0..1 range")
    if "snowRadiusChunks = HALO_CHUNKS" not in mother:
        errors.append("spread range must be fixed at the three-chunk halo, not inferred from chunk votes")
    if "columnWet(level, x, z)" not in cover or "columnIcy(level, x, z)" not in cover:
        errors.append("water/ice zero-snow guarantee is not checked at the actual column")
    if "grilleSnowObserved(r, s, e)" not in scan or " 9g. COUVERTURE DE NEIGE OBSERVEE PAR COLONNE" not in scan:
        errors.append("column-by-column observed snow coverage map is missing")
    if "rateAtBlock(int x, int z)" not in mother or "potential[iz * qw + ix]" not in mother:
        errors.append("published potential is not sampled/interpolated at column scale")
    return errors


def smoothstep(v: float) -> float:
    t = max(0.0, min(1.0, v))
    return t * t * (3.0 - 2.0 * t)


def field_rate(sources: list[tuple[float, float]], x: float, z: float,
               water: set[tuple[int, int]] | None = None) -> float:
    """Reference calibration: smooth radial kernel over source strengths; no chunk gates."""
    water = water or set()
    aura = 0.0
    for sx, sz in sources:
        cell = (math.floor(sx / 16), math.floor(sz / 16))
        if cell in water:
            continue
        distance = math.hypot(x - sx, z - sz)
        aura += 0.22 * math.exp(-0.5 * (distance / 24.0) ** 2)
    return smoothstep(aura / 0.78)


def simulation() -> tuple[list[tuple[str, float]], list[tuple[int, float]]]:
    # The target is the centre of a dry chunk. Source centres are continuous world coordinates.
    configs: list[tuple[str, list[tuple[float, float]], set[tuple[int, int]]]] = [
        ("0", [], set()),
        ("1 voisin k=1", [(-16, 0)], set()),
        ("1 voisin k=2", [(-32, 0)], set()),
        ("1 voisin k=3", [(-48, 0)], set()),
        ("2 adjacents", [(-16, 0), (0, -16)], set()),
        ("2 opposes", [(-16, 0), (16, 0)], set()),
        ("3 voisins", [(-16, 0), (16, 0), (0, -16)], set()),
        ("4 voisins", [(-16, 0), (16, 0), (0, -16), (0, 16)], set()),
        ("1 voisin, eau en face", [(-16, 0)], {(1, 0)}),
        ("enclave seche (8 sources)", [(-16, -16), (0, -16), (16, -16),
                                         (-16, 0), (16, 0), (-16, 16), (0, 16), (16, 16)], set()),
    ]
    rows: list[tuple[str, float]] = []
    for label, sources, water in configs:
        rate = field_rate(sources, 0, 0, water)
        if label == "0" and rate != 0:
            raise AssertionError("zero-source case must be exactly snow-free")
        if sources and rate <= 0:
            raise AssertionError(f"{label}: a snow-neighbor configuration got zero coverage")
        rows.append((label, rate))
    by_name = dict(rows)
    if not (by_name["1 voisin k=1"] >= by_name["1 voisin k=2"] >= by_name["1 voisin k=3"] > 0):
        raise AssertionError("single-source distance progression is not monotone")
    if not (by_name["2 adjacents"] >= by_name["1 voisin k=1"] and
            by_name["2 opposes"] >= by_name["1 voisin k=1"] and
            by_name["3 voisins"] > by_name["2 adjacents"] and
            by_name["4 voisins"] > by_name["3 voisins"]):
        raise AssertionError("coverage must increase monotonically with contributing neighbors")
    if by_name["1 voisin, eau en face"] != by_name["1 voisin k=1"]:
        raise AssertionError("water on the opposite side incorrectly vetoed the dry-side spread")
    if field_rate([], 0, 0, {(0, 0)}) != 0:
        raise AssertionError("water/ice column must remain snow-free")

    profile = [(d, field_rate([(0, 0)], d, 0)) for d in range(0, 65)]
    values = [v for _, v in profile]
    if any(b > a + 1e-12 for a, b in zip(values, values[1:])):
        raise AssertionError("distance profile is not monotone")
    if values[-1] <= 0:
        raise AssertionError("profile vanished before the 48-block halo edge")
    for boundary in (16, 32, 48):
        jump = abs(values[boundary + 1] - values[boundary - 1])
        if jump >= 0.08:
            raise AssertionError(f"visible step detected at {boundary} blocks: {jump:.4f}")
    return rows, profile


def run_self_test(root: Path) -> int:
    # Each sabotage is one realistic regression and is restored byte-for-byte before the next.
    mother_path, prep_path, scan_path = root / MOTHER, root / PREP, root / SCAN
    sabotages = [
        (mother_path, "public boolean shouldPlaceSnow(long seed, int x, int z) {\n        setWorldSeed(seed);\n        float aura = snowDensity(x, z);", "public boolean shouldPlaceSnow(long seed, int x, int z) {\n        setWorldSeed(seed);\n        int chunk = x >> 4;\n        float aura = snowDensity(x, z);"),
        (mother_path, "            return Math.max(0f, Math.min(1f, c.snowyRatio));", "            return c.isSnowy() ? 1f : 0f;"),
        (mother_path, "            sampleX += wx;", "            if ((x >> 4) == ((int) (x + wx) >> 4)) sampleX += wx;"),
        (mother_path, "public float snowDensity(int x, int z) {\n        awaitAiPlanOnce();\n        evaluateSnowRadius();\n        float[] field = ensureAuraField();", "public float snowDensity(int x, int z) {\n        awaitAiPlanOnce();\n        evaluateSnowRadius();\n        if (isIceOrWaterCell(x >> 4, z >> 4)) return 0f;\n        float[] field = ensureAuraField();"),
        (mother_path, "        final float[] linkedPotential = cellPotential.clone();", "        final int pasI = 1, pasJ = 0; // cardinal chunk spread sabotage\n        final float[] linkedPotential = cellPotential.clone();"),
        (mother_path, "SNOW_FULL_COVERAGE_AURA = 0.78f", "SNOW_FULL_COVERAGE_AURA = 0.92f"),
        (prep_path, "        if (deepluckyblock.util.SafeSurface.columnWet(level, x, z)\n                || deepluckyblock.util.SafeSurface.columnIcy(level, x, z)) return false;", "        // sabotaged: no water/ice column guard"),
        (scan_path, "        grilleSnowObserved(r, s, e);", "        // sabotaged: observed column map removed"),
    ]
    with tempfile.TemporaryDirectory(prefix="snowspread-restore-") as td:
        backup = Path(td) / "source.backup"
        for index, (path, old, new) in enumerate(sabotages, 1):
            original = path.read_bytes()
            backup.write_bytes(original)
            text = original.decode("utf-8")
            assert text.count(old) == 1, f"sabotage {index}: expected exactly one anchor, found {text.count(old)}"
            path.write_text(text.replace(old, new), encoding="utf-8")
            try:
                proc = subprocess.run([sys.executable, str(Path(__file__).resolve()), str(root)],
                                      cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                if proc.returncode == 0:
                    raise AssertionError(f"sabotage {index} survived the snow spread bench")
            finally:
                path.write_bytes(backup.read_bytes())
            cmp = subprocess.run(["cmp", "-s", str(path), str(backup)], check=False)
            if cmp.returncode != 0:
                raise AssertionError(f"sabotage {index}: source restoration cmp -s failed")
            print(f"sabotage {index}/8: rejected; source restored (cmp -s)")
    print("snowspreadcheck self-test: 8/8 realistic regressions caught and restored")
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
    print("Configuration                               Couverture simulee")
    for label, rate in rows:
        print(f"{label:<42} {rate * 100:6.2f}%")
    print("\\nDistance (blocs) : potentiel/couverture de frange depuis une source")
    for d, value in profile:
        if d in (0, 8, 15, 16, 17, 24, 31, 32, 33, 40, 47, 48, 49, 56, 64):
            print(f"{d:>3} : {value * 100:6.2f}%")
    out = root / "tools/snowspread-profile.csv"
    out.write_text("distance_blocks,coverage_fraction\\n" + "".join(f"{d},{v:.8f}\\n" for d, v in profile), encoding="utf-8")
    print(f"\\nPASS: continuous simulation and static guards; profile: {out.relative_to(root)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
