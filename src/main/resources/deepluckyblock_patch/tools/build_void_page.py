#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Void_Five_Signatures_preview.html.

Meme GABARIT que les pages Gaia / Poison / Feu / Terre / Vent / Eau / Foudre / Ombre /
Sang : meme squelette, meme CSS, simplement reteinte. Les deux morceaux partages vivent
dans le depot :

  tools/preview_base.css   -- le CSS du gabarit
  tools/preview_render.js  -- le moteur de rendu cable sur les identifiants du gabarit

Le moteur de rendu est generique SAUF son en-tete de donnees (SUBS / DUR / NAMES / BEATS /
SFX / GP) et l'appel a buildThunder. Le Vide a CINQ signatures depuis la T389, comme le
Sang : les tableaux du gabarit retrouvent leur taille d'origine, mais les variantes 0 et 1
restent cablees sur les cadrages de la foudre (boltShot / hammerShot) et doivent etre
remplacees, comme pour toutes les familles precedentes.

CE QUI CHANGE PAR RAPPORT A LA PAGE DU SANG
-------------------------------------------
  * buildVoid(variant, t, salt) ne prend NI lien NI inversion : la signature a trois
    arguments, pas six. Aucune des cinq signatures du vide ne relie deux entites, donc
    la page n'a qu'UN acteur -- la victime -- et pas de porteur a -7.
  * Le sol du moteur du vide est a y = 0 et non a -0,86 : VoidSignatureVfx ne pose ni
    flaque ni menisque, il travaille autour du torse. La grille du gabarit reste donc a
    sa hauteur d'origine, et le correctif de plan de sol de la page du Sang NE DOIT PAS
    etre reporte.
  * Trois cadrages : la sphere est un objet de 3,1 blocs de demi-largeur qui GONFLE, le
    prisme un octaedre qui descend de 2 blocs, la faille un plan vertical mince qu'il
    faut regarder de FACE -- une faille vue par la tranche n'est qu'un trait.

L'archive est embarquee en base64 (bloc DLB-BUNDLE) : le bouton de telechargement
fonctionne meme dans une visionneuse integree.

Variable d'environnement SHA : viser refs/heads/<branche>, JAMAIS un SHA de commit -- la
page est generee AVANT son propre commit, donc un SHA fige pointerait dans le vide.
"""
import base64
import hashlib
import io
import os

ROOT = 'src/main/resources/deepluckyblock_patch/tools/'
ENGINE = io.open(ROOT + 'void_build.js', encoding='utf-8').read()
ENGINE = ENGINE.replace(
    "if (typeof module !== 'undefined' && module.exports) module.exports = { buildVoid };",
    '')
CSS = io.open(ROOT + 'preview_base.css', encoding='utf-8').read()
RENDER = io.open(ROOT + 'preview_render.js', encoding='utf-8').read()

# --- Remplacement de l'en-tete de donnees du moteur partage ---------------------------
MARK = "\nconst cv=document.getElementById('scene')"
assert RENDER.count(MARK) == 1, 'marqueur de decoupe introuvable dans preview_render.js'
_, TAIL = RENDER.split(MARK, 1)

HEAD = r"""/* ===== Rendu ===== */
const SUBS=['grille 9\u00d79 \u00b7 lentille \u00b7 anneau d\u2019Einstein \u00b7 disque d\u2019accr\u00e9tion','octa\u00e8dre \u00b7 sept rayons r\u00e9fract\u00e9s \u00b7 indices 1,14 \u2192 1,62','pi\u00e8ge \u00e0 loup de 8,6 blocs \u00b7 neuf morsures manqu\u00e9es \u00b7 claquement en 7 ticks','anneau au sol \xb7 cage lentill\xe9e \xb7 sept paraboles','sept stations \xb7 phyllotaxie \xb7 \xd71,35 par maillon'];
const DUR=[160,190,170,180,200], NAMES=['Sph\u00e8re de Distorsion','Prisme du Dragon','Faille du N\u00e9ant','Porte d\u2019Horizon','L\u2019Effondrement en Cha\u00eene'];
const BEATS=[
 [['0 \u2192 25','la grille t\u00e9moin sort droite : 9\u00d79, au pas de 0,78 \u2014 <b>c\u2019est le seul rep\u00e8re qui prouvera la d\u00e9formation</b>'],
  ['25 \u2192 48','<b>la lentille</b> : la grille se creuse en <code>rs\u00b2 / max(r0, 0,55)</code>, le pas se resserre l\u00e0 o\u00f9 l\u2019espace se tord'],
  ['48 \u2192 74','<b>l\u2019anneau d\u2019Einstein</b> \u00e0 1,14 rayon : trois grands cercles orthogonaux, pas des parall\u00e8les \u2014 une sph\u00e8re en latitudes est une bouillie grise'],
  ['48 \u2192 106','<b>le disque d\u2019accr\u00e9tion</b> : 30 blocs en <code>exp(\u22123,1\u00b7p)</code> tombent depuis 1,9 bloc, puis basculent en rotation diff\u00e9rentielle'],
  ['106 \u2192 130','<b>l\u2019effondrement</b> : tout rentre, et rebondit une fois \u2014 <code>sin(snap\u00b7\u03c0\u00b71,6)\u00b7(1\u2212snap)\u00b70,55</code>'],
  ['130 \u2192 160','<b>la restitution</b> : ce qui a \u00e9t\u00e9 aval\u00e9 ressort en c\u00f4ne, en rose cendr\u00e9']],
 [['0 \u2192 20','la descente en <b>trois \u00e0-coups</b> \u2014 ticks 4, 10, 16 : <b>le serveur frappe trois fois, donc l\u2019image sursaute trois fois</b>. Le recul est calcul\u00e9 <i>avant</i> tout le reste et appliqu\u00e9 \u00e0 l\u2019objet entier'],
  ['20 \u2192 62','<b>le faisceau incident est un cylindre</b> : une \u00e2me blanche et <b>sept fils parall\u00e8les</b> \u00e0 0,71 bloc de l\u2019axe. Sept fils entrent, sept faisceaux divergents sortiront \u2014 <i>c\u2019est le verbe de la signature en une image</i>'],
  ['19 \u2192 140','<b>les trajets internes</b>, visibles dans le verre : chaque rayon est <b>cass\u00e9 deux fois</b> par <code>refract()</code>, loi de Snell-Descartes compl\u00e8te, r\u00e9flexion totale comprise. Un faisceau s\u2019allume \u00e0 mesure que son tour approche'],
  ['62 \u2192 116','<b>les sept faisceaux</b>, un toutes les 9 frames \u2014 <b>l\u2019horaire exact du serveur</b>. Leur direction n\u2019est pas \u00e9crite : elle est <b>calcul\u00e9e</b>. Indice <code>n = 1,14 + k\u00b70,08</code> \u2014 <b>le violet d\u00e9vie plus que le rouge</b>, donc l\u2019\u00e9ventail s\u2019ouvre tout seul, comme un vrai prisme'],
  ['62 \u2192 116','chaque faisceau est <b>creux</b> (couronne de 5 cubes autour du vide), s\u2019arr\u00eate au sol et y laisse <b>une couronne d\u2019impact</b> \u2014 la Lueur de 120 ticks que pose le serveur'],
  ['150 \u2192 170','<b>la fracture</b> : les douze ar\u00eates se d\u00e9solidarisent et <b>quatre \u00e9clats d\u2019endstone sont arrach\u00e9s au sol</b> par <code>ballistic()</code> \u2014 chute, rebond amorti, repos'],
  ['170 \u2192 190','<b>l\u2019\u00e9clat</b> : anneau \u00e0 2,0 de rayon, exactement celui du serveur, et la colonne de L\u00e9vitation en rose invers\u00e9']],
 [['0 \u2192 2','<b>la f\u00ealure au sol</b> \u2014 un tronc de 38 points et sept branches, hach\u00e9 une fois pour toutes. <b>Elle survit \u00e0 tout</b> : quand le pi\u00e8ge s\u0027est referm\u00e9, la cicatrice reste'],
  ['2 \u2192 51','<b>le pi\u00e8ge s\u0027arme</b>. Deux m\u00e2choires de 2,05 bloc sur charni\u00e8res \u00e0 \u00b10,52, ouvertes \u00e0 <b>62\u00b0</b> en <code>1\u2212(1\u2212u)\u00b3</code> \u2014 vif puis lent, comme un ressort qu\u0027on bande. Aux deux bouts, les <b>ressorts de torsion</b> : leur pas se resserre avec la charge. L\u0027angle de fermeture n\u0027est pas \u00e9crit \u00e0 la main, il se <b>r\u00e9sout</b> \u2014 un pi\u00e8ge qui ferme \u00e0 un cheveu pr\u00e8s ne ferme pas'],
  ['2 \u2192 140','ce n\u0027est pas un disque mais une <b>DEUX FOIS QUATRE M\u00c8TRES TRENTE</b> couch\u00e9e en travers du chemin de l\u0027arme : le corridor du serveur, 9,0 de long sur \u00b11,6, <b>perpendiculaire au coup</b>. Les <b>neuf dents</b> de chaque m\u00e2choire tombent \u00e0 <b>mi-chemin</b> de celles d\u0027en face : elles s\u0027engr\u00e8nent au lieu de se cogner, et c\u0027est \u00e0 \u00e7a qu\u0027on reconna\u00eet un pi\u00e8ge \u00e0 loup'],
  ['20 \u2192 100','<b>les neuf aspirations</b>, une toutes les 10 frames : la mati\u00e8re est tir\u00e9e vers le <b>PLAN</b> et non vers le centre \u2014 <b>le serveur n\u0027applique que la composante perpendiculaire</b>, et tirer vers le centre entasserait tout le corridor en un point'],
  ['20 \u2192 100','\u00e0 chacune, une <b>morsure manqu\u00e9e</b> : le pi\u00e8ge se referme d\u0027un tiers puis se rouvre. Neuf fois il essaie, la dixi\u00e8me il mord \u2014 <b>le compte des neuf se lit sur le m\u00e9canisme</b>, pas seulement sur la barre au sol'],
  ['51 \u2192 140','<b>arm\u00e9, et il tremble</b> : 1,5\u00b0 au d\u00e9but, 5\u00b0 juste avant de mordre. C\u0027est le seul avertissement que re\u00e7oit la victime'],
  ['112 \u2192 140','<b>l\u0027autre c\u00f4t\u00e9</b> remonte en violet chorus, le seul ton froid du dossier : C\u00e9cit\u00e9 60 et Naus\u00e9e 100'],
  ['140 \u2192 147','<b>LE CLAQUEMENT</b>, sept ticks sur cent soixante-dix. L\u0027angle tombe en <code>u<sup>2,2</sup></code> et le bord dent\u00e9 laisse <b>cinq \u00e9chos</b> aux positions qu\u0027il vient de quitter : sans tra\u00een\u00e9e, l\u0027\u0153il ne voit que la position d\u0027arriv\u00e9e et le coup n\u0027existe pas. En <code>u\u00b3</code> sur cinq ticks, mesur\u00e9 au banc, la fermeture tombait <b>entre deux images</b>'],
  ['143 \u2192 165','<b>le splash de void</b> : trente-quatre gerbes balistiques, <b>chacune avec sa tra\u00een\u00e9e</b> de quatre positions sur la m\u00eame parabole \u2014 une gerbe sans tra\u00een\u00e9e est une pluie de confettis. Vite et bas aux commissures, haut et lent au centre, plus l\u0027onde plate qui fuit la ligne de morsure'],
  ['140 \u2192 170','<b>l\u0027\u00e9jection</b> : ce qui \u00e9tait dedans part <b>perpendiculairement</b>, des deux c\u00f4t\u00e9s \u00e0 la fois \u2014 exactement le <code>voidPush</code> du serveur, qui ne corrige que la composante perpendiculaire']],
 [['0 \u2192 22','<b>le trac\u00e9 au sol</b>, quatre passes de dix points \u00e0 <b>2,12 de rayon</b> \u2014 ticks 0, 5, 10, 15 : <b>le serveur dessine son cercle avant d\u2019ouvrir quoi que ce soit</b>, et l\u2019image le dessine aux m\u00eames ticks'],
  ['24 \u2192 60','<b>l\u2019anneau</b> s\u2019ouvre. Rep\u00e8re explicite recalcul\u00e9 \u00e0 chaque pas \u2014 b\u00e2tie sur les axes du monde, la section se vrille et le tore sort en ruban tordu'],
  ['28 \u2192 150','<b>la cage lentill\u00e9e</b> : seize montants dont le rayon suit <b>\u2212k/r</b>, le potentiel d\u2019un puits. Ils se resserrent en descendant vers la gueule \u2014 <b>une porte n\u2019est pas un cylindre, c\u2019est un entonnoir</b>'],
  ['34 \u2192 142','<b>les sept transits</b>, un toutes les 18 frames \u2014 l\u2019horaire du serveur. Vraies <b>paraboles balistiques</b> : 3,5 bl/s vers l\u2019avant, 4,4 bl/s vers le haut, gravit\u00e9 9,0. Azimut tournant d\u2019un <b>septi\u00e8me de tour</b> \u00e0 chaque passage \u2014 au bout des sept, le paquet est diss\u00e9min\u00e9 en \u00e9toile, exactement comme le fait <code>seedAng + pass\u00b72\u03c0/7</code>'],
  ['34 \u2192 142','chaque transit tra\u00eene <b>neuf motes</b> derri\u00e8re lui et <b>dispara\u00eet une quinzaine de frames</b> en passant : <b>c\u2019est le trou dans la trajectoire qui prouve la travers\u00e9e</b>'],
  ['150 \u2192 180','<b>la fermeture</b> : une onde balaye 3,4 blocs, l\u2019anneau se r\u00e9sorbe sur son \u00e2me et la <b>boule de section</b> reste une seconde \u2014 \u00e9jection radiale, puis Chute Lente 140']],
 [['0 \u2192 30','<b>la marque</b> : UN seul corps est d\u00e9sign\u00e9, pas une zone. Un anneau de <b>cubes d\u2019abysse</b> \u2014 la vraie texture de portail, pos\u00e9e sur des volumes et non sur une vitre \u2014 se referme sur lui : <b>le rayon de l\u2019anneau EST le temps qui reste</b>'],
  ['30','<b>la premi\u00e8re implosion</b>. La coquille tombe en <code>(1\u2212\u00e2ge)\u00b3</code> sur 26 frames, trois grands cercles claquent au centre, une onde balaye le sol et un <b>crat\u00e8re reste</b>. Le crat\u00e8re est la m\u00e9moire de la cha\u00eene : \u00e0 la fin, le sol raconte le trajet'],
  ['30 \u2192 195','<b>le circuit</b> : un fil court jusqu\u2019au corps suivant en <b>8 frames</b>, t\u00eate vive devant. Derri\u00e8re lui le fil reste et <b>pulse</b>. Les sept d\u00e9parts tombent aux ticks <b>30, 70, 105, 135, 160, 180, 195</b> \u2014 l\u2019horaire exact du serveur, dont le d\u00e9lai raccourcit de 5 frames \u00e0 chaque maillon'],
  ['30 \u2192 195','<b>les implosions grossissent</b> : rayon <code>1,40 + rang\u00b70,25</code>, et la frappe monte en <code>\u00d71,35</code> cumulatif. La septi\u00e8me est trois fois la premi\u00e8re \u2014 <b>la cha\u00eene se lit sans compteur</b>'],
  ['30 \u2192 195','<b>les stations ne se superposent jamais</b> : elles sont pos\u00e9es en <b>phyllotaxie</b> (angle d\u2019or, rayon en <code>\u221arang</code>), la m\u00eame loi que les graines d\u2019un tournesol. C\u2019est la seule disposition qui garantisse sept points lisibles sans en placer un seul \u00e0 la main'],
  ['195 \u2192 205','<b>la vidange</b> : le circuit se d\u00e9charge \u00e0 rebours, du dernier corps vers le premier, en 10 frames. C\u2019est le seul moment o\u00f9 l\u2019\u0153il remonte la cha\u00eene'],
  ['205 \u2192 200','<b>la d\u00e9tonation</b> : une sph\u00e8re de cubes d\u2019abysse de <code>2,0 + 0,30\u00b7maillons</code> blocs et seize colonnes d\u2019\u00e9jection. <b>Elle ne vient que si la cha\u00eene a tenu</b> \u2014 sur une cible isol\u00e9e, l\u2019animation s\u2019arr\u00eate au premier crat\u00e8re']]];
/* Conception sonore. Le vide n'a pas de registre dedie : il emprunte a l'End (enderman
   teleport, portal.trigger), au cristal (amethyst, beacon) et a la rupture (glass, anvil).
   Les onze sons de la famille sont attestes un par un contre la table des identifiants
   reels de 1.21.1 -- RESPAWN_ANCHOR_DEPLETE est le seul a etre un Holder, donc le seul a
   demander .value(). */
const SFX=[
 "<b>0 \u2192 18</b> l\u2019amorce \u2014 <b>cinq</b> <code>beacon.ambient</code> dont le volume monte et la hauteur avec : on pr\u00e9vient avant de tirer. <b>20</b> l\u2019amor\u00e7age \u2014 <code>enderman.teleport</code> \u00e0 <b>0,45</b> de hauteur, c\u2019est-\u00e0-dire tr\u00e8s grave : la m\u00eame banque que l\u2019End, mais descendue d\u2019une octave. <b>22 \u2192 52</b> les six tractions \u2014 <code>amethyst_block.chime</code> dont la hauteur monte de 0,70 \u00e0 1,15 : l\u2019oreille entend le puits se refermer. <b>112</b> l\u2019effondrement \u2014 <code>enderman.teleport</code> \u00e0 <b>0,38</b> + <code>beacon.deactivate</code> dans la m\u00eame frame. <b>128</b> la restitution \u2014 <code>respawn_anchor.deplete</code>.",
 "<b>0 \u2192 20</b> la descente \u2014 trois <code>amethyst_block.place</code> dont la hauteur monte par paliers : l\u2019objet se <i>pose</i>, il ne tombe pas. <b>42</b> le trait \u2014 <code>beacon.power_select</code> aigu. <b>62 \u2192 125</b> <b>les sept faisceaux</b> \u2014 une <code>amethyst_block.chime</code> par faisceau, <b>\u00e0 la position du corps touch\u00e9</b> et non au centre : en foule, les sept sons viennent de sept endroits diff\u00e9rents. <b>150</b> la fracture \u2014 <code>amethyst_block.break</code>. <b>170</b> l\u2019\u00e9clat \u2014 <code>respawn_anchor.deplete</code>.",
 "<b>2</b> la f\u00ealure \u2014 <code>glass.break</code> \u00e0 <b>1,80</b> de hauteur et <b>0,35</b> de volume : tr\u00e8s aigu, tr\u00e8s faible. C\u2019est le son le plus discret de la famille, et c\u2019est voulu \u2014 une d\u00e9chirure commence par un craquement, pas par un coup. <b>14</b> l\u2019ouverture \u2014 <code>enderman.teleport</code>. <b>20 \u2192 100</b> les neuf aspirations \u2014 <code>amethyst_block.chime</code> grave et r\u00e9gulier. <b>112</b> l\u2019autre c\u00f4t\u00e9 \u2014 <code>portal.trigger</code>. <b>140</b> la fermeture \u2014 <code>anvil.land</code> aigu + <code>enderman.teleport</code> : du m\u00e9tal, puis le vide.",
 "<b>0 \u2192 15</b> le trac\xe9 \u2014 <b>quatre</b> <code>beacon.power_select</code> dont le volume et la hauteur montent ensemble : la porte s\u2019annonce avant d\u2019exister. <b>24</b> l\u2019ouverture \u2014 <code>respawn_anchor.deplete</code>, <b>le seul son Holder de la famille</b>, donc le seul \xe0 demander <code>.value()</code>, \xe0 0,55 de hauteur. <b>34 \u2192 142</b> les sept transits \u2014 un <code>enderman.teleport</code> par passage, hauteur de 0,70 \xe0 1,12 : on entend le tourniquet acc\xe9l\xe9rer. <b>150</b> la fermeture \u2014 <code>beacon.deactivate</code> grave <b>et</b> <code>anvil.land</code> aigu dans la m\xeame frame : le vide, puis le m\xe9tal.",
 "<b>Le compte \u00e0 rebours, et il ACC\u00c9L\u00c8RE.</b> Chaque corps marqu\u00e9 re\u00e7oit <b>trois</b> <code>amethyst_block.chime</code> avant d\u2019imploser, \u00e0 la hauteur <code>0,62 + rang\u00b70,20 + profondeur\u00b70,05</code> : le tic monte d\u2019un cran \u00e0 chaque maillon, et comme le d\u00e9lai se raccourcit de cinq frames \u00e0 chaque fois, <b>le rythme se serre en m\u00eame temps que la hauteur monte</b>. C\u2019est le seul signal dont le joueur ait besoin : il entend la cha\u00eene prendre, et il entend si elle meurt. <b>\u00c0 chaque implosion</b> un <code>amethyst_block.break</code>. <b>\u00c0 la d\u00e9tonation</b> \u2014 et seulement si la cha\u00eene a tenu \u2014 <code>respawn_anchor.deplete</code>, le seul Holder de la famille et donc le seul \u00e0 r\u00e9clamer <code>.value()</code>, doubl\u00e9 d\u2019un <code>anvil.land</code> grave."];
const GP=[
 "<b>Elle RASSEMBLE.</b> Son int\u00e9r\u00eat n\u2019est pas de frapper fort, c\u2019est de mettre six ennemis au m\u00eame endroit \u2014 apr\u00e8s quoi n\u2019importe quelle autre arme les prend tous. <b>Foule :</b> six tractions de 5 ticks dans un rayon de 5,0, <b>vitesse proportionnelle \u00e0 la distance</b> (born\u00e9e \u00e0 0,78) \u2014 une vitesse constante ferait osciller les proches autour du point comme un pendule. Lenteur II tout du long. <b>Duel :</b> il n\u2019y a personne \u00e0 rassembler, alors elle change de m\u00e9tier \u2014 elle s\u2019accroche \u00e0 l\u2019unique cible, la <b>soul\u00e8ve</b> (L\u00e9vitation), et chaque traction tape <b>2,45 fois plus fort</b>. <b>Timer.</b> Effondrement au tick 112, restitution au 128 : celle-l\u00e0 <b>repousse</b> tout ce qui avait \u00e9t\u00e9 tir\u00e9. <b>Buff :</b> Chute Lente 100 ticks au porteur.",
 "<b>Il DISTRIBUE.</b> Sept faisceaux, sept corps, un chacun. <b>Foule :</b> jusqu\u2019\u00e0 sept victimes distinctes dans 6,5, tir\u00e9es en s\u00e9quence toutes les 9 frames ; chacune re\u00e7oit une empreinte <b>et la Lueur</b> \u2014 le seul debuff franchement utile de la famille, puisqu\u2019il rend visibles \u00e0 travers les murs ceux qui sont marqu\u00e9s. <b>Duel :</b> sous trois corps, les sept faisceaux <b>CONVERGENT</b> sur le m\u00eame ; chaque trait tape moins, mais sept traits posent quatre empreintes \u2014 <b>la d\u00e9tonation est garantie</b>. <b>Timer.</b> Fracture au tick 150 : Faiblesse II, 120 ticks, sur toute la zone. \u00c9clat au 170 : L\u00e9vitation. <b>Buff :</b> Vitesse 80 ticks au porteur \u00e0 la fracture.",
 "<b>Elle TRIE.</b> Son aire n\u2019est pas un disque mais un <b>CORRIDOR de 9 blocs de long sur 3,2 de large</b>, perpendiculaire \u00e0 l\u2019axe du coup \u2014 la d\u00e9chirure est un plan, sa trace au sol est une ligne. Un disque aurait touch\u00e9 ce qui se trouve devant et derri\u00e8re la faille, c\u2019est-\u00e0-dire ce qui n\u2019en est s\u00e9par\u00e9 par rien. <b>C\u2019est la seule aire du mod o\u00f9 le placement lat\u00e9ral d\u00e9cide.</b> <b>Foule :</b> neuf aspirations qui tirent vers l\u2019<b>axe</b> et non vers le centre (seule la composante perpendiculaire est appliqu\u00e9e : tirer vers le centre entasserait tout le corridor en un point). <b>Duel :</b> la victime unique est <b>RETENUE 70 ticks</b> \u2014 elle ne peut pas sortir du corridor \u2014 et chaque aspiration tape <b>1,73 fois plus fort</b>. <b>Timer.</b> Tick 112 : C\u00e9cit\u00e9 60 + Naus\u00e9e 100. Tick 140 : fermeture, \u00e9jection <b>perpendiculaire</b>, deux empreintes. <b>Buff :</b> Chute Lente 120 ticks au porteur.",
 "<b>Elle EXPULSE.</b> C\u2019est l\u2019<b>exact inverse de la Sph\xe8re</b>, et c\u2019est la raison de la garder : deux outils oppos\xe9s sur la m\xeame famille donnent au joueur une d\xe9cision \xe0 prendre. <b>Foule :</b> sept transits toutes les 18 frames ; chacun prend le corps le plus proche de l\u2019<b>anneau</b> (pas du centre \u2014 la porte avale ce qui est tomb\xe9 dedans, pas ce qui passait au large) et l\u2019exp\xe9die sur un azimut qui <b>tourne d\u2019un septi\xe8me de tour</b> : au bout des sept, le paquet est diss\xe9min\xe9 en \xe9toile. <b>Duel :</b> rien \xe0 diss\xe9miner, alors la porte devient un <b>MOULIN</b> \u2014 l\u2019azimut fait un <b>demi-tour</b> \xe0 chaque passage, la victime est chass\xe9e d\u2019un c\xf4t\xe9 puis de l\u2019autre, et chaque transit tape <b>deux fois plus fort</b> (0,92 contre 0,45). Elle ne va nulle part, mais elle n\u2019arr\xeate jamais de tomber. <b>Timer.</b> Fermeture au tick 150 : <code>base\xb71,25</code>, deux empreintes, \xe9jection radiale. <b>Buff :</b> Chute Lente 140 ticks au porteur.",
 "<b>Elle PROPAGE \u2014 et c\u2019est la seule signature du mod dont la puissance d\u00e9pende de la DISPOSITION des ennemis.</b> Un seul corps est marqu\u00e9 au d\u00e9part. Deux secondes plus tard il implose, et l\u2019implosion marque <b>le corps le plus proche encore vivant</b>, qui implose \u00e0 son tour. <b>Chaque maillon frappe \u00d71,35 du pr\u00e9c\u00e9dent</b> (<code>base\u00b70,70\u00b71,35^profondeur</code>) et le d\u00e9lai tombe de 40 \u00e0 14 frames : la cha\u00eene s\u2019emballe. <b>Foule serr\u00e9e :</b> six corps relay\u00e9s, un septi\u00e8me saut, puis une <b>d\u00e9tonation</b> de <code>base\u00b70,90\u00b7(maillons+1)</code> sur <code>2,0 + 0,30\u00b7cha\u00eene</code> blocs \u2014 de loin le plus gros chiffre de la famille. <b>Cible isol\u00e9e :</b> aucun relais \u00e0 5,5 blocs, la cha\u00eene meurt au premier maillon ; repli \u00e0 <code>base\u00b70,87</code>, <b>deux maillons au maximum</b>, et la signature est faible. <b>C\u2019est voulu</b> : les quatre autres se moquent de l\u2019espacement, celle-ci r\u00e9compense le joueur qui a d\u2019abord group\u00e9 ses cibles \u2014 avec la Sph\u00e8re de Distorsion, par exemple. <b>Timer.</b> \u00c0 partir du troisi\u00e8me maillon, Vitesse 60 ticks au porteur : la cha\u00eene le r\u00e9compense pendant qu\u2019elle court. \u00c0 la d\u00e9tonation, Lenteur II 60 ticks sur tout ce qui reste debout. <b>Plafond :</b> huit maillons, et un corps d\u00e9j\u00e0 br\u00fbl\u00e9 ne peut pas \u00eatre repris \u2014 sans quoi deux corps c\u00f4te \u00e0 c\u00f4te se renverraient la cha\u00eene ind\u00e9finiment.",];
/* L'EMPREINTE DU NEANT, ressource partagee par les trois. Quatre au maximum. Elle ne fait
   AUCUN degat par elle-meme : c'est un SEUIL. A la quatrieme, l'espace cede -- implosion,
   Levitation, Lenteur II, et un echo banque au porteur. Au 3e et au 7e echo, le porteur
   gagne Vitesse et Chute Lente -- JAMAIS de Force : la ferveur du Sang recompense deja
   l'acharnement par des degats, et le Vide paie en terrain. Les echos se periment en 200
   ticks : c'est l'enchainement qui paie, pas l'accumulation. */
"""

# PIEGE DU GABARIT PARTAGE, vecu en redescendant de 5 a 3 (des entrees vides etaient
# restees dans GP) puis en remontant de 3 a 5 : les six tables sont indexees par le numero
# de variante, et une seule qui serait trop courte donne un `undefined` AFFICHE dans le
# panneau, sans la moindre erreur JS. On compte, et on refuse de livrer sinon.
VARIANTS = 5


def _count_entries(src, name):
    """Entrees de premier niveau du litteral `name=[...]` : chaines et crochets imbriques
       compris, et une virgule finale ne compte pas (`[a,b,]` a DEUX entrees)."""
    import re
    backslash = chr(92)
    m = re.search(r'\b%s=\[' % name, src)
    assert m, 'table %s introuvable dans HEAD' % name
    i, depth, n, quote, last = m.end(), 1, 1, None, None
    while depth:
        c = src[i]
        if quote:
            if c == backslash:
                i += 1
            elif c == quote:
                quote = None
        elif c == '"' or c == "'":
            quote = c
        elif c == '[':
            depth += 1
        elif c == ']':
            depth -= 1
            if not depth and last == ',':
                n -= 1
        elif c == ',' and depth == 1:
            n += 1
        if not c.isspace():
            last = c
        i += 1
    return n


for _name in ('SUBS', 'DUR', 'NAMES', 'BEATS', 'SFX', 'GP'):
    _n = _count_entries(HEAD, _name)
    assert _n == VARIANTS, 'table %s : %d entrees pour %d variantes' % (_name, _n, VARIANTS)
print('  tables de donnees : 6 x %d entrees' % VARIANTS)

RENDER = HEAD + MARK + TAIL
assert RENDER.count('buildThunder') == 1, 'appel buildThunder introuvable'
RENDER = RENDER.replace('buildThunder', 'buildVoid')

# --- LE PIEGE DU GABARIT PARTAGE. render() appelle le constructeur avec les angles de
#     camera en 4e et 5e argument (buildThunder(variant,t,salt,camYaw,camPitch)) : la foudre
#     s'en sert pour ses panneaux-reclame. buildVoid n'a que TROIS parametres -- aucune des
#     cinq signatures du vide ne relie deux entites, donc il n'y a ni longueur de lien ni
#     inversion a passer. Les deux arguments en trop sont coupes ici ; les laisser serait
#     sans effet en JS, mais le jour ou buildVoid gagnera un 4e parametre, la page lui
#     passerait silencieusement un angle de camera. C'est exactement le defaut qui a replie
#     le siphon du Sang sur sa victime.
CALL_OLD = 'buildVoid(variant,t,salt,camYaw,camPitch)'
CALL_NEW = 'buildVoid(variant,t,salt)'
assert RENDER.count(CALL_OLD) == 1, 'appel du constructeur introuvable'
RENDER = RENDER.replace(CALL_OLD, CALL_NEW)

# --- La grille temoin du GABARIT (le quadrillage de decor) n'a rien a voir avec la grille
#     temoin de la SPHERE (qui fait partie de la signature). Celle du gabarit sert de plan
#     de reference : a 6,2 blocs elle est plus etroite que le disque d'accretion, qui monte
#     a 3,4 de demi-largeur mais que la perspective etale. 7,6 suffit pour les trois.
GR_OLD = ' const R=6.2,N=12;'
GR_NEW = ' const R=8.6,N=16;'
assert RENDER.count(GR_OLD) == 1, 'rayon de grille introuvable'
RENDER = RENDER.replace(GR_OLD, GR_NEW)

# --- LE SOL. T391 : c'etait le defaut le plus grossier de la page, et il tenait a une
#     ligne. Le gabarit partage dessine son quadrillage a y = -0,47 ; VoidSignatureVfx,
#     lui, pose tout ce qui touche terre sur GROUND_Y, dont le repli vaut -0,90 parce que
#     l'origine d'une signature est le TORSE de la victime (bbHeight x 0,50 cote serveur)
#     et qu'un corps d'1,80 a les pieds 0,90 plus bas. Resultat : la mare de la Porte, les
#     couronnes d'impact du Prisme et les marques de la Chaine etaient peintes QUARANTE-TROIS
#     CENTIMETRES SOUS LE SOL DESSINE. Il n'y a qu'un seul plancher dans une scene : le
#     quadrillage et les pieds du mannequin descendent donc sur celui de la figure, et c'est
#     la constante partagee qui le donne -- jamais un nombre recopie.
FLOOR_OLD = """  let p=project(u,-0.47,-R),q=project(u,-0.47,R);"""
FLOOR_NEW = """  let p=project(u,GFLOOR,-R),q=project(u,GFLOOR,R);"""
assert RENDER.count(FLOOR_OLD) == 1, 'premiere passe du quadrillage introuvable'
RENDER = RENDER.replace(FLOOR_OLD, FLOOR_NEW)
FLOOR_OLD = """  p=project(-R,-0.47,u);q=project(R,-0.47,u);"""
FLOOR_NEW = """  p=project(-R,GFLOOR,u);q=project(R,GFLOOR,u);"""
assert RENDER.count(FLOOR_OLD) == 1, 'seconde passe du quadrillage introuvable'
RENDER = RENDER.replace(FLOOR_OLD, FLOOR_NEW)
RENDER = RENDER.replace('function ground(){',
                        'const GFLOOR=VOID_SHAPE.GROUND_FALLBACK;\nfunction ground(){', 1)

# --- Cadrage. Le gabarit partage cable la variante 0 sur boltShot (la chute de l'eclair)
#     et la variante 1 sur hammerShot (la chute du marteau) : herite tel quel, il filmerait
#     le vide avec la camera de la foudre. Les trois echelles n'ont rien de commun -- une
#     sphere qui GONFLE de 0,05 a 1,10 de rayon en entrainant une grille de 3,12 de
#     demi-largeur, un octaedre qui DESCEND de deux blocs puis deploie sept faisceaux de
#     3,5, et un plan vertical mince de 4,4 de haut.
CAM_OLD = """function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 let span;
 if(variant===1){const s=hammerShot(t);span=s.span;VCY=s.center;}
 else if(variant===0){const s=boltShot(t);span=s.span;VCY=s.center;}
 else{span=23.0;VCY=VIEW_CENTER_BY[variant];}
 SCALE=Math.min(W/span,H/(span*0.80))*zoom;}"""
CAM_NEW = """/* boltShot et hammerShot ci-dessus appartiennent au gabarit partage (foudre) et ne
   servent pas ici : le vide a son propre cadrage, et il lui en faut CINQ. T391 : les
   quatre dernieres signatures ont ete refaites de zero, et leurs emprises n'ont plus
   rien a voir avec celles de T390. */
function voidShot(tt){
 /* LA SPHERE. La grille temoin fait 3,12 blocs de demi-largeur, mais elle se CREUSE :
    cadrer sur son etendue au repos couperait le disque d'accretion des que celui-ci
    bascule. 9,0 tient l'ensemble, et la vue se resserre a l'effondrement. */
 if(variant===0){const k=smoothstep((tt-0.66)/0.12);
  return {span:9.0-1.3*k, center:0.95, pan:0};}
 /* LE PRISME. Sept faisceaux refractes de 5,6 blocs de portee, tires dans sept
    directions calculees : l'emprise est une SPHERE de 5,6 de rayon autour du cristal, et
    non plus la paire d'ailes de T390. Le centre descend avec le cristal pendant les
    vingt premiers ticks. */
 if(variant===1){const d=smoothstep(tt/0.11);
  return {span:9.8, center:1.00-0.32*d, pan:0};}
 /* LA FAILLE. Ce n'est plus une amande au-dessus de la victime mais une DECHIRURE DE
    8,6 BLOCS couchee en travers du chemin de l'arme -- le corridor du serveur. Il faut
    donc un cadre large et bas, et la camera la prend de trois quarts (cf. YAW_BY) pour
    montrer a la fois sa longueur et ses deux levres. */
 if(variant===2){return {span:11.4, center:0.45, pan:0};}
 /* LA PORTE. Large et BASSE : l'anneau fait 4,24 de diametre au sol, les sept transits
    retombent a 3,5 du centre et l'onde de fermeture balaye 3,4. Un trou ne se voit qu'en
    plongee : le cadrage est tenu haut (cf. PITCH_BY). */
 if(variant===3){const k=smoothstep((tt-0.84)/0.16);
  return {span:10.2-1.0*k, center:0.10, pan:0};}
 /* LA TRAME. Sept noeuds simules entre 2,2 et 3,5 de rayon au depart, tires vers le
    barycentre par onze tensions. Le cadre doit tenir la boite AU DEBUT, quand elle est la
    plus large, et se resserrer a la convergence. */
 const c=smoothstep((tt-0.74)/0.20);
 return {span:9.8-2.0*c, center:0.30-0.10*c, pan:0};}
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 const s=voidShot(t); VCY=s.center; VCX=s.pan||0;
 SCALE=Math.min(W/s.span,H/(s.span*0.80))*zoom;}"""
assert RENDER.count(CAM_OLD) == 1, 'bloc updateCam introuvable dans preview_render.js'
RENDER = RENDER.replace(CAM_OLD, CAM_NEW)

# --- (Le plan de sol est traite plus haut : une seule constante, VOID_SHAPE.GROUND_FALLBACK,
#     pour le quadrillage, les pieds du mannequin et tout ce que les figures posent par
#     terre. T391 : les quatre signatures refaites touchent le sol en permanence --
#     couronnes d'impact, marques de noeud, onde de fermeture, eclats qui rebondissent --
#     et deux planchers concurrents se voyaient immediatement.)

# --- RECADRAGE HORIZONTAL. Le gabarit centre toujours la vue sur x = 0 et ne reglait que
#     la hauteur (VCY). Aucune des cinq signatures du vide n'est excentree -- il n'y a pas
#     de porteur a -7 ici -- mais voidShot renvoie un champ `pan` comme les autres pages, et
#     une page qui ne saurait pas le lire casserait a la premiere retouche de cadrage.
PROJ_OLD = "function project(x,y,z){const yc=y-VCY,rx=x*CY-z*SY,rz=x*SY+z*CY,ry=yc*CP-rz*SP;"
PROJ_NEW = "function project(x,y,z){const yc=y-VCY,xc=x-VCX,rx=xc*CY-z*SY,rz=xc*SY+z*CY,ry=yc*CP-rz*SP;"
assert RENDER.count(PROJ_OLD) == 1, 'projection introuvable'
RENDER = RENDER.replace(PROJ_OLD, PROJ_NEW)
assert RENDER.count("let VCY=1.05;") == 1, 'declaration VCY introuvable'
RENDER = RENDER.replace("let VCY=1.05;", "let VCY=1.05, VCX=0;")

# --- UN SEUL ACTEUR. Le sang en dessinait deux parce que le siphon et la transfusion sont
#     des LIENS : un conduit entre deux points vides ne se lit pas. Le vide n'a pas de lien
#     -- le trait d'entree du prisme vient bien de la main du porteur, mais il s'arrete a
#     4,6 blocs, qui est une longueur FIXE et non la distance reelle entre deux corps. Une
#     seule silhouette, donc, a l'echelle : c'est elle qui donne sa taille a la sphere.
ACT_ANCHOR = "function glow(vox){ctx.save();ctx.globalCompositeOperation='lighter';"
ACT_NEW = """/* REPERE D'APERCU -- il ne fait pas partie de la signature. En jeu, cette place est
   occupee par la victime ; ici, une silhouette en fil de fer donne l'echelle. Sans elle,
   une sphere de 1,10 de rayon et une sphere de 3 blocs sont le meme dessin. */
/* T390 -- Seuil de profondeur du squelette. La fenetre d'abysse est OPAQUE et son centre
   tombe sur le torse de la victime : la moitie du corps situee derriere le disque doit
   donc disparaitre, exactement comme en jeu. Le squelette est peint deux fois, avant la
   fenetre (tout) puis apres (FIG_NEAR = profondeur du disque, donc la moitie avant
   seulement). Une passe unique montrerait un mannequin soit intact devant un trou, soit
   entierement avale : ni l'un ni l'autre n'est ce que le joueur verra. */
let FIG_NEAR=-1e9;
function wireBox(x,y,z,hx,hy,hz){
 const c=[];
 for(let i=0;i<8;i++)c.push(project(x+((i&1)?hx:-hx),y+((i&2)?hy:-hy),z+((i&4)?hz:-hz)));
 const E=[[0,1],[2,3],[4,5],[6,7],[0,2],[1,3],[4,6],[5,7],[0,4],[1,5],[2,6],[3,7]];
 ctx.beginPath();
 for(const e of E){const a=c[e[0]],b=c[e[1]];
  if((a[2]+b[2])*0.5<FIG_NEAR)continue;
  ctx.moveTo(a[0],a[1]);ctx.lineTo(b[0],b[1]);}
 ctx.stroke();
}
function figure(x,tall,tint){
 const F=VOID_SHAPE.GROUND_FALLBACK;  /* les pieds sur le MEME plancher que les figures */
 ctx.strokeStyle=tint;ctx.lineWidth=DPR;
 const legs=tall*0.44, torso=tall*0.34, head=tall*0.22;
 /* Deux jambes, deux bras : une seule boite verticale lisait comme un lampadaire. */
 wireBox(x,F+legs/2,-0.09,0.10,legs/2,0.07);
 wireBox(x,F+legs/2, 0.09,0.10,legs/2,0.07);
 wireBox(x,F+legs+torso/2,0,0.13,torso/2,0.21);
 wireBox(x,F+legs+torso*0.62,-0.29,0.08,torso*0.42,0.08);
 wireBox(x,F+legs+torso*0.62, 0.29,0.08,torso*0.42,0.08);
 wireBox(x,F+legs+torso+head/2,0,0.14,head/2,0.14);
}
/* L'etiquette n'est pas dans la scene : c'est de l'interface. Elle passe donc en dernier,
   au-dessus de tout, y compris de la fenetre -- sinon le disque l'avalerait, et on
   perdrait le seul reperage de l'acteur. */
function actorLabels(){
 const p=project(0,VOID_SHAPE.GROUND_FALLBACK+1.80+0.24,0);
 ctx.fillStyle='#2f5a6b';ctx.font=(11*DPR)+'px ui-monospace,Menlo,monospace';
 ctx.textAlign='center';ctx.fillText('VICTIME',p[0],p[1]);
}
/* 1,80 et pas 1,95 : c'est la taille d'un joueur, et c'est elle qui justifie le repli de
   sol a -0,90 -- l'origine de la signature est le torse, soit la moitie du corps. */
function actors(){ figure(0,1.80,'#2f5a6b'); }
function glow(vox){ctx.save();ctx.globalCompositeOperation='lighter';"""
assert RENDER.count(ACT_ANCHOR) == 1, 'ancre glow introuvable'
RENDER = RENDER.replace(ACT_ANCHOR, ACT_NEW)
assert RENDER.count(" ground();\n") == 1, 'appel ground() introuvable'
RENDER = RENDER.replace(" ground();\n", " ground();\n actors();\n")

# --- UN ANGLE PAR VARIANTE. Regle acquise au Sang : une courbe ne se lit que de profil,
#     et une vue axiale ne prouve rien. Ici elle mord deux fois.
#
#     LA SPHERE a besoin d'un angle FRANC (0,62 / 0,30) : sa grille temoin est un plan
#     horizontal, et vue de face elle se reduit a une ligne -- toute la deformation,
#     c'est-a-dire tout le sujet, disparait.
#
#     LA FAILLE demande l'inverse, ET le piege s'est referme une premiere fois. Elle
#     s'ouvre dans la direction LATERALE : box(..., f = wander, lateral = lL, y = yy), donc
#     son plan est (lateral, y) et sa normale est l'axe f. Or l'apercu fixe f = x. A yaw
#     proche de ZERO la camera regarde le long de z, c'est-a-dire DANS le plan de la
#     faille : premiere capture, un trait vertical de 476 cubes ou ni l'amande, ni les deux
#     levres, ni les trois fenetres ne se distinguaient. Il faut regarder le long de f,
#     donc yaw proche de pi/2. On s'arrete a 1,30 (75 degres) et non a 1,571 : de face
#     exactement, la faille n'a plus aucune epaisseur et redevient un dessin plat.
YAW_OLD = """document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;syncPanel();});"""
YAW_NEW = """const YAW_BY=[0.62,0.52,1.02,0.82,0.78], PITCH_BY=[0.24,0.22,0.26,0.40,0.30];
document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;
 camYaw=YAW_BY[variant];camPitch=PITCH_BY[variant];zoom=1;
 syncPanel();});"""
assert RENDER.count(YAW_OLD) == 1, 'gestionnaire de variante introuvable'
RENDER = RENDER.replace(YAW_OLD, YAW_NEW)

# --- T390b -- LA FENETRE D'ABYSSE, DANS LA PAGE ET POUR DE VRAI ---------------------
#
#     L'apercu peignait les cinq signatures et DECRIVAIT le core shader en prose. Une page
#     dont le role est de juger une forme ne peut pas se permettre de raconter la moitie
#     qui manque : le bord voxel et la fenetre se jugent ENSEMBLE, puisque tout l'interet
#     du bord est ce qu'il encadre. Et la premiere chose que la page a montree, une fois le
#     shader branche, c'est que les fenetres DEBORDAIENT des figures.
#
#     On execute donc le vrai shader. Le .vsh et le .fsh sont lus dans assets/ au moment de
#     la construction et portes en GLSL ES 3.00 par les MEMES deux substitutions mecaniques
#     que la page de demonstration -- la fonction est importee de build_void_shader_demo,
#     pas recopiee : une palette qui vit en trois exemplaires a deja coute assez cher.
#
#     LE POINT DELICAT, ET POURQUOI C'EST FIDELE. En jeu, les coordonnees de texture
#     viennent de gl_Position, c'est-a-dire de l'ECRAN. Ici le contour est projete par la
#     camera de la page puis envoye en coordonnees d'horloge (matrices identite) : la
#     relation « ou est le pixel -> quelle etoile » est donc exactement celle du jeu. Le
#     facteur w (la distance, en jeu) se simplifie dans textureProj et la composante z
#     n'intervient dans aucune matrice de couche. Ce que la page affiche n'est pas une
#     imitation du shader : c'est le shader.
import sys as _sys
_here = os.path.dirname(os.path.abspath(__file__))
if _here not in _sys.path:
    _sys.path.insert(0, _here)
try:
    import build_void_shader_demo as _demo
except ImportError as _err:        # pragma: no cover
    raise SystemExit('build_void_shader_demo.py introuvable a cote de ce script : %s' % _err)

_SH = 'src/main/resources/assets/deep_lucky_block/shaders/core/'
_TX = 'src/main/resources/assets/deep_lucky_block/textures/effect/'
for _p in (_SH + 'void_portal.vsh', _SH + 'void_portal.fsh',
           _TX + 'void_sky.png', _TX + 'void_stars.png'):
    assert os.path.exists(_p), 'asset du shader absent : ' + _p

_VSH = _demo.escape_backtick(_demo.to_es300(io.open(_SH + 'void_portal.vsh', encoding='utf-8').read(), True))
_FSH = _demo.escape_backtick(_demo.to_es300(io.open(_SH + 'void_portal.fsh', encoding='utf-8').read(), False))

# Le miroir JS doit exposer les cotes partagees, sinon la fenetre retomberait sur des
# constantes a elle -- exactement le defaut que cette passe corrige.
assert 'VOID_SHAPE' in ENGINE, 'void_build.js n\'expose pas VOID_SHAPE'

PORTAL_JS = r"""
/* ===== LA FENETRE D'ABYSSE =========================================================
   Le vrai void_portal.vsh / void_portal.fsh, compiles ici. La surface est dessinee dans
   un canevas WebGL hors ecran puis COMPOSEE dans l'ordre du peintre du canevas 2D, a la
   profondeur de son centre : ce qui est derriere disparait, ce qui est devant reste. Un
   simple calque empile en CSS aurait mis la fenetre soit devant tout, soit derriere tout.

   MIROIR DE VoidPortalFx.queueSignature. Trois signatures en ont une, pas cinq, et
   aucune n'a de rayon a elle : tout vient de VOID_SHAPE, c'est-a-dire des cotes de la
   figure. Un contour qui depasse la levre cesse d'etre un trou et redevient un objet.

   Si WebGL manque, ready() rend faux et la page se comporte exactement comme avant : on
   ne perd que la fenetre, jamais la choregraphie. */
const PORTAL=(function(){
 const VSH=`__VSH__`;
 const FSH=`__FSH__`;
 const SKY="__SKY__", STARS="__STARS__";
 const SECTORS=22, LAYERS=15;
 const cn=document.createElement('canvas');
 let gl=null,pg=null,vbo=null,t0=null,t1=null,ok=false,why='';
 try{gl=cn.getContext('webgl2',{alpha:true,premultipliedAlpha:false,
  preserveDrawingBuffer:true,antialias:true});}catch(e){why=e.message;}
 function sh(ty,src){const o=gl.createShader(ty);gl.shaderSource(o,src);gl.compileShader(o);
  if(!gl.getShaderParameter(o,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(o));
  return o;}
 function tex(src){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,
   new Uint8Array([5,13,37,255]));
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.REPEAT);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.REPEAT);
  const im=new Image();im.onload=function(){gl.bindTexture(gl.TEXTURE_2D,t);
   gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,im);};
  im.src=src;return t;}
 if(gl){try{
  pg=gl.createProgram();gl.attachShader(pg,sh(gl.VERTEX_SHADER,VSH));
  gl.attachShader(pg,sh(gl.FRAGMENT_SHADER,FSH));gl.linkProgram(pg);
  if(!gl.getProgramParameter(pg,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(pg));
  vbo=gl.createBuffer();t0=tex(SKY);t1=tex(STARS);ok=true;
 }catch(e){why=e.message;ok=false;console.warn('fenetre indisponible :',why);}}
 const ID=new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]);
 return{
  ready:function(){return ok;},
  why:function(){return why;},
  canvas:function(){return cn;},
  sectors:SECTORS,
  /* Miroir exact de VoidPortalFx.queueSignature : memes plans, memes marges, memes
     seuils. T391 : les CINQ signatures en ont une, et il a fallu un quatrieme plan.
       mode 0 = panneau-reclame (la Sphere)
       mode 1 = au sol          (la Porte)
       mode 2 = plan des levres (la Faille)
     T392 : le Prisme et la Chaine n'ont PLUS de fenetre. Coller une ouverture a chaque
     animation faisait cinq fois la meme image — le retour utilisateur etait explicite.
     Ces deux-la portent la texture du portail sur des VOLUMES (voir portalCubes), ce qui
     laisse trois fenetres pour les trois figures qui ont reellement un interieur. */
  windowOf:function(variant,t){
   if(t<=0||t>=1)return null;
   if(variant===0){
    const r=VOID_SHAPE.warpHorizon(t)*0.94;
    return r<0.10?null:{mode:0,y:VOID_SHAPE.WARP_YC,a:r,b:r,label:'sph\u00e8re'};}
   if(variant===1)return null;            /* le Prisme : des cubes, pas une vitre */
   if(variant===2){
    /* La fenetre epouse la GUEULE : elle se dresse quand le piege se referme et
       disparait avec lui. Calee sur riftCrown elle gardait la meme taille pendant
       tout le claquement, et le trou cessait d'appartenir a la figure. */
    const half=VOID_SHAPE.riftHalfLength(t)*0.86;
    const h=VOID_SHAPE.riftJawPoint(1,VOID_SHAPE.riftJawAngle(t),VOID_SHAPE.RIFT_JAW)[1];
    if(half<0.06||h<0.12)return null;
    return {mode:2,y:VOID_SHAPE.GROUND_FALLBACK+0.03+h*0.52,
            a:half,b:h*0.52,label:'gueule'};}
   if(variant===3){
    const r=VOID_SHAPE.gateRing(t)*0.88;
    return r<0.12?null:{mode:1,y:VOID_SHAPE.GROUND_FALLBACK+0.06,a:r,b:r,label:'mare'};}
   return null;                           /* la Chaine : des cubes, pas une vitre */
  },
  /* pts : le contour DEJA PROJETE, en pixels ecran. On le renvoie tel quel en
     coordonnees d'horloge, donc gl_Position EST la position ecran -- comme en jeu. */
  /* UNE passe plein ecran par image. Les formes y decoupent ensuite leur part : le
     shader lisant ses UV a l'ecran, decouper apres vaut exactement tirer avant. */
  full:function(w,h,gameTime){
   return this.draw(w*0.5,h*0.5,[[0,0],[w,0],[w,h],[0,h]],w,h,gameTime);},
  draw:function(cx,cy,pts,w,h,gameTime){
   if(!ok||pts.length<3)return false;
   if(cn.width!==w||cn.height!==h){cn.width=w;cn.height=h;}
   gl.viewport(0,0,w,h);
   gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);
   gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);gl.disable(gl.CULL_FACE);
   const nx=function(x){return x/w*2-1;}, ny=function(y){return 1-y/h*2;}, v=[];
   for(let s=0;s<pts.length;s++){
    const p=pts[s], q=pts[(s+1)%pts.length];
    v.push(nx(cx),ny(cy),0, nx(p[0]),ny(p[1]),0, nx(q[0]),ny(q[1]),0);}
   gl.bindBuffer(gl.ARRAY_BUFFER,vbo);
   gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(v),gl.DYNAMIC_DRAW);
   gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,3,gl.FLOAT,false,0,0);
   gl.useProgram(pg);
   gl.uniformMatrix4fv(gl.getUniformLocation(pg,'ProjMat'),false,ID);
   gl.uniformMatrix4fv(gl.getUniformLocation(pg,'ModelViewMat'),false,ID);
   gl.uniform1f(gl.getUniformLocation(pg,'GameTime'),gameTime%1.0);
   gl.uniform1i(gl.getUniformLocation(pg,'EndPortalLayers'),LAYERS);
   gl.uniform1i(gl.getUniformLocation(pg,'Sampler0'),0);
   gl.uniform1i(gl.getUniformLocation(pg,'Sampler1'),1);
   gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,t0);
   gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,t1);
   gl.drawArrays(gl.TRIANGLES,0,v.length/3);
   return true;}
 };
})();

/* Le contour, dans le repere de l'apercu : x = f (axe de l'attaque), y = hauteur,
   z = lateral, avec dx = 1 et dz = 0 comme partout dans cette page.
     - panneau-reclame : un cercle d'ECRAN, puisque la camera de l'apercu est
       orthographique et qu'une ellipse face camera s'y projette en cercle exact ;
     - au sol        : (cos, sin) dans le plan (f, lateral), a hauteur fixe ;
     - fente         : (lateral, hauteur), f nul -- vue dans l'axe de l'attaque elle
       s'ecrase en trait, exactement comme la faille qu'elle remplit ;
     - plan libre    : les deux vecteurs de base viennent de la figure (l'equateur du
       cristal), donc la fenetre tourne et bascule avec lui sans jamais en sortir. */
/* L'ENVELOPPE D'UN CUBE A L'ECRAN. Un cube vu en projection parallele montre un
   hexagone — ou un carre s'il est vu de face. Plutot que de choisir, on projette les huit
   sommets et on prend leur enveloppe convexe : le contour est juste dans tous les cas, y
   compris quand le cube tourne sur lui-meme (spin), ce qu'il fait toujours ici. */
function cubeHull(f,lat,y,half,spin){
 const ca=Math.cos(spin), sa=Math.sin(spin), pts=[];
 let d=1e9;
 for(let i=0;i<8;i++){
  const sx=(i&1)?half:-half, sy=(i&2)?half:-half, sz=(i&4)?half:-half;
  const rx=sx*ca-sz*sa, rz=sx*sa+sz*ca;
  const p=project(f+rx, y+sy, lat+rz);
  if(p[2]<d)d=p[2];
  pts.push([p[0],p[1]]);
 }
 /* Enveloppe convexe par balayage angulaire autour du barycentre : huit points, le tri
    coute moins cher que n'importe quelle structure, et l'ordre est directement celui du
    trace. */
 let mx=0,my=0;
 for(const p of pts){mx+=p[0];my+=p[1];}
 mx/=8;my/=8;
 pts.sort(function(u,v){return Math.atan2(u[1]-my,u[0]-mx)-Math.atan2(v[1]-my,v[0]-mx);});
 return {pts:pts,d:d};
}

function portalRim(variant,t){
 const w=PORTAL.windowOf(variant,t);
 if(!w||!PORTAL.ready())return null;
 const c=project(0,w.y,0), pts=[], N=PORTAL.sectors;
 for(let s=0;s<N;s++){
  const ang=s*2*Math.PI/N, ca=Math.cos(ang), sa=Math.sin(ang);
  if(w.mode===0){pts.push([c[0]+ca*w.a*SCALE, c[1]+sa*w.a*SCALE]);continue;}
  let p;
  if(w.mode===1)      p=project(ca*w.a, w.y, sa*w.b);
  else if(w.mode===2) p=project(0, w.y+sa*w.b, ca*w.a);
  else                p=project(w.ax*ca*w.a + w.bx*sa*w.b,
                                w.y + w.ay*ca*w.a + w.by*sa*w.b,
                                w.az*ca*w.a + w.bz*sa*w.b);
  pts.push([p[0],p[1]]);
 }
 return {c:c, pts:pts, win:w};
}
"""
PORTAL_JS = (PORTAL_JS.replace('__VSH__', _VSH).replace('__FSH__', _FSH)
             .replace('__SKY__', _demo.b64(_TX + 'void_sky.png'))
             .replace('__STARS__', _demo.b64(_TX + 'void_stars.png')))

# Le module doit exister avant la premiere image. La derniere ligne du moteur partage
# amorce la boucle : on se glisse juste avant.
BOOT = 'syncPanel();requestAnimationFrame(loop);'
assert RENDER.count(BOOT) == 1, 'amorce de boucle introuvable'
RENDER = RENDER.replace(BOOT, PORTAL_JS + '\n' + BOOT)

# --- L'HORLOGE DU SHADER. GameTime vanilla avance d'un 24000e par tick ; on reproduit ce
#     rythme EXACT plutot qu'une derive confortable, sinon la page promettrait un
#     tourbillon que le jeu ne donnera pas. La preuve de la parallaxe n'est pas la vitesse
#     des etoiles : c'est qu'elles ne suivent pas la figure quand on fait tourner la vue.
CLK_OLD = 'let last=performance.now();'
CLK_NEW = 'let last=performance.now(), gameTime=0;'
assert RENDER.count(CLK_OLD) == 1, 'horloge introuvable'
RENDER = RENDER.replace(CLK_OLD, CLK_NEW)
TIC_OLD = ' if(playing){t+=dt/(DUR[variant]/20);if(t>=1)t=0;'
TIC_NEW = ' if(playing){t+=dt/(DUR[variant]/20);if(t>=1)t=0;gameTime+=dt*(20.0/24000.0);'
assert RENDER.count(TIC_OLD) == 1, 'avance du temps introuvable'
RENDER = RENDER.replace(TIC_OLD, TIC_NEW)

# --- COMPOSITION A LA BONNE PROFONDEUR. Le peintre trie ses faces par profondeur
#     croissante et les pose dans cet ordre : la fenetre entre dans ce tri comme n'importe
#     quelle face, a la profondeur de son centre. Les cubes derriere sont donc peints puis
#     RECOUVERTS -- c'est bien ce que fait une surface opaque qui ecrit la profondeur.
QD_OLD = """ const quads=[];
 ctx.lineJoin='round';"""
QD_NEW = """ /* La fenetre d'abysse. Ni rayon ni hauteur propres : tout est lu dans les cotes de
    la figure (VOID_SHAPE), comme VoidPortalFx le fait en jeu. */
 const rim=portalRim(variant,t);
 const pc=rim?rim.c:null;
 const cubes=vox.portalCubes||[];
 const quads=[];
 /* Une seule passe GL, puis chaque forme y decoupe sa part a SA profondeur. */
 const gl_ok=(rim||cubes.length)?PORTAL.full(W,H,gameTime):false;
 if(rim&&gl_ok)quads.push({d:rim.c[2],portal:true,poly:rim.pts});
 if(gl_ok)for(const c of cubes){
  const hu=cubeHull(c[0],c[1],c[2],c[3],0);
  quads.push({d:hu.d,portal:true,poly:hu.pts});
 }
 ctx.lineJoin='round';"""
assert RENDER.count(QD_OLD) == 1, 'ouverture du tableau de quads introuvable'
RENDER = RENDER.replace(QD_OLD, QD_NEW)

PT_OLD = """ for(const q of quads){
  ctx.fillStyle=q.fill;"""
PT_NEW = """ for(const q of quads){
  if(q.portal){
   ctx.save();ctx.beginPath();
   ctx.moveTo(q.poly[0][0],q.poly[0][1]);
   for(let i=1;i<q.poly.length;i++)ctx.lineTo(q.poly[i][0],q.poly[i][1]);
   ctx.closePath();ctx.clip();
   ctx.drawImage(PORTAL.canvas(),0,0);
   ctx.restore();
   continue}
  ctx.fillStyle=q.fill;"""
assert RENDER.count(PT_OLD) == 1, 'boucle de peinture introuvable'
RENDER = RENDER.replace(PT_OLD, PT_NEW)

# --- La moitie AVANT du squelette repasse par-dessus la fenetre, puis les etiquettes.
END_OLD = " document.getElementById('baseCount').textContent=vox.length;"
END_NEW = """ if(pc){FIG_NEAR=pc[2];actors();FIG_NEAR=-1e9;}
 actorLabels();
 document.getElementById('portalChip').textContent=
   !PORTAL.ready()?'abysse : WebGL absent'
   :rim?('fen\\u00eatre \\u00b7 '+rim.win.label+' '+rim.win.a.toFixed(2).replace('.',',')+' bl')
   :cubes.length?('cubes d\\u2019abysse \\u00b7 '+cubes.length)
   :(variant===1||variant===4?'cubes d\\u2019abysse \\u00b7 0':'fen\\u00eatre ferm\\u00e9e');
 document.getElementById('baseCount').textContent=vox.length;"""
assert RENDER.count(END_OLD) == 1, 'fin de render() introuvable'
RENDER = RENDER.replace(END_OLD, END_NEW)

ZIP = 'deliverables/DLB_Void_Five_Signatures_src.zip'
ZIP_NAME = os.path.basename(ZIP)
raw = io.open(ZIP, 'rb').read() if os.path.exists(ZIP) else b''
B64 = base64.b64encode(raw).decode('ascii')
SHA256 = hashlib.sha256(raw).hexdigest()
ENTRIES = 0
if raw:
    import zipfile
    with zipfile.ZipFile(ZIP) as z:
        ENTRIES = len(z.namelist())

sha = os.environ.get('SHA', 'refs/heads/arena/605e67fb-dlbv1-21-1neoforge')
URL = (f'https://github.com/Deepie1/dlbv1.21.1neoforge/raw/{sha}/'
       f'deliverables/{ZIP_NAME}')

PAGE = f"""<!DOCTYPE html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Deep Lucky Block — Vide, cinq signatures</title>
<style>
{CSS}
</style></head>
<body>
<noscript><div style="margin:12px;padding:12px;border:1px solid #6a53c0;border-radius:8px;\
background:#140f28;color:#d6c8ff;font:13px system-ui">Le JavaScript est désactivé dans ce \
lecteur : l’animation ne peut pas démarrer. Télécharge l’aperçu et ouvre-le dans un \
navigateur, ou récupère les sources via ce lien direct : <a style="color:#c9b6ff" \
href="{URL}" rel="noopener">{ZIP_NAME}</a></div></noscript>
<aside>
  <div class="eyebrow">Deep Lucky Block · Void VFX</div>
  <div class="build">T390 · 4 novembre 2026 — douzième famille · palette repeinte · \
core shader void_portal · grille témoin · tore · chaînette</div>\
  <h1>VIDE</h1><h2>Cinq signatures</h2>
  <p class="intro">Refonte complète de l’élément vide, <b>portée à cinq signatures</b> \
(T389) : le Vide tirait dans un sac trois fois plus petit que celui du Sang. Les trois \
premières passaient de \
<b>16 à 22 ticks</b> dans une routine générique — et deux d’entre elles n’avaient même pas \
leur propre durée : le Prisme partageait son <code>case</code> avec <code>BIG_BANG_SCAR</code>, \
la Faille le sien avec <code>CHAOS_KALEIDO_LOOP</code>. Elles font désormais \
<b>160 / 190 / 170</b>, et les deux nouvelles <b>180 / 200</b>, dans un moteur voxel dédié \
(<code>VoidSignatureVfx</code>), zéro RenderType supplémentaire. <b>Le critère d’admission \
d’une nouvelle signature n’était pas une forme libre mais un VERBE libre :</b> la sphère \
rassemble, le prisme distribue, la faille trie — restaient <b>expulser</b> (la Porte, exact \
inverse de la sphère) et <b>propager</b> (l’Effondrement, seule signature dont la puissance dépend de l’ESPACEMENT des ennemis). <b>Les trois règles qui gouvernent ce fichier :</b> on ne montre \
pas le vide, on montre <b>ce qu’il fait à ce qui l’entoure</b> — d’où la grille témoin, qui \
est le sujet et non le décor ; le noir pur est invisible sur fond noir, donc toute masse \
sombre porte son <b>liseré</b> violet néon ; et la <b>densité est une variable expressive</b> — le \
pas de la grille passe de 0,145 à 0,040 là où l’espace se tord, si bien que le maillage \
est pointillé au loin et jointif au bord.</p>\
  <div class="rule"></div>
  <span class="label">Signature de l’enchantement</span>
  <div class="variants" role="group" aria-label="Variante Vide">
    <button class="variant active" data-variant="0"><span class="num">Ⅰ</span><span>\
<b>Sphère de Distorsion</b><small>8,0 s · elle RASSEMBLE · disque d’accrétion</small>\
</span></button>
    <button class="variant" data-variant="1"><span class="num">Ⅱ</span><span>\
<b>Prisme du Dragon</b><small>9,5 s · il DISTRIBUE · sept faisceaux</small></span></button>
    <button class="variant" data-variant="2"><span class="num">Ⅲ</span><span>\
<b>Faille du Néant</b><small>8,5 s · elle TRIE · corridor, pas disque</small>\
</span></button>
    <button class="variant" data-variant="3"><span class="num">Ⅳ</span><span>\
<b>Porte d’Horizon</b><small>9,0 s · elle EXPULSE · un tore, donc un trou</small>\
</span></button>
    <button class="variant" data-variant="4"><span class="num">Ⅴ</span><span>\
<b>L’Effondrement en Chaîne</b><small>10,0 s · elle PROPAGE · sept implosions, ×1,35 chacune</small>\
</span></button>
  </div>
  <span class="label">Chorégraphie du modèle Java</span>
  <div class="controls"><button class="btn primary" id="play">Pause</button>\
<button class="btn" id="replay">Rejouer</button>\
<button class="btn" id="dice">Graine</button></div>
  <input class="timeline" id="timeline" type="range" min="0" max="1000" value="0" \
aria-label="Progression de l’animation">
  <div class="phase" id="phase">01 — la grille témoin</div>
  <div class="stats"><div class="stat"><b id="baseCount">0</b><span>Voxels</span></div>\
<div class="stat"><b id="duration">8,0 s</b><span>Durée en jeu</span></div>\
<div class="stat"><b id="seedStat">7331</b><span>Graine</span></div></div>
  <div class="legend"><span style="--c:#0E0216">Néant</span>\
<span style="--c:#050D25">Abysse</span><span style="--c:#2D0043">Basalte</span>\
<span style="--c:#282077">Ardoise</span><span style="--c:#6C13A4">Halo</span>\
<span style="--c:#9518E2">Horizon</span><span style="--c:#D7B4F7">Glacier</span>\
<span style="--c:#FFFFFF">Singularité</span><span style="--c:#D8D2A4">Endstone</span>\
<span style="--c:#4B5295">Chorus</span><span style="--c:#B18E8C">Restitution</span></div>

  <p class="refs" id="beatsRefs"></p>
  <p class="refs" id="gpRefs"></p>
  <p class="refs" id="sfxRefs"></p>
  <p class="refs"><b>L’empreinte du néant</b> — la ressource partagée des cinq. \
<b>Quatre au maximum</b>, et elle ne fait <b>aucun dégât par elle-même</b> : c’est un \
<b>seuil</b>, pas un robinet. À la quatrième, l’espace cède — implosion, Lévitation, \
Lenteur II, et un écho banqué au porteur. Une graine de poussière violette flotte au-dessus de \
la tête <b>par empreinte posée</b> : une ressource à seuil qu’on ne voit pas n’est pas une \
ressource. <b>Au 3ᵉ et au 7ᵉ écho</b>, le porteur gagne <b>Vitesse et Chute Lente — jamais \
de Force</b> : la ferveur du Sang récompense déjà l’acharnement par des dégâts, et la \
famille dont le sujet est le déplacement paie en terrain. Les échos se périment en \
<b>200 ticks</b> : c’est l’enchaînement qui paie, pas l’accumulation.</p>
  <p class="refs"><b>T390 — la repeinte et le trou.</b> La palette vient désormais des \
quatre gammes du Vide : <b>Deep Abyssal Void</b> (#050D25), le <b>charbon</b>, \
<b>Call of the Void</b> (#0e0216 → #9518e2) et la série <b>bleu/mauve délavée</b>. Règle \
appliquée : <b>la luminance de chaque rôle est conservée, seule la teinte change</b> — \
l’échelle monte 8 &lt; 13 &lt; 21 &lt; 44 &lt; 62 &lt; 84 &lt; 198 &lt; 255. Une seule \
exception, la <b>lèvre</b>, qui passe au violet néon et descend de 189 à 84 : à luminance \
égale aucune gamme n’offrait de couleur chromatique, et la garder revenait à garder un \
blanc sale. Ce qui porte une figure, c’est l’écart <b>lèvre/corps</b> (84 contre 21), pas \
l’écart lèvre/fond. Les deux tons de <b>matière étrangère</b> ne changent pas : ils sont \
le monde avalé, pas le vide.</p>
  <p class="refs"><b>Le trou, et il tourne ici.</b> Un <b>core shader</b> maison \
(<code>void_portal</code>, calqué sur <code>rendertype_end_portal</code>) ouvre au cœur de \
chaque signature un <b>disque opaque</b> rempli d’un ciel d’abysse : quinze couches \
d’étoiles additives dont les coordonnées de texture viennent de l’<b>écran</b> et non du \
modèle. C’est la réponse au seul vrai défaut des cinq figures — un bord n’existe que si ce \
qu’il encadre est sombre, et devant un mur de pierre claire l’anneau de la Porte encadrait \
de la pierre claire.<br><b>Cette page exécute le shader lui-même</b>, lu dans \
<code>assets/</code> à la construction et porté en GLSL ES 3.00 par deux substitutions \
mécaniques. Le disque est composé <b>dans l’ordre du peintre</b>, à la profondeur de son \
centre : ce qui passe derrière disparaît — y compris la moitié arrière de la silhouette, \
qui est exactement ce que verra le joueur. <b>Faites tourner la vue :</b> la figure \
pivote, le champ d’étoiles reste accroché au viewport. C’est toute la différence entre une \
texture collée sur un panneau et un portail.</p>
  <p class="refs"><b>Moteur :</b> <code>VoidSignatureVfx</code> — Sphère de Distorsion \
(#26), Prisme du Dragon (#27), Faille du Néant (#34), Porte d’Horizon (#44), \
l’Effondrement en Chaîne (#45). Les pièces sont émises par les \
émetteurs voxel du manager : aucun RenderType supplémentaire. <b>Attention :</b> l’aperçu \
ne modélise ni le tamis de densité, ni le voile d’alpha, ni le plafond de 7 000 cubes du \
moteur — il sert à relire la chorégraphie, <b>pas à la valider en jeu</b>.<br>Tests : \
<b>/anim void</b> · <b>/dlbvfx play 26</b> · <b>27</b> · <b>34</b> · <b>44</b> · \
<b>45</b> · <b>/dlbvfx stop</b>. Pour la Faille, se placer <b>dans l’axe</b> de la \
déchirure puis <b>à deux pas de côté</b> : c’est la seule aire du mod où le placement \
latéral décide. Pour la Porte, le duel est le cas intéressant — la victime doit faire un \
<b>va-et-vient</b>, pas s’éloigner. Pour l’Effondrement, comparer <b>six mobs serrés</b> à six mobs \
<b>dispersés</b> : c’est tout le sujet de la signature.</p>

  <a class="download" id="srcZip" href="{URL}" download="{ZIP_NAME}" rel="noopener">\
Télécharger les sources Vide · ZIP</a>
  <div class="zipnote">ZIP autonome embarqué dans cette page — {ENTRIES} entrées, \
{len(raw)} octets, SHA-256 <code>{SHA256}</code>. Si l’aperçu intégré bloque le \
téléchargement, ouvre cette page dans un onglet du navigateur ou récupère le même fichier \
dans <code>deliverables/</code>.</div>
</aside>
<main>
  <canvas id="scene" tabindex="0" aria-label="Aperçu animé des cinq signatures Vide">\
</canvas>
  <div class="topbar"><span class="chip live">Modèle Java T390 · VoidSignatureVfx</span>\
<span class="right"><a class="chip zipchip" id="srcZipTop" href="{URL}" \
download="{ZIP_NAME}" rel="noopener">ZIP sources</a>\
<span class="chip" id="portalChip">fenêtre —</span>\
<span class="chip" id="visibleCount">0 voxels visibles</span></span></div>
  <div class="hint">Glisser pour orbiter — <b>les étoiles de la fenêtre ne \
suivent pas</b> · Molette pour zoomer · Espace pour pause</div>
  <div class="variant-title"><b id="variantName">Sphère de Distorsion</b>\
<span id="variantSub">grille 9×9 · lentille · anneau d’Einstein · disque d’accrétion</span>\
</div>
</main>
<script>
/* ===== Moteur de géométrie — inliné depuis tools/void_build.js (miroir du .java) ===== */
{ENGINE}
{RENDER}
</script>
<script>
/* DLB-BUNDLE — archive embarquée : le bouton fonctionne même en visionneuse intégrée. */
(function () {{
  var ZIP_B64 = "{B64}";
  var ZIP_NAME = "{ZIP_NAME}";
  var link = document.getElementById('srcZip');
  var linkTop = document.getElementById('srcZipTop');
  if (!link && !linkTop) return;
  function toBlobUrl() {{
    var bin = atob(ZIP_B64);
    var bytes = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return URL.createObjectURL(new Blob([bytes], {{ type: 'application/zip' }}));
  }}
  var url = null;
  try {{ url = toBlobUrl(); }} catch (err) {{ console.warn('ZIP embarque indisponible', err); }}
  var targets = [link, linkTop].filter(Boolean);
  if (url) targets.forEach(function (el) {{ el.href = url; el.setAttribute('download', ZIP_NAME); }});
  targets.forEach(function (el) {{
    el.addEventListener('click', function (ev) {{
      if (!url) return;
      try {{
        var a = document.createElement('a');
        a.href = url; a.download = ZIP_NAME;
        document.body.appendChild(a); a.click(); a.remove();
        ev.preventDefault();
      }} catch (err) {{ }}
    }});
  }});
}})();
</script>
</body></html>
"""

out = 'deliverables/DLB_Void_Five_Signatures_preview.html'
io.open(out, 'w', encoding='utf-8').write(PAGE)
print(f'{out} : {os.path.getsize(out)} o  (ZIP embarqué : {len(raw)} o, {ENTRIES} entrées)')
