# Eau — trois signatures, un seul moteur voxel (v1)

Refonte complète de l'élément **Eau** (水), le dernier des cinq éléments chinois classiques à
n'avoir aucun moteur dédié. Jusqu'ici ses trois signatures étaient dessinées **en ligne** dans
`WeaponWorldVfxManager#impactWater` — une nappe de cubes bleus, une « vague » générique et un
buisson de piques — et duraient **14 à 22 ticks**, soit moins d'une seconde, là où le feu, la terre,
le métal, la lumière, le poison, la nature et le vent ont tous reçu une chorégraphie de 80 à 170
ticks dans leur propre fichier. L'eau n'avait par ailleurs **aucun gameplay** : aucun effet sur les
mobs, aucun son, aucune emprise.

Cette version introduit **`WaterSignatureVfx`** (moteur voxel), **`WaterTerrainVfx`** (vrais blocs du
sol emportés), le **gameplay serveur** des trois signatures et la famille **`/anim water`**.

---

## 0. Ce qui change, en une page

| Avant | Après |
|---|---|
| Durées **14 / 22 / 14** ticks | **104 / 150 / 120** ticks (5,2 s · 7,5 s · 6,0 s) |
| Dessin en ligne dans le manager (~150 lignes partagées) | `WaterSignatureVfx.java`, un fichier dédié, trois chorégraphies |
| Aucun bloc réel | `WaterTerrainVfx.java` : vrais `BlockState` poussés par la vague / avalés par le tourbillon |
| Aucun gameplay | `applyWaterGameplay` : marche de la vague, aspiration zonale, prison de glace |
| Aucun son | 11 sons vanilla répartis sur les trois chorégraphies |
| 7 familles `/anim` | **8 familles** : `fire water wind earth metal light poison nature` |

---

## 1. Les trois signatures

### 1.1 Raz-de-marée (`HYDRO_TIDAL_SURGE`, #17, 104 ticks)

Le reproche classique des vagues en voxel : **une plaque bleue qui apparaît et disparaît**. Ici la
masse d'eau a une épaisseur, une crête qui avance plus vite que le pied, et une fin (le reflux).

| Phase | `t` | Ce qui se passe |
|---|---|---|
| Retrait | 0,00–0,18 | trois arcs de succion reculent : l'eau se retire et découvre le sol |
| Montée + marche | 0,10–0,54 | 19 colonnes pleines se lèvent à **4,60 blocs** et le front passe de **−3,40 à +2,80** |
| Déferlement | 0,34–0,64 | la lèvre s'enroule vers l'avant (tube) et bascule |
| Écume + gerbes | 0,50–0,82 | 58 moutons blancs discontinus, 46 gouttes balistiques |
| Reflux | 0,66–1,00 | trois rides concentriques plates s'éloignent et meurent |

Détails qui comptent :

- le front n'est **pas droit** : `forward = front − u²·1,15`, les ailes restent en retard ;
- chaque colonne est **cisaillée** : `shear = curl·v²·1,45 + march·v·0,38`, donc le haut double le bas ;
- l'écume est **discontinue par construction** (`if (h > 0,62) continue`) : jamais une nappe blanche ;
- les gerbes sont de vraies paraboles : `dY = life·(3,60+1,70h) − life²·(4,30+1,60h2)`.

### 1.2 Maelström abyssal (`ABYSSAL_MAELSTROM`, #18, 150 ticks)

Un tourbillon réel tourne en **rotation différentielle** : plus on approche de l'axe, plus c'est
rapide et plus c'est bas. C'est exactement ce que fait le bassin.

| Phase | `t` | Ce qui se passe |
|---|---|---|
| Ouverture | 0,00–0,20 | le bassin se creuse, rayon **4,35**, profondeur **3,30** |
| Rotation | 0,12–0,74 | 5 anneaux, vitesse **×1 au bord → ×3,4 à la gorge**, 3 bras d'écume en spirale logarithmique (1,6 tour) |
| Implosion | 0,60–0,80 | les anneaux se referment sur l'axe, la gorge s'éteint |
| Geyser | 0,76–1,00 | colonne de **9,00 blocs** construite de bas en haut, couronne d'écume en parapluie |

La gorge est un cylindre de quatre étages en `ABYSS` (0x0B3550) qui pulse à `0,88 + 0,18·sin(t·24)`.
Chaque cube de paroi est incliné sur la pente locale (`slope = −(0,42 + 0,95·k)`) : l'entonnoir est
lisse au lieu d'être un escalier d'anneaux.

### 1.3 Givre du permafrost (`PERMAFROST_RIME`, #19, 120 ticks)

Le givre ne « pousse » pas : il **court**, il **cristallise**, puis il **éclate**.

| Phase | `t` | Ce qui se passe |
|---|---|---|
| Étoile de gel | 0,00–0,22 | 8 branches courent au sol sur 4,60 blocs, avec une ramification un segment sur trois |
| Pics | 0,10–0,46 | 9 aiguilles en couronne (jusqu'à **3,20 blocs**) + 3 pics majeurs autour de la cible |
| Prison | 0,30–0,66 | coque translucide de 3 couches × 4 niveaux autour de la cible |
| Fissures | 0,60–0,80 | 22 veines sombres apparaissent, la coque tremble (`sin(t·52)`) |
| Éclatement | 0,78–1,00 | 60 éclats partent en tournant + anneau de froid au sol |

---

## 2. Vrais blocs du sol (`WaterTerrainVfx`)

Même technique que `WindTerrainVfx` et `SeismicTerrainVfx` : les `BlockState`, `ModelData`, lumières
et hauteurs de collision d'une plaque de sol sont **figés au premier tick**, puis réimprimés via
l'atlas de blocs. **Rien n'est retiré du monde** : ce sont des copies qui voyagent.

| Mode | Déclenché par | Trajectoire |
|---|---|---|
| `MODE_SURGE` | `HYDRO_TIDAL_SURGE` | `pickup` dépend de la position **le long de l'axe de la vague** : le bloc n'est emporté qu'au passage du front, puis poussé en avant, soulevé sur la crête (`sin(ride·π)`), dérivé latéralement, reposé au reflux |
| `MODE_MAELSTROM` | `ABYSSAL_MAELSTROM` | `pickup` dépend de la distance à l'axe : le bloc est aspiré en spirale (rayon → **0,18–0,92**, angle enroulé de 1,3 à 2,4 tours), **descend** la paroi, disparaît dans la gorge, puis est **recraché par le geyser** (`u > 0,88`) |

Plafonds : **104 blocs** par instance, **6 instantanés** simultanés, rayon de capture 4 (vague) ou 5
(maelström), pourtour clairsemé (`dist > rayon·0,55 && h > 0,50 → ignoré`) pour que le sol d'origine
reste lisible sans cadre carré.

Comme pour la tornade, **chaque bloc reçoit son propre tirage** (rayon d'arrivée, nombre de tours,
durée de vol, vitesse de culbute, portance) : deux débris ne décrivent jamais la même courbe.

---

## 3. Gameplay serveur

### 3.1 Raz-de-marée — le mur **marche vraiment**

| Aspect | Comportement |
|---|---|
| Front | 12 passes, ticks **12 → 56**, position de **−3,40 à +2,80** le long de l'axe attaquant → cible |
| Sélection | bande de **±1,70 en profondeur** et **±3,60 en largeur** autour du front (pas un cylindre : la vague *avance*) |
| Effet | poussée **(1,15 ; 0,52 ; 1,15)** dans le sens de la vague, `hurtMarked`, `fallDistance = 0` |
| Dégâts | un pulse de `clamp(0,085 × arme, 0,05, 0,55)` par passe traversée |
| Trempage | `clearFire()` (les flammes s'éteignent) + Lenteur II 3,5 s |
| Reflux | tick 74 : tout ce qui reste dans 5 blocs est ramené vers l'arrière (−0,52) |
| Sons | `ambient.underwater.enter` · `block.bubble_column.upwards_ambient` ×6 · `ambient.underwater.exit` ×7 · `entity.generic.splash` |

### 3.2 Maelström abyssal — aspiration zonale **convergente**

Réutilise la machinerie de tornade validée en jeu (`WindZone` / `WIND_HOLDS` / hélices tirées au
sort par mob), réglée en entonnoir :

| Aspect | Comportement |
|---|---|
| Zone | rayon **4,35**, montée **2,30** (on tourne au ras de l'eau), `spin` **0,42 rad/tick**, 118 ticks, capture **continue** |
| Convergence | `converge = true` → **tous** les rayons d'arrivée visent la gorge (**0,18 à 1,13**), chacun le sien : les mobs se resserrent en spirale sans se superposer |
| Désuniformisation | rotation ×0,70–1,55, montée ×0,80–1,08, durée de vol **55–100 ticks**, phase d'ondulation propre (±0,34 en rayon, ±0,20 en hauteur) |
| Dégâts | un pulse de `clamp(0,075 × arme, 0,05, 0,50)` toutes les **11 ticks** |
| Noyade | toutes les 22 ticks : `clearFire()` + Lenteur II sur toute la cuvette |
| Libération | **`RELEASE_GEYSER`** : poussée verticale **1,85–2,40**, horizontale seulement **0,30–0,85** — recraché, pas soufflé |
| Geyser | tick **114** : tout ce qui est encore tenu est relâché d'un coup, +`min(4,5 ; 2,6 × pulse)` de dégâts |
| Sons | `ambient.underwater.loop` · `block.bubble_column.whirlpool_ambient` ×7 · `entity.generic.explode` + `entity.generic.splash` au geyser |

### 3.3 Givre du permafrost — prison de glace puis rupture

| Aspect | Comportement |
|---|---|
| Cible | immobilisation **complète** 72 ticks (`fullStun`, comme la mâchoire d'épines) |
| Gel vanilla | `setTicksFrozen(260)` sur la cible, `200` sur l'entourage : la teinte bleue du gel est visible |
| Entourage | rayon **3,60**, toutes les 10 ticks : `clearFire()`, Lenteur III, Fatigue II, pulse `clamp(0,045 × arme, 0,04, 0,32)` |
| Craquements | sons aux ticks 78 et 86 |
| Éclatement | tick **94** : libération, `min(5,0 ; 6 × pulse)` de dégâts, projection radiale **(0,85 ; 0,62)** |
| Sons | `block.powder_snow.place` · `block.glass.place` · `block.glass.break` ×3 · `entity.player.hurt_freeze` · `block.powder_snow.break` |

---

## 4. Commande `/anim water`

`/anim` expose désormais **huit** familles : `fire water wind earth metal light poison nature`.
`/anim water` donne une **épée en bois** au glint **0x62C7E8** (bleu eau), nommée
`§3Animation Water §8· §760 / 40` : 60 % de slash originel, 40 % de grosses animations, qui
alternent **raz-de-marée → maelström → givre** via la clé NBT `DlbAnimNext`.

Les anciennes valeurs NBT restent lues : `eau`, `water`, `aqua`, `glace`, `ice`, `givre`, `frost`
pointent toutes sur la famille canonique `water`.

---

## 5. Fichiers touchés

| Fichier | Rôle |
|---|---|
| `client/WaterSignatureVfx.java` | **nouveau** — moteur voxel des trois signatures |
| `client/WaterTerrainVfx.java` | **nouveau** — vrais blocs du sol (vague / tourbillon) |
| `client/WeaponWorldVfxManager.java` | durées 104/150/120, routage `impactWater` → moteur dédié, hooks terrain |
| `enchantments/WeaponVfxServerListener.java` | `applyWaterGameplay` + 3 sous-gameplays, `ANIM_WATER`, `converge`/`RELEASE_GEYSER` |
| `util/AnimFamily.java` | huitième famille `water` (alias, glint, code couleur, nom affiché) |
| `commands/DlbVfxCommand.java` | aide de `/anim` mise à jour |

Tous ont leur miroir dans `deepluckyblock_patch/FIXES_MCREATOR/` (c'est lui que `CONSTRUIRE-LE-MOD.bat`
restaure après une régénération MCreator).

---

## 6. Tester en jeu

```text
/anim water                 épée de démonstration (60 % slash / 40 % grosses animations)
/dlbvfx play 17             raz-de-marée
/dlbvfx play 18             maelström abyssal
/dlbvfx play 19             givre du permafrost
```

Ce qu'il faut voir :

1. la vague **traverse** la cible de part en part et pousse les mobs devant elle, au lieu de les gifler sur place ;
2. les blocs du sol se soulèvent **au passage du front**, pas tous en même temps ;
3. dans le maelström, les mobs **se resserrent en spirale** vers la gorge sur des rayons différents, puis sont **recrachés à la verticale** ;
4. le givre fige la cible, la couronne de pics blesse ce qui s'approche, et le tout **éclate** au tick 94.

---

## 9. Vérification de l'aperçu HTML (`deliverables/DLB_Eau_v1_preview.html`)

L'aperçu rejoue en JavaScript **les formules exactes** de `WaterSignatureVfx` et de `WaterTerrainVfx`
(mêmes constantes, mêmes `smooth()`, même `hash()`), afin de pouvoir juger une chorégraphie sans
lancer le jeu. Audit automatisé sous Chromium/SwiftShader, trois instants par variante :

| Variante | t = 0,26 | t = 0,56 | t = 0,86 | Pic mesuré |
|---|---|---|---|---|
| Ⅰ Raz-de-marée | 100 | 393 | 443 | **544** (t ≈ 0,77) |
| Ⅱ Maelström abyssal | 394 | 454 | 352 | **470** (t ≈ 0,66) |
| Ⅲ Givre du permafrost | 206 | 439 | 502 | **553** (t ≈ 0,79) |

Zéro erreur JavaScript, et le téléchargement du ZIP embarqué renvoie bien les 344 505 octets attendus.
Ces nombres sont des **cubes visibles après tri de profondeur** : le plafond de sécurité du manager
(7 000 cubes/frame) reste très loin.

### Deux corrections sorties de cet audit

1. **Densité du maelström.** Les anneaux étaient tirés à `34 − ring × 3` cubes et les bras d'écume
   étaient au nombre de 3 : vu de l'extérieur, l'entonnoir laissait passer le décor entre les cubes.
   Porté à `44 − ring × 4` cubes par anneau et **4 bras** de 30 segments. C'est le seul changement
   de contenu visuel fait après l'écriture initiale.
2. **Bug de la fonction `mix()` du gabarit d'aperçu** (HTML uniquement, le mod n'était pas touché).
   Le gabarit hérité de l'aperçu Vent calculait
   `((ar + (b>>16&255) - ar) * t)`, où les parenthèses annulent le terme `ar` : le résultat valait
   `br × t` au lieu de `ar + (br − ar) × t`. Conséquence : **toute couleur mélangée avec `t` proche de 0
   tombait au noir**. L'entonnoir et la base du geyser s'affichaient en « obsidienne ». La version
   corrigée, `(ar + ((b>>16&255) − ar) * t)`, a été appliquée à `DLB_Eau_v1_preview.html` **et** à
   `DLB_Vent_v8_preview.html`, qui portait le même défaut.
   Côté mod, `WeaponWorldVfxManager.mixRgb()` fait `a × (1 − k) + b × k` : elle était **déjà correcte**,
   aucune couleur du jeu n'a jamais été affectée.

