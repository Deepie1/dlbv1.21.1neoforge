package deepluckyblock.block;

import deepluckyblock.blockentity.DeepLuckyBlockBlockEntity;
import deepluckyblock.item.DeepLuckyBlockItem;
import deepluckyblock.procedures.TestProcedure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 DeepLuckyBlockBlock — Bloc avec support BlockEntity & NBT Luck
 * ═══════════════════════════════════════════════════════════════
 *
 * ⚙️ CORRECTION DES 4 BUGS CRITIQUES :
 * • Stockage NBT sur BlockEntity ("Luck" + "SlbLuckBonus") :
 *   1. COMPATIBLE WANDS/TOTEMS : Les mods externes peuvent lire et écrire les clés.
 *   2. PERSISTANT : La chance du bloc ne disparaît jamais après un redémarrage.
 *   3. MULTI-DIMENSIONS : Aucune collision entre Overworld, Nether et End.
 *   4. MALÉDICTIONS : "SlbLuckBonus" accepte les valeurs négatives (curses).
 * • Secours RAM (PLACED_LUCK) conservé uniquement en cas d'absence de BlockEntity.
 *
 * ⚠️ v2 — CORRECTIONS :
 * • Silk Touch : le drop sauvegarde la luck sous la clé "Luck" dans
 *   "BlockEntityTag" ; setPlacedBy lisait uniquement "SlbLuckBonus" →
 *   la luck du bloc silk-touché était perdue au re-placement.
 *   Désormais les deux clés sont lues ("Luck" d'abord, puis "SlbLuckBonus").
 */
public class DeepLuckyBlockBlock extends Block implements EntityBlock {

    public static final String NBT_LUCK_KEY = "SlbLuckBonus";

    /**
     * T449 : taux de retour d'un Deep Lucky Block en cassant un Deep Lucky Block.
     * Consigne utilisateur, litterale : « il faut fixer le taux de drop d'un
     * nouveau DLB a 2 % ». Valeur unique et unique porte de retour par cassage.
     */
    public static final int SELF_DROP_PERCENT = 2;

    public static final int MAX_LUCK_BONUS = 100;

    public static final int MIN_LUCK_BONUS = -100;

    /**
     * T460 : la chance est desormais FRACTIONNAIRE, par pas de 0,25.
     *
     * <p>Consigne : « je veux absolument mettre le systeme de 0,25 minimum, pas
     * 1 minimum en ajout ! on ne parle pas de % mais bien de valeur ajoutee,
     * donc un block a luck 10 + une emeraude donne block a 10.75 ».
     *
     * <p>Elle est stockee en CENTIEMES dans une cle NBT distincte. L'ancienne
     * cle entiere {@link #NBT_LUCK_KEY} continue d'etre ecrite, arrondie, pour
     * tous les lecteurs existants (wands, scripts, Lucky World Invasion) ; elle
     * sert aussi de repli pour les blocs crees avant ce changement, relus comme
     * {@code valeur x 100}. Reutiliser la cle d'origine aurait transforme un
     * bloc a +10 en bloc a +0,10.
     */
    public static final String NBT_LUCK_CENTI_KEY = "SlbLuckCenti";

    /** Pas minimal de chance, en centiemes : 0,25. */
    public static final int LUCK_STEP_CENTI = 25;

    public static final int MAX_LUCK_CENTI = MAX_LUCK_BONUS * 100;

    public static final int MIN_LUCK_CENTI = MIN_LUCK_BONUS * 100;

    private static final Map<Long, Integer> PLACED_LUCK = new ConcurrentHashMap<>();

    public DeepLuckyBlockBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.METAL)
                // Durabilité de la DIRT (0.5) + résistance explosion conservée
                .strength(0.5f, 3600000.0f)
                .lightLevel(state -> 6)
                .hasPostProcess((bs, br, bp) -> true)
                .emissiveRendering((bs, br, bp) -> true)
                .instrument(NoteBlockInstrument.BIT)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
    }

    // ═══════════════════════════════════════════════════
    // VITESSE DE MINAGE : bonus si le joueur tient une pioche
    // ═══════════════════════════════════════════════════

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float progress = super.getDestroyProgress(state, player, level, pos);
        ItemStack held = player.getMainHandItem();

        // Si l'outil est déjà "efficace" (bloc dans un tag mineable), on ne double pas le bonus
        if (held.getDestroySpeed(state) > 1.0F) {
            return progress;
        }

        if (held.getItem() instanceof PickaxeItem pickaxe) {
            // Bois=2, Pierre=4, Fer=6, Diamant=8, Netherite=9, Or=12
            progress *= pickaxe.getTier().getSpeed();
        }

        return progress;
    }

    // ═══════════════════════════════════════════════════
    // EMPÊCHER LE DROP DU BLOC (ne drop JAMAIS lui-même)
    // ═══════════════════════════════════════════════════

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.emptyList();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeepLuckyBlockBlockEntity(pos, state);
    }

    // ═══════════════════════════════════════════════════
    // QUAND LE BLOC EST POSÉ : stocke la chance dans le NBT du BlockEntity
    // ═══════════════════════════════════════════════════

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide()) {
            // T460 : tout se lit en CENTIEMES pour ne pas perdre les quarts.
            int luckCenti = DeepLuckyBlockItem.getLuckCenti(stack);

            // 1) Luck portée dans le tag racine de l'item
            if (luckCenti == 0 && deepluckyblock.util.DeepNbt.hasTag(stack)) {
                CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
                if (tag != null && tag.contains(NBT_LUCK_CENTI_KEY)) {
                    luckCenti = tag.getInt(NBT_LUCK_CENTI_KEY);
                } else if (tag != null && tag.contains(NBT_LUCK_KEY)) {
                    luckCenti = tag.getInt(NBT_LUCK_KEY) * 100;
                }
            }

            // 2) Luck portée dans "BlockEntityTag" (bloc silk-touché).
            //    Le drop Silk Touch écrit la clé "Luck" → c'est la source principale.
            if (luckCenti == 0 && deepluckyblock.util.DeepNbt.hasTag(stack)) {
                CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
                if (tag != null && tag.contains("BlockEntityTag")) {
                    CompoundTag beTag = tag.getCompound("BlockEntityTag");
                    // "LuckCenti" d'abord : c'est la seule cle qui porte les quarts.
                    if (beTag.contains("LuckCenti")) {
                        luckCenti = beTag.getInt("LuckCenti");
                    } else if (beTag.contains("Luck")) {
                        luckCenti = beTag.getInt("Luck") * 100;
                    } else if (beTag.contains(NBT_LUCK_KEY)) {
                        luckCenti = beTag.getInt(NBT_LUCK_KEY) * 100;
                    }
                }
            }
            int luck = DeepLuckyBlockItem.centiToLuck(luckCenti);

            // FIX v7 (resolu, diagnostic desormais inutile en permanence) : la
            // trace [SLB-DEBUG] a servi a confirmer la valeur de luck lue dans
            // l'item au moment de la pose. Retiree pour ne plus polluer les
            // logs (voir deepluckyblock.util.DebugLog pour le remplacement
            // cible structure/coeurs).

            // 1. Stockage complet dans le BlockEntity natif ("Luck" + "SlbLuckBonus")
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DeepLuckyBlockBlockEntity dlBe) {
                dlBe.setLuckCenti(luckCenti);   // T460 : met aussi l'entier a jour
                dlBe.setSlbLuckBonus(0);
                // FIX v7 : miroir dans getPersistentData() (NeoForgeData) pour
                // couvrir les lecteurs qui passent par le persistent data.
                be.getPersistentData().putInt("Luck", luck);
            } else if (be != null) {
                // Fallback pour une block entity étrangère (ne devrait pas arriver)
                CompoundTag beData = be.getPersistentData();
                beData.putInt("Luck", luck);          // Clé native Lucky Block
                beData.putInt("SlbLuckBonus", 0);     // Bonus/malus Wand externe
                be.setChanged();
            } else {
                // Fallback RAM uniquement si aucune block entity n'existe
                if (luck != 0) {
                    PLACED_LUCK.put(pos.asLong(), luck);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════
    // QUAND LE BLOC EST CASSÉ : lit le NBT (Luck + SlbLuckBonus de la Wand)
    // ═══════════════════════════════════════════════════

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level world, BlockPos pos,
                                       Player player, boolean willHarvest, FluidState fluid) {
        if (world.isClientSide()) {
            return super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
        }

        int mainLuck = 0;
        int slbBonus = 0;
        String forcedBranch = "";

        BlockEntity be = world.getBlockEntity(pos);
        if (be != null && be.getPersistentData().contains("dlbForcedBranch")) {
            forcedBranch = be.getPersistentData().getString("dlbForcedBranch");
        }
        int mainLuckCenti = 0;
        if (be instanceof DeepLuckyBlockBlockEntity dlBe) {
            mainLuck = dlBe.getLuck();
            mainLuckCenti = dlBe.getLuckCenti();
            slbBonus = dlBe.getSlbLuckBonus();
            // FIX v7 : fallback persistent data (miroir écrit dans setPlacedBy,
            // mondes anciens, wands...) si le champ du BE est a 0.
            if (mainLuck == 0) {
                mainLuck = be.getPersistentData().getInt("Luck");
            }
        } else if (be != null) {
            CompoundTag beData = be.getPersistentData();
            mainLuck = beData.getInt("Luck");
            slbBonus = beData.getInt("SlbLuckBonus"); // Accepte malédictions négatives et wands !
        } else {
            mainLuck = PLACED_LUCK.getOrDefault(pos.asLong(), 0);
        }

        PLACED_LUCK.remove(pos.asLong());

        // T460 : si aucun centieme n'a ete lu (BE etrangere, monde ancien, RAM),
        // on retombe sur l'entier ; sinon la fraction est conservee.
        if (mainLuckCenti == 0) mainLuckCenti = mainLuck * 100;
        int totalCenti = mainLuckCenti + slbBonus * 100;
        int totalLuck = mainLuck + slbBonus;

        // FIX v7 (resolu) : trace de diagnostic retiree, voir setPlacedBy ci-dessus.

        world.removeBlock(pos, false);

        if (player != null && !player.isCreative() &&
                EnchantmentHelper.getItemEnchantmentLevel(deepluckyblock.util.DeepEnchant.keyHolder(world.registryAccess(), Enchantments.SILK_TOUCH), player.getMainHandItem()) > 0) {
            ItemStack drop = new ItemStack(this);
            if (totalCenti != 0) {
                CompoundTag beTag = deepluckyblock.util.DeepNbt.getOrCreateTagElement(drop, "BlockEntityTag");
                // Les deux cles : "LuckCenti" porte les quarts, "Luck" reste
                // lisible par les wands et les scripts externes.
                beTag.putInt("LuckCenti", totalCenti);
                beTag.putInt("Luck", DeepLuckyBlockItem.centiToLuck(totalCenti));
                DeepLuckyBlockItem.setLuckCenti(drop, totalCenti);
            }
            Block.popResource(world, pos, drop);
            return true;
        }

        // ================================================================
        // T449 : RETOUR D'UN DEEP LUCKY BLOCK -- 2 %, PORTE UNIQUE
        // ================================================================
        // Retour utilisateur : « j'ai casse 50 DLB et j'en ai recupere plus
        // d'une vingtaine hors achievement » (~40 %). Le coupable n'etait PAS
        // un drop par cassage -- il n'en existait aucun -- mais les averses de
        // serie : onLuckyStreak versait 0-5 blocs et triggerNegativeStreak
        // 0-6, A CHAQUE serie (seuils 5 et -3), donc en boucle. Sur 50 casses
        // cela faisait bien une vingtaine de blocs rendus. Ces averses sont
        // supprimees (voir DeepLuckyBlockAdvancements) et le retour passe
        // desormais par CETTE seule porte, a taux fixe.
        if (world instanceof net.minecraft.server.level.ServerLevel dropLevel
                && dropLevel.random.nextInt(100) < SELF_DROP_PERCENT) {
            Block.popResource(world, pos, new ItemStack(this));
        }

        if (!forcedBranch.isEmpty() && world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            TestProcedure.executeForcedInfusedOutcome(serverLevel, pos.getX(), pos.getY(), pos.getZ(),
                    totalLuck, "g".equalsIgnoreCase(forcedBranch));
            return true;
        }

        if (totalLuck != 0) {
            TestProcedure.executeWithLuckBonus(world, pos.getX(), pos.getY(), pos.getZ(), totalLuck);
        } else {
            TestProcedure.execute(world, pos.getX(), pos.getY(), pos.getZ());
        }

        return true;
    }

    // ═══════════════════════════════════════════════════
    // IMMUNITÉS
    // ═══════════════════════════════════════════════════

    @Override
    public float getExplosionResistance() {
        return 3600000.0f;
    }

    @Override
    public void wasExploded(Level world, BlockPos pos, Explosion e) {
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return false;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return 0;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return true;
    }

    // ═══════════════════════════════════════════════════
    // NETTOYAGE
    // ═══════════════════════════════════════════════════

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide()) {
            PLACED_LUCK.remove(pos.asLong());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
