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
    elif 'empreinte[i] ? 0' not in td:
        echecs.append('VERROU 3 : la distance n\'est plus mesuree depuis l\'empreinte -- '
                      'le tablier plat entre la forme de l\'ouvrage et son rectangle '
                      'ne serait plus traite')
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
        if td.count('+ 3') != 4 or td.count('+ 4') != 4:
            echecs.append('VERROU 4 : les poids de chanfrein ne sont plus 3 en '
                          'orthogonal et 4 en diagonale, quatre fois chacun (lu : %d et '
                          '%d) -- une autre ponderation ne vaut plus la distance '
                          'euclidienne et les angles a 45 degres reviennent dans les '
                          'coins' % (td.count('+ 3'), td.count('+ 4')))
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
    if prof is not None and 'groundY +' not in prof:
        echecs.append('VERROU 5 : la cible n\'est plus ancree sur groundY -- elle '
                      'pourrait passer sous le plateau et recreuser une tranchee')

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
    # < 2 occurrences = constante declaree mais jamais utilisee. Chercher le nom tout
    # court etait satisfait par sa propre DECLARATION -- troisieme fois que ce piege
    # se referme sur moi. (Sabotage Z12 : ne mordait pas sans ce comptage.)
    if code.count('TALUS_MESURE_BALAYAGES') < 2:
        echecs.append('VERROU 7 : la mesure ne rebalaye plus les chunks absents -- elle '
                      'abandonnerait au premier passage, alors que le keeper les pompe')

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

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('laketaluscheck : %d verrou(x) rompu(s)' % len(echecs))
        return 1

    print('laketaluscheck : 12 verrous tiennent -- releve avant le clear, cible calculee '
          'une fois et jamais pendant l\'ecriture, pente partant de l\'empreinte et non '
          'du rectangle, distance euclidienne par chanfrein, profil franc au contact et '
          'tangent dehors, lissage epingle aux deux bouts, aucune colonne sans cible, '
          'pose jamais bloquee, anneau tenu aux quatre bords, creusement suivant la '
          'forme du lac avec un plancher par colonne jusqu\'au plafond du monde, '
          'colonnes hors ouvrage epargnees, bedrock et terre porteuse intouchees')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
