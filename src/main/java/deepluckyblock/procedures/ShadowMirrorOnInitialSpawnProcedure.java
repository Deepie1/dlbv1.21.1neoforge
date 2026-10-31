package deepluckyblock.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ShadowMirrorOnInitialSpawnProcedure {

    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (entity == null)
            return;

        Player nearestPlayer = world.getNearestPlayer(x, y, z, 10, false);

        if (nearestPlayer != null && entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {

            ItemStack helmet = nearestPlayer.getItemBySlot(EquipmentSlot.HEAD).copy();
            livingEntity.setItemSlot(EquipmentSlot.HEAD, helmet);

            ItemStack chestplate = nearestPlayer.getItemBySlot(EquipmentSlot.CHEST).copy();
            livingEntity.setItemSlot(EquipmentSlot.CHEST, chestplate);

            ItemStack leggings = nearestPlayer.getItemBySlot(EquipmentSlot.LEGS).copy();
            livingEntity.setItemSlot(EquipmentSlot.LEGS, leggings);

            ItemStack boots = nearestPlayer.getItemBySlot(EquipmentSlot.FEET).copy();
            livingEntity.setItemSlot(EquipmentSlot.FEET, boots);
        }
    }
}