package deepluckyblock.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * PLUGIN MIXIN UNIQUE — deep_lucky_block.
 *
 * ⚙️ Rôle : gérer les INTÉGRATIONS OPTIONNELLES de la compatibilité avec le
 *    modpack "Lucky World Invasion" (de Laink) via les mods :
 *      - com.lwi.luckystats (LuckyStats)
 *      - com.lwi.luckyxp   (LuckyXP : pool, XP, roulette, shower...)
 *
 * Ces intégrations sont RÉELLES : elles injectent le bloc deep_lucky_block dans
 * le pool / le système d'XP de Laink. On ne les désactive donc PAS.
 *
 * Mécanique : les 4 mixins com.lwi.* sont DÉCLARÉS dans deep_lucky_block.mixins.json
 * (ils existent dans ton workspace). Le plugin, via shouldApplyMixin(), ne les
 * APPLIQUE que si la classe cible du modpack est présente. Sinon il les saute :
 * Mixin n'atteint jamais la résolution de la cible -> aucun
 *    "Error loading class: com.lwi... (ClassNotFoundException)" ni
 *    "Skipping virtual target ... for deep_lucky_block.mixins.json:..."
 *
 * ✅ Modpack présent  -> mixins appliqués  -> intégration active.
 * ✅ Modpack absent   -> mixins sautés      -> AUCUN warn, AUCUN crash.
 * ✅ Modpack renommé  -> détection par classe (pas par nom) -> toujours OK.
 *
 * NOTE : getMixins() renvoie null volontairement : les mixins sont déjà listés
 *    dans le JSON, on ne les ré-ajoute pas (sinon double enregistrement).
 */
public final class DlbMixinPlugin implements IMixinConfigPlugin {

    /** [Nom du mixin d'intégration, classe cible du mod optionnel]. */
    private static final String[][] OPTIONAL_MIXINS = {
        { "LuckyStatsSyncMixin",         "com.lwi.luckystats.util.PlayerStatsAccess" },
        { "LuckyXpEventRollsMixin",      "com.lwi.luckyxp.event.EventRolls" },
        { "LuckyXpShowerMixin",          "com.lwi.luckyxp.event.LuckyBlockShower" },
        { "LuckyXpRouletteOverlayMixin", "com.lwi.luckyxp.client.RouletteOverlay" },
    };

    @Override
    public void onLoad(String mixinPackage) {
        // Rien à initialiser.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Aucune cible supplémentaire à fusionner.
    }

    @Override
    public List<String> getMixins() {
        // Les 4 mixins com.lwi.* sont déjà déclarés dans le JSON.
        return null;
    }

    /**
     * Décide si un mixin doit être appliqué.
     * Pour les intégrations optionnelles : on n'applique QUE si le mod est là.
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName == null) return true;
        String simple = mixinClassName.contains(".")
                ? mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1)
                : mixinClassName;

        for (String[] pair : OPTIONAL_MIXINS) {
            if (pair[0].equals(simple)) {
                return isClassPresent(pair[1]);
            }
        }
        return true;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        // Aucun pré-traitement.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        // Aucun post-traitement.
    }

    /** Teste la présence d'une classe sur le classpath (sans l'initialiser). */
    private static boolean isClassPresent(String fqcn) {
        try {
            Class.forName(fqcn, false, DlbMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
