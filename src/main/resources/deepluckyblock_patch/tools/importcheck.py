#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
importcheck -- attrape le « cannot find symbol » du a un import oublie.

javac est absent de l'environnement de travail : typecheck et semcheck ne
lisent QUE le fichier courant et ne savent rien des imports. Resultat, un
appel du genre DeepNbt.isModLoot(stack) ecrit sans « import
deepluckyblock.util.DeepNbt; » passe tous les bancs et ne tombe qu'au
runClient, chez l'utilisateur.

Ce banc cherche les usages de la forme « Machin.quelqueChose » et « new Machin( »
ou Machin commence par une majuscule, puis verifie que Machin est resolvable :
import explicite, import joker, type declare dans le fichier, type du meme
paquet, ou java.lang.

Usage :
    python3 tools/importcheck.py                 # tout src/main/java
    python3 tools/importcheck.py <fichier...>    # ciblé
Sortie : code 1 s'il reste un symbole non resolu.
"""
import os
import re
import sys

# Comme typecheck.py : se lance DEPUIS LA RACINE du depot.
RACINE = os.getcwd()
SRC = os.path.join(RACINE, 'src', 'main', 'java')

JAVA_LANG = {
    'Object', 'String', 'StringBuilder', 'CharSequence', 'Integer', 'Long', 'Short',
    'Byte', 'Float', 'Double', 'Boolean', 'Character', 'Number', 'Math', 'System',
    'Thread', 'Runnable', 'Exception', 'RuntimeException', 'Error', 'Throwable',
    'IllegalArgumentException', 'IllegalStateException', 'NullPointerException',
    'UnsupportedOperationException', 'IndexOutOfBoundsException', 'ClassCastException',
    'ArithmeticException', 'NumberFormatException', 'Class', 'Enum', 'Record',
    'Iterable', 'Comparable', 'Cloneable', 'AutoCloseable', 'Void', 'Deprecated',
    'Override', 'SuppressWarnings', 'FunctionalInterface', 'SafeVarargs', 'Process',
    'StrictMath', 'Package', 'ThreadLocal', 'StackTraceElement', 'Runtime',
    'AssertionError', 'StackOverflowError', 'OutOfMemoryError', 'NoSuchFieldError',
    'NoSuchMethodError', 'LinkageError', 'InterruptedException', 'CloneNotSupportedException',
    'ArrayIndexOutOfBoundsException', 'StringIndexOutOfBoundsException', 'NegativeArraySizeException',
    'ArrayStoreException', 'SecurityException', 'ClassNotFoundException', 'Iterable',
    'Appendable', 'Readable', 'Thread', 'ProcessBuilder', 'Boolean', 'FunctionalInterface',
}

# Les constantes de bloc/type qu'on ecrit en MAJUSCULES ne sont pas des types.
TOUT_MAJUSCULE = re.compile(r'^[A-Z0-9_]+$')

# Types IMBRIQUES HERITES d'une classe mere Minecraft : utilisables sans import
# parce que la classe les herite. On ne peut pas les deduire sans le classpath.
HERITES = {
    'Flag',  # Goal.Flag, dans les sous-classes de Goal
}


def _blanc(m):
    """Meme longueur, memes retours a la ligne : sinon les numeros de ligne glissent."""
    return ''.join('\n' if c == '\n' else ' ' for c in m.group(0))


def sans_bruit(txt):
    """Neutralise commentaires et litteraux SANS changer les offsets."""
    txt = re.sub(r'/\*.*?\*/', _blanc, txt, flags=re.S)
    txt = re.sub(r'//[^\n]*', _blanc, txt)
    txt = re.sub(r'"(?:\\.|[^"\\\n])*"', _blanc, txt)
    txt = re.sub(r"'(?:\\.|[^'\\\n])*'", _blanc, txt)
    return txt


def analyse(chemin):
    brut = open(chemin, encoding='utf-8').read()
    code = sans_bruit(brut)

    paquet = ''
    m = re.search(r'^\s*package\s+([\w.]+)\s*;', code, re.M)
    if m:
        paquet = m.group(1)

    connus = set(JAVA_LANG)
    joker = False
    for m in re.finditer(r'^\s*import\s+(static\s+)?([\w.]+?)(\.\*)?\s*;', code, re.M):
        statique, chemin_imp, etoile = m.group(1), m.group(2), m.group(3)
        if etoile:
            joker = True
            continue
        dernier = chemin_imp.split('.')[-1]
        connus.add(dernier)
        if statique:
            # import static a.b.C.MEMBRE -> C est utilisable aussi
            morceaux = chemin_imp.split('.')
            if len(morceaux) >= 2:
                connus.add(morceaux[-2])

    # types declares dans le fichier (y compris imbriques)
    for m in re.finditer(r'\b(?:class|interface|enum|record|@interface)\s+(\w+)', code):
        connus.add(m.group(1))

    # types du meme paquet : les autres fichiers du meme dossier
    dossier = os.path.dirname(chemin)
    for f in os.listdir(dossier):
        if f.endswith('.java'):
            connus.add(f[:-5])

    # parametres de type generiques <T>, <T extends X>
    for m in re.finditer(r'<\s*([A-Z]\w*)\s*(?:extends[^>]*)?>', code):
        connus.add(m.group(1))

    # VARIABLES a majuscule : MCreator genere « PartDefinition LunettesDuPatron = ... ».
    # Ce sont des variables, pas des types : les compter comme symboles manquants
    # noierait le banc sous le bruit.
    for m in re.finditer(r'(?<![\w.])[\w.]+(?:<[^;=()]*>)?(?:\[\])?\s+([A-Z]\w*)\s*=', code):
        connus.add(m.group(1))
    for m in re.finditer(r'for\s*\(\s*(?:final\s+)?[\w.<>\[\]]+\s+([A-Z]\w*)\s*:', code):
        connus.add(m.group(1))

    connus |= HERITES

    manquants = {}
    # usage statique : Machin.quelqueChose  (pas precede d'un point ni d'un mot)
    for m in re.finditer(r'(?<![\w.$])([A-Z][A-Za-z0-9_]*)\s*\.', code):
        nom = m.group(1)
        if nom in connus or TOUT_MAJUSCULE.match(nom):
            continue
        manquants.setdefault(nom, brut[:m.start()].count('\n') + 1)
    # instanciation : new Machin(
    for m in re.finditer(r'\bnew\s+([A-Z][A-Za-z0-9_]*)\s*[(<]', code):
        nom = m.group(1)
        if nom in connus:
            continue
        manquants.setdefault(nom, brut[:m.start()].count('\n') + 1)

    return manquants, joker


def main():
    cibles = sys.argv[1:]
    if not cibles:
        cibles = []
        for d, _, fs in os.walk(SRC):
            for f in fs:
                if f.endswith('.java'):
                    cibles.append(os.path.join(d, f))
    cibles.sort()

    total = 0
    flous = 0
    for c in cibles:
        if not os.path.isabs(c):
            c = os.path.join(RACINE, c)
        manquants, joker = analyse(c)
        if not manquants:
            continue
        rel = os.path.relpath(c, RACINE)
        if joker:
            # un import joker peut tout couvrir : on signale sans echouer
            flous += 1
            print('?    %s : import joker present, %d symbole(s) non tracable(s) : %s'
                  % (rel, len(manquants), ', '.join(sorted(manquants))))
            continue
        for nom, ligne in sorted(manquants.items(), key=lambda kv: kv[1]):
            print('ERR  %s:%d  « %s » utilise sans import' % (rel, ligne, nom))
            total += 1

    if total:
        print('\nimportcheck : %d symbole(s) non resolu(s) -- le build echouera.' % total)
        return 1
    print('importcheck : %d fichier(s), aucun import manquant%s'
          % (len(cibles), ' (%d avec import joker, non verifiables)' % flous if flous else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
