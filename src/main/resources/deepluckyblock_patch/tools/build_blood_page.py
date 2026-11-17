#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Blood_Five_Signatures_preview.html.

Meme GABARIT que les pages Gaia / Poison / Feu / Terre / Vent / Eau / Foudre : meme
squelette (aside / variants / controls / timeline / phase / stats / legend / refs /
download + main / canvas / topbar / hint / variant-title), meme CSS, simplement reteinte.
Les deux morceaux partages vivent dans le depot :

  tools/preview_base.css   — le CSS du gabarit
  tools/preview_render.js  — le moteur de rendu cable sur les identifiants du gabarit

Le moteur de rendu est generique SAUF son en-tete de donnees (SUBS / DUR / NAMES / BEATS /
SFX / GP) et l'appel a buildThunder. Le Sang est la PREMIERE famille a cinq signatures :
les tableaux du gabarit n'en portaient que trois, et les variantes 0 et 1 etaient cablees
sur les cadrages de la foudre (boltShot / hammerShot). Les deux sont remplaces ici. Les deux sont remplaces ici, par decoupe sur un
marqueur litteral : decouper par index de lignes echoue silencieusement des que le fichier
partage bouge, d'ou les assert.

L'archive est embarquee en base64 (bloc DLB-BUNDLE) : le bouton de telechargement
fonctionne meme dans une visionneuse integree.

Variable d'environnement SHA : viser refs/heads/<branche>, JAMAIS un SHA de commit — la
page est generee AVANT son propre commit, donc un SHA fige pointerait dans le vide.
"""
import base64
import hashlib
import io
import os

ROOT = 'src/main/resources/deepluckyblock_patch/tools/'
ENGINE = io.open(ROOT + 'blood_build.js', encoding='utf-8').read()
ENGINE = ENGINE.replace(
    "if (typeof module !== 'undefined' && module.exports) module.exports = { buildBlood };",
    '')
CSS = io.open(ROOT + 'preview_base.css', encoding='utf-8').read()
RENDER = io.open(ROOT + 'preview_render.js', encoding='utf-8').read()

# --- Remplacement de l'en-tete de donnees du moteur partage ---------------------------
MARK = "\nconst cv=document.getElementById('scene')"
assert RENDER.count(MARK) == 1, 'marqueur de decoupe introuvable dans preview_render.js'
_, TAIL = RENDER.split(MARK, 1)

HEAD = r"""/* ===== Rendu ===== */
const SUBS=['\u00e9chafaud de 4,6 blocs \u00b7 couperet de 1,4 \u00b7 clouage','arc de 3,4 blocs \u00b7 rideau de go\u00fattes \u00b7 flaque','embout \u00b7 tuyau en arc \u00b7 calice qui se remplit','c\u0153ur anatomique \u00b7 crosse aortique \u00b7 7 battements','11 taillades \u00b7 onde au sol \u00b7 brume'];
const DUR=[150,120,170,140,180], NAMES=['Couperet','Tranchant Sanglant','Siphon Carmin','Transfusion Vitale','Carnage Berserk'];
const BEATS=[
 [['0 \u2192 18','l\u2019\u00e9chafaud sort du sol : deux montants, un linteau, les ferrures'],
  ['18 \u2192 46','la mont\u00e9e : le couperet grimpe entre les rainures, la cha\u00eene se tend'],
  ['46 \u2192 62','la suspension : il reste en haut, immobile \u2014 <b>le seul temps mort du mod</b>'],
  ['62 \u2192 72','<b>la chute</b> : dix ticks, en chute libre, sans ralenti'],
  ['72 \u2192 108','la morsure : la lame est dans le billot, le sang gicle \u00e0 l\u2019horizontale'],
  ['108 \u2192 150','l\u2019\u00e9coulement : la flaque s\u2019\u00e9tale, l\u2019\u00e9chafaud s\u2019efface par le bas']],
 [['0 \u2192 14','l\u2019amorce : le trait de coupe s\u2019ouvre, net, sur 3,4 blocs'],
  ['14 \u2192 38','<b>l\u2019arc</b> : la plaie s\u2019\u00e9carte, les l\u00e8vres se retroussent'],
  ['38 \u2192 66','le rideau : les gouttes se d\u00e9tachent du bord inf\u00e9rieur et tombent'],
  ['66 \u2192 96','l\u2019impact au sol : chaque goutte fait sa couronne'],
  ['96 \u2192 120','la flaque : les couronnes fusionnent, le m\u00e9nisque se ferme']],
 [['0 \u2192 14','<b>la morsure</b> : la bride de fer se plaque sur la poitrine et quatre crochets d\u2019os se plantent autour'],
  ['14 \u2192 34','<b>le tuyau</b> : il se construit de la victime vers le porteur, par-dessus \u2014 un siphon monte et redescend. Sa longueur est leur distance r\u00e9elle, et ses deux bouts suivent les deux corps'],
  ['27 \u2192 40','<b>le calice</b> appara\u00eet dans la main du porteur, vide'],
  ['34 \u2192 136','<b>les six bols</b> : un renflement clair remonte le tuyau toutes les 17 frames, et <b>le calice monte d\u2019un sixi\u00e8me \u00e0 chaque arriv\u00e9e</b>. Les c\u00f4tes sombres glissent vers le porteur : c\u2019est le sens de l\u2019\u00e9coulement'],
  ['34 \u2192 170','la plaie goutte, une flaque s\u2019\u00e9tale sous la victime'],
  ['136 \u2192 153','le tuyau se tend et s\u2019amincit, l\u2019arc s\u2019aplatit'],
  ['153 \u2192 170','<b>l\u2019arrachement</b> : les crochets sautent, le tuyau se r\u00e9tracte vers le calice, qui reste plein dans la main']],
 [['0 \u2192 20','l\u2019appel : deux vaisseaux plongent dans la victime, deux remontent vers le porteur'],
  ['20 \u2192 118','<b>sept battements</b> : systole, diastole \u2014 le c\u0153ur se contracte de 12 %'],
  ['42 \u2192 118','les convois : chaque battement pousse une perle dans chaque vaisseau'],
  ['118 \u2192 132','le dernier souffle : le c\u0153ur ralentit, les coronaires p\u00e2lissent'],
  ['132 \u2192 140','la dissolution : l\u2019organe s\u2019efface par le haut']],
 [['0 \u2192 25','la premi\u00e8re taillade : un arc seul, lent, presque propre'],
  ['25 \u2192 70','la mont\u00e9e : les taillades se rapprochent et commencent \u00e0 se chevaucher'],
  ['70 \u2192 112','<b>la fr\u00e9n\u00e9sie</b> : jusqu\u2019\u00e0 huit arcs vivants en m\u00eame temps'],
  ['112 \u2192 128','l\u2019onde au sol : tout ce qui a gicl\u00e9 retombe d\u2019un coup'],
  ['128 \u2192 170','la brume : les plus fines gouttes restent en suspension'],
  ['170 \u2192 180','la retomb\u00e9e : la brume creuse et la flaque reste']]];
/* Conception sonore. Le sang n'a pas de registre dedie dans la banque vanilla : il emprunte
   au tranchant (player.attack.sweep), au liquide (honey, slime, drink) et a l'os. Les trois
   noms pressentis qui n'existent PAS en 1.21.1 -- RAVAGER_BITE, CROSSBOW_LOADING_END,
   ARMOR_EQUIP_CHAIN -- ont ete remplaces apres verification contre la table des 1 806
   identifiants reels. Les 31 sons de la famille sont attestes un par un. */
const SFX=[
 "<b>Le seul silence du mod.</b> <b>0 \u2192 18</b> l\u2019\u00e9chafaud \u2014 <code>wood.place</code> par pi\u00e8ce, puis <code>anvil.land</code> grave pour le linteau. <b>18 \u2192 46</b> la mont\u00e9e \u2014 <b>neuf</b> <code>chain.place</code> dont l\u2019\u00e9cart <i>diminue</i> \u00e0 mesure que la lame monte : l\u2019oreille entend une traction qui acc\u00e9l\u00e8re alors que l\u2019image ne montre qu\u2019un objet qui glisse. <b>46 \u2192 62</b> <b>rien</b>. Seize ticks sans un son \u2014 c\u2019est le seul endroit de la famille o\u00f9 le silence est \u00e9crit. <b>62</b> la chute \u2014 <code>chain.break</code> sec. <b>72</b> la morsure \u2014 <code>anvil.land</code> + <code>player.attack.crit</code> + <code>slime.squish</code> dans la m\u00eame frame. <b>74 \u2192 100</b> les giclures \u2014 <code>honey_block.slide</code>. <b>108 \u2192 150</b> l\u2019\u00e9coulement, <code>honey_block.fall</code> de plus en plus espac\u00e9s.",
 "<b>0</b> l\u2019amorce \u2014 <code>player.attack.sweep</code>, et rien d\u2019autre : la coupe doit sonner <i>propre</i>, l\u2019horreur vient apr\u00e8s. <b>14</b> l\u2019arc \u2014 <code>slime.squish</code> \u00e9touff\u00e9. <b>38 \u2192 66</b> le rideau \u2014 <b>une <code>honey_block.fall</code> par goutte</b>, chacune a sa propre position : c\u2019est ce qui donne sa <i>largeur</i> \u00e0 la plaie. <b>66 \u2192 96</b> les impacts \u2014 <code>honey_block.break</code>. <b>96 \u2192 120</b> la flaque, <code>water.ambient</code> tr\u00e8s grave.",
 "<b>0 \u2192 16</b> les crochets \u2014 quatre <code>bone_block.place</code> secs, un par crochet. <b>16</b> le serrage \u2014 <code>chain.place</code>. <b>16 \u2192 44</b> le conduit \u2014 <code>honey_block.slide</code> en boucle. <b>44 \u2192 112</b> <b>les six bols</b> \u2014 <code>generic.drink</code> toutes les 17 frames, hauteur <i>montante</i> : chaque bol est plus aigu que le pr\u00e9c\u00e9dent, le porteur se remplit. <b>\u00c0 chaque arriv\u00e9e</b>, le calice encaisse \u2014 <code>generic.drink</code> puis une \u00e9claboussure. <b>153</b> la rupture \u2014 <code>chain.break</code> + <code>slime.squish</code>. <b>158</b> le reflux.",
 "<b>La seule bande bienveillante de la famille.</b> <b>0 \u2192 20</b> l\u2019appel \u2014 <code>beacon.activate</code> grave. <b>20 \u2192 118</b> <b>les sept battements</b> \u2014 deux coups par battement (<code>warden.heartbeat</code> appuy\u00e9 puis <code>slime.squish</code> doux) : <i>poum-ta</i>, systole puis diastole. Un seul coup aurait sonn\u00e9 comme un marteau. <b>42 \u2192 118</b> les convois \u2014 <code>honey_block.slide</code> au d\u00e9part de chaque perle. <b>\u00c0 l\u2019arriv\u00e9e</b>, <code>generic.drink</code> pour l\u2019alli\u00e9 soign\u00e9. <b>132</b> la dissolution \u2014 <code>beacon.deactivate</code>.",
 "<b>0 \u2192 112</b> les onze taillades \u2014 <code>player.attack.sweep</code> pour chacune, et l\u2019\u00e9cart passe de 25 \u00e0 7 ticks : la bande acc\u00e9l\u00e8re avec l\u2019image. Aux <b>4\u1d49, 7\u1d49 et 10\u1d49 coups TOUCH\u00c9S</b> \u2014 pas lanc\u00e9s \u2014 <code>player.attack.crit</code> se superpose : le palier de rage s\u2019entend. <b>112</b> l\u2019onde \u2014 <code>honey_block.break</code> massif. <b>128 \u2192 170</b> la brume \u2014 <code>water.ambient</code> en nappe. <b>170</b> la retomb\u00e9e."];
const GP=[
 "<b>Contact.</b> <b>Une ex\u00e9cution, pas un coup.</b> Les d\u00e9g\u00e2ts sont multipli\u00e9s par <code>1 + 1,25 \u00d7 (vie manquante)</code> : \u00e0 pleine vie c\u2019est un coup ordinaire, \u00e0 20 % de PV c\u2019est le double. La signature ne punit pas le combat, elle le <i>termine</i>. <b>Timer.</b> La cible est <b>clou\u00e9e 86 ticks</b> \u2014 le temps exact de la mont\u00e9e, de la suspension et de la chute : elle voit venir. <b>Buff :</b> R\u00e9sistance si la lame tue.",
 "<b>Contact.</b> <b>Une ligne, pas un disque</b> \u2014 seule zone en ligne du mod : tout ce qui est sur l\u2019arc est touch\u00e9, ce qui est \u00e0 c\u00f4t\u00e9 ne l\u2019est pas. <b>Duel :</b> 4 piles d\u2019h\u00e9morragie d\u2019un coup, <b>200 ticks</b>. <b>Foule :</b> 2 piles chacun, +1 \u00e0 la cible centrale. <b>Timer.</b> Flaque de <b>180 ticks</b> sous les victimes. <b>Buff :</b> le porteur r\u00e9cup\u00e8re un demi-c\u0153ur par pile pos\u00e9e. <b>Le tarif de la pile est tomb\u00e9 de 0,115 \u00e0 0,075 de la base</b> : elle \u00e9tait, de loin, la plus grosse source de d\u00e9g\u00e2ts des cinq, alors qu\u2019elle se d\u00e9crit comme celle qui frappe peu.",
 "<b>Contact.</b> Six pr\u00e9l\u00e8vements de 17 ticks. <b>58 % de ce qui est pris est rendu</b> au porteur \u2014 pas la totalit\u00e9 : un siphon qui rend tout serait une arme sans co\u00fbt. <b>Trois conduits au maximum</b>, m\u00eame dans une foule. <b>LA LAISSE.</b> Le conduit relie deux \u00eatres qui bougent : ses deux bouts suivent la victime et le porteur, sa longueur est leur distance r\u00e9elle, et <b>au-del\u00e0 de 9 blocs il casse</b> \u2014 la pompe s\u2019arr\u00eate, les bols restants sont perdus. Fuir n\u2019est pas gratuit : l\u2019arrachement co\u00fbte <b>45 % de la base et deux piles d\u2019h\u00e9morragie</b>, et la morsure a pos\u00e9 <b>Lenteur II pendant 120 ticks</b>. <b>Timer.</b> Rupture au tick 153, <b>doubl\u00e9e si la cible est sous 34 % de PV</b>. <b>Buff :</b> la ferveur monte \u00e0 chaque bol.",
 "<b>La seule aire bienveillante de la famille.</b> Le c\u0153ur flotte <b>au milieu exact du segment porteur-victime</b> et s\u2019y maintient : il <b>pr\u00e9l\u00e8ve dans un rayon de 6,0</b> sur les ennemis et <b>redistribue dans 4,0</b> au porteur, aux joueurs et aux <b>compagnons apprivois\u00e9s</b>. <b>Sept battements.</b> 85 % du pot est reversible, dont <b>55 % pour le porteur</b> \u2014 il soigne les siens, il ne se sacrifie pas. <b>M\u00eame laisse que le siphon :</b> au-del\u00e0 de 9 blocs les vaisseaux s\u2019arrachent et le c\u0153ur se r\u00e9sorbe. <b>Les animaux de b\u00e9tail ne sont ni pr\u00e9lev\u00e9s ni soign\u00e9s.</b>",
 "<b>Contact.</b> Onze taillades, de 25 \u00e0 112 ticks, l\u2019\u00e9cart se resserre. <b>Les paliers de rage tombent aux 4\u1d49, 7\u1d49 et 10\u1d49 coups <i>touch\u00e9s</i></b>, pas lanc\u00e9s : frapper le vide ne rend pas enrag\u00e9. <b>Timer.</b> Onde au sol au tick 115, brume de 130 \u00e0 170. <b>Malus assum\u00e9 :</b> \u00e0 partir de 6 coups le porteur prend <b>Fatigue 60 ticks</b> \u2014 la fr\u00e9n\u00e9sie co\u00fbte. <b>Ressource.</b> L\u2019h\u00e9morragie <b>punit la fuite</b> : \u00d7(1 + 1,5 \u00d7 min(1, vitesse / 0,22)), soit jusqu\u2019\u00e0 <b>\u00d72,5</b> pour une cible qui court."];
"""

RENDER = HEAD + MARK + TAIL
assert RENDER.count('buildThunder') == 1, 'appel buildThunder introuvable'
RENDER = RENDER.replace('buildThunder', 'buildBlood')

# --- LE PIEGE DU GABARIT PARTAGE. render() appelle le constructeur avec les angles de
#     camera en 4e et 5e argument (buildThunder(variant,t,salt,camYaw,camPitch)) : la foudre
#     s'en sert pour ses panneaux-reclame. Depuis que buildBlood prend linkLen et linkEndY a
#     ces deux places, la page passait camYaw EN GUISE DE LONGUEUR DE CONDUIT — 0,16 bloc —
#     et tout le siphon se repliait sur la victime. L'apercu fixe desormais un lien
#     explicite : sept blocs, la main 25 cm plus haut que la poitrine, lien intact.
CALL_OLD = 'buildBlood(variant,t,salt,camYaw,camPitch)'
CALL_NEW = 'buildBlood(variant,t,salt,LINK_LEN,LINK_END_Y,false)'
assert RENDER.count(CALL_OLD) == 1, 'appel du constructeur introuvable'
RENDER = RENDER.replace(CALL_OLD, CALL_NEW)
RENDER = RENDER.replace('let W=0,H=0,DPR=1, variant=0',
                        'const LINK_LEN=7.0, LINK_END_Y=0.25;\nlet W=0,H=0,DPR=1, variant=0')

# --- La grille s'arretait a 6,2 blocs alors que le porteur se tient a -7 : il flottait
#     au-dela du sol dessine. Elle s'elargit pour les deux signatures de lien.
GR_OLD = ' const R=6.2,N=12;'
GR_NEW = ' const R=(variant===2||variant===3)?9.4:6.2,N=(variant===2||variant===3)?16:12;'
assert RENDER.count(GR_OLD) == 1, 'rayon de grille introuvable'
RENDER = RENDER.replace(GR_OLD, GR_NEW)

# --- Cadrage. Le gabarit partage cable la variante 0 sur boltShot (la chute de l'eclair)
#     et la variante 1 sur hammerShot (la chute du marteau) : herite tel quel, il filmait
#     le sang avec la camera de la foudre. Et VIEW_CENTER_BY n'a que TROIS entrees, donc
#     les variantes 3 et 4 cadraient sur undefined. Les cinq echelles n'ont rien de commun
#     -- un echafaud de 4,6 blocs debout, un arc de 3,4 couche, un conduit de 7 a
#     l'horizontale, un coeur de 1,5 et une sphere de taillades de 5 -- donc chacune a son
#     cadrage. Le couperet DESCEND avec la lame : la camera suit ce qui tombe.
CAM_OLD = """function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 let span;
 if(variant===1){const s=hammerShot(t);span=s.span;VCY=s.center;}
 else if(variant===0){const s=boltShot(t);span=s.span;VCY=s.center;}
 else{span=23.0;VCY=VIEW_CENTER_BY[variant];}
 SCALE=Math.min(W/span,H/(span*0.80))*zoom;}"""
CAM_NEW = """/* boltShot et hammerShot ci-dessus appartiennent au gabarit partage (foudre) et ne
   servent pas ici : le sang a son propre cadrage, et il lui en faut CINQ. */
function bloodShot(tt){
 /* L'echafaud fait 4,6 blocs de haut : une portee de 7,4 le decapitait en haut comme
     en bas. 9,2 le cadre en entier, et la vue se resserre de deux blocs a la chute. */
 if(variant===0){const k=smoothstep((tt-0.41)/0.10);
  return {span:9.2-1.9*k, center:2.45-0.55*k, pan:0};}
 if(variant===1) return {span:7.2, center:1.05, pan:0};
 /* Le conduit court de la cible (x=0) jusqu'au porteur (x=-7) : la vue se recentre sur
    son milieu, sans quoi la moitie de la signature est hors champ. */
 if(variant===2) return {span:9.8, center:0.35, pan:-3.1};
 /* La transfusion est un lien, elle aussi : le coeur est au MILIEU du segment et deux
    vaisseaux vont jusqu'au porteur. Cadrage de lien, donc, comme le siphon. */
 if(variant===3) return {span:9.4, center:1.00, pan:-3.4};
 return {span:8.6, center:1.25, pan:0};}
function updateCam(){CY=Math.cos(camYaw);SY=Math.sin(camYaw);CP=Math.cos(camPitch);SP=Math.sin(camPitch);
 const s=bloodShot(t); VCY=s.center; VCX=s.pan||0;
 SCALE=Math.min(W/s.span,H/(s.span*0.80))*zoom;}"""
assert RENDER.count(CAM_OLD) == 1, 'bloc updateCam introuvable dans preview_render.js'
RENDER = RENDER.replace(CAM_OLD, CAM_NEW)

# --- Deux defauts du gabarit partage, visibles des le premier rendu de la page ---------
#
# 1. LA GRILLE N'EST PAS AU SOL. Le gabarit trace son quadrillage a y = -0,47, hauteur
#    heritee de la foudre, alors que le moteur du sang pose son sol a GROUND_Y = -0,86.
#    Les flaques, les couronnes et la colonne du siphon se retrouvaient donc 0,39 bloc
#    SOUS le plan dessine : le liquide avait l'air de flotter au-dessus de rien. Pour une
#    famille dont la premiere regle est « rien ne flotte », c'est le defaut a corriger
#    avant tous les autres.
GRID_OLD = "let p=project(u,-0.47,-R),q=project(u,-0.47,R);"
GRID_OLD2 = "  p=project(-R,-0.47,u);q=project(R,-0.47,u);"
assert RENDER.count(GRID_OLD) == 1 and RENDER.count(GRID_OLD2) == 1, 'plan de sol introuvable'
RENDER = RENDER.replace(GRID_OLD, "let p=project(u,-0.86,-R),q=project(u,-0.86,R);")
RENDER = RENDER.replace(GRID_OLD2, "  p=project(-R,-0.86,u);q=project(R,-0.86,u);")

# 2. PAS DE RECADRAGE HORIZONTAL. Le gabarit centre toujours la vue sur x = 0 et ne reglait
#    que la hauteur (VCY). Les signatures de la foudre sont des colonnes verticales, donc
#    cela suffisait. Le Siphon, lui, est un conduit de 7 blocs tendu a l'HORIZONTALE entre
#    la cible et le porteur : centre sur zero, il sortait du cadre par la gauche. On ajoute
#    donc un centre horizontal, VCX, symetrique de VCY.
PROJ_OLD = "function project(x,y,z){const yc=y-VCY,rx=x*CY-z*SY,rz=x*SY+z*CY,ry=yc*CP-rz*SP;"
PROJ_NEW = "function project(x,y,z){const yc=y-VCY,xc=x-VCX,rx=xc*CY-z*SY,rz=xc*SY+z*CY,ry=yc*CP-rz*SP;"
assert RENDER.count(PROJ_OLD) == 1, 'projection introuvable'
RENDER = RENDER.replace(PROJ_OLD, PROJ_NEW)
assert RENDER.count("let VCY=1.05;") == 1, 'declaration VCY introuvable'
RENDER = RENDER.replace("let VCY=1.05;", "let VCY=1.05, VCX=0;")

# --- 3. LES DEUX CORPS. Un conduit entre deux points vides ne se lit pas : on dessine la
#     victime et le porteur en fil de fer, avec leur nom. Decor d'apercu, comme la grille.
ACT_ANCHOR = "function glow(vox){ctx.save();ctx.globalCompositeOperation='lighter';"
ACT_NEW = """/* REPERES D'APERCU — ils ne font pas partie de la signature. En jeu, ces deux places
   sont occupees par la victime et par le porteur ; ici, deux silhouettes en fil de fer
   disent ou commence et ou finit l'effet. */
function wireBox(x,y,z,hx,hy,hz){
 const c=[];
 for(let i=0;i<8;i++)c.push(project(x+((i&1)?hx:-hx),y+((i&2)?hy:-hy),z+((i&4)?hz:-hz)));
 const E=[[0,1],[2,3],[4,5],[6,7],[0,2],[1,3],[4,6],[5,7],[0,4],[1,5],[2,6],[3,7]];
 ctx.beginPath();
 for(const e of E){ctx.moveTo(c[e[0]][0],c[e[0]][1]);ctx.lineTo(c[e[1]][0],c[e[1]][1]);}
 ctx.stroke();
}
function figure(x,tall,label,tint){
 const F=-0.86;                       /* les pieds, sur le meme sol que les flaques */
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
function actors(){
 if(variant===2||variant===3){
  figure(0,1.95,'VICTIME','#7a3a4e');
  figure(-7,1.80,'PORTEUR','#4a5a8c');
 } else figure(0,1.95,'VICTIME','#7a3a4e');
}
function glow(vox){ctx.save();ctx.globalCompositeOperation='lighter';"""
assert RENDER.count(ACT_ANCHOR) == 1, 'ancre glow introuvable'
RENDER = RENDER.replace(ACT_ANCHOR, ACT_NEW)
assert RENDER.count(" ground();\n") == 1, 'appel ground() introuvable'
RENDER = RENDER.replace(" ground();\n", " ground();\n actors();\n")

# --- 4. UN ANGLE PAR VARIANTE. Le siphon et la transfusion sont des liens HORIZONTAUX :
#     a 0,62 radian ils fuient vers le fond et se lisent comme une saucisse en diagonale.
YAW_OLD = """document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;syncPanel();});"""
YAW_NEW = """const YAW_BY=[0.62,0.62,0.16,0.22,0.62], PITCH_BY=[0.30,0.30,0.14,0.20,0.30];
document.querySelectorAll('.variant').forEach(b=>b.onclick=()=>{
 variant=+b.dataset.variant;t=0;
 camYaw=YAW_BY[variant];camPitch=PITCH_BY[variant];zoom=1;
 syncPanel();});"""
assert RENDER.count(YAW_OLD) == 1, 'gestionnaire de variante introuvable'
RENDER = RENDER.replace(YAW_OLD, YAW_NEW)

ZIP = 'deliverables/DLB_Blood_Five_Signatures_src.zip'
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
<title>Deep Lucky Block — Sang, cinq signatures</title>
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
  <div class="eyebrow">Deep Lucky Block · Blood VFX</div>
  <div class="build">T379 · 4 novembre 2026 — onzième famille · la première à CINQ \
signatures · échafaud · cœur anatomique · frénésie</div>\
  <h1>SANG</h1><h2>Cinq signatures</h2>
  <p class="intro">Refonte complète de l’élément sang, et la première famille du mod à \
porter <b>cinq</b> signatures : une exécution, une coupe, un siphon, une transfusion et une \
frénésie. Un seul moteur voxel (<code>BloodSignatureVfx</code>), zéro RenderType \
supplémentaire. <b>Les trois règles qui gouvernent ce fichier :</b> rien ne flotte — toute \
goutte tombe sous une gravité réelle de 11 blocs/s² ; toute nappe porte son <b>ménisque</b>, \
le liseré clair sans lequel un liquide n’est qu’une tache plate ; et le rouge ne se lit \
contre lui-même que si une matière étrangère le borde — le fer, le bois, l’os.</p>\
  <div class="rule"></div>
  <span class="label">Signature de l’enchantement</span>
  <div class="variants" role="group" aria-label="Variante Sang">
    <button class="variant active" data-variant="0"><span class="num">Ⅰ</span><span>\
<b>Couperet</b><small>7,5 s · échafaud · chute en 10 ticks</small></span></button>
    <button class="variant" data-variant="1"><span class="num">Ⅱ</span><span>\
<b>Tranchant Sanglant</b><small>6,0 s · zone en ligne · rideau de gouttes</small>\
</span></button>
    <button class="variant" data-variant="2"><span class="num">Ⅲ</span><span>\
<b>Siphon Carmin</b><small>8,5 s · on pique, le verre se remplit</small></span></button>
    <button class="variant" data-variant="3"><span class="num">Ⅳ</span><span>\
<b>Transfusion Vitale</b><small>7,0 s · cœur · soigne les alliés</small></span></button>
    <button class="variant" data-variant="4"><span class="num">Ⅴ</span><span>\
<b>Carnage Berserk</b><small>9,0 s · 11 taillades · frénésie</small></span></button>
  </div>
  <span class="label">Chorégraphie du modèle Java</span>
  <div class="controls"><button class="btn primary" id="play">Pause</button>\
<button class="btn" id="replay">Rejouer</button>\
<button class="btn" id="dice">Graine</button></div>
  <input class="timeline" id="timeline" type="range" min="0" max="1000" value="0" \
aria-label="Progression de l’animation">
  <div class="phase" id="phase">01 — l’échafaud</div>
  <div class="stats"><div class="stat"><b id="baseCount">0</b><span>Voxels</span></div>\
<div class="stat"><b id="duration">7,5 s</b><span>Durée en jeu</span></div>\
<div class="stat"><b id="seedStat">5099</b><span>Graine</span></div></div>
  <div class="legend"><span style="--c:#2B0507">Caillot</span>\
<span style="--c:#5E0A10">Veineux</span><span style="--c:#8E0F16">Chair</span>\
<span style="--c:#C2121B">Artériel</span><span style="--c:#E8252C">Frais</span>\
<span style="--c:#FF5A52">Giclure</span><span style="--c:#FFB9A8">Plasma</span>\
<span style="--c:#6E7279">Fer</span><span style="--c:#4A3426">Bois</span>\
<span style="--c:#E8E0CC">Os</span></div>

  <p class="refs" id="beatsRefs"></p>
  <p class="refs" id="gpRefs"></p>
  <p class="refs" id="sfxRefs"></p>
  <p class="refs"><b>Moteur :</b> <code>BloodSignatureVfx</code> — Couperet (#5), \
Tranchant Sanglant (#9), Siphon Carmin (#10), Transfusion Vitale (#11), Carnage Berserk \
(#12). Les pièces sont émises par les émetteurs voxel du manager : aucun RenderType \
supplémentaire. <b>Attention :</b> l’aperçu ne modélise ni le tamis de densité, ni le \
voile d’alpha, ni le plafond de 7 000 cubes du moteur — il sert à relire la chorégraphie, \
<b>pas à la valider en jeu</b>.<br>Tests : <b>/anim sang</b> · <b>/dlbvfx play 5</b> · \
<b>/dlbvfx play 9</b> · <b>/dlbvfx play 10</b> · <b>/dlbvfx play 11</b> · \
<b>/dlbvfx play 12</b> · <b>/dlbvfx stop</b></p>

  <a class="download" id="srcZip" href="{URL}" download="{ZIP_NAME}" rel="noopener">\
Télécharger les sources Sang · ZIP</a>
  <div class="zipnote">ZIP autonome embarqué dans cette page — {ENTRIES} entrées, \
{len(raw)} octets, SHA-256 <code>{SHA256}</code>. Si l’aperçu intégré bloque le \
téléchargement, ouvre cette page dans un onglet du navigateur ou récupère le même fichier \
dans <code>deliverables/</code>.</div>
</aside>
<main>
  <canvas id="scene" tabindex="0" aria-label="Aperçu animé des cinq signatures Sang">\
</canvas>
  <div class="topbar"><span class="chip live">Modèle Java T379 · BloodSignatureVfx</span>\
<span class="right"><a class="chip zipchip" id="srcZipTop" href="{URL}" \
download="{ZIP_NAME}" rel="noopener">ZIP sources</a>\
<span class="chip" id="visibleCount">0 voxels visibles</span></span></div>
  <div class="hint">Glisser pour orbiter · Molette pour zoomer · Espace pour pause</div>
  <div class="variant-title"><b id="variantName">Couperet</b>\
<span id="variantSub">échafaud de 4,6 blocs · couperet de 1,4 · clouage</span></div>
</main>
<script>
/* ===== Moteur de géométrie — inliné depuis tools/blood_build.js (miroir du .java) ===== */
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

out = 'deliverables/DLB_Blood_Five_Signatures_preview.html'
io.open(out, 'w', encoding='utf-8').write(PAGE)
print(f'{out} : {os.path.getsize(out)} o  (ZIP embarqué : {len(raw)} o, {ENTRIES} entrées)')
