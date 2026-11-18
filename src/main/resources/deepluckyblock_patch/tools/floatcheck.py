#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""floatcheck — detecteur de « possible lossy conversion from double to float ».

POURQUOI CET OUTIL EXISTE
-------------------------
Il n'y a pas de JDK dans l'environnement de travail. jparse ne verifie que la
SYNTAXE : il accepte sans broncher un double passe la ou javac exige un float.
L'erreur ne sort qu'au build Gradle, chez l'utilisateur, ce qui coute un
aller-retour complet par faute. Deux pieges deja payes, 14 erreurs d'un coup :

  * Mth.atan2 rend un DOUBLE, contrairement a Mth.sin/Mth.cos qui rendent un
    float — donc Mth.sin(Mth.atan2(a, b) * 2.0f) ne compile pas ;
  * une methode de rendu declarant « double cx, double cy, double cz » alors que
    les primitives box()/fragment() prennent des float.

CE QU'IL FAIT
-------------
Un mini-verificateur de types limite aux flottants. Par fichier : signatures des
methodes declarees, champs de classe, puis methode par methode les variables
locales AVEC LEUR POSITION (deux variables du meme nom dans deux blocs ne sont
pas la meme variable). Il infere ensuite le type de chaque expression et signale
quatre fautes :

  1. argument double passe a un parametre float ;
  2. « float x = <expression double> » ;
  3. « x = <expression double> » sur un x deja declare float ;
  4. « return <double> » dans une methode qui rend un float.

Les compound assignments (x += d) ne sont PAS signales : Java y insere un cast
implicite, c'est legal.

CE QU'IL NE FAIT PAS
--------------------
Pas de resolution de surcharge entre classes, pas de generiques, pas de champs
venus d'autres fichiers. Tout appel de type de retour inconnu est NEUTRE : son
contenu ne contamine pas l'expression englobante. Le parti pris est assume —
zero faux positif sur un arbre qui compile, quitte a laisser passer des cas
tordus. Un faux positif rend l'outil inutilisable ; un oubli coute un build.

Usage : python3 floatcheck.py <fichier.java> [...]
        python3 floatcheck.py --all          (tout src/main/java)
Sortie : une ligne par alerte ; code de retour 1 s'il y en a.
"""

import io
import os
import re
import sys

# --- Tables d'API ----------------------------------------------------------

# Rendent un DOUBLE quoi qu'il arrive.
DOUBLE_FNS = set("""
Math.sqrt Math.pow Math.atan2 Math.hypot Math.exp Math.log Math.log10 Math.cbrt
Math.random Math.toRadians Math.toDegrees Math.sin Math.cos Math.tan Math.asin
Math.acos Math.atan Math.sinh Math.cosh Math.tanh Math.floor Math.ceil Math.rint
Math.expm1 Math.log1p Math.nextUp Math.nextDown Math.ulp Math.IEEEremainder
Mth.atan2 Mth.lengthSquared
""".split())

# Rendent un FLOAT quoi qu'il arrive.
FLOAT_FNS = set("""
Mth.sin Mth.cos Mth.sqrt Mth.invSqrt Mth.wrapDegrees Mth.degreesDifference
Mth.frac Mth.rotLerp Mth.nextFloat
""".split())

# Le type de retour suit celui des arguments.
OVERLOADED = ('Math.max', 'Math.min', 'Math.abs', 'Math.signum', 'Math.copySign',
              'Mth.clamp', 'Mth.lerp', 'Mth.abs', 'Mth.square')

# Rendent un entier : neutres vis-a-vis des flottants.
INT_FNS = ('Math.round', 'Math.floorDiv', 'Math.floorMod', 'Math.toIntExact',
           'Mth.floor', 'Mth.ceil', 'Mth.nextInt')

DOUBLE_CONSTS = ('Math.PI', 'Math.E', 'Math.TAU')

# Parametres float d'API, uniquement quand aucune surcharge double n'existe.
API_FLOAT_PARAMS = {
    ('Mth.sin', 1): [0],
    ('Mth.cos', 1): [0],
    ('Mth.sqrt', 1): [0],
}

PRIMS = ('float', 'double', 'int', 'long', 'short', 'byte', 'char', 'boolean')

DBL = ' 7.7 '      # marqueur : vaut un double
NEU = ' 7.7f '     # marqueur : neutre (float, int, inconnu)

# --- Pretraitement ---------------------------------------------------------


def strip_noise(src):
    """Blanchit commentaires et litteraux texte en conservant les offsets."""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find('\n', i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = ' '
            i = j
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if src[k] != '\n':
                    out[k] = ' '
            i = j
        elif c in '"\'':
            q, j = c, i + 1
            while j < n:
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == q:
                    j += 1
                    break
                if src[j] == '\n':
                    break
                j += 1
            for k in range(i + 1, min(j, n)):
                if src[k] != '\n':
                    out[k] = ' '
            i = j
        else:
            i += 1
    return ''.join(out)


def match_paren(s, open_idx, opener='(', closer=')'):
    depth = 0
    for i in range(open_idx, len(s)):
        if s[i] == opener:
            depth += 1
        elif s[i] == closer:
            depth -= 1
            if depth == 0:
                return i
    return -1


def split_top(s, sep=','):
    """Decoupe sur un separateur de premier niveau."""
    parts, depth, cur = [], 0, []
    for ch in s:
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        if ch == sep and depth == 0:
            parts.append(''.join(cur))
            cur = []
        else:
            cur.append(ch)
    parts.append(''.join(cur))
    return [p.strip() for p in parts]


def find_top(s, token):
    """Index de la premiere occurrence du token au premier niveau, sinon -1."""
    depth, i, n = 0, 0, len(s)
    while i < n:
        ch = s[i]
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        elif depth == 0 and s.startswith(token, i):
            return i
        i += 1
    return -1


# --- Signatures et declarations -------------------------------------------

SIG_RE = re.compile(
    r'(?:^|[\s;{}])(?:(?:public|private|protected|static|final|synchronized|abstract|native|default)\s+)*'
    r'(?P<ret>[A-Za-z_][\w.<>\[\],\s]*?)\s+(?P<name>[A-Za-z_]\w*)\s*\((?P<args>[^;{]*?)\)\s*(?:throws[^{;]*)?\{',
    re.S)

KEYWORDS = {'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'try',
            'new', 'return', 'else', 'do', 'case', 'record'}


def norm_type(t):
    t = re.sub(r'\s+', '', t)
    t = re.sub(r'<.*>', '', t)
    return t


def parse_params(argstr):
    out = []
    for a in split_top(argstr):
        a = re.sub(r'\bfinal\b', ' ', a).strip()
        if not a:
            continue
        a = re.sub(r'\.\.\.', '[]', a)
        m = re.match(r'^(?P<type>[A-Za-z_][\w.<>\[\],\s]*?)\s+(?P<name>[A-Za-z_]\w*)\s*$', a)
        if not m:
            out.append(('?', '?'))
            continue
        out.append((norm_type(m.group('type')), m.group('name')))
    return out


def collect(clean):
    """Signatures (nom -> arite -> liste), corps de methodes, champs de classe."""
    sigs, bodies = {}, []
    for m in SIG_RE.finditer(clean):
        name, ret = m.group('name'), norm_type(m.group('ret'))
        if name in KEYWORDS or ret in KEYWORDS or not ret:
            continue
        params = parse_params(m.group('args'))
        open_idx = clean.index('{', m.end() - 1)
        close_idx = match_paren(clean, open_idx, '{', '}')
        if close_idx < 0:
            continue
        sigs.setdefault(name, {}).setdefault(len(params), []).append(
            (ret, [p[0] for p in params]))
        bodies.append((name, ret, params, open_idx + 1, close_idx))
    return sigs, bodies


DECL_RE = re.compile(
    r'(?<![\w.])(?:final\s+)?(float|double|int|long|short|byte)(\s*\[\s*\])?\s+'
    r'(?P<rest>[A-Za-z_]\w*\s*(?:\[\s*\])?\s*(?==|;|,|\)|:))')


def collect_decls(clean, start, end):
    """Liste (position, nom, type, est_tableau) pour la tranche donnee."""
    out = []
    for m in DECL_RE.finditer(clean, start, end):
        typ = m.group(1)
        arr = bool(m.group(2))
        rest = m.group('rest')
        nm = re.match(r'[A-Za-z_]\w*', rest).group(0)
        out.append((m.start(), nm, typ, arr or bool(re.search(r'\[\s*\]', rest))))
    # Declarateurs multiples : float a = 1.0f, b = 2.0f;
    for m in re.finditer(r'(?<![\w.])(?:final\s+)?(float|double|int|long|short|byte)\s+'
                         r'([A-Za-z_]\w*[^;{}]*);', clean[start:end]):
        typ = m.group(1)
        for piece in split_top(m.group(2)):
            mm = re.match(r'([A-Za-z_]\w*)\s*(?:=|$)', piece)
            if mm:
                out.append((start + m.start(), mm.group(1), typ, False))
    return out


class Scope(object):
    """Declarations indexees par nom, resolues par POSITION.

    Deux « float deep » et « double deep » dans deux blocs d'une meme methode ne
    sont pas la meme variable : on prend toujours la declaration la plus proche
    AVANT l'usage. Sans ca, une variable d'un bloc anterieur contamine tout.
    """

    def __init__(self, fields, locals_, params, body_start):
        self.by_name = {}
        for pos, nm, typ, arr in fields:
            self.by_name.setdefault(nm, []).append((-2, typ, arr))
        for typ, nm in params:
            base = typ.replace('[]', '')
            if base in PRIMS:
                self.by_name.setdefault(nm, []).append((-1, base, typ.endswith('[]')))
        for pos, nm, typ, arr in locals_:
            self.by_name.setdefault(nm, []).append((pos, typ, arr))
        for v in self.by_name.values():
            v.sort()
        self.body_start = body_start

    def typ(self, name, pos, indexed):
        cands = self.by_name.get(name)
        if not cands:
            return None
        best = None
        for p, t, arr in cands:
            if p < pos:
                best = (p, t, arr)
        if best is None:
            return None
        _p, t, arr = best
        if arr and not indexed:
            return None          # la reference au tableau n'est pas un scalaire
        if indexed and not arr:
            return None
        return t


# --- Inference -------------------------------------------------------------

FLOAT_LIT = re.compile(r'(?<![\w.])(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?[fF](?![\w.])')
DOUBLE_LIT = re.compile(
    r'(?<![\w.])(?:\d+\.\d*|\.\d+|\d+\.)(?:[eE][+-]?\d+)?[dD]?(?![\w.(])'
    r'|(?<![\w.])\d+(?:[eE][+-]?\d+)[dD]?(?![\w.])'
    r'|(?<![\w.])\d+[dD](?![\w.])')

CAST_RE = re.compile(r'\(\s*(float|double|int|long|short|byte|char)\s*\)')
CALL_RE = re.compile(r'(?<![\w.$])((?:[A-Za-z_$][\w$]*\s*\.\s*)*[A-Za-z_$][\w$]*)\s*\(')


def operand_end(s, i):
    """Fin de l'operande unaire commencant a i (apres un cast)."""
    n = len(s)
    while i < n and s[i] in ' \t\n':
        i += 1
    while i < n and s[i] in '+-!~':
        i += 1
        while i < n and s[i] in ' \t\n':
            i += 1
    m = CAST_RE.match(s, i)
    if m:
        return operand_end(s, m.end())
    if i < n and s[i] == '(':
        j = match_paren(s, i)
        return n if j < 0 else j + 1
    m = re.match(r'[A-Za-z_$][\w$]*(?:\s*\.\s*[A-Za-z_$][\w$]*)*', s[i:])
    if not m:
        return i
    j = i + m.end()
    while True:
        while j < n and s[j] in ' \t\n':
            j += 1
        if j < n and s[j] == '(':
            k = match_paren(s, j)
            j = n if k < 0 else k + 1
            continue
        if j < n and s[j] == '[':
            k = match_paren(s, j, '[', ']')
            j = n if k < 0 else k + 1
            continue
        if j < n and s[j] == '.':
            m2 = re.match(r'\.\s*[A-Za-z_$][\w$]*', s[j:])
            if m2:
                j += m2.end()
                continue
        break
    return j


def is_double(expr, scope, sigs, pos):
    """True si l'expression vaut certainement un double."""
    e = expr.strip()
    if not e:
        return False

    # Ternaire : la condition ne donne pas le type, seules les deux branches.
    q = find_top(e.replace('::', '@@'), '?')
    if q >= 0:
        tail = e[q + 1:]
        c = find_top(tail.replace('::', '@@'), ':')
        if c >= 0:
            return (is_double(tail[:c], scope, sigs, pos)
                    or is_double(tail[c + 1:], scope, sigs, pos))

    # Expression booleenne : jamais un double.
    probe = e.replace('<=', '@@').replace('>=', '@@').replace('==', '@@').replace('!=', '@@')
    for op in ('&&', '||'):
        if find_top(probe, op) >= 0:
            return False
    for op in ('<=', '>=', '==', '!='):
        if find_top(e, op) >= 0:
            return False
    for op in ('<', '>'):
        if find_top(e, op) >= 0 and not re.search(r'[A-Za-z_]\s*<[\w\s,<>\[\]]*>\s*[({]', e):
            return False

    # Casts : le cast porte sur l'operande unaire qui suit, pas sur toute
    # l'expression. « (float) Math.floor(x) * 2.0 » reste un double a cause du
    # 2.0, mais Math.floor n'y est pour rien.
    while True:
        m = CAST_RE.search(e)
        if not m:
            break
        end = operand_end(e, m.end())
        e = e[:m.start()] + (DBL if m.group(1) == 'double' else NEU) + e[end:]

    # Appels, du plus interne au plus externe. Un appel de type de retour
    # inconnu devient NEUTRE : ses arguments ne doivent pas contaminer
    # l'expression qui l'englobe.
    while True:
        last = None
        for m in CALL_RE.finditer(e):
            last = m
        if last is None:
            break
        name = re.sub(r'\s+', '', last.group(1))
        op = last.end() - 1
        cl = match_paren(e, op)
        if cl < 0:
            break
        args = [a for a in split_top(e[op + 1:cl]) if a]
        if name in DOUBLE_FNS:
            rep = DBL
        elif name in OVERLOADED:
            rep = DBL if any(is_double(a, scope, sigs, pos) for a in args) else NEU
        elif name in FLOAT_FNS or name in INT_FNS:
            rep = NEU
        else:
            short = name.split('.')[-1]
            rets = set()
            if '.' not in name:
                for sig in sigs.get(short, {}).get(len(args), []):
                    rets.add(sig[0])
            rep = DBL if rets == {'double'} else NEU
        e = e[:last.start()] + rep + e[cl + 1:]

    for c in DOUBLE_CONSTS:
        if c in e:
            return True

    # Identifiants restants, resolus par position.
    for m in re.finditer(r'(?<![\w.$])([A-Za-z_$][\w$]*)', e):
        nm = m.group(1)
        after = e[m.end():].lstrip()
        indexed = after.startswith('[')
        if scope.typ(nm, pos, indexed) == 'double':
            return True

    # Litteral double nu (1.5 au lieu de 1.5f).
    return bool(DOUBLE_LIT.search(FLOAT_LIT.sub(' ', e)))


# --- Analyse ---------------------------------------------------------------

def line_of(src, pos):
    return src.count('\n', 0, pos) + 1


def check_file(path):
    raw = io.open(path, encoding='utf-8').read()
    clean = strip_noise(raw)
    sigs, bodies = collect(clean)

    body_spans = [(b0, b1) for _n, _r, _p, b0, b1 in bodies]

    def in_body(p):
        return any(b0 <= p < b1 for b0, b1 in body_spans)

    fields = [d for d in collect_decls(clean, 0, len(clean)) if not in_body(d[0])]

    alerts = []
    for name, ret, params, b0, b1 in bodies:
        body = clean[b0:b1]
        scope = Scope(fields, collect_decls(clean, b0, b1), params, b0)

        # 1. Argument double dans un parametre float.
        for m in CALL_RE.finditer(body):
            callee = re.sub(r'\s+', '', m.group(1))
            op = m.end() - 1
            cl = match_paren(body, op)
            if cl < 0:
                continue
            args = [a for a in split_top(body[op + 1:cl]) if a]
            target = None
            if (callee, len(args)) in API_FLOAT_PARAMS:
                idxs = API_FLOAT_PARAMS[(callee, len(args))]
                target = ['float' if i in idxs else '?' for i in range(len(args))]
            elif '.' not in callee:
                cands = sigs.get(callee, {}).get(len(args), [])
                # Surcharges de meme arite : impossible de trancher, on passe.
                if len(cands) == 1:
                    target = cands[0][1]
            if target is None:
                continue
            for i, a in enumerate(args):
                if i < len(target) and target[i] == 'float' and is_double(a, scope, sigs, b0 + m.start()):
                    alerts.append((line_of(clean, b0 + m.start()), name,
                                   "%s(), argument %d : « %s » vaut un double, le parametre est float"
                                   % (callee, i + 1, a.strip()[:60])))

        # 2. float x = <double>.
        for m in re.finditer(r'(?<![\w.])(?:final\s+)?float\s+([A-Za-z_]\w*)\s*=\s*([^;]+);', body):
            if is_double(m.group(2), scope, sigs, b0 + m.start()):
                alerts.append((line_of(clean, b0 + m.start()), name,
                               "float %s = ... : la valeur vaut un double" % m.group(1)))

        # 3. Affectation simple d'un double a un float deja declare.
        for m in re.finditer(r'(?<![\w.<>+\-*/%&|^!=])([A-Za-z_]\w*)\s*=(?!=)\s*([^;]+);', body):
            nm = m.group(1)
            if nm in PRIMS:
                continue
            if scope.typ(nm, b0 + m.start(), False) != 'float':
                continue
            if re.search(r'(?<![\w.])(float|double|int|long|short|byte)\s+%s\s*$' % re.escape(nm),
                         body[max(0, m.start() - 40):m.start()]):
                continue
            if is_double(m.group(2), scope, sigs, b0 + m.start()):
                alerts.append((line_of(clean, b0 + m.start()), name,
                               "%s = ... : affectation d'un double a un float" % nm))

        # 4. return d'un double dans une methode float.
        if ret == 'float':
            for m in re.finditer(r'(?<![\w.])return\s+([^;]+);', body):
                if is_double(m.group(1), scope, sigs, b0 + m.start()):
                    alerts.append((line_of(clean, b0 + m.start()), name,
                                   "return d'un double dans une methode qui rend un float"))

    return alerts


def main(argv):
    paths = [a for a in argv[1:] if a != '--all']
    if not paths:
        paths = []
        for dirpath, _dirs, files in os.walk('src/main/java'):
            for f in files:
                if f.endswith('.java'):
                    paths.append(os.path.join(dirpath, f))
        paths.sort()
    total = 0
    for p in paths:
        try:
            alerts = check_file(p)
        except Exception as exc:  # noqa: BLE001
            print("%s : ANALYSE IMPOSSIBLE (%s)" % (p, exc))
            continue
        seen = set()
        for ln, meth, msg in sorted(alerts):
            key = (ln, msg)
            if key in seen:
                continue
            seen.add(key)
            print("%s:%d  [%s]  %s" % (p, ln, meth, msg))
            total += 1
    print("floatcheck : %d fichier(s), %d alerte(s)" % (len(paths), total))
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
