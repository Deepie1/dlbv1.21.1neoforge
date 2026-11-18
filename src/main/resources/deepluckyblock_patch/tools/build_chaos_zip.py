#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Chaos_Three_Signatures_src.zip.

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
]

README = """DLB - CHAOS : TROIS SIGNATURES, ET LES PIXELS 3D (T397)
======================================================

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
  ChaosSignatureVfx.java             le moteur voxel des trois signatures
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
    out = 'deliverables/DLB_Chaos_Three_Signatures_src.zip'
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
