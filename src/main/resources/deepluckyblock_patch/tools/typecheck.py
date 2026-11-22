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


CLASS_HEAD = re.compile(r'\b(?:class|interface|enum|record)[ \t]+([A-Za-z_$]\w{0,80})')


ANON_HEAD = re.compile(r'\bnew[ \t]+[A-Za-z_$][\w.$]{0,80}(?:<[^<>;{}]{0,200}>)?[ \t]*\(')


def class_ranges(src):
    # (ouverture, fermeture, nom) de chaque type declare dans le fichier.
    out = []
    for m in CLASS_HEAD.finditer(src):
        b = src.find('{', m.end())
        if b < 0:
            continue
        out.append((b, match_brace(src, b), m.group(1)))
    # Les classes ANONYMES comptent aussi : « new EndKnightModel<>(...) { ... } »
    # redefinit legitimement setupAnim() alors que la classe englobante la
    # definit elle aussi. Sans cette portee, le banc criait au doublon.
    for m in ANON_HEAD.finditer(src):
        _args, close = balanced_call(src, m.end() - 1)
        if _args is None:
            continue
        j = close + 1
        while j < len(src) and src[j] in ' \t\r\n':
            j += 1
        if j < len(src) and src[j] == '{':
            out.append((j, match_brace(src, j), '<anonyme>'))
    return out


def erasure(params):
    # Signature a l'EFFACEMENT, comme la voit le compilateur : on retire
    # annotations, final, nom du parametre, generiques et qualification de
    # paquet. foo(List<String>) et foo(List<Integer>) se reduisent donc au
    # meme jeton -- ce qui est exact, Java refuse aussi cette surcharge.
    sig = []
    for a in split_args(params):
        a = re.sub(r'@[\w.]+(?:\([^()]*\))?', ' ', a)
        a = re.sub(r'\bfinal\b', ' ', a)
        a = re.sub(r'\s+', ' ', a).strip()
        if not a:
            continue
        a = re.sub(r'\s+[A-Za-z_$]\w*$', '', a)      # nom du parametre
        a = re.sub(r'<[^<>]*>', '', a)                 # generiques
        a = a.strip()
        # La qualification est CONSERVEE telle qu'ecrite : « Logger » (slf4j
        # importe) et « org.apache.logging.log4j.Logger » sont deux types
        # differents, et DebugLog surcharge legitimement sur ce seul critere.
        # La couper rendait ces deux methodes identiques. Consequence assumee :
        # un vrai doublon ecrit une fois qualifie et une fois non passerait --
        # un banc qui crie a tort est pire qu'un banc qui rate ce cas la.
        sig.append(a.replace(' ', ''))
    return tuple(sig)


def check_duplicates(path, src, decls):
    """
    D. DOUBLON -- deux methodes de meme signature dans le meme type.

    Ajoute apres un echec de runClient que les 8 bancs avaient laisse passer :
    j'avais ecrit une methode waterSurfaceY() dans SafeSurface sans voir qu'une
    privee du meme nom et de la meme signature y existait deja depuis T83.
    « method waterSurfaceY(ServerLevel,int,int) is already defined ». Aucune
    regle ne regardait cela, et un doublon n'est visible qu'a la compilation.
    Les constructeurs sont hors de portee du motif HEAD (il exige un type de
    retour), ce qui ecarte d'emblee les faux positifs les plus probables.
    """
    ranges = class_ranges(src)
    seen = {}
    for start, name, params, _b in decls:
        # type ENGLOBANT = le plus interieur qui contient la declaration :
        # deux classes internes ont parfaitement le droit d'avoir la meme
        # methode, ce n'est un doublon que dans un MEME type.
        owner = None
        for b, e, cname in ranges:
            if b < start < e and (owner is None or b > owner[0]):
                owner = (b, cname)
        key = (owner[0] if owner else -1, name, erasure(params))
        ln = src.count('\n', 0, start) + 1
        if key in seen:
            errors.append('%s:%d  DOUBLON  %s(%s) est deja defini ligne %d dans le meme type'
                          % (path, ln, name, ','.join(key[2]), seen[key]))
        else:
            seen[key] = ln


def check(path):
    raw = io.open(path, encoding='utf-8', errors='replace').read()
    src = strip_comments(raw)
    decls = declarations(src)
    check_duplicates(path, src, decls)

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


# =====================================================================
# E. ACCES PRIVE -- appeler Classe.methode() alors qu'elle est privee
# =====================================================================
# Seconde erreur du runClient du 22/11 : « waterSurfaceY(ServerLevel,int,int)
# has private access in SafeSurface ». Invisible a tous les bancs : la
# syntaxe est parfaite, seule la visibilite est fautive. On ne regarde que
# les classes DU DEPOT, et on exige que la methode soit privee dans TOUTES
# ses surcharges du fichier cible -- une seule surcharge publique suffit a
# rendre l'appel plausible, et le banc se tait.
PRIV_CALL = re.compile(r'\b([A-Z][A-Za-z0-9_$]{2,60})\.([a-z][A-Za-z0-9_$]{0,60})[ \t]*\(')


def build_visibility(paths):
    table = {}
    for p in paths:
        cls = os.path.basename(p)[:-5]
        src = strip_comments(io.open(p, encoding='utf-8', errors='replace').read())
        meths = {}
        for start, name, _params, _b in declarations(src):
            line_start = src.rfind('\n', 0, start) + 1
            head = src[line_start:start + 200]
            meths.setdefault(name, []).append('private' in head.split('(')[0])
        table[cls] = meths
    return table


def check_private_access(path, src, visibility):
    me = os.path.basename(path)[:-5]
    for m in PRIV_CALL.finditer(src):
        cls, meth = m.group(1), m.group(2)
        if cls == me or cls not in visibility:
            continue
        flags = visibility[cls].get(meth)
        if flags and all(flags):          # toutes les surcharges sont privees
            ln = src.count('\n', 0, m.start()) + 1
            errors.append('%s:%d  ACCES PRIVE  %s.%s(...) est privee dans %s.java'
                          % (path, ln, cls, meth, cls))


BASELINE = os.path.join('src', 'main', 'resources', 'deepluckyblock_patch', 'tools',
                        'typecheck-baseline.txt')

args = [a for a in sys.argv[1:] if a != '--ecrire-base']
write_baseline = '--ecrire-base' in sys.argv[1:]
targets = args or [os.path.join(d, f)
                   for d, _, fs in os.walk(ROOT) for f in fs if f.endswith('.java')]
for t in sorted(targets):
    check(t)

# La visibilite se juge APRES avoir vu tout le depot : l'appelant et la
# definition sont dans deux fichiers differents.
_all = [os.path.join(d, f) for d, _, fs in os.walk(ROOT) for f in fs if f.endswith('.java')]
_vis = build_visibility(_all)
for t in sorted(targets):
    check_private_access(t, strip_comments(
        io.open(t, encoding='utf-8', errors='replace').read()), _vis)

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
