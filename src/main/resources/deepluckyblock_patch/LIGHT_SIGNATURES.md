# Lumière V6.3 — restauration du rendu additif et des slashes

## Fichiers synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

- `client/LightSignatureVfx.java` — géométrie des trois animations V6 ;
- `client/WeaponWorldVfxManager.java` — durées, dimensions monde verrouillées, double passe alpha/additive, cible principale additive, flush dans le bon ordre, trajectoire latérale d’Astral, orientation de Chrono et déclenchement direct du slash au clic gauche ;
- `enchantments/WeaponVfxServerListener.java` — multi-ciblage, collisions balayées, dégâts, emprises, sons et rajeunissement ;
- `commands/DlbVfxCommand.java` — `/anim light`, démos par famille/effet et test du contour vanilla ;
- `resources/deep_lucky_block.mixins.json` — suppression du refmap inexistant ;
- `resources/licenses/pixel_musketeer.txt` — licence déplacée hors du resource pack ;
- `resources/assets/deep_lucky_block/textures/item/lunettesdupatron.png` — texture 256×256.

`WeaponVisualEffectId.java` ne change pas : les titres et mappings Lumière existaient déjà. **Metal et Arc ne sont pas modifiés par cette V6.**

> **Suppression manuelle lors d’une copie par-dessus un ancien projet :** supprimer `src/main/resources/assets/deep_lucky_block/font/PIXEL_MUSKETEER_LICENSE.txt`. Un ZIP de sources ne peut pas représenter cette suppression ; la nouvelle licence est dans `src/main/resources/licenses/pixel_musketeer.txt`.

## Budget visuel et double passe

Les durées sont de **30 ticks pour Seraph Sky Pillar**, 80 pour Astral Star Lance et 100 pour Chrono Eternal Halo. L’emprise de Chrono continue néanmoins pendant 200 ticks côté serveur. Les trois rendus utilisent une passe alpha et une passe additive distincte, avec des contours de cubes animés.

Pics mesurés sur **1 001 frames** de l’aperçu hors ligne. Astral a aussi été balayé sur **32 seeds**, avec un maximum observé de **843 voxels** :

| Signature | Pic V4 | Pic V5 | Pic V6 |
|---|---:|---:|---:|
| Séraphin | 320 | 316 | **316 voxels** |
| Astral | 613 | 655 | **799 démo ; 843 max observé** |
| Chrono | 799 | 799 | **799 voxels** |

Ces chiffres décrivent la géométrie soumise au rendu, pas un framerate garanti en jeu. Séraphin conserve son budget malgré la rétraction et le nouveau dimensionnement. Astral ajoute le disque, les étoiles et une quatrième constellation possible avec 799 voxels sur la seed de démonstration et 843 au maximum sur les 32 seeds auditées. Chrono conserve son budget.

Pour Séraphin, l’échelle de variante, la densité aléatoire, la pulsation et le hash de taille des voxels sont désactivés pendant le rendu. La largeur body transmise par le serveur reste une dimension monde directe, sans seconde multiplication côté client.

## Seraph Sky Pillar — hitbox body, cœur rétractile et fondu global

### Dimensions calculées pour chaque cible

La hauteur reste de **80 blocs**, de `Y − 0,90` à `Y + 79,10`. La largeur extérieure n’est plus une constante : le serveur lit l’AABB body de chaque mob et retient **max(X, Z)**, sans utiliser Y.

- Exemple demandé, hitbox **1,5 / 2 / 1** : beam extérieur **1,5 × 80 × 1,5**.
- Le cœur part de **1/12 de la largeur extérieure**, atteint **11/12** pendant les dégâts, puis revient à **1/12**. Sa hauteur reste toujours de 80 blocs.
- Le diamètre du sceau horizontal vaut exactement **2,5 × la largeur extérieure**, donc son rayon vaut **1,25 × largeur**. Pour un beam de **2,4 blocs**, le rayon est exactement **3 blocs**. Dans l’exemple à 1,5 bloc, le diamètre est 3,75 blocs.
- L’aperçu hors ligne utilise la référence 2,4 blocs afin de montrer le cercle de rayon 3 demandé. En jeu, chaque instance primaire ou secondaire conserve sa propre largeur.

Le manager désactive les échelles de variante et les hash de dimensions : la largeur transmise par le serveur n’est pas multipliée une seconde fois côté client.

### Chronologie de 30 ticks

1. **Ticks 0–10 :** expansion X/Z du cœur ; aucun dégât avant le tick 10.
2. **Ticks 10–14 :** cœur à 11/12 de la largeur extérieure ; pulses de dégâts aux ticks **10, 12 et 14**.
3. **Ticks 14–22 :** rétraction progressive du cœur jusqu’à 1/12 ; aucun dégât supplémentaire.
4. **Ticks 22–30 :** fondu global et simultané des deux cubes, des douze arêtes, de la pluie extérieure et du sceau. La frame finale est vide ; aucune disparition instantanée au tick 16.

La collision serveur utilise le demi-cœur maximal `largeur × 11/24` et les bornes verticales `−0,90 / +79,10`. Elle est donc recalculée avec la même hitbox que le rendu. Une AABB dont le bord entre dans ce rayon compte même si son centre est légèrement extérieur.

Vingt-quatre traînées descendent autour du volume, à des hauteurs et vitesses différentes. Le sceau reste horizontal à `Y + 79,35` : trois anneaux, deux carrés fermés, un octogramme, seize rayons, huit runes et quatre satellites reliés. Toutes les dimensions radiales suivent le facteur du sceau, y compris les boucles satellites.

### Un pilier par hostile, murs compris

La cible principale reçoit son instance. Tous les autres `LivingEntity` qui sont des `Enemy`, hors joueurs et attaquant, dans une sphère de **10 blocs** autour de la primaire reçoivent chacun une instance centrée sur leur position et calculée avec **leur propre hitbox**. Les cibles sont triées par identifiant pour une exécution déterministe.

- Verrou de position de **15 ticks**, avec `fullStun=false` : IA et attaques conservées.
- Aucune ligne de vue requise ; le pilier traverse les structures verticales.
- Dégâts primaires par pulse : **18 %**, entre 0,10 et 1,25 ; contre `sensitive_to_smite`, **25 %**, entre 0,10 et 1,75.
- Facteur, minimum et plafond secondaires multipliés par **0,70**.
- Glowing après une touche réussie ; aucun knockback d’éjection.

### Sons vanilla

Chaque pilier joue `AMETHYST_BLOCK_CHIME` et `BEACON_AMBIENT` aigu au tick 0, `BEACON_ACTIVATE` au tick 9, `TOTEM_USE` et un second `BEACON_AMBIENT` au tick 10, `NOTE_BLOCK_BELL` au tick 12, `BEACON_POWER_SELECT` au tick 14 et `BEACON_DEACTIVATE` au tick 24. Les volumes secondaires sont réduits. Les horaires sont vérifiés statiquement ; l’audition NeoForge reste à contrôler dans `runClient`.

## Astral Star Lance — disque céleste, constellations variables et charge épaissie

### Zone circulaire de 18 blocs

La lance part toujours de **−18 blocs à gauche**, atteint la cible à 0 puis continue à **+18 blocs à droite**. Un cercle horizontal de **18 blocs de rayon**, donc 36 blocs de diamètre, matérialise toute la zone entre le départ et la cible. La caméra de l’aperçu garde maintenant ce cercle entier dans le cadre au lieu de suivre uniquement la pointe.

Le disque contient **72 étoiles** : les douze étoiles nommées, dont **Polaris**, puis soixante points répartis uniformément par racine du rayon aléatoire. Leurs coordonnées F/L restent dans le cercle et leurs hauteurs varient. Polaris conserve son noyau et sa croix plus lumineux.

Six étoiles filantes potentielles ont chacune une probabilité, une position, une direction, une durée et une fenêtre propres à la seed. Certaines activations n’en affichent aucune, d’autres plusieurs ; leurs traînées et leurs têtes apparaissent puis disparaissent progressivement.

### Deux à quatre constellations uniques par activation

Un mélange déterministe de la seed choisit **2, 3 ou 4 constellations uniques** parmi :

- **Grande Ourse** — sept étoiles ;
- **Cassiopée** — cinq étoiles en W ;
- **Orion** — sept étoiles et ceinture reliée ;
- **Cygne** — Deneb, Sadr, Gienah, Delta Cygni et Albireo, sous forme de croix du Nord.

Les centres et échelles sont tirés dans le disque en laissant la place à la constellation complète. Le tirage reste stable pendant toute l’animation : les constellations ne changent pas à chaque frame. Chaque constellation sélectionnée alimente la charge par un canal et deux paquets lumineux.

### Fade in, charge ×2,2 et fade out

La lance apparaît progressivement pendant les premiers ticks au lieu d’être instantanément présente. Tous ses traits principaux — âme et rails du manche, bagues, hélices, arêtes de lame, fuller, croisée, runes et contours des gemmes — utilisent :

```text
épaisseur = épaisseur V5 × (1 + 1,2 × smooth(t / 0,30))
```

Le facteur commence donc à **×1** et atteint exactement **×2,2 à l’impact du tick 24**. La géométrie conserve son manche octogonal continu de 9 blocs, sa lame de 2,30 blocs et sa longueur totale de 10,2 blocs.

Après la course vers la droite, `lanceAlpha` effectue un fondu de sortie de **t = 0,78 à t = 1**. Un second fondu global termine le ciel, les constellations, les météores et les particules. La frame 80 est vide ; il n’y a plus de disparition anticipée à 88 % ni de cut instantané. Le manager transmet alpha 240 à Astral et laisse cette chorégraphie gérer le fondu, sans seconde atténuation linéaire.

### Gameplay et sons inchangés

La primaire est verrouillée dès l’activation pour **40 ticks**, avant l’impact, avec attaques conservées. Le serveur vérifie le segment mobile du manche à la pointe aux ticks **4 à 52**. Un ensemble partagé empêche toute double touche.

- Primaire : **30 %**, minimum 0,15, plafond 2,75, réservé au tick 24.
- Chaque autre ennemi touché avant ou après : exactement **50 % de la lance originale**, push de 0,45 × l’échelle et Glowing.
- Joueurs, attaquant et non-ennemis exclus des touches collatérales ; ligne de vue requise pour elles.

Sons conservés : améthyste aux ticks 0/4/8/12, tridents aux ticks 4/20/40, sweeps aux ticks 14/36, téléportations aux ticks 15/64, critique au tick 24, enclume discrète au tick 30 et carillon final au tick 48.

## Chrono Eternal Halo — AoE conservée et sons sur chaque cran visible

L’horloge conserve ses chiffres **1 à 12**, ses 60 graduations, trois aiguilles, quatre engrenages et son pendule. Son centre local reste à **Y + 4,05**, face à la caméra, sans changement de la géométrie V4.

L’angle visible est quantifié en crans entiers de 0 à 99 via `chronoVisualStep(tick)`. Le serveur appelle la même fonction :

- cran supérieur au précédent : **`NOTE_BLOCK_HAT`, pitch 1,90** ;
- cran inférieur au précédent : **`NOTE_BLOCK_SNARE`, pitch 1,55** ;
- cran identique : aucun son.

Les sons ne sont donc pas joués sur un intervalle arbitraire : chaque avance visible produit un tick et chaque recul visible produit l’autre tick. Les sons d’ambiance existants restent présents aux étapes de charge, dette et extinction.

Chrono reste une **AoE sphérique de 10 blocs** autour de la cible touchée. Toutes les créatures vivantes non-joueurs, hostiles ou passives, reçoivent leur propre horloge. L’attaquant est exclu et les cibles sont triées par identifiant.

**Chaque mob de l’AoE** est verrouillé en position pendant exactement **200 ticks = 10 secondes**, avec attaques conservées. Les ennemis non-boss reçoivent Lenteur II ; Warden, Wither et Ender Dragon gardent leur contrôle.

Au tick 55, la primaire reçoit **24 %** de l’attaque capturée, bornée entre 0,12 et 2,00 ; chaque autre mob reçoit **10 %**, borné entre 0,06 et 1,10. Après une touche réussie, le rajeunissement est appliqué individuellement à chaque mob encore vivant.

Sons d’ambiance conservés : améthyste et téléportation au tick 0, cloches aux ticks 15/30/45/55, `CHORUS_FRUIT_TELEPORT` aux ticks 55 et 86, `BONE_MEAL_USE` au tick 58, extinction de balise au tick 100 et libération au tick 200. Ils s’ajoutent aux ticks hat/snare synchronisés.

### Rajeunissement sans remplacement d’entité

Après une touche réussie, si un mob de l’AoE est encore vivant et possède une forme bébé vanilla prise en charge, **la même entité** reçoit cette forme : `Zombie` et ses variantes, `Piglin`, et `AgeableMob` pour les créatures compatibles.

Il n’y a aucun `spawn`, suppression ou remplacement. L’UUID, les objets en main, l’armure, les effets et les autres données restent attachés à l’entité. La santé est lue **après les dégâts** puis restaurée à cette valeur : rajeunir ne soigne pas. La cible reçoit aussi **Faiblesse I pendant 105 ticks**.

**Skeleton n’a pas de forme bébé vanilla** et n’est donc pas transformé, pas plus que les boss ou les types sans API bébé. Aucun faux bébé n’est fabriqué.

Tous les dégâts différés passent par `hurtWithCapturedWeapon` : crédit du tueur, armure/protections et verrou anti-récursion.

## Corrections de log inchangées

- Suppression du `refmap` inexistant.
- Licence Pixel Musketeer déplacée sous `licenses/`.
- Texture des lunettes agrandie de 169×169 à 256×256 pour les mipmaps.
- Niveau de log client MCreator ramené de `DEBUG` à `INFO`.

Ces changements ne prétendent pas réparer les diagnostics vanilla/NeoForge : `/teleport`, schéma d’URL des assets de développement, sons de corne de chèvre, shader `Sampler2` ou contenu généré de `run/config/fml.toml`. Aucun shader vanilla n’est remplacé.

## Correctif de compilation V6.1 — six erreurs `runClient`

Le retour MCreator/NeoForge 1.21.1 a permis de corriger les types mappings qui ne sont pas détectables par tree-sitter :

- les coordonnées des instances secondaires de Séraphin et Chrono sont converties en `float`, conformément aux paramètres réseau de `broadcastNear` ;
- les trois appels Astral utilisent `SoundEvents.TRIDENT_THROW.value()` car `lightSound` attend un `SoundEvent` et non un `Holder<SoundEvent>` ;
- la branche indépendante `GOLDEN_CRIT_STAR` réutilise `sizeScaleOf(target)` : elle ne possède pas de variable `fx` et ne doit pas appeler le helper propre à Séraphin.

Aucune géométrie, durée, trajectoire, valeur de dégâts ou séquence sonore n’est modifiée par ce correctif de compilation.

## Correctif de diagnostic V6.2 — slash et effets isolés

Le slash de mêlée était déclenché uniquement lorsque le tick client observait la transition de l’état transitoire `swinging`, dans une fenêtre de deux ticks. V6.2 écoute aussi `InputEvent.InteractionKeyMappingTriggered` : le clic gauche local crée immédiatement l’arc. Le polling reste le filet de sécurité pour les autres joueurs et les swings initiés par le jeu. Un marqueur de déduplication empêche le tick suivant de créer une seconde copie. Les dégâts, les probabilités et les paquets serveur ne changent pas.

Les commandes suivantes utilisent le même paquet visuel S2C que `playDebug`, sans appliquer les dégâts, les emprises ou les transformations du combat :

```text
/dlbvfx demo light             famille Lumière, une animation à la fois
/dlbvfx demo seraph            Seraph seul, répété après son fondu
/dlbvfx demo astral            Astral seul, répété après son fondu
/dlbvfx demo chrono            Chrono seul, répété après son fondu
/dlbvfx demo astral slash      entaille Astral seule, sans la lance
/dlbvfx demo 25 slash          même test par identifiant numérique
/dlbvfx demo fire              exemple pour une autre famille
/dlbvfx glow 8                 contour vanilla seul sur le dummy, pendant 8 secondes
/dlbvfx stop                   arrêt de la boucle isolée
```

À utiliser dans une zone dégagée et en reculant après le lancement : le point de présentation reste fixe et le ciel d’Astral mesure 36 blocs de diamètre. Tous les noms d’éléments et les 41 identifiants/noms d’effets sont acceptés. `swing` est un alias de `slash`. `/dlbvfx elements <famille>` conserve son rôle historique mais emprunte maintenant ce même séquenceur : chaque effet dispose de sa durée complète plus huit ticks de pause. L’ancien envoi toutes les huit ticks superposait notamment Seraph, Astral et Chrono et ne permettait pas d’identifier le rendu défaillant.

`/dlbvfx glow` ne joue volontairement aucun paquet Light. Il vérifie séparément le `MobEffects.GLOWING` de l’entité. En combat, Seraph et Astral demandent ce contour seulement après une touche secondaire acceptée ; une animation visible ne garantit donc pas que ce statut a été appliqué, et inversement.

Aucune géométrie V6, aucune trajectoire, aucune durée de signature, aucun son, aucune collision et aucune valeur de dégâts n’est modifiée en V6.2. Metal et Arc restent hors de portée. La compilation et le rendu doivent néanmoins être revérifiés dans `runClient` : les audits locaux ne peuvent pas observer les shaders ou les contours vanilla.

## Correctif de rendu V6.3 — deux corrections ciblées après les tests isolés

Le retour `runClient` a confirmé que `/dlbvfx demo astral` exécute bien sa boucle serveur alors que la lance et l’horloge restent invisibles. Le problème n’est donc pas la commande, le calcul de la durée ou l’absence du paquet de démonstration.

### Slash : inversion des indicateurs de flush

`flushBatches` attend les paramètres `(hasAlpha, hasAdditive)`, mais les trois appels lui transmettaient `(addVc != null, alphaVc != null)`. Quand les deux passes existent, l’inversion est masquée parce que les deux booléens valent `true`. Un slash Lumière est **additif-seul** : le code vidait alors le RenderType translucide, jamais le buffer additif contenant réellement l’arc.

Les trois appels transmettent désormais `(alphaVc != null, addVc != null)`. Cette correction concerne aussi les autres effets à passe unique ; les trajectoires et les huit ticks du slash ne changent pas.

### Lance et horloge : cible de sortie additive

La passe monde additive était envoyée explicitement vers `PARTICLES_TARGET` pendant `RenderLevelStageEvent.Stage.AFTER_PARTICLES`, alors que le renderer additif des lames, qui utilise le même shader, écrit dans la cible principale. À ce stade, les particules vanilla sont déjà composées. Le pattern observé en jeu — corps translucide de Séraphin visible, mais liens additifs d’Astral/Chrono invisibles — correspond à cette sortie devenue inactive sur le pipeline Intel du test.

La passe additive utilise maintenant `RenderStateShard.MAIN_TARGET`, comme le renderer de lame. Elle conserve le shader lightning, la transparence additive, l’absence de culling, le test de profondeur et le masque couleur. La passe alpha n’est pas modifiée.

Enfin, une exception de rendu n’est plus totalement silencieuse : le premier échec puis au maximum un message toutes les cinq secondes sont journalisés avec `[DLB-VFX] World render batch aborted`. Le nettoyage défensif existant est conservé. Ce journal permettra de distinguer une géométrie qui lève une exception d’un simple problème de framebuffer.

Aucun voxel, rayon, timing, fade, dégât, son ou comportement serveur n’est changé en V6.3. Les commandes V6.2 restent identiques et servent de test de régression :

```text
/dlbvfx demo astral slash
/dlbvfx stop
/dlbvfx demo astral
/dlbvfx stop
/dlbvfx demo chrono
```

Le slash doit maintenant apparaître seul ; la lance doit montrer son manche, sa lame et son ciel ; l’horloge doit montrer son cadran et ses contours. La vérification GPU réelle reste indispensable : les audits hors ligne valident la structure et les modèles, pas le framebuffer NeoForge/Intel.

## Aperçu hors ligne et limites de validation

`deliverables/DLB_Light_Three_Signatures_preview.html` est autonome, sans réseau ni dépendance externe. Il permet de lire, mettre en pause, scrubber, orbiter, zoomer et comparer les trois modèles. Le ZIP `DLB_Light_Three_Signatures_src.zip` contient les 19 entrées de sources, miroirs et correctifs.

Le workspace ne contient pas le JDK/Gradle/MCreator nécessaire à une compilation NeoForge. Les audits de syntaxe Java, modèles numériques, collisions simulées et Chromium ne remplacent pas une compilation ou un test en jeu. Après copie, lancer `runClient` sous Windows/MCreator, puis tester séparément `/dlbvfx demo seraph`, `/dlbvfx demo astral`, `/dlbvfx demo chrono` et `/dlbvfx demo astral slash` avant de retester `/anim light` en combat.

Tests prioritaires en jeu :

1. Séraphin : tester plusieurs largeurs de hitbox, dont 1,5 / 2 / 1 ; mesurer le beam et le sceau 2,5×, la rétraction ticks 14–22, le fondu global 22–30 et l’absence de dégâts après le tick 14.
2. Astral : vérifier le disque de 18 blocs, les 72 étoiles, les météores variables, le tirage de 2–4 constellations uniques, l’épaisseur ×2,2 au tick 24 et les fondus ; contrôler une seule touche et 50 % + push sur les ennemis avant/après.
3. Chrono : placer zombies et animaux dans les 10 blocs, vérifier les horloges individuelles, le verrou de 10 s, les attaques conservées et la synchronisation des ticks avant/retour avec les aiguilles.
4. Rajeunissement : contrôler mêmes entités, équipement et PV post-dégâts ; vérifier qu’un squelette n’est pas converti.
5. Vérifier tous les sons vanilla et mesurer le framerate avec plusieurs piliers et horloges secondaires.
