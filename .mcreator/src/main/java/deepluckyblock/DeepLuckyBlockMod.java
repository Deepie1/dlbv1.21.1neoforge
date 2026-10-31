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
		NeoForge.EVENT_BUS.register(this);
		// IEventBus bus = context.getModEventBus();  ->  modBus (parametre)
		DeepLuckyBlockModBlocks.REGISTRY.register(modBus);
		DeepLuckyBlockModItems.REGISTRY.register(modBus);
		DeepLuckyBlockModEntities.REGISTRY.register(modBus);
		DeepLuckyBlockModTabs.REGISTRY.register(modBus);
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

	public static void queueServerWork(int delay, Runnable action) {
		if (Thread.currentThread() == serverThread)
			workToBeScheduled.add(new IntObjectImmutablePair<>(delay, action));
	}

	// ServerTickEvent.Post (Forge) -> ServerTickEvent.Post (NeoForge).
	// L'ancien code verifiait phase == END ; .Post fait exactement ca.
	@SubscribeEvent
	public void tick(ServerTickEvent.Post event) {
		serverThread = Thread.currentThread();
		deepluckyblock.util.DeepEnchant.rememberAccess(event.getServer().overworld().registryAccess());
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
