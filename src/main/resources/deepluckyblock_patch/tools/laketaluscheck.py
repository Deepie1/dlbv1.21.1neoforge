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
    # L'ORDRE DEMANDE : clean, PUIS smooth, PUIS paste.
    # Un talus pose apres la structure la raboterait ; pose avant le clear il serait
    # efface. Sa place est entre les deux, et nulle part ailleurs.
    if 'talusAutour(' not in code:
        echecs.append('VERROU 1 : aucun raccord entre le clear et la pose -- le plateau '
                      'resterait borde de la falaise verticale signalee le 23/11')
    else:
        enchaine = re.search(
            r'clearBuildVolume\s*\([^;]*?talusAutour\s*\([^;]*?PASTE_Q\.offer',
            code, re.S)
        if not enchaine:
            echecs.append('VERROU 1 : la sequence clear -> talus -> pose n\'est plus '
                          'enchainee -- l\'ordre exact etait la consigne du 23/11')
    if code.count('talusAutour(') < 2:
        echecs.append('VERROU 1 : talusAutour est defini mais jamais appele -- une '
                      'fonction ecrite n\'est pas une fonction appelee')

    # ------------------------------------------------------------------- VERROU 2
    # MESURER D'ABORD, ECRIRE ENSUITE. C'est tout le sujet.
    for quoi, pourquoi in (
            ('mesurerChunk', 'la passe de releve'),
            ('appliquerChunk', 'la passe d\'ecriture'),
            ('mesureFinie', 'le drapeau qui separe les deux passes')):
        if quoi not in code:
            echecs.append('VERROU 2 : %s absent (%s) -- sans separation stricte, le '
                          'lissage relit ce qu\'il vient d\'ecrire et la tranchee revient'
                          % (quoi, pourquoi))
    # Le drapeau doit AIGUILLER les deux passes, pas seulement exister : les appeler
    # toutes deux dans le meme tick remettrait l'ecriture devant la mesure pour les
    # chunks suivants, donc la retroaction. (Sabotage Z2 : ne mordait pas sans ceci.)
    if not re.search(r'if\s*\(\s*mesureFinie\s*\)\s*appliquerChunk\s*\([^;]*?\)\s*;'
                     r'\s*else\s*mesurerChunk\s*\(', code):
        echecs.append('VERROU 2 : mesureFinie n\'aiguille plus les deux passes -- mesure '
                      'et ecriture pourraient se faire dans le meme tick, ce qui rouvre '
                      'la boucle de retroaction')

    appl = corps(code, 'private void appliquerChunk(')
    if appl is None:
        echecs.append('VERROU 2 : corps de appliquerChunk introuvable')
    else:
        # L'ecriture ne doit JAMAIS re-mesurer la hauteur du terrain : elle doit lire
        # le releve. Un seul getHeight ici et la boucle de retroaction est rouverte.
        if 'getHeight(' in appl:
            echecs.append('VERROU 2 : appliquerChunk re-mesure la hauteur du terrain '
                          'au lieu de lire le releve -- c\'est exactement la boucle de '
                          'retroaction qui a produit une tranchee a chaque run T459-T468')
        if 'solNaturel[' not in appl:
            echecs.append('VERROU 2 : appliquerChunk ne lit pas le releve solNaturel -- '
                          'sur quoi deciderait-il alors ?')

    # ------------------------------------------------------------------- VERROU 3
    # LE FLOU PORTE SUR LE RELEVE, PAS SUR LE MONDE.
    # Flouter le monde serait une passe de lissage de plus, donc la meme retroaction.
    ad = corps(code, 'private void adoucirReleve(')
    if ad is None:
        echecs.append('VERROU 3 : adoucirReleve absent -- la pente reproduirait les '
                      'ruptures du terrain naturel (mesure : 2259 colonnes en falaise '
                      'au lieu de 592)')
    else:
        for interdit in ('level.', 'chunk.', 'setBlock'):
            if interdit in ad:
                echecs.append('VERROU 3 : adoucirReleve touche au monde (%s) -- il ne '
                              'doit operer que sur le tableau releve' % interdit)
    # < 2 occurrences = defini mais jamais appele. Chercher le nom tout court etait
    # satisfait par la DEFINITION elle-meme. (Sabotage Z4 : ne mordait pas sans ceci.)
    if code.count('adoucirReleve(') < 2:
        echecs.append('VERROU 3 : adoucirReleve est defini mais jamais appele -- la pente '
                      'reproduirait les ruptures du terrain naturel')

    # ------------------------------------------------------------------- VERROU 4
    # LA BOITE DE L'OUVRAGE EST INTOUCHABLE.
    # La structure y sera posee entiere ; un talus qui mord dedans la mutile.
    for nom in ('mesurerChunk', 'appliquerChunk'):
        c = corps(code, 'private void %s(' % nom)
        if c is None:
            continue
        if not re.search(r'if\s*\(\s*d\s*<=\s*0\s*\)\s*continue', c):
            echecs.append('VERROU 4 : %s ne protege pas l\'interieur de la boite -- le '
                          'talus raboterait le terrain ou la structure doit etre posee'
                          % nom)

    # ------------------------------------------------------------------- VERROU 5
    # LE RACCORD EST COSMETIQUE : IL NE DOIT JAMAIS EMPECHER LA POSE.
    # Un chunk absent, une exception, un depassement de delai : dans les trois cas la
    # pose doit partir quand meme. Un lac non pose est un bug bien pire qu'un bord brut.
    if 'TALUS_DEADLINE_MS' not in code:
        echecs.append('VERROU 5 : pas de delai maximal sur le talus -- il pourrait '
                      'retenir la pose indefiniment')
    tal = corps(code, 'static void talusAutour(')
    if tal is None or 'catch' not in tal or 'onDone.run()' not in tal:
        echecs.append('VERROU 5 : talusAutour ne rend pas la main en cas d\'echec -- une '
                      'exception dans le raccord annulerait la pose du lac')

    # ------------------------------------------------------------------- VERROU 6
    # LA ZONE TENUE DOIT COUVRIR L'ANNEAU.
    # Sinon les chunks du raccord ne sont pas charges, le talus les saute en silence,
    # et on obtient un lissage a trous -- defaut invisible au banc, visible en jeu.
    tr = re.search(r'ChunkKeeper\.track\s*\(([^;]*?)\);', code, re.S)
    if not tr:
        echecs.append('VERROU 6 : plus aucun ChunkKeeper.track pour le lac')
    else:
        # Les DEUX coins. Elargir le seul coin min laisse la moitie de l'anneau hors de
        # la zone tenue. (Sabotage Z8 : ne mordait pas quand on ne testait qu'un coin.)
        arg = tr.group(1)
        if 'offset(-TALUS_LARGEUR' not in arg:
            echecs.append('VERROU 6 : la zone tenue ne couvre pas les bords nord/ouest de '
                          'l\'anneau -- le raccord y sauterait des pans entiers en silence')
        if 'offset(TALUS_LARGEUR' not in arg:
            echecs.append('VERROU 6 : la zone tenue ne couvre pas les bords sud/est de '
                          'l\'anneau -- le raccord y sauterait des pans entiers en silence')

    # ------------------------------------------------------------------- VERROU 7
    # LE PROFIL DOIT RESTER BORNE ENTRE LE PLATEAU ET LE TERRAIN NATUREL.
    # C'est ce qui interdit mathematiquement la tranchee ET la crevasse : la cible vaut
    # groundY + (naturel - groundY) * k avec k dans [0,1], donc elle est toujours comprise
    # entre les deux. Si ce calcul disparait, la garantie disparait avec lui.
    cb = corps(code, 'private int cible(')
    if cb is None:
        echecs.append('VERROU 7 : la fonction de profil (cible) a disparu')
    else:
        if not re.search(r'u\s*\*\s*u\s*\*\s*\(\s*3(\.0D)?\s*-\s*2(\.0D)?\s*\*\s*u\s*\)', cb):
            echecs.append('VERROU 7 : le profil n\'est plus la courbe tangente aux deux '
                          'bouts -- une interpolation lineaire laisse une arete visible '
                          'au raccord avec le terrain naturel')
        if 'groundY +' not in cb:
            echecs.append('VERROU 7 : la cible n\'est plus ancree sur groundY -- elle '
                          'pourrait passer sous le plateau et recreuser une tranchee')

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('laketaluscheck : %d verrou(x) rompu(s)' % len(echecs))
        return 1

    print('laketaluscheck : 7 verrous tiennent -- sequence clean/smooth/paste respectee, '
          'mesure separee de l\'ecriture, flou sur le releve et non sur le monde, '
          'boite de l\'ouvrage intouchee, pose jamais bloquee par le raccord, '
          'anneau tenu charge, profil borne entre plateau et terrain naturel')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
