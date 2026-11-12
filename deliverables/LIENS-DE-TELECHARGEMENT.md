# Liens de téléchargement — toujours à jour

Ces liens pointent sur la **branche de travail** (`arena/605e67fb-dlbv1-21-1neoforge`), pas sur un commit figé : ils servent donc **toujours la dernière version poussée**. Aucune mise à jour de ce fichier n'est nécessaire quand le code change.

Forme utilisée : `https://github.com/<dépôt>/raw/refs/heads/<branche>/<chemin>` — GitHub redirige vers le fichier brut exact.

> **Si le navigateur sert une vieille version** (le CDN de GitHub garde les fichiers bruts en cache quelques minutes), remplacer `refs/heads/arena/605e67fb-dlbv1-21-1neoforge` par le SHA du commit — par exemple `raw/fa83a2f/…` pour la v1.3 — ou ajouter `?v=2` à la fin de l'adresse.

## Eau — v1.4 (retours de la partie d'essai du 12 novembre 2026)

| Fichier | Lien |
|---|---|
| **Sources complètes (ZIP, 23 entrées, 410511 o)** — moteur + terrain + faux trou + gameplay + doc + outil + miroirs | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Water_Three_Signatures_src.zip |
| **Aperçu animé v1.4** (HTML autonome, ZIP ci-dessus embarqué) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Eau_v1_preview.html |
| `WaterSignatureVfx.java` — moteur voxel (raz-de-marée / maelström / givre) · **corrigé** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WaterSignatureVfx.java |
| `WeaponVfxServerListener.java` — gameplay eau + appels du faux trou · **corrigé** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |
| `GhostBlockMask.java` — faux trou : efface / creuse / gèle le sol côté client, sans toucher au monde | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/GhostBlockMask.java |
| `WaterTerrainVfx.java` — vrais blocs du sol poussés par la vague, avalés par le tourbillon | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WaterTerrainVfx.java |
| `WeaponWorldVfxManager.java` — durées 104 / 150 / 120, routage `impactWater` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java |
| `WeaponVisualEffectId.java` — ids eau 17 / 18 / 19 | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java |
| `PixelFx.java` — façade particules imposée | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/PixelFx.java |
| `AnimFamily.java` — 8ᵉ famille : `water` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/util/AnimFamily.java |
| `DlbVfxCommand.java` — `/anim water` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/commands/DlbVfxCommand.java |
| `WATER_SIGNATURES.md` — documentation §1 → §14 | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md |
| `semcheck.py` — contrôle sémantique (constante inconnue / usage avant déclaration) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/tools/semcheck.py |
| `water_build.js` — géométrie de l'aperçu, miroir JS du moteur Java | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/tools/water_build.js |
| Miroir MCreator `WaterSignatureVfx.java` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/client/WaterSignatureVfx.java |
| Miroir MCreator `WaterTerrainVfx.java` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/client/WaterTerrainVfx.java |
| Miroir MCreator `GhostBlockMask.java` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/enchantments/GhostBlockMask.java |
| Miroir MCreator `WeaponVfxServerListener.java` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |
| **Dépôt source complet** (ZIP, 741 entrées, 3558036 o) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src-modifies-t301-t374.zip |

```text
DLB_Water_Three_Signatures_src.zip   410511 o   23 entrées   sha256 f4e9929f57d6df71774b2af71c0b0439…
DLB_Eau_v1_preview.html              591202 o   badge v1.4
src-modifies-t301-t374.zip           3560484 o   741 entrées
```

## Vent — refonte complète (v9)

| Fichier | Lien |
|---|---|
| Sources complètes (ZIP, 25 entrées, 421012 o — moteur + rendu + gameplay + commande `/anim` + miroirs) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Wind_Three_Signatures_src.zip |
| Aperçu animé **v8** (HTML autonome, ZIP embarqué rafraîchi au gameplay v9) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Vent_v8_preview.html |
| Aperçu animé v7 (version précédente) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Vent_v7_preview.html |
| Aperçu (chemin historique, même contenu) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Wind_Three_Signatures_preview.html |
| `WindSignatureVfx.java` (moteur voxel du vent) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WindSignatureVfx.java |
| `WindTerrainVfx.java` (**v8** — arrachement progressif du sol, puis montée jusqu'au sommet) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WindTerrainVfx.java |
| `WeaponWorldVfxManager.java` (routage `impactWind`) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java |
| `WeaponVisualEffectId.java` (`TEMPEST_EYE` = 42) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java |
| `DlbVfxCommand.java` (**v7** — `/anim`, épées en bois, glint élémentaire) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/commands/DlbVfxCommand.java |
| `AnimFamily.java` (**v7** — familles `/anim` en anglais, sans doublon) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/util/AnimFamily.java |
| `WeaponVfxServerListener.java` (**v7** — aspiration zonale, priorité vent) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |
| `WIND_SIGNATURES.md` (documentation) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/WIND_SIGNATURES.md |
| Miroirs MCreator (`FIXES_MCREATOR`) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/client/WindSignatureVfx.java |

Trois signatures : **Zéphyr** (#22, 4,0 s, chorégraphie refaite — colonne d'air verticale, lance de cristal détaillée, trident, éclosion, ≈ 1 240 cubes/image) · **Tempête de sable** (#41, 7,0 s, sortie de l'archive Terre, ≈ 990 cubes) · **Œil du cyclone** (#42, 6,0 s, ≈ 956 cubes). Un seul moteur, aucun `DeepElement` ajouté, aucun ordinal déplacé.

**v8 (11/11)** : « les blocs commencent direct dans la tornade » — corrigé. Plus aucun bloc en vol au démarrage : le vortex **mord le sol du centre vers l'extérieur**, chaque motte apparaît **à sa vraie place**, tremble, se décolle de quelques centimètres en petit fragment, puis est aspirée vers l'hélice **au fur et à mesure qu'elle monte** (rayon et angle interpolés depuis sa position réelle), culbute de plus en plus vite et finit au sommet avant d'être éjectée ; une motte qui a fini son vol repart du sol.

**v7 (11/11)** : la tornade happe **toute créature du rayon 4,6** et continue d'aspirer celles qui y entrent (l'emprise au sol ne la contre plus), vitesse tangentielle réelle, **blocs emportés jusqu'au sommet** de la tour, et `/anim` revu : **épées en bois**, **glint à la couleur de l'élément**, **plus de doublons**, **noms en anglais** (`fire wind earth metal light poison nature`).

**v6 (11/11)** : lances qui tombent de +8,6 blocs, entonnoirs étroits au sol/larges en haut (7,9 et 8,9 blocs), tourbillon interne du sable dans le même sens, `WindTerrainVfx` (vrais blocs du sol), slash de vent clair bleu-vert translucide, cyclone à 10,5 blocs / 170 ticks / éjection aléatoire.

**`/anim wind`** : épée **en bois** au glint bleu-vert dont la famille NBT fait tourner Zéphyr → sable → cyclone et déclenche le gameplay correspondant (`/anim` seul liste les sept familles).

**Gameplay (v9)** : Zéphyr cloue la cible 4 s puis la lâche · **tempête de sable = vraie tornade** — toute créature du rayon 4,2 est happée en continu, monte 7,6 blocs sur **son propre** rayon d'orbite (≈ 1 mob sur 3 passe par l'œil), avec rotation, ondulation et durée de vol tirées au sort pour que les mobs **s'entremêlent** au lieu de suivre le même patron, puis est **projetée haut et loin dès qu'elle atteint le sommet** (cécité, lenteur, dégâts toutes les 12 ticks, dégâts de chute) · œil du cyclone = toutes les créatures du rayon 4,6 happées, orbitées, montées de 10,5 blocs puis éjectées dans une direction aléatoire, dégâts de tornade toutes les 10 ticks **+ dégâts de chute vanilla** à l'atterrissage. Sons 100 % vanilla.

Empreintes des archives de cette version :

```text
DLB_Wind_Three_Signatures_src.zip  421 012 o  sha256 8bf066076444e201…
src-modifies-t301-t374.zip       3 536 176 o  sha256 2c06c6de8920bc6e…
```

## Lumière — dernier correctif (V6.5)

| Fichier | Lien |
|---|---|
| Sources complètes (ZIP, 19 entrées) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Light_Three_Signatures_src.zip |
| Aperçu animé (HTML autonome) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Light_Three_Signatures_preview.html |
| `LightSignatureVfx.java` (géométrie) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/LightSignatureVfx.java |
| `WeaponWorldVfxManager.java` (moteur voxel V6.4) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java |
| `LIGHT_SIGNATURES.md` (documentation) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/LIGHT_SIGNATURES.md |
| Archive cumulée `src-modifies-t301-t374.zip` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src-modifies-t301-t374.zip |

## Autres aperçus du dépôt

| Élément | Aperçu | Sources |
|---|---|---|
| Terre | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Earth_Three_Signatures_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Earth_Three_Signatures_src.zip |
| Feu | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Fire_Three_Signatures_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Fire_Three_Signatures_src.zip |
| Gaia | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Gaia_World_Tree_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Gaia_World_Tree_src.zip |
| Métal | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Metal_Three_Signatures_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Metal_Three_Signatures_src.zip |
| Poison | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Poison_Three_Signatures_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Poison_Three_Signatures_src.zip |
| Vent | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Wind_Three_Signatures_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Wind_Three_Signatures_src.zip |
| Eau | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Eau_v1_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Water_Three_Signatures_src.zip |
| Wood | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Wood_Three_Signatures_preview.html | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Wood_Three_Signatures_src.zip |

## Rappel

- Dans l'aperçu intégré, le bouton « Télécharger … · ZIP » fonctionne : l'archive est embarquée dans la page (Blob local, aucune requête réseau). Les aperçus Vent et Lumière exposent en plus une pastille permanente **ZIP SOURCES** en haut à droite, toujours cliquable même quand le panneau défile.
- Si un environnement bloque malgré tout le téléchargement, utilise les liens ci-dessus dans un onglet du navigateur.
- Chaque archive est vérifiable : `sha256sum` du fichier doit correspondre au SHA-256 affiché sous le bouton de l'aperçu.
