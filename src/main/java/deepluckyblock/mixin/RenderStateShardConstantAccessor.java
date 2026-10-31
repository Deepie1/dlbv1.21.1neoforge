package deepluckyblock.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessors des constantes d'état du glint (hors shaders). Type de retour EXACT.
 */
@Mixin(RenderStateShard.class)
public interface RenderStateShardConstantAccessor {
    @Accessor("GLINT_TRANSPARENCY")
    static RenderStateShard.TransparencyStateShard deepen_glintTransparency() { throw new AssertionError(); }

    @Accessor("COLOR_WRITE")
    static RenderStateShard.WriteMaskStateShard deepen_colorWrite() { throw new AssertionError(); }

    @Accessor("NO_CULL")
    static RenderStateShard.CullStateShard deepen_noCull() { throw new AssertionError(); }

    @Accessor("EQUAL_DEPTH_TEST")
    static RenderStateShard.DepthTestStateShard deepen_equalDepthTest() { throw new AssertionError(); }

    @Accessor("GLINT_TEXTURING")
    static RenderStateShard.TexturingStateShard deepen_glintTexturing() { throw new AssertionError(); }

    @Accessor("ENTITY_GLINT_TEXTURING")
    static RenderStateShard.TexturingStateShard deepen_entityGlintTexturing() { throw new AssertionError(); }

    @Accessor("ITEM_ENTITY_TARGET")
    static RenderStateShard.OutputStateShard deepen_itemEntityTarget() { throw new AssertionError(); }

    @Accessor("VIEW_OFFSET_Z_LAYERING")
    static RenderStateShard.LayeringStateShard deepen_viewOffsetZLayering() { throw new AssertionError(); }
}
