package deepluckyblock;

import deepluckyblock.blockentity.DeepLuckyBlockBlockEntity;
import deepluckyblock.init.DeepLuckyBlockModBlocks;
import deepluckyblock.item.DeepLuckyBlockItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * FIX v8 — DIAGNOSTIC LUCK + COFFRES (fichier manuel, NON regénéré par MCreator).
 *
 * Ce fichier a un double role :
 *  1) Banner de version : au chargement du mod, les lignes [SLB-V8]
 *     confirment que c'est bien le code v8 qui tourne (les versions
 *     precedantes n'affichaient rien : on ne pouvait pas distinguer
 *     quels fichiers etaient en place).
 *  2) Traces de diagnostic du flux de chance et des coffres lucky via
 *     des evenements NeoForge, independantes des fichiers "elements" de
 *     MCreator (qui peuvent etre regenerees et faire perdre des edits) :
 *       - POSE   : clic droit avec le bloc en main → luck de l'item.
 *                  La luck vient de l'infusion (recette : bloc + carottes
 *                  dorées, trace [SLB-DEBUG] infusion dans le recipe) —
 *                  donc POSE=10 prouve que l'infusion A FONCTIONNE.
 *       - CASSE  : cassure du bloc → luck lue dans le BlockEntity
 *                  (ceci est la valeur utilisee par la roue de drops).
 *       - COFFRE : clic droit sur un coffre → drapeaux slb_* (rank, filled).
 *
 * Chaines attendues pour un bloc infuse +10 :
 *   [SLB-DEBUG] infusion: ... luckFinale=10
 *   [SLB-V8] POSE (avant) : item luck=10
 *   [SLB-V8] CASSE : beLuck=10
 * En jeu : holo flottant "⚡ +10% Luck Bonus!" au-dessus de la roue.
 *
 * Chercher dans le log : [SLB-V8]  et  [SLB-DEBUG].
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class SlbDiagnostics {

    /**
     * T4 (20/09) : les traces de diagnostic passaient par System.out, donc elles
     * n'apparaissaient PAS dans logs/latest.log (le log du serveur ne capture que
     * les loggers). LIRE-MOI-IMPORTANT.md demande justement de verifier
     * « [SLB-V8] » DANS le log : on passe donc tout par le logger du mod, qui
     * ecrit a la fois dans la console et dans latest.log.
     */
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final String VERSION = "v8 (2026-09-05)";

    /**
     * Traces de diagnostic POSE / CASSE / COFFRE.
     *
     * DESACTIVEES (demande utilisateur : « desactive aussi les debugs qui ne
     * nous servent plus, ceux sur le bloc pose etc »).
     *
     * Elles avaient servi a diagnostiquer le transfert de luck a la pose et le
     * bug des coffres de l'Everest -- deux sujets aujourd'hui clos et valides
     * en jeu. Elles n'apportent donc plus rien et polluent la console : 407
     * lignes [SLB-V8] dans dernierslogs6, dont une par bloc pose, ce qui noie
     * les messages reellement utiles.
     *
     * Passer a true pour les reactiver ponctuellement si un bug de luck ou de
     * coffre revenait. La banniere de version, elle, reste toujours affichee :
     * c'est elle qui confirme que le bon jar est charge.
     */
    public static boolean TRACE_BLOCKS = false;

    private SlbDiagnostics() {}

    /** Banner au chargement du mod (appee depuis le constructeur du @Mod). */
    public static void banner() {
        deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] ═══════════════════════════════════════════════════");
        deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] Deep Lucky Block " + VERSION + " charge");
        deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] - armures : material avec Layer (rendu custom NeoForge)");
        deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] - coffres : NeoForgeData + BLOCK_ENTITY_DATA + transfert a la pose");
        deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] - traces : [SLB-V8] POSE / CASSE / COFFRE dans ce log");
        deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] ═══════════════════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════
    // 1) POSE : quel bonus porte l'item qui va etre pose ?
    //    (+ COFFRE : les drapeaux slb_* sont-ils la ?)
    // ═══════════════════════════════════════════════════

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!TRACE_BLOCKS) return;   // traces de pose desactivees (voir TRACE_BLOCKS)
            if (event.getLevel().isClientSide()) return;

            ItemStack main = event.getEntity().getMainHandItem();
            if (main.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                    && blockItem.getBlock() == DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get()) {
                deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] POSE (avant) : item luck="
                        + DeepLuckyBlockItem.getLuckBonus(main)
                        + " a " + event.getPos());
            }

            BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            if (be instanceof ChestBlockEntity) {
                CompoundTag data = be.getPersistentData();
                if (data.getBoolean("slb_auto_chest")) {
                    deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] COFFRE clic : drapeaux presents rank="
                            + data.getInt("slb_chest_rank")
                            + " filled=" + data.getBoolean("slb_filled")
                            + " a " + event.getPos());
                } else {
                    deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] COFFRE clic : AUCUN drapeau slb_auto_chest a "
                            + event.getPos() + " (persistentData=" + data + ")");
                }
            }
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════════
    // 2) CASSE : quelle chance est dans le BlockEntity au moment de la casse ?
    //    (c'est CETTE valeur que la roue de drops utilise)
    // ═══════════════════════════════════════════════════

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (!TRACE_BLOCKS) return;   // traces de casse desactivees (voir TRACE_BLOCKS)
            if (event.getLevel().isClientSide()) return;
            if (event.getState().getBlock() != DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get()) return;

            BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            int beLuck = 0;
            int wandBonus = 0;
            String beType = "null";
            if (be != null) {
                beType = be.getType().toString();
                if (be instanceof DeepLuckyBlockBlockEntity dlBe) {
                    beLuck = dlBe.getLuck();
                    // FIX (faux-positif de diagnostic, PAS un bug de jeu réel --
                    // audit confirmé : "beLuck=100 persistentDataLuck=0" dans les
                    // logs) : "SlbLuckBonus" n'est PAS un miroir de la luck
                    // principale du bloc -- c'est un CHAMP SÉPARÉ, le bonus/malus
                    // externe posé par une Wand (voir DeepLuckyBlockBlockEntity
                    // Javadoc "Luck" vs "SlbLuckBonus", et
                    // DeepLuckyBlockBlock#onDestroyedByPlayer qui calcule
                    // totalLuck = mainLuck (getLuck(), ICI 100) + slbBonus
                    // (getSlbLuckBonus(), ICI 0 car aucune Wand n'a été utilisée)
                    // -- la roue de drops utilise bien totalLuck=100, PAS 0.
                    // L'ancien libellé "persistentDataLuck" laissait croire à tort
                    // que la luck de 100 posée par le joueur avait été perdue au
                    // moment de la casse ; en réalité elle était intacte dans
                    // beLuck depuis le début, et le second champ à 0 est le
                    // comportement ATTENDU (pas de Wand appliquée sur ce bloc).
                    // Lit directement le champ dédié pour refléter cette réalité.
                    wandBonus = dlBe.getSlbLuckBonus();
                } else {
                    wandBonus = be.getPersistentData().getInt("SlbLuckBonus");
                }
            }
            deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-V8] CASSE : luckPrincipale(Luck)=" + beLuck
                    + " bonusWandExterne(SlbLuckBonus)=" + wandBonus
                    + " totalUtiliseParLaRoue=" + (beLuck + wandBonus)
                    + " beType=" + beType
                    + " a " + event.getPos());
        } catch (Throwable ignored) {}
    }
}
