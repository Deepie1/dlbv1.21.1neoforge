package deepluckyblock.util;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import deepluckyblock.entity.CrimsonButterflyEntity;

/**
 * T110 (consignes dev du 30/09, verbatim : « si on frappe le papillon, ca nous
 * fait 2 frame ou on prends feu » · « comme si on prenait un mini degat de feu »
 * · « entre 0 et 05 pv de degats ... random ») : la representation n'est pas
 * complete tant que le papillon n'est « qu'une image » — le contre-coup crimson
 * reagit quand on l'agresse.
 *
 * <p>Comportement : quand un joueur frappe un CrimsonButterflyEntity, il
 * s'enflamme TRES brievement (2 ticks, ~2 frames — juste le temps de voir la
 * flamme apparaitre, exactement comme demande) et recoit aleatoirement entre
 * 0 et 0,5 coeur de degats de feu (tirage pile : soit rien, soit 0,5 coeur).
 *
 * <p>Ce handler vit dans un util autonome (pas dans l'entite generee par
 * MCreator) : l'entite crimson reste inchangee, et le comportement se retire
 * d'un coup si jamais il derange en test.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class CrimsonButterflyRetaliation {

    private CrimsonButterflyRetaliation() { }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        // Cote serveur seulement : on n'allume secondes/flame que si la
        // representation serveur est concernee (l'evenement se relance en client).
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getTarget() instanceof CrimsonButterflyEntity)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // « 2 frame ou on prends feu » : ignition quasi instantanee (2 ticks),
        // jamais moins que l'allumage courant (on ne reset pas un feu deja fort).
        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 2));

        // « entre 0 et 05 pv de degats ... random » : pile 0 (rien) ou face
        // 0,5 coeur (1,0 pv), en degat de feu (mini-brulure crimson).
        float dmg = player.level().getRandom().nextBoolean() ? 1.0f : 0.0f;
        if (dmg > 0.0f) player.hurt(player.damageSources().inFire(), dmg);
    }
}
