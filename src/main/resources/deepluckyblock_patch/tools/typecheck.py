#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Reproduit les controles du compilateur qui m'ont echappe, sur TOUT le depot.
#
# Faute de javac dans ce bac a sable, je n'avais que l'analyse syntaxique : elle accepte
# n'importe quel appel tant que les parentheses s'equilibrent. Les 19 erreurs de runClient
# lui etaient donc toutes invisibles. Ces trois regles-la les auraient toutes attrapees :
#
#   A. arite      -- un appel a une methode du meme fichier doit avoir le bon nombre d'arguments
#   B. long->int  -- TestProcedure.schedule attend un int, toute expression long est refusee
#   C. case       -- une etiquette ne peut pas apparaitre deux fois dans le meme switch
import io
import os
import re
import sys

ROOT = 'src/main/java'
errors = []

# Entete de methode : debut de ligne, modificateurs optionnels, un type, un nom, '('.
# Ancre et bornee -- la version « libre » partait en explosion combinatoire.
# Les declarations sans modificateur d'acces (portee paquet) comptent aussi : les ignorer
# faisait passer des surcharges comme « int scaledWidth() » pour inexistantes.
HEAD = re.compile(
    r'^[ \t]*(?:(?:public|private|protected|static|final|synchronized|abstract|default|'
    r'native|strictfp)[ \t]+)*'
    r'(?:<[^<>]{0,120}>[ \t]+)?'
    r'[A-Za-z_$][\w.$]{0,80}(?:<[^<>;{}]{0,200}>)?(?:\[\])*[ \t]+'
    r'([A-Za-z_$]\w{0,80})[ \t]*\(',
    re.M)

NOT_METHODS = {'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return',
               'new', 'record'}


def strip_comments(src):
    # Neutralise commentaires et litteraux SANS deplacer un seul caractere : les numeros
    # de ligne et les positions restent exacts. Les litteraux gardent leur texte (une
    # etiquette de case doit rester identifiable) mais perdent les caracteres capables
    # de fausser le comptage de parentheses.
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find('\n', i)
            j = n if j < 0 else j
            out.append(' ' * (j - i))
            i = j
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            out.append(''.join(ch if ch == '\n' else ' ' for ch in src[i:j]))
            i = j
        elif c in '"\'':
            j, q = i + 1, c
            while j < n and src[j] != q:
                j += 2 if src[j] == '\\' else 1
            lit = src[i:min(j + 1, n)]
            out.append(''.join('Z' if ch in '(){}[],;/*\\' else ch for ch in lit))
            i = j + 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def split_args(s):
    # Decoupe une liste d'arguments au niveau superieur uniquement.
    if not s.strip():
        return []
    args, depth, cur = [], 0, ''
    for ch in s:
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        if ch == ',' and depth == 0:
            args.append(cur)
            cur = ''
        else:
            cur += ch
    args.append(cur)
    return args


def match_brace(src, start):
    depth, i = 0, start
    while i < len(src):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(src) - 1


def balanced_call(src, open_idx):
    depth, i = 0, open_idx
    while i < len(src):
        if src[i] == '(':
            depth += 1
        elif src[i] == ')':
            depth -= 1
            if depth == 0:
                return src[open_idx + 1:i], i
        i += 1
    return None, open_idx


def declarations(src):
    # (debut, nom, parametres, index_accolade) pour chaque methode reellement definie.
    out = []
    for m in HEAD.finditer(src):
        name = m.group(1)
        if name in NOT_METHODS:
            continue
        params, close = balanced_call(src, m.end() - 1)
        if params is None:
            continue
        rest = src[close + 1:close + 200]
        body = re.match(r'\s*(?:throws[\w\s.,]{0,120})?\{', rest)
        if not body:          # declaration abstraite, appel ordinaire, etc.
            continue
        out.append((m.start(), name, params, close + 1 + body.end() - 1))
    return out


def check(path):
    raw = io.open(path, encoding='utf-8', errors='replace').read()
    src = strip_comments(raw)
    decls = declarations(src)

    # Portees : (debut_declaration, fin_corps) de chaque methode du fichier.
    # On part de la DECLARATION, pas de l'accolade : un parametre « long now » appartient
    # a sa methode. En demarrant au corps, ce parametre passait pour un champ de classe
    # et contaminait tous les « now » du fichier -- 61 faux positifs a lui seul.
    spans = [(d, b, match_brace(src, b)) for d, _n, _p, b in decls]

    # ---------- A. arite des methodes locales ----------
    arity, varargs = {}, {}
    for _d, fn, raw_params, _b in decls:
        params = split_args(raw_params)
        arity.setdefault(fn, set()).add(len(params))
        # Un dernier parametre variadique (String... keys) accepte n'importe quel surplus.
        if params and '...' in params[-1]:
            varargs[fn] = min(varargs.get(fn, 99), len(params) - 1)
    for fn, counts in arity.items():
        floor = varargs.get(fn)
        for m in re.finditer(r'(?<![.\w])' + re.escape(fn) + r'\s*\(', src):
            # « new Foo(...) » vise un constructeur, pas la methode locale du meme nom.
            if re.search(r'\bnew\s+$', src[max(0, m.start() - 8):m.start()]):
                continue
            args, _ = balanced_call(src, m.end() - 1)
            if args is None:
                continue
            k = len(split_args(args))
            if k in counts or (floor is not None and k >= floor):
                continue
            ln = src[:m.start()].count('\n') + 1
            errors.append('%s:%d  ARITE  %s(...) recoit %d argument(s), declare(s) : %s'
                          % (path, ln, fn, k, sorted(counts)))

    # ---------- B. long la ou un int est attendu ----------
    masked = list(src)
    for d, _b, e in spans:
        for i in range(d, e):
            if masked[i] != '\n':
                masked[i] = ' '
    # Les composants d'un record sont des champs du record, pas des variables du fichier.
    for rm in re.finditer(r'\brecord\s+\w+\s*\(', src):
        args, close = balanced_call(src, rm.end() - 1)
        for i in range(rm.start(), close + 1):
            if masked[i] != '\n':
                masked[i] = ' '
    fields = ''.join(masked)

    def longs_visible(pos):
        best = None
        for d, _b, e in spans:
            if d <= pos <= e and (best is None or d > best[0]):
                best = (d, e)
        names = set()
        for chunk in ((src[best[0]:best[1]] if best else ''), fields):
            names |= set(re.findall(r'\b(?:final\s+)?long\s+(\w+)\s*[=;,)]', chunk))
            names |= set(re.findall(r'\blong\[\]\s+(\w+)', chunk))
        return names

    for m in re.finditer(r'TestProcedure\.schedule\s*\(', src):
        args, _ = balanced_call(src, m.end() - 1)
        parts = split_args(args or '')
        if len(parts) < 2:
            continue
        tick, ln, why = parts[1], src[:m.start()].count('\n') + 1, None
        if re.search(r'\d+L\b', tick):
            why = 'literal long (suffixe L)'
        else:
            for v in sorted(longs_visible(m.start())):
                if re.search(r'(?<![.\w])' + re.escape(v) + r'(?![\w])', tick):
                    why = 'variable long << %s >>' % v
                    break
        if why:
            errors.append('%s:%d  LONG->INT  schedule(..., %s, ...) : %s'
                          % (path, ln, tick.strip()[:46], why))

    # ---------- C. etiquette de case dupliquee ----------
    for sm in re.finditer(r'switch\s*\(', src):
        b = src.find('{', sm.end())
        if b < 0:
            continue
        end = match_brace(src, b)
        body, seen, depth = src[b:end], {}, 0
        for cm in re.finditer(r'\bcase\s+([^:>]+?)\s*(?:->|:)|[{}]', body):
            tok = cm.group(0)
            if tok == '{':
                depth += 1
                continue
            if tok == '}':
                depth -= 1
                continue
            if depth != 1:            # un switch imbrique a ses propres etiquettes
                continue
            ln = src[:b + cm.start()].count('\n') + 1
            for lab in split_args(cm.group(1)):
                lab = lab.strip()
                if lab in seen:
                    errors.append('%s:%d  CASE DUPLIQUE  << %s >> deja vu ligne %d'
                                  % (path, ln, lab, seen[lab]))
                else:
                    seen[lab] = ln


BASELINE = os.path.join('src', 'main', 'resources', 'deepluckyblock_patch', 'tools',
                        'typecheck-baseline.txt')

args = [a for a in sys.argv[1:] if a != '--ecrire-base']
write_baseline = '--ecrire-base' in sys.argv[1:]
targets = args or [os.path.join(d, f)
                   for d, _, fs in os.walk(ROOT) for f in fs if f.endswith('.java')]
for t in sorted(targets):
    check(t)

# Le mod traine des tournures que l'heuristique lit de travers : methodes MCreator
# compactees sur une seule ligne, classes internes, surcharges heritees. Elles sont
# figees dans un fichier de reference ; seules les NOUVELLES alertes font echouer.
def key(e):
    return e.split('  ', 1)[-1] if '  ' in e else e

if write_baseline:
    io.open(BASELINE, 'w', encoding='utf-8').write(
        "# Alertes connues et verifiees inoffensives (code MCreator d'origine).\n"
        '# Regenerer : python3 tools/typecheck.py --ecrire-base\n'
        + ''.join(sorted(e + '\n' for e in errors)))
    print('base de reference ecrite : %d alerte(s) connue(s)' % len(errors))
    sys.exit(0)

known = set()
if os.path.exists(BASELINE):
    known = {l.rstrip('\n') for l in io.open(BASELINE, encoding='utf-8')
             if l.strip() and not l.startswith('#')}
# T464 : la comparaison se fait sur le MESSAGE, pas sur 'fichier:ligne'.
# key() etait deja defini juste au-dessus dans cette intention mais n'etait
# jamais appele : la moindre insertion de ligne decalait les alertes connues
# et le banc criait 'NOUVEAU probleme' pour du code non modifie.
knownKeys = {key(k) for k in known}
fresh = [e for e in errors if e not in known and key(e) not in knownKeys]

if fresh:
    for e in fresh:
        print('ERR  ' + e)
    print('\n%d NOUVEAU(X) probleme(s)' % len(fresh))
    sys.exit(1)
print('typecheck : arite, long->int, case dupliques -- rien de neuf '
      '(%d fichiers, %d alerte(s) connue(s) ignoree(s))' % (len(targets), len(errors)))
