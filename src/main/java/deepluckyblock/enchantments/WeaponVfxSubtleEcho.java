package deepluckyblock.enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * T351-SUBTLE-ECHO : <b>50 accents discrets supplémentaires</b>, cette fois <b>dérivés des grosses
 * animations</b> (les effets « forts » de {@link WeaponVisualEffectId}, 30 à l'origine, 37 depuis T352).
 *
 * <p><b>Idée :</b> une grosse animation ne tombe qu'une fois sur dix. Quand c'est le cas, on garde la
 * grande forme ; le reste du temps on en joue le <i>souvenir</i> : même géométrie, même élément, même
 * direction, mais réduite à 2 à 6 mini-pixels. Le joueur reconnaît donc la signature de son
 * enchantement (l'arc de l'Annihilating Arc, la colonne du Seraph, l'orbite de l'Abyssal Maelstrom...)
 * sans jamais se prendre une forme qui englobe la cible.</p>
 *
 * <p><b>Deux paliers :</b></p>
 * <ul>
 *   <li>{@code 0..29} : un écho par effet fort (30), le plus « lisible » des deux ;</li>
 *   <li>{@code 30..49} : 20 échos encore plus discrets, avec un mouvement <i>voisin</i> (l'arc devient
 *       une traînée, la spirale devient un anneau...), pour ne jamais voir deux fois la même chose.</li>
 * </ul>
 *
 * <p>Les couleurs ne sont pas stockées ici : elles viennent de la famille élémentaire de l'effet
 * d'origine, puis du mélange élémentaire de l'arme s'il y en a plusieurs (T351-ELEMENT-BLEND).</p>
 */
public final class WeaponVfxSubtleEcho {

    /** Nombre d'échos fournis par cette classe (50). */
    public static final int COUNT = 50;
    /** Décalage d'index dans l'espace global des accents discrets ({@code 0..49} = {@link WeaponVfxSubtle}). */
    public static final int INDEX_BASE = WeaponVfxSubtle.COUNT;

    // ── Mouvements d'écho (géométrie de la grosse animation, en minuscule) ──
    /** Traînée qui file le long de la frappe. */
    public static final int MOTION_TRAIL  = 0;
    /** Arc fin qui balaie perpendiculairement au coup. */
    public static final int MOTION_ARC    = 1;
    /** Colonne verticale étroite qui monte. */
    public static final int MOTION_SPIRE  = 2;
    /** Petite orbite autour du point d'impact. */
    public static final int MOTION_ORBIT  = 3;
    /** Anneau horizontal minuscule, très lent. */
    public static final int MOTION_RING   = 4;
    /** Chute depuis le haut (météore, gouttes). */
    public static final int MOTION_FALL   = 5;
    /** Mini-tourbillon qui aspire vers le point d'impact. */
    public static final int MOTION_SIPHON = 6;
    /** Éclair sec en zigzag. */
    public static final int MOTION_BOLT   = 7;
    /** Couronne au-dessus de l'impact (halo). */
    public static final int MOTION_HALO   = 8;
    /** Micro-prisme : quelques facettes qui s'écartent. */
    public static final int MOTION_PRISM  = 9;
    /** Vague qui déferle vers l'avant. */
    public static final int MOTION_WAVE   = 10;
    /** Croix pixel 3D qui s'ouvre. */
    public static final int MOTION_CROSS  = 11;
    /** Étoile rayonnante (critique). */
    public static final int MOTION_STAR   = 12;
    /** Éclosion radiale au sol / au point d'impact. */
    public static final int MOTION_BLOOM  = 13;

    /**
     * Un écho discret d'une grosse animation.
     *
     * @param index    index 0..49 dans cette classe
     * @param element  famille élémentaire de l'effet d'origine
     * @param effectId id de la grosse animation dont cet accent découle (1..30)
     * @param motion   mouvement parmi les {@code MOTION_*} ci-dessus
     * @param count    nombre de mini-pixels
     * @param size     demi-taille de base d'un pixel
     * @param speed    vitesse d'expansion
     * @param life     durée de vie en ticks
     * @param gravity  gravité appliquée (positif = tombe, négatif = monte)
     * @param spread   rayon d'apparition autour du point d'impact
     * @param tier     palier de discrétion (0 = lisible, 1 = presque invisible)
     * @param name     nom lisible pour les commandes / la documentation
     */
    public record Echo(int index, DeepElement element, int effectId, int motion,
                       int count, float size, float speed, int life, float gravity,
                       float spread, int tier, String name) {

        /** Nom lisible du mouvement (arc, colonne, orbite...). */
        public String motionName() {
            return WeaponVfxSubtleEcho.motionName(motion);
        }

        /** Grosse animation d'origine. */
        public WeaponVisualEffectId source() {
            return WeaponVisualEffectId.byId(effectId);
        }
    }

    private static final Echo[] ENTRIES = new Echo[COUNT];

    static {
        // ── Palier 0 : un écho par grosse animation (30) ──
        // Ordre = ids 1..30, avec pour chacun le mouvement qui « cite » la grande forme.
        final int[] motions = {
                MOTION_ARC,    // 01 RAZOR_GLEAM          : le fil qui chante
                MOTION_PRISM,  // 02 SUPREME_PRISM_EDGE   : facettes
                MOTION_TRAIL,  // 03 SPECTRAL_REACH       : allonge spectral
                MOTION_ARC,    // 04 TWIN_ECHO_SLASH      : double entaille
                MOTION_ARC,    // 05 GUILLOTINE_CLEAVE    : couperet
                MOTION_STAR,   // 06 GOLDEN_CRIT_STAR     : étoile critique
                MOTION_CROSS,  // 07 FOCUSED_BULLSEYE     : croix de visée
                MOTION_CROSS,  // 08 OVERCLOCK_CYBER_X    : croix cyber
                MOTION_FALL,   // 09 DRIPPING_BLOOD_EDGE  : gouttes
                MOTION_SIPHON, // 10 CRIMSON_SIPHON       : aspiration
                MOTION_SIPHON, // 11 VITAL_TRANSFUSION    : transfert
                MOTION_ARC,    // 12 CARNAGE_BERSERK      : tourbillon de lames
                MOTION_WAVE,   // 13 MOLTEN_FIRE_WAVE     : vague de feu
                MOTION_WAVE,   // 14 SOUL_FIRE_CRESCENT   : croissant
                MOTION_FALL,   // 15 BRIMSTONE_METEOR     : météore
                MOTION_BOLT,   // 16 CHAIN_VOLT_ARC       : éclair
                MOTION_WAVE,   // 17 HYDRO_TIDAL_SURGE    : déferlante
                MOTION_ORBIT,  // 18 ABYSSAL_MAELSTROM    : maelström
                MOTION_BLOOM,  // 19 PERMAFROST_RIME      : givre qui éclot
                MOTION_BLOOM,  // 20 VENOM_BRIAR_FANG     : ronces
                MOTION_BLOOM,  // 21 SEISMIC_TECTONIC     : fissure
                MOTION_TRAIL,  // 22 ZEPHYR_WIND_LANCE    : lance de vent
                MOTION_SPIRE,  // 23 SERAPH_SKY_PILLAR    : colonne céleste
                MOTION_ORBIT,  // 24 WITHER_SHADOW_RIFT   : rift tournant
                MOTION_TRAIL,  // 25 ASTRAL_STAR_LANCE    : rayon stellaire
                MOTION_ORBIT,  // 26 VOID_WARP_SPHERE     : sphère de vide
                MOTION_PRISM,  // 27 DRAGON_CHAOS_PRISM   : prisme du dragon
                MOTION_HALO,   // 28 CHRONO_ETERNAL_HALO  : couronne temporelle
                MOTION_RING,   // 29 WARLORD_RALLY_SIGIL  : sceau au sol
                MOTION_ARC     // 30 ANNIHILATING_ARC     : arc final
        };
        final int[] counts = {
                4, 3, 3, 4, 4, 5, 4, 4, 3, 4, 3, 5, 5, 4, 3,
                4, 5, 4, 4, 3, 4, 3, 3, 3, 3, 4, 3, 4, 4, 5
        };

        for (int i = 0; i < 30; i++) {
            int effectId = i + 1;
            WeaponVisualEffectId src = WeaponVisualEffectId.byId(effectId);
            if (src == null) continue;
            int motion = motions[i];
            // Plus l'effet d'origine est « gros » (priorité haute), plus l'écho reste petit :
            // on ne veut pas doubler visuellement une animation déjà spectaculaire.
            float size = 0.036f - (i % 4) * 0.002f;
            float speed = 0.085f + (i % 5) * 0.012f;
            int life = 9 + (i % 5);
            ENTRIES[i] = new Echo(i, src.element, effectId, motion,
                    Math.max(2, Math.min(6, counts[i])), size, speed, life,
                    gravityFor(motion), spreadFor(motion), 0, label(src) + " · écho");
        }

        // ── Palier 1 : 20 échos encore plus discrets, mouvement voisin ──
        final int[] ids = {
                1, 2, 4, 5, 6, 8, 9, 10, 13, 14,
                15, 16, 17, 18, 19, 20, 21, 24, 26, 27
        };
        for (int i = 0; i < ids.length; i++) {
            WeaponVisualEffectId src = WeaponVisualEffectId.byId(ids[i]);
            if (src == null) continue;
            int near = neighbourMotion(motions[ids[i] - 1]);
            float size = 0.030f - (i % 3) * 0.002f;
            float speed = 0.070f + (i % 4) * 0.010f;
            int life = 7 + (i % 4);
            ENTRIES[30 + i] = new Echo(30 + i, src.element, ids[i], near,
                    2 + (i % 2), size, speed, life,
                    gravityFor(near), spreadFor(near), 1, label(src) + " · écho II");
        }
    }

    private WeaponVfxSubtleEcho() {}

    public static Echo get(int index) {
        return ENTRIES[Math.floorMod(index, COUNT)];
    }

    public static List<Echo> all() {
        return List.of(ENTRIES);
    }

    /** Tous les échos d'une famille élémentaire. */
    public static List<Echo> forElement(DeepElement el) {
        List<Echo> out = new ArrayList<>(5);
        for (Echo e : ENTRIES) {
            if (e.element() == el) out.add(e);
        }
        return out;
    }

    /** Échos qui dérivent d'une grosse animation donnée (1 ou 2 selon l'effet). */
    public static List<Echo> forEffect(int effectId) {
        List<Echo> out = new ArrayList<>(2);
        for (Echo e : ENTRIES) {
            if (e.effectId() == effectId) out.add(e);
        }
        return out;
    }

    /** Choisit un écho déterministe pour une famille élémentaire. */
    public static Echo pick(DeepElement el, long seed) {
        List<Echo> pool = forElement(el);
        if (pool.isEmpty()) return ENTRIES[0];
        return pool.get((int) Math.floorMod(seed >>> 13, pool.size()));
    }

    /** Mouvement « voisin » : même intention, geste plus ténu (palier 1). */
    private static int neighbourMotion(int motion) {
        return switch (motion) {
            case MOTION_ARC -> MOTION_TRAIL;
            case MOTION_PRISM -> MOTION_CROSS;
            case MOTION_TRAIL -> MOTION_ARC;
            case MOTION_STAR -> MOTION_PRISM;
            case MOTION_CROSS -> MOTION_STAR;
            case MOTION_WAVE -> MOTION_TRAIL;
            case MOTION_FALL -> MOTION_BLOOM;
            case MOTION_SIPHON -> MOTION_ORBIT;
            case MOTION_ORBIT -> MOTION_RING;
            case MOTION_RING -> MOTION_HALO;
            case MOTION_HALO -> MOTION_RING;
            case MOTION_BOLT -> MOTION_TRAIL;
            case MOTION_SPIRE -> MOTION_HALO;
            default -> MOTION_BLOOM;
        };
    }

    private static float gravityFor(int motion) {
        return switch (motion) {
            case MOTION_SPIRE, MOTION_HALO, MOTION_ORBIT -> -0.008f;
            case MOTION_FALL -> 0.030f;
            case MOTION_SIPHON -> -0.004f;
            case MOTION_BLOOM -> 0.006f;
            case MOTION_RING -> 0.002f;
            case MOTION_WAVE, MOTION_TRAIL, MOTION_BOLT -> 0.010f;
            default -> 0.004f;
        };
    }

    private static float spreadFor(int motion) {
        return switch (motion) {
            case MOTION_SPIRE -> 0.05f;
            case MOTION_HALO, MOTION_RING -> 0.16f;
            case MOTION_BLOOM -> 0.12f;
            case MOTION_ORBIT, MOTION_SIPHON -> 0.11f;
            case MOTION_BOLT -> 0.04f;
            default -> 0.09f;
        };
    }

    static String motionName(int motion) {
        return switch (motion) {
            case MOTION_TRAIL -> "traînée";
            case MOTION_ARC -> "arc";
            case MOTION_SPIRE -> "colonne";
            case MOTION_ORBIT -> "orbite";
            case MOTION_RING -> "anneau";
            case MOTION_FALL -> "chute";
            case MOTION_SIPHON -> "tourbillon";
            case MOTION_BOLT -> "éclair";
            case MOTION_HALO -> "couronne";
            case MOTION_PRISM -> "prisme";
            case MOTION_WAVE -> "vague";
            case MOTION_CROSS -> "croix";
            case MOTION_STAR -> "étoile";
            default -> "éclosion";
        };
    }

    /** "RAZOR_GLEAM" -> "Razor Gleam" (nom lisible sans table de traduction supplémentaire). */
    static String label(WeaponVisualEffectId fx) {
        String raw = fx.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (upper && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
            if (c == ' ') upper = true;
        }
        return sb.toString();
    }
}
