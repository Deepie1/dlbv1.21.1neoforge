#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Chaos_Five_Signatures_preview.html.

Meme GABARIT que les douze pages precedentes : meme squelette, meme CSS, simplement
reteinte. Les deux morceaux partages vivent dans le depot :

  tools/preview_base.css   -- le CSS du gabarit
  tools/preview_render.js  -- le moteur de rendu cable sur les identifiants du gabarit

CE QUI CHANGE PAR RAPPORT A LA PAGE DU VIDE
-------------------------------------------
  * CINQ variantes : les six tables de donnees reviennent a cinq entrees chacune,
    et l'assertion de comptage le verifie avant de livrer. Une table trop courte donne un
    « undefined » AFFICHE dans le panneau, sans la moindre erreur JS.
  * LES PIXELS 3D. C'est la nouveaute du lot, et elle demande une passe de peinture a
    elle : un Pixel 3D n'est pas un cube colore, c'est un cube ANTHRACITE dont les douze
    aretes defilent en arc-en-ciel. En jeu, le fragment shader deduit l'arete des UV de
    face ; ici il n'y a pas de shader, donc on trace le CONTOUR de chaque face visible
    avec la couleur que chaosHue() aurait rendue. Meme formule, meme vitesse d'onde, meme
    dependance a la position monde : ce que la page montre est ce que le shader calcule.
  * Le sol est a -0,90 (le torse de la victime moins la moitie d'un corps d'1,80), comme
    pour le Sang et contrairement au Vide : la grille du gabarit descend donc d'autant.

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
ENGINE = io.open(ROOT + 'chaos_build.js', encoding='utf-8').read()
_MOD = "if (typeof module !== 'undefined') module.exports"
assert ENGINE.count(_MOD) == 1, 'bloc module.exports introuvable dans chaos_build.js'
ENGINE = ENGINE[:ENGINE.index(_MOD)]
CSS = io.open(ROOT + 'preview_base.css', encoding='utf-8').read()
RENDER = io.open(ROOT + 'preview_render.js', encoding='utf-8').read()

# --- Remplacement de l'en-tete de donnees du moteur partage ---------------------------
MARK = "\nconst cv=document.getElementById('scene')"
assert RENDER.count(MARK) == 1, 'marqueur de decoupe introuvable dans preview_render.js'
_, TAIL = RENDER.split(MARK, 1)

HEAD = r"""/* ===== Rendu ===== */
const SUBS=['D\u00d4ME de 8 blocs \u00b7 12 c\u00f4tes \u00b7 6 gouttes qui explosent sur le mob',
            'treillis de 340 cellules \u00b7 un front de d\u00e9sordre \u00b7 le gris se disperse sur 32 crans',
            'une singularit\u00e9 \u00b7 une coquille \u00b7 150 tra\u00een\u00e9es radiales \u00b7 la toile cosmique',
            'x = r\u00b7x\u00b7(1\u2212x) r\u00e9ellement it\u00e9r\u00e9e \u00b7 r EST LE TEMPS \u00b7 1 anneau \u2192 2 \u2192 4 \u2192 8 \u2192 le brouillard \u00b7 63 impacts qui acc\u00e9l\u00e8rent',
            'automate de Bak-Tang-Wiesenfeld \u00b7 900 grains \u00b7 plaque fractale \u00b7 150 ticks de silence \u00b7 UNE avalanche'];
const DUR=[150,170,200,170,200], NAMES=['Boucle Kal\u00e9idoscopique','Sph\u00e8re d\u2019Entropie','Cicatrice du Big Bang','Cascade de Bifurcation','Le Grain de Trop'];
const BEATS=[
 [['0 \u2192 30','<b>LA COUPOLE SE L\u00c8VE.</b> La version pr\u00e9c\u00e9dente \u00e9tait un disque plat de <b>2,84 blocs</b> flottant \u00e0 1,05 au-dessus du mob : en jeu, cela passait pour un tapis pos\u00e9 sur la t\u00eate. C\u2019est maintenant un <b>d\u00f4me de huit blocs de rayon et 6,70 de fl\u00e8che</b>, et le mob occupe le <b>centre exact</b> de sa base. Mesur\u00e9 au banc : rayon 8,16 \u00e0 la cerclure, sommet \u00e0 5,84'],
  ['0 \u2192 150','<b>LES DOUZE C\u00d4TES.</b> Les anciennes parois de verre, relev\u00e9es : chacune part du sol et monte au z\u00e9nith o\u00f9 toutes se rejoignent. Six <b>fentes de pliage</b> (mode DUAL, gris 25) et six <b>axes de r\u00e9flexion</b> (MONO, gris 21), en alternance. Sans elles on voit un nuage de points ; avec elles on voit une <i>vo\u00fbte</i> \u2014 c\u2019est la pi\u00e8ce qui fait lire \u00ab d\u00f4me \u00bb'],
  ['0 \u2192 150','<b>LA SYM\u00c9TRIE MIROIR, et c\u2019est toujours le sujet.</b> Neuf \u00e9clats de cinq cubes sont tir\u00e9s dans un <b>unique secteur de trente degr\u00e9s</b>, puis copi\u00e9s <b>douze fois</b> : six rotations et, dans chaque cellule, l\u2019\u00e9clat <b>ET SON REFLET</b> (<code>local = CELL \u2212 ang</code>). Le param\u00e8tre qui portait le rayon porte d\u00e9sormais la <b>latitude</b> : le motif est enroul\u00e9 sur la demi-sph\u00e8re au lieu d\u2019\u00eatre pos\u00e9 \u00e0 plat'],
  ['0 \u2192 150','<b>SEPT PARALL\u00c8LES</b> en sens altern\u00e9, dont le nombre de marques <b>suit le rayon du cercle</b> (de 56 en bas \u00e0 9 au sommet). \u00c0 densit\u00e9 constante : un parall\u00e8le qui garderait le m\u00eame compte en montant s\u2019\u00e9paissirait, et trahirait la courbure au lieu de la montrer. Plus la <b>cerclure au sol</b>, 96 cubes clairs \u2014 c\u2019est \u00e0 elle qu\u2019on mesure les huit blocs'],
  ['21 \u2192 34, puis tous les 18 ticks','<b>LA CONCENTRATION, puis LA GOUTTE.</b> Six fois dans l\u2019animation, <b>un tiers des \u00e9clats</b> glisse vers le z\u00e9nith et s\u2019y agglom\u00e8re : la goutte est faite de la mati\u00e8re de la figure, elle n\u2019appara\u00eet pas de nulle part. Les deux autres tiers <b>restent sur la coupole</b> \u2014 premi\u00e8re version mesur\u00e9e au banc : 100 % des \u00e9clats montaient, la coupole se vidait de son motif et la concentration, \u00e9tant permanente, ne se voyait pas. Elle varie maintenant de <b>5 % \u00e0 40 %</b> d\u2019\u00e9clats en haut'],
  ['\u22125 \u2192 0 avant chaque temps fort','<b>LA CHUTE.</b> La goutte se d\u00e9tache du sommet et tombe <b>en carr\u00e9</b> (<code>1 \u2212 fall\u00b2</code>) : 5,84 \u2192 4,96 \u2192 2,33 \u2192 0,35 en cinq ticks. Une goutte <i>tombe</i>, elle ne descend pas \u00e0 vitesse constante \u2014 c\u2019est l\u2019acc\u00e9l\u00e9ration qui la rend \u00ab puissante \u00bb. Une colonne de dix cubes la suit en r\u00e9tr\u00e9cissant, sans quoi elle se t\u00e9l\u00e9porterait d\u2019une image \u00e0 l\u2019autre'],
  ['aux 6 temps forts','<b>L\u2019EXPLOSION SUR LE MOB.</b> Elle est centr\u00e9e \u00e0 <b>0,35</b>, c\u2019est-\u00e0-dire sur le <b>torse</b>. Premi\u00e8re version : la goutte descendait jusqu\u2019\u00e0 \u22120,86 \u2014 elle <b>traversait</b> la cible pour s\u2019\u00e9craser sous ses pieds, ce qui se lit comme une pluie qui tombe \u00e0 c\u00f4t\u00e9, pas comme un coup port\u00e9. Trente-six \u00e9clats sur une latitude qui descend jusqu\u2019\u00e0 \u22120,22 : une explosion sur un corps s\u2019\u00e9tale aussi vers le <b>bas</b>. Mesure : 0 cube pr\u00e8s du mob \u00e0 t\u22124, <b>67</b> \u00e0 l\u2019impact'],
  ['69 \u2192 117, puis 117 \u2192 150','<b>LE D\u00c9CROCHAGE</b> \u2014 les douze reflets cessent d\u2019\u00eatre solidaires, les c\u00f4tes se rompent en pointill\u00e9, les parall\u00e8les perdent six dixi\u00e8mes de leurs marques, et la coupole crache des perles par le bord. Puis <b>LE R\u00c9ALIGNEMENT</b> : tout revient \u00e0 sa place, les corps montent au gris 29. Les \u00e9clats d\u00e9rivent sur des sinuso\u00efdes de fr\u00e9quences <b>enti\u00e8res</b> : \u00e0 <code>t = 1</code> ils sont revenus exactement \u00e0 leur position de <code>t = 0</code>, et la boucle peut \u00eatre jou\u00e9e sans raccord']],
 [['0 \u2192 41','<b>le treillis se d\u00e9ploie</b>. Quatorze latitudes, jusqu\u2019\u00e0 vingt-sept cellules par anneau, <b>trois cent quarante cubes</b> \u2014 et ils sont tous <b>au m\u00eame gris (cran 16), \u00e0 la m\u00eame taille, en mode MONO</b>. L\u2019\u0153il lit une surface, pas des cubes : c\u2019est l\u2019\u00e9tat d\u2019ordre, et il faut le poser avant de pouvoir le d\u00e9truire'],
  ['2 \u2192 170','<b>les trois grands cercles</b>, un par plan orthogonal, en mode DUAL. Ils c\u00e8dent <b>en dernier</b> : ce sont eux qui gardent la m\u00e9moire de la forme initiale, et sans cette m\u00e9moire personne ne saurait, \u00e0 la fin, ce qui a \u00e9t\u00e9 d\u00e9truit'],
  ['17 \u2192 170','<b>LE FRONT DE D\u00c9SORDRE</b> entre dans la sph\u00e8re. Il ne frappe pas partout : il <b>traverse</b> le long de l\u2019axe attaquant \u2192 cible, avec un bord d\u00e9chiquet\u00e9 (<code>0,62 de balayage + 0,38 de hachage</code>). Il existe donc toujours une moiti\u00e9 encore propre \u00e0 c\u00f4t\u00e9 d\u2019une moiti\u00e9 d\u00e9truite \u2014 <b>c\u2019est cette cohabitation qui se lit comme de l\u2019entropie</b>, l\u00e0 o\u00f9 une destruction uniforme se lirait comme un fondu'],
  ['40 \u2192 143','<b>LE GRIS SE DISPERSE, et c\u2019est la ligne qui porte la signature.</b> Chaque cellule atteinte interpole son cran de gris <b>du 16 commun vers un tirage uniforme sur les 32</b>. Au d\u00e9but la sph\u00e8re est d\u2019un gris rigoureusement uniforme ; \u00e0 la fin les m\u00eames cellules couvrent toute l\u2019\u00e9chelle du noir au blanc. <b>L\u2019histogramme s\u2019aplatit sous les yeux du spectateur</b> \u2014 l\u2019entropie, litt\u00e9ralement'],
  ['60 \u2192 170','<b>l\u2019ar\u00eate monte par paliers</b> : MONO tant que la cellule tient, puis RAINBOW, puis <b>SPECTRUM</b> \u2014 la roue enti\u00e8re, vert et jaune compris, interdits partout ailleurs dans le mod \u2014 puis <b>STROBE</b> quand il ne reste plus rien \u00e0 tenir. La discipline monochrome c\u00e8de en quatre crans'],
  ['70 \u2192 170','<b>le noyau</b> grossit exactement comme l\u2019armature maigrit. Six branches, sept cubes chacune, <b>toujours au spectre complet</b> : le d\u00e9sordre y est d\u00e9j\u00e0 total, c\u2019est de l\u00e0 qu\u2019il vient'],
  ['100 \u2192 160','<b>les cellules se d\u00e9tachent</b> : \u00e0 saturation, chacune quitte le treillis en <b>Pixel libre</b>, avec un gris tir\u00e9 au hasard et une gravit\u00e9 de 0,34. La sph\u00e8re devient un nuage'],
  ['150 \u2192 165','<b>LE FANT\u00d4ME.</b> Le treillis d\u2019origine r\u00e9appara\u00eet une seconde, tr\u00e8s p\u00e2le, puis s\u2019\u00e9teint. R\u00e8gle de l\u2019\u00e9l\u00e9ment : <b>le d\u00e9sordre doit \u00eatre r\u00e9versible \u00e0 l\u2019\u0153il</b>. Sans ce rappel, un spectateur ne peut pas dire ce qui a \u00e9t\u00e9 perdu']],
 [['0 \u2192 32','<b>LA SINGULARIT\u00c9.</b> Quarante-six fragments convergent en <code>pull = u\u00b2</code> avec une courte com\u00e8te chacun, et au centre un point qui grossit <b>en luminance et non en taille</b> (gris 14 \u2192 31) : une singularit\u00e9 n\u2019est pas un gros objet, c\u2019est un petit objet insoutenable. <b>Trente-deux ticks, et pas quatre-vingt-quatre</b> \u2014 l\u2019aspiration est une inspiration, pas le sujet. L\u2019ancienne version y passait quarante-deux pour cent du temps et se lisait donc comme une <i>implosion</i>, l\u2019exact contraire du titre'],
  ['32 \u2192 40','<b>LA BOULE DE FEU.</b> Quatre-vingt-dix cubes au gris 31, mode MONO, sur une sph\u00e8re qui passe de 0,18 \u00e0 1,7 bloc en huit ticks. C\u2019est le seul moment de la signature o\u00f9 l\u2019image est enti\u00e8rement blanche'],
  ['36 \u2192 200','<b>LA COQUILLE EN EXPANSION</b> \u2014 cent cinquante fragments sur une sph\u00e8re de Fibonacci, rayon <code>5,8\u00b7\u221a((t\u22120,16)/0,84)</code>. La racine carr\u00e9e est obligatoire : l\u2019expansion doit \u00eatre violente puis ralentir, un fondu lin\u00e9aire donnerait une bulle de savon qui monte \u00e0 vitesse constante'],
  ['40 \u2192 200','<b>LES TRA\u00ceN\u00c9ES RADIALES, et c\u2019est elles qui font lire \u00ab big bang \u00bb.</b> Chaque fragment porte <b>quatre cubes align\u00e9s sur son rayon</b>, de plus en plus petits et sombres, et ils sont <b>derri\u00e8re</b> lui \u2014 donc \u00e0 un rayon plus PETIT. Les cent cinquante tra\u00een\u00e9es pointent toutes vers le m\u00eame point : ce faisceau de directions convergentes <b>est</b> l\u2019image de l\u2019explosion. Sans elles, une coquille qui grandit n\u2019est qu\u2019un nuage de points \u2014 on ne voit pas d\u2019o\u00f9 il vient'],
  ['116 \u2192 172','<b>LA TOILE COSMIQUE.</b> Une coquille parfaitement r\u00e9guli\u00e8re est une boule de discoth\u00e8que. Chaque fragment est donc attir\u00e9 vers l\u2019un de <b>quatorze n\u0153uds</b>, et un pont de deux cubes l\u2019y relie : la surface lisse se grumelle en <b>filaments</b>, exactement comme la mati\u00e8re r\u00e9elle l\u2019a fait. La structure cesse d\u2019\u00eatre une sph\u00e8re pour devenir un r\u00e9seau'],
  ['156 \u2192 200','<b>LE REFROIDISSEMENT</b> se lit dans le mode d\u2019ar\u00eate, fragment par fragment et d\u00e9cal\u00e9 par le hachage pour qu\u2019aucune fronti\u00e8re nette ne traverse la coquille : <b>MONO</b> blanc dans la boule de feu, <b>SPECTRUM</b> pendant l\u2019expansion, <b>RAINBOW</b> froid \u00e0 la fin. La couleur raconte la temp\u00e9rature'],
  ['160 \u2192 200','<b>LA CICATRICE.</b> Quarante-deux marques au sol en mode <b>EMBER</b> \u2014 d\u00e9rive lente autour de l\u2019ambre, fort scintillement, le seul ton chaud des trois signatures \u2014 et des braises qui montent. C\u2019est ce qui reste quand l\u2019univers a fini de se d\u00e9plier']],
 [['0 \u2192 170','<b>r N\u2019EST PLUS UNE DIRECTION DE L\u2019ESPACE, C\u2019EST LE TEMPS.</b> La premi\u00e8re version tra\u00e7ait le diagramme de Feigenbaum tel qu\u2019on le publie \u2014 r en abscisse sur un axe du monde, x en hauteur, quatre-vingt-seize colonnes. Figure exacte, et <b>graphique</b> : mise en volume puis \u00e9paissie pour ne pas dispara\u00eetre par la tranche, elle devenait une nappe informe qui remplissait le cadre. <b>Un diagramme scientifique ne devient pas une attaque parce qu\u2019on le dessine en cubes.</b> Ici r monte <b>avec le temps</b>, l\u2019orbite est it\u00e9r\u00e9e \u00e0 <b>un seul</b> r \u00e0 la fois \u2014 220 pas br\u00fbl\u00e9s, 96 \u00e9chantillons \u2014 et chaque valeur que prend l\u2019attracteur devient le <b>rayon d\u2019un anneau centr\u00e9 sur la victime</b>'],
  ['0 \u2192 170','<b>UN ANNEAU, PUIS DEUX, PUIS QUATRE, PUIS LE BROUILLARD</b> \u2014 et le compte n\u2019est pas \u00e9crit, il est <b>mesur\u00e9</b>. \u00c0 chaque image l\u2019orbite est tri\u00e9e et ses valeurs confondues regroup\u00e9es : le nombre d\u2019anneaux <i>est</i> le nombre de valeurs distinctes qu\u2019elle vient de prendre. Les paliers de r sont cal\u00e9s sur les six vagues de coups, et c\u2019est tout le sujet de la signature : \u00e0 la vague k l\u2019orbite a 2<sup>k</sup> valeurs \u2014 donc 2<sup>k</sup> anneaux \u2014 et la vague compte 2<sup>k</sup> impacts. <b>Le nombre qu\u2019on voit et le nombre qu\u2019on encaisse sont le m\u00eame nombre.</b> Au-del\u00e0 de vingt branches, il n\u2019y a plus rien \u00e0 compter : la couronne se remplit'],
  ['0 \u2192 170','<b>LE MICROSCOPE.</b> Les fen\u00eatres de la cascade sont minuscules \u2014 la p\u00e9riode 4 tient entre 3,449 et 3,544, la 8 entre 3,544 et 3,564, la 16 entre 3,564 et 3,569. Un rayon proportionnel \u00e0 x les \u00e9craserait toutes sur l\u2019\u00e9paisseur d\u2019un cube : on verrait un anneau, puis deux, puis plus jamais rien de nouveau. Alors le rayon part de la <b>position vraie</b> de la valeur dans l\u2019attracteur \u2014 les deux premiers anneaux naissent donc l\u2019un de l\u2019autre, comme la bifurcation le fait vraiment \u2014 puis glisse vers le <b>rang</b> de la branche \u00e0 mesure que les paquets autosimilaires se serrent. <b>L\u2019ordre et le nombre restent exacts ; seul l\u2019\u00e9cartement est redress\u00e9</b>, pour qu\u2019il reste comptable \u00e0 l\u2019\u0153il. Le rayon fait aussi la couleur, si bien que deux anneaux voisins ne sont jamais du m\u00eame ton'],
  ['24 \u2192 116','<b>SIX VAGUES, SIX GESTES, ET TOUS FRAPPENT LA CIBLE.</b> La premi\u00e8re version faisait tomber les impacts <i>sur le diagramme</i>, au point de l\u2019attracteur correspondant au rang du coup : l\u2019id\u00e9e \u00e9tait jolie et le r\u00e9sultat n\u2019attaquait rien \u2014 la mati\u00e8re se d\u00e9ployait sans jamais traverser le corps vis\u00e9. <b>Une figure n\u2019est pas une attaque.</b> Les soixante-trois coups arrivent maintenant tous \u00e0 hauteur de torse et repartent de l\u2019autre c\u00f4t\u00e9. Ce que la bifurcation garde, c\u2019est le <b>nombre</b> : 1, 2, 4, 8, 16, 32 \u2014 et le nombre dicte la forme, parce qu\u2019on ne frappe pas \u00e0 trente-deux comme on frappe \u00e0 un'],
  ['24 \u2192 116','<b>AUCUN DES SIX NE PARTAGE SON AXE D\u2019ARRIV\u00c9E AVEC SON VOISIN.</b> <b>1 coup \u2014 la lance</b> : longitudinale, elle entre devant et ressort derri\u00e8re, seul geste de la signature \u00e0 suivre l\u2019axe de l\u2019attaque. <b>2 coups \u2014 les m\u00e2choires</b> : deux lames plates, l\u2019une qui tombe, l\u2019autre qui monte, qui se referment exactement sur le torse, puis un disque chass\u00e9 \u00e0 plat. <b>4 coups \u2014 les anneaux</b> : quatre cercles <i>verticaux</i> qui se resserrent de 3,3 \u00e0 0,35 bloc et se croisent dans le corps. <b>8 coups \u2014 la gerbe</b> : un c\u00f4ne frontal qui se referme en avan\u00e7ant. <b>16 coups \u2014 le tourbillon</b> : seize arcs qui enroulent la cible sur deux tours avant de la couper, en braise. <b>32 coups \u2014 la gr\u00eale</b> : saturation verticale dans un cylindre d\u2019un bloc et demi, plus une onde au sol \u2014 \u00e0 trente-deux, on ne vise plus, on noie. Les dur\u00e9es tombent de huit ticks \u00e0 trois et demi'],
  ['132 \u2192 148','<b>LA FEN\u00caTRE DE P\u00c9RIODE TROIS.</b> En plein chaos, l\u2019ordre revient d\u2019un coup : vers r&nbsp;=&nbsp;3,83 la carte logistique retrouve une p\u00e9riode <i>exacte</i>. <b>Trois anneaux nets se d\u00e9tachent de la couronne et se referment sur la victime</b>, trois fois, aux ticks 132, 140 et 148 \u2014 c\u2019est le seul geste r\u00e9p\u00e9t\u00e9 de la signature, et il l\u2019est parce que la p\u00e9riode, elle, est r\u00e9ellement exacte. La version pr\u00e9c\u00e9dente plantait ici une colonne verticale de quatorze cubes <i>au-dessus</i> de la cible : une tour qui flottait sans rapport avec la figure. <b>Un geste doit sortir de ce que la signature est, pas se poser \u00e0 c\u00f4t\u00e9</b>'],
  ['140 \u2192 170','<b>LE REPLI.</b> Les anneaux ne s\u2019\u00e9teignent pas : ils se <b>referment</b> sur la victime (rayon \u00d7&nbsp;0,20 en vingt-deux ticks) pendant que le c\u0153ur, \u00e0 l\u2019aplomb du corps, bat de plus en plus vite \u2014 sa fr\u00e9quence suit la distance qui s\u00e9pare r du chaos (3&nbsp;Hz \u00e0 r&nbsp;=&nbsp;3,40, dix-sept \u00e0 3,99). L\u2019<b>ombre au sol</b> dit que les anneaux sont concentriques ; les <b>douze montants</b> qui descendent jusqu\u2019au terrain les emp\u00eachent de flotter']],
 [['0 \u2192 150','<b>UN VRAI AUTOMATE AB\u00c9LIEN DE BAK-TANG-WIESENFELD.</b> Grille 19\u00d719, un grain d\u00e9pos\u00e9 au centre ; d\u00e8s qu\u2019une case en compte <b>quatre</b>, elle se vide sur ses quatre voisines, et la r\u00e9action continue tant qu\u2019une case d\u00e9passe le seuil. Neuf cents grains sont tomb\u00e9s au moment de la rupture. <b>L\u2019automate sature \u00e0 TROIS</b> \u2014 une case stable n\u2019en contient jamais quatre \u2014 donc ce n\u2019est pas un tas au sens courant mais une <b>plaque fractale</b>, et toute sa beaut\u00e9 se lit <b>de dessus</b> : c\u2019est pourquoi la cam\u00e9ra de cette signature est la seule, avec le kal\u00e9idoscope, \u00e0 regarder presque \u00e0 la verticale. La hauteur, qui tient en trois crans, est port\u00e9e par la <b>couleur</b> (violet, magenta, blanc) : sans ce code, vue de dessus, la plaque n\u2019aurait aucun relief. <b>Les losanges concentriques ne sont pas dessin\u00e9s : ils arrivent.</b> C\u2019est la d\u00e9finition m\u00eame de la criticalit\u00e9 auto-organis\u00e9e'],
  ['0 \u2192 150','<b>CENT CINQUANTE TICKS O\u00d9 IL NE SE PASSE PRESQUE RIEN</b> \u2014 et c\u2019est le sujet. Le tas monte, le tremblement des cases hautes s\u2019intensifie (<code>build\u00b2</code>), un grain reste <b>suspendu au-dessus du centre</b> et descend de 4,6 \u00e0 0,5 bloc, son ombre port\u00e9e au sol se resserrant sous lui. On sait exactement ce qu\u2019on attend, on ne sait pas quand'],
  ['52 / 92 / 126','<b>TROIS MICRO-GLISSEMENTS, VOLONTAIREMENT MAIGRES.</b> Quatorze cubes qui d\u00e9valent un flanc, six ticks, un dixi\u00e8me de d\u00e9g\u00e2t. Ils ne sont pas l\u00e0 pour frapper mais pour dire que <b>\u00e7a tient encore</b>. Chacun part d\u2019un flanc diff\u00e9rent (angle d\u2019or) et rampe au sol au lieu de s\u2019\u00e9lever'],
  ['144 \u2192 150','<b>LE DERNIER SOUFFLE.</b> Six ticks o\u00f9 le tas se fige et o\u00f9 le grain touche. Rien ne bouge, un seul cube blanc grossit au-dessus du centre en <code>w\u00b3</code>. <b>C\u2019est ce silence-l\u00e0 qui fait le coup</b> : une signature qui frappe sans arr\u00eat ne peut pas, par construction, produire un instant qui compte'],
  ['150','<b>L\u2019AVALANCHE \u2014 LE SEUL \u00c9V\u00c9NEMENT DE LA SIGNATURE.</b> Elle vaut <b>quatre fois et demie</b> l\u2019arme, touche tout dans six blocs, et arrive sans pr\u00e9venir. Trois couches simultan\u00e9es : le <b>crat\u00e8re</b> (le tas s\u2019est vid\u00e9 par le centre, et le centre est un trou), <b>huit coul\u00e9es</b> qui descendent les flancs et vont loin \u2014 ce sont elles qui portent le coup \u2014 et la <b>poussi\u00e8re</b>, qui monte pendant que tout le reste descend. Sans elle, on verrait des cubes glisser ; avec elle, c\u2019est un \u00e9croulement'],
  ['150 \u2192 190','<b>LA RETOMB\u00c9E.</b> L\u2019onde de d\u00e9charge court au sol jusqu\u2019\u00e0 sept blocs et demi, la palette bascule du gris \u00e0 la <b>braise</b>, et les d\u00e9bris retombent \u00e0 plat. Le d\u00e9clouage pr\u00e9c\u00e8de la projection : clouer puis pousser ne pousse rien']]];
/* Conception sonore. Le Chaos est la seule famille du mod ou le DESACCORD est voulu :
   son souffle de swing est grave (0,66) et son timbre aigu (1,62), deux hauteurs qui ne
   s'accordent pas. Tout vient de la banque vanilla 1.21.1, rien n'est ajoute. */
const SFX=[
 "<b>POURQUOI ON N\u2019ENTENDAIT QUE L\u2019ENCLUME.</b> Le serveur ne diffuse un son qu\u2019\u00e0 <b>seize blocs</b>, et cette port\u00e9e ne s\u2019\u00e9tend (<code>16 \u00d7 volume</code>) qu\u2019<b>au-dessus de 1,0</b>. Les soixante-dix appels des cinq signatures \u00e9taient \u00e9crits entre <b>0,20 et 0,50</b> : diffus\u00e9s \u00e0 seize blocs seulement, puis ramen\u00e9s par l\u2019att\u00e9nuation sous le seuil audible \u00e0 quatre blocs \u2014 or la cam\u00e9ra recule \u00e0 huit. Seul <code>anvil.land</code>, \u00e0 0,92 et d\u00e9j\u00e0 tr\u00e8s fort dans l\u2019\u00e9chantillon vanilla, passait. <b>0 \u2192 30 l\u2019apparition</b> \u2014 <code>beacon.activate</code> en fondu depuis z\u00e9ro (rampe en <b>puissance 2,2</b> : l\u2019oreille \u00e9tant logarithmique, une rampe lin\u00e9aire s\u2019entend comme un son d\u00e9j\u00e0 pr\u00e9sent au deuxi\u00e8me pas), plus onze <code>amethyst_block.place</code> qui <b>acc\u00e9l\u00e8rent</b> (ratio 0,86). <b>Les six gouttes</b> \u2014 un sifflement cinq ticks AVANT l\u2019impact (on anticipe la chute au lieu de la subir), puis trois registres empil\u00e9s : <code>lightning_bolt.impact</code> \u00e0 2,2, <code>generic.explode</code> \u00e0 1,85, <code>amethyst_cluster.break</code> \u00e0 1,30. Le grave descend de goutte en goutte pendant que l\u2019aigu monte. <b>128 \u2192 150 la disparition</b> \u2014 fondu vers z\u00e9ro, une octave et demie plus bas",
 "<b>0 \u2192 24 l\u2019apparition</b> \u2014 <code>conduit.activate</code> monte du silence mais sa <b>hauteur descend</b> (1,34 \u2192 0,78) : un son qui se renforce en s\u2019abaissant se lit comme une structure qui prend du poids, pas comme une alarme. <b>20 \u2192 150 LE D\u00c9SORDRE EN VITESSE</b> \u2014 trente-deux <code>sculk.clicking</code> dont l\u2019intervalle se resserre (ratio 0,955) pendant que volume et hauteur montent : au d\u00e9but on compte les clics, \u00e0 la fin on ne les s\u00e9pare plus. <b>C\u2019est la perte d\u2019information rendue en tempo</b>, et c\u2019est la seule traduction honn\u00eate de l\u2019entropie en son. <b>Les huit pulsations</b> \u2014 deux registres dont l\u2019\u00e9cart <b>s\u2019ouvre</b> : le grave s\u2019enfonce de 0,92 \u00e0 0,56 pendant que l\u2019aigu se tend de 0,90 \u00e0 1,90. C\u2019est l\u2019\u00e9cart, pas le volume, qui fait entendre la d\u00e9gradation. <b>150 \u2192 170</b> <code>conduit.deactivate</code> en fondu vers z\u00e9ro",
 "<b>6 \u2192 82 L\u2019EFFONDREMENT</b> \u2014 dix-huit coups de timbale (<code>note_block.basedrum</code>) dont l\u2019intervalle est multipli\u00e9 par <b>0,885 \u00e0 chaque coup</b> : le compte \u00e0 rebours ne bat pas la mesure, il se <b>pr\u00e9cipite</b>. Volume 0,90 \u2192 2,80, hauteur 0,50 \u2192 1,05 : grave et lointain au d\u00e9but, pr\u00e9sent et tendu \u00e0 la fin. Dessous, <code>warden.heartbeat</code> en fondu depuis z\u00e9ro. <b>80 LE CREUX</b> \u2014 une inspiration tir\u00e9e vers l\u2019aigu puis deux ticks de rien : sans silence, la d\u00e9tonation n\u2019a pas de relief. <b>84 LA D\u00c9TONATION</b> \u2014 quatre registres sur la m\u00eame frame, tous au-dessus de 2,0 : <code>warden.sonic_boom</code> \u00e0 0,50 de hauteur, <code>generic.explode</code> \u00e0 0,62, <code>lightning_bolt.thunder</code> \u00e0 0,88, <code>end_portal.spawn</code> \u00e0 1,42. Le seul moment de la suite qui doive s\u2019entendre \u00e0 quarante blocs. <b>92 \u2192 196 L\u2019EXPANSION</b> \u2014 quatorze r\u00e9p\u00e9titions qui d\u00e9croissent vers z\u00e9ro <b>en descendant d\u2019une octave</b> : un son qui s\u2019\u00e9teint \u00e0 hauteur constante reste sur place, c\u2019est la chute de hauteur qui le fait partir au loin",
 "<b>Cette signature n\u2019\u00e9mettait AUCUN son</b> \u2014 ni apparition, ni temps fort, ni fin : z\u00e9ro appel direct dans tout <code>applyFeigenbaumGameplay</code>. <b>0 \u2192 21</b> une note tenue, unique, basse : l\u2019\u00e9tat avant la premi\u00e8re bifurcation. <b>LES SIX VAGUES, 2^k frappes chacune.</b> La loi de la figure est le <b>doublement</b>, et la partition l\u2019applique litt\u00e9ralement : une frappe, deux, quatre, huit, seize, <b>trente-deux</b>, serr\u00e9es dans une fen\u00eatre qui r\u00e9tr\u00e9cit de 9,0 \u00e0 3,8 ticks. \u00c0 la sixi\u00e8me on n\u2019entend plus des coups mais un <b>roulement</b>. L\u2019acc\u00e9l\u00e9ration vient du NOMBRE, pas d\u2019un resserrement arbitraire \u2014 c\u2019est la bifurcation rendue audible, exactement comme la figure la montre par le comptage des anneaux. <b>132 / 140 / 148 LA FEN\u00caTRE DE P\u00c9RIODE 3</b> \u2014 trois cloches <b>identiques</b>, m\u00eame hauteur, m\u00eame volume, espac\u00e9es de huit ticks pile. Apr\u00e8s trente-deux frappes indistinctes, trois sons qu\u2019on peut compter : <b>l\u2019ordre revenu s\u2019entend par le retour du d\u00e9nombrable</b>",
 "<b>0 \u2192 24 LE TAS SE REMPLIT</b> \u2014 vingt chutes de <code>sand.place</code> dont l\u2019intervalle <b>ralentit</b> (ratio 1,045). Un tas qui se remplit se tasse : il accepte de moins en moins vite, et ce ralentissement annonce que la pente approche de son seuil. C\u2019est le seul ritardando des cinq signatures, et il est le contraire exact de l\u2019acc\u00e9l\u00e9ration du Big Bang. <b>52 / 92 / 126 / 150 LES QUATRE AVALANCHES</b> \u2014 chacune est une courte rafale de gravier qui <b>acc\u00e9l\u00e8re</b> (ratio 0,80), ferm\u00e9e par un coup grave. La quatri\u00e8me est la rupture : quatorze grains au lieu de six, <code>warden.sonic_boom</code> \u00e0 2,90 et hauteur plancher au lieu de <code>iron_golem.damage</code>. <b>C\u2019est le grain de trop, et il s\u2019entend comme tel.</b> <b>170 \u2192 198</b> la poussi\u00e8re retombe en fondu vers z\u00e9ro \u2014 le dernier gravier roule encore quand tout le reste s\u2019est tu"
];
const GP=[
 "<b>Elle R\u00c9P\u00c8TE.</b> Six branches, six frappes, et <b>tout ce qui est frapp\u00e9 revient une seconde fois</b>. <b>Foule :</b> les six branches prennent six corps DIFF\u00c9RENTS, en tournant ; chacun repart avec une part de <b>discordance</b>, et les six retomb\u00e9es arrivent ENSEMBLE \u00e0 la fermeture de la boucle \u2014 un seul coup d\u2019arme, six punitions simultan\u00e9es sept secondes plus tard. <b>Duel :</b> il n\u2019y a personne \u00e0 r\u00e9partir, donc les six branches <b>CONVERGENT</b> ; chaque frappe tape pr\u00e8s du double (0,58 contre 0,34) et la discordance s\u2019empile sur le m\u00eame corps, ce qui produit <b>la plus grosse retomb\u00e9e unique de la famille</b>. R\u00e9partir r\u00e9compense la foule, converger r\u00e9compense le duel : la m\u00eame arme, deux lectures. <b>Timer.</b> Tick 126 : r\u00e9alignement, Lenteur II 60 ticks sur toute la zone. Tick 142 : fermeture. <b>Debuff :</b> Naus\u00e9e 80 ticks \u00e0 chaque frappe. <b>Buff :</b> Vitesse II et Chute Lente au porteur \u2014 <b>jamais de Force</b>, le mod paie les familles de d\u00e9placement en terrain.",
 "<b>Elle D\u00c9SORDONNE \u2014 et c\u2019est la seule signature du mod dont la bonne r\u00e9ponse soit de PARTIR.</b> Les huit pulsations ne tapent presque rien : trois dixi\u00e8mes de l\u2019arme. Ce qui fait mal, c\u2019est l\u2019<b>entropie</b> accumul\u00e9e, qui multiplie chaque pulsation suivante (<code>\u00d7(1 + palier\u00b70,42)</code>) et empile <b>trois contraintes de nature diff\u00e9rente</b> plut\u00f4t que trois crans du m\u00eame ralentissement \u2014 trois Lenteurs ne se distinguent pas \u00e0 l\u2019\u0153il, Faiblesse puis Lenteur+Naus\u00e9e puis Fatigue+C\u00e9cit\u00e9, si. <b>Le compteur REDESCEND</b> d\u00e8s qu\u2019on sort du champ : une valeur qui ne redescendrait jamais aurait rendu la sortie inutile, donc la zone d\u00e9corative. <b>Foule :</b> 4,6 blocs de rayon, tout le monde accumule en m\u00eame temps. <b>Duel :</b> plus personne \u00e0 enfermer, donc la sph\u00e8re se <b>RESSERRE \u00e0 2,8 blocs</b> et accumule deux fois plus vite \u2014 une cible seule ne peut pas s\u2019en extraire \u00e0 la marche, elle doit d\u00e9penser quelque chose. <b>Timer.</b> Ticks 60 et 110 : un cran suppl\u00e9mentaire pour ceux qui sont rest\u00e9s. Tick 150 : <b>le solde</b>, <code>base\u00b70,34\u00b7palier</code>. <b>Buff :</b> R\u00e9sistance II 140 ticks au porteur \u2014 <b>la seule famille du mod \u00e0 en donner</b> : l\u2019entropie est un d\u00e9sordre qui \u00e9pargne celui qui en est le centre.",
 "<b>Elle CR\u00c9E puis D\u00c9TRUIT.</b> Deux temps, et rien entre les deux. <b>L\u2019aspiration</b> dure quatre-vingt-quatre ticks et ne fait <b>aucun d\u00e9g\u00e2t</b> : son r\u00f4le est de RASSEMBLER et d\u2019ANNONCER \u2014 quatorze tractions, un compte \u00e0 rebours audible qui acc\u00e9l\u00e8re, une Lenteur qui s\u2019aggrave par paliers. <b>La d\u00e9tonation</b> paie au NOMBRE : chaque corps pr\u00e9sent au-del\u00e0 du premier ajoute <b>douze pour cent \u00e0 la part de tout le monde</b>, plafonn\u00e9 \u00e0 six corps \u2014 sans plafond, un troupeau de poulets vaudrait un boss. C\u2019est la r\u00e9compense de l\u2019aspiration, et c\u2019est pour \u00e7a que les deux phases ne sont pas s\u00e9parables. <b>Duel :</b> il n\u2019y a personne \u00e0 rassembler, donc l\u2019aspiration devient un <b>CLOUAGE</b> \u2014 l\u2019emprise du Vide, qui refuse le recul \u00e0 la source, emp\u00eache un tiers de sortir la cible du foyer \u2014 et la d\u00e9tonation frappe <b>2,20 contre 1,15</b>. La signature ne perd pas son int\u00e9r\u00eat \u00e0 un contre un, elle en change. <b>D\u00e9gressif</b> avec la distance mais jamais nul (plancher 0,38) : le bord d\u2019une explosion doit rester une explosion, pas une limite administrative. <b>Timer.</b> 96 \u2192 186 : <b>la cicatrice</b>, sept passages, un rayon qui se referme de 3,2 \u00e0 1,4 bloc, et un cran d\u2019entropie par passage \u2014 la seule zone persistante du lot, et elle justifie le nom. <b>Debuff :</b> Naus\u00e9e et Faiblesse 120 ticks \u00e0 la d\u00e9tonation, plus une part de discordance report\u00e9e. <b>Buff :</b> Absorption II et Chute Lente \u2014 il a tenu au centre d\u2019un big bang.",
 "<b>Elle DIVISE.</b> Six vagues, <code>2^k</code> coups chacune, soixante-trois impacts \u2014 mais chaque coup vaut <code>base / 2^(k\u00b70,62)</code> : le total par vague <b>monte</b> pendant que chaque coup <b>baisse</b>. <b>La bifurcation contamine :</b> \u00e0 partir de la troisi\u00e8me vague les coups ne tombent plus tous sur la cible, ils se r\u00e9partissent sur tout ce qui est dans les cinq blocs, <b>un ennemi par branche</b>. Une attaque qui se divise doit diviser ses cibles. Les deux derni\u00e8res vagues laissent une <b>confusion</b> de 70 ticks : apr\u00e8s le point d\u2019accumulation, plus rien n\u2019est pr\u00e9visible. <b>Foule :</b> c\u2019est son terrain \u2014 trente-deux coups cherchant des cibles distinctes. <b>Duel :</b> tout revient sur un seul corps, clou\u00e9 164 ticks",
 "<b>Elle ATTEND.</b> Diam\u00e9tralement oppos\u00e9e \u00e0 la Cascade, et c\u2019est tout l\u2019int\u00e9r\u00eat : l\u00e0 o\u00f9 l\u2019autre frappe soixante-trois fois en acc\u00e9l\u00e9rant, celle-ci ne frappe <b>qu\u2019une seule fois</b>, apr\u00e8s sept secondes et demie. Les trois micro-glissements valent un dixi\u00e8me de d\u00e9g\u00e2t et ralentissent ; l\u2019avalanche vaut <b>4,5\u00d7 l\u2019arme</b> avec d\u00e9croissance lente jusqu\u2019\u00e0 six blocs, plus <b>faiblesse</b> et <b>lenteur II</b> pendant 110 ticks. Le clouage tient <b>jusqu\u2019au bout</b> : on ne s\u2019\u00e9carte pas d\u2019un tas de sable qui s\u2019effondre, on est dessous. Le prix est assum\u00e9 \u2014 pendant sept secondes, elle ne fait presque rien"];
/* LA DISCORDANCE, ressource partagee par les trois. Elle ne ressemble ni au saignement du
   Sang ni a l'empreinte du Vide, parce qu'elle ne s'additionne pas dans le temps : elle
   DIFFERE. Un poison classique recompense celui qui frappe puis s'enfuit ; le report
   recompense l'inverse -- la victime sait ce qui arrive, le joueur sait quand, les deux ont
   une raison de rester. Et comme la somme tombe en UNE fois, elle peut tuer. */
"""

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
RENDER = RENDER.replace('buildThunder', 'buildChaos')

# --- LE PIEGE DU GABARIT PARTAGE. render() appelle le constructeur avec les angles de
#     camera en 4e et 5e argument : la foudre en a besoin pour son disque en panneau-
#     reclame, le chaos n'a aucune piece orientee vers l'ecran. Trois arguments, donc.
CALL_OLD = 'buildChaos(variant,t,salt,camYaw,camPitch)'
CALL_NEW = 'buildChaos(variant,t,salt)'
assert RENDER.count(CALL_OLD) == 1, 'appel a cinq arguments introuvable'
RENDER = RENDER.replace(CALL_OLD, CALL_NEW)

# --- LE SOL. ChaosSignatureVfx travaille autour du torse et pose ses reflets, son disque
#     et sa cicatrice a `ground`, qui vaut -0,90 dans l'apercu. La grille du gabarit est a
#     -0,47 : laissee la, les marques au sol flottent une demi-hauteur d'homme au-dessus.
GR_OLD = ' const R=6.2,N=12;'
GR_NEW = ' const R=9.0,N=18;'
assert RENDER.count(GR_OLD) == 1, 'parametres de grille introuvables'
RENDER = RENDER.replace(GR_OLD, GR_NEW)
for _old, _new in (
    ("  let p=project(u,-0.47,-R),q=project(u,-0.47,R);",
     "  let p=project(u,GFLOOR,-R),q=project(u,GFLOOR,R);"),
    ("  p=project(-R,-0.47,u);q=project(R,-0.47,u);",
     "  p=project(-R,GFLOOR,u);q=project(R,GFLOOR,u);"),
):
    assert RENDER.count(_old) == 1, 'plan de sol introuvable : ' + _old
    RENDER = RENDER.replace(_old, _new)
RENDER = RENDER.replace('function ground(){',
                        'const GFLOOR=CHAOS_SHAPE.GROUND_FALLBACK;\nfunction ground(){', 1)

# --- LES CADRAGES. Trois figures, trois echelles : le kaleidoscope tient dans 6 blocs, la
#     sphere dans 4, le big bang s'ouvre jusqu'a 11 et doit rester entier a l'ecran.
CAM_OLD = """let VCY=1.05;
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 let span;
 if(variant===1){const s=hammerShot(t);span=s.span;VCY=s.center;}
 else if(variant===0){const s=boltShot(t);span=s.span;VCY=s.center;}
 else{span=23.0;VCY=VIEW_CENTER_BY[variant];}
 SCALE=Math.min(W/span,H/(span*0.80))*zoom;}"""
CAM_NEW = """/* boltShot et hammerShot ci-dessus appartiennent au gabarit partage (foudre) et ne
   servent pas ici : aucune des trois figures du chaos ne tombe du ciel. */
let VCY=1.05;
/* Le big bang passe d'un coeur d'un cube a une onde de 11 blocs : un cadrage fixe
   sacrifierait l'un ou l'autre. La vue s'ouvre donc AVEC l'explosion, en suivant la meme
   racine carree que la geometrie -- un zoom lineaire donnerait une acceleration apparente
   que la figure n'a pas. */
function bangShot(tt){
 const BANG=CHAOS_SHAPE.BANG;
 if(tt<BANG) return {center:1.20, span:7.6-1.6*(tt/BANG)};
 const e=(tt-BANG)/(1-BANG), wave=Math.sqrt(e);
 return {center:1.20, span:5.4+9.8*wave};}
/* LE TAS passe d'une pile de deux blocs de rayon a des debris projetes a cinq et demi,
   et pendant tout le montage il faut voir LE GRAIN SUSPENDU, qui part a 4,7 blocs de
   haut. Un cadrage fixe perdrait soit le grain, soit l'avalanche : la vue descend donc
   avec le grain, puis s'ouvre avec l'ecroulement, en racine carree comme la geometrie. */
function pileShot(tt){
 const BREAK=0.75;
 if(tt<BREAK){const b=tt/BREAK;return {center:0.75-0.55*b, span:11.4-0.8*b};}
 const e=(tt-BREAK)/(1-BREAK), w=Math.sqrt(e);
 return {center:0.20+0.30*w, span:10.6+8.0*w};}
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 let span;
 if(variant===2){const s=bangShot(t);span=s.span;VCY=s.center;}
 else if(variant===4){const s=pileShot(t);span=s.span;VCY=s.center;}
 /* LA CASCADE garde un cadrage FIXE, et c'est voulu : tout l'interet est de voir les
    anneaux se DEDOUBLER. Une camera qui bouge rendrait la lecture impossible -- on ne
    saurait plus si la couronne s'elargit ou si la vue se rapproche. */
 else if(variant===3){span=10.6;VCY=1.05;}
 else if(variant===0){span=20.5;VCY=2.35;}
 else{span=5.6;VCY=1.15;}
 SCALE=Math.min(W/span,H/(span*0.80))*zoom;}"""
assert RENDER.count(CAM_OLD) == 1, 'bloc de camera introuvable'
RENDER = RENDER.replace(CAM_OLD, CAM_NEW)

# --- LE MANNEQUIN. Un repere d'echelle, et rien d'autre : sans lui, une sphere de 1,87 et
#     une sphere de 5 blocs se ressemblent trait pour trait a l'ecran.
ACT_ANCHOR = "function glow(vox){ctx.save();ctx.globalCompositeOperation='lighter';"
ACT_NEW = """/* REPERE D'APERCU -- il ne fait pas partie de la signature. En jeu, cette place est
   occupee par la victime : l'origine des trois figures est son TORSE, donc le mannequin
   mesure 1,80 et ses pieds touchent GFLOOR. */
function actors(){
 const parts=[[0,-0.90+0.68,0,0.18,0.68,0.11],
              [0,-0.90+1.52,0,0.23,0.18,0.14],
              [0,-0.90+1.74,0,0.17,0.17,0.17]];
 ctx.save();ctx.strokeStyle='rgba(120,100,180,0.42)';ctx.lineWidth=DPR;
 for(const p of parts){
  const c=[[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]]
    .map(o=>project(p[0]+o[0]*p[3],p[1]+o[1]*p[4],p[2]+o[2]*p[5]));
  const E=[[0,1],[1,2],[2,3],[3,0],[4,5],[5,6],[6,7],[7,4],[0,4],[1,5],[2,6],[3,7]];
  ctx.beginPath();
  for(const e of E){ctx.moveTo(c[e[0]][0],c[e[0]][1]);ctx.lineTo(c[e[1]][0],c[e[1]][1]);}
  ctx.stroke();
 }
 ctx.restore();
}
function glow(vox){ctx.save();ctx.globalCompositeOperation='lighter';"""
assert RENDER.count(ACT_ANCHOR) == 1, 'ancre du mannequin introuvable'
RENDER = RENDER.replace(ACT_ANCHOR, ACT_NEW)
assert RENDER.count(" ground();\n") == 1, 'appel a ground() introuvable'
RENDER = RENDER.replace(" ground();\n", " ground();\n actors();\n")

# --- LES PIXELS 3D. La passe de peinture du gabarit remplit chaque face d'un aplat ; un
#     Pixel 3D veut en plus son LISERE arc-en-ciel. On trace donc le contour de la face
#     avec la couleur que chaosHue() rendrait, a la meme phase et a la meme vitesse d'onde
#     que le fragment shader -- ce n'est pas une imitation de la couleur, c'est la formule.
PX_OLD = """   quads.push({d:d*0.25,pts:pts,fill:shade(v.rgb,k,al)});"""
PX_NEW = """   quads.push({d:d*0.25,pts:pts,fill:shade(v.rgb,k,al),
               edge:v.pixel?pixelEdge(v,f):null,
               ew:v.pixel?Math.max(1.35*DPR,Math.max(v.hx,v.hy,v.hz)*SCALE*0.50):0});"""
assert RENDER.count(PX_OLD) == 1, 'boucle de faces introuvable'
RENDER = RENDER.replace(PX_OLD, PX_NEW)

PT_OLD = """ for(const q of quads){
  ctx.fillStyle=q.fill;
  if(q.pt){ctx.fillRect(q.pt[0],q.pt[1],1.7,1.7);continue}
  ctx.beginPath();ctx.moveTo(q.pts[0][0],q.pts[0][1]);
  for(let i=1;i<4;i++)ctx.lineTo(q.pts[i][0],q.pts[i][1]);
  ctx.closePath();ctx.fill();
 }"""
PT_NEW = """ for(const q of quads){
  ctx.fillStyle=q.fill;
  if(q.pt){ctx.fillRect(q.pt[0],q.pt[1],1.7,1.7);continue}
  ctx.beginPath();ctx.moveTo(q.pts[0][0],q.pts[0][1]);
  for(let i=1;i<4;i++)ctx.lineTo(q.pts[i][0],q.pts[i][1]);
  ctx.closePath();ctx.fill();
  /* LE LISERE DU PIXEL 3D. Les douze aretes du cube sont les quatre bords de ses six
     faces : tracer le contour de chaque face visible les produit toutes, exactement
     comme le fragment shader les deduit des UV. Trace en 'lighter' parce qu'une arete
     fullbright ne se melange pas avec ce qu'il y a derriere, elle s'y ajoute. */
  if(q.edge){
   ctx.save();ctx.globalCompositeOperation='lighter';
   ctx.strokeStyle=q.edge;ctx.lineWidth=q.ew;ctx.stroke();ctx.restore();
  }
 }"""
assert RENDER.count(PT_OLD) == 1, 'boucle de peinture introuvable'
RENDER = RENDER.replace(PT_OLD, PT_NEW)

HUE_JS = """
/* ===== LA COULEUR DES ARETES, telle que void_pixel.fsh la calcule ==================
   Trois fonctions reprises a l'identique du GLSL :
     hueToRgb   -- teinte vers RGB par valeurs absolues, sans branchement
     chaosHue   -- la PALETTE DEMANDEE, qui n'est PAS la roue complete : cyan electrique,
                   magenta neon, violet profond, bleu. Ces quatre teintes occupent la
                   moitie FROIDE de la roue, entre 0,50 et 0,92. Une roue entiere y aurait
                   ajoute du vert et du jaune, qui n'ont rien a faire dans un chaos
                   prismatique froid.
     l'onde     -- phase spatiale du cube PLUS le temps. La phase vient de la position
                   monde (0,17x + 0,23y + 0,13z), donc deux cubes voisins n'ont jamais la
                   meme couleur et l'onde glisse DANS L'ESPACE autant que dans le temps.
   La page n'a pas de GameTime : on prend l'horloge de l'animation, remise a la meme
   echelle (48 tours pour 24 000 ticks). */
function hueToRgb(h){
 h=h-Math.floor(h);
 const r=Math.abs(h*6-3)-1, g=2-Math.abs(h*6-2), b=2-Math.abs(h*6-4);
 return [Math.max(0,Math.min(1,r)),Math.max(0,Math.min(1,g)),Math.max(0,Math.min(1,b))];
}
function chaosHue(u){return 0.50+0.42*(0.5+0.5*Math.sin(u*6.2831853));}
function noise1(n){return (Math.sin(n*12.9898)*43758.5453)%1;}
/* LES HUIT MODES, repris un par un du .fsh. Le contour d'une face ne peut pas porter
   deux couleurs comme le fait DUAL en jeu (ou chaque arete connait son orientation) :
   on prend alors la teinte de base, et la paire se lit quand meme sur les faces
   voisines du meme cube. C'est la seule concession du miroir, et elle est notee. */
function pixelEdge(v,f){
 const TAU=6.2831853;
 const wave=v.phase+(performance.now()/1000)*0.95;
 const m=v.pmode, h=v.phue===undefined?0:v.phue;
 let c;
 if(m===0)      c=hueToRgb(chaosHue(wave));
 else if(m===1){const k=0.72+0.28*Math.sin(wave*TAU); c=hueToRgb(h).map(x=>x*k);}
 else if(m===2){/* MONO : l'arete porte LA luminance DE SON CUBE. Une valeur fixe
                   rendait la sphere ordonnee uniformement blanche. */
                const g=(v.gray===undefined?16:v.gray)/31;
                const x=(0.21+0.66*g)*(0.82+0.18*Math.sin(wave*TAU)); c=[x,x,x];}
 else if(m===3) c=hueToRgb(wave*0.6);
 else if(m===4){const sp=0.5+0.5*Math.sin(wave*TAU*3.0);
                const b=hueToRgb(h).map(x=>x*0.62);
                c=b.map(x=>x+(1-x)*sp*sp*sp);}
 else if(m===5){/* DUAL : deux teintes selon l'orientation de l'arete. Le contour d'une
                   face ne peut pas en porter deux, mais une face HORIZONTALE n'a pas les
                   memes aretes qu'une face verticale : on choisit donc la teinte sur la
                   normale de la face. Moyenner les deux delavait tout en blanc. */
                const k=0.76+0.24*Math.sin(wave*TAU);
                const h2=h>0.72?h-0.26:h+0.26;
                c=hueToRgb((f&&f.n&&f.n[1]!==0)?h:h2).map(x=>x*k);}
 else if(m===6){const q=Math.floor((wave-Math.floor(wave))*6)/6;
                const k=0.80+0.20*Math.abs(noise1(Math.floor(wave*6)));
                c=hueToRgb(chaosHue(q)).map(x=>x*k);}
 else          {const f=0.45+0.55*Math.abs(noise1(Math.floor(wave*9)+v.phase));
                c=hueToRgb(h+0.07*Math.sin(wave*TAU*0.5)).map(x=>x*f);}
 return 'rgba('+(c[0]*255|0)+','+(c[1]*255|0)+','+(c[2]*255|0)+','
        +(0.95*(v.pfade===undefined?1:v.pfade)).toFixed(3)+')';
}
"""
RENDER = HUE_JS + RENDER

# --- Le compteur de Pixels 3D, dans le bandeau du haut : c'est le chiffre que la page
#     doit prouver, puisque la specification demande « plusieurs milliers par seconde ».
END_OLD = " document.getElementById('baseCount').textContent=vox.length;"
END_NEW = """ let np=0;for(const v of vox)if(v.pixel)np++;
 document.getElementById('pixelChip').textContent='Pixels 3D \\u00b7 '+np;
 document.getElementById('baseCount').textContent=vox.length;"""
assert RENDER.count(END_OLD) == 1, 'fin de render() introuvable'
RENDER = RENDER.replace(END_OLD, END_NEW)

# --- Cadrages par variante au clic : le big bang se regarde d'un peu plus haut.
YAW_OLD = """document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;syncPanel();});"""
YAW_NEW = """/* T406 -- LA GALETTE EST DEVENUE UNE COUPOLE, ET LA CAMERA DOIT SUIVRE.
   PITCH_BY[0] valait 1,42 : presque la verticale, parce que l'ancienne figure etait une
   IMAGE PLANE et qu'une image plane se regarde de face. Un dome de huit blocs de rayon
   vu d'au-dessus redevient exactement ce disque -- on perd toute la hauteur, c'est-a-dire
   tout ce que la refonte a ajoute. Le tangage descend donc a 0,40 rad (23 degres) : assez
   bas pour que la COURBURE se lise et que la goutte ait une hauteur de chute visible,
   assez haut pour que les six secteurs restent identifiables au sol. */
const YAW_BY=[0.26,0.72,0.64,0.34,0.58], PITCH_BY=[0.40,0.42,0.52,1.00,1.18];
document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;
 camYaw=YAW_BY[variant];camPitch=PITCH_BY[variant];zoom=1;
 syncPanel();});
camYaw=YAW_BY[0];camPitch=PITCH_BY[0];"""
assert RENDER.count(YAW_OLD) == 1, 'gestionnaire de variante introuvable'
RENDER = RENDER.replace(YAW_OLD, YAW_NEW)

ZIP = 'deliverables/DLB_Chaos_Five_Signatures_src.zip'
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
<title>Deep Lucky Block — Chaos, trois signatures</title>
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
  <div class="eyebrow">Deep Lucky Block · Chaos VFX</div>
  <div class="build">T397 · 4 novembre 2026 — treizième famille · système de \
<b>Pixels 3D</b> · core shader <code>void_pixel</code> · la discordance</div>\
  <h1>CHAOS</h1><h2>Cinq signatures</h2>
  <p class="intro">Les trois derniers titres du mod sous la seconde, et <b>deux \
nouvelles</b>. \
<code>CHAOS_KALEIDO_LOOP</code> et <code>ENTROPY_SPHERE</code> partageaient un \
<code>case</code> à <b>18 ticks</b>, <code>BIG_BANG_SCAR</code> en avait <b>22</b> — pour \
une sphère d’entropie et un big bang. Ils font désormais <b>150 / 170 / 200</b> dans un \
moteur dédié (<code>ChaosSignatureVfx</code>). Les deux dernières — <b>Cascade de \
Bifurcation</b> (170 t) et <b>Le Grain de Trop</b> (200 t) — tentent une approche que les \
trois premières n’avaient pas : <b>la figure n’est pas dessinée, elle est calculée</b>. \
L’une itère vraiment la carte logistique <code>x = r·x·(1−x)</code>, l’autre exécute \
vraiment l’automate de sable de Bak-Tang-Wiesenfeld — et elles sont <b>diamétralement \
opposées</b> : soixante-trois impacts qui accélèrent contre <b>un seul</b>, après sept \
secondes et demie de silence. <b>Les trois règles qui gouvernent ce \
fichier :</b> le chaos ne se lit que <b>contre un ordre</b>, donc chaque figure commence \
par poser une symétrie irréprochable ; le désordre doit <b>revenir</b> au moins une fois, \
sinon le spectateur ne sait jamais ce qui a été cassé ; et seules les <b>arêtes</b> \
portent la discordance. <b>Les corps sont en niveaux de gris, sans exception</b> : \
trente-deux crans du presque-noir au presque-blanc, et <b>pas un seul ton coloré</b>. \
Toute la couleur passe par les douze arêtes.</p>
  <div class="rule"></div>
  <span class="label">Signature de l’enchantement</span>
  <div class="variants" role="group" aria-label="Variante Chaos">
    <button class="variant active" data-variant="0"><span class="num">Ⅰ</span><span>\
<b>Boucle Kaléidoscopique</b><small>7,5 s · elle RÉPÈTE · réalignement forcé</small>\
</span></button>
    <button class="variant" data-variant="1"><span class="num">Ⅱ</span><span>\
<b>Sphère d’Entropie</b><small>8,5 s · elle DÉSORDONNE · érosion monotone</small>\
</span></button>
    <button class="variant" data-variant="2"><span class="num">Ⅲ</span><span>\
<b>Cicatrice du Big Bang</b><small>10,0 s · elle CRÉE puis DÉTRUIT</small>\
</span></button>
    <button class="variant" data-variant="3"><span class="num">Ⅳ</span><span>\
<b>Cascade de Bifurcation</b><small>8,5 s · elle DIVISE · 63 impacts qui accélèrent</small>\
</span></button>
    <button class="variant" data-variant="4"><span class="num">Ⅴ</span><span>\
<b>Le Grain de Trop</b><small>10,0 s · elle ATTEND · une seule avalanche</small>\
</span></button>
  </div>
  <span class="label">Chorégraphie du modèle Java</span>
  <div class="controls"><button class="btn primary" id="play">Pause</button>\
<button class="btn" id="replay">Rejouer</button>\
<button class="btn" id="dice">Graine</button></div>
  <input class="timeline" id="timeline" type="range" min="0" max="1000" value="0" \
aria-label="Progression de l’animation">
  <div class="phase" id="phase">01 — le montage</div>
  <div class="stats"><div class="stat"><b id="baseCount">0</b><span>Voxels</span></div>\
<div class="stat"><b id="duration">7,5 s</b><span>Durée en jeu</span></div>\
<div class="stat"><b id="seedStat">7331</b><span>Graine</span></div></div>
  <div class="legend"><span style="--c:#1A1A1A">Masque</span>\
<span style="--c:#3A3A3A">Corps</span><span style="--c:#5E5E5E">Corps clair</span>\
<span style="--c:#8C8C8C">Armature</span><span style="--c:#B4B4B4">Verre</span>\
<span style="--c:#E6E6E6">Éclat</span><span style="--c:#FFFFFF">Cœur</span></div>
  <div class="legend"><span style="--c:#3FE8FF">Arête · cyan</span>\
<span style="--c:#5B6BFF">Arête · bleu</span><span style="--c:#A14BFF">Arête · violet</span>\
<span style="--c:#FF48C8">Arête · magenta</span><span style="--c:#FF9A2E">Arête · braise</span></div>

  <p class="refs" id="beatsRefs"></p>
  <p class="refs" id="gpRefs"></p>
  <p class="refs" id="sfxRefs"></p>
  <p class="refs"><b>LES APPARITIONS ET LES DISPARITIONS, DE RIEN À RIEN.</b> Le \
gestionnaire d’impacts donnait à toutes les familles la même courbe d’opacité, \
<code>240·(1−t)</code>. Elle a deux défauts, et ils sont de nature opposée. Au départ \
c’est un <b>échelon</b> : la signature est à <b>pleine opacité dès sa première image</b>, \
elle ne commence pas, elle <i>apparaît</i>. À l’arrivée c’est une rampe à <b>pente \
constante</b>, donc l’œil voit deux ruptures — là où la rampe démarre et là où elle touche \
zéro — et entre les deux la figure passe la moitié de sa chorégraphie à <b>50 % d’alpha</b>, \
c’est-à-dire délavée pendant précisément les instants qu’elle est censée raconter.</p>
  <p class="refs"><b>La correction.</b> Chaos pose désormais sa propre enveloppe et \
l’appelant lui passe une opacité de référence constante : \
<code>env = smoothstep(t, 0, 0,06) · (1 − smoothstep(t, 0,84, 1))</code>. Un smoothstep a \
une <b>dérivée nulle aux deux bornes</b> : la signature n’a donc ni démarrage ni arrêt, \
seulement une accélération puis un ralenti, et le <b>plateau du milieu est plein</b>. \
L’entrée est courte — 6 %, neuf ticks sur cent cinquante — parce qu’une signature d’impact \
doit <b>répondre au coup</b> : au-delà d’une demi-seconde on ne lit plus une frappe mais \
une invocation. La sortie est presque trois fois plus longue, 16 %, parce que l’œil \
pardonne une arrivée rapide et <b>jamais</b> un arrachage. Elle est appliquée dans \
<code>put()</code>, le point d’émission unique du fichier : un paramètre de plus à faire \
descendre jusqu’à deux cents sites aurait garanti qu’on en oublie un — et il suffit d’un \
seul cube resté opaque pour que toute l’extinction se remarque.</p>
  <p class="refs"><b>Et la poussière ?</b> Un Pixel 3D libre, une fois né, ne connaît plus \
la signature qui l’a émis : il vit sa vie dans <code>VoidPixelManager</code> et \
l’enveloppe ne l’atteint pas. Un pixel né à 95 % de la chorégraphie avec quarante ticks \
de vie <b>reste donc seul en l’air une seconde après que tout le reste a disparu</b>. La \
première correction — raccourcir sa vie proportionnellement à l’enveloppe — était fausse, \
et le banc l’a montré : à <i>t</i> = 0,97 la Sphère d’Entropie affichait encore 126 pixels \
à l’alpha 222. Ce n’est pas l’enveloppe qui borne, c’est <b>le temps qui reste</b> : \
<code>vie = min(vie, ticks restants)</code>, et aucune naissance quand il reste moins de \
douze ticks, parce qu’en dessous le fondu propre du pixel se joue en deux images et se lit \
comme un <b>clignotement</b> — le défaut qu’on corrige, mais en pire. Chaque pixel monte \
enfin sur 8 % de sa vie et redescend sur les 30 derniers, en smoothstep lui aussi.</p>
  <p class="refs"><b>LES PIXELS 3D — le système demandé, et il porte MAINTENANT TOUTE la \
géométrie.</b> Les trois signatures n’emploient plus une seule fois \
<code>emitVoxelCube</code> : perles, parois, treillis, débris et cicatrice passent tous \
par le core shader. La règle de l’élément est donc appliquée <b>mécaniquement</b> et non \
par discipline — <b>aucun corps ne peut sortir coloré</b>, puisqu’aucun chemin de rendu ne \
le permet plus. Un corps n’a qu’une <b>luminance</b>, une arête n’a qu’une <b>teinte</b>, \
et les deux ne se disputent jamais l’œil.</p>
  <p class="refs"><b>Trente-deux gris, huit modes d’arête, et un seul octet pour les \
deux.</b> Le format de sommet reste <code>POSITION_TEX_COLOR</code> et ne bougera pas : \
<code>UV0</code> porte les coordonnées de face d’où sortent les douze arêtes, et les \
quatre canaux de <code>Color</code> sont pris. Le canal <b>vert</b> est donc empaqueté — \
<code>g = mode·32 + gris</code> — ce qui donne <b>huit comportements × trente-deux \
luminances, soit 256 cubes visuellement distincts</b>, pour une division et un modulo par \
sommet. Ajouter un attribut aurait imposé un format maison, donc un \
<code>RenderType</code> de plus, donc un deuxième chemin à maintenir pour un seul \
scalaire.</p>
  <p class="refs"><b>Les huit dérivés de l’arête.</b> <b>RAINBOW</b> — l’onde froide \
continue, le mode par défaut. <b>TINTED</b> — teinte imposée dont seul l’éclat pulse. \
<b>MONO</b> — luminance seule : dans la Sphère d’Entropie, c’est le mode de ce qui est \
<i>encore en ordre</i>. <b>SPECTRUM</b> — la roue entière, vert et jaune compris, \
interdits partout ailleurs : le mode du débordement. <b>PULSE</b> — une tête vive qui \
<i>court</i> le long de l’arête, donc une direction et une vitesse. <b>DUAL</b> — deux \
teintes selon l’orientation de l’arête ; l’œil lit la <i>cage</i> du cube au lieu d’un \
halo. <b>STROBE</b> — l’arc-en-ciel quantifié en six crans : la couleur ne glisse plus, \
elle <i>saute</i>, et on lit « cassé » avant de le comprendre. <b>EMBER</b> — dérive lente \
et fort scintillement, la braise de la cicatrice.</p>
  <p class="refs"><b>Les arêtes ne sont pas de la géométrie.</b> Chaque cube n’a que ses \
<b>six faces, 24 sommets</b> ; le fragment shader détecte le bord dans les UV de face \
(<code>min(uv, 1−uv) &lt; 0,10</code>) et les quatre bords de six faces donnent exactement \
les douze arêtes, chacune vue par ses deux faces adjacentes. Une arête modélisée aurait \
coûté <b>huit fois plus de sommets</b>. <b>Fullbright par construction</b> : le programme \
ne reçoit ni lightmap ni overlay, il n’y a donc rien à désactiver pour qu’il brille à \
minuit. <b>La palette par défaut n’est pas la roue complète</b> — \
<code>chaosHue(u) = 0,50 + 0,42·(0,5+0,5·sin 2πu)</code> ne parcourt que la <b>moitié \
froide</b> : cyan électrique, bleu, violet profond, magenta néon. Seul <b>SPECTRUM</b> en \
sort, et c’est pour cela qu’il signifie quelque chose.</p>
  <p class="refs"><b>Pourquoi la phase est calculée côté CPU, et une seule fois par \
cube.</b> L’attribut <code>Position</code> d’un core shader est <b>relatif à la \
caméra</b> : calculer la couleur depuis cette position aurait fait <b>glisser l’arc-en-ciel \
quand le joueur marche</b>, alors que la consigne demande une onde accrochée au monde. La \
phase <code>(0,17x + 0,23y + 0,13z) mod 1</code> est donc évaluée en Java, une addition de \
trois produits par cube, et transportée dans le canal <b>rouge</b> du sommet. Ce n’est pas \
une entorse à la règle « aucun calcul de couleur RGB côté CPU » : aucune couleur n’est \
calculée, seul un <b>scalaire de position</b> l’est. Les canaux restants transportent le \
<b>mode et le gris empaquetés</b> (vert), la <b>teinte imposée</b> (bleu) et le \
<b>fondu</b> (alpha) — le format de sommet est <code>POSITION_TEX_COLOR</code>, le plus \
étroit qui fasse le travail.</p>
  <p class="refs"><b>Le budget, et comment il est tenu.</b> <code>VoidPixelManager</code> \
stocke <b>12 000 pixels</b> dans <b>treize tableaux primitifs parallèles</b> — \
<code>double[]</code> pour les positions, <code>float[]</code> pour les vitesses, \
<code>short[]</code> pour les âges, <code>byte[]</code> pour le mode et la teinte. \
<b>Aucune allocation</b> à l’exécution, donc aucun passage de ramasse-miettes : une liste \
d’objets aurait créé plusieurs milliers d’instances par seconde et saccadé toutes les \
quelques secondes. Le retrait se fait par <b>échange avec le dernier vivant</b>, en \
O(1) — un décalage de tableau aurait coûté O(n) par mort. Le rendu est <b>un seul lot, un \
seul <code>endBatch</code></b>, à l’étape <code>AFTER_TRANSLUCENT_BLOCKS</code> : choix \
délibéré contre <code>AFTER_PARTICLES</code>, parce qu’à cette étape l’eau et le verre \
sont déjà dans le tampon de profondeur et un cube derrière une vitre est correctement \
masqué. <b>Interpolation de sous-frame</b> maison : sans elle, les cubes avancent par \
saccades de 20 Hz, ce qui se voit immédiatement sur une gerbe rapide.</p>
  <p class="refs"><b>Cette page exécute la formule du shader</b>, pas une imitation. \
<code>hueToRgb</code>, <code>chaosHue</code> et l’onde sont reprises à l’identique du \
<code>.fsh</code> ; le liseré est tracé en mode <i>lighter</i>, parce qu’une arête \
fullbright ne se mélange pas avec ce qu’il y a derrière, elle s’y <b>ajoute</b>. \
<b>Attention :</b> l’aperçu ne modélise ni le tamis de densité, ni le plafond de 7 000 \
cubes du moteur — il sert à relire la chorégraphie, <b>pas à la valider en jeu</b>.</p>
  <p class="refs"><b>La discordance</b> — la ressource partagée des trois, et elle ne \
ressemble ni au saignement du Sang ni à l’empreinte du Vide. Tout dégât du Chaos encaisse \
une part dans un <b>tampon</b> ; ce tampon ne fait rien, ne tique pas, et retombe <b>d’un \
coup</b> à l’échéance. C’est la traduction exacte de ce que le kaléidoscope montre : une \
image répétée avec un décalage. <b>Un poison classique récompense celui qui frappe puis \
s’enfuit</b> ; le report récompense l’inverse — la victime sait ce qui arrive, le joueur \
sait quand, et les deux ont une raison de rester. Et comme la somme tombe en une fois, \
<b>elle peut tuer</b>, ce qu’un poison à un demi-cœur par seconde ne fait jamais. Une \
graine rose avertit au-dessus de la tête, <b>de plus en plus serrée</b> à mesure que \
l’échéance approche : une ressource à retardement qu’on ne voit pas venir est une punition \
arbitraire.</p>
  <p class="refs"><b>Moteur :</b> <code>ChaosSignatureVfx</code> — Boucle \
Kaléidoscopique (#35), Sphère d’Entropie (#36), Cicatrice du Big Bang (#37), plus \
<code>VoidPixelManager</code> et le core shader <code>void_pixel</code>. \
Le Chaos devient la <b>treizième famille d’animation</b> : <code>/anim chaos</code> \
fonctionne, avec ses alias (<i>kaléidoscope</i>, <i>entropie</i>, <i>discorde</i>, \
<i>big_bang</i>, <i>cataclysme</i>).<br>Tests : <b>/anim chaos</b> · \
<b>/dlbvfx play 35</b> · <b>36</b> · <b>37</b> · <b>/dlbvfx stop</b>. Pour la Sphère, \
comparer <b>rester</b> et <b>sortir au bout de trois pulsations</b> : c’est tout le sujet. \
Pour le Big Bang, comparer <b>un corps seul</b> et <b>six corps serrés</b> — la prime de \
foule est de 12 % par corps.</p>

  <a class="download" id="srcZip" href="{URL}" download="{ZIP_NAME}" rel="noopener">\
Télécharger les sources Chaos · ZIP</a>
  <div class="zipnote">ZIP autonome embarqué dans cette page — {ENTRIES} entrées, \
{len(raw)} octets, SHA-256 <code>{SHA256}</code>. Si l’aperçu intégré bloque le \
téléchargement, ouvre cette page dans un onglet du navigateur ou récupère le même fichier \
dans <code>deliverables/</code>.</div>
</aside>
<main>
  <canvas id="scene" tabindex="0" aria-label="Aperçu animé des trois signatures Chaos">\
</canvas>
  <div class="topbar"><span class="chip live">Modèle Java T397 · ChaosSignatureVfx</span>\
<span class="right"><a class="chip zipchip" id="srcZipTop" href="{URL}" \
download="{ZIP_NAME}" rel="noopener">ZIP sources</a>\
<span class="chip" id="pixelChip">Pixels 3D · 0</span>\
<span class="chip" id="visibleCount">0 voxels visibles</span></span></div>
  <div class="hint">Glisser pour orbiter · Molette pour zoomer · Espace pour pause — \
<b>les arêtes arc-en-ciel sont les Pixels 3D</b></div>
  <div class="variant-title"><b id="variantName">Boucle Kaléidoscopique</b>\
<span id="variantSub">6 branches · mandala à 3 anneaux · panneaux de miroir · réalignement forcé</span>\
</div>
</main>
<script>
/* ===== Moteur de géométrie — inliné depuis tools/chaos_build.js (miroir du .java) ===== */
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
  if (!ZIP_B64) return;
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

out = 'deliverables/DLB_Chaos_Five_Signatures_preview.html'
io.open(out, 'w', encoding='utf-8').write(PAGE)
print(f'{out} : {os.path.getsize(out)} o  (ZIP embarqué : {len(raw)} o, {ENTRIES} entrées)')
