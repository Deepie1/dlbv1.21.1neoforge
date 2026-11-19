package deepluckyblock.enchantments;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * T410-SKILLS — LANCEMENT ET ÉTAT DES QUARANTE-CINQ COMPÉTENCES.
 *
 * <p>Trois responsabilités, et trois seulement :</p>
 * <ol>
 *   <li><b>Lancer</b> la signature sur un clic droit porté SUR UN MOB, arme de mêlée en main ;</li>
 *   <li><b>Facturer</b> la recharge dans le NBT de l'arme ;</li>
 *   <li><b>Basculer le glint</b> : chargé = halo de l'élément au palier 13, en recharge =
 *       le halo d'origine de l'arme, celui de son rang.</li>
 * </ol>
 *
 * <p><b>Pourquoi un clic droit SUR un mob et pas dans le vide.</b> Le clic droit à vide est
 * déjà pris : il tend un arc, lance un trident, écorce une bûche, mange — et, dans ce mod,
 * il déclenche déjà {@code army_summon} via {@code PlayerInteractEvent.RightClickItem}.
 * Exiger une cible supprime tout conflit sans ajouter de touche à retenir, et donne au skill
 * une victime désignée — ce dont les quarante-cinq chorégraphies ont besoin, puisqu'elles
 * sont toutes construites autour d'un corps.</p>
 *
 * <p><b>Ce que le skill ne confisque jamais.</b> Un clic droit n'est annulé que lorsqu'une
 * signature part réellement. Pendant la recharge, l'interaction vanilla reprend la main :
 * sans cela, tenir une épée à competence rendrait impossible de commercer, de tondre ou de
 * seller pendant trente à soixante secondes. Pour la même raison les marchands et les
 * animaux apprivoisés du joueur ne sont jamais des cibles de compétence — chez eux le clic
 * droit a un sens que personne n'accepterait de perdre.</p>
 *
 * <p><b>Pourquoi le glint sert de jauge.</b> Aucun voile gris, aucune ligne « prêt » : le
 * halo de l'arme change à lui seul. Chargé, il prend la couleur de l'élément au palier 13 de
 * l'échelle de {@code /glint} — bien au-dessus du palier 4 par défaut, donc impossible à
 * confondre. Lancé, il redevient exactement ce qu'il était, y compris si le joueur lui avait
 * mis un {@code /glint} à la main : l'ancienne valeur est mise de côté, pas écrasée.</p>
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class WeaponSkillHandler {

    private WeaponSkillHandler() {}

    /** Période de rafraîchissement du glint. Un quart de seconde suffit et ne coûte rien. */
    private static final int GLINT_REFRESH_TICKS = 5;

    /**
     * Armes de mêlée uniquement. Le trident, l'arc et l'arbalète en sont absents volontairement :
     * leur clic droit a déjà un rôle que le skill n'a pas à confisquer.
     */
    private static boolean isMeleeSkillWeapon(ItemStack stack) {
        // Une seule definition, dans WeaponSkill : le butin pose les competences sur
        // exactement les armes qui savent les lancer.
        return WeaponSkill.canCastSignature(stack);
    }

    /**
     * Corps sur lesquels le clic droit vaut mieux que n'importe quelle compétence :
     * les marchands (commerce) et les animaux apprivoisés du joueur (ordre assis,
     * équipement, soin). Les frapper reste possible — au clic GAUCHE, comme toujours.
     */
    private static boolean keepsVanillaInteraction(Player player, LivingEntity target) {
        if (target instanceof AbstractVillager) return true;
        return target instanceof TamableAnimal tame && tame.isTame()
                && tame.getOwnerUUID() != null && tame.getOwnerUUID().equals(player.getUUID());
    }

    // =====================================================================================
    //  1. LANCEMENT
    // =====================================================================================

    @SubscribeEvent
    public static void onRightClickMob(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (target == player || !target.isAlive()) return;
        if (keepsVanillaInteraction(player, target)) return;

        ItemStack weapon = player.getMainHandItem();
        if (!isMeleeSkillWeapon(weapon)) return;

        WeaponSkill.Entry entry = WeaponSkill.firstOn(weapon);
        if (entry == null) return;

        long now = sl.getGameTime();
        // En recharge, on ne consomme PAS le clic : l'interaction vanilla doit continuer
        // de fonctionner normalement. Le joueur sait de toute facon ou il en est, le halo
        // de l'arme le lui dit en permanence.
        if (!WeaponSkill.isCharged(weapon, now)) return;

        boolean cast = WeaponVfxServerListener.castSkillSignature(
                sl, player, target, weapon, entry.effect(), 6);
        if (!cast) return;

        WeaponSkill.startCooldown(weapon, now, entry);
        // Le glint retombe à l'état « en recharge » dès cette image, sans attendre le tick.
        applyGlintState(weapon, false, entry);
        // T415-FIX-3 : et le client doit l'apprendre, sinon l'arme reste visuellement
        // « prête » alors que la compétence vient de partir.
        player.inventoryMenu.broadcastChanges();
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.55f, 1.45f);
        event.setCanceled(true);
    }

    // =====================================================================================
    //  2. LE GLINT COMME JAUGE
    // =====================================================================================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        if (player.tickCount % GLINT_REFRESH_TICKS != 0) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        boolean changed = refresh(player.getMainHandItem(), now);
        changed |= refresh(player.getOffhandItem(), now);
        // T415-FIX-3 (2/2) : sans cette ligne le serveur gardait le nouvel etat pour lui.
        // On ne diffuse QUE sur changement : rediffuser l'inventaire cinq fois par seconde
        // ferait clignoter l'objet tenu en main.
        if (changed) player.inventoryMenu.broadcastChanges();
    }

    /** Renvoie vrai quand le halo vient de changer d'etat, donc quand le client doit etre prevenu. */
    private static boolean refresh(ItemStack stack, long now) {
        if (!isMeleeSkillWeapon(stack)) return false;
        WeaponSkill.Entry entry = WeaponSkill.firstOn(stack);
        if (entry == null) return false;
        boolean charged = WeaponSkill.isCharged(stack, now);
        CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
        boolean already = tag != null && tag.getBoolean(WeaponSkill.GLINT_ON_KEY);
        // On n'écrit du NBT que lorsque l'état CHANGE. Sans ce test, l'arme serait réécrite
        // cinq fois par seconde et le client la verrait se rééquiper en boucle.
        if (charged == already) return false;
        applyGlintState(stack, charged, entry);
        return true;
    }

    /**
     * Pose ou retire le halo « compétence prête ».
     *
     * <p>À la pose, la couleur forcée d'origine est rangée dans {@link WeaponSkill#GLINT_SAVED_KEY}
     * et le palier passe à {@link WeaponSkill#CHARGED_GLINT_TIER}. Au retrait, on remet
     * exactement ce qui était là — ou on efface les deux clés si l'arme n'avait pas de
     * couleur forcée, ce qui la fait retomber sur le halo de son rang.</p>
     */
    private static void applyGlintState(ItemStack stack, boolean charged, WeaponSkill.Entry entry) {
        if (stack == null || stack.isEmpty() || entry == null) return;
        // T415-FIX-3 (1/2) : .copy() OBLIGATOIRE. DeepNbt.getOrCreateTag renvoie la compound
        // VIVANTE du composant CUSTOM_DATA, et l'instantane que le menu d'inventaire garde
        // pour detecter les changements (lastSlots) partage cette MEME instance : la muter
        // sur place modifiait l'objet ET sa copie de reference, si bien que la comparaison
        // ne voyait aucune difference et que rien n'etait jamais envoye au client. C'est
        // pour cela que le halo ne changeait pas d'un poil en jeu. On repart donc d'une
        // copie independante, que commit() repose comme un composant NEUF.
        CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(stack).copy();
        String force = deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY;
        String tier = deepluckyblock.client.EnchantAura.GLINT_TIER_NBT_KEY;
        if (charged) {
            if (!tag.getBoolean(WeaponSkill.GLINT_ON_KEY)) {
                // -1 = « il n'y avait aucune couleur forcée », à distinguer d'un noir opaque.
                tag.putInt(WeaponSkill.GLINT_SAVED_KEY,
                        tag.contains(force) ? tag.getInt(force) : -1);
            }
            tag.putInt(force, 0xFF000000 | (entry.effect().element().primary & 0xFFFFFF));
            tag.putInt(tier, WeaponSkill.CHARGED_GLINT_TIER);
            tag.putBoolean(WeaponSkill.GLINT_ON_KEY, true);
        } else {
            int saved = tag.contains(WeaponSkill.GLINT_SAVED_KEY)
                    ? tag.getInt(WeaponSkill.GLINT_SAVED_KEY) : -1;
            if (saved == -1) {
                tag.remove(force);
                tag.remove(tier);
            } else {
                tag.putInt(force, saved);
            }
            tag.remove(WeaponSkill.GLINT_SAVED_KEY);
            tag.putBoolean(WeaponSkill.GLINT_ON_KEY, false);
        }
        deepluckyblock.util.DeepNbt.commit(stack, tag);
    }
}
