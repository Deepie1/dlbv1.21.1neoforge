package deepluckyblock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SEISMIC — capture et reprojection du terrain réel, exclusivement côté client.
 *
 * <p>Au premier tick, les BlockState, ModelData, positions et lumières de 144 colonnes maximum de quatre
 * couches sont figés. La passe de rendu réimprime les vrais modèles via l'atlas de blocs, sans
 * supprimer le terrain original. Les colonnes restent axis-aligned : seule leur hauteur change.
 * Aucun masque, aucune cellule omise, aucune particule de sol ni roche générique.</p>
 *
 * <p>Techniques étudiées : AcademyCraft GroundHeaveClient (renderSingleBlock au-dessus du monde intact),
 * FDBosses Geburah/RadialEarthquake (BlockState capturé, montée, maintien et descente), NuclearCraft ShockwaveDebrisEmitter (ModelData et rendu monde). La chronologie
 * P/S/Rayleigh suit les références pédagogiques IRIS ; le champ de faille et les rythmes sont propres
 * à Deep Lucky Block.</p>
 */
public final class SeismicTerrainVfx {
    private static final int MAX_SNAPSHOTS = 8;
    private static final int MAX_BLOCKS_PER_SNAPSHOT = 576;
    private static final int COLUMN_DEPTH = 4;
    private static final float REPRINT_EPSILON = 0.012f;
    private static final Map<SnapshotKey, TerrainSnapshot> SNAPSHOTS = new LinkedHashMap<>();
    private static ClientLevel trackedLevel;

    private SeismicTerrainVfx() { }

    public static void beginTerrainTick(ClientLevel level) {
        if (trackedLevel == level) return;
        trackedLevel = level;
        SNAPSHOTS.clear();
    }

    /** Capture unique : les ticks suivants ne remplacent jamais le « screen » initial. */
    public static void prepare(ClientLevel level, double x, double y, double z,
                               float dirX, float dirZ, float worldScale, int salt) {
        if (level == null) return;
        beginTerrainTick(level);
        SnapshotKey key = key(x, y, z, salt);
        if (SNAPSHOTS.containsKey(key)) return;

        float length = Mth.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 0.001f) { dirX = 0.0f; dirZ = 1.0f; }
        else { dirX /= length; dirZ /= length; }
        worldScale = Math.max(0.05f, worldScale);
        int radius = Mth.clamp(Mth.ceil(5.50f * worldScale), 6, 7);
        int originY = Mth.floor(y);
        List<CapturedBlock> blocks = new ArrayList<>(484);
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        captureColumns:
        for (int ix = -radius; ix <= radius; ix++) for (int iz = -radius; iz <= radius; iz++) {
            int bx = Mth.floor(x) + ix, bz = Mth.floor(z) + iz;
            BlockPos surface = findSurface(level, bx, originY, bz, y);
            if (surface == null) continue;
            double relX = (surface.getX() + 0.5 - x) / worldScale;
            double relZ = (surface.getZ() + 0.5 - z) / worldScale;
            float f = (float) (dirX * relX + dirZ * relZ);
            float l = (float) (-dirZ * relX + dirX * relZ);
            if (Math.abs(f) > 5.20f || Math.abs(l) > 5.20f) continue;

            int light = LevelRenderer.getLightColor(level, surface.above());
            float columnHash = hash(salt, surface.getX() * 73856093 ^ surface.getY() * 19349663
                    ^ surface.getZ() * 83492791);
            // La surface et ses trois supports partagent f/l/hash : aucune couche ne se désynchronise.
            for (int depth = 0; depth < COLUMN_DEPTH; depth++) {
                BlockPos pos = surface.below(depth);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) break;
                BakedModel model = dispatcher.getBlockModel(state);
                ModelData modelData = model.getModelData(level, pos, state, ModelData.EMPTY);
                blocks.add(new CapturedBlock(pos.immutable(), state, modelData, light, f, l,
                        columnHash, depth));
            }
            if (blocks.size() >= MAX_BLOCKS_PER_SNAPSHOT) break captureColumns;
        }

        TerrainSnapshot snapshot = new TerrainSnapshot(level, x, y, z, dirX, dirZ, worldScale,
                salt, List.copyOf(blocks), buildFractures(salt));
        SNAPSHOTS.put(key, snapshot);
        while (SNAPSHOTS.size() > MAX_SNAPSHOTS) {
            Iterator<SnapshotKey> it = SNAPSHOTS.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    /** Passe opaque indépendante, exécutée avant les anciens voxels translucides. */
    public static boolean render(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                 PoseStack pose, Vec3 camera, double x, double y, double z,
                                 float dirX, float dirZ, float worldScale, int salt, float t) {
        ClientLevel level = mc.level;
        if (level == null || t <= 0.0f || t >= 1.0f) return false;
        TerrainSnapshot snapshot = SNAPSHOTS.get(key(x, y, z, salt));
        if (snapshot == null || snapshot.level != level || snapshot.blocks.isEmpty()) return false;

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        boolean emitted = false;
        for (CapturedBlock block : snapshot.blocks) {
            float rise = riseAt(snapshot, block, t);
            pose.pushPose();
            pose.translate(block.pos.getX() - camera.x,
                    block.pos.getY() + rise + REPRINT_EPSILON - camera.y,
                    block.pos.getZ() - camera.z);
            // Aucun scale, yaw, pitch ou roll : modèle exact et volumes toujours axis-aligned.
            dispatcher.renderSingleBlock(block.state, pose, buffers, block.light,
                    OverlayTexture.NO_OVERLAY, block.modelData, null);
            pose.popPose();
            emitted = true;
        }
        return emitted;
    }

    /** Montée verticale sans rotation ni translation latérale : les volumes restent sur leur grille. */
    private static float riseAt(TerrainSnapshot snapshot, CapturedBlock block, float t) {
        float f = block.f, l = block.l, h = block.hash;
        FractureInfluence influence = nearestFracture(snapshot.fractures, f, l);
        Fracture fracture = influence.fracture;
        float distance = influence.distance;
        float side = influence.signedDistance < 0.0f ? -1.0f : 1.0f;
        float progress = clamp(influence.u / fracture.length);
        float start = fracture.delay + progress * 0.045f + h * 0.014f;
        float envelope = smooth((t - start) / (0.105f + fracture.depth * 0.025f))
                * (1.0f - smooth((t - 0.835f - h * 0.018f) / 0.145f));

        float radial = Mth.sqrt(f * f + l * l);
        // P : compression verticale courte. S : différentiel vertical de part et d'autre.
        float p = damped(t - 0.018f - radial * 0.0045f, 96.0f, 49.0f) * 0.024f;
        float shear = damped(t - 0.145f - radial * 0.0080f, 47.0f, 15.0f) * 0.022f * side;
        float attenuation = 1.0f / (1.0f + radial * 0.18f);
        float rayleigh = 0.0f;
        for (int wave = 0; wave < 3; wave++) {
            float arrival = 0.285f + wave * 0.155f + radial * (0.021f - wave * 0.002f);
            rayleigh += damped(t - arrival, 27.0f - wave * 3.0f, 10.5f + wave * 2.0f)
                    * (0.090f - wave * 0.014f) * attenuation;
        }
        float near = clamp(1.0f - distance / 2.40f);
        float aftershocks = damped(t - 0.515f - h * 0.025f, 39.0f, 17.0f) * 0.050f * near
                + damped(t - 0.690f - h * 0.018f, 44.0f, 20.0f) * 0.035f * near;

        float bank = clamp(1.0f - Math.max(0.0f, distance - influence.width) / 1.75f);
        float uplift = 0.20f + bank * (0.30f + fracture.depth * 0.22f) + h * 0.12f;
        // Les lignes de fracture restent basses, les berges montent : aucun trou artificiel.
        if (influence.inside) uplift *= 0.12f;
        float rise = envelope * uplift + p + shear + rayleigh + aftershocks;
        return Math.max(0.0f, rise * snapshot.worldScale);
    }

    /** Quatre racines indépendantes, six filles et trois tertiaires : aucune faille dominante. */
    private static List<Fracture> buildFractures(int salt) {
        List<Fracture> out = new ArrayList<>(13);
        float[] rootAngles = {0.18f, 2.22f, -1.96f};
        for (int root = 0; root < 3; root++) {
            float rf = (root == 0 ? -2.65f : root == 1 ? 2.10f : 1.90f)
                    + (hash(salt, 1000 + root * 31) - 0.5f) * 0.45f;
            float rl = (root == 0 ? -1.25f : root == 1 ? 1.35f : -1.55f)
                    + (hash(salt, 1001 + root * 37) - 0.5f) * 0.45f;
            float angle = rootAngles[root] + (hash(salt, 1002 + root * 41) - 0.5f) * 0.70f;
            float length = 2.65f + hash(salt, 1003 + root * 43) * 1.00f;
            float width = 0.36f + hash(salt, 1004 + root * 47) * 0.20f;
            float depth = 0.76f + hash(salt, 1005 + root * 53) * 0.46f;
            float delay = 0.052f + root * 0.027f + hash(salt, 1006 + root * 59) * 0.018f;
            float curvature = (hash(salt, 1007 + root * 61) - 0.5f) * 0.92f;
            Fracture parent = new Fracture(out.size(), rf, rl, angle, length, width, depth,
                    delay, curvature, salt + 1100 + root * 101, 0);
            out.add(parent);
            for (int child = 0; child < 2; child++) {
                float at = 0.31f + child * 0.35f + (hash(salt, 1200 + root * 67 + child) - 0.5f) * 0.12f;
                float[] origin = pointOn(parent, at);
                float sign = child == 0 ? -1.0f : 1.0f;
                float childAngle = angle + sign * (0.72f + hash(salt, 1201 + root * 71 + child) * 0.82f)
                        + (hash(salt, 1202 + root * 73 + child) - 0.5f) * 0.34f;
                float childLength = 0.90f + hash(salt, 1203 + root * 79 + child) * 1.15f;
                float childWidth = width * (0.48f + hash(salt, 1204 + root * 83 + child) * 0.24f);
                float childDepth = depth * (0.64f + hash(salt, 1205 + root * 89 + child) * 0.24f);
                Fracture branch = new Fracture(out.size(), origin[0], origin[1], childAngle,
                        childLength, childWidth, childDepth, delay + 0.035f + at * 0.050f,
                        (hash(salt, 1206 + root * 97 + child) - 0.5f) * 0.66f,
                        salt + 1300 + root * 131 + child * 17, 1);
                out.add(branch);
                if ((root + child) % 2 == 0) {
                    float[] tip = pointOn(branch, 0.52f + hash(salt, 1400 + root * 101 + child) * 0.20f);
                    float tertiaryAngle = childAngle - sign * (0.66f + hash(salt, 1401 + root * 103 + child) * 0.62f);
                    out.add(new Fracture(out.size(), tip[0], tip[1], tertiaryAngle,
                            0.55f + hash(salt, 1402 + root * 107 + child) * 0.70f,
                            childWidth * (0.52f + hash(salt, 1403 + root * 109 + child) * 0.18f),
                            childDepth * 0.72f, branch.delay + 0.052f,
                            (hash(salt, 1404 + root * 113 + child) - 0.5f) * 0.42f,
                            salt + 1500 + root * 137 + child * 19, 2));
                }
            }
        }
        // Une quatrième rupture excentrée traverse le réseau sans partager son origine.
        out.add(new Fracture(out.size(), -2.40f, 1.85f, -0.12f, 1.90f, 0.25f, 0.68f,
                0.155f, 0.31f, salt + 1700, 0));
        return List.copyOf(out);
    }

    private static float[] pointOn(Fracture fracture, float u) {
        float along = fracture.length * u;
        float bend = Mth.sin(u * (float) Math.PI) * fracture.curvature;
        float dx = Mth.cos(fracture.angle), dl = Mth.sin(fracture.angle);
        return new float[]{fracture.startF + dx * along - dl * bend,
                fracture.startL + dl * along + dx * bend};
    }

    private static FractureInfluence nearestFracture(List<Fracture> fractures, float f, float l) {
        FractureInfluence best = null;
        float bestScore = Float.MAX_VALUE;
        for (Fracture fracture : fractures) {
            float dx = Mth.cos(fracture.angle), dl = Mth.sin(fracture.angle);
            float rf = f - fracture.startF, rl = l - fracture.startL;
            float rawU = rf * dx + rl * dl;
            float u = Mth.clamp(rawU, 0.0f, fracture.length);
            float bend = Mth.sin(u / fracture.length * (float) Math.PI) * fracture.curvature;
            float signed = -rf * dl + rl * dx - bend;
            float outside = Math.abs(rawU - u);
            float distance = Mth.sqrt(signed * signed + outside * outside);
            float width = fracture.width
                    * (0.64f + 0.36f * Mth.sin(u / fracture.length * (float) Math.PI))
                    * (0.80f + 0.36f * noise1(u * 2.15f, fracture.seed));
            width = Math.max(0.055f, width);
            float score = distance - width;
            if (score >= bestScore) continue;
            boolean inside = rawU >= 0.0f && rawU <= fracture.length && Math.abs(signed) < width;
            best = new FractureInfluence(fracture, u, signed, distance, width, inside);
            bestScore = score;
        }
        return best;
    }

    private static BlockPos findSurface(ClientLevel level, int x, int originY, int z, double worldY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(worldY + 0.75));
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(worldY - 24.0));
        for (int y = maxY; y >= minY; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = shape.max(Direction.Axis.Y);
            if (!Double.isFinite(top) || top <= 0.0) continue;
            return pos.immutable();
        }
        return null;
    }

    private static float noise1(float x, int seed) {
        int i = Mth.floor(x);
        float u = smooth(x - i);
        float a = hash(seed, i * 73856093);
        float b = hash(seed, (i + 1) * 73856093);
        return a + (b - a) * u;
    }

    private static float hash(int seed, int n) {
        int h = seed ^ (n * 0x45D9F3B);
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }

    private static float damped(float age, float frequency, float decay) {
        return age <= 0.0f ? 0.0f : Mth.sin(age * frequency) * (float) Math.exp(-age * decay);
    }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float clamp(float x) {
        return Mth.clamp(x, 0.0f, 1.0f);
    }

    private static SnapshotKey key(double x, double y, double z, int salt) {
        return new SnapshotKey(Mth.floor(x), Mth.floor(y), Mth.floor(z), salt);
    }

    private record SnapshotKey(int x, int y, int z, int salt) { }
    private record CapturedBlock(BlockPos pos, BlockState state, ModelData modelData,
                                 int light, float f, float l, float hash, int columnDepth) { }
    private record TerrainSnapshot(ClientLevel level, double x, double y, double z,
                                   float dirX, float dirZ, float worldScale, int salt,
                                   List<CapturedBlock> blocks, List<Fracture> fractures) { }
    private record Fracture(int id, float startF, float startL, float angle, float length,
                            float width, float depth, float delay, float curvature,
                            int seed, int generation) { }
    private record FractureInfluence(Fracture fracture, float u, float signedDistance,
                                     float distance, float width, boolean inside) { }
}
