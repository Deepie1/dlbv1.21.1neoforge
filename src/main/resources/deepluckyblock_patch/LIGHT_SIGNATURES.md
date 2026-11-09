# Lumière — trois signatures et nettoyage du log

## Fichiers à conserver synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

- `client/LightSignatureVfx.java` — géométrie des trois animations ;
- `client/WeaponWorldVfxManager.java` — durées, double passe alpha/additive et `/anim light` côté client ;
- `enchantments/WeaponVfxServerListener.java` — dégâts serveur, protections, sons et rotation ;
- `commands/DlbVfxCommand.java` — commande `/anim light` ;
- `resources/deep_lucky_block.mixins.json` — suppression du refmap inexistant ;
- `resources/licenses/pixel_musketeer.txt` — licence déplacée hors du resource pack ;
- `resources/assets/deep_lucky_block/textures/item/lunettesdupatron.png` — texture 256×256.

`WeaponVisualEffectId.java` ne change pas : les titres et mappings Lumière existaient déjà.

> **Suppression manuelle lors d'une copie par-dessus un ancien projet :** supprimer `src/main/resources/assets/deep_lucky_block/font/PIXEL_MUSKETEER_LICENSE.txt`. Un ZIP de sources ne peut pas représenter cette suppression ; la nouvelle licence est dans `src/main/resources/licenses/pixel_musketeer.txt`.

## Chorégraphie et dégâts

Les durées sont de 100 ticks pour Seraph Sky Pillar, 80 pour Astral Star Lance et 100 pour Chrono Eternal Halo.

### Seraph Sky Pillar

Six ailes de 84 plumes, douze lances descendantes, sceau au sol, puis pilier octogonal. Impact au tick 40 : 34 % de l'attaque capturée sur la cible principale, 49 % si son type appartient à `sensitive_to_smite`. Les ennemis secondaires dans le cylindre de 1,65 bloc reçoivent 18 % / 26 %. Les valeurs sont bornées. Glowing dure 100 ticks.

### Astral Star Lance

Une constellation forge une lance octogonale qui se déplace avec son fût, sa pointe et ses orbites. Au tick 24, un segment de 8,70 blocs centré sur la cible et orienté par l'attaque est testé par collision segment/AABB : 30 % sur la primaire, 18 % sur les ennemis traversés. Les collatéraux exigent une ligne de vue. Le corridor visible et la zone utilisent la même échelle que le rendu.

### Chrono Eternal Halo

Trois plans orthogonaux, deux couronnes de 88 maillons par plan, aiguilles à détente et éclats rembobinés. Les ennemis non-boss reçoivent Lenteur II ; Warden, Wither et Ender Dragon gardent leur contrôle. La dette unique arrive au tick 55 : 38 % sur la primaire, 16 % dans 1,55 bloc.

Tous les dégâts différés passent par `hurtWithCapturedWeapon` : crédit du tueur, armure/protections et verrou anti-récursion. Les touches secondaires excluent l'attaquant, les joueurs et les non-ennemis. Aucun `setNoAi`, déplacement forcé ou nouveau hold n'est ajouté par la Lumière.

## Corrections du log prises en charge par le mod

- Suppression de la propriété `refmap` qui pointait vers un fichier jamais généré.
- Suppression du chemin resource-pack invalide `font/PIXEL_MUSKETEER_LICENSE.txt` ; la licence reste distribuée sous `licenses/`.
- Agrandissement Lanczos de la texture des lunettes de 169×169 à 256×256, sans recadrage, pour permettre les mipmaps.
- Niveau de log du client MCreator ramené de `DEBUG` à `INFO` dans `build.gradle`.

Ces changements ne prétendent pas réparer les diagnostics appartenant à vanilla ou à NeoForge : ambiguïté de `/teleport`, schéma d'URL des assets de développement, sons de corne de chèvre, shader `rendertype_entity_translucent_emissive`/`Sampler2` et contenu généré de `run/config/fml.toml`. Aucun shader vanilla n'est remplacé par ce patch.

## Vérification utilisateur

Le workspace ne contient pas le JDK/Gradle/MCreator nécessaire à une compilation NeoForge. Après copie des sources et miroirs, lancer `runClient` sous Windows/MCreator, puis `/anim light`. Les trois signatures tournent dans l'ordre sur les grandes animations de l'épée. L'aperçu HTML hors ligne et les audits statiques ne remplacent pas ce test en jeu.
