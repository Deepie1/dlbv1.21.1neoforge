package deepluckyblock.mixin;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/**
 * "Le Java qui s'occupe direct de l'injection."
 *
 * Mixin détecte automatiquement cette classe via ServiceLoader
 * (META-INF/services/org.spongepowered.asm.mixin.connect.IMixinConnector)
 * et l'appelle très tôt au démarrage. Elle enregistre la config des mixins du
 * glint par rang, SANS avoir à ajouter "MixinConfigs" au manifeste du jar.
 *
 * -> Aucune édition manuelle de build.gradle n'est nécessaire pour l'injection.
 *
 * Le système de glint est celui porté de Deep's Enchantments+ (EnchantAura +
 * EnchantGlintTyped + accessors RenderStateShard) : il remplace l'ancien
 * GlintColors basé sur la réflexion, dont la construction échouait en jeu
 * obfusqué ("build null"). Les anciens fichiers sont archivés dans
 * verification/retired/.
 */
public class GlintMixinConnector implements IMixinConnector {

    /** Nom EXACT de la config chargée par le launcher (--mixin.config). */
    public static final String CONFIG = "deep_lucky_block.mixins.json";

    /** Les 8 classes client que la config doit lister (glint + accessors + X-Ray local miners_eye T341). */
    public static final String[] EXPECTED = {
            "ItemRendererEnchantMixin",
            "RenderTypeEnchantMixin",
            "HumanoidArmorLayerEnchantMixin",
            "RenderStateShardConstantAccessor",
            "RenderStateShardShaderAccessor",
            "BlockXrayMixin",
            "LiquidBlockRendererXrayMixin",
            "SectionCompilerXrayMixin"
    };

    @Override
    public void connect() {
        Mixins.addConfiguration(CONFIG);
        if (Boolean.getBoolean("dlb.debug")) {
            System.out.println("[DeepLuckyBlock] Config mixin enregistrée : " + CONFIG
                    + " — mixins attendus : " + String.join(", ", EXPECTED));
        }
    }
}
