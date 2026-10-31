package deepluckyblock.compat;

import deepluckyblock.procedures.TestProcedure;

import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Injection externe de compatibilite avec les mods officiels de Laink.
 *
 * Cette classe observe le BreakEvent AVANT la destruction du BlockEntity, puis
 * TestProcedure consomme le contexte au moment ou le bloc lance normalement sa
 * roulette. DeepLuckyBlockBlock.java ne contient donc AUCUNE modification et
 * peut etre regenere par MCreator sans supprimer cette compatibilite.
 *
 * Lucky Tools, Lucky Tweaks et Lucky Stats restent facultatifs : leurs API sont
 * appelees uniquement par reflection.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class LuckyToolsCompatibility {

    /** Trace de diagnostic des equipements Lucky (anneau/ceinture/idole). Desactivee : spam a chaque bloc. */
    public static boolean DEBUG_LUCKY_TOOLS = false;


    private static final ResourceLocation DEEP_LUCKY_BLOCK_ID =
            ResourceLocation.tryParse("deep_lucky_block:deep_lucky_block");

    private record BreakKey(ResourceKey<Level> dimension, long packedPos) {}
    private record BreakContext(int effectiveLuck, boolean boosted,
                                boolean cursed, boolean beltReroll,
                                UUID playerId) {}
    private record StatsContext(UUID playerId, int createdTick) {}

    /** Contexte valable uniquement entre BreakEvent et playerDestroy, sur le meme tick. */
    private static final Map<BreakKey, BreakContext> PENDING_BREAKS =
            new ConcurrentHashMap<>();
    /** Nombre de breaks du Ring avant le passage du handler officiel HIGH. */
    private static final Map<BreakKey, Integer> RING_BREAKS_BEFORE =
            new ConcurrentHashMap<>();
    /** Ticket conserve jusqu'au resultat reel de la roulette (~quelques secondes). */
    private static final Map<BreakKey, StatsContext> PENDING_STATS =
            new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;

    private static Method tweaksGetCapturedLuck;
    private static Method tweaksGetContributedChance;

    private static Method toolsIsBoostedBreak;
    private static Method toolsIsCursedBreak;
    private static Method toolsConfirmCurseBadDrop;
    // Lucky Tools 1.2.6 ne fournit pas encore de registre public permettant a
    // un moteur externe de se declarer "cursable".
    private static Method toolsStateGet;
    private static Method starBoostedGet;
    private static Method starBoostedIsBoosted;
    private static Method starBoostedUnmark;

    private static Method curiosFindEquipped;
    private static Method curiosIsEquipped;
    private static Method ringGetBreaks;
    private static Method ringGetBonusChance;
    private static Method ringAddBreak;
    private static Item luckyRingItem;
    private static Item luckyBeltItem;

    private static Method hammerGetCharge;
    private static Method hammerSetCharge;
    private static Method hammerPlaySacrifice;
    private static Item luckyHammerItem;
    private static int hammerMaxCharge = 30;

    private static Method statsIncrementCounter;
    private static Method statsMarkBreakDeterministic;
    private static Method statsRecordDrop;
    private static Method statsGetStats;

    private LuckyToolsCompatibility() {}

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        if (ModList.get().isLoaded("luckytweaks")) {
            try {
                Class<?> api = Class.forName("com.lwi.luckytweaks.api.LuckyTweaksApi");
                tweaksGetCapturedLuck = api.getMethod("getCapturedLuck");
                tweaksGetContributedChance = api.getMethod("getContributedChance");
            } catch (Throwable ignored) {
                tweaksGetCapturedLuck = null;
                tweaksGetContributedChance = null;
            }
        }

        if (ModList.get().isLoaded("luckytools")) {
            try {
                Class<?> api = Class.forName("com.lwi.luckytools.api.LuckyToolsApi");
                toolsIsBoostedBreak = api.getMethod("isBoostedBreak");
                toolsIsCursedBreak = api.getMethod("isCursedBreak");
                toolsConfirmCurseBadDrop = api.getMethod(
                        "confirmCurseBadDrop", ServerPlayer.class);
            } catch (Throwable ignored) {
                toolsIsBoostedBreak = null;
                toolsIsCursedBreak = null;
                toolsConfirmCurseBadDrop = null;
            }

            // Fallback separe : une modification interne future de ToolsState ne
            // peut pas casser les API publiques boost/curse.
            try {
                Class<?> state = Class.forName("com.lwi.luckytools.util.ToolsState");
                toolsStateGet = state.getMethod("get", Player.class);
            } catch (Throwable ignored) {
                toolsStateGet = null;
            }

            try {
                Class<?> star = Class.forName(
                        "com.lwi.luckytools.util.StarBoostedBlocksData");
                starBoostedGet = star.getMethod("get", ServerLevel.class);
                starBoostedIsBoosted = star.getMethod("isBoosted", long.class);
                starBoostedUnmark = star.getMethod("unmark", long.class);
            } catch (Throwable ignored) {
                starBoostedGet = null;
                starBoostedIsBoosted = null;
                starBoostedUnmark = null;
            }

            try {
                luckyRingItem = BuiltInRegistries.ITEM.get(
                        ResourceLocation.tryParse("luckytools:lucky_ring"));
                luckyBeltItem = BuiltInRegistries.ITEM.get(
                        ResourceLocation.tryParse("luckytools:lucky_belt"));
                Class<?> curios = Class.forName("com.lwi.luckytools.util.CuriosHelper");
                curiosFindEquipped = curios.getMethod(
                        "findEquipped", LivingEntity.class, Item.class);
                curiosIsEquipped = curios.getMethod(
                        "isEquipped", LivingEntity.class, Item.class);

                Class<?> ring = Class.forName("com.lwi.luckytools.item.LuckyRingItem");
                ringGetBreaks = ring.getMethod("getBreaks", ItemStack.class);
                ringGetBonusChance = ring.getMethod("getBonusChance", ItemStack.class);
                ringAddBreak = ring.getMethod("addBreak", ItemStack.class);
            } catch (Throwable ignored) {
                curiosFindEquipped = null;
                curiosIsEquipped = null;
                ringGetBreaks = null;
                ringGetBonusChance = null;
                ringAddBreak = null;
            }

            try {
                luckyHammerItem = BuiltInRegistries.ITEM.get(
                        ResourceLocation.tryParse("luckytools:lucky_hammer"));
                Class<?> hammer = Class.forName("com.lwi.luckytools.item.LuckyHammerItem");
                hammerGetCharge = hammer.getMethod("getCharge", ItemStack.class);
                hammerSetCharge = hammer.getMethod("setCharge", ItemStack.class, int.class);
                hammerMaxCharge = hammer.getField("MAX_CHARGE").getInt(null);

                Class<?> handler = Class.forName("com.lwi.luckytools.LuckyHammerHandler");
                hammerPlaySacrifice = handler.getMethod(
                        "playSacrifice", ServerLevel.class, BlockPos.class, int.class);
            } catch (Throwable ignored) {
                hammerGetCharge = null;
                hammerSetCharge = null;
                hammerPlaySacrifice = null;
                hammerMaxCharge = 30;
            }
        }

        if (ModList.get().isLoaded("luckystats")) {
            try {
                Class<?> api = Class.forName("com.lwi.luckystats.api.LuckyStatsApi");
                try {
                    statsIncrementCounter = api.getMethod(
                            "incrementCounter", Player.class, String.class, int.class);
                } catch (Throwable ignored) {
                    statsIncrementCounter = null;
                }
                try {
                    statsMarkBreakDeterministic = api.getMethod("markBreakDeterministic");
                } catch (Throwable ignored) {
                    statsMarkBreakDeterministic = null;
                }
                try {
                    statsRecordDrop = api.getMethod("recordDrop",
                            ServerPlayer.class, int.class, double.class, double.class);
                } catch (Throwable ignored) {
                    statsRecordDrop = null;
                }
                try {
                    statsGetStats = api.getMethod("getStats", Player.class);
                } catch (Throwable ignored) {
                    statsGetStats = null;
                }
            } catch (Throwable ignored) {
                statsIncrementCounter = null;
                statsMarkBreakDeterministic = null;
                statsRecordDrop = null;
                statsGetStats = null;
            }
        }
    }

    private static Object invoke(Method method, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeOn(Method method, Object target, Object... args) {
        if (method == null || target == null) return null;
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeVoid(Method method, Object... args) {
        if (method == null) return false;
        try {
            method.invoke(null, args);
            return true;
        } catch (Throwable error) {
            System.err.println("[DeepLuckyBlock/LuckyStats] API call failed: " + error);
            return false;
        }
    }

    private static int reflectedInt(Method method) {
        Object value = invoke(method);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Integer reflectedNullableInt(Method method) {
        Object value = invoke(method);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static boolean reflectedBoolean(Method method) {
        return Boolean.TRUE.equals(invoke(method));
    }

    private static ItemStack findEquippedRing(ServerPlayer player) {
        if (curiosFindEquipped == null || luckyRingItem == null) return ItemStack.EMPTY;
        Object value = invoke(curiosFindEquipped, player, luckyRingItem);
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    private static boolean isBeltEquipped(ServerPlayer player) {
        if (curiosIsEquipped == null || luckyBeltItem == null) return false;
        return Boolean.TRUE.equals(invoke(curiosIsEquipped, player, luckyBeltItem));
    }

    private static int ringBreaks(ItemStack ring) {
        Object value = invoke(ringGetBreaks, ring);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int ringBonus(ItemStack ring) {
        Object value = invoke(ringGetBonusChance, ring);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int hammerCharge(ItemStack hammer) {
        Object value = invoke(hammerGetCharge, hammer);
        return value instanceof Number number ? number.intValue() : hammerMaxCharge;
    }

    /**
     * Fallback exact pour Lucky Tools 1.2.6 : son LuckyPool ne connait que les
     * pools natifs du mod Lucky Block, donc isCursedBreak() reste false pour un
     * moteur Java externe. On utilise ses propres compteurs persistants sans
     * copier ni modifier le jar de Laink.
     */
    private static boolean rollExternalCurseFallback(ServerPlayer player) {
        Object value = invoke(toolsStateGet, player);
        if (!(value instanceof CompoundTag state)) return false;

        int badRemaining = state.getInt("curse_bad");
        if (badRemaining <= 0) return false;

        int blocksRemaining = state.getInt("curse_blocks");
        boolean forceBad = blocksRemaining <= 0
                || player.serverLevel().getRandom().nextInt(blocksRemaining) < badRemaining;
        if (blocksRemaining > 0) {
            state.putInt("curse_blocks", blocksRemaining - 1);
        }
        if (forceBad) {
            invoke(statsMarkBreakDeterministic);
        }
        return forceBad;
    }

    /** Capture le compteur du Ring avant le handler officiel Lucky Tools (HIGH). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeepLuckyBlockBreakEarly(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative()) return;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (DEEP_LUCKY_BLOCK_ID == null || !DEEP_LUCKY_BLOCK_ID.equals(blockId)) return;
        if (EnchantmentHelper.getItemEnchantmentLevel(
                deepluckyblock.util.DeepEnchant.keyHolder(level.registryAccess(), Enchantments.SILK_TOUCH),
                player.getMainHandItem()) > 0) return;

        initialize();
        ItemStack ring = findEquippedRing(player);
        if (!ring.isEmpty()) {
            RING_BREAKS_BEFORE.put(
                    new BreakKey(level.dimension(), event.getPos().asLong()),
                    ringBreaks(ring));
        }
    }

    /**
     * Garantie Hammer a HIGH. Si le handler officiel passe avant, l'evenement
     * est deja annule et cette methode est ignoree ; s'il passe apres, il voit
     * notre annulation. Dans les deux ordres, un seul sacrifice est applique.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLuckyHammerBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) return;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (DEEP_LUCKY_BLOCK_ID == null || !DEEP_LUCKY_BLOCK_ID.equals(blockId)) return;

        initialize();
        ItemStack held = player.getMainHandItem();
        if (luckyHammerItem == null || !held.is(luckyHammerItem)
                || hammerGetCharge == null || hammerSetCharge == null) return;

        int charge = hammerCharge(held);
        if (charge >= hammerMaxCharge) return;

        event.setCanceled(true);
        level.destroyBlock(event.getPos(), false);
        int newCharge = Math.min(hammerMaxCharge, charge + 1);
        invoke(hammerSetCharge, held, newCharge);
        player.inventoryMenu.broadcastChanges();
        invoke(hammerPlaySacrifice, level, event.getPos(), newCharge);
        boolean ready = newCharge >= hammerMaxCharge;
        player.displayClientMessage(
                Component.translatable("message.luckytools.hammer.charge",
                        newCharge, hammerMaxCharge)
                        .withStyle(ready ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                true);
        if (ready) {
            player.sendSystemMessage(
                    Component.translatable("message.luckytools.hammer.ready")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        RING_BREAKS_BEFORE.remove(
                new BreakKey(level.dimension(), event.getPos().asLong()));
    }

    /**
     * Capture au BreakEvent NORMAL : Lucky Tweaks (HIGHEST) et Lucky Tools
     * (HIGH) ont deja rempli leurs ThreadLocal, mais le BlockEntity existe encore.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onDeepLuckyBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (DEEP_LUCKY_BLOCK_ID == null || !DEEP_LUCKY_BLOCK_ID.equals(blockId)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // Le Silk Touch ne lance pas de roulette. Lucky Tools transporte lui-meme
        // Luck + flags gambled/scanned/boosted vers l'ItemStack et la future pose.
        if (EnchantmentHelper.getItemEnchantmentLevel(
                deepluckyblock.util.DeepEnchant.keyHolder(level.registryAccess(), Enchantments.SILK_TOUCH),
                player.getMainHandItem()) > 0) return;

        initialize();
        BreakKey breakKey = new BreakKey(level.dimension(), event.getPos().asLong());

        int storedLuck = 0;
        int slbBonus = 0;
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (blockEntity != null) {
            CompoundTag data = blockEntity.saveWithoutMetadata(level.registryAccess());
            storedLuck = data.getInt("Luck");
            slbBonus = data.getInt("SlbLuckBonus");
        }

        Integer capturedLuck = reflectedNullableInt(tweaksGetCapturedLuck);
        int baseLuck = capturedLuck != null ? capturedLuck : storedLuck;

        ItemStack ring = findEquippedRing(player);
        Integer breaksBefore = RING_BREAKS_BEFORE.remove(breakKey);
        if (!ring.isEmpty() && breaksBefore != null
                && ringBreaks(ring) <= breaksBefore && ringAddBreak != null) {
            // Le handler officiel n'a pas incremente ce Ring pour notre bloc :
            // fallback anti-regression, une seule progression par break.
            invoke(ringAddBreak, ring);
        }

        int ringChance = reflectedInt(tweaksGetContributedChance);
        if (ringChance == 0 && !ring.isEmpty()) {
            // Fallback si Lucky Tools n'a pas injecte le bonus via Lucky Tweaks.
            ringChance = ringBonus(ring);
        }
        int effectiveLuck = Math.max(-100,
                Math.min(100, baseLuck + slbBonus + ringChance));

        boolean beltReroll = isBeltEquipped(player);
        boolean boosted = reflectedBoolean(toolsIsBoostedBreak);
        if (!boosted && starBoostedGet != null) {
            // Fallback Idol boost : si le handler officiel n'a pas reconnu le
            // moteur externe, consomme directement la marque de cette position.
            Object starData = invoke(starBoostedGet, level);
            if (Boolean.TRUE.equals(invokeOn(
                    starBoostedIsBoosted, starData, event.getPos().asLong()))) {
                boosted = true;
                invokeOn(starBoostedUnmark, starData, event.getPos().asLong());
                invoke(statsMarkBreakDeterministic);
            }
        }
        boolean cursed = false;
        if (!boosted) {
            cursed = reflectedBoolean(toolsIsCursedBreak);
            if (!cursed) cursed = rollExternalCurseFallback(player);
        }

        // [debug desactive] Cette trace se declenchait a CHAQUE cassage de lucky
        // block portant un equipement Lucky (anneau / ceinture / idole), soit
        // en pratique a chaque bloc. Le systeme est valide, la trace ne sert
        // plus qu'a polluer la console. Passer DEBUG_LUCKY_TOOLS a true pour la
        // reactiver ponctuellement.
        if (DEBUG_LUCKY_TOOLS && (!ring.isEmpty() || beltReroll || boosted || cursed)) {
            System.out.println("[DeepLuckyBlock/LuckyTools] luck=" + effectiveLuck
                    + ", ring=" + ringChance
                    + ", belt=" + beltReroll
                    + ", idolBoost=" + boosted
                    + ", idolCurse=" + cursed);
        }

        PENDING_BREAKS.put(breakKey,
                new BreakContext(effectiveLuck, boosted, cursed,
                        beltReroll, player.getUUID()));
    }

    /**
     * Appele au tout debut de TestProcedure.execute / executeWithLuckBonus.
     * Retourne true uniquement lorsqu'un BreakEvent du Deep Lucky Block attend
     * precisement a cette position.
     */
    public static boolean consumeAndExecute(Level world, double x, double y, double z) {
        if (!(world instanceof ServerLevel level)) return false;

        BlockPos pos = BlockPos.containing(x, y, z);
        BreakKey key = new BreakKey(level.dimension(), pos.asLong());
        BreakContext context = PENDING_BREAKS.remove(key);
        if (context == null) return false;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(context.playerId());
        if (player != null && !player.isCreative()
                && !context.boosted() && !context.cursed()
                && (statsRecordDrop != null || statsGetStats != null)) {
            PENDING_STATS.put(key, new StatsContext(
                    player.getUUID(), level.getServer().getTickCount()));
        }

        TestProcedure.executeLuckyToolsOutcome(
                level, x, y, z, context.effectiveLuck(),
                context.boosted(), context.cursed(), context.beltReroll());

        if (player != null && context.boosted()) {
            // Notre outcome engine ne passe pas dans le mixin de drops natif :
            // le compteur Legendary est donc confirme une seule fois ici.
            invoke(statsIncrementCounter, player, "legendary", 1);
        }
        if (player != null && context.cursed()) {
            // Un vrai resultat RED/BLACK a ete accepte : dette de malediction -1.
            invoke(toolsConfirmCurseBadDrop, player);
        }
        return true;
    }

    /**
     * Enregistre le resultat REEL dans Lucky Stats une fois la roulette terminee.
     * Le score de tier et le percentile alimentent Average Luck, Average Luck
     * (all time) et les compteurs de tirages. Les outcomes Idol, declares
     * deterministes, n'obtiennent volontairement aucun ticket ici.
     */
    public static void recordResolvedOutcome(ServerLevel level,
                                             double x, double y, double z,
                                             double averageLuckScore) {
        BlockPos pos = BlockPos.containing(x, y, z);
        StatsContext context = PENDING_STATS.remove(
                new BreakKey(level.dimension(), pos.asLong()));
        if (context == null || (statsRecordDrop == null && statsGetStats == null)) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(context.playerId());
        if (player == null) return;

        averageLuckScore = Math.max(0.0, Math.min(100.0, averageLuckScore));

        // Lucky Stats attend directement un percentile 0..1. Notre score est
        // deja sur son echelle native 0..100 : aucune valeur negative n'entre
        // dans Average Luck ou Average Luck All-Time.
        double percentile = averageLuckScore / 100.0;
        int tier;
        if (averageLuckScore <= 12.5) tier = -3;
        else if (averageLuckScore <= 30.0) tier = -2;
        else if (averageLuckScore < 50.0) tier = -1;
        else if (averageLuckScore == 50.0) tier = 0;
        else if (averageLuckScore < 70.0) tier = 1;
        else if (averageLuckScore < 87.5) tier = 2;
        else tier = 3;

        // Alimente les DEUX stats Average Luck deja fournies par Lucky Stats.
        // L'API officielle reste prioritaire.
        boolean recorded = invokeVoid(
                statsRecordDrop, player, tier, 1.0D, percentile);

        if (!recorded) {
            // Filet de securite pour une variation de classloader/API : ecrit
            // exactement les cles natives lues par le HUD de Lucky Stats.
            Object statsObject = invoke(statsGetStats, player);
            if (statsObject instanceof CompoundTag stats) {
                int permil = (int) Math.round(
                        Math.max(0.0, Math.min(1.0, percentile)) * 1000.0);
                stats.putLong("luck_score_sum",
                        stats.getLong("luck_score_sum") + tier);
                stats.putInt("luck_score_count",
                        stats.getInt("luck_score_count") + 1);
                stats.putLong("luck_pct_permil_sum",
                        stats.getLong("luck_pct_permil_sum") + permil);
                stats.putInt("luck_pct_count",
                        stats.getInt("luck_pct_count") + 1);

                int[] recent = stats.getIntArray("luck_pct_recent");
                int[] next;
                if (recent.length < 10) {
                    next = new int[recent.length + 1];
                    System.arraycopy(recent, 0, next, 0, recent.length);
                    next[recent.length] = permil;
                } else {
                    next = new int[10];
                    System.arraycopy(recent, recent.length - 9,
                            next, 0, 9);
                    next[9] = permil;
                }
                stats.putIntArray("luck_pct_recent", next);
                recorded = true;
            }
        }

        if (recorded) {
            System.out.println("[DeepLuckyBlock/LuckyStats] base Average Luck updated: score="
                    + String.format(java.util.Locale.ROOT, "%.1f", averageLuckScore)
                    + ", percentile="
                    + String.format(java.util.Locale.ROOT, "%.1f%%", percentile * 100.0));
        }

        // Luck Deep 90..100 devient Average Luck 95..100 : grades LEGENDARY+
        // inchanges malgre la nouvelle echelle statistique.
        if (averageLuckScore >= 95.0) {
            invoke(statsIncrementCounter, player, "legendary", 1);
        }
    }

    /** Une casse annulee ne doit jamais laisser un contexte contaminer un autre tick. */
    @SubscribeEvent
    public static void onServerTickEnd(ServerTickEvent.Post event) {
        if (!PENDING_BREAKS.isEmpty()) PENDING_BREAKS.clear();
        if (!RING_BREAKS_BEFORE.isEmpty()) RING_BREAKS_BEFORE.clear();

        // Une roulette normale se termine en quelques secondes. Nettoyage de
        // secours si une exception empeche generateResult de consommer le ticket.
        int now = event.getServer().getTickCount();
        PENDING_STATS.entrySet().removeIf(entry ->
                now - entry.getValue().createdTick() > 400);
    }
}
