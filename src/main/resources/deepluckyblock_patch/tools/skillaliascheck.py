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

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('skillaliascheck : %d verrou(x) rompu(s) -- un enchantement peut relancer '
              'une competence' % len(echecs))
        return 1

    print('skillaliascheck : 5 verrous tiennent -- aucun enchantement ne mene a une '
          'competence (melee) ; les armes a distance gardent leurs alias')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
