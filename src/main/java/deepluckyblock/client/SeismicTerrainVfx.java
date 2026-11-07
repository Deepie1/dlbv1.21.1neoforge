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
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
 * supprimer le terrain original. Une fissure principale centrée et deux/trois branches pilotent
 * un champ continu d'inclinaison. Chaque colonne est articulée du bas plat vers son top écarté.
 * Un précalcul OBB/SAT résout les contacts importants entre voisins sans déplacer la base.
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
            float edgeFactor = edgeFactor(f, l, salt);
            // Le pourtour n'est pas réimprimé : le vrai sol reste seul visible, sans cadre carré.
            if (edgeFactor <= 0.0f) continue;

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
                        columnHash, depth, edgeFactor, 0.0f, 0.0f, 0.0f));
            }
            if (blocks.size() >= MAX_BLOCKS_PER_SNAPSHOT) break captureColumns;
        }

        TerrainSnapshot snapshot = new TerrainSnapshot(level, x, y, z, dirX, dirZ, worldScale,
                salt, List.copyOf(blocks), buildFractures(salt));
        snapshot = withCollisionCorrections(snapshot);
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
            BlockTransform transform = transformAt(snapshot, block, t);
            // Une colonne revenue à zéro n'est pas dessinée par-dessus le terrain d'origine.
            // Cela supprime aussi le carré fantôme autour de la zone capturée.
            if (transform.weight <= 0.0001f) continue;
            pose.pushPose();
            pose.translate(transform.x - camera.x, transform.y - camera.y, transform.z - camera.z);
            pose.mulPose(transform.rotation);
            pose.translate(-0.5f, -0.5f, -0.5f);
            // Modèle et dimensions exacts ; la rotation est limitée et transmise aux voisins.
            dispatcher.renderSingleBlock(block.state, pose, buffers, block.light,
                    OverlayTexture.NO_OVERLAY, block.modelData, null);
            pose.popPose();
            emitted = true;
        }
        return emitted;
    }

    /** Colonne articulée du bas vers le haut : base plane, tops écartés, voisinage continu. */
    private static BlockTransform transformAt(TerrainSnapshot snapshot, CapturedBlock block, float t) {
        Deformation deformation = deformationAt(snapshot, block, t);
        float dirX = snapshot.dirX * deformation.dirF - snapshot.dirZ * deformation.dirL;
        float dirZ = snapshot.dirZ * deformation.dirF + snapshot.dirX * deformation.dirL;
        float topAngle = deformation.angle;
        float rise = deformation.rise;

        float x = block.pos.getX() + 0.5f;
        float y = block.pos.getY() + block.columnDepth - (COLUMN_DEPTH - 1) + 0.5f + rise;
        float z = block.pos.getZ() + 0.5f;
        float targetAngle = topAngle * tiltFactor(block.columnDepth);
        for (int layer = COLUMN_DEPTH - 2; layer >= block.columnDepth; layer--) {
            float belowAngle = topAngle * tiltFactor(layer + 1);
            float currentAngle = topAngle * tiltFactor(layer);
            x += (Mth.sin(belowAngle) + Mth.sin(currentAngle)) * 0.5f * dirX;
            y += (Mth.cos(belowAngle) + Mth.cos(currentAngle)) * 0.5f;
            z += (Mth.sin(belowAngle) + Mth.sin(currentAngle)) * 0.5f * dirZ;
        }
        Quaternionf rotation = targetAngle <= 0.0001f ? new Quaternionf()
                : new Quaternionf().setAngleAxis(targetAngle, dirZ, 0.0f, -dirX);
        x += block.collisionX * deformation.envelope;
        y += block.collisionY * deformation.envelope;
        z += block.collisionZ * deformation.envelope;
        return new BlockTransform(x, y + REPRINT_EPSILON * deformation.weight, z, rotation,
                deformation.weight);
    }

    private static float tiltFactor(int depth) {
        return switch (depth) {
            case 0 -> 1.0f;
            case 1 -> 0.58f;
            case 2 -> 0.24f;
            default -> 0.0f;
        };
    }

    private static Deformation deformationAt(TerrainSnapshot snapshot, CapturedBlock block, float t) {
        float f = block.f, l = block.l, h = block.hash;
        FractureInfluence nearest = nearestFracture(snapshot.fractures, f, l);
        float start = nearest.fracture.delay + clamp(nearest.u / nearest.fracture.length) * 0.045f + h * 0.014f;
        float closure = 1.0f - smooth((t - 0.825f - h * 0.018f) / 0.145f);
        float envelope = smooth((t - start) / 0.135f) * closure;

        float vectorF = 0.0f, vectorL = 0.0f, uplift = 0.0f, activity = 0.0f;
        for (Fracture fracture : snapshot.fractures) {
            FractureInfluence influence = influenceOn(fracture, f, l);
            float range = fracture.generation == 0 ? 2.80f : 1.55f;
            float weight = 1.0f - smooth(influence.distance / range);
            if (weight <= 0.0f) continue;
            float side = Math.abs(influence.signedDistance) < 0.0001f
                    ? (h < 0.5f ? -1.0f : 1.0f) : (influence.signedDistance < 0.0f ? -1.0f : 1.0f);
            float strength = weight * fracture.depth * (fracture.generation == 0 ? 1.0f : 0.62f);
            vectorF += -Mth.sin(fracture.angle) * side * strength;
            vectorL += Mth.cos(fracture.angle) * side * strength;
            uplift += weight * weight * fracture.depth * (fracture.generation == 0 ? 0.38f : 0.16f);
            activity = Math.max(activity, weight);
        }
        float magnitude = Mth.sqrt(vectorF * vectorF + vectorL * vectorL);
        float dirF = magnitude > 0.0001f ? vectorF / magnitude : 0.0f;
        float dirL = magnitude > 0.0001f ? vectorL / magnitude : 0.0f;
        float angle = Math.min(0.38f, magnitude * 0.34f) * envelope;

        float radial = Mth.sqrt(f * f + l * l);
        float p = damped(t - 0.018f - radial * 0.0045f, 96.0f, 49.0f) * 0.024f;
        float shear = damped(t - 0.145f - radial * 0.0080f, 47.0f, 15.0f) * 0.018f;
        float attenuation = 1.0f / (1.0f + radial * 0.18f);
        float rayleigh = 0.0f;
        for (int wave = 0; wave < 3; wave++) {
            float arrival = 0.285f + wave * 0.155f + radial * (0.021f - wave * 0.002f);
            rayleigh += damped(t - arrival, 27.0f - wave * 3.0f, 10.5f + wave * 2.0f)
                    * (0.075f - wave * 0.012f) * attenuation;
        }
        float aftershocks = damped(t - 0.515f - h * 0.025f, 39.0f, 17.0f) * 0.040f * activity
                + damped(t - 0.690f - h * 0.018f, 44.0f, 20.0f) * 0.028f * activity;
        float edge = block.edgeFactor;
        float rise = (envelope * Math.min(0.72f, uplift)
                + closure * ((p + shear + rayleigh) * activity + aftershocks)) * edge;
        angle *= edge;
        envelope *= edge;
        rise = Math.max(0.0f, rise * snapshot.worldScale);
        float weight = Math.max(activity * envelope, Math.max(angle * 4.0f, rise * 20.0f));
        return new Deformation(dirF, dirL, angle, rise, envelope, weight);
    }

    /** Une grande fissure centrée sur le mob, puis deux ou trois filles aléatoires. */
    private static List<Fracture> buildFractures(int salt) {
        float mainAngle = hash(salt, 2000) * (float) Math.PI * 2.0f;
        float mainLength = 6.20f + hash(salt, 2001) * 1.40f;
        float dx = Mth.cos(mainAngle), dl = Mth.sin(mainAngle);
        List<Fracture> out = new ArrayList<>(4);
        Fracture main = new Fracture(0, -dx * mainLength * 0.5f, -dl * mainLength * 0.5f,
                mainAngle, mainLength, 0.32f, 1.0f, 0.055f, 0.0f, salt + 2100, 0);
        out.add(main);
        int branches = 2 + (hash(salt, 2002) >= 0.5f ? 1 : 0);
        for (int branch = 0; branch < branches; branch++) {
            float at = 0.30f + branch * (0.40f / (branches - 1))
                    + (hash(salt, 2010 + branch * 41) - 0.5f) * 0.08f;
            float[] origin = pointOn(main, at);
            float side = hash(salt, 2011 + branch * 43) < 0.5f ? -1.0f : 1.0f;
            float angle = mainAngle + side * (0.70f + hash(salt, 2012 + branch * 47) * 1.10f)
                    + (hash(salt, 2013 + branch * 53) - 0.5f) * 0.30f;
            out.add(new Fracture(out.size(), origin[0], origin[1], angle,
                    1.35f + hash(salt, 2014 + branch * 59) * 1.60f,
                    0.16f + hash(salt, 2015 + branch * 61) * 0.09f,
                    0.38f + hash(salt, 2016 + branch * 67) * 0.34f,
                    0.10f + branch * 0.035f + hash(salt, 2017 + branch * 71) * 0.025f,
                    (hash(salt, 2018 + branch * 73) - 0.5f) * 0.38f,
                    salt + 2200 + branch * 101, 1));
        }
        return List.copyOf(out);
    }

    /** Précalcul unique à ouverture maximale ; seules les pénétrations visibles sont corrigées. */
    private static TerrainSnapshot withCollisionCorrections(TerrainSnapshot snapshot) {
        int size = snapshot.blocks.size();
        BlockTransform[] base = new BlockTransform[size];
        CollisionBox[] boxes = new CollisionBox[size];
        for (int i = 0; i < size; i++) {
            base[i] = transformAt(snapshot, snapshot.blocks.get(i), 0.45f);
            boxes[i] = new CollisionBox(base[i]);
        }
        for (int iteration = 0; iteration < 12; iteration++) {
            for (int i = 0; i < size; i++) {
                CapturedBlock aBlock = snapshot.blocks.get(i);
                if (base[i].weight <= 0.0001f) continue;
                for (int j = i + 1; j < size; j++) {
                    if (base[j].weight <= 0.0001f) continue;
                    CapturedBlock bBlock = snapshot.blocks.get(j);
                    if (Math.abs(aBlock.f - bBlock.f) > 1.6f || Math.abs(aBlock.l - bBlock.l) > 1.6f) continue;
                    Overlap overlap = collisionOverlap(boxes[i], boxes[j]);
                    if (overlap == null || overlap.depth < 0.08f) continue;
                    float aWeight = aBlock.columnDepth == COLUMN_DEPTH - 1 ? 0.0f : aBlock.edgeFactor;
                    float bWeight = bBlock.columnDepth == COLUMN_DEPTH - 1 ? 0.0f : bBlock.edgeFactor;
                    float total = aWeight + bWeight;
                    if (total <= 0.0f) continue;
                    float amount = Math.min(0.10f, overlap.depth + 0.0001f);
                    boxes[i].move(-overlap.axis.x() * amount * aWeight / total,
                            -overlap.axis.y() * amount * aWeight / total,
                            -overlap.axis.z() * amount * aWeight / total);
                    boxes[j].move(overlap.axis.x() * amount * bWeight / total,
                            overlap.axis.y() * amount * bWeight / total,
                            overlap.axis.z() * amount * bWeight / total);
                }
            }
        }
        List<CapturedBlock> corrected = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CapturedBlock block = snapshot.blocks.get(i);
            corrected.add(new CapturedBlock(block.pos, block.state, block.modelData, block.light,
                    block.f, block.l, block.hash, block.columnDepth, block.edgeFactor,
                    (boxes[i].x - base[i].x) / block.edgeFactor,
                    (boxes[i].y - base[i].y) / block.edgeFactor,
                    (boxes[i].z - base[i].z) / block.edgeFactor));
        }
        return new TerrainSnapshot(snapshot.level, snapshot.x, snapshot.y, snapshot.z,
                snapshot.dirX, snapshot.dirZ, snapshot.worldScale, snapshot.salt,
                List.copyOf(corrected), snapshot.fractures);
    }

    private static Overlap collisionOverlap(CollisionBox a, CollisionBox b) {
        Vector3f delta = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z);
        float best = Float.MAX_VALUE;
        Vector3f bestAxis = null;
        for (int i = 0; i < 3; i++) {
            Overlap hit = penetrationAxis(a, b, delta, a.axes[i], best, bestAxis);
            if (hit == null) return null;
            best = hit.depth;
            bestAxis = hit.axis;
            hit = penetrationAxis(a, b, delta, b.axes[i], best, bestAxis);
            if (hit == null) return null;
            best = hit.depth;
            bestAxis = hit.axis;
        }
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            Vector3f cross = new Vector3f(a.axes[i]).cross(b.axes[j]);
            if (cross.lengthSquared() < 1.0E-8f) continue;
            cross.normalize();
            Overlap hit = penetrationAxis(a, b, delta, cross, best, bestAxis);
            if (hit == null) return null;
            best = hit.depth;
            bestAxis = hit.axis;
        }
        return bestAxis == null ? null : new Overlap(best, bestAxis);
    }

    private static Overlap penetrationAxis(CollisionBox a, CollisionBox b, Vector3f delta,
                                           Vector3f axis, float best, Vector3f bestAxis) {
        float radiusA = 0.5f * (Math.abs(a.axes[0].dot(axis)) + Math.abs(a.axes[1].dot(axis))
                + Math.abs(a.axes[2].dot(axis)));
        float radiusB = 0.5f * (Math.abs(b.axes[0].dot(axis)) + Math.abs(b.axes[1].dot(axis))
                + Math.abs(b.axes[2].dot(axis)));
        float distance = delta.dot(axis);
        float penetration = radiusA + radiusB - Math.abs(distance);
        if (penetration <= 0.00001f) return null;
        if (penetration >= best) return new Overlap(best, bestAxis);
        Vector3f oriented = distance < 0.0f ? new Vector3f(axis).negate() : new Vector3f(axis);
        return new Overlap(penetration, oriented);
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
            FractureInfluence candidate = influenceOn(fracture, f, l);
            float score = candidate.distance - candidate.width;
            if (score >= bestScore) continue;
            best = candidate;
            bestScore = score;
        }
        return best;
    }

    private static FractureInfluence influenceOn(Fracture fracture, float f, float l) {
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
        boolean inside = rawU >= 0.0f && rawU <= fracture.length && Math.abs(signed) < width;
        return new FractureInfluence(fracture, u, signed, distance, width, inside);
    }

    /** Empreinte asymétrique : déformation nulle bien avant la limite de capture. */
    private static float edgeFactor(float f, float l, int salt) {
        float asymmetricF = f + 0.26f * l;
        float asymmetricL = l - 0.19f * f;
        float radial = Mth.sqrt((asymmetricF / 5.25f) * (asymmetricF / 5.25f)
                + (asymmetricL / 4.75f) * (asymmetricL / 4.75f));
        float boundary = 1.0f
                + 0.16f * (noise1(f * 0.57f + l * 0.31f, salt + 3100) - 0.5f)
                + 0.06f * Mth.sin(f * 0.83f - l * 0.47f);
        float ratio = radial / boundary;
        return 1.0f - smooth((ratio - 0.52f) / 0.30f);
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
                                 int light, float f, float l, float hash, int columnDepth,
                                 float edgeFactor, float collisionX, float collisionY, float collisionZ) { }
    private record TerrainSnapshot(ClientLevel level, double x, double y, double z,
                                   float dirX, float dirZ, float worldScale, int salt,
                                   List<CapturedBlock> blocks, List<Fracture> fractures) { }
    private record Fracture(int id, float startF, float startL, float angle, float length,
                            float width, float depth, float delay, float curvature,
                            int seed, int generation) { }
    private record FractureInfluence(Fracture fracture, float u, float signedDistance,
                                     float distance, float width, boolean inside) { }
    private static final class CollisionBox {
        float x, y, z;
        final Vector3f[] axes;
        CollisionBox(BlockTransform transform) {
            this.x = transform.x;
            this.y = transform.y;
            this.z = transform.z;
            this.axes = new Vector3f[]{new Vector3f(1.0f, 0.0f, 0.0f).rotate(transform.rotation),
                    new Vector3f(0.0f, 1.0f, 0.0f).rotate(transform.rotation),
                    new Vector3f(0.0f, 0.0f, 1.0f).rotate(transform.rotation)};
        }
        void move(float dx, float dy, float dz) { this.x += dx; this.y += dy; this.z += dz; }
    }
    private record Overlap(float depth, Vector3f axis) { }
    private record Deformation(float dirF, float dirL, float angle, float rise,
                               float envelope, float weight) { }
    private record BlockTransform(float x, float y, float z, Quaternionf rotation, float weight) { }
}
