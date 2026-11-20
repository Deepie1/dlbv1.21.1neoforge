# Telechargement

## ⬇️ L'archive — c'est le seul lien

https://github.com/Deepie1/dlbv1.21.1neoforge/raw/37d3158b6b0fd69e63c891557821f74453086b69/deliverables/DLB_src.zip

`DLB_src.zip` · 118 fichiers · 2 159 750 octets
<br>SHA-256 `7b0cfeae13f8db887cfaa42ab7740ac98dbeddea1fa32b7ce6098d44643e66fb`

Le fichier `deep_lucky_block-neoforge-1.21.1.mcreator` est dedans, à la racine.

---

## Vos deux règles, écrites telles quelles

### Le skill au drop

**5 % pour un skill d'élément aléatoire.** Je tire **l'élément d'abord**, puis une signature
à l'intérieur de cet élément. Ce n'est pas un détail : tirer directement parmi les
45 signatures ne donne pas le même résultat, parce que les éléments n'en portent pas le
même nombre — les mieux fournis sortiraient bien plus souvent que les autres.

**Le troc a changé sur les deux branches**, parce qu'il ne disait pas ce que vous aviez
demandé :

| | Avant | Maintenant |
|---|---|---|
| Troc 1 | exactement **2** enchantements retirés | **0 à 3**, tirés au hasard |
| Troc 2 | **une seule** valeur 0-3 appliquée à tout le monde | **chaque** enchantement a son propre tirage 0-3 |

Le second tirage indépendant à 5 % est conservé : deux skills = 1 chance sur 400.

### Le slash visuel

- **Au drop : 7 %** des armes y ont droit, **à condition** de porter un enchantement lié à
  un élément — sans élément, le slash n'aurait aucune couleur à prendre.
- **Au coup : 6 %**, et le coup qui porte le slash frappe **+10 %**.
- Le slash sort **à la couleur de l'élément** de l'arme.

**Un seul tirage décide des deux.** L'image et le bonus de dégâts sortent de la même ligne,
côté serveur. Si je les avais séparés, vous auriez vu des slashs sans bonus et encaissé des
bonus sans slash — invérifiable en jouant.

**Ce que j'ai dû retirer pour que la règle tienne :**

- le **client** sortait un slash à **chaque swing** de toute arme à aura, sans aucun bonus
  de dégâts. Il ne le fait plus que pour les épées de démonstration `/anim` ;
- côté serveur, un ancien jet donnait **+5 % sur 23 %** des coups, sans aucun visuel. Jet et
  constantes supprimés ;
- deux blocs de slash devenus inatteignables, supprimés aussi.

## La mesure, pas la promesse

Banc statistique sur **400 000 tirages**, avec les pourcentages **relus dans le code** :

| Mesure | Résultat | Attendu |
|---|---|---|
| Au moins un skill | 4,97 % | 5 % |
| Deux skills | 0,26 % | 0,25 % |
| Slash, arme autorisée | 5,97 % des coups | 6 % |
| Slash, arme ordinaire | 0,00 % | 0 % |
| Dégâts du coup porteur | +10 % | +10 % |

## Un piège évité de justesse

`DeepNbt.java` portait la nouvelle clé `dlbSlashVisual`… et n'était **pas** dans la liste
des fichiers livrés par le générateur. Il serait parti modifié mais absent : le serveur
aurait cherché une méthode inexistante. C'est la **cinquième** fois que ce piège se
présente ; le contrôle croisé est désormais systématique avant chaque archive.

## Ce que je n'ai pas pu vérifier

`javac` n'existe pas ici : **la compilation n'est pas prouvée**. Et je ne peux pas lancer le
jeu : le rendu du slash à l'écran et la bascule du halo restent à confirmer par vous.
