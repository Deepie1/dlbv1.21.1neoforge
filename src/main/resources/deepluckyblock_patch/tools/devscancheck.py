#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
devscancheck -- L'OUTIL DE SCAN DOIT RESTER JETABLE, ET SA MESURE DOIT RESTER VRAIE.

DevTerrainScan.java est un outil de developpement. Le dev a pose une condition explicite :
pouvoir le supprimer d'un coup, sans la moindre repercussion. Cela se verifie, et cela se
casse facilement.

Ce banc tient donc deux promesses a la fois.

  PROMESSE 1 -- JETABLE. Si le fichier n'existe pas, ce banc est VERT et se tait. C'est le
  comportement normal apres suppression de l'outil : un banc qui passerait au rouge serait
  lui-meme la repercussion qu'on voulait eviter. Et tant que le fichier existe, aucune
  autre classe du mod ne doit le nommer : une seule reference ailleurs, et la suppression
  casse la compilation.

  PROMESSE 2 -- LA MESURE EST VRAIE. Un outil de mesure se casse en silence : rien ne
  s'arrete dans le jeu quand il ment. Trois facons de le casser :
    - le rendre ACTIF (charger ou generer les chunks qu'il observe), ce qui lui ferait
      montrer un terrain que le pipeline n'avait pas, et couterait 8 a 12 s par chunk a
      froid (mesure T304) sur un serveur deja en retard de centaines de ticks ;
    - MAQUILLER les chunks absents avec une valeur plausible, alors que « verifier si des
      chunks manquent » est precisement la raison d'etre de l'outil ;
    - PERDRE l'alignement des grilles sur les chunks, ce qui rend toute lecture
      geometrique fausse sans que rien ne le signale.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/devscancheck.py [racine]
Sortie :  code 0 si l'outil est absent OU si les sept verrous tiennent, 1 sinon.
"""

import io
import os
import re
import sys

OUTIL = 'src/main/java/deepluckyblock/dev/DevTerrainScan.java'
CLASSE = 'DevTerrainScan'
JAVA = 'src/main/java/deepluckyblock'

# Appels qui chargent, generent ou ATTENDENT un chunk. Interdits dans l'outil.
#   getChunk(        -> getChunk(..., FULL, true) : generation synchrone (T56)
#   SafeSurface.request / forceLoad / setChunkForced -> mise en file de generation (T304)
#   level.getBlockState / level.getHeight -> passent par getChunk : meme piege
ACTIFS = (
    r'\.getChunk\s*\(',
    r'SafeSurface\.request\s*\(',
    r'\bforceLoad\s*\(',
    r'\bsetChunkForced\s*\(',
    r'\blevel\.getBlockState\s*\(',
    r'\blevel\.getHeight\s*\(',
)


def lire(racine, rel):
    chemin = os.path.join(racine, rel)
    if not os.path.exists(chemin):
        return None
    with io.open(chemin, encoding='utf-8') as f:
        return f.read()


def sans_commentaires(src):
    """Retire blocs et lignes de commentaire : une regle ne doit jamais mordre sur la prose."""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'(?m)//.*$', '', src)
    return src


def controler(racine='.'):
    src = lire(racine, OUTIL)

    # ------------------------------------------------------------------ PROMESSE 1a
    # Outil absent = etat normal apres suppression. Vert, et on se tait.
    if src is None:
        print('devscancheck : outil de scan absent (supprime) -- rien a verifier, et c\'est '
              'exactement le comportement attendu.')
        return 0

    echecs = []
    code = sans_commentaires(src)

    # ------------------------------------------------------------------ VERROU 1
    # Personne d'autre ne nomme la classe : sans quoi la suppression casse le build.
    referents = []
    for dossier, _, fichiers in os.walk(os.path.join(racine, JAVA)):
        for f in fichiers:
            if not f.endswith('.java'):
                continue
            chemin = os.path.join(dossier, f)
            rel = os.path.relpath(chemin, racine).replace(os.sep, '/')
            if rel == OUTIL:
                continue
            with io.open(chemin, encoding='utf-8') as fh:
                if CLASSE in fh.read():
                    referents.append(rel)
    if referents:
        echecs.append('VERROU 1 : %d fichier(s) nomment %s (%s) -- supprimer l\'outil '
                      'casserait la compilation, or il doit rester jetable'
                      % (len(referents), CLASSE, ', '.join(referents[:4])))

    # ... et l'outil ne vit que dans son propre package.
    autres = []
    dossier_dev = os.path.join(racine, JAVA, 'dev')
    if os.path.isdir(dossier_dev):
        autres = [f for f in os.listdir(dossier_dev) if f.endswith('.java')
                  and f != os.path.basename(OUTIL)]
    if autres:
        echecs.append('VERROU 1 : le package dev contient aussi %s -- l\'outil doit rester '
                      'seul dans son dossier pour rester supprimable d\'un geste'
                      % ', '.join(autres))

    # ------------------------------------------------------------------ VERROU 2
    # Il s'accroche tout seul : aucune edition d'un autre fichier n'est requise.
    # Test sur le CODE et non sur le source : la javadoc de l'outil cite
    # « @EventBusSubscriber » en toutes lettres, et chercher dans les commentaires
    # laissait passer la suppression de l'annotation elle-meme.
    if '@EventBusSubscriber' not in code:
        echecs.append('VERROU 2 : plus de @EventBusSubscriber -- l\'outil devrait alors etre '
                      'branche a la main ailleurs, et ne serait plus detachable')
    if 'RegisterCommandsEvent' not in code:
        echecs.append('VERROU 2 : la commande ne s\'enregistre plus toute seule')

    # ------------------------------------------------------------------ VERROU 3
    # Lecture strictement passive.
    for motif in ACTIFS:
        t = re.search(motif, code)
        if t:
            echecs.append('VERROU 3 : appel actif %s -- il charge, genere ou attend un '
                          'chunk ; la sonde modifierait le terrain qu\'elle mesure'
                          % t.group(0).strip())
    if 'getChunkNow(' not in code:
        echecs.append('VERROU 3 : plus de lecture par getChunkNow -- c\'est la seule qui ne '
                      'declenche jamais de generation')

    # ------------------------------------------------------------------ VERROU 4
    # Un chunk absent n'est JAMAIS remplace par une valeur plausible.
    if 'ABSENT' not in code:
        echecs.append('VERROU 4 : le marqueur ABSENT a disparu -- les chunks manquants '
                      'seraient indiscernables, alors que c\'est l\'objet meme du scan')
    if '"----"' not in code:
        echecs.append('VERROU 4 : les grilles n\'ecrivent plus « ---- » pour un chunk non '
                      'charge -- un trou deviendrait invisible a la lecture')
    # Le relief theorique doit rester une reference a part, jamais un bouche-trou.
    #
    # On COMPTE sur le fichier entier au lieu de borner la branche « chunk non charge »
    # par ses accolades : ce bornage est precisement le piege qui avait rendu une regle
    # silencieuse en T470 (les chaines du code contiennent des accolades, et une branche
    # un peu longue sort de la fenetre). Deux appels sont legitimes et seulement deux :
    # le sol de reference, et le calcul du relief theorique complet.
    appels_bruit = code.count('getBaseHeight(')
    if appels_bruit > 2:
        echecs.append('VERROU 4 : %d appels a getBaseHeight au lieu de 2 -- un troisieme '
                      'sert tres probablement a combler les chunks absents avec du relief '
                      'theorique, ce qui masquerait exactement ce que le scan doit montrer'
                      % appels_bruit)
    if appels_bruit < 2:
        echecs.append('VERROU 4 : %d appel(s) a getBaseHeight au lieu de 2 -- la grille de '
                      'reference du relief d\'origine a disparu' % appels_bruit)

    # ------------------------------------------------------------------ VERROU 5
    # Alignement des grilles sur les chunks : sans lui, toute lecture est fausse.
    if '<< 4' not in code:
        echecs.append('VERROU 5 : plus de bornage en << 4 -- la zone ne serait plus alignee '
                      'sur les frontieres de chunk')
    # le pas des vues doit diviser 16, sinon un caractere chevauche deux chunks
    if '16 % p' not in code:
        echecs.append('VERROU 5 : le pas des vues ne force plus un diviseur de 16 -- un '
                      'caractere pourrait chevaucher deux chunks et brouiller la geometrie')
    # une colonne de grille = 4 caracteres, et un separateur tous les 16
    if "(x & 15) == 0" not in code:
        echecs.append('VERROU 5 : plus de separateur de chunk tous les 16 blocs dans les '
                      'grilles')

    # ------------------------------------------------------------------ VERROU 6
    # Scan complet a CHAQUE etape, et les 3 jalons du pipeline sont bien suivis.
    for jalon in ('1_DEBUT_CLEAR', '2_APRES_CLEAR', '3_APRES_PASTE'):
        if jalon not in code:
            echecs.append('VERROU 6 : le jalon %s n\'est plus capture' % jalon)
    if 'PASTE_Q' not in code:
        echecs.append('VERROU 6 : l\'outil n\'observe plus la file de pose du lac -- il ne '
                      'saurait plus dater la fin du clear ni la fin de la pose')
    if 'zoneBlockBounds' not in code:
        echecs.append('VERROU 6 : l\'outil n\'observe plus la zone epinglee -- il ne saurait '
                      'plus quand le pipeline commence ni quand il finit')

    # ------------------------------------------------------------------ VERROU 7
    # Aucune tache de build ne doit RESTAURER l'outil apres sa suppression : ce serait
    # la repercussion la plus sournoise qui soit, puisqu'elle ne se verrait qu'au
    # prochain gradlew. La tache applyMcreatorPatch recopie une liste fixe de fichiers
    # cadre depuis le miroir ; l'outil ne doit jamais y entrer.
    #
    # On lit le CODE et non la prose : le script explique en commentaire quel outil a
    # remplace l'ancien, et une regle qui mord sur une phrase est un faux positif. Elle
    # ne doit se declencher que si le nom apparait dans une instruction -- typiquement
    # une entree de liste a recopier. Groovy partage la syntaxe // et /* */ de Java.
    gradle = lire(racine, 'src/main/resources/deepluckyblock_patch/tools/'
                          'deep-lucky-block-patch.gradle')
    if gradle and CLASSE in sans_commentaires(gradle):
        echecs.append('VERROU 7 : %s est nomme dans le CODE de deep-lucky-block-patch.gradle '
                      '-- la tache de patch le restaurerait apres suppression' % CLASSE)

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('devscancheck : %d verrou(x) rompu(s)' % len(echecs))
        return 1

    print('devscancheck : 7 verrous tiennent -- outil isole et supprimable, auto-accroche, '
          'sonde passive, chunks absents jamais maquilles, grilles alignees sur les chunks, '
          '3 jalons suivis, aucune restauration par le build')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
