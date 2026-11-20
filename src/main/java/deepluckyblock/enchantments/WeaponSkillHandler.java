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
    //  1 bis. L'ENCLUME REFUSE LA SECONDE COMPÉTENCE
    // =====================================================================================

    /**
     * T425 — UNE ENCLUME NE PEUT PLUS POSER UNE DEUXIÈME COMPÉTENCE.
     *
     * <p>La règle « une compétence par objet » est appliquée à la lecture de toute façon,
     * mais la tenir aussi À LA SOURCE évite au joueur de dépenser des niveaux pour un
     * pouvoir que le jeu lui retirerait aussitôt après. L'enclume n'affiche alors aucun
     * résultat, comme pour n'importe quelle combinaison impossible.</p>
     *
     * <p>Elle ne refuse QUE cela. Une réparation, un renommage ou un livre ordinaire passe
     * comme avant, même sur une arme qui porte déjà une compétence — le sacrifice doit en
     * apporter une pour que la question se pose. Et reposer le MÊME enchantement de
     * compétence pour en monter le niveau reste permis : cela fait toujours une seule
     * compétence à l'arrivée.</p>
     */
    @SubscribeEvent
    public static void onAnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        ItemStack gauche = event.getLeft();
        ItemStack droite = event.getRight();
        if (gauche == null || droite == null || gauche.isEmpty() || droite.isEmpty()) return;
        // Ce que le sacrifice apporterait, jugé sur l'objet de GAUCHE : c'est lui qui décide
        // si une signature peut s'y lancer, jamais le livre posé à droite.
        java.util.Set<String> apportees = WeaponSkill.skillPathsOffered(droite, gauche);
        if (apportees.isEmpty()) return;
        java.util.HashSet<String> total =
                new java.util.HashSet<>(WeaponSkill.skillPathsOffered(gauche, gauche));
        total.addAll(apportees);
        if (total.size() > 1) event.setCanceled(true);
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
        // T425 : une seule competence par objet, et le nettoyage passe AVANT le halo.
        // Dans l'autre ordre, le halo prendrait la couleur d'une competence sur le point
        // d'etre retiree, puis changerait de teinte tout seul au tick suivant.
        boolean changed = WeaponSkill.enforceSingleSkill(player.getMainHandItem());
        changed |= WeaponSkill.enforceSingleSkill(player.getOffhandItem());
        changed |= refresh(player.getMainHandItem(), now);
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
        // T421 : une capacité activée n'a pas d'animation (effect() == null). Sans cette
        // garde, lire entry.effect().element() lèverait une NullPointerException en plein
        // tick serveur.
        if (entry.effect() == null || entry.effect().element() == null) return;
        if (charged) {
            if (!tag.getBoolean(WeaponSkill.GLINT_ON_KEY)) {
                // -1 = « il n'y avait aucune couleur forcée », à distinguer d'un noir opaque.
                tag.putInt(WeaponSkill.GLINT_SAVED_KEY,
                        tag.contains(force) ? tag.getInt(force) : -1);
                // T421 : et le PALIER, qu'on oubliait. Voir WeaponSkill.GLINT_SAVED_TIER_KEY.
                tag.putInt(WeaponSkill.GLINT_SAVED_TIER_KEY,
                        tag.contains(tier) ? tag.getInt(tier) : -1);
            }
            int savedRgb = tag.getInt(WeaponSkill.GLINT_SAVED_KEY) & 0xFFFFFF;
            boolean hadColor = tag.getInt(WeaponSkill.GLINT_SAVED_KEY) != -1;
            tag.putInt(force, 0xFF000000
                    | readyColor(entry.effect().element().primary & 0xFFFFFF, savedRgb, hadColor));
            tag.putInt(tier, WeaponSkill.CHARGED_GLINT_TIER);
            tag.putBoolean(WeaponSkill.GLINT_ON_KEY, true);
        } else {
            int saved = tag.contains(WeaponSkill.GLINT_SAVED_KEY)
                    ? tag.getInt(WeaponSkill.GLINT_SAVED_KEY) : -1;
            int savedTier = tag.contains(WeaponSkill.GLINT_SAVED_TIER_KEY)
                    ? tag.getInt(WeaponSkill.GLINT_SAVED_TIER_KEY) : -1;
            if (saved == -1) tag.remove(force); else tag.putInt(force, saved);
            // T421 : le palier revient lui aussi à ce qu'il était, sinon l'arme reste
            // éclatante en recharge et les deux états deviennent indiscernables.
            if (savedTier == -1) tag.remove(tier); else tag.putInt(tier, savedTier);
            tag.remove(WeaponSkill.GLINT_SAVED_KEY);
            tag.remove(WeaponSkill.GLINT_SAVED_TIER_KEY);
            tag.putBoolean(WeaponSkill.GLINT_ON_KEY, false);
        }
        deepluckyblock.util.DeepNbt.commit(stack, tag);
    }

    /**
     * T421 — LA COULEUR « PRÊTE » NE PEUT PAS RESSEMBLER À CELLE DU RANG.
     *
     * <p>Le palier redescend maintenant pendant la recharge, mais il restait un cas où l'œil
     * ne voit toujours rien : une arme dont le halo de rang est déjà proche de la couleur de
     * l'élément. Une épée légendaire rouge portant une compétence BLOOD passait de rouge à
     * rouge. Quand les deux teintes sont trop voisines, on éclaircit franchement celle de
     * l'état prêt — elle reste la couleur de l'élément, en plus lumineuse.</p>
     *
     * @param elementRgb couleur de l'élément de la compétence
     * @param savedRgb   couleur de halo que l'arme portait avant
     * @param hadColor   faux quand l'arme n'avait aucune couleur forcée : rien à éviter
     */
    private static int readyColor(int elementRgb, int savedRgb, boolean hadColor) {
        if (!hadColor) return elementRgb;
        // T426 : ÉCLAIRCIR UN NOIR, C'EST LE SUPPRIMER. La règle T421 part du principe qu'une
        // teinte plus lumineuse reste la même teinte — vrai d'un rouge, faux d'un élément dont
        // toute l'identité est l'absence de lumière. Le Vide (0x444444) ressortait en gris
        // clair, exactement le contraire du halo attendu. Sous ce seuil on garde la couleur
        // brute : la compétence chargée se signale alors par son palier, pas par sa clarté.
        if (value(elementRgb) < DARK_ELEMENT_VALUE) return elementRgb;
        int dr = ((elementRgb >> 16) & 0xFF) - ((savedRgb >> 16) & 0xFF);
        int dg = ((elementRgb >> 8) & 0xFF) - ((savedRgb >> 8) & 0xFF);
        int db = (elementRgb & 0xFF) - (savedRgb & 0xFF);
        // Distance de Manhattan : largement suffisante pour « ces deux teintes se
        // confondent », et sans racine carrée dans un tick serveur.
        if (Math.abs(dr) + Math.abs(dg) + Math.abs(db) >= 150) return elementRgb;
        return brighten(elementRgb);
    }

    /**
     * Valeur d'une couleur : son canal le plus fort, 0..255.
     *
     * <p>Et surtout pas une luminance perçue. Rec. 601 pondère le rouge à 0,299 et classerait
     * le Sang (0xDD0000, valeur 221) comme sombre : la règle T421 ne s'appliquerait plus
     * justement au cas qui l'a fait écrire, l'épée légendaire rouge à compétence BLOOD. La
     * question posée ici n'est pas « cette couleur éclaire-t-elle ? » mais « reste-t-il de la
     * lumière à éteindre ? », et c'est le canal maximal qui y répond.</p>
     */
    private static int value(int rgb) {
        return Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
    }

    /**
     * En dessous, l'élément est sombre par nature et garde sa couleur brute. Seuls le Vide
     * (0x444444, valeur 68) et le Chaos (0x2B0B38, valeur 56) passent sous ce seuil ; l'Ombre
     * (142), le Sang (221) et la Foudre (255) restent éclaircissables.
     */
    private static final int DARK_ELEMENT_VALUE = 96;

    /** Remonte la teinte vers le blanc de 55 %, en gardant son identité colorée. */
    private static int brighten(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = r + (255 - r) * 55 / 100;
        g = g + (255 - g) * 55 / 100;
        b = b + (255 - b) * 55 / 100;
        return (r << 16) | (g << 8) | b;
    }
}
