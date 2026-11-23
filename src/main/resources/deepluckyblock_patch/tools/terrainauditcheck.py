#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
terrainauditcheck -- UN AUDIT QUI MODIFIE CE QU'IL OBSERVE NE MESURE RIEN.

Pourquoi ce banc existe. Huit tours de correctifs sur le raccord de terrain du Crimson
Lake (T459 a T468) ont ete poses puis annules en bloc, sans jamais reparer quoi que ce
soit. La cause tenait en une phrase : personne n'avait jamais VU le terrain. T471 ajoute
donc une instrumentation -- et une instrumentation est exactement le genre de code qu'on
casse sans s'en apercevoir, puisque rien dans le jeu ne s'arrete quand elle ment.

Les quatre facons de la casser, dans l'ordre de gravite :

  1. LA RENDRE ACTIVE. Une sonde qui charge ou genere les chunks qu'elle lit detruit la
     mesure : elle montre un terrain que le pipeline n'avait pas. Pire, T304 l'a chiffre,
     une generation a froid coute 8 a 12 s -- cinq cents chunks d'anneau et la partie est
     morte. L'audit ne doit employer QUE des lectures passives.

  2. LA RENDRE AVEUGLE A UNE ETAPE. Trois jalons portent tout le diagnostic demande :
     avant le clear (le relief d'origine), apres le clear (des chunks ont-ils ete
     sautes), apres la pose (l'ancien terrain subsiste-t-il). Il en manque un, et la
     question correspondante redevient sans reponse.

  3. LA LAISSER PARLER TROP TARD. La derniere photographie doit precede le relachement
     des chunks : apres ChunkKeeper.release ils peuvent etre decharges, et toute la zone
     se lirait « absente ». L'ordre de ces deux lignes est le banc tout entier.

  4. LA LAISSER TOURNER TOUTE SEULE. Armee en permanence, elle couterait son prix a
     chaque spawn naturel, pour personne.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/terrainauditcheck.py [racine]
Sortie :  code 0 si les six verrous tiennent, 1 sinon.
"""

import io
import os
import re
import sys

AUDIT = 'src/main/java/deepluckyblock/util/TerrainAudit.java'
LAC = 'src/main/java/deepluckyblock/procedures/CrimsonLakeStructureSpawnProcedure.java'

# Jalons sans lesquels une des questions posees reste sans reponse.
JALONS = ('AVANT_CLEAR', 'APRES_CLEAR', 'APRES_PASTE')

# Appels qui chargent, generent ou ATTENDENT un chunk. Interdits dans l'audit.
#   getChunk      -> getChunk(..., FULL, true) : generation synchrone (T56)
#   getChunkNow   -> attend la fin d'une generation en vol (T301-B, 7,7 ms mesurees)
#   request       -> met le chunk en file de generation (T304)
#   level.getBlockState / getHeight -> passent par getChunk : meme piege
ACTIFS = (
    r'\.getChunk\s*\(',
    r'\.getChunkNow\s*\(',
    r'SafeSurface\.request\s*\(',
    r'\blevel\.getBlockState\s*\(',
    r'\blevel\.getHeight\s*\(',
    r'\bforceLoad\s*\(',
    r'\bsetChunkForced\s*\(',
)


def lire(racine, rel):
    chemin = os.path.join(racine, rel)
    if not os.path.exists(chemin):
        return None
    with io.open(chemin, encoding='utf-8') as f:
        return f.read()


def sans_commentaires(src):
    """Retire blocs et lignes de commentaire : une regle ne doit jamais mordre sur de la prose."""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'(?m)//.*$', '', src)
    return src


def controler(racine='.'):
    echecs = []
    audit = lire(racine, AUDIT)
    lac = lire(racine, LAC)

    if audit is None:
        print('ECHEC  fichier introuvable : %s' % AUDIT)
        return 1
    if lac is None:
        print('ECHEC  fichier introuvable : %s' % LAC)
        return 1

    audit_code = sans_commentaires(audit)
    lac_code = sans_commentaires(lac)

    # ------------------------------------------------------------------ VERROU 1
    # Lecture strictement passive.
    for motif in ACTIFS:
        trouve = re.search(motif, audit_code)
        if trouve:
            echecs.append('VERROU 1 : TerrainAudit appelle %s -- cet appel charge, genere ou '
                          'attend un chunk ; la sonde modifierait le terrain qu\'elle mesure'
                          % trouve.group(0).strip())

    # La lecture de chunk doit passer par le cache passif, et par lui seul.
    if 'SafeSurface.cached(' not in audit_code:
        echecs.append('VERROU 1 : TerrainAudit ne lit plus les chunks via SafeSurface.cached '
                      '-- c\'est la seule lecture qui ne declenche jamais de generation')

    # ------------------------------------------------------------------ VERROU 2
    # Les trois jalons existent, et sont bien poses dans le pipeline du lac.
    for jalon in JALONS:
        if jalon not in lac_code:
            echecs.append('VERROU 2 : le jalon %s n\'est plus capture dans le pipeline du lac '
                          '-- la question a laquelle il repond redevient sans reponse' % jalon)

    # ------------------------------------------------------------------ VERROU 3
    # La derniere photographie precede le relachement des chunks.
    # Comptage sur le FICHIER ENTIER : borner une methode par accolades est faux des que
    # le fichier contient des chaines a accolades (lecon de la cassure E, T470).
    i_snap = lac_code.find('TerrainAudit.snapshot(j.level, "APRES_PASTE")')
    i_rel = lac_code.find('ChunkKeeper.release(j.level)')
    if i_snap < 0:
        echecs.append('VERROU 3 : plus de photographie APRES_PASTE sur le job de pose')
    elif i_rel < 0:
        echecs.append('VERROU 3 : ChunkKeeper.release(j.level) introuvable -- ancrage perdu, '
                      'le banc ne peut plus verifier l\'ordre')
    elif i_snap > i_rel:
        echecs.append('VERROU 3 : APRES_PASTE est capture APRES ChunkKeeper.release -- les '
                      'chunks peuvent deja etre decharges, toute la zone se lirait « absente »')

    # Meme exigence pour l'ecriture du fichier.
    i_fin = lac_code.find('TerrainAudit.finish(j.level)')
    if i_fin >= 0 and i_rel >= 0 and i_fin > i_rel:
        echecs.append('VERROU 3 : le rapport est ecrit apres ChunkKeeper.release')

    # ------------------------------------------------------------------ VERROU 4
    # L'audit ne s'arme pas tout seul.
    m = re.search(r'private\s+static\s+volatile\s+boolean\s+armed\s*=\s*([^;]+);', audit_code)
    if not m:
        echecs.append('VERROU 4 : le drapeau « armed » a disparu -- l\'audit tournerait a '
                      'chaque spawn naturel')
    elif 'true' in m.group(1) and 'getBoolean' not in m.group(1):
        echecs.append('VERROU 4 : l\'audit est arme en dur (armed = %s) -- il couterait son '
                      'prix a chaque spawn naturel, pour personne' % m.group(1).strip())

    # ... et il se desarme une fois la session consommee.
    # Comptage des seules REAFFECTATIONS : la declaration du champ porte la meme
    # expression et gonflerait le total de 1, ce qui laisserait passer la suppression
    # d'un desarmement (cassure E, exactement le piege de T470). D'ou l'ancrage en debut
    # de ligne, qui exclut « private static volatile boolean armed = ... ».
    rearmements = re.findall(r'(?m)^\s*armed\s*=\s*Boolean\.getBoolean\s*\(', audit_code)
    if len(rearmements) < 2:
        echecs.append('VERROU 4 : %d desarmement(s) de fin de session au lieu de 2 (finish ET '
                      'abort) -- la construction suivante serait auditee a l\'insu du joueur'
                      % len(rearmements))

    # ------------------------------------------------------------------ VERROU 5
    # Un seul fichier rendu, et son chemin est annonce hors de la limitation de debit.
    if not re.search(r'ZipOutputStream', audit_code):
        echecs.append('VERROU 5 : le rapport n\'est plus un ZIP unique -- la demande porte '
                      'explicitement sur UN seul fichier a renvoyer')
    if 'announceLakeProgress' in audit_code:
        echecs.append('VERROU 5 : l\'audit annonce via announceLakeProgress, limite a un '
                      'message toutes les 20 s -- c\'est justement celui qui donne le '
                      'fichier qui serait avale')

    # ------------------------------------------------------------------ VERROU 6
    # Le repli par bruit reste passif ET reste un repli (jamais la source principale).
    if 'getBaseHeight(' in audit_code:
        if 'OCEAN_FLOOR_WG' not in audit_code:
            echecs.append('VERROU 6 : le repli par bruit n\'utilise plus OCEAN_FLOOR_WG -- '
                          'c\'est la seule heightmap disponible sans generer le chunk')
    else:
        echecs.append('VERROU 6 : plus de repli par bruit -- l\'anneau exterieur non charge '
                      'redeviendrait illisible, or c\'est la que vit le raccord')

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('terrainauditcheck : %d verrou(x) rompu(s) -- l\'audit de terrain ne peut plus '
              'etre cru' % len(echecs))
        return 1

    print('terrainauditcheck : 6 verrous tiennent -- sonde passive, 3 jalons, capture avant '
          'release, armement explicite, fichier unique, repli par bruit')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
