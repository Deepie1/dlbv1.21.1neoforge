package deepluckyblock.enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * T350-VFX-VARIATIONS : bibliothèque d'animations dérivées de TOUS les effets d'arme existants.
 *
 * <p><b>Problème.</b> Chaque enchantement ne disposait que d'une seule animation « pleine » : dès qu'on
 * enchaînait les coups, le même effet revenait à l'identique, et les effets des différents enchantements
 * semblaient surgir sans logique.</p>
 *
 * <p><b>Solution.</b> 50 déclinaisons construites sur les 30 effets :</p>
 * <ul>
 *   <li>une variante <b>pleine</b> par effet (densité de pixels 100 %),</li>
 *   <li>20 variantes <b>allégées</b> des effets les plus courants, avec de moins en moins de particules
 *       (densité 70 % → 30 %), une taille légèrement réduite et un léger décalage de teinte.</li>
 * </ul>
 *
 * <p>La densité est appliquée au niveau du moteur de pixels : les pixels de silhouette (alpha fort)
 * restent toujours dessinés, seuls les pixels secondaires s'espacent. Les formes restent donc lisibles
 * tout en devenant nettement moins « agressives », exactement comme demandé.</p>
 */
public final class WeaponVfxVariant {

    /** Nombre de déclinaisons allégées (déclaré AVANT COUNT : ordre d'initialisation des champs). */
    public static final int LITE_COUNT = 20;
    /** Nombre total de déclinaisons (une pleine par effet + 20 allégées). */
    public static final int COUNT = WeaponVisualEffectId.values().length + LITE_COUNT;

    /**
     * Une déclinaison concrète : effet de base + réglages de rendu.
     *
     * @param index        index global 0..49
     * @param effect       effet d'origine
     * @param density      part des pixels secondaires conservés (1.0 = tout)
     * @param scale        facteur de taille global
     * @param hueStep      décalage de teinte en pas de 0.02 (petites variations de couleur)
     * @param lite         vrai pour les déclinaisons allégées
     */
    public record Entry(int index, WeaponVisualEffectId effect, float density, float scale,
                        int hueStep, boolean lite) {
    }

    private static final Entry[] ENTRIES = new Entry[COUNT];

    static {
        WeaponVisualEffectId[] all = WeaponVisualEffectId.values();
        int i = 0;
        for (WeaponVisualEffectId fx : all) {
            if (i >= COUNT) break;
            ENTRIES[i] = new Entry(i, fx, 1.0f, 1.0f, 0, false);
            i++;
        }
        // Les 20 déclinaisons allégées : densité qui décroît régulièrement (0.70 -> 0.30),
        // taille qui se resserre (0.92 -> 0.80) et teinte qui glisse très légèrement.
        for (int k = 0; k < LITE_COUNT && i < COUNT; k++, i++) {
            WeaponVisualEffectId fx = all[k % all.length];
            float f = k / (float) (LITE_COUNT - 1);       // 0..1
            float density = 0.70f - 0.40f * f;            // 0.70 .. 0.30
            float scale = 0.92f - 0.12f * f;              // 0.92 .. 0.80
            int hueStep = (k % 5) - 2;                    // -2..+2
            ENTRIES[i] = new Entry(i, fx, density, scale, hueStep, true);
        }
        // Sécurité : si le tableau n'est pas totalement rempli (ajout/retrait d'effets), on complète.
        for (int k = 0; k < COUNT; k++) {
            if (ENTRIES[k] == null) {
                ENTRIES[k] = new Entry(k, all[k % all.length], 1.0f, 1.0f, 0, false);
            }
        }
    }

    private WeaponVfxVariant() {}

    /** Les 50 déclinaisons, dans l'ordre (30 pleines puis 20 allégées). */
    public static java.util.List<Entry> all() {
        return java.util.List.of(ENTRIES);
    }

    public static Entry get(int index) {
        return ENTRIES[Math.floorMod(index, COUNT)];
    }

    /** Toutes les déclinaisons d'un effet donné (pleine puis allégées). */
    public static List<Entry> forEffect(WeaponVisualEffectId fx) {
        List<Entry> out = new ArrayList<>(3);
        for (Entry e : ENTRIES) {
            if (e.effect() == fx) out.add(e);
        }
        return out;
    }

    /**
     * Choisit une déclinaison pour un déclenchement. Déterministe à partir de la graine de l'instance :
     * l'effet garde son identité (même famille, même forme de base) mais alterne entre version pleine
     * et versions allégées, ce qui évite la répétition tout en restant cohérent.
     *
     * @param fx   effet résolu pour le coup
     * @param seed graine tirée au déclenchement
     */
    public static Entry pick(WeaponVisualEffectId fx, long seed) {
        List<Entry> pool = forEffect(fx);
        if (pool.isEmpty()) return ENTRIES[0];
        long r = (seed >>> 13) & 0x7FFFFFFFL;
        // 55 % version pleine, 45 % réparti sur les déclinaisons allégées
        if (pool.size() == 1 || (r % 100) < 55) return pool.get(0);
        int liteIndex = 1 + (int) ((r / 100) % (pool.size() - 1));
        return pool.get(liteIndex);
    }

    /** Index 0..49 à utiliser pour la commande de test. */
    public static Entry byEffectAndLite(WeaponVisualEffectId fx, boolean lite) {
        Entry fallback = ENTRIES[0];
        for (Entry e : ENTRIES) {
            if (e.effect() == fx) {
                if (e.lite() == lite) return e;
                if (!lite) fallback = e;
            }
        }
        return fallback;
    }
}
