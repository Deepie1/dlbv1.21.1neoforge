# Lumière V2 — trois signatures et nettoyage du log

## Fichiers à conserver synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

- `client/LightSignatureVfx.java` — géométrie des trois animations V2 ;
- `client/WeaponWorldVfxManager.java` — durées, double passe alpha/additive et `/anim light` côté client ;
- `enchantments/WeaponVfxServerListener.java` — dégâts serveur, protections, sons et rotation ;
- `commands/DlbVfxCommand.java` — commande `/anim light` ;
- `resources/deep_lucky_block.mixins.json` — suppression du refmap inexistant ;
- `resources/licenses/pixel_musketeer.txt` — licence déplacée hors du resource pack ;
- `resources/assets/deep_lucky_block/textures/item/lunettesdupatron.png` — texture 256×256.

`WeaponVisualEffectId.java` ne change pas : les titres et mappings Lumière existaient déjà. Les comportements Metal et Arc ne sont pas modifiés par cette V2.

> **Suppression manuelle lors d'une copie par-dessus un ancien projet :** supprimer `src/main/resources/assets/deep_lucky_block/font/PIXEL_MUSKETEER_LICENSE.txt`. Un ZIP de sources ne peut pas représenter cette suppression ; la nouvelle licence est dans `src/main/resources/licenses/pixel_musketeer.txt`.

## Chorégraphie et dégâts

Les durées sont de 100 ticks pour Seraph Sky Pillar, 80 pour Astral Star Lance et 100 pour Chrono Eternal Halo. Les trois rendus emploient des primitives cubiques/translucides, une passe alpha et une passe additive distincte, ainsi que des contours de cubes dont les douze arêtes sont animées.

### Seraph Sky Pillar — rayon céleste continu

L'ancienne conception à ailes, plumes, lances et grand sceau est entièrement supprimée. Un fin noyau blanc tombe du ciel pendant les 12 premiers ticks, entouré de trois filaments. Le rayon actif possède une enveloppe cylindrique translucide à douze faces, 48 niveaux légers, 18 paquets descendants et 26 motes basses. La diffusion terminale reste volontairement faible : 24 rayons au sol, au maximum environ 1,55 bloc, purement décoratifs.

**Le rayon est la zone de dégâts.** Le serveur effectue une nouvelle recherche d'entités toutes les 4 ticks, aux ticks **12, 16, …, 92**, soit **21 pulses**. Il ne conserve aucune liste de victimes figée à l'activation :

- entrer dans le rayon après son apparition expose dès le prochain pulse ;
- rester dedans maintient les dégâts ;
- en sortir interrompt immédiatement les pulses suivants ;
- plusieurs ennemis peuvent être touchés simultanément dans le même rayon.

Chaque pulse applique 6 % de l'attaque capturée, borné entre 0,04 et 0,55 point de dégâts, ou 9 %, borné entre 0,06 et 0,80 point, contre les types `sensitive_to_smite`. Glowing est rafraîchi pour 10 ticks après une touche réussie. Aucun knockback n'éjecte artificiellement les cibles.

Le contour visuel atteint 0,48 + 0,09 = 0,57 bloc ; le cylindre serveur utilise **0,58 bloc**, multiplié par l'échelle visuelle, puis mesure la distance horizontale à l'AABB réelle de chaque occupant. Verticalement, la zone suit le rayon de −1,17 à +9,51 blocs (recouvrement de 3 % des prismes inclus) autour du centre capturé, avec la même échelle. Une cible n'est donc pas touchée uniquement parce qu'elle se trouvait à l'impact initial.

### Astral Star Lance — projectile astronomique détaillé

Le concept de lance et sa collision sont conservés, avec une construction beaucoup plus dense : constellation initiale, trois cages/portails octogonaux, huit nœuds gravés et reliés, fût octogonal de 34 segments, huit plaques longitudinales de neuf sections, pointe de 14 sections, trois hélices, six ailettes nervurées, six arcs orbitaux de douze voxels et un sillage de 48 éclats. À la perforation, deux fronts de 56 maillons — l'un transversal, l'autre horizontal — accompagnent la diffraction. Ces décorations n'agrandissent pas silencieusement la collision.

Au tick 24, un segment de **8,70 blocs**, centré sur la cible et orienté par l'attaque, est testé par collision continue segment/AABB : 30 % sur la primaire, 18 % sur les ennemis traversés. Les collatéraux exigent une ligne de vue. Le corridor visible et la zone utilisent la même échelle que le rendu. Le test par slabs évite le tunneling entre les deux extrémités.

### Chrono Eternal Halo — horloge, arrêt et rembobinage

L'armillaire abstraite est remplacée par une horloge frontale immédiatement identifiable :

- chiffres **1 à 12** dessinés en voxels avec une fonte 3×5 ;
- **60 graduations** et double cadran de 120 maillons ;
- trois aiguilles de longueurs et rapports de vitesse différents ;
- quatre engrenages de 12 ou 16 dents, à sens alternés ;
- pendule animé, dont la masse est un cube à contours scannés ;
- fracture en douze directions à la dette et 42 éclats de mémoire.

Les aiguilles avancent par crans. À la dette, aiguilles, engrenages et pendule s'arrêtent net ; ils repartent ensuite en arrière pendant le rembobinage. Les ennemis non-boss reçoivent Lenteur II ; Warden, Wither et Ender Dragon gardent leur contrôle. La dette unique arrive au tick 55 : 38 % sur la primaire, 16 % dans 1,55 bloc.

Tous les dégâts différés passent par `hurtWithCapturedWeapon` : crédit du tueur, armure/protections et verrou anti-récursion. Les touches secondaires excluent l'attaquant, les joueurs et les non-ennemis. Aucun `setNoAi`, déplacement forcé ou nouveau hold n'est ajouté par la Lumière.

## Corrections du log prises en charge par le mod

- Suppression de la propriété `refmap` qui pointait vers un fichier jamais généré.
- Suppression du chemin resource-pack invalide `font/PIXEL_MUSKETEER_LICENSE.txt` ; la licence reste distribuée sous `licenses/`.
- Agrandissement Lanczos de la texture des lunettes de 169×169 à 256×256, sans recadrage, pour permettre les mipmaps.
- Niveau de log du client MCreator ramené de `DEBUG` à `INFO` dans `build.gradle`.

Ces changements ne prétendent pas réparer les diagnostics appartenant à vanilla ou à NeoForge : ambiguïté de `/teleport`, schéma d'URL des assets de développement, sons de corne de chèvre, shader `rendertype_entity_translucent_emissive`/`Sampler2` et contenu généré de `run/config/fml.toml`. Aucun shader vanilla n'est remplacé par ce patch.

## Aperçu hors ligne et limites de validation

`deliverables/DLB_Light_Three_Signatures_preview.html` est autonome, sans réseau ni dépendance externe. Il permet de lire, mettre en pause, scrubber, orbiter, zoomer et comparer les trois modèles. Le ZIP `DLB_Light_Three_Signatures_src.zip` contient les sources principales, les miroirs MCreator et les correctifs de ressources.

Le workspace ne contient pas le JDK/Gradle/MCreator nécessaire à une compilation NeoForge. Les audits de syntaxe Java, les modèles numériques, les collisions simulées et Chromium ne remplacent pas une compilation ou un test en jeu. Après copie des sources et miroirs, lancer `runClient` sous Windows/MCreator, puis `/anim light`. Vérifier en particulier qu'un ennemi qui entre tardivement dans Séraphin est touché, et qu'un ennemi qui en sort cesse de l'être.
