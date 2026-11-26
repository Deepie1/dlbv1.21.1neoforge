#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BANC — LE COLIS DE SYNCHRONISATION DOIT ETRE A JOUR (T490).

REGLE
    deliverables/DLB_sync.zip est le seul canal par lequel les sources
    corrigees arrivent reellement dans le workspace MCreator du developpeur.
    Un colis perime est pire qu'un colis absent : le script de mise a jour
    annonce un succes, affiche une empreinte, et installe du vieux code. On
    aurait alors une preuve qui ment, ce qui est exactement le defaut que
    toute cette mecanique cherche a supprimer.

DEFAUT VISE
    T490 : quatre crashs de suite sur un code deja corrige, parce que rien ne
    reliait « le correctif est pousse » a « le correctif est chez le
    developpeur ». Le colis est ce lien. Il doit donc etre reconstruit a
    chaque modification d'un .java ou du script de reparation.

VERROUS
    V1 — le colis existe.
    V2 — il contient exactement les memes .java que l'arbre de travail
         (aucun manquant, aucun en trop).
    V3 — chaque .java du colis est identique, octet pour octet, a celui de
         l'arbre.
    V4 — le script de reparation Gradle est present et identique.
    V5 — EMPREINTE.txt annonce l'empreinte reellement calculee sur l'arbre,
         et le compte de fichiers correspond. Un colis juste avec une
         etiquette fausse ferait echouer la verification chez le developpeur
         sans raison comprehensible.
    V6 — le script de reparation embarque dans le colis imprime bien
         l'empreinte et porte bien la garde. Sans cela, le developpeur
         reinstalle une version aveugle et on repart pour un tour.

    Reparation : python3 tools/build_sync_zip.py
"""

import os
import sys
import zipfile

RAC = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(RAC, "tools"))

from empreinte_sources import empreinte  # noqa: E402

COLIS = os.path.join(RAC, "deliverables", "DLB_sync.zip")
INIT = "src/main/resources/deepluckyblock_patch/tools/deep-lucky-block-patch.gradle"


def controler(racine=RAC):
    echecs = []

    # V1
    if not os.path.isfile(COLIS):
        print("SYNCZIP V1 : deliverables/DLB_sync.zip est absent. "
              "Construis-le : python3 tools/build_sync_zip.py")
        return 1

    with zipfile.ZipFile(COLIS) as z:
        noms = set(z.namelist())
        dans_colis = {n for n in noms if n.endswith(".java")}

        base = os.path.join(racine, "src", "main", "java")
        dans_arbre = set()
        for dossier, _, fichiers in os.walk(base):
            for f in fichiers:
                if f.endswith(".java"):
                    absolu = os.path.join(dossier, f)
                    dans_arbre.add(
                        os.path.relpath(absolu, racine).replace(os.sep, "/"))

        # V2
        manquants = sorted(dans_arbre - dans_colis)
        surnumeraires = sorted(dans_colis - dans_arbre)
        if manquants:
            echecs.append(
                "SYNCZIP V2 : %d fichier(s) de l'arbre MANQUENT dans le colis, "
                "dont %s. Le developpeur recevrait des sources incompletes."
                % (len(manquants), manquants[0]))
        if surnumeraires:
            echecs.append(
                "SYNCZIP V2 : %d fichier(s) du colis n'existent plus dans "
                "l'arbre, dont %s. Un colis ne supprime rien : ces fichiers "
                "ressusciteraient chez le developpeur."
                % (len(surnumeraires), surnumeraires[0]))

        # V3
        differents = []
        for n in sorted(dans_arbre & dans_colis):
            chemin = os.path.join(racine, n.replace("/", os.sep))
            with open(chemin, "rb") as fh:
                if z.read(n) != fh.read():
                    differents.append(n)
        if differents:
            echecs.append(
                "SYNCZIP V3 : %d fichier(s) du colis sont PERIMES, dont %s. "
                "Le colis a ete construit avant la derniere edition : "
                "reconstruis-le avant de publier."
                % (len(differents), differents[0]))

        # V4
        init_abs = os.path.join(racine, INIT.replace("/", os.sep))
        if INIT not in noms:
            echecs.append(
                "SYNCZIP V4 : le script de reparation Gradle n'est pas dans le "
                "colis. C'est pourtant le fichier que le developpeur oublie "
                "toujours de remplacer, et sans lui ni l'empreinte ni la garde "
                "n'existent chez lui.")
            contenu_init = b""
        else:
            contenu_init = z.read(INIT)
            with open(init_abs, "rb") as fh:
                if contenu_init != fh.read():
                    echecs.append(
                        "SYNCZIP V4 : le script de reparation du colis differe "
                        "de celui de l'arbre. Reconstruis le colis.")

        # V5
        if "EMPREINTE.txt" not in noms:
            echecs.append(
                "SYNCZIP V5 : EMPREINTE.txt absent du colis. La verification "
                "chez le developpeur dependrait alors d'une valeur recopiee a "
                "la main dans un message, donc potentiellement perimee.")
        else:
            attendu, nb = empreinte(racine)
            annonce = z.read("EMPREINTE.txt").decode("utf-8").split()
            if len(annonce) < 2 or annonce[0] != attendu or annonce[1] != str(nb):
                echecs.append(
                    "SYNCZIP V5 : EMPREINTE.txt annonce %r alors que l'arbre "
                    "vaut '%s %d'. Le script de mise a jour echouerait chez le "
                    "developpeur sans raison comprehensible."
                    % (" ".join(annonce), attendu, nb))

        # V6
        if contenu_init:
            texte = contenu_init.decode("utf-8", "replace")
            if "empreinte des sources Java" not in texte:
                echecs.append(
                    "SYNCZIP V6 : le script de reparation du colis n'imprime "
                    "pas l'empreinte. Le developpeur reinstallerait une version "
                    "aveugle, incapable de lui dire quelle version tourne.")
            if "Un commentaire est intercale" not in texte:
                echecs.append(
                    "SYNCZIP V6 : le script de reparation du colis ne porte pas "
                    "la garde @SubscribeEvent. Le build ne s'arreterait plus "
                    "avant le crash de chargement.")

    for e in echecs:
        print(e)
    if echecs:
        return 1
    print("synczipcheck : colis conforme (6 verrous).")
    return 0


if __name__ == "__main__":
    sys.exit(controler())
