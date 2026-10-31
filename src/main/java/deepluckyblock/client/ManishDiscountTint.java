package deepluckyblock.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * ==================== MANISH : TEINTE ROUGE SUR LES ECHANGES EN REMISE ====================
 *
 * FIX (demande en jeu, reponse a la question laissee sans suite par la session
 * precedente : "quand on ouvre la liste des echanges possibles, je veux que le
 * trade avec discount ait un background different sur le bouton, comme une
 * teinte rougeatre, sur la texture vanilla") :
 *
 * Le rendu vanilla de {@code MerchantScreen} ne distingue visuellement PAS une
 * offre en remise d'une offre au prix plein : seul le prix barre dans l'objet
 * de cout change, ce qui est invisible tant qu'on ne regarde pas l'item en
 * detail. Le joueur ne peut donc pas reperer d'un coup d'oeil, dans la liste
 * des 7 offres de Manish, lesquelles sont soldees.
 *
 * APPROCHE (aucun mixin, aucune texture ajoutee) :
 *  - On s'abonne a {@link ScreenEvent.Render.Pre} (bus de jeu, cote CLIENT
 *    uniquement) : cet evenement est emis juste AVANT le rendu de l'ecran.
 *    On dessine donc notre teinte a ce moment-la, et le bouton vanilla se
 *    rend PAR-DESSUS -- la texture d'origine reste donc parfaitement
 *    visible, exactement comme demande ("sur la texture vanilla"), notre
 *    couleur ne faisant que colorer le fond du bouton.
 *  - La teinte est un simple {@code fill()} semi-transparent (ARGB) aux
 *    coordonnees du bouton d'offre concerne : pas de ressource externe, pas
 *    de shader, rien qui puisse casser un resource pack.
 *
 * DETECTION DE LA REMISE :
 * Vanilla stocke la remise dans {@code MerchantOffer#getSpecialPriceDiff()} :
 * valeur NEGATIVE = prix reduit (remise), positive = prix majore (penalite de
 * reputation). On ne teinte donc qu'en rouge les offres a diff < 0.
 *
 * ROBUSTESSE : l'ecran marchand vanilla n'expose pas publiquement son champ
 * {@code scrollOff} (package-private), donc plutot que de tenter d'y acceder
 * par reflexion -- fragile et casse par tout remappage -- on retrouve l'index
 * reel de chaque bouton via {@code TradeOfferButton#getIndex()}, qui est
 * public et fait autorite : c'est la valeur que vanilla lui-meme utilise pour
 * savoir quelle offre le bouton represente, defilement deja pris en compte.
 * Tout est enveloppe dans un try/catch : en cas d'evolution de l'API, la
 * teinte disparait silencieusement, l'ecran de commerce reste 100% fonctionnel.
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class ManishDiscountTint {

    private ManishDiscountTint() {}

    /** Rouge translucide pose sous le bouton d'une offre en remise (ARGB). */
    private static final int DISCOUNT_TINT_ARGB = 0x66C81E1E;

    /**
     * Marge rognee sur le contour du bouton : le widget vanilla fait 89x20 mais
     * sa texture utile (le cadre du bouton) laisse 1 px transparent sur les
     * bords -- teinter la zone brute deborderait legerement du cadre visible.
     */
    private static final int INSET = 1;

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        try {
            if (!(event.getScreen() instanceof MerchantScreen screen)) return;

            MerchantOffers offers = screen.getMenu().getOffers();
            if (offers == null || offers.isEmpty()) return;

            GuiGraphics graphics = event.getGuiGraphics();

            // Les boutons d'offre sont des widgets enregistres de l'ecran : on
            // les parcourt sans jamais supposer leur position ni leur nombre
            // (vanilla en affiche 7 au maximum, mais c'est un detail interne).
            for (Renderable renderable : screen.renderables) {
                if (!(renderable instanceof AbstractWidget widget)) continue;
                if (!widget.visible) continue;

                int index = indexOfTradeButton(widget);
                if (index < 0 || index >= offers.size()) continue;

                MerchantOffer offer = offers.get(index);
                if (offer == null) continue;

                // specialPriceDiff < 0  =>  l'offre est SOLDEE.
                if (offer.getSpecialPriceDiff() >= 0) continue;

                graphics.fill(
                        widget.getX() + INSET,
                        widget.getY() + INSET,
                        widget.getX() + widget.getWidth() - INSET,
                        widget.getY() + widget.getHeight() - INSET,
                        DISCOUNT_TINT_ARGB);
            }
        } catch (Throwable ignored) {
            // Jamais de crash de l'ecran de commerce a cause d'un simple effet
            // cosmetique : en cas de probleme on n'affiche simplement rien.
        }
    }

    /**
     * Index de l'offre representee par ce widget, ou -1 si ce n'est pas un
     * bouton d'offre marchande.
     *
     * {@code MerchantScreen.TradeOfferButton} est une classe interne
     * package-private : on ne peut pas l'importer, mais sa methode
     * {@code getIndex()} est publique. On l'appelle donc par reflexion, une
     * seule fois par bouton et par frame, en mettant la Method en cache. Le
     * test porte sur le NOM SIMPLE de la classe, insensible au package, ce qui
     * reste valide quel que soit le mapping (mojmap en dev comme en prod sur
     * NeoForge 1.21.1).
     */
    private static java.lang.reflect.Method cachedGetIndex;
    private static Class<?> cachedButtonClass;

    private static int indexOfTradeButton(AbstractWidget widget) {
        try {
            Class<?> type = widget.getClass();
            if (type != cachedButtonClass) {
                if (!"MerchantScreen$TradeOfferButton".equals(simpleBinaryName(type))) return -1;
                java.lang.reflect.Method m = type.getMethod("getIndex");
                m.setAccessible(true);
                cachedButtonClass = type;
                cachedGetIndex = m;
            }
            if (cachedGetIndex == null) return -1;
            Object value = cachedGetIndex.invoke(widget);
            return value instanceof Integer i ? i : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** "a.b.Outer$Inner" -> "Outer$Inner". */
    private static String simpleBinaryName(Class<?> type) {
        String name = type.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
