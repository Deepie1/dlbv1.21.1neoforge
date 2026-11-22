#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
skillaliascheck -- UN ENCHANTEMENT NE DOIT JAMAIS MENER A UNE COMPETENCE.

La regle, posee en T445 apres trois signalements successifs : les 45 animations de
signature sont des COMPETENCES. Sur une arme de melee, une competence ne se declenche
que par la marque dlbSkillGranted, posee par le tirage a 5 % du butin. Aucun
enchantement -- du mod ou vanilla -- ne doit pouvoir en declencher une.

L'EXCEPTION, voulue : les armes a distance (arc, arbalete, trident, armes a projectile)
gardent leurs petites animations resolues par la table d'alias.

Ce banc relit la structure du code et echoue des que l'un des quatre verrous saute. Il
existe parce que le lien avait ete coupe deux fois en surface -- une fois dans le
lanceur de competence (T429), une fois dans un gestionnaire de clic droit (T444) -- et
qu'il repoussait ailleurs a chaque fois. Ici on verifie la RACINE : la table d'alias.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/skillaliascheck.py [racine]
Sortie :  code 0 si les quatre verrous tiennent, 1 sinon.
"""

import io
import os
import re
import sys

VFX = 'src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java'
SKILL = 'src/main/java/deepluckyblock/enchantments/WeaponSkill.java'
APPEL_ALIAS = 'forEnchantmentLocation('
RENDU = 'src/main/java/deepluckyblock/client/WeaponBladeVisualRenderer.java'
GENERE = 'src/main/java/deepluckyblock/procedures/GenerateLuckyItemProcedure.java'


def lire(racine, rel):
    chemin = os.path.join(racine, rel)
    if not os.path.exists(chemin):
        return None
    return io.open(chemin, encoding='utf-8').read()


def sans_commentaires(src):
    """Retire blocs et lignes de commentaire, en gardant le nombre de lignes."""
    src = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group(0).count('\n'), src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


def corps_methode(src, signature):
    """Corps d'une methode reperee par un fragment de sa signature, accolades comptees."""
    i = src.find(signature)
    if i < 0:
        return None
    j = src.find('{', i)
    if j < 0:
        return None
    prof = 0
    for k in range(j, len(src)):
        if src[k] == '{':
            prof += 1
        elif src[k] == '}':
            prof -= 1
            if prof == 0:
                return src[j:k + 1]
    return None


def controler(racine):
    echecs = []
    vfx_brut = lire(racine, VFX)
    skill_brut = lire(racine, SKILL)
    if vfx_brut is None or skill_brut is None:
        print('skillaliascheck : fichiers introuvables sous %s' % racine)
        return 1
    vfx = sans_commentaires(vfx_brut)
    skill = sans_commentaires(skill_brut)

    # VERROU 1 -- la resolution de competence n'emprunte plus la table d'alias.
    corps = corps_methode(skill, 'private static Entry resolve(')
    if corps is None:
        echecs.append('VERROU 1 : methode resolve(ResourceLocation, boolean) introuvable')
    elif APPEL_ALIAS in corps:
        echecs.append('VERROU 1 : WeaponSkill.resolve lit encore la table d\'alias '
                      '(forEnchantmentLocation) -- un alias redevient une competence')

    # VERROU 2 -- la marque dlbSkillGranted reste exigee dans scan().
    corps = corps_methode(skill, 'public static List<Found> scan(')
    if corps is None:
        echecs.append('VERROU 2 : methode scan(ItemStack) introuvable')
    elif 'grantedPath(' not in corps or 'accordee' not in corps:
        echecs.append('VERROU 2 : scan() n\'exige plus la marque dlbSkillGranted')

    # VERROU 3 -- dans resolveLayers, la melee sort AVANT tout usage d'alias.
    corps = corps_methode(vfx, 'public static List<ResolvedLayer> resolveLayers(')
    if corps is None:
        echecs.append('VERROU 3 : methode resolveLayers(ItemStack, int) introuvable')
    else:
        sortie = corps.find('estArmeADistance(')
        alias = corps.find(APPEL_ALIAS)
        if sortie < 0:
            echecs.append('VERROU 3 : resolveLayers ne separe plus melee et distance '
                          '(appel a estArmeADistance absent)')
        elif alias >= 0 and alias < sortie:
            echecs.append('VERROU 3 : resolveLayers lit la table d\'alias AVANT d\'avoir '
                          'ecarte la melee -- une arme de melee reprend les alias')

    # VERROU 4 -- le gantelet ne traduit plus un enchantement d'armure en signature.
    corps = corps_methode(vfx, 'public static List<ResolvedLayer> resolveBareHandGauntletLayers(')
    if corps is None:
        echecs.append('VERROU 4 : methode resolveBareHandGauntletLayers introuvable')
    elif 'GAUNTLET_MAP.get(' in corps:
        echecs.append('VERROU 4 : le poing nu relit GAUNTLET_MAP -- un enchantement '
                      'de plastron rejoue une animation de competence')

    # VERROU 5 -- personne d'autre dans le mod n'appelle la table d'alias.
    dehors = []
    for dp, _, fns in os.walk(os.path.join(racine, 'src/main/java')):
        if 'FIXES_MCREATOR' in dp:
            continue
        for fn in fns:
            if not fn.endswith('.java') or fn == 'WeaponVisualEffectId.java':
                continue
            chemin = os.path.join(dp, fn)
            src = sans_commentaires(io.open(chemin, encoding='utf-8').read())
            for n, ligne in enumerate(src.split('\n'), 1):
                if APPEL_ALIAS in ligne:
                    dehors.append('%s:%d' % (fn, n))
    if dehors:
        echecs.append('VERROU 5 : la table d\'alias est lue hors de sa classe : %s'
                      % ', '.join(dehors))

    # =====================================================================================
    #  T470 -- UNE COMPETENCE ACCORDEE DOIT RESTER ATTEIGNABLE
    # =====================================================================================
    # Les verrous 1 a 5 empechent qu'un enchantement mene a une competence. Ceux-ci
    # empechent l'inverse, constate en jeu apres plus de 300 blocs casses : une competence
    # bel et bien accordee que RIEN ne laissait voir. Trois causes independantes, trois
    # verrous.

    rendu = lire(racine, RENDU)
    genere = lire(racine, GENERE)
    if rendu is None or genere is None:
        echecs.append('T470 : WeaponBladeVisualRenderer ou GenerateLuckyItemProcedure introuvable')
    else:
        rendu = sans_commentaires(rendu)
        genere = sans_commentaires(genere)

        # VERROU 6 -- le rendu de lame ne subordonne plus la competence au tirage d'aura.
        # Les deux tirages sont independants : 5 % pour la competence, 15 % en melee pour
        # l'aura. Exiger les deux donnait 0,75 % d'armes animees.
        m = re.search(r'boolean\s+bladePixels\s*=([^;]*);', rendu, re.S)
        if m is None:
            echecs.append('VERROU 6 : affectation de bladePixels introuvable dans le rendu de lame')
        else:
            expr = m.group(1)
            if 'hasLuckyWeaponAura' in expr and 'skillPixels' not in expr:
                echecs.append('VERROU 6 : bladePixels depend de hasLuckyWeaponAura sans porte '
                              'de competence -- une arme a competence mais sans aura reste nue')
            if 'skillPixels' in expr and 'WeaponSkill.grantedPath(' not in rendu:
                echecs.append('VERROU 6 : skillPixels ne se fonde pas sur la marque '
                              'dlbSkillGranted (WeaponSkill.grantedPath)')

        # VERROU 7 -- le tirage ne s'annule plus devant une CAPACITE ACTIVEE.
        # scan() compte aussi les huit capacites activees, qui passent sans marque :
        # army_summon sur une epee LEGENDARY+, ore_detector sur une pioche RARE+ volaient
        # le tirage sans qu'aucune competence d'element ne soit jamais accordee.
        corps = corps_methode(genere, 'private static void rollSkillOnDrop(')
        if corps is None:
            echecs.append('VERROU 7 : methode rollSkillOnDrop introuvable')
        else:
            if 'WeaponSkill.scan(' in corps:
                echecs.append('VERROU 7 : rollSkillOnDrop s\'abstient sur scan() -- une capacite '
                              'activee tombee du pool commun annule le tirage a 5 %')
            if 'WeaponSkill.grantedPath(' not in corps:
                echecs.append('VERROU 7 : rollSkillOnDrop ne verifie plus la marque accordee')

        # VERROU 8 -- TOUTES les sorties posent les finitions, branche ETERNAL comprise.
        # applyEnchantmentsFromConfig rend la main deux fois ; tant que les finitions
        # etaient ecrites en ligne tout en bas, le rang ETERNAL (luck >= 100) les sautait.
        appels = len(re.findall(r'finishSkillAndSlashRolls\s*\(', genere))
        if appels < 3:
            echecs.append('VERROU 8 : finishSkillAndSlashRolls n\'est pas pose sur les DEUX '
                          'sorties de applyEnchantmentsFromConfig (declaration + 2 appels '
                          'attendus, %d occurrence(s) vue(s)) -- le rang ETERNAL repart sans '
                          'competence' % appels)
        poses = len(re.findall(r'(?<!void )rollSkillOnDrop\s*\(', genere))
        if poses != 1:
            echecs.append('VERROU 8 : rollSkillOnDrop doit etre appele depuis le seul point '
                          'commun finishSkillAndSlashRolls (%d appel(s) vu(s))' % poses)

    # VERROU 9 -- la marque l'emporte sur l'arbitrage au niveau.
    # Une capacite activee porte souvent un niveau superieur a la competence accordee ;
    # si elle gagnait l'arbitrage, l'arme redevenait muette (effect() == null : pas de
    # calque, pas de clic droit, pas d'encart).
    for nom in ('public static Entry retainedOn(', 'public static Found retainedFoundOn('):
        corps = corps_methode(skill, nom)
        if corps is None:
            echecs.append('VERROU 9 : methode %s introuvable' % nom.split()[-1])
        elif 'gardeeSur(' not in corps:
            echecs.append('VERROU 9 : %s arbitre sans consulter la marque accordee '
                          '(gardeeSur) -- une capacite activee de plus haut niveau peut '
                          'evincer la competence' % nom.split()[-1])
    corps = corps_methode(skill, 'private static Found gardeeSur(')
    if corps is None:
        echecs.append('VERROU 9 : methode gardeeSur introuvable')
    elif 'grantedPath(' not in corps:
        echecs.append('VERROU 9 : gardeeSur ne lit pas la marque dlbSkillGranted')
    corps = corps_methode(skill, 'public static List<net.minecraft.resources.ResourceLocation> extraSkillEnchants(')
    if corps is None:
        echecs.append('VERROU 9 : methode extraSkillEnchants introuvable')
    elif 'accordeeParmi(' not in corps:
        echecs.append('VERROU 9 : extraSkillEnchants peut retirer la competence accordee')

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('skillaliascheck : %d verrou(x) rompu(s) -- un enchantement peut relancer '
              'une competence' % len(echecs))
        return 1

    print('skillaliascheck : 9 verrous tiennent -- aucun enchantement ne mene a une '
          'competence (melee), et une competence accordee reste visible, tiree et retenue')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
