package deepluckyblock.enchantments;

/**
 * T361-MOTIONS-UNIQUES : <b>signature de mouvement</b> propre à chacune des 40 grosses animations.
 *
 * <p><b>Problème résolu.</b> Toutes les attaques (balayages, impacts, rayons) partageaient un très
 * petit nombre de trajectoires : un arc de pixels identique pour 35 enchantements, puis un
 * « anneau + croix » commun à tous ceux qui n'avaient pas de cas dédié, et enfin un trio
 * hélice + cercle runique réutilisé par l'eau, le poison, la transfusion et la flamme d'âme.
 * Résultat : les grosses animations semblaient être la même animation recolorée.</p>
 *
 * <p><b>Solution.</b> Chaque effet reçoit ici un <b>profil de mouvement</b> : une des 22 familles
 * géométriques ({@link #SHAPE_CRESCENT}, {@link #SHAPE_VORTEX}, {@link #SHAPE_MAW}, {@link #SHAPE_DUNE},
 * {@link #SHAPE_CROSS_STAR}…) plus des paramètres chiffrés (nombre de bras, sens et vitesse de
 * rotation, montée/descente, échelle, oscillation). Le moteur client dessine alors une trajectoire
 * réellement différente : ce qui tourne, ce qui monte, ce qui se referme, ce qui tombe, ce qui
 * aspire ou ce qui éclate change d'un enchantement à l'autre.</p>
 *
 * <p>Les 40 combinaisons sont uniques (voir {@link #signatureOf(WeaponVisualEffectId)}) : deux
 * enchantements ne peuvent pas produire la même animation, même à couleur égale.</p>
 */
public final class WeaponVfxMotion {

    private WeaponVfxMotion() {}

    // =====================================================================
    // 22 familles de mouvement (la FORME du trajet, pas la couleur)
    // =====================================================================
    /** Arc de lame classique, pixels jointifs le long du tranchant. */
    public static final int SHAPE_CRESCENT = 0;
    /** Deux arcs croisés, l'un plus petit et décalé (rémanence). */
    public static final int SHAPE_DOUBLE_ARC = 1;
    /** Trois arcs concentriques aux teintes décalées (prisme). */
    public static final int SHAPE_PRISM = 2;
    /** Estoc rectiligne, crénelé, vers la cible. */
    public static final int SHAPE_LANCE = 3;
    /** Colonne verticale qui balaie l'arc (chute/hauteur). */
    public static final int SHAPE_COLUMN = 4;
    /** Anneaux en orbite autour de l'impact. */
    public static final int SHAPE_ORBIT = 5;
    /** Éclats projetés vers l'extérieur (glace, aiguillons). */
    public static final int SHAPE_SHARDS = 6;
    /** Nappe ondulante qui suit l'arc (eau, fluide). */
    public static final int SHAPE_WAVE = 7;
    /** Spirale qui ASPIRE vers le centre (vide, siphon, maelström). */
    public static final int SHAPE_VORTEX = 8;
    /** Gouttes qui tombent le long de l'arc (sang, venin). */
    public static final int SHAPE_DRIP = 9;
    /** Hélice enroulée autour de la trajectoire (vent, transfusion). */
    public static final int SHAPE_SPIRAL = 10;
    /** Éclair crénelé à angles francs (foudre, surcharge). */
    public static final int SHAPE_BOLT = 11;
    /** Dune : blocs soulevés + grains qui retombent (terre). */
    public static final int SHAPE_DUNE = 12;
    /** Mâchoire : dents qui se referment vers le centre (ronces, crocs). */
    public static final int SHAPE_MAW = 13;
    /** Faille verticale qui s'ouvre puis se referme (vide, ombre). */
    public static final int SHAPE_RIFT = 14;
    /** Triple griffure diagonale qui raye l'espace (ombre). */
    public static final int SHAPE_CLAW = 15;
    /** Disque sombre + liseré lumineux (éclipse). */
    public static final int SHAPE_ECLIPSE = 16;
    /** Pilier de lumière qui monte du sol (séraphin). */
    public static final int SHAPE_PILLAR = 17;
    /** Grand cercle runique au sol qui tourne (sceau, commandement). */
    public static final int SHAPE_RUNE_CIRCLE = 18;
    /** Étoile rayonnante : rayons partant d'un cœur blanc (big bang). */
    public static final int SHAPE_CROSS_STAR = 19;
    /** Pétales qui s'ouvrent vers le haut (sève, nature). */
    public static final int SHAPE_BLOOM = 20;
    /** Braises/cendres qui montent en dérivant (feu, miasme). */
    public static final int SHAPE_ASH = 21;

    /** Nombre de familles (sert au compteur de /dlbvfx). */
    public static final int SHAPE_COUNT = 22;

    /**
     * Profil de mouvement.
     *
     * @param shape  une des constantes {@code SHAPE_*}
     * @param arms   nombre de bras / branches / couches (1..6)
     * @param spin   rotation en tours par animation (négatif = sens inverse)
     * @param rise   déplacement vertical en blocs sur toute l'animation (négatif = chute)
     * @param scale  multiplicateur d'amplitude (0.7 = compact, 1.6 = très ample)
     * @param wobble oscillation latérale en blocs (0 = trajectoire nette)
     */
    public record Profile(int shape, int arms, float spin, float rise, float scale, float wobble) {}

    private static Profile p(int shape, int arms, float spin, float rise, float scale, float wobble) {
        return new Profile(shape, arms, spin, rise, scale, wobble);
    }

    /** Profil d'arc simple (utilisé en interne pour les arcs secondaires). */
    public static Profile crescent(int arms, float wobble) {
        return p(SHAPE_CRESCENT, arms, 0.0f, 0.0f, 1.0f, wobble);
    }

    /**
     * Assigne à chaque enchantement une trajectoire <b>unique</b> et cohérente avec son élément :
     * le feu monte, l'eau ondule, la glace éclate, la foudre zigzague, la terre soulève des blocs,
     * le vent s'enroule, le poison goutte et se referme, le vide aspire, la lumière rayonne.
     */
    public static Profile of(WeaponVisualEffectId fx) {
        if (fx == null) return p(SHAPE_CRESCENT, 1, 0.0f, 0.0f, 1.0f, 0.04f);
        return switch (fx) {
            // --- I. Tranchant / allonge / échos / exécution -------------------------------
            case RAZOR_GLEAM -> p(SHAPE_CRESCENT, 1, 0.00f, 0.00f, 0.94f, 0.010f);
            case SUPREME_PRISM_EDGE -> p(SHAPE_PRISM, 3, 0.35f, 0.00f, 1.08f, 0.020f);
            case SPECTRAL_REACH -> p(SHAPE_LANCE, 1, 0.00f, 0.00f, 1.25f, 0.000f);
            case TWIN_ECHO_SLASH -> p(SHAPE_DOUBLE_ARC, 2, -0.45f, 0.00f, 1.02f, 0.030f);

            // --- II. Critiques -------------------------------------------------------------
            case GUILLOTINE_CLEAVE -> p(SHAPE_COLUMN, 1, 0.00f, 0.00f, 1.20f, 0.015f);
            case GOLDEN_CRIT_STAR -> p(SHAPE_ORBIT, 2, 1.10f, 0.10f, 1.00f, 0.010f);
            case FOCUSED_BULLSEYE -> p(SHAPE_CRESCENT, 1, 0.00f, 0.00f, 0.72f, 0.000f);
            case OVERCLOCK_CYBER_X -> p(SHAPE_BOLT, 1, 0.00f, 0.00f, 1.06f, 0.120f);

            // --- III. Sang ----------------------------------------------------------------
            case DRIPPING_BLOOD_EDGE -> p(SHAPE_DRIP, 1, 0.00f, -0.85f, 0.96f, 0.020f);
            case CRIMSON_SIPHON -> p(SHAPE_VORTEX, 3, -1.30f, -0.25f, 1.05f, 0.040f);
            case VITAL_TRANSFUSION -> p(SHAPE_SPIRAL, 2, 1.60f, 0.35f, 1.00f, 0.030f);
            case CARNAGE_BERSERK -> p(SHAPE_CRESCENT, 2, 0.00f, 0.00f, 1.45f, 0.130f);

            // --- IV. Éléments -------------------------------------------------------------
            case MOLTEN_FIRE_WAVE -> p(SHAPE_ASH, 5, 0.40f, 1.60f, 1.25f, 0.090f);
            case SOUL_FIRE_CRESCENT -> p(SHAPE_CRESCENT, 2, 0.00f, 0.75f, 1.15f, 0.070f);
            case BRIMSTONE_METEOR -> p(SHAPE_COLUMN, 2, 0.55f, -1.30f, 1.45f, 0.060f);
            case CHAIN_VOLT_ARC -> p(SHAPE_BOLT, 3, 0.00f, 0.15f, 1.35f, 0.200f);
            case HYDRO_TIDAL_SURGE -> p(SHAPE_WAVE, 2, 0.25f, 0.10f, 1.15f, 0.110f);
            case ABYSSAL_MAELSTROM -> p(SHAPE_VORTEX, 4, 1.75f, -0.45f, 1.35f, 0.060f);
            case PERMAFROST_RIME -> p(SHAPE_SHARDS, 6, 0.00f, 0.30f, 1.05f, 0.030f);
            case VENOM_BRIAR_FANG -> p(SHAPE_MAW, 3, 0.00f, -0.20f, 0.88f, 0.040f);
            case SEISMIC_TECTONIC -> p(SHAPE_DUNE, 3, 0.15f, 0.55f, 1.30f, 0.080f);
            case ZEPHYR_WIND_LANCE -> p(SHAPE_SPIRAL, 1, 2.10f, 0.45f, 1.40f, 0.050f);
            case SERAPH_SKY_PILLAR -> p(SHAPE_PILLAR, 1, 0.00f, 0.00f, 1.30f, 0.015f);

            // --- V. Ombre / vide / astral / dragon ----------------------------------------
            case WITHER_SHADOW_RIFT -> p(SHAPE_CLAW, 3, 0.00f, -0.15f, 1.10f, 0.025f);
            case ASTRAL_STAR_LANCE -> p(SHAPE_LANCE, 2, 0.00f, 0.00f, 0.90f, 0.000f);
            case VOID_WARP_SPHERE -> p(SHAPE_VORTEX, 6, -2.20f, 0.00f, 0.95f, 0.030f);
            case DRAGON_CHAOS_PRISM -> p(SHAPE_PRISM, 4, 1.20f, 0.25f, 1.35f, 0.070f);

            // --- VI. Spéciaux -------------------------------------------------------------
            case CHRONO_ETERNAL_HALO -> p(SHAPE_ORBIT, 1, 0.60f, 0.00f, 1.05f, 0.010f);
            case WARLORD_RALLY_SIGIL -> p(SHAPE_RUNE_CIRCLE, 4, 0.40f, 0.00f, 1.45f, 0.020f);
            case ANNIHILATING_ARC -> p(SHAPE_DOUBLE_ARC, 3, 0.30f, 0.10f, 1.60f, 0.050f);

            // --- VII. T352 : 3es animations ------------------------------------------------
            case THORN_MAW_ROOT -> p(SHAPE_MAW, 5, 0.00f, 0.55f, 1.25f, 0.060f);
            case TITAN_GRASP -> p(SHAPE_MAW, 5, 0.00f, 0.20f, 1.35f, 0.075f);
            case SANDSTORM_DUNE -> p(SHAPE_DUNE, 5, 1.90f, 0.85f, 1.40f, 0.130f);
            case ECLIPSE_VEIL -> p(SHAPE_ECLIPSE, 1, -0.35f, 0.30f, 1.20f, 0.020f);
            case NULL_RIFT_FISSURE -> p(SHAPE_RIFT, 1, 0.00f, 0.00f, 1.15f, 0.045f);
            case CHAOS_KALEIDO_LOOP -> p(SHAPE_PRISM, 2, 2.60f, 0.00f, 1.25f, 0.100f);
            case ENTROPY_SPHERE -> p(SHAPE_ORBIT, 3, -1.50f, 0.00f, 1.15f, 0.040f);
            case BIG_BANG_SCAR -> p(SHAPE_CROSS_STAR, 5, 1.40f, 0.20f, 1.50f, 0.060f);

            // --- VIII. T352b : Bois (brun) & Poison (vert foncé) --------------------------
            case VERDANT_SAP_BLOOM -> p(SHAPE_BLOOM, 3, 0.30f, 0.65f, 1.05f, 0.045f);
            case MIASMA_SHROUD -> p(SHAPE_ASH, 3, -0.30f, 0.95f, 1.15f, 0.070f);
            case PLAGUE_NOVA -> p(SHAPE_SHARDS, 10, 0.00f, 0.45f, 1.10f, 0.030f);
        };
    }

    /** Nom lisible d'une famille de mouvement (affiché par {@code /dlbvfx}). */
    public static String labelOf(int shape) {
        return switch (shape) {
            case SHAPE_CRESCENT -> "arc de lame";
            case SHAPE_DOUBLE_ARC -> "double arc croisé";
            case SHAPE_PRISM -> "arcs prismatiques";
            case SHAPE_LANCE -> "estoc rectiligne";
            case SHAPE_COLUMN -> "colonne balayante";
            case SHAPE_ORBIT -> "anneaux en orbite";
            case SHAPE_SHARDS -> "éclats projetés";
            case SHAPE_WAVE -> "nappe ondulante";
            case SHAPE_VORTEX -> "spirale aspirante";
            case SHAPE_DRIP -> "gouttes qui tombent";
            case SHAPE_SPIRAL -> "hélice enroulée";
            case SHAPE_BOLT -> "éclair crénelé";
            case SHAPE_DUNE -> "dune soulevée";
            case SHAPE_MAW -> "mâchoire qui se referme";
            case SHAPE_RIFT -> "faille verticale";
            case SHAPE_CLAW -> "triple griffure";
            case SHAPE_ECLIPSE -> "éclipse";
            case SHAPE_PILLAR -> "pilier de lumière";
            case SHAPE_RUNE_CIRCLE -> "cercle runique";
            case SHAPE_CROSS_STAR -> "étoile rayonnante";
            case SHAPE_BLOOM -> "pétales qui s'ouvrent";
            case SHAPE_ASH -> "braises montantes";
            default -> "arc";
        };
    }

    /**
     * Signature numérique unique : sert de garde-fou (commande {@code /dlbvfx}) pour vérifier
     * qu'aucun enchantement ne réutilise le mouvement d'un autre.
     */
    public static String signatureOf(WeaponVisualEffectId fx) {
        Profile pr = of(fx);
        return pr.shape() + "/" + pr.arms() + "/" + Math.round(pr.spin() * 100)
                + "/" + Math.round(pr.rise() * 100) + "/" + Math.round(pr.scale() * 100)
                + "/" + Math.round(pr.wobble() * 1000);
    }
}
