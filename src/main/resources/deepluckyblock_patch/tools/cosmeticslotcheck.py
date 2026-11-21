#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
cosmeticslotcheck -- UN ORNEMENT DE TETE, UN DE TORSE, UN DE PIEDS.

LA REGLE, telle qu'elle a ete posee :
    « faut limiter a un cosmetique tete, un cosmetique torso, et un cosmetique pieds »

LE DEFAUT CORRIGE (T448). renderPlayerAura testait les drapeaux de tete dans QUATRE
instructions « if » independantes :
    if (F_HALO_CONTINUOUS)   -> haloContinu
    if (F_COMBO_110_AVATAR)  -> soleilAvatar   (chaine exclusive #110/#098/#097/Roi/diademe)
    if (F_ENCH_MYSTIC_MIND)  -> espritMystique
    if (F_ENCH_RADIANT_AEGIS)-> egideRadieuse
Elles etaient donc CUMULABLES. Le combo #110 (drapeaux 0x1101 = AVATAR | RADIANT_AEGIS |
CLOUD_CUSHION) posait le soleil ET l'egide sur le meme crane. Idem au sol : les trois
sceaux (Seigneur de Guerre, Peste, Terreur) se superposaient au meme rayon.

CE QUE VERIFIE CE BANC. Dans le corps de renderPlayerAura, pour chaque groupe
d'emplacement, un seul test commence une instruction -- tous les autres sont des
« else if ». Autrement dit : une chaine, pas une liste.

Un « if » de plus dans un groupe = deux ornements au meme endroit = ce banc echoue.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/cosmeticslotcheck.py [racine]
Sortie :  code 0 si les trois emplacements tiennent, 1 sinon.
"""

import io
import os
import re
import sys

AURA = os.path.join('src', 'main', 'java', 'deepluckyblock', 'client', 'CosmeticPixelAura.java')

GROUPES = {
    'TETE': [
        'F_COMBO_110_AVATAR', 'F_COMBO_098_SWORD_GOD', 'F_COMBO_097_ETERNAL',
        'F_ENCH_KING', 'F_ENCH_CROWN_HALO', 'F_ENCH_RADIANT_AEGIS',
        'F_ENCH_MYSTIC_MIND', 'F_HALO_CONTINUOUS',
    ],
    'PIEDS': [
        'F_COMBO_106_WARLORD', 'F_COMBO_062_PLAGUE', 'F_ENCH_DREAD_AURA',
    ],
}


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
    chemin = os.path.join(racine, AURA)
    if not os.path.exists(chemin):
        print('cosmeticslotcheck : %s introuvable' % AURA)
        return 1
    corps = corps_methode(sans_commentaires(io.open(chemin, encoding='utf-8').read()),
                          'void renderPlayerAura(')
    if corps is None:
        print('cosmeticslotcheck : renderPlayerAura introuvable dans CosmeticPixelAura.')
        return 1

    echecs = []
    for nom, drapeaux in GROUPES.items():
        ouvertures = []
        for d in drapeaux:
            # Chaque test du drapeau : precede de « else » ou non ?
            for m in re.finditer(r'(\belse\s+)?\bif\s*\(\s*\(\s*flags\s*&\s*'
                                 r'CosmeticAuraClientTicker\.' + re.escape(d) + r'\s*\)',
                                 corps):
                if not m.group(1):
                    ouvertures.append(d)
        if len(ouvertures) > 1:
            echecs.append(
                '%s : %d instructions « if » independantes (%s).\n'
                '        Elles sont CUMULABLES : plusieurs ornements se superposent au meme\n'
                '        endroit du corps. Un seul test doit ouvrir la chaine, tous les autres\n'
                '        doivent etre des « else if ».'
                % (nom, len(ouvertures), ', '.join(ouvertures)))
        elif len(ouvertures) == 0:
            echecs.append('%s : aucun test de drapeau trouve ; le groupe a-t-il ete renomme ?' % nom)

    # Le torse (les ailes) : archange et givre s'excluent, les rubans n'arrivent
    # qu'en l'absence des deux.
    if 'rubansAile' in corps and not re.search(r'!\s*archangel\s*&&\s*!\s*winged', corps):
        echecs.append('TORSE : les rubans ne sont plus conditionnes a l\'absence d\'ailes '
                      '(trois jeux d\'ailes = une tache).')

    if echecs:
        print('cosmeticslotcheck : %d EMPLACEMENT(S) EN DEFAUT\n' % len(echecs))
        for e in echecs:
            print('  !! %s\n' % e)
        return 1

    print('cosmeticslotcheck : tete, torse et pieds tiennent -- un seul ornement par emplacement')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
