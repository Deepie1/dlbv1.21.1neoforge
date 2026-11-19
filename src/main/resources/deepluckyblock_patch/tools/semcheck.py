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

  (C) SOUNDEVENTS .value() EN TROP — en 1.21.1 les champs de SoundEvents sont de DEUX
      types : la plupart sont des SoundEvent nus, une minorité (117) sont des
      Holder.Reference<SoundEvent>. Appeler .value() sur un champ nu ne compile pas
      (« cannot find symbol: method value() »). L'inverse n'est PAS une erreur :
      playSound et ArmorMaterial acceptent un Holder tel quel. On ne signale donc que
      le .value() en trop, et les constantes qui n'existent pas du tout.
      Cas réel : SoundEvents.GENERIC_DRINK.value() — Holder en 1.21.4, nu en 1.21.1.

Heuristique volontairement prudente : on préfère rater un cas que noyer la sortie.
"""
import json
import os
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
                # re.M est INDISPENSABLE : sans lui « ^ » ne vaut qu'au tout debut de la
                # chaine, et la premiere constante qui suit un commentaire de section
                # (« // === III. SANG ... === ») restait invisible. C'est ce qui faisait
                # passer DRIPPING_BLOOD_EDGE pour une constante inconnue alors qu'elle est
                # declaree ligne 53 de WeaponVisualEffectId.
                found |= set(re.findall(r'(?:^|,|\{)\s*([A-Z][A-Z0-9_]{1,})\s*[(,;]', body, re.M))
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
    # variables LOCALES en majuscules. « final float H = 1.22f * s; » au debut d'une
    # methode est une declaration aussi valable qu'un champ static : sans cette regle, un
    # modele geometrique qui nomme ses grandeurs H, AV, R... sort des dizaines de faux
    # positifs. On reste prudent : seulement les types primitifs, var et String.
    declared |= set(re.findall(
        r'\b(?:final\s+)?(?:float|double|int|long|boolean|char|byte|short|var|String)\s+'
        r'([A-Z][A-Z0-9_]*)\s*=', clean))
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


SOUND_TABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           'soundevents_1211.json')


def _span_jusqu_au_point_virgule(clean, debut):
    """Fin de l'instruction qui commence a `debut` : le premier ';' hors accolade et
       hors parenthese. Un switch expression en contient plusieurs, d'ou le comptage."""
    i, prof = debut, 0
    while i < len(clean):
        c = clean[i]
        if c in '({[':
            prof += 1
        elif c in ')}]':
            prof -= 1
        elif c == ';' and prof <= 0:
            return i
        i += 1
    return len(clean)


def _args_de_premier_niveau(texte):
    """Decoupe une liste d'arguments sur les virgules de premier niveau."""
    args, prof, debut = [], 0, 0
    for i, c in enumerate(texte):
        if c in '({[':
            prof += 1
        elif c in ')}]':
            prof -= 1
        elif c == ',' and prof == 0:
            args.append((debut, texte[debut:i]))
            debut = i + 1
    args.append((debut, texte[debut:]))
    return args


def check_sound_events(path, clean):
    """(C) Le .value() en trop ET le .value() manquant, plus les constantes inexistantes.

       Le .value() manquant ne se voit QUE dans un contexte type : `Level.playSound`
       accepte aussi bien `SoundEvent` que `Holder<SoundEvent>`, donc l'immense majorite
       des appels est licite dans les deux formes. Trois contextes exigent le son nu :
         1. une declaration `SoundEvent x = ...;` (switch expression compris) ;
         2. un `return` dans une methode qui rend un `SoundEvent` ;
         3. un argument passe a une methode du fichier dont le parametre est `SoundEvent`.
       C'est le cas 1 qui a casse la compilation en novembre 2026 : une branche de switch
       rendait Holder.Reference<SoundEvent> quand les autres rendaient SoundEvent."""
    try:
        with open(SOUND_TABLE, encoding='utf-8') as fh:
            table = json.load(fh)
    except (OSError, ValueError):
        return []
    holder = set(table['holder'])
    plain = set(table['plain'])
    problems = []
    vus = set()
    jeton = re.compile(r'SoundEvents\.([A-Z][A-Z0-9_]*)(\s*\.\s*value\s*\(\s*\))?')

    def exige_son_nu(depart, fin, pourquoi):
        for m in jeton.finditer(clean, depart, fin):
            if m.group(2) or m.group(1) not in holder or m.start() in vus:
                continue
            vus.add(m.start())
            problems.append((clean.count('\n', 0, m.start()) + 1, m.group(1),
                             'declare Holder.Reference<SoundEvent> et ' + pourquoi
                             + ' : .value() manquant (ne compile pas)'))

    # 1. SoundEvent x = ... ;  — et surtout le switch expression, ou une seule branche
    #    en Holder suffit a faire echouer tout le switch.
    for d in re.finditer(r'(?<![\w.])SoundEvent\s+[a-zA-Z_]\w*\s*=', clean):
        if clean[:d.start()].rstrip().endswith('<'):
            continue
        exige_son_nu(d.end(), _span_jusqu_au_point_virgule(clean, d.end()),
                     'sert ici a remplir un SoundEvent')

    # 2. return, dans une methode qui rend un SoundEvent nu.
    for d in re.finditer(r'(?<![\w.<])SoundEvent\s+[a-zA-Z_]\w*\s*\([^;{)]*\)\s*\{', clean):
        corps, prof, i = None, 0, d.end() - 1
        while i < len(clean):
            if clean[i] == '{':
                prof += 1
            elif clean[i] == '}':
                prof -= 1
                if prof == 0:
                    corps = i
                    break
            i += 1
        if corps is None:
            continue
        for r in re.finditer(r'(?<![\w.])return\b', clean[d.end():corps]):
            depart = d.end() + r.end()
            exige_son_nu(depart, _span_jusqu_au_point_virgule(clean, depart),
                         'est rendu par une methode qui declare SoundEvent')

    # 3. Les methodes du fichier qui prennent un SoundEvent en parametre.
    attendus = {}
    for d in re.finditer(r'(?<![\w.])(?:private|public|protected|static|final|\s)+'
                         r'[\w.<>\[\]]+\s+([a-zA-Z_]\w*)\s*\(([^;{)]*)\)\s*\{', clean):
        for rang, (_, arg) in enumerate(_args_de_premier_niveau(d.group(2))):
            if re.search(r'(?<![\w.<])SoundEvent\s+[a-zA-Z_]\w*\s*$', arg.strip()):
                attendus.setdefault(d.group(1), set()).add(rang)
    for nom, rangs in attendus.items():
        for appel in re.finditer(r'(?<![\w.])' + re.escape(nom) + r'\s*\(', clean):
            fin, prof, i = None, 0, appel.end() - 1
            while i < len(clean):
                if clean[i] in '([{':
                    prof += 1
                elif clean[i] in ')]}':
                    prof -= 1
                    if prof == 0:
                        fin = i
                        break
                i += 1
            if fin is None:
                continue
            for rang, (decal, _) in enumerate(_args_de_premier_niveau(clean[appel.end():fin])):
                if rang in rangs:
                    debut = appel.end() + decal
                    exige_son_nu(debut, appel.end() + decal + len(_args_de_premier_niveau(
                        clean[appel.end():fin])[rang][1]),
                        'est passe a ' + nom + '(), qui attend un SoundEvent')

    for m in jeton.finditer(clean):
        ident, call = m.group(1), m.group(2)
        line = clean.count('\n', 0, m.start()) + 1
        if ident not in holder and ident not in plain:
            problems.append((line, ident, "cette constante n'existe pas en 1.21.1"))
        elif call and ident in plain:
            problems.append((line, ident,
                             'est un SoundEvent nu : .value() en trop (ne compile pas)'))
    problems.sort()
    return problems


def main(paths):
    bad = 0
    for path in paths:
        src = open(path, encoding='utf-8').read()
        clean = strip_noise(src)
        a = check_constants(path, clean)
        b = check_use_before_decl(path, clean)
        c = check_sound_events(path, clean)
        name = path.split('/')[-1]
        if not a and not b and not c:
            print(f'OK   {name}')
            continue
        bad += 1
        for line, ident in a:
            print(f'ERR  {name}:{line}  constante inconnue « {ident} »')
        for line, var, meth in b:
            print(f'ERR  {name}:{line}  « {var} » lu avant sa déclaration (dans {meth})')
        for line, ident, why in c:
            print(f'ERR  {name}:{line}  SoundEvents.{ident} — {why}')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
