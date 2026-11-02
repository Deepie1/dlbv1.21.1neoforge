package deepluckyblock.procedures;

import deepluckyblock.init.DeepLuckyBlockModItems;
import deepluckyblock.advancements.DeepLuckyBlockAdvancements;
import deepluckyblock.compat.DeepCustomGlint;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import deepluckyblock.util.DeepEnchant;
import net.neoforged.fml.common.EventBusSubscriber;

@SuppressWarnings({"removal", "deprecation"})
@EventBusSubscriber(modid = "deep_lucky_block")
public class GenerateLuckyItemProcedure {

    // T226 : journal cible pour le cycle de vie des structures (reservation,
    // confirmation, liberation). Reste en INFO/WARN : ce ne sont pas des debugs.
    private static final Logger LOGGER = LogUtils.getLogger();


    /** Tag portant la couleur EXACTE du rang, lu par {@code EnchantAura}. */
    public static final String GLINT_NBT_KEY = "dlbGlint";

    /*
     * POOLS D'ENCHANTEMENTS — ARCHITECTURE JSON V10
     * ---------------------------------------------
     * Aucun enchantement n'est associé à un rang dans ce Java. Chaque fichier
     * config/enchant_<type>.json contient rank_pools.<RANK> puis les catégories
     * offensive / utility / curse. Le Java calcule uniquement le rang depuis la
     * luck, lit exactement cette ligne et effectue le tirage aléatoire.
     *
     * Les seuls IDs encore écrits ici appartiennent à des règles spéciales
     * documentées : Healer's Sword, Brimstone, Feather Falling, priorité
     * Sharpness/Protection et groupes de conflits vanilla.
     */
    private static final Gson GSON = new Gson();

    // ==================== 🔧 DEBUG MODE ====================
    public static boolean DEBUG_STRUCTURE_LOOP = false;
    public static boolean FORCE_BONUS_ENCHANT = false; // 10% de chance d'enchantement supplémentaire bonus ("★ Lucky ! ★")
    private static final AtomicInteger DEBUG_STRUCTURE_COUNTER = new AtomicInteger(0);
    private static final int TOTAL_STRUCTURES = 6;

    // ==================== STRUCTURE / GENERAL ====================
    private static final int STRUCTURE_LUCK_MIN = 70;
    // ⛔ T221 : 17 -> 22 %. L'Everest etait GARANTI sur un Creator Event
    //    « Inoxtag » (aucun filtre 17 %) alors que les 5 autres structures
    //    passaient le filtre PUIS se partageaient le tirage. En remettant les
    //    6 structures dans le meme sac (voir handleCreatorEvent), le taux
    //    GLOBAL de structures baisserait de 0,5239 % a 0,4014 % par bloc
    //    (luck infusee 0, table T220) : 22 % le remet a 0,5195 %, donc le
    //    joueur ne voit AUCUNE perte de frequence, seulement l'equite.
    //    Mesure a luck infusee 0 : 1 structure tous les ~192 blocs casses,
    //    et chacune des 6 est desormais STRICTEMENT equiprobable.
    //    Monter cette seule constante suffit a rendre les structures plus
    //    frequentes (40 -> ~1 structure tous les 106 blocs).
    private static final int STRUCTURE_TRIGGER_CHANCE = 22;
    private static final int[] NORMAL_STRUCTURE_IDS = {0, 1, 2, 3, 4};
    /** Identifiant de l'Everest : 6e structure, desormais tiree comme les autres. */
    private static final int EVEREST_STRUCTURE_ID = 5;

    // ==================== CURSED ITEM ====================
    private static final int CURSED_LUCK_MAX = 10;

    // ==================== MALÉDICTIONS (CURSES) ====================
    // Leur disponibilité et leur sévérité viennent exclusivement de la ligne
    // curse du rang courant dans chaque JSON. Le dosage de probabilité reste
    // géré plus bas : USED garanti, rangs intermédiaires possibles, MYTHIC+ zéro.
    // ⛔ Lore explicatif "☠" (doublait le nom de la malédiction) : désactivé
    //    pour la publication. Remettre sur true pour le réactiver.
    private static final boolean CURSED_LORE_ENABLED = false;

    // ==================== CREATOR EVENT CURVE ====================
    // Progression linéaire : 12% à luck 0 → 28% à luck 50 → 69% à luck 100
    private static final int CREATOR_CHANCE_AT_LUCK_0 = 12;
    private static final int CREATOR_CHANCE_AT_LUCK_50 = 28;
    private static final int CREATOR_CHANCE_AT_LUCK_100 = 69;

    // ==================== STREAK SYSTEM ====================
    private static final int STREAK_THRESHOLD = 5;
    private static final int STREAK_LUCK_BONUS = 5;
    private static final long STREAK_BUFF_DURATION_TICKS = 20L * 60L;
    private static final int NEGATIVE_STREAK_THRESHOLD = 3;

    // ==================== PITY TIMER ====================
    private static final int PITY_ETERNAL_THRESHOLD = 500;

    // ==================== STORM SYSTEM ====================
    private static final int STORM_LUCK_BONUS = 15;
    private static final int STORM_LIGHTNING_CHANCE = 20;
    private static final double STORM_LIGHTNING_MIN_DIST = 5.0;
    private static final double STORM_LIGHTNING_MAX_DIST = 8.0;

    // ==================== NBT TAGS ====================
    private static final String TAG_STREAK = "slb_streak";
    private static final String TAG_BUFF_EXPIRY_TICK = "slb_buff_expiry";
    private static final String TAG_PITY_ETERNAL = "slb_pity_eternal";

    // ==================== EVEREST WORLD FLAG ====================
    private static final String EVEREST_SAVED_DATA_ID = "slb_everest_spawned";

    // ================= T226 : RESERVATION PLUTOT QUE CONSOMMATION =============
    // ⛔ CAUSE RACINE de « mes structures ne spawnent pas malgre +400 blocs » :
    //    le drapeau « cette structure est deja apparue sur ce monde » etait
    //    ECRIT SUR DISQUE des le TIRAGE (flagData.normalSpawned[id] = true +
    //    setDirty()), alors que la construction reelle se fait beaucoup plus
    //    tard, ailleurs (Structures5Procedure), et de facon asynchrone.
    //    Entre les deux, SIX chemins laissaient le drapeau brule sans rien batir :
    //      1. file d'attente PENDING_STRUCTURES perdue au redemarrage du serveur
    //      2. attente du chargement NBT interrompue
    //      3. terrain refuse / doPaste renvoie false
    //      4. getNearestPlayer(..., 64) renvoie null -> execute() jamais appele
    //      5. remplacement Bo' Cube (5 %) effacant result.hasStructure
    //      6. exception quelconque entre le tirage et la pose
    //    Chaque incident consommait DEFINITIVEMENT une des 6 structures. Une
    //    fois les 6 brulees, available.isEmpty() est toujours vrai et PLUS
    //    AUCUNE structure ne peut apparaitre sur ce monde, quel que soit le
    //    nombre de blocs casses. C'est exactement l'etat observe.
    //
    //    DESORMAIS : le tirage pose une RESERVATION en memoire (evite de tirer
    //    deux fois la meme structure tant qu'une generation est en vol), et le
    //    drapeau persistant n'est ecrit QUE lorsque la structure est reellement
    //    terminee (Structures5Procedure.notifyGenerationFinished). Tout echec
    //    libere la reservation : la structure redevient tirable.
    //    Un redemarrage vide les reservations -> rien ne reste coince.
    private static final java.util.Set<Integer> STRUCTURE_RESERVED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** true si la structure est deja batie sur ce monde, ou en cours de pose. */
    private static boolean structureUnavailable(EverestFlagData f, int id) {
        if (STRUCTURE_RESERVED.contains(id)) return true;
        return id == EVEREST_STRUCTURE_ID ? f.everestSpawned : f.normalSpawned[id];
    }

    /** Appele par Structures5Procedure quand la structure est REELLEMENT posee. */
    public static void commitStructureSpawn(ServerLevel level, int id) {
        if (id < 0) return;
        STRUCTURE_RESERVED.remove(id);
        if (level == null) return;
        EverestFlagData f = EverestFlagData.get(level);
        if (id == EVEREST_STRUCTURE_ID) f.everestSpawned = true;
        else if (id < f.normalSpawned.length) f.normalSpawned[id] = true;
        f.setDirty();
        deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT] ID={} confirmee et enregistree comme unique sur ce monde", id);
    }

    /** Appele si la generation echoue : la structure redevient disponible. */
    public static void releaseStructureReservation(int id) {
        if (id >= 0 && STRUCTURE_RESERVED.remove(id)) {
            deepluckyblock.util.DebugLog.warn(LOGGER, "[STRUCT] ID={} non posee : reservation liberee, elle pourra retomber", id);
        }
    }

    /** Diagnostic + reparation d'un monde dont les 6 drapeaux ont ete brules. */
    public static int[] structureFlagSnapshot(ServerLevel level) {
        EverestFlagData f = EverestFlagData.get(level);
        int[] out = new int[6];
        for (int i = 0; i < 5; i++) out[i] = f.normalSpawned[i] ? 1 : 0;
        out[5] = f.everestSpawned ? 1 : 0;
        return out;
    }

    public static void resetStructureFlags(ServerLevel level) {
        EverestFlagData f = EverestFlagData.get(level);
        java.util.Arrays.fill(f.normalSpawned, false);
        f.everestSpawned = false;
        f.setDirty();
        STRUCTURE_RESERVED.clear();
        deepluckyblock.util.DebugLog.info(LOGGER, "[STRUCT] Drapeaux de structures remis a zero sur ce monde");
    }

    public static class EverestFlagData extends SavedData {
        public boolean everestSpawned = false;
        public boolean[] normalSpawned = new boolean[5];

        public static EverestFlagData load(CompoundTag tag, HolderLookup.Provider registries) {
            EverestFlagData d = new EverestFlagData();
            d.everestSpawned = tag.getBoolean("everestSpawned");
            for (int i = 0; i < 5; i++) {
                d.normalSpawned[i] = tag.getBoolean("struct_" + i);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("everestSpawned", everestSpawned);
            for (int i = 0; i < 5; i++) {
                tag.putBoolean("struct_" + i, normalSpawned[i]);
            }
            return tag;
        }

        public static EverestFlagData get(ServerLevel level) {
            DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
            return storage.computeIfAbsent(new SavedData.Factory<>(EverestFlagData::new, EverestFlagData::load), EVEREST_SAVED_DATA_ID);
        }
    }

    // Grades POSITIFS (hors Doomed/Cursed) -> encore leurs achievements grade_<key>.
    private static final String[] GRADE_KEYS_POSITIVE = {
        "used", "worn", "common", "uncommon", "unusual", "remarkable",
        "rare", "outstanding", "epic", "exceptional", "legendary",
        "mythic", "relic", "masterwork", "eternal"
    };

    // ==================== SPECIAL ENCHANT RULES ====================
    private static final String FEATHER_FALLING_ID = "minecraft:feather_falling";
    private static final int FEATHER_FALLING_MAX = 50;
    private static final int FEATHER_FALLING_STEP = 5;
    // FF peut dépasser le cap standard : +0 à +5 niveaux de plus que les autres enchants
    private static final int FEATHER_FALLING_BONUS_MAX = 5;

    // ==================== BRIMSTONE (ULTRA-DÉGÂTS) ====================
    // Brimstone : disponible à partir de luck 80, niveau max II partout
    // (80-99 et ETERNAL). Brimstone ne peut jamais devenir le niveau 100 :
    // l'unique god enchant ETERNAL vient du pool COMMON dédié dans le JSON.
    private static final String BRIMSTONE_ID = "deep_lucky_block:brimstone";
    private static final int BRIMSTONE_MIN_LUCK = 80;
    private static final int BRIMSTONE_MAX_LEVEL = 2;

    private static final String LEAP_KEYWORD = "leap";
    private static final int LEAP_MAX_LEVEL = 6;
    private static final int LEAP_ACCEPT_CHANCE = 50;

    // ==================== UNBREAKABLE / DELAY ====================
    // Unbreakable (moddé, max 1 = incassable) : disponible à partir de luck 50
    // mais avec une CHANCE d'acceptation réduite ("un peu de chance").
    private static final int UNBREAKABLE_MIN_LUCK = 50;
    private static final int UNBREAKABLE_ACCEPT_CHANCE = 10;
    // Delay : niveau 1 (luck < 50) ou 1-2 (luck >= 50). Jamais au-delà de II.
    private static final int DELAY_MIN_LEVEL = 1;
    private static final int DELAY_MAX_LEVEL = 2;

    // ==================== LUNETTES DU PATRON ====================
    // 10% de chance qu'un casque généré soit remplacé par les Lunettes du
    // Patron, enchantées exactement comme un casque (pool + rank du tirage).
    private static final int PATRON_GLASSES_CHANCE = 10;

    private static final String LIGHTNESS_KEYWORD = "lightness";
    private static final int BASIC_LIGHTNESS_MIN_LEVEL = 20;
    private static final int BASIC_LIGHTNESS_APPLY_CHANCE = 8;

    private static final String SHARPNESS_ID = "minecraft:sharpness";
    private static final String PROTECTION_ID = "minecraft:protection";
    private static final int PRIORITY_ENCHANT_CHANCE = 66;

    private static final int BONUS_GOLDEN_APPLES_CHANCE = 2;
    private static final int BONUS_APPLE_BUNDLE_CHANCE = 6;
    private static final int BONUS_FIRARAIN_POTION_CHANCE = 9;
    private static final int BONUS_WOLVES_CHANCE = 7;

    // ==================== BO CUBE DE BOIS REPLACEMENT ====================
    private static final int BO_CUBE_DE_BOIS_CHANCE = 5;
    private static final String BO_CUBE_DE_BOIS_ID = "deep_lucky_block:bo_cube_de_bois";

    // ==================== APPLE STASH (coffre de pommes) ====================
    // Nerf demandé : plus de coffre plein de 64 pommes par case.
    // Pommes normales : 0-3 par case. Dorées : 0-8 (1 case). Enchantées : 0-3 (1 case).
    // Pommes moddées : seulement 30% de présence chacune, 1-2 exemplaires.
    private static final int APPLE_STASH_NORMAL_MAX = 3;
    private static final int APPLE_STASH_GOLDEN_MAX = 8;
    private static final int APPLE_STASH_NOTCH_MAX = 3;
    private static final int APPLE_STASH_MODDED_CHANCE_PERCENT = 30;
    private static final int APPLE_STASH_MODDED_MAX = 2;

    // ==================== FIRARAIN POTION DROP ====================
    // Drop 1-7 potions avec 9% de chance (BONUS_FIRARAIN_POTION_CHANCE)
    private static final int FIRARAIN_POTION_DROP_MIN = 1;
    private static final int FIRARAIN_POTION_DROP_MAX = 7;
    // ⚠️ Vérifie le vrai registry name de l'élément dans MCreator (modid:registry_name)
    private static final String FIRARAIN_POTION_ID = "deep_lucky_block:firarain_potion";
    private static Item FIRARAIN_POTION_CACHE = null;

    // ==================== RANK BOOST ENCHANTS ====================
    // Les enchants standards sont cappés à 7 ; au-delà, seul le boost de rang
    // peut pousser 1-2 enchants random à un niveau supérieur (+X selon le grade)
    private static final int STANDARD_ENCHANT_MAX_LEVEL = 7;

    private static final Set<String> PHASING_ALLOWED = Set.of(
        "deep_lucky_block:phasing", "deep_lucky_block:steady_shot",
        "deep_lucky_block:ranger", "deep_lucky_block:inverted_arrows",
        "deep_lucky_block:instant_shot", "deep_lucky_block:point_shoot",
        "deep_lucky_block:weak_force", "deep_lucky_block:retrieval",
        "minecraft:quick_charge", "minecraft:unbreaking", "minecraft:mending"
    );
    private static final Set<String> PIERCE_REPEAT_UNSAFE = Set.of(
        "deep_lucky_block:brimstone", "deep_lucky_block:delay",
        "deep_lucky_block:meteor", "deep_lucky_block:storm_strike",
        "deep_lucky_block:thunderstruck", "deep_lucky_block:water_arrow",
        "deep_lucky_block:dry_arrow", "deep_lucky_block:teleport_arrow",
        "deep_lucky_block:swap_arrow", "deep_lucky_block:grapple",
        "deep_lucky_block:briar_arrow"
    );
    private static final Set<String> TOY_UNSAFE = Set.of(
        "deep_lucky_block:brimstone", "deep_lucky_block:delay",
        "deep_lucky_block:meteor", "deep_lucky_block:storm_strike",
        "deep_lucky_block:thunderstruck", "deep_lucky_block:water_arrow",
        "deep_lucky_block:dry_arrow", "deep_lucky_block:poison_arrow",
        "deep_lucky_block:dragons_breath_arrow", "deep_lucky_block:heavy_impact",
        "deep_lucky_block:wraith_arrow", "deep_lucky_block:briar_arrow",
        "deep_lucky_block:suppression_shot", "deep_lucky_block:elemental_chain"
    );

    private static final List<Set<String>> CONFLICT_GROUPS = List.of(
        Set.of("minecraft:silk_touch", "minecraft:fortune"),
        Set.of("minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"),
        Set.of("minecraft:protection", "minecraft:blast_protection",
               "minecraft:fire_protection", "minecraft:projectile_protection",
               "deep_lucky_block:divine_protection"),
        Set.of("minecraft:infinity", "minecraft:mending"),
        // Retrieval rend des flèches alors qu'Infinity les rend illimitées :
        // les deux effets sont redondants et donc incompatibles.
        Set.of("minecraft:infinity", "deep_lucky_block:retrieval"),
        // Eau fertile et assèchement total sont des effets opposés.
        Set.of("deep_lucky_block:water_arrow", "deep_lucky_block:dry_arrow"),
        // Trois styles de vitesse continue pour bottes : un seul à la fois.
        Set.of("deep_lucky_block:momentum", "deep_lucky_block:trailblazer",
               "deep_lucky_block:windrunner"),
        Set.of("minecraft:multishot", "minecraft:piercing"),
        Set.of("deep_lucky_block:pierce", "minecraft:piercing"),
        Set.of("deep_lucky_block:unbreakable", "deep_lucky_block:eternal"),
        Set.of("deep_lucky_block:multishot_bow", "deep_lucky_block:scatter",
               "minecraft:multishot"),
        Set.of("deep_lucky_block:teleport_arrow", "deep_lucky_block:swap_arrow",
               "deep_lucky_block:grapple"),
        Set.of("deep_lucky_block:brimstone", "deep_lucky_block:delay",
               "deep_lucky_block:meteor"),
        Set.of("deep_lucky_block:storm_strike", "deep_lucky_block:thunderstruck"),
        Set.of("deep_lucky_block:ricochet", "deep_lucky_block:bounce"),
        Set.of("deep_lucky_block:sniper", "deep_lucky_block:weak_force"),
        Set.of("deep_lucky_block:pulling_power", "deep_lucky_block:fishing_grapple"),
        Set.of("deep_lucky_block:fishing_grapple", "deep_lucky_block:ender_hook"),
        Set.of("deep_lucky_block:infernal_line", "deep_lucky_block:cooked_catch"),
        Set.of("deep_lucky_block:chain_lightning", "deep_lucky_block:hydro_shock", "minecraft:channeling"),
        Set.of("deep_lucky_block:explosive_return", "deep_lucky_block:sincere_loyalty"),
        Set.of("deep_lucky_block:aerodynamic", "deep_lucky_block:wind_rider"),
        Set.of("minecraft:riptide", "deep_lucky_block:homing_trident"),
        Set.of("minecraft:riptide", "deep_lucky_block:sincere_loyalty"),
        Set.of("minecraft:riptide", "deep_lucky_block:scorching_prongs"),
        Set.of("minecraft:riptide", "deep_lucky_block:wind_lance"),
        Set.of("minecraft:riptide", "deep_lucky_block:harpoon"),
        Set.of("minecraft:riptide", "deep_lucky_block:lingering_skewer"),
        Set.of("minecraft:depth_strider", "minecraft:frost_walker"),
        Set.of("minecraft:loyalty", "minecraft:riptide"),
        Set.of("minecraft:channeling", "minecraft:riptide")
    );

    private static List<Item> APPLE_ITEMS_CACHE = null;

    // ==================== CHIENS DES CRÉATEURS (nom + couleur du nom + collier assorti) ====================
    private record DogPet(String name, String color, net.minecraft.world.item.DyeColor collar) {}

    private static final DogPet[] DOG_PETS = {
        new DogPet("Fluffy", "a", net.minecraft.world.item.DyeColor.GREEN),
        new DogPet("Ace", "c", net.minecraft.world.item.DyeColor.RED),
        new DogPet("Yuki", "f", net.minecraft.world.item.DyeColor.WHITE),
        new DogPet("Natsu", "6", net.minecraft.world.item.DyeColor.ORANGE),
        new DogPet("Kira", "9", net.minecraft.world.item.DyeColor.BLUE),
        new DogPet("Chaussette", "5", net.minecraft.world.item.DyeColor.PURPLE),
        new DogPet("Mouki", "e", net.minecraft.world.item.DyeColor.YELLOW)
    };

    // ==================== CREATORS ====================
    private record CreatorProfile(String creatorId, String[] helmetNames, String[] chestplateNames,
                                  String[] leggingsNames, String[] bootsNames, String[] swordNames,
                                  String[] pickaxeNames, String[] axeNames, String[] shovelNames,
                                  String[] bowNames, String[] crossbowNames, String[] tridentNames,
                                  String[] shieldNames, boolean isInoxtag) {
        String getNameForType(ItemType type, RandomSource random) {
            String[] names = switch (type) {
                case HELMET -> helmetNames; case CHESTPLATE -> chestplateNames;
                case LEGGINGS -> leggingsNames; case BOOTS -> bootsNames;
                case SWORD -> swordNames; case PICKAXE -> pickaxeNames;
                case AXE -> axeNames; case SHOVEL -> shovelNames;
                case HOE -> getHoeNames();
                case BOW -> bowNames; case CROSSBOW -> crossbowNames;
                case TRIDENT -> tridentNames; case SHIELD -> shieldNames;
                case FISHING_ROD -> getFishingRodNames(); case ELYTRA -> getElytraNames();
                default -> swordNames;
            };
            return names[random.nextInt(names.length)];
        }
        boolean isMaximeBiaggi() { return "maximebiaggi".equals(creatorId); }

        /**
         * Noms de houe dedies au createur (pas empruntes d'un autre outil).
         */
        String[] getHoeNames() {
            String color = swordNames.length > 0 ? swordNames[0].substring(0, 2) : "§f";
            String name = switch (creatorId) {
                case "wankilstudio" -> "WankilStudio";
                case "laink" -> "Laink";
                case "squeezie" -> "Squeezie";
                case "fuze" -> "FuzeIII";
                case "inoxtag" -> "l'Everest";
                case "kameto" -> "KCorp";
                case "frigiel" -> "Frigiel";
                case "aypierre" -> "AyPierre";
                case "michou" -> "Michou";
                case "hctuan" -> "Hctuan";
                case "linca" -> "Linca";
                case "etoiles" -> "Etoiles";
                case "mastu" -> "Mastu";
                case "joyca" -> "Joyca";
                case "maximebiaggi" -> "Maxime Biaggi";
                case "djilsi" -> "Djilsi";
                default -> creatorId.substring(0, 1).toUpperCase() + creatorId.substring(1);
            };
            return new String[]{color + "§lHoue de " + name};
        }
        String[] getFishingRodNames() {
            String color = swordNames.length > 0 ? swordNames[0].substring(0, 2) : "§f";
            return new String[]{color + "§lCanne à Pêche de " + creatorId};
        }
        String[] getElytraNames() {
            String color = swordNames.length > 0 ? swordNames[0].substring(0, 2) : "§f";
            return new String[]{color + "§lÉlytres de " + creatorId};
        }
    }

    private static final List<CreatorProfile> CREATORS = List.of(
        new CreatorProfile("wankilstudio",
            new String[]{"§d§lHeaume WankilStudio®","§d§lCasque de l'Évasion WankilStudio®"},
            new String[]{"§d§lPlastron du Flamant Rose WankilStudio®"},
            new String[]{"§d§lJambières WankilStudio®","§d§lPantalon de Terracid"},
            new String[]{"§d§lClaquettes Sacrées WankilStudio®","§d§lCrocs du Flamant"},
            new String[]{"§d§lLame WankilStudio®","§d§lÉpée du Flamant Rose"},
            new String[]{"§d§lPioche de l'Évasion WankilStudio®"},
            new String[]{"§d§lHache WankilStudio®"},
            new String[]{"§d§lPelle de l'Évasion WankilStudio®"},
            new String[]{"§d§lArc du Vautour WankilStudio®"},
            new String[]{"§d§lArbalète WankilStudio®"},
            new String[]{"§d§lTrident du Flamant WankilStudio®"},
            new String[]{"§d§lBouclier WankilStudio®"}, false),
        new CreatorProfile("laink",
            new String[]{"§3§lCasque de Laink","§3§lHeaume du Braqueur"},
            new String[]{"§3§lPlastron de Laink","§3§lArmure du Survivant"},
            new String[]{"§3§lJambières de Laink"},
            new String[]{"§3§lBottes du Braqueur","§3§lBaskets de Laink"},
            new String[]{"§3§lLame de Laink","§3§lÉpée du Braqueur Masqué"},
            new String[]{"§3§lPioche de Laink"},
            new String[]{"§3§lHache du Braqueur"},
            new String[]{"§3§lPelle de Laink"},
            new String[]{"§3§lArc de Laink"},
            new String[]{"§3§lArbalète du Braqueur"},
            new String[]{"§3§lTrident de Laink"},
            new String[]{"§3§lBouclier du Braqueur"}, false),
        new CreatorProfile("squeezie",
            new String[]{"§e§lCasque \"T'as Capté ?\"","§e§lHeaume du Roi du Web"},
            new String[]{"§e§lPlastron du Roi du Web","§e§lArmure du Marathonien"},
            new String[]{"§e§lJambières de Squeezie"},
            new String[]{"§e§lBottes du Marathonien","§e§lBaskets du Web"},
            new String[]{"§e§lLame du Roi du Web","§e§lÉpée du Marathonien"},
            new String[]{"§e§lPioche de Squeezie"},
            new String[]{"§e§lHache de Squeezie"},
            new String[]{"§e§lPelle de Squeezie"},
            new String[]{"§e§lArc de l'Ancien Temps"},
            new String[]{"§e§lArbalète de Squeezie"},
            new String[]{"§e§lTrident de Squeezie"},
            new String[]{"§e§lBouclier de Squeezie"}, false),
        new CreatorProfile("fuze",
            new String[]{"§6§lCasque en Paladium","§6§lHeaume de Didier"},
            new String[]{"§6§lPlastron en Paladium Divin"},
            new String[]{"§6§lJambières en Paladium"},
            new String[]{"§6§lBottes en Paladium","§6§lEternal Server Boots"},
            new String[]{"§6§lÉpée de Didier","§6§lLame en Paladium"},
            new String[]{"§6§lPioche en Paladium","§6§lPioche des Mines Infinies"},
            new String[]{"§6§lHache en Paladium"},
            new String[]{"§6§lPelle en Paladium"},
            new String[]{"§6§lArc en Paladium","§6§lArc de FuzeIII"},
            new String[]{"§6§lArbalète de Didier","§6§lArbalète en Paladium"},
            new String[]{"§6§lTrident en Paladium"},
            new String[]{"§6§lBouclier en Paladium"}, false),
        new CreatorProfile("inoxtag",
            new String[]{"§b§lCasque de l'Everest","§b§lHeaume d'Altitude"},
            new String[]{"§b§lPlastron de l'Ascension","§b§lArmure du Sommet"},
            new String[]{"§b§lJambières d'Altitude","§b§lPantalon du Sommet"},
            new String[]{"§b§lBottes d'Altitude","§b§lCrampon de l'Everest"},
            new String[]{"§b§lLame de l'Everest","§b§lÉpée du Sommet"},
            new String[]{"§b§lPioche du Sommet","§b§lPiolet de l'Everest"},
            new String[]{"§b§lHache de l'Ascension"},
            new String[]{"§b§lPelle du Sommet"},
            new String[]{"§b§lArc de l'Ascension"},
            new String[]{"§b§lArbalète d'Altitude"},
            new String[]{"§b§lTrident de l'Everest"},
            new String[]{"§b§lBouclier du Sommet"}, true),
        new CreatorProfile("kameto",
            new String[]{"§9§lCasque de Capitaine Kameto","§9§lHeaume KCorp"},
            new String[]{"§9§lPlastron du Mur Bleu KCorp"},
            new String[]{"§9§lJogging Officiel KCorp"},
            new String[]{"§9§lBottes de Contre-Attaque KCorp"},
            new String[]{"§9§lLame du Capitaine Kameto","§9§lÉpée KCorp"},
            new String[]{"§9§lPioche KCorp"},
            new String[]{"§9§lHache du Mur Bleu"},
            new String[]{"§9§lPelle KCorp"},
            new String[]{"§9§lArc du Mur Bleu KCorp"},
            new String[]{"§9§lArbalète KCorp"},
            new String[]{"§9§lTrident du Capitaine Kameto"},
            new String[]{"§9§lBouclier du Mur Bleu KCorp"}, false),
        new CreatorProfile("frigiel",
            new String[]{"§5§lCasque de Frigiel","§5§lHelm of Fluffy"},
            new String[]{"§5§lPlastron de Frigiel","§5§lArmure de Fluffy"},
            new String[]{"§5§lJambières de Frigiel"},
            new String[]{"§5§lBottes de Fluffy","§5§lBottes de Frigiel"},
            new String[]{"§5§lÉpée de Fluffy","§5§lLame de Frigiel"},
            new String[]{"§5§lPioche de Frigiel","§5§lPioche de Fluffy"},
            new String[]{"§5§lHache de Fluffy"},
            new String[]{"§5§lPelle de Frigiel"},
            new String[]{"§5§lArc des Mondes Perdus Frigiel"},
            new String[]{"§5§lArbalète de Fluffy"},
            new String[]{"§5§lTrident de Frigiel"},
            new String[]{"§5§lBouclier de Fluffy"}, false),
        new CreatorProfile("aypierre",
            new String[]{"§6§lCasque du Pionnier AyPierre","§6§lHeaume du Bâtisseur"},
            new String[]{"§6§lPlastron du Bâtisseur AyPierre","§6§lPlastron d'AyPierre"},
            new String[]{"§6§lJambières du Bâtisseur","§6§lJambières d'AyPierre"},
            new String[]{"§6§lBottes du Bâtisseur","§6§lBottes d'AyPierre"},
            new String[]{"§6§lÉpée du Pionnier AyPierre","§6§lLame d'AyPierre"},
            new String[]{"§6§lPioche du Créateur AyPierre","§6§lPioche d'AyPierre"},
            new String[]{"§6§lHache du Bâtisseur AyPierre"},
            new String[]{"§6§lPelle du Terrain Plat AyPierre"},
            new String[]{"§6§lArc du Bâtisseur AyPierre"},
            new String[]{"§6§lArbalète du Pionnier AyPierre"},
            new String[]{"§6§lTrident d'AyPierre"},
            new String[]{"§6§lBouclier du Pionnier AyPierre"}, false),
        new CreatorProfile("michou",
            new String[]{"§d§lCasque Michoucroute","§d§lHelm de MichMich"},
            new String[]{"§d§lPlastron Mechoui","§d§lArmure MichMich"},
            new String[]{"§d§lJambières Michoucroute"},
            new String[]{"§d§lBottes de Michou","§d§lBottes Lunaires de MichMich"},
            new String[]{"§d§lÉpée Rose de Michou","§d§lLame Mechoui"},
            new String[]{"§d§lPioche de Michou","§d§lPioche MichMich"},
            new String[]{"§d§lHache Michoucroute"},
            new String[]{"§d§lPelle Mechoui"},
            new String[]{"§d§lArc Chaotique de Michou","§d§lArc Céleste MichMich"},
            new String[]{"§d§lArbalète Star de Michou"},
            new String[]{"§d§lTrident Michoucroute"},
            new String[]{"§d§lBouclier Mechoui de MichMich"}, false),
        new CreatorProfile("hctuan",
            new String[]{
                "§c§lT§f§lE§c§lN§f§lG§c§lA §f§lM§c§lA§f§lS§c§lT§f§lE§c§lR §f§lHelmet",
                "§8§lT§6§lE§8§lN§6§lG§8§lA §6§lM§8§lA§6§lS§8§lT§6§lE§8§lR §6§lHelmet",
                "§1§lT§6§lE§1§lN§6§lG§1§lA §6§lM§1§lA§6§lS§1§lT§6§lE§1§lR §6§lHelmet",
                "§b§lCasque de Hctuan"},
            new String[]{
                "§c§lT§f§lE§c§lN§f§lG§c§lA §f§lM§c§lA§f§lS§c§lT§f§lE§c§lR §f§lChestplate",
                "§8§lT§6§lE§8§lN§6§lG§8§lA §6§lM§8§lA§6§lS§8§lT§6§lE§8§lR §6§lChestplate",
                "§1§lT§6§lE§1§lN§6§lG§1§lA §6§lM§1§lA§6§lS§1§lT§6§lE§1§lR §6§lChestplate",
                "§b§lPlastron de Hctuan"},
            new String[]{
                "§c§lT§f§lE§c§lN§f§lG§c§lA §f§lM§c§lA§f§lS§c§lT§f§lE§c§lR §f§lLeggings",
                "§b§lJambières de Hctuan"},
            new String[]{
                "§c§lT§f§lE§c§lN§f§lG§c§lA §f§lM§c§lA§f§lS§c§lT§f§lE§c§lR §f§lBoots",
                "§b§lBottes de Hctuan"},
            new String[]{
                "§c§lT§f§lE§c§lN§f§lG§c§lA §f§lM§c§lA§f§lS§c§lT§f§lE§c§lR §f§lBlade",
                "§8§lT§6§lE§8§lN§6§lG§8§lA §6§lM§8§lA§6§lS§8§lT§6§lE§8§lR §6§lBlade",
                "§1§lT§6§lE§1§lN§6§lG§1§lA §6§lM§1§lA§6§lS§1§lT§6§lE§1§lR §6§lBlade",
                "§b§lÉpée de Hctuan"},
            new String[]{
                "§c§lT§f§lE§c§lN§f§lG§c§lA §f§lM§c§lA§f§lS§c§lT§f§lE§c§lR §f§lPickaxe",
                "§b§lPioche de Hctuan"},
            new String[]{"§b§lHache de Hctuan"},
            new String[]{"§b§lPelle de Hctuan"},
            new String[]{
                "§c§lT§f§lE§c§lN§f§lG§c§lA §f§lM§c§lA§f§lS§c§lT§f§lE§c§lR §f§lBow",
                "§b§lArc de Hctuan"},
            new String[]{"§b§lArbalète de Hctuan"},
            new String[]{"§b§lTrident de Hctuan"},
            new String[]{"§b§lBouclier de Hctuan"}, false),
        new CreatorProfile("linca",
            new String[]{"§a§lCasque de Papa Linca","§a§lHelm du Nouveau Papa"},
            new String[]{"§a§lPlastron de Papa Linca","§a§lArmure Paternelle"},
            new String[]{"§a§lJambières de Papa Linca"},
            new String[]{"§a§lBottes de Papa Linca","§a§lChaussons du Nouveau Né"},
            new String[]{"§a§lÉpée de Papa Linca","§a§lLame Paternelle"},
            new String[]{"§a§lPioche de Linca"},
            new String[]{"§a§lHache de Papa Linca"},
            new String[]{"§a§lPelle de Linca"},
            new String[]{"§a§lArc de Papa Linca"},
            new String[]{"§a§lArbalète de Linca"},
            new String[]{"§a§lTrident de Papa Linca"},
            new String[]{"§a§lBouclier Paternel de Linca"}, false),
        new CreatorProfile("etoiles",
            new String[]{"§e§lCasque d'Étoiles ✦","§e§lHelm du Maître de QI"},
            new String[]{"§e§lPlastron d'Étoiles ✦","§e§lArmure du Génie"},
            new String[]{"§e§lJambières d'Étoiles ✦"},
            new String[]{"§e§lBottes d'Étoiles ✦","§e§lBottes du Maître de QI"},
            new String[]{"§e§lÉpée d'Étoiles ✦","§e§lLame du Maître de QI"},
            new String[]{"§e§lPioche d'Étoiles ✦"},
            new String[]{"§e§lHache d'Étoiles ✦"},
            new String[]{"§e§lPelle d'Étoiles ✦"},
            new String[]{"§e§lArc d'Étoiles ✦","§e§lArc du Génie"},
            new String[]{"§e§lArbalète d'Étoiles ✦"},
            new String[]{"§e§lTrident d'Étoiles ✦"},
            new String[]{"§e§lBouclier d'Étoiles ✦"}, false),
        new CreatorProfile("mastu",
            new String[]{"§2§lCasque de Mastu","§2§lHelm du Stream Master"},
            new String[]{"§2§lPlastron de Mastu"},
            new String[]{"§2§lJambières de Mastu"},
            new String[]{"§2§lBottes de Mastu"},
            new String[]{"§2§lÉpée de Mastu","§2§lLame du Stream Master"},
            new String[]{"§2§lPioche de Mastu"},
            new String[]{"§2§lHache de Mastu"},
            new String[]{"§2§lPelle de Mastu"},
            new String[]{"§2§lArc de Mastu"},
            new String[]{"§2§lArbalète de Mastu"},
            new String[]{"§2§lTrident de Mastu"},
            new String[]{"§2§lBouclier de Mastu"}, false),
        new CreatorProfile("joyca",
            new String[]{"§c§lCasque de Joyca","§c§lHelm de la Joie"},
            new String[]{"§c§lPlastron de Joyca"},
            new String[]{"§c§lJambières de Joyca"},
            new String[]{"§c§lBottes de Joyca","§c§lBaskets de la Joie"},
            new String[]{"§c§lÉpée de Joyca","§c§lLame de la Joie"},
            new String[]{"§c§lPioche de Joyca"},
            new String[]{"§c§lHache de Joyca"},
            new String[]{"§c§lPelle de Joyca"},
            new String[]{"§c§lArc de Joyca"},
            new String[]{"§c§lArbalète de Joyca"},
            new String[]{"§c§lTrident de Joyca"},
            new String[]{"§c§lBouclier de Joyca"}, false),
        new CreatorProfile("maximebiaggi",
            new String[]{"§7§lCasque du Gros Nez Biaggi","§7§lHelm aux Implants Capillaires"},
            new String[]{"§7§lPlastron de Maxime Biaggi"},
            new String[]{"§7§lJambières de Maxime Biaggi"},
            new String[]{"§7§lBottes de Maxime Biaggi","§7§lBottes du Gros Nez"},
            new String[]{"§7§lÉpée du Gros Nez","§7§lLame aux Implants de Biaggi"},
            new String[]{"§7§lPioche de Maxime Biaggi","§7§lPioche du Gros Nez"},
            new String[]{"§7§lHache de Maxime Biaggi"},
            new String[]{"§7§lPelle aux Implants"},
            new String[]{"§7§lArc du Gros Nez Biaggi"},
            new String[]{"§7§lArbalète de Maxime Biaggi"},
            new String[]{"§7§lTrident du Gros Nez"},
            new String[]{"§7§lBouclier de Maxime Biaggi"}, false),
        new CreatorProfile("djilsi",
            new String[]{"§4§lCasque de Djilsi","§4§lHelm du Boss Djilsi"},
            new String[]{"§4§lPlastron de Djilsi"},
            new String[]{"§4§lJambières de Djilsi"},
            new String[]{"§4§lBottes de Djilsi"},
            new String[]{"§4§lÉpée de Djilsi","§4§lLame du Boss Djilsi"},
            new String[]{"§4§lPioche de Djilsi"},
            new String[]{"§4§lHache de Djilsi"},
            new String[]{"§4§lPelle de Djilsi"},
            new String[]{"§4§lArc de Djilsi"},
            new String[]{"§4§lArbalète de Djilsi"},
            new String[]{"§4§lTrident de Djilsi"},
            new String[]{"§4§lBouclier de Djilsi"}, false)
    );

    private static final List<ItemType> CONFIGURED_TYPES = Arrays.asList(
        ItemType.AXE, ItemType.BOOTS, ItemType.BOW, ItemType.CHESTPLATE,
        ItemType.CROSSBOW, ItemType.HELMET, ItemType.LEGGINGS, ItemType.PICKAXE,
        ItemType.SHOVEL, ItemType.SWORD, ItemType.HOE, ItemType.TRIDENT,
        ItemType.SHIELD, ItemType.FISHING_ROD, ItemType.ELYTRA
    );

    // ==================== LUCK CONTEXT ====================
    private record LuckContext(int effectiveLuck, boolean forceCreator, boolean forceEternal,
                               boolean isStormActive, boolean lightningTriggered) {}

    private static LuckContext computeLuckContext(int rolledLuck, ServerPlayer player, Level level,
                                                  double x, double y, double z, RandomSource random) {
        int luck = rolledLuck;
        boolean stormActive = false;
        boolean lightningTriggered = false;
        boolean forceCreator = false;
        boolean forceEternal = false;

        if (level instanceof ServerLevel sl && sl.isThundering()) {
            stormActive = true;
            luck = Math.min(100, luck + STORM_LUCK_BONUS);
            if (random.nextInt(100) < STORM_LIGHTNING_CHANCE && player != null) {
                spawnStormLightning(sl, player, random);
                lightningTriggered = true;
            }
        }

        if (player != null && level instanceof ServerLevel sl2) {
            CompoundTag data = player.getPersistentData();
            long expiry = data.getLong(TAG_BUFF_EXPIRY_TICK);
            long now = sl2.getServer().getTickCount();
            if (expiry > now) {
                luck = Math.min(100, luck + STREAK_LUCK_BONUS);
            } else if (expiry > 0) {
                data.putLong(TAG_BUFF_EXPIRY_TICK, 0L);
            }
        }

        if (player != null) {
            CompoundTag data = player.getPersistentData();
            int pityET = data.getInt(TAG_PITY_ETERNAL);

            if (pityET >= PITY_ETERNAL_THRESHOLD) {
                forceEternal = true;
                forceCreator = true;
                luck = 100;
            }
        }

        return new LuckContext(luck, forceCreator, forceEternal, stormActive, lightningTriggered);
    }

    private static void spawnStormLightning(ServerLevel sl, ServerPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = STORM_LIGHTNING_MIN_DIST + random.nextDouble() *
                      (STORM_LIGHTNING_MAX_DIST - STORM_LIGHTNING_MIN_DIST);
        double lx = player.getX() + Math.cos(angle) * dist;
        double lz = player.getZ() + Math.sin(angle) * dist;

        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, sl);
        bolt.moveTo(lx, player.getY(), lz);
        bolt.setVisualOnly(false);
        sl.addFreshEntity(bolt);

        sendActionBarToPlayer(player, "§e§l⚡ Storm's blessing... and its wrath! ⚡");
    }

    // ==================== GENERATE CHEST PAYLOAD (SANS DEBUG LOOP) ====================
    public static TestProcedure.BranchResult generateChestPayload(Level level, double x, double y, double z,
                                                                  int mainLuck, RandomSource random) {
        boolean oldDebug = DEBUG_STRUCTURE_LOOP;
        DEBUG_STRUCTURE_LOOP = false;
        try {
            return generatePayload(level, x, y, z, mainLuck, random);
        } finally {
            DEBUG_STRUCTURE_LOOP = oldDebug;
        }
    }

    private static ItemStack createBoCubeDeBoisReward() {
        try {
            ResourceLocation itemId = ResourceLocation.tryParse(BO_CUBE_DE_BOIS_ID);
            if (itemId == null) return ItemStack.EMPTY;

            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null) return ItemStack.EMPTY;

            ItemStack stack = new ItemStack(item);
            // PORT 1.21.1 : "Unbreakable" est un composant de donnees (pas un
            // champ NBT). Pas de foil, item incassable.
            stack.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                    new net.minecraft.world.item.component.Unbreakable(false));
            applyBoCubeLore(stack);
            return stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void applyBoCubeLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        try {
            // PORT 1.21.1 : le lore est le composant ItemLore (plus de NBT "display")
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Très utile pour fuir : utilisez-le une seule fois et sautez dessus en allant dans la direction choisie."));
            lore.add(Component.literal("§7Spammez saut pour prendre aussi de la hauteur."));
            lore.add(Component.literal("§6Attention à la chute !"));
            stack.set(net.minecraft.core.component.DataComponents.LORE,
                    new net.minecraft.world.item.component.ItemLore(lore));
        } catch (Exception ignored) {}
    }

    // ⛔ NOUVEAU (publication) : nom FIXE pour que tous les Bo' Cube de Bois se
    //    STACKENT. Avant, la couleur du nom dépendait du rang du tirage
    //    (profile.colorCode) : chaque Bo' Cube avait donc un NBT différent et ne
    //    se stackait pas, même avec le même enchantement. Avec un nom fixe, le
    //    NBT devient identique pour tous -> ils se stackent normalement.
    //    L'ancien code (couleur selon le rang) est conservé dans le else.
    //    Remettre BO_CUBE_FIXED_NAME sur false pour retrouver l'ancien affichage.
    private static final boolean BO_CUBE_FIXED_NAME = true;

    private static void replacePositiveRewardWithBoCube(TestProcedure.BranchResult result,
                                                        RankProfile profile) {
        ItemStack stack = createBoCubeDeBoisReward();
        if (stack.isEmpty()) return;

        String displayColor = (profile != null && profile.colorCode != null && !profile.colorCode.isBlank())
                ? profile.colorCode : "6";
        if (BO_CUBE_FIXED_NAME) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lBo' Cube de Bois"));
        } else {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§" + displayColor + "§lBo' Cube de Bois"));
        }

        result.items.clear();
        result.items.add(stack);
        result.particles.clear();
        result.particles.add("ENCHANT");
        result.particles.add("END_ROD");
        result.hasStructure = false;
        result.structureId = -1;
        result.deferredAction = null;
        result.title = "Bo' Cube de Bois";
        result.titleColorCode = displayColor;
    }

    private static void tryReplacePositiveRewardWithBoCube(TestProcedure.BranchResult result,
                                                           RankProfile profile,
                                                           RandomSource random) {
        // ⛔ T224 — BUG SILENCIEUX TROUVE A L'AUDIT : quand ce remplacement (5 %)
        //    tombait sur un tirage qui venait d'obtenir une structure, il faisait
        //    « result.hasStructure = false » APRES que le flag persistant du monde
        //    (normalSpawned[structId] / everestSpawned) ait deja ete ecrit.
        //    La structure etait donc comptee comme deja apparue pour ce monde...
        //    sans jamais avoir ete construite. Comme chaque structure est unique
        //    par monde, elle etait PERDUE DEFINITIVEMENT. Probabilite d'en perdre
        //    au moins une sur les 6 : 1 - 0,95^6 = 26,5 %.
        if (result.hasStructure) {
            return;
        }

        if (result == null || result.negative) return;
        if (random.nextInt(100) >= BO_CUBE_DE_BOIS_CHANCE) return;
        replacePositiveRewardWithBoCube(result, profile);
    }

    /** Suffixe de titre d'une structure : reprend le titre deja pose par la
     *  branche courante (« Creator Event - X » ou « Legendary Reward »). */
    private static String structureTitleSuffix(TestProcedure.BranchResult result) {
        String t = result.title == null ? "" : stripColor(result.title);
        if (t.startsWith("Creator Event - ")) t = t.substring("Creator Event - ".length());
        return t;
    }

    // ===================== T224 : STRUCTURES DECOUPLEES DU CREATOR EVENT =====
    // ⛔ CAUSE RACINE du retour « j'ai toujours aucune structure et j'en ai
    //    casse une bonne centaine ! » : tout ce bloc vivait a l'INTERIEUR de
    //    handleCreatorEvent. Une structure ne pouvait donc apparaitre QUE si le
    //    tirage avait d'abord produit un Creator Event (12 % a luck 0, ~40 % en
    //    moyenne sur les rangs >= 70). Un tirage « objet normal » de rang
    //    Outstanding/Rare/Epic... ne pouvait JAMAIS reveler de structure.
    //    Mesure a luck infusee 0 : 0,5195 % par bloc, soit 1 structure tous les
    //    193 blocs, et 59 % de chance de n'en voir AUCUNE sur 100 blocs casses.
    //    APRES : le test est remonte dans generatePayload, il s'applique aux
    //    DEUX branches. Plus aucune constante de probabilite n'est touchee ;
    //    le facteur « Creator Event » disparait simplement du produit.
    //    Nouveau taux a luck 0 : 0,9543 % par bloc = 1 structure tous les
    //    105 blocs. Pour accelerer encore, monter STRUCTURE_TRIGGER_CHANCE
    //    (22 -> 35 donne 1 structure tous les 66 blocs a luck 0).
    private static void maybeTriggerStructure(TestProcedure.BranchResult result, Level level,
                                              int branchLuck, ServerPlayer nearestSP,
                                              RandomSource random, CreatorProfile creator) {
        // 🧪 DEBUG COMMAND /dlb : aucune structure ne peut être déclenchée pendant
        // une génération debug (ni l'Everest, ni les structures normales).
        if (DEBUG_COMMAND_ACTIVE) {
            return;
        }

        // ===================== STRUCTURES (T221) =====================
        // ⛔ BUG CORRIGE (« dans les luckyblock a luck 0 il semblerait que
        //    j'aie pas de structure a part l'everest ? ») :
        //    AVANT  -- creator Inoxtag + luck >= 70 => Everest GARANTI, sans
        //              passer par STRUCTURE_TRIGGER_CHANCE, puis `return`
        //              sec : un Creator Event Inoxtag ne pouvait JAMAIS
        //              donner une structure normale. Les 5 autres devaient
        //              passer le filtre 17 % PUIS se partager le tirage.
        //              Probabilite par bloc casse a luck infusee 0 (table
        //              T220) : Everest 0,1476 % contre 0,0753 % pour une
        //              structure normale precise -> l'Everest sortait
        //              presque 2x plus souvent que n'importe quelle autre,
        //              et sortait quasiment toujours en premier.
        //    APRES  -- un SEUL tirage commun aux 6 structures : luck >= 70,
        //              puis STRUCTURE_TRIGGER_CHANCE, puis choix UNIFORME
        //              parmi celles qui ne sont pas encore apparues sur la
        //              map. Chacune des 6 vaut exactement 0,0866 % par bloc
        //              a luck 0. La regle « une seule fois par monde »
        //              par structure est conservee telle quelle.
        if (branchLuck < STRUCTURE_LUCK_MIN) return;
        if (!(level instanceof ServerLevel sl)) return;
        // T313 : les 6 structures uniques par monde (Citadelle, Observatoire, Dragon, Cirque,
        // Epave, Everest) sont des structures de surface Overworld (ciel ouvert + biomes Overworld).
        // Dans le Nether (plafond bedrock scelle a Y=127 + maxBuildHeight=256 / 16 sections) ou l'End,
        // un Lucky Block casse ne doit ni les poser hors-sol sur le toit du Nether ni bruler leur quota unique !
        if (sl.dimension() != Level.OVERWORLD || sl.dimensionType().hasCeiling()) return;

        EverestFlagData flagData = EverestFlagData.get(sl);

        // Clin d'oeil conserve : Inoxtag annonce que sa montagne est deja la.
        if ((creator != null && creator.isInoxtag()) && flagData.everestSpawned && nearestSP != null) {
            sendActionBarToPlayer(nearestSP, "§8§oL'Everest ne peut être invoqué qu'une fois par monde...");
        }

        if (random.nextInt(100) >= STRUCTURE_TRIGGER_CHANCE) return;

        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < NORMAL_STRUCTURE_IDS.length; i++) {
            if (!structureUnavailable(flagData, NORMAL_STRUCTURE_IDS[i])) available.add(NORMAL_STRUCTURE_IDS[i]);
        }
        if (!structureUnavailable(flagData, EVEREST_STRUCTURE_ID)) available.add(EVEREST_STRUCTURE_ID);
        if (available.isEmpty()) {
            return; // Les 6 structures sont deja apparues sur cette map -> STOP !
        }

        int structId = available.get(random.nextInt(available.size()));

        if (structId == EVEREST_STRUCTURE_ID) {
            STRUCTURE_RESERVED.add(EVEREST_STRUCTURE_ID);   // T226 : reservation, pas consommation

            result.hasStructure = true;
            result.structureId = EVEREST_STRUCTURE_ID;
            result.title = "§b§l✦ EVEREST D'INOXTAG ✦ - " + structureTitleSuffix(result);
            result.titleColorCode = "b";
            result.particles.add("FIREWORK");
            result.particles.add("DRAGON_BREATH");

            if (nearestSP != null) {
                sendTitle(nearestSP, "§b§l⛰ EVEREST ⛰",
                        "§fLa montagne sacrée est apparue... une seule fois !");
            }
            return;
        }

        STRUCTURE_RESERVED.add(structId);                   // T226 : reservation, pas consommation

        result.hasStructure = true;
        result.structureId = structId;

        String structName = switch (structId) {
            case 0 -> "Citadelle Sylvestre";
            case 1 -> "Observatoire Féerique";
            case 2 -> "Guilde du Dragon";
            case 3 -> "Cirque Maudit";
            case 4 -> "Ruine d'Épave Fantôme";
            default -> "Structure Mystique";
        };
        result.title = structName + " - " + structureTitleSuffix(result);
        result.titleColorCode = switch (structId) {
            case 0 -> "2"; case 1 -> "b"; case 2 -> "4";
            case 3 -> "d"; case 4 -> "3";
            default -> "6";
        };
        result.particles.add("FIREWORK");
        result.particles.add("DRAGON_BREATH");
    }

    // ==================== LUCKY TOOLS — FORCED TOP OUTCOME ====================
    /**
     * Point d'entree optionnel pour le Lucky Idol de Laink. Produit un veritable
     * objet ETERNAL (luck 100) sans remplacer le comportement normal du mod et
     * sans laisser les bonus secondaires remplacer la recompense principale.
     */
    public static TestProcedure.BranchResult generateLuckyToolsEternalPayload(
            Level level, double x, double y, double z, RandomSource random) {
        if (level != null && !level.isClientSide()) {
            deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());
        }
        final int eternalLuck = 100;
        RankProfile profile = profileFromLuck(eternalLuck);

        TestProcedure.BranchResult result = new TestProcedure.BranchResult();
        result.negative = false;
        result.branchLuck = eternalLuck;
        result.rank = profile.rank;
        result.titleColorCode = profile.colorCode;
        result.items = new ArrayList<>();
        result.particles = new ArrayList<>();
        result.hasStructure = false;
        result.structureId = -1;

        // Une recompense ETERNAL directe : aucun remplacement par le stash de
        // pommes, la potion bonus ou un autre evenement secondaire.
        handleNormalItem(result, eternalLuck, random, profile);
        result.particles.add("TOTEM_OF_UNDYING");

        // 5 % seulement : remplace le reward positif final par le Bo Cube.
        tryReplacePositiveRewardWithBoCube(result, profile, random);
        return result;
    }

    // ==================== GENERATE PAYLOAD ====================
    public static TestProcedure.BranchResult generatePayload(Level level, double x, double y, double z,
                                                             int mainLuck, RandomSource random) {
        if (level != null && !level.isClientSide()) {
            deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());
        }
        ServerPlayer nearestSP = findNearestServerPlayer(level, x, y, z, 32.0);
        int rolledLuck = mainLuck;

        LuckContext ctx = computeLuckContext(rolledLuck, nearestSP, level, x, y, z, random);
        int branchLuck = mainLuck;

        RankProfile profile = profileFromLuck(branchLuck);
        TestProcedure.BranchResult result = new TestProcedure.BranchResult();
        result.negative = false;
        result.branchLuck = branchLuck;
        result.rank = profile.rank;
        result.titleColorCode = profile.colorCode;
        result.items = new ArrayList<>();
        result.particles = new ArrayList<>();
        result.hasStructure = false;
        result.structureId = -1;

        // ==================== 🔧 DEBUG STRUCTURE LOOP ====================
        if (DEBUG_STRUCTURE_LOOP) {
            return generateDebugStructureLoop(level, x, y, z, result, profile, random);
        }
        // ==================== FIN DEBUG ====================

        // Luck exacte 56 : recompense unique, garantie et sans enchantement.
        // Le return anticipe empeche les Creator Events, Apple Stash, chiens,
        // Healer's Sword et autres bonus de remplacer les Lunettes du Patron.
        if (branchLuck == 56) {
            ItemStack glasses = new ItemStack(
                    DeepLuckyBlockModItems.LUNETTES_DU_PATRON_HELMET.get());
            result.items.add(glasses);
            result.title = "Lunettes du Patron";
            result.particles.add("END_ROD");
            result.particles.add("ENCHANT");

            // 5 % seulement : meme ce reward special peut etre remplace.
            tryReplacePositiveRewardWithBoCube(result, profile, random);

            if (nearestSP != null) incrementPityEternal(nearestSP);
            updateStreak(nearestSP, level, x, y, z, true);
            return result;
        }

        if (rolledLuck <= CURSED_LUCK_MAX && !ctx.forceCreator() && !ctx.forceEternal()) {
            updateStreak(nearestSP, level, x, y, z, true);
            return generateBrokenCursedItem(random, result, rolledLuck);
        }

        boolean creatorEvent;
        if (ctx.forceCreator() || ctx.forceEternal()) {
            creatorEvent = true;
        } else {
            creatorEvent = random.nextInt(100) < creatorEventChance(branchLuck);
        }

        CreatorProfile pickedCreator = null;
        if (creatorEvent) {
            pickedCreator = handleCreatorEvent(result, level, x, y, z, branchLuck, ctx, nearestSP, random, profile);
        } else {
            handleNormalItem(result, branchLuck, random, profile);
        }

        // T224 : le tirage de structure s'applique aux DEUX branches.
        maybeTriggerStructure(result, level, branchLuck, nearestSP, random, pickedCreator);

        if (nearestSP != null) {
            if (branchLuck >= 100) {
                nearestSP.getPersistentData().putInt(TAG_PITY_ETERNAL, 0);
            } else {
                incrementPityEternal(nearestSP);
            }
        }

        // "Soit l'un soit l'autre" : si un bonus se déclenche, on retire l'item principal
        int preBonusCount = result.items.size();
        BonusLootOutcome bonusOutcome = addBonusLoot(level, x, y, z, result, random);
        if (bonusOutcome.triggered()) {
            for (int i = 0; i < preBonusCount && !result.items.isEmpty(); i++) {
                result.items.remove(0);
            }
        }

        // ===== ÉVÉNEMENT CHIENS (noms = chiens des créateurs ; collier = couleur du nom ; Team Claquette si x3) =====
        boolean dogsTriggered = false;
        if (random.nextInt(100) < BONUS_WOLVES_CHANCE && level instanceof ServerLevel sl && nearestSP != null) {
            dogsTriggered = true;
            // Les chiens sont la recompense : aucun objet ne les accompagne.
            result.items.clear();
            // Easter egg : si pile 3 chiens -> Ben, Jerry & Samousse (Team Claquette)
            String[] claquetteTrio = {"§d§lBen", "§d§lJerry", "§d§lSamousse"};

            int wolfCount = 1 + random.nextInt(8);
            final int wc = wolfCount;
            final ServerPlayer owner = nearestSP;
            final List<DogPet> shuffledDogs = new ArrayList<>(Arrays.asList(DOG_PETS));
            Collections.shuffle(shuffledDogs, new java.util.Random(random.nextLong()));

            Runnable prevWolf = result.deferredAction;
            result.deferredAction = () -> {
                if (prevWolf != null) {
                    try { prevWolf.run(); } catch (Exception ignored) {}
                }

                final boolean isTrio = (wc == 3);

                for (int wi = 0; wi < wc; wi++) {
                    net.minecraft.world.entity.animal.Wolf wolf =
                            new net.minecraft.world.entity.animal.Wolf(EntityType.WOLF, sl);

                    double wAngle = random.nextDouble() * Math.PI * 2;
                    double wDist = 2.0 + random.nextDouble() * 3.0;

                    wolf.moveTo(
                            x + Math.cos(wAngle) * wDist,
                            y + 1,
                            z + Math.sin(wAngle) * wDist,
                            random.nextFloat() * 360,
                            0
                    );

                    wolf.tame(owner);
                    wolf.setOrderedToSit(false);
                    wolf.setPersistenceRequired();
                    wolf.getPersistentData().putBoolean("slb_gift_wolf", true);

                    String name;
                    net.minecraft.world.item.DyeColor collar;
                    if (isTrio) {
                        name = claquetteTrio[wi];
                        collar = net.minecraft.world.item.DyeColor.PINK;
                    } else {
                        DogPet pet = shuffledDogs.get(wi % shuffledDogs.size());
                        name = "§" + pet.color() + "§l" + pet.name();
                        collar = pet.collar();
                    }
                    wolf.setCustomName(Component.literal(name));
                    wolf.setCustomNameVisible(true); // [1.21.1] setCollarColor privé -> nom du collier non modifiable (comportement best-effort)

                    int randomHealth = 3 + random.nextInt(18);

                    try {
                        var maxHealthAttribute = wolf.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                        if (maxHealthAttribute != null) {
                            maxHealthAttribute.setBaseValue(randomHealth);
                        }
                        wolf.setHealth((float) randomHealth);
                    } catch (Exception ignored) {
                        wolf.setHealth(Math.min((float) randomHealth, wolf.getMaxHealth()));
                    }

                    sl.addFreshEntity(wolf);
                    // Laisse le chien près du joueur : s'il spawn dans un mur ou
                    // tombe, il se téléporte à côté de son maître (comme les chats).
                    keepDogNearOwner(sl, wolf, owner.getUUID());
                }

                String msg = isTrio ? "§d§l🐕 Ben, Jerry & Samousse (Team Claquette) débarquent !"
                                    : "§a§l🐕 " + wc + " chiens te rejoignent !";
                sendActionBarNearby(sl, x, y, z, msg);
                sl.playSound(null, x, y, z, SoundEvents.WOLF_WHINE, SoundSource.NEUTRAL, 1.0f, 1.2f);
            };
        }

        // Healer's Sword — 5%, niveau des enchants selon la luck du tirage.
        // 🔒 STRICTEMENT deux enchantements : unbreaking (niveau selon luck)
        //    + blood_transfusion (le soin du mod, niveau selon luck).
        //    AUCUN autre enchantement, AUCUNE malédiction.
        if (!dogsTriggered && !bonusOutcome.nonItemOnly() && random.nextInt(100) < 5) {
            RankProfile healerProfile = profileFromLuck(branchLuck);
            Item specialItem = materialByLuck(ItemType.SWORD, branchLuck, random);
            ItemStack special = new ItemStack(specialItem);

            // Niveau selon la luck : +1 palier tous les 20 points (1 -> 5 max).
            int healerLevel = Math.max(1, Math.min(5, 1 + branchLuck / 20));

            addEnchantment(special, "minecraft:unbreaking", healerLevel);
            addEnchantment(special, "deep_lucky_block:blood_transfusion", healerLevel);

            String healerColor = healerProfile.colorCode;
            special.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§" + healerColor + "§lHealer's Magic Sword"));

            result.items.add(0, special);
            result.title = "§" + healerColor + "§l✦ Healer's Sword (" + healerProfile.display + ") ✦";
            result.titleColorCode = healerColor;
        }

        if (result.items.isEmpty() && !bonusOutcome.nonItemOnly() && !dogsTriggered) {
            ItemStack fallback = createItemForType(ItemType.SWORD, branchLuck, branchLuck, random, false, false);
            profile.applyName(fallback, "Blade");
            result.items.add(fallback);
        }

        // 5 % seulement : remplace le reward positif final par le Bo Cube.
        tryReplacePositiveRewardWithBoCube(result, profile, random);

        // 🏆 Achievement "Premier ULTIMATE" : le grade max = ETERNAL (luck 100),
        //    l'objet possédant l'unique enchantement level 100.
        if (branchLuck >= 100 && nearestSP != null) {
            DeepLuckyBlockAdvancements.grantFirstUltimate(nearestSP);
        }

        // 🏆 Achievement "un grade obtenu" : enregistre le grade du tirage positif.
        if (nearestSP != null) {
            for (String gk : GRADE_KEYS_POSITIVE) {
                if (branchLuck >= TestProcedure.QualityTier.valueOf(gk.toUpperCase(java.util.Locale.ROOT)).minLuck
                        && branchLuck <= TestProcedure.QualityTier.valueOf(gk.toUpperCase(java.util.Locale.ROOT)).maxLuck) {
                    DeepLuckyBlockAdvancements.grantGrade(nearestSP, gk);
                    break;
                }
            }
        }

        updateStreak(nearestSP, level, x, y, z, true);

        return result;
    }

    // ==================== 🔧 DEBUG STRUCTURE LOOP ====================
    private static TestProcedure.BranchResult generateDebugStructureLoop(Level level, double x, double y, double z,
                                                                         TestProcedure.BranchResult result,
                                                                         RankProfile profile, RandomSource random) {
        int structId = Math.abs(DEBUG_STRUCTURE_COUNTER.getAndIncrement() % TOTAL_STRUCTURES);

        result.hasStructure = true;
        result.structureId = structId;

        String structName = switch (structId) {
            case 0 -> "§2§l✦ [DEBUG] Citadelle Sylvestre ✦";
            case 1 -> "§b§l✦ [DEBUG] Observatoire Féerique ✦";
            case 2 -> "§4§l✦ [DEBUG] Sanctuaire du Dragon ✦";
            case 3 -> "§d§l✦ [DEBUG] Cirque Maudit ✦";
            case 4 -> "§3§l✦ [DEBUG] Épave Fantôme ✦";
            case 5 -> "§b§l✦ [DEBUG] Everest d'Inoxtag ✦";
            default -> "§6§l✦ [DEBUG] Structure ✦";
        };

        result.title = structName;
        result.titleColorCode = switch (structId) {
            case 0 -> "2"; case 1 -> "b"; case 2 -> "4";
            case 3 -> "d"; case 4 -> "3"; case 5 -> "b";
            default -> "6";
        };
        result.particles.add("FIREWORK");
        result.particles.add("DRAGON_BREATH");

        ItemStack debugItem = createItemForType(ItemType.SWORD, 50, 50, random, false, false);
        debugItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§l[DEBUG] Test Structure #" + structId));
        result.items.add(debugItem);

        // System.out.println("[SLB-DEBUG] Structure loop → ID=" + structId + " (" + structName + ")"); // logs debug désactivés

        ServerPlayer sp = findNearestServerPlayer(level, x, y, z, 32.0);
        if (sp != null) {
            sendActionBarToPlayer(sp, "§e[DEBUG] Structure ID=" + structId + " → " + structName);
        }

        return result;
    }

    // ==================== CREATOR EVENT CHANCE CURVE ====================
    private static int creatorEventChance(int luck) {
        if (luck <= 50) {
            return CREATOR_CHANCE_AT_LUCK_0
                + (int) Math.round((CREATOR_CHANCE_AT_LUCK_50 - CREATOR_CHANCE_AT_LUCK_0) * luck / 50.0);
        }
        return CREATOR_CHANCE_AT_LUCK_50
            + (int) Math.round((CREATOR_CHANCE_AT_LUCK_100 - CREATOR_CHANCE_AT_LUCK_50) * (luck - 50) / 50.0);
    }

    // ==================== ABRÉVIATIONS DE RANG (suffixe des items Creator Event) ====================
    // Code court ajouté à la fin du nom des items de Creator Event pour montrer
    // le grade du tirage. Calée sur la VRAIE liste des 17 grades
    // (TestProcedure.QualityTier) :
    //   DOOMED=[DO]  CURSED=[CU]  USED=[U]  WORN=[W]  COMMON=[C]
    //   UNCOMMON=[UC]  UNUSUAL=[UN]  REMARKABLE=[RM]  OUTSTANDING=[OS]
    //   RARE=[R]  EPIC=[E]  EXCEPTIONAL=[EX]  LEGENDARY=[L]
    //   MYTHIC=[M]  RELIC=[RL]  MASTERWORK=[MW]  ETERNAL=[ET]
    // Couleur : reprise de la couleur d'origine du rang (QualityTier.colorCode),
    // via le même helper simpleColorCode que RankProfile — pas de gris fixe.
    private static String rankSuffix(int luck) {
        TestProcedure.QualityTier tier = TestProcedure.QualityTier.getQualityTier(luck);
        String code = switch (tier) {
            case DOOMED -> "DO";
            case CURSED -> "CU";
            case USED -> "U";
            case WORN -> "W";
            case COMMON -> "C";
            case UNCOMMON -> "UC";
            case UNUSUAL -> "UN";
            case REMARKABLE -> "RM";
            case RARE -> "R";
            case OUTSTANDING -> "OS";
            case EPIC -> "E";
            case EXCEPTIONAL -> "EX";
            case LEGENDARY -> "L";
            case MYTHIC -> "M";
            case RELIC -> "RL";
            case MASTERWORK -> "MW";
            case ETERNAL -> "ET";
        };
        return " §" + simpleColorCode(tier.colorCode) + "[" + code + "]";
    }

    // ==================== HANDLE CREATOR EVENT ====================
    private static CreatorProfile handleCreatorEvent(TestProcedure.BranchResult result, Level level,
                                           double x, double y, double z, int branchLuck,
                                           LuckContext ctx, ServerPlayer nearestSP,
                                           RandomSource random, RankProfile profile) {
        CreatorProfile creator = CREATORS.get(random.nextInt(CREATORS.size()));
        boolean isMaximeBiaggi = creator.isMaximeBiaggi();
        ItemType itemType = pickRandomItemType(random, branchLuck);
        String creatorName = creator.getNameForType(itemType, random);
        String finalName = applyGrammarPrefix(creatorName, itemType);

        ItemStack creatorItem = createItemForType(itemType, branchLuck, branchLuck, random, true, isMaximeBiaggi);
        // Couleur du nom = couleur du RANG (comme les récompenses normales),
        // SAUF les noms à lettres uniques (ex. "TENGA MASTER") qui gardent leur
        // multicolore. Le badge "[CODE]" y est toujours ajouté.
        String displayedName = hasUniqueLetterColors(finalName)
                ? finalName
                : recolorNameToRank(finalName, profile.colorCode);
        creatorItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(displayedName + rankSuffix(branchLuck)));

        addArrowsIfRanged(itemType, result, random);
        result.items.add(creatorItem);

        // 🏆 Achievement "item de Créateur" : compte 1/5/10 + hub "Le Syndicat des Créateurs".
        if (nearestSP != null) {
            DeepLuckyBlockAdvancements.grantCreatorItem(nearestSP);
        }

        result.title = "Creator Event - " + stripColor(finalName);
        result.particles.add("END_ROD");
        result.particles.add("ENCHANT");

        return creator;
    }


    private static void handleNormalItem(TestProcedure.BranchResult result, int branchLuck,
                                         RandomSource random, RankProfile profile) {
        ItemType type = pickRandomItemType(random, branchLuck);
        ItemStack stack = createItemForType(type, branchLuck, branchLuck, random, false, false);
        // Ne pas écraser un nom déjà posé (ex: Lunettes du Patron remplaçant un casque).
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            profile.applyName(stack, displayName(type));
        }

        addArrowsIfRanged(type, result, random);
        result.items.add(stack);

        result.title = profile.display + " Reward";
        result.particles.add("ENCHANT");
    }

    public static void notifyNegativeBranch(Level level, double x, double y, double z) {
        ServerPlayer sp = findNearestServerPlayer(level, x, y, z, 32.0);
        updateStreak(sp, level, x, y, z, false);
        if (sp != null) {
            incrementPityEternal(sp);
        }
    }

    // ==================== STREAK LOGIC ====================
    private static void updateStreak(ServerPlayer player, Level level, double x, double y, double z, boolean positive) {
        if (player == null || !(level instanceof ServerLevel sl)) return;

        CompoundTag data = player.getPersistentData();
        int current = data.getInt(TAG_STREAK);

        if (positive) {
            current = (current >= 0) ? current + 1 : 1;
        } else {
            current = (current <= 0) ? current - 1 : -1;
        }
        data.putInt(TAG_STREAK, current);

        if (current >= STREAK_THRESHOLD) {
            long expiry = sl.getServer().getTickCount() + STREAK_BUFF_DURATION_TICKS;
            data.putLong(TAG_BUFF_EXPIRY_TICK, expiry);
            data.putInt(TAG_STREAK, 0);

            sendActionBarToPlayer(player, "§a§l✦ Lucky Streak! +" + STREAK_LUCK_BONUS + " Luck for 60s ✦");
            sl.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.5f);

            // 🏆 Achievement "Série de Chance" + Chaîne de Chance (3 séries) + hub "Le Destin".
            DeepLuckyBlockAdvancements.onLuckyStreak(player);
        } else if (current <= -NEGATIVE_STREAK_THRESHOLD) {
            data.putInt(TAG_STREAK, 0);
            sendActionBarToPlayer(player, "§c§l✦ Bad Luck Streak... fate has abandoned you ✦");
            sl.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8f, 0.5f);

            // 🏆 Achievement "Série de Malchance" + hub "Le Destin".
            DeepLuckyBlockAdvancements.triggerNegativeStreak(player);
        }
    }

    // ==================== PITY HELPERS ====================
    private static void incrementPityEternal(ServerPlayer sp) {
        CompoundTag data = sp.getPersistentData();
        data.putInt(TAG_PITY_ETERNAL, data.getInt(TAG_PITY_ETERNAL) + 1);
    }

    // ==================== CHIEN : LAISSE AU JOUEUR ====================
    // Les chiens offerts par le mod restent près de leur maître : s'ils se
    // retrouvent dans un mur (spawn en souterrain) ou tombent, ils se
    // téléportent sur un bloc solide à côté du joueur toutes les secondes.
    private static void keepDogNearOwner(ServerLevel level,
                                         net.minecraft.world.entity.animal.Wolf wolf,
                                         java.util.UUID ownerId) {
        TestProcedure.schedule(level, TestProcedure.currentTick(level) + 20, () -> {
            if (wolf.isRemoved() || !wolf.isAlive()) return;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null) return;

            if (owner.isAlive() && wolf.level() == owner.level()
                    && wolf.distanceToSqr(owner) > 12.0 * 12.0) {
                BlockPos feet = owner.blockPosition();
                boolean teleported = false;
                for (int dx = -2; dx <= 2 && !teleported; dx++) {
                    for (int dz = -2; dz <= 2 && !teleported; dz++) {
                        BlockPos p = feet.offset(dx, 0, dz);
                        if (level.getBlockState(p).isAir()
                                && level.getBlockState(p.above()).isAir()
                                && level.getBlockState(p.below()).blocksMotion()) {
                            wolf.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                            teleported = true;
                        }
                    }
                }
                if (!teleported) {
                    wolf.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                }
            }
            keepDogNearOwner(level, wolf, ownerId);
        });
    }

    // ==================== PLAYER LOOKUP + TITLE HELPERS ====================
    private static ServerPlayer findNearestServerPlayer(Level level, double x, double y, double z, double radius) {
        if (!(level instanceof ServerLevel sl)) return null;
        Player p = sl.getNearestPlayer(x, y, z, radius, false);
        return (p instanceof ServerPlayer sp) ? sp : null;
    }

    private static void sendTitle(ServerPlayer sp, String title, String subtitle) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
    }

    private static void sendActionBarToPlayer(ServerPlayer sp, String message) {
        sp.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(message)));
    }

    private static void sendActionBarNearby(ServerLevel level, double x, double y, double z, String message) {
        Component text = Component.literal(message);
        AABB box = new AABB(x - 32, y - 16, z - 32, x + 32, y + 16, z + 32);
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            if (p instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    // ==================== GRAMMAR PREFIX ====================
    private static String applyGrammarPrefix(String name, ItemType type) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder prefix = new StringBuilder();
        int idx = 0;
        while (idx < name.length() - 1 && name.charAt(idx) == '§') {
            prefix.append(name.charAt(idx)).append(name.charAt(idx + 1));
            idx += 2;
        }
        String rest = name.substring(idx);
        String lr = rest.toLowerCase(Locale.ROOT);
        if (lr.startsWith("la ") || lr.startsWith("le ") || lr.startsWith("les ")
         || lr.startsWith("l'") || lr.startsWith("tenga")) {
            return name;
        }
        String article = getGrammarArticle(rest, type);
        if (article.isEmpty()) return name;
        return prefix.toString() + article + rest;
    }

    private static String getGrammarArticle(String rest, ItemType type) {
        String lr = rest.toLowerCase(Locale.ROOT);
        if (lr.startsWith("hache") || lr.startsWith("houe")) {
            return "La ";
        }
        if (lr.startsWith("heaume") || lr.startsWith("haubert")) {
            return "Le ";
        }
        if (lr.matches("^[aàâäeéèêëiîïoôöuùûü].*")) {
            return "L'";
        }
        return switch (type) {
            case HELMET, CHESTPLATE, TRIDENT, SHIELD -> "Le ";
            case LEGGINGS, BOOTS -> "Les ";
            case SWORD, PICKAXE, AXE, SHOVEL, HOE -> "La ";
            case BOW, CROSSBOW -> "L'";
            default -> "La ";
        };
    }

    /**
     * Détecte les noms de créateur à LETTRES UNIQUES (un code couleur par
     * lettre, ex. les "TENGA MASTER" de Hctuan : §c§lT§f§lE§c§lN...).
     * Ces noms sont des exceptions volontaires : on conserve leur multicolore.
     */
    private static boolean hasUniqueLetterColors(String name) {
        if (name == null || name.isEmpty()) return false;
        int colorCodeCount = 0;
        for (int i = 0; i + 1 < name.length(); i++) {
            if (name.charAt(i) == '§') {
                char c = Character.toLowerCase(name.charAt(i + 1));
                if (Character.isDigit(c) || (c >= 'a' && c <= 'f')) {
                    colorCodeCount++;
                }
            }
        }
        return colorCodeCount >= 2;
    }

    /**
     * Remplace la couleur de BASE du nom par la couleur du rang, en conservant
     * le texte et les styles (gras, souligné, etc.). Seule la toute première
     * couleur "§X" est recolorée : le reste du nom (y compris les articles
     * grammaticaux ajoutés) est préservé.
     */
    private static String recolorNameToRank(String name, String rankColorCode) {
        if (name == null || name.isEmpty() || name.charAt(0) != '§') return name;
        char rc = (rankColorCode != null && rankColorCode.length() > 0) ? rankColorCode.charAt(0) : 'f';
        StringBuilder out = new StringBuilder();
        int i = 0;
        boolean recolored = false;
        while (i + 1 < name.length() && name.charAt(i) == '§') {
            char c = name.charAt(i + 1);
            char lc = Character.toLowerCase(c);
            boolean isColor = Character.isDigit(lc) || (lc >= 'a' && lc <= 'f');
            if (isColor && !recolored) {
                out.append('§').append(rc);
                recolored = true;
            } else {
                out.append('§').append(c);
            }
            i += 2;
        }
        out.append(name.substring(i));
        return out.toString();
    }

    private static void addArrowsIfRanged(ItemType type, TestProcedure.BranchResult result, RandomSource random) {
        if (type == ItemType.BOW || type == ItemType.CROSSBOW) {
            int arrows = 1 + random.nextInt(6);
            result.items.add(new ItemStack(Items.ARROW, arrows));
        }
    }

    // ==================== BONUS LOOT ====================
    private record BonusLootOutcome(boolean triggered, boolean nonItemOnly) {}

    private static BonusLootOutcome addBonusLoot(Level level, double x, double y, double z,
                                                  TestProcedure.BranchResult result, RandomSource random) {
        boolean triggered = false;
        int itemCountBeforeBonus = result.items.size();

        if (random.nextInt(100) < BONUS_GOLDEN_APPLES_CHANCE) {
            int golden = random.nextInt(17);
            int notch = random.nextInt(7);
            if (golden > 0) result.items.add(new ItemStack(Items.GOLDEN_APPLE, golden));
            if (notch > 0) result.items.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, notch));
            triggered = true;
        }

        if (random.nextInt(100) < BONUS_APPLE_BUNDLE_CHANCE) {
            final double cx = x, cy = y, cz = z;
            final long seed = random.nextLong();
            Runnable previous = result.deferredAction;
            result.deferredAction = () -> {
                if (previous != null) {
                    try { previous.run(); } catch (Exception ignored) {}
                }
                placeAppleChestInWorld(level, cx, cy, cz, RandomSource.create(seed));
            };

            // Apple Stash est une recompense complete : aucun objet principal,
            // aucune pomme bonus et aucune potion ne doit etre donne en plus.
            while (result.items.size() > itemCountBeforeBonus) {
                result.items.remove(result.items.size() - 1);
            }
            return new BonusLootOutcome(true, true);
        }

        // Firarain Potion : 1-7 exemplaires (9% de probabilité)
        if (random.nextInt(100) < BONUS_FIRARAIN_POTION_CHANCE) {
            int potionCount = FIRARAIN_POTION_DROP_MIN
                + random.nextInt(FIRARAIN_POTION_DROP_MAX - FIRARAIN_POTION_DROP_MIN + 1);
            ItemStack potions = createFirarainPotionStack(potionCount);
            if (!potions.isEmpty()) {
                result.items.add(potions);
                triggered = true;
            }
        }

        return new BonusLootOutcome(triggered, false);
    }

    // ==================== FIRARAIN POTION LOOT ====================
    private static ItemStack createFirarainPotionStack(int count) {
        try {
            if (FIRARAIN_POTION_CACHE == null || FIRARAIN_POTION_CACHE == Items.AIR) {
                ResourceLocation rl = ResourceLocation.tryParse(FIRARAIN_POTION_ID);
                if (rl != null) {
                    FIRARAIN_POTION_CACHE = BuiltInRegistries.ITEM.get(rl);
                }
                if (FIRARAIN_POTION_CACHE == null || FIRARAIN_POTION_CACHE == Items.AIR) {
                    rl = ResourceLocation.tryParse("deep_lucky_block:firarain_potion");
                    if (rl != null) {
                        FIRARAIN_POTION_CACHE = BuiltInRegistries.ITEM.get(rl);
                    }
                }
                if (FIRARAIN_POTION_CACHE == null || FIRARAIN_POTION_CACHE == Items.AIR) {
                    for (Item it : BuiltInRegistries.ITEM) {
                        ResourceLocation key = BuiltInRegistries.ITEM.getKey(it);
                        if (key != null && key.getPath().equals("firarain_potion")) {
                            FIRARAIN_POTION_CACHE = it;
                            break;
                        }
                    }
                }
            }
            if (FIRARAIN_POTION_CACHE == null || FIRARAIN_POTION_CACHE == Items.AIR) {
                return ItemStack.EMPTY; // id introuvable -> drop ignoré, pas de crash
            }
            return new ItemStack(FIRARAIN_POTION_CACHE, count);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void placeAppleChestInWorld(Level level, double x, double y, double z, RandomSource random) {
        if (!(level instanceof ServerLevel sl)) return;

        BlockPos targetPos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (!sl.getBlockState(targetPos).isAir() && !sl.getBlockState(targetPos).canBeReplaced()) {
            targetPos = targetPos.above();
            if (!sl.getBlockState(targetPos).isAir() && !sl.getBlockState(targetPos).canBeReplaced()) {
                targetPos = new BlockPos((int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z));
                if (!sl.getBlockState(targetPos).isAir() && !sl.getBlockState(targetPos).canBeReplaced()) {
                    return;
                }
            }
        }

        sl.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), 3);

        if (sl.getBlockEntity(targetPos) instanceof ChestBlockEntity chest) {
            // PORT 1.21.1 : setCustomName n'existe plus sur BlockEntity (nom du coffre non settable).

            int size = chest.getContainerSize();

            // 1) Pommes normales : 0-3 par case (nerf demandé, plus de 64 par case).
            for (int i = 0; i < size; i++) {
                int count = random.nextInt(APPLE_STASH_NORMAL_MAX + 1);
                chest.setItem(i, count > 0
                        ? new ItemStack(Items.APPLE, count)
                        : ItemStack.EMPTY);
            }

            // 2) Ordre de slots aléatoire pour placer les pommes spéciales
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < size; i++) slots.add(i);
            Collections.shuffle(slots, new java.util.Random(random.nextLong()));
            int cursor = 0;

            // 3) 0-8 pommes dorées à un slot random
            int goldenCount = random.nextInt(APPLE_STASH_GOLDEN_MAX + 1);
            if (goldenCount > 0 && cursor < slots.size()) {
                chest.setItem(slots.get(cursor++), new ItemStack(Items.GOLDEN_APPLE, goldenCount));
            }

            // 4) 0-3 pommes dorées enchantées à un slot random
            int notchCount = random.nextInt(APPLE_STASH_NOTCH_MAX + 1);
            if (notchCount > 0 && cursor < slots.size()) {
                chest.setItem(slots.get(cursor++), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, notchCount));
            }

            // 5) Pommes moddées : seulement 30% de présence chacune, 1-2 exemplaires
            //    (avant : quasi toutes présentes -> trop cheat).
            List<Item> otherApples = getAppleItems();
            otherApples.removeIf(it -> it == Items.APPLE || it == Items.GOLDEN_APPLE
                    || it == Items.ENCHANTED_GOLDEN_APPLE);
            Collections.shuffle(otherApples, new java.util.Random(random.nextLong()));
            for (Item apple : otherApples) {
                if (cursor >= slots.size()) break;
                if (random.nextInt(100) >= APPLE_STASH_MODDED_CHANCE_PERCENT) continue;
                int count = 1 + random.nextInt(APPLE_STASH_MODDED_MAX);
                chest.setItem(slots.get(cursor++), new ItemStack(apple, count));
            }

            chest.setChanged();
        }

        sl.playSound(null, targetPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
        sl.playSound(null, targetPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.5f);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            targetPos.getX() + 0.5, targetPos.getY() + 1.2, targetPos.getZ() + 0.5,
            20, 0.5, 0.5, 0.5, 0.05);
        sl.sendParticles(ParticleTypes.END_ROD,
            targetPos.getX() + 0.5, targetPos.getY() + 1.5, targetPos.getZ() + 0.5,
            10, 0.3, 0.3, 0.3, 0.02);

        sendActionBarNearby(sl, x, y, z, "§d§l✦ §fHere's a stash of apples for you~ §d§l✦");
    }

    private static List<Item> getAppleItems() {
        if (APPLE_ITEMS_CACHE != null) return new ArrayList<>(APPLE_ITEMS_CACHE);
        List<Item> found = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) continue;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) continue;
            String id = key.toString().toLowerCase(Locale.ROOT);
            if (id.contains("apple") && (item.components().get(net.minecraft.core.component.DataComponents.FOOD) != null)) {
                found.add(item);
            }
        }
        APPLE_ITEMS_CACHE = new ArrayList<>(found);
        return found;
    }

    // ==================== BROKEN CURSED ITEM ====================
    // Règles (retours de test en jeu) :
    // - Les curses viennent du VRAI pool du type (JSON curse + vanilla) : les
    //   curses moddées du mod sont donc incluses.
    // - 1 à 3 curses, pondérées 1,1,2,2,3 (souvent peu, parfois 3).
    // - + 1 à 3 enchantements NORMaux, comme un item de luck COMMON (vrai
    //   pipeline enchant, malédictions désactivées ici car posées à part).
    // - Durabilité restante aléatoire : 1 à 30 max.
    // - Le rang (CURSED) est visible dans le nom, et un lore explique POURQUOI
    //   l'item est maudit (liste des curses réellement posées). Ce lore est
    //   désactivé pour la publication via CURSED_LORE_ENABLED.
    private static TestProcedure.BranchResult generateBrokenCursedItem(RandomSource random,
                                                                       TestProcedure.BranchResult result,
                                                                       int luck) {
        // Sous-rang négatif : BROKEN est le pire, puis CURSED, puis USED.
        // Les frontières restent confinées à la branche luck 0-10.
        String conditionRank = luck <= 2 ? "BROKEN" : luck <= 6 ? "CURSED" : "USED";
        result.rank = itemRankByNameOrFallback(conditionRank, TestProcedure.ItemRank.CURSED);
        result.branchLuck = luck;
        result.titleColorCode = conditionRank.equals("BROKEN") ? "4"
                : conditionRank.equals("CURSED") ? "c" : "8";
        result.title = "§" + result.titleColorCode + "§l" + conditionRank + " Item";

        ItemType type = pickRandomItemType(random, luck);
        Item item = switch (type) {
            case HELMET -> Items.LEATHER_HELMET;
            case CHESTPLATE -> Items.LEATHER_CHESTPLATE;
            case LEGGINGS -> Items.LEATHER_LEGGINGS;
            case BOOTS -> Items.LEATHER_BOOTS;
            case SWORD -> Items.WOODEN_SWORD;
            case PICKAXE -> Items.WOODEN_PICKAXE;
            case AXE -> Items.WOODEN_AXE;
            case SHOVEL -> Items.WOODEN_SHOVEL;
            case HOE -> Items.WOODEN_HOE;
            case BOW -> Items.BOW;
            case CROSSBOW -> Items.CROSSBOW;
            case TRIDENT -> Items.TRIDENT;
            case SHIELD -> Items.SHIELD;
                case FISHING_ROD -> Items.FISHING_ROD;
                case ELYTRA -> Items.ELYTRA;
            default -> Items.WOODEN_SWORD;
        };

        ItemStack stack = new ItemStack(item);

        // 1) Enchantements normaux 1-3, comme un item de luck COMMON (luck 20).
        applyEnchantmentsFromConfig(stack, type, 20, 20, random, false, false, null, false);

        // 2) Curses lues sur la ligne JSON du sous-rang exact.
        List<String> curses = new ArrayList<>(loadEnchantCategories(
                type.configKey, conditionRank, luck, Set.of("curse")));
        Collections.shuffle(curses, new java.util.Random(random.nextLong()));
        int curseCount = switch (conditionRank) {
            case "BROKEN" -> 3;                  // le plus maudit
            case "CURSED" -> 2 + random.nextInt(2); // 2-3
            default -> 1 + random.nextInt(2);    // USED : 1-2
        };
        int appliedCurses = 0;
        for (String id : curses) {
            if (appliedCurses >= curseCount) break;
            addEnchantment(stack, id, 1);
            if (stackHasEnchant(stack, id)) appliedCurses++;
        }

        // 3) Durabilité RESTANTE demandée : BROKEN=1, CURSED=30-60, USED=10-30.
        if (stack.isDamageableItem()) {
            int wantedRemaining = switch (conditionRank) {
                case "BROKEN" -> 1;
                case "CURSED" -> 30 + random.nextInt(31);
                default -> 10 + random.nextInt(21);
            };
            int remaining = Math.max(1, Math.min(wantedRemaining, stack.getMaxDamage() - 1));
            stack.setDamageValue(Math.max(1, stack.getMaxDamage() - remaining));
        }

        // 4) Nom avec le sous-rang visible + lore "pourquoi maudit".
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§" + result.titleColorCode + "§l"
                + conditionRank + " " + displayName(type)));
        applyCursedLore(stack, type);

        addArrowsIfRanged(type, result, random);
        result.items.add(stack);
        result.particles.add("SMOKE");
        result.particles.add("SOUL");
        return result;
    }

    /** Lore explicatif : liste les malédictions réellement posées sur l'item. */
    private static void applyCursedLore(ItemStack stack, ItemType type) {
        if (!CURSED_LORE_ENABLED) return;
        if (stack == null || stack.isEmpty()) return;
        Set<String> curseIds = new HashSet<>(getAllConfiguredCurses(type));
        // PORT 1.21.1 : le lore est le composant ItemLore (plus de NBT "display")
        List<Component> lore = new ArrayList<>();
        deepluckyblock.util.DeepEnchant.map(stack).forEach((en, lvl) -> {
            ResourceLocation key = deepluckyblock.util.DeepEnchant.getKeySafe(en);
            if (key == null) return;
            if (!curseIds.contains(key.toString().toLowerCase(Locale.ROOT))) return;
            lore.add(Component.literal("§c☠ ").append(Enchantment.getFullname(deepluckyblock.util.DeepEnchant.holder(en), lvl)));
        });
        if (!lore.isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.LORE,
                    new net.minecraft.world.item.component.ItemLore(lore));
        }
    }

    // ==================== PUBLIC API ====================
    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  ENCHANTEMENT D'UNE PIÈCE EXISTANTE (armure Samouss du trio, etc.)   ║
    // ╠══════════════════════════════════════════════════════════════════════╣
    // ║  Contrairement à generate(), cette méthode NE CRÉE PAS d'objet :     ║
    // ║  elle prend la pièce telle quelle et lui applique uniquement les     ║
    // ║  enchantements + le nom coloré correspondant au luck donné.          ║
    // ║  L'item d'origine (SAMOUSS_HELMET, etc.) est donc conservé.          ║
    // ╚══════════════════════════════════════════════════════════════════════╝

    /**
     * Enchante une pièce déjà existante selon un luck donné, sans changer l'item.
     *
     * @param stack la pièce à enchanter (modifiée sur place et renvoyée)
     * @param luck  0-100 : détermine le rang, le nombre et la puissance
     * @param slot  emplacement d'armure, pour choisir le bon pool JSON
     */
    /**
     * Équipe un mob d'une armure ENCHANTÉE mais INVISIBLE.
     *
     * Les pièces sont de vraies pièces (protection + enchantements actifs côté
     * serveur), mais rien n'est rendu à l'écran : les renderers du trio
     * n'ajoutent plus de HumanoidArmorLayer. Aucun scintillement d'enchantement
     * n'apparaît non plus, puisque c'est la couche d'armure qui le dessinait.
     *
     * Chaque emplacement tire SON PROPRE luck entre luckMin et luckMax.
     * dropChance = 0 : cette armure technique n'est jamais lâchée, pour ne pas
     * interférer avec le loot Samouss géré par dropCustomDeathLoot().
     *
     * Les pièces utilisées sont volontairement en CUIR : c'est le matériau le
     * plus discret si jamais une couche de rendu était réactivée par erreur,
     * et il n'ajoute presque aucune armure de base — la protection provient
     * des enchantements, pas du matériau.
     */
    public static void equipHiddenEnchantedArmor(net.minecraft.world.entity.Mob mob,
                                                 int luckMin, int luckMax, RandomSource random) {
        equipHiddenEnchantedArmor(mob, luckMin, luckMax, random, false);
    }

    /**
     * Variante avec CHOIX DU MATÉRIAU. {@code netherite=true} : les 4 pièces
     * sont en Netherite (plus grosse base d'armure vanilla que le cuir),
     * toujours rendues INVISIBLES (aucun HumanoidArmorLayer pour ces mobs)
     * et toujours enchantées comme les pièces cuir ci-dessous.
     */
    public static void equipHiddenEnchantedArmor(net.minecraft.world.entity.Mob mob,
                                                 int luckMin, int luckMax, RandomSource random,
                                                 boolean netherite) {
        if (mob == null) return;
        if (!mob.level().isClientSide()) {
            deepluckyblock.util.DeepEnchant.rememberServerAccess(mob.level().registryAccess());
        }
        try {
            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            for (EquipmentSlot slot : slots) {
                ItemStack piece = new ItemStack(netherite ? switch (slot) {
                    case HEAD  -> Items.NETHERITE_HELMET;
                    case CHEST -> Items.NETHERITE_CHESTPLATE;
                    case LEGS  -> Items.NETHERITE_LEGGINGS;
                    default    -> Items.NETHERITE_BOOTS;
                } : switch (slot) {
                    case HEAD  -> Items.LEATHER_HELMET;
                    case CHEST -> Items.LEATHER_CHESTPLATE;
                    case LEGS  -> Items.LEATHER_LEGGINGS;
                    default    -> Items.LEATHER_BOOTS;
                });
                int luck = rollLuck(luckMin, luckMax, random);
                enchantExistingArmor(piece, luck, slot, random);
                mob.setItemSlot(slot, piece);
                mob.setDropChance(slot, 0.0f);   // jamais lâchée
            }
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "[deep_lucky_block] Armure invisible impossible a equiper", t);
        }
    }

    /**
     * Enchante l'armure DÉJÀ PORTÉE par un mob, sans la remplacer.
     *
     * Utilisé pour Samouss, qui possède son propre set : les pièces existantes
     * sont conservées telles quelles et reçoivent seulement des enchantements.
     * Les emplacements vides sont ignorés.
     */
    public static void enchantWornArmor(net.minecraft.world.entity.Mob mob,
                                        int luckMin, int luckMax, RandomSource random) {
        if (mob == null) return;
        if (!mob.level().isClientSide()) {
            deepluckyblock.util.DeepEnchant.rememberServerAccess(mob.level().registryAccess());
        }
        try {
            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            for (EquipmentSlot slot : slots) {
                ItemStack worn = mob.getItemBySlot(slot);
                if (worn == null || worn.isEmpty()) continue;   // rien a cet emplacement
                enchantExistingArmor(worn, rollLuck(luckMin, luckMax, random), slot, random);
            }
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "[deep_lucky_block] Enchantement de l'armure portee impossible", t);
        }
    }

    /** Luck aléatoire borné à [luckMin, luckMax], lui-même contenu dans 0..100. */
    private static int rollLuck(int luckMin, int luckMax, RandomSource random) {
        int lo = Math.max(0, Math.min(100, luckMin));
        int hi = Math.max(lo, Math.min(100, luckMax));
        return lo + random.nextInt(hi - lo + 1);
    }

    public static ItemStack enchantExistingArmor(ItemStack stack, int luck,
                                                 EquipmentSlot slot, RandomSource random) {
        if (stack == null || stack.isEmpty()) return stack;
        try {
            ItemType type = slotToItemType(slot);
            int clamped = Math.max(0, Math.min(100, luck));
            applyEnchantmentsFromConfig(stack, type, clamped, clamped, random, false, false);

            // Nom coloré au rang correspondant, en conservant le nom d'origine
            // de la pièce (ex. « Casque de Samouss ») s'il en possède un.
            RankProfile profile = profileFromLuck(clamped);
            String baseName = stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                    ? stack.getHoverName().getString()
                    : stack.getItem().getDescription().getString();
            profile.applyName(stack, baseName);
        } catch (Throwable t) {
            // Un échec d'enchantement ne doit jamais empêcher le drop de la pièce.
            com.mojang.logging.LogUtils.getLogger().error(
                    "[deep_lucky_block] Enchantement de piece impossible", t);
        }
        return stack;
    }

    public static ItemStack generate(Level level, int luck, TestProcedure.QualityTier tier, RandomSource random) {
        if (level != null && !level.isClientSide()) {
            deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());
        }
        int branchLuck = luck;
        ItemType type = pickRandomItemType(random, luck);
        ItemStack stack = createItemForType(type, luck, branchLuck, random, false, false);
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            RankProfile profile = profileFromLuck(branchLuck);
            profile.applyName(stack, displayName(type));
        }
        return stack;
    }

    public static ItemStack generateSpecific(Level level, int luck, TestProcedure.QualityTier tier,
                                             RandomSource random, EquipmentSlot slot) {
        if (level != null && !level.isClientSide()) {
            deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());
        }
        return createItemForType(slotToItemType(slot), luck, luck, random, false, false);
    }

    public static ItemStack generateSpecific(Level level, int luck, TestProcedure.QualityTier tier,
                                             RandomSource random, String itemType) {
        if (level != null && !level.isClientSide()) {
            deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());
        }
        return createItemForType(normalizeItemType(itemType), luck, luck, random, false, false);
    }

    // ==================== CREATE ITEM ====================
    private static ItemStack createItemForType(ItemType type, int mainLuck, int branchLuck,
                                               RandomSource random, boolean creatorMode, boolean isMaximeBiaggi) {
        // 👓 Lunettes du Patron : 10% de chance qu'un CASQUE soit remplacé par les
        // Lunettes du Patron, enchantées exactement comme un casque (pool helmet,
        // niveaux et rank du tirage). Hors Creator Event (l'item garde son identité).
        boolean patronGlasses = type == ItemType.HELMET
                && !creatorMode
                && random.nextInt(100) < PATRON_GLASSES_CHANCE;

        Item item = patronGlasses
                ? DeepLuckyBlockModItems.LUNETTES_DU_PATRON_HELMET.get()
                : materialByLuck(type, branchLuck, random);
        ItemStack stack = new ItemStack(item);
        deepluckyblock.util.DeepNbt.markLuckyBlockOrigin(stack);
        applyEnchantmentsFromConfig(stack, type, mainLuck, branchLuck, random, creatorMode, isMaximeBiaggi);

        if (patronGlasses) {
            RankProfile profile = profileFromLuck(Math.max(mainLuck, branchLuck));
            profile.applyName(stack, "Lunettes du Patron");
        }
        return stack;
    }

    private static Item materialByLuck(ItemType type, int luck, RandomSource random) {
        if (luck < 35) {
            return switch (type) {
                case HELMET -> random.nextInt(10) == 0 ? Items.LEATHER_HELMET : Items.IRON_HELMET;
                case CHESTPLATE -> random.nextInt(10) == 0 ? Items.LEATHER_CHESTPLATE : Items.IRON_CHESTPLATE;
                case LEGGINGS -> random.nextInt(10) == 0 ? Items.LEATHER_LEGGINGS : Items.IRON_LEGGINGS;
                case BOOTS -> random.nextInt(10) == 0 ? Items.LEATHER_BOOTS : Items.IRON_BOOTS;
                case SWORD -> random.nextBoolean() ? Items.STONE_SWORD : Items.IRON_SWORD;
                case PICKAXE -> Items.IRON_PICKAXE;
                case AXE -> Items.IRON_AXE;
                case SHOVEL -> Items.IRON_SHOVEL;
                case HOE -> random.nextBoolean() ? Items.STONE_HOE : Items.IRON_HOE;
                case BOW -> Items.BOW;
                case CROSSBOW -> Items.CROSSBOW;
                case TRIDENT -> Items.TRIDENT;
                case SHIELD -> Items.SHIELD;
                case FISHING_ROD -> Items.FISHING_ROD;
                case ELYTRA -> Items.ELYTRA;
                default -> Items.IRON_SWORD;
            };
        }
        if (luck < 75) {
            boolean diamond = luck >= 55 || random.nextInt(100) < Math.max(5, (luck - 30) * 2);
            if (diamond) return switch (type) {
                case HELMET -> Items.DIAMOND_HELMET;
                case CHESTPLATE -> Items.DIAMOND_CHESTPLATE;
                case LEGGINGS -> Items.DIAMOND_LEGGINGS;
                case BOOTS -> Items.DIAMOND_BOOTS;
                case SWORD -> Items.DIAMOND_SWORD;
                case PICKAXE -> Items.DIAMOND_PICKAXE;
                case AXE -> Items.DIAMOND_AXE;
                case SHOVEL -> Items.DIAMOND_SHOVEL;
                case HOE -> Items.DIAMOND_HOE;
                case BOW -> Items.BOW;
                case CROSSBOW -> Items.CROSSBOW;
                case TRIDENT -> Items.TRIDENT;
                case SHIELD -> Items.SHIELD;
                case FISHING_ROD -> Items.FISHING_ROD;
                case ELYTRA -> Items.ELYTRA;
                default -> Items.DIAMOND_SWORD;
            };
            return switch (type) {
                case HELMET -> Items.GOLDEN_HELMET;
                case CHESTPLATE -> Items.GOLDEN_CHESTPLATE;
                case LEGGINGS -> Items.GOLDEN_LEGGINGS;
                case BOOTS -> Items.GOLDEN_BOOTS;
                case SWORD -> Items.GOLDEN_SWORD;
                case PICKAXE -> Items.GOLDEN_PICKAXE;
                case AXE -> Items.GOLDEN_AXE;
                case SHOVEL -> Items.GOLDEN_SHOVEL;
                case HOE -> Items.GOLDEN_HOE;
                case BOW -> Items.BOW;
                case CROSSBOW -> Items.CROSSBOW;
                case TRIDENT -> Items.TRIDENT;
                case SHIELD -> Items.SHIELD;
                case FISHING_ROD -> Items.FISHING_ROD;
                case ELYTRA -> Items.ELYTRA;
                default -> Items.GOLDEN_SWORD;
            };
        }
        return switch (type) {
            case HELMET -> Items.NETHERITE_HELMET;
            case CHESTPLATE -> Items.NETHERITE_CHESTPLATE;
            case LEGGINGS -> Items.NETHERITE_LEGGINGS;
            case BOOTS -> Items.NETHERITE_BOOTS;
            case SWORD -> Items.NETHERITE_SWORD;
            case PICKAXE -> Items.NETHERITE_PICKAXE;
            case AXE -> Items.NETHERITE_AXE;
            case SHOVEL -> Items.NETHERITE_SHOVEL;
            case HOE -> Items.NETHERITE_HOE;
            case BOW -> Items.BOW;
            case CROSSBOW -> Items.CROSSBOW;
            case TRIDENT -> Items.TRIDENT;
            case SHIELD -> Items.SHIELD;
                case FISHING_ROD -> Items.FISHING_ROD;
                case ELYTRA -> Items.ELYTRA;
            default -> Items.NETHERITE_SWORD;
        };
    }

    // ==================== ENCHANTMENTS ====================
    private static boolean isBasicLightness(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).contains(LIGHTNESS_KEYWORD);
    }

    private static boolean isLeap(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).contains(LEAP_KEYWORD);
    }

    private static boolean isFeatherFalling(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).equals(FEATHER_FALLING_ID);
    }

    private static boolean isProtectionFamily(String id) {
        if (id == null) return false;
        String n=id.toLowerCase(Locale.ROOT);
        return n.equals("minecraft:protection")
                || n.equals("minecraft:fire_protection")
                || n.equals("minecraft:blast_protection")
                || n.equals("minecraft:projectile_protection");
    }

    private static boolean isBrimstone(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).equals(BRIMSTONE_ID);
    }

    private static boolean isDelay(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).contains("delay");
    }

    private static boolean isUnbreakable(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).contains("unbreakable");
    }

    // FIX (rapporte en jeu : "tes enchantements coeurs, je les ai eu direct
    // level 3 ! il faut repartir par level rank de luck, certains disponibles
    // au level 1 dans des ranks tres bas") : les 7 coeurs elementaires
    // (fire/water/earth/wind/electric/shadow/light_heart) sont declares avec
    // maxLevel=3 (voir ModEnchantments) ET n'apparaissent dans le pool JSON
    // qu'a partir du rang MYTHIC (luck 94+, voir enchant_chestplate.json) --
    // sans charte dediee, rollModdedLevelFor plafonnait deja a 3 des MYTHIC
    // (maxAllowedModded=10, mais min avec le maxLevel REEL de l'enchant = 3),
    // et guaranteeOneHighLevelEnchant forcait ensuite systematiquement un
    // niveau dans le haut de la plage (2-3) : resultat, un coeur obtenu
    // n'etait quasiment JAMAIS vu au niveau 1, exactement le bug rapporte.
    // Meme mecanisme que rollProtectionLevelForLuck : progression EXPLICITE
    // par rang, pour que le niveau 1 reste bien le cas normal au premier
    // rang ou l'enchant peut apparaitre (MYTHIC), et que 3 reste reserve
    // aux rangs vraiment sommitaux (MASTERWORK/ETERNAL).
    private static boolean isElementalHeart(String id) {
        if (id == null) return false;
        String n = id.toLowerCase(Locale.ROOT);
        String path = n.startsWith(NS_PREFIX) ? n.substring(NS_PREFIX.length()) : n;
        return deepluckyblock.enchantments.CustomEnchantment.ELEMENTAL_HEART_IDS.contains(path);
    }

    // FIX (rapporte en jeu : "fais en sorte qu'on cmmence a avoir des
    // enchantements coeurs level 1 a partir de epic, enfin le grade au
    // dessus de rare") : les 7 coeurs elementaires + Heart Resonance
    // n'apparaissaient auparavant qu'a partir de MYTHIC (voir
    // enchant_chestplate.json, rank_pools) ; ils sont desormais aussi
    // presents dans les pools EPIC et LEGENDARY (juste au-dessus de RARE),
    // toujours au niveau I au premier rang ou ils peuvent apparaitre.
    private static int rollElementalHeartLevelForLuck(int luck, RandomSource random) {
        TestProcedure.ItemRank rank = profileFromLuck(luck).rank();
        return switch (rank) {
            case EPIC, LEGENDARY, MYTHIC -> 1;         // premiers rangs d'apparition : toujours I
            case RELIC -> 1 + random.nextInt(2);       // 1-2
            case MASTERWORK -> 2 + random.nextInt(2);  // 2-3
            case ETERNAL -> 3;                         // rang absolu : toujours III (max)
            default -> 1;                              // ne devrait pas arriver (hors pool avant EPIC)
        };
    }

    private static int getMaxAllowedVanillaByLuck(int luck) {
        if (luck <= 10) return 1;  // USED : Max Lvl I
        if (luck <= 50) return 2;  // COMMON : Max Lvl II
        if (luck <= 80) return 3;  // UNCOMMON : Max Lvl III
        if (luck <= 92) return 4;  // RARE : Max Lvl IV
        if (luck <= 98) return 5;  // EPIC / MYTHIC : Max Lvl V
        if (luck == 99) return 10; // RELIC : Max Lvl X
        return 15;                 // ETERNAL : Max Lvl XV
    }

    // EXCEPTION UNIQUE : seul minecraft:protection utilise cette progression.
    // Tous les autres enchantements conservent strictement l'ancien systeme.
    private static int rollProtectionLevelForLuck(int luck, RandomSource random) {
        TestProcedure.ItemRank rank = profileFromLuck(luck).rank();
        int min;
        int max;
        switch (rank) {
            case COMMON -> { min = 1; max = 2; }
            case UNCOMMON -> { min = 1; max = 3; }
            case RARE -> { min = 2; max = 4; }
            case EPIC -> { min = 3; max = 5; }
            case LEGENDARY -> { min = 4; max = 6; }
            case MYTHIC -> { min = 5; max = 7; }
            case RELIC -> { min = 6; max = 8; }
            case MASTERWORK -> { min = 8; max = 10; }
            case ETERNAL -> { min = 10; max = 15; }
            default -> { min = 1; max = 1; }
        }
        return bounded(random, min, max);
    }

    // Charte de difficulté pour brimstone : luck 80+ uniquement, niveau max II.
    // L'ETERNAL suit la même charte (I-II) : Brimstone est toujours exclu du
    // niveau 100, réservé aux enchantements COMMON du pool JSON dédié.
    private static int rollBrimstoneLevelForLuck(int luck, RandomSource random) {
        if (luck < BRIMSTONE_MIN_LUCK) {
            return 1; // ne devrait jamais arriver : isEnchantmentAllowedForLuck bloque en amont
        }
        return 1 + random.nextInt(BRIMSTONE_MAX_LEVEL); // 1..2
    }

    private static int rollLevelFor(String id, EnchantRange range, RandomSource random, int luck) {
        if (isProtectionFamily(id)) {
            return rollProtectionLevelForLuck(luck, random);
        }
        if (isElementalHeart(id)) {
            return rollElementalHeartLevelForLuck(luck, random);
        }
        if (isBrimstone(id)) {
            return rollBrimstoneLevelForLuck(luck, random);
        }
        if (isDelay(id)) {
            // Delay : max level II, minimum I. Luck < 50 -> I, luck >= 50 -> I-II.
            return luck >= 50
                    ? DELAY_MIN_LEVEL + random.nextInt(DELAY_MAX_LEVEL - DELAY_MIN_LEVEL + 1)
                    : DELAY_MIN_LEVEL;
        }
        if (isFeatherFalling(id)) {
            int maxSteps = Math.min(FEATHER_FALLING_MAX / FEATHER_FALLING_STEP,
                                    Math.max(1, range.maxLevel() / FEATHER_FALLING_STEP + 2));
            int steps = 1 + random.nextInt(maxSteps);
            return Math.min(STANDARD_ENCHANT_MAX_LEVEL, steps * FEATHER_FALLING_STEP)
                   + random.nextInt(FEATHER_FALLING_BONUS_MAX + 1);
        }
        if (isLeap(id)) {
            int lo = Math.max(1, range.minLevel());
            int hi = Math.min(LEAP_MAX_LEVEL, Math.max(lo, range.maxLevel()));
            return bounded(random, lo, hi);
        }
        if (isBasicLightness(id)) {
            int lo = Math.max(BASIC_LIGHTNESS_MIN_LEVEL, range.minLevel());
            return bounded(random, lo, Math.max(lo, range.maxLevel()));
        }
        int maxL = range.maxLevel();
        if (id.startsWith("minecraft:")) {
            maxL = Math.min(getMaxAllowedVanillaByLuck(luck), maxL);
        }
        return rollRankAverageLevel(maxL, random);
    }

    private static int rollRankAverageLevel(int maxLevel, RandomSource random) {
        if (maxLevel <= 1) return 1;
        if (maxLevel == 2) return 1 + random.nextInt(2);

        int roll = random.nextInt(100);
        if (roll < 25) {
            return 1 + random.nextInt(Math.min(2, maxLevel));
        } else if (roll < 75) {
            int midMin = Math.max(2, maxLevel / 3);
            int midMax = Math.max(midMin, (maxLevel * 2) / 3);
            return midMin + random.nextInt(midMax - midMin + 1);
        } else {
            int highMin = Math.max(1, (maxLevel * 2) / 3);
            return highMin + random.nextInt(maxLevel - highMin + 1);
        }
    }

    // ==================== BRIDAGE SÉPARÉ : NIVEAUX MODDÉS VS VANILLA ====================
    private static int rollModdedLevelFor(String id, EnchantRange range, RandomSource random, int luck) {
        int maxAllowedModded;
        if (luck <= 10) {
            maxAllowedModded = 1; // USED : Max Lvl I
        } else if (luck <= 50) {
            maxAllowedModded = 1; // COMMON : Max Lvl I
        } else if (luck <= 80) {
            maxAllowedModded = 2; // UNCOMMON : Max Lvl II
        } else if (luck <= 92) {
            maxAllowedModded = 3; // RARE : Max Lvl III
        } else if (luck <= 96) {
            maxAllowedModded = 5; // EPIC : Max Lvl V
        } else if (luck <= 98) {
            maxAllowedModded = 10; // MYTHIC : Max Lvl X
        } else if (luck == 99) {
            maxAllowedModded = 15; // RELIC : Max Lvl XV
        } else {
            maxAllowedModded = 100; // ETERNAL : Max Lvl C / 100 (God Tier)
        }
        int rolled = rollLevelFor(id, range, random, luck);
        return Math.min(maxAllowedModded, rolled);
    }

    private static boolean shouldAllowBasicLightness(int effectiveLuck, boolean creatorMode,
                                                     boolean isMaximeBiaggi, RandomSource random) {
        if (creatorMode && isMaximeBiaggi) {
            return random.nextInt(100) < BASIC_LIGHTNESS_APPLY_CHANCE;
        }
        if (creatorMode) return false;
        if (effectiveLuck > 45) return false;
        return random.nextInt(100) < BASIC_LIGHTNESS_APPLY_CHANCE;
    }

    private static boolean isWeapon(ItemType t) {
        return t == ItemType.SWORD || t == ItemType.AXE || t == ItemType.TRIDENT;
    }

    // ⛔ T224 — retour « enorme probleme la ! j'ai eu direct une pioche mythic
    //    qui fait +31 damage mais elle tue le dragon de l'end en 5 coups !! »
    //    CAUSE : enforcePowerTier() sortait immediatement sur « !weapon && !armor »,
    //    et isWeapon() ne listait que SWORD|AXE. Une PIOCHE, une PELLE ou une HOUE
    //    pouvait donc recevoir supreme_sharpness (12,0 pt/niveau), extreme_sharpness
    //    (7,0), peerless / ultra_edge (6,0)... SANS AUCUN PLAFOND, alors que ces
    //    enchants sont bien dans OFFENSIVE_POWER_WEIGHTS (shovel_halberd,
    //    shovel_heavy_hit, shovel_blade y figurent explicitement).
    //    Un outil porteur de degats est desormais plafonne sur la bande offensive
    //    de son rang, reduite par TOOL_OFFENSIVE_FACTOR : un outil ne doit pas
    //    rivaliser avec une arme du meme grade. Mythic = bande (24 ; 38)
    //    -> plafond outil 38 x 0,5 = 19.
    private static boolean isDamageTool(ItemType t) {
        return t == ItemType.PICKAXE || t == ItemType.SHOVEL || t == ItemType.HOE;
    }

    /** Part de la bande offensive du rang accordee a un outil (non-arme). */
    private static final double TOOL_OFFENSIVE_FACTOR = 0.5;

    private static boolean isArmor(ItemType t) {
        return t == ItemType.HELMET || t == ItemType.CHESTPLATE
            || t == ItemType.LEGGINGS || t == ItemType.BOOTS;
    }

    private static boolean isTool(ItemType t) {
        return t == ItemType.PICKAXE || t == ItemType.AXE || t == ItemType.SHOVEL || t == ItemType.HOE;
    }

    // ==================== PROBABILITÉ & PROGRESSION DES ENCHANTS MODDÉS PAR PALIER ====================
    private static int computeModdedTarget(int luck, int maxModdedAvailable, RandomSource random) {
        if (maxModdedAvailable <= 0) return 0;
        int target;
        if (luck <= 25) {
            target = (random.nextInt(100) < 20) ? 1 : 0; // COMMON : 20% de chance d'1 moddé basique
        } else if (luck <= 50) {
            target = (random.nextInt(100) < 45) ? 1 : 0; // UNCOMMON : 45% de chance d'1 moddé
        } else if (luck <= 75) {
            target = 1; // RARE : 1 moddé garanti
        } else if (luck <= 90) {
            target = 1 + ((random.nextInt(100) < 35) ? 1 : 0); // EPIC : 1 garanti + 35% chance 2ème
        } else if (luck <= 95) {
            target = 1 + ((random.nextInt(100) < 50) ? 1 : 0); // MYTHIC : 1 garanti + 50% chance 2ème
        } else if (luck <= 98) {
            target = 2; // RELIC : 2 garantis
        } else if (luck == 99) {
            target = 2 + ((random.nextInt(100) < 50) ? 1 : 0); // MASTERWORK : 2 garantis + 50% chance 3ème
        } else {
            target = 3; // ETERNAL : 3 garantis (ou Lvl 100 divin)
        }
        return Math.min(maxModdedAvailable, target);
    }

    // ==================== RANK JSON ====================
    // Aucun ID d'enchantement n'est classé ici. Les fichiers JSON v2 sont la
    // source unique : Java calcule seulement le rang, lit sa ligne, puis tire
    // aléatoirement dans offensive / utility / curse.
    // Ranks originaux restaurés, avec BROKEN ajouté sous CURSED/USED pour la
    // nouvelle progression des objets fortement endommagés.
    private static final List<String> ENCHANT_RANK_ORDER = List.of(
        "BROKEN", "CURSED", "USED", "COMMON", "UNCOMMON", "RARE",
        "EPIC", "LEGENDARY", "MYTHIC", "RELIC", "MASTERWORK", "ETERNAL"
    );

    private static String enchantRankKeyForLuck(int luck) {
        // Source unique = système de ranks initial du projet. On ne redéfinit
        // plus aucune frontière positive de luck dans GenerateLuckyItemProcedure.
        String originalRank = profileFromLuck(luck).rank().name();
        return ENCHANT_RANK_ORDER.contains(originalRank) ? originalRank : "COMMON";
    }

    private static TestProcedure.ItemRank itemRankByNameOrFallback(
            String name, TestProcedure.ItemRank fallback) {
        try {
            return Enum.valueOf(TestProcedure.ItemRank.class, name);
        } catch (IllegalArgumentException ignored) {
            // Tant que BROKEN n'est pas ajouté à l'enum de TestProcedure, son
            // nom/couleur/durabilité fonctionnent et le champ enum reste CURSED.
            return fallback;
        }
    }


    // ==================== MALÉDICTIONS : JSON PAR RANG ====================
    private static final Map<String, List<String>> CURSE_POOL_CACHE = new HashMap<>();

    static List<String> loadCursePool(String itemType, int luck) {
        String rank = enchantRankKeyForLuck(luck);
        String cacheKey = itemType + "|" + rank;
        List<String> cached = CURSE_POOL_CACHE.get(cacheKey);
        if (cached != null) return new ArrayList<>(cached);
        List<String> loaded = loadEnchantCategories(itemType, rank, luck, Set.of("curse"));
        CURSE_POOL_CACHE.put(cacheKey, loaded);
        return new ArrayList<>(loaded);
    }

    private static List<String> getApplicableCurses(ItemType type, int luck) {
        return loadCursePool(type.configKey, luck);
    }

    private static List<String> getAllConfiguredCurses(ItemType type) {
        return loadAllRankCategories(type.configKey, Set.of("curse"));
    }

    private static void applyEnchantmentsFromConfig(ItemStack stack, ItemType type, int mainLuck,
                                                    int branchLuck, RandomSource random,
                                                    boolean creatorMode, boolean isMaximeBiaggi) {
        applyEnchantmentsFromConfig(stack, type, mainLuck, branchLuck, random, creatorMode, isMaximeBiaggi, null, true);
    }

    private static void applyEnchantmentsFromConfig(ItemStack stack, ItemType type, int mainLuck,
                                                    int branchLuck, RandomSource random,
                                                    boolean creatorMode, boolean isMaximeBiaggi,
                                                    Set<String> onlyCategories, boolean allowCurses) {
        int effectiveLuck = Math.max(mainLuck, branchLuck);
        String rankKey = enchantRankKeyForLuck(effectiveLuck);

        // Le JSON décide de la nature de chaque enchantement. Pour les outils,
        // Java choisit seulement la catégorie de build puis lit la bonne ligne.
        Set<String> selectedCategories = onlyCategories;
        if (selectedCategories == null && isTool(type)) {
            selectedCategories = (type == ItemType.HOE || random.nextBoolean())
                    ? Set.of("utility") : Set.of("offensive");
        }
        List<String> pool = (selectedCategories == null)
                ? loadEnchantPool(type.configKey, rankKey, effectiveLuck)
                : loadEnchantCategories(type.configKey, rankKey, effectiveLuck, selectedCategories);
        // FIX v8 (resolu) : diagnostic retire du chemin normal (spammait a
        // chaque generation d'item). Un pool vide reste une vraie anomalie
        // -> conserve en LOGGER.warn (une ligne, pas un println repete).
        if (pool.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger("deep_lucky_block").warn(
                    "[deep_lucky_block] Pool d'enchantements vide pour type={} rankKey={}", type.configKey, rankKey);
            return;
        }
        boolean isEternal = effectiveLuck >= 100;

        boolean allowBasicLightness = shouldAllowBasicLightness(effectiveLuck, creatorMode, isMaximeBiaggi, random);

        List<String> filteredPool = new ArrayList<>();
        for (String id : pool) {
            if (isBasicLightness(id) && !allowBasicLightness) continue;
            if (isLeap(id) && random.nextInt(100) >= LEAP_ACCEPT_CHANCE) continue;
            // Unbreakable : même au bon palier de luck, seulement "un peu de chance".
            if (isUnbreakable(id) && random.nextInt(100) >= UNBREAKABLE_ACCEPT_CHANCE) continue;
            filteredPool.add(id);
        }
        if (filteredPool.isEmpty()) filteredPool = pool;

        if (isEternal) {
            List<String> all = weightedOrderByRarity(filteredPool, effectiveLuck, random);

            // L'unique niveau 100 vient exclusivement du pool JSON dédié. Ce
            // pool ne contient que des enchantements MODDÉS, simples et COMMON.
            List<String> commonGodPool = loadEternalLevel100Pool(type.configKey);
            Collections.shuffle(commonGodPool, new java.util.Random(random.nextLong()));
            String chosen = tryAddEternalCommonGodEnchant(stack, commonGodPool);

            // Sécurité : si la config dédiée est vide/invalide, on cherche un
            // autre enchant moddé multi-niveaux dans le pool COMMON uniquement.
            if (chosen == null) {
                List<String> commonFallback = loadEnchantPool(type.configKey, "COMMON", 100);
                Collections.shuffle(commonFallback, new java.util.Random(random.nextLong()));
                chosen = tryAddEternalCommonGodEnchant(stack, commonFallback);
            }

            // ETERNAL reçoit 4 à 6 extras bas niveau, plus l'unique niveau 100 :
            // total normal 5 à 7, sans second niveau 100.
            Set<String> appliedEternal = getExistingEnchantIds(stack);
            int extra = 4 + random.nextInt(3);
            int added = 0;
            for (String id : all) {
                if (added >= extra) break;
                if (id.equals(chosen)) continue;
                if (appliedEternal.contains(id) || conflictsWithApplied(id, appliedEternal)) continue;
                int extraLevel = PROTECTION_ID.equals(id)
                        ? 10 + random.nextInt(6)
                        : isProtectionFamily(id) ? 4
                        : 1 + random.nextInt(5);
                addEnchantment(stack, id, extraLevel);
                if (stackHasEnchant(stack, id)) {
                    appliedEternal.add(id);
                    added++;
                }
            }
            enforcePowerTier(stack, type, effectiveLuck, random);
            return;
        }

        List<String> vanilla = new ArrayList<>();
        List<String> modded = new ArrayList<>();
        for (String id : filteredPool) {
            if (id.startsWith("minecraft:")) vanilla.add(id);
            else modded.add(id);
        }
        Collections.shuffle(vanilla, new java.util.Random(random.nextLong()));
        modded = weightedOrderByRarity(modded, effectiveLuck, random);

        EnchantRange range = enchantRange(mainLuck, branchLuck, creatorMode, random);
        int targetCount = rollEnchantCount(effectiveLuck, random);
        // Les Creator Events peuvent dépasser légèrement la base du rang, sans
        // modifier la progression normale des autres récompenses.
        if (creatorMode) {
            targetCount += random.nextInt(effectiveLuck >= 93 ? 3 : 2);
        }

        int moddedTarget = computeModdedTarget(effectiveLuck, modded.size(), random);

        // ==================== BONUS ENCHANTEMENT "★ Lucky ! ★" (10% de chance) ====================
        if (targetCount > 0 && (FORCE_BONUS_ENCHANT || random.nextInt(100) < 10)) {
            targetCount++;
            FORCE_BONUS_ENCHANT = false;
        }
        if (!creatorMode) {
            targetCount = Math.min(targetCount,
                    maxEnchantCount(profileFromLuck(effectiveLuck).rank()));
        }

        Set<String> applied = getExistingEnchantIds(stack);
        int count = 0, moddedApplied = 0, vanillaApplied = 0;
        int vanillaTarget = targetCount - moddedTarget;

        // Signature de rang : un haut rang doit se ressentir, mais UN SEUL
        // enchantement du palier courant est favorisé. Tous les autres restent
        // tirés dans le grand pool cumulatif (souvent COMMON/UNCOMMON/RARE), ce
        // qui évite les objets composés uniquement d'enchants surpuissants.
        int signatureChance = loadRankSignatureChance(type.configKey, rankKey);
        if (targetCount > 0 && random.nextInt(100) < signatureChance) {
            Set<String> signatureCategories = selectedCategories == null
                    ? Set.of("offensive", "utility") : selectedCategories;
            List<String> signaturePool = weightedOrderByRarity(
                    loadBestSignaturePool(type.configKey, rankKey, effectiveLuck, signatureCategories),
                    effectiveLuck, random);
            for (String id : signaturePool) {
                if (applied.contains(id) || conflictsWithApplied(id, applied)) continue;
                int level = id.startsWith("minecraft:")
                        ? rollLevelFor(id, range, random, effectiveLuck)
                        : rollModdedLevelFor(id, range, random, effectiveLuck);
                addEnchantment(stack, id, level);
                if (stackHasEnchant(stack, id)) {
                    applied.add(id);
                    count++;
                    if (id.startsWith("minecraft:")) {
                        vanillaApplied++;
                        vanilla.remove(id);
                    } else {
                        moddedApplied++;
                        modded.remove(id);
                    }
                    break;
                }
            }
        }

        if (targetCount > count && random.nextInt(100) < PRIORITY_ENCHANT_CHANCE) {
            String priorityId = null;
            if (isWeapon(type) && pool.contains(SHARPNESS_ID)) priorityId = SHARPNESS_ID;
            else if (isArmor(type) && pool.contains(PROTECTION_ID)) priorityId = PROTECTION_ID;

            if (priorityId != null && !applied.contains(priorityId)
                    && !conflictsWithApplied(priorityId, applied)) {
                addEnchantment(stack, priorityId, rollLevelFor(priorityId, range, random, effectiveLuck));
                if (stackHasEnchant(stack, priorityId)) {
                    applied.add(priorityId);
                    count++;
                    vanillaApplied++;
                    vanilla.remove(priorityId);
                }
            }
        }

        for (String id : modded) {
            if (moddedApplied >= moddedTarget || count >= targetCount) break;
            if (applied.contains(id) || conflictsWithApplied(id, applied)) continue;
            addEnchantment(stack, id, rollModdedLevelFor(id, range, random, effectiveLuck));
            if (stackHasEnchant(stack, id)) {
                applied.add(id); count++; moddedApplied++;
            }
        }

        for (String id : vanilla) {
            if (vanillaApplied >= vanillaTarget || count >= targetCount) break;
            if (applied.contains(id) || conflictsWithApplied(id, applied)) continue;
            addEnchantment(stack, id, rollLevelFor(id, range, random, effectiveLuck));
            if (stackHasEnchant(stack, id)) {
                applied.add(id); count++; vanillaApplied++;
            }
        }

        if (count < targetCount) {
            List<String> rem = new ArrayList<>();
            rem.addAll(modded); rem.addAll(vanilla);
            Collections.shuffle(rem, new java.util.Random(random.nextLong()));
            for (String id : rem) {
                if (count >= targetCount) break;
                if (applied.contains(id) || conflictsWithApplied(id, applied)) continue;
                addEnchantment(stack, id, rollLevelFor(id, range, random, effectiveLuck));
                if (stackHasEnchant(stack, id)) {
                    applied.add(id); count++;
                }
            }
        }

        int requiredMinimum = minEnchantCount(effectiveLuck);
        if (count < requiredMinimum && !filteredPool.isEmpty()) {
            List<String> all = new ArrayList<>(filteredPool);
            Collections.shuffle(all, new java.util.Random(random.nextLong()));
            for (String id : all) {
                if (count >= requiredMinimum) break;
                if (applied.contains(id) || conflictsWithApplied(id, applied)) continue;
                addEnchantment(stack, id, rollLevelFor(id, range, random, effectiveLuck));
                if (stackHasEnchant(stack, id)) {
                    applied.add(id); count++;
                }
            }
        }

        // ==================== UN ENCHANT DANS LE HAUT DE SA PLAGE ====================
        // Retour de test : "tu fonctionnes en moyenne". On garantit qu'UN des
        // enchantements posés soit dans le tiers supérieur de sa plage (le reste
        // garde la variété moyenne/basse). Ne dépasse JAMAIS le max réel de
        // l'enchantement (pas d'items cassés) et respecte le nombre d'enchants.
        if (count > 0 && !isEternal) {
            guaranteeOneHighLevelEnchant(stack, range, effectiveLuck, random);
        }

        // ==================== MALÉDICTIONS : DOSAGE PAR GRADE ====================
        if (allowCurses && !stack.isEmpty()) {
            // COURBE DE CURSE : USED = 1-2 garanties ; COMMON -> LEGENDARY (11-90) = 20% ; MYTHIC+ (91+) = JAMAIS
            List<String> curses = getApplicableCurses(type, effectiveLuck);
            if (!curses.isEmpty()) {
                if (effectiveLuck <= 10) {
                    int curseCount = 1 + random.nextInt(2);
                    for (int i = 0; i < curseCount; i++) {
                        addEnchantment(stack, curses.get(random.nextInt(curses.size())), 1);
                    }
                } else if (effectiveLuck <= 90) {
                    if (random.nextInt(100) < 20) {
                        addEnchantment(stack, curses.get(random.nextInt(curses.size())), 1);
                    }
                }
                // MYTHIC+ (luck >= 91) et ETERNAL : aucune curse
            }
        }

        if (!stack.isEmpty()) {
            applyKeywordLimitations(stack, random);
        }

        if (!stack.isEmpty()) {
            applyRankBoosts(stack, effectiveLuck, random);
        }

        // ==================== PALIERS DE PUISSANCE/ARMURE PAR RANG ====================
        // Dernier garde-fou, APRÈS tous les tirages/boosts ci-dessus : voir
        // Javadoc de enforcePowerTier. Empêche un objet d'un rang donné de
        // dépasser (ou de rester très en-dessous) de la fourchette de
        // puissance normale de son rang, même après cumul aléatoire de
        // plusieurs bonus indépendants.
        if (!stack.isEmpty()) {
            enforcePowerTier(stack, type, effectiveLuck, random);
        }

        // FIX v8 (resolu) : diagnostic retire (spammait a chaque generation).
    }

    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  PALIERS INVISIBLES DE PUISSANCE / ARMURE PAR RANG                   ║
    // ╠══════════════════════════════════════════════════════════════════════╣
    // ║ RAPPORTÉ EN JEU : "je recevais du legendaire plus fort qu'un rang    ║
    // ║ au-dessus [...] il faut tout bien calibrer, voir les output des      ║
    // ║ enchantements, selectionner l'enchantement selon le nombre de        ║
    // ║ degats ou de protection MAX, combler". Cause racine : chaque         ║
    // ║ enchantement est tiré et plafonné INDÉPENDAMMENT (rollLevelFor,      ║
    // ║ rollModdedLevelFor, applyRankBoosts...), mais rien ne borne jamais   ║
    // ║ la PUISSANCE TOTALE RÉSULTANTE une fois tous les enchantements       ║
    // ║ cumulés : un tirage malchanceux pour un rang haut (peu de bonus      ║
    // ║ tirés) peut produire un total plus faible qu'un tirage chanceux      ║
    // ║ pour le rang juste en-dessous (plusieurs bonus offensifs/défensifs   ║
    // ║ qui se cumulent). Les paliers ci-dessous sont invisibles au joueur   ║
    // ║ (aucun affichage, aucun nom) : ils ne font QUE re-doser après-coup   ║
    // ║ les enchantements déjà choisis, pour garantir qu'un objet d'un rang  ║
    // ║ donné reste TOUJOURS dans la fourchette min/max de puissance de ce   ║
    // ║ rang, strictement croissante de rang en rang (le pire objet d'un     ║
    // ║ rang N ne peut plus jamais dépasser le meilleur objet du rang N+1,   ║
    // ║ ni tomber sous le pire objet du rang N-1).                          ║
    // ║                                                                      ║
    // ║ MÉTRIQUE DE PUISSANCE : on réutilise EXACTEMENT les mêmes            ║
    // ║ coefficients que {@link deepluckyblock.enchantments.                ║
    // ║ RealAttributeModifierHandler} (les VRAIS bonus appliqués en jeu,     ║
    // ║ pas une estimation séparée) pour ne jamais créer un décalage entre   ║
    // ║ "ce que le palier calibre" et "ce que le joueur ressent vraiment" :  ║
    // ║  - armes (SWORD/AXE)      : somme pondérée des bonus ATTACK_DAMAGE   ║
    // ║    fixes du mod + Sharpness/Smite/Bane vanilla (mêmes coefficients   ║
    // ║    que CombatStatsCalculator) ;                                      ║
    // ║  - armures (HELMET/CHEST/LEGS/BOOTS) : somme des bonus ARMOR fixes   ║
    // ║    du mod + l'équivalent ARMOR de Protection vanilla (même ratio     ║
    // ║    4 %/niveau ≈ 1 point/niveau que RealAttributeModifierHandler).    ║
    // ║                                                                      ║
    // ║ AJUSTEMENT : si le score est hors fourchette, on ne retire/ajoute    ║
    // ║ JAMAIS d'enchantement (pour ne pas casser le nombre d'enchants déjà  ║
    // ║ calibré par rollEnchantCount) : on ne fait QUE monter/descendre le   ║
    // ║ niveau des enchantements contributeurs déjà posés (proportionnel à   ║
    // ║ leur poids, jamais sous 1, jamais au-delà de leur maxLevelPotentialFor).║
    // ╚══════════════════════════════════════════════════════════════════════╝

    /** Poids de puissance PAR NIVEAU des enchantements offensifs (ATTACK_DAMAGE), en points de dégâts. */
    private static final Map<String, Double> OFFENSIVE_POWER_WEIGHTS = Map.ofEntries(
            Map.entry("deep_lucky_block:brutal", 1.5),
            Map.entry("deep_lucky_block:mighty_strike", 1.2),
            Map.entry("deep_lucky_block:sighing_strike", 1.0),
            Map.entry("deep_lucky_block:shovel_halberd", 2.0),
            Map.entry("deep_lucky_block:shovel_heavy_hit", 2.0),
            Map.entry("deep_lucky_block:shovel_blade", 2.0),
            Map.entry("deep_lucky_block:sharp_edge", 1.0),
            Map.entry("deep_lucky_block:reinforced_sharpness", 2.0),
            Map.entry("deep_lucky_block:sharpened_sharpness", 1.5),
            Map.entry("deep_lucky_block:swift_strike", 1.0),
            Map.entry("deep_lucky_block:extension", 1.2),
            Map.entry("deep_lucky_block:blood_pact", 2.0),
            Map.entry("deep_lucky_block:peerless", 6.0),          // flat 4.0 + ~0.5*base(~4) mult
            Map.entry("deep_lucky_block:extreme_sharpness", 7.0),  // flat 5.0 + ~0.12*base mult
            Map.entry("deep_lucky_block:ultra_edge", 6.0),
            Map.entry("deep_lucky_block:advanced_sharpness", 4.0),
            Map.entry("deep_lucky_block:supreme_sharpness", 12.0),
            Map.entry("minecraft:sharpness", 1.0),
            Map.entry("minecraft:smite", 1.0),
            Map.entry("minecraft:bane_of_arthropods", 1.0));

    /** Poids de puissance PAR NIVEAU des enchantements défensifs (ARMOR/ARMOR_TOUGHNESS), en points d'armure. */
    private static final Map<String, Double> DEFENSIVE_POWER_WEIGHTS = Map.ofEntries(
            Map.entry("minecraft:protection", 0.25),        // 4%/niv * ARMOR_POINT_PER_PERCENT(0.25)
            Map.entry("deep_lucky_block:divine_protection", 2.5),
            Map.entry("deep_lucky_block:stomach_pouch", 2.5));

    /** Enchantements exclus du recalibrage (progression déjà dédiée, ne pas y toucher). */
    private static boolean isExemptFromPowerTier(String id) {
        return PROTECTION_ID.equals(id) || isElementalHeart(id) || isBrimstone(id)
                || isDelay(id) || isLeap(id) || isBasicLightness(id) || isFeatherFalling(id)
                || isUnbreakable(id);
    }

    /** Score de puissance courant de l'objet, selon la carte de poids fournie. */
    private static double currentPowerScore(ItemStack stack, Map<String, Double> weights) {
        double total = 0;
        for (Map.Entry<String, Double> w : weights.entrySet()) {
            Enchantment en = DeepEnchant.byId(w.getKey());
            if (en == null) continue;
            int lvl = DeepEnchant.getLevel(stack, en);
            if (lvl > 0) total += lvl * w.getValue();
        }
        return total;
    }

    /**
     * Fourchette [min, max] de puissance NORMALE pour un rang donné. Chaque
     * palier est strictement au-dessus du précédent (min(N+1) > max(N) n'est
     * pas garanti à l'égalité stricte partout, mais la progression reste
     * strictement croissante en moyenne/à la marge haute -- volontairement,
     * un LÉGER chevauchement de bas de plage/haut de plage voisin subsiste,
     * ex. le meilleur COMMON peut valoir un peu plus que le pire UNCOMMON,
     * mais jamais plus que le MEILLEUR UNCOMMON) : un rang élevé garde
     * toujours un plafond largement supérieur au rang précédent, exactement
     * la garantie demandée ("un legendaire ne doit plus dépasser un mythic").
     */
    private record PowerTier(double min, double max) {}

    private static PowerTier offensiveTierFor(TestProcedure.ItemRank rank) {
        return switch (rank) {
            case BROKEN, CURSED, USED -> new PowerTier(0, 3);
            case COMMON -> new PowerTier(1, 5);
            case UNCOMMON -> new PowerTier(3, 8);
            case RARE -> new PowerTier(6, 13);
            case EPIC -> new PowerTier(11, 20);
            case LEGENDARY -> new PowerTier(17, 28);
            case MYTHIC -> new PowerTier(24, 38);
            case RELIC -> new PowerTier(33, 50);
            case MASTERWORK -> new PowerTier(44, 64);
            case ETERNAL -> new PowerTier(58, 90);
            default -> new PowerTier(0, 5);
        };
    }

    private static PowerTier defensiveTierFor(TestProcedure.ItemRank rank) {
        return switch (rank) {
            case BROKEN, CURSED, USED -> new PowerTier(0, 1);
            case COMMON -> new PowerTier(0.5, 2);
            case UNCOMMON -> new PowerTier(1.5, 3.5);
            case RARE -> new PowerTier(3, 5.5);
            case EPIC -> new PowerTier(4.5, 7.5);
            case LEGENDARY -> new PowerTier(6.5, 10);
            case MYTHIC -> new PowerTier(9, 13.5);
            case RELIC -> new PowerTier(12, 18);
            case MASTERWORK -> new PowerTier(16, 24);
            case ETERNAL -> new PowerTier(21, 32);
            default -> new PowerTier(0, 2);
        };
    }

    /**
     * Recalibre APRÈS COUP la puissance totale (offensive pour les armes,
     * défensive pour les armures) pour qu'elle reste dans la fourchette
     * {@link #offensiveTierFor}/{@link #defensiveTierFor} du rang courant.
     * Ne touche JAMAIS au nombre d'enchantements ni aux enchantements exemptés
     * (voir {@link #isExemptFromPowerTier}) : ne fait QUE monter/descendre le
     * niveau des enchantements contributeurs déjà présents sur l'objet, de
     * façon proportionnelle à leur poids respectif.
     */
    private static void enforcePowerTier(ItemStack stack, ItemType type, int luck, RandomSource random) {
        if (stack == null || stack.isEmpty()) return;
        boolean weapon = isWeapon(type);
        boolean armor = isArmor(type);
        boolean tool = isDamageTool(type);
        if (!weapon && !armor && !tool) return;

        TestProcedure.ItemRank rank = profileFromLuck(luck).rank();
        Map<String, Double> weights = armor ? DEFENSIVE_POWER_WEIGHTS : OFFENSIVE_POWER_WEIGHTS;
        PowerTier tier = armor ? defensiveTierFor(rank) : offensiveTierFor(rank);
        if (tool) {
            // Bande offensive reduite : un outil reste un outil.
            tier = new PowerTier(tier.min() * TOOL_OFFENSIVE_FACTOR,
                                 tier.max() * TOOL_OFFENSIVE_FACTOR);
        }

        double score = currentPowerScore(stack, weights);

        // Liste des enchants contributeurs RÉELLEMENT présents sur l'objet
        // (hors exemptés), avec leur poids -- sert de levier d'ajustement.
        List<Map.Entry<String, Double>> contributors = new ArrayList<>();
        for (Map.Entry<String, Double> w : weights.entrySet()) {
            if (isExemptFromPowerTier(w.getKey())) continue;
            Enchantment en = DeepEnchant.byId(w.getKey());
            if (en == null) continue;
            if (DeepEnchant.getLevel(stack, en) > 0) contributors.add(w);
        }

        if (score > tier.max() && !contributors.isEmpty()) {
            // Trop puissant pour son rang : redescend proportionnellement les
            // niveaux des contributeurs jusqu'à repasser sous le plafond
            // (jamais en-dessous de 1, jamais brutalement à 0).
            double excess = score - tier.max();
            for (Map.Entry<String, Double> w : contributors) {
                if (excess <= 0.0001) break;
                Enchantment en = DeepEnchant.byId(w.getKey());
                if (en == null) continue;
                int lvl = DeepEnchant.getLevel(stack, en);
                if (lvl <= 1) continue;
                int reduceBy = Math.min(lvl - 1, (int) Math.ceil(excess / Math.max(0.01, w.getValue())));
                if (reduceBy <= 0) continue;
                int newLvl = Math.max(1, lvl - reduceBy);
                setEnchantmentLevel(stack, en, newLvl);
                excess -= (lvl - newLvl) * w.getValue();
            }
        } else if (score < tier.min() && !contributors.isEmpty()) {
            // Trop faible pour son rang (mauvais tirage cumulé) : remonte
            // proportionnellement les contributeurs déjà posés, sans jamais
            // dépasser leur maxLevelPotentialFor (jamais d'objet cassé).
            double deficit = tier.min() - score;
            EnchantRange range = enchantRange(luck, luck, false, random);
            for (Map.Entry<String, Double> w : contributors) {
                if (deficit <= 0.0001) break;
                Enchantment en = DeepEnchant.byId(w.getKey());
                if (en == null) continue;
                int lvl = DeepEnchant.getLevel(stack, en);
                int cap = maxLevelPotentialFor(w.getKey(), range, luck, en);
                if (lvl >= cap) continue;
                int increaseBy = Math.min(cap - lvl, (int) Math.ceil(deficit / Math.max(0.01, w.getValue())));
                if (increaseBy <= 0) continue;
                int newLvl = Math.min(cap, lvl + increaseBy);
                setEnchantmentLevel(stack, en, newLvl);
                deficit -= (newLvl - lvl) * w.getValue();
            }
        }
    }

    // ==================== BOOST DE RANG SELON LE GRADE ====================
    private static void applyRankBoosts(ItemStack stack, int effectiveLuck, RandomSource random) {
        TestProcedure.ItemRank rank = profileFromLuck(effectiveLuck).rank();

        int boostCount = switch (rank) {
            case COMMON -> 0;
            case UNCOMMON, RARE -> 1;
            case EPIC -> 1 + random.nextInt(2);
            case MYTHIC, RELIC, MASTERWORK -> 2;
            case ETERNAL -> 0;
            default -> 0;
        };
        if (boostCount <= 0) return;

        int bonus = switch (rank) {
            case UNCOMMON -> 1 + random.nextInt(2);    // +1 à +2
            case RARE -> 2 + random.nextInt(3);        // +2 à +4
            case EPIC -> 4 + random.nextInt(4);        // +4 à +7
            case MYTHIC -> 6 + random.nextInt(5);      // +6 à +10
            case RELIC -> 8 + random.nextInt(6);       // +8 à +13
            case MASTERWORK -> 10 + random.nextInt(7); // +10 à +16
            default -> 0;
        };
        if (bonus <= 0) return;

        Map<Enchantment, Integer> enchants = new HashMap<>(deepluckyblock.util.DeepEnchant.map(stack)); // PORT 1.21.1
        if (enchants.isEmpty()) return;

        List<Enchantment> pool = new ArrayList<>(enchants.keySet());
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));

        int boosted = 0;
        for (Enchantment ench : pool) {
            if (boosted >= boostCount) break;
            ResourceLocation boostId = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
            if (boostId != null && (PROTECTION_ID.equals(boostId.toString())
                    || BRIMSTONE_ID.equals(boostId.toString()))) {
                // Protection ET Brimstone possèdent déjà leur plage exacte par grade ;
                // ne pas leur ajouter le boost générique (+10 à +16 en MASTERWORK),
                // sinon la charte de difficulté de brimstone serait cassée.
                boosted++;
                continue;
            }
            int current = enchants.get(ench);
            setEnchantmentLevel(stack, ench, Math.min(200, current + bonus));
            boosted++;
        }
    }

    private static void setEnchantmentLevel(ItemStack stack, Enchantment ench, int newLevel) {
        if (ench == null || newLevel <= 0) return;
        ResourceLocation rl = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
        if (rl == null) return;
        removeEnchantment(stack, ench);
        addEnchantment(stack, rl.toString(), newLevel);
    }

    // ==================== GARANTIE UN ENCHANT HAUT NIVEAU ====================
    /** Monte UN enchant au hasard dans le tiers supérieur de sa plage autorisée. */
    private static void guaranteeOneHighLevelEnchant(ItemStack stack, EnchantRange range,
                                                     int luck, RandomSource random) {
        Map<Enchantment, Integer> enchants = new HashMap<>(deepluckyblock.util.DeepEnchant.map(stack)); // PORT 1.21.1
        if (enchants.isEmpty()) return;

        List<Enchantment> pool = new ArrayList<>(enchants.keySet());
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));

        for (Enchantment ench : pool) {
            ResourceLocation rl = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
            if (rl == null) continue;
            String id = rl.toString();

            int max = maxLevelPotentialFor(id, range, luck, ench);
            if (max <= 1) continue; // pas de marge -> on essaye un autre enchant

            int low = Math.max(1, (max * 2) / 3);
            int high = low + random.nextInt(Math.max(1, max - low + 1));
            if (high > enchants.get(ench)) {
                forceSetEnchantLevel(stack, ench, high);
            }
            return; // UN seul enchant garanti dans le haut de sa plage
        }
    }

    /**
     * Niveau max réellement autorisé pour un enchant sur cet item, à cette luck.
     * Ne dépasse jamais le maxLevel réel de l'enchantement (évite les items
     * "inutilisables" avec un niveau énorme caché derrière un affichage niveau I).
     */
    private static int maxLevelPotentialFor(String id, EnchantRange range, int luck, Enchantment ench) {
        // Protection a sa propre charte par grade : on n'y touche pas.
        if (PROTECTION_ID.equals(id)) return 1;
        // FIX (voir Javadoc de rollElementalHeartLevelForLuck) : return 1 ici
        // (comme Protection ci-dessus) empeche guaranteeOneHighLevelEnchant de
        // forcer un coeur elementaire fraichement tire au niveau I vers un
        // niveau superieur "haut de plage" -- la charte par rang gere deja
        // elle-meme la progression 1->3, on ne doit plus jamais la court-circuiter.
        if (isElementalHeart(id)) return 1;
        if (isBrimstone(id)) {
            return BRIMSTONE_MAX_LEVEL; // luck 80+ : max II
        }
        if (isDelay(id)) return DELAY_MAX_LEVEL;
        if (isLeap(id)) return Math.min(LEAP_MAX_LEVEL, Math.max(1, range.maxLevel()));
        if (isBasicLightness(id)) return Math.max(BASIC_LIGHTNESS_MIN_LEVEL, range.maxLevel());
        if (isFeatherFalling(id)) {
            return Math.min(STANDARD_ENCHANT_MAX_LEVEL, range.maxLevel()) + FEATHER_FALLING_BONUS_MAX;
        }
        if (id.startsWith("minecraft:")) {
            // Vanilla : cap par luck PUIS par le max réel de l'enchantement.
            return Math.min(Math.min(getMaxAllowedVanillaByLuck(luck), range.maxLevel()),
                    Math.max(1, ench.getMaxLevel()));
        }
        // Moddé : cap par palier de luck (même logique que rollModdedLevelFor),
        // puis par le max réel de l'enchantement (jamais au-delà).
        int cap;
        if (luck <= 50) cap = 1;
        else if (luck <= 80) cap = 2;
        else if (luck <= 92) cap = 3;
        else if (luck <= 96) cap = 5;
        else if (luck <= 98) cap = 10;
        else if (luck == 99) cap = 15;
        else cap = 100;
        int m = Math.min(cap, Math.max(1, range.maxLevel()));
        return Math.min(m, Math.max(1, ench.getMaxLevel()));
    }

    /** Pose un niveau précis directement dans le NBT (sans caps génériques). */
    private static void forceSetEnchantLevel(ItemStack stack, Enchantment ench, int level) {
        if (ench == null || level <= 0 || stack == null || stack.isEmpty()) return;
        try {
            removeEnchantment(stack, ench);
            deepluckyblock.util.DeepEnchant.setLevel(stack, ench, Math.min(200, level));
        } catch (Exception ignored) {}
    }

    private static void applyKeywordLimitations(ItemStack stack, RandomSource random) {
        if (stack.isEmpty()) return;

        Map<Enchantment, Integer> allEnchants = new HashMap<>(deepluckyblock.util.DeepEnchant.map(stack)); // PORT 1.21.1
        if (allEnchants.isEmpty()) return;

        Map<String, List<Enchantment>> keywordGroups = new HashMap<>();

        for (Enchantment ench : allEnchants.keySet()) {
            ResourceLocation rl = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
            if (rl == null) continue;

            String enchId = rl.toString().toLowerCase(Locale.ROOT);

            for (String keyword : new String[]{"sharpness", "sweep", "edge", "mending",
                                               "jump", "leap", "walker", "arrow",
                                               "swift", "protection"}) {
                if (enchId.contains(keyword)) {
                    keywordGroups.computeIfAbsent(keyword, k -> new ArrayList<>()).add(ench);
                    break;
                }
            }
        }

        List<Enchantment> toRemove = new ArrayList<>();

        for (Map.Entry<String, List<Enchantment>> entry : keywordGroups.entrySet()) {
            String keyword = entry.getKey();
            List<Enchantment> enchants = entry.getValue();

            if (enchants.size() <= 1) continue;

            int maxAllowed = switch (keyword) {
                case "swift" -> 2;
                case "protection" -> 3;
                default -> 1;
            };

            if (enchants.size() > maxAllowed) {
                Collections.shuffle(enchants, new java.util.Random(random.nextLong()));

                for (int i = maxAllowed; i < enchants.size(); i++) {
                    toRemove.add(enchants.get(i));
                }
            }
        }

        for (Enchantment ench : toRemove) {
            removeEnchantment(stack, ench);
        }
    }

    private static void removeEnchantment(ItemStack stack, Enchantment enchantment) {
        if (enchantment == null) return;
        // PORT 1.21.1 : plus de NBT "Enchantments" — suppression via le composant.
        deepluckyblock.util.DeepEnchant.remove(stack, enchantment);
    }

    private static final String NS_PREFIX = "deep_lucky_block:";

    /**
     * T307-RARITY : Tirage pondéré sans remise selon l'échelle à 7 paliers (COMMON -> LEGENDARY)
     * et filtrage par seuil minimal de chance ({@link deepluckyblock.Rarity#minLuck()}).
     * Les enchantements Épiques, Mythiques et Légendaires deviennent nettement plus rares à obtenir
     * tout en restant déblocables aux hauts paliers de chance.
     */
    private static List<String> weightedOrderByRarity(List<String> candidates, int effectiveLuck, RandomSource random) {
        if (candidates == null || candidates.size() <= 1) {
            return candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
        }
        List<String> eligible = new ArrayList<>(candidates.size());
        List<String> locked = new ArrayList<>();
        for (String id : candidates) {
            if (id == null) continue;
            if (id.startsWith("minecraft:") || effectiveLuck < 0 || id.toLowerCase(Locale.ROOT).contains("curse")) {
                eligible.add(id);
                continue;
            }
            deepluckyblock.Rarity r = deepluckyblock.enchantments.CustomEnchantment.getRarityByPath(id);
            if (r == null || effectiveLuck >= r.minLuck()) {
                eligible.add(id);
            } else {
                locked.add(id);
            }
        }
        List<String> ordered = weightedSampleWithoutReplacement(eligible, effectiveLuck, random);
        if (!locked.isEmpty()) {
            ordered.addAll(weightedSampleWithoutReplacement(locked, effectiveLuck, random));
        }
        return ordered;
    }

    private static List<String> weightedSampleWithoutReplacement(List<String> pool, int effectiveLuck, RandomSource random) {
        List<String> remaining = new ArrayList<>(pool);
        int n = remaining.size();
        if (n <= 1) return remaining;
        int[] weights = new int[n];
        int totalWeight = 0;
        for (int i = 0; i < n; i++) {
            String id = remaining.get(i);
            deepluckyblock.Rarity r = deepluckyblock.enchantments.CustomEnchantment.getRarityByPath(id);
            int w = (r != null) ? r.weight() : 10;
            // Léger coup de pouce aux rangs ultimes (Mythic/Relic/Masterwork/Eternal, luck >= 95)
            // tout en gardant LEGENDARY nettement plus rare que COMMON/UNCOMMON/RARE.
            if (effectiveLuck >= 97 && (r == deepluckyblock.Rarity.LEGENDARY || r == deepluckyblock.Rarity.MYTHIC)) {
                w += 2;
            } else if (effectiveLuck >= 94 && r == deepluckyblock.Rarity.LEGENDARY) {
                w += 1;
            }
            weights[i] = Math.max(1, w);
            totalWeight += weights[i];
        }
        List<String> out = new ArrayList<>(n);
        int activeCount = n;
        while (activeCount > 0 && totalWeight > 0) {
            int roll = random.nextInt(totalWeight);
            int chosenIdx = activeCount - 1;
            int acc = 0;
            for (int i = 0; i < activeCount; i++) {
                acc += weights[i];
                if (roll < acc) {
                    chosenIdx = i;
                    break;
                }
            }
            out.add(remaining.get(chosenIdx));
            totalWeight -= weights[chosenIdx];
            int lastIdx = activeCount - 1;
            remaining.set(chosenIdx, remaining.get(lastIdx));
            weights[chosenIdx] = weights[lastIdx];
            activeCount--;
        }
        return out;
    }

    private static boolean conflictsWithApplied(String id, Set<String> applied) {
        if (id == null) return false;
        String n = id.toLowerCase(Locale.ROOT);

        // Correctif (audit demande utilisateur, équilibrage des 7 cœurs
        // élémentaires) : empêche aussi la génération PROCÉDURALE (Lucky
        // Block) de graver deux cœurs élémentaires différents sur le même
        // plastron -- même règle que CustomEnchantment.checkCompatibilityMod
        // (anvil/commandes), réutilisée ici via la même liste partagée pour
        // ne jamais désynchroniser les deux chemins de vérification.
        String heartPath = n.startsWith(NS_PREFIX) ? n.substring(NS_PREFIX.length()) : n;
        if (deepluckyblock.enchantments.CustomEnchantment.ELEMENTAL_HEART_IDS.contains(heartPath)) {
            for (String a : applied) {
                if (a == null) continue;
                String al = a.toLowerCase(Locale.ROOT);
                String aPath = al.startsWith(NS_PREFIX) ? al.substring(NS_PREFIX.length()) : al;
                if (!aPath.equals(heartPath)
                        && deepluckyblock.enchantments.CustomEnchantment.ELEMENTAL_HEART_IDS.contains(aPath)) {
                    return true;
                }
            }
        }

        // Phasing annule le traitement normal des impacts : seuls les effets de
        // trajectoire/maniement explicitement autorisés peuvent l'accompagner.
        if (n.equals("deep_lucky_block:phasing")) {
            for (String a : applied) if (!PHASING_ALLOWED.contains(a.toLowerCase(Locale.ROOT))) return true;
        } else if (!PHASING_ALLOWED.contains(n)) {
            for (String a : applied) if (a.equalsIgnoreCase("deep_lucky_block:phasing")) return true;
        }
        // Pierce peut déclencher plusieurs impacts : pas d'explosion, terrain ou téléportation répétée.
        if (n.equals("deep_lucky_block:pierce")) {
            for (String a : applied) if (PIERCE_REPEAT_UNSAFE.contains(a.toLowerCase(Locale.ROOT))) return true;
        } else if (PIERCE_REPEAT_UNSAFE.contains(n)) {
            for (String a : applied) if (a.equalsIgnoreCase("deep_lucky_block:pierce")) return true;
        }
        // Toy tire environ cinq fois par seconde : bloque les effets d'impact destructeurs.
        if (n.equals("deep_lucky_block:toy")) {
            for (String a : applied) if (TOY_UNSAFE.contains(a.toLowerCase(Locale.ROOT))) return true;
        } else if (TOY_UNSAFE.contains(n)) {
            for (String a : applied) if (a.equalsIgnoreCase("deep_lucky_block:toy")) return true;
        }

        for (Set<String> group : CONFLICT_GROUPS) {
            if (!group.contains(n)) continue;
            for (String a : applied) {
                if (a == null) continue;
                String an = a.toLowerCase(Locale.ROOT);
                if (group.contains(an) && !an.equals(n)) return true;
            }
        }
        // Unbreakable (moddé, incassable) est incompatible avec TOUTES les
        // variantes de mending (mending, basic_mending, chrono_mending...).
        // Unbreaking (vanilla) RESTE compatible avec les mendings.
        if (n.contains("unbreakable")) {
            for (String a : applied) {
                if (a == null) continue;
                if (a.toLowerCase(Locale.ROOT).contains("mending")) return true;
            }
        }
        if (n.contains("mending")) {
            for (String a : applied) {
                if (a == null) continue;
                if (a.toLowerCase(Locale.ROOT).contains("unbreakable")) return true;
            }
        }
        // Unbreaking (vanilla) et Unbreakable (moddé) = mutuellement exclusifs.
        // Unbreakable rend l'item indestructible, donc Unbreaking devient inutile.
        // Les deux ID contiennent "unbreak" : on les traite comme un meme groupe de conflit.
        // Match robuste sur sous-chaine pour fonctionner quel que soit le namespace moddé.
        if (n.contains("unbreak")) {
            for (String a : applied) {
                if (a == null) continue;
                String an = a.toLowerCase(Locale.ROOT);
                if (!an.equals(n) && (an.contains("unbreak") || an.equals("eternal") || an.endsWith(":eternal"))) return true;
            }
        }
        String candBare = n.contains(":") ? n.substring(n.indexOf(':') + 1) : n;
        if (candBare.equals("eternal")) {
            for (String a : applied) {
                if (a == null) continue;
                String an = a.toLowerCase(Locale.ROOT);
                if (an.contains("unbreak") || an.contains("mending")) return true;
            }
        }
        // T306-FAMILIES : progression Enfant -> Upgrade par famille globale
        for (Set<String> family : deepluckyblock.enchantments.CustomEnchantment.UPGRADE_FAMILIES) {
            if (family.contains(candBare)) {
                for (String a : applied) {
                    if (a == null) continue;
                    String ab = a.toLowerCase(Locale.ROOT);
                    if (ab.contains(":")) ab = ab.substring(ab.indexOf(':') + 1);
                    if (!ab.equals(candBare) && family.contains(ab)) return true;
                }
            }
        }
        return false;
    }

    private static EnchantRange enchantRange(int mainLuck, int branchLuck, boolean creatorMode, RandomSource random) {
        int el = Math.max(mainLuck, branchLuck);
        int minE, maxE, minL, maxL;

        if (el <= 10) { minE = 1; maxE = 2; minL = 1; maxL = 1; }
        else if (el <= 50) { minE = 1; maxE = 2; minL = 1; maxL = 2; }
        else if (el <= 80) { minE = 1; maxE = 2; minL = 1; maxL = 3; }
        else if (el <= 92) { minE = 2; maxE = 3; minL = 1; maxL = 5; }
        else if (el <= 96) { minE = 3; maxE = 4; minL = 1; maxL = 8; }
        else if (el <= 98) { minE = 4; maxE = 5; minL = 1; maxL = 13; }
        else if (el == 99) { minE = 4; maxE = 6; minL = 1; maxL = 18; }
        else { minE = 1; maxE = 1; minL = 100; maxL = 100; }

        if (creatorMode) {
            int levelBonus;
            int countBonus;
            if (el <= 50) {
                levelBonus = random.nextInt(2);
                countBonus = random.nextInt(2);
            } else if (el <= 96) {
                levelBonus = random.nextInt(3);
                countBonus = random.nextInt(2);
            } else {
                levelBonus = random.nextInt(5);
                countBonus = random.nextInt(3);
            }
            minE += countBonus;
            maxE += countBonus;
            maxL += levelBonus;
        }
        return new EnchantRange(minE, maxE, minL, maxL);
    }

    // Progression lisible du NOMBRE d'enchantements, hors malédictions.
    // Les curses sont ajoutées séparément et ne consomment jamais ces places.
    private static int rollEnchantCount(int luck, RandomSource random) {
        TestProcedure.ItemRank rank = profileFromLuck(luck).rank();
        return switch (rank) {
            case BROKEN, CURSED, USED, COMMON -> 2;
            case UNCOMMON -> 2 + random.nextInt(2);              // 2-3
            case RARE -> 3 + random.nextInt(2);                  // 3-4
            case EPIC -> 4 + random.nextInt(2);                  // 4-5
            case LEGENDARY -> random.nextInt(100) < 5
                    ? 7 : 5 + random.nextInt(2);                 // 5-6, 5% -> 7
            case MYTHIC -> random.nextInt(100) < 8
                    ? 8 : 6 + random.nextInt(2);                 // 6-7, 8% -> 8
            case RELIC -> random.nextInt(100) < 10
                    ? 9 : 7 + random.nextInt(2);                 // 7-8, 10% -> 9
            case MASTERWORK -> random.nextInt(100) < 12
                    ? 10 : 8 + random.nextInt(2);                // 8-9, 12% -> 10
            case ETERNAL -> 5; // branche spéciale : 1 niveau 100 + extras
            default -> 2;
        };
    }

    private static int minEnchantCount(int luck) {
        return switch (profileFromLuck(luck).rank()) {
            case BROKEN, CURSED, USED, COMMON, UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
            case MYTHIC -> 6;
            case RELIC -> 7;
            case MASTERWORK -> 8;
            case ETERNAL -> 5;
            default -> 2;
        };
    }

    private static int maxEnchantCount(TestProcedure.ItemRank rank) {
        return switch (rank) {
            case BROKEN, CURSED, USED -> 2;
            case COMMON -> 3;       // 2 + éventuel Lucky
            case UNCOMMON -> 4;     // 2-3 + éventuel Lucky
            case RARE -> 5;
            case EPIC -> 6;
            case LEGENDARY -> 7;
            case MYTHIC -> 8;
            case RELIC -> 9;
            case MASTERWORK -> 10;
            case ETERNAL -> 7;
            default -> 3;
        };
    }

    // ==================== CHARGEMENT DES POOLS JSON V3 PAR RANG ====================
    static List<String> loadEnchantPool(String itemType, String rankKey, int luck) {
        return loadEnchantCategories(itemType, rankKey, luck, Set.of("offensive", "utility"));
    }

    static List<String> loadEnchantCategories(String itemType, String rankKey, int luck,
                                              Set<String> wantedCategories) {
        List<String> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean allowed = CONFIGURED_TYPES.stream().anyMatch(t -> t.configKey.equals(itemType));
        if (!allowed || rankKey == null || !ENCHANT_RANK_ORDER.contains(rankKey)) return all;

        String configFile = "config/enchant_" + itemType + ".json";
        try (InputStream stream = GenerateLuckyItemProcedure.class.getClassLoader()
                .getResourceAsStream(configFile)) {
            if (stream == null) return all;
            JsonObject root = GSON.fromJson(new InputStreamReader(stream), JsonObject.class);
            if (root == null || !root.has("rank_pools")) return all;
            JsonObject ranks = root.getAsJsonObject("rank_pools");
            if (!ranks.has(rankKey) || !ranks.get(rankKey).isJsonObject()) return all;
            JsonObject rankPool = ranks.getAsJsonObject(rankKey);
            JsonObject minimumLuck = root.has("minimum_luck")
                    && root.get("minimum_luck").isJsonObject()
                    ? root.getAsJsonObject("minimum_luck") : new JsonObject();

            for (String category : wantedCategories) {
                if (!rankPool.has(category) || !rankPool.get(category).isJsonArray()) continue;
                for (JsonElement element : rankPool.getAsJsonArray(category)) {
                    if (element == null || !element.isJsonPrimitive()) continue;
                    String id = element.getAsString();
                    if (id == null || id.isBlank()) continue;
                    String normalized = id.toLowerCase(Locale.ROOT);
                    // Seuil précis optionnel piloté par le JSON (ex. luck 80),
                    // sans jamais nommer l'enchantement dans cette procédure.
                    if (minimumLuck.has(normalized)
                            && minimumLuck.get(normalized).getAsInt() > luck) continue;
                    if (seen.add(normalized)) all.add(id);
                }
            }
        } catch (Exception exception) {
            deepluckyblock.util.DebugLog.err("[DeepLuckyBlock] Impossible de lire " + configFile
                    + " au rang " + rankKey + " : " + exception.getMessage());
        }
        return all;
    }

    // Charge uniquement les enchantements débloqués À ce rang (différence entre
    // le pool cumulatif courant et le pool cumulatif précédent).
    private static List<String> loadExactRankCategories(String itemType, String rankKey, int luck,
                                                         Set<String> categories) {
        List<String> current = loadEnchantCategories(itemType, rankKey, luck, categories);
        int index = ENCHANT_RANK_ORDER.indexOf(rankKey);
        if (index <= ENCHANT_RANK_ORDER.indexOf("COMMON")) return current;
        String previous = ENCHANT_RANK_ORDER.get(index - 1);
        Set<String> lower = new HashSet<>(loadEnchantCategories(itemType, previous, luck, categories));
        current.removeIf(lower::contains);
        return current;
    }

    private static List<String> loadBestSignaturePool(String itemType, String rankKey, int luck,
                                                       Set<String> categories) {
        int index = ENCHANT_RANK_ORDER.indexOf(rankKey);
        int commonIndex = ENCHANT_RANK_ORDER.indexOf("COMMON");
        for (int i = index; i >= commonIndex; i--) {
            String candidateRank = ENCHANT_RANK_ORDER.get(i);
            List<String> exact = loadExactRankCategories(
                    itemType, candidateRank, luck, categories);
            if (!exact.isEmpty()) return exact;
        }
        return new ArrayList<>();
    }

    private static int loadRankSignatureChance(String itemType, String rankKey) {
        String configFile = "config/enchant_" + itemType + ".json";
        try (InputStream stream = GenerateLuckyItemProcedure.class.getClassLoader()
                .getResourceAsStream(configFile)) {
            if (stream == null) return 0;
            JsonObject root = GSON.fromJson(new InputStreamReader(stream), JsonObject.class);
            if (root != null && root.has("rank_signature_chance")
                    && root.get("rank_signature_chance").isJsonObject()) {
                JsonObject chances = root.getAsJsonObject("rank_signature_chance");
                if (chances.has(rankKey)) return Math.max(0, Math.min(100,
                        chances.get(rankKey).getAsInt()));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // Pool explicitement déclaré dans le JSON pour l'unique niveau 100 ETERNAL.
    // Une intersection avec COMMON empêche une erreur de config de promouvoir
    // accidentellement un enchantement puissant.
    private static List<String> loadEternalLevel100Pool(String itemType) {
        List<String> configured = new ArrayList<>();
        String configFile = "config/enchant_" + itemType + ".json";
        try (InputStream stream = GenerateLuckyItemProcedure.class.getClassLoader()
                .getResourceAsStream(configFile)) {
            if (stream == null) return configured;
            JsonObject root = GSON.fromJson(new InputStreamReader(stream), JsonObject.class);
            if (root == null || !root.has("eternal_level_100_pool")
                    || !root.get("eternal_level_100_pool").isJsonArray()) return configured;
            Set<String> common = new HashSet<>(loadEnchantPool(itemType, "COMMON", 100));
            for (JsonElement element : root.getAsJsonArray("eternal_level_100_pool")) {
                if (element == null || !element.isJsonPrimitive()) continue;
                String id = element.getAsString();
                if (id != null && id.startsWith("deep_lucky_block:") && common.contains(id)) {
                    configured.add(id);
                }
            }
        } catch (Exception exception) {
            deepluckyblock.util.DebugLog.err("[DeepLuckyBlock] Pool ETERNAL niveau 100 invalide dans "
                    + configFile + " : " + exception.getMessage());
        }
        return configured;
    }

    // Utilisé uniquement pour reconnaître les curses déjà posées dans le lore.
    private static List<String> loadAllRankCategories(String itemType,
                                                       Set<String> wantedCategories) {
        List<String> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rank : ENCHANT_RANK_ORDER) {
            for (String id : loadEnchantCategories(itemType, rank, 100, wantedCategories)) {
                if (seen.add(id.toLowerCase(Locale.ROOT))) all.add(id);
            }
        }
        return all;
    }

    /**
     * Pose l'unique niveau 100 d'un ETERNAL. Les candidats ont déjà été limités
     * au pool COMMON du JSON. Vanilla et tout enchantement dont le niveau max
     * naturel est inférieur à III sont refusés : aucun niveau I/II ne peut être
     * artificiellement transformé en niveau 100.
     */
    private static String tryAddEternalCommonGodEnchant(ItemStack stack, List<String> candidates) {
        if (stack == null || stack.isEmpty() || candidates == null) return null;
        for (String id : candidates) {
            if (id == null || id.isBlank() || id.startsWith("minecraft:")) continue;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) continue;
            Enchantment enchantment = deepluckyblock.util.DeepEnchant.reg(deepluckyblock.util.DeepEnchant.access()).get(rl);
            if (enchantment == null || enchantment.getMaxLevel() < 3
                    || DeepEnchant.getLevel(stack, enchantment) > 0) continue;
            DeepEnchant.setLevel(stack, enchantment, 100);
            return id;
        }
        return null;
    }

    static void addEnchantment(ItemStack stack, String id, int lvl) {
        if (stack == null || stack.isEmpty() || id == null || id.isBlank() || lvl <= 0) {
            return;
        }
        ResourceLocation rl;
        try {
            String[] p = id.contains(":") ? id.split(":", 2) : new String[]{"minecraft", id};
            rl = ResourceLocation.fromNamespaceAndPath(p[0], p[1]);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("deep_lucky_block").warn(
                    "[deep_lucky_block] addEnchantment: ResourceLocation invalide id={}", id, e);
            return;
        }
        Enchantment en = deepluckyblock.util.DeepEnchant.reg(deepluckyblock.util.DeepEnchant.access()).get(rl);
        if (en == null) {
            org.slf4j.LoggerFactory.getLogger("deep_lucky_block").warn(
                    "[deep_lucky_block] addEnchantment: introuvable dans le registre id={} rl={}", id, rl);
            return;
        }
        if (DeepEnchant.getLevel(stack, en) > 0) return;

        // Caps spéciaux centralisés : ils s'appliquent aussi aux extras ETERNAL.
        if (en.getMaxLevel() == 1 || isUnbreakable(id)) {
            lvl = 1;
        } else if (isBrimstone(id)) {
            lvl = Math.min(BRIMSTONE_MAX_LEVEL, lvl);
        } else if (isDelay(id)) {
            lvl = Math.min(DELAY_MAX_LEVEL, lvl);
        } else if (isLeap(id)) {
            lvl = Math.min(LEAP_MAX_LEVEL, lvl);
        } else if (isFeatherFalling(id)) {
            lvl = Math.min(STANDARD_ENCHANT_MAX_LEVEL + FEATHER_FALLING_BONUS_MAX, lvl);
        } else if (isBasicLightness(id)) {
            lvl = Math.max(BASIC_LIGHTNESS_MIN_LEVEL, Math.min(200, lvl));
        } else if (PROTECTION_ID.equals(rl.toString())) {
            // Protection conserve sa progression spéciale par grade.
            lvl = Math.min(200, lvl);
        } else if (lvl > en.getMaxLevel()) {
            lvl = en.getMaxLevel();
        }

        deepluckyblock.util.DeepEnchant.setLevel(stack, en, Math.max(1, Math.min(200, lvl)));
        // 🎨 NOUVEAU ENVIRONNEMENT : glint COLORÉ selon le thème de l'enchantement.
        // No-op si la lib Glint & Glamour n'est pas bundle/installée.
        DeepCustomGlint.applyThemed(stack);
    }


    private static Set<String> getExistingEnchantIds(ItemStack s) {
        Set<String> set = new HashSet<>();
        // PORT 1.21.1 : getAllEnchantments() (sans arg) supprimee -> DeepEnchant.map
        deepluckyblock.util.DeepEnchant.map(s).forEach((e, l) -> {
            ResourceLocation k = deepluckyblock.util.DeepEnchant.getKeySafe(e);
            if (k != null) set.add(k.toString());
        });
        return set;
    }

    private static boolean stackHasEnchant(ItemStack s, String id) {
        if (id == null || id.isBlank()) return false;
        try {
            String[] p = id.contains(":") ? id.split(":", 2) : new String[]{"minecraft", id};
            Enchantment en = deepluckyblock.util.DeepEnchant.reg(deepluckyblock.util.DeepEnchant.access()).get(ResourceLocation.fromNamespaceAndPath(p[0], p[1]));
            return en != null && DeepEnchant.getLevel(s, en) > 0;
        } catch (Exception e) { return false; }
    }

    // ==================== TYPES ====================
    private enum ItemType {
        HELMET("helmet"), CHESTPLATE("chestplate"), LEGGINGS("leggings"), BOOTS("boots"),
        SWORD("sword"), PICKAXE("pickaxe"), AXE("axe"), SHOVEL("shovel"), HOE("hoe"),
        BOW("bow"), CROSSBOW("crossbow"), TRIDENT("trident"), SHIELD("shield"),
        FISHING_ROD("fishing_rod"), ELYTRA("elytra");
        final String configKey;
        ItemType(String k) { this.configKey = k; }
    }

    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  ÉLYTRE — DISPONIBILITÉ PROGRESSIVE SELON LE LUCK                    ║
    // ╠══════════════════════════════════════════════════════════════════════╣
    // ║  L'élytre n'apparaît plus dès luck 0 : son poids monte linéairement   ║
    // ║  depuis 0 au palier RARE jusqu'à 66 au luck 100, où l'on retrouve     ║
    // ║  EXACTEMENT le comportement actuel (4,502 %).                        ║
    // ║                                                                      ║
    // ║   luck  <58  (jusqu'à REMARKABLE) : poids  0  →  0 %    jamais        ║
    // ║   luck   62  RARE                 : poids  6  →  0,427 %              ║
    // ║   luck   68  OUTSTANDING          : poids 16  →  1,130 %              ║
    // ║   luck   78  EPIC                 : poids 31  →  2,166 %              ║
    // ║   luck   90  LEGENDARY            : poids 50  →  3,448 %              ║
    // ║   luck  100  MYTHIC+              : poids 66  →  4,502 %  (inchangé)  ║
    // ║                                                                      ║
    // ║  ELYTRA_MIN_LUCK  : luck à partir duquel l'élytre devient possible.   ║
    // ║  ELYTRA_MAX_WEIGHT: poids atteint au luck 100 (66 = état actuel).     ║
    // ╚══════════════════════════════════════════════════════════════════════╝
    private static final int ELYTRA_MIN_LUCK    = 58;   // palier RARE
    private static final int ELYTRA_MAX_WEIGHT  = 66;   // poids au luck 100
    private static final int NORMAL_TYPE_WEIGHT = 100;  // les 14 autres types

    /** Poids de l'élytre pour un luck donné : 0 sous RARE, 66 au luck 100. */
    private static int elytraWeightForLuck(int luck) {
        if (luck < ELYTRA_MIN_LUCK) return 0;
        int clamped = Math.min(100, luck);
        return (int) Math.round(ELYTRA_MAX_WEIGHT
                * (double) (clamped - ELYTRA_MIN_LUCK) / (100.0 - ELYTRA_MIN_LUCK));
    }

    /**
     * Ancienne signature conservée pour compatibilité : sans information de
     * luck, on garde le comportement historique (élytre à poids plein).
     */
    private static ItemType pickRandomItemType(RandomSource r) {
        return pickRandomItemType(r, 100);
    }

    private static ItemType pickRandomItemType(RandomSource r, int luck) {
        final int elytraWeight = elytraWeightForLuck(luck);
        int totalWeight = (CONFIGURED_TYPES.size() - 1) * NORMAL_TYPE_WEIGHT + elytraWeight;
        int roll = r.nextInt(totalWeight);
        for (ItemType type : CONFIGURED_TYPES) {
            int weight = type == ItemType.ELYTRA ? elytraWeight : NORMAL_TYPE_WEIGHT;
            if (weight == 0) continue;           // élytre indisponible à ce luck
            if (roll < weight) return type;
            roll -= weight;
        }
        return ItemType.SWORD;
    }

    private static ItemType normalizeItemType(String i) {
        if (i == null) return ItemType.SWORD;
        return switch (i.toLowerCase(Locale.ROOT)) {
            case "head", "helmet" -> ItemType.HELMET;
            case "chest", "chestplate" -> ItemType.CHESTPLATE;
            case "legs", "leggings" -> ItemType.LEGGINGS;
            case "feet", "boots" -> ItemType.BOOTS;
            case "mainhand", "sword" -> ItemType.SWORD;
            case "pickaxe" -> ItemType.PICKAXE;
            case "axe" -> ItemType.AXE;
            case "shovel" -> ItemType.SHOVEL;
            case "hoe" -> ItemType.HOE;
            case "bow" -> ItemType.BOW;
            case "crossbow" -> ItemType.CROSSBOW;
            case "trident" -> ItemType.TRIDENT;
            case "shield", "offhand" -> ItemType.SHIELD;
            case "fishing_rod", "rod" -> ItemType.FISHING_ROD;
            case "elytra", "wings" -> ItemType.ELYTRA;
            default -> ItemType.SWORD;
        };
    }

    private static String displayName(ItemType t) {
        return switch (t) {
            case HELMET -> "Helmet"; case CHESTPLATE -> "Chestplate";
            case LEGGINGS -> "Leggings"; case BOOTS -> "Boots";
            case SWORD -> "Blade"; case PICKAXE -> "Pickaxe";
            case AXE -> "Axe"; case SHOVEL -> "Shovel";
            case BOW -> "Bow"; case CROSSBOW -> "Crossbow";
            case HOE -> "Hoe"; case TRIDENT -> "Trident"; case SHIELD -> "Shield";
            case FISHING_ROD -> "Fishing Rod"; case ELYTRA -> "Elytra";
            default -> "Item";
        };
    }

    private static ItemType slotToItemType(EquipmentSlot s) {
        if (s == null) return ItemType.SWORD;
        return switch (s) {
            case HEAD -> ItemType.HELMET;
            case CHEST -> ItemType.CHESTPLATE;
            case LEGS -> ItemType.LEGGINGS;
            case FEET -> ItemType.BOOTS;
            case MAINHAND -> ItemType.SWORD;
            case OFFHAND -> ItemType.SHIELD;
            default -> ItemType.SWORD;
        };
    }

    // ==================== RANK PROFILE ====================
    private record RankProfile(TestProcedure.ItemRank rank, String display, String colorCode,
                               int rgb, boolean hex) {
        // Les deux rangs "spéciaux" gardent leur décoration (§n souligné,
        // ✦ encadrantes) mais prennent TOUS leur couleur dans colorCode, donc
        // dans TestProcedure.QualityTier : une seule source de vérité.
        //
        // ATTENTION : le glint lit le DERNIER code couleur du nom
        // (EnchantAura.parseColorCode). La décoration finale doit donc porter
        // colorCode, jamais une couleur en dur — sinon le glint ignore le rang
        // (c'était le bug : ETERNAL finissait en §6 -> glint or, MASTERWORK
        // en §f -> glint blanc).
        /**
         * Nom de l'item, dans la couleur EXACTE du rang.
         *
         * <p>Deux chemins : les 13 rangs vanilla gardent la chaîne § historique
         * (inchangée), les 4 rangs hors-palette passent par {@code TextColor}.
         * Ces derniers n'ont AUCUN code § dans leur texte — le glint reçoit donc
         * la couleur par le tag NBT posé dans {@link #applyName}.
         */
        Component formatItemName(String itemType) {
            String text = display + " " + itemType;
            if (!hex) {
                String c = "§" + colorCode;
                String legacy = switch (rank) {
                    case MASTERWORK -> c + "§l§n" + text;
                    case ETERNAL -> c + "§l\u2726 " + c + "§l§o" + text + " §r" + c + "§l\u2726";
                    default -> c + "§l" + text;
                };
                return Component.literal(legacy);
            }
            Style base = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
            return switch (rank) {
                case MASTERWORK -> Component.literal(text)
                        .withStyle(base.withBold(true).withUnderlined(true));
                case ETERNAL -> Component.literal("\u2726 ").withStyle(base.withBold(true))
                        .append(Component.literal(text).withStyle(base.withBold(true).withItalic(true)))
                        .append(Component.literal(" \u2726").withStyle(base.withBold(true)));
                default -> Component.literal(text).withStyle(base.withBold(true));
            };
        }

        /** Pose le nom ET, pour les rangs hors-palette, la couleur exacte du glint. */
        void applyName(ItemStack stack, String itemType) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, formatItemName(itemType));
            deepluckyblock.util.DeepNbt.markLuckyBlockOrigin(stack);
            if (hex) stampGlint(stack);
        }

        /** Écrit la couleur exacte du rang pour {@code EnchantAura}. */
        void stampGlint(ItemStack stack) {
            try {
                CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(stack);
                tag.putInt(GLINT_NBT_KEY, 0xFF000000 | (rgb & 0xFFFFFF));
                deepluckyblock.util.DeepNbt.commit(stack, tag);
            } catch (Throwable ignored) {
                // jamais bloquant : le glint retombe sur le parsing §
            }
        }
    }

    // Profil issu du systeme de grades UNIFIE (QualityTier). Noms, couleurs et
    // rang viennent tous de la meme source que la roulette -> plus aucun decalage.
    private static RankProfile profileFromLuck(int luck) {
        TestProcedure.QualityTier t = TestProcedure.QualityTier.getQualityTier(luck);
        return new RankProfile(t.rank, t.displayName, simpleColorCode(t.colorCode),
                t.exactRgb(), t.isHexColor());
    }

    /** Extrait le code couleur simple (1 caractere) depuis "§X" ou "§X§l". */
    private static String simpleColorCode(String colorCode) {
        if (colorCode == null || colorCode.length() < 2) return "f";
        return colorCode.substring(1, 2);
    }

    private record EnchantRange(int minEnchants, int maxEnchants, int minLevel, int maxLevel) {}

    private static int bounded(RandomSource r, int min, int max) {
        if (max <= min) return min;
        return min + r.nextInt(max - min + 1);
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "").trim();
    }

    private static class AtomicInteger {
        private int value;

        public AtomicInteger(int initialValue) {
            this.value = initialValue;
        }

        public synchronized int getAndIncrement() {
            return value++;
        }
    }

    // ==================== 🧪 DEBUG COMMAND /dlb (branche "g" uniquement) ====================
    // Usage (OP requis, permission level 2) :
    //   /dlb g <luck> <count>   -> <count> générations POSITIVES à la position du joueur.
    //
    // <luck>  = nombre précis ("5") OU plage inclusive ("5-35" -> 5 à 35 inclus).
    // <count> = nombre de générations (1 à 64), traitées 1 par 1.
    //
    // Exemples :
    //   /dlb g 10-30 5  -> 5 générations positives, luck aléatoire entre 10 et 30 inclus
    //   /dlb g 56 1     -> 1 génération à luck exacte 56 (Lunettes du Patron)
    //
    // 🚫 Structures : DEBUG_COMMAND_ACTIVE bloque TOUS les déclencheurs de structures
    //    pendant la commande (Everest + structures normales). Les bonus loot normaux
    //    (Apple Stash, chiens...) restent actifs.
    // ℹ️  La branche "g" passe par le VRAI pipeline : streak et pity évoluent comme
    //    sur un vrai tirage.
    // ℹ️  La branche "b" (/dlb b <luck> <count>) est enregistrée DIRECTEMENT dans
    //    GenerateBadProcedure (appel direct de GenerateBadProcedure.generate).
    // FIX rapport en jeu ("/dlb b fonctionne elle, par contre /dlb g est pas reconnue") :
    // GenerateBadProcedure.DEBUG_COMMAND_ENABLED (branche "b") était déjà à true, mais ce
    // flag (branche "g") était resté à false. Comme GenerateBadProcedure.onRegisterDebugCommands
    // n'enregistre "g" QUE si ce flag est true, la sous-commande "g" n'existait jamais du tout
    // (d'où "Incorrect argument for command"). Remis à true pour que /dlb g fonctionne aussi.
    public static boolean DEBUG_COMMAND_ENABLED = true; // 🔧 remettre sur false pour désactiver /dlb g avant publication finale
    public static boolean DEBUG_COMMAND_ACTIVE = false; // true uniquement pendant l'exécution

    @SubscribeEvent
    public static void onRegisterDebugCommands(RegisterCommandsEvent event) {
        // T312-DLB-LAUNCH : L'enregistrement de /dlb (g, b, probs, w, rank) est désormais
        // unifié dans deepluckyblock.commands.DlbCommand afin d'éviter tout conflit Brigadier.
    }

    // 🧪 DEBUG COMMAND /dlb (branche "g" uniquement)
    // La logique d'exécution de "g" vit ICI (runDebugCommand est public).
    // ⚠️  Pour la fiabilité de l'enregistrement, la branche "g" est AUSSI
    //     déclarée dans GenerateBadProcedure.onRegisterDebugCommands (classe
    //     dont l'enregistrement est prouvé fonctionnel). Brigadier fusionne
    //     les deux déclarations : aucun conflit, une seule commande.
    /**
     * 🎡 T227 — /dlb w &lt;luck infusee&gt; &lt;count&gt;
     *
     * Retours en jeu vises :
     *   « je tente une generation sur 30 blocs a luck 0, ca me selectionne
     *     uniquement du broken item » / « ./dlb g 0 30 » / « au lieu de donner
     *     ce qu'on est sense avoir a luck 0 »
     *   « j'ai vraiment l'impression que notre systeme de roulette est pas du
     *     tout le meme que celui qu'on a faconne ensemble en html »
     *
     * DIFFERENCE AVEC /dlb g :
     *   /dlb g &lt;rang&gt;  -> FORCE le rang final, la roue n'est jamais tiree.
     *                      /dlb g 0 30 force donc 30x le rang 0, qui est sous
     *                      CURSED_LUCK_MAX (10) : 30 items casses, c'est normal.
     *   /dlb w &lt;luck&gt;  -> tire la roue de 10 cases dans les keyframes, comme
     *                      le HTML, puis genere le resultat obtenu.
     *
     * count &lt;= 10 : les objets sont reellement donnes.
     * count &gt;  10 : mode statistique, aucun objet, juste la repartition.
     */
    /**
     * 🔬 T231 — /dlb probs &lt;luck&gt;
     *
     * Affiche les 15 probabilites que la roue utilise REELLEMENT a cette luck,
     * l'ordre de tri qui en decoule, et le plan de quotas qui en resulte.
     * Sert a verifier d'un coup d'oeil que le JSON, le Java et la roue disent
     * la meme chose, sans avoir a deduire quoi que ce soit d'un echantillon.
     */
    public static int runProbsCommand(CommandSourceStack source, int infusedLuck) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c[SLB-DEBUG] commande joueur uniquement."));
            return 0;
        }
        double[] p = TestProcedure.tierProbsAt(infusedLuck);
        TestProcedure.QualityTier[] tiers = TestProcedure.POSITIVE_TIERS_VIEW();
        Integer[] order = new Integer[p.length];
        for (int i = 0; i < p.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, c) -> {
            int cmp = Double.compare(p[c], p[a]);
            return cmp != 0 ? cmp : Integer.compare(a, c);
        });
        int skulls = infusedLuck >= 100 ? 3 : (infusedLuck > 75 ? 2 : 1);
        int ncol = 10 - skulls;
        double[] share = {0.50, 0.30, 0.20};
        int budget = Math.min(TestProcedure.quotaBudgetFor(infusedLuck), ncol - 1);
        player.sendSystemMessage(Component.literal(
            "§e── probabilites vues par la roue a luck §f" + infusedLuck + "§e ──"));
        double tot = 0;
        for (int i = 0; i < p.length; i++) tot += p[i];
        for (Integer idx : order) {
            if (p[idx] <= 0) continue;
            player.sendSystemMessage(Component.literal(String.format(
                "§7%-12s §f%7.4f%%", tiers[idx].displayName, p[idx] * 100.0)));
        }
        player.sendSystemMessage(Component.literal(String.format(
            "§8somme = %.6f  (doit valoir 1.000000)", tot)));
        StringBuilder plan = new StringBuilder("§e plan : ");
        int[] part = new int[3];
        double[] frac = new double[3];
        int placed = 0;
        for (int k = 0; k < 3; k++) {
            double raw = budget * share[k];
            part[k] = (int) Math.floor(raw);
            frac[k] = raw - part[k];
            placed += part[k];
        }
        while (placed < budget) {
            int best = 0; double bf = -1.0;
            for (int k = 0; k < 3; k++) if (frac[k] > bf) { bf = frac[k]; best = k; }
            part[best]++; frac[best] = -1.0; placed++;
        }
        // T238 : un groupe d'ex aequo (moins de 10 % d'ecart relatif) est affiche
        //        « A/B », car l'ordre y est retire au hasard a chaque roue.
        final double TIE_REL = 0.10;
        int[] grpEnd = new int[p.length];
        int gi = 0;
        while (gi < p.length) {
            int gj = gi + 1;
            double ref = p[order[gi]];
            while (gj < p.length && ref > 0.0 && (ref - p[order[gj]]) / ref <= TIE_REL) gj++;
            for (int t = gi; t < gj; t++) grpEnd[t] = gj;
            gi = gj;
        }
        for (int k = 0; k < 3; k++) {
            if (part[k] <= 0) continue;
            StringBuilder lbl = new StringBuilder();
            int start = k;
            while (start > 0 && grpEnd[start - 1] == grpEnd[k]) start--;
            for (int t = start; t < grpEnd[k]; t++) {
                if (lbl.length() > 0) lbl.append("/");
                lbl.append(tiers[order[t]].displayName);
            }
            plan.append("§f").append(lbl).append("§7x").append(part[k]).append("  ");
        }
        plan.append("§7+ ").append(ncol - placed).append(" libre(s) + ")
            .append(skulls).append(" crane(s)");
        player.sendSystemMessage(Component.literal(plan.toString()));
        return 1;
    }

    public static int runWheelCommand(CommandSourceStack source, int infusedLuck, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c[SLB-DEBUG] /dlb est une commande joueur uniquement."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        RandomSource random = level.random;
        final double x = player.getX(), y = player.getY(), z = player.getZ();
        final boolean giveItems = count <= 10;

        boolean prevActive = DEBUG_COMMAND_ACTIVE;
        DEBUG_COMMAND_ACTIVE = true; // 🚫 pas de structures pendant le test
        java.util.TreeMap<String, Integer> tally = new java.util.TreeMap<>();
        java.util.LinkedHashMap<String, Integer> order = new java.util.LinkedHashMap<>();
        for (TestProcedure.QualityTier t : TestProcedure.QualityTier.values()) order.put(t.displayName, 0);
        int skulls = 0, done = 0;
        try {
            player.sendSystemMessage(Component.literal(
                "§e[SLB-DEBUG] Roue reelle — luck infusee §f" + infusedLuck + "§e, " + count + " tirage(s)"
                + (giveItems ? "" : " §7(mode statistique, aucun objet donne)")));
            for (int i = 0; i < count; i++) {
                java.util.List<String> cells = (count <= 3) ? new java.util.ArrayList<>() : null;
                int rolled = TestProcedure.debugRollWheel(infusedLuck, random, cells);
                done++;
                if (cells != null) {
                    player.sendSystemMessage(Component.literal("§7roue: " + String.join(" §8| ", cells)));
                }
                if (rolled < 0) {
                    skulls++;
                    if (giveItems) player.sendSystemMessage(Component.literal("§8#" + (i + 1) + " CRANE (bad)"));
                    continue;
                }
                String name = TestProcedure.QualityTier.getQualityTier(rolled).displayName;
                order.merge(name, 1, Integer::sum);
                if (giveItems) {
                    TestProcedure.BranchResult res = generateChestPayload(level, x, y, z, rolled, random);
                    deliverDebugResult(level, player, res, "w", rolled, i + 1);
                }
            }
            // Repartition finale, cranes separes (demande : pourcentages hors crane).
            int colored = done - skulls;
            player.sendSystemMessage(Component.literal(
                "§e──── repartition sur " + done + " tirage(s) ────"));
            for (java.util.Map.Entry<String, Integer> e : order.entrySet()) {
                if (e.getValue() == 0) continue;
                double pctAll = 100.0 * e.getValue() / Math.max(1, done);
                double pctCol = 100.0 * e.getValue() / Math.max(1, colored);
                player.sendSystemMessage(Component.literal(String.format(
                    "§7%-12s §f%4d  §7total §f%5.2f%%  §7hors crane §f%5.2f%%",
                    e.getKey(), e.getValue(), pctAll, pctCol)));
            }
            player.sendSystemMessage(Component.literal(String.format(
                "§8CRANE        §f%4d  §7total §f%5.2f%%", skulls, 100.0 * skulls / Math.max(1, done))));
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.trace(t);
            player.sendSystemMessage(Component.literal("§c[SLB-DEBUG] Erreur pendant /dlb w (voir console) : " + t));
        } finally {
            DEBUG_COMMAND_ACTIVE = prevActive;
        }
        return done;
    }

    public static int runDebugCommand(CommandSourceStack source, String luckSpec, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c[SLB-DEBUG] /dlb est une commande joueur uniquement."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        RandomSource random = level.random;
        final double x = player.getX(), y = player.getY(), z = player.getZ();

        boolean prevActive = DEBUG_COMMAND_ACTIVE;
        DEBUG_COMMAND_ACTIVE = true; // 🚫 structures interdites pendant toute la commande
        int done = 0;
        try {
            for (int i = 0; i < count; i++) {
                int luck;
                try {
                    luck = parseDebugLuck(luckSpec, random);
                } catch (NumberFormatException ex) {
                    player.sendSystemMessage(Component.literal(
                        "§c[SLB-DEBUG] Luck invalide : '" + luckSpec + "' (attendu : \"5\" ou \"5-35\")"));
                    break;
                }
                done++;
                // T227 : rappel au testeur — ici luck est un RANG FORCE, pas une
                // luck infusee. Sous CURSED_LUCK_MAX l'item casse est le resultat
                // ATTENDU. Pour tester une luck infusee, utiliser /dlb w.
                if (i == 0 && luck <= CURSED_LUCK_MAX) {
                    player.sendSystemMessage(Component.literal(
                        "§6[SLB-DEBUG] /dlb g force le RANG " + luck + " (<= " + CURSED_LUCK_MAX
                        + ") : l'item casse est donc normal. Pour tester la roue a LUCK INFUSEE "
                        + luck + ", utilise §f/dlb w " + luck + " " + count));
                }
                TestProcedure.BranchResult res = generateChestPayload(level, x, y, z, luck, random);
                deliverDebugResult(level, player, res, "g", luck, i + 1);
            }
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.trace(t);
            player.sendSystemMessage(Component.literal("§c[SLB-DEBUG] Erreur pendant /dlb (voir console) : " + t));
        } finally {
            DEBUG_COMMAND_ACTIVE = prevActive;
        }
        return done;
    }

    /** Parse "5" ou "5-35" (bornes incluses) et renvoie une luck aléatoire dans la plage. */
    private static int parseDebugLuck(String spec, RandomSource random) {
        String s = spec.trim();
        int dash = s.indexOf('-');
        int min, max;
        if (dash <= 0) {
            min = max = Integer.parseInt(s);
        } else {
            min = Integer.parseInt(s.substring(0, dash).trim());
            max = Integer.parseInt(s.substring(dash + 1).trim());
        }
        if (min > max) { int t = min; min = max; max = t; }
        min = Math.max(0, Math.min(100, min));
        max = Math.max(0, Math.min(100, max));
        if (min == max) return min;
        return min + random.nextInt(max - min + 1);
    }

    /** Livre un résultat de génération : action différée + particules + drops + titres + log console. */
    private static void deliverDebugResult(ServerLevel level, ServerPlayer player,
                                           TestProcedure.BranchResult res, String mode, int luck, int index) {
        if (res == null) return;

        // Bonus loot différé (Apple Stash, chiens...) exécuté à la position du joueur
        if (res.deferredAction != null) {
            try { res.deferredAction.run(); } catch (Throwable ignored) {}
        }

        // Particules du résultat
        if (res.particles != null) {
            for (String p : res.particles) {
                ParticleOptions po = debugParticleFor(p);
                if (po != null) {
                    level.sendParticles(po, player.getX(), player.getY() + 1.0, player.getZ(),
                            10, 0.4, 0.4, 0.4, 0.03);
                }
            }
        }

        // Items droppés 1 par 1 aux pieds du joueur ("pose player")
        if (res.items != null) {
            for (ItemStack stack : res.items) {
                if (stack == null || stack.isEmpty()) continue;
                ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack);
                drop.setDefaultPickUpDelay();
                drop.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2, (level.random.nextDouble() - 0.5) * 0.1);
                level.addFreshEntity(drop);
            }
        }

        String title = (res.title != null && !res.title.isEmpty())
                ? res.title
                : "§6§lDebug " + mode.toUpperCase(Locale.ROOT) + " Reward";
        sendTitle(player, title, "§7Luck: §f" + luck + " §7- Génération #" + index);
        // Logs debug désactivés pour la publication.
        // System.out.println("[SLB-DEBUG] /dlb " + mode + " #" + index + " luck=" + luck
        //         + " rank=" + res.rank + " items=" + (res.items != null ? res.items.size() : 0)
        //         + (res.hasStructure ? " ⛰ STRUCTURE (anormale en debug !)" : ""));
    }

    private static ParticleOptions debugParticleFor(String name) {
        if (name == null) return null;
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "ENCHANT" -> ParticleTypes.ENCHANT;
            case "END_ROD" -> ParticleTypes.END_ROD;
            case "SMOKE" -> ParticleTypes.SMOKE;
            case "SOUL" -> ParticleTypes.SOUL;
            case "FIREWORK" -> ParticleTypes.FIREWORK;
            case "DRAGON_BREATH" -> ParticleTypes.DRAGON_BREATH;
            case "TOTEM_OF_UNDYING" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "HAPPY_VILLAGER" -> ParticleTypes.HAPPY_VILLAGER;
            default -> null;
        };
    }
}
