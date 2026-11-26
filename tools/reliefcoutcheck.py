#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
T493 -- VERROUS SUR LE COUT DU DIAGNOSTIC.

Le 26/11, le calcul du relief theorique a coute 442 530 ms pour 12 544 colonnes
(35,3 ms/colonne), sur une zone qui n'etait meme pas celle du chantier. Sur la
vraie zone (230 144 colonnes) il aurait fallu 2 h 17. Il a tourne malgre tout,
a affame le serveur pendant plus de huit minutes (jusqu'a 15 351 ms de retard
par tick), a continue 3 min 25 APRES que le redacteur eut renonce, et le rapport
est sorti avec ses sections theoriques vides.

Et le compte-rendu d'un habillage incomplet passait par DebugLog.warn(), eteint
par defaut : le correctif T492 etait muet chez le dev.

Ces verrous interdisent le retour de chacun de ces points.
"""
import io, os, re, sys

RACINE = os.path.dirname(os.path.abspath(__file__))
if os.path.basename(RACINE) == 'tools':
    RACINE = os.path.dirname(RACINE)

SCAN  = os.path.join(RACINE, 'src/main/java/deepluckyblock/dev/DevTerrainScan.java')
DEBUG = os.path.join(RACINE, 'src/main/java/deepluckyblock/util/DebugLog.java')
PREP  = os.path.join(RACINE, 'src/main/java/deepluckyblock/procedures/StructureTerrainPrep.java')

echecs = []


def lire(p):
    if not os.path.exists(p):
        echecs.append("fichier absent : %s" % p)
        return ""
    return io.open(p, encoding='utf-8').read()


def bloc(code, depuis):
    """Corps {...} de la declaration qui commence a `depuis` (appariement reel)."""
    i = code.find('{', depuis)
    if i < 0:
        return ""
    p = 0
    j = i
    e = None
    while j < len(code):
        c = code[j]
        if e is None:
            if code.startswith('//', j):
                j = code.find('\n', j)
                if j < 0:
                    break
                continue
            if code.startswith('/*', j):
                j = code.find('*/', j) + 2
                continue
            if c in '"\'':
                e = c
            elif c == '{':
                p += 1
            elif c == '}':
                p -= 1
                if p == 0:
                    return code[i:j + 1]
        elif c == '\\':
            j += 2
            continue
        elif c == e:
            e = None
        j += 1
    return ""


def corps(code, signature):
    m = re.search(signature, code)
    if not m:
        return None
    return bloc(code, m.start())


# ---------------------------------------------------------------- 1
def v1_attente_bornee(c):
    """L'attente du relief ne doit plus etre une attente de plusieurs minutes."""
    m = re.search(r'BRUIT_ATTENTE_MS\s*=\s*([0-9_]+)L?\s*;', c)
    if not m:
        return "BRUIT_ATTENTE_MS introuvable"
    v = int(m.group(1).replace('_', ''))
    if v > 60_000:
        return ("BRUIT_ATTENTE_MS = %d ms : le redacteur attendrait %d s avant d'ecrire. "
                "Le 26/11 cette attente a coute 240 s par session pour 9 s d'ecriture." % (v, v // 1000))
    return None


# ---------------------------------------------------------------- 2
def v2_budget_existe(c):
    """Un budget dur doit exister et etre raisonnable."""
    m = re.search(r'BRUIT_BUDGET_MS\s*=\s*([0-9_]+)L?\s*;', c)
    if not m:
        return ("BRUIT_BUDGET_MS absent : rien ne borne le cout du relief theorique. "
                "Le 26/11 il a tourne 442 s.")
    v = int(m.group(1).replace('_', ''))
    if v > 120_000:
        return "BRUIT_BUDGET_MS = %d ms : trop large pour un calcul de confort." % v
    return None


# ---------------------------------------------------------------- 3
def v3_estimation_avant_calcul(c):
    """calculerBruit doit ESTIMER puis RENONCER, pas se lancer tete baissee."""
    b = corps(c, r'private\s+static\s+void\s+calculerBruit\s*\(')
    if b is None:
        return "calculerBruit introuvable"
    if 'BRUIT_BUDGET_MS' not in b:
        return "calculerBruit ne consulte aucun budget."
    # il faut une comparaison d'un cout prevu au budget, suivie d'un retour.
    if not re.search(r'if\s*\([^)]*>\s*BRUIT_BUDGET_MS\s*\)', b):
        return ("calculerBruit ne compare pas un cout prevu a BRUIT_BUDGET_MS : "
                "il ne peut pas renoncer avant de commencer.")
    m = re.search(r'if\s*\([^)]*>\s*BRUIT_BUDGET_MS\s*\)', b)
    suite = b[m.end():m.end() + 1400]
    if 'return' not in suite:
        return "la comparaison au budget ne mene a aucun abandon (pas de return)."
    return None


# ---------------------------------------------------------------- 4
def v4_calibrage_mesure(c):
    """Le cout par colonne doit etre MESURE, pas suppose."""
    b = corps(c, r'private\s+static\s+void\s+calculerBruit\s*\(')
    if b is None:
        return "calculerBruit introuvable"
    if 'BRUIT_SONDES_CALIBRAGE' not in b or 'nanoTime' not in b:
        return ("calculerBruit ne mesure pas le cout reel d'une colonne (sondes de "
                "calibrage + nanoTime) : toute estimation serait une supposition.")
    return None


# ---------------------------------------------------------------- 5
def v5_annulation_reelle(c):
    """Renoncer doit ARRETER le calcul, pas seulement cesser de l'attendre."""
    b = corps(c, r'private\s+static\s+void\s+attendreBruit\s*\(')
    if b is None:
        return "attendreBruit introuvable"
    if 'bruitAnnule' not in b:
        return ("attendreBruit ne pose pas bruitAnnule : le fil continuerait a tourner "
                "pour personne, comme le 26/11 (3 min 25 de CPU apres l'abandon).")
    if 'interrupt' not in b:
        return "attendreBruit n'interrompt pas le fil du relief."
    b2 = corps(c, r'private\s+static\s+(?:boolean\s+)?bruitPerime\s*\(')
    if b2 is None or 'bruitAnnule' not in b2:
        return "bruitPerime ne lit pas bruitAnnule : le drapeau serait pose mais jamais lu."
    bc = corps(c, r'private\s+static\s+void\s+calculerBruit\s*\(')
    if bc is None or 'bruitPerime' not in bc:
        return "calculerBruit ne consulte jamais bruitPerime : il ne peut pas etre annule."
    return None


# ---------------------------------------------------------------- 6
def v6_priorite_basse(c):
    """Le fil ne doit pas concurrencer le serveur."""
    b = corps(c, r'private\s+static\s+void\s+lancerBruit\s*\(')
    if b is None:
        return "lancerBruit introuvable"
    if re.search(r'setPriority\s*\(\s*[^)]*NORM_PRIORITY\s*-\s*1', b):
        return ("le fil du relief tourne en NORM_PRIORITY-1 : c'est ce reglage qui a vole "
                "10 a 15 s par tick au serveur le 26/11.")
    if not re.search(r'setPriority\s*\(\s*Thread\.MIN_PRIORITY\s*\)', b):
        return "le fil du relief n'est pas explicitement en MIN_PRIORITY."
    return None


# ---------------------------------------------------------------- 7
def v7_respiration(c):
    """Un fil de fond doit rendre la main, la priorite seule ne suffit pas."""
    b = corps(c, r'private\s+static\s+void\s+calculerBruit\s*\(')
    if b is None:
        return "calculerBruit introuvable"
    if 'Thread.sleep' not in b:
        return ("calculerBruit ne rend jamais la main (aucun Thread.sleep) : sur 4 coeurs "
                "la priorite basse ne suffit pas a liberer le serveur.")
    return None


# ---------------------------------------------------------------- 8
def v8_pas_de_clobbering(c):
    """Ouvrir une session doit couper le relief de la precedente."""
    # TOUS les points d'ouverture, pas le premier : il y en a deux (automatique et
    # /dlbscan manuel), et n'en couvrir qu'un laisse le defaut entier sur l'autre.
    sites = [m.start() for m in re.finditer(r'=\s*ouvrir\s*\(\s*level\s*,', c)]
    if not sites:
        return "aucun appel a ouvrir() trouve"
    nus = []
    for i in sites:
        amont = c[max(0, i - 700):i]
        if 'couperBruit' not in amont:
            nus.append(c[:i].count('\n') + 1)
    if nus:
        return ("point(s) d'ouverture sans coupure du relief precedent : ligne(s) %s. "
                "Le 26/11 la session fantome a ecrase le calcul de la vraie (12 544 "
                "colonnes publiees a la place des 230 144 attendues)."
                % ", ".join(str(n) for n in nus))
    b = corps(c, r'private\s+static\s+void\s+couperBruit\s*\(')
    if b is None:
        return "couperBruit() introuvable"
    if 'bruitAnnule' not in b or 'interrupt' not in b:
        return "couperBruit() ne pose pas bruitAnnule et/ou n'interrompt pas le fil."
    return None


# ---------------------------------------------------------------- 9
def v9_canal_alerte_inextinguible(c):
    """alerte() doit exister et NE PAS dependre de ENABLED."""
    b = corps(c, r'public\s+static\s+void\s+alerte\s*\(')
    if b is None:
        return "DebugLog.alerte() absent."
    if 'ENABLED' in b or 'STRUCTURE' in b:
        return ("alerte() consulte ENABLED/STRUCTURE : elle s'eteindrait comme warn(), "
                "et un travail incomplet redeviendrait silencieux.")
    if not re.search(r'LOGGER\s*\.\s*(warn|error)\s*\(', b):
        return "alerte() n'ecrit rien dans le log."
    return None


# ---------------------------------------------------------------- 10
def v10_reliquats_en_alerte(c):
    """Les deux compte-rendus d'incomplétude doivent passer par alerte()."""
    manquants = []
    for motif, nom in ((r'NON HABILLEES', 'habillage'), (r'NON REPLANTES', 'replantation')):
        m = re.search(motif, c)
        if not m:
            manquants.append("%s : compte-rendu absent" % nom)
            continue
        amont = c[max(0, m.start() - 420):m.start()]
        if 'DebugLog.alerte' not in amont:
            manquants.append("%s : le reliquat passe par un canal extinguible "
                             "(warn/structure), il serait invisible chez le dev" % nom)
    return " | ".join(manquants) if manquants else None


# ---------------------------------------------------------------- 11
def v11_alerte_bornee(c):
    """alerte() ne doit pas etre appelee dans une boucle par colonne."""
    for m in re.finditer(r'DebugLog\.alerte\s*\(', c):
        amont = c[max(0, m.start() - 300):m.start()]
        # un for/while immediatement amont, sans fermeture, trahit un appel en boucle
        if re.search(r'\b(for|while)\s*\([^)]*\)\s*\{[^{}]*$', amont):
            lig = c[:m.start()].count('\n') + 1
            return ("alerte() appelee dans une boucle (ligne %d) : un canal "
                    "inextinguible dans une boucle est un spam garanti." % lig)
    return None


# ---------------------------------------------------------------- 12
def v12_rattrapage_pilote_par_progres(c):
    """Le rattrapage ne doit pas dependre d'un nombre de tours choisi au hasard."""
    # Chercher le NOM ne suffit pas : renommer la declaration laisse les usages
    # intacts et le simple « in c » se fait rassurer par eux. On exige une
    # DECLARATION reelle, avec une valeur utilisable, ET au moins un usage.
    manquants = []
    for pref, quoi in (('DRESS_RETRY', 'habillage'), ('REPLANT_RETRY', 'replantation')):
        for suffixe, role, mini in (('STAGNATION_MAX', 'critere de stagnation', 1),
                                    ('GRACE_TOURS', 'tours de grace', 1)):
            nom = '%s_%s' % (pref, suffixe)
            m = re.search(r'static\s+final\s+int\s+' + nom + r'\s*=\s*([0-9_]+)\s*;', c)
            if not m:
                manquants.append("%s : %s NON DECLARE (%s). Le rattrapage retomberait sur "
                                 "un compteur de tours arbitraire -- 6 tours en T492, soit "
                                 "12 s, alors que les chunks du sud ont mis bien plus "
                                 "longtemps a arriver." % (quoi, role, nom))
                continue
            if int(m.group(1).replace('_', '')) < mini:
                manquants.append("%s : %s declare a %s, donc inoperant."
                                 % (quoi, role, m.group(1)))
            # declare mais jamais lu = decoration
            if len(re.findall(nom + r'\b', c)) < 2:
                manquants.append("%s : %s declare mais JAMAIS lu." % (quoi, role))
    return " | ".join(manquants) if manquants else None


# ---------------------------------------------------------------- 13
def v13_rattrapage_termine(c):
    """La boucle de rattrapage doit avoir un plafond, une stagnation ET une butoir."""
    m = re.search(r'DRESS_RETRY_TOURS\s*=\s*([0-9_]+)\s*;', c)
    if not m:
        return "DRESS_RETRY_TOURS introuvable"
    if int(m.group(1).replace('_', '')) > 200:
        return "DRESS_RETRY_TOURS trop grand : la terminaison n'est plus garantie."
    i = c.find('tours[0] < DRESS_RETRY_TOURS')
    if i < 0:
        return "la condition de poursuite du rattrapage d'habillage est introuvable"
    cond = c[i:i + 320]
    for quoi, nom in (('DRESS_RETRY_STAGNATION_MAX', 'la stagnation'),
                      ('DRESS_RETRY_DEADLINE_MS', 'la date butoir')):
        if quoi not in cond:
            return "la poursuite du rattrapage ne consulte pas %s" % nom
    j = c.find('tourRattrapage < REPLANT_RETRY_TOURS')
    if j < 0:
        return "la condition de poursuite de la replantation est introuvable"
    if 'REPLANT_RETRY_STAGNATION_MAX' not in c[j:j + 320]:
        return "la poursuite de la replantation ne consulte pas la stagnation"
    return None


VERROUS = [
    ("1  attente du relief bornee",            SCAN,  v1_attente_bornee),
    ("2  budget dur du relief",                SCAN,  v2_budget_existe),
    ("3  estimation AVANT de se lancer",       SCAN,  v3_estimation_avant_calcul),
    ("4  cout par colonne mesure",             SCAN,  v4_calibrage_mesure),
    ("5  annulation reelle du fil",            SCAN,  v5_annulation_reelle),
    ("6  fil en priorite minimale",            SCAN,  v6_priorite_basse),
    ("7  fil qui rend la main",                SCAN,  v7_respiration),
    ("8  pas d'ecrasement entre sessions",     SCAN,  v8_pas_de_clobbering),
    ("9  canal d'alerte inextinguible",        DEBUG, v9_canal_alerte_inextinguible),
    ("10 reliquats dits a voix haute",         PREP,  v10_reliquats_en_alerte),
    ("11 alerte jamais en boucle",             PREP,  v11_alerte_bornee),
    ("12 rattrapage pilote par le progres",    PREP,  v12_rattrapage_pilote_par_progres),
    ("13 rattrapage qui termine",              PREP,  v13_rattrapage_termine),
]


def main():
    caches = {}
    for nom, chemin, fn in VERROUS:
        if chemin not in caches:
            caches[chemin] = lire(chemin)
        try:
            pb = fn(caches[chemin])
        except Exception as ex:
            pb = "verrou casse : %r" % (ex,)
        if pb:
            echecs.append("%s -> %s" % (nom, pb))
            print("  ECHEC  %s" % nom)
            print("         %s" % pb)
        else:
            print("  ok     %s" % nom)

    print()
    if echecs:
        print("reliefcoutcheck : %d ECHEC(S) sur %d verrous" % (len(echecs), len(VERROUS)))
        sys.exit(1)
    print("reliefcoutcheck : %d/%d verrous OK" % (len(VERROUS), len(VERROUS)))


if __name__ == '__main__':
    main()
