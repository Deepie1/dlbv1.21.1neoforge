#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Void_Five_Signatures_src.zip.

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
    # T388-VOID : le fichier nouveau de cette livraison.
    'src/main/java/deepluckyblock/client/VoidSignatureVfx.java',
    'src/main/java/deepluckyblock/client/BloodSignatureVfx.java',
    'src/main/java/deepluckyblock/client/ShadowSignatureVfx.java',
    'src/main/java/deepluckyblock/client/ThunderSignatureVfx.java',
    'src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java',
    'src/main/java/deepluckyblock/client/WeaponBladeVisualRenderer.java',
    'src/main/java/deepluckyblock/client/GaiaWorldTreeVfx.java',
    'src/main/java/deepluckyblock/client/WaterSignatureVfx.java',
    'src/main/java/deepluckyblock/client/WaterTerrainVfx.java',
    # T378-NO-JITTER : l'aura cosmetique emet elle aussi des cubes, hors passe VFX.
    'src/main/java/deepluckyblock/client/CosmeticPixelAura.java',
    # T378-UMBRAL-GLINT : le glint melange de l'ombre touche ces trois fichiers.
    'src/main/java/deepluckyblock/client/EnchantGlintTyped.java',
    'src/main/java/deepluckyblock/client/DeepGlintColor.java',
    'src/main/java/deepluckyblock/client/EnchantAura.java',
    'src/main/java/deepluckyblock/commands/DlbVfxCommand.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxMotion.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxSubtle.java',
    'src/main/java/deepluckyblock/enchantments/DeepElement.java',
    'src/main/java/deepluckyblock/enchantments/GhostBlockMask.java',
    'src/main/java/deepluckyblock/enchantments/PixelFx.java',
    'src/main/java/deepluckyblock/util/AnimFamily.java',
]
EXTRA = [
    'src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md',
    'src/main/resources/deepluckyblock_patch/LIRE-MOI-IMPORTANT.md',
    'src/main/resources/deepluckyblock_patch/tools/semcheck.py',
    'src/main/resources/deepluckyblock_patch/tools/soundevents_1211.json',
    'src/main/resources/deepluckyblock_patch/tools/typecheck.py',
    'src/main/resources/deepluckyblock_patch/tools/typecheck-baseline.txt',
    'src/main/resources/deepluckyblock_patch/tools/void_build.js',
    'src/main/resources/deepluckyblock_patch/tools/blood_build.js',
    'src/main/resources/deepluckyblock_patch/tools/shadow_build.js',
    'src/main/resources/deepluckyblock_patch/tools/thunder_build.js',
    'src/main/resources/deepluckyblock_patch/tools/water_build.js',
    'src/main/resources/deepluckyblock_patch/tools/preview_base.css',
    'src/main/resources/deepluckyblock_patch/tools/preview_render.js',
    'src/main/resources/deepluckyblock_patch/tools/build_shadow_page.py',
    'src/main/resources/deepluckyblock_patch/tools/build_shadow_zip.py',
    'src/main/resources/deepluckyblock_patch/tools/build_blood_page.py',
    'src/main/resources/deepluckyblock_patch/tools/build_blood_zip.py',
    'src/main/resources/deepluckyblock_patch/tools/build_void_page.py',
    'src/main/resources/deepluckyblock_patch/tools/build_void_zip.py',
    'src/main/resources/deepluckyblock_patch/tools/deep-lucky-block-patch.gradle',
]

README = """DLB — VIDE, TROIS SIGNATURES (T388)
==================================================

Douzieme famille refondue. Les trois signatures du Vide passent de 16-22 ticks a
160/190/170, avec un moteur voxel dedie, un gameplay serveur complet et une ressource
partagee : l'empreinte du neant.

CE QUE CONTIENT CETTE ARCHIVE
-----------------------------
  src/main/java/...                  les sources, a copier dans ton projet
  src/.../FIXES_MCREATOR/...         la copie miroir, identique fichier pour fichier
  src/.../WATER_SIGNATURES.md        le journal technique — le Vide est au SS 48
  src/.../tools/                     les outils de controle et le miroir JS de l'apercu

CE QU'ELLE NE CONTIENT PAS, ET POURQUOI
---------------------------------------
Pas de build.gradle. Il ecraserait le bloc de patch que tu maintiens a la main.
Rien hors de src/, a part ce fichier.

LE FICHIER NOUVEAU
------------------
  VoidSignatureVfx.java              n'existe pas encore chez toi : a creer dans
                                     deepluckyblock/client/

LES CINQ POINTS D'ACCROCHE
--------------------------
  1. AnimFamily.java                 la constante VOID, ALL, canonical, glint, couleur, nom
  2. WeaponWorldVfxManager           animationSlashEffect : la branche de famille. SANS
                                     ELLE, le vide retombe sur le BOIS et le petit arc du
                                     swing sort en BRUN.
  3. WeaponWorldVfxManager           impactVoid devient un aiguillage : les trois ids
                                     signataires partent vers VoidSignatureVfx, le reste
                                     garde impactVoidLegacy.
  4. WeaponWorldVfxManager l.~777    les trois durees dediees (160/190/170). Le prisme
                                     etait jusqu'ici dans le meme case que BIG_BANG_SCAR
                                     et la faille dans celui de CHAOS_KALEIDO_LOOP : il
                                     faut les EN SORTIR, pas seulement changer le chiffre.
  5. WeaponVfxServerListener         ANIM_VOID + applyVoidGameplay, cable en 3 endroits

L'EMPREINTE DU NEANT, LA RESSOURCE PARTAGEE
-------------------------------------------
Quatre empreintes au maximum. Elle ne fait AUCUN degat par elle-meme : c'est un SEUIL.
A la quatrieme, l'espace cede — implosion, soulevement (Levitation), Lenteur II, et un
echo banque au porteur. Le halo compte a voix haute : une graine de poussiere cyan au-
dessus de la tete par empreinte posee, pour qu'on sache qui est a 3 sur 4.

Toute empreinte porte une ECHEANCE ABSOLUE calculee au demarrage de la signature : aucun
appel ne peut la repousser. C'est la lecon de l'hemorragie sans fin (T387), appliquee
avant d'avoir le bogue.

LA MOBILITE, ET PAS LES DEGATS
------------------------------
Au 3e et au 7e echo, le porteur gagne Vitesse et Chute Lente — jamais de Force. La
ferveur du Sang recompense deja l'acharnement par des degats ; refaire la meme chose ici
aurait donne deux fois la meme famille. Le Vide deplace, donc il paie en terrain. Les
echos se periment en 200 ticks : c'est l'ENCHAINEMENT qui paie, pas l'accumulation.

TESTS EN JEU
------------
  /anim void        puis frapper un mob entoure d'autres, puis un mob SEUL
  /dlbvfx play 26   Sphere de Distorsion     — visuel seul
  /dlbvfx play 27   Prisme du Dragon         — visuel seul
  /dlbvfx play 34   Faille du Neant          — visuel seul
  /dlbvfx stop

  Pour la Faille, se placer DANS l'axe de la dechirure puis a deux pas de cote : c'est la
  seule aire du mod ou le placement lateral decide.
"""

def main():
    out = 'deliverables/DLB_Void_Five_Signatures_src.zip'
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
        a = io.open(rel, 'rb').read()
        b = io.open(mirror, 'rb').read()
        if a != b:
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
    bad = [n for n in names
           if n != 'LISEZ-MOI-CONTENU.txt' and not n.startswith('src/')]
    assert not bad, 'chemins hors src/ : %r' % bad
    assert not [n for n in names if n.endswith('build.gradle')], 'build.gradle dans l archive'
    print('%s : %d entrees, %d octets' % (out, len(names), len(raw)))
    print('SHA-256 %s' % hashlib.sha256(raw).hexdigest())
    return 0


if __name__ == '__main__':
    sys.exit(main())
