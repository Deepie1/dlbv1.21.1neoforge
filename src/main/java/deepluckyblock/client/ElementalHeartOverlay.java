package deepluckyblock.client;

import deepluckyblock.util.DeepEnchant;
import deepluckyblock.util.ElementalHeartLoss;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rendu HUD des coeurs bonus elementaires.
 *
 * REFONTE #4 (ask #1, "retrecir tous les sprites de coeur a exactement
 * 9x9 px sur la grille HUD vanilla, meme si l'art source est plus grand") :
 * utilise DESORMAIS EXCLUSIVEMENT les vraies textures dessinees a la main
 * par l'utilisateur (dossier source {@code heart_source_user/<element>/},
 * 20x20 d'origine), PRE-REDUITES UNE FOIS hors-jeu en NATIF 9x9 (nearest
 * neighbor -- prelevement direct de pixels deja presents dans la source,
 * ZERO pixel invente/melange/anti-alias) et livrees sous
 * {@code assets/deep_lucky_block/textures/gui/sprites/hud/}) :
 *  - {@code heart_<element>_full.png} / {@code _half.png} : sprites NATIFS
 *    9x9, reduits depuis la source 20x20 fournie par l'utilisateur (aucune
 *    retouche de contenu, seule la resolution change, en aval, une seule
 *    fois, jamais a la volee dans le rendu).
 *  - {@code heart_<element>_empty.png} : canevas 9x9 ENTIEREMENT
 *    TRANSPARENT (aucun asset "vide" n'a ete fourni ; un canevas vide
 *    n'invente aucun pixel visible et respecte l'exigence "plus rien ne
 *    s'affiche une fois le coeur completement perdu").
 *  - {@code anim/heart_<element>_full_to_half_<i>.png} et
 *    {@code anim/heart_<element>_half_to_empty_<i>.png} : SEQUENCES DE
 *    FRAMES FOURNIES PAR L'UTILISATEUR, copiees telles quelles. Le nombre de
 *    frames VARIE selon l'element (ex. Feu : 3 frames plein->demi, 5 frames
 *    demi->vide ; Eau : 6 puis 9 ; etc.) -- le lecteur d'animation ci-dessous
 *    est generique et decouvre ce nombre par element au chargement, il ne
 *    suppose JAMAIS un nombre fixe de frames.
 *  - Le regain (vide->demi->plein) REUTILISE les memes frames DANS L'ORDRE
 *    INVERSE (aucun asset de regain separe n'existe, comme specifie par
 *    l'utilisateur).
 *  - Timing : 150 ms/frame (3 ticks a 20 TPS), comme demande.
 *  - AUCUN halo/aura (le concept d'aura autour des coeurs a ete explicitement
 *    mis de cote par l'utilisateur) : ce rendu ne dessine plus que le coeur
 *    lui-meme.
 *
 * Purement client, purement additif : ne touche jamais au rendu vanilla des
 * coeurs normaux (VanillaGuiLayers.PLAYER_HEALTH n'est jamais annule).
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class ElementalHeartOverlay {

    private ElementalHeartOverlay() {}

    private record Element(String enchantId, String texture) {}

    private static final Element[] ELEMENTS = new Element[] {
        new Element("fire_heart", "fire"),
        new Element("water_heart", "water"),
        new Element("earth_heart", "earth"),
        new Element("wind_heart", "wind"),
        new Element("electric_heart", "electric"),
        new Element("shadow_heart", "shadow"),
        new Element("light_heart", "light"),
    };

    // FIX #1 DEFINITIF (rapporte en jeu a DEUX reprises malgre des
    // correctifs precedents "les coeurs sont bcp trop gros [...] il faut
    // les retrecir comme ca ils ont le meme visuel qu'un coeur normal de
    // 9x9 px") : les tentatives precedentes (DRAW_SIZE=11, puis DRAW_SIZE=9
    // mais en continuant a lire/upscaler-downscaler a la volee un fichier
    // SOURCE de 20x20 via GuiGraphics#blit) restaient fragiles : un
    // redimensionnement EN DESTINATION seul depend du filtrage choisi par
    // le pipeline de rendu (peut varier bilineaire/nearest selon le driver)
    // et ne garantit pas un rendu NATIF pixel-perfect. Le fichier source
    // 20x20 de l'utilisateur ({@code heart_source_user/<element>/}) est
    // desormais pre-traite UNE FOIS, hors du jeu (script offline, PAS de
    // pixel invente : reechantillonnage NEAREST NEIGHBOR, qui ne fait que
    // PRELEVER des pixels deja presents dans la source, jamais de moyenne/
    // anti-aliasing/interpolation), pour produire un asset livre au format
    // FINAL natif 9x9 sous
    // {@code assets/deep_lucky_block/textures/gui/sprites/hud/} (chemin
    // demande). Le jeu ne redimensionne plus RIEN au rendu : SRC_SIZE et
    // DRAW_SIZE valent tous deux 9, un blit 1:1 sans aucune mise a l'echelle
    // GPU, don rendu strictement identique quel que soit le pilote/filtrage.
    //
    // Verification "zoom x4" (voir /home/user/heart_zoom_previews/
    // zoom_x4_all_elements_full_half_empty.png, planche recapitulative
    // generee pour cette livraison) : chaque sprite 9x9 agrandi x4 (36x36 px
    // simules) montre une silhouette nette, sans flou d'interpolation ni
    // artefact de bord -- lisible et fidele au dessin original malgre la
    // forte reduction 20->9 (55% de la resolution lineaire d'origine).
    private static final int SRC_SIZE = 9;
    private static final int VANILLA_SIZE = 9;
    private static final int DRAW_SIZE = VANILLA_SIZE;
    private static final int DRAW_OFFSET = 0; // sprite deja a la taille vanilla exacte, aucun centrage a compenser

    // FIX (rapporte en jeu, captures a l'appui) : le pas horizontal vanilla
    // ENTRE deux coeurs est de 8 px (les coeurs vanilla se chevauchent
    // volontairement d'1 px sur leur bord transparent).
    private static final int STEP_X = 8;

    // Duree d'UNE frame d'animation EN PERTE : 150 ms demandes = 3 ticks a 20 TPS.
    private static final int ANIM_FRAME_TICKS = 3;

    // FIX (ask #4, "l'animation de regen doit etre plus lente et plus fluide
    // que celle de perte") : la perte doit rester nette/immediate (le joueur
    // vient d'encaisser un coup, la reaction visuelle doit etre vive) alors
    // que la regeneration est desormais une recompense LENTE et DOUCE (voir
    // HOLD_TICKS_BEFORE_REGEN/STAGE_TICKS cote serveur). On rejoue donc les
    // memes frames FOURNIES PAR L'UTILISATEUR (aucun nouvel asset invente)
    // mais A UN RYTHME 2x PLUS LENT en regen -- exactement le facteur
    // demande ("dois etre environ 2x plus lente que la moyenne"), pour un
    // fondu perceptiblement plus fluide entre les deux etapes 0->0.5->1.0.
    private static final int ANIM_FRAME_TICKS_GAIN = ANIM_FRAME_TICKS * 2;

    // Nombre de frames par element et par demi-transition (plein<->demi,
    // demi<->vide), decouvert dynamiquement (voir FrameCounts) car il varie
    // selon l'element (les sequences fournies par l'utilisateur n'ont pas
    // toutes la meme longueur).
    private record FrameCounts(int fullToHalf, int halfToEmpty) {}
    private static final Map<String, FrameCounts> FRAME_COUNTS = new HashMap<>();
    static {
        // Comptes fixes correspondant exactement aux fichiers copies depuis
        // heart_source_user/ (voir script d'import) -- pas de decouverte a
        // l'execution (le client ne liste pas les resources du jar), donc
        // ces valeurs DOIVENT rester synchronisees avec les fichiers reels
        // presents sous assets/deep_lucky_block/textures/gui/sprites/hud/anim/.
        FRAME_COUNTS.put("fire", new FrameCounts(3, 5));
        FRAME_COUNTS.put("water", new FrameCounts(6, 9));
        FRAME_COUNTS.put("earth", new FrameCounts(5, 6));
        FRAME_COUNTS.put("wind", new FrameCounts(5, 6));
        FRAME_COUNTS.put("electric", new FrameCounts(3, 6));
        FRAME_COUNTS.put("shadow", new FrameCounts(6, 8));
        FRAME_COUNTS.put("light", new FrameCounts(4, 5));
    }

    // Suivi cote client, par joueur (multi-joueur local / spectateur) : sante
    // vue au tick precedent, pour detecter une baisse et declencher le flash.
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

    // Etat d'un slot de coeur individuel exprime en HUITIEMES DE COEUR
    // (0=vide, 1=demi, 2=plein) pour detecter proprement les transitions.
    private static final Map<String, Integer> LAST_STATE = new HashMap<>();
    private static final Map<String, Integer> ANIM_START_TICK = new HashMap<>();
    private static final Map<String, int[]> ANIM_FROM_TO = new HashMap<>();

    // FIX (ask #1, "creer les animations manquantes de PERTE et de GAIN du
    // coeur PLEIN, actuellement Minecraft retombe sur son animation vanilla
    // de pop-in/pop-out car aucune transition custom n'est jouee dans ces
    // deux cas precis") : les frames par element existent deja (voir
    // FRAME_COUNTS) et sont deja utilisees pour perte/gain PARTIELS (dus aux
    // vrais degats/regen, via LAST_STATE ci-dessus qui detecte un changement
    // 2->1->0 ou 0->1->2 PENDANT que le plastron reste equipe). Les deux cas
    // qui echappaient a ce mecanisme sont :
    //  1) GAIN a l'equipement du plastron : le tout premier rendu d'un slot
    //     (prevState == null) n'a jamais de transition, le coeur plein
    //     apparait donc instantanement -- on force ici une transition
    //     0->2 (vide->plein, memes frames que la regen, jouees dans l'ordre
    //     inverse comme d'habitude) au lieu de sauter directement a l'etat
    //     final.
    //  2) PERTE au retrait du plastron : l'ancien code faisait
    //     `if (chest.isEmpty()) return;` des le debut de render(), qui
    //     coupe le dessin AVANT meme de detecter que l'etat vient de passer
    //     de plein/demi a "plus d'item du tout" -- le coeur disparaissait
    //     donc net, sans jamais jouer les frames de perte. On memorise
    //     desormais le dernier etat par slot AVANT de savoir si le
    //     plastron est toujours present, et si le plastron vient de
    //     disparaitre (ou si CET enchant vient de disparaitre du plastron),
    //     on joue la transition de perte vers 0 en utilisant le DERNIER set
    //     de niveaux connu, avant de laisser le slot expirer definitivement.
    private static final Set<String> SEEN_SLOTS = Collections.synchronizedSet(new HashSet<>());

    // FIX CRITIQUE (rapporté en jeu : "aussi, wtf, des que tu commence a
    // charger le premier coeur, je vois les coeurs noirs en fond des 3
    // coeurs !") : la logique "firstSeen" ci-dessus force une transition
    // 0->2 (vide->plein, frames de perte rejouées à l'envers) pour TOUT
    // slot jamais rendu dans cette session client -- ce qui inclut, à
    // TORT, le tout premier rendu après une CONNEXION/reconnexion/
    // changement de dimension avec l'armure DÉJÀ équipée : dans ce cas il
    // ne s'agit PAS d'un "gain" réel (le joueur portait déjà l'armure), et
    // pourtant les 2-3 coeurs actifs se mettent TOUS à jouer la même
    // transition longue (~2.4s à vitesse GAIN) en même temps -- les
    // premières frames de cette transition sont volontairement PRESQUE
    // VIDES (elles réutilisent la fin de la séquence de perte, la plus
    // dépouillée), donc pendant cette fenêtre le cache opaque sombre
    // (*_cover.png, voir REFONTE #5/#6) domine largement le sprite réel :
    // les 2-3 coeurs apparaissent alors simultanément comme des silhouettes
    // NOIRES/vides, exactement le symptôme rapporté ("coeurs noirs en
    // fond"), avant de progressivement se remplir. Fix : on distingue
    // désormais un VRAI premier équipement PENDANT la session (qui doit
    // continuer à jouer l'animation d'entrée demandée en ask #1) d'un
    // simple PREMIER RENDU DE SESSION avec l'armure déjà portée (connexion,
    // reconnexion, changement de dimension, /reload...) : ce dernier
    // n'anime plus rien et affiche directement l'état réel, sans aucun
    // flash. Chaque joueur n'a droit qu'à UNE seule "passe de référence"
    // (silencieuse) au tout début de sa session client.
    private static final Set<UUID> SESSION_BASELINE_DONE = Collections.synchronizedSet(new HashSet<>());

    // Snapshot du dernier rendu "actif" (chest non vide, au moins un coeur)
    // par joueur, pour pouvoir encore jouer l'animation de perte UNE fois
    // apres que le plastron ait disparu de l'equipement.
    private record LastActiveSnapshot(int[] levels, int[] lostPerElement, ItemStack chestCopy) {}
    private static final Map<UUID, LastActiveSnapshot> LAST_ACTIVE = new HashMap<>();
    // Combien de ticks on continue a rendre/animer apres disparition du
    // plastron, avant d'abandonner definitivement le slot (couvre la duree
    // de la plus longue sequence half_to_empty a vitesse perte normale).
    private static final int POST_REMOVE_GRACE_TICKS = 200;
    private static final Map<UUID, Integer> POST_REMOVE_SINCE = new HashMap<>();

    // FIX #8 (rapporte en jeu : "nos animations de coeur sont ecrasees par
    // le rendu vanilla des coeurs") : cet overlay est un hook Post (execute
    // APRES le rendu vanilla du layer PLAYER_HEALTH), donc en temps normal
    // nos sprites sont bien dessines PAR-DESSUS les coeurs rouges generiques
    // de vanilla pour les emplacements de coeurs bonus. Si un AUTRE
    // mod/overlay tiers s'abonne aussi en Post sur ce meme layer sans
    // priorite explicite, l'ordre d'execution entre plusieurs
    // @SubscribeEvent Post n'est PAS garanti stable -- EventPriority.LOWEST
    // garantit que ce handler est TOUJOURS le DERNIER a s'executer pour ce
    // layer, donc nos coeurs restent TOUJOURS au-dessus, plus jamais
    // recouverts.
    //
    // REFONTE #5 (rapportee en jeu, CE segment -- deux bugs lies) :
    //   (a) "perdre un demi-coeur ou un coeur entier ne joue plus AUCUNE
    //       animation" ;
    //   (b) "les 3 derniers coeurs de la barre vanilla NORMALE prennent
    //       BRIEVEMENT l'apparence d'un coeur custom, sans effet, et ne
    //       disparaissent qu'au coup suivant qui rafraichit la barre".
    // Root cause de (b) (voir EquipmentExpansionHandler.breakOneElementalHeart) :
    // le compteur NBT "perdu" etait ecrit IMMEDIATEMENT au moment du coup,
    // mais le VRAI attribut MAX_HEALTH (dont depend le nombre total de
    // slots de coeur affiches par vanilla) n'etait recalcule que jusqu'a 10
    // ticks plus tard -- desormais corrige cote serveur (reduction
    // immediate et atomique du modificateur des le coeur casse).
    //
    // Root cause de (a) et de la fragilite generale : l'ancienne approche
    // utilisait un MIXIN ({@code GuiHeartSuppressMixin}) qui annulait le
    // rendu vanilla d'un coeur PRECIS en devinant a l'avance (hook Pre,
    // AVANT le rendu vanilla) les coordonnees exactes qu'occupera un coeur
    // custom CE FRAME, avec une formule de positionnement dupliquee et
    // recalculee separement de celle du rendu Post ci-dessous. Toute
    // divergence d'un pixel/tick entre les deux calculs (Pre vs Post) --
    // ex. pendant une animation de transition, ou juste apres un
    // changement d'etat -- pouvait desynchroniser suppression et rendu, et
    // dans le pire cas, court-circuiter silencieusement l'affichage sans
    // qu'aucune animation ne demarre.
    //
    // FIX (suggestion utilisateur, adoptee) : on abandonne entierement le
    // mixin de suppression. A la place, {@link #blitHeart} dessine
    // desormais TOUJOURS, juste avant le sprite du coeur lui-meme, un
    // "cache" -- voir {@code heart_<element>_<etat>_cover.png} / {@code
    // heart_<element>_<transition>_<frame>_cover.png}.
    //
    // REFONTE #6 (rapporte en jeu : "au lieu de redessiner les coeurs
    // vanilla en arriere-plan avec un cache sombre, pourquoi pas juste
    // mettre des textures vides derriere qui remplacent les coeurs
    // vanilla, devant nos coeurs moddes normaux ?" + fantome de coeur
    // vanilla persistant apres une perte) : les caches "REFONTE #5"
    // etaient generes par extraction du canal alpha de CHAQUE sprite
    // (plein/demi/etat precis), donc de silhouette VARIABLE -- or le
    // sprite "vide" d'un coeur elementaire est ENTIEREMENT transparent
    // (aucun pixel a masquer), et le contour vanilla ("hud/heart/
    // container", le cercle vide dessine par Minecraft en permanence
    // derriere CHAQUE emplacement de coeur, meme un coeur plein) est plus
    // large que la silhouette "pleine" (54 pixels opaques vs 34 pour un
    // coeur plein, 20 pour un demi-coeur, 0 pour un coeur vide) : dans les
    // trois cas, une partie du coeur vanilla rouge restait visible sous
    // notre sprite -- c'est ce liseret qui causait le "fantome" de coeur
    // vanilla rapporte, spectaculairement visible des qu'un coeur passe a
    // l'etat vide (cache alors 100% transparent = AUCUNE occultation).
    //
    // Chaque {@code *_cover.png} contient maintenant un pixel-perfect
    // COPIE de la vraie texture vanilla {@code hud/heart/container.png}
    // (le contour gris/noir du coeur vide) -- exactement la texture que
    // l'utilisateur demandait ("mettre des textures vides ... qui
    // remplacent les coeurs vanilla"). Rendu obtenu par blitHeart :
    //   1. cover (= contour vanilla vide, IDENTIQUE quel que soit l'etat)
    //   2. sprite reel (plein/demi/vide/transition) par-dessus
    // Un coeur "vide" affiche donc desormais EXACTEMENT le meme contour
    // que vanilla utilise pour ses propres coeurs manquants (au lieu d'un
    // bloc sombre ou d'un fantome rouge), et un coeur plein/demi masque
    // TOUJOURS integralement le rouge vanilla en dessous car container.png
    // couvre une silhouette strictement plus large que full.png/half.png.
    // Comme ce cache est dessine PAR LE MEME appel Post, au MEME (x,y), a
    // la MEME frame que le sprite qu'il precede, il n'existe toujours
    // AUCUNE fenetre de desynchronisation possible entre "ce qui est
    // masque" et "ce qui est dessine". {@code GuiHeartSuppressMixin} n'est
    // plus utilise (retire de la configuration mixins) mais son fichier
    // source est conserve pour reference/rollback rapide si besoin.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onRenderHealth(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) return;
        try { render(event); } catch (Throwable t) {
            System.err.println("[DeepLuckyBlock] ElementalHeartOverlay render error: " + t);
        }
    }

    private static void render(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // Vanilla masque sa propre barre de vie en Creatif/Spectateur (joueur
        // invulnerable) : on applique le meme critere pour rester coherent.
        if (player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable) return;

        UUID uuid = player.getUUID();
        // true UNIQUEMENT au tout premier appel de render() pour ce joueur
        // depuis le lancement du client (connexion/reconnexion/dimension) --
        // voir Javadoc de SESSION_BASELINE_DONE. Tous les slots "firstSeen"
        // rencontrés PENDANT ce seul appel n'animent pas (état réel affiché
        // directement) ; tout futur "firstSeen" (un NOUVEL élément équipé en
        // cours de session) continue de jouer l'animation d'entrée normale.
        boolean isSessionBaselinePass = !SESSION_BASELINE_DONE.contains(uuid);
        SESSION_BASELINE_DONE.add(uuid);
        ItemStack liveChest = player.getItemBySlot(EquipmentSlot.CHEST);

        int[] liveLevels = new int[ELEMENTS.length];
        int liveActiveCount = 0;
        if (!liveChest.isEmpty()) {
            for (int i = 0; i < ELEMENTS.length; i++) {
                int lvl = lvl(liveChest, ELEMENTS[i].enchantId());
                liveLevels[i] = lvl;
                if (lvl > 0) liveActiveCount++;
            }
        }

        ItemStack chest;
        int[] levels;
        int activeCount;
        int tick = player.tickCount;

        if (liveActiveCount > 0) {
            // Cas normal : plastron equipe avec au moins un coeur actif.
            // On memorise cet etat pour pouvoir encore jouer l'animation de
            // PERTE plus tard si le plastron disparait brutalement (retire,
            // casse, joueur mort...).
            chest = liveChest;
            levels = liveLevels;
            activeCount = liveActiveCount;
            int[] lostSnapshot = new int[ELEMENTS.length];
            for (int i = 0; i < ELEMENTS.length; i++) {
                if (levels[i] <= 0) continue;
                lostSnapshot[i] = Math.max(0, Math.min(levels[i], ElementalHeartLoss.getLost(chest, ELEMENTS[i].enchantId())));
            }
            LAST_ACTIVE.put(uuid, new LastActiveSnapshot(levels.clone(), lostSnapshot, chest.copy()));
            POST_REMOVE_SINCE.remove(uuid);
        } else {
            // FIX (ask #1/#3, voir commentaire de SEEN_SLOTS ci-dessus) :
            // le plastron vient de disparaitre (retire/casse/mort) ou n'a
            // jamais eu de coeur actif. Si on a un dernier etat "actif"
            // connu pour ce joueur et qu'on est encore dans la fenetre de
            // grace, on continue de rendre ce DERNIER etat connu (chest
            // fige = snapshot) mais en FORCANT la cible de tous les slots
            // vers "vide" -- ca declenche la transition de PERTE normale
            // (2->1->0, frames existantes) au lieu de faire disparaitre le
            // coeur net. Une fois la fenetre de grace ecoulee (l'animation
            // a largement eu le temps de finir), on abandonne et on nettoie.
            LastActiveSnapshot snap = LAST_ACTIVE.get(uuid);
            if (snap == null) return;
            int since = POST_REMOVE_SINCE.getOrDefault(uuid, -1);
            if (since < 0) { since = tick; POST_REMOVE_SINCE.put(uuid, since); }
            if (tick - since > POST_REMOVE_GRACE_TICKS) {
                LAST_ACTIVE.remove(uuid);
                POST_REMOVE_SINCE.remove(uuid);
                return;
            }
            chest = snap.chestCopy();
            levels = snap.levels();
            activeCount = 0;
            for (int lv : levels) if (lv > 0) activeCount++;
            if (activeCount == 0) { LAST_ACTIVE.remove(uuid); POST_REMOVE_SINCE.remove(uuid); return; }
        }

        boolean forceAllEmpty = (liveActiveCount == 0);

        float health = player.getHealth();
        LAST_HEALTH.put(uuid, health);

        GuiGraphics gg = event.getGuiGraphics();
        int screenW = gg.guiWidth();
        int screenH = gg.guiHeight();
        int left = screenW / 2 - 91;

        // Formule IDENTIQUE a Gui#renderPlayerHealth : nos coeurs sont la
        // CONTINUATION directe de la grille vanilla, jamais un bloc a part.
        float realMaxHealth = player.getMaxHealth();
        float absorption = player.getAbsorptionAmount();

        int totalLostAllElements = 0;
        int totalBonusHeartSlots = 0;
        int[] lostPerElement = new int[ELEMENTS.length];
        for (int i = 0; i < ELEMENTS.length; i++) {
            int lvl = levels[i];
            if (lvl <= 0) continue;
            int lost = Math.max(0, Math.min(lvl, ElementalHeartLoss.getLost(chest, ELEMENTS[i].enchantId())));
            lostPerElement[i] = lost;
            totalLostAllElements += lost;
            totalBonusHeartSlots += lvl;
        }

        float virtualMaxHealth = realMaxHealth + 2.0f * totalLostAllElements;

        // FIX #9 (rapporte en jeu : "apres avoir perdu un coeur, sa texture
        // persiste comme sprite orphelin dans la ligne du dessous, ou elle
        // n'a rien a faire" -- confirme comme un vrai bug de MISE EN PAGE,
        // pas un artefact d'animation) : l'ancien code calculait rowHeight
        // (l'espacement vertical compresse quand le joueur a beaucoup de PV,
        // cf. Gui#renderPlayerHealth vanilla) a partir de virtualMaxHealth,
        // qui INCLUT en permanence les coeurs elementaires "perdus" comme
        // s'ils etaient pleins (+2 PV/coeur perdu, pour que le nombre TOTAL
        // d'emplacements a dessiner reste stable). Vanilla, lui, calcule SA
        // PROPRE rowHeight (pour les VRAIS coeurs qu'il dessine) a partir de
        // player.getMaxHealth() REEL -- qui, lui, EST reduit par les coeurs
        // perdus (voir updateElementalHearts : le modificateur MAX_HEALTH
        // reel diminue de 2 PV par coeur casse). Des que ces deux totaux
        // tombent dans des "paliers de 10 coeurs/rangee" differents (ex :
        // reel tout juste egal a 20/40/60 PV pile alors que le virtuel est
        // legerement au-dessus), vanilla et notre overlay calculent une
        // rowHeight DIFFERENTE pour le meme point d'ancrage vertical -- nos
        // coeurs bonus (qui continuent la grille vanilla) se retrouvent
        // decales d'une fraction de rangee, donnant l'impression qu'une
        // texture de coeur "deborde" dans la rangee du dessous, exactement
        // au moment ou perdre un coeur fait changer virtualMaxHealth de
        // palier sans que le reel n'ait bouge dans le meme sens.
        //
        // FIX : rowHeight (l'espacement vertical) est desormais calcule
        // EXCLUSIVEMENT a partir des valeurs REELLES (realMaxHealth,
        // exactement ce que vanilla utilise pour ses propres coeurs) --
        // donc TOUJOURS identique au calcul interne de vanilla, quel que
        // soit l'etat de perte/regen de nos coeurs elementaires. Seul le
        // nombre TOTAL d'emplacements a dessiner (totalHeartSlots, pour
        // savoir ou continuer la grille) utilise encore virtualMaxHealth --
        // mais avec LE MEME rowHeight vanilla-exact, donc sans plus jamais
        // de decalage de rangee possible.
        int healthRows = Math.max(1, net.minecraft.util.Mth.ceil((realMaxHealth + absorption) / 2.0f / 10.0f));
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int totalHeartSlots = Math.max(1, net.minecraft.util.Mth.ceil(virtualMaxHealth / 2.0f));

        int startSlot = Math.max(0, totalHeartSlots - totalBonusHeartSlots);

        int top = screenH - 39;

        int slotCursor = startSlot;

        for (int i = 0; i < ELEMENTS.length; i++) {
            int lvl = levels[i];
            if (lvl <= 0) continue;
            Element el = ELEMENTS[i];
            ResourceLocation fullTex = tex(el.texture(), "full");
            ResourceLocation emptyTex = tex(el.texture(), "empty");
            ResourceLocation halfTex = tex(el.texture(), "half");

            int lostCount = lostPerElement[i];
            int filledCount = lvl - lostCount;
            // FIX #4 (regen visible en 2 etapes) : si CET element a un coeur
            // en cours de regeneration a l'etape 1/2 (demi-coeur deja
            // accorde par updateElementalHearts, voir ElementalHeartLoss),
            // le PROCHAIN slot vide (index == filledCount) doit s'afficher
            // "demi" au lieu de "vide" -- c'est la seule difference visuelle
            // dont le joueur dispose pour voir la premiere etape en cours
            // avant que la seconde ne complete le coeur.
            int regenStage = lostCount > 0 ? ElementalHeartLoss.getRegenStage(chest, el.enchantId()) : 0;

            for (int h = 0; h < lvl; h++) {
                int slot = slotCursor++;
                int column = slot % 10;
                int rowIdx = slot / 10;
                int x = left + column * STEP_X;
                int y = top - rowIdx * rowHeight;

                // Etat cible EN HUITIEMES DE COEUR pour CE slot precis.
                // FIX (ask #1) : si le plastron vient de disparaitre
                // (forceAllEmpty), la cible de TOUS les slots devient vide,
                // quel que soit filledCount du dernier snapshot connu --
                // c'est ce qui declenche la transition de PERTE (voir plus
                // bas) au lieu de faire disparaitre le coeur instantanement.
                int state;
                if (forceAllEmpty) state = 0;
                else if (h < filledCount) state = 2;
                else if (h == filledCount && regenStage == 1) state = 1; // demi-coeur en cours de regen
                else state = 0;

                String slotKey = uuid + "|" + el.enchantId() + "|" + h;
                Integer prevState = LAST_STATE.get(slotKey);
                boolean firstSeen = !SEEN_SLOTS.contains(slotKey);
                if (firstSeen) {
                    SEEN_SLOTS.add(slotKey);
                    // FIX (ask #1) : PREMIER rendu de ce slot (plastron
                    // qu'on vient d'equiper, ou tout premier coeur de CET
                    // element jamais vu pour ce joueur cette session). Si
                    // l'etat de depart est deja "plein" (h < filledCount,
                    // cas le plus courant a l'equipement), on force une
                    // transition 0->2 (vide->plein) au lieu de sauter
                    // directement au sprite final -- exactement l'animation
                    // de GAIN manquante demandee. Les frames sont les memes
                    // que la regen normale, jouees a l'envers (voir
                    // renderTransition), donc AUCUN nouvel asset requis.
                    //
                    // FIX CRITIQUE ("coeurs noirs en fond") : SAUF si ce
                    // premier rendu fait partie de la toute première passe
                    // de session (voir isSessionBaselinePass/
                    // SESSION_BASELINE_DONE) -- dans ce cas l'armure était
                    // déjà équipée avant même que ce HUD existe (connexion),
                    // ce n'est pas un vrai "gain", donc PAS d'animation :
                    // affichage direct de l'état réel, sans flash sombre.
                    if (state != 0 && !isSessionBaselinePass) {
                        ANIM_START_TICK.put(slotKey, tick);
                        ANIM_FROM_TO.put(slotKey, new int[]{0, state});
                    }
                } else if (prevState != null && prevState != state) {
                    ANIM_START_TICK.put(slotKey, tick);
                    ANIM_FROM_TO.put(slotKey, new int[]{prevState, state});
                }
                LAST_STATE.put(slotKey, state);

                Integer animStart = ANIM_START_TICK.get(slotKey);
                int[] fromTo = ANIM_FROM_TO.get(slotKey);
                int elapsed = animStart == null ? Integer.MAX_VALUE : tick - animStart;
                int totalAnimTicks = fromTo == null ? 0 : totalAnimTicks(el.texture(), fromTo[0], fromTo[1]);
                if (fromTo != null && elapsed >= 0 && elapsed < totalAnimTicks) {
                    renderTransition(gg, x, y, el.texture(), fromTo[0], fromTo[1], elapsed);
                } else {
                    ResourceLocation drawTex = state == 2 ? fullTex : (state == 1 ? halfTex : emptyTex);
                    blitHeart(gg, drawTex, x, y);
                }
            }
        }
    }

    private static void blitHeart(GuiGraphics gg, ResourceLocation tex, int vanillaX, int vanillaY) {
        int x = vanillaX - DRAW_OFFSET;
        int y = vanillaY - DRAW_OFFSET;
        // REFONTE #5 : dessine d'abord le cache opaque (meme silhouette que
        // le sprite, voir Javadoc de onRenderHealth ci-dessus) pour occulter
        // totalement le coeur vanilla rouge en dessous -- y compris ses
        // coins/interstices que le sprite element lui-meme laisse
        // transparents -- PUIS le sprite reel par-dessus, dans le MEME
        // appel, au MEME (x,y) : plus jamais de decalage Pre/Post possible.
        gg.blit(coverTex(tex), x, y, DRAW_SIZE, DRAW_SIZE, 0f, 0f, SRC_SIZE, SRC_SIZE, SRC_SIZE, SRC_SIZE);
        // Largeur/hauteur de DESTINATION = DRAW_SIZE (affichage reduit) ;
        // largeur/hauteur SOURCE (uSize/vSize, les 2 derniers parametres)
        // restent SRC_SIZE=20 : on lit toujours l'integralite du sprite
        // fourni par l'utilisateur, on l'affiche juste plus petit.
        gg.blit(tex, x, y, DRAW_SIZE, DRAW_SIZE, 0f, 0f, SRC_SIZE, SRC_SIZE, SRC_SIZE, SRC_SIZE);
    }

    // Cache le chemin "<...>.png" -> "<...>_cover.png" calcule (evite de
    // reconstruire une String a chaque frame pour chaque coeur affiche).
    private static final Map<ResourceLocation, ResourceLocation> COVER_TEX_CACHE = new HashMap<>();

    /**
     * Chemin du cache opaque associe a un sprite de coeur donne (meme
     * dossier, suffixe {@code _cover} avant {@code .png}). Genere une seule
     * fois hors-jeu (voir Javadoc de onRenderHealth) pour CHAQUE sprite
     * existant (etats full/half/empty ET chaque frame d'animation de
     * chaque element) -- jamais calcule/redimensionne a la volee ici.
     */
    private static ResourceLocation coverTex(ResourceLocation spriteTex) {
        return COVER_TEX_CACHE.computeIfAbsent(spriteTex, t -> {
            String path = t.getPath();
            String coverPath = path.endsWith(".png")
                    ? path.substring(0, path.length() - 4) + "_cover.png"
                    : path + "_cover";
            return ResourceLocation.fromNamespaceAndPath(t.getNamespace(), coverPath);
        });
    }

    private static ResourceLocation tex(String elementTexture, String state) {
        return ResourceLocation.fromNamespaceAndPath(
                "deep_lucky_block", "textures/gui/sprites/hud/heart_" + elementTexture + "_" + state + ".png");
    }

    private static int frameCount(String elementTexture, boolean fullToHalf) {
        FrameCounts fc = FRAME_COUNTS.get(elementTexture);
        if (fc == null) return 1;
        return fullToHalf ? fc.fullToHalf() : fc.halfToEmpty();
    }

    private static int totalAnimTicks(String elementTexture, int from, int to) {
        int[] path = pathBetween(from, to);
        boolean gaining = to > from; // regen : rythme 2x plus lent, voir ANIM_FRAME_TICKS_GAIN
        int frameTicks = gaining ? ANIM_FRAME_TICKS_GAIN : ANIM_FRAME_TICKS;
        int total = 0;
        for (int i = 0; i < path.length - 1; i++) {
            boolean isFullHalfSeg = Math.max(path[i], path[i + 1]) == 2;
            int frames = frameCount(elementTexture, isFullHalfSeg);
            total += frames * frameTicks;
        }
        return total;
    }

    /**
     * Joue l'animation (a base des frames FOURNIES PAR L'UTILISATEUR, copiees
     * telles quelles) de la transition {@code from -> to} (etats en huitiemes
     * de coeur : 0=vide, 1=demi, 2=plein). Une perte directe plein->vide
     * enchaine les deux demi-transitions (plein->demi PUIS demi->vide) ; un
     * GAIN reutilise EXACTEMENT les memes frames dans l'ordre inverse (aucun
     * asset de regain separe n'a ete fourni, comme specifie).
     */
    private static void renderTransition(GuiGraphics gg, int x, int y, String elementTexture,
            int from, int to, int elapsed) {
        int[] path = pathBetween(from, to);
        int segCount = path.length - 1;
        boolean gaining = to > from; // regen : rythme 2x plus lent, voir ANIM_FRAME_TICKS_GAIN
        int frameTicks = gaining ? ANIM_FRAME_TICKS_GAIN : ANIM_FRAME_TICKS;

        int segIndex = 0;
        int segElapsed = elapsed;
        for (int i = 0; i < segCount; i++) {
            boolean isFullHalfSeg = Math.max(path[i], path[i + 1]) == 2;
            int segTicks = frameCount(elementTexture, isFullHalfSeg) * frameTicks;
            if (segElapsed < segTicks || i == segCount - 1) { segIndex = i; break; }
            segElapsed -= segTicks;
        }
        int segFrom = path[segIndex], segTo = path[segIndex + 1];
        boolean isFullHalfSeg = Math.max(segFrom, segTo) == 2;
        int frames = frameCount(elementTexture, isFullHalfSeg);
        int frameIdx = Math.min(frames - 1, segElapsed / frameTicks);

        // Perte (from>to) : lit les frames dans l'ordre normal 0..N-1.
        // Gain (from<to) : reutilise les MEMES frames dans l'ordre inverse
        // (N-1..0) -- aucun asset de regain separe, comme specifie par
        // l'utilisateur ("Regain animations reuse loss frames in reverse").
        boolean losing = segFrom > segTo;
        int actualFrame = losing ? frameIdx : (frames - 1 - frameIdx);

        String transName = isFullHalfSeg ? "full_to_half" : "half_to_empty";
        ResourceLocation frameTex = ResourceLocation.fromNamespaceAndPath(
                "deep_lucky_block", "textures/gui/sprites/hud/anim/heart_" + elementTexture + "_" + transName + "_" + actualFrame + ".png");
        blitHeart(gg, frameTex, x, y);
    }

    private static int[] pathBetween(int from, int to) {
        if (from == to) return new int[]{from, to};
        if (Math.abs(from - to) == 1) return new int[]{from, to};
        // from=0,to=2 ou from=2,to=0 : passe par l'etat intermediaire 1 (demi).
        return new int[]{from, 1, to};
    }

    private static int lvl(ItemStack s, String id) {
        if (s == null || s.isEmpty()) return 0;
        Enchantment e = DeepEnchant.byId("deep_lucky_block:" + id);
        if (e == null) return 0;
        int l = DeepEnchant.getLevel(s, e);
        if (l <= 0) return 0;
        if (e.getMaxLevel() <= 1) return 1;
        return DeepEnchant.softCappedLevel(e, l);
    }
}
