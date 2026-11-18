package deepluckyblock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import deepluckyblock.DeepLuckyBlockMod;

import org.joml.Matrix4f;

/**
 * T397-PIXEL3D — le gestionnaire de <b>Pixels 3D</b> : plusieurs milliers de cubes
 * miniatures par seconde, un seul lot de rendu, zéro entité, zéro particule vanilla.
 *
 * <h2>Pourquoi des tableaux primitifs et pas une liste d'objets</h2>
 *
 * <p>Un système qui crée « plusieurs milliers de cubes par seconde » crée aussi plusieurs
 * milliers d'<b>objets</b> par seconde si on le laisse faire. À 4 000 pixels/s et une
 * seconde de vie, c'est 4 000 instances vivantes en permanence et 4 000 abandonnées par
 * seconde : le ramasse-miettes passe toutes les quelques secondes, et chaque passage est
 * une saccade. Dix tableaux de {@code float} alloués une fois pour toutes ne produisent
 * <b>aucun déchet</b>, jamais — le coût d'un pixel se réduit à dix écritures dans des
 * tableaux déjà chauds en cache.</p>
 *
 * <p>Le retrait suit la même logique : pas de décalage ni de trous, on remplace le mort par
 * le dernier vivant et on décrémente le compteur. C'est O(1), et l'ordre n'a aucune
 * importance puisque la passe de rendu est additive sur la profondeur.</p>
 *
 * <h2>Pourquoi un seul lot</h2>
 *
 * <p>Le coût dominant du rendu n'est pas le nombre de triangles mais le nombre d'<b>appels
 * de dessin</b>. Six faces par cube font 24 sommets ; 4 000 cubes font 96 000 sommets, ce
 * qu'une carte graphique de 2010 avale sans broncher — à condition que ce soit en UNE passe.
 * Les mêmes 4 000 cubes en 4 000 appels mettent n'importe quelle machine à genoux. Tout
 * passe donc par un {@code RenderType} unique, vidé d'un coup en fin de passe.</p>
 *
 * <h2>Ce que le CPU ne fait jamais</h2>
 *
 * <p>Aucune conversion de couleur. Le processeur écrit par cube une <b>phase</b> (un
 * scalaire), un numéro de mode, une teinte imposée et un fondu ; la roue chromatique, le
 * dégradé des arêtes et le grain du cœur sont intégralement calculés par le fragment
 * shader. Voir {@code void_pixel.fsh}.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class VoidPixelManager {

    private VoidPixelManager() {}

    // =====================================================================
    //  CAPACITÉ
    // =====================================================================

    /**
     * Plafond du vivier. Douze mille cubes représentent 288 000 sommets : c'est le point
     * où une carte d'entrée de gamme commence à sentir la passe translucide, et c'est très
     * au-delà de ce qu'une signature demande (la plus dense en réclame environ 2 600).
     * Un plafond franc vaut mieux qu'une chute de cadence : au-delà, {@link #spawnPixel}
     * refuse en silence.
     */
    private static final int MAX = 12000;

    /**
     * Distance de rendu, au carré. Un cube de huit centimètres à soixante mètres couvre
     * moins d'un pixel d'écran : le dessiner coûte un quad complet pour un scintillement.
     * Quarante-huit blocs est la distance où ses arêtes restent lisibles.
     */
    private static final double MAX_DIST_SQR = 48.0 * 48.0;

    /** Arêtes arc-en-ciel : cyan, magenta, violet, bleu en onde continue. */
    public static final int MODE_RAINBOW = 0;
    /** Arêtes d'une teinte imposée, dont seul l'éclat pulse. Pour les détails colorés. */
    public static final int MODE_TINTED = 1;
    /** Arêtes en luminance seule. Pour les détails qui ne doivent pas tirer l'œil. */
    public static final int MODE_MONO = 2;

    // =====================================================================
    //  LE VIVIER — dix tableaux parallèles, aucune allocation après le chargement
    // =====================================================================

    private static final double[] PX = new double[MAX];
    private static final double[] PY = new double[MAX];
    private static final double[] PZ = new double[MAX];
    private static final float[] VX = new float[MAX];
    private static final float[] VY = new float[MAX];
    private static final float[] VZ = new float[MAX];
    private static final float[] SIZE = new float[MAX];
    private static final float[] SPIN = new float[MAX];
    private static final short[] AGE = new short[MAX];
    private static final short[] MAX_AGE = new short[MAX];
    private static final byte[] MODE = new byte[MAX];
    private static final byte[] HUE = new byte[MAX];
    /** Traînée gravitationnelle par pixel : 0 = flotte, 1 = tombe comme une pierre. */
    private static final float[] GRAV = new float[MAX];

    private static int count = 0;

    /** Instant du dernier tick client, pour interpoler le rendu entre deux états. */
    private static long lastTickNanos = System.nanoTime();

    /**
     * Dimension du dernier tick. Sans elle, les cubes d'un combat au Nether resteraient en
     * mémoire au retour dans l'Overworld et réapparaîtraient aux mêmes coordonnées, dans
     * un monde qui n'a rien à voir. Le niveau n'est pas remis à {@code null} lors d'un
     * transit par portail, donc le garde-fou {@code mc.level == null} ne suffit pas : c'est
     * la clé de dimension qu'il faut comparer.
     */
    private static net.minecraft.resources.ResourceLocation lastDimension = null;

    // =====================================================================
    //  API PUBLIQUE
    // =====================================================================

    /**
     * Fait naître un Pixel 3D. Signature demandée, à l'unité près.
     *
     * @param size     arête du cube en blocs ; la plage utile va de 0,08 à 0,20
     * @param lifetimeTicks durée de vie ; au-delà de 32 767 le compteur déborderait, donc borné
     */
    public static void spawnPixel(double x, double y, double z,
                                  double vx, double vy, double vz,
                                  float size, int lifetimeTicks) {
        spawnPixel(x, y, z, vx, vy, vz, size, lifetimeTicks, MODE_RAINBOW, 0.0f, 0.0f);
    }

    /**
     * Variante complète.
     *
     * @param mode    {@link #MODE_RAINBOW}, {@link #MODE_TINTED} ou {@link #MODE_MONO}
     * @param hue     teinte 0..1 du mode {@link #MODE_TINTED}, ignorée sinon
     * @param gravity 0 pour un cube qui flotte, 1 pour un cube qui tombe
     */
    public static void spawnPixel(double x, double y, double z,
                                  double vx, double vy, double vz,
                                  float size, int lifetimeTicks,
                                  int mode, float hue, float gravity) {
        if (count >= MAX || lifetimeTicks <= 0 || size <= 0.001f) return;
        int i = count++;
        PX[i] = x; PY[i] = y; PZ[i] = z;
        VX[i] = (float) vx; VY[i] = (float) vy; VZ[i] = (float) vz;
        SIZE[i] = size;
        // Une rotation propre à chaque cube, tirée de sa position de naissance : deux
        // cubes voisins ne doivent pas présenter la même face à la caméra, sinon une
        // gerbe ressemble à une grille.
        SPIN[i] = (float) ((x * 12.9898 + y * 78.233 + z * 37.719) % 6.2831853);
        AGE[i] = 0;
        MAX_AGE[i] = (short) Math.min(32000, lifetimeTicks);
        MODE[i] = (byte) mode;
        HUE[i] = (byte) Math.round(Math.max(0.0f, Math.min(1.0f, hue)) * 255.0f);
        GRAV[i] = gravity;
    }

    /** Combien de cubes vivent en ce moment. Utile aux bancs et au diagnostic. */
    public static int alive() {
        return count;
    }

    /** Vide le vivier. Appelé au changement de dimension et à la déconnexion. */
    public static void clear() {
        count = 0;
    }

    // =====================================================================
    //  PHYSIQUE
    // =====================================================================

    /**
     * Un tick de monde, pas un tick de rendu : la physique avance au rythme du jeu, le
     * rendu interpole entre deux états. Mettre la physique dans la boucle de rendu la
     * rendrait dépendante de la cadence — les cubes tomberaient deux fois plus vite à
     * 120 images par seconde qu'à 60.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { count = 0; lastDimension = null; return; }
        net.minecraft.resources.ResourceLocation dim = mc.level.dimension().location();
        if (lastDimension != null && !lastDimension.equals(dim)) clear();
        lastDimension = dim;
        // L'horloge de sous-frame est posée ICI et nulle part ailleurs : c'est le seul
        // instant où l'on sait qu'un tick vient de finir. Le rendu s'en sert pour
        // interpoler sans dépendre d'une API de suivi de delta.
        lastTickNanos = System.nanoTime();
        if (mc.isPaused()) return;
        if (count == 0) return;
        for (int i = 0; i < count; ) {
            short age = (short) (AGE[i] + 1);
            if (age >= MAX_AGE[i]) {
                // Remplacement par le dernier vivant : O(1), aucun décalage de tableau.
                int last = --count;
                if (i != last) {
                    PX[i] = PX[last]; PY[i] = PY[last]; PZ[i] = PZ[last];
                    VX[i] = VX[last]; VY[i] = VY[last]; VZ[i] = VZ[last];
                    SIZE[i] = SIZE[last]; SPIN[i] = SPIN[last];
                    AGE[i] = AGE[last]; MAX_AGE[i] = MAX_AGE[last];
                    MODE[i] = MODE[last]; HUE[i] = HUE[last]; GRAV[i] = GRAV[last];
                }
                continue;   // la case i porte maintenant un autre cube : on la retraite
            }
            AGE[i] = age;
            PX[i] += VX[i];
            PY[i] += VY[i];
            PZ[i] += VZ[i];
            // Frottement puis gravité, dans cet ordre : l'inverse amortirait la chute
            // elle-même et les cubes lourds flotteraient.
            VX[i] *= 0.94f;
            VY[i] *= 0.94f;
            VZ[i] *= 0.94f;
            VY[i] -= 0.0135f * GRAV[i];
            SPIN[i] += 0.07f;
            i++;
        }
    }

    // =====================================================================
    //  RENDU
    // =====================================================================

    /**
     * Une passe, un lot, un vidage. {@code AFTER_TRANSLUCENT_BLOCKS} est l'étape demandée
     * et la bonne : après elle, l'eau et le verre sont déjà dans le tampon de profondeur,
     * donc un cube derrière une vitre est correctement masqué, alors qu'à
     * {@code AFTER_PARTICLES} il passerait devant.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (count == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Tant que les ressources n'ont pas été chargées une première fois, le fournisseur
        // rend null : dessiner remonterait en NPE au fond de RenderSystem.setShader.
        if (ModShaders.voidPixel() == null) return;

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        Matrix4f mat = pose.last().pose();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc;
        try {
            vc = buffers.getBuffer(ModRenderTypes.voidPixel());
        } catch (Throwable ignored) {
            return;   // pile de rendu exotique (Sodium, Iris) : on s'abstient
        }
        if (vc == null) return;

        // Sous-frame : fraction du tick de cinquante millisecondes déjà écoulée. Même
        // procédé que le reste du mod ; en pause, elle vaut zéro, donc rien ne glisse.
        float partial = mc.isPaused() ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, (System.nanoTime() - lastTickNanos) / 50_000_000.0f));

        for (int i = 0; i < count; i++) {
            // Interpolation : la position affichée est celle du tick précédent plus la
            // fraction écoulée. Sans elle, les cubes avancent par saccades de 20 Hz, ce
            // qui se voit immédiatement sur une gerbe rapide.
            double wx = PX[i] + VX[i] * partial;
            double wy = PY[i] + VY[i] * partial;
            double wz = PZ[i] + VZ[i] * partial;

            double dx = wx - cam.x;
            double dy = wy - cam.y;
            double dz = wz - cam.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DIST_SQR) continue;

            float life = (AGE[i] + partial) / (float) MAX_AGE[i];
            if (life >= 1.0f) continue;
            // Fondu en fin de vie seulement : un cube qui pâlit dès sa naissance n'est
            // jamais vu à pleine intensité, et la gerbe paraît terne.
            float fade = life < 0.72f ? 1.0f : 1.0f - (life - 0.72f) / 0.28f;
            // Le cube grossit un peu en naissant. Mesuré : sans cela, l'apparition de
            // quatre cents cubes sur une seule frame se lit comme un clignotement.
            float grow = life < 0.10f ? life / 0.10f : 1.0f;
            float half = SIZE[i] * 0.5f * grow;

            // La phase spatiale, calculée ICI et une seule fois par cube : une addition
            // de trois produits, pas une couleur. Le modulo garde le résultat dans une
            // plage où un float ne perd pas ses décimales, même à trois millions de
            // blocs de l'origine — un cube à 30 000 000 aurait sinon une phase quantifiée
            // par pas de 2, donc une couleur figée.
            double raw = (wx * 0.17 + wy * 0.23 + wz * 0.13) % 1.0;
            if (raw < 0.0) raw += 1.0;
            int phase = (int) (raw * 255.0);

            emitCube(vc, mat, (float) dx, (float) dy, (float) dz, half, SPIN[i],
                    phase, MODE[i] & 0xFF, HUE[i] & 0xFF, Math.round(fade * 255.0f));
        }

        buffers.endBatch(ModRenderTypes.voidPixel());
    }

    /**
     * Les 24 sommets d'un cube, avec rotation autour de l'axe vertical.
     *
     * <p>Les coordonnées de texture ne désignent aucune texture — il n'y a pas de sampler.
     * Elles portent la position du fragment SUR SA FACE, de 0 à 1, et c'est à partir d'elles
     * seules que le shader reconstitue les douze arêtes. C'est tout l'intérêt du procédé :
     * douze arêtes en volume auraient demandé douze parallélépipèdes, soit près de trois
     * cents sommets par cube au lieu de vingt-quatre.</p>
     */
    private static void emitCube(VertexConsumer vc, Matrix4f mat,
                                 float cx, float cy, float cz, float h, float spin,
                                 int phase, int mode, int hue, int alpha) {
        float cs = (float) Math.cos(spin);
        float sn = (float) Math.sin(spin);

        // Les huit coins, tournés une fois puis réutilisés par les six faces.
        float x0 = -h, x1 = h, z0 = -h, z1 = h;
        float ax = cx + x0 * cs - z0 * sn, az = cz + x0 * sn + z0 * cs;
        float bx = cx + x1 * cs - z0 * sn, bz = cz + x1 * sn + z0 * cs;
        float dx2 = cx + x1 * cs - z1 * sn, dz2 = cz + x1 * sn + z1 * cs;
        float ex = cx + x0 * cs - z1 * sn, ez = cz + x0 * sn + z1 * cs;
        float yb = cy - h, yt = cy + h;

        // Bas et haut
        face(vc, mat, ax, yb, az, bx, yb, bz, dx2, yb, dz2, ex, yb, ez, phase, mode, hue, alpha);
        face(vc, mat, ex, yt, ez, dx2, yt, dz2, bx, yt, bz, ax, yt, az, phase, mode, hue, alpha);
        // Les quatre flancs
        face(vc, mat, ax, yb, az, ex, yb, ez, ex, yt, ez, ax, yt, az, phase, mode, hue, alpha);
        face(vc, mat, bx, yb, bz, ax, yb, az, ax, yt, az, bx, yt, bz, phase, mode, hue, alpha);
        face(vc, mat, dx2, yb, dz2, bx, yb, bz, bx, yt, bz, dx2, yt, dz2, phase, mode, hue, alpha);
        face(vc, mat, ex, yb, ez, dx2, yb, dz2, dx2, yt, dz2, ex, yt, ez, phase, mode, hue, alpha);
    }

    private static void face(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int phase, int mode, int hue, int alpha) {
        vc.addVertex(mat, x1, y1, z1).setUv(0.0f, 0.0f).setColor(phase, mode, hue, alpha);
        vc.addVertex(mat, x2, y2, z2).setUv(1.0f, 0.0f).setColor(phase, mode, hue, alpha);
        vc.addVertex(mat, x3, y3, z3).setUv(1.0f, 1.0f).setColor(phase, mode, hue, alpha);
        vc.addVertex(mat, x4, y4, z4).setUv(0.0f, 1.0f).setColor(phase, mode, hue, alpha);
    }
}
