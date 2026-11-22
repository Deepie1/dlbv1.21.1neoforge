#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
meteorstompcheck -- UNE CHUTE NE SE MESURE PAS DANS LE TICK.

Le defaut repare en T470, rapporte en jeu : « Meteor Stomp a du mal a se declencher,
une fois sur dix au mieux ». Il n'etait pas aleatoire, il etait structurellement rate.

Le declencheur vivait dans le tick du joueur :

    if (metStomp > 0 && player.fallDistance >= 4.0f && player.onGround()) { ... }

Or le moteur remet fallDistance a zero PENDANT entity.tick(), dans checkFallDamage(),
c'est-a-dire AVANT que EntityTickEvent.Post ne soit diffuse. Le tick ou le joueur touche
le sol, ce test lisait donc toujours une chute de zero bloc. Les rares declenchements
etaient des accidents de synchronisation : bloc a collision partielle, rebond, pas sur
un bord, serveur en retard -- tout ce qui fait passer onGround a vrai sur un tick ou la
remise a zero n'a pas encore eu lieu.

LivingFallEvent est le seul endroit ou la hauteur de chute existe encore : diffuse depuis
causeFallDamage, juste avant la remise a zero, il porte la distance exacte. La regle
demandee -- « toute chute de plus de 6 blocs compte comme une activation » -- n'est
tenable que la.

Ce banc verifie que le declencheur n'est pas revenu dans le tick, et que le seuil est
bien celui demande.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/meteorstompcheck.py [racine]
Sortie :  code 0 si les quatre verrous tiennent, 1 sinon.
"""

import io
import os
import re
import sys

HANDLER = 'src/main/java/deepluckyblock/enchantments/UniversalEnchantmentHandler.java'
ENCHANT = 'deep_lucky_block:meteor_stomp'
SEUIL_DEMANDE = 6.0


def lire(racine, rel):
    chemin = os.path.join(racine, rel)
    if not os.path.exists(chemin):
        return None
    return io.open(chemin, encoding='utf-8').read()


def sans_commentaires(src):
    """Retire blocs et lignes de commentaire, en gardant le nombre de lignes."""
    src = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group(0).count('\n'), src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


def corps_methode(src, signature):
    """Corps d'une methode reperee par un fragment de sa signature, accolades comptees."""
    i = src.find(signature)
    if i < 0:
        return None
    j = src.find('{', i)
    if j < 0:
        return None
    prof = 0
    for k in range(j, len(src)):
        if src[k] == '{':
            prof += 1
        elif src[k] == '}':
            prof -= 1
            if prof == 0:
                return src[j:k + 1]
    return None


def controler(racine):
    echecs = []
    brut = lire(racine, HANDLER)
    if brut is None:
        print('meteorstompcheck : %s introuvable sous %s' % (HANDLER, racine))
        return 1
    src = sans_commentaires(brut)

    # VERROU 1 -- le declencheur est abonne a LivingFallEvent.
    # On exige la SIGNATURE complete, pas le nom nu : un simple import, une mention en
    # commentaire ou une methode homonyme ne doivent pas suffire a faire taire ce banc.
    abonnement = re.search(
        r'@SubscribeEvent\s+public\s+static\s+void\s+(\w+)\s*\(\s*LivingFallEvent\s+\w+\s*\)',
        src)
    if abonnement is None:
        echecs.append('VERROU 1 : aucun @SubscribeEvent sur LivingFallEvent -- la hauteur '
                      'de chute n\'est lue nulle part ou elle existe encore')

    # VERROU 2 -- meteor_stomp est bien lu DANS ce handler, et nulle part dans le tick.
    corps_chute = None
    if abonnement is not None:
        corps_chute = corps_methode(src, 'private static void onMeteorStompFallImpl(')
        if corps_chute is None:
            # handler monobloc : on retombe sur le corps de la methode abonnee elle-meme
            corps_chute = corps_methode(src, 'void %s(LivingFallEvent' % abonnement.group(1))
    if corps_chute is None:
        echecs.append('VERROU 2 : corps du declencheur de chute introuvable')
    elif ENCHANT not in corps_chute:
        echecs.append('VERROU 2 : le declencheur de chute ne lit pas %s' % ENCHANT)

    # Volontairement PAS un decoupage par accolades : ce fichier contient des chaines de
    # format slf4j ou les accolades ne s'equilibrent pas, et un corps_methode() naif s'y
    # arrete trop tot -- il avait laisse passer une reintroduction du test dans le tick
    # lors de la mise a l'epreuve de ce banc. On compte donc les lectures sur le FICHIER
    # entier : l'enchantement ne doit etre lu qu'a un seul endroit, le declencheur de chute.
    lectures = [n for n, ligne in enumerate(src.split('\n'), 1) if ENCHANT in ligne]
    if len(lectures) != 1:
        echecs.append('VERROU 2 : %s est lu %d fois dans %s (une seule lecture attendue, '
                      'dans le declencheur de chute) -- lignes %s'
                      % (ENCHANT, len(lectures), os.path.basename(HANDLER),
                         ', '.join(str(n) for n in lectures)))
    elif corps_chute is not None and ENCHANT not in corps_chute:
        echecs.append('VERROU 2 : l\'unique lecture de %s (ligne %d) est HORS du declencheur '
                      'de chute' % (ENCHANT, lectures[0]))

    # VERROU 3 -- le seuil est celui demande, et il se lit sur la distance de l'evenement.
    m = re.search(r'METEOR_STOMP_MIN_FALL\s*=\s*([0-9]+(?:\.[0-9]+)?)f?\s*;', src)
    if m is None:
        echecs.append('VERROU 3 : constante METEOR_STOMP_MIN_FALL absente -- le seuil de '
                      'chute n\'est plus nomme')
    else:
        valeur = float(m.group(1))
        if valeur > SEUIL_DEMANDE:
            echecs.append('VERROU 3 : seuil de chute a %g blocs, au-dela des %g demandes '
                          '-- des chutes qui doivent compter ne comptent pas'
                          % (valeur, SEUIL_DEMANDE))
    if corps_chute is not None and 'getDistance()' not in corps_chute:
        echecs.append('VERROU 3 : le declencheur n\'utilise pas event.getDistance() -- la '
                      'seule mesure de chute encore valide a cet instant')

    # VERROU 4 -- plus aucun test de chute sur fallDistance pour cet enchantement.
    # Le piege du sous-mot est evite : on cherche la lecture du champ, pas un nom.
    if corps_chute is not None and re.search(r'\.fallDistance\b', corps_chute):
        echecs.append('VERROU 4 : le declencheur relit player.fallDistance -- il est remis '
                      'a zero avant d\'arriver ici')

    if echecs:
        for e in echecs:
            print('ECHEC  %s' % e)
        print('meteorstompcheck : %d verrou(x) rompu(s) -- Meteor Stomp peut redevenir '
              'intermittent' % len(echecs))
        return 1

    print('meteorstompcheck : 4 verrous tiennent -- toute chute de plus de %g blocs est une '
          'activation, lue sur LivingFallEvent' % SEUIL_DEMANDE)
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
