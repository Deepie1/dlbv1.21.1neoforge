package deepluckyblock.item;

import deepluckyblock.procedures.GenerateLuckyItemProcedure;
import deepluckyblock.procedures.TestProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Oeuf de spawn de Bob le Pecheur Eternel.
 * Spawn un zombie configure avec armure level 77, canne a peche et attributs de Bob.
 */
public class BobSpawnEggItem extends Item {

    public BobSpawnEggItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("\u00a7aBob Spawn Egg");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        spawnBob((ServerLevel) level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        Player player = context.getPlayer();
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** Spawn un Bob complet (armure 77 + attributs + canne a peche). */
    public static void spawnBob(ServerLevel level, double x, double y, double z) {
        RandomSource random = level.getRandom();
        Zombie bob = new Zombie(EntityType.ZOMBIE, level);
        bob.moveTo(x, y, z, random.nextFloat() * 360F, 0F);

        // --- Nom + persistance ---
        bob.setCustomName(Component.literal("\u00a76\u00a7lBob 'The Eternal Fisher'"));
        bob.setCustomNameVisible(true);
        bob.setPersistenceRequired();
        bob.getPersistentData().putBoolean("IsEternalBob", true);

        // --- Attributs (identiques au Bob de GenerateBad) ---
        try {
            if (bob.getAttribute(Attributes.MAX_HEALTH) != null) {
                bob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(70.0);
                bob.setHealth(70.0f);
            }
            if (bob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                bob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.23 * 1.6);
            }
            if (bob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
                bob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0);
            }
        } catch (Exception ignored) {}

        // --- Armure level 77 (via GenerateLuckyItemProcedure) ---
        TestProcedure.QualityTier tier = TestProcedure.QualityTier.getQualityTier(77);
        bob.setItemSlot(EquipmentSlot.HEAD, safeEquip(level, 77, tier, random, EquipmentSlot.HEAD));
        bob.setItemSlot(EquipmentSlot.CHEST, safeEquip(level, 77, tier, random, EquipmentSlot.CHEST));
        bob.setItemSlot(EquipmentSlot.LEGS, safeEquip(level, 77, tier, random, EquipmentSlot.LEGS));
        bob.setItemSlot(EquipmentSlot.FEET, safeEquip(level, 77, tier, random, EquipmentSlot.FEET));
        bob.setItemSlot(EquipmentSlot.MAINHAND, safeEquip(level, 77, tier, random, EquipmentSlot.MAINHAND));

        bob.setDropChance(EquipmentSlot.HEAD, 0.35f);
        bob.setDropChance(EquipmentSlot.CHEST, 0.15f);
        bob.setDropChance(EquipmentSlot.LEGS, 0.25f);
        bob.setDropChance(EquipmentSlot.FEET, 0.15f);
        bob.setDropChance(EquipmentSlot.MAINHAND, 0.15f);

        // --- Canne a peche (offhand) ---
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        rod.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("\u00a76\u00a7l\u2726 Eternal Fisher's Rod \u2726"));
        bob.setItemSlot(EquipmentSlot.OFFHAND, rod);
        bob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);

        level.addFreshEntity(bob);
    }

    /** Genere une piece d'armure level 77. Fallback fer si la generation echoue. */
    private static ItemStack safeEquip(ServerLevel level, int luck, TestProcedure.QualityTier tier,
                                        RandomSource random, EquipmentSlot slot) {
        try {
            ItemStack stack = GenerateLuckyItemProcedure.generateSpecific(level, luck, tier, random, slot);
            if (stack != null && !stack.isEmpty()) return stack;
        } catch (Exception ignored) {}
        return switch (slot) {
            case HEAD -> new ItemStack(Items.IRON_HELMET);
            case CHEST -> new ItemStack(Items.IRON_CHESTPLATE);
            case LEGS -> new ItemStack(Items.IRON_LEGGINGS);
            case FEET -> new ItemStack(Items.IRON_BOOTS);
            case MAINHAND -> new ItemStack(Items.IRON_SWORD);
            default -> new ItemStack(Items.FISHING_ROD);
        };
    }
}
