package deepluckyblock.procedures;

import deepluckyblock.enchantments.PixelFx;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Locale;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "deep_lucky_block")
public class SanctuarySpawnerEquipProcedure {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TOTAL_SPAWNS = 10;
    private static final int SEARCH_RADIUS_XZ = 15;
    private static final int SEARCH_RADIUS_Y = 15;
    private static final boolean DEBUG = false;

    private static void debug(String msg) {
        if (DEBUG) deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-SPAWNER] {}", msg);
    }

    // =====================================================================
    // GEL DU SERVEUR : CAUSE RACINE TROUVEE (T9, 20/09)
    // =====================================================================
    // PRUVE par le crash-report du 20/09 12:04:02 (Watchdog : « A single server
    // tick took 60.00 seconds ») et par sa pile d'appels :
    //
    //   SanctuarySpawnerEquipProcedure.findNearestSpawner(:177)
    //     -> Level.getBlockState -> Level.getChunk(x, z, FULL, true)
    //     -> ServerChunkCache.getChunk -> BlockableEventLoop.managedBlock
    //     -> ... attente de la generation de chunk sur le THREAD PRINCIPAL ...
    //
    // Les deux scans de spawners balayaient 31x31x31 = 29 791 positions avec
    // getBlockState(). Or getBlockState force la GENERATION SYNCHRONE du chunk
    // des qu'il n'est pas en memoire. Le scan (rayon 15) debordait donc sur les
    // chunks VOISINS, non charges : chaque zombie/squelette/noye qui apparait
    // dans un chunk fraichement genere (exactement ce que produit un paste de
    // structure : des centaines de mobs d'un coup) forcait la generation de
    // jusqu'a 8 chunks supplementaires, sur le thread principal, pendant que le
    // pipeline de terrain travaillait sur la meme zone -- d'ou les 60 secondes
    // de gel et le redemarrage force du serveur.
    //
    // Le parcours ci-dessous ne touche QUE les chunks deja charges et generes,
    // et saute les sections 16x16x16 entierement vides (un spawner n'est jamais
    // de l'air) : plus aucune generation de chunk n'est declenchee, et le cout
    // du scan passe de 29 791 lectures de blocs a quelques centaines.
    private static long skippedChunkReads = 0L;
    private static long scans = 0L;

    private static void forEachCandidate(ServerLevel level, BlockPos center, int rx, int ry,
                                         java.util.function.Consumer<BlockPos> visitor) {
        scans++;
        int x0 = center.getX() - rx, x1 = center.getX() + rx;
        int z0 = center.getZ() - rx, z1 = center.getZ() + rx;
        int y0 = Math.max(level.getMinBuildHeight(), center.getY() - ry);
        int y1 = Math.min(level.getMaxBuildHeight() - 1, center.getY() + ry);
        if (y0 > y1) return; // T313 : hors hauteur constructible -> aucun acces getSection hors bornes
        int sec0 = level.getSectionIndex(y0), sec1 = level.getSectionIndex(y1);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int cx = x0 >> 4; cx <= (x1 >> 4); cx++) {
            for (int cz = z0 >> 4; cz <= (z1 >> 4); cz++) {
                // getChunkNow : lecture seule, ne genere JAMAIS (l'ancien
                // getBlockState generait ici : c'est ce qui gelait le serveur).
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || !deepluckyblock.util.SafeSurface.isReallyGenerated(chunk)) {
                    skippedChunkReads++;
                    continue;
                }
                int bx0 = Math.max(x0, cx << 4), bx1 = Math.min(x1, (cx << 4) + 15);
                int bz0 = Math.max(z0, cz << 4), bz1 = Math.min(z1, (cz << 4) + 15);
                var sections = chunk.getSections();
                int s0 = Math.max(0, sec0), s1 = Math.min(sections.length - 1, sec1);
                for (int sec = s0; sec <= s1; sec++) {
                    LevelChunkSection section = sections[sec];
                    if (section == null || section.hasOnlyAir()) continue; // 4096 positions sautees
                    int sy0 = Math.max(y0, level.getSectionYFromSectionIndex(sec) << 4);
                    int sy1 = Math.min(y1, (level.getSectionYFromSectionIndex(sec) << 4) + 15);
                    for (int x = bx0; x <= bx1; x++)
                        for (int z = bz0; z <= bz1; z++)
                            for (int y = sy0; y <= sy1; y++)
                                visitor.accept(m.set(x, y, z));
                }
            }
        }
        if (scans % 500 == 0)
            deepluckyblock.util.DebugLog.info(LOGGER, "[SLB-SPAWNER] {} scans sans generation de chunk forcee ({} chunks voisins non charges laisses tranquilles)",
                    scans, skippedChunkReads);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob instanceof Zombie || mob instanceof Skeleton || mob instanceof Drowned)) return;
        if (mob.getPersistentData().getBoolean("slb_sanctuary_equipped")) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos mobPos = mob.blockPosition();
        SpawnerBlockEntity spawner = findNearestSpawner(level, mobPos);

        SpawnProfile profile = detectProfile(mob);
        if (profile == null) {
            if (spawner != null) {
                profile = new SpawnProfile("dragon_tower", 3);
            } else {
                return;
            }
        }

        if (spawner != null) {
            CompoundTag data = spawner.getPersistentData();
            int count = data.getInt("slb_spawn_count");

            if (count >= MAX_TOTAL_SPAWNS) {
                breakSpawner(level, spawner.getBlockPos());
                event.setCanceled(true);
                return;
            }

            count++;
            data.putInt("slb_spawn_count", count);
            spawner.setChanged();

            if (count >= MAX_TOTAL_SPAWNS) {
                breakSpawner(level, spawner.getBlockPos());
            }
        }

        try {
            equipCustomMob(mob, level, profile.tierId);
            mob.getPersistentData().putBoolean("slb_sanctuary_equipped", true);
        } catch (Exception e) {
            LOGGER.error("[SLB-SPAWNER] Erreur équipement mob", e);
        }
    }

    private static SpawnProfile detectProfile(Mob mob) {
        String name = mob.getName().getString().toLowerCase(Locale.ROOT);

        // 1. Détection prioritaire par nom personnalisé
        // Cirque
        if (name.contains("chapiteau")) return new SpawnProfile("circus", 1);
        if (name.contains("acrobate")) return new SpawnProfile("circus", 2);
        if (name.contains("ménagerie") || name.contains("menagerie") || name.contains("cirque")) return new SpawnProfile("circus", 3);
        if (name.contains("maître de piste") || name.contains("maitre de piste") || name.contains("trône") || name.contains("trone")) return new SpawnProfile("circus", 4);

        // Sanctuaire Dragon
        if (name.contains("hall verdoyant")) return new SpawnProfile("dragon_tower", 1);
        if (name.contains("guilde")) return new SpawnProfile("dragon_tower", 2);
        if (name.contains("tour") && !name.contains("sommet") && !name.contains("astrale")) return new SpawnProfile("dragon_tower", 3);
        if (name.contains("dragon de pierre")) return new SpawnProfile("dragon_tower", 4);
        if (name.contains("sommet eternel") || name.contains("sommet éternel")) return new SpawnProfile("dragon_tower", 5);

        // Observatoire
        if (name.contains("vestibule")) return new SpawnProfile("observatory", 1);
        if (name.contains("jardin suspendu")) return new SpawnProfile("observatory", 2);
        if (name.contains("observatoire") || name.contains("tour astrale")) return new SpawnProfile("observatory", 3);
        if (name.contains("vigie celeste") || name.contains("vigie céleste") || name.contains("cime")) return new SpawnProfile("observatory", 4);
        if (name.contains("orbe astral")) return new SpawnProfile("observatory", 5);

        // Citadelle
        if (name.contains("porte sylvestre")) return new SpawnProfile("citadel", 1);
        if (name.contains("cour de lierre")) return new SpawnProfile("citadel", 2);
        if (name.contains("salle des remparts")) return new SpawnProfile("citadel", 3);
        if (name.contains("garde du bastion")) return new SpawnProfile("citadel", 4);
        if (name.contains("trone de la citadelle") || name.contains("trône de la citadelle")) return new SpawnProfile("citadel", 5);

        // Jardins Corrompus
        if (name.contains("rive contaminee") || name.contains("rive contaminée")) return new SpawnProfile("corrupted_gardens", 1);
        if (name.contains("bosquet infecte") || name.contains("bosquet infecté")) return new SpawnProfile("corrupted_gardens", 2);
        if (name.contains("jardins corrompus") || name.contains("jardin corrompu")) return new SpawnProfile("corrupted_gardens", 3);
        if (name.contains("racines carmines")) return new SpawnProfile("corrupted_gardens", 4);
        if (name.contains("coeur putride") || name.contains("cœur putride")) return new SpawnProfile("corrupted_gardens", 5);

        // 2. Détection par tag NBT de palier
        if (mob.getTags().contains("slb_circus_t1")) return new SpawnProfile("circus", 1);
        if (mob.getTags().contains("slb_circus_t2")) return new SpawnProfile("circus", 2);
        if (mob.getTags().contains("slb_circus_t3")) return new SpawnProfile("circus", 3);
        if (mob.getTags().contains("slb_circus_t4")) return new SpawnProfile("circus", 4);
        if (mob.getTags().contains("slb_circus_t5")) return new SpawnProfile("circus", 5);

        if (mob.getTags().contains("slb_sanctuary_t1")) return new SpawnProfile("dragon_tower", 1);
        if (mob.getTags().contains("slb_sanctuary_t2")) return new SpawnProfile("dragon_tower", 2);
        if (mob.getTags().contains("slb_sanctuary_t3")) return new SpawnProfile("dragon_tower", 3);
        if (mob.getTags().contains("slb_sanctuary_t4")) return new SpawnProfile("dragon_tower", 4);
        if (mob.getTags().contains("slb_sanctuary_t5")) return new SpawnProfile("dragon_tower", 5);

        if (mob.getTags().contains("slb_observatory_t1")) return new SpawnProfile("observatory", 1);
        if (mob.getTags().contains("slb_observatory_t2")) return new SpawnProfile("observatory", 2);
        if (mob.getTags().contains("slb_observatory_t3")) return new SpawnProfile("observatory", 3);
        if (mob.getTags().contains("slb_observatory_t4")) return new SpawnProfile("observatory", 4);
        if (mob.getTags().contains("slb_observatory_t5")) return new SpawnProfile("observatory", 5);

        if (mob.getTags().contains("slb_citadel_t1")) return new SpawnProfile("citadel", 1);
        if (mob.getTags().contains("slb_citadel_t2")) return new SpawnProfile("citadel", 2);
        if (mob.getTags().contains("slb_citadel_t3")) return new SpawnProfile("citadel", 3);
        if (mob.getTags().contains("slb_citadel_t4")) return new SpawnProfile("citadel", 4);
        if (mob.getTags().contains("slb_citadel_t5")) return new SpawnProfile("citadel", 5);

        if (mob.getTags().contains("slb_corrupted_gardens_t1")) return new SpawnProfile("corrupted_gardens", 1);
        if (mob.getTags().contains("slb_corrupted_gardens_t2")) return new SpawnProfile("corrupted_gardens", 2);
        if (mob.getTags().contains("slb_corrupted_gardens_t3")) return new SpawnProfile("corrupted_gardens", 3);
        if (mob.getTags().contains("slb_corrupted_gardens_t4")) return new SpawnProfile("corrupted_gardens", 4);
        if (mob.getTags().contains("slb_corrupted_gardens_t5")) return new SpawnProfile("corrupted_gardens", 5);

        return null;
    }

    private static SpawnerBlockEntity findNearestSpawner(ServerLevel level, BlockPos center) {
        final SpawnerBlockEntity[] bestRef = new SpawnerBlockEntity[]{null};
        final double[] bestDistRef = new double[]{Double.MAX_VALUE};

        forEachCandidate(level, center, SEARCH_RADIUS_XZ, SEARCH_RADIUS_Y, pos -> {
            // Vérification en mémoire ultra-rapide avant l'appel getBlockEntity.
            if (!level.getBlockState(pos).is(Blocks.SPAWNER)) return;
            if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) return;

            if (pos.distSqr(center) < bestDistRef[0]) {
                bestDistRef[0] = pos.distSqr(center);
                bestRef[0] = spawner;
            }
        });

        return bestRef[0];
    }

    private static void breakSpawner(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.SPAWNER)) return;
        level.levelEvent(2001, pos, Block.getId(Blocks.SPAWNER.defaultBlockState()));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        RandomSource random = level.getRandom();
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.5f, 1.0f);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 1.5f, 1.2f);
        PixelFx.send(level, ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.1);

        int gaCount = random.nextInt(6); // 0 à 5 Golden Apple
        if (gaCount > 0) {
            Block.popResource(level, pos, new ItemStack(Items.GOLDEN_APPLE, gaCount));
        }
        if (random.nextBoolean()) { // 0 à 1 Enchanted Golden Apple (50%)
            Block.popResource(level, pos, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        }
        if (random.nextBoolean()) { // 0 à 1 Deep's Lucky Block qui brille (50%)
            ItemStack dlb = new ItemStack(deepluckyblock.init.DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get(), 1);
            ItemEntity ie = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, dlb);
            ie.setGlowingTag(true);
            level.addFreshEntity(ie);
        }
    }

    private static void equipCustomMob(Mob mob, ServerLevel level, int tierId) {
        RandomSource random = level.getRandom();

        // T338-MOB-PIECE-BY-PIECE : Chaque pièce d'équipement est désormais tirée INDÉPENDAMMENT
        // comme si elle sortait réellement d'un Deep Lucky Block infusé autour de la moyenne désirée
        // du palier de spawner (et les slots pré-remplis par le template NBT du spawner sont vidés
        // pour qu'une armure complète + arme reste rare, surtout du même grade).
        int desiredBonusLuck = switch (tierId) {
            case 1 -> -35; // Moyenne basse (Used / Worn / Common + quelques maudits)
            case 2 -> 5;   // Moyenne Lucky Block standard (Common / Uncommon / Unusual)
            case 3 -> 38;  // Moyenne intermédiaire (Uncommon -> Remarkable / Outstanding)
            case 4 -> 65;  // Moyenne haute (Remarkable -> Rare / Epic)
            case 5 -> 88;  // Moyenne élite de salle finale (Outstanding -> Legendary / Mythic+)
            default -> -35;
        };

        float armorSlotChance = switch (tierId) {
            case 1 -> 0.24f; // ~0.96 pièce d'armure en moyenne (4/4 = 0,33 %)
            case 2 -> 0.30f; // ~1.20 pièce d'armure en moyenne (4/4 = 0,81 %)
            case 3 -> 0.36f; // ~1.44 pièce d'armure en moyenne (4/4 = 1,68 %)
            case 4 -> 0.42f; // ~1.68 pièce d'armure en moyenne (4/4 = 3,11 %)
            case 5 -> 0.48f; // ~1.92 pièce d'armure en moyenne (4/4 = 5,31 %)
            default -> 0.24f;
        };

        float weaponChance = switch (tierId) {
            case 1 -> 0.50f;
            case 2 -> 0.60f;
            case 3 -> 0.68f;
            case 4 -> 0.75f;
            case 5 -> 0.82f;
            default -> 0.50f;
        };

        boolean guaranteeOnePiece = tierId >= 2;

        GenerateLuckyItemProcedure.equipMobPieceByPieceFromLuckyBlock(
                mob, level, desiredBonusLuck, armorSlotChance, weaponChance,
                guaranteeOnePiece, 0.05f, 0.05f, random);

        // Filet de sécurité supplémentaire T113 (aucun echolocation sur mob de spawner)
        stripEcholocation(mob);
    }

    /**
     * T113 : retire l'affixe echolocation de toutes les pieces portees par un
     * mob de structure (casque compris). L'affixe reste disponible dans le
     * pool pour les joueurs ; seul le port par un mob est interdit, sinon tout
     * le groupe brille en continu dans le noir.
     */
    private static void stripEcholocation(Mob mob) {
        try {
            net.minecraft.world.item.enchantment.Enchantment echo =
                    deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:echolocation");
            if (echo == null) return;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                    EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.BODY}) {
                ItemStack s = mob.getItemBySlot(slot);
                if (!s.isEmpty() && deepluckyblock.util.DeepEnchant.getLevel(s, echo) > 0) {
                    deepluckyblock.util.DeepEnchant.remove(s, echo);
                }
            }
        } catch (Throwable ignored) {
            // v1 prudente : jamais de crash parce qu'un affixe n'etait pas la
        }
    }

    private static ItemStack safeSpecific(ServerLevel level, int luck, TestProcedure.QualityTier tier,
                                          RandomSource random, EquipmentSlot slot) {
        ItemStack stack = GenerateLuckyItemProcedure.generateSpecific(level, luck, tier, random, slot);
        if (stack == null || stack.isEmpty()) {
            return switch (slot) {
                case HEAD -> new ItemStack(Items.IRON_HELMET);
                case CHEST -> new ItemStack(Items.IRON_CHESTPLATE);
                case LEGS -> new ItemStack(Items.IRON_LEGGINGS);
                case FEET -> new ItemStack(Items.IRON_BOOTS);
                case MAINHAND -> new ItemStack(Items.IRON_SWORD);
                case OFFHAND -> new ItemStack(Items.IRON_SWORD);
                // PORT 1.21.1 : EquipmentSlot.BODY (armure animale) ajouté.
                case BODY -> new ItemStack(Items.IRON_HORSE_ARMOR);
            };
        }
        return stack;
    }

    private static ItemStack safeSpecificByType(ServerLevel level, int luck, TestProcedure.QualityTier tier,
                                                RandomSource random, String type) {
        ItemStack stack = GenerateLuckyItemProcedure.generateSpecific(level, luck, tier, random, type);
        if (stack == null || stack.isEmpty()) {
            return switch (type) {
                case "bow" -> new ItemStack(Items.BOW);
                case "axe" -> new ItemStack(Items.IRON_AXE);
                default -> new ItemStack(Items.IRON_SWORD);
            };
        }
        return stack;
    }

    private static void shuffleSlots(EquipmentSlot[] slots, RandomSource random) {
        for (int i = slots.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            EquipmentSlot tmp = slots[i];
            slots[i] = slots[j];
            slots[j] = tmp;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Toutes les 40 ticks (2 secondes), le serveur vérifie les spawners proches dans un rayon de 15 blocs.
        if (player.tickCount % 40 != 0) return;

        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();

        forEachCandidate(level, center, SEARCH_RADIUS_XZ, SEARCH_RADIUS_Y, pos -> {
            if (!level.getBlockState(pos).is(Blocks.SPAWNER)) return;
            if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) return;
            if (spawner.getPersistentData().getBoolean("slb_light_repaired")) return;

            repairSpawnerLightRules(level, pos.immutable(), spawner);
        });
    }

    private static void repairSpawnerLightRules(ServerLevel level, BlockPos pos, SpawnerBlockEntity spawner) {
        try {
            CompoundTag tag = spawner.saveWithFullMetadata(level.registryAccess());
            if (!tag.contains("SpawnData", Tag.TAG_COMPOUND)) return;

            CompoundTag spawnData = tag.getCompound("SpawnData");
            if (!spawnData.contains("entity", Tag.TAG_COMPOUND)) return;

            CompoundTag entity = spawnData.getCompound("entity");
            if (!isModSpawner(entity)) return;

            // 1. Injection des règles de lumière dans SpawnData
            spawnData.put("custom_spawn_rules", createLightRules());
            tag.put("SpawnData", spawnData);

            // 2. Injection dans SpawnPotentials (ou création d'une entrée par défaut si vide)
            if (tag.contains("SpawnPotentials", Tag.TAG_LIST)) {
                ListTag potentials = tag.getList("SpawnPotentials", Tag.TAG_COMPOUND);
                if (potentials.isEmpty()) {
                    CompoundTag pot = new CompoundTag();
                    pot.put("data", spawnData.copy());
                    pot.putInt("weight", 1);
                    potentials.add(pot);
                } else {
                    for (int i = 0; i < potentials.size(); i++) {
                        CompoundTag pot = potentials.getCompound(i);
                        if (pot.contains("data", Tag.TAG_COMPOUND)) {
                            CompoundTag data = pot.getCompound("data");
                            data.put("custom_spawn_rules", createLightRules());
                            pot.put("data", data);
                        }
                    }
                }
                tag.put("SpawnPotentials", potentials);
            } else {
                ListTag potentials = new ListTag();
                CompoundTag pot = new CompoundTag();
                pot.put("data", spawnData.copy());
                pot.putInt("weight", 1);
                potentials.add(pot);
                tag.put("SpawnPotentials", potentials);
            }

            spawner.loadWithComponents(tag, level.registryAccess());
            spawner.getPersistentData().putBoolean("slb_light_repaired", true);
            spawner.setChanged();
            level.sendBlockUpdated(pos, spawner.getBlockState(), spawner.getBlockState(), 3);
            debug("Réparation automatique lumière spawner réussie en " + pos);
        } catch (Exception e) {
            LOGGER.error("[SLB-SPAWNER] Erreur de réparation lumière spawner en " + pos, e);
        }
    }

    private static boolean isModSpawner(CompoundTag entity) {
        if (entity.contains("Tags", Tag.TAG_LIST)) {
            ListTag tags = entity.getList("Tags", Tag.TAG_STRING);
            for (int i = 0; i < tags.size(); i++) {
                String t = tags.getString(i);
                if (t.startsWith("slb_")) return true;
            }
        }
        if (entity.contains("CustomName", Tag.TAG_STRING)) {
            String name = entity.getString("CustomName").toLowerCase(Locale.ROOT);
            if (name.contains("chapiteau") || name.contains("acrobate") || name.contains("ménagerie") || name.contains("menagerie") || name.contains("cirque")
             || name.contains("maître de piste") || name.contains("maitre") || name.contains("trône") || name.contains("trone")
             || name.contains("verdoyant") || name.contains("guilde") || name.contains("dragon") || name.contains("sommet")
             || name.contains("vestibule") || name.contains("jardin") || name.contains("astrale") || name.contains("vigie")
             || name.contains("orbe") || name.contains("sylvestre") || name.contains("lierre") || name.contains("remparts")
             || name.contains("bastion") || name.contains("citadelle") || name.contains("observatoire")
             || name.contains("contaminee") || name.contains("contaminée")
             || name.contains("bosquet infecte") || name.contains("bosquet infecté")
             || name.contains("corrompu") || name.contains("corrompus")
             || name.contains("racines carmines")
             || name.contains("coeur putride") || name.contains("cœur putride")) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag createLightRules() {
        CompoundTag rules = new CompoundTag();
        CompoundTag blockLight = new CompoundTag();
        blockLight.putInt("min_inclusive", 0);
        blockLight.putInt("max_inclusive", 15);
        CompoundTag skyLight = new CompoundTag();
        skyLight.putInt("min_inclusive", 0);
        skyLight.putInt("max_inclusive", 15);
        rules.put("block_light_limit", blockLight);
        rules.put("sky_light_limit", skyLight);
        return rules;
    }

    private record SpawnProfile(String family, int tierId) {}
}
