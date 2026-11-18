#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Chaos_Three_Signatures_preview.html.

Meme GABARIT que les douze pages precedentes : meme squelette, meme CSS, simplement
reteinte. Les deux morceaux partages vivent dans le depot :

  tools/preview_base.css   -- le CSS du gabarit
  tools/preview_render.js  -- le moteur de rendu cable sur les identifiants du gabarit

CE QUI CHANGE PAR RAPPORT A LA PAGE DU VIDE
-------------------------------------------
  * TROIS variantes et non cinq : les six tables de donnees reviennent a trois entrees,
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
_MOD = "if (typeof module !== 'undefined' && module.exports) {"
assert ENGINE.count(_MOD) == 1, 'bloc module.exports introuvable dans chaos_build.js'
ENGINE = ENGINE[:ENGINE.index(_MOD)]
CSS = io.open(ROOT + 'preview_base.css', encoding='utf-8').read()
RENDER = io.open(ROOT + 'preview_render.js', encoding='utf-8').read()

# --- Remplacement de l'en-tete de donnees du moteur partage ---------------------------
MARK = "\nconst cv=document.getElementById('scene')"
assert RENDER.count(MARK) == 1, 'marqueur de decoupe introuvable dans preview_render.js'
_, TAIL = RENDER.split(MARK, 1)

HEAD = r"""/* ===== Rendu ===== */
const SUBS=['6 branches \u00b7 mandala \u00e0 3 anneaux \u00b7 panneaux de miroir \u00b7 r\u00e9alignement forc\u00e9',
            '3 grands cercles \u00b7 6 m\u00e9ridiens \u00b7 coque interne \u00b7 \u00e9rosion monotone',
            '40 com\u00e8tes \u00b7 cage de confinement \u00b7 16 rayons barbel\u00e9s \u00b7 anneau noir'];
const DUR=[150,170,200], NAMES=['Boucle Kal\u00e9idoscopique','Sph\u00e8re d\u2019Entropie','Cicatrice du Big Bang'];
const BEATS=[
 [['0 \u2192 24','<b>le montage</b> : les six miroirs se verrouillent un par un, <code>amethyst_block.place</code> dont la hauteur monte \u00e0 chaque cran. <b>La sym\u00e9trie d\u2019ordre six est pos\u00e9e sans la moindre entorse</b> \u2014 c\u2019est elle, et rien d\u2019autre, qui rendra la rupture lisible'],
  ['4 \u2192 63','<b>les six branches sortent</b> en <code>smooth(t, 0,04, 0,42)</code> jusqu\u2019\u00e0 2,90 blocs. Chaque branche est une \u00e9pine de <b>douze n\u0153uds</b>, et chaque n\u0153ud est un <b>TRIANGLE</b> de trois cubes, plus un liant au demi-pas : mesur\u00e9 sur la premi\u00e8re version, huit cubes espac\u00e9s de trente centim\u00e8tres se lisaient comme une ligne pointill\u00e9e, pas comme un cristal'],
  ['24 \u2192 69','<b>la mandala</b> \u2014 trois anneaux concentriques de dix-huit cubes, qui tournent en sens altern\u00e9. C\u2019est l\u2019image qu\u2019un vrai kal\u00e9idoscope renvoie, et elle manquait enti\u00e8rement : sans elle, la figure \u00e9tait une \u00e9toile \u00e0 six branches'],
  ['30 \u2192 69','<b>les panneaux de miroir</b> \u2014 sept cubes en biais entre deux branches voisines, la paroi de verre qui FABRIQUE la sym\u00e9trie. Leur alpha vaut <code>1 \u2212 chaos</code> : ils s\u2019effacent exactement \u00e0 mesure que la figure se d\u00e9r\u00e8gle, donc c\u2019est bien la paroi qui c\u00e8de'],
  ['69 \u2192 111','<b>LE D\u00c9CROCHAGE</b>, \u00e0 46 % et pas avant. Chaque branche a son propre retard (<code>stagger = (bras\u00b70,37) mod 1</code>) : la sym\u00e9trie ne casse pas d\u2019un bloc, elle s\u2019effrite dans l\u2019ordre. Un cube <b>rose</b> appara\u00eet au coude de chaque branche, \u00e0 l\u2019endroit exact o\u00f9 elle c\u00e8de'],
  ['117 \u2192 129','<b>LE R\u00c9ALIGNEMENT FORC\u00c9</b> \u2014 la deuxi\u00e8me r\u00e8gle du fichier. Sur douze ticks, <code>relock</code> annule tout le d\u00e9sordre : la figure <b>redevient un kal\u00e9idoscope parfait</b>. Sans ce retour \u00e0 l\u2019ordre, le spectateur ne saurait jamais ce qui a \u00e9t\u00e9 cass\u00e9'],
  ['129 \u2192 150','la rupture d\u00e9finitive, puis le fondu. <b>Le moyeu, lui, n\u2019a pas boug\u00e9 d\u2019un degr\u00e9</b> : six cubes \u00e0 contresens, parfaitement r\u00e9guliers du d\u00e9but \u00e0 la fin \u2014 c\u2019est le t\u00e9moin, et c\u2019est ce qui rend le d\u00e9sordre des branches mesurable']],
 [['0 \u2192 5','<b>les trois grands cercles</b> se tracent, un par plan orthogonal, \u00e0 <code>amethyst_block.chime</code> montant. Trente-quatre segments chacun et non vingt-six : \u00e0 1,87 bloc de rayon, vingt-six cubes laissent 45 cm entre deux voisins, soit six fois leur taille \u2014 ce n\u2019\u00e9tait pas une sph\u00e8re, c\u2019\u00e9taient trois colliers'],
  ['3 \u2192 51','le rayon s\u2019ouvre \u00e0 <b>1,87 bloc</b>. <b>Six m\u00e9ridiens</b> joignent les p\u00f4les et une <b>coque interne</b> \u00e0 52 % du rayon tourne \u00e0 l\u2019envers : une sph\u00e8re vide se lit comme un anneau vue de profil, une sph\u00e8re \u00e0 deux coques garde son volume sous tous les angles'],
  ['27 \u2192 146','<b>L\u2019\u00c9ROSION</b>, et elle est MONOTONE. Chaque cube a un seuil fixe tir\u00e9 d\u2019un hachage de son indice, compar\u00e9 \u00e0 <code>entropie = (t \u2212 0,16) / 0,70</code> : <b>un cube tomb\u00e9 reste tomb\u00e9</b>. Un tirage par image aurait fait clignoter l\u2019armature enti\u00e8re au lieu de l\u2019\u00e9roder \u2014 et l\u2019\u00e9rosion est tout le sujet'],
  ['40 \u2192 146','<b>chaque cube tomb\u00e9 DEVIENT un Pixel 3D</b> qui d\u00e9rive et retombe. C\u2019est la seule fa\u00e7on de montrer que la mati\u00e8re n\u2019est pas d\u00e9truite, seulement d\u00e9sordonn\u00e9e \u2014 ce qui est la d\u00e9finition de l\u2019entropie'],
  ['60 \u2192 146','<b>le noyau grossit exactement comme l\u2019armature maigrit</b> : octa\u00e8dre rose, six sommets et <b>douze ar\u00eates</b> de trois cubes. La mati\u00e8re passe de l\u2019ordre au d\u00e9sordre, elle ne s\u2019\u00e9vapore pas'],
  ['102','<b>le d\u00e9litement</b> \u2014 l\u2019armature l\u00e2che un paquet de morceaux d\u2019un coup, <code>amethyst_block.break</code>'],
  ['150 \u2192 170','<b>L\u2019EFFONDREMENT DU NOYAU</b> : le compteur d\u2019entropie se solde. Tout ce qui est rest\u00e9 dans la sph\u00e8re paie <b>un cran de d\u00e9g\u00e2ts par palier accumul\u00e9</b>, et le disque au sol s\u2019\u00e9teint']],
 [['0 \u2192 12','<b>la compression commence</b>. Quarante fragments \u00e0 rayon FIXE convergent en <code>pull = u\u00b2</code> \u2014 un rayon tir\u00e9 \u00e0 chaque image aurait donn\u00e9 un nuage qui palpite, pas un effondrement'],
  ['12 \u2192 84','<b>les com\u00e8tes</b> : chaque fragment tra\u00eene trois cubes sur le chemin qu\u2019il vient de parcourir. Quarante points qui se rapprochent ne disent pas qu\u2019ils TOMBENT ; quarante com\u00e8tes, si'],
  ['30 \u2192 84','<b>la cage de confinement</b> \u2014 douze cubes sur un cuboct\u00e8dre qui se referme avec le c\u0153ur, et le <b>tic-tac</b> : quatorze <code>amethyst_block.hit</code> dont la hauteur monte \u00e0 chaque traction. <b>Le joueur a quatre secondes pour comprendre ce qui arrive</b>, et c\u2019est ce d\u00e9lai qui rend la d\u00e9tonation lisible plut\u00f4t que subie'],
  ['60 \u2192 84','le c\u0153ur passe du <b>rose au BLANC</b> \u00e0 72 % de la compression : le seul blanc du fichier, et il n\u2019appartient qu\u2019\u00e0 cette signature'],
  ['84','<b>LA D\u00c9TONATION.</b> <code>generic.explode</code> + <code>respawn_anchor.deplete</code> + <code>amethyst_block.resonate</code> dans la m\u00eame frame \u2014 un seul \u00e9chantillon ne fait jamais une d\u00e9tonation'],
  ['90 \u2192 170','<b>seize rayons barbel\u00e9s</b> en spirale de Fibonacci (<code>k\u00b72,39996</code>), douze pas chacun, deux barbes perpendiculaires un pas sur trois : un rayon nu est un trait, un rayon barbel\u00e9 est une onde de choc. L\u2019expansion suit <code>\u221ae</code> \u2014 un fondu lin\u00e9aire laisse une marche visible'],
  ['93 \u2192 170','<b>L\u2019ANNEAU NOIR</b>, horizontal, quarante-huit corps plus une couronne tra\u00eenante, et il va <b>plus vite que les rayons</b>. Il n\u2019\u00e9met aucune lumi\u00e8re : son r\u00f4le est de MASQUER, pour que l\u2019explosion ait un bord net au lieu de se diluer'],
  ['96 \u2192 186','<b>la cicatrice</b> \u2014 trente-quatre marques au sol, et elle RETR\u00c9CIT de 3,2 \u00e0 1,4 bloc. Une zone qui garde son rayon jusqu\u2019\u00e0 dispara\u00eetre d\u2019un coup ne donne aucune information ; une zone qui se referme dit au joueur combien de temps il lui reste']]];
/* Conception sonore. Le Chaos est la seule famille du mod ou le DESACCORD est voulu :
   son souffle de swing est grave (0,66) et son timbre aigu (1,62), deux hauteurs qui ne
   s'accordent pas. Tout vient de la banque vanilla 1.21.1, rien n'est ajoute. */
const SFX=[
 "<b>0 \u2192 24 le montage</b> \u2014 six <code>amethyst_block.place</code> dont le volume ET la hauteur montent ensemble (0,30\u21920,55 / 0,80\u21921,50) : les miroirs se verrouillent un par un, et on les compte \u00e0 l\u2019oreille. <b>26</b> le verrou \u2014 <code>beacon.power_select</code> aigu. <b>30 \u2192 120 les six frappes</b> \u2014 <code>amethyst_block.chime</code> \u00e0 la position du CORPS TOUCH\u00c9 et non au centre, hauteur 0,70 + 0,13 par bras : en foule, les six coups viennent de six endroits diff\u00e9rents et la hauteur dit lequel. Doubl\u00e9 d\u2019un <code>glass.break</code> tr\u00e8s aigu \u00e0 0,22 de volume. <b>126</b> le r\u00e9alignement \u2014 <code>beacon.power_select</code>, m\u00eame \u00e9chantillon qu\u2019au verrou mais <b>grave</b> : la figure revient d\u2019o\u00f9 elle vient. <b>142</b> la fermeture \u2014 <code>amethyst_block.break</code>.",
 "<b>2 \u2192 14 les trois cercles</b> \u2014 un <code>amethyst_block.chime</code> par plan, 0,52 / 0,74 / 0,96 : trois hauteurs pour trois plans, c\u2019est la figure qui s\u2019annonce. <b>22 \u2192 134 les huit pulsations</b> \u2014 <code>beacon.ambient</code> tr\u00e8s bas (0,26) dont la hauteur monte de 0,42 \u00e0 0,91 : le champ se CHARGE, et le joueur entend o\u00f9 il en est sans regarder. <b>60 et 110</b> les deux d\u00e9litements \u2014 <code>amethyst_block.break</code>. <b>150 l\u2019effondrement</b> \u2014 <code>respawn_anchor.deplete</code> (le seul Holder du lot, donc le seul \u00e0 r\u00e9clamer <code>.value()</code>) \u00e0 0,62 de hauteur, doubl\u00e9 d\u2019une <code>anvil.land</code> aigu\u00eb : le vide, puis le m\u00e9tal.",
 "<b>6 \u2192 84 le tic-tac</b> \u2014 quatorze <code>amethyst_block.hit</code> dont le volume monte de 0,24 \u00e0 0,57 et la hauteur de 0,55 \u00e0 1,53. <b>C\u2019est le seul avertissement dont le joueur ait besoin</b> : il entend l\u2019aspiration s\u2019emballer. <b>84 LA D\u00c9TONATION</b> \u2014 pile de trois couches dans la m\u00eame frame : <code>generic.explode</code> \u00e0 0,52 de hauteur pour le corps, <code>respawn_anchor.deplete</code> \u00e0 1,30 pour le claquement, <code>amethyst_block.resonate</code> \u00e0 0,42 pour la tra\u00eene. <b>96 \u2192 186 la cicatrice</b> \u2014 un <code>amethyst_block.hit</code> \u00e0 0,20 de volume toutes les quinze frames, hauteur DESCENDANTE de 1,50 \u00e0 1,08 : la zone s\u2019\u00e9teint, et on l\u2019entend s\u2019\u00e9teindre."];
const GP=[
 "<b>Elle R\u00c9P\u00c8TE.</b> Six branches, six frappes, et <b>tout ce qui est frapp\u00e9 revient une seconde fois</b>. <b>Foule :</b> les six branches prennent six corps DIFF\u00c9RENTS, en tournant ; chacun repart avec une part de <b>discordance</b>, et les six retomb\u00e9es arrivent ENSEMBLE \u00e0 la fermeture de la boucle \u2014 un seul coup d\u2019arme, six punitions simultan\u00e9es sept secondes plus tard. <b>Duel :</b> il n\u2019y a personne \u00e0 r\u00e9partir, donc les six branches <b>CONVERGENT</b> ; chaque frappe tape pr\u00e8s du double (0,58 contre 0,34) et la discordance s\u2019empile sur le m\u00eame corps, ce qui produit <b>la plus grosse retomb\u00e9e unique de la famille</b>. R\u00e9partir r\u00e9compense la foule, converger r\u00e9compense le duel : la m\u00eame arme, deux lectures. <b>Timer.</b> Tick 126 : r\u00e9alignement, Lenteur II 60 ticks sur toute la zone. Tick 142 : fermeture. <b>Debuff :</b> Naus\u00e9e 80 ticks \u00e0 chaque frappe. <b>Buff :</b> Vitesse II et Chute Lente au porteur \u2014 <b>jamais de Force</b>, le mod paie les familles de d\u00e9placement en terrain.",
 "<b>Elle D\u00c9SORDONNE \u2014 et c\u2019est la seule signature du mod dont la bonne r\u00e9ponse soit de PARTIR.</b> Les huit pulsations ne tapent presque rien : trois dixi\u00e8mes de l\u2019arme. Ce qui fait mal, c\u2019est l\u2019<b>entropie</b> accumul\u00e9e, qui multiplie chaque pulsation suivante (<code>\u00d7(1 + palier\u00b70,42)</code>) et empile <b>trois contraintes de nature diff\u00e9rente</b> plut\u00f4t que trois crans du m\u00eame ralentissement \u2014 trois Lenteurs ne se distinguent pas \u00e0 l\u2019\u0153il, Faiblesse puis Lenteur+Naus\u00e9e puis Fatigue+C\u00e9cit\u00e9, si. <b>Le compteur REDESCEND</b> d\u00e8s qu\u2019on sort du champ : une valeur qui ne redescendrait jamais aurait rendu la sortie inutile, donc la zone d\u00e9corative. <b>Foule :</b> 4,6 blocs de rayon, tout le monde accumule en m\u00eame temps. <b>Duel :</b> plus personne \u00e0 enfermer, donc la sph\u00e8re se <b>RESSERRE \u00e0 2,8 blocs</b> et accumule deux fois plus vite \u2014 une cible seule ne peut pas s\u2019en extraire \u00e0 la marche, elle doit d\u00e9penser quelque chose. <b>Timer.</b> Ticks 60 et 110 : un cran suppl\u00e9mentaire pour ceux qui sont rest\u00e9s. Tick 150 : <b>le solde</b>, <code>base\u00b70,34\u00b7palier</code>. <b>Buff :</b> R\u00e9sistance II 140 ticks au porteur \u2014 <b>la seule famille du mod \u00e0 en donner</b> : l\u2019entropie est un d\u00e9sordre qui \u00e9pargne celui qui en est le centre.",
 "<b>Elle CR\u00c9E puis D\u00c9TRUIT.</b> Deux temps, et rien entre les deux. <b>L\u2019aspiration</b> dure quatre-vingt-quatre ticks et ne fait <b>aucun d\u00e9g\u00e2t</b> : son r\u00f4le est de RASSEMBLER et d\u2019ANNONCER \u2014 quatorze tractions, un compte \u00e0 rebours audible qui acc\u00e9l\u00e8re, une Lenteur qui s\u2019aggrave par paliers. <b>La d\u00e9tonation</b> paie au NOMBRE : chaque corps pr\u00e9sent au-del\u00e0 du premier ajoute <b>douze pour cent \u00e0 la part de tout le monde</b>, plafonn\u00e9 \u00e0 six corps \u2014 sans plafond, un troupeau de poulets vaudrait un boss. C\u2019est la r\u00e9compense de l\u2019aspiration, et c\u2019est pour \u00e7a que les deux phases ne sont pas s\u00e9parables. <b>Duel :</b> il n\u2019y a personne \u00e0 rassembler, donc l\u2019aspiration devient un <b>CLOUAGE</b> \u2014 l\u2019emprise du Vide, qui refuse le recul \u00e0 la source, emp\u00eache un tiers de sortir la cible du foyer \u2014 et la d\u00e9tonation frappe <b>2,20 contre 1,15</b>. La signature ne perd pas son int\u00e9r\u00eat \u00e0 un contre un, elle en change. <b>D\u00e9gressif</b> avec la distance mais jamais nul (plancher 0,38) : le bord d\u2019une explosion doit rester une explosion, pas une limite administrative. <b>Timer.</b> 96 \u2192 186 : <b>la cicatrice</b>, sept passages, un rayon qui se referme de 3,2 \u00e0 1,4 bloc, et un cran d\u2019entropie par passage \u2014 la seule zone persistante du lot, et elle justifie le nom. <b>Debuff :</b> Naus\u00e9e et Faiblesse 120 ticks \u00e0 la d\u00e9tonation, plus une part de discordance report\u00e9e. <b>Buff :</b> Absorption II et Chute Lente \u2014 il a tenu au centre d\u2019un big bang."];
/* LA DISCORDANCE, ressource partagee par les trois. Elle ne ressemble ni au saignement du
   Sang ni a l'empreinte du Vide, parce qu'elle ne s'additionne pas dans le temps : elle
   DIFFERE. Un poison classique recompense celui qui frappe puis s'enfuit ; le report
   recompense l'inverse -- la victime sait ce qui arrive, le joueur sait quand, les deux ont
   une raison de rester. Et comme la somme tombe en UNE fois, elle peut tuer. */
"""

VARIANTS = 3


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
 return {center:1.20, span:6.0+11.5*wave};}
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 let span;
 if(variant===2){const s=bangShot(t);span=s.span;VCY=s.center;}
 else if(variant===0){span=7.0;VCY=1.05;}
 else{span=6.4;VCY=1.15;}
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
               edge:v.pixel?pixelEdge(v):null,
               ew:v.pixel?Math.max(1.1*DPR,Math.max(v.hx,v.hy,v.hz)*SCALE*0.42):0});"""
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
function pixelEdge(v){
 const wave=v.phase+(performance.now()/1000)*0.95;
 let c;
 if(v.pmode===0)      c=hueToRgb(chaosHue(wave));
 else if(v.pmode===1){const k=0.72+0.28*Math.sin(wave*6.2831853);
                      c=hueToRgb(v.phue).map(x=>x*k);}
 else                {const x=0.70+0.30*Math.sin(wave*6.2831853);c=[x,x,x];}
 return 'rgba('+(c[0]*255|0)+','+(c[1]*255|0)+','+(c[2]*255|0)+','
        +(0.92*(v.pfade===undefined?1:v.pfade)).toFixed(3)+')';
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
YAW_NEW = """/* LA SYMETRIE DU KALEIDOSCOPE EST HORIZONTALE : six branches a soixante degres dans le
   plan XZ. Vue a 0,34 de tangage, c'est-a-dire presque de profil, l'ordre six ne se lit
   pas du tout -- mesure sur capture. La figure se regarde donc d'EN HAUT (0,92), et elle
   est la seule du lot dans ce cas. */
const YAW_BY=[0.58,0.72,0.64], PITCH_BY=[0.92,0.38,0.42];
document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;
 camYaw=YAW_BY[variant];camPitch=PITCH_BY[variant];zoom=1;
 syncPanel();});
camYaw=YAW_BY[0];camPitch=PITCH_BY[0];"""
assert RENDER.count(YAW_OLD) == 1, 'gestionnaire de variante introuvable'
RENDER = RENDER.replace(YAW_OLD, YAW_NEW)

ZIP = 'deliverables/DLB_Chaos_Three_Signatures_src.zip'
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
  <h1>CHAOS</h1><h2>Trois signatures</h2>
  <p class="intro">Les trois derniers titres du mod sous la seconde. \
<code>CHAOS_KALEIDO_LOOP</code> et <code>ENTROPY_SPHERE</code> partageaient un \
<code>case</code> à <b>18 ticks</b>, <code>BIG_BANG_SCAR</code> en avait <b>22</b> — pour \
une sphère d’entropie et un big bang. Ils font désormais <b>150 / 170 / 200</b> dans un \
moteur dédié (<code>ChaosSignatureVfx</code>). <b>Les trois règles qui gouvernent ce \
fichier :</b> le chaos ne se lit que <b>contre un ordre</b>, donc chaque figure commence \
par poser une symétrie irréprochable ; le désordre doit <b>revenir</b> au moins une fois, \
sinon le spectateur ne sait jamais ce qui a été cassé ; et seules les <b>arêtes</b> \
portent la discordance — c’est la consigne d’origine de <code>DeepElement.CHAOS</code>, \
dont la masse centrale reste presque noire.</p>
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
  <div class="legend"><span style="--c:#120616">Corps</span>\
<span style="--c:#2B0B38">Corps clair</span><span style="--c:#5B2A68">Armature</span>\
<span style="--c:#9B3FD0">Arête</span><span style="--c:#FF48C8">Rupture</span>\
<span style="--c:#3FE8FF">Étincelle</span><span style="--c:#FFFFFF">Cœur</span>\
<span style="--c:#23232A">Pixel 3D</span></div>

  <p class="refs" id="beatsRefs"></p>
  <p class="refs" id="gpRefs"></p>
  <p class="refs" id="sfxRefs"></p>
  <p class="refs"><b>LES PIXELS 3D — le système demandé, et il tourne ici.</b> Un Pixel 3D \
est un cube miniature de 0,07 à 0,12 bloc : son <b>cœur</b> est anthracite \
<code>#23232A</code> à 65 % d’opacité, ses <b>douze arêtes</b> défilent en arc-en-ciel \
<b>fullbright</b> — insensibles à la nuit comme aux grottes. <b>Les arêtes ne sont pas de \
la géométrie.</b> Chaque cube n’a que ses <b>six faces, 24 sommets</b> ; le fragment \
shader détecte le bord dans les UV de face (<code>uv.x &lt; 0,10 || uv.x &gt; 0,90 || …</code>) \
et les quatre bords de six faces donnent exactement les douze arêtes, chacune vue par ses \
deux faces adjacentes. Une arête modélisée aurait coûté <b>huit fois plus de sommets</b>. \
<b>Fullbright par construction</b> : le programme ne reçoit ni lightmap ni overlay, il n’y \
a donc rien à désactiver pour qu’il brille à minuit. <b>La palette n’est pas la roue \
complète</b> — <code>chaosHue(u) = 0,50 + 0,42·(0,5+0,5·sin 2πu)</code> ne parcourt que la \
<b>moitié froide</b> : cyan électrique, bleu, violet profond, magenta néon. Le vert et le \
jaune n’ont rien à faire dans un chaos prismatique.</p>
  <p class="refs"><b>Pourquoi la phase est calculée côté CPU, et une seule fois par \
cube.</b> L’attribut <code>Position</code> d’un core shader est <b>relatif à la \
caméra</b> : calculer la couleur depuis cette position aurait fait <b>glisser l’arc-en-ciel \
quand le joueur marche</b>, alors que la consigne demande une onde accrochée au monde. La \
phase <code>(0,17x + 0,23y + 0,13z) mod 1</code> est donc évaluée en Java, une addition de \
trois produits par cube, et transportée dans le canal <b>rouge</b> du sommet. Ce n’est pas \
une entorse à la règle « aucun calcul de couleur RGB côté CPU » : aucune couleur n’est \
calculée, seul un <b>scalaire de position</b> l’est. Les canaux restants transportent le \
<b>mode</b> (vert), la <b>teinte imposée</b> (bleu) et le <b>fondu</b> (alpha) — le format \
de sommet est <code>POSITION_TEX_COLOR</code>, le plus étroit qui fasse le travail.</p>
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

out = 'deliverables/DLB_Chaos_Three_Signatures_preview.html'
io.open(out, 'w', encoding='utf-8').write(PAGE)
print(f'{out} : {os.path.getsize(out)} o  (ZIP embarqué : {len(raw)} o, {ENTRIES} entrées)')
