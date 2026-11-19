package deepluckyblock.enchantments;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
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
 * déjà pris : il tend un arc, lance un trident, écorce une bûche, mange. Exiger une cible
 * supprime tout conflit sans ajouter de touche à retenir, et donne au skill une victime
 * désignée — ce dont les quarante-cinq chorégraphies ont besoin, puisqu'elles sont toutes
 * construites autour d'un corps. Les armes à distance sont exclues pour la même raison :
 * leur clic droit leur appartient.</p>
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
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof HoeItem;
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

        ItemStack weapon = player.getMainHandItem();
        if (!isMeleeSkillWeapon(weapon)) return;

        WeaponSkill.Entry entry = WeaponSkill.firstOn(weapon);
        if (entry == null) return;

        long now = sl.getGameTime();
        if (!WeaponSkill.isCharged(weapon, now)) {
            // En recharge : on consomme quand même le clic, sinon le joueur déclenche
            // l'interaction vanilla de la créature (monter un cochon, tondre un mouton)
            // alors qu'il voulait lancer son skill.
            event.setCanceled(true);
            return;
        }

        boolean cast = WeaponVfxServerListener.castSkillSignature(
                sl, player, target, weapon, entry.effect(), 6);
        if (!cast) return;

        WeaponSkill.startCooldown(weapon, now, entry);
        // Le glint retombe à l'état « en recharge » dès cette image, sans attendre le tick.
        applyGlintState(weapon, false, entry);
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
        refresh(player.getMainHandItem(), now);
        refresh(player.getOffhandItem(), now);
    }

    private static void refresh(ItemStack stack, long now) {
        if (!isMeleeSkillWeapon(stack)) return;
        WeaponSkill.Entry entry = WeaponSkill.firstOn(stack);
        if (entry == null) return;
        boolean charged = WeaponSkill.isCharged(stack, now);
        CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
        boolean already = tag != null && tag.getBoolean(WeaponSkill.GLINT_ON_KEY);
        // On n'écrit du NBT que lorsque l'état CHANGE. Sans ce test, l'arme serait réécrite
        // cinq fois par seconde et le client la verrait se rééquiper en boucle.
        if (charged == already) return;
        applyGlintState(stack, charged, entry);
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
        CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(stack);
        String force = deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY;
        String tier = deepluckyblock.client.EnchantAura.GLINT_TIER_NBT_KEY;
        if (charged) {
            if (!tag.getBoolean(WeaponSkill.GLINT_ON_KEY)) {
                // -1 = « il n'y avait aucune couleur forcée », à distinguer d'un noir opaque.
                tag.putInt(WeaponSkill.GLINT_SAVED_KEY,
                        tag.contains(force) ? tag.getInt(force) : -1);
            }
            tag.putInt(force, 0xFF000000 | (elementGlintRgb(entry) & 0xFFFFFF));
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

    /** Couleur du halo chargé : celle de l'élément de la signature, comme les épées /anim. */
    private static int elementGlintRgb(WeaponSkill.Entry entry) {
        return entry.effect().element().primary;
    }

    /** Utilitaire partagé avec l'infobulle : secondes restantes, 0 si le skill est prêt. */
    public static int remainingSeconds(ItemStack stack, long gameTime) {
        return WeaponSkill.remainingSeconds(stack, gameTime);
    }

    /** Message d'état bref, utilisé par la commande de diagnostic. */
    public static String debugState(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        WeaponSkill.Entry entry = WeaponSkill.firstOn(held);
        if (entry == null) return "aucune competence sur l'arme tenue";
        long now = player.serverLevel().getGameTime();
        int left = WeaponSkill.remainingSeconds(held, now);
        return entry.effect().name() + " (" + entry.enchantPath() + ") recharge "
                + entry.cooldownSeconds() + " s, restant " + left + " s";
    }
}
