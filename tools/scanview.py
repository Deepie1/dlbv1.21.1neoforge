#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
scanview -- TRANSFORME UN DOSSIER DE RAPPORTS DE SCAN EN VISIONNEUSE 3D AUTONOME,
            ET EN LISTE DE DEFAUTS QUE PERSONNE N'A EU A DEMANDER.

Consigne dev du 23/11 : « a chaque fois que je te donnerai tous les txt, je voudrais
que non seulement tu regle les problemes, mais en plus tu me les cite explicitement
sans que j'ai a te les donner, mais aussi que tu me rende a chaque fois un lien de
telechargement qui mene a une interface html ou je peux trouver le modele 3d de ma
zone scanee complete, que je peux deplacer sous tous les angles avec la souris [...]
pour chaque etape, et voir parfaitement ou tu te trompe. a gauche pense a mettre un
panel ou je selectionnerai le moment d'appercu voulu avec toutes les etapes. »

Deux sorties, une seule commande :

  1. UNE PAGE HTML AUTONOME. Aucun reseau, aucune bibliotheque exterieure, aucun CDN :
     le rendu est du WebGL ecrit a la main, les donnees sont embarquees en base64 dans
     le fichier. La page fonctionne hors ligne, aujourd'hui et dans cinq ans. Panneau
     de gauche : une entree par etape du pipeline, le mode d'affichage, l'exageration
     verticale, les calques. Souris : glisser pour pivoter, molette pour zoomer, clic
     droit pour deplacer. Le survol affiche les coordonnees et l'altitude sous le
     curseur.

  2. UN DIAGNOSTIC ECRIT, imprime sur la sortie standard et repris dans la page. Il
     est calcule, pas redige : chaque defaut porte son chiffre et l'endroit ou le
     verifier dans la vue 3D.

CE QUI N'EST PAS UNE ERREUR, ET QUI DOIT LE RESTER. Un chunk non charge n'est PAS un
trou dans le terrain : c'est une absence de mesure. Les confondre a deja coute un tour
entier d'enquete (le rapport annoncait « 153 chunks sautes » la ou le clear etait a
100 %). La visionneuse les distingue donc par construction : les colonnes non mesurees
sont rendues dans un gris plat, a part, et peuvent etre masquees d'un clic.

Usage :  python3 tools/scanview.py <dossier_rapports> [-o sortie.html] [--pas N]
         --pas N sous-echantillonne d'un facteur N (1 = pleine resolution).
"""

import argparse
import base64
import io
import os
import re
import sys

VIDE = -32768          # colonne mesuree mais vide
ABSENT = -32767        # chunk non charge : AUCUNE mesure


# --------------------------------------------------------------------------- lecture

def _texte(chemin):
    with io.open(chemin, encoding='utf-8', errors='replace') as f:
        return f.read().replace('\r\n', '\n').replace('\r', '\n')


def entete(txt):
    """Bornes de la zone, emprise, sol de reference. Tout le reste s'y indexe."""
    d = {}
    m = re.search(r'zone scannee\s*:\s*x\s*(-?\d+)\.\.(-?\d+)\s+z\s*(-?\d+)\.\.(-?\d+)', txt)
    if not m:
        raise ValueError('en-tete illisible : bornes de zone introuvables')
    d['x0'], d['x1'], d['z0'], d['z1'] = (int(g) for g in m.groups())
    m = re.search(r'emprise structure\s*:\s*x\s*(-?\d+)\.\.(-?\d+)\s+z\s*(-?\d+)\.\.(-?\d+)', txt)
    d['ex0'], d['ex1'], d['ez0'], d['ez1'] = (int(g) for g in m.groups()) if m else (0, 0, 0, 0)
    m = re.search(r'sol de reference y=(-?\d+)', txt)
    d['sol'] = int(m.group(1)) if m else 64
    m = re.search(r'^etape\s*:\s*(\S+)\s+(.*)$', txt, re.M)
    d['nom'] = m.group(1) if m else '?'
    d['detail'] = m.group(2).strip() if m else ''
    m = re.search(r'instant\s*:\s*t\+(\d+)\s*ms', txt)
    d['t'] = int(m.group(1)) if m else 0
    m = re.search(r'chunks charges\s*:\s*(\d+)\s*/\s*(\d+)', txt)
    d['chunks'] = (int(m.group(1)), int(m.group(2))) if m else (0, 0)
    m = re.search(r'colonnes lues\s*:\s*(\d+)\s*/\s*(\d+)[^\d]*(\d+)', txt)
    d['colonnes'] = (int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else (0, 0, 0)
    return d


def _bloc(txt, debut, fins):
    i = txt.find(debut)
    if i < 0:
        return None
    j = len(txt)
    for f in fins:
        k = txt.find(f, i + len(debut))
        if 0 <= k < j:
            j = k
    return txt[i:j]


def grille(txt, section, x0, x1, z0, z1):
    """Section 9x : une valeur entiere sur 4 caracteres par colonne."""
    bloc = _bloc(txt, section, ['\n9', '\n 9', '\n==='])
    if bloc is None:
        return None
    w = x1 - x0 + 1
    out = [ABSENT] * (w * (z1 - z0 + 1))
    for ligne in bloc.split('\n'):
        if not ligne.startswith('z='):
            continue
        try:
            z = int(ligne[2:9])
        except ValueError:
            continue
        if z < z0 or z > z1:
            continue
        corps = ligne[9:].replace('|', '')
        base = (z - z0) * w
        for k in range(len(corps) // 4):
            x = x0 + k
            if x > x1:
                break
            cel = corps[4 * k:4 * k + 4].strip()
            if not cel or cel == '----':
                continue                       # chunk absent : on laisse ABSENT
            if cel == '....':
                out[base + k] = VIDE
                continue
            try:
                out[base + k] = int(cel)
            except ValueError:
                pass
    return out


def carte(txt, section, x0, x1, z0, z1):
    """Section 8x : un caractere par colonne."""
    bloc = _bloc(txt, section, ['\n 8', '\n8', '\n==='])
    if bloc is None:
        return None
    w = x1 - x0 + 1
    out = [' '] * (w * (z1 - z0 + 1))
    for ligne in bloc.split('\n'):
        if not ligne.startswith('z='):
            continue
        try:
            z = int(ligne[2:9])
        except ValueError:
            continue
        if z < z0 or z > z1:
            continue
        corps = ligne[9:].replace('|', '')
        base = (z - z0) * w
        for k, ch in enumerate(corps):
            if x0 + k > x1:
                break
            out[base + k] = ch
    return out


def charger(dossier):
    """Renvoie (meta, [etapes]) ; chaque etape porte ses grilles et ses cartes."""
    fichiers = sorted(f for f in os.listdir(dossier) if f.endswith('_GRILLES.txt'))
    if not fichiers:
        raise SystemExit('aucun fichier *_GRILLES.txt dans %s' % dossier)
    etapes = []
    meta = None
    for nom in fichiers:
        tg = _texte(os.path.join(dossier, nom))
        h = entete(tg)
        if meta is None:
            meta = {k: h[k] for k in ('x0', 'x1', 'z0', 'z1', 'ex0', 'ex1', 'ez0', 'ez1', 'sol')}
        x0, x1, z0, z1 = meta['x0'], meta['x1'], meta['z0'], meta['z1']
        e = dict(h)
        e['fichier'] = nom
        e['sol_y'] = grille(tg, '9a.', x0, x1, z0, z1)
        e['surf_y'] = grille(tg, '9b.', x0, x1, z0, z1)
        nomc = nom.replace('_GRILLES.txt', '_CARTES.txt')
        pc = os.path.join(dossier, nomc)
        e['anom'] = carte(_texte(pc), '8e.', x0, x1, z0, z1) if os.path.exists(pc) else None
        if e['sol_y'] is None:
            raise SystemExit('%s : section 9a introuvable' % nom)
        etapes.append(e)
    return meta, etapes


# ----------------------------------------------------------------------- diagnostic

def _empreinte(etapes, meta):
    """Colonnes ou la POSE a ecrit : difference entre la derniere etape et celle d'avant
    la pose. C'est la forme reelle de l'ouvrage, pas son rectangle englobant."""
    if len(etapes) < 2:
        return set()
    av, ap = etapes[-2]['sol_y'], etapes[-1]['sol_y']
    w = meta['x1'] - meta['x0'] + 1
    fp = set()
    for i, (a, b) in enumerate(zip(av, ap)):
        if a in (ABSENT, VIDE) or b in (ABSENT, VIDE):
            continue
        if a != b:
            fp.add((meta['x0'] + i % w, meta['z0'] + i // w))
    return fp


def diagnostic(meta, etapes):
    """Liste de defauts calculee, chacun avec son chiffre et ou le verifier."""
    w = meta['x1'] - meta['x0'] + 1
    sol = meta['sol']
    ex0, ex1, ez0, ez1 = meta['ex0'], meta['ex1'], meta['ez0'], meta['ez1']
    pbs = []

    def ajoute(gravite, titre, detail, ou):
        pbs.append({'g': gravite, 't': titre, 'd': detail, 'o': ou})

    def dans_emprise(x, z):
        return ex0 <= x <= ex1 and ez0 <= z <= ez1

    # --- 1. mesure incomplete : un chunk absent n'est pas un trou, mais c'est un angle mort
    for e in etapes:
        lu, tot, abs_ = e['colonnes']
        if tot and abs_:
            manque_emp = 0
            for i, v in enumerate(e['sol_y']):
                if v == ABSENT and dans_emprise(meta['x0'] + i % w, meta['z0'] + i // w):
                    manque_emp += 1
            g = 3 if manque_emp else 1
            ajoute(g, 'Mesure incomplete a l\'etape %s' % e['nom'],
                   '%d colonnes non mesurees sur %d (%.1f %%), dont %d DANS l\'emprise. '
                   'Une colonne non mesuree n\'est pas un trou du terrain : c\'est un '
                   'angle mort de l\'outil.' % (abs_, tot, 100.0 * abs_ / tot, manque_emp),
                   'calque « chargement », colonnes grises')

    # --- 2. etape sans effet : deux captures identiques signalent un jalon mal place
    for n in range(1, len(etapes)):
        a, b = etapes[n - 1], etapes[n]
        chg = sum(1 for x, y in zip(a['sol_y'], b['sol_y'])
                  if x not in (ABSENT,) and y not in (ABSENT,) and x != y)
        if chg == 0:
            ajoute(3, 'Etape sans aucun effet : %s -> %s' % (a['nom'], b['nom']),
                   'Aucune colonne mesuree n\'a bouge entre ces deux captures. Soit le '
                   'jalon se declenche au mauvais moment, soit l\'operation attendue n\'a '
                   'pas eu lieu.', 'mode « changement », vue entierement neutre')

    # --- 3. tablier plat : la marque du rectangle de coupe
    fp = _empreinte(etapes, meta)
    if fp:
        der = etapes[-1]['sol_y']
        plat = tot_emp = 0
        for i, v in enumerate(der):
            x, z = meta['x0'] + i % w, meta['z0'] + i // w
            if not dans_emprise(x, z) or (x, z) in fp or v in (ABSENT, VIDE):
                continue
            tot_emp += 1
            if v == sol - 1 or v == sol:
                plat += 1
        if tot_emp:
            part = 100.0 * plat / tot_emp
            if part > 25.0:
                ajoute(3, 'Tablier plat autour de l\'ouvrage',
                       '%d colonnes sur %d (%.1f %%) de l\'emprise hors ouvrage sont restees '
                       'a l\'altitude du plancher de clear. C\'est le rectangle de coupe : '
                       'un plateau artificiel entre la forme de l\'ouvrage et son rectangle '
                       'englobant.' % (plat, tot_emp, part),
                       'mode « altitude », surface uniforme autour de la structure')
            else:
                ajoute(0, 'Tablier plat resorbe',
                       '%d colonnes sur %d (%.1f %%) seulement restent au plancher : '
                       'le raccord de terrain a fait son travail.' % (plat, tot_emp, part),
                       'mode « altitude »')

    # --- 4. falaises et trous nets, par etape, d'apres la carte d'anomalies
    for e in etapes:
        an = e['anom']
        if not an:
            continue
        c = {}
        for ch in an:
            c[ch] = c.get(ch, 0) + 1
        mes = sum(v for k, v in c.items() if k not in ('@', ' '))
        if not mes:
            continue
        f, v_, t_, bang = c.get('F', 0), c.get('V', 0), c.get('T', 0), c.get('!', 0)
        if f and 100.0 * f / mes > 8.0:
            ajoute(2, 'Falaises a l\'etape %s' % e['nom'],
                   '%d colonnes (%.1f %% des colonnes mesurees) presentent une marche d\'au '
                   'moins 4 blocs avec un voisin.' % (f, 100.0 * f / mes),
                   'mode « anomalies », colonnes orange')
        if v_ > 50:
            ajoute(3, 'Trous nets a l\'etape %s' % e['nom'],
                   '%d colonnes sont a 4 blocs ou plus sous LEURS QUATRE voisins. Ce sont '
                   'des cuvettes franches, pas du relief : un trou sans raison.' % v_,
                   'mode « anomalies », colonnes rouges')
        if t_ > 50:
            ajoute(2, 'Tours isolees a l\'etape %s' % e['nom'],
                   '%d colonnes depassent leurs quatre voisins de 4 blocs ou plus.' % t_,
                   'mode « anomalies », colonnes violettes')
        if bang and 100.0 * bang / mes > 15.0:
            ajoute(1, 'Air piege a l\'etape %s' % e['nom'],
                   '%d colonnes (%.1f %%) ont du vide sous leur premier bloc solide : '
                   'grottes naturelles, ou surplombs laisses par une coupe.'
                   % (bang, 100.0 * bang / mes), 'mode « anomalies », colonnes bleues')

    # --- 5. debordement : une operation qui ecrit hors de l'emprise annoncee
    for n in range(1, len(etapes)):
        a, b = etapes[n - 1], etapes[n]
        hors = 0
        for i, (p, q) in enumerate(zip(a['sol_y'], b['sol_y'])):
            if p == ABSENT or q == ABSENT or p == q:
                continue
            x, z = meta['x0'] + i % w, meta['z0'] + i // w
            if not dans_emprise(x, z):
                hors += 1
        if hors > 200:
            ajoute(2, 'Ecriture hors emprise : %s -> %s' % (a['nom'], b['nom']),
                   '%d colonnes ont change EN DEHORS de l\'emprise annoncee. Attendu si un '
                   'raccord de terrain deborde volontairement ; anormal sinon.' % hors,
                   'mode « changement », colonnes hors du cadre blanc')

    # --- 6. sections theoriques vides : perte de donnee silencieuse
    for e in etapes:
        an = e['anom']
        if an and not any(ch in ('c', 'r') for ch in an):
            ajoute(1, 'Relief theorique absent a l\'etape %s' % e['nom'],
                   'Aucune colonne « creuse » ni « remblai » : la comparaison au relief '
                   'de generation n\'a pas abouti (calcul de fond expire). Le reste du '
                   'rapport est valide, cette comparaison-la manque.',
                   'mode « anomalies », aucune teinte verte ni brune')

    pbs.sort(key=lambda p: -p['g'])
    return pbs


# ------------------------------------------------------------------------ encodage

def _b64(octets):
    return base64.b64encode(octets).decode('ascii')


def encoder(meta, etapes, pas):
    """Hauteurs en octets (offset commun), anomalies en index de palette."""
    x0, x1, z0, z1 = meta['x0'], meta['x1'], meta['z0'], meta['z1']
    W, D = x1 - x0 + 1, z1 - z0 + 1
    w = (W + pas - 1) // pas
    d = (D + pas - 1) // pas
    vals = [v for e in etapes for v in e['sol_y'] if v not in (ABSENT, VIDE)]
    if not vals:
        raise SystemExit('aucune altitude exploitable')
    ymin, ymax = min(vals), max(vals)
    if ymax - ymin > 250:
        ymin = max(ymin, ymax - 250)
    ANOM = '.VTF!cr@ '
    paquets = []
    for e in etapes:
        hs = bytearray(w * d)
        hw = bytearray(w * d)
        an = bytearray(w * d)
        src_s, src_w, src_a = e['sol_y'], e['surf_y'], e['anom']
        for jz in range(d):
            sz = jz * pas
            for jx in range(w):
                sx = jx * pas
                i = sz * W + sx
                o = jz * w + jx
                v = src_s[i]
                if v == ABSENT:
                    hs[o] = 255
                elif v == VIDE:
                    hs[o] = 254
                else:
                    hs[o] = max(0, min(253, v - ymin))
                if src_w is not None:
                    vw = src_w[i]
                    hw[o] = 255 if vw in (ABSENT, VIDE) else max(0, min(253, vw - ymin))
                else:
                    hw[o] = 255
                if src_a is not None:
                    k = ANOM.find(src_a[i])
                    an[o] = k if k >= 0 else 0
                else:
                    an[o] = 0
        paquets.append({
            'nom': e['nom'], 'detail': e['detail'], 't': e['t'],
            'fichier': e['fichier'], 'chunks': e['chunks'], 'colonnes': e['colonnes'],
            'hs': _b64(bytes(hs)), 'hw': _b64(bytes(hw)), 'an': _b64(bytes(an)),
        })
    return {'w': w, 'd': d, 'pas': pas, 'ymin': ymin, 'ymax': ymax,
            'x0': x0, 'z0': z0, 'sol': meta['sol'],
            'ex0': meta['ex0'], 'ex1': meta['ex1'], 'ez0': meta['ez0'], 'ez1': meta['ez1'],
            'etapes': paquets}


def json_min(o):
    """Petit serialiseur : evite d'importer json pour un usage aussi simple."""
    if isinstance(o, dict):
        return '{' + ','.join('"%s":%s' % (k, json_min(v)) for k, v in o.items()) + '}'
    if isinstance(o, (list, tuple)):
        return '[' + ','.join(json_min(v) for v in o) + ']'
    if isinstance(o, bool):
        return 'true' if o else 'false'
    if isinstance(o, (int, float)):
        return repr(o)
    if o is None:
        return 'null'
    s = str(o).replace('\\', '\\\\').replace('"', '\\"')
    s = s.replace('\n', '\\n').replace('\t', ' ')
    return '"%s"' % s


# ---------------------------------------------------------------------------- HTML

GABARIT = None   # rempli plus bas par _gabarit()


def construire(dossier, sortie, pas):
    meta, etapes = charger(dossier)
    pbs = diagnostic(meta, etapes)
    donnees = encoder(meta, etapes, pas)
    html = _gabarit()
    html = html.replace('/*__DONNEES__*/', 'const DATA = %s;' % json_min(donnees))
    html = html.replace('/*__DIAG__*/', 'const DIAG = %s;' % json_min(pbs))
    html = html.replace('__TITRE__', os.path.basename(os.path.abspath(dossier)))
    with io.open(sortie, 'w', encoding='utf-8') as f:
        f.write(html)
    return meta, etapes, pbs, donnees


def _gabarit():
    return r'''<!DOCTYPE html>
<html lang="fr"><head><meta charset="utf-8">
<title>DLB -- scan 3D __TITRE__</title>
<style>
 *{box-sizing:border-box;margin:0;padding:0}
 html,body{height:100%;background:#0d1016;color:#c9d4e3;font:13px/1.45 "Segoe UI",Roboto,Arial,sans-serif;overflow:hidden}
 #app{display:flex;height:100%}
 #panel{width:320px;min-width:320px;height:100%;overflow-y:auto;background:#151a23;border-right:1px solid #2a3340;padding:14px}
 #panel h1{font-size:15px;color:#fff;margin-bottom:2px}
 #panel .sub{font-size:11px;color:#6d7b8d;margin-bottom:14px;word-break:break-all}
 .grp{margin-bottom:16px}
 .ttl{display:block;font-size:10px;letter-spacing:.14em;text-transform:uppercase;color:#5f7a99;margin-bottom:7px;font-weight:600}
 .step{display:block;width:100%;text-align:left;background:#1d2531;border:1px solid #2a3340;color:#b9c6d8;
   padding:7px 9px;margin-bottom:4px;border-radius:5px;cursor:pointer;font:inherit}
 .step:hover{background:#243040;border-color:#3b5a7a}
 .step.on{background:#1b3a5c;border-color:#4b8fd6;color:#fff}
 .step b{display:block;font-size:12px}
 .step span{font-size:10px;color:#7c8ca1}
 .step.on span{color:#a8c6e6}
 select,input[type=range]{width:100%}
 select{background:#1d2531;color:#c9d4e3;border:1px solid #2a3340;border-radius:5px;padding:6px}
 .chk{display:flex;align-items:center;gap:7px;margin:5px 0;cursor:pointer;font-size:12px}
 .leg{font-size:11px;line-height:1.75}
 .leg i{display:inline-block;width:11px;height:11px;border-radius:2px;margin-right:6px;vertical-align:-1px}
 .stat{font-size:11px;color:#8b9bb0}
 .stat b{color:#dce6f2;font-weight:600}
 #diag .it{border-left:3px solid #555;padding:6px 0 6px 8px;margin-bottom:8px;font-size:11px}
 #diag .g3{border-color:#e05a5a}#diag .g2{border-color:#e0a23c}#diag .g1{border-color:#4b8fd6}#diag .g0{border-color:#48b07a}
 #diag .it b{display:block;color:#e6edf6;margin-bottom:2px;font-size:11.5px}
 #diag .it em{display:block;color:#6d7b8d;font-style:normal;margin-top:3px}
 #view{flex:1;position:relative;min-width:0}
 canvas{display:block;width:100%;height:100%;cursor:grab}
 canvas:active{cursor:grabbing}
 #hud{position:absolute;left:12px;bottom:12px;background:rgba(10,14,20,.86);border:1px solid #2a3340;
   border-radius:6px;padding:8px 11px;font-size:11px;pointer-events:none;white-space:pre;color:#9fb0c6}
 #tag{position:absolute;right:12px;top:12px;background:rgba(10,14,20,.86);border:1px solid #2a3340;
   border-radius:6px;padding:8px 11px;font-size:12px;color:#dce6f2}
 #err{position:absolute;inset:0;display:none;align-items:center;justify-content:center;padding:40px;
   text-align:center;color:#e05a5a;font-size:14px;background:#0d1016}
</style></head><body>
<div id="app">
 <div id="panel">
  <h1>Scan de terrain -- vue 3D</h1>
  <div class="sub">__TITRE__</div>
  <div class="grp"><label class="ttl">Etape du pipeline</label><div id="steps"></div></div>
  <div class="grp"><label class="ttl">Coloration</label>
   <select id="mode">
    <option value="alt">Altitude</option>
    <option value="anom">Anomalies (trous, falaises)</option>
    <option value="diffprec">Changement depuis l etape precedente</option>
    <option value="diff0">Changement depuis la premiere etape</option>
    <option value="charge">Chargement des chunks</option>
    <option value="sol">Ecart au sol de reference</option>
   </select></div>
  <div class="grp"><label class="ttl">Calques</label>
   <label class="chk"><input type="checkbox" id="cEau" checked> Eau et feuillage</label>
   <label class="chk"><input type="checkbox" id="cAbs" checked> Colonnes non mesurees</label>
   <label class="chk"><input type="checkbox" id="cEmp" checked> Cadre de l emprise</label>
   <label class="chk"><input type="checkbox" id="cFil"> Fil de fer</label></div>
  <div class="grp"><label class="ttl">Exageration verticale <span id="vexL">x3</span></label>
   <input type="range" id="vex" min="1" max="12" step="1" value="3"></div>
  <div class="grp"><label class="ttl">Legende</label><div class="leg" id="legende"></div></div>
  <div class="grp"><label class="ttl">Cette etape</label><div class="stat" id="stats"></div></div>
  <div class="grp"><label class="ttl">Defauts releves</label><div id="diag"></div></div>
 </div>
 <div id="view"><canvas id="cv"></canvas>
  <div id="tag"></div><div id="hud"></div><div id="err"></div></div>
</div>
<script>
/*__DONNEES__*/
/*__DIAG__*/
(function(){
"use strict";
var cv=document.getElementById("cv"), elErr=document.getElementById("err");
function fatal(m){elErr.style.display="flex";elErr.textContent=m;}

function dec(b){var s=atob(b),n=s.length,a=new Uint8Array(n);for(var i=0;i<n;i++)a[i]=s.charCodeAt(i);return a;}
var W=DATA.w,D=DATA.d,PAS=DATA.pas,YMIN=DATA.ymin,X0=DATA.x0,Z0=DATA.z0,SOL=DATA.sol,ET=DATA.etapes;
for(var q=0;q<ET.length;q++){ET[q].HS=dec(ET[q].hs);ET[q].HW=dec(ET[q].hw);ET[q].AN=dec(ET[q].an);
  ET[q].hs=null;ET[q].hw=null;ET[q].an=null;}
var N=W*D, cur=0, vex=3;
var CX=(W-1)*PAS/2, CZ=(D-1)*PAS/2, BAS=YMIN-6;
function hy(a,i){var v=a[i];return v>=254?null:v+YMIN;}

var pos=new Float32Array(N*3), nor=new Float32Array(N*3), col=new Float32Array(N*3);
var posEau=new Float32Array(N*3);
var idx=new Uint32Array(0), idxEau=new Uint32Array(0), nIdx=0, nEau=0;

function hauteurs(dst,eau){
  var A=ET[cur],H=eau?A.HW:A.HS,HS=A.HS;
  for(var z=0;z<D;z++)for(var x=0;x<W;x++){
    var i=z*W+x,o=i*3,v=H[i],y;
    if(v>=254){y=BAS;}
    else{y=v+YMIN; if(eau&&HS[i]<254&&v<=HS[i])y=HS[i]+YMIN;}
    dst[o]=x*PAS-CX;dst[o+1]=y;dst[o+2]=z*PAS-CZ;
  }
}
function normales(src,dst){
  for(var z=0;z<D;z++)for(var x=0;x<W;x++){
    var p=(z*W+x)*3;
    var hl=src[(z*W+(x>0?x-1:x))*3+1], hr=src[(z*W+(x<W-1?x+1:x))*3+1];
    var hd=src[((z>0?z-1:z)*W+x)*3+1], hu=src[((z<D-1?z+1:z)*W+x)*3+1];
    var nx=hl-hr, nz=hd-hu, ny=2*PAS, L=Math.sqrt(nx*nx+ny*ny+nz*nz)||1;
    dst[p]=nx/L;dst[p+1]=ny/L;dst[p+2]=nz/L;
  }
}
function indices(){
  var a=[],b=[],A=ET[cur],HS=A.HS,HW=A.HW,abs=document.getElementById("cAbs").checked;
  for(var z=0;z<D-1;z++)for(var x=0;x<W-1;x++){
    var i0=z*W+x,i1=i0+1,i2=i0+W,i3=i2+1;
    var na=(HS[i0]>=254?1:0)+(HS[i1]>=254?1:0)+(HS[i2]>=254?1:0)+(HS[i3]>=254?1:0);
    if(na===0||abs){a.push(i0,i2,i1,i1,i2,i3);}
    if(na===0&&HW[i0]<254&&HW[i1]<254&&HW[i2]<254&&HW[i3]<254&&
       (HW[i0]>HS[i0]||HW[i1]>HS[i1]||HW[i2]>HS[i2]||HW[i3]>HS[i3])){b.push(i0,i2,i1,i1,i2,i3);}
  }
  idx=new Uint32Array(a);nIdx=a.length;idxEau=new Uint32Array(b);nEau=b.length;
}
function ramp(t){t=t<0?0:(t>1?1:t);
  var s=[[0.10,0.22,0.45],[0.15,0.42,0.33],[0.42,0.60,0.28],[0.72,0.66,0.33],[0.55,0.42,0.33],[0.93,0.95,0.98]];
  var f=t*(s.length-1),k=Math.min(s.length-2,Math.floor(f)),u=f-k;
  return [s[k][0]+(s[k+1][0]-s[k][0])*u,s[k][1]+(s[k+1][1]-s[k][1])*u,s[k][2]+(s[k+1][2]-s[k][2])*u];}
var ANC=[[0.38,0.42,0.48],[0.90,0.22,0.22],[0.70,0.35,0.85],[0.95,0.58,0.15],
         [0.25,0.60,0.92],[0.30,0.75,0.45],[0.70,0.50,0.25],[0.17,0.19,0.23],[0.12,0.14,0.17]];
function couleurs(){
  var m=document.getElementById("mode").value,A=ET[cur],HS=A.HS,AN=A.AN;
  var lo=1e9,hi=-1e9,i,v;
  for(i=0;i<N;i++){v=hy(HS,i);if(v===null)continue;if(v<lo)lo=v;if(v>hi)hi=v;}
  if(hi<=lo)hi=lo+1;
  var ref=(m==="diff0")?ET[0]:ET[cur>0?cur-1:0];
  for(var j=0;j<N;j++){
    var o=j*3,c,y=hy(HS,j);
    if(y===null){c=[0.19,0.21,0.25];}
    else if(m==="alt"){c=ramp((y-lo)/(hi-lo));}
    else if(m==="anom"){c=ANC[AN[j]]||ANC[0];}
    else if(m==="charge"){c=[0.30,0.62,0.40];}
    else if(m==="sol"){var dd=y-SOL;
      c=dd===0?[0.85,0.85,0.88]:(dd>0?ramp(0.55+Math.min(0.45,dd/40)):[0.15,0.30+Math.max(-0.25,dd/60),0.55]);}
    else{var r=hy(ref.HS,j);
      if(r===null){c=[0.22,0.24,0.28];}
      else{var e=y-r;
        if(e===0)c=[0.26,0.28,0.32];
        else if(e<0){var t=Math.min(1,-e/25);c=[0.20+0.70*t,0.25,0.25];}
        else{var t2=Math.min(1,e/25);c=[0.20,0.35+0.55*t2,0.30];}}}
    col[o]=c[0];col[o+1]=c[1];col[o+2]=c[2];
  }
}
// GLSL. PIEGE : en GLSL ES 3.00 un fragment shader n a AUCUNE precision par defaut
// pour float. "precision highp float;" doit donc preceder TOUTE declaration qui
// emploie un type flottant, y compris la sortie "out vec4". Placee apres, le
// compilateur refuse -- "type requires declaration of default precision qualifier" --
// et le navigateur ne montre plus que "Fragment shader is not compiled".
function sources(deux){
 var vs=(deux?"#version 300 es\nprecision highp float;\n#define IN in\n#define OUT out\n"
             :"precision highp float;\n#define IN attribute\n#define OUT varying\n")+
  "IN vec3 aP;IN vec3 aN;IN vec3 aC;uniform mat4 uMVP;uniform float uVex;uniform float uY0;"+
  "OUT vec3 vN;OUT vec3 vC;void main(){vec3 p=aP;p.y=(p.y-uY0)*uVex;gl_Position=uMVP*vec4(p,1.0);"+
  "vN=normalize(vec3(aN.x,aN.y/uVex,aN.z));vC=aC;}";
 var fs=(deux?"#version 300 es\nprecision highp float;\nout vec4 oC;\n#define IN in\n#define FRAG oC\n"
             :"precision highp float;\n#define IN varying\n#define FRAG gl_FragColor\n")+
  "IN vec3 vN;IN vec3 vC;uniform float uA;uniform float uTint;void main(){"+
  "vec3 L=normalize(vec3(0.45,0.82,0.35));float d=max(dot(normalize(vN),L),0.0);float s=0.34+0.66*d;"+
  "vec3 b=mix(vC,vec3(0.16,0.46,0.78),uTint);FRAG=vec4(b*s,uA);}";
 return {vs:vs,fs:fs};
}
function construire(g,deux){
 var s=sources(deux),jo=[];
 function sh(t,src){var o=g.createShader(t);g.shaderSource(o,src);g.compileShader(o);
  if(!g.getShaderParameter(o,g.COMPILE_STATUS)){
    jo.push((t===g.VERTEX_SHADER?"vertex : ":"fragment : ")+(g.getShaderInfoLog(o)||"refuse sans message"));
    return null;}
  return o;}
 var a=sh(g.VERTEX_SHADER,s.vs),b=sh(g.FRAGMENT_SHADER,s.fs);
 if(!a||!b)return{err:jo.join("\n")};
 var p=g.createProgram();g.attachShader(p,a);g.attachShader(p,b);
 g.bindAttribLocation(p,0,"aP");g.bindAttribLocation(p,1,"aN");g.bindAttribLocation(p,2,"aC");
 g.linkProgram(p);
 if(!g.getProgramParameter(p,g.LINK_STATUS))
   return{err:"edition de liens : "+(g.getProgramInfoLog(p)||"refusee sans message")};
 return {pr:p};
}
// WebGL2 d abord. Si ses shaders sont refuses on REPART sur un canvas neuf en WebGL1
// (un canvas ne peut pas changer de type de contexte) : un refus de shader ne doit
// jamais se solder par un rectangle noir muet.
var gl=cv.getContext("webgl2"), v2=!!gl, res=gl?construire(gl,true):null;
if(gl&&res&&res.err){
  var neuf=cv.cloneNode(false);cv.parentNode.replaceChild(neuf,cv);cv=neuf;
  gl=cv.getContext("webgl")||cv.getContext("experimental-webgl");v2=false;res=null;
}
if(!gl){gl=cv.getContext("webgl")||cv.getContext("experimental-webgl");v2=false;}
if(!gl){fatal("WebGL indisponible dans ce navigateur.");return;}
if(!v2&&!gl.getExtension("OES_element_index_uint")){
  fatal("Ce navigateur ne gere pas les maillages de plus de 65000 points. Regenerez la page avec --pas 2.");return;}
if(!res)res=construire(gl,v2);
if(res.err){fatal("Les shaders n ont pas compile :\n"+res.err);return;}
var pr=res.pr;gl.useProgram(pr);
var uMVP=gl.getUniformLocation(pr,"uMVP"),uVex=gl.getUniformLocation(pr,"uVex"),
    uA=gl.getUniformLocation(pr,"uA"),uY0=gl.getUniformLocation(pr,"uY0"),
    uTint=gl.getUniformLocation(pr,"uTint");
var bP=gl.createBuffer(),bN=gl.createBuffer(),bC=gl.createBuffer(),bI=gl.createBuffer(),bE=gl.createBuffer();
function att(b,a,loc,dyn){gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,a,dyn?gl.DYNAMIC_DRAW:gl.STATIC_DRAW);
  gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,3,gl.FLOAT,false,0,0);}

var cadreP,cadreN,cadreC,nCadre;
(function(){var a=[],e=[DATA.ex0,DATA.ex1,DATA.ez0,DATA.ez1];
 function P(x,z){a.push((x-X0)-CX,SOL,(z-Z0)-CZ);}
 P(e[0],e[2]);P(e[1],e[2]);P(e[1],e[2]);P(e[1],e[3]);P(e[1],e[3]);P(e[0],e[3]);P(e[0],e[3]);P(e[0],e[2]);
 cadreP=new Float32Array(a);nCadre=a.length/3;
 cadreN=new Float32Array(a.length);cadreC=new Float32Array(a.length);
 for(var i=0;i<a.length;i+=3){cadreN[i+1]=1;cadreC[i]=1;cadreC[i+1]=1;cadreC[i+2]=1;}})();

var az=-0.7,el=0.62,dist=Math.max(W,D)*PAS*1.2,tgt=[0,0,0];
function cible(){tgt[1]=(SOL-YMIN)*vex;}
function mat(){
  var ar=(cv.width||1)/(cv.height||1),f=1/Math.tan(0.52),zf=dist*8+6000;
  var P=[f/ar,0,0,0, 0,f,0,0, 0,0,-(zf+1)/(zf-1),-1, 0,0,-2*zf/(zf-1),0];
  var ce=Math.cos(el),se=Math.sin(el),ca=Math.cos(az),sa=Math.sin(az);
  var ex=tgt[0]+dist*ce*sa, ey=tgt[1]+dist*se, ez=tgt[2]+dist*ce*ca;
  var zx=ex-tgt[0],zy=ey-tgt[1],zz=ez-tgt[2],zl=Math.sqrt(zx*zx+zy*zy+zz*zz)||1;zx/=zl;zy/=zl;zz/=zl;
  var xx=zz, xy=0, xz=-zx;
  var xl=Math.sqrt(xx*xx+xz*xz)||1;xx/=xl;xz/=xl;
  var yx=zy*xz-zz*xy, yy=zz*xx-zx*xz, yz=zx*xy-zy*xx;
  var V=[xx,yx,zx,0, xy,yy,zy,0, xz,yz,zz,0,
        -(xx*ex+xy*ey+xz*ez), -(yx*ex+yy*ey+yz*ez), -(zx*ex+zy*ey+zz*ez), 1];
  var M=new Float32Array(16);
  for(var r=0;r<4;r++)for(var c=0;c<4;c++){var s=0;for(var k=0;k<4;k++)s+=V[k*4+r]*P[c*4+k];M[c*4+r]=s;}
  return {M:M,eye:[ex,ey,ez],fwd:[-zx,-zy,-zz],right:[xx,xy,xz],up:[yx,yy,yz],f:f,ar:ar};
}
function dessine(){
  gl.viewport(0,0,cv.width,cv.height);
  gl.clearColor(0.051,0.063,0.086,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
  gl.enable(gl.DEPTH_TEST);
  var m=mat();gl.uniformMatrix4fv(uMVP,false,m.M);gl.uniform1f(uVex,vex);gl.uniform1f(uY0,YMIN);
  gl.uniform1f(uTint,0);gl.uniform1f(uA,1);
  att(bP,pos,0,1);att(bN,nor,1,1);att(bC,col,2,1);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,bI);
  if(nIdx)gl.drawElements(document.getElementById("cFil").checked?gl.LINES:gl.TRIANGLES,nIdx,gl.UNSIGNED_INT,0);
  if(document.getElementById("cEau").checked&&nEau){
    gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);
    gl.uniform1f(uA,0.55);gl.uniform1f(uTint,1);
    att(bP,posEau,0,1);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,bE);
    gl.drawElements(gl.TRIANGLES,nEau,gl.UNSIGNED_INT,0);
    gl.depthMask(true);gl.disable(gl.BLEND);gl.uniform1f(uTint,0);gl.uniform1f(uA,1);
  }
  if(document.getElementById("cEmp").checked&&nCadre){
    att(bP,cadreP,0,0);att(bN,cadreN,1,0);att(bC,cadreC,2,0);
    gl.drawArrays(gl.LINES,0,nCadre);
  }
}
function majGeom(){
  indices();
  hauteurs(pos,false);normales(pos,nor);hauteurs(posEau,true);couleurs();
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,bI);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,idx,gl.DYNAMIC_DRAW);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,bE);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,idxEau,gl.DYNAMIC_DRAW);
  dessine();
}
var elSteps=document.getElementById("steps");
ET.forEach(function(e,i){
  var b=document.createElement("button");b.className="step"+(i===0?" on":"");
  b.innerHTML="<b>"+(i+1)+". "+e.nom+"</b><span>t+"+(e.t/1000).toFixed(1)+" s -- "+e.chunks[0]+"/"+e.chunks[1]+" chunks mesures</span>";
  b.onclick=function(){cur=i;
    for(var k=0;k<elSteps.children.length;k++)elSteps.children[k].className="step"+(k===i?" on":"");
    majTag();majStats();majGeom();};
  elSteps.appendChild(b);});
function majTag(){document.getElementById("tag").textContent=(cur+1)+"/"+ET.length+"   "+ET[cur].nom;}
function majStats(){var e=ET[cur],c=e.colonnes;
  document.getElementById("stats").innerHTML="<b>"+e.detail+"</b><br>instant : t+"+(e.t/1000).toFixed(1)+" s<br>"+
   "colonnes mesurees : <b>"+c[0]+"</b> / "+c[1]+"<br>non mesurees : <b>"+c[2]+"</b><br>"+
   "chunks charges : <b>"+e.chunks[0]+"</b> / "+e.chunks[1];}
var LEG={
 alt:[["#2a4a73","bas"],["#6b9947","moyen"],["#b8a855","haut"],["#eef2f8","sommet"],["#30353f","non mesure"]],
 anom:[["#e03838","trou net (cuvette franche)"],["#b359d9","tour isolee"],["#f29426","falaise >= 4 blocs"],
       ["#409ceb","air piege dessous"],["#4cbf73","creuse"],["#b38040","remblai"],
       ["#616b7a","rien a signaler"],["#30353f","non mesure"]],
 diffprec:[["#b94040","abaisse"],["#44b874","releve"],["#43484f","inchange"],["#30353f","non mesure"]],
 diff0:[["#b94040","abaisse"],["#44b874","releve"],["#43484f","inchange"],["#30353f","non mesure"]],
 charge:[["#4c9e66","mesure"],["#30353f","NON mesure : angle mort, PAS un trou du terrain"]],
 sol:[["#d9d9e0","au sol de reference"],["#b8a855","au-dessus"],["#2a4a73","en dessous"],["#30353f","non mesure"]]};
function majLeg(){var m=document.getElementById("mode").value,h="";
 LEG[m].forEach(function(p){h+="<i style=\"background:"+p[0]+"\"></i>"+p[1]+"<br>";});
 document.getElementById("legende").innerHTML=h;}
document.getElementById("mode").onchange=function(){majLeg();couleurs();dessine();};
["cEau","cAbs","cEmp","cFil"].forEach(function(id){document.getElementById(id).onchange=majGeom;});
document.getElementById("vex").oninput=function(){vex=+this.value;
  document.getElementById("vexL").textContent="x"+vex;cible();dessine();};
(function(){var h="";DIAG.forEach(function(p){
  h+='<div class="it g'+p.g+'"><b>'+p.t+"</b>"+p.d+"<em>ou regarder : "+p.o+"</em></div>";});
 if(!DIAG.length)h='<div class="it g0"><b>Aucun defaut detecte</b>Les controles automatiques passent.</div>';
 document.getElementById("diag").innerHTML=h;})();
var AIDE="glisser : pivoter\nmolette : zoomer\nclic droit : deplacer";
var drag=0,lx=0,ly=0;
cv.addEventListener("contextmenu",function(e){e.preventDefault();});
cv.addEventListener("mousedown",function(e){drag=(e.button===2||e.shiftKey)?2:1;lx=e.clientX;ly=e.clientY;});
window.addEventListener("mouseup",function(){drag=0;});
window.addEventListener("mousemove",function(e){
  if(!drag){survol(e);return;}
  var dx=e.clientX-lx,dy=e.clientY-ly;lx=e.clientX;ly=e.clientY;
  if(drag===1){az-=dx*0.006;el=Math.max(-1.45,Math.min(1.45,el+dy*0.006));}
  else{var s=dist*0.0016,ca=Math.cos(az),sa=Math.sin(az);
       tgt[0]-=(dx*ca-dy*sa)*s;tgt[2]+=(dx*sa+dy*ca)*s;}
  dessine();});
cv.addEventListener("wheel",function(e){e.preventDefault();
  dist=Math.max(20,Math.min(Math.max(W,D)*PAS*5,dist*(e.deltaY>0?1.1:0.9)));dessine();},{passive:false});
function survol(e){
  var hud=document.getElementById("hud"),r=cv.getBoundingClientRect();
  if(!r.width||!r.height){hud.textContent=AIDE;return;}
  var mx=(e.clientX-r.left)/r.width*2-1, my=1-(e.clientY-r.top)/r.height*2;
  var m=mat(),ey=m.eye,fw=m.fwd,ri=m.right,up=m.up,sx=mx*m.ar/m.f,sy=my/m.f;
  var dx=fw[0]+ri[0]*sx+up[0]*sy, dy=fw[1]+ri[1]*sx+up[1]*sy, dz=fw[2]+ri[2]*sx+up[2]*sy;
  var L=Math.sqrt(dx*dx+dy*dy+dz*dz)||1;dx/=L;dy/=L;dz/=L;
  var A=ET[cur],HS=A.HS,t=0,lim=dist*4,pas=Math.max(1,PAS*0.75),trouve=null;
  for(var s=0;s<3000&&t<lim;s++){
    t+=pas;
    var px=ey[0]+dx*t, py=ey[1]+dy*t, pz=ey[2]+dz*t;
    var gx=Math.round((px+CX)/PAS), gz=Math.round((pz+CZ)/PAS);
    if(gx<0||gz<0||gx>=W||gz>=D)continue;
    var hv=hy(HS,gz*W+gx);if(hv===null)continue;
    if(py<=(hv-YMIN)*vex){trouve=[gx,gz,hv];break;}
  }
  if(trouve){
    var wx=X0+trouve[0]*PAS, wz=Z0+trouve[1]*PAS;
    var noms=["rien a signaler","TROU NET","tour isolee","falaise","air piege","creuse","remblai","non charge","vide"];
    hud.textContent="x "+wx+"   z "+wz+"   y "+trouve[2]+"\n"+
      (noms[A.AN[trouve[1]*W+trouve[0]]]||"rien a signaler")+"   (sol de reference "+SOL+")";
  } else hud.textContent=AIDE;
}
function taille(){var d=window.devicePixelRatio||1;
  cv.width=Math.max(1,Math.floor(cv.clientWidth*d));cv.height=Math.max(1,Math.floor(cv.clientHeight*d));dessine();}
window.addEventListener("resize",taille);
document.getElementById("hud").textContent=AIDE;
cible();majTag();majStats();majLeg();majGeom();taille();
})();
</script></body></html>
'''


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[1])
    ap.add_argument('dossier')
    ap.add_argument('-o', '--sortie', default=None)
    ap.add_argument('--pas', type=int, default=1)
    a = ap.parse_args()
    sortie = a.sortie or os.path.join(
        os.path.dirname(os.path.abspath(a.dossier)) or '.',
        'scan3d_' + os.path.basename(os.path.abspath(a.dossier)) + '.html')
    meta, etapes, pbs, don = construire(a.dossier, sortie, max(1, a.pas))
    print('scanview : %s' % sortie)
    print('  zone      : x %d..%d  z %d..%d   (%d x %d colonnes, pas %d)'
          % (meta['x0'], meta['x1'], meta['z0'], meta['z1'], don['w'], don['d'], don['pas']))
    print('  emprise   : x %d..%d  z %d..%d   sol de reference y=%d'
          % (meta['ex0'], meta['ex1'], meta['ez0'], meta['ez1'], meta['sol']))
    print('  etapes    : %d' % len(etapes))
    for i, e in enumerate(etapes):
        print('     %d. %-24s t+%7.1f s   %d/%d chunks'
              % (i + 1, e['nom'], e['t'] / 1000.0, e['chunks'][0], e['chunks'][1]))
    print('  poids     : %.2f Mo' % (os.path.getsize(sortie) / 1048576.0))
    print('')
    print('  DEFAUTS RELEVES AUTOMATIQUEMENT (%d)' % len(pbs))
    niv = {3: 'GRAVE  ', 2: 'NOTABLE', 1: 'MINEUR ', 0: 'OK     '}
    for p in pbs:
        print('   [%s] %s' % (niv[p['g']], p['t']))
        for ligne in _plier(p['d'], 86):
            print('              %s' % ligne)
        print('              -> ou regarder : %s' % p['o'])
    return 0


def _plier(s, n):
    mots, ligne, out = s.split(), '', []
    for m in mots:
        if len(ligne) + len(m) + 1 > n:
            out.append(ligne)
            ligne = m
        else:
            ligne = (ligne + ' ' + m).strip()
    if ligne:
        out.append(ligne)
    return out


if __name__ == '__main__':
    sys.exit(main())
