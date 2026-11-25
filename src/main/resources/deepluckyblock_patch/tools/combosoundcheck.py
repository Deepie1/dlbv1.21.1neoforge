#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
combosoundcheck -- LE SON DE COMBO NE SONNE QU'A LA DECOUVERTE.

LA REGLE, telle qu'elle a ete posee (T487) :
    « probleme pour les combos, tu mets un enorme son a chaque activation, c'est
      mauvais, ca risque de spam ! mets seulement le son a la decouverte du combo
      pour la premiere fois »

LE DEFAUT CORRIGE. handleServerComboTick comparait le masque courant au masque du
tick precedent (prevServerMask) et jouait UI_TOAST_CHALLENGE_COMPLETE ou
AMETHYST_BLOCK_CHIME sur chaque bit nouvellement allume. Or prevServerMask ne vit
qu'en memoire, dans STATES :
  - STATES.clear() le vide des que la table depasse 256 entrees (tous les 511 ticks) ;
  - il repart de zero a chaque reconnexion et a chaque mort ;
  - et le masque lui-meme se rallume des qu'on rechange une piece d'equipement.
Le meme combo sonnait donc en boucle. « Nouveau depuis le tick d'avant » n'est pas
« jamais vu ».

CE QUE VERIFIE CE BANC.
  VERROU 1  Le playSound d'annonce est SOUS une condition de premiere decouverte,
            pas au premier niveau du bloc d'activation.
  VERROU 2  La decouverte est persistee, et sous la sous-balise que NeoForge
            recopie au respawn -- sinon elle est oubliee a chaque mort et le son
            revient : on aurait remplace un spam par un spam plus rare.
  VERROU 3  Le bit est reellement ECRIT (putLongArray) et le tag RATTACHE au parent.
            getCompound() rend un tag neuf et detache quand la cle manque : sans le
            put, tout ce qu'on y ecrit disparait a la sauvegarde, et la fonction
            renverrait « premiere fois » indefiniment.
  VERROU 4  La fonction de marquage repond NON quand le bit est deja pose. Une
            fonction qui renvoie toujours true laisserait le son spammer sans que
            rien d'autre ne change.
  VERROU 5  La banniere, elle, reste affichee a CHAQUE activation : elle est muette,
            c'est le retour visuel du joueur. La corriger en la supprimant serait
            resoudre le probleme en retirant la fonction.

Usage :  python3 src/main/resources/deepluckyblock_patch/tools/combosoundcheck.py [racine]
Sortie :  code 0 si le son ne peut plus spammer, 1 sinon.
"""

import io
import os
import re
import sys

COMBOS = os.path.join('src', 'main', 'java', 'deepluckyblock', 'enchantments',
                      'EnchantmentComboHandler.java')

# Les deux sons d'ANNONCE de combo. Les autres playSound du fichier sont des effets
# de gameplay (enclume, explosion, teleportation) et ne sont pas concernes.
SONS = ('UI_TOAST_CHALLENGE_COMPLETE', 'AMETHYST_BLOCK_CHIME')


def sans_commentaires(src):
    """Retire blocs et lignes de commentaire, en gardant le nombre de lignes."""
    src = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group(0).count('\n'), src, flags=re.S)
    return re.sub(r'//[^\n]*', '', src)


def corps(code, entete):
    """Corps d'une methode reperee par son entete, accolades equilibrees."""
    i = code.find(entete)
    if i < 0:
        return None
    j = code.find('{', i)
    if j < 0:
        return None
    prof = 0
    for k in range(j, len(code)):
        if code[k] == '{':
            prof += 1
        elif code[k] == '}':
            prof -= 1
            if prof == 0:
                return code[j:k + 1]
    return None


def controler(racine):
    chemin = os.path.join(racine, COMBOS)
    if not os.path.isfile(chemin):
        print('combosoundcheck : fichier introuvable -- %s' % chemin)
        return 1
    brut = io.open(chemin, encoding='utf-8').read()
    code = sans_commentaires(brut)
    echecs = []

    bloc = corps(code, 'private static void handleServerComboTick(')
    if bloc is None:
        echecs.append('handleServerComboTick est introuvable -- le banc ne verifie rien')
        bloc = ''

    # ---------------------------------------------------------------- VERROU 1
    # Le son d'annonce doit etre garde par la premiere decouverte.
    for son in SONS:
        if son not in bloc:
            continue
        # On isole l'instruction playSound qui porte ce son, puis on remonte pour
        # verifier qu'un « if (marquerComboDecouvert( » la domine.
        pos = bloc.index(son)
        avant = bloc[max(0, pos - 900):pos]
        if 'marquerComboDecouvert(' not in avant:
            echecs.append(
                'VERROU 1 : le son %s est joue sans condition de premiere decouverte '
                '-- il repartira a chaque reactivation du combo (changement de piece, '
                'mort, reconnexion)' % son)

    # ---------------------------------------------------------------- VERROU 2
    # La decouverte doit survivre a la mort du joueur.
    marque = corps(code, 'private static boolean marquerComboDecouvert(')
    persist = corps(code, 'private static net.minecraft.nbt.CompoundTag persistOf(')
    if marque is None:
        echecs.append('VERROU 2 : marquerComboDecouvert est absente -- rien ne retient '
                      'quels combos le joueur a deja vus')
    if persist is None:
        echecs.append('VERROU 2 : persistOf est absente -- la decouverte n\'est pas '
                      'rangee dans les donnees persistantes du joueur')
    elif 'PlayerPersisted' not in persist and 'TAG_PERSIST' not in persist:
        echecs.append('VERROU 2 : la decouverte n\'est pas rangee sous "PlayerPersisted" '
                      '-- NeoForge ne recopie que cette sous-balise au respawn, donc '
                      'elle serait oubliee a chaque mort et le son reviendrait')
    if '"PlayerPersisted"' not in code:
        echecs.append('VERROU 2 : la sous-balise "PlayerPersisted" n\'apparait nulle part '
                      '-- aucune donnee ne survivra au respawn')

    # ---------------------------------------------------------------- VERROU 3
    # Le bit doit etre ecrit, et le tag rattache a son parent.
    if persist is not None and '.put(' not in persist:
        echecs.append('VERROU 3 : persistOf ne rattache pas la sous-balise a la racine '
                      '-- getCompound rend un tag DETACHE quand la cle manque, donc tout '
                      'ce qu\'on y ecrit serait perdu a la sauvegarde')
    if marque is not None and 'putLongArray(' not in marque:
        echecs.append('VERROU 3 : marquerComboDecouvert n\'ecrit jamais le bit -- la '
                      'decouverte ne serait pas memorisee et le son resonnerait')

    # ---------------------------------------------------------------- VERROU 4
    # Elle doit savoir dire NON.
    if marque is not None:
        # La garde d'entree « if (index < 0) return false; » contient deja un
        # « return false » : chercher ce texte tout court etait satisfait par elle, et
        # le sabotage « if (false) return false; » passait au travers. On verifie donc
        # le ROLE : un test du bit DEJA POSE qui mene a un refus.
        sans_garde = re.sub(r'if\s*\(\s*index\s*<\s*0\s*\)\s*return\s+false\s*;',
                            '', marque)
        norm = re.sub(r'\s+', ' ', sans_garde)
        if 'return false' not in sans_garde:
            echecs.append('VERROU 4 : marquerComboDecouvert ne renvoie jamais false en '
                          'dehors de sa garde d\'index -- elle declarerait chaque '
                          'activation comme une premiere fois et le son spammerait '
                          'exactement comme avant')
        elif not re.search(r'!=\s*0L?\s*\)\s*return\s+false', norm):
            echecs.append('VERROU 4 : marquerComboDecouvert ne teste pas le bit DEJA '
                          'POSE avant de refuser -- son refus ne depend plus de ce que '
                          'le joueur a deja decouvert')
        if 'return true' not in marque:
            echecs.append('VERROU 4 : marquerComboDecouvert ne renvoie jamais true -- '
                          'le son ne se ferait plus jamais entendre, meme a la decouverte')

    # ---------------------------------------------------------------- VERROU 5
    # Le retour visuel reste a chaque activation : on corrige le son, pas la fonction.
    if 'displayClientMessage(banner' not in bloc:
        echecs.append('VERROU 5 : la banniere d\'activation a disparu -- supprimer le '
                      'retour au joueur n\'est pas corriger le son')
    else:
        pos_b = bloc.index('displayClientMessage(banner')
        entre = bloc[max(0, pos_b - 400):pos_b]
        if 'marquerComboDecouvert(' in entre:
            echecs.append('VERROU 5 : la banniere est passee sous la condition de '
                          'premiere decouverte -- elle est muette, elle doit rester '
                          'affichee a chaque activation')

    if echecs:
        print('combosoundcheck : %d DEFAUT(S)\n' % len(echecs))
        for e in echecs:
            print('  !! %s\n' % e)
        return 1

    print('combosoundcheck : le son de combo ne sonne qu\'a la decouverte -- marquage '
          'persistant sous "PlayerPersisted", bit reellement ecrit, banniere conservee '
          'a chaque activation')
    return 0


if __name__ == '__main__':
    sys.exit(controler(sys.argv[1] if len(sys.argv) > 1 else '.'))
