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
