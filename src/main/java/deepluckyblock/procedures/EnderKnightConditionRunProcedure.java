package deepluckyblock.procedures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;

public class EnderKnightConditionRunProcedure {

    public static boolean execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (entity == null || world == null) return false;
        if (!(entity instanceof LivingEntity living)) return false;

        if (living.deathTime > 0) return false;

        double dx = entity.getDeltaMovement().x;
        double dz = entity.getDeltaMovement().z;
        double speed = Math.sqrt(dx * dx + dz * dz);

        if (speed <= 0.01) return false;

        Player nearestPlayer = world.getNearestPlayer(x, y, z, 32.0, false);
        if (nearestPlayer == null) return false;

        double distance = nearestPlayer.distanceToSqr(x, y, z);
        return distance > 64.0;
    }
}