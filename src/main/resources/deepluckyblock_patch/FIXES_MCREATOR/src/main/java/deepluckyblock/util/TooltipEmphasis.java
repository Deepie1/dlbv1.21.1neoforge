package deepluckyblock.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🔆 TooltipEmphasis — mettre en gras ce qu'il ne faut pas rater
 * ═══════════════════════════════════════════════════════════════
 *
 * <p>Une description d'enchantement est une phrase. Deux choses dedans ne sont pas de la
 * phrase : <b>la touche sur laquelle appuyer</b> et <b>le temps de recharge</b>. Ce sont
 * elles qu'on cherche en survolant un objet, et elles se perdent au milieu du texte. Cette
 * classe les met en gras au moment de l'affichage.</p>
 *
 * <p><b>Au moment de l'affichage, et nulle part ailleurs.</b> Les fichiers de langue ne sont
 * pas touchés : les textes de l'auteur restent exactement ce qu'ils sont. Si cette classe
 * disparaissait, les descriptions se réafficheraient telles quelles.</p>
 *
 * <p><b>Pourquoi si peu de motifs.</b> Tout mettre en gras revient à ne rien mettre en gras.
 * Seuls trois types d'information sont marqués : la touche, la durée, et l'objet exigé sans
 * lequel la capacité ne part pas. Les pourcentages de dégâts, eux, sont volontairement
 * laissés en texte normal : il y en a dans presque chaque description, les marquer tous
 * noierait la touche et la recharge qu'on voulait justement faire ressortir.</p>
 *
 * <p><b>La couleur survit.</b> Le code {@code §r} qui ferme le gras remet aussi la couleur à
 * blanc. On réémet donc derrière lui la couleur qui était active avant le passage en gras,
 * sinon la fin de chaque phrase changerait de teinte.</p>
 */
public final class TooltipEmphasis {

    private TooltipEmphasis() {
    }

    /** Gras. */
    private static final String BOLD = "\u00a7l";
    /** Remise à zéro : ferme le gras, mais efface aussi la couleur. */
    private static final String RESET = "\u00a7r";

    /**
     * Ce qu'on met en gras, en français et en anglais :
     * <ol>
     *   <li>les touches : clic droit, clic gauche, main vide, accroupi ;</li>
     *   <li>les durées : « 15 s », « 18 secondes », « 3 minutes », « 40 ticks » ;</li>
     *   <li>l'Étoile du Nether, seul objet qu'une capacité exige pour partir.</li>
     * </ol>
     */
    private static final Pattern EMPHASIS = Pattern.compile(
            "(?iu)("
                    + "clics?[ \u00a0-]droits?(?:\\s+main\\s+vide)?"
                    + "|clics?[ \u00a0-]gauches?"
                    + "|main\\s+vide"
                    + "|accroupi\\w*"
                    + "|right[- ]click(?:ing|s)?"
                    + "|left[- ]click(?:ing|s)?"
                    + "|sneak(?:ing)?"
                    + "|empty\\s+hand"
                    + "|\u00c9toile\\s+du\\s+Nether"
                    + "|Nether\\s+Star"
                    + "|\\d+(?:[.,]\\d+)?\\s*(?:secondes?|seconds?|minutes?|ticks?|sec\\b|min\\b|s\\b)"
                    + ")");

    /**
     * Rend la chaîne avec les touches et les durées en gras.
     *
     * @param raw texte d'origine, codes {@code §} compris ; {@code null} est rendu tel quel
     * @return le même texte, enrichi — jamais tronqué ni réécrit
     */
    public static String emphasize(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        // Deja mis en gras a la main par l'auteur : on ne touche a rien. Son choix prime,
        // et empiler deux fois le gras produirait des §l§l qui mangent de la place.
        if (raw.indexOf(BOLD) >= 0) return raw;

        Matcher m = EMPHASIS.matcher(raw);
        StringBuilder out = new StringBuilder(raw.length() + 32);
        int last = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            out.append(raw, last, m.start());
            out.append(BOLD).append(m.group()).append(RESET).append(colorAt(raw, m.start()));
            last = m.end();
        }
        if (!any) return raw;
        out.append(raw, last, raw.length());
        return out.toString();
    }

    /**
     * La couleur active juste avant {@code pos} : on remonte le texte à la recherche du
     * dernier {@code §} suivi d'un chiffre ou d'une lettre de couleur. Les codes de style
     * (gras, italique…) sont ignorés, seul {@code §r} coupe la recherche puisqu'il remet
     * tout à zéro.
     *
     * @return le code couleur à réémettre, ou une chaîne vide s'il n'y en avait aucun
     */
    private static String colorAt(String raw, int pos) {
        for (int i = pos - 2; i >= 0; i--) {
            if (raw.charAt(i) != '\u00a7') continue;
            char c = Character.toLowerCase(raw.charAt(i + 1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) return "\u00a7" + c;
            if (c == 'r') return "";
        }
        return "";
    }
}
