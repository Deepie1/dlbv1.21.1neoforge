# Lumière V5 — double pilier de 80 blocs, lance latérale et ticks d’horloge

## Fichiers synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

- `client/LightSignatureVfx.java` — géométrie des trois animations V5 ;
- `client/WeaponWorldVfxManager.java` — durées, dimensions monde verrouillées, double passe alpha/additive, trajectoire latérale d’Astral et orientation de Chrono ;
- `enchantments/WeaponVfxServerListener.java` — multi-ciblage, collisions balayées, dégâts, emprises, sons et rajeunissement ;
- `commands/DlbVfxCommand.java` — commande `/anim light` ;
- `resources/deep_lucky_block.mixins.json` — suppression du refmap inexistant ;
- `resources/licenses/pixel_musketeer.txt` — licence déplacée hors du resource pack ;
- `resources/assets/deep_lucky_block/textures/item/lunettesdupatron.png` — texture 256×256.

`WeaponVisualEffectId.java` ne change pas : les titres et mappings Lumière existaient déjà. **Metal et Arc ne sont pas modifiés par cette V5.**

> **Suppression manuelle lors d’une copie par-dessus un ancien projet :** supprimer `src/main/resources/assets/deep_lucky_block/font/PIXEL_MUSKETEER_LICENSE.txt`. Un ZIP de sources ne peut pas représenter cette suppression ; la nouvelle licence est dans `src/main/resources/licenses/pixel_musketeer.txt`.

## Budget visuel et double passe

Les durées sont de **30 ticks pour Seraph Sky Pillar**, 80 pour Astral Star Lance et 100 pour Chrono Eternal Halo. L’emprise de Chrono continue néanmoins pendant 200 ticks côté serveur. Les trois rendus utilisent une passe alpha et une passe additive distincte, avec des contours de cubes animés.

Pics mesurés sur **1 001 frames** de l’aperçu hors ligne :

| Signature | Pic V3 | Pic V4 | Pic V5 |
|---|---:|---:|---:|
| Séraphin | 220 | 320 | **316 voxels** |
| Astral | 533 | 613 | **655 voxels** |
| Chrono | 799 | 799 | **799 voxels** |

Ces chiffres décrivent la géométrie soumise au rendu, pas un framerate garanti en jeu. Séraphin conserve un nombre fixe de contours malgré son nouveau volume. Astral gagne une âme continue, une lame pleine et des détails sans revenir aux cages surchargées de V2. Chrono conserve son budget.

Pour Séraphin, l’échelle du mob, l’échelle de variante, la densité aléatoire, la pulsation et le hash de taille des voxels sont désactivés pendant le rendu. Les dimensions demandées restent donc des dimensions monde, et pas des valeurs ensuite modifiées par le moteur.

## Seraph Sky Pillar — deux cuboïdes de 80 blocs

### Dimensions contractuelles et animation

Le rayon précédent de 0,60 × 16 est remplacé par **deux cuboïdes verticaux coaxiaux** :

- cuboïde extérieur jaune-blanc légèrement transparent : **exactement 6 × 80 × 6 blocs** ; demi-dimensions `(3 ; 40 ; 3)` autour de `Y + 39,10` ; bornes `Y − 0,90` à `Y + 79,10` ;
- cuboïde intérieur blanc très pâle/jaune : **0,5 × 80 × 0,5** au début, puis expansion uniquement en X/Z jusqu’à **5,5 × 80 × 5,5** au moment des dégâts ; demi-largeur `0,25 + 2,50 × croissance` ;
- la hauteur intérieure ne varie jamais : elle reste toujours de **80 blocs**.

Le volume alpha et le contour lumineux du cœur sont séparés : le contour ne crée pas une seconde enveloppe élargie susceptible de dépasser les 5,5 blocs demandés. Douze arêtes verticales soulignent le cube extérieur.

1. **Ticks 0–10 :** montée de la charge et croissance X/Z du cœur ; aucun dégât avant le tick 10.
2. **Ticks 10–14 :** cœur à 5,5 × 80 × 5,5, trois pulses de dégâts aux ticks **10, 12 et 14**.
3. **Ticks 14–30 :** pluie extérieure persistante et extinction progressive, sans dégât après le tick 14.

La collision serveur utilise le rayon intérieur **2,75** et les mêmes bornes verticales `−0,90 / +79,10`. Une AABB dont le bord entre dans le rayon compte même si son centre est légèrement extérieur.

### Pluie extérieure et sceau horizontal connecté

Vingt-quatre traînées verticales fines descendent sur les quatre faces du cube, à des hauteurs et vitesses différentes. Elles ne franchissent pas la face intérieure et restent visibles pendant l’extinction.

Le cercle magique reste **horizontal**, à `Y + 79,35`. Trois anneaux complets, un hexagone fermé, un hexagramme formé de deux triangles fermés, douze rayons reliant les anneaux et six petites boucles tangentes forment un réseau connecté. Les runes sont accrochées à leurs rayons : ce ne sont plus des fragments isolés. Aucun décor du sceau n’ajoute de dégâts.

### Un pilier par hostile, murs compris

La cible principale reçoit son instance. Tous les autres `LivingEntity` qui sont des `Enemy`, hors joueurs et attaquant, dans une sphère de **10 blocs** autour de la primaire reçoivent chacun une instance centrée sur leur propre position. Les cibles sont triées par identifiant pour une exécution déterministe.

- Chaque cible est verrouillée en position pendant **15 ticks**, avec `fullStun=false` : elle conserve son IA et peut attaquer.
- Séraphin ne demande pas de ligne de vue : les hostiles derrière une structure sont également ciblés et le pilier traverse les structures verticales.
- Dégâts primaires par pulse : **18 %** de l’attaque capturée, bornés entre 0,10 et 1,25 ; contre `sensitive_to_smite`, **25 %**, bornés entre 0,10 et 1,75.
- Toutes les valeurs secondaires — facteur, minimum et plafond — sont multipliées par **0,70**.
- Glowing est rafraîchi après une touche réussie ; aucun knockback d’éjection.

### Sons vanilla vérifiés dans la séquence

Chaque pilier joue `AMETHYST_BLOCK_CHIME` et **`BEACON_AMBIENT` à pitch 2,00** au tick 0, `BEACON_ACTIVATE` aigu au tick 9, `TOTEM_USE` et **`BEACON_AMBIENT` à pitch 2,00** au tick 10, `NOTE_BLOCK_BELL` au tick 12, **`BEACON_POWER_SELECT` à pitch 1,90** au tick 14 et `BEACON_DEACTIVATE` au tick 24. Les deux sons de balise aigus accompagnent l’allumage du beam ; les volumes secondaires sont réduits.

## Astral Star Lance — long trajet latéral, ciel étoilé et dégâts de passage

### Trajectoire indépendante du corps du joueur

Le manager oriente l’axe local F selon la direction perpendiculaire à l’attaque : `(-forwardZ ; forwardX)`. La pointe suit :

```text
head = −18 + easeInOut((t − 0,04) / 0,26) × 18
           + easeOut((t − 0,30) / 0,30) × 18
```

Elle part donc de **−18 blocs à gauche**, atteint la cible à **0** à l’impact du tick 24, puis continue jusqu’à **+18 blocs à droite**. Elle ne naît pas du joueur et ne revient pas vers lui. Le manche s’étend de `head − 9 × scale` à la douille `head − 1,10 × scale` ; la lame mesure 2,30 blocs et la pointe finale `head + 1,20 × scale`.

La cible primaire est verrouillée dès l’activation pour **40 ticks**, soit jusqu’au tick 40, avec `fullStun=false` : le verrou précède l’impact et conserve les attaques, sans `NoAi`, retrait de cible ni `stopUsingItem`.

### Silhouette de lance, pas une flèche segmentée

- manche octogonal continu de 9 blocs, âme alpha et huit rails lumineux ;
- neuf bagues localisées, deux hélices gravées et pommeau rayonnant ;
- lame de **2,30 blocs** en douze sections losangiques, âme translucide, arêtes et fuller bleu ;
- largeur de lame maximale **2,20 blocs** de diamètre, croisée d’or à quatre ailettes et sept runes ;
- sillage, éclats et onde au sol restent subordonnés à la silhouette.

### Tous les ennemis touchés avant et après la primaire

Le serveur vérifie le segment mobile du manche à la pointe aux ticks **4 à 52 inclus**, par collision segment/AABB avec ligne de vue. Un ensemble partagé `astralHitTargets` empêche de toucher deux fois le même ennemi au cours du déplacement.

- Primaire : **30 %** de l’attaque capturée, minimum 0,15, plafond 2,75, au tick 24 ; sa touche est réservée à ce tick pour rester synchronisée avec l’impact visuel.
- Chaque autre ennemi touché avant ou après la primaire : **15 %**, minimum 0,075, plafond 1,375, donc exactement **50 % des dégâts originaux de la lance**.
- Chaque contact reçoit un **push de 0,45 × l’échelle** dans le sens de la lance et Glowing.
- Les joueurs et non-ennemis restent exclus des touches collatérales.

Sons vanilla : carillons d’améthyste aux ticks 0/4/8/12, `CHORUS_FRUIT_TELEPORT` aux ticks 15 et 64, `TRIDENT_THROW` au tick 4 puis au tick 20, `PLAYER_ATTACK_CRIT` au tick 24, `ANVIL_PLACE` discret au tick 30, `PLAYER_ATTACK_SWEEP` aux ticks 14 et 36, un trident supplémentaire au tick 40 et carillon final au tick 48.

### Étoiles distinctes et constellations

Un champ de **douze étoiles nommées** occupe des coordonnées célestes différentes : Sirius, Canopus, Vega, Capella, Arcturus, Procyon, Altair, Aldebaran, Regulus, Antares, Deneb et **Polaris** (RA 2,530 h, DEC 89,264°). Polaris reçoit un noyau plus grand et une croix plus étendue. Les points sont répartis dans le ciel, sans carte plane ni regroupement autour de la cible.

Les constellations conservent leurs coordonnées J2000, leur recentrage barycentrique et leurs rotations de présentation :

- **Grande Ourse :** sept étoiles et liaisons du chariot, centrée sur `(-5 ; 4 ; -4)` ;
- **Cassiopée :** cinq étoiles en W, centrée sur `(0,40 ; 6 ; 4)` ;
- **Orion :** sept étoiles, ceinture reliée, centrée sur `(5 ; 3,5 ; -3)`.

Trois canaux et six paquets lumineux convergent vers trois sections de l’arme pendant la charge.

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

## Aperçu hors ligne et limites de validation

`deliverables/DLB_Light_Three_Signatures_preview.html` est autonome, sans réseau ni dépendance externe. Il permet de lire, mettre en pause, scrubber, orbiter, zoomer et comparer les trois modèles. Le ZIP `DLB_Light_Three_Signatures_src.zip` contient les 19 entrées de sources, miroirs et correctifs.

Le workspace ne contient pas le JDK/Gradle/MCreator nécessaire à une compilation NeoForge. Les audits de syntaxe Java, modèles numériques, collisions simulées et Chromium ne remplacent pas une compilation ou un test en jeu. Après copie, lancer `runClient` sous Windows/MCreator, puis `/anim light`.

Tests prioritaires en jeu :

1. Séraphin : mesurer les deux cubes 6 × 80 × 6 et 0,5 → 5,5 × 80, les dégâts ticks 10–14 uniquement, la pluie extérieure, le sceau horizontal et l’audition des deux sons de balise aigus.
2. Astral : placer plusieurs ennemis sur toute la ligne avant et après la cible ; vérifier une seule touche, 50 % + push, départ gauche et sortie droite sans passage par le joueur ; repérer Polaris et les constellations.
3. Chrono : placer zombies et animaux dans les 10 blocs, vérifier les horloges individuelles, le verrou de 10 s, les attaques conservées et la synchronisation des ticks avant/retour avec les aiguilles.
4. Rajeunissement : contrôler mêmes entités, équipement et PV post-dégâts ; vérifier qu’un squelette n’est pas converti.
5. Vérifier tous les sons vanilla et mesurer le framerate avec plusieurs piliers et horloges secondaires.
