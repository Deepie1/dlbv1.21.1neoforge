#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Chaos_Five_Signatures_src.zip.

REGLES DU LIVRABLE, apprises a la dure :

  * JAMAIS de build.gradle dans l'archive. Il ecraserait le bloc de patch que l'utilisateur
    maintient a la main dans son projet MCreator.
  * Rien hors de src/, a la seule exception de LISEZ-MOI-CONTENU.txt.
  * Les deux copies de chaque .java (source et miroir FIXES_MCREATOR) doivent etre
    IDENTIQUES au moment de l'empaquetage. Toute modification posterieure a une resynchro
    desynchronise les miroirs en silence : d'ou l'assert ci-dessous, qui refuse de produire
    une archive incoherente plutot que de la livrer.
"""
import hashlib
import io
import os
import sys
import zipfile

MIRROR = 'src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/'
JAVA = [
    # T397-CHAOS : les deux fichiers NOUVEAUX de cette livraison.
    'src/main/java/deepluckyblock/client/ChaosSignatureVfx.java',
    'src/main/java/deepluckyblock/client/VoidPixelManager.java',
    # T397-PIXEL3D : les deux fichiers d'infrastructure patches pour le nouveau shader.
    'src/main/java/deepluckyblock/client/ModShaders.java',
    'src/main/java/deepluckyblock/client/ModRenderTypes.java',
    # Les points d'accroche.
    'src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java',
    'src/main/java/deepluckyblock/util/AnimFamily.java',
    # Le contexte indispensable pour compiler les precedents.
    'src/main/java/deepluckyblock/client/VoidSignatureVfx.java',
    'src/main/java/deepluckyblock/client/VoidPortalFx.java',
    'src/main/java/deepluckyblock/client/BloodSignatureVfx.java',
    'src/main/java/deepluckyblock/client/ShadowSignatureVfx.java',
    'src/main/java/deepluckyblock/client/ThunderSignatureVfx.java',
    'src/main/java/deepluckyblock/client/WaterSignatureVfx.java',
    'src/main/java/deepluckyblock/client/WaterTerrainVfx.java',
    'src/main/java/deepluckyblock/client/GaiaWorldTreeVfx.java',
    'src/main/java/deepluckyblock/client/WeaponBladeVisualRenderer.java',
    'src/main/java/deepluckyblock/client/CosmeticPixelAura.java',
    'src/main/java/deepluckyblock/client/EnchantGlintTyped.java',
    'src/main/java/deepluckyblock/client/DeepGlintColor.java',
    'src/main/java/deepluckyblock/client/EnchantAura.java',
    'src/main/java/deepluckyblock/commands/DlbVfxCommand.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxMotion.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxSubtle.java',
    'src/main/java/deepluckyblock/enchantments/DeepElement.java',
    'src/main/java/deepluckyblock/enchantments/GhostBlockMask.java',
    'src/main/java/deepluckyblock/enchantments/PixelFx.java',
    # T410-SKILLS : les quarante-cinq signatures deviennent des competences au clic droit.
    'src/main/java/deepluckyblock/enchantments/WeaponSkill.java',
    'src/main/java/deepluckyblock/enchantments/WeaponSkillHandler.java',
    'src/main/java/deepluckyblock/client/EnchantmentTooltipHandler.java',
    'src/main/java/deepluckyblock/client/ClientTranslationInjector.java',
    # T410-FIX : sans create(), les 14 enchantements neufs n'ont pas de metadonnees
    # et tout le mod les ignore. Le JSON seul ne suffit pas.
    'src/main/java/deepluckyblock/enchantments/ModEnchantments.java',
    # T411-SKILLS : les capacites activees quittent la liste d'enchantements pour
    # l'encart SKILL, et 5 % des armes lootees repartent avec une competence.
    # Ces trois fichiers portent le changement : sans eux l'archive livre l'ancien
    # comportement, meme si le depot, lui, est a jour.
    'src/main/java/deepluckyblock/procedures/EnchantmentTooltipFixProcedure.java',
    'src/main/java/deepluckyblock/procedures/GenerateLuckyItemProcedure.java',
    'src/main/java/deepluckyblock/enchantments/UniversalEnchantmentHandler.java',
]
EXTRA = [
    # T397-PIXEL3D : les trois fichiers du core shader. Ils ne vivent PAS sous
    # deepluckyblock_patch mais a leur emplacement reel dans assets/ : c'est la que le
    # chargeur de ressources les cherche, et un asset range ailleurs ne se charge pas.
    'src/main/resources/assets/deep_lucky_block/shaders/core/void_pixel.json',
    'src/main/resources/assets/deep_lucky_block/shaders/core/void_pixel.vsh',
    'src/main/resources/assets/deep_lucky_block/shaders/core/void_pixel.fsh',
    # Le shader de la famille precedente, parce que ModShaders et ModRenderTypes
    # enregistrent maintenant LES DEUX : livrer l'un sans l'autre produirait un mod qui
    # refuse de demarrer sur un asset introuvable.
    'src/main/resources/assets/deep_lucky_block/shaders/core/void_portal.json',
    'src/main/resources/assets/deep_lucky_block/shaders/core/void_portal.vsh',
    'src/main/resources/assets/deep_lucky_block/shaders/core/void_portal.fsh',
    'src/main/resources/assets/deep_lucky_block/textures/effect/void_sky.png',
    'src/main/resources/assets/deep_lucky_block/textures/effect/void_stars.png',
    'src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md',
    'src/main/resources/deepluckyblock_patch/LIRE-MOI-IMPORTANT.md',
    'src/main/resources/deepluckyblock_patch/tools/semcheck.py',
    'src/main/resources/deepluckyblock_patch/tools/floatcheck.py',
    # glslcheck : il attrape ce que la demo navigateur ne peut pas voir, c'est-a-dire les
    # mots reserves par GLSL 1.50 (le jeu) et pas par GLSL ES 3.00 (le navigateur).
    'src/main/resources/deepluckyblock_patch/tools/glslcheck.py',
    'src/main/resources/deepluckyblock_patch/tools/typecheck.py',
    'src/main/resources/deepluckyblock_patch/tools/typecheck-baseline.txt',
    'src/main/resources/deepluckyblock_patch/tools/soundevents_1211.json',
    'src/main/resources/deepluckyblock_patch/tools/chaos_build.js',
    'src/main/resources/deepluckyblock_patch/tools/void_build.js',
    'src/main/resources/deepluckyblock_patch/tools/blood_build.js',
    'src/main/resources/deepluckyblock_patch/tools/preview_base.css',
    'src/main/resources/deepluckyblock_patch/tools/preview_render.js',
    'src/main/resources/deepluckyblock_patch/tools/build_chaos_page.py',
    'src/main/resources/deepluckyblock_patch/tools/build_chaos_zip.py',
    'src/main/resources/deepluckyblock_patch/tools/build_pixel_shader_demo.py',
    'src/main/resources/deepluckyblock_patch/tools/build_void_page.py',
    'src/main/resources/deepluckyblock_patch/tools/build_void_zip.py',
    'src/main/resources/deepluckyblock_patch/tools/deep-lucky-block-patch.gradle',
    # T410-SKILLS : quatorze signatures n'avaient AUCUN enchantement porteur. Sans ces
    # quatorze fichiers, leur competence est injouable : rien ne peut l'amener sur une arme.
    'src/main/resources/data/deep_lucky_block/enchantment/feigenbaum_cascade.json',
    'src/main/resources/data/deep_lucky_block/enchantment/sandpile_critical.json',
    'src/main/resources/data/deep_lucky_block/enchantment/titan_grasp.json',
    'src/main/resources/data/deep_lucky_block/enchantment/miasma_shroud.json',
    'src/main/resources/data/deep_lucky_block/enchantment/plague_nova.json',
    'src/main/resources/data/deep_lucky_block/enchantment/eclipse_veil.json',
    'src/main/resources/data/deep_lucky_block/enchantment/null_rift.json',
    'src/main/resources/data/deep_lucky_block/enchantment/horizon_gate.json',
    'src/main/resources/data/deep_lucky_block/enchantment/chain_collapse.json',
    'src/main/resources/data/deep_lucky_block/enchantment/sandstorm_dune.json',
    'src/main/resources/data/deep_lucky_block/enchantment/tempest.json',
    'src/main/resources/data/deep_lucky_block/enchantment/thorn_maw.json',
    'src/main/resources/data/deep_lucky_block/enchantment/verdant_sap_bloom.json',
    'src/main/resources/data/deep_lucky_block/enchantment/wildwood_awakening.json',
]

README = """DLB - SOURCES DU MOD : L ARCHIVE UNIQUE
======================================

Cette archive est la seule a telecharger. Elle porte toujours le meme nom
(DLB_src.zip) et contient a chaque fois l integralite des fichiers a jour.

------------------------------------------------------------------------------
DERNIERE LIVRAISON : LES ANIMATIONS DEVIENNENT DES COMPETENCES  (T410)
------------------------------------------------------------------------------
Fichiers nouveaux :
    src/main/java/deepluckyblock/enchantments/WeaponSkill.java
    src/main/java/deepluckyblock/enchantments/WeaponSkillHandler.java
    src/main/resources/data/deep_lucky_block/enchantment/  (14 fichiers)
Fichiers a remplacer :
    src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java
    src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java
    src/main/java/deepluckyblock/client/ClientTranslationInjector.java
    src/main/java/deepluckyblock/client/EnchantmentTooltipHandler.java

1. ON NE SUBIT PLUS LES GRANDES ANIMATIONS, ON LES LANCE.
   Avant, une grosse animation tombait toute seule, sur trois pour cent des
   coups d epee. Ce tirage n existe plus. Les quarante-cinq signatures sont
   devenues des COMPETENCES : arme de melee en main, CLIC DROIT SUR UN MOB.
   Pas de touche a retenir, pas de shift. Le clic droit dans le vide ne change
   pas : il tend toujours l arc et mange toujours le pain.

2. CHAQUE COMPETENCE A SA RECHARGE, ET ELLE EST CALCULEE.
   Aucune valeur n a ete choisie a la main. La formule est la meme pour les
   quarante-cinq, a partir de ce que fait vraiment l animation :
       charge   = nombre de coups portes + duree de l animation / 40 ticks
       recharge = 30 s + 2 s par point de charge, plafonnee a 90 s
   Une animation qui frappe souvent ET longtemps coute donc plus cher. En
   pratique les recharges vont de 32 a 60 secondes. Elle est ecrite en toutes
   lettres sur l objet, dans la description de la competence.

3. L ETAT SE LIT SUR L ARME, PAS DANS UNE BARRE.
   Aucun voile gris, aucune ligne  pret . Quand la competence est chargee, le
   halo de l arme change : il prend la couleur de son element, a l intensite 13
   de l echelle de /glint, tres au-dessus du 4 habituel. Une fois lancee, le
   halo redevient exactement ce qu il etait - y compris un /glint pose a la
   main, qui est mis de cote et rendu intact.

4. LES GRANDES ANIMATIONS NE SONT PAS PERDUES POUR LA DISTANCE.
   Elles passent a l arc, a l arbalete et au trident lance, ou elles gardent
   leur ancien role : cinq pour cent des impacts, avec le bonus de degats et
   l effet elementaire. La signature jouee est celle des enchantements de l ARME
   DE TIR, jamais ceux d une epee rangee dans l inventaire.

5. QUATORZE ENCHANTEMENTS QUI MANQUAIENT.
   Quatorze signatures sur quarante-cinq n avaient aucun enchantement capable de
   les porter : elles etaient donc injouables. Les fichiers sont crees, avec une
   rarete qui suit la puissance (poids 10 pour les recharges courtes, 5 au-dela
   de 40 s, 2 au-dela de 50 s).

6. LES TEXTES SONT DANS LE FICHIER MCREATOR, PAS DANS LES JSON DE LANGUE.
   Les quatre-vingt-dix lignes francaises et les quatre-vingt-dix anglaises sont
   posees dans ClientTranslationInjector, APRES la lecture des fichiers de
   langue, donc elles gagnent sur eux. Un nouvel export MCreator ne les efface
   plus.

NON VERIFIE : javac est absent de l environnement, les bancs (semcheck,
floatcheck, typecheck) ne valent donc pas une compilation, et rien n a pu etre
observe en jeu.

------------------------------------------------------------------------------
CONTENU HISTORIQUE : CHAOS, LES SIGNATURES ET LES PIXELS 3D (T397+)
------------------------------------------------------------------------------

Treizieme et derniere famille refondue. Les trois signatures du Chaos passent de 18-22
ticks a 150/170/200, avec un moteur voxel dedie, un gameplay serveur complet, une
ressource partagee (la discordance) et un CORE SHADER maison : void_pixel.

CE QUE CONTIENT CETTE ARCHIVE
-----------------------------
  src/main/java/...                  les sources, a copier dans ton projet
  src/.../FIXES_MCREATOR/...         la copie miroir, identique fichier pour fichier
  src/main/resources/assets/...      les assets des shaders, A LEUR EMPLACEMENT REEL
  src/.../WATER_SIGNATURES.md        le journal technique
  src/.../tools/                     les outils de controle et le miroir JS de l'apercu

CE QU'ELLE NE CONTIENT PAS, ET POURQUOI
---------------------------------------
Pas de build.gradle. Il ecraserait le bloc de patch que tu maintiens a la main.
Rien hors de src/, a part ce fichier.

LES DEUX FICHIERS JAVA NOUVEAUX
-------------------------------
  ChaosSignatureVfx.java             le moteur voxel des cinq signatures
  VoidPixelManager.java              LE SYSTEME DE PIXELS 3D : tableaux primitifs,
                                     physique au tick, rendu en UN SEUL lot

                                     tous les deux dans deepluckyblock/client/

LES TROIS ASSETS NOUVEAUX
-------------------------
  assets/deep_lucky_block/shaders/core/void_pixel.json
  assets/deep_lucky_block/shaders/core/void_pixel.vsh
  assets/deep_lucky_block/shaders/core/void_pixel.fsh

  ATTENTION : le JSON ecrit "deep_lucky_block:void_pixel" dans ses champs vertex et
  fragment. Ce n'est pas une coquetterie - vanilla ne cherche que dans minecraft:, et
  c'est NeoForge qui patche le chargement pour accepter un namespace. Sans le prefixe,
  le jeu cherche minecraft:shaders/core/void_pixel.vsh et echoue au demarrage.

LE SYSTEME DE PIXELS 3D, EN BREF
--------------------------------
Un Pixel 3D est un cube miniature (0,08 a 0,20 bloc) dont le coeur est anthracite
#23232A a 65 % d'opacite et dont les DOUZE ARETES defilent en arc-en-ciel fullbright.

  * Les aretes ne sont PAS de la geometrie. Chaque cube n'a que ses six faces, soit 24
    sommets ; le fragment shader detecte le bord dans les UV de face et les quatre bords
    de six faces donnent exactement douze aretes. Une arete modelisee aurait coute huit
    fois plus de sommets.
  * Fullbright par construction : le programme ne recoit ni lightmap ni overlay.
  * Treize tableaux primitifs paralleles, 12 000 cubes, ZERO allocation a l'execution,
    retrait par echange avec le dernier vivant en O(1).
  * Un seul lot de rendu, a l'etape AFTER_TRANSLUCENT_BLOCKS - choix delibere contre
    AFTER_PARTICLES : a cette etape l'eau et le verre sont deja dans le tampon de
    profondeur, donc un cube derriere une vitre est correctement masque.
  * La phase de l'onde est calculee cote CPU, UNE FOIS PAR CUBE, parce que l'attribut
    Position d'un core shader est RELATIF A LA CAMERA : la calculer dans le shader aurait
    fait glisser tout l'arc-en-ciel des que le joueur marche. Ce n'est pas une couleur
    qui est calculee cote CPU, c'est un scalaire de position.

  API :  VoidPixelManager.spawnPixel(x, y, z, vx, vy, vz, size, lifetimeTicks)
         VoidPixelManager.spawnPixel(..., int mode, float hue, float gravity)
         modes : MODE_RAINBOW = 0, MODE_TINTED = 1, MODE_MONO = 2

LES POINTS D'ACCROCHE
---------------------
  WeaponWorldVfxManager   durees 150/170/200, isChaosSignature, impactChaosSignature,
                          la voix de swing du Chaos, la rotation /anim
  WeaponVfxServerListener applyChaosGameplay + les trois signatures serveur,
                          la discordance (CHAOS_DISCORDS), l'entropie (CHAOS_ENTROPIES),
                          le pool ANIM_CHAOS et le balayage des deux cartes
  AnimFamily              le Chaos devient la TREIZIEME famille : /anim chaos

A TESTER EN JEU
---------------
  /anim chaos  ·  /dlbvfx play 35  ·  36  ·  37  ·  /dlbvfx stop

  Pour la Sphere, comparer RESTER et SORTIR au bout de trois pulsations : c'est tout le
  sujet de la signature, et c'est la seule du mod ou partir est la bonne reponse.

  Pour le Big Bang, comparer UN corps seul et SIX corps serres - la prime de foule est de
  douze pour cent par corps, plafonnee a six.

  Pour les Pixels 3D : les regarder DE NUIT et DANS UNE GROTTE. Les aretes doivent briller
  exactement pareil. Puis F3+T pendant qu'une signature est a l'ecran : le shader doit se
  recharger sans crash - c'est pour cela que ModShaders garde l'instance du callback et
  jamais la candidate.
"""


def main():
    # UNE SEULE ARCHIVE, UN SEUL NOM, POUR TOUJOURS.
    # L'utilisateur a demande le 4 novembre 2026 d'arreter de multiplier les archives et
    # les formes de liens : il veut un fichier unique, toujours au meme nom, qui contient
    # tout ce dont il a besoin. Ne pas reintroduire de nom date ou thematique ici.
    out = 'deliverables/DLB_src.zip'
    os.makedirs('deliverables', exist_ok=True)
    problems = []

    for rel in JAVA:
        mirror = MIRROR + rel
        if not os.path.exists(rel):
            problems.append('source absente : ' + rel)
            continue
        if not os.path.exists(mirror):
            problems.append('miroir absent : ' + mirror)
            continue
        if io.open(rel, 'rb').read() != io.open(mirror, 'rb').read():
            problems.append('MIROIR DESYNCHRONISE : ' + rel)

    for rel in EXTRA:
        if not os.path.exists(rel):
            problems.append('fichier absent : ' + rel)

    if problems:
        for p in problems:
            print('  ! ' + p)
        print('ARCHIVE NON PRODUITE — corriger puis relancer.')
        return 1

    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel in JAVA:
            z.write(rel, rel)
        for rel in JAVA:
            z.write(MIRROR + rel, MIRROR + rel)
        for rel in EXTRA:
            z.write(rel, rel)
        z.writestr('LISEZ-MOI-CONTENU.txt', README)

    raw = io.open(out, 'rb').read()
    with zipfile.ZipFile(out) as z:
        names = z.namelist()
    bad = [n for n in names if n != 'LISEZ-MOI-CONTENU.txt' and not n.startswith('src/')]
    assert not bad, 'chemins hors src/ : %r' % bad
    assert not [n for n in names if n.endswith('build.gradle')], 'build.gradle dans l archive'
    print('%s : %d entrees, %d octets' % (out, len(names), len(raw)))
    print('SHA-256 %s' % hashlib.sha256(raw).hexdigest())
    return 0


if __name__ == '__main__':
    sys.exit(main())
