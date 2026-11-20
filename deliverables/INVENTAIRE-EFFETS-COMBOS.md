# Inventaire des effets esthetiques des combos (dlbcombos)

Releve automatique des sources, T429. Rien n'est ajoute de memoire : chaque ligne
porte son numero de ligne dans le fichier d'origine.

| Mesure | Valeur |
|---|---|
| Combos declares (`EnchantmentComboRegistry`) | **309** |
| Paliers | UNCOMMON 102, NORMAL 65, RARE 54, EPIC 36, MYTHIC 28, ULTRA_RARE 24 |
| Appels visuels dans `EnchantmentComboHandler` | **116** |
| Appels sonores | **13** |
| Formes de dessin distinctes | **10** |
| Particules vanilla distinctes | **20** |
| Palettes nommees | **12** |

## Deux couches bien separees

**Couche 1 — les auras continues : DEJA en mini-cubes.** `CosmeticPixelAura` dessine
couronnes, halos au sol et ailes avec des cubes de pixels (« AILES EN VRAIE MEMBRANE
DE PIXELS (5 x 3 cubes par aile) »), a chaque frame, cote client. **Rien a reconstruire ici.**

**Couche 2 — les salves d'evenement : en particules vanilla.** Les 116 appels de
`EnchantmentComboHandler` passent par `EnchantmentParticleFX` et emettent des
`ParticleTypes.*`. **C'est exactement la surface a reconstruire en mini-blocs.**

## A. Les 10 formes de dessin — la surface du chantier

| Forme | Appels | Ce qu'elle dessine |
|---|---|---|
| `burstOnEntity` | 44 | Explosion ponctuelle centrée sur le buste d'une entité. |
| `shockwaveRing` | 43 | Anneau d'onde de choc horizontal au sol ou à mi-hauteur. |
| `spawnVisualLightning` | 8 | Vrai eclair vanilla (LightningBolt setVisualOnly) pose au sol : entite, pas particule — a traiter a part. |
| `send` | 7 | PixelFx.send : emission directe d'une particule a une position precise, sans forme. |
| `slashArc` | 4 | Arc d'entaille oriente devant l'attaquant (balayages, lames maitresses). |
| `crownHalo` | 3 | Halo de couronne discret au-dessus de la tête (Roi, Général, Esprit Mystique, Garde Radieuse). |
| `tetherBeam` | 3 | Rayon corde tendue de particules entre deux positions (grappin, ricochet, foudre, sangsue). |
| `cloudCushion` | 2 | T307-CLOUD : Coussin de nuages cotonneux directement sous les pieds du joueur (Marcheur des Nuages triple saut |
| `wingRibbons` | 1 | Rubans d'ailes derrière les épaules (Élytres, Endurance Ailée). |
| `doubleHelix` | 1 | Double hélice ascendante autour d'une entité (vampirisme, bénédictions, chrono). |

Trois formes existent mais ne sont **jamais** appelees par les combos :
`blockDebrisRing`, `meteorSkyfall`, `ankleTrail` (cette derniere sert 35 fois ailleurs).

## B. Les 12 palettes nommees

| Constante | Source | Taille |
|---|---|---|
| `CRIMSON_BLOOD` | `DeepElement.BLOOD.primary` | 1.15 |
| `SCARLET_MIST` | `DeepElement.BLOOD.secondary` | 0.90 |
| `GOLD_DIVINE` | `DeepElement.LIGHT.primary` | 1.05 |
| `SHADOW_VIOLET` | `DeepElement.SHADOW.primary` | 1.10 |
| `TOXIC_GREEN` | `DeepElement.POISON.primary` | 1.00 |
| `TEMPORAL_AMBER` | `DeepElement.LIGHT.secondary` | 0.85 |
| `FROST_CYAN` | `DeepElement.WATER.highlight` | 0.95 |
| `OVERCLOCK_CYAN` | `DeepElement.THUNDER.primary` | 1.05 |
| `STEEL_SILVER` | `DeepElement.METAL.primary` | 0.95 |
| `EARTH_OCHRE` | `DeepElement.EARTH.primary` | 1.10 |
| `CLOUD_WHITE` | `DeepElement.LIGHT.highlight` | 1.05 |
| `HONEY_AMBER` | `DeepElement.LIGHT.secondary` | 1.05 |

## C. Les 20 particules vanilla a remplacer

`CLOUD`, `CRIT`, `DRAGON_BREATH`, `ELECTRIC_SPARK`, `ENCHANTED_HIT`, `END_ROD`, `EXPLOSION`, `FLAME`, `GUST_EMITTER_SMALL`, `HAPPY_VILLAGER`, `LAVA`, `NAUTILUS`, `PORTAL`, `SCULK_SOUL`, `SNOWFLAKE`, `SONIC_BOOM`, `SOUL_FIRE_FLAME`, `SPLASH`, `SWEEP_ATTACK`, `TOTEM_OF_UNDYING`

Reparties ainsi :

| Particule / palette | Emissions |
|---|---|
| (herite) | 11 |
| palette GOLD_DIVINE | 9 |
| vanilla ELECTRIC_SPARK | 8 |
| vanilla CLOUD | 8 |
| vanilla LAVA | 8 |
| palette OVERCLOCK_CYAN | 7 |
| vanilla CRIT | 7 |
| palette CRIMSON_BLOOD | 7 |
| palette EARTH_OCHRE | 6 |
| palette SHADOW_VIOLET | 6 |
| vanilla FLAME | 5 |
| vanilla SCULK_SOUL | 4 |
| vanilla END_ROD | 4 |
| vanilla GUST_EMITTER_SMALL | 3 |
| vanilla NAUTILUS | 3 |
| vanilla SPLASH | 2 |
| vanilla SOUL_FIRE_FLAME | 2 |
| vanilla DRAGON_BREATH | 2 |
| vanilla HAPPY_VILLAGER | 2 |
| vanilla PORTAL | 1 |
| palette FROST_CYAN | 1 |
| vanilla SNOWFLAKE | 1 |
| teinte 0x84CC16 | 1 |
| vanilla ENCHANTED_HIT | 1 |
| palette STEEL_SILVER | 1 |
| palette SCARLET_MIST | 1 |
| vanilla EXPLOSION | 1 |
| teinte 0x4ADE80 | 1 |
| vanilla TOTEM_OF_UNDYING | 1 |
| vanilla SWEEP_ATTACK | 1 |
| vanilla SONIC_BOOM | 1 |

## D. Les auras continues (deja en cubes)

| Drapeau | Ce qui est dessine |
|---|---|
| `F_HALO_CONTINUOUS` | 3 anneaux : couronne 14 points 0xFFF3C8->0xFFC63D, couronne 10 points, socle au sol 10 points |
| `F_COMBO_110_AVATAR` | couronne double 16 + 12 points, 0xFFF6D0 / 0xFFFFFF, la plus lumineuse |
| `F_COMBO_097_ETERNAL` | couronne 12 points 0xFFE9A0 -> 0xFFB020 |
| `F_COMBO_098_SWORD_GOD` | idem Eternal (meme branche) |
| `F_COMBO_101_ARCHANGEL` | ailes en membrane de pixels, 5 x 3 cubes par aile, 0xFFE9A0 / 0xFFFBEA |
| `F_COMBO_106_WARLORD` | anneau au sol 12 points 0xFF7A2E -> 0xFFC46B |
| `F_COMBO_062_PLAGUE` | anneau au sol 10 points 0x6BAF1E -> 0x3E7A0C |
| `F_ENCH_KING` | couronne 10 points 0xFFD34D -> 0xFF9500 |
| `F_ENCH_MYSTIC_MIND` | couronne 10 points 0xC08BFF -> 0x7B3BE8 |
| `F_ENCH_RADIANT_AEGIS` | couronne 10 points 0xFFF7D9 -> 0xBFE8FF |
| `F_ENCH_WINGED_ENDURANCE` | ailes 0xCFE8FF / 0xFFFFFF, uniquement en vol ou hors sol |
| `F_ENCH_DREAD_AURA` | anneau 8 points 0x3A0A66 -> 0x7B2BD6 |

## E. Detail exhaustif : 116 effets, combo par combo

**#093 Avatar du Zéphyr : +30 % vitesse aérienne**

| Ligne | Forme | Couleur |
|---|---|---|
| 348 | `crownHalo` | (herite) |

**#020 Cavalier Royal : sur monture -> Régénération I monture + onde sabots**

| Ligne | Forme | Couleur |
|---|---|---|
| 419 | `shockwaveRing` | palette GOLD_DIVINE |

**#032 Pêcheur Grappin & #057 Pêcheur de Monstres : hameçon planté dans un ennemi !**

| Ligne | Forme | Couleur |
|---|---|---|
| 455 | `burstOnEntity` | vanilla ELECTRIC_SPARK |

**#078 Pêcheur Dimensionnel de l'End : accroupi -> téléportation vers le flotteur !**

| Ligne | Forme | Couleur |
|---|---|---|
| 470 | `burstOnEntity` | vanilla PORTAL |

**#034 Acrobate de Roulade : annule chute + Invisibilité 2 s**

| Ligne | Forme | Couleur |
|---|---|---|
| 484 | `burstOnEntity` | vanilla CLOUD |

**#041 Patineur d'Azote : Glissade cryogénique**

| Ligne | Forme | Couleur |
|---|---|---|
| 492 | `shockwaveRing` | palette FROST_CYAN |
| 499 | `burstOnEntity` | vanilla SNOWFLAKE |

**#056 Tourelle Aérofrein : s'accroupir en plein vol d'Élytres fige en l'air 3 s + révèle les cibles !**

| Ligne | Forme | Couleur |
|---|---|---|
| 537 | `shockwaveRing` | palette OVERCLOCK_CYAN |

**#063 Pont Dimensionnel : propulsion aérienne + immunité projectiles**

| Ligne | Forme | Couleur |
|---|---|---|
| 572 | `cloudCushion` | (herite) |

**#069 Artilleur Inquisiteur : salve d'arbalète -> Vitesse II + 30% remboursement**

| Ligne | Forme | Couleur |
|---|---|---|
| 602 | `shockwaveRing` | vanilla FLAME |

**#072 Bélier aux Ailes de Fer : percuter des ennemis en plein vol d'Élytres !**

| Ligne | Forme | Couleur |
|---|---|---|
| 617 | `burstOnEntity` | vanilla CRIT |

**#080 Colosse Franchisseur : franchir un dénivelé >= 1.45 bloc déclenche une onde sismique !**

| Ligne | Forme | Couleur |
|---|---|---|
| 627 | `shockwaveRing` | palette EARTH_OCHRE |

**#081 Ange à la Plume Céleste : planer dans les airs régénère, purifie et brûle les morts-vivants**

| Ligne | Forme | Couleur |
|---|---|---|
| 646 | `wingRibbons` | palette GOLD_DIVINE |

**#086 Berserker Immortel : sous 35 % PV -> Célérité III frénétique**

| Ligne | Forme | Couleur |
|---|---|---|
| 667 | `doubleHelix` | palette CRIMSON_BLOOD |

**#088 Avatar Infernal : sur magma/lave ou en feu -> Régénération II + Force I + aura incendiaire**

| Ligne | Forme | Couleur |
|---|---|---|
| 680 | `shockwaveRing` | vanilla FLAME |

**#090 Faucon Ouragan : cratère d'ouragan à l'atterrissage d'Élytres !**

| Ligne | Forme | Couleur |
|---|---|---|
| 703 | `cloudCushion` | (herite) |
| 704 | `shockwaveRing` | vanilla GUST_EMITTER_SMALL |

**#137 Catapulte Cinétique : bondir depuis l'accroupissement -> onde répulsive + Vitesse II**

| Ligne | Forme | Couleur |
|---|---|---|
| 869 | `shockwaveRing` | vanilla CLOUD |

**#158 Rebond Tellurique : atterrissage après > 3 blocs -> onde de choc sur 4 blocs (6 dégâts)**

| Ligne | Forme | Couleur |
|---|---|---|
| 915 | `shockwaveRing` | palette EARTH_OCHRE |

**#169 Dompteur d'Orage Ailé : en vol d'Élytres -> répare les ailes et électrocute les ennemis proches**

| Ligne | Forme | Couleur |
|---|---|---|
| 955 | `burstOnEntity` | vanilla ELECTRIC_SPARK |

**#181 Comète à Grappin : atterrissage chargé -> onde météore 5 blocs**

| Ligne | Forme | Couleur |
|---|---|---|
| 987 | `shockwaveRing` | vanilla LAVA |

**#188 Éveil de la Chimère Prismatique : Régénération II périodique + onde électrique en sautant**

| Ligne | Forme | Couleur |
|---|---|---|
| 1027 | `shockwaveRing` | vanilla ELECTRIC_SPARK |

**#206 Avatar de la Chimère Primordiale : feu/noyade annulés + éclair curatif à chaque saut**

| Ligne | Forme | Couleur |
|---|---|---|
| 1103 | `shockwaveRing` | vanilla ELECTRIC_SPARK |

**#093 Avatar du Zéphyr : 25 % de chance de dévier les projectiles !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1445 | `burstOnEntity` | vanilla CLOUD |

**#104 Fantôme de l'Éclipse Totale : 30 % d'esquive fantomatique totale dans l'ombre/nuit !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1454 | `burstOnEntity` | palette SHADOW_VIOLET |

**#087 Vengeance du Warden : impulsion sonique Sculk révélant les ennemis à 16 blocs**

| Ligne | Forme | Couleur |
|---|---|---|
| 1467 | `shockwaveRing` | vanilla SCULK_SOUL |

**#091 Renaissance Solaire : sous 22 % PV (60 s cd) -> Nova Solaire (+6 cœurs)**

| Ligne | Forme | Couleur |
|---|---|---|
| 1491 | `shockwaveRing` | vanilla FLAME |
| 1493 | `crownHalo` | palette GOLD_DIVINE |

**#099 Seigneur de Sang Immortel : empêche un coup fatal toutes les 90 s en drainant la vie à 8 blocs (+8 cœurs) !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1508 | `shockwaveRing` | palette CRIMSON_BLOOD |
| 1513 | `tetherBeam` | palette CRIMSON_BLOOD |

**#173 Avatar de la Salamandre : en feu -> libère une nova ardente lorsque touché**

| Ligne | Forme | Couleur |
|---|---|---|
| 1576 | `shockwaveRing` | vanilla FLAME |

**#181 Comète à Grappin & #186 Archer des Failles Astrales : immunité chute post-tir**

| Ligne | Forme | Couleur |
|---|---|---|
| 1605 | `shockwaveRing` | vanilla LAVA |

**#204 Tireur Céleste Omniscient : zéro dégât de chute + 40 % déviation des projectiles**

| Ligne | Forme | Couleur |
|---|---|---|
| 1669 | `burstOnEntity` | vanilla CLOUD |

**#016 Chasseur Opportuniste : attaque en tombant -> +20 % dégâts + étincelles**

| Ligne | Forme | Couleur |
|---|---|---|
| 1732 | `burstOnEntity` | vanilla CRIT |

**#025 Faucheur Champêtre : attaque à la houe -> balayage 360° + fauche les hautes herbes**

| Ligne | Forme | Couleur |
|---|---|---|
| 1762 | `slashArc` | teinte 0x84CC16 |

**#033 Pyromane des Braises : cible déjà en feu -> +35 % dégâts + déflagration + Vitesse I**

| Ligne | Forme | Couleur |
|---|---|---|
| 1793 | `burstOnEntity` | vanilla LAVA |

**#046 Maître du Wing Chun : à mains nues -> +7 dégâts + rafale d'impact**

| Ligne | Forme | Couleur |
|---|---|---|
| 1810 | `burstOnEntity` | vanilla CRIT |

**#047 Brise-Os du Colosse : sprinter + frapper -> Lenteur IV + Faiblesse II (3 s) + onde écarlate**

| Ligne | Forme | Couleur |
|---|---|---|
| 1817 | `burstOnEntity` | palette CRIMSON_BLOOD |

**#048 Inquisiteur Sacré : contre morts-vivants -> +50 % dégâts sacrés, feu solaire + +1 cœur**

| Ligne | Forme | Couleur |
|---|---|---|
| 1825 | `burstOnEntity` | palette GOLD_DIVINE |

**#049 Bourreau Décapiteur : exécute les monstres ordinaires sous 18 % PV !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1833 | `burstOnEntity` | palette CRIMSON_BLOOD |

**#051 Trident Pogo Marin : frapper au Trident en l'air -> rebond géant + geyser sous la cible !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1844 | `burstOnEntity` | vanilla SPLASH |

**#058 Écho du Duelliste : 0,5 s après chaque coup mêlée, une lame fantôme re-frappe pour 30 % !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1859 | `burstOnEntity` | palette OVERCLOCK_CYAN |

**#059 Embuscade Chirurgicale : attaquer accroupi -> +55 % critique perforant**

| Ligne | Forme | Couleur |
|---|---|---|
| 1868 | `burstOnEntity` | vanilla ENCHANTED_HIT |

**#064 Seigneur Vampire : vol de vie excédentaire converti en Cœurs d'Absorption (jusqu'à +6 cœurs)**

| Ligne | Forme | Couleur |
|---|---|---|
| 1883 | `burstOnEntity` | palette CRIMSON_BLOOD |

**#065 Duelliste Éclair : bond vers la cible (+35 % dégâts)**

| Ligne | Forme | Couleur |
|---|---|---|
| 1897 | `slashArc` | palette OVERCLOCK_CYAN |

**#066 Bastion Miroir : riposte après parade (+75 % dégâts + brise bouclier + étourdit)**

| Ligne | Forme | Couleur |
|---|---|---|
| 1909 | `burstOnEntity` | vanilla CRIT |

**#079 Berserker Sismique : attaque en plein sprint -> fissure tellurique projetant devant vous !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1927 | `shockwaveRing` | palette EARTH_OCHRE |

**#082 Assassin Nocturne : depuis l'Invisibilité -> téléportation dos + x2,10 dégâts + Obscurité**

| Ligne | Forme | Couleur |
|---|---|---|
| 1949 | `burstOnEntity` | palette SHADOW_VIOLET |

**#084 Faucheur de Tempête : toutes les 4 frappes mêlée -> Éclair Céleste en chaîne + Vitesse II**

| Ligne | Forme | Couleur |
|---|---|---|
| 1957 | `spawnVisualLightning` | (herite) |
| 1963 | `tetherBeam` | palette OVERCLOCK_CYAN |

**#085 Titan Tellurique : +35 % onde sismique aux frappes mêlée**

| Ligne | Forme | Couleur |
|---|---|---|
| 1972 | `shockwaveRing` | palette EARTH_OCHRE |

**#089 Seigneur des Abysses : +35 % Marée Pressurisée au Trident même à sec**

| Ligne | Forme | Couleur |
|---|---|---|
| 1986 | `burstOnEntity` | vanilla SPLASH |

**#094 Pacte Maudit de Verre : x2,25 dégâts abyssaux purs + vol de vie 15 % !**

| Ligne | Forme | Couleur |
|---|---|---|
| 1993 | `burstOnEntity` | palette SHADOW_VIOLET |

**#098 Exterminateur de Titans Absolu : +45 % dégâts contre Boss / monstres > 40 PV !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2027 | `burstOnEntity` | vanilla CRIT |

**#100 Archange du Jugement Solaire : rayons célestes aveuglants sur frappe mêlée !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2032 | `slashArc` | palette GOLD_DIVINE |

**#101 Poséidon du Cataclysme : Trident -> +50 % dégâts + foudre & maelström !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2047 | `spawnVisualLightning` | (herite) |
| 2048 | `shockwaveRing` | vanilla NAUTILUS |

**#105 Seigneur de Guerre de l'Apocalypse : inflige Wither II + Brûlure d'Âme à chaque coup !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2072 | `burstOnEntity` | vanilla SOUL_FIRE_FLAME |

**#106 Maître du Temps Immuable : fige les ennemis critiques dans le temps (Lenteur V) !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2078 | `burstOnEntity` | palette OVERCLOCK_CYAN |

**#109 Avatar Suprême du Deep Lucky : +30 % dégâts globaux !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2114 | `burstOnEntity` | vanilla END_ROD |

**#130 Lame à Choc Thermique : cible ralentie/gelée ET en feu -> +40 % dégâts**

| Ligne | Forme | Couleur |
|---|---|---|
| 2150 | `burstOnEntity` | vanilla CLOUD |

**#150 Faucheuse de Flammes Bleues : cercle de feu des âmes (3,5 blocs) + Wither II + 5 dégâts magiques**

| Ligne | Forme | Couleur |
|---|---|---|
| 2200 | `shockwaveRing` | vanilla SOUL_FIRE_FLAME |

**#155 Hache Fend-Orage : attaque à la hache -> +35 % dégâts électriques + répare 2 durabilité**

| Ligne | Forme | Couleur |
|---|---|---|
| 2238 | `burstOnEntity` | vanilla ELECTRIC_SPARK |

**#157 Plongeon du Rapace : attaque en chute -> +45 % dégâts + bourrasque**

| Ligne | Forme | Couleur |
|---|---|---|
| 2245 | `shockwaveRing` | vanilla GUST_EMITTER_SMALL |

**#162 Brise-Pavois Nordique : après blocage au bouclier, prochaine hache -> +45 % + étourdissement**

| Ligne | Forme | Couleur |
|---|---|---|
| 2262 | `burstOnEntity` | vanilla CRIT |

**#167 Harpon de Vapeur Ardente : coup au Trident -> explosion de vapeur +6 dégâts de feu**

| Ligne | Forme | Couleur |
|---|---|---|
| 2280 | `shockwaveRing` | vanilla CLOUD |

**#170 Katana Surcadencé : frappe critique/chute -> foudre en chaîne sur 4 ennemis (+7) + Célérité II**

| Ligne | Forme | Couleur |
|---|---|---|
| 2306 | `burstOnEntity` | vanilla ELECTRIC_SPARK |

**#172 Paladin de l'Aube Pure : +65 % + soin contre morts-vivants, +35 % contre vivants**

| Ligne | Forme | Couleur |
|---|---|---|
| 2322 | `burstOnEntity` | palette GOLD_DIVINE |

**#176 Mirage du Vent Vif : riposte chargée -> +45 % dégâts**

| Ligne | Forme | Couleur |
|---|---|---|
| 2343 | `burstOnEntity` | vanilla CLOUD |

**#177 Monolithe Inébranlable : coup de hache -> onde sismique projetant les ennemis en l'air**

| Ligne | Forme | Couleur |
|---|---|---|
| 2349 | `shockwaveRing` | palette EARTH_OCHRE |

**#179 Tempête de Mille Entailles : toutes les 4 frappes -> entaille circulaire (65 % zone) + soin 1,5 cœur**

| Ligne | Forme | Couleur |
|---|---|---|
| 2367 | `slashArc` | palette CRIMSON_BLOOD |

**#189 Bourreau des Colosses : hache -> +45 % dégâts + onde tellurique 4 blocs**

| Ligne | Forme | Couleur |
|---|---|---|
| 2410 | `shockwaveRing` | palette EARTH_OCHRE |

**#200 Dieu-Bourreau du Cataclysme : hache -> +50 % dégâts + séisme volcanique**

| Ligne | Forme | Couleur |
|---|---|---|
| 2460 | `shockwaveRing` | vanilla LAVA |

**#209 Élu Astral de la Chance Infinie : +35 % dégâts critiques stellaires + nova curatrice**

| Ligne | Forme | Couleur |
|---|---|---|
| 2697 | `shockwaveRing` | vanilla END_ROD |
| 2710 | `spawnVisualLightning` | (herite) |

**#045 Citadelle Vivante : onde répulsive 4 blocs + 2 cœurs d'Absorption + répare bouclier**

| Ligne | Forme | Couleur |
|---|---|---|
| 2753 | `shockwaveRing` | palette STEEL_SILVER |

**#066 Bastion Miroir : charge l'arme 4 s pour la contre-attaque Brise-Garde**

| Ligne | Forme | Couleur |
|---|---|---|
| 2771 | `burstOnEntity` | palette OVERCLOCK_CYAN |

**#098 Exterminateur de Titans Absolu : onde météore sur blocage !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2777 | `shockwaveRing` | vanilla LAVA |

**#191 Égide Solaire Inviolable : renvoie 150 % des dégâts bloqués, soigne 2 cœurs et Résistance III (4 s)**

| Ligne | Forme | Couleur |
|---|---|---|
| 2876 | `burstOnEntity` | palette GOLD_DIVINE |

**#073 Faucheuse d'Âmes : restaure +2 cœurs + +6 XP dans un tourbillon de sculk**

| Ligne | Forme | Couleur |
|---|---|---|
| 2930 | `shockwaveRing` | vanilla SCULK_SOUL |

**#082 Assassin Nocturne : réactive l'Invisibilité sur kill**

| Ligne | Forme | Couleur |
|---|---|---|
| 2937 | `burstOnEntity` | palette SHADOW_VIOLET |

**#086 Berserker Immortel : restaure 25 % des PV manquants**

| Ligne | Forme | Couleur |
|---|---|---|
| 2945 | `burstOnEntity` | palette SCARLET_MIST |

**#192 Archonte de la Nécropole : élimination à la houe -> déflagration nécrotique + soin 2,5 cœurs**

| Ligne | Forme | Couleur |
|---|---|---|
| 2998 | `shockwaveRing` | vanilla SCULK_SOUL |

**#083 Empereur de Légion : bénit les soldats invoqués**

| Ligne | Forme | Couleur |
|---|---|---|
| 3057 | `crownHalo` | palette GOLD_DIVINE |

**#146 Harponneur Foudroyant : lancer / tracter la ligne -> Vitesse II + arc électrique proche**

| Ligne | Forme | Couleur |
|---|---|---|
| 3079 | `burstOnEntity` | vanilla ELECTRIC_SPARK |

**#093 Avatar du Zéphyr : bourrasque perforante à chaque lancer de Trident !**

| Ligne | Forme | Couleur |
|---|---|---|
| 3088 | `shockwaveRing` | vanilla GUST_EMITTER_SMALL |

**#038 Arbalétrier à Dispersion : tir à bout portant repousse tous les ennemis devant soi !**

| Ligne | Forme | Couleur |
|---|---|---|
| 3198 | `burstOnEntity` | vanilla END_ROD |

**#075 / #125 / #153 / #167 : Impacts de Trident**

| Ligne | Forme | Couleur |
|---|---|---|
| 3238 | `shockwaveRing` | vanilla EXPLOSION |
| 3253 | `shockwaveRing` | vanilla NAUTILUS |
| 3267 | `shockwaveRing` | vanilla CLOUD |

**#008 Archer des Ronces : flaque toxique ralentissante à l'impact**

| Ligne | Forme | Couleur |
|---|---|---|
| 3284 | `shockwaveRing` | teinte 0x4ADE80 |

**#026 Archer Rebond : impact électrisé**

| Ligne | Forme | Couleur |
|---|---|---|
| 3306 | `burstOnEntity` | vanilla ELECTRIC_SPARK |

**#052 Archer Gravité : aspire les ennemis en un point puis explose en onde d'âmes !**

| Ligne | Forme | Couleur |
|---|---|---|
| 3322 | `shockwaveRing` | vanilla SCULK_SOUL |

**#053 Archer à Retardement : marque + double détonation critique à l'expiration**

| Ligne | Forme | Couleur |
|---|---|---|
| 3337 | `burstOnEntity` | vanilla CRIT |

**#069 Artilleur Inquisiteur : braise soufrée explosive**

| Ligne | Forme | Couleur |
|---|---|---|
| 3359 | `burstOnEntity` | vanilla LAVA |

**#070 Spectre Dimensionnel : leurre aveuglant + Invisibilité & Force I**

| Ligne | Forme | Couleur |
|---|---|---|
| 3369 | `shockwaveRing` | palette SHADOW_VIOLET |

**#074 Archer d'Orage en Chaîne : rebond électrique sur 3 cibles + Vitesse III courte**

| Ligne | Forme | Couleur |
|---|---|---|
| 3389 | `tetherBeam` | palette OVERCLOCK_CYAN |

**#092 Pluie Cataclysmique : 2 météorites secondaires autour de l'impact**

| Ligne | Forme | Couleur |
|---|---|---|
| 3404 | `shockwaveRing` | vanilla LAVA |

**#095 Dragonnier de l'End : zone de souffle draconique qui soigne le tireur et ronge les ennemis !**

| Ligne | Forme | Couleur |
|---|---|---|
| 3422 | `shockwaveRing` | vanilla DRAGON_BREATH |

**#178 Bombardier du Chaos : brume alchimique 4 blocs (Wither II, Faiblesse II, Lenteur II — 5 s)**

| Ligne | Forme | Couleur |
|---|---|---|
| 3482 | `shockwaveRing` | vanilla DRAGON_BREATH |

**#186 Archer des Failles Astrales : faille gravitationnelle (5 dégâts magiques) + 6 s anti-chute**

| Ligne | Forme | Couleur |
|---|---|---|
| 3502 | `shockwaveRing` | palette SHADOW_VIOLET |

**#197 Canonnier d'Apocalypse Solaire : impact explosif ardent**

| Ligne | Forme | Couleur |
|---|---|---|
| 3583 | `shockwaveRing` | vanilla FLAME |

**#103 Forge du Créateur Éternel : préserve 80 % durabilité outil + chance Diamant/Émeraude !**

| Ligne | Forme | Couleur |
|---|---|---|
| 3748 | `burstOnEntity` | vanilla TOTEM_OF_UNDYING |

**#071 Amiral Hauturier : Absorption + Grâce du Dauphin + 50 % double butin**

| Ligne | Forme | Couleur |
|---|---|---|
| 3979 | `burstOnEntity` | vanilla NAUTILUS |

**#097 Dieu de la Lame Suprême : chaque 3e frappe fend l'espace sur 7 blocs (60 % dégâts) !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2012 | `send` | vanilla SWEEP_ATTACK |
| 2013 | `send` | palette GOLD_DIVINE |

**#107 Poing Divin du Chauve Suprême (SAITAMA — 4 pièces + MAINS VIDES !)**

| Ligne | Forme | Couleur |
|---|---|---|
| 2086 | `send` | vanilla SONIC_BOOM |

**#005 Mineur Étincelle : miner de la roche dans l'obscurité -> Vision Nocturne + étincelles**

| Ligne | Forme | Couleur |
|---|---|---|
| 3617 | `send` | vanilla LAVA |

**#042 Cœur de Forge Naine : révèle les filons cachés à 6 blocs + XP + nourriture**

| Ligne | Forme | Couleur |
|---|---|---|
| 3679 | `send` | vanilla END_ROD |

**#043 Bûcheron Sylvestre : Célérité II + replante pousse + attire le bois coupé**

| Ligne | Forme | Couleur |
|---|---|---|
| 3696 | `send` | vanilla HAPPY_VILLAGER |

**#044 Moissonneur d'Abondance : nourrit + accélère cultures 3x3 + Carotte/Pomme Dorée rare**

| Ligne | Forme | Couleur |
|---|---|---|
| 3719 | `send` | vanilla HAPPY_VILLAGER |

**#108 Inquisiteur à l'Arbalète de Soufre : carreaux +45 % dégâts + foudre infernale !**

| Ligne | Forme | Couleur |
|---|---|---|
| 2106 | `spawnVisualLightning` | (herite) |

**#185 Cataphractaire Foudroyant : sur monture -> foudre + +45 % dégâts**

| Ligne | Forme | Couleur |
|---|---|---|
| 2391 | `spawnVisualLightning` | (herite) |

**#195 Archonte des Marées Célestes : Trident -> foudre + +40 % dégâts + soin 1,5 cœur**

| Ligne | Forme | Couleur |
|---|---|---|
| 2436 | `spawnVisualLightning` | (herite) |

**#102 Valkyrie Supersonique : flèches tirées en vol d'Élytres abattent la foudre !**

| Ligne | Forme | Couleur |
|---|---|---|
| 3434 | `spawnVisualLightning` | (herite) |

**#190 Tempête d'Étoiles Foudroyantes : foudroie 4,5 blocs (+8 dégâts) + Vitesse III (5 s)**

| Ligne | Forme | Couleur |
|---|---|---|
| 3514 | `spawnVisualLightning` | (herite) |
