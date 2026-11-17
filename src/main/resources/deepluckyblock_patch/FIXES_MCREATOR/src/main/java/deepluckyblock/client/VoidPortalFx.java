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
 * T390-VOID-PORTAL — la <b>fenêtre</b> : une ellipse remplie par le core shader
 * {@code void_portal}, ouverte dans l'aire que la figure encadre.
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
 * matière, elle <b>remplace ce qu'on voit à travers</b>. La surface est opaque, écrit la
 * profondeur, et affiche un ciel d'abysse en parallaxe. Le bord voxel reste l'objet ; la
 * fenêtre garantit seulement que derrière ce bord il y a l'abîme, et pas le mur du village.</p>
 *
 * <h2>Trois fenêtres, pas cinq — et aucune en panneau-réclame par défaut</h2>
 *
 * <p>Première version (T390) : un disque face caméra de rayon fixe au cœur des cinq
 * signatures. L'aperçu l'a mise en défaut avant le jeu, et sur les deux points à la fois.</p>
 *
 * <p><b>La taille.</b> Les rayons venaient de l'emprise de la figure et non de son
 * OUVERTURE : la sphère a un horizon de 1,10 et recevait une fenêtre de 1,55. Un disque qui
 * déborde de la lèvre cesse d'être un trou — il redevient un objet, avec sa silhouette
 * circulaire à lui, c'est-à-dire exactement ce que la règle du bord interdit. Les trois
 * fenêtres lisent donc maintenant les cotes de la figure elle-même
 * ({@link VoidSignatureVfx#warpHorizon}, {@link VoidSignatureVfx#riftGape},
 * {@link VoidSignatureVfx#gateRing}) et se tiennent en deçà.</p>
 *
 * <p><b>L'orientation.</b> Un panneau-réclame ne vaut que pour une figure dont l'ouverture
 * fait face au joueur d'où qu'il regarde, et il n'y en a qu'une : la sphère. La Porte est un
 * anneau POSÉ AU SOL — son trou est horizontal, et un disque vertical planté dedans lisait
 * comme une bulle flottante. La Faille est une fente VERTICALE orientée par l'attaque : son
 * ouverture est dans le plan des deux lèvres, et vue par la tranche elle doit disparaître,
 * exactement comme la fente.</p>
 *
 * <p><b>Le Prisme et la Trame n'en ont pas, et c'est un choix.</b> La fenêtre répare les
 * figures qui encadrent un GRAND VIDE. Le prisme est un octaèdre en fil de fer habité par
 * son cœur et sept faisceaux ; la trame est un réseau d'arêtes sans intérieur. Leur ajouter
 * un disque, c'était poser un trou là où aucune lèvre ne le justifie — et retomber sur la
 * silhouette circulaire parasite. Une ligne fine se lit sur n'importe quel fond ; c'est
 * l'aire vide qui avait besoin d'être comblée.</p>
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
 * image sur une ouverture qui grandit en vingt, strictement invisible. C'est le seul
 * couplage accepté, et il est volontaire.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class VoidPortalFx {

    private VoidPortalFx() {}

    /** Au-delà, on laisse tomber : cinq signatures simultanées sont déjà une mêlée. */
    private static final int MAX_WINDOWS = 8;

    /** Découpage du contour. 22 secteurs : plus aucun angle visible, coût 22 quads. */
    private static final int SECTORS = 22;

    // --- Les trois plans possibles ---------------------------------------------------
    /** Face caméra. Réservé à la sphère, seule figure dont l'ouverture suit le regard. */
    private static final int MODE_BILLBOARD = 0;
    /** Horizontal, dans le plan du sol. La Porte : son anneau est posé par terre. */
    private static final int MODE_GROUND = 1;
    /** Vertical, dans le plan des deux lèvres. La Faille : invisible par la tranche. */
    private static final int MODE_FISSURE = 2;

    private static final double[] WX = new double[MAX_WINDOWS];
    private static final double[] WY = new double[MAX_WINDOWS];
    private static final double[] WZ = new double[MAX_WINDOWS];
    /** Demi-étendue le long du premier vecteur de base. */
    private static final float[] WA = new float[MAX_WINDOWS];
    /** Demi-étendue le long du second. Différente de {@link #WA} : une fente n'est pas ronde. */
    private static final float[] WB = new float[MAX_WINDOWS];
    private static final int[] WMODE = new int[MAX_WINDOWS];
    /** Axe attaquant → cible, pour le seul mode {@link #MODE_FISSURE}. */
    private static final float[] WDX = new float[MAX_WINDOWS];
    private static final float[] WDZ = new float[MAX_WINDOWS];
    private static int pending;

    // =====================================================================
    // DÉPÔT (appelé par le rendu des signatures, une fois par image)
    // =====================================================================

    /**
     * Point d'entrée unique du rendu des signatures : <b>une</b> ligne côté
     * {@code WeaponWorldVfxManager}, tout le réglage de la fenêtre ici.
     *
     * <p>Les marges (0,94 pour la sphère, 0,80 pour la faille, 0,88 pour la porte) ne sont
     * pas décoratives : ce sont elles qui garantissent que le contour de la fenêtre tombe
     * À L'INTÉRIEUR de la lèvre, donc qu'on ne voie jamais sa silhouette propre.</p>
     *
     * @param variant une des constantes de {@link VoidSignatureVfx} (WARP…LATTICE)
     * @param worldX,worldY,worldZ origine de l'effet dans le monde (le sol, pas le centre)
     * @param t       progression de la signature, 0 → 1
     * @param dx,dz   direction unitaire ATTAQUANT → CIBLE, comme pour le rendu voxel
     * @param groundOffset écart entre la surface réelle et le centre de l'effet
     */
    public static void queueSignature(int variant, double worldX, double worldY, double worldZ,
                                      float t, float dx, float dz, float groundOffset) {
        if (t <= 0.0f || t >= 1.0f) return;
        float ground = VoidSignatureVfx.groundClamp(groundOffset);

        if (variant == VoidSignatureVfx.WARP) {
            // L'horizon de la sphère, moins six pour cent : les trois grands cercles
            // restent visibles tout autour de la fenêtre, et ce sont eux la figure.
            float r = VoidSignatureVfx.warpHorizon(t) * 0.94f;
            if (r < 0.10f) return;
            queue(MODE_BILLBOARD, worldX, worldY + VoidSignatureVfx.WARP_YC, worldZ, r, r, 1.0f, 0.0f);

        } else if (variant == VoidSignatureVfx.RIFT) {
            // La fente. Sa LARGEUR suit l'écartement des lèvres — quand la faille n'est
            // qu'une craquelure, la fenêtre est un fil ; quand elle bâille, c'est une
            // amande. Sa HAUTEUR, elle, ne bouge pas : les lèvres courent sur toute la
            // hauteur dès la première image, seul l'écartement raconte l'ouverture.
            float gape = VoidSignatureVfx.riftGape(t);
            float halfWidth = VoidSignatureVfx.RIFT_LIP * gape * 0.80f;
            if (halfWidth < 0.04f) return;
            float halfHeight = VoidSignatureVfx.RIFT_HEIGHT * 0.44f;
            double centre = worldY + ground + 0.05 + VoidSignatureVfx.RIFT_HEIGHT * 0.50;
            queue(MODE_FISSURE, worldX, centre, worldZ, halfWidth, halfHeight, dx, dz);

        } else if (variant == VoidSignatureVfx.GATE) {
            // La mare. Douze pour cent de marge sous l'anneau : le tracé au sol et le tore
            // du bas restent entièrement lisibles autour du trou.
            float r = VoidSignatureVfx.gateRing(t) * 0.88f;
            if (r < 0.12f) return;
            // Six centimètres au-dessus du plancher de la figure : assez pour ne pas se
            // battre avec le tracé au sol dans le tampon de profondeur, assez peu pour que
            // la mare ait l'air d'être le sol lui-même.
            queue(MODE_GROUND, worldX, worldY + ground + 0.06, worldZ, r, r, 1.0f, 0.0f);
        }
        // PRISM et LATTICE : rien. Voir l'en-tête de classe — ce sont des figures sans
        // intérieur, et un trou sans lèvre pour l'encadrer est juste un disque noir.
    }

    /**
     * Dépose une fenêtre pour cette image. Coordonnées du <b>monde</b> (pas relatives à la
     * caméra) : l'écouteur ci-dessous n'a pas la même caméra sous la main au même instant.
     */
    public static void queue(int mode, double worldX, double worldY, double worldZ,
                             float halfA, float halfB, float dx, float dz) {
        if (halfA <= 0.02f || halfB <= 0.02f || pending >= MAX_WINDOWS) return;
        WMODE[pending] = mode;
        WX[pending] = worldX;
        WY[pending] = worldY;
        WZ[pending] = worldZ;
        WA[pending] = halfA;
        WB[pending] = halfB;
        WDX[pending] = dx;
        WDZ[pending] = dz;
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

        // Base caméra du panneau-réclame. getLeftVector/getUpVector renvoient les vecteurs
        // INTERNES de la caméra : on recopie avant de toucher quoi que ce soit.
        Vector3f camRight = new Vector3f(camera.getLeftVector()).mul(-1.0f);
        Vector3f camUp = new Vector3f(camera.getUpVector());

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(ModRenderTypes.voidPortal());
        Vector3f tmp = new Vector3f();
        Vector3f axisA = new Vector3f();
        Vector3f axisB = new Vector3f();

        for (int i = 0; i < count; i++) {
            switch (WMODE[i]) {
                case MODE_GROUND:
                    // Horizontal. La mare de la Porte : vue de haut c'est un disque, vue
                    // de l'œil d'un joueur debout c'est l'ellipse écrasée qu'on attend
                    // d'un trou dans le sol.
                    axisA.set(1.0f, 0.0f, 0.0f);
                    axisB.set(0.0f, 0.0f, 1.0f);
                    break;
                case MODE_FISSURE:
                    // Vertical, dans le plan des deux lèvres. Le premier axe est le
                    // LATÉRAL (perpendiculaire à l'attaque) : c'est la direction dans
                    // laquelle la faille s'écarte. Vue dans l'axe de l'attaque, la fenêtre
                    // s'efface — ce qui est la bonne réponse, la fente aussi.
                    axisA.set(-WDZ[i], 0.0f, WDX[i]);
                    if (axisA.lengthSquared() < 1.0e-6f) axisA.set(1.0f, 0.0f, 0.0f);
                    axisA.normalize();
                    axisB.set(0.0f, 1.0f, 0.0f);
                    break;
                default:
                    axisA.set(camRight);
                    axisB.set(camUp);
                    break;
            }
            float cx = (float) (WX[i] - cam.x);
            float cy = (float) (WY[i] - cam.y);
            float cz = (float) (WZ[i] - cam.z);
            ellipse(vc, mat, tmp, cx, cy, cz, WA[i], WB[i], axisA, axisB);
        }

        buffers.endBatch(ModRenderTypes.voidPortal());
    }

    /**
     * Ellipse en éventail. Le mode QUADS impose quatre sommets : le dernier secteur est
     * <b>dédoublé</b> (p1 écrit deux fois), ce qui dégénère le second triangle du quad en
     * segment — invisible, et c'est la façon la moins chère d'obtenir un triangle dans un
     * tampon à quads sans ouvrir un second {@code RenderType}.
     */
    private static void ellipse(VertexConsumer vc, Matrix4f mat, Vector3f tmp,
                                float cx, float cy, float cz, float halfA, float halfB,
                                Vector3f axisA, Vector3f axisB) {
        float previousX = 0.0f;
        float previousY = 0.0f;
        float previousZ = 0.0f;
        for (int s = 0; s <= SECTORS; s++) {
            float angle = (float) (s * 2.0 * Math.PI / SECTORS);
            float ca = Mth.cos(angle) * halfA;
            float sa = Mth.sin(angle) * halfB;
            float px = cx + axisA.x() * ca + axisB.x() * sa;
            float py = cy + axisA.y() * ca + axisB.y() * sa;
            float pz = cz + axisA.z() * ca + axisB.z() * sa;
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
