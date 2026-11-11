# Vent — trois signatures, un seul moteur voxel (v7)

Refonte complète de l’élément **Vent**. Jusqu’ici il n’existait pas comme famille : la lance de zéphyr
était un modèle compacté caché dans `GaiaWorldTreeVfx` (variant `ZEPHYR`), et la tempête de sable
dormait en archive dans `EarthSignatureVfx` (`SANDSTORM`, code Terre, hors rotation). Aucune des deux
n’avait de langage visuel commun : pas de ruban, pas de grain, pas de tourbillon continu.

Cette version introduit **`WindSignatureVfx`**, un moteur voxel dédié, et trois chorégraphies
distinctes partageant une identité unique — blanc glacé, azur, ardoise — avec de l’ocre réservé au
sable.

---

## 0bis. v7 — les six reproches du second test en jeu

| Reproche | Correction | Où |
|---|---|---|
| « aucun mob du rayon ne se fait prendre par le cyclone » | le cyclone n'est plus une **liste figée** de victimes mais une **zone d'aspiration** (`WindZone`) de **4,60 blocs** qui vit 170 ticks : à chaque tick elle happe toute créature vivante présente dans son cylindre — la cible frappée **et** tout son entourage, y compris celles qui entrent en cours de route. Principe des mods de tornade : aspiration zonale, pas ciblage unique. | `WeaponVfxServerListener` (`WindZone`, `captureWindZone`, `tickWindZones`) |
| « … et toujours rien » | **cause réelle trouvée** : une emprise au sol encore active (séisme / titan, ou le clouage du Zéphyr) **rappelait le mob à sa position d'origine à chaque tick**, juste après la téléportation de la tornade. Le vent **prime** désormais : `MOVEMENT_HOLDS.remove(id)` à la capture et garde explicite dans `onSignatureHoldTick`. | idem |
| « le pull ne ressemble pas à celui des mods de tornade » | la position sur l'hélice est garantie par `teleportTo`, et la **vitesse** est posée sur la **tangente** de l'hélice (delta entre le point du tick et celui du tick suivant) avec `hurtMarked = true` : aspiration + rotation + montée lissées côté client, au lieu d'un à-coup sec par tick. | `tickWindHold` |
| « les blocs emportés sont cappés au milieu » | `WindTerrainVfx` : la montée atteignait `0,55 + 0,45·taille` de la hauteur (≈ 65-70 %) et le palier haut ne durait que 4 % du cycle. Désormais **`peak = 0,70 + 0,30·h` puis `dy = rise·H·peak·(0,94 + 0,06·taille)`** : chaque bloc a son propre plafond entre 70 % et 100 % de la tour, donc la colonne est garnie sur **toute** sa hauteur au lieu de former un couvercle. Palier haut allongé (`rise` jusqu'à 0,74 du cycle, chute à partir de 0,88) et **sommet plus large** que la base (`radius1 = max(orbit·1,45 ; 1,55 + h3·1,35)`). | `WindTerrainVfx` + aperçu |
| « `/anim` : des épées en netherite » | les sept épées de démonstration sont en **bois** (`Items.WOODEN_SWORD`). | `DlbVfxCommand` |
| « glint = couleur de l'élément » | la couleur forcée `dlbGlintForce` (même clé que `/glint <couleur>`) est posée à la création de l'épée + `ENCHANTMENT_GLINT_OVERRIDE = true`, donc le halo est visible **sans aucun enchantement** et prend la teinte de l'élément : feu `#FF6A2A`, vent `#9FF2DC`, terre `#9FC25E`, métal `#C4C8CD`, lumière `#FFD75E`, poison `#46C24C`, nature `#74C34A`. | idem |
| « doublons `earth`/`terre`, titres en français » | nouvelle source unique **`util/AnimFamily`** : sept noms **anglais canoniques** — `fire`, `wind`, `earth`, `metal`, `light`, `poison`, `nature` —, un seul par famille (plus aucun doublon dans l'autocomplétion ni dans l'aide), titres affichés en anglais. Les anciennes valeurs NBT (`feu`, `vent`, `terre`, `gaia`) restent **lues** par les épées déjà distribuées. | `util/AnimFamily` + `DlbVfxCommand` + `WeaponWorldVfxManager` + `WeaponVfxServerListener` |

Le lâcher applique en plus un **souffle final** (derniers dégâts de tornade, confusion, lenteur) juste
avant la chute libre : dégâts de tornade **et** dégâts de chute, comme demandé.

---

## 0. v6 — les six reproches faits en jeu, corrigés

Tous les points relevés après le test en jeu du 11/11 sont traités, dans cet ordre :

| Reproche | Correction | Où |
|---|---|---|
| « `/anim vent` fait des slash de Gaia et de Terre » | le slash du vent a **sa propre lame** : `renderVoxelSwingSlash` détecte les trois signatures de vent et impose `0x9FF2DC` / `0x5FC0AC` / `0xEAFFF8` au lieu des teintes de l'élément porteur (Bois pour Zéphyr et Cyclone, Terre pour Sable) | `WeaponWorldVfxManager` |
| « slash très clairs bleu-vert et légèrement transparents » | opacité du slash du vent **152/255** (≈ 60 %), rayon ×1,24, épaisseur ×0,62, balayage jusqu'à 172°, et le matériau du vent passe en verre (`cubeTex = 0,05`) au lieu de l'écorce (0,19) ou du grain de sable (0,22) | idem |
| « les tornades ne propulsent pas du tout dans l'air » | montée de l'emprise **4,20 → 10,50 blocs** ; toute créature vivante est happée (la cible d'entraînement est un **villageois** en mode Paisible, donc pas une `Enemy` : c'était la cause du « rien ne se passe ») | `WeaponVfxServerListener` |
| « ça relâche trop vite » | emprise **104 → 170 ticks** (8,5 s), visuel du cyclone **120 → 170 ticks** pour que l'entonnoir vive jusqu'au lâcher | idem + `WeaponWorldVfxManager` |
| « une vraie tornade prend, fait monter, puis lâche tout en haut dans un sens aléatoire » | au sommet, l'éjection part dans une **direction aléatoire** (angle tiré par `RandomSource`, force 1,45–2,00, +0,95 vertical) : plus la tangente de l'orbite. `fallDistance` reste remise à zéro à cet instant, donc les dégâts de chute vanilla s'ajoutent aux dégâts de tornade | `WeaponVfxServerListener` |
| « les tornades sont beaucoup trop petites » | hauteur visible multipliée par ~2,3 : cyclone **8,90 blocs**, sable **7,90**, lances qui tombent de **+8,60** | `WindSignatureVfx` |
| « la tornade de sable a la partie intérieure à l'envers » | entonnoir extérieur **0,45 → 2,55** et tourbillon interne **0,22 → 1,27** : étroit au sol, large en haut, les deux dans le **même sens** ; l'interne garde ses blocs sombres et opaques (`SAND_DEEP`, 0,86–1,00 d'alpha) | idem |
| « la lance ne part pas d'assez haut et on dirait des flèches » | nouvelle lance : **chute depuis +8,60 blocs**, tête facettée de 14 niveaux (base large 0,28 → pointe 0,03), collier de 16 cubes, fût hexagonal de 16 sections (2,30 blocs), 4 viroles, 2 ailerons, pommeau et sillage hélicoïdal ; satellites à 0,80 et 0,74 d'échelle, décalés dans le temps | idem |
| « je ne vois aucun cube du sol réel » | **`WindTerrainVfx`** (nouveau) : capture les vrais `BlockState` + `ModelData` du terrain autour de la tornade et les réimprime en spirale, exactement comme `SeismicTerrainVfx` pour la faille | nouveau fichier |

---

## 1. Fichiers synchronisés

Le dossier `FIXES_MCREATOR/src/main` contient les mêmes copies que le `src/main` principal :

| Fichier | Rôle |
|---|---|
| `client/WindSignatureVfx.java` | moteur du vent : Zéphyr, Tempête de sable, Œil du cyclone (v6 : hauteurs ×2,3, entonnoirs étroits au sol, lances) |
| `client/WindTerrainVfx.java` | vrais blocs du sol happés par la tornade (capture `BlockState` + `ModelData`, rendu spirale) — **v7 : les blocs montent jusqu'au sommet**, entonnoir plus large en haut |
| `client/WeaponWorldVfxManager.java` | routage `impactWind`, fin du détour Gaia pour le vent, durée d’affichage 120 ticks pour `TEMPEST_EYE` |
| `enchantments/WeaponVisualEffectId.java` | identifiant `TEMPEST_EYE` (#42), alias `/dlbvfx`, gardes de rotation |
| `enchantments/WeaponVfxMotion.java` | mouvement d’écho de lame spiralé pour `TEMPEST_EYE` |
| `util/AnimFamily.java` | **nouveau v7** — noms anglais canoniques des familles (`fire`, `wind`, `earth`, `metal`, `light`, `poison`, `nature`), couleurs de glint et titres ; les anciens noms restent lus |
| `commands/DlbVfxCommand.java` | `/anim <famille>` (épée en **bois**, glint de l'élément, un seul nom par famille) et `/dlbvfx` ; la démo applique le vrai gameplay du vent à la cible visée |
| `enchantments/WeaponVfxServerListener.java` | **gameplay du vent** : zone d'aspiration (`WindZone`) de 4,60 blocs, aspiration continue, rotation + montée 10,50, dégâts sur plusieurs ticks, souffle final, dégâts de chute, sons |
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
| `MINT` | `#9CE8D2` | **v6** — vert d'eau clair des lances et des anneaux d'air |
| `SLATE` / `STEEL` | `#59636B`… `#B9C6CE` | poussière minérale fine (les vrais blocs viennent de `WindTerrainVfx`) |
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
| Jupe de dune | 4 strates × 32 cubes | ellipse, crête asymétrique, s’affaisse en fin de vie |
| Entonnoir extérieur | 6 × 30 | monte de −1,10 à **+6,80 blocs**, rayon **0,45 → 2,55** : étroit au sol, large en haut |
| **Tourbillon interne** | 4 × 22 | rayon **0,22 → 1,27**, **même sens** que l’extérieur, blocs sombres et opaques (`SAND_DEEP`, alpha 0,86–1,00) |
| Mini-tornades | 2 × 26 | au pied de la colonne, montent de 2,40 blocs |
| Grains individuels | 130 | advection hélicoïdale sur toute la hauteur, extinction en fin de course |
| Voile de poussière | 30 | cubes larges très transparents qui referment l’arrière-plan |
| **Blocs du sol réel** | ~96 | `WindTerrainVfx` : herbe, sable, pierre, neige… capturés puis emportés en spirale |

Le tout vit entre 0,00 et 0,74, puis retombe jusqu’à 1,00 : aucune disparition sèche.
Tout le tourbillon tourne dans **un seul sens** (dune, bras, rubans, mini-tornades, grains) : plus aucun
ruban à contre-courant, l’ensemble se lit comme un seul vent.
**Pic mesuré : ≈ 980 cubes/image (hors blocs de terrain).**

### 3.3 Œil du cyclone (`TEMPEST_EYE`, #42, 120 ticks)

Troisième signature, nouvelle — et la plus grande. Entonnoir extérieur de **8 brins × 26 niveaux**
(rayon **0,55 → 2,95**, hauteur **8,90 blocs**), entonnoir interne de 6 brins (rayon 0,24 → 1,34),
noyau torsadé, **œil sommital** à 8,01 blocs (anneau de 44 segments + 10 rayons de 9 cubes), anneau de
sol de 56 segments, mur de nuages de 2 × 6 × 18, éclatement sommital à 0,62, puis effondrement.
**Tous les brins tournent dans le même sens** (le contre-rotatif de la v2 se lisait comme deux
tornades collées).
**Blocs de terrain réels** : `WindTerrainVfx` capture jusqu’à **110 blocs** (surface + profondeur) dans
un rayon de 4–6 blocs, puis les fait décoller, spiraler, culbuter (quaternion réutilisé, aucune
allocation par bloc), s’élever sur toute la hauteur et retomber. Ce sont les vrais modèles du sol
(herbe, sable, pierre, neige, netherrack…), pas des cubes d’ardoise génériques.
**Pic mesuré : ≈ 960 cubes/image (hors blocs de terrain).**

Le visuel du cyclone dure **170 ticks**, exactement la durée de l’emprise : l’entonnoir ne disparaît
plus avant le lâcher.

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

## 5. Gameplay — réaction des mobs, dégâts, effets, sons

Tout est appliqué côté serveur dans `WeaponVfxServerListener`. Le vent n'ajoute **aucun identifiant
d'effet** : il réutilise les emprises existantes (`MOVEMENT_HOLDS`) et une nouvelle emprise
`WIND_HOLDS` qui fait tourner le mob autour de l'axe de la tornade.

### 5.1 Zéphyr — la cible est clouée, transpercée, puis lâchée

| Aspect | Comportement |
|---|---|
| Emprise | `applyMovementHold` 80 ticks, cible **soulevée de 0,55 bloc** : elle ne peut ni avancer ni fuir |
| Mouvement | immobile, vibration de la colonne côté rendu |
| Dégâts | 3 impacts de **0,38 × dégâts de l'arme** (ticks 33 / 37 / 42) + 4 rafales de `clamp(0,09 × arme, 0,05, 0,60)` (ticks 12 / 28 / 44 / 60) → ≈ **1,5 × arme** au total |
| Effets | Lenteur I pendant 3 s à la libération |
| Chute | 0,55 bloc : sous le seuil de dégâts de chute, aucune pénalité ajoutée |
| Sons | `warden.emerg` (ouverture) · `item.trident.throw` × 3 départs · `entity.player.attack.sweep` + `block.amethyst_block.chime` × 3 impacts · `entity.ender_dragon.flap` sur les rafales · `entity.ender_dragon.flap` + `entity.phantom.ambient` à la libération |

### 5.2 Tempête de sable — nuage de sable sur tout l'entourage

| Aspect | Comportement |
|---|---|
| Cibles | tous les `Enemy` dans **2,40 blocs** du centre |
| Emprise | aucune immobilisation totale : le sable colle (Lenteur II) et aveugle |
| Effets | **Cécité 2,5 s** + **Lenteur II 4,5 s** |
| Dégâts | un pulse toutes les **14 ticks** pendant 140 ticks de `clamp(0,07 × arme, 0,04, 0,45)` → ≈ **0,7 × arme** par mob |
| Sons | `block.honey_block.slide` (le sable coule) · `block.rooted_dirt.break` à chaque pulse · `entity.phantom.ambient` · `entity.ender_dragon.flap` · `entity.fire.extinguish` (fin du nuage) |

### 5.3 Œil du cyclone — les mobs sont emportés puis relâchés en l'air

| Aspect | Comportement |
|---|---|
| Cibles | **toute créature vivante** dans **3,10 blocs** (hors joueurs et hors attaquant) : depuis la v6 le villageois de la cible d'entraînement est happé, ce qui rend le test visible en mode Paisible |
| Emprise | `WIND_HOLDS` **170 ticks** (8,5 s) : `NoAi` forcé, cible **téléportée chaque tick** sur une orbite |
| Mouvement | rayon qui se resserre de sa distance de départ à **0,75 bloc**, **0,34 rad/tick** (≈ 9 tours), montée de **10,50 blocs** en sinusoïde (accélération douce, sommet atteint au lâcher) |
| Dégâts | un pulse toutes les **10 ticks** de `clamp(0,08 × arme, 0,05, 0,60)` + **0,45 × arme** au lâcher (tick 170) |
| Effets | au lâcher : **Nausée 6 s** + **Lenteur I 4 s** |
| **Lâcher** | au sommet, vitesse **aléatoire** : angle tiré par `RandomSource`, force 1,45–2,00, **+0,95** vertical — le mob part dans une direction quelconque, pas sur la tangente |
| **Dégâts de chute** | au lâcher la `fallDistance` est **remise à zéro à cet instant** et le mob retombe de ~10 blocs : la chute vanilla applique ses dégâts normaux à l'atterrissage. Tornade + chute se cumulent donc bien |
| Sons | `entity.ender_dragon.flap` (aspiration + boucle toutes les 20 ticks jusqu'à 160) · `block.warden.emerg` · `entity.lightning_bolt.thunder` (tonnerre à 90) · `entity.generic.explode` + `block.amethyst_block.chime` (lâcher, joués **à 10,5 blocs de haut**) |

### 5.4 Règles communes

- Les dégâts passent par `hurtWithCapturedWeapon` : armure, résistance et enchantements de protection
  du mob s'appliquent normalement, et les i-frames sont réarmées pour permettre le rythme demandé.
- Aucun mob ne peut cumuler deux emprises de vent : la seconde remplace la première, qui est relâchée
  proprement (IA et gravité rendues).
- Un mob qui meurt pendant l'emprise est relâché immédiatement, sans téléportation posthume.
- Aucun son de mod n'est ajouté : tout passe par la banque vanilla déjà utilisée par les autres
  signatures (aucun `sounds.json`, aucun paquet réseau en plus).

## 6. Tester en jeu

```text
/anim vent                 (ou /anim wind)                → épée de démo : 60 % slash + 40 % signatures de vent
/dlbvfx play 22            (ou /dlbvfx demo wind_lance)   → lance de zéphyr
/dlbvfx play 41            (ou /dlbvfx demo sandstorm)    → tempête de sable
/dlbvfx play 42            (ou /dlbvfx demo tempest)      → œil du cyclone
/dlbvfx stop
```

`/anim vent` donne une épée en netherite dont le NBT `DlbAnimFamily = "vent"` fait tourner, dans l'ordre,
Zéphyr → Tempête de sable → Œil du cyclone à chaque grosse animation : c'est le seul moyen de déclencher
`TEMPEST_EYE` en combat, puisqu'aucun enchantement ne le porte encore. Le slash (60 % des coups) et le
gameplay serveur suivent la même famille.

Critères de réussite :

1. Les trois signatures sont **visibles** et ne se chevauchent pas dans la démo isolée ; la démo
   applique maintenant le vrai gameplay au mob visé (emprise, dégâts, effets, sons).
2. Aucun nouveau `[DLB-VFX] World render batch aborted` ni `IllegalStateException: Not building!`.
3. La lance de zéphyr montre bien la colonne d’air verticale, le fût planté avec sa pointe de cristal
   et ses deux hélices contra-rotatives qui s’ouvrent à l’éclosion.
4. Sur un zombie de test : Zéphyr le cloue 4 s en l’air et le lâche sans dégât de chute ; la tempête de
   sable l’aveugle et le ralentit ; l’œil du cyclone le fait tournoyer, le monte de 4 blocs, puis le
   projette — et il perd des points de vie **à l’atterrissage** en plus des dégâts de tornade.
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
