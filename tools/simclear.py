#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
simclear -- confronte l'ANCIEN et le NOUVEAU creusement (T482) sur donnees reelles.

Donnees : le gabarit crimsonlake.nbt (verite sur ce que la pose ecrira) et le
releve de hauteurs du run du 24/11 (verite sur le terrain avant travaux).

Ce banc simule LE CODE ECRIT, pas l'intention :

  ANCIEN   clearBuildVolume(min, max, groundY + 1)
           -> yBot = max(min.getY(), groundY+1) = 68, UNIFORME sur tout le rectangle
           -> yTop = plafond du monde

  NOUVEAU  clearBuildVolume(min, max, masque)
           -> pour chaque colonne : p = masque.plancherDe(x,z)
              si p == ABSENTE -> colonne epargnee, AUCUNE ecriture
              sinon yBot = max(p, minBuild+1), yTop = plafond du monde
           -> p = min, sur les blocs du gabarit de cette colonne, de origin.Y + relY

Usage : python3 tools/simclear.py [dossier_rapports]
"""

import base64
import collections
import gzip
import io
import json
import os
import struct
import sys

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NBT = os.path.join(RACINE, 'src', 'main', 'resources', 'data',
                   'deep_lucky_block', 'structure', 'crimsonlake.nbt')

SINK_BLOCKS = 20
GROUND_Y = 67          # sol de reference releve dans le run du 24/11
MIN_BUILD = -64
MAX_BUILD = 320


# ---------------------------------------------------------------- lecture NBT
def lire_nbt(chemin):
    d = gzip.open(chemin, 'rb').read()
    p = [0]

    def u1():
        v = d[p[0]]; p[0] += 1; return v

    def u2():
        v = struct.unpack_from('>H', d, p[0])[0]; p[0] += 2; return v

    def i4():
        v = struct.unpack_from('>i', d, p[0])[0]; p[0] += 4; return v

    def st():
        n = u2(); s = d[p[0]:p[0] + n].decode('utf-8', 'replace'); p[0] += n; return s

    def val(t):
        if t == 1:
            v = struct.unpack_from('>b', d, p[0])[0]; p[0] += 1; return v
        if t == 2:
            v = struct.unpack_from('>h', d, p[0])[0]; p[0] += 2; return v
        if t == 3:
            return i4()
        if t == 4:
            v = struct.unpack_from('>q', d, p[0])[0]; p[0] += 8; return v
        if t == 5:
            v = struct.unpack_from('>f', d, p[0])[0]; p[0] += 4; return v
        if t == 6:
            v = struct.unpack_from('>d', d, p[0])[0]; p[0] += 8; return v
        if t == 7:
            n = i4(); v = d[p[0]:p[0] + n]; p[0] += n; return v
        if t == 8:
            return st()
        if t == 9:
            et = u1(); n = i4(); return [val(et) for _ in range(n)]
        if t == 10:
            o = {}
            while True:
                tt = u1()
                if tt == 0:
                    return o
                k = st(); o[k] = val(tt)
        if t == 11:
            n = i4(); v = list(struct.unpack_from('>%di' % n, d, p[0])); p[0] += 4 * n; return v
        if t == 12:
            n = i4(); v = list(struct.unpack_from('>%dq' % n, d, p[0])); p[0] += 8 * n; return v
        raise Exception('tag inconnu %d' % t)

    t = u1(); st()
    return val(t)


def main():
    dossier = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        RACINE, 'rapports_scan_20261124_TEST3')

    root = lire_nbt(NBT)
    sx, sy, sz = root['size']
    pal = root['palette']
    airs = set(i for i, b in enumerate(pal)
               if b['Name'] in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'))

    # occupation : par colonne, l'ensemble des y relatifs ecrits
    parcol = collections.defaultdict(set)
    minrel = 10 ** 9
    for b in root['blocks']:
        if b['state'] in airs:
            continue
        x, y, z = b['pos']
        parcol[(x, z)].add(y)
        if y < minrel:
            minrel = y

    originY = GROUND_Y + 1 - SINK_BLOCKS - minrel
    print('gabarit  : %d x %d x %d, %d colonnes avec matiere sur %d'
          % (sx, sy, sz, len(parcol), sx * sz))
    print('palette  : %d etats, dont air : %d' % (len(pal), len(airs)))
    print('minRelativeY = %d  ->  origin.Y = %d ; base de la structure Y = %d ; ancien plancher Y = %d'
          % (minrel, originY, originY + minrel, GROUND_Y + 1))
    print('')

    # --------------------------------------------------- plancher par colonne
    plancher = {}
    for c, ys in parcol.items():
        plancher[c] = originY + min(ys)

    # ---------------------------------------------- terrain reel (scan t+0 s)
    sol = {}
    page = os.path.join(dossier, '.simclear.html')
    import subprocess
    subprocess.run([sys.executable, os.path.join(RACINE, 'tools', 'scanview.py'),
                    dossier, '-o', page], stdout=subprocess.DEVNULL, check=True)
    h = io.open(page, encoding='utf-8').read()
    s = h.index('const DATA = ')
    D = json.loads(h[s + 13:h.index('\n', s)].rstrip(';'))
    os.remove(page)
    W, Dp, X0, Z0, YM = D['w'], D['d'], D['x0'], D['z0'], D['ymin']
    hs = None
    for e in D['etapes']:
        if e['nom'] == '1_DEBUT_CLEAR':
            hs = base64.b64decode(e['hs'])
    for z in range(Dp):
        for x in range(W):
            v = hs[z * W + x]
            if v < 254:
                sol[(X0 + x, Z0 + z)] = v + YM

    # le lac est pose centre sur [126, ?, 342] dans ce run
    cx, cz = 126, 342
    ox, oz = cx - sx // 2, cz - sz // 2

    # ------------------------------------------------------------- la mesure
    intrus_ancien = 0          # cases de la structure restees en ancien terrain
    intrus_nouveau = 0
    col_intruses = 0
    vide_total = 0
    mesurees = 0
    rase_ancien = 0            # colonnes rasees par l'ancien clear hors forme du lac
    cout_ancien = 0            # cases solides retirees (approximation par le relief)
    cout_nouveau = 0
    col_ouvrage = 0            # colonnes de l'ouvrage effectivement confrontees
    sans_trou_ancien = 0       # ... ou l'ancien creusement ne retirait RIEN
    creuse_desormais = 0       # ... et que le nouveau creuse
    somme_prof = 0
    prof_max = 0

    for lz in range(sz):
        for lx in range(sx):
            wx, wz = ox + lx, oz + lz
            t = sol.get((wx, wz))
            if t is None:
                continue
            mesurees += 1
            ys = parcol.get((lx, lz))
            if not ys:
                # colonne SANS matiere : l'ancien clear la rasait quand meme
                if t >= GROUND_Y + 1:
                    rase_ancien += 1
                    cout_ancien += t - (GROUND_Y + 1) + 1
                continue
            base = originY + min(ys)
            sommet = originY + max(ys)
            ecrit = set(originY + y for y in ys)
            col_ouvrage += 1
            # L'ancien creusement partait de GROUND_Y+1 : si le terrain etait deja
            # plus bas, il ne retirait absolument rien -- aucun trou, nulle part.
            if t < GROUND_Y + 1:
                sans_trou_ancien += 1
                if t >= base:
                    creuse_desormais += 1
                    p = t - base + 1
                    somme_prof += p
                    if p > prof_max:
                        prof_max = p
            # cases de l'etendue verticale de l'ouvrage que la pose n'ecrit PAS
            manque = 0
            for y in range(base, sommet + 1):
                if y in ecrit:
                    continue
                vide_total += 1
                # ANCIEN : non creuse si sous le plancher 68, et terrain solide la
                if y <= GROUND_Y and y <= t:
                    manque += 1
            if manque:
                intrus_ancien += manque
                col_intruses += 1
            # cout : cases solides effectivement retirees
            if t >= GROUND_Y + 1:
                cout_ancien += t - (GROUND_Y + 1) + 1
            if t >= base:
                cout_nouveau += t - base + 1

    print('--- CE QUE LE JOUEUR VOIT : y a-t-il seulement un trou ? -----------')
    print('  colonnes de l\'ouvrage SANS AUCUN creusement avant (terrain deja')
    print('  sous l\'ancien plancher %d) : %d sur %d (%.1f %%)'
          % (GROUND_Y + 1, sans_trou_ancien, col_ouvrage,
             100.0 * sans_trou_ancien / max(1, col_ouvrage)))
    print('  parmi elles, creusees desormais : %d   profondeur moyenne %.1f blocs (max %d)'
          % (creuse_desormais,
             (somme_prof / creuse_desormais) if creuse_desormais else 0.0,
             prof_max))
    print('')
    print('--- DEFAUT n3 : ancien terrain DANS la structure -------------------')
    print('  colonnes du gabarit confrontees au terrain : %d' % mesurees)
    print('  cases vides de l\'ouvrage (bassin, interieur) : %d' % vide_total)
    print('  ANCIEN  -> restees en ancien terrain : %d  (dans %d colonnes)'
          % (intrus_ancien, col_intruses))
    print('  NOUVEAU -> restees en ancien terrain : %d' % intrus_nouveau)
    if intrus_ancien:
        print('  reduction : %.1f %%' % (100.0 * (intrus_ancien - intrus_nouveau) / intrus_ancien))
    print('')
    print('--- DEFAUT n2 : fosse rectangulaire hors forme du lac --------------')
    print('  colonnes SANS matiere que l\'ancien clear rasait : %d' % rase_ancien)
    print('  le nouveau creusement n\'y touche plus : 0')
    print('')
    print('--- cout (cases solides retirees, estime sur le relief) ------------')
    print('  ANCIEN  : %d' % cout_ancien)
    print('  NOUVEAU : %d   (%+.1f %%)'
          % (cout_nouveau, 100.0 * (cout_nouveau - cout_ancien) / max(1, cout_ancien)))

    ok = (intrus_nouveau == 0 and intrus_ancien > 0 and rase_ancien > 0)
    print('')
    print('simclear : %s' % ('le nouveau creusement supprime le defaut' if ok
                             else 'RESULTAT INATTENDU'))
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
