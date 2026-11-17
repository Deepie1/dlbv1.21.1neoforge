package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import deepluckyblock.DeepLuckyBlockMod;

/**
 * T390-VOID-PORTAL — le {@code RenderType} qui branche {@link ModShaders#voidPortal()} sur
 * les deux textures du Vide.
 *
 * <p>Calqué sur {@code RenderType.END_PORTAL} de 1.21.1, avec trois écarts assumés :</p>
 * <ul>
 *   <li><b>{@code NO_CULL}.</b> Vanilla dessine des faces de bloc dont l'enroulement est
 *       garanti ; nous dessinons un disque face caméra qu'on peut contourner. Sans
 *       {@code NO_CULL}, la fenêtre disparaît dès qu'on passe derrière.</li>
 *   <li><b>Pas de {@code setOutputState}.</b> Le reste du mod vise {@code PARTICLES_TARGET} ;
 *       ici on reste sur la cible principale, parce que la fenêtre écrit la <b>profondeur</b>
 *       et doit donc occulter le décor comme un vrai trou — la cible particules est
 *       recomposée après coup et n'occulte rien.</li>
 *   <li><b>Masque d'écriture par défaut</b> ({@code COLOR_DEPTH_WRITE}), pour la même raison.</li>
 * </ul>
 *
 * <p><b>Pourquoi un fournisseur et pas le shader lui-même.</b> {@code ShaderStateShard} prend
 * un {@code Supplier} : l'état est évalué à chaque passe. Après un F3+T, {@code ModShaders}
 * aura remplacé son champ, et ce {@code RenderType} — construit une seule fois — pointera
 * automatiquement sur le nouveau programme. Capturer l'instance ici gèlerait un programme GL
 * détruit.</p>
 *
 * <p><b>Pourquoi {@code MultiTextureStateShard}.</b> Le shader déclare deux samplers.
 * {@code TextureStateShard} n'en lie qu'un (Sampler0) ; le second resterait sur la dernière
 * texture liée par le jeu, et le champ d'étoiles afficherait un bout d'atlas. L'ordre des
 * {@code add()} est l'ordre des samplers : fond d'abord, étoiles ensuite.</p>
 */
public final class ModRenderTypes {

    private ModRenderTypes() {}

    public static final ResourceLocation VOID_SKY =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "textures/effect/void_sky.png");

    public static final ResourceLocation VOID_STARS =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "textures/effect/void_stars.png");

    private static final RenderStateShard.ShaderStateShard VOID_PORTAL_SHADER =
            new RenderStateShard.ShaderStateShard(ModShaders::voidPortal);

    /**
     * Construit à la demande puis conservé : un {@code RenderType} doit être un
     * <b>singleton</b>, sans quoi {@code MultiBufferSource} ouvre un tampon par instance et
     * la géométrie se disperse en autant de passes muettes.
     */
    private static RenderType voidPortalType;

    public static RenderType voidPortal() {
        if (voidPortalType == null) {
            voidPortalType = RenderType.create(
                    "dlb_void_portal",
                    DefaultVertexFormat.POSITION,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(VOID_PORTAL_SHADER)
                            .setTextureState(new RenderStateShard.MultiTextureStateShard.Builder()
                                    // blur = false, mipmap = false : les étoiles sont des pixels
                                    // isolés ; un filtrage linéaire les étalerait en taches grises
                                    // et un mipmap les ferait disparaître à distance.
                                    .add(VOID_SKY, false, false)
                                    .add(VOID_STARS, false, false)
                                    .build())
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .createCompositeState(false));
        }
        return voidPortalType;
    }
}
