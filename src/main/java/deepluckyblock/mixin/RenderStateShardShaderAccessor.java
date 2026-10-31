package deepluckyblock.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessors des ShaderStateShard du glint (ils vivent dans RenderStateShard,
 * PAS dans RenderType). Type de retour EXACT (requis par l'AP mixin).
 *
 * 1.21.1 : les champs RENDERTYPE_GLINT_DIRECT_SHADER et RENDERTYPE_ARMOR_GLINT_SHADER
 * n'existent PLUS dans RenderStateShard (glintDirect/armorGlint supprimes de RenderType).
 * Ne pas re-ajouter ces 2 accessors = crash InvalidAccessorException au lancement.
 */
@Mixin(RenderStateShard.class)
public interface RenderStateShardShaderAccessor {
    @Accessor("RENDERTYPE_GLINT_SHADER")
    static RenderStateShard.ShaderStateShard deepen_glintShader() { throw new AssertionError(); }

    @Accessor("RENDERTYPE_GLINT_TRANSLUCENT_SHADER")
    static RenderStateShard.ShaderStateShard deepen_glintTranslucentShader() { throw new AssertionError(); }

    @Accessor("RENDERTYPE_ENTITY_GLINT_SHADER")
    static RenderStateShard.ShaderStateShard deepen_entityGlintShader() { throw new AssertionError(); }

    @Accessor("RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER")
    static RenderStateShard.ShaderStateShard deepen_entityGlintDirectShader() { throw new AssertionError(); }

    @Accessor("RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER")
    static RenderStateShard.ShaderStateShard deepen_armorEntityGlintShader() { throw new AssertionError(); }
}
