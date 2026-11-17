# Liens de téléchargement — toujours à jour

Ces liens pointent sur la **branche de travail** (`arena/605e67fb-dlbv1-21-1neoforge`), pas sur un commit figé : ils servent donc **toujours la dernière version poussée**. Aucune mise à jour de ce fichier n'est nécessaire quand le code change.

Forme utilisée : `https://github.com/<dépôt>/raw/refs/heads/<branche>/<chemin>` — GitHub redirige vers le fichier brut exact.

> **Si le navigateur sert une vieille version** (le CDN de GitHub garde les fichiers bruts en cache quelques minutes), remplacer `refs/heads/arena/605e67fb-dlbv1-21-1neoforge` par le SHA du commit — par exemple `raw/fa83a2f/…` pour la v1.3 — ou ajouter `?v=2` à la fin de l'adresse.

## Sang — cinq signatures · 17 novembre 2026 ⟵ **LOT COURANT**

- **Aperçu (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Blood_Five_Signatures_preview.html
- **Sources (ZIP)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Blood_Five_Signatures_src.zip

> **T387 — 17/11 · les dégâts du Sang s'arrêtent avec l'animation, et ils se voient.**
> Trois retours de jeu corrigés. **(1)** Les hémorragies débordaient de 73 à 143 ticks après
> la dernière image, et la flaque de sang **repoussait** la fin de saignée de quatre secondes
> toutes les secondes — la boucle ne se fermait jamais. Chaque signature a désormais une
> **échéance absolue** : animation + 1,0 s, pour les cinq. Le total de dégâts est conservé,
> concentré au lieu d'être étalé. **(2)** Les huit points de dégâts passent par `bloodHurt` :
> plus aucun coup de la famille sans **anneau de gouttes** autour du corps, en poussière
> rouge sang et non en `FALLING_DRIPSTONE_LAVA` orange. **(3)** La pulsation du glint ne se
> déclenchait **jamais** sur les épées de `/anim` (aucun enchantement réel à lire) ; elle lit
> maintenant `DlbAnimFamily`. Le critère de séparation devient le **canal vert** : feu
> (255,130,16)→(255,203,94), toujours ≥ 130 ; sang (124,0,18)→(200,8,41), toujours ≤ 8.
> Aperçu : `T387_glint_feu_vs_sang.png`. ZIP **1 075 559 o / 60 entrées**, page **1 519 767 o**.
> Suffixez les liens de **`?v=387`**.

> **T386 — le Carnage devient une VOLEE DE LAMES, et le glint du Feu cesse d'etre
> celui du Sang.** (1) **CARNAGE BERSERK (animation 5)** : les onze arcs tires d'un
> hachage sont remplaces par **onze lames**. Chacune se forge **dans le ciel** a 3,5-5,2
> blocs, tombe en **25 ticks**, **retrecit** en convergeant et entre dans le **torse**
> (55 % de la hitbox) ; l'entaille s'ouvre a l'impact et les perles giclent a
> contre-sens. Silhouette 1:14, corps sombre et **fil clair sorti du corps**, epaisseur
> laterale suivant la largeur pour rester lisible de profil. Quatre lames dans le ciel
> au plus fort de la salve. **Le degat n'a pas bouge d'un tick** : `at` designe
> desormais l'impact, la lame nait avant. Chaque lame **siffle** neuf ticks avant de
> toucher. (2) **Glint** : le Feu prend une base **orange foncee rougeatre**
> (192, 65, 10) avec une **breve** excursion vers le rouge (224, 51, 15) toutes les
> 4,3 s ; le Sang une base **rouge foncee** (138, 2, 8) avec de **courts** passages vers
> un rouge plus intense (186, 0, 25) toutes les 5,7 s. Rythmes premiers entre eux, phase
> decalee par item : les deux familles ne se confondent plus, ni au repos ni au sommet.

> **T385 — quatre défauts vus en jeu.** (1) **Le conduit sortait par l'arrière du
> mob** : les signatures à lien dessinent le bout porteur à `f` négatif, donc la
> direction doit aller du porteur vers la victime — elle allait dans l'autre sens, le
> tuyau partait à 180° du joueur. (2) **Chaque tic appliquait le recul vanilla** : la
> cible dérivait toujours dans le même sens et sortait de son animation en glissant ;
> on lui rend sa vitesse après le dégât, les reculs voulus passent intacts. (3)
> **Quatre signatures sur cinq ne verrouillaient pas la victime** : emprises de 44 /
> 150 / 118 / 112 ticks, chacune lâchant avant son temps fort. (4) **L'hémorragie
> battait à dix ticks pour les cinq** — désormais 14 / 6 / 10 / 8 selon la signature.

> **T384 — correctif de compilation.** `SoundEvents.GENERIC_DRINK.value()` ne compile
> pas en 1.21.1 : ce champ y est un `SoundEvent` **nu**, pas un `Holder` (il ne le
> devient qu'en 1.21.4). Deux lignes corrigées. Les 472 usages de `SoundEvents` du mod
> ont été audités contre la table réelle de la 1.21.1 (117 holders, 1 486 nus),
> désormais versée au dépôt avec un contrôle `semcheck` dédié. **Le build passe.**

> **T383 — l'échelle du mob était appliquée deux fois.** La mise à l'échelle par la
> hitbox existait bien : `putPosColor`, point de sortie unique de toute la géométrie,
> applique √(l²·h / 0,6²·1,8) autour du point d'impact — 0,42 pour un poulet, 1,00 pour
> un joueur, 3,59 pour un ravageur. Mais **trois grandeurs ne sont pas de la géométrie
> locale**, ce sont des distances déjà mesurées dans le monde, et elles étaient
> multipliées une seconde fois : la **hauteur du sol** sous l'ancre (la flaque de sang
> descendait trois mètres **sous** le sol sur un ravageur), la **longueur du conduit**
> victime → porteur (il dépassait le porteur de douze blocs, et s'arrêtait à 5,45 au lieu
> de 7,00 sur un poulet) et la **position des relais** de la chaîne de foudre (l'éclair
> frappait derrière chacune de ses victimes). `unscaleWorld` divise désormais à l'entrée
> ce que la sortie multiplie — mesuré à **−6,98 pour une cible à −7,00**, à 0,78 comme à
> 1,75 d'échelle. Corrige le **Sang** et l'**Ombre** d'un coup. La bande propre au sang
> est resserrée à `échelle^0,55` bornée à **[0,78 ; 1,75]** : les cinq signatures sont des
> assemblages (échafaud, calice, cœur), à 4,0 le cœur aurait fait sept blocs de haut.

> **T382 — le cœur de la Transfusion**, modèle anatomique : ventricules fuselés, sillon
> interventriculaire, deux auricules enroulées, crosse aortique couchée dans le plan de
> la liaison. **T381 — le Siphon v2** : le conduit **monte puis redescend**, ancres
> reconnaissables aux deux bouts, volume visible **dans** le calice.

## Ombre — trois signatures · 4 novembre 2026

> **Version 3.2 — bande son complète et base de dégâts corrigée.**
>
> **Les dégâts ignoraient les enchantements.** Toutes les familles calculaient leur base
> avec l'attribut `ATTACK_DAMAGE` seul. Cet attribut contient l'arme et la Force, mais
> **pas** Tranchant, Châtiment ni Fléau des arthropodes : depuis la 1.21 ces enchantements
> ne sont plus des modificateurs d'attribut, ils sont appliqués au moment du coup. Une épée
> en diamant **Tranchant V** lançait donc des signatures calibrées sur **8** dégâts alors
> que son coup normal en fait **11** — enchanter son arme n'avait aucun effet sur les
> animations. La base est maintenant copiée exactement depuis le coup qui a lancé
> l'animation, enchantements compris, et évaluée **contre le corps touché** (Châtiment et
> Fléau sont conditionnels). Correction appliquée aux **dix familles**, pas seulement à
> l'ombre.
>
> **Bande son, même procédé que la foudre.** Quatre pièces : fondu d'entrée, accents posés
> sur les jalons exacts de l'animation, dispersion aléatoire, fondu de sortie décalé dans
> l'espace. Une différence voulue : la foudre monte en hauteur tonale, **l'ombre descend**.
> Les trois signatures passent de 9/6/17 à **30/28/32 instants sonores**, sans aucun trou
> de plus de 20 ticks — le pire était l'éclipse, **72 ticks** où l'image montrait cendre,
> braises et sceau pendant que le son retombait à zéro. Tous les sons ont été vérifiés
> contre le registre `sound_event` de la 1.21.1 : 97 constantes, existence et `.value()`
> contrôlés un par un.

> **Version 3.1 — l'éclipse devient une prison.** Trois écarts corrigés d'un coup : les
> dégâts étaient **pulsés** (cinq battements) et non constants, les **joueurs étaient
> tous épargnés** — y compris les adversaires — tandis que le **bétail et les villageois**
> étaient frappés, et **rien n'empêchait de sortir** du dôme. Désormais une **brûlure de
> 12 % de la base toutes les 10 ticks** s'applique à tout le monde pareil, par-dessus les
> battements existants ; une **paroi sphérique** renvoie à l'intérieur tout ce qui
> l'atteint, par les côtés comme par le dessus, du tick 48 (pose du voile) au tick 178
> (levée). Sont pris : les **hostiles**, les **neutres qui ont une cible** et les **autres
> joueurs**. Sont épargnés : le **porteur** — seul à franchir la paroi dans les deux sens —
> le **bétail**, les **villageois** et les **compagnons apprivoisés**.

> **Version 3 — les quatre corrections demandées.**
>
> **La main bouge en sortant de terre.** Les doigts restaient droits pendant toute la
> sortie puis se fermaient d'un bloc : une main de mannequin posée sur un rail. Chaque
> doigt a maintenant son **pli de repos** (0,19 / 0,13 / 0,21 / 0,33 rad), sort avec son
> **propre retard** — l'auriculaire a presque un demi-temps sur l'index — et se **griffe**
> en demi-sinus pendant l'émergence, avec un tremblement qui s'éteint à mesure que la
> prise se ferme. Le pouce fait levier à part. Côté serveur la prise **bloque** le mob,
> le **traîne** le long du couloir pendant trente ticks (la main et le mob avancent sur
> la *même* formule, donc jamais de décalage) et inflige des **dégâts toutes les six
> ticks** pendant le trajet, au lieu d'une seule gifle à l'arrivée.
>
> **L'éclipse fait face au joueur et a du volume.** Le disque était dessiné dans un plan
> fixe : dès qu'on contournait le mob il devenait **un trait**. Il est maintenant en
> panneau-réclame — reconstruit chaque image dans le repère de la caméra, donc **rond vu
> de partout** (mesuré : rapport largeur/hauteur 0,994 sous sept azimuts). Épaissir le
> disque n'aurait rien réglé, il serait resté orienté ; et bomber du noir ne se voit pas.
> Le relief vient donc d'ailleurs : **quatre couronnes de braises** étagées en profondeur
> sur la calotte, un **anneau orbital incliné** de 54 cubes qui passe **devant puis
> derrière** le disque — sa moitié arrière est supprimée là où la silhouette la masque,
> ce qui donne une occlusion exacte et gratuite — des filaments de couronne et des
> protubérances qui **sortent du plan**, et six éclats de l'anneau de diamant projetés
> vers le joueur.
>
> **La faille s'ouvre deux blocs derrière le mob.** Elle s'ouvrait **dans** la cible, qui
> se trouvait donc à l'origine du cône de souffle et que le test d'angle épargnait : le
> souffle passait à côté de sa propre victime. Elle recule de deux blocs du côté opposé
> au porteur et **souffle vers lui**, donc à travers le mob — effet secondaire voulu, le
> crâne fait enfin face au joueur au lieu de lui présenter son occiput. La position est
> **recalculée en fonction de la portée réelle** : le serveur balaie les mobs du cône,
> place la bouche derrière le **plus éloigné**, et si le groupe est plus long que la
> portée il la ramène devant le **plus proche** plutôt que de souffler dans le vide.
> Toutes les victimes du cône sont **verrouillées** pendant soixante-dix ticks.

> **Version 2 — les trois corrections demandées.** La **main** faisait demi-tour (elle
> présentait le dos à sa proie) et a grandi de 1,45 à **2,45** — sommet mesuré à 2,42
> blocs. Le crâne de Wither est devenu un **crâne de démon** de ~210 pièces : arcades
> obliques, museau, mâchoire à quatre couches de gorge, cornes annelées. L'**éclipse** est
> montée de 10,5 à **23 blocs au-dessus du mob**, le **ciel passe à minuit** pendant les
> dix secondes puis revient à l'heure qu'il faisait, et le disque est relié au dôme par
> neuf rayons crépusculaires et une colonne en spirale.

> **Dixième famille refondue.** `SPECTRAL_REACH`, `WITHER_SHADOW_RIFT` et `ECLIPSE_VEIL`
> passaient de **14 à 18 ticks** — moins d'une seconde — avec un rendu générique de
> tentacules et aucun gameplay. Elles tiennent maintenant **118, 160 et 200 ticks**, avec un
> moteur voxel dédié, un gameplay serveur complet et une ressource partagée.
>
> **Le problème propre à cet élément.** Les neuf familles précédentes se voient parce
> qu'elles **émettent** de la lumière. L'ombre, non : du noir sur un monde déjà sombre ne
> produit *aucune image*, et l'ancien rendu tombait exactement là-dedans — ses bras noirs
> étaient invisibles la nuit et dans les grottes, c'est-à-dire là où on joue. Trois règles
> en découlent : **jamais de noir sans liseré** (c'est le liseré qu'on voit, le noir n'est
> que le trou qu'il entoure), **la silhouette avant la matière**, et le noir n'est pas
> `0x000000` mais `0x0B0814` — le noir pur se confond avec le ciel de nuit.
>
> **Les trois chorégraphies.**
> Ⅰ **Allonge Spectrale** — l'ombre du porteur se détache du sol et court sur neuf blocs ;
> une échine la remonte, puis une **main articulée** — à plat, puis décollée par rotation
> autour du poignet — se referme et **ramène** la cible. Rien n'y est radial : le sujet est
> l'allonge.
> Ⅱ **Faille du Flétrisseur** — une fente s'ouvre en amande, un **crâne de 96 pièces** en
> sort, souffle la flétrissure, puis la faille **inspire** : tout est tiré vers elle.
> Ⅲ **Voile d'Éclipse** — un soleil noir monte, sa couronne se déploie, un **dôme de sept
> blocs** descend sur l'arène. L'anneau de diamant annonce la fin ; trois griffures déchirent
> le dôme.
>
> **AoE *et* cible seule, pour chacune.** Allonge : couloir de 9×2,7 blocs / cible clouée
> 72 ticks puis tractée, trois serrages. Faille : aspiration de 8 blocs en quatre secousses /
> Flétrissure **II** double durée. Éclipse : cinq battements sous le dôme / les trois
> griffures **convergent** sur la cible unique (×3).
>
> **Buffs et debuffs.** Liant des trois : la **marque d'ombre**, qui fonctionne à l'envers de
> la charge statique de la foudre — elle majore la **durée des afflictions** (+35 % par pile)
> et non les dégâts, parce que l'ombre est l'élément des malédictions. À saturation elle
> s'effondre. Côté porteur : Absorption sur l'Allonge, soin et Régénération sur la Faille,
> Vision nocturne et Force tant qu'il reste **sous** le dôme de l'Éclipse.
>
> **Deux décalages trouvés en relisant les horloges côte à côte.** L'emprise de l'Allonge
> durait 40 ticks (valeur copiée de la foudre) alors que la traction a lieu au tick 70 : la
> victime avait dix-sept ticks pour sortir du cadre pendant que la main se refermait sur du
> vide → **72 ticks**. Et les battements de l'Éclipse commençaient au tick 54, alors que
> l'ourlet du dôme ne touche le sol qu'au tick 69 : **la zone frappait avant d'exister**
> → premier battement au tick 68.
>
> **Deux bogues attrapés avant livraison.** (1) Le paramètre `scale` de la main était reçu
> puis **jamais appliqué** : la main sortait à l'échelle 1 quoi qu'on passe, un réglage mort.
> (2) Quatre sons pressentis (`soul_escape`, `phantom.bite`, `sculk.clicking`, `wither.shoot`)
> **n'existent pas** en 1.21 — chaque constante a été vérifiée par `grep` contre les usages
> réels du dépôt avant d'être retenue.
>
> **Tests :** `/anim shadow` (ou `/anim ombre`), puis frapper un mob entouré d'autres.
> Visuel seul : `/dlbvfx play 3` · `/dlbvfx play 24` · `/dlbvfx play 33`.

| Fichier | Lien |
|---|---|
| Aperçu animé (HTML) | [DLB_Shadow_Three_Signatures_preview.html](https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Shadow_Three_Signatures_preview.html) |
| Sources (ZIP, 46 entrées) | [DLB_Shadow_Three_Signatures_src.zip](https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Shadow_Three_Signatures_src.zip) |
| Le fichier **nouveau** | [ShadowSignatureVfx.java](https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/ShadowSignatureVfx.java) |
| Journal technique (§ 37) | [WATER_SIGNATURES.md](https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md) |

> **⚠ Le `.html` s'ouvre en texte brut** depuis `/raw/` : clic droit → « Enregistrer
> sous », puis ouvrir le fichier local. Le ZIP est aussi **embarqué dans la page**, donc le
> bouton de téléchargement fonctionne même hors ligne.

---

## Foudre v12 — 4 novembre 2026

> **Trois retours de combat, trois corrections.** Le recul du marteau part désormais : la v11
> a débloqué la fonction, la v12 la règle et traite les deux autres signatures.
>
> **1. Le nuage de l'orage était « visé sur le joueur ».** Il ne l'était pas : le gameplay est
> ancré sur la cible, et les deux balayages de victimes excluent explicitement les joueurs.
> Mais le nuage fait **3,40 blocs de rayon** et flotte à huit de haut, et en mêlée on se tient
> à deux ou trois blocs de ce qu'on frappe : on est **sous sa jupe**. La mitraille tombe entre
> 0,55 et 2,60 blocs du centre, donc certains traits atterrissent aux pieds du joueur. De
> l'intérieur, un nuage centré sur le mob et un nuage centré sur soi, c'est la même image.
> Même cause que le marteau, même remède : **le porteur recule de dix blocs** à l'appel.
>
> **2. L'éclair qui tombe de très haut ne verrouillait pas sa cible.** Le point de chute est
> figé à l'instant du coup — délibérément, un éclair ne poursuit pas sa proie. Mais le mob
> continuait de marcher pendant les vingt ticks de descente : un pas de côté et le trait
> tombait à côté. La cible est maintenant **clouée sur place quarante ticks**, de
> l'ionisation jusqu'au premier bond de la chaîne. Sans étourdissement complet : elle est
> immobilisée, pas désactivée — elle se défend encore, elle ne peut plus sortir de la mire.
>
> **3. Le recul du marteau : cinq blocs de plus, et deux fois plus haut.** `HAMMER_CLEARANCE`
> passe de 6,0 à **11,0**, et la vitesse verticale est doublée, bornes comprises
> (`0,42–1,05` → `0,84–1,50`). Doubler la vitesse ne monte pas deux fois plus haut mais
> **quatre** fois : la hauteur varie comme le carré de la vitesse.
>
> | Terrain derrière | v verticale | vol | portée | apogée |
> |---|---|---|---|---|
> | sol plat | 0,858 | 21 t | 11,00 bl | 4,60 bl |
> | talus de 2 | 1,420 | 35 t | 11,00 bl | 12,60 bl |
> | mur plein | 1,500 | 37 t | 11,00 bl | 14,06 bl |
>
> La portée tombe à onze blocs dans les trois cas : c'est la série géométrique qui s'en
> charge, recalculée pour chaque durée de vol.
>
> **Et la conséquence à ne pas manquer** : la garantie anti-dégâts de chute bouclait sur la
> **montée** seule. Avec un apogée quatre fois plus haut elle s'arrêtait en plein ciel et vous
> rendait toute la descente. Elle couvre maintenant l'aller-retour complet.
>
> Démarche complète au **§36** de `WATER_SIGNATURES.md`.

| Fichier | Lien |
|---|---|
| **⬇ APERÇU ANIMÉ FOUDRE** (HTML autonome — le ZIP est *dedans*) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Thunder_Three_Signatures_preview.html |
| **⬇ Sources complètes** (ZIP, 42 entrées, 694 211 o) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Thunder_Three_Signatures_src.zip |
| `WeaponVfxServerListener.java` — **les trois correctifs** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |
| `WATER_SIGNATURES.md` — **§36, cadrage / verrou / dose** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md |

**SHA-256 du ZIP** : `1ee8d4aa08bb566d4f8935dba4ee61c2b31431cbc4d2d2406e1a5c212b54d801`

**Un seul fichier Java change depuis la v11** : `WeaponVfxServerListener.java` (plus la doc).

---

## Foudre v11 — 4 novembre 2026

> **Un seul correctif, mais il débloque la fonction entière : le recul du porteur part enfin.**
>
> « Je ne vois pas mon recul avant l'animation marteau — je suis donc encore dedans. » Le code
> de la v10 était pourtant bien dans le jeu compilé, et le calcul balistique était juste. Ce
> qui manquait n'était pas le mouvement : c'était **de le dire au client**.
>
> Pour un **mob**, le serveur fait autorité : on change sa vitesse, il bouge. Pour un **joueur**,
> c'est son propre client qui simule son déplacement ; une vitesse posée côté serveur n'est
> qu'un avis, écrasé au paquet de position suivant. Le drapeau prévu pour cela, `hurtMarked`,
> ne suffisait pas ici : il demande l'envoi d'un paquet de vélocité en **fin de tick**, construit
> à partir de la vitesse telle qu'elle est à ce moment-là. Or la poussée part depuis l'intérieur
> de l'événement de dégâts, très tôt dans le tick, et deux choses passent derrière : la fin de
> `Player.attack`, qui multiplie la vitesse horizontale de l'attaquant par 0,6 en course, puis la
> physique, qui rabote la composante verticale contre le sol. Le paquet partait bien — avec un
> vecteur mort.
>
> Le marteau construit maintenant le paquet lui-même et le remet **directement à la connexion du
> joueur**, dans la foulée du `setDeltaMovement`. `hurtMarked` n'est volontairement plus levé :
> il provoquerait un second paquet en fin de tick, bâti sur la vitesse entre-temps rabotée, qui
> annulerait le premier.
>
> **Et un filet.** Huit ticks après la poussée, une tâche différée vérifie que le joueur a bien
> décollé — déplacement mesuré depuis **sa** position de départ, et non depuis le point de chute,
> dont la distance dépend de l'allonge de l'arme et ne dit rien de fiable. Moins de deux blocs
> parcourus : le vecteur a été mangé en route, on le renvoie. Une seule fois.
>
> Le reste de la v10 est inchangé : balistique `v = √(2gh)`, sonde de relief à douze pas, zéro
> dégât de chute, chaîne d'éclairs bond par bond sur de vrais corps.
>
> La démarche complète est écrite au **§35** de `WATER_SIGNATURES.md`.

| Fichier | Lien |
|---|---|
| **⬇ APERÇU ANIMÉ FOUDRE** (HTML autonome — le ZIP est *dedans*) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Thunder_Three_Signatures_preview.html |
| **⬇ Sources complètes** (ZIP, 42 entrées, 690 806 o) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Thunder_Three_Signatures_src.zip |
| `WeaponVfxServerListener.java` — **le correctif du recul** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |
| `WATER_SIGNATURES.md` — **§35, pourquoi un joueur n'est pas un mob** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md |

**SHA-256 du ZIP** : `43cc4e9ceb2daadab72f96ce040d02d5a07e36b3d6d1713de160669e51d414ac`

**Un seul fichier change par rapport à la v10** : `WeaponVfxServerListener.java` (plus la doc).
Si vous avez déjà posé la v10, il suffit de remplacer ce fichier-là.

---

## Foudre v10 — 4 novembre 2026

> **Deux retours de jeu, corrigés.**
>
> **1. Le marteau ne tombe plus sur son porteur.** « Vu qu'il est dedans, il ne voit pas le
> marteau, juste une masse jaune lui tomber dessus » — exact : une masse de trente blocs à un
> mètre de la caméra remplit l'écran. Le joueur est désormais projeté **six blocs en arrière**
> au moment de l'invocation, avec une composante verticale calculée à partir du relief
> *réellement sondé* derrière lui (douze pas, hauteur libre à chaque pas) : une montagne juste
> derrière ne le bloque pas, il monte dessus. Balistique exacte — `v = √(2gh)` pour la hauteur,
> série géométrique de raison 0,91 pour viser les six blocs — et **aucun dégât de chute**.
> Réservé aux joueurs : un mob n'a pas de caméra.
>
> **2. La chaîne d'éclairs cherche enfin les mobs.** « Il ne cherche pas réellement les mobs
> dans la zone […] il suit bêtement une chorégraphie. » Le défaut était **des deux côtés**.
> Côté serveur les six maillons étaient figés d'un coup : un mob mort faisait disparaître son
> maillon, et la boîte de recherche restait collée à la hauteur de la cible initiale. Chaque
> bond est maintenant cherché **au moment du bond**, depuis la position réelle du corps
> précédent, dans un disque de 8 blocs sur 12 de haut. Les **quatre colonnes** visent elles
> aussi de vrais corps, position *et* hauteur. Côté client, le rendu dessinait ses relais sur
> un anneau tiré de la graine ; un nouveau `KIND_THUNDER_LINK` transporte la position de chaque
> victime et `chainRelays` relie **ces points-là, seulement eux**.
>
> L'anneau survit uniquement pour `/dlbvfx play 16`, qui joue l'animation sans gameplay, donc
> sans victime. Les petites répliques ne déroulent plus de chaîne du tout.

| Fichier | Lien |
|---|---|
| **⬇ APERÇU ANIMÉ FOUDRE** (HTML autonome — le ZIP est *dedans*) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Thunder_Three_Signatures_preview.html |
| **⬇ Sources complètes** (ZIP, 42 entrées, 687 711 o) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Thunder_Three_Signatures_src.zip |
| `WeaponVfxServerListener.java` — **recul du porteur** + chaîne bond par bond | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |
| `ThunderSignatureVfx.java` — `chainRelays`, les arcs vont **sur les vraies victimes** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/ThunderSignatureVfx.java |
| `WeaponWorldVfxManager.java` — `KIND_THUNDER_LINK` et `registerThunderLink` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java |
| `WATER_SIGNATURES.md` — documentation, **§34 = ce lot** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md |
| `typecheck.py` — arité, `long`→`int`, `case` en double | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/tools/typecheck.py |
| `deep-lucky-block-patch.gradle` — ton script d'init, archivé | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/tools/deep-lucky-block-patch.gradle |
| `WeaponVisualEffectId.java` — les 3 identifiants de foudre (4 / 8 / 16) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java |
| `DlbVfxCommand.java` — commande d'aperçu `/dlbvfx` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/java/deepluckyblock/commands/DlbVfxCommand.java |
| `thunder_build.js` — miroir JS de la géométrie (inchangé par ce lot) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/tools/thunder_build.js |
| Miroir MCreator `WeaponVfxServerListener.java` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |

**Comment ouvrir l'aperçu :** GitHub sert les `.html` bruts en `text/plain` — un clic
affichera donc le code source, pas l'animation. Faire **clic droit → Enregistrer la cible
sous…**, puis ouvrir le fichier enregistré dans le navigateur. La page est autonome :
l'archive des sources est embarquée dedans, le bouton « Télécharger les sources » marche
hors ligne.

**Pour tester en jeu :** `/anim thunder` donne l'épée, puis frapper un mob avec plusieurs
autres autour. `/dlbvfx play 8` et `/dlbvfx play 16` ne jouent que le visuel, sans gameplay —
donc sans recul ni chaîne réelle.

**Contrôle de l'archive :**

```
python3 src/main/resources/deepluckyblock_patch/tools/typecheck.py
```

## Eau v1.6 + Bois (13 novembre 2026)

> Le maelström : la paroi du bassin était faite de 11 hélices à nombre fixe, soit 28 %
> de couverture au bord — on voyait littéralement à travers. Elle compte maintenant ses
> dalles par anneau et couvre 138 % partout. Plus aucune texture de bloc dans la gorge.
> Le bois : troisième animation créée (`WILDWOOD_AWAKENING`), la rotation en joue bien 3.

| Fichier | Lien |
|---|---|
| **Sources completes (ZIP, 34 entrees, 600598 o)** — eau v1.6 + bois + doc §1→§19 + outils + miroirs | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/deliverables/DLB_Thunder_Three_Signatures_src.zip |
| **Aperçu animé v1.6** (HTML autonome — gorge du maelström remplie) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/deliverables/DLB_Eau_v1_preview.html |
| **Aperçu animé FOUDRE** (HTML autonome — les 3 signatures, caméra libre) · **v1.8** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/deliverables/DLB_Thunder_Three_Signatures_preview.html |
| `ThunderSignatureVfx.java` — **les 3 chorégraphies de foudre** (1002 l.) · **v1.8** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/client/ThunderSignatureVfx.java |
| `thunder_build.js` — miroir JS de la géométrie de foudre · **v1.8** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/tools/thunder_build.js |
| `WaterSignatureVfx.java` — **paroi à comptage proportionnel (138 % de couverture)** · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/client/WaterSignatureVfx.java |
| `WaterTerrainVfx.java` — **plus aucun bloc de sol dans le maelström** · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/client/WaterTerrainVfx.java |
| `water_build.js` — miroir JS de la géométrie, `op()` hissé aux 3 signatures · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/tools/water_build.js |
| `WeaponVisualEffectId.java` — **`WILDWOOD_AWAKENING` #43, 3ᵉ animation de bois** · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java |
| `GaiaWorldTreeVfx.java` — **variante `GROVE`** (modèle `ZEPHYR_VOXELS` réemployé, sans lance) · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/client/GaiaWorldTreeVfx.java |
| `WeaponVfxMotion.java` — `case WILDWOOD_AWAKENING` (switch exhaustif : bloquant à la compilation) · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/enchantments/WeaponVfxMotion.java |
| `WeaponWorldVfxManager.java` — sélecteur bois `% 2` → **`% 3`**, durée 104, routage `GROVE` · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/client/WeaponWorldVfxManager.java |
| `WeaponVfxServerListener.java` — `ANIM_WOOD` à **3 entrées** + gameplay eau · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/enchantments/WeaponVfxServerListener.java |
| `DlbVfxCommand.java` — durée et créneau de test du nouvel effet · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/commands/DlbVfxCommand.java |
| `WATER_SIGNATURES.md` — documentation §1 → **§19** (§18 maelström, §19 bois) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/WATER_SIGNATURES.md |
| `GhostBlockMask.java` — faux trou côté client | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/enchantments/GhostBlockMask.java |
| `WeaponVfxVariant.java` — table des déclinaisons (`density` 0,70 → 0,30) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/enchantments/WeaponVfxVariant.java |
| `DeepElement.java` — `WIND` en fin d'énumération | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/enchantments/DeepElement.java |
| `AnimFamily.java` — `nature` → `wood` | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/java/deepluckyblock/util/AnimFamily.java |
| `semcheck.py` — contrôle sémantique | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/tools/semcheck.py |
| Miroir MCreator `WaterSignatureVfx.java` · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/client/WaterSignatureVfx.java |
| Miroir MCreator `WaterTerrainVfx.java` · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/client/WaterTerrainVfx.java |
| Miroir MCreator `WeaponVisualEffectId.java` · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java |
| Miroir MCreator `GaiaWorldTreeVfx.java` · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/client/GaiaWorldTreeVfx.java |
| Miroir MCreator `WeaponVfxMotion.java` · **v1.6** | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/src/main/java/deepluckyblock/enchantments/WeaponVfxMotion.java |
| **Dépôt source complet** (ZIP, 741 entrées, 3563326 o) | https://github.com/Deepie1/dlbv1.21.1neoforge/raw/9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5/src-modifies-t301-t374.zip |

```text
DLB_Water_Three_Signatures_src.zip   600598 o   34 entrées   sha256 3824f4b6e6d91e93092c6f8594c932c2…
DLB_Eau_v1_preview.html              593949 o   badge v1.6
src-modifies-t301-t374.zip           3563326 o   741 entrées
commit                               9bf0633fc87b54fbc7d19bb9c82e564cb4e7b9e5
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
