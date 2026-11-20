# Les effets esthétiques des combos — liste complète

Relevé des sources, T429. Chaque valeur est lue dans le code, aucune n'est approchée.

Il y a **deux familles** d'ornements, et elles ne se ressemblent pas :

1. **Les ornements permanents** — ailes, halos, couronnes, auras au sol. Ils sont
   **déjà dessinés en mini-cubes** par `CosmeticPixelAura`, à chaque frame.
2. **Les ornements ponctuels** — couronne de lumière, rubans d'ailes, hélice, coussin de
   nuages, rayon tendu. Ils partent en **particules vanilla** depuis `EnchantmentComboHandler`.

---

# PARTIE 1 — LES 12 ORNEMENTS PERMANENTS (déjà en mini-cubes)

Rendu : `CosmeticPixelAura`, côté client, une passe par frame, via
`WeaponWorldVfxManager.emitVoxelCube`. Plafond **1 400 cubes par frame**, portée **40 blocs**,
profil recalculé toutes les **10 ticks**.

## A. Accordés par un COMBO — 6

### #110 Avatar Suprême du Deep Lucky — `ULTRA_RARE`, 0xFFFFFF
**Couronne double au-dessus de la tête**, la plus lumineuse du mod.
| Anneau | Hauteur | Rayon | Points | Couleurs | Alpha | Taille | Rotation |
|---|---|---|---|---|---|---|---|
| externe | `height + 0.30` | 0.54 | 16 | 0xFFF6D0 → 0xFFC63D | 250 | 1.8 | `+time × 0.55` |
| interne | `height + 0.44` | 0.40 | 12 | 0xFFFFFF → 0xFFE9A0 | 235 | 1.5 | `−time × 0.72` |

Pièces : épée nétherite `peerless` 5 · bouclier `perfect_parry` 3 · casque `food_is_heaven` 1 ·
plastron `heart_resonance` 1 · jambières `giant_step` 5 · bottes `cloud_walker` 1.

### #097 Armure Éternelle Transcendante — `ULTRA_RARE`, 0xFFD700
**Couronne simple.** `height + 0.28` · rayon 0.40 · 12 points · 0xFFE9A0 → 0xFFB020 ·
alpha 235 · taille 1.5 · rotation `+time × 0.5`.

Pièces : casque `king` 3 · plastron `divine_protection` 3 · jambières `giant_step` 3 ·
bottes `cloud_walker` 1.

### #098 Dieu de la Lame Suprême — `ULTRA_RARE`, 0xFFAA00
**Même couronne que #097** (branche commune dans le code).

Pièces : épée nétherite `supreme_sharpness` 10 · casque `king` 3 · plastron `heart_resonance` 1 ·
jambières `leap_power` 3 · bottes `windrunner` 1.

### #101 Archange du Jugement Solaire — `ULTRA_RARE`, 0xFDE047
**Deux ailes en membrane de pixels.** 5 colonnes × 3 rangées = 15 cubes, plus 5 plumes de
pointe → **20 cubes par aile, 40 au total**. Couleurs 0xFFE9A0 (membrane) / 0xFFFBEA (bord),
échelle **1.0**. Battement `sin(time × 2.6)`, amplitude croissante vers la pointe.
Épaules à `height × 0.66`, recul 0.16 → 0.46 blocs. Bord externe alpha 235, membrane 150.
**Portées en permanence**, au sol comme en vol.

Pièces : épée nétherite `annihilating_sweep` 3 · bouclier `shield_quake` 1 ·
casque `radiant_ward` 2 · plastron `light_heart` 2 · bottes `lightness` 3.

### #106 Seigneur de Guerre de l'Apocalypse — `ULTRA_RARE`, 0xEF4444
**Anneau au sol.** `y + 0.10` · rayon 0.85 (le plus large) · 12 points ·
0xFF7A2E → 0xFFC46B · alpha 195 · taille 1.2 · rotation `−time × 0.45`.

Pièces : épée nétherite `wither_blade` 1 · bouclier `perfect_parry` 2 · casque `commander` 1 ·
plastron `fire_heart` 2 · jambières `dash` 2 · bottes `blaze_walker` 1.

### #062 Peste Ambulante — `RARE`, 0x84CC16
**Anneau au sol**, en couche translucide. `y + 0.14` · rayon 0.95 · 10 points ·
0x6BAF1E → 0x3E7A0C · alpha 150 · taille 1.1 · rotation `+time × 0.30`.
**Le seul ornement permanent accessible hors ULTRA_RARE** — deux pièces en diamant suffisent :
bottes `super_stinky_feet` 1 · plastron `dread_aura` 1.

## B. Accordés par un ENCHANTEMENT d'armure — 5

Lus par `getArmorEnchantLvl` : **l'armure seule compte**, jamais l'arme.

| Enchantement | Ornement | Hauteur | Rayon | Points | Couleurs | Alpha | Taille | Rotation |
|---|---|---|---|---|---|---|---|---|
| `king` | couronne | `height + 0.26` | 0.34 | 10 | 0xFFD34D → 0xFF9500 | 225 | 1.4 | `+time × 0.42` |
| `mystic_mind` | couronne | `height + 0.34` | 0.32 | 10 | 0xC08BFF → 0x7B3BE8 | 215 | 1.3 | `−time × 0.40` |
| `heart_resonance` | couronne | `height + 0.20` | 0.36 | 10 | 0xFFF7D9 → 0xBFE8FF | 210 | 1.3 | `+time × 0.36` |
| `dread_aura` | anneau bas | `y + 0.32` | 0.66 | 8 | 0x3A0A66 → 0x7B2BD6 | 165 | 1.0 | `−time × 0.38` |
| `wing_toughness_armor` | **ailes** | épaules | — | 40 cubes | 0xCFE8FF / 0xFFFFFF | 235 / 150 | ×0.82 | battement 2.6 |

Les ailes d'`wing_toughness_armor` ne sortent **qu'en l'air ou en vol d'élytres** — contrairement
à celles de l'Archange, permanentes, et elles sont **18 % plus petites**.

## C. Le halo générique — 1

`F_HALO_CONTINUOUS` : **trois anneaux** à la fois, avec une respiration verticale
`sin(time × 1.35) × 0.035`.

| Anneau | Hauteur | Rayon | Points | Couleurs | Alpha | Taille | Rotation |
|---|---|---|---|---|---|---|---|
| couronne | `height + 0.36 + bob` | 0.46 | 14 | 0xFFF3C8 → 0xFFC63D | 240 | 1.6 | `+time × 0.62` |
| halo haut | `height + 0.50 + bob×0.6` | 0.33 | 10 | 0xFFFFFF → 0xFFE9A0 | 215 | 1.35 | `−time × 0.85` |
| socle au sol | `y + 0.06` | 0.58 | 10 | 0xFFE9A0 → 0xFFC63D | 150 | 1.0 | `−time × 0.33` |

Conditions : trident enchanté en main ou en seconde main, **ou** arme portant le marqueur
`dlbWeaponAura` tiré au butin (mêlée 15 %, distance 40 %). Il ne s'affiche **que si aucun autre
ornement n'est actif** — c'est le seul du lot à être exclusif.

## Règles de cumul

Les trois couronnes **#110 / (#097 ou #098) / `king`** sont une chaîne `else if` : une seule
sort, dans cet ordre de priorité. `mystic_mind`, `heart_resonance`, les ailes et les anneaux au
sol se cumulent librement. Le halo générique s'efface devant n'importe lequel des autres.

---

# PARTIE 2 — LES ORNEMENTS PONCTUELS EN PARTICULES VANILLA

Ce sont ceux qui restent à reconstruire en mini-blocs.

| Ornement | Ligne | Combo déclencheur |
|---|---|---|
| `crownHalo` | 348 | #093 Avatar du Zéphyr — +30 % vitesse aérienne |
| `crownHalo` | 1493 | #091 Renaissance Solaire — sous 22 % PV, Nova Solaire |
| `crownHalo` | 3057 | #083 Empereur de Légion — bénit les soldats invoqués |
| `wingRibbons` | 646 | #081 Ange à la Plume Céleste — planer régénère et purifie |
| `doubleHelix` | 667 | #086 Berserker Immortel — sous 35 % PV, Célérité III |
| `cloudCushion` | 572 | #063 Pont Dimensionnel — propulsion aérienne |
| `cloudCushion` | 703 | #090 Faucon Ouragan — cratère à l'atterrissage |
| `tetherBeam` | 1513 | #099 Seigneur de Sang Immortel — survit à un coup fatal |
| `tetherBeam` | 1963 | #084 Faucheur de Tempête — Éclair Céleste en chaîne |
| `tetherBeam` | 3389 | #074 Archer d'Orage en Chaîne — rebond sur 3 cibles |

---

# PARTIE 3 — LE RESTE DES EFFETS VISUELS DES COMBOS

Au-delà des ornements, `EnchantmentComboHandler` émet **116 effets visuels** au total sur
**106 combos nommés**, dont **87 salves** qui ne sont pas des ornements :

- `burstOnEntity` **44** — explosion ponctuelle sur le buste d'une entité
- `shockwaveRing` **43** — anneau d'onde au sol ou à mi-hauteur
- `spawnVisualLightning` **8** — vrai éclair vanilla (entité, pas particule)
- `PixelFx.send` **7** — particule unique à une position précise
- `slashArc` **4** — arc d'entaille devant l'attaquant

**20 particules vanilla distinctes** : `CLOUD`, `CRIT`, `DRAGON_BREATH`, `ELECTRIC_SPARK`,
`ENCHANTED_HIT`, `END_ROD`, `EXPLOSION`, `FLAME`, `GUST_EMITTER_SMALL`, `HAPPY_VILLAGER`,
`LAVA`, `NAUTILUS`, `PORTAL`, `SCULK_SOUL`, `SNOWFLAKE`, `SONIC_BOOM`, `SOUL_FIRE_FLAME`,
`SPLASH`, `SWEEP_ATTACK`, `TOTEM_OF_UNDYING`.

**12 palettes** déjà dérivées de `DeepElement`, donc directement transposables sur vos textures :
`CRIMSON_BLOOD` (BLOOD.primary) · `SCARLET_MIST` (BLOOD.secondary) · `GOLD_DIVINE` (LIGHT.primary) ·
`SHADOW_VIOLET` (SHADOW.primary) · `TOXIC_GREEN` (POISON.primary) · `TEMPORAL_AMBER` (LIGHT.secondary) ·
`FROST_CYAN` (WATER.highlight) · `OVERCLOCK_CYAN` (THUNDER.primary) · `STEEL_SILVER` (METAL.primary) ·
`EARTH_OCHRE` (EARTH.primary) · `CLOUD_WHITE` (LIGHT.highlight) · `HONEY_AMBER` (LIGHT.secondary).

Trois formes sont déclarées mais **jamais appelées par un combo** : `blockDebrisRing`,
`meteorSkyfall`, `ankleTrail` (cette dernière sert 35 fois ailleurs dans le mod).

Également **13 effets sonores**, non listés ici : ils ne sont pas visuels.

## Chiffres du registre

**310 combos déclarés** : UNCOMMON 103 · NORMAL 65 · RARE 54 · EPIC 36 · MYTHIC 28 ·
ULTRA_RARE 24. Sur ces 310, **6 seulement** accordent un ornement permanent.

Deux remarques relevées au passage : le javadoc de la classe annonce « 210 Combos » alors
que `ComboMask` en annonce 310 — c'est 310 qui est juste. Et l'identifiant du combo d'index
278, `briseur_élan_brutal`, est le seul à porter un accent, là où les 309 autres sont en
ASCII pur.
