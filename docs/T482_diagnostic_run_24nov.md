# T482 — Diagnostic du run du 24 novembre 2026 (scan-20261124-043601)

Build testé : `45ce18e`. Monde `TEST2 (1)`, graine `3668398517386263602`.
Demande `/dlbtest crimsonlake` à `126,140,58`, centre retenu `[126, 45, 342]`, sol de référence **Y = 67**.
Rapports : `rapports_scan_20261124_TEST3/` (13 fichiers, 20 Mo).
Page 3D : `deliverables/scan3d_TEST3.html`.

---

## 0. Ce qui a MARCHÉ dans ce run

Deux correctifs antérieurs sont confirmés en jeu pour la première fois :

| Point | Preuve |
|---|---|
| **T480 — jalon « pendant la pose »** | `P2_PENDANT_POSE` est à **t+24,9 s**, le clear à t+20,6 s, la pose finie à t+26,1 s. Les trois captures ont des contenus différents. Le doublon est mort. |
| **T479 — le talus tourne** | L'étape s'appelle `2_APRES_CLEAR_ET_TALUS` et **16 100 colonnes sur 27 976** de l'anneau (57,5 %) ont été réécrites. Le « tablier plat » tombe à **2,5 %** (il était à 100 % au T478). |

Le talus **n'est donc plus débranché ni expiré**. C'est un fait mesuré, pas une déduction.

---

## 1. Reproche n°3 — « le trou n'a pas troué tout l'espace, l'ancien terrain pénètre ma structure »

**C'est exact, et c'est la faute la plus grave. Cause trouvée, vérifiée dans le code ET dans le NBT.**

### Le mécanisme

```java
// l.994  — la base de la structure
final BlockPos lakeMin = finalOrigin.offset(0, minRelativeY, 0);   // Y = 48

// l.1014 — le plancher du creusement
clearBuildVolume(level, lakeMin, lakeMax, groundY + 1, ...)        // Y = 68

// l.1871 — ce que le clear applique réellement
int yBot = Math.max(Math.max(min.getY(), clearFloorY), ...);       // max(48, 68) = 68
```

La structure commence à **Y = 48** (`groundY + 1 − SINK_BLOCKS`, avec `SINK_BLOCKS = 20`).
Le creusement commence à **Y = 68**.
**Les 20 niveaux Y 48→67 ne sont JAMAIS creusés.**

Le commentaire du code assume ce choix :

> « T93 : le sous-sol naturel est conservé, le bassin est creusé par la matière même du template »

**Cette phrase est impossible à tenir.** Le filtre de pose, l.750 :

```java
if (info.state().isAir() || isLaggy(info.state())) continue;
```

La pose **saute l'air**. Et le fichier `crimsonlake.nbt` ne contient **aucun état d'air dans sa palette** (vérifié : 366 états, 0 air, 1 366 835 blocs pour une boîte 246×236×284). Le template est *creux par absence*, pas par de l'air explicite.

> **Une pose qui saute l'air ne peut rien creuser. Elle ne sait qu'ajouter de la matière.**
> Le bassin ne sera donc jamais excavé par le template, contrairement à ce que le commentaire T93 affirme depuis des mois.

### Le chiffrage

Dans les 20 couches sous le plancher de creusement (monde Y 48→67) :

| | cellules | part |
|---|---|---|
| total | 1 397 280 | 100 % |
| écrites par la pose | 807 242 | 57,8 % |
| **laissées en ancien terrain** | **590 038** | **42,2 %** |

Et sur l'ensemble du gabarit : **26 447 colonnes sur 69 864 (37,9 %) ne reçoivent jamais le moindre bloc.** C'est le « un tiers » que vous décrivez — le chiffre est remarquablement juste.

### Ce que j'ai cru à tort en chemin

J'ai d'abord cherché l'ancien terrain **en surface**, par enclavement : une colonne intacte entourée d'ouvrage des quatre côtés. Résultat : **25 colonnes, enfoncement 1 bloc**. Rien.

Cette recherche était vaine par construction : l'intrusion est **sous la surface**, dans les 20 couches du bassin. Un relevé de hauteurs (heightmap) ne voit que le sommet de chaque colonne — **il est structurellement aveugle à ce défaut**. Les 13,7 % de colonnes « intactes » que je mesure dans la boîte sont en bordure, pas l'intrusion.

### Le correctif n'est pas une ligne

Baisser simplement le plancher à `lakeMin.getY()` serait **pire** : cela creuserait le **rectangle entier** de 246×284 sur 20 blocs de profondeur, y compris les 26 447 colonnes où le lac n'a aucun bloc — soit une fosse rectangulaire béante autour du lac. Il faut un creusement **masqué par colonne**, à la profondeur du bloc le plus bas du gabarit dans cette colonne. Or le T469 a justement supprimé le masque de colonnes (« aucun masque de colonnes »). C'est donc un arbitrage, pas une correction évidente — voir § 4.

---

## 2. Reproche n°2 — « des limites droites de trou extérieur vers intérieur »

**Exact. Cause : le creusement est un parallélépipède, et ses angles sortent de la portée du talus.**

### Ce que ce n'est PAS (hypothèses testées et écartées)

| Hypothèse | Mesure | Verdict |
|---|---|---|
| Le contour de la pose est rectiligne | plus longue ligne droite : **13 blocs** | **faux** — le contour est organique |
| Les bords suivent la grille des chunks (16) | marches alignées : **6,8 %** contre 6,25 % attendu au hasard | **faux** |
| Les falaises viennent des chunks non chargés au relevé | taux plat : 12,5 % / 14,1 % / 19,3 % / 18,4 % selon la distance | **faux** — aucune corrélation |

### Ce que c'est

`clearBuildVolume(level, lakeMin, lakeMax, ...)` rase un **rectangle**. Là où ce rectangle tranche une colline, il laisse une **paroi verticale parfaitement droite**. Deux segments relevés dans ce run :

| Bord | Longueur | Abaissement moyen | Max | Distance à l'ouvrage |
|---|---|---|---|---|
| horizontal `z = 201`, x 214→249 | **36 blocs** | **18,6 blocs** | 26 | **61 à 91 blocs** |
| vertical `x = 4`, z 377→417 | **41 blocs** | 5,0 blocs | 12 | 5 à 8 blocs |

Le mur de `z = 201` est le cas d'école : une falaise droite de 36 blocs de long et 18 à 26 blocs de haut, située à **61–91 blocs** de l'empreinte du lac. Or la rampe du talus vaut `TALUS_LARGEUR = 48` et l'application est gardée par :

```java
if (distance[i] >= wRampe) continue;
```

> **Le talus raccorde autour de la FORME ORGANIQUE du lac ; le clear rase un RECTANGLE.**
> Les angles du rectangle sont à ~65 blocs ou plus de la forme organique — **hors rampe**. Ils sont donc rasés nets et jamais raccordés.

Au total : **1 695 colonnes creusées sur 8 322 hors ouvrage (20,4 %) sont au-delà de la rampe et ne seront jamais adoucies**, quelle que soit la qualité du profil de raccord.

---

## 3. Reproche n°1 — « toujours pas de smooth maîtrisé en vue »

**Le talus tourne, mais il tient très loin de ce que la simulation du T479 promettait.**

Falaises (marche ≥ 4 blocs avec un voisin), état final contre terrain naturel :

| Zone | colonnes | falaises finales | terrain naturel | marche max |
|---|---|---|---|---|
| rampe écrite (≤48) | 26 652 | **15,8 %** | 27,7 % | **81** |
| au contact de l'ouvrage | 811 | **20,6 %** | 32,5 % | 81 |
| hors rampe (>48) | 96 605 | 16,3 % | 15,5 % | 49 |

Le talus **améliore** réellement (27,7 % → 15,8 %), mais la simulation du T479 annonçait **1,3 % dans la rampe et 0 % au contact**. On est à **15,8 % et 20,6 %**. Il reste **933 falaises de 20 blocs ou plus** dans la rampe.

> C'est la deuxième fois que ce piège se referme : **une simulation écrite par l'auteur de l'algorithme flatte l'algorithme**. Le banc T479 mesurait un champ de cibles calculé en mémoire ; le jeu, lui, applique ce champ à travers un budget de 40 ms/tick, une échéance de 180 s, et par-dessus un clear rectangulaire qui n'était pas dans la simulation.

**Pourquoi vous ne le voyez pas** : le sol naturel est à Y = 67 et le clear démarre à Y = 68. Sur terrain plat, **le clear n'enlève rien** et il n'y a donc **aucune fosse dans laquelle le raccord pourrait se voir**. Le talus ne devient visible que là où il y avait du relief — et précisément là, 20,4 % de la surface creusée est hors de sa portée (§ 2).

---

## 4. Les trois correctifs, et l'arbitrage qui vous revient

| # | Défaut | Correctif | Coût / risque |
|---|---|---|---|
| 3 | ancien terrain dans le bassin | creusement **masqué par colonne** jusqu'au bloc le plus bas du gabarit | réintroduit le masque de colonnes supprimé au T469 ; +0 à 1,4 M blocs effacés |
| 2 | parois droites aux angles | faire porter le talus sur la **boîte du clear**, pas sur l'empreinte organique ; ou creuser en forme organique plutôt qu'en rectangle | rampe à porter de 48 à ~96, ou masque de clear organique |
| 1 | raccord peu visible | découle de 2 et 3 : sans fosse, pas de raccord visible | — |

Le point 3 a une variante **beaucoup plus agressive** (creuser tout le rectangle à Y=48) que je **déconseille** : elle produirait une fosse rectangulaire de 20 blocs autour du lac.

---

## 5. Ce qui n'est pas validé, et ce que les rapports cachent

- **14,5 % de la boîte de l'ouvrage n'a jamais été observée.** Les tranches **z 432→462** (31 rangées, l'extrémité sud du lac) sont non mesurées à plus de 90 %. **Toute conclusion y est aveugle**, y compris les miennes.
- Le sommaire affirme « couverture : complete sur toutes les captures » alors que **48 128 colonnes (21,7 %) sont non mesurées** aux étapes 2 à 4, et **77 312 (34,8 %) à l'étape 1**. **Le rapport se contredit** — c'est le même genre de mensonge de rapport que le T478 avait déjà corrigé ailleurs.
- Le « relief théorique » est absent aux 4 étapes (calcul de fond expiré) : la comparaison au relief de génération manque toujours.
- Serveur très chargé pendant le run : `Can't keep up!` de 10 568 ms, 3 890 ms, 2 408 ms. Les durées mesurées ne sont pas représentatives d'un serveur sain.
- 3 avertissements `Tried to load a DUMMY block entity` à (93,70,463), (90,70,462), (92,70,461) — toujours pas traités.

---

## 6. Un défaut que j'ai introduit moi-même, et qui n'a pas été vu pendant deux tours

Au **T480**, j'ai écrit la copie miroir de `DevTerrainScan.java` à

```
FIXES_MCREATOR/deepluckyblock/dev/DevTerrainScan.java          <-- chemin que personne ne lit
```

au lieu de

```
FIXES_MCREATOR/src/main/java/deepluckyblock/dev/DevTerrainScan.java   <-- chemin canonique
```

L'emplacement canonique est donc resté **périmé** (sans le correctif T480) pendant les tours T480 et T481, et j'ai annoncé « miroir : 0 divergence sur 227 » aux deux tours. **Le contrôle que j'utilisais appliquait la même convention de chemin erronée que ma faute** : il ne pouvait structurellement pas la voir.

Corrigé dans ce tour : fichier remis à l'emplacement canonique, doublon supprimé, et nouveau banc **`tools/mirrorcheck.py`** qui vérifie *deux* choses — l'identité des contenus **et** le respect de la convention de chemin. Les deux sabotages (rejeu exact de la faute T480 ; contenu divergent) sont mordus.

> **Leçon, à ajouter aux précédentes : un contrôle qui partage la convention de l'erreur qu'il doit détecter confirme l'erreur au lieu de la signaler.**
