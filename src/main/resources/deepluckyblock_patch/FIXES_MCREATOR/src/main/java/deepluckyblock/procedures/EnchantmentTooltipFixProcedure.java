package deepluckyblock.procedures;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.neoforged.fml.common.EventBusSubscriber;
import deepluckyblock.enchantments.CustomEnchantment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Single owner of enchantment tooltip rendering.
 *
 * V36 - Correctif "description manquante" (canne a peche / trident / elytre).
 *
 * Ce que corrige cette version :
 *
 *  1. RESOLUTION MULTI-NAMESPACE
 *     L'ancienne version ne testait QUE "enchantment.<namespace du registre>.<path>.desc".
 *     Si cette cle manquait dans le fichier lang genere, la methode renvoyait null
 *     et la ligne etait purement et simplement omise => description invisible en jeu,
 *     SANS aucun message d'erreur. On teste desormais deep_lucky_block PUIS
 *     supreme_lucky_block avant d'abandonner.
 *
 *  2. ECHEC NON SILENCIEUX
 *     Une cle absente est maintenant journalisee une fois (WARN) et, si
 *     SHOW_MISSING_MARKER vaut true, affichee directement dans l'infobulle sous la
 *     forme "(missing: enchantment.xxx.yyy.desc)". Un probleme de traduction devient
 *     donc immediatement visible au lieu de disparaitre.
 *
 *  3. RETOUR A LA LIGNE
 *     Certaines descriptions depassent 400 caracteres (miners_guidance = 422) et
 *     produisaient une infobulle plus large que l'ecran. Elles sont maintenant
 *     coupees proprement en conservant le code couleur actif.
 *
 *  4. ANTI-DOUBLON ETENDU
 *     Les fragments issus du retour a la ligne sont enregistres dans le filtre de
 *     suppression, afin qu'un second passage (ou un mod tiers type Enchantment
 *     Descriptions) ne duplique pas les lignes.
 */
@SuppressWarnings({"removal", "deprecation"})
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class EnchantmentTooltipFixProcedure {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Plafond de securite pour la conversion en chiffres romains. Releve de 200
    // a 10000 : au-dela de 200 le code retombait sur le nombre brut (symptome
    // du niveau affiche en chiffres arabes au lieu de romain), ce qui empechait
    // tout affichage correct pour un niveau force > 200 (ex : 1000 via
    // commande). 10000 couvre tres largement les niveaux forces raisonnables.
    private static final int MAX_ROMAN = 10000;

    /** Largeur maximale d'une ligne de description, en caracteres visibles. */
    private static final int WRAP_WIDTH = 90;

    /** Indentation des lignes de continuation. */
    private static final String CONT_INDENT = "    ";

    /** Mettre a false pour masquer le marqueur de traduction manquante en production. */
    private static final boolean SHOW_MISSING_MARKER = false;

    /** Namespaces testes, dans l'ordre, pour retrouver une description. */
    private static final String[] DESC_NAMESPACES = {"deep_lucky_block", "supreme_lucky_block"};

    private static final Set<String> MISSING_REPORTED = Collections.synchronizedSet(new HashSet<>());

    /** Marqueur invisible dans Style#getInsertion() permettant à SystemTooltipWindowRenderer de remplacer proprement le bloc texte sans casser l'indexation JEI/EMI. */
    public static final String DLB_SYSTEM_INSERTION_TAG = "\u200b#dlb_sys";

    private static Component markSystemManaged(Component c) {
        if (c == null) return null;
        return Component.empty().withStyle(s -> s.withInsertion(DLB_SYSTEM_INSERTION_TAG)).append(c);
    }

    public static boolean isSystemManagedComponent(Component c) {
        return c != null && DLB_SYSTEM_INSERTION_TAG.equals(c.getStyle().getInsertion());
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        try {
            onTooltipImpl(event);
        } catch (Throwable t) {
            // FIX v37 DIAGNOSTIC : si onTooltipImpl() plante APRES avoir déjà
            // supprimé les lignes vanilla d'enchantement (voir plus bas) mais
            // AVANT de les réinsérer reformatées, l'item finit avec le halo
            // (géré par un système de rendu totalement indépendant) mais SANS
            // AUCUNE ligne d'enchantement dans l'infobulle -> symptôme exact
            // rapporté en jeu. On journalise l'exception complète pour le
            // confirmer, et on ne laisse JAMAIS planter l'infobulle plus loin.
            // FIX (rapporte en jeu : logs pollues, 9705/10821 lignes d'un log de
            // session normale etaient des [SLB-DEBUG-TOOLTIP], car ce handler est
            // appele a CHAQUE FRAME ou un tooltip est visible -- impossible a
            // gater derriere un simple flag sans spammer quand meme. Une VRAIE
            // exception ici reste anormale et doit etre journalisee (LOGGER,
            // pas println), mais sans spam : une seule fois par type d'item.
            LOGGER.error("[deep_lucky_block] Exception dans onTooltip pour {}", safeItemLabel(event), t);
        }
    }

    /**
     * FIX v40 : cle de traduction du composant RACINE d'une ligne d'infobulle,
     * ou null si ce n'est pas une TranslatableContents. Enchantment.getFullname()
     * construit "Component.translatable(cle).append(niveau)" : le niveau est un
     * SIBLING ajoute par append(), la cle du composant lui-meme (avant tout
     * append) reste donc "enchantment.<namespace>.<path>", peu importe le
     * niveau ou la langue active. C'est cette stabilite qu'on exploite pour
     * reconnaitre de façon fiable une ligne de nom d'enchantement deja
     * inseree par vanilla, sans dependre du texte final rendu.
     */
    private static String rootTranslationKey(Component line) {
        if (line != null && line.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return null;
    }

    private static String safeItemLabel(ItemTooltipEvent event) {
        try {
            ItemStack s = event.getItemStack();
            return s == null ? "null" : s.getItem().toString();
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static void onTooltipImpl(ItemTooltipEvent event) {
        deepluckyblock.client.ClientTranslationInjector.ensureInjected();
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;

        // PORT 1.21.1 : composant ItemEnchantments -> DeepEnchant
        Map<Enchantment, Integer> map = deepluckyblock.util.DeepEnchant.map(stack);
        if (map.isEmpty()) return;

        List<Component> lines = event.getToolTip();
        if (lines == null || lines.isEmpty()) return;

        List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(e -> {
            ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(e.getKey());
            return id == null ? "" : id.toString();
        }));

        // Collecte de tout texte titre/description susceptible d'avoir deja ete insere
        // par vanilla, par Enchantment Descriptions ou par un precedent callback.
        Set<String> exact = new HashSet<>();
        Set<String> names = new HashSet<>();
        Map<ResourceLocation, List<Component>> descCache = new LinkedHashMap<>();

        // FIX v40 : cles de traduction (namespace:path, SANS le niveau) de
        // chaque enchantement present sur l'objet. Sert a reperer de maniere
        // FIABLE la ligne de nom deja inseree automatiquement par vanilla
        // (ItemEnchantments.addToTooltip() -> Enchantment.getFullname(),
        // qui produit une TranslatableContents dont la cle est exactement
        // "enchantment.<namespace>.<path>"), independamment du texte rendu
        // (langue, apostrophes, styles...). L'ANCIENNE comparaison PAR TEXTE
        // EXACT echouait silencieusement des qu'un caractere differait entre
        // le texte pre-calcule ici et celui reellement rendu par vanilla
        // (traduction manquante, style different, etc.) -> la ligne
        // d'origine n'etait alors PAS supprimee, et notre ligne reformatee
        // s'ajoutait par-dessus -> doublon "XI XI", constate aussi bien sur
        // des enchantements VANILLA que sur des enchantements DU MOD (donc
        // pas limite au bug de double-append dans buildName() deja corrige
        // en FIX v39, qui ne concernait que le texte vanilla).
        Set<String> nameKeys = new HashSet<>();

        for (Map.Entry<Enchantment, Integer> entry : entries) {
            Enchantment ench = entry.getKey();

            try {
                exact.add(Enchantment.getFullname(deepluckyblock.util.DeepEnchant.holder(ench), entry.getValue()).getString());
            } catch (Throwable ignored) {
                // certains enchantements moddes explosent sur getFullname : on ignore
            }

            ResourceLocation idBase = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
            if (idBase != null) {
                String base = "enchantment." + idBase.getNamespace() + "." + idBase.getPath();
                String translated = Component.translatable(base).getString();
                if (!translated.equals(base)) names.add(translated);
                // FIX v40 : memorise la cle de traduction elle-meme (pas le
                // texte) pour une suppression fiable de la ligne vanilla.
                nameKeys.add(base);
            }

            ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
            if (id == null) continue;

            String raw = rawDescription(id);
            if (raw != null) {
                // FIX (rapporté en jeu : "la description de Light Heart etc.
                // affiche litteralement 'X' au lieu du nombre de coeurs") :
                // quelques descriptions (coeurs elementaires) contiennent un
                // "%s" substituable par le niveau/la valeur reelle de
                // l'enchantement sur CET item (ex. "+3 heart(s)" au lieu de
                // "+X heart(s)"). On applique le format ici, une seule fois,
                // AVANT le decoupage en lignes (wrap), pour que la valeur
                // apparaisse correctement quel que soit le niveau force.
                if (raw.indexOf("%s") >= 0) {
                    try {
                        raw = String.format(java.util.Locale.ROOT, raw, entry.getValue());
                    } catch (Throwable ignoredFormat) {
                        // format invalide (ne devrait pas arriver) : on garde le texte brut plutot que de planter.
                    }
                }
                exact.add(raw);
                List<Component> wrapped = wrap(raw);
                for (Component c : wrapped) exact.add(c.getString());
                descCache.put(id, wrapped);
            }
        }

        exact.add(Component.translatable("tooltip.deep_lucky_block.hold_shift").getString());
        exact.add(Component.translatable("tooltip.supreme_lucky_block.hold_shift").getString());

        // On conserve le nom de l'objet a l'index 0, puis on supprime tout rendu
        // existant de titre/description/pied de page avant de reinserer une copie unique.
        for (int i = lines.size() - 1; i >= 1; i--) {
            Component line = lines.get(i);
            String raw = line.getString();
            if (raw == null || raw.isBlank()) continue;

            // FIX v40 : verification PRIORITAIRE par cle de traduction (fiable,
            // independante du texte rendu). Enchantment.getFullname() (vanilla)
            // produit une TranslatableContents dont la cle "racine" est
            // exactement "enchantment.<namespace>.<path>" (le niveau est un
            // COMPOSANT ENFANT separe -- "enchantment.level.<n>" -- ajoute par
            // append(), donc la cle du composant PARENT reste stable quel que
            // soit le niveau ou la langue). C'est ce composant parent qu'on
            // reconnait ici pour supprimer la ligne d'origine avant de la
            // remplacer, meme si le texte rendu ne correspondait pas
            // exactement a ce qu'on avait pre-calcule (langue manquante,
            // apostrophe differente, style, etc.) -- c'est cette non-
            // correspondance TEXTUELLE qui causait le doublon "XI XI" observe
            // aussi bien sur des enchantements vanilla que sur ceux du mod.
            boolean remove = nameKeys.contains(rootTranslationKey(line));

            if (!remove) {
                remove = exact.contains(raw)
                        || raw.contains("enchantment.deep_lucky_block.")
                        || raw.contains("enchantment.supreme_lucky_block.")
                        || raw.contains("enchantment.level.");
            }

            if (!remove) {
                for (String name : names) {
                    if (raw.equals(name)
                            || raw.matches("^" + Pattern.quote(name) + "\\s+[IVXLCDM0-9]+$")
                            || raw.startsWith("\u2022 " + name + ":")
                            || raw.startsWith("\u2022 " + name + " :")) {
                        remove = true;
                        break;
                    }
                }
            }

            if (remove) lines.remove(i);
        }

        boolean shift = Screen.hasShiftDown();
        int index = Math.min(1, lines.size());
        Set<ResourceLocation> handled = new HashSet<>();

        // FIX (rapporté en jeu, capture d'écran "quand je shift sur une pièce
        // avec plusieurs coeurs élémentaires, chaque ligne '+X <Élément>
        // Heart' apparaît collée à côté de son propre enchantement, en
        // couleur différente et précédée d'un ❤ -- alors que je veux que ce
        // soit regroupé avec les autres bonus de la pièce (\"+8 Armor\"),
        // MÊME style (bleu), SANS icône ni couleur différente par élément\") :
        // on ne construit plus la ligne directement dans la boucle
        // d'enchantements (voir plus bas, buildHeartBonusLine n'est plus
        // appelée immédiatement) -- on l'accumule ici, puis on l'insère UNE
        // SEULE FOIS, groupée, directement dans la section native "When on
        // Head/Body/Legs/Feet:" (repérée par sa clé de traduction
        // item.modifiers.*), juste après les autres lignes d'attribut de
        // cette section (Armor, Armor Toughness, Knockback Resistance...),
        // avec exactement le même style (bleu, pas d'icône).
        List<Component> heartBonusLines = new ArrayList<>();

        // ==================== FIX CRITIQUE (ask #2/#3) ====================
        // Recalcule ET REMPLACE EN PLACE les lignes vertes natives "Attack
        // Damage"/"Attack Speed"/"Attack Knockback" de la section "When in
        // Main Hand" par la vraie valeur (enchantements du mod déjà inclus
        // dans l'attribut réel + Sharpness/Knockback vanilla simulés
        // + cas spécial arme de soin -> valeur NÉGATIVE). Fait
        // INCONDITIONNELLEMENT (pas seulement sous Shift) car il s'agit de
        // corriger une ligne DÉJÀ affichée par défaut, pas d'ajouter une
        // info supplémentaire.
        try {
            overrideNativeAttackStats(stack, lines);
        } catch (Throwable statsOverrideFail) {
            LOGGER.error("[deep_lucky_block] Echec du recalcul Attack Damage/Speed, ignore", statsOverrideFail);
        }

        // ==================== DPS RÉEL / ARMURE EFFECTIVE ====================
        // FIX (rapporté en jeu : "le tooltip affiche 9 de dégâts même avec
        // Sharpness qui devrait monter à 10") : l'infobulle vanilla n'affiche
        // QUE l'attribut ATTACK_DAMAGE/ARMOR de BASE de l'objet, jamais les
        // bonus appliqués en combat par les enchantements du mod (ni même
        // Sharpness vanilla, qui n'apparaît jamais dans le tooltip vanilla
        // non plus). On calcule ici la vraie valeur "en combat" via
        // CombatStatsCalculator et on l'ajoute en plus de la ligne vanilla
        // d'origine (qu'on NE supprime PAS, pour rester cohérent avec
        // l'affichage standard de Minecraft) afin que le joueur puisse
        // comparer deux objets sur leur puissance RÉELLE.
        // FIX (rapporté en jeu, v41) : cette ligne "DPS réel"/"Armure effective"
        // ne doit PLUS être une section à part affichée par défaut -- les
        // vrais bonus sont désormais appliqués directement aux attributs
        // vanilla eux-mêmes (voir RealAttributeModifierHandler, "When in Main
        // Hand"/"When on Body" reflètent maintenant les bonus du mod). Cette
        // ligne devient un détail secondaire optionnel, visible uniquement en
        // maintenant Shift, pour les enchantements CONDITIONNELS dont la
        // moyenne appliquée à l'attribut réel ne représente qu'une
        // approximation (comparaison exacte contre une cible générique).
        if (shift) {
            try {
                index = insertComputedCombatStats(stack, lines, index);
            } catch (Throwable statsFail) {
                LOGGER.error("[deep_lucky_block] Echec du calcul DPS/armure effectif, ignore", statsFail);
            }
        }

        // FIX v37 : les lignes vanilla d'enchantement ont DÉJÀ été supprimées
        // ci-dessus. Si la réinsertion échoue pour UN enchantement précis
        // (getKeySafe/buildName/description qui explose), les enchants
        // précédemment traités ne doivent pas disparaître, et l'item ne doit
        // JAMAIS finir avec zéro ligne d'enchantement (symptôme "que le
        // halo, aucune description"). Chaque enchant est donc désormais
        // isolé dans son propre try/catch, avec un filet de secours qui
        // affiche au moins le nom brut de l'enchantement si le formatage
        // avancé (couleurs, romain, description) plante.
        for (Map.Entry<Enchantment, Integer> entry : entries) {
            Enchantment ench = entry.getKey();
            try {
                ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
                if (id == null) {
                    // FIX v38 DIAGNOSTIC : ce cas etait la cause reelle du bug
                    // (getKeySafe() renvoyait null cote client a cause d'un
                    // lookup par IDENTITE d'objet dans le mauvais registre).
                    // Doit desormais rester silencieux/absent grace au cache
                    // KEY_BY_IDENTITY dans DeepEnchant. On logge pour verifier.
                    continue;
                }
                if (!handled.add(id)) continue;

                Component nameLine;
                try {
                    nameLine = buildName(ench, entry.getValue());
                } catch (Throwable nameFail) {
                    deepluckyblock.util.DebugLog.warn(LOGGER, "[deep_lucky_block] buildName() a echoue pour {} -> repli sur le nom brut", id, nameFail);
                    nameLine = fallbackName(ench, id, entry.getValue());
                }
                if (shift && isModEnchantment(id)) {
                    deepluckyblock.Rarity rar = CustomEnchantment.getRarityByPath(id.getPath());
                    if (rar != null) {
                        nameLine = Component.empty().append(nameLine).append(" ").append(CustomEnchantment.formatRarityBadge(rar));
                    }
                }
                lines.add(index++, markSystemManaged(nameLine));

                Component heartBonusLine = buildHeartBonusLine(id, entry.getValue());
                if (heartBonusLine != null) heartBonusLines.add(heartBonusLine);

                if (!shift) continue;

                List<Component> desc = descCache.get(id);
                if (desc != null) {
                    for (Component c : desc) lines.add(index++, markSystemManaged(c));
                } else if (isModEnchantment(id)) {
                    reportMissing(id);
                    if (SHOW_MISSING_MARKER) {
                        lines.add(index++, markSystemManaged(Component.literal(
                                CONT_INDENT + "\u00a78\u00a7o(missing: " + descriptionKey(id, id.getNamespace()) + ")")));
                    }
                }
            } catch (Throwable perEnchantFail) {
                // Un seul enchantement en echec ne doit JAMAIS faire perdre
                // tous les autres : on journalise et on continue la boucle.
                LOGGER.error("[deep_lucky_block] Echec de reinsertion tooltip pour un enchantement, ignore", perEnchantFail);
            }
        }

        if (!heartBonusLines.isEmpty()) {
            insertHeartBonusLines(lines, heartBonusLines);
        }

        // T316-COMBOS : Affiche UNIQUEMENT les synergies actives sur la pièce équipée (aucun spoil des combos possibles),
        // et sous Shift, affiche sous chaque combo actif sa description complète (effet + circonstance).
        try {
            List<Component> comboLines = deepluckyblock.enchantments.EnchantmentComboRegistry
                    .buildTooltipComboLines(stack, event.getEntity(), shift);
            for (Component cl : comboLines) {
                lines.add(index++, markSystemManaged(cl));
            }
        } catch (Throwable ignoredComboTooltip) {}

        if (!shift) lines.add(markSystemManaged(Component.translatable("tooltip.deep_lucky_block.hold_shift")));
    }

    /**
     * Insère {@code heartBonusLines} juste après la dernière ligne
     * d'attribut de la section d'équipement native (repérée par la clé de
     * traduction {@code item.modifiers.<slot>}, ex. {@code item.modifiers.
     * chest} -> "When on Body:") -- exactement là où se trouvent déjà les
     * lignes vanilla "+8 Armor"/"+2 Armor Toughness"/"+1 Knockback
     * Resistance". Si aucune section "When on ..." n'est trouvée (objet qui
     * n'a par ailleurs aucun attribut vanilla, ex. armure sans bonus
     * d'armure de base -- cas rare mais possible), on se rabat sur un ajout
     * en fin de liste plutôt que de perdre l'information.
     */
    private static void insertHeartBonusLines(List<Component> lines, List<Component> heartBonusLines) {
        int sectionHeaderIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String key = rootTranslationKey(lines.get(i));
            if (key != null && key.startsWith("item.modifiers.")) sectionHeaderIdx = i;
        }
        if (sectionHeaderIdx < 0) {
            lines.addAll(heartBonusLines);
            return;
        }
        // Cherche la fin de cette section : la première ligne suivante qui
        // est soit une autre section "item.modifiers.*", soit qui n'est
        // plus une ligne d'attribut (attribute.modifier.*/neoforge.modifier.*).
        int insertAt = lines.size();
        for (int i = sectionHeaderIdx + 1; i < lines.size(); i++) {
            Component line = lines.get(i);
            String key = rootTranslationKey(line);
            if (key != null && key.startsWith("item.modifiers.")) { insertAt = i; break; }
            if (findAttributeModifierTranslatable(line) == null) { insertAt = i; break; }
        }
        lines.addAll(insertAt, heartBonusLines);
    }

    // ------------------------------------------------------------------
    // DPS réel (armes) / réduction de dégâts effective (armures)
    // ------------------------------------------------------------------

    /**
     * Insère une (ou deux) ligne(s) calculée(s) juste après le nom de
     * l'objet : DPS réel pour une arme de mêlée, ou réduction de dégâts
     * effective pour une pièce d'armure. Ne fait rien pour les objets qui ne
     * sont ni l'un ni l'autre (outils purs, arcs, etc. -- gérés séparément
     * si besoin plus tard). Renvoie le nouvel index d'insertion pour la
     * suite du traitement (noms/descriptions d'enchantements).
     */
    private static int insertComputedCombatStats(ItemStack stack, List<Component> lines, int index) {
        Component summary = buildCompactCombatSummary(stack);
        if (summary != null) {
            lines.add(index++, markSystemManaged(summary));
        }
        return index;
    }

    /**
     * Résumé compact sur 1 ligne du DPS réel (arme de mêlée) ou de l'armure effective (pièce d'armure)
     * pour la fenêtre [ SYSTÈME ] sous Shift — concis, sans note à rallonge.
     */
    public static Component buildCompactCombatSummary(ItemStack stack) {
        boolean isMeleeWeapon = stack.getItem() instanceof net.minecraft.world.item.SwordItem
                || stack.getItem() instanceof net.minecraft.world.item.AxeItem
                || stack.getItem() instanceof net.minecraft.world.item.TridentItem
                || stack.getItem() instanceof net.minecraft.world.item.MaceItem
                || stack.getItem() instanceof net.minecraft.world.item.ShovelItem;
        boolean isArmor = stack.getItem() instanceof net.minecraft.world.item.ArmorItem;
        boolean fr = deepluckyblock.util.TranslationFallback.isFrench();

        if (isMeleeWeapon) {
            deepluckyblock.util.CombatStatsCalculator.MeleeStats stats =
                    deepluckyblock.util.CombatStatsCalculator.computeMeleeStats(stack);
            if (stats.effectiveDamage() > 0f) {
                MutableComponent line = Component.literal("\u00a7b\u2694 \u00a77")
                        .append(deepluckyblock.util.TranslationFallback.translatableOrFallback(
                                "tooltip.deep_lucky_block.stats.real_dps",
                                fr ? "DPS r\u00e9el : " : "Effective DPS: "))
                        .append(Component.literal("\u00a7f" + trimNumber(stats.effectiveDamage()) + " \u00a78dmg \u00d7 \u00a7f"
                                + trimNumber(stats.attackSpeed()) + "/s \u00a78= \u00a7b"
                                + trimNumber(stats.dps()) + " DPS"));
                if (stats.hasConditionalBonuses()) {
                    line.append(Component.literal(fr ? " \u00a78(\u2248 std)" : " \u00a78(\u2248 std)"));
                }
                return line;
            }
        } else if (isArmor) {
            deepluckyblock.util.CombatStatsCalculator.ArmorStats stats =
                    deepluckyblock.util.CombatStatsCalculator.computeArmorStats(stack);
            if (stats.damageReductionPercent() > 0f) {
                return Component.literal("\u00a7b\u26e8 \u00a77")
                        .append(deepluckyblock.util.TranslationFallback.translatableOrFallback(
                                "tooltip.deep_lucky_block.stats.effective_armor",
                                fr ? "Armure effective : " : "Effective Armor: "))
                        .append(Component.literal("\u00a7f" + trimNumber(stats.baseArmor()) + " \u00a78+ \u00a7b"
                                + trimNumber(stats.damageReductionPercent()) + "% \u00a77"))
                        .append(deepluckyblock.util.TranslationFallback.translatableOrFallback(
                                "tooltip.deep_lucky_block.stats.extra_reduction",
                                fr ? "r\u00e9d. suppl." : "extra red.").withStyle(ChatFormatting.GRAY));
            }
        }
        return null;
    }

    /**
     * FIX CRITIQUE (ask #2/#3, rapporté en jeu : "les lignes vertes Attack
     * Damage/Attack Speed dans 'When in Main Hand' restent figées à leur
     * valeur par défaut peu importe les enchantements posés (Sharpness,
     * Smite, Looting, Fire Aspect...), la simulation d'un coup réel n'est
     * jamais reflétée") : remplace EN PLACE le texte des lignes vanilla
     * natives "+X.X Attack Damage/Attack Speed/Attack Knockback" (section
     * "When in Main Hand"/"When on Body", générée par
     * {@code ItemStack.addAttributeTooltips}/{@code addModifierTooltip})
     * par la vraie valeur recalculée par {@link CombatStatsCalculator}, qui
     * simule -- exactement comme demandé -- un coup porté sur une cible
     * neutre (dégâts/recul infligés, temps de recharge observé) APRÈS
     * application de tous les enchantements (du mod, déjà de vrais
     * AttributeModifier via RealAttributeModifierHandler -- ET vanilla,
     * Sharpness/Knockback, jamais reflétés autrement par le jeu).
     *
     * <p>Repère les lignes cibles par CLÉ DE TRADUCTION (pas par texte
     * brut, pour rester robuste à la langue/aux couleurs) : vanilla génère
     * ces lignes via {@code attribute.modifier.plus.<op>}/
     * {@code attribute.modifier.take.<op>}/{@code attribute.modifier.equals.<op>}
     * avec pour second argument un Component traduisible dont la clé est
     * {@code attribute.name.<attribut>} (ex. {@code attribute.name.generic.attack_damage}).
     */
    private static void overrideNativeAttackStats(ItemStack stack, List<Component> lines) {
        boolean isMeleeWeapon = stack.getItem() instanceof net.minecraft.world.item.SwordItem
                || stack.getItem() instanceof net.minecraft.world.item.AxeItem
                || stack.getItem() instanceof net.minecraft.world.item.TridentItem
                || stack.getItem() instanceof net.minecraft.world.item.MaceItem
                || stack.getItem() instanceof net.minecraft.world.item.ShovelItem;
        if (!isMeleeWeapon) return;

        deepluckyblock.util.CombatStatsCalculator.MeleeStats stats =
                deepluckyblock.util.CombatStatsCalculator.computeMeleeStats(stack);

        // FIX CRITIQUE (rapporté en jeu, capture d'écran "La Hache de Papa
        // Linca" : "74.5 Attack Damage" imprimé 5 FOIS d'affilée au lieu
        // d'une seule ligne mise à jour, "1 Attack Speed" dupliqué 2 fois --
        // "seulement environ 10% des objets fonctionnent correctement") :
        // CAUSE RACINE trouvée en auditant RealAttributeModifierHandler.
        // Cette classe ajoute, pour une arme donnée, PLUSIEURS
        // AttributeModifier DISTINCTS sur le MÊME attribut ATTACK_DAMAGE
        // (un par enchantement du mod actif : "brutal", "sharp_edge",
        // "peerless_flat", "peerless_mult", "cursed_blade", etc., chacun
        // avec son propre ResourceLocation d'ID). Vanilla/NeoForge génère
        // alors, dans le tooltip natif "When in Main Hand", UNE LIGNE DE
        // TEXTE PAR MODIFICATEUR (ItemStack#addAttributeTooltips itère sur
        // TOUS les modifiers de l'attribut, pas seulement un total fusionné)
        // -- une arme avec 5 enchantements du mod touchant Attack Damage
        // produit donc mécaniquement 5 lignes "+X Attack Damage" séparées.
        // L'ancienne version de cette méthode parcourait TOUTES les lignes
        // et réécrivait CHACUNE avec exactement la MÊME valeur recalculée
        // (stats.effectiveDamage()), aboutissant très exactement au bug
        // rapporté : N lignes identiques au lieu d'une seule ligne mise à
        // jour. Les "10% qui marchaient" étaient simplement les objets
        // n'ayant qu'UN SEUL enchantement du mod touchant Attack Damage
        // (donc une seule ligne native à réécrire, coïncidence).
        //
        // FIX : on ne réécrit plus QUE la PREMIÈRE ligne trouvée pour
        // chaque attribut cible (Attack Damage / Attack Speed / Attack
        // Knockback) avec la vraie valeur totale recalculée, puis on
        // SUPPRIME toutes les lignes supplémentaires pour ce même
        // attribut -- exactement le comportement demandé ("modifier la
        // ligne", jamais "écrire N fois la ligne"). Fonctionne
        // identiquement pour 1 comme pour N modificateurs sous-jacents.
        java.util.Set<String> alreadyHandledAttrKeys = new HashSet<>();
        for (int i = 1; i < lines.size(); ) {
            Component line = lines.get(i);
            if (System.getProperty("dlb.debugTooltip") != null) {
                deepluckyblock.util.DebugLog.info(LOGGER, "[DEBUG-TOOLTIP] line[{}] class={} text='{}' key={}",
                        i, line.getContents().getClass().getSimpleName(), line.getString(),
                        (line.getContents() instanceof TranslatableContents tc) ? tc.getKey() : "-");
            }

            // FIX CRITIQUE (rapporté en jeu, ask #5/#6 : "les lignes vertes
            // Attack Damage/Attack Speed ne changent JAMAIS, même avec Blood
            // Transfusion (arme de soin) ou un vrai bonus du mod appliqué")
            // -- cause racine trouvée par inspection du bytecode NeoForge
            // (AttributeUtil.applyTextFor -> IAttributeExtension#toBaseComponent) :
            // depuis NeoForge (et non plus vanilla pur), la ligne native N'EST
            // PLUS directement une TranslatableContents à la racine. Elle est
            // enveloppée dans Component.literal(" ").append(translatable(...))
            // (racine = LiteralContents " ", le translatable qu'on cherchait
            // devient un SIBLING). L'ancien test
            // "line.getContents() instanceof TranslatableContents" échouait
            // donc SILENCIEUSEMENT sur 100% des lignes réelles observées en
            // jeu (confirmé par log [DEBUG-TOOLTIP] : class=LiteralContents,
            // key=-), et la boucle entière ne faisait jamais rien -- aussi
            // bien pour une épée nue que pour une arme de soin. On recherche
            // désormais la TranslatableContents pertinente dans la racine
            // ET dans la chaîne de siblings (Component#getSiblings()),
            // couvrant aussi bien "attribute.modifier.equals.0" (valeur de
            // base fusionnée, cas dominant observé) que "neoforge.modifier.
            // plus/take" (IAttributeExtension#toComponent, modificateur
            // additionnel isolé) : les DEUX préfixes sont désormais acceptés.
            TranslatableContents translatable = findAttributeModifierTranslatable(line);
            if (translatable == null) { i++; continue; }

            Object[] args = translatable.getArgs();
            if (args.length < 2 || !(args[1] instanceof Component attrNameComp)) { i++; continue; }
            if (!(attrNameComp.getContents() instanceof TranslatableContents attrNameTranslatable)) { i++; continue; }
            String attrKey = attrNameTranslatable.getKey();
            if (attrKey == null) { i++; continue; }

            Float newValue = null;
            boolean forceNegativeColor = false;
            if (attrKey.endsWith("attack_damage")) {
                newValue = stats.effectiveDamage();
                forceNegativeColor = stats.isHealingWeapon();
            } else if (attrKey.endsWith("attack_speed")) {
                newValue = stats.attackSpeed();
            } else if (attrKey.endsWith("attack_knockback")) {
                newValue = stats.attackKnockback();
            }
            if (newValue == null) { i++; continue; }

            if (!alreadyHandledAttrKeys.add(attrKey)) {
                // FIX (duplication) : cet attribut a déjà eu sa ligne
                // réécrite plus haut (première occurrence) -- toute ligne
                // supplémentaire pour le MÊME attribut est un doublon natif
                // (un modificateur du mod de plus) et doit être SUPPRIMÉE,
                // jamais réécrite une seconde fois avec la même valeur.
                lines.remove(i);
                continue; // ne pas incrémenter i : l'élément suivant a glissé à l'index i
            }

            String formatted = trimNumber(Math.abs(newValue));
            // Reproduit le format natif "attribute.modifier.equals.0" ->
            // "%s %s" (valeur puis nom d'attribut), avec le signe +/- géré
            // comme vanilla (equals = valeur affichée telle quelle, sans
            // signe explicite ; plus/take ajoutent +/-). Ici, on affiche
            // TOUJOURS la valeur nette avec un signe explicite pour lever
            // toute ambiguïté (demande explicite : valeur NÉGATIVE visible
            // pour une arme de soin).
            String sign = forceNegativeColor ? "-" : "";
            ChatFormatting color = forceNegativeColor ? ChatFormatting.RED : ChatFormatting.DARK_GREEN;
            // FIX : réintroduit l'espace d'indentation en tête de ligne que
            // NeoForge applique lui-même (Component.literal(" ").append(...))
            // avant chaque ligne de modificateur -- sans lui, la ligne
            // remplacée apparaissait légèrement décalée vers la gauche par
            // rapport aux autres lignes "When in Main Hand"/"When on Body"
            // (ex. les lignes d'armure "+8 Armor" non interceptées ici).
            Component replaced = Component.literal(" " + sign + formatted + " ").append(attrNameComp).withStyle(color);
            lines.set(i, replaced);
            i++;
        }
    }

    /**
     * FIX CRITIQUE (voir Javadoc de {@link #overrideNativeAttackStats}) :
     * recherche récursive, dans la racine PUIS dans la chaîne de siblings
     * d'une ligne de tooltip, de la première {@link TranslatableContents}
     * dont la clé correspond à un modificateur d'attribut natif -- que ce
     * soit le format vanilla pur ({@code attribute.modifier.plus/take/equals.<op>})
     * ou le format NeoForge ({@code neoforge.modifier.plus/take}, utilisé
     * pour un modificateur isolé non fusionné avec la valeur de base). Sans
     * cette recherche en profondeur, une ligne enveloppée par NeoForge dans
     * {@code Component.literal(" ").append(translatable)} n'était JAMAIS
     * reconnue (sa racine est une LiteralContents, pas une TranslatableContents),
     * et la ligne "When in Main Hand" restait figée sur la valeur brute de
     * l'objet quels que soient les enchantements posés.
     */
    private static TranslatableContents findAttributeModifierTranslatable(Component component) {
        if (component == null) return null;
        if (component.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (key != null && (key.startsWith("attribute.modifier.") || key.startsWith("neoforge.modifier."))) {
                return tc;
            }
        }
        for (Component sibling : component.getSiblings()) {
            TranslatableContents found = findAttributeModifierTranslatable(sibling);
            if (found != null) return found;
        }
        return null;
    }

    private static String trimNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01f) return String.valueOf(Math.round(value));
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    // ------------------------------------------------------------------
    // COEURS ELEMENTAIRES -- ligne "+X <Element> Heart" (demande explicite,
    // voir FIX au point d'appel ci-dessus).
    // ------------------------------------------------------------------

    /** Paths d'enchantement coeur reconnus par la ligne de bonus. Mêmes 7
     * éléments que EquipmentExpansionHandler#ELEMENTAL_HEART_IDS et que les
     * particules de gain/perte (voir spawnHeartParticles).
     *
     * FIX (compatibilité FR, rapporté : "les traductions doivent toujours
     * etre incluses dans le mcreator, sinon seront ecrasées") : l'ancienne
     * version de ce bloc contenait une Map<String,String> de libellés
     * anglais CODÉS EN DUR ("Fire Heart", "Water Heart"...), utilisée
     * directement dans le tooltip quelle que soit la langue du client --
     * un joueur en fr_fr voyait donc "+3 Fire Heart" au lieu d'un texte
     * français. Chaque coeur élémentaire possède DÉJÀ sa propre clé de
     * traduction MCreator "enchantment.deep_lucky_block.<path>" avec une
     * entrée fr_fr existante (ex. "Coeur de Feu" pour fire_heart, voir
     * assets/deep_lucky_block/lang/fr_fr.json) -- ce sont CES clés qu'il
     * faut réutiliser ici via Component.translatable, jamais du texte en
     * dur, pour que la ligne de bonus suive automatiquement la langue du
     * client ET survive à toute régénération MCreator (les lang files du
     * projet restent la SEULE source de vérité pour ces libellés).
     */
    private static final java.util.Set<String> HEART_ELEMENT_PATHS = new java.util.HashSet<>(java.util.Arrays.asList(
            "fire_heart", "water_heart", "earth_heart", "wind_heart",
            "electric_heart", "shadow_heart", "light_heart"
    ));

    /**
     * Construit la ligne "+X <Élément> Heart" pour un enchantement coeur
     * élémentaire actif sur l'objet (null pour tout autre enchantement).
     * Le nombre de coeurs affiché est le niveau SOFT-CAPPÉ (même formule que
     * {@code EquipmentExpansionHandler#elementalHeartLvl}, un niveau vaut un
     * coeur, soit +2 PV) -- pas le niveau brut, pour rester cohérent avec le
     * bonus de vie réellement appliqué même au-delà du niveau max nominal.
     *
     * FIX (rapporté en jeu : "je veux que la ligne des coeurs soit avec les
     * bonus, dans la section 'When on Body', pas éparpillée à côté de
     * chaque enchantement, en bleu comme les +8 Armor, sans le cœur ni les
     * couleurs différentes par élément") : la ligne adopte désormais
     * EXACTEMENT le style visuel des lignes d'attribut natives "+8 Armor"
     * de la section "When on Head/Body/Legs/Feet:" -- couleur BLEU unique
     * (ChatFormatting.BLUE, la couleur vanilla des bonus positifs
     * d'équipement, cf. capture d'écran de référence), sans icône ❤, et
     * sans couleur distincte par élément (toutes en bleu, comme demandé).
     * L'espace d'indentation en tête reproduit celui des lignes vanilla
     * (Component.literal(" ").append(...)) pour un alignement identique.
     */
    private static Component buildHeartBonusLine(ResourceLocation id, int rawLevel) {
        if (id == null || !"deep_lucky_block".equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (!HEART_ELEMENT_PATHS.contains(path)) return null;
        int hearts = Math.max(1, rawLevel);
        // FIX FR : Component.translatable sur la clé MCreator existante
        // "enchantment.deep_lucky_block.<path>" (déjà traduite en fr_fr,
        // voir Javadoc ci-dessus) au lieu du libellé anglais en dur.
        Component label = Component.translatable("enchantment.deep_lucky_block." + path);
        return Component.literal(" +" + hearts + " ").append(label).withStyle(ChatFormatting.BLUE);
    }

    // ------------------------------------------------------------------
    // Nom formate
    // ------------------------------------------------------------------

    // PORT 1.21.1 : Enchantment.getFullname renvoie Component (pas MutableComponent).
    private static net.minecraft.network.chat.Component buildName(Enchantment ench, int level) {
        ResourceLocation enchId = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
        if (enchId != null && "deep_lucky_block".equals(enchId.getNamespace())) {
            return CustomEnchantment.formatNameMod(ench, level);
        }
        // FIX v40 : Enchantment.getFullname() (vanilla) delegue l'affichage du
        // niveau a la cle de traduction vanilla "enchantment.level.<n>", qui
        // n'existe QUE pour un petit nombre de niveaux (historiquement jusqu'a
        // ~10 en vanilla pur ; Minecraft/NeoForge n'en fournissent pas au-dela).
        // Des qu'un niveau force (via commande) depasse cette plage -- vanilla
        // OU nos propres enchantements du namespace "supreme_lucky_block" (qui
        // ne passent PAS par CustomEnchantment.formatNameMod ci-dessus, voir
        // le test de namespace plus haut) -- Enchantment.getFullname() affiche
        // la CLE BRUTE non traduite (ex. "enchantment.level.1000") au lieu
        // d'un chiffre romain. On construit donc desormais le nom ET le
        // niveau nous-memes, en code, exactement comme formatNameMod() le
        // fait deja pour le namespace deep_lucky_block : plus aucune
        // dependance a "enchantment.level.<n>", donc plus de cle brute
        // affichee, quel que soit le niveau force (jusqu'a MAX_ROMAN).
        net.minecraft.network.chat.MutableComponent name;
        if (enchId != null) {
            // FIX : meme filet de secours que formatNameMod() (voir
            // TranslationFallback) pour les enchantements hors namespace
            // deep_lucky_block (ex. supreme_lucky_block, vanilla) qui
            // passent par cette branche.
            String key = "enchantment." + enchId.getNamespace() + "." + enchId.getPath();
            name = deepluckyblock.util.TranslationFallback.translatableOrFallback(key, enchId.getPath().replace('_', ' '));
        } else {
            // Cas degenere : impossible de resoudre la ResourceLocation de
            // l'enchantement (ne devrait jamais arriver en pratique). On
            // retombe sur le comportement vanilla d'origine plutot que de
            // planter, sachant que ce cas est deja gere par fallbackName()
            // via le try/catch de l'appelant si celui-ci echoue aussi.
            name = (net.minecraft.network.chat.MutableComponent) Enchantment.getFullname(deepluckyblock.util.DeepEnchant.holder(ench), 1);
        }
        int maxLevel = Math.max(1, ench.getMaxLevel());
        if (level != 1 || maxLevel != 1) {
            String value = level <= MAX_ROMAN ? toRoman(level) : String.valueOf(level);
            name = name.append(net.minecraft.network.chat.Component.literal(" " + value));
        }
        ChatFormatting color = CustomEnchantment.isCurseMod(ench) ? ChatFormatting.RED : ChatFormatting.GRAY;
        return name.withStyle(color);
    }


    /**
     * FIX v37 : filet de secours si buildName() (donc getFullname/formatNameMod,
     * qui a besoin d'un Holder résolu dans le registre) échoue pour un
     * enchantement précis. On affiche au moins un nom lisible + le niveau en
     * chiffres romains, à partir du seul ResourceLocation (aucune dépendance
     * au registre / au RegistryAccess), pour ne jamais laisser un enchant
     * comptabilisé mais invisible dans l'infobulle.
     */
    private static Component fallbackName(Enchantment ench, ResourceLocation id, int level) {
        String base = "enchantment." + id.getNamespace() + "." + id.getPath();
        String resolved = deepluckyblock.util.TranslationFallback.resolve(base);
        String label = resolved != null ? resolved : id.getPath().replace('_', ' ');
        ChatFormatting color = CustomEnchantment.isCurseMod(ench) ? ChatFormatting.RED : ChatFormatting.GRAY;
        String value = level <= MAX_ROMAN ? toRoman(level) : String.valueOf(level);
        String suffix = (level == 1) ? "" : (" " + value);
        return Component.literal(label + suffix).withStyle(color);
    }

    // ------------------------------------------------------------------
    // Resolution de la description
    // ------------------------------------------------------------------

    /**
     * Renvoie la description brute (avec ses codes couleur) ou null si aucune
     * traduction n'existe dans aucun namespace connu.
     */
    private static String rawDescription(ResourceLocation id) {
        Language language = Language.getInstance();

        List<String> keys = new ArrayList<>(3);
        keys.add(descriptionKey(id, id.getNamespace()));
        for (String ns : DESC_NAMESPACES) {
            String key = descriptionKey(id, ns);
            if (!keys.contains(key)) keys.add(key);
        }

        for (String key : keys) {
            String value = language.getOrDefault(key);
            if (value != null && !value.equals(key) && !value.isBlank()) return value;
        }

        // FIX v42 : filet de secours -- rapporte en jeu, des enchantements
        // dont la cle EXISTE bel et bien dans en_us.json/fr_fr.json (verifie
        // manuellement : titans_grasp, light_heart, electric_heart,
        // overcharge...) etaient malgre tout signales "manquants" par le
        // code ci-dessus. Cause probable : Language.getInstance() (vanilla)
        // peut, a certains instants (juste apres un (re)chargement de
        // ressources / changement de langue / interference avec
        // ClientTranslationInjector qui appelle Language.inject() de
        // maniere asynchrone via event.enqueueWork()), renvoyer une instance
        // dont la table de traduction n'est pas encore/plus a jour, sans
        // qu'aucune exception ne soit levee -- getOrDefault() renvoie alors
        // simplement la cle en echo, indiscernable d'une vraie absence.
        // On lit donc ICI, en dernier recours, directement le JSON package
        // du mod (present dans les resources, jamais affecte par l'etat de
        // Language), independamment de tout reload/inject. Resultat mis en
        // cache pour ne payer le cout de lecture qu'une seule fois par cle.
        for (String key : keys) {
            String value = fallbackJsonLookup(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /** Cache cle de traduction -> valeur, alimente par lecture directe du JSON packagé du mod. */
    private static final Map<String, String> JSON_FALLBACK_CACHE = new HashMap<>();
    private static volatile String JSON_FALLBACK_LANG = null;

    private static synchronized void ensureJsonFallbackLoaded() {
        String langCode = resolveLangCode();
        if (langCode.equals(JSON_FALLBACK_LANG)) return;
        JSON_FALLBACK_CACHE.clear();
        JSON_FALLBACK_LANG = langCode;
        // Priorite a la langue active, puis repli sur l'anglais (toujours
        // present) afin qu'une langue partiellement traduite ne prive pas
        // le filet de secours des cles encore absentes de sa propre langue.
        loadLangFileIntoCache("/assets/deep_lucky_block/lang/" + langCode + ".json");
        loadLangFileIntoCache("/deepluckyblock_patch/lang/" + langCode + ".json");
        if (!"en_us".equals(langCode)) {
            loadLangFileIntoCache("/assets/deep_lucky_block/lang/en_us.json");
            loadLangFileIntoCache("/deepluckyblock_patch/lang/en_us.json");
        }
    }

    private static String resolveLangCode() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.languageCode != null) {
                String code = mc.options.languageCode.toLowerCase(Locale.ROOT);
                return code.startsWith("fr") ? "fr_fr" : "en_us";
            }
        } catch (Throwable ignored) {
        }
        return "en_us";
    }

    private static void loadLangFileIntoCache(String classpathResource) {
        try (InputStream stream = EnchantmentTooltipFixProcedure.class.getResourceAsStream(classpathResource)) {
            if (stream == null) return;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                if (root == null) return;
                for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                    com.google.gson.JsonElement v = e.getValue();
                    if (v instanceof JsonPrimitive prim && prim.isString()) {
                        // Ne remplace jamais une entree deja chargee depuis la
                        // langue prioritaire par une entree de langue de repli.
                        JSON_FALLBACK_CACHE.putIfAbsent(e.getKey(), prim.getAsString());
                    }
                }
            }
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.warn(LOGGER, "[deep_lucky_block] Echec de lecture du filet de secours lang ({})", classpathResource, t);
        }
    }

    private static String fallbackJsonLookup(String key) {
        ensureJsonFallbackLoaded();
        return JSON_FALLBACK_CACHE.get(key);
    }

    private static String descriptionKey(ResourceLocation id, String namespace) {
        return "enchantment." + namespace + "." + id.getPath() + ".desc";
    }

    public static boolean isModEnchantmentId(ResourceLocation id) {
        return id != null && isModEnchantment(id);
    }

    public static Component formatRuneTitle(Enchantment ench, ResourceLocation id, int level) {
        try {
            return buildName(ench, level);
        } catch (Throwable t) {
            return fallbackName(ench, id, level);
        }
    }

    /**
     * Résout la description d'un enchantement, substitue %s si présent, retire la répétition
     * redondante « • Nom : » en tête de ligne et adoucit le blanc brut (§f) en argent lisible (§7)
     * tout en conservant les valeurs colorées (§c, §a, §b, §e, §6, §d).
     */
    public static String resolveCleanDescription(ResourceLocation id, int level) {
        if (id == null) return null;
        String raw = rawDescription(id);
        if (raw == null || raw.isBlank()) return null;
        if (raw.indexOf("%s") >= 0) {
            try {
                raw = String.format(Locale.ROOT, raw, level);
            } catch (Throwable ignored) {}
        }
        String trimmed = raw.trim();
        // Retire le préfixe redondant « §7• §eNom : » ou « • Nom : » lorsque présent
        if (trimmed.contains("\u2022")) {
            int bulletIdx = trimmed.indexOf('\u2022');
            int colonIdx = trimmed.indexOf(':', bulletIdx);
            if (bulletIdx <= 4 && colonIdx > bulletIdx && colonIdx - bulletIdx <= 54) {
                int cut = colonIdx + 1;
                while (cut < trimmed.length() && trimmed.charAt(cut) == ' ') cut++;
                trimmed = trimmed.substring(cut);
            }
        }
        trimmed = trimmed.replace("\u00a7f", "\u00a77");
        if (!trimmed.startsWith("\u00a7")) {
            trimmed = "\u00a77" + trimmed;
        }
        return trimmed;
    }

    private static boolean isModEnchantment(ResourceLocation id) {
        for (String ns : DESC_NAMESPACES) {
            if (ns.equals(id.getNamespace())) return true;
        }
        return false;
    }

    private static void reportMissing(ResourceLocation id) {
        if (MISSING_REPORTED.add(id.toString())) {
            deepluckyblock.util.DebugLog.warn(LOGGER, "[deep_lucky_block] Description manquante pour {} -> cle attendue: {}",
                    id, descriptionKey(id, id.getNamespace()));
        }
    }

    // ------------------------------------------------------------------
    // Retour a la ligne en preservant les codes de formatage
    // ------------------------------------------------------------------

    private static List<Component> wrap(String text) {
        List<Component> out = new ArrayList<>();
        if (text == null) return out;
        if (visibleLength(text) <= WRAP_WIDTH) {
            out.add(Component.literal(text));
            return out;
        }

        StringBuilder line = new StringBuilder();
        String active = "";
        int visible = 0;

        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            int wl = visibleLength(word);

            if (visible > 0 && visible + 1 + wl > WRAP_WIDTH) {
                out.add(Component.literal(line.toString()));
                line = new StringBuilder(CONT_INDENT).append(active);
                visible = CONT_INDENT.length();
            } else if (visible > 0) {
                line.append(' ');
                visible++;
            }

            line.append(word);
            visible += wl;

            String code = lastCode(word);
            if (!code.isEmpty()) active = code;
        }

        if (line.length() > 0) out.add(Component.literal(line.toString()));
        return out;
    }

    /** Longueur en caracteres reellement affiches (les paires section+code ne comptent pas). */
    private static int visibleLength(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\u00a7') {
                i++;
                continue;
            }
            n++;
        }
        return n;
    }

    /** Dernier code de formatage rencontre dans le mot, pour le reappliquer a la ligne suivante. */
    private static String lastCode(String s) {
        String code = "";
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '\u00a7') code = "\u00a7" + s.charAt(i + 1);
        }
        return code;
    }

    // ------------------------------------------------------------------

    public static String toRoman(int number) {
        if (number <= 0) return String.valueOf(number);
        if (number > MAX_ROMAN) return String.valueOf(number);
        // Table complete (milliers inclus) pour que les niveaux forces au-dela
        // de 100/200 (jusqu'a 1000+) restent en chiffres romains ("M" pour
        // 1000) au lieu de retomber sur le nombre brut.
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                out.append(symbols[i]);
                number -= values[i];
            }
        }
        return out.toString();
    }
}
