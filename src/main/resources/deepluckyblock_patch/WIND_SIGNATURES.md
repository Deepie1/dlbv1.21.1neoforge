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

Trois lances d’air comprimé, décalées de 120°, qui convergent vers la cible et la percent.

| Phase | `t` | Contenu |
|---|---|---|
| Ouverture | 0,00 → 0,18 | vortex de trois bras spiralés autour de la cible, anneau de sol qui balaie |
| Convergence | 0,16 → 0,42 | chaque lance passe de 3,60 blocs en arrière à 0,42 bloc devant la cible ; l’écart latéral tombe de 1,62 à 0,06 |
| Percement | 0,42 → 0,52 | pointe de cristal enfoncée, anneau de compression (24 segments) + 12 poussières d’air |
| Dissipation | 0,50 → 0,94 | les rubans hélicoïdaux sont emportés vers l’extérieur (rayon 0,20 → 1,30), trois plumes dérivent par lance |

Anatomie d’une lance : fût de **9 sections** jointives (demi-largeur 0,088 → 0,058), âme lumineuse à
44 %, **quatre ailettes** arrière, pointe de **cinq sections** (0,140 → 0,008), **ruban hélicoïdal** de
16 pas tournant à `t × 26`, trois plumes arrachées. Les trois lances, leurs rubans et les plumes sont
les seules pièces qui bougent : le vortex, lui, tourne autour d’un centre fixe.

### 3.2 Tempête de sable (`SANDSTORM_DUNE`, #41, 140 ticks)

Réactivée depuis l’archive Terre, réécrite pour le vent et rendue par le nouveau moteur.

| Élément | Densité | Comportement |
|---|---|---|
| Jupe de dune | 3 strates × 24 cubes | ellipse, crête asymétrique, s’affaisse en fin de vie |
| Bras spiralés | 3 × 16 | montent de −0,92 à +1,63 bloc, rayon 1,02 → 0,40, révélation décalée par niveau |
| Rubans horizontaux | 4 × 26 | quatre ceintures à vitesses différentes, elles « fouettent » la scène |
| Grains individuels | 48 | advection hélicoïdale, cycle propre, extinction en fin de course |
| Voile de poussière | 12 | cubes larges très transparents qui referment l’arrière-plan |

Le tout vit entre 0,00 et 0,74, puis retombe jusqu’à 1,00 : aucune disparition sèche.

### 3.3 Œil du cyclone (`TEMPEST_EYE`, #42, 120 ticks)

Troisième signature, nouvelle. Deux entonnoirs **contra-rotatifs** (±3 brins de 16 niveaux), un œil
net à 1,42 bloc de hauteur avec anneau et six rayons, neuf débris d’ardoise en orbite croisée, anneau
de sol de 32 segments, éclatement sommital à partir de 0,62, puis **effondrement** : les deux
entonnoirs se rejoignent et s’éteignent.

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
3. La lance de zéphyr montre bien ses trois fûts, ses rubans qui tournent et ses plumes emportées.
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
