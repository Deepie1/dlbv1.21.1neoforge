#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
subscribecheck.py -- toute @SubscribeEvent porte-t-elle sur un VRAI gestionnaire ?

LE DEFAUT (T488, crash du 25/11 04:17:27, signale par le dev) :

    java.lang.IllegalArgumentException: Method private static int
    deepluckyblock.procedures.CrimsonLakeStructureSpawnProcedure.pomperDiffusion(
    net.minecraft.server.level.ServerLevel) annotated with @SubscribeEvent must
    have only one parameter that is an Event subtype

  J'avais insere une methode utilitaire ENTRE l'annotation @SubscribeEvent et la
  methode qu'elle annotait. L'annotation s'est donc reportee sur l'utilitaire, dont
  le parametre n'est pas un Event : NeoForge refuse de charger le mod et le jeu
  crashe au demarrage. Les quinze bancs Python etaient verts, l'equilibre des
  accolades etait bon, le miroir et le ZIP etaient a jour : RIEN ne l'a vu, parce
  que tous les controles portaient sur le contenu des methodes et aucun sur ce
  qu'une annotation annote reellement.

  Lecon generale : une annotation s'applique a la declaration SUIVANTE. Inserer du
  code sous une annotation la detourne silencieusement. C'est une faute que seule
  une verification de l'appariement annotation/declaration peut attraper, et elle
  coute un crash au demarrage -- la plus chere de toutes.

CE QUE VERIFIE LE BANC, sur tous les .java du mod :
  V1  chaque @SubscribeEvent est suivie d'une declaration de METHODE
  V2  cette methode a EXACTEMENT un parametre
  V3  le type de ce parametre est un Event (son nom simple finit par « Event »
      ou contient « Event. », ex. PlayerEvent.PlayerLoggedOutEvent)
  V4  dans une classe @EventBusSubscriber, cette methode est STATIC
      (NeoForge l'exige ; sinon echec de chargement du mod)

USAGE : python3 subscribecheck.py [racine]
"""
import os
import re
import sys


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


def parametres(code, ouvrante):
    """Contenu des parentheses ouvertes en `ouvrante`, par equilibrage."""
    prof, k = 0, ouvrante
    while k < len(code):
        if code[k] == '(':
            prof += 1
        elif code[k] == ')':
            prof -= 1
            if prof == 0:
                return code[ouvrante + 1:k]
        k += 1
    return None


def decoupe_parametres(txt):
    """Decoupe par virgules de NIVEAU 0 (les generiques en contiennent)."""
    out, prof, cur = [], 0, ''
    for c in txt:
        if c in '<([':
            prof += 1
        elif c in '>)]':
            prof -= 1
        if c == ',' and prof == 0:
            out.append(cur)
            cur = ''
        else:
            cur += c
    if cur.strip():
        out.append(cur)
    return [p.strip() for p in out if p.strip()]


def lister_java(racine):
    for dp, _, fns in os.walk(racine):
        if 'FIXES_MCREATOR' in dp:
            continue          # le miroir est verifie par mirrorcheck
        for fn in fns:
            if fn.endswith('.java'):
                yield os.path.join(dp, fn)


def controler(racine):
    echecs = []
    base = os.path.join(racine, 'src', 'main', 'java')
    if not os.path.isdir(base):
        print('!! arborescence Java introuvable : %s' % base)
        return 1

    fichiers = 0
    annotations = 0
    for chemin in lister_java(base):
        brut = open(chemin, encoding='utf-8', errors='replace').read()
        if '@SubscribeEvent' not in brut:
            continue
        fichiers += 1
        code = sans_commentaires(brut)
        bus = '@EventBusSubscriber' in code
        court = os.path.relpath(chemin, racine)

        for m in re.finditer(r'@SubscribeEvent\b', code):
            annotations += 1
            ligne = code[:m.start()].count('\n') + 1
            reste = code[m.end():]

            # L'annotation peut porter SES PROPRES arguments :
            # @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true).
            # Sans les sauter, on les lirait comme la liste de parametres de la
            # methode et on signalerait « 2 parametres » sur du code parfaitement sain.
            tete_blanc = len(reste) - len(reste.lstrip())
            if reste[tete_blanc:tete_blanc + 1] == '(':
                args_annot = parametres(reste, tete_blanc)
                if args_annot is not None:
                    reste = reste[tete_blanc + len(args_annot) + 2:]

            # Sauter les annotations qui suivent et les espaces.
            pos = 0
            while True:
                suite = reste[pos:]
                blanc = len(suite) - len(suite.lstrip())
                pos += blanc
                if reste[pos:pos + 1] == '@':
                    fin = reste.find('\n', pos)
                    if fin < 0:
                        break
                    pos = fin
                    continue
                break

            tete = reste[pos:pos + 400]
            par = tete.find('(')
            accolade = tete.find('{')
            ptvirg = tete.find(';')
            # --- V1 : c'est bien une methode, pas un champ ni une classe ---
            if par < 0 or (0 <= ptvirg < par) or (0 <= accolade < par):
                echecs.append('%s:%d -- @SubscribeEvent ne precede pas une methode. '
                              'Une annotation porte sur la declaration SUIVANTE : du '
                              'code insere sous elle la detourne silencieusement.'
                              % (court, ligne))
                continue
            signature = tete[:par]
            if re.search(r'\b(class|interface|enum|record)\b', signature):
                echecs.append('%s:%d -- @SubscribeEvent porte sur un type, pas sur une '
                              'methode.' % (court, ligne))
                continue

            args = parametres(tete, par)
            if args is None:
                echecs.append('%s:%d -- signature illisible sous @SubscribeEvent.'
                              % (court, ligne))
                continue
            liste = decoupe_parametres(args)

            # --- V2 : exactement un parametre ---
            if len(liste) != 1:
                echecs.append('%s:%d -- la methode annotee @SubscribeEvent a %d '
                              'parametre(s) ; NeoForge en exige exactement un. '
                              'Le mod refuse de se charger (crash au demarrage).'
                              % (court, ligne, len(liste)))
                continue

            # --- V3 : ce parametre est un Event ---
            typ = liste[0].split('=')[0].strip()
            typ = re.sub(r'\bfinal\b', '', typ).strip()
            typ = typ.split()[0] if typ.split() else typ
            if 'Event' not in typ:
                nom_methode = signature.strip().split()[-1] if signature.strip() else '?'
                echecs.append('%s:%d -- la methode « %s » annotee @SubscribeEvent prend '
                              '« %s », qui n est pas un Event. NeoForge refuse de '
                              'charger le mod : « must have only one parameter that is '
                              'an Event subtype ».' % (court, ligne, nom_methode, typ))
                continue

            # --- V4 : static dans une classe @EventBusSubscriber ---
            if bus and not re.search(r'\bstatic\b', signature):
                echecs.append('%s:%d -- gestionnaire non static dans une classe '
                              '@EventBusSubscriber ; NeoForge l exige static.'
                              % (court, ligne))

    if echecs:
        for e in echecs:
            print('!! ' + e)
        print('!! subscribecheck : %d probleme(s)' % len(echecs))
        return 1
    print('subscribecheck : OK (%d annotations @SubscribeEvent sur %d fichiers)'
          % (annotations, fichiers))
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
