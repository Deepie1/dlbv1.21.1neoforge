package deepluckyblock.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T471 — PHOTOGRAPHIE DU TERRAIN, ETAPE PAR ETAPE, AU BLOC PRES.
 *
 * <p><b>Pourquoi cette classe existe.</b> Huit tours de correctifs (T459 a T468) ont ete
 * passes sur le raccord de terrain du Crimson Lake sans jamais le reparer, puis annules en
 * bloc par T469. La raison tient en une phrase : <i>personne n'a jamais vu le terrain</i>.
 * On corrigeait sur la foi de captures d'ecran et d'hypotheses, en relancant une pose de
 * huit minutes a chaque essai. Les bancs, eux, ne voient ni le relief ni les chunks.</p>
 *
 * <p>Cette classe ne corrige rien. Elle <b>mesure</b>, et elle rend un seul fichier qu'on
 * peut lire et comparer hors du jeu : l'etat exact de chaque colonne de la zone, a chaque
 * etape du pipeline, plus l'analyse des anomalies que ces etats revelent.</p>
 *
 * <p><b>Ce qu'elle regarde.</b> Toute l'emprise de la structure, plus un anneau de
 * {@value #DEFAULT_RING_CHUNKS} chunks tout autour — c'est la que vit le raccord, et c'est
 * donc la que se voient les failles. Pour chaque colonne : le sommet reel, le sommet
 * SOLIDE, la surface d'eau, les deux blocs correspondants, et le volume d'air piege sous le
 * sol. Pour chaque chunk : s'il etait charge au moment de la lecture.</p>
 *
 * <p><b>Lecture passive, et c'est capital.</b> Rien ici ne genere, ne charge ni ne demande
 * un chunk : on lit {@link SafeSurface#cached} et, quand le chunk n'est pas la, la colonne
 * est marquee ABSENTE plutot que forcee. Un audit qui generait des chunks changerait
 * precisement ce qu'il est cense observer, et ajouterait 500 generations a un pipeline qui
 * fait deja monter le serveur a 497 ticks de retard. Un chunk absent n'est pas un trou dans
 * la mesure : c'est une mesure en soi — un chunk que le pipeline n'a pas tenu ne peut pas
 * avoir ete lisse.</p>
 *
 * <p><b>Cout.</b> Une capture lit {@code largeur x profondeur} colonnes sur une tranche
 * verticale bornee autour du sol. Sur l'emprise du lac (246 x 284) plus trois chunks, cela
 * fait environ 130 000 colonnes et une fraction de seconde par etape, sans aucune ecriture
 * ni generation. Le fichier n'est ecrit qu'a la toute fin.</p>
 */
public final class TerrainAudit {

    private TerrainAudit() {
    }

    /** Rayon d'anneau, en chunks, ajoute autour de l'emprise. Le raccord vit la. */
    public static final int DEFAULT_RING_CHUNKS = 3;

    /** Hauteur scannee au-dessus du sol de reference. */
    private static final int SCAN_ABOVE = 120;
    /** Profondeur scannee sous le sol de reference. */
    private static final int SCAN_BELOW = 80;

    /** Profondeur de recherche d'air PIEGE sous le premier solide. */
    private static final int AIR_PROBE_DEPTH = 48;

    /** Au-dela, la palette deborde et les blocs en trop sont ranges sous « autre ». */
    private static final int PALETTE_MAX = 254;

    /** Valeur des colonnes dont le chunk n'etait pas charge. */
    public static final short ABSENT = Short.MIN_VALUE;
    /** Valeur des colonnes chargees mais entierement vides sur la tranche scannee. */
    public static final short VIDE = Short.MIN_VALUE + 1;

    private static volatile boolean armed = Boolean.getBoolean("dlb.audit.terrain");

    /** Arme l'audit pour la PROCHAINE construction. Pose par la commande de test. */
    public static void arm() {
        armed = true;
    }

    /** Rend l'armement a son etat par defaut (la propriete systeme, sinon desarme). */
    public static void disarm() {
        armed = Boolean.getBoolean("dlb.audit.terrain");
    }

    public static boolean armed() {
        return armed;
    }

    /** Vrai si une session est ouverte : les accroches peuvent appeler sans se garder. */
    public static boolean running() {
        return session != null;
    }

    // =====================================================================================
    //  ETAT
    // =====================================================================================

    private static final class Etape {
        final String nom;
        final long tMs;
        final short[] ySurface;
        final short[] ySolid;
        final short[] yWater;
        final byte[] idSurface;
        final byte[] idSolid;
        final short[] airSous;
        final boolean[] chunkCharge;
        int colonnesLues;
        int colonnesAbsentes;
        int colonnesBruit;

        Etape(String nom, long tMs, int n, int nChunks) {
            this.nom = nom;
            this.tMs = tMs;
            this.ySurface = new short[n];
            this.ySolid = new short[n];
            this.yWater = new short[n];
            this.idSurface = new byte[n];
            this.idSolid = new byte[n];
            this.airSous = new short[n];
            this.chunkCharge = new boolean[nChunks];
        }
    }

    private static final class Session {
        ServerLevel level;
        String label;
        long t0;
        // emprise de la structure
        int ex0, ez0, ex1, ez1;
        int groundY;
        // zone auditee (emprise + anneau), alignee sur les chunks
        int x0, z0, w, d;
        int cx0, cz0, cw, cd;
        int yFloor, yTop;
        final List<Etape> etapes = new ArrayList<>(6);
        final List<String> palette = new ArrayList<>(PALETTE_MAX);
        final Map<String, Integer> paletteIdx = new HashMap<>(PALETTE_MAX);
        int[] templateCols;
        long templateBlocs;
        String note = "";
    }

    private static volatile Session session;

    /**
     * L'audit trace dans la console du serveur, pas via {@code DebugLog} : celui-ci est muet
     * par defaut depuis T221 (« silence pour la publication »), et un audit qu'on vient
     * d'armer explicitement ne doit jamais etre silencieux.
     */
    private static void log(Object msg) {
        org.slf4j.LoggerFactory.getLogger("deep_lucky_block").info("{}", msg);
    }

    // =====================================================================================
    //  CYCLE DE VIE
    // =====================================================================================

    /**
     * Ouvre une session. Sans appel a {@link #arm()} ni propriete systeme, ne fait rien :
     * l'audit ne doit jamais couter quoi que ce soit a une partie normale.
     *
     * @param min coin bas de l'emprise de la structure
     * @param max coin haut de l'emprise
     * @param groundY sol de reference retenu par la sonde — centre de la tranche scannee
     */
    public static void begin(ServerLevel level, String label, BlockPos min, BlockPos max,
                             int groundY, int ringChunks) {
        if (!armed || level == null || min == null || max == null) return;
        try {
            Session s = new Session();
            s.level = level;
            s.label = label == null ? "structure" : label;
            s.t0 = System.currentTimeMillis();
            s.ex0 = Math.min(min.getX(), max.getX());
            s.ex1 = Math.max(min.getX(), max.getX());
            s.ez0 = Math.min(min.getZ(), max.getZ());
            s.ez1 = Math.max(min.getZ(), max.getZ());
            s.groundY = groundY;

            int ring = Math.max(0, ringChunks) * 16;
            s.cx0 = (s.ex0 - ring) >> 4;
            s.cz0 = (s.ez0 - ring) >> 4;
            int cx1 = (s.ex1 + ring) >> 4;
            int cz1 = (s.ez1 + ring) >> 4;
            s.cw = cx1 - s.cx0 + 1;
            s.cd = cz1 - s.cz0 + 1;
            s.x0 = s.cx0 << 4;
            s.z0 = s.cz0 << 4;
            s.w = s.cw * 16;
            s.d = s.cd * 16;

            s.yFloor = Math.max(level.getMinBuildHeight(), groundY - SCAN_BELOW);
            s.yTop = Math.min(level.getMaxBuildHeight() - 1, groundY + SCAN_ABOVE);
            s.templateCols = new int[s.w * s.d];
            session = s;
            log("[DLB-AUDIT] session ouverte " + s.label + " : zone " + s.w + "x" + s.d
                    + " (" + s.cw + "x" + s.cd + " chunks), Y " + s.yFloor + ".." + s.yTop);
        } catch (Throwable t) {
            session = null;
        }
    }

    /**
     * Ferme la session sans rien ecrire et rend l'armement a son etat par defaut.
     *
     * <p>Le desarmement a lieu MEME si aucune session n'etait ouverte : une construction
     * qui echoue avant la premiere capture laisserait sinon l'audit arme, et le prochain
     * spawn naturel serait mesure a l'insu du joueur. Appeler cette methode deux fois est
     * sans effet.</p>
     */
    public static void abort(String raison) {
        Session s = session;
        session = null;
        armed = Boolean.getBoolean("dlb.audit.terrain");
        if (s == null) return;
        log("[DLB-AUDIT] session abandonnee : " + raison);
    }

    /**
     * Enregistre qu'une colonne du monde recoit au moins un bloc du gabarit.
     *
     * <p>C'est la mesure qui repond a « est-ce que l'ancien terrain reste dans ma
     * structure » : une colonne de l'emprise que le gabarit n'ecrit jamais laisse
     * forcement voir ce qu'il y avait avant.</p>
     */
    public static void noteTemplateBlock(int worldX, int worldZ) {
        Session s = session;
        if (s == null) return;
        int i = index(s, worldX, worldZ);
        if (i < 0) return;
        s.templateCols[i]++;
        s.templateBlocs++;
    }

    public static void note(String texte) {
        Session s = session;
        if (s != null && texte != null) s.note = s.note.isEmpty() ? texte : s.note + " | " + texte;
    }

    private static int index(Session s, int x, int z) {
        int dx = x - s.x0, dz = z - s.z0;
        if (dx < 0 || dz < 0 || dx >= s.w || dz >= s.d) return -1;
        return dz * s.w + dx;
    }

    // =====================================================================================
    //  CAPTURE
    // =====================================================================================

    /**
     * Photographie l'etat courant de toute la zone. Lecture passive : un chunk absent est
     * note absent, jamais demande.
     */
    public static void snapshot(ServerLevel level, String nomEtape) {
        Session s = session;
        if (s == null || level == null || level != s.level) return;
        try {
            long t0 = System.nanoTime();
            Etape e = new Etape(nomEtape, System.currentTimeMillis() - s.t0, s.w * s.d, s.cw * s.cd);
            BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
            net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
            net.minecraft.world.level.levelgen.RandomState rs = level.getChunkSource().randomState();

            for (int cz = 0; cz < s.cd; cz++) {
                for (int cx = 0; cx < s.cw; cx++) {
                    ChunkAccess chunk = SafeSurface.cached(level, s.cx0 + cx, s.cz0 + cz);
                    boolean charge = chunk != null;
                    e.chunkCharge[cz * s.cw + cx] = charge;
                    int bx0 = (s.cx0 + cx) << 4, bz0 = (s.cz0 + cz) << 4;
                    // T58 (lecon de SafeSurface.height) : ne JAMAIS payer par colonne ce qui
                    // se calcule par chunk. Le ciel represente l'essentiel de la tranche
                    // scannee ; les sections vides se sautent en O(1) et le depart de scan
                    // vaut pour les 256 colonnes du chunk. Sans cela : 200 lectures x 130 000
                    // colonnes = 26 M d'acces par etape, sur le thread serveur, alors qu'il
                    // affiche deja « Can't keep up! ».
                    int yDepart = charge ? departScan(chunk, s.yTop, s.yFloor) : s.yFloor;
                    for (int oz = 0; oz < 16; oz++) {
                        for (int ox = 0; ox < 16; ox++) {
                            int i = index(s, bx0 + ox, bz0 + oz);
                            if (i < 0) continue;
                            if (!charge) {
                                // Chunk pas en memoire : plutot que de rendre un trou, on
                                // lit le relief THEORIQUE par bruit pur -- zero generation,
                                // zero chargement (modele wetFraction de T465). L'anneau
                                // exterieur reste donc entierement lisible, et c'est
                                // justement la que se joue le raccord.
                                int h = bruit(level, gen, rs, bx0 + ox, bz0 + oz);
                                if (h == Integer.MIN_VALUE) {
                                    e.ySurface[i] = ABSENT;
                                    e.ySolid[i] = ABSENT;
                                    e.yWater[i] = ABSENT;
                                    e.colonnesAbsentes++;
                                } else {
                                    e.ySolid[i] = (short) clampShort(h);
                                    int mer = Math.max(level.getSeaLevel(), h);
                                    e.ySurface[i] = (short) clampShort(mer);
                                    e.yWater[i] = (short) (h < level.getSeaLevel()
                                            ? clampShort(level.getSeaLevel()) : VIDE);
                                    e.colonnesBruit++;
                                }
                                continue;
                            }
                            lireColonne(s, e, chunk, bp, bx0 + ox, bz0 + oz, i, yDepart);
                            e.colonnesLues++;
                        }
                    }
                }
            }
            s.etapes.add(e);
            log("[DLB-AUDIT] " + nomEtape + " : " + e.colonnesLues + " colonnes lues, "
                    + e.colonnesBruit + " par bruit, " + e.colonnesAbsentes + " absentes, "
                    + ((System.nanoTime() - t0) / 1_000_000L) + " ms");
        } catch (Throwable t) {
            log("[DLB-AUDIT] capture " + nomEtape + " echouee : " + t);
        }
    }

    /** Relief theorique par bruit pur : ne genere rien, ne charge rien, ne demande rien. */
    private static int bruit(ServerLevel level,
                             net.minecraft.world.level.chunk.ChunkGenerator gen,
                             net.minecraft.world.level.levelgen.RandomState rs, int x, int z) {
        try {
            return gen.getBaseHeight(x, z,
                    net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /** Y du sommet de la plus haute section non vide : tout ce qui est au-dessus est du ciel. */
    private static int departScan(ChunkAccess chunk, int yTop, int yFloor) {
        try {
            net.minecraft.world.level.chunk.LevelChunkSection[] secs = chunk.getSections();
            for (int si = secs.length - 1; si >= 0; si--) {
                net.minecraft.world.level.chunk.LevelChunkSection sec = secs[si];
                if (sec == null || sec.hasOnlyAir()) continue;
                int hautSection = chunk.getMinBuildHeight() + (si << 4) + 15;
                return Math.min(yTop, hautSection);
            }
            return yFloor;
        } catch (Throwable t) {
            return yTop;
        }
    }

    private static void lireColonne(Session s, Etape e, ChunkAccess chunk,
                                    BlockPos.MutableBlockPos bp, int x, int z, int i,
                                    int yDepart) {
        int ySurface = Integer.MIN_VALUE;
        int ySolid = Integer.MIN_VALUE;
        int yWater = Integer.MIN_VALUE;
        BlockState stSurface = null;
        BlockState stSolid = null;
        int air = 0;
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
            if (!estAir && ySurface == Integer.MIN_VALUE) {
                ySurface = y;
                stSurface = st;
            }
            if (estFluide && yWater == Integer.MIN_VALUE) {
                yWater = y;
            }
            if (!estAir && !estFluide && !solideVu) {
                ySolid = y;
                stSolid = st;
                solideVu = true;
                // Sous le premier solide, on ne cherche plus que l'air piege, et sur une
                // profondeur bornee : le reste du sous-sol ne dit rien du raccord.
                yStop = Math.max(s.yFloor, y - AIR_PROBE_DEPTH);
            }
            // Volume d'air PIEGE : ne compte que ce qui se trouve sous le premier solide.
            // C'est la signature d'une cavite, d'une fosse comblee en surface, ou d'un
            // clear qui a creuse sans que rien ne retombe.
            if (solideVu && estAir) air++;
        }

        e.ySurface[i] = (short) (ySurface == Integer.MIN_VALUE ? VIDE : clampShort(ySurface));
        e.ySolid[i] = (short) (ySolid == Integer.MIN_VALUE ? VIDE : clampShort(ySolid));
        e.yWater[i] = (short) (yWater == Integer.MIN_VALUE ? VIDE : clampShort(yWater));
        e.idSurface[i] = (byte) idDe(s, stSurface);
        e.idSolid[i] = (byte) idDe(s, stSolid);
        e.airSous[i] = (short) Math.min(Short.MAX_VALUE, air);
    }

    private static int clampShort(int v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE + 2) return Short.MIN_VALUE + 2;
        return v;
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
        int v = s.palette.size(); // 0 reste « rien »
        s.paletteIdx.put(nom, v);
        return v;
    }

    // =====================================================================================
    //  ECRITURE
    // =====================================================================================

    /**
     * Ecrit le rapport et ferme la session. Un seul fichier ZIP, dans le dossier du monde,
     * sous {@code dlb_audit/}.
     *
     * @return le chemin ecrit, ou {@code null} si rien n'a ete ecrit
     */
    public static java.nio.file.Path finish(ServerLevel level) {
        Session s = session;
        if (s == null) return null;
        session = null;
        // Un armement vaut pour UNE construction : sans cela le prochain spawn naturel
        // serait audite a son tour, a l'insu du joueur.
        armed = Boolean.getBoolean("dlb.audit.terrain");
        if (s.etapes.isEmpty()) return null;
        try {
            java.nio.file.Path dir = level.getServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("dlb_audit");
            java.nio.file.Files.createDirectories(dir);
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                    .format(new java.util.Date());
            java.nio.file.Path out = dir.resolve("dlb-terrain-" + safe(s.label) + "-" + stamp + ".zip");

            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                    new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(out)))) {
                zip.setLevel(9);
                ecrire(zip, "RAPPORT.txt", rapport(s));
                ecrire(zip, "palette.txt", palette(s));
                ecrire(zip, "chunks.csv", chunksCsv(s));
                ecrire(zip, "template_colonnes.txt",
                        grilleInt(s, s.templateCols, 0));
                for (int n = 0; n < s.etapes.size(); n++) {
                    Etape e = s.etapes.get(n);
                    String base = "etapes/" + n + "_" + safe(e.nom) + "_";
                    ecrire(zip, base + "ySolid.txt", grilleShort(s, e.ySolid, s.groundY));
                    ecrire(zip, base + "ySurface.txt", grilleShort(s, e.ySurface, s.groundY));
                    ecrire(zip, base + "yWater.txt", grilleShort(s, e.yWater, s.groundY));
                    ecrire(zip, base + "idSolid.txt", grilleByte(s, e.idSolid));
                    ecrire(zip, base + "idSurface.txt", grilleByte(s, e.idSurface));
                    ecrire(zip, base + "airSous.txt", grilleShort(s, e.airSous, 0));
                }
            }
            log("[DLB-AUDIT] rapport ecrit : " + out);
            annonce(level, out);
            return out;
        } catch (Throwable t) {
            log("[DLB-AUDIT] ecriture impossible : " + t);
            return null;
        }
    }

    /**
     * Annonce le chemin en clair a tous les joueurs. Volontairement hors de
     * {@code announceLakeProgress}, dont la limite d'un message toutes les 20 s pourrait
     * avaler justement celui qui donne le fichier a recuperer.
     */
    private static void annonce(ServerLevel level, java.nio.file.Path out) {
        try {
            net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component
                    .literal("[DLB] Audit de terrain ecrit : " + out)
                    .withStyle(net.minecraft.ChatFormatting.AQUA);
            for (net.minecraft.server.level.ServerPlayer pl : level.players()) {
                pl.sendSystemMessage(msg);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ecrire(java.util.zip.ZipOutputStream zip, String nom, CharSequence contenu)
            throws java.io.IOException {
        zip.putNextEntry(new java.util.zip.ZipEntry(nom));
        zip.write(contenu.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String safe(String s) {
        return s == null ? "x" : s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** Grille de hauteurs, en ECART au sol de reference : des petits nombres, donc un petit fichier. */
    private static CharSequence grilleShort(Session s, short[] data, int ref) {
        StringBuilder sb = new StringBuilder(data.length * 4);
        sb.append("# origine x=").append(s.x0).append(" z=").append(s.z0)
          .append(" largeur=").append(s.w).append(" profondeur=").append(s.d)
          .append(" reference=").append(ref).append('\n');
        sb.append("# valeurs = ecart a la reference ; A = chunk absent ; V = colonne vide\n");
        for (int z = 0; z < s.d; z++) {
            int base = z * s.w;
            for (int x = 0; x < s.w; x++) {
                if (x > 0) sb.append(' ');
                short v = data[base + x];
                if (v == ABSENT) sb.append('A');
                else if (v == VIDE) sb.append('V');
                else sb.append(v - ref);
            }
            sb.append('\n');
        }
        return sb;
    }

    private static CharSequence grilleInt(Session s, int[] data, int ref) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        sb.append("# origine x=").append(s.x0).append(" z=").append(s.z0)
          .append(" largeur=").append(s.w).append(" profondeur=").append(s.d).append('\n');
        sb.append("# nombre de blocs du gabarit ecrits dans la colonne\n");
        for (int z = 0; z < s.d; z++) {
            int base = z * s.w;
            for (int x = 0; x < s.w; x++) {
                if (x > 0) sb.append(' ');
                sb.append(data[base + x] - ref);
            }
            sb.append('\n');
        }
        return sb;
    }

    private static CharSequence grilleByte(Session s, byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 4);
        sb.append("# indices de palette (voir palette.txt), 0 = rien\n");
        for (int z = 0; z < s.d; z++) {
            int base = z * s.w;
            for (int x = 0; x < s.w; x++) {
                if (x > 0) sb.append(' ');
                sb.append(data[base + x] & 0xFF);
            }
            sb.append('\n');
        }
        return sb;
    }

    private static CharSequence palette(Session s) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("0\t(rien)\n");
        for (int i = 0; i < s.palette.size(); i++) {
            sb.append(i + 1).append('\t').append(s.palette.get(i)).append('\n');
        }
        if (s.palette.size() >= PALETTE_MAX) sb.append(PALETTE_MAX).append("\t(palette saturee)\n");
        return sb;
    }

    private static CharSequence chunksCsv(Session s) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("chunkX,chunkZ,dansEmprise");
        for (Etape e : s.etapes) sb.append(",charge_").append(safe(e.nom));
        for (int n = 1; n < s.etapes.size(); n++) {
            sb.append(",colonnesChangees_").append(safe(s.etapes.get(n).nom));
        }
        sb.append('\n');
        for (int cz = 0; cz < s.cd; cz++) {
            for (int cx = 0; cx < s.cw; cx++) {
                int wx = (s.cx0 + cx) << 4, wz = (s.cz0 + cz) << 4;
                boolean dans = wx + 15 >= s.ex0 && wx <= s.ex1 && wz + 15 >= s.ez0 && wz <= s.ez1;
                sb.append(s.cx0 + cx).append(',').append(s.cz0 + cz).append(',').append(dans ? 1 : 0);
                for (Etape e : s.etapes) sb.append(',').append(e.chunkCharge[cz * s.cw + cx] ? 1 : 0);
                for (int n = 1; n < s.etapes.size(); n++) {
                    sb.append(',').append(changesDansChunk(s, s.etapes.get(n - 1),
                            s.etapes.get(n), cx, cz));
                }
                sb.append('\n');
            }
        }
        return sb;
    }

    private static int changesDansChunk(Session s, Etape a, Etape b, int cx, int cz) {
        int n = 0;
        int bx0 = (s.cx0 + cx) << 4, bz0 = (s.cz0 + cz) << 4;
        for (int oz = 0; oz < 16; oz++) {
            for (int ox = 0; ox < 16; ox++) {
                int i = index(s, bx0 + ox, bz0 + oz);
                if (i < 0) continue;
                if (a.ySolid[i] == ABSENT || b.ySolid[i] == ABSENT) continue;
                if (a.ySolid[i] != b.ySolid[i] || a.ySurface[i] != b.ySurface[i]) n++;
            }
        }
        return n;
    }

    // =====================================================================================
    //  ANALYSE
    // =====================================================================================

    private static CharSequence rapport(Session s) {
        StringBuilder r = new StringBuilder(64 * 1024);
        Etape premiere = s.etapes.get(0);
        Etape derniere = s.etapes.get(s.etapes.size() - 1);

        r.append("================================================================\n");
        r.append(" DLB — AUDIT DE TERRAIN : ").append(s.label).append('\n');
        r.append("================================================================\n\n");
        r.append("dimension        : ").append(s.level.dimension().location()).append('\n');
        r.append("graine           : ").append(s.level.getSeed()).append('\n');
        r.append("emprise          : x ").append(s.ex0).append("..").append(s.ex1)
         .append("  z ").append(s.ez0).append("..").append(s.ez1)
         .append("  (").append(s.ex1 - s.ex0 + 1).append(" x ").append(s.ez1 - s.ez0 + 1).append(")\n");
        r.append("zone auditee     : x ").append(s.x0).append("..").append(s.x0 + s.w - 1)
         .append("  z ").append(s.z0).append("..").append(s.z0 + s.d - 1)
         .append("  (").append(s.w).append(" x ").append(s.d).append(" = ")
         .append(s.w * s.d).append(" colonnes)\n");
        r.append("chunks           : ").append(s.cw).append(" x ").append(s.cd)
         .append(" = ").append(s.cw * s.cd).append('\n');
        r.append("sol de reference : Y=").append(s.groundY)
         .append("   tranche scannee Y ").append(s.yFloor).append("..").append(s.yTop).append('\n');
        r.append("blocs du gabarit : ").append(s.templateBlocs).append('\n');
        if (!s.note.isEmpty()) r.append("notes            : ").append(s.note).append('\n');
        r.append('\n');

        // ---- 1. etapes
        r.append("----------------------------------------------------------------\n");
        r.append(" 1. ETAPES CAPTUREES\n");
        r.append("----------------------------------------------------------------\n");
        r.append(String.format("%-3s %-22s %9s %9s %9s %9s %11s%n",
                "#", "etape", "t(ms)", "lues", "bruit", "absentes", "chunks chg."));
        for (int n = 0; n < s.etapes.size(); n++) {
            Etape e = s.etapes.get(n);
            int ch = 0;
            for (boolean b : e.chunkCharge) if (b) ch++;
            r.append(String.format("%-3d %-22s %9d %9d %9d %9d %11s%n",
                    n, e.nom, e.tMs, e.colonnesLues, e.colonnesBruit, e.colonnesAbsentes,
                    ch + "/" + e.chunkCharge.length));
        }
        r.append('\n');
        r.append("  lues     : relevees bloc par bloc dans un chunk charge : l'etat REEL du monde.\n");
        r.append("  bruit    : chunk pas en memoire, relief THEORIQUE recalcule par bruit pur. La\n");
        r.append("             forme du terrain est juste, mais rien de ce que le pipeline a pu\n");
        r.append("             ecrire la ne s'y voit. Voir la carte de chargement, section 6.\n");
        r.append("  absentes : ni chunk, ni bruit exploitable (cas rare).\n\n");
        r.append("Un chunk non charge ne peut etre ni coupe, ni pose, ni lisse. S'il tombe dans\n");
        r.append("l'emprise, c'est l'anomalie elle-meme, et non un defaut de la mesure.\n\n");

        // ---- 2. chunks sautes
        r.append("----------------------------------------------------------------\n");
        r.append(" 2. CHUNKS SAUTES ENTRE DEUX ETAPES\n");
        r.append("----------------------------------------------------------------\n");
        for (int n = 1; n < s.etapes.size(); n++) {
            Etape a = s.etapes.get(n - 1), b = s.etapes.get(n);
            int totalTouches = 0, totalEmprise = 0, sautes = 0;
            StringBuilder liste = new StringBuilder();
            for (int cz = 0; cz < s.cd; cz++) {
                for (int cx = 0; cx < s.cw; cx++) {
                    int wx = (s.cx0 + cx) << 4, wz = (s.cz0 + cz) << 4;
                    boolean dans = wx + 15 >= s.ex0 && wx <= s.ex1 && wz + 15 >= s.ez0 && wz <= s.ez1;
                    int ch = changesDansChunk(s, a, b, cx, cz);
                    if (ch > 0) totalTouches++;
                    if (dans) {
                        totalEmprise++;
                        if (ch == 0) {
                            sautes++;
                            if (sautes <= 40) {
                                liste.append("      chunk ").append(s.cx0 + cx).append(',')
                                     .append(s.cz0 + cz).append("  (bloc ").append(wx).append(',')
                                     .append(wz).append(")  charge=")
                                     .append(b.chunkCharge[cz * s.cw + cx] ? "oui" : "NON").append('\n');
                            }
                        }
                    }
                }
            }
            r.append("  ").append(a.nom).append(" -> ").append(b.nom).append(" : ")
             .append(totalTouches).append(" chunk(s) modifie(s) ; dans l'emprise ")
             .append(totalEmprise - sautes).append('/').append(totalEmprise)
             .append(" touches, ").append(sautes).append(" SAUTE(S)\n");
            if (liste.length() > 0) r.append(liste);
        }
        r.append('\n');

        // ---- 3. ancien terrain dans l'emprise
        r.append("----------------------------------------------------------------\n");
        r.append(" 3. ANCIEN TERRAIN RESTE DANS L'EMPRISE\n");
        r.append("----------------------------------------------------------------\n");
        int empriseCols = 0, jamaisEcrites = 0, depassent = 0;
        StringBuilder pires = new StringBuilder();
        int listees = 0;
        for (int z = s.ez0; z <= s.ez1; z++) {
            for (int x = s.ex0; x <= s.ex1; x++) {
                int i = index(s, x, z);
                if (i < 0) continue;
                empriseCols++;
                boolean ecrite = s.templateCols[i] > 0;
                if (!ecrite) jamaisEcrites++;
                short fin = derniere.ySolid[i];
                if (!ecrite && fin != ABSENT && fin != VIDE && fin > s.groundY) {
                    depassent++;
                    if (listees < 40) {
                        pires.append("      x=").append(x).append(" z=").append(z)
                             .append("  sol final Y=").append(fin)
                             .append("  (sol de reference ").append(s.groundY).append(", +")
                             .append(fin - s.groundY).append(")\n");
                        listees++;
                    }
                }
            }
        }
        r.append("  colonnes d'emprise            : ").append(empriseCols).append('\n');
        r.append("  jamais ecrites par le gabarit : ").append(jamaisEcrites)
         .append(pct(jamaisEcrites, empriseCols)).append('\n');
        r.append("  dont sol final AU-DESSUS du sol de reference : ").append(depassent)
         .append(pct(depassent, empriseCols)).append("   <-- ancien relief visible\n");
        if (pires.length() > 0) r.append(pires);
        r.append("\n  Une colonne que le gabarit n'ecrit jamais montre forcement ce qu'il y avait\n");
        r.append("  avant. Le rectangle declare d'un NBT n'est pas sa silhouette.\n\n");

        // ---- 4. failles et marches
        r.append("----------------------------------------------------------------\n");
        r.append(" 4. FAILLES ET MARCHES (etape finale : ").append(derniere.nom).append(")\n");
        r.append("----------------------------------------------------------------\n");
        r.append(marches(s, derniere));
        r.append('\n');

        // ---- 5. profil bord -> exterieur
        r.append("----------------------------------------------------------------\n");
        r.append(" 5. PROFIL DU RACCORD, DU BORD VERS L'EXTERIEUR\n");
        r.append("----------------------------------------------------------------\n");
        r.append(profil(s, premiere, derniere));
        r.append('\n');

        // ---- 6. vues du dessus
        r.append("----------------------------------------------------------------\n");
        r.append(" 6. VUES DU DESSUS (1 caractere = ").append(pas(s)).append(" bloc(s))\n");
        r.append("----------------------------------------------------------------\n");
        r.append("  Legende hauteur (ecart au sol de reference Y=").append(s.groundY).append(") :\n");
        r.append("    '.' = +/-1   ',' = -3..-2   ':' = -7..-4   ';' = -15..-8   '#' = <-15\n");
        r.append("    '-' = 2..3   '=' = 4..7     '+' = 8..15    '*' = >15\n");
        r.append("    'A' = chunk absent   'V' = colonne vide\n");
        r.append("    '[' ']' = bords gauche/droit de l'emprise   '_' = bords haut/bas\n\n");
        for (Etape e : s.etapes) {
            r.append("  >>> ").append(e.nom).append('\n');
            r.append(vue(s, e));
            r.append('\n');
        }
        r.append("  >>> CHARGEMENT DES CHUNKS (1 caractere = 1 chunk)\n");
        r.append("      dans l'emprise : 'O' = charge,  'X' = NON CHARGE  <-- a examiner\n");
        r.append("      dehors         : 'o' = charge,  '.' = non charge (attendu)\n");
        for (Etape e : s.etapes) {
            r.append("      --- ").append(e.nom).append('\n');
            r.append(vueChunks(s, e));
        }
        r.append('\n');
        r.append("  >>> GABARIT : colonnes ecrites par le NBT ('#' = ecrite, ' ' = jamais)\n");
        r.append(vueTemplate(s));
        r.append('\n');
        for (int n = 1; n < s.etapes.size(); n++) {
            r.append("  >>> CHANGEMENTS ").append(s.etapes.get(n - 1).nom).append(" -> ")
             .append(s.etapes.get(n).nom)
             .append("  ('#' = sol modifie, ' ' = intact, 'A' = absent)\n");
            r.append(vueDiff(s, s.etapes.get(n - 1), s.etapes.get(n)));
            r.append('\n');
        }

        r.append("----------------------------------------------------------------\n");
        r.append(" FICHIERS JOINTS\n");
        r.append("----------------------------------------------------------------\n");
        r.append("  palette.txt            index -> identifiant de bloc\n");
        r.append("  chunks.csv             chargement et colonnes changees, par chunk et par etape\n");
        r.append("  template_colonnes.txt  nombre de blocs du gabarit par colonne\n");
        r.append("  etapes/<n>_<etape>_*   grilles pleine resolution, une ligne par Z :\n");
        r.append("                           ySolid   sommet solide (ecart au sol de reference)\n");
        r.append("                           ySurface sommet reel, eau et feuillage compris\n");
        r.append("                           yWater   surface d'eau\n");
        r.append("                           idSolid / idSurface  indices de palette\n");
        r.append("                           airSous  blocs d'air piges sous le sommet solide\n");
        return r;
    }

    private static String pct(int a, int b) {
        if (b <= 0) return "";
        return String.format(" (%.1f %%)", 100.0 * a / b);
    }

    private static CharSequence marches(Session s, Etape e) {
        StringBuilder sb = new StringBuilder(8192);
        int[] histo = new int[9];
        int pireVal = 0, pireX = 0, pireZ = 0;
        List<int[]> top = new ArrayList<>();
        for (int z = 0; z < s.d; z++) {
            for (int x = 0; x < s.w; x++) {
                int i = z * s.w + x;
                short h = e.ySolid[i];
                if (h == ABSENT || h == VIDE) continue;
                int pire = 0;
                if (x + 1 < s.w) pire = Math.max(pire, delta(e, i, i + 1));
                if (z + 1 < s.d) pire = Math.max(pire, delta(e, i, i + s.w));
                histo[tranche(pire)]++;
                if (pire >= 6) top.add(new int[]{pire, s.x0 + x, s.z0 + z});
                if (pire > pireVal) {
                    pireVal = pire;
                    pireX = s.x0 + x;
                    pireZ = s.z0 + z;
                }
            }
        }
        String[] noms = {"0", "1", "2-3", "4-5", "6-9", "10-15", "16-25", "26-50", ">50"};
        sb.append("  Denivele maximal avec le voisin est/sud, par colonne :\n");
        for (int i = 0; i < histo.length; i++) {
            sb.append(String.format("    %-7s %8d%n", noms[i], histo[i]));
        }
        sb.append("  marche la plus haute : ").append(pireVal)
          .append(" blocs, en x=").append(pireX).append(" z=").append(pireZ).append('\n');
        top.sort((a, b) -> b[0] - a[0]);
        int n = Math.min(40, top.size());
        if (n > 0) sb.append("  les ").append(n).append(" plus hautes marches (>= 6 blocs) :\n");
        for (int i = 0; i < n; i++) {
            int[] t = top.get(i);
            sb.append("      ").append(t[0]).append(" blocs  x=").append(t[1])
              .append(" z=").append(t[2]).append('\n');
        }
        sb.append("  Total de colonnes a marche >= 6 : ").append(top.size()).append('\n');
        return sb;
    }

    private static int delta(Etape e, int i, int j) {
        short a = e.ySolid[i], b = e.ySolid[j];
        if (a == ABSENT || a == VIDE || b == ABSENT || b == VIDE) return 0;
        return Math.abs(a - b);
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

    /**
     * Hauteur moyenne par distance au bord de l'emprise, avant et apres. C'est la coupe qui
     * dit si le raccord descend en pente ou tombe d'un coup — et jusqu'ou il porte.
     */
    private static CharSequence profil(Session s, Etape avant, Etape apres) {
        int rayon = 48;
        double[] sAvant = new double[2 * rayon + 1];
        double[] sApres = new double[2 * rayon + 1];
        int[] n = new int[2 * rayon + 1];
        for (int z = 0; z < s.d; z++) {
            for (int x = 0; x < s.w; x++) {
                int wx = s.x0 + x, wz = s.z0 + z;
                int dBord = distanceAuBord(s, wx, wz);
                if (dBord < -rayon || dBord > rayon) continue;
                int i = z * s.w + x;
                short a = avant.ySolid[i], b = apres.ySolid[i];
                if (a == ABSENT || a == VIDE || b == ABSENT || b == VIDE) continue;
                int k = dBord + rayon;
                sAvant[k] += a;
                sApres[k] += b;
                n[k]++;
            }
        }
        StringBuilder sb = new StringBuilder(4096);
        sb.append("  distance au bord : negatif = dans l'emprise, positif = dehors.\n");
        sb.append(String.format("    %8s %10s %10s %10s %9s%n",
                "dist", "avant", "apres", "delta", "colonnes"));
        for (int k = 0; k < n.length; k++) {
            if (n[k] == 0) continue;
            double a = sAvant[k] / n[k], b = sApres[k] / n[k];
            sb.append(String.format("    %8d %10.2f %10.2f %10.2f %9d%n",
                    k - rayon, a, b, b - a, n[k]));
        }
        return sb;
    }

    private static int distanceAuBord(Session s, int x, int z) {
        int dx = Math.max(s.ex0 - x, x - s.ex1);
        int dz = Math.max(s.ez0 - z, z - s.ez1);
        int d = Math.max(dx, dz);
        if (x >= s.ex0 && x <= s.ex1 && z >= s.ez0 && z <= s.ez1) {
            int in = Math.min(Math.min(x - s.ex0, s.ex1 - x), Math.min(z - s.ez0, s.ez1 - z));
            return -in;
        }
        return d;
    }

    /**
     * Facteur de reduction des vues du dessus. Les grilles pleine resolution vivent dans les
     * fichiers joints ; le rapport, lui, doit rester lisible d'un seul coup d'oeil et tenir
     * dans une fenetre de terminal.
     */
    private static int pas(Session s) {
        int p = 1;
        while (s.w / p > 160) p++;
        return p;
    }

    private static CharSequence vue(Session s, Etape e) {
        int p = pas(s);
        StringBuilder sb = new StringBuilder((s.w / p + 8) * (s.d / p + 2));
        for (int z = 0; z < s.d; z += p) {
            sb.append("  ");
            for (int x = 0; x < s.w; x += p) {
                int i = z * s.w + x;
                sb.append(carHauteur(s, e.ySolid[i], s.x0 + x, s.z0 + z, p));
            }
            sb.append('\n');
        }
        return sb;
    }

    private static char carHauteur(Session s, short h, int wx, int wz, int p) {
        if (h == ABSENT) return 'A';
        if (h == VIDE) return 'V';
        // Le contour de l'emprise doit rester visible APRES sous-echantillonnage. Tester
        // l'egalite exacte le ferait disparaitre des que le bord ne tombe pas pile sur une
        // colonne echantillonnee : avec un pas de 3, le bord droit s'evaporait purement et
        // simplement. On teste donc si le bord traverse la FENETRE que ce caractere resume.
        boolean dansX = wx + p > s.ex0 && wx <= s.ex1;
        boolean dansZ = wz + p > s.ez0 && wz <= s.ez1;
        if (dansZ && wx <= s.ex0 && wx + p > s.ex0) return '[';
        if (dansZ && wx <= s.ex1 && wx + p > s.ex1) return ']';
        if (dansX && ((wz <= s.ez0 && wz + p > s.ez0) || (wz <= s.ez1 && wz + p > s.ez1))) return '_';
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

    private static CharSequence vueChunks(Session s, Etape e) {
        StringBuilder sb = new StringBuilder();
        for (int cz = 0; cz < s.cd; cz++) {
            sb.append("      ");
            for (int cx = 0; cx < s.cw; cx++) {
                int wx = (s.cx0 + cx) << 4, wz = (s.cz0 + cz) << 4;
                boolean dans = wx + 15 >= s.ex0 && wx <= s.ex1 && wz + 15 >= s.ez0 && wz <= s.ez1;
                boolean ch = e.chunkCharge[cz * s.cw + cx];
                sb.append(dans ? (ch ? 'O' : 'X') : (ch ? 'o' : '.'));
            }
            sb.append('\n');
        }
        return sb;
    }

    private static CharSequence vueTemplate(Session s) {
        int p = pas(s);
        StringBuilder sb = new StringBuilder();
        for (int z = 0; z < s.d; z += p) {
            sb.append("  ");
            for (int x = 0; x < s.w; x += p) {
                sb.append(s.templateCols[z * s.w + x] > 0 ? '#' : ' ');
            }
            sb.append('\n');
        }
        return sb;
    }

    private static CharSequence vueDiff(Session s, Etape a, Etape b) {
        int p = pas(s);
        StringBuilder sb = new StringBuilder();
        for (int z = 0; z < s.d; z += p) {
            sb.append("  ");
            for (int x = 0; x < s.w; x += p) {
                int i = z * s.w + x;
                if (a.ySolid[i] == ABSENT || b.ySolid[i] == ABSENT) sb.append('A');
                else sb.append(a.ySolid[i] != b.ySolid[i] || a.ySurface[i] != b.ySurface[i] ? '#' : ' ');
            }
            sb.append('\n');
        }
        return sb;
    }
}
