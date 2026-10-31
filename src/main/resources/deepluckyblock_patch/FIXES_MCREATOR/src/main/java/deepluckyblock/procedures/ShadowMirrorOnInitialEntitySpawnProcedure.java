package deepluckyblock.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ShadowMirrorOnInitialEntitySpawnProcedure {

    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (entity == null)
            return;

        Player nearestPlayer = world.getNearestPlayer(x, y, z, 20, false);

        if (nearestPlayer != null && entity instanceof LivingEntity living) {

            living.setItemSlot(EquipmentSlot.HEAD, nearestPlayer.getItemBySlot(EquipmentSlot.HEAD).copy());
            living.setItemSlot(EquipmentSlot.CHEST, nearestPlayer.getItemBySlot(EquipmentSlot.CHEST).copy());
            living.setItemSlot(EquipmentSlot.LEGS, nearestPlayer.getItemBySlot(EquipmentSlot.LEGS).copy());
            living.setItemSlot(EquipmentSlot.FEET, nearestPlayer.getItemBySlot(EquipmentSlot.FEET).copy());

            ItemStack mainHand = nearestPlayer.getMainHandItem().copy();
            if (!mainHand.isEmpty()) {
                living.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
            }

            ItemStack offHand = nearestPlayer.getOffhandItem().copy();
            if (!offHand.isEmpty()) {
                living.setItemSlot(EquipmentSlot.OFFHAND, offHand);
            }
        }
    }
}