# Lumière V4 — cast, beams multiples, vraies constellations et verrou temporel

## Fichiers à conserver synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

- `client/LightSignatureVfx.java` — géométrie des trois animations V4 ;
- `client/WeaponWorldVfxManager.java` — durées, dimensions monde de Séraphin, double passe alpha/additive, orientation de Chrono et `/anim light` côté client ;
- `enchantments/WeaponVfxServerListener.java` — multi-ciblage, collisions, dégâts, emprises, sons et rajeunissement ;
- `commands/DlbVfxCommand.java` — commande `/anim light` ;
- `resources/deep_lucky_block.mixins.json` — suppression du refmap inexistant ;
- `resources/licenses/pixel_musketeer.txt` — licence déplacée hors du resource pack ;
- `resources/assets/deep_lucky_block/textures/item/lunettesdupatron.png` — texture 256×256.

`WeaponVisualEffectId.java` ne change pas : les titres et mappings Lumière existaient déjà. **Metal et Arc ne sont pas modifiés par cette V4.**

> **Suppression manuelle lors d'une copie par-dessus un ancien projet :** supprimer `src/main/resources/assets/deep_lucky_block/font/PIXEL_MUSKETEER_LICENSE.txt`. Un ZIP de sources ne peut pas représenter cette suppression ; la nouvelle licence est dans `src/main/resources/licenses/pixel_musketeer.txt`.

## Budget visuel et double passe

Les durées sont de **30 ticks pour Seraph Sky Pillar**, 80 pour Astral Star Lance et 100 pour Chrono Eternal Halo. L'emprise de Chrono continue néanmoins pendant 200 ticks côté serveur. Les trois rendus utilisent une passe alpha et une passe additive distincte, avec des contours de cubes animés.

Pics mesurés sur 1 001 frames de l'aperçu hors ligne :

| Signature | Pic V2 | Pic V3 | Pic V4 |
|---|---:|---:|---:|
| Séraphin | 699 | 220 | **320 voxels** |
| Astral | 2 342 | 533 | **613 voxels** |
| Chrono | 1 084 | 799 | **799 voxels** |

Ces chiffres décrivent la géométrie soumise au rendu, pas un framerate garanti en jeu. Le beam V4 ajoute seulement huit panneaux tangentiels à sa structure : il devient un volume lisible sans retrouver les empilements de cubes translucides des anciennes versions. Astral ajoute des arêtes de lame utiles, pas une pluie de particules. Chrono conserve son budget V3.

Pour Séraphin, l'échelle du mob, l'échelle de variante, la densité aléatoire, la pulsation et le hash de taille des voxels sont désactivés pendant le rendu. Les rayons et longueurs demandés restent donc des dimensions monde, et pas des valeurs ensuite modifiées par le moteur.

## Seraph Sky Pillar — ligne de cast puis beam massif

### Machine à états de 30 ticks

1. **Ticks 0–10, soit 0,5 s :** une `VFX_Line` verticale fine, de rayon **0,05 bloc**, relie le bas au sommet du futur rayon. Elle est purement visuelle et ne possède aucune hitbox ni dégât.
2. **Ticks 10–11, soit 1 tick :** la ligne disparaît et le `VFX_Beam` s'ouvre jusqu'à son rayon complet de **0,60 bloc**, soit **1,20 bloc de diamètre**. La transition n'utilise plus la lente dilatation de V3.
3. **Ticks 10–14, soit 0,2 s :** la collision cylindrique et les dégâts sont actifs. Le volume va de **Y−0,90 à Y+15,10** autour du pivot, soit exactement **16 blocs**. Le pivot étant le centre de la hitbox de la cible, cette convention couvre le sol et le sommet pour la cible humanoïde standard.
4. **Ticks 14–16 :** le beam se referme et s'éteint. Aucun dégât n'est programmé après le tick 14.
5. **Jusqu'au tick 30 :** le sceau divin supérieur termine son propre fondu, sans hitbox.

Le beam comprend un cœur alpha, huit panneaux tangentiels, douze arêtes et quatre jonctions. **Son trait blanc central grandit progressivement de 0,05 à 0,16 bloc de rayon entre les ticks 10 et 14**, sans changer le rayon extérieur de 0,60. Un zombie standard tient dans son volume horizontal. Le cylindre serveur teste le point le plus proche de l'AABB : une hitbox qui entre dans le rayon compte même si son centre est légèrement à l'extérieur.

Le cercle supérieur est reconstruit comme un **sceau divin animé horizontal** : trois couronnes contrarotatives, deux étoiles à seize branches, huit runes, trois petits cercles satellites et un cœur blanc. Toutes ces pièces sont dessinées dans le plan X/Z à hauteur fixe, au-dessus du beam ; le disque n'est donc plus vertical. Aucun décor du sceau n'ajoute de dégâts.

### Un beam par hostile, murs compris

La cible principale reçoit son instance. Tous les autres `LivingEntity` qui sont des `Enemy`, hors joueurs et attaquant, dans une sphère de **10 blocs** autour de la primaire reçoivent chacun une vraie instance `SERAPH_SKY_PILLAR` centrée sur leur propre position. Les cibles sont triées par identifiant pour une exécution déterministe.

- Chaque cible est verrouillée en position pendant **15 ticks**, avec `fullStun=false` : elle conserve son IA et peut attaquer.
- Séraphin ne demande pas de ligne de vue : les hostiles extérieurs ou derrière une structure sont également ciblés, et le beam traverse les structures verticales.
- Les pulses de dégâts ont lieu aux ticks **10, 12 et 14**. La collision est revérifiée à chaque pulse sur la position verrouillée du beam.
- Dégâts primaires par pulse : **18 %** de l'attaque capturée, bornés entre 0,10 et 1,25 ; contre `sensitive_to_smite`, **25 %**, bornés entre 0,10 et 1,75.
- Toutes les valeurs secondaires — facteur, minimum et plafond — sont multipliées par **0,70**, donc exactement **30 % de dégâts en moins** que la primaire.
- Glowing est rafraîchi 10 ticks après une touche réussie. Il n'y a pas de knockback d'éjection.

### Sons vanilla

Chaque beam joue, avec un volume moindre pour les secondaires : `AMETHYST_BLOCK_CHIME` au tick 0, `BEACON_ACTIVATE` au tick 9, `TOTEM_USE` au tick 10, `NOTE_BLOCK_BELL` au tick 12 et `BEACON_DEACTIVATE` au tick 24.

## Astral Star Lance — coordonnées réelles, volumes séparés et lame sacrée

### Constellations

Les étoiles sont construites à partir de coordonnées de type **J2000**, avec ascension droite en heures et déclinaison en degrés, puis projetées sur des vecteurs 3D unitaires :

- **Grande Ourse :** Dubhe, Merak, Phecda, Megrez, Alioth, Mizar et Alkaid ; sept étoiles et les liaisons du chariot ;
- **Cassiopée :** Caph, Schedar, Gamma Cas, Ruchbah et Segin ; cinq étoiles en W ;
- **Orion :** Betelgeuse, Bellatrix, Alnitak, Alnilam, Mintaka, Saiph et Rigel ; les trois étoiles de la ceinture sont reliées.

Chaque constellation est recentrée sur son barycentre. Cette translation rigide conserve les distances angulaires et la forme ; elle évite seulement que tous les vecteurs célestes se retrouvent au-dessus de la lance. Les trois volumes ont ensuite des centres distincts : `(-3,50 ; 3,20 ; -2,60)`, `(0,40 ; 4,10 ; 2,80)` et `(3,40 ; 2,90 ; -2,20)`. Ils sont donc répartis des deux côtés et au-delà de la zone de départ de la lance.

Des rotations initiales différentes présentent leurs silhouettes, puis des rotations animées et des inclinaisons distinctes conservent leur profondeur réelle. Les constellations ne sont plus des motifs plats regroupés autour de l'origine. Trois canaux et six paquets lumineux convergent vers trois sections de l'arme pendant la charge.

### Lance sacrée

La silhouette segmentée simple est remplacée par une construction complète :

- fût quadrangulaire de **20 sections**, quatre rails continus et âme lumineuse ;
- douille à quatre couronnes octogonales et pommeau rayonnant ;
- lame large à section losangique de **9 sections**, arêtes longitudinales, surfaces alpha, pointe et arête centrale ;
- fuller bleu, croisée d'or à quatre ailettes, gemme et cinq runes ;
- deux hélices de charge, sillage structuré, corridor d'impact et reflet au sol.

Au tick 24, le segment continu de **8,70 blocs**, centré sur la cible et orienté par l'attaque, conserve sa collision segment/AABB par slabs : **30 %** sur la primaire, **18 %** sur les ennemis traversés. Les collatéraux exigent une ligne de vue. La primaire est tenue en position pendant **25 ticks** avec attaques conservées.

Sons vanilla : carillons d'améthyste aux ticks 0/4/8/12, `CHORUS_FRUIT_TELEPORT` aux ticks 15 et 64, `TRIDENT_THROW` au tick 20, `PLAYER_ATTACK_CRIT` au tick 24, `ANVIL_PLACE` discret au tick 30 et carillon final au tick 48.

## Chrono Eternal Halo — face au joueur, hors sol et verrou de dix secondes

L'horloge conserve ses chiffres **1 à 12**, ses 60 graduations, trois aiguilles, quatre engrenages et son pendule. Son centre local est élevé à **Y+4,05**. Le contrôle des cuboïdes orientés sur 1 001 frames donne un point bas minimal de **Y+0,4306** autour du pivot : cadran, engrenages, éclats et pendule restent entièrement au-dessus du sol.

Le manager calcule à chaque frame la direction de la position de l'effet vers la caméra. Le plan du cadran suit cette direction, ce qui évite de présenter l'horloge par la tranche lorsque le joueur se déplace.

Chrono est maintenant une **AoE de 10 blocs** autour de la cible touchée. Toutes les créatures vivantes non-joueurs dans cette sphère, hostiles ou passives, reçoivent leur propre instance d'horloge. L'attaquant est exclu. Les cibles sont triées par identifiant pour un traitement déterministe.

**Chaque mob de l'AoE** est verrouillé en position pendant exactement **200 ticks = 10 secondes**. L'emprise utilise `fullStun=false` : aucun `NoAi`, aucun retrait de cible et aucun `stopUsingItem` ne sont appliqués par cette emprise. Les mobs peuvent donc continuer à attaquer. Le tick serveur replace seulement leur position et annule leur vélocité ; la gravité et les déplacements redeviennent normaux à la libération.

Les ennemis non-boss de l'AoE reçoivent toujours Lenteur II ; Warden, Wither et Ender Dragon gardent leur contrôle. Au tick 55, chaque cible reçoit sa dette :

- **24 %** de l'attaque capturée sur la primaire, bornée entre 0,12 et 2,00 points ;
- **10 %** sur chaque autre mob dans les 10 blocs, bornée entre 0,06 et 1,10 point.

Après une touche réussie, le rajeunissement est appliqué individuellement à chaque mob encore vivant. L'ancienne zone secondaire de 1,55 bloc et l'ancien déplacement radial sont supprimés.

Sons vanilla : améthyste et téléportation au tick 0, cloches aux ticks 15/30/45/55, `CHORUS_FRUIT_TELEPORT` aux ticks 55 et 86, `BONE_MEAL_USE` au tick 58, extinction de balise au tick 100 et son de libération au tick 200.

### Rajeunissement sans remplacement d'entité

Après une touche réussie, si un mob de l'AoE est encore vivant et possède une forme bébé vanilla prise en charge, **la même entité** reçoit cette forme :

- `Zombie`, donc également Husk, Drowned, ZombieVillager et ZombifiedPiglin ;
- `Piglin` ;
- `AgeableMob`, pour les créatures vanilla/mod compatibles avec cette API.

Il n'y a aucun `spawn`, aucune suppression et aucune copie approximative d'inventaire. L'UUID, les objets en main, l'armure, les effets, la persistence et les autres données restent attachés à l'entité. La santé est lue **après les dégâts**, puis explicitement restaurée à cette valeur, bornée par le maximum courant : rajeunir ne soigne pas et n'inflige pas une seconde dette.

La cible rajeunie reçoit aussi **Faiblesse I pendant 105 ticks**. Les règles vanilla propres à chaque forme restent actives. **Skeleton n'a pas de forme bébé vanilla** et n'est donc pas transformé, pas plus que les boss ou les types sans API bébé.

Tous les dégâts différés passent par `hurtWithCapturedWeapon` : crédit du tueur, armure/protections et verrou anti-récursion. Les touches secondaires d'Astral excluent les non-ennemis. Chrono exclut l'attaquant et les joueurs, mais inclut bien tous les autres mobs vivants dans ses 10 blocs.

## Corrections du log prises en charge par le mod

- Suppression de la propriété `refmap` qui pointait vers un fichier jamais généré.
- Suppression du chemin resource-pack invalide `font/PIXEL_MUSKETEER_LICENSE.txt` ; la licence reste distribuée sous `licenses/`.
- Agrandissement Lanczos de la texture des lunettes de 169×169 à 256×256, sans recadrage, pour permettre les mipmaps.
- Niveau de log du client MCreator ramené de `DEBUG` à `INFO` dans `build.gradle`.

Ces changements ne prétendent pas réparer les diagnostics appartenant à vanilla ou à NeoForge : ambiguïté de `/teleport`, schéma d'URL des assets de développement, sons de corne de chèvre, shader `rendertype_entity_translucent_emissive`/`Sampler2` et contenu généré de `run/config/fml.toml`. Aucun shader vanilla n'est remplacé par ce patch.

## Aperçu hors ligne et limites de validation

`deliverables/DLB_Light_Three_Signatures_preview.html` est autonome, sans réseau ni dépendance externe. Il permet de lire, mettre en pause, scrubber, orbiter, zoomer et comparer les trois modèles. Le ZIP `DLB_Light_Three_Signatures_src.zip` contient les sources principales, les miroirs MCreator et les correctifs de ressources.

Le workspace ne contient pas le JDK/Gradle/MCreator nécessaire à une compilation NeoForge. Les audits de syntaxe Java, les modèles numériques, les collisions simulées et Chromium ne remplacent pas une compilation ou un test en jeu. Après copie des sources et miroirs, lancer `runClient` sous Windows/MCreator, puis `/anim light`.

Tests prioritaires en jeu :

1. Séraphin : vérifier 0,5 s sans dégâts, le beam de 1,20 × 16, la fin des dégâts au tick 14 et plusieurs hostiles derrière un mur dans les 10 blocs ; mesurer le rapport secondaire/primaire de 0,70.
2. Astral : orbiter autour des constellations pour voir leurs profondeurs, vérifier leurs rotations et l'impact du corridor au tick 24.
3. Chrono : placer plusieurs zombies et animaux dans les 10 blocs, vérifier qu'ils ont tous une horloge et un verrou de 10 s, qu'ils peuvent attaquer, puis contrôler leur libération.
4. Rajeunissement : inspecter plusieurs zombies équipés et un animal après Chrono — mêmes entités, mêmes objets, PV post-dégâts — et vérifier qu'un squelette n'est pas converti.
5. Vérifier l'audition des sons vanilla et mesurer le framerate avec plusieurs beams secondaires dans la scène réelle.
