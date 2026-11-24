#!/usr/bin/env python3
"""
simtalus -- rejoue le raccord de terrain ECRIT dans transformeeDistance()/preparer()
et mesure la marche verticale au contact de l'ouvrage, cote par cote.

Piege evite : une premiere version partait du gabarit NBT et d'une origine Y
supposee. Elle annoncait 18 blocs de marche sur les QUATRE cotes alors que le
scan du 24 nov. en mesure 0,3 a l'est et 14 a l'ouest : la simulation etait
fausse, pas le scan. On part donc du relief REELLEMENT MESURE :
  - surface de l'ouvrage      = etape 3_APRES_PASTE sur les colonnes d'emprise
  - sol naturel               = etape 1_DEBUT_CLEAR
  - marche ANCIENNE           = ce que le scan montre, sans modele
  - marche NOUVELLE           = simulee avec l'ancre propagee
Sortie 0 si la marche simulee passe sous le seuil sur les quatre cotes ET si la
pente ne cache pas une falaise reportee plus loin.
"""
import base64, collections, importlib.util, io, json, os, statistics, subprocess, sys

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TALUS_LARGEUR = 48
SEUIL = 6
SEUIL_PENTE = 6                 # marche maxi entre deux colonnes voisines de la rampe

spec = importlib.util.spec_from_file_location('sc', os.path.join(RACINE, 'tools', 'simclear.py'))
sc = importlib.util.module_from_spec(spec); spec.loader.exec_module(sc)


def reliefs(dossier):
    page = os.path.join('/tmp', '.simtalus.html')
    subprocess.run([sys.executable, os.path.join(RACINE, 'tools', 'scanview.py'),
                    dossier, '-o', page], stdout=subprocess.DEVNULL, check=True)
    h = io.open(page, encoding='utf-8').read()
    s = h.index('const DATA = ')
    D = json.loads(h[s + 13:h.index('\n', s)].rstrip(';'))
    os.remove(page)
    out = {}
    for e in D['etapes']:
        hs = base64.b64decode(e['hs'])
        m = {}
        for z in range(D['d']):
            for x in range(D['w']):
                v = hs[z * D['w'] + x]
                if v < 254:
                    m[(D['x0'] + x, D['z0'] + z)] = v + D['ymin']
        out[e['nom']] = m
    return out


def secteur(wx, wz, x0, x1, z0, z1):
    d = {'OUEST': wx - x0, 'EST': x1 - wx, 'NORD': wz - z0, 'SUD': z1 - wz}
    return min(d, key=d.get)


def table(titre, sect, seuil):
    print(titre)
    print('  secteur  paires   moyenne    max   >=%d blocs' % seuil)
    pire = 0.0
    for k in ('OUEST', 'EST', 'NORD', 'SUD'):
        v = sect.get(k) or []
        if not v:
            print('  %-7s       0        -      -        -' % k)
            continue
        n = sum(1 for a in v if a >= seuil)
        pire = max(pire, max(v))
        print('  %-7s %7d   %7.2f  %5.1f   %5d (%4.1f %%)'
              % (k, len(v), statistics.mean(v), max(v), n, 100.0 * n / len(v)))
    print('')
    return pire


def main():
    dossier = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        RACINE, 'rapports_scan_20261124_TEST3')
    root = sc.lire_nbt(sc.NBT)
    sx, sy, sz = root['size']
    pal = root['palette']
    airs = set(i for i, b in enumerate(pal)
               if b['Name'] in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'))
    cols = set()
    for b in root['blocks']:
        if b['state'] in airs:
            continue
        x, y, z = b['pos']
        cols.add((x, z))
    ox, oz = 3, 200
    rel = reliefs(dossier)
    final, naturel = rel['3_APRES_PASTE'], rel['1_DEBUT_CLEAR']

    emp = {}
    for (lx, lz) in cols:
        c = (ox + lx, oz + lz)
        if c in final:
            emp[c] = final[c]
    if not emp:
        print('ECHEC  aucune colonne d\'emprise mesuree')
        return 1
    xs = [c[0] for c in emp]; zs = [c[1] for c in emp]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    rx0, rz0 = x0 - TALUS_LARGEUR, z0 - TALUS_LARGEUR
    rw = (x1 + TALUS_LARGEUR) - rx0 + 1
    rd = (z1 + TALUS_LARGEUR) - rz0 + 1
    print('emprise x %d..%d  z %d..%d   %d colonnes mesurees   anneau %d x %d\n'
          % (x0, x1, z0, z1, len(emp), rw, rd))

    INF = 1 << 28
    d = [INF] * (rw * rd)
    anc = [None] * (rw * rd)
    for (wx, wz), ys in emp.items():
        i = (wz - rz0) * rw + (wx - rx0)
        d[i] = 0; anc[i] = ys
    for iz in range(rd):
        for ix in range(rw):
            i = iz * rw + ix
            if d[i] == 0: continue
            m, a = d[i], anc[i]
            for dx, dz, w in ((-1, 0, 3), (0, -1, 3), (-1, -1, 4), (1, -1, 4)):
                jx, jz = ix + dx, iz + dz
                if 0 <= jx < rw and 0 <= jz < rd:
                    j = jz * rw + jx
                    if d[j] + w < m: m, a = d[j] + w, anc[j]
            d[i], anc[i] = m, a
    for iz in range(rd - 1, -1, -1):
        for ix in range(rw - 1, -1, -1):
            i = iz * rw + ix
            if d[i] == 0: continue
            m, a = d[i], anc[i]
            for dx, dz, w in ((1, 0, 3), (0, 1, 3), (1, 1, 4), (-1, 1, 4)):
                jx, jz = ix + dx, iz + dz
                if 0 <= jx < rw and 0 <= jz < rd:
                    j = jz * rw + jx
                    if d[j] + w < m: m, a = d[j] + w, anc[j]
            d[i], anc[i] = m, a

    w3 = TALUS_LARGEUR * 3
    TALUS_FLOU_PASSES = 6
    GROUNDY = int(statistics.median([naturel[c] for c in naturel if c in emp] or [68])) + 1

    # Le champ est construit PUIS lisse, exactement comme preparer() : six passes
    # de moyenne 3x3, empreinte et au-dela de la rampe epinglees. L'omettre ferait
    # simuler autre chose que le code ecrit -- le lissage est precisement ce qui
    # rattrape les sauts d'ancre entre deux colonnes de perimetre eloignees.
    champs = {}

    def construire(avec_ancre):
        ch = [None] * (rw * rd)
        for iz in range(rd):
            for ix in range(rw):
                i = iz * rw + ix
                wx, wz = rx0 + ix, rz0 + iz
                if (wx, wz) in emp:
                    # Fidelite au code : l'ANCIEN preparer() epinglait l'empreinte a
                    # groundY (plancher constant), le NOUVEAU a la surface reelle.
                    # Epingler la vraie surface dans les deux cas flattait l'ancien
                    # profil -- il tombait a 3,1 quand le scan en mesure 12.
                    ch[i] = float(emp[(wx, wz)]) if avec_ancre else float(GROUNDY)
                    continue
                sn = naturel.get((wx, wz))
                if sn is None: continue
                u = min(1.0, d[i] / float(w3))
                k = 1.0 - (1.0 - u) ** 3
                a = GROUNDY if (not avec_ancre or anc[i] is None) else anc[i]
                ch[i] = a + (sn - a) * k
        for _ in range(TALUS_FLOU_PASSES):
            out = list(ch)
            for iz in range(1, rd - 1):
                for ix in range(1, rw - 1):
                    i = iz * rw + ix
                    wx, wz = rx0 + ix, rz0 + iz
                    if (wx, wz) in emp or d[i] >= w3: continue
                    acc = 0.0; n = 0
                    for dz in (-1, 0, 1):
                        for dx in (-1, 0, 1):
                            v = ch[(iz + dz) * rw + ix + dx]
                            if v is not None: acc += v; n += 1
                    if n: out[i] = acc / n
            ch = out
        return ch

    def but(wx, wz, avec_ancre):
        if (wx, wz) in emp: return emp[(wx, wz)]
        if not (rx0 <= wx < rx0 + rw and rz0 <= wz < rz0 + rd): return None
        if avec_ancre not in champs: champs[avec_ancre] = construire(avec_ancre)
        return champs[avec_ancre][(wz - rz0) * rw + (wx - rx0)]

    # Relief PROPRE de l'ouvrage : une partie de la marche au contact est inscrite
    # dans la structure elle-meme (falaises internes jusqu'a 137 blocs). Le raccord
    # n'en est pas responsable et ne peut pas la supprimer. On mesure donc l'EXCES
    # de la marche sur le relief local de l'ouvrage.
    propre = {}
    for c in emp:
        v = [abs(emp[w] - emp[c]) for w in ((c[0]+1,c[1]), (c[0]-1,c[1]),
                                            (c[0],c[1]+1), (c[0],c[1]-1)) if w in emp]
        propre[c] = max(v) if v else 0

    # --- 1. ce que le scan MONTRE aujourd'hui (aucun modele)
    mes = collections.defaultdict(list)
    for (wx, wz) in emp:
        for v in ((wx+1, wz), (wx-1, wz), (wx, wz+1), (wx, wz-1)):
            if v in emp: continue
            if v in final:
                e = abs(final[v] - emp[(wx, wz)]) - propre[(wx, wz)]
                mes[secteur(wx, wz, x0, x1, z0, z1)].append(max(0.0, e))
    table('MESURE  exces de marche au contact, scan du 24 nov. (marche moins relief propre)',
          mes, SEUIL)

    # --- 2/3. simulations
    pires = {}
    sect_par = {}
    for titre, avec in (('SIMULE  ancien profil : plancher constant groundY=%d' % GROUNDY, False),
                        ('SIMULE  nouveau profil : ancre = surface reelle de l\'ouvrage', True)):
        sect = collections.defaultdict(list)
        for (wx, wz) in emp:
            for v in ((wx+1, wz), (wx-1, wz), (wx, wz+1), (wx, wz-1)):
                if v in emp: continue
                c = but(v[0], v[1], avec)
                if c is None: continue
                e = abs(c - emp[(wx, wz)]) - propre[(wx, wz)]
                sect[secteur(wx, wz, x0, x1, z0, z1)].append(max(0.0, e))
        pires[avec] = table(titre, sect, SEUIL)
        sect_par[avec] = sect

    # --- 4. la falaise n'a pas seulement ete repoussee plus loin
    # Rayons sortants depuis les SEULES colonnes de perimetre, interrompus des que
    # l'on retombe dans l'emprise : une premiere version traversait l'ouvrage et
    # comptait ses falaises internes comme des defauts de rampe (max 137).
    def pentes(avec):
        p = []
        for (wx, wz) in emp:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (wx + dx, wz + dz) in emp: continue
                for t in range(1, TALUS_LARGEUR):
                    p1 = (wx + dx * t, wz + dz * t)
                    p2 = (wx + dx * (t + 1), wz + dz * (t + 1))
                    if p1 in emp or p2 in emp: break
                    a1, a2 = but(p1[0], p1[1], avec), but(p2[0], p2[1], avec)
                    if a1 is None or a2 is None: break
                    n1, n2 = naturel.get(p1), naturel.get(p2)
                    if n1 is None or n2 is None: break
                    # Excedent sur le relief que la NATURE avait deja : en bout
                    # d'anneau la cible EST le sol naturel, et compter ses falaises
                    # propres ferait echouer un raccord irreprochable.
                    p.append(max(0.0, abs(a2 - a1) - abs(n2 - n1)))
        return p

    print('PENTE   raidissement cree par la rampe entre deux colonnes voisines')
    print('        (marche de la cible moins marche du sol naturel au meme endroit) :')
    frac = {}
    for nom, avec in (('ancien ', False), ('nouveau', True)):
        p = pentes(avec)
        n = sum(1 for a in p if a >= SEUIL_PENTE)
        frac[avec] = 100.0 * n / len(p) if p else 0.0
        print('        %s : %d couples, moyenne %.2f, max %.1f, >=%d blocs : %d (%.3f %%)'
              % (nom, len(p), statistics.mean(p) if p else 0, max(p) if p else 0,
                 SEUIL_PENTE, n, frac[avec]))
    print('')

    moy = {}
    for avec in (False, True):
        moy[avec] = max((statistics.mean(v) for v in sect_par[avec].values() if v), default=0)
    print('exces moyen maxi par secteur -- ancien %.2f   ->   nouveau %.2f'
          % (moy[False], moy[True]))
    print('exces maxi au contact        -- ancien %.1f   ->   nouveau %.1f'
          % (pires[False], pires[True]))
    print('(secteur SUD : les tranches z>432 sont hors couverture du scan du '
          '24 nov. -- angle mort connu, pas un resultat)')

    sur = sum(1 for v in sect_par[True].values() for a in v if a >= SEUIL)
    sur0 = sum(1 for v in sect_par[False].values() for a in v if a >= SEUIL)
    print('contacts au-dela de %d blocs -- ancien %d   ->   nouveau %d'
          % (SEUIL, sur0, sur))
    ok = (sur == 0 and moy[True] < 1.0 and frac[True] <= frac[False]
          and frac[True] < 0.1)
    if not ok:
        print('ECHEC  le raccord laisse encore une marche franche')
        return 1
    print('OK  aucun contact au-dela de %d blocs, exces moyen sous 1 bloc sur chaque '
          'cote, et la rampe ne reporte pas la falaise plus loin' % SEUIL)
    return 0


if __name__ == '__main__':
    sys.exit(main())
