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
 * T375-WATER-TERRAIN — <b>vrais blocs du sol</b> emportés par l'eau.
 *
 * <p>Même technique que {@link WindTerrainVfx} et {@link SeismicTerrainVfx} : les {@link BlockState},
 * {@link ModelData}, positions et lumières d'une plaque de sol sont figés côté client au premier
 * tick, puis réimprimés à l'identique via l'atlas de blocs. <b>Rien n'est retiré du monde</b> : ce
 * sont des copies qui voyagent, donc aucune désynchronisation serveur, aucun trou dans le terrain.</p>
 *
 * <p>Deux trajectoires, choisies par la signature :</p>
 * <ul>
 *   <li>{@link #MODE_SURGE} — <b>le raz-de-marée pousse</b>. Un bloc n'est emporté qu'au passage du
 *       front (son {@code pickup} dépend de sa position <i>le long de l'axe de la vague</i>, pas de
 *       sa distance au centre) : la plaque se soulève donc de proche en proche, comme une vague
 *       réelle qui ramasse ce qu'elle rencontre. Le bloc part en avant, rebondit sur la crête,
 *       culbute, dérive latéralement puis retombe et glisse quand l'eau reflue.</li>
 *   <li>{@link #MODE_MAELSTROM} — <b>le tourbillon avale</b>. Le bloc est aspiré vers l'axe en
 *       spirale (rayon qui se resserre, angle qui s'enroule, altitude qui <b>descend</b> le long de
 *       la paroi de l'entonnoir), disparaît dans la gorge, puis est <b>recraché par le geyser</b>
 *       tout à la fin.</li>
 * </ul>
 *
 * <p>Comme pour la tornade, chaque bloc reçoit un tirage propre (rayon d'arrivée, vitesse de
 * rotation, durée de vol, culbute) : les débris ne décrivent jamais la même courbe, sinon la
 * spirale se lit comme un pattern dupliqué.</p>
 */
public final class WaterTerrainVfx {

    /** Vague qui pousse les blocs devant elle. */
    public static final int MODE_SURGE = 0;
    /** Tourbillon qui avale les blocs puis les recrache. */
    public static final int MODE_MAELSTROM = 1;

    private static final int MAX_SNAPSHOTS = 6;
    private static final int MAX_BLOCKS = 104;

    private record SnapshotKey(long x, long y, long z, int salt) { }

    /**
     * Bloc capturé + paramètres stables de sa trajectoire.
     *
     * @param bearing    relèvement réel du bloc autour de l'axe de l'effet
     * @param groundDist distance réelle du bloc à l'axe
     * @param along      projection signée sur l'axe de marche de la vague (−1 arrière … +1 avant)
     * @param pickup     instant, dans la vie de l'effet, où l'eau atteint enfin ce bloc
     * @param flight     durée de son voyage, indépendante de l'heure de ramassage
     */
    private record Debris(BlockState state, ModelData modelData, int light,
                          float surfaceY, float bearing, float groundDist, float along,
                          float lateral, float radius1, float size, float turns,
                          float flight, float pickup, float tumbleSpeed, float lift) { }

    private record Snapshot(ClientLevel level, int mode, float dirX, float dirZ,
                            float depth, List<Debris> debris) { }

    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new LinkedHashMap<>();
    /** Quaternion réutilisé : la passe de rendu ne doit rien allouer par bloc. */
    private static final org.joml.Quaternionf ROTATION = new org.joml.Quaternionf();
    private static ClientLevel trackedLevel;

    private WaterTerrainVfx() { }

    /** Purge quand le joueur change de monde (appelé chaque tick client par le manager). */
    public static void beginTerrainTick(ClientLevel level) {
        if (trackedLevel == level) return;
        trackedLevel = level;
        SNAPSHOTS.clear();
    }

    /**
     * Capture unique : les ticks suivants réutilisent le même « screen » de terrain.
     *
     * @param mode  {@link #MODE_SURGE} ou {@link #MODE_MAELSTROM}
     * @param depth profondeur du bassin (maelström) ou hauteur de crête (vague), en blocs
     */
    public static void prepare(ClientLevel level, double x, double y, double z, int salt,
                               int mode, float dirX, float dirZ, float depth) {
        if (level == null) return;
        beginTerrainTick(level);
        float len = Mth.sqrt(dirX * dirX + dirZ * dirZ);
        float ax = len < 0.001f ? 0.0f : dirX / len;
        float az = len < 0.001f ? 1.0f : dirZ / len;
        if (depth < 1.0f) depth = 3.0f;
        SnapshotKey key = key(x, y, z, salt);
        if (SNAPSHOTS.containsKey(key)) return;

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        int originY = Mth.floor(y);
        int radius = mode == MODE_MAELSTROM ? 5 : 4;
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
            // Le pourtour reste clairsemé : le sol d'origine doit rester lisible, sans cadre carré.
            if (dist > radius * 0.55f && h > 0.50f) continue;

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

            float h2 = hash(salt, 0x4C21 ^ (surface.getX() * 2654435761L));
            float h3 = hash(salt, 0x6B07 + surface.getZ() * 40503);
            float bearing = (float) Math.atan2(relZ, relX);
            // Coordonnées « vague » : le long de l'axe de marche, et perpendiculairement.
            float along = (float) (relX * ax + relZ * az);
            float lateral = (float) (-relX * az + relZ * ax);

            float pickup;
            float flight;
            if (mode == MODE_SURGE) {
                // Le front part de −3,4 et finit à +2,8 entre t = 0,12 et t = 0,58 : un bloc est
                // ramassé quand le mur arrive SUR LUI, jamais avant.
                float frontProgress = Mth.clamp((along + 3.40f) / 6.20f, 0.0f, 1.0f);
                pickup = Mth.clamp(0.12f + frontProgress * 0.46f + (h2 - 0.5f) * 0.05f, 0.0f, 0.74f);
                flight = 0.30f + h * 0.22f;
            } else {
                // Le tourbillon mord du centre vers l'extérieur, comme la tornade.
                pickup = Mth.clamp(0.05f + 0.52f * (dist / (radius + 0.5f)) + 0.10f * h2, 0.0f, 0.70f);
                flight = 0.34f + h * 0.26f;
            }
            float radius1 = mode == MODE_MAELSTROM
                    ? 0.22f + 0.70f * h3                                  // tout finit dans la gorge
                    : Mth.clamp(dist * 0.80f + h3 * 1.30f, 0.45f, 4.20f); // la vague disperse

            debris.add(new Debris(state, modelData, light, topY,
                    bearing, dist, along, lateral, radius1,
                    0.28f + h2 * 0.22f,
                    mode == MODE_MAELSTROM ? 1.30f + h3 * 1.10f : 0.35f + h3 * 0.55f,
                    flight, pickup,
                    1.30f + h3 * 2.70f,
                    0.70f + h * 0.60f));
            if (debris.size() >= MAX_BLOCKS) break capture;
        }
        if (debris.isEmpty()) return;
        SNAPSHOTS.put(key, new Snapshot(level, mode, ax, az, depth, List.copyOf(debris)));
        while (SNAPSHOTS.size() > MAX_SNAPSHOTS) {
            var it = SNAPSHOTS.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    /**
     * Réimprime les blocs capturés à leur place dans l'eau.
     *
     * @return {@code true} si au moins un bloc a été dessiné (le manager termine alors le batch)
     */
    public static boolean render(Minecraft mc, MultiBufferSource.BufferSource buffers, PoseStack pose,
                                 net.minecraft.world.phys.Vec3 camera,
                                 double x, double y, double z, int salt, float t) {
        ClientLevel level = mc.level;
        if (level == null || t <= 0.0f || t >= 1.0f) return false;
        Snapshot snapshot = SNAPSHOTS.get(key(x, y, z, salt));
        if (snapshot == null || snapshot.level() != level || snapshot.debris().isEmpty()) return false;

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        boolean maelstrom = snapshot.mode() == MODE_MAELSTROM;
        float ax = snapshot.dirX(), az = snapshot.dirZ();
        float depth = snapshot.depth();
        boolean emitted = false;
        for (Debris d : snapshot.debris()) {
            float u = (t - d.pickup()) / d.flight();
            if (u <= 0.0f) continue;
            // Un bloc qui a fini son voyage recommence : l'eau continue de travailler le terrain.
            u = u - Mth.floor(u);
            float tear = smooth(u / 0.18f);
            float ride = smooth((u - 0.12f) / 0.58f);
            float land = smooth((u - 0.84f) / 0.16f);

            float worldX;
            float worldZ;
            float dy;
            float tumble;
            if (maelstrom) {
                // Aspiration : le rayon migre de la distance réelle vers la gorge, l'angle s'enroule
                // et l'altitude DESCEND le long de la paroi — l'inverse exact de la tornade de sable.
                float pull = tear * (0.28f + 0.72f * ride);
                float angle = d.bearing() + pull * u * d.turns() * TAU;
                float target = d.radius1() * (0.85f + 0.26f * Mth.sin(u * 8.0f + d.bearing() * 5.0f));
                float radius = Mth.lerp(pull, d.groundDist(), target);
                worldX = (float) x + Mth.cos(angle) * radius;
                worldZ = (float) z + Mth.sin(angle) * radius;
                // Descente en cuvette, puis remontée brutale quand le geyser recrache (u > 0,88).
                float spit = smooth((u - 0.88f) / 0.12f);
                dy = tear * 0.10f - ride * depth * (0.55f + 0.45f * pull) * (1.0f - spit)
                        + spit * (6.40f * d.lift());
                tumble = tear * (u * 8.0f + d.bearing()) * d.tumbleSpeed();
            } else {
                // Poussée : le bloc part en avant avec le front, s'élève sur la crête, dérive et
                // retombe quand la vague l'a dépassé.
                float push = tear * (0.35f + 0.65f * ride);
                float along = d.along() + push * (2.30f + 2.40f * d.turns());
                float lateral = d.lateral() + push * (d.radius1() - d.groundDist()) * 0.35f
                        * (d.lateral() >= 0.0f ? 1.0f : -1.0f);
                worldX = (float) x + ax * along - az * lateral;
                worldZ = (float) z + az * along + ax * lateral;
                float crest = Mth.sin(Mth.clamp(ride, 0.0f, 1.0f) * (float) Math.PI);
                dy = tear * 0.12f + crest * depth * 0.46f * d.lift() - land * 0.40f;
                tumble = tear * (u * 5.0f + d.lateral()) * d.tumbleSpeed() * 0.75f;
            }

            float size = d.size() * (0.25f + 0.75f * tear) * (1.0f - land * 0.30f);
            if (size <= 0.05f) continue;

            pose.pushPose();
            pose.translate(worldX - camera.x, d.surfaceY() + dy - camera.y, worldZ - camera.z);
            ROTATION.identity()
                    .rotateY(tumble * 0.40f)
                    .rotateX(tumble * 0.80f)
                    .rotateZ(tumble * 0.52f);
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
