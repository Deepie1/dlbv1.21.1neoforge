#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
structureannouncecheck.py — 8e banc permanent (T449).

Verrouille DEUX regressions constatees en jeu.

=====================================================================
VERROU 1 — « STRUCTURE DISCOVERED » POUR *TOUTE* STRUCTURE
=====================================================================
Consigne utilisateur, litterale : « lorsqu'une structure spawn, tu dois
TOUJOURS mettre le message de discovery avec les coordonnees une fois
apparu ». Constate : circus a spawn sans aucun message.

Deux defauts cumules :
  1. Structures4Procedure (circus id 3, shipdead id 4, everest id 5)
     ne contenait AUCUN appel d'annonce. Seul Structures5Procedure
     (citadel / observatory / dragon) annoncait.
  2. announceStructureSpawn sortait en silence sur `player == null`,
     alors que le porteur vient d'un getNearestPlayer(..., 128, ...)
     evalue au PLAN, des minutes avant la fin de pose.

Ce banc echoue si :
  - un des trois points de fin de pose n'appelle plus l'annonce ;
  - announceStructureSpawn redevient silencieuse sur player == null ;
  - l'annonce de Structures5 retombe a l'interieur du `if (j.player != null)` ;
  - la table des noms d'affichage perd une structure.

=====================================================================
VERROU 2 — TAUX DE RETOUR DES DEEP LUCKY BLOCKS
=====================================================================
Consigne : « le taux de drop d'un nouveau DLB doit etre fixe a 2 % »,
« retire les bonus de chance trop boostes », « divise par 2 les gains de
DLB via les achievements ». Mesure du defaut : 50 DLB casses => plus de
20 rendus hors achievement (~40 %).

Cause reelle (contre-intuitive) : il n'existait AUCUN drop par cassage.
Les blocs venaient des averses de serie — onLuckyStreak versait
0-5 blocs et triggerNegativeStreak 0-6, a CHAQUE serie (seuils 5 et -3),
sans verrou d'achievement, donc en boucle.

Ce banc echoue si :
  - SELF_DROP_PERCENT n'est plus exactement 2 ;
  - une averse de blocs reapparait dans les methodes de serie ;
  - un palier d'achievement remonte au-dessus de sa valeur divisee ;
  - un bonus de chance repasse au-dessus du plafond baisse.

Sortie vide + code 0 = succes.
"""

import io
import os
import re
import sys

def sans_commentaires_gardant_chaines(src):
    """Retire // et /* */ sans toucher au contenu des chaines."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c in '"\'':
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == c:
                    break
                j += 1
            out.append(src[i:j + 1])
            i = j + 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            if j < 0:
                break
            i = j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i)
            i = (j + 2) if j >= 0 else n
            continue
        out.append(c)
        i += 1
    return "".join(out)


RACINE = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", ".."))
JAVA = os.path.join(RACINE, "src", "main", "java", "deepluckyblock")

S4 = os.path.join(JAVA, "procedures", "Structures4Procedure.java")
S5 = os.path.join(JAVA, "procedures", "Structures5Procedure.java")
BLOC = os.path.join(JAVA, "block", "DeepLuckyBlockBlock.java")
ADV = os.path.join(JAVA, "advancements", "DeepLuckyBlockAdvancements.java")
GEN = os.path.join(JAVA, "procedures", "GenerateLuckyItemProcedure.java")

problemes = []


def lire(chemin):
    if not os.path.isfile(chemin):
        problemes.append("FICHIER ABSENT : %s" % chemin)
        return None
    return io.open(chemin, encoding="utf-8").read()


def sans_commentaires(src):
    """Retire commentaires et litteraux pour ne juger que du code reel."""
    out = []
    i, n, etat = 0, len(src), None
    while i < n:
        c = src[i]
        if etat is None:
            if c == "/" and i + 1 < n and src[i + 1] == "/":
                while i < n and src[i] != "\n":
                    i += 1
            elif c == "/" and i + 1 < n and src[i + 1] == "*":
                i += 2
                while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                    i += 1
                i += 2
            elif c == '"':
                etat = '"'
                out.append('""')
                i += 1
            else:
                out.append(c)
                i += 1
        else:
            if c == "\\":
                i += 2
            elif c == etat:
                etat = None
                i += 1
            else:
                i += 1
    return "".join(out)


def corps_methode(code, signature):
    """Extrait le corps d'une methode par comptage d'accolades."""
    d = code.find(signature)
    if d < 0:
        return None
    d = code.find("{", d)
    if d < 0:
        return None
    prof, i = 0, d
    while i < len(code):
        if code[i] == "{":
            prof += 1
        elif code[i] == "}":
            prof -= 1
            if prof == 0:
                return code[d:i + 1]
        i += 1
    return None


# =====================================================================
# VERROU 1
# =====================================================================
s5 = lire(S5)
s4 = lire(S4)

if s5 is not None:
    c5 = sans_commentaires(s5)

    corps = corps_methode(c5, "announceStructureSpawn(ServerLevel level, Player player")
    if corps is None:
        problemes.append(
            "ANNONCE : announceStructureSpawn(ServerLevel, Player, String, BlockPos, BlockPos) "
            "introuvable dans Structures5Procedure. Signature changee ou methode supprimee.")
    else:
        # Elle doit rester publique : Structures4Procedure l'appelle.
        if not re.search(r"public\s+static\s+void\s+announceStructureSpawn", c5):
            problemes.append(
                "ANNONCE : announceStructureSpawn n'est plus publique -- "
                "Structures4Procedure (circus/shipdead/everest) ne peut plus l'appeler.")
        # Interdiction du retour silencieux sur porteur absent.
        if re.search(r"if\s*\(\s*player\s*==\s*null\s*\)\s*return\s*;", corps):
            problemes.append(
                "ANNONCE : retour silencieux « if (player == null) return; » REAPPARU dans "
                "announceStructureSpawn. Une structure posee loin du joueur, ou apres sa "
                "deconnexion, redevient muette. Le repli doit diffuser a level.players().")
        # Le repli de diffusion doit exister.
        if "level.players()" not in corps:
            problemes.append(
                "ANNONCE : le repli de diffusion (level.players()) a disparu "
                "d'announceStructureSpawn ; sans porteur, plus aucun message.")

    # L'appel de Structures5 doit etre HORS du test de porteur.
    tick = corps_methode(c5, "public static void onServerTick")
    if tick is None:
        problemes.append("ANNONCE : onServerTick introuvable dans Structures5Procedure.")
    else:
        appels = [m for m in re.finditer(r"announceStructureSpawn\s*\(", tick)]
        if not appels:
            problemes.append(
                "ANNONCE : Structures5Procedure n'annonce plus la fin de pose "
                "(citadel / observatory / dragon sans message).")
        for m in appels:
            avant = tick[max(0, m.start() - 420):m.start()]
            # Si le dernier bloc ouvert juste avant est un test de porteur, l'annonce
            # est de nouveau conditionnee -> defaut T449 exactement.
            dernier = None
            for g in re.finditer(r"if\s*\(\s*j\.player\s*!=\s*null\s*\)\s*\{", avant):
                dernier = g
            if dernier is not None:
                reste = avant[dernier.end():]
                if reste.count("}") == 0:
                    problemes.append(
                        "ANNONCE : l'appel de Structures5Procedure est RETOMBE dans "
                        "« if (j.player != null) { ... } ». L'annonce doit etre hors de ce test.")

    # Table des noms d'affichage complete.
    noms = corps_methode(c5, "String structureDisplayName(String rawName)")
    if noms is None:
        problemes.append("ANNONCE : structureDisplayName introuvable.")
    else:
        for cle in ("citadel", "observatory", "dragon", "circus", "shipdead", "everest"):
            if '""' not in noms and cle not in noms:
                problemes.append("ANNONCE : structureDisplayName ne connait plus « %s »." % cle)
        # Les litteraux sont neutralises : on verifie sur la source brute.
        bruts = corps_methode(s5, "String structureDisplayName(String rawName)") or ""
        for cle in ("citadel", "observatory", "dragon", "circus", "shipdead", "everest"):
            if '"%s"' % cle not in bruts:
                problemes.append(
                    "ANNONCE : structureDisplayName ne mappe plus « %s » -- "
                    "le message afficherait la clef technique." % cle)

if s4 is not None:
    c4 = sans_commentaires(s4)
    n4 = len(re.findall(r"announceStructureSpawn\s*\(", c4))
    if n4 < 3:
        problemes.append(
            "ANNONCE : Structures4Procedure ne contient que %d appel(s) d'annonce (3 attendus : "
            "PasteJob pour circus/shipdead, EverestJob pour everest, et le repli rawBlocks vide "
            "qui pose par placeInWorld sans creer de PasteJob). C'est le fichier qui pose circus : "
            "chacune de ses sorties de pose doit annoncer." % n4)

    # Le repli direct (template.placeInWorld) ne cree aucun PasteJob : il doit
    # annoncer lui-meme, sinon la structure apparait sans un mot.
    for m in re.finditer(r"placeInWorld", c4):
        fin = c4.find("return true", m.end())
        if fin < 0 or "announceStructureSpawn" not in c4[m.end():fin]:
            problemes.append(
                "ANNONCE : une pose directe par template.placeInWorld sort sans annoncer. "
                "Ce chemin ne cree pas de PasteJob et n'atteint donc jamais le point de "
                "fin de pose : la structure apparaitrait en silence.")
    if "job.annMin" not in c4 or "job.annMax" not in c4:
        problemes.append(
            "ANNONCE : les bornes annMin/annMax de l'EverestJob ont disparu ; "
            "l'Everest ne peut plus annoncer ses coordonnees.")

# =====================================================================
# VERROU 2
# =====================================================================
bloc = lire(BLOC)
if bloc is not None:
    cb = sans_commentaires(bloc)
    m = re.search(r"SELF_DROP_PERCENT\s*=\s*(\d+)\s*;", cb)
    if m is None:
        problemes.append(
            "DROP : SELF_DROP_PERCENT introuvable dans DeepLuckyBlockBlock -- "
            "le taux de retour de 2 % n'est plus defini.")
    elif int(m.group(1)) != 2:
        problemes.append(
            "DROP : SELF_DROP_PERCENT = %s, or la consigne fixe le taux de retour "
            "d'un Deep Lucky Block a 2 %%." % m.group(1))
    if "SELF_DROP_PERCENT" in cb and cb.count("SELF_DROP_PERCENT") < 2:
        problemes.append(
            "DROP : SELF_DROP_PERCENT est declare mais jamais lu -- "
            "le tirage de 2 % n'est pas branche sur le cassage.")

adv = lire(ADV)
if adv is not None:
    ca = sans_commentaires(adv)

    for methode, etiquette in (("public static void triggerNegativeStreak", "serie negative (0-6 blocs)"),
                               ("public static void onLuckyStreak", "serie positive (0-5 blocs)")):
        corps = corps_methode(ca, methode)
        if corps is None:
            problemes.append("DROP : %s introuvable." % methode)
        elif "dropGlowingLuckyBlocks" in corps:
            problemes.append(
                "DROP : une averse de Deep Lucky Blocks est REAPPARUE dans la %s. "
                "Ces methodes se declenchent a chaque serie, sans verrou d'achievement : "
                "c'est exactement ce qui rendait plus de 20 DLB sur 50 casses." % etiquette)

    paliers = corps_methode(ca, "int getMilestoneRewardCount")
    if paliers is None:
        problemes.append("DROP : getMilestoneRewardCount introuvable.")
    else:
        plafonds = {"10": 1, "50": 2, "1000": 50}
        for cle, attendu in plafonds.items():
            m = re.search(r"case\s+%s\s*->\s*(\d+)\s*;" % cle, paliers)
            if m is None:
                problemes.append("DROP : palier %s absent de getMilestoneRewardCount." % cle)
            elif int(m.group(1)) > attendu:
                problemes.append(
                    "DROP : palier %s rend %s blocs, au-dessus du maximum divise par 2 (%d)."
                    % (cle, m.group(1), attendu))
        for cle, attendu in (("100", 6), ("500", 11)):
            m = re.search(r"case\s+%s\s*->\s*level\.random\.nextInt\((\d+)\)" % cle, paliers)
            if m is None:
                problemes.append("DROP : palier %s n'utilise plus nextInt(...)." % cle)
            elif int(m.group(1)) > attendu:
                problemes.append(
                    "DROP : palier %s tire nextInt(%s), au-dessus de la borne divisee par 2 (%d)."
                    % (cle, m.group(1), attendu))

    m = re.search(r'grantCustomAchievement\(player,\s*""\s*\+\s*structureKey,\s*(\d+)\)', ca)
    if m is not None and int(m.group(1)) > 1:
        problemes.append(
            "DROP : l'achievement de structure rend %s blocs (1 attendu apres division par 2)."
            % m.group(1))
    for nom, attendu in (("all_structures", 5),):
        m = re.search(r'grantCustomAchievement\(player,\s*"%s",\s*(\d+)\)' % nom, adv)
        if m is None:
            problemes.append("DROP : recompense « %s » introuvable." % nom)
        elif int(m.group(1)) > attendu:
            problemes.append(
                "DROP : « %s » rend %s blocs, au-dessus de la valeur divisee par 2 (%d)."
                % (nom, m.group(1), attendu))

# T449b : les recompenses de serie doivent exister (divisees par 2), PAS disparaitre,
# et rester a usage unique. T449 les avait supprimees : « aux achievements, je ne
# recois plus un seul deep lucky block ».
if adv is not None:
    ca2 = sans_commentaires(adv)
    for methode, cle, plafond in (
            ("public static void triggerNegativeStreak", "negative_streak", 3),
            ("public static void onLuckyStreak", "lucky_streak", 2)):
        corps = corps_methode(ca2, methode)
        if corps is None:
            continue
        if "dropOnFirstUnlock" not in corps:
            problemes.append(
                "DROP : %s ne verse plus AUCUN Deep Lucky Block. La consigne est "
                "« divise par 2 les gains via les achievements », pas « supprime-les »."
                % cle)
        else:
            d = corps.find("dropOnFirstUnlock")
            g = corps.find("grantAdvancementFully")
            if g >= 0 and d > g:
                problemes.append(
                    "DROP : dropOnFirstUnlock suit grantAdvancementFully pour %s. "
                    "L'advancement serait deja acquis et la recompense jamais versee." % cle)
    for nom, attendu in (("LUCKY_STREAK_REWARD_MAX", 2), ("NEGATIVE_STREAK_REWARD_MAX", 3)):
        m = re.search(r"%s\s*=\s*(\d+)\s*;" % nom, ca2)
        if m is None:
            problemes.append("DROP : %s introuvable." % nom)
        elif int(m.group(1)) != attendu:
            problemes.append(
                "DROP : %s = %s, attendu %d (borne divisee par 2)."
                % (nom, m.group(1), attendu))
    if "nextInt(maxCount + 1)" not in ca2:
        problemes.append(
            "DROP : le tirage aleatoire a disparu. La consigne est « 0-2 random » et "
            "« 0-3 random », pas un montant fige.")

# T451 : le halo de tete doit etre la signature d'un COMBO, jamais d'un item.
TICK = os.path.join(JAVA, "client", "CosmeticAuraClientTicker.java")
tick = lire(TICK)
if tick is not None:
    ct = sans_commentaires(tick)
    for ligne in ct.splitlines():
        if "F_HALO_CONTINUOUS" not in ligne or "|=" not in ligne:
            continue
        if "mask" not in ligne:
            problemes.append(
                "HALO : F_HALO_CONTINUOUS est pose sans lire le masque de combos -> "
                "%s. Un cosmetique qu'aucun combo n'explique est invisible en infobulle."
                % ligne.strip())
    if "hasContinuousWeaponHalo" in ct:
        problemes.append(
            "HALO : le predicat d'item hasContinuousWeaponHalo est de retour ; "
            "le halo redeviendrait une propriete de l'arme tenue.")
    # T452 : les cosmetiques portes par le joueur se decident sur un combo actif.
    if "surLoot(" in ct:
        problemes.append(
            "COSMETIQUE : surLoot(...) est de retour dans CosmeticAuraClientTicker. "
            "Les drapeaux se decideraient a nouveau sur l'enchantement d'une piece "
            "portee, c'est-a-dire sur un item.")
    lignes = ct.splitlines()
    for n, ligne in enumerate(lignes):
        if "|=" not in ligne or "F_ENCH_" not in ligne:
            continue
        # Remonte la condition, y compris sur plusieurs lignes, MAIS s'arrete des
        # qu'une instruction precedente se termine (« ; » ou « } »). Sans cette borne,
        # un « if (comboActif...) » voisin couvrirait une pose qui n'est pas la sienne.
        contexte = ligne
        k = n - 1
        while k >= 0 and n - k <= 6:
            prec = lignes[k].strip()
            if not prec:
                k -= 1
                continue
            if prec.endswith(";") or prec.endswith("}"):
                break
            contexte = prec + "\n" + contexte
            if "if (" in prec or "if(" in prec:
                break
            k -= 1
        if "comboActif" not in contexte and "mask.has(" not in contexte:
            problemes.append(
                "COSMETIQUE : %s est pose sans combo actif -> %s"
                % (ligne.split("F_ENCH_")[1].split(";")[0].strip(), ligne.strip()))

    # Un chemin absent de tout combo rend son cosmetique DEFINITIVEMENT muet.
    # ATTENTION : sans_commentaires() VIDE aussi les chaines (c'est voulu, cela evite
    # les faux positifs sur du texte). Les noms de chemins seraient donc invisibles :
    # il faut une passe qui retire les commentaires MAIS conserve les chaines.
    code_avec_chaines = sans_commentaires_gardant_chaines(tick)
    REG = os.path.join(JAVA, "enchantments", "EnchantmentComboRegistry.java")
    reg = lire(REG)
    if reg is not None:
        d = reg.index("List<ComboDef> list = new ArrayList<>(210);")
        fin = reg.index("COMBOS = Collections.unmodifiableList(list);")
        connus = set(re.findall(r'"([a-z0-9_]+)"', reg[d:fin]))
        # T453 : plus aucune tolerance. wing_toughness_armor etait la seule
        # exception ; la voilure est desormais rattachee a trois combos reels.
        TOLERES = set()
        for appel in re.findall(r"comboActif\(mask,([^)]*)\)", code_avec_chaines):
            chemins = re.findall(r'"([a-z0-9_]+)"', appel)
            if not chemins:
                continue
            if any(c in connus for c in chemins):
                continue
            if set(chemins) <= TOLERES:
                continue
            problemes.append(
                "COSMETIQUE : aucun des chemins %s n'appartient a un combo ; ce "
                "cosmetique ne pourrait plus JAMAIS s'allumer." % chemins)

    # T453 : la voilure n'a pas d'enchantement porteur ; elle tient a des index.
    if reg is not None:
        m = re.search(r"([^;}]*?)f \|= F_ENCH_WINGED_ENDURANCE", code_avec_chaines)
        idx = sorted(set(re.findall(r"IDX_[A-Z0-9_]+", m.group(1)))) if m else []
        if len(idx) < 3:
            problemes.append(
                "COSMETIQUE : F_ENCH_WINGED_ENDURANCE est accroche a %d combo(s) (%s) ; "
                "il en faut 3, sinon le cosmetique redevient injoignable."
                % (len(idx), ", ".join(idx) or "aucun"))
        for nom in idx:
            if not re.search(r"%s\s*=\s*\d+\s*;" % nom, reg):
                problemes.append(
                    "COSMETIQUE : %s n'est declare dans aucun combo du registre." % nom)

    if "hasLuckyWeaponAura" in ct:
        problemes.append(
            "HALO : CosmeticAuraClientTicker lit hasLuckyWeaponAura (booleen tire au "
            "loot). Les cosmetiques portes par le joueur se decident sur les combos.")

# ==================== T454 : GARNISON DU SOMMET DE L'EVEREST ====================
if s4 is not None:
    c4 = sans_commentaires_gardant_chaines(s4)
    for nom, attendu in (("EVEREST_ZOMBIES_MAX", 21), ("EVEREST_SQUELETTES_MAX", 12),
                         ("EVEREST_PIECES_MIN", 1), ("EVEREST_PIECES_MAX", 5),
                         ("EVEREST_LUCK_MIN", 1), ("EVEREST_LUCK_MAX", 30)):
        m = re.search(r"%s\s*=\s*(\d+)\s*;" % nom, c4)
        if m is None:
            problemes.append("EVEREST : %s introuvable." % nom)
        elif int(m.group(1)) != attendu:
            problemes.append("EVEREST : %s = %s, attendu %d."
                             % (nom, m.group(1), attendu))

    # La garnison doit etre posee au point de FIN de pose, le seul ou la montagne
    # est complete : sinon le scan descendant tombe dans le vide.
    m = re.search(r'announceStructureSpawn\(job\.level, job\.player, "everest".*?\n(.{0,400})',
                  c4, re.S)
    if m is None or "spawnEverestSummitMobs" not in m.group(1):
        problemes.append(
            "EVEREST : spawnEverestSummitMobs n'est pas appele juste apres l'annonce de "
            "fin de pose. Pose plus tot, la surface de la montagne n'existe pas encore.")

    # « SUR la structure, pas dedans » : garanti par un scan DESCENDANT depuis le
    # plafond, puis deux blocs d'air libres. Retirer l'un des trois casse la garantie.
    corps = corps_methode(c4, "private static BlockPos chercheSurfaceEverest")
    if corps is None:
        problemes.append("EVEREST : chercheSurfaceEverest introuvable.")
    else:
        if "Direction.DOWN" not in corps:
            problemes.append(
                "EVEREST : chercheSurfaceEverest ne descend plus depuis le plafond. "
                "C'est ce balayage qui garantit la peau EXTERIEURE et non une cavite.")
        for test, raison in (
                ("getBlockState(pieds).isAir()",
                 "le bloc ou se posent les pieds n'est plus verifie"),
                ("getBlockState(pieds.above()).isAir()",
                 "la place pour la tete n'est plus verifiee")):
            if test not in corps:
                problemes.append(
                    "EVEREST : %s -> le mob peut apparaitre DANS la montagne." % raison)
        if "sol.isAir()" not in corps:
            problemes.append(
                "EVEREST : la colonne entierement vide n'est plus ecartee ; le mob "
                "tomberait du ciel a cote de la montagne.")

# ==================== T456 : HYGIENE DE PUBLICATION ====================
# Le mod part en publication : aucune sortie de diagnostic PASSIVE ne doit subsister.
# Les reponses des commandes op ne comptent pas (il faut les taper pour les voir).
DBG = os.path.join(JAVA, "util", "DebugLog.java")
dbg = lire(DBG)
if dbg is not None:
    cd = sans_commentaires_gardant_chaines(dbg)
    for nom in ("ENABLED", "STRUCTURE", "HEART"):
        m = re.search(r"boolean\s+%s\s*=\s*(\w+)\s*;" % nom, cd)
        if m is None:
            problemes.append("PUBLICATION : DebugLog.%s introuvable." % nom)
        elif m.group(1) != "false":
            problemes.append(
                "PUBLICATION : DebugLog.%s vaut %s. La console doit etre muette par "
                "defaut ; le rallumage passe par -Ddlb.debug=1." % (nom, m.group(1)))

if tick is not None:
    if "DLB-DIAG" in sans_commentaires_gardant_chaines(tick):
        problemes.append(
            "PUBLICATION : le traceur [DLB-DIAG] (T443) ecrit encore dans le chat du "
            "joueur. Il a servi a etablir la cause du halo ; il n'a plus de raison d'etre.")

# Tout System.out/err hors DebugLog doit etre garde par la propriete de debug.
for racine, _, fichiers in os.walk(JAVA):
    for nom in fichiers:
        if not nom.endswith(".java") or nom == "DebugLog.java":
            continue
        chemin = os.path.join(racine, nom)
        src = lire(chemin)
        if src is None:
            continue
        propre = sans_commentaires_gardant_chaines(src)
        if "System.out.print" not in propre and "System.err.print" not in propre:
            continue
        if "dlb.debug" not in propre and "DLB_DEBUG" not in propre:
            problemes.append(
                "PUBLICATION : %s ecrit sur System.out/err sans garde de debug." % nom)

# Les chronos en chat doivent rester derriere l'interrupteur. La garde peut etre
# sur la ligne meme ou sur le « if » qui la precede : il faut donc regarder les deux,
# sans deborder sur l'instruction d'avant (bornee par « ; » ou « } »).
for fichier, src in (("Structures4Procedure.java", s4), ("Structures5Procedure.java", s5)):
    if src is None:
        continue
    lignes = sans_commentaires_gardant_chaines(src).splitlines()
    for n, ligne in enumerate(lignes):
        if "sendSystemMessage" not in ligne:
            continue
        if '"ms' not in ligne and "dur\\u00e9e totale" not in ligne:
            continue
        contexte = ligne
        k = n - 1
        while k >= 0 and n - k <= 4:
            prec = lignes[k].strip()
            if not prec:
                k -= 1
                continue
            if prec.endswith(";") or prec.endswith("}"):
                break
            contexte = prec + "\n" + contexte
            if prec.startswith("if ("):
                break
            k -= 1
        if "DebugLog.ENABLED" not in contexte:
            problemes.append(
                "PUBLICATION : chrono en chat non masque dans %s -> %s"
                % (fichier, ligne.strip()[:110]))

# ==================== T457 : TABLE D'INFUSION DE LUCK ====================
# Consigne : « retire tout ca : Pomme, carotte, pain, patate (crue/cuite),
# boeuf/porc/poulet cuits, baies sucrees, baies lumineuses ».
# Deux risques opposes : (a) un aliment revient dans la table ; (b) un retrait
# trop large emporte les formes DOREES (carotte doree, pomme d'or, pomme d'or
# enchantee), dont les identifiants contiennent ceux des aliments retires.
RECETTE = os.path.join(JAVA, "recipe", "DeepLuckyBlockCraftingRecipe.java")

rec = lire(RECETTE)
if rec is not None:
    corps = corps_methode(sans_commentaires(rec), "private static int getLuckCenti")
    if corps is None:
        problemes.append("LUCK : getLuckCenti introuvable dans DeepLuckyBlockCraftingRecipe.")
    else:
        # (a) les 10 aliments bannis. Le prefixe litteral « Items. » suffit a
        # distinguer APPLE de GOLDEN_APPLE et POTATO de POISONOUS_POTATO.
        BANNIS = ("APPLE", "CARROT", "BREAD", "POTATO", "BAKED_POTATO",
                  "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN",
                  "SWEET_BERRIES", "GLOW_BERRIES")
        for nom in BANNIS:
            if re.search(r"Items\.%s\s*\)" % nom, corps):
                problemes.append(
                    "LUCK : Items.%s est de nouveau un materiau d'infusion. "
                    "Consigne T457 : la nourriture simple a ete retiree de la table." % nom)

        # (b) ce qui DOIT rester, avec sa valeur exacte : formes dorees (que la
        # suppression des aliments ne doit pas emporter) et ancres de l'echelle T350.
        # T458 : la table est en CENTIEMES de luck (100 = +1). C'est la raison
        # d'etre de l'echelle : une table en entiers ne peut pas porter +0,75.
        ATTENDUS = (("GOLD_INGOT", 50), ("GOLD_BLOCK", 500),
                    ("EMERALD", 75), ("EMERALD_BLOCK", 700),
                    ("DIAMOND", 100), ("DIAMOND_BLOCK", 1000),
                    ("GOLDEN_CARROT", 200), ("GOLDEN_APPLE", 600),
                    ("ENCHANTED_GOLDEN_APPLE", 1500), ("NETHERITE_INGOT", 500),
                    ("NETHERITE_BLOCK", 4500), ("NETHER_STAR", 2500))
        for nom, val in ATTENDUS:
            m = re.search(r"Items\.%s\s*\)\s*\)?\s*return\s+(-?\d+)" % nom, corps)
            if m is None:
                problemes.append(
                    "LUCK : Items.%s a disparu de la table d'infusion (attendu %+d). "
                    "Un retrait trop large ?" % (nom, val))
            elif int(m.group(1)) != val:
                problemes.append(
                    "LUCK : Items.%s vaut %s, attendu %+d."
                    % (nom, m.group(1), val))

        # (c) le versant negatif reste entier : un menage ne doit pas le vider.
        MALUS = (("ROTTEN_FLESH", -100), ("SLIME_BALL", -100), ("BONE", -100),
                 ("SPIDER_EYE", -200), ("POISONOUS_POTATO", -300),
                 ("FERMENTED_SPIDER_EYE", -600), ("PUFFERFISH", -600),
                 ("WITHER_SKELETON_SKULL", -1200))
        for nom, val in MALUS:
            m = re.search(r"Items\.%s\s*\)\s*\)?\s*return\s+(-?\d+)" % nom, corps)
            if m is None:
                problemes.append("LUCK : le malus Items.%s a disparu (attendu %+d)." % (nom, val))
            elif int(m.group(1)) != val:
                problemes.append("LUCK : Items.%s vaut %s, attendu %+d." % (nom, m.group(1), val))


    # (d) T458 : l'accumulation DOIT rester en centiemes, avec un arrondi UNIQUE
    # en fin de craft. Si quelqu'un remet un arrondi par materiau, +0,75 redevient
    # +1 et tout le reequilibrage disparait sans qu'aucune valeur n'ait bouge.
    corps_asm = corps_methode(sans_commentaires(rec), "public ItemStack assemble")
    if corps_asm is None:
        problemes.append("LUCK : assemble(...) introuvable.")
    else:
        for attendu in ("simulatedCenti", "toAddCenti", "DEGRESSIVE_THRESHOLD_CENTI"):
            if attendu not in corps_asm:
                problemes.append(
                    "LUCK : assemble() n'utilise plus %s -- l'accumulation en "
                    "centiemes a ete perdue (T458)." % attendu)
        if re.search(r"simulated\s*>=\s*25\b", corps_asm):
            problemes.append(
                "LUCK : seuil degressif compare a 25 au lieu de 2500 centiemes (T458).")
    m_seuil = re.search(r"DEGRESSIVE_THRESHOLD_CENTI\s*=\s*(\d+)\s*;",
                        sans_commentaires(rec))
    if m_seuil is None:
        problemes.append("LUCK : DEGRESSIVE_THRESHOLD_CENTI introuvable.")
    elif int(m_seuil.group(1)) != 2500:
        problemes.append("LUCK : seuil degressif = %s centiemes, attendu 2500."
                         % m_seuil.group(1))

gen = lire(GEN)
if gen is not None:
    cg = sans_commentaires(gen)
    for nom, plafond in (("STREAK_LUCK_BONUS", 2), ("STORM_LUCK_BONUS", 5)):
        m = re.search(r"%s\s*=\s*(\d+)\s*;" % nom, cg)
        if m is None:
            problemes.append("CHANCE : %s introuvable." % nom)
        elif int(m.group(1)) > plafond:
            problemes.append(
                "CHANCE : %s = %s, au-dessus du plafond baisse (%d). Consigne : "
                "« retire les bonus de chance trop boostes, baisse les »."
                % (nom, m.group(1), plafond))

for p in problemes:
    print(p)
sys.exit(1 if problemes else 0)
