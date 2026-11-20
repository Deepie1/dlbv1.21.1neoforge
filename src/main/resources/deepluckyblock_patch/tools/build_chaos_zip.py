#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble deliverables/DLB_Chaos_Five_Signatures_src.zip.

REGLES DU LIVRABLE, apprises a la dure :

  * JAMAIS de build.gradle dans l'archive. Il ecraserait le bloc de patch que l'utilisateur
    maintient a la main dans son projet MCreator.
  * Rien hors de src/, a la seule exception du fichier .mcreator (le launcher du mod).
  * AUCUN lisez-moi : l'utilisateur ne les lit pas, ils encombrent l'archive.
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
    'src/main/java/deepluckyblock/commands/DlbSkillCommand.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxMotion.java',
    'src/main/java/deepluckyblock/enchantments/WeaponVfxSubtle.java',
    'src/main/java/deepluckyblock/enchantments/DeepElement.java',
    'src/main/java/deepluckyblock/enchantments/GhostBlockMask.java',
    'src/main/java/deepluckyblock/enchantments/PixelFx.java',
    # T410-SKILLS : les quarante-cinq signatures deviennent des competences au clic droit.
    'src/main/java/deepluckyblock/enchantments/SkillNames.java',
    'src/main/java/deepluckyblock/enchantments/WeaponSkill.java',
    # T417 : porte la clef dlbSlashVisual et sa lecture. Modifie sans etre livre, le
    # serveur chercherait une methode absente.
    'src/main/java/deepluckyblock/util/DeepNbt.java',
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
    # T413 : met en gras les touches et les recharges dans les descriptions.
    # Fichier NEUF : sans cette ligne il ne part pas, et les deux fichiers qui
    # l'appellent ne compileraient pas chez l'utilisateur.
    'src/main/java/deepluckyblock/util/TooltipEmphasis.java',
    # T414 : c'est CE fichier qui dessine la fenetre [SYSTEME] des objets gradés,
    # et donc la section COMPETENCES. Sans lui l'archive livre l'ancien affichage.
    'src/main/java/deepluckyblock/client/SystemTooltipWindowRenderer.java',
]
EXTRA = [
    # T416 : LE FICHIER MCREATOR. C'est le launcher du mod et c'est LUI qui ecrase les
    # fichiers de langue a la regeneration : une traduction absente de son language_map
    # est une traduction perdue. Il voyage donc avec les sources, a la racine, la ou
    # l'utilisateur le depose dans son projet.
    'deep_lucky_block-neoforge-1.21.1.mcreator',
    # Les deux fichiers de langue, pour que l'archive soit juste MEME sans passer par
    # MCreator : qui recopie seulement src/ doit obtenir les memes textes que le
    # language_map ci-dessus. Les trois sources sont verifiees identiques avant envoi.
    'src/main/resources/assets/deep_lucky_block/lang/en_us.json',
    'src/main/resources/assets/deep_lucky_block/lang/fr_fr.json',
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

    raw = io.open(out, 'rb').read()
    with zipfile.ZipFile(out) as z:
        names = z.namelist()
    # T416 : le .mcreator est la SEULE entree admise hors de src/ ; les lisez-moi ont ete
    # retires du livrable a la demande de l'utilisateur.
    HORS_SRC_AUTORISE = {'deep_lucky_block-neoforge-1.21.1.mcreator'}
    bad = [n for n in names if n not in HORS_SRC_AUTORISE and not n.startswith('src/')]
    assert not bad, 'chemins hors src/ : %r' % bad
    assert not [n for n in names if 'LISEZ-MOI' in n or 'LIRE-MOI' in n], 'lisez-moi dans l archive'
    assert 'deep_lucky_block-neoforge-1.21.1.mcreator' in names, 'fichier MCreator absent de l archive'
    assert not [n for n in names if n.endswith('build.gradle')], 'build.gradle dans l archive'
    print('%s : %d entrees, %d octets' % (out, len(names), len(raw)))
    print('SHA-256 %s' % hashlib.sha256(raw).hexdigest())
    return 0


if __name__ == '__main__':
    sys.exit(main())
