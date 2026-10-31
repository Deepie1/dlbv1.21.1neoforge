# ⚠️ LIRE ÇA (IMPORTANT) — Deep Lucky Block 1.21.1

## Bonne nouvelle : la réparation est maintenant AUTOMATIQUE

Dans `build.gradle` il y a un bloc **"PATCH AUTOMATIQUE — MCREATOR 1.21.1"**
(ne pas le supprimer) qui, **avant chaque compilation** (runClient, build…),
restaure d'office les 8 fichiers que le générateur de MCreator 2026.1 casse
(`DeepLuckyBlockMod`, `DeepLuckyBlockModEntities`, `DeepLuckyBlockModEntityRenderers`,
`DeepLuckyBlockModItems`, `DeepLuckyBlockModModels`, `DeepLuckyBlockModTabs`,
`DeepLuckyBlockModVariables` + le mixin `MultiBufferSourceEnchantMixin`).

**Tu n'as RIEN à faire de plus : importe le projet, lance runClient.**

Ce patch a été testé en conditions réelles : les 8 fichiers ont été cassés
exactement comme MCreator le fait, puis `./gradlew build` les a réparés tout
seul → BUILD SUCCESSFUL, 0 erreur.

## v5 — les 3 bugs de gameplay sont corrigés

1. **Advancements débloqués à l'entrée du monde** : le générateur MCreator
   1.21.1 écrit `"trigger": "minecraft:tick"` (toujours vrai) dans les 51
   advancements du mod. Les JSON sont corrigés (triggers custom
   `deep_lucky_block_broken`/`negative_streak` pour les milestones,
   `minecraft:impossible` pour le reste — l'attribution réelle se fait par le
   code Java, comme dans la version 1.20.1 d'origine). Le patch auto recopie
   les 51 JSON corrigés à chaque build : même si MCreator les ré-écrit,
   ils sont réparés avant la compilation.
2. **Crash NPE "unbound value … armor_material deep_lucky_block:samouss"**
   (tooltip inventaire créatif) : les `ArmorMaterial` du mod (samouss,
   lunettes_du_patron) étaient dans un registre séparé jamais enregistré au
   bus. `DeepLuckyBlockModItems.ARMOR_MATERIALS.register(modBus)` ajouté au
   constructeur `@Mod` (fichier du kit restauré par le patch auto).
3. **Enchantements non sauvegardables** ("can't be serialized to a value"
   → les mobs/items ne persistaient pas) : `DeepEnchant.holder()` créait des
   `Holder.Direct`, qui ne se sérialisent pas en NBT. Il produit maintenant
   des holders keyés (`wrapAsHolder` via le `RegistryAccess` du level),
   comme le fait vanilla.

## La seule exception (cas rare)

Si un jour MCreator remplace ton **build.gradle** par son propre template
(le bloc de patch disparaît), la réparation auto ne se déclenchera plus.
Dans ce cas :
1. double-clique sur ce fichier `REPARER.bat` (il trouve la racine du
   projet tout seul), puis
2. lance runClient.

## D'où viennent les erreurs 1.21.1 de MCreator ?

MCreator 2026.1 ré-écrit, à l'import et après chaque modification d'élément,
ses fichiers "cadre" avec un générateur 1.21.1 bugué (constructeur `@Mod`
sans `modBus`/`modContainer`, appels `init(event)`, materials d'armure
manquants, mixin à l'API 1.20.1…). Le reste du projet (items, entités,
procédures, structures, recettes, advancements, config) est 100 % compatible
1.21.1 — c'est le mod complet testé : serveur démarré, 0 erreur, 0 warning.

## Si tu préfères ne PAS passer par MCreator

`jar/deep_lucky_block-2.3.jar` (à la racine du projet) est le mod compilé :
glisse-le dans le dossier `mods/` d'une instance NeoForge 1.21.1, c'est parti.
