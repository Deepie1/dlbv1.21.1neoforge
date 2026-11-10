# Vent V1 — trois signatures, un seul moteur voxel

Refonte complète de l’élément **Vent**. Jusqu’ici il n’existait pas comme famille : la lance de zéphyr
était un modèle compacté caché dans `GaiaWorldTreeVfx` (variant `ZEPHYR`), et la tempête de sable
dormait en archive dans `EarthSignatureVfx` (`SANDSTORM`, code Terre, hors rotation). Aucune des deux
n’avait de langage visuel commun : pas de ruban, pas de grain, pas de tourbillon continu.

Cette version introduit **`WindSignatureVfx`**, un moteur voxel dédié, et trois chorégraphies
distinctes partageant une identité unique — blanc glacé, azur, ardoise — avec de l’ocre réservé au
sable.

---

## 1. Fichiers synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

| Fichier | Rôle |
|---|---|
| `client/WindSignatureVfx.java` | **nouveau** — moteur du vent : Zéphyr, Tempête de sable, Œil du cyclone |
| `client/WeaponWorldVfxManager.java` | routage `impactWind`, fin du détour Gaia pour le vent, durée d’affichage 120 ticks pour `TEMPEST_EYE` |
| `enchantments/WeaponVisualEffectId.java` | identifiant `TEMPEST_EYE` (#42), alias `/dlbvfx`, gardes de rotation |
| `enchantments/WeaponVfxMotion.java` | mouvement d’écho de lame spiralé pour `TEMPEST_EYE` |
| `commands/DlbVfxCommand.java` | créneaux isolés (114 / 174 / 154 ticks) pour les trois signatures |
| `deepluckyblock_patch/WIND_SIGNATURES.md` | ce document |
| `deliverables/DLB_Wind_Three_Signatures_preview.html` | aperçu autonome (Canvas 2D) des trois chorégraphies, à jour de la v2 |

---

## 2. Identité visuelle

| Rôle | Couleur | Usage |
|---|---|---|
| `AIR_DEEP` | `#2C4658` | creux des entonnoirs, ombre des bras |
| `AIR` | `#4E7F99` | fût des lances, cordes du vortex |
| `AZURE` | `#6FBEDC` | rubans, rayons de l’œil |
| `PALE` | `#A9E2F2` | ailettes, brins secondaires |
| `GLASS` | `#D9F5FF` | cœurs lumineux, pointes |
| `WHITE` | `#F4FDFF` | arêtes, éclats d’impact |
| `SLATE` / `STEEL` | `#59636B`… `#B9C6CE` | débris arrachés au sol (cyclone) |
| `SAND_DARK` → `DUST` | `#6E5B41` → `#F0E2BF` | sable et poussière (tempête) |

Le vent ne réutilise volontairement **aucune** couleur de Terre ni de Bois : c’est ce qui rend la
famille identifiable en combat, même quand plusieurs effets se chevauchent.

---

## 3. Les trois signatures

### 3.1 Zéphyr — Lance de zéphyr (`ZEPHYR_WIND_LANCE`, #22, 80 ticks)

Chorégraphie refaite de fond en comble (v2). L’ancien trident de lances obliques à 120° était
illisible de face ; le Zéphyr est désormais un **axe vertical unique** : une colonne d’air qui
descend du ciel, une lance qui la suit et se plante dans le sol, puis une éclosion latérale.

| Phase | `t` | Contenu |
|---|---|---|
| Aspiration | 0,00 → 0,20 | trois anneaux de sol (3 × 48) se contractent de 2,34 à 0,90 bloc, la poussière monte |
| Nouage | 0,10 → 0,34 | la colonne se noue : 5 brins externes (40 niveaux) + 3 brins internes (24 niveaux), 12,5 tours |
| Chute | 0,22 → 0,46 | la lance descend de `tipY = 0,78` à `−0,86` (`easeOut`) en vibrant de ±0,02 |
| Percement | 0,44 → 0,53 | `pierce` enfonce la pointe de 0,22 bloc, anneau de compression au sol (48 + 36 + 28 éclats) |
| Éclosion | 0,50 → 0,90 | les deux hélices s’ouvrent (rayon `0,30 → 1,30`), 40 éclats et 10 plumes partent en cône |
| Dissipation | 0,84 → 1,00 | fondu global de la colonne, les éclats retombent |

Anatomie, sous-système par sous-système :

| # | Sous-système | Cubes | Détail |
|---|---|---|---|
| 1 | Anneaux de sol | 144 | 3 anneaux × 48, rayons 0,90 / 1,62 / 2,34, contre-rotation, aspiration `0,34 + 0,66·suck` |
| 2 | Colonne d’air | 272 | 5 brins × 40 niveaux (rayon 1,00 → 0,46, hauteur 3,30 → 1,34) + 3 brins × 24 (0,58 → 0,22) |
| 3 | Lame | 70 | 14 niveaux, section en losange (4 faces + arête), demi-largeur 0,016 → 0,202, `WHITE`/`GLASS` |
| 4 | Douille et fût | 158 | douille de 3 anneaux hexagonaux (18) + fût à **six pans** de 20 sections avec âme lumineuse (140), demi-largeur 0,152 → 0,110 |
| 5 | Viroles et ailettes | 46 | 5 viroles de 6 cubes de cristal (30) + 4 ailettes recourbées de 4 cubes (16) |
| 6 | Hélices et pommeau | 59 | pommeau de 7 cubes au sommet + 2 hélices contra-rotatives × 26 pas, `t × 22`, qui s’écartent de `blow·(0,80 + u·0,40)` |
| 7 | Plumes | 10 | dérive radiale `blow·(0,70 + 1,80·h)`, inclinaison sur l’angle |
| 8 | Trident satellite | 276 | 3 × 92 : lame de 24 cubes, fût à quatre pans de 45, 2 viroles de 8, 3 ailettes, hélice de 12 ; rayon `0,58·(1 − 0,55·drop)` |
| 9 | Couronne de compression | 112 | anneau `−0,55` (48) + anneau `−1,10` (36) + 28 puits, portée `0,34 + 1,75·(1 − impact)` |
| 10 | Éclats | 40 | gerbe de 40 cubes, angle d’or, montée `blow·(0,45 + 1,10·h)` |
| 11 | Brume périphérique | 12 | cubes larges très transparents à 2,05 – 2,95 blocs |
| 12 | Annelets secondaires | 72 | 4 anneaux × 18 posés le long de la colonne, `GLASS`, alpha `0,52 + 0,30·h` |

**Pic mesuré : ≈ 1 240 cubes/image** (1 231 relevés dans l’aperçu HTML à `t = 0,47`), très en dessous du
plafond `HARD_CUBE_CAP_PER_FRAME = 7000`, même en triple impact simultané.

La lance est une vraie arme : lame taillée en losange, douille, fût à six pans avec âme lumineuse,
viroles de cristal, ailettes recourbées et pommeau. Les trois satellites du trident reprennent les
mêmes pièces à échelle réduite (lame, fût à quatre pans, viroles, ailettes, hélice d’air).

### 3.2 Tempête de sable (`SANDSTORM_DUNE`, #41, 140 ticks)

Réactivée depuis l’archive Terre, réécrite pour le vent et rendue par le nouveau moteur.

| Élément | Densité | Comportement |
|---|---|---|
| Jupe de dune | 3 strates × 24 cubes | ellipse, crête asymétrique, s’affaisse en fin de vie |
| Bras spiralés | 3 × 16 | montent de −0,92 à +1,63 bloc, rayon 1,02 → 0,40, révélation décalée par niveau |
| Rubans horizontaux | 7 × 48 | sept ceintures à vitesses différentes, **toutes dans le même sens** que les bras |
| Grains individuels | 48 | advection hélicoïdale, cycle propre, extinction en fin de course |
| Voile de poussière | 12 | cubes larges très transparents qui referment l’arrière-plan |

Le tout vit entre 0,00 et 0,74, puis retombe jusqu’à 1,00 : aucune disparition sèche.
Tout le tourbillon tourne dans **un seul sens** (dune, bras, rubans, mini-tornades, grains) : plus aucun
ruban à contre-courant, l’ensemble se lit comme un seul vent.
**Pic mesuré : ≈ 990 cubes/image.**

### 3.3 Œil du cyclone (`TEMPEST_EYE`, #42, 120 ticks)

Troisième signature, nouvelle. Deux entonnoirs **contra-rotatifs** (±3 brins de 16 niveaux), un œil
net à 1,42 bloc de hauteur avec anneau et six rayons, neuf débris d’ardoise en orbite croisée, anneau
de sol de 32 segments, éclatement sommital à partir de 0,62, puis **effondrement** : les deux
entonnoirs se rejoignent et s’éteignent.
**Blocs de terrain (56)** : les mêmes blocs d’ardoise que le sol (`SLATE`, `SLATE_DARK`, `STEEL`) sont
posés au sol, happés par la tornade (`cyc < 0,16`), montent en spirale contre la paroi, culbutent
(roulis continu `2,4 + 3,6·h`), puis sont éjectés et retombent avant de recommencer. Le rayon suit la
paroi de l’entonnoir (`lerp(r0, 0,62, lift) × (1 + 0,60·sin πc)`), la vitesse angulaire augmente avec
l’aspiration.
**Pic mesuré : ≈ 960 cubes/image.**

`TEMPEST_EYE` est déclaré **en fin d’énumération** (`ordinal` 12) pour ne décaler aucun `ordinal()`
existant, qui sert de sel de rendu. Il est exclu de `signatureFor` et de `animationsOf` : la rotation
Bois reste exactement Thorn → Verdant → Zéphyr, la rotation Terre reste Seismic → Titan → Rally.
Aucun enchantement ne le déclenche encore : il est disponible via `/dlbvfx` (démo et test) et prêt
pour un futur enchantement.

---

## 4. Routage

```text
impactWood(fx)            → ZEPHYR_WIND_LANCE  → impactWind(ZEPHYR)
                          → TEMPEST_EYE        → impactWind(TEMPEST)
                          → THORN / VERDANT    → GaiaWorldTreeVfx (inchangé)
impactEarth(fx)           → SANDSTORM_DUNE     → impactWind(SANDSTORM)
                          → SEISMIC / TITAN / RALLY → EarthSignatureVfx (inchangé)
```

`impactWind` inverse la direction (les impacts transportent cible → attaquant), coupe
`voxelPulseEnabled` — sans quoi les rubans et les grains se disloquent — et transmet une graine
stable (`seed & 0x7FFFFFFF`) pour que le tirage des plumes, grains et débris ne change pas d’une frame
à l’autre.

Aucune source de buffers n’est demandée par cette classe : tous les cubes passent par
`emitVoxelCube` / `emitVoxelBoxRotated` de `WeaponWorldVfxManager`, qui possède depuis **V6.4** sa
propre source à RenderType fixes (c’est cette correction qui a supprimé le `Not building!`).

---

## 5. Gameplay, dégâts, sons — inchangés

La refonte est **purement visuelle** :

- Zéphyr conserve son immobilisation de 80 ticks, ses sons de lancement (ticks 24 / 28 / 33) et
  d’impact (33 / 37 / 42), et ses dégâts.
- La tempête de sable et l’œil du cyclone n’ajoutent ni dégât ni effet : ce sont des signatures
  visuelles, comme l’étaient les archives.
- Aucun identifiant existant ne change de numéro, de catégorie ou de priorité ; `TEMPEST_EYE` est
  ajouté avec l’identifiant libre 42.

---

## 6. Tester en jeu

```text
/dlbvfx play 22            (ou /dlbvfx demo wind_lance)   → lance de zéphyr
/dlbvfx play 41            (ou /dlbvfx demo sandstorm)    → tempête de sable
/dlbvfx play 42            (ou /dlbvfx demo tempest)      → œil du cyclone
/dlbvfx stop
```

Critères de réussite :

1. Les trois signatures sont **visibles** et ne se chevauchent pas dans la démo isolée.
2. Aucun nouveau `[DLB-VFX] World render batch aborted` ni `IllegalStateException: Not building!`.
3. La lance de zéphyr montre bien la colonne d’air verticale, le fût planté avec sa pointe de cristal
   et ses deux hélices contra-rotatives qui s’ouvrent à l’éclosion.
4. La tempête de sable reste ocre et lisible au sol ; l’œil du cyclone montre deux entonnoirs en sens
   opposés, un anneau de calme et des débris.
5. `/dlbvfx elements bois` conserve trois animations (Thorn, Verdant, Zéphyr) et `/dlbvfx elements terre`
   trois également (Seismic, Titan, Rally).

---

## 7. Limites de la vérification hors ligne

Le workspace ne contient ni JDK, ni Gradle, ni MCreator : la compilation et `runClient` restent à
faire côté atelier. Les contrôles réalisés ici valident la structure (appels et arités des émetteurs,
équilibre des blocs, palette, bornes temporelles), le rendu de l’aperçu HTML autonome, et l’intégrité
des archives. Ils ne remplacent pas un framebuffer NeoForge réel.
