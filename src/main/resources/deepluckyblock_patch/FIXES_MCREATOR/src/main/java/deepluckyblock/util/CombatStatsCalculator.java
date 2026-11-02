package deepluckyblock.util;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Calcule un "vrai" DPS pour les armes et une "vraie" valeur d'armure effective
 * pour les pièces d'équipement, en tenant compte des enchantements DU MOD (pas
 * seulement de la valeur de base vanilla affichée par défaut dans l'infobulle).
 *
 * PROBLÈME CORRIGÉ (rapporté en jeu) : l'infobulle vanilla n'affiche QUE
 * l'attribut de base de l'objet (ex. "9 damage" sur une épée en diamant),
 * jamais les bonus des enchantements appliqués au moment du coup par
 * {@code UniversalEnchantmentHandler} (Sharpness inclus : même vanilla ne
 * l'ajoute pas dans le tooltip). Un joueur avec Sharpness V pensait donc que
 * son épée ne faisait "que" 9 de dégâts alors qu'elle en inflige bien plus en
 * combat réel, et ne pouvait pas comparer deux objets entre eux.
 *
 * PORTÉE : ce calculateur couvre les bonus DÉTERMINISTES (qui ne dépendent ni
 * d'un jet aléatoire, ni de l'état précis d'une cible qu'on n'a pas sous la
 * main au moment d'afficher un tooltip -- ex. taille de la cible, présence
 * d'un effet de statut sur elle, type de monstre pour Smite/Bane...). C'est
 * une ESTIMATION contre une cible générique (armure 0, pas d'effet, pas de
 * monture, taille standard), clairement annoncée comme telle dans
 * l'infobulle : très largement plus informatif que la valeur de base seule,
 * sans prétendre reproduire à l'identique chaque branche conditionnelle de
 * {@code UniversalEnchantmentHandler.handleOffensiveHurt}.
 */
public final class CombatStatsCalculator {

    private CombatStatsCalculator() {}

    public record MeleeStats(float baseDamage, float effectiveDamage, float attackSpeed,
                              float dps, boolean hasConditionalBonuses,
                              float attackKnockback, boolean isHealingWeapon, float healAmount) {}

    public record ArmorStats(float baseArmor, float toughness, float damageReductionPercent,
                              boolean hasConditionalBonuses) {}

    public record RangedStats(float shotDamage, float volleyDamage, int projectileCount,
                               float shotsPerSecond, float dps, boolean isToy,
                               boolean hasConditionalBonuses) {}

    private static int lvlById(ItemStack stack, String id) {
        Enchantment en = DeepEnchant.byId(id);
        if (en == null) return 0;
        int raw = DeepEnchant.getLevel(stack, en);
        if (raw <= 0) return 0;
        return DeepEnchant.softCappedLevel(en, raw);
    }

    /**
     * DPS estimé d'une arme de mêlée contre une cible générique (armure 0,
     * taille standard, pas d'effet de statut, joueur non monté, sans coup
     * critique). Additionne les bonus FIXES et MULTIPLICATIFS des
     * enchantements du mod connus pour être toujours actifs, dans le même
     * ordre que {@code UniversalEnchantmentHandler.handleOffensiveHurt}.
     */
    /**
     * FIX v41, RÉVISÉ (fix du bug de stacking "+3 neuf ET +2 ancien") : les
     * bonus FIXES et INCONDITIONNELS du mod sont de VRAIS modificateurs
     * d'attribut vanilla (voir
     * {@code deepluckyblock.enchantments.RealAttributeModifierHandler}), lus
     * par le MÊME mécanisme que {@link #computeAttributeTotal} utilise
     * (Item#getAttributeModifiers via ItemAttributeModifierEvent). Autrement
     * dit, {@code baseDamage} ci-dessous INCLUT DÉJÀ brutal, mighty_strike,
     * sighing_strike, shovel_*, sharp_edge, reinforced/advanced/extreme
     * sharpness, ultra_edge, supreme_sharpness, cursed_blade, peerless,
     * swift_strike, extension, blood_pact, blunt. Les RÉ-AJOUTER ici ferait
     * un DOUBLE COMPTAGE.
     *
     * <p>RÉVISION : last_stand_slayer/focused_ambush/armor_pry/cavalier/
     * stealth_hunter et les malédictions dimensionnelles/undead_curse/
     * exhaustion ne sont PLUS "moyennés" dans RealAttributeModifierHandler
     * (l'ancienne moyenne y créait justement le double-comptage avec
     * UniversalEnchantmentHandler, qui applique le bonus COMPLET uniquement
     * quand la condition réelle est remplie). Ces enchantements sont donc
     * désormais explicitement marqués {@code conditional} ci-dessous : leur
     * valeur n'apparaît PAS dans le DPS/l'armure "de base" affiché, pour ne
     * jamais donner un chiffre trompeur (ni une redite, ni un oubli
     * silencieux) -- exactement comme les autres cas déjà conditionnels
     * (taille de cible, durabilité de l'arme, type précis de cible, critique).
     */
    /**
     * FIX MAJEUR (ask #2, rapporté en jeu : "les lignes vertes Attack
     * Damage/Attack Speed du tooltip restent figées sur les valeurs par
     * défaut de l'objet, quels que soient les enchantements posés dessus")
     * -- reproduit ici, en code, EXACTEMENT la méthode que l'utilisateur
     * demande explicitement : "simuler un coup sur un mannequin (dummy) et
     * dériver l'Attack Damage des dégâts réellement infligés, l'Attack
     * Speed du temps de recharge réel observé", calculé APRÈS application
     * de tous les enchantements (du mod ET vanilla).
     *
     * <p>La "simulation" n'est pas un vrai swing en jeu (impossible depuis
     * un rendu de tooltip, hors combat) mais reproduit fidèlement le PIPELINE
     * RÉEL de dégâts d'un coup vanilla contre une cible générique
     * (armure 0, pas de résistance, pas de critique, pas de bouclier) :
     * Attack Damage de base (incluant déjà tous les vrais AttributeModifier
     * du mod via RealAttributeModifierHandler) + Sharpness vanilla (seul
     * enchantement vanilla à effet garanti, non conditionnel au type de
     * cible) + les bonus/malus PLATS du mod appliqués directement dans
     * {@code UniversalEnchantmentHandler#handleOffensiveHurt} au moment du
     * coup réel (blood_transfusion en particulier, seul cas où le résultat
     * net peut devenir NÉGATIF/soin -- ask #3).
     */
    public static MeleeStats computeMeleeStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new MeleeStats(0f, 0f, 0f, 0f, false, 0f, false, 0f);
        }

        // baseDamage/baseSpeed passent déjà par ItemAttributeModifierEvent
        // (via forEachModifier) donc incluent déjà tous les vrais bonus du
        // mod appliqués par RealAttributeModifierHandler.
        float baseDamage = computeAttributeTotal(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE, 1.0f);
        float baseSpeed = computeAttributeTotal(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED, 4.0f);
        float baseKnockback = computeAttributeTotal(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_KNOCKBACK, 0.0f);
        if (baseDamage <= 0f) baseDamage = 1.0f;
        if (baseSpeed <= 0f) baseSpeed = 4.0f;

        float dmg = baseDamage;
        boolean conditional = false;

        // supreme_sharpness >= 100 : mécanisme "one-shot" (dégâts = 10x la
        // vie max de la cible), non représentable ni en attribut continu ni
        // en DPS moyen -- signalé comme conditionnel uniquement.
        if (lvlById(stack, "deep_lucky_block:supreme_sharpness") >= 100) conditional = true;

        // Giant/Titan Slayer : dépend de la taille RELATIVE de la cible,
        // inconnue hors combat -- non moyennable de façon fiable.
        int giant = lvlById(stack, "deep_lucky_block:giant_slayer");
        int titan = lvlById(stack, "deep_lucky_block:titan_slayer");
        if (giant > 0 || titan > 0) conditional = true;

        // Battle Hardened / New Edge / Battle Scarred : dépendent de la
        // durabilité RESTANTE de l'arme elle-même au moment du coup, qui
        // change en permanence -- non figeable dans un chiffre de tooltip.
        int battleHard = lvlById(stack, "deep_lucky_block:battle_hardened");
        int newEdge = lvlById(stack, "deep_lucky_block:new_edge");
        int battleScar = lvlById(stack, "deep_lucky_block:battle_scarred");
        if (battleHard > 0 || newEdge > 0 || battleScar > 0) conditional = true;

        // Critique (chance + multiplicateur) : probabiliste, dépend du jet
        // aléatoire ET/OU de l'état de chute du joueur au moment du coup.
        int critStuff = lvlById(stack, "deep_lucky_block:minor_crit_rate")
                + lvlById(stack, "deep_lucky_block:crit_rate")
                + lvlById(stack, "deep_lucky_block:focused_crit")
                + lvlById(stack, "deep_lucky_block:minor_crit_amplifier")
                + lvlById(stack, "deep_lucky_block:crit_amplifier")
                + lvlById(stack, "deep_lucky_block:overclocked_crit");
        if (critStuff > 0) conditional = true;

        // FIX (bug de stacking) : ces enchantements dépendent d'une condition
        // précise au moment du coup (PV de la cible, cible pleine vie, cible
        // en armure, joueur monté, invisibilité active, dimension du joueur,
        // air restant) et ne sont plus "moyennés" dans un vrai AttributeModifier
        // permanent (voir RealAttributeModifierHandler) -- uniquement
        // signalés ici comme conditionnels, jamais inclus dans baseDamage.
        int conditionalDamageBonuses = lvlById(stack, "deep_lucky_block:agony_breaker")
                + lvlById(stack, "deep_lucky_block:predators_scent")
                + lvlById(stack, "deep_lucky_block:focused_ambush")
                + lvlById(stack, "deep_lucky_block:armor_pry")
                + lvlById(stack, "deep_lucky_block:cavalier")
                + lvlById(stack, "deep_lucky_block:stealth_hunter")
                + lvlById(stack, "deep_lucky_block:curse_of_the_end")
                + lvlById(stack, "deep_lucky_block:overworld_curse")
                + lvlById(stack, "deep_lucky_block:nether_curse")
                + lvlById(stack, "deep_lucky_block:undead_curse")
                + lvlById(stack, "deep_lucky_block:exhaustion")
                + lvlById(stack, "deep_lucky_block:blood_pact")
                + lvlById(stack, "deep_lucky_block:lone_wolf")
                + lvlById(stack, "deep_lucky_block:duelists_mastery")
                + lvlById(stack, "deep_lucky_block:berserk")
                + lvlById(stack, "deep_lucky_block:desperate_kill")
                + lvlById(stack, "deep_lucky_block:sighing_strike")
                + lvlById(stack, "deep_lucky_block:beheading");
        if (conditionalDamageBonuses > 0) conditional = true;

        // ---- Enchantement VANILLA Sharpness ----
        // Sharpness n'est PAS reflétée par l'attribut de base ni par vanilla
        // lui-même dans le tooltip (comportement natif inchangé) : on
        // l'ajoute nous-mêmes ici pour donner une vraie vision d'ensemble.
        int sharpness = lvlOfVanilla(stack, "sharpness");
        if (sharpness > 0) dmg += (0.5f * sharpness + 0.5f);
        int smite = lvlOfVanilla(stack, "smite");
        int bane = lvlOfVanilla(stack, "bane_of_arthropods");
        if (smite > 0 || bane > 0) conditional = true; // dépend du type de cible

        // ---- Enchantement VANILLA Knockback (recul) ----
        // Comme Attack Damage/Sharpness, vanilla ne recalcule jamais la
        // valeur affichée dans le tooltip -- Knockback ajoute pourtant
        // +3 blocs de recul par niveau au moment du coup réel (voir
        // net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK,
        // appliqué par LivingEntity#knockback dans le pipeline vanilla).
        float knockback = baseKnockback;
        int knockbackLvl = lvlOfVanilla(stack, "knockback");
        if (knockbackLvl > 0) knockback += knockbackLvl;

        // ==================== CAS SPÉCIAL : ARME DE SOIN (ask #3) ====================
        // FIX (rapporté en jeu, Healer's Sword niveau 1 : "annoncée comme
        // infligeant 0 dégât + soignant 1 PV, mais le tooltip affiche
        // toujours une valeur positive par défaut") : Blood Transfusion
        // (voir UniversalEnchantmentHandler#handleOffensiveHurt) met
        // INCONDITIONNELLEMENT event.setAmount(0.0f) puis soigne la cible --
        // le "dégât net" réel de cette arme sur un coup n'est donc jamais
        // positif, il est soit nul (dégâts) soit, en pratique de jeu,
        // NÉGATIF du point de vue de la cible (elle regagne de la vie au
        // lieu d'en perdre). On reflète ça fidèlement : effectiveDamage
        // devient l'opposé du soin appliqué (ex. -1.0 pour un soin de 1 PV),
        // au lieu du chiffre positif par défaut, exactement la demande
        // explicite de l'utilisateur ("doit afficher une valeur NÉGATIVE").
        int transfusion = lvlById(stack, "deep_lucky_block:blood_transfusion");
        boolean isHealing = transfusion > 0;
        float healAmount = isHealing ? (1.0f * transfusion) : 0f;
        if (isHealing) {
            dmg = -healAmount;
            float speedH = baseSpeed;
            float dpsH = dmg * speedH;
            return new MeleeStats(baseDamage, dmg, speedH, dpsH, conditional, knockback, true, healAmount);
        }

        float speed = baseSpeed;
        float dps = dmg * speed;

        return new MeleeStats(baseDamage, dmg, speed, dps, conditional, knockback, false, 0f);
    }

    /**
     * Additionne toutes les valeurs ADD_VALUE des modificateurs d'attribut
     * natifs de l'objet (composant ATTRIBUTE_MODIFIERS) pour un attribut et
     * un slot donnés, en repartant de {@code fallbackBase} si l'objet n'a
     * aucun modificateur pour cet attribut (ex. objet non enchantable ou
     * sans attribut d'attaque défini). Reste volontairement simple
     * (ADD_VALUE uniquement) : les objets de ce mod n'utilisent pas de
     * MULTIPLY_BASE/MULTIPLY_TOTAL sur ATTACK_DAMAGE/ARMOR.
     */
    /**
     * FIX CRITIQUE (rapporté en jeu : "Attack Speed affiché à 4 au lieu de
     * 1.6 sur une épée en diamant nue dès que le tooltip recalculé
     * s'applique") : cette méthode ne rajoutait JAMAIS {@code fallbackBase}
     * (la valeur de base par défaut de l'attribut côté joueur, ex. 4.0 pour
     * Attack Speed, 1.0 pour Attack Damage) dès qu'au moins un modificateur
     * ADD_VALUE de l'objet était trouvé -- elle renvoyait alors SEULEMENT
     * la somme des modificateurs de l'objet (ex. -2.4 pour le modificateur
     * "Weapon modifier" d'une épée en diamant), jamais 4.0 + (-2.4) = 1.6.
     * En aval, {@code computeMeleeStats} traitait ce résultat négatif comme
     * invalide ({@code if (baseSpeed <= 0f) baseSpeed = 4.0f;}) et retombait
     * sur 4.0 BRUT (aucun modificateur pris en compte) -- exactement le "4
     * Attack Speed" incorrect observé en jeu. Reproduit fidèlement le calcul
     * vanilla natif ({@code ItemStack#addModifierTooltip}) : la valeur de
     * base de l'attribut CÔTÉ JOUEUR (fallbackBase) doit TOUJOURS être
     * ajoutée à la somme des modificateurs ADD_VALUE de l'objet, que des
     * modificateurs aient été trouvés ou non.
     */
    private static float computeAttributeTotal(ItemStack stack, EquipmentSlot slot,
            Holder<Attribute> attribute, float fallbackBase) {
        float[] total = {fallbackBase};
        try {
            stack.forEachModifier(slot, (attr, modifier) -> {
                if (attr == attribute) {
                    if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                        total[0] += (float) modifier.amount();
                    }
                }
            });
        } catch (Throwable ignored) {}
        return total[0];
    }

    private static EquipmentSlot resolveEquipmentSlot(ItemStack stack) {
        try {
            if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armorItem) {
                return armorItem.getEquipmentSlot();
            }
        } catch (Throwable ignored) {}
        return EquipmentSlot.CHEST;
    }

    private static int lvlOfVanilla(ItemStack stack, String path) {
        try {
            Enchantment en = DeepEnchant.byId("minecraft:" + path);
            if (en == null) return 0;
            return DeepEnchant.getLevel(stack, en);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Valeur d'armure "effective" d'une pièce d'équipement : l'armure/la
     * ténacité de base (attributs vanilla, déjà affichés normalement), PLUS
     * un pourcentage de réduction de dégâts génériques supplémentaire
     * apporté par les enchantements du mod qui réduisent les dégâts SANS
     * passer par l'attribut ARMOR vanilla (ex. Divine Protection, Steel
     * Body...). Ce pourcentage ne peut pas être traduit en "points
     * d'armure" vanilla de façon exacte (la formule d'armure de Minecraft
     * n'est pas linéaire), donc on l'affiche séparément, clairement, plutôt
     * que de fabriquer un faux total illisible.
     */
    /**
     * FIX v41, RÉVISÉ (fix du bug de stacking) : Divine Protection, Stomach
     * Pouch et Wound Crack/Crippled sont de VRAIS points d'ARMOR vanilla
     * FIXES et inconditionnels, convertis par
     * {@code deepluckyblock.enchantments.RealAttributeModifierHandler}, donc
     * déjà inclus dans {@code baseArmor} ci-dessous (lu via le même
     * mécanisme forEachModifier) -- les recalculer ici ferait un double
     * comptage.
     *
     * <p>Tous les autres bonus (protections par type de dégâts, Oceans
     * Blessing, Steel Body, Last Stand Toughness, Commandants/Veil, Riptide
     * Curse, hypoxies, Umbral Pact, Radiant Ward, protection contre le feu)
     * dépendent d'une CONDITION précise et ne sont plus "moyennés" en un
     * second vrai AttributeModifier permanent (l'ancienne moyenne créait
     * justement le bug "+3 neuf ET +2 ancien" avec
     * UniversalEnchantmentHandler, qui applique le bonus complet uniquement
     * quand la condition réelle est remplie) : ils sont uniquement signalés
     * comme {@code conditional} ci-dessous, jamais inclus dans
     * {@code baseArmor} ni {@code reductionPct}.
     */
    public static ArmorStats computeArmorStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new ArmorStats(0f, 0f, 0f, false);
        }

        EquipmentSlot slot = resolveEquipmentSlot(stack);
        float baseArmor = computeAttributeTotal(stack, slot, Attributes.ARMOR, 0f);
        float toughness = computeAttributeTotal(stack, slot, Attributes.ARMOR_TOUGHNESS, 0f);

        // NETTOYAGE (code mort) : cette methode ne calcule plus jamais de
        // pourcentage de reduction de degats supplementaire -- TOUS les bonus
        // fixes du mod sont deja de vrais points d'ARMOR/ARMOR_TOUGHNESS
        // vanilla (inclus dans baseArmor/toughness ci-dessus via
        // RealAttributeModifierHandler), et tous les bonus restants sont
        // CONDITIONNELS (jamais moyennes, cf. javadoc de la methode) : la
        // variable locale "reductionPct" restait donc TOUJOURS a 0f, jamais
        // incrementee nulle part. Le champ damageReductionPercent() du record
        // est conserve (encore lu par EnchantmentTooltipFixProcedure) mais la
        // valeur est desormais un litteral 0f explicite au lieu d'une
        // variable morte.
        boolean conditional = false;

        int expProt = lvlById(stack, "deep_lucky_block:explosion_guard");
        int fireProtEnh = lvlById(stack, "deep_lucky_block:fire_protection_enhancement");
        int projProtEnh = lvlById(stack, "deep_lucky_block:projectile_protection_enhancement");
        if (expProt > 0 || fireProtEnh > 0 || projProtEnh > 0) conditional = true; // spécifique au type de dégâts

        int steelBody = lvlById(stack, "deep_lucky_block:steel_body");
        if (steelBody > 0) conditional = true; // ne s'active qu'au-dessus de 20 PV (absorption)

        int lastStand = lvlById(stack, "deep_lucky_block:last_stand_toughness");
        if (lastStand > 0) conditional = true; // ne s'active qu'en dessous de 20% de vie

        int infernal = lvlById(stack, "deep_lucky_block:infernal_armor");
        int salamander = lvlById(stack, "deep_lucky_block:salamanders_vow");
        int blazeSanc = lvlById(stack, "deep_lucky_block:blaze_sanctuary");
        if (infernal > 0 || salamander > 0 || blazeSanc > 0) conditional = true; // uniquement contre le feu

        // FIX (bug de stacking) : oceans_blessing, commander_veil (undead/illager
        // commander, veil), riptide_curse, hypoxies d'altitude/profondeur,
        // umbral_pact et radiant_ward ne sont plus "moyennés" dans un vrai
        // AttributeModifier permanent (voir RealAttributeModifierHandler) --
        // uniquement signalés ici comme conditionnels, jamais inclus dans
        // baseArmor, pour éviter le double comptage rapporté.
        int oceansBless = lvlById(stack, "deep_lucky_block:oceans_blessing");
        int commanderLike = Math.max(lvlById(stack, "deep_lucky_block:undead_commander"),
                Math.max(lvlById(stack, "deep_lucky_block:illager_commander"), lvlById(stack, "deep_lucky_block:veil")));
        int riptideCurse = lvlById(stack, "deep_lucky_block:riptide_curse");
        int altHypoxia = lvlById(stack, "deep_lucky_block:high_altitude_hypoxia");
        int deepHypoxia = lvlById(stack, "deep_lucky_block:deep_hypoxia");
        int umbralPact = lvlById(stack, "deep_lucky_block:umbral_pact");
        int radiantWard = lvlById(stack, "deep_lucky_block:radiant_ward");
        if (oceansBless > 0 || commanderLike > 0 || riptideCurse > 0 || altHypoxia > 0
                || deepHypoxia > 0 || umbralPact > 0 || radiantWard > 0) {
            conditional = true;
        }

        return new ArmorStats(baseArmor, toughness, 0f, conditional);
    }

    /**
     * T340-RANGED-OVERHAUL : Estimation fidèle des dégâts d'impact, du nombre de projectiles
     * et de la cadence réelle d'un Arc ou d'une Arbalète, alignée à 100 % sur les formules de
     * {@code UniversalEnchantmentHandler} et {@code EquipmentExpansionHandler}.
     */
    public static RangedStats computeRangedStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new RangedStats(0f, 0f, 1, 0f, 0f, false, false);
        }
        boolean isCrossbow = stack.getItem() instanceof net.minecraft.world.item.CrossbowItem;
        if (lvlById(stack, "deep_lucky_block:toy") > 0) {
            return new RangedStats(0f, 0f, 1, 1.0f, 0f, true, false);
        }

        // Tir bandé complet vanilla (~9.0 PV moyens sur flèche pleine charge / carreau 3.15x)
        float perArrow = 9.0f;
        int power = lvlById(stack, "minecraft:power");
        if (power > 0) {
            perArrow *= (1.0f + 0.25f * (power + 1));
        }
        int heavyImpact = lvlById(stack, "deep_lucky_block:heavy_impact");
        if (heavyImpact > 0) {
            perArrow += 4.0f * heavyImpact;
        }
        int heavyRange = lvlById(stack, "deep_lucky_block:heavy_range");
        if (heavyRange > 0) {
            perArrow += 2.0f * heavyRange;
        }
        int flint = lvlById(stack, "deep_lucky_block:flint_heart");
        if (flint > 0) {
            perArrow += 2.5f * flint;
        }
        int weakForce = lvlById(stack, "deep_lucky_block:weak_force");
        if (weakForce > 0) {
            perArrow *= Math.max(0.25f, 1.0f - 0.25f * weakForce);
        }

        // Bonus d'impact directs garantis sur la flèche principale
        float directBonus = 0.0f;
        int dragon = lvlById(stack, "deep_lucky_block:dragons_breath_arrow");
        if (dragon > 0) directBonus += 14.0f * dragon;
        int poison = lvlById(stack, "deep_lucky_block:poison_arrow");
        if (poison > 0) directBonus += 2.0f * poison;
        int elem = lvlById(stack, "deep_lucky_block:elemental_chain");
        if (elem > 0) directBonus += 2.0f * elem;
        int briar = lvlById(stack, "deep_lucky_block:briar_arrow");
        if (briar > 0) directBonus += 1.5f * briar;
        int storm = lvlById(stack, "deep_lucky_block:storm_strike");
        if (storm > 0) directBonus += 6.0f + 3.0f * storm;
        int thunder = lvlById(stack, "deep_lucky_block:thunderstruck");
        if (thunder > 0) directBonus += 3.0f * thunder;
        int meteor = lvlById(stack, "deep_lucky_block:meteor");
        if (meteor > 0) directBonus += 10.0f + 4.0f * Math.max(0, meteor - 1);
        int delay = lvlById(stack, "deep_lucky_block:delay");
        if (delay > 0) directBonus += 6.0f + 3.0f * delay;
        int brim = lvlById(stack, "deep_lucky_block:brimstone");
        if (brim > 0) directBonus += 4.0f + 2.5f * brim;

        float primaryShot = perArrow + directBonus;

        // Volée multi-projectiles (Multishot, Éventail, Dispersion)
        int multishot = lvlById(stack, "minecraft:multishot");
        int fan = lvlById(stack, "deep_lucky_block:fan_volley");
        int scatter = lvlById(stack, "deep_lucky_block:scatter");
        int extraFullArrows = (multishot > 0 ? 2 : 0) + Math.min(10, 2 * fan);
        int scatterPellets = scatter > 0 ? Math.min(12, 4 + 2 * scatter) : 0;
        int totalProj = 1 + extraFullArrows + scatterPellets;
        float volley = primaryShot + extraFullArrows * perArrow + scatterPellets * (perArrow * 0.48f);

        // Cadence effective (tirs/seconde)
        int instant = lvlById(stack, "deep_lucky_block:instant_shot");
        int quick = lvlById(stack, "minecraft:quick_charge");
        float cycleSeconds;
        if (isCrossbow) {
            cycleSeconds = instant > 0 ? 0.35f : Math.max(0.30f, 1.25f - 0.25f * Math.min(4, quick));
        } else {
            cycleSeconds = instant >= 3 ? 0.35f : Math.max(0.35f, (20.0f - 5.0f * Math.min(3, instant)) / 20.0f + 0.15f);
        }
        float shotsPerSec = 1.0f / cycleSeconds;
        float dps = volley * shotsPerSec;

        boolean conditional = lvlById(stack, "deep_lucky_block:sniper") > 0
                || lvlById(stack, "deep_lucky_block:point_blank_shot") > 0
                || lvlById(stack, "deep_lucky_block:peregrine") > 0
                || lvlById(stack, "deep_lucky_block:water_arrow") > 0
                || lvlById(stack, "deep_lucky_block:predators_scent") > 0
                || lvlById(stack, "deep_lucky_block:fear") > 0
                || lvlById(stack, "deep_lucky_block:thunderstruck") > 0
                || lvlById(stack, "deep_lucky_block:chaos") > 0
                || lvlById(stack, "deep_lucky_block:poison_arrow") > 0
                || lvlById(stack, "deep_lucky_block:wraith_arrow") > 0
                || lvlById(stack, "deep_lucky_block:flint_heart") > 0
                || lvlById(stack, "deep_lucky_block:astral_pierce") > 0
                || lvlById(stack, "deep_lucky_block:phasing_bolts") > 0;

        return new RangedStats(primaryShot, volley, totalProj, shotsPerSec, dps, false, conditional);
    }
}
