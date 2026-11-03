package deepluckyblock.enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * T351-SUBTLE-VFX : <b>55 déclinaisons discrètes</b> construites sur les familles
 * élémentaires (12 familles × 5 paliers : Bois brun, Poison vert foncé, Terre vert clair/brun...).
 *
 * <p><b>Pourquoi ?</b> Les 50 déclinaisons de {@link WeaponVfxVariant} restaient des déclinaisons des
 * 30 grosses animations : beaucoup de formes qui « englobent » la cible (cercles, colonnes, sphères).
 * Il manquait la petite touche de combat crédible : une braise qui monte, une goutte qui tombe, une
 * poussière qui s'envole, une étincelle qui glisse le long du tranchant...</p>
 *
 * <p>Ces accents sont volontairement minimalistes : 2 à 9 mini-pixels, jamais de forme englobante,
 * 0,2 à 0,8 s de vie. Le palier {@code 0} est le plus visible, le palier {@code 4} est presque un
 * chuchotement — ce qui permet d'avoir une gradation naturelle de discrétion.</p>
 */
public final class WeaponVfxSubtle {

    /** Nombre total d'accents subtils (12 familles × 5 paliers : POISON séparé du Bois en T352b). */
    public static final int COUNT = 60;
    /** Décalage de {@code kind} utilisé sur le réseau pour transporter un accent subtil. */
    public static final int NET_KIND_BASE = 100;
    /**
     * T351-SUBTLE-ECHO : espace d'index complet = 50 accents élémentaires (0..49) puis
     * 50 échos de grosses animations (50..99, voir {@link WeaponVfxSubtleEcho}).
     */
    public static final int TOTAL = COUNT + WeaponVfxSubtleEcho.COUNT;

    /** Famille élémentaire d'un accent par son index global (0..{@code TOTAL-1}). */
    public static DeepElement elementOf(int index) {
        return (index < COUNT) ? get(index).element()
                : WeaponVfxSubtleEcho.get(index - COUNT).element();
    }

    /** Nom lisible d'un accent par son index global. */
    public static String nameOf(int index) {
        return (index < COUNT) ? get(index).name()
                : WeaponVfxSubtleEcho.get(index - COUNT).name();
    }

    /** Description courte (forme / mouvement + nombre de pixels) d'un accent par son index global. */
    public static String detailOf(int index) {
        if (index < COUNT) {
            Subtle s = get(index);
            return "forme " + s.shapeName() + ", " + s.count() + " pixels";
        }
        WeaponVfxSubtleEcho.Echo e = WeaponVfxSubtleEcho.get(index - COUNT);
        return "écho de " + WeaponVfxSubtleEcho.label(e.source())
                + ", mouvement " + e.motionName() + ", " + e.count() + " pixels";
    }

    // ── Formes disponibles (rendues par le moteur de mini-pixels, jamais de forme englobante) ──
    /** Étincelle vive qui file le long du coup. */
    public static final int SHAPE_SPARK       = 0;
    /** Braise qui monte lentement en refroidissant. */
    public static final int SHAPE_EMBER       = 1;
    /** Gouttelette qui retombe sous la gravité. */
    public static final int SHAPE_DROPLET     = 2;
    /** Brume légère qui s'étale et s'efface. */
    public static final int SHAPE_MIST        = 3;
    /** Fragments de feuille / pétale qui tournoient. */
    public static final int SHAPE_LEAF        = 4;
    /** Éclat de givre / cristal qui scintille une fois.  */
    public static final int SHAPE_SHARD       = 5;
    /** Poussière soulevée au point d'impact. */
    public static final int SHAPE_DUST        = 6;
    /** Volute d'ombre qui s'enroule puis se dissipe. */
    public static final int SHAPE_WISP        = 7;
    /** Reflet unique qui balaie l'arc de frappe. */
    public static final int SHAPE_GLINT       = 8;
    /** Micro-éclair sec. */
    public static final int SHAPE_BOLT        = 9;
    /** Bulles qui remontent. */
    public static final int SHAPE_BUBBLE      = 10;
    /** Cendre qui retombe en tournoyant. */
    public static final int SHAPE_ASH         = 11;
    /** Motes de lumière qui flottent sur place. */
    public static final int SHAPE_MOTES       = 12;
    /** Craquelure de pierre qui s'effrite. */
    public static final int SHAPE_CRUMBLE     = 13;
    /** Traînée d'éclats colorés qui dérive (nuance de famille). */
    public static final int SHAPE_DRIFT       = 14;

    /**
     * Un accent subtil concret.
     *
     * @param index    index 0..49 (stable, utilisé par /dlbvfx)
     * @param element  famille élémentaire
     * @param shape    forme parmi les {@code SHAPE_*} ci-dessus
     * @param tier     palier de discrétion 0 (visible) .. 4 (à peine perceptible)
     * @param count    nombre de mini-pixels émis
     * @param size     demi-taille de base d'un pixel
     * @param speed    vitesse d'éclatement
     * @param life     durée de vie en ticks
     * @param gravity  gravité appliquée (positif = tombe, négatif = monte)
     * @param name     nom lisible pour les commandes / la documentation
     */
    public record Subtle(int index, DeepElement element, int shape, int tier,
                         int count, float size, float speed, int life, float gravity, String name) {

        /** Nom lisible de la forme (braise, goutte, brume...). */
        public String shapeName() {
            return nameFor(shape);
        }
    }

    private static final Subtle[] ENTRIES = new Subtle[COUNT];

    static {
        int i = 0;
        for (DeepElement el : DeepElement.values()) {
            for (int tier = 0; tier < 5 && i < COUNT; tier++) {
                int shape = shapeFor(el, tier);
                // Plus le palier monte, plus c'est discret : moins de pixels, plus petits, plus courts.
                int count = Math.max(2, 9 - tier);
                float size = 0.040f - tier * 0.005f;
                float speed = 0.10f + tier * 0.015f;
                int life = Math.max(5, 16 - tier * 2);
                float gravity = gravityFor(shape);
                ENTRIES[i] = new Subtle(i, el, shape, tier, count, size, speed, life, gravity,
                        el.displayName + " " + nameFor(shape) + " " + roman(tier + 1));
                i++;
            }
        }
    }

    private WeaponVfxSubtle() {}

    public static Subtle get(int index) {
        return ENTRIES[Math.floorMod(index, COUNT)];
    }

    public static List<Subtle> all() {
        return List.of(ENTRIES);
    }

    /** Tous les accents d'une famille élémentaire. */
    public static List<Subtle> forElement(DeepElement el) {
        List<Subtle> out = new ArrayList<>(5);
        for (Subtle s : ENTRIES) {
            if (s.element() == el) out.add(s);
        }
        return out;
    }

    /** Choisit un accent subtil de la famille demandée, de façon déterministe mais variée. */
    public static Subtle pick(DeepElement el, long seed) {
        List<Subtle> pool = forElement(el);
        if (pool.isEmpty()) return ENTRIES[0];
        return pool.get((int) Math.floorMod(seed >>> 11, pool.size()));
    }

    // ── Associe une forme à chaque famille, avec deux variantes par famille selon le palier ──
    private static int shapeFor(DeepElement el, int tier) {
        return switch (el) {
            case FIRE -> (tier % 2 == 0) ? SHAPE_EMBER : (tier == 3 ? SHAPE_MIST : SHAPE_SPARK);
            case WATER -> switch (tier) {
                case 0 -> SHAPE_DROPLET;
                case 1 -> SHAPE_MIST;
                case 2 -> SHAPE_BUBBLE;
                case 3 -> SHAPE_SHARD;
                default -> SHAPE_DRIFT;
            };
            // Bois = éclats d'écorce / feuilles qui tournoient (brun).
            case WOOD -> (tier % 2 == 0) ? SHAPE_LEAF : SHAPE_CRUMBLE;
            // Poison = gouttes et brume âcre (vert foncé), jamais de grosses formes.
            case POISON -> switch (tier) {
                case 0 -> SHAPE_DROPLET;
                case 1 -> SHAPE_MIST;
                case 2 -> SHAPE_BUBBLE;
                default -> SHAPE_ASH;
            };
            case EARTH -> (tier % 2 == 0) ? SHAPE_DUST : SHAPE_CRUMBLE;
            case METAL -> (tier % 2 == 0) ? SHAPE_GLINT : SHAPE_SPARK;
            case THUNDER -> (tier % 2 == 0) ? SHAPE_BOLT : SHAPE_SPARK;
            case LIGHT -> (tier % 2 == 0) ? SHAPE_MOTES : SHAPE_GLINT;
            case SHADOW -> (tier % 2 == 0) ? SHAPE_WISP : SHAPE_ASH;
            case BLOOD -> (tier % 2 == 0) ? SHAPE_DROPLET : SHAPE_ASH;
            case VOID -> (tier % 2 == 0) ? SHAPE_MOTES : SHAPE_WISP;
            // T352-CHAOS : traînée bariolée + reflet instable (toujours 2..9 pixels).
            case CHAOS -> (tier % 2 == 0) ? SHAPE_DRIFT : SHAPE_GLINT;
        };
    }

    private static float gravityFor(int shape) {
        return switch (shape) {
            case SHAPE_EMBER, SHAPE_MIST, SHAPE_BUBBLE, SHAPE_MOTES -> -0.012f;
            case SHAPE_DUST, SHAPE_WISP -> 0.004f;
            case SHAPE_CRUMBLE, SHAPE_ASH, SHAPE_LEAF -> 0.020f;
            case SHAPE_DROPLET -> 0.032f;
            default -> 0.010f;
        };
    }

    private static String nameFor(int shape) {
        return switch (shape) {
            case SHAPE_SPARK -> "étincelle";
            case SHAPE_EMBER -> "braise";
            case SHAPE_DROPLET -> "goutte";
            case SHAPE_MIST -> "brume";
            case SHAPE_LEAF -> "fragment";
            case SHAPE_SHARD -> "éclat";
            case SHAPE_DUST -> "poussière";
            case SHAPE_WISP -> "volute";
            case SHAPE_GLINT -> "reflet";
            case SHAPE_BOLT -> "arc";
            case SHAPE_BUBBLE -> "bulle";
            case SHAPE_ASH -> "cendre";
            case SHAPE_MOTES -> "motes";
            case SHAPE_CRUMBLE -> "gravats";
            default -> "traînée";
        };
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }
}
