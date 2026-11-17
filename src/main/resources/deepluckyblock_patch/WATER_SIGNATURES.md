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

---

## 10. Mouvement des mobs — doctrine complète (révision v2)

La première passe de gameplay marchait, mais le **mouvement** des victimes était le maillon faible :
poussées identiques pour tout le monde, verrous mal cadrés, projections sans variation, et presque
aucun retour visuel sur les mobs eux-mêmes. Cette révision reprend les quatre points un par un.

### 10.1 Les deux types de verrou

Il y a maintenant une distinction explicite, et elle est volontaire :

| | **Verrou mou** | **Verrou dur** |
|---|---|---|
| Qui | Raz-de-marée (`SurgeRide`) | Maelström (`WindHold`) |
| Ce qui est imposé | la **vitesse**, chaque tick | la **position**, chaque tick (`teleportTo`) |
| IA | coupée (`setNoAi`) | coupée (`setNoAi`) |
| Physique | **conservée** : bute contre les murs, tombe dans les trous, percute les autres mobs | ignorée : le mob suit l'hélice quoi qu'il arrive |
| Pourquoi | une vague **pousse**, elle ne téléporte pas | un tourbillon **tient** sa proie |

Le verrou mou était indispensable : sans coupure d'IA, le mob se retournait et marchait à
contre-courant au milieu de la vague. Sans conservation de la physique, il traversait les murs.

### 10.2 Quand verrouiller

| Signature | Cible du verrou | Déclenchement | Durée | Raison |
|---|---|---|---|---|
| Raz-de-marée | tout mob dans la bande du front | au passage du front | **26 à 42 ticks**, tirés au sort par mob | transport, puis relâche |
| Maelström | mobs dans 4,35 blocs | capture continue de la zone | 55 à 100 ticks (déjà par mob) | aspiration |
| Givre | la cible | à l'impact | **jusqu'au tick 94** | voir le correctif ci-dessous |
| Givre | l'entourage | au **3ᵉ** passage de froid | jusqu'au tick 94 | le permafrost fige ce qui traîne |

> **Correctif de timing.** Le verrou de la cible du givre expirait au tick **72** alors que la prison
> n'éclate qu'au tick **94** : la cible disposait de 22 ticks pour s'enfuir *avant* la rupture, et
> l'éclatement frappait souvent le vide. Le verrou tient désormais jusqu'à l'instant exact de la
> rupture, et c'est le callback du tick 94 qui libère tout le monde.

### 10.3 Quand projeter

| Moment | Qui | Direction | Force horizontale | Portance |
|---|---|---|---|---|
| Fin de surf (**échelonnée**, ticks ~38 à ~98) | surfeurs | avant de la vague ± 0,45 rad | 1,45 → 2,30 | 0,70 → 1,10 |
| Tick 74 | ce qui n'a pas été emporté | reflux, vers l'arrière | 0,52 | 0,18 |
| Tick 114 — geyser | happés par l'entonnoir | verticale + dispersion | 0,30 → 0,85 | 1,85 → 2,40 |
| Tick 114 — retardataires | non tenus, dans 2,6 blocs | angle aléatoire | 0,22 → 0,60 | 1,55 → 2,10 |
| Tick 94 — éclatement | tout dans 3,6 blocs | radial | **0,55 + 0,80 × proximité** | **0,42 + 0,38 × proximité** |

Deux principes :

1. **Jamais au même instant.** Pour le raz-de-marée la projection n'est pas un événement planifié :
   elle survient quand la durée de transport tirée à l'embarquement expire. Comme les embarquements
   s'étalent du tick 12 au tick 56 et que les durées valent 26 à 42 ticks, les mobs sont jetés les
   uns après les autres, du tick ~38 au tick ~98.
2. **Jamais avec la même force.** Chaque projection combine un terme déterministe et un terme
   aléatoire. L'éclatement du givre ajoute un terme de **proximité** : collé à la prison on part
   deux fois plus fort et on prend deux fois plus de dégâts qu'au bord de la couronne.

`fallDistance` est remis à zéro **juste avant** chaque projection : toute la chute qui suit est
comptée par le jeu et ajoute ses propres dégâts par-dessus ceux de la signature.

### 10.4 Le transport du raz-de-marée en détail

Trois forces se composent à chaque tick dans `tickSurgeRide` :

```
drive = speed × (0,55 + 0,45 · sin(min(1 ; u×1,15) · π))     entraînement dans l'axe
sway  = drift × sin(elapsed × 0,31 + bob)                    dérive latérale
vy    = lift × sin(u · π) + 0,04 · sin(elapsed × 0,44 + bob) portance de la crête
```

Paramètres **tirés au sort à l'embarquement, propres à chaque mob** :

| Paramètre | Plage | Effet |
|---|---|---|
| `speed` | 0,62 → 1,08 | vitesse d'entraînement |
| `lift` | 0,26 → 0,50 | hauteur sur la crête |
| `drift` | ± 0,08 → 0,38 | écart latéral (signe aléatoire) |
| `bob` | 0 → 2π | phase du ballottement |
| `roll` | ± 6 → 20 °/tick | culbute sur soi-même (`setYRot`) |
| `carry` | 26 → 42 ticks | durée du transport |

C'est la même leçon que pour la tornade de sable : sans ces six tirages, on voit immédiatement que
tous les mobs suivent le même patron.

### 10.5 Succion d'approche du maelström

La zone ne capture que dans 4,35 blocs, mais un maelström se voit de plus loin. Une passe toutes les
**3 ticks**, du tick 8 au tick 110, tire vers la gorge tout ce qui se trouve entre **4,35 et 7,20**
blocs, sans le capturer :

```
grip = (0,16 + 0,13 × (1 − (dist − 4,35)/(7,20 − 4,35))) × ramp
vx  −= ux·grip + uz·grip×0,55      ← la composante tangentielle (×0,55) fait entrer
vz  −= uz·grip − ux·grip×0,55         les mobs en SPIRALE, pas en ligne droite
```

`ramp` monte de 0 à 1 sur les 40 premiers ticks : l'aspiration s'installe, elle ne claque pas.

### 10.6 Noyade

Les mobs présents dans la cuvette perdent **55 points d'air** à chaque passe (toutes les 22 ticks,
plancher −18). Les créatures qui respirent sous l'eau ne sont pas concernées : le jeu leur remet
l'air de lui-même, aucun test d'espèce n'est nécessaire.

### 10.7 Effets visuels ajoutés

Tous passent par `PixelFx`, le wrapper maison de `sendParticles` (enveloppé dans un `try/catch` :
un effet cosmétique ne peut pas casser un tick serveur).

| Signature | Moment | Particules | Rôle |
|---|---|---|---|
| Raz-de-marée | chaque passe du front | `SPLASH` ×18 étalé sur la largeur, `BUBBLE` ×10 | rendre le **mur** visible sur ses 3,6 blocs |
| Raz-de-marée | embarquement | `SPLASH` ×14 sur le mob + `GENERIC_SPLASH` | on voit **qui** vient d'être happé |
| Raz-de-marée | pendant le surf | `SPLASH` ×4 tous les 3 ticks, `BUBBLE` ×3 tous les 7 | sillage individuel |
| Raz-de-marée | projection | `SPLASH` ×22 + son | ponctuer le lâcher |
| Raz-de-marée | reflux / fin | `CURRENT_DOWN` ×26, `SPLASH` ×20 | l'eau se retire |
| Maelström | pendant l'aspiration | `BUBBLE_COLUMN_UP` ×14, `CURRENT_DOWN` ×18, `BUBBLE` ×5 par noyé | l'entonnoir respire |
| Maelström | geyser | colonne de 10 étages × (`SPLASH` ×9 + `BUBBLE_COLUMN_UP` ×5) sur 9 blocs | le jet se voit de loin |
| Maelström | geyser | `broadcastNear(ABYSSAL_MAELSTROM, sizeScale 0,55)` | rejoue la signature en réduit sur l'axe |
| Givre | chaque passe de froid | `SNOWFLAKE` ×6 par victime + ×12 au sol | la zone est lisible |
| Givre | gel par exposition | `SNOWFLAKE` ×22 + `GLASS_PLACE` | marque l'instant où le mob est pris |
| Givre | éclatement | `SNOWFLAKE` ×60, `WHITE_ASH` ×36, `SNOWFLAKE` ×14 par victime | la rupture |
| Givre | éclatement | `broadcastNear(PERMAFROST_RIME, sizeScale 0,50)` | rejoue la signature au bon tick |

### 10.8 Registres ajoutés

```java
SURGE_RIDES      Map<Integer, SurgeRide>   surfeurs, tickés par tickSurgeRide
SURGE_RECAPTURE  Map<Integer, Long>        45 ticks d'immunité après avoir été jeté
FROST_EXPOSURE   Map<Integer, Integer>     compteur d'exposition au froid
```

`refreshSignatureAi` consulte maintenant `SURGE_RIDES` en plus de `MOVEMENT_HOLDS`, `SOUL_HOLDS` et
`WIND_HOLDS` : sans cela, la fin d'un autre effet rendait le contrôle au mob **en plein transport**
et on le voyait se retourner pour marcher contre la vague. Les trois registres sont purgés dans la
passe « une fois par tick » et par un filet de sécurité au tick 120 du givre.

---

## 11. Le « faux trou » du maelström — comment on efface le sol sans y toucher

### 11.1 Les trois façons de faire, et pourquoi deux sont à rejeter

| Méthode | Comment | Verdict |
|---|---|---|
| **A. Casser les vrais blocs** (`setBlock` / `destroyBlock`) | on met `AIR`, on restaure après | ❌ **Inacceptable** |
| **B. Détourner le rendu de chunk** | hook sur `BlockAndTintGetter`, re-maillage des sections | ❌ Coûteux et fragile |
| **C. Mentir au client** (`ClientboundBlockUpdatePacket`) | on annonce « air » aux joueurs, le serveur ne change rien | ✅ **Retenu** |

**Pourquoi pas A.** Casser les blocs pour de vrai, c'est déclencher tout le moteur de jeu : le sable
et le gravier tombent, l'eau et la lave coulent dans le trou, la redstone se met à jour, les coffres
droppent leur contenu, les mobs tombent dedans, les events de minage partent. Et surtout : si le
serveur crashe, si le chunk se décharge, ou si le joueur se déconnecte pendant les 7,5 secondes de
l'animation, **le cratère reste définitivement** dans son monde. Un effet cosmétique n'a pas le
droit de faire ça.

**Pourquoi pas B.** Il faudrait intercepter l'accès aux `BlockState` au niveau du moteur de rendu,
forcer le re-maillage des sections à chaque frame, et se battre avec les optimiseurs de rendu des
autres mods. Beaucoup de travail pour un résultat fragile.

### 11.2 La méthode retenue — `enchantments/GhostBlockMask.java`

On envoie aux joueurs à portée un `ClientboundBlockUpdatePacket` qui dit « ce bloc est de l'air ».
Le client l'affiche comme de l'air. **Le serveur, lui, n'a rien changé.**

```java
new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState())  →  player.connection.send(…)
```

Pour restaurer, on relit l'état **réel** et on le renvoie :

```java
new ClientboundBlockUpdatePacket(level, pos)   // lit le vrai bloc, maintenant
```

| Avantage | Détail |
|---|---|
| Zéro effet de bord | pas de mise à jour de bloc, pas de gravité, pas de fluide, pas de redstone, pas de drop |
| Les entités ne tombent pas dedans | la collision serveur est **inchangée** — exactement ce qu'on veut, les mobs doivent tourner dans l'entonnoir, pas s'y enterrer |
| Rien à réparer après un crash | le monde sur le disque n'a jamais été modifié ; recharger le chunk suffit |
| Restauration exacte | si quelqu'un pose un bloc pendant l'animation, c'est **son** bloc qui réapparaît |

**La limite, assumée :** l'illusion est purement visuelle. Un joueur qui marche sur le trou ne tombe
pas, il marche sur un sol invisible. Pour un effet d'arme de 7,5 secondes c'est le bon compromis —
et c'est aussi pourquoi la durée est plafonnée à 240 ticks par `MAX_LIFETIME`.

### 11.3 Rendre l'ouverture et la fermeture *smooth*

Trois mécanismes se combinent :

**1. Le profil, pas un cylindre.** La profondeur suit une parabole :

```
depth = maxDepth × (1 − (dist / radius)²)
```

large et peu profond au bord, resserré et plongeant au centre. Sans ça, le trou a des parois
verticales et ressemble à une cage d'ascenseur.

**2. L'ouverture décalée.** Chaque bloc a son propre instant de disparition :

```
open = openAt + dist × delayPerRing      (3,4 ticks par bloc de distance)
     + dy                                (la colonne s'enfonce couche par couche)
```

Le trou se creuse **du centre vers le bord**, à la vitesse de la spirale qui l'aspire. Ouverture
étalée sur ~15 ticks au lieu d'un clignotement instantané.

**3. La fermeture en sens inverse.** Le rebouchage est décalé lui aussi, mais **à l'envers** :

```
close = closeAt + (radius − dist) × closeStagger    (2,6 ticks par bloc)
      − dy                                          (se remplit du fond vers le haut)
```

Le **bord** se referme en premier, le centre en dernier — c'est le sens dans lequel l'eau reflue.

**4. Et surtout : la continuité avec `WaterTerrainVfx`.** Le bloc ne disparaît pas dans le néant.
Au même instant, le moteur client le dessine **en train de voler** dans la spirale, avec sa vraie
texture. L'œil suit le bloc : il ne voit pas une disparition, il voit un **arrachement**.

**5. La nappe de surface.** Nouveau bloc de rendu n° 7 du maelström : un anneau d'eau plaqué au sol
qui **déborde du bassin**. Son rôle est autant technique qu'esthétique — il recouvre l'arête nette
entre le terrain intact et le bord du trou. L'œil voit de l'eau qui tourne par-dessus un rebord,
pas une découpe géométrique. Même rôle pour la **brume de froid** du givre.

### 11.4 Les trois usages dans l'élément eau

| Signature | Méthode | Ce que le joueur voit |
|---|---|---|
| **Maelström** | `digFunnel(r 4,35 · prof. 3,30 · ouvre t+6 · referme t+128)` | un vrai entonnoir creusé dans le sol |
| **Raz-de-marée** | `scourLine(−3,40 → +2,80 · demi-largeur 3,40 · prof. 2)` | la terre est **arrachée sous le front**, jamais devant lui |
| **Givre** | `freezeSurface(r 4,20 · PACKED_ICE au cœur, SNOW_BLOCK au bord)` | le sol **gèle** puis dégèle — rien à déneiger après |

`freezeSurface` utilise `PACKED_ICE` et `SNOW_BLOCK` parce qu'ils sont **opaques** : la glace
normale est translucide et aurait laissé passer la lumière du bloc d'origine, trahissant l'illusion.
Le bord du disque gelé est **dentelé** par un hash de position — un cercle parfait se repère
immédiatement.

### 11.5 Garde-fous

```
MAX_ACTIVE   = 420 masques simultanés sur tout le serveur  (au-delà : le plus ancien saute)
MAX_LIFETIME = 240 ticks par masque                        (même si l'appelant oublie de restaurer)
SEND_RADIUS  = 96 blocs                                    (inutile de mentir à qui ne voit rien)
tick()       = une passe par tick, restaure ce qui a expiré
restoreAll() = tout annuler d'un coup
```

Chaque envoi est enveloppé dans un `try/catch` : un paquet cosmétique ne doit jamais casser un tick
serveur. `tick()` est appelé depuis la passe « une fois par tick » déjà protégée de
`WeaponVfxServerListener`.

---

## 12. Détails ajoutés aux trois animations

| # | Signature | Élément | Rôle |
|---|---|---|---|
| 7 | Raz-de-marée | **Ruissellement** — 34 filets verticaux qui descendent la face du mur, phase et vitesse propres | sans eux la paroi est lisse et on ne lit pas sa hauteur |
| 8 | Raz-de-marée | **Sol mouillé** — 40 plaques sombres devant le front | ancre le mur au terrain au lieu de le laisser flotter |
| 7 | Maelström | **Nappe de surface** — 3 anneaux débordants, 38 à 54 cubes chacun | masque la découpe du trou fantôme |
| 8 | Maelström | **Bulles** — 30 bulles remontant du fond, cycle individuel | seul élément lisible *dans* le trou : il en donne la profondeur |
| 5 | Givre | **Brume de froid** — 46 plaques basses à la dérive | masque la découpe du sol gelé |
| 6 | Givre | **Cristaux en suspension** — 26 éclats en orbite lente | distingue un sort actif d'un bloc de glace posé là |

Charge mesurée après ajout (pic de cubes visibles) : **517** raz-de-marée, **607** maelström,
**574** givre — toujours très loin du plafond de 7 000 cubes/frame du manager.

---

## 13. Correctif de compilation du 12 novembre 2026 — et l'outil qui l'aurait évité

Le `runClient` a échoué sur **quatre** erreurs, toutes invisibles pour le contrôle syntaxique
utilisé jusqu'ici (`java-parser` valide la grammaire, pas les symboles) :

| Fichier | Ligne | Erreur | Cause |
|---|---|---|---|
| `WaterSignatureVfx` | 273 | `cannot find symbol: H` | nom repris du JavaScript de l'aperçu ; la constante Java s'appelle **`SURGE_HEIGHT`** |
| `WaterSignatureVfx` | 281 | `cannot find symbol: HALF_WIDTH` | idem → **`SURGE_HALF_WIDTH`** |
| `WaterSignatureVfx` | 296 | `cannot find symbol: HALF_WIDTH` | idem |
| `WeaponVfxServerListener` | 2514 | `cannot find symbol: carved` | `int carved = digFunnel(…)` avait été inséré **après** le message client qui l'utilise |

Les trois premières viennent du fait que l'aperçu HTML et le moteur Java sont écrits en parallèle :
le JS utilise des noms courts (`H`, `HW`) qui n'existent pas côté Java. La quatrième est une erreur
d'ordre d'insertion.

### Nouvel outil : `tools/semcheck.py`

Contrôle sémantique léger, complémentaire de `java-parser` :

```
python3 semcheck.py <fichiers .java…>
```

| Détection | Exemple attrapé |
|---|---|
| **(A) constante inconnue** — identifiant MAJUSCULE utilisé sans point devant et déclaré nulle part dans le projet | `H`, `HALF_WIDTH` |
| **(B) utilisation avant déclaration** — variable locale lue plus haut que sa déclaration, dans la même méthode | `carved` |

Il neutralise d'abord commentaires et chaînes, construit un dictionnaire de **toutes** les constantes
et enums du projet (une constante peut venir d'un autre fichier), puis ne signale que ce qui n'est
nulle part. Pour (B) il retient la **première** déclaration d'un nom — sans cela, chaque variable
redéclarée dans deux boucles frères produit un faux positif.

**Mode d'emploi.** Comme `lamcheck2`, l'outil reste bruyant sur les gros fichiers qui listent des
constantes d'enum (`WeaponWorldVfxManager`, `DlbVfxCommand`). La règle est donc la même :
**comparer la sortie à celle d'une version qui compilait**, et ne regarder que les lignes nouvelles.
Baseline actuelle des faux positifs connus :

```
WeaponVfxServerListener : candidate@1666, part@1857, zone@3001, baseline@3257
WeaponWorldVfxManager   : dy/dz@1857, s@2991, hs@3009 + constantes d'enum multi-lignes
DlbVfxCommand           : constantes d'enum multi-lignes
```

Sur les fichiers de l'élément eau, la sortie est **OK** partout : `WaterSignatureVfx`,
`WaterTerrainVfx`, `GhostBlockMask`.

---

## 14. v1.3 — retours de la partie d'essai du 12 novembre 2026

Six reproches relevés en jeu, six correctifs.

### 14.1 « Le slash de water est un mélange de water et de earth · il y a des slashs marrons »

**Cause.** `WeaponWorldVfxManager.animationSlashEffect(...)` choisit l'effet du *petit* arc (les 60 %
de « slash originel ») à partir de la famille `/anim`. Il y avait une branche pour `fire`, `poison`,
`earth`, `light`, `metal`, `wind`… **mais aucune pour `water`** : l'eau tombait dans le `return`
par défaut, celui de `nature`, donc `THORN_MAW_ROOT` et `VERDANT_SAP_BLOOM`. La palette suit
`fx.element()`, d'où les arcs verts et bruns sous un glint bleu.

**Correctif.** Branche `WATER` ajoutée, qui renvoie les trois identifiants de l'élément :
`HYDRO_TIDAL_SURGE` / `ABYSSAL_MAELSTROM` / `PERMAFROST_RIME`.

### 14.2 « Maelström : on voit bien le trou, mais ça manque gravement de particules à l'intérieur »

Le trou creusé en v1.2 a rendu visible un défaut qui ne se voyait pas avant : les cinq anneaux du
bassin ne dessinaient qu'une **armature**, et entre eux il n'y avait rien. Trois blocs de rendu
remplissent désormais le volume (`WaterSignatureVfx`, blocs 9 à 11) :

| Bloc | Contenu | Cubes |
|---|---|---|
| 9 | **Paroi volumétrique** — 11 nappes hélicoïdales, 1,9 tour du bord à la gorge, cubes étirés tangentiellement (×2,05) pour se toucher et former une surface | 374 max |
| 10 | **Remplissage** — 200 gouttes en suspension *dans* le cône ; rayon tiré en `sqrt(h)` pour une répartition homogène et non un entassement sur l'axe | 200 max |
| 11 | **Cœur** — voir 14.3 | 136 |

Côté serveur, l'entonnoir est aussi rempli de **vraies particules** toutes les 2 ticks du tick 6 au
tick 124 : trois bras en spirale (`SPLASH` + `BUBBLE`), la gorge (`BUBBLE_COLUMN_UP`, `UNDERWATER`),
une colonne remontante et des embruns au-dessus du bord.

### 14.3 « Détaille à mort le cœur du maelström, avec des cubes de différentes tailles »

La lisibilité vient du **contraste d'échelle**, pas du nombre. Quatre couches superposées, à trois
tailles nettement séparées et trois vitesses de rotation différentes :

| Couche | Nombre | Taille | Rotation |
|---|---|---|---|
| (a) grosses dalles | 6 | **0,34** | `spin × 1,70` |
| (b) blocs moyens, double hélice | 24 | **0,155** | `spin × 3,10` |
| (c) éclats rapides | 90 | **0,055** | `spin × 4,20 → 6,80` |
| (d) l'œil — noyau sombre pulsant | 16 | 0,105 | `spin × 0,90` |

L'œil (d) est ce qui rend le fond *lisible* : sans lui le centre « bave » et on ne sait plus où est
le fond de l'entonnoir.

Comptage de l'aperçu : le maelström passe de **607** à **1283** voxels visibles (plafond 7 000).

### 14.4 « Pour la première animation, la wave, pas besoin de trouer le sol »

L'appel `GhostBlockMask.scourLine(...)` est retiré de `applySurgeGameplay`. Une vague déferle, elle
ne creuse pas. La méthode reste dans l'API de `GhostBlockMask` mais n'a plus aucun appelant.

### 14.5 et 14.6 « La vague fonctionne en AoE » · « le mob recule avec la vague de plein fouet »

Le mur ne capturait que dans une bande de `|along| <= 1,70` et `|side| <= 3,60`, et **seules** les
créatures embarquées subissaient quelque chose : tout ce qui n'était pas capturable (cooldown de
réembarquement, mob tenu par une tornade, mob trop sur le côté) traversait la vague sans rien sentir.

Nouveau comportement :

* **bande élargie** à `|along| <= 2,60` et `|side| <= 3,90` — le mur a une épaisseur ;
* **tout** ce que le mur traverse est poussé, surfeur ou non :
  `shove = (breaking ? 0,95 : 0,62) x (0,45 + 0,55 x head)` où
  `head = 1 - |side| / 3,90` — de plein fouet au centre, effleuré sur les bords ;
* les dégâts sont appliqués **une seule fois par vague**, au premier contact, via un
  `Set<Integer> struck` partagé par les douze passes ;
* le **reflux** ne renvoie plus personne vers l'attaquant : il pousse encore vers l'avant
  (`+dir x 0,38` au lieu de `-dir x 0,52`). Une vague ne recrache pas ses victimes vers le tireur.


---

## 15. v1.5 — « l'eau a des slashs de nature, la nature a des slashs d'eau »

### 15.1 Ce qui se passait réellement

Le diagnostic du joueur était juste, mais la cause n'était ni dans l'eau ni dans la nature :
**l'élément Vent n'existait pas.**

Les trois animations d'air avaient été garées chez les voisins, faute de famille à elles :

| Animation | Élément déclaré (avant) | Élément réel |
|---|---|---|
| `ZEPHYR_WIND_LANCE` (22) | `DeepElement.WOOD` | Vent |
| `TEMPEST_EYE` (42) | `DeepElement.WOOD` | Vent |
| `SANDSTORM_DUNE` (41) | `DeepElement.EARTH` | Vent |

Conséquence en jeu : la rotation du Bois était `{ZEPHYR_WIND_LANCE, THORN_MAW_ROOT,
VERDANT_SAP_BLOOM}`. **Une fois sur trois, frapper avec une arme « nature » jouait la lance de
vent** — un arc pâle, bleuté-blanc, que l'œil lit immédiatement comme de l'eau. Et comme
`ZEPHYR_WIND_LANCE` appartenait *aussi* au pool `ANIM_WIND`, c'était le seul effet du mod présent
dans deux familles à la fois.

L'autre moitié du symptôme (« l'eau a des slashs de nature ») avait déjà été corrigée en v1.4 :
sans branche `WATER` dans `animationSlashEffect`, l'eau retombait dans le `return` par défaut,
c'est-à-dire le trio du Bois. Les deux bugs avaient donc la même racine : **le défaut de
l'aiguillage, c'était « nature »**, et tout ce qui n'était pas explicitement routé y atterrissait.

### 15.2 La contrainte qui dictait la solution

`DeepElement.ordinal()` n'est pas un simple index : il sert de **numéro de bit** dans les masques
de palette (`1 << el.ordinal()`, `MASK_ALL`), lus par `WeaponWorldVfxManager`,
`WeaponVfxServerListener`, `EnchantmentComboRegistry`, `MinersEyeClientXray` et
`WeaponVisualEffectId.elementMaskOf`.

Insérer `WIND` au milieu de l'énumération aurait décalé **tous** les masques déjà en circulation —
y compris ceux stockés en NBT sur les armes existantes. `WIND` est donc ajouté **après `CHAOS`**,
en dernière position (ordinal 12) : les douze familles historiques gardent leur bit, et `MASK_ALL`
s'élargit simplement de `0xFFF` à `0x1FFF`.

### 15.3 Ce qui a changé

* **`DeepElement.WIND("Vent", 0x9FF2DC, 0x4FB8A4, 0xE6FFF8)`**, ajouté en fin d'énumération.
* Les trois animations d'air déclarent `DeepElement.WIND`.
* `WeaponVisualEffectId.animationsOf(WIND)` renvoie la rotation explicite
  `{ZEPHYR_WIND_LANCE, SANDSTORM_DUNE, TEMPEST_EYE}`, comme Terre et Métal.
  Les exclusions `fx != SANDSTORM_DUNE && fx != TEMPEST_EYE` qui bricolaient le résultat dans
  `signatureFor` et `animationsOf` **ont disparu** : elles n'avaient de sens que tant que le vent
  squattait une autre famille.
* Pool serveur : `ANIM_GAIA` devient **`ANIM_WOOD = {THORN_MAW_ROOT, VERDANT_SAP_BLOOM}`**.
  Plus aucun effet n'appartient à deux familles.
* Aiguillage client : `case WIND -> impactWindElement(iv)` dans le `switch` d'impact.
  `impactWood` et `impactEarth` ne détournent plus les identifiants du vent au vol.
* `applyVoxelMaterial` : le vent a sa branche d'élément (`cubeVeil 0.94`, `cubeTex 0.050`)
  au lieu d'une rustine par identifiant placée avant le `switch`.
* `WeaponVfxSubtle.shapeFor` reçoit `case WIND` — dérive, brume, poussière, feuille emportée.
  **Ce point était bloquant** : c'est un `switch` *expression* exhaustif sans `default`, donc
  l'ajout d'une constante à l'énumération l'aurait fait échouer à la compilation.

### 15.4 « nature » s'appelait mal : c'est le Bois

La famille `/anim` s'appelait `nature`, un nom qui n'existe nulle part ailleurs dans le mod :
l'élément correspondant est `DeepElement.WOOD`, « Bois », de couleur écorce `0xA9743C`.

* `AnimFamily.NATURE` → **`AnimFamily.WOOD`** (`"wood"`), y compris dans `ALL` et l'aide `/anim`.
* Le glint passe du vert feuille `0x74C34A` au brun d'écorce `0xA9743C`, la vraie couleur de
  `DeepElement.WOOD`.
* `/anim nature` et `/anim gaia` **continuent de fonctionner** : `canonical()` accepte toujours
  `gaia`, `nature`, `bois`, `wood`, `foret`, `tree`. Aucune arme déjà enchantée ne perd sa famille.
* Les codes couleur des huit familles redeviennent tous distincts : la Terre prend `§a`
  (sa couleur réelle `0x9FC25E` est olive, pas dorée) et le Bois garde `§6`.

### 15.5 Vérifications

Aucun JDK ici, donc trois filets successifs :

1. `java-parser` sur les sept fichiers touchés — syntaxe OK.
2. Audit des `switch` sur `DeepElement` par appariement d'accolades, en ignorant les `switch`
   imbriqués : **un seul** switch expression exhaustif sans `default` existait
   (`WeaponVfxSubtle.shapeFor`), il a été complété. Les autres ont un `default` ou sont des
   instructions.
3. `semcheck.py` en différentiel contre `HEAD` : à numéros de ligne près, **le jeu d'alertes est
   identique à la baseline qui compilait**, aux deux `case` du nouveau `switch` près (faux positif
   connu, §13).

Enfin, un contrôle croisé automatique compare chaque pool `ANIM_*` à l'élément déclaré de chaque
animation : **aucun effet dans deux familles, et chaque animation joue dans la famille de son
élément.**

---

## 16. v1.5b — « la texture du trou disparaît et ne revient que brièvement à la fin »

### 16.1 Deux horloges qui ne se parlaient pas

Le trou du maelström est un **faux trou** : `GhostBlockMask.digFunnel` annonce « air » aux clients
sans toucher au monde (§11). Il faut donc que quelque chose le remplisse à l'écran, sinon le
joueur voit le cratère de terre brut. Ce quelque chose, c'était le bassin d'eau — et les deux
n'étaient pas synchronisés :

| | début | fin |
|---|---|---|
| **Eau du bassin** (anneaux, bras, gorge, paroi, remplissage, cœur) | tick 0 | **tick 120** (`basinLife = 1 − implode`, nul dès t = 0,80) |
| **Trou fantôme** | tick 6 | **tick 128 au bord, ~139 au centre** |

Entre les deux : **une seconde et demie de cratère à nu**. Et comme le geyser part au tick 114,
il repassait brièvement devant le trou — d'où la description exacte du joueur : « ça disparaît,
puis ça revient brièvement à la fin ».

Détail aggravant : le bassin ne meurt pas d'un coup, il **rétrécit** (`1 − implode × 0,22` au bord).
La terre se découvrait donc par le pourtour dès le tick 100, bien avant la disparition complète.

### 16.2 Correctif : une nappe de fermeture en iris

Un vrai remous qui meurt ne s'évapore pas, **il se referme** : la surface se recolle du bord vers
l'axe. C'est exactement ce que fait le bloc 12.

* L'iris se ferme entre **t = 0,54 et t = 0,78** (`sealGrow`), soit juste avant que `basinLife`
  n'atteigne zéro — il n'y a donc jamais d'instant où ni le bassin ni la nappe ne couvre la gorge.
* Tant que le bassin couvre encore, la nappe n'est qu'un **film translucide**
  (`0,42 + 0,58 × implode`) : on continue de voir le tourbillon s'effondrer au travers. Elle ne
  devient franchement opaque qu'au moment où elle devient la seule chose qui cache la terre.
* Une fois l'iris refermé, l'opacité est forcée à son plancher `sealGrow²` **partout**, y compris
  au centre — sans quoi l'axe restait translucide et laissait voir le sol.
* Un bourrelet d'écume court sur la lèvre de l'iris : le front qui se referme se lit.

### 16.3 Et surtout : le sol revient SOUS l'eau

Le correctif visuel ne suffisait pas — il fallait aussi que le rebouchage tombe au bon moment.
`digFunnel` passe de `gameNow + 128` à **`gameNow + 118`** : la dernière colonne se rebouche au
tick ~129 au lieu de ~139.

L'ordre est désormais garanti :

```
tick 114 … 141   nappe pleinement opaque (0,95)
tick 118 … 129   le sol réapparaît, bord puis centre   <- entièrement sous la nappe
tick 140 … 148   la nappe s'efface                      <- le sol est déjà là
```

Le joueur ne voit jamais le trou nu, et ne voit jamais non plus les blocs « pop » : ils
reviennent à l'abri sous une surface d'eau opaque.

### 16.4 Vérifications

* Simulation tick par tick de la couverture du rayon creusé (4,35) sur les ticks 80 → 151 :
  **plus aucun tick avec une zone nue** (le résidu de 0,65 bloc au tick 118 du premier jet a été
  supprimé en resserrant l'iris de 0,86 à 0,78).
* Aperçu rejoué sous navigateur (Playwright) aux instants critiques t = 0,60 / 0,70 / 0,76 / 0,80 /
  0,86 / 0,90 / 0,93 / 0,96 : **aucune erreur JS**, et le comptage ne s'effondre plus après le
  tick 120 (567 → 669 voxels au lieu de la poignée du geyser).
* Pic de voxels : **1 617** à t = 0,70 (contre 1 283 avant), très en deçà du plafond dur de 7 000
  par frame. Le surcoût n'existe que pendant le recouvrement, quand le bassin s'efface déjà.

---

## 17. v1.5c — Le seuil d'alpha du moteur : pourquoi la nappe v1.5b était invisible

### 17.1 Le verdict de la partie d'essai

La nappe de fermeture livrée en v1.5b a été jugée **« encore pire qu'avant »**, suivie de
l'observation décisive du joueur :

> « les blocs pixels trop transparents n'apparaissent pas du tout en jeu »

Cette phrase a permis de trouver la cause réelle, qui n'est ni dans l'eau ni dans le masque
fantôme mais dans le **moteur de rendu voxel** lui-même.

### 17.2 Le tamis T350 : sous alpha 200, le moteur jette les cubes

`WeaponWorldVfxManager.emitVoxelCube()` et `emitVoxelBoxRotated()` contiennent tous deux, depuis
le lot T350-VFX-VARIATIONS, un allègement de charge :

```java
if (pixelDensity < 0.999f && alpha < 200) {
    float keep = pixelHash(0x5EED, (int) (cx * 977.0f + cy * 613.0f + cz * 389.0f));
    if (keep > pixelDensity) return;            // le cube n'est jamais émis
}
```

Et `pixelDensity` vient de la déclinaison tirée (`WeaponVfxVariant`) :

```java
float density = 0.70f - 0.40f * f;              // 0,70 pour la 1re « lite », 0,30 pour la 20e
```

Conséquence, mesurée et non supposée : **tout cube émis sous alpha 200 a 30 à 70 % de chances
d'être purement et simplement supprimé.** Le seuil n'est pas progressif, et il ne s'applique
qu'aux déclinaisons allégées — ce qui explique qu'il soit passé inaperçu jusqu'ici.

### 17.3 Les deux conséquences, dont une antérieure au bug signalé

| Élément | Alpha nominal | Sort réel en jeu |
|---|---|---|
| Nappe v1.5b (`0,42 + 0,58 × implode`) | ≈ 107 pendant toute la phase utile | effacée aux deux tiers → **invisible** |
| Bassin, dès `basinLife` ≈ 0,85 | 255 × 0,88 × 0,85 ≈ 191 | décimé → **désintégration en confettis** |

Le second point est le plus important : **le bug existait déjà avant la v1.5b.** Le bassin du
maelström n'a jamais « fondu » à l'écran, il s'est toujours *désagrégé* à partir du tick ~108,
parce que son alpha est multiplié par `basinLife`. La v1.5b n'a fait qu'ajouter par-dessus un
film que le moteur ne dessinait pas davantage — d'où l'aggravation ressentie.

### 17.4 La règle de conception qui en découle

> **Dans ce moteur, rien ne disparaît par fondu d'alpha. Tout disparaît par la géométrie.**

Un fondu ne produit pas un estompage : il produit une décimation aléatoire, puis le vide. Les
transitions doivent donc être portées par le rayon, la hauteur ou le nombre de cubes — jamais
par l'opacité.

Cette règle est appliquée par un seul point de passage, `WaterSignatureVfx.opaque()` :

```java
private static int opaque(float v) {
    int i = Math.round(v);
    if (i <= 10) return 0;                              // réellement mort : on n'émet rien
    return i < SOLID_FLOOR ? SOLID_FLOOR : Math.min(255, i);   // SOLID_FLOOR = 208
}
```

Les **16 émissions** du maelström passent désormais par cette fonction : un cube est soit absent,
soit franchement dessiné.

### 17.5 La transparence de l'eau n'est pas perdue pour autant

Point essentiel, et c'est ce qui rend le correctif acceptable visuellement : le tamis teste
l'alpha **avant** le voile de matière. L'ordre dans `emitVoxelCube` est :

1. `alphaFloor` / `pixelDensity` → **le cube est gardé ou jeté** ;
2. `cubeVeil` → `alpha = Math.round(alpha * cubeVeil)`.

Or `applyVoxelMaterial` donne `cubeVeil = 0.62f` à l'élément `WATER` (0,78 pour le permafrost).
Un cube verrouillé à 208 arrive donc à l'écran à **129/255** : il est réellement dessiné, et il
reste translucide. On obtient les deux à la fois, là où la v1.5b n'obtenait ni l'un ni l'autre.

### 17.6 La nappe refondue : un iris purement géométrique

```java
float sealGrow = smooth((t - 0.50f) / 0.22f);   // l'iris se FERME   : 0,50 → 0,72
float sealBack = smooth((t - 0.90f) / 0.07f);   // puis se ROUVRE    : 0,90 → 0,97
float innerR = BASIN_RADIUS * Math.max(1.0f - sealGrow, sealBack);
float outerR = BASIN_RADIUS * Math.max(open, 0.92f) * (1.0f - 0.82f * sealBack);
```

L'opacité ne varie plus du tout : la fermeture se lit parce que le trou central se referme, et
la disparition finale se lit parce que la couronne se rouvre *et* se rétracte, jusqu'à
`innerR > outerR` où plus aucun anneau n'est émis. La lèvre (`lip`) ne sert plus qu'à la teinte
de l'écume.

### 17.7 Vérification de la chronologie (150 ticks)

| tick | trou encore creusé | nappe | bassin | état |
|---|---|---|---|---|
| 60 → 117 | r = 4,35 | se ferme | vivant (désormais **opaque**) | couvert par le bassin |
| 120 | r = 3,57 | **pleine 0 → 4,35** | mort | couvert par la nappe |
| 129 | r = 0,09 | pleine | mort | le sol revient **sous** la nappe |
| 135 | 0 | pleine | mort | sol rebouché |
| 138 → 144 | 0 | se rouvre puis meurt | mort | le vrai sol est visible |

**Aucun tick avec le trou à nu**, et le bassin ne meurt plus à l'air libre mais caché sous la
nappe. La couverture est étanche : les anneaux sont espacés de 0,40 m pour une extension
radiale de 0,88 m par dalle, soit un recouvrement de plus de deux fois.

### 17.8 Ce que l'aperçu HTML ne sait pas dire

L'aperçu ne modélise ni le tamis `pixelDensity` ni le voile `cubeVeil` : il affichait sans
broncher les cubes que le jeu supprimait. **Un rendu correct dans l'aperçu ne prouve donc rien
sur la visibilité en jeu.** Le miroir `tools/water_build.js` reproduit maintenant `opaque()`
à l'identique (fonction `op()`), de sorte que les deux implémentations émettent exactement le
même jeu de cubes ; mais la validation de la *visibilité* reste une affaire de lecture du code
du moteur, pas de capture d'écran.

Contrôles passés pour cette livraison : `java-parser` OK · `semcheck.py` en différentiel contre
`HEAD` → **identique** · miroirs `FIXES_MCREATOR` → MIRROR_OK sur les 199 fichiers ·
`node --check` sur le miroir JS et sur le script de la page · Playwright sur 8 instants →
**0 erreur JS**, et plus aucun cube d'eau sous le seuil de 208 aux 10 instants testés.

---

## 18. Maelström v1.6 — la gorge devient un volume d'eau (13 novembre 2026)

Retour de test : « *le centre du maelström est toujours mal rempli ; je ne vois que des petits
pixels dedans ainsi que des mini textures de blocs* », « *les surfaces ne sont pas remplies de
bleu tournoyant abymes* ». La v1.5c avait réglé le **seuil d'alpha** ; elle n'avait pas réglé la
**géométrie**. Voici la cause, mesurée, et sa correction.

### 18.1 La cause : une paroi faite de 11 hélices, quel que soit le rayon

La paroi du bassin était dessinée par un nombre **fixe** d'hélices — 11 — réparties sur toute la
hauteur de l'entonnoir. Or la circonférence, elle, n'est pas fixe : elle vaut `2π·r`, et au bord
du bassin `r = 4,35 m`, soit **27,3 m de tour**. Onze dalles de 0,70 m de large y couvrent
`11 × 0,70 = 7,7 m` : **28 %**. Les 72 % restants étaient des trous, et à travers ces trous on
voyait le fond du cratère, le sol avalé et les blocs réimprimés. D'où la lecture « petits pixels
et mini textures de blocs » : il ne manquait pas de couleur, il manquait de la **matière**.

Couverture mesurée avant correction, du bord vers la gorge :

| profondeur `v` | 0 (bord) | 0,25 | 0,50 | 0,75 | 1 (gorge) |
|---|---|---|---|---|---|
| couverture | **28 %** | 31 % | 35 % | 45 % | 85 % |

La gorge paraissait « moins pire » simplement parce que le rayon y est petit.

### 18.2 La correction : le nombre de dalles est calculé, pas choisi

La paroi est reconstruite en **24 anneaux** et chaque anneau calcule lui-même combien de dalles
il lui faut pour être plein :

```java
count = Math.max(9, Math.round(TAU * radius / (tang * 2.0f * 0.72f)))
```

`tang` est la demi-largeur tangentielle de la dalle ; le facteur `0,72` impose un recouvrement
de 28 %. Le résultat ne dépend plus de la profondeur : **138 % de couverture partout**, de 54
dalles au bord à 15 dans la gorge, soit **929 cubes** pour la paroi.

| profondeur `v` | 0 | 0,17 | 0,33 | 0,50 | 0,67 | 0,83 | 1 |
|---|---|---|---|---|---|---|---|
| couverture | 139 % | 138 % | 139 % | 138 % | 138 % | 138 % | 138 % |

Le reste de la gorge suit la même logique — remplacer le semis par de la matière :

- **Rideau intérieur** : 200 gouttes de 4 à 10 cm → **120 lames larges** de 15 à 26 cm,
  orientées tangentiellement (`rot(..., ang, -(0.30+0.70*v), 0)`) : elles tournent avec l'eau
  au lieu de flotter dedans.
- **Cœur** : 6 → **12** grandes dalles (0,34 → **0,44 m**), 24 → **40** blocs moyens
  (0,155 → **0,230 m**), 90 → **54** éclats (0,055 → **0,115 m**), œil 0,105 → **0,150 m**.
  Moins d'éléments, chacun deux fois plus gros : la gorge est pleine au lieu d'être poudrée.

Budget : **≈ 1 930 voxels** au pic (plafond moteur 7 000/frame).

### 18.3 Aucune texture de bloc dans le maelström

`WaterTerrainVfx` capturait le sol sur un disque de 5 blocs — c'est-à-dire **la bouche de
l'entonnoir tout entière** — et réimprimait ces `BlockState` en train d'y tomber, en boucle
(`u = u - floor(u)`) pendant les 150 ticks. Il y avait donc **en permanence** des cubes de terre,
d'herbe et de pierre en train de tourner au centre.

La capture est désormais **désactivée pour `MODE_MAELSTROM`** : le tourbillon ne montre que de
l'eau. Le sol disparaît sous le masque fantôme (`GhostBlockMask.digFunnel`), anneau par anneau,
et l'eau le remplace. Le raz-de-marée (`MODE_SURGE`) garde ses blocs : il les pousse **devant**
lui, à l'air libre, et c'est là que l'effet se voit.

### 18.4 La règle d'alpha étendue aux trois signatures

La v1.5c n'avait appliqué `opaque()` qu'au maelström. L'audit a montré que `renderSurge` et
`renderRime` émettaient encore des alphas bruts :

| méthode | sites `opaque()` avant | sites bruts avant | après |
|---|---|---|---|
| `renderSurge` | 0 | 8 | **8 `opaque()`** |
| `renderMaelstrom` | 17 | 0 | 17 |
| `renderRime` | 1 | 10 | **11 `opaque()`** |

Les trois alphas « maîtres » (lignes 122, 334, 790) restent bruts : ce sont les fondus globaux
d'entrée et de sortie, et `opaque()` est appliqué en aval, à chaque point d'émission. Le miroir
`tools/water_build.js` reçoit exactement les mêmes 8 et 10 enveloppements, `op()` ayant été hissé
au niveau du fichier.

Conséquence attendue en jeu : la brume de froid et la nappe de sol mouillé, jusqu'ici réduites à
un semis aléatoire de 30 à 70 % de leurs cubes par le tamis, deviennent des surfaces franches.

### 18.5 Contrôles passés

`java-parser` sur les 8 fichiers touchés · `semcheck.py` en différentiel contre `HEAD` →
**identique à la baseline** · miroirs `FIXES_MCREATOR` → **MIRROR_OK sur 209 fichiers, 0 écart** ·
`node --check` sur le miroir JS et sur le script de la page · Playwright sur les 3 variantes et
10 instants → **0 erreur JS**, pic à 1 937 voxels.

---

## 19. Bois — la troisième animation (13 novembre 2026)

Retour de test : « *problème wood ! je ne vois que deux animations, les vignes et le sap tree*,
l'autre a disparu, trouve la cause et répare ».

### 19.1 Cause : ce n'était pas une régression, c'était un emprunt

`git show 442d5a4~1` montre l'ancien pool, à l'époque nommé `ANIM_GAIA` :

```java
{ ZEPHYR_WIND_LANCE, THORN_MAW_ROOT, VERDANT_SAP_BLOOM }
```

La troisième entrée était **la lance de zéphyr**, c'est-à-dire une animation de **vent**. C'est
elle qui faisait apparaître des arcs pâles « eau » dans la rotation du Bois, et c'est pour cette
raison qu'elle a été rendue à son élément lors de la séparation des familles (v1.5, `442d5a4`).
Le Bois n'a donc jamais possédé que **deux** signatures réellement en bois : rien n'est cassé,
il manquait une animation qui n'avait jamais existé. Elle existe maintenant.

### 19.2 `WILDWOOD_AWAKENING` — l'éveil de la futaie

| | |
|---|---|
| identifiant | **43** (1 → 42 étaient contigus et tous pris) |
| catégorie | `Category.ELEMENT`, priorité 74 |
| élément | `DeepElement.WOOD` — couleurs héritées, aucun RGB individuel (`T374-PALETTE-LOCK`) |
| durée | **104 ticks** (`WeaponWorldVfxManager` et `DlbVfxCommand`) |
| mouvement de lame | `SHAPE_BLOOM`, 4 échos, décalage 0,10 / élévation 0,50 / portée 1,20 |
| rendu | `GaiaWorldTreeVfx.GROVE` (= 3) |

Le modèle réutilise **`ZEPHYR_VOXELS`**, l'arbre aux branches étalées resté **orphelin** depuis
que la lance de zéphyr a rejoint le moteur du vent : une silhouette plus large et plus basse que
l'arbre sacré, déjà dessinée, déjà équilibrée, et que plus rien n'utilisait. La futaie s'en sert
sans la moindre lance :

- croissance en **0,38** (entre les 0,30 du zéphyr et les 0,46 du sacré) ;
- **pluie de feuilles nettement plus dense** : départ à 0,28 au lieu de 0,34, pas
  d'échantillonnage 10 au lieu de 7, chute de 3,05 m au lieu de 2,35 ;
- **sève** (`renderSacredSap`), pas de fruits d'or : la futaie est sauvage, pas sacrée ;
- dérive des feuilles en mode « non-zéphyr » (`age × 0,16`), donc **aucune lecture de vent**.

Côté serveur, l'effet joue un **accent sonore seulement** — `WOOD_BREAK` puis `GRASS_BREAK` à
+26 ticks. Aucun maintien, aucun dégât supplémentaire : rien qui puisse interférer avec les
priorités de `MOVEMENT_HOLDS`, de `WIND_HOLDS` ou de `SURGE_RIDES`.

### 19.3 Les sept points d'ancrage

| fichier | modification |
|---|---|
| `WeaponVisualEffectId.java` | déclaration de la constante #43 |
| `WeaponVfxMotion.java` | `case WILDWOOD_AWAKENING` — **obligatoire** : ce `switch` est exhaustif et n'a pas de `default` |
| `WeaponWorldVfxManager.java` | durée 104 · sélecteur `index % 2` → **`% 3`** · routage vers `GROVE` dans `impactWood` |
| `GaiaWorldTreeVfx.java` | constante `GROVE`, choix du modèle, durée de croissance, branche de rendu |
| `WeaponVfxServerListener.java` | ajout à `ANIM_WOOD` · accent sonore |
| `DlbVfxCommand.java` | durée 104 · créneau de test `dummyNextEffectTick` = 140 |

`/anim wood` fait tourner le pool par `DlbAnimNext % pool.length` : il joue donc bien **trois**
animations en boucle, et la rotation de l'enchantement suit la même règle via `index % 3`.

### 19.4 Contrôles passés

`java-parser` sur les 7 fichiers · audit des `switch` **aplatis au niveau 1** → le seul `switch`
exhaustif sans `default` portant sur ces constantes (`WeaponVfxMotion:105`) **couvre** le nouvel
identifiant · unicité des identifiants → 0 doublon · `BY_ID` dimensionné à 64, l'entrée 43 y
tient · `semcheck.py` en différentiel → seul écart, le faux positif connu « constante inconnue »
sur les `case` non qualifiés · miroirs `FIXES_MCREATOR` → **MIRROR_OK sur 209 fichiers**.

---

## §20 — v1.7 : pourquoi le cratère restait visible, et le banyan de la futaie

### 20.1 Le trou du maelström — trois causes, dont une jamais soupçonnée

En v1.6 la couverture géométrique de la paroi avait été portée de 28 % à 138 % et le nombre de
dalles de 410 à 929. Le joueur a pourtant répondu : « on voit quand même toujours le trou ».
Le diagnostic par les chiffres a montré que la densité n'était **pas** en cause. Trois défauts
indépendants se superposaient.

**Cause nº 1 — le profil de la paroi ne suivait pas celui du trou.** `GhostBlockMask.digFunnel`
creuse un **paraboloïde** : `depth = BASIN_DEPTH × (1 − ratio²)`. Le bloc 9 dessinait, lui, un
**cône** décalé : `y = −0,95 − BASIN_DEPTH × v²`. Les deux surfaces ne coïncident nulle part :

| v | rayon de la paroi | y de la paroi | fond réel du trou | verdict |
|---|---|---|---|---|
| 0,00 | 4,35 | −0,95 | 0,00 | dalles enterrées, invisibles |
| 0,26 | 3,37 | −1,17 | −1,31 | **14 cm de terre à nu** |
| 0,39 | 2,89 | −1,46 | −1,85 | **39 cm de terre à nu** |
| 0,52 | 2,40 | −1,85 | −2,30 | **45 cm de terre à nu** |
| 0,65 | 1,91 | −2,35 | −2,66 | **31 cm de terre à nu** |
| 0,78 | 1,42 | −2,97 | −2,95 | couvre |

La paroi *flottait au-dessus du fond* sur toute la couronne médiane, et la toute première couche
du cratère (de `y = 0` à `y = −0,95`) n'était couverte par **rien**. Aucune augmentation de
densité ne pouvait corriger cela : les dalles étaient denses, mais au mauvais endroit.

Correctif — le profil est désormais la copie conforme du masque, avec deux marges :

```java
float rr   = 1.0f - v * 0.86f;                                   // rayon normalisé
float sink = BASIN_DEPTH * (1.0f - rr * rr) + 0.70f * (1.0f - rr); // paraboloïde + débord
float y0   = -0.18f - sink * open;                               // -0,18 : arase du sol
float slope = -(0.18f + 0.82f * rr * open);                      // tangente du paraboloïde
```

Le terme `0,70 × (1 − rr)` fait passer la dalle **sous** le fond, ce qui absorbe l'arrondi du
masque en couches entières. La pente est inversée : raide au bord, presque plate dans la gorge —
l'ancien cône inclinait les dalles à contresens.

**Cause nº 2 — l'emprise de la paroi était multipliée par `open`.** Le masque atteint le bord du
cratère au tick 20 ; la paroi, dont le rayon valait `BASIN_RADIUS × open`, ne l'atteignait qu'au
tick 30. Pendant dix ticks le sol était effacé là où aucune eau n'était encore dessinée. Le
facteur `open` a été retiré du rayon : l'emprise au sol est pleine dès le premier tick et c'est
la **profondeur**, elle seule, qui s'enfonce.

**Cause nº 3 — la nappe de surface n'existait qu'à partir de t = 0,50.** Elle n'était dessinée
que si `sealGrow > 0,002`, soit à partir du tick 75. Elle couvre maintenant toute la durée, son
rayon intérieur enchaînant trois régimes par un simple `min/max` :

```java
float innerR = BASIN_RADIUS * Math.min(open, Math.max(1.0f - sealGrow, sealBack));
```

Pleine au tick 0, elle s'ouvre au rythme de `open`, se referme en iris de 0,50 à 0,72, puis se
rouvre de 0,90 à 0,97 pour la résorption. Entre les ticks 30 et 75 elle ne coûte rien :
`innerR == outerR`, la boucle ne tourne pas.

### 20.2 Recalage des deux horloges

Les deux horloges — celle du masque serveur et celle du visuel client — sont désormais vérifiées
couche par couche. `GhostBlockMask` a reçu une constante `LAYER_STAGGER = 5` : à une couche par
tick le trou atteignait son fond en trois ticks quand la paroi met trente ticks à descendre.
L'ouverture passe de `gameNow + 6` à `gameNow + 14`.

| distance | couche | trou ouvert | paroi descendue | marge |
|---|---|---|---|---|
| 0,00 | 0 / 1 / 2 | 14 / 19 / 24 | 9 / 15 / 20 | +5 / +4 / +4 |
| 1,00 | 0 / 1 / 2 | 17 / 22 / 27 | 10 / 15 / 21 | +7 / +7 / +6 |
| 2,00 | 0 / 1 / 2 | 20 / 25 / 30 | 11 / 18 / 26 | +9 / +7 / +4 |
| 3,00 | 0 / 1 | 24 / 29 | 14 / 26 | +10 / +3 |
| 3,50 | 0 | 25 | 18 | +7 |

**Vingt couples (distance, couche) vérifiés, zéro marge négative**, minimum +3 ticks. La fermeture
reste à `gameNow + 118` : le sol revient entre les ticks 118 et 129, sous la nappe pleinement
opaque de 108 à 135.

### 20.3 Coût

30 anneaux au lieu de 24, 1 085 dalles au lieu de 929. Couverture tangentielle 138 %, couverture
**radiale 195 à 213 %** — c'est elle qui manquait. Pic mesuré dans l'aperçu : **2 117 voxels**,
pour un plafond de 7 000 par image.

### 20.4 `WILDWOOD_AWAKENING` — un banyan, et non une variante de l'arbre sacré

Reproche du joueur : « tu répètes sap ! où est passée la 3ᵉ animation wood ? ». Il avait raison,
et la cause était dans le code : le variant `GROVE` réutilisait `ZEPHYR_VOXELS` et appelait
`renderFallingLeaves` **et** `renderSacredSap` — exactement les deux routines de
`VERDANT_SAP_BLOOM`. À quelques paramètres près, c'était la même animation.

Tout a été refait. La silhouette n'est plus une chaîne packée recopiée mais un modèle
**construit** par `buildGrove()`, donc impossible à confondre par accident :

| pièce | description | voxels |
|---|---|---|
| tronc | trapu, cannelé (8 cannelures), 2,20 m, en torsion | 135 |
| **contreforts** | **six arcs qui quittent le tronc à 1,6 m et replongent au sol** | 90 |
| couronne | **parasol plat** de 2,6 m de rayon, 3 coques, et non une boule | 227 |
| lianes | onze lianes pendantes sous le parasol | 77 |
| | **total** | **529** |

```
                @   @  @  @ @@ @   @ @ @
              @ @   @ @   @ @@ @@   @ @@@@@          @ parasol
          @@@ @                          @@@@@
         @                                     @@
        @ @                                      @ @
       @          |                    |             @   | lianes
                  |       ######       |
                          ######                         # tronc
                  |     \\\\\\\\\\     |
                       \\# ## ## \\    |
                  |    \ # ## ##  \                      \ contreforts
                      \  #### ###  \   |
                  | \    #### ###    \
```

Hauteur 2,3 m pour 5,3 m de large, là où l'arbre sacré monte à 4,7 m sur un fût étroit : les deux
silhouettes ne se ressemblent en rien.

La chorégraphie est elle aussi exclusive — **ni sève, ni fruits, ni lances** :

* `renderGroveRoots` : neuf racines percent le sol en anneau, rampent vers l'extérieur segment
  par segment en ondulant, portent des pousses vertes sur leur dos, puis se retirent. Cette
  animation se joue **au ras du sol**, quand les deux autres bois se jouent en hauteur.
* `renderGroveSpores` : soixante-quatre spores **montent** en spirale depuis le sol et mûrissent
  du vert au doré en s'élevant — mouvement exactement **inverse** de la pluie de feuilles des
  deux autres arbres. C'est le repère le plus immédiat pour les distinguer d'un seul regard.

### 20.5 Contrôles

`java-parser` sur les 4 fichiers modifiés · `semcheck.py` → `OK` sur les trois fichiers touchés ·
vérification numérique des 20 couples (distance, couche) → 0 anomalie · `node --check` sur le
miroir JS · réinjection de `buildWater` dans l'aperçu par appariement d'accolades, 2 blocs
`<script>` valides · Playwright sur 9 instants → **aucune erreur JS**, pic 2 117 voxels ·
miroirs `FIXES_MCREATOR` → **MIRROR_OK sur 209 fichiers, 0 écart**.

---

## §21 — Foudre : les trois signatures et la charge statique (4 novembre 2026)

Neuvième famille refondue sur treize. Les trois effets de foudre existaient dans l'énumération
depuis l'origine, mais deux d'entre eux ne faisaient rien de particulier et le troisième,
`CHAIN_VOLT_ARC`, n'était **jamais tiré** : il figurait dans la liste des effets ELEMENT morts.
Cette version lui donne une chorégraphie, un gameplay, et le branche à la commande.

| Effet | id | Durée | Rôle |
|---|---|---|---|
| `CHAIN_VOLT_ARC` | 16 | 130 t | L'éclair qui tombe du ciel, puis saute de cible en cible |
| `OVERCLOCK_CYBER_X` | 8 | 120 t | La surcharge : on se suralimente, puis on explose |
| `TWIN_ECHO_SLASH` | 4 | 96 t | Deux entailles croisées, la seconde en écho |

### 21.1 Le piège du `case` en double

Porter `CHAIN_VOLT_ARC` de 11 à 130 ticks n'était pas une addition mais un **remplacement** :
la constante figurait déjà dans le `switch` des durées. Ajouter la nouvelle ligne sans retirer
l'ancienne donne deux `case` pour la même valeur dans le même `switch` — en Java c'est une
erreur de compilation, pas un avertissement. La règle qui en sort : avant d'ajouter un `case`,
toujours vérifier que la constante n'est pas déjà listée ailleurs dans le même bloc.

### 21.2 Trois sons qui n'existaient pas

Le premier jet utilisait `SoundEvents.LIGHTNING_BOLT_IMPACT`, `NOTE_BLOCK_BIT` et
`TRIDENT_THUNDER`. Un `grep` sur le dossier source semblait confirmer qu'ils étaient déjà
employés ailleurs — sauf que le `grep` voyait **mes propres lignes**, écrites quelques minutes
plus tôt. Interrogé sur le dernier commit (`git grep … HEAD`), le dépôt répond : zéro occurrence.
Ces trois noms n'ont jamais été utilisés dans ce projet, et rien ne garantissait qu'ils existent
dans la version de Minecraft visée. C'est exactement la classe d'erreur que `java-parser` laisse
passer : la syntaxe est parfaite, le symbole est introuvable à la compilation.

Les trois ont été remplacés par des sons **attestés dans le code déjà compilé**, en conservant la
forme d'accès exacte (certains sons sont des `Holder` et exigent `.value()`) :

| Voulu | Retenu | Preuve |
|---|---|---|
| `LIGHTNING_BOLT_IMPACT` | `LIGHTNING_BOLT_THUNDER` | 8 usages au HEAD |
| `NOTE_BLOCK_BIT.value()` | `NOTE_BLOCK_BELL.value()` | 12 usages au HEAD |
| `TRIDENT_THUNDER.value()` | `TRIDENT_THROW.value()` | 6 usages au HEAD |

Un contrôle final liste les 11 symboles `SoundEvents` / `ParticleTypes` / `MobEffects` de la
section foudre et les confronte au HEAD : **11 prouvés, 0 absent**. La règle générale :
*ne jamais faire confiance à un `grep` sur l'arbre de travail pour prouver qu'un symbole existe —
interroger le dernier commit.*

### 21.3 La charge statique

Le liant des trois effets. Chaque coup de foudre dépose une pile sur la victime ; chaque pile
majore les dégâts de foudre suivants de 15 %, jusqu'à 5 piles (+75 %), et s'efface après
140 ticks sans nouveau coup. À la sixième pile la cible **claque** : décharge sur elle-même et
sur trois voisins au plus, dans un rayon de 3,20 bloc.

C'est là qu'était le vrai danger. La décharge déposait une pile sur chaque voisin **en repassant
par la fonction d'ajout** — donc un voisin déjà chargé claquait à son tour, ce qui rechargeait le
premier, qui reclaquait. Trois ennemis serrés suffisaient à refermer l'anneau et à faire déborder
la pile d'appels : plus un crash qu'un effet. La décharge dépose désormais sa pile **directement
dans la table**, sans jamais rappeler la fonction d'ajout, et plafonne à `STATIC_MAX - 1`.
Une éclaboussure ne peut donc pas en déclencher une autre : il faut une nouvelle frappe du joueur.

### 21.4 Les trois gameplays

**`CHAIN_VOLT_ARC`** — l'impact principal tombe au tick 20. Au tick 24 on repère la chaîne :
six relais au plus, de proche en proche, rayon 7, jamais un joueur, un ensemble d'identifiants
interdisant de toucher deux fois la même créature. Chaque saut retire 18 % de puissance.
*Si la cible est seule*, l'énergie des six relais ne disparaît pas : elle revient en trois
décharges successives sur elle.

**`OVERCLOCK_CYBER_X`** — la surcharge se paie d'avance. Hâte III, Vitesse II et Force I
pendant 140 ticks, quatre paliers sonores, puis au tick 94 la nova, rayon 4,50. Seul contre un
ennemi unique elle frappe à 90 % et pose deux piles ; contre un groupe elle frappe à 45 % et
ralentit. L'effet récompense donc le duel et reste correct en mêlée.

**`TWIN_ECHO_SLASH`** — deux passes : la lame au tick 8, l'écho au tick 32 à 70 %. L'écho
**consomme** les piles accumulées par la première passe et les convertit en dégâts
(+35 % par pile) : l'enchaînement est le sujet de l'effet. Le débordement suit un arc de 210°
devant le joueur — un produit scalaire inférieur à −0,259 exclut ce qui est dans le dos.

### 21.5 Le X des deux lames

`drawBlade` dessinait les deux entailles dans le même plan horizontal : au lieu d'un X, on voyait
deux arcs superposés. La relecture du fichier a montré pourquoi — plusieurs termes étaient
multipliés par `0.0f`, et un drapeau `first` ne servait plus à rien. Ce sont les restes d'une
intention jamais appliquée : l'inclinaison avait été prévue, puis neutralisée.

La fonction prend désormais un paramètre `tilt` (0 = lame à plat, 1 = lame verticale) qui verse
la coordonnée latérale dans la hauteur : `flat = 1 - tilt`, `y = 0.95 + lat * tilt * 0.92`,
`pitch = tilt * 1,5708`. La première lame passe à plat, la seconde à la verticale et en sens
inverse : les deux entailles décrivent un X dans l'espace. *Leçon : un terme multiplié par zéro
ou une variable morte signalent presque toujours une intention oubliée en chemin.*

### 21.6 Contrôles

`java-parser` sur les 4 fichiers modifiés + le nouveau · `semcheck.py` **en différentiel contre
le HEAD** : 41 lignes avant, 38 après, **aucun nouvel écart** introduit par la foudre ·
11/11 symboles vanilla prouvés au HEAD · vérification qu'aucune variable mutée n'est capturée
par une lambda · `WeaponVfxMotion.of()` (le seul `switch` exhaustif sans `default`) contient bien
les trois constantes · miroirs `FIXES_MCREATOR` → **MIRROR_OK sur 210 fichiers, 0 écart**.

Le gameplay de la foudre n'immobilise jamais : il ne pose que `MOVEMENT_SLOWDOWN`. Il ne peut
donc pas entrer en conflit avec `MOVEMENT_HOLDS`, `WIND_HOLDS` ou `SURGE_RIDES`.

---

## §22 — « Pas assez électrique » : refonte des signatures 2 et 3 (4 novembre 2026)

Verdict de l'essai : l'arc en chaîne passait, les deux autres non. Le diagnostic tient en deux
phrases. La **Surcadence** n'était pas électrique, elle était *électronique* — cage néon
parfaitement régulière, pistes de circuit imprimé, histogramme de cadence, anneaux de scan :
rien n'y claquait, tout y était lisse et stable. La **Double entaille** n'était pas une
décharge, c'était un *ruban lisse* — un arc de cercle parfait, donc une lame d'énergie
générique, interchangeable avec celle de n'importe quel autre élément.

### 22.1 Ce qui rend un trait électrique

Un néon et un éclair se distinguent par **l'instabilité**, pas par la couleur. Un tracé qui ne
change pas d'une image à l'autre est perçu comme un tube de néon, même violet, même avec un cœur
blanc. D'où l'horloge de scintillement introduite ici :

```java
int strobe = (int) (t * 34.0f);   // la graine avance par paliers
```

En injectant `strobe` dans la graine des tracés, la cage entière est **redessinée plusieurs fois
par seconde**. Une arête sur cinq s'éteint à chaque palier. L'œil ne lit plus une structure
mais une décharge qui peine à s'établir.

### 22.2 Surcadence — d'une carte électronique à une installation haute tension

| Avant | Après |
|---|---|
| 12 arêtes néon régulières | 12 **arcs** fractals qui se retracent et papillotent |
| Pistes de circuit imprimé (vert PCB) | **Échelle de Jacob** entre électrodes voisines |
| Histogramme de cadence, anneaux de scan | **Contournement au sol** : la terre fuit |
| X en chaîne de cubes | X de **deux éclairs** croisés en rotation |
| 4 pylônes décoratifs | 4 **électrodes** à couronne d'effluves |

L'échelle de Jacob est l'ajout décisif : l'arc s'amorce en bas entre deux électrodes, monte,
s'allonge donc s'affaiblit, puis **rompt net** et se rallume en bas. C'est l'image de haute
tension la plus reconnaissable qui soit, et elle raconte exactement ce que fait l'effet —
une machine poussée au-delà de son régime.

La constante `PCB` est devenue inutile et a été **supprimée** plutôt que laissée en place :
une couleur déclarée que personne n'emploie est du code mort, et le code mort masque toujours
une intention abandonnée (cf. §21.5).

### 22.3 La lame — et le bug de la dalle violette

Le tranchant est désormais tracé par une nouvelle primitive, `boltArc`, qui déplace
fractalement une polyligne **le long d'un arc** et non d'un segment droit. `drawBolt` ne sait
relier que deux points ; l'appeler segment par segment aurait annulé le bruit à chaque jonction
et redonné un cercle.

Premier jet : **la lame est devenue une dalle violette pleine**, un bloc informe occupant tout
l'écran. Cause exacte : `drawBolt` traite son paramètre `thick` comme un *facteur* de
`BOLT_CORE` (0,075), alors que `boltArc` l'utilisait comme une *épaisseur absolue*. Avec
`thick = 0,60`, la gaine passait de 0,10 à **1,38 bloc de demi-épaisseur**, soit quatorze fois
trop. Deux fonctions voisines, un paramètre de même nom, deux unités différentes : la
multiplication par `BOLT_CORE` a été rétablie dans `boltArc`, et le garde-fou d'entrée
(`thick <= 0.004`) relevé à `0.02` pour qu'il teste bien un facteur.

Seconde correction, plus subtile : même avec un tranchant zigzaguant, la lame restait molle,
parce que **le corps du ruban suivait toujours un cercle parfait**. Corps et tranchant partagent
maintenant la même graine et la même amplitude, donc le même déplacement fractal — la coupe
entière zigzague. Des filaments partent des bords, et les arcs de liaison relient un point
**pris sur** la lame horizontale à un point **pris sur** la lame verticale, au lieu de dix arcs
tirés au hasard dans une sphère.

### 22.4 Contrôles

`java-parser` OK · `semcheck.py` OK · aucune variable mutée capturée par une lambda ·
miroir JS mis à jour dans le même tour, `node --check` OK · **0 NaN** et **0 voxel sous le
plancher d'opacité** sur 99 instants × 3 variantes · Playwright 3 × 6 instants, **0 erreur JS**,
comptes DOM identiques à ceux de node · pics 492 / 883 / 771 cubes, très en deçà du plafond de
7 000 · miroirs `FIXES_MCREATOR` **210/210**.

Les durées (130 / 120 / 96 ticks) et tout le gameplay serveur sont **inchangés** : cette refonte
est purement visuelle, elle ne retouche ni les dégâts, ni la charge statique, ni les sons.

---

## 23. Foudre v3 — proportions, plancher, et un barème qui ne se paie plus tout seul

La refonte v2 a été rejetée sur trois points. Les trois étaient justes, et les trois se mesurent.

### 23.1 « Des cubes giga épais »

Mesure du rapport **longueur de segment / largeur de gaine** sur la v2 :

| élément | longueur de segment | largeur de gaine | rapport |
|---|---|---|---|
| échelle de Jacob | 0,136 | 0,276 | **0,49** |
| X croisé | 0,177 | 0,294 | **0,60** |
| trait maître | 0,393 | 0,367 | 1,07 |
| cage | 0,293 | 0,221 | 1,33 |

Un segment de l'échelle de Jacob était **deux fois plus large que long**. Ce n'est pas un arc,
c'est un cube ; aligner des cubes ne fait pas un éclair, cela fait un chapelet. L'erreur de la
v2 est d'avoir augmenté le nombre de nœuds pour obtenir du détail **sans toucher à l'épaisseur** :
plus on subdivise, plus les segments raccourcissent, et à épaisseur constante ils finissent carrés.

Deux leviers ont été actionnés ensemble. `BOLT_CORE` descend de **0,075 à 0,040**, et chaque
appel a vu son nombre de nœuds recalculé pour que la longueur de segment reste supérieure au
triple de la largeur : échelle de Jacob 14 → 7 nœuds, X croisé 13 → 8, cage 9 → 8, trait maître
56 → 44, relais de chaîne 20 → 12, liaison d'écho 11 → 8.

| élément | longueur de segment | largeur de gaine | rapport |
|---|---|---|---|
| échelle de Jacob | 0,271 | 0,071 | **3,85** |
| X croisé | 0,287 | 0,090 | **3,19** |
| cage | 0,330 | 0,098 | **3,35** |
| trait maître | 0,500 | 0,164 | **3,05** |

Sur l'ensemble des segments réellement émis (99 instants × 3 variantes, section carrée isolée),
le rapport **moyen** passe de moins de 1 à **9,6 / 5,6 / 6,7**.

Les billes de plasma de la Surcadence relevaient du même reproche : des cubes de 0,22 de côté
couraient le long de montants larges de 0,13 — la bille était deux fois plus grosse que le
conducteur. Une charge qui file se lit comme un **trait étiré dans son sens de marche**, pas
comme un dé : la traînée est devenue une suite de segments effilés reliant les positions
successives, surmontée d'une tête à peine plus large.

### 23.2 « Le 3ᵉ passe sous le sol »

La lame verticale était un cercle de rayon 3,84 centré à `y = 0,95`. Son point bas tombait donc
à `0,95 − 0,92 × 3,84 = −2,58`, pour un sol à **−0,44** : **2,14 bloc sous terre**, soit près de
la moitié de la figure invisible et payée quand même.

Une constante `GROUND_Y = −0,42` borne désormais tout le fichier. La lame inclinée est
raccourcie d'un tiers **et** son pivot est relevé de sorte que le bas du cercle vienne effleurer
le sol : la coupe devient une roue posée au sol, lisible en entier, à **−0,42 exactement** pour
les trois échelles (1,08 / 0,80 / 0,65). Deux autres fuites ont été trouvées au passage :

- les **arcs rampants** de la Surcadence plongeaient à −0,59 ; ils sont plaqués au sol, ce qui
  est de toute façon le sens d'un contournement de surface ;
- le **flash d'impact** de l'Arc fulgurant était une sphère pleine centrée à 0,30 pour un rayon
  vertical de 1,4 : sa moitié basse était enterrée. Le sol la tronque — ce qu'on voit d'un vrai
  impact est une coupole, pas une bille.

Résultat : `y_min` = −0,46 / −0,44 / −0,43 contre −1,06 / −0,59 / −2,58.

### 23.3 « Mal optimisée »

Le ruban de la lame se recouvrait lui-même : chaque section faisait 0,30 de large sur 0,50 de
long pour un pas de 0,24 le long de l'arc. Les demi-arêtes ont été divisées par deux et les
sections ramenées de 44 à 34, et surtout la demi-longueur dans le sens de l'arc est maintenant
**déduite du pas réel** (`r × reach / segments × 0,58`) au lieu d'être une constante — une
constante ne peut convenir ni aux deux lames ni aux rémanences, et la figer fragmentait le ruban
en plaquettes détachées. Les rémanences passent de quatre à trois.

Pic de cubes : **456 / 777 / 584** contre 492 / 928 / 771, pour une lecture nettement plus nette.

### 23.4 « Dégâts mal calculés »

Le vrai défaut n'était pas le chiffre, c'était la **boucle fermée** : la première passe posait
une pile de charge statique que la seconde consommait aussitôt. La signature s'offrait donc
`+35 %` à chaque utilisation, sans la moindre décision du joueur — et le débordement faisait
pire, puisque les deux passes posaient une pile sur chaque victime du groupe, la seconde se
payant le multiplicateur de la première.

Trois corrections :

1. **La pile n'est plus posée que par la première passe**, sur la cible comme sur le débordement.
   L'écho ne consomme donc plus que des piles venues d'**autres** coups de foudre : le bonus
   récompense un enchaînement, pas le simple fait d'appuyer.
2. Barème ramené de `0,80` à **`0,55`** (plancher 1,5 → 1,2), bonus de pile `+35 %` → **`+30 %`**,
   débordement `0,55` → **`0,45`**.
3. Rayon du débordement ramené de 3,40 à **3,05**, qui est le rayon de la lame telle qu'elle est
   réellement dessinée. Mordre au-delà du visible donne l'impression de dégâts sortis de nulle part.

Pour une arme à 7 dégâts, en plus du coup normal du joueur :

| situation | avant | après |
|---|---|---|
| cible seule | 10,89 (**1,56×** l'arme) | **6,55** (0,94×) |
| 1 pile d'un autre coup | 12,26 | 7,35 |
| 3 piles | 15,01 | 8,97 |
| 5 piles (maximum) | 17,75 | **10,59** |

L'écho reste le gros lot de la famille, mais il faut désormais le préparer.

### 23.5 Contrôles

`java-parser` OK sur les deux fichiers · `semcheck.py` OK · `lamcheck2` **rigoureusement
identique à HEAD** sur le gameplay, donc aucune régression de capture introduite · miroir JS
porté dans le même tour, `node --check` OK · **0 NaN**, **0 voxel sous le plancher d'opacité**,
**0 voxel sous le sol** sur 99 instants × 3 variantes · Playwright 3 × 6 instants, **0 erreur
JS**, comptes DOM identiques à node · miroirs `FIXES_MCREATOR` **237/237** · durées
130 / 120 / 96 ticks inchangées.

---

## 24. Foudre v4 — une peau 8 bits, et une signature entièrement remplacée

Les v3 des signatures 2 et 3 ont été jugées pires que la v1. Plutôt que de les retoucher une
troisième fois, elles sont reprises à la base, chacune dans une direction différente.

### 24.1 Surcadence Cyber-X : pixel art 8 bits

Le style n'est pas un filtre qu'on applique après coup, c'est un ensemble de **contraintes**.
Elles sont toutes tenues, et vérifiables :

| règle | mise en œuvre | contrôle |
|---|---|---|
| grille fixe | `snap()` aligne tout sur `PIX = 0,1875` | — |
| un cube = un pixel | `pix()` force un cube de côté `PIX`, jamais autre chose | — |
| aucune rotation | tout passe par `box()`, `rot()` n'est plus appelé | **0 cube tourné** sur 99 instants |
| palette fermée | 7 teintes indexées dans `GB[]`, aucune interpolation | — |
| horloge à paliers | 18 images pour 120 ticks, `fr = (int)(t × 18)` | — |
| tracés entiers | cercles rasterisés au **Bresenham** | — |

La chorégraphie est neuve : le cadre se trace en balayage, quatre sprites d'éclair définis en
ASCII dans le code alternent sur trois poses sans jamais pivoter, une jauge de surcharge se
remplit case par case, un anneau de Bresenham gagne un pixel à la fois, et la décharge finit en
nappe au sol **tramée en damier** — car sur une console un dégradé se fait au damier, pas à
l'alpha. C'est aussi ce qui la rend compatible avec le tamis du moteur, qui décime tout alpha
inférieur à 200.

Le gameplay de la Surcadence est **inchangé** : seule la peau change.

### 24.2 L'« Appel de l'orage » remplace la « Double entaille d'écho »

L'ancienne signature frappait deux fois au même endroit et ne demandait rien au joueur. La
nouvelle est un **contrat de position** : le nuage se noue à huit blocs au-dessus du point
d'impact et n'y bouge plus.

- **0 → 27** : 112 bouffées convergent de l'extérieur et le cumulonimbus se noue ;
- **34 → 105** : cinq éclairs tombent sur des points dispersés, chacun avec deux ramifications,
  son flash et sa brûlure. Le **ventre du nuage s'illumine avant chaque départ** — c'est le
  seul avertissement, et il est volontairement lisible ;
- **105 → 133** : le nuage se contracte de 30 %, une colonne d'appel monte du sol ;
- **133 → 153** : la décharge, **sept traits serrés** plutôt qu'un seul tronc, car un trait
  assez épais pour paraître massif cesse d'être un éclair (voir §23.1) ;
- **143 → 170** : l'onde de choc s'ouvre jusqu'à dix blocs.

Dégâts, pour une base de `max(1,2 ; arme × 0,55)` :

| situation | dégâts | rapport à l'arme |
|---|---|---|
| cible immobile sous le nuage | 15,79 | **2,26 ×** |
| cible qui sort après deux frappes | 2,31 | 0,33 × |
| mob à dix blocs (répercussion) | 0,85 | 0,12 × |

Le rapport de 2,26 est volontairement élevé : il se paie de **sept secondes** pendant lesquelles
tout est télégraphié et la zone est fixe. Sortir divise les dégâts par sept.

### 24.3 Ce que le remplacement a entraîné ailleurs

Renommer n'aurait servi à rien si le reste du mod avait continué de parler d'écho :

- `TWIN_ECHO_SLASH` → `STORMCALL_JUDGMENT`, **21 occurrences dans 8 fichiers**. L'identifiant
  numérique **4 est conservé** : c'est lui la clé de persistance et de dispatch, et les
  enchantements déclencheurs (`echo_strike`, `combo_slash`, …) sont intacts ;
- durée **96 → 170 ticks** dans le gestionnaire comme dans la commande de test (`204` de marge) ;
- la **seconde entaille différée du balayage** (`PENDING_ECHOES`) est débranchée : elle aurait
  superposé un swing fantôme à chaque coup. La file est conservée, documentée comme inutilisée ;
- le **balayage d'arme** devient trois anneaux étagés qui montent, et la **lame en main** voit
  des charges remonter vers la pointe au lieu d'une rangée fantôme décalée ;
- `SHAPE_DOUBLE_ARC` → `SHAPE_COLUMN` dans la table des mouvements.

### 24.4 Contrôles

`java-parser` OK sur les **8 fichiers** touchés · `semcheck.py` OK · `lamcheck2` : les seuls
écarts avec HEAD sont les variables locales des nouveaux blocs, aucune capture de variable mutée
· `node --check` OK · **0 NaN**, **0 alpha sous 208** sur 99 instants × 3 variantes · Playwright
3 × 6 instants, **0 erreur JS**, comptes DOM identiques à node · pics **456 / 470 / 651** cubes
pour un plafond de 7 000 · rapport longueur/épaisseur moyen des arcs **9,60** et **6,84** ·
**0 cube tourné** dans la variante 8 bits · miroirs `FIXES_MCREATOR` **237/237**.

---

## 25. Surcadence Cyber-X — v5 : le pixel passe dans la géométrie

### 25.1 Pourquoi la v4 était à refaire

La v4 habillait la Surcadence d'un vrai vocabulaire 8 bits — sprites d'éclair à trois poses,
cadre d'interface qui se traçait au balayage, jauge de surcharge remplie case par case, anneau
de Bresenham au sol. Chaque élément était juste pris isolément, et l'ensemble était faux :
**c'était un HUD posé au milieu d'une scène 3D**. Un sprite est plat ; dans un monde où la
caméra tourne, il se trahit au premier pas de côté. Le verdict est sans appel — « des pixels
arts plats 2D sur un plan 3D, ça n'a pas de sens ».

La leçon tient en une phrase : **le style « bits » doit porter sur la géométrie, pas sur des
vignettes**. Grille, cubes tous identiques, palette fermée, horloge à paliers — mais appliqués
à des volumes réels qu'on peut contourner.

### 25.2 Les deux outils

Tout repose sur deux fonctions, et elles remplacent `sprite`, `pixelCircle` et `plot8`.

`voxelLine` rastérise un segment en **Bresenham tridimensionnel** : le tracé avance d'une
cellule à la fois en arithmétique entière, ce qui donne les marches d'escalier du pixel art,
cette fois en volume. Un garde-fou abandonne au-delà de 192 pas. Le drapeau `fat` double la
cellule sur X et sur Z : le conducteur gagne une section 2×2 **sans changer la taille du cube
ni quitter la grille** — c'est ainsi qu'un tronc se distingue d'une brindille.

`pixelBolt` est un **arbre fractal récursif**. Profondeur 2 pour le tronc, 1 pour les branches,
0 pour les brindilles. Chaque nœud dévie d'un bruit haché pondéré par `sin(u·π)` — l'écart est
maximal au milieu, nul aux deux extrémités, de sorte que les points d'attache restent francs.
La teinte se choisit **par index dans la palette** selon la profondeur, jamais par
interpolation : blanc au cœur, bleu vers les pointes.

### 25.3 Les réglages, et ce qu'ils ont coûté

Quatre passes ont été nécessaires, chacune corrigeant un défaut visible :

- **buisson.** Huit arbres, 34 % de branchement par nœud : le volume se remplissait et on ne
  distinguait plus une seule décharge. Un éclair est **aéré** → trois troncs ;
- **colonnes droites.** 10 nœuds sur 2,7 blocs font 1,5 cellule par tronçon : le zigzag passait
  **sous la résolution de la grille**. Moins de nœuds, plus longs (`3 + depth*2`), amplitude
  0,55, hauteur portée à 3,20 → l'escalier redevient lisible ;
- **forêt de poteaux.** Les quatre électrodes montaient sur toute la hauteur et doublaient la
  cage. Elles sont ramenées au tiers et **s'effacent** quand les arbres s'allument (`socle =
  1 − 0,80·cage`). Un premier essai en pointillé a été abandonné : il produisait des cubes
  flottants orphelins au lieu d'un conducteur ;
- **tache rose.** Le magenta, à 7 % sur un tronçon à section 2×2, ne faisait pas un pixel
  d'accent mais une tache de six cubes. Il quitte les éclairs et reste au **noyau**, où il
  signe la surcharge.

La ramification se lit désormais par profondeur : **62 %** sur le tronc — c'est elle qui fait
reconnaître un éclair — **18 %** sur les branches, sinon le buisson revient.

### 25.4 Contrôles

`java-parser` OK · `node --check` OK · **0 NaN**, **0 alpha sous 208**, **0 dépassement du
plafond de 7 000** sur 99 instants × 3 variantes · pics **456 / 626 / 651** cubes · Playwright
3 × 7 instants, **0 erreur JS**, comptes DOM identiques à node · quatre graines distinctes
rendent 366 à 550 cubes, la structure ne dégénère pas.

Deux invariants valent pour toute la variante : **0 cube tourné** et **0 cube hors grille sur
34 030** — une seule demi-taille, `0,0938`. C'est la définition opératoire du style : si un
seul cube pivotait ou se décalait d'un demi-pas, la grille cesserait de se voir.

Les deux `ERR  constante inconnue « FRAMES »` de `semcheck.py` sont un faux positif connu de
l'outil sur les `final` locaux en majuscules : la v4, qui compile, les déclenche aux mêmes
lignes — et deux de plus que la v5.

---

## 26. Surcadence Cyber-X — v6 : la planche de référence, appliquée

### 26.1 Ce qui n'allait pas, et ce n'était pas un réglage

La v5 avait réglé la question du volume — vrais arbres fractals, Bresenham 3D, zéro sprite —
mais elle restait illisible, et pour une raison qui n'a rien à voir avec la technique : **elle
montrait quatre choses à la fois**. Une cage de trois troncs, quatre conducteurs verticaux, des
liaisons de pointe à pointe, un noyau pulsant. Quatre objets de même poids visuel dans le même
mètre cube : le résultat n'est pas un effet, c'est une boîte.

La correction ne pouvait donc pas être un coefficient. Il a fallu redire **ce que l'animation
raconte** : la foudre frappe le porteur et ne repart pas. Elle reste plantée sur lui, elle
grésille, elle éclate au sol. **Un seul objet domine** — le trait vertical — et tout le reste
n'existe que pour le servir. Il n'y a plus de cage.

### 26.2 L'éclairage : trois règles lues sur la planche

**Le halo ne se fait pas par transparence.** En pixel art, une aura, c'est une couronne de
pixels de teinte intermédiaire autour du trait. Cela tombe d'autant mieux ici que le moteur
écarte tout cube sous 200 d'alpha : une aura translucide aurait été parfaitement visible dans
l'aperçu et **parfaitement invisible en jeu** — le piège dans lequel ce chantier était déjà
tombé une fois. La gaine est donc opaque et descend dans la palette par couronne : blanc au
cœur, cyan à une cellule, bleu à deux.

**Le cycle est de cinq images** : deux fines, une de flash, deux fines. C'est le flash qui fait
la foudre ; une intensité constante se lit comme un néon. Les images fines sont **nues**, un
seul cube de large — leur donner une couronne permanente retransformait le trait en tube. Le
battement se mesure, en cubes par image : `170 · 97 · 136 · 398 · 123`.

**L'aura doit être ajourée**, et c'est la seule chose que la planche ne pouvait pas m'apprendre.
À plat, on voit le cœur *à travers* son halo. En volume, une gaine pleine **masque le cœur** :
au premier essai il ne restait qu'un bloc bleu compact. Les cubes de couronne sont donc semés
par un bruit haché (78 % de la couronne proche, 45 % et 38 % de la lointaine), ce qui laisse
voir le trait blanc et rend au passage le grain du flou.

### 26.3 La forme

Un éclair descend et **se ramifie vers le bas**. La probabilité de fourche croît avec la
progression le long du trait (`0,70 · u` sur le tronc) : le haut reste franc, le bas s'ouvre en
patte d'oie. Une branche **continue de descendre en s'écartant** — avec une direction
quelconque on obtient un oursin, pas une décharge.

Deux corrections de forme ont suivi la première image : l'errance est passée de 0,52 à **1,15**,
sans quoi un trait de 4,40 blocs de haut reste un poteau ; et le nombre de nœuds de `3+2·depth`
à `4+3·depth`, pour qu'il y ait assez de coudes pour que le zigzag existe.

### 26.4 Contrôles

`java-parser` OK · `node --check` OK · **0 NaN**, **0 alpha sous 208**, **0 dépassement du
plafond de 7 000** sur 99 instants × 3 variantes · pics **456 / 608 / 651** · Playwright
3 × 8 instants, **0 erreur JS** · cinq graines donnent des pics de 547 à 608, la structure ne
dégénère pas · **0 cube tourné**, **0 cube hors grille sur 22 719**, une seule demi-taille.

Le rapport longueur/épaisseur se mesure sur la **section** du trait, tranche horizontale par
tranche horizontale — ni l'amplitude du zigzag, ni l'empreinte au sol ne sont une épaisseur.
Il vaut **4,20 sur l'image de flash** et **21,0 sur les images fines**, pour un plancher exigé
de 2,5.

`CAGE_HALF` et `ECHO_RADIUS` ont disparu : la première n'avait plus d'usage, la seconde était
déjà morte côté miroir et n'a jamais existé côté Java.

---

## 27. Signature n°2 — le pixel art est abandonné, place au marteau de Thor

### 27.1 Pourquoi tout jeter

Les versions 4 à 6 ont cherché à faire tenir un style « bits » dans une scène en trois
dimensions. La v4 posait des sprites plats, la v5 a mis la grille dans la géométrie, la v6 a
appliqué l'éclairage d'une planche de référence. Chaque étape corrigeait la précédente, et le
résultat restait rejeté. La conclusion n'est pas qu'il manquait un réglage : **le thème
lui-même ne tenait pas**. Un rendu pixel suppose une grille et un point de vue fixes ; dès que
la caméra tourne librement autour de l'effet, la grille cesse d'être une intention et devient
un défaut.

La signature change donc de sujet. `OVERCLOCK_CYBER_X` devient **`MJOLNIR_HAMMERFALL`** —
« Marteau de Thor ». L'identifiant numérique **8 reste**, c'est lui la clé de persistance et de
dispatch, et les enchantements déclencheurs (`overclocked_crit`, `overcharge`) sont intacts.

### 27.2 Le marteau

C'est un **objet**, pas un effet, et c'est ce qui change tout dans la façon de le construire :
il a un corps, deux plaques de frappe, des rivets, des rainures gravées, des runes serties, une
lanière de cuir, un pommeau et un anneau de suspension. Une soixantaine de boîtes orientées.

La règle qui fait le volume n'est pas la couleur mais la **valeur** : une face éclairée, une
face moyenne, une face d'ombre, et une arête plus claire que tout le reste. Toutes les pièces
sont du même or ; seule leur luminosité change.

Deux corrections sont venues de la première image rendue :

- les arêtes étaient d'abord soulignées par des boîtes **roulées à 45°**. Une boîte tournée sur
  une arête déborde des deux faces : le marteau avait quatre ailettes blanches. Une baguette
  droite, rentrée dans le volume, fait le même travail sans rien faire saillir ;
- les plaques de frappe étaient entièrement dans la teinte de reflet, ce qui les faisait lire
  comme du papier collé sur l'or. Le reflet ne tient plus que l'arête.

La chute est **accélérée en cube**. Une descente linéaire se lit comme un ascenseur ; il faut
que le dernier tiers soit brutal pour qu'on lise une frappe.

### 27.3 Le gameplay

**L'impact**, au tick 40 — exactement quand le marteau touche le sol à l'écran : un carré de
**3×3 blocs** centré sur la cible prend **deux fois les dégâts de l'arme**. Deux fois, au sens
strict : la valeur utilisée est celle de l'événement de dégâts, donc **enchantements et effets
d'attaque compris**. Un Tranchant V ou une potion de Force se répercutent tels quels.

**La dalle** reste sous tension **dix secondes** (200 ticks). Tout ce qui se trouve dans le
carré — **mobs et joueurs**, l'attaquant excepté — prend une décharge légère une fois par
seconde. La zone est figée au sol : c'est le terrain qui est électrifié, pas la cible, et elle
ne suit donc personne.

Deux garde-fous repris de la mécanique des tornades, pour la même raison : le hook de tick est
appelé une fois **par entité vivante** et par tick. Sans verrou de passage unique, une dalle
frapperait autant de fois qu'il y a de mobs chargés dans le monde. Et un registre de
rafraîchissement par victime empêche la décharge de se déclencher à chaque tick.

Aucun son n'accompagne la décharge de la dalle : une par seconde et par victime pendant dix
secondes deviendrait insupportable. Les étincelles suffisent.

### 27.4 Ce que le remplacement a entraîné ailleurs

`OVERCLOCK_CYBER_X` → `MJOLNIR_HAMMERFALL`, **14 occurrences dans 7 fichiers** ; durée **120 →
250 ticks** dans le gestionnaire comme dans la commande de test (`290` de marge) ;
`ThunderSignatureVfx.OVERCLOCK` → `HAMMER`. Les libellés français qui parlaient de « surcadence »
ont été recalés dans six fichiers — un identifiant renommé sans ses descriptions laisse une
documentation qui ment.

En revanche `overclocked_crit`, `overcharge` et `OVERCLOCK_CYAN` **n'ont pas été touchés** : ce
sont respectivement des clés d'enchantement et une couleur de particules, sans rapport avec la
signature.

Tout le pixel art disparaît du fichier : `PIX`, `PXL`, `pix`, `snap`, `cell`, `voxelLine`,
`pixelBolt`, et `CAGE_HALF` avec eux.

### 27.5 Contrôles

`java-parser` OK sur les **10 fichiers** touchés · `node --check` OK · `semcheck.py` ne
signale que ses faux positifs connus — dont `zone lu avant sa déclaration`, qu'il produit déjà
sur `tickWindZones`, du code existant qui compile · `lamcheck2` n'ajoute aucune capture de
variable mutée · **0 NaN**, **0 alpha sous 208** sur 99 instants × 3 variantes · Playwright
**0 erreur JS**.


---

## 28. Le marteau se disloquait : un signe de lacet

### 28.1 Le symptôme

Vu de trois quarts, le marteau paraissait juste. En tournant la caméra, les plaques de frappe
se détachaient du corps, les panneaux de face partaient en biais, et l'objet se lisait comme
un tas de tôles. Ce n'était pas une question de goût : c'était un bug.

### 28.2 La cause

`piece()` fait deux choses : il place la pièce, et il oriente sa boîte. Les deux tournaient en
sens contraire.

La position était tournée de **+lacet** :

    f = lf·cos(lacet) − ll·sin(lacet)
    lat = lf·sin(lacet) + ll·cos(lacet)

tandis que `emitVoxelBoxRotated` envoie l'axe X local sur `(cos lacet, 0, −sin lacet)`, ce qui
est une rotation de **−lacet**. Passer le même lacet aux deux faisait donc tourner chaque
pièce à l'envers de sa propre position, d'un angle double. À un huitième de tour, une plaque
de frappe se retrouvait perpendiculaire à l'endroit où elle aurait dû être.

Le contrôle qui l'établit compare les huit sommets de chaque pièce au même modèle tourné d'un
bloc : avec le signe d'origine, l'écart monte à **1,27 bloc** ; avec le signe opposé, il est
**exactement nul**.

À lacet nul, les deux conventions coïncident — et c'est tout le piège : l'effet était correct
sur l'image de référence, et faux partout ailleurs.

Le correctif ajoute aussi l'orientation du repère de l'effet, `atan2(dz, dx)`. Sans elle, les
pièces se placent le long de l'arme mais restent orientées sur les axes du monde : invisible
dans l'aperçu, qui fixe `dx = 1, dz = 0`, mais faux en jeu dès que le joueur ne regarde pas
plein est.

**Leçon générale.** Quand un helper place *et* oriente, les deux rotations doivent être
vérifiées ensemble par un test d'invariance : le modèle tourné doit être la rotation rigide du
modèle au repos. Une inspection visuelle sous un seul angle ne peut pas l'attraper.

### 28.3 Le modèle, refait

L'ancienne tête était une boîte sur laquelle on avait collé, face par face, des panneaux de la
même taille que la face. Même sans le bug de lacet, cette construction ne tient pas : un
panneau de la taille de son support n'a aucune raison de se lire comme un relief.

La tête est maintenant un empilement de **cinq tranches emboîtées**, de plus en plus étroites
vers le haut et vers le bas : la silhouette est chanfreinée, donc solide sous tous les angles,
et il n'y a plus une seule feuille rapportée. Tout détail est **rentré** dans le volume qu'il
habille.

Le manche est un **prisme octogonal** : une boîte, plus une seconde inscrite tournée d'un
huitième de tour. Cette rotation-là est sans danger parce qu'elle est **centrée sur l'axe** —
c'est précisément ce qui la distingue du chanfrein à 45° posé sur une arête, qui saillait des
deux faces.

**89 pièces** contre 45, avec cadres de frappe, rivets, cannelures, filets, runes serties,
poinçons, lanière à cinq tours, gemmes de pommeau et anneau de suspension.

---

## 29. Trente blocs de chute, et un sol qui n'a plus de bordure

### 29.1 La chute

Le marteau est maintenant appelé à **30 blocs**, contre 5,60. La courbe de descente ne change
pas — elle reste cubique — et c'est elle qui rend la hauteur jouable : l'essentiel des trente
blocs est avalé dans les trois derniers ticks. Une descente linéaire sur trente blocs aurait
duré une éternité au début et serait arrivée sans force.

Cette hauteur casse tout cadrage fixe : un plan qui montre le marteau à trente blocs réduit
l'aura au sol à une tache, et un plan qui montre l'aura perd le marteau hors champ. L'aperçu
**suit** donc la chute : la vue rejoue la même courbe que le mod et se resserre au fur et à
mesure que le marteau descend. Un premier essai basculait d'un plan à l'autre entre deux
instants fixes ; le raccord tombait au mauvais moment et l'impact se retrouvait en bas du
cadre. Suivre vaut mieux que couper.

### 29.2 L'aura : rayon 6, puissance décroissante

L'aura au sol passe d'un carré de 3×3 à un **disque de 6 blocs de rayon**, et la décharge
**décroît du centre vers le bord** : plein tarif au point de chute, **15 %** à la limite. Le
plancher n'est pas un détail — sans lui, le bord serait une ligne morte, et une zone dont le
bord ne fait rien est une zone plus petite qu'annoncée.

C'est un **disque** et non un carré. Le moteur ne sait chercher que dans une boîte, donc la
recherche reste carrée, mais la distance est vérifiée ensuite et tout ce qui dépasse 6 blocs
est écarté. Sans cette vérification, les coins auraient porté à 8,49 blocs.

### 29.3 Pourquoi le contour a disparu

La version précédente dessinait un liseré carré, quatre bornes d'angle et une dalle en damier.
C'était lisible, et c'était faux. Un cadre dit « voici les limites du piège » ; ce qu'il
fallait dire, c'est « le sol mord, de moins en moins fort à mesure qu'on s'éloigne ». Un
contour transforme une menace continue en case de jeu de plateau.

Le dessin est donc fait de **foyers** — 58 points tirés au hasard dans le disque, chacun avec
sa période et sa phase propres. Trois conséquences :

- la zone est **asymétrique** à chaque instant, et aucun motif ne se répète ;
- la limite extérieure n'est **jamais une ligne** : la densité tombe jusqu'à zéro, donc le bord
  s'éteint au lieu d'être tracé ;
- la densité **suit la puissance**. Les foyers sont tirés en `R·u^0,72` au lieu de la racine
  carrée d'un tirage uniforme, ce qui les resserre vers le centre — là, précisément, où la
  décharge fait le plus mal. Le dessin dit donc la règle du jeu sans l'écrire.

La dalle en damier a été retirée avec le reste : posée sous l'aura, elle redessinait
exactement le carré qu'on venait de supprimer.

### 29.4 Contrôles

`java-parser` OK · `node --check` OK · **0 NaN**, **0 alpha sous 208** sur 99 instants × 3
variantes · pic de 299 cubes pour le marteau, loin du plafond de 7 000 · Playwright **0 erreur
JS** · centre de masse de l'aura relevé à cinq instants : il se déplace entre (−0,30 ; 0,51)
et (1,10 ; −0,51) et ne revient jamais à l'origine, ce qui vérifie l'asymétrie autrement qu'à
l'œil.

---

## 30. Une vraie chute, et un ciel qui n'est plus vide

### 30.1 Le cube n'était la chute de rien

La descente était calculée en `fall³`. Résultat : le marteau restait quasiment immobile
pendant les deux premiers tiers du trajet, puis franchissait vingt blocs en trois ticks. Ce
n'était pas « une chute qui s'accélère », c'était une stagnation suivie d'une téléportation.

Un corps lâché sans vitesse initiale parcourt une distance proportionnelle au **carré** du
temps — c'est la définition même d'une accélération constante. La courbe est donc passée en
`fall²`, et le minutage a suivi : la chute occupe maintenant **42 ticks** au lieu de 14, et
l'impact passe du tick 40 au **tick 50**.

Le profil obtenu, relevé tick par tick :

| tick | hauteur | vitesse |
|---|---|---|
| 8 | 29,58 | 0,00 |
| 20 | 27,06 | 0,34 |
| 32 | 19,89 | 0,73 |
| 44 | 8,07 | 1,11 |
| 50 | 0,41 | 1,37 |

La vitesse croît **linéairement** : c'est la signature d'une gravité constante, et c'est
vérifiable sans regarder l'écran. Trente blocs en 2,13 s, contre 2,47 s pour une chute réelle
de 30 m — le jeu accélère un peu, ce qui est la bonne direction.

Le flottement d'attente a disparu avec le reste. Il ne reste qu'un frémissement de cinq ticks
à l'apparition, le temps que l'œil trouve l'objet.

### 30.2 L'air haut

Le marteau traversait trente blocs de vide. Une chute n'a pas que deux extrémités, elle a un
**milieu**, et ce milieu doit être occupé. L'arc fulgurant règle le problème depuis le début
avec son ionisation ascendante ; le marteau reprend le principe en trois couches :

- **la nappe** : dix arcs horizontaux tirés entre 9 et 30 blocs, re-tirés par paliers, qui
  font craquer le plafond ;
- **les canaux** : deux traits verticaux qui descendent du ciel jusqu'au marteau, dont un avec
  gaine. Ce sont eux qui disent que le marteau est **tiré** vers le bas et non lâché ;
- **la traînée** : pendant la descente, des arcs restent accrochés au-dessus de lui, d'autant
  plus longs que la chute est avancée. C'est la lecture de la vitesse.

### 30.3 Synchronisation

Le coup doit porter à l'instant exact où le marteau touche le sol à l'écran, sinon le joueur
voit deux événements au lieu d'un. Le report de l'impact de 40 à 50 ticks a donc été répercuté
sur la frappe, sur la pose de l'aura, et sur les paliers sonores — passés de trois à quatre,
resserrés vers la fin pour que l'oreille suive l'accélération que l'œil voit.

### 30.4 Contrôles

Pic de **509 cubes** pour le marteau, loin du plafond de 7 000 · **0 NaN**, **0 alpha sous
208** · `java-parser` OK · `node --check` OK · Playwright **0 erreur JS** · profil de chute
relevé numériquement, vitesse linéaire confirmée.

## 31. Un éclair ne se couche pas — et l'orage se charge au bon endroit

Deux corrections, et elles vont ensemble.

**La nappe horizontale du marteau est supprimée.** Le chapitre précédent avait ajouté dix
arcs couchés entre 9 et 30 blocs d'altitude, pour meubler l'air au-dessus du marteau. C'était
une faute. Un éclair suit le champ électrique, et ce champ est **vertical** : il va du nuage
au sol. Un trait couché en pleine hauteur ne correspond à rien de physique — à l'écran, il ne
se lit pas comme de la foudre mais comme une rayure sur l'image. Les deux canaux verticaux
que le marteau suit en descendant, et la traînée qu'il tire derrière lui, restent : ceux-là
accompagnent une chute, ils ont un sens. `renderHammer` n'émet plus un seul segment couché.

**Et surtout : la charge d'ambiance n'était pas demandée pour le marteau.** Elle l'était pour
l'**arc fulgurant en chaîne** (`CHAIN_VOLT_ARC`, id 16), la signature n° 1. C'est là qu'elle
est maintenant, en deux couches ajoutées avant l'ionisation :

- **le plafond grésille.** Treize filaments courts et *pendants* apparaissent entre 14 et 31
  blocs, repiqués toutes les deux frames (`step = t·30`). Ils descendent de leur point
  d'accroche — jamais l'inverse — sur 1,5 à 5,9 blocs. C'est le ciel qui se charge : la
  décharge arrive, mais elle n'est pas encore là.
- **quatre éclairs tombent.** Ils partent de `SKY_HEIGHT × 1,42` (environ 31 blocs) et
  descendent, l'un après l'autre, décalés de 52 millisecondes. Deux sur cinq touchent le sol ;
  les autres s'éteignent en l'air, comme les décharges intra-nuage d'un vrai orage. Chacun vit
  à peine un dixième de seconde.

L'ensemble s'éteint à 58 % de l'animation, pour laisser la chaîne de relais courir au sol sans
concurrence visuelle. L'altitude maximale atteinte par la signature passe à **30,8 blocs**.

**Le cadrage de l'aperçu suit.** Un plan fixe aurait sacrifié soit l'orage, soit la chaîne :
la vue s'ouvre large et haut (centre 10,5 · portée 31), puis redescend sur le sol (centre 1,4 ·
portée 14) pendant la frappe. Même principe que le suivi du marteau.

**La page d'aperçu repart du gabarit.** Elle avait dérivé vers un balisage inventé pour
l'occasion. Elle reprend maintenant le **gabarit du base builder** — celui de Gaia, Poison,
Feu, Terre, Vent : mêmes blocs (`eyebrow`, `build`, `intro`, `rule`, `variants`, `controls`,
`timeline`, `phase`, `stats`, `legend`, `refs`, `download`, `zipnote`, `topbar`, `hint`,
`variant-title`), mêmes identifiants, même archive embarquée en base64. Seules les teintes
changent. Les deux morceaux partagés sont versionnés : `tools/preview_base.css` et
`tools/preview_render.js`.

**Mesures** — pics 522 / 427 / 651 cubes (plafond moteur : 7 000), moyennes 331 / 158 / 270,
zéro valeur non finie, zéro alpha sous le seuil de 208. Aperçu : 0 erreur JS.

## 32. Les dégâts par contact, et une bande-son qui vient de la banque vanilla

Jusqu'ici, toute la foudre frappait par **minuterie** : à tel tick, tout ce qui se trouve dans
tel rayon encaisse. C'est juste pour une onde de choc, qui est effectivement instantanée et
partout à la fois. C'est **faux** pour un objet qui tombe.

### Le volume de contact

Le marteau met quarante-deux ticks à descendre trente blocs. Pendant ce temps il occupe une
position précise, et un mob perché à mi-hauteur — sur un toit, un échafaudage, en vol —
devrait le prendre en pleine tête *avant* que le sol ne soit touché. Il ne le prenait pas.

`ThunderHit` corrige ça. C'est un cylindre qui connaît sa hauteur à chaque tick, suit
exactement la même loi que le rendu (chute en `t²`), et ne frappe une victime **qu'une seule
fois** : traverser un corps n'est pas y rester. Vérification faite contre la géométrie du
miroir JS, tick par tick, du 8ᵉ au 50ᵉ : l'écart entre le bas du marteau à l'écran et le bas
du volume ne dépasse jamais **0,71 bloc**, et il est toujours *négatif* — le volume est
légèrement en retard sur l'image, jamais en avance. On n'est donc jamais touché par un marteau
qui n'est pas encore arrivé.

Trois usages :

| Signature | Volume | Fenêtre | Dégâts |
|---|---|---|---|
| Arc fulgurant | 4 colonnes, r 1,30 × 8,4 blocs | 3 ticks chacune, aux ticks 5/12/19/26 | 42 % de la base |
| Marteau | cylindre mobile, r 1,45 | ticks 8 → 50, en `t²` | **50 % de la frappe** |
| Appel de l'orage | colonne centrale, r 2,60 × 8 blocs | 4 ticks au tick 140 | 55 % de la base |

Le son de touche part de **la victime**, pas du centre de l'effet : c'est ce qui fait entendre
qu'on a été accroché au passage plutôt que soufflé de loin.

### Les effets de statut

Ils disent ce que les dégâts ne disent pas. L'écrasement du marteau donne Lenteur III trois
secondes, Faiblesse II et Fatigue II quatre secondes, et **projette** vers l'extérieur : un
coup de masse met au sol, il ne fait pas que blesser. L'aura entretient Lenteur I, Lenteur II
dans la moitié centrale — c'est ce qui en fait vraiment du contrôle de terrain : on peut
encaisser, mais on sort moins vite qu'on n'est entré. La mitraille de l'orage pose
**Luminescence** : le nuage désigne ses cibles, et le joueur voit qui n'est pas sorti de la
zone. La décharge ajoute Nausée au centre. Les relais de la chaîne marquent chaque victime.

### La bande-son

Trois règles, et tout vient de la banque vanilla 1.21.1 — aucun fichier ajouté.

**Un seul échantillon ne fait jamais une détonation.** Le tonnerre de Minecraft est long et
mou : il lui manque l'attaque. `thunderBoom()` empile donc quatre couches qui se complètent —
le claquement sec de `item.mace.smash_ground_heavy` (ajouté en 1.21 pour la masse, c'est-à-dire
pour exactement ce geste-là), la masse de `entity.lightning_bolt.thunder`, le souffle de
`entity.generic.explode`, et le sub de `entity.warden.sonic_boom` qui met le poids en bas du
spectre. Le marteau y ajoute `block.anvil.land` à 0,48 de hauteur tonale.

**Un orage ne s'arrête jamais net.** `rollingThunder()` programme quatre répliques de plus en
plus faibles, de plus en plus graves, de plus en plus espacées, et **décalées dans l'espace**
autour du point d'impact pour qu'elles ne sortent pas du même haut-parleur.

**On prévient avant de frapper.** `chargeSwell()` fait monter la tension avant qu'il n'y ait
quoi que ce soit à voir : volume croissant, intervalle décroissant.

Le reste suit la même logique de **position** : le grondement d'invocation du marteau est émis
à vingt-six blocs de haut, pas sur la cible — on lève la tête avant de comprendre. Pendant la
chute, cinq passages d'**enclume** (`block.anvil.fall` + `item.wind_charge.throw`) ont leur
source qui *descend avec l'objet*, le volume doublant et la hauteur montant d'un demi-ton : un
Doppler sans Doppler. Les six battements de `entity.warden.heartbeat` de l'orage transforment
l'intervalle mort d'une seconde et demie en attente. Les relais de la chaîne utilisent
`block.conduit.attack_target` dont la hauteur monte de 0,09 à chaque bond : on *entend* la
chaîne se propager. Et l'aura grésille une fois toutes les quinze frames à un point tiré au
hasard — un crépitement par tick serait un bourdonnement, et un bourdonnement, on cesse de
l'écouter.

Dix-neuf identifiants sonores sont employés. Tous ont été vérifiés comme existant dans la
version 1.21.1 : soit ils étaient déjà utilisés ailleurs dans le mod, soit leur présence a été
contrôlée sur le registre vanilla avant écriture.

## 33. Dix-neuf erreurs de compilation, et pourquoi le contrôle ne les voyait pas

Le lot précédent ne compilait pas. `runClient` a sorti dix-neuf erreurs, et aucune n'avait été
détectée ici. Ce n'est pas un hasard : l'outil de contrôle dont je disposais était un
**analyseur syntaxique**. Il vérifie que les accolades se ferment et que les parenthèses
s'équilibrent. Il ne sait rien des types ni des signatures. Pour lui, `clamp(x, 0f, 1f)` est un
appel parfaitement formé — c'est le compilateur, et lui seul, qui sait que `clamp` n'accepte
qu'un argument.

### Ce qui n'allait pas

**`clamp` à trois arguments** (`ThunderSignatureVfx`, lignes 511 et 598). La méthode locale est
`private static float clamp(float value)` : elle borne déjà entre 0 et 1, c'est tout son objet.
J'ai écrit `clamp(v, 0.0f, 1.0f)` par réflexe `Mth.clamp`. Les deux arguments en trop sont
retirés, le comportement est identique.

**Étiquette de `switch` en double** (`DlbVfxCommand`, ligne 728). En élargissant la liste
d'alias de l'élément foudre, `"thunder"` et `"foudre"` se sont retrouvés sur la ligne
`"lightning", "eclair", "éclair"` *et* sur la ligne suivante. Java refuse deux fois la même
étiquette dans un `switch`. La ligne redondante disparaît ; les cinq alias restent.

**Seize `long` passés à un `int`** (`WeaponVfxServerListener`). `TestProcedure.schedule` est
déclaré `schedule(ServerLevel level, int absoluteTick, Runnable task)` — un `int`. Les trois
méthodes de foudre déclaraient `final long now = TestProcedure.currentTick(sl)` et comptaient
en littéraux `50L`, en tableaux `long[]`. Java ne rétrécit pas un `long` en `int` tout seul :
la conversion est potentiellement destructrice, donc elle doit être écrite. Tout est repassé
en `int`, y compris les signatures de `rollingThunder` et `chargeSwell`.

### Le vrai bug, celui que le compilateur ne pouvait pas voir

En relisant ces conversions j'en ai trouvé une **quatrième**, qui compilait parfaitement et qui
cassait l'aura du marteau en jeu.

Il y a deux horloges dans ce mod, et elles n'ont rien à voir :

| horloge | source | comportement |
|---|---|---|
| tick de **serveur** | `getServer().getTickCount()` | repart de zéro à chaque lancement du jeu |
| temps du **monde** | `level.getGameTime()` | suit la sauvegarde, ne redescend jamais |

`TestProcedure.schedule` compte en ticks de serveur. Les zones persistantes — `ShockZone`,
`WindZone`, `ThunderHit` — stockent une date d'expiration comparée, à chaque tick, au temps du
monde. Or l'aura du marteau était construite ainsi :

```java
SHOCK_ZONES.add(new ShockZone(sl, now + 50L + SHOCK_ZONE_TICKS, ...));  // now = tick serveur
```

tandis que `tickShockZones` fait `long now = sl.getGameTime()`. Sur un monde neuf dans une
session fraîche, les deux valeurs sont proches et ça marche. Sur **n'importe quel monde plus
vieux que la session en cours** — c'est-à-dire tous, en pratique — `getGameTime()` vaut des
millions, `getTickCount()` quelques milliers : l'aura naissait avec une date d'expiration déjà
largement dépassée. Elle disparaissait au tick suivant. Pas de champ électrique, pas de dégâts
de sol, et rien dans les journaux.

Le code du vent séparait déjà proprement les deux (`int scheduleNow` pour la planification,
`long gameNow` pour les zones). La foudre fait pareil maintenant, et les noms disent lequel est
lequel. Les deux `addThunderHit` qui passaient `TestProcedure.currentTick(sl)` en date de début
et de fin sont passés à `sl.getGameTime()` pour la même raison.

### L'outil qui manquait

`tools/typecheck.py` rejoue trois règles du compilateur sur l'arbre Java :

1. **arité** — tout appel à une méthode définie dans le même fichier doit avoir un nombre
   d'arguments que l'une de ses déclarations accepte (les paramètres variadiques sont gérés) ;
2. **`long` → `int`** — le deuxième argument de `TestProcedure.schedule` est inspecté : suffixe
   `L`, tableau `long[]`, variable déclarée `long` dans la portée visible ;
3. **`case` en double** — deux étiquettes identiques dans le même `switch`, en tenant compte
   des `switch` imbriqués.

Lancé sur la version d'avant correction, il retrouve les dix-neuf erreurs. Le mod traîne par
ailleurs vingt-quatre signalements inoffensifs, dus à du code MCreator d'origine compacté sur
une seule ligne et à des classes internes ; ils sont figés dans `typecheck-baseline.txt` et
seules les **nouvelles** alertes font échouer le contrôle.

```
python3 src/main/resources/deepluckyblock_patch/tools/typecheck.py
```

### Le script d'init Gradle

`tools/deep-lucky-block-patch.gradle` est la copie conforme du script qui vit dans
`%USERPROFILE%\.mcreator\gradle\init.d\`. Il est hors du dépôt par nature — Gradle le charge
depuis le dossier utilisateur, pas depuis le projet — donc aucun commit ne peut le corriger. Il
est archivé ici pour être versionné avec le reste, et la version à jour sera livrée dans chaque
archive. Au passage, il explique l'échec du lot précédent : il lève une `GradleException` si
l'un des huit fichiers cadre manque de l'archive, et il restaure soixante et un fichiers.

---

## §34 — Foudre v10 : le joueur sort de la zone, et la chaîne regarde enfin où elle va

Deux retours de jeu, les deux portant sur la même faute : **l'animation racontait une
histoire que le monde ne vivait pas**.

### 1. Le marteau tombait sur son propre porteur

> « dans l'état actuel le joueur qui joue l'animation se fait prendre par le marteau et il
> comprend rien » — « vu qu'il est dedans, il ne voit pas le marteau, juste une masse jaune
> lui tomber dessus »

Un marteau de trente blocs de haut qui tombe à un mètre de la caméra ne se regarde pas : il
remplit l'écran. Le travail de mise en scène — la chute en t², les cinq passages d'enclume qui
se rapprochent, les quatre-vingt-neuf pièces du modèle — était invisible pour la seule
personne qui l'avait déclenché.

`clearTheBlastZone` écarte donc le porteur de **six blocs** au moment de l'invocation. Il lui
reste cinquante ticks pour se retourner et voir la chose arriver.

Une poussée horizontale pure ne valait rien : un talus, un mur, une simple marche derrière le
joueur l'arrête au premier bloc et il reste dans la zone. La méthode **sonde le trajet** avant
de pousser — douze pas d'un demi-bloc, et à chaque pas la hauteur libre la plus basse où la
boîte du joueur tient. La plus haute de ces valeurs donne la montée à franchir, et c'est elle
qui fixe la composante verticale. Un mur plein compte pour la montée maximale : mieux vaut
sauter dedans que rester sous le marteau.

Le reste est de la balistique, et il faut la faire, sinon une poussée constante envoie à trois
blocs ou à quinze selon la hauteur du saut :

| grandeur | formule | pourquoi |
|---|---|---|
| vitesse verticale | `v = √(2gh)`, `g = 0,08` bloc/tick² | hauteur demandée = montée sondée + 1,15 bloc de marge |
| durée du vol | `2v / g` | aller-retour complet |
| portée horizontale | `v·(1 − 0,91ⁿ) / 0,09` | un corps en l'air garde 91 % de sa vitesse par tick : la distance est une série géométrique, qu'on inverse pour viser les six blocs |

La distance de chute est remise à zéro toutes les quatre frames pendant tout l'enchaînement :
elle ne dépasse jamais deux blocs, donc jamais le seuil des trois. **Personne ne paie
l'atterrissage d'un vol qu'il n'a pas demandé**, y compris quand le recul fait passer
par-dessus un bord.

Le recul ne s'applique qu'à un **joueur**. Sa raison d'être est une raison de cadrage ; un mob
n'a pas de caméra, et le projeter à six blocs à chacun de ses coups ne ferait que casser son
combat.

### 2. La chaîne d'éclairs ne cherchait pas les mobs

> « là il ne cherche pas réellement les mobs dans la zone et va d'un en 1, il suit bêtement
> une chorégraphie de mouvement et skip plein de mobs »

Le diagnostic était exact, et le défaut était **des deux côtés du réseau**.

**Côté serveur**, les six maillons étaient figés d'un coup au tick 24. Deux conséquences, les
deux mauvaises : un mob mort entre-temps faisait disparaître son maillon au lieu de le passer
au suivant, et la boîte de recherche restait collée à la hauteur de la cible initiale — un mob
trois blocs plus haut n'existait tout simplement pas. La chaîne suivait un itinéraire décidé
d'avance.

`chainVoltHop` se construit maintenant **bond par bond** : à chaque relais on repart de la
position réelle du corps qu'on vient de toucher et on cherche le plus proche vivant pas encore
servi, dans un **disque** de huit blocs (la boîte passée au moteur est carrée parce qu'il ne
sait pas chercher autrement, mais un coin de carré est à 1,41 fois le rayon annoncé et l'arc
n'y porte pas), sur **douze blocs de hauteur centrés sur le point de départ**. C'est ce que
fait un arc électrique.

Les **quatre colonnes** qui tombent autour du point visé souffraient du même mal : angle d'or,
rayon fixe, joli sur le papier et inutile en jeu. Chacune cherche désormais un corps vivant et
tombe dessus — position **et hauteur**, sinon le volume de contact reste calé sur la cible
initiale et un mob perché encaisse zéro. La géométrie ne sert plus que de repli quand il n'y a
personne.

**Côté client**, le rendu dessinait ses relais sur un anneau tiré de la graine, et l'assumait
en commentaire : « même si le client ignore la position réelle des victimes ». Les dégâts
tombaient sur les bons mobs, les arcs partaient ailleurs. Un nouveau `KIND_THUNDER_LINK`
transporte la position de chaque victime au moment où elle encaisse ; `registerThunderLink`
l'accroche à l'animation en cours et `chainRelays` relie ces points-là, seulement eux.

Trois cas, dans cet ordre :

| situation | chaîne dessinée |
|---|---|
| l'animation a reçu des maillons | les **vraies** victimes |
| petite réplique (`sizeScale < 0,55`) sans maillon | **rien** — les quatre colonnes sont aussi des arcs, au tiers de la taille ; chacune déroulant la sienne, l'écran se remplit d'éclairs qui ne vont nulle part |
| grande animation sans maillon | l'anneau de secours — c'est le cas de `/dlbvfx play 16`, qui joue l'animation sans gameplay |

Le seuil ne peut pas confondre les deux : la grande animation de combat vaut toujours au moins
1,365 (`bigMeleeVisualScale`), les répliques 0,34.

**La page d'aperçu garde l'anneau**, et c'est normal : elle n'a pas de mobs. Le miroir JS n'est
donc pas touché par ce lot.

## 35. Le recul du porteur ne partait pas — un joueur n'est pas un mob

Le lot v10 posait bien `setDeltaMovement` sur le porteur du marteau, et ce code était
bien dans le jeu compilé. En combat réel, rien : le joueur restait sous la masse.

**La cause.** Pour un mob, le serveur fait autorité sur le déplacement : on change sa
vitesse, il bouge, et le changement part vers les clients avec le reste de son état. Pour
un **joueur**, c'est l'inverse : c'est son propre client qui simule son déplacement et qui
envoie sa position au serveur. Une vitesse posée côté serveur n'est qu'un avis, et elle est
écrasée au paquet de position suivant.

Le drapeau prévu pour cela, `hurtMarked`, ne suffit pas ici. Il demande l'envoi d'un paquet
de vélocité en **fin de tick**, construit à partir de la vitesse telle qu'elle est à ce
moment-là. Or notre poussée part depuis l'intérieur de l'événement de dégâts, donc très tôt
dans le tick, et plusieurs choses passent derrière : la fin de `Player.attack`, qui multiplie
la vitesse horizontale de l'attaquant par 0,6 en course, puis la physique, qui rabote la
composante verticale contre le sol. Le paquet finit par partir, mais avec un vecteur mort.

**Le correctif.** On fabrique le paquet nous-même, avec le vecteur voulu, et on le remet
directement à la connexion du joueur, dans la foulée du `setDeltaMovement` :

```java
final Vec3 launch = new Vec3(dx * vh, vy, dz * vh);
rider.setDeltaMovement(launch);
rider.hasImpulse = true;
rider.fallDistance = 0.0F;
if (rider instanceof ServerPlayer pushed) {
    pushed.connection.send(new ClientboundSetEntityMotionPacket(pushed));
}
```

`hurtMarked` n'est volontairement **pas** levé : il provoquerait un second paquet en fin de
tick, construit sur la vitesse entre-temps rabotée, qui annulerait le premier. C'est la même
manœuvre que celle employée par les mods qui déplacent un joueur hors du flux d'attaque
vanilla, et c'est ce que fait la masse de 1.21 pour son propre rebond.

**Le filet.** Huit ticks après la poussée, une tâche différée vérifie que le joueur a bien
décollé — déplacement mesuré depuis **sa** position de départ, et non depuis le point de
chute, dont la distance dépend de l'allonge de l'arme et ne dit rien de fiable. S'il a
parcouru moins de deux blocs, le vecteur a été mangé en route et on le renvoie. Une seule
fois : rattraper un échec, pas catapulter deux fois, et ne pas reprendre la main sur un
joueur qui s'est déjà écarté tout seul.

**La leçon générale.** Chaque fois que ce dépôt déplace un joueur en dehors d'un dégât
vanilla — recul, aspiration, projection d'une zone — il faut le paquet explicite. Déplacer un
mob et déplacer un joueur ne sont pas la même opération, même si l'appel Java est identique.

## 36. Trois retours de combat : le cadrage, le verrou, la dose

Le recul du marteau partait enfin (§35). Trois défauts sont apparus derrière.

### « L'animation du nuage est visée sur le joueur, pas sur le mob »

Elle ne l'est pas. `applyStormcallGameplay` ancre tout sur `target.getX/Y/Z()` au moment du
coup, les deux balayages de victimes excluent explicitement les joueurs, et le visuel est
émis à la position de la cible. Le diagnostic était faux mais l'observation était juste.

La cause est géométrique : `CLOUD_R` vaut 3,40 et `CLOUD_Y` vaut 8,0. Le nuage fait donc
presque sept blocs de large et flotte à huit de haut. En mêlée on se tient à deux ou trois
blocs de ce qu'on frappe, c'est-à-dire **sous la jupe du nuage**. La mitraille tombe entre
0,55 et 2,60 blocs du centre : certains traits atterrissent littéralement aux pieds du
joueur. De l'intérieur, un nuage centré sur le mob et un nuage centré sur soi donnent la
même image.

Même cause que le marteau, donc même remède : `clearTheBlastZone(sl, attacker, ax, az,
STORM_CLEARANCE)` en tête de la procédure. Dix blocs — moins que les onze du marteau, parce
que le nuage ne tombe pas sur le porteur ; il suffit d'en sortir pour le voir en entier.

### « Pour l'éclair qui tombe de très haut, tu as oublié de lock le mob »

Exact. `applyChainVoltGameplay` fige `ox/oy/oz` à l'instant du coup — délibérément, un
éclair ne poursuit pas sa proie. Mais le mob, lui, continue de marcher pendant les vingt
ticks de descente : un pas de côté et le trait tombe à côté de lui.

```java
final long chainHoldEnd = sl.getGameTime() + 40L;
applyMovementHold(target, chainHoldEnd, false);
TestProcedure.schedule(sl, now + 40, () -> releaseMovementHold(target, chainHoldEnd));
```

Quarante ticks couvrent l'ionisation, les quatre colonnes, la frappe principale au vingtième
et le premier bond de la chaîne. **Sans** étourdissement complet : le mob est immobilisé, pas
désactivé. Il se défend encore, il ne peut simplement plus sortir de la mire.

Noter les deux horloges, une fois de plus : l'emprise expire en temps de **monde**
(`getGameTime`), la tâche différée compte en ticks de **serveur** (`currentTick`).

### « Ajoute 5 blocs de recul, et projette deux fois plus haut »

`HAMMER_CLEARANCE` passe de 6,0 à **11,0**. La vitesse verticale est **doublée**, bornes
comprises : `0,42–1,05` devient `0,84–1,50`.

Doubler la vitesse ne monte pas deux fois plus haut mais **quatre** fois — la hauteur varie
comme le carré de la vitesse. Le plafond de 1,50 existe pour que le vol ne dure pas plus
longtemps que l'animation qu'il sert à regarder.

| Terrain derrière | v verticale | vol | v horizontale | portée | apogée |
|---|---|---|---|---|---|
| sol plat | 0,858 | 21 t | 1,148 | 11,00 bl | 4,60 bl |
| talus de 2 | 1,420 | 35 t | 1,028 | 11,00 bl | 12,60 bl |
| mur plein | 1,500 | 37 t | 1,021 | 11,00 bl | 14,06 bl |

La portée reste exactement à onze blocs dans les trois cas : c'est la série géométrique qui
s'en charge, `v·(1−0,91ⁿ)/0,09`, recalculée pour chaque durée de vol.

**Une conséquence à ne pas manquer.** La garantie anti-chute bouclait sur `flight + 28`, soit
la **montée** plus une marge. Avec un apogée quatre fois plus haut, elle s'arrêtait en plein
ciel et rendait au joueur toute la descente. Elle couvre maintenant `2 * flight + 30`,
c'est-à-dire l'aller-retour complet.

## 37. L'ombre — le seul élément qu'on ne peut pas éclairer

Dixième famille refondue, et la première à poser un problème que les neuf précédentes
n'avaient pas.

### Le problème

Le feu, la foudre, le métal, la lumière se voient parce qu'ils **émettent**. Une flamme, un
arc, un éclat se détachent de n'importe quel fond. L'ombre, non : du noir sur un monde déjà
sombre ne produit **aucune image**. L'ancien rendu tombait exactement là-dedans — ses bras
noirs étaient littéralement invisibles la nuit et dans les grottes, c'est-à-dire là où on
joue.

Trois règles en sont sorties, et tout `ShadowSignatureVfx` les applique :

1. **Jamais de noir sans liseré.** Toute masse sombre est bordée de violet ou d'orchidée.
   C'est le liseré qu'on voit ; le noir n'est que le trou qu'il entoure.
2. **La silhouette avant la matière.** Une forme d'ombre se lit à son contour. Les modèles
   sont construits en couches de *valeur croissante vers l'extérieur* — l'inverse d'un objet
   éclairé.
3. **Le noir n'est pas `0x000000`.** Le noir pur se confond avec le ciel de nuit et le fond
   des grottes : la forme disparaît. `VOID_INK = 0x0B0814` lit comme un trou tout en restant
   détachable.

### Deux constantes qui ne sont pas celles de la foudre

`GROUND_Y` vaut **−0,86** et non −0,42. L'origine d'un impact est à mi-hauteur du mob, donc
le sol réel est entre 0,6 et 1,0 bloc plus bas. La foudre s'en accommode — ses effets au sol
sont des lueurs. Une ombre, non : une ombre qui flotte à un demi-bloc du sol se lit
immédiatement comme un bug. Quitte à ce qu'elle s'enfonce un peu sous un mob court : une
ombre enfouie est *invisible là où elle s'enfonce* et juste partout ailleurs, alors qu'une
ombre flottante est fausse partout.

Le plancher d'opacité, lui, ne change pas : `SOLID_FLOOR = 208`. On efface **par la
géométrie**, jamais par fondu d'alpha — sinon le tamis décime la forme en pointillés.

### Le roulis, dérivé et non deviné

Trois modèles de ce fichier tournent sur eux-mêmes : la main qui se redresse, les cornes du
crâne, la mâchoire. Les trois signes de roulis ont été **dérivés de `rotWrite`**, pas
essayés au hasard.

Un roulis de θ envoie l'axe Y local de la boîte sur `(−sin θ, cos θ)` dans le plan
(avant, hauteur). De là :

| Pièce | Direction voulue | Roulis |
|---|---|---|
| phalange de la main | axe `h` → `(sin ang, cos ang)`, plus le couchage | `−(lie + ang)` |
| segment de corne | `(−sin ang, cos ang)` | `+ang` |
| pièce de mâchoire | la position tourne de `+j` | `+j` |

C'est exactement le piège du marteau de Thor : au signe opposé, les pièces se placent bien
mais s'orientent à l'envers. La main se levait, et chaque doigt restait couché sur lui-même.

### Le dôme en treillis

Une demi-sphère pleine de sept blocs de rayon demande des milliers de cubes et, surtout,
**enferme la caméra dans une boîte noire** : le joueur ne voit plus le combat. Neuf méridiens
et cinq parallèles donnent la même lecture — on est dedans, c'est fermé — en laissant passer
le regard. Même leçon que l'anneau de la foudre : une forme se lit à ses lignes de
construction, pas à son remplissage.

### Les durées

`ECLIPSE_VEIL` était coincé à **18 ticks** dans un `case` fourre-tout, et les deux autres
n'étaient même pas listés — ils tombaient sur le `default -> 14` de fin de table.

| Signature | Avant | Après |
|---|---|---|
| `SPECTRAL_REACH` (#3) | 14 t | **118 t** |
| `WITHER_SHADOW_RIFT` (#24) | 14 t | **160 t** |
| `ECLIPSE_VEIL` (#33) | 18 t | **200 t** |

### La marque d'ombre

Liant des trois signatures, comme la charge statique pour la foudre — mais **à l'envers**.
La charge statique majore les *dégâts* ; la marque majore la **durée des afflictions**
(+35 % par pile, quatre piles maximum). L'ombre du mod est l'élément des malédictions
(`DeepElement.isCursePath` renvoie `SHADOW` pour toutes), donc sa ressource partagée devait
porter sur les états, pas sur les points de vie.

À saturation la marque **s'effondre** : dégâts d'un coup, Cécité et Faiblesse. Contrairement
au claquage de la charge statique, l'effondrement **ne pose aucune marque** — la question de
la récursion en anneau ne se pose donc jamais.

### AoE *et* cible seule, pour chacune

| Signature | Plusieurs ennemis | Ennemi seul | Buff du porteur |
|---|---|---|---|
| Allonge Spectrale | couloir de 9×2,7 blocs, 4 vagues de lames | cible clouée 72 t puis **ramenée**, 3 serrages | Absorption, selon le nombre drainé |
| Faille du Flétrisseur | cône de souffle, puis **aspiration** de 8 blocs en 4 secousses | Flétrissure **II** double durée, fermeture plein tarif | soin + Régénération, 1 PV par victime |
| Voile d'Éclipse | **nuit forcée**, dôme de 7 blocs, 5 battements, onde de choc, griffures | les **3 griffures convergent** = triple dégâts | Vision nocturne + Force tant qu'il reste dedans |

## §38 — Ombre v2 : la main, le démon, et le ciel confisqué

Trois reproches, trois réponses. La v1 était lisible ; elle n'était pas impressionnante.

### La main tournait le dos à sa proie

Un demi-tour, littéralement : `base + Mth.PI` dans la rotation des phalanges. La main se
refermait sur la cible en lui présentant le dos de la main — correct géométriquement,
absurde à regarder. Et elle est passée de `1.45f` à **`2.45f`**, soit un sommet mesuré à
**2,42 blocs** : une main qui sort du sol doit faire peur à l'échelle d'un joueur, pas à
celle d'un gant.

Le facteur avait d'ailleurs un bug plus grave que sa valeur : `handPiece` **recevait**
`scale` et ne l'utilisait jamais. Dix-neuf appels le passaient consciencieusement dans le
vide. C'est le genre de défaut qu'on ne voit pas en relisant l'appelant, seulement en
mesurant la géométrie produite.

### Le crâne de Wither est devenu un crâne de démon

De 96 à **~210 pièces** : six tranches crâniennes, un occiput, une crête de cinq épines,
des arcades sourcilières **obliques** (c'est l'obliquité qui fait la menace — une arcade
horizontale fait un crâne surpris), un museau abaissé, un maxillaire à canines, une
mâchoire qui s'ouvre sur quatre couches de gorge, et deux cornes de six segments annelés
qui s'enroulent vers l'arrière.

**Deux tours entiers ont été perdus sur les orbites.** Le réflexe — empiler des couches
concentriques le long de la profondeur locale, puis entourer le tout d'une boîte « rebord »
— produit une orbite *noire*. Les couches s'occultent entre elles, et le rebord plein
masque exactement ce qu'il est censé encadrer. La règle qui marche : **empiler sans
chevauchement** le long de la profondeur, et faire les encadrements en **anneau de quatre
barres**, jamais en boîte pleine. L'orbite v2 va donc du fond `VOID_INK` (0,215) à la
pupille fendue `SPARK` (0,320) sans qu'aucune couche ne morde sur sa voisine.

### L'éclipse : 23 blocs, et la nuit pour de vrai

Le disque était à 10,5 blocs. À cette hauteur il se confondait avec le dôme de 7 — deux
objets de taille comparable, à portée de main l'un de l'autre, qui se lisaient comme un
seul tas. Il est maintenant à **22,0** au-dessus du point d'impact (≈ **23 blocs** depuis
le sol, qui est à −0,86), et son rayon passe de 2,55 à **4,60** : un objet lointain doit
grandir pour garder sa présence.

Mais éloigner le disque crée le problème inverse. **Deux objets séparés par vingt blocs de
vide ne forment pas un événement, ils forment deux décors.** D'où ce qui les relie :

- **9 rayons crépusculaires** brisés (un segment sur cinq omis au hasard) qui partent du
  limbe et descendent jusqu'à l'ourlet du dôme ;
- une **colonne en spirale** de 26 pas qui monte du sceau au sol jusqu'au disque ;
- côté serveur, la même colonne en particules, six paliers par battement, pour que le
  joueur ait une raison de **lever les yeux**.

Le reste est de la densité : 54 étoiles qui s'allument par scintillement haché, une
couronne portée de 36 à **48 filaments** plus **3 protubérances** en arc, 30 braises qui
montent à contre-courant des 62 cendres qui tombent, 8 runes qui tournent à contresens sur
le cercle de garde, 5 arcs rampants sur le dôme. Pic mesuré : **1 554 cubes** à t = 0,81,
pour un plafond moteur de 7 000.

### Le ciel : voler l'heure, et surtout la rendre

Le monde passe à **minuit** (`setDayTime(18000)`) pendant les 200 ticks, puis retrouve
l'heure qu'il avait, **avancée du temps écoulé**.

Le piège est le chevauchement. Deux éclipses coup sur coup, traitées naïvement, cassent le
monde pour de bon : la seconde enregistre *minuit* comme heure d'origine, la première rend
minuit en finissant, et le serveur reste dans la nuit pour toujours — sans que personne
puisse relier la panne à une épée. `stealSky` ne retient donc l'heure **qu'à la première
prise** ; les éclipses suivantes se contentent de repousser l'échéance, et `returnSky`
refuse de rendre quoi que ce soit tant qu'une éclipse plus récente tient la dimension.

La carte est indexée par le **nom** de la dimension, pas par le `ServerLevel` : une carte
statique qui retient un niveau l'empêche d'être libéré au déchargement du monde.

### Le cadrage de la page : chaque élément filme le sien

Le gabarit d'aperçu appelait `boltShot` et `hammerShot` pour les variantes 0 et 1 — les
caméras de la **foudre**. Hérité tel quel, le dôme d'ombre était donc cadré par la chute
d'un éclair. Chaque élément définit maintenant son `<elem>Shot(tt)` ; celui de l'ombre
**s'ouvre pendant la montée** du disque (`span` de 19 à 40 blocs) au lieu de choisir entre
voir le sol et voir le ciel.

### Deux recalages trouvés en relisant les deux horloges côte à côte

L'emprise de l'Allonge durait 40 ticks — la valeur copiée de la chaîne de foudre. Mais la
main se lève au tick 53 et ne tire qu'au **tick 70** : la victime avait dix-sept ticks pour
sortir du cadre pendant que la main, dessinée au point d'impact figé, se refermait sur du
vide. L'emprise court maintenant jusqu'au tick **72**.

Les battements de l'Éclipse commençaient au tick 54. Or l'ourlet du dôme ne touche le sol
qu'au tick **69** (le voile atteint 0,55 de fermeture à `t = 0,345`, sur 200 ticks). Le
joueur encaissait donc un battement alors que la demi-sphère était encore suspendue
au-dessus de lui : **la zone frappait avant d'exister.** Premier battement au tick 68,
dernier au tick 140 — en même temps que l'anneau de diamant, si bien que le battement final
et l'avertissement ne font plus qu'un son.

### Quatre sons qui n'existent pas

`SOUL_ESCAPE`, `PHANTOM_BITE`, `SCULK_CLICKING` et `WITHER_SHOOT` ne sont **pas** dans la
banque 1.21 utilisée ici. Aucun JDK sur cette machine : une erreur de nom ne se serait vue
qu'à la compilation, chez l'utilisateur. Chaque constante a donc été vérifiée par `grep`
contre les usages réels du dépôt avant d'être retenue, et les quatre ont été remplacées par
`CHORUS_FRUIT_TELEPORT`, `PLAYER_ATTACK_SWEEP`, `CHEST_LOCKED` et `WITHER_AMBIENT`.

### Le piège de famille, pour la troisième fois

Sans branche dans `WeaponWorldVfxManager` (ligne ~1835), une famille retombe dans le
`return switch (index % 3)` du **BOIS** et sort en brun. Subi par l'eau en T375, par la
foudre en T376 ; évité ici. C'est le défaut le plus coûteux à diagnostiquer du fichier,
parce que l'effet *s'affiche* — il est juste de la mauvaise famille.

### Tests en jeu

`/anim shadow` (ou `/anim ombre`), puis frapper un mob entouré d'autres. Visuel seul :
`/dlbvfx play 3` · `/dlbvfx play 24` · `/dlbvfx play 33` · `/dlbvfx stop`.

## §39 — Sang : cinq signatures, et la première famille qui n'en compte pas trois

Onzième famille refondue, et la première à **cinq** signatures : `GUILLOTINE_CLEAVE` (#5),
`DRIPPING_BLOOD_EDGE` (#9), `CRIMSON_SIPHON` (#10), `VITAL_TRANSFUSION` (#11),
`CARNAGE_BERSERK` (#12). Durées 150 / 120 / 170 / 140 / 180 ticks contre 14-18 avant.
Un seul moteur, `BloodSignatureVfx` (1 321 lignes), aucun `RenderType` supplémentaire.

### Trois règles, écrites avant la première ligne de rendu

**1. Rien ne flotte.** Toute goutte tombe sous `GRAVITY = 11.0f`. Le sang est le seul
élément de ce mod dont le spectateur connaît par cœur le comportement physique : une
giclure qui plane se voit immédiatement, là où une flamme ou une ombre peuvent se
permettre n'importe quoi.

**2. Toute nappe porte son ménisque.** Un liquide sans liseré clair sur son bord n'est
qu'une tache plate. C'est la contrepartie exacte de la règle de l'ombre — là-bas le liseré
dessinait le trou noir, ici il donne son épaisseur au rouge.

**3. Le rouge ne se lit pas contre le rouge.** D'où les matières étrangères : le fer de la
lame, le bois de l'échafaud, l'os des crochets. Sans elles, la guillotine n'est qu'une
masse sombre et le siphon qu'un tuyau.

### Ce que les cinq rendus ont coûté à l'inspection

Aucun JDK ici : le Java ne peut pas être exécuté. Un miroir JavaScript
(`tools/blood_build.js`) reproduit les cinq rendus cube pour cube, et c'est lui qu'on
regarde. Cinq défauts n'étaient visibles **que** sur image, aucun n'aurait été détecté par
les outils de contrôle :

**Le couperet traversait le linteau.** Le mouton culminait à 4,99 alors que la poutre est à
4,60. Pire, le biseau était construit avec un sommet *constant* : la colonne de droite
finissait à 0,04 bloc de haut, et la lame se réduisait à un éclat triangulaire. Un couperet
de guillotine est un **trapèze** — corps rectangulaire, bord oblique. Corrigé par un
`bodyH` de hauteur fixe sous le biseau, 24 colonnes au lieu de 17 (à 17, la marche du
biseau dépassait la largeur d'une colonne : le tranchant se lisait comme une scie), et un
mouton posé *sur* le corps de lame au lieu d'une hauteur en dur.

**Le cœur était une arrosoir.** Trois teintes séparées de deux crans donnent une masse
uniforme une fois l'ombrage des faces appliqué. Il a fallu cinq crans **et** un second
terme d'éclairage vertical : sans lui, toutes les tranches reçoivent la même valeur et
l'organe s'aplatit en disque. Les coronaires, elles, étaient posées à `1,02 × w` alors que
la frontière des deux ventricules culmine à `0,90 × w` : elles **flottaient à côté** du
cœur au lieu d'y courir.

**Les vaisseaux sortaient en pointillés.** Le pas était dimensionné sur l'écart
*horizontal* du trajet, alors que les deux vaisseaux d'entrée ne couvrent que 1,30 bloc en
`f` mais descendent de 1,50 en hauteur, flèche de l'arc en plus. Dimensionné sur la
longueur réelle, le pas repasse sous le diamètre du tube.

**Le siphon sortait en tige unique.** Rayon d'hélice 0,085 pour des brins épais de 0,052 :
les deux brins se chevauchaient. On perdait l'enroulement, donc le sens d'écoulement — qui
est tout le sujet de la signature. Rayon porté à 0,135 et quatre tours au lieu de 2,4.
La colonne qui se vide, elle, était une **cage** de fil de fer : huit points sur un rayon
de 0,30, soit 0,235 d'espacement pour des cubes de 0,06. Quatorze points, des cubes plus
épais, et surtout le ménisque posé sur le niveau restant : c'est lui qu'on regarde
descendre.

**La frénésie ne montrait que trois arcs.** La mesure numérique disait pourtant « sept
taillades vivantes ». Les deux étaient vraies : l'effacement géométrique était *linéaire*,
donc l'arc était réduit à un fil dès le tiers de sa vie. `sqrt(1 - remnant)` le garde épais
pendant la moitié de sa durée puis l'escamote — et c'est le **chevauchement** des arcs qui
fait la frénésie, pas leur nombre.

### Deux défauts du gabarit d'aperçu, hérités de la foudre

La grille du gabarit partagé est tracée à `y = -0,47`, hauteur héritée de la foudre, alors
que le moteur du sang pose son sol à `GROUND_Y = -0,86`. Toutes les flaques se retrouvaient
**0,39 bloc sous le plan dessiné** : pour une famille dont la première règle est « rien ne
flotte », c'était le défaut à corriger avant les autres.

Et le gabarit ne réglait que la hauteur de vue (`VCY`), jamais le centre horizontal : les
signatures de la foudre sont des colonnes verticales. Le Siphon est un conduit de 7 blocs
tendu à l'**horizontale** — centré sur zéro, il sortait du cadre. D'où `VCX`, symétrique de
`VCY`. Les tableaux du gabarit (`DUR`, `BEATS`, `NAMES`, `SUBS`, `SFX`, `GP`) n'avaient par
ailleurs que **trois** entrées, et les variantes 0 et 1 étaient câblées sur les cadrages de
la foudre (`boltShot` / `hammerShot`).

### L'hémorragie punit la fuite

La ressource partagée des cinq signatures : cinq piles au maximum, un tic toutes les 10
ticks, 42 % du drain reversé au porteur. Son seul mécanisme notable est le multiplicateur
de vitesse — `×(1 + 1,5 × min(1, vitesse / 0,22))`, soit jusqu'à **×2,5** pour une cible
qui court. Un saignement qu'on peut distancer n'est pas un saignement ; ici, courir est la
pire réponse, et rester immobile le moindre mal. C'est la seule mécanique du mod qui
récompense l'immobilité.

### Ce qui distingue chaque signature

**Couperet** est une exécution : dégâts `×(1 + 1,25 × vie manquante)`, clouage 86 ticks —
le temps exact de la montée, de la suspension et de la chute. La cible *voit venir*. C'est
aussi le seul endroit du mod où le silence est écrit : seize ticks sans un son pendant la
suspension.

**Tranchant Sanglant** est la seule zone **en ligne** du mod (`bladeLineVictims`) : ce qui
est sur l'arc est touché, ce qui est à côté ne l'est pas.

**Transfusion Vitale** est la seule aire **bienveillante** : porteur, joueurs et compagnons
apprivoisés. Le bétail n'est ni prélevé ni soigné. 85 % du pot est reversible, dont 55 %
pour le porteur — il soigne les siens, il ne se sacrifie pas.

**Carnage Berserk** fait tomber ses paliers de rage aux 4ᵉ, 7ᵉ et 10ᵉ coups **touchés**, pas
lancés : frapper le vide ne rend pas enragé. Et il assume son coût — Fatigue 60 ticks au
porteur à partir de 6 coups.

### Trois sons qui n'existent pas, pour la quatrième fois

`RAVAGER_BITE`, `CROSSBOW_LOADING_END` et `ARMOR_EQUIP_CHAIN` ne sont pas dans la banque
1.21.1. Le réflexe pris en §38 (vérifier chaque constante avant de l'écrire) a été
systématisé : une table des **1 806** identifiants réels a été extraite, et les 31 sons de
la famille validés un par un — y compris leur nature de `Holder`, qui impose `.value()`.

### Tests en jeu

`/anim sang`, puis frapper un mob entouré d'autres. Visuel seul : `/dlbvfx play 5` ·
`/dlbvfx play 9` · `/dlbvfx play 10` · `/dlbvfx play 11` · `/dlbvfx play 12` ·
`/dlbvfx stop`.

---

## §40 — Le conduit qui ne reliait rien

Le Siphon et la Transfusion racontaient tous les deux la même histoire : un lien
entre deux êtres. Ni l'un ni l'autre ne le tenait réellement.

Trois mensonges, trouvés en relisant le code plutôt que les captures.

**La longueur était une constante.** `REACH = 7.0f`. Le conduit mesurait sept
blocs que le porteur soit à trois mètres ou à onze. À trois, il traversait le
porteur et continuait quatre blocs derrière lui ; à onze, il s'arrêtait en l'air
au milieu de nulle part. La capture d'écran ne pouvait pas le montrer : la scène
d'aperçu place toujours le porteur à exactement sept blocs.

**Aucun bout ne suivait son entité.** L'effet part en `KIND_IMPACT`, qui fige
l'ancre au point d'impact, et `ImpactView.dirX()/dirZ()` dérivent du seul
`yawDeg` figé dans le paquet. La victime pouvait se mettre à courir : le conduit
restait pendu là où elle avait été frappée.

**Le cœur de la Transfusion n'était pas où le serveur croyait.** Le visuel le
dessinait à `heartF = -1.30`, soit à un bloc trente du porteur ; le serveur
prélevait et redistribuait depuis le milieu du segment. Deux cœurs, l'un visible
et faux, l'autre invisible et vrai.

### Ce qu'il a fallu

Le paquet ne transporte pas d'identifiant d'entité, seulement deux points. Le
client retrouve donc les deux corps par proximité — rayon de trois blocs autour
de chaque extrémité, une seule fois, comme `updatePoisonFollow` le fait déjà
pour le poison — puis recale l'ancre sur la victime à `bbHeight × 0,50`, l'autre
bout sur le porteur à `bbHeight × 0,62`, et recalcule à chaque image la
direction, la longueur et le dénivelé. `render()` prend trois paramètres de plus :
`linkLen`, `linkEndY`, `linkBroken`.

Ce qui dépendait de `REACH` en dépend toujours, mais en proportion : le ventre du
conduit vaut `(0,55 − 0,30·drain) × (reach/REACH)`, le pas de l'hélice
`26 × reach/REACH`, le nombre de segments `max(26, 62 × reach/REACH)`. Sans cela
un conduit de deux blocs garderait un ventre de sept et raclerait le sol.

Mesures sur le miroir JavaScript, extrémité basse du conduit :

| portée demandée | 2 | 4 | 7 | 11 |
|---|---|---|---|---|
| siphon, `xmin` | −2,03 | −4,04 | −6,98 | −10,98 |
| transfusion, `xmin` | −1,99 | −3,98 | −6,98 | −10,98 |

### La laisse

Un lien qui s'étire à l'infini n'est pas un lien. Au-delà de **neuf blocs** il
casse : son de chaîne brisée, gerbe de particules, `base × 0,45` de dégâts
d'arrachement et deux piles d'hémorragie pour la victime, puis la pompe s'arrête
— les bols ou les battements restants sont perdus. Le client et le serveur
portent la même constante (`BLOOD_LINK_BREAK = 9.0`) et rompent donc en même
temps, sans paquet supplémentaire.

Fuir reste coûteux : la morsure du Siphon pose Lenteur II pendant 120 ticks, et
l'hémorragie frappe une cible qui court jusqu'à deux fois et demie plus fort.

### Un chiffre qui ne tenait pas

En calculant les cinq totaux pour répondre à une question de revue, le Tranchant
Sanglant — la signature d'entretien, celle qui « frappe peu et saigne longtemps »
— sortait première des cinq : 150 points sur une cible mobile contre 147 au
Carnage, et en six secondes au lieu de neuf. Quatre piles à `0,115 × base` sur
vingt-deux tics, c'était simplement trop. Le tarif unitaire passe à **0,075** et
la durée de 220 à 200 ticks. Les quatre piles restent : c'est son identité.

Classement après correction, en duel, arme à 8 de dégâts, cible immobile → cible
qui court :

| signature | total | DPS |
|---|---|---|
| Carnage | 82 → 147 | 9,2 → 16,4 |
| Siphon | 51 → 100 | 5,9 → 11,8 |
| Tranchant | 48 → 101 | 7,9 → 16,8 |
| Couperet | 50 → 90 | 6,6 → 12,0 |
| Transfusion | 15 → 15 | 2,2 → 2,2 |

La Transfusion reste volontairement dernière : elle ne tue pas, elle soigne un
groupe.

### Post-scriptum : la laisse punissait la mauvaise personne

Première version de `bloodLinkHolds` : un booléen. Elle répondait « non » aussi
bien quand la victime avait fui qu'au moment où le porteur mourait — et dans les
deux cas `bloodLinkSnap` facturait l'arrachement **à la victime**. Tuer le
siphonneur coûtait donc 45 % de la base et deux piles d'hémorragie à celui qui
venait de le tuer.

Trois états maintenant : `LINK_HOLDS`, `LINK_TORN` (la distance — et cela se
paie), `LINK_GONE` (un des deux corps a disparu — la pompe s'arrête, personne ne
paie). Côté client, symétriquement, la disparition de l'un des deux corps marque
`linkBroken` : un conduit accroché à un mort se résorbe au lieu de pendre dans
le vide jusqu'à la fin de l'animation.

---

## §41 — « Je ne comprends pas du tout ce que tu essaies de représenter »

Verdict de l'utilisateur sur le Siphon Carmin, captures à l'appui. Il avait
raison, et le diagnostic tient en quatre points lus sur ses images :

1. **Une saucisse par terre.** Le conduit pendait d'un ventre proportionnel à la
   portée, et le point de ponction était à `mouthY = 0.95` — 95 cm *au-dessus* de
   l'ancre, c'est-à-dire au-dessus de la tête de la plupart des mobs. Le tuyau
   partait donc du vide et retombait au sol.
2. **Un tonneau à côté.** La « colonne qui se vide » était bâtie depuis
   `GROUND_Y`, pas dans le corps : à l'écran, un fût rouge planté près du départ.
   En jeu, elle aurait été *invisible*, cachée dans le modèle du mob. Un élément
   qui ne se voit pas en jeu et qui désoriente dans l'aperçu n'a aucune raison
   d'exister.
3. **Des éclats d'os flottants.** Les crochets étaient quatre bâtonnets de 3 cm
   accrochés à rien.
4. **Rien au bout.** La question posée était « ça mène où ? ». Le dessin ne
   répondait pas : le tuyau s'arrêtait dans l'air.

### Trois objets, trois seulement

- **L'embout** vissé dans la victime : deux couronnes de fer de rayon décroissant
  et quatre crochets d'os plantés en croix. On voit *où* on pique.
- **Le tuyau** : il passe **par-dessus** — un siphon monte et redescend, c'est sa
  définition — avec des **côtes sombres qui glissent vers le porteur**. L'ancienne
  double hélice disait aussi le sens de l'écoulement, mais seulement de près ;
  une côte presque noire sur un tuyau rouge se lit à dix blocs.
- **Le calice** dans la main du porteur, qui **monte d'un sixième à chaque bol**.
  C'est lui qui raconte le volume prélevé, à la place de la colonne. Un
  spectateur comprend le vol de vie sans lire un chiffre.

Le bec verseur vise le **bord de la coupe** : une descente libre faisait
ressortir le tuyau cinquante centimètres *sous* le pied du calice. Et le calice
est tenu 22 cm devant le porteur, pas dans son torse.

### Le piège qui a tout caché

`render()` appelle le constructeur avec les angles de caméra en 4ᵉ et 5ᵉ argument
— `buildThunder(variant, t, salt, camYaw, camPitch)` — parce que la foudre s'en
sert pour ses panneaux-réclame. Depuis que `buildBlood` prend `linkLen` et
`linkEndY` à ces deux places, **la page passait `camYaw` comme longueur de
conduit**, soit 0,16 bloc : tout le siphon se repliait sur la victime. L'aperçu
fixe désormais un lien explicite.

### Et les deux corps

La page montrait un tuyau entre deux points vides. Elle dessine maintenant, dans
le **décor** (là où vit déjà la grille de sol, pas dans le moteur Java), deux
silhouettes filaires légendées **VICTIME** et **PORTEUR**. La question « ça mène
où, sur le joueur ? » a désormais une réponse visible. Caméra de profil pour les
deux signatures de lien : à 0,62 radian un lien horizontal fuit vers le fond.

## §42 — « On dirait une amande » : pourquoi le cœur n'en était pas un

Verdict de la quatrième relecture de la Transfusion, et il était net : *le cœur ne
ressemble pas du tout à un cœur, on dirait une sorte d'amande*. Il avait pourtant
vingt tranches, un sillon, des vaisseaux. Le défaut n'était dans aucun de ces
détails : il était dans le **profil**.

L'ancienne largeur de tranche valait `sin(√v · 2,55) · (0,52 − 0,10 v)`. Cette
courbe est maximale **au milieu** — 0,46 à mi-hauteur — et retombe **aux deux
bouts**, 0,23 en haut comme en bas. Un volume large au centre et pointu aux deux
extrémités porte un nom, et ce n'est pas « cœur » : c'est une amande. Un cœur est
large **en haut**, là où arrivent les gros vaisseaux, et pointu **en bas**, une
seule fois. La règle tient en une ligne : **une pointe, et une seule, et elle est
en bas.**

Le nouveau profil sépare les deux étages au sillon auriculo-ventriculaire, placé
à 74 % de la hauteur. Sous le sillon, les ventricules :
`0,60 · sin(90° · u^0,62) · (0,62 + 0,38·√u)`. Le sinus donne l'**épaule ronde**
sous la ceinture — un cône a des flancs droits, un organe non ; le facteur en
racine resserre le tiers inférieur pour que l'**apex redevienne une pointe**, et
non le bas d'un œuf. Au-dessus du sillon, les oreillettes enflent en sens
inverse : elles se remplissent pendant que les ventricules se vident.

Quatre erreurs annexes, toutes visibles dès qu'on regarde une planche d'anatomie
plutôt que son propre code :

- **Le pan noir.** La face opposée à la lumière tombait à 0,10 de clarté, donc en
  `VENOUS` : la moitié de l'organe était un trou brun. Le relief se garde, mais le
  creux le plus sombre doit rester **du muscle rouge** (plancher 0,70 · 0,30).
- **La rangée de dents.** La ceinture posait 22 cubes de 4 cm sur un périmètre de
  2,9 blocs : un cube tous les 13 cm, donc des **dents**. Trente cubes de 8 cm se
  touchent et font une ceinture.
- **Les orbites.** Les auricules partaient droit sur le côté comme deux bras, et
  leur teinte `VENOUS` ouvrait deux **trous noirs** aux coins de la base. Elles
  s'enroulent maintenant contre l'oreillette, en rouge moyen.
- **L'anse de panier.** Un arc d'épaisseur constante qui redescend aussi bas des
  deux côtés est une anse, pas une aorte. Elle s'amincit en montant (8,6 → 5,6 cm),
  sa branche descendante s'arrête plus haut, et surtout sa crosse est couchée
  **dans le plan de la liaison** : de biais, la caméra la voyait par la tranche et
  l'arc se refermait en patate. **Une courbe ne se lit que de profil.**

Reste la taille : 0,92 bloc d'échelle donnait un organe d'1,12 bloc flottant entre
deux joueurs de 1,80 — une babiole. À 1,45, le cœur fait 1,77 bloc de haut et la
signature a enfin un sujet. Le cadrage de la variante III suit : portée 9,4 au lieu
de 10,4.

Une note d'outillage pour finir. `semcheck.py` a sorti 35 « constante inconnue »
sur `H` et `AV` — les deux grandeurs locales du modèle. Faux positifs : l'outil ne
connaissait que les champs `static`. Il accepte désormais les **variables locales
en majuscules** déclarées avec un type primitif. Un faux positif se corrige dans
l'outil, jamais dans la source.

## §43 — Ce que l'échelle du mob multipliait deux fois

Question posée : les animations qui visent **un seul mob** se mettent-elles à
l'échelle de sa hitbox ? Réponse honnête en deux temps.

Oui, depuis T359, et de la bonne manière : `putPosColor` est le **point de sortie
unique** de toute la géométrie du moteur, et il y applique un facteur tiré de la
hitbox de la victime — `sizeScaleOf` = √(l²·h / 0,6²·1,8), soit 0,42 pour un
poulet, 1,00 pour un joueur, 2,86 pour un golem, 3,59 pour un ravageur. La
transformation se fait **autour du point d'impact**, sur les positions *et* sur
les tailles : l'animation entière grossit, elle ne se contente pas d'épaissir ses
pixels. Rien à refaire de ce côté.

Non, pour tout ce qui n'est pas du dessin. Trois grandeurs reçues par les moteurs
de signature ne sont pas de la géométrie locale : ce sont **des distances déjà
mesurées dans le monde**.

| Grandeur | Qui la produit | Ce qui se passait à l'échelle 3,59 |
|---|---|---|
| Hauteur du sol sous l'ancre | `probeGroundOffset` | la flaque de sang descendait à trois mètres **sous** le sol |
| Longueur du conduit victime → porteur | `updateBloodLink` | le conduit dépassait le porteur de douze blocs |
| Position des relais de la chaîne | `registerThunderLink` | l'éclair allait frapper **derrière** chacune de ses victimes |

Multipliées une seconde fois par la sortie, elles manquaient leur cible — trop
loin sur un gros mob, trop court sur un petit (sur un poulet, le même conduit
s'arrêtait à 5,45 blocs au lieu de 7,00). La correction tient en une fonction,
`unscaleWorld`, appliquée **à l'entrée** : on divise par l'échelle ce que la
sortie va multiplier. Mesuré sur la géométrie réelle, le bout du conduit tombe
désormais à −6,98 pour une cible à −7,00, à 0,78 comme à 1,75 d'échelle.

Le code du mod savait déjà le faire : le Feu, la Terre et le Poison divisent
depuis toujours leur sondage de sol par leur `worldScale`. Ce sont le **Sang** et
l'**Ombre**, qui passent par `probeGroundOffset`, qui ne le faisaient pas.

Reste la question du goût. L'échelle brute va de 0,30 à 4,0, et c'est tenable
pour un nuage de pixels ; pour les cinq signatures du sang, qui sont des
**assemblages** — un échafaud, un calice, un cœur —, à 4,0 le cœur de la
Transfusion ferait sept blocs de haut et l'échafaud du Couperet dix-huit. La
bande est donc resserrée par une racine : `échelle^0,55`, bornée à [0,78 ; 1,75].
Monotone — un mob plus gros donne toujours une animation plus grande — mais un
lapin reste lisible et un ravageur ne remplit pas l'écran.

## §44 — `GENERIC_DRINK` : la même constante, deux types selon la version

Le build a échoué sur deux lignes, et l'erreur est instructive :

```
WeaponVfxServerListener.java:3713: error: cannot find symbol
        SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, ...
  symbol: method value()   location: variable GENERIC_DRINK of type SoundEvent
```

Dans `net.minecraft.sounds.SoundEvents`, les champs ne sont pas tous du même type.
La grande majorité — **1 486** en 1.21.1 — sont des `SoundEvent` nus ; une minorité
— **117** — sont des `Holder.Reference<SoundEvent>`, parce que le jeu a besoin de
les passer à des API qui veulent un `Holder` (sons d'armure, explosions, trident).
Sur un `Holder` on écrit `.value()` ; sur un `SoundEvent` nu, la méthode n'existe
pas.

Le piège est que **la répartition bouge d'une version à l'autre**. `GENERIC_DRINK`
est un `SoundEvent` nu en 1.20.1 et en 1.21.1, mais devient un
`Holder.Reference<SoundEvent>` en 1.21.4. Le code avait été écrit avec le réflexe
de la version récente.

L'inverse — un `Holder` sans `.value()` — n'est **pas** une erreur : `Level.playSound`
et le constructeur de `ArmorMaterial` ont tous deux une surcharge qui prend un
`Holder<SoundEvent>` directement. L'audit a relevé six sites de ce genre
(`TRIDENT_THROW`, trois `GENERIC_EXPLODE`, `ARMOR_EQUIP_LEATHER`,
`ARMOR_EQUIP_CHAIN`) : tous légitimes, aucun touché.

**Garde-fou.** La table complète de la 1.21.1 est désormais dans le dépôt
(`tools/soundevents_1211.json`, 117 holders + 1 486 nus) et `semcheck.py` gagne un
contrôle (C) : `.value()` sur un champ nu, et constante de son inexistante. Passé
sur la version cassée, l'outil ressort **exactement les deux lignes 3713 et 4113**
du compilateur ; passé sur les 212 fichiers du mod corrigé, il ne trouve plus rien.
Cela couvre aussi le vieux piège des **sons inventés** : 472 usages, 158 constantes
distinctes, toutes vérifiées contre le registre réel.

## §45 — Ce que la première partie en jeu a montré

Quatre défauts, tous réels, tous corrigés. Aucun n'était visible depuis l'aperçu :
l'aperçu dessine une scène *déjà cadrée*, il ne joue pas la partie.

**1. Le conduit sortait par l'arrière du mob.** `box()` pose un point à
`x = cx + dx·f`, et les deux signatures à lien dessinent le bout **porteur** à `f`
**négatif** — le calice du Siphon à `−reach+0,22`, le cœur de la Transfusion à
`−linkLen/2`. Le porteur se trouve donc dans le monde en `victime + dir·(−linkLen)` :
il faut que `dir` aille **du porteur vers la victime**. Or `linkDirX/Z` va dans
l'autre sens. Le conduit partait donc à 180° du joueur, dans le vide derrière le
mob — d'où « ça annule à chaque fois comme si j'étais pas détecté ». Un signe.
C'est la deuxième fois que ce piège tombe sur ce projet ; le premier était le
corridor de la main.

**2. Chaque tic de signature appliquait le recul vanilla.** `target.hurt()` pousse
toujours dans la direction attaquant → victime. Onze taillades de Carnage, six bols
de Siphon, et surtout une hémorragie qui bat sans arrêt : la cible dérivait
régulièrement, toujours dans le même sens, et sortait de sa propre animation en
glissant. Un tic de signature n'est pas un coup d'épée — on lui rend sa vitesse
après le dégât. Les reculs **voulus** (l'onde du Carnage, la projection du
Couperet) sont posés après l'appel : ils passent intacts.

**3. Quatre signatures sur cinq ne verrouillaient pas la victime.** Seul le
Couperet tenait sa cible. Les quatre autres la laissaient marcher pendant 120 à
180 ticks pendant que leur décor restait planté au point d'impact.

| Signature | Emprise | Pourquoi là |
|---|---|---|
| Couperet | 86 + 20 t | déjà en place |
| Arête qui goutte | 44 t | l'entaille et les quatre rigoles sont dessinées **sur** le corps |
| Siphon | 150 t | quatre crochets vissés ; lâche **avant** la rupture du tick 153 |
| Transfusion | 118 t | le cœur est calculé au milieu du segment : si la victime marche, il dérive |
| Carnage | 112 t | lâche **avant** l'onde du tick 115 — c'est elle, et elle seule, qui projette |

**4. L'hémorragie battait à dix ticks pour les cinq.** C'est le métronome qu'on
entend le plus longtemps, et il était identique partout : d'où l'impression que
« les timers sont tous pareils ». Chaque signature a maintenant sa cadence —
Couperet **14** (lent et lourd), Arête **6** (rapide, c'est son nom), Siphon **10**
(aspiration régulière), Carnage **8** (nerveux). Le total reste calibré ; seule la
répartition change. Quand deux hémorragies se superposent, c'est la plus rapide
qui l'emporte.

---

## §46 — T386 · le Carnage devient une VOLÉE DE LAMES, et le glint du Feu cesse d'être celui du Sang

Deux demandes sans rapport l'une avec l'autre, traitées dans le même passage parce
qu'elles touchent la même chaîne cliente.

### 1. « Les lames du Carnage », enfin

Levée d'ambiguïté d'abord : l'« animation 5 » dont il était question n'est pas le
Couperet mais **CARNAGE BERSERK** (id 12, `variant=4`, 180 ticks) — la signature
qui portait jusqu'ici onze *arcs*.

**Ce qui n'allait pas.** Les onze taillades étaient des arcs tracés dans un plan
tiré d'un hachage : `planeYaw`, `planeTilt`, un rayon, un centre. Résultat à
l'écran : des rubans rouges qui flottent *autour* du mob sans jamais le désigner.
Aucune pointe, aucun sens de marche, aucun point d'arrivée — donc aucun lien
lisible entre ce qu'on voit et les onze tics de dégât que le serveur applique.

**Ce qui remplace.** Chaque coup est maintenant **une lame** :

| Étape | Ce qu'elle fait |
|---|---|
| naissance | une étincelle de forge à **3,5 → 5,2 blocs** au-dessus du sol, à un azimut écarté par un terme en nombre d'or ; la lame **s'allonge** depuis le vide sur les 20 premiers % de sa vie |
| chute | **25 ticks**, accélérée (deux tiers de carré, un tiers de courbe douce) |
| convergence | elle **rétrécit** en descendant : `shrink = 1 − 0,52·p`, longueur et largeur ensemble |
| cible | le **torse**, `getY() + bbHeight × 0,55` — la hauteur exacte que vise déjà le serveur, dispersée de ±0,13 pour que onze lames ne percent pas le même pixel |
| impact | une **entaille** s'ouvre dans le plan de la lame puis se referme, douze perles giclent **à contre-sens** du coup |

**Silhouette.** Rapport longueur/largeur **1:14** (2,4 → 3,5 blocs de long). Le
premier jet était à 1:7 : des *feuilles*, pas des lames. Profil à pointe franche
sur 18 %, dos presque droit, talon effilé — le sinus du premier jet n'avait pas de
plat, donc pas de lame mais un fuseau. Corps **sombre** (`FRESH` → `GORE`) et fil
**clair** (`PLASMA`) sorti à `w + 0,030` : posé à `0,9 w` il était *dans* le corps,
dont le demi-côté vaut déjà `w + 0,024` — invisible. L'épaisseur latérale suit la
largeur (`0,020 + 0,35 w`) au lieu d'être fixe : une plaque vue par la tranche est
un fil, c'est la leçon de la courbe de l'Ombre.

**Densité.** La chute dure **0,14** (25 ticks) alors que l'écart entre deux impacts
va de 0,029 à 0,067 : il y a donc **quatre lames dans le ciel** au début de la
salve et deux à la fin. À 0,085 puis 0,115 il n'y en avait qu'une à la fois — une
file d'attente, pas une volée. L'échantillonnage suit la longueur (**un cube tous
les 9 cm**, 14 à 30 par lame) : à seize cubes fixes le corps était pointillé.

**Le dégât n'a pas bougé d'un tick.** `at` désigne désormais l'instant de
l'**impact**, pas celui de la naissance : la lame naît 25 ticks *avant*. Les onze
tics de dégât du serveur (25 → 112), l'onde du tick 115 et l'emprise de 112 ticks
restent exactement où ils étaient.

**Son.** Chaque lame **siffle** neuf ticks avant de toucher — `entity.arrow.shoot`
à 2,6 blocs au-dessus de la cible, là où elle vient de se forger. C'est le seul son
de la signature qui *annonce* au lieu de constater.

### 2. Le glint du Feu et celui du Sang se confondaient

**Cause.** `DeepGlintColor` prenait ses deux teintes dans `DeepElement` :
`FIRE.primary = 0xFF4A10` et `BLOOD.primary = 0xDD0000`. Deux rouges-orangés
voisins, et `shade()` ne module que la **luminosité** — la teinte ne bougeait
jamais. Sur une lame qui tourne, les deux familles donnaient la même chose.

**Deux bases écartées.** Feu `0xC0410A` (orange brûlé, nettement plus jaune), Sang
`0x8A0208` (rouge sombre, presque sans vert). Ces constantes sont **locales au
glint** : les VFX continuent de lire `DeepElement`.

**Une respiration par famille**, et c'est elle qui tranche pour de bon :

| | base | sommet | période | durée de l'excursion |
|---|---|---|---|---|
| **Feu** | (192, 65, 10) | (224, 51, 15) | 4,3 s | 20 % du cycle |
| **Sang** | (138, 2, 8) | (186, 0, 25) | 5,7 s | 16 % du cycle |

Deux teintes fixes voisines restent voisines ; deux teintes qui ne battent **ni au
même rythme ni vers le même rouge** se distinguent même quand elles se croisent.
Même au sommet, le feu est clair et orangé, le sang pourpre et sourd. La phase est
décalée par item (`identityHashCode × 2654435761`) : deux armes côte à côte ne
clignotent pas ensemble, ce qui trahirait un effet calculé plutôt qu'une matière.
Montée et retour en `sin` lissé — aucune cassure.

**Où c'est branché.** `DeepGlintColor.familyBreath(stack, argb)`, appelé depuis
`EnchantAura.applySmoothGlint` **après** `smoothGlint`. Jamais avant : faire varier
la *cible* de la transition à chaque image relancerait son chronomètre en
permanence et figerait la couleur sur sa valeur de départ. Le second pôle du
dégradé est recalculé sur la teinte respirée (`breatheAccent`) pour que le dégradé
suive la pulsation. La famille est mise en cache deux secondes dans l'état déjà
tenu par item — sans ce cache, la carte d'enchantements serait relue à chaque image
en plus des deux lectures que `resolve()` fait déjà. Les malédictions sont exclues :
elles sont rouges par décision antérieure et ne respirent pas.

---

## §47 — T387 · les dégâts du Sang s'arrêtent avec l'animation, et ils se voient

Trois retours de jeu du 17/11, tous sur la famille Sang.

### 1. « en fin d'animation ça déclenche des ticks de dégâts qui continuent sur une grande durée sans sens »

Le constat portait sur **les cinq** signatures. Deux causes, pas une.

**La première, arithmétique.** `addBleed` posait `expires = MAINTENANT + durée`, et les
appels tombent **tard** dans la chorégraphie : la morsure du couperet au tick 83, la
rupture du siphon au tick 153, la onzième lame du carnage au tick 112. Avec 140 à 200
ticks de durée, la saignée débordait de 73 à 143 ticks — jusqu'à **sept secondes** après
la dernière image. L'arête était la pire : 200 ticks de saignée posés au tick 10 d'une
animation qui en dure 120.

**La seconde, et c'est elle qui donnait l'impression de dégâts sans fin.** La flaque de
sang mord toutes les 20 ticks, et chaque morsure **reposait** une hémorragie de 80 ticks.
`addBleed` écrasait l'échéance avec la sienne. Une victime qui reste dans la flaque voyait
donc sa fin de saignée repoussée de quatre secondes, **toutes les secondes** : la boucle
ne se fermait jamais tant que la flaque vivait.

Le correctif tient en trois règles.

- Chaque signature calcule une **échéance absolue** en temps de monde, prise à l'instant
  où elle démarre : `bleedEnd = getGameTime() + durée_animation + 20`. Les sept appels
  passent par `addBleedUntil`, qui convertit cette échéance en durée restante. Peu importe
  désormais à quel tick la saignée est posée, elle vise la même fin.
- `addBleed` **ne recule plus jamais** une échéance : `if (previous.expires() > expiry)
  expiry = previous.expires()`. La morsure de flaque, elle, est bornée par la flaque
  elle-même (`pool.expires()`), ce qui ferme la boucle pour de bon.
- Les quatre flaques sont bornées de même, à `durée_animation + 40`.

| signature | animation | dernier dégât posé | fin de saignée | après la fin |
|---|---|---|---|---|
| Couperet | 150 t | t+83 | 170 t | **20 t (1,0 s)** |
| Arête | 120 t | t+10 | 140 t | **20 t (1,0 s)** |
| Siphon | 170 t | t+153 | 190 t | **20 t (1,0 s)** |
| Carnage | 180 t | t+112 | 200 t | **20 t (1,0 s)** |

Le total de dégâts est conservé : ce qui n'est plus étalé est **concentré**. Le couperet
passe de 0,085 à 0,115 par pile et par battement, le carnage de 0,09 à 0,115, le siphon
de 0,10 à 0,16 et de 0,08 à 0,12 — et le dégât que la rupture du siphon n'a plus le temps
de saigner lui est rendu à l'impact, où il **se voit** (0,75 → 1,10 de base).

### 2. « mets des particules / gouttes de sang autour du mob »

Les neuf points de dégâts du bloc appelaient `hurtWithCapturedWeapon` nu : la barre de vie
descendait sans que rien ne coule. Quand une particule sortait, c'était
`FALLING_DRIPSTONE_LAVA`, **orange**, qui se lit comme de la lave.

`bloodDrops` pose deux `DustParticleOptions` taillées pour le sang — `(0.64, 0.02, 0.05)`
à l'échelle 1,35 pour ce qui gicle, `(0.31, 0.01, 0.03)` à 1,70 pour ce qui a déjà coulé.
La géométrie compte autant que la couleur : un nuage **centré** sur l'entité disparaît
dans son modèle, donc les gouttes sont semées sur un **anneau** un peu plus large que la
hitbox, entre 18 % et 76 % de la hauteur, avec une retombée sombre au pied. Le nombre suit
l'intensité, plafonné — une mêlée longue ne doit pas noircir l'écran.

Huit des neuf sites passent désormais par `bloodHurt`, qui ne peut pas frapper sans faire
couler. Le neuvième est `tickBleed`, qui sème son propre anneau à chaque perte, plus dense
quand la victime court.

### 3. « les glints feu et sang se ressemblent encore »

T386 avait déjà écarté les deux bases — (192, 65, 10) contre (138, 2, 8) — et l'essai en
jeu a tranché : toujours trop proches. **Trois** raisons, dont une qui annulait tout le
reste.

**`breathFamilyOf` ne lisait que les enchantements réels.** L'épée donnée par
`/anim <famille>` n'en porte aucun : sa couleur vient d'une clé NBT et son halo du
composant vanilla. La méthode renvoyait donc `BREATH_NONE`, et **la pulsation de T386 ne
s'est jamais déclenchée sur l'outil même qui sert à la juger**. Elle lit maintenant
`DlbAnimFamily` avant toute chose.

**Vingt degrés de teinte ne se lisent pas sur un halo qui défile.** Le critère change :
ce n'est plus la teinte qui sépare les deux familles, c'est le **canal vert**.

|  | base | sommet | vert |
|---|---|---|---|
| Feu | (255, 130, 16) ambre franc | (255, 203, 94) jaune blanc | **toujours ≥ 130** |
| Sang | (124, 0, 18) bordeaux | (200, 8, 41) écarlate artériel | **toujours ≤ 8** |

Un écart de 122 sur un canal entier se voit du coin de l'œil, en mouvement, sur un item
minuscule dans une barre d'action. Et le sens physique suit : une braise plus chaude vire
au jaune, un sang qui bat ne change pas de teinte — il change de **luminosité**, ce que
fait exactement un pouls sous la peau. Les sommets, eux aussi, s'éloignent au lieu de se
rejoindre : T386 montait le feu vers un rouge vif, c'est-à-dire vers le sang, à l'instant
précis où les deux sont le plus visibles.

Les excursions sont élargies (0,20 → 0,30 pour le feu, 0,16 → 0,24 pour le sang) et
presque totales en profondeur (0,97 et 0,94). Le feu passe désormais près d'un tiers de
son cycle à monter vers le jaune. Les deux périodes restent premières entre elles —
4,3 s et 5,7 s — donc les sommets ne tombent jamais ensemble, et la phase reste décalée
par item.

`AnimFamily.glintColor` suit : `FIRE` passe de `0xFF6A2A` à `0xFF8210`, `BLOOD` de
`0xE01822` à `0xCD082A`. `DeepElement` n'est pas touché : ces constantes sont locales au
glint et ne changent aucun VFX.


---

## §48 — T388 · le VIDE, douzième famille : trois signatures, et une monnaie qui n'est pas la vie

Le Vide était resté ce qu'il était depuis le début du mod : trois identifiants
(`VOID_WARP_SPHERE` 26, `DRAGON_CHAOS_PRISM` 27, `NULL_RIFT_FISSURE` 34) qui partageaient
une seule routine générique de **16 à 22 ticks** — une coquille de cubes inclinés qui
s'effondre, une cage, un anneau pointillé — et **aucun gameplay**. Pire : deux des trois
n'avaient même pas leur propre durée. Le prisme partageait son `case` avec
`BIG_BANG_SCAR`, la faille le sien avec `CHAOS_KALEIDO_LOOP` et `ENTROPY_SPHERE`. Les
sortir de ces groupes était le préalable à tout le reste : on ne donne pas une durée
propre à une signature qui n'a pas sa propre étiquette.

### 1. Ce que le Vide fait, et que personne d'autre ne fait

La question posée avant d'écrire une ligne : **quelle est sa monnaie ?**

Le Sang punit — il saigne, il draine, il récompense l'acharnement. L'Ombre aveugle — elle
marque, elle affaiblit, elle récompense l'embuscade. Si le Vide avait payé en dégâts
périodiques, il aurait été du sang bleu.

**Le Vide déplace.** Sa monnaie n'est pas la perte de vie, c'est la **position** : il
tire, il soulève, il éjecte, il retient. Toute sa valeur tactique est là — un ennemi
arraché à sa place est un ennemi qui ne fait plus ce qu'il faisait.

Conséquence assumée, et c'est la décision la plus discutable du lot : **le Vide ne donne
aucun bonus de dégâts à son porteur.** La ferveur du Sang donne déjà Force à qui
s'acharne ; refaire la même récompense aurait revenu à écrire deux fois la même famille
avec une autre palette. Le Vide paie en **mobilité** : Vitesse et Chute Lente. La famille
dont le sujet est le déplacement récompense par du déplacement.

### 2. L'empreinte du néant — un SEUIL, pas une goutte à goutte

`VOID_MARKS`, quatre au maximum. Elle **ne fait aucun dégât par elle-même**. C'est la
différence de nature avec l'hémorragie : l'hémorragie est un robinet, l'empreinte est un
compteur. À la quatrième, l'espace cède — implosion, **Lévitation**, Lenteur II, et un
écho banqué au porteur.

Trois leçons des tours précédentes sont appliquées **avant** d'avoir le bogue :

* **Échéance absolue.** `addVoidMark` reçoit une `deadline` calculée au démarrage de la
  signature et ne peut que la **raccourcir** (`min`), jamais l'allonger. C'est exactement
  le défaut de l'hémorragie sans fin du §47, repris à l'envers.
* **Purge.** `sweepVoidTables` balaie les deux cartes toutes les cent ticks, par le tick
  d'entité déjà en place — aucun abonnement de plus. `BLOOD_POOLS` n'a toujours pas la
  sienne ; celle-ci est écrite dès le premier jour.
* **Lisibilité.** Une ressource à seuil qu'on ne voit pas n'est pas une ressource. Si
  rien ne distingue un corps à trois empreintes d'un corps à une, le joueur ne peut pas
  choisir sa cible et le système se réduit à du hasard. `tickVoidMark` **compte à voix
  haute** : une graine de poussière cyan au-dessus de la tête par empreinte, deux fois par
  seconde, en étoile tournante. À trois sur quatre, un souffle de `PORTAL` s'ajoute.

Les échos du porteur se périment en **200 ticks**. Un compteur nu aurait eu deux défauts,
et le second est un bogue : il n'aurait jamais été vidé, et surtout il aurait récompensé
l'**accumulation** au lieu de l'**enchaînement** — trois effondrements séparés par dix
minutes de marche auraient payé comme trois effondrements en six secondes.

### 3. Trois aérations, trois réponses différentes à la question du duel

C'est la contrainte répétée à chaque famille : **AoE et ennemi seul**, et aucun des deux
cas décevant. Les trois n'y répondent pas de la même manière, et c'est voulu.

| | foule | duel |
|---|---|---|
| **Sphère** (160 t) — elle **RASSEMBLE** | six tractions de 5 ticks vers le centre, vitesse **proportionnelle à la distance**, Lenteur II | il n'y a personne à rassembler : elle **s'accroche**, soulève (Lévitation) et chaque traction tape **×2,45** |
| **Prisme** (190 t) — il **DISTRIBUE** | sept faisceaux, **un corps distinct chacun**, tous rendus visibles à travers les murs (Lueur) | sous trois corps, les sept faisceaux **CONVERGENT** : chacun tape moins, mais sept traits posent quatre empreintes — la détonation est **garantie** |
| **Faille** (170 t) — elle **TRIE** | neuf aspirations **vers l'axe**, puis éjection perpendiculaire à la fermeture | la victime unique est **RETENUE** 70 ticks : elle ne peut pas sortir du corridor, et chaque aspiration tape **×1,73** |

**La vitesse de traction de la sphère est proportionnelle à la distance**, bornée à 0,78.
Une vitesse constante aurait fait osciller les corps proches autour du point comme un
pendule : ils traversent le centre, repartent, sont retirés. Proportionnelle, ce qui est
loin arrive vite et ce qui est près ne bouge presque plus.

### 4. Le CORRIDOR — la seule aire du mod où le placement latéral décide

`voidCorridor` est une forme d'aire nouvelle : une bande de **9 blocs de long sur 3,2 de
large**, orientée **perpendiculairement à l'axe du coup**, parce que la déchirure s'ouvre
en travers du chemin de l'arme.

Un disque aurait été un contresens : il aurait touché ce qui se trouve devant et derrière
la déchirure, c'est-à-dire ce qui n'en est séparé **par rien**. La faille est un **plan** ;
sa trace au sol est une **ligne**. Se tenir dans son axe ou à deux pas de côté doit
changer le résultat, et c'est le seul endroit du mod où c'est vrai.

Second point, plus fin : les aspirations tirent vers l'**axe**, pas vers le **centre**.
Seule la composante perpendiculaire est appliquée. Tirer vers le centre aurait entassé
tout le corridor en un point — et la ligne, qui est la seule idée de la signature,
n'aurait plus été lisible une fois la première aspiration passée.

### 5. Le glint : pourquoi PAS le cyan de l'horizon

> **T390.** La palette a été repeinte depuis (voir §50) : la lèvre vaut maintenant
> `0x9518E2`. Le raisonnement ci-dessous est conservé tel quel parce que sa **conclusion**
> l'est aussi — le glint reste `0xFF3FD8`, et il tient toujours l'écart d'un canal entier
> face à la nouvelle lèvre comme face aux onze autres familles.

La couleur que le joueur voit dans l'animation est `HORIZON 0x7FD4E8`. C'était le choix
évident, et il est faux.

L'Eau tient déjà `0x62C7E8`. Soit, canal par canal : **(98, 199, 232)** contre
**(127, 212, 232)** — 29, 13 et **0**. La règle acquise en T386 puis T387 est qu'il faut un
**canal entier d'écart (≥ 120)** avec chaque famille, sans quoi deux lames se confondent
en main. Le cyan échouait sur les trois.

Restait à chercher dans la palette du Vide la teinte qui n'appartient à personne :
`CHORUS 0x8E5BA6`, « l'autre côté ». Montée à sa valeur de **reflet** — même raisonnement
que l'Ombre : sur une lame, un glint est un reflet, donc la valeur la plus claire de
l'élément — elle donne **`0xFF3FD8`**. Vérifié un par un contre les onze autres familles :
≥ 120 d'écart sur au moins un canal, le plus serré étant la Terre (131 sur le vert).

Pour le code couleur du nom, les seize codes hérités ne contiennent **qu'un** magenta (`§d`)
et **qu'un** violet (`§5`), et les deux sont pris — par la Foudre et par l'Ombre. Le Vide
prend donc **`§9`**, ce qui reste du côté froid de la roue et demeure lisible sur le fond
sombre d'une infobulle (`§0` et `§8` n'y sont plus qu'une tache).

### 6. Le volet visuel — `VoidSignatureVfx.java`, et six pièges de lisibilité

Le moteur suit le patron de `BloodSignatureVfx` : une seule primitive `box(...)`, un seul
appel à `emitVoxelCube`, `voxelPulseEnabled` coupé en try/finally. Six défauts ont été
corrigés au rendu, chacun généralisable :

1. **Le fondu qui coûte plus cher.** Une primitive linéaire dont le pas dérive de
   l'épaisseur (`len / (w · k)`) **multiplie ses cubes en s'amincissant**. Pic mesuré :
   **5 195 cubes** sur une image de la sphère, pour un plafond moteur de 7 000. Plancher
   absolu sur le pas, et la courbe de densité devient plate : 1 211 / 1 659 / 1 677 /
   1 596 / 1 662 / 1 554.
2. **Corollaire positif.** Une fois le plancher posé, le pas devient une **variable
   expressive** : `0,040 + 0,105 · (1 − bend)`. La grille est pointillée au loin et
   jointive là où l'espace se tord — la densité raconte la déformation.
3. **Une sphère rendue par des parallèles de latitude est une bouillie grise** : on voit
   l'avant ET l'arrière. Treize parallèles remplacées par **trois grands cercles
   orthogonaux**.
4. **Une surface d'épaisseur nulle disparaît par la tranche.** Le disque d'accrétion
   reçoit un terme d'évasement et de gondolement,
   `flare = (0,035 + 0,085 · clamp(r1/3,2)) · sin(atan2(l1,f1) · 2 + t · 5)`.
5. **Dans une gerbe symétrique, l'élément central ne subit pas le balayage** (`sin(0) = 0`)
   et casse la silhouette en deux. Le quatrième des sept faisceaux du prisme reçoit son
   propre rôle : il devient le **cou** du dragon — il se raccourcit et se relève en courbe.
6. **Une grille témoin plus étroite que l'horizon rend la déformation invisible.** 9×9
   au pas de 0,78, soit une demi-largeur de 3,12 pour un horizon qui monte à 1,10.

### 7. Ce qui reste à valider en jeu

Le bac à sable n'a ni JDK, ni Gradle, ni MCreator : `jparse`, `semcheck` et `typecheck`
passent (baselines inchangées, Void 0 erreur), mais **rien n'est compilé**. À vérifier
manette en main :

* la **densité** des trois signatures sur un dragon (`sizeScale` 4,0) — la bande resserrée
  de `bloodSignatureScale` leur est appliquée, mais elle n'a jamais été jugée sur une
  grille ;
* le **corridor** de la faille : se placer dans l'axe, puis à deux pas de côté ;
* la **convergence** du prisme en duel : les quatre empreintes doivent tomber avant le
  septième faisceau ;
* le **magenta** du glint à côté du violet de l'Ombre et du bleu-violet de la Foudre.

---

## §49 — T389 · le Vide passe de trois à cinq : la PORTE et la TRAME

Le retour sur la T388 tenait en sept mots : **« il faut 5 animations, tu en a 3 »**. Le
Sang en a cinq depuis la T379 ; le Vide venait de naître avec trois. La rotation
`/anim void` tirait donc dans un sac trois fois plus petit, et la douzième famille était
la seule à ne pas tenir son rang.

Deux signatures de plus, `HORIZON_GATE` **44** et `NULL_LATTICE` **45**. Les
identifiants 1 à 43 étaient tous pris, sans trou : les deux entrées vont **en fin
d'énumération**, comme `TEMPEST_EYE` avant elles, parce que `ordinal()` sert de sel de
rendu et qu'une insertion au milieu décalerait le sel de tout ce qui suit.

### 1. Le critère : un VERBE libre, pas une forme libre

Les trois signatures existantes occupaient déjà trois verbes : la sphère **rassemble**,
le prisme **distribue**, la faille **trie**. Une quatrième qui aurait seulement eu une
autre silhouette aurait été la même signature jouée une quatrième fois. Il fallait deux
verbes que le Vide peut tenir et que personne d'autre ne tient :

| # | Nom | Verbe | Ce que ça change pour le joueur |
|---|-----|-------|---------------------------------|
| 26 | Sphère de distorsion | **RASSEMBLE** | met six corps au même endroit |
| 27 | Prisme du dragon | **DISTRIBUE** | un faisceau par corps |
| 34 | Faille du néant | **TRIE** | une ligne, pas un disque |
| **44** | **Porte d'horizon** | **EXPULSE** | sort les corps de la zone, en étoile |
| **45** | **Constellation nulle** | **LIE** | ce qui touche un nœud touche tous les nœuds |

La porte est l'**exact inverse** de la sphère, et c'est la raison de la garder : deux
outils opposés sur la même famille donnent au joueur une décision à prendre. La trame est
la seule signature du mod dont la zone est **figée à la pose** — les quatre autres
réinterrogent le monde à chaque battement et laissent toujours une porte de sortie.

### 2. Ce que chacune apporte à la géométrie de la famille

Le Vide ne dessine que des **arêtes** : pas un aplat dans les 1 648 lignes du fichier.
Chaque nouvelle forme devait donc apporter une propriété topologique que les trois
premières n'avaient pas.

* **La porte est un TORE** — la seule surface **fermée à trou** de la famille. Une sphère
  n'a pas d'autre côté, un anneau si. Tout le sens de la signature tient dans ce trou,
  d'où une **arête intérieure** dédiée, un fil clair tracé à `ring − tube · 0,92` : sans
  elle, le trou se lit comme un disque sombre et la porte redevient une roue.
* **La trame est un GRAPHE** — le seul objet **non centré**. Sept nœuds, onze arêtes, pas
  de milieu. Les quatre autres signatures naissent sur la victime ; celle-ci naît
  **entre** les victimes, et c'est précisément ce qui lui permet de dire que plusieurs
  corps sont solidaires.

### 3. Les deux pièges payés en route

**Un tube bâti sur les axes du monde se vrille.** Déjà vécu sur la couronne du prisme en
T388 : le tore reprend un repère **explicite** recalculé à chaque pas — normale radiale
`(cos a, sin a · cos θ, sin a · sin θ)`, verticale locale `(0, −sin θ, cos θ)`. Sans lui,
la section tourne avec l'azimut et l'anneau sort en ruban tordu.

**Une arête droite d'emblée est un trait de règle : elle ne pèse rien.** Les onze arêtes
de la trame **pendent** avant de se tendre, en vraie **chaînette** :

```
sag = (cosh(1,65) − cosh(1,65 · (2u − 1))) / (cosh(1,65) − 1)
```

`Mth` n'a pas de `cosh` : c'est `Math.cosh`, en `double`. Elles se tendent **une par une**
dans un ordre haché (`tenseAt = 0,38 + hash(sel + 51, e · 29) · 0,30`), et un éclat blanc
parcourt l'arête au moment où elle se tend — le seul endroit du dossier où quelque chose
se propage **le long** d'une arête.

### 4. Trois réglages corrigés à l'image, pas au jugé

Le harnais d'inspection a renvoyé trois défauts que la lecture du code n'aurait pas
donnés :

1. **La convergence arrivait trop tôt et trop fort** (`pull = converge · 0,88`) : entre
   0,80 et 0,86 la trame n'était plus qu'une bouillie grise de 1,8 bloc. Rééquilibrée en
   `converge · 0,46 + collapse · 0,54` — la trame reste **lisible** pendant qu'elle se
   resserre, et le pincement final est bref.
2. **Onze arêtes d'un seul ton font une masse.** Une fois tendues, elles alternent
   `GLACIER` et `HORIZON` sur la parité de l'arête.
3. **La déchirure passait après l'effondrement**, donc les arêtes avaient une longueur
   nulle au moment de rompre et *personne ne voyait rien rompre*. L'ordre est inversé :
   `tear` démarre à 0,86, `collapse` à 0,92.

### 5. Le gameplay, deux cas chacun

Doctrine T388 inchangée : **le Vide déplace**, le porteur est récompensé en **mobilité**,
jamais en dégâts.

**Porte d'horizon** (180 ticks, `base = max(1,3 ; dégât · 0,80)`). *Foule* : sept transits
toutes les 18 frames ; chacun prend le corps le plus proche de l'anneau et l'expédie sur
un azimut qui **tourne** d'un septième de tour — au bout des sept, le paquet est
disséminé en étoile. *Duel* : rien à disséminer, la porte devient un **moulin** — l'azimut
fait un **demi-tour** à chaque passage, la victime est chassée d'un côté puis de l'autre
et chaque transit tape **deux fois plus fort** (0,92 contre 0,45). Fermeture à 150 :
`base · 1,25`, deux empreintes, éjection.

**Constellation nulle** (200 ticks, `base = max(1,3 ; dégât · 0,78)`). La liste est
**capturée une fois**, sept nœuds au plus, et fuir n'en sort pas. *Foule* : onze mises en
tension, chacune frappe **tout le monde** pour `base · 0,26` — modeste par corps, énorme
sur le groupe. *Duel* : une trame à un seul nœud n'a pas d'arête, alors elle se referme
sur sa victime — `applyMovementHold` 90 ticks et l'intégralité des tensions pour elle
seule (`base · 0,42`). Rupture à 184 : `base · 1,15`, deux empreintes, les bouts partent.

La trame capture des **identifiants**, jamais des références : une référence retenue deux
cents ticks garde en mémoire une entité morte et retirée du monde.

### 6. Les cinq points d'accroche d'un identifiant neuf

Posés dans cet ordre, et aucun n'est facultatif :

1. `WeaponVisualEffectId` — la constante **en fin de liste**, plus ses alias.
2. `WeaponVfxMotion` l.105 — **le seul `switch` exhaustif du dépôt, sans `default`** : sans
   les deux `case`, le fichier ne compile plus. C'est le piège le plus cher d'ici.
3. `WeaponWorldVfxManager` — `isVoidSignature`, `fxDuration` (180 et 200), aiguillage
   `impactVoid` vers `VoidSignatureVfx.GATE` / `.LATTICE`.
4. **Les deux tables de `/anim`** — `animationSlashCount` (le Vide passe à 5, comme le
   Sang) **et** `animationSlashEffect` (cinq branches) ; sans la seconde, les index 3 et 4
   retombent sur le `default` et la Faille sort deux fois sur cinq.
5. `WeaponVfxServerListener` — `ANIM_VOID` à cinq entrées et le routage de
   `applyVoidGameplay`.

Au passage, `dummyNextEffectTick` ignorait **les cinq** signatures du vide : le balayage
`/dlbvfx dummytest` passait à l'effet suivant au bout de 62 ticks, soit au tiers de la
sphère et au quart de la trame. La revue complète ne montrait la fin d'**aucune** d'entre
elles.

### 7. Ce qui reste à valider en jeu

`jparse`, `semcheck` et `typecheck` passent — les écarts de `semcheck` (+4 sur le Manager,
+2 sur `WeaponVfxMotion`) sont exactement les six `case` ajoutés, que l'outil ne sait pas
résoudre. **Rien n'est compilé.** Manette en main :

* `/anim void` répété une vingtaine de fois : les **cinq** doivent sortir, aucune deux
  fois plus souvent que les autres ;
* `/dlbvfx play 44` puis `45`, sur un mob **seul** puis sur un groupe de cinq ;
* la porte en duel : la victime doit faire un **va-et-vient**, pas s'éloigner ;
* la trame : s'éloigner après la pose ne doit **pas** sortir du contrat ;
* le trou de la porte vu de dessus, et l'arête intérieure à contre-jour.

---

## §50 — T390 · VIDE : la repeinte, et le trou qui n'était pas là

### 0. Le constat

> « tes couleurs sont pas bonnes ! »

Quatre gammes ont été fournies avec la remarque :

| gamme | teintes |
| --- | --- |
| **Deep Abyssal Void** | `#050D25` |
| **charbon** | `#000000` · `#1c1c1c` · `#3d3d3d` · `#5c5c5c` · `#8c8c8c` |
| **Call of the Void** | `#0e0216` · `#5b175d` · `#6c13a4` · `#9518e2` · `#2d0043` |
| **bleu / mauve délavé** | `#282077` · `#4B5295` · `#686D9B` · `#9B8491` · `#B18E8C` · `#E2BFAE` |

Elles règlent deux problèmes qui n'en font qu'un seul. Le premier est la **palette voxel**
des cinq signatures, bâtie en T388 autour d'un cyan. Le second est plus ancien et n'avait
jamais été nommé : les cinq figures ne dessinent que des **bords**, et un bord n'existe que
si ce qu'il encadre est sombre. Devant un mur de pierre claire, l'anneau de la Porte
d'Horizon encadre de la pierre claire.

### 1. La repeinte — la règle, et son unique exception

**La luminance de chaque rôle est conservée, seule la teinte change.** Tout le fichier
repose sur une échelle qui monte des corps presque noirs jusqu'au fil de l'anneau ;
intervertir deux barreaux rendrait les cinq signatures illisibles, aussi justes soient les
couleurs prises une à une.

| rôle | T388/T389 | L | T390 | L | origine |
| --- | --- | --- | --- | --- | --- |
| `NOTHING` | `0x04030A` | 4 | `0x0E0216` | 8 | Call of the Void, le noir violé |
| `ABYSS` | `0x0C0A18` | 12 | `0x050D25` | 13 | **Deep Abyssal Void** |
| `BASALT` | `0x1A1726` | 26 | `0x2D0043` | 21 | Call of the Void, la prune |
| `SLATE` | `0x2E2B40` | 46 | `0x282077` | 44 | délavé, l'indigo |
| `HALO` | `0x4A5E78` | 91 | `0x6C13A4` | 62 | Call of the Void, le violet moyen |
| `HORIZON` | `0x7FD4E8` | 189 | `0x9518E2` | **84** | Call of the Void, le **violet néon** |
| `GLACIER` | `0xBFF2FF` | 228 | `0xD7B4F7` | 198 | `#9518E2` surexposé |
| `SINGULAR` | `0xFFFFFF` | 255 | `0xFFFFFF` | 255 | inchangé |
| `ENDSTONE` | `0xD8D2A4` | 207 | `0xD8D2A4` | 207 | **inchangé** |
| `ENDSTONE_DK` | `0x8A855F` | 130 | `0x8A855F` | 130 | **inchangé** |
| `CHORUS` | `0x8E5BA6` | 115 | `0x4B5295` | 88 | délavé, le bleu ardoise |
| `INVERT` | `0xFF9A4A` | 175 | `0xB18E8C` | 152 | délavé, le rose cendré |

Échelle résultante : **8 < 13 < 21 < 44 < 62 < 84 < 198 < 255**, monotone, vérifiée par le
script de repeinte qui refuse d'écrire si elle ne l'est plus.

**L'exception, c'est la lèvre, et elle a coûté une passe pour rien.** Première tentative :
`#C3C8E8` (le bleu poussière éclairci), luminance 202, c'est-à-dire l'ancien cyan à la
teinte près. Rendu au banc d'essai : parfaitement lisible, et **sans identité** — un blanc
sale. Le diagnostic est net : à cette luminance, aucune des quatre gammes n'offre de
couleur chromatique, donc conserver la luminance de la lèvre revenait à **ne rien
repeindre du tout**.

Seconde passe, règle inversée : le violet néon devient la teinte maîtresse et l'échelle se
reconstruit autour de lui. La lèvre tombe de 189 à 84. Ce qui porte une figure n'est pas
l'écart lèvre / fond mais l'écart **lèvre / corps** : 84 contre 21, soit quatre fois,
contre cinq fois auparavant. Les deux crans au-dessus — surexposition `#D7B4F7` et fil de
l'anneau blanc — gardent leur luminance et ramènent la lumière là où il en faut.

Deux invariants. Les tons de **matière étrangère** ne bougent pas : ils représentent le
monde avalé, pas le vide, et les repeindre supprimerait le contraste qui fait lire
l'engloutissement. Et puisque le violet est désormais la couleur de la maison, l'**autre
côté** aperçu dans la fente passe au bleu ardoise : c'est lui, maintenant, qui doit détonner.

**Réserve honnête :** l'Ombre tient `0x4D318E` / `0x7100A7`. Le violet n'est donc plus
l'exclusivité d'une famille. Les deux restent distinguables par la luminance (84 contre 44
et 62) et surtout par la forme, mais la séparation n'est plus celle, nette, du cyan contre
le violet. C'est le prix de la gamme demandée, pas un oubli.

### 2. Le trou : un vrai core shader, le seul du mod

Le reste du mod est en mini-cubes. Ici, non — et pour une raison précise : ce qui rend un
champ d'étoiles crédible, c'est qu'il est accroché à l'**écran** et non au monde, donc
qu'il glisse quand la tête tourne. Aucun empilement de voxels ne produit ça. Seul un
shader qui prend ses coordonnées de texture dans l'espace **projeté** le fait. C'est le
procédé exact du portail de l'End, d'où la copie.

```
assets/deep_lucky_block/textures/effect/void_sky.png      64 x 64    #050D25 moucheté
assets/deep_lucky_block/textures/effect/void_stars.png   128 x 128   4,6 % de couverture
assets/deep_lucky_block/shaders/core/void_portal.json     Sampler0/1, GameTime, EndPortalLayers = 15
assets/deep_lucky_block/shaders/core/void_portal.vsh      texProj0 = projection(gl_Position)
assets/deep_lucky_block/shaders/core/void_portal.fsh      15 couches additives, COLORS[16]
deepluckyblock/client/ModShaders.java                     RegisterShadersEvent (bus MOD, client)
deepluckyblock/client/ModRenderTypes.java                 ShaderStateShard + MultiTextureStateShard
deepluckyblock/client/VoidPortalFx.java                   le disque face caméra
```

Trois écarts volontaires avec `rendertype_end_portal` :

1. **Le fond.** Vanilla multiplie `end_sky.png` par `COLORS[0]`, ce qui l'écrase à presque
   noir. Ici `void_sky.png` vaut déjà `#050D25`, donc on le prend tel quel : noir absolu à
   l'œil, mais avec le bleu de nuit qui empêche la surface de se confondre avec un trou de
   rendu.
2. **La palette.** `COLORS[]` reprend les quatre gammes, de la couche la plus lointaine
   (indice 0, étoiles minuscules et serrées) à la plus proche (indice 14, grosses étoiles
   rares, violet néon). Chaque entrée garde la **teinte** de son hexadécimal ; seule la
   luminosité suit une rampe croissante. Sans cette rampe, quinze couches additives
   saturent en blanc et toute la palette disparaît.
3. **Le mouvement.** Le portail de l'End ne fait défiler ses couches qu'à la verticale et à
   vive allure. Le Vide doit **dériver** : montée ralentie de moitié, plus une dérive
   latérale d'autant plus rapide que la couche est proche.

Deux pièges déjà payés par d'autres mods, et notés ici pour ne pas les repayer.
**Un :** ne jamais retenir l'instance passée à `registerShader` — c'est celle livrée **au
callback** qu'il faut garder, sinon le premier F3+T laisse un pointeur sur un programme GL
détruit. **Deux :** vanilla ne cherche ses shaders que dans `minecraft:shaders/core/` ;
NeoForge patche le chargement pour autoriser un namespace, d'où le `deep_lucky_block:` à la
fois dans le `ResourceLocation` Java **et** dans les champs `vertex` / `fragment` du JSON.

### 3. La fenêtre, et pourquoi elle ne trahit pas la règle du bord

`VoidSignatureVfx` applique une règle stricte : on ne dessine jamais le vide, on dessine
son **bord**. La fenêtre ne dessine toujours pas de matière — elle **remplace ce qu'on voit
à travers**. Le disque est opaque, écrit la profondeur, et affiche un ciel d'abysse. Le bord
voxel reste l'objet ; la fenêtre garantit seulement qu'il y a l'abîme derrière lui, et pas
le mur du village.

#### La première version était fausse, et c'est la page qui l'a dit

La T390 posait **cinq** fenêtres : un disque face caméra, de rayon fixe, au cœur de chaque
signature. Tant que le shader n'était jugé que sur un banc isolé, ça tenait. Une fois le
shader branché **dans la page d'aperçu**, derrière les vraies figures, les deux erreurs ont
sauté aux yeux en une capture.

**La taille.** Les rayons venaient de l'**emprise** de la figure, pas de son **ouverture**.
L'horizon de la sphère monte à 1,10 : elle recevait une fenêtre de 1,55. Un disque qui
déborde de la lèvre cesse d'être un trou — il redevient un objet, avec sa silhouette
circulaire à lui, c'est-à-dire exactement ce que la règle du bord interdit. Le défaut qu'on
croyait corriger était recréé un cran plus loin.

**L'orientation.** Un panneau-réclame ne vaut que pour une figure dont l'ouverture fait face
au joueur d'où qu'il regarde, et il n'y en a qu'une : la sphère. La Porte est un anneau
**posé au sol** — un disque vertical planté dedans lisait comme une bulle flottante au-dessus
de l'anneau. La Faille est une fente **verticale orientée par l'attaque** : son ouverture est
dans le plan des deux lèvres.

#### Trois fenêtres, trois plans, zéro constante

Les trois fenêtres ne portent plus aucune cote à elles. Elles **appellent** les fonctions de
forme de la figure, extraites pour l'occasion dans `VoidSignatureVfx` et partagées par le
rendu, par la fenêtre et par le miroir JS :

| | plan | demi-étendues | hauteur du centre | source |
| --- | --- | --- | --- | --- |
| Sphère de Distorsion | face caméra | `warpHorizon(t) × 0,94` (≤ 1,08) | `WARP_YC` = 0,46 | l'horizon lui-même |
| Faille du Néant | plan des lèvres | `RIFT_LIP × riftGape(t) × 0,80` (≤ 0,66) **×** 1,34 | sol + 0,05 + 1,53 | l'écartement des lèvres |
| **Porte d'Horizon** | **horizontal** | `gateRing(t) × 0,88` (≤ 1,87) | sol + 0,06 | le rayon de l'anneau |

Les marges — 0,94 · 0,80 · 0,88 — ne sont pas décoratives : ce sont elles qui garantissent
que le contour tombe **à l'intérieur** de la lèvre, donc qu'on ne voie jamais la silhouette
propre de la fenêtre. Et comme les trois tailles sont celles de la figure, l'ouverture et la
fermeture n'ont plus besoin d'être programmées : la fenêtre naît, grandit et meurt avec ce
qu'elle remplit. Les deux `smoothstep` de la première version ont disparu avec les
constantes qu'elles pilotaient.

**Le Prisme et la Trame n'en ont pas, et c'est un choix.** La fenêtre répare les figures qui
encadrent un **grand vide**. Le prisme est un octaèdre en fil de fer habité par son cœur et
sept faisceaux ; la trame est un réseau d'arêtes sans intérieur. Leur ajouter un disque,
c'était poser un trou là où aucune lèvre ne le justifie. Une ligne fine se lit sur n'importe
quel fond — c'est l'**aire vide** qui avait besoin d'être comblée, pas le dessin.

**Conséquence cherchée, pour la Faille :** vue dans l'axe de l'attaque, la fenêtre s'écrase
en trait et disparaît. C'est la bonne réponse — la fente aussi. Pour la Porte, même logique
dans l'autre sens : vue du ras du sol, la mare se referme en ligne, et il faut lever un peu
la tête pour voir dans le trou. Une fenêtre qui reste pleinement visible de partout serait
une décalcomanie.

#### Une seule source pour les trois cotes

`warpHorizon`, `riftGape` et `gateRing` étaient enfouies au milieu de trois méthodes de rendu
de plusieurs centaines de lignes. Les recopier dans la fenêtre, c'était s'engager à les tenir
à jour à deux endroits — et la palette du Vide a déjà appris ce que ça coûte. Elles sont
désormais publiques, appelées par les renderers eux-mêmes, et le miroir JS les expose dans
`VOID_SHAPE` pour que la page les lise aussi. Trois exemplaires du **nom**, un seul du
**nombre**.

Aucun état ne survit à une image. Le rendu des signatures **dépose** une fenêtre par image,
la classe la consomme dans la même étape — rien à nettoyer au changement de monde, rien à
resynchroniser entre deux horloges. L'ordre des deux écouteurs `AFTER_PARTICLES` n'étant pas
garanti, la file peut être dessinée avec une image de retard : invisible sur un disque qui
grandit en vingt, et c'est le seul couplage accepté.

### 4. Ce qui a été vérifié sans le jeu

Ni JDK ni Minecraft ici. Ce qui a pu être prouvé l'a été :

* le `.vsh` et le `.fsh` ont été **portés mécaniquement en GLSL ES 3.00** (deux
  substitutions) et exécutés dans Chromium/SwiftShader, avec les vraies textures et la même
  géométrie — compilation, édition de liens et rendu sans erreur ;
* mesure sur le disque : **6 %** de pixels allumés, luminance moyenne 16 / 255, pic 107.
  C'est bien un champ d'étoiles, pas une purée laiteuse — le risque n°1 de quinze couches
  additives ;
* le champ est **accroché à l'écran** : à géométrie identique, déplacer le disque dans le
  viewport change entièrement le motif. La parallaxe est là ;
* les cinq signatures repeintes ont été re-rendues au banc voxel (1677 / 1494 / 476 / 942 /
  996 cubes, inchangés) et relues une par une ;
* **la page d'aperçu exécute désormais le shader elle-même**, derrière les vraies figures et
  composé dans l'ordre du peintre. Trois mesures y ont été prises :
  * le partage par profondeur fonctionne — 847 cubes derrière / 830 devant pour la sphère,
    258 / 218 pour la faille, 436 / 506 pour la porte. Ce qui passe derrière la fenêtre
    disparaît, y compris la moitié arrière de la silhouette de la victime ;
  * la parallaxe est **prouvée sans bouger la caméra** : à deux niveaux de zoom, le même
    carré de 40 × 40 pixels au centre de la mare rend une empreinte **identique au bit
    près** (1 111 683) alors que le trou, lui, a presque doublé. Les étoiles sont accrochées
    à l'écran, pas au disque ;
  * la fente disparaît bien quand on la regarde par la tranche, et la mare quand on
    s'accroupit au ras du sol.

### 5. À essayer en jeu

* `/dlbvfx play 26`, `27`, `34`, `44`, `45` **devant un mur de pierre claire** : c'est le
  cas qui motivait la fenêtre ;
* la même chose de nuit, puis dans l'eau, puis en regardant le ciel ;
* tourner autour d'une Porte d'Horizon ouverte : les étoiles doivent **glisser**, pas suivre
  le disque ; et la mare doit rester **couchée dans l'anneau**, jamais debout ;
* se mettre **à hauteur du sol** devant une Porte : la mare doit se refermer en trait. Se
  placer **dans l'axe de l'attaque** devant une Faille : la fenêtre doit disparaître avec la
  fente, pas rester affichée en panneau ;
* F3+T pendant qu'une signature est à l'écran : le shader doit se recharger sans crash ;
* vérifier que la lèvre violette se distingue encore d'une signature d'Ombre jouée juste
  après.
