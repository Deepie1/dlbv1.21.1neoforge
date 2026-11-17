package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import deepluckyblock.DeepLuckyBlockMod;

import java.io.IOException;

/**
 * T390-VOID-PORTAL — enregistrement du <b>core shader</b> maison {@code void_portal}.
 *
 * <p><b>Pourquoi un vrai core shader, alors que tout le reste du mod est en voxels ?</b>
 * Parce que le Vide est le seul élément dont l'effet n'est pas une forme mais une
 * <i>profondeur</i>. Un champ d'étoiles en parallaxe ne peut pas être cubé : ce qui le rend
 * crédible, c'est que les étoiles sont accrochées à l'<b>écran</b> et non au monde, donc
 * qu'elles glissent quand la tête tourne. Aucun empilement de mini-cubes ne produit ça —
 * seul un shader qui prend ses coordonnées de texture dans l'espace projeté le fait. C'est
 * exactement le procédé du portail de l'End, et c'est pour cette raison qu'on le copie.</p>
 *
 * <h2>Trois temps, dans cet ordre, sinon rien ne marche</h2>
 * <ol>
 *   <li>{@code ModShaders} (ce fichier) construit le {@link ShaderInstance} à partir des
 *       trois fichiers {@code assets/deep_lucky_block/shaders/core/void_portal.*} ;</li>
 *   <li>{@link ModRenderTypes} enveloppe l'instance dans un {@code ShaderStateShard} —
 *       un <b>fournisseur</b>, jamais la valeur, voir plus bas ;</li>
 *   <li>{@link VoidPortalFx} dessine la géométrie avec ce {@code RenderType}.</li>
 * </ol>
 *
 * <h2>Deux pièges, tous deux déjà payés par d'autres mods</h2>
 *
 * <p><b>1. Ne JAMAIS retenir l'instance qu'on passe à l'événement.</b> {@code registerShader}
 * reçoit une instance <i>candidate</i> et un callback ; c'est l'objet livré <b>au callback</b>
 * qu'il faut garder. À chaque rechargement de ressources (F3+T, changement de pack) le moteur
 * reconstruit les shaders : qui a mémorisé l'objet du premier appel pointe ensuite sur un
 * programme GL détruit, et le jeu crashe au premier triangle.</p>
 *
 * <p><b>2. Le namespace dans le nom.</b> Vanilla cherche ses shaders dans
 * {@code minecraft:shaders/core/} et n'accepte pas de préfixe. NeoForge patche à la fois le
 * chargement du JSON et celui des deux programmes pour autoriser
 * {@code deep_lucky_block:void_portal} : d'où le {@code ResourceLocation} ci-dessous, et
 * d'où les champs {@code "vertex"} / {@code "fragment"} du JSON qui portent eux aussi le
 * namespace. Sans ça, le chargement échoue en cherchant {@code minecraft:...void_portal.vsh}.</p>
 *
 * <p>Enfin, {@code ResourceLocation} n'a plus de constructeur public depuis 1.21 :
 * {@link ResourceLocation#fromNamespaceAndPath} est la seule porte d'entrée.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModShaders {

    private ModShaders() {}

    /** {@code assets/deep_lucky_block/shaders/core/void_portal.{json,vsh,fsh}} */
    public static final ResourceLocation VOID_PORTAL_ID =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "void_portal");

    /**
     * Rempli par le callback de {@link RegisterShadersEvent}, et <b>remplacé</b> à chaque
     * rechargement de ressources. Jamais lu directement : passer par {@link #voidPortal()}.
     */
    private static ShaderInstance voidPortal;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        // Le format de sommet DOIT être celui que la géométrie écrira (POSITION seul, comme
        // rendertype_end_portal) : c'est lui qui décide des attributs « in » compilés dans le
        // .vsh. Un POSITION_COLOR ici et un addVertex() sans couleur là-bas = sommets décalés.
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), VOID_PORTAL_ID, DefaultVertexFormat.POSITION),
                loaded -> voidPortal = loaded);
    }

    /**
     * @return le shader compilé, ou {@code null} tant que le premier chargement de ressources
     *         n'a pas eu lieu. Les appelants doivent tester : dessiner avec un shader nul
     *         remonte en NPE au fond de {@code RenderSystem}.
     */
    public static ShaderInstance voidPortal() {
        return voidPortal;
    }
}
