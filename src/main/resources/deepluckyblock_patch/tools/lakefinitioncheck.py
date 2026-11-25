#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
lakefinitioncheck.py -- verrous de la finition du lac carmin (T488).

REGLE DU DEV (run du 25/11/2026, monde TEST2 (2), centre [130,44,342]) :

  1. « je vois dans les airs des fragments d'anciens terrains [...] tout ce qui est
     entite qui est reste dans les aires (blocs non solides genre snow carpet, herbes,
     fleurs etc [...] des blocs de neige [...] des blocs de powdered snow) qui n'ont pas
     leur place dans le ciel [...] trouve la cause, et regle en preventif aussi pour les
     autres blocs du meme genre qui pourraient bugger de la meme facon, moddes inclus »
  2. « tu n'as pas replante les arbres dans le terrain, et t'as pas repeint ni remis de
     la vegetation sur le sol repeint -- regle ca ! »
  4. « tu as oublie de fixwater aussi, on a une fonction pour ca normalement »
  5. « il reste quelques chunks dans la structure, appartenant a l'ancien terrain »
  6. « si je vais pour casser a la main ce fameux chunk qui n'a pas sa place ici, il se
     remet instantanement comme du rollback »

LES DEUX DEFAUTS TROUVES :

  A. appliquerChunk() bornait son rabotage a sommetVeg[], releve avec la heightmap
     MOTION_BLOCKING. Cette heightmap ignore PAR DEFINITION tout bloc sans collision :
     couche de neige, tapis, herbes, fleurs, neige poudreuse. Le sommet tombait donc
     SOUS eux et le rabotage les laissait flotter -- exactement la liste du reproche 1.
     Correction : WORLD_SURFACE (premier bloc NON AIR), qui englobe la famille entiere,
     blocs moddes compris, sans liste nominative.

  B. clearChunkDirect() et appliquerChunk() ecrivent directement dans les palettes de
     section. Aucun client n'en est informe : le joueur continue d'afficher l'ancien
     terrain la ou le serveur a deja pose de l'air (reproche 5), et quand il casse un de
     ces blocs fantomes, le serveur acquitte sa prediction sans envoyer de changement --
     le client revient alors a l'etat perime qu'il a en cache, ce qui se voit comme un
     rollback instantane (reproche 6). Correction : markChunkModified() a chaque chunk
     ecrit, puis broadcastModifiedChunks() en fin de chantier.

  Et la finition absente : le lac ne passait par aucune des passes de decorateFinish
  (reproches 2 et 4). On rebranche les passes COSMETIQUES seulement -- fixwater, purge
  des couvertures volantes, balayage des flottants -- sans fuseGroundToStructure ni
  enforceGlacisGradient, qui sont la chaine de lissage debranchee en T469.

CE QUE VERIFIENT LES VERROUS :
  V1  le releve du sommet n'utilise plus MOTION_BLOCKING mais WORLD_SURFACE
  V2  les DEUX ecritures directes de palette marquent leur chunk comme modifie
  V3  la diffusion aux clients existe et vient APRES les passes de finition
  V4  fixwater est appele par le lac, via l'acces public ajoute
  V5  la purge des couvertures volantes est appelee sur l'anneau, pas sur la seule emprise
  V6  la finition ne rappelle PAS la chaine de lissage debranchee en T469
  V7  les chunks restent tenus jusqu'a la fin de la finition (release dans le callback)
  V8  le critere de purge reste generique (classes + tags), jamais une liste de blocs
  V9  les arbres sont releves AVANT le creusement, et le releve est vide a chaque chantier
  V10 les arbres sont replantes, et la replantation se re-ancre sur le sol COURANT
  V11 le sol est repeint et revegetalise apres la pose
  V12 l'emprise est protegee pendant l'habillage, puis la protection est levee

USAGE : python3 lakefinitioncheck.py [racine]
"""
import os
import re
import sys

LAC = 'src/main/java/deepluckyblock/procedures/CrimsonLakeStructureSpawnProcedure.java'
PREP = 'src/main/java/deepluckyblock/procedures/StructureTerrainPrep.java'


def sans_commentaires(src):
    """Retire commentaires et chaines en PRESERVANT le nombre de lignes."""
    out = []
    i, n, etat = 0, len(src), None
    while i < n:
        c = src[i]
        if etat == '//':
            if c == '\n':
                etat = None
                out.append(c)
            else:
                out.append(' ')
        elif etat == '/*':
            if c == '*' and i + 1 < n and src[i + 1] == '/':
                etat = None
                out.append('  ')
                i += 2
                continue
            out.append('\n' if c == '\n' else ' ')
        elif etat == '"':
            if c == '\\':
                out.append('  ')
                i += 2
                continue
            if c == '"':
                etat = None
            out.append(' ')
        else:
            if c == '/' and i + 1 < n and src[i + 1] == '/':
                etat = '//'
                out.append('  ')
                i += 2
                continue
            if c == '/' and i + 1 < n and src[i + 1] == '*':
                etat = '/*'
                out.append('  ')
                i += 2
                continue
            if c == '"':
                etat = '"'
                out.append(' ')
            else:
                out.append(c)
        i += 1
    return ''.join(out)


def corps(code, entete):
    """Extrait le corps d'une methode par equilibrage d'accolades."""
    i = code.find(entete)
    if i < 0:
        return None
    j = code.find('{', i)
    if j < 0:
        return None
    prof, k = 0, j
    while k < len(code):
        if code[k] == '{':
            prof += 1
        elif code[k] == '}':
            prof -= 1
            if prof == 0:
                return code[j:k + 1]
        k += 1
    return None


def controler(racine):
    echecs = []
    plac = os.path.join(racine, LAC)
    pprep = os.path.join(racine, PREP)
    for p in (plac, pprep):
        if not os.path.isfile(p):
            print('!! fichier absent : %s' % p)
            return 1
    lac = sans_commentaires(open(plac, encoding='utf-8').read())
    prep = sans_commentaires(open(pprep, encoding='utf-8').read())

    # ---- V1 : le releve du sommet voit les blocs sans collision ----
    # Cherche par ROLE dans tout le fichier : talusMesurer() n'est qu'un passe-plat
    # vers la classe interne, son corps ne contient pas le releve.
    aff = re.search(r'sommetVeg\s*\[\s*i\s*\]\s*=(.{0,220})', lac, re.S)
    if not aff:
        echecs.append('V1 : aucune affectation de sommetVeg[i] trouvee -- le releve du '
                      'sommet de colonne a disparu ; le rabotage ne peut plus se borner.')
    else:
        expr = aff.group(1)
        if 'MOTION_BLOCKING' in expr:
            echecs.append('V1 : sommetVeg[i] est encore releve avec MOTION_BLOCKING. '
                          'Cette heightmap ignore par definition les blocs sans collision '
                          '(neige fine, tapis, herbes, fleurs, neige poudreuse) : ce sont '
                          'exactement les blocs que le dev voit flotter dans les airs.')
        if 'WORLD_SURFACE' not in expr:
            echecs.append('V1 : sommetVeg[i] doit etre releve avec WORLD_SURFACE (premier '
                          'bloc NON AIR) pour couvrir toute la famille, mods compris.')

    # ---- V2 : les deux ecritures directes de palette marquent leur chunk ----
    for nom, entete in (('appliquerChunk', 'private void appliquerChunk'),
                        ('clearChunkDirect', 'private long clearChunkDirect')):
        b = corps(lac, entete)
        if b is None:
            echecs.append('V2 : methode %s introuvable -- une ecriture directe de palette '
                          'a ete supprimee ou renommee.' % nom)
            continue
        if not re.search(r'(getStates\(\)|states)\s*\.\s*set\s*\(', b):
            echecs.append('V2 : %s n ecrit plus en palette ; verrou a reviser.' % nom)
        if 'markChunkModified' not in b:
            echecs.append('V2 : %s ecrit directement en palette sans marquer son chunk '
                          '(markChunkModified). Les clients gardent alors l ancien terrain '
                          'affiche, et casser un bloc fantome le fait revenir comme un '
                          'rollback.' % nom)

    # ---- V3 : la diffusion existe, et apres les passes de finition ----
    iDiff = lac.find('broadcastModifiedChunks')
    if iDiff < 0:
        echecs.append('V3 : le lac ne diffuse jamais les chunks modifies aux clients. '
                      'Sans broadcastModifiedChunks le travail reste invisible cote joueur.')
    else:
        iPurge = lac.find('clearFlyingCover')
        iSweep = lac.find('sweepFloatingNaturalPass')
        for nomp, ip in (('clearFlyingCover', iPurge), ('sweepFloatingNaturalPass', iSweep)):
            if ip >= 0 and ip > iDiff:
                echecs.append('V3 : %s est appele APRES broadcastModifiedChunks ; ses '
                              'ecritures ne seraient jamais diffusees.' % nomp)

    # ---- V4 : fixwater branche et accessible ----
    if 'fixWaterAround' not in lac:
        echecs.append('V4 : le lac n appelle pas fixwater. Le dev : « tu as oublie de '
                      'fixwater aussi, on a une fonction pour ca normalement ».')
    if not re.search(r'public\s+static\s+void\s+fixWaterAround\s*\(', prep):
        echecs.append('V4 : fixWaterAround doit etre public dans StructureTerrainPrep '
                      '(fixWaterNearStructure est prive et donc inatteignable du lac).')
    if 'fixWaterNearStructure' not in (corps(prep, 'public static void fixWaterAround') or ''):
        echecs.append('V4 : fixWaterAround doit deleguer a fixWaterNearStructure, la '
                      'fonction existante -- pas en reecrire une autre.')

    # ---- V5 : purge appliquee a l anneau, pas a la seule emprise ----
    if 'clearFlyingCover' not in lac:
        echecs.append('V5 : la purge des couvertures volantes n est pas appelee par le lac.')
    else:
        app = re.search(r'clearFlyingCover\s*\(([^;]{0,160})\)', lac)
        if app and not re.search(r'\ba[Mm](in|ax)\b|anneau', app.group(1)):
            echecs.append('V5 : clearFlyingCover semble appele sur l emprise seule. Les '
                          'blocs suspendus signales sont sur le talus, donc hors emprise : '
                          'la purge doit porter sur l anneau.')
    if not re.search(r'public\s+static\s+void\s+clearFlyingCover\s*\(', prep):
        echecs.append('V5 : clearFlyingCover doit etre public dans StructureTerrainPrep.')

    # ---- V6 : la chaine de lissage debranchee en T469 ne revient pas ----
    for interdit in ('fuseGroundToStructure', 'enforceGlacisGradient', 'decorateFinish'):
        if interdit in lac:
            echecs.append('V6 : le lac rappelle %s. Cette chaine de lissage a ete '
                          'debranchee en T469 apres cinq rafistolages rates ; la '
                          'rebrancher ramene tranchees et crevasses.' % interdit)

    # ---- V7 : les chunks tenus jusqu a la fin de la finition ----
    # Le fichier compte plusieurs release() sur les chemins d'abandon : un simple
    # « il en existe un plus loin » serait satisfait par eux et ne mordrait sur rien.
    # On verifie donc les deux moities du ROLE, dans la fenetre de la finition.
    rels = [m.start() for m in re.finditer(r'ChunkKeeper\.release', lac)]
    if not rels:
        echecs.append('V7 : plus aucun release de ChunkKeeper -- fuite de chunks tenus.')
    elif iDiff >= 0:
        # (a) le relachement doit suivre la diffusion DANS LE MEME BLOC.
        if not any(iDiff < r < iDiff + 300 for r in rels):
            echecs.append('V7 : aucun ChunkKeeper.release ne suit immediatement la '
                          'diffusion. Le relachement doit clore la finition, sinon les '
                          'chunks restent tenus ou sont lachés trop tot.')
        # (b) rien ne doit relacher les chunks entre le debut de la finition et elle.
        # Prendre la DERNIERE borne avant la diffusion : la premiere du fichier
        # appartient au chemin d'abandon, qui relache legitimement ses chunks.
        bornes = [m.start() for m in re.finditer(r'lakeOceanRelocs = 0;', lac)
                  if m.start() < iDiff]
        iFin = bornes[-1] if bornes else -1
        if iFin >= 0 and any(iFin < r < iDiff for r in rels):
            echecs.append('V7 : un ChunkKeeper.release intervient ENTRE la fin de la pose '
                          'et la diffusion. Les chunks peuvent se decharger pendant la '
                          'finition, qui travaillerait alors sur des chunks absents.')

    # ---- V8 : le critere de purge reste generique ----
    dec = corps(prep, 'private static boolean isSurfaceDecor')
    if dec is None:
        echecs.append('V8 : isSurfaceDecor introuvable -- le critere generique de la '
                      'purge a disparu.')
    else:
        if 'instanceof' not in dec:
            echecs.append('V8 : isSurfaceDecor ne raisonne plus par classe de bloc ; la '
                          'couverture des blocs moddes n est plus garantie.')
        if '.is(net.minecraft.tags.BlockTags' not in dec and 'BlockTags.' not in dec:
            echecs.append('V8 : isSurfaceDecor ne raisonne plus par tag ; le dev a demande '
                          'un correctif preventif « moddes inclus », donc pas une liste.')

    # ---- V9 : releve des arbres avant le creusement, remis a zero par chantier ----
    if 'captureTreesInChunk' not in lac:
        echecs.append('V9 : aucun releve d arbres dans le lac. replantTrees() n aurait '
                      'alors rien a replanter : le dev « tu n as pas replante les arbres ».')
    else:
        mes = corps(lac, 'private void mesurerChunk')
        if mes is None or 'captureTreesInChunk' not in mes:
            echecs.append('V9 : le releve des arbres doit se faire dans mesurerChunk, '
                          'c est-a-dire AVANT le creusement et sur un balayage deja '
                          'budgete. Apres le clear les arbres n existent plus.')
        elif re.search(r'if\s*\(\s*(false|0\s*[=!]=\s*1|1\s*==\s*0)\s*\)[\s\S]{0,160}$',
                       mes[:mes.find('captureTreesInChunk')]):
            # Un verrou de PRESENCE ne verifie pas l'EFFET : « if (false) capture... »
            # laisse le texte en place tout en desactivant le releve.
            echecs.append('V9 : le releve des arbres est desactive par une garde '
                          'toujours fausse. Le texte est la, l effet non.')
        # Le releve doit avoir lieu a la MESURE, jamais a l'application : a ce
        # moment le clear est passe et il n y a plus d arbre a relever.
        app = corps(lac, 'private void appliquerChunk') or ''
        if 'captureTreesInChunk' in app:
            echecs.append('V9 : un releve d arbres a lieu dans appliquerChunk, donc '
                          'APRES le creusement : il ne releverait que du vide.')
    if 'resetCapturedTrees' not in lac:
        echecs.append('V9 : le releve d arbres n est jamais vide ; un chantier '
                      'replanterait les arbres du chantier precedent.')
    if not re.search(r'public\s+static\s+void\s+captureTreesInChunk\s*\(', prep):
        echecs.append('V9 : captureTreesInChunk doit etre public dans StructureTerrainPrep.')
    capt = corps(prep, 'public static void captureTreesInChunk') or ''
    if 'scanTrees' not in capt:
        echecs.append('V9 : captureTreesInChunk doit deleguer a scanTrees, la fonction '
                      'existante -- pas en reecrire une autre.')

    # ---- V10 : replantation, re-ancree sur le sol courant ----
    if 'replantCapturedTrees' not in lac:
        echecs.append('V10 : les arbres releves ne sont jamais replantes.')
    rep = corps(prep, 'public static void replantCapturedTrees') or ''
    if 'replantTrees' not in rep:
        echecs.append('V10 : replantCapturedTrees doit deleguer a replantTrees.')
    job = corps(prep, 'private static final class ReplantJob') or ''
    if 'findNaturalGroundY' not in job:
        echecs.append('V10 : ReplantJob ne cherche plus le sol courant '
                      '(findNaturalGroundY). Sur un terrain abaisse par le talus, les '
                      'arbres seraient replantes a leur ancienne altitude, donc '
                      'suspendus -- precisement le defaut qu on corrige.')

    # ---- V11 : repeinture et revegetalisation ----
    if 'dressAndRevegetate' not in lac:
        echecs.append('V11 : le sol n est ni repeint ni revegetalise. Le dev : « t as '
                      'pas repeint ni remis de la vegetation sur le sol repeint ».')
    dre = corps(prep, 'public static void dressAndRevegetate') or ''
    if 'dressOrLegacy' not in dre:
        echecs.append('V11 : dressAndRevegetate doit deleguer a dressOrLegacy, qui '
                      'habille avec la palette naturelle puis repose la vegetation.')
    iDress = lac.find('dressAndRevegetate')
    iPlant = lac.find('replantCapturedTrees')
    if iDress >= 0 and iPlant >= 0 and iPlant < iDress:
        echecs.append('V11 : les arbres sont replantes AVANT la repeinture du sol ; '
                      'l habillage repasserait sur leurs pieds.')

    # ---- V12 : emprise protegee pendant l habillage, protection levee ensuite ----
    iProt = lac.find('protectFootprint')
    if iProt < 0:
        echecs.append('V12 : l emprise n est pas protegee pendant l habillage ; la '
                      'repeinture passerait par-dessus la structure posee.')
    elif iDress >= 0 and iProt > iDress:
        echecs.append('V12 : protectFootprint est pose APRES l habillage, donc trop tard.')
    iLeve = lac.find('clearFootprintProtection')
    if iLeve < 0:
        echecs.append('V12 : la protection d emprise n est jamais levee ; elle fuirait '
                      'sur le chantier suivant.')
    elif iDiff >= 0 and not (iDiff < iLeve < iDiff + 300):
        # L'ordre du TEXTE ne dit rien ici : les etapes sont des lambdas definies
        # dans l'ordre inverse de leur execution. On verifie donc le ROLE -- la
        # levee doit se faire dans le bloc final, celui qui diffuse aux clients.
        echecs.append('V12 : la protection d emprise n est pas levee dans le bloc final '
                      '(celui de la diffusion). Levee trop tot, l habillage deborderait '
                      'sur la structure ; jamais levee, elle fuirait sur le chantier '
                      'suivant.')

    if echecs:
        for e in echecs:
            print('!! ' + e)
        print('!! lakefinitioncheck : %d probleme(s)' % len(echecs))
        return 1
    print('lakefinitioncheck : OK (12 verrous)')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
