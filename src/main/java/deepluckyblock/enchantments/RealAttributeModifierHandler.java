package deepluckyblock.enchantments;

import deepluckyblock.util.DeepEnchant;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * ==================== VRAIS MODIFICATEURS D'ATTRIBUT (v41) ====================
 * FIX rapporté en jeu : "je ne veux pas d'une ligne 'DPS réel' à part, je veux
 * que les enchantements du mod modifient DIRECTEMENT les chiffres vanilla
 * affichés dans 'When in Main Hand' / 'When on Body' (Attack Damage, Attack
 * Speed, Armor, Knockback...)".
 *
 * Contrairement à {@code CombatStatsCalculator} (qui ne fait QUE calculer un
 * texte affiché en plus, sans toucher au jeu), cette classe s'abonne à
 * {@link ItemAttributeModifierEvent} : c'est l'évènement que NeoForge/vanilla
 * interroge à la fois (a) pour construire le tooltip natif "When in Main
 * Hand"/"When on Body", ET (b) pour peupler les VRAIS AttributeInstance du
 * joueur au moment de l'équipement (voir LivingEntity#setItemSlot ->
 * ItemStack#forEachModifier -> ItemStack#getAttributeModifiers() -> ce même
 * évènement). Un modificateur ajouté ici est donc à 100% un vrai bonus de
 * jeu, pas un artifice de tooltip : Attack Damage affiché ET réellement
 * appliqué en combat augmentent tous les deux, comme demandé.
 *
 * PORTÉE (voir tableau de classification complet en mémoire de session) :
 * seuls les enchantements dont l'effet est FIXE ET INCONDITIONNEL sont ici
 * convertis en vrai modificateur d'attribut. Les enchantements dont l'effet
 * dépend d'un état trop instable/imprévisible OU d'une CONDITION précise au
 * moment du coup (durabilité restante de l'ARME elle-même, taille relative
 * d'une cible inconnue, vie manquante du porteur, streak de combo en cours,
 * type précis du prochain ennemi rencontré, cible en armure ou non, joueur
 * monté ou non, type de dégâts subi...) restent EXCLUS ici : {@link
 * UniversalEnchantmentHandler} reste l'UNIQUE source de vérité pour ces
 * bonus, appliqués seulement QUAND la condition réelle est remplie. Ils
 * restent visibles dans la ligne "DPS réel" / "Armure effective" (Shift,
 * voir EnchantmentTooltipFixProcedure), marqués "conditionnel" (voir
 * {@code CombatStatsCalculator}).
 *
 * FIX CRITIQUE (rapporté en jeu -- "au lieu de faire et de noter +5.5
 * armor, tu mets ET +3 armor ET tu laisses l'ancien +2 armor" : le système
 * doit RECALCULER ET REMPLACER une valeur dérivée, jamais en garder deux en
 * même temps) : la version précédente de cette classe convertissait AUSSI
 * les bonus CONDITIONNELS "moyennés" (armor_pry, cavalier, stealth_hunter,
 * divine_protection non -- restait fixe --, oceans_blessing, steel_body,
 * umbral_pact, radiant_ward...) en un second vrai AttributeModifier
 * PERMANENT (actif en continu, qu'importe si la condition réelle était
 * remplie), documenté à l'époque comme un "double-comptage accepté" avec
 * {@link UniversalEnchantmentHandler} qui applique lui le bonus COMPLET
 * uniquement quand la condition est vraiment remplie. Résultat exact du bug
 * rapporté : le joueur voyait un "+X Armor"/"+X Attack Damage" recalculé
 * dans le tooltip (le "nouveau" chiffre), tandis que l'ANCIEN mécanisme
 * (bonus complet appliqué au moment du coup par Universal) restait actif
 * EN PLUS, sans jamais être retiré ni remplacé -- deux bonus cumulés pour
 * un seul enchantement. Fix : tous les bonus CONDITIONNELS/moyennés ont été
 * RETIRÉS de cette classe (ils ne sont plus dupliqués nulle part, seul
 * {@code UniversalEnchantmentHandler} les calcule désormais, au bon
 * moment, avec la valeur exacte -- jamais une moyenne). Seuls les bonus
 * VRAIMENT fixes et inconditionnels (aucune condition de jeu, valeur
 * identique à chaque coup/tick) restent ici comme vrai AttributeModifier,
 * et leur ancienne ré-application dans {@code UniversalEnchantmentHandler}
 * a été supprimée en miroir : chaque enchantement de ce fichier n'est plus
 * calculé qu'à UN SEUL endroit, jamais deux.
 *
 * CONVERSION ARMURE : le système d'armure vanilla n'est pas linéaire, mais
 * l'approximation communément admise (sans Armor Toughness) est qu'1 point
 * d'armure ≈ 4% de réduction de dégâts (jusqu'à 20 points = 80% cap). Les
 * bonus "% de réduction de dégâts" de ce mod (Divine Protection, Stomach
 * Pouch, etc.) sont donc convertis en points d'ARMOR vanilla via ce ratio
 * (points = pourcentage / 4). C'est une approximation clairement documentée,
 * pas une reproduction exacte de la formule de dégâts.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class RealAttributeModifierHandler {
    private RealAttributeModifierHandler() {}

    // Ratio d'approximation "% de réduction de dégâts" -> "points d'armure".
    private static final double ARMOR_POINT_PER_PERCENT = 1.0 / 4.0;

    private static int lvl(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return 0;
        Enchantment en = DeepEnchant.byId("deep_lucky_block:" + id);
        if (en == null) return 0;
        int raw = DeepEnchant.getLevel(stack, en);
        if (raw <= 0) return 0;
        return DeepEnchant.softCappedLevel(en, raw);
    }

    /**
     * FIX (rapporté en jeu, ask #2/#3 -- "8 Attack Damage / 1.6 Attack
     * Speed" IDENTIQUES sur une épée nue et sur une épée avec Sweeping
     * Slash III + Fire Aspect II + Looting II + Smite I") : niveau d'un
     * enchantement VANILLA (Sharpness/Smite/Bane of Arthropods/Knockback...),
     * jamais couvert par {@link #lvl}, qui ne connaît que les enchants du
     * mod. Sans cette lecture, Sharpness/Smite -- les enchantements vanilla
     * qui influencent réellement l'Attack Damage/Knockback -- n'étaient
     * jamais pris en compte ici, laissant le tooltip figé sur la valeur de
     * base peu importe l'enchant posé.
     */
    private static int vanillaLvl(ItemStack stack, String path) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            Enchantment en = DeepEnchant.byId("minecraft:" + path);
            if (en == null) return 0;
            return Math.max(0, DeepEnchant.getLevel(stack, en));
        } catch (Throwable t) {
            return 0;
        }
    }

    private static ResourceLocation mid(String suffix) {
        return ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "modifier_real_" + suffix);
    }

    /**
     * FIX (bug distinct de collision d'ID, découvert en auditant le bug de
     * stacking rapporté) : pour les pièces d'ARMURE, l'ancien {@link #mid}
     * générait le MÊME identifiant ({@code modifier_real_divine_protection})
     * pour n'importe quelle pièce -- casque, plastron, jambières, bottes. Or
     * quand deux pièces portent le même enchantement (ex. Divine Protection
     * sur casque ET plastron), {@code LivingEntity#collectEquipmentChanges}
     * traite chaque emplacement l'un après l'autre et fait TOUJOURS
     * {@code attributeInstance.removeModifier(id)} avant de réappliquer :
     * comme l'id était identique, la pièce traitée en second écrasait
     * silencieusement le bonus de la première au lieu de s'additionner
     * comme documenté ("cumulable sur chaque pièce"). Suffixer l'id par le
     * SLOT rend chaque pièce indépendante -- plus aucune collision, chaque
     * pièce garde son propre modificateur qui se recalcule (et se remplace
     * lui-même, jamais les autres) à chaque changement d'équipement.
     */
    private static ResourceLocation midSlot(String suffix, EquipmentSlot slot) {
        return ResourceLocation.fromNamespaceAndPath("deep_lucky_block",
                "modifier_real_" + suffix + "_" + slot.getName());
    }

    private static void add(ItemAttributeModifierEvent event, Holder<Attribute> attribute, String idSuffix,
            double amount, AttributeModifier.Operation op, EquipmentSlotGroup group) {
        if (Math.abs(amount) < 0.0001) return;
        event.addModifier(attribute, new AttributeModifier(mid(idSuffix), amount, op), group);
    }

    private static void addArmor(ItemAttributeModifierEvent event, Holder<Attribute> attribute, String idSuffix,
            double amount, AttributeModifier.Operation op, EquipmentSlotGroup group, EquipmentSlot slot) {
        if (Math.abs(amount) < 0.0001) return;
        event.addModifier(attribute, new AttributeModifier(midSlot(idSuffix, slot), amount, op), group);
    }

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) return;
            applyWeaponBonuses(event, stack);
            applyArmorBonuses(event, stack);
            applyElytraBonuses(event, stack);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "[deep_lucky_block] Erreur dans RealAttributeModifierHandler, ignoree.", t);
        }
    }

    // ==================== ARME (Main Hand) ====================
    private static void applyWeaponBonuses(ItemAttributeModifierEvent event, ItemStack stack) {
        EquipmentSlotGroup mh = EquipmentSlotGroup.MAINHAND;
        AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;
        AttributeModifier.Operation MULT = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;

        // NOTE (ask #2/#3) : Sharpness/Smite/Bane of Arthropods/Knockback
        // vanilla ne sont VOLONTAIREMENT PAS ajoutés ici comme de vrais
        // AttributeModifier -- vanilla applique déjà ces enchantements à
        // chaque coup réel via son propre pipeline de dégâts (totalement
        // indépendant de ItemAttributeModifierEvent). Les ajouter ICI EN
        // PLUS créerait un DOUBLE COMPTAGE réel en combat (dégâts
        // effectivement doublés pour Sharpness, recul doublé pour
        // Knockback), contrairement aux enchantements PROPRES au mod
        // ci-dessous dont le double-comptage a été explicitement accepté
        // par l'utilisateur en amont. Le recalcul demandé pour la LIGNE DE
        // TOOLTIP elle-même (Attack Damage/Attack Speed/Knockback en tenant
        // compte de Sharpness/Smite/Bane/enchant du mod) est fait de façon
        // PUREMENT AFFICHÉE (sans toucher au jeu réel) par
        // {@link EnchantmentTooltipFixProcedure#overrideNativeAttackStats}
        // + {@link deepluckyblock.util.CombatStatsCalculator}.

        // ---- Bonus FIXES et inconditionnels ----
        add(event, Attributes.ATTACK_DAMAGE, "brutal", 1.5 * lvl(stack, "brutal"), ADD, mh);

        int mighty = lvl(stack, "mighty_strike");
        add(event, Attributes.ATTACK_DAMAGE, "mighty_strike", 1.2 * mighty, ADD, mh);
        add(event, Attributes.ATTACK_KNOCKBACK, "mighty_strike_kb", 0.4 * mighty, ADD, mh);

        add(event, Attributes.ATTACK_DAMAGE, "sighing_strike", 1.0 * lvl(stack, "sighing_strike"), ADD, mh);

        int shovelDmg = Math.max(lvl(stack, "shovel_halberd"),
                Math.max(lvl(stack, "shovel_heavy_hit"), lvl(stack, "shovel_blade")));
        add(event, Attributes.ATTACK_DAMAGE, "shovel_family", 2.0 * shovelDmg, ADD, mh);

        add(event, Attributes.ATTACK_DAMAGE, "sharp_edge", 1.0 * lvl(stack, "sharp_edge"), ADD, mh);
        add(event, Attributes.ATTACK_DAMAGE, "reinforced_sharpness", 2.0 * lvl(stack, "reinforced_sharpness"), ADD, mh);
        // sharpened_sharpness : seule la partie FIXE (1.5/niv) est reprise ici,
        // le bonus additionnel "si cible en armure" reste conditionnel (cf.
        // CombatStatsCalculator, même choix).
        add(event, Attributes.ATTACK_DAMAGE, "sharpened_sharpness", 1.5 * lvl(stack, "sharpened_sharpness"), ADD, mh);
        add(event, Attributes.ATTACK_DAMAGE, "swift_strike", 1.0 * lvl(stack, "swift_strike"), ADD, mh);
        add(event, Attributes.ATTACK_DAMAGE, "extension", 1.2 * lvl(stack, "extension"), ADD, mh);
        // FIX DOUBLE-COMPTAGE JUMEAU (ask #5) : Titan's Grasp ("Crushing melee
        // damage bonus, but your attack speed is reduced") -- le bonus de
        // degats plat est deja gere cote combat (handleOffensiveHurt), mais le
        // MALUS de vitesse d'attaque, lui, etait pose UNIQUEMENT sur le JOUEUR
        // par EquipmentExpansionHandler#updateHeartEnchantmentPassives a
        // chaque tick (TITANS_GRASP_SPEED_ID), fixe et inconditionnel (aucune
        // condition de jeu au-dela du simple port de l'arme), jamais visible
        // dans la ligne native "When in Main Hand" -> "Attack Speed". Meme
        // formule (-min(0.45, 0.15*niv), multiplicatif) reprise ici, le calcul
        // cote tick reste pour l'instant en place UNIQUEMENT parce qu'il gere
        // aussi le nettoyage Creatif/Spectateur -- a terme, doublon a retirer
        // une fois confirme que Attributes.ATTACK_SPEED nettoie deja seul en
        // mode Creatif (les modificateurs d'ITEM disparaissent des qu'il est
        // retire de la main, contrairement aux anciens modifs JOUEUR).
        add(event, Attributes.ATTACK_SPEED, "titans_grasp", -Math.min(0.45, 0.15 * lvl(stack, "titans_grasp")), MULT, mh);
        // blood_pact : bonus +2/niv quasi toujours actif (ne s'annule que si le
        // porteur a moins de 2 PV, cas limite négligeable pour un tooltip).
        add(event, Attributes.ATTACK_DAMAGE, "blood_pact", 2.0 * lvl(stack, "blood_pact"), ADD, mh);
        // blunt : malus fixe et inconditionnel.
        add(event, Attributes.ATTACK_DAMAGE, "blunt", -1.5 * lvl(stack, "blunt"), ADD, mh);

        // ---- Bonus FIXE + MULTIPLICATIF (comme dans handleOffensiveHurt) ----
        int peerless = lvl(stack, "peerless");
        add(event, Attributes.ATTACK_DAMAGE, "peerless_flat", 4.0 * peerless, ADD, mh);
        add(event, Attributes.ATTACK_DAMAGE, "peerless_mult", 0.50 * peerless, MULT, mh);

        int extreme = lvl(stack, "extreme_sharpness");
        add(event, Attributes.ATTACK_DAMAGE, "extreme_sharpness_flat", 5.0 * extreme, ADD, mh);
        add(event, Attributes.ATTACK_DAMAGE, "extreme_sharpness_mult", 0.12 * extreme, MULT, mh);

        int ultra = lvl(stack, "ultra_edge");
        add(event, Attributes.ATTACK_DAMAGE, "ultra_edge_flat", 4.0 * ultra, ADD, mh);
        add(event, Attributes.ATTACK_DAMAGE, "ultra_edge_mult", 0.10 * ultra, MULT, mh);

        int advSharp = lvl(stack, "advanced_sharpness");
        add(event, Attributes.ATTACK_DAMAGE, "advanced_sharpness_flat", 3.0 * advSharp, ADD, mh);
        add(event, Attributes.ATTACK_DAMAGE, "advanced_sharpness_mult", 0.08 * advSharp, MULT, mh);

        // supreme_sharpness : au-dela de 100 c'est un mecanisme "one-shot" non
        // representable en attribut continu -> exclu (reste Shift-only).
        int supSharp = lvl(stack, "supreme_sharpness");
        if (supSharp > 0 && supSharp < 100) {
            add(event, Attributes.ATTACK_DAMAGE, "supreme_sharpness_flat", 8.0 * supSharp, ADD, mh);
            add(event, Attributes.ATTACK_DAMAGE, "supreme_sharpness_mult", 0.18 * supSharp, MULT, mh);
        }

        // cursed_blade : x10 -> +9.0 en ADD_MULTIPLIED_TOTAL (base 1.0 + 9.0 = 10.0).
        add(event, Attributes.ATTACK_DAMAGE, "cursed_blade", 9.0 * lvl(stack, "cursed_blade"), MULT, mh);

        // ---- Bonus CONDITIONNELS (last_stand_slayer, focused_ambush, armor_pry,
        // cavalier, stealth_hunter, malédictions dimensionnelles, undead_curse,
        // exhaustion) : SUPPRIMÉS D'ICI (FIX du bug de stacking, voir Javadoc de
        // classe). Ces bonus dépendent d'une CONDITION précise au moment du coup
        // (cible <30% PV, cible pleine vie, cible en armure, joueur monté,
        // invisibilité, dimension du joueur, air restant...) : les représenter
        // comme un vrai AttributeModifier PERMANENT ici les rendait actifs EN
        // PERMANENCE (même hors condition), PENDANT que UniversalEnchantmentHandler
        // appliquait EN PLUS le bonus complet uniquement quand la condition était
        // réellement remplie -- exactement le "+3 neuf ET +2 ancien" rapporté.
        // Seul UniversalEnchantmentHandler calcule désormais ces bonus, avec la
        // valeur EXACTE (jamais une moyenne), au moment exact où ils s'appliquent.
    }

    // ==================== ARMURE (When on Body) ====================
    private static void applyArmorBonuses(ItemAttributeModifierEvent event, ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armorItem)) return; // seules les vraies pieces d'armure comptent
        EquipmentSlot slot = armorItem.getEquipmentSlot();
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
        AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;

        // ---- Bonus FIXES et inconditionnels ----
        // FIX (bug de collision d'ID) : chaque bonus utilise désormais addArmor
        // (id suffixé par le SLOT) au lieu de add (id partagé par toutes les
        // pièces) -- deux pièces portant le même enchantement (ex. Divine
        // Protection sur casque ET plastron) gardent maintenant CHACUNE leur
        // propre modificateur au lieu que la seconde équipée écrase la
        // première. Voir Javadoc de {@link #midSlot}.
        //
        // FIX CRITIQUE (rapporté en jeu : "deux casques avec Protection I et
        // Protection VI affichent EXACTEMENT le même '+3 Armor/+3 Armor
        // Toughness' dans le tooltip, le niveau de l'enchantement n'a AUCUN
        // effet visible") : l'enchantement vanilla Protection n'a jamais été
        // converti en vrai AttributeModifier ARMOR ici (il n'agit nativement
        // que sur le pipeline interne de dégâts de Minecraft, jamais sur
        // l'attribut ARMOR affiché) -- résultat, sa ligne "+3 Armor" restait
        // TOUJOURS celle, fixe, de la pièce de base (matériau de l'armure),
        // quel que soit le niveau de Protection posé, exactement le bug
        // rapporté. Fix : chaque niveau de Protection ajoute désormais un
        // vrai bonus d'ARMOR réel et visible, approximation vanilla
        // documentée (~4% de réduction de dégâts "EPF" par niveau ≈ 1 point
        // d'armure/niveau via ARMOR_POINT_PER_PERCENT), plafonné à +20
        // (le cap vanilla de 80% EPF à 20 niveaux cumulés) PAR pièce -- ainsi
        // Protection I et Protection VI produisent désormais des totaux
        // clairement différents (+1 vs +6 Armor) au lieu d'être identiques.
        int vanillaProtection = vanillaLvl(stack, "protection");
        addArmor(event, Attributes.ARMOR, "vanilla_protection",
                Math.min(20.0, 4.0 * vanillaProtection * ARMOR_POINT_PER_PERCENT), ADD, group, slot);

        // Divine Protection : -10%/niv, cumulable sur chaque piece -> +2.5 armure/niv PAR piece.
        addArmor(event, Attributes.ARMOR, "divine_protection", 10.0 * lvl(stack, "divine_protection") * ARMOR_POINT_PER_PERCENT, ADD, group, slot);
        // Stomach Pouch (plastron uniquement) : -10%/niv -> +2.5 armure/niv.
        addArmor(event, Attributes.ARMOR, "stomach_pouch", 10.0 * lvl(stack, "stomach_pouch") * ARMOR_POINT_PER_PERCENT, ADD, group, slot);
        // Wound Crack / Crippled : malus +20%/niv de degats subis -> -5.0 armure/niv.
        int woundCrack = Math.max(lvl(stack, "wound_crack"), lvl(stack, "crippled"));
        addArmor(event, Attributes.ARMOR, "wound_crack", -20.0 * woundCrack * ARMOR_POINT_PER_PERCENT, ADD, group, slot);

        // FIX CRITIQUE (ask #5, rapporté en jeu, capture d'écran "L'Armure de
        // Fluffy" -- Protection V/Thorns III/Adrenalaline III/Sustained
        // Resistance I posés sur l'armure NE modifient PAS les lignes +8
        // Armor/+3 Armor Toughness/+10% Knockback Resistance) : Surefooted
        // ("Adds knockback resistance [...]", bottes uniquement) est un
        // bonus FIXE et INCONDITIONNEL (actif dès que le niveau > 0, sans
        // aucune condition de jeu) qui était géré EXCLUSIVEMENT par
        // {@code EquipmentExpansionHandler#playerTickImpl} via un vrai
        // AttributeModifier posé sur le JOUEUR à chaque tick -- jamais sur
        // l'ITEM lui-même via {@link ItemAttributeModifierEvent}. Résultat :
        // le bonus fonctionnait bel et bien en jeu (le joueur résistait
        // réellement plus au recul), mais n'apparaissait JAMAIS dans la
        // ligne native "When on Body" -> "Knockback Resistance" de
        // l'infobulle des bottes, exactement le symptôme rapporté ("les
        // enchantements ne modifient pas les lignes affichées"). Le
        // recalcul de la fonte de distance de chute (composante VRAIMENT
        // conditionnelle : ne s'applique qu'au moment d'un atterrissage,
        // cf. Javadoc CombatStatsCalculator) reste tick-based dans
        // EquipmentExpansionHandler -- la boucle de tick n'y repose plus
        // que sur cette partie fall-distance, le knockback resistance est
        // maintenant réglé ICI, en miroir supprimé côté tick (voir FIX
        // jumeau dans EquipmentExpansionHandler.playerTickImpl).
        addArmor(event, Attributes.KNOCKBACK_RESISTANCE, "surefooted", 0.18 * lvl(stack, "surefooted"), ADD, group, slot);

        // FIX DOUBLE-COMPTAGE JUMEAU (ask #5) : Stonebound Roots ("Greatly
        // reduces knockback and fall damage, but slows your movement",
        // jambières uniquement) -- même bug exact que Surefooted ci-dessus :
        // volet knockback-resistance ET malus de vitesse sont FIXES et
        // INCONDITIONNELS (actifs dès que le niveau > 0), gérés
        // exclusivement par {@code EquipmentExpansionHandler
        // #updateHeartEnchantmentPassives} via des AttributeModifier posés
        // sur le JOUEUR, jamais visibles dans "When on Body" -> "Knockback
        // Resistance"/"Movement Speed". Miroir ici, formules identiques
        // (min(1.0, 0.25*niv) pour la résistance, -0.06*niv multiplicatif
        // pour la vitesse). La réduction de distance de chute (instantanée,
        // au moment de l'atterrissage) reste tick-based, comme pour Surefoot.
        AttributeModifier.Operation SPEED_MULT = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        addArmor(event, Attributes.KNOCKBACK_RESISTANCE, "stonebound_roots",
                Math.min(1.0, 0.25 * lvl(stack, "stonebound_roots")), ADD, group, slot);
        addArmor(event, Attributes.MOVEMENT_SPEED, "stonebound_roots",
                -0.06 * lvl(stack, "stonebound_roots"), SPEED_MULT, group, slot);

        // FIX DOUBLE-COMPTAGE JUMEAU (ask #5) : Gale Step ("Higher jumps and
        // softer landings, carried by the wind", bottes uniquement) -- même
        // bug : bonus de saut + réduction des dégâts de chute FIXES et
        // INCONDITIONNELS, gérés exclusivement côté tick jusqu'ici. Miroir
        // ici, formules identiques (+0.10*niv sur Jump Strength, additif ;
        // -min(0.75, 0.20*niv) sur Fall Damage Multiplier, multiplicatif).
        addArmor(event, Attributes.JUMP_STRENGTH, "gale_step", 0.10 * lvl(stack, "gale_step"), ADD, group, slot);
        addArmor(event, Attributes.FALL_DAMAGE_MULTIPLIER, "gale_step",
                -Math.min(0.75, 0.20 * lvl(stack, "gale_step")), SPEED_MULT, group, slot);

        // Anchoring (jambières uniquement) : "Sneaking [...] increases
        // knockback resistance" -- la hausse de résistance au recul ne
        // s'applique QUE quand le joueur est accroupi (Shift maintenu),
        // donc CONDITIONNEL au sens strict de cette classe (dépend d'un état
        // de jeu instantané, pas d'un simple équipement). Reste calculé par
        // EquipmentExpansionHandler#playerTickImpl (setKnockback), qui gère
        // déjà correctement le retrait du bonus dès que Shift est relâché.

        // ---- Bonus CONDITIONNELS (protections par type de dégâts, Oceans
        // Blessing, Steel Body, Last Stand Toughness, Commandants/Veil, Riptide
        // Curse, hypoxies, Umbral Pact, Radiant Ward) : SUPPRIMÉS D'ICI (FIX du
        // bug de stacking, voir Javadoc de classe). Comme pour les armes, ces
        // bonus dépendent d'une CONDITION précise (type de dégâts subi, PV du
        // porteur, cible précise, position dans le monde, moment du jour...) ;
        // les représenter comme un vrai AttributeModifier PERMANENT les rendait
        // actifs en continu PENDANT que UniversalEnchantmentHandler appliquait
        // EN PLUS le bonus complet quand la condition était réellement remplie
        // -- exactement le "+3 neuf ET +2 ancien" rapporté ("Armure : +5.5"
        // recalculé alors que l'ancien +2/+3 restait affiché/actif). Seul
        // UniversalEnchantmentHandler calcule désormais ces bonus, avec la
        // valeur EXACTE au moment exact où la condition est remplie.

        // ==================== ENCHANTEMENTS "CŒUR" (v43, RETIRÉ EN v44) ====================
        // ANCIEN COMPORTEMENT (v43) : ce bloc ajoutait ICI un modificateur
        // MAX_HEALTH/KNOCKBACK_RESISTANCE réel PAR élément pour que les
        // cœurs apparaissent dans le tooltip natif "When on Body".
        //
        // FIX CRITIQUE (rapporté en jeu, ask #1 -- "8 lignes '+6 Max Health'
        // identiques, impossible de savoir quel cœur donne quoi") : v43
        // avait deux effets de bord graves, non repérés à l'époque :
        //  1. DOUBLE-COMPTAGE RÉEL : {@link EquipmentExpansionHandler
        //     #updateElementalHearts}/{@code #updateHeartEnchantmentPassives}
        //     appliquent DÉJÀ ces mêmes bonus (MAX_HEALTH par cœur,
        //     KNOCKBACK_RESISTANCE pour Stonebound Roots) en continu
        //     (toutes les 10 ticks) sur l'AttributeInstance RÉELLE du
        //     joueur, avec des ResourceLocation d'ID DIFFÉRENTES
        //     ("modifier_fire_heart" côté tick vs "modifier_real_fire_heart"
        //     ici) : les deux modificateurs s'ADDITIONNAIENT au lieu de se
        //     remplacer -> le joueur recevait deux fois le bonus de vie de
        //     CHAQUE cœur porté (et une résistance au knockback doublée pour
        //     Stonebound Roots), sans qu'aucun message d'erreur ne le
        //     signale.
        //  2. TOOLTIP AMBIGU : le rendu natif vanilla d'un AttributeModifier
        //     (addModifierTooltip) affiche TOUJOURS "+X <Nom de
        //     l'attribut>" (ex. "+6 Max Health") -- le nom/ID interne du
        //     modificateur n'est JAMAIS affiché en jeu. Ajouter sept
        //     modificateurs MAX_HEALTH distincts (un par élément) produisait
        //     donc mécaniquement sept lignes "+6 Max Health" strictement
        //     identiques et indiscernables, EXACTEMENT le bug rapporté
        //     (image-1 : la description de chaque cœur -- qui, elle,
        //     nomme l'élément et le nombre de cœurs, ex. "Fire Heart:
        //     Grants +3 heart(s)..." -- reste juste au-dessus, rendant ce
        //     bloc de lignes "+6 Max Health" natif à la fois redondant ET
        //     illisible).
        //
        // Les cœurs élémentaires restent donc gérés EXCLUSIVEMENT par
        // EquipmentExpansionHandler (seule source de vérité pour le bonus de
        // vie RÉEL, y compris la réduction dynamique par cœur "perdu", voir
        // ElementalHeartLoss/ask #9) ; leur bonus est déjà clairement
        // labellisé PAR ÉLÉMENT dans la description de chaque enchantement
        // (colonne shift, "Fire Heart: Grants +X heart(s)...", etc. --
        // exactement le format "nom + bonus" demandé). Stonebound Roots
        // suit la même logique et reste uniquement géré par
        // updateHeartEnchantmentPassives (tick-based).
    }

    // ==================== ÉLYTRE (When on Body -- CAS SPÉCIAL) ====================
    /**
     * FIX CRITIQUE (ask #5, jumeau de Surefooted ci-dessus) : {@code
     * net.minecraft.world.item.ElytraItem} N'ÉTEND PAS {@link ArmorItem}
     * (elle étend directement {@code Item}, avec {@code Equipable}/{@code
     * Wearable} en interface) -- {@link #applyArmorBonuses} exclut donc
     * SYSTÉMATIQUEMENT l'élytre via son garde {@code instanceof ArmorItem},
     * et un bloc Iron Wings placé par erreur À L'INTÉRIEUR de cette méthode
     * ne se déclencherait JAMAIS pour une vraie élytre. Iron Wings ("Adds 3
     * armor points per level while the elytra is worn") est FIXE et
     * INCONDITIONNEL une fois l'élytre équipée (aucune condition de jeu
     * au-delà du simple port de l'objet) -- géré auparavant UNIQUEMENT par
     * {@code FishingTridentElytraHandler#elytraTickImpl} via un vrai
     * AttributeModifier posé sur le JOUEUR à chaque tick, jamais visible
     * dans la ligne native "When on Body" -> "Armor" de l'infobulle de
     * l'élytre elle-même. Méthode dédiée, indépendante de {@link
     * #applyArmorBonuses}, pour couvrir ce cas particulier.
     */
    private static void applyElytraBonuses(ItemAttributeModifierEvent event, ItemStack stack) {
        if (!stack.is(net.minecraft.world.item.Items.ELYTRA)) return;
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(EquipmentSlot.CHEST);
        addArmor(event, Attributes.ARMOR, "iron_wings", 3.0 * lvl(stack, "iron_wings"),
                AttributeModifier.Operation.ADD_VALUE, group, EquipmentSlot.CHEST);
    }
}
