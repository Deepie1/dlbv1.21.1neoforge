#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
rangedcheck -- UN ARC NE JOUE NI SIGNATURE, NI SLASH, NI DEUX ELEMENTS.

LA REGLE, telle qu'elle a ete posee :

    « les animations d'armes a distance sont toutes SAUF celles vues ensemble via le
      site HTML (les 3 a 5 animations par elements) et hors slash. Attention, une arme
      a distance n'a qu'UN SEUL element, donc qu'un seul element d'animation : un arc
      ne devrait pas jouer une petite animation d'eau ET de poison. »

Mesure faite pour en fixer le sens : les 47 entrees de WeaponVisualEffectId se
repartissent en 3 a 5 par DeepElement -- exactement le decoupage des apercus
DLB_<Element>_Three/Five_Signatures_preview.html. L'ensemble revu en HTML est donc
l'enumeration ENTIERE. « Toutes sauf celles vues en HTML » signifie par consequent :
AUCUNE des 47 signatures sur une arme a distance. Il leur reste les accents discrets
de WeaponVfxSubtle (2 a 9 mini-pixels) et le sillage attache au projectile.

POURQUOI CE BANC EXISTE. T445 a accorde aux armes a distance le droit de lire la table
d'alias, sans borner ce que ce droit laissait passer : une arbalete a lance le
raz-de-maree Hydro. Une exception doit etre definie par CE QU'ELLE AUTORISE. Les trois
verrous ci-dessous decrivent ce qui est autorise ; tout le reste est refuse.

VERROUS
  V1  resolveLayers ramene maxLayers a 1 sur la branche « arme a distance »
      -> un seul calque, donc un seul element, pour TOUS les appelants.
  V2  onLivingHurtVisuals connait le drapeau aDistance et coupe avant tout
      broadcastNear de signature (KIND_IMPACT / KIND_BEAM_SIPHON).
  V3  onProjectileBlockVisuals ne diffuse plus aucune signature : accent seulement.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/rangedcheck.py [racine]
Sortie :  code 0 si les trois verrous tiennent, 1 sinon.
"""

import io
import os
import re
import sys

VFX = os.path.join('src', 'main', 'java', 'deepluckyblock', 'enchantments',
                   'WeaponVisualEffectId.java')
LISTENER = os.path.join('src', 'main', 'java', 'deepluckyblock', 'enchantments',
                        'WeaponVfxServerListener.java')

SIGNATURES_BROADCAST = re.compile(r'broadcastNear\s*\([^;]*?KIND_(IMPACT|BEAM_SIPHON)', re.S)


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
    vfx = lire(racine, VFX)
    listener = lire(racine, LISTENER)
    if vfx is None or listener is None:
        print('rangedcheck : fichiers introuvables sous %s' % racine)
        return 1
    vfx = sans_commentaires(vfx)
    listener = sans_commentaires(listener)

    # ---- V1 : une arme a distance ne resout qu'UN calque ----------------------------------
    corps = corps_methode(vfx, 'List<ResolvedLayer> resolveLayers(ItemStack stack, int maxLayers)')
    if corps is None:
        echecs.append('V1 : resolveLayers(ItemStack, int) introuvable dans WeaponVisualEffectId.')
    else:
        i_dist = corps.find('estArmeADistance(')
        i_clamp = corps.find('maxLayers = 1')
        if i_dist < 0:
            echecs.append('V1 : resolveLayers ne distingue plus les armes a distance '
                          '(estArmeADistance absent).')
        elif i_clamp < 0:
            echecs.append(
                'V1 : resolveLayers ne ramene plus maxLayers a 1 pour les armes a distance.\n'
                '        Sans ce plafond, un arc rend plusieurs calques et joue DEUX elements\n'
                '        (une petite animation d\'eau ET de poison). Le plafond doit rester\n'
                '        a la source : tous les appelants en heritent.')
        elif i_clamp < i_dist:
            echecs.append('V1 : le plafond maxLayers = 1 precede le test estArmeADistance ; '
                          'il toucherait aussi la melee.')

    # ---- V2 : le coup porte ne retombe jamais sur une signature a distance -----------------
    corps = corps_methode(listener, 'void onLivingHurtVisuals(')
    if corps is None:
        echecs.append('V2 : onLivingHurtVisuals introuvable dans WeaponVfxServerListener.')
    else:
        if 'estArmeADistance(' not in corps:
            echecs.append(
                'V2 : onLivingHurtVisuals ne consulte plus estArmeADistance.\n'
                '        Sans ce drapeau, l\'echec de l\'accent discret (une fois sur dix)\n'
                '        retombe sur broadcastNear(KIND_IMPACT) : la signature complete.')
        else:
            premier_broadcast = SIGNATURES_BROADCAST.search(corps)
            garde = re.search(r'\baDistance\b', corps)
            if premier_broadcast and garde and garde.start() > premier_broadcast.start():
                echecs.append('V2 : une diffusion de signature precede toute garde aDistance '
                              'dans onLivingHurtVisuals.')
            if not re.search(r'if\s*\(\s*aDistance\s*\)\s*continue\s*;', corps):
                echecs.append(
                    'V2 : la coupure « if (aDistance) continue; » a disparu de la boucle de\n'
                    '        calques. C\'est elle qui empeche la giga-animation sur arbalete.')

    # ---- V3 : impact sur bloc = accent discret, jamais une signature -----------------------
    corps = corps_methode(listener, 'void onProjectileBlockVisuals(')
    if corps is None:
        echecs.append('V3 : onProjectileBlockVisuals introuvable dans WeaponVfxServerListener.')
    else:
        if SIGNATURES_BROADCAST.search(corps):
            echecs.append(
                'V3 : onProjectileBlockVisuals diffuse de nouveau une signature\n'
                '        (KIND_IMPACT / KIND_BEAM_SIPHON). Une fleche plantee dans un mur\n'
                '        rejouerait la grosse animation de l\'arme. Accent discret seulement.')
        if 'playSubtleAccent(' not in corps:
            echecs.append('V3 : onProjectileBlockVisuals ne joue plus l\'accent discret ; '
                          'la trainee a l\'impact sur bloc a ete demandee explicitement.')

    if echecs:
        print('rangedcheck : %d VERROU(S) ROMPU(S)\n' % len(echecs))
        for e in echecs:
            print('  !! %s\n' % e)
        return 1

    print('rangedcheck : 3 verrous tiennent -- une arme a distance garde ses accents discrets, '
          'un seul element, et aucune des 47 signatures')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
