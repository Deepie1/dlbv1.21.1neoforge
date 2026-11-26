#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CONSTRUCTEUR DE deliverables/DLB_sync.zip — le colis de SYNCHRONISATION.

POURQUOI UN DEUXIEME ZIP
    deliverables/DLB_src.zip est un colis INCREMENTAL : il ne contient que ce
    qui a change depuis une base de reference. C'est le bon outil quand le
    workspace du developpeur est a jour.
    Il ne l'est pas. Quatre crashs de suite l'ont montre, et on ne sait plus
    dire de quel etat il part. Dans ce cas un colis incremental ne repare
    rien : il faut poser l'INTEGRALITE des sources Java.

    L'archive complete du depot existe (codeload) mais pese 59 Mo, dont 38 Mo
    de ressources, de rapports de scan et d'anciens colis — inutiles pour
    reparer une compilation. Ce colis-ci pese environ 2 Mo.

CONTENU — strictement ce qu'il faut pour que le mod compile et se charge :
    - src/main/java/**  (les 226 .java, en entier)
    - le script de reparation Gradle, qui doit AUSSI etre reinstalle dans
      %USERPROFILE%\\.mcreator\\gradle\\init.d\\ : le log du 26/11 a montre que
      celui du developpeur etait perime lui aussi (il n'imprimait pas
      l'empreinte), donc ni la garde ni l'empreinte ne pouvaient l'alerter.
    - EMPREINTE.txt : l'empreinte attendue, pour que la verification ne
      dependent pas d'une valeur recopiee a la main dans un message.

Les ressources ne sont volontairement PAS incluses : elles ne causent pas de
crash de chargement, elles pesent 38 Mo, et DLB_src.zip les couvre deja.

    python3 tools/build_sync_zip.py
"""

import os
import sys
import zipfile

RAC = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(RAC, "tools"))

from empreinte_sources import empreinte  # noqa: E402

CIBLE = os.path.join(RAC, "deliverables", "DLB_sync.zip")
INIT = "src/main/resources/deepluckyblock_patch/tools/deep-lucky-block-patch.gradle"

LISEZMOI = """COLIS DE SYNCHRONISATION - Deep Lucky Block
===========================================

Ce colis contient l'INTEGRALITE des sources Java du mod, plus le script de
reparation Gradle. Il sert a remettre d'aplomb un workspace MCreator dont on
ne sait plus de quelle version il part.

INSTALLATION AUTOMATIQUE (recommande)
    deliverables/METTRE-A-JOUR.ps1 telecharge et installe ce colis tout seul,
    puis affiche l'empreinte avant / apres pour prouver que la copie a eu lieu.

INSTALLATION A LA MAIN
    1. Copier src\\main\\java\\  par-dessus celui du workspace MCreator,
       en ecrasant.
    2. Copier
         src\\main\\resources\\deepluckyblock_patch\\tools\\deep-lucky-block-patch.gradle
       vers
         %%USERPROFILE%%\\.mcreator\\gradle\\init.d\\deep-lucky-block-patch.gradle
       (CETTE ETAPE EST SOUVENT OUBLIEE : c'est ce fichier qui imprime
        l'empreinte et qui arrete le build avant un crash.)
    3. Supprimer
         <workspace>\\build\\classes\\java\\main\\deepluckyblock\\
       sans quoi Gradle affichera « compileJava UP-TO-DATE » et relancera
       l'ancien binaire. C'est exactement ce qui s'est produit les 25 et 26
       novembre : quatre crashs identiques sur un code deja corrige.

VERIFICATION
    Au lancement suivant, la console MCreator doit afficher :
        [DLB-PATCH] empreinte des sources Java : %(emp)s (%(nb)d fichiers)
    Si l'empreinte differe, Gradle ne lit pas le dossier que vous avez mis a
    jour. Si la ligne est absente, c'est l'etape 2 qui n'a pas ete faite.

empreinte attendue : %(emp)s
fichiers Java      : %(nb)d
"""


def construire():
    emp, nb = empreinte(RAC)
    if emp is None:
        print("src/main/java introuvable", file=sys.stderr)
        return 2

    base = os.path.join(RAC, "src", "main", "java")
    fichiers = []
    for dossier, _, noms in os.walk(base):
        for n in noms:
            if n.endswith(".java"):
                absolu = os.path.join(dossier, n)
                rel = os.path.relpath(absolu, RAC).replace(os.sep, "/")
                fichiers.append((rel, absolu))
    fichiers.sort()

    init_abs = os.path.join(RAC, INIT)
    if not os.path.isfile(init_abs):
        print("script de reparation introuvable : %s" % INIT, file=sys.stderr)
        return 2

    os.makedirs(os.path.dirname(CIBLE), exist_ok=True)
    with zipfile.ZipFile(CIBLE, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel, absolu in fichiers:
            z.write(absolu, rel)
        z.write(init_abs, INIT)
        z.writestr("EMPREINTE.txt", "%s %d\n" % (emp, nb))
        z.writestr("LISEZ-MOI.txt",
                   (LISEZMOI % {"emp": emp, "nb": nb}).replace("\n", "\r\n"))

    taille = os.path.getsize(CIBLE)
    print("DLB_sync.zip : %d .java + script d'init, empreinte %s, %.1f Mo"
          % (len(fichiers), emp, taille / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(construire())
