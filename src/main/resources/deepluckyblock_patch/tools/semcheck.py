#!/usr/bin/env python3
"""
Contrôle sémantique léger pour les fichiers Java du mod, là où java-parser ne voit rien.

Deux familles d'erreurs, toutes deux rencontrées en vrai sur ce projet :

  (A) CONSTANTE INCONNUE — un identifiant en MAJUSCULES utilisé sans point devant
      (donc censé être une constante du fichier) alors qu'il n'est déclaré nulle part.
      Cas réel : « H » et « HALF_WIDTH » au lieu de SURGE_HEIGHT / SURGE_HALF_WIDTH.

  (B) UTILISATION AVANT DÉCLARATION — une variable locale lue plus haut que la ligne
      qui la déclare, dans la même méthode.
      Cas réel : « carved » utilisé dans le message client avant son int carved = ...

Heuristique volontairement prudente : on préfère rater un cas que noyer la sortie.
"""
import re
import sys

KEYWORDS = {
    'ABSTRACT', 'ASSERT', 'BOOLEAN', 'BREAK', 'BYTE', 'CASE', 'CATCH', 'CHAR', 'CLASS',
    'CONST', 'CONTINUE', 'DEFAULT', 'DO', 'DOUBLE', 'ELSE', 'ENUM', 'EXTENDS', 'FINAL',
    'FINALLY', 'FLOAT', 'FOR', 'GOTO', 'IF', 'IMPLEMENTS', 'IMPORT', 'INSTANCEOF', 'INT',
    'INTERFACE', 'LONG', 'NATIVE', 'NEW', 'PACKAGE', 'PRIVATE', 'PROTECTED', 'PUBLIC',
    'RETURN', 'SHORT', 'STATIC', 'SUPER', 'SWITCH', 'SYNCHRONIZED', 'THIS', 'THROW',
    'THROWS', 'TRANSIENT', 'TRY', 'VOID', 'VOLATILE', 'WHILE', 'TRUE', 'FALSE', 'NULL',
}
PRIMS = r'(?:int|float|double|long|boolean|char|byte|short|var|String|Vec3|BlockPos|BlockState|' \
        r'LivingEntity|ServerPlayer|ServerLevel|Mob|ItemStack|Component|AABB|Entity)'


def strip_noise(src):
    """Neutralise commentaires et chaînes, en préservant les numéros de ligne."""
    out = []
    i, n = 0, len(src)
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
        elif c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == '\\' else 1
            j = min(j + 1, n)
            out.append(''.join(ch if ch == '\n' else ' ' for ch in src[i:j]))
            i = j
        elif c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == '\\' else 1
            j = min(j + 1, n)
            out.append(' ' * (j - i))
            i = j
        else:
            out.append(c)
            i += 1
    return ''.join(out)


GLOBAL_CONSTS = None


def project_constants(root='src/main/java'):
    """Toutes les constantes/enums déclarées dans le projet : une constante peut très bien
    venir d'un autre fichier (import statique, case d'un switch sur enum, …)."""
    global GLOBAL_CONSTS
    if GLOBAL_CONSTS is not None:
        return GLOBAL_CONSTS
    import os
    found = set()
    for dirpath, _dirs, files in os.walk(root):
        if 'FIXES_MCREATOR' in dirpath:
            continue
        for f in files:
            if not f.endswith('.java'):
                continue
            try:
                txt = open(os.path.join(dirpath, f), encoding='utf-8', errors='ignore').read()
            except OSError:
                continue
            found |= set(re.findall(r'\bstatic\s+(?:final\s+)?[\w<>\[\],.\s]+?\b([A-Z][A-Z0-9_]{1,})\s*[=;]', txt))
            # constantes d'enum : NOM( ou NOM, ou NOM; en tête de ligne
            for body in re.findall(r'\benum\s+\w+[^{]*\{(.*?)\n\s*(?:public|private|protected|\})', txt, re.S):
                found |= set(re.findall(r'(?:^|,|\{)\s*([A-Z][A-Z0-9_]{1,})\s*[(,;]', body))
    GLOBAL_CONSTS = found
    return found


def check_constants(path, clean):
    # champs static, AVEC ou SANS final (LAST_WIND_ZONE_TICK, NEXT_MIASMA_CHAIN_ID…)
    declared = set(re.findall(r'\bstatic\s+(?:final\s+)?[\w<>\[\],.\s]+?\b([A-Z][A-Z0-9_]*)\s*[=;]', clean))
    declared |= set(re.findall(r'\bfinal\s+static\s+[\w<>\[\],.\s]+?\b([A-Z][A-Z0-9_]*)\s*[=;]', clean))
    # tout identifiant importé est un NOM DE CLASSE, pas une constante manquante (AABB, Vec3…)
    declared |= set(re.findall(r'^import\s+(?:static\s+)?[\w.]*?([A-Za-z_]\w*);', clean, re.M))
    # classes déclarées dans le fichier et types du même paquet
    declared |= set(re.findall(r'\b(?:class|interface|enum|record)\s+(\w+)', clean))
    declared |= project_constants()
    declared |= set(re.findall(r'\benum\s+\w+\s*\{([^}]*)\}', clean, re.S) and
                    re.findall(r'\b([A-Z][A-Z0-9_]{2,})\s*[(,;]', ' '.join(re.findall(r'\benum\s+\w+\s*\{([^}]*)\}', clean, re.S))) or [])
    # constantes d'enum déclarées dans le fichier, et champs record
    problems = []
    for m in re.finditer(r'(?<![.\w])([A-Z][A-Z0-9_]{0,})(?![\w(])', clean):
        name = m.group(1)
        if len(name) < 1 or name in KEYWORDS or name in declared:
            continue
        if len(name) == 1 and name not in ('H',):      # lettres seules : trop ambigu sauf H
            continue
        if re.fullmatch(r'[A-Z]', name) and name != 'H':
            continue
        line = clean.count('\n', 0, m.start()) + 1
        problems.append((line, name))
    return problems


def check_use_before_decl(path, clean):
    problems = []
    # découpe grossière en méthodes : "<modifiers> type name(...) {" au niveau 1
    for mm in re.finditer(r'\n    (?:public|private|protected|static|final|\s)+[\w<>\[\],.\s]+\s(\w+)\s*\([^;{]*\)\s*\{', clean):
        start = mm.end() - 1
        depth, i, n = 0, start, len(clean)
        while i < n:
            if clean[i] == '{':
                depth += 1
            elif clean[i] == '}':
                depth -= 1
                if depth == 0:
                    break
            i += 1
        body = clean[start:i]
        base_line = clean.count('\n', 0, start) + 1
        # Une variable peut être redéclarée dans plusieurs blocs frères : seule la PREMIÈRE
        # déclaration compte, sinon chaque boucle produit un faux positif.
        first_decl = {}
        for dm in re.finditer(r'(?<![\w.])(?:final\s+)?' + PRIMS + r'\s+(\w+)\s*[=;)]', body):
            var = dm.group(1)
            if var not in first_decl:
                first_decl[var] = dm.start()
        # idem pour les variables de boucle for(X a : ...) et les captures de pattern
        for dm in re.finditer(r'\bfor\s*\([^;)]*?\b(\w+)\s*[:=]', body):
            var = dm.group(1)
            if var not in first_decl or dm.start() < first_decl[var]:
                first_decl[var] = dm.start()
        for dm in re.finditer(r'\binstanceof\s+\w+\s+(\w+)', body):
            var = dm.group(1)
            if var not in first_decl or dm.start() < first_decl[var]:
                first_decl[var] = dm.start()
        for var, decl_at in first_decl.items():
            um = re.search(r'(?<![\w.])' + re.escape(var) + r'(?![\w])', body)
            if um is None or um.start() >= decl_at:
                continue
            problems.append((base_line + body.count('\n', 0, um.start()), var, mm.group(1)))
    return problems


def main(paths):
    bad = 0
    for path in paths:
        src = open(path, encoding='utf-8').read()
        clean = strip_noise(src)
        a = check_constants(path, clean)
        b = check_use_before_decl(path, clean)
        name = path.split('/')[-1]
        if not a and not b:
            print(f'OK   {name}')
            continue
        bad += 1
        for line, ident in a:
            print(f'ERR  {name}:{line}  constante inconnue « {ident} »')
        for line, var, meth in b:
            print(f'ERR  {name}:{line}  « {var} » lu avant sa déclaration (dans {meth})')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
