package deepluckyblock.procedures;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

/**
 * Procedure a relier au trigger "Helmet tick event" du casque.
 *
 * Force I = amplificateur 0.
 * L'effet court est rafraichi uniquement lorsque necessaire. Quand le casque
 * est retire, la procedure ne tourne plus et l'effet expire en moins de 0,5 s.
 * Une potion de Force plus longue ou une Force II+ n'est jamais supprimee.
 */
public final class StrengthHelmetTickProcedure {

    private static final int EFFECT_DURATION_TICKS = 20;
    private static final int REFRESH_AT_OR_BELOW_TICKS = 5;
    private static final int STRENGTH_ONE_AMPLIFIER = 0;

    private StrengthHelmetTickProcedure() {}

    /** Signature minimale normalement generee par MCreator. */
    public static void execute(Entity entity) {
        applyStrength(entity);
    }

    /**
     * Surcharge compatible si le trigger MCreator transmet toutes les
     * dependances disponibles du Helmet tick event.
     */
    public static void execute(LevelAccessor world,
                               double x, double y, double z,
                               Entity entity, ItemStack itemstack) {
        applyStrength(entity);
    }

    private static void applyStrength(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) return;
        if (livingEntity.level().isClientSide()) return;

        MobEffectInstance current = livingEntity.getEffect(MobEffects.DAMAGE_BOOST);

        // Ne jamais ecraser une Force II ou superieure.
        if (current != null && current.getAmplifier() > STRENGTH_ONE_AMPLIFIER) {
            return;
        }

        // Une potion Force I plus longue reste prioritaire. L'effet du casque
        // n'est rafraichi que lorsqu'il est absent ou presque termine.
        if (current == null || current.getDuration() <= REFRESH_AT_OR_BELOW_TICKS) {
            livingEntity.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_BOOST,
                    EFFECT_DURATION_TICKS,
                    STRENGTH_ONE_AMPLIFIER,
                    false, // pas un effet ambient
                    false, // masque les particules
                    true   // conserve l'icone dans l'inventaire
            ));
        }
    }
}
