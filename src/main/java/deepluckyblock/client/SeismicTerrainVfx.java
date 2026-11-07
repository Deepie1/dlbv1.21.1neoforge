package deepluckyblock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SEISMIC — capture et reprojection du terrain réel, exclusivement côté client.
 *
 * <p>Au premier tick, les BlockState, ModelData, positions et lumières de la zone sont figés.
 * La passe de rendu réimprime ensuite les vrais modèles via l'atlas de blocs. Les cellules de la
 * faille sont omises : ce ne sont ni des particules de sol, ni des roches génériques. Les lèvres
 * se séparent et pivotent avec leurs faces latérales visibles. Un support sombre occulte le haut
 * du terrain original dans les cellules retirées ; le monde et ses collisions restent intacts.</p>
 *
 * <p>Techniques étudiées : AcademyCraft GroundHeaveClient (renderSingleBlock et léger agrandissement
 * anti-z-fighting), FDBosses Geburah/RadialEarthquake (BlockState capturé, pivot et phases de
 * soulèvement), NuclearCraft ShockwaveDebrisEmitter (ModelData et rendu monde). La chronologie
 * P/S/Rayleigh suit les références pédagogiques IRIS ; le champ de faille et les rythmes sont propres
 * à Deep Lucky Block.</p>
 */
public final class SeismicTerrainVfx {
    private static final int MAX_SNAPSHOTS = 8;
    private static final int MAX_BLOCKS_PER_SNAPSHOT = 144;
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
        int radius = Mth.clamp(Mth.ceil(4.25f * worldScale), 4, 6);
        int originY = Mth.floor(y);
        List<CapturedBlock> blocks = new ArrayList<>(96);
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        for (int ix = -radius; ix <= radius; ix++) for (int iz = -radius; iz <= radius; iz++) {
            int bx = Mth.floor(x) + ix, bz = Mth.floor(z) + iz;
            BlockPos surface = findSurface(level, bx, originY, bz, y);
            if (surface == null) continue;
            double relX = (surface.getX() + 0.5 - x) / worldScale;
            double relZ = (surface.getZ() + 0.5 - z) / worldScale;
            float f = (float) (dirX * relX + dirZ * relZ);
            float l = (float) (-dirZ * relX + dirX * relZ);
            if (Math.abs(f) > 4.20f || Math.abs(l) > 3.20f) continue;

            BlockState state = level.getBlockState(surface);
            if (state.isAir()) continue;
            BakedModel model = dispatcher.getBlockModel(state);
            ModelData modelData = model.getModelData(level, surface, state, ModelData.EMPTY);
            int light = LevelRenderer.getLightColor(level, surface.above());
            blocks.add(new CapturedBlock(surface.immutable(), state, modelData, light, f, l,
                    hash(salt, surface.getX() * 73856093 ^ surface.getY() * 19349663
                            ^ surface.getZ() * 83492791)));
            if (blocks.size() >= MAX_BLOCKS_PER_SNAPSHOT) break;
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
        VertexConsumer voidVc = null;
        Vector3f voidTmp = new Vector3f();
        boolean emitted = false;
        for (CapturedBlock block : snapshot.blocks) {
            Motion motion = motionAt(snapshot, block, t);
            if (motion.occlusion > 0.015f) {
                if (voidVc == null) voidVc = buffers.getBuffer(WeaponWorldVfxManager.getTranslucentRenderType());
                // Occulte aussi la source sous les blocs déplacés : pas de « doublon » original/reprint.
                emitVoidCell(voidVc, pose.last().pose(), voidTmp, snapshot, block,
                        camera, motion.occlusion);
                emitted = true;
            }
            if (motion.voidOpen > 0.975f) continue;

            double drop = motion.voidOpen > 0.015f ? (1.0f - motion.voidOpen) * (0.45f + motion.depth * 0.65f) : 0.0f;
            double worldX = block.pos.getX() + motion.offsetX;
            double worldY = block.pos.getY() + motion.rise + REPRINT_EPSILON - drop;
            double worldZ = block.pos.getZ() + motion.offsetZ;
            pose.pushPose();
            pose.translate(worldX - camera.x, worldY - camera.y, worldZ - camera.z);
            pose.translate(0.5f, 0.0f, 0.5f);
            pose.mulPose(Axis.YP.rotation(motion.yaw));
            pose.mulPose(Axis.XP.rotation(motion.pitch));
            pose.mulPose(Axis.ZP.rotation(motion.roll));
            // Recouvre la surface source sans z-fighting, sans changer la silhouette du modèle.
            pose.scale(1.002f, 1.0f, 1.002f);
            pose.translate(-0.5f, 0.0f, -0.5f);
            dispatcher.renderSingleBlock(block.state, pose, buffers, block.light,
                    OverlayTexture.NO_OVERLAY, block.modelData, null);
            pose.popPose();
            emitted = true;
        }
        return emitted;
    }

    /** P précoce, S transversal, graphe de rupture, Rayleigh puis répliques. */
    private static Motion motionAt(TerrainSnapshot snapshot, CapturedBlock block, float t) {
        float f = block.f, l = block.l, h = block.hash;
        FractureInfluence influence = nearestFracture(snapshot.fractures, f, l);
        Fracture fracture = influence.fracture;
        float distance = influence.distance;
        float side = influence.signedDistance < 0.0f ? -1.0f : 1.0f;
        float progress = clamp(influence.u / fracture.length);
        float start = fracture.delay + progress * 0.045f + h * 0.014f;
        float open = outBack((t - start) / (0.070f + fracture.depth * 0.028f))
                * (1.0f - smooth((t - 0.835f - h * 0.018f) / 0.145f));
        open = Math.max(0.0f, open);
        float voidOpen = influence.inside ? clamp(open) : 0.0f;

        float radial = Mth.sqrt(f * f + l * l);
        float p = damped(t - 0.018f - radial * 0.0045f, 96.0f, 49.0f) * 0.024f;
        float shear = damped(t - 0.145f - radial * 0.0080f, 47.0f, 15.0f) * 0.060f;
        float attenuation = 1.0f / (1.0f + radial * 0.18f);
        float rayleigh = 0.0f;
        for (int wave = 0; wave < 3; wave++) {
            float arrival = 0.285f + wave * 0.155f + radial * (0.021f - wave * 0.002f);
            rayleigh += damped(t - arrival, 27.0f - wave * 3.0f, 10.5f + wave * 2.0f)
                    * (0.115f - wave * 0.018f) * attenuation;
        }
        float near = clamp(1.0f - distance / 2.40f);
        float aftershocks = damped(t - 0.515f - h * 0.025f, 39.0f, 17.0f) * 0.065f * near
                + damped(t - 0.690f - h * 0.018f, 44.0f, 20.0f) * 0.045f * near;

        float bank = clamp(1.0f - Math.max(0.0f, distance - influence.width) / 1.75f);
        float uplift = (0.10f + bank * (0.34f + fracture.depth * 0.36f + h * 0.20f))
                * (0.82f + noise1(f * 0.72f + l * 0.31f, snapshot.salt + 303) * 0.36f);
        float rise = open * uplift + p + rayleigh + aftershocks;
        float separation = side * open * (0.055f + bank * (0.16f + influence.width * 0.22f));
        float normalF = -Mth.sin(fracture.angle), normalL = Mth.cos(fracture.angle);
        float shearOffset = shear * (0.45f + bank * 0.55f);
        float localF = normalF * separation + rayleigh * 0.30f + shear * 0.24f;
        float localL = normalL * separation + shearOffset;

        float offsetX = (snapshot.dirX * localF - snapshot.dirZ * localL) * snapshot.worldScale;
        float offsetZ = (snapshot.dirZ * localF + snapshot.dirX * localL) * snapshot.worldScale;
        float roll = side * open * (0.035f + bank * (0.10f + h * 0.10f)) + shear * 0.70f;
        float pitch = rayleigh * 0.34f + (h - 0.5f) * open * bank * 0.11f;
        float yaw = shear * 0.32f + (h - 0.5f) * open * 0.055f;
        float occlusion = Math.max(voidOpen, clamp(open));
        return new Motion(offsetX, rise * snapshot.worldScale, offsetZ,
                yaw, pitch, roll, voidOpen, occlusion, fracture.depth);
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

    /** Masque opaque de la cellule source retirée ; les berges surélevées fournissent la profondeur. */
    private static void emitVoidCell(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                     TerrainSnapshot snapshot, CapturedBlock block,
                                     Vec3 camera, float open) {
        float x0 = (float) (block.pos.getX() - camera.x) - 0.006f;
        float x1 = x0 + 1.012f;
        float z0 = (float) (block.pos.getZ() - camera.z) - 0.006f;
        float z1 = z0 + 1.012f;
        VoxelShape shape = block.state.getCollisionShape(snapshot.level, block.pos);
        float top = shape.isEmpty() ? 1.0f : (float) shape.max(Direction.Axis.Y);
        float y = (float) (block.pos.getY() + top - camera.y) + 0.006f;
        int shade = Math.round(7 + 7 * (1.0f - open));
        int argb = 0xFF000000 | (shade << 16) | (shade << 8) | shade;
        put(vc, mat, tmp, x0, y, z0, argb);
        put(vc, mat, tmp, x0, y, z1, argb);
        put(vc, mat, tmp, x1, y, z1, argb);
        put(vc, mat, tmp, x1, y, z0, argb);
    }

    private static void put(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                            float x, float y, float z, int argb) {
        mat.transformPosition(x, y, z, tmp);
        vc.addVertex(tmp.x(), tmp.y(), tmp.z())
                .setColor((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
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

    private static float outBack(float x) {
        x = clamp(x);
        float u = x - 1.0f;
        return 1.0f + 2.70158f * u * u * u + 1.70158f * u * u;
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
                                 int light, float f, float l, float hash) { }
    private record TerrainSnapshot(ClientLevel level, double x, double y, double z,
                                   float dirX, float dirZ, float worldScale, int salt,
                                   List<CapturedBlock> blocks, List<Fracture> fractures) { }
    private record Fracture(int id, float startF, float startL, float angle, float length,
                            float width, float depth, float delay, float curvature,
                            int seed, int generation) { }
    private record FractureInfluence(Fracture fracture, float u, float signedDistance,
                                     float distance, float width, boolean inside) { }
    private record Motion(float offsetX, float rise, float offsetZ,
                          float yaw, float pitch, float roll, float voidOpen, float occlusion,
                          float depth) { }
}
