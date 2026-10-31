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

    public static final int MAX_LUCK_BONUS = 100;

    public static final int MIN_LUCK_BONUS = -100;

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
            int luck = DeepLuckyBlockItem.getLuckBonus(stack);

            // 1) Luck portée dans le tag racine de l'item (clé "SlbLuckBonus")
            if (luck == 0 && deepluckyblock.util.DeepNbt.hasTag(stack)) {
                CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
                if (tag != null && tag.contains(NBT_LUCK_KEY)) {
                    luck = tag.getInt(NBT_LUCK_KEY);
                }
            }

            // 2) Luck portée dans "BlockEntityTag" (bloc silk-touché).
            //    Le drop Silk Touch écrit la clé "Luck" → c'est la source principale.
            if (luck == 0 && deepluckyblock.util.DeepNbt.hasTag(stack)) {
                CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
                if (tag != null && tag.contains("BlockEntityTag")) {
                    CompoundTag beTag = tag.getCompound("BlockEntityTag");
                    if (beTag.contains("Luck")) {
                        luck = beTag.getInt("Luck");
                    } else if (beTag.contains(NBT_LUCK_KEY)) {
                        luck = beTag.getInt(NBT_LUCK_KEY);
                    }
                }
            }

            // FIX v7 (resolu, diagnostic desormais inutile en permanence) : la
            // trace [SLB-DEBUG] a servi a confirmer la valeur de luck lue dans
            // l'item au moment de la pose. Retiree pour ne plus polluer les
            // logs (voir deepluckyblock.util.DebugLog pour le remplacement
            // cible structure/coeurs).

            // 1. Stockage complet dans le BlockEntity natif ("Luck" + "SlbLuckBonus")
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DeepLuckyBlockBlockEntity dlBe) {
                dlBe.setLuck(luck);
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

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DeepLuckyBlockBlockEntity dlBe) {
            mainLuck = dlBe.getLuck();
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

        int totalLuck = mainLuck + slbBonus;

        // FIX v7 (resolu) : trace de diagnostic retiree, voir setPlacedBy ci-dessus.

        world.removeBlock(pos, false);

        if (player != null && !player.isCreative() &&
                EnchantmentHelper.getItemEnchantmentLevel(deepluckyblock.util.DeepEnchant.keyHolder(world.registryAccess(), Enchantments.SILK_TOUCH), player.getMainHandItem()) > 0) {
            ItemStack drop = new ItemStack(this);
            if (totalLuck != 0) {
                deepluckyblock.util.DeepNbt.getOrCreateTagElement(drop, "BlockEntityTag").putInt("Luck", totalLuck);
            }
            Block.popResource(world, pos, drop);
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
