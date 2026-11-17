#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Blood_Five_Signatures_src.zip.

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
    # T379-BLOOD : le fichier nouveau de cette livraison.
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
    'src/main/resources/deepluckyblock_patch/tools/typecheck.py',
    'src/main/resources/deepluckyblock_patch/tools/typecheck-baseline.txt',
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
    'src/main/resources/deepluckyblock_patch/tools/deep-lucky-block-patch.gradle',
]

README = """DLB — SANG, CINQ SIGNATURES (T379)
==================================================

Onzieme famille refondue, et la premiere a CINQ signatures. Les cinq passent de 14-18
ticks a 150/120/170/140/180, avec un moteur voxel dedie, un gameplay serveur complet et
une ressource partagee : l'hemorragie.

CE QUE CONTIENT CETTE ARCHIVE
-----------------------------
  src/main/java/...                  les sources, a copier dans ton projet
  src/.../FIXES_MCREATOR/...         la copie miroir, identique fichier pour fichier
  src/.../WATER_SIGNATURES.md        le journal technique — le Sang est aux SS 39 et 40
  src/.../tools/                     les outils de controle et le miroir JS de l'apercu

CE QU'ELLE NE CONTIENT PAS, ET POURQUOI
---------------------------------------
Pas de build.gradle. Il ecraserait le bloc de patch que tu maintiens a la main.
Rien hors de src/, a part ce fichier.

LE FICHIER NOUVEAU
------------------
  BloodSignatureVfx.java             n'existe pas encore chez toi : a creer dans
                                     deepluckyblock/client/

LES QUATRE POINTS D'ACCROCHE
----------------------------
  1. AnimFamily.java                 la constante BLOOD, ALL, canonical, glint, couleur, nom
  2. WeaponWorldVfxManager           la branche de famille. SANS ELLE, le sang retombe
                                     sur le BOIS et sort en BRUN. Le tirage client doit
                                     passer a CINQ signatures, pas trois.
  3. WeaponWorldVfxManager l.~688    les cinq durees dediees (150/120/170/140/180)
  4. WeaponVfxServerListener         ANIM_BLOOD + applyBloodGameplay, cable en 4 endroits

L'HEMORRAGIE, LA RESSOURCE PARTAGEE
-----------------------------------
Cinq piles au maximum, un tic toutes les 10 ticks. Elle PUNIT LA FUITE : les degats sont
multiplies par (1 + 1,5 x min(1, vitesse / 0,22)), soit jusqu'a deux fois et demie pour
une cible qui court. Rester immobile est la seule facon de la subir a moindre cout.

TESTS EN JEU
------------
  /anim sang        puis frapper un mob entoure d'autres
  /dlbvfx play 5    Couperet                 — visuel seul
  /dlbvfx play 9    Tranchant Sanglant       — visuel seul
  /dlbvfx play 10   Siphon Carmin            — visuel seul
  /dlbvfx play 11   Transfusion Vitale       — visuel seul
  /dlbvfx play 12   Carnage Berserk          — visuel seul
  /dlbvfx stop
"""

def main():
    out = 'deliverables/DLB_Blood_Five_Signatures_src.zip'
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
