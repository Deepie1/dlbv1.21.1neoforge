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
 * FICHIER PAR ETAPE -- {@code 01_1_DEBUT_CLEAR.txt}, {@code 02_2_APRES_CLEAR_ET_TALUS.txt}, ... --
 * avec les sections RAPPORT, CARTES et GRILLES clairement separees dans chaque fichier,
 * plus un {@code 00_SOMMAIRE.txt} pour comparer les etapes. Le chemin est annonce en chat.
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
    private static final int MAX_ETAPES = 12;

    /**
     * T477 : cadence de capture PENDANT la pose.
     *
     * <p>Mesure en jeu (23/11) : l'etape 3_APRES_PASTE est arrivee vide (0 chunk sur
     * 81). Cause : le lac fait {@code PASTE_Q.poll()} puis {@code ChunkKeeper.release()}
     * dans le MEME tick -- la zone est relachee et les chunks sont deja partis quand le
     * scan observe la file vide. Aucune capture declenchee par le vidage de la file ne
     * peut donc etre pleine. Le remede n'est pas d'arriver plus vite, c'est de ne plus
     * dependre de cet instant : on capture regulierement TANT QUE la pose tourne, et la
     * derniere de ces captures porte une donnee reelle du terrain pose.</p>
     */
    private static final long CAPTURE_PASTE_MS = 3_000L;

    /** Largeur maximale d'une vue en caracteres, avant sous-echantillonnage. */
    private static final int VUE_LARGEUR_MAX = 400;

    /** Hauteur de la vue 3D oblique, en lignes. */
    private static final int VUE3D_HAUTEUR = 120;

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

    /**
     * Attente maximale du relief theorique avant de rediger sans lui.
     *
     * <p>T477 : 120 s ne suffisaient pas. Mesure en jeu (23/11) : le serveur accumulait
     * 588 ticks de retard (« Can't keep up! ») et le fil, lance en MIN_PRIORITY, n'a pas
     * eu de CPU -- le delai a expire et TOUTES les sections theoriques sont sorties
     * vides. Un delai calibre sur un serveur sain expire sur un serveur charge, et un
     * repli silencieux-mais-degrade reste une perte de donnee. Le delai est double ET la
     * priorite du fil relevee (voir lancerBruit).</p>
     */
    /**
     * T493 : 20 s, et non plus 240 s.
     *
     * <p>Mesure du 26/11 : le redacteur a attendu QUATRE MINUTES (06:11:07 -> 06:15:07),
     * le relief n'etait toujours pas pret, et il a ecrit sans lui -- puis a recommence
     * pour la seconde session (06:12:49 -> 06:16:49). Les « 249 s de redaction »
     * annoncees etaient 240 s d'attente et 9 s d'ecriture reelle. Attendre quatre
     * minutes un resultat qui n'arrivera pas ne le fait pas arriver : ca retarde juste
     * les fichiers d'autant. Le relief est desormais BORNE (voir BRUIT_BUDGET_MS) :
     * s'il doit arriver, il arrive vite ; sinon il n'est pas lance du tout.</p>
     */
    private static final long BRUIT_ATTENTE_MS = 20_000L;

    /**
     * T493 -- BUDGET DUR DU RELIEF THEORIQUE.
     *
     * <p>Cout mesure le 26/11 : 12 544 colonnes en 442 530 ms, soit 35,3 ms PAR COLONNE.
     * La zone reelle du lac en comptait 230 144 : au meme cout, 8 119 s, soit
     * <b>2 h 17</b>. Ce calcul n'avait aucune chance d'aboutir -- et il a tourne quand
     * meme, en NORM_PRIORITY-1, pendant 7 min 22, pendant que le serveur accumulait
     * 10 a 15 secondes de retard par rapport au temps reel, SANS DISCONTINUER, bien
     * apres que le lac fut termine (06:13:41 -> 06:19:28 au moins). Le joueur a paye
     * huit minutes de jeu injouable pour une section de rapport qui est sortie vide.
     *
     * <p>Un diagnostic qui coute plus cher que le defaut qu'il diagnostique n'est pas un
     * diagnostic. Le calcul s'alloue desormais ce budget, estime son cout AVANT de
     * commencer, et s'il n'entre pas il ne commence pas.
     */
    private static final long BRUIT_BUDGET_MS = 45_000L;

    /** T493 : nombre de sondes de calibrage avant de decider du pas d'echantillonnage. */
    private static final int BRUIT_SONDES_CALIBRAGE = 64;

    /** T493 : pas maximal du reseau d'echantillonnage (le bruit est lisse, 16 suffit). */
    private static final int BRUIT_PAS_MAX = 16;

    /** T493 : taille d'un lot entre deux respirations du fil. */
    private static final int BRUIT_LOT = 192;

    /** Au-dela de ce silence, on ecrit au lieu de risquer de tout perdre. */
    private static final long SILENCE_MAX_MS = 300_000L;

    /**
     * T491 -- DELAI DE GRACE APRES LE RELACHEMENT DE LA ZONE.
     *
     * <p>Le lac relache son emprise dans le tick meme ou sa file de pose se vide.
     * Jusqu'ici la session se fermait la, et l'etape {@code 4_FIN} etait meme
     * SUPPRIMEE pour ne pas dupliquer la photo de {@code 3_APRES_PASTE}. Le
     * resultat : plus rien ne mesurait l'habillage, la replantation et la
     * finition, qui se jouent APRES la pose. Autrement dit l'outil fermait les
     * yeux exactement la ou le defaut signale par le dev se trouve (« le sol
     * n'a pas ete repeint, tu n'as pas replante »). Un instrument qui s'arrete
     * avant la fin de l'operation ne mesure pas l'operation.
     *
     * <p>La session reste donc ouverte ce delai apres le relachement, puis prend
     * {@code 4_FIN}. Les chunks peuvent avoir commence a se decharger : ce n'est
     * pas masque, la capture compte et liste ses colonnes non mesurables.
     */
    private static final long APRES_RELACHE_MS = 30_000L;

    /** Annonce unique : son absence du log prouve que l'outil n'est pas dans la build. */
    private static volatile boolean banniereFaite;

    private static final class Etape {
        final String nom;
        final String detail;
        final long tMs;
        // T477 : NON finals. La grille est realignee quand l'emprise epinglee grandit
        // (voir elargir) ; une etape deja capturee doit suivre ce realignement, sinon
        // elle reste indexee sur une grille qui n'existe plus.
        int[] ySolid;      // sommet du premier bloc solide (ni air ni fluide)
        int[] ySurface;    // sommet du premier bloc non-air (eau et feuillage compris)
        int[] yWater;      // surface du premier fluide
        char[] idSolid;    // index palette du bloc solide de surface
        char[] idSurface;  // index palette du bloc de surface
        short[] airSous;   // blocs d'air piges sous le premier solide
        short[] nSolides;  // nombre de blocs pleins dans la tranche (densite)
        boolean[] coucheNeige; // vraie couche SNOW dans les 48 blocs sous/au-dessus de la surface observee
        boolean[] chunkCharge;
        /** T477 : colonnes effectivement lues par chunk -- doit valoir 256 partout. */
        short[] colonnesParChunk;
        /** T477 : etape capturee avant un elargissement, puis realignee. */
        boolean remappee;
        int lues, absentes;
        /** T477 : compteurs de couverture. Un chunk saute doit se voir, pas se deviner. */
        int chunksVisites, colonnesVisitees, horsGrille;
        /** T477 : la zone a ete relachee avant la capture -- les chunks ne sont plus la. */
        boolean zoneRelachee;
        /** Instantane du potentiel directionnel neige, jamais recalcule par le scan. */
        deepluckyblock.util.TerrainMother.SnowSpreadSnapshot snowSpread;

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
            this.coucheNeige = new boolean[n];
            this.chunkCharge = new boolean[nc];
            this.colonnesParChunk = new short[nc];
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
        /** Dernier potentiel neige publie par TerrainMother, conserve apres son end(). */
        deepluckyblock.util.TerrainMother.SnowSpreadSnapshot snowSpreadSnapshot;
        /**
         * Relief theorique. VOLATILE et publie d'un seul coup a la fin du calcul : il est
         * rempli par un fil de fond, et un lecteur ne doit jamais voir un tableau a moitie
         * ecrit.
         */
        volatile int[] bruit;
        Thread filBruit;        // calcul du relief theorique, lance des l'ouverture
        /**
         * T477 : numero de grille. Un elargissement relance le calcul du relief ; le fil
         * precedent travaille encore sur l'ANCIENNE grille et ne doit jamais publier son
         * tableau -- il aurait la mauvaise taille et le mauvais origine.
         */
        volatile int bruitGen;

        /**
         * T493 : annulation REELLE du fil de relief. Avant, le seul moyen d'arreter le
         * calcul etait d'incrementer bruitGen -- ce que seule une relance faisait. Quand
         * le redacteur renoncait (06:15:07), le fil continuait pour personne : il a
         * tourne 3 min 25 de plus, jusqu'a 06:18:32, et a publie un resultat que plus
         * aucun fichier n'allait lire. Du CPU vole au serveur pour rien.
         */
        volatile boolean bruitAnnule;
        int dernierPasteQ = -1;
        boolean zoneVue;
        /** T477 : au moins une capture n'a pas couvert toute la grille. */
        boolean couvertureRompue;
        /** T477 : nombre de fois que l'emprise epinglee a grandi sous nos pieds. */
        int elargissements;
        /** T477 : emprise d'origine, avant tout elargissement. */
        int ox0, oz0, ox1, oz1;
        // T480 : arme a la transition file vide -> file pleine, jamais laisse a 0.
        long dernierePastePeriodiqueMs;

        /** T491 : instant du relachement de la zone, 0 tant qu'elle est tenue. */
        long relacheeDepuisMs;

        /**
         * T491 : vrai des que la file de pose a quitte 0, c'est-a-dire des qu'une
         * construction a reellement eu lieu dans cette emprise. Une emprise
         * epinglee puis relachee sans que rien ne soit bati est une SONDE DE
         * PLACEMENT, pas un chantier : la mesurer produit deux photos identiques
         * d'un terrain intact.
         */
        boolean chantierVu;
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
        couperBruit(session);   // T493 : un scan manuel ne doit pas laisser courir le precedent
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
        // T477 : sondage a CHAQUE tick, y compris session ouverte. Avant : 1 tick sur 8.
        // Le lac fait PASTE_Q.poll() puis ChunkKeeper.release() dans le meme tick ; en
        // n'ouvrant l'oeil qu'un tick sur huit, le scan arrivait systematiquement apres
        // le relachement et capturait une zone vide. Le cout est d'un appel a
        // ChunkKeeper et d'une lecture de file par tick -- nul a cette echelle.

        for (ServerLevel level : server.getAllLevels()) {
            int[] zone = zoneTenue(level);
            Session s = session;

            // --- ouverture : la zone epinglee apparait = le pipeline vient de reserver
            //     son emprise, juste avant le premier coup de pioche du clear.
            if (s == null && zone != null && phase == Phase.ARME) {
                int gy = solDeReference(level, zone);
                // T493 : couper le relief de la session precedente AVANT d'en ouvrir une
                // autre. Le 26/11, la session fantome a lance son propre calcul pendant
                // que celui de la vraie session tournait encore : les deux se sont
                // disputes la machine, et c'est le resultat de la FAUSSE (12 544
                // colonnes) qui est sorti a 06:18:32 -- celui de la vraie n'a jamais
                // existe.
                couperBruit(session);
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

            // --- T477 : l'emprise epinglee peut GRANDIR apres l'ouverture (la sonde de
            //     placement est epinglee avant l'emprise reelle de la structure). On la
            //     relit a chaque tick et la session s'aligne dessus.
            if (zone != null) elargir(s, zone);

            // --- transitions de la file de pose
            int q = pasteQueueSize();
            if (q >= 0) {
                if (q > 0) s.chantierVu = true;
                if (s.dernierPasteQ == 0 && q > 0) {
                    // T478 : ce jalon ne date plus le seul clear. Depuis que la
                    // sequence est clear -> talus -> pose, la file de pose recoit son
                    // job APRES le raccord de terrain. Le nom le dit, sinon le rapport
                    // attribuerait au clear un relief que le talus vient de modeler --
                    // un jalon qui date la mauvaise chose est un piege a diagnostic.
                    capturer(s, "2_APRES_CLEAR_ET_TALUS",
                            "la file de pose vient de recevoir son job : le clear ET le "
                            + "raccord de terrain sont termines");
                    // T480 : on ARME ici le chronometre des captures pendant la pose.
                    // Sans cela il vaut 0, le delai est deja ecoule au premier tick ou la
                    // file est non vide, et la premiere capture "PENDANT_POSE" part avant
                    // la moindre ecriture : elle redit mot pour mot l'etape precedente.
                    // Mesure sur le run TEST2 : P2_PENDANT_POSE a t+13.9 s contre
                    // 2_APRES_CLEAR a t+13.2 s, zero colonne d'ecart sur 116 224 mesurees.
                    s.dernierePastePeriodiqueMs = System.currentTimeMillis();
                } else if (s.dernierPasteQ > 0 && q == 0) {
                    capturer(s, "3_APRES_PASTE",
                            "la file de pose s'est videe : la derniere tranche est posee");
                }
                s.dernierPasteQ = q;
            }

            // --- T477 : captures PENDANT la pose. L'etape finale 3_APRES_PASTE est
            //     condamnee a etre vide (la zone est relachee dans le tick meme ou la
            //     file se vide) : la donnee utile du terrain pose vient d'ici.
            if (q > 0 && s.etapes.size() < MAX_ETAPES - 2
                    && System.currentTimeMillis() - s.dernierePastePeriodiqueMs > CAPTURE_PASTE_MS) {
                s.dernierePastePeriodiqueMs = System.currentTimeMillis();
                capturer(s, "P" + s.etapes.size() + "_PENDANT_POSE",
                        "capture pendant la pose (file de pose = " + q + ")");
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

                // T491 -- SONDE DE PLACEMENT, PAS CHANTIER.
                // Une emprise epinglee puis relachee sans que la file de pose ait
                // jamais bouge n'a rien bati. Le 26/11, une telle sonde d'un seul
                // chunk s'est epinglee a 279 blocs du lac une seconde apres la
                // fermeture de la vraie session : le scan, re-arme, l'a prise pour un
                // nouveau chantier et a produit un second dossier d'apparence
                // legitime -- 946 Kio et 240 s de redaction en pleine partie -- ou
                // 1_DEBUT_CLEAR et 4_FIN etaient la meme photo d'un terrain intact.
                // Deux dossiers pour un seul essai, dont un entierement faux : c'est
                // pire que pas de mesure, parce que ca se lit comme une mesure.
                if (!s.chantierVu && s.elargissements == 0) {
                    log("[DLB-SCAN] session abandonnee sans ecriture : l'emprise x "
                            + s.ex0 + ".." + s.ex1 + "  z " + s.ez0 + ".." + s.ez1
                            + " a ete epinglee puis relachee sans qu'aucune construction "
                            + "n'y ait lieu (file de pose toujours vide, emprise jamais "
                            + "elargie). C'est une sonde de placement : la mesurer "
                            + "produirait deux photos identiques d'un terrain intact.");
                    couperBruit(s);                 // T493 : et on coupe son calcul
                    session = null;
                    phase = veilleAuto ? Phase.ARME : Phase.DESARME;
                    return;
                }

                // T491 -- DELAI DE GRACE.
                // Ne PAS fermer ici. Le lac relache son emprise dans le tick meme ou
                // sa file se vide, mais l'habillage, la replantation et la finition
                // viennent ensuite. Fermer maintenant revient a photographier un
                // chantier avant le coup de balai, puis a conclure sur la proprete.
                if (s.relacheeDepuisMs == 0L) {
                    s.relacheeDepuisMs = System.currentTimeMillis();
                    dire(level, "zone relachee : derniere mesure dans "
                            + (APRES_RELACHE_MS / 1000L) + " s, le temps que la finition "
                            + "(habillage, replantation) se termine.",
                            ChatFormatting.AQUA);
                    continue;
                }
                if (System.currentTimeMillis() - s.relacheeDepuisMs < APRES_RELACHE_MS) {
                    continue;
                }

                capturer(s, "4_FIN",
                        "zone relachee depuis " + (APRES_RELACHE_MS / 1000L)
                        + " s : etat FINAL, finition comprise (habillage, replantation)");
                ecrire(s, "fin du pipeline + delai de finition");
                session = null;
                phase = veilleAuto ? Phase.ARME : Phase.DESARME;
                return;
            }

            // T491 : la zone a ete re-epinglee pendant le delai de grace (un nouveau
            // chantier demarre). On rend la main immediatement plutot que de melanger
            // deux chantiers dans une seule session.
            if (zone != null && s.relacheeDepuisMs != 0L) {
                capturer(s, "4_FIN",
                        "zone re-epinglee avant la fin du delai : mesure finale anticipee");
                ecrire(s, "fin du pipeline (un nouveau chantier demarre)");
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
            s.ox0 = s.ex0; s.oz0 = s.ez0; s.ox1 = s.ex1; s.oz1 = s.ez1;

            poserGeometrie(s);

            s.yFloor = Math.max(level.getMinBuildHeight(), groundY - SCAN_BELOW);
            s.yTop = Math.min(level.getMaxBuildHeight() - 1, groundY + SCAN_ABOVE);
            s.derniereCaptureMs = System.currentTimeMillis();
            lancerBruit(s);
            dire(level, "session ouverte : zone " + s.w + "x" + s.d + " blocs ("
                    + s.cw + "x" + s.cd + " chunks), sol de reference Y=" + groundY,
                    ChatFormatting.AQUA);
            return s;
        } catch (Throwable t) {
            log("[DLB-SCAN] ouverture impossible : " + t);
            return null;
        }
    }

    /** Grille alignee chunk deduite de l'emprise courante, anneau compris. */
    private static void poserGeometrie(Session s) {
        int ring = RING_CHUNKS * 16;
        s.cx0 = (s.ex0 - ring) >> 4;
        s.cz0 = (s.ez0 - ring) >> 4;
        s.cw = ((s.ex1 + ring) >> 4) - s.cx0 + 1;
        s.cd = ((s.ez1 + ring) >> 4) - s.cz0 + 1;
        s.x0 = s.cx0 << 4;
        s.z0 = s.cz0 << 4;
        s.w = s.cw * 16;
        s.d = s.cd * 16;
    }

    /**
     * T477 : SUIVRE L'EMPRISE QUAND ELLE GRANDIT. C'est le correctif central.
     *
     * <p>Mesure en jeu (23/11) : le rapport annonce une emprise de 48 x 48 blocs et une
     * zone de 144 x 144 pour un lac de 246 x 284 qui a clere 304 chunks -- 30 fois trop
     * petit. Cause : {@code ouvrir()} lisait {@code ChunkKeeper.zoneBlockBounds()} UNE
     * seule fois, au premier tick ou une zone est epinglee, et la figeait. Or la
     * premiere zone epinglee n'est pas l'emprise de la structure : c'est la sonde de
     * placement du lac, {@code probeMin/probeMax = (px,pz) +/- 8} (ligne 795), que
     * {@code preloadBox} elargit de 8 blocs de plus et que {@code zoneBlockBounds}
     * arrondit au chunk -- soit exactement les 3 x 3 chunks x -208..-161 z 464..511
     * observes. Le vrai {@code track()} du lac arrive ensuite et ETEND la zone ; le
     * scan ne le voyait jamais.</p>
     *
     * <p>Desormais la zone epinglee est relue a CHAQUE tick et la session s'aligne
     * dessus : la grille est reconstruite et les etapes deja capturees sont reindexees
     * sur la nouvelle origine, sans perdre une seule mesure. Les colonnes qui n'ont
     * jamais ete lues restent ABSENT -- on n'invente rien pour faire joli.</p>
     *
     * @return vrai si la geometrie a change.
     */
    private static boolean elargir(Session s, int[] zone) {
        if (zone == null) return false;
        int zx0 = Math.min(zone[0], zone[2]), zx1 = Math.max(zone[0], zone[2]);
        int zz0 = Math.min(zone[1], zone[3]), zz1 = Math.max(zone[1], zone[3]);
        int nex0 = Math.min(s.ex0, zx0), nex1 = Math.max(s.ex1, zx1);
        int nez0 = Math.min(s.ez0, zz0), nez1 = Math.max(s.ez1, zz1);
        if (nex0 == s.ex0 && nex1 == s.ex1 && nez0 == s.ez0 && nez1 == s.ez1) return false;
        try {
            int ow = s.w, od = s.d, ox0 = s.x0, oz0 = s.z0;
            int ocw = s.cw, ocd = s.cd, ocx0 = s.cx0, ocz0 = s.cz0;
            s.ex0 = nex0; s.ex1 = nex1; s.ez0 = nez0; s.ez1 = nez1;
            poserGeometrie(s);
            int n = s.w * s.d, nc = s.cw * s.cd;
            for (Etape e : s.etapes) {
                int[] ySolid = new int[n], ySurface = new int[n], yWater = new int[n];
                char[] idSolid = new char[n], idSurface = new char[n];
                short[] airSous = new short[n], nSolides = new short[n];
                java.util.Arrays.fill(ySolid, ABSENT);
                java.util.Arrays.fill(ySurface, ABSENT);
                java.util.Arrays.fill(yWater, ABSENT);
                for (int z = 0; z < od; z++) {
                    int dz = (oz0 + z) - s.z0;
                    if (dz < 0 || dz >= s.d) continue;
                    for (int x = 0; x < ow; x++) {
                        int dx = (ox0 + x) - s.x0;
                        if (dx < 0 || dx >= s.w) continue;
                        int from = z * ow + x, to = dz * s.w + dx;
                        ySolid[to] = e.ySolid[from];
                        ySurface[to] = e.ySurface[from];
                        yWater[to] = e.yWater[from];
                        idSolid[to] = e.idSolid[from];
                        idSurface[to] = e.idSurface[from];
                        airSous[to] = e.airSous[from];
                        nSolides[to] = e.nSolides[from];
                    }
                }
                boolean[] cc = new boolean[nc];
                short[] cpc = new short[nc];
                for (int cz = 0; cz < ocd; cz++) {
                    int dcz = (ocz0 + cz) - s.cz0;
                    if (dcz < 0 || dcz >= s.cd) continue;
                    for (int cx = 0; cx < ocw; cx++) {
                        int dcx = (ocx0 + cx) - s.cx0;
                        if (dcx < 0 || dcx >= s.cw) continue;
                        cc[dcz * s.cw + dcx] = e.chunkCharge[cz * ocw + cx];
                        cpc[dcz * s.cw + dcx] = e.colonnesParChunk[cz * ocw + cx];
                    }
                }
                e.ySolid = ySolid; e.ySurface = ySurface; e.yWater = yWater;
                e.idSolid = idSolid; e.idSurface = idSurface;
                e.airSous = airSous; e.nSolides = nSolides;
                e.chunkCharge = cc; e.colonnesParChunk = cpc;
                e.absentes = n - e.lues;
                e.colonnesVisitees = n;
                e.chunksVisites = nc;
                e.remappee = true;
            }
            lancerBruit(s);      // la grille a change : le relief theorique est a refaire
            s.elargissements++;
            String m = "emprise elargie (" + s.elargissements + ") : x " + s.ex0 + ".." + s.ex1
                    + "  z " + s.ez0 + ".." + s.ez1 + "  ("
                    + (s.ex1 - s.ex0 + 1) + " x " + (s.ez1 - s.ez0 + 1) + " blocs), zone scannee "
                    + s.w + "x" + s.d + " (" + s.cw + "x" + s.cd + " chunks)";
            s.journal.add(m);
            dire(s.level, m, ChatFormatting.AQUA);
            return true;
        } catch (Throwable t) {
            log("[DLB-SCAN] elargissement impossible : " + t);
            return false;
        }
    }

    /**
     * Relief THEORIQUE de toute la zone, par bruit pur.
     *
     * <p>Ne genere rien et ne charge rien. Sert de reference absolue du terrain d'origine,
     * et surtout de point de comparaison pour les chunks jamais charges : sans lui, un
     * chunk absent ne dirait meme pas quelle forme il aurait du avoir.</p>
     */
    /**
     * T493 : coupe le relief theorique d'une session dont plus personne n'attend rien.
     *
     * <p>Appelee a CHAQUE ouverture de session -- il y a deux points d'ouverture
     * (automatique et {@code /dlbscan} manuel) et les deux peuvent se superposer a une
     * session encore vivante. Le 26/11, c'est la session fantome qui a publie SON
     * relief (12 544 colonnes) a la place de celui de la vraie session (230 144).</p>
     */
    private static void couperBruit(Session s) {
        if (s == null) return;
        s.bruitAnnule = true;
        Thread f = s.filBruit;
        if (f != null) f.interrupt();
    }

    /** T493 : le calcul est-il devenu inutile (grille changee, ou session abandonnee) ? */
    private static boolean bruitPerime(Session s, int gGen) {
        return s.bruitGen != gGen || s.bruitAnnule;
    }

    private static void calculerBruit(Session s, int gw, int gd, int gx0, int gz0, int gGen) {
        long t0 = System.currentTimeMillis();
        int[] tmp = new int[gw * gd];
        java.util.Arrays.fill(tmp, ABSENT);
        try {
            var gen = s.level.getChunkSource().getGenerator();
            var rs = s.level.getChunkSource().randomState();
            var type = net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG;

            // ---- 1) CALIBRAGE : combien coute REELLEMENT une colonne, ici, maintenant ?
            // On ne suppose pas, on mesure. Le cout depend de la machine, du generateur
            // et de la charge du serveur ; une constante codee en dur serait fausse
            // partout sauf sur la machine ou elle a ete ecrite.
            long tCal = System.nanoTime();
            int sondes = 0;
            for (int i = 0; i < BRUIT_SONDES_CALIBRAGE; i++) {
                if (bruitPerime(s, gGen)) return;
                int px = (int) (((long) i * 7919L) % gw);
                int pz = (int) (((long) i * 6271L) % gd);
                gen.getBaseHeight(gx0 + px, gz0 + pz, type, s.level, rs);
                sondes++;
            }
            double nsCol = sondes > 0 ? (double) (System.nanoTime() - tCal) / sondes : 1e9;

            // ---- 2) PAS ADAPTATIF : le bruit de generation est LISSE. L'echantillonner
            // tous les PAS blocs et interpoler donne la meme carte a un bloc pres, pour
            // PAS^2 fois moins de calcul. On choisit le plus petit pas qui tient dans le
            // budget.
            int pas = 1;
            long ech = (long) gw * gd;
            while (pas < BRUIT_PAS_MAX
                    && ech * nsCol > BRUIT_BUDGET_MS * 1_000_000.0) {
                pas *= 2;
                ech = (long) ((gw + pas - 1) / pas + 1) * ((gd + pas - 1) / pas + 1);
            }
            double prevuMs = ech * nsCol / 1e6;

            // ---- 3) S'IL N'ENTRE PAS, ON NE COMMENCE PAS.
            // C'est le coeur du correctif : le 26/11, 230 144 colonnes a 35,3 ms ont ete
            // lancees sans que personne n'ait calcule que cela faisait 2 h 17.
            if (prevuMs > BRUIT_BUDGET_MS) {
                log("[DLB-SCAN] relief theorique NON LANCE : " + gw + "x" + gd + " = "
                        + ((long) gw * gd) + " colonnes a " + String.format("%.1f", nsCol / 1e6)
                        + " ms/colonne, soit " + Math.round(prevuMs / 1000.0) + " s meme au pas "
                        + pas + " -- au-dessus du budget de " + (BRUIT_BUDGET_MS / 1000)
                        + " s. Les sections theoriques seront vides, mais le serveur reste "
                        + "jouable : un diagnostic ne doit jamais couter plus cher que le "
                        + "defaut qu'il diagnostique.");
                if (!bruitPerime(s, gGen)) s.bruit = tmp;
                return;
            }

            // ---- 4) ECHANTILLONNAGE, avec respiration et date butoir.
            int nx = (gw + pas - 1) / pas + 1;
            int nz = (gd + pas - 1) / pas + 1;
            int[] grille = new int[nx * nz];
            java.util.Arrays.fill(grille, ABSENT);
            int faits = 0;
            long tLot = System.nanoTime();
            boolean coupe = false;
            for (int j = 0; j < nz && !coupe; j++) {
                int wz = gz0 + Math.min(gd - 1, j * pas);
                for (int i = 0; i < nx; i++) {
                    if (bruitPerime(s, gGen)) return;
                    if (System.currentTimeMillis() - t0 > BRUIT_BUDGET_MS) { coupe = true; break; }
                    int wx = gx0 + Math.min(gw - 1, i * pas);
                    grille[j * nx + i] = gen.getBaseHeight(wx, wz, type, s.level, rs);
                    if (++faits % BRUIT_LOT == 0) {
                        // Respiration : on rend la main. Le fil tourne en MIN_PRIORITY,
                        // mais sur une machine a 4 coeurs la priorite seule ne suffit
                        // pas -- c'est ce qui a vole 10 a 15 s par tick au serveur.
                        long dt = System.nanoTime() - tLot;
                        try {
                            Thread.sleep(Math.max(1L, Math.min(25L, dt / 1_000_000L * 2L)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        tLot = System.nanoTime();
                    }
                }
            }
            if (bruitPerime(s, gGen)) return;

            // ---- 5) INTERPOLATION bilineaire du reseau vers chaque colonne.
            for (int z = 0; z < gd; z++) {
                double fz = (double) z / pas;
                int j0 = (int) fz; if (j0 > nz - 2) j0 = Math.max(0, nz - 2);
                double tz = fz - j0;
                for (int x = 0; x < gw; x++) {
                    double fx = (double) x / pas;
                    int i0 = (int) fx; if (i0 > nx - 2) i0 = Math.max(0, nx - 2);
                    double tx = fx - i0;
                    int v00 = grille[j0 * nx + i0];
                    int v10 = grille[j0 * nx + Math.min(nx - 1, i0 + 1)];
                    int v01 = grille[Math.min(nz - 1, j0 + 1) * nx + i0];
                    int v11 = grille[Math.min(nz - 1, j0 + 1) * nx + Math.min(nx - 1, i0 + 1)];
                    // Une valeur manquante ne s'invente pas : la colonne reste ABSENT.
                    if (v00 == ABSENT || v10 == ABSENT || v01 == ABSENT || v11 == ABSENT) continue;
                    double lo = v00 + (v10 - v00) * tx;
                    double hi = v01 + (v11 - v01) * tx;
                    tmp[z * gw + x] = (int) Math.round(lo + (hi - lo) * tz);
                }
            }
            if (bruitPerime(s, gGen)) return;
            s.bruit = tmp;      // publication en un seul coup
            log("[DLB-SCAN] relief theorique pret : " + ((long) gw * gd) + " colonnes en "
                    + (System.currentTimeMillis() - t0) + " ms (pas " + pas + ", "
                    + faits + " sondes" + (coupe ? ", INTERROMPU par la date butoir" : "")
                    + ", " + String.format("%.2f", nsCol / 1e6) + " ms/colonne mesures)");
        } catch (Throwable t) {
            java.util.Arrays.fill(tmp, ABSENT);
            if (!bruitPerime(s, gGen)) s.bruit = tmp;
            log("[DLB-SCAN] relief theorique indisponible : " + t);
        }
    }

    /**
     * Lance le relief theorique DES L'OUVERTURE, sur un fil de fond.
     *
     * <p>Pourquoi : {@code getBaseHeight} echantillonne une colonne de bruit complete a
     * chaque appel, et la zone en compte ~140 000. Mesure en jeu : appele sur le fil
     * serveur au moment d'ecrire, il a GELE la partie entierement -- plus de chat, plus de
     * tick, le joueur a du tuer le jeu, et aucun fichier n'est sorti. Lance ici, il tourne
     * pendant les trois minutes de construction et il est pret quand on redige.</p>
     */
    private static void lancerBruit(Session s) {
        try {
            // T477 : la geometrie est FIGEE a l'instant du lancement. Le fil ne relit
            // jamais s.w/s.d/s.x0/s.z0 : un elargissement les change sous ses pieds.
            final int gw = s.w, gd = s.d, gx0 = s.x0, gz0 = s.z0;
            final int gGen = ++s.bruitGen;
            s.bruit = null;
            Thread t = new Thread(() -> calculerBruit(s, gw, gd, gx0, gz0, gGen), "DLB-SCAN-relief");
            t.setDaemon(true);
            // T477 : NORM-1 et non MIN. En MIN_PRIORITY le fil n'a recu aucun CPU
            // pendant que le serveur rattrapait 588 ticks de retard, et le relief
            // theorique n'etait pas pret au bout de 120 s. Un cran sous la normale
            // suffit a ne pas concurrencer le fil serveur tout en avancant.
            // T493 : RETOUR EN MIN_PRIORITY. T477 l'avait releve a NORM-1 pour que le
            // calcul aboutisse ; il a abouti, en volant 10 a 15 s par tick au serveur
            // pendant 7 min 22. Ce n'etait pas le bon arbitrage : le jeu passe avant le
            // rapport. La respiration par lots (BRUIT_LOT) remplace la priorite elevee.
            t.setPriority(Thread.MIN_PRIORITY);
            s.filBruit = t;
            t.start();
        } catch (Throwable t) {
            log("[DLB-SCAN] fil du relief theorique non lance : " + t);
        }
    }

    /** Attend le relief theorique sans bloquer indefiniment ; a defaut, sections vides. */
    private static void attendreBruit(Session s) {
        Thread t = s.filBruit;
        if (t != null && t.isAlive()) {
            log("[DLB-SCAN] attente du relief theorique...");
            try {
                t.join(BRUIT_ATTENTE_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        int[] b = s.bruit;
        if (b == null || b.length != s.w * s.d) {
            // T493 : ON ANNULE. Le 26/11, le redacteur a renonce a 06:15:07 et le fil a
            // continue jusqu'a 06:18:32 -- 3 min 25 de CPU vole au serveur pour publier
            // un resultat que plus aucun fichier n'allait lire.
            couperBruit(s);
            int[] vide = new int[s.w * s.d];
            java.util.Arrays.fill(vide, ABSENT);
            s.bruit = vide;
            log("[DLB-SCAN] relief theorique non termine a temps : sections theoriques "
                    + "vides, et le calcul est ARRETE (il ne servirait plus a rien).");
        } else {
            s.bruitAnnule = true;   // resultat obtenu : plus rien a calculer
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
        deepluckyblock.util.TerrainMother.SnowSpreadSnapshot snow =
                deepluckyblock.util.TerrainMother.latestSnowSpreadSnapshot();
        if (snow != null) s.snowSpreadSnapshot = snow;
        e.snowSpread = s.snowSpreadSnapshot;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        for (int cz = 0; cz < s.cd; cz++) {
            for (int cx = 0; cx < s.cw; cx++) {
                ChunkAccess chunk = chunkPassif(s.level, s.cx0 + cx, s.cz0 + cz);
                boolean charge = chunk != null;
                e.chunkCharge[cz * s.cw + cx] = charge;
                e.chunksVisites++;
                int bx0 = (s.cx0 + cx) << 4, bz0 = (s.cz0 + cz) << 4;
                // Depart de scan calcule UNE FOIS PAR CHUNK : le ciel represente
                // l'essentiel de la tranche et se saute en O(1) par section vide.
                int yDepart = charge ? departScan(chunk, s.yTop, s.yFloor) : s.yFloor;
                for (int oz = 0; oz < 16; oz++) {
                    for (int ox = 0; ox < 16; ox++) {
                        int i = (bz0 + oz - s.z0) * s.w + (bx0 + ox - s.x0);
                        // T477 : une colonne hors grille est COMPTEE, jamais ignoree en
                        // silence. C'est exactement ce genre de « continue » muet qui
                        // laisse croire qu'une grille est complete alors qu'elle saute
                        // des chunks entiers.
                        if (i < 0 || i >= e.ySolid.length) { e.horsGrille++; continue; }
                        e.colonnesVisitees++;
                        e.colonnesParChunk[cz * s.cw + cx]++;
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
        // T477 : la capture se controle elle-meme. Si la somme des colonnes ne retombe
        // pas sur la taille de la grille, la grille est fausse et il faut le lire dans
        // le journal, pas le deduire d'un dessin.
        boolean exact = e.colonnesVisitees == s.w * s.d
                && e.lues + e.absentes == s.w * s.d
                && e.chunksVisites == s.cw * s.cd
                && e.horsGrille == 0;
        if (!exact) {
            s.couvertureRompue = true;
            log("[DLB-SCAN] COUVERTURE ROMPUE a " + nom + " : " + e.colonnesVisitees
                    + " colonnes visitees pour " + (s.w * s.d) + " attendues, "
                    + e.chunksVisites + " chunks pour " + (s.cw * s.cd) + ", "
                    + e.horsGrille + " hors grille");
        }
        if (nCharges == 0 && !s.etapes.isEmpty()) {
            Etape av = s.etapes.get(s.etapes.size() - 1);
            int avCharges = 0;
            for (boolean b : av.chunkCharge) if (b) avCharges++;
            if (avCharges > 0) e.zoneRelachee = true;
        }
        String ligne = nom + " : " + e.lues + " colonnes lues, " + e.absentes + " absentes, "
                + nCharges + "/" + e.chunkCharge.length + " chunks charges, "
                + ((System.nanoTime() - t0) / 1_000_000L) + " ms"
                + (exact ? "" : "  [COUVERTURE ROMPUE]")
                + (e.zoneRelachee ? "  [ZONE DEJA RELACHEE - capture vide]" : "");
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
        boolean coucheNeige = false;
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
            // Observe la vraie couche SNOW (comme celle posee par la finition),
            // y compris sous la canopee. Fenetre bornee a 48 blocs autour du premier
            // bloc de surface pour ne pas confondre une grotte profonde et le manteau.
            if (st.is(Blocks.SNOW) && ySurface != ABSENT && y >= ySurface - AIR_PROBE_DEPTH) {
                coucheNeige = true;
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
        e.coucheNeige[i] = coucheNeige;
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

    /**
     * Prepare le dossier sur le fil serveur, puis REDIGE AILLEURS.
     *
     * <p>La redaction produit une dizaine de Mio de texte et lit ~140 000 colonnes par
     * etape. Mesure en jeu : faite sur le fil serveur, elle a fige la partie -- chat
     * compris -- et le joueur a du tuer le jeu avant qu'un seul fichier ne soit ecrit. Or
     * tout ce qu'elle lit est deja capture en memoire (tableaux d'entiers et palette) :
     * rien n'oblige a tenir le fil serveur pendant ce temps. Le dossier, lui, est cree
     * tout de suite et son chemin annonce : meme si la suite echoue, le joueur sait ou
     * regarder.</p>
     */
    private static void ecrire(Session s, String cause) {
        if (s.etapes.isEmpty()) {
            log("[DLB-SCAN] rien a ecrire : aucune etape capturee");
            return;
        }
        // Affectation en deux temps puis copie dans des finales : une finale vierge
        // affectee dans un try et capturee ensuite par une lambda est legale, mais sans
        // JDK sous la main je ne prends aucun risque de compilation sur un detail pareil.
        java.nio.file.Path dirTmp = null;
        net.minecraft.server.MinecraftServer serveurTmp = null;
        try {
            serveurTmp = s.level.getServer();
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                    .format(new java.util.Date());
            dirTmp = serveurTmp
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("dlb_scan").resolve("scan-" + stamp);
            java.nio.file.Files.createDirectories(dirTmp);
        } catch (Throwable t) {
            log("[DLB-SCAN] dossier de sortie impossible : " + t);
            dire(s.level, "ECHEC : dossier de sortie impossible : " + t, ChatFormatting.RED);
            return;
        }
        final java.nio.file.Path dir = dirTmp;
        final net.minecraft.server.MinecraftServer serveur = serveurTmp;
        log("[DLB-SCAN] dossier cree : " + dir);
        dire(s.level, "redaction en cours en tache de fond (" + s.etapes.size()
                + " etapes) -- le jeu reste jouable. Dossier : " + dir, ChatFormatting.AQUA);
        try {
            Thread w = new Thread(() -> rediger(s, cause, dir, serveur), "DLB-SCAN-redaction");
            w.setDaemon(true);
            w.start();
        } catch (Throwable t) {
            log("[DLB-SCAN] fil de redaction non lance, repli sur le fil serveur : " + t);
            rediger(s, cause, dir, serveur);
        }
    }

    /** Redaction integrale : hors du fil serveur. Ne touche qu'a des donnees capturees. */
    private static void rediger(Session s, String cause, java.nio.file.Path dir,
                                net.minecraft.server.MinecraftServer serveur) {
        long tDebut = System.currentTimeMillis();
        attendreBruit(s);
        try {
            long total = 0;
            List<String> ecrits = new ArrayList<>();

            // UN FICHIER PAR ETAPE : rapport, cartes et grilles sont reunis sous des
            // en-tetes explicites. Toutes les mesures restent presentes et lisibles ;
            // seule la separation en trois pieces disparait.
            for (int n = 0; n < s.etapes.size(); n++) {
                Etape e = s.etapes.get(n);
                Etape prec = n > 0 ? s.etapes.get(n - 1) : null;
                // Un seul fichier par etape, mais trois blocs faciles a reperer et a
                // lire seuls. Aucun contenu n'est supprime ; les cartes et grilles sont
                // simplement concatenees apres leurs en-tetes de section.
                String base = String.format("%02d_%s", n + 1, nomFichier(e.nom));
                StringBuilder r = new StringBuilder(8 << 20);
                enteteEtape(r, s, e, prec, n, cause);
                titre(r, "RAPPORT");
                section0Couverture(r, s, e);
                section2ChunksAbsents(r, s, e);
                tableauParChunk(r, s, e);
                section4bComposition(r, s, e);
                section5InterChunks(r, s, e);
                section6Marches(r, s, e);
                section10Palette(r, s);

                titre(r, "CARTES");
                section8Vues(r, s, e, prec);
                coupesVerticales(r, s, e);

                titre(r, "GRILLES");
                section9Grilles(r, s, e, prec);
                total += ecrireFichier(s, dir.resolve(base + ".txt"), r, ecrits);
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

            final long ko = total / 1024L;
            final long secondes = (System.currentTimeMillis() - tDebut) / 1000L;
            log("[DLB-SCAN] termine : " + ecrits.size() + " fichiers, " + ko
                    + " Kio, en " + secondes + " s, dans " + dir);
            direDepuisFond(serveur, s, ecrits.size() + " fichiers ecrits, " + ko
                    + " Kio au total en " + secondes + " s, dans : " + dir, ChatFormatting.AQUA);
            direDepuisFond(serveur, s, "ouvre 00_SOMMAIRE.txt en premier ; chaque fichier "
                    + "d'etape contient RAPPORT, CARTES et GRILLES.", ChatFormatting.AQUA);
        } catch (Throwable t) {
            log("[DLB-SCAN] ecriture impossible : " + t);
            direDepuisFond(serveur, s, "ECHEC d'ecriture : " + t, ChatFormatting.RED);
        }
    }

    /**
     * Parler au joueur depuis le fil de redaction.
     *
     * <p>Toucher la liste des joueurs hors du fil serveur est une faute : on repasse par
     * {@code serveur.execute}. Si le serveur s'arrete entretemps, le message est perdu
     * mais les fichiers, eux, sont deja sur le disque -- et le log les nomme.</p>
     */
    private static void direDepuisFond(net.minecraft.server.MinecraftServer serveur,
                                       Session s, String texte, ChatFormatting couleur) {
        try {
            serveur.execute(() -> dire(s.level, texte, couleur));
        } catch (Throwable ignored) {
            // serveur deja arrete : le log a deja tout dit
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
        r.append("instant. RAPPORT, CARTES et GRILLES sont reunis sous des en-tetes distincts.\n");
        r.append("Le present fichier porte les COMPARAISONS entre etapes.\n\n");
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
        // T477 : la premiere zone epinglee n'est PAS l'emprise de la structure (c'est la
        // sonde de placement). Tracer l'ecart entre ce qu'on a vu en premier et ce qu'on
        // a fini par scanner est le seul moyen de savoir si la mesure a vise juste.
        r.append("emprise initiale: x ").append(s.ox0).append("..").append(s.ox1)
         .append("   z ").append(s.oz0).append("..").append(s.oz1)
         .append("   (").append(s.ox1 - s.ox0 + 1).append(" x ").append(s.oz1 - s.oz0 + 1)
         .append(" blocs)\n");
        r.append("elargissements  : ").append(s.elargissements);
        if (s.elargissements == 0) {
            r.append("   (l'emprise n'a jamais grandi apres l'ouverture)\n");
        } else {
            long av = (long) (s.ox1 - s.ox0 + 1) * (s.oz1 - s.oz0 + 1);
            long ap = (long) (s.ex1 - s.ex0 + 1) * (s.ez1 - s.ez0 + 1);
            r.append("   -- la zone epinglee a grandi apres l'ouverture ; la\n");
            r.append("                  session l'a suivie. Surface finale / initiale = ")
             .append(av > 0 ? (ap * 10 / av) / 10.0 : 0.0).append(" x\n");
        }
        // T482 : ne plus annoncer « couverture complete » quand des dizaines de
        // milliers de colonnes n'ont pas pu etre lues. Le drapeau couvertureRompue
        // ne verifie QUE l'integrite du balayage (chaque case a bien ete visitee) ;
        // il ne dit rien de ce qui a ete MESURE. Au run du 24/11 il annoncait
        // « complete » alors que 48 128 colonnes sur 222 208 (21,7 %) etaient
        // illisibles faute de chunk charge : le rapport se contredisait tout seul.
        // Les deux faits sont desormais distincts et affiches cote a cote.
        int pireAbsentes = 0;
        String pireEtape = "";
        for (Etape e2 : s.etapes) {
            if (e2.absentes > pireAbsentes) { pireAbsentes = e2.absentes; pireEtape = e2.nom; }
        }
        int ncolTot = s.w * s.d;
        r.append("balayage        : ").append(s.couvertureRompue
                ? "AU MOINS UNE CAPTURE INCOMPLETE -- voir section 0 de chaque etape"
                : "complet sur toutes les captures (chaque case a ete visitee)").append('\n');
        r.append("mesure          : ");
        if (pireAbsentes == 0) {
            r.append("toutes les colonnes ont pu etre lues");
        } else {
            r.append("INCOMPLETE -- jusqu'a ").append(pireAbsentes).append(" colonnes sur ")
             .append(ncolTot).append(" (")
             .append(ncolTot > 0 ? (pireAbsentes * 1000L / ncolTot) / 10.0 : 0.0)
             .append(" %) non mesurables faute de chunk charge, au pire a l'etape ")
             .append(pireEtape)
             .append("\n                  une colonne non mesuree n'est PAS un trou du terrain :")
             .append(" c'est un angle mort de l'outil");
        }
        r.append('\n');
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
        r.append("FICHIERS PRODUITS PAR ETAPE\n");
        r.append("  NN_<etape>.txt : un fichier unique avec trois sections lisibles seules :\n");
        r.append("    RAPPORT : couverture, chunks absents, composition, raccords, marches\n");
        r.append("    CARTES  : vues du dessus, relief, profondeurs, anomalies, 3D et coupes\n");
        r.append("    GRILLES : valeur de CHAQUE colonne, au bloc pres\n");
        r.append("Rien n'est omis ; seules les trois pieces sont reunies.\n\n");
        r.append("SOMMAIRE DE CE FICHIER\n");
        r.append("  1  etapes capturees\n");
        r.append("  2  CHUNKS NON CHARGES par etape (les « chunks qui manquent »)\n");
        r.append("  3  chunks NON TOUCHES d'une etape a l'autre (sautes par le clear / la pose)\n");
        r.append("  4  ancien terrain reste dans l'emprise\n");
        r.append("  7  profil du raccord, du bord vers l'exterieur\n");
        r.append(" 9z  relief theorique de generation (reference commune)\n");
        r.append(" 10  palette des blocs\n");
        r.append("\nLes rapports, cartes, coupes et grilles au bloc pres sont dans le fichier\n");
        r.append("de chaque etape ; le present sommaire garde les comparaisons de la serie.\n");
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
    /**
     * Section 3 -- CE QUE L'OPERATION A VRAIMENT SAUTE.
     *
     * <p>T478. Cette section a menti au dev. Elle annoncait « chunks d'emprise SAUTES :
     * 153/304 » sur le run du 23/11, ce qui donnait l'impression que le clear ne coupait
     * que la moitie de ce qu'il devait. Verification faite sur les grilles du meme run :
     * la boite reelle de l'ouvrage (x 12..254, z 181..463) etait rasee a
     * <b>68769 colonnes sur 68769, soit 100,000 %</b>, zero colonne au-dessus du plancher.
     * Le clear n'avait rien saute du tout.</p>
     *
     * <p>Le defaut etait ici : {@code if (colonnesChangees == 0) sautes++}. Un compteur
     * qui appelle « saut » toute absence de changement confond trois situations qui
     * n'ont rien a voir :</p>
     * <ol>
     *   <li><b>non comparable</b> -- le chunk n'etait pas charge a l'etape precedente,
     *       donc aucune colonne ne peut etre comparee. On ne sait pas. (83 chunks.)</li>
     *   <li><b>rien a couper</b> -- le sol etait deja sous le plancher de l'operation.
     *       Sur un ocean gele, la moitie de l'emprise est deja au niveau de la mer :
     *       l'operation n'a rien a y faire, et ne rien faire est le bon comportement.
     *       (65 chunks.)</li>
     *   <li><b>vrai saut</b> -- il restait de la matiere au-dessus du plancher et elle
     *       est toujours la. C'est le seul cas qui soit un defaut. (0 chunk.)</li>
     * </ol>
     *
     * <p>Seconde cause de confusion, corrigee elle aussi : l'emprise de ce rapport est
     * ARRONDIE AU CHUNK, donc plus large que la boite de l'ouvrage (jusqu'a 15 blocs par
     * bord). Les 4925 colonnes restees hautes du run etaient, toutes, hors de la boite --
     * du terrain que le clear n'avait aucune raison de toucher. La boite reellement
     * modifiee est desormais mesuree et imprimee a cote de l'emprise, pour que les deux
     * ne soient plus confondues.</p>
     */
    private static void section3ChunksNonTouches(StringBuilder r, Session s) {
        titre(r, "3. CE QUE L'OPERATION A VRAIMENT SAUTE  (clear / pose)");
        if (s.etapes.size() < 2) {
            r.append("  (une seule etape capturee : rien a comparer)\n");
            return;
        }
        r.append("  COMMENT LIRE. L'emprise de ce rapport est arrondie au chunk : elle deborde la\n")
         .append("  boite reelle de l'ouvrage de 15 blocs au plus par bord. Et une operation qui\n")
         .append("  rase a un plancher ne change rien la ou le sol etait deja dessous. Ces deux\n")
         .append("  cas ne sont PAS des sauts. Seul  VRAI SAUT  signale un defaut.\n\n");
        for (int n = 1; n < s.etapes.size(); n++) {
            Etape a = s.etapes.get(n - 1), b = s.etapes.get(n);

            // --- passe 1 : plancher effectif de l'operation + boite reellement modifiee.
            // Le plancher est le 95e centile du sol APRES, mesure sur les seules colonnes
            // qui ont bouge : c'est le niveau auquel l'operation a arase. Le centile (et
            // non le maximum) absorbe les quelques colonnes aberrantes de bordure.
            final int DEC = 64, NB = 384;          // y de -64 a 319, negatifs compris
            int[] hist = new int[NB];
            int nch = 0;
            int mx0 = Integer.MAX_VALUE, mx1 = Integer.MIN_VALUE;
            int mz0 = Integer.MAX_VALUE, mz1 = Integer.MIN_VALUE;
            for (int z = s.ez0; z <= s.ez1; z++) {
                for (int x = s.ex0; x <= s.ex1; x++) {
                    int i = idx(s, x, z);
                    if (i < 0) continue;
                    if (a.ySolid[i] == ABSENT || b.ySolid[i] == ABSENT) continue;
                    if (a.ySolid[i] == b.ySolid[i] && a.ySurface[i] == b.ySurface[i]
                            && a.nSolides[i] == b.nSolides[i]) continue;
                    nch++;
                    if (x < mx0) mx0 = x;
                    if (x > mx1) mx1 = x;
                    if (z < mz0) mz0 = z;
                    if (z > mz1) mz1 = z;
                    int v = b.ySolid[i] + DEC;
                    if (v < 0) v = 0;
                    if (v >= NB) v = NB - 1;
                    hist[v]++;
                }
            }
            boolean planConnu = nch > 0;
            int plancher = 0;
            if (planConnu) {
                int seuil = (int) (nch * 0.95D), cum = 0;
                for (int v = 0; v < NB; v++) {
                    cum += hist[v];
                    if (cum >= seuil) { plancher = v - DEC; break; }
                }
            }

            // --- passe 2 : classement de chaque chunk d'emprise.
            int empriseTot = 0, touches = 0, rienACouper = 0, nonComparable = 0, vraiSaut = 0;
            StringBuilder liste = new StringBuilder();
            for (int cz = 0; cz < s.cd; cz++) {
                for (int cx = 0; cx < s.cw; cx++) {
                    if (!chunkDansEmprise(s, cx, cz)) continue;
                    empriseTot++;
                    int ch = colonnesChangees(s, a, b, cx, cz);
                    if (ch > 0) { touches++; continue; }
                    // aucun changement : lequel des trois cas ?
                    int bx0 = (s.cx0 + cx) << 4, bz0 = (s.cz0 + cz) << 4;
                    int cmp = 0, hautMax = Integer.MIN_VALUE;
                    for (int oz = 0; oz < 16; oz++) {
                        for (int ox = 0; ox < 16; ox++) {
                            int i = idx(s, bx0 + ox, bz0 + oz);
                            if (i < 0) continue;
                            if (a.ySolid[i] == ABSENT || b.ySolid[i] == ABSENT) continue;
                            cmp++;
                            if (b.ySolid[i] > hautMax) hautMax = b.ySolid[i];
                        }
                    }
                    if (cmp == 0) { nonComparable++; continue; }
                    if (!planConnu || hautMax <= plancher) { rienACouper++; continue; }
                    vraiSaut++;
                    if (vraiSaut <= 200) {
                        liste.append("      VRAI SAUT  chunk ").append(s.cx0 + cx).append(',')
                             .append(s.cz0 + cz).append("   blocs x ").append(bx0).append("..")
                             .append(bx0 + 15).append("  z ").append(bz0).append("..")
                             .append(bz0 + 15)
                             .append("   sol le plus haut apres = ").append(hautMax)
                             .append("  (plancher ").append(plancher).append(")\n");
                    }
                }
            }

            r.append("  ").append(a.nom).append("  ->  ").append(b.nom).append('\n');
            r.append("      plancher effectif de l'operation : ");
            if (planConnu) r.append("y=").append(plancher)
                            .append("   (95e centile du sol apres, sur ").append(nch)
                            .append(" colonnes modifiees)\n");
            else r.append("inconnu -- aucune colonne modifiee\n");
            r.append("      boite REELLEMENT modifiee        : ");
            if (nch > 0) r.append("x ").append(mx0).append("..").append(mx1)
                          .append("   z ").append(mz0).append("..").append(mz1)
                          .append("   (").append(mx1 - mx0 + 1).append(" x ")
                          .append(mz1 - mz0 + 1).append(" blocs)\n");
            else r.append("aucune\n");
            r.append("      emprise du rapport (chunk ronde) : x ").append(s.ex0).append("..")
             .append(s.ex1).append("   z ").append(s.ez0).append("..").append(s.ez1)
             .append("   (").append(empriseTot).append(" chunks)\n");
            r.append("      chunks modifies (toute la zone)  : ").append(touches).append('\n');
            r.append("      --- classement des ").append(empriseTot).append(" chunks d'emprise ---\n");
            r.append("      touches par l'operation          : ").append(touches).append('\n');
            r.append("      rien a couper (deja sous y=")
             .append(planConnu ? String.valueOf(plancher) : "?").append(")   : ").append(rienACouper).append('\n');
            r.append("      non comparable (absent avant)    : ").append(nonComparable).append('\n');
            r.append("      VRAI SAUT (defaut)               : ").append(vraiSaut).append('\n');
            int juges = touches + rienACouper + vraiSaut;
            r.append("      VERDICT : ");
            if (vraiSaut == 0 && juges > 0)
                r.append("AUCUN SAUT. ").append(juges).append(" chunks jugeables sur ")
                 .append(empriseTot).append(", tous conformes.\n");
            else if (vraiSaut > 0)
                r.append(vraiSaut).append(" CHUNKS REELLEMENT SAUTES sur ").append(juges)
                 .append(" jugeables -- voir la liste.\n");
            else
                r.append("INDECIDABLE : aucun chunk comparable.\n");
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

    // ---------------------------------------------------------------- 0
    /**
     * T477 : PREUVE DE COUVERTURE. Aucune carte ne vaut si la grille qui la porte saute
     * des chunks. Cette section compte ce qui a ete reellement parcouru et le compare a
     * ce qui devait l'etre ; un ecart est annonce en toutes lettres plutot que laisse a
     * deviner dans un dessin.
     */
    private static void section0Couverture(StringBuilder r, Session s, Etape e) {
        titre(r, "0. COUVERTURE DE LA GRILLE  (controle de non-omission)");
        int nc = s.cw * s.cd, ncol = s.w * s.d;
        int charges = 0, empriseTotal = 0, empriseCharges = 0;
        for (int cz = 0; cz < s.cd; cz++) {
            for (int cx = 0; cx < s.cw; cx++) {
                boolean ch = e.chunkCharge[cz * s.cw + cx];
                if (ch) charges++;
                if (chunkDansEmprise(s, cx, cz)) {
                    empriseTotal++;
                    if (ch) empriseCharges++;
                }
            }
        }
        r.append("  grille           : ").append(s.cw).append(" x ").append(s.cd)
         .append(" chunks  =  ").append(nc).append(" chunks,  ")
         .append(s.w).append(" x ").append(s.d).append(" blocs  =  ").append(ncol).append(" colonnes\n");
        r.append("  origine grille   : x ").append(s.x0).append("  z ").append(s.z0)
         .append("   (chunk ").append(s.cx0).append(",").append(s.cz0).append(")\n");
        r.append("  emprise          : x ").append(s.ex0).append("..").append(s.ex1)
         .append("   z ").append(s.ez0).append("..").append(s.ez1)
         .append("   (").append(s.ex1 - s.ex0 + 1).append(" x ").append(s.ez1 - s.ez0 + 1)
         .append(" blocs, ").append(empriseTotal).append(" chunks)\n\n");

        r.append("  COMPTEURS DE PARCOURS (mesures pendant la capture, pas recalcules) :\n");
        ligneCompte(r, "chunks visites par la boucle", e.chunksVisites, nc);
        ligneCompte(r, "colonnes visitees", e.colonnesVisitees, ncol);
        ligneCompte(r, "colonnes lues + absentes", e.lues + e.absentes, ncol);
        ligneCompte(r, "colonnes tombees hors grille", e.horsGrille, 0);
        r.append("      colonnes lues (chunk charge)   : ").append(e.lues).append('\n');
        r.append("      colonnes absentes (chunk hors memoire) : ").append(e.absentes).append('\n');
        r.append("      chunks charges                 : ").append(charges).append(" / ").append(nc)
         .append("   dont dans l'emprise ").append(empriseCharges).append(" / ").append(empriseTotal).append('\n');
        if (e.remappee) {
            r.append("      NOTE : etape capturee avant un elargissement d'emprise, puis\n");
            r.append("             reindexee sur la grille actuelle. Les colonnes ajoutees par\n");
            r.append("             l'elargissement n'ont jamais ete lues et comptent comme absentes.\n");
        }
        if (e.zoneRelachee) {
            r.append("      ALERTE : la zone etait DEJA RELACHEE au moment de cette capture.\n");
            r.append("             Les chunks n'etaient plus tenus en memoire : cette etape ne\n");
            r.append("             mesure rien. Se reporter aux captures PENDANT la pose.\n");
        }
        r.append('\n');

        r.append("  CHUNKS N'AYANT PAS RENDU LEURS 256 COLONNES (doit etre vide) :\n");
        int fautifs = 0;
        for (int cz = 0; cz < s.cd; cz++) {
            for (int cx = 0; cx < s.cw; cx++) {
                int v = e.colonnesParChunk[cz * s.cw + cx];
                if (v == 256) continue;
                fautifs++;
                if (fautifs <= 40) {
                    r.append("      chunk ").append(s.cx0 + cx).append(",").append(s.cz0 + cz)
                     .append("   blocs x ").append((s.cx0 + cx) << 4).append("..").append(((s.cx0 + cx) << 4) + 15)
                     .append("  z ").append((s.cz0 + cz) << 4).append("..").append(((s.cz0 + cz) << 4) + 15)
                     .append("   -> ").append(v).append(" colonne(s)\n");
                }
            }
        }
        if (fautifs == 0) r.append("      (aucun : les ").append(nc)
                .append(" chunks ont tous rendu exactement 256 colonnes)\n");
        else if (fautifs > 40) r.append("      ... et ").append(fautifs - 40).append(" autre(s)\n");
        r.append('\n');

        boolean ok = e.chunksVisites == nc && e.colonnesVisitees == ncol
                && e.lues + e.absentes == ncol && e.horsGrille == 0 && fautifs == 0;
        r.append("  VERDICT : ").append(ok
                ? "COUVERTURE COMPLETE -- aucun chunk, aucune colonne n'a ete saute."
                : "COUVERTURE ROMPUE -- la grille ci-dessus est incomplete, voir les ecarts.")
         .append("\n\n");
    }

    private static void ligneCompte(StringBuilder r, String nom, int vu, int attendu) {
        r.append("      ").append(nom);
        for (int k = nom.length(); k < 40; k++) r.append(' ');
        r.append(": ").append(vu).append("  attendu ").append(attendu)
         .append(vu == attendu ? "   OK" : "   <<< ECART").append('\n');
    }

    // ---------------------------------------------------------------- 8
    private static void section8Vues(StringBuilder r, Session s, Etape e, Etape prec) {
        int p = pas(s);
        titre(r, "8. CARTES DU DESSUS  (1 caractere = " + p + " x " + p + " blocs, zone "
                + s.w + " x " + s.d + ")");
        r.append("Toutes les cartes partagent la MEME grille : une colonne de caracteres\n");
        r.append("correspond au meme X dans chacune d'elles, et les lignes sont groupees par\n");
        r.append("16 (un chunk), separateur '|'. Elles se superposent donc directement.\n");
        if (p > 1) {
            r.append("ECHANTILLONNAGE : 1 colonne sur ").append(p)
             .append(" -- la zone depasse ").append(VUE_LARGEUR_MAX).append(" caracteres de large.\n");
        } else {
            r.append("PLEINE RESOLUTION : une colonne de terrain = un caractere, rien n'est agrege.\n");
        }
        r.append('\n');

        r.append("-".repeat(100)).append('\n');
        r.append(" 8a. ALTITUDE ABSOLUE -- ").append(e.nom).append('\n');
        r.append("-".repeat(100)).append('\n');
        carteHauteurs(r, s, e, p);
        r.append('\n');

        r.append("-".repeat(100)).append('\n');
        r.append(" 8b. RELIEF OMBRE -- les courbes et les volumes\n");
        r.append("-".repeat(100)).append('\n');
        carteRelief(r, s, e, p);
        r.append('\n');

        r.append("-".repeat(100)).append('\n');
        r.append(" 8c. COURBES DE NIVEAU (tous les 4 blocs)\n");
        r.append("-".repeat(100)).append('\n');
        carteCourbes(r, s, e, p);
        r.append('\n');

        r.append("-".repeat(100)).append('\n');
        r.append(" 8d. PROFONDEUR AU BLOC PRES sous Y=").append(s.groundY).append('\n');
        r.append("-".repeat(100)).append('\n');
        carteProfondeur(r, s, e, p);
        r.append('\n');

        r.append("-".repeat(100)).append('\n');
        r.append(" 8e. ANOMALIES -- trous nets, falaises, air piege, creusement\n");
        r.append("-".repeat(100)).append('\n');
        carteAnomalies(r, s, e, p);
        r.append('\n');

        r.append("-".repeat(100)).append('\n');
        r.append(" 8f. CHARGEMENT DES CHUNKS (1 caractere = 1 chunk)\n");
        r.append("-".repeat(100)).append('\n');
        r.append("      dans l'emprise : 'O' charge,  'X' NON CHARGE  <-- a examiner\n");
        r.append("      dehors         : 'o' charge,  '.' non charge (attendu)\n\n");
        vueChunks(r, s, e);
        r.append('\n');

        if (prec != null) {
            r.append("-".repeat(100)).append('\n');
            r.append(" 8g. CHANGEMENTS DEPUIS ").append(prec.nom)
             .append("   ('#' modifie, ' ' intact, '@' chunk absent)\n");
            r.append("-".repeat(100)).append('\n');
            vueDiff(r, s, prec, e, p);
            r.append('\n');
        }

        r.append("-".repeat(100)).append('\n');
        r.append(" 8h. VUE 3D OBLIQUE\n");
        r.append("-".repeat(100)).append('\n');
        vue3D(r, s, e);
        r.append('\n');
    }

    private static int pas(Session s) {
        int p = 1;
        while (s.w / p > VUE_LARGEUR_MAX) p += 1;
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

    // =================================================================================
    //  T477 — CARTES. Un caractere par colonne, pleine resolution.
    // =================================================================================

    /** Caractere a produire pour une colonne de la grille. */
    private interface CarCol { char car(int i, int wx, int wz); }

    /** Y solide d'une colonne, ou ABSENT/VIDE. Hors grille : ABSENT. */
    private static int yAt(Session s, Etape e, int wx, int wz) {
        int i = idx(s, wx, wz);
        return i < 0 ? ABSENT : e.ySolid[i];
    }

    /** Vrai si la valeur porte une altitude exploitable. */
    private static boolean util(int y) {
        return y != ABSENT && y != VIDE;
    }

    /**
     * Trame commune a toutes les cartes : regle des X, etiquette des Z, separateurs de
     * chunk tous les 16 blocs. Toute carte lit la MEME grille, donc deux cartes se
     * superposent colonne par colonne.
     */
    private static void carte(StringBuilder r, Session s, int p, CarCol f) {
        regleX(r, s, p, 9);
        for (int z = 0; z < s.d; z += p) {
            if ((z & 15) == 0 && z > 0) {
                r.append(" ".repeat(9));
                for (int cx = 0; cx < s.cw; cx++) r.append('+').append("-".repeat(16 / p));
                r.append("+\n");
            }
            r.append(String.format("z=%6d ", s.z0 + z));
            for (int x = 0; x < s.w; x += p) {
                if ((x & 15) == 0) r.append('|');
                r.append(f.car(z * s.w + x, s.x0 + x, s.z0 + z));
            }
            r.append("|\n");
        }
    }

    /** 8a : altitude ABSOLUE, echelle de 10 niveaux calee sur la zone. */
    private static void carteHauteurs(StringBuilder r, Session s, Etape e, int p) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < e.ySolid.length; i++) {
            int y = e.ySolid[i];
            if (!util(y)) continue;
            if (y < lo) lo = y;
            if (y > hi) hi = y;
        }
        if (lo > hi) { r.append("  (aucune colonne lue)\n"); return; }
        final int flo = lo, fhi = hi;
        final int span = Math.max(1, fhi - flo);
        final char[] RAMPE = {'0','1','2','3','4','5','6','7','8','9'};
        r.append("  Altitude absolue, de Y=").append(flo).append(" ('0') a Y=").append(fhi)
         .append(" ('9'), soit ").append(String.format("%.1f", span / 10.0))
         .append(" bloc(s) par palier.\n");
        r.append("  '@' chunk non charge   ' ' colonne vide\n\n");
        carte(r, s, p, (i, wx, wz) -> {
            int y = e.ySolid[i];
            if (y == ABSENT) return '@';
            if (y == VIDE) return ' ';
            int k = (int) ((long) (y - flo) * 9 / span);
            return RAMPE[Math.max(0, Math.min(9, k))];
        });
    }

    /**
     * 8b : RELIEF OMBRE. Lumiere rasante venue du nord-ouest, pente calculee sur les
     * voisins immediats. C'est la vue qui donne les courbes et les volumes : une pente
     * exposee a la lumiere s'eclaircit, une pente a l'ombre se charge d'encre, un plat
     * reste neutre. Un bord franc apparait comme un trait noir net.
     */
    private static void carteRelief(StringBuilder r, Session s, Etape e, int p) {
        r.append("  Lumiere rasante du nord-ouest. Clair = pente face a la lumiere,\n");
        r.append("  sombre = pente a l'ombre, '=' = plat. Un bord vertical ressort en '#'.\n");
        r.append("  clair ')' '.' ':' '=' '+' '*' '%' '#' sombre    '@' non charge   ' ' vide\n\n");
        final char[] OMBRE = {')','.',':','=','+','*','%','#'};
        carte(r, s, p, (i, wx, wz) -> {
            int y = e.ySolid[i];
            if (y == ABSENT) return '@';
            if (y == VIDE) return ' ';
            int xm = yAt(s, e, wx - p, wz), xp = yAt(s, e, wx + p, wz);
            int zm = yAt(s, e, wx, wz - p), zp = yAt(s, e, wx, wz + p);
            int gx = (util(xm) && util(xp)) ? (xp - xm) : (util(xm) ? (y - xm) * 2 : (util(xp) ? (xp - y) * 2 : 0));
            int gz = (util(zm) && util(zp)) ? (zp - zm) : (util(zm) ? (y - zm) * 2 : (util(zp) ? (zp - y) * 2 : 0));
            int sh = -(gx + gz);
            int k;
            if (sh >= 8) k = 0;
            else if (sh >= 4) k = 1;
            else if (sh >= 1) k = 2;
            else if (sh == 0) k = 3;
            else if (sh >= -3) k = 4;
            else if (sh >= -7) k = 5;
            else if (sh >= -15) k = 6;
            else k = 7;
            return OMBRE[k];
        });
    }

    /** 8c : COURBES DE NIVEAU tous les 4 blocs -- la forme du terrain, sans interpretation. */
    private static void carteCourbes(StringBuilder r, Session s, Etape e, int p) {
        r.append("  Une marque la ou le terrain franchit un multiple de 4 en Y. Les chiffres\n");
        r.append("  donnent la dizaine de l'altitude franchie (Y=120 -> '2'), ce qui permet de\n");
        r.append("  lire une pente sans compter les lignes. ' ' = entre deux courbes.\n\n");
        carte(r, s, p, (i, wx, wz) -> {
            int y = e.ySolid[i];
            if (y == ABSENT) return '@';
            if (y == VIDE) return ' ';
            int n = yAt(s, e, wx + p, wz), m = yAt(s, e, wx, wz + p);
            int pal = Math.floorDiv(y, 4);
            boolean bord = (util(n) && Math.floorDiv(n, 4) != pal) || (util(m) && Math.floorDiv(m, 4) != pal);
            if (!bord) return ' ';
            return (char) ('0' + Math.floorMod(y / 10, 10));
        });
    }

    /**
     * 8d : PROFONDEUR sous le sol de reference, au bloc pres. Chaque caractere est une
     * valeur exacte et non une tranche : c'est la carte a lire pour juger d'un bassin.
     */
    private static void carteProfondeur(StringBuilder r, Session s, Etape e, int p) {
        r.append("  Profondeur SOUS le sol de reference Y=").append(s.groundY).append(" :\n");
        r.append("    '.' au niveau       '1'..'9' de 1 a 9 blocs plus bas\n");
        r.append("    'a'..'z' de 10 a 35 blocs plus bas      '#' plus de 35 blocs plus bas\n");
        r.append("    '^' AU-DESSUS du sol de reference (1 a 9)   'A'..'Z' au-dessus de 10 a 35\n");
        r.append("    '*' plus de 35 blocs au-dessus   '@' non charge   ' ' vide\n\n");
        carte(r, s, p, (i, wx, wz) -> {
            int y = e.ySolid[i];
            if (y == ABSENT) return '@';
            if (y == VIDE) return ' ';
            int d = s.groundY - y;
            if (d == 0) return '.';
            if (d > 0) {
                if (d <= 9) return (char) ('0' + d);
                if (d <= 35) return (char) ('a' + (d - 10));
                return '#';
            }
            int u = -d;
            if (u <= 9) return '^';
            if (u <= 35) return (char) ('A' + (u - 10));
            return '*';
        });
    }

    /**
     * 8e : ANOMALIES. Tout ce qui ne ressemble pas a du terrain : marche verticale, trou
     * net, air piege, colonne creusee bien en dessous du relief theorique. C'est la carte
     * qui montre les « trous nets » et les bords francs sans avoir a les chercher.
     */
    private static void carteAnomalies(StringBuilder r, Session s, Etape e, int p) {
        int[] th = s.bruit;
        final boolean avecTheo = th != null && th.length == s.w * s.d;
        r.append("  'V' trou NET : la colonne est >= 4 blocs sous ses 4 voisins (cuvette franche)\n");
        r.append("  'T' tour : la colonne est >= 4 blocs AU-DESSUS de ses 4 voisins\n");
        r.append("  'F' falaise : marche >= 4 blocs avec un voisin        '!' air piege dessous\n");
        r.append("  'c' creuse : >= 4 blocs sous le relief theorique de generation\n");
        r.append("  'r' remblai : >= 4 blocs au-dessus du relief theorique\n");
        r.append("  '.' rien a signaler    '@' non charge    ' ' vide\n");
        if (!avecTheo) r.append("  (relief theorique indisponible : 'c' et 'r' ne sont pas calcules)\n");
        r.append('\n');
        final int[] theo = th;
        carte(r, s, p, (i, wx, wz) -> {
            int y = e.ySolid[i];
            if (y == ABSENT) return '@';
            if (y == VIDE) return ' ';
            int n0 = yAt(s, e, wx - p, wz), n1 = yAt(s, e, wx + p, wz);
            int n2 = yAt(s, e, wx, wz - p), n3 = yAt(s, e, wx, wz + p);
            int nb = 0, sousTous = 0, surTous = 0, marche = 0;
            int[] vs = {n0, n1, n2, n3};
            for (int v : vs) {
                if (!util(v)) continue;
                nb++;
                if (v - y >= 4) sousTous++;
                if (y - v >= 4) surTous++;
                int md = Math.abs(v - y);
                if (md > marche) marche = md;
            }
            if (nb == 4 && sousTous == 4) return 'V';
            if (nb == 4 && surTous == 4) return 'T';
            if (marche >= 4) return 'F';
            if (e.airSous[i] > 0) return '!';
            if (avecTheo) {
                int t = theo[i];
                if (t != ABSENT) {
                    if (t - y >= 4) return 'c';
                    if (y - t >= 4) return 'r';
                }
            }
            return '.';
        });
    }

    /**
     * 8h : VUE 3D OBLIQUE. Le relief est projete depuis le sud, en vue plongeante : les
     * Z lointains montent dans l'image, l'altitude la fait monter davantage, et le
     * remplissage sous la crete donne un volume plein. L'occlusion est traitee du plus
     * proche au plus lointain : ce qui est devant masque ce qui est derriere, exactement
     * comme a l'ecran. Une cuvette se lit comme un creux, un trou net comme une encoche.
     */
    private static void vue3D(StringBuilder r, Session s, Etape e) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < e.ySolid.length; i++) {
            int y = e.ySolid[i];
            if (!util(y)) continue;
            if (y < lo) lo = y;
            if (y > hi) hi = y;
        }
        if (lo > hi) { r.append("  (aucune colonne lue : pas de vue 3D)\n"); return; }
        int px = Math.max(1, (s.w + VUE_LARGEUR_MAX - 1) / VUE_LARGEUR_MAX);
        int nx = s.w / px;
        int span = Math.max(1, hi - lo);
        // Hauteur d'image partagee entre la fuite en Z et l'amplitude du relief.
        double zComp = 0.45, yEch = 0.50;
        int besoin = (int) (s.d * zComp / Math.max(1, px)) + (int) (span * yEch) + 4;
        double k = besoin > VUE3D_HAUTEUR ? (double) VUE3D_HAUTEUR / besoin : 1.0;
        zComp *= k; yEch *= k;
        int haut = (int) (s.d * zComp / Math.max(1, px)) + (int) (span * yEch) + 4;
        char[][] toile = new char[haut][nx];
        for (char[] l : toile) java.util.Arrays.fill(l, ' ');
        final char[] RAMPE = {'.',':','-','=','+','*','%','#'};
        int bas = haut - 1;
        // Du plus PROCHE (z max) au plus LOINTAIN : on n'ecrit que dans le vide, donc
        // le premier plan gagne et masque l'arriere-plan.
        for (int z = s.d - 1; z >= 0; z--) {
            int rowBase = bas - (int) ((s.d - 1 - z) * zComp / Math.max(1, px));
            if (rowBase < 0) continue;
            for (int xc = 0; xc < nx; xc++) {
                int x = xc * px;
                int y = e.ySolid[z * s.w + x];
                if (!util(y)) continue;
                int top = rowBase - (int) ((y - lo) * yEch);
                if (top < 0) top = 0;
                if (top > bas) continue;
                int ki = (int) ((long) (y - lo) * 7 / span);
                char c = RAMPE[Math.max(0, Math.min(7, ki))];
                for (int row = top; row <= rowBase && row <= bas; row++) {
                    if (toile[row][xc] != ' ') break;   // occlusion : deja peint par devant
                    toile[row][xc] = (row == top) ? c : ':';
                }
            }
        }
        r.append("  Vue prise du SUD (Z croissant vers l'observateur), en plongee.\n");
        r.append("  1 caractere = ").append(px).append(" bloc(s) en X.  Altitudes de Y=")
         .append(lo).append(" a Y=").append(hi).append(".\n");
        r.append("  Crete : '.' bas ':' '-' '=' '+' '*' '%' '#' haut.  Corps du relief : ':'.\n");
        r.append("  Le premier plan masque l'arriere-plan : un creux se voit en encoche.\n\n");
        for (int row = 0; row < haut; row++) {
            r.append("   ").append(new String(toile[row])).append('\n');
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

        r.append('\n').append("-".repeat(100)).append('\n');
        r.append(" 9f. TAUX BRUT DU SPREAD DE NEIGE PAR COLONNE\n");
        r.append("     Potentiel continu lisse en milli-unites (0..1000), echantillonne aux\n");
        r.append("     coordonnees monde de chaque colonne; aucun seuil ni decision par chunk.\n");
        r.append("     ????=profil non disponible, ----=chunk non charge a cette capture.\n");
        r.append("-".repeat(100)).append('\n');
        grilleSnowSpread(r, s, e);

        r.append('\n').append("-".repeat(100)).append('\n');
        r.append(" 9g. COUVERTURE DE NEIGE OBSERVEE PAR COLONNE\n");
        r.append("     1000=vraie couche SNOW trouvee dans les 48 blocs autour de la surface\n");
        r.append("     (canopee comprise); 0000=aucune couche; ????=colonne non lue; ----=chunk absent.\n");
        r.append("     Mesure directe, distincte du potentiel calcule en 9f; cavernes profondes exclues.\n");
        r.append("-".repeat(100)).append('\n');
        grilleSnowObserved(r, s, e);
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

    /** Grille monde du potentiel continu ; lecture seule d'un instantane deja publie. */
    private static void grilleSnowSpread(StringBuilder r, Session s, Etape e) {
        r.append(" ".repeat(9));
        for (int cx = 0; cx < s.cw; cx++) {
            String label = "x=" + ((s.cx0 + cx) << 4);
            r.append('|').append(label);
            for (int k = label.length(); k < 16 * 4; k++) r.append(' ');
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
                int chunkIndex = (z >> 4) * s.cw + (x >> 4);
                if (!e.chunkCharge[chunkIndex]) {
                    r.append("----");
                    continue;
                }
                float rate = e.snowSpread == null ? Float.NaN
                        : e.snowSpread.rateAtBlock(s.x0 + x, s.z0 + z);
                if (Float.isNaN(rate)) r.append("????");
                else if (rate < 0f) r.append("NEUT");
                else {
                    // Stabilise les float binaires (.315f peut etre 314.99999)
                    // sans arrondir .0525 vers 0.053 : ces valeurs cadrent le modele.
                    int milli = (int) Math.floor(rate * 1000.0 + 0.001);
                    r.append(String.format("%4d", Math.max(0, Math.min(9999, milli))));
                }
            }
            r.append("|\n");
        }
    }

    /** Carte brute de toute couche SNOW dans la tranche de surface, canopee comprise. */
    private static void grilleSnowObserved(StringBuilder r, Session s, Etape e) {
        for (int cz = 0; cz < s.cd; cz++) {
            r.append(" ".repeat(9));
            for (int cx = 0; cx < s.cw; cx++) r.append('+').append("-".repeat(64));
            r.append("+\n");
            for (int lz = 0; lz < 16; lz++) {
                int z = cz * 16 + lz;
                if (z >= s.d) break;
                r.append(String.format("z=%6d ", s.z0 + z));
                for (int cx = 0; cx < s.cw; cx++) {
                    r.append('|');
                    for (int lx = 0; lx < 16; lx++) {
                        int x = cx * 16 + lx;
                        if (x >= s.w) break;
                        int i = z * s.w + x;
                        if (!e.chunkCharge[cz * s.cw + cx]) { r.append("----"); continue; }
                        if (e.ySurface[i] == ABSENT || e.idSurface[i] == 0) { r.append("????"); continue; }
                        r.append(e.coucheNeige[i] ? "1000" : "0000");
                    }
                }
                r.append("|\n");
            }
        }
        r.append(" ".repeat(9));
        for (int cx = 0; cx < s.cw; cx++) r.append('+').append("-".repeat(64));
        r.append("+\n");
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
