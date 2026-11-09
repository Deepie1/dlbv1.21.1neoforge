# Lumière V3 — pilier de lumière, constellations et rajeunissement

## Fichiers à conserver synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

- `client/LightSignatureVfx.java` — géométrie des trois animations V3 ;
- `client/WeaponWorldVfxManager.java` — durées, double passe alpha/additive, fondu propre à Séraphin et `/anim light` côté client ;
- `enchantments/WeaponVfxServerListener.java` — dégâts serveur, diamètre du pilier, rajeunissement, protections, sons et rotation ;
- `commands/DlbVfxCommand.java` — commande `/anim light` ;
- `resources/deep_lucky_block.mixins.json` — suppression du refmap inexistant ;
- `resources/licenses/pixel_musketeer.txt` — licence déplacée hors du resource pack ;
- `resources/assets/deep_lucky_block/textures/item/lunettesdupatron.png` — texture 256×256.

`WeaponVisualEffectId.java` ne change pas : les titres et mappings Lumière existaient déjà. Les comportements Metal et Arc ne sont pas modifiés par cette V3.

> **Suppression manuelle lors d'une copie par-dessus un ancien projet :** supprimer `src/main/resources/assets/deep_lucky_block/font/PIXEL_MUSKETEER_LICENSE.txt`. Un ZIP de sources ne peut pas représenter cette suppression ; la nouvelle licence est dans `src/main/resources/licenses/pixel_musketeer.txt`.

## Budget visuel et double passe

Les durées restent de 100 ticks pour Seraph Sky Pillar, 80 pour Astral Star Lance et 100 pour Chrono Eternal Halo. Les trois rendus utilisent des primitives cubiques/translucides, une passe alpha et une passe additive distincte, ainsi que des contours de cubes animés.

Les pics mesurés sur 1 001 frames de l'aperçu sont :

| Signature | Pic V2 | Pic V3 | Réduction |
|---|---:|---:|---:|
| Séraphin | 699 | **220 voxels** | −68,5 % |
| Astral | 2 342 | **533 voxels** | −77,2 % |
| Chrono | 1 084 | **799 voxels** | −26,3 % |

Ces chiffres décrivent la géométrie soumise au rendu, pas un framerate garanti en jeu. Séraphin supprime surtout les centaines de volumes translucides superposés qui causaient de l'overdraw. Astral remplace les cages et arcs chaotiques par des pièces de lame lisibles. Chrono conserve son cadran avec moins de maillons et sans cube complexe dans chaque engrenage.

## Seraph Sky Pillar — dilatation puis refermeture

La composition reprend les points communs des références fournies : une **source céleste circulaire**, une étoile inscrite, un centre blanc et une longue colonne verticale blanc/jaune. Les deux couronnes tournent en sens opposés ; huit petits motifs orbitent autour de la source. Le décor de la source ne crée aucune zone de dégâts supplémentaire.

Le rayon suit cette chorégraphie :

1. un filet blanc descend pendant les premiers ticks ;
2. à partir du tick 12, le diamètre s'ouvre progressivement pendant 20 ticks ;
3. le pilier reste largement ouvert au centre de l'animation ;
4. à partir du tick 74, il se referme pendant 20 ticks ;
5. redevenu très fin, il s'éteint avec son propre fondu terminal.

Le cœur blanc reste fin même à pleine ouverture. Huit arêtes ivoire/or, trois filaments et quatre jonctions suffisent à rendre le volume. Le bas ne comporte que douze filets et huit motes décoratifs.

### Collision et dégâts

Le serveur effectue toujours une nouvelle recherche d'occupants toutes les 4 ticks, aux ticks **12, 16, …, 92**, soit 21 pulses. Entrer tardivement expose au prochain pulse ; sortir interrompt les touches suivantes ; plusieurs ennemis peuvent partager le rayon.

**Le rayon de collision grandit et rétrécit avec le rendu.** La géométrie visible utilise un rayon de 0,10 à 0,78 bloc ; le serveur ajoute le demi-volume de 0,04 des arêtes, soit **0,14 à 0,82 bloc**, multiplié par l'échelle visuelle. La même double courbe smoothstep d'ouverture et de refermeture est implémentée des deux côtés.

Chaque pulse applique 6 % de l'attaque capturée, borné entre 0,04 et 0,55 point de dégâts, ou 9 %, borné entre 0,06 et 0,80 point, contre les types `sensitive_to_smite`. Glowing est rafraîchi pour 10 ticks après une touche réussie. Aucun knockback n'éjecte les cibles. La hauteur de collision reste de −1,17 à +9,51 blocs, recouvrement des prismes inclus.

## Astral Star Lance — vraies constellations et lame sacrée

Les anciennes cages, plaques nombreuses et arcs orbitaux sont supprimés. La nouvelle composition conserve une silhouette simple et lisible :

- **Grande Ourse** : sept étoiles et les liaisons du chariot ;
- **Cassiopée** : cinq étoiles en W ;
- **Orion** : sept étoiles, dont les trois de la ceinture ;
- trois canaux de lumière et six paquets qui convergent réellement vers la base de la lame pendant sa charge ;
- fût quadrangulaire de vingt sections et quatre rails continus ;
- lame en losange de sept sections, quatre arêtes longitudinales et pointe blanche ;
- croisée d'or à quatre ailettes angulaires, six gravures et trois hélices discrètes ;
- sillage limité à vingt étoiles et reflet circulaire au sol à l'impact.

Les constellations ne sont donc pas seulement décoratives : leur lumière forge la lance. Le reflet au sol et les huit rayons d'impact restent décoratifs et n'agrandissent pas la zone de dégâts.

Au tick 24, le segment continu de **8,70 blocs**, centré sur la cible et orienté par l'attaque, conserve exactement sa collision segment/AABB : 30 % sur la primaire, 18 % sur les ennemis traversés. Les collatéraux exigent une ligne de vue. Le corridor visible et la zone utilisent la même échelle.

## Chrono Eternal Halo — dette faible et forme bébé

L'horloge frontale conserve ses chiffres **1 à 12**, ses 60 graduations, trois aiguilles, quatre engrenages et son pendule. Les maillons du double cadran passent de 120 à 72 par couronne ; les engrenages gardent leurs dents et leurs rotations. Aiguilles, engrenages et pendule s'arrêtent à la dette puis repartent en arrière.

Les ennemis non-boss reçoivent toujours Lenteur II ; Warden, Wither et Ender Dragon gardent leur contrôle. La dette du tick 55 devient volontairement plus faible :

- **24 %** de l'attaque capturée sur la primaire, borné entre 0,12 et 2,00 points ;
- **10 %** sur les touches secondaires dans 1,55 bloc, borné entre 0,06 et 1,10 point.

### Rajeunissement sans remplacement d'entité

Après une touche réussie, si la cible est encore vivante et possède une forme bébé vanilla prise en charge, **la même entité** reçoit cette forme :

- `Zombie`, donc également Husk, Drowned, ZombieVillager et ZombifiedPiglin ;
- `Piglin` ;
- `AgeableMob`, pour les créatures vanilla/mod compatibles avec cette API.

Il n'y a aucun `spawn`, aucune suppression et aucune copie approximative d'inventaire. L'UUID, les objets en main, l'armure, les effets, la persistence et les autres données de l'entité restent donc attachés à elle. La santé est lue **après les dégâts**, puis explicitement restaurée à cette valeur, bornée par le maximum courant : rajeunir ne soigne pas et n'inflige pas une seconde dette.

La cible rajeunie reçoit aussi **Faiblesse I pendant 105 ticks**. Les règles vanilla propres à chaque forme restent actives : un bébé zombie garde son comportement de bébé zombie, et une créature `AgeableMob` peut grandir selon son âge vanilla.

**Skeleton n'a pas de forme bébé vanilla.** Il n'est donc pas transformé, pas plus que les boss ou les types sans API bébé. Aucun faux modèle ou remplacement risqué n'est fabriqué.

Tous les dégâts différés passent par `hurtWithCapturedWeapon` : crédit du tueur, armure/protections et verrou anti-récursion. Les touches secondaires excluent l'attaquant, les joueurs et les non-ennemis. Aucun `setNoAi`, déplacement forcé ou nouveau hold n'est ajouté par la Lumière.

## Corrections du log prises en charge par le mod

- Suppression de la propriété `refmap` qui pointait vers un fichier jamais généré.
- Suppression du chemin resource-pack invalide `font/PIXEL_MUSKETEER_LICENSE.txt` ; la licence reste distribuée sous `licenses/`.
- Agrandissement Lanczos de la texture des lunettes de 169×169 à 256×256, sans recadrage, pour permettre les mipmaps.
- Niveau de log du client MCreator ramené de `DEBUG` à `INFO` dans `build.gradle`.

Ces changements ne prétendent pas réparer les diagnostics appartenant à vanilla ou à NeoForge : ambiguïté de `/teleport`, schéma d'URL des assets de développement, sons de corne de chèvre, shader `rendertype_entity_translucent_emissive`/`Sampler2` et contenu généré de `run/config/fml.toml`. Aucun shader vanilla n'est remplacé par ce patch.

## Aperçu hors ligne et limites de validation

`deliverables/DLB_Light_Three_Signatures_preview.html` est autonome, sans réseau ni dépendance externe. Il permet de lire, mettre en pause, scrubber, orbiter, zoomer et comparer les trois modèles. Le ZIP `DLB_Light_Three_Signatures_src.zip` contient les sources principales, les miroirs MCreator et les correctifs de ressources.

Le workspace ne contient pas le JDK/Gradle/MCreator nécessaire à une compilation NeoForge. Les audits de syntaxe Java, les modèles numériques, les collisions simulées et Chromium ne remplacent pas une compilation ou un test en jeu. Après copie des sources et miroirs, lancer `runClient` sous Windows/MCreator, puis `/anim light`.

Tests prioritaires en jeu : vérifier le diamètre de Séraphin au début, au milieu et à la fin ; faire entrer/sortir un ennemi du rayon ; inspecter un zombie équipé après Chrono pour confirmer qu'il est bébé avec les mêmes PV restants et le même équipement ; vérifier qu'un squelette n'est pas converti.
