package deepluckyblock.procedures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;

public class EnderKnightConditionDeathProcedure {

    public static boolean execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (entity == null) return false;
        if (!(entity instanceof LivingEntity living)) return false;

        if (living.getMaxHealth() <= 0.0F) return false;

        return living.deathTime > 0;
    }
}