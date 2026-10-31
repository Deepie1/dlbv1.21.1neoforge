package deepluckyblock.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import deepluckyblock.DeepLuckyBlockMod;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

/**
 * Trigger custom "deep_lucky_block_broken" — PORT 1.21.1.
 *
 * ⚠️ Rupture 1.21 : les triggers ne s'enregistrent plus via
 * CriteriaTriggers.register (supprimé). C'est une vraie entrée de registre :
 *  - le trigger est un DeferredHolder dans Registries.TRIGGER_TYPE
 *    (enregistré par le constructeur @Mod via TRIGGER_TYPES)
 *  - l'instance est un record SimpleInstance avec un Codec (plus de
 *    createInstance(JsonObject)/serializeToJson)
 *  - pas de getId() : l'id = le nom d'enregistrement ("deep_lucky_block_broken")
 *
 * Utilisable dans un JSON d'advancement (dossier data/<modid>/advancement/...) :
 *   "conditions": { "deep_lucky_block:deep_lucky_block_broken": { "blocks_broken": { "min": 10 } } }
 * (en 1.21 la clé "trigger" a disparu des JSON de critères).
 */
public class DeepLuckyBlockAdvancementTrigger extends SimpleCriterionTrigger<DeepLuckyBlockAdvancementTrigger.Instance> {

    /** Hub de registration : attaché au mod bus par le constructeur @Mod. */
    public static final DeferredRegister<net.minecraft.advancements.CriterionTrigger<?>> TRIGGER_TYPES =
            DeferredRegister.create(Registries.TRIGGER_TYPE, DeepLuckyBlockMod.MODID);

    public static final DeferredHolder<net.minecraft.advancements.CriterionTrigger<?>, DeepLuckyBlockAdvancementTrigger> TRIGGER =
            TRIGGER_TYPES.register("deep_lucky_block_broken", DeepLuckyBlockAdvancementTrigger::new);

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** Déclenche le trigger : valide les critères des advancements JSON qui l'utilisent. */
    public void trigger(ServerPlayer player, int blocksBroken) {
        this.trigger(player, instance -> instance.blocksBroken().matches(blocksBroken));
    }

    /** Instance de critère — record 1.21.1 (remplace AbstractCriterionTriggerInstance). */
    public record Instance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints blocksBroken)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                        MinMaxBounds.Ints.CODEC.optionalFieldOf("blocks_broken", MinMaxBounds.Ints.ANY).forGetter(Instance::blocksBroken))
                .apply(instance, Instance::new));
    }
}
