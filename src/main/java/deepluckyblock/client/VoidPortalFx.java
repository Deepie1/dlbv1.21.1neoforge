package deepluckyblock.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import deepluckyblock.DeepLuckyBlockMod;

/**
 * T390-VOID-PORTAL — la <b>fenêtre</b> : un disque face caméra, rempli par le core shader
 * {@code void_portal}, ouvert au cœur de chacune des cinq signatures du Vide.
 *
 * <h2>Ce que ça résout</h2>
 *
 * <p>{@link VoidSignatureVfx} applique une règle stricte : « on ne dessine jamais le vide, on
 * dessine son BORD ». L'intérieur de chaque figure est donc laissé rigoureusement vide — la
 * lèvre est l'objet. C'est juste, mais ça repose entièrement sur ce qu'il y a derrière :
 * devant un mur de pierre claire, l'anneau de la Porte d'Horizon encadre… de la pierre
 * claire. Le trou n'existe que si le décor derrière lui est sombre.</p>
 *
 * <p>Cette classe comble ce manque sans trahir la règle : elle ne dessine toujours pas de
 * matière, elle <b>remplace ce qu'on voit à travers</b>. Le disque est opaque, écrit la
 * profondeur, et affiche un ciel d'abysse en parallaxe. Le bord voxel reste l'objet ; la
 * fenêtre garantit seulement que derrière ce bord il y a l'abîme, et pas le mur du village.</p>
 *
 * <h2>Pourquoi une file, et pas un registre d'instances</h2>
 *
 * <p>Les cinq signatures sont déjà pilotées par {@code WeaponWorldVfxManager} : durée, phase,
 * atténuation, position. Dupliquer cette machinerie ici inviterait les deux horloges à
 * diverger. À la place, le rendu des signatures <b>dépose</b> une fenêtre par image via
 * {@link #queue}, et cette classe la consomme dans la même étape de rendu. Aucun état ne
 * survit à une image : rien à nettoyer quand le monde change, rien à resynchroniser.</p>
 *
 * <p>L'ordre d'appel des deux écouteurs {@code AFTER_PARTICLES} n'est pas garanti. Si
 * celui-ci passe en premier, il dessine la file de l'image précédente : un retard d'une
 * image sur un disque qui grandit en vingt, strictement invisible. C'est le seul couplage
 * accepté, et il est volontaire.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class VoidPortalFx {

    private VoidPortalFx() {}

    /** Au-delà, on laisse tomber : cinq signatures simultanées sont déjà une mêlée. */
    private static final int MAX_WINDOWS = 8;

    /** Découpage du disque. 22 secteurs : plus aucun angle visible, coût 22 quads. */
    private static final int SECTORS = 22;

    private static final double[] WX = new double[MAX_WINDOWS];
    private static final double[] WY = new double[MAX_WINDOWS];
    private static final double[] WZ = new double[MAX_WINDOWS];
    private static final float[] WR = new float[MAX_WINDOWS];
    private static int pending;

    // Rayon maximal par signature, en blocs. Chaque figure a sa propre emprise : la Porte
    // d'Horizon EST un portail (anneau à 2,12 blocs), la Faille n'est qu'une fente.
    private static final float[] MAX_RADIUS = {
            1.55f,  // WARP    — la lentille
            1.25f,  // PRISM   — le cœur du prisme
            1.05f,  // RIFT    — la fente, volontairement étroite
            1.95f,  // GATE    — le vrai portail, au ras de l'anneau
            1.35f   // LATTICE — le moyeu de la trame
    };

    // Hauteur du centre de la fenêtre au-dessus de l'origine de l'effet. Les cinq figures
    // n'ont pas le même centre de gravité : la lentille flotte, la faille rampe au sol, la
    // Porte s'élève jusqu'au tore haut. Une valeur unique poserait la fenêtre à côté du
    // bord censé l'encadrer, et c'est précisément le défaut qu'elle vient corriger.
    private static final float[] LIFT = {
            1.20f,  // WARP
            1.30f,  // PRISM
            0.85f,  // RIFT
            1.45f,  // GATE
            1.15f   // LATTICE
    };

    // =====================================================================
    // DÉPÔT (appelé par le rendu des signatures, une fois par image)
    // =====================================================================

    /**
     * Ouverture puis fermeture de la fenêtre, sur la même horloge {@code t} que la signature.
     *
     * <p>Deux fondus en {@code smoothstep} plutôt qu'un seul triangle : une fenêtre qui
     * s'ouvre linéairement « saute » à l'image 1, parce qu'un disque noir de 20 cm en plein
     * jour est déjà parfaitement visible. L'ouverture est aussi plus lente que la fermeture —
     * un trou dans le réel se déchire avec effort et se referme d'un coup.</p>
     *
     * @param variant une des constantes de {@link VoidSignatureVfx} (WARP…LATTICE)
     * @param t       progression de la signature, 0 → 1
     * @return le rayon en blocs, ou 0 si la fenêtre est fermée à cet instant
     */
    public static float radiusFor(int variant, float t) {
        if (t <= 0.06f || t >= 0.995f) return 0.0f;
        float open = smooth(Mth.clamp((t - 0.06f) / 0.26f, 0.0f, 1.0f));
        float shut = smooth(Mth.clamp((t - 0.84f) / 0.16f, 0.0f, 1.0f));
        int index = (variant >= 0 && variant < MAX_RADIUS.length) ? variant : 0;
        return MAX_RADIUS[index] * open * (1.0f - shut);
    }

    private static float smooth(float u) {
        return u * u * (3.0f - 2.0f * u);
    }

    /**
     * Point d'entrée unique du rendu des signatures : <b>une</b> ligne côté
     * {@code WeaponWorldVfxManager}, tout le réglage de la fenêtre ici.
     *
     * @param variant   constante de {@link VoidSignatureVfx} (WARP…LATTICE)
     * @param worldX,worldY,worldZ origine de l'effet dans le monde (le sol, pas le centre)
     * @param t         progression de la signature, 0 → 1
     */
    public static void queueSignature(int variant, double worldX, double worldY, double worldZ, float t) {
        float radius = radiusFor(variant, t);
        if (radius <= 0.02f) return;
        int index = (variant >= 0 && variant < LIFT.length) ? variant : 0;
        queue(worldX, worldY + LIFT[index], worldZ, radius);
    }

    /**
     * Dépose une fenêtre pour cette image. Coordonnées du <b>monde</b> (pas relatives à la
     * caméra) : l'écouteur ci-dessous n'a pas la même caméra sous la main au même instant.
     */
    public static void queue(double worldX, double worldY, double worldZ, float radius) {
        if (radius <= 0.02f || pending >= MAX_WINDOWS) return;
        WX[pending] = worldX;
        WY[pending] = worldY;
        WZ[pending] = worldZ;
        WR[pending] = radius;
        pending++;
    }

    // =====================================================================
    // RENDU
    // =====================================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        int count = pending;
        pending = 0;
        if (count <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Tant que les ressources n'ont pas été chargées une première fois, le fournisseur
        // rend null : dessiner ici remonterait en NPE dans RenderSystem.setShader.
        if (ModShaders.voidPortal() == null) return;

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f mat = event.getPoseStack().last().pose();

        // Base caméra du billboard. getLeftVector/getUpVector renvoient les vecteurs INTERNES
        // de la caméra : on recopie avant de toucher quoi que ce soit.
        Vector3f right = new Vector3f(camera.getLeftVector()).mul(-1.0f);
        Vector3f up = new Vector3f(camera.getUpVector());

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.voidPortal());
        Vector3f tmp = new Vector3f();

        for (int i = 0; i < count; i++) {
            float cx = (float) (WX[i] - cam.x);
            float cy = (float) (WY[i] - cam.y);
            float cz = (float) (WZ[i] - cam.z);
            disc(vc, mat, tmp, cx, cy, cz, WR[i], right, up);
        }

        buffers.endBatch(ModRenderTypes.voidPortal());
    }

    /**
     * Disque en éventail. Le mode QUADS impose quatre sommets : le dernier secteur est
     * <b>dédoublé</b> (p1 écrit deux fois), ce qui dégénère le second triangle du quad en
     * segment — invisible, et c'est la façon la moins chère d'obtenir un triangle dans un
     * tampon à quads sans ouvrir un second {@code RenderType}.
     */
    private static void disc(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                             float cx, float cy, float cz, float radius,
                             Vector3f right, Vector3f up) {
        float previousX = 0.0f;
        float previousY = 0.0f;
        float previousZ = 0.0f;
        for (int s = 0; s <= SECTORS; s++) {
            float angle = (float) (s * 2.0 * Math.PI / SECTORS);
            float ca = Mth.cos(angle) * radius;
            float sa = Mth.sin(angle) * radius;
            float px = cx + right.x() * ca + up.x() * sa;
            float py = cy + right.y() * ca + up.y() * sa;
            float pz = cz + right.z() * ca + up.z() * sa;
            if (s > 0) {
                put(vc, mat, tmp, cx, cy, cz);
                put(vc, mat, tmp, previousX, previousY, previousZ);
                put(vc, mat, tmp, px, py, pz);
                put(vc, mat, tmp, px, py, pz);
            }
            previousX = px;
            previousY = py;
            previousZ = pz;
        }
    }

    /** Format POSITION seul : aucune couleur, aucun UV. Tout vient du shader. */
    private static void put(VertexConsumer vc, Matrix4f mat, Vector3f tmp, float x, float y, float z) {
        mat.transformPosition(x, y, z, tmp);
        vc.addVertex(tmp.x(), tmp.y(), tmp.z());
    }
}
