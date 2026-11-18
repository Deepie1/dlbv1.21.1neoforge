#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
glslcheck — detecte dans les core shaders ce que la demo navigateur NE PEUT PAS voir.

POURQUOI CET OUTIL EXISTE
-------------------------
La page de demonstration compile les vrais .vsh/.fsh du mod, mais elle les compile en
GLSL ES 3.00 (WebGL 2), alors que Minecraft 1.21 les compile en GLSL 1.50 desktop. Les
deux grammaires ne reservent pas les memes mots. Le 18 novembre 2026, le mot « packed »
-- reserve en 1.50 comme qualificatif de disposition de bloc uniforme, parfaitement
ordinaire en ES 3.00 -- a traverse la demo sans une alerte et fait planter le jeu au
chargement :

    ERROR: 0:70: 'packed' : reserved word

La demo reste la meilleure preuve que le shader FAIT ce qu'on dit ; elle n'est pas une
preuve qu'il COMPILE dans le jeu. Cet outil comble precisement cet ecart, et rien d'autre.

USAGE
-----
    python3 glslcheck.py            # tous les shaders de assets/
    python3 glslcheck.py a.vsh b.fsh

Code de sortie 1 si une alerte est levee.
"""
import io
import os
import re
import sys

# Mots RESERVES par GLSL 1.50 (annexe « keywords reserved for future use ») qui ne le sont
# PAS en GLSL ES 3.00. Les utiliser comme identifiant compile dans le navigateur et casse
# dans le jeu : c'est exactement la classe de bogue invisible qu'on traque.
RESERVED = (
    'active asm cast class common enum extern external filter fixed goto half inline '
    'input interface long namespace noinline output packed partition patch public '
    'resource row_major column_major sampler3DRect shared short sizeof static subroutine '
    'superp template this typedef union unsigned using volatile'
).split()

# Pieges deja payes sur ce projet, verifies en meme temps parce qu'ils ont le meme profil :
# verts au banc, rouges en jeu.
BANNED_PATTERNS = [
    (r'#moj_import', "#moj_import hors vanilla : recopier le contenu a la main"),
    (r'\bconst\s+vec[234]\s+\w+\s*\[', "ES 3.00 refuse « const vecN X[] » : utiliser un if/else"),
]

DEFAULT_ROOT = os.path.join('src', 'main', 'resources', 'assets')


def strip_comments(src):
    src = re.sub(r'/\*.*?\*/', ' ', src, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', src)


def check(path):
    src = io.open(path, encoding='utf-8').read()
    body = strip_comments(src)
    alerts = []
    for word in RESERVED:
        for m in re.finditer(r'\b' + re.escape(word) + r'\b', body):
            line = body[:m.start()].count('\n') + 1
            alerts.append((line, "« %s » est un mot reserve de GLSL 1.50 "
                                 "(le jeu refuse de compiler ; le navigateur, lui, accepte)"
                           % word))
    for pattern, why in BANNED_PATTERNS:
        for m in re.finditer(pattern, body):
            line = body[:m.start()].count('\n') + 1
            alerts.append((line, why))
    return sorted(alerts)


def collect(args):
    if args:
        return list(args)
    found = []
    for root, _dirs, files in os.walk(DEFAULT_ROOT):
        for name in sorted(files):
            if name.endswith(('.vsh', '.fsh', '.glsl')):
                found.append(os.path.join(root, name))
    return found


def main(argv):
    paths = collect(argv[1:])
    if not paths:
        print('glslcheck : aucun shader trouve sous %s' % DEFAULT_ROOT)
        return 1
    total = 0
    for path in paths:
        for line, why in check(path):
            print('ERR  %s:%d  %s' % (os.path.basename(path), line, why))
            total += 1
    print('glslcheck : %d shader(s), %d alerte(s)' % (len(paths), total))
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
