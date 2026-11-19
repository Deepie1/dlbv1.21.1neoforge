# Telechargement

## ⬇️ L'archive — c'est le seul lien

https://github.com/Deepie1/dlbv1.21.1neoforge/raw/5592dd1da30e252237ebd0a824242a48bf08463a/deliverables/DLB_src.zip

`DLB_src.zip` · 116 fichiers · 1 971 438 octets
<br>SHA-256 `abc8af9eafa20a2ecc36f6415cc9c3644d0d1c3780e60ce726a6f2b5266259d4`

**Il n'y a plus qu'une seule archive et un seul lien.** `DLB_src.zip` porte toujours le
même nom et contient à chaque fois **tous** les fichiers à jour. L'adresse contient le
numéro de la livraison (`5592dd1…`) : c'est ce qui garantit que vous recevez la dernière
version et jamais une copie en cache.

---

## 1. Pourquoi vous voyiez les compétences par deux

Le coupable n'est pas le tirage au sort : c'est la **façon de reconnaître** une
compétence sur une arme.

Pour savoir quelles compétences afficher, le mod demandait « quelles animations cette
arme déclenche-t-elle ? ». C'est une question **visuelle**, et sa réponse est
volontairement large : pour que les armes brillent joliment, **24 enchantements vanilla**
sont rattachés à une animation. Tranchant et Impaling allument l'éclat `RAZOR_GLEAM`,
Aura de feu et Flamme allument la vague de feu, Raccommodage et Solidité allument le halo
Chrono, Butin et Fortune le sigle de ralliement, Toile la glace…

Le mod prenait cette réponse pour un inventaire de **pouvoirs**. Résultat : une épée
lootée parfaitement ordinaire, avec **Tranchant + Aura de feu**, se voyait créditer de
**deux compétences qu'elle ne possédait pas** — et Tranchant se trouve sur presque toutes
les épées, d'où le « par deux » quasi systématique. Voici la mesure, avant et après :

| L'arme | Ce que vous voyiez | Ce que vous verrez |
|---|---|---|
| Épée lootée ordinaire (Tranchant + Aura de feu) | `sharp_edge` **et** `fiery_sweep` | *aucune* |
| Épée à compétence + Raccommodage | `thorn_maw` **et** `chrono_mending` | `thorn_maw` |
| Épée à compétence, seule | `thorn_maw` | `thorn_maw` |
| Épée avec Toile de soie | `heavy_slow_hit` | *aucune* |

Désormais le mod part des **enchantements du mod réellement posés sur l'arme**. Les
surnoms internes du mod (`briar_cage`, `thorn_maw`… qui désignent la même animation)
continuent d'être reconnus ; les enchantements vanilla, eux, ne fabriquent plus de
compétence imaginaire.

**Le même défaut touchait le lanceur.** Une épée n'ayant que Raccommodage était traitée
comme une arme à compétence : elle recevait le halo et pouvait lancer une signature au
clic droit sur un mob. Le halo, l'infobulle et le lanceur lisent maintenant **la même
méthode**, donc ils ne peuvent plus se contredire.

## 2. Le tirage : 5 %, puis 5 % à nouveau

Votre règle est maintenant écrite telle quelle : un premier tirage à **5 %** décide d'une
compétence ; **et seulement s'il a donné**, un **second tirage indépendant à 5 %** peut en
ajouter une deuxième. Deux compétences valent donc 5 % × 5 % = **1 chance sur 400**, et
jamais davantage. Chaque compétence paie **son propre troc** (deux enchantements perdus,
ou tout le monde qui redescend de 0 à 3 niveaux).

## 3. Pourquoi le glint ne changeait « pas du tout »

Le code qui pose le halo « compétence prête » était bien écrit, bien appelé, et il
s'exécutait : il posait la couleur de l'élément et le palier 13. Mais **le serveur gardait
ce changement pour lui**.

En 1.21, les données d'un objet vivent dans un composant. La fonction utilisée renvoie la
compound **vivante** de ce composant, et le menu d'inventaire garde, pour repérer les
changements, un **instantané qui partage exactement la même compound**. En la modifiant
sur place, on modifiait donc l'objet **et sa propre référence de comparaison** : le
serveur comparait, ne voyait aucune différence, et n'envoyait rien. Côté client, rien ne
bougeait — jamais. Ce qui explique aussi la vieille observation « le palier 13 n'a jamais
été vu ».

C'est précisément ce qui différenciait `/anim` : cette commande pose en plus un composant
vanilla, un vrai changement visible, donc tout l'objet est renvoyé au client — couleur
comprise.

Deux corrections : on repart d'une **copie** de la compound (le composant reposé est alors
réellement neuf), et on **prévient explicitement le client** au moment où l'état bascule —
au rechargement comme au lancement. Le halo ne clignote pas pour autant : l'envoi n'a lieu
qu'au changement d'état, pas cinq fois par seconde.

---

## Ce que j'ai vérifié, et ce que je n'ai pas pu vérifier

Vérifié : `semcheck` (les 4 alertes du fichier de butin sont **antérieures**, prouvé en
remisant mes modifications), `typecheck` (24 alertes connues ; l'alerte « nouvelle » est
un faux positif déjà listé, simplement descendu de la ligne 3338 à 3354), `floatcheck`
(221 fichiers, 0), un banc qui rejoue l'ancienne et la nouvelle reconnaissance sur la
vraie table d'alias du mod (c'est le tableau ci-dessus), et **13 relectures faites dans
l'archive elle-même**, pas dans mon brouillon.

Non vérifié, et je le dis clairement : `javac` n'existe pas dans cet environnement, donc
**la compilation n'est pas prouvée**, et je ne peux pas lancer le jeu — **le rendu du halo
à l'écran reste à confirmer par vous**. Si le halo ne basculait toujours pas, la prochaine
piste serait le cache de couleur de 150 ms côté client, et non la synchronisation.
