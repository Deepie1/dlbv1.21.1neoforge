#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mirrorcheck -- le miroir FIXES_MCREATOR est-il VRAIMENT une copie du code ?

POURQUOI CE BANC EXISTE
    Au T480 la copie miroir de DevTerrainScan.java a ete ecrite a
        FIXES_MCREATOR/deepluckyblock/dev/DevTerrainScan.java
    au lieu de
        FIXES_MCREATOR/src/main/java/deepluckyblock/dev/DevTerrainScan.java
    Le fichier a donc ete depose a un chemin que PERSONNE ne lit, pendant que
    l'emplacement canonique gardait une version perimee (sans le correctif T480).
    Le controle de l'epoque comparait avec la MEME convention de chemin erronee :
    il a donc confirme ma propre faute et annonce "0 divergence sur 227".

    Un controle qui partage l'erreur qu'il doit detecter ne detecte rien.
    Ce banc verifie donc DEUX choses, pas une :
      1. tout .java du miroir correspond a un .java reel IDENTIQUE ;
      2. aucun .java du miroir ne se trouve a un chemin HORS convention
         (c'est ce second point qui aurait mordu au T480).

CONVENTION
    Le kit est lu par deep-lucky-block-patch.gradle sous la forme
        kit/src/main/java/deepluckyblock/<...>.java
    Tout .java place ailleurs dans le kit est un fichier orphelin.

USAGE
    python3 tools/mirrorcheck.py          # sans argument, depuis la racine
    -> code de sortie 0 si vert, 1 si rouge
"""

import os
import sys

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIROIR = os.path.join(RACINE, 'src', 'main', 'resources',
                      'deepluckyblock_patch', 'FIXES_MCREATOR')
PREFIXE = os.path.join('src', 'main', 'java')


def lister_java(racine):
    out = []
    for dp, _, fns in os.walk(racine):
        for fn in fns:
            if fn.endswith('.java') or fn.endswith('.java.disabled'):
                p = os.path.join(dp, fn)
                out.append(os.path.relpath(p, racine))
    return sorted(out)


def main():
    if not os.path.isdir(MIROIR):
        print('mirrorcheck : miroir introuvable -> %s' % MIROIR)
        return 1

    erreurs = []
    fichiers = lister_java(MIROIR)
    if not fichiers:
        print('mirrorcheck : AUCUN .java dans le miroir -- banc sans objet')
        return 1

    orphelins = []
    compares = 0
    divergents = []
    manquants = []

    for rel in fichiers:
        norm = rel.replace('\\', '/')
        # 1) convention de chemin
        if not norm.startswith('src/main/java/'):
            orphelins.append(norm)
            continue
        cible = os.path.join(RACINE, rel)
        source = os.path.join(MIROIR, rel)
        if not os.path.isfile(cible):
            manquants.append(norm)
            continue
        compares += 1
        with open(source, 'rb') as f:
            a = f.read()
        with open(cible, 'rb') as f:
            b = f.read()
        if a != b:
            divergents.append(norm)

    print('mirrorcheck')
    print('  fichiers .java dans le miroir : %d' % len(fichiers))
    print('  compares au code reel         : %d' % compares)
    print('  hors convention (orphelins)   : %d' % len(orphelins))
    print('  sans contrepartie reelle      : %d' % len(manquants))
    print('  contenus divergents           : %d' % len(divergents))

    for p in orphelins:
        erreurs.append('ORPHELIN   %s  (attendu sous src/main/java/)' % p)
    for p in manquants:
        erreurs.append('MANQUANT   %s  (present au miroir, absent du code)' % p)
    for p in divergents:
        erreurs.append('DIVERGENT  %s' % p)

    # Garde-fou : un banc qui ne compare rien ne doit pas etre vert.
    if compares < 200:
        erreurs.append('BANC SANS OBJET : seulement %d fichiers compares '
                       '(attendu >= 200) -- la convention de chemin a change ?'
                       % compares)

    if erreurs:
        print('')
        for e in erreurs:
            print('  [ROUGE] %s' % e)
        print('')
        print('mirrorcheck : %d probleme(s)' % len(erreurs))
        return 1

    print('')
    print('mirrorcheck : vert')
    return 0


if __name__ == '__main__':
    sys.exit(main())
