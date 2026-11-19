# Telechargement

## ⬇️ L'archive — c'est le seul lien

https://github.com/Deepie1/dlbv1.21.1neoforge/raw/7617279ffae554d617da230ece75d6ed70e672b7/deliverables/DLB_src.zip

`DLB_src.zip` · 116 fichiers · 2 148 548 octets
<br>SHA-256 `2e103593f52f906a91f5a8527a13f4205389ed01d7eceefc4228b97331393906`

**Le fichier `deep_lucky_block-neoforge-1.21.1.mcreator` est dedans**, à la racine de
l'archive, avec les deux fichiers de langue.

---

## Réponse à votre question : non, il en manquait 100

Vous aviez raison de demander. Le `.mcreator` portait **2 518 traductions par langue**, et
**100 clés n'y étaient pas** :

- les **90 textes de signature** (`skill.deep_lucky_block.<nom>.1` et `.2` : thorn_maw,
  warp, tempest, chrono_mending…) ;
- les **10 clés de l'encart compétence** : `section_skills`, `skill_header`,
  `skill_named`, `cooldown_suffix` et les six `cast.*` (clic droit sur un mob, outil en
  main, canne, main vide, fusée, accroupi en sprint).

Elles vivaient **uniquement dans le code Java**, qui les pose au démarrage. Ça marchait —
mais ça ne tenait qu'à ce fichier de code. Comme c'est le `.mcreator` qui **écrase** les
traductions à la régénération, une régénération fabriquait des fichiers de langue
**sans** ces 100 clés.

C'est corrigé : **2 618 entrées par langue**, et les trois sources (le `.mcreator`,
`en_us.json`, `fr_fr.json`) sont maintenant **identiques au caractère près**.

## Un vrai défaut trouvé en chemin : « Bondissant »

En comparant les textes un par un, deux clés françaises ne concordaient pas. Le
`.mcreator` disait :

> « Vous fait rebondir à chaque atterrissage » · « exactement comme sur un bloc de slime »

alors que son **propre texte anglais** et le fichier de langue disent :

> « Le rebond suit la chute et perd de l'élan à chaque impact, jusqu'à s'arrêter. »

Le `.mcreator` gagnant, c'est la **vieille description, fausse**, qui serait partie en
jeu. Les deux clés sont alignées sur le rebond amorti.

## Les lisez-moi sont partis

Retirés de l'archive : `LISEZ-MOI-CONTENU.txt`, `LIRE-MOI-IMPORTANT.md` et le journal
`WATER_SIGNATURES.md` (237 Ko). J'ai aussi supprimé du générateur le texte qui fabriquait
le premier — 152 lignes de code devenu inutile — et retourné ses garde-fous : l'archive
**refuse** désormais de se fabriquer si un lisez-moi s'y glisse ou si le `.mcreator`
manque.

## Toujours inclus

Les correctifs de la livraison précédente : compétences qui sortaient par deux
(24 enchantements vanilla créaient des compétences fantômes), tirage à 5 % puis 5 %, et
halo « compétence prête » enfin transmis au client.

## Ce que je n'ai pas pu vérifier

`javac` n'existe pas ici : **la compilation n'est pas prouvée**. Et je ne peux pas lancer
le jeu, donc le rendu du halo à l'écran reste à confirmer par vous. Le fichier `.mcreator`
a été relu après écriture : JSON valide, 48 éléments de mod intacts, format d'origine
respecté à l'octet près.
