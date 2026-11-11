package deepluckyblock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T374-WIND-TERRAIN — <b>vrais blocs du sol</b> arrachés par la tornade.
 *
 * <p>Même technique que le séisme : les {@link BlockState}, {@link ModelData}, positions et lumières
 * d'une couronne de sol sont figés côté client au premier tick, puis réimprimés à l'identique via
 * l'atlas de blocs. Rien n'est retiré du monde : la tornade soulève une copie des blocs, exactement
 * comme {@link SeismicTerrainVfx} le fait pour la faille.</p>
 *
 * <p>Trajectoire : chaque bloc reste posé au sol, puis décolle, monte en spirale dans le sens de la
 * tornade en grossissant légèrement, culbute sur lui-même, atteint le sommet et est éjecté vers
 * l'extérieur avant de fondre. Aucun cube générique : ici ce sont les teintes réelles du terrain
 * (herbe, sable, pierre, neige, netherrack…) qui volent.</p>
 */
public final class WindTerrainVfx {

    private static final int MAX_SNAPSHOTS = 6;
    private static final int MAX_BLOCKS = 110;

    private record SnapshotKey(long x, long y, long z, int salt) { }

    /** Bloc capturé + paramètres stable de sa trajectoire. */
    private record Debris(BlockState state, ModelData modelData, int light,
                          float surfaceY, float phase, float radius0, float radius1,
                          float size, float turns, float speed, float liftDelay,
                          float tumbleSpeed, float spinDir) { }

    private record Snapshot(ClientLevel level, float height,
                            float spinDir, List<Debris> debris) { }

    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new LinkedHashMap<>();
    /** Quaternion réutilisé : la passe de rendu ne doit rien allouer par bloc. */
    private static final org.joml.Quaternionf ROTATION = new org.joml.Quaternionf();
    private static ClientLevel trackedLevel;

    private WindTerrainVfx() { }

    /** Purge quand le joueur change de monde (appelé chaque tick client par le manager). */
    public static void beginTerrainTick(ClientLevel level) {
        if (trackedLevel == level) return;
        trackedLevel = level;
        SNAPSHOTS.clear();
    }

    /** Capture unique : les ticks suivants réutilisent le même « screen » de terrain. */
    public static void prepare(ClientLevel level, double x, double y, double z, int salt,
                               float height, float spinDir) {
        if (level == null) return;
        beginTerrainTick(level);
        if (height < 1.0f) height = 4.0f;
        float dir = spinDir < 0.0f ? -1.0f : 1.0f;
        SnapshotKey key = key(x, y, z, salt);
        if (SNAPSHOTS.containsKey(key)) return;

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        int originY = Mth.floor(y);
        int radius = Mth.clamp(Mth.ceil(3.30f + height * 0.16f), 4, 6);
        List<Debris> debris = new ArrayList<>(MAX_BLOCKS);

        capture:
        for (int ix = -radius; ix <= radius; ix++) for (int iz = -radius; iz <= radius; iz++) {
            int bx = Mth.floor(x) + ix, bz = Mth.floor(z) + iz;
            BlockPos surface = findSurface(level, bx, originY, bz);
            if (surface == null) continue;
            double relX = surface.getX() + 0.5 - x;
            double relZ = surface.getZ() + 0.5 - z;
            float dist = (float) Math.sqrt(relX * relX + relZ * relZ);
            if (dist > radius + 0.5f) continue;
            float h = hash(salt, surface.getX() * 73856093 ^ surface.getY() * 19349663
                    ^ surface.getZ() * 83492791);
            // Le pourtour est clairsemé : le sol d'origine reste lisible, sans cadre carré.
            if (dist > radius * 0.55f && h > 0.52f) continue;

            BlockState state = level.getBlockState(surface);
            if (state.isAir()) continue;
            BakedModel model = dispatcher.getBlockModel(state);
            ModelData modelData;
            try {
                modelData = model.getModelData(level, surface, state, ModelData.EMPTY);
            } catch (RuntimeException ignored) {
                modelData = ModelData.EMPTY;
            }
            int light = LevelRenderer.getLightColor(level, surface.above());
            VoxelShape shape = state.getCollisionShape(level, surface);
            float topY = shape.isEmpty()
                    ? surface.getY() + 1.0f
                    : (float) (surface.getY() + shape.max(Direction.Axis.Y));

            float h2 = hash(salt, 0x51F3 ^ (surface.getX() * 2654435761L));
            float h3 = hash(salt, 0x7A19 + surface.getZ() * 40503);
            float phase = (float) (Math.atan2(relZ, relX) / (Math.PI * 2.0));
            float orbit = Mth.clamp(dist * 0.55f + h2 * 1.10f, 0.35f, 3.10f);
            // T374-WIND-TOP : sommet plus large que la base (profil d'entonnoir demandé).
            float top = Math.max(orbit * 1.45f, 1.55f + h3 * 1.35f);
            debris.add(new Debris(state, modelData, light, topY,
                    phase, orbit, top,
                    0.30f + h2 * 0.22f,
                    1.15f + h3 * 0.85f,
                    0.85f + h * 0.55f,
                    h2 * 0.30f,
                    1.40f + h3 * 2.60f,
                    dir));
            if (debris.size() >= MAX_BLOCKS) break capture;
        }
        if (debris.isEmpty()) return;
        SNAPSHOTS.put(key, new Snapshot(level, height, dir, List.copyOf(debris)));
        while (SNAPSHOTS.size() > MAX_SNAPSHOTS) {
            var it = SNAPSHOTS.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    /**
     * Réimprime les blocs capturés à leur place du tourbillon.
     *
     * @return {@code true} si au moins un bloc a été dessiné (le manager doit alors terminer le batch)
     */
    public static boolean render(Minecraft mc, MultiBufferSource.BufferSource buffers, PoseStack pose,
                                 net.minecraft.world.phys.Vec3 camera,
                                 double x, double y, double z, int salt, float t) {
        ClientLevel level = mc.level;
        if (level == null || t <= 0.0f || t >= 1.0f) return false;
        Snapshot snapshot = SNAPSHOTS.get(key(x, y, z, salt));
        if (snapshot == null || snapshot.level() != level || snapshot.debris().isEmpty()) return false;

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        float height = snapshot.height();
        float spin = snapshot.spinDir();
        boolean emitted = false;
        for (Debris d : snapshot.debris()) {
            float cycle = t * d.speed() + d.phase();
            cycle = cycle - Mth.floor(cycle);
            float lift = smooth((cycle - d.liftDelay()) / 0.22f);
            if (lift <= 0.001f) continue;
            // T374-WIND-TOP : la montée atteint le SOMMET de la tour (avant, le facteur 0,55+0,45·taille
            // plafonnait tous les blocs vers 65-70 % de la hauteur : « on dirait qu'ils sont cappés
            // au milieu »). Le palier haut est aussi allongé pour que les blocs restent visibles en haut.
            float fall = smooth((cycle - 0.88f) / 0.12f);
            float rise = smooth(cycle / 0.74f);
            float angle = (d.phase() + cycle * d.turns()) * TAU * spin;
            float radius = Mth.lerp(rise, d.radius0(), d.radius1())
                    * (0.80f + 0.26f * Mth.sin(cycle * 9.0f + d.phase() * 7.0f))
                    + fall * 1.15f;
            // Chaque bloc culmine à une hauteur propre (70 à 100 % de la tour) : la colonne reste
            // garnie sur toute sa hauteur au lieu de former un couvercle à mi-course.
            float peak = 0.70f + 0.30f * Mth.clamp((d.turns() - 1.15f) / 0.85f, 0.0f, 1.0f);
            float dy = rise * height * peak * (0.94f + 0.06f * d.size()) - fall * height * 0.92f;
            float size = d.size() * (1.0f - fall * 0.25f);
            if (size <= 0.05f) continue;

            pose.pushPose();
            pose.translate(x + Math.cos(angle) * radius - camera.x,
                    d.surfaceY() + dy - camera.y,
                    z + Math.sin(angle) * radius - camera.z);
            float tumble = (t * 8.0f + d.phase()) * d.tumbleSpeed();
            ROTATION.identity().rotateY(angle).rotateX(tumble * 0.85f).rotateZ(tumble * 0.55f);
            pose.mulPose(ROTATION);
            pose.scale(size, size, size);
            pose.translate(-0.5f, -0.5f, -0.5f);
            dispatcher.renderSingleBlock(d.state(), pose, buffers, d.light(),
                    OverlayTexture.NO_OVERLAY, d.modelData(), null);
            pose.popPose();
            emitted = true;
        }
        return emitted;
    }

    private static final float TAU = (float) (Math.PI * 2.0);

    /** Sol atteignable : on descend depuis deux blocs au-dessus de l'origine jusqu'à six en dessous. */
    private static BlockPos findSurface(ClientLevel level, int bx, int originY, int bz) {
        for (int dy = 2; dy >= -6; dy--) {
            BlockPos pos = new BlockPos(bx, originY + dy, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getCollisionShape(level, pos).isEmpty()) continue;
            return pos.immutable();
        }
        return null;
    }

    private static SnapshotKey key(double x, double y, double z, int salt) {
        return new SnapshotKey(Mth.floor(x * 2.0), Mth.floor(y * 2.0), Mth.floor(z * 2.0), salt);
    }

    private static float smooth(float value) {
        float x = Mth.clamp(value, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float hash(int seed, long n) {
        long h = (seed * 0x9E3779B97F4A7C15L) ^ (n * 0xBF58476D1CE4E5B9L);
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (float) ((h >>> 11) * 0x1.0p-53);
    }
}
