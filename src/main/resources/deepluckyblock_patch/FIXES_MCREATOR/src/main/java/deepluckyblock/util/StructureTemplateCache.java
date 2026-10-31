package deepluckyblock.util;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FIX (rapporté en jeu : freeze serveur d'environ 78 secondes / "Can't keep up
 * ... Running 78183ms or 1563 ticks behind" au premier spawn de shipdead) :
 *
 * StructureTemplateManager.get()/.getOrCreate() ne conserve AUCUN cache entre
 * deux structures différentes tant qu'elles n'ont pas déjà été demandées une
 * première fois. La toute première fois qu'une structure du mod (ex :
 * shipdead.nbt, 4.9 Mo, ~1.7M entrées de blocs) est réclamée en jeu, le
 * serveur doit désérialiser tout le NBT de façon SYNCHRONE sur le thread
 * principal, AVANT même le calcul de la zone plate ou le placement des blocs
 * (qui, eux, sont bien répartis sur plusieurs ticks via PASTE_QUEUE). Pour un
 * fichier de plusieurs Mo, ce chargement à froid peut prendre plusieurs
 * dizaines de secondes pendant lesquelles AUCUN tick serveur ne peut avancer.
 *
 * FIX en deux parties :
 *  1) StructureTemplateCache.get(...) : un cache dédié au mod (persistant
 *     tant que le serveur tourne) partagé par toutes les procédures qui
 *     posent des structures (Structures4Procedure, Structures5Procedure,
 *     CrimsonLakeStructureSpawnProcedure, GenerateBadProcedure/hourglass) ;
 *     une structure n'est donc plus jamais désérialisée deux fois.
 *  2) StructureTemplateCache.preloadAll(...) : appelé une seule fois depuis
 *     DeepLuckyBlockMod#onServerStarted, AVANT que les joueurs ne soient
 *     connectés / ne tickent. Le coût de désérialisation de TOUTES les
 *     structures du mod (y compris shipdead et citadel, les deux plus
 *     grosses) est ainsi payé une fois pendant le démarrage du serveur --
 *     où un délai n'est jamais perçu comme un freeze en jeu -- au lieu
 *     d'être payé en une fois pendant une partie déjà en cours, quand un
 *     joueur ouvre un lucky block et se retrouve avec un serveur figé
 *     78 secondes ("Can't keep up").
 */
public final class StructureTemplateCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, StructureTemplate> CACHE = new ConcurrentHashMap<>();
    /**
     * T156 : NBT déjà lu/décompressé/filtré au lancement du jeu. Un
     * StructureTemplate final exige les registres du serveur et ne peut donc
     * être construit avant l'ouverture d'une map, mais tout le travail disque
     * lourd est payé avant l'écran des mondes.
     */
    private static final Map<String, CompoundTag> GAME_LAUNCH_TAGS = new ConcurrentHashMap<>();
    private static final Map<String, NbtAirFilter.Result> GAME_LAUNCH_FILTERS = new ConcurrentHashMap<>();
    private static final Set<String> GAME_LAUNCH_LOADING = ConcurrentHashMap.newKeySet();

    /** Toutes les structures NBT du mod, à précharger au démarrage serveur. */
    public static final List<String> ALL_STRUCTURE_NAMES = List.of(
            "circus", "shipdead", "everest", "citadel", "observatory",
            "dragon", "crimsonlake", "sand_hourglass"
    );

    private StructureTemplateCache() {}

    /**
     * Retourne le template demandé, en le servant depuis le cache du mod si
     * déjà chargé, sinon en le chargeant (via le StructureTemplateManager
     * vanilla) et en le mettant en cache pour tous les appels futurs (toutes
     * procédures confondues). Retourne null en cas d'échec ou de template
     * vide (taille X == 0), comme le faisait le code appelant auparavant.
     */
    public static StructureTemplate get(ServerLevel level, String nbtName) {
        StructureTemplate cached = CACHE.get(nbtName);
        if (cached != null) return cached;

        // =================================================================
        // T54 : LE PARSE NBT DE SECOURS NE BLOQUE PLUS LE THREAD SERVEUR
        // =================================================================
        // AVANT : quand le template n'etait pas encore en memoire, on tombait
        // dans `mgr.get()/getOrCreate()`, c'est-a-dire une deserialisation
        // SYNCHRONE en plein tick serveur. Mesures : ~1,3 s pour crimsonlake,
        // jusqu'a 60 s de watchdog pour citadel (76 Mo decompresses). Un appel
        // `getBlockState` ou une generation de chunk n'ont pas le monopole du
        // gel : celui-ci etait du meme ordre.
        // MAINTENANT : le fichier n'est parse sur le thread serveur que s'il est
        // PETIT (seuil ci-dessous, les decors et les petites structures : quelques
        // dizaines de ms, comme avant). Au-dela, la deserialisation part en tache
        // de fond ({@link #requestAsync}) et la methode rend null : tous les
        // appelants savent deja differer (ils testent isCached/attendent ou
        // abandonnent proprement l'evenement), donc la structure apparait au
        // tick suivant au lieu de figer le jeu.
        // `-Ddlb.templateSync=1` restaure l'ancien comportement (diagnostic).
        long sizeKb = fileSizeKb(level, nbtName);
        boolean big = sizeKb > TEMPLATE_SYNC_MAX_KB;
        if (big && !SYNC_PARSE_ALWAYS) {
            LOGGER.warn("[STRUCT-CACHE] '{}' ({} Ko) pas encore en memoire : chargement lance en tache de fond, "
                            + "aucune deserialisation synchrone (le jeu ne se fige pas ; nouvel essai au tick suivant)",
                    nbtName, sizeKb);
            requestAsync(level, nbtName);
            return null;
        }
        ResourceLocation resLoc = ResourceLocation.tryParse("deep_lucky_block:" + nbtName);
        if (resLoc == null) return null;
        StructureTemplateManager mgr = level.getStructureManager();
        StructureTemplate template;
        long t0 = System.currentTimeMillis();
        try {
            Optional<StructureTemplate> opt = mgr.get(resLoc);
            template = opt.isPresent() ? opt.get() : mgr.getOrCreate(resLoc);
        } catch (Exception ex) {
            LOGGER.error("[STRUCT-CACHE] Échec chargement '{}'", nbtName, ex);
            return null;
        }
        if (template == null || template.getSize().getX() == 0) return null;
        long ms = System.currentTimeMillis() - t0;
        if (ms > 150) {
            LOGGER.warn("[STRUCT-CACHE] '{}' deserialise sur le thread serveur en {} ms ({} Ko) -- "
                            + "structure normalement pre-chargee ; voir preloadAll()/tickWarmup()", nbtName, ms, sizeKb);
        }
        CACHE.put(nbtName, template);
        return template;
    }

    /**
     * T54 : au-dela de ce seuil (Ko de NBT compresse), plus aucune deserialisation sur le
     * thread serveur : chargement en tache de fond. Les decors et petites structures (en
     * dessous) restent charges immediatement, comme avant.
     */
    private static final long TEMPLATE_SYNC_MAX_KB = Long.getLong("dlb.templateSyncMaxKb", 256L);
    /** T54 : -Ddlb.templateSync=1 -> ancien comportement (parse synchrone systematique). */
    private static final boolean SYNC_PARSE_ALWAYS = Boolean.getBoolean("dlb.templateSync");

    /** Taille du fichier NBT (Ko), sans le lire entierement. -1 si inconnue. */
    private static long fileSizeKb(ServerLevel level, String nbtName) {
        try {
            ResourceLocation file = ResourceLocation.tryParse("deep_lucky_block:structure/" + nbtName + ".nbt");
            if (file == null) return -1L;
            Optional<Resource> res = level.getServer().getResourceManager().getResource(file);
            if (res.isEmpty()) return -1L;
            try (InputStream in = res.get().open()) {
                long n = in.available();
                return n > 0 ? n / 1024L : -1L;
            }
        } catch (Throwable t) {
            return -1L;   // taille inconnue : on reste prudent (traite comme "petit")
        }
    }

    /** Structures dont le chargement de fond a deja ete demande. */
    private static final Set<String> WARMING = ConcurrentHashMap.newKeySet();
    /** Structures dont le chargement de fond a echoue (on repasse en synchrone). */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    /**
     * FIX T7 (citadel 76 Mo decompresses, "A single server tick took 60.29
     * seconds") : le parse d'un gros NBT ne se fait plus JAMAIS sur le thread
     * serveur quand on peut l'eviter.
     *
     * Le simple fait d'appeler {@code StructureTemplateManager.get()} sur
     * citadel / shipdead / dragon / crimsonlake bloque le thread serveur le
     * temps de decompresser et de deserialiser plusieurs dizaines de Mo de NBT
     * (mesure : 1,5 s pour citadel sur une machine a 16 Go ; sur une machine
     * plus juste ou avec moins de RAM libre, le meme parse depasse la seconde
     * par trentaine et le watchdog tue la partie). Pendant ce temps AUCUN tick
     * serveur n'avance et rien n'apparait dans les logs.
     *
     * {@link #requestAsync} fait ce meme travail sur un thread de fond unique
     * (jamais deux parses en parallele, pour ne pas doubler la pression memoire),
     * ce qui permet a {@code Structures5Procedure} d'attendre que le template
     * soit pret AU LIEU de bloquer le serveur : la generation de la structure
     * est simplement mise en attente quelques ticks et demarre toute seule des
     * que le NBT est charge (aucun message d'erreur, aucune action du joueur).
     *
     * Le parse utilise l'API publique vanilla {@code readStructure(CompoundTag)}
     * -- exactement ce que fait le chargement synchrone, DataFixer compris --
     * et n'ecrit jamais dans la map interne du StructureTemplateManager
     * (aucun etat vanilla n'est touche depuis ce thread).
     */
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DLB-StructureLoader");
        t.setDaemon(true);
        // T75 : MIN_PRIORITY -> NORM_PRIORITY. Mesure en jeu (23/09) : 'dragon'
        // chargeait en 43 863 ms sur la machine utilisateur contre 2 859 ms en bac a
        // sable, parce que ce thread se faisait voler son temps CPU par la generation
        // de chunks -- alors qu'il est sur le CHEMIN CRITIQUE (le joueur attend son
        // NBT : « toujours en cours de chargement (10 s / 20 s / 30 s / 40 s) »).
        // Il reste un thread de fond daemon : il ne bloque jamais le serveur.
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });

    /** Vrai si la structure est deja en cache (chargement instantane). */
    public static boolean isCached(String nbtName) {
        return nbtName != null && CACHE.containsKey(nbtName);
    }

    /** Vrai si un chargement de fond est en cours pour cette structure. */
    public static boolean isPending(String nbtName) {
        return nbtName != null && WARMING.contains(nbtName);
    }

    /** Vrai si le chargement de fond a echoue (l'appelant doit repasser en synchrone). */
    public static boolean hasFailed(String nbtName) {
        return nbtName != null && FAILED.contains(nbtName);
    }

    /** Autorise une nouvelle tentative apres un echec de chargement de fond. */
    public static void clearFailed(String nbtName) {
        if (nbtName != null) FAILED.remove(nbtName);
    }

    /**
     * T156 : lance au chargement du mod, donc au lancement du jeu et non à
     * l'ouverture d'une map, la partie coûteuse indépendante d'un ServerLevel.
     */
    public static void preloadCompressedAtGameLaunch() {
        int queued = 0;
        for (String name : ALL_STRUCTURE_NAMES) {
            if (!GAME_LAUNCH_LOADING.add(name) || GAME_LAUNCH_TAGS.containsKey(name)) continue;
            queued++;
            LOADER.submit(() -> {
                long t0 = System.currentTimeMillis();
                String path = "/data/deep_lucky_block/structure/" + name + ".nbt";
                try (InputStream in = StructureTemplateCache.class.getResourceAsStream(path)) {
                    if (in == null) throw new java.io.FileNotFoundException(path);
                    // T157 : -1 désactivait le filtre en flux et conservait les
                    // millions d'airs jusqu'à l'ouverture de la map. available()
                    // donne ici la taille du NBT compressé embarqué, comme le
                    // Resource serveur utilisé par l'ancien chemin T73.
                    long compressedBytes = Math.max(0L, in.available());
                    NbtAirFilter.Result pre = NbtAirFilter.filter(in, name, compressedBytes);
                    CompoundTag tag;
                    if (pre != null && pre.tag != null) {
                        tag = pre.tag;
                    } else {
                        try (InputStream fallback = StructureTemplateCache.class.getResourceAsStream(path)) {
                            if (fallback == null) throw new java.io.FileNotFoundException(path);
                            tag = NbtIo.readCompressed(fallback, NbtAccounter.unlimitedHeap());
                        }
                    }
                    StructureTemplate ready = materializeAtGameLaunch(name, tag, pre);
                    if (ready != null) {
                        CACHE.put(name, ready);
                        deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' TEMPLATE COMPLET chargé AU LANCEMENT DU JEU en {}ms; prêt pour toutes les maps",
                                name, System.currentTimeMillis() - t0);
                    } else {
                        GAME_LAUNCH_TAGS.put(name, tag);
                        if (pre != null) GAME_LAUNCH_FILTERS.put(name, pre);
                        LOGGER.warn("[STRUCT-CACHE] '{}' NBT préparé au lancement en {}ms; résolution registres reportée",
                                name, System.currentTimeMillis() - t0);
                    }
                } catch (Throwable ex) {
                    LOGGER.error("[STRUCT-CACHE] préchargement jeu impossible pour '{}' : {}", name, ex.toString());
                } finally {
                    GAME_LAUNCH_LOADING.remove(name);
                }
            });
        }
        deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-T156] lancement du jeu : {}/{} NBT lourds mis en file AVANT toute ouverture de map",
                queued, ALL_STRUCTURE_NAMES.size());
    }

    /** Construit aussi le StructureTemplate avant toute map avec les registres intégrés. */
    private static StructureTemplate materializeAtGameLaunch(String name, CompoundTag tag,
                                                               NbtAirFilter.Result pre) {
        try {
            StructureTemplate t = new StructureTemplate();
            Object lookup = net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
            boolean loaded = false;
            for (java.lang.reflect.Method m : StructureTemplate.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && CompoundTag.class.isAssignableFrom(p[1])
                        && p[0].isInstance(lookup)) {
                    m.setAccessible(true);
                    m.invoke(t, lookup, tag);
                    loaded = true;
                    break;
                }
                if (p.length == 1 && CompoundTag.class.isAssignableFrom(p[0])) {
                    m.setAccessible(true);
                    m.invoke(t, tag);
                    loaded = true;
                    break;
                }
            }
            if (!loaded || t.getSize().getX() == 0) return null;
            return compact(name, t, pre);
        } catch (Throwable ex) {
            LOGGER.warn("[STRUCT-CACHE] matérialisation jeu de '{}' indisponible : {}", name, ex.toString());
            return null;
        }
    }

    /**
     * Demarre (ou rejoint) le chargement de la structure sur le thread de fond.
     * Retourne immediatement : ne bloque jamais le thread appelant.
     */
    public static void requestAsync(final ServerLevel level, final String nbtName) {
        if (nbtName == null || level == null) return;
        if (CACHE.containsKey(nbtName)) return;
        if (hasFailed(nbtName)) return;          // deja tente et echoue : on ne boucle pas
        if (!WARMING.add(nbtName)) return;       // deja en cours
        LOADER.submit(() -> {
            long t0 = System.currentTimeMillis();
            try {
                StructureTemplate t = loadFromDisk(level, nbtName);
                if (t != null) {
                    CACHE.put(nbtName, t);
                    deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' charge en tache de fond en {}ms (le serveur n'a jamais ete bloque)",
                            nbtName, System.currentTimeMillis() - t0);
                } else {
                    FAILED.add(nbtName);
                    LOGGER.error("[STRUCT-CACHE] '{}' introuvable ou vide (tache de fond, {}ms)", nbtName, System.currentTimeMillis() - t0);
                }
            } catch (Throwable ex) {
                FAILED.add(nbtName);
                LOGGER.error("[STRUCT-CACHE] Echec du chargement en tache de fond de '{}' ({}ms) : {}",
                        nbtName, System.currentTimeMillis() - t0, ex.toString(), ex);
            } finally {
                WARMING.remove(nbtName);
            }
        });
    }

    // =====================================================================
    // T73 : COMPACTAGE DE LA PALETTE (l'air n'est plus un "bloc" a poser)
    // =====================================================================
    /** Listes de blocs compactees : blocs reels + air INTERIEUR seulement. */
    private static final java.util.Map<String, java.util.List<StructureTemplate.StructureBlockInfo>> COMPACT =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Positions d'air INTERIEUR (encodees) du template, calculees une fois. */
    private static final java.util.Map<String, java.util.Set<Long>> INT_AIR =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Liste compactee d'un template (null si le template n'est pas encore charge). */
    public static java.util.List<StructureTemplate.StructureBlockInfo> compactBlocks(String nbtName) {
        return nbtName == null ? null : COMPACT.get(nbtName);
    }

    /** Positions d'air interieur (memes codes que le filtre de pose). */
    public static java.util.Set<Long> interiorAir(String nbtName) {
        if (nbtName == null) return null;
        java.util.Set<Long> s = INT_AIR.get(nbtName);
        return s == null ? null : java.util.Set.copyOf(s);
    }

    /**
     * T73 : prepare les structures de donnees du template une seule fois.
     *
     * <p>1) compte l'air rejete et garde une liste compactee ; 2) calcule l'air
     * INTERIEUR (flood fill depuis les faces) ; 3) allege le template vanilla si
     * c'est possible, pour que les millions d'entrees d'air ne restent pas en
     * memoire pendant toute la session.
     */
    private static StructureTemplate compact(String nbtName, StructureTemplate t, NbtAirFilter.Result pre) {
        try {
            // T73 : si le filtre en flux a deja nettoye le tag, la liste de blocs ne
            // contient QUE les vrais blocs + l'air interieur deja calcule : rien a
            // recompter, rien a recalculer, le template vanilla est deja celui qui
            // doit etre pose.
            if (pre != null) {
                java.util.List<StructureTemplate.StructureBlockInfo> deja =
                        paletteBlocks(t);
                if (deja != null && !deja.isEmpty()) {
                    COMPACT.put(nbtName, deja);
                    INT_AIR.put(nbtName, pre.interior);
                    long gain = Math.max(0L, pre.total - deja.size());
                    deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' : {} entree(s) au fichier -> {} bloc(s) a poser "
                                    + "+ {} air interieur ({} air exterieur JAMAIS materialise(s) = {} %, "
                                    + "lecture en flux {} ms, {}x{}x{}) -- T73",
                            nbtName, pre.total, pre.realBlocks, pre.airInterior, pre.airExterior,
                            pre.total == 0 ? 0 : (100 * pre.airExterior / pre.total),
                            pre.millis, pre.sx, pre.sy, pre.sz);
                    // T73b : preuve de coherence des coordonnees lues (une erreur de
                    // decodage faisait exploser la zone d'edition : 274 648 blocs de
                    // large au lieu de 104 pour citadel, et la pose partait en vrille).
                    deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' : positions lues X {}..{} Y {}..{} Z {}..{} "
                                    + "(boite {}x{}x{}, {} hors boite) -- T73b",
                            nbtName, pre.minX, pre.maxX, pre.minY, pre.maxY, pre.minZ, pre.maxZ,
                            pre.sx, pre.sy, pre.sz, pre.outOfBox);
                    if (gain > 0) {
                        // ordre de grandeur : ~200 o d'objets NBT par entree d'air evitee
                        deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' : ~{} Mo de memoires NBT evitees, "
                                        + "et {} entree(s) en moins sur le chemin de pose -- T73",
                                nbtName, gain * 200L / 1048576L, gain);
                    }
                }
                return t;
            }
            java.util.List<StructureTemplate.StructureBlockInfo> all = paletteBlocks(t);
            if (all == null || all.isEmpty()) return t;
            Vec3i size = t.getSize();
            int total = all.size();

            // 1) air INTERIEUR (celui qui sert a creuser l'interieur de la structure)
            java.util.Set<Long> interior = interiorAirOf(all, size);
            INT_AIR.put(nbtName, interior);

            // 2) liste compactee : blocs reels + air interieur, rien d'autre
            java.util.List<StructureTemplate.StructureBlockInfo> compact = new java.util.ArrayList<>(total);
            int airExt = 0, realBlocks = 0, airInt = 0;
            for (StructureTemplate.StructureBlockInfo info : all) {
                if (!info.state().isAir()) { compact.add(info); realBlocks++; continue; }
                net.minecraft.core.BlockPos p = info.pos();
                if (interior.contains(p.asLong())) { compact.add(info); airInt++; }
                else airExt++;
            }
            COMPACT.put(nbtName, compact);

            // 3) allege le template vanilla (memoire de la session)
            int freed = shrinkVanilla(t, compact);

            deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' : {} entree(s) -> {} VRAI bloc(s) + {} air interieur "
                            + "({} air exterieur ignore(s) = {} %, ~{} Mo de memoire evites{}) -- T73",
                    nbtName, total, realBlocks, airInt, airExt,
                    total == 0 ? 0 : (100 * airExt / total),
                    String.format("%.0f", airExt * 80.0 / 1048576.0),
                    freed > 0 ? ", template vanilla allege" : "");
        } catch (Throwable ex) {
            LOGGER.warn("[STRUCT-CACHE] compactage de '{}' impossible ({}) -- liste vanilla conservee",
                    nbtName, ex.toString());
        }
        return t;
    }

    /** Bloc de blocs d'une palette, par reflexion (meme lecture que les procedures). */
    private static java.util.List<StructureTemplate.StructureBlockInfo> paletteBlocks(StructureTemplate t) {
        try {
            for (java.lang.reflect.Field f : StructureTemplate.class.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(t);
                if (val instanceof java.util.List<?> list && !list.isEmpty()
                        && list.get(0) instanceof StructureTemplate.Palette pal) {
                    return pal.blocks();
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /**
     * T73 : tente d'alleger le template vanilla en remplacant sa liste de blocs
     * par la liste compactee. Trois strategies (la premiere qui marche gagne) ;
     * en cas d'echec, le template garde ses entrees mais la pose utilise de toute
     * facon la liste compactee.
     */
    private static int shrinkVanilla(StructureTemplate t,
                                     java.util.List<StructureTemplate.StructureBlockInfo> compact) {
        try {
            for (java.lang.reflect.Field f : StructureTemplate.class.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(t);
                if (!(val instanceof java.util.List<?> list) || list.isEmpty()
                        || !(list.get(0) instanceof StructureTemplate.Palette pal)) continue;
                java.util.List<StructureTemplate.StructureBlockInfo> src = pal.blocks();
                if (src.size() <= compact.size()) return 0;      // rien a gagner
                // strategie 1 : la liste est-elle mutable ?
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<StructureTemplate.StructureBlockInfo> mut =
                            (java.util.List<StructureTemplate.StructureBlockInfo>) src;
                    mut.clear();
                    mut.addAll(compact);
                    return src.size();
                } catch (UnsupportedOperationException notMutable) { }
                // strategie 2 : remplacer le champ prive 'blocks' de la Palette
                try {
                    for (java.lang.reflect.Field pf : StructureTemplate.Palette.class.getDeclaredFields()) {
                        if (pf.getName().equals("blocks") && pf.getType() == java.util.List.class) {
                            pf.setAccessible(true);
                            pf.set(pal, compact);
                            return compact.size();
                        }
                    }
                } catch (Throwable ignored) { }
                // strategie 3 : remplacer le champ 'palettes' du template
                try {
                    java.util.List<StructureTemplate.Palette> np = new java.util.ArrayList<>(1);
                    var ctor = StructureTemplate.Palette.class.getDeclaredConstructor(java.util.List.class);
                    ctor.setAccessible(true);
                    np.add(ctor.newInstance(compact));
                    f.set(t, np);
                    return compact.size();
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /**
     * T73 : air INTERIEUR du template (celui qui n'est relie a aucune face).
     * Meme algorithme que le filtre de pose, mais calcule UNE fois au chargement
     * (thread de fond) au lieu de chaque pose (thread serveur).
     */
    private static java.util.Set<Long> interiorAirOf(java.util.List<StructureTemplate.StructureBlockInfo> blocks,
                                                     Vec3i size) {
        int sx = size.getX(), sy = size.getY(), sz = size.getZ();
        if (sx <= 0 || sy <= 0 || sz <= 0) return java.util.Set.of();
        if ((long) sx * sy * sz > 40_000_000L) return java.util.Set.of();   // garde-fou memoire
        boolean[][][] solid = new boolean[sx][sy][sz];
        for (StructureTemplate.StructureBlockInfo i : blocks) {
            net.minecraft.core.BlockPos p = i.pos();
            int x = p.getX(), y = p.getY(), z = p.getZ();
            if (x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz) solid[x][y][z] = !i.state().isAir();
        }
        boolean[][][] ext = new boolean[sx][sy][sz];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            if ((x == 0 || x == sx - 1 || y == 0 || y == sy - 1 || z == 0 || z == sz - 1)
                    && !solid[x][y][z]) { ext[x][y][z] = true; q.add(new int[]{x, y, z}); }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                if (nx < 0 || nx >= sx || ny < 0 || ny >= sy || nz < 0 || nz >= sz) continue;
                if (ext[nx][ny][nz] || solid[nx][ny][nz]) continue;
                ext[nx][ny][nz] = true; q.add(new int[]{nx, ny, nz});
            }
        }
        java.util.Set<Long> r = new java.util.HashSet<>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            if (!solid[x][y][z] && !ext[x][y][z]) r.add(net.minecraft.core.BlockPos.asLong(x, y, z));
        }
        return r;
    }

    /**
     * Lecture + deserialisation hors thread serveur. Reproduit exactement le
     * chemin vanilla : meme fichier (namespace:structure/&lt;nom&gt;.nbt), meme
     * methode de parse (readStructure, DataFixer applique), aucune ecriture
     * dans les structures de donnees vanilla.
     */
    private static StructureTemplate loadFromDisk(ServerLevel level, String nbtName) throws Exception {
        ResourceLocation file = ResourceLocation.tryParse("deep_lucky_block:structure/" + nbtName + ".nbt");
        if (file == null) return null;
        Optional<Resource> res = level.getServer().getResourceManager().getResource(file);
        StructureTemplateManager mgr = level.getStructureManager();
        // T156 : priorité au NBT déjà préparé au lancement DU JEU. Il est retiré
        // de la map intermédiaire dès conversion pour ne pas doubler la mémoire.
        CompoundTag tag = GAME_LAUNCH_TAGS.remove(nbtName);
        NbtAirFilter.Result pre = GAME_LAUNCH_FILTERS.remove(nbtName);
        if (tag != null) {
            deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' utilise le NBT préparé au lancement du jeu (aucune relecture à l'ouverture de map)", nbtName);
        } else {
            if (res.isEmpty()) return null;
            // Secours dédié/serveur : même pipeline si aucun lancement client n'a précédé.
            try (InputStream in = res.get().open()) {
                pre = NbtAirFilter.filter(in, nbtName, fileSizeKb(level, nbtName) * 1024L);
            } catch (Throwable ignored) {
            }
            if (pre == null && NbtAirFilter.paletteHasNoAir(nbtName)) {
                deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] '{}' : palette SANS aucun air -- structure deja creuse, "
                        + "lecture vanilla (rien a supprimer, aucune entree d'air en memoire) -- T73c",
                        nbtName);
            }
            if (pre != null && pre.tag != null) {
                tag = pre.tag;
            } else {
                try (InputStream in2 = res.get().open()) {
                    tag = NbtIo.readCompressed(in2, NbtAccounter.unlimitedHeap());
                }
            }
        }
        StructureTemplate t = mgr.readStructure(tag);
        if (pre != null) pre.tag = null;    // le tag intermediaire est liberable tout de suite
        tag = null;
        if (t == null || t.getSize().getX() == 0) return null;
        return compact(nbtName, t, pre);
    }

    /**
     * Rechauffe le cache EN TACHE DE FOND, une structure a la fois, en lissant
     * la charge sur plusieurs ticks.
     *
     * Complement du passage au chargement paresseux : sans lui, la premiere
     * apparition d'une grosse structure paierait d'un coup son parse (jusqu'a
     * ~1.5 s pour citadel) sur le thread serveur, en plein jeu.
     *
     * Le rechauffage est volontairement TRES etale : une structure toutes les
     * {@code WARM_INTERVAL_TICKS} (30 s), et uniquement si elle n'est pas deja
     * en cache. Sur une partie normale, les structures sont donc chargees bien
     * avant d'etre demandees, sans que le joueur ne percoive quoi que ce soit,
     * et une partie courte ne paie jamais le chargement de structures qu'elle
     * n'aurait de toute facon jamais rencontrees.
     *
     * A appeler depuis un tick serveur ; la methode se charge elle-meme de ne
     * rien faire la plupart du temps.
     */
    private static int warmIndex = 0;

    /**
     * DESACTIVE (rapporte en jeu : « je pense que ton systeme de prechargement
     * est inutile et fait plus laguer qu'autre chose »).
     *
     * Le rechauffage de fond chargeait une grosse structure toutes les 30 s.
     * Chaque chargement bloque le thread serveur le temps du parse -- le log
     * le montre noir sur blanc : « circus charge en 12187ms » suivi
     * immediatement de « Can't keep up! 12274ms behind », puis « citadel en
     * 26976ms » -> « 27020ms behind ». Ces pics tombaient au hasard, y compris
     * pendant une generation de structure, et aggravaient le retard accumule.
     *
     * Le chargement paresseux suffit : une structure est parsee a sa premiere
     * apparition, une seule fois, au moment ou le joueur s'attend de toute
     * facon a un temps de generation.
     */
    public static void tickWarmup(ServerLevel level) {
        // T131 : DESACTIVE REELLEMENT. Les tests T129/T130 montrent que ce
        // "fond" partage en pratique disque, CPU et GC avec le pipeline actif.
        // Une structure non demandee ne doit jamais concurrencer le chemin
        // critique de moins de 20 s. Le chargement a la demande reste asynchrone
        // et les 153 petits decors restent precharges par preloadAll().
        return;
        /*
        // =================================================================
        // T75 : RECHARFFAGE DE FOND REACTIVE (et non plus desactive)
        // =================================================================
        // La version d'origine chargeait une structure toutes les 30 s SUR LE THREAD
        // SERVEUR : d'ou « circus charge en 12187ms » + « 12274ms behind ». Celle-ci
        // ne fait RIEN de bloquant : elle DEMANDE le chargement asynchrone (thread
        // DLB-StructureLoader, priorite normale) et rend la main immediatement.
        //
        // Pourquoi c'est necessaire : sur la machine de l'utilisateur le parse de
        // 'dragon' a pris 43 863 ms. Tant qu'il se declenche au moment ou le joueur
        // casse le lucky block, ces 44 s sont du temps d'attente POUR RIEN (le joueur
        // voit « toujours en cours de chargement (40 s) »). Declenche au demarrage du
        // monde, ce meme parse se paie pendant que le joueur marche/regarde autour.
        //
        // GARDE-FOUS (la demande « ne pas precharger toutes les structures » reste
        // respectee -- on n'immobilise ni le monde ni une minute au demarrage) :
        //   - une seule structure a la fois, toutes les WARM_INTERVAL_TICKS (10 s) ;
        //   - uniquement si la memoire disponible le permet (jamais au prix d'un OOM) ;
        //   - uniquement les structures reellement utilisees par le mod.
        if (level == null) return;
        if (warmIndex >= ALL_STRUCTURE_NAMES.size()) return;
        long now = level.getGameTime();   // T75 : ticks du monde (getTickCount n'existe pas sur ServerLevel)
        if (now - warmLastTick < WARM_INTERVAL_TICKS) return;
        warmLastTick = now;
        // Memoire : on exige une marge confortable (le parse d'une grosse structure
        // compactee occupe quelques dizaines de Mo le temps du chargement).
        Runtime rt = Runtime.getRuntime();
        long headroomMo = (rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())) / (1024L * 1024L);
        if (headroomMo < WARM_MIN_HEADROOM_MO) {
            deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] rechauffage de fond suspendu : {} Mo de marge disponible seulement "
                    + "(minimum {}) -- les structures se chargeront a la demande (T75)",
                    headroomMo, WARM_MIN_HEADROOM_MO);
            return;
        }
        String nbt = ALL_STRUCTURE_NAMES.get(warmIndex++);
        if (CACHE.containsKey(nbt) || WARMING.contains(nbt) || FAILED.contains(nbt)) return;
        deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] rechauffage de fond '{}' demande au demarrage ({} Mo de marge, T75)",
                nbt, headroomMo);
        requestAsync(level, nbt);
        */
    }

    /** T75 : une structure rechauffee toutes les 10 s apres le demarrage du monde. */
    private static final int WARM_INTERVAL_TICKS = 200;
    /** T75 : marge memoire minimale (Mo) pour lancer un rechauffage. */
    private static final long WARM_MIN_HEADROOM_MO = 350;
    private static long warmLastTick = -100000L;


    /**
     * Précharge toutes les structures connues du mod. À appeler une seule
     * fois, sur le thread principal, avant que des joueurs ne soient
     * connectés (typiquement depuis ServerStartedEvent).
     */
    public static void preloadAll(ServerLevel level) {
        long t0 = System.currentTimeMillis();
        int ok = 0;

        // FIX (rapporte en jeu : « tu peux pas precharger toutes les structures
        // a la creation d'une map, ca fait vraiment perdre un temps fou, en
        // moyenne plusieurs minutes ! »).
        //
        // MESURE du cout reel (parse NbtIo chronometre sur les vrais fichiers) :
        //
        //   citadel      72.8 Mo decompresses -> 1471 ms
        //   shipdead     59.9 Mo              -> 1571 ms
        //   crimsonlake  47.0 Mo              -> 1303 ms
        //   dragon       27.8 Mo              ->  728 ms
        //   circus       25.1 Mo              ->  773 ms
        //   observatory   9.2 Mo              ->  243 ms
        //   everest       1.8 Mo              ->   48 ms
        //   ----------------------------------------------
        //   8 GROSSES    245 Mo               -> ~6.1 s de parse PUR
        //   153 DECO     0.2 Mo               -> ~40 ms
        //
        // Les 6 secondes de parse ne sont que la partie visible : les 245 Mo
        // d'objets NBT alloues d'un coup declenchent des cycles GC majeurs et,
        // sur une machine a RAM limitee ou a disque lent, du swap. C'est ce qui
        // transforme 6 s de calcul en plusieurs MINUTES d'attente ressentie.
        // (Verifie ici meme : le sandbox, avec 1.9 Go, n'arrive pas a charger
        // les 8 grosses structures de suite sans s'etrangler.)
        //
        // CORRECTIF : on ne precharge QUE les 153 petits schematics de decor.
        //   - Ils coutent 40 ms au total, soit 0.6 % du cout d'avant.
        //   - C'est le seul cas ou le prechargement est justifie : ils sont
        //     poses en RAFALE (5 a 9 d'affilee) juste apres un paste, donc les
        //     charger a la volee provoquerait une salve d'I/O au pire moment.
        //
        // Les 8 grosses structures passent en chargement PARESSEUX : get() les
        // chargera a la premiere utilisation et les gardera en cache. Le cout
        // n'est pas supprime, il est DEPLACE la ou il est acceptable :
        //   - il ne frappe plus la creation de map (toutes les structures
        //     payees d'avance, y compris celles jamais rencontrees) ;
        //   - il est paye une seule fois, uniquement pour la structure qui
        //     apparait reellement, et au moment ou le joueur casse un lucky
        //     block -- un instant ou un temps de chargement bref est attendu.
        // Une seule structure = ~1.5 s au pire au lieu de 6 s + GC pour huit.
        for (String name : deepluckyblock.procedures.StructureScatterDecor.ALL_DECO_NAMES) {
            StructureTemplate t = get(level, name);
            if (t != null) ok++;
        }

        long dt = System.currentTimeMillis() - t0;
        long ready = ALL_STRUCTURE_NAMES.stream().filter(CACHE::containsKey).count();
        deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT-CACHE] Préchargement des décors : {}/{} en {}ms. "
                        + "Grosses structures déjà entièrement matérialisées au lancement : {}/{}.",
                ok, deepluckyblock.procedures.StructureScatterDecor.ALL_DECO_NAMES.size(),
                dt, ready, ALL_STRUCTURE_NAMES.size());
    }
}
