package deepluckyblock.advancements;

import com.mojang.serialization.Codec;
import deepluckyblock.DeepLuckyBlockMod;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Trigger custom "negative_streak" (sans conditions) — PORT 1.21.1.
 *
 * ⚠️ Rupture 1.21 :
 *  - plus de CriteriaTriggers.register(...) — le trigger est une entrée de
 *    registre (DeferredRegister, voir DeepLuckyBlockAdvancementTrigger.TRIGGER_TYPES
 *    où tous les triggers du mod sont regroupés et attachés au mod bus)
 *  - plus de getId() — l'id = le nom d'enregistrement "negative_streak"
 *  - l'instance est un record SimpleInstance avec Codec (vide ici)
 *
 * ⚠ Le nom du fichier/classe est historique : ce fichier sert aussi de hub
 * d'enregistrement (le TRIGGER_TYPES commun est déclaré dans
 * DeepLuckyBlockAdvancementTrigger pour éviter une double déclaration).
 */
public class DeepLuckyBlockBrokenTrigger extends SimpleCriterionTrigger<DeepLuckyBlockBrokenTrigger.TriggerInstance> {

    /** Le trigger enregistré dans Registries.TRIGGER_TYPE (id "negative_streak"). */
    public static final net.neoforged.neoforge.registries.DeferredHolder<
            net.minecraft.advancements.CriterionTrigger<?>, DeepLuckyBlockBrokenTrigger> TRIGGER =
            DeepLuckyBlockAdvancementTrigger.TRIGGER_TYPES
                    .register("negative_streak", DeepLuckyBlockBrokenTrigger::new);

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Déclenche le trigger : valide les critères des advancements JSON qui l'utilisent. */
    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    /** Instance de critère sans condition — record 1.21.1. */
    public record TriggerInstance() implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = Codec.unit(new TriggerInstance());

        @Override
        public java.util.Optional<net.minecraft.advancements.critereon.ContextAwarePredicate> player() {
            return java.util.Optional.empty();
        }
    }
}
