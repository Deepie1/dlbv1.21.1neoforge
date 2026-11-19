# Liens de téléchargement — toujours à jour

Ces liens pointent sur la **branche de travail** (`arena/605e67fb-dlbv1-21-1neoforge`), pas sur un commit figé : ils servent donc **toujours la dernière version poussée**. Aucune mise à jour de ce fichier n'est nécessaire quand le code change.

Forme utilisée : `https://github.com/<dépôt>/raw/refs/heads/<branche>/<chemin>` — GitHub redirige vers le fichier brut exact.

> ## ⚠️ « Mais c'est la même page ! » — lire ceci avant de rouvrir un lien
>
> **`?v=N` NE SERT À RIEN sur ces adresses, et c'était une erreur de le croire.** Mesuré le
> 4 novembre 2026 : `github.com/…/raw/…/fichier.html` renvoie un **302** vers
> `raw.githubusercontent.com/…/fichier.html` — **sans la chaîne de requête**. Le
> paramètre est donc jeté par la redirection et n'atteint jamais le CDN qui sert
> réellement les octets. Il n'a jamais cassé le cache ; quand ça marchait, c'est que le
> cache avait simplement expiré tout seul.
>
> **La seule adresse garantie fraîche est celle qui porte le SHA du commit**, parce que
> son *chemin* est unique et n'a donc jamais pu être mis en cache :
>
> ```
> https://github.com/Deepie1/dlbv1.21.1neoforge/raw/<SHA>/deliverables/<fichier>
> ```
>
> Les liens en `refs/heads/…` ci-dessous restent la référence **stable** — ils serviront
> toujours la dernière version, une fois le cache expiré (quelques minutes). Les liens en
> SHA sont l'adresse **immédiate**, à utiliser le jour de la livraison.

## Chaos — cinq signatures, deux nouvelles · 4 novembre 2026 ⟵ **LOT COURANT**

### Liens figés sur le commit `7fe030ee` — garantis frais

- **Aperçu des cinq signatures (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/7fe030ee518f0eb97eca2a39c291d07971a44118/deliverables/DLB_Chaos_Five_Signatures_preview.html
- **Démo du shader Pixels 3D (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/7fe030ee518f0eb97eca2a39c291d07971a44118/deliverables/DLB_Void_Pixel_shader_demo.html
- **Sources (ZIP)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/7fe030ee518f0eb97eca2a39c291d07971a44118/deliverables/DLB_Chaos_Five_Signatures_src.zip

### Liens stables sur la branche — toujours la dernière version

- **Aperçu des cinq signatures (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Chaos_Five_Signatures_preview.html
- **Démo du shader Pixels 3D (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Void_Pixel_shader_demo.html
- **Sources (ZIP)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Chaos_Five_Signatures_src.zip
- **Ce fichier de liens** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/LIENS-DE-TELECHARGEMENT.md

> **T402b — la Cascade attaque enfin, et Le Grain de Trop porte son titre.**
>
> **Ⅳ ne frappait pas.** Les soixante-trois impacts tombaient *sur le diagramme*, au point
> de l'attracteur correspondant au rang du coup : la matière se déployait sans jamais
> traverser le corps visé. Les six vagues sont refaites en **six gestes distincts**, tous
> à hauteur de torse — **1** la lance (longitudinale, elle ressort derrière) · **2** les
> mâchoires (deux lames plates, l'une qui tombe, l'autre qui monte) · **4** les anneaux
> (quatre cercles verticaux resserrés de 3,3 à 0,35 bloc, croisés dans le corps) · **8**
> la gerbe (cône frontal) · **16** le tourbillon (deux tours d'enroulement puis la coupe,
> en braise) · **32** la grêle (saturation verticale plus une onde au sol). Aucun ne
> partage son axe d'arrivée avec son voisin.
>
> **Ⅴ manquait de détails et son titre ne se lisait pas.** Le grain faisait quatorze
> centièmes de bloc au milieu de sept cents cubes de plaque. Il est maintenant gros,
> entouré d'une coque qui bat, relié à sa case d'arrivée par un **fil à plomb** et
> annoncé par une **cible au sol qui se resserre**. Surtout, le front d'avalanche
> **rayonne depuis son point de chute** au lieu de tout faire tomber d'un bloc : braise
> au cœur, transition en blanc, bord encore intact en violet. Et six **éboulements de
> démonstration** montrent la règle pendant la montée — une case blanchit, quatre grains
> partent vers ses quatre voisines.
>
> ---
>
> **T402 — le Chaos passe de trois à cinq, et les deux nouvelles ne sont pas dessinées :
> elles sont calculées.**
>
> Les trois premières signatures posaient des cubes sur des courbes écrites à la main.
> Les deux nouvelles font tourner un vrai algorithme et posent un cube à chaque valeur
> qu'il produit. C'est le changement d'approche, et il se voit : les motifs qui
> apparaissent ne pourraient pas être inventés.
>
> **Ⅳ. Cascade de Bifurcation (170 ticks) — elle DIVISE.** La carte logistique
> `x = r·x·(1−x)` est réellement itérée : 96 colonnes de `r` entre 2,70 et 3,99, 110 pas
> de transitoire brûlés pour que l'orbite se pose sur son attracteur, 26 échantillons
> tracés. `r` court sur l'axe attaquant → cible, `x` est la hauteur. Le dédoublement est
> **mesuré, pas prétendu** : à `r` = 2,85 la nappe tient dans **une** bande de 0,02 bloc ;
> à 3,20 elle en fait **deux** (0,76) ; à 3,50, **quatre** (1,28) ; à 3,90 l'œil ne compte
> plus rien (2,27). Six vagues aux ticks 24, 48, 70, 88, 102, 112 — intervalles qui se
> resserrent 24, 22, 18, 14, 10 — et la vague `k` porte `2^k` coups : **63 impacts qui
> accélèrent**. Chaque coup tombe **sur le diagramme lui-même**, à la valeur que
> l'attracteur prend vraiment à son `r` : la grêle épouse la cascade au lieu de se
> superposer à elle.
>
> **Ⅴ. Le Grain de Trop (200 ticks) — elle ATTEND.** L'automate abélien de
> Bak-Tang-Wiesenfeld, exécuté pour de bon : grille 19×19, un grain déposé au centre, et
> toute case qui en compte quatre se vide sur ses quatre voisines. Neuf cents grains au
> moment de la rupture. **L'automate sature à trois** — une case stable n'en contient
> jamais quatre — donc ce n'est pas un tas mais une **plaque fractale**, et c'est
> pourquoi sa caméra est la seule, avec le kaléidoscope, à regarder presque à la
> verticale ; la hauteur, qui tient en trois crans, est portée par la **couleur**. Trois
> micro-glissements volontairement maigres, puis **une seule** avalanche au tick 150,
> qui vaut 4,5 × l'arme.
>
> **Elles sont diamétralement opposées, terme à terme** : 63 impacts qui accélèrent
> contre **un seul** après sept secondes et demie de silence ; une nappe verticale vue de
> profil contre une plaque horizontale vue de dessus ; le spectre entier contre des gris
> qui virent à la braise ; une équation exacte contre un automate émergent.
>
> **Aucun temps fort ne partage sa géométrie avec un autre.** Les six vagues de la
> Cascade ne diffèrent pas par un angle ou une échelle : leur nombre double, leur
> étalement sur l'axe `r` change, leur durée tombe de 8 à 3,5 ticks, et une seule voix
> sonore est jouée par vague, montant d'une octave à chaque doublement — trente-deux
> sons sur la dernière vague seraient un bruit blanc.
>
> **Vérifié.** `floatcheck` 218 fichiers / 0 alerte · `glslcheck` 4 / 0 · `typecheck`
> rien de neuf · `semcheck` identique au commit précédent sur les six fichiers touchés
> (21 alertes sur `WeaponVfxServerListener` **avant comme après**) · `node --check` sur
> le miroir JS · 16 captures Playwright sans une seule erreur JS.
>
> **Toujours à valider en jeu** : `javac` n'est pas disponible ici, les bancs ne
> remplacent pas une compilation.

## Chaos — quinze coups, quinze formes · 4 novembre 2026

- **Aperçu voxel des trois signatures (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Chaos_Three_Signatures_preview.html
- **Démo du core shader des Pixels 3D (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Void_Pixel_shader_demo.html
- **Sources (ZIP)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Chaos_Three_Signatures_src.zip

> **T401b — « TU RÉPÈTES LITTÉRALEMENT LES MÊMES ATTAQUES ! »**
>
> Reproche exact, et c'était un défaut de conception, pas un réglage. T401 avait **une
> seule** routine de frappe — une lance qui tombe, une couronne qui s'ouvre — recopiée
> **quinze fois**, avec pour uniques différences un angle qui tournait et un facteur
> d'échelle. Six fois de suite dans le kaléidoscope, huit dans la sphère, une dans le
> big bang : même objet, même vitesse, même silhouette. Ce n'était pas une
> chorégraphie, c'était un métronome — et comme la deuxième occurrence n'apprend plus
> rien à l'œil, les quatorze suivantes devenaient du bruit.
>
> **La règle appliquée maintenant : aucun temps fort ne partage sa géométrie avec un
> autre.** Pas un décalage — une forme, une direction d'arrivée, une vitesse et un
> registre chromatique qui lui appartiennent. Et chaque geste découle de ce que sa
> signature **est** :
>
> | | Kaléidoscope (6) | Sphère d'entropie (8) | Big bang |
> |---|---|---|---|
> | | I. douze éclats **convergents**, le choc ressort en colonne | 1. morsure de treillis | 14 **tractions** qui tirent vers le foyer et se resserrent |
> | | II. deux **lames croisées** qui se rencontrent sur le corps | 2. anneau de **cisaillement**, plat et tremblant | |
> | | III. la **cage** à six parois qui se referme — un écrasement, sans projectile | 3. **effondrement de secteur** : le seul geste qui arrive d'un seul côté | **détonation en trois temps** : compression, éclair (4 ticks), onde de choc (26 ticks) |
> | | IV. le **prisme éclaté** : un coup entre, douze sortent | 4. **vrillage** à deux hélices contraires | |
> | | V. la **spirale**, seul geste continu : elle visse | 5. **pluie** verticale — un axe neuf après quatre gestes latéraux | |
> | | VI. le **repliement** : dix-huit rayons s'écrasent, tout rejaillit en colonne | 6. **implosion** : la seule qui finisse par une absence | |
> | | | 7. **onde** centrifuge qui traverse | |
> | | | 8. **dissolution** : plus aucune forme nette | |
>
> **Mesuré plutôt que supposé.** Un banc calcule l'empreinte géométrique de chaque
> geste — nombre de cubes, rayon moyen et maximal, hauteur, étendue verticale, taille
> moyenne. Les quinze empreintes sont désormais distinctes : rayon maximal de 3,04 à
> 4,60 sur le kaléidoscope, étendue verticale de 2,20 à **8,18** (la colonne du
> repliement), et sur le big bang 6,20 pour l'éclair contre 6,29 pour l'onde — deux
> tailles voisines mais l'une est un noyau, l'autre un anneau au sol.
>
> **Trois gestes rallongés après capture.** Le vrillage, la pluie et la dissolution
> culminaient sous le rayon de la coquille d'entropie (≈ 2,2) : ils se jouaient
> *derrière* elle et ne se voyaient pas. Ils passent à 2,6-2,7. Eux seuls — grossir les
> huit aurait réuniformisé l'ensemble, c'est-à-dire refait la faute.
>
> **Les sons suivent.** Le même couple améthyste + verre brisé à chacun des quinze
> temps, et l'oreille le classe en bourdon de fond dès le troisième. Onze timbres
> désormais, indexés sur le geste : balayage d'attaque pour les lames, coffre verrouillé
> pour la cage, améthyste brisée pour le prisme, chaîne pour la spirale, enclume pour le
> repliement, charge sonique du Warden pour l'onde, bloc de slime pour la dissolution.
> Tous pris dans l'inventaire **déjà employé** par le mod : sans `javac` dans
> l'environnement de travail, introduire sept constantes non vérifiées aurait risqué un
> échec de compilation en jeu pour un détail de timbre.

> **T401 — « EN QUOI ÇA ATTAQUE LES MOBS ? »**
>
> Le reproche était fondé, mais pas pour la raison qu'on croirait : **les dégâts
> tombaient déjà**. Ce qui manquait, c'est que *rien ne touchait la cible à l'image*. Les
> trois signatures sont des figures — un kaléidoscope, une sphère, une explosion — et une
> figure se déploie **autour** d'un point sans jamais le frapper. L'œil voyait donc un
> décor somptueux et un mob qui perdait des cœurs sans cause visible : la définition
> exacte d'un effet qui n'attaque pas. Cinq corrections, dans cet ordre.
>
> **1. Les coups.** Sur chaque temps où le serveur inflige un dégât — et seulement sur
> ceux-là — une **lance de Pixels 3D fond sur le corps** depuis l'extérieur, puis
> **éclate** en trois couronnes, une gerbe et un noyau. Les ticks ne sont pas devinés :
> ce sont les nombres de `applyKaleidoGameplay` (30, 48, 66, 84, 102, 120),
> `applyEntropySphereGameplay` (22, 38 … 134) et `applyBigBangGameplay` (84), écrits dans
> la même unité des deux côtés. Six lances sur sept secondes et demie, ce n'est plus une
> vitrine, c'est un combat.
>
> **2. Un coup se mesure à sa figure.** Première version mesurée au banc : la lance pesait
> **dix-sept cubes contre mille cent soixante-quatorze** de kaléidoscope, soit un virgule
> quatre pour cent de l'image. Le coup *existait* et restait invisible — exactement le
> défaut qu'on corrigeait. La lance est passée à trois brins et cinquante-trois cubes, et
> l'éclat porte désormais un facteur d'échelle par signature : ×2,3 sur la cicatrice du
> big bang (cinq blocs de rayon, elle avalait tout) et ×1,9 sur la sphère d'entropie, où
> la couronne **crève la coquille** au lieu de mourir à l'intérieur.
>
> **3. Les mobs sont cloués au sol, et vivants.** Pendant les trois animations, la cible
> et tout ce qui se trouve dans le rayon ne se déplacent plus — mais gardent leur IA,
> leur regard et leurs attaques : `applyMovementHold(…, fullStun = false)`. Un mob qui
> fuit pendant qu'une figure se déploie autour de lui est la première raison pour
> laquelle on ne voyait rien toucher. Les joueurs ne sont jamais cloués (rubber-banding).
> La prise est **relevée avant la détonation du big bang** : clouer puis pousser ne
> pousse rien, le tick d'emprise suivant ramènerait le corps au foyer.
>
> **4. Les temps forts s'entendent.** Chaque frappe pose un `chaosBeatMark` **sur le corps
> de la victime** : `AMETHYST_BLOCK_HIT` + `GLASS_BREAK` + une gerbe `CHAOS_DUST_SPARK`,
> avec un pitch qui monte frappe après frappe.
>
> **5. L'item porte sa propre vie.** Sept Pixels 3D gravitent en permanence autour de
> l'arme, sur sept trajectoires fermées distinctes (anneau, méridien, huit, trèfle,
> ellipse rétrograde, montée le long du fil, grande orbite couchée). Toutes les
> fréquences sont des multiples **entiers** d'un tour de huit secondes : la constellation
> se répète donc **exactement**, sans jamais sauter. Et le slash de base porte enfin de
> vrais Pixels 3D à arêtes multicolores — l'arc voxel, qui ne peut recevoir qu'**une**
> couleur de corps, est rabattu au rôle d'armature sombre derrière eux.
>
> À valider en jeu sur le Chaos avant de propager aux douze autres familles.

> **T400 — UN MOT INTERDIT DANS LE SHADER : LE JEU NE DÉMARRAIT PLUS.**
>
> `runClient` s'arrêtait au chargement des ressources, sur une erreur de compilation du
> core shader des Pixels 3D :
>
> ```
> Invalid shaders/core/void_pixel.json: Couldn't compile vertex program
> ERROR: 0:70: 'packed' : reserved word
> ```
>
> Le vertex shader désempaquetait le canal vert dans une variable locale nommée
> `packed`. **`packed` est un mot réservé de GLSL 1.50**, la version que Minecraft
> compile : c'est un qualificatif de disposition des blocs uniformes. Le nom est
> désormais `gBits`, et le commentaire qui l'entoure interdit explicitement de le
> renommer en arrière. Aucun autre changement : la formule de désempaquetage, les huit
> modes, les gris et les enveloppes de T399 sont intacts.
>
> **Ce qui a laissé passer la faute, et ce qui la rattrapera désormais.** La page de
> démonstration compile les *vrais* fichiers du mod — mais en **GLSL ES 3.00** (WebGL 2),
> qui ne réserve pas `packed`. Le shader compilait donc dans le navigateur pendant qu'il
> refusait de compiler dans le jeu. La démo prouve ce que le shader *fait* ; elle ne
> prouvait pas qu'il *compile*. Un outil comble maintenant exactement cet écart :
>
> - `tools/glslcheck.py` — lit les quatre `.vsh`/`.fsh`, hors commentaires, et signale
>   tout identifiant figurant dans la liste des mots réservés par GLSL 1.50 mais pas par
>   GLSL ES 3.00 (`packed`, `filter`, `resource`, `input`, `output`, `interface`,
>   `sizeof`, `row_major`, `this`, `union`… une cinquantaine), plus deux pièges déjà payés
>   ici (`#moj_import` hors vanilla, `const vecN X[]` global). Il sort en erreur, donc il
>   s'enchaîne avec `floatcheck.py` avant chaque livraison.
>
> Vérifié dans les deux sens : sur le code corrigé il affiche `4 shader(s), 0 alerte(s)` ;
> remis sur la version cassée, il pointe les trois lignes 70, 71 et 72. Un balayage des
> quatre shaders n'a trouvé **aucun autre** mot interdit.

> **T399 — LES APPARITIONS ET LES DISPARITIONS, DE RIEN À RIEN.**
>
> Le gestionnaire d'impacts donnait à toutes les familles la même courbe d'opacité,
> `240·(1−t)`. Elle a deux défauts, et ils sont de nature opposée.
>
> - **Au départ, un échelon.** La signature est à **pleine opacité dès sa première
>   image** : elle ne commence pas, elle *apparaît*.
> - **À l'arrivée, une pente constante.** L'œil voit donc **deux ruptures** — là où la
>   rampe démarre et là où elle touche zéro — et entre les deux la figure passe la moitié
>   de sa chorégraphie à **50 % d'alpha**, c'est-à-dire délavée pendant précisément les
>   instants qu'elle est censée raconter.
>
> **La correction, pour Chaos.** L'appelant passe désormais une opacité de référence
> constante et la signature pose sa propre enveloppe :
> `env = smoothstep(t, 0, 0,06) · (1 − smoothstep(t, 0,84, 1))`. Un smoothstep a une
> **dérivée nulle aux deux bornes** : il n'y a donc ni démarrage ni arrêt, seulement une
> accélération puis un ralenti — et **le plateau du milieu est plein**, ce qui rend la
> chorégraphie *plus* lisible qu'avant, pas moins. L'entrée est courte (6 %, neuf ticks
> sur cent cinquante) parce qu'une signature d'impact doit **répondre au coup** : au-delà
> d'une demi-seconde on ne lit plus une frappe mais une invocation. La sortie est presque
> trois fois plus longue (16 %) parce que l'œil pardonne une arrivée rapide et **jamais**
> un arrachage. Elle s'applique dans `put()`, le point d'émission unique du fichier.
>
> **La poussière, qui était le vrai piège.** Un Pixel 3D libre, une fois né, ne connaît
> plus la signature qui l'a émis : il vit sa vie dans `VoidPixelManager` et l'enveloppe ne
> l'atteint pas. Un pixel né à 95 % de la chorégraphie avec quarante ticks de vie **reste
> seul en l'air une seconde après que tout le reste a disparu**. La première correction —
> raccourcir sa vie proportionnellement à l'enveloppe — était fausse, et le banc l'a
> montré : à *t* = 0,97 la Sphère d'Entropie affichait encore **126 pixels à l'alpha
> 222**. Ce n'est pas l'enveloppe qui borne, c'est **le temps qui reste** :
> `vie = min(vie, ticks restants)`, et **aucune naissance quand il reste moins de douze
> ticks** — en dessous, le fondu propre du pixel se joue en deux images et se lit comme un
> **clignotement**, soit le défaut qu'on corrige, mais en pire.
>
> **Le fondu d'un pixel lui-même** passe de « plein à la naissance puis rampe linéaire sur
> les 28 derniers % » à `smoothstep(vie, 0, 0,08) · (1 − smoothstep(vie, 0,70, 1))`.
> L'entrée reste très courte à dessein : au-delà, un cube passe le début de sa course à
> être invisible et la gerbe paraît terne.
>
> **Et les douze autres familles ?** Elles reçoivent la même intention sous une forme qui
> **ne peut pas régresser un réglage existant** : `240·(1−t)` est multiplié par
> `smoothstep(t, 0, 0,05) · (1 − smoothstep(t, 0,90, 1))`. Ce facteur vaut **exactement 1**
> entre 5 % et 90 %, donc la courbe sur laquelle l'Eau, la Foudre, l'Ombre, le Sang et le
> Vide ont été réglés est inchangée ; il n'ajoute jamais d'opacité, il en **retire aux deux
> bouts**. L'échelon d'entrée et la rupture de sortie disparaissent partout, sans toucher
> à une seule chorégraphie validée.
>
> **Mesuré au banc**, opacité maximale par image, sur les trois signatures :
> `t=0 → 0` · `t=0,01 → 1` · `t=0,03 → 47` · `t=0,06 → 197` · `t=0,50 → 222` ·
> `t=0,84 → 219` · `t=0,92 → 202` · `t=0,97 → 2` · `t=1 → 0`. De rien à rien, et plein au
> milieu.
>
> ---
>
> **T398 — les quatre reproches, traités un par un.** Le lot précédent a été refusé sur les
> trois signatures et sur la palette des corps. Voici ce qui a changé, et pourquoi.
>
> ---
>
> **1. LES CORPS SONT EN NIVEAUX DE GRIS. Sans exception, et ce n'est plus une
> discipline — c'est une mécanique.** Les trois signatures n'appellent plus une seule fois
> `emitVoxelCube` : perles, parois, treillis, débris, anneaux et cicatrice passent
> **tous** par le core shader des Pixels 3D. Aucun chemin de rendu ne permet donc plus
> qu'un corps sorte coloré. Trente-deux crans de luminance, de `0,055` à `0,855` — la
> rampe ne part pas de zéro, parce que **le noir n'est pas une couleur sur un fond de
> nuit** (`#05040d`) : un corps au cran zéro disparaît au lieu d'être sombre. Huit crans
> sont nommés et servent de vocabulaire : `MASQUE 2 · PROFOND 6 · CORPS 11 · MÉDIAN 16 ·
> ARMATURE 21 · VERRE 25 · ÉCLAT 29 · CŒUR 31`.
>
> **2. LA COULEUR EST AUX ARÊTES, AVEC LUMIÈRE ET DÉRIVE.** Les douze arêtes sont
> fullbright par construction (le programme ne reçoit **ni lightmap ni overlay** : il n'y
> a rien à désactiver, elles brillent parce qu'aucune lumière n'est jamais appliquée) et
> leur teinte **dérive en continu** sur `chaosHue(phase + GameTime·48 + (u+v)·0,35)` —
> la moitié froide de la roue : cyan électrique, bleu, violet profond, magenta néon.
>
> **3. HUIT DÉRIVÉS DU CUBE, ET 256 COMBINAISONS.** Le reproche était « trop peu de
> variations de mes cubes spéciaux ». Le canal vert du sommet est désormais **empaqueté**
> — `g = mode·32 + gris` — ce qui donne **huit comportements d'arête × trente-deux
> luminances de corps**, pour une division et un modulo par sommet. Aucun attribut ajouté,
> donc aucun format de sommet maison, donc aucun second `RenderType` à maintenir :
> `POSITION_TEX_COLOR` suffisait, à condition de s'en servir vraiment.
>
> | # | Dérivé | Ce qu'il fait | Ce qu'il raconte |
> |---|--------|---------------|------------------|
> | 0 | `RAINBOW` | l'onde froide continue | l'état normal |
> | 1 | `TINTED` | teinte imposée, seul l'éclat pulse | un détail qui doit rester rose |
> | 2 | `MONO` | **la luminance du cube lui-même** | **ce qui est encore en ordre** |
> | 3 | `SPECTRUM` | la roue **entière**, vert et jaune compris | **le débordement** |
> | 4 | `PULSE` | une tête vive qui *court* le long de l'arête | une direction, une vitesse |
> | 5 | `DUAL` | deux teintes selon l'orientation de l'arête | l'œil lit la **cage** du cube |
> | 6 | `STROBE` | l'arc-en-ciel quantifié en six crans | **cassé** — la couleur saute |
> | 7 | `EMBER` | dérive lente, fort scintillement | la braise qui refroidit |
>
> Le vert et le jaune n'existent **que** dans `SPECTRUM`. C'est précisément pour cela
> qu'ils veulent dire quelque chose : quand ils apparaissent, quelque chose a débordé.
>
> ---
>
> **4. ★ LES TROIS SIGNATURES ILLUSTRENT MAINTENANT LEUR TITRE.** C'était le reproche
> principal — « la Boucle Kaléidoscopique n'a pas du tout d'effet kaléidoscope ». Les
> trois ont été refaites sur le même constat : **une symétrie ne se vérifie pas sur des
> points.** Trente-quatre perles répétées douze fois donnent des confettis, pas un
> kaléidoscope. Il faut des **formes continues** dont l'œil suit le contour.
>
> **I. LA BOUCLE KALÉIDOSCOPIQUE** (#35, 150 t) — **refaite de zéro.** Neuf éclats de
> **cinq cubes contigus** forment des chevrons que l'œil lit comme des *formes*. Chaque
> éclat est répété dans **six secteurs × deux miroirs** = **douze reflets**, soit 540
> cubes en symétrie diédrale d'ordre 6. Douze **parois de verre continues** tracent les
> rayons du miroir, trois anneaux concentriques tournent en sens alternés, une cerclure de
> quatre-vingt-six cubes ferme la figure. La galette est **vraiment plate** (0,045 bloc
> d'épaisseur) et l'aperçu la regarde **de dessus** — une symétrie d'ordre six vue de
> profil n'est qu'une ligne. À 46 %, **le décrochage** : les douze reflets cessent d'être
> des reflets, et les éclats passent en `STROBE`. À 78 %, **le réalignement forcé** les
> rabat d'un coup en `ÉCLAT 29`.
>
> **II. LA SPHÈRE D'ENTROPIE** (#36, 170 t) — **le désordre se lit par contraste, pas par
> densité.** Un treillis **fermé** de 16 parallèles (jusqu'à 34 cellules par anneau) : à
> l'ancienne densité les cellules étaient six fois plus petites que leur écart, et la
> sphère ressemblait à un pointillé. Puis un **front de désordre** balaie la sphère sur
> quatre canaux simultanés — position, taille, **gris** et **mode d'arête**. La découverte
> qui a tout débloqué : **l'ordre doit être SOMBRE.** Tant qu'une cellule est intacte elle
> est en `MONO` au cran 6, presque noire ; à mesure qu'elle se désagrège son gris monte
> vers les crans 15 à 31 **et** son arête passe `RAINBOW → SPECTRUM → STROBE`. On voit
> donc littéralement une sphère ordonnée et sombre **rongée** par une marée claire et
> colorée. Les cellules totalement perdues se détachent en Pixels 3D libres sous gravité.
>
> **III. LA CICATRICE DU BIG BANG** (#37, 200 t) — **c'est la traînée qui fait le big
> bang.** Cent-soixante-dix débris, chacun suivi d'une **comète de cinq cubes** dont le
> rayon, la taille, l'alpha **et le gris** décroissent depuis la tête. L'ancienne version
> peignait ces traînées en gris profond : invisibles, et sans elles un big bang n'est
> qu'un nuage qui grandit. La **chaleur** pilote le mode d'arête — `MONO` au cœur,
> `SPECTRUM` dans la bande étroite du débordement, `RAINBOW` une fois refroidi — et la
> bande `SPECTRUM` a été resserrée parce qu'**une bande de mode trop large tue
> l'identité** : le vert doit rester un événement. Un anneau de choc de soixante-seize
> cubes s'ouvre en `sqrt`, une toile cosmique de quatorze nœuds se tisse, et
> quarante-deux marques `EMBER` **restent** au sol.
>
> ---
>
> **La démo du shader ne décrit pas le programme : elle l'exécute.** `void_pixel.vsh` et
> `void_pixel.fsh` sont lus dans `assets/` à la construction et portés en GLSL ES 3.00 par
> des substitutions **mécaniques** — l'en-tête `#version` et les `layout(location)` des
> trois attributs, rien d'autre — puis compilés par votre navigateur sur le vrai format de
> sommet du jeu. Les **huit boutons de mode** sont cliquables, et le bouton *gris
> uniforme* bascule entre les deux états que la Sphère d'Entropie met en scène. Le curseur
> monte à **12 000 cubes**, le plafond exact du gestionnaire : 288 000 sommets et
> 144 000 triangles en un seul lot.
>
> **Le gameplay serveur est inchangé** (six miroirs et six frappes pour la Boucle, huit
> pulsations et six crans d'entropie pour la Sphère, quatorze tractions et une détonation
> à prime de foule pour le Big Bang) : ce lot ne touche **que** le rendu.
>
> **À vérifier en jeu, et c'est le cas décisif** : une signature **de nuit** puis **dans
> une grotte**. Les corps doivent rester gris et les arêtes briller exactement pareil — si
> les arêtes s'assombrissent, c'est qu'une lightmap s'est glissée dans le `RenderType`.
> Puis **F3+T** pendant qu'une signature est à l'écran : le shader doit se recharger sans
> crash.

## Vide — la porte engloutit, le prisme plante sept lances · 4 novembre 2026

- **Aperçu voxel + abysse (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Void_Five_Signatures_preview.html
- **Démo du shader (page web)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Void_Portal_shader_demo.html
- **Sources (ZIP)** : https://github.com/Deepie1/dlbv1.21.1neoforge/raw/refs/heads/arena/605e67fb-dlbv1-21-1neoforge/deliverables/DLB_Void_Five_Signatures_src.zip

> **T396 — l'emprise du Vide, le glint noir, l'arc en cubes et la voix du swing.**
>
> Cinq corrections, et la première explique les autres.
>
> 1. **Aucune animation ne verrouillait le mob.** Les cinq signatures repositionnaient
>    leurs victimes à la main (`setPos`, `voidPush`) sans jamais déclarer qu'elles les
>    **tenaient**. Un seul dégât venu d'ailleurs — un autre mob, une flèche — et
>    `LivingEntity.hurt` appliquait son recul. L'infrastructure d'emprise existait déjà
>    dans le fichier (`MovementHold`, `isSignatureFullyStunned`, les gardes
>    `onWoodHeld*`), mais **aucune des cinq fonctions du Vide ne l'appelait**.
> 2. **D'où l'enfoncement inopérant de la Porte d'Horizon** : le puits ne corrigeait que
>    l'ordonnée, donc le corps poussé latéralement **glissait hors de l'anneau en gardant
>    sa profondeur**, et l'animation continuait à jouer à côté de sa victime. L'emprise du
>    Vide pose trois verrous — recul refusé à la source (`LivingKnockBackEvent`), dérive
>    effacée chaque tick (une poussée par collision ne lève aucun événement de recul), IA
>    coupée (un mob qui marche sort du puits sans aucun dégât) — et ne dicte **jamais**
>    l'ordonnée, pour que la sphère rassemble et que la chaîne tire comme avant.
> 3. **Glint noir** : le Vide affichait le magenta de rang lu dans le nom de l'arme. Mode
>    de dégradé dédié, sur le patron du mode ombral : du **noir** sur plus des trois
>    quarts de la surface, traversé de **filaments** étroits.
> 4. **Aura de cubes absente sur l'épée `/anim`** : sans enchantement, elle était rejetée
>    par **deux** filtres successifs (aucune couche résolue, puis pas d'aura de butin).
>    Elle reçoit une couche synthétisée depuis sa famille.
> 5. **Arc de coup** : plus de noir à bout blanc. Cinq tons de la palette, pointe lavande,
>    et l'arc sème les **mêmes petits cubes à texture d'abysse** que l'arme porte au repos.
> 6. **Sons** : `grep playSound` sur le gestionnaire client renvoyait **zéro**. Les grosses
>    signatures sonnent (14 à 20 sons chacune) mais ne sortent qu'à 40 % ; les **six coups
>    sur dix** restants étaient muets. Chaque swing a désormais un **souffle** et un
>    **timbre** propres à sa famille.

> **T395b — compilation : 14 erreurs `double → float`, et l'outil qui les aurait vues.**
>
> Le build a refusé `VoidSignatureVfx.java` sur quatorze « possible lossy conversion from
> double to float ». Deux causes, aucune logique : `Mth.atan2` rend un **double** là où
> `Mth.sin`/`Mth.cos` rendent un float, donc `Mth.sin(Mth.atan2(…) * 2.0f)` ne compile pas
> (2 sites) ; et `renderChain` déclarait son centre en `double cx, cy, cz` quand les
> primitives `box()` / `fragment()` le prennent en `float` (12 sites). Signature alignée sur
> les quatre autres fonctions de rendu, deux casts ajoutés.
>
> Ces fautes ne sont pas rattrapables par `jparse`, qui ne lit que la syntaxe : elles ne
> sortent qu'au build, chez vous. D'où **`tools/floatcheck.py`**, un mini-vérificateur de
> types limité aux flottants : signatures du fichier, champs, variables locales **avec leur
> position** (deux `deep` dans deux blocs ne sont pas la même variable), ternaires dont seule
> la branche compte, surcharges `max`/`min`/`clamp` résolues par leurs arguments, et tout
> appel de type de retour inconnu rendu **neutre** pour que ses arguments ne contaminent pas
> l'expression qui l'englobe. Calibré dans les deux sens : il retrouve **exactement** les 14
> lignes de javac, ni plus ni moins, et sort **0 alerte sur les 216 fichiers** de l'arbre qui
> compile. Un faux positif rendrait l'outil inutilisable ; un oubli coûte un build.
>
> **T395 — la Porte avale, le Prisme plante.**
>
> **1. La Porte d'Horizon emporte ce qui reste dedans.** Entre les sept transits, tout
> corps resté dans l'anneau **s'enfonce** : 134 ticks pour la pleine profondeur, puis la
> fermeture le **rend à la surface** en 22 ticks et l'éjecte. Trois règles que ça impose,
> et chacune a une raison qui se vérifie en jeu : on ne touche **que l'ordonnée**, sinon
> les poussées horizontales des transits seraient annulées et le tourniquet disparaîtrait ;
> la profondeur est plafonnée à la **hauteur des yeux moins 0,28 bloc** — mesurée sur le
> regard et non sur la taille, parce qu'une araignée de 0,90 de haut a l'œil à 0,585 et
> passerait sous la surface avec un simple ratio, après quoi `isInWall()` facture de
> l'asphyxie à chaque tick et la porte devient une arme à dégâts continus, ce qu'elle
> n'est pas ; enfin un corps **expulsé par un transit remonte avant d'être poussé**, sinon
> sa parabole part d'un bloc sous le sol, et il garde 26 ticks de répit le temps que son
> arc se termine. Rester coûte (`base·(1,25 + 0,45·profondeur)` à la fermeture), sortir se
> paie en transits : les deux lectures ont un prix.
>
> **2. Le Prisme : l'éventail réfracté est retiré, sept lances le remplacent.** Trois
> correctifs successifs — amincir, éteindre plus tôt, rabattre dans un cône avant —
> traitaient des symptômes. Le défaut était en amont : **une signature qui vise ne peut
> pas laisser un calcul décider où elle envoie**. Les lances sont entièrement composées.
> Elles tombent des **sept azimuts du faisceau d'entrée**, de 4,6 blocs en 9 ticks,
> traversent le cristal et se plantent la pointe **0,95 bloc derrière le centre** — la
> cible est derrière le centre, pas dessus, sinon rien n'est transpercé. Trois des sept
> arrivent par en dessous et ressortent par le corps. À l'éclat, elles sont arrachées :
> sept lances debout pendant que le cristal se disperse seraient sept objets sans
> propriétaire.
>
> **3. La lance, pièce par pièce — 194 cubes, pas inférieur au cube partout.** Un **fer**
> en feuille de 0,32 bloc à deux tranchants clairs (un fer noir sur fond noir n'existe
> pas : ici le liseré a un sens, c'est le fil de la lame), une **douille** de deux bagues
> de huit, **deux crochets** — le détail qui dit que l'objet est une arme et pas un bâton,
> puisqu'il empêche le corps traversé de remonter le long de la hampe —, une **hampe**
> effilée de 1,76 bloc avec son filet clair continu et trois ligatures, un **talon** à
> contrepoids et trois ailettes. Première version mesurée au banc : hampe de 0,09 pour
> 3 blocs de long (un crayon), ailettes espacées de 0,087 pour des cubes de 0,044 (une
> poignée de confettis). Tout a été repris sur mesure.
>
> **4. L'aperçu le montre.** La victime filaire de la page **s'enfonce** sous la Porte,
> coupée au plancher, avec sa profondeur écrite à côté d'elle — une figure qui descend de
> huit pixels ne prouve rien.

> **T394 — neuf reproches, traités un par un.**
>
> **1. Le prisme « ne ressemblait plus à rien » — il tournait trop vite.** Il ne se
> déformait pas : à 11,4 radians par unité de temps, soit près de deux tours par seconde,
> l'œil n'a pas le temps de reconstruire l'octaèdre et ne voit qu'une cage qui gigote.
> Vitesse ramenée à 6,2, recul de chaque tir divisé par deux, arêtes affinées de 0,080 à
> 0,064 bloc. La forme se lit de nouveau, pendant tout le tir.
>
> **2. « Tous les traits sont trop épais par rapport aux cubes. »** Mesuré au banc,
> groupe par groupe, en comparant la distance au plus proche voisin à la largeur du cube :
> les faisceaux faisaient 0,156 bloc de large **et** étaient troués (une couronne de cinq
> cubes tous les 0,155), les sept fils du trait d'entrée avaient un pas de 0,13 pour des
> cubes de 0,048 — plus de vide que de matière —, le souffle du dragon atteignait 0,166.
> Tout est passé en **pas plus petit que le cube**, seule façon d'obtenir un trait plein :
> faisceaux à 0,050, fils à 0,040, souffle à 0,050. Plus aucun pointillé involontaire.
>
> **3. Le dragon : bout d'aile, pieds, battement.** La voilure se terminait au carré —
> elle a désormais un **doigt terminal** qui l'effile jusqu'à la pointe. Les pieds
> vanilla, énormes à cette échelle parce que vanilla les cache sous un corps bien plus
> massif, sont réduits de 40 %. Et le battement passe de 0,52 à **0,95 radian** : c'est
> l'animation principale de la bête, comme chez vanilla.
>
> **4. Le sang ne peut plus blesser après la fin.** T387 avait borné chaque source
> séparément ; chacune respectait sa borne et la somme débordait quand même — la flaque
> vivait deux secondes de plus que la saignée, son plancher « au moins une perte »
> rajoutait une période à chaque morsure, et l'arrachement de lien s'offrait trois
> secondes à partir de n'importe quand. Un **mur unique par porteur**, écrit au lancement
> de la signature, est désormais consulté par toutes les sources. Après lui, aucun dégât
> de sang ne peut exister.
>
> **5. La Faille « laguait » — il y avait trop de mouvement, pas trop peu.** Neuf morsures
> manquées plus un frisson permanent sur quatre-vingt-dix ticks ne se lisent pas comme une
> bête qui guette mais comme une image qui saute — et quand tout bouge tout le temps, le
> vrai claquement ne ressort plus. **Trois** morsures, mais à 62 % de la course ; frisson
> divisé par trois ; la gueule se **tend** lentement pendant l'attente pour lui donner une
> direction. Toute l'énergie récupérée passe dans la fermeture : **choc d'acier** en trois
> oscillations amorties, **onde de choc** qui parcourt la ligne de morsure dans les deux
> sens, et neuf échos au lieu de cinq.
>
> **6. Les fenêtres de portail sont cadrées sur la figure.** La Faille recevait une
> ellipse de 3,70 × 0,66 qui arrivait de biais et ne coïncidait avec rien. La vraie
> ouverture est une **lentille** bordée par les deux rangées de dents : le contour est
> maintenant échantillonné sur la géométrie vivante — même `riftJawPoint`, même portée,
> même base que les mâchoires. La découpe **est** la gueule.
>
> **7. La chaîne touche tout le monde.** Deux trous réels : tuer le maillon amorcé
> **annulait la détonation** (le code sortait sur un corps nul — la parade gratuite que le
> commentaire prétendait interdire), et un mob présent mais jamais visité ne recevait
> rien. La détonation a désormais une position de repli, le relais porte à 7,0 blocs, et
> un **glanage** final frappe tout corps vivant du rayon de travail qui n'a jamais été un
> maillon.
>
> **8. Les montants de la Porte ondulent enfin.** Troisième demande, et la bonne mesure
> cette fois : l'ondulation valait 0,17 bloc sur un montant de 2,45, soit sept pour cent —
> invisible. Elle vaut maintenant 0,62, en **somme de deux harmoniques non
> commensurables** (donc jamais symétrique, jamais périodique à l'œil), et surtout chaque
> montant porte une **bosse qui monte** le long de lui et sort par le haut. C'est ça, une
> vague.

> **T393 — « le dragon ne ressemble à rien », et la Faille se voyait à peine.**
>
> **1. Le dragon est maintenant le modèle vanilla, en plus petit.** La silhouette
> paramétrique précédente inventait une anatomie ; celle-ci porte la liste de boîtes
> exacte d'`EnderDragonModel` — museau carré de 16 et lèvre plate de 12×5×16, corps de
> 24×24×64, ailes de 56 en deux segments, et surtout **le cou et la queue faits cinq et
> douze fois de la même boîte de 10×10×10**, ce qui donne la démarche de serpent. Les
> boîtes sont ORIENTÉES (`emitVoxelBoxRotated`), ce que cette classe n'avait jamais
> utilisé : le dragon entier coûte une soixantaine de cubes contre près de huit cents.
> Trois défauts corrigés après capture : la membrane en damier se lisait comme un
> damier, la queue au pas constant finissait en pointillé, et l'échelle écrasait le
> cristal.
>
> **2. La Faille est un piège à loup.** Le reproche portait sur la visibilité ; le
> défaut était ailleurs — **il n'y avait pas de mécanisme**. Deux mâchoires de 2,05 bloc
> sur charnières, dont l'angle de fermeture se *résout* pour que les pointes se
> rejoignent exactement sur l'axe ; **neuf dents par mâchoire décalées d'un demi-pas**
> pour qu'elles s'engrènent ; **neuf morsures manquées**, une par aspiration du serveur,
> si bien que le compte des neuf se lit sur le mécanisme ; le claquement en sept ticks
> avec **cinq échos** du bord denté — en cinq ticks et en u³ il tombait *entre deux
> images*, mesuré au banc ; et **trente-quatre gerbes balistiques, chacune avec sa
> traînée**, parce qu'une gerbe sans traînée est une pluie de confettis.

> **T392 — les quatre reproches de l'aperçu précédent, un par un.**
>
> **1. La texture de portail n'est plus partout une fenêtre.** Coller une ouverture ovale
> dans les cinq animations les faisait se ressembler par leur accessoire commun. La règle
> tient maintenant en une phrase : **une fenêtre pour une figure qui ENCADRE un vide, des
> cubes pour une figure qui en CONTIENT.** Trois fenêtres — Sphère, Faille, Porte — et
> deux volumes de **cubes d'abysse** — Prisme (9 cubes), Effondrement (jusqu'à 85).
> C'est le même core shader : il tire ses coordonnées de la position à l'écran, donc il
> remplit indifféremment une ouverture de trois mètres et un cube de quatre centimètres.
>
> **2. Le Prisme a son dragon, et c'est lui qui tire.** La figure montrait un octaèdre
> tirant sept rayons réfractés : une belle mécanique, mais **sans personne pour
> l'actionner**. Un dragon tourne maintenant autour du cristal — ossature en cage de six
> lisses, crête dorsale, ailes en triangle épaule-poignet-hanche avec bord d'attaque en
> Bézier, quatre nervures et une griffe. Sa gueule et ses yeux **chauffent une
> demi-seconde avant chaque tir**, et son souffle va de sa gueule à la face du cristal.
> Le cristal n'est plus l'acteur : il est le prisme qui découpe le souffle.
>
> **3. La Faille montre enfin ses coups.** Trois objets tombent sur le tick exact, à trois
> échelles de temps : une **décharge** qui saute d'une lèvre à l'autre (4 frames), les
> deux lèvres qui **claquent** puis reviennent (10 frames), et une **barre au sol par
> aspiration subie, qui reste**. Au neuvième coup la rangée est pleine : le joueur peut
> compter ce qu'il a pris.
>
> **4. Les montants de la Porte sont continus et vivants.** Le défaut était arithmétique :
> un pas de 0,115 bloc avec des cubes de 1,4 cm donne un pointillé, quoi qu'on fasse par
> ailleurs. Le pas descend à **0,026**, sous la taille du cube, et les cubes se
> recouvrent. Chaque montant reçoit en outre **sa** phase et **sa** fréquence — seize
> colonnes en phase font un rideau, seize colonnes déphasées font de l'air qui tombe —
> avec une **crête vive qui remonte** pour donner le sens du courant.
>
> **La cinquième signature est jetée et refaite : L'EFFONDREMENT EN CHAÎNE** (#45,
> `CHAIN_COLLAPSE`, 10 s, elle **PROPAGE**). Un seul ennemi est marqué. Deux secondes plus
> tard il implose, et l'implosion marque **le corps le plus proche**, qui implose à son
> tour. Chaque maillon frappe **×1,35 du précédent** et le délai tombe de 40 à 14
> frames : la chaîne s'emballe. Six corps relayés mènent à une **détonation** de
> `base·0,90·(maillons+1)` — de loin le plus gros chiffre de la famille. Une **cible
> isolée** ne trouve aucun relais : la chaîne meurt au premier maillon, deux maillons au
> maximum, signature faible.
>
> **C'est la seule signature du mod dont la puissance dépende de l'espacement des
> ennemis**, et c'est voulu : elle récompense le joueur qui a d'abord groupé ses cibles —
> avec la Sphère de Distorsion, par exemple, qui **rassemble**. Deux signatures de la même
> famille se répondent enfin.
>
> **Le son est le compte à rebours.** Trois `amethyst_block.chime` avant chaque implosion,
> hauteur `0,62 + rang·0,20 + profondeur·0,05`. Le tic monte d'un cran à chaque maillon
> pendant que le rythme se serre : on **entend** la chaîne prendre, et on entend si elle
> meurt. Les sept stations sont posées en **phyllotaxie** (angle d'or, rayon en √rang) :
> elles ne se superposent jamais, quel que soit le sel.
>
> **L'épée du Vide a ses cubes de portail.** Quatorze petits cubes d'abysse flottent
> autour de la lame, en plus du glint rose. Ils s'accrochent à des nœuds du tranchant
> choisis selon **l'angle d'or**, et décrivent une lissajous de périodes
> **incommensurables** (0,61 contre 0,83) : la figure ne se referme jamais, donc l'œil ne
> voit pas de boucle. L'écart reste entre 4 et 11 centièmes d'unité de modèle —
> **collés ou presque**, jamais un nuage détaché.
>
> **À vérifier en jeu, et c'est le cas décisif** : six mobs **serrés**, puis les mêmes six
> **dispersés**. L'écart de puissance doit s'entendre avant de se voir.
>
> Le banc de rendu ne prouve rien : il ignore le tamis de densité, le voile d'alpha et le
> plafond de sept mille cubes par image. Seul le jeu tranche.

## Sang — cinq signatures · 17 novembre 2026

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
> *(Consigne d'epoque : « suffixez les liens de ?v=387 ». Elle est fausse — voir
> l'encadre en tete de fichier : la redirection de GitHub jette la chaine de requete.)*

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
