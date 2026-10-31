package deepluckyblock.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ==================== CONFIG : PRIX/GAINS DE L'INFUSION DE CHANCE ====================
 * FIX (demande en jeu -- "je veux que tu m'ajoute une option dans un fichier
 * [...] qui me permette de recalculer le prix des infusions luck de tous mes
 * items utilises par mon bloc [...] je peux individuellement dans ce fichier
 * modifier le taux de luck ajoute, je peux aussi sinon de maniere generale
 * reduire ou augmenter les gains de luck [...] genre actuellement sur 1, si
 * je le baisse a 0.5, alors si admettons le lingot de netherite donnait 10,
 * la il donnera plus que 5 (la moitie), etc. Si la valeur est 2, le lingot
 * de netherite qui de base est a 10 alors maintenant elle devient 20") :
 *
 * Ce fichier de config (deep_lucky_block-luck_infusion.toml, genere au
 * premier lancement) expose :
 *
 *  - globalLuckMultiplier : un multiplicateur UNIQUE applique a TOUS les
 *    items d'infusion en meme temps (positifs ET negatifs, valeur par
 *    defaut 1.0 = comportement inchange). Exemple demande : 0.5 -> tous les
 *    gains sont divises par 2 (un lingot de Netherite qui donnait +20 en
 *    donne desormais +10) ; 2.0 -> tous les gains sont doubles (+20 -> +40).
 *
 *  - perItemLuckMultiplier : une liste de paires "id_item=multiplicateur"
 *    (ex. "minecraft:netherite_ingot=2.0") pour ajuster le taux de luck
 *    ajoute INDIVIDUELLEMENT, item par item, en plus du multiplicateur
 *    global (les deux se cumulent par multiplication). Un item absent de
 *    cette liste garde un multiplicateur individuel de 1.0 (aucun
 *    changement autre que le multiplicateur global).
 *
 * Valeur finale d'un item = valeur de base (codee dans
 * DeepLuckyBlockCraftingRecipe#getLuckValue) x globalLuckMultiplier x
 * multiplicateur individuel de cet item (si present dans la liste),
 * arrondie et jamais ramenee a 0 si la valeur de base etait non nulle
 * (voir DeepLuckyBlockCraftingRecipe#applyMultiplier).
 */
public class LuckInfusionConfig {

    public static ModConfigSpec.DoubleValue globalLuckMultiplier;
    public static ModConfigSpec.ConfigValue<List<? extends String>> perItemLuckMultiplier;
    public static ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        init(builder);
        SPEC = builder.build();
    }

    public static void init(ModConfigSpec.Builder builder) {
        builder.comment(
                "Reglages de l'infusion de chance (recette : Deep Lucky Block + objets de chance).",
                "Permet de recalculer/ajuster le taux de luck ajoute par chaque item utilise dans la recette.")
                .push("luck_infusion");

        globalLuckMultiplier = builder
                .comment(
                        "Multiplicateur GLOBAL applique a TOUS les items d'infusion (positifs et negatifs).",
                        "1.0 = comportement d'origine (aucun changement).",
                        "0.5 = tous les gains sont divises par 2 (ex. Lingot de Netherite : +20 -> +10).",
                        "2.0 = tous les gains sont doubles (ex. Lingot de Netherite : +20 -> +40).")
                .defineInRange("globalLuckMultiplier", 1.0, 0.0, 100.0);

        perItemLuckMultiplier = builder
                .comment(
                        "Multiplicateurs INDIVIDUELS par item, format \"id_item=multiplicateur\" (un par ligne).",
                        "Se cumule (par multiplication) avec globalLuckMultiplier ci-dessus.",
                        "Exemple : \"minecraft:netherite_ingot=2.0\" double le gain de CET item uniquement.",
                        "Un item absent de cette liste garde un multiplicateur individuel de 1.0.")
                .defineList("perItemLuckMultiplier", getDefaultPerItemEntries(), obj -> obj instanceof String);

        builder.pop();
    }

    private static List<String> getDefaultPerItemEntries() {
        // Vide par defaut : le joueur ajoute ses propres lignes s'il veut
        // surcharger un item precis (le multiplicateur global suffit pour
        // un ajustement uniforme).
        return new ArrayList<>();
    }

    /**
     * Parse "perItemLuckMultiplier" en Map&lt;id item en minuscules, multiplicateur&gt;.
     * Toute ligne malformee (pas de '=', nombre invalide) est silencieusement ignoree.
     */
    public static Map<String, Double> parsePerItemMultipliers() {
        Map<String, Double> result = new HashMap<>();
        try {
            for (String entry : perItemLuckMultiplier.get()) {
                if (entry == null) continue;
                int eq = entry.indexOf('=');
                if (eq <= 0 || eq == entry.length() - 1) continue;
                String id = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String valueStr = entry.substring(eq + 1).trim();
                try {
                    double value = Double.parseDouble(valueStr);
                    if (!id.isEmpty() && value >= 0) result.put(id, value);
                } catch (NumberFormatException ignored) {
                    // ligne malformee, ignoree
                }
            }
        } catch (Throwable ignored) {
            // config pas encore chargee / erreur de lecture -> aucun override individuel
        }
        return result;
    }

    /** Multiplicateur global courant, 1.0 en cas d'erreur/config non chargee. */
    public static double getGlobalMultiplier() {
        try {
            return globalLuckMultiplier.get();
        } catch (Throwable ignored) {
            return 1.0;
        }
    }
}
