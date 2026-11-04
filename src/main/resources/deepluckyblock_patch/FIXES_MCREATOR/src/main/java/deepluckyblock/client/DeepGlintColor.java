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
    private static final int NATURE   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.WOOD.primary;
    private static final int FIRE     = 0xFF000000 | deepluckyblock.enchantments.DeepElement.FIRE.primary;
    private static final int WATER    = 0xFF000000 | deepluckyblock.enchantments.DeepElement.WATER.primary;
    private static final int POISON   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.POISON.primary;
    private static final int SPEED    = 0xFF3A2E80;
    private static final int SHADOW   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.SHADOW.primary;
    private static final int CHAOS_BASE = 0xFF000000 | deepluckyblock.enchantments.DeepElement.CHAOS.primary;
    private static final int DRAGON   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.VOID.primary;
    private static final int AIR      = 0xFFDAE8F4;
    private static final int WIND     = 0xFF969CA6;
    private static final int DAMAGE   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.METAL.primary;
    private static final int FORCE    = 0xFFFF7A14;
    private static final int UNDEAD   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.BLOOD.primary;
    private static final int LIGHT    = 0xFF000000 | deepluckyblock.enchantments.DeepElement.LIGHT.primary;
    private static final int ECHO     = 0xFF000000 | deepluckyblock.enchantments.DeepElement.THUNDER.primary;
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
        // Les familles canoniques sont résolues avant les catégories utilitaires génériques.
        // En particulier, la foudre ne doit plus passer par le Feu et l'exécution par le Métal.
        if (s.contains("thunder") || s.contains("lightning") || s.contains("storm")
                || s.contains("channeling") || s.contains("electric") || s.contains("overclock")
                || s.contains("overcharge") || s.contains("sonic") || s.contains("voltaic")
                || s.contains("galvan") || s.contains("discharge")) return ECHO;
        if (s.contains("blood") || s.contains("leech") || s.contains("vamp")
                || s.contains("devour") || s.contains("execute") || s.contains("behead")
                || s.contains("decapit") || s.contains("guillotine") || s.contains("carnage")
                || s.contains("siphon") || s.contains("undead")) return UNDEAD;
        if (s.contains("chaos") || s.contains("entropy") || s.contains("prism")
                || s.contains("discord") || s.contains("anarch") || s.contains("kaleido")
                || s.contains("pandemon") || s.contains("mirage")) return CHAOS_BASE;

        // Feu / lave / météore / phénix / heat
        if (s.contains("fire") || s.contains("flame") || s.contains("blaze")
                || s.contains("lava") || s.contains("magma") || s.contains("sweeping_fire")
                || s.contains("meteor") || s.contains("phoenix") || s.contains("scorch")
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
        if (s.contains("soul") || s.contains("life") || s.contains("wither_edge")) return UNDEAD;

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
                || s.contains("hover") || s.contains("air_walk") || s.contains("aerial_cushion")) return AIR;

        if (s.contains("beheading") || s.contains("decapit") || s.contains("armor_pry")
                || s.contains("armor_shatter") || s.contains("blunt") || s.contains("gauntlet")
                || s.contains("punch") || s.contains("kill") || s.contains("hatred")
                || s.contains("lone_wolf") || s.contains("duelists_mastery") || s.contains("peerless")
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
     * Module seulement la luminosité selon la force : la teinte reste strictement inchangée.
     */
    private static int shade(int rgb, double s) {
        // Variation de luminosité uniquement : le niveau ne désature jamais vers du blanc
        // et ne peut donc plus transformer le Sang en rose ni créer une teinte étrangère.
        double lum = (s <= 0.5) ? 0.70 + 0.60 * s : 1.0 - 0.48 * (s - 0.5) * 2.0;
        int outR = clamp255((int) (((rgb >> 16) & 0xFF) * lum));
        int outG = clamp255((int) (((rgb >> 8) & 0xFF) * lum));
        int outB = clamp255((int) ((rgb & 0xFF) * lum));
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
    private static final int FLASH_POISON  = 0xFF000000 | deepluckyblock.enchantments.DeepElement.POISON.highlight; // Vert émeraude toxique vif
    private static final int FLASH_FIRE    = 0xFF000000 | deepluckyblock.enchantments.DeepElement.FIRE.highlight; // Orange ardent (FIRE)
    private static final int FLASH_ECHO    = 0xFF000000 | deepluckyblock.enchantments.DeepElement.THUNDER.highlight; // Foudre : jaune pâle canonique, sans accent cyan
    private static final int FLASH_WATER   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.WATER.highlight; // Eau : bleu très clair canonique
    private static final int FLASH_BLOOD   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.BLOOD.highlight; // Rouge sang / vampirisme / exécution
    private static final int FLASH_SHADOW  = 0xFF000000 | deepluckyblock.enchantments.DeepElement.SHADOW.highlight; // Violet ombre / Wither
    private static final int FLASH_DRAGON  = 0xFF000000 | deepluckyblock.enchantments.DeepElement.VOID.highlight; // Gris Ender / vide / téléport
    private static final int FLASH_LIGHT   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.LIGHT.highlight; // Or solaire sacré
    private static final int FLASH_NATURE  = 0xFF000000 | deepluckyblock.enchantments.DeepElement.WOOD.highlight; // Bois clair / récolte
    private static final int FLASH_MAGIC   = 0xFF000000 | deepluckyblock.enchantments.DeepElement.CHAOS.highlight; // Violet arcanique
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

        // T359-GLINT-NO-FAKE-FIRE : une arme à distance ne prend jamais la teinte ambre
        // « force » (qui se lit comme du feu) : seule une famille élémentaire la colore.
        boolean rangedWeapon = isRangedWeapon(stack);

        int[] tier1 = new int[10];
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

            // T350-CURSE-NO-FX : une malédiction ne déclenche jamais d'éclat ni de particule.
            if (deepluckyblock.enchantments.DeepElement.isCursePath(rl.getPath())) {
                continue;
            }

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

            int t2 = classifySecondaryCombatFlash(s, defensiveArmor, rangedWeapon);
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
        // Foudre / électrique / surcadencé / onde sonique -> surbrillance jaune pâle
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
                || s.contains("decapit") || s.contains("predators_scent") || s.contains("guillotine") || s.contains("reapers_scythe") || s.contains("agony_breaker") || s.contains("last_stand")) {
            return FLASH_BLOOD;
        }
        // Wither / ombre / fantôme / spectre -> Violet sombre lumineux
        if (s.contains("wither") || s.contains("shadow") || s.contains("phantom")
                || s.contains("wraith") || s.contains("umbral") || s.contains("dark")
                || s.contains("dread") || s.contains("stealth") || s.contains("soul")) {
            return FLASH_SHADOW;
        }
        // Dragon / Ender / vide / téléport / phase -> gris canonique du Vide
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

    /**
     * T359-GLINT-NO-FAKE-FIRE : la famille « FORCE » est ambre/orangée, ce qui se confond avec le
     * FEU. Sur une arme à distance, elle faisait virer le glint à l'orange dès qu'on bandait l'arc,
     * même sans le moindre enchantement de feu (un simple {@code power} suffisait).
     *
     * <p>Règle désormais : sur arc / arbalète / trident, seuls les enchantements ÉLÉMENTAIRES
     * colorent le flash — un enchantement purement cinétique ({@code power}, {@code punch},
     * {@code heavy_impact}...) ne teinte plus rien (le glint garde sa couleur de rang).</p>
     */
    private static int classifySecondaryCombatFlash(String s, boolean defensiveArmor, boolean rangedWeapon) {
        if (s.contains("curse") || s.contains("cursed") || s.contains("blemish")) {
            return FLASH_CURSE;
        }
        int base = baseColor(s);
        if (base == AIR || base == WIND) return FLASH_AIR;
        if (base == FORCE) return rangedWeapon ? 0 : FLASH_FORCE;
        if (base == SPEED) return FLASH_SPEED;
        if (!defensiveArmor && base == DAMAGE && (s.contains("crit") || s.contains("seismic")
                || s.contains("brutal") || s.contains("titan") || s.contains("giant")
                || s.contains("annihilat") || s.contains("supreme"))) {
            return rangedWeapon ? 0 : FLASH_FORCE;
        }
        return 0;
    }

    /** T359-GLINT-NO-FAKE-FIRE : vrai pour arc, arbalète et trident. */
    private static boolean isRangedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof net.minecraft.world.item.BowItem
                || stack.getItem() instanceof net.minecraft.world.item.CrossbowItem
                || stack.getItem() instanceof net.minecraft.world.item.TridentItem;
    }

    // =======================================================================
    // T333-UNIQUE-CONTEXTUAL-GLOW : Déclenchement intense uniquement lorsque la
    // condition propre à l'enchantement de la pièce est réellement remplie !
    // (Fini le déclenchement générique redondant au simple swing dans le vide ou dégât quelconque.)
    // =======================================================================
    // =======================================================================
    // PALETTE-LOCK : alternance de rôles exacts, sans interpolation RGB entre éléments.
    // =======================================================================
    /**
     * Les cibles sont déjà des rôles de palette exacts. Une interpolation RGB entre deux cibles
     * créerait une teinte intermédiaire étrangère sur une arme multi-éléments : on alterne donc
     * directement les couleurs validées, comme dans le moteur de voxels.
     */
    public static int smoothGlint(ItemStack stack, int target, boolean armorSlot) {
        return target;
    }

    private static final java.util.WeakHashMap<ItemStack, long[]> CONTEXT_GLOW_CACHE = new java.util.WeakHashMap<>();
    private static final long CONTEXT_CACHE_TTL_NS = 90_000_000L; // ~90 ms (< 2 ticks)

    public static int resolveContextualFlash(net.minecraft.world.entity.LivingEntity wearer,
                                             ItemStack stack, boolean defensiveArmor) {
        if (!(wearer instanceof net.minecraft.world.entity.player.Player player)) return 0;
        if (stack == null || stack.isEmpty() || !stack.isEnchanted()) return 0;

        long nowNs = System.nanoTime();
        synchronized (CONTEXT_GLOW_CACHE) {
            long[] cached = CONTEXT_GLOW_CACHE.get(stack);
            if (cached != null && (nowNs - cached[1]) <= CONTEXT_CACHE_TTL_NS) {
                return (int) cached[0];
            }
        }

        int computed = computeContextualFlashUncached(player, stack, defensiveArmor);
        synchronized (CONTEXT_GLOW_CACHE) {
            CONTEXT_GLOW_CACHE.put(stack, new long[]{computed, nowNs});
        }
        return computed;
    }

    private static int computeContextualFlashUncached(net.minecraft.world.entity.player.Player player,
                                                      ItemStack stack, boolean defensiveArmor) {
        Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchants = deepluckyblock.util.DeepEnchant.map(stack);
        if (enchants.isEmpty()) return 0;

        // T359-GLINT-NO-FAKE-FIRE : même règle pour l'éclat contextuel.
        boolean rangedWeapon = isRangedWeapon(stack);

        int[] activeColors = new int[16];
        int activeCount = 0;

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
            String fullId = rl.toString().toLowerCase(Locale.ROOT);
            String path = rl.getPath().toLowerCase(Locale.ROOT);

            if (!isUniqueConditionMet(player, stack, path, defensiveArmor)) {
                continue;
            }

            int col = classifyPrimaryElementFlash(fullId);
            if (col == 0) {
                col = classifySecondaryCombatFlash(fullId, defensiveArmor, rangedWeapon);
            }
            if (col == 0) {
                int base = baseColor(fullId);
                // T359-GLINT-NO-FAKE-FIRE : sur une arme à distance, l'ambre « force » est
                // ignoré (il passait pour du feu) ; les autres teintes restent inchangées.
                if (rangedWeapon && base == FORCE) {
                    base = 0xFFFFFFFF;
                }
                if (base != 0xFFFFFFFF) {
                    col = 0xFF000000 | brightenForFlash(base);
                }
            }
            if (col != 0) {
                boolean dup = false;
                for (int i = 0; i < activeCount; i++) {
                    if (activeColors[i] == col) { dup = true; break; }
                }
                if (!dup && activeCount < activeColors.length) {
                    activeColors[activeCount++] = col;
                }
            }
        }

        if (activeCount == 0) return 0;
        if (activeCount == 1) return activeColors[0];
        int cycle = (player.tickCount / 20) % activeCount;
        return activeColors[Math.floorMod(cycle, activeCount)];
    }

    private static int brightenForFlash(int rgb) {
        float scale = 1.18f;
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * scale));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * scale));
        int b = Math.min(255, Math.round((rgb & 0xFF) * scale));
        return (r << 16) | (g << 8) | b;
    }

    private static boolean isUniqueConditionMet(net.minecraft.world.entity.player.Player player,
                                                ItemStack stack, String path, boolean defensiveArmor) {
        var level = player.level();
        if (level == null) return false;
        net.minecraft.core.BlockPos pos = player.blockPosition();

        // 1) Obscurité / Nuit / Furtivité
        if (path.equals("shadow_heart") || path.equals("voidlings_hunger") || path.equals("umbral_pact")
                || path.equals("shadow_sprint") || path.equals("shadow_hunt")) {
            return level.getMaxLocalRawBrightness(pos) <= 7;
        }
        if (path.equals("echolocation")) {
            return level.getMaxLocalRawBrightness(pos) <= 7 && (player.tickCount % 40) < 10;
        }
        if (path.equals("night_veil") || path.equals("phantom_step") || path.equals("stealth_hunter")) {
            return player.isInvisible() || (player.isShiftKeyDown() && level.getMaxLocalRawBrightness(pos) <= 8);
        }

        // 2) Pleine Lumière / Solaire / Sacré
        if (path.equals("light_heart") || path.equals("light_walker")) {
            return level.getMaxLocalRawBrightness(pos) >= 12;
        }

        // 3) Eau / Pluie / Glace
        if (path.equals("water_heart") || path.equals("tidal_ward") || path.equals("amphibious")
                || path.equals("oceans_blessing") || path.equals("buoy") || path.equals("breath_holding")
                || path.equals("ocean_current") || path.equals("riptide_curse") || path.equals("curse_of_the_depths")) {
            return player.isInWater();
        }
        if (path.equals("conduit_heart") || path.equals("rain_nourishment") || path.equals("hydro_shock")) {
            return player.isInWaterOrRain();
        }
        if (path.equals("tidal_step")) {
            return player.isInWater() && player.onGround();
        }
        if (path.equals("frost_trail") || path.equals("frost_walker") || path.equals("snow_remover")) {
            var below = level.getBlockState(pos.below()).getBlock().getDescriptionId();
            return below.contains("ice") || below.contains("snow") || level.getBlockState(pos).getBlock().getDescriptionId().contains("snow");
        }

        // 4) Feu / Lave / Chaleur
        if (path.equals("blaze_battle") || path.equals("blazing_rage") || path.equals("fire_shell")
                || path.equals("salamanders_vow") || path.equals("scorching_bath")
                || path.equals("infernal_armor") || path.equals("fire_devour") || path.equals("blaze_sanctuary")) {
            return player.isOnFire() || player.isInLava();
        }
        if (path.equals("fire_heart") || path.equals("blaze_walker") || path.equals("magma_walker")) {
            if (player.isOnFire() || player.isInLava()) return true;
            String below = level.getBlockState(pos.below()).getBlock().getDescriptionId();
            return below.contains("magma") || below.contains("obsidian") || below.contains("lava");
        }
        if (path.equals("ember_step")) {
            return player.isSprinting() && player.onGround();
        }

        // 5) Orage / Foudre
        if (path.equals("electric_heart") || path.equals("thundercall_pact") || path.equals("chain_lightning")
                || path.equals("curse_of_the_sky_conductor") || path.equals("curse_of_the_lightning_rod") || path.equals("lightning_calamity")) {
            return level.isThundering() || level.isRainingAt(pos);
        }

        // 6) Ciel / Haute Altitude / Vol Élytres / Marche Aérienne / Paroi
        if (path.equals("cloud_stride")) {
            return player.getY() >= 160.0 && !player.isShiftKeyDown();
        }
        if (path.equals("wind_heart")) {
            return player.getY() >= 140.0 || !player.onGround();
        }
        if (path.equals("high_altitude_hypoxia")) {
            return player.getY() > 100.0;
        }
        if (path.equals("ground_skimmer")) {
            return player.isFallFlying() && !level.getBlockState(pos.below(4)).isAir();
        }
        if (path.equals("air_brake")) {
            return player.isFallFlying() && player.isShiftKeyDown();
        }
        if (path.equals("aerodynamic") || path.equals("wind_rider") || path.equals("iron_wings")
                || path.equals("wing_toughness_armor") || path.equals("rocket_blast") || path.equals("curse_of_turbulence")) {
            return player.isFallFlying();
        }
        if (path.equals("air_walk")) {
            if (player.isShiftKeyDown() || player.isInWater() || player.isInLava()) return false;
            var under = level.getBlockState(pos.below());
            return !player.onGround() || under.isAir() || under.is(net.minecraft.world.level.block.Blocks.BARRIER);
        }
        if (path.equals("aerial_cushion_step")) {
            return player.fallDistance >= 2.8f;
        }
        if (path.equals("air_hover") || path.equals("ascending_gale")) {
            return !player.onGround() && player.isShiftKeyDown();
        }
        if (path.equals("cloud_walker") || path.equals("peregrine")) {
            return !player.onGround() && !player.isInWater();
        }
        if (path.equals("wall_runner")) {
            return player.horizontalCollision && !player.onGround();
        }

        // 7) Souterrain / Cible Minerai / Arbre / Culture / Pêche
        if (path.equals("earth_heart") || path.equals("deep_hypoxia") || path.equals("hypoxia_haste")
                || path.equals("burrowing") || path.equals("mining_stability")) {
            return player.getY() < 32.0 && !level.canSeeSky(pos);
        }
        if (path.equals("vein_miner") || path.equals("miners_eye") || path.equals("extracting")
                || path.equals("prospecting") || path.equals("auto_smelt") || path.equals("flint_strike")) {
            return isLookingAtBlockMatching(level, "ore");
        }
        if (path.equals("lumberjack") || path.equals("tree_felling")) {
            return isLookingAtBlockMatching(level, "log") || isLookingAtBlockMatching(level, "stem");
        }
        if (path.equals("farmers_hoe") || path.equals("bountiful_harvest") || path.equals("replanting")
                || path.equals("weeding_hoe") || path.equals("composting")) {
            String below = level.getBlockState(pos.below()).getBlock().getDescriptionId();
            return below.contains("farmland") || isLookingAtBlockMatching(level, "crop")
                    || isLookingAtBlockMatching(level, "wheat") || isLookingAtBlockMatching(level, "carrot")
                    || isLookingAtBlockMatching(level, "potato") || isLookingAtBlockMatching(level, "beetroot");
        }
        if (path.contains("fishing") || path.equals("cooked_catch") || path.equals("curse_of_false_bite")
                || path.equals("curse_of_scorching_line")) {
            return player.fishing != null;
        }

        // 8) PV bas / Faim pleine / Posture / Bouclier / Arc bandé / Monture / Mains nues
        if (path.equals("berserk") || path.equals("adrenaline") || path.equals("second_wind")
                || path.equals("last_stand_escape") || path.equals("last_stand_toughness")
                || path.equals("desperate_kill") || path.equals("life_block") || path.equals("phoenix_rebirth")) {
            return player.getHealth() <= player.getMaxHealth() * 0.35f;
        }
        if (path.equals("gluttony") || path.equals("meat_armor") || path.equals("food_power_strike")) {
            return player.getFoodData().getFoodLevel() >= 18;
        }
        if (path.equals("fast_eating") || path.equals("assimilation") || path.equals("food_is_heaven")) {
            return player.isUsingItem() && player.getUseItem().has(net.minecraft.core.component.DataComponents.FOOD);
        }
        if (path.equals("shelter")) {
            return player.isShiftKeyDown() && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 0.002;
        }
        if (path.equals("anchoring") || path.equals("focused_steadiness") || path.equals("focused_crit")
                || path.equals("focused_punch_gauntlet")) {
            return player.isShiftKeyDown();
        }
        if (path.equals("spring_coil_soles")) {
            return player.isShiftKeyDown() && player.onGround();
        }
        if (path.equals("concentration")) {
            return player.isUsingItem() && player.getDeltaMovement().horizontalDistanceSqr() < 0.002;
        }
        if (path.equals("shield_efficiency") || path.equals("bash_power") || path.equals("bash_damage")
                || path.equals("shield_quake") || path.equals("perfect_parry")) {
            return player.isBlocking();
        }
        if (stack.getItem() instanceof net.minecraft.world.item.BowItem
                || stack.getItem() instanceof net.minecraft.world.item.CrossbowItem
                || stack.getItem() instanceof net.minecraft.world.item.TridentItem) {
            if (stack.getItem() instanceof net.minecraft.world.item.CrossbowItem
                    && net.minecraft.world.item.CrossbowItem.isCharged(stack)) {
                return true;
            }
            return player.isUsingItem() && player.getUseItem() == stack && player.getTicksUsingItem() >= 5;
        }
        if (path.equals("cavalier") || path.equals("horse_boots")) {
            return player.isPassenger();
        }
        if (path.equals("momentum") || path.equals("electric_speed") || path.equals("windrunner")
                || path.equals("sprint_punch_gauntlet") || path.equals("carriers_stride")) {
            return player.isSprinting();
        }
        if (path.equals("slide")) {
            return player.isShiftKeyDown() && player.getDeltaMovement().horizontalDistanceSqr() > 0.03;
        }
        if (path.equals("trailblazer")) {
            String below = level.getBlockState(pos.below()).getBlock().getDescriptionId();
            return below.contains("path") || below.contains("brick") || below.contains("cobblestone");
        }
        if (path.equals("strafe") || path.equals("evasive_footwork")) {
            return Math.abs(player.xxa) > 0.35f;
        }
        if (path.equals("straight_punch_gauntlet") || path.equals("wing_chun_gauntlet")) {
            return player.getMainHandItem().isEmpty();
        }
        if (path.equals("new_edge") || path.equals("promising_blade")) {
            return stack.isDamageableItem() && stack.getDamageValue() == 0 && isAimingAtLivingEntity();
        }
        if (path.equals("battle_hardened") || path.equals("recycle")) {
            return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() * 0.70f;
        }

        // 9) Armures réactives à l'impact UNIQUEMENT si la pièce porte vraiment un enchantement de riposte/réaction
        if (defensiveArmor) {
            if (path.equals("wardenspine") || path.equals("powerful_thorns") || path.equals("ember_core")
                    || path.equals("burning_heart") || path.equals("pain_devour") || path.equals("kinetic_conversion")
                    || path.equals("panic_escape") || path.equals("blaze_repulsion_heart")
                    || path.equals("soul_repulsion_blast") || path.equals("armor_damage")
                    || path.equals("armor_shatter") || path.equals("wound_crack")) {
                return player.hurtTime > 0;
            }
            return false;
        }

        // 10) Armes de mêlée à effet d'impact : s'illuminent UNIQUEMENT sur un coup réellement porté sur une cible vivante (jamais en frappant l'air !)
        return isLandingHitOnTarget(player, stack);
    }

    private static boolean isLookingAtBlockMatching(net.minecraft.world.level.Level level, String keyword) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)) return false;
            if (bhr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
            String desc = level.getBlockState(bhr.getBlockPos()).getBlock().getDescriptionId().toLowerCase(Locale.ROOT);
            return desc.contains(keyword);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAimingAtLivingEntity() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            return mc != null && mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLandingHitOnTarget(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        try {
            if (stack != player.getMainHandItem()) return false;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity target) {
                return target.isAlive() && target.hurtTime >= 6 && (player.swinging || player.attackAnim > 0.0F);
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
