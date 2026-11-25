package deepluckyblock.procedures;

import deepluckyblock.advancements.DeepLuckyBlockAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import deepluckyblock.entity.CrimsonButterflyEntity;
import deepluckyblock.init.DeepLuckyBlockModEntities;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block")
public class CrimsonLakeStructureSpawnProcedure {

    // ACTIVE/DESACTIVE les logs de debug [SLB-DEBUG]. Mettre a false avant publication.
    private static final boolean DEBUG_LOGS = false;

    // T303-PERF : l'ancien plafond fixe 25 000/tick imposait un plancher incompressible de 55 ticks
    // (2,75 s d'attente vide) sur 1 359 489 blocs. Borne en temps reel par PASTE_TICK_BUDGET_MS (40 ms).
    private static final int BLOCKS_PER_TICK        = 400000;
    /**
     * T48 : budget de temps de la pose par tick (la structure du lac fait
     * plusieurs millions de blocs : laisser la boucle tourner jusqu'a
     * BLOCKS_PER_TICK sans regarder l'horloge tenait le tick trop longtemps).
     */
    private static final long PASTE_TICK_BUDGET_MS  = 40L;
    private static final int FAST_FLAG              = 2 | 16 | 32;
    private static final java.util.EnumSet<net.minecraft.world.level.levelgen.Heightmap.Types> LAKE_HEIGHTMAPS =
            java.util.EnumSet.of(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE);
    /**
     * Enfoncement du lac dans le terrain naturel (blocs).
     *
     * <p>28/09 : 10 -> 20 (consigne Dev en jeu : « mets crimsonlake encore 10 blocs
     * enfonces dans le sol deja existant »). N'agit que sur lakeOrigin() : tout le lac
     * descend de 10 blocs supplementaires SOUS le terrain naturel ; le volume degage
     * (clearBuildVolume) suit automatiquement puisque son depart est
     * origin.offset(0, minRelativeY, 0). Les logs [DLB-LAKE] (sink=...) le confirment.</p>
     */
    private static final int SINK_BLOCKS = 20;
    private static final long BUILD_DELAY_TICKS     = 400;
    private static final long BUILD_START_DELAY_TICKS = 200;
    private static final int BUTTERFLY_DELAY_TICKS  = 600;
    private static final int BUTTERFLY_INTERVAL     = 40;
    // T110 (consigne dev 30/09 : « triple le nombre de papillons ») : le
    // plafond periodique passe de 150-400 a 450-1200 (x3), et les papillons
    // d'annonce de 20 a 60 (x3). Les packs (3-6 par intervalle) sont inchanges,
    // ils mettent juste ~3x plus de temps a atteindre le plafond.
    private static final int BUTTERFLY_MIN          = 450;
    private static final int BUTTERFLY_MAX          = 1200;
    private static final int INITIAL_BUTTERFLIES_MIN = 20;
    private static final int INITIAL_BUTTERFLIES_MAX = 77;
    // T303-PERF : 120 s (2400 ticks) strictement conserve en jeu (T157), pilotable uniquement pour le banc de test (-Ddlb.lake.omenTicks).
    private static final int PRE_DISCOVERY_TICKS = Math.max(1, Integer.getInteger("dlb.lake.omenTicks", 120 * 20));

    // === T234 : JAMAIS DE BLOCAGE INFINI (ticket utilisateur 11/10 : « ca charge
    //    infini !! toujours pas apparu ?? crimsonlake ? ») ======================
    //    Reconstitution du freeze d'1 h 30 en log : « loading surrounding chunks
    //    and building layer by layer... » a 22:54:09/22:54:10 (2 relances de
    //    queueBuild), quelques chunks effaces (warnings DUMMY a 22:54:52), puis
    //    SILENCE TOTAL : le clear pas-a-pas attendait un chunk qui ne venait
    //    jamais ; le seul garde-fou existant (ChunkKeeper, 600 s depuis le
    //    PREMIER track) a relache la zone a 23:04:10, apres quoi TOUTES les
    //    attentes internes continuaient de se replanifier chaque tick pour
    //    toujours, sans plus aucun message.
    //    Cause racine : AUCUNE attente du pipeline lac n'avait de borne dure
    //    (sonde zoneLoaded replanifiee a l'infini ; chunk absent du clear
    //    replanifie a l'infini ; blocs differes de la pose attendus a l'infini).
    //    Correctif : chaque attente a une DEADLINE dure + progression annoncee
    //    (chat + console) ; depassee, la construction est ABANDONNEE PROPREMENT
    //    (zone relachee, etat nettoye, lac RETIRABLE a nouveau) -- jamais de
    //    silence infini, jamais de pose a l'aveugle sur chunks manquants.
    private static final long LAKE_PROBE_DEADLINE_MS =
            Long.getLong("dlb.lake.probeDeadlineMs", 90_000L);
    private static final long LAKE_CLEAR_CHUNK_WAIT_MS =
            Long.getLong("dlb.lake.clearChunkWaitMs", 75_000L);
    private static final long LAKE_PASTE_DEFER_MS =
            Long.getLong("dlb.lake.pasteDeferMs", 180_000L);
    // Borne globale de la construction : volontairement INFERIEURE au garde-fou
    // ChunkKeeper (600 s depuis le 1er track) pour liberer la zone soi-meme
    // proprement AVANT que le garde-fou ne le fasse a sa place.
    private static final long LAKE_TOTAL_DEADLINE_MS =
            Long.getLong("dlb.lake.totalDeadlineMs", 540_000L);
    /** Debut de la construction courante (0 = aucune). Pose au 1er queueBuild. */
    private static long lakeBuildT0Ms = 0L;
    /** Derniere annonce de progression (anti-spam chat/console). */
    private static long lakeLastAnnounceMs = 0L;
    /** T234 : le chat « building layer by layer » ne part qu'une fois par build. */
    private static boolean lakeProbeAnnounced = false;
    // T234 : la liste filtree (1 359 489 blocs, ~1 s de re-filtrage mesure en jeu
    // sur le PC de l'utilisateur entre deux relances de queueBuild : messages a
    // 22:54:09 puis 22:54:10) est REUTILISEE entre les tentatives tant que le
    // centre ne bouge pas -- sans quoi chaque relance de borne+delai coutait
    // 1 s de thread serveur a pleine frequence.
    private static java.util.List<StructureTemplate.StructureBlockInfo> lakeFiltCache = null;
    private static long lakeFiltCacheKey = Long.MIN_VALUE;
    private static int lakeFiltCacheMinRelY = 0;

    /**
     * T234 : abandon PROPRE et annonce d'une construction de lac. Idempotent.
     * Ne pose RIEN de plus, relache la zone tenue, nettoie les files, rend le
     * lac de nouveau tirable (comme T228 pour Structures5) et le dit au joueur.
     */
    private static void abortLakeBuild(ServerLevel level, String reason) {
        long waited = lakeBuildT0Ms > 0 ? (System.currentTimeMillis() - lakeBuildT0Ms) : -1;
        deepluckyblock.util.DebugLog.err("[DLB-LAKE] ABORT T234 : " + reason
                + (waited >= 0 ? " (construction abandonnee apres " + waited / 1000 + " s)" : "")
                + " -- aucun blocage infini, terrain/clear non termine laisse en l'etat, le lac redevient tirable");
        lakeBuildT0Ms = 0L;
        lakeFiltCache = null;
        lakeProbeAnnounced = false;
        lakeOceanRelocs = 0;      // T465
        lakeWaterMode = false;    // T465
        // Une pose en file pour le meme niveau ne doit pas demarrer apres abandon.
        PASTE_Q.removeIf(j -> j.level == level);
        deepluckyblock.util.ChunkKeeper.release(level);
        Structures5Procedure.notifyGenerationAborted(level);
        // Retirable a nouveau dans ce monde (le drapeau « deja apparu » etait
        // pose au DECLENCHEMENT, ligne markWorldTriggered) -- parite T228.
        try {
            ServerLevel ow = level.getServer().overworld();
            if (!ow.isDebug()) stateOf(ow).clear();
        } catch (Throwable ignored) { }
        Component msg = Component.literal(
                "Crimson Lake: construction ABORTED (" + reason + "). "
                        + "The world is intact and the lake can be discovered again.")
                .withStyle(ChatFormatting.RED);
        for (var pl : level.players()) pl.sendSystemMessage(msg);
    }

    /** T234 : annonce de progression bornee (max 1 message toutes les 20 s).
     *  Console TOUJOURS (c'est le ticket : l'utilisateur passait 1 h 30 sans
     *  aucun signe de vie) ; chat joueur borne au meme rythme. */
    private static void announceLakeProgress(ServerLevel level, String text) {
        long now = System.currentTimeMillis();
        if (now - lakeLastAnnounceMs < 20_000L) return;
        lakeLastAnnounceMs = now;
        deepluckyblock.util.DebugLog.out("[DLB-LAKE] progression : " + text);
        Component msg = Component.literal("Crimson Lake: " + text).withStyle(ChatFormatting.GRAY);
        for (var pl : level.players()) pl.sendSystemMessage(msg);
    }

    @SuppressWarnings("removal")
    private static final ResourceLocation TEMPLATE_ID  = ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "crimsonlake");

    /**
     * T50 : NBT reellement utilise. Par defaut « crimsonlake ».
     *
     * <p>La propriete systeme {@code -Ddlb.test.lakeTemplate=<nbt>} permet de
     * rejouer TOUT le pipeline du lac (pre-chauffage, prepZone, ordre T39,
     * blocs differes, finitions) avec une petite structure : indispensable dans
     * un environnement de test ou le lac (3,46 Mo, ~5,9 M blocs) ne tient pas en
     * memoire. Le comportement de production est inchange.
     */
    private static final String NBT_NAME = System.getProperty("dlb.test.lakeTemplate", "crimsonlake");

    private static final Set<Long> TRIGGERED = ConcurrentHashMap.newKeySet();
    private static final List<PendingBuild> PENDING = new ArrayList<>();
    private static final Map<Long, SpawnTask> ACTIVE = new ConcurrentHashMap<>();
    private static final Queue<PasteJob> PASTE_Q = new ArrayDeque<>();
    private static final Random RNG = new Random();

    private static volatile MinecraftServer theServer;
    private static volatile long loginTick = Long.MIN_VALUE;
    private static int tickCounter = 0;

    private static boolean buildAllowed() {
        if (theServer == null || loginTick == Long.MIN_VALUE) return false;
        return theServer.getTickCount() - loginTick >= BUILD_DELAY_TICKS;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("crimsonlake")
                        .executes(ctx -> { handleCommand(ctx.getSource().getPlayerOrException()); return 1; })
                        .then(Commands.literal("reset")
                                .executes(ctx -> { handleReset(ctx.getSource().getPlayerOrException()); return 1; }))
        );
    }

    /**
     * T50 : rejoue exactement l'evenement (meme anneau, meme position, meme
     * pipeline) pour la commande de test {@code /dlbtest crimsonlake}. Le lac
     * n'etait declenchable que par l'evenement aleatoire (1/500 par minute) ou
     * par /crimsonlake en jeu : impossible a tester en console, et impossible a
     * rejouer rapidement apres une correction.
     */
    public static boolean triggerFromTest(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return false;
        try {
            // theServer n'est renseigne que par la connexion d'un joueur : sans
            // ca, buildAllowed() resterait faux et le tick handler ne ferait
            // rien du tout (le pipeline ne demarrerait jamais en console).
            theServer = level.getServer();
            loginTick = theServer.getTickCount() - BUILD_DELAY_TICKS;
            stateOf(theServer.overworld()).clear();
            TRIGGERED.clear();
            PENDING.clear();
        } catch (Throwable ignored) { }
        triggerAt(level, player, positionInFront(level, player), "dlbtest crimsonlake");
        return true;
    }

    private static void handleCommand(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        triggerAt(level, player, positionInFront(level, player), "commande /crimsonlake");
    }

    private static void handleReset(ServerPlayer player) {
        ServerLevel overworld = player.getServer().overworld();
        try {
            stateOf(overworld).clear();
            TRIGGERED.clear();
            PENDING.clear();
            player.sendSystemMessage(Component.literal("Crimson Lake state reset -> can trigger again (testing).")
                    .withStyle(ChatFormatting.YELLOW));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Reset failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private static BlockPos positionInFront(ServerLevel level, ServerPlayer player) {
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double yaw = Math.toRadians(player.getYRot());
        double fdx = -Math.sin(yaw), fdz = Math.cos(yaw);
        int sx = 243, sz = 292;
        // FIX T7 : cet appel ne doit JAMAIS declencher le parse du NBT de
        // crimsonlake (3,46 Mo -> ~1,3 s de freeze) : il ne sert qu'a estimer la
        // taille du template pour positionner le centre. Tant que le fichier
        // n'est pas en memoire, on utilise les dimensions par defaut (mesurees
        // une fois pour toutes, elles ne dependent pas du monde).
        if (deepluckyblock.util.StructureTemplateCache.isCached(NBT_NAME)) {
            var tmpl = deepluckyblock.util.StructureTemplateCache.get(level, NBT_NAME);
            if (tmpl != null) { sx = tmpl.getSize().getX(); sz = tmpl.getSize().getZ(); }
        } else {
            deepluckyblock.util.StructureTemplateCache.requestAsync(level, NBT_NAME);
        }
        double halfDepth = Math.abs(fdx) * (sx / 2.0) + Math.abs(fdz) * (sz / 2.0);
        // T305-DIST2X : distance X et Z par rapport au joueur doublee !
        int ox = (int) Math.round(px + fdx * (halfDepth * 2.0));
        int oz = (int) Math.round(pz + fdz * (halfDepth * 2.0));
        return new BlockPos(ox, (int) py, oz);
    }

    private static void triggerAt(ServerLevel level, ServerPlayer player, BlockPos pos, String source) {
        ServerLevel overworld = level.getServer().overworld();
        if (isWorldTriggered(overworld)) return;
        long key = pos.asLong();
        if (!TRIGGERED.add(key)) return;
        markWorldTriggered(overworld);
        if (DEBUG_LOGS) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] CrimsonLake TRIGGER from " + source);

        // 🏆 Achievement : le Crimson Lake (événement ultra rare) a été trouvé.
        // T157 : aucun message, avancement ni construction avant le présage complet.
        // 20..77 papillons apparaissent irrégulièrement pendant exactement 120 s,
        // alternativement près du joueur et dans la future emprise du lac.
        int initialCount = INITIAL_BUTTERFLIES_MIN
                + RNG.nextInt(INITIAL_BUTTERFLIES_MAX - INITIAL_BUTTERFLIES_MIN + 1);
        int serverTick = level.getServer().getTickCount();
        for (int b = 0; b < initialCount; b++) {
            final boolean nearPlayer = (b & 1) == 0;
            final BlockPos plannedCenter = pos;
            final int tickOffset = (int) (((long) (b + 1) * PRE_DISCOVERY_TICKS) / initialCount);
            TestProcedure.schedule(level, serverTick + tickOffset, () -> {
                // Le joueur peut se déplacer pendant les 120 s : le trail suit sa
                // position réelle. Les apparitions dans la future zone restent prévues.
                BlockPos spawnCenter = nearPlayer && !player.isRemoved()
                        ? player.blockPosition() : plannedCenter;
                spawnInitialButterflies(level, spawnCenter, 1);
            });
        }
        TestProcedure.schedule(level, serverTick + PRE_DISCOVERY_TICKS, () -> {
            // T239 : un avancement absent (pack non charge, arbre a 57
            // entrees) ne doit PLUS avaler le message ni la suite -- log +
            // continuité quoi qu'il arrive. (Cause la plus probable de la
            // disparition silencieuse du 11/10.)
            if (player != null)
                try { DeepLuckyBlockAdvancements.grantCrimsonLake(player); }
                catch (Throwable grantErr) {
                    deepluckyblock.util.DebugLog.err("[DLB-LAKE] avancement crimsonlake NON accorde (le build continue) : " + grantErr);
                }
            Component msg = Component.literal(
                    "Congratulations! You found the secret event: Crimson Lake. Please step back! Construction is starting.")
                    .withStyle(ChatFormatting.GOLD);
            for (var p : level.players()) p.sendSystemMessage(msg);
            deepluckyblock.util.DebugLog.err("[DLB-LAKE] presage de 120 s termine : la construction debute (PENDING="
                    + PENDING.size() + ", NBT cache=" + deepluckyblock.util.StructureTemplateCache.isCached(NBT_NAME) + ")");
        });
        // T239 : WATCHDOG -- 10 s apres la fin censée du presage, si rien n'a
        // démarre, le dire noir sur blanc avec l'etat complet (PENDING, cache
        // NBT, echec de chargement). Toute disparition future sera auto-diagnostiquee.
        TestProcedure.schedule(level, serverTick + PRE_DISCOVERY_TICKS + 200, () -> {
            if (!PENDING.isEmpty()) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE WATCHDOG] construction toujours en attente 130 s apres le trigger : "
                        + "PENDING=" + PENDING.size()
                        + " NBT cache=" + deepluckyblock.util.StructureTemplateCache.isCached(NBT_NAME)
                        + " NBT echec=" + deepluckyblock.util.StructureTemplateCache.hasFailed(NBT_NAME)
                        + " buildT0ms=" + lakeBuildT0Ms
                        + " -- la cause du blocage est dans cette ligne.");
            } else if (PASTE_Q.isEmpty() && lakeBuildT0Ms == 0L) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE WATCHDOG] 130 s apres le trigger, le pipeline n'a JAMAIS demarre "
                        + "(PENDING vide, PASTE_Q vide, sans horloge de build) -- suicide avant queueBuild ; "
                        + "remonter les lignes [DLB-LAKE] ci-dessus.");
            }
        });

        long startTick = level.getServer().getTickCount() + PRE_DISCOVERY_TICKS;
        PENDING.add(new PendingBuild(level, pos.getX(), pos.getY(), pos.getZ(), startTick));
        // T239 : VISIBILITE PERMANENTE (System.err = jamais masque par dlb.debug).
        // Le 11/10 une generation demandee par /dlbtest pendant une autre a
        // disparu en silence total ; desormais chaque etape du lac laisse une
        // trace, jusqu'au watchdog de fin de presage.
        deepluckyblock.util.DebugLog.err("[DLB-LAKE] TRIGGER (" + source + ") : centre prevu "
                + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " -- presage de 120 s demarre (tick serveur " + (startTick - PRE_DISCOVERY_TICKS) + ")");
        // === T46 : PRE-CHAUFFAGE DES CHUNKS PENDANT LES 60 s D'ANNONCE ===
        // Mesure en jeu du 23/09 : le pre-chargement du lac (406x444 = 783 chunks,
        // monde neuf) a tourne 24 minutes et n'a jamais fini -- lancé APRES les
        // 60 s d'annonce, il les passait a attendre des chunks que personne ne
        // demandait encore. On demande maintenant la zone DES L'ANNONCE : les
        // chunks vierges se generent en parallele pendant que le joueur recule,
        // et le pipeline demarre sur une zone deja prete.
        {
            int wsx = 243, wsz = 292;   // dimensions mesurees du lac (NBT 3,46 Mo)
            try {
                if (deepluckyblock.util.StructureTemplateCache.isCached(NBT_NAME)) {
                    var t = deepluckyblock.util.StructureTemplateCache.get(level, NBT_NAME);
                    if (t != null) { wsx = t.getSize().getX(); wsz = t.getSize().getZ(); }
                }
            } catch (Throwable ignored) { }
            BlockPos wMin = new BlockPos(pos.getX() - wsx / 2, pos.getY(), pos.getZ() - wsz / 2);
            BlockPos wMax = wMin.offset(wsx - 1, 1, wsz - 1);
            StructureTerrainPrep.warmZone(level, wMin, wMax, 80);
        }
        // FIX T7 : les 60 s d'annonce servent de fenetre de chargement pour le
        // NBT (3,46 Mo, ~1,3 s de parse) : il est pret bien avant la pose, donc
        // la construction ne bloque plus le thread serveur.
        deepluckyblock.util.StructureTemplateCache.requestAsync(level, NBT_NAME);

    }

    private static int diceTick;
    // ⛔ T227 — recalibrage demande : « Crimson Lake [...] doit etre calibree pour
    //    se declencher en moyenne une fois par partie de 3 h ».
    //    Un de est lance tous les 1200 ticks = 60 s reelles.
    //    3 h = 10 800 s = 180 lancers -> il faut p = 1/180 par lancer.
    //    ANCIENNE valeur : 500 faces -> 1 declenchement toutes les 500 min = 8 h 20.
    //    NOUVELLE valeur : 180 faces -> 1 declenchement toutes les 180 min = 3 h 00.
    //    (DICE_HIT reste 99, qui doit rester STRICTEMENT inferieur a DICE_SIDES.)
    private static final int DICE_SIDES = 180;
    private static final int DICE_HIT = 99;

    private static void rollDice() {
        if (theServer == null) return;
        ServerLevel ow = theServer.overworld();
        if (ow == null || isWorldTriggered(ow)) return;
        int roll = RNG.nextInt(DICE_SIDES);
        if (roll != DICE_HIT) return;
        // T313 : Crimson Lake est un evenement de surface Overworld uniquement --
        // ne jamais le declencher sur le toit bedrock du Nether ou dans le vide de l'End.
        for (ServerPlayer p : ow.players()) {
            triggerAt(ow, p, positionInFront(ow, p), "dice " + DICE_HIT + "/" + DICE_SIDES);
            return;
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        theServer = event.getEntity().getServer();
        loginTick = theServer != null ? theServer.getTickCount() : Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        loginTick = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!buildAllowed()) return;

        if (diceTick++ % 1200 == 0 && PASTE_Q.isEmpty() && PENDING.isEmpty()) {
            rollDice();
        }

        if (PASTE_Q.isEmpty() && PENDING.isEmpty() && ACTIVE.isEmpty()) return;

        tickCounter++;
        if (DEBUG_LOGS && (tickCounter % 200 == 0 || !PASTE_Q.isEmpty()))
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] CrimsonLake TICK: PASTE=" + PASTE_Q.size() + " PENDING=" + PENDING.size() + " ACTIVE=" + ACTIVE.size());

        long now = theServer.getTickCount();

        if (!PENDING.isEmpty()) {
            long minQueueNanos = Math.min(10_000_000_000L, (long) PRE_DISCOVERY_TICKS * 50_000_000L);
            Iterator<PendingBuild> it = PENDING.iterator();
            while (it.hasNext()) {
                PendingBuild pb = it.next();
                if (now < pb.startTick || System.nanoTime() - pb.queuedNanos < minQueueNanos) continue;
                // FIX T7 : le NBT de crimsonlake ne doit JAMAIS etre parse sur le
                // thread serveur (3,46 Mo -> ~1,3 s de freeze a la premiere
                // apparition). Tant qu'il n'est pas en memoire, la construction
                // reste en attente : on a lance le chargement de fond et on
                // repasse dans 20 ticks. Aucun bloc de structure n'est perdu.
                if (!deepluckyblock.util.StructureTemplateCache.isCached(NBT_NAME)
                        && !deepluckyblock.util.StructureTemplateCache.hasFailed(NBT_NAME)) {
                    deepluckyblock.util.StructureTemplateCache.requestAsync(pb.level, NBT_NAME);
                    pb.startTick = now + 20;
                    if (pb.startTick % 400L < 20L)
                        // T239 : visible sans dlb.debug -- etape decisive du pipeline.
                        deepluckyblock.util.DebugLog.err("[DLB-LAKE] NBT crimsonlake charge en tache de fond, construction reportee (cache="
                                + deepluckyblock.util.StructureTemplateCache.isCached(NBT_NAME) + ", serveur non bloque)");
                    continue;
                }
                queueBuild(pb.level, pb.px, pb.py, pb.pz);
                it.remove();
            }
        }

        if (!PASTE_Q.isEmpty()) {
            PasteJob j = PASTE_Q.peek();
            // T48 : la zone reste TENUE pendant la pose (aucune generation synchrone).
            deepluckyblock.util.ChunkKeeper.keep(j.level);
            StructurePlaceSettings rs = new StructurePlaceSettings();
            BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
            long pasteT0 = System.currentTimeMillis();
            if (j.idx < j.blocks.size()) {
                int end = Math.min(j.idx + BLOCKS_PER_TICK, j.blocks.size());
                for (int i = j.idx; i < end; i++) {
                    if ((i & 255) == 0 && System.currentTimeMillis() - pasteT0 > PASTE_TICK_BUDGET_MS) { end = i; break; }
                    // T48 : chunk absent -> DIFFERE (demande async), jamais de
                    // generation synchrone sur le thread serveur.
                    if (!placeCrimsonOne(j, i, rs, bp)) j.defer.add(i);
                }
                j.idx = end;
                flushLakeChunk(j);
            } else if (!j.defer.isEmpty()) {
                // Passe de rattrapage : les blocs differes (chunk pas encore en
                // memoire) sont reposes ici, sans jamais bloquer.
                java.util.Iterator<Integer> it = j.defer.iterator();
                while (it.hasNext()) {
                    if (System.currentTimeMillis() - pasteT0 > PASTE_TICK_BUDGET_MS) break;
                    if (placeCrimsonOne(j, it.next(), rs, bp)) it.remove();
                }
                flushLakeChunk(j);
            }
            if (j.idx >= j.blocks.size() && !j.defer.isEmpty()) {
                j.waitTicks++;
                // T234 : attente BORNEE des chunks qui ne viennent jamais. Avant :
                // retour silencieux a l'infini (meme constat que le clear). A
                // l'echeance, les quelques blocs restants sont abandonnes
                // explicitement (comptes et annonces) et la pose SE TERMINE :
                // un lac a 1 359 489 blocs moins une poignee >> un blocage infini.
                if (j.deferSinceMs == 0L) j.deferSinceMs = System.currentTimeMillis();
                long waiting = System.currentTimeMillis() - j.deferSinceMs;
                if (waiting > LAKE_PASTE_DEFER_MS) {
                    deepluckyblock.util.DebugLog.err("[DLB-LAKE] T234 : " + j.defer.size() + " bloc(s) differes abandonnes apres "
                            + waiting / 1000 + " s (chunk definitidement indisponible) -- la pose se termine quand meme");
                    announceLakeProgress(j.level, j.defer.size() + " blocks at unloaded terrain edges were skipped; finishing.");
                    j.defer.clear();
                } else {
                    if (j.waitTicks % 400 == 0) {
                        announceLakeProgress(j.level, "finishing " + j.defer.size()
                                + " blocks on loading chunks (" + waiting / 1000 + "s / "
                                + LAKE_PASTE_DEFER_MS / 1000 + "s)...");
                    }
                    return;
                }
            } else if (j.defer.isEmpty()) {
                j.deferSinceMs = 0L;
            }
            // T234 : borne globale de construction, meme pendant la pose. Couper
            // AVANT le garde-fou ChunkKeeper (600 s), jamais apres.
            if (lakeBuildT0Ms > 0 && System.currentTimeMillis() - lakeBuildT0Ms > LAKE_TOTAL_DEADLINE_MS
                    && !(j.idx >= j.blocks.size() && j.defer.isEmpty())) {
                abortLakeBuild(j.level, "pose toujours en cours a la deadline globale ("
                        + (System.currentTimeMillis() - lakeBuildT0Ms) / 1000 + " s)");
                return;
            }
            if (j.idx >= j.blocks.size()) {
                PASTE_Q.poll();
                if (DEBUG_LOGS) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] CrimsonLake PASTE COMPLETE");
                // T234 : construction reussie -> reset des bornes/caches de build.
                lakeBuildT0Ms = 0L;
                lakeFiltCache = null;
                lakeProbeAnnounced = false;

                // Passe de finition APRES le paste, comme les autres structures :
                // naturalize (vegetation contre les murs), cleanup de la
                // vegetation flottante, gestion de l'eau rouverte par le paste,
                // decor thematique, puis replant des arbres sauves par prepZone.
                // Sans cela CrimsonLake restait pose sur un terrain nu et non
                // raccorde -- c'est la seconde moitie du passage en v2.
                int jsy = j.blocks.isEmpty() ? 0 : j.sz; // borne Z (sx/sz connus du job)
                BlockPos cMin = j.origin;
                BlockPos cMax = j.origin.offset(j.sx - 1, 0, jsy - 1);
                // T164/Circus brut : le NBT Crimson contient déjà eau, berges,
                // végétation et décors. Ne jamais lancer decorateFinish ici :
                // cette passe générique rescannait/replantait les 504 chunks et
                // représentait à elle seule plus de 100 secondes.
                //
                // T469 : RETOUR A LA BASE GITHUB. Consigne dev du 22/11 23:22,
                // apres le run de 19:21 : « aucun smooth visible », « gros trous
                // toujours la ! reprends de la base originele que tu avais github
                // / et revois ton approche ! ».
                //
                // T459 avait branche ici le raccord T240 (smoothAround) et, avant
                // la pose, smoothRingOnly. Cinq tours de correctifs (T463, T464,
                // T465, T467, T468) n'ont jamais rendu ce raccord correct : a
                // chaque run il a produit soit une tranchee, soit une crevasse,
                // soit l'ancien relief laisse dans l'emprise. La chaine de
                // lissage est donc DEBRANCHEE du lac -- pas rafistolee une
                // sixieme fois. Le lac redevient ce qu'il etait a la base :
                // clear franc a groundY+1, puis paste. Le bord est net et
                // vertical, mais il n'y a plus ni trou ni faille.
                //
                // smoothAround/protectFootprint restent intacts dans
                // StructureTerrainPrep : les autres structures s'en servent.
                // Seul le lac ne les appelle plus.
                lakeWaterMode = false;
                lakeOceanRelocs = 0;

                // T488 -- PASSE DE FINITION. Le lac batit son terrain lui-meme (clear
                // direct + talus) et ne passait donc par AUCUNE des finitions que les
                // autres structures recoivent via decorateFinish. D'ou les reproches du
                // 25/11. On n'appelle PAS decorateFinish en entier : il relance
                // fuseGroundToStructure/enforceGlacisGradient, c'est-a-dire la chaine de
                // lissage debranchee en T469 apres cinq rafistolages rates. On reprend
                // seulement les passes COSMETIQUES, qui ne retouchent aucune altitude :
                //   1. fixwater sur l'emprise + anneau,
                //   2. purge des couvertures volantes (famille entiere, mods compris),
                //   3. balayage des naturels flottants,
                //   4. diffusion aux clients de TOUT ce que le chantier a ecrit en
                //      palette -- sans elle le joueur garde l'ancien terrain affiche.
                final net.minecraft.server.level.ServerLevel flvl = j.level;
                final BlockPos fMin = j.origin;
                final BlockPos fMax = j.origin.offset(j.sx - 1, Math.max(0, j.sy - 1), j.sz - 1);
                final BlockPos aMin = fMin.offset(-TALUS_LARGEUR, 0, -TALUS_LARGEUR);
                final BlockPos aMax = fMax.offset(TALUS_LARGEUR, 0, TALUS_LARGEUR);
                final Runnable finFinition = () -> {
                    try {
                        StructureTerrainPrep.clearFlyingCover(flvl, aMin, aMax);
                    } catch (Throwable t) {
                        deepluckyblock.util.DebugLog.err("[DLB-LAKE] T488 purge des couvertures volantes echouee : " + t);
                    }
                    StructureTerrainPrep.sweepFloatingNaturalPass(flvl, aMin, aMax, () -> {
                        // Diffusion EN DERNIER : tout ce qui precede ecrit en palette.
                        StructureTerrainPrep.broadcastModifiedChunks(flvl);
                        deepluckyblock.util.ChunkKeeper.release(flvl);
                        deepluckyblock.util.DebugLog.out("[DLB-LAKE] T488 finition terminee "
                                + "(fixwater + purge des flottants + diffusion aux clients)");
                    });
                };
                try {
                    StructureTerrainPrep.fixWaterAround(flvl, fMin, fMax, TALUS_LARGEUR, finFinition);
                } catch (Throwable t) {
                    deepluckyblock.util.DebugLog.err("[DLB-LAKE] T488 fixwater echoue : " + t);
                    finFinition.run();
                }

                Structures5Procedure.notifyGenerationFinished(j.level);

                // T111 (constat dev 30/09 : « crimson lake a fini ? je n'ai pas
                // eu le message disant que c'etait apparu avec les coordonnees »)
                // : annonce de DECOUVERTE en fin de pipeline de pose, parite
                // citadel (Structures5Procedure : « ✦ STRUCTURE DISCOVERED ✦ ...
                // Center coordinates »), avec la duree de la pose reelle.
                {
                    int cx = Math.floorDiv(cMin.getX() + cMax.getX(), 2);
                    int cy = Math.floorDiv(cMin.getY() + cMax.getY(), 2);
                    int cz = Math.floorDiv(cMin.getZ() + cMax.getZ(), 2);
                    String dur = String.format(java.util.Locale.ROOT, "%.1f",
                            (System.currentTimeMillis() - j.t0) / 1000.0);
                    Component discovered = Component.literal(
                            "§6§l✦ STRUCTURE DISCOVERED ✦ §r"
                                    + "§e§l" + Structures5Procedure.structureDisplayName(NBT_NAME)
                                    + " §f§lhas spawned! §7Center coordinates: "
                                    + "§b[" + cx + ", " + cy + ", " + cz + "]"
                                    // T462 : la duree de pose ne part plus aux joueurs. C'est une
                                    // mesure de developpement, pas une information de jeu ; elle
                                    // reste dans la trace DebugLog juste en dessous.
                                    + " §6✦");
                    for (var pl : j.level.players()) pl.sendSystemMessage(discovered);
                    // T234 : la fin de construction est toujours visible en
                    // console (tant pis pour le serveur headless/test) --
                    // c'est le seul marqueur certain que le pipeline est FINIT.
                    deepluckyblock.util.DebugLog.out("[DLB-LAKE] STRUCTURE DISCOVERED (T111): center [" + cx + ", " + cy
                            + ", " + cz + "], built in " + dur + "s");
                }

                int cap = BUTTERFLY_MIN + RNG.nextInt(BUTTERFLY_MAX - BUTTERFLY_MIN + 1);
                long bflyStart = j.level.getServer().getTickCount() + BUTTERFLY_DELAY_TICKS;
                ACTIVE.put(System.nanoTime(), new SpawnTask(j.level, j.bfly, bflyStart, cap, j.sx, j.sz));
            }
        }

        if (!ACTIVE.isEmpty()) {
            Iterator<Map.Entry<Long, SpawnTask>> it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                SpawnTask task = it.next().getValue();
                long t = task.level.getServer().getTickCount();
                if (t < task.nextTick) continue;
                if (task.spawned >= task.cap) { it.remove(); continue; }
                spawnPack(task);
                task.nextTick = t + BUTTERFLY_INTERVAL;
                if (task.spawned >= task.cap) it.remove();
            }
        }
    }

    private static void spawnInitialButterflies(ServerLevel level, BlockPos center, int count) {
        for (int i = 0; i < count; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            double dist = 4.0 + RNG.nextDouble() * 16.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            // FIX papillons invisibles (rapport en jeu) : chercher un sol PRES
            // du point de déclenchement (center.getY()), pas la surface absolue
            // du monde. Si le joueur trouve l'event en sous-sol/grotte (ex.
            // y=-60), l'ancien findSurfaceY(level,x,z) scannait depuis le
            // TOIT du monde et renvoyait la vraie surface extérieure (souvent
            // y=70-100+), à 100+ blocs au-dessus/à l'écart du joueur : les
            // papillons apparaissaient bien, mais totalement hors de vue.
            int sy = findSurfaceYNear(level, x, z, center.getY());
            if (sy < 0) sy = center.getY();
            CrimsonButterflyEntity b = new CrimsonButterflyEntity(DeepLuckyBlockModEntities.CRIMSON_BUTTERFLY.get(), level);
            b.moveTo(x + 0.5, sy + 10.0, z + 0.5, RNG.nextFloat() * 360F, 0F);
            level.addFreshEntity(b);
        }
    }

    private static void spawnPack(SpawnTask task) {
        ServerLevel level = task.level;
        int cx = task.center.getX(), cz = task.center.getZ();
        int halfX = task.sx / 2 + 50;
        int halfZ = task.sz / 2 + 50;
        int pack = Math.min(3 + RNG.nextInt(4), task.cap - task.spawned);
        for (int i = 0; i < pack; i++) {
            double ux = RNG.nextDouble() * 2 - 1;
            double bx = Math.signum(ux) * ux * ux;
            double uz = RNG.nextDouble() * 2 - 1;
            double bz = Math.signum(uz) * uz * uz;
            int x = cx + (int) Math.round(bx * halfX);
            int z = cz + (int) Math.round(bz * halfZ);
            // FIX papillons invisibles : idem spawnInitialButterflies, on
            // cherche un sol proche de l'altitude du déclenchement, pas la
            // surface absolue du monde.
            int sy = findSurfaceYNear(level, x, z, task.center.getY());
            if (sy < 0) sy = task.center.getY();
            CrimsonButterflyEntity b = new CrimsonButterflyEntity(DeepLuckyBlockModEntities.CRIMSON_BUTTERFLY.get(), level);
            b.moveTo(x + 0.5, sy + 10.0, z + 0.5, RNG.nextFloat() * 360F, 0F);
            level.addFreshEntity(b);
        }
        task.spawned += pack;
    }

    /**
     * FIX papillons invisibles : cherche le sol le plus proche de refY (position
     * du joueur au moment du déclenchement de l'event), en scannant d'abord
     * VERS LE HAUT puis VERS LE BAS depuis refY, au lieu de toujours partir du
     * plafond du monde (qui renvoie la surface extérieure même si l'event a
     * été déclenché en sous-sol/grotte/mine, faisant apparaître les papillons
     * à 100+ blocs du joueur, donc invisibles).
     */
    private static int findSurfaceYNear(ServerLevel level, int x, int z, int refY) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int range = 40; // suffisant pour sortir d'une grotte/mine sans remonter jusqu'à la surface
        for (int dy = 0; dy <= range; dy++) {
            int yUp = refY + dy;
            if (yUp <= maxY && yUp > minY) {
                BlockState above = level.getBlockState(new BlockPos(x, yUp, z));
                BlockState below = level.getBlockState(new BlockPos(x, yUp - 1, z));
                if (above.isAir() && above.getFluidState().isEmpty()
                        && !below.isAir() && below.getFluidState().isEmpty()) return yUp;
            }
            if (dy == 0) continue;
            int yDown = refY - dy;
            if (yDown <= maxY && yDown > minY) {
                BlockState above = level.getBlockState(new BlockPos(x, yDown, z));
                BlockState below = level.getBlockState(new BlockPos(x, yDown - 1, z));
                if (above.isAir() && above.getFluidState().isEmpty()
                        && !below.isAir() && below.getFluidState().isEmpty()) return yDown;
            }
        }
        // Repli : ancien comportement (surface absolue du monde) si rien de
        // praticable n'a été trouvé près du joueur.
        return findSurfaceY(level, x, z);
    }

    private static int findSurfaceY(ServerLevel level, int x, int z) {
        for (int y = level.getMaxBuildHeight() - 1; y > level.getMinBuildHeight(); y--) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && s.getFluidState().isEmpty()) return y + 1;
        }
        return -1;
    }

    private static void queueBuild(ServerLevel level, int px, int py, int pz) {
        if (DEBUG_LOGS) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] CrimsonLake QUEUE BUILD at " + px + "," + py + "," + pz);
        // T234 : horloge de construction. Premiere entree = debut du build ;
        // toutes les deadlines T234 (global + par phase) s'y referent.
        if (lakeBuildT0Ms == 0L) {
            lakeBuildT0Ms = System.currentTimeMillis();
            lakeLastAnnounceMs = lakeBuildT0Ms;
            lakeProbeAnnounced = false;
            // T465 : etat marin remis a zero au demarrage d'une construction
            // NEUVE seulement. Un relogement repasse par la branche else, donc
            // son compteur survit -- c'est lui qui borne la boucle.
            lakeOceanRelocs = 0;
            lakeWaterMode = false;
        } else {
            // Borne globale : au-dela, meme une simple relance n'a plus lieu
            // d'etre (la zone tenue serait de toute facon coupee par le
            // garde-fou ChunkKeeper a 600 s) : abandon propre, jamais infini.
            long lived = System.currentTimeMillis() - lakeBuildT0Ms;
            if (lived > LAKE_TOTAL_DEADLINE_MS) {
                abortLakeBuild(level, "deadline globale depassee (" + lived / 1000 + " s)");
                return;
            }
        }
        // FIX (freeze serveur au chargement à froid d'un gros NBT) : cache
        // mod-wide partagé (StructureTemplateCache) au lieu d'un appel direct
        // au StructureTemplateManager.
        StructureTemplate tmpl = deepluckyblock.util.StructureTemplateCache.get(level, NBT_NAME);
        if (tmpl == null) {
            // T239 : ancien retour muet. Si le cache a ECHOUE (parse impossible),
            // le pipeline mourait ici sans aucune trace.
            deepluckyblock.util.DebugLog.err("[DLB-LAKE] ABORT implicite : template indisponible en memoire (echec cache="
                    + deepluckyblock.util.StructureTemplateCache.hasFailed(NBT_NAME) + ") -- construction annulee");
            return;
        }
        List<StructureTemplate.StructureBlockInfo> raw = exBlocks(tmpl);
        if (raw.isEmpty()) {
            deepluckyblock.util.DebugLog.err("[DLB-LAKE] ABORT implicite : template vide (0 bloc) -- construction annulee");
            return;
        }

        int sx = tmpl.getSize().getX();
        int sy = tmpl.getSize().getY();
        int sz = tmpl.getSize().getZ();

        BlockPos bfly = new BlockPos(px, py + 10, pz);

        // T234 : filtrage mis en cache entre relances sur le MEME centre (le
        // re-filtrage coute ~1 s de thread serveur). Invalide a l'abandon, au
        // deplacement du centre (conflit tardif) et en fin de pose.
        List<StructureTemplate.StructureBlockInfo> filt;
        final int minRelativeY;
        long filterKey = (((long) px) << 32) ^ (pz & 0xffffffffL) ^ (((long) py) << 24);
        if (lakeFiltCache != null && lakeFiltCacheKey == filterKey) {
            filt = lakeFiltCache;
            minRelativeY = lakeFiltCacheMinRelY;
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[DLB-LAKE] T234 : liste de pose REUTILISEE ("
                    + filt.size() + " blocs) -- aucun re-filtrage pour cette relance");
        } else {
            long filterT0 = System.currentTimeMillis();
            filt = new ArrayList<>(raw.size());
            int minRelativeY0 = Integer.MAX_VALUE;
            for (var info : raw) {
                if (info.state().isAir() || isLaggy(info.state())) continue;
                filt.add(info);
                if (info.pos().getY() < minRelativeY0) minRelativeY0 = info.pos().getY();
            }
            if (filt.isEmpty()) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE] ABORT implicite : 0 bloc posable apres filtrage -- construction annulee");
                return;
            }
            lakeFiltCache = filt;
            lakeFiltCacheKey = filterKey;
            lakeFiltCacheMinRelY = minRelativeY0;
            minRelativeY = minRelativeY0;
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[DLB-LAKE] liste de pose prête sans tri : " + filt.size()
                    + " blocs filtrés en " + (System.currentTimeMillis() - filterT0) + "ms");
        }
        // T175 : NE PLUS TRIER 1 359 489 blocs sur le thread serveur.
        // orderByY créait plusieurs millions de comparaisons et une énorme
        // pression GC juste après l'annonce : le log restait muet pendant plus
        // de cinq minutes avec des retards de 16–18 s. Le clear est terminé
        // avant le paste et la physique est neutralisée; l'ordre Y n'est donc
        // pas une condition de correction. Conserver l'ordre préchargé du NBT.

        // T234 : le message « building layer by layer » n'est annonce qu'UNE
        // FOIS par construction (avant, chaque relance de queueBuild le
        // renvoyait au chat -- d'ou le double message 22:54:09/22:54:10).
        if (!lakeProbeAnnounced) {
            lakeProbeAnnounced = true;
            Component msg2 = Component.literal(
                    "Crimson Lake: loading surrounding chunks and building layer by layer...")
                    .withStyle(ChatFormatting.AQUA);
            for (var pl : level.players()) pl.sendSystemMessage(msg2);
        }

        final List<StructureTemplate.StructureBlockInfo> fFilt = filt;
        // T80 : la sonde ne lit QUE groundY(px, pz) (ancrage vertical du lac) --
        // elle n'a donc besoin que du chunk du CENTRE, charge en FULL avec sa
        // heightmap primee (regle T7 inchangee : aucune lecture sur chunk non
        // charge). Avant, TOUTE l'emprise (340 chunks, dont ~23 s de generation
        // en CI, run 36336086415) etait demandee ici pour cette seule lecture,
        // puis le prepZone qui suit rechargeait une zone encore plus large
        // (756 chunks) : deux attentes payees pour le meme terrain. Le
        // prepZone suivant charge toujours l'emprise entiere de son cote :
        // aucun changement de geometrie, d'ancrage ni de pipeline.
        BlockPos probeMin = new BlockPos(px - 8, py, pz - 8);
        BlockPos probeMax = new BlockPos(px + 8, py, pz + 8);
        StructureTerrainPrep.setTerrainRingScalePercent(100);
        StructureTerrainPrep.setStructureName(NBT_NAME);
        // Read the actual ground only after loading; the player's altitude is not terrain.
        StructureTerrainPrep.preloadBox(level, probeMin, probeMax, () -> {
            if (!deepluckyblock.util.ChunkKeeper.zoneLoaded(level, probeMin, probeMax)) {
                // T234 : relance BORNEE. Avant : +1 tick a l'infini, avec un
                // re-filtrage de 1,36 M de blocs a CHAQUE essai (~1 s de thread
                // serveur par tentative, observe en jeu a 22:54:09/22:54:10).
                // Desormais : cadence 2 s, filtrage reutilise, deadline dure,
                // progression annoncee, abandon propre au-dela.
                long waiting = lakeBuildT0Ms > 0 ? System.currentTimeMillis() - lakeBuildT0Ms : 0L;
                if (waiting > LAKE_PROBE_DEADLINE_MS) {
                    abortLakeBuild(level, "sonde terrain non chargee apres " + waiting / 1000
                            + " s (deadline " + LAKE_PROBE_DEADLINE_MS / 1000 + " s)");
                    return;
                }
                announceLakeProgress(level, "still preparing terrain (" + waiting / 1000
                        + "s / " + LAKE_PROBE_DEADLINE_MS / 1000 + "s)...");
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 40,
                        () -> queueBuild(level, px, py, pz));
                return;
            }
            // =========================================================
            // T465 : ANCRAGE VERTICAL -- MESURE, RELOGEMENT, PUIS NAPPE
            // =========================================================
            // Avant : groundY(level, px, pz, py - 1). Quand la sonde echouait,
            // elle rendait py-1, c'est-a-dire L'ALTITUDE DU DECLENCHEUR, sans
            // le dire. Demande a Y=213 au-dessus d'un ocean, cela donnait un
            // ancrage a 212, une origine a 193, rabotee par le clamp a
            // maxBuildHeight - sy = 84 : exactement le [-811,84,-313] annonce
            // en jeu, soit 22 blocs au-dessus de la mer.
            int probedY = deepluckyblock.util.SafeSurface.groundYOrMissing(level, px, pz);
            lakeWaterMode = false;
            double wet = wetFraction(level, px, pz, sx, sz);
            if (wet > OCEAN_WET_TRIGGER || probedY == Integer.MIN_VALUE) {
                // 1er choix (decision utilisateur) : REFUSER LA MER, ALLER A TERRE.
                BlockPos land = (lakeOceanRelocs < MAX_OCEAN_RELOCS)
                        ? findLandSite(level, px, pz, Math.max(sx, sz) / 2 + 16, 6, sx, sz)
                        : null;
                if (land != null) {
                    lakeOceanRelocs++;
                    deepluckyblock.util.DebugLog.err("[DLB-LAKE] site marin (" + Math.round(wet * 100)
                            + "% d'eau sur l'emprise, sol " + (probedY == Integer.MIN_VALUE ? "INTROUVABLE" : "a Y=" + probedY)
                            + ") : relogement " + lakeOceanRelocs + "/" + MAX_OCEAN_RELOCS
                            + " vers [" + land.getX() + "," + land.getZ() + "]");
                    deepluckyblock.util.ChunkKeeper.release(level);
                    queueBuild(level, land.getX(), py, land.getZ());
                    return;
                }
                // Dernier recours : la couche d'eau la plus haute fait office de
                // sol, et on ne lisse rien -- lisser de l'eau n'a aucun sens.
                int ws = deepluckyblock.util.SafeSurface.waterSurfaceY(level, px, pz);
                if (ws != Integer.MIN_VALUE) {
                    probedY = ws;
                    lakeWaterMode = true;
                    deepluckyblock.util.DebugLog.err("[DLB-LAKE] aucun site terrestre apres "
                            + lakeOceanRelocs + " relogement(s) : pose EN MER, nappe a Y=" + ws
                            + " prise pour sol, lissage de terrain desactive");
                }
            }
            if (probedY == Integer.MIN_VALUE) {
                // Ni sol, ni nappe : on ne devine plus une altitude. L'ancien
                // repli muet est precisement ce qui a produit le lac dans le ciel.
                deepluckyblock.util.DebugLog.err("[DLB-LAKE] ABORT: aucun sol ni nappe sous ["
                        + px + "," + pz + "] -- aucune altitude inventee, aucun terrain modifie");
                deepluckyblock.util.ChunkKeeper.release(level);
                Structures5Procedure.notifyGenerationAborted(level);
                lakeBuildT0Ms = 0L;
                lakeFiltCache = null;
                lakeProbeAnnounced = false;
                lakeOceanRelocs = 0;
                lakeWaterMode = false;
                return;
            }
            // T477 : ANCRAGE SUR L'EMPRISE, PLUS SUR UNE SEULE COLONNE.
            //
            // Mesure en jeu (log du 23/11, monde TEST, graine 3668398517386263602,
            // lac centre [-189,83,488]) : la sonde a lu le sol a Y=102 parce que la
            // colonne du centre tombait sur un sommet enneige, alors que le relief
            // moyen des 246 x 284 blocs de l'emprise est a Y~88. Le clear part de
            // groundY+1 : il a donc tranche le monde a plat a Y=102 et laisse
            // intacte une dalle de montagne de 14 blocs DANS le bassin. Le rapport
            // de scan le prouve : 44 chunks sur 81 plafonnent a exactement 102,
            // aucun ne depasse, 13 % des colonnes ont une section de tronc ou de
            // feuillage comme bloc de surface et 24,7 % ont de l'air piege dessous.
            // Les trois symptomes vus par le joueur (sol non repeint, arbres coupes
            // a moitie, trou angulaire) sont ce seul defaut.
            //
            // Correctif : la colonne du centre reste la SEULE lecture reelle (regle
            // T7 : jamais de lecture sur chunk non charge, et elle seule est
            // chargee ici), mais on la corrige de l'ecart entre le relief theorique
            // du centre et la MEDIANE du relief theorique de toute l'emprise. Le
            // bruit de generation ne charge ni ne genere rien : c'est le meme
            // procede que wetFraction juste en dessous. Mediane et non moyenne :
            // un seul pic ne doit pas deplacer l'ancrage.
            int groundY = ancrerSurEmprise(level, px, pz, sx, sz, probedY);
            BlockPos origin = lakeOrigin(px, pz, groundY, minRelativeY, sx, sz);
            // T157 : adapter l'ancrage vertical AVANT toute annonce/passe au lieu
            // d'abandonner le lac dans les mondes bas ou près du plafond.
            int minOriginY = level.getMinBuildHeight() + 1 - minRelativeY;
            int maxOriginY = level.getMaxBuildHeight() - sy;
            int clampedY = Math.max(minOriginY, Math.min(maxOriginY, origin.getY()));
            if (clampedY != origin.getY()) origin = new BlockPos(origin.getX(), clampedY, origin.getZ());
            final BlockPos finalOrigin = origin;
            BlockPos min = finalOrigin;
            BlockPos max = finalOrigin.offset(sx - 1, sy - 1, sz - 1);

            // T163 : les deux minutes de présage laissaient d'autres structures
            // s'enregistrer dans la future emprise. Revalider AU DERNIER MOMENT,
            // avant le moindre clear, et déplacer tout le lac en cas de conflit.
            int lakeRadius = Math.max(sx, sz) / 2 + 16;
            String conflict = deepluckyblock.util.StructureSites.conflict(level, px, pz, lakeRadius);
            if (conflict != null) {
                BlockPos shifted = deepluckyblock.util.StructureSites.freeOffset(level,
                        new BlockPos(px, py, pz), 12, lakeRadius, lakeRadius);
                String shiftedConflict = deepluckyblock.util.StructureSites.conflict(
                        level, shifted.getX(), shifted.getZ(), lakeRadius);
                deepluckyblock.util.ChunkKeeper.release(level);
                if (shiftedConflict != null || (shifted.getX() == px && shifted.getZ() == pz)) {
                    // Garde absolue anti-boucle : l'ancienne version rappelait
                    // queueBuild éternellement sur le même centre.
                    deepluckyblock.util.DebugLog.err("[DLB-LAKE] ABORT: aucune emprise complete libre trouvee; "
                            + "aucun terrain modifie (conflit '" + shiftedConflict + "')");
                    // T228 : ABORT = notifyGenerationAborted, jamais Finished :
                    // Finished gravait le lac comme « deja apparu » alors qu'il
                    // n'avait rien pose -> il devenait intirable pour toujours.
                    Structures5Procedure.notifyGenerationAborted(level);
                    // T234 : reset des bornes/caches de build pour toute abort.
                    lakeBuildT0Ms = 0L;
                    lakeFiltCache = null;
                    lakeProbeAnnounced = false;
                    lakeOceanRelocs = 0;      // T465
                    lakeWaterMode = false;    // T465
                    return;
                }
                if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[DLB-LAKE] conflit tardif avec '" + conflict
                        + "' : centre déplacé UNE FOIS vers [" + shifted.getX() + "," + shifted.getZ()
                        + "] AVANT terrain/clear");
                queueBuild(level, shifted.getX(), py, shifted.getZ());
                return;
            }
            // Réserver immédiatement l'emprise : aucun autre événement lancé
            // pendant le long paste ne pourra désormais apparaître à l'intérieur.
            deepluckyblock.util.StructureSites.register(level, NBT_NAME,
                    min.getX(), min.getZ(), max.getX(), max.getZ());

            if (finalOrigin.getY() + minRelativeY <= level.getMinBuildHeight()
                    || max.getY() >= level.getMaxBuildHeight()) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE] ABORT: template outside build height; no blocks placed");
                deepluckyblock.util.ChunkKeeper.release(level);
                // T228 : ABORT = notifyGenerationAborted (voir le correctif
                // ci-dessus) : la structure doit redevenir tirable.
                Structures5Procedure.notifyGenerationAborted(level);
                // T234 : reset des bornes/caches de build pour toute abort.
                lakeBuildT0Ms = 0L;
                lakeFiltCache = null;
                lakeProbeAnnounced = false;
                lakeOceanRelocs = 0;      // T465
                lakeWaterMode = false;    // T465
                return;
            }
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[DLB-LAKE] anchor: ground=" + groundY + ", lowest block="
                    + (finalOrigin.getY() + minRelativeY) + ", sink=" + SINK_BLOCKS);
            // All shaping precedes clearing. Nothing may refill the interior before paste.
            // groundY is the SOLID selected ground: shared clearance only removes what
            // stands ABOVE it. The lake's own sink and its interior are written by the
            // paste itself over the preserved ground (T93 : on n'efface plus sous le sol).
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[DLB-LAKE] clearance floor: selected groundY=" + groundY
                    + ", shared clearance starts at Y=" + (groundY + 1)
                    + ", foundation Y=" + (finalOrigin.getY() + minRelativeY)
                    + " (sink=" + SINK_BLOCKS + ", T93 : sous-sol preserve, creuse par le paste)");
            // T164 : Crimson Lake suit désormais le pipeline BRUT de Circus.
            // Le lac contient déjà son eau, ses berges, son fond et ses décors :
            // prepZone + decorateTerrainOnly retraitaient 504 chunks et 1,36 M
            // blocs avant la pose (plus de 100 s), puis les retravaillaient
            // encore après. Une seule opération est nécessaire : clear réel à
            // partir de groundY+1, puis paste -air. Sink/fond, ancrage, présage,
            // contrôle d'emprise et règles propres au lac restent inchangés.
            // T234 : TOUTE l'emprise est desormais TENUE par le ChunkKeeper
            // pendant le clear+paste (avant : seul le nob de sonde 3x3 chunks
            // l'etait ; le reste n'etait demande qu'un chunk a la fois par le
            // clear, sans le moteur de maintien construit pour ca en T46-T135).
            // Le keeper pompe alors toute l'emprise en parallele pendant le
            // clear -- plus de blocage a la chaine sur un chunk manquant ; et
            // la deadline globale T234 (540 s) abandonne proprement AVANT le
            // garde-fou du keeper (600 s), jamais apres.
            // T478 : la zone tenue couvre AUSSI l'anneau du talus. Sans cette marge
            // les chunks du raccord ne sont pas charges et le talus les saute en
            // silence -- on obtiendrait un lissage a trous.
            deepluckyblock.util.ChunkKeeper.track(level,
                    finalOrigin.offset(0, minRelativeY, 0).offset(-TALUS_LARGEUR, 0, -TALUS_LARGEUR),
                    max.offset(TALUS_LARGEUR, 0, TALUS_LARGEUR));
            announceLakeProgress(level, "terrain locked, clearing starts (" + sx + "x" + sz + " blocks)...");
            // T303-PERF : regroupement lineaire O(N) par chunk monde (bucket counting sans Comparator ni GC,
            // ~6 ms sur 1,36 M blocs, respecte T175) pour ecrire chaque chunk en continu via LevelChunkSection.
            final List<StructureTemplate.StructureBlockInfo> bucketedFilt =
                    bucketByWorldChunk(fFilt, finalOrigin.getX(), finalOrigin.getZ(), sx, sz);
            final BlockPos lakeMin = finalOrigin.offset(0, minRelativeY, 0);
            final BlockPos lakeMax = max;
            // T469 : le clear de la base, et rien d'autre. Plancher groundY+1
            // (T93 : le sous-sol naturel est conserve, le bassin est creuse par
            // la matiere meme du template), aucun masque de colonnes, aucune
            // protection d'emprise, aucun lissage avant la pose.
            // T479 -- ETALEMENT EXTERIEUR VERS INTERIEUR (consigne du 23/11). Le T478
            // s'arretait au bord de la boite et laissait intact le tablier plat entre
            // la forme organique du lac et son rectangle englobant : 19789 colonnes,
            // 100 % de ce qui pouvait l'etre. Quatre temps desormais :
            //   1. talusMesurer -- RELEVE du terrain intact, AVANT le clear. Sans lui le
            //      tablier n'aurait aucune altitude naturelle vers laquelle remonter :
            //      on ne peut pas etaler vers l'interieur un relief deja efface.
            //   2. clearBuildVolume -- T482 : creuse la FORME du lac, une profondeur par
            //      colonne, du bloc le plus bas que le gabarit y posera jusqu'au plafond
            //      du monde. Avant, il rasait le RECTANGLE englobant a groundY+1 : les
            //      20 niveaux du bassin n'etaient jamais creuses (l'ancien terrain
            //      traversait la structure, 42,2 % des cases sous le plancher) et les
            //      angles du rectangle, a 61..91 blocs de l'ouvrage donc hors rampe du
            //      talus, restaient en parois droites.
            //   3. talusAppliquer -- etale le terrain depuis l'EMPREINTE de l'ouvrage
            //      vers l'exterieur, en traversant le tablier plat que le clear laisse
            //      entre la forme organique du lac et son rectangle englobant.
            //   4. PASTE_Q -- pose la structure, intacte, sur son empreinte protegee.
            final MasqueClear masqueClear = new MasqueClear(bucketedFilt, finalOrigin, lakeMin, lakeMax);
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out(
                    "[DLB-LAKE] T482 masque de creusement : " + masqueClear.colonnes + " colonnes d'ouvrage sur "
                    + (masqueClear.w * masqueClear.d) + " de la boite ; plancher par colonne, plafond = ciel");
            talusMesurer(level, lakeMin, lakeMax, groundY, bucketedFilt, finalOrigin,
                    talus -> clearBuildVolume(level, lakeMin, lakeMax, masqueClear,
                            () -> talusAppliquer(talus,
                                    () -> PASTE_Q.offer(new PasteJob(level, bucketedFilt,
                                            finalOrigin, bfly, System.currentTimeMillis(), sx, sy, sz)))));
        });
    }

    // =================================================================
    // T465 : LE LAC NE SE POSE PLUS EN L'AIR AU-DESSUS DE LA MER
    // =================================================================
    // Au plus, ce nombre de relogements avant de renoncer et de se rabattre
    // sur la nappe. Chaque relogement relance queueBuild, donc un preload :
    // il est hors de question de boucler.
    private static final int MAX_OCEAN_RELOCS = 6;
    /** Au-dela : le site est considere comme marin et on cherche la terre. */
    private static final double OCEAN_WET_TRIGGER = 0.50;
    /** En deca : un site candidat est accepte comme terrestre. */
    private static final double OCEAN_WET_ACCEPT = 0.25;
    private static int lakeOceanRelocs = 0;
    /** Dernier recours : la nappe tient lieu de sol, et on ne lisse rien. */
    private static boolean lakeWaterMode = false;

    /** T477 : cote de la grille d'echantillons de l'ancrage (81 points). */
    private static final int ANCHOR_GRID = 8;
    /** T477 : correction d'ancrage bornee -- une mediane aberrante ne doit pas noyer le lac. */
    private static final int ANCHOR_MAX_SHIFT = 48;

    /**
     * T477 : ancrage vertical calcule sur TOUTE l'emprise, et non sur la seule
     * colonne du centre.
     *
     * <p>Renvoie l'altitude de sol a utiliser comme {@code groundY}. Le point de
     * depart est {@code probedY}, seule lecture REELLE du terrain (chunk du centre,
     * charge, heightmap primee). On lui applique l'ecart entre le relief theorique
     * du centre et la mediane du relief theorique de 81 points repartis sur
     * l'emprise : si le centre est sur un pic, l'ancrage redescend au niveau
     * typique du lac ; si l'emprise est plate, la correction est nulle et le
     * comportement est exactement celui d'avant.</p>
     *
     * <p>{@code getBaseHeight} est du bruit pur : aucun chunk n'est charge ni
     * genere, et le cout est celui de 82 colonnes de bruit.</p>
     */
    private static int ancrerSurEmprise(ServerLevel level, int px, int pz, int sx, int sz, int probedY) {
        try {
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            var type = net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG;
            int stepX = Math.max(1, sx / ANCHOR_GRID), stepZ = Math.max(1, sz / ANCHOR_GRID);
            int half = ANCHOR_GRID / 2;
            int[] ech = new int[(ANCHOR_GRID + 1) * (ANCHOR_GRID + 1)];
            int n = 0;
            for (int ix = -half; ix <= half; ix++) {
                for (int iz = -half; iz <= half; iz++) {
                    ech[n] = gen.getBaseHeight(px + ix * stepX, pz + iz * stepZ, type, level, rs);
                    n++;
                }
            }
            if (n == 0) return probedY;
            java.util.Arrays.sort(ech, 0, n);
            int theoMedian = ech[n / 2];
            int theoCentre = gen.getBaseHeight(px, pz, type, level, rs);
            int correction = theoMedian - theoCentre;
            if (correction < -ANCHOR_MAX_SHIFT) correction = -ANCHOR_MAX_SHIFT;
            if (correction > ANCHOR_MAX_SHIFT) correction = ANCHOR_MAX_SHIFT;
            int ancre = probedY + correction;
            int minY = level.getMinBuildHeight() + 2;
            int maxY = level.getMaxBuildHeight() - 2;
            if (ancre < minY) ancre = minY;
            if (ancre > maxY) ancre = maxY;
            deepluckyblock.util.DebugLog.out("[DLB-LAKE] T477 ancrage : colonne centrale Y=" + probedY
                    + ", relief theorique centre=" + theoCentre + " mediane emprise=" + theoMedian
                    + " (" + n + " points sur " + sx + "x" + sz + "), correction " + correction
                    + " -> groundY=" + ancre);
            return ancre;
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.err("[DLB-LAKE] T477 ancrage indisponible, colonne centrale conservee : " + t);
            return probedY;
        }
    }

    /**
     * T465 : part NOYEE de l'emprise, sondee SANS generer le moindre chunk.
     *
     * <p>Modele deja eprouve dans Structures5Procedure (choix de zone seche) :
     * bruit pur {@code getBaseHeight(OCEAN_FLOOR_WG)} compare au niveau de la
     * mer quand le chunk n'est pas charge, lecture reelle {@code columnWet}
     * quand il l'est. 25 points repartis sur toute l'emprise -- le centre seul
     * ne dit rien d'une emprise de 246 x 284.
     */
    private static double wetFraction(ServerLevel level, int cx, int cz, int sx, int sz) {
        var gen = level.getChunkSource().getGenerator();
        var rs = level.getChunkSource().randomState();
        int minDryY = Math.max(63, level.getSeaLevel());
        int stepX = Math.max(1, sx / 4), stepZ = Math.max(1, sz / 4);
        int wet = 0, n = 0;
        for (int ix = -2; ix <= 2; ix++) {
            for (int iz = -2; iz <= 2; iz++) {
                int qx = cx + ix * stepX, qz = cz + iz * stepZ;
                n++;
                if (deepluckyblock.util.SafeSurface.isLoadedAt(level, qx, qz)) {
                    if (deepluckyblock.util.SafeSurface.columnWet(level, qx, qz)) wet++;
                } else {
                    int hf = gen.getBaseHeight(qx, qz,
                            net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                    if (hf < minDryY) wet++;
                }
            }
        }
        return n == 0 ? 0.0 : (double) wet / (double) n;
    }

    /**
     * T465 : premier site TERRESTRE autour du centre, en anneaux croissants.
     * Sondage par bruit : aucun chunk genere, donc on peut regarder loin sans
     * payer les 8 a 12 s de generation a froid redoutees par T304.
     */
    private static BlockPos findLandSite(ServerLevel level, int cx, int cz,
                                         int step, int rings, int sx, int sz) {
        for (int r = 1; r <= rings; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = cx + dx * step, z = cz + dz * step;
                    if (wetFraction(level, x, z, sx, sz) <= OCEAN_WET_ACCEPT) {
                        return new BlockPos(x, 0, z);
                    }
                }
            }
        }
        return null;
    }

    static BlockPos lakeOrigin(int x, int z, int groundY, int minRelativeY, int sx, int sz) {
        // Reference is the first free block above natural ground, not template padding.
        return new BlockPos(x - sx / 2, groundY + 1 - SINK_BLOCKS - minRelativeY, z - sz / 2);
    }

    /**
     * Creuse la place de l'ouvrage, COLONNE PAR COLONNE, jusqu'au ciel.
     *
     * <p><b>T482 -- pourquoi le T93 etait faux.</b> Le T93 (consigne dev 29/09 :
     * « tu supprimes meme les parties qui sont sol et en dessous du sol -- efface
     * UNIQUEMENT LE HAUT ») avait remonte le plancher du creusement a
     * {@code groundY + 1}, en s'appuyant sur cette mesure : « socle 100 % plein,
     * SEULS ~78 blocs d'air existent sous le sol, le bassin est donc creuse par la
     * matiere meme du template ».
     *
     * <p><b>Cette mesure comptait les blocs d'air EXPLICITES d'un fichier qui n'en
     * contient aucun.</b> La palette de {@code crimsonlake.nbt} compte 366 etats et
     * zero air : le gabarit est creux PAR ABSENCE D'ENTREE, pas par de l'air pose.
     * Compter les entrees « air » y donne forcement ~0, et en conclure que le bassin
     * est plein est un contresens. Le bon decompte est celui des cases SANS entree :
     * 26 447 colonnes sur 69 864 (37,9 %) ne recoivent jamais un bloc, et sous
     * l'ancien plancher 590 038 cases sur 1 397 280 (42,2 %) restaient en terre.
     *
     * <p>S'y ajoutait une impossibilite de principe : la pose saute l'air
     * ({@code info.state().isAir() -> continue}), donc <b>elle ne sait qu'ajouter de
     * la matiere, jamais en retirer</b>. Le bassin ne pouvait pas « etre creuse par
     * le template ». Resultat en jeu, run du 24/11 : l'ancien terrain traversait la
     * structure.
     *
     * <p><b>Ce que fait desormais le creusement</b> (consigne dev 24/11 : « creuser la
     * forme du lac mais monter a y max pour assurer que la moindre montagne n'y
     * soit ») :
     * <ul>
     *   <li>il suit la <b>forme reelle</b> de l'ouvrage, pas son rectangle englobant :
     *       une colonne ou le gabarit ne pose rien n'est pas touchee du tout. C'est
     *       ce qui supprime la fosse rectangulaire et ses parois droites ;</li>
     *   <li>dans une colonne de l'ouvrage, il part du <b>bloc le plus bas que le
     *       gabarit y posera</b> -- la place de la structure est donc vide, bassin
     *       compris ;</li>
     *   <li>et il monte jusqu'au <b>plafond du monde</b>, pour qu'aucune montagne ne
     *       subsiste au-dessus de l'ouvrage.</li>
     * </ul>
     * Sous ce plancher par colonne, le sol et le sous-sol naturels restent intacts :
     * la structure repose toujours sur la terre du monde.
     *
     * @param masque profondeur de creusement par colonne, voir {@link MasqueClear}
     */
    static void clearBuildVolume(ServerLevel level, BlockPos min, BlockPos max,
                                 MasqueClear masque, Runnable onReady) {
        new LakeClearJob(level, min, max, masque, onReady).step();
    }

    /**
     * T482 : masque de creusement -- une profondeur par colonne, deduite du gabarit.
     *
     * <p>Pour chaque colonne (x,z) du monde, retient l'altitude du bloc le PLUS BAS
     * que la pose y ecrira. Une colonne ou le gabarit ne pose rien reste
     * {@link #ABSENTE} : le creusement ne doit pas y toucher.
     *
     * <p>La liste recue est celle reellement posee (air et blocs lourds deja filtres),
     * exactement la meme que celle dont {@code TalusJob} tire son empreinte : le trou
     * et le raccord de terrain parlent donc de la meme forme.
     */
    static final class MasqueClear {
        static final int ABSENTE = Integer.MIN_VALUE;
        final int x0, z0, w, d;
        private final int[] plancher;
        int colonnes;

        MasqueClear(List<StructureTemplate.StructureBlockInfo> blocs, BlockPos origin,
                    BlockPos min, BlockPos max) {
            x0 = min.getX(); z0 = min.getZ();
            w = max.getX() - x0 + 1; d = max.getZ() - z0 + 1;
            plancher = new int[Math.max(1, w * d)];
            java.util.Arrays.fill(plancher, ABSENTE);
            int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
            for (StructureTemplate.StructureBlockInfo bi : blocs) {
                int ix = ox + bi.pos().getX() - x0;
                int iz = oz + bi.pos().getZ() - z0;
                if (ix < 0 || iz < 0 || ix >= w || iz >= d) continue;
                int i = iz * w + ix;
                int wy = oy + bi.pos().getY();
                if (plancher[i] == ABSENTE) { plancher[i] = wy; colonnes++; }
                else if (wy < plancher[i]) plancher[i] = wy;
            }
        }

        /** Altitude a partir de laquelle creuser cette colonne, ou {@link #ABSENTE}. */
        int plancherDe(int wx, int wz) {
            int ix = wx - x0, iz = wz - z0;
            if (ix < 0 || iz < 0 || ix >= w || iz >= d) return ABSENTE;
            return plancher[iz * w + ix];
        }
    }

    // ================================================================================
    // T479 -- ETALEMENT DU TERRAIN, DE L'EXTERIEUR VERS L'OUVRAGE
    // ================================================================================

    /** Largeur de l'etalement, en blocs, mesuree DEPUIS L'EMPREINTE de l'ouvrage. */
    private static final int TALUS_LARGEUR = 48;
    /** Passes de lissage du champ de cibles. */
    private static final int TALUS_FLOU_PASSES = 6;
    /** Passes de bouchage des trous du releve, par diffusion. */
    private static final int TALUS_BOUCHAGE_PASSES = 8;
    /** Profondeur de recherche du sol sous la vegetation. */
    private static final int TALUS_VEG_SCAN = 48;
    /** Balayages maximaux de la passe de mesure, le temps que les chunks arrivent. */
    /**
     * T486 : nombre de balayages consecutifs SANS le moindre chunk gagne avant
     * d'abandonner. L'ancien critere etait un nombre de tours fixe : sur le run du
     * 24 nov. les 12 tours ont ete consommes pendant que le serveur affichait
     * « Can't keep up! » (10,5 s de gel), et le releve a rendu la main avec 60 chunks
     * jamais vus -- tous au sud, parce que le curseur balaie les z croissants et que
     * le dernier servi est toujours le meme cote. Compter la STAGNATION et non les
     * tours laisse le keeper finir son travail tant qu'il progresse.
     */
    private static final int TALUS_STAGNATION_MAX = 8;
    /**
     * T486 : temps laisse au ChunkKeeper pour pomper l'anneau AVANT que le releve
     * commence. Le pipeline lancait le releve dans la foulee de track() : le keeper
     * venait de recevoir 550 chunks et n'en avait encore aucun. Le releve balayait
     * alors du vide, consommait ses tours et rendait la main. Sur le run du 24 nov.
     * tout le pipeline a tenu en 26 s pour 550 chunks a charger -- il n'a jamais
     * laisse au monde le temps d'exister au sud.
     */
    private static final long TALUS_PRECHARGE_MS = 60_000L;
    /** Remblai maximal sous la cible. Genereux : la cible ne depasse jamais le naturel. */
    private static final int TALUS_REMBLAI_MAX = 64;
    /** Le raccord est cosmetique : passe ce delai il rend la main, la pose n'attend jamais. */
    private static final long TALUS_DEADLINE_MS = 180_000L;

    /**
     * Etale le terrain naturel vers l'ouvrage, pour qu'aucun contour de coupe ne subsiste.
     *
     * <p>T479, consigne dev du 23/11 : « remets l'etalement exterieur vers interieur, un
     * smooth opti (bien + que precedemment), pour eviter les fameux contours a la coupe,
     * je veux un resultat propre sans gros angles visible ni trous sans aucun sens ».</p>
     *
     * <p><b>Ce que le T478 avait rate.</b> Son talus s'arretait au bord de la boite. Or
     * l'ouvrage est une forme organique inscrite dans un rectangle : 38696 colonnes sur
     * les 68769 de la boite recoivent de la matiere, les 30073 autres restaient le
     * plateau plat laisse par le clear. Mesure du run TEST2 : <b>19789 colonnes de
     * tablier plat, soit 100 % de ce qui pouvait l'etre, inchangees par le T478.</b>
     * Le raccord exterieur etait propre et le rectangle restait entier -- c'est bien
     * « le contour a la coupe », et il etait toujours la.</p>
     *
     * <p><b>Trois corrections.</b></p>
     * <ol>
     *   <li><b>La pente part de l'EMPREINTE, plus de la boite.</b> La distance est
     *       mesuree jusqu'a la premiere colonne ou l'ouvrage pose reellement de la
     *       matiere. L'etalement traverse donc le tablier, franchit le bord de la boite
     *       sans rien y marquer, et rejoint le terrain naturel. Le rectangle n'existe
     *       plus comme figure : il n'est plus une frontiere pour personne.</li>
     *   <li><b>Distance euclidienne, plus de Chebyshev.</b> La distance de Chebyshev a
     *       un rectangle est constante par paliers carres : elle fabrique des aretes a
     *       45 degres dans les angles -- les « gros angles visibles ». Une transformation
     *       de distance par chanfrein 3-4, en deux balayages, donne des courbes de niveau
     *       rondes qui epousent la forme de l'ouvrage.</li>
     *   <li><b>Le releve est pris AVANT le clear</b>, donc sur le terrain intact, y
     *       compris sous l'emprise. Sans cela le tablier n'aurait eu aucune altitude
     *       naturelle vers laquelle remonter : on ne peut pas etaler vers l'interieur un
     *       relief qu'on a deja efface.</li>
     * </ol>
     *
     * <p><b>Profil.</b> {@code cible = groundY + (naturel - groundY) * (1 - (1-u)^3)},
     * avec {@code u = distance / largeur} borne a 1. Cette courbe est tangente a
     * l'horizontale a son extremite exterieure -- elle rejoint donc le terrain naturel
     * sans aucune cassure -- mais elle a une pente franche au contact de l'ouvrage, ce
     * qui empeche la collerette plate que donnait le lissage symetrique du T478. Comme
     * k reste dans [0,1], la cible reste toujours comprise entre le plateau et le terrain
     * naturel : <b>ni tranchee ni crevasse ne sont representables</b>.</p>
     *
     * <p>Cinq profils ont ete compares sur le terrain reel du run, a largeur et lissage
     * egaux (tablier plat restant / colonnes en falaise) : smoothstep 22,2 % / 4,53 % ;
     * ease-out quadratique 10,8 % / 4,63 % ; <b>ease-out cubique 8,8 % / 4,73 %</b> ;
     * ease-out quartique 7,6 % / 4,79 % ; racine 7,9 % / 4,56 %. Le cubique est le
     * meilleur compromis, et passer la largeur de 40 a 48 ramene les falaises a 2,42 %.</p>
     *
     * <p><b>Resultat simule sur le terrain reel du run</b>, juge hors ouvrage sur un cadre
     * commun : tablier plat 100 % puis 9,7 % ; colonnes en falaise 13,5 % puis 2,4 % ;
     * angles marques 22,6 % puis 20,3 %. Pour reference le terrain NATUREL de cette
     * region est a 18,7 % de falaises : le raccord est plus doux que le monde lui-meme.</p>
     *
     * <p><b>Les trous.</b> Un chunk absent au moment du releve laissait un vide, et un
     * vide non traite est une plaque de terrain brut au milieu d'une zone lissee -- un
     * « trou sans aucun sens ». La mesure rebalaye donc les chunks manquants jusqu'a
     * douze fois pendant que le keeper les pompe, et ce qui manque encore est comble par
     * diffusion depuis les voisins. Aucune colonne de l'anneau ne reste sans cible.</p>
     */
    static void talusMesurer(ServerLevel level, BlockPos min, BlockPos max, int groundY,
                             List<StructureTemplate.StructureBlockInfo> blocs, BlockPos origin,
                             java.util.function.Consumer<TalusJob> onDone) {
        TalusJob job;
        try {
            job = new TalusJob(level, min, max, groundY, blocs, origin);
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.err("[DLB-LAKE] T479 releve impossible, raccord abandonne :");
            deepluckyblock.util.DebugLog.trace(t);
            onDone.accept(null);
            return;
        }
        job.lancerMesure(onDone);
    }

    /** Second temps : calcule le relief voulu, puis l'ecrit. Jamais bloquant pour la pose. */
    static void talusAppliquer(TalusJob job, Runnable onDone) {
        if (job == null) {
            if (onDone != null) onDone.run();
            return;
        }
        try {
            job.lancerApplication(onDone);
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.err("[DLB-LAKE] T479 raccord impossible, on passe a la pose :");
            deepluckyblock.util.DebugLog.trace(t);
            if (onDone != null) onDone.run();
        }
    }

    private static final class TalusJob {
        static final int ABSENT_T = Integer.MIN_VALUE;
        final ServerLevel level;
        final BlockPos min, max;
        final int groundY;
        /** Anneau = boite elargie de TALUS_LARGEUR sur les quatre cotes. */
        final int rx0, rz0, rx1, rz1, rw, rd;
        /** Altitude du sol naturel, relevee AVANT le clear. */
        final int[] solNaturel;
        /** Sommet de la vegetation, pour ne pas laisser d'arbre suspendu au-dessus d'une pente. */
        final int[] sommetVeg;
        /** Colonnes ou l'ouvrage pose reellement de la matiere : intouchables, et origine de la pente. */
        final boolean[] empreinte;
        /**
         * T484 : altitude de la SURFACE de l'ouvrage, colonne par colonne d'empreinte.
         * Le gabarit n'a aucune marge vide du cote ouest : son bord tranche le bassin
         * en plein, et la surface y est ~18 blocs sous le sol naturel. Sans cette
         * mesure la pente visait un plancher constant et laissait une falaise franche.
         */
        final int[] surfaceOuvrage;
        /** Altitude a rejoindre au contact, propagee depuis la colonne d'empreinte la plus proche. */
        int[] ancre;
        /** Distance de chanfrein a l'empreinte, en tiers de bloc (3 = orthogonal, 4 = diagonal). */
        int[] distance;
        /** Altitude voulue pour chaque colonne. Calculee une fois, jamais relue du monde. */
        int[] cible;
        final int ccx0, ccz0, ccw, ccd;
        final boolean[] chunkFait;
        int curseur, balayage;
        boolean mesureFinie;
        long colonnesRelevees, colonnesRabotees, colonnesRemblayees, blocsEcrits;
        int chunksManquants, trousCombles, empreinteCol;
        /**
         * T486 : une colonne dont le sol naturel n'a JAMAIS ete relevee et qu'aucun
         * voisin releve n'a pu combler. L'ultime recours de boucherTrous() lui donnait
         * groundY -- une altitude CONSTANTE, exactement le defaut que T484bis venait de
         * corriger pour l'ancre : la pente vise alors un plancher fictif et taille une
         * marche dans du terrain qu'on n'a jamais mesure. Sur le run du 24 nov. cela
         * touchait 10 136 colonnes de l'anneau (13,5 %), dont 9 280 au sud et dans les
         * coins sud. On les marque pour ne PAS y toucher.
         */
        boolean[] inventee;
        int colonnesInventees, chunksTardifs, balayagesSteriles;
        final long t0;
        long dernierLogMs;
        Runnable finApplication;
        java.util.function.Consumer<TalusJob> finMesure;

        TalusJob(ServerLevel level, BlockPos min, BlockPos max, int groundY,
                 List<StructureTemplate.StructureBlockInfo> blocs, BlockPos origin) {
            this.level = level; this.min = min; this.max = max; this.groundY = groundY;
            rx0 = min.getX() - TALUS_LARGEUR; rx1 = max.getX() + TALUS_LARGEUR;
            rz0 = min.getZ() - TALUS_LARGEUR; rz1 = max.getZ() + TALUS_LARGEUR;
            rw = rx1 - rx0 + 1; rd = rz1 - rz0 + 1;
            solNaturel = new int[rw * rd];
            sommetVeg = new int[rw * rd];
            empreinte = new boolean[rw * rd];
            surfaceOuvrage = new int[rw * rd];
            java.util.Arrays.fill(solNaturel, ABSENT_T);
            java.util.Arrays.fill(surfaceOuvrage, ABSENT_T);
            java.util.Arrays.fill(sommetVeg, ABSENT_T);
            // L'empreinte vient de la liste reellement posee (air et blocs lourds deja
            // filtres) : c'est la forme vraie de l'ouvrage, pas son rectangle englobant.
            int ox = origin.getX(), oz = origin.getZ(), oy = origin.getY();
            for (StructureTemplate.StructureBlockInfo bi : blocs) {
                int i = idx(ox + bi.pos().getX(), oz + bi.pos().getZ());
                if (i < 0) continue;
                if (!empreinte[i]) { empreinte[i] = true; empreinteCol++; }
                int ySurf = oy + bi.pos().getY();
                if (surfaceOuvrage[i] == ABSENT_T || ySurf > surfaceOuvrage[i]) surfaceOuvrage[i] = ySurf;
            }
            ccx0 = rx0 >> 4; ccz0 = rz0 >> 4;
            ccw = (rx1 >> 4) - ccx0 + 1; ccd = (rz1 >> 4) - ccz0 + 1;
            chunkFait = new boolean[ccw * ccd];
            t0 = System.currentTimeMillis();
            dernierLogMs = t0;
        }

        private int idx(int x, int z) {
            int ix = x - rx0, iz = z - rz0;
            if (ix < 0 || iz < 0 || ix >= rw || iz >= rd) return -1;
            return iz * rw + ix;
        }

        void lancerMesure(java.util.function.Consumer<TalusJob> onDone) {
            finMesure = onDone;
            curseur = 0; balayage = 0; mesureFinie = false;
            announceLakeProgress(level, "surveying terrain before clearing...");
            attendreZone();
        }

        /**
         * T486 : on ne releve pas un terrain qui n'est pas encore charge. Tant que le
         * keeper a des chunks en attente, on lui rend la main tick par tick. Borne par
         * TALUS_PRECHARGE_MS : un chunk qui ne vient jamais ne doit pas retenir la pose.
         */
        private void attendreZone() {
            try {
                deepluckyblock.util.ChunkKeeper.keep(level);
                if (System.currentTimeMillis() - t0 < TALUS_PRECHARGE_MS
                        && !deepluckyblock.util.ChunkKeeper.zoneComplete(level)) {
                    if (System.currentTimeMillis() - dernierLogMs >= 20_000L) {
                        dernierLogMs = System.currentTimeMillis();
                        announceLakeProgress(level, "waiting for terrain chunks to load...");
                    }
                    TestProcedure.schedule(level, TestProcedure.currentTick(level) + 1,
                            this::attendreZone);
                    return;
                }
            } catch (Throwable t) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE] T486 attente de zone interrompue :");
                deepluckyblock.util.DebugLog.trace(t);
            }
            pasMesure();
        }

        void lancerApplication(Runnable onDone) {
            finApplication = onDone;
            preparer();
            // T486 : l'application ne faisait qu'UN passage. Un chunk absent etait
            // re-demande puis definitivement saute -- il arrivait une seconde plus tard
            // et ne recevait jamais le raccord. On reutilise chunkFait pour repasser
            // tant qu'il en manque, exactement comme le releve.
            java.util.Arrays.fill(chunkFait, false);
            balayage = 0;
            curseur = 0; mesureFinie = true; balayagesSteriles = 0; chunksManquants = 0;
            announceLakeProgress(level, "blending terrain into the structure...");
            pasApplication();
        }

        // ------------------------------------------------------------------ MESURE

        private void pasMesure() {
            try {
                pasMesureGuarde();
            } catch (Throwable t) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE] T479 releve interrompu :");
                deepluckyblock.util.DebugLog.trace(t);
                finirMesure();
            }
        }

        private void finirMesure() {
            if (deepluckyblock.util.DebugLog.ENABLED)
                deepluckyblock.util.DebugLog.out("[DLB-LAKE] T479 releve : " + colonnesRelevees
                        + " colonnes sur " + (rw * rd) + ", empreinte " + empreinteCol
                        + " colonnes, " + chunksManquants + " chunks encore absents apres "
                        + balayage + " balayages, en " + (System.currentTimeMillis() - t0) + " ms");
            java.util.function.Consumer<TalusJob> f = finMesure;
            finMesure = null;
            if (f != null) f.accept(this);
        }

        private void pasMesureGuarde() {
            if (System.currentTimeMillis() - t0 > TALUS_DEADLINE_MS) { finirMesure(); return; }
            deepluckyblock.util.ChunkKeeper.keep(level);
            long deadline = System.nanoTime() + 40_000_000L;
            int total = ccw * ccd;
            while (curseur < total && System.nanoTime() < deadline) {
                if (!chunkFait[curseur]) {
                    int cx = ccx0 + (curseur % ccw), cz = ccz0 + (curseur / ccw);
                    var ca = level.getChunkSource().getChunkNow(cx, cz);
                    if (ca instanceof net.minecraft.world.level.chunk.LevelChunk lc) {
                        mesurerChunk(lc);
                        chunkFait[curseur] = true;
                    } else {
                        deepluckyblock.util.SafeSurface.reRequest(level, cx, cz);
                    }
                }
                curseur++;
            }
            if (curseur < total) {
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 1, this::pasMesure);
                return;
            }
            // Fin de balayage : reste-t-il des chunks absents ? Le keeper les pompe,
            // on repasse. Un trou de releve deviendrait une plaque de terrain brut.
            int manquants = 0;
            for (boolean b : chunkFait) if (!b) manquants++;
            // T486 : un tour ne compte que s'il n'a RIEN rapporte. Tant que les chunks
            // arrivent, on continue -- la deadline reste le garde-fou.
            if (balayage == 0 || manquants < chunksManquants) balayagesSteriles = 0;
            else balayagesSteriles++;
            chunksManquants = manquants;
            balayage++;
            if (manquants > 0 && balayagesSteriles < TALUS_STAGNATION_MAX) {
                curseur = 0;
                if (System.currentTimeMillis() - dernierLogMs >= 20_000L) {
                    dernierLogMs = System.currentTimeMillis();
                    announceLakeProgress(level, "surveying terrain (" + manquants + " chunks pending)...");
                }
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 1, this::pasMesure);
                return;
            }
            finirMesure();
        }

        /** Vegetation et couverture meuble : ne doivent pas etre prises pour le sol. */
        private boolean estVegetation(BlockState st) {
            if (st.isAir()) return true;
            if (st.is(net.minecraft.tags.BlockTags.LEAVES)) return true;
            if (st.is(net.minecraft.tags.BlockTags.LOGS)) return true;
            if (st.is(Blocks.SNOW)) return true;
            return !st.blocksMotion();
        }

        /** Releve pur, aucune ecriture. Tourne AVANT le clear, donc sur terrain intact. */
        private void mesurerChunk(net.minecraft.world.level.chunk.LevelChunk chunk) {
            int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int bas = level.getMinBuildHeight() + 1;
            for (int lz = 0; lz < 16; lz++) {
                int wz = baseZ + lz;
                if (wz < rz0 || wz > rz1) continue;
                for (int lx = 0; lx < 16; lx++) {
                    int wx = baseX + lx;
                    if (wx < rx0 || wx > rx1) continue;
                    int i = idx(wx, wz);
                    if (i < 0 || solNaturel[i] != ABSENT_T) continue;
                    int haut = chunk.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, lx, lz);
                    int y = haut, stop = Math.max(bas, haut - TALUS_VEG_SCAN);
                    while (y > stop && estVegetation(chunk.getBlockState(p.set(wx, y, wz)))) y--;
                    solNaturel[i] = y;
                    // T488 (reproche dev du 25/11 : « je vois dans les airs des fragments
                    // d'anciens terrains [...] tout ce qui est entite qui est reste dans les
                    // aires (blocs non solides genre snow carpet, herbes, fleurs etc) [...] des
                    // blocs de neige [...] des blocs de powdered snow ») : le releve utilisait
                    // MOTION_BLOCKING, qui par DEFINITION ignore tout bloc sans collision --
                    // couche de neige, tapis, herbes, fleurs, neige poudreuse. Le sommet releve
                    // tombait donc SOUS eux ; appliquerChunk() rabotait jusqu'a « haut » et les
                    // laissait flotter au-dessus du vide. WORLD_SURFACE = premier bloc NON AIR :
                    // il englobe toute la famille, blocs moddes compris, sans liste en dur.
                    sommetVeg[i] = Math.max(haut, chunk.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, lx, lz));
                    colonnesRelevees++;
                }
            }
        }

        // -------------------------------------------------------------- PREPARATION
        // Pur calcul sur tableaux. Aucune lecture du monde, aucune ecriture.

        /**
         * Construit le relief voulu. Quatre temps, dans cet ordre : boucher les trous du
         * releve, mesurer la distance a l'empreinte, tracer le profil, lisser le champ.
         */
        private void preparer() {
            boucherTrous();
            distance = transformeeDistance();
            int w3 = TALUS_LARGEUR * 3;
            double[] champ = new double[rw * rd];
            for (int i = 0; i < champ.length; i++) {
                if (empreinte[i]) {
                    champ[i] = surfaceOuvrage[i] == ABSENT_T ? groundY : surfaceOuvrage[i];
                    continue;
                }
                // T484 : on part de la surface REELLE de l'ouvrage au contact (ancre),
                // pas d'un plancher commun. Mesure du 24 nov. : au bord ouest la surface
                // de l'ouvrage est a Y 59..64 alors que groundY vaut 68 et le sol
                // naturel monte jusqu'a 82 -- l'ancienne formule laissait la falaise
                // entiere a la charge de personne.
                int a = ancre[i] == ABSENT_T ? groundY : ancre[i];
                double u = Math.min(1.0D, distance[i] / (double) w3);
                double v = 1.0D - u;
                double k = 1.0D - v * v * v;            // tangent dehors, franc au contact
                champ[i] = a + (solNaturel[i] - a) * k;
            }
            lisserChamp(champ);
            cible = new int[rw * rd];
            for (int i = 0; i < champ.length; i++) cible[i] = (int) Math.round(champ[i]);
        }

        /**
         * Un chunk absent au releve laisse un vide ; un vide laisse une plaque de terrain
         * brut au milieu du lissage. On comble par diffusion depuis les voisins connus.
         */
        private void boucherTrous() {
            for (int passe = 0; passe < TALUS_BOUCHAGE_PASSES; passe++) {
                boolean reste = false;
                int[] sortie = solNaturel.clone();
                for (int iz = 0; iz < rd; iz++) {
                    for (int ix = 0; ix < rw; ix++) {
                        int i = iz * rw + ix;
                        if (solNaturel[i] != ABSENT_T) continue;
                        long somme = 0; int n = 0;
                        for (int dz = -1; dz <= 1; dz++) {
                            int jz = iz + dz;
                            if (jz < 0 || jz >= rd) continue;
                            for (int dx = -1; dx <= 1; dx++) {
                                int jx = ix + dx;
                                if (jx < 0 || jx >= rw) continue;
                                int v = solNaturel[jz * rw + jx];
                                if (v == ABSENT_T) continue;
                                somme += v; n++;
                            }
                        }
                        if (n > 0) { sortie[i] = (int) (somme / n); trousCombles++; }
                        else reste = true;
                    }
                }
                System.arraycopy(sortie, 0, solNaturel, 0, sortie.length);
                if (!reste) break;
            }
            // T486 : ultime recours. Une colonne sans AUCUN voisin releve prenait le sol
            // de l'ouvrage -- une altitude constante, donc une cible fausse partout ou le
            // terrain reel n'est pas a cette altitude. On lui donne toujours groundY pour
            // que les calculs restent bornes, mais on la MARQUE : appliquerChunk() n'y
            // ecrira rien. Mieux vaut du terrain brut qu'une marche creusee vers un
            // plancher invente.
            inventee = new boolean[solNaturel.length];
            for (int i = 0; i < solNaturel.length; i++)
                if (solNaturel[i] == ABSENT_T) {
                    solNaturel[i] = groundY;
                    inventee[i] = true;
                    colonnesInventees++;
                }
        }

        /**
         * Distance a l'empreinte par chanfrein 3-4, deux balayages.
         *
         * <p>Le poids 3 en orthogonal et 4 en diagonale approche la distance euclidienne
         * a 2 % pres, pour deux parcours du tableau. C'est ce qui donne des courbes de
         * niveau rondes : la distance de Chebyshev, elle, est constante par carres
         * emboites et imprime une arete a 45 degres dans chaque angle.</p>
         */
        private int[] transformeeDistance() {
            // T484 : le chanfrein transporte AUSSI l'altitude de l'ouvrage le plus
            // proche. Connaitre la distance ne suffisait pas : il faut savoir QUELLE
            // altitude rejoindre, sinon la pente vise un plancher constant et tranche
            // net partout ou le bord du gabarit coupe le bassin en pleine profondeur.
            final int INF = 1 << 28;
            int[] d = new int[rw * rd];
            ancre = new int[rw * rd];
            for (int i = 0; i < d.length; i++) {
                if (empreinte[i]) { d[i] = 0; ancre[i] = surfaceOuvrage[i]; }
                else { d[i] = INF; ancre[i] = ABSENT_T; }
            }
            for (int iz = 0; iz < rd; iz++) {
                for (int ix = 0; ix < rw; ix++) {
                    int i = iz * rw + ix;
                    if (d[i] == 0) continue;
                    int m = d[i], a = ancre[i];
                    if (ix > 0 && d[i - 1] + 3 < m) { m = d[i - 1] + 3; a = ancre[i - 1]; }
                    if (iz > 0 && d[i - rw] + 3 < m) { m = d[i - rw] + 3; a = ancre[i - rw]; }
                    if (ix > 0 && iz > 0 && d[i - rw - 1] + 4 < m) { m = d[i - rw - 1] + 4; a = ancre[i - rw - 1]; }
                    if (ix + 1 < rw && iz > 0 && d[i - rw + 1] + 4 < m) { m = d[i - rw + 1] + 4; a = ancre[i - rw + 1]; }
                    d[i] = m; ancre[i] = a;
                }
            }
            for (int iz = rd - 1; iz >= 0; iz--) {
                for (int ix = rw - 1; ix >= 0; ix--) {
                    int i = iz * rw + ix;
                    if (d[i] == 0) continue;
                    int m = d[i], a = ancre[i];
                    if (ix + 1 < rw && d[i + 1] + 3 < m) { m = d[i + 1] + 3; a = ancre[i + 1]; }
                    if (iz + 1 < rd && d[i + rw] + 3 < m) { m = d[i + rw] + 3; a = ancre[i + rw]; }
                    if (ix + 1 < rw && iz + 1 < rd && d[i + rw + 1] + 4 < m) { m = d[i + rw + 1] + 4; a = ancre[i + rw + 1]; }
                    if (ix > 0 && iz + 1 < rd && d[i + rw - 1] + 4 < m) { m = d[i + rw - 1] + 4; a = ancre[i + rw - 1]; }
                    d[i] = m; ancre[i] = a;
                }
            }
            return d;
        }

        /**
         * Lissage du relief voulu, les DEUX bouts epingles.
         *
         * <p>L'empreinte est epinglee parce que l'ouvrage ne doit pas bouger. Mais le
         * terrain naturel au-dela de la rampe doit l'etre aussi : sans cela le flou
         * deborderait de l'anneau et irait deplacer, d'un bloc ou deux, un relief situe
         * a cinquante blocs de l'ouvrage -- des ecritures inutiles, et des arbres
         * decalottes loin de tout chantier. En epinglant les deux extremites, le flou
         * se fond progressivement vers chacune : aucune marche ne se forme ni au contact
         * de l'ouvrage, ni a la lisiere exterieure.</p>
         */
        private void lisserChamp(double[] champ) {
            final int w3 = TALUS_LARGEUR * 3;
            for (int passe = 0; passe < TALUS_FLOU_PASSES; passe++) {
                double[] sortie = champ.clone();
                for (int iz = 1; iz < rd - 1; iz++) {
                    for (int ix = 1; ix < rw - 1; ix++) {
                        int i = iz * rw + ix;
                        if (empreinte[i] || distance[i] >= w3) continue;
                        double s = 0.0D;
                        for (int dz = -1; dz <= 1; dz++)
                            for (int dx = -1; dx <= 1; dx++)
                                s += champ[(iz + dz) * rw + ix + dx];
                        sortie[i] = s / 9.0D;
                    }
                }
                System.arraycopy(sortie, 0, champ, 0, sortie.length);
            }
        }

        // -------------------------------------------------------------- APPLICATION

        private void pasApplication() {
            try {
                pasApplicationGuarde();
            } catch (Throwable t) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE] T479 raccord interrompu, on passe a la pose :");
                deepluckyblock.util.DebugLog.trace(t);
                finirApplication();
            }
        }

        private void finirApplication() {
            if (deepluckyblock.util.DebugLog.ENABLED)
                deepluckyblock.util.DebugLog.out("[DLB-LAKE] T479 raccord : " + colonnesRabotees
                        + " colonnes rabotees, " + colonnesRemblayees + " remblayees, "
                        + blocsEcrits + " blocs ecrits, " + trousCombles + " trous de releve combles, "
                        + colonnesInventees + " colonnes SANS releve laissees brutes, "
                        + chunksManquants + " chunks jamais obtenus, " + chunksTardifs
                        + " rattrapes au rebalayage, en "
                        + (System.currentTimeMillis() - t0) + " ms");
            // T486 : un raccord incomplet est un defaut VISIBLE en jeu. Le taire derriere
            // un log de debogage desactive revient a livrer une marche sans le dire.
            if (colonnesInventees > 0 || chunksManquants > 0)
                announceLakeProgress(level, "terrain blended, but " + chunksManquants
                        + " chunks never loaded and " + colonnesInventees
                        + " columns were left raw (no survey)");
            Runnable r = finApplication;
            finApplication = null;
            if (r != null) r.run();
        }

        private void pasApplicationGuarde() {
            if (System.currentTimeMillis() - t0 > TALUS_DEADLINE_MS) {
                announceLakeProgress(level, "blending timed out, pasting now...");
                finirApplication();
                return;
            }
            deepluckyblock.util.ChunkKeeper.keep(level);
            long deadline = System.nanoTime() + 40_000_000L;
            int total = ccw * ccd;
            while (curseur < total && System.nanoTime() < deadline) {
                if (!chunkFait[curseur]) {
                    int cx = ccx0 + (curseur % ccw), cz = ccz0 + (curseur / ccw);
                    var ca = level.getChunkSource().getChunkNow(cx, cz);
                    if (ca instanceof net.minecraft.world.level.chunk.LevelChunk lc) {
                        appliquerChunk(lc);
                        chunkFait[curseur] = true;
                        if (balayage > 0) chunksTardifs++;
                    } else {
                        deepluckyblock.util.SafeSurface.reRequest(level, cx, cz);
                    }
                }
                curseur++;
            }
            if (System.currentTimeMillis() - dernierLogMs >= 30_000L) {
                dernierLogMs = System.currentTimeMillis();
                announceLakeProgress(level, "blending terrain (" + curseur + "/" + total + ")...");
            }
            if (curseur < total) {
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 1, this::pasApplication);
                return;
            }
            // T486 : fin de balayage. Tant qu'il manque un chunk, le keeper le pompe et
            // on repasse -- la deadline reste le seul garde-fou. Sans cette reprise les
            // 60 chunks du sud arrives en cours de route n'etaient jamais raccordes.
            int restants = 0;
            for (boolean b : chunkFait) if (!b) restants++;
            if (balayage == 0 || restants < chunksManquants) balayagesSteriles = 0;
            else balayagesSteriles++;
            chunksManquants = restants;
            balayage++;
            if (restants > 0 && balayagesSteriles < TALUS_STAGNATION_MAX) {
                curseur = 0;
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 1, this::pasApplication);
                return;
            }
            finirApplication();
        }

        /**
         * Ecriture. La hauteur voulue vient du tableau {@code cible}, jamais du monde :
         * aucune heightmap n'est relue ici. Le monde n'est consulte que pour savoir ou se
         * trouve la surface a modifier, ce qui ne peut pas influer sur la cible -- c'est
         * la difference exacte avec le lissage T459-T468, qui recalculait sa consigne a
         * partir de ce qu'il venait d'ecrire.
         */
        private void appliquerChunk(net.minecraft.world.level.chunk.LevelChunk chunk) {
            int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
            var sections = chunk.getSections();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int plafond = level.getMaxBuildHeight() - 2;
            int socle = level.getMinBuildHeight() + 1;
            boolean[] secTouchee = new boolean[sections.length];
            final int wRampe = TALUS_LARGEUR * 3;
            boolean rien = true;
            for (int lz = 0; lz < 16; lz++) {
                int wz = baseZ + lz;
                if (wz < rz0 || wz > rz1) continue;
                for (int lx = 0; lx < 16; lx++) {
                    int wx = baseX + lx;
                    if (wx < rx0 || wx > rx1) continue;
                    int i = idx(wx, wz);
                    if (i < 0 || empreinte[i]) continue;   // l'ouvrage est intouchable
                    // T486 : sol naturel jamais releve ni comble par un voisin reel. Y
                    // appliquer la pente reviendrait a raboter vers groundY un terrain
                    // dont on ignore l'altitude.
                    if (inventee != null && inventee[i]) continue;
                    // Au-dela de la rampe la cible EST le terrain naturel : rien a faire,
                    // et surtout rien a abimer. Sans ce test on reecrirait tout l'anneau.
                    if (distance[i] >= wRampe) continue;
                    int but = cible[i];
                    int haut = Math.min(plafond, Math.max(but, Math.max(solNaturel[i], sommetVeg[i])));
                    // Ou est la surface actuelle ? Balayage local borne, sans heightmap.
                    // Le plancher du balayage borne AUSSI le remblai : si rien de solide
                    // n'est trouve dans la fenetre, on remblaie depuis ce plancher et pas
                    // depuis la bedrock -- sinon une colonne d'eau profonde se remplirait
                    // de terre sur cent blocs.
                    int bas = Math.max(socle, but - TALUS_REMBLAI_MAX);
                    int cur = bas - 1;
                    for (int y = haut; y >= bas; y--) {
                        BlockState st = etat(sections, chunk, lx, y, lz);
                        if (st != null && !st.isAir() && st.blocksMotion()) { cur = y; break; }
                    }
                    if (cur == but) continue;
                    if (cur > but) {
                        for (int y = but + 1; y <= haut; y++) {
                            int si = chunk.getSectionIndex(y);
                            if (si < 0 || si >= sections.length) continue;
                            var sec = sections[si];
                            if (sec == null) continue;
                            BlockState prev = sec.getStates().get(lx, y & 15, lz);
                            if (prev.isAir() || prev.getBlock() == Blocks.BEDROCK) continue;
                            if (prev.hasBlockEntity()) chunk.removeBlockEntity(p.set(wx, y, wz).immutable());
                            sec.getStates().set(lx, y & 15, lz, air);
                            secTouchee[si] = true; rien = false; blocsEcrits++;
                        }
                        colonnesRabotees++;
                    } else {
                        BlockState mat = cur >= bas ? etat(sections, chunk, lx, cur, lz) : null;
                        if (mat == null || mat.isAir() || !mat.blocksMotion())
                            mat = Blocks.DIRT.defaultBlockState();
                        for (int y = cur + 1; y <= Math.min(plafond, but); y++) {
                            int si = chunk.getSectionIndex(y);
                            if (si < 0 || si >= sections.length) continue;
                            var sec = sections[si];
                            if (sec == null) continue;
                            BlockState prev = sec.getStates().get(lx, y & 15, lz);
                            if (prev.getBlock() == Blocks.BEDROCK) continue;
                            if (prev.hasBlockEntity()) chunk.removeBlockEntity(p.set(wx, y, wz).immutable());
                            sec.getStates().set(lx, y & 15, lz, mat);
                            secTouchee[si] = true; rien = false; blocsEcrits++;
                        }
                        colonnesRemblayees++;
                    }
                }
            }
            if (rien) return;
            for (int si = 0; si < sections.length; si++) {
                if (!secTouchee[si]) continue;
                var sec = sections[si];
                sec.recalcBlockCounts();
                level.getChunkSource().getLightEngine().updateSectionStatus(
                        net.minecraft.core.SectionPos.of(chunk.getPos(),
                                chunk.getSectionYFromSectionIndex(si)), sec.hasOnlyAir());
            }
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk,
                    java.util.EnumSet.of(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE));
            chunk.setUnsaved(true);
            level.getChunkSource().getLightEngine().tryScheduleUpdate();
            // T488 : une ecriture directe de palette ne previent AUCUN client. Sans ce
            // marquage le joueur continue d'afficher l'ancien terrain la ou le serveur
            // a deja pose de l'air -- et casser un de ces blocs fantomes le fait
            // « revenir comme un rollback » (le client annule sa prediction et revient
            // a l'etat perime qu'il a en cache). Voir broadcast en fin de chantier.
            StructureTerrainPrep.markChunkModified(chunk);
        }

        /** Lecture d'un etat par la palette, sans passer par une heightmap. */
        private BlockState etat(net.minecraft.world.level.chunk.LevelChunkSection[] sections,
                                net.minecraft.world.level.chunk.LevelChunk chunk,
                                int lx, int y, int lz) {
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= sections.length) return null;
            var sec = sections[si];
            if (sec == null) return null;
            return sec.getStates().get(lx, y & 15, lz);
        }
    }

    private static final class LakeClearJob {
        final ServerLevel level;
        final BlockPos min, max;
        /** T482 : profondeur de creusement par colonne (forme reelle de l'ouvrage). */
        final MasqueClear masque;
        final Runnable onReady;
        /** Plancher de chaque colonne du chunk courant, {@link Integer#MAX_VALUE} = colonne a ne pas toucher. */
        private final int[] planCol = new int[256];
        long colonnesCreusees, colonnesEpargnees;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int cx0, cz0, cx1, cz1;
        int cx, cz;
        long cleared;
        int chunksDone;
        // T234 : bornes anti-blocage (jamais d'attente infinie d'un chunk).
        final int totalChunks;
        final long t0;
        long blockedSinceMs;
        int blockedCx = Integer.MIN_VALUE, blockedCz = Integer.MIN_VALUE;
        long lastProgressLogMs;

        LakeClearJob(ServerLevel level, BlockPos min, BlockPos max, MasqueClear masque, Runnable onReady) {
            this.level = level; this.min = min; this.max = max;
            this.masque = masque; this.onReady = onReady;
            cx0 = min.getX() >> 4; cz0 = min.getZ() >> 4;
            cx1 = max.getX() >> 4; cz1 = max.getZ() >> 4;
            cx = cx0; cz = cz0;
            totalChunks = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
            t0 = System.currentTimeMillis();
            lastProgressLogMs = t0;
        }

        /**
         * T101 : CLEAR OPTIMISE (consigne dev 29/09 : « ta technique de clear de
         * chunks n'est PAS DU TOUT optimisee, surtout pour crimsonlake -- vois en
         * ligne le plus opti, pour eviter d'avoir les blocs d'air et etre opti,
         * comprendre tous les autres, non solides, liquides etc, mais pas l'air »).
         *
         * Methode = celle des outils rapides du monde (FAWE/WorldEdit) :
         * on n'appelle PLUS JAMAIS level.setBlock() bloc par bloc. Chaque
         * setBlock declenchait pour CHAQUE bloc : verification de voisins,
         * mise a jour de heightmap (1 scan vertical) et repropagation de
         * lumiere -- soit ~30+ unites de travail par bloc sur un volume de
         * centaines de milliers de blocs. Desormais :
         *   1. on ecrit directement dans le PalettedContainer de la section
         *      (stockage brut du chunk, z = y*256+z*16+x) : AUCUNE notification
         *      de voisin, AUCUN tick de fluide (liquides effaces silencieusement,
         *      exactement ce que demande la consigne : « non solides, liquides
         *      etc »), AUCUNE mise a jour de lumiere par bloc ;
         *   2. « mais pas l'air » : un bloc DEJA air n'est JAMAIS reecrit
         *      (0 cout), et une section entierement vide est saute entierement ;
         *   3. les blocs-entites (coffres, panneaux...) du volume sont d'abord
         *      liberes proprement, sinon ils resteraient en fantome tickants ;
         *   4. UNE FOIS PAR CHUNK (au lieu de par bloc) : recomptage des blocs
         *      par section (recalcBlockCounts), heightmaps re-amorcees
         *      (primeHeightmaps), lumiere re-planifiee (updateSectionStatus +
         *      tryScheduleUpdate) et chunk marque a sauvegarder.
         * T482 : le plancher n'est plus unique (groundY + 1, plancher T93) mais
         * PROPRE A CHAQUE COLONNE, pris dans {@link MasqueClear} ; une colonne hors
         * ouvrage n'est pas touchee. La bedrock reste intouchable comme avant.
         * Si un chunk de la boite n'est pas un
         * LevelChunk (ProtoChunk en cours de worldgen -- ne devrait pas arriver
         * dans la zone epinglee), on replie sur l'ancien chemin setBlock pour
         * ce chunk-la seulement : securite d'abord, vitesse ensuite.
         */
        void step() {
            // T234 : un clear de casse NE DOIT JAMAIS mourir en silence ni
            // tourner a l'infini. Toute exception devient un abandon propre.
            try {
                stepGuarded();
            } catch (Throwable t) {
                deepluckyblock.util.DebugLog.err("[DLB-LAKE] CLEAR a plante en mais ne bouclera plus (cause ci-dessous) :");
                deepluckyblock.util.DebugLog.trace(t);
                abortLakeBuild(level, "exception pendant le clear : " + t);
            }
        }

        private void stepGuarded() {
            long nowMs = System.currentTimeMillis();
            // T234 — borne globale de construction : couper AVANT le garde-fou
            // ChunkKeeper (600 s) eut ete ; le garde-fou relachait la zone puis
            // le clear attendait ses chunks A JAMAIS (log du 11/10, 1 h 30).
            if (lakeBuildT0Ms > 0 && nowMs - lakeBuildT0Ms > LAKE_TOTAL_DEADLINE_MS) {
                abortLakeBuild(level, "clear toujours en cours a la deadline globale ("
                        + (nowMs - lakeBuildT0Ms) / 1000 + " s)");
                return;
            }
            deepluckyblock.util.ChunkKeeper.keep(level);
            long deadline = System.nanoTime() + 40_000_000L;   // 40 ms : un chunk ~1-3 ms
            while (cx <= cx1 && System.nanoTime() < deadline) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    // T234 : attente trackee et BORNEE du chunk manquant. Avant,
                    // cette boucle replanifiait ce chunk a l'infini, en silence,
                    // meme apres le relache par garde-fou du ChunkKeeper.
                    if (blockedCx != cx || blockedCz != cz) {
                        blockedCx = cx; blockedCz = cz;
                        blockedSinceMs = System.currentTimeMillis();
                    } else {
                        long waiting = System.currentTimeMillis() - blockedSinceMs;
                        if (waiting > LAKE_CLEAR_CHUNK_WAIT_MS) {
                            abortLakeBuild(level, "chunk [" + cx + "," + cz + "] toujours absence apres "
                                    + waiting / 1000 + " s d'attente (clear " + chunksDone + "/" + totalChunks + ")");
                            return;
                        }
                        if (System.currentTimeMillis() - lastProgressLogMs >= 10_000L) {
                            lastProgressLogMs = System.currentTimeMillis();
                            announceLakeProgress(level, "waiting for terrain chunk [" + cx + "," + cz + "] ("
                                    + waiting / 1000 + "s, clear " + chunksDone + "/" + totalChunks + ")...");
                            // Relance complete : le maintien keeper pompe deja
                            // l'emprise ; on re-emet explicitement cette tete.
                            deepluckyblock.util.ChunkKeeper.keep(level);
                        }
                    }
                    deepluckyblock.util.SafeSurface.reRequest(level, cx, cz);
                    break;
                }
                blockedCx = Integer.MIN_VALUE; blockedCz = Integer.MIN_VALUE; blockedSinceMs = 0L;
                if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk lc) {
                    cleared += clearChunkDirect(lc);
                } else {
                    cleared += clearChunkPerBlock(chunk);
                }
                chunksDone++;
                if (++cz > cz1) { cz = cz0; cx++; }
            }
            // T234 : compte-rendu de progression borne (chat + console),
            // ~1 toutes les 20-30 s, jamais de silence.
            if (cx <= cx1 && System.currentTimeMillis() - lastProgressLogMs >= 30_000L) {
                lastProgressLogMs = System.currentTimeMillis();
                announceLakeProgress(level, "clearing " + chunksDone + "/" + totalChunks + " chunks ("
                        + cleared + " blocks removed)...");
            }
            if (cx <= cx1) {
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 1, this::step);
            } else {
                if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[DLB-LAKE] CLEAR COMPLETE (T101 direct-sections): " + cleared
                        + " blocks removed on " + chunksDone + " chunks; paste may start"
                        + " -- T482 colonnes creusees " + colonnesCreusees
                        + ", epargnees (hors forme du lac) " + colonnesEpargnees);
                if (onReady != null) onReady.run();
            }
        }

        /** Chemin rapide : ecriture directe de palette, voir T101. Renvoie le nombre de blocs retires. */
        private long clearChunkDirect(net.minecraft.world.level.chunk.LevelChunk chunk) {
            long n = 0;
            int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
            int lx0 = Math.max(0, min.getX() - baseX), lx1 = Math.min(15, max.getX() - baseX);
            int lz0 = Math.max(0, min.getZ() - baseZ), lz1 = Math.min(15, max.getZ() - baseZ);
            if (lx0 > lx1 || lz0 > lz1) return 0;
            // T482 : un plancher PAR COLONNE. Une colonne ou l'ouvrage ne pose rien
            // garde Integer.MAX_VALUE et n'est pas touchee : c'est ce qui fait suivre
            // au trou la forme du lac au lieu de son rectangle englobant.
            final int solMin = level.getMinBuildHeight() + 1;
            int yTop = level.getMaxBuildHeight() - 2;
            int yBot = Integer.MAX_VALUE;
            java.util.Arrays.fill(planCol, Integer.MAX_VALUE);
            for (int lz = lz0; lz <= lz1; lz++)
                for (int lx = lx0; lx <= lx1; lx++) {
                    int p = masque.plancherDe(baseX + lx, baseZ + lz);
                    if (p == MasqueClear.ABSENTE) { colonnesEpargnees++; continue; }
                    p = Math.max(p, solMin);
                    planCol[(lz << 4) | lx] = p;
                    if (p < yBot) yBot = p;
                    colonnesCreusees++;
                }
            if (yBot == Integer.MAX_VALUE || yBot > yTop) return 0;   // aucune colonne d'ouvrage ici
            BlockState air = Blocks.AIR.defaultBlockState();
            // 1) blocs-entites du volume : liberes AVANT toute ecriture palette.
            java.util.List<BlockPos> kill = null;
            for (BlockPos be : chunk.getBlockEntities().keySet()) {
                if (be.getY() > yTop
                        || be.getX() < baseX + lx0 || be.getX() > baseX + lx1
                        || be.getZ() < baseZ + lz0 || be.getZ() > baseZ + lz1) continue;
                if (be.getY() < planCol[((be.getZ() - baseZ) << 4) | (be.getX() - baseX)]) continue;
                if (kill == null) kill = new ArrayList<>();
                kill.add(be.immutable());
            }
            if (kill != null) for (BlockPos bp : kill) level.removeBlockEntity(bp);
            // 2) ecriture palette directe, section par section.
            var sections = chunk.getSections();
            int i0 = Math.max(0, chunk.getSectionIndex(yBot));
            int i1 = Math.min(sections.length - 1, chunk.getSectionIndex(yTop));
            boolean anyTouched = false;
            for (int i = i0; i <= i1; i++) {
                net.minecraft.world.level.chunk.LevelChunkSection sec = sections[i];
                if (sec == null || sec.hasOnlyAir()) continue;      // « eviter d'avoir les blocs d'air »
                int secY = chunk.getSectionYFromSectionIndex(i);
                int sy = secY << 4;
                int ly0 = Math.max(sy, yBot) - sy, ly1 = Math.min(sy + 15, yTop) - sy;
                if (ly0 > ly1) continue;
                var states = sec.getStates();
                boolean touched = false;
                for (int ly = ly0; ly <= ly1; ly++)
                    for (int lz = lz0; lz <= lz1; lz++)
                        for (int lx = lx0; lx <= lx1; lx++) {
                            // T482 : sous le plancher de SA colonne, on ne touche a rien
                            // (terre porteuse) ; hors ouvrage le plancher vaut MAX_VALUE.
                            if (sy + ly < planCol[(lz << 4) | lx]) continue;
                            BlockState prev = states.get(lx, ly, lz);
                            if (prev.isAir() || prev.getBlock() == Blocks.BEDROCK) continue;
                            // T175 : supprimer également le NBT différé des block
                            // entities avant l'écriture palette directe. Sinon un
                            // prochain chargement recrée hopper/comparator sur AIR.
                            if (prev.hasBlockEntity())
                                chunk.removeBlockEntity(new BlockPos(baseX + lx, sy + ly, baseZ + lz));
                            states.set(lx, ly, lz, air);
                            touched = true; n++;
                        }
                if (touched) {
                    anyTouched = true;
                    sec.recalcBlockCounts();
                    // Lumiere : la section a change en contournant setBlock ->
                    // reevaluation FORCEE (sinon zones noires / soleil gele).
                    level.getChunkSource().getLightEngine().updateSectionStatus(
                            net.minecraft.core.SectionPos.of(chunk.getPos(), secY), sec.hasOnlyAir());
                }
            }
            // 3) une seule fois par chunk : heightmaps + flush lumiere + sauvegarde.
            if (anyTouched) {
                net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk,
                        java.util.EnumSet.of(
                                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE));
                chunk.setUnsaved(true);
                // T488 : meme dette que le talus -- le clear ecrit en palette sans
                // prevenir les clients. Marque ici, diffuse en fin de chantier.
                StructureTerrainPrep.markChunkModified(chunk);
                level.getChunkSource().getLightEngine().tryScheduleUpdate();
            }
            return n;
        }

        /** Repli (ProtoChunk ou acces atypique) : ancien chemin, colonne par colonne, setBlock. */
        private long clearChunkPerBlock(net.minecraft.world.level.chunk.ChunkAccess chunk) {
            long n = 0;
            int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
            int lx0 = Math.max(0, min.getX() - baseX), lx1 = Math.min(15, max.getX() - baseX);
            int lz0 = Math.max(0, min.getZ() - baseZ), lz1 = Math.min(15, max.getZ() - baseZ);
            if (lx0 > lx1 || lz0 > lz1) return 0;
            final int solMin = level.getMinBuildHeight() + 1;
            for (int lx = lx0; lx <= lx1; lx++)
                for (int lz = lz0; lz <= lz1; lz++) {
                    // T482 : meme masque par colonne que le chemin rapide.
                    int yBot = masque.plancherDe(baseX + lx, baseZ + lz);
                    if (yBot == MasqueClear.ABSENTE) continue;
                    yBot = Math.max(yBot, solMin);
                    int top = Math.min(level.getMaxBuildHeight() - 1,
                            chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, lx, lz));
                    for (int y = yBot; y <= top; y++) {
                        BlockState state = chunk.getBlockState(pos.set(baseX + lx, y, baseZ + lz));
                        if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FAST_FLAG);
                            n++;
                        }
                    }
                }
            return n;
        }
    }

    /**
     * T48 : pose UN bloc du lac. Ne bloque JAMAIS : si le chunk n'est pas en
     * memoire, on rend false et l'appelant differe le bloc (la demande de
     * chargement part en tache de fond).
     *
     * <p>AVANT : `getChunkSource().getChunk(cx, cz, true)` pour CHAQUE bloc --
     * generation FULL synchrone, ~15 s par chunk mesure en jeu, « Can't keep up!
     * Running 26381 ms ». C'etait la plus grosse source de freeze du mod.
     */
    private static void flushLakeChunk(PasteJob j) {
        if (j.lastChunk != null && j.lastChunkTouched) {
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(j.lastChunk, LAKE_HEIGHTMAPS);
            j.lastChunk.setUnsaved(true);
            j.level.getChunkSource().getLightEngine().tryScheduleUpdate();
            j.lastChunkTouched = false;
        }
    }

    private static boolean placeCrimsonOne(PasteJob j, int i, StructurePlaceSettings rs, BlockPos.MutableBlockPos bp) {
        var info = j.blocks.get(i);
        // T303-PERF : CrimsonLake est pose sans rotation ni miroir -> evite 1,36 M d'allocations calculateRelativePosition.
        BlockPos p = info.pos();
        int wx = j.origin.getX() + p.getX(), wy = j.origin.getY() + p.getY(), wz = j.origin.getZ() + p.getZ();
        if (j.level.isOutsideBuildHeight(wy)) return true; // T313 : garde hors limites Y de la dimension
        bp.set(wx, wy, wz);
        int cx = wx >> 4, cz = wz >> 4;
        if (cx != j.lastCx || cz != j.lastCz) {
            flushLakeChunk(j);
            j.lastCx = cx; j.lastCz = cz;
            j.lastLoaded = deepluckyblock.util.SafeSurface.loaded(j.level, cx, cz);
            j.lastChunk = j.lastLoaded ? j.level.getChunkSource().getChunkNow(cx, cz) : null;
        }
        if (!j.lastLoaded || j.lastChunk == null) {
            deepluckyblock.util.SafeSurface.request(j.level, cx, cz);
            j.lastCx = Integer.MIN_VALUE;
            j.lastChunk = null;
            return false;
        }
        BlockState target = info.state();
        if (!target.hasBlockEntity()) {
            // T303-PERF (modele FAWE PaperweightGetBlocks + T101 clearChunkDirect) :
            // ecriture directe dans LevelChunkSection + heightmaps primees 1 seule fois par chunk
            // (304 appels au lieu de 1 359 489 recalculs de 4 heightmaps).
            net.minecraft.world.level.chunk.LevelChunk chunk = j.lastChunk;
            var sections = chunk.getSections();
            int secIdx = chunk.getSectionIndex(wy);
            if (secIdx < 0 || secIdx >= sections.length) return true;
            net.minecraft.world.level.chunk.LevelChunkSection sec = sections[secIdx];
            if (sec == null) return true;
            boolean wasOnlyAir = sec.hasOnlyAir();
            BlockState old = sec.setBlockState(wx & 15, wy & 15, wz & 15, target, false);
            if (old != target) {
                if (old.hasBlockEntity()) chunk.removeBlockEntity(bp);
                if (wasOnlyAir != sec.hasOnlyAir()) {
                    j.level.getChunkSource().getLightEngine().updateSectionStatus(
                            net.minecraft.core.SectionPos.of(chunk.getPos(), wy >> 4), sec.hasOnlyAir());
                }
                if (net.minecraft.world.level.lighting.LightEngine.hasDifferentLightProperties(
                        chunk, bp, old, target)) {
                    j.level.getChunkSource().getLightEngine().checkBlock(bp);
                }
                j.level.getChunkSource().blockChanged(bp);
                j.lastChunkTouched = true;
            }
        } else {
            j.level.setBlock(bp, target, FAST_FLAG);
            j.lastChunkTouched = true;
        }
        if (info.nbt() != null) {
            var be = j.level.getBlockEntity(bp);
            if (be != null) {
                CompoundTag tag = info.nbt().copy();
                tag.putInt("x", bp.getX()); tag.putInt("y", bp.getY()); tag.putInt("z", bp.getZ());
                be.loadWithComponents(tag, j.level.registryAccess());
            }
        }
        return true;
    }

    /**
     * T303-PERF : Regroupement lineaire O(N) par chunk monde (bucket counting sans Comparator ni boxe,
     * respectant T175) : garantit que tous les blocs d'un meme chunk (cx, cz) sont contigus (~6 ms sur 1,36 M blocs).
     */
    private static List<StructureTemplate.StructureBlockInfo> bucketByWorldChunk(
            List<StructureTemplate.StructureBlockInfo> blocks, int ox, int oz, int sx, int sz) {
        int n = blocks.size();
        if (n <= 1) return blocks;
        int cx0 = ox >> 4, cz0 = oz >> 4;
        int cx1 = (ox + Math.max(0, sx - 1)) >> 4, cz1 = (oz + Math.max(0, sz - 1)) >> 4;
        int w = cx1 - cx0 + 1, d = cz1 - cz0 + 1;
        int buckets = w * d;
        if (buckets <= 1 || buckets > 16384) return blocks;
        int[] counts = new int[buckets + 1];
        for (int i = 0; i < n; i++) {
            BlockPos p = blocks.get(i).pos();
            int bx = ((ox + p.getX()) >> 4) - cx0;
            int bz = ((oz + p.getZ()) >> 4) - cz0;
            int b = (bx >= 0 && bx < w && bz >= 0 && bz < d) ? (bx * d + bz) : buckets;
            counts[b]++;
        }
        int[] offsets = new int[buckets + 1];
        int sum = 0;
        for (int b = 0; b <= buckets; b++) {
            offsets[b] = sum;
            sum += counts[b];
        }
        StructureTemplate.StructureBlockInfo[] out = new StructureTemplate.StructureBlockInfo[n];
        for (int i = 0; i < n; i++) {
            var info = blocks.get(i);
            BlockPos p = info.pos();
            int bx = ((ox + p.getX()) >> 4) - cx0;
            int bz = ((oz + p.getZ()) >> 4) - cz0;
            int b = (bx >= 0 && bx < w && bz >= 0 && bz < d) ? (bx * d + bz) : buckets;
            out[offsets[b]++] = info;
        }
        return java.util.Arrays.asList(out);
    }

    private static List<StructureTemplate.StructureBlockInfo> orderByY(List<StructureTemplate.StructureBlockInfo> blocks) {
        List<StructureTemplate.StructureBlockInfo> sorted = new ArrayList<>(blocks);
        sorted.sort(java.util.Comparator
                .comparingInt((StructureTemplate.StructureBlockInfo b) -> b.pos().getX() >> 4)
                .thenComparingInt(b -> b.pos().getZ() >> 4)
                .thenComparingInt(b -> b.pos().getY())
                .thenComparingInt(b -> b.pos().getX())
                .thenComparingInt(b -> b.pos().getZ()));
        return sorted;
    }

    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.StructureBlockInfo> exBlocks(StructureTemplate t) {
        try {
            for (Field f : StructureTemplate.class.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(t);
                if (val instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof StructureTemplate.Palette) {
                    return ((StructureTemplate.Palette) list.get(0)).blocks();
                }
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    private static boolean isLaggy(BlockState s) {
        return s.is(Blocks.TRIPWIRE)
            || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.WHITE_CARPET) || s.is(Blocks.ORANGE_CARPET)
            || s.is(Blocks.MAGENTA_CARPET) || s.is(Blocks.LIGHT_BLUE_CARPET) || s.is(Blocks.YELLOW_CARPET)
            || s.is(Blocks.LIME_CARPET) || s.is(Blocks.PINK_CARPET) || s.is(Blocks.GRAY_CARPET)
            || s.is(Blocks.LIGHT_GRAY_CARPET) || s.is(Blocks.CYAN_CARPET) || s.is(Blocks.PURPLE_CARPET)
            || s.is(Blocks.BLUE_CARPET) || s.is(Blocks.BROWN_CARPET) || s.is(Blocks.GREEN_CARPET)
            || s.is(Blocks.RED_CARPET) || s.is(Blocks.BLACK_CARPET);
    }

    private static boolean isWorldTriggered(ServerLevel overworld) {
        try {
            return stateOf(overworld).triggered;
        } catch (Exception e) { return false; }
    }

    private static void markWorldTriggered(ServerLevel overworld) {
        try { stateOf(overworld).mark(); } catch (Exception ignored) {}
    }

    private static CrimsonLakeState stateOf(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CrimsonLakeState::new, CrimsonLakeState::load), "crimson_lake_state");
    }

    public static class CrimsonLakeState extends SavedData {
        public boolean triggered = false;
        public void mark() { triggered = true; setDirty(); }
        public void clear() { triggered = false; setDirty(); }
        public static CrimsonLakeState load(CompoundTag tag, HolderLookup.Provider registries) {
            CrimsonLakeState s = new CrimsonLakeState();
            s.triggered = tag.getBoolean("triggered");
            return s;
        }
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("triggered", triggered);
            return tag;
        }
    }

    private static final class PendingBuild {
        final ServerLevel level;
        final int px, py, pz;
        // FIX T7 : non-final -- le champ est repousse tant que le NBT
        // "crimsonlake" (3,46 Mo) n'est pas charge, au lieu de bloquer le
        // thread serveur le temps du parse (voir la boucle de PENDING).
        long startTick;
        final long queuedNanos = System.nanoTime();
        PendingBuild(ServerLevel l, int x, int y, int z, long t) { level = l; px = x; py = y; pz = z; startTick = t; }
    }

    private static final class PasteJob {
        final ServerLevel level;
        final List<StructureTemplate.StructureBlockInfo> blocks;
        final BlockPos origin;
        final BlockPos bfly;
        final long t0;
        final int total;
        final int sx, sy, sz;
        int idx;
        /** T48 : blocs dont le chunk n'etait pas en memoire (repose au tick suivant). */
        final List<Integer> defer = new ArrayList<>();
        /** T53 / T303-PERF : cache direct LevelChunk + etat dirty pour primeHeightmaps unique par chunk. */
        final deepluckyblock.util.SafeSurface.ChunkMemo memo = new deepluckyblock.util.SafeSurface.ChunkMemo();
        int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        boolean lastLoaded = false;
        net.minecraft.world.level.chunk.LevelChunk lastChunk = null;
        boolean lastChunkTouched = false;
        int waitTicks;
        /** T234 : debut de l'attente des blocs differes (0 = aucune attente). */
        long deferSinceMs;
        PasteJob(ServerLevel l, List<StructureTemplate.StructureBlockInfo> b, BlockPos o, BlockPos bf, long t, int sx, int sy, int sz) {
            level = l; blocks = b; origin = o; bfly = bf; t0 = t; total = b.size();
            this.sx = sx; this.sy = sy; this.sz = sz; idx = 0;
        }
    }

    private static final class SpawnTask {
        final ServerLevel level;
        final BlockPos center;
        final int sx, sz;
        long nextTick;
        int spawned;
        final int cap;
        SpawnTask(ServerLevel l, BlockPos c, long firstTick, int cap, int sx, int sz) {
            level = l; center = c; nextTick = firstTick; spawned = 0; this.cap = cap; this.sx = sx; this.sz = sz;
        }
    }
}
