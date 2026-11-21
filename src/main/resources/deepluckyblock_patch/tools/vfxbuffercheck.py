#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
vfxbuffercheck -- DEUX RENDERTYPE MAISON EN MEME TEMPS EXIGENT UNE SOURCE POSSEDEE.

LE DEFAUT, DEUX FOIS.

    java.lang.IllegalStateException: Not building!
        at BufferBuilder.ensureBuilding(BufferBuilder.java:68)

MultiBufferSource.BufferSource -- celui que rend Minecraft.renderBuffers().bufferSource()
-- ne possede QU'UN SEUL BufferBuilder partage pour tous les RenderType absents de sa
carte interne `fixedBuffers`. Demander un deuxieme RenderType maison TERMINE donc le
premier (endBatch implicite) :

    add   = buffers.getBuffer(getAdditiveType());     // ouvre le builder partage
    alpha = buffers.getBuffer(getTranslucentType());  // le TERMINE
    ... ecrire un sommet dans `add` -> "Not building!"

La forme est traitresse : rien ne signale l'invalidation, le premier consommateur reste
une reference parfaitement valide en Java. Seul le premier sommet emis leve.

HISTORIQUE -- ET RAISON D'ETRE DE CE BANC.
  * T370 l'a corrige dans WeaponWorldVfxManager (slash, Astral, Chrono, rayon Seraph) en
    se donnant une source POSSEDEE dont les deux RenderType sont declares "fixed".
  * T446 a du le corriger A NOUVEAU dans CosmeticPixelAura : la passe cosmetique etait
    restee sur la source vanilla. Symptome : le halo continu (flags=0x800) apparaissait
    une image puis disparaissait definitivement.

Corriger un fichier ne corrige pas une FORME. Ce banc verrouille la forme partout.

LA REGLE
    Tout fichier qui demande deux RenderType DIFFERENTS a une source de tampons doit
    obtenir cette source d'une fabrique possedee (MultiBufferSource.immediateWithBuffers),
    jamais directement de renderBuffers().bufferSource().

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/vfxbuffercheck.py [racine]
Sortie :  code 0 si le verrou tient, 1 sinon.
"""

import io
import os
import re
import sys

RACINE_JAVA = os.path.join('src', 'main', 'java')
EXCLU = 'FIXES_MCREATOR'

SOURCE_VANILLA = re.compile(r'renderBuffers\s*\(\s*\)\s*\.\s*bufferSource\s*\(\s*\)')
SOURCE_POSSEDEE = re.compile(r'MultiBufferSource\s*\.\s*immediateWithBuffers\s*\(')
APPEL_GETBUFFER = re.compile(r'\.\s*getBuffer\s*\(')


def sans_commentaires(src):
    """Retire blocs et lignes de commentaire, en gardant le nombre de lignes."""
    src = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group(0).count('\n'), src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


def argument(src, i):
    """Texte de l'argument d'un appel dont la parenthese ouvrante est en i, parentheses comptees."""
    prof = 0
    for k in range(i, len(src)):
        if src[k] == '(':
            prof += 1
        elif src[k] == ')':
            prof -= 1
            if prof == 0:
                return ' '.join(src[i + 1:k].split())
    return None


def fichiers_java(racine):
    base = os.path.join(racine, RACINE_JAVA)
    trouves = []
    for dossier, _, noms in os.walk(base):
        if EXCLU in dossier:
            continue
        for nom in noms:
            if nom.endswith('.java'):
                trouves.append(os.path.join(dossier, nom))
    return sorted(trouves)


def controler(racine):
    base = os.path.join(racine, RACINE_JAVA)
    if not os.path.isdir(base):
        print('vfxbuffercheck : arborescence Java introuvable sous %s' % racine)
        return 1

    echecs = []
    examines = 0
    concernes = 0

    for chemin in fichiers_java(racine):
        src = sans_commentaires(io.open(chemin, encoding='utf-8').read())
        examines += 1

        if not SOURCE_VANILLA.search(src):
            continue

        # Arguments distincts passes a getBuffer dans ce fichier.
        args = []
        for m in APPEL_GETBUFFER.finditer(src):
            a = argument(src, m.end() - 1)
            if a and a not in args:
                args.append(a)
        if len(args) < 2:
            continue

        concernes += 1
        if SOURCE_POSSEDEE.search(src):
            continue

        rel = os.path.relpath(chemin, racine)
        echecs.append(
            '%s : demande %d RenderType differents (%s) mais prend sa source de\n'
            '        renderBuffers().bufferSource() -- le second getBuffer termine le premier,\n'
            '        tout sommet ecrit ensuite dans le premier leve "Not building!".\n'
            '        Corriger comme T370/T446 : une source possedee via\n'
            '        MultiBufferSource.immediateWithBuffers(fixed, partage).'
            % (rel, len(args), ', '.join(args[:4])))

    if echecs:
        print('vfxbuffercheck : %d FICHIER(S) EN DEFAUT\n' % len(echecs))
        for e in echecs:
            print('  !! %s\n' % e)
        return 1

    print('vfxbuffercheck : verrou tenu -- %d fichier(s) examine(s), %d combinant deux '
          'RenderType passent tous par une source possedee' % (examines, concernes))
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
