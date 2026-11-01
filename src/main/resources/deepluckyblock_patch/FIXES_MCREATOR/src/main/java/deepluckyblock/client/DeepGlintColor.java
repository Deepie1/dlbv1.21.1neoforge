package deepluckyblock.client;

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * DeepGlintColor — résolveur de COULEUR DE THÈME pour les enchantements de
 * Deep's Lucky Block. Même palette que deeps_enchantments : chaque famille
 * (malédiction, nature, feu, eau, poison, vitesse, ombre, dragon, air, vent,
 * dégâts, force, ...) a une couleur, et la force (niveau/luck) assombrit ou
 * éclaircit la teinte.
 *
 * C'est le "nouvel environnement" qui permet d'avoir un glint COLORÉ sur les
 * enchantements du mod, y compris en main / inventaire via la lib Glint & Glamour
 * (voir DeepCustomGlint).
 */
public final class DeepGlintColor {

    /** Mode debug : true = log [DLB-GLINT] (résolution). false = silencieux. */
    public static boolean DEBUG = false;

    // Couleurs "pleines" (0xFFRRGGBB) par famille.
    private static final int CURSE    = 0xFFFF5555;
    private static final int NATURE   = 0xFF28B440;
    private static final int FIRE     = 0xFFFF5C08;
    private static final int WATER    = 0xFF286EFF;
    private static final int POISON   = 0xFF1C8C40;
    private static final int SPEED    = 0xFF3A2E80;
    private static final int SHADOW   = 0xFF1E1030;
    private static final int DRAGON   = 0xFFB28CFF;
    private static final int AIR      = 0xFFDAE8F4;
    private static final int WIND     = 0xFF969CA6;
    private static final int DAMAGE   = 0xFFC8CDD8;
    private static final int FORCE    = 0xFFFF7A14;
    private static final int UNDEAD   = 0xFF3A5C46;
    private static final int LIGHT    = 0xFFF8F8D4;
    private static final int ECHO     = 0xFF50C4CE;
    private static final int MAGIC    = 0xFF8252CC;
    private static final int CHRONO   = 0xFF969EB2;
    private static final int LOOT     = 0xFFFFD656;
    private static final int WHITE    = 0xFFFFFFFF;

    // =======================================================================
    // COULEUR DU RANG (ajout du 2026-08-31)
    // -----------------------------------------------------------------------
    // GenerateLuckyItemProcedure colore le NOM de l'item avec la couleur du
    // rang (recolorNameToRank, l. 1122). C'est cette couleur-là qui doit teinter
    // le glint. GlintColors la relit déjà côté client pour le glint vanilla ;
    // la table et l'algorithme ci-dessous sont IDENTIQUES aux siens, pour que les
    // deux moteurs (mixins maison et Glint & Glamour) tombent toujours sur la
    // même couleur. Aucune dépendance client ici : appelable côté serveur.
    // =======================================================================

    /** Codes couleur Minecraft -> RGB. Valeurs identiques à GlintColors.CODE_TO_RGB. */
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
     * Couleur du RANG de l'item (0x00RRGGBB), lue dans le DERNIER code § de son
     * nom — exactement comme {@code GlintColors.capture}. Renvoie {@code 0} si le
     * nom ne porte aucun code couleur (0 est aussi la valeur « pas de
     * recoloration » de GlintColors, donc les deux moteurs restent d'accord).
     *
     * <p>Attention : {@code 0xFFFFFF} est une couleur de rang légitime (§f),
     * d'où le sentinel 0 et non WHITE.
     */
    public static int gradeColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            return parseColorCode(stack.getHoverName().getString());
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Même algorithme que {@code GlintColors.parseColorCode} : scan vers l'arrière. */
    public static int parseColorCode(String s) {
        if (s == null) return 0;
        for (int i = s.length() - 2; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '\u00a7') {
                Integer rgb = CODE_TO_RGB.get(Character.toLowerCase(s.charAt(i + 1)));
                if (rgb != null) return rgb;
            }
        }
        return 0;
    }

    private DeepGlintColor() {}

    /**
     * Couleur de thème d'un enchantement, déduite de son id + de sa force.
     * @param id     registry id complet (ex. "deep_lucky_block:water_arrow")
     * @param level  niveau de l'enchantement (0-100 pour les god)
     * @return 0xFFRRGGBB (WHITE si aucun thème)
     */
    public static int resolve(String id, int level) {
        if (id == null) return WHITE;
        String s = id.toLowerCase(Locale.ROOT);

        // Malédictions (curse) → rouge forcé.
        if (s.contains("curse") || s.contains("cursed") || s.contains("blemish")
                || s.contains("unlucky")) return CURSE;

        int base = baseColor(s);
        if (base == WHITE) return WHITE;

        // Force : plus le niveau/luck est haut, plus la teinte est saturée/foncée.
        double strength = Math.max(0.0, Math.min(1.0, (level >= 0 ? level : 0) / 100.0));
        return shade(base, strength);
    }

    /** Couleur "famille", non modulée, à partir des mots-clés de l'id. */
    private static int baseColor(String s) {
        // Feu / lave / météore / phénix / foudre / heat
        if (s.contains("fire") || s.contains("flame") || s.contains("blaze")
                || s.contains("lava") || s.contains("magma") || s.contains("sweeping_fire")
                || s.contains("meteor") || s.contains("phoenix") || s.contains("thunder")
                || s.contains("lightning") || s.contains("storm") || s.contains("scorch")
                || s.contains("ember") || s.contains("infernal") || s.contains("ember")
                || s.contains("trailblazer") || s.contains("campfire")) return FIRE;

        // Eau / océan / glace / conduit / hydro / tide
        if (s.contains("water") || s.contains("ocean") || s.contains("ice")
                || s.contains("frost") || s.contains("conduit") || s.contains("hydro")
                || s.contains("tide") || s.contains("fish") || s.contains("amphib")
                || s.contains("buoy") || s.contains("undertow") || s.contains("hydra")
                || s.contains("snow")) return WATER;

        // Nature / récolte / mine / bois / harvest
        if (s.contains("nature") || s.contains("harvest") || s.contains("lumber")
                || s.contains("tree") || s.contains("replant") || s.contains("compost")
                || s.contains("vein") || s.contains("sickle") || s.contains("weeding")
                || s.contains("hoe") || s.contains("farm") || s.contains("hoe_harvest")
                || s.contains("core_harvest") || s.contains("loot_harvest")
                || s.contains("bountiful") || s.contains("scoop") || s.contains("shovel")
                || s.contains("ore") || s.contains("prospect") || s.contains("mining")) return NATURE;

        // Poison / venin / wither
        if (s.contains("poison") || s.contains("venom") || s.contains("wither")
                || s.contains("toxic") || s.contains("fang") || s.contains("scorpion")
                || s.contains("briar")) return POISON;

        // Vitesse / night vision / swiftness / haste
        if (s.contains("speed") || s.contains("swift") || s.contains("night_vision")
                || s.contains("haste") || s.contains("sprint") || s.contains("dash")
                || s.contains("leap") || s.contains("stride") || s.contains("pace")
                || s.contains("momentum") || s.contains("wind") || s.contains("gale")
                || s.contains("shadow_sprint") || s.contains("step") || s.contains("jump")) return SPEED;

        // Ombres / fantôme / voile / stealth
        if (s.contains("shadow") || s.contains("phantom") || s.contains("veil")
                || s.contains("stealth") || s.contains("ghost") || s.contains("wraith")
                || s.contains("dark") || s.contains("night") || s.contains("sneak")
                || s.contains("dread")) return SHADOW;

        // Dragon / ender / vide / téléport / warp
        if (s.contains("dragon") || s.contains("ender") || s.contains("void")
                || s.contains("teleport") || s.contains("warp") || s.contains("dimension")
                || s.contains("end_") || s.contains("phase") || s.contains("portal")
                || s.contains("chromatic") || s.contains("echo_strike")) return DRAGON;

        // Air pur / souffle / breath / oxygen
        if (s.contains("breath") || s.contains("oxygen") || s.contains("air")
                || s.contains("hover") || s.contains("fall") || s.contains("feather")
                || s.contains("glid") || s.contains("wing") || s.contains("elytra")) return AIR;

        // Vent / déplacement (mouvement)
        if (s.contains("wind") || s.contains("air_brake") || s.contains("aerodynamic")
                || s.contains("windrunner") || s.contains("runner") || s.contains("slide")
                || s.contains("roll")) return WIND;

        // Dégâts / sharpness / crit / strike (gris clair)
        if (s.contains("damage") || s.contains("sharp") || s.contains("crit")
                || s.contains("strike") || s.contains("slash") || s.contains("edge")
                || s.contains("blade") || s.contains("sword") || s.contains("sweep")
                || s.contains("hit") || s.contains("strike") || s.contains("pierce")
                || s.contains("execute") || s.contains("slayer") || s.contains("brutal")
                || s.contains("savage") || s.contains("mass_slaughter")
                || s.contains("annihilat")) return DAMAGE;

        // Force / power explicite / mighty / strong
        if (s.contains("power") || s.contains("mighty") || s.contains("strength")
                || s.contains("strong") || s.contains("force") || s.contains("thorns")
                || s.contains("heavy") || s.contains("bash")) return FORCE;

        // Mort-vivant / âme / soul / undead / lifesteal
        if (s.contains("soul") || s.contains("undead") || s.contains("blood")
                || s.contains("leech") || s.contains("life") || s.contains("devour")
                || s.contains("vamp") || s.contains("wither_edge")) return UNDEAD;

        // Lumière / bénédiction / holy / bless / divine
        if (s.contains("bless") || s.contains("divine") || s.contains("holy")
                || s.contains("light") || s.contains("priest") || s.contains("sacred")
                || s.contains("angel") || s.contains("benedict")) return LIGHT;

        // Son / écho / sonar
        if (s.contains("echo") || s.contains("sound") || s.contains("sonar")
                || s.contains("resonance") || s.contains("whisper")) return ECHO;

        // Magie / mystic / arcane / mental
        if (s.contains("mystic") || s.contains("magic") || s.contains("arcane")
                || s.contains("mental") || s.contains("focus") || s.contains("spell")
                || s.contains("concentration") || s.contains("ardent")) return MAGIC;

        // Chrono / temps / mending / durability
        if (s.contains("chrono") || s.contains("time") || s.contains("mending")
                || s.contains("durab") || s.contains("repair") || s.contains("unbreaking")
                || s.contains("unbreakable") || s.contains("eternal")) return CHRONO;

        // Butin / loot / fortune / magnétique / plunder
        if (s.contains("loot") || s.contains("fortune") || s.contains("magnet")
                || s.contains("plunder") || s.contains("greed") || s.contains("treasure")
                || s.contains("recycle") || s.contains("attract")) return LOOT;

        return extraFamily(s);
    }

    /**
     * COMPLÉMENT du 2026-08-31 — appelé UNIQUEMENT si aucune famille ci-dessus
     * n'a matché : il ne peut donc jamais modifier une couleur déjà attribuée.
     * Il couvre les ~130 IDs du mod (gants, tirs, corps, commandement, debuffs…)
     * qui passaient à travers la table d'origine et restaient sans glint coloré.
     */
    private static int extraFamily(String s) {
        // NB : s contient le namespace ("deep_lucky_block:..."), donc un mot-clé
        // comme "block" matcherait TOUS les IDs du mod. Les mots-clés ambigus
        // sont testés par s.endsWith(":...") et non s.contains(...).
        if (s.contains("brimstone") || s.contains("molten") || s.contains("torch")
                || s.contains("blazing") || s.contains("burning") || s.contains("flint")
                || s.contains("firarain") || s.contains("explosive")) return FIRE;

        if (s.contains("harpoon") || s.contains("trident") || s.contains("catch")
                || s.contains("rain") || s.contains("hydr")) return WATER;

        if (s.contains("pebble") || s.contains("earth") || s.contains("food")) return NATURE;

        if (s.contains("corrosion") || s.contains("trauma") || s.contains("wound")
                || s.contains("fracture") || s.contains("cripple") || s.contains("sticky")
                || s.contains("exhaust")) return POISON;

        if (s.contains("adrenaline") || s.contains("exercise") || s.contains("fast")
                || s.contains("rhythm") || s.contains("peregrine") || s.contains("strafe")
                || s.contains("evasive") || s.contains("foot") || s.contains("escape")
                || s.contains("panic") || s.contains("horse_boots") || s.contains("rapid")) return SPEED;

        if (s.contains("fear") || s.contains("stinky") || s.contains("noise")
                || s.contains("perception") || s.contains("sense")) return SHADOW;

        if (s.contains("assimilation") || s.contains("chaos") || s.contains("transfer")
                || s.contains("warden") || s.contains("phasing")) return DRAGON;

        if (s.contains("cloud") || s.contains("bounc") || s.contains("landing")
                || s.contains("hover")) return AIR;

        if (s.contains("beheading") || s.contains("decapit") || s.contains("armor_pry")
                || s.contains("armor_shatter") || s.contains("blunt") || s.contains("gauntlet")
                || s.contains("punch") || s.contains("kill") || s.contains("hatred")
                || s.contains("lone_wolf") || s.contains("solo_combat") || s.contains("peerless")
                || s.contains("veteran") || s.contains("battle") || s.contains("berserk")
                || s.contains("disarm") || s.contains("shield_quake") || s.endsWith(":block")
                || s.contains("sniper") || s.contains("scatter") || s.contains("shot")
                || s.contains("shoo")
                || s.contains("arrow") || s.contains("skewer") || s.contains("ricochet")
                || s.contains("grapple") || s.contains("string_snap") || s.contains("cavalier")
                || s.contains("ranger")) return DAMAGE;

        if (s.contains("steel_body") || s.contains("tempered") || s.contains("sturdy")
                || s.contains("brute") || s.contains("meat") || s.contains("stomach")
                || s.contains("glutton") || s.contains("corpulent") || s.contains("chest_tightness")
                || s.contains("iron_lung") || s.contains("hypoxia") || s.contains("resistance")
                || s.contains("toughness") || s.contains("protection") || s.contains("shelter")
                || s.contains("shield_efficiency") || s.contains("extension") || s.contains("reach")
                || s.contains("commander") || s.contains("general") || s.contains("leader")
                || s.contains("king") || s.contains("army") || s.contains("summon")) return FORCE;

        if (s.contains("thirst")) return UNDEAD;

        if (s.contains("loyal")) return LIGHT;

        if (s.contains("delay")) return CHRONO;

        if (s.contains("extract") || s.contains("retrieval") || s.contains("toy")) return LOOT;

        return WHITE;
    }

    /**
     * Module la teinte de base selon la force :
     *  - s <= 0.5 : mélange blanc → teinte (plus la force monte, plus c'est coloré) ;
     *  - s > 0.5  : assombrit vers 52 % de luminosité (fort = plus profond).
     */
    private static int shade(int rgb, double s) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int outR, outG, outB;
        if (s <= 0.5) {
            double t = s * 2.0;   // 0..1
            outR = (int) (255 + (r - 255) * t);
            outG = (int) (255 + (g - 255) * t);
            outB = (int) (255 + (b - 255) * t);
        } else {
            double t = (s - 0.5) * 2.0; // 0..1
            double lum = 1.0 - 0.48 * t;  // 1.0 → 0.52
            outR = (int) (r * lum);
            outG = (int) (g * lum);
            outB = (int) (b * lum);
        }
        outR = clamp255(outR); outG = clamp255(outG); outB = clamp255(outB);
        return 0xFF000000 | (outR << 16) | (outG << 8) | outB;
    }

    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }

    /** Convertit 0xFFRRGGBB → int[] {0..255} pour la lib CustomGlint. */
    public static int[] toRgbArray(int rgb) {
        return new int[]{ (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF };
    }

    // =======================================================================
    // T310-GLINT-FLASH : Couleur active d'action (swing d'arme / tir / proc)
    // branchée directement sur CE moteur DeepGlintColor (0 nouveau système,
    // palette bornée aux constantes de famille -> cache EnchantGlintTyped fixe).
    // =======================================================================
    private static final int FLASH_POISON  = 0xFF28EE58; // Vert émeraude toxique vif
    private static final int FLASH_FIRE    = 0xFFFF5C08; // Orange ardent (FIRE)
    private static final int FLASH_ECHO    = 0xFF38EEFF; // Cyan électrique / foudre / sonique
    private static final int FLASH_WATER   = 0xFF2888FF; // Azur polaire / océan
    private static final int FLASH_BLOOD   = 0xFFFF1E38; // Rouge sang / vampirisme / exécution
    private static final int FLASH_SHADOW  = 0xFF9B38FF; // Violet ombre / Wither
    private static final int FLASH_DRAGON  = 0xFFC47AFF; // Magenta Ender / vide / téléport
    private static final int FLASH_LIGHT   = 0xFFFFE048; // Or solaire sacré
    private static final int FLASH_NATURE  = 0xFF28D848; // Vert sylvestre / récolte
    private static final int FLASH_MAGIC   = 0xFF9E58FF; // Violet arcanique
    private static final int FLASH_AIR     = 0xFFD8F2FF; // Blanc céleste / bourrasque
    private static final int FLASH_FORCE   = 0xFFFF8418; // Ambre cinétique / impact lourd
    private static final int FLASH_SPEED   = 0xFF4C7CFF; // Cobalt vélocité
    private static final int FLASH_CURSE   = 0xFFFF4444; // Rouge malédiction

    /**
     * T310-GLINT-FLASH : Résout la couleur de flash temporaire de l'item pendant
     * un coup / tir / blocage / proc défensif, à partir des familles existantes de
     * {@link DeepGlintColor}.
     *
     * <p>Priorité aux enchantements élémentaires / procs actifs (Poison, Feu,
     * Foudre/Écho, Eau/Givre, Sang/Vampirisme, Ombre/Wither, Dragon/Vide, Sacré,
     * Nature, Magie) afin qu'un enchantement passif utilitaire ({@code unbreaking},
     * {@code mending}, {@code soul_bound}) ne masque jamais le flash élémentaire
     * actif (ex. vert poison pendant le coup d'épée). Si l'item possède plusieurs
     * éléments actifs de même rang, {@code cycleIndex} alterne proprement d'un
     * coup au suivant. Renvoie {@code 0} si aucun thème actif n'est présent.
     */
    public static int resolveActiveFlash(ItemStack stack, int cycleIndex, boolean defensiveArmor) {
        if (stack == null || stack.isEmpty()) return 0;
        Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchants;
        try {
            enchants = deepluckyblock.util.DeepEnchant.map(stack);
        } catch (Throwable t) {
            return 0;
        }
        if (enchants == null || enchants.isEmpty()) return 0;

        int[] tier1 = new int[8];
        int tier1Count = 0;
        int bestTier2Color = 0;
        int bestTier2Lvl = -1;

        for (Map.Entry<net.minecraft.world.item.enchantment.Enchantment, Integer> e : enchants.entrySet()) {
            var ench = e.getKey();
            if (ench == null) continue;
            net.minecraft.resources.ResourceLocation rl;
            try {
                rl = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
            } catch (Throwable t) {
                continue;
            }
            if (rl == null) continue;
            String s = rl.toString().toLowerCase(Locale.ROOT);
            int lvl = e.getValue() == null ? 1 : e.getValue();

            // Ignore les enchantements purement passifs de durabilité / sauvegarde
            if (s.endsWith(":unbreaking") || s.endsWith(":mending") || s.endsWith(":basic_mending")
                    || s.endsWith(":chrono_mending") || s.endsWith(":basic_chrono_mending")
                    || s.endsWith(":unbreakable") || s.endsWith(":eternal")
                    || s.endsWith(":soul_bound") || s.endsWith(":infinity")
                    || s.endsWith(":looting") || s.endsWith(":fortune")
                    || s.endsWith(":efficiency") || s.endsWith(":protection")) {
                continue;
            }

            int t1 = classifyPrimaryElementFlash(s);
            if (t1 != 0) {
                boolean already = false;
                for (int i = 0; i < tier1Count; i++) {
                    if (tier1[i] == t1) { already = true; break; }
                }
                if (!already && tier1Count < tier1.length) {
                    tier1[tier1Count++] = t1;
                }
                continue;
            }

            int t2 = classifySecondaryCombatFlash(s, defensiveArmor);
            if (t2 != 0 && lvl > bestTier2Lvl) {
                bestTier2Lvl = lvl;
                bestTier2Color = t2;
            }
        }

        if (tier1Count == 1) return tier1[0];
        if (tier1Count > 1) {
            int idx = Math.floorMod(cycleIndex, tier1Count);
            return tier1[idx];
        }
        return bestTier2Color;
    }

    private static int classifyPrimaryElementFlash(String s) {
        // Poison / venin / toxique / ronces -> Vert émeraude vif
        if (s.contains("poison") || s.contains("venom") || s.contains("toxic")
                || s.contains("fang") || s.contains("scorpion") || s.contains("briar")) {
            return FLASH_POISON;
        }
        // Foudre / électrique / surcadencé / onde sonique -> Cyan électrique
        if (s.contains("thunder") || s.contains("lightning") || s.contains("storm")
                || s.contains("channeling") || s.contains("electric") || s.contains("overclock")
                || s.contains("overcharge") || s.contains("hydro_shock")
                || s.contains("echo") || s.contains("sonic") || s.contains("sonar")) {
            return FLASH_ECHO;
        }
        // Feu / lave / météore / phénix / soufre / braise -> Orange ardent
        if (s.contains("fire") || s.contains("flame") || s.contains("blaze")
                || s.contains("blazing") || s.contains("burning") || s.contains("lava")
                || s.contains("magma") || s.contains("meteor") || s.contains("phoenix")
                || s.contains("scorch") || s.contains("ember") || s.contains("infernal")
                || s.contains("brimstone") || s.contains("molten") || s.contains("flint")
                || s.contains("torch") || s.contains("fiery")) {
            return FLASH_FIRE;
        }
        // Eau / givre / océan / courant -> Azur polaire
        if (s.contains("water") || s.contains("ocean") || s.contains("ice")
                || s.contains("frost") || s.contains("conduit") || s.contains("hydro")
                || s.contains("tide") || s.contains("undertow") || s.contains("snow")
                || s.contains("riptide")) {
            return FLASH_WATER;
        }
        // Sang / vampirisme / sangsue / exécution -> Rouge sang
        if (s.contains("blood") || s.contains("leech") || s.contains("vamp")
                || s.contains("devour") || s.contains("execute") || s.contains("behead")
                || s.contains("decapit") || s.contains("killing_sense") || s.contains("last_stand")) {
            return FLASH_BLOOD;
        }
        // Wither / ombre / fantôme / spectre -> Violet sombre lumineux
        if (s.contains("wither") || s.contains("shadow") || s.contains("phantom")
                || s.contains("wraith") || s.contains("umbral") || s.contains("dark")
                || s.contains("dread") || s.contains("stealth") || s.contains("soul")) {
            return FLASH_SHADOW;
        }
        // Dragon / Ender / vide / téléport / phase -> Magenta Ender
        if (s.contains("dragon") || s.contains("ender") || s.contains("void")
                || s.contains("teleport") || s.contains("swap_arrow") || s.contains("warp")
                || s.contains("phasing") || s.contains("dimension")) {
            return FLASH_DRAGON;
        }
        // Lumière / sacré / divin -> Or solaire
        if (s.contains("holy") || s.contains("divine") || s.contains("sacred")
                || s.contains("radiant") || s.contains("light_heart") || s.contains("angel")
                || s.contains("smite") || s.contains("blessing")) {
            return FLASH_LIGHT;
        }
        // Nature / récolte / sylviculture / veine -> Vert nature
        if (s.contains("nature") || s.contains("harvest") || s.contains("lumber")
                || s.contains("tree_felling") || s.contains("replant") || s.contains("compost")
                || s.contains("vein_miner") || s.contains("earth_heart")) {
            return FLASH_NATURE;
        }
        // Magie / chaos / arcanique -> Violet arcanique
        if (s.contains("chaos") || s.contains("arcane") || s.contains("mystic")
                || s.contains("magic")) {
            return FLASH_MAGIC;
        }
        return 0;
    }

    private static int classifySecondaryCombatFlash(String s, boolean defensiveArmor) {
        if (s.contains("curse") || s.contains("cursed") || s.contains("blemish")) {
            return FLASH_CURSE;
        }
        int base = baseColor(s);
        if (base == AIR || base == WIND) return FLASH_AIR;
        if (base == FORCE) return FLASH_FORCE;
        if (base == SPEED) return FLASH_SPEED;
        if (!defensiveArmor && base == DAMAGE && (s.contains("crit") || s.contains("seismic")
                || s.contains("brutal") || s.contains("titan") || s.contains("giant")
                || s.contains("annihilat") || s.contains("supreme"))) {
            return FLASH_FORCE;
        }
        return 0;
    }
}
