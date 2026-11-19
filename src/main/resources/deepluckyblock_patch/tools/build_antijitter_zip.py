# -*- coding: utf-8 -*-
"""Construit le ZIP du correctif anti-tremblement (T407).

A lancer DEPUIS LA RACINE du depot :

    python3 src/main/resources/deepluckyblock_patch/tools/build_antijitter_zip.py

Le correctif ne touche que l'infrastructure de rendu : il vaut donc pour TOUTES les
familles a Pixels (Gaia, Chaos, Void, feu, eau, foudre...), et pas seulement pour celle
qui a servi a le reproduire.
"""
import hashlib
import io
import os
import zipfile

OUT = 'deliverables/DLB_Anti_Jitter_Fix_src.zip'

JAVA = [
    'src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java',
    'src/main/java/deepluckyblock/client/VoidPixelManager.java',
]

NOTE = u"""CORRECTIF ANTI-TREMBLEMENT DES ANIMATIONS A PIXELS  (T407)
=========================================================

Symptome
--------
Les animations tremblent, vibrent ou grelottent a l'echelle du pixel pendant qu'elles
jouent. Constate sur Gaia Wood, mais le defaut touchait toutes les familles a Pixels.

Les deux fichiers de ce ZIP remplacent les votres tels quels. Ils ne touchent QUE
l'infrastructure de rendu : aucune choregraphie, aucune couleur, aucune duree ne change.

    src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java
    src/main/java/deepluckyblock/client/VoidPixelManager.java


Cause n.1 : deux horloges qui derivent
--------------------------------------
La fraction de tick ecoulee etait recalculee a la main, en cinq endroits :

    clamp((System.nanoTime() - lastTickNanos) / 50_000_000, 0, 1)

Ce calcul suppose qu'un tick dure EXACTEMENT cinquante millisecondes de temps reel.
C'est faux. Le client declenche un tick quand son accumulateur depasse 50 ms, si bien
que l'ecart reel entre deux ClientTickEvent.Post oscille : a soixante images par
seconde, alternativement 50,0 et 66,7 ms. Des que l'ecart depasse 50 ms, la borne
SATURE a 1,0 — l'animation se fige une image entiere, puis repart a zero. Un palier,
puis un saut, a chaque periode.

Pendant ce temps la camera et les entites sont interpolees par Minecraft avec SON
partial tick, qui ne sature jamais. Les deux horloges derivaient donc l'une par rapport
a l'autre, image apres image, et toute la geometrie vibrait par rapport au monde.

Correction : WeaponWorldVfxManager.partialTick() lit desormais l'horloge qui fait
autorite :

    Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)

L'argument « true » n'est pas indifferent : c'est exactement celui dont
GameRenderer.renderLevel tire le flottant qui positionne la camera et interpole les
entites. Meme valeur, donc meme instant. On le lit sur Minecraft plutot que sur
l'evenement de rendu — c'est le meme objet — afin que la valeur soit juste pour TOUS
les appelants, y compris ceux qui dessinent avant la passe (rendu de lame, aura
cosmetique), sans dependre de l'ordre des handlers.

Au passage, beginFrame() divisait par 1.0e9 la ou les autres divisaient par 5.0e7 :
deux unites differentes pour une meme grandeur. Les cinq sites n'en font plus qu'un.


Cause n.2 : une image de retard sur la couche de structure
----------------------------------------------------------
La couche de structure est remplie par les signatures a AFTER_PARTICLES, le stage 9.
VoidPixelManager la dessinait a AFTER_TRANSLUCENT_BLOCKS, le stage 7 — donc AVANT :
il affichait le contenu de l'image precedente. Les positions etant stockees en absolu
(camX() + offset au remplissage) puis ramenees au repere camera au rendu
(SX[i] - cam.x), le resultat valait

    offset - (camera_N - camera_N-1)

c'est-a-dire l'offset decale de l'OPPOSE du deplacement de camera entre deux images.
Camera immobile, rien ne bouge ; joueur qui marche, le balancement de tete faisait
osciller toute l'armature en opposition de phase.

Correction : le rendu passe a AFTER_WEATHER, le stage 10. Le remplissage a eu lieu, et
la camera est bien celle de l'image courante.


Ce qui a ete verifie, et ce qui ne l'a pas ete
----------------------------------------------
Verifie sur les sources, et non suppose :
  - RenderLevelStageEvent.getPartialTick() rend un net.minecraft.client.DeltaTracker
    (NeoForge branche 1.21.1 ; fichier inchange depuis 1.21-rc1, donc valable pour le
    21.1.190 de ce projet) ;
  - DeltaTracker expose bien float getGameTimeDeltaPartialTick(boolean) ;
  - Minecraft.getTimer() est public ;
  - l'ordre des dix stages a ete relu pour etablir que le stage 9 precede le stage 10 ;
  - aucune autre horloge maison ne subsiste ailleurs dans le mod.

Bancs : semcheck 4 ERR connues / 0 nouvelle, VoidPixelManager OK ; floatcheck 0 alerte ;
typecheck 218 fichiers, rien de neuf.

NON verifie : javac est absent de l'environnement de developpement utilise, ces bancs
ne valent donc pas une compilation. Le correctif n'a pas pu etre observe en jeu.

Si un tremblement residuel persistait, l'information utile est de savoir s'il apparait
SURTOUT EN MARCHANT (ce serait encore la couche de structure) ou MEME A L'ARRET (ce
serait une troisieme cause, a chercher ailleurs).
"""


def main():
    if not os.path.isdir('deliverables'):
        raise SystemExit('A lancer depuis la racine du depot (dossier deliverables/ absent).')
    for p in JAVA:
        if not os.path.isfile(p):
            raise SystemExit('fichier absent : ' + p)

    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('LISEZ-MOI.txt', NOTE.encode('utf-8'))
        for p in JAVA:
            z.write(p, p)

    # Garde-fou : ce ZIP ne doit jamais embarquer de build.gradle.
    with zipfile.ZipFile(OUT) as z:
        names = z.namelist()
        assert not [n for n in names if 'build.gradle' in n], 'build.gradle dans le ZIP'
        for p in JAVA:
            s = z.read(p).decode('utf-8', 'replace')
            assert 'getGameTimeDeltaPartialTick' in s, 'correction absente de ' + p
            assert '/ 50_000_000.0f' not in s, 'horloge maison encore presente dans ' + p

    size = os.path.getsize(OUT)
    sha = hashlib.sha256(io.open(OUT, 'rb').read()).hexdigest()
    print('%s : %d entrees, %d octets' % (OUT, len(names), size))
    print('SHA-256 %s' % sha)


if __name__ == '__main__':
    main()
