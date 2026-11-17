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
const SUBS=['grille 9\u00d79 \u00b7 lentille \u00b7 anneau d\u2019Einstein \u00b7 disque d\u2019accr\u00e9tion','octa\u00e8dre \u00b7 sept faisceaux creux \u00b7 ailes de membrane','amande de 4,4 blocs \u00b7 deux l\u00e8vres \u00b7 trois fen\u00eatres','tore au sol \xb7 membrane en spirale \xb7 tore inclin\xe9 \xb7 sept transits','sept n\u0153uds \xb7 onze ar\xeates en cha\xeenette \xb7 d\xe9chirure par le milieu'];
const DUR=[160,190,170,180,200], NAMES=['Sph\u00e8re de Distorsion','Prisme du Dragon','Faille du N\u00e9ant','Porte d\u2019Horizon','Constellation Nulle'];
const BEATS=[
 [['0 \u2192 25','la grille t\u00e9moin sort droite : 9\u00d79, au pas de 0,78 \u2014 <b>c\u2019est le seul rep\u00e8re qui prouvera la d\u00e9formation</b>'],
  ['25 \u2192 48','<b>la lentille</b> : la grille se creuse en <code>rs\u00b2 / max(r0, 0,55)</code>, le pas se resserre l\u00e0 o\u00f9 l\u2019espace se tord'],
  ['48 \u2192 74','<b>l\u2019anneau d\u2019Einstein</b> \u00e0 1,14 rayon : trois grands cercles orthogonaux, pas des parall\u00e8les \u2014 une sph\u00e8re en latitudes est une bouillie grise'],
  ['48 \u2192 106','<b>le disque d\u2019accr\u00e9tion</b> : 30 blocs en <code>exp(\u22123,1\u00b7p)</code> tombent depuis 1,9 bloc, puis basculent en rotation diff\u00e9rentielle'],
  ['106 \u2192 130','<b>l\u2019effondrement</b> : tout rentre, et rebondit une fois \u2014 <code>sin(snap\u00b7\u03c0\u00b71,6)\u00b7(1\u2212snap)\u00b70,55</code>'],
  ['130 \u2192 160','<b>la restitution</b> : ce qui a \u00e9t\u00e9 aval\u00e9 ressort en c\u00f4ne, en rose cendr\u00e9']],
 [['0 \u2192 20','la descente, en <b>trois paliers</b> : l\u2019octa\u00e8dre se pose, il ne tombe pas'],
  ['20 \u2192 42','la rotation et quatre \u00e9clats d\u2019endstone qui orbitent'],
  ['42 \u2192 62','<b>le trait d\u2019entr\u00e9e</b> : plein, depuis la main du porteur \u00e0 <code>f = \u22124,6</code>'],
  ['62 \u2192 118','<b>sept faisceaux creux</b>, un toutes les 9 frames. Le quatri\u00e8me \u2014 celui du milieu \u2014 ne subit pas le balayage (<code>sin(0)=0</code>) : il devient <b>le cou du dragon</b>, se raccourcit et se rel\u00e8ve'],
  ['95 \u2192 140','<b>les ailes</b> : 26 points de membrane par c\u00f4t\u00e9, la silhouette se ferme'],
  ['140 \u2192 170','la fracture : l\u2019octa\u00e8dre se fend'],
  ['170 \u2192 190','l\u2019\u00e9clat : la couronne au sol et la colonne']],
 [['0 \u2192 29','l\u2019ouverture \u2014 17 % de la dur\u00e9e : une f\u00ealure, presque rien'],
  ['29 \u2192 60','<b>l\u2019amande</b> s\u2019\u00e9carte en <code>sin(u\u00b7\u03c0)^0,62</code> \u2014 pas une ellipse : une amande a deux pointes'],
  ['60 \u2192 108','<b>les deux l\u00e8vres</b> : hachages <i>diff\u00e9rents</i> (<code>salt+81</code> / <code>salt+82</code>) \u2014 avec le m\u00eame, les deux bords sont jumeaux et la d\u00e9chirure a l\u2019air dessin\u00e9e'],
  ['70 \u2192 130','<b>trois fen\u00eatres sur l\u2019autre c\u00f4t\u00e9</b>, en violet chorus, \u00e0 {0,40 ; 0,055}, {0,49 ; 0,030}, {0,57 ; 0,070}'],
  ['130 \u2192 170','la fermeture \u2014 18 % : les l\u00e8vres se rejoignent, et ce qui \u00e9tait dedans part']],
 [['0 \u2192 22','le <b>trac\xe9 au sol</b> : 76 cubes \xe0 2,12 de rayon, t\xeate blanche en pointe \u2014 <b>l\u2019empreinte existe avant la porte</b>'],
  ['7 \u2192 47','<b>le tore bas</b> s\u2019ouvre. Rep\xe8re <i>explicite</i> recalcul\xe9 \xe0 chaque pas (tangente, normale radiale, verticale locale) : b\xe2tie sur les axes du monde, la section se vrille et l\u2019anneau sort en ruban tordu'],
  ['40 \u2192 140','<b>la membrane</b> : spirale logarithmique \xe0 <b>deux brins</b> en <code>exp(\u22122,35\xb7u)</code> qui <b>descend</b> \u2014 un disque plein boucherait le trou, c\u2019est-\xe0-dire le sujet'],
  ['54 \u2192 155','<b>le second tore</b>, 0,62\xd7 et <b>inclin\xe9</b> de 0,52 rad deux blocs et quart plus haut : sans l\u2019inclinaison il se lirait comme un \xe9cho du premier, pas comme une autre sortie'],
  ['61 \u2192 155','<b>sept transits</b> : chute en endstone, puis une quinzaine de frames o\xf9 le fragment <b>n\u2019existe pas</b>, puis sortie en rose cendr\u00e9 \u2014 <b>c\u2019est le trou dans la trajectoire qui prouve la travers\xe9e</b>'],
  ['155 \u2192 180','<b>la fermeture</b> : c\u2019est le <i>rayon de l\u2019\xe2me</i> qui se r\xe9sorbe, pas la section \u2014 le tore se referme sur lui-m\xeame au lieu de maigrir']],
 [['0 \u2192 20','<b>l\u2019\xe9veil des sept n\u0153uds</b>, d\xe9cal\xe9 de 2,7 ticks chacun : losange noir, c\u0153ur d\u2019abysse et six pointes claires r\xe9tractiles'],
  ['20 \u2192 76','les onze ar\xeates naissent <b>molles</b> et pendent en vraie <b>cha\xeenette</b> \u2014 <code>(cosh(1,65) \u2212 cosh(1,65\xb7(2u\u22121))) / (cosh(1,65) \u2212 1)</code>. Une ar\xeate droite d\u2019embl\xe9e est un trait de r\xe8gle : elle ne p\xe8se rien'],
  ['76 \u2192 136','<b>la mise en tension</b>, une ar\xeate \xe0 la fois dans un ordre <b>hach\xe9</b>, et un \xe9clat blanc parcourt l\u2019ar\xeate au moment o\xf9 elle se tend \u2014 le seul endroit du dossier o\xf9 quelque chose se propage <i>le long</i> d\u2019une ar\xeate'],
  ['112 \u2192 184','<b>la convergence</b> vers le barycentre. Dos\xe9e \xe0 0,46 seulement : \xe0 0,88 la trame devenait une bouillie grise de 1,8 bloc <b>vingt ticks avant</b> la fin'],
  ['172 \u2192 186','<b>la d\xe9chirure, par le milieu</b> de chaque ar\xeate \u2014 et <b>avant</b> l\u2019effondrement : l\u2019ordre inverse donnait des ar\xeates de longueur nulle au moment de rompre, donc personne ne voyait rien rompre'],
  ['184 \u2192 200','<b>le point</b> : tout ce qui \xe9tait li\xe9 tient dans un cube blanc de 0,085, h\xe9riss\xe9 de six pointes']]];
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
 "<b>0 \u2192 24</b> l\u2019\xe9veil \u2014 un <code>amethyst_block.place</code> <b>par n\u0153ud</b>, \xe0 la position du corps et non au centre, la hauteur montant avec le rang : <b>l\u2019oreille compte les corps pris</b>. <b>40 \u2192 150</b> les onze mises en tension \u2014 <code>amethyst_block.chime</code> <b>au barycentre</b>, qui se d\xe9place \xe0 mesure que la trame se resserre : la source du son converge avec elle. <b>160</b> la convergence \u2014 <code>beacon.power_select</code> \xe0 0,45, tr\xe8s grave. <b>184</b> la rupture \u2014 <code>amethyst_block.break</code> + <code>glass.break</code>."];
const GP=[
 "<b>Elle RASSEMBLE.</b> Son int\u00e9r\u00eat n\u2019est pas de frapper fort, c\u2019est de mettre six ennemis au m\u00eame endroit \u2014 apr\u00e8s quoi n\u2019importe quelle autre arme les prend tous. <b>Foule :</b> six tractions de 5 ticks dans un rayon de 5,0, <b>vitesse proportionnelle \u00e0 la distance</b> (born\u00e9e \u00e0 0,78) \u2014 une vitesse constante ferait osciller les proches autour du point comme un pendule. Lenteur II tout du long. <b>Duel :</b> il n\u2019y a personne \u00e0 rassembler, alors elle change de m\u00e9tier \u2014 elle s\u2019accroche \u00e0 l\u2019unique cible, la <b>soul\u00e8ve</b> (L\u00e9vitation), et chaque traction tape <b>2,45 fois plus fort</b>. <b>Timer.</b> Effondrement au tick 112, restitution au 128 : celle-l\u00e0 <b>repousse</b> tout ce qui avait \u00e9t\u00e9 tir\u00e9. <b>Buff :</b> Chute Lente 100 ticks au porteur.",
 "<b>Il DISTRIBUE.</b> Sept faisceaux, sept corps, un chacun. <b>Foule :</b> jusqu\u2019\u00e0 sept victimes distinctes dans 6,5, tir\u00e9es en s\u00e9quence toutes les 9 frames ; chacune re\u00e7oit une empreinte <b>et la Lueur</b> \u2014 le seul debuff franchement utile de la famille, puisqu\u2019il rend visibles \u00e0 travers les murs ceux qui sont marqu\u00e9s. <b>Duel :</b> sous trois corps, les sept faisceaux <b>CONVERGENT</b> sur le m\u00eame ; chaque trait tape moins, mais sept traits posent quatre empreintes \u2014 <b>la d\u00e9tonation est garantie</b>. <b>Timer.</b> Fracture au tick 150 : Faiblesse II, 120 ticks, sur toute la zone. \u00c9clat au 170 : L\u00e9vitation. <b>Buff :</b> Vitesse 80 ticks au porteur \u00e0 la fracture.",
 "<b>Elle TRIE.</b> Son aire n\u2019est pas un disque mais un <b>CORRIDOR de 9 blocs de long sur 3,2 de large</b>, perpendiculaire \u00e0 l\u2019axe du coup \u2014 la d\u00e9chirure est un plan, sa trace au sol est une ligne. Un disque aurait touch\u00e9 ce qui se trouve devant et derri\u00e8re la faille, c\u2019est-\u00e0-dire ce qui n\u2019en est s\u00e9par\u00e9 par rien. <b>C\u2019est la seule aire du mod o\u00f9 le placement lat\u00e9ral d\u00e9cide.</b> <b>Foule :</b> neuf aspirations qui tirent vers l\u2019<b>axe</b> et non vers le centre (seule la composante perpendiculaire est appliqu\u00e9e : tirer vers le centre entasserait tout le corridor en un point). <b>Duel :</b> la victime unique est <b>RETENUE 70 ticks</b> \u2014 elle ne peut pas sortir du corridor \u2014 et chaque aspiration tape <b>1,73 fois plus fort</b>. <b>Timer.</b> Tick 112 : C\u00e9cit\u00e9 60 + Naus\u00e9e 100. Tick 140 : fermeture, \u00e9jection <b>perpendiculaire</b>, deux empreintes. <b>Buff :</b> Chute Lente 120 ticks au porteur.",
 "<b>Elle EXPULSE.</b> C\u2019est l\u2019<b>exact inverse de la Sph\xe8re</b>, et c\u2019est la raison de la garder : deux outils oppos\xe9s sur la m\xeame famille donnent au joueur une d\xe9cision \xe0 prendre. <b>Foule :</b> sept transits toutes les 18 frames ; chacun prend le corps le plus proche de l\u2019<b>anneau</b> (pas du centre \u2014 la porte avale ce qui est tomb\xe9 dedans, pas ce qui passait au large) et l\u2019exp\xe9die sur un azimut qui <b>tourne d\u2019un septi\xe8me de tour</b> : au bout des sept, le paquet est diss\xe9min\xe9 en \xe9toile. <b>Duel :</b> rien \xe0 diss\xe9miner, alors la porte devient un <b>MOULIN</b> \u2014 l\u2019azimut fait un <b>demi-tour</b> \xe0 chaque passage, la victime est chass\xe9e d\u2019un c\xf4t\xe9 puis de l\u2019autre, et chaque transit tape <b>deux fois plus fort</b> (0,92 contre 0,45). Elle ne va nulle part, mais elle n\u2019arr\xeate jamais de tomber. <b>Timer.</b> Fermeture au tick 150 : <code>base\xb71,25</code>, deux empreintes, \xe9jection radiale. <b>Buff :</b> Chute Lente 140 ticks au porteur.",
 "<b>Elle LIE.</b> Seule signature du mod dont la zone est <b>fig\xe9e \xe0 la pose</b> : la liste des corps est captur\xe9e <b>une fois</b>, sept au plus, et <b>fuir n\u2019en sort pas</b>. Les quatre autres r\xe9interrogent le monde \xe0 chaque battement et laissent toujours une porte de sortie ; c\u2019est le prix \xe0 payer pour qu\u2019un lien veuille dire quelque chose. <b>Foule :</b> onze mises en tension, et <b>chacune frappe tout le monde</b> pour <code>base\xb70,26</code> \u2014 modeste par corps, \xe9norme sur le groupe, et chaque tension raccourcit l\u2019ar\xeate en rapprochant les corps du barycentre. <b>Duel :</b> une trame \xe0 un seul n\u0153ud n\u2019a pas d\u2019ar\xeate, alors elle se referme sur sa victime \u2014 <b>retenue 90 ticks</b> et l\u2019int\xe9gralit\xe9 des tensions pour elle seule (<code>base\xb70,42</code>). <b>Timer.</b> Convergence au 160 : Lenteur II et traction forte. Rupture au 184 : <code>base\xb71,15</code>, deux empreintes, les bouts partent. <b>Buff :</b> Chute Lente 140 ticks au porteur.",];
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
GR_NEW = ' const R=7.6,N=14;'
assert RENDER.count(GR_OLD) == 1, 'rayon de grille introuvable'
RENDER = RENDER.replace(GR_OLD, GR_NEW)

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
   servent pas ici : le vide a son propre cadrage, et il lui en faut TROIS. */
function voidShot(tt){
 /* LA SPHERE. La grille temoin fait 3,12 blocs de demi-largeur, mais elle se CREUSE :
    cadrer sur son etendue au repos couperait le disque d'accretion des que celui-ci
    bascule. 8,2 tient l'ensemble, et la vue se resserre d'un bloc et demi a
    l'effondrement -- la camera accompagne ce qui rentre. */
 if(variant===0){const k=smoothstep((tt-0.66)/0.12);
  return {span:9.0-1.3*k, center:0.95, pan:0};}
 /* LE PRISME. Il descend de deux blocs pendant les vingt premiers ticks, donc le centre
    de vue descend avec lui : fixe, l'octaedre entrait par le haut du cadre. Les ailes
    portent la silhouette a 3,5 de demi-envergure. */
 if(variant===1){const d=smoothstep(tt/0.11);
  return {span:10.8, center:2.60-0.75*d, pan:0};}
 /* LA FAILLE. Un plan vertical MINCE : vue par la tranche, ce n'est qu'un trait. La
    camera la prend de face (yaw proche de zero, cf. YAW_BY) et serre, parce que
    l'amande ne fait que 4,4 blocs de haut sur 1,6 de large. */
 if(variant===2){return {span:8.8, center:1.10, pan:0};}
 /* LA PORTE. Large et BASSE : 4,24 blocs de diametre au sol pour 2,25 de haut, plus les
    fragments qui ressortent jusqu'a 2,76 du centre. C'est la seule signature de la
    famille dont le sujet est un TROU, et un trou ne se voit qu'en plongee : le cadrage
    est tenu haut (cf. PITCH_BY) et la vue se resserre d'un bloc a la fermeture. */
 if(variant===3){const k=smoothstep((tt-0.84)/0.16);
  return {span:7.8-0.9*k, center:0.34, pan:0};}
 /* LA TRAME. Le seul objet NON CENTRE : ses sept noeuds sont tires entre 1,25 et 2,80 de
    rayon et entre 0,10 et 2,15 de haut, donc le cadre doit tenir une boite de 5,6 sur
    2,05 -- et il doit la tenir AU DEBUT, quand elle est la plus large. A la convergence
    la camera se resserre pour ne pas laisser l'objet se perdre au milieu du vide. */
 const c=smoothstep((tt-0.74)/0.20);
 return {span:8.8-2.2*c, center:1.05-0.12*c, pan:0};}
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 const s=voidShot(t); VCY=s.center; VCX=s.pan||0;
 SCALE=Math.min(W/s.span,H/(s.span*0.80))*zoom;}"""
assert RENDER.count(CAM_OLD) == 1, 'bloc updateCam introuvable dans preview_render.js'
RENDER = RENDER.replace(CAM_OLD, CAM_NEW)

# --- LE PLAN DE SOL NE BOUGE PAS. La page du Sang descendait le quadrillage de -0,47 a
#     -0,86 parce que BloodSignatureVfx pose ses flaques a cette hauteur et que, pour une
#     famille dont la premiere regle est « rien ne flotte », un liquide 0,39 bloc sous le
#     plan dessine est le defaut a corriger avant tous les autres. VoidSignatureVfx ne pose
#     NI flaque NI menisque : il travaille autour du torse, et son seul contact avec le sol
#     est la couronne d'eclat du prisme, qui lit son altitude reelle. Reporter le correctif
#     du Sang ici aurait donc decale la grille sans raison. Elle reste a -0,47.

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
function wireBox(x,y,z,hx,hy,hz){
 const c=[];
 for(let i=0;i<8;i++)c.push(project(x+((i&1)?hx:-hx),y+((i&2)?hy:-hy),z+((i&4)?hz:-hz)));
 const E=[[0,1],[2,3],[4,5],[6,7],[0,2],[1,3],[4,6],[5,7],[0,4],[1,5],[2,6],[3,7]];
 ctx.beginPath();
 for(const e of E){ctx.moveTo(c[e[0]][0],c[e[0]][1]);ctx.lineTo(c[e[1]][0],c[e[1]][1]);}
 ctx.stroke();
}
function figure(x,tall,label,tint){
 const F=-0.47;                       /* les pieds, sur le plan de grille du gabarit */
 ctx.strokeStyle=tint;ctx.lineWidth=DPR;
 const legs=tall*0.44, torso=tall*0.34, head=tall*0.22;
 /* Deux jambes, deux bras : une seule boite verticale lisait comme un lampadaire. */
 wireBox(x,F+legs/2,-0.09,0.10,legs/2,0.07);
 wireBox(x,F+legs/2, 0.09,0.10,legs/2,0.07);
 wireBox(x,F+legs+torso/2,0,0.13,torso/2,0.21);
 wireBox(x,F+legs+torso*0.62,-0.29,0.08,torso*0.42,0.08);
 wireBox(x,F+legs+torso*0.62, 0.29,0.08,torso*0.42,0.08);
 wireBox(x,F+legs+torso+head/2,0,0.14,head/2,0.14);
 const p=project(x,F+tall+0.22,0);
 ctx.fillStyle=tint;ctx.font=(11*DPR)+'px ui-monospace,Menlo,monospace';
 ctx.textAlign='center';ctx.fillText(label,p[0],p[1]);
}
function actors(){ figure(0,1.95,'VICTIME','#2f5a6b'); }
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
YAW_NEW = """const YAW_BY=[0.62,0.48,1.30,0.80,0.75], PITCH_BY=[0.24,0.24,0.18,0.36,0.26];
document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;
 camYaw=YAW_BY[variant];camPitch=PITCH_BY[variant];zoom=1;
 syncPanel();});"""
assert RENDER.count(YAW_OLD) == 1, 'gestionnaire de variante introuvable'
RENDER = RENDER.replace(YAW_OLD, YAW_NEW)

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
inverse de la sphère) et <b>lier</b> (la Trame, seul objet non centré de la famille). <b>Les trois règles qui gouvernent ce fichier :</b> on ne montre \
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
<b>Constellation Nulle</b><small>10,0 s · elle LIE · sept nœuds, onze arêtes</small>\
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
le monde avalé, pas le vide.<br>En jeu — et <b>pas dans cet aperçu</b> — un \
<b>core shader</b> maison (<code>void_portal</code>, calqué sur \
<code>rendertype_end_portal</code>) ouvre au cœur de chaque signature un disque opaque \
rempli d’un ciel d’abysse en <b>parallaxe d’écran</b> : quinze couches d’étoiles \
additives, accrochées au viewport et non au monde. C’est la réponse au seul vrai défaut \
des cinq figures : un bord n’existe que si ce qu’il encadre est sombre, et devant un mur \
de pierre claire l’anneau de la Porte encadrait de la pierre claire.</p>
  <p class="refs"><b>Moteur :</b> <code>VoidSignatureVfx</code> — Sphère de Distorsion \
(#26), Prisme du Dragon (#27), Faille du Néant (#34), Porte d’Horizon (#44), \
Constellation Nulle (#45). Les pièces sont émises par les \
émetteurs voxel du manager : aucun RenderType supplémentaire. <b>Attention :</b> l’aperçu \
ne modélise ni le tamis de densité, ni le voile d’alpha, ni le plafond de 7 000 cubes du \
moteur — il sert à relire la chorégraphie, <b>pas à la valider en jeu</b>.<br>Tests : \
<b>/anim void</b> · <b>/dlbvfx play 26</b> · <b>27</b> · <b>34</b> · <b>44</b> · \
<b>45</b> · <b>/dlbvfx stop</b>. Pour la Faille, se placer <b>dans l’axe</b> de la \
déchirure puis <b>à deux pas de côté</b> : c’est la seule aire du mod où le placement \
latéral décide. Pour la Porte, le duel est le cas intéressant — la victime doit faire un \
<b>va-et-vient</b>, pas s’éloigner. Pour la Trame, <b>s’éloigner après la pose ne doit pas \
sortir du contrat</b>.</p>

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
<span class="chip" id="visibleCount">0 voxels visibles</span></span></div>
  <div class="hint">Glisser pour orbiter · Molette pour zoomer · Espace pour pause</div>
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
