#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
devscancheck -- L'OUTIL DE SCAN DOIT RESTER JETABLE, ET SA MESURE DOIT RESTER VRAIE.

DevTerrainScan.java est un outil de developpement. Le dev a pose une condition explicite :
pouvoir le supprimer d'un coup, sans la moindre repercussion. Cela se verifie, et cela se
casse facilement.

Ce banc tient donc deux promesses a la fois.

  PROMESSE 1 -- JETABLE. Si le fichier n'existe pas, ce banc est VERT et se tait. C'est le
  comportement normal apres suppression de l'outil : un banc qui passerait au rouge serait
  lui-meme la repercussion qu'on voulait eviter. Et tant que le fichier existe, aucune
  autre classe du mod ne doit le nommer : une seule reference ailleurs, et la suppression
  casse la compilation.

  PROMESSE 2 -- LA MESURE EST VRAIE. Un outil de mesure se casse en silence : rien ne
  s'arrete dans le jeu quand il ment. Trois facons de le casser :
    - le rendre ACTIF (charger ou generer les chunks qu'il observe), ce qui lui ferait
      montrer un terrain que le pipeline n'avait pas, et couterait 8 a 12 s par chunk a
      froid (mesure T304) sur un serveur deja en retard de centaines de ticks ;
    - MAQUILLER les chunks absents avec une valeur plausible, alors que « verifier si des
      chunks manquent » est precisement la raison d'etre de l'outil ;
    - PERDRE l'alignement des grilles sur les chunks, ce qui rend toute lecture
      geometrique fausse sans que rien ne le signale.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/devscancheck.py [racine]
Sortie :  code 0 si l'outil est absent OU si les huit verrous tiennent, 1 sinon.
"""

import io
import os
import re
import sys

OUTIL = 'src/main/java/deepluckyblock/dev/DevTerrainScan.java'
CLASSE = 'DevTerrainScan'
JAVA = 'src/main/java/deepluckyblock'

# Appels qui chargent, generent ou ATTENDENT un chunk. Interdits dans l'outil.
#   getChunk(        -> getChunk(..., FULL, true) : generation synchrone (T56)
#   SafeSurface.request / forceLoad / setChunkForced -> mise en file de generation (T304)
#   level.getBlockState / level.getHeight -> passent par getChunk : meme piege
ACTIFS = (
    r'\.getChunk\s*\(',
    r'SafeSurface\.request\s*\(',
    r'\bforceLoad\s*\(',
    r'\bsetChunkForced\s*\(',
    r'\blevel\.getBlockState\s*\(',
    r'\blevel\.getHeight\s*\(',
)


def lire(racine, rel):
    chemin = os.path.join(racine, rel)
    if not os.path.exists(chemin):
        return None
    with io.open(chemin, encoding='utf-8') as f:
        return f.read()


def sans_commentaires(src):
    """Retire blocs et lignes de commentaire : une regle ne doit jamais mordre sur la prose."""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'(?m)//.*$', '', src)
    return src


def bloc_equilibre(code, depuis):
    """Retourne le bloc { ... } qui suit `depuis`, accolades appariees.

    Defaut corrige (T491) : le verrou 15 bornait la branche a une fenetre de 2600
    caracteres. La fenetre debordait sur le bloc suivant -- qui capture lui aussi
    4_FIN -- si bien que supprimer la capture de la branche examinee ne declenchait
    rien : le verrou voyait le 4_FIN du voisin. Un verrou qui lit au-dela de ce
    qu'il controle finit toujours par se faire rassurer par le code d'a cote.
    """
    i = code.find('{', depuis)
    if i < 0:
        return ''
    prof, j, n = 0, i, len(code)
    while j < n:
        c = code[j]
        if c == '"':
            j += 1
            while j < n and code[j] != '"':
                j += 2 if code[j] == '\\' else 1
        elif c == '{':
            prof += 1
        elif c == '}':
            prof -= 1
            if prof == 0:
                return code[i:j + 1]
        j += 1
    return code[i:]


def controler(racine='.'):
    src = lire(racine, OUTIL)

    # ------------------------------------------------------------------ PROMESSE 1a
    # Outil absent = etat normal apres suppression. Vert, et on se tait.
    if src is None:
        print('devscancheck : outil de scan absent (supprime) -- rien a verifier, et c\'est '
              'exactement le comportement attendu.')
        return 0

    echecs = []
    code = sans_commentaires(src)

    # ------------------------------------------------------------------ VERROU 1
    # Personne d'autre ne nomme la classe : sans quoi la suppression casse le build.
    referents = []
    for dossier, _, fichiers in os.walk(os.path.join(racine, JAVA)):
        for f in fichiers:
            if not f.endswith('.java'):
                continue
            chemin = os.path.join(dossier, f)
            rel = os.path.relpath(chemin, racine).replace(os.sep, '/')
            if rel == OUTIL:
                continue
            with io.open(chemin, encoding='utf-8') as fh:
                if CLASSE in fh.read():
                    referents.append(rel)
    if referents:
        echecs.append('VERROU 1 : %d fichier(s) nomment %s (%s) -- supprimer l\'outil '
                      'casserait la compilation, or il doit rester jetable'
                      % (len(referents), CLASSE, ', '.join(referents[:4])))

    # ... et l'outil ne vit que dans son propre package.
    autres = []
    dossier_dev = os.path.join(racine, JAVA, 'dev')
    if os.path.isdir(dossier_dev):
        autres = [f for f in os.listdir(dossier_dev) if f.endswith('.java')
                  and f != os.path.basename(OUTIL)]
    if autres:
        echecs.append('VERROU 1 : le package dev contient aussi %s -- l\'outil doit rester '
                      'seul dans son dossier pour rester supprimable d\'un geste'
                      % ', '.join(autres))

    # ------------------------------------------------------------------ VERROU 2
    # Il s'accroche tout seul : aucune edition d'un autre fichier n'est requise.
    # Test sur le CODE et non sur le source : la javadoc de l'outil cite
    # « @EventBusSubscriber » en toutes lettres, et chercher dans les commentaires
    # laissait passer la suppression de l'annotation elle-meme.
    if '@EventBusSubscriber' not in code:
        echecs.append('VERROU 2 : plus de @EventBusSubscriber -- l\'outil devrait alors etre '
                      'branche a la main ailleurs, et ne serait plus detachable')
    if 'RegisterCommandsEvent' not in code:
        echecs.append('VERROU 2 : la commande ne s\'enregistre plus toute seule')

    # ------------------------------------------------------------------ VERROU 3
    # Lecture strictement passive.
    for motif in ACTIFS:
        t = re.search(motif, code)
        if t:
            echecs.append('VERROU 3 : appel actif %s -- il charge, genere ou attend un '
                          'chunk ; la sonde modifierait le terrain qu\'elle mesure'
                          % t.group(0).strip())
    if 'getChunkNow(' not in code:
        echecs.append('VERROU 3 : plus de lecture par getChunkNow -- c\'est la seule qui ne '
                      'declenche jamais de generation')

    # ------------------------------------------------------------------ VERROU 4
    # Un chunk absent n'est JAMAIS remplace par une valeur plausible.
    if 'ABSENT' not in code:
        echecs.append('VERROU 4 : le marqueur ABSENT a disparu -- les chunks manquants '
                      'seraient indiscernables, alors que c\'est l\'objet meme du scan')
    if '"----"' not in code:
        echecs.append('VERROU 4 : les grilles n\'ecrivent plus « ---- » pour un chunk non '
                      'charge -- un trou deviendrait invisible a la lecture')
    # Le relief theorique doit rester une reference a part, jamais un bouche-trou.
    #
    # On COMPTE sur le fichier entier au lieu de borner la branche « chunk non charge »
    # par ses accolades : ce bornage est precisement le piege qui avait rendu une regle
    # silencieuse en T470 (les chaines du code contiennent des accolades, et une branche
    # un peu longue sort de la fenetre). Deux appels sont legitimes et seulement deux :
    # le sol de reference, et le calcul du relief theorique complet.
    #
    # T493 : un TROISIEME appel est desormais legitime -- et un seul : la sonde de
    # CALIBRAGE, qui mesure le cout reel d'une colonne pour decider si le relief tient
    # dans son budget. Elle JETTE son resultat (aucune affectation), donc elle ne peut
    # rien boucher. On ne releve donc pas betement le plafond a 3 : on exige que le
    # troisieme appel soit bien celui-la, et qu'il reste sans valeur de retour utilisee.
    appels = [m for m in re.finditer(r'getBaseHeight\s*\(', code)]
    appels_bruit = len(appels)
    jetes = 0
    for m in appels:
        debut = code.rfind('\n', 0, m.start()) + 1
        tete = code[debut:m.start()]
        # Un resultat EXPLOITE est affecte ou renvoye ; une sonde de calibrage est une
        # instruction nue. C'est l'affectation qui distingue, pas le recepteur.
        if '=' not in tete and 'return' not in tete:
            jetes += 1
    attendus = 2 + jetes
    if appels_bruit > attendus:
        echecs.append('VERROU 4 : %d appels a getBaseHeight pour %d legitime(s) (2 utiles '
                      '+ %d sonde(s) de calibrage a resultat jete) -- un appel de plus sert '
                      'tres probablement a combler les chunks absents avec du relief '
                      'theorique, ce qui masquerait exactement ce que le scan doit montrer'
                      % (appels_bruit, attendus, jetes))
    if appels_bruit - jetes < 2:
        echecs.append('VERROU 4 : %d appel(s) utile(s) a getBaseHeight au lieu de 2 -- la '
                      'grille de reference du relief d\'origine a disparu'
                      % (appels_bruit - jetes))
    if jetes > 1:
        echecs.append('VERROU 4 : %d sondes de calibrage a resultat jete au lieu d\'une '
                      'seule -- au-dela, ce n\'est plus un calibrage' % jetes)

    # ------------------------------------------------------------------ VERROU 5
    # Alignement des grilles sur les chunks : sans lui, toute lecture est fausse.
    if '<< 4' not in code:
        echecs.append('VERROU 5 : plus de bornage en << 4 -- la zone ne serait plus alignee '
                      'sur les frontieres de chunk')
    # le pas des vues doit diviser 16, sinon un caractere chevauche deux chunks
    if '16 % p' not in code:
        echecs.append('VERROU 5 : le pas des vues ne force plus un diviseur de 16 -- un '
                      'caractere pourrait chevaucher deux chunks et brouiller la geometrie')
    # une colonne de grille = 4 caracteres, et un separateur tous les 16
    if "(x & 15) == 0" not in code:
        echecs.append('VERROU 5 : plus de separateur de chunk tous les 16 blocs dans les '
                      'grilles')

    # ------------------------------------------------------------------ VERROU 6
    # Scan complet a CHAQUE etape, et les 3 jalons du pipeline sont bien suivis.
    for jalon in ('1_DEBUT_CLEAR', '2_APRES_CLEAR', '3_APRES_PASTE'):
        if jalon not in code:
            echecs.append('VERROU 6 : le jalon %s n\'est plus capture' % jalon)
    if 'PASTE_Q' not in code:
        echecs.append('VERROU 6 : l\'outil n\'observe plus la file de pose du lac -- il ne '
                      'saurait plus dater la fin du clear ni la fin de la pose')
    if 'zoneBlockBounds' not in code:
        echecs.append('VERROU 6 : l\'outil n\'observe plus la zone epinglee -- il ne saurait '
                      'plus quand le pipeline commence ni quand il finit')

    # ------------------------------------------------------------------ VERROU 7
    # Aucune tache de build ne doit RESTAURER l'outil apres sa suppression : ce serait
    # la repercussion la plus sournoise qui soit, puisqu'elle ne se verrait qu'au
    # prochain gradlew. La tache applyMcreatorPatch recopie une liste fixe de fichiers
    # cadre depuis le miroir ; l'outil ne doit jamais y entrer.
    #
    # On lit le CODE et non la prose : le script explique en commentaire quel outil a
    # remplace l'ancien, et une regle qui mord sur une phrase est un faux positif. Elle
    # ne doit se declencher que si le nom apparait dans une instruction -- typiquement
    # une entree de liste a recopier. Groovy partage la syntaxe // et /* */ de Java.
    gradle = lire(racine, 'src/main/resources/deepluckyblock_patch/tools/'
                          'deep-lucky-block-patch.gradle')
    if gradle and CLASSE in sans_commentaires(gradle):
        echecs.append('VERROU 7 : %s est nomme dans le CODE de deep-lucky-block-patch.gradle '
                      '-- la tache de patch le restaurerait apres suppression' % CLASSE)

    # ------------------------------------------------------------------ VERROU 8
    # UN FICHIER PAR ETAPE, et chaque etape reellement analysee.
    #
    # Deux regressions distinctes sont possibles et toutes deux ont deja eu lieu :
    # retomber sur un fichier monolithique (intransportable), et laisser une section
    # n'analyser QUE la derniere etape tout en pretendant couvrir le scan -- le fichier
    # paraissait complet alors que trois etapes sur quatre n'etaient pas examinees.
    # Les trois familles de donnees sont reunies dans UN fichier numerote par etape ;
    # le verrou 12 verifie les trois sections et l'absence de fichiers annexes.
    if '%02d_%s' not in code:
        echecs.append('VERROU 8 : le scan ne nomme plus un fichier par etape -- on est '
                      'revenu a un fichier unique, que l\'utilisateur ne peut pas envoyer')
    if 'resolve(base + ".txt")' not in code:
        echecs.append('VERROU 8 : le fichier principal de l\'etape n\'est plus ecrit sous '
                      'son nom numerote -- les etapes ne seraient plus separees')
    # On teste l'ECRITURE du fichier, pas la mention de son nom : l'outil cite
    # « 00_SOMMAIRE.txt » dans le texte qu'il imprime pour guider la lecture, et
    # chercher le nom tout court laissait donc passer la suppression du fichier.
    if 'resolve("00_SOMMAIRE' not in code:
        echecs.append('VERROU 8 : le fichier de synthese n\'est plus ECRIT -- les '
                      'comparaisons entre etapes n\'appartiennent a aucun fichier d\'etape '
                      'et seraient perdues')
    if 'Etape e = s.etapes.get(s.etapes.size() - 1);' in code:
        echecs.append('VERROU 8 : une section ne lit que la DERNIERE etape -- elle serait '
                      'recopiee a l\'identique dans chaque fichier d\'etape et mentirait sur '
                      'ce qu\'elle mesure')
    for sec in ('section2ChunksAbsents', 'section4bComposition', 'section5InterChunks',
                'section6Marches', 'tableauParChunk', 'coupesVerticales'):
        m = re.search(r'private static void ' + sec + r'\(([^)]*)\)', code)
        if m is None:
            echecs.append('VERROU 8 : section %s introuvable' % sec)
        elif 'Etape ' not in m.group(1):
            echecs.append('VERROU 8 : %s ne recoit pas l\'etape a mesurer -- elle ne peut '
                          'pas produire une analyse par etape' % sec)

    # ------------------------------------------------------------------ VERROU 9
    # L'outil doit MESURER SANS QU'ON AIT RIEN A TAPER.
    # Panne reellement constatee en jeu : phase partait a DESARME, le dev a lance
    # /dlbtest crimsonlake sans avoir tape /dlbscan, le lac s'est construit pendant
    # cinq minutes, et AUCUN fichier n'est sorti -- sans le moindre avertissement.
    # Un outil de mesure qu'on peut oublier d'allumer ne mesure rien.
    if re.search(r'Phase\s+phase\s*=\s*Phase\.DESARME', code):
        echecs.append('VERROU 9 : le scan demarre DESARME -- il faudrait penser a taper '
                      '/dlbscan AVANT chaque test, et son silence serait indiscernable '
                      'd\'une panne (regression deja subie une fois)')
    if not re.search(r'Phase\s+phase\s*=\s*Phase\.ARME', code):
        echecs.append('VERROU 9 : la phase initiale n\'est plus ARME -- plus rien ne '
                      'garantit qu\'une construction lancee sans preparation soit mesuree')
    # Apres ecriture la veille doit se reprendre, sinon seule la 1re structure est vue.
    # Compter les occurrences ne suffit pas : un seuil chiffre laisse regresser un site
    # sur N sans rien dire (verifie -- retirer 1 des 4 sites passait au travers). On
    # verrouille l'INTENTION : tout endroit qui ecrit puis ferme la session doit rendre
    # la main a la veille.
    fermetures = 0
    for m in re.finditer(r'ecrire\(s,', code):
        bloc = code[m.start():m.start() + 320]
        if 'session = null;' not in bloc:
            continue
        fermetures += 1
        if 'veilleAuto ?' not in bloc:
            echecs.append('VERROU 9 : une fermeture de session ("%s") ne rend pas la main '
                          'a la veille -- apres cette ecriture, la construction suivante '
                          'repasserait inapercue'
                          % bloc.split('ecrire(s,')[1].split(')')[0].strip().strip('"'))
    if fermetures < 5:
        echecs.append('VERROU 9 : %d site(s) de fermeture trouve(s) au lieu des 5 attendus '
                      '(arret manuel, scan manuel, fin de pipeline, plafond d\'etapes, '
                      'silence) -- un chemin de sortie a disparu ou n\'ecrit plus rien'
                      % fermetures)
    # Et l'outil doit s'annoncer : un log au demarrage prouve qu'il est dans la build.
    if 'banniereFaite' not in code:
        echecs.append('VERROU 9 : plus aucune annonce au demarrage -- impossible de '
                      'distinguer un outil absent de la build d\'un outil qui dort')
    # Enfin, une session ne doit jamais rester ouverte sans jamais rien ecrire.
    if 'SILENCE_MAX_MS' not in code:
        echecs.append('VERROU 9 : plus de chien de garde de silence -- un pipeline qui '
                      'abandonne sans relacher la zone emporterait toutes les captures')

    # ----------------------------------------------------------------- VERROU 10
    # LA REDACTION NE DOIT JAMAIS TOURNER SUR LE FIL SERVEUR.
    # Panne reellement constatee en jeu : le relief theorique (~140 000 appels a
    # getBaseHeight) puis la redaction de ~10 Mio etaient faits sur le fil serveur au
    # moment d'ecrire. La partie a gele entierement -- chat compris -- le joueur a du
    # tuer le jeu, et aucun fichier n'est sorti. Tout ce que la redaction lit est deja
    # capture en memoire : rien ne justifie de tenir le fil serveur.
    corps_ecrire = ''
    m = re.search(r'private static void ecrire\(Session s, String cause\) \{', code)
    if m is None:
        echecs.append('VERROU 10 : ecrire(Session, String) introuvable')
    else:
        suite = code[m.end():]
        fin = suite.find('private static void rediger(')
        corps_ecrire = suite if fin < 0 else suite[:fin]

    if corps_ecrire:
        if 'new Thread(' not in corps_ecrire:
            echecs.append('VERROU 10 : ecrire() ne delegue plus a un fil de fond -- la '
                          'redaction gelerait la partie, comme la panne deja subie')
        if 'calculerBruit(' in corps_ecrire:
            echecs.append('VERROU 10 : le relief theorique est calcule depuis ecrire(), '
                          'donc sur le fil serveur : ~140 000 appels de bruit qui figent '
                          'le jeu avant qu\'un seul fichier ne soit ecrit')
        for lourd in ('section9Grilles(', 'coupesVerticales(', 'tableauParChunk('):
            if lourd in corps_ecrire:
                echecs.append('VERROU 10 : %s est appelee depuis ecrire() (fil serveur) '
                              'au lieu du fil de redaction' % lourd.rstrip('('))
        if 'createDirectories' not in corps_ecrire:
            echecs.append('VERROU 10 : le dossier de sortie n\'est plus cree tout de '
                          'suite -- en cas d\'echec le joueur n\'a meme pas un dossier '
                          'ou regarder')

    # Le relief doit demarrer des l'ouverture, pour etre pret quand on redige.
    if 'lancerBruit(' not in code:
        echecs.append('VERROU 10 : le relief theorique n\'est plus lance a l\'ouverture '
                      '-- il serait paye d\'un bloc au moment d\'ecrire')
    # Et il doit etre publie d'un coup, jamais lu a moitie rempli.
    if not re.search(r'volatile int\[\] bruit', code):
        echecs.append('VERROU 10 : le relief theorique n\'est pas volatile -- le fil de '
                      'redaction pourrait lire un tableau a moitie ecrit')
    # Parler au joueur depuis un fil de fond sans repasser par le serveur est une faute.
    m = re.search(r'private static void rediger\(', code)
    if m is None:
        echecs.append('VERROU 10 : rediger(...) introuvable')
    else:
        suite = code[m.end():]
        fin = suite.find('private static void direDepuisFond(')
        corps = suite if fin < 0 else suite[:fin]
        if 'dire(s.level' in corps:
            echecs.append('VERROU 10 : rediger() parle au joueur directement depuis le '
                          'fil de fond -- il faut repasser par serveur.execute')

    # ----------------------------------------------------------------- VERROU 11
    # L'EMPRISE DOIT ETRE SUIVIE, PAS FIGEE A L'OUVERTURE.
    # Panne reellement constatee en jeu (23/11) : le rapport annonce une emprise de
    # 48 x 48 blocs pour un lac de 246 x 284 qui a clere 304 chunks -- 30 fois trop
    # petit. La premiere zone epinglee n'est pas l'emprise de la structure : c'est la
    # sonde de placement (probeMin/probeMax +/- 8, elargie de 8 par preloadBox,
    # arrondie au chunk = 3 x 3 chunks). Le vrai track() arrive ensuite et ETEND la
    # zone. Lire zoneBlockBounds() une seule fois condamne la mesure a viser a cote.
    if 'elargir(' not in code:
        echecs.append('VERROU 11 : plus de suivi de l\'emprise -- la session resterait '
                      'figee sur la premiere zone epinglee, qui est la sonde de '
                      'placement et non l\'emprise de la structure (mesure 30x trop petite)')
    else:
        m = re.search(r'private static boolean elargir\(Session s, int\[\] zone\) \{', code)
        if m is None:
            echecs.append('VERROU 11 : elargir(Session, int[]) introuvable')
        else:
            suite = code[m.end():]
            fin = suite.find('\n    private static ')
            corps = suite if fin < 0 else suite[:fin]
            # Il doit reellement reindexer les etapes deja capturees, pas seulement
            # changer les bornes : sinon les tableaux restent indexes sur l'ancienne grille.
            for attendu, pourquoi in (
                    ('poserGeometrie(', 'la grille n\'est pas reconstruite'),
                    ('e.ySolid =', 'les etapes deja capturees ne sont pas reindexees'),
                    ('e.chunkCharge =', 'l\'etat de chargement des chunks n\'est pas reindexe'),
                    ('lancerBruit(', 'le relief theorique n\'est pas recalcule sur la nouvelle grille')):
                if attendu not in corps:
                    echecs.append('VERROU 11 : elargir() n\'appelle pas %s -- %s'
                                  % (attendu, pourquoi))
        # Et il doit etre branche dans le tick, sinon il ne sert a rien.
        if not re.search(r'if \(zone != null\) elargir\(s, zone\)', code):
            echecs.append('VERROU 11 : elargir() n\'est pas appele a chaque tick depuis la '
                          'boucle de garde -- l\'emprise ne serait jamais relue')
    # Publication perimee : un elargissement relance le calcul du relief ; l'ancien fil
    # ne doit jamais publier un tableau dimensionne pour la grille precedente.
    if 'bruitGen' not in code:
        echecs.append('VERROU 11 : pas de garde de generation sur le relief theorique -- '
                      'apres un elargissement, l\'ancien fil publierait un tableau de la '
                      'mauvaise taille')

    # ----------------------------------------------------------------- VERROU 12
    # LA COUVERTURE DOIT ETRE PROUVEE, PAS SUPPOSEE.
    # Une carte ne vaut rien si la grille qui la porte saute des chunks en silence.
    # Le « continue » muet sur une colonne hors grille est exactement ce qui permet a
    # une grille incomplete de passer pour complete.
    # < 2 occurrences = definie mais jamais appelee. Chercher le nom tout court
    # laissait passer la suppression de l'appel : la definition porte deja le nom.
    if code.count('section0Couverture(') < 2:
        echecs.append('VERROU 12 : la section de couverture est absente ou definie sans '
                      'jamais etre ecrite -- rien ne prouverait qu\'aucun chunk ni aucune '
                      'colonne n\'a ete saute')
    for champ, pourquoi in (
            ('chunksVisites', 'le nombre de chunks parcourus n\'est plus compte'),
            ('colonnesVisitees', 'le nombre de colonnes parcourues n\'est plus compte'),
            ('horsGrille', 'les colonnes tombees hors grille ne sont plus comptees'),
            ('colonnesParChunk', 'le compte de colonnes PAR CHUNK disparait : '
                                 'un chunk a 255 colonnes passerait inapercu')):
        if champ not in code:
            echecs.append('VERROU 12 : compteur %s absent -- %s' % (champ, pourquoi))
    # Les compteurs doivent etre CONFRONTES a la taille attendue, pas seulement affiches.
    if 'COUVERTURE ROMPUE' not in code:
        echecs.append('VERROU 12 : les compteurs ne sont jamais confrontes a la taille '
                      'attendue de la grille -- compter sans comparer ne verifie rien')
    # Les cartes demandees doivent exister ET etre ecrites.
    for appel, quoi in (('carteHauteurs(', 'altitude absolue'),
                        ('carteRelief(', 'relief ombre (les courbes)'),
                        ('carteProfondeur(', 'profondeurs'),
                        ('carteAnomalies(', 'trous nets et falaises'),
                        ('vue3D(', 'vue 3D')):
        if code.count(appel) < 2:
            echecs.append('VERROU 12 : la carte « %s » est definie mais jamais appelee '
                          '(ou absente) -- une section qui n\'est pas ecrite n\'existe pas'
                          % quoi)
    # Un fichier par etape doit garder les trois contenus sous des en-tetes explicites.
    # Le test est borne au redacteur : des mentions ailleurs ne peuvent pas faire passer
    # une section supprimee ou une ancienne sortie en fichiers annexes.
    m = re.search(r'private static void rediger\(Session s, String cause,.*?\n    private static void direDepuisFond\(',
                  code, re.S)
    if m is None:
        echecs.append('VERROU 12 : bloc rediger() introuvable pour verifier le format de sortie')
    else:
        redacteur = m.group(0)
        for label, appel in (("RAPPORT", 'section0Couverture(r, s, e)'),
                             ("CARTES", 'section8Vues(r, s, e, prec)'),
                             ("GRILLES", 'section9Grilles(r, s, e, prec)')):
            if not re.search(r'titre\(r,\s*"' + label + r'"\)\s*;.*?'
                             + re.escape(appel), redacteur, re.S):
                echecs.append('VERROU 12 : section %s absente de la sortie unique par etape'
                              % label)
        if 'resolve(base + "_CARTES.txt")' in redacteur or \
           'resolve(base + "_GRILLES.txt")' in redacteur:
            echecs.append('VERROU 12 : des fichiers CARTES/GRILLES separent encore les '
                          'sections -- le plafond de 20 pieces reste depasse')
        if not re.search(r'log\("\[DLB-SCAN\] termine : "\s*\+\s*'
                         r'ecrits\.size\(\)\s*\+\s*" fichiers', redacteur):
            echecs.append('VERROU 12 : le compteur du journal ne derive pas du nombre de '
                          'fichiers effectivement ecrits')
        if not re.search(r'direDepuisFond\(serveur, s, ecrits\.size\(\)\s*\+'
                         r'\s*" fichiers ecrits', redacteur):
            echecs.append('VERROU 12 : le compteur annonce au joueur ne derive pas du nombre '
                          'de fichiers effectivement ecrits')

    # ----------------------------------------------------------------- VERROU 13
    # NE PAS APPELER « SAUT » CE QUI N'EN EST PAS UN.
    # T478. La section 3 annoncait « chunks d'emprise SAUTES : 153/304 » sur le run du
    # 23/11 et a fait croire au dev que le clear ne faisait que la moitie du travail.
    # Verification sur les grilles du meme run : la boite reelle etait rasee a 68769
    # colonnes sur 68769, soit 100,000 %. Le compteur fautif tenait en une ligne :
    # « si aucune colonne n'a change, alors le chunk a ete saute ». Il confondait le
    # vrai defaut avec deux situations parfaitement normales -- le chunk qui n'etait
    # pas charge a l'etape precedente (on ne sait pas) et celui dont le sol etait deja
    # sous le plancher (rien a couper). Un rapport de mesure qui accuse a tort est pire
    # qu'un rapport muet : il envoie corriger du code qui marche.
    for champ, pourquoi in (
            ('vraiSaut', 'le seul cas qui soit un defaut n\'est plus distingue'),
            ('rienACouper', 'le sol deja sous le plancher serait de nouveau compte comme saut'),
            ('nonComparable', 'le chunk absent a l\'etape precedente serait compte comme saut')):
        if champ not in code:
            echecs.append('VERROU 13 : la section 3 ne classe plus les chunks (%s absent) -- '
                          '%s' % (champ, pourquoi))
    # Le plancher doit etre MESURE sur les colonnes qui ont bouge, pas suppose.
    if 'plancher' not in code or 'planConnu' not in code:
        echecs.append('VERROU 13 : le plancher effectif de l\'operation n\'est plus mesure -- '
                      'sans lui on ne peut pas dire si un chunk avait quelque chose a couper')
    # La boite reellement modifiee doit etre imprimee a cote de l'emprise arrondie au
    # chunk : c'est leur confusion qui a fait prendre 4925 colonnes hors boite pour du
    # travail non fait.
    if 'boite REELLEMENT modifiee' not in code:
        echecs.append('VERROU 13 : la boite reellement modifiee n\'est plus imprimee -- '
                      'elle serait de nouveau confondue avec l\'emprise arrondie au chunk')
    # Et le verdict doit etre ECRIT, pas laisse a deduire d'une liste de chunks.
    if 'AUCUN SAUT' not in code or 'CHUNKS REELLEMENT SAUTES' not in code:
        echecs.append('VERROU 13 : la section 3 n\'ecrit plus de verdict -- le lecteur '
                      'devrait de nouveau interpreter des compteurs bruts')

    # --- VERROU 14 (T480) : le chronometre des captures PENDANT la pose doit etre
    # ARME a la transition "file vide -> file pleine". Laisse a 0, le delai est deja
    # ecoule au premier tick ou la file est non vide : la premiere capture part avant
    # la moindre ecriture et redit mot pour mot l'etape precedente. Constate sur le run
    # TEST2 : P2_PENDANT_POSE a t+13.9 s contre 2_APRES_CLEAR a t+13.2 s, zero colonne
    # d'ecart sur 116 224 mesurees -- une etape entiere de rapport pour rien.
    # On compte les AFFECTATIONS, pas les mentions : une declaration ou un commentaire
    # qui nomme le champ ne l'arme pas.
    armements = code.count('dernierePastePeriodiqueMs = System.currentTimeMillis()')
    if armements < 2:
        echecs.append('VERROU 14 : le chronometre des captures pendant la pose n\'est arme '
                      'qu\'a %d endroit(s) -- il faut l\'armer AUSSI a la transition file '
                      'vide -> file pleine, sinon la premiere capture part a zero ecriture '
                      'et duplique l\'etape precedente' % armements)
    else:
        # Et il faut que l'un de ces armements soit bien DANS la branche de transition,
        # pas deux fois dans la capture periodique.
        i = code.find('dernierPasteQ == 0 && q > 0')
        j = code.find('dernierPasteQ > 0 && q == 0')
        if i < 0 or j < 0 or j < i or \
           'dernierePastePeriodiqueMs = System.currentTimeMillis()' not in code[i:j]:
            echecs.append('VERROU 14 : le chronometre est bien arme deux fois, mais aucun '
                          'armement ne se trouve dans la branche de transition file vide -> '
                          'file pleine : la premiere capture pendant la pose repartira a vide')

    # ---------------------------------------------------------------------------
    # VERROU 15 (T491) -- LA MESURE FINALE NE PART PAS AVANT LA FINITION.
    #
    # Defaut d'origine : la session se fermait dans le tick meme ou ChunkKeeper
    # relachait la zone. Or le lac relache son emprise quand sa FILE DE POSE se
    # vide -- l'habillage du sol, la replantation et la finition viennent apres.
    # L'outil photographiait donc le chantier avant le coup de balai, puis on
    # concluait sur la proprete. Pire : 4_FIN etait carrement supprimee quand
    # elle tombait pres de 3_APRES_PASTE, si bien qu'aucune etape ne montrait
    # jamais l'etat final.
    # On exige un delai de grace explicite entre le relachement et la capture.
    # ---------------------------------------------------------------------------
    if 'APRES_RELACHE_MS' not in code:
        echecs.append('VERROU 15 : plus de delai de grace apres le relachement de la zone '
                      '-- la mesure finale repartirait avant la finition (habillage, '
                      'replantation) et ne montrerait jamais l\'etat reellement livre')
    elif 'relacheeDepuisMs' not in code:
        echecs.append('VERROU 15 : le delai APRES_RELACHE_MS existe mais rien ne date le '
                      'relachement -- il ne peut pas etre attendu')
    else:
        # La capture 4_FIN de la branche « zone relachee » doit etre precedee, dans
        # cette meme branche, de l'attente du delai. On borne la recherche a la
        # branche elle-meme pour ne pas se laisser convaincre par un test situe
        # ailleurs dans le fichier.
        i = code.find('zone == null && s.zoneVue')
        if i < 0:
            echecs.append('VERROU 15 : branche de fermeture sur zone relachee introuvable')
        else:
            branche = bloc_equilibre(code, i)
            jf = branche.find('"4_FIN"')
            if jf < 0:
                echecs.append('VERROU 15 : la branche de fermeture ne capture plus 4_FIN '
                              '-- aucune etape ne mesurerait l\'etat final, exactement le '
                              'trou qui a fait manquer l\'habillage et la replantation')
            else:
                avant = branche[:jf]
                # Il ne suffit pas que les deux NOMS apparaissent : il faut une
                # comparaison effective. Neutraliser le test en « if (false) » laisse
                # les noms en place dans le corps -- et un verrou qui se contente de
                # voir un nom se fait berner par n'importe quel code mort.
                if not re.search(r'relacheeDepuisMs\s*<\s*APRES_RELACHE_MS', avant):
                    echecs.append('VERROU 15 : 4_FIN est capturee sans qu\'aucune '
                                  'comparaison n\'attende reellement APRES_RELACHE_MS '
                                  '-- la finition (habillage, replantation) resterait '
                                  'hors de la mesure finale')
                if not re.search(r'relacheeDepuisMs\s*=\s*System\.currentTimeMillis\(\)', avant):
                    echecs.append('VERROU 15 : l\'instant du relachement n\'est jamais '
                                  'date avant la capture -- le delai ne peut pas courir')
                if 'continue;' not in avant:
                    echecs.append('VERROU 15 : la branche ne rend jamais la main au tick '
                                  'pendant l\'attente -- le delai ne serait pas respecte')

    # ---------------------------------------------------------------------------
    # VERROU 16 (T491) -- UNE SONDE DE PLACEMENT N'EST PAS UN CHANTIER.
    #
    # Mesure du 26/11 : une emprise d'un seul chunk s'est epinglee a 279 blocs du
    # lac, une seconde apres la fermeture de la vraie session, puis s'est
    # relachee sans que rien n'y soit construit. Le scan, re-arme, l'a prise pour
    # un nouveau chantier : SECOND DOSSIER COMPLET, 946 Kio, 240 s de redaction
    # en pleine partie, ou 1_DEBUT_CLEAR et 4_FIN etaient la meme photo d'un
    # terrain intact. Deux dossiers pour un seul essai, dont un faux -- et un
    # faux qui se lit exactement comme une mesure. C'est pire que pas de mesure.
    # On exige que la session sache si une construction a vraiment eu lieu, et
    # qu'elle se ferme SANS ECRIRE dans le cas contraire.
    # ---------------------------------------------------------------------------
    if 'chantierVu' not in code:
        echecs.append('VERROU 16 : rien ne distingue un chantier reel d\'une emprise '
                      'epinglee puis relachee a vide -- une sonde de placement '
                      'produirait un seCond dossier de scan entierement faux '
                      '(deux photos identiques d\'un terrain intact)')
    else:
        # (a) le temoin doit etre pose quand la file de pose est non vide : c'est
        #     la seule preuve qu'une construction a eu lieu dans cette emprise.
        if not re.search(r'q\s*>\s*0\s*\)?\s*s\.chantierVu\s*=\s*true', code) and \
           not re.search(r'if\s*\(\s*q\s*>\s*0\s*\)\s*\{?\s*s\.chantierVu\s*=\s*true', code):
            echecs.append('VERROU 16 : chantierVu n\'est pas arme sur une file de pose non '
                          'vide -- le temoin ne temoigne de rien')
        # (b) la sortie sans chantier doit fermer la session SANS ecrire de dossier.
        k = code.find('!s.chantierVu')
        if k < 0:
            echecs.append('VERROU 16 : chantierVu existe mais n\'est jamais teste a la '
                          'fermeture -- la sonde de placement serait quand meme redigee')
        else:
            sortie = code[k:k + 900]
            fin = sortie.find('return')
            sortie = sortie[:fin if fin > 0 else len(sortie)]
            if 'session = null' not in sortie:
                echecs.append('VERROU 16 : la branche sans chantier ne ferme pas la session')
            if 'ecrire(' in sortie:
                echecs.append('VERROU 16 : la branche sans chantier ECRIT quand meme un '
                              'dossier -- c\'est precisement le second dossier faux du '
                              '26/11 que ce verrou doit empecher')
            if 'veilleAuto ?' not in sortie:
                echecs.append('VERROU 16 : la branche sans chantier ne rend pas la main a '
                              'la veille -- le vrai chantier suivant passerait inapercu')

    # ---------------------------------------------------------------- VERROU 17
    # Le profil est continu par colonne et le rapport expose aussi la neige observee.
    mother = lire(racine, 'src/main/java/deepluckyblock/util/TerrainMother.java') or ''
    mother_code = sans_commentaires(mother)
    snapshot = re.search(r'public static final class SnowSpreadSnapshot\s*\{', mother_code)
    snap_body = bloc_equilibre(mother_code, snapshot.start()) if snapshot else ''
    if 'rateAtBlock(int x, int z)' not in snap_body or 'potential[iz * qw + ix]' not in snap_body:
        echecs.append('VERROU 17 : snapshot non interpole a l echelle colonne/quart')
    getter = re.search(r'public static SnowSpreadSnapshot latestSnowSpreadSnapshot\(\)\s*\{', mother_code)
    fin_mere = re.search(r'public static void end\(\)\s*\{', mother_code)
    corps_getter = bloc_equilibre(mother_code, getter.start()) if getter else ''
    corps_fin_mere = bloc_equilibre(mother_code, fin_mere.start()) if fin_mere else ''
    if ('m.snowSpreadSnapshot' not in corps_getter or 'LAST_SNOW_SPREAD_SNAPSHOT' not in corps_getter or
            'LAST_SNOW_SPREAD_SNAPSHOT = m.snowSpreadSnapshot' not in corps_fin_mere):
        echecs.append('VERROU 17 : profil continu perdu apres la fin de Mother')
    cap = re.search(r'private static void capturer\(Session s, String nom, String detail\)\s*\{', code)
    corps_cap = bloc_equilibre(code, cap.start()) if cap else ''
    if ('latestSnowSpreadSnapshot()' not in corps_cap or 'e.snowSpread = s.snowSpreadSnapshot' not in corps_cap):
        echecs.append('VERROU 17 : capturer() ne fige pas le profil neige')
    grilles = re.search(r'private static void section9Grilles\(StringBuilder r, Session s, Etape e, Etape prec\)\s*\{', code)
    corps_grilles = bloc_equilibre(code, grilles.start()) if grilles else ''
    if 'grilleSnowSpread(r, s, e)' not in corps_grilles or 'grilleSnowObserved(r, s, e)' not in corps_grilles:
        echecs.append('VERROU 17 : grille potentielle ou carte observee absente du rapport')
    observed = re.search(r'private static void grilleSnowObserved\(StringBuilder r, Session s, Etape e\)\s*\{', code)
    observed_body = bloc_equilibre(code, observed.start()) if observed else ''
    if ('1000' not in observed_body or '0000' not in observed_body or 'chunkCharge' not in observed_body
            or 'coucheNeige' not in observed_body):
        echecs.append('VERROU 17 : carte de neige observee colonne par colonne invalide')
    lire_col = re.search(r'private static void lireColonne\(Session s, Etape e, ChunkAccess chunk,', code)
    lire_col_body = bloc_equilibre(code, lire_col.start()) if lire_col else ''
    if ('import net.minecraft.world.level.block.Blocks;' not in code
            or 'st.is(Blocks.SNOW)' not in lire_col_body or 'AIR_PROBE_DEPTH' not in lire_col_body
            or 'e.coucheNeige[i] = coucheNeige' not in lire_col_body):
        echecs.append('VERROU 17 : la couche SNOW sous canopee n est pas observee dans la tranche de surface')

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('devscancheck : %d verrou(x) rompu(s)' % len(echecs))
        return 1

    print('devscancheck : 17 verrous tiennent -- outil isole et supprimable, auto-accroche, '
          'sonde passive, chunks absents jamais maquilles, grilles alignees sur les chunks, '
          '3 jalons suivis, aucune restauration par le build, un fichier par etape avec '
          'RAPPORT/CARTES/GRILLES reunis et compteur reel, '
          'mesure sans armement manuel, redaction hors du fil serveur, '
          'emprise suivie et non figee, couverture prouvee chunk par chunk, '
          'sauts reels distingues des non-sauts, '
          'captures pendant la pose armees a la transition, '
          'mesure finale apres le delai de finition, '
          'sonde de placement jamais prise pour un chantier, '
          'potentiel neige continu, map observee par colonne et snapshot conserve apres fin de Mother')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
