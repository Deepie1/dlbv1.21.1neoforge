package deepluckyblock.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * EnchantAura — CALCUL DE L'AURA COLORÉE, porté depuis Deep's Enchantments+.
 *
 * <p><b>Différence avec le mod d'origine :</b> là-bas l'aura prend la couleur de
 * l'enchantement le plus puissant. Ici, la règle de Deep's Lucky Block est
 * différente : <b>la couleur du glint est celle du RANG tiré par
 * {@code GenerateLuckyItemProcedure}</b>.
 *
 * <p>Chaîne complète, vérifiée dans les sources :
 * <ol>
 *   <li>{@code GenerateLuckyItemProcedure} l. 1122 → {@code recolorNameToRank(finalName, profile.colorCode)}
 *       colore le NOM de l'item avec la couleur du rang ;</li>
 *   <li>{@code rankSuffix(luck)} l. 1081 ajoute {@code " §<couleurDuTier>[CODE]"} —
 *       même code couleur que le nom, donc le DERNIER {@code §} du nom porte bien
 *       la couleur du grade ;</li>
 *   <li>cette classe relit ce code, et {@code EnchantGlintTyped} construit le
 *       RenderType de glint teinté que {@code RenderTypeEnchantMixin} injecte.</li>
 * </ol>
 *
 * <p>Un item enchanté sans code couleur de rang (butin vanilla, craft externe,
 * livre d'enchantement…) renvoie 0 → <b>glint violet vanilla</b>.
 *
 * <p>Le contrat avec le moteur de rendu est inchangé par rapport au mod
 * d'origine : {@link #auraColor} renvoie {@code 0xAARRGGBB} ou {@code 0}, et les
 * mixins de rendu passent par {@link #capture}/{@link #clear}/{@link #currentRgb}.
 */
public final class EnchantAura {

    private EnchantAura() {}

    /** Mode debug : logs {@code [AURA]} / {@code [GLINT-RT]} throttlés. */
    public static boolean DEBUG = false;

    /** Traces bavardes ({@code [MIXIN]} par item, {@code [GLINT-BUILD]} pas à pas). */
    public static boolean DEBUG_VERBOSE = false;

    /**
     * Repli optionnel : si un item est enchanté mais n'a AUCUNE couleur de rang,
     * utiliser la couleur de thème de son enchantement le plus fort
     * ({@link DeepGlintColor}) au lieu du glint violet vanilla.
     * {@code false} par défaut : seuls les items rangés sont teintés.
     */
    public static boolean THEME_FALLBACK = false;

    /**
     * T310-GLINT-FLASH : Surcharge temporaire purement côté client pendant un
     * coup d'arme/outil, un tir actif ou un proc défensif d'armure. Utilise
     * directement {@link DeepGlintColor#resolveActiveFlash} + {@link EnchantGlintTyped},
     * sans aucune écriture NBT serveur (aucune saccade de ré-équipement), puis
     * restaure automatiquement la couleur précédente ({@link #auraColor}) dès
     * la fin du geste.
     */
    public static boolean ACTIVE_SWING_FLASH = true;

    // =======================================================================
    // Codes couleur Minecraft -> RGB
    // Valeurs identiques à celles de l'ancien GlintColors et aux couleurs
    // vanilla, pour que la teinte du glint corresponde au nom de l'item.
    // =======================================================================
    /** Tag posé par {@code GenerateLuckyItemProcedure.GLINT_NBT_KEY}. */
    public static final String GLINT_NBT_KEY = "dlbGlint";

    /**
     * Tag posé par la commande {@code /glint <couleur>} (voir
     * {@code deepluckyblock.procedures.GlintCommand}) : couleur FORCÉE,
     * prioritaire sur absolument tout (rang, thème d'enchantement...).
     *
     * <p>Contrairement à {@link #GLINT_NBT_KEY}, la présence du tag suffit
     * (on ne teste pas {@code != 0}) : un {@code /glint black} doit pouvoir
     * produire un halo NOIR, alors que {@code 0} sert de sentinel "aucune
     * couleur" partout ailleurs dans cette classe.
     *
     * <p>Fonctionne même sur un item totalement dépourvu d'enchantement :
     * {@code GlintCommand} pose en plus le composant vanilla
     * {@code DataComponents.ENCHANTMENT_GLINT_OVERRIDE = true}, qui force
     * {@code ItemStack.hasFoil()} à {@code true} indépendamment de
     * {@code isEnchanted()} — c'est ce composant, et non une astuce du mod,
     * qui rend le halo visible ; cette classe ne fait que le TEINTER.
     */
    public static final String GLINT_FORCE_NBT_KEY = "dlbGlintForce";

    private static final Map<Character, Integer> CODE_TO_RGB = buildCodeTable();

    private static Map<Character, Integer> buildCodeTable() {
        Map<Character, Integer> m = new HashMap<>();
        m.put('0', 0x000000); m.put('1', 0x0000AA);
        m.put('2', 0x00AA00); m.put('3', 0x00AAAA);
        m.put('4', 0xAA0000); m.put('5', 0xAA00AA);
        m.put('6', 0xFFAA00); m.put('7', 0xAAAAAA);
        m.put('8', 0x555555); m.put('9', 0x5555FF);
        m.put('a', 0x55FF55); m.put('b', 0x55FFFF);
        m.put('c', 0xFF5555); m.put('d', 0xFF55FF);
        m.put('e', 0xFFFF55); m.put('f', 0xFFFFFF);
        return m;
    }

    /**
     * Renvoie {@code 0} si aucun glint teinté ne doit s'appliquer (violet
     * vanilla), sinon {@code 0xAARRGGBB}.
     */
    public static int auraColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        // ── COULEUR FORCÉE PAR /glint <couleur> ────────────────────────────
        // Prioritaire sur TOUT (rang, thème...) et valable même SANS aucun
        // enchantement (GlintCommand pose alors ENCHANTMENT_GLINT_OVERRIDE=true
        // pour que hasFoil() renvoie quand même true). Doit donc être testé
        // AVANT le "enchants.isEmpty() -> return 0" ci-dessous.
        try {
            CompoundTag forceTag = deepluckyblock.util.DeepNbt.tag(stack);
            if (forceTag != null && forceTag.contains(GLINT_FORCE_NBT_KEY, 99)) {
                int forced = forceTag.getInt(GLINT_FORCE_NBT_KEY) & 0xFFFFFF;
                log("aura('forcé par /glint') -> #" + Integer.toHexString(0xFF000000 | forced));
                return 0xFF000000 | forced;
            }
        } catch (Throwable t) {
            // tag illisible -> on retombe sur le comportement normal
        }

        // On lit getEnchantments() et non isEnchanted() : pour un LIVRE enchanté
        // les enchantements sont dans "StoredEnchantments" et isEnchanted() peut
        // renvoyer false. Un item non enchanté renvoie une map vide -> 0.
        Map<Enchantment, Integer> enchants;
        try {
            enchants = deepluckyblock.util.DeepEnchant.map(stack); // PORT 1.21.1
        } catch (Throwable t) {
            return 0;
        }
        if (enchants == null || enchants.isEmpty()) return 0;

        String name;
        try {
            name = stack.getHoverName().getString();
        } catch (Throwable t) {
            return 0;
        }

        // Le Bo' Cube de Bois garde le glint violet vanilla "subtil"
        // (comportement déjà en place dans l'ancien GlintColors).
        if (name != null && name.contains("Bo' Cube de Bois")) {
            log("aura('Bo' Cube de Bois') -> violet vanilla");
            return 0;
        }

        // ── COULEUR EXACTE (rangs hors des 16 codes vanilla) ──────────────────
        // GenerateLuckyItemProcedure rend 4 rangs (3 gris distincts + un
        // vert-jaune) via TextColor : leur nom stylé ne contient AUCUN code §,
        // donc parseColorCode ne peut pas les voir. La couleur exacte est posée
        // dans le tag NBT "dlbGlint". Prioritaire sur le parsing §.
        // Les 13 autres rangs n'ont pas ce tag : chemin hérité inchangé.
        try {
            CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
            if (tag != null && tag.contains(GLINT_NBT_KEY, 99)) {
                int exact = tag.getInt(GLINT_NBT_KEY) & 0xFFFFFF;
                if (exact != 0) {
                    log("aura('" + name + "') rang exact (NBT) -> #" + Integer.toHexString(0xFF000000 | exact));
                    return 0xFF000000 | exact;
                }
            }
        } catch (Throwable t) {
            // tag illisible -> on retombe sur le parsing §
        }

        int rgb = parseColorCode(name);
        if (rgb != 0) {
            int argb = 0xFF000000 | (rgb & 0xFFFFFF);
            log("aura('" + name + "') rang -> #" + Integer.toHexString(argb));
            return argb;
        }

        if (THEME_FALLBACK) {
            int themed = themeColor(enchants);
            if (themed != 0) {
                log("aura('" + name + "') sans rang -> thème #" + Integer.toHexString(themed));
                return themed;
            }
        }
        log("aura('" + name + "') aucun code couleur de rang -> violet vanilla");
        return 0;
    }

    /**
     * Relit le DERNIER code {@code §} du nom (même algorithme que l'ancien
     * {@code GlintColors.parseColorCode}) : c'est celui du suffixe de rang, donc
     * la couleur du grade. Renvoie 0 si le nom n'en porte aucun.
     *
     * <p><b>Limite connue (héritée) :</b> {@code 0} sert à la fois de « pas de
     * code couleur » et de valeur du noir pur. Un rang {@code §0} retombe donc sur
     * le glint violet vanilla, exactement comme dans l'ancien GlintColors. Les
     * rangs sombres réels ({@code §8} = 0x555555) sont teintés normalement.
     */
    public static int parseColorCode(String s) {
        if (s == null) return 0;
        for (int i = s.length() - 2; i >= 0; i--) {
            if (s.charAt(i) == '\u00a7') {
                Integer rgb = CODE_TO_RGB.get(Character.toLowerCase(s.charAt(i + 1)));
                if (rgb != null) return rgb;
            }
        }
        return 0;
    }

    /** Repli « thème d'enchantement » : le plus haut niveau, départage par ID. */
    private static int themeColor(Map<Enchantment, Integer> enchants) {
        int best = 0;
        int bestLevel = -1;
        String bestId = null;
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            Enchantment ench = e.getKey();
            if (ench == null) continue;
            String id;
            try {
                var rl = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
                if (rl == null) continue;
                id = rl.toString();
            } catch (Throwable t) {
                continue;
            }
            int lvl = e.getValue() == null ? 0 : e.getValue();
            int color = DeepGlintColor.resolve(id, lvl);
            if (color == 0xFFFFFFFF) continue;                 // pas de thème
            if (lvl > bestLevel || (lvl == bestLevel && (bestId == null || id.compareTo(bestId) < 0))) {
                best = color;
                bestLevel = lvl;
                bestId = id;
            }
        }
        return best;
    }

    // =======================================================================
    // Couleur courante, pour les mixins de rendu (contrat du mod d'origine)
    // =======================================================================
    private static int current = 0;

    /** T311-GLINT-INTENSE : true uniquement pendant la fenêtre active d'attaque/tir/impact. */
    private static boolean currentIntense = false;

    public static int currentRgb() { return current; }

    public static boolean isCurrentIntense() { return currentIntense; }

    public static void capture(ItemStack stack) {
        int v = auraColor(stack);
        current = v;
        currentIntense = false;
        if (DEBUG && stack != null && !stack.isEmpty()) {
            log("capture -> #" + Integer.toHexString(v));
        }
    }

    /**
     * T310/T311-GLINT-INTENSE : Capture la couleur du glint avec surcharge temporaire
     * HAUTE INTENSITÉ pendant l'action active (frappe en mêlée, tir arc/arbalète/trident ou
     * impact sur armure portée), puis retour immédiat à la couleur ET à l'intensité
     * normales ({@link #auraColor(ItemStack)}) dès que {@code activeAction} repasse à {@code false}.
     */
    public static void captureWithFlash(ItemStack stack, boolean activeAction,
                                        int cycleIndex, boolean defensiveArmor) {
        int base = auraColor(stack);
        if (ACTIVE_SWING_FLASH && activeAction && stack != null && !stack.isEmpty()) {
            // Respecte explicitement /glint clear (ENCHANTMENT_GLINT_OVERRIDE == false)
            Boolean override = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            if (!Boolean.FALSE.equals(override)) {
                int flash = DeepGlintColor.resolveActiveFlash(stack, cycleIndex, defensiveArmor);
                int chosen = (flash != 0) ? flash : base;
                if (chosen != 0) {
                    current = chosen;
                    currentIntense = true;
                    if (DEBUG) {
                        log("captureWithFlash (INTENSE) -> #" + Integer.toHexString(chosen)
                                + " (retour prévu -> #" + Integer.toHexString(base) + ")");
                    }
                    return;
                }
            }
        }
        current = base;
        currentIntense = false;
        if (DEBUG && stack != null && !stack.isEmpty()) {
            log("capture -> #" + Integer.toHexString(base));
        }
    }

    public static void clear() {
        current = 0;
        currentIntense = false;
    }

    // =======================================================================
    private static long lastLog = 0;

    private static void log(String msg) {
        if (!DEBUG) return;
        long now = System.currentTimeMillis();
        if (now - lastLog < 300) return;   // throttle ~3/s
        lastLog = now;
        deepluckyblock.util.DebugLog.out("[AURA] " + msg);
    }
}
