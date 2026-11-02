package deepluckyblock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.server.TickTask;

import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Comparator;

import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;

import deepluckyblock.init.DeepLuckyBlockModTabs;
import deepluckyblock.init.DeepLuckyBlockModItems;
import deepluckyblock.init.DeepLuckyBlockModEntities;
import deepluckyblock.init.DeepLuckyBlockModBlocks;
import deepluckyblock.network.DeepPayloads;

/*
 * PORT 1.20.1 (Forge) -> 1.21.1 (NeoForge).
 *
 *  Changements majeurs :
 *   - @Mod constructor : (FMLJavaModLoadingContext) -> (IEventBus modBus, ModContainer)
 *   - net.minecraftforge.* -> net.neoforged.* (imports)
 *   - MinecraftForge.EVENT_BUS -> NeoForge.EVENT_BUS
 *   - context.registerConfig(...) -> modContainer.registerConfig(...)
 *   - ServerTickEvent.Post (phase END) -> ServerTickEvent.Post
 *   - SidedThreadGroups (supprimé dans NeoForge) -> Thread.isMainThread()
 *   - SimpleChannel/NetworkRegistry -> DeepPayloads (PayloadRegistrar)
 */
@Mod("deep_lucky_block")
public class DeepLuckyBlockMod {
	public static final Logger LOGGER = LogManager.getLogger(DeepLuckyBlockMod.class);
	public static final String MODID = "deep_lucky_block";

	public DeepLuckyBlockMod(IEventBus modBus, ModContainer modContainer) {
		// Start of user code block mod constructor
		// End of user code block mod constructor
		// FIX CRITIQUE (rapporté en jeu, captures d'écran "When on Legs"/
		// "When on Body" -- "+7 Armor" PUIS "+6 Armor" affichés l'un sous
		// l'autre au lieu d'une seule ligne "+13 Armor" : "je vois encore
		// des doublons de lignes, notamment sur les +x armor", "il y a
		// l'ancienne ligne, + notre nouvelle") : CAUSE RACINE confirmée --
		// une pièce d'armure porte PLUSIEURS AttributeModifier DISTINCTS sur
		// le MÊME attribut ARMOR (le bonus vanilla de base du matériau ET,
		// pour chaque enchantement du mod fixe/inconditionnel converti par
		// RealAttributeModifierHandler -- Protection, Divine Protection,
		// Stomach Pouch, Wound Crack... -- un modificateur supplémentaire
		// avec son propre ResourceLocation d'id). Le rendu natif vanilla du
		// tooltip (ItemStack#addAttributeTooltips) affiche PAR DÉFAUT une
		// ligne SÉPARÉE par modificateur trouvé pour un même attribut,
		// jamais un total fusionné -- exactement le "+7/+6 Armor" rapporté
		// (le même bug déjà corrigé pour Attack Damage/Attack Speed dans
		// EnchantmentTooltipFixProcedure#overrideNativeAttackStats, mais qui
		// ne couvre QUE "When in Main Hand", jamais Armor/Armor Toughness/
		// Knockback Resistance de "When on Legs"/"When on Body"). NeoForge
		// fournit un opt-in officiel pour EXACTEMENT ce cas ("Instead of
		// showing '+1 Armor' three times, the tooltip shows '+3 Armor'
		// once") : NeoForgeMod.enableMergedAttributeTooltips(), à appeler
		// une seule fois au constructeur du mod -- fusionne côté rendu
		// TOUTES les lignes de modificateurs identiques par nom/opération
		// pour un même attribut (Armor, Armor Toughness, Knockback
		// Resistance, etc.) en une seule ligne totalisée, sur TOUTES les
		// pièces d'équipement du jeu (vanilla et modées), sans toucher aux
		// vrais AttributeModifier ni à leur valeur réelle en combat.
		net.neoforged.neoforge.common.NeoForgeMod.enableMergedAttributeTooltips();
		NeoForge.EVENT_BUS.register(this);
		// IEventBus bus = context.getModEventBus();  ->  modBus (parametre)
		DeepLuckyBlockModBlocks.REGISTRY.register(modBus);
		DeepLuckyBlockModItems.REGISTRY.register(modBus);
		// FIX v5 : les ArmorMaterial du mod (samouss, lunettes_du_patron) vivent dans un
		// registre SEPARATE (BuiltInRegistries.ARMOR_MATERIAL). Sans ce register(),
		// leurs DeferredHolder restent "unbound" -> crash NPE
		// "Trying to access unbound value: ResourceKey[minecraft:armor_material /
		//  deep_lucky_block:samouss]" des que un item les lit (tooltip inventaire creative).
		DeepLuckyBlockModItems.ARMOR_MATERIALS.register(modBus);
		DeepLuckyBlockModEntities.REGISTRY.register(modBus);
		DeepLuckyBlockModTabs.REGISTRY.register(modBus);

		// FIX v8 : banniere de version + diagnostics (log "[SLB-V8]")
		SlbDiagnostics.banner();
		// Start of user code block mod init
		// Filet de securite : charge les mixins de compatibilite meme si le
		// reconditionnement MCreator perd l'attribut MixinConfigs du manifeste.
		deepluckyblock.compat.DeepMixinBootstrap.ensureConfigured();
		// Enregistre le BlockEntityType du Deep Lucky Block (canonique, unique).
		deepluckyblock.init.DeepLuckyBlockModBlockEntities.REGISTRY.register(modBus);
		// Enregistre le registre de nos enchantements autonomes Supreme Lucky Block
		deepluckyblock.enchantments.ModEnchantments.register(modBus);
		// Enregistre le sérialiseur de la recette d'infusion de chance du Deep's Lucky Block
		// (PORT 1.21.1 : Registries.RECIPE_SERIALIZER, plus ForgeRegistries)
		deepluckyblock.recipe.DeepLuckyBlockCraftingRecipe.RECIPE_SERIALIZERS.register(modBus);
		// Enregistre les triggers custom d'advancements (PORT 1.21.1 :
		// Registries.TRIGGER_TYPE, plus CriteriaTriggers.register)
		deepluckyblock.advancements.DeepLuckyBlockAdvancementTrigger.TRIGGER_TYPES.register(modBus);
		// FIX v6 : force le chargement ET l'initialisation de la classe
		// DeepLuckyBlockBrokenTrigger — son <clinit> enregistre le trigger
		// "negative_streak" dans TRIGGER_TYPES. Sans ce touch, la classe
		// n'etait chargee qu'a la 1ere mauvaise serie (runtime) : au parse des
		// advancements, "deep_lucky_block:negative_streak" etait "Unknown
		// registry key ... trigger_type" -> l'advancement etait supprime
		// ("Ignored advancement ... it doesn't exist anymore?").
		try {
			Class.forName(deepluckyblock.advancements.DeepLuckyBlockBrokenTrigger.class.getName());
		} catch (ClassNotFoundException ignored) {
			// Impossible : la classe est dans le meme jar.
		}
		// FIX v7 : force le chargement de la procedure d'autofill des coffres
		// (son @EventBusSubscriber enregistre le handler RightClickBlock qui
		// remplit les Lucky Chests ; on ne depend pas du scan d'annotations).
		try {
			Class.forName(deepluckyblock.procedures.LuckyChestAutofillProcedure.class.getName());
		} catch (ClassNotFoundException ignored) {
			// Impossible : la classe est dans le meme jar.
		}
		// Onglet creatif personnalise (survit aux regenerations MCreator - classe non generee)
		deepluckyblock.CustomCreativeTabs.REGISTRY.register(modBus);
		// Items custom (oeuf de Bob) - non regeneres par MCreator
		deepluckyblock.CustomCreativeTabs.ITEMS.register(modBus);

		// CONFIGS : context.registerConfig -> modContainer.registerConfig
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.TillageBootConfig.SPEC, "deep_lucky_block-tillage_boot.toml");
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.CoreCollectionConfig.SPEC, "deep_lucky_block-core_collection.toml");
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.CropHarvestConfig.Common.SPEC, "deep_lucky_block-crop_harvest.toml");
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.OreDetectorConfig.SPEC, "deep_lucky_block-ore_detector.toml");
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.RangeFootBlockConfig.SPEC, "deep_lucky_block-range_foot_block.toml");
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.TransferConfig.SPEC, "deep_lucky_block-transfer.toml");
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.WeedRemovalConfig.SPEC, "deep_lucky_block-weed_removal.toml");
		// FIX (demande en jeu : prix/gains d'infusion de chance configurables)
		// -- LuckInfusionConfig.SPEC existait deja mais n'etait jamais
		// enregistre aupres du ModContainer, donc le fichier
		// deep_lucky_block-luck_infusion.toml n'etait jamais genere/lu et le
		// multiplicateur global + les overrides par item restaient inactifs.
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.config.LuckInfusionConfig.SPEC, "deep_lucky_block-luck_infusion.toml");
		// Config du drop de Deep Lucky Block (defaut TRUE, desactivable par le modpack LWI Reloaded)
		modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, deepluckyblock.procedures.SupremeLuckyBlockDropProcedure.DROP_SPEC, "deep_lucky_block-lucky_drop.toml");

		// RESEAU : SimpleChannel -> payloads NeoForge
		// PORT 1.21.1 : payloads enregistrés via RegisterPayloadHandlersEvent (voir DeepPayloads).
		// End of user code block mod init
	}

	// Start of user code block mod methods
	// End of user code block mod methods

	private static final Queue<IntObjectPair<Runnable>> workToBeScheduled = new ConcurrentLinkedQueue<>();
	private static final PriorityQueue<TickTask> workQueue = new PriorityQueue<>(Comparator.comparingInt(TickTask::getTick));

	// SidedThreadGroups.SERVER (Forge) est SUPPRIME dans NeoForge.
	// On ne queue le travail que si on est sur le thread principal (server thread).
	// [1.21.1] Thread.isMainThread() (patch NeoForge) evite : on capture le thread
	// serveur sur le premier tick (plus fiable, aucune dependance au patch).
	private static volatile Thread serverThread;

	public static boolean isServerThread() {
		Thread st = serverThread;
		return st != null && Thread.currentThread() == st;
	}

	public static void queueServerWork(int delay, Runnable action) {
		if (Thread.currentThread() == serverThread)
			workToBeScheduled.add(new IntObjectImmutablePair<>(delay, action));
	}

	@SubscribeEvent
	public void onServerAboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
		serverThread = Thread.currentThread();
		deepluckyblock.util.DeepEnchant.rememberServerAccess(event.getServer().registryAccess());
	}

	@SubscribeEvent
	public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
		deepluckyblock.util.DeepEnchant.clearServerAccess();
		serverThread = null;
	}

	// FIX v6 : le cache de RegistryAccess etait pose trop tard (ServerTickEvent.Post
	// = fin de tick). Au PREMIER tick, le chargement des entites (equipment des
	// mobs, spawners) appelait DeepEnchant.reg(access()) AVANT tout Post :
	// access() retournait RegistryAccess.EMPTY (sans registre datapack
	// minecraft:enchantment) -> "Missing registry: minecraft:enchantment" ->
	// tout le drop d'items plantait (enchantements, glow, infusions de luck,
	// coffres vides, mobs sans armure). ServerStartedEvent fire apres le
	// chargement du monde, AVANT le premier tick : le cache est pret a temps.
	@SubscribeEvent
	public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
		serverThread = Thread.currentThread();
		deepluckyblock.util.DeepEnchant.rememberServerAccess(event.getServer().registryAccess());
		net.minecraft.server.level.ServerLevel overworld = event.getServer().overworld();
		if (overworld != null) {
			deepluckyblock.util.DeepEnchant.rememberServerAccess(overworld.registryAccess());
			// FIX (freeze serveur ~78s au premier spawn de shipdead, log :
			// "Can't keep up! ... Running 78183ms or 1563 ticks behind") :
			// on précharge ici, AVANT toute connexion de joueur, toutes les
			// structures NBT du mod (StructureTemplateCache) pour que leur
			// désérialisation synchrone (jusqu'à ~5 Mo / ~1.7M blocs pour
			// shipdead et citadel) soit payée une seule fois au démarrage du
			// serveur plutôt que de figer le serveur en pleine partie la
			// première fois qu'un joueur ouvre un lucky block qui en a besoin.
			deepluckyblock.util.StructureTemplateCache.preloadAll(overworld);
		}
	}

	// ServerTickEvent.Post (Forge) -> ServerTickEvent.Post (NeoForge).
	// L'ancien code verifiait phase == END ; .Post fait exactement ca.
	@SubscribeEvent
	public void tick(ServerTickEvent.Post event) {
		serverThread = Thread.currentThread();
		deepluckyblock.util.DeepEnchant.rememberServerAccess(event.getServer().overworld().registryAccess());
		int currentTick = event.getServer().getTickCount();
		IntObjectPair<Runnable> work;
		while ((work = workToBeScheduled.poll()) != null) {
			workQueue.add(new TickTask(currentTick + work.leftInt(), work.right()));
		}
		while (!workQueue.isEmpty() && currentTick >= workQueue.peek().getTick()) {
			workQueue.poll().run();
		}
		// Rechauffage progressif du cache de structures : une grosse structure
		// toutes les 30 s, au lieu de toutes les charger a la creation de la
		// map. Voir StructureTemplateCache#tickWarmup.
		try {
			deepluckyblock.util.StructureTemplateCache.tickWarmup(event.getServer().overworld());
		} catch (Throwable ignored) { }
	}
}
