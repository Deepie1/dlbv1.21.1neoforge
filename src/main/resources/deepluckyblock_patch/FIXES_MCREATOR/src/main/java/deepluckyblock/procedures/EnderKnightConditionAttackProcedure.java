package deepluckyblock.procedures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;

public class EnderKnightConditionAttackProcedure {

    public static boolean execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (entity == null || world == null) return false;
        if (!(entity instanceof LivingEntity living)) return false;

        if (living.deathTime > 0) return false;

        Player nearestPlayer = world.getNearestPlayer(x, y, z, 3.0, false);
        if (nearestPlayer == null) return false;
        if (!nearestPlayer.isAlive()) return false;

        return true;
    }
}