#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
EMPREINTE DES SOURCES JAVA — pour savoir, sans discuter, QUELLE version tourne.

POURQUOI
    Trois lancements de suite ont crashe avec le meme message alors que le
    correctif etait livre depuis des heures. A chaque fois le journal disait
    « compileJava UP-TO-DATE » : les fichiers du poste n'avaient pas bouge.
    Mais il a fallu lire le journal ligne par ligne pour s'en apercevoir, et le
    developpeur, lui, n'avait aucun moyen de le voir.

    Desormais le script de reparation Gradle imprime cette empreinte a CHAQUE
    build. Une ligne, visible dans la console MCreator :

        [DLB-PATCH] empreinte des sources Java : a1b2c3d4e5f6 (226 fichiers)

    Elle suffit a trancher : si elle ne correspond pas a celle annoncee avec la
    livraison, le workspace est perime, point final. Plus de supposition.

DEFINITION — volontairement triviale, pour etre reproductible a l'identique
en Groovy (cote Gradle) et en Python (ici) :
    - tous les .java sous src/main/java, chemins relatifs en separateurs '/'
    - tries par ordre d'octets du chemin
    - pour chacun : octets du chemin, '\\n', octets du fichier, '\\n'
    - SHA-1 de l'ensemble, 12 premiers caracteres hexadecimaux
Les fichiers sont lus en OCTETS : aucune question d'encodage ni de fin de ligne.

Le workflow GitHub compare la valeur calculee ici a celle imprimee par Gradle.
Si les deux implementations divergent, le banc echoue : une empreinte a
laquelle on ne peut pas se fier serait pire qu'une absence d'empreinte.
"""

import hashlib
import os
import sys


def empreinte(racine="."):
    base = os.path.join(racine, "src", "main", "java")
    if not os.path.isdir(base):
        print("src/main/java introuvable sous %s" % racine, file=sys.stderr)
        return None, 0

    chemins = []
    for dossier, _, fichiers in os.walk(base):
        for f in fichiers:
            if f.endswith(".java"):
                absolu = os.path.join(dossier, f)
                rel = os.path.relpath(absolu, base).replace(os.sep, "/")
                chemins.append((rel, absolu))
    chemins.sort(key=lambda t: t[0].encode("utf-8"))

    h = hashlib.sha1()
    for rel, absolu in chemins:
        h.update(rel.encode("utf-8"))
        h.update(b"\n")
        with open(absolu, "rb") as fh:
            h.update(fh.read())
        h.update(b"\n")
    return h.hexdigest()[:12], len(chemins)


if __name__ == "__main__":
    racine = sys.argv[1] if len(sys.argv) > 1 else "."
    e, n = empreinte(racine)
    if e is None:
        sys.exit(2)
    print("%s %d" % (e, n))
