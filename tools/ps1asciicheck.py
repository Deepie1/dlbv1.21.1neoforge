#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
T495 -- LE SCRIPT DE MISE A JOUR DOIT RESTER LISIBLE PAR WINDOWS POWERSHELL 5.1.

Le 26/11 au soir, METTRE-A-JOUR.ps1 a refuse de s'analyser chez le dev :
« Jeton inattendu "fichiers" », « Le terminateur ' est manquant dans la chaine »,
et une cascade d'erreurs a partir de la ligne 269.

Cause : le fichier etait en UTF-8 SANS BOM et contenait sept tirets cadratins
U+2014 (E2 80 94). Windows PowerShell 5.1 lit un .ps1 sans BOM comme de l'ANSI
(cp1252) : les trois octets deviennent trois caracteres, dont 0x94, le
guillemet fermant courbe. PowerShell accepte les guillemets courbes comme
DELIMITEURS DE CHAINE -- a la ligne 251, le tiret se trouvait a l'interieur
d'une chaine, qu'il a donc refermee en plein milieu. Tout le reste du fichier
etait decale.

Le piege : le dev avait d'abord COLLE le script dans la console, ou le texte
arrive deja decode. Cela avait parfaitement marche. Seule la lecture du
FICHIER echouait. Un script peut donc etre valide au collage et casse a
l'execution.

Verrous : ASCII pur, aucun guillemet courbe, equilibre des chaines, et la
transliteration ne doit pas avoir mange de mots.
"""
import io, os, re, sys

RACINE = os.path.dirname(os.path.abspath(__file__))
if os.path.basename(RACINE) == 'tools':
    RACINE = os.path.dirname(RACINE)

CIBLES = [os.path.join(RACINE, 'deliverables/METTRE-A-JOUR.ps1')]
echecs = []

COURBES = {0x2018: "'", 0x2019: "'", 0x201C: '"', 0x201D: '"', 0x201E: '"',
           0x2013: '-', 0x2014: '--', 0x00AB: '"', 0x00BB: '"', 0x00A0: 'espace'}


def v1_ascii_pur(nom, octets, texte):
    mauvais = [(i, b) for i, b in enumerate(octets) if b > 127]
    if mauvais:
        lig = octets[:mauvais[0][0]].count(b'\n'[0]) + 1
        return ("%d octet(s) non-ASCII, le premier ligne %d (0x%02X). Windows "
                "PowerShell 5.1 lira ce fichier en cp1252 et un seul de ces octets "
                "peut refermer une chaine en plein milieu."
                % (len(mauvais), lig, mauvais[0][1]))
    return None


def v2_pas_de_guillemet_courbe(nom, octets, texte):
    """Meme en ASCII pur c'est impossible, mais le verrou doit survivre a un
    futur passage en UTF-8 avec BOM : il teste le TEXTE, pas les octets."""
    trouves = sorted({ord(c) for c in texte if ord(c) in COURBES})
    if trouves:
        d = ", ".join("U+%04X (ecrire %s)" % (c, COURBES[c]) for c in trouves)
        return ("caractere(s) typographique(s) interdit(s) : %s. PowerShell traite "
                "les guillemets courbes comme des delimiteurs de chaine." % d)
    return None


def v3_bom_coherent(nom, octets, texte):
    """Soit ASCII pur sans BOM, soit UTF-8 AVEC BOM. Jamais UTF-8 sans BOM."""
    bom = octets[:3] == b'\xef\xbb\xbf'
    pur = all(b < 128 for b in octets)
    if not pur and not bom:
        return ("fichier non-ASCII et SANS BOM : c'est exactement la combinaison qui "
                "a casse le script le 26/11. Ajouter un BOM UTF-8 ou revenir a l'ASCII.")
    return None


def v4_chaines_equilibrees(nom, octets, texte):
    """Une ligne de code ne doit pas laisser une chaine ouverte derriere elle.

    On ignore les commentaires et les here-strings. Ce verrou n'est pas un
    analyseur PowerShell : il attrape le cas reel observe -- un nombre impair
    de guillemets sur une ligne de code."""
    dans_here = False
    for n, l in enumerate(texte.split('\n'), 1):
        if re.search(r"@[\"']\s*$", l):
            dans_here = True
            continue
        if dans_here:
            if re.match(r"^\s*[\"']@", l):
                dans_here = False
            continue
        nu = l.lstrip()
        if nu.startswith('#'):
            continue
        code = l.split('#')[0] if l.count('"') % 2 == 0 else l
        if code.count('"') % 2 != 0:
            return ("ligne %d : nombre IMPAIR de guillemets doubles -- une chaine "
                    "reste ouverte.\n           %s" % (n, l.strip()[:72]))
    return None


def v5_transliteration_propre(nom, octets, texte):
    """Remplacer des caracteres ne doit pas avoir colle deux mots ensemble."""
    suspects = []
    for n, l in enumerate(texte.split('\n'), 1):
        for m in re.finditer(r'[a-z]{3,}--[a-z]{3,}', l):
            suspects.append("ligne %d : %s" % (n, m.group(0)))
    if suspects:
        return "mots colles par la transliteration : " + " | ".join(suspects[:3])
    return None


def v6_contrat_intact(nom, octets, texte):
    """Les 7 etapes et la verification finale doivent toujours etre la."""
    # Chercher une sous-chaine ne suffit pas : « Start-TranscriptX » contient
    # « Start-Transcript », et un jeton present dix fois survit a la perte de
    # neuf d'entre elles. On exige donc un APPEL reel, borne par des limites de
    # mot, pour chaque element du contrat.
    exigences = [
        (r'\b1/7\b.*\b2/7\b.*\b3/7\b.*\b4/7\b.*\b5/7\b.*\b6/7\b.*\b7/7\b',
         "les 7 etapes annoncees"),
        (r'function\s+Get-Empreinte\b',            "la fonction Get-Empreinte"),
        (r'Get-Empreinte\s+\$dstJava\b',           "le calcul de l'empreinte APRES installation"),
        (r'Get-Content\s*\(Join-Path\s+\$extrait\s+"EMPREINTE\.txt"\)',
         "la lecture de EMPREINTE.txt DANS le colis"),
        (r'\$apres\.hash\s+-ne\s+\$attendu',      "la comparaison empreinte obtenue / attendue"),
        (r'DLB_sync\.zip"?\s*$|DLB_sync\.zip"',   "le nom du colis"),
        (r'Join-Path\s+\$gradleHome\s+"init\.d"', "l'installation dans init.d"),
        (r'Start-Transcript\s+-Path\b',            "l'ouverture du transcript"),
        (r'Read-Host\b',                           "la pause avant fermeture"),
        (r'Remove-Item\s+-Path\s+\$dstJava\b',     "le remplacement de src\\main\\java"),
    ]
    manquants = [nom for motif, nom in exigences
                 if not re.search(motif, texte, re.S | re.M)]
    if manquants:
        return "le script a perdu : " + " | ".join(manquants)
    return None


VERROUS = [("1 ASCII pur", v1_ascii_pur),
           ("2 aucun guillemet courbe", v2_pas_de_guillemet_courbe),
           ("3 BOM coherent avec le contenu", v3_bom_coherent),
           ("4 chaines equilibrees", v4_chaines_equilibrees),
           ("5 transliteration sans mots colles", v5_transliteration_propre),
           ("6 contrat du script intact", v6_contrat_intact)]


def main():
    for chemin in CIBLES:
        nom = os.path.basename(chemin)
        if not os.path.exists(chemin):
            echecs.append("%s : fichier absent" % nom)
            print("  ECHEC  %s : absent" % nom)
            continue
        octets = io.open(chemin, 'rb').read()
        try:
            texte = octets.decode('utf-8-sig')
        except UnicodeDecodeError:
            texte = octets.decode('cp1252')
        for titre, fn in VERROUS:
            pb = fn(nom, octets, texte)
            if pb:
                echecs.append("%s / %s -> %s" % (nom, titre, pb))
                print("  ECHEC  %s" % titre)
                print("         %s" % pb)
            else:
                print("  ok     %s" % titre)
    print()
    if echecs:
        print("ps1asciicheck : %d ECHEC(S)" % len(echecs))
        sys.exit(1)
    print("ps1asciicheck : %d/%d verrous OK" % (len(VERROUS), len(VERROUS)))


if __name__ == '__main__':
    main()
