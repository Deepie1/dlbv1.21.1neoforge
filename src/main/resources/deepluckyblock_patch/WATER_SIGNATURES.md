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

