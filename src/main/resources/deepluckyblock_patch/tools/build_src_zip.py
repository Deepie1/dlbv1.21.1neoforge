# -*- coding: utf-8 -*-
# ═══════════════════════════════════════════════════════════════════════════
# CONSTRUCTEUR DE deliverables/DLB_src.zip
# ═══════════════════════════════════════════════════════════════════════════
#
# LA LISTE DES FICHIERS N'EST PLUS ECRITE A LA MAIN. Elle est DERIVEE de git :
# tout ce qui differe entre l'etat de reference (deliverables/BASELINE.txt, le
# dernier commit dont on sait qu'il a compile chez l'utilisateur) et l'ARBRE DE
# TRAVAIL actuel -- modifications non commitees et fichiers neufs compris.
#
# POURQUOI. T461 : la liste manuelle avait derive. Le ZIP contenait
# DeepLuckyBlockBlock.java mais pas DeepLuckyBlockBlockEntity.java, et
# CrimsonLakeStructureSpawnProcedure.java mais pas StructureTerrainPrep.java.
# L'utilisateur a recu des APPELANTS SANS LEURS DEFINITIONS : 7 erreurs
# « cannot find symbol » sur un arbre pourtant coherent. Une liste manuelle est
# une copie de la verite, et toute copie derive. Celle-ci est calculee.
#
# Les notices restent manuelles -- c'est leur valeur -- mais un fichier SANS
# notice est quand meme livre, et le script le signale bruyamment.
#
#   python3 src/main/resources/deepluckyblock_patch/tools/build_src_zip.py
# ═══════════════════════════════════════════════════════════════════════════
import os, sys, subprocess, zipfile

RAC = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..', '..'))
Z   = os.path.join(RAC, 'deliverables', 'DLB_src.zip')
BAS = os.path.join(RAC, 'deliverables', 'BASELINE.txt')

# Le miroir documentaire n'est jamais compile : seuls les 61 fichiers restaures
# par applyMcreatorPatch en sortent, et ceux-la sont traites a part.
EXCLUS = ('src/main/resources/deepluckyblock_patch/FIXES_MCREATOR/',)

# T462 : UN ZIP N'EFFACE RIEN. Un fichier retire du depot survit indefiniment
# dans le workspace de l'utilisateur. Constate en jeu : un .txt de licence oublie
# dans un dossier d'assets faisait imprimer « Invalid path in pack ... ignoring »
# -- une ERROR a chaque lancement, pour tout joueur. Ces chemins sont donc
# ANNONCES dans le LISEZ-MOI, et supprimes automatiquement par le script Gradle
# de reparation (tools/deep-lucky-block-patch.gradle).
PARASITES = ['src/main/resources/assets/deep_lucky_block/font/PIXEL_MUSKETEER_LICENSE.txt']

# Fichiers livres meme s'ils n'ont pas bouge depuis la reference : l'utilisateur
# les a deja, les renvoyer ne coute rien et garantit la continuite du colis.
EXTRA = ['src/main/java/deepluckyblock/util/DebugLog.java']

# Ordre de presentation ; tout le reste suit, par ordre alphabetique.
ORDRE = [
 'src/main/java/deepluckyblock/procedures/Structures5Procedure.java',
 'src/main/java/deepluckyblock/procedures/Structures4Procedure.java',
 'src/main/java/deepluckyblock/block/DeepLuckyBlockBlock.java',
 'src/main/java/deepluckyblock/blockentity/DeepLuckyBlockBlockEntity.java',
 'src/main/java/deepluckyblock/item/DeepLuckyBlockItem.java',
 'src/main/java/deepluckyblock/recipe/DeepLuckyBlockCraftingRecipe.java',
 'src/main/java/deepluckyblock/CustomCreativeTabs.java',
 'src/main/java/deepluckyblock/advancements/DeepLuckyBlockAdvancements.java',
 'src/main/java/deepluckyblock/client/CosmeticAuraClientTicker.java',
 'src/main/java/deepluckyblock/util/DeepNbt.java',
 'src/main/java/deepluckyblock/util/DebugLog.java',
 'src/main/java/deepluckyblock/procedures/GenerateLuckyItemProcedure.java',
 'src/main/java/deepluckyblock/procedures/StructureTerrainPrep.java',
 'src/main/java/deepluckyblock/procedures/CrimsonLakeStructureSpawnProcedure.java',
 'src/main/java/deepluckyblock/enchantments/UniversalEnchantmentHandler.java',
 'src/main/java/deepluckyblock/enchantments/WeaponSkill.java',
 'src/main/java/deepluckyblock/client/WeaponBladeVisualRenderer.java',
 'src/main/java/deepluckyblock/util/TerrainAudit.java',
]

NOTICES = {
 'src/main/java/deepluckyblock/util/TerrainAudit.java':
  "T471 : LA PHOTOGRAPHIE DU TERRAIN, ETAPE PAR ETAPE, AU BLOC PRES.\n"
  "     Huit tours de correctifs sur le raccord du Crimson Lake (T459 a T468) ont ete\n"
  "     poses puis annules en bloc sans rien reparer. La cause tenait en une phrase :\n"
  "     personne n'avait jamais VU le terrain. Cette classe ne corrige rien, elle\n"
  "     mesure. Elle photographie toute l'emprise PLUS trois chunks d'anneau, a trois\n"
  "     jalons -- avant le clear, apres le clear, apres la pose -- et rend UN seul ZIP\n"
  "     dans <monde>/dlb_audit/ : vues du dessus, chunks sautes, ancien terrain reste\n"
  "     dans l'emprise, hauteur des marches, profil du raccord, grilles pleine\n"
  "     resolution. La sonde est strictement PASSIVE (SafeSurface.cached, et relief par\n"
  "     bruit OCEAN_FLOOR_WG quand le chunk n'est pas la) : un audit qui generait des\n"
  "     chunks montrerait un terrain que le pipeline n'avait pas, et couterait 8 a 12 s\n"
  "     par chunk a froid (T304). Arme par /dlbtest crimsonlake, desarme apres usage.",

 'src/main/java/deepluckyblock/procedures/Structures5Procedure.java':
  "T449 : LE MESSAGE DE DECOUVERTE, PARTAGE ET PLUS JAMAIS MUET.\n"
  "     announceStructureSpawn(ServerLevel, Player, nom, min, max) est passee PUBLIQUE :\n"
  "     Structures4Procedure l'appelle maintenant pour circus / shipdead / everest.\n"
  "     Le « if (player == null) return; » a saute -- sans porteur, le message part a\n"
  "     tous les joueurs de la dimension. L'appel de fin de pose est sorti du test\n"
  "     « if (j.player != null) ». structureDisplayName est publique elle aussi.",

 'src/main/java/deepluckyblock/procedures/Structures4Procedure.java':
  "T449/T454/T455 : ANNONCE DE DECOUVERTE + GARNISON DU SOMMET.\n"
  "     Ce fichier pose circus (3), shipdead (4) et everest (5) et ne contenait aucune\n"
  "     annonce ; trois appels ajoutes. T454 : spawnEverestSummitMobs pose des mobs SUR\n"
  "     la montagne -- on part du PLAFOND de la boite et on descend jusqu'au premier\n"
  "     bloc non-air, donc jamais dans une cavite. T455 : maxima tripes, 0-21 zombies\n"
  "     et 0-12 squelettes, 1-5 pieces par mob sur des emplacements DISTINCTS.",

 'src/main/java/deepluckyblock/block/DeepLuckyBlockBlock.java':
  "T449/T460 : TAUX DE RETOUR 2 %, PUIS CHANCE FRACTIONNAIRE.\n"
  "     SELF_DROP_PERCENT = 2, lu a un SEUL endroit, dans onDestroyedByPlayer (le Silk\n"
  "     Touch garde son retour a 100 %). T460 : setPlacedBy et onDestroyedByPlayer\n"
  "     travaillent en CENTIEMES. La cle NBT des centiemes est NOUVELLE\n"
  "     (SlbLuckCenti) : reutiliser l'ancienne aurait relu un bloc a +10 comme +0,10.\n"
  "     Le drop Silk Touch ecrit LuckCenti ET Luck, pour que la fraction survive.",

 'src/main/java/deepluckyblock/blockentity/DeepLuckyBlockBlockEntity.java':
  "T460 : LE MAILLON QUI MANQUAIT AU COLIS (voir T461).\n"
  "     Porte getLuckCenti / setLuckCenti et persiste « LuckCenti » a cote de l'ancien\n"
  "     « Luck ». Sans lui, DeepLuckyBlockBlock ne compile pas : c'est exactement\n"
  "     l'erreur qu'a rapportee le runClient de T461, parce que ce fichier etait absent\n"
  "     du ZIP alors que son appelant y etait. La lecture prend LuckCenti en priorite\n"
  "     et retombe sur Luck x 100 pour tous les blocs poses avant ce changement.",

 'src/main/java/deepluckyblock/item/DeepLuckyBlockItem.java':
  "T460 : LA CHANCE DU BLOC EST FRACTIONNAIRE, PAR PAS DE 0,25.\n"
  "     Un bloc a 10 plus une emeraude donne 10.75, et non 11. API en centiemes :\n"
  "     getLuckCenti / setLuckCenti / snapLuckCenti / centiToLuck / formatLuck.\n"
  "     snapLuckCenti recale sur le quart le plus proche et ne peut jamais ramener a\n"
  "     zero un apport non nul. L'ancienne cle entiere reste ecrite, arrondie, pour\n"
  "     les wands et les scripts. Le signe % a ete retire : c'est une valeur ajoutee.",

 'src/main/java/deepluckyblock/recipe/DeepLuckyBlockCraftingRecipe.java':
  "T458/T460 : TABLE D'INFUSION EN CENTIEMES, PLUS AUCUN ARRONDI.\n"
  "     Emeraude +0,75, lingot d'or +0,50, bloc d'or +5, bloc d'emeraude +7, bloc de\n"
  "     diamant +10. T460 a SUPPRIME l'arrondi de fin de craft : il faisait d'un bloc\n"
  "     a 10 plus une emeraude un bloc a 11. Effet de bord disparu au passage -- huit\n"
  "     crafts d'une emeraude et un craft de huit emeraudes donnent tous deux +6.",

 'src/main/java/deepluckyblock/CustomCreativeTabs.java':
  "T460 : les deux variantes creatives +100 / -100 passent par setLuckCenti(+-10000)\n"
  "     au lieu d'ecrire l'entier directement. Sans ce fichier le mod compile quand\n"
  "     meme -- c'est pour cela que son absence du ZIP est passee inapercue jusqu'a\n"
  "     T461 -- mais les deux blocs creatifs ne portent alors aucune cle fractionnaire.",

 'src/main/java/deepluckyblock/advancements/DeepLuckyBlockAdvancements.java':
  "T451 : LES GAINS D'ACHIEVEMENT RETABLIS, BORNES DIVISEES PAR 2, VERROUILLES.\n"
  "     T449 les avait SUPPRIMES (sur-correction signalee en jeu), T449b les avait\n"
  "     FIGES (deuxieme erreur : le tirage doit rester). Le defaut d'origine n'etait ni\n"
  "     le montant ni le tirage mais la REPETITION : l'averse repartait a chaque serie.\n"
  "     D'ou dropOnFirstUnlock, appele AVANT grantAdvancementFully -- apres, le succes\n"
  "     serait deja acquis et le verrou refuserait toujours. ATTENTION : le switch\n"
  "     « dragon -> 4, circus -> 8 » est un BITMASK de suivi, pas une recompense.",

 'src/main/java/deepluckyblock/client/CosmeticAuraClientTicker.java':
  "T452/T453/T456 : TOUS LES COSMETIQUES SUR LES COMBOS, PUIS SILENCE DE PUBLICATION.\n"
  "     F_HALO_CONTINUOUS venait d'un booleen tire au loot et, combine a « f == 0 »,\n"
  "     s'allumait quand AUCUN combo n'etait actif. Tout passe par comboActif(mask,...)\n"
  "     et la garde d'entree est mask.isEmpty(). T453 : la voilure est rattachee aux\n"
  "     combos #72, #169 et #199 par index. T456 : le traceur [DLB-DIAG] est supprime,\n"
  "     mais l'appel getCosmeticFlags reste -- il remplit le cache lu par le rendu.",

 'src/main/java/deepluckyblock/util/DeepNbt.java':
  "Marquage d'origine du butin (markLuckyBlockOrigin) : c'est lui qui distingue une\n"
  "     arme tombee d'un Deep Lucky Block d'une arme ordinaire, et donc qui autorise\n"
  "     l'aura de lame. Sans marquage, pas de visuel.",

 'src/main/java/deepluckyblock/util/DebugLog.java':
  "T456 : l'interrupteur unique de publication. ENABLED, STRUCTURE et HEART sont a\n"
  "     false ; seul -Ddlb.debug=1 (ou DLB_DEBUG=1) les rallume. Les WARN et les ERROR\n"
  "     ne sont PAS masques : un mod publie qui avale ses erreurs est indebogable.",

 'src/main/java/deepluckyblock/procedures/GenerateLuckyItemProcedure.java':
  "T470 : LE TIRAGE DE COMPETENCE AVAIT DEUX FUITES.\n"
  "     1) La branche ETERNAL (luck >= 100) rendait la main AVANT les finitions, qui\n"
  "     etaient ecrites en ligne tout en bas : une arme de rang ETERNAL ne pouvait\n"
  "     structurellement jamais recevoir de competence. Les finitions sont passees en\n"
  "     methode, finishSkillAndSlashRolls, appelee sur les DEUX sorties.\n"
  "     2) rollSkillOnDrop s'abstenait des que scan() renvoyait quelque chose -- or\n"
  "     scan() compte aussi les huit capacites activees, qui passent sans marque :\n"
  "     army_summon sur une epee LEGENDARY+, ore_detector sur une pioche RARE+ volaient\n"
  "     le tirage a 5 %. Il ne s'abstient plus que devant une competence ACCORDEE.\n"
  "     Le taux reste 5 % et le troc reste entier, comme demande.",

 'src/main/resources/deepluckyblock_patch/tools/terrainauditcheck.py':
  "T471 : LE BANC QUI PROTEGE LA MESURE.\n"
  "     Six verrous sur TerrainAudit, parce qu'une instrumentation se casse sans que\n"
  "     rien ne s'arrete dans le jeu : 1) la sonde reste PASSIVE (aucun getChunk,\n"
  "     getChunkNow, request ni level.getBlockState -- sinon elle genere le terrain\n"
  "     qu'elle pretend observer) ; 2) les trois jalons AVANT_CLEAR / APRES_CLEAR /\n"
  "     APRES_PASTE sont bien poses ; 3) la derniere photographie PRECEDE\n"
  "     ChunkKeeper.release, sans quoi toute la zone se lirait « absente » ;\n"
  "     4) l'audit s'arme explicitement et se desarme apres usage ; 5) le rendu reste\n"
  "     UN seul ZIP, annonce hors du canal limite a 20 s ; 6) le repli par bruit\n"
  "     OCEAN_FLOOR_WG survit, seul moyen de lire l'anneau non charge.\n"
  "     Les neuf cassures correspondantes ont ete testees : toutes rendent le code 1.",

 'src/main/resources/deepluckyblock_patch/tools/meteorstompcheck.py':
  "T470 : BANC NEUF. Verifie que Meteor Stomp n'est pas retourne dans le tick, que\n"
  "     son declencheur est bien abonne a LivingFallEvent, qu'il lit\n"
  "     event.getDistance() et jamais fallDistance, et que le seuil ne depasse pas\n"
  "     les 6 blocs demandes. Ses quatre verrous ont ete mis a l'epreuve en cassant\n"
  "     l'invariant un par un -- dont le piege du sous-mot sur le nom de la methode.",

 'src/main/resources/deepluckyblock_patch/tools/skillaliascheck.py':
  "T470 : QUATRE VERROUS AJOUTES (6 a 9). Les cinq premiers empechaient qu'un\n"
  "     enchantement mene a une competence ; ceux-ci empechent l'inverse, constate en\n"
  "     jeu : une competence accordee que rien ne laissait voir. Ils tiennent le rendu\n"
  "     de lame (la competence ne depend plus du tirage d'aura), le tirage au butin\n"
  "     (une capacite activee ne le vole plus, les deux sorties le posent) et\n"
  "     l'arbitrage (la marque dlbSkillGranted passe avant le niveau).",

 'src/main/java/deepluckyblock/enchantments/UniversalEnchantmentHandler.java':
  "T470 : METEOR STOMP NE SE LIT PLUS DANS LE TICK.\n"
  "     Il testait player.fallDistance >= 4 && onGround() dans EntityTickEvent.Post.\n"
  "     Le moteur remet fallDistance a zero pendant entity.tick(), dans\n"
  "     checkFallDamage(), donc AVANT que ce handler ne tourne : le tick de\n"
  "     l'atterrissage, la chute lue valait toujours zero. Les declenchements etaient\n"
  "     des accidents de synchronisation -- d'ou le « une fois sur dix » signale.\n"
  "     Nouveau declencheur : LivingFallEvent, diffuse depuis causeFallDamage juste\n"
  "     avant la remise a zero, qui porte la distance exacte. Seuil nomme\n"
  "     METEOR_STOMP_MIN_FALL = 6.0f : toute chute de plus de 6 blocs est une\n"
  "     activation. L'effet (explosion 3.0 sans degat de bloc, anneau de lave) est\n"
  "     inchange.",

 'src/main/java/deepluckyblock/enchantments/WeaponSkill.java':
  "T470 : LA MARQUE L'EMPORTE SUR L'ARBITRAGE AU NIVEAU.\n"
  "     retainedOn / retainedFoundOn / extraSkillEnchants arbitraient par winner(), au\n"
  "     plus haut niveau. Depuis que le tirage ne s'abstient plus devant une capacite\n"
  "     activee, une arme peut porter les deux -- et une capacite de plus haut niveau\n"
  "     aurait evince la competence accordee. Comme une capacite n'a pas d'animation\n"
  "     (effect() == null), l'arme serait redevenue muette : ni calque, ni clic droit,\n"
  "     ni encart. gardeeSur() consulte dlbSkillGranted d'abord.",

 'src/main/java/deepluckyblock/client/WeaponBladeVisualRenderer.java':
  "T470 : LA CAUSE PRINCIPALE DES « SKILLS INTROUVABLES ».\n"
  "     Les calques d'une competence n'etaient rendus que si bladePixels etait vrai,\n"
  "     c'est-a-dire si l'arme avait gagne l'AURA -- un tirage INDEPENDANT a 15 % en\n"
  "     melee, pose au loot par DeepNbt.markLuckyBlockOrigin. Il fallait donc gagner\n"
  "     DEUX tirages : 5 % pour la competence, puis 15 % pour avoir le droit de la\n"
  "     voir. Produit : 0,75 % d'armes de melee animees, soit moins d'une arme\n"
  "     attendue sur 300 blocs casses. L'animation n'etait pas absente, elle etait\n"
  "     inatteignable. Depuis T445, les calques d'une arme de melee ne viennent QUE de\n"
  "     la competence accordee : elle se suffit donc a elle-meme. L'aura garde son\n"
  "     role de minorite decorative (T409) sur les armes SANS competence.",

 'src/main/java/deepluckyblock/util/SafeSurface.java':
  "T465 : LA SONDE DE SOL NE PEUT PLUS INVENTER UNE ALTITUDE.\n"
  "     groundY() sondait 96 blocs vers le bas A PARTIR DE max(sommet du terrain,\n"
  "     altitude fournie par l'appelant). Declenche a Y=213 au-dessus d'un ocean,\n"
  "     cela balayait 213 -> 118 : ni le fond (~40) ni meme la mer (62) n'etaient\n"
  "     dans la fenetre. La methode rendait alors son repli SILENCIEUX, c'est-a-dire\n"
  "     l'altitude du joueur. Le lac etait ancre a 212, origine 193, rabotee par le\n"
  "     clamp a maxBuildHeight - sy = 84 : l'altitude exacte vue en jeu, 22 blocs\n"
  "     au-dessus de la mer. Le defaut n'avait rien d'oceanique -- au-dessus d'une\n"
  "     plaine a Y=70, declenche a 213, la fenetre ratait le sol pareillement.\n"
  "     La fenetre part desormais du SOMMET DU TERRAIN. Pour un appelant dont le\n"
  "     repli est deja sous ce sommet (le cas normal) le resultat est INCHANGE.\n"
  "     Nouvelles : groundYOrMissing() rend Integer.MIN_VALUE au lieu d'inventer,\n"
  "     et waterSurfaceY() rend la couche d'eau la plus haute (dernier recours).\n"
  "     groundY() reste disponible avec son repli, mais le trace desormais.",

 'src/main/java/deepluckyblock/procedures/StructureTerrainPrep.java':
  "T464 : LA COURONNE REPOSE LE SOL DETECTE, ET ELLE RACCORDE LA MARGE.\n"
  "     1) Fini le GRASS_BLOCK en dur. detectedTopBlock() rend le bloc LU sur place\n"
  "     s'il est deja un dessus legitime (terre cuite, sable, gravier, mousse...),\n"
  "     sinon la palette du chunk (paletteAt), et GRASS_BLOCK en dernier recours\n"
  "     seulement. Le defaut existait en TROIS exemplaires : rebuildColumnSection et\n"
  "     les deux chemins de rebuildColumnFast, plus un pre-filtre a liste explicite.\n"
  "     Si la coupe tombe deja sur un dessus correct, on ne repeint plus du tout.\n"
  "     2) protectFootprint accepte la SILHOUETTE reelle (un bit par colonne) : les\n"
  "     26 447 colonnes de marge de crimsonlake cessent d'etre exclues du lissage.\n"
  "     3) clampSmoothDelta et fade mesurent desormais la distance a cette silhouette\n"
  "     (transformee de chanfrein), pas au rectangle englobant -- sinon la marge\n"
  "     heritait d'un droit de coupe de 64 blocs et d'un saut de poids sur l'arete du\n"
  "     rectangle, ce qui aurait recreuse la meme tranchee par un autre chemin.\n"
  "     Masque absent => comportement rectangle d'origine, a l'identique.\n"
  "     T459 (toujours valable) : porte smoothRingOnly et setForcedFootprintProtection.\n"
  "     Porte smoothRingOnly(level, min, max, onDone) et setForcedFootprintProtection\n"
  "     (boolean), tous deux appeles par CrimsonLakeStructureSpawnProcedure. Leur\n"
  "     absence du ZIP est la cause de 5 des 7 erreurs du runClient de T461.\n"
  "     setForcedFootprintProtection existe parce que la protection d'emprise etait\n"
  "     gardee par « !PASTED_OCCUPIED.isEmpty() » : elle ne pouvait donc pas agir AVANT\n"
  "     la pose, precisement quand la couronne en a besoin. Defaut : false.",

 'src/main/java/deepluckyblock/procedures/CrimsonLakeStructureSpawnProcedure.java':
  "T464 : LE CLEAR NE RASE PLUS QUE LES COLONNES REELLEMENT BATIES.\n"
  "     crimsonlake.nbt DECLARE 246 x 284 au sol mais n'ecrit que X 0..215 / Z 14..267\n"
  "     (mesure sur les 1 366 835 blocs du fichier). clearBuildVolume rasait le\n"
  "     rectangle entier : 26 447 colonnes, 37,9 %, jusqu'a 122 blocs de large, etaient\n"
  "     videes puis jamais remplies par le paste (qui n'ecrit pas l'air du NBT) ni\n"
  "     reconstruites par le lissage (isProtected les excluait). C'est la faille droite\n"
  "     a angle droit rapportee en jeu. occupiedColumns() construit la silhouette en une\n"
  "     passe ; elle borne les DEUX chemins de clear et la protection d'emprise.\n"
  "     Verifie : 0 colonne enclavee, donc aucun relief ne peut rester au milieu du lac.\n"
  "     T465 : plus d'ancrage sur l'altitude du joueur. Si l'emprise est marine\n"
  "     (25 sondes de bruit, zero chunk genere), le lac est RELOGE vers la terre\n"
  "     ferme -- au plus 6 fois. En dernier recours seulement il se pose en mer,\n"
  "     la couche d'eau la plus haute tenant lieu de sol, et les DEUX passes de\n"
  "     lissage sont sautees : il n'y a pas de terrain a raccorder.\n"
  "     T459 (toujours valable) : smoothRingOnly avant la pose, smoothAround apres.\n"
  "     Le verrou de zone et le clear au-dessus du sol existaient deja ; manquaient le\n"
  "     lissage et la repeinte. On n'a PAS rappele prepZone + decorateTerrainOnly, que\n"
  "     T164 avait retires pour une raison mesuree (504 chunks, 1,36 M blocs, 100 s+).\n"
  "     A la place : smoothRingOnly avant la pose, puis le raccord T240 (smoothAround)\n"
  "     apres. L'emprise est protegee DE FORCE pendant les deux passes -- sans cela le\n"
  "     lissage d'avant-pose remonterait de la terre au-dessus de groundY+1 dans le\n"
  "     bassin, et le paste ne l'effacerait pas puisqu'il n'ecrit pas l'air du NBT.",

 'src/main/java/deepluckyblock/mixin/DeepMixinConnector.java':
  "T456 : commentaire seul. Le connecteur etait deja muet ; System.out y est\n"
  "     volontaire (il tourne avant tout logger du mod) et la garde l'est aussi : une\n"
  "     propriete systeme, pas DebugLog, qui n'est pas encore chargeable a ce stade.",

 'src/main/java/deepluckyblock/procedures/StructureTerrainPrep.java':
  "T469 : CE FICHIER EST REVENU A SA VERSION DE LA BASE bf10dea, A L'IDENTIQUE.\n"
  "     Tout ce que T459, T464, T467 et T468 y avaient ajoute pour le lac --\n"
  "     smoothRingOnly, la protection d'emprise forcee, la metrique de silhouette,\n"
  "     l'epinglage a y0, le masque de colonnes -- a ete retire : c'est cette\n"
  "     machinerie qui creusait les trous et les tranchees rapportes en jeu.\n"
  "     smoothAround, protectFootprint et clearFootprintProtection RESTENT en place :\n"
  "     les AUTRES structures s'en servent et n'ont jamais pose probleme. Le fichier\n"
  "     est livre precisement pour que ce revert soit applique chez toi.",

 'src/main/resources/deepluckyblock_patch/tools/structureannouncecheck.py':
  "T467 : trois gardes, chacune testee EN CASSANT l'invariant.\n"
  "     1) garde d'epinglage supprimee -> alerte ; 2) garde deplacee APRES l'ecriture\n"
  "     de target = y0, le nom restant present -> alerte (c'est le piege du sous-mot\n"
  "     qui m'avait deja eu deux fois) ; 3) brush remis sur Math.hypot du rectangle\n"
  "     -> alerte. Restauration verifiee par cmp apres chaque essai.",

 'src/main/resources/deepluckyblock_patch/tools/typecheck.py':
  "T466 : DEUX REGLES NEES D'UN ECHEC DE COMPILATION QUE LES 8 BANCS AVAIENT\n"
  "     LAISSE PASSER (runClient du 22/11, 2 erreurs).\n"
  "     D. DOUBLON : j'avais ajoute waterSurfaceY() a SafeSurface sans voir qu'une\n"
  "     privee de MEME signature y existait depuis T83. Comparaison a l'effacement,\n"
  "     par type englobant ; les classes anonymes comptent comme portee (sinon\n"
  "     setupAnim redefinie dans un « new Model(){} » criait a tort) et la\n"
  "     qualification de paquet est conservee (Logger slf4j vs log4j).\n"
  "     E. ACCES PRIVE : appeler Classe.methode() privee depuis un autre fichier.\n"
  "     Ne se declenche que si TOUTES les surcharges sont privees.\n"
  "     Les deux ont ete validees en reintroduisant les erreurs exactes du log :\n"
  "     elles sont signalees, et 0 faux positif sur les 225 fichiers.\n"
  "T464 : le banc comparait ses alertes connues en incluant « fichier:ligne ». La\n"
  "     moindre insertion decalait les 24 alertes de reference et il annoncait des\n"
  "     « NOUVEAUX problemes » sur du code non touche (6 faux positifs ce tour).\n"
  "     La fonction key(), qui retire ce prefixe, etait deja ecrite juste au-dessus\n"
  "     mais n'etait jamais appelee : elle l'est maintenant. Verifie apres coup sur un\n"
  "     cas temoin : une vraie erreur d'arite fait toujours echouer le banc.",

 'src/main/resources/deepluckyblock_patch/tools/typecheck-baseline.txt':
  "Base du banc typecheck, recalee A LA MAIN a chaque decalage de lignes. Jamais\n"
  "     regeneree : une regeneration absorberait en silence de VRAIS problemes neufs.",

 'src/main/resources/deepluckyblock_patch/tools/structureannouncecheck.py':
  "LE 8e BANC. Annonces de structure, taux de retour, table de chance en centiemes,\n"
  "     pas de 0,25, preparation de terrain du lac -- et, depuis T461, COMPLETUDE DU\n"
  "     COLIS : la regle ZIPLIVR refuse que DLB_src.zip omette un fichier modifie\n"
  "     depuis l'etat de reference, ou qu'il en contienne une version perimee.",

 'src/main/resources/deepluckyblock_patch/tools/build_src_zip.py':
  "T461 : le constructeur du ZIP, sorti de /tmp et entre dans le depot. Sa liste de\n"
  "     fichiers est DERIVEE DE GIT, plus jamais recopiee a la main.",

 'src/main/resources/deepluckyblock_patch/tools/deep-lucky-block-patch.gradle':
  "T462 : le script de reparation supprime desormais aussi les fichiers PARASITES.\n"
  "     Un ZIP ajoute et ecrase, il n'efface jamais : PIXEL_MUSKETEER_LICENSE.txt,\n"
  "     retire du depot depuis des dizaines de commits, survivait dans le workspace\n"
  "     et faisait imprimer « Invalid path in pack ... ignoring » -- une ERROR a\n"
  "     chaque lancement. ATTENTION : ce fichier n'agit que depuis\n"
  "     %USERPROFILE%\\.mcreator\\gradle\\init.d\\ -- il faut l'y RECOPIER.",

 'src/main/java/deepluckyblock/procedures/CrimsonLakeStructureSpawnProcedure.java':
  "T469 : LE LAC NE TOUCHE PLUS AU TERRAIN -- RETOUR A LA BASE GITHUB.\n"
  "     Consigne dev du 22/11 23:22, apres le run de 19:21 : « aucun smooth visible »,\n"
  "     « gros trous toujours la ! reprends de la base originele que tu avais github »,\n"
  "     « et revois ton approche ! ».\n"
  "     T459 avait branche sur le lac les deux passes de lissage (smoothRingOnly avant\n"
  "     la pose, smoothAround apres). Cinq tours de correctifs ont suivi -- T463\n"
  "     (reverte), T464, T465, T467, T468 -- sans qu'un seul run en jeu montre un\n"
  "     raccord correct : tranchee rectiligne, crevasse, couronne repeinte en herbe,\n"
  "     ancien relief laisse dans l'emprise, pics de glace traversant la structure.\n"
  "     La chaine est DEBRANCHEE, pas rafistolee une sixieme fois. Le lac revient a\n"
  "     son etat de la base bf10dea : clear franc a groundY+1 (T93 : le sous-sol est\n"
  "     conserve, le bassin est creuse par la matiere meme du template), puis paste.\n"
  "     Aucun masque de colonnes, aucune protection d'emprise, aucun lissage.\n"
  "     GARDE de l'apres-base : l'ancrage T465 (le lac ne se pose plus en l'air\n"
  "     au-dessus de la mer) et l'annonce T462 (nom lisible, pas de duree de pose aux\n"
  "     joueurs). Ni l'un ni l'autre ne touche au terrain.\n"
  "     CONSEQUENCE ASSUMEE : le bord de la coupe est net et vertical. C'est le\n"
  "     comportement de la base -- le seul qui n'ait jamais produit de trou.",

 'deliverables/BASELINE.txt':
  "T461 : l'etat de reference du poste de l'utilisateur -- le dernier commit dont on\n"
  "     sait qu'il a compile chez lui. Il n'avance que sur un runClient reussi.",
}


def _git(rac, *args):
    return subprocess.run(['git'] + list(args), cwd=rac,
                          capture_output=True, text=True, timeout=120)


def baseline(rac):
    """Premiere ligne non vide et non commentee de deliverables/BASELINE.txt."""
    with open(os.path.join(rac, 'deliverables', 'BASELINE.txt'), encoding='utf-8') as fh:
        for li in fh:
            li = li.strip()
            if li and not li.startswith('#'):
                return li.split()[0]
    raise RuntimeError('BASELINE.txt ne contient aucun commit.')


def fichiers_livrables(rac):
    """Tout ce qui differe entre l'etat de reference et l'ARBRE DE TRAVAIL.

    Compare a l'arbre et non a HEAD : le ZIP se construit AVANT le commit, donc
    une modification non encore commitee doit deja partir dans le colis.
    Renvoie (liste_ordonnee, base). Leve RuntimeError si git est inutilisable.
    """
    base = baseline(rac)
    r = _git(rac, 'diff', '--name-only', '--diff-filter=d', base, '--', 'src/')
    if r.returncode != 0:
        raise RuntimeError('git diff a echoue : ' + (r.stderr.strip() or '?'))
    vus = set(x for x in r.stdout.splitlines() if x.strip())

    # Fichiers neufs jamais indexes : invisibles pour git diff.
    r2 = _git(rac, 'ls-files', '--others', '--exclude-standard', '--', 'src/')
    if r2.returncode == 0:
        vus |= set(x for x in r2.stdout.splitlines() if x.strip())

    vus |= set(EXTRA)
    vus = set(p for p in vus if not p.startswith(EXCLUS))
    vus = set(p for p in vus if os.path.exists(os.path.join(rac, p)))

    # Le constructeur voyage avec le colis ; BASELINE.txt reste au depot (il
    # n'aurait aucun sens dans un workspace MCreator, ou il creerait un dossier
    # « deliverables » parasite).
    p = 'src/main/resources/deepluckyblock_patch/tools/build_src_zip.py'
    if os.path.exists(os.path.join(rac, p)):
        vus.add(p)

    tete = [p for p in ORDRE if p in vus]
    return tete + sorted(vus - set(tete)), base


def fichiers_supprimes(rac, base):
    """Fichiers retires du depot depuis la reference : un ZIP ne peut PAS les
    enlever du poste de l'utilisateur, il faut donc les lui nommer."""
    r = _git(rac, 'diff', '--name-only', '--diff-filter=D', base, '--', 'src/')
    if r.returncode != 0:
        return []
    return [x for x in r.stdout.splitlines() if x.strip()]


def main():
    try:
        liste, base = fichiers_livrables(RAC)
    except Exception as exc:                                  # pragma: no cover
        print('ECHEC : ' + str(exc)); return 1

    muets = [p for p in liste if p not in NOTICES]
    for p in muets:
        print('  ! AUCUNE NOTICE : ' + p)

    man = ["SOURCES A JOUR - Deep Lucky Block",
           "=================================",
           "",
           "Ce n'est PAS une copie du mod : c'est le DELTA complet entre ce que vous",
           "avez deja compile (commit " + base[:8] + ") et l'etat actuel. La liste n'est plus",
           "tenue a la main -- elle est calculee par git -- depuis que T461 a montre",
           "qu'une liste manuelle finit par oublier un fichier : vous aviez recu",
           "DeepLuckyBlockBlock.java sans DeepLuckyBlockBlockEntity.java, et",
           "CrimsonLakeStructureSpawnProcedure.java sans StructureTerrainPrep.java,",
           "d'ou 7 erreurs « cannot find symbol » sur un code pourtant coherent.",
           "",
           "Decompressez a la racine du workspace MCreator, en ecrasant.",
           ""]
    aretirer = fichiers_supprimes(RAC, base) + [
        q for q in PARASITES if not os.path.exists(os.path.join(RAC, q))]
    if aretirer:
        man += ["A SUPPRIMER A LA MAIN (un ZIP ajoute et ecrase, il n'efface jamais)",
                "----------------------------------------------------------------"]
        man += ['  ' + q for q in aretirer]
        man += ["", "  Le script Gradle de reparation les supprime tout seul, a condition",
                "  d'avoir recopie tools/deep-lucky-block-patch.gradle dans",
                "  %USERPROFILE%\\.mcreator\\gradle\\init.d\\ .", ""]
    for rel in liste:
        man.append('  ' + rel)
        man.append('     ' + NOTICES.get(
            rel, "Modifie depuis le dernier build verifie. (Pas de notice detaillee.)"))
        man.append('')
    man += ["CE QU'IL FAUT RETENIR",
            "---------------------",
            "1. LE COLIS EST VERIFIE. Le banc structureannouncecheck.py refuse qu'un",
            "   fichier modifie manque au ZIP, ou qu'il y figure dans une version",
            "   perimee. Un arbre coherent ne suffit pas : il faut qu'il ARRIVE.",
            "",
            "2. UNE SEULE PORTE DE RETOUR : DeepLuckyBlockBlock.SELF_DROP_PERCENT = 2.",
            "",
            "3. LA CHANCE EST FRACTIONNAIRE, par pas de 0,25, stockee en centiemes sous",
            "   une cle NBT NOUVELLE. L'ancienne cle reste ecrite, arrondie.",
            "",
            "Rappel : javac est absent de l'environnement de travail. Ces fichiers passent",
            "les huit bancs de coherence, mais seul un runClient prouve qu'ils compilent.",
            ""]

    with zipfile.ZipFile(Z, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        zi = zipfile.ZipInfo('LISEZ-MOI.txt'); zi.external_attr = 0o100644 << 16
        zi.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(zi, '\n'.join(man).encode('utf-8'))
        for rel in liste:
            zi = zipfile.ZipInfo(rel); zi.external_attr = 0o100644 << 16
            zi.compress_type = zipfile.ZIP_DEFLATED
            with open(os.path.join(RAC, rel), 'rb') as fh:
                z.writestr(zi, fh.read())

    print('ZIP : %d entrees (%d fichiers + LISEZ-MOI), %d octets, base %s'
          % (len(liste) + 1, len(liste), os.path.getsize(Z), base[:8]))
    if muets:
        print('      %d fichier(s) sans notice -- livres quand meme.' % len(muets))
    return 0


if __name__ == '__main__':
    sys.exit(main())
