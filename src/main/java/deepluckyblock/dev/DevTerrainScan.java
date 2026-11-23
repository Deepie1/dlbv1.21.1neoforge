package deepluckyblock.dev;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OUTIL DE DEVELOPPEMENT — SCAN DE TERRAIN DU CRIMSON LAKE, AU BLOC PRES.
 *
 * <h2>Ce fichier est jetable</h2>
 * Il est <b>entierement autonome</b> : aucune autre classe du mod ne le nomme, ne l'importe
 * et ne l'appelle. Il s'enregistre tout seul ({@code @EventBusSubscriber}), s'accroche au
 * pipeline par <b>observation</b> et non par modification, et lit l'etat du lac par
 * reflexion tolerante aux pannes. <b>Supprimer ce fichier suffit a retirer la totalite de
 * l'outil</b> : rien d'autre ne cesse de compiler, rien d'autre ne change de comportement.
 * Le package {@code deepluckyblock.dev} n'existe que pour lui.
 *
 * <h2>Pourquoi il existe</h2>
 * Huit tours de correctifs sur le raccord de terrain (T459 a T468) ont ete poses puis
 * annules en bloc sans rien reparer. La cause tenait en une phrase : personne n'avait
 * jamais VU le terrain. On corrigeait sur des captures d'ecran, en relancant une pose de
 * huit minutes a chaque essai. Cet outil ne corrige rien : il <b>mesure</b>, et il rend un
 * seul fichier texte, lisible hors du jeu, qui dit l'etat exact du terrain a chaque etape.
 *
 * <h2>Ce qu'il garantit sur la geometrie</h2>
 * <ul>
 *   <li><b>Orientation explicite et verifiable.</b> Une ligne du fichier = un Z constant ;
 *       X croit vers la droite (est), Z croit vers le bas (sud). Chaque grille porte ses
 *       propres regles de coordonnees monde, en tete et en marge, donc l'orientation se
 *       controle sans me croire sur parole.</li>
 *   <li><b>Alignement sur les chunks.</b> La zone scannee est bornee aux frontieres de
 *       chunk ({@code cx << 4}), jamais sur une coupe arbitraire, et les grilles portent un
 *       separateur visible toutes les 16 colonnes et toutes les 16 lignes. Une colonne de
 *       la grille appartient donc a un chunk identifiable a l'oeil.</li>
 *   <li><b>Y absolus, pas des symboles.</b> Les grilles principales donnent la hauteur
 *       reelle de chaque colonne en clair. Les vues en caracteres ne sont qu'un resume
 *       pour la forme d'ensemble.</li>
 *   <li><b>Les chunks absents ne sont jamais maquilles.</b> Un chunk qui n'est pas en
 *       memoire s'ecrit {@code ----}, se compte, et se liste. C'est le point que le dev
 *       veut verifier : il ne doit surtout pas etre masque par une valeur plausible.</li>
 *   <li><b>Les frontieres inter-chunks sont mesurees pour elles-memes</b> (section 5) :
 *       le denivele exactement entre x=15 d'un chunk et x=0 du suivant, ce qu'une vue
 *       d'ensemble ne montre pas.</li>
 * </ul>
 *
 * <h2>Lecture strictement passive</h2>
 * Aucune generation, aucun chargement, aucune attente de chunk : lecture par
 * {@code getChunkSource().getChunkNow(...)} seulement, et chunk absent = chunk declare
 * absent. Un scan qui chargerait ses chunks montrerait un terrain que le pipeline n'avait
 * pas, et couterait 8 a 12 s par chunk a froid (mesure T304) sur un serveur qui affiche
 * deja « Can't keep up! ».
 *
 * <h2>Usage</h2>
 * <pre>
 *   (rien a taper)      l'outil est ARME D'OFFICE au demarrage du serveur
 *   /dlbscan            remet la veille et repart de zero
 *   /dlbscan now        scan immediat autour du joueur, ecrit les fichiers tout de suite
 *   /dlbscan off        coupe la veille (ecrit ce qui a deja ete capture)
 *   /dlbscan stop       ecrit ce qui a deja ete capture, la veille reste active
 *   /dlbscan status     etat courant
 * </pre>
 * La sortie est un DOSSIER {@code <monde>/dlb_scan/scan-<horodatage>/} contenant UN
 * FICHIER PAR ETAPE -- {@code 01_1_DEBUT_CLEAR.txt}, {@code 02_2_APRES_CLEAR.txt}, ... --
 * chacun portant le scan complet de toute la zone a cet instant et lisible seul, plus un
 * {@code 00_SOMMAIRE.txt} qui ne contient que les comparaisons d'une etape a l'autre. Le
 * chemin du dossier est annonce en chat.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class DevTerrainScan {

    private DevTerrainScan() {
    }

    // =================================================================================
    //  REGLAGES
    // =================================================================================

    /** Anneau ajoute autour de l'emprise, en chunks. C'est la que vit le raccord. */
    private static final int RING_CHUNKS = 3;

    /** Hauteur scannee au-dessus du sol de reference. */
    private static final int SCAN_ABOVE = 140;
    /** Profondeur scannee sous le sol de reference. */
    private static final int SCAN_BELOW = 90;
    /** Profondeur de recherche d'air PIEGE sous le premier bloc solide. */
    private static final int AIR_PROBE_DEPTH = 48;

    /** Demi-cote de la zone prise par {@code /dlbscan now}, en blocs. */
    private static final int MANUAL_RADIUS = 176;

    /** Plafond d'etapes : au-dela le fichier devient inexploitable. */
    private static final int MAX_ETAPES = 8;

    /** Capture de progression pendant les phases longues. */
    private static final long CAPTURE_PERIODIQUE_MS = 120_000L;

    /** Palette de blocs ; au-dela les blocs en trop tombent dans « (deborde) ». */
    private static final int PALETTE_MAX = 4000;

    /** Colonne sans chunk en memoire. */
    private static final int ABSENT = Integer.MIN_VALUE;
    /** Colonne lue mais entierement vide sur la tranche scannee. */
    private static final int VIDE = Integer.MIN_VALUE + 1;

    private static final String LAC = "deepluckyblock.procedures.CrimsonLakeStructureSpawnProcedure";

    // =================================================================================
    //  ETAT
    // =================================================================================

    private enum Phase { DESARME, ARME, EN_COURS }

    /**
     * ARME D'OFFICE, et non DESARME.
     *
     * <p>Premiere version : l'outil demarrait desarme et il fallait penser a taper
     * {@code /dlbscan} AVANT de declencher la structure. Resultat constate en jeu : le lac
     * s'est construit pendant cinq minutes, l'outil a dormi, aucun fichier n'est sorti, et
     * RIEN n'avait prevenu. Un outil de mesure qu'on peut oublier d'allumer ne mesure
     * rien. Le cout d'une veille permanente est nul tant qu'aucune zone n'est epinglee --
     * un seul appel a ChunkKeeper par tick -- et cette classe est de toute facon
     * supprimee avant publication. {@code /dlbscan off} coupe la veille.</p>
     */
    private static volatile Phase phase = Phase.ARME;

    /** La veille se reprend apres chaque ecriture, sauf si le dev l'a coupee. */
    private static volatile boolean veilleAuto = true;

    /** Au-dela de ce silence, on ecrit au lieu de risquer de tout perdre. */
    private static final long SILENCE_MAX_MS = 300_000L;

    /** Annonce unique : son absence du log prouve que l'outil n'est pas dans la build. */
    private static volatile boolean banniereFaite;

    private static final class Etape {
        final String nom;
        final String detail;
        final long tMs;
        final int[] ySolid;      // sommet du premier bloc solide (ni air ni fluide)
        final int[] ySurface;    // sommet du premier bloc non-air (eau et feuillage compris)
        final int[] yWater;      // surface du premier fluide
        final char[] idSolid;    // index palette du bloc solide de surface
        final char[] idSurface;  // index palette du bloc de surface
        final short[] airSous;   // blocs d'air piges sous le premier solide
        final short[] nSolides;  // nombre de blocs pleins dans la tranche (densite)
        final boolean[] chunkCharge;
        int lues, absentes;

        Etape(String nom, String detail, long tMs, int n, int nc) {
            this.nom = nom;
            this.detail = detail;
            this.tMs = tMs;
            this.ySolid = new int[n];
            this.ySurface = new int[n];
            this.yWater = new int[n];
            this.idSolid = new char[n];
            this.idSurface = new char[n];
            this.airSous = new short[n];
            this.nSolides = new short[n];
            this.chunkCharge = new boolean[nc];
        }
    }

    private static final class Session {
        ServerLevel level;
        long t0 = System.currentTimeMillis();
        long derniereCaptureMs;
        // emprise de la structure (bornes monde, incluses)
        int ex0, ez0, ex1, ez1;
        boolean empriseConnue;
        int groundY;
        // zone scannee, alignee chunk
        int x0, z0, w, d, cx0, cz0, cw, cd;
        int yFloor, yTop;
        final List<Etape> etapes = new ArrayList<>(MAX_ETAPES);
        final List<String> palette = new ArrayList<>();
        final Map<String, Integer> paletteIdx = new HashMap<>();
        final List<String> journal = new ArrayList<>();
        int[] bruit;            // relief theorique, pour les chunks jamais charges
        int dernierPasteQ = -1;
        boolean zoneVue;
    }

    private static volatile Session session;

    private static void log(Object msg) {
        org.slf4j.LoggerFactory.getLogger("deep_lucky_block").info("{}", msg);
    }

    private static void dire(ServerLevel level, String texte, ChatFormatting couleur) {
        try {
            Component c = Component.literal("[DLB-SCAN] " + texte).withStyle(couleur);
            for (ServerPlayer p : level.players()) p.sendSystemMessage(c);
        } catch (Throwable ignored) {
        }
        log("[DLB-SCAN] " + texte);
    }

    // =================================================================================
    //  COMMANDE
    // =================================================================================

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("dlbscan")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            armer(ctx.getSource());
                            return 1;
                        })
                        .then(Commands.literal("now").executes(ctx -> {
                            scanImmediat(ctx.getSource());
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            veilleAuto = false;
                            stop(ctx.getSource());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[DLB-SCAN] veille COUPEE : plus aucune construction ne "
                                            + "sera mesuree jusqu'a /dlbscan.")
                                    .withStyle(ChatFormatting.GRAY), false);
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(ctx -> {
                            stop(ctx.getSource());
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            statut(ctx.getSource());
                            return 1;
                        }))
        );
    }

    private static void armer(CommandSourceStack src) {
        phase = Phase.ARME;
        veilleAuto = true;
        session = null;
        src.sendSuccess(() -> Component.literal(
                "[DLB-SCAN] arme. Lance maintenant /dlbtest crimsonlake : toute la zone "
                        + "sera scannee a chaque etape, et UN FICHIER PAR ETAPE sortira dans "
                        + "<monde>/dlb_scan/scan-<horodatage>/.")
                .withStyle(ChatFormatting.AQUA), false);
    }

    private static void stop(CommandSourceStack src) {
        Session s = session;
        if (s != null && !s.etapes.isEmpty()) {
            ecrire(s, "arret manuel");
        }
        // /dlbscan stop ECRIT puis rend la main ; il ne coupe pas la veille, sinon la
        // construction suivante repasserait inapercue -- la panne meme qu'on repare ici.
        // Seul /dlbscan off coupe la veille, et il a deja mis veilleAuto a false.
        phase = veilleAuto ? Phase.ARME : Phase.DESARME;
        session = null;
        final boolean veille = veilleAuto;
        src.sendSuccess(() -> Component.literal(veille
                        ? "[DLB-SCAN] capture ecrite ; la veille reste active pour la "
                                + "prochaine construction."
                        : "[DLB-SCAN] desarme.")
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static void statut(CommandSourceStack src) {
        Session s = session;
        final String txt;
        if (s == null) {
            txt = "phase=" + phase + (veilleAuto ? " (VEILLE ACTIVE : la prochaine "
                    + "construction sera mesuree)" : " (veille COUPEE)")
                    + ", aucune session ouverte. File de pose du lac = "
                    + pasteQueueSize() + " (-1 = reflexion indisponible).";
        } else {
            txt = "phase=" + phase + ", " + s.etapes.size() + " etape(s) capturee(s), zone "
                    + s.w + "x" + s.d + " (" + s.cw + "x" + s.cd + " chunks), emprise "
                    + (s.empriseConnue ? (s.ex0 + ".." + s.ex1 + " / " + s.ez0 + ".." + s.ez1)
                                       : "pas encore connue");
        }
        src.sendSuccess(() -> Component.literal("[DLB-SCAN] " + txt)
                .withStyle(ChatFormatting.AQUA), false);
    }

    private static void scanImmediat(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        BlockPos p = BlockPos.containing(src.getPosition());
        Session s = ouvrir(level,
                p.getX() - MANUAL_RADIUS, p.getZ() - MANUAL_RADIUS,
                p.getX() + MANUAL_RADIUS, p.getZ() + MANUAL_RADIUS,
                p.getY(), false);
        if (s == null) {
            src.sendFailure(Component.literal("[DLB-SCAN] ouverture impossible."));
            return;
        }
        session = s;
        capturer(s, "SCAN_MANUEL", "demande par /dlbscan now autour de " + p.toShortString());
        ecrire(s, "scan manuel");
        session = null;
        // Un releve ponctuel ne doit pas eteindre la surveillance : sinon un /dlbscan now
        // de controle rendrait la construction suivante invisible, sans que rien ne le dise.
        phase = veilleAuto ? Phase.ARME : Phase.DESARME;
    }

    // =================================================================================
    //  OBSERVATION DU PIPELINE  (aucune modification du lac)
    // =================================================================================

    /**
     * Taille de la file de pose du lac, lue par reflexion.
     *
     * <p>C'est le signal de phase le plus net qui existe sans toucher au lac : la file
     * recoit son job <b>dans le callback de fin de clear</b>, et le perd a la derniere
     * tranche posee. Passer de 0 a 1 date donc exactement la fin du clear, et revenir a 0
     * la fin de la pose. Si la reflexion echoue, on rend -1 et l'outil retombe sur un
     * echantillonnage periodique : degrade, mais jamais muet.</p>
     */
    private static int pasteQueueSize() {
        try {
            Class<?> c = Class.forName(LAC);
            java.lang.reflect.Field f = c.getDeclaredField("PASTE_Q");
            f.setAccessible(true);
            Object q = f.get(null);
            if (q instanceof java.util.Collection<?> col) return col.size();
        } catch (Throwable ignored) {
        }
        return -1;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!banniereFaite) {
            banniereFaite = true;
            log("[DLB-SCAN] present et EN VEILLE : la prochaine construction de structure "
                    + "sera mesuree a chaque etape, sans rien avoir a taper. Sortie dans "
                    + "<monde>/dlb_scan/scan-<horodatage>/, un fichier par etape. "
                    + "/dlbscan off pour couper, /dlbscan status pour l'etat.");
        }
        if (phase == Phase.DESARME) return;
        try {
            tickGarde(event);
        } catch (Throwable t) {
            log("[DLB-SCAN] tick en echec, scan desarme : " + t);
            phase = Phase.DESARME;
            session = null;
        }
    }

    private static void tickGarde(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null) return;
        // En attente, on sonde a CHAQUE tick : le clear demarre dans le tick meme ou
        // ChunkKeeper epingle la zone, et il s'accorde 40 ms par passe. Sonder une fois
        // sur huit laisserait disparaitre jusqu'a 300 ms de terrain avant la 1re mesure.
        // Une fois la session ouverte, un sondage sur huit suffit largement.
        if (session != null && (server.getTickCount() & 7) != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            int[] zone = zoneTenue(level);
            Session s = session;

            // --- ouverture : la zone epinglee apparait = le pipeline vient de reserver
            //     son emprise, juste avant le premier coup de pioche du clear.
            if (s == null && zone != null && phase == Phase.ARME) {
                int gy = solDeReference(level, zone);
                s = ouvrir(level, zone[0], zone[1], zone[2], zone[3], gy, true);
                if (s == null) continue;
                session = s;
                phase = Phase.EN_COURS;
                s.zoneVue = true;
                s.dernierPasteQ = Math.max(0, pasteQueueSize());
                capturer(s, "1_DEBUT_CLEAR",
                        "premier tick ou la zone epinglee est visible ; le clear vient de "
                                + "demarrer dans le tick precedent (voir section 1 pour "
                                + "l'ecart deja creuse)");
                continue;
            }
            if (s == null || s.level != level) continue;

            // --- transitions de la file de pose
            int q = pasteQueueSize();
            if (q >= 0) {
                if (s.dernierPasteQ == 0 && q > 0) {
                    capturer(s, "2_APRES_CLEAR",
                            "la file de pose vient de recevoir son job : le clear est termine");
                } else if (s.dernierPasteQ > 0 && q == 0) {
                    capturer(s, "3_APRES_PASTE",
                            "la file de pose s'est videe : la derniere tranche est posee");
                }
                s.dernierPasteQ = q;
            }

            // --- progression : une capture de temps en temps, pour voir le clear avancer
            if (System.currentTimeMillis() - s.derniereCaptureMs > CAPTURE_PERIODIQUE_MS
                    && s.etapes.size() < MAX_ETAPES - 1) {
                capturer(s, "P" + s.etapes.size() + "_PROGRESSION",
                        "capture periodique, file de pose = " + q);
            }

            // --- chien de garde : plus rien ne bouge, on ECRIT plutot que de tout perdre.
            // Le pipeline peut abandonner sans relacher la zone, et le joueur peut quitter
            // le monde en pleine construction. Sans cela la session reste ouverte pour
            // toujours et aucun fichier ne sort : le pire resultat possible pour un outil
            // dont l'unique role est de produire une mesure.
            if (System.currentTimeMillis() - s.derniereCaptureMs > SILENCE_MAX_MS) {
                capturer(s, "9_SILENCE", "plus aucune evolution depuis "
                        + (SILENCE_MAX_MS / 1000L) + " s : pipeline arrete ou abandonne");
                ecrire(s, "silence prolonge du pipeline");
                session = null;
                phase = veilleAuto ? Phase.ARME : Phase.DESARME;
                return;
            }

            // --- fermeture : la zone est relachee = le pipeline a fini (ou abandonne)
            if (zone == null && s.zoneVue) {
                // Le lac relache la zone dans le bloc meme ou il retire son job de la
                // file : sans ce garde-fou, 3_APRES_PASTE et 4_FIN tomberaient au meme
                // tick et le fichier porterait deux fois la meme photo, pour 600 Kio.
                boolean dejaPrise = !s.etapes.isEmpty()
                        && s.etapes.get(s.etapes.size() - 1).nom.equals("3_APRES_PASTE")
                        && System.currentTimeMillis() - s.derniereCaptureMs < 2000L;
                if (!dejaPrise) {
                    capturer(s, "4_FIN",
                            "ChunkKeeper a relache la zone : fin (ou abandon) du pipeline");
                }
                ecrire(s, "fin du pipeline");
                session = null;
                phase = veilleAuto ? Phase.ARME : Phase.DESARME;
                return;
            }
            if (s.etapes.size() >= MAX_ETAPES) {
                ecrire(s, "plafond d'etapes atteint");
                session = null;
                phase = veilleAuto ? Phase.ARME : Phase.DESARME;
                return;
            }
        }
    }

    private static int[] zoneTenue(ServerLevel level) {
        try {
            return deepluckyblock.util.ChunkKeeper.zoneBlockBounds(level);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Sol de reference : mediane grossiere du relief theorique au centre de la zone. */
    private static int solDeReference(ServerLevel level, int[] zone) {
        try {
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            int cx = (zone[0] + zone[2]) / 2, cz = (zone[1] + zone[3]) / 2;
            int somme = 0, n = 0;
            for (int dx = -64; dx <= 64; dx += 32) {
                for (int dz = -64; dz <= 64; dz += 32) {
                    somme += gen.getBaseHeight(cx + dx, cz + dz,
                            net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                            level, rs);
                    n++;
                }
            }
            return n > 0 ? somme / n : level.getSeaLevel();
        } catch (Throwable t) {
            return level.getSeaLevel();
        }
    }

    // =================================================================================
    //  SESSION
    // =================================================================================

    private static Session ouvrir(ServerLevel level, int ex0, int ez0, int ex1, int ez1,
                                  int groundY, boolean empriseConnue) {
        try {
            Session s = new Session();
            s.level = level;
            s.ex0 = Math.min(ex0, ex1);
            s.ex1 = Math.max(ex0, ex1);
            s.ez0 = Math.min(ez0, ez1);
            s.ez1 = Math.max(ez0, ez1);
            s.empriseConnue = empriseConnue;
            s.groundY = groundY;

            int ring = RING_CHUNKS * 16;
            s.cx0 = (s.ex0 - ring) >> 4;
            s.cz0 = (s.ez0 - ring) >> 4;
            s.cw = ((s.ex1 + ring) >> 4) - s.cx0 + 1;
            s.cd = ((s.ez1 + ring) >> 4) - s.cz0 + 1;
            s.x0 = s.cx0 << 4;
            s.z0 = s.cz0 << 4;
            s.w = s.cw * 16;
            s.d = s.cd * 16;

            s.yFloor = Math.max(level.getMinBuildHeight(), groundY - SCAN_BELOW);
            s.yTop = Math.min(level.getMaxBuildHeight() - 1, groundY + SCAN_ABOVE);
            s.derniereCaptureMs = System.currentTimeMillis();
            dire(level, "session ouverte : zone " + s.w + "x" + s.d + " blocs ("
                    + s.cw + "x" + s.cd + " chunks), sol de reference Y=" + groundY,
                    ChatFormatting.AQUA);
            return s;
        } catch (Throwable t) {
            log("[DLB-SCAN] ouverture impossible : " + t);
            return null;
        }
    }

    /**
     * Relief THEORIQUE de toute la zone, par bruit pur.
     *
     * <p>Ne genere rien et ne charge rien. Sert de reference absolue du terrain d'origine,
     * et surtout de point de comparaison pour les chunks jamais charges : sans lui, un
     * chunk absent ne dirait meme pas quelle forme il aurait du avoir.</p>
     */
    private static void calculerBruit(Session s) {
        s.bruit = new int[s.w * s.d];
        try {
            var gen = s.level.getChunkSource().getGenerator();
            var rs = s.level.getChunkSource().randomState();
            var type = net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG;
            for (int z = 0; z < s.d; z++) {
                for (int x = 0; x < s.w; x++) {
                    s.bruit[z * s.w + x] = gen.getBaseHeight(s.x0 + x, s.z0 + z, type, s.level, rs);
                }
            }
        } catch (Throwable t) {
            java.util.Arrays.fill(s.bruit, ABSENT);
            log("[DLB-SCAN] relief theorique indisponible : " + t);
        }
    }

    // =================================================================================
    //  CAPTURE — SCAN COMPLET DE TOUTE LA ZONE, A CHAQUE ETAPE
    // =================================================================================

    private static void capturer(Session s, String nom, String detail) {
        if (s.etapes.size() >= MAX_ETAPES) return;
        long t0 = System.nanoTime();
        Etape e = new Etape(nom, detail, System.currentTimeMillis() - s.t0,
                s.w * s.d, s.cw * s.cd);
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        for (int cz = 0; cz < s.cd; cz++) {
            for (int cx = 0; cx < s.cw; cx++) {
                ChunkAccess chunk = chunkPassif(s.level, s.cx0 + cx, s.cz0 + cz);
                boolean charge = chunk != null;
                e.chunkCharge[cz * s.cw + cx] = charge;
                int bx0 = (s.cx0 + cx) << 4, bz0 = (s.cz0 + cz) << 4;
                // Depart de scan calcule UNE FOIS PAR CHUNK : le ciel represente
                // l'essentiel de la tranche et se saute en O(1) par section vide.
                int yDepart = charge ? departScan(chunk, s.yTop, s.yFloor) : s.yFloor;
                for (int oz = 0; oz < 16; oz++) {
                    for (int ox = 0; ox < 16; ox++) {
                        int i = (bz0 + oz - s.z0) * s.w + (bx0 + ox - s.x0);
                        if (i < 0 || i >= e.ySolid.length) continue;
                        if (!charge) {
                            // JAMAIS de valeur de remplacement plausible : un chunk absent
                            // doit se voir, c'est precisement ce qu'on vient verifier.
                            e.ySolid[i] = ABSENT;
                            e.ySurface[i] = ABSENT;
                            e.yWater[i] = ABSENT;
                            e.absentes++;
                        } else {
                            lireColonne(s, e, chunk, bp, bx0 + ox, bz0 + oz, i, yDepart);
                            e.lues++;
                        }
                    }
                }
            }
        }
        s.etapes.add(e);
        s.derniereCaptureMs = System.currentTimeMillis();
        int nCharges = 0;
        for (boolean b : e.chunkCharge) if (b) nCharges++;
        String ligne = nom + " : " + e.lues + " colonnes lues, " + e.absentes + " absentes, "
                + nCharges + "/" + e.chunkCharge.length + " chunks charges, "
                + ((System.nanoTime() - t0) / 1_000_000L) + " ms";
        s.journal.add(ligne);
        dire(s.level, ligne, ChatFormatting.GRAY);
    }

    /** Chunk SANS jamais generer, charger ni attendre. Null = reellement absent. */
    private static ChunkAccess chunkPassif(ServerLevel level, int cx, int cz) {
        try {
            return level.getChunkSource().getChunkNow(cx, cz);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Y du sommet de la plus haute section non vide : au-dessus, il n'y a que du ciel. */
    private static int departScan(ChunkAccess chunk, int yTop, int yFloor) {
        try {
            LevelChunkSection[] secs = chunk.getSections();
            for (int si = secs.length - 1; si >= 0; si--) {
                LevelChunkSection sec = secs[si];
                if (sec == null || sec.hasOnlyAir()) continue;
                return Math.min(yTop, chunk.getMinBuildHeight() + (si << 4) + 15);
            }
            return yFloor;
        } catch (Throwable t) {
            return yTop;
        }
    }

    private static void lireColonne(Session s, Etape e, ChunkAccess chunk,
                                    BlockPos.MutableBlockPos bp, int x, int z, int i,
                                    int yDepart) {
        int ySurface = ABSENT, ySolid = ABSENT, yWater = ABSENT;
        BlockState stSurface = null, stSolid = null;
        int air = 0, pleins = 0;
        boolean solideVu = false;
        int yStop = s.yFloor;

        for (int y = yDepart; y >= yStop; y--) {
            bp.set(x, y, z);
            BlockState st;
            try {
                st = chunk.getBlockState(bp);
            } catch (Throwable t) {
                break;
            }
            if (st == null) continue;
            boolean estAir = st.isAir();
            boolean estFluide = !st.getFluidState().isEmpty();
            if (!estAir) {
                pleins++;
                if (ySurface == ABSENT) {
                    ySurface = y;
                    stSurface = st;
                }
            }
            if (estFluide && yWater == ABSENT) yWater = y;
            if (!estAir && !estFluide && !solideVu) {
                ySolid = y;
                stSolid = st;
                solideVu = true;
                // Sous le premier solide on ne cherche plus que l'air piege, et sur une
                // profondeur bornee : le sous-sol profond ne dit rien du raccord.
                yStop = Math.max(s.yFloor, y - AIR_PROBE_DEPTH);
            }
            if (solideVu && estAir) air++;
        }

        e.ySurface[i] = ySurface == ABSENT ? VIDE : ySurface;
        e.ySolid[i] = ySolid == ABSENT ? VIDE : ySolid;
        e.yWater[i] = yWater == ABSENT ? VIDE : yWater;
        e.idSurface[i] = (char) idDe(s, stSurface);
        e.idSolid[i] = (char) idDe(s, stSolid);
        e.airSous[i] = (short) Math.min(Short.MAX_VALUE, air);
        e.nSolides[i] = (short) Math.min(Short.MAX_VALUE, pleins);
    }

    private static int idDe(Session s, BlockState st) {
        if (st == null) return 0;
        String nom;
        try {
            nom = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
        } catch (Throwable t) {
            return 0;
        }
        Integer k = s.paletteIdx.get(nom);
        if (k != null) return k;
        if (s.palette.size() >= PALETTE_MAX) return PALETTE_MAX;
        s.palette.add(nom);
        int v = s.palette.size();
        s.paletteIdx.put(nom, v);
        return v;
    }

    private static int idx(Session s, int wx, int wz) {
        int dx = wx - s.x0, dz = wz - s.z0;
        if (dx < 0 || dz < 0 || dx >= s.w || dz >= s.d) return -1;
        return dz * s.w + dx;
    }

    private static boolean dansEmprise(Session s, int wx, int wz) {
        return wx >= s.ex0 && wx <= s.ex1 && wz >= s.ez0 && wz <= s.ez1;
    }

    private static boolean chunkDansEmprise(Session s, int cx, int cz) {
        int wx = (s.cx0 + cx) << 4, wz = (s.cz0 + cz) << 4;
        return wx + 15 >= s.ex0 && wx <= s.ex1 && wz + 15 >= s.ez0 && wz <= s.ez1;
    }

    // =================================================================================
    //  ECRITURE — UN SEUL FICHIER TEXTE
    // =================================================================================

    private static void ecrire(Session s, String cause) {
        if (s.etapes.isEmpty()) {
            log("[DLB-SCAN] rien a ecrire : aucune etape capturee");
            return;
        }
        // Le relief theorique coute ~135 000 appels a getBaseHeight, soit quelques
        // secondes. On le paie ICI, une seule fois, APRES la fin du pipeline -- jamais
        // pendant la construction, ou le serveur est deja en retard de plusieurs centaines
        // de ticks.
        calculerBruit(s);
        try {
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                    .format(new java.util.Date());
            java.nio.file.Path dir = s.level.getServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("dlb_scan").resolve("scan-" + stamp);
            java.nio.file.Files.createDirectories(dir);

            long total = 0;
            List<String> ecrits = new ArrayList<>();

            // UN FICHIER PAR ETAPE. Chacun porte le scan COMPLET de toute la zone a cet
            // instant precis et se lit seul : un seul fichier monolithique melangeait les
            // etapes et devenait intransportable.
            for (int n = 0; n < s.etapes.size(); n++) {
                Etape e = s.etapes.get(n);
                Etape prec = n > 0 ? s.etapes.get(n - 1) : null;
                StringBuilder r = new StringBuilder(4 << 20);
                enteteEtape(r, s, e, prec, n, cause);
                section2ChunksAbsents(r, s, e);
                tableauParChunk(r, s, e);
                section4bComposition(r, s, e);
                section5InterChunks(r, s, e);
                section6Marches(r, s, e);
                section8Vues(r, s, e, prec);
                coupesVerticales(r, s, e);
                section9Grilles(r, s, e, prec);
                section10Palette(r, s);
                total += ecrireFichier(s,
                        dir.resolve(String.format("%02d_%s.txt", n + 1, nomFichier(e.nom))),
                        r, ecrits);
            }

            // Le sommaire ne porte QUE ce qui compare les etapes entre elles.
            StringBuilder r = new StringBuilder(2 << 20);
            entete(r, s, cause);
            sommaireFichiers(r, s, ecrits);
            section1Etapes(r, s);
            section3ChunksNonTouches(r, s);
            section4AncienTerrain(r, s);
            section7Profil(r, s);
            grilleTheorique(r, s);
            section10Palette(r, s);
            total += ecrireFichier(s, dir.resolve("00_SOMMAIRE.txt"), r, ecrits);

            dire(s.level, (s.etapes.size() + 1) + " fichiers ecrits, " + (total / 1024L)
                    + " Kio au total, dans : " + dir, ChatFormatting.AQUA);
            dire(s.level, "ouvre 00_SOMMAIRE.txt en premier : il designe les chunks fautifs "
                    + "et renvoie au fichier d'etape qui les detaille.", ChatFormatting.AQUA);
        } catch (Throwable t) {
            log("[DLB-SCAN] ecriture impossible : " + t);
            dire(s.level, "ECHEC d'ecriture : " + t, ChatFormatting.RED);
        }
    }

    /** Un nom d'etape finit dans un nom de fichier : rien d'autre que [A-Za-z0-9_-]. */
    private static String nomFichier(String nom) {
        StringBuilder b = new StringBuilder(nom.length());
        for (int i = 0; i < nom.length(); i++) {
            char c = nom.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            b.append(ok ? c : '_');
        }
        return b.length() == 0 ? "etape" : b.toString();
    }

    private static long ecrireFichier(Session s, java.nio.file.Path out, StringBuilder r,
                                      List<String> ecrits) throws java.io.IOException {
        java.nio.file.Files.write(out,
                r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long octets = java.nio.file.Files.size(out);
        ecrits.add(String.format("%-26s %7d Kio", out.getFileName().toString(),
                Math.max(1L, octets / 1024L)));
        log("[DLB-SCAN] ecrit : " + out + "  (" + (octets / 1024L) + " Kio)");
        return octets;
    }

    private static void sommaireFichiers(StringBuilder r, Session s, List<String> ecrits) {
        titre(r, "0. LES FICHIERS DE CE SCAN");
        r.append("Un fichier par etape, chacun portant le scan COMPLET de toute la zone a cet\n");
        r.append("instant et lisible seul. Le present fichier ne porte que les COMPARAISONS\n");
        r.append("d'une etape a l'autre, qui n'appartiennent a aucune des deux.\n\n");
        for (String f : ecrits) r.append("   ").append(f).append('\n');
        r.append("\nOrdre de lecture : la section 3 ci-dessous nomme les chunks sautes d'une\n");
        r.append("etape a l'autre ; le fichier de l'etape concernee en donne le detail au bloc\n");
        r.append("pres (tableau par chunk, coupes verticales, puis grilles).\n");
    }

    private static void grilleTheorique(StringBuilder r, Session s) {
        titre(r, "9z. RELIEF THEORIQUE DE GENERATION  (reference commune a toutes les etapes)");
        r.append("Hauteur que le bruit de generation prevoit pour chaque colonne, structure mise\n");
        r.append("a part. Elle ne depend d'aucune etape : elle n'est donc donnee qu'une fois,\n");
        r.append("ici, et n'est JAMAIS melangee aux mesures dans les grilles d'etape.\n\n");
        grilleY(r, s, s.bruit);
    }

    private static void enteteEtape(StringBuilder r, Session s, Etape e, Etape prec,
                                    int n, String cause) {
        r.append("=".repeat(100)).append('\n');
        r.append(" DLB -- SCAN DE TERRAIN   ETAPE ").append(n + 1).append(" / ")
         .append(s.etapes.size()).append("   :   ").append(e.nom).append('\n');
        r.append("=".repeat(100)).append('\n');
        r.append("etape               : ").append(e.nom).append("   ").append(e.detail).append('\n');
        r.append("instant             : t+").append(e.tMs).append(" ms apres l'ouverture du scan\n");
        if (prec != null) {
            r.append("etape precedente    : ").append(prec.nom).append("   (t+").append(prec.tMs)
             .append(" ms, soit ").append(e.tMs - prec.tMs).append(" ms plus tot)\n");
        } else {
            r.append("etape precedente    : aucune -- c'est la premiere capture\n");
        }
        r.append("ecriture declenchee : ").append(cause).append('\n');
        r.append("colonnes lues       : ").append(e.lues).append(" / ").append(s.w * s.d)
         .append("   non mesurables (chunk absent) : ").append(e.absentes).append('\n');
        int ch = 0;
        for (boolean b : e.chunkCharge) if (b) ch++;
        r.append("chunks charges      : ").append(ch).append(" / ").append(s.cw * s.cd).append('\n');
        r.append("zone scannee        : x ").append(s.x0).append("..").append(s.x0 + s.w - 1)
         .append("   z ").append(s.z0).append("..").append(s.z0 + s.d - 1)
         .append("   (").append(s.w).append(" x ").append(s.d).append(" blocs, ")
         .append(s.cw).append(" x ").append(s.cd).append(" chunks)\n");
        r.append("emprise structure   : x ").append(s.ex0).append("..").append(s.ex1)
         .append("   z ").append(s.ez0).append("..").append(s.ez1)
         .append("   + anneau de ").append(RING_CHUNKS).append(" chunks\n");
        r.append("tranche sondee      : y ").append(s.yFloor).append(" .. ").append(s.yTop)
         .append("   sol de reference y=").append(s.groundY).append('\n');
        r.append("\nCE FICHIER CONTIENT, POUR CETTE SEULE ETAPE :\n");
        r.append("    2   chunks non charges  (les « chunks qui manquent »)\n");
        r.append("    3b  tableau par chunk   (une ligne par chunk de toute la zone)\n");
        r.append("    4b  composition de la surface, premier bloc solide, air piege\n");
        r.append("    5   raccords inter-chunks, mesures a la couture\n");
        r.append("    6   marches et failles\n");
        r.append("    8   vues du dessus en caracteres\n");
        r.append("    8b  coupes verticales   (le terrain vu de cote)\n");
        r.append("    9   donnees au bloc pres : ySolid, ySurface, ecart depuis l'etape\n");
        r.append("        precedente, eau, air piege\n");
        r.append("   10   palette des blocs\n");
        r.append("Les comparaisons entre etapes sont dans 00_SOMMAIRE.txt.\n");
    }

    private static void titre(StringBuilder r, String t) {
        r.append('\n').append("=".repeat(100)).append('\n')
         .append(' ').append(t).append('\n')
         .append("=".repeat(100)).append('\n');
    }

    private static void entete(StringBuilder r, Session s, String cause) {
        r.append("=".repeat(100)).append('\n');
        r.append(" DLB -- SCAN DE TERRAIN (outil de developpement, fichier jetable)\n");
        r.append("=".repeat(100)).append('\n');
        r.append("date            : ").append(new java.util.Date()).append('\n');
        r.append("cause d'ecriture: ").append(cause).append('\n');
        r.append("dimension       : ").append(s.level.dimension().location()).append('\n');
        r.append("graine          : ").append(s.level.getSeed()).append('\n');
        r.append("emprise         : ")
         .append(s.empriseConnue ? "(zone epinglee par ChunkKeeper, ARRONDIE AU CHUNK) "
                                 : "(ESTIMEE autour du joueur) ")
         .append("x ").append(s.ex0).append("..").append(s.ex1)
         .append("   z ").append(s.ez0).append("..").append(s.ez1)
         .append("   (").append(s.ex1 - s.ex0 + 1).append(" x ").append(s.ez1 - s.ez0 + 1)
         .append(" blocs)\n");
        r.append("zone scannee    : x ").append(s.x0).append("..").append(s.x0 + s.w - 1)
         .append("   z ").append(s.z0).append("..").append(s.z0 + s.d - 1)
         .append("   (").append(s.w).append(" x ").append(s.d).append(" = ")
         .append(s.w * s.d).append(" colonnes)\n");
        r.append("chunks          : ").append(s.cw).append(" x ").append(s.cd).append(" = ")
         .append(s.cw * s.cd).append("   (chunk X ").append(s.cx0).append("..")
         .append(s.cx0 + s.cw - 1).append(", chunk Z ").append(s.cz0).append("..")
         .append(s.cz0 + s.cd - 1).append(")\n");
        r.append("anneau autour   : ").append(RING_CHUNKS).append(" chunks\n");
        r.append("                  NOTE : l'emprise ci-dessus est celle que le pipeline a\n");
        r.append("                  reellement epinglee, arrondie aux frontieres de chunk. Elle\n");
        r.append("                  deborde donc un peu le rectangle exact du gabarit NBT.\n");
        r.append("sol de reference: Y=").append(s.groundY)
         .append("   tranche scannee Y ").append(s.yFloor).append("..").append(s.yTop).append('\n');
        r.append('\n');
        r.append("ORIENTATION (a verifier d'un coup d'oeil sur les regles de chaque grille)\n");
        r.append("  X croit vers la DROITE (est).  Z croit vers le BAS (sud).\n");
        r.append("  Une ligne de grille = un Z constant. Une colonne = un X constant.\n");
        r.append("  La zone est bornee aux frontieres de chunk : chaque bloc de 16 colonnes\n");
        r.append("  separe par « | » est exactement un chunk, et les lignes sont groupees par 16.\n");
        r.append('\n');
        r.append("CONVENTIONS DE VALEUR\n");
        r.append("  ----  chunk NON CHARGE au moment de la lecture (valeur inconnue, jamais devinee)\n");
        r.append("  ....  colonne lue mais entierement vide sur la tranche scannee\n");
        r.append("  sinon la hauteur Y ABSOLUE du bloc concerne\n");
        r.append('\n');
        r.append("SOMMAIRE\n");
        r.append("  1  etapes capturees\n");
        r.append("  2  CHUNKS NON CHARGES par etape (les « chunks qui manquent »)\n");
        r.append("  3  chunks NON TOUCHES d'une etape a l'autre (sautes par le clear / la pose)\n");
        r.append("  4  ancien terrain reste dans l'emprise\n");
        r.append("  7  profil du raccord, du bord vers l'exterieur\n");
        r.append(" 9z  relief theorique de generation (reference commune)\n");
        r.append(" 10  palette des blocs\n");
        r.append("\nTout le reste -- composition, raccords inter-chunks, marches, vues,\n");
        r.append("coupes verticales et grilles au bloc pres -- est dans le fichier de CHAQUE\n");
        r.append("etape, puisque ces mesures appartiennent a un instant et non a la serie.\n");
    }

    // ---------------------------------------------------------------- 1
    private static void section1Etapes(StringBuilder r, Session s) {
        titre(r, "1. ETAPES CAPTUREES");
        r.append(String.format("%-3s %-20s %9s %10s %10s %14s%n",
                "#", "etape", "t(ms)", "lues", "absentes", "chunks charges"));
        for (int n = 0; n < s.etapes.size(); n++) {
            Etape e = s.etapes.get(n);
            int ch = 0;
            for (boolean b : e.chunkCharge) if (b) ch++;
            r.append(String.format("%-3d %-20s %9d %10d %10d %14s%n",
                    n, e.nom, e.tMs, e.lues, e.absentes,
                    ch + "/" + e.chunkCharge.length));
        }
        r.append('\n');
        for (Etape e : s.etapes) {
            r.append("  ").append(e.nom).append(" : ").append(e.detail).append('\n');
        }
        r.append('\n');
        r.append("Les etapes sont detectees SANS modifier le pipeline : l'apparition de la zone\n");
        r.append("epinglee date le debut du clear, la file de pose qui recoit son job date la fin\n");
        r.append("du clear, la meme file qui se vide date la fin de la pose, et le relachement de\n");
        r.append("la zone date la fin du pipeline.\n\n");

        // Honnetete de la mesure : la 1re capture ne peut pas preceder le tout premier
        // coup de pioche, puisque le clear part dans le tick meme ou la zone est epinglee.
        // Plutot que de taire cette limite, on la CHIFFRE en comparant la 1re capture au
        // relief theorique de generation.
        Etape p = s.etapes.get(0);
        int compares = 0, deja = 0, creuseMax = 0;
        for (int i = 0; i < p.ySolid.length; i++) {
            int b = s.bruit[i];
            if (!mesurable(p.ySolid[i]) || b == ABSENT) continue;
            compares++;
            int ecart = b - p.ySolid[i];
            if (ecart >= 3) {
                deja++;
                if (ecart > creuseMax) creuseMax = ecart;
            }
        }
        r.append("RETARD DE LA PREMIERE CAPTURE (limite assumee, pas un defaut cache)\n");
        r.append("  Le clear demarre dans le tick meme ou la zone est epinglee : aucune mesure\n");
        r.append("  externe ne peut le preceder. Ecart mesure entre la 1re capture et le relief\n");
        r.append("  theorique de generation :\n");
        r.append("    colonnes comparables                    : ").append(compares).append('\n');
        r.append("    colonnes deja plus basses de >= 3 blocs : ").append(deja)
         .append(pct(deja, compares)).append('\n');
        r.append("    creusement maximal deja constate        : ").append(creuseMax)
         .append(" bloc(s)\n");
        r.append("  Attention : cet ecart melange ce que le clear a deja mange ET les\n");
        r.append("  differences normales entre le bruit pur et le monde decore (arbres, grottes,\n");
        r.append("  structures vanilla, constructions du joueur). Il se lit comme une BORNE\n");
        r.append("  SUPERIEURE du retard, pas comme sa valeur exacte.\n");
        r.append("  La reference d'origine fiable reste la grille 9.THEORIQUE.\n");
    }

    // ---------------------------------------------------------------- 2
    private static void section2ChunksAbsents(StringBuilder r, Session s, Etape e) {
        titre(r, "2. CHUNKS NON CHARGES A CETTE ETAPE  (les « chunks qui manquent »)");
        r.append("Un chunk non charge ne peut etre ni coupe, ni pose, ni lisse. S'il tombe dans\n");
        r.append("l'emprise, c'est l'anomalie elle-meme et non un defaut de la mesure. La colonne\n");
        r.append("« theorique » rappelle la hauteur que le bruit de generation prevoyait la, pour\n");
        r.append("situer le trou sans jamais le combler dans les grilles.\n\n");
        {
            int manquantsEmprise = 0, manquantsDehors = 0;
            StringBuilder liste = new StringBuilder();
            for (int cz = 0; cz < s.cd; cz++) {
                for (int cx = 0; cx < s.cw; cx++) {
                    if (e.chunkCharge[cz * s.cw + cx]) continue;
                    boolean dans = chunkDansEmprise(s, cx, cz);
                    if (dans) manquantsEmprise++; else manquantsDehors++;
                    if (dans && manquantsEmprise <= 200) {
                        int wx = (s.cx0 + cx) << 4, wz = (s.cz0 + cz) << 4;
                        int b = s.bruit[idx(s, wx + 8, wz + 8) < 0 ? 0 : idx(s, wx + 8, wz + 8)];
                        liste.append("      chunk ").append(s.cx0 + cx).append(',')
                             .append(s.cz0 + cz).append("   blocs x ").append(wx).append("..")
                             .append(wx + 15).append("  z ").append(wz).append("..").append(wz + 15)
                             .append("   theorique Y~").append(b).append('\n');
                    }
                }
            }
            r.append("  ").append(e.nom).append(" : ").append(manquantsEmprise)
             .append(" chunk(s) NON CHARGE(S) DANS L'EMPRISE, ").append(manquantsDehors)
             .append(" hors emprise\n");
            if (liste.length() > 0) r.append(liste);
            r.append('\n');
        }
    }

    // ---------------------------------------------------------------- 3
    private static void section3ChunksNonTouches(StringBuilder r, Session s) {
        titre(r, "3. CHUNKS NON TOUCHES D'UNE ETAPE A L'AUTRE  (sautes par le clear / la pose)");
        if (s.etapes.size() < 2) {
            r.append("  (une seule etape capturee : rien a comparer)\n");
            return;
        }
        for (int n = 1; n < s.etapes.size(); n++) {
            Etape a = s.etapes.get(n - 1), b = s.etapes.get(n);
            int empriseTot = 0, sautes = 0, touches = 0;
            StringBuilder liste = new StringBuilder();
            for (int cz = 0; cz < s.cd; cz++) {
                for (int cx = 0; cx < s.cw; cx++) {
                    int ch = colonnesChangees(s, a, b, cx, cz);
                    boolean dans = chunkDansEmprise(s, cx, cz);
                    if (ch > 0) touches++;
                    if (!dans) continue;
                    empriseTot++;
                    if (ch == 0) {
                        sautes++;
                        if (sautes <= 200) {
                            int wx = (s.cx0 + cx) << 4, wz = (s.cz0 + cz) << 4;
                            boolean chargeA = a.chunkCharge[cz * s.cw + cx];
                            boolean chargeB = b.chunkCharge[cz * s.cw + cx];
                            liste.append("      chunk ").append(s.cx0 + cx).append(',')
                                 .append(s.cz0 + cz).append("   blocs x ").append(wx).append("..")
                                 .append(wx + 15).append("  z ").append(wz).append("..")
                                 .append(wz + 15)
                                 .append("   charge avant=").append(chargeA ? "oui" : "NON")
                                 .append(" apres=").append(chargeB ? "oui" : "NON").append('\n');
                        }
                    }
                }
            }
            r.append("  ").append(a.nom).append("  ->  ").append(b.nom).append('\n');
            r.append("      chunks modifies (toute la zone) : ").append(touches).append('\n');
            r.append("      chunks d'emprise touches        : ").append(empriseTot - sautes)
             .append('/').append(empriseTot).append('\n');
            r.append("      chunks d'emprise SAUTES         : ").append(sautes).append('\n');
            if (liste.length() > 0) r.append(liste);
            r.append('\n');
        }
    }

    private static int colonnesChangees(Session s, Etape a, Etape b, int cx, int cz) {
        int n = 0;
        int bx0 = (s.cx0 + cx) << 4, bz0 = (s.cz0 + cz) << 4;
        for (int oz = 0; oz < 16; oz++) {
            for (int ox = 0; ox < 16; ox++) {
                int i = idx(s, bx0 + ox, bz0 + oz);
                if (i < 0) continue;
                if (a.ySolid[i] == ABSENT || b.ySolid[i] == ABSENT) continue;
                if (a.ySolid[i] != b.ySolid[i] || a.ySurface[i] != b.ySurface[i]
                        || a.nSolides[i] != b.nSolides[i]) n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 4
    private static void section4AncienTerrain(StringBuilder r, Session s) {
        titre(r, "4. ANCIEN TERRAIN RESTE DANS L'EMPRISE");
        Etape prem = s.etapes.get(0), dern = s.etapes.get(s.etapes.size() - 1);
        int tot = 0, inchangees = 0, plusHautQueRef = 0;
        StringBuilder liste = new StringBuilder();
        int listees = 0;
        for (int wz = s.ez0; wz <= s.ez1; wz++) {
            for (int wx = s.ex0; wx <= s.ex1; wx++) {
                int i = idx(s, wx, wz);
                if (i < 0) continue;
                if (prem.ySolid[i] == ABSENT || dern.ySolid[i] == ABSENT) continue;
                tot++;
                boolean identique = prem.ySolid[i] == dern.ySolid[i]
                        && prem.ySurface[i] == dern.ySurface[i]
                        && prem.nSolides[i] == dern.nSolides[i];
                if (identique) {
                    inchangees++;
                    if (dern.ySolid[i] != VIDE && dern.ySolid[i] > s.groundY) {
                        plusHautQueRef++;
                        if (listees < 120) {
                            liste.append("      x=").append(wx).append(" z=").append(wz)
                                 .append("   Y=").append(dern.ySolid[i])
                                 .append("  (+").append(dern.ySolid[i] - s.groundY)
                                 .append(" au-dessus du sol de reference)   chunk ")
                                 .append(wx >> 4).append(',').append(wz >> 4).append('\n');
                            listees++;
                        }
                    }
                }
            }
        }
        r.append("  colonnes d'emprise comparables       : ").append(tot).append('\n');
        r.append("  INCHANGEES de bout en bout           : ").append(inchangees)
         .append(pct(inchangees, tot)).append('\n');
        r.append("  dont plus hautes que le sol de ref.  : ").append(plusHautQueRef)
         .append(pct(plusHautQueRef, tot)).append("   <-- ancien relief visible dans la structure\n");
        if (liste.length() > 0) r.append(liste);
        r.append("\n  Une colonne inchangee entre la premiere et la derniere etape n'a recu ni\n");
        r.append("  coupe ni bloc : elle montre exactement ce qu'il y avait avant.\n");
    }

    /**
     * Composition de la surface dans l'emprise, et air piege sous le sol.
     *
     * <p>Deux questions que la hauteur seule ne tranche pas : « la surface qu'on voit
     * est-elle celle de la structure ou l'ancien terrain » (un GRASS_BLOCK ou un STONE au
     * milieu d'un lac de nether repond tout seul), et « y a-t-il des cavites sous la
     * croute » (un clear qui creuse sans que rien ne retombe laisse de l'air piege).</p>
     */
    private static void section4bComposition(StringBuilder r, Session s, Etape e) {
        titre(r, "4b. COMPOSITION DE LA SURFACE DANS L'EMPRISE, ET AIR PIEGE");
        r.append("  etape mesuree : ").append(e.nom).append("\n\n");

        int[] compte = new int[s.palette.size() + 2];
        int[] compteSolide = new int[s.palette.size() + 2];
        int colonnes = 0, avecAir = 0;
        long totalAir = 0;
        List<int[]> grosAir = new ArrayList<>();
        for (int wz = s.ez0; wz <= s.ez1; wz++) {
            for (int wx = s.ex0; wx <= s.ex1; wx++) {
                int i = idx(s, wx, wz);
                if (i < 0 || e.ySolid[i] == ABSENT) continue;
                colonnes++;
                int id = e.idSurface[i];
                if (id >= 0 && id < compte.length) compte[id]++;
                int ids = e.idSolid[i];
                if (ids >= 0 && ids < compteSolide.length) compteSolide[ids]++;
                int air = e.airSous[i];
                if (air > 0) {
                    avecAir++;
                    totalAir += air;
                    if (air >= 8) grosAir.add(new int[]{air, wx, wz});
                }
            }
        }
        r.append("  blocs de surface, par frequence (colonnes d'emprise lues : ")
         .append(colonnes).append(") :\n");
        List<int[]> tri = new ArrayList<>();
        for (int i = 0; i < compte.length; i++) if (compte[i] > 0) tri.add(new int[]{compte[i], i});
        tri.sort((a, b) -> b[0] - a[0]);
        for (int k = 0; k < Math.min(40, tri.size()); k++) {
            int[] t = tri.get(k);
            String nom = t[1] == 0 ? "(rien)"
                    : (t[1] <= s.palette.size() ? s.palette.get(t[1] - 1) : "(deborde)");
            r.append(String.format("     %9d  %s  %s%n", t[0], pct(t[0], colonnes).trim(), nom));
        }
        r.append("\n  premier bloc SOLIDE de la colonne (l'eau et le feuillage sont ignores) :\n");
        r.append("  C'est lui qui trahit le fond d'origine : un GRASS_BLOCK ou un STONE sous la\n");
        r.append("  nappe du lac veut dire que la coupe n'est jamais passee a cet endroit.\n");
        List<int[]> triS = new ArrayList<>();
        for (int i = 0; i < compteSolide.length; i++) {
            if (compteSolide[i] > 0) triS.add(new int[]{compteSolide[i], i});
        }
        triS.sort((a, b) -> b[0] - a[0]);
        for (int k = 0; k < Math.min(40, triS.size()); k++) {
            int[] t = triS.get(k);
            String nom = t[1] == 0 ? "(rien)"
                    : (t[1] <= s.palette.size() ? s.palette.get(t[1] - 1) : "(deborde)");
            r.append(String.format("     %9d  %s  %s%n", t[0], pct(t[0], colonnes).trim(), nom));
        }

        r.append("\n  colonnes avec de l'air PIEGE sous le premier solide : ").append(avecAir)
         .append(pct(avecAir, colonnes)).append("   total ").append(totalAir)
         .append(" bloc(s) d'air\n");
        grosAir.sort((a, b) -> b[0] - a[0]);
        int n = Math.min(80, grosAir.size());
        if (n > 0) r.append("  les ").append(n).append(" plus profondes (>= 8 blocs d'air) :\n");
        for (int k = 0; k < n; k++) {
            int[] t = grosAir.get(k);
            r.append("      ").append(t[0]).append(" blocs d'air   x=").append(t[1])
             .append(" z=").append(t[2]).append("   chunk ").append(t[1] >> 4)
             .append(',').append(t[2] >> 4).append('\n');
        }
    }

    // ---------------------------------------------------------------- 5
    private static void section5InterChunks(StringBuilder r, Session s, Etape e) {
        titre(r, "5. RACCORDS INTER-CHUNKS  (denivele exactement a la frontiere)");
        r.append("Mesure prise entre la derniere colonne d'un chunk et la premiere du suivant\n");
        r.append("(x=15 contre x=0, puis z=15 contre z=0). C'est le defaut qu'une vue d'ensemble\n");
        r.append("ne montre pas : une marche franche pile sur la couture de deux chunks.\n\n");
        r.append("  etape mesuree : ").append(e.nom).append("\n\n");

        List<int[]> pires = new ArrayList<>();
        long sommeFront = 0;
        int nFront = 0, nAbs = 0;
        // frontieres verticales (entre chunk cx et cx+1)
        for (int cx = 0; cx < s.cw - 1; cx++) {
            for (int z = 0; z < s.d; z++) {
                int xa = ((s.cx0 + cx) << 4) + 15, xb = xa + 1;
                int ia = idx(s, xa, s.z0 + z), ib = idx(s, xb, s.z0 + z);
                if (ia < 0 || ib < 0) continue;
                int ha = e.ySolid[ia], hb = e.ySolid[ib];
                if (!mesurable(ha) || !mesurable(hb)) { nAbs++; continue; }
                int dd = Math.abs(ha - hb);
                sommeFront += dd;
                nFront++;
                if (dd >= 4) pires.add(new int[]{dd, xa, s.z0 + z, 0});
            }
        }
        // frontieres horizontales (entre chunk cz et cz+1)
        for (int cz = 0; cz < s.cd - 1; cz++) {
            for (int x = 0; x < s.w; x++) {
                int za = ((s.cz0 + cz) << 4) + 15, zb = za + 1;
                int ia = idx(s, s.x0 + x, za), ib = idx(s, s.x0 + x, zb);
                if (ia < 0 || ib < 0) continue;
                int ha = e.ySolid[ia], hb = e.ySolid[ib];
                if (!mesurable(ha) || !mesurable(hb)) { nAbs++; continue; }
                int dd = Math.abs(ha - hb);
                sommeFront += dd;
                nFront++;
                if (dd >= 4) pires.add(new int[]{dd, s.x0 + x, za, 1});
            }
        }
        r.append("  paires de colonnes mesurees aux frontieres : ").append(nFront).append('\n');
        r.append("  paires inexploitables (chunk absent)       : ").append(nAbs).append('\n');
        if (nFront > 0) {
            r.append(String.format("  denivele moyen a la frontiere              : %.2f bloc(s)%n",
                    (double) sommeFront / nFront));
        }
        r.append("  paires a denivele >= 4                     : ").append(pires.size()).append('\n');

        // comparaison avec l'interieur des chunks : une couture anormale se voit par ecart
        long sommeInt = 0;
        int nInt = 0;
        for (int z = 0; z < s.d; z++) {
            for (int x = 0; x < s.w - 1; x++) {
                if (((s.x0 + x) & 15) == 15) continue;   // on saute les frontieres
                int ia = idx(s, s.x0 + x, s.z0 + z), ib = idx(s, s.x0 + x + 1, s.z0 + z);
                if (ia < 0 || ib < 0) continue;
                int ha = e.ySolid[ia], hb = e.ySolid[ib];
                if (!mesurable(ha) || !mesurable(hb)) continue;
                sommeInt += Math.abs(ha - hb);
                nInt++;
            }
        }
        if (nInt > 0 && nFront > 0) {
            double mf = (double) sommeFront / nFront, mi = (double) sommeInt / nInt;
            r.append(String.format("  denivele moyen a l'INTERIEUR des chunks    : %.2f bloc(s)%n", mi));
            r.append(String.format("  rapport frontiere / interieur              : %.2f%n", mf / mi));
            r.append("  Un rapport proche de 1 signifie que les coutures ne se distinguent pas du\n");
            r.append("  reste du relief. Nettement au-dessus de 1, le defaut est bien le decoupage\n");
            r.append("  en chunks lui-meme, et non la forme generale du terrain.\n");
        }
        pires.sort((p, q) -> q[0] - p[0]);
        int n = Math.min(120, pires.size());
        if (n > 0) r.append("\n  les ").append(n).append(" pires coutures :\n");
        for (int k = 0; k < n; k++) {
            int[] t = pires.get(k);
            r.append("      ").append(t[0]).append(" blocs   ")
             .append(t[3] == 0 ? "frontiere X entre x=" + t[1] + " et x=" + (t[1] + 1) + ", z=" + t[2]
                               : "frontiere Z entre z=" + t[2] + " et z=" + (t[2] + 1) + ", x=" + t[1])
             .append("   chunks ").append(t[1] >> 4).append(',').append(t[2] >> 4).append('\n');
        }
    }

    private static boolean mesurable(int y) {
        return y != ABSENT && y != VIDE;
    }

    // ---------------------------------------------------------------- 6
    private static void section6Marches(StringBuilder r, Session s, Etape e) {
        titre(r, "6. MARCHES ET FAILLES");
        r.append("  etape mesuree : ").append(e.nom).append("\n\n");
        int[] histo = new int[9];
        List<int[]> top = new ArrayList<>();
        for (int z = 0; z < s.d; z++) {
            for (int x = 0; x < s.w; x++) {
                int i = z * s.w + x;
                if (!mesurable(e.ySolid[i])) continue;
                int pire = 0;
                if (x + 1 < s.w) pire = Math.max(pire, delta(e, i, i + 1));
                if (z + 1 < s.d) pire = Math.max(pire, delta(e, i, i + s.w));
                histo[tranche(pire)]++;
                if (pire >= 6) top.add(new int[]{pire, s.x0 + x, s.z0 + z});
            }
        }
        String[] noms = {"0", "1", "2-3", "4-5", "6-9", "10-15", "16-25", "26-50", ">50"};
        r.append("  denivele maximal avec le voisin est/sud, par colonne :\n");
        for (int i = 0; i < histo.length; i++) {
            r.append(String.format("     %-7s %9d%n", noms[i], histo[i]));
        }
        top.sort((a, b) -> b[0] - a[0]);
        r.append("  colonnes a marche >= 6 : ").append(top.size()).append('\n');
        int n = Math.min(120, top.size());
        if (n > 0) r.append("  les ").append(n).append(" plus hautes :\n");
        for (int k = 0; k < n; k++) {
            int[] t = top.get(k);
            r.append("      ").append(t[0]).append(" blocs   x=").append(t[1])
             .append(" z=").append(t[2]).append("   chunk ").append(t[1] >> 4)
             .append(',').append(t[2] >> 4)
             .append(dansEmprise(s, t[1], t[2]) ? "   (dans l'emprise)" : "   (dehors)")
             .append('\n');
        }
    }

    private static int delta(Etape e, int i, int j) {
        if (!mesurable(e.ySolid[i]) || !mesurable(e.ySolid[j])) return 0;
        return Math.abs(e.ySolid[i] - e.ySolid[j]);
    }

    private static int tranche(int d) {
        if (d == 0) return 0;
        if (d == 1) return 1;
        if (d <= 3) return 2;
        if (d <= 5) return 3;
        if (d <= 9) return 4;
        if (d <= 15) return 5;
        if (d <= 25) return 6;
        if (d <= 50) return 7;
        return 8;
    }

    // ---------------------------------------------------------------- 7
    private static void section7Profil(StringBuilder r, Session s) {
        titre(r, "7. PROFIL DU RACCORD, DU BORD VERS L'EXTERIEUR");
        Etape prem = s.etapes.get(0), dern = s.etapes.get(s.etapes.size() - 1);
        int rayon = RING_CHUNKS * 16;
        double[] sa = new double[2 * rayon + 1], sb = new double[2 * rayon + 1];
        int[] n = new int[2 * rayon + 1];
        for (int z = 0; z < s.d; z++) {
            for (int x = 0; x < s.w; x++) {
                int dBord = distanceAuBord(s, s.x0 + x, s.z0 + z);
                if (dBord < -rayon || dBord > rayon) continue;
                int i = z * s.w + x;
                if (!mesurable(prem.ySolid[i]) || !mesurable(dern.ySolid[i])) continue;
                int k = dBord + rayon;
                sa[k] += prem.ySolid[i];
                sb[k] += dern.ySolid[i];
                n[k]++;
            }
        }
        r.append("  distance au bord de l'emprise : negatif = dedans, positif = dehors.\n");
        r.append(String.format("    %8s %10s %10s %10s %10s%n",
                "dist", "avant", "apres", "delta", "colonnes"));
        for (int k = 0; k < n.length; k++) {
            if (n[k] == 0) continue;
            double a = sa[k] / n[k], b = sb[k] / n[k];
            r.append(String.format("    %8d %10.2f %10.2f %10.2f %10d%n",
                    k - rayon, a, b, b - a, n[k]));
        }
        r.append("\n  Une colonne « delta » qui tombe brutalement a zero des le premier bloc\n");
        r.append("  exterieur signe un bord vertical : aucune transition n'a ete appliquee.\n");
    }

    private static int distanceAuBord(Session s, int x, int z) {
        if (dansEmprise(s, x, z)) {
            return -Math.min(Math.min(x - s.ex0, s.ex1 - x), Math.min(z - s.ez0, s.ez1 - z));
        }
        return Math.max(Math.max(s.ex0 - x, x - s.ex1), Math.max(s.ez0 - z, z - s.ez1));
    }

    // ---------------------------------------------------------------- 8
    private static void section8Vues(StringBuilder r, Session s, Etape e, Etape prec) {
        int p = pas(s);
        titre(r, "8. VUES DU DESSUS EN CARACTERES  (1 caractere = " + p + " x " + p + " blocs)");
        r.append("  Ecart au sol de reference Y=").append(s.groundY).append(" :\n");
        r.append("    '#' <-15   ';' -15..-8   ':' -7..-4   ',' -3..-2   '.' -1..+1\n");
        r.append("    '-' +2..+3  '=' +4..+7   '+' +8..+15  '*' >+15\n");
        r.append("    '@' chunk NON CHARGE     ' ' colonne vide\n");
        r.append("    '[' ']' bords gauche/droit de l'emprise   '_' bords haut/bas\n");
        r.append("  Les lignes sont groupees par 16 (un chunk) et les colonnes separees par '|'.\n\n");
        r.append("  >>> RELIEF A CETTE ETAPE : ").append(e.nom).append('\n');
        vueHauteurs(r, s, e, p);
        r.append('\n');
        r.append("  >>> CHARGEMENT DES CHUNKS (1 caractere = 1 chunk)\n");
        r.append("      dans l'emprise : 'O' charge,  'X' NON CHARGE  <-- a examiner\n");
        r.append("      dehors         : 'o' charge,  '.' non charge (attendu)\n\n");
        vueChunks(r, s, e);
        r.append('\n');
        if (prec != null) {
            r.append("  >>> CHANGEMENTS DEPUIS ").append(prec.nom)
             .append("   ('#' modifie, ' ' intact, '@' chunk absent)\n");
            vueDiff(r, s, prec, e, p);
            r.append('\n');
        }
    }

    private static int pas(Session s) {
        int p = 1;
        while (s.w / p > 176) p += 1;
        while (16 % p != 0 && p < 16) p += 1;   // un caractere ne doit jamais chevaucher 2 chunks
        return p;
    }

    private static void regleX(StringBuilder r, Session s, int p, int marge) {
        // Deux lignes de graduation : centaines/dizaines du X monde au debut de chaque chunk.
        int parChunk = 16 / p;
        StringBuilder l1 = new StringBuilder(), l2 = new StringBuilder();
        l1.append(" ".repeat(marge));
        l2.append(" ".repeat(marge));
        for (int cx = 0; cx < s.cw; cx++) {
            String etiq = Integer.toString((s.cx0 + cx) << 4);
            if (etiq.length() > parChunk) etiq = etiq.substring(etiq.length() - parChunk);
            l1.append('|').append(etiq);
            for (int k = etiq.length(); k < parChunk; k++) l1.append(' ');
            l2.append('|').append(Integer.toString(s.cx0 + cx).length() <= parChunk
                    ? Integer.toString(s.cx0 + cx) : "");
            int ln = Integer.toString(s.cx0 + cx).length();
            for (int k = (ln <= parChunk ? ln : 0); k < parChunk; k++) l2.append(' ');
        }
        r.append(l1).append("|   <- X monde au debut de chaque chunk\n");
        r.append(l2).append("|   <- numero de chunk X\n");
    }

    private static void vueHauteurs(StringBuilder r, Session s, Etape e, int p) {
        regleX(r, s, p, 9);
        for (int z = 0; z < s.d; z += p) {
            if ((z & 15) == 0 && z > 0) {
                r.append(" ".repeat(9));
                for (int cx = 0; cx < s.cw; cx++) {
                    r.append('+').append("-".repeat(16 / p));
                }
                r.append("+\n");
            }
            r.append(String.format("z=%6d ", s.z0 + z));
            for (int x = 0; x < s.w; x += p) {
                if ((x & 15) == 0) r.append('|');
                r.append(carHauteur(s, e, z * s.w + x, s.x0 + x, s.z0 + z, p));
            }
            r.append("|\n");
        }
    }

    private static char carHauteur(Session s, Etape e, int i, int wx, int wz, int p) {
        int h = e.ySolid[i];
        if (h == ABSENT) return '@';
        boolean dansX = wx + p > s.ex0 && wx <= s.ex1;
        boolean dansZ = wz + p > s.ez0 && wz <= s.ez1;
        if (dansZ && wx <= s.ex0 && wx + p > s.ex0) return '[';
        if (dansZ && wx <= s.ex1 && wx + p > s.ex1) return ']';
        if (dansX && ((wz <= s.ez0 && wz + p > s.ez0) || (wz <= s.ez1 && wz + p > s.ez1))) return '_';
        if (h == VIDE) return ' ';
        int d = h - s.groundY;
        if (d > 15) return '*';
        if (d >= 8) return '+';
        if (d >= 4) return '=';
        if (d >= 2) return '-';
        if (d >= -1) return '.';
        if (d >= -3) return ',';
        if (d >= -7) return ':';
        if (d >= -15) return ';';
        return '#';
    }

    private static void vueChunks(StringBuilder r, Session s, Etape e) {
        r.append("          ");
        for (int cx = 0; cx < s.cw; cx++) r.append(Math.abs((s.cx0 + cx) % 10));
        r.append("   <- chunk X (dernier chiffre)\n");
        for (int cz = 0; cz < s.cd; cz++) {
            r.append(String.format("   cz=%5d ", s.cz0 + cz));
            for (int cx = 0; cx < s.cw; cx++) {
                boolean dans = chunkDansEmprise(s, cx, cz);
                boolean ch = e.chunkCharge[cz * s.cw + cx];
                r.append(dans ? (ch ? 'O' : 'X') : (ch ? 'o' : '.'));
            }
            r.append('\n');
        }
    }

    private static void vueDiff(StringBuilder r, Session s, Etape a, Etape b, int p) {
        regleX(r, s, p, 9);
        for (int z = 0; z < s.d; z += p) {
            r.append(String.format("z=%6d ", s.z0 + z));
            for (int x = 0; x < s.w; x += p) {
                if ((x & 15) == 0) r.append('|');
                int i = z * s.w + x;
                if (a.ySolid[i] == ABSENT || b.ySolid[i] == ABSENT) r.append('@');
                else r.append(a.ySolid[i] != b.ySolid[i] || a.ySurface[i] != b.ySurface[i]
                        || a.nSolides[i] != b.nSolides[i] ? '#' : ' ');
            }
            r.append("|\n");
        }
    }

    // ---------------------------------------------------------------- 9
    private static void section9Grilles(StringBuilder r, Session s, Etape e, Etape prec) {
        titre(r, "9. DONNEES AU BLOC PRES -- UNE VALEUR PAR COLONNE");
        r.append("Une ligne par Z, une valeur par colonne X, groupees par chunk. Les Y sont\n");
        r.append("ABSOLUS (coordonnee du monde), jamais relatifs.\n");
        r.append("  ----  chunk non charge : la mesure n'existe pas, et rien ne la remplace\n");
        r.append("  ....  colonne vide sur toute la tranche sondee\n\n");

        r.append("-".repeat(100)).append('\n');
        r.append(" 9a. ySolid -- sommet du premier bloc SOLIDE (ni air ni fluide)\n");
        r.append("-".repeat(100)).append('\n');
        grilleY(r, s, e.ySolid);

        r.append('\n').append("-".repeat(100)).append('\n');
        r.append(" 9b. ySurface -- sommet du premier bloc NON-AIR (eau et feuillage compris)\n");
        r.append("-".repeat(100)).append('\n');
        grilleY(r, s, e.ySurface);

        if (prec != null) {
            int[] ecart = new int[s.w * s.d];
            for (int i2 = 0; i2 < ecart.length; i2++) {
                if (e.ySolid[i2] == ABSENT || prec.ySolid[i2] == ABSENT) ecart[i2] = ABSENT;
                else if (!mesurable(e.ySolid[i2]) || !mesurable(prec.ySolid[i2])) ecart[i2] = VIDE;
                else ecart[i2] = e.ySolid[i2] - prec.ySolid[i2];
            }
            r.append('\n').append("-".repeat(100)).append('\n');
            r.append(" 9c. ECART DE ySolid DEPUIS ").append(prec.nom).append('\n');
            r.append("     0 = colonne INTACTE depuis l'etape precedente. Un chunk entierement a\n");
            r.append("     zero apres le clear est un chunk que le clear n'a pas touche.\n");
            r.append("     negatif = terrain abaisse, positif = terrain remonte.\n");
            r.append("-".repeat(100)).append('\n');
            grilleY(r, s, ecart);
        }

        r.append('\n').append("-".repeat(100)).append('\n');
        r.append(" 9d. EAU -- Y absolu de la surface du fluide, par chunk en contenant\n");
        r.append("-".repeat(100)).append('\n');
        grilleParChunk(r, s, e.yWater, null);

        r.append('\n').append("-".repeat(100)).append('\n');
        r.append(" 9e. AIR PIEGE sous le premier solide, par chunk en contenant\n");
        r.append("     (nombre de blocs d'air trouves sous le sommet solide, sonde sur ")
         .append(AIR_PROBE_DEPTH).append(" blocs)\n");
        r.append("-".repeat(100)).append('\n');
        grilleParChunk(r, s, null, e.airSous);
    }

    /**
     * Grille limitee aux chunks qui portent reellement l'information. L'eau et l'air
     * piege sont absents de la grande majorite des 528 chunks : les emettre tous
     * coûterait des centaines de Kio de zeros et noierait les quelques chunks qui
     * comptent. Un chunk omis est declare omis, jamais sous-entendu.
     */
    private static void grilleParChunk(StringBuilder r, Session s, int[] data, short[] court) {
        int emis = 0, omis = 0, absents = 0;
        StringBuilder corps = new StringBuilder();
        for (int cz = 0; cz < s.cd; cz++) {
            for (int cx = 0; cx < s.cw; cx++) {
                int bx = (s.cx0 + cx) << 4, bz = (s.cz0 + cz) << 4;
                boolean porteur = false, vuAbsent = false;
                for (int oz = 0; oz < 16 && !porteur; oz++) {
                    for (int ox = 0; ox < 16; ox++) {
                        int i = idx(s, bx + ox, bz + oz);
                        if (i < 0) continue;
                        if (data != null) {
                            if (data[i] == ABSENT) { vuAbsent = true; continue; }
                            if (mesurable(data[i])) { porteur = true; break; }
                        } else {
                            if (court[i] > 0) { porteur = true; break; }
                        }
                    }
                }
                if (!porteur) { if (vuAbsent) absents++; else omis++; continue; }
                emis++;
                corps.append("  chunk ").append(s.cx0 + cx).append(',').append(s.cz0 + cz)
                     .append("   blocs x ").append(bx).append("..").append(bx + 15)
                     .append("  z ").append(bz).append("..").append(bz + 15)
                     .append(chunkDansEmprise(s, cx, cz) ? "   [EMPRISE]" : "").append('\n');
                for (int oz = 0; oz < 16; oz++) {
                    corps.append(String.format("   z=%6d ", bz + oz));
                    for (int ox = 0; ox < 16; ox++) {
                        int i = idx(s, bx + ox, bz + oz);
                        if (i < 0) { corps.append("  ??"); continue; }
                        if (data != null) {
                            int v = data[i];
                            if (v == ABSENT) corps.append("----");
                            else if (!mesurable(v)) corps.append("....");
                            else corps.append(String.format("%4d", v));
                        } else {
                            corps.append(String.format("%4d", (int) court[i]));
                        }
                    }
                    corps.append('\n');
                }
                corps.append('\n');
            }
        }
        r.append("  ").append(emis).append(" chunk(s) emis, ").append(omis)
         .append(" omis faute de contenu, ").append(absents)
         .append(" non charges (donc non mesurables)\n\n");
        r.append(corps);
    }

    private static void grilleY(StringBuilder r, Session s, int[] data) {
        // regle X : le X monde du debut de chaque chunk, aligne sur 4 caracteres par colonne
        r.append(" ".repeat(9));
        for (int cx = 0; cx < s.cw; cx++) {
            String e = "x=" + ((s.cx0 + cx) << 4);
            r.append('|').append(e);
            for (int k = e.length(); k < 16 * 4; k++) r.append(' ');
        }
        r.append("|\n");
        for (int z = 0; z < s.d; z++) {
            if ((z & 15) == 0) {
                r.append(" ".repeat(9));
                for (int cx = 0; cx < s.cw; cx++) r.append('+').append("-".repeat(64));
                r.append("+\n");
            }
            r.append(String.format("z=%6d ", s.z0 + z));
            for (int x = 0; x < s.w; x++) {
                if ((x & 15) == 0) r.append('|');
                int v = data[z * s.w + x];
                if (v == ABSENT) r.append("----");
                else if (v == VIDE) r.append("....");
                else r.append(String.format("%4d", v));
            }
            r.append("|\n");
        }
    }

    // ---------------------------------------------------------------- 10
    private static void section10Palette(StringBuilder r, Session s) {
        titre(r, "10. PALETTE DES BLOCS");
        r.append("0\t(rien / colonne vide)\n");
        for (int i = 0; i < s.palette.size(); i++) {
            r.append(i + 1).append('\t').append(s.palette.get(i)).append('\n');
        }
    }

    private static String nomBloc(Session s, int id) {
        if (id <= 0) return "(rien)";
        return id <= s.palette.size() ? s.palette.get(id - 1) : "(deborde)";
    }

    /**
     * Une ligne par chunk de la zone. C'est la vue qui permet de repondre d'un coup
     * d'oeil a « quels chunks manquent » et « lesquels gardent l'ancien terrain » sans
     * lire une seule grille : un chunk d'emprise dont l'ecart moyen au relief theorique
     * vaut environ zero apres le clear n'a jamais ete touche.
     */
    private static void tableauParChunk(StringBuilder r, Session s, Etape e) {
        titre(r, "3b. TABLEAU PAR CHUNK  (une ligne par chunk de toute la zone)");
        r.append("E = chunk dans l'emprise de la structure. d.theo = ecart MOYEN au relief\n");
        r.append("theorique de generation : proche de 0 => le terrain d'origine est intact ici.\n");
        r.append("Un chunk ABSENT n'a aucune valeur : elle n'est ni estimee ni comblee.\n\n");
        r.append(String.format("%-9s %-23s %s %-6s %5s %5s %5s %5s %5s %5s %8s  %s%n",
                "chunk", "blocs x / z", "E", "etat", "lues", "yMin", "yMoy", "yMax",
                "vides", "air", "d.theo", "bloc de surface dominant"));
        for (int cz = 0; cz < s.cd; cz++) {
            for (int cx = 0; cx < s.cw; cx++) {
                int bx = (s.cx0 + cx) << 4, bz = (s.cz0 + cz) << 4;
                boolean charge = e.chunkCharge[cz * s.cw + cx];
                boolean dans = chunkDansEmprise(s, cx, cz);
                String nom = (s.cx0 + cx) + "," + (s.cz0 + cz);
                String blocs = bx + ".." + (bx + 15) + " / " + bz + ".." + (bz + 15);
                if (!charge) {
                    r.append(String.format("%-9s %-23s %s %-6s %5s %5s %5s %5s %5s %5s %8s  %s%n",
                            nom, blocs, dans ? "E" : " ", "ABSENT", "-", "-", "-", "-", "-", "-",
                            "-", dans ? "<<< CHUNK D'EMPRISE NON CHARGE" : ""));
                    continue;
                }
                int lues = 0, vides = 0, air = 0, nEcart = 0;
                int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
                long somme = 0, sommeEcart = 0;
                int[] freq = new int[s.palette.size() + 2];
                for (int oz = 0; oz < 16; oz++) {
                    for (int ox = 0; ox < 16; ox++) {
                        int i = idx(s, bx + ox, bz + oz);
                        if (i < 0) continue;
                        int v = e.ySolid[i];
                        if (v == ABSENT) continue;
                        if (!mesurable(v)) { vides++; continue; }
                        lues++;
                        somme += v;
                        if (yMin > v) yMin = v;
                        if (yMax < v) yMax = v;
                        // ABSENT vaut Integer.MIN_VALUE : le soustraire deborderait.
                        if (mesurable(s.bruit[i])) { sommeEcart += v - s.bruit[i]; nEcart++; }
                        if (e.airSous[i] > 0) air++;
                        int id = e.idSurface[i];
                        if (id >= 0 && id < freq.length) freq[id]++;
                    }
                }
                int best = 0;
                for (int k = 1; k < freq.length; k++) if (freq[k] > freq[best]) best = k;
                r.append(String.format("%-9s %-23s %s %-6s %5d %5s %5s %5s %5d %5d %8s  %s%n",
                        nom, blocs, dans ? "E" : " ", "ok", lues,
                        lues == 0 ? "-" : Integer.toString(yMin),
                        lues == 0 ? "-" : Long.toString(somme / lues),
                        lues == 0 ? "-" : Integer.toString(yMax),
                        vides, air,
                        nEcart == 0 ? "-" : String.format("%+.1f", (double) sommeEcart / nEcart),
                        lues == 0 ? "" : nomBloc(s, best)));
            }
        }
    }

    /**
     * Tranches verticales. Une carte vue du dessus ne montre ni une marche, ni une
     * crevasse, ni une dalle restee en l'air : il faut regarder le terrain DE COTE.
     */
    private static void coupesVerticales(StringBuilder r, Session s, Etape e) {
        titre(r, "8b. COUPES VERTICALES  (le terrain vu de cote)");
        r.append("Y absolu en ordonnee, distance horizontale en abscisse.\n");
        r.append("  '#' solide   '~' fluide   ':' feuillage ou couverture   ' ' air\n");
        r.append("  '@' chunk non charge      '!' sommet d'une colonne ayant de l'AIR PIEGE dessous\n");
        r.append("RECONSTRUCTION ASSUMEE : la silhouette est deduite de ySolid / yWater / ySurface,\n");
        r.append("ce ne sont pas des blocs relus un par un. Une cavite interne n'apparait donc pas\n");
        r.append("en creux ; elle est signalee par '!' au sommet de la colonne concernee.\n\n");
        int p = pas(s);
        int yHaut = Math.min(s.yTop, s.groundY + 24);
        int yBas = Math.max(s.yFloor, s.groundY - 56);
        for (int k = 1; k <= 4; k++) {
            coupe(r, s, e, p, yBas, yHaut, s.ez0 + (int) ((long) (s.ez1 - s.ez0) * k / 5), true);
        }
        for (int k = 1; k <= 4; k++) {
            coupe(r, s, e, p, yBas, yHaut, s.ex0 + (int) ((long) (s.ex1 - s.ex0) * k / 5), false);
        }
    }

    private static void coupe(StringBuilder r, Session s, Etape e, int p,
                              int yBas, int yHaut, int fixe, boolean selonX) {
        int n = selonX ? s.w : s.d;
        r.append(selonX ? "  >>> COUPE EST-OUEST a z=" : "  >>> COUPE NORD-SUD a x=").append(fixe)
         .append("   abscisse = ").append(selonX ? 'X' : 'Z').append(' ')
         .append(selonX ? s.x0 : s.z0).append(" -> ")
         .append(selonX ? s.x0 + s.w - 1 : s.z0 + s.d - 1)
         .append(", 1 caractere = ").append(p).append(" blocs\n");
        for (int y = yHaut; y >= yBas; y--) {
            r.append(String.format("  y=%5d ", y));
            for (int t = 0; t < n; t += p) {
                int i = selonX ? idx(s, s.x0 + t, fixe) : idx(s, fixe, s.z0 + t);
                if (i < 0) { r.append(' '); continue; }
                int sol = e.ySolid[i];
                if (sol == ABSENT) { r.append('@'); continue; }
                char c = ' ';
                if (mesurable(sol) && y <= sol) {
                    c = (y == sol && e.airSous[i] > 0) ? '!' : '#';
                } else if (mesurable(e.yWater[i]) && y <= e.yWater[i]) {
                    c = '~';
                } else if (mesurable(e.ySurface[i]) && y <= e.ySurface[i]) {
                    c = ':';
                }
                r.append(c);
            }
            r.append('\n');
        }
        r.append("          ");
        for (int t = 0; t < n; t += p) {
            int w = selonX ? s.x0 + t : s.z0 + t;
            boolean dedans = selonX ? (w >= s.ex0 && w <= s.ex1) : (w >= s.ez0 && w <= s.ez1);
            r.append(dedans ? '=' : '-');
        }
        r.append("   '=' sous l'emprise de la structure\n\n");
    }

    private static String pct(int a, int b) {
        return b <= 0 ? "" : String.format("  (%.1f %%)", 100.0 * a / b);
    }
}
