#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
laketaluscheck -- LE RACCORD DU LAC NE DOIT PLUS JAMAIS CREUSER DE TRANCHEE.

Contexte. Le dev a demande le 23/11 : « le tout doit etre parfaitement clean, puis smooth
avec les alentours puis on paste la structure ». La partie « clean » etait deja acquise --
mesure du run TEST2 : le clear rase sa boite a 68769 colonnes sur 68769, soit 100,000 %.
C'est justement cette perfection qui faisait le defaut visible : un plateau parfaitement
plat borde d'une falaise verticale de 34 blocs, les « beaux trous nets plats ».

Le raccord manquait parce qu'il avait ete DEBRANCHE en T469, apres cinq tentatives de
reparation (T459, T463, T464, T465, T467, T468) qui avaient toutes produit soit une
tranchee, soit une crevasse. La cause n'etait pas un reglage trop fort : l'ancien lissage
RELISAIT le terrain qu'il venait d'ecrire. Chaque passe rabotait le resultat de la
precedente et le creux s'auto-entretenait. C'est une boucle de retroaction.

Le talus T478 la rend structurellement impossible en separant strictement deux temps :
il MESURE tout l'anneau dans un tableau, puis il ECRIT en ne consultant plus que ce
tableau. Ce banc verrouille cette separation, et tout ce qui la rendrait caduque.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/laketaluscheck.py [racine]
Sortie :  code 0 si les sept verrous tiennent, 1 sinon.
"""

import io
import os
import re
import sys

LAC = 'src/main/java/deepluckyblock/procedures/CrimsonLakeStructureSpawnProcedure.java'


def lire(racine, rel):
    chemin = os.path.join(racine, rel)
    if not os.path.exists(chemin):
        return None
    with io.open(chemin, encoding='utf-8') as f:
        return f.read()


def sans_commentaires(src):
    """Une regle ne doit jamais mordre sur de la prose : commentaires retires."""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'(?m)//.*$', '', src)
    return src


def corps(code, signature):
    """Renvoie le corps d'une methode, accolades equilibrees. None si introuvable."""
    i = code.find(signature)
    if i < 0:
        return None
    j = code.find('{', i)
    if j < 0:
        return None
    n = 0
    for k in range(j, len(code)):
        if code[k] == '{':
            n += 1
        elif code[k] == '}':
            n -= 1
            if n == 0:
                return code[j:k + 1]
    return None


def controler(racine='.'):
    brut = lire(racine, LAC)
    if brut is None:
        print('ECHEC  laketaluscheck : %s introuvable' % LAC)
        return 1
    code = sans_commentaires(brut)
    echecs = []

    # ------------------------------------------------------------------- VERROU 1
    # L'ORDRE : relever AVANT le clear, etaler APRES, poser en dernier.
    # Le releve doit precede le clear, sinon il mesure un terrain deja efface et le
    # tablier n'a plus aucune altitude naturelle vers laquelle remonter.
    enchaine = re.search(
        r'talusMesurer\s*\([^;]*?clearBuildVolume\s*\([^;]*?talusAppliquer\s*\([^;]*?PASTE_Q\.offer',
        code, re.S)
    if not enchaine:
        echecs.append('VERROU 1 : la sequence releve -> clear -> etalement -> pose n\'est '
                      'plus enchainee dans cet ordre -- un releve pris apres le clear '
                      'mesurerait le plateau rase au lieu du terrain naturel')
    for nom in ('talusMesurer(', 'talusAppliquer('):
        if code.count(nom) < 2:
            echecs.append('VERROU 1 : %s est defini mais jamais appele' % nom)

    # ------------------------------------------------------------------- VERROU 2
    # LA CIBLE EST CALCULEE UNE FOIS, ET JAMAIS RECALCULEE PENDANT L'ECRITURE.
    # C'est l'invariant qui rend la tranchee impossible. Le lissage T459-T468
    # recalculait sa consigne a partir de ce qu'il venait d'ecrire : chaque passe
    # rabotait la precedente.
    appl = corps(code, 'private void appliquerChunk(')
    if appl is None:
        echecs.append('VERROU 2 : corps de appliquerChunk introuvable')
    else:
        if re.search(r'cible\s*\[[^\]]*\]\s*=', appl):
            echecs.append('VERROU 2 : appliquerChunk ECRIT dans cible[] -- la consigne '
                          'serait recalculee pendant l\'ecriture, ce qui rouvre la boucle '
                          'de retroaction qui a produit une tranchee a chaque run T459-T468')
        if 'getHeight(' in appl:
            echecs.append('VERROU 2 : appliquerChunk relit une heightmap -- la hauteur '
                          'voulue doit venir du tableau cible, pas du monde en cours de '
                          'modification')
        if 'cible[i]' not in appl:
            echecs.append('VERROU 2 : appliquerChunk ne lit pas cible[] -- sur quoi '
                          'deciderait-il alors ?')
    prep = corps(code, 'private void preparer(')
    if prep is None:
        echecs.append('VERROU 2 : preparer() absent -- le relief voulu n\'est plus '
                      'construit en une fois, hors de toute ecriture')
    else:
        for etape, pourquoi in (
                ('boucherTrous(', 'un chunk absent laisserait une plaque de terrain brut '
                                  'au milieu du lissage -- un trou sans aucun sens'),
                ('transformeeDistance(', 'sans distance a l\'empreinte il n\'y a pas de pente'),
                ('lisserChamp(', 'sans lissage le relief garde les ruptures du terrain naturel')):
            if etape not in prep:
                echecs.append('VERROU 2 : preparer() n\'appelle plus %s -- %s'
                              % (etape, pourquoi))

    # ------------------------------------------------------------------- VERROU 3
    # LA PENTE PART DE L'EMPREINTE, PAS DE LA BOITE.
    # C'est toute la difference entre le T478 et le T479. Mesure du run TEST2 : en
    # partant de la boite, les 19789 colonnes de tablier plat restaient intactes a
    # 100 % -- le rectangle de coupe etait toujours la.
    if 'empreinte' not in code:
        echecs.append('VERROU 3 : l\'empreinte de l\'ouvrage n\'est plus calculee -- la '
                      'pente repartirait du rectangle englobant et le tablier plat '
                      'resterait entier')
    td = corps(code, 'private int[] transformeeDistance(')
    if td is None:
        echecs.append('VERROU 3 : transformeeDistance absente')
    else:
        # T484 : la graine s'ecrit desormais en bloc (elle pose aussi l'ancre), plus
        # en ternaire. Ce qui compte n'a pas change : l'empreinte vaut 0, le reste INF.
        graine = (re.search(r'empreinte\[i\]\s*\?\s*0', td)
                  or re.search(r'if\s*\(\s*empreinte\[i\]\s*\)\s*\{[^}]*\bd\[i\]\s*=\s*0\b', td))
        if graine is None:
            echecs.append('VERROU 3 : la distance n\'est plus mesuree depuis l\'empreinte '
                          '-- le tablier plat entre la forme de l\'ouvrage et son '
                          'rectangle ne serait plus traite')
        if not re.search(r'd\[i\]\s*=\s*INF|:\s*INF', td):
            echecs.append('VERROU 3 : les colonnes hors empreinte ne partent plus de '
                          'l\'infini -- la distance serait fausse des le premier balayage')
    if appl is not None and 'empreinte[i]' not in appl:
        echecs.append('VERROU 3 : appliquerChunk ne protege plus l\'empreinte -- '
                      'l\'etalement raboterait le terrain ou la structure doit etre posee')

    # ------------------------------------------------------------------- VERROU 4
    # DISTANCE EUCLIDIENNE, PAS CHEBYSHEV.
    # La distance de Chebyshev a un rectangle est constante par carres emboites :
    # elle imprime une arete a 45 degres dans chaque angle. Ce sont les « gros angles
    # visibles ». Le chanfrein 3-4 approche l'euclidienne a 2 % pres.
    if td is not None:
        # Deux balayages x (deux voisins orthogonaux + deux diagonaux) = 4 et 4.
        # Tester la seule PRESENCE des poids laissait passer leur permutation :
        # mettre 4 en orthogonal rend la distance fausse sans rien faire disparaitre.
        # (Sabotage Z7 : ne mordait pas sans ce comptage.)
        # T484 : compter les occurrences etait fragile -- le transport de l'ancre
        # double chaque mention. On verifie maintenant QUI porte quel poids :
        # les quatre voisins orthogonaux a 3, les quatre diagonaux a 4. C'est
        # l'enonce exact de la regle, et il resiste a une reecriture.
        orth = re.findall(r'd\[i (?:- 1|\+ 1|- rw|\+ rw)\]\s*\+\s*(\d+)', td)
        diag = re.findall(r'd\[i (?:- rw - 1|- rw \+ 1|\+ rw \+ 1|\+ rw - 1)\]\s*\+\s*(\d+)', td)
        if len(set(orth)) != 1 or (orth and orth[0] != '3') or len(orth) < 4:
            echecs.append('VERROU 4 : les voisins orthogonaux ne pesent plus 3 '
                          '(lu : %r) -- la distance cesse d\'approcher l\'euclidienne'
                          % (orth,))
        if len(set(diag)) != 1 or (diag and diag[0] != '4') or len(diag) < 4:
            echecs.append('VERROU 4 : les voisins diagonaux ne pesent plus 4 '
                          '(lu : %r) -- les aretes a 45 degres reviennent dans les coins'
                          % (diag,))
        if td.count('for (') < 4:
            echecs.append('VERROU 4 : la transformee de distance n\'a plus ses deux '
                          'balayages -- un seul balayage laisse des distances fausses '
                          'du cote non parcouru')

    # ------------------------------------------------------------------- VERROU 5
    # LE PROFIL : FRANC AU CONTACT, TANGENT A L'EXTERIEUR.
    # Une courbe tangente aux DEUX bouts (smoothstep) laisse une collerette plate
    # autour de l'ouvrage : mesure, 22,2 % de tablier plat restant contre 8,8 %.
    prof = corps(code, 'private void preparer(')
    if prof is not None and not re.search(r'1\.0D\s*-\s*v\s*\*\s*v\s*\*\s*v', prof):
        echecs.append('VERROU 5 : le profil n\'est plus l\'ease-out cubique 1-(1-u)^3 -- '
                      'une courbe tangente aussi du cote de l\'ouvrage recree la '
                      'collerette plate, une courbe non tangente dehors recree une arete')
    # T484 : RETARGETE, PAS SUPPRIME. L'ancien verrou exigeait 'groundY +' dans la
    # cible. Il figeait precisement le defaut signale : la pente rejoignait un
    # plancher CONSTANT, alors que la surface de l'ouvrage descend a Y 59 au bord
    # ouest (le gabarit n'y a aucune marge vide, mesure du 24 nov.). La pente vise
    # desormais l'altitude reelle de l'ouvrage le plus proche. Ce qui reste protege
    # est l'essentiel d'avant : la cible est une INTERPOLATION entre deux altitudes
    # mesurees, donc elle ne peut pas passer sous les deux ni creuser de tranchee.
    if prof is not None:
        if 'ancre[' not in prof:
            echecs.append('VERROU 5 : la cible n\'utilise plus l\'ancre -- retour a un '
                          'plancher constant, et la falaise franche au bord du gabarit')
        if not re.search(r'=\s*a\s*\+\s*\(\s*solNaturel\[i\]\s*-\s*a\s*\)\s*\*\s*k', prof):
            echecs.append('VERROU 5 : la cible n\'interpole plus entre l\'ancre et le sol '
                          'naturel -- elle pourrait sortir de cet intervalle et creuser '
                          'une tranchee')
        if not re.search(r'ancre\[i\]\s*==\s*ABSENT_T\s*\?\s*groundY', prof):
            echecs.append('VERROU 5 : aucune valeur de repli quand l\'ancre manque -- '
                          'une colonne sans ouvrage proche viserait n\'importe quoi')

    # ------------------------------------------------------------------- VERROU 6
    # LES DEUX BOUTS DU LISSAGE SONT EPINGLES.
    # Sans epinglage exterieur le flou deborde de l'anneau et va deplacer, d'un bloc
    # ou deux, un relief situe a cinquante blocs de tout chantier.
    lis = corps(code, 'private void lisserChamp(')
    if lis is None:
        echecs.append('VERROU 6 : lisserChamp absent')
    else:
        if 'empreinte[i]' not in lis:
            echecs.append('VERROU 6 : le lissage ne protege plus l\'ouvrage')
        if 'distance[i] >=' not in lis:
            echecs.append('VERROU 6 : le lissage n\'est plus epingle a sa lisiere '
                          'exterieure -- il deborderait sur du terrain naturel intact')
    if appl is not None and 'distance[i] >=' not in appl:
        echecs.append('VERROU 6 : l\'ecriture ne s\'arrete plus a la lisiere de la rampe '
                      '-- tout l\'anneau serait reecrit, y compris du terrain a ne pas '
                      'toucher')

    # ------------------------------------------------------------------- VERROU 7
    # AUCUNE COLONNE SANS CIBLE, AUCUN TROU.
    bt = corps(code, 'private void boucherTrous(')
    if bt is None:
        echecs.append('VERROU 7 : boucherTrous absent -- un chunk absent au releve '
                      'laisserait une plaque de terrain brut au milieu du lissage')
    elif 'ABSENT_T' not in bt:
        echecs.append('VERROU 7 : boucherTrous ne cherche plus les colonnes non relevees')
    # T486 -- RETARGE. L'ancienne regle comptait les occurrences de
    # TALUS_MESURE_BALAYAGES : un nombre de TOURS fixe. Sur le run du 24 nov. les
    # 12 tours ont ete consommes pendant 10,5 s de gel serveur et le releve a rendu
    # la main avec 60 chunks jamais vus, tous au sud. Le critere juste n'est pas le
    # nombre de tours mais la STAGNATION. On verifie donc les trois roles, pas un nom.
    for fn, quoi in (('private void pasMesureGuarde(', 'le releve'),
                     ('private void pasApplicationGuarde(', 'l\'application')):
        b = corps(code, fn)
        if b is None:
            echecs.append('VERROU 7 : %s est introuvable' % quoi)
            continue
        if 'chunkFait[curseur] = true' not in b:
            echecs.append('VERROU 7 : %s ne marque pas les chunks traites -- sans marque '
                          'il ne peut pas repasser sur ceux qui manquaient' % quoi)
        if 'balayagesSteriles' not in b:
            echecs.append('VERROU 7 : %s n\'abandonne pas sur la STAGNATION mais sur un '
                          'nombre de tours -- un gel serveur consomme les tours et le '
                          'cote balaye en dernier (le sud) reste a nu' % quoi)
        if 'curseur = 0' not in b:
            echecs.append('VERROU 7 : %s ne rebalaye jamais -- il abandonnerait au '
                          'premier passage, alors que le keeper pompe encore' % quoi)
    # Une colonne jamais relevee ne doit pas etre rabotee vers une altitude inventee.
    if 'inventee' not in (bt or ''):
        echecs.append('VERROU 7 : boucherTrous ne marque pas les colonnes inventees -- '
                      'son ultime recours leur donne groundY, une altitude CONSTANTE, '
                      'et la pente taille alors une marche dans du terrain non mesure')
    if 'inventee[i] = true' not in (bt or ''):
        echecs.append('VERROU 7 : boucherTrous declare le marquage mais ne marque '
                      'aucune colonne -- la garde d\'appliquerChunk serait morte')
    # Relever un terrain pas encore charge, c'est relever du vide. Le pipeline doit
    # laisser au keeper le temps de pomper l'anneau avant de commencer.
    lm = corps(code, 'void lancerMesure(')
    az = corps(code, 'private void attendreZone(')
    if lm is None or 'attendreZone()' not in lm:
        echecs.append('VERROU 7 : le releve demarre sans attendre que la zone soit '
                      'chargee -- il balaierait du vide et consommerait ses tours')
    if az is None or 'zoneComplete' not in az:
        echecs.append('VERROU 7 : l\'attente de zone ne consulte pas le keeper')
    if az is not None and 'TALUS_PRECHARGE_MS' not in az:
        echecs.append('VERROU 7 : l\'attente de zone n\'est pas bornee -- un chunk qui '
                      'ne vient jamais retiendrait la pose')
    ap = corps(code, 'private void appliquerChunk(')
    if ap is None or 'inventee[i]' not in ap:
        echecs.append('VERROU 7 : appliquerChunk ecrit sur les colonnes sans releve -- '
                      'mieux vaut du terrain brut qu\'une marche vers un plancher fictif')

    # ------------------------------------------------------------------- VERROU 8
    # LE RACCORD EST COSMETIQUE : IL NE DOIT JAMAIS EMPECHER LA POSE.
    if 'TALUS_DEADLINE_MS' not in code:
        echecs.append('VERROU 8 : pas de delai maximal -- le raccord pourrait retenir '
                      'la pose indefiniment')
    ta = corps(code, 'static void talusAppliquer(')
    if ta is None or 'catch' not in ta or 'onDone.run()' not in ta:
        echecs.append('VERROU 8 : talusAppliquer ne rend pas la main en cas d\'echec -- '
                      'une exception dans le raccord annulerait la pose du lac')
    tm = corps(code, 'static void talusMesurer(')
    if tm is None or 'catch' not in tm or 'accept(null)' not in tm:
        echecs.append('VERROU 8 : talusMesurer ne rend pas la main en cas d\'echec -- '
                      'un releve impossible annulerait la construction entiere')

    # ------------------------------------------------------------------- VERROU 9
    # LA ZONE TENUE COUVRE L'ANNEAU AUX QUATRE BORDS.
    tr = re.search(r'ChunkKeeper\.track\s*\(([^;]*?)\);', code, re.S)
    if not tr:
        echecs.append('VERROU 9 : plus aucun ChunkKeeper.track pour le lac')
    else:
        arg = tr.group(1)
        if 'offset(-TALUS_LARGEUR' not in arg:
            echecs.append('VERROU 9 : la zone tenue ne couvre pas les bords nord/ouest '
                          'de l\'anneau -- le raccord y sauterait des pans entiers')
        if 'offset(TALUS_LARGEUR' not in arg:
            echecs.append('VERROU 9 : la zone tenue ne couvre pas les bords sud/est '
                          'de l\'anneau -- le raccord y sauterait des pans entiers')

    # ------------------------------------------------------------------ VERROU 10
    # T482 : LE CREUSEMENT SUIT LA FORME DU LAC, PAS SON RECTANGLE.
    # Avant, clearBuildVolume recevait un plancher unique (groundY + 1) applique a
    # TOUT le rectangle englobant. Consequences mesurees sur le run du 24/11 :
    # 48,2 % des colonnes de l'ouvrage n'etaient pas creusees du tout (leur terrain
    # etait deja sous ce plancher) et les angles du rectangle, hors rampe du talus,
    # restaient en parois droites. Le creusement doit desormais recevoir un MASQUE
    # par colonne construit sur la liste reellement posee.
    appel = re.search(r'clearBuildVolume\s*\(([^;]*?)\)\s*,?\s*$', code, re.M)
    if 'clearBuildVolume' not in code:
        echecs.append('VERROU 10 : plus aucun appel a clearBuildVolume')
    else:
        seg = code[code.find('clearBuildVolume'):]
        seg = seg[:400]
        if 'groundY + 1' in seg or 'groundY+1' in seg:
            echecs.append('VERROU 10 : le creusement repart d\'un plancher unique '
                          'groundY + 1 -- le bassin ne serait plus creuse et la fosse '
                          'redeviendrait rectangulaire')
        if 'masque' not in seg.lower():
            echecs.append('VERROU 10 : le creusement ne recoit plus de masque par colonne')
    # Le trou et le raccord de terrain doivent parler de LA MEME forme : le masque
    # de creusement et l'empreinte du talus sont construits sur la meme liste de
    # blocs poses. Si l'un des deux change de source, le trou et la pente se
    # decalent -- exactement le defaut des bords droits.
    mc = re.search(r'new\s+MasqueClear\s*\(\s*([A-Za-z_][\w]*)\s*,', code)
    tm = re.search(r'talusMesurer\s*\(\s*[^,]+,\s*[^,]+,\s*[^,]+,\s*[^,]+,\s*([A-Za-z_][\w]*)\s*,', code)
    if mc is None:
        echecs.append('VERROU 10 : le masque de creusement n\'est plus construit')
    elif tm is None:
        echecs.append('VERROU 10 : appel a talusMesurer introuvable')
    elif mc.group(1) != tm.group(1):
        echecs.append('VERROU 10 : le creusement (%s) et le raccord (%s) ne partent '
                      'plus de la meme liste de blocs -- le trou et la pente ne '
                      'decriraient plus la meme forme' % (mc.group(1), tm.group(1)))

    if 'class MasqueClear' not in code:
        echecs.append('VERROU 10 : la classe MasqueClear a disparu')
    else:
        ctor = corps(code, 'MasqueClear(List<StructureTemplate.StructureBlockInfo> blocs')
        if ctor is None:
            echecs.append('VERROU 10 : constructeur de MasqueClear introuvable')
        elif 'bi.pos().getY()' not in ctor or 'ABSENTE' not in ctor:
            echecs.append('VERROU 10 : MasqueClear ne retient pas le bloc le plus bas '
                          'de chaque colonne')
        # Mentionner la hauteur ne suffit pas : c'est le SENS de la comparaison qui
        # decide. Retenir le bloc le plus HAUT laisserait tout le bassin en terre.
        elif not re.search(r'wy\s*<\s*plancher\[\s*i\s*\]', ctor):
            echecs.append('VERROU 10 : MasqueClear ne garde pas le MINIMUM de la '
                          'colonne (comparaison absente ou inversee) -- le creusement '
                          'partirait du sommet et laisserait le bassin plein de terre')

    # ------------------------------------------------------------------ VERROU 11
    # UNE COLONNE HORS OUVRAGE N'EST PAS TOUCHEE, ET LE PLANCHER EST BIEN RELU
    # PAR COLONNE DANS LA BOUCLE D'ECRITURE. Une regle qui se contenterait de
    # verifier la presence de "planCol" serait satisfaite par la seule declaration :
    # on exige l'usage comme BORNE dans la boucle, et le saut sur ABSENTE.
    direct = corps(code, 'private long clearChunkDirect(')
    if direct is None:
        echecs.append('VERROU 11 : clearChunkDirect introuvable')
    else:
        if 'MasqueClear.ABSENTE' not in direct:
            echecs.append('VERROU 11 : clearChunkDirect ne distingue plus les colonnes '
                          'hors ouvrage -- il raserait de nouveau tout le rectangle')
        if not re.search(r'if\s*\(\s*sy\s*\+\s*ly\s*<\s*planCol\[', direct):
            echecs.append('VERROU 11 : la boucle d\'ecriture ne borne plus chaque '
                          'colonne par son propre plancher')
        if 'getMaxBuildHeight' not in direct:
            echecs.append('VERROU 11 : le creusement ne monte plus jusqu\'au plafond '
                          'du monde -- une montagne pourrait rester sur l\'ouvrage')
    repli = corps(code, 'private long clearChunkPerBlock(')
    if repli is None:
        echecs.append('VERROU 11 : clearChunkPerBlock introuvable')
    elif 'MasqueClear.ABSENTE' not in repli:
        echecs.append('VERROU 11 : le chemin de repli ignore le masque -- il creuserait '
                      'le rectangle entier la ou le chemin rapide suit la forme')

    # ------------------------------------------------------------------ VERROU 12
    # LA BEDROCK ET LA TERRE PORTEUSE RESTENT INTOUCHEES.
    if direct is not None:
        if 'Blocks.BEDROCK' not in direct:
            echecs.append('VERROU 12 : la bedrock n\'est plus protegee du creusement')
        if 'getMinBuildHeight' not in direct:
            echecs.append('VERROU 12 : le plancher par colonne n\'est plus borne par '
                          'le fond du monde')

    # ------------------------------------------------------------------ VERROU 13
    # L'ANCRE EST LA SURFACE REELLE DE L'OUVRAGE, ET ELLE VOYAGE.
    # Mesure du 24 nov. : le gabarit a 30 colonnes de marge vide a l'est, 14 au nord,
    # 16 au sud et ZERO a l'ouest. Du cote sans marge son bord tranche le bassin en
    # pleine profondeur ; la marche au contact y atteint 14 blocs contre 2 ailleurs.
    # Trois exigences, car deux ne suffisaient pas :
    #   a) la surface est relevee par colonne, au MAXIMUM des blocs poses ;
    #   b) elle est semee dans la transformee de distance et propagee avec elle ;
    #   c) elle part de origin.getY(), donc en coordonnees du monde.
    ctor = corps(code, 'TalusJob(ServerLevel level, BlockPos min, BlockPos max, int groundY,')
    if ctor is None:
        echecs.append('VERROU 13 : constructeur de TalusJob introuvable')
    else:
        if 'surfaceOuvrage' not in ctor:
            echecs.append('VERROU 13 : la surface de l\'ouvrage n\'est plus relevee -- '
                          'la pente ne saurait plus quelle altitude rejoindre')
        if not re.search(r'ySurf\s*>\s*surfaceOuvrage\[i\]', ctor):
            echecs.append('VERROU 13 : la surface retenue n\'est plus le MAXIMUM des '
                          'blocs poses -- viser le bas de l\'ouvrage ferait plonger la '
                          'pente sous le bassin')
        if not re.search(r'oy\s*=\s*origin\.getY\(\)', ctor) or 'oy + bi.pos().getY()' not in ctor:
            echecs.append('VERROU 13 : la surface n\'est plus ramenee en altitude monde '
                          'par origin.getY() -- elle resterait relative au gabarit')
    # Le champ doit EXISTER, pas seulement etre affecte : un renommage de la
    # declaration laisse le constructeur intact et le banc aveugle.
    if not re.search(r'final\s+int\[\]\s+surfaceOuvrage\s*;', code):
        echecs.append('VERROU 13 : le champ surfaceOuvrage n\'est plus declare')
    if not re.search(r'int\[\]\s+ancre\s*;', code):
        echecs.append('VERROU 13 : le champ ancre n\'est plus declare')
    if td is not None:
        # Une sortie anticipee laisse le second balayage en place dans le texte tout
        # en le rendant mort : l'ancre resterait fausse du cote non parcouru.
        if td.count('return d') != 1:
            echecs.append('VERROU 13 : la transformee de distance ne sort plus en un '
                          'seul point (%d) -- un retour anticipe rendrait le second '
                          'balayage mort et l\'ancre fausse d\'un cote'
                          % td.count('return d'))
        if 'ancre' not in td:
            echecs.append('VERROU 13 : la transformee de distance ne transporte plus '
                          'l\'ancre -- connaitre la distance sans connaitre l\'altitude '
                          'a rejoindre ne sert a rien')
        if 'ancre[i] = surfaceOuvrage[i]' not in td:
            echecs.append('VERROU 13 : l\'ancre n\'est plus semee sur la surface de '
                          'l\'ouvrage -- elle propagerait une valeur sans rapport')
        # L'ancre doit suivre le MEME voisin que la distance, sinon elle vient d'une
        # colonne qui n'est pas la plus proche et la pente vise de travers.
        if len(re.findall(r'\{\s*m\s*=\s*d\[[^\]]+\]\s*\+\s*\d+;\s*a\s*=\s*ancre\[[^\]]+\];\s*\}', td)) < 8:
            echecs.append('VERROU 13 : l\'ancre ne suit plus le voisin qui a fourni la '
                          'distance -- elle viendrait d\'une autre colonne que la plus '
                          'proche')

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('laketaluscheck : %d verrou(x) rompu(s)' % len(echecs))
        return 1

    print('laketaluscheck : 13 verrous tiennent -- releve avant le clear, cible calculee '
          'une fois et jamais pendant l\'ecriture, pente partant de l\'empreinte et non '
          'du rectangle, distance euclidienne par chanfrein, profil franc au contact et '
          'tangent dehors, lissage epingle aux deux bouts, aucune colonne sans cible, '
          'pose jamais bloquee, anneau tenu aux quatre bords, creusement suivant la '
          'forme du lac avec un plancher par colonne jusqu\'au plafond du monde, '
          'colonnes hors ouvrage epargnees, bedrock et terre porteuse intouchees, '
          'pente rejoignant la surface reelle de l\'ouvrage au contact')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
