package deepluckyblock.procedures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;

import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

import deepluckyblock.entity.ShadowMirrorEntity;
import deepluckyblock.entity.SlimeKingEntity;
import deepluckyblock.entity.EndKnightEntity;
import deepluckyblock.entity.BenEntity;
import deepluckyblock.entity.JerryEntity;
import deepluckyblock.entity.SamoussBossEntity;
import deepluckyblock.init.DeepLuckyBlockModEntities;
import deepluckyblock.advancements.DeepLuckyBlockAdvancements;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "deep_lucky_block")
public class GenerateBadProcedure {

    // ============================================================
    // ACTIVE/DESACTIVE les logs de debug [SLB-DEBUG].
    // Mettre a false avant publication.
    // ============================================================
    private static final boolean DEBUG_LOGS = false;

    // ACTIVE/DESACTIVE la commande de test /eventtest.
    // Mettre a FALSE avant publication pour que les joueurs n'y aient pas acces.
    // ============================================================
    private static final boolean EVENT_TEST_ENABLED = false;

    private static final String SKY_LOOT_TRAP_TAG = "slb_skyloot_trap";
    private static final String SKY_LOOT_ID_TAG = "slb_skyloot_id";
    private static final String SKY_LOOT_GROUND_X_TAG = "slb_skyloot_ground_x";
    private static final String SKY_LOOT_GROUND_Y_TAG = "slb_skyloot_ground_y";
    private static final String SKY_LOOT_GROUND_Z_TAG = "slb_skyloot_ground_z";
    private static final String RANDOM_EXPLOSION_POWER_TAG = "slb_random_explosion_power";

    // Garde-fous des effets de potion temporaires poses par GenerateBad.
    // Chaque entree ne concerne qu'UN effet precis applique par le mod : aucun
    // clear global et aucun effet tiers du joueur n'est supprime.
    private static final String EVENT_EFFECT_GUARDS_TAG = "slb_event_effect_guards";
    private static final String EFFECT_GUARD_EFFECT_TAG = "Effect";
    private static final String EFFECT_GUARD_EXPIRES_TAG = "ExpiresAt";
    private static final String EFFECT_GUARD_APPLIED_TAG = "AppliedAt";
    private static final String EFFECT_GUARD_AMPLIFIER_TAG = "Amplifier";
    private static final String EFFECT_GUARD_ENTITY_TAG = "EntityId";
    private static final String EFFECT_GUARD_PREVIOUS_TAG = "PreviousEffect";
    private static final int EFFECT_GUARD_GRACE_TICKS = 3;
    private static final int EFFECT_GUARD_MAX_ENTRIES = 64;

    private static final int TOTAL_NEGATIVE_EVENTS = 88;

    // Cat Invasion : 1 a 5 compagnons au total. Dewey occupe une place dans
    // ce tirage avec exactement 10 % de chance d'etre inclus.
    private static final int CAT_MIN_COUNT = 1;
    private static final int CAT_MAX_COUNT = 5;
    private static final int DEWEY_POOL_CHANCE_PERCENT = 10;
    private static final int CAT_INVASION_EVENT_NUMBER = 17;
    // Pool GREEN : chance de base 10 % (1/10), augmentee de +7 points.
    private static final int GREEN_CAT_INVASION_CHANCE_PERCENT = 17;

    // Marqueur de version pour verifier que les deux procedures sont synchronisees.
    public static final String BAD_API_VERSION = "V4.34-GREEN-1-IN-20-CATS-17-GRAVEL-87";
    public enum BadEventCategory { BLACK, RED, GREEN }

    // Numeros joueur (1-87). Chaque event appartient exactement a une categorie.
    // BLACK : un mob/une entite hostile attaque directement le joueur.
    // RED   : piege, degats, debuff ou destruction sans attaque directe obligatoire.
    // GREEN : resultat positif ou neutre de la branche bad.
    private static final int[] BLACK_EVENT_NUMBERS = {
        4,5,6,7,12,19,20,21,26,27,28,29,30,31,34,36,38,39,42,43,
        56,57,58,64,65,66,68,73,74,77,78,80,81,82,83,84,85,88
    };
    private static final int[] RED_EVENT_NUMBERS = {
        1,2,3,8,9,10,11,13,14,15,16,22,23,24,25,32,35,37,41,
        44,45,46,48,49,50,51,52,53,55,59,60,63,67,69,71,72,75,76,79
    };
    private static final int[] GREEN_EVENT_NUMBERS = {
        17,18,33,40,47,54,61,62,70,86,87
    };

    private static int pickCategorizedEventId(BadEventCategory category, RandomSource random) {
        int[] pool = switch (category) {
            case BLACK -> BLACK_EVENT_NUMBERS;
            case RED -> RED_EVENT_NUMBERS;
            case GREEN -> GREEN_EVENT_NUMBERS;
        };

        if (category == BadEventCategory.GREEN) {
            // CatInvasion conserve exactement 17 %. Les 10 autres events GREEN,
            // dont GravelInventory, se partagent equitablement les 83 % restants.
            if (random.nextInt(100) < GREEN_CAT_INVASION_CHANCE_PERCENT) {
                return CAT_INVASION_EVENT_NUMBER - 1;
            }
            int alternative = random.nextInt(pool.length - 1);
            for (int number : pool) {
                if (number == CAT_INVASION_EVENT_NUMBER) continue;
                if (alternative-- == 0) return number - 1;
            }
        }

        return pool[random.nextInt(pool.length)] - 1;
    }

    private static BadEventCategory randomEuropeanRouletteCategory(RandomSource random) {
        // Meme ponderation que la roulette visuelle : 1/20 GREEN, 19/40 RED,
        // 19/40 BLACK. Aucun changement de categorie pour les tirages forces.
        if (random.nextInt(20) == 0) return BadEventCategory.GREEN;
        return random.nextBoolean() ? BadEventCategory.RED : BadEventCategory.BLACK;
    }

    public static BadEventCategory categoryForEventNumber(int eventNumber) {
        for (int number : BLACK_EVENT_NUMBERS) if (number == eventNumber) return BadEventCategory.BLACK;
        for (int number : RED_EVENT_NUMBERS) if (number == eventNumber) return BadEventCategory.RED;
        return BadEventCategory.GREEN;
    }

    /**
     * Score Average Luck natif de Lucky Stats, sans changer les numeros
     * canoniques /eventtest : BLACK = 0..24, RED = 25..49, GREEN = 50.
     * L'ordre existant de chaque pool conserve la progression de gravite.
     */
    public static int averageLuckScoreForEventNumber(int eventNumber) {
        for (int number : GREEN_EVENT_NUMBERS) {
            if (number == eventNumber) return 50;
        }
        for (int index = 0; index < RED_EVENT_NUMBERS.length; index++) {
            if (RED_EVENT_NUMBERS[index] == eventNumber) {
                return descendingScore(index, RED_EVENT_NUMBERS.length, 49, 25);
            }
        }
        for (int index = 0; index < BLACK_EVENT_NUMBERS.length; index++) {
            if (BLACK_EVENT_NUMBERS[index] == eventNumber) {
                return descendingScore(index, BLACK_EVENT_NUMBERS.length, 24, 0);
            }
        }
        return 0;
    }

    private static int descendingScore(int index, int count,
                                       int highestScore, int lowestScore) {
        if (count <= 1) return highestScore;
        double progress = index / (double) (count - 1);
        return highestScore - (int) Math.round(
                progress * (highestScore - lowestScore));
    }

    private static final String[] NARRATORS = {
        "Deep", "Deep", "Deep", "DeepWalls", "Dee-Walls", "DeepWallas"
    };

    private static final String[] FAMILY_NAMES = {
        "event.deep_lucky_block.family.auntie_helene", "event.deep_lucky_block.family.grandpa_roger", "event.deep_lucky_block.family.cousin_george",
        "event.deep_lucky_block.family.uncle_bernard", "event.deep_lucky_block.family.granny_lucienne", "event.deep_lucky_block.family.cousin_kevin",
        "event.deep_lucky_block.family.auntie_monique", "event.deep_lucky_block.family.grandpa_marcel", "event.deep_lucky_block.family.uncle_jean_pierre",
        "event.deep_lucky_block.family.cousin_brigitte", "event.deep_lucky_block.family.grandma_odette", "event.deep_lucky_block.family.uncle_fernand",
        "event.deep_lucky_block.family.cousin_didier", "event.deep_lucky_block.family.auntie_josiane", "event.deep_lucky_block.family.grandpa_albert"
    };

    // Noms = chats des créateurs ; chaque nom a sa couleur et son skin (variant) réaliste
    private record CatPet(String name, String color, String variantId) {}

    // Les 11 chats standards utilisent les 11 variantes vanilla, sans doublon.
    // Nevada et Ymir rejoignent ce pool normal lors du tirage. Dewey est le
    // quatorzieme compagnon possible, avec une probabilite separee de 10 %.
    private static final CatPet[] CAT_PETS = {
        new CatPet("Jumbo",   "6", "minecraft:tabby"),
        new CatPet("Lua",     "8", "minecraft:black"),
        new CatPet("Pumpkin", "c", "minecraft:red"),
        new CatPet("Tiplouf", "7", "minecraft:british_shorthair"),
        new CatPet("Matcha",  "a", "minecraft:white"),
        new CatPet("Poupou",  "b", "minecraft:ragdoll"),
        new CatPet("Belle",   "f", "minecraft:persian"),
        new CatPet("Schnouk", "4", "minecraft:siamese"),
        new CatPet("Maurice", "d", "minecraft:jellie"),
        new CatPet("Azrak",   "9", "minecraft:all_black"),
        new CatPet("Sam",     "e", "minecraft:calico")
    };

    // Indices supplementaires du pool normal (hors Dewey).
    private static final int CAT_POOL_NEVADA = CAT_PETS.length;
    private static final int CAT_POOL_YMIR = CAT_PETS.length + 1;
    private static final int CAT_REGULAR_POOL_SIZE = CAT_PETS.length + 2;

    // Chaque Dewey soigne independamment 0.5 point de vie environ toutes les 2.4 s.
    // Le soin direct se cumule. Une Regeneration I de 49 ticks sert d'indicateur
    // visible, mais expire avant son premier tick de soin vanilla (50 ticks).
    private static void refreshDeweyRegen(ServerLevel level, Cat dewey, UUID ownerId) {
        TestProcedure.schedule(level, TestProcedure.currentTick(level) + 48, () -> {
            if (dewey.isRemoved() || !dewey.isAlive()) return;

            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null) return; // joueur deconnecte

            if (owner.isAlive()
                    && dewey.level() == owner.level()
                    && dewey.distanceToSqr(owner) <= 30.0 * 30.0) {
                owner.heal(0.5f);
                giveEventEffect(owner, MobEffects.REGENERATION, 49, 0,
                        false, true, true);
                owner.serverLevel().sendParticles(ParticleTypes.HEART,
                        owner.getX(), owner.getY() + 1.0, owner.getZ(),
                        1, 0.2, 0.25, 0.2, 0.01);
            }

            // Ne s'arrete plus si le chat est momentanement trop loin ou si le
            // joueur est mort : le soin reprend apres teleport/respawn.
            refreshDeweyRegen(level, dewey, ownerId);
        });
    }

    // Applique strictement une variante vanilla 1.20.1.
    // getOptional evite le fallback silencieux du registre vers le tabby brun.
    private static void applyCatVariant(Cat cat, String variantId) {
        ResourceLocation id = ResourceLocation.tryParse(variantId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid cat variant id: " + variantId);
        }

        var variant = BuiltInRegistries.CAT_VARIANT.getHolder(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown cat variant: " + id));
        cat.setVariant(variant);

        if (DEBUG_LOGS) {
            ResourceLocation applied = BuiltInRegistries.CAT_VARIANT.getKey(variant.value());
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] Cat variant applied: requested=" + id + ", actual=" + applied);
        }
    }

    private static void keepPetNearOwner(ServerLevel level, Entity pet, UUID ownerId) {
        TestProcedure.schedule(level, TestProcedure.currentTick(level) + 20, () -> {
            if (pet.isRemoved() || !pet.isAlive()) return;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null) return;

            if (owner.isAlive() && pet.level() == owner.level()
                    && pet.distanceToSqr(owner) > 12.0 * 12.0) {
                RandomSource random = owner.serverLevel().getRandom();
                boolean teleported = false;
                for (int attempt = 0; attempt < 8 && !teleported; attempt++) {
                    int ox = random.nextInt(5) - 2;
                    int oz = random.nextInt(5) - 2;
                    BlockPos feet = owner.blockPosition().offset(ox, 0, oz);
                    if (owner.serverLevel().getBlockState(feet).isAir()
                            && owner.serverLevel().getBlockState(feet.above()).isAir()
                            && owner.serverLevel().getBlockState(feet.below()).blocksMotion()) {
                        pet.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        teleported = true;
                    }
                }
                if (!teleported) {
                    pet.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                }
            }
            keepPetNearOwner(level, pet, ownerId);
        });
    }

    private static void bindCatToOwner(Cat cat, ServerPlayer owner) {
        if (owner == null) return;
        cat.tame(owner);
        keepPetNearOwner(owner.serverLevel(), cat, owner.getUUID());
    }

    private static void spawnRegularNamedCat(ServerLevel level, double x, double y, double z,
                                             RandomSource random, ServerPlayer owner,
                                             CatPet pet) {
        Cat cat = new Cat(EntityType.CAT, level);
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 1.5 + random.nextDouble() * 4.0;
        cat.moveTo(x + Math.cos(angle) * dist, y + 1,
                z + Math.sin(angle) * dist, random.nextFloat() * 360, 0);
        cat.setPersistenceRequired();
        applyCatVariant(cat, pet.variantId());
        cat.setCustomName(Component.literal("§" + pet.color() + "§l" + pet.name()));
        cat.setCustomNameVisible(true);
        if (level.addFreshEntity(cat)) {
            bindCatToOwner(cat, owner);
        } else if (DEBUG_LOGS) {
            deepluckyblock.util.DebugLog.err("[SLB-DEBUG] Failed to spawn cat " + pet.name());
        }
    }

    // Nevada : un ocelot qui court vite et se teleporte aussi vers le joueur.
    private static void spawnNevada(ServerLevel level, double x, double y, double z,
                                    RandomSource random, ServerPlayer owner) {
        var o = EntityType.OCELOT.create(level);
        if (o == null) return;
        double ang = random.nextDouble() * Math.PI * 2, dist = 2 + random.nextDouble() * 4;
        o.moveTo(x + Math.cos(ang) * dist, y + 1, z + Math.sin(ang) * dist, random.nextFloat() * 360, 0);
        o.setPersistenceRequired();
        o.setCustomName(Component.literal("§e§lNevada"));
        o.setCustomNameVisible(true);
        try { o.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.55); } catch (Exception ignored) {} // cours vite
        level.addFreshEntity(o);
        if (owner != null) keepPetNearOwner(level, o, owner.getUUID());
    }

    // Ymir : chat tabby fragile (1 PV), brillant et lie au joueur.
    private static void spawnYmir(ServerLevel level, double x, double y, double z,
                                  RandomSource random, ServerPlayer owner) {
        Cat cat = new Cat(EntityType.CAT, level);
        double ang = random.nextDouble() * Math.PI * 2, dist = 2 + random.nextDouble() * 4;
        cat.moveTo(x + Math.cos(ang) * dist, y + 1, z + Math.sin(ang) * dist, random.nextFloat() * 360, 0);
        cat.setPersistenceRequired();
        applyCatVariant(cat, "minecraft:tabby");
        try { cat.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0); cat.setHealth(1.0f); } catch (Exception ignored) {} // 1 PV
        cat.setCustomName(Component.literal("§a§lYmir"));
        cat.setCustomNameVisible(true);
        cat.setGlowingTag(true); // brille
        level.addFreshEntity(cat);
        bindCatToOwner(cat, owner);
    }

    // Dewey est TOUJOURS noir. Il occupe une des 1-5 places lorsque son tirage
    // global de 10 % reussit ; il peut donc apparaitre avec d'autres compagnons.
    private static boolean spawnDewey(ServerLevel level, double x, double y, double z, RandomSource random) {
        Player nearest = level.getNearestPlayer(x, y, z, 200.0, false);
        if (!(nearest instanceof ServerPlayer owner) || !owner.isAlive()) return false;

        Cat dewey = new Cat(EntityType.CAT, level);
        dewey.moveTo(x, y + 1, z, random.nextFloat() * 360, 0);
        dewey.setPersistenceRequired();
        applyCatVariant(dewey, "minecraft:all_black");

        if (dewey.getAttribute(Attributes.MAX_HEALTH) != null) {
            dewey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5.0);
        }
        dewey.setHealth(5.0f);
        dewey.setCustomName(Component.literal("§5§l✦ Dewey ✦"));
        dewey.setCustomNameVisible(true);
        dewey.setGlowingTag(true);

        if (!level.addFreshEntity(dewey)) {
            if (DEBUG_LOGS) deepluckyblock.util.DebugLog.err("[SLB-DEBUG] Failed to add Dewey to the level");
            return false;
        }

        bindCatToOwner(dewey, owner);
        giveEventEffect(owner, MobEffects.REGENERATION, 49, 0,
                false, true, true);
        level.sendParticles(ParticleTypes.END_ROD,
                dewey.getX(), dewey.getY() + 0.5, dewey.getZ(),
                30, 0.6, 0.6, 0.6, 0.05);
        for (int second = 1; second <= 12; second++) {
            TestProcedure.schedule(level, TestProcedure.currentTick(level) + second * 20, () -> {
                if (!dewey.isAlive()) return;
                level.sendParticles(ParticleTypes.END_ROD,
                        dewey.getX(), dewey.getY() + 0.5, dewey.getZ(),
                        8, 0.4, 0.4, 0.4, 0.02);
                level.sendParticles(ParticleTypes.WITCH,
                        dewey.getX(), dewey.getY() + 0.8, dewey.getZ(),
                        4, 0.4, 0.4, 0.4, 0.01);
            });
        }

        refreshDeweyRegen(level, dewey, owner.getUUID());
        owner.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("§5§l").append(
                        Component.translatable("event.deep_lucky_block.dewey.heal_message"))));
        return true;
    }

    // ==================== EVENT LISTENERS ====================

    @SubscribeEvent
    public static void onPoisonedChestOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;

        CompoundTag data = chest.getPersistentData();
        if (!data.getBoolean("slb_poisoned_gift")) return;

        // Un seul clic arme le coffre, MAIS le clic n'est pas annule :
        // le GUI du vrai coffre ULTIMATE s'ouvre normalement avant l'explosion.
        if (data.getBoolean("slb_poison_triggered")) return;
        data.putBoolean("slb_poison_triggered", true);
        chest.setChanged();

        int delay = 10 + level.getRandom().nextInt(31); // 0,5 a 2 secondes
        AABB warningBox = new AABB(
                pos.getX() - 8, pos.getY() - 4, pos.getZ() - 8,
                pos.getX() + 9, pos.getY() + 5, pos.getZ() + 9);
        Component congratulations = Component.literal("§6§l").append(
                Component.translatable("event.deep_lucky_block.poisoned_gift.clicked"));
        for (Player p : level.getEntitiesOfClass(Player.class, warningBox)) {
            if (p instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetActionBarTextPacket(congratulations));
            }
        }
        level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.1f, 1.25f);
        level.sendParticles(ParticleTypes.FIREWORK,
                pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                20, 0.45, 0.45, 0.45, 0.08);

        TestProcedure.schedule(level, TestProcedure.currentTick(level) + delay, () -> {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity armedChest) {
                for (int i = 0; i < armedChest.getContainerSize(); i++) {
                    armedChest.setItem(i, ItemStack.EMPTY);
                }
                armedChest.setChanged();
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }

            float boomBase = 2.0f + level.getRandom().nextInt(3);
            float explosionPower = boomBase + level.getRandom().nextFloat() * boomBase;
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;
            level.explode(null, cx, cy, cz, explosionPower, Level.ExplosionInteraction.BLOCK);

            AABB damageBox = new AABB(cx - 8, cy - 4, cz - 8, cx + 8, cy + 4, cz + 8);
            for (Player p : level.getEntitiesOfClass(Player.class, damageBox)) {
                if (p instanceof ServerPlayer sp && sp.isAlive()) {
                    giveEventEffect(sp, MobEffects.POISON, 400, 2);
                    giveEventEffect(sp, MobEffects.CONFUSION, 300, 1);
                    giveEventEffect(sp, MobEffects.BLINDNESS, 200, 0);
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 10, 1.5, 1.5, 1.5, 0.1);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 40, 2.0, 2.0, 2.0, 0.1);
        });
    }

    @SubscribeEvent
    public static void onMimicChestOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        CompoundTag data = chest.getPersistentData();
        if (!data.getBoolean("slb_mimic_chest")) return;

        event.setCanceled(true);
        if (data.getBoolean("slb_mimic_triggered")) return;
        data.putBoolean("slb_mimic_triggered", true);
        chest.setChanged();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.CHEST_LOCKED,
                SoundSource.HOSTILE, 1.5f, 0.55f);
        level.sendParticles(ParticleTypes.SQUID_INK,
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                30, 0.7, 0.7, 0.7, 0.04);

        Player target = event.getEntity();
        int count = 2 + level.getRandom().nextInt(3);
        for (int i = 0; i < count; i++) {
            Mob mob;
            int type = level.getRandom().nextInt(3);
            if (type == 0) mob = new Zombie(EntityType.ZOMBIE, level);
            else if (type == 1) mob = new Skeleton(EntityType.SKELETON, level);
            else mob = new Pillager(EntityType.PILLAGER, level);

            double angle = Math.PI * 2.0 * i / count;
            mob.moveTo(pos.getX() + 0.5 + Math.cos(angle) * 2.0,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5 + Math.sin(angle) * 2.0,
                    level.getRandom().nextFloat() * 360.0f, 0.0f);
            equipGeneratedMimicMob(mob, level, level.getRandom());
            if (target != null) mob.setTarget(target);
            level.addFreshEntity(mob);
        }
    }

    @SubscribeEvent
    public static void onSkyLootPickedUp(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack pickedStack = event.getItemEntity().getItem();
        CompoundTag pickedTag = deepluckyblock.util.DeepNbt.tag(pickedStack);
        if (pickedTag == null || !pickedTag.getBoolean(SKY_LOOT_TRAP_TAG)) return;

        String trapId = pickedTag.getString(SKY_LOOT_ID_TAG);
        int groundX = pickedTag.getInt(SKY_LOOT_GROUND_X_TAG);
        int groundY = pickedTag.getInt(SKY_LOOT_GROUND_Y_TAG);
        int groundZ = pickedTag.getInt(SKY_LOOT_GROUND_Z_TAG);
        pickedTag.remove(SKY_LOOT_TRAP_TAG);
        pickedTag.remove(SKY_LOOT_ID_TAG);
        pickedTag.remove(SKY_LOOT_GROUND_X_TAG);
        pickedTag.remove(SKY_LOOT_GROUND_Y_TAG);
        pickedTag.remove(SKY_LOOT_GROUND_Z_TAG);

        boolean foundInInventory = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack inventoryStack = player.getInventory().getItem(slot);
            CompoundTag inventoryTag = deepluckyblock.util.DeepNbt.tag(inventoryStack);
            if (inventoryTag == null || !trapId.equals(inventoryTag.getString(SKY_LOOT_ID_TAG))) continue;
            inventoryTag.remove(SKY_LOOT_TRAP_TAG);
            inventoryTag.remove(SKY_LOOT_ID_TAG);
            inventoryTag.remove(SKY_LOOT_GROUND_X_TAG);
            inventoryTag.remove(SKY_LOOT_GROUND_Y_TAG);
            inventoryTag.remove(SKY_LOOT_GROUND_Z_TAG);
            foundInInventory = true;
            break;
        }
        if (!foundInInventory) return;
        player.inventoryMenu.broadcastChanges();

        ServerLevel level = player.serverLevel();
        BlockPos supportPos = new BlockPos(groundX, groundY, groundZ);
        double tntX = supportPos.getX() + 0.5;
        double tntY = supportPos.getY() + 1.0;
        double tntZ = supportPos.getZ() + 0.5;

        level.playSound(null, supportPos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.5f, 1.0f);
        level.sendParticles(ParticleTypes.FLAME, tntX, tntY, tntZ, 30, 0.5, 0.5, 0.5, 0.05);
        actionBar(level, player.getX(), player.getY(), player.getZ(), "c", "Something clicks beneath your feet...");
        narrate(level, player.getX(), player.getY(), player.getZ(), level.getRandom(), "That sound came from below...");

        TestProcedure.schedule(level, TestProcedure.currentTick(level) + 10, () -> {
            float boomBase = 5.0f + level.getRandom().nextInt(5);
            float explosionPower = boomBase + level.getRandom().nextFloat() * boomBase; // base .. x2
            level.explode(null, tntX, tntY, tntZ, explosionPower, Level.ExplosionInteraction.TNT);
        });
    }

    // ==================== EVENT TEST MODE ====================
    private static volatile boolean testRunning = false;
    private static volatile boolean testPbOnly = false;
    private static volatile boolean testClassicOnly = false;
    private static volatile ServerLevel testLevel = null;
    private static volatile ServerPlayer testPlayer = null;
    private static volatile int testIndex = 0;
    private static int testTick = 0;
    private static final int TEST_INTERVAL = 60;
    private static final String[] TEST_CLASSIC = {
        "VoidPocket","EldritchWhispers","MiningFatigue","PhantomRain","AngryBees","PillagerAmbush",
        "YouveBeenSlimed","TheFrenchTax","CursedArmor","InventoryShuffle","SkyLoot","SilverfishInfestation",
        "PoisonCloud","GravityWell","BlindTrap","WitherCurse","CatInvasion","PoisonedGift","WolfPack",
        "SlimeKingBoss","NinjaAssassin","AcidRain","ChaosTerraform","NetherWarp","MiningRollback",
        "EndKnightAmbush","CursedTomb","ArenaOfTheForgotten","CavernOfDespair","ShadowMirrorSpawn",
        "BobTheEternalFisher","AnvilRain","FakeDiamondTroll","CreeperParty","LavaCageTroll",
        "WardenScreamTroll","SpectralArrowShower","SolarEclipse","CursedBellTroll","DragonIllusion","MoltenFootsteps"
    };
    private static final String[] TEST_PB = {
        "PB_Mobs","PB_MobTowers","PB_Megaton","PB_BlockGrave","PB_BlockShower","PB_Transform","PB_Replace",
        "PB_Dryness","PB_TntSplosion","PB_HeightNoise","PB_MadGeometry","PB_MadderGeometry","PB_Classic",
        "PB_Lightning","PB_SandForDessert","PB_InTheEnd","PB_HellOnEarth","PB_Lifeless","PB_TrappedTribe",
        "PB_BuffedDown","PB_Crush","PB_Void","PB_WorldSnake","PB_DoubleSnake","PB_TunnelBore","PB_CreeperSoul",
        "PB_Target","PB_IceAge","PB_TeleRandom","PB_CrazyPort","PB_DangerCall"
    };
    private static final String[] TEST_EXTRA = {
        "MobInvasion", "ChargedCreeperExecution", "EndCrystalMinefield", "TntSkySerpent",
        "NetherBreach", "TheLastPlatform", "SandHourglass", "MagmaCubeMonsoon",
        "CobwebBrood", "SkeletonFiringSquad", "FoodRot", "ChestMimicField", "ShadowDisciples",
        "DeweyOnly", "GravelInventory", "CrewAmbush"
    };

    // Mode immersion : tous les events dans l'ordre, un toutes les 10 secondes,
    // sans aucun prefixe/debug [EventTest] affiche au joueur.
    private static volatile boolean immersiveRunActive = false;
    private static volatile UUID immersiveRunPlayer = null;
    private static volatile int immersiveRunIndex = 0;
    private static volatile int immersiveRunTick = 0;
    private static final int IMMERSIVE_RUN_INTERVAL = 200;

    @SubscribeEvent
    public static void onTestRegisterCommands(RegisterCommandsEvent event) {
        if (!EVENT_TEST_ENABLED) return;
        event.getDispatcher().register(
            Commands.literal("eventtest")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> { startTest(ctx.getSource().getPlayerOrException(), false, false); return 1; })
                .then(Commands.literal("stop").executes(ctx -> { stopTest(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("pb").executes(ctx -> { startTest(ctx.getSource().getPlayerOrException(), true, false); return 1; }))
                .then(Commands.literal("classic").executes(ctx -> { startTest(ctx.getSource().getPlayerOrException(), false, true); return 1; }))
                .then(Commands.literal("end").executes(ctx -> { runSingleEvent(ctx.getSource().getPlayerOrException(), "PB_InTheEnd"); return 1; }))
                .then(Commands.literal("nether").executes(ctx -> { runSingleEvent(ctx.getSource().getPlayerOrException(), "PB_HellOnEarth"); return 1; }))
                .then(Commands.literal("serpent1").executes(ctx -> { runSingleEvent(ctx.getSource().getPlayerOrException(), "PB_WorldSnake"); return 1; }))
                .then(Commands.literal("serpent2").executes(ctx -> { runSingleEvent(ctx.getSource().getPlayerOrException(), "PB_DoubleSnake"); return 1; }))
                .then(Commands.literal("tunnel").executes(ctx -> { runSingleEvent(ctx.getSource().getPlayerOrException(), "PB_TunnelBore"); return 1; }))
                .then(Commands.literal("run")
                    .then(Commands.argument("number", IntegerArgumentType.integer(1, TOTAL_NEGATIVE_EVENTS))
                        .executes(ctx -> {
                            runSingleEventByNumber(
                                    ctx.getSource().getPlayerOrException(),
                                    IntegerArgumentType.getInteger(ctx, "number"));
                            return 1;
                        })))
        );

        event.getDispatcher().register(
            Commands.literal("eventrun")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> { startImmersiveRun(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("stop").executes(ctx -> { stopImmersiveRun(); return 1; }))
        );
    }

    @SubscribeEvent
    public static void onTestServerTick(ServerTickEvent.Post event) {
        if (!testRunning || testLevel == null || testPlayer == null) return;
        if (testLevel.getServer().getPlayerList().getPlayer(testPlayer.getUUID()) == null) { stopTest(null); return; }
        testTick++;
        if (testTick < TEST_INTERVAL) return;
        testTick = 0;

        String[] evs;
        if (testPbOnly) evs = TEST_PB;
        else if (testClassicOnly) evs = allClassicEvents();
        else evs = allOrderedEvents();

        if (testIndex >= evs.length) {
            testPlayer.sendSystemMessage(Component.literal("\u00a7a\u00a7l[EventTest] Termine! " + evs.length + " events."));
            stopTest(testPlayer); return;
        }

        String name = evs[testIndex];
        RandomSource rng = testLevel.getRandom();
        double x = testPlayer.getX(), y = testPlayer.getY(), z = testPlayer.getZ();
        TestProcedure.BranchResult res = new TestProcedure.BranchResult();
        res.negative = true; res.branchLuck = 50;
        res.items = new ArrayList<>(); res.particles = new ArrayList<>(); res.deferredAction = null;

        try {
            String m = "event_" + name;
            try {
                java.lang.reflect.Method rm = GenerateBadProcedure.class.getDeclaredMethod(m, ServerLevel.class, double.class, double.class, double.class, RandomSource.class, TestProcedure.BranchResult.class, int.class);
                rm.setAccessible(true); rm.invoke(null, testLevel, x, y, z, rng, res, 50);
            } catch (NoSuchMethodException e) {
                java.lang.reflect.Method rm = GenerateBadProcedure.class.getDeclaredMethod(m, ServerLevel.class, double.class, double.class, double.class, RandomSource.class, TestProcedure.BranchResult.class);
                rm.setAccessible(true); rm.invoke(null, testLevel, x, y, z, rng, res);
            }
        } catch (Exception e) {
            testPlayer.sendSystemMessage(Component.literal("\u00a7c[EventTest] Err " + name + ": " + e.getMessage()));
        }

        if (res.title != null) {
            String col = res.titleColorCode != null && res.titleColorCode.length() == 1
                    ? res.titleColorCode : "d";
            testPlayer.sendSystemMessage(
                    Component.literal("\u00a77[" + (testIndex + 1) + "/" + evs.length + "] \u00a7" + col + "\u00a7l")
                            .append(localizeEventTitle(res.title)));
        }
        if (res.deferredAction != null) { try { res.deferredAction.run(); } catch (Throwable e) { deepluckyblock.util.DebugLog.trace(e); } }
        testIndex++;
    }

    private static String[] allClassicEvents() {
        String[] all = new String[TEST_CLASSIC.length + TEST_EXTRA.length];
        System.arraycopy(TEST_CLASSIC, 0, all, 0, TEST_CLASSIC.length);
        System.arraycopy(TEST_EXTRA, 0, all, TEST_CLASSIC.length, TEST_EXTRA.length);
        return all;
    }

    private static String[] allOrderedEvents() {
        // Ordre canonique identique aux cases 1-87 de generate().
        String[] all = new String[TOTAL_NEGATIVE_EVENTS];
        System.arraycopy(TEST_CLASSIC, 0, all, 0, TEST_CLASSIC.length);
        System.arraycopy(TEST_PB, 0, all, TEST_CLASSIC.length, TEST_PB.length);
        System.arraycopy(TEST_EXTRA, 0, all, TEST_CLASSIC.length + TEST_PB.length, TEST_EXTRA.length);
        return all;
    }

    private static void runSingleEventByNumber(ServerPlayer player, int number) {
        String[] events = allOrderedEvents();
        int index = Mth.clamp(number, 1, events.length) - 1;
        runSingleEvent(player, events[index]);
    }

    private static TestProcedure.BranchResult createNamedEventResult(ServerPlayer player, String name) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        TestProcedure.BranchResult result = new TestProcedure.BranchResult();
        result.negative = true;
        result.branchLuck = 50;
        result.items = new ArrayList<>();
        result.particles = new ArrayList<>();
        result.deferredAction = null;

        String methodName = "event_" + name;
        try {
            Method method;
            try {
                method = GenerateBadProcedure.class.getDeclaredMethod(methodName,
                        ServerLevel.class, double.class, double.class, double.class,
                        RandomSource.class, TestProcedure.BranchResult.class, int.class);
                method.setAccessible(true);
                method.invoke(null, level, player.getX(), player.getY(), player.getZ(),
                        random, result, 50);
            } catch (NoSuchMethodException ignored) {
                method = GenerateBadProcedure.class.getDeclaredMethod(methodName,
                        ServerLevel.class, double.class, double.class, double.class,
                        RandomSource.class, TestProcedure.BranchResult.class);
                method.setAccessible(true);
                method.invoke(null, level, player.getX(), player.getY(), player.getZ(),
                        random, result);
            }
            return result;
        } catch (Throwable error) {
            if (DEBUG_LOGS) deepluckyblock.util.DebugLog.trace(error);
            return null;
        }
    }

    private static void startImmersiveRun(ServerPlayer player) {
        testRunning = false; // evite que les deux runners se melangent
        immersiveRunActive = true;
        immersiveRunPlayer = player.getUUID();
        immersiveRunIndex = 0;
        immersiveRunTick = IMMERSIVE_RUN_INTERVAL; // premier event immediat
    }

    private static void stopImmersiveRun() {
        immersiveRunActive = false;
        immersiveRunPlayer = null;
        immersiveRunIndex = 0;
        immersiveRunTick = 0;
    }

    @SubscribeEvent
    public static void onImmersiveRunTick(ServerTickEvent.Post event) {
        if (!immersiveRunActive || immersiveRunPlayer == null) return;
        immersiveRunTick++;
        if (immersiveRunTick < IMMERSIVE_RUN_INTERVAL) return;
        immersiveRunTick = 0;

        ServerPlayer player = event.getServer().getPlayerList().getPlayer(immersiveRunPlayer);
        if (player == null) {
            stopImmersiveRun();
            return;
        }

        String[] events = allOrderedEvents();
        if (immersiveRunIndex >= events.length) {
            stopImmersiveRun();
            return;
        }

        TestProcedure.BranchResult result = createNamedEventResult(player, events[immersiveRunIndex++]);
        if (result == null) return;

        // Teaser immersif uniquement : aucun index, nom de code ou prefixe debug.
        if (result.title != null && !result.title.isBlank()) {
            String color = result.titleColorCode != null && result.titleColorCode.length() == 1
                    ? result.titleColorCode : "d";
            Component teaser = Component.literal("§" + color + "§l")
                    .append(localizeEventTitle(result.title));
            player.connection.send(new ClientboundSetActionBarTextPacket(teaser));
        }

        if (result.deferredAction != null) {
            Runnable action = result.deferredAction;
            ServerLevel actionLevel = player.serverLevel();
            TestProcedure.schedule(actionLevel, TestProcedure.currentTick(actionLevel) + 20, () -> {
                try {
                    action.run();
                } catch (Throwable error) {
                    if (DEBUG_LOGS) deepluckyblock.util.DebugLog.trace(error);
                }
            });
        }
    }

    private static void startTest(ServerPlayer player, boolean pb, boolean classic) {
        stopTest(player);
        testRunning = true; testPbOnly = pb; testClassicOnly = classic;
        testLevel = player.serverLevel(); testPlayer = player; testIndex = 0; testTick = TEST_INTERVAL;
        player.sendSystemMessage(Component.literal("\u00a7e\u00a7l[EventTest] " + (pb ? "Pandora" : classic ? "Classique" : "Tous") + " | /eventtest stop"));
    }

    private static void stopTest(ServerPlayer player) {
        if (!testRunning && player != null) { player.sendSystemMessage(Component.literal("\u00a77[EventTest] Inactif.")); return; }
        testRunning = false; testPbOnly = false; testClassicOnly = false;
        testLevel = null; testPlayer = null; testIndex = 0; testTick = 0;
        if (player != null) player.sendSystemMessage(Component.literal("\u00a7c\u00a7l[EventTest] Arret."));
    }

    // Lance un seul evenement (par nom de code : PB_InTheEnd, PB_WorldSnake, VoidPocket, etc.) a la position du joueur.
    private static void runSingleEvent(ServerPlayer player, String name) {
        ServerLevel level = player.serverLevel();
        RandomSource rng = level.getRandom();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        TestProcedure.BranchResult res = new TestProcedure.BranchResult();
        res.negative = true; res.branchLuck = 50;
        res.items = new ArrayList<>(); res.particles = new ArrayList<>(); res.deferredAction = null;
        String m = "event_" + name;
        try {
            java.lang.reflect.Method rm;
            try {
                rm = GenerateBadProcedure.class.getDeclaredMethod(m, ServerLevel.class, double.class, double.class, double.class, RandomSource.class, TestProcedure.BranchResult.class, int.class);
                rm.setAccessible(true); rm.invoke(null, level, x, y, z, rng, res, 50);
            } catch (NoSuchMethodException e) {
                rm = GenerateBadProcedure.class.getDeclaredMethod(m, ServerLevel.class, double.class, double.class, double.class, RandomSource.class, TestProcedure.BranchResult.class);
                rm.setAccessible(true); rm.invoke(null, level, x, y, z, rng, res);
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("\u00a7c[eventtest] Err " + name + ": " + e.getMessage()));
            return;
        }
        if (res.title != null) {
            String col = res.titleColorCode != null && res.titleColorCode.length() == 1
                    ? res.titleColorCode : "d";
            player.sendSystemMessage(Component.literal("\u00a77[eventtest] \u00a7" + col + "\u00a7l")
                    .append(localizeEventTitle(res.title)));
        }
        if (res.deferredAction != null) { try { res.deferredAction.run(); } catch (Throwable e) { deepluckyblock.util.DebugLog.trace(e); } }
    }

    // ==================== ENTRYPOINT ====================

    public static TestProcedure.BranchResult generate(Level level, double x, double y, double z,
                                                       int mainLuck, RandomSource random) {
        return generate(level, x, y, z, mainLuck, random,
                randomEuropeanRouletteCategory(random));
    }

    public static TestProcedure.BranchResult generate(Level level, double x, double y, double z,
                                                       int mainLuck, RandomSource random,
                                                       BadEventCategory category) {
        if (!(level instanceof ServerLevel server)) return null;

        // 60% des events se localisent sur le joueur (inescapables), 40% sur le bloc.
        // Le Void Pocket reste TOUJOURS sur le bloc (coords originales ox/oy/oz).
        Player breaker = server.getNearestPlayer(x, y, z, 200.0, false);
        double ox = x, oy = y, oz = z;
        if (breaker != null && random.nextDouble() < 0.60) {
            x = breaker.getX();
            y = breaker.getY() + 1.0;
            z = breaker.getZ();
        }

        int eventId = pickCategorizedEventId(category, random);
        TestProcedure.BranchResult result = new TestProcedure.BranchResult();
        result.negative = true;
        result.branchLuck = mainLuck;
        result.badEventNumber = eventId + 1;
        result.items = new ArrayList<>();
        result.particles = new ArrayList<>();
        result.deferredAction = null;

        // La roulette choisit BLACK / RED / GREEN, puis un event du pool.
        // GREEN conserve sa ponderation speciale CatInvasion a 17 %.
        switch (eventId) {
            case 0  -> event_VoidPocket(server, ox, oy, oz, random, result);
            case 1  -> event_EldritchWhispers(server, x, y, z, random, result);
            case 2  -> event_MiningFatigue(server, x, y, z, random, result);
            case 3  -> event_PhantomRain(server, x, y, z, random, result);
            case 4  -> event_AngryBees(server, x, y, z, random, result);
            case 5  -> event_PillagerAmbush(server, x, y, z, random, result);
            case 6  -> event_YouveBeenSlimed(server, x, y, z, random, result);
            case 7  -> event_TheFrenchTax(server, x, y, z, random, result);
            case 8  -> event_CursedArmor(server, x, y, z, random, result);
            case 9  -> event_InventoryShuffle(server, x, y, z, random, result);
            case 10 -> event_SkyLoot(server, x, y, z, random, result);
            case 11 -> event_SilverfishInfestation(server, x, y, z, random, result);
            case 12 -> event_PoisonCloud(server, x, y, z, random, result);
            case 13 -> event_GravityWell(server, x, y, z, random, result);
            case 14 -> event_BlindTrap(server, x, y, z, random, result);
            case 15 -> event_WitherCurse(server, x, y, z, random, result);
            case 16 -> event_CatInvasion(server, x, y, z, random, result);
            case 17 -> event_PoisonedGift(server, ox, oy, oz, random, result);
            case 18 -> event_WolfPack(server, x, y, z, random, result);
            case 19 -> event_SlimeKingBoss(server, x, y, z, random, result);
            case 20 -> event_NinjaAssassin(server, x, y, z, random, result);
            case 21 -> event_AcidRain(server, x, y, z, random, result);
            case 22 -> event_ChaosTerraform(server, x, y, z, random, result);
            case 23 -> event_NetherWarp(server, x, y, z, random, result);
            case 24 -> event_MiningRollback(server, x, y, z, random, result);
            case 25 -> event_EndKnightAmbush(server, x, y, z, random, result);
            case 26 -> event_CursedTomb(server, x, y, z, random, result, mainLuck);
            case 27 -> event_ArenaOfTheForgotten(server, x, y, z, random, result, mainLuck);
            case 28 -> event_CavernOfDespair(server, x, y, z, random, result);
            case 29 -> event_ShadowMirrorSpawn(server, x, y, z, random, result);
            case 30 -> event_BobTheEternalFisher(server, x, y, z, random, result);
            case 31 -> event_AnvilRain(server, x, y, z, random, result);
            case 32 -> event_FakeDiamondTroll(server, x, y, z, random, result);
            case 33 -> event_CreeperParty(server, ox, oy, oz, random, result);
            case 34 -> event_LavaCageTroll(server, x, y, z, random, result);
            case 35 -> event_WardenScreamTroll(server, x, y, z, random, result);
            case 36 -> event_SpectralArrowShower(server, x, y, z, random, result);
            case 37 -> event_SolarEclipse(server, x, y, z, random, result);
            case 38 -> event_CursedBellTroll(server, x, y, z, random, result);
            case 39 -> event_DragonIllusion(server, x, y, z, random, result);
            case 40 -> event_MoltenFootsteps(server, x, y, z, random, result);
            case 41 -> event_PB_Mobs(server, x, y, z, random, result);
            case 42 -> event_PB_MobTowers(server, x, y, z, random, result);
            case 43 -> event_PB_Megaton(server, x, y, z, random, result);
            case 44 -> event_PB_BlockGrave(server, x, y, z, random, result);
            case 45 -> event_PB_BlockShower(server, x, y, z, random, result);
            case 46 -> event_PB_Transform(server, x, y, z, random, result);
            case 47 -> event_PB_Replace(server, x, y, z, random, result);
            case 48 -> event_PB_Dryness(server, x, y, z, random, result);
            case 49 -> event_PB_TntSplosion(server, x, y, z, random, result);
            case 50 -> event_PB_HeightNoise(server, x, y, z, random, result);
            case 51 -> event_PB_MadGeometry(server, x, y, z, random, result);
            case 52 -> event_PB_MadderGeometry(server, x, y, z, random, result);
            case 53 -> event_PB_Classic(server, x, y, z, random, result);
            case 54 -> event_PB_Lightning(server, x, y, z, random, result);
            case 55 -> event_PB_SandForDessert(server, x, y, z, random, result);
            case 56 -> event_PB_InTheEnd(server, x, y, z, random, result);
            case 57 -> event_PB_HellOnEarth(server, x, y, z, random, result);
            case 58 -> event_PB_Lifeless(server, x, y, z, random, result);
            case 59 -> event_PB_TrappedTribe(server, x, y, z, random, result);
            case 60 -> event_PB_BuffedDown(server, x, y, z, random, result);
            case 61 -> event_PB_Crush(server, x, y, z, random, result);
            case 62 -> event_PB_Void(server, x, y, z, random, result);
            case 63 -> event_PB_WorldSnake(server, x, y, z, random, result);
            case 64 -> event_PB_DoubleSnake(server, x, y, z, random, result);
            case 65 -> event_PB_TunnelBore(server, x, y, z, random, result);
            case 66 -> event_PB_CreeperSoul(server, x, y, z, random, result);
            case 67 -> event_PB_Target(server, x, y, z, random, result);
            case 68 -> event_PB_IceAge(server, x, y, z, random, result);
            case 69 -> event_PB_TeleRandom(server, x, y, z, random, result);
            case 70 -> event_PB_CrazyPort(server, x, y, z, random, result);
            case 71 -> event_PB_DangerCall(server, x, y, z, random, result);
            case 72 -> event_MobInvasion(server, x, y, z, random, result);
            case 73 -> event_ChargedCreeperExecution(server, ox, oy, oz, random, result);
            case 74 -> event_EndCrystalMinefield(server, x, y, z, random, result);
            case 75 -> event_TntSkySerpent(server, x, y, z, random, result);
            case 76 -> event_NetherBreach(server, x, y, z, random, result);
            case 77 -> event_TheLastPlatform(server, x, y, z, random, result);
            case 78 -> event_SandHourglass(server, x, y, z, random, result);
            case 79 -> event_MagmaCubeMonsoon(server, x, y, z, random, result);
            case 80 -> event_CobwebBrood(server, x, y, z, random, result);
            case 81 -> event_SkeletonFiringSquad(server, x, y, z, random, result);
            case 82 -> event_FoodRot(server, x, y, z, random, result);
            case 83 -> event_ChestMimicField(server, x, y, z, random, result);
            case 84 -> event_ShadowDisciples(server, x, y, z, random, result);
            case 85 -> event_DeweyOnly(server, x, y, z, random, result);
            case 86 -> event_GravelInventory(server, x, y, z, random, result);
            case 87 -> event_CrewAmbush(server, x, y, z, random, result);
            default -> throw new IllegalStateException("Unhandled negative event id: " + eventId);
        }

        // La couleur/rank visible correspond TOUJOURS a la poche de roulette,
        // meme si l'event possedait historiquement une autre couleur locale.
        switch (category) {
            case BLACK -> {
                result.titleColorCode = "0";
                result.rank = TestProcedure.ItemRank.BROKEN;
            }
            case RED -> {
                result.titleColorCode = "c";
                result.rank = TestProcedure.ItemRank.CURSED;
            }
            case GREEN -> {
                result.titleColorCode = "a";
                result.rank = TestProcedure.ItemRank.COMMON;
                result.particles.add("HAPPY_VILLAGER");
            }
        }

        if (result.particles.isEmpty()) {
            result.particles.add("SMOKE");
            result.particles.add("SOUL");
        }

        // DEBUG: wrap deferredAction avec logs de timing pour identifier les events lents/freezants
        if (result.deferredAction != null) {
            final String dbgName = result.title != null ? result.title : ("eventId=" + eventId);
            final Runnable orig = result.deferredAction;
            result.deferredAction = () -> {
                long t0 = System.currentTimeMillis();
                if (DEBUG_LOGS) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] >>> START " + dbgName);
                try {
                    orig.run();
                } catch (Throwable e) {
                    deepluckyblock.util.DebugLog.err("[SLB-DEBUG] ERROR " + dbgName + ": " + e);
                    deepluckyblock.util.DebugLog.trace(e);
                }
                long ms = System.currentTimeMillis() - t0;
                if (DEBUG_LOGS) {
                    if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] <<< END " + dbgName + " (" + ms + "ms)");
                    if (ms > 100) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] !!! LENT: " + dbgName + " a pris " + ms + "ms");
                }
            };
        }
        return result;
    }

    // ==================== CORE HELPERS ====================

    /**
     * Applique un effet temporaire appartenant a GenerateBad sans jamais faire de
     * clear global. L'effet deja present est laisse a Minecraft, qui le conserve
     * comme effet masque s'il doit reprendre apres le notre.
     */
    private static void giveEventEffect(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect,
                                        int duration, int amplifier,
                                        boolean ambient, boolean visible, boolean showIcon) {
        if (player == null || effect == null || duration <= 0 || !player.isAlive()) return;

        // Sauvegarde AVANT addEffect : l'instance active peut etre modifiee en
        // place par Minecraft lors de la fusion des effets.
        CompoundTag previousTag = null;
        MobEffectInstance previous = player.getEffect(effect);
        if (previous != null) {
            // PORT 1.21.1 : plus de CompoundTag.putAll.
            previousTag = (net.minecraft.nbt.CompoundTag) previous.save();
        }

        MobEffectInstance requested = new MobEffectInstance(
                effect, duration, amplifier, ambient, visible, showIcon);
        player.addEffect(requested);

        // Meme si un effet plus fort reste visible, Minecraft peut placer le
        // notre dans sa chaine HiddenEffect. On garde donc le garde-fou : il sera
        // conservateur et ne touchera pas l'effet prioritaire s'il est encore la.
        ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.value());
        MobEffectInstance activeAfter = player.getEffect(effect);
        if (effectId == null || activeAfter == null) return;

        CompoundTag persistent = player.getPersistentData();
        ListTag guards = persistent.getList(EVENT_EFFECT_GUARDS_TAG, 10);
        while (guards.size() >= EFFECT_GUARD_MAX_ENTRIES) guards.remove(0);

        long now = player.serverLevel().getGameTime();
        CompoundTag guard = new CompoundTag();
        guard.putString(EFFECT_GUARD_EFFECT_TAG, effectId.toString());
        guard.putLong(EFFECT_GUARD_APPLIED_TAG, now);
        guard.putLong(EFFECT_GUARD_EXPIRES_TAG,
                now + duration + EFFECT_GUARD_GRACE_TICKS);
        guard.putInt(EFFECT_GUARD_AMPLIFIER_TAG, amplifier);
        guard.putInt(EFFECT_GUARD_ENTITY_TAG, player.getId());
        if (previousTag != null) {
            guard.put(EFFECT_GUARD_PREVIOUS_TAG, previousTag);
        }
        guards.add(guard);
        persistent.put(EVENT_EFFECT_GUARDS_TAG, guards);
    }

    private static void giveEventEffect(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect,
                                        int duration, int amplifier) {
        giveEventEffect(player, effect, duration, amplifier,
                false, true, true);
    }

    /** Vieillit une sauvegarde MobEffectInstance, y compris sa chaine HiddenEffect. */
    private static CompoundTag ageSavedEffect(CompoundTag saved, int elapsedTicks) {
        if (saved == null || saved.isEmpty()) return null;
        CompoundTag aged = saved.copy();

        CompoundTag agedHidden = null;
        if (aged.contains("HiddenEffect", 10)) {
            agedHidden = ageSavedEffect(aged.getCompound("HiddenEffect"), elapsedTicks);
            if (agedHidden == null) aged.remove("HiddenEffect");
            else aged.put("HiddenEffect", agedHidden);
        }

        int duration = aged.getInt("Duration");
        if (duration == MobEffectInstance.INFINITE_DURATION) return aged;

        long remainingLong = (long) duration - elapsedTicks;
        if (remainingLong > 0L) {
            aged.putInt("Duration", (int) Math.min(Integer.MAX_VALUE, remainingLong));
            return aged;
        }

        // Comme le tick vanilla : si l'effet visible expire, l'effet masque
        // encore valide reprend. Sinon il ne reste rien a restaurer.
        return agedHidden;
    }

    private static void sendExplicitEffectRemoval(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect) {
        player.connection.send(new ClientboundRemoveMobEffectPacket(player.getId(), effect));
    }

    private static void cleanExpiredEventEffectGuards(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(EVENT_EFFECT_GUARDS_TAG, 9)) return;

        ListTag guards = persistent.getList(EVENT_EFFECT_GUARDS_TAG, 10);
        if (guards.isEmpty()) {
            persistent.remove(EVENT_EFFECT_GUARDS_TAG);
            return;
        }

        long now = player.serverLevel().getGameTime();
        for (int index = guards.size() - 1; index >= 0; index--) {
            CompoundTag guard = guards.getCompound(index);
            if (now < guard.getLong(EFFECT_GUARD_EXPIRES_TAG)) continue;

            ResourceLocation effectId = ResourceLocation.tryParse(
                    guard.getString(EFFECT_GUARD_EFFECT_TAG));
            net.minecraft.core.Holder<MobEffect> effect = effectId != null
                    ? BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
                    : null;
            if (effect == null) {
                guards.remove(index);
                continue;
            }

            MobEffectInstance current = player.getEffect(effect);
            if (current == null) {
                // Le serveur l'a deja retire normalement, mais ce paquet force le
                // client a abandonner une eventuelle icone fantome a 00:00.
                sendExplicitEffectRemoval(player, effect);
                guards.remove(index);
                continue;
            }

            boolean matchesOurExpiredEffect =
                    current.getAmplifier() == guard.getInt(EFFECT_GUARD_AMPLIFIER_TAG)
                    && !current.isInfiniteDuration()
                    && current.getDuration() <= EFFECT_GUARD_GRACE_TICKS;

            if (matchesOurExpiredEffect) {
                // Retire UNIQUEMENT l'effet arrive a expiration et identifie par
                // type + amplificateur + duree. Aucun autre effet n'est clear.
                player.removeEffect(effect);
                sendExplicitEffectRemoval(player, effect);

                // Si le joueur possedait deja cet effet, on le restaure avec sa
                // duree reellement restante. Pas de restauration apres respawn :
                // cela respecterait mal un deces ou un nettoyage volontaire.
                if (player.getId() == guard.getInt(EFFECT_GUARD_ENTITY_TAG)
                        && guard.contains(EFFECT_GUARD_PREVIOUS_TAG, 10)) {
                    long elapsedLong = Math.max(0L,
                            now - guard.getLong(EFFECT_GUARD_APPLIED_TAG));
                    int elapsed = (int) Math.min(Integer.MAX_VALUE, elapsedLong);
                    CompoundTag agedPrevious = ageSavedEffect(
                            guard.getCompound(EFFECT_GUARD_PREVIOUS_TAG), elapsed);
                    if (agedPrevious != null && player.getEffect(effect) == null) {
                        MobEffectInstance restored = MobEffectInstance.load(agedPrevious);
                        if (restored != null) player.addEffect(restored);
                    }
                }
            }
            // Si un effet different, plus long, plus fort ou infini est actif,
            // il appartient potentiellement au joueur/a un autre mod : intouchable.
            guards.remove(index);
        }

        if (guards.isEmpty()) persistent.remove(EVENT_EFFECT_GUARDS_TAG);
        else persistent.put(EVENT_EFFECT_GUARDS_TAG, guards);
    }

    private static void cleanLegacyExpiredEffects(ServerPlayer player) {
        List<net.minecraft.core.Holder<MobEffect>> expired = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            // -1 signifie volontairement INFINI : ne jamais le toucher. Seules
            // les instances finies deja arrivees a 0 (etat anormal) sont retirees.
            if (!instance.isInfiniteDuration() && instance.getDuration() <= 0) {
                expired.add(instance.getEffect());
            }
        }
        for (net.minecraft.core.Holder<MobEffect> effect : expired) {
            player.removeEffect(effect);
            sendExplicitEffectRemoval(player, effect);
        }
    }

    @SubscribeEvent
    public static void onEventEffectGuardPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        // Les effets suivis passent d'abord afin de pouvoir restaurer proprement
        // un effet anterieur. Le filet legacy ne traite ensuite que les 00:00
        // orphelins crees avant ce patch.
        cleanExpiredEventEffectGuards(player);
        cleanLegacyExpiredEffects(player);
    }

    private static void giveEffect(ServerLevel level, double x, double y, double z,
                                    double radius, net.minecraft.core.Holder<MobEffect> effect, int duration, int amplifier) {
        if (duration <= 0) return;
        Player target = level.getNearestPlayer(x, y, z, 200.0, false);
        if (!(target instanceof ServerPlayer sp) || !sp.isAlive()) return;
        giveEventEffect(sp, effect, duration, amplifier);
    }

    private static BlockPos findDrySolidBelow(ServerLevel level, double x, double y, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int startY = Math.min((int) Math.floor(y), level.getMaxBuildHeight() - 1);

        for (int blockY = startY; blockY >= level.getMinBuildHeight(); blockY--) {
            BlockPos pos = new BlockPos(blockX, blockY, blockZ);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return null;
            if (state.blocksMotion()) return pos;
        }
        return null;
    }

    private static BlockPos findDrySkyLootGround(ServerLevel level, ServerPlayer player,
                                                   double spawnY, RandomSource random) {
        List<BlockPos> candidates = new ArrayList<>();
        int centerX = (int) Math.floor(player.getX());
        int centerZ = (int) Math.floor(player.getZ());

        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double candidateX = centerX + dx + 0.5;
                double candidateZ = centerZ + dz + 0.5;
                double offsetX = candidateX - player.getX();
                double offsetZ = candidateZ - player.getZ();
                double distanceSquared = offsetX * offsetX + offsetZ * offsetZ;
                if (distanceSquared < 4.0 || distanceSquared > 36.0) continue;

                BlockPos support = findDrySolidBelow(level, candidateX, spawnY, candidateZ);
                if (support == null || support.getY() + 1.0 >= spawnY) continue;
                candidates.add(support);
            }
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static int randomDuration(RandomSource random) { return 20 * (5 + random.nextInt(26)); }

    // Les Components translatable sont resolus par CHAQUE client selon sa langue.
    // Aucune detection serveur de la langue n'est necessaire.
    public static Component localizeEventTitle(String title) {
        if (title == null) return Component.empty();
        String key = switch (title) {
            case "The ground feels too thin..." -> "event.deep_lucky_block.void_pocket.title";
            case "Old voices fill your mind..." -> "event.deep_lucky_block.eldritch_whispers.title";
            case "Your arms turn to lead..." -> "event.deep_lucky_block.mining_fatigue.title";
            case "Wings circle overhead..." -> "event.deep_lucky_block.phantom_rain.title";
            case "A buzz swells in the air..." -> "event.deep_lucky_block.angry_bees.title";
            case "Crossbows cock nearby..." -> "event.deep_lucky_block.pillager_ambush.title";
            case "Something wet and green..." -> "event.deep_lucky_block.youve_been_slimed.title";
            case "You hear coins jingling..." -> "event.deep_lucky_block.the_french_tax.title";
            case "Your gear clings too tight..." -> "event.deep_lucky_block.cursed_armor.title";
            case "Your bags stir on their own..." -> "event.deep_lucky_block.inventory_shuffle.title";
            case "A gleam drops from above..." -> "event.deep_lucky_block.sky_loot.title";
            case "The stone starts to writhe..." -> "event.deep_lucky_block.silverfish_infestation.title";
            case "A bitter fog creeps in..." -> "event.deep_lucky_block.poison_cloud.title";
            case "The world tugs at you..." -> "event.deep_lucky_block.gravity_well.title";
            case "The light drains away..." -> "event.deep_lucky_block.blind_trap.title";
            case "A chill bites your bones..." -> "event.deep_lucky_block.wither_curse.title";
            case "Two golden eyes watch from the dark..." -> "event.deep_lucky_block.cat_invasion_dewey.title";
            case "Soft paws pad closer..." -> "event.deep_lucky_block.cat_invasion.title";
            case "Congratulations! Your reward is waiting..." -> "event.deep_lucky_block.poisoned_gift.title";
            case "Howls close in around you..." -> "event.deep_lucky_block.wolf_pack.title";
            case "Something huge stirs below..." -> "event.deep_lucky_block.slime_king_boss.title";
            case "Unseen eyes are watching..." -> "event.deep_lucky_block.ninja_assassin.title";
            case "The air turns acrid..." -> "event.deep_lucky_block.acid_rain.title";
            case "The land begins to warp..." -> "event.deep_lucky_block.chaos_terraform.title";
            case "A portal drags at you..." -> "event.deep_lucky_block.nether_warp.title";
            case "Your tools suddenly feel wrong..." -> "event.deep_lucky_block.mining_rollback.title";
            case "Tears open in the air..." -> "event.deep_lucky_block.end_knight_ambush.title";
            case "An old tomb crawls upward..." -> "event.deep_lucky_block.cursed_tomb.title";
            case "Walls rise to cage you..." -> "event.deep_lucky_block.arena_of_the_forgotten.title";
            case "The floor gives way..." -> "event.deep_lucky_block.cavern_of_despair.title";
            case "Your shadow steps free..." -> "event.deep_lucky_block.shadow_mirror_spawn.title";
            case "A fishing line snaps taut..." -> "event.deep_lucky_block.bob_the_eternal_fisher.title";
            case "Better watch the sky..." -> "event.deep_lucky_block.anvil_rain.title";
            case "Gems twinkle nearby..." -> "event.deep_lucky_block.fake_diamond.title";
            case "A surprise is brewing..." -> "event.deep_lucky_block.creeper_party.title";
            case "Glass walls close in..." -> "event.deep_lucky_block.lava_cage.title";
            case "The earth shudders deep..." -> "event.deep_lucky_block.warden_scream.title";
            case "Arrows hum overhead..." -> "event.deep_lucky_block.spectral_arrow_shower.title";
            case "The sun begins to fade..." -> "event.deep_lucky_block.solar_eclipse.title";
            case "A bell starts to toll..." -> "event.deep_lucky_block.cursed_bell.title";
            case "Vast wings beat above..." -> "event.deep_lucky_block.dragon_illusion.title";
            case "The ground grows warm..." -> "event.deep_lucky_block.molten_footsteps.title";
            case "They are closing in..." -> "event.deep_lucky_block.pb_mobs.title";
            case "They climb upon each other..." -> "event.deep_lucky_block.pb_mob_towers.title";
            case "Heavy things plummet..." -> "event.deep_lucky_block.pb_megaton.title";
            case "Stone rains from above..." -> "event.deep_lucky_block.pb_block_grave.title";
            case "Debris showers down..." -> "event.deep_lucky_block.pb_block_shower.title";
            case "The ground shifts its skin..." -> "event.deep_lucky_block.pb_transform.title";
            case "The earth is rewritten..." -> "event.deep_lucky_block.pb_replace.title";
            case "The land grows parched..." -> "event.deep_lucky_block.pb_dryness.title";
            case "Fuses spark all around..." -> "event.deep_lucky_block.pb_tnt_splosion.title";
            case "The ground heaves upward..." -> "event.deep_lucky_block.pb_height_noise.title";
            case "Strange shapes bloom..." -> "event.deep_lucky_block.pb_mad_geometry.title";
            case "Madness takes solid form..." -> "event.deep_lucky_block.pb_madder_geometry.title";
            case "Odd towers sprout up..." -> "event.deep_lucky_block.pb_classic.title";
            case "The storm tears open..." -> "event.deep_lucky_block.pb_lightning.title";
            case "Dust chokes the air..." -> "event.deep_lucky_block.pb_sand_for_dessert.title";
            case "Reality frays at the edges..." -> "event.deep_lucky_block.pb_in_the_end.title";
            case "Heat wells up from below..." -> "event.deep_lucky_block.pb_hell_on_earth.title";
            case "Life drains from the land..." -> "event.deep_lucky_block.pb_lifeless.title";
            case "Steel whispers down..." -> "event.deep_lucky_block.pb_trapped_tribe.title";
            case "Strength flees the area..." -> "event.deep_lucky_block.pb_buffed_down.title";
            case "An unseen weight presses..." -> "event.deep_lucky_block.pb_crush.title";
            case "The ground opens beneath..." -> "event.deep_lucky_block.pb_void.title";
            case "Something coils above..." -> "event.deep_lucky_block.pb_world_snake.title";
            case "Twin shapes wind above..." -> "event.deep_lucky_block.pb_double_snake.title";
            case "Something bores below..." -> "event.deep_lucky_block.pb_tunnel_bore.title";
            case "The air crackles hot..." -> "event.deep_lucky_block.pb_creeper_soul.title";
            case "Marks bloom on the ground..." -> "event.deep_lucky_block.pb_target.title";
            case "A deep frost settles..." -> "event.deep_lucky_block.pb_ice_age.title";
            case "Space folds around you..." -> "event.deep_lucky_block.pb_tele_random.title";
            case "Reality yanks you away..." -> "event.deep_lucky_block.pb_crazy_port.title";
            case "Ticking fills the air..." -> "event.deep_lucky_block.pb_danger_call.title";
            case "Footsteps gather from every direction..." -> "event.deep_lucky_block.mob_invasion.title";
            case "A blue circle begins to hiss..." -> "event.deep_lucky_block.charged_creeper_execution.title";
            case "Glass stars ignite across the horizon..." -> "event.deep_lucky_block.end_crystal_minefield.title";
            case "A distant line begins to fall..." -> "event.deep_lucky_block.tnt_sky_serpent.title";
            case "The ground splits with a foreign heat..." -> "event.deep_lucky_block.nether_breach.title";
            case "The world drops away below you..." -> "event.deep_lucky_block.last_platform.title";
            case "Time begins to pour above you..." -> "event.deep_lucky_block.sand_hourglass.title";
            case "Burning shapes gather in the clouds..." -> "event.deep_lucky_block.magma_cube_monsoon.title";
            case "The landscape begins to grow threads..." -> "event.deep_lucky_block.cobweb_brood.title";
            case "Two rings of bones take aim..." -> "event.deep_lucky_block.skeleton_firing_squad.title";
            case "Something in your bags begins to spoil..." -> "event.deep_lucky_block.food_rot.title";
            case "Too many locks click open at once..." -> "event.deep_lucky_block.chest_mimic_field.title";
            case "Your equipment casts more than one shadow..." -> "event.deep_lucky_block.shadow_disciples.title";
            default -> null;
        };
        return key == null ? Component.literal(title) : Component.translatable(key);
    }

    private static Component localizeEventMessage(String message) {
        if (message == null) return Component.empty();
        String key = switch (message) {
            case "⚠ VOID POCKET!" -> "event.deep_lucky_block.void_pocket.action";
            case "The ground collapses... lava at the bottom." -> "event.deep_lucky_block.void_pocket.narration";
            case "Ancient whispers flood your mind..." -> "event.deep_lucky_block.eldritch_whispers.narration";
            case "Your arms feel like lead..." -> "event.deep_lucky_block.mining_fatigue.narration";
            case "🐝 Furious swarm!" -> "event.deep_lucky_block.angry_bees.action";
            case "BZZZZZ! Angry bees!" -> "event.deep_lucky_block.angry_bees.narration";
            case "⚔ Pillager ambush!" -> "event.deep_lucky_block.pillager_ambush.action";
            case "Pillagers surge from nowhere!" -> "event.deep_lucky_block.pillager_ambush.narration";
            case "YOU'VE BEEN SLIMED!" -> "event.deep_lucky_block.youve_been_slimed.action";
            case "Slime everywhere... yuck." -> "event.deep_lucky_block.youve_been_slimed.narration";
            case "Your armor binds with a curse..." -> "event.deep_lucky_block.cursed_armor.narration";
            case "Inventory shaken like a cocktail..." -> "event.deep_lucky_block.inventory_shuffle.narration";
            case "⚠ No dry solid ground found for Sky Loot!" -> "event.deep_lucky_block.sky_loot.no_ground";
            case "✦ Something brilliant is falling nearby... ✦" -> "event.deep_lucky_block.sky_loot.action";
            case "A streak of gold cuts across the sky..." -> "event.deep_lucky_block.sky_loot.narration";
            case "🐛 Parasites!" -> "event.deep_lucky_block.silverfish.action";
            case "The stone cracks and swarms..." -> "event.deep_lucky_block.silverfish.narration";
            case "A sickly green cloud escapes..." -> "event.deep_lucky_block.poison_cloud.narration";
            case "Gravity reverses..." -> "event.deep_lucky_block.gravity_well.narration";
            case "Everything goes dark..." -> "event.deep_lucky_block.blind_trap.narration";
            case "A dark aura eats you..." -> "event.deep_lucky_block.wither_curse.narration";
            case "Take good care of them for me, please." -> "event.deep_lucky_block.cat_invasion.care_message";
            case "✦ ULTIMATE SANCTUARY REWARD appeared! Grab it fast! ✦" -> "event.deep_lucky_block.poisoned_gift.action";
            case "Fortune has left something behind..." -> "event.deep_lucky_block.poisoned_gift.narration";
            case "👑 The Slime King has awakened! 👑" -> "event.deep_lucky_block.slime_king.action";
            case "The ground trembles... the Slime King rises from the depths!" -> "event.deep_lucky_block.slime_king.narration";
            case "🥷 A Ninja Assassin stalks you..." -> "event.deep_lucky_block.ninja.action";
            case "Eyes watch from the shadows..." -> "event.deep_lucky_block.ninja.narration";
            case "☠ Poison Cloud ☠" -> "event.deep_lucky_block.acid_rain.action";
            case "☠ The land twists into chaos!" -> "event.deep_lucky_block.chaos_terraform.action";
            case "Reality warps... the ground betrays you." -> "event.deep_lucky_block.chaos_terraform.narration";
            case "⚠ The Nether refused you..." -> "event.deep_lucky_block.nether_warp.refused";
            case "⚠ No safe Nether spot!" -> "event.deep_lucky_block.nether_warp.no_safe_spot";
            case "🔥 Forced into the Nether!" -> "event.deep_lucky_block.nether_warp.action";
            case "A portal engulfs you... welcome to Hell." -> "event.deep_lucky_block.nether_warp.narration";
            case "☠ Mining blocked 30s! Poison cloud!" -> "event.deep_lucky_block.mining_rollback.action";
            case "Green mist... your tools feel useless for 30 seconds." -> "event.deep_lucky_block.mining_rollback.narration";
            case "💀 Cursed Family Tomb!" -> "event.deep_lucky_block.cursed_tomb.action";
            case "A cursed family tomb appears..." -> "event.deep_lucky_block.cursed_tomb.narration";
            case "⚔ Arena of the Forgotten!" -> "event.deep_lucky_block.arena.action";
            case "An arena materializes... equipped zombies await." -> "event.deep_lucky_block.arena.narration";
            case "⚠ Cavern of Despair!" -> "event.deep_lucky_block.cavern.action";
            case "The ground opens... cursed cavern full of lava and monsters." -> "event.deep_lucky_block.cavern.narration";
            case "👤 Dark Double!" -> "event.deep_lucky_block.shadow_mirror.action";
            case "A Dark Double materializes..." -> "event.deep_lucky_block.shadow_mirror.narration";
            case "🎣 Bob appeared!" -> "event.deep_lucky_block.bob.action";
            case "Bob 'The Eternal Fisher' appears... and he looks ANGRY." -> "event.deep_lucky_block.bob.narration";
            case "⚠ Pluie d'Enclumes ! Regarde en l'air..." -> "event.deep_lucky_block.anvil_rain.action";
            case "✦ Diamants !!! ✦" -> "event.deep_lucky_block.fake_diamond.action";
            case "Don't move..." -> "event.deep_lucky_block.creeper_party.action";
            case "The heat keeps rising..." -> "event.deep_lucky_block.lava_cage.action";
            case "⚠ LE WARDEN S'ÉVEILLE... ⚠" -> "event.deep_lucky_block.warden.action";
            case "It heard you..." -> "event.deep_lucky_block.warden.heard";
            case "⚠ Pluie de Flèches Fantômes !" -> "event.deep_lucky_block.spectral_arrows.action";
            case "The daylight is being swallowed..." -> "event.deep_lucky_block.solar_eclipse.action";
            case "✦ Le jour revient après l'Éclipse !" -> "event.deep_lucky_block.solar_eclipse.end";
            case "The bell tolls again..." -> "event.deep_lucky_block.cursed_bell.action";
            case "Something enormous is directly above you..." -> "event.deep_lucky_block.dragon_illusion.action";
            case "Don't stop moving..." -> "event.deep_lucky_block.molten_footsteps.action";
            case "☠ Stacked mob towers!" -> "event.deep_lucky_block.pb_mob_towers.action";
            case "⚠ Heavy mass rain!" -> "event.deep_lucky_block.pb_megaton.action";
            case "⚠ Block grave!" -> "event.deep_lucky_block.pb_block_grave.action";
            case "⚠ Block shower!" -> "event.deep_lucky_block.pb_block_shower.action";
            case "⚠ The ground shifts!" -> "event.deep_lucky_block.pb_transform.action";
            case "⚠ Blocks replaced!" -> "event.deep_lucky_block.pb_replace.action";
            case "⚠ Drought: the land is dying!" -> "event.deep_lucky_block.pb_dryness.action";
            case "⚠ Earthquake!" -> "event.deep_lucky_block.pb_height_noise.action";
            case "⚠ Mad geometry!" -> "event.deep_lucky_block.pb_mad_geometry.action";
            case "⚠ Demented geometry!" -> "event.deep_lucky_block.pb_madder_geometry.action";
            case " Creative towers!" -> "event.deep_lucky_block.pb_classic.action";
            case "The heat is unbearable!" -> "event.deep_lucky_block.pb_sand.action";
            case "The land turns into the End..." -> "event.deep_lucky_block.pb_end.action";
            case "Welcome to Hell..." -> "event.deep_lucky_block.pb_hell.action";
            case "Life has vanished..." -> "event.deep_lucky_block.pb_lifeless.action";
            case "⚠ Arrow rain + poison!" -> "event.deep_lucky_block.pb_trapped_tribe.action";
            case "⚠ Every nearby being is weakened!" -> "event.deep_lucky_block.pb_buffed_down.action";
            case "⚠ Massive crush!" -> "event.deep_lucky_block.pb_crush.action";
            case "⚠ Holes to the void!" -> "event.deep_lucky_block.pb_void.action";
            case " Block snake!" -> "event.deep_lucky_block.pb_world_snake.action";
            case " Two block snakes!" -> "event.deep_lucky_block.pb_double_snake.action";
            case "⚠ Giant tunnel dug!" -> "event.deep_lucky_block.pb_tunnel.action";
            case "⚠ Painted targets + zombies!" -> "event.deep_lucky_block.pb_target.action";
            case "⚠ Ice age! You're freezing!" -> "event.deep_lucky_block.pb_ice_age.action";
            case "⚠ Random teleportation!" -> "event.deep_lucky_block.pb_tele_random.action";
            case "⚠ Massive teleportation!" -> "event.deep_lucky_block.pb_crazy_port.action";
            case "Something clicks beneath your feet..." -> "event.deep_lucky_block.sky_loot.pickup";
            case "That sound came from below..." -> "event.deep_lucky_block.sky_loot.pickup_narration";
            default -> null;
        };
        return key == null ? Component.literal(message) : Component.translatable(key);
    }

    private static void actionBarTranslated(ServerLevel level, double x, double y, double z,
                                            String colorCode, String translationKey, Object... args) {
        Component text = Component.literal("§" + colorCode + "§l")
                .append(Component.translatable(translationKey, args));
        AABB box = new AABB(x - 32, y - 16, z - 32, x + 32, y + 16, z + 32);
        for (Player p : level.getEntitiesOfClass(Player.class, box))
            if (p instanceof ServerPlayer sp)
                sp.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    private static void narrateTranslated(ServerLevel level, double x, double y, double z,
                                           RandomSource random, String translationKey, Object... args) {
        String narrator = NARRATORS[random.nextInt(NARRATORS.length)];
        String color = switch (narrator) {
            case "DeepWalls"  -> "§5";
            case "Dee-Walls"  -> "§3";
            case "DeepWallas" -> "§6";
            default -> "§8";
        };
        Component line = Component.literal(color + "[" + narrator + "] §7")
                .append(Component.translatable(translationKey, args));
        level.getEntitiesOfClass(Player.class,
                new AABB(x - 24, y - 8, z - 24, x + 24, y + 8, z + 24))
             .forEach(p -> p.displayClientMessage(line, true));
    }

    private static void actionBar(ServerLevel level, double x, double y, double z,
                                   String colorCode, String message) {
        Component text = Component.literal("§" + colorCode + "§l")
                .append(localizeEventMessage(message));
        AABB box = new AABB(x - 32, y - 16, z - 32, x + 32, y + 16, z + 32);
        for (Player p : level.getEntitiesOfClass(Player.class, box))
            if (p instanceof ServerPlayer sp)
                sp.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    private static void ensureAirAtPlayer(ServerLevel level, double x, double y, double z) {
        int px = (int) Math.floor(x), py = (int) Math.floor(y), pz = (int) Math.floor(z);
        for (int dx = -1; dx <= 1; dx++) for (int dz2 = -1; dz2 <= 1; dz2++) {
            level.setBlock(new BlockPos(px + dx, py, pz + dz2), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(px + dx, py + 1, pz + dz2), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void playBadSound(ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0f, 0.6f);
    }

    private static void narrate(ServerLevel level, double x, double y, double z,
                                  RandomSource random, String message) {
        String narrator = NARRATORS[random.nextInt(NARRATORS.length)];
        String color = switch (narrator) {
            case "DeepWalls"  -> "§5";
            case "Dee-Walls"  -> "§3";
            case "DeepWallas" -> "§6";
            default -> "§8";
        };
        Component line = Component.literal(color + "[" + narrator + "] §7")
                .append(localizeEventMessage(message));
        level.getEntitiesOfClass(Player.class,
                new AABB(x - 24, y - 8, z - 24, x + 24, y + 8, z + 24))
             .forEach(p -> p.displayClientMessage(line, true));
    }

    private static void placeLuckyChest(ServerLevel level, BlockPos pos, int rankId) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            CompoundTag data = chest.getPersistentData();
            data.putBoolean("slb_auto_chest", true);
            data.putBoolean("slb_filled", false);
            data.putInt("slb_chest_rank", rankId);
            chest.setChanged();
        }
    }

    private static int rollChestRank(RandomSource random) {
        int r = random.nextInt(100);
        if (r < 55) return 1;
        if (r < 85) return 2;
        return 3;
    }

    private static void placeWallTorch(ServerLevel level, BlockPos pos, Direction facing) {
        try {
            level.setBlock(pos,
                    Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing), 3);
        } catch (Exception e) {
            level.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
        }
    }

    private static void placeChestsInArea(ServerLevel level, BlockPos origin,
                                           int minX, int maxX, int minZ, int maxZ,
                                           int floorY, List<BlockPos> reserved, RandomSource random) {
        int count = 1 + random.nextInt(5);
        int placed = 0, attempts = 0;
        while (placed < count && attempts < 40) {
            int cx = minX + random.nextInt(maxX - minX + 1);
            int cz = minZ + random.nextInt(maxZ - minZ + 1);
            BlockPos cp = origin.offset(cx, floorY, cz);
            boolean isReserved = false;
            for (BlockPos rp : reserved) if (cp.equals(rp)) { isReserved = true; break; }
            if (!isReserved && level.getBlockState(cp).isAir()) {
                placeLuckyChest(level, cp, rollChestRank(random));
                placed++;
            }
            attempts++;
        }
    }

    // ==================== ALL EVENTS ====================

    private static void event_TheFrenchTax(ServerLevel level, double x, double y, double z,
                                            RandomSource random, TestProcedure.BranchResult r) {
        r.title = "You hear coins jingling..."; r.titleColorCode = "c";
        r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> {
            playBadSound(level, x, y, z);
            ItemStack stolen = ItemStack.EMPTY;
            int stolenAmount = 0;
            for (Player p : level.getEntitiesOfClass(Player.class,
                    new AABB(x - 5, y - 4, z - 5, x + 5, y + 4, z + 5))) {
                if (!(p instanceof ServerPlayer sp) || !sp.isAlive()) continue;

                // "Comme la vraie taxe" : on tire UN SEUL item au hasard dans l'inventaire
                // COMPLET du joueur (sac + hotbar + armure portee + main secondaire).
                // - Si c'est une piece d'equipement (armure/offhand), non empilable : elle est saisie entierement.
                // - Si c'est un item empilable (ex: 32 pierre), seul un pourcentage (~20%, au moins 1,
                //   jamais la totalite) est preleve, "en plusieurs exemplaires".
                List<Integer> mainSlots = new ArrayList<>();
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    if (!sp.getInventory().getItem(i).isEmpty()) mainSlots.add(i);
                }
                EquipmentSlot[] wornSlots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND };
                List<EquipmentSlot> wornCandidates = new ArrayList<>();
                for (EquipmentSlot es : wornSlots) {
                    if (!sp.getItemBySlot(es).isEmpty()) wornCandidates.add(es);
                }

                int totalCandidates = mainSlots.size() + wornCandidates.size();
                if (totalCandidates == 0) continue;

                int pick = random.nextInt(totalCandidates);
                if (pick < mainSlots.size()) {
                    int slot = mainSlots.get(pick);
                    ItemStack stack = sp.getInventory().getItem(slot);
                    if (stack.getCount() <= 1) {
                        // Objet non empilable (ou dernier exemplaire) : saisie totale.
                        stolen = stack.copy();
                        stolenAmount = stolen.getCount();
                        sp.getInventory().setItem(slot, ItemStack.EMPTY);
                    } else {
                        int toRemove = Math.max(1, Math.min(stack.getCount() - 1,
                                Math.round(stack.getCount() * 0.20f)));
                        stolen = stack.copyWithCount(toRemove);
                        stolenAmount = toRemove;
                        stack.shrink(toRemove);
                        sp.getInventory().setItem(slot, stack);
                    }
                } else {
                    EquipmentSlot es = wornCandidates.get(pick - mainSlots.size());
                    ItemStack stack = sp.getItemBySlot(es);
                    // Une piece portee (armure/main secondaire) est toujours saisie entierement.
                    stolen = stack.copy();
                    stolenAmount = stolen.getCount();
                    sp.setItemSlot(es, ItemStack.EMPTY);
                }
                sp.inventoryMenu.broadcastChanges();
                if (!stolen.isEmpty()) break;
            }
            BlockPos signPos = new BlockPos((int) Math.floor(x), (int) Math.floor(y) + 1, (int) Math.floor(z));
            if (level.getBlockState(signPos).isAir()) {
                level.setBlock(signPos, Blocks.OAK_SIGN.defaultBlockState(), 3);
                if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
                    sign.setText(sign.getFrontText()
                            .setMessage(0, Component.literal("§c§l").append(Component.translatable("event.deep_lucky_block.sign.taxed")))
                            .setMessage(1, Component.literal("§c§l").append(Component.translatable("event.deep_lucky_block.sign.by")))
                            .setMessage(2, Component.literal("§c§l").append(Component.translatable("event.deep_lucky_block.sign.state")))
                            .setMessage(3, Component.literal("§4§l💀")), true);
                    sign.setChanged();
                }
            }
            Component stolenName = stolen.isEmpty()
                    ? Component.translatable("event.deep_lucky_block.the_french_tax.nothing")
                    : stolen.getDisplayName();
            actionBarTranslated(level, x, y, z, "c",
                    "event.deep_lucky_block.the_french_tax.taken", stolenName);
            narrateTranslated(level, x, y, z, random,
                    "event.deep_lucky_block.the_french_tax.narration", stolenName);
        };
    }

    private static void event_CatInvasion(ServerLevel level, double x, double y, double z,
                                           RandomSource random, TestProcedure.BranchResult r) {
        // Nombre TOTAL de compagnons : uniforme de 1 a 5, Dewey compris.
        final int totalCompanions = CAT_MIN_COUNT
                + random.nextInt(CAT_MAX_COUNT - CAT_MIN_COUNT + 1);

        // Dewey n'est plus une branche "Dewey seul" : il fait partie du tirage
        // et prend exactement une place avec 10 % de chance par evenement.
        final boolean includeDewey = random.nextInt(100) < DEWEY_POOL_CHANCE_PERCENT;
        final int regularCompanions = totalCompanions - (includeDewey ? 1 : 0);

        r.title = includeDewey
                ? "Two golden eyes watch from the dark..."
                : "Soft paws pad closer...";
        r.titleColorCode = includeDewey ? "5" : "d";
        r.rank = TestProcedure.ItemRank.COMMON;
        r.particles.add(includeDewey ? "END_ROD" : "HAPPY_VILLAGER");

        // Pool normal sans doublon : 11 chats standards + Nevada + Ymir.
        // Dewey est gere separement pour garantir exactement 10 % d'inclusion.
        final List<Integer> regularPool = new ArrayList<>();
        for (int index = 0; index < CAT_REGULAR_POOL_SIZE; index++) {
            regularPool.add(index);
        }
        Collections.shuffle(regularPool, new java.util.Random(random.nextLong()));

        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.CAT_AMBIENT,
                    SoundSource.NEUTRAL, 1.5f, includeDewey ? 0.7f : 1.0f);

            Player nearestOwner = level.getNearestPlayer(x, y, z, 200.0, false);
            ServerPlayer catOwner = nearestOwner instanceof ServerPlayer sp ? sp : null;

            // Dewey occupe une place parmi les 1-5, jamais une place bonus.
            if (includeDewey) {
                spawnDewey(level, x, y, z, random);
            }

            // Complete le groupe avec des identites uniques choisies au hasard.
            for (int pick = 0; pick < regularCompanions; pick++) {
                int poolIndex = regularPool.get(pick);
                if (poolIndex < CAT_PETS.length) {
                    spawnRegularNamedCat(level, x, y, z, random, catOwner,
                            CAT_PETS[poolIndex]);
                } else if (poolIndex == CAT_POOL_NEVADA) {
                    spawnNevada(level, x, y, z, random, catOwner);
                } else if (poolIndex == CAT_POOL_YMIR) {
                    spawnYmir(level, x, y, z, random, catOwner);
                }
            }

            narrate(level, x, y, z, random,
                    "Take good care of them for me, please.");
        };
    }

    // Event vert independant no 86 : Dewey apparait seul, avec son soin,
    // son effet visible, son suivi et sa teleportation habituels.
    private static void event_DeweyOnly(ServerLevel level, double x, double y, double z,
                                        RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Two golden eyes watch from the dark...";
        r.titleColorCode = "a";
        r.rank = TestProcedure.ItemRank.COMMON;
        r.particles.add("END_ROD");
        r.particles.add("HAPPY_VILLAGER");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.CAT_AMBIENT,
                    SoundSource.NEUTRAL, 1.5f, 0.7f);
            spawnDewey(level, x, y, z, random);
        };
    }

    // Event GREEN no 87 : chaque slot vide de l'inventaire principal et de la
    // hotbar recoit une pile aleatoire de 1 a 64 blocs de gravier. Les slots
    // d'armure et la seconde main ne sont jamais modifies.
    private static void event_GravelInventory(ServerLevel level, double x, double y, double z,
                                              RandomSource random, TestProcedure.BranchResult r) {
        r.title = "DU GRAVIER !!";
        r.titleColorCode = "a";
        r.rank = TestProcedure.ItemRank.COMMON;
        r.particles.add("HAPPY_VILLAGER");

        final long gravelSeed = random.nextLong();
        r.deferredAction = () -> {
            Player nearest = level.getNearestPlayer(x, y, z, 200.0, false);
            if (!(nearest instanceof ServerPlayer player)) return;

            RandomSource gravelRandom = RandomSource.create(gravelSeed);
            int mainInventorySlots = Math.min(36,
                    player.getInventory().getContainerSize());
            for (int slot = 0; slot < mainInventorySlots; slot++) {
                if (player.getInventory().getItem(slot).isEmpty()) {
                    player.getInventory().setItem(slot,
                            new ItemStack(Items.GRAVEL,
                                    1 + gravelRandom.nextInt(64)));
                }
            }
            player.inventoryMenu.broadcastChanges();
        };
    }

    private static void event_PoisonedGift(ServerLevel level, double x, double y, double z,
                                            RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Congratulations! Your reward is waiting...";
        r.titleColorCode = "a";
        r.rank = TestProcedure.ItemRank.COMMON;
        r.particles.add("FIREWORK");
        r.deferredAction = () -> {
            // Coordonnees exactes de l'ancien Lucky Block, pas les coordonnees du joueur.
            BlockPos chestPos = BlockPos.containing(x, y, z);

            // Utilise le vrai systeme de coffre du mod : rank 3 = ULTIMATE.
            placeLuckyChest(level, chestPos, 3);
            if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) return;

            CompoundTag data = chest.getPersistentData();
            data.putBoolean("slb_poisoned_gift", true);
            data.putBoolean("slb_poison_triggered", false);
            data.putBoolean("slb_auto_chest", true);
            data.putBoolean("slb_filled", false);
            data.putInt("slb_chest_rank", 3);
            // [1.21.1] setCustomName supprimé sur BlockEntity : nom conservé uniquement dans les données
            // chest.setCustomName(Component.literal("§6§l").append(Component.translatable("event.deep_lucky_block.name.ultimate_reward")));
            chest.setChanged();

            level.sendParticles(ParticleTypes.END_ROD,
                    chestPos.getX() + 0.5, chestPos.getY() + 1.2, chestPos.getZ() + 0.5,
                    30, 0.5, 0.5, 0.5, 0.05);
            level.sendParticles(ParticleTypes.FIREWORK,
                    chestPos.getX() + 0.5, chestPos.getY() + 1.5, chestPos.getZ() + 0.5,
                    20, 0.5, 0.7, 0.5, 0.08);
            level.playSound(null, chestPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1.1f, 1.1f);
            level.playSound(null, chestPos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 1.0f, 1.5f);
            actionBar(level, x, y, z, "6",
                    "✦ ULTIMATE SANCTUARY REWARD appeared! Grab it fast! ✦");
            narrate(level, x, y, z, random, "Fortune has left something behind...");
        };
    }

    private static void event_WolfPack(ServerLevel level, double x, double y, double z,
                                        RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Howls close in around you..."; r.titleColorCode = "4";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("SMOKE"); r.particles.add("ASH");
        final int count = 8 + random.nextInt(5);
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 1.5f, 0.6f);
            Player nearest = level.getNearestPlayer(x, y, z, 16, false);
            for (int i = 0; i < count; i++) {
                Wolf wolf = new Wolf(EntityType.WOLF, level);
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 3.0 + random.nextDouble() * 4.0;
                wolf.moveTo(x + Math.cos(angle) * dist, y + 1, z + Math.sin(angle) * dist,
                        random.nextFloat() * 360, 0);
                wolf.setPersistenceRequired();
                wolf.setCustomName((count == 3 ? Component.translatable("event.deep_lucky_block.name.team_claquette") : Component.translatable("event.deep_lucky_block.name.enraged_wolf")));
                wolf.setCustomNameVisible(true);
                if (nearest != null) {
                    wolf.setPersistentAngerTarget(nearest.getUUID());
                    wolf.setRemainingPersistentAngerTime(6000);
                    wolf.setTarget((LivingEntity) nearest);
                } // [1.21.1] setCollarColor privé -> nom du collier non modifiable (comportement best-effort)
                level.addFreshEntity(wolf);
            }
            actionBarTranslated(level, x, y, z, "4", "event.deep_lucky_block.wolf_pack.action", count);
            narrateTranslated(level, x, y, z, random, "event.deep_lucky_block.wolf_pack.narration", count);
        };
    }

    // ==================== SLIME KING BOSS (custom Blockbench entity) ====================

    private static void event_SlimeKingBoss(ServerLevel level, double x, double y, double z,
                                             RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Something huge stirs below...";
        r.titleColorCode = "a";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("HAPPY_VILLAGER");
        r.particles.add("ITEM_SLIME");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 2.0f, 0.4f);
            level.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.0f, 0.3f);

            SlimeKingEntity boss = new SlimeKingEntity(
                    DeepLuckyBlockModEntities.SLIME_KING.get(), level);
            boss.moveTo(x + 0.5, y + 1, z + 0.5, random.nextFloat() * 360, 0);
            level.addFreshEntity(boss);

            replaceGroundWithFade(level, x, y, z, random, 6, Blocks.SLIME_BLOCK.defaultBlockState());

            level.sendParticles(ParticleTypes.ITEM_SLIME, x + 0.5, y + 2, z + 0.5,
                    150, 4, 3, 4, 0.3);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5, y + 4, z + 0.5,
                    60, 3, 2, 3, 0.1);

            actionBar(level, x, y, z, "a", "👑 The Slime King has awakened! 👑");
            narrate(level, x, y, z, random,
                    "The ground trembles... the Slime King rises from the depths!");
        };
    }

    private static void event_NinjaAssassin(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Unseen eyes are watching..."; r.titleColorCode = "0"; r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("SQUID_INK"); r.particles.add("SMOKE");
        r.deferredAction = () -> { level.playSound(null, x, y, z, SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 0.8f, 1.5f);
            Zombie ninja = new Zombie(EntityType.ZOMBIE, level);
            double angle = random.nextDouble() * Math.PI * 2; double dist = 5.0 + random.nextDouble() * 3.0;
            ninja.moveTo(x + Math.cos(angle) * dist, y + 1, z + Math.sin(angle) * dist, random.nextFloat() * 360, 0);
            ninja.setPersistenceRequired(); ninja.setCustomName(Component.literal("§0§l🥷 ").append(Component.translatable("event.deep_lucky_block.name.ninja_assassin"))); ninja.setCustomNameVisible(false);
            ninja.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 1, false, false, false));
            ninja.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false, false));
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            GenerateLuckyItemProcedure.addEnchantment(sword, "minecraft:sharpness", 3);
            GenerateLuckyItemProcedure.addEnchantment(sword, "minecraft:fire_aspect", 2);
            sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§0§l🥷 ").append(Component.translatable("event.deep_lucky_block.name.silent_blade")));
            ninja.setItemSlot(EquipmentSlot.MAINHAND, sword); ninja.setDropChance(EquipmentSlot.MAINHAND, 0.1f);
            try { ninja.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0); ninja.setHealth(40.0f);
                ninja.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5.0);
                ninja.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35); } catch (Exception ignored) {}
            level.addFreshEntity(ninja);
            actionBar(level, x, y, z, "0", "🥷 A Ninja Assassin stalks you...");
            narrate(level, x, y, z, random, "Eyes watch from the shadows...");
        };
    }

    private static void event_AcidRain(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The air turns acrid..."; r.titleColorCode = "2"; r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("SMOKE");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.8f);

            for (int i = 0; i < 24; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = random.nextDouble() * 14.0;
                double px = x + Math.cos(angle) * dist;
                double pz = z + Math.sin(angle) * dist;
                double py = y + 2 + random.nextDouble() * 5;

                ThrownPotion potion =
                        new ThrownPotion(EntityType.POTION, level);
                ItemStack poisonPotion = new ItemStack(Items.SPLASH_POTION);
                poisonPotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(Potions.STRONG_POISON));
                potion.setItem(poisonPotion);
                potion.setPos(px, py, pz);
                potion.setDeltaMovement(0, -0.5, 0);
                level.addFreshEntity(potion);
            }

            actionBar(level, x, y, z, "2", "☠ Poison Cloud ☠");
        };
    }

    private static void event_ChaosTerraform(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The land begins to warp..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("DRAGON_BREATH"); r.particles.add("SMOKE");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int rad = 8; int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
            BlockState[] chaoticGround = { Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(),
                Blocks.SOUL_SAND.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState() };
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz2 = -rad; dz2 <= rad; dz2++) {
                    double distSq = dx * dx + dz2 * dz2;
                    if (distSq > rad * rad) continue;
                    double chance = 1.0 - (Math.sqrt(distSq) / (double) rad);
                    if (random.nextDouble() > chance) continue;

                    for (int dy = 0; dy >= -2; dy--) {
                        BlockPos p = new BlockPos(ix + dx, iy + dy, iz + dz2);
                        BlockState state = level.getBlockState(p);
                        if (!state.isAir() && state.blocksMotion() && !state.is(Blocks.BEDROCK)) {
                            level.setBlock(p, chaoticGround[random.nextInt(chaoticGround.length)], 2);
                            break;
                        }
                    }
                }
            }
            ensureAirAtPlayer(level, x, y, z);
            actionBar(level, x, y, z, "5", "☠ The land twists into chaos!");
            narrate(level, x, y, z, random, "Reality warps... the ground betrays you.");
        };
    }

    private static void event_NetherWarp(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A portal drags at you...";
        r.titleColorCode = "4";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("PORTAL");
        r.deferredAction = () -> {
            Player target = level.getNearestPlayer(x, y, z, 32.0, false);
            if (!(target instanceof ServerPlayer player) || !player.isAlive()) return;

            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            if (nether == null) {
                buildNetherrackFallbackCage(player.serverLevel(), player);
                return;
            }

            level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER,
                    SoundSource.PLAYERS, 1.5f, 0.65f);
            beginBoundedNetherSearch(level, nether, player, random);
        };
    }

    // Recherche etalee sur 10 secondes : jamais de boucle de generation de chunks
    // massive dans un seul tick. 20 essais maximum, un essai toutes les 0,5 s.
    private static void beginBoundedNetherSearch(ServerLevel origin, ServerLevel nether,
                                                  ServerPlayer player, RandomSource random) {
        int deadline = TestProcedure.currentTick(origin) + 200;
        double scaledX = player.getX() / 8.0;
        double scaledZ = player.getZ() / 8.0;
        continueNetherSearch(origin, nether, player.getUUID(), scaledX, scaledZ,
                random, 0, deadline);
    }

    private static void continueNetherSearch(ServerLevel origin, ServerLevel nether,
                                             UUID playerId, double scaledX, double scaledZ,
                                             RandomSource random, int attempt, int deadline) {
        ServerPlayer player = origin.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) return;

        int now = TestProcedure.currentTick(origin);
        if (now >= deadline || attempt >= 20) {
            buildNetherrackFallbackCage(player.serverLevel(), player);
            return;
        }

        // Technique 1 (essais 0-9) : autour des coordonnees Nether equivalentes.
        // Technique 2 (essais 10-19) : colonnes aleatoires plus lointaines.
        boolean localTechnique = attempt < 10;
        int sx = localTechnique
                ? Mth.floor(scaledX + (random.nextDouble() - 0.5) * 192.0)
                : random.nextInt(2001) - 1000;
        int sz = localTechnique
                ? Mth.floor(scaledZ + (random.nextDouble() - 0.5) * 192.0)
                : random.nextInt(2001) - 1000;

        BlockPos safe = findSafeNetherSpawnAttempt(nether, sx, sz, random, localTechnique);
        if (safe != null) {
            prepareNetherLanding(nether, safe);
            player.teleportTo(nether,
                    safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            player.playSound(SoundEvents.PORTAL_TRAVEL, 1.0f, 1.0f);
            return;
        }

        player.serverLevel().sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.5, 0.8, 0.5, 0.2);
        TestProcedure.schedule(origin, now + 10, () ->
                continueNetherSearch(origin, nether, playerId, scaledX, scaledZ,
                        random, attempt + 1, deadline));
    }

    private static BlockPos findSafeNetherSpawnAttempt(ServerLevel nether, int sx, int sz,
                                                        RandomSource random, boolean localTechnique) {
        int minY = Math.max(32, nether.getMinBuildHeight() + 4);
        int maxY = Math.min(120, nether.getMaxBuildHeight() - 4);
        int startY = localTechnique ? maxY : 40 + random.nextInt(Math.max(1, maxY - 39));

        // Technique locale : scan vertical complet sous le toit.
        // Technique aleatoire : scan d'abord autour d'un Y aleatoire, puis vers le bas.
        int scanBottom = localTechnique ? minY : Math.max(minY, startY - 45);
        for (int sy = startY; sy >= scanBottom; sy--) {
            BlockPos feet = new BlockPos(sx, sy, sz);
            BlockState below = nether.getBlockState(feet.below());
            BlockState atFeet = nether.getBlockState(feet);
            BlockState atHead = nether.getBlockState(feet.above());
            if (!below.blocksMotion() || !below.getFluidState().isEmpty()) continue;
            if (!atFeet.isAir() || !atHead.isAir()) continue;

            boolean safe = true;
            for (int dx = -3; dx <= 3 && safe; dx++) {
                for (int dz = -3; dz <= 3 && safe; dz++) {
                    for (int dy = -1; dy <= 3; dy++) {
                        BlockState state = nether.getBlockState(feet.offset(dx, dy, dz));
                        if (state.is(Blocks.LAVA)
                                || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                            safe = false;
                            break;
                        }
                    }
                }
            }
            if (safe) return feet;
        }
        return null;
    }

    private static void prepareNetherLanding(ServerLevel nether, BlockPos safePos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos pos = safePos.offset(dx, dy, dz);
                    BlockState state = nether.getBlockState(pos);
                    if (state.is(Blocks.LAVA)
                            || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                            || (dy > 0 && !state.isAir())) {
                        nether.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                nether.setBlock(safePos.offset(dx, -1, dz),
                        Blocks.NETHERRACK.defaultBlockState(), 3);
            }
        }
    }

    private static void buildNetherrackFallbackCage(ServerLevel level, ServerPlayer player) {
        int bx = Mth.floor(player.getX());
        int by = Mth.floor(player.getY());
        int bz = Mth.floor(player.getZ());
        BlockPos center = new BlockPos(bx, by + 1, bz);

        // Interieur 3x3x3 vide, coquille complete 5x5x5 en netherrack.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dy) == 2 || Math.abs(dz) == 2;
                    if (shell) {
                        if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                            level.setBlock(pos, Blocks.NETHERRACK.defaultBlockState(), 3);
                        }
                    } else if (!(dx == 0 && dy == -2 && dz == 0)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.GHAST_SCREAM,
                SoundSource.HOSTILE, 2.5f, 0.55f);
        level.playSound(null, player.blockPosition(), SoundEvents.GHAST_AMBIENT,
                SoundSource.HOSTILE, 1.5f, 0.7f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(),
                60, 1.5, 1.5, 1.5, 0.08);
    }

    private static void event_MiningRollback(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Your tools suddenly feel wrong..."; r.titleColorCode = "2"; r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("SMOKE"); r.particles.add("SOUL");
        r.deferredAction = () -> {
            for (int t = 0; t < 60; t += 5) level.sendParticles(ParticleTypes.SNEEZE, x + 0.5, y + 1.5, z + 0.5, 30, 3.0, 1.5, 3.0, 0.02);
            level.playSound(null, x, y, z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.8f, 1.2f);
            giveEffect(level, x, y, z, 5, MobEffects.POISON, 20 * 3, 1);
            giveEffect(level, x, y, z, 5, MobEffects.WEAKNESS, 20 * 30, 0);
            giveEffect(level, x, y, z, 5, MobEffects.DIG_SLOWDOWN, 20 * 30, 4);
            actionBar(level, x, y, z, "2", "☠ Mining blocked 30s! Poison cloud!");
            narrate(level, x, y, z, random, "Green mist... your tools feel useless for 30 seconds.");
        };
    }

    private static void event_PoisonCloud(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A bitter fog creeps in..."; r.titleColorCode = "2"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        final int durPoison = (3 + random.nextInt(28)) * 20;
        final int[] amps = {0, 0, 0, 1};
        final int amp = amps[random.nextInt(amps.length)];
        final int d2 = randomDuration(random);
        r.deferredAction = () -> { playBadSound(level, x, y, z); giveEffect(level, x, y, z, 5, MobEffects.POISON, durPoison, amp); giveEffect(level, x, y, z, 5, MobEffects.HUNGER, d2, 2);
            level.sendParticles(ParticleTypes.SNEEZE, x + 0.5, y + 1.5, z + 0.5, 40, 3, 1.5, 3, 0.01); narrate(level, x, y, z, random, "A sickly green cloud escapes..."); };
    }

    private static void event_EldritchWhispers(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Old voices fill your mind..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SQUID_INK"); r.particles.add("SOUL");
        final int d1 = randomDuration(random), d2 = randomDuration(random), d3 = randomDuration(random);
        r.deferredAction = () -> { playBadSound(level, x, y, z); giveEffect(level, x, y, z, 8, MobEffects.DARKNESS, d1, 0); giveEffect(level, x, y, z, 8, MobEffects.CONFUSION, d2, 1); giveEffect(level, x, y, z, 8, MobEffects.MOVEMENT_SLOWDOWN, d3, 1); narrate(level, x, y, z, random, "Ancient whispers flood your mind..."); };
    }

    private static void event_MiningFatigue(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Your arms turn to lead..."; r.titleColorCode = "7"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("ASH"); final int d = randomDuration(random);
        r.deferredAction = () -> { playBadSound(level, x, y, z); giveEffect(level, x, y, z, 8, MobEffects.DIG_SLOWDOWN, d, 4); narrate(level, x, y, z, random, "Your arms feel like lead..."); };
    }

    private static void event_GravityWell(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The world tugs at you..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("DRAGON_BREATH"); final int d = randomDuration(random);
        r.deferredAction = () -> { playBadSound(level, x, y, z); giveEffect(level, x, y, z, 8, MobEffects.LEVITATION, d, 3); giveEffect(level, x, y, z, 8, MobEffects.SLOW_FALLING, 20 * 2, 0); narrate(level, x, y, z, random, "Gravity reverses..."); };
    }

    private static void event_BlindTrap(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The light drains away..."; r.titleColorCode = "0"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("SQUID_INK");
        final int d1 = randomDuration(random), d2 = randomDuration(random);
        r.deferredAction = () -> { playBadSound(level, x, y, z); giveEffect(level, x, y, z, 8, MobEffects.BLINDNESS, d1, 0); giveEffect(level, x, y, z, 8, MobEffects.MOVEMENT_SLOWDOWN, d2, 2); narrate(level, x, y, z, random, "Everything goes dark..."); };
    }

    private static void event_WitherCurse(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A chill bites your bones..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SOUL"); r.particles.add("ASH");
        final int d1 = randomDuration(random), d2 = randomDuration(random);
        r.deferredAction = () -> { playBadSound(level, x, y, z); giveEffect(level, x, y, z, 8, MobEffects.WITHER, d1, 1); giveEffect(level, x, y, z, 8, MobEffects.WEAKNESS, d2, 1); narrate(level, x, y, z, random, "A dark aura eats you..."); };
    }

    private static void event_CursedArmor(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Your gear clings too tight..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SOUL");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(x - 5, y - 4, z - 5, x + 5, y + 4, z + 5))) {
                if (!(p instanceof ServerPlayer sp) || !sp.isAlive()) continue;
                List<ItemStack> eq = new ArrayList<>(); for (ItemStack a : sp.getArmorSlots()) if (!a.isEmpty()) eq.add(a);
                if (!eq.isEmpty()) { ItemStack t = eq.get(random.nextInt(eq.size()));
                    GenerateLuckyItemProcedure.addEnchantment(t, "minecraft:binding_curse", 1);
                    GenerateLuckyItemProcedure.addEnchantment(t, "minecraft:vanishing_curse", 1); } }
            narrate(level, x, y, z, random, "Your armor binds with a curse...");
        };
    }

    private static void event_InventoryShuffle(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Your bags stir on their own..."; r.titleColorCode = "7"; r.rank = TestProcedure.ItemRank.COMMON; r.particles.add("CLOUD"); final long seed = random.nextLong();
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            for (Player p : level.getEntitiesOfClass(Player.class, new AABB(x - 5, y - 4, z - 5, x + 5, y + 4, z + 5))) {
                if (!(p instanceof ServerPlayer sp) || !sp.isAlive()) continue;
                List<ItemStack> c = new ArrayList<>(36); for (int i = 0; i < 36; i++) c.add(sp.getInventory().getItem(i).copy());
                Collections.shuffle(c, new java.util.Random(seed)); for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, c.get(i));
                sp.inventoryMenu.broadcastChanges(); }
            narrate(level, x, y, z, random, "Inventory shaken like a cocktail...");
        };
    }

    private static void event_PhantomRain(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        final int amount = 7 + random.nextInt(5); r.title = "Wings circle overhead..."; r.titleColorCode = "b"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("CLOUD");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            for (int i = 0; i < amount; i++) { Phantom p = new Phantom(EntityType.PHANTOM, level); p.moveTo(x + (random.nextDouble() - 0.5) * 8, y + 14 + random.nextInt(8), z + (random.nextDouble() - 0.5) * 8); p.setPersistenceRequired(); level.addFreshEntity(p); }
            actionBarTranslated(level, x, y, z, "b", "event.deep_lucky_block.phantom_rain.action", amount);
            narrateTranslated(level, x, y, z, random, "event.deep_lucky_block.phantom_rain.narration", amount); };
    }

    private static void event_AngryBees(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        final int count = 8 + random.nextInt(6); r.title = "A buzz swells in the air..."; r.titleColorCode = "e"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> { playBadSound(level, x, y, z); Player nearest = level.getNearestPlayer(x, y, z, 16, false);
            for (int i = 0; i < count; i++) { var bee = new net.minecraft.world.entity.animal.Bee(EntityType.BEE, level);
                bee.moveTo(x + (random.nextDouble() - 0.5) * 4, y + 2 + random.nextInt(3), z + (random.nextDouble() - 0.5) * 4);
                bee.setPersistenceRequired(); bee.setRemainingPersistentAngerTime(20 * 60);
                if (nearest != null) bee.setPersistentAngerTarget(nearest.getUUID()); level.addFreshEntity(bee); }
            actionBar(level, x, y, z, "e", "🐝 Furious swarm!"); narrate(level, x, y, z, random, "BZZZZZ! Angry bees!"); };
    }

    private static void event_PillagerAmbush(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        final int count = 5 + random.nextInt(4); r.title = "Crossbows cock nearby..."; r.titleColorCode = "7"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE"); r.particles.add("ASH");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            for (int i = 0; i < count; i++) { Pillager p = new Pillager(EntityType.PILLAGER, level); p.moveTo(x + (random.nextDouble() - 0.5) * 6, y + 1, z + (random.nextDouble() - 0.5) * 6); p.setPersistenceRequired(); p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW)); level.addFreshEntity(p); }
            actionBar(level, x, y, z, "7", "⚔ Pillager ambush!"); narrate(level, x, y, z, random, "Pillagers surge from nowhere!"); };
    }

    private static void event_YouveBeenSlimed(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Something wet and green..."; r.titleColorCode = "a"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("HAPPY_VILLAGER"); final int sd = randomDuration(random);
        r.deferredAction = () -> { level.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.2f, 0.7f);
            Slime big = new Slime(EntityType.SLIME, level); big.moveTo(x + 0.5, y + 1, z + 0.5); big.setSize(4, true); big.setPersistenceRequired(); level.addFreshEntity(big);
            for (int i = 0; i < 4 + random.nextInt(4); i++) { Slime s = new Slime(EntityType.SLIME, level); s.moveTo(x + (random.nextDouble() - 0.5) * 5, y + 1, z + (random.nextDouble() - 0.5) * 5); s.setSize(1 + random.nextInt(2), true); level.addFreshEntity(s); }
            replaceGroundWithFade(level, x, y, z, random, 5, Blocks.SLIME_BLOCK.defaultBlockState());
            giveEffect(level, x, y, z, 8, MobEffects.MOVEMENT_SLOWDOWN, sd, 2);
            actionBar(level, x, y, z, "a", "YOU'VE BEEN SLIMED!"); narrate(level, x, y, z, random, "Slime everywhere... yuck."); };
    }

    private static void event_SilverfishInfestation(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The stone starts to writhe..."; r.titleColorCode = "7"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("ASH");
        r.deferredAction = () -> { playBadSound(level, x, y, z); int rad = 5; int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
            for (int dx = -rad; dx <= rad; dx++) for (int dz2 = -rad; dz2 <= rad; dz2++) for (int dy = -2; dy <= 2; dy++) { if (dx * dx + dz2 * dz2 + dy * dy > rad * rad) continue; BlockPos p = new BlockPos(ix + dx, iy + dy, iz + dz2); BlockState st = level.getBlockState(p);
                if ((st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.STONE_BRICKS) || st.is(Blocks.ANDESITE) || st.is(Blocks.DIORITE) || st.is(Blocks.GRANITE) || st.is(Blocks.DEEPSLATE)) && random.nextInt(3) != 0)
                    level.setBlock(p, Blocks.INFESTED_STONE.defaultBlockState(), 3); }
            for (int i = 0; i < 5 + random.nextInt(4); i++) { Silverfish sf = new Silverfish(EntityType.SILVERFISH, level); sf.moveTo(x + (random.nextDouble() - 0.5) * 4, y + 1, z + (random.nextDouble() - 0.5) * 4); level.addFreshEntity(sf); }
            actionBar(level, x, y, z, "7", "🐛 Parasites!"); narrate(level, x, y, z, random, "The stone cracks and swarms..."); };
    }

    private static void event_VoidPocket(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The ground feels too thin..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("SQUID_INK"); r.particles.add("SMOKE");
        r.deferredAction = () -> { playBadSound(level, x, y, z); int rad = 15; int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
            for (int dx = -rad; dx <= rad; dx++) for (int dz2 = -rad; dz2 <= rad; dz2++) { if (dx * dx + dz2 * dz2 > rad * rad) continue;
                for (int dy = 0; dy <= 10; dy++) level.setBlock(new BlockPos(ix + dx, iy + dy, iz + dz2), Blocks.AIR.defaultBlockState(), 3);
                for (int dy = 1; dy <= 30; dy++) level.setBlock(new BlockPos(ix + dx, iy - dy, iz + dz2), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(ix + dx, iy - 12, iz + dz2), random.nextInt(5) == 0 ? Blocks.COBWEB.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(ix + dx, iy - 28, iz + dz2), Blocks.LAVA.defaultBlockState(), 3); level.setBlock(new BlockPos(ix + dx, iy - 29, iz + dz2), Blocks.LAVA.defaultBlockState(), 3);
                level.setBlock(new BlockPos(ix + dx, iy - 30, iz + dz2), Blocks.OBSIDIAN.defaultBlockState(), 3); }
            actionBar(level, x, y, z, "4", "⚠ VOID POCKET!"); narrate(level, x, y, z, random, "The ground collapses... lava at the bottom."); };
    }

    private static void event_SkyLoot(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A gleam drops from above...";
        r.titleColorCode = "6";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("FIREWORK");
        r.deferredAction = () -> {
            Player nearest = level.getNearestPlayer(x, y, z, 200.0, false);
            if (!(nearest instanceof ServerPlayer target) || !target.isAlive()) return;

            double sy = Math.min(level.getMaxBuildHeight() - 1.5, target.getY() + 28.0);
            BlockPos dryGround = findDrySkyLootGround(level, target, sy, random);
            if (dryGround == null) {
                actionBar(level, target.getX(), target.getY(), target.getZ(), "c",
                        "⚠ No dry solid ground found for Sky Loot!");
                return;
            }

            double sx = dryGround.getX() + 0.5;
            double sz = dryGround.getZ() + 0.5;

            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§l§o").append(Component.translatable("event.deep_lucky_block.name.sky_blade")));
            GenerateLuckyItemProcedure.addEnchantment(sword, "minecraft:binding_curse", 1);
            GenerateLuckyItemProcedure.addEnchantment(sword, "minecraft:vanishing_curse", 1);
            CompoundTag swordTag = deepluckyblock.util.DeepNbt.getOrCreateTag(sword);
            swordTag.putBoolean(SKY_LOOT_TRAP_TAG, true);
            swordTag.putString(SKY_LOOT_ID_TAG, java.util.UUID.randomUUID().toString());
            swordTag.putInt(SKY_LOOT_GROUND_X_TAG, dryGround.getX());
            swordTag.putInt(SKY_LOOT_GROUND_Y_TAG, dryGround.getY());
            swordTag.putInt(SKY_LOOT_GROUND_Z_TAG, dryGround.getZ());

            spawnGlowingItem(level, sx, sy, sz, sword);
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.5f, 0.6f);
            actionBar(level, target.getX(), target.getY(), target.getZ(), "6",
                    "✦ Something brilliant is falling nearby... ✦");
            narrate(level, target.getX(), target.getY(), target.getZ(), random,
                    "A streak of gold cuts across the sky...");
        };
    }

    private static void event_ShadowMirrorSpawn(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Your shadow steps free..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SQUID_INK"); r.particles.add("SOUL"); r.particles.add("SMOKE");
        r.deferredAction = () -> { level.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.8f, 0.5f);
            ShadowMirrorEntity m = new ShadowMirrorEntity(DeepLuckyBlockModEntities.SHADOW_MIRROR.get(), level);
            m.moveTo(x + 0.5, y + 1, z + 0.5, random.nextFloat() * 360, 0); level.addFreshEntity(m);
            level.sendParticles(ParticleTypes.SQUID_INK, x + 0.5, y + 1.5, z + 0.5, 30, 1, 1, 1, 0.02);
            actionBar(level, x, y, z, "8", "👤 Dark Double!"); narrate(level, x, y, z, random, "A Dark Double materializes..."); };
    }

    private static void event_BobTheEternalFisher(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A fishing line snaps taut...";
        r.titleColorCode = "6";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("SOUL");
        r.particles.add("SMOKE");

        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 1.0f, 0.6f);

            Zombie bob = new Zombie(EntityType.ZOMBIE, level);
            bob.moveTo(x + 0.5, y + 1.0, z + 0.5);

            bob.setCustomName(Component.literal("§6§l").append(Component.translatable("event.deep_lucky_block.name.eternal_fisher")));
            bob.setCustomNameVisible(true);
            bob.setPersistenceRequired();

            // Buffs aleatoires de Bob : +0-50% vitesse et detection, +0-20% sur vie OU puissance.
            double speedMult = 1.0 + random.nextDouble() * 0.50;  // +0 a +50% vitesse
            double rangeMult = 1.0 + random.nextDouble() * 0.50;  // +0 a +50% range de detection
            double healthMult = 1.0;
            double attackMult = 1.0;
            if (random.nextBoolean()) {
                healthMult = 1.0 + random.nextDouble() * 0.20;   // +0 a +20% vie
            } else {
                attackMult = 1.0 + random.nextDouble() * 0.20;   // +0 a +20% puissance
            }

            double bobMaxHealth = 70.0 * healthMult;
            double bobBaseAttack = (bob.getAttribute(Attributes.ATTACK_DAMAGE) != null)
                    ? bob.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() : 3.0;

            if (bob.getAttribute(Attributes.MAX_HEALTH) != null) {
                bob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(bobMaxHealth);
            }
            bob.setHealth((float) bobMaxHealth);

            if (bob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                bob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.23 * 1.6 * speedMult);
            }
            if (bob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
                bob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0 * rangeMult);
            }
            if (bob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                bob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(bobBaseAttack * attackMult);
            }
            bob.goalSelector.addGoal(2, new MeleeAttackGoal(bob, 1.2, false));
            bob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(bob, Player.class, true));

            TestProcedure.QualityTier tier = TestProcedure.QualityTier.getQualityTier(77);

            bob.setItemSlot(EquipmentSlot.HEAD, safeSpecific(level, 77, tier, random, EquipmentSlot.HEAD));
            bob.setItemSlot(EquipmentSlot.CHEST, safeSpecific(level, 77, tier, random, EquipmentSlot.CHEST));
            bob.setItemSlot(EquipmentSlot.LEGS, safeSpecific(level, 77, tier, random, EquipmentSlot.LEGS));
            bob.setItemSlot(EquipmentSlot.FEET, safeSpecific(level, 77, tier, random, EquipmentSlot.FEET));
            bob.setItemSlot(EquipmentSlot.MAINHAND, safeSpecific(level, 77, tier, random, EquipmentSlot.MAINHAND));

            ItemStack rod = new ItemStack(Items.FISHING_ROD);
            rod.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§l").append(Component.translatable("event.deep_lucky_block.name.eternal_rod")));
            bob.setItemSlot(EquipmentSlot.OFFHAND, rod);

            bob.setDropChance(EquipmentSlot.HEAD, 0.35f);
            bob.setDropChance(EquipmentSlot.CHEST, 0.15f);
            bob.setDropChance(EquipmentSlot.LEGS, 0.25f);
            bob.setDropChance(EquipmentSlot.FEET, 0.15f);
            bob.setDropChance(EquipmentSlot.MAINHAND, 0.15f);
            bob.setDropChance(EquipmentSlot.OFFHAND, 0.00f);

            bob.getPersistentData().putBoolean("IsEternalBob", true);

            level.addFreshEntity(bob);

            actionBar(level, x, y, z, "6", "🎣 Bob appeared!");
            narrate(level, x, y, z, random, "Bob 'The Eternal Fisher' appears... and he looks ANGRY.");
        };
    }

    // ==================== END KNIGHT AMBUSH ====================

    private static void event_EndKnightAmbush(ServerLevel level, double x, double y, double z,
                                               RandomSource random, TestProcedure.BranchResult r) {
        int count = 2 + random.nextInt(4);
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("DRAGON_BREATH");
        r.particles.add("SOUL");

        r.title = "Tears open in the air...";
        r.titleColorCode = "5";
        final int spawnCount = count;
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.4f);
            level.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.8f, 0.6f);
            level.sendParticles(ParticleTypes.PORTAL, x + 0.5, y + 2, z + 0.5, 200, 4, 3, 4, 0.5);

            for (int i = 0; i < spawnCount; i++) {
                try {
                    EndKnightEntity knight = new EndKnightEntity(
                            DeepLuckyBlockModEntities.END_KNIGHT.get(), level);
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 6.0 + random.nextDouble() * 6.0;
                    knight.moveTo(x + Math.cos(angle) * dist, y + 1,
                            z + Math.sin(angle) * dist, random.nextFloat() * 360, 0);
                    knight.setPersistenceRequired();
                    level.addFreshEntity(knight);
                } catch (Exception e) {
                    Zombie fb = new Zombie(EntityType.ZOMBIE, level);
                    fb.moveTo(x + (random.nextDouble() - 0.5) * 10, y + 1,
                            z + (random.nextDouble() - 0.5) * 10);
                    fb.setCustomName(Component.literal("§5§l").append(Component.translatable("event.deep_lucky_block.name.end_knight")));
                    fb.setCustomNameVisible(true);
                    fb.setPersistenceRequired();
                    level.addFreshEntity(fb);
                }
            }

            actionBarTranslated(level, x, y, z, "5", "event.deep_lucky_block.end_knight.action", spawnCount);
            narrateTranslated(level, x, y, z, random, "event.deep_lucky_block.end_knight.narration", spawnCount);
        };
    }

    private static void event_CursedTomb(ServerLevel level, double x, double y, double z,
                                          RandomSource random, TestProcedure.BranchResult r, int mainLuck) {
        r.title = "An old tomb crawls upward..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("SOUL"); r.particles.add("ASH");
        r.deferredAction = () -> {
            playBadSound(level, x, y, z);
            int px = (int) Math.floor(x), py = (int) Math.floor(y), pz = (int) Math.floor(z);
            final int W = 13, D = 9, H = 7;
            BlockPos o = new BlockPos(px - W / 2, py - 1, pz - D / 2);
            for (int dx = 0; dx < W; dx++) for (int dz2 = 0; dz2 < D; dz2++) { BlockState floor = random.nextInt(4) == 0 ? Blocks.SOUL_SAND.defaultBlockState() : random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.SOUL_SOIL.defaultBlockState(); level.setBlock(o.offset(dx, 0, dz2), floor, 3); }
            for (int dy = 1; dy <= H; dy++) { for (int dx = 0; dx < W; dx++) { BlockState wall = (dx + dy) % 5 == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState(); level.setBlock(o.offset(dx, dy, 0), wall, 3); level.setBlock(o.offset(dx, dy, D - 1), wall, 3); } for (int dz2 = 1; dz2 < D - 1; dz2++) { BlockState wall = (dz2 + dy) % 5 == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState(); level.setBlock(o.offset(0, dy, dz2), wall, 3); level.setBlock(o.offset(W - 1, dy, dz2), wall, 3); } }
            for (int dx = 0; dx < W; dx++) for (int dz2 = 0; dz2 < D; dz2++) { boolean edge = dx == 0 || dx == W - 1 || dz2 == 0 || dz2 == D - 1; level.setBlock(o.offset(dx, H + 1, dz2), edge ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState(), 3); }
            for (int dx = 1; dx < W - 1; dx++) for (int dz2 = 1; dz2 < D - 1; dz2++) for (int dy = 1; dy <= H; dy++) level.setBlock(o.offset(dx, dy, dz2), Blocks.AIR.defaultBlockState(), 3);
            int[] pillarX = {3, W - 4}; int[] pillarZ = {2, D - 3}; for (int pcx : pillarX) for (int pcz : pillarZ) for (int dy = 1; dy <= H; dy++) level.setBlock(o.offset(pcx, dy, pcz), dy == H ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 3);
            for (int dx = 2; dx < W - 2; dx += 3) { placeWallTorch(level, o.offset(dx, 3, 1), Direction.SOUTH); placeWallTorch(level, o.offset(dx, 3, D - 2), Direction.NORTH); }
            for (int dz2 = 2; dz2 < D - 2; dz2 += 3) { placeWallTorch(level, o.offset(1, 3, dz2), Direction.EAST); placeWallTorch(level, o.offset(W - 2, 3, dz2), Direction.WEST); }
            level.setBlock(o.offset(1, 1, 1), Blocks.SOUL_FIRE.defaultBlockState(), 3); level.setBlock(o.offset(W - 2, 1, 1), Blocks.SOUL_FIRE.defaultBlockState(), 3); level.setBlock(o.offset(1, 1, D - 2), Blocks.SOUL_FIRE.defaultBlockState(), 3); level.setBlock(o.offset(W - 2, 1, D - 2), Blocks.SOUL_FIRE.defaultBlockState(), 3);
            level.setBlock(o.offset(W / 2, H, D / 2), Blocks.SOUL_LANTERN.defaultBlockState(), 3); level.setBlock(o.offset(3, H, D / 2), Blocks.SOUL_LANTERN.defaultBlockState(), 3); level.setBlock(o.offset(W - 4, H, D / 2), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
            BlockPos spawnerPos = o.offset(W / 2, 1, D / 2); level.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 3); if (level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity sp) sp.getSpawner().setEntityId(EntityType.ZOMBIE, level, random, spawnerPos);
            for (int i = 0; i < 10; i++) level.setBlock(o.offset(1 + random.nextInt(W - 2), 2 + random.nextInt(H - 1), 1 + random.nextInt(D - 2)), Blocks.COBWEB.defaultBlockState(), 3);
            level.setBlock(o.offset(W / 2, 1, 1), Blocks.SKELETON_SKULL.defaultBlockState(), 3); level.setBlock(o.offset(W / 2, 1, D - 2), Blocks.SKELETON_SKULL.defaultBlockState(), 3); level.setBlock(o.offset(2, 1, 1), Blocks.POTTED_DEAD_BUSH.defaultBlockState(), 3); level.setBlock(o.offset(W - 3, 1, 1), Blocks.POTTED_DEAD_BUSH.defaultBlockState(), 3);
            List<BlockPos> reserved = new ArrayList<>(); reserved.add(spawnerPos);
            for (int i = 0; i < 3 + random.nextInt(2); i++) { Zombie zomb = new Zombie(EntityType.ZOMBIE, level); zomb.moveTo(o.getX() + 2 + random.nextInt(W - 4), o.getY() + 2, o.getZ() + 2 + random.nextInt(D - 4)); zomb.setPersistenceRequired(); zomb.setCustomName(Component.literal("§c§l").append(Component.translatable(FAMILY_NAMES[random.nextInt(FAMILY_NAMES.length)]))); zomb.setCustomNameVisible(true); equipZombieForLuck(zomb, level, mainLuck, random); level.addFreshEntity(zomb); }
            placeChestsInArea(level, o, 1, W - 2, 1, D - 2, 1, reserved, random);
            level.setBlock(o.offset(W / 2, 1, 0), Blocks.AIR.defaultBlockState(), 3); level.setBlock(o.offset(W / 2, 2, 0), Blocks.AIR.defaultBlockState(), 3); level.setBlock(o.offset(W / 2, 3, 0), Blocks.AIR.defaultBlockState(), 3);
            ensureAirAtPlayer(level, x, y, z); actionBar(level, x, y, z, "8", "💀 Cursed Family Tomb!"); narrate(level, x, y, z, random, "A cursed family tomb appears..."); };
    }

    private static void event_ArenaOfTheForgotten(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r, int mainLuck) {
        r.title = "Walls rise to cage you..."; r.titleColorCode = "c"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE"); r.particles.add("ASH");
        r.deferredAction = () -> {
            playBadSound(level, x, y, z); int px = (int) Math.floor(x), py = (int) Math.floor(y), pz = (int) Math.floor(z); final int W = 15, D = 15, H = 9; BlockPos o = new BlockPos(px - W / 2, py - 1, pz - D / 2);
            for (int dx = 0; dx < W; dx++) for (int dz2 = 0; dz2 < D; dz2++) { boolean path = dx == W / 2 || dz2 == D / 2; BlockState floor = path ? Blocks.GRAVEL.defaultBlockState() : random.nextInt(4) == 0 ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState(); level.setBlock(o.offset(dx, 0, dz2), floor, 3); }
            for (int dy = 1; dy <= H; dy++) { for (int dx = 0; dx < W; dx++) { BlockState w = random.nextInt(5) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState(); level.setBlock(o.offset(dx, dy, 0), w, 3); level.setBlock(o.offset(dx, dy, D - 1), w, 3); } for (int dz2 = 1; dz2 < D - 1; dz2++) { BlockState w = random.nextInt(5) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState(); level.setBlock(o.offset(0, dy, dz2), w, 3); level.setBlock(o.offset(W - 1, dy, dz2), w, 3); } }
            for (int dx = 0; dx < W; dx++) for (int dz2 = 0; dz2 < D; dz2++) { boolean edge = dx == 0 || dx == W - 1 || dz2 == 0 || dz2 == D - 1; level.setBlock(o.offset(dx, H + 1, dz2), edge ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), 3); }
            for (int dx = 1; dx < W - 1; dx++) for (int dz2 = 1; dz2 < D - 1; dz2++) for (int dy = 1; dy <= H; dy++) level.setBlock(o.offset(dx, dy, dz2), Blocks.AIR.defaultBlockState(), 3);
            int[][] corners = {{1, 1}, {1, D - 2}, {W - 2, 1}, {W - 2, D - 2}}; for (int[] c : corners) { for (int dy = 1; dy <= 4; dy++) level.setBlock(o.offset(c[0], dy, c[1]), dy == 4 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState(), 3); level.setBlock(o.offset(c[0], 5, c[1]), Blocks.LANTERN.defaultBlockState(), 3); }
            for (int dx = 3; dx < W - 3; dx += 3) { placeWallTorch(level, o.offset(dx, 3, 1), Direction.SOUTH); placeWallTorch(level, o.offset(dx, 3, D - 2), Direction.NORTH); } for (int dz2 = 3; dz2 < D - 3; dz2 += 3) { placeWallTorch(level, o.offset(1, 3, dz2), Direction.EAST); placeWallTorch(level, o.offset(W - 2, 3, dz2), Direction.WEST); }
            level.setBlock(o.offset(W / 2, H, D / 2), Blocks.CHAIN.defaultBlockState(), 3); level.setBlock(o.offset(W / 2, H - 1, D / 2), Blocks.LANTERN.defaultBlockState(), 3);
            for (int dx = 3; dx < W - 3; dx++) { level.setBlock(o.offset(dx, 1, 3), Blocks.IRON_BARS.defaultBlockState(), 3); level.setBlock(o.offset(dx, 1, D - 4), Blocks.IRON_BARS.defaultBlockState(), 3); } for (int dz2 = 3; dz2 < D - 3; dz2++) { level.setBlock(o.offset(3, 1, dz2), Blocks.IRON_BARS.defaultBlockState(), 3); level.setBlock(o.offset(W - 4, 1, dz2), Blocks.IRON_BARS.defaultBlockState(), 3); }
            level.setBlock(o.offset(W / 2, 1, 3), Blocks.AIR.defaultBlockState(), 3); level.setBlock(o.offset(W / 2, 1, D - 4), Blocks.AIR.defaultBlockState(), 3); level.setBlock(o.offset(3, 1, D / 2), Blocks.AIR.defaultBlockState(), 3); level.setBlock(o.offset(W - 4, 1, D / 2), Blocks.AIR.defaultBlockState(), 3);
            int mobCount = 4 + random.nextInt(3); List<BlockPos> reserved = new ArrayList<>(); for (int i = 0; i < mobCount; i++) { Zombie zomb = new Zombie(EntityType.ZOMBIE, level); zomb.moveTo(o.getX() + 4 + random.nextInt(W - 8), o.getY() + 2, o.getZ() + 4 + random.nextInt(D - 8)); zomb.setPersistenceRequired(); equipZombieForLuck(zomb, level, mainLuck, random); level.addFreshEntity(zomb); }
            placeChestsInArea(level, o, 1, 2, 1, D - 2, 1, reserved, random); ensureAirAtPlayer(level, x, y, z); actionBar(level, x, y, z, "c", "⚔ Arena of the Forgotten!"); narrate(level, x, y, z, random, "An arena materializes... equipped zombies await."); };
    }

    private static void event_CavernOfDespair(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The floor gives way..."; r.titleColorCode = "4"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("SOUL"); r.particles.add("SQUID_INK"); r.particles.add("ASH");
        r.deferredAction = () -> {
            playBadSound(level, x, y, z); int px = (int) Math.floor(x), py = (int) Math.floor(y), pz = (int) Math.floor(z); final int W = 17, D = 17, H = 12; BlockPos o = new BlockPos(px - W / 2, py - 10, pz - D / 2);
            for (int dx = 0; dx < W; dx++) for (int dz2 = 0; dz2 < D; dz2++) { level.setBlock(o.offset(dx, 0, dz2), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3); level.setBlock(o.offset(dx, H, dz2), random.nextInt(4) == 0 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState(), 3); }
            for (int dy = 1; dy < H; dy++) { for (int dx = 0; dx < W; dx++) { level.setBlock(o.offset(dx, dy, 0), Blocks.DEEPSLATE.defaultBlockState(), 3); level.setBlock(o.offset(dx, dy, D - 1), Blocks.DEEPSLATE.defaultBlockState(), 3); } for (int dz2 = 1; dz2 < D - 1; dz2++) { level.setBlock(o.offset(0, dy, dz2), Blocks.DEEPSLATE.defaultBlockState(), 3); level.setBlock(o.offset(W - 1, dy, dz2), Blocks.DEEPSLATE.defaultBlockState(), 3); } }
            for (int dx = 2; dx < W - 2; dx++) for (int dz2 = 2; dz2 < D - 2; dz2++) for (int dy = 1; dy < H; dy++) level.setBlock(o.offset(dx, dy, dz2), Blocks.AIR.defaultBlockState(), 3);
            for (int dx = 2; dx < W - 2; dx++) for (int dz2 = 2; dz2 < D - 2; dz2++) { BlockState ground = random.nextInt(5) == 0 ? Blocks.SOUL_SAND.defaultBlockState() : random.nextInt(4) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(); level.setBlock(o.offset(dx, 1, dz2), ground, 3); }
            for (int dx = 4; dx < W - 4; dx++) for (int dz2 = 4; dz2 < D - 4; dz2++) if (random.nextInt(4) == 0 && !(dx >= W / 2 - 1 && dx <= W / 2 + 1 && dz2 >= D / 2 - 1 && dz2 <= D / 2 + 1)) level.setBlock(o.offset(dx, 1, dz2), Blocks.LAVA.defaultBlockState(), 3);
            for (int i = 0; i < 20; i++) level.setBlock(o.offset(2 + random.nextInt(W - 4), 2 + random.nextInt(H - 3), 2 + random.nextInt(D - 4)), Blocks.COBWEB.defaultBlockState(), 3);
            BlockPos sp1 = o.offset(W / 2, 2, D / 2); BlockPos sp2 = o.offset(4, 2, 4); BlockPos sp3 = o.offset(W - 5, 2, D - 5); for (BlockPos spPos : new BlockPos[]{sp1, sp2, sp3}) { level.setBlock(spPos, Blocks.SPAWNER.defaultBlockState(), 3); if (level.getBlockEntity(spPos) instanceof SpawnerBlockEntity s) s.getSpawner().setEntityId(EntityType.ZOMBIE, level, random, spPos); }
            for (int i = 0; i < 6; i++) { Zombie zomb = new Zombie(EntityType.ZOMBIE, level); zomb.moveTo(o.getX() + 4 + random.nextInt(W - 8), o.getY() + 3, o.getZ() + 4 + random.nextInt(D - 8)); zomb.setPersistenceRequired(); level.addFreshEntity(zomb); }
            List<BlockPos> reserved = List.of(sp1, sp2, sp3); int chestCount = 1 + random.nextInt(5); int placed = 0, attempts = 0; while (placed < chestCount && attempts < 50) { int cx2 = 3 + random.nextInt(W - 6); int cz2 = 3 + random.nextInt(D - 6); BlockPos cp = o.offset(cx2, 2, cz2); if (level.getBlockState(cp).isAir() && !level.getBlockState(o.offset(cx2, 1, cz2)).is(Blocks.LAVA) && !reserved.contains(cp)) { placeLuckyChest(level, cp, rollChestRank(random)); placed++; } attempts++; }
            for (int dy = 0; dy >= -10; dy--) { level.setBlock(new BlockPos(px, py + dy, pz), Blocks.AIR.defaultBlockState(), 3); level.setBlock(new BlockPos(px + 1, py + dy, pz), Blocks.AIR.defaultBlockState(), 3); level.setBlock(new BlockPos(px, py + dy, pz + 1), Blocks.AIR.defaultBlockState(), 3); level.setBlock(new BlockPos(px + 1, py + dy, pz + 1), Blocks.AIR.defaultBlockState(), 3); }
            int landY = py - 10 + 2; for (int dx = -1; dx <= 1; dx++) for (int dz2 = -1; dz2 <= 1; dz2++) { level.setBlock(new BlockPos(px + dx, landY, pz + dz2), Blocks.AIR.defaultBlockState(), 3); level.setBlock(new BlockPos(px + dx, landY + 1, pz + dz2), Blocks.AIR.defaultBlockState(), 3); } level.setBlock(new BlockPos(px, landY - 1, pz), Blocks.DIRT.defaultBlockState(), 3);
            actionBar(level, x, y, z, "4", "⚠ Cavern of Despair!"); narrate(level, x, y, z, random, "The ground opens... cursed cavern full of lava and monsters."); };
    }

    // ==================== ZOMBIE EQUIP HELPERS ====================

    private static void equipZombieForLuck(Zombie zombie, ServerLevel level, int mainLuck, RandomSource random) {
        // T338-MOB-PIECE-BY-PIECE : Simule pièce par pièce un vrai tirage Lucky Block autour de la moyenne désirée
        // (au lieu d'une armure 4/4 + épée ayant toutes le même grade).
        int el = Math.max(10, Math.min(mainLuck, 50));
        int desiredBonusLuck = Mth.clamp(el * 2 - 45, -45, 55);
        GenerateLuckyItemProcedure.equipMobPieceByPieceFromLuckyBlock(
                zombie, level, desiredBonusLuck, 0.34f, 0.68f, true, 0.02f, 0.05f, random);
    }

    // Equipement aleatoire enchante piece par piece (simulation Lucky Block) pour les mobs du tunnel : 15% de drop chacune.
    private static void equipTunnelMob(Zombie z, ServerLevel level, RandomSource random) {
        int desiredBonusLuck = -25 + random.nextInt(76); // -25..+50 autour d'un Lucky Block bas/moyen
        GenerateLuckyItemProcedure.equipMobPieceByPieceFromLuckyBlock(
                z, level, desiredBonusLuck, 0.32f, 0.65f, true, 0.15f, 0.15f, random);
    }

    private static ItemStack enchantedPiece(Item item, int luck, RandomSource random) {
        ItemStack stack = new ItemStack(item); String type = detectConfigType(item);
        if (type != null) { int enchCount = 1 + (luck > 30 ? random.nextInt(2) : 0); int enchLevel = Math.min(1 + random.nextInt(Math.max(1, luck / 20)), 5); List<String> pool = loadEnchantPoolStatic(type); Collections.shuffle(pool, new java.util.Random(random.nextLong())); int applied = 0; for (String id : pool) { if (applied >= enchCount) break; GenerateLuckyItemProcedure.addEnchantment(stack, id, enchLevel); applied++; } }
        return stack;
    }

    private static String detectConfigType(Item item) {
        if (item == Items.LEATHER_HELMET || item == Items.IRON_HELMET) return "helmet"; if (item == Items.LEATHER_CHESTPLATE || item == Items.IRON_CHESTPLATE) return "chestplate"; if (item == Items.LEATHER_LEGGINGS || item == Items.IRON_LEGGINGS) return "leggings"; if (item == Items.LEATHER_BOOTS || item == Items.IRON_BOOTS) return "boots"; if (item == Items.IRON_SWORD || item == Items.STONE_SWORD) return "sword"; if (item == Items.BOW) return "bow"; if (item == Items.CROSSBOW) return "crossbow"; return null;
    }

    private static List<String> loadEnchantPoolStatic(String type) {
        List<String> pool = new ArrayList<>(); try { java.lang.reflect.Method m = GenerateLuckyItemProcedure.class.getDeclaredMethod("loadEnchantPool", String.class); m.setAccessible(true); @SuppressWarnings("unchecked") List<String> result = (List<String>) m.invoke(null, type); if (result != null) pool.addAll(result); } catch (Exception ignored) {} return pool;
    }

    // ==================== GLOWING ITEM ====================

    private static void spawnGlowingItem(ServerLevel level, double x, double y, double z, ItemStack item) {
        if (!item.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§l").append(Component.translatable("event.deep_lucky_block.name.mysterious_loot")));
        ItemEntity e = new ItemEntity(level, x, y, z, item);
        e.setInvulnerable(true);
        e.setPickUpDelay(20);

        CompoundTag nbt = new CompoundTag();
        e.save(nbt);
        nbt.putShort("Age", (short) -32768);
        nbt.putShort("Health", (short) 32767);
        nbt.putBoolean("Invulnerable", true);
        nbt.putBoolean("PersistenceRequired", true);
        nbt.putShort("PickupDelay", (short) 20);
        nbt.putInt("Lifespan", Integer.MAX_VALUE);
        e.load(nbt);

        e.setGlowingTag(true);
        e.setNoGravity(false);
        e.setDeltaMovement(0, -0.05, 0);
        e.setCustomNameVisible(false);
        level.addFreshEntity(e);
    }

    // ==================== PROPAGATION ZONE SUR LE SOL (FADE À 0) ====================
    private static void replaceGroundWithFade(ServerLevel level, double x, double y, double z, RandomSource random, int maxRadius, BlockState replacement) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        int cz = (int) Math.floor(z);
        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > maxRadius * maxRadius) continue;
                double dist = Math.sqrt(distSq);
                double chance = 1.0 - (dist / (double) maxRadius);
                if (random.nextDouble() > chance) continue;

                for (int dy = 0; dy >= -2; dy--) {
                    BlockPos p = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState state = level.getBlockState(p);
                    if (!state.isAir() && state.blocksMotion() && !state.is(Blocks.BEDROCK)) {
                        level.setBlock(p, replacement, 2);
                        break;
                    }
                }
            }
        }
    }

    // ==================== 5 NOUVEAUX ÉVÉNEMENTS TROLL (ZÉRO POTION) ====================

    private static void event_AnvilRain(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Better watch the sky..."; r.titleColorCode = "c"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> {
            playBadSound(level, x, y, z);
            int cx = (int) Math.floor(x), cy = Math.min(319, (int) Math.floor(y) + 72), cz = (int) Math.floor(z);
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if (dx * dx + dz * dz > 49) continue;
                    int roll = random.nextInt(100);
                    BlockPos pos = new BlockPos(cx + dx, cy, cz + dz);
                    if (roll < 60) {
                        FallingBlockEntity.fall(level, pos, Blocks.DAMAGED_ANVIL.defaultBlockState());
                    } else if (roll < 65) {
                        FallingBlockEntity.fall(level, pos, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
                    }
                }
            }
            actionBar(level, x, y, z, "c", "⚠ Pluie d'Enclumes ! Regarde en l'air...");
        };
    }

    private static void event_FakeDiamondTroll(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Gems twinkle nearby..."; r.titleColorCode = "6"; r.rank = TestProcedure.ItemRank.COMMON; r.particles.add("HAPPY_VILLAGER");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
            int count = 5 + random.nextInt(4);
            List<ItemEntity> fakeGems = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ItemStack diamond = new ItemStack(Items.DIAMOND);
                diamond.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§b§l").append(Component.translatable("event.deep_lucky_block.name.diamond")));
                ItemEntity ie = new ItemEntity(level, x - 2 + random.nextDouble() * 4, y + 1, z - 2 + random.nextDouble() * 4, diamond);
                ie.setPickUpDelay(32767);
                ie.setGlowingTag(true);
                level.addFreshEntity(ie);
                fakeGems.add(ie);
            }
            actionBar(level, x, y, z, "6", "✦ Diamants !!! ✦");
            TestProcedure.schedule(level, TestProcedure.currentTick(level) + 60, () -> {
                for (ItemEntity ie : fakeGems) {
                    if (ie != null && ie.isAlive()) {
                        level.sendParticles(ParticleTypes.FIREWORK, ie.getX(), ie.getY(), ie.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                        level.playSound(null, ie.getX(), ie.getY(), ie.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                        ie.discard();
                    }
                }
            });
        };
    }

    private record FireworkDesign(byte type, int[] colors, int[] fades,
                                  boolean trail, boolean flicker) {}

    // 20 designs bases sur les combinaisons communautaires les plus appreciees :
    // rainbow, creeper vert, starburst dore, galaxy, ice, sunset, pastel, etc.
    private static FireworkDesign communityFireworkDesign(int id) {
        int red = DyeColor.RED.getFireworkColor();
        int orange = DyeColor.ORANGE.getFireworkColor();
        int yellow = DyeColor.YELLOW.getFireworkColor();
        int lime = DyeColor.LIME.getFireworkColor();
        int green = DyeColor.GREEN.getFireworkColor();
        int cyan = DyeColor.CYAN.getFireworkColor();
        int lightBlue = DyeColor.LIGHT_BLUE.getFireworkColor();
        int blue = DyeColor.BLUE.getFireworkColor();
        int purple = DyeColor.PURPLE.getFireworkColor();
        int magenta = DyeColor.MAGENTA.getFireworkColor();
        int pink = DyeColor.PINK.getFireworkColor();
        int white = DyeColor.WHITE.getFireworkColor();
        int black = DyeColor.BLACK.getFireworkColor();
        int gray = DyeColor.GRAY.getFireworkColor();

        return switch (Math.floorMod(id, 20)) {
            case 0 -> new FireworkDesign((byte) 4, new int[]{red, orange, yellow, lime, cyan, blue, purple, magenta}, new int[]{white}, true, true); // rainbow burst
            case 1 -> new FireworkDesign((byte) 3, new int[]{black}, new int[]{lime, green}, true, true); // creeper reveal
            case 2 -> new FireworkDesign((byte) 2, new int[]{yellow, orange, white}, new int[]{red}, true, true); // golden starburst
            case 3 -> new FireworkDesign((byte) 1, new int[]{purple, magenta, blue}, new int[]{black, cyan}, true, true); // galaxy
            case 4 -> new FireworkDesign((byte) 1, new int[]{lightBlue, white, cyan}, new int[]{blue}, true, true); // ice nova
            case 5 -> new FireworkDesign((byte) 4, new int[]{orange, red, pink}, new int[]{purple}, false, true); // sunset burst
            case 6 -> new FireworkDesign((byte) 2, new int[]{red, white, blue}, new int[]{white}, true, true); // tricolor star
            case 7 -> new FireworkDesign((byte) 3, new int[]{green, lime}, new int[]{yellow}, false, true); // classic creeper
            case 8 -> new FireworkDesign((byte) 1, new int[]{white}, new int[]{lightBlue, gray}, true, true); // silver willow
            case 9 -> new FireworkDesign((byte) 0, new int[]{pink, magenta, white}, new int[]{purple}, true, true); // cherry blossom
            case 10 -> new FireworkDesign((byte) 4, new int[]{cyan, lime, yellow}, new int[]{white}, true, false); // aurora
            case 11 -> new FireworkDesign((byte) 2, new int[]{blue, cyan}, new int[]{white}, true, false); // ocean star
            case 12 -> new FireworkDesign((byte) 1, new int[]{red, orange}, new int[]{yellow, white}, false, true); // solar flare
            case 13 -> new FireworkDesign((byte) 3, new int[]{purple, black}, new int[]{magenta}, true, true); // ender creeper
            case 14 -> new FireworkDesign((byte) 4, new int[]{lime, green, cyan}, new int[]{yellow}, false, true); // toxic burst
            case 15 -> new FireworkDesign((byte) 2, new int[]{white, yellow}, new int[]{orange}, true, true); // shooting star
            case 16 -> new FireworkDesign((byte) 1, new int[]{red, magenta, pink}, new int[]{white}, true, false); // ruby bloom
            case 17 -> new FireworkDesign((byte) 0, new int[]{black}, new int[]{red, orange, yellow}, true, true); // night reveal
            case 18 -> new FireworkDesign((byte) 4, new int[]{purple, pink, lightBlue, lime, yellow}, new int[]{white}, true, true); // festival
            default -> new FireworkDesign((byte) 1, new int[]{blue, green, pink, white}, new int[]{cyan, yellow}, true, true); // community finale
        };
    }

    /** T229 : Type NBT hérité (0-4) -> Shape du composant 1.21.1. */
    private static net.minecraft.world.item.component.FireworkExplosion.Shape
            shapeFromLegacyType(byte type) {
        return switch (type) {
            case 1 -> net.minecraft.world.item.component.FireworkExplosion.Shape.LARGE_BALL;
            case 2 -> net.minecraft.world.item.component.FireworkExplosion.Shape.STAR;
            case 3 -> net.minecraft.world.item.component.FireworkExplosion.Shape.CREEPER;
            case 4 -> net.minecraft.world.item.component.FireworkExplosion.Shape.BURST;
            default -> net.minecraft.world.item.component.FireworkExplosion.Shape.SMALL_BALL;
        };
    }

    private static ItemStack createCommunityFirework(RandomSource random) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag fireworks = deepluckyblock.util.DeepNbt.getOrCreateTagElement(rocket, "Fireworks");
        fireworks.putByte("Flight", (byte) (2 + random.nextInt(2)));
        ListTag explosions = new ListTag();

        int layers = 1 + random.nextInt(3);
        for (int layer = 0; layer < layers; layer++) {
            FireworkDesign design = communityFireworkDesign(random.nextInt(20));
            CompoundTag explosion = new CompoundTag();
            explosion.putByte("Type", design.type());
            explosion.putIntArray("Colors", design.colors());
            explosion.putIntArray("FadeColors", design.fades());
            explosion.putBoolean("Trail", design.trail());
            explosion.putBoolean("Flicker", design.flicker());
            explosions.add(explosion);
        }
        fireworks.put("Explosions", explosions);

        // ⛔ T229 — Le bloc ci-dessus ecrit le NBT « Fireworks/Explosions », qui
        //    est le format de 1.20.4 ET ANTERIEUR. Depuis 1.20.5 le rendu client
        //    lit le COMPOSANT minecraft:fireworks ; l'ancien NBT est inerte pour
        //    l'affichage. Les 20 designs communautaires sortaient donc
        //    probablement en fusee blanche par defaut.
        //    On reecrit la meme chose au format composant 1.21.1. L'ancien NBT
        //    est laisse en place (inoffensif) au cas ou il servirait ailleurs.
        try {
            java.util.List<net.minecraft.world.item.component.FireworkExplosion> layersC =
                    new java.util.ArrayList<>();
            for (int i = 0; i < explosions.size(); i++) {
                CompoundTag e = explosions.getCompound(i);
                layersC.add(new net.minecraft.world.item.component.FireworkExplosion(
                        shapeFromLegacyType(e.getByte("Type")),
                        new it.unimi.dsi.fastutil.ints.IntArrayList(e.getIntArray("Colors")),
                        new it.unimi.dsi.fastutil.ints.IntArrayList(e.getIntArray("FadeColors")),
                        e.getBoolean("Trail"),
                        e.getBoolean("Flicker")));
            }
            rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS,
                    new net.minecraft.world.item.component.Fireworks(
                            fireworks.getByte("Flight"), layersC));
        } catch (Exception ignored) {}
        return rocket;
    }

    private static void launchCommunityFireworkShow(ServerLevel level, double x, double y, double z,
                                                     RandomSource random) {
        int rockets = 6 + random.nextInt(7); // 6-12 rockets par creeper festif
        int start = TestProcedure.currentTick(level);
        for (int i = 0; i < rockets; i++) {
            final int delay = i * 4;
            TestProcedure.schedule(level, start + delay, () -> {
                double fx = x + (random.nextDouble() - 0.5) * 5.0;
                double fz = z + (random.nextDouble() - 0.5) * 5.0;
                // Le rocket part du creeper, monte reellement dans le ciel, puis explose.
                FireworkRocketEntity rocket = new FireworkRocketEntity(
                        level, fx, y + 1.2, fz, createCommunityFirework(random));
                rocket.setDeltaMovement(
                        (random.nextDouble() - 0.5) * 0.08,
                        0.65 + random.nextDouble() * 0.18,
                        (random.nextDouble() - 0.5) * 0.08);
                level.addFreshEntity(rocket);
            });
        }
    }

    private static void setCreeperCharged(Creeper creeper, boolean charged) {
        if (!charged) return;
        // La propriete vanilla du creeper charge est serialisee sous "powered".
        CompoundTag tag = new CompoundTag();
        creeper.save(tag);
        tag.putBoolean("powered", true);
        creeper.load(tag);
    }

    private static void event_CreeperParty(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A surprise is brewing...";
        r.titleColorCode = "a";
        r.rank = TestProcedure.ItemRank.COMMON;
        r.particles.add("FIREWORK");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            Player target = level.getNearestPlayer(x, y, z, 32.0, false);

            int creeperCount = 8 + random.nextInt(9); // 8-16 en cercle
            double ringRadius = 7.0 + random.nextDouble() * 4.0;
            for (int i = 0; i < creeperCount; i++) {
                double angle = (Math.PI * 2.0 * i / creeperCount)
                        + (random.nextDouble() - 0.5) * 0.22;
                Creeper creeper = new Creeper(EntityType.CREEPER, level);
                creeper.moveTo(x + Math.cos(angle) * ringRadius, y + 1.0,
                        z + Math.sin(angle) * ringRadius,
                        (float) Math.toDegrees(angle + Math.PI), 0.0f);
                creeper.setCustomName(Component.literal("§a§l").append(
                        Component.translatable("event.deep_lucky_block.name.deep_friend")));
                creeper.setCustomNameVisible(true);
                creeper.setPersistenceRequired();

                // Les vrais ET les faux peuvent etre charges.
                boolean charged = random.nextDouble() < 0.28;
                setCreeperCharged(creeper, charged);

                boolean realCreeper = random.nextBoolean(); // 50 % normal, 50 % spectacle
                if (realCreeper) {
                    creeper.setNoAi(false);
                    if (target != null) creeper.setTarget(target);
                    level.addFreshEntity(creeper);
                } else {
                    creeper.setNoAi(true);
                    level.addFreshEntity(creeper);
                    TestProcedure.schedule(level, TestProcedure.currentTick(level) + 50, () -> {
                        if (!creeper.isAlive()) return;
                        double fx = creeper.getX();
                        double fy = creeper.getY();
                        double fz = creeper.getZ();
                        creeper.discard();
                        launchCommunityFireworkShow(level, fx, fy, fz, random);
                    });
                }
            }
            actionBar(level, x, y, z, "a", "Don't move...");
        };
    }

    private static void event_LavaCageTroll(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Glass walls close in...";
        r.titleColorCode = "4";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("FLAME");
        r.deferredAction = () -> {
            playBadSound(level, x, y, z);
            int bx = Mth.floor(x);
            int by = Mth.floor(y);
            int bz = Mth.floor(z);
            BlockPos sphereCenter = new BlockPos(bx, by + 1, bz);

            List<BlockPos> glassPositions = new ArrayList<>();
            List<BlockPos> lavaSources = new ArrayList<>();
            Map<BlockPos, BlockState> originalGlassStates = new HashMap<>();
            HashSet<BlockPos> originallyAir = new HashSet<>();

            // Memorise tout l'air de la zone : permet de nettoyer aussi la lave qui a coule.
            int scanRadius = 7;
            for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                for (int dy = -scanRadius; dy <= scanRadius; dy++) {
                    for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                        if (dx * dx + dy * dy + dz * dz > scanRadius * scanRadius) continue;
                        BlockPos pos = sphereCenter.offset(dx, dy, dz);
                        if (level.getBlockState(pos).isAir()) originallyAir.add(pos.immutable());
                    }
                }
            }

            // Cage COMPLETE 5x5x5 : quatre murs, sol et plafond.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean shell = Math.abs(dx) == 2 || Math.abs(dy) == 2 || Math.abs(dz) == 2;
                        if (!shell) continue;
                        BlockPos pos = sphereCenter.offset(dx, dy, dz);
                        BlockState old = level.getBlockState(pos);
                        if (old.is(Blocks.BEDROCK)) continue;
                        originalGlassStates.put(pos.immutable(), old);
                        level.setBlock(pos, Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), 3);
                        glassPositions.add(pos.immutable());
                    }
                }
            }

            // Sphere CREUSE de lave, rayon ~6 et epaisseur ~1 bloc.
            // On ne remplace que l'air : l'interieur reste vide et la cage reste intacte.
            final double radius = 6.0;
            final double innerRadius = 5.25;
            final double outerRadius = 6.35;
            int outer = (int) Math.ceil(outerRadius);
            for (int dx = -outer; dx <= outer; dx++) {
                for (int dy = -outer; dy <= outer; dy++) {
                    for (int dz = -outer; dz <= outer; dz++) {
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (distance < innerRadius || distance > outerRadius) continue;
                        BlockPos pos = sphereCenter.offset(dx, dy, dz);
                        if (!level.getBlockState(pos).isAir()) continue;
                        level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
                        lavaSources.add(pos.immutable());
                    }
                }
            }

            Collections.shuffle(glassPositions, new java.util.Random(random.nextLong()));
            actionBar(level, x, y, z, "4", "The heat keeps rising...");
            level.playSound(null, x, y, z, SoundEvents.LAVA_AMBIENT,
                    SoundSource.BLOCKS, 1.8f, 0.65f);

            // La cage se fissure en quatre etapes. A la fin, toute lave creee dans
            // l'air initial est nettoyee, y compris les coulures hors de la coquille.
            int batchSize = Math.max(1, (glassPositions.size() + 3) / 4);
            for (int step = 1; step <= 4; step++) {
                final int currentStep = step;
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + step * 20, () -> {
                    level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK,
                            SoundSource.BLOCKS, 1.0f, 0.8f + currentStep * 0.1f);
                    int from = (currentStep - 1) * batchSize;
                    int to = Math.min(glassPositions.size(), currentStep * batchSize);
                    for (int i = from; i < to; i++) {
                        BlockPos pos = glassPositions.get(i);
                        if (level.getBlockState(pos).is(Blocks.ORANGE_STAINED_GLASS)) {
                            BlockState original = originalGlassStates.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                            level.setBlock(pos, original, 3);
                            level.sendParticles(ParticleTypes.FLAME,
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    3, 0.2, 0.2, 0.2, 0.03);
                        }
                    }

                    if (currentStep == 4) {
                        // FIX rapport en jeu ("tu as supprimé la lave en meme temps que ton
                        // verre, alors qu'il faut laisser la lave dans le monde") : la cage
                        // de verre se brise et devoile la sphere de lave, qui doit RESTER en
                        // place comme un vrai piege environnemental. On ne nettoie donc plus
                        // la lave ici -- seul le verre est retire (boucle au-dessus).
                        Block.popResource(level, sphereCenter, new ItemStack(Items.EXPERIENCE_BOTTLE, 3));
                    }
                });
            }
        };
    }

    private static void event_WardenScreamTroll(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The earth shudders deep..."; r.titleColorCode = "3"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SCULK_SOUL");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, 2.0f, 0.8f);
            level.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0f, 0.8f);
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1, z, 40, 1.0, 1.0, 1.0, 0.1);

            Entity warden = EntityType.WARDEN.create(level);
            if (warden == null) return;
            warden.moveTo(x + 5.0, y, z, random.nextFloat() * 360, 0);
            level.addFreshEntity(warden);
            actionBar(level, x, y, z, "4", "⚠ LE WARDEN S'ÉVEILLE... ⚠");

            // FIX rapport en jeu (chat "faut attendre une seconde de plus avant
            // de faire tomber le lucy snow golem, il a pas le temps de se
            // relever droit") : 50 ticks (2.5s) ne suffisaient pas au Warden
            // pour terminer son animation d'apparition/redressement avant que
            // le golem ne lui tombe dessus. Porté à 70 ticks (3.5s), soit
            // +1 seconde comme demandé.
            TestProcedure.schedule(level, TestProcedure.currentTick(level) + 70, () -> {
                if (!warden.isAlive()) return;
                if (random.nextInt(100) < 60) {
                    SnowGolem snowman = new SnowGolem(EntityType.SNOW_GOLEM, level);
                    double spawnY = warden.getY() + 36.0;
                    snowman.moveTo(warden.getX(), spawnY, warden.getZ(), 0, 0);
                    snowman.setCustomName(Component.literal("§6§l").append(
                            Component.translatable("event.deep_lucky_block.name.lucky_snow_golem")));
                    snowman.setCustomNameVisible(true);
                    snowman.setPersistenceRequired();
                    snowman.setGlowingTag(true);
                    // Invulnerable le temps de la chute et du souffle d'impact.
                    snowman.setInvulnerable(true);
                    snowman.setDeltaMovement(0, -1.05, 0);
                    snowman.hurtMarked = true;
                    level.addFreshEntity(snowman);
                    level.sendParticles(ParticleTypes.CLOUD,
                            warden.getX(), spawnY, warden.getZ(),
                            40, 0.8, 0.8, 0.8, 0.06);
                    level.sendParticles(ParticleTypes.END_ROD,
                            warden.getX(), spawnY, warden.getZ(),
                            24, 0.5, 0.7, 0.5, 0.04);

                    // Trainee lumineuse pendant la chute pour qu'il soit bien visible.
                    for (int trail = 1; trail <= 5; trail++) {
                        TestProcedure.schedule(level,
                                TestProcedure.currentTick(level) + trail * 5, () -> {
                                    if (!snowman.isAlive()) return;
                                    level.sendParticles(ParticleTypes.SNOWFLAKE,
                                            snowman.getX(), snowman.getY() + 0.8, snowman.getZ(),
                                            12, 0.35, 0.45, 0.35, 0.04);
                                    level.sendParticles(ParticleTypes.END_ROD,
                                            snowman.getX(), snowman.getY() + 0.8, snowman.getZ(),
                                            5, 0.25, 0.35, 0.25, 0.02);
                                });
                    }

                    // Impact apres 1,4 s de chute visible.
                    TestProcedure.schedule(level, TestProcedure.currentTick(level) + 28, () -> {
                        if (!warden.isAlive()) return;
                        snowman.moveTo(warden.getX(), warden.getY() + 1.5, warden.getZ(), 0, 0);
                        snowman.hurtMarked = true;
                        warden.kill();
                        level.explode(null, warden.getX(), warden.getY() + 1, warden.getZ(),
                                2.0f, Level.ExplosionInteraction.NONE);
                        level.sendParticles(ParticleTypes.FIREWORK,
                                warden.getX(), warden.getY() + 1, warden.getZ(),
                                40, 0.6, 0.6, 0.6, 0.15);
                        level.sendParticles(ParticleTypes.SNOWFLAKE,
                                warden.getX(), warden.getY() + 1, warden.getZ(),
                                60, 0.8, 0.8, 0.8, 0.12);
                        level.playSound(null, warden.getX(), warden.getY(), warden.getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2f, 1.2f);
                        level.playSound(null, warden.getX(), warden.getY(), warden.getZ(),
                                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5f, 1.0f);

                        // Le golem survit au souffle, puis redevient vulnerable apres 3 s.
                        TestProcedure.schedule(level, TestProcedure.currentTick(level) + 60, () -> {
                            if (snowman.isAlive()) snowman.setInvulnerable(false);
                        });
                    });
                } else {
                    actionBar(level, x, y, z, "4", "It heard you...");
                }
            });
        };
    }

    // ==================== 5 NOUVEAUX ÉVÉNEMENTS NÉGATIFS V1.0.1 ====================

    private static void event_SpectralArrowShower(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Arrows hum overhead..."; r.titleColorCode = "e"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("CRIT");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.5f, 1.0f);
            for (int i = 0; i < 15; i++) {
                // FIX v41 : ItemStack.EMPTY causait "Cannot encode empty ItemStack"
                // (ItemStack.save() l'interdit explicitement depuis 1.21+, contrairement
                // a 1.20.1 ou c'etait tolere silencieusement) -> plantage de la
                // sauvegarde du chunk (EntityStorage) des qu'une de ces fleches
                // decoratives etait encore vivante. On lui donne une vraie pile
                // (1x spectral_arrow, l'item vanilla approprie pour ce type de fleche).
                SpectralArrow arrow = new SpectralArrow(level, x - 5 + random.nextDouble() * 10, y + 15, z - 5 + random.nextDouble() * 10, new ItemStack(Items.SPECTRAL_ARROW), null);
                arrow.setDeltaMovement(0, -1.5, 0);
                level.addFreshEntity(arrow);
            }
            actionBar(level, x, y, z, "e", "⚠ Pluie de Flèches Fantômes !");
        };
    }

    private static void event_SolarEclipse(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The sun begins to fade..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
            long oldTime = level.getDayTime();
            level.setDayTime(18000L);
            Player target = level.getNearestPlayer(x, y, z, 30.0, false);
            if (target instanceof ServerPlayer sp) {
                giveEventEffect(sp, MobEffects.DARKNESS, 600, 0);
                for (int i = 0; i < 3; i++) {
                    Phantom phantom = new Phantom(EntityType.PHANTOM, level);
                    phantom.moveTo(x - 5 + random.nextDouble() * 10, y + 10, z - 5 + random.nextDouble() * 10);
                    level.addFreshEntity(phantom);
                }
            }
            actionBar(level, x, y, z, "8", "The daylight is being swallowed...");
            TestProcedure.schedule(level, TestProcedure.currentTick(level) + 600, () -> {
                level.setDayTime(oldTime);
                actionBar(level, x, y, z, "e", "✦ Le jour revient après l'Éclipse !");
            });
        };
    }

    private static void event_CursedBellTroll(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A bell starts to toll..."; r.titleColorCode = "6"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SOUL");
        r.deferredAction = () -> {
            BlockPos bellPos = new BlockPos((int) Math.floor(x), (int) Math.floor(y) + 1, (int) Math.floor(z));
            level.setBlock(bellPos, Blocks.BELL.defaultBlockState(), 3);
            level.playSound(null, bellPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 0.8f);
            actionBar(level, x, y, z, "6", "The bell tolls again...");
            for (int round = 0; round < 5; round++) {
                final int rNum = round;
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + (round * 200), () -> {
                    if (!level.getBlockState(bellPos).is(Blocks.BELL)) return;
                    level.playSound(null, bellPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.0f);
                    level.sendParticles(ParticleTypes.SOUL, bellPos.getX() + 0.5, bellPos.getY() + 1, bellPos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
                    int count = 5 + random.nextInt(6);
                    for (int i = 0; i < count; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double dist = 4.0 + random.nextDouble() * 8.0;
                        Zombie m = new Zombie(EntityType.ZOMBIE, level);
                        m.moveTo(x + Math.cos(angle) * dist, y + 1, z + Math.sin(angle) * dist);
                        m.getPersistentData().putBoolean("slb_sanctuary_equipped", true);
                        level.addFreshEntity(m);
                    }
                    if (rNum == 4) {
                        TestProcedure.schedule(level, TestProcedure.currentTick(level) + 200, () -> {
                            if (level.getBlockState(bellPos).is(Blocks.BELL)) {
                                level.setBlock(bellPos, Blocks.AIR.defaultBlockState(), 3);
                                level.playSound(null, bellPos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.5f, 1.0f);
                                level.sendParticles(ParticleTypes.FIREWORK, bellPos.getX() + 0.5, bellPos.getY() + 1, bellPos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.1);
                                int appleCount = 1 + random.nextInt(6);
                                Block.popResource(level, bellPos, new ItemStack(Items.GOLDEN_APPLE, appleCount));
                            }
                        });
                    }
                });
            }
        };
    }

    private static void event_DragonIllusion(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Vast wings beat above..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("DRAGON_BREATH");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.8f);
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0f, 1.0f);
            Player target = level.getNearestPlayer(x, y, z, 30.0, false);
            if (target instanceof ServerPlayer sp) {
                giveEventEffect(sp, MobEffects.BLINDNESS, 60, 0);
            }
            actionBar(level, x, y, z, "5", "Something enormous is directly above you...");
        };
    }

    private static void event_MoltenFootsteps(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The ground grows warm..."; r.titleColorCode = "4"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("FLAME");
        r.deferredAction = () -> {
            int durationSeconds = 5 + random.nextInt(26);
            level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 1.5f, 1.0f);
            actionBar(level, x, y, z, "4", "Don't stop moving...");
            for (int s = 0; s < durationSeconds; s++) {
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + (s * 20), () -> {
                    Player target = level.getNearestPlayer(x, y, z, 50.0, false);
                    if (target != null && target.isAlive()) {
                        BlockPos footPos = target.blockPosition().below();
                        int radius = 1 + random.nextInt(3);
                        for (BlockPos p : BlockPos.betweenClosed(footPos.offset(-radius, 0, -radius), footPos.offset(radius, 0, radius))) {
                            BlockState state = level.getBlockState(p);
                            if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.LAVA)) {
                                level.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                                if (random.nextInt(100) < 5) {
                                    BlockPos lavaPos = p.immutable();
                                    TestProcedure.schedule(level, TestProcedure.currentTick(level) + 20, () -> {
                                        if (level.getBlockState(lavaPos).is(Blocks.MAGMA_BLOCK)) {
                                            level.setBlock(lavaPos, Blocks.LAVA.defaultBlockState(), 3);
                                        }
                                    });
                                }
                            }
                        }
                    }
                });
            }
        };
    }

    // ==================== PANDORA-INSPIRED EVENTS ====================

    // Transformation du sol centree sur le joueur : 100% des blocs de surface dans un rayon de 5, puis densite decroissante vers le bord.
    /**
     * ⛔ T230 — SURFACE REELLE D'UNE COLONNE.
     *
     * Retour en jeu : « on remplace les troncs d'arbres par de l'obsi [...] le
     * probleme est que ca ne fonctionne pas du tout ».
     *
     * Cause : tous les scans d'arbres bouclaient sur une bande Y FIXE autour du
     * joueur (par ex. dy = -2..+30 depuis cy = Y du bloc casse). Des que le
     * terrain monte ou descend — colline, vallee, plage — les troncs sortent de
     * la bande et ne sont JAMAIS vus. Sur terrain plat ca marchait, ailleurs
     * l'effet etait invisible.
     *
     * Ici on demande au heightmap la vraie hauteur de CHAQUE colonne, bornee a
     * +/-48 autour de la reference pour ne pas attraper une ile flottante.
     */
    // ⚠️ MOTION_BLOCKING renvoie le sommet de ce qui bloque le mouvement : sur une
    //    colonne boisee c'est le SOMMET DES FEUILLES, pas le sol. Les scans d'arbres
    //    doivent donc balayer VERS LE BAS depuis cette valeur (fenetre surf-36..surf+4)
    //    pour atteindre la base du tronc. Un arbre vanilla depasse rarement 30 blocs.
    private static int columnSurfaceY(ServerLevel level, int colX, int colZ, int refY) {
        try {
            int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                    colX, colZ) - 1;
            if (h < refY - 48 || h > refY + 48) return refY;
            return h;
        } catch (Exception e) {
            return refY;
        }
    }

    /** Ancienne signature : couverture DEGRADEE sur les bords, 1 seule couche. */
    private static void convertTerrainSafe(ServerLevel level, double x, double y, double z, int radius, int yRange, RandomSource random, BlockState[] replacements) {
        convertTerrainSafe(level, x, y, z, radius, yRange, random, replacements, false, 1);
    }

    /**
     * ⛔ T235 — COUVERTURE TOTALE OPTIONNELLE.
     *
     * Retour en jeu : « tout le sol de la zone doit etre remplace par de la
     * stone de l'End [...] la tu pose juste de maniere random des blocs d'obsi
     * ce qui n'a aucun sens et t'es formellement interdit ».
     *
     * Cause : cette methode ne convertissait une colonne qu'avec une
     * PROBABILITE decroissante avec la distance
     *     chance = 1 - (dist - 5) / (rayon - 5)
     * soit environ un tiers du disque sur un rayon de 24 a 42, et UN SEUL bloc
     * par colonne. Le resultat etait un sol mite, moitie end stone moitie herbe.
     * Ce degrade est volontaire pour les autres events (transition douce), donc
     * on ne le supprime pas : on ajoute un mode couverture totale.
     *
     * @param fullCoverage true = 100 % des colonnes du disque, sans degrade
     * @param layers       nombre de blocs solides convertis par colonne, de haut en bas
     */
    private static void convertTerrainSafe(ServerLevel level, double x, double y, double z, int radius, int yRange, RandomSource random, BlockState[] replacements, boolean fullCoverage, int layers) {
        if (replacements == null || replacements.length == 0) return;
        int maxR = Math.min(Math.max(radius, 1), 50);
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        final int inner = 5;
        for (int dx = -maxR; dx <= maxR; dx++) {
            for (int dz = -maxR; dz <= maxR; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > maxR) continue;
                if (!fullCoverage) {
                    double chance = dist <= inner ? 1.0 : (1.0 - (dist - inner) / (double) (maxR - inner));
                    if (chance <= 0.0 || random.nextDouble() > chance) continue;
                }
                int colX = cx + dx, colZ = cz + dz;
                int done = 0;                       // T235 : compte les couches posees
                // ⛔ T230 — MEME BUG QUE LES SCANS D'ARBRES, mais ici il touchait
                //    7 events d'un coup (InTheEnd, HellOnEarth, Lifeless, IceAge,
                //    Replace, SandForDessert, Transform).
                //    L'ancienne boucle allait de cy+3 a cy-8 : une fenetre de 12
                //    blocs centree sur le JOUEUR. Des que le sol d'une colonne
                //    sortait de cette fenetre (colline, vallee, falaise), aucun
                //    bloc n'etait converti et l'event paraissait mort.
                //    On ancre desormais sur la surface REELLE de chaque colonne,
                //    ce qui donne enfin un sens au parametre yRange, jusqu'ici
                //    recu mais JAMAIS utilise dans le corps de la methode.
                int surf = columnSurfaceY(level, colX, colZ, cy);
                // ⚠️ columnSurfaceY rend le sommet des FEUILLES sur une colonne
                //    boisee. En mode couverture totale la fenetre doit donc etre
                //    assez haute pour traverser tout l'arbre (logs et leaves sont
                //    sautes par les "continue" plus bas) et atteindre le vrai sol,
                //    sinon le sol SOUS LES ARBRES resterait en herbe.
                int depth = fullCoverage ? Math.max(40, layers + 2)
                                         : Math.max(Math.max(2, yRange), layers + 2);
                for (int sy = surf + 2; sy >= surf - depth && done < layers; sy--) {
                    BlockState s = level.getBlockState(mut.set(colX, sy, colZ));
                    if (s.isAir()) continue;
                    // FIX : un fluide (eau/lave) ne doit pas stopper la recherche du sol.
                    // On traverse l'eau/la lave sans la toucher (elle reste en place) pour
                    // trouver et convertir le vrai lit solide en dessous (fond de mare/lac).
                    // Avant ce fix : "break" ici laissait le sol sous l'eau intact, ce qui,
                    // combine a d'autres etapes qui suppriment ensuite ce sol, creait un
                    // trou beant sous les points d'eau au lieu d'un simple assechement visuel.
                    if (!s.getFluidState().isEmpty()) continue;
                    if (s.is(Blocks.BEDROCK) || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_PORTAL_FRAME) || s.is(Blocks.COMMAND_BLOCK) || s.is(Blocks.STRUCTURE_BLOCK) || s.is(Blocks.JIGSAW) || s.is(Blocks.SPAWNER)) break;
                    if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES)) continue; // arbres : on descend au vrai sol
                    if (s.blocksMotion()) {
                        level.setBlock(mut, replacements[random.nextInt(replacements.length)], 3);
                        done++;
                    }
                    // sinon (plante, neige, tapis) : on descend chercher le vrai sol solide
                }
            }
        }
    }

    // Renvoie le Y du bloc solide de surface pour une colonne, -1 sinon (saute l'eau/lave).
    private static int findSurfaceSolidY(ServerLevel level, int cx, int cz, int topStart) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight();
        int start = Math.min(topStart, maxY);
        for (int sy = start; sy >= minY; sy--) {
            BlockState s = level.getBlockState(mut.set(cx, sy, cz));
            if (s.isAir()) continue;
            if (!s.getFluidState().isEmpty()) return -1;
            if (s.blocksMotion()) return sy;
        }
        return -1;
    }

    // Teleporte le joueur a un endroit SUR (sol solide + 2 blocs d'air) dans un rayon borne, sans charger de chunks lointains.
    private static boolean safePlayerTeleport(ServerLevel level, ServerPlayer sp, double cx, double cz, double range, RandomSource random) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int topY = level.getMaxBuildHeight() - 2;
        int minBuild = level.getMinBuildHeight();
        for (int attempt = 0; attempt < 24; attempt++) {
            int ix = (int) Math.floor(cx + (random.nextDouble() - 0.5) * range * 2.0);
            int iz = (int) Math.floor(cz + (random.nextDouble() - 0.5) * range * 2.0);
            for (int sy = topY; sy > minBuild; sy--) {
                BlockState below = level.getBlockState(mut.set(ix, sy - 1, iz));
                if (!below.blocksMotion() || !below.getFluidState().isEmpty()) continue;
                BlockState feet = level.getBlockState(mut.set(ix, sy, iz));
                BlockState head = level.getBlockState(mut.set(ix, sy + 1, iz));
                if (feet.isAir() && head.isAir()) {
                    sp.teleportTo(level, ix + 0.5, sy, iz + 0.5, sp.getYRot(), sp.getXRot());
                    return true;
                }
            }
        }
        return false;
    }

    // Pool de "vrais blocs pleins" (cubes opaques) y compris moddes : exclut portails, carpet, fences, dalles, escaliers, verre, etc.
    private static volatile Block[] SNAKE_BLOCKS = null;
    private static Block[] getSnakeBlocks(ServerLevel level) {
        Block[] cached = SNAKE_BLOCKS;
        if (cached != null) return cached;
        List<Block> out = new ArrayList<>();
        BlockPos probe = BlockPos.ZERO;
        for (Block b : BuiltInRegistries.BLOCK) {
            try {
                BlockState s = b.defaultBlockState();
                if (s.isAir()) continue;
                if (!s.getFluidState().isEmpty()) continue;                 // pas de liquides
                if (b instanceof net.minecraft.world.level.block.FallingBlock) continue; // pas de gravite (sand, gravel, concrete powder, moddes)
                if (b instanceof net.minecraft.world.level.block.EntityBlock) continue;  // pas d'interactifs a BE : coffres, fours, dispensers, barils, shulker, beacon, jukebox, moddes...
                if (b == Blocks.CRAFTING_TABLE || b == Blocks.CARTOGRAPHY_TABLE || b == Blocks.LOOM || b == Blocks.SMITHING_TABLE || b == Blocks.STONECUTTER || b == Blocks.NOTE_BLOCK) continue; // tables/menus sans BE
                // cubes pleins (collision pleine) : INCLUT verre et verres colores, EXCLUT vitres, dalles, escaliers, murets, fences
                if (!Block.isShapeFullBlock(s.getCollisionShape(level, probe))) continue;
                out.add(b);
            } catch (Throwable ignored) {}
        }
        if (out.isEmpty()) {
            out.add(Blocks.STONE); out.add(Blocks.COBBLESTONE); out.add(Blocks.OAK_PLANKS);
            out.add(Blocks.GRANITE); out.add(Blocks.SANDSTONE);
        }
        Block[] arr = out.toArray(new Block[0]);
        SNAKE_BLOCKS = arr;
        return arr;
    }

    // Fait pivoter un angle (radians) vers une cible, par pas max, en prenant le chemin le plus court.
    private static double turnToward(double current, double target, double maxStep) {
        double diff = target - current;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        return current + Mth.clamp(diff, -maxStep, maxStep);
    }

    // Serpent qui se tortille violemment : change de cap souvent, plonge dans le sol puis remonte,
    // va a droite/gauche. Chaque generation : palette, taille et comportement differents.
    private static void drawBlockSnake(ServerLevel level, double ox, double oy, double oz, RandomSource random, int baseSteps) {
        Block[] pool = getSnakeBlocks(level);
        // repartition du rayon : 50% r1, 20% r2, 15% r3, 10% r4, 5% r5
        double rr = random.nextDouble();
        double size = rr < 0.50 ? 1.0 : rr < 0.70 ? 2.0 : rr < 0.85 ? 3.0 : rr < 0.95 ? 4.0 : 5.0;
        boolean hollow = size >= 2.0;            // tube creux pour radius >= 2
        boolean singleMat = size <= 2.0;          // materiau unique pour radius <= 2 (pas de melange)
        double speed = hollow ? (0.45 + random.nextDouble() * 0.2) : (0.6 + random.nextDouble() * 0.3);
        int steps = (size <= 1.0) ? ((int) (baseSteps * 2.5) + random.nextInt(baseSteps)) : (50 + random.nextInt(50));
        Block singleMatBlock = singleMat ? pool[random.nextInt(pool.length)] : null;
        Block[] palette = null;
        if (!singleMat) {
            int palSize = 3 + random.nextInt(4);
            palette = new Block[palSize];
            for (int i = 0; i < palSize; i++) palette[i] = pool[random.nextInt(pool.length)];
        }

        // depart : colonne aleatoire du MEME CHUNK que le joueur, sur le sol solide
        int pcx = (int) Math.floor(ox) >> 4;
        int pcz = (int) Math.floor(oz) >> 4;
        int sx = (pcx << 4) + random.nextInt(16);
        int sz = (pcz << 4) + random.nextInt(16);
        int surfY = findSurfaceSolidY(level, sx, sz, level.getMaxBuildHeight() - 2);
        if (surfY < 0) { sx = (int) Math.floor(ox); sz = (int) Math.floor(oz); surfY = findSurfaceSolidY(level, sx, sz, (int) Math.floor(oy) + 4); }
        if (surfY < 0) surfY = (int) Math.floor(oy);
        double curX = sx + 0.5, curY = surfY + 1.0, curZ = sz + 0.5;

        // demarre vers le +Y puis part en vrille (se deplace toujours en X et Z aussi)
        double yaw = random.nextDouble() * Math.PI * 2;
        double pitch = 0.9 + random.nextDouble() * 0.4;             // depart vers le HAUT
        double targetYaw = yaw, targetPitch = 0.8 + random.nextDouble() * 0.5; // premier cap vers le haut
        int turnsLeft = 8 + random.nextInt(8);                     // monte un peu avant de vriller
        int nextZombie = 15 + random.nextInt(35);
        int rad = (int) Math.ceil(size);
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 2, maxY = level.getMaxBuildHeight() - 2;
        double sizeSq = size * size;
        for (int i = 0; i < steps; i++) {
            // change de cap SOUVENT => boucles en X/Z, plonge puis remonte, jamais longtemps tout droit
            if (turnsLeft <= 0) {
                targetYaw = random.nextDouble() * Math.PI * 2;
                targetPitch = (random.nextDouble() - 0.5) * 2.4;     // -1.2 a +1.2 : plonge OU remonte (equilibre)
                turnsLeft = 4 + random.nextInt(10);                 // nouveau cap toutes les 4-13 etapes
            }
            turnsLeft--;
            yaw = turnToward(yaw, targetYaw, 0.5);                   // virages serres => vraies boucles
            pitch += (targetPitch - pitch) * 0.25;                   // PAS de poussee vers le haut
            double cp = Math.cos(pitch);
            double newX = curX + cp * Math.cos(yaw) * speed;
            double newY = curY + Math.sin(pitch) * speed;
            double newZ = curZ + cp * Math.sin(yaw) * speed;
            int bx = Mth.floor(newX), by = Mth.floor(newY), bz = Mth.floor(newZ);
            for (int x = -rad; x <= rad; x++)
                for (int y = -rad; y <= rad; y++)
                    for (int z = -rad; z <= rad; z++) {
                        if (by + y < minY || by + y > maxY) continue;
                        double ddx = bx + x + 0.5 - newX, ddy = by + y + 0.5 - newY, ddz = bz + z + 0.5 - newZ;
                        if (ddx * ddx + ddy * ddy + ddz * ddz > sizeSq) continue;        // hors de la sphere
                        if (hollow) {                               // tube creux : on ne peint que la coquille
                            double odx = bx + x + 0.5 - curX, ody = by + y + 0.5 - curY, odz = bz + z + 0.5 - curZ;
                            if (odx * odx + ody * ody + odz * odz <= sizeSq) continue;
                        }
                        BlockState s = level.getBlockState(mut.set(bx + x, by + y, bz + z));
                        if (!s.is(Blocks.BEDROCK) && !s.is(Blocks.NETHER_PORTAL) && !s.is(Blocks.END_PORTAL) && !s.is(Blocks.END_PORTAL_FRAME) && !s.is(Blocks.SPAWNER) && !s.is(Blocks.COMMAND_BLOCK) && !s.is(Blocks.STRUCTURE_BLOCK) && !s.is(Blocks.JIGSAW))
                            level.setBlock(mut, (singleMat ? singleMatBlock : palette[random.nextInt(palette.length)]).defaultBlockState(), 2);
                    }
            curX = newX; curY = newY; curZ = newZ;
            if (curY < minY) { curY = minY + 1; targetPitch = Math.abs(targetPitch); pitch = Math.abs(pitch); }
            if (curY > maxY) { curY = maxY - 1; targetPitch = -Math.abs(targetPitch); pitch = -Math.abs(pitch); }
            // de temps en temps : un zombie equipe (luck 0-50) pose sur le dos du serpent
            if (--nextZombie <= 0) {
                nextZombie = 20 + random.nextInt(40);
                int zx = Mth.floor(curX), zy = Mth.floor(curY), zz = Mth.floor(curZ);
                BlockPos f = new BlockPos(zx, zy + 1, zz), h = new BlockPos(zx, zy + 2, zz);
                if (level.getBlockState(f).blocksMotion()) level.setBlock(f, Blocks.AIR.defaultBlockState(), 2);
                if (level.getBlockState(h).blocksMotion()) level.setBlock(h, Blocks.AIR.defaultBlockState(), 2);
                Zombie zomb = new Zombie(EntityType.ZOMBIE, level);
                zomb.moveTo(zx + 0.5, zy + 1, zz + 0.5, random.nextFloat() * 360, 0);
                zomb.setPersistenceRequired();
                equipTunnelMob(zomb, level, random);
                level.addFreshEntity(zomb);
            }
        }
    }

    private static BlockPos findMobInvasionSpawn(ServerLevel level, double x, double y, double z,
                                                  RandomSource random, double preferredAngle) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = preferredAngle + (random.nextDouble() - 0.5) * 0.9;
            double distance = 4.0 + random.nextDouble() * 6.0;
            int sx = Mth.floor(x + Math.cos(angle) * distance);
            int sz = Mth.floor(z + Math.sin(angle) * distance);
            int groundY = findSurfaceSolidY(level, sx, sz,
                    Math.min(level.getMaxBuildHeight() - 2, Mth.floor(y) + 12));
            if (groundY < level.getMinBuildHeight()) continue;
            BlockPos feet = new BlockPos(sx, groundY + 1, sz);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                return feet;
            }
        }
        return BlockPos.containing(x, y + 1, z);
    }

    private static void event_MobInvasion(ServerLevel level, double x, double y, double z,
                                          RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Footsteps gather from every direction...";
        r.titleColorCode = "4";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("SMOKE");
        r.particles.add("ASH");

        final int zombies = 5 + random.nextInt(6);   // 5-10
        final int creepers = random.nextInt(5);      // 0-4
        final int skeletons = random.nextInt(5);     // 0-4
        final int total = zombies + creepers + skeletons;

        r.deferredAction = () -> {
            Player target = level.getNearestPlayer(x, y, z, 32.0, false);
            level.playSound(null, x, y, z, SoundEvents.ZOMBIE_AMBIENT,
                    SoundSource.HOSTILE, 1.8f, 0.65f);
            level.playSound(null, x, y, z, SoundEvents.SKELETON_AMBIENT,
                    SoundSource.HOSTILE, 1.2f, 0.75f);

            int index = 0;
            for (int i = 0; i < zombies; i++, index++) {
                double angle = (Math.PI * 2.0 * index) / Math.max(1, total);
                BlockPos spawn = findMobInvasionSpawn(level, x, y, z, random, angle);
                Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
                zombie.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        random.nextFloat() * 360.0f, 0.0f);
                zombie.setBaby(random.nextDouble() < 0.35); // mini-zombies inclus
                zombie.setPersistenceRequired();
                if (target != null) zombie.setTarget(target);
                level.addFreshEntity(zombie);
            }

            for (int i = 0; i < creepers; i++, index++) {
                double angle = (Math.PI * 2.0 * index) / Math.max(1, total);
                BlockPos spawn = findMobInvasionSpawn(level, x, y, z, random, angle);
                Creeper creeper = new Creeper(EntityType.CREEPER, level);
                creeper.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        random.nextFloat() * 360.0f, 0.0f);
                creeper.setPersistenceRequired();
                setCreeperCharged(creeper, random.nextDouble() < 0.22);
                if (target != null) creeper.setTarget(target);
                level.addFreshEntity(creeper);
            }

            for (int i = 0; i < skeletons; i++, index++) {
                double angle = (Math.PI * 2.0 * index) / Math.max(1, total);
                BlockPos spawn = findMobInvasionSpawn(level, x, y, z, random, angle);
                Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
                skeleton.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        random.nextFloat() * 360.0f, 0.0f);
                skeleton.setPersistenceRequired();
                if (target != null) skeleton.setTarget(target);
                level.addFreshEntity(skeleton);
            }
        };
    }

    private static void event_PB_Mobs(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "They are closing in..."; r.titleColorCode = "c"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        final int count = 20 + random.nextInt(80);
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            for (int i = 0; i < count; i++) {
                EntityType<?> type = switch (random.nextInt(5)) { case 0 -> EntityType.ZOMBIE; case 1 -> EntityType.SKELETON; case 2 -> EntityType.SPIDER; case 3 -> EntityType.CREEPER; default -> EntityType.HUSK; };
                Mob mob = (Mob) type.create(level); if (mob == null) continue;
                mob.moveTo(x + (random.nextDouble()-0.5)*30, y+1, z + (random.nextDouble()-0.5)*30, random.nextFloat()*360, 0);
                mob.setPersistenceRequired();
                if (mob instanceof Creeper creeper) {
                    setCreeperCharged(creeper, random.nextDouble() < 0.18);
                }
                level.addFreshEntity(mob);
            }
            actionBarTranslated(level, x, y, z, "c", "event.deep_lucky_block.pb_mobs.action", count);
        };
    }

    private static void event_PB_MobTowers(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "They climb upon each other..."; r.titleColorCode = "c"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int baseTowers = 2 + random.nextInt(6); // 2-7 avant multiplicateur
            double towerMultiplier = 1.5 + random.nextDouble() * 0.8; // x1.5 a x2.3
            int towers = Math.max(3, (int) Math.round(baseTowers * towerMultiplier)); // 3-16 tours
            for (int t = 0; t < towers; t++) {
                double angle = (Math.PI * 2.0 * t / towers) + (random.nextDouble() - 0.5) * 0.45;
                double dist = 5 + random.nextInt(14);
                int tx = (int)(x + Math.cos(angle)*dist), tz = (int)(z + Math.sin(angle)*dist);
                int groundY = findSurfaceSolidY(level, tx, tz, (int) Math.floor(y) + 8);
                if (groundY < 0) continue;
                // Tailles tres variees : 2-5 petites, 6-10 moyennes, 11-15 geantes.
                int sizeRoll = random.nextInt(100);
                int h = sizeRoll < 35 ? 2 + random.nextInt(4)
                        : sizeRoll < 85 ? 6 + random.nextInt(5)
                        : 11 + random.nextInt(5);
                Mob vehicle = (Mob) (random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON).create(level);
                if (vehicle == null) continue;
                vehicle.moveTo(tx + 0.5, groundY + 1, tz + 0.5, 0, 0);
                vehicle.setPersistenceRequired();
                level.addFreshEntity(vehicle);
                Mob current = vehicle;
                for (int dy = 1; dy < h; dy++) {
                    Mob passenger = (Mob) (random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON).create(level);
                    if (passenger == null) break;
                    passenger.setPersistenceRequired();
                    level.addFreshEntity(passenger);      // d'abord ajouter au monde
                    passenger.startRiding(current, true); // ENSUITE monter (fix "passengers for unknown entity")
                    current = passenger;
                }
            }
            actionBar(level, x, y, z, "7", "\u2620 Stacked mob towers!");
        };
    }

    private static void event_PB_Megaton(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Heavy things plummet..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> {
            int count = 10 + random.nextInt(20);
            for (int i = 0; i < count; i++) {
                int fx = (int)(x + (random.nextDouble()-0.5)*30), fz = (int)(z + (random.nextDouble()-0.5)*30), fy = Math.min(319, (int)y + 30 + random.nextInt(20));
                BlockState heavy = switch (random.nextInt(3)) { case 0 -> Blocks.ANVIL.defaultBlockState(); case 1 -> Blocks.DAMAGED_ANVIL.defaultBlockState(); default -> Blocks.IRON_BLOCK.defaultBlockState(); };
                FallingBlockEntity.fall(level, new BlockPos(fx, fy, fz), heavy);
            }
            actionBar(level, x, y, z, "8", "\u26a0 Heavy mass rain!");
        };
    }

    private static void event_PB_BlockGrave(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Stone rains from above..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> {
            int count = 30 + random.nextInt(70);
            BlockState[] safe = {Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
            for (int i = 0; i < count; i++) {
                int fx = (int)(x + (random.nextDouble()-0.5)*40), fz = (int)(z + (random.nextDouble()-0.5)*40), fy = Math.min(319, (int)y + 30 + random.nextInt(20));
                FallingBlockEntity.fall(level, new BlockPos(fx, fy, fz), safe[random.nextInt(safe.length)]);
            }
            actionBar(level, x, y, z, "8", "\u26a0 Block grave!");
        };
    }

    private static void event_PB_BlockShower(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Debris showers down..."; r.titleColorCode = "7"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("CLOUD");
        r.deferredAction = () -> {
            int count = 30 + random.nextInt(70);
            BlockState[] safe = {Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
            for (int i = 0; i < count; i++) {
                int fx = (int)(x + (random.nextDouble()-0.5)*50), fz = (int)(z + (random.nextDouble()-0.5)*50), fy = Math.min(319, (int)y + 25 + random.nextInt(15));
                FallingBlockEntity.fall(level, new BlockPos(fx, fy, fz), safe[random.nextInt(safe.length)]);
            }
            actionBar(level, x, y, z, "7", "\u26a0 Block shower!");
        };
    }

    private static void event_PB_Transform(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The ground shifts its skin..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("DRAGON_BREATH");
        BlockState[] to = {Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.GRANITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState()};
        r.deferredAction = () -> { playBadSound(level, x, y, z); convertTerrainSafe(level, x, y, z, 10 + random.nextInt(20), 4, random, to); actionBar(level, x, y, z, "5", "\u26a0 The ground shifts!"); };
    }

    private static void event_PB_Replace(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The earth is rewritten..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        BlockState[] to = {Blocks.OBSIDIAN.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
        r.deferredAction = () -> { playBadSound(level, x, y, z); convertTerrainSafe(level, x, y, z, 15 + random.nextInt(30), 5, random, to); actionBar(level, x, y, z, "5", "\u26a0 Blocks replaced!"); };
    }

    private static boolean isCompletelyDry(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).getFluidState().isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            if (!level.getBlockState(pos.relative(direction)).getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static BlockState dryCoralState(Block block) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        return state;
    }

    private static void event_PB_Dryness(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The land grows parched...";
        r.titleColorCode = "6";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("CLOUD");
        r.deferredAction = () -> {
            playBadSound(level, x, y, z);
            int rad = 18, cx = Mth.floor(x), cy = Mth.floor(y), cz = Mth.floor(z);
            BlockState[] dirtVariants = {
                    Blocks.DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(),
                    Blocks.ROOTED_DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState()
            };
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

            // 1) Asseche d'abord TOUTES les sources d'eau/lave et tue la vegetation.
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz > rad * rad) continue;
                    for (int dy = -6; dy <= 6; dy++) {
                        BlockState state = level.getBlockState(mut.set(cx + dx, cy + dy, cz + dz));
                        if (!state.getFluidState().isEmpty()) {
                            level.setBlock(mut, Blocks.AIR.defaultBlockState(), 3);
                        } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM)
                                || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK)
                                || state.is(Blocks.MUD)) {
                            level.setBlock(mut, dirtVariants[random.nextInt(dirtVariants.length)], 3);
                        } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.TALL_GRASS)
                                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                                || state.is(net.minecraft.tags.BlockTags.FLOWERS)) {
                            level.setBlock(mut, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }

            // 2) Seulement APRES l'assechement, pose les coraux morts sur terre ferme.
            // Aucun coral n'est place dans l'eau et WATERLOGGED est force a false.
            Block[] driedCorals = {
                    Blocks.DEAD_BRAIN_CORAL_BLOCK, Blocks.DEAD_BUBBLE_CORAL_BLOCK,
                    Blocks.DEAD_FIRE_CORAL_BLOCK, Blocks.DEAD_HORN_CORAL_BLOCK,
                    Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.DEAD_BRAIN_CORAL,
                    Blocks.DEAD_BUBBLE_CORAL, Blocks.DEAD_FIRE_CORAL,
                    Blocks.DEAD_HORN_CORAL, Blocks.DEAD_TUBE_CORAL,
                    Blocks.DEAD_BRAIN_CORAL_FAN, Blocks.DEAD_BUBBLE_CORAL_FAN,
                    Blocks.DEAD_FIRE_CORAL_FAN, Blocks.DEAD_HORN_CORAL_FAN,
                    Blocks.DEAD_TUBE_CORAL_FAN
            };

            int coralPatches = 24 + random.nextInt(25); // 24-48 zones seches
            for (int patch = 0; patch < coralPatches; patch++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double distance = 2.0 + random.nextDouble() * (rad - 2.0);
                int px = cx + Mth.floor(Math.cos(angle) * distance);
                int pz = cz + Mth.floor(Math.sin(angle) * distance);
                int groundY = findSurfaceSolidY(level, px, pz, cy + 8);
                if (groundY < level.getMinBuildHeight()) continue;

                int cluster = 1 + random.nextInt(4);
                for (int i = 0; i < cluster; i++) {
                    int ox = random.nextInt(3) - 1;
                    int oz = random.nextInt(3) - 1;
                    BlockPos ground = new BlockPos(px + ox, groundY, pz + oz);
                    BlockPos coralPos = ground.above();
                    BlockState support = level.getBlockState(ground);
                    if (!support.blocksMotion() || !support.getFluidState().isEmpty()) continue;
                    if (!level.getBlockState(coralPos).isAir()) continue;
                    if (!isCompletelyDry(level, coralPos)) continue;

                    Block coral = driedCorals[random.nextInt(driedCorals.length)];
                    level.setBlock(coralPos, dryCoralState(coral), 2);
                }
            }

            actionBar(level, x, y, z, "6", "\u26a0 Drought: the land is dying!");
        };
    }

    private static void event_PB_TntSplosion(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Fuses spark all around..."; r.titleColorCode = "4"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("EXPLOSION");
        r.deferredAction = () -> {
            int count = 20 + random.nextInt(80);
            for (int i = 0; i < count; i++) {
                PrimedTnt tnt = EntityType.TNT.create(level); if (tnt == null) continue;
                tnt.moveTo(x + (random.nextDouble()-0.5)*40, y + 1, z + (random.nextDouble()-0.5)*40); tnt.setFuse(20 + random.nextInt(80)); level.addFreshEntity(tnt);
            }
            actionBarTranslated(level, x, y, z, "c", "event.deep_lucky_block.pb_tnt.action", count);
        };
    }

    private static void event_PB_HeightNoise(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The ground heaves upward..."; r.titleColorCode = "6"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int rad = 8 + random.nextInt(8), cx = (int)x, cy = (int)y, cz = (int)z;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx*dx + dz*dz > rad*rad) continue; int lift = -16 + random.nextInt(33);
                for (int dy = 0; dy < 32; dy++) {
                    BlockPos from = new BlockPos(cx+dx, cy+dy-16, cz+dz); BlockPos to = new BlockPos(cx+dx, cy+dy+lift-16, cz+dz);
                    BlockState s = level.getBlockState(from); if (s.isAir()) continue;
                    level.setBlock(from, Blocks.AIR.defaultBlockState(), 3); level.setBlock(to, s, 3);
                }
            }
            ensureAirAtPlayer(level, x, y, z); actionBar(level, x, y, z, "6", "\u26a0 Earthquake!");
        };
    }

    private static void event_PB_MadGeometry(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Strange shapes bloom..."; r.titleColorCode = "rainbow"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("DRAGON_BREATH");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            BlockState[] shapes = {Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.POLISHED_GRANITE.defaultBlockState(), Blocks.BRICKS.defaultBlockState()};
            int count = 3 + random.nextInt(7), rad = 40 + random.nextInt(110);
            for (int s = 0; s < count; s++) {
                int sx = (int)(x + (random.nextDouble()-0.5)*rad), sz = (int)(z + (random.nextDouble()-0.5)*rad), sy = (int)y + random.nextInt(10), size = 3 + random.nextInt(7);
                BlockState mat = shapes[random.nextInt(shapes.length)];
                for (int dx = -size; dx <= size; dx++) for (int dy = 0; dy < size*2; dy++) for (int dz = -size; dz <= size; dz++) if (dx*dx + dy*dy + dz*dz <= size*size) level.setBlock(new BlockPos(sx+dx, sy+dy, sz+dz), mat, 3);
            }
            actionBar(level, x, y, z, "5", "\u26a0 Mad geometry!");
        };
    }

    private static void event_PB_MadderGeometry(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Madness takes solid form..."; r.titleColorCode = "rainbow"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("DRAGON_BREATH");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            BlockState[] shapes = {Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState()};
            int count = 10 + random.nextInt(70), rad = 80;
            for (int s = 0; s < count; s++) {
                int sx = (int)(x + (random.nextDouble()-0.5)*rad), sz = (int)(z + (random.nextDouble()-0.5)*rad), sy = 60 + random.nextInt(80), size = 1 + random.nextInt(5);
                BlockState mat = shapes[random.nextInt(shapes.length)];
                for (int dx = -size; dx <= size; dx++) for (int dy = 0; dy < size; dy++) for (int dz = -size; dz <= size; dz++) level.setBlock(new BlockPos(sx+dx, sy+dy, sz+dz), mat, 3);
            }
            actionBar(level, x, y, z, "5", "\u26a0 Demented geometry!");
        };
    }

    private static void event_PB_Classic(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Odd towers sprout up..."; r.titleColorCode = "a"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("HAPPY_VILLAGER");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            // [base, plante], toutes stables : bamboo sur dirt, cactus sur sable (voisins degages), sinon blocs pleins
            BlockState[][] defs = {
                { Blocks.DIRT.defaultBlockState(), Blocks.BAMBOO.defaultBlockState() },
                { Blocks.SAND.defaultBlockState(), Blocks.CACTUS.defaultBlockState() },
                { Blocks.DIRT.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState() },
                { Blocks.DIRT.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState() },
                { Blocks.DIRT.defaultBlockState(), Blocks.MELON.defaultBlockState() },
                { Blocks.DIRT.defaultBlockState(), Blocks.CARVED_PUMPKIN.defaultBlockState() }
            };
            int towers = 3 + random.nextInt(8);
            for (int t = 0; t < towers; t++) {
                int tx = (int)(x + (random.nextDouble()-0.5)*36), tz = (int)(z + (random.nextDouble()-0.5)*36);
                int groundY = findSurfaceSolidY(level, tx, tz, (int) Math.floor(y) + 3);
                if (groundY < 0) continue;
                BlockState[] def = defs[random.nextInt(defs.length)];
                boolean cactus = def[1].is(Blocks.CACTUS);
                level.setBlock(new BlockPos(tx, groundY, tz), def[0], 3); // base solide
                int h = 3 + random.nextInt(13);
                for (int dy = 1; dy <= h; dy++) {
                    BlockPos p = new BlockPos(tx, groundY + dy, tz);
                    level.setBlock(p, def[1], cactus ? 2 : 3);
                    if (cactus) for (int s = 0; s < 4; s++) { // degager les voisins horizontaux pour le cactus
                        BlockPos n = p.relative(Direction.from2DDataValue(s));
                        if (level.getBlockState(n).blocksMotion()) level.setBlock(n, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            actionBar(level, x, y, z, "a", " Creative towers!");
        };
    }

    private static void event_PB_Lightning(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The storm tears open..."; r.titleColorCode = "e"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("END_ROD");
        r.deferredAction = () -> { level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0f, 0.8f);
            int count = 40 + random.nextInt(160);
            for (int i = 0; i < count; i++) { LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level); bolt.moveTo(x + (random.nextDouble()-0.5)*40, y, z + (random.nextDouble()-0.5)*40, 0, 0); level.addFreshEntity(bolt); }
            actionBarTranslated(level, x, y, z, "e", "event.deep_lucky_block.pb_lightning.action", count);
        };
    }

    private static void event_PB_SandForDessert(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Dust chokes the air..."; r.titleColorCode = "e"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("CLOUD");
        BlockState[] to = {Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.RED_SAND.defaultBlockState()};
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            convertTerrainSafe(level, x, y, z, 14 + random.nextInt(18), 5, random, to);
            level.setDayTime(18000L); // la nuit tombe dans le desert
            int cx = (int) x, cy = (int) y, cz = (int) z, radius = 18;
            // cactus 1-3 sur sable (dispersion circulaire)
            int cacti = 5 + random.nextInt(8);
            for (int i = 0; i < cacti; i++) {
                double ang = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * radius;
                int fx = cx + (int) Math.round(Math.cos(ang) * d), fz = cz + (int) Math.round(Math.sin(ang) * d);
                int gy = findSurfaceSolidY(level, fx, fz, cy + 3);
                if (gy < 0) continue;
                if (!level.getBlockState(new BlockPos(fx, gy, fz)).is(net.minecraft.tags.BlockTags.SAND)) level.setBlock(new BlockPos(fx, gy, fz), Blocks.SAND.defaultBlockState(), 3);
                int h = 1 + random.nextInt(3); // 1-3 cactus
                for (int dy = 1; dy <= h; dy++) {
                    BlockPos p = new BlockPos(fx, gy + dy, fz);
                    level.setBlock(p, Blocks.CACTUS.defaultBlockState(), 2); // flag 2 = pas d'update (evite la casse)
                    for (int s = 0; s < 4; s++) { // degager les 4 voisins horizontaux
                        Direction sd = Direction.from2DDataValue(s);
                        BlockPos n = p.relative(sd);
                        if (level.getBlockState(n).blocksMotion()) level.setBlock(n, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            // husks (zombies desertiques) en dispersion circulaire
            int husks = 3 + random.nextInt(5);
            for (int i = 0; i < husks; i++) {
                Mob h = (Mob) EntityType.HUSK.create(level);
                if (h == null) continue;
                double ang = random.nextDouble() * Math.PI * 2, d = 4 + random.nextDouble() * (radius - 4);
                h.moveTo(cx + Math.cos(ang) * d, cy + 1, cz + Math.sin(ang) * d, random.nextFloat() * 360, 0);
                h.setPersistenceRequired();
                level.addFreshEntity(h);
            }
            // Brule le joueur tant qu'il est dans la zone desert (2s pendant 20s)
            Player desertPlayer = level.getNearestPlayer(x, y, z, 64, false);
            if (desertPlayer instanceof ServerPlayer sp3) {
                for (int t = 0; t < 10; t++) {
                    final int delay = t * 40;
                    TestProcedure.schedule(level, TestProcedure.currentTick(level) + delay, () -> {
                        if (sp3.isAlive() && sp3.level().dimensionType().ultraWarm()) sp3.setRemainingFireTicks((2) * 20);
                        else if (sp3.isAlive()) sp3.setRemainingFireTicks((3) * 20);
                    });
                }
            }
            actionBar(level, x, y, z, "e", "The heat is unbearable!");
        };
    }

    private static void event_PB_InTheEnd(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Reality frays at the edges..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("PORTAL");
        // ⛔ T230 — l'OBSIDIENNE est RETIREE de la palette de SOL.
        //    Retour en jeu : « ca pose des blocs d'obsi en mode faux pilliers de
        //    maniere random environ tous les 5-6 blocs ». C'etait exactement ca :
        //    la palette contenait 4x END_STONE + 1x OBSIDIAN, donc convertTerrainSafe
        //    transformait 1 bloc de sol sur 5 en obsidienne, au hasard.
        //    L'obsidienne ne doit venir QUE des troncs d'arbres (etape 2).
        BlockState[] to = {Blocks.END_STONE.defaultBlockState()};
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int rad = 24 + random.nextInt(18); // +10 blocs vs les autres
            int cx = (int) x, cy = (int) y, cz = (int) z;
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            // 1) transforme le sol en end stone (convertTerrainSafe saute les logs/leaves => trouve le vrai sol sous les arbres)
            // T235 : couverture TOTALE du disque, 4 couches de profondeur.
            //        Plus aucune colonne sautee, plus aucun degrade sur les bords :
            //        tout le sol de la zone devient de l'end stone, et l'obsidienne
            //        ne provient QUE des troncs (etape 2).
            convertTerrainSafe(level, x, y, z, rad, 5, random, to, true, 4);
            // 2) remplace le WOOD (logs) des arbres par OBSIDIANNE, supprime les leaves, et memorise le sommet d'obsidienne par colonne
            final int noneY = Integer.MIN_VALUE;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad) continue;
                int topY = noneY;
                // T230 : on part de la surface REELLE de cette colonne, pas du Y du joueur.
                int surf = columnSurfaceY(level, cx + dx, cz + dz, cy);
                for (int dy = -36; dy <= 4; dy++) {
                    BlockState s = level.getBlockState(mut.set(cx + dx, surf + dy, cz + dz));
                    if (s.is(net.minecraft.tags.BlockTags.LOGS)) {
                        level.setBlock(mut, Blocks.OBSIDIAN.defaultBlockState(), 3);
                        topY = surf + dy; // dy croissant => reste le sommet
                    } else if (s.is(net.minecraft.tags.BlockTags.LEAVES)) {
                        level.setBlock(mut, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                // 3) 10% de chance : crystal de l'End sur le sommet d'obsidienne de cette colonne
                if (topY != noneY && random.nextDouble() < 0.10) {
                    BlockPos top = new BlockPos(cx + dx, topY, cz + dz);
                    if (level.getBlockState(top.above()).isAir()) {
                        Entity ec = EntityType.END_CRYSTAL.create(level);
                        if (ec != null) { ec.moveTo(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5, 0, 0); level.addFreshEntity(ec); }
                    }
                }
            }
            // 0-2 End Knights dans la zone
            int knights = random.nextInt(3);
            for (int i = 0; i < knights; i++) {
                double ang = random.nextDouble() * Math.PI * 2, d = 4 + random.nextDouble() * (rad - 4);
                double sx = cx + Math.cos(ang) * d, sz = cz + Math.sin(ang) * d;
                try {
                    EndKnightEntity knight = new EndKnightEntity(DeepLuckyBlockModEntities.END_KNIGHT.get(), level);
                    knight.moveTo(sx, cy + 1, sz, random.nextFloat() * 360, 0);
                    knight.setPersistenceRequired();
                    level.addFreshEntity(knight);
                } catch (Exception ignored) {}
            }
            // 2-6 endermen dans la zone
            int endermen = 2 * (2 + random.nextInt(5)); // x2
            Player nearestEnd = level.getNearestPlayer(x, y, z, 64, false);
            for (int i = 0; i < endermen; i++) {
                Mob e = (Mob) EntityType.ENDERMAN.create(level);
                if (e == null) continue;
                double ang = random.nextDouble() * Math.PI * 2, d = 3 + random.nextDouble() * (rad - 3);
                e.moveTo(cx + Math.cos(ang) * d, cy + 1, cz + Math.sin(ang) * d, random.nextFloat() * 360, 0);
                e.setPersistenceRequired();
                if ((i & 1) == 0 && nearestEnd != null) { // la moitié enervée contre le joueur
                    e.setTarget(nearestEnd);
                    if (e instanceof net.minecraft.world.entity.NeutralMob nm) { nm.setPersistentAngerTarget(nearestEnd.getUUID()); nm.setRemainingPersistentAngerTime(6000); }
                }
                level.addFreshEntity(e);
            }
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.9f);
            actionBar(level, x, y, z, "5", "The land turns into the End...");
        };
    }

    private static void event_PB_HellOnEarth(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Heat wells up from below..."; r.titleColorCode = "4"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("FLAME");
        BlockState[] to = {Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState()};
        BlockState[] mound = {Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState()};
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int rad = 14 + random.nextInt(18);
            int cx = (int) x, cy = (int) y, cz = (int) z;
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            // 1) retire d'abord les arbres (logs + leaves) dans le rayon
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad) continue;
                // T230 : scan relatif a la surface reelle de la colonne.
                int surfH = columnSurfaceY(level, cx + dx, cz + dz, cy);
                for (int dy = -36; dy <= 4; dy++) {
                    BlockState s = level.getBlockState(mut.set(cx + dx, surfH + dy, cz + dz));
                    if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES))
                        level.setBlock(mut, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            // 2) transforme le sol (le sol sous les anciens arbres est maintenant expose)
            convertTerrainSafe(level, x, y, z, rad, 5, random, to);
            // 3) comble l'air au-dessus du sol => forme une montagne nether lisse (dome)
            int domeR = Math.min(rad, 16);
            int peakH = 5 + random.nextInt(4); // 5-8
            for (int dx = -domeR; dx <= domeR; dx++) for (int dz = -domeR; dz <= domeR; dz++) {
                double d = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (d > domeR) continue;
                int fillH = (int) Math.round((1.0 - d / domeR) * peakH); // 0 au bord, max au centre
                if (fillH <= 0) continue;
                int gy = findSurfaceSolidY(level, cx + dx, cz + dz, cy + 3);
                if (gy < 0) continue;
                for (int h = 1; h <= fillH; h++) {
                    BlockState s = level.getBlockState(mut.set(cx + dx, gy + h, cz + dz));
                    if (s.isAir() || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES) || !s.getFluidState().isEmpty())
                        level.setBlock(mut, mound[random.nextInt(mound.length)], 3);
                }
            }
            ensureAirAtPlayer(level, x, y, z); // degage le joueur (le dome peut l'entourer)
            // petites zones de glowstone rares
            int glows = 2 + random.nextInt(3);
            for (int i = 0; i < glows; i++) {
                double ang = random.nextDouble() * Math.PI * 2, dd = random.nextDouble() * domeR;
                int fx = cx + (int) Math.round(Math.cos(ang) * dd), fz = cz + (int) Math.round(Math.sin(ang) * dd);
                int gy = findSurfaceSolidY(level, fx, fz, cy + 3);
                if (gy < 0) continue;
                level.setBlock(new BlockPos(fx, gy + 1, fz), Blocks.GLOWSTONE.defaultBlockState(), 3);
                if (random.nextBoolean()) level.setBlock(new BlockPos(fx + 1, gy + 1, fz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
            // petites structures en bone_block (style fossile)
            int bones = 1 + random.nextInt(2);
            for (int i = 0; i < bones; i++) {
                double ang = random.nextDouble() * Math.PI * 2, dd = 3 + random.nextDouble() * (domeR - 3);
                int fx = cx + (int) Math.round(Math.cos(ang) * dd), fz = cz + (int) Math.round(Math.sin(ang) * dd);
                int gy = findSurfaceSolidY(level, fx, fz, cy + 3);
                if (gy < 0) continue;
                for (int h = 1; h <= 3; h++) level.setBlock(new BlockPos(fx, gy + h, fz), Blocks.BONE_BLOCK.defaultBlockState(), 3);
                level.setBlock(new BlockPos(fx, gy + 2, fz + 1), Blocks.BONE_BLOCK.defaultBlockState(), 3);
                level.setBlock(new BlockPos(fx, gy + 2, fz - 1), Blocks.BONE_BLOCK.defaultBlockState(), 3);
            }
            // flammes / feu d'ames
            for (int i = 0; i < 14; i++) {
                double ang = random.nextDouble() * Math.PI * 2, dd = random.nextDouble() * domeR;
                int fx = cx + (int) Math.round(Math.cos(ang) * dd), fz = cz + (int) Math.round(Math.sin(ang) * dd);
                int gy = findSurfaceSolidY(level, fx, fz, cy + 3);
                if (gy < 0) continue;
                if (level.getBlockState(new BlockPos(fx, gy + 1, fz)).isAir())
                    level.setBlock(new BlockPos(fx, gy + 1, fz), random.nextBoolean() ? Blocks.FIRE.defaultBlockState() : Blocks.SOUL_FIRE.defaultBlockState(), 3);
            }
            level.playSound(null, x, y, z, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.2f, 0.7f);
            // 1 ghast dans le ciel + 1-3 blazes en hauteur (attaquent si le joueur sort par le dessus)
            Player netherPlayer = level.getNearestPlayer(x, y, z, 64, false);
            if (netherPlayer != null) {
                var ghast = EntityType.GHAST.create(level);
                if (ghast != null) { ghast.moveTo(x + (random.nextDouble()-0.5)*20, y + 15 + random.nextInt(10), z + (random.nextDouble()-0.5)*20, random.nextFloat()*360, 0); ghast.setPersistenceRequired(); level.addFreshEntity(ghast); }
                int blazes = 1 + random.nextInt(3);
                for (int i = 0; i < blazes; i++) {
                    var blaze = EntityType.BLAZE.create(level);
                    if (blaze != null) { blaze.moveTo(x + (random.nextDouble()-0.5)*30, y + 8 + random.nextInt(6), z + (random.nextDouble()-0.5)*30, random.nextFloat()*360, 0); blaze.setPersistenceRequired(); level.addFreshEntity(blaze); }
                }
            }
            actionBar(level, x, y, z, "c", "Welcome to Hell...");
        };
    }

    private static void event_PB_Lifeless(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Life drains from the land..."; r.titleColorCode = "8"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("ASH");
        BlockState[] to = {Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState()};
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int rad = 20 + random.nextInt(20);
            int cx = (int) x, cy = (int) y, cz = (int) z;
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            // 1) retire D'ABORD toutes les leaves + logs (arbres) de la zone
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad) continue;
                // T230 : scan relatif a la surface reelle de la colonne.
                int surfL = columnSurfaceY(level, cx + dx, cz + dz, cy);
                for (int dy = -36; dy <= 4; dy++) {
                    BlockState s = level.getBlockState(mut.set(cx + dx, surfL + dy, cz + dz));
                    if (s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)) level.setBlock(mut, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            // 2) transforme le sol en terre morte (le sol sous les anciens arbres, ou sous
            // l'eau des mares/lacs, est maintenant expose et converti par convertTerrainSafe)
            convertTerrainSafe(level, x, y, z, rad, 5, random, to);
            // 3) retire la vegetation restante (herbe haute, fougeres, fleurs, algues sous l'eau)
            // FIX : ne PLUS retirer Blocks.GRASS_BLOCK ici -- c'est le bloc de terre SOLIDE
            // (pas la plante), deja converti en pierre/gravier a l'etape 2. Le supprimer en AIR
            // creusait un veritable trou dans le sol (visible notamment sous les points d'eau,
            // la ou convertTerrainSafe ne posait jamais de remplacement a cause du bug de fluide
            // desormais corrige). On retire par contre bien le kelp/seagrass ("les algues").
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad) continue;
                for (int dy = -3; dy <= 10; dy++) {
                    BlockState s = level.getBlockState(mut.set(cx + dx, cy + dy, cz + dz));
                    if (s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
                            || s.is(net.minecraft.tags.BlockTags.FLOWERS)
                            || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)
                            || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS)) {
                        level.setBlock(mut, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            actionBar(level, x, y, z, "8", "Life has vanished...");
        };
    }

    private static void event_PB_TrappedTribe(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Steel whispers down..."; r.titleColorCode = "c"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("CRIT");
        r.deferredAction = () -> { level.playSound(null, x, y, z, SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 2.0f, 0.8f);
            // FIX T221 : ItemStack.EMPTY causait "Cannot encode empty ItemStack" dans
            //             AbstractArrow.addAdditionalSaveData A CHAQUE sauvegarde de chunk.
            //             Log du 05Oct2026 : 784 exceptions + serveur 88 s (1761 ticks) en retard.
            //             Meme correctif que le FIX v41 deja applique a la SpectralArrow plus haut.
            //             pickup DISALLOWED : la pluie reste une punition, pas un stock de fleches.
            for (int i = 0; i < 200; i++) { net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(level, x + (random.nextDouble()-0.5)*20, y + 15, z + (random.nextDouble()-0.5)*20, new ItemStack(Items.ARROW), null); arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED; arrow.setDeltaMovement((random.nextDouble()-0.5)*0.5, -1.5, (random.nextDouble()-0.5)*0.5); level.addFreshEntity(arrow); }
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(x-20, y-8, z-20, x+20, y+8, z+20))) { m.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1)); m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1)); }
            actionBar(level, x, y, z, "7", "\u26a0 Arrow rain + poison!");
        };
    }

    private static void event_PB_BuffedDown(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Strength flees the area..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SQUID_INK");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(x-25, y-10, z-25, x+25, y+10, z+25))) { m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200 + random.nextInt(400), random.nextInt(3))); m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200 + random.nextInt(400), random.nextInt(3))); m.addEffect(new MobEffectInstance(MobEffects.POISON, 100 + random.nextInt(100), 0)); }
            actionBar(level, x, y, z, "5", "\u26a0 Every nearby being is weakened!");
        };
    }

    private static void event_PB_Crush(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "An unseen weight presses..."; r.titleColorCode = "4"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("SMOKE");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(x-30, y-15, z-30, x+30, y+15, z+30))) m.hurt(level.damageSources().generic(), 20 + random.nextInt(50));
            actionBar(level, x, y, z, "c", "\u26a0 Massive crush!");
        };
    }

    private static void event_PB_Void(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The ground opens beneath..."; r.titleColorCode = "0"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("SQUID_INK");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int rad = 5 + random.nextInt(5), cx = (int)x, cy = (int)y, cz = (int)z;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) { if (dx*dx + dz*dz > rad*rad) continue; for (int dy = 0; dy < 60; dy++) { BlockPos p = new BlockPos(cx+dx, cy-dy, cz+dz); if (level.getBlockState(p).is(Blocks.BEDROCK)) break; level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); } }
            ensureAirAtPlayer(level, x, y, z); actionBar(level, x, y, z, "8", "\u26a0 Holes to the void!");
        };
    }

    private static void event_PB_WorldSnake(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Something coils above..."; r.titleColorCode = "2"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("HAPPY_VILLAGER");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            drawBlockSnake(level, x, y, z, random, 150);
            actionBar(level, x, y, z, "2", " Block snake!");
        };
    }

    private static void event_PB_DoubleSnake(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Twin shapes wind above..."; r.titleColorCode = "2"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("HAPPY_VILLAGER");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            drawBlockSnake(level, x, y, z, random, 120);
            drawBlockSnake(level, x, y, z, random, 120);
            actionBar(level, x, y, z, "2", " Two block snakes!");
        };
    }

    private static void event_PB_TunnelBore(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Something bores below..."; r.titleColorCode = "6"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("SMOKE");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int tunnels = 1 + random.nextInt(2);
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            int minB = level.getMinBuildHeight() + 2, maxB = level.getMaxBuildHeight() - 2;
            for (int t = 0; t < tunnels; t++) {
                double yaw = random.nextDouble() * Math.PI * 2;
                double pitch = -(0.35 + random.nextDouble() * 0.35); // PIQUE VERS LE BAS
                double px = x, py = Math.floor(y) - 0.5, pz = z;     // demarre juste sous la surface
                int rad = 2;
                int len = 16 + random.nextInt(16); // 16-31 (leger => pas de freeze)
                int endX = (int) Math.round(x), endY = (int) Math.floor(y), endZ = (int) Math.round(z);
                for (int i = 0; i < len; i++) {
                    yaw += (random.nextDouble() - 0.5) * 0.25;
                    pitch += (random.nextDouble() - 0.5) * 0.04;
                    pitch = Mth.clamp(pitch, -0.9, 0.2);
                    px += Math.cos(yaw) * Math.cos(pitch);
                    pz += Math.sin(yaw) * Math.cos(pitch);
                    py += Math.sin(pitch);
                    int ix = (int) Math.round(px), iy = (int) Math.round(py), iz = (int) Math.round(pz);
                    if (iy < minB) break;
                    if (iy > maxB) iy = maxB;
                    // creuse avec flag 2 (pas d'update voisin/lumiere) + saute les fluides => evite tout freeze
                    for (int dx = -rad; dx <= rad; dx++) for (int dy = -rad; dy <= rad; dy++) for (int dz = -rad; dz <= rad; dz++) {
                        if (dx * dx + dy * dy + dz * dz > rad * rad) continue;
                        BlockState s = level.getBlockState(mut.set(ix + dx, iy + dy, iz + dz));
                        if (!s.isAir() && !s.is(Blocks.BEDROCK) && !s.is(Blocks.SPAWNER) && s.getFluidState().isEmpty())
                            level.setBlock(mut, Blocks.AIR.defaultBlockState(), 2);
                    }
                    // glowstone au PLAFOND de la cavite, uniquement sous terre (roche au-dessus) : jamais dans le ciel
                    if (i > 4 && i % 6 == 0) {
                        BlockPos ceil = new BlockPos(ix, iy + rad, iz);
                        if (level.getBlockState(ceil).isAir() && !level.getBlockState(ceil.above()).isAir())
                            level.setBlock(ceil, Blocks.GLOWSTONE.defaultBlockState(), 2);
                    }
                    endX = ix; endY = iy; endZ = iz; // memorise le fond du tunnel
                }
                // une dizaine de mobs equipes (luck 0-50, 15% drop) au fond du tunnel, loin du joueur
                int safeY = Math.max(minB + 1, endY);
                int mobCount = 8 + random.nextInt(5);
                for (int m = 0; m < mobCount; m++) {
                    double ang = random.nextDouble() * Math.PI * 2;
                    double dist = 0.5 + random.nextDouble() * 2.0;
                    Zombie zomb = new Zombie(EntityType.ZOMBIE, level);
                    zomb.moveTo(endX + 0.5 + Math.cos(ang) * dist, safeY, endZ + 0.5 + Math.sin(ang) * dist, random.nextFloat() * 360, 0);
                    zomb.setPersistenceRequired();
                    equipTunnelMob(zomb, level, random);
                    level.addFreshEntity(zomb);
                }
            }
            actionBar(level, x, y, z, "6", "\u26a0 Giant tunnel dug!");
        };
    }

    private static void event_PB_CreeperSoul(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The air crackles hot..."; r.titleColorCode = "4"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("EXPLOSION");
        r.deferredAction = () -> {
            int count = 80 + random.nextInt(220);
            for (int i = 0; i < count; i++) level.explode(null, x + (random.nextDouble()-0.5)*40, y + random.nextDouble()*10, z + (random.nextDouble()-0.5)*40, 1.0f + random.nextFloat()*3.0f, Level.ExplosionInteraction.NONE);
            actionBarTranslated(level, x, y, z, "c", "event.deep_lucky_block.pb_creeper_soul.action", count);
        };
    }

    private static void event_PB_Target(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Marks bloom on the ground..."; r.titleColorCode = "e"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("CRIT");
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            int count = 6 + random.nextInt(8);
            for (int i = 0; i < count; i++) {
                int tx = (int)(x + (random.nextDouble()-0.5)*30), tz = (int)(z + (random.nextDouble()-0.5)*30);
                int groundY = findSurfaceSolidY(level, tx, tz, (int) Math.floor(y) + 3);
                if (groundY < 0) continue;
                BlockPos red = new BlockPos(tx, groundY + 1, tz);
                level.setBlock(red, Blocks.RED_CONCRETE.defaultBlockState(), 3);
                level.setBlock(red.above(), Blocks.TARGET.defaultBlockState(), 3);
                Zombie z2 = new Zombie(EntityType.ZOMBIE, level);
                z2.moveTo(tx + 0.5, groundY + 3, tz + 0.5, random.nextFloat() * 360, 0);
                z2.setPersistenceRequired();
                z2.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false, true));
                level.addFreshEntity(z2);
            }
            actionBar(level, x, y, z, "e", "\u26a0 Painted targets + zombies!");
        };
    }

    private static void event_PB_IceAge(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A deep frost settles..."; r.titleColorCode = "b"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("CLOUD");
        BlockState[] to = {Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState()};
        r.deferredAction = () -> { playBadSound(level, x, y, z);
            convertTerrainSafe(level, x, y, z, 14 + random.nextInt(18), 5, random, to);
            int cx = (int) x, cy = (int) y, cz = (int) z, rad = 22;
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            // gele l'eau (y+1 etc devient de la glace), un peu de blue ice par endroits
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    int bx = cx + dx, by = cy + dy, bz = cz + dz;
                    BlockState s = level.getBlockState(mut.set(bx, by, bz));
                    if (s.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) level.setBlock(mut, (random.nextInt(12) == 0 ? Blocks.BLUE_ICE : Blocks.ICE).defaultBlockState(), 3);
                }
            }
            // couche de neige (y+1) au-dessus des surfaces solides gelées
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad) continue;
                int gy = findSurfaceSolidY(level, cx + dx, cz + dz, cy + 3);
                if (gy < 0) continue;
                BlockPos top = new BlockPos(cx + dx, gy + 1, cz + dz);
                if (level.getBlockState(top).isAir()) level.setBlock(top, Blocks.SNOW.defaultBlockState(), 3);
            }
            // Effet de froid : Slowness + Mining Fatigue + degats periodiques pendant 5-30s
            Player icePlayer = level.getNearestPlayer(x, y, z, 64, false);
            if (icePlayer instanceof ServerPlayer sp2) {
                int frostDuration = (5 + random.nextInt(26)) * 20;
                giveEventEffect(sp2, MobEffects.MOVEMENT_SLOWDOWN, frostDuration, 2);
                giveEventEffect(sp2, MobEffects.DIG_SLOWDOWN, frostDuration, 1);
                giveEventEffect(sp2, MobEffects.WEAKNESS, frostDuration, 0);
                int ticks = frostDuration / 40;
                for (int t = 0; t < ticks; t++) {
                    final int delay = t * 40;
                    TestProcedure.schedule(level, TestProcedure.currentTick(level) + delay, () -> {
                        if (sp2.isAlive()) {
                            sp2.hurt(level.damageSources().freeze(), 1.0f);
                            level.sendParticles(ParticleTypes.SNOWFLAKE, sp2.getX(), sp2.getY() + 1, sp2.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
                        }
                    });
                }
            }
            actionBar(level, x, y, z, "b", "\u26a0 Ice age! You're freezing!");
        };
    }

    private static void event_PB_TeleRandom(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Space folds around you..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.CURSED; r.particles.add("PORTAL");
        r.deferredAction = () -> { level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0f, 1.0f);
            // melange court des mobs autour (restent dans les chunks charges, aucun chargement de chunk lointain)
            for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(x - 25, y - 8, z - 25, x + 25, y + 8, z + 25)))
                m.teleportTo(x + (random.nextDouble() - 0.5) * 24, m.getY(), z + (random.nextDouble() - 0.5) * 24);
            actionBar(level, x, y, z, "5", "\u26a0 Random teleportation!");
        };
    }

    private static void event_PB_CrazyPort(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Reality yanks you away..."; r.titleColorCode = "5"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("PORTAL");
        r.deferredAction = () -> {
            level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0f, 1.5f);
            Player nearest = level.getNearestPlayer(x, y, z, 40, false);
            List<Mob> nearby = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(x - 10, y - 4, z - 10, x + 10, y + 4, z + 10)));
            boolean moved = false;
            if (nearest instanceof ServerPlayer sp && sp.isAlive()) {
                double ox = sp.getX(), oz = sp.getZ();
                moved = safePlayerTeleport(level, sp, x, z, 80.0, random);
                if (moved) {
                    double dx = sp.getX() - ox, dz = sp.getZ() - oz;
                    giveEventEffect(sp, MobEffects.CONFUSION, 200, 1);
                    // les mobs autour (rayon 10) suivent le joueur a sa destination
                    for (Mob m : nearby) {
                        if (!m.isAlive()) continue;
                        m.teleportTo(m.getX() + dx, m.getY(), m.getZ() + dz);
                    }
                    actionBar(level, sp.getX(), sp.getY(), sp.getZ(), "5", "\u26a0 Massive teleportation!");
                } else {
                    actionBar(level, x, y, z, "5", "\u26a0 Massive teleportation!");
                }
            } else {
                actionBar(level, x, y, z, "5", "\u26a0 Massive teleportation!");
            }
        };
    }

    private static void event_PB_DangerCall(ServerLevel level, double x, double y, double z, RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Ticking fills the air..."; r.titleColorCode = "4"; r.rank = TestProcedure.ItemRank.BROKEN; r.particles.add("EXPLOSION");
        r.deferredAction = () -> {
            int count = 50 + random.nextInt(120); List<BlockPos> redstoneBlocks = new ArrayList<>();
            for (int i = 0; i < count; i++) { int fx = (int)(x + (random.nextDouble()-0.5)*40), fz = (int)(z + (random.nextDouble()-0.5)*40), fy = Math.min(319, (int)y + 30 + random.nextInt(25)); PrimedTnt tnt = EntityType.TNT.create(level); if (tnt == null) continue; tnt.moveTo(fx + 0.5, fy, fz + 0.5); tnt.setFuse(40 + random.nextInt(60)); level.addFreshEntity(tnt); if (random.nextInt(3) == 0) redstoneBlocks.add(new BlockPos(fx, fy - 1, fz)); }
            TestProcedure.schedule(level, TestProcedure.currentTick(level) + 30, () -> {
                for (BlockPos rp : redstoneBlocks)
                    if (level.getBlockState(rp).isAir() && level.getBlockState(rp.below()).blocksMotion())
                        level.setBlock(rp, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            });
            actionBarTranslated(level, x, y, z, "c", "event.deep_lucky_block.pb_danger_call.action", count);
        };
    }

    // ==================== NOUVEAUX EVENTS V3 ====================

    private static void event_ChargedCreeperExecution(ServerLevel level, double x, double y, double z,
                                                       RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A blue circle begins to hiss...";
        r.titleColorCode = "3";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("ELECTRIC_SPARK");
        final int count = 8 + random.nextInt(9);
        r.deferredAction = () -> {
            Player target = level.getNearestPlayer(x, y, z, 40.0, false);
            List<Creeper> creepers = new ArrayList<>();
            double radius = 11.0 + random.nextDouble() * 5.0;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0 * i / count;
                Creeper creeper = new Creeper(EntityType.CREEPER, level);
                creeper.moveTo(x + Math.cos(angle) * radius, y + 1.0,
                        z + Math.sin(angle) * radius,
                        (float) Math.toDegrees(angle + Math.PI), 0.0f);
                creeper.setNoAi(true);
                creeper.setPersistenceRequired();
                setCreeperCharged(creeper, random.nextDouble() < 0.55);
                level.addFreshEntity(creeper);
                creepers.add(creeper);
            }
            level.playSound(null, x, y, z, SoundEvents.CREEPER_PRIMED,
                    SoundSource.HOSTILE, 2.0f, 0.55f);
            TestProcedure.schedule(level, TestProcedure.currentTick(level) + 30, () -> {
                for (Creeper creeper : creepers) {
                    if (!creeper.isAlive()) continue;
                    creeper.setNoAi(false);
                    if (target != null && target.isAlive()) creeper.setTarget(target);
                }
            });
        };
    }

    private static void event_EndCrystalMinefield(ServerLevel level, double x, double y, double z,
                                                   RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Glass stars ignite across the horizon...";
        r.titleColorCode = "5";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("END_ROD");
        r.particles.add("DRAGON_BREATH");
        final int radius = 45 + random.nextInt(16); // rayon 45-60, sortie tres eloignee
        final int count = 100 + random.nextInt(61); // densite maintenue : 100-160 cristaux
        r.deferredAction = () -> {
            List<EndCrystal> crystals = new ArrayList<>();
            int top = Math.min(level.getMaxBuildHeight() - 3, Mth.floor(y) + 18);
            for (int i = 0; i < count; i++) {
                double angle = i * 2.399963229728653; // repartition sunflower
                double distance = 5.0 + Math.sqrt((i + 1.0) / count) * (radius - 5.0);
                int px = Mth.floor(x + Math.cos(angle) * distance);
                int pz = Mth.floor(z + Math.sin(angle) * distance);
                int groundY = findSurfaceSolidY(level, px, pz, top);
                if (groundY < level.getMinBuildHeight()) continue;
                BlockPos pedestal = new BlockPos(px, groundY + 1, pz);
                if (!level.getBlockState(pedestal).isAir()) continue;
                level.setBlock(pedestal, Blocks.OBSIDIAN.defaultBlockState(), 3);
                EndCrystal crystal = EntityType.END_CRYSTAL.create(level);
                if (crystal == null) continue;
                crystal.moveTo(px + 0.5, groundY + 2.0, pz + 0.5, 0.0f, 0.0f);
                crystal.setShowBottom(true);
                level.addFreshEntity(crystal);
                crystals.add(crystal);
            }
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL,
                    SoundSource.HOSTILE, 2.5f, 1.2f);
            Collections.shuffle(crystals, new java.util.Random(random.nextLong()));
            int triggers = Math.min(8, crystals.size());
            for (int i = 0; i < triggers; i++) {
                EndCrystal crystal = crystals.get(i);
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 60 + i * 8, () -> {
                    if (!crystal.isAlive()) return;
                    // FIX v41 : voir explication dans event_SpectralArrowShower() plus haut.
                    SpectralArrow arrow = new SpectralArrow(level, crystal.getX(), crystal.getY() + 18.0, crystal.getZ(), new ItemStack(Items.SPECTRAL_ARROW), null);
                    arrow.setDeltaMovement(0.0, -2.5, 0.0);
                    level.addFreshEntity(arrow);
                });
            }
        };
    }

    private static void event_TntSkySerpent(ServerLevel level, double x, double y, double z,
                                             RandomSource random, TestProcedure.BranchResult r) {
        r.title = "A distant line begins to fall...";
        r.titleColorCode = "4";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("SMOKE");
        final int durationTicks = (3 + random.nextInt(8)) * 20; // 3-10 s
        final int tntCount = 40 + random.nextInt(51);            // description originale : 40-90
        final double startAngle = random.nextDouble() * Math.PI * 2.0;
        r.deferredAction = () -> {
            Player initial = level.getNearestPlayer(x, y, z, 96.0, false);
            if (!(initial instanceof ServerPlayer target)) return;
            UUID targetId = target.getUUID();
            int startTick = TestProcedure.currentTick(level);

            for (int index = 0; index < tntCount; index++) {
                final int step = index;
                int delay = (int) Math.round(index * durationTicks / (double) Math.max(1, tntCount - 1));
                TestProcedure.schedule(level, startTick + delay, () -> {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetId);
                    if (player == null || !player.isAlive()) return;

                    double progress = step / (double) Math.max(1, tntCount - 1);
                    // La queue commence tres loin et le nez rejoint la position ACTUELLE du joueur.
                    double distance = 72.0 * (1.0 - progress) + 2.0;
                    double angle = startAngle + progress * Math.PI * 5.0;
                    double tx = player.getX() + Math.cos(angle) * distance;
                    double tz = player.getZ() + Math.sin(angle) * distance;
                    double ty = Math.min(level.getMaxBuildHeight() - 2,
                            player.getY() + 34.0 + random.nextDouble() * 18.0);

                    PrimedTnt tnt = EntityType.TNT.create(level);
                    if (tnt == null) return;
                    tnt.moveTo(tx, ty, tz);
                    // La meche commence hors de vue, en hauteur. Le joueur voit surtout la chute.
                    tnt.setFuse(55 + random.nextInt(41));
                    double vx = (player.getX() - tx) * (0.030 + progress * 0.018);
                    double vz = (player.getZ() - tz) * (0.030 + progress * 0.018);
                    tnt.setDeltaMovement(vx, -0.30 - random.nextDouble() * 0.16, vz);
                    level.addFreshEntity(tnt);
                });
            }
        };
    }

    private static void event_NetherBreach(ServerLevel level, double x, double y, double z,
                                            RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The ground splits with a foreign heat...";
        r.titleColorCode = "4";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("FLAME");
        r.deferredAction = () -> {
            int cx = Mth.floor(x), cy = Mth.floor(y), cz = Mth.floor(z);
            double yaw = random.nextDouble() * Math.PI * 2.0;
            for (int step = -16; step <= 16; step++) {
                int px = cx + Mth.floor(Math.cos(yaw) * step);
                int pz = cz + Mth.floor(Math.sin(yaw) * step);
                int groundY = findSurfaceSolidY(level, px, pz, cy + 10);
                if (groundY < level.getMinBuildHeight()) continue;
                int width = 1 + random.nextInt(3);
                for (int ox = -width; ox <= width; ox++) {
                    for (int oz = -width; oz <= width; oz++) {
                        if (ox * ox + oz * oz > width * width) continue;
                        BlockPos ground = new BlockPos(px + ox, groundY, pz + oz);
                        if (level.getBlockState(ground).is(Blocks.BEDROCK)) continue;
                        level.setBlock(ground, random.nextInt(5) == 0
                                ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                : Blocks.NETHERRACK.defaultBlockState(), 3);
                        if (random.nextInt(5) == 0 && level.getBlockState(ground.above()).isAir()) {
                            level.setBlock(ground.above(), Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
            }

            Player target = level.getNearestPlayer(x, y, z, 48.0, false);
            Entity ghast = EntityType.GHAST.create(level);
            if (ghast instanceof Mob ghastMob) {
                ghastMob.moveTo(x + (random.nextDouble() - 0.5) * 18.0,
                        y + 16.0, z + (random.nextDouble() - 0.5) * 18.0,
                        random.nextFloat() * 360.0f, 0.0f);
                ghastMob.setPersistenceRequired();
                if (target != null) ghastMob.setTarget(target);
                level.addFreshEntity(ghastMob);
            }
            int blazes = 3 + random.nextInt(4);
            for (int i = 0; i < blazes; i++) {
                Mob blaze = (Mob) EntityType.BLAZE.create(level);
                if (blaze == null) continue;
                double a = Math.PI * 2.0 * i / blazes;
                blaze.moveTo(x + Math.cos(a) * 12.0, y + 5.0 + random.nextDouble() * 5.0,
                        z + Math.sin(a) * 12.0, random.nextFloat() * 360.0f, 0.0f);
                blaze.setPersistenceRequired();
                if (target != null) blaze.setTarget(target);
                level.addFreshEntity(blaze);
            }
            int piglins = 8 + random.nextInt(7);
            for (int i = 0; i < piglins; i++) {
                Piglin piglin = EntityType.PIGLIN.create(level);
                if (piglin == null) continue;
                double a = Math.PI * 2.0 * i / piglins;
                BlockPos pos = findMobInvasionSpawn(level, x, y, z, random, a);
                piglin.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        random.nextFloat() * 360.0f, 0.0f);
                piglin.setPersistenceRequired();
                if (target != null) piglin.setTarget(target);
                level.addFreshEntity(piglin);
            }
            level.playSound(null, x, y, z, SoundEvents.GHAST_SCREAM,
                    SoundSource.HOSTILE, 2.0f, 0.65f);
        };
    }

    private static void event_TheLastPlatform(ServerLevel level, double x, double y, double z,
                                               RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The world drops away below you...";
        r.titleColorCode = "8";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("CLOUD");
        r.deferredAction = () -> {
            Player nearest = level.getNearestPlayer(x, y, z, 64.0, false);
            if (!(nearest instanceof ServerPlayer player)) return;
            int px = Mth.floor(player.getX()), pz = Mth.floor(player.getZ());
            int groundY = findLastSolidIgnoringSurfaceFluids(level, px, pz,
                    Mth.floor(player.getY()) + 16, level.getMinBuildHeight());
            if (groundY < level.getMinBuildHeight()) groundY = Mth.floor(player.getY()) - 1;
            int platformY = Math.min(level.getMaxBuildHeight() - 5, groundY + 180);
            BlockPos center = new BlockPos(px, platformY, pz);
            List<BlockPos> platform = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    level.setBlock(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                    platform.add(pos.immutable());
                    for (int air = 1; air <= 3; air++) {
                        level.setBlock(pos.above(air), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            player.teleportTo(level, px + 0.5, platformY + 1.0, pz + 0.5,
                    player.getYRot(), player.getXRot());

            Phantom phantom = new Phantom(EntityType.PHANTOM, level);
            phantom.moveTo(px + 0.5, platformY + 14.0, pz + 0.5);
            phantom.setPersistenceRequired();
            level.addFreshEntity(phantom);
            for (int i = 0; i < 4; i++) {
                Shulker shulker = EntityType.SHULKER.create(level);
                if (shulker == null) continue;
                int sx = (i % 2 == 0 ? -2 : 2);
                int sz = (i < 2 ? -2 : 2);
                shulker.moveTo(px + sx + 0.5, platformY + 1.0, pz + sz + 0.5);
                shulker.setPersistenceRequired();
                shulker.setTarget(player);
                level.addFreshEntity(shulker);
            }

            for (int ring = 2; ring >= 0; ring--) {
                final int removeRing = ring;
                int delay = (3 - ring) * 40;
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + delay, () -> {
                    for (BlockPos pos : platform) {
                        int dx = Math.abs(pos.getX() - center.getX());
                        int dz = Math.abs(pos.getZ() - center.getZ());
                        if (Math.max(dx, dz) == removeRing
                                && level.getBlockState(pos).is(Blocks.POLISHED_BLACKSTONE)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                });
            }
        };
    }

    private static void buildHourglassFallback(ServerLevel level, BlockPos origin) {
        int center = 7;
        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 17; y++) {
                for (int z = 0; z < 15; z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) {
            if (x == 0 || x == 14 || z == 0 || z == 14) {
                level.setBlock(origin.offset(x, 0, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
                level.setBlock(origin.offset(x, 16, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            }
        }
        int[][] corners = {{1,1},{1,13},{13,1},{13,13}};
        for (int[] c : corners) for (int y = 1; y < 16; y++) {
            level.setBlock(origin.offset(c[0], y, c[1]), Blocks.CUT_COPPER.defaultBlockState(), 2);
        }
        for (int y = 2; y <= 14; y++) {
            if (y == 8) continue;
            double radius = y < 8 ? 1.0 + (8 - y) * 0.8 : 1.0 + (y - 8) * 0.8;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                BlockPos pos = origin.offset(center + dx, y, center + dz);
                if (d <= radius && d >= radius - 1.15) {
                    level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 2);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) level.setBlock(origin.offset(center + dx, 8, center + dz), Blocks.GLASS.defaultBlockState(), 2);
        }
        for (int y = 10; y <= 13; y++) {
            double radius = 1.0 + (y - 8) * 0.8 - 1.2;
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz < radius * radius) {
                    level.setBlock(origin.offset(center + dx, y, center + dz), Blocks.SAND.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void event_SandHourglass(ServerLevel level, double x, double y, double z,
                                             RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Time begins to pour above you...";
        r.titleColorCode = "6";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("ASH");
        r.deferredAction = () -> {
            int cx = Mth.floor(x), cz = Mth.floor(z);
            int groundY = findLastSolidIgnoringSurfaceFluids(level, cx, cz,
                    Mth.floor(y) + 16, level.getMinBuildHeight());
            if (groundY < level.getMinBuildHeight()) groundY = Mth.floor(y) - 1;
            BlockPos origin = new BlockPos(cx - 7, groundY + 1, cz - 7);
            // FIX (cohérence avec StructureTemplateCache) : ce NBT est petit
            // (~9 Ko) donc non responsable de freezes, mais on passe quand
            // même par le cache mod-wide (déjà préchargé au démarrage
            // serveur) pour éviter toute désérialisation redondante.
            StructureTemplate template = deepluckyblock.util.StructureTemplateCache.get(level, "sand_hourglass");
            if (template != null
                    && template.getSize().getX() >= 15
                    && template.getSize().getY() >= 17) {
                template.placeInWorld(level, origin, origin,
                        new StructurePlaceSettings(), random, 3);
            } else {
                buildHourglassFallback(level, origin);
            }

            Player nearest = level.getNearestPlayer(x, y, z, 64.0, false);
            if (nearest instanceof ServerPlayer player) {
                player.teleportTo(level, origin.getX() + 7.5, origin.getY() + 3.0,
                        origin.getZ() + 7.5, player.getYRot(), player.getXRot());
            }
            // Seuls les sables DANS les limites NBT sont coches : aucune fuite exterieure.
            for (int sx = 0; sx < 15; sx++) for (int sy = 0; sy < 17; sy++) for (int sz = 0; sz < 15; sz++) {
                BlockPos pos = origin.offset(sx, sy, sz);
                if (level.getBlockState(pos).is(Blocks.SAND)) {
                    level.scheduleTick(pos, Blocks.SAND, 2 + random.nextInt(8));
                }
            }
        };
    }

    private static void event_MagmaCubeMonsoon(ServerLevel level, double x, double y, double z,
                                                RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Burning shapes gather in the clouds...";
        r.titleColorCode = "4";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("FLAME");
        final int count = 10 + random.nextInt(16);
        r.deferredAction = () -> {
            for (int i = 0; i < count; i++) {
                MagmaCube cube = new MagmaCube(EntityType.MAGMA_CUBE, level);
                int size = 1 + random.nextInt(5);
                cube.setSize(size, true);
                cube.moveTo(x + (random.nextDouble() - 0.5) * 24.0,
                        y + 18.0 + random.nextDouble() * 14.0,
                        z + (random.nextDouble() - 0.5) * 24.0,
                        random.nextFloat() * 360.0f, 0.0f);
                cube.setDeltaMovement(0.0, -0.8 - random.nextDouble() * 0.5, 0.0);
                cube.setPersistenceRequired();
                level.addFreshEntity(cube);
                if (size >= 4) {
                    TestProcedure.schedule(level, TestProcedure.currentTick(level) + 35, () -> {
                        if (cube.isAlive()) {
                            replaceGroundWithFade(level, cube.getX(), cube.getY(), cube.getZ(),
                                    random, 3, Blocks.MAGMA_BLOCK.defaultBlockState());
                        }
                    });
                }
            }
        };
    }

    private static int findLastSolidIgnoringSurfaceFluids(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = Math.min(top, level.getMaxBuildHeight() - 2); y >= Math.max(bottom, level.getMinBuildHeight()); y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.is(net.minecraft.tags.BlockTags.FLOWERS) || state.is(Blocks.GRASS_BLOCK)
                    || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                    || state.is(Blocks.LARGE_FERN)) continue;
            if (state.blocksMotion()) return y;
        }
        return -1;
    }

    private static void event_CobwebBrood(ServerLevel level, double x, double y, double z,
                                          RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The landscape begins to grow threads...";
        r.titleColorCode = "f";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("WHITE_ASH");
        r.deferredAction = () -> {
            int cx = Mth.floor(x), cy = Mth.floor(y), cz = Mth.floor(z);
            int radius = 20;
            int start = TestProcedure.currentTick(level);

            // Sphere exacte de rayon 20. Pour chaque colonne X/Z, on cherche le
            // dernier bloc solide DANS la sphere, puis absolument tout ce qui est
            // au-dessus (air, eau, plantes...) devient 70 % cobweb / 30 % air.
            // Une tranche X par tick limite la charge serveur.
            for (int dx = -radius; dx <= radius; dx++) {
                final int sliceX = dx;
                TestProcedure.schedule(level, start + dx + radius, () -> {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int horizontalSq = sliceX * sliceX + dz * dz;
                        if (horizontalSq > radius * radius) continue;
                        int verticalRadius = Mth.floor(Math.sqrt(radius * radius - horizontalSq));
                        int minY = cy - verticalRadius;
                        int maxY = cy + verticalRadius;
                        int px = cx + sliceX;
                        int pz = cz + dz;
                        int groundY = findLastSolidIgnoringSurfaceFluids(level, px, pz, maxY, minY);
                        if (groundY < minY) continue;

                        for (int py = groundY + 1; py <= maxY; py++) {
                            BlockPos pos = new BlockPos(px, py, pz);
                            BlockState old = level.getBlockState(pos);
                            if (old.is(Blocks.BEDROCK) || old.hasBlockEntity()) continue;
                            level.setBlock(pos, random.nextDouble() < 0.70
                                    ? Blocks.COBWEB.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                });
            }

            TestProcedure.schedule(level, start + 45, () -> {
                Player target = level.getNearestPlayer(x, y, z, 48.0, false);
                int spiders = 20 + random.nextInt(16);
                for (int i = 0; i < spiders; i++) {
                    CaveSpider spider = new CaveSpider(EntityType.CAVE_SPIDER, level);
                    double angle = Math.PI * 2.0 * i / spiders;
                    BlockPos pos = findMobInvasionSpawn(level, x, y, z, random, angle);
                    spider.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                            random.nextFloat() * 360.0f, 0.0f);
                    spider.setPersistenceRequired();
                    if (target != null) spider.setTarget(target);
                    level.addFreshEntity(spider);
                }
                int silverfishCount = 12 + random.nextInt(13);
                for (int i = 0; i < silverfishCount; i++) {
                    Silverfish silverfish = new Silverfish(EntityType.SILVERFISH, level);
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    BlockPos pos = findMobInvasionSpawn(level, x, y, z, random, angle);
                    silverfish.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    if (target != null) silverfish.setTarget(target);
                    level.addFreshEntity(silverfish);
                }
            });
        };
    }

    private static ItemStack generateLuckyBow(ServerLevel level, RandomSource random, int luck) {
        TestProcedure.QualityTier tier = TestProcedure.QualityTier.getQualityTier(luck);
        for (int attempt = 0; attempt < 18; attempt++) {
            ItemStack generated = GenerateLuckyItemProcedure.generate(level, luck, tier, random);
            if (generated != null && !generated.isEmpty() && generated.getItem() instanceof BowItem) {
                return generated;
            }
        }
        return enchantedPiece(Items.BOW, Math.max(1, luck), random);
    }

    private static ItemStack generateLuckyCrossbow(ServerLevel level, RandomSource random, int luck) {
        TestProcedure.QualityTier tier = TestProcedure.QualityTier.getQualityTier(luck);
        for (int attempt = 0; attempt < 18; attempt++) {
            ItemStack generated = GenerateLuckyItemProcedure.generate(level, luck, tier, random);
            if (generated != null && !generated.isEmpty() && generated.getItem() instanceof CrossbowItem) {
                return generated;
            }
        }
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        GenerateLuckyItemProcedure.addEnchantment(crossbow, "minecraft:quick_charge", 1 + random.nextInt(3));
        GenerateLuckyItemProcedure.addEnchantment(crossbow, "minecraft:piercing", 1 + random.nextInt(4));
        return crossbow;
    }

    private static void event_SkeletonFiringSquad(ServerLevel level, double x, double y, double z,
                                                   RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Two rings of bones take aim...";
        r.titleColorCode = "7";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("CRIT");
        final int count = 12 + random.nextInt(9);
        r.deferredAction = () -> {
            Player target = level.getNearestPlayer(x, y, z, 48.0, false);
            for (int i = 0; i < count; i++) {
                double radius = (i % 2 == 0) ? 11.0 : 17.0;
                double angle = Math.PI * 2.0 * i / count;
                int sx = Mth.floor(x + Math.cos(angle) * radius);
                int sz = Mth.floor(z + Math.sin(angle) * radius);
                int groundY = findSurfaceSolidY(level, sx, sz, Mth.floor(y) + 18);
                if (groundY < level.getMinBuildHeight()) groundY = Mth.floor(y);
                Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
                skeleton.moveTo(sx + 0.5, groundY + 1.0, sz + 0.5,
                        (float) Math.toDegrees(angle + Math.PI), 0.0f);
                int luck = random.nextInt(78); // USED au minimum, jusqu'a OUTSTANDING
                ItemStack bow = generateLuckyBow(level, random, luck);
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
                skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.30f);
                if (random.nextBoolean()) {
                    skeleton.setItemSlot(EquipmentSlot.HEAD,
                            safeSpecific(level, luck, TestProcedure.QualityTier.getQualityTier(luck), random, EquipmentSlot.HEAD));
                    skeleton.setDropChance(EquipmentSlot.HEAD, 0.08f);
                }
                skeleton.setPersistenceRequired();
                if (target != null) skeleton.setTarget(target);
                level.addFreshEntity(skeleton);
            }
        };
    }

    private record FoodBackup(int slot, ItemStack stack) {}

    private static void removeEventRottenFlesh(ServerPlayer player, String eventId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
            if (stack.is(Items.ROTTEN_FLESH) && tag != null
                    && eventId.equals(tag.getString("slb_food_rot_id"))) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void restoreFoodBackups(ServerLevel schedulerLevel, UUID playerId,
                                           String eventId, List<FoodBackup> backups, int retries) {
        ServerPlayer player = schedulerLevel.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            if (retries > 0) {
                TestProcedure.schedule(schedulerLevel, TestProcedure.currentTick(schedulerLevel) + 200, () ->
                        restoreFoodBackups(schedulerLevel, playerId, eventId, backups, retries - 1));
            }
            return;
        }

        // Retire seulement NOTRE rotten flesh taggee. Si elle a ete jetee/mangee,
        // la nourriture originale est quand meme rendue integralement.
        removeEventRottenFlesh(player, eventId);
        for (FoodBackup backup : backups) {
            ItemStack restored = backup.stack().copy(); // ID, count, composants/NBT moddedes conserves
            ItemStack current = player.getInventory().getItem(backup.slot());
            if (current.isEmpty()) {
                player.getInventory().setItem(backup.slot(), restored);
            } else if (!player.getInventory().add(restored)) {
                player.drop(restored, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void event_FoodRot(ServerLevel level, double x, double y, double z,
                                      RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Something in your bags begins to spoil...";
        r.titleColorCode = "2";
        r.rank = TestProcedure.ItemRank.CURSED;
        r.particles.add("SNEEZE");
        final boolean permanentLoss = random.nextBoolean(); // 50 % jamais rendu
        r.deferredAction = () -> {
            Player nearest = level.getNearestPlayer(x, y, z, 64.0, false);
            if (!(nearest instanceof ServerPlayer player)) return;
            List<FoodBackup> backups = new ArrayList<>();
            String eventId = UUID.randomUUID().toString();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty() || !(stack.has(net.minecraft.core.component.DataComponents.FOOD))) continue; // inclut les nourritures moddees
                backups.add(new FoodBackup(slot, stack.copy()));
                ItemStack rotten = new ItemStack(Items.ROTTEN_FLESH, stack.getCount());
                deepluckyblock.util.DeepNbt.getOrCreateTag(rotten).putString("slb_food_rot_id", eventId);
                player.getInventory().setItem(slot, rotten);
            }
            player.inventoryMenu.broadcastChanges();
            if (backups.isEmpty()) return;

            giveEventEffect(player, MobEffects.HUNGER, 900, 2);
            int husks = 5 + random.nextInt(6);
            for (int i = 0; i < husks; i++) {
                Mob husk = (Mob) EntityType.HUSK.create(level);
                if (husk == null) continue;
                double a = Math.PI * 2.0 * i / husks;
                BlockPos pos = findMobInvasionSpawn(level, x, y, z, random, a);
                husk.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        random.nextFloat() * 360.0f, 0.0f);
                husk.setTarget(player);
                level.addFreshEntity(husk);
            }

            if (!permanentLoss) {
                TestProcedure.schedule(level, TestProcedure.currentTick(level) + 900, () ->
                        restoreFoodBackups(level, player.getUUID(), eventId, backups, 30));
            }
        };
    }

    private static void equipGeneratedMimicMob(Mob mob, ServerLevel level, RandomSource random) {
        // T338-MOB-PIECE-BY-PIECE : Chaque pièce du mob mimic est tirée indépendamment depuis le Lucky Block
        int desiredBonusLuck = -15 + random.nextInt(91); // -15..+75
        GenerateLuckyItemProcedure.equipMobPieceByPieceFromLuckyBlock(
                mob, level, desiredBonusLuck, 0.36f, 0.72f, true, 0.08f, 0.16f, random);
        mob.setPersistenceRequired();
    }

    private static void event_ChestMimicField(ServerLevel level, double x, double y, double z,
                                               RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Too many locks click open at once...";
        r.titleColorCode = "6";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("ENCHANT");
        final int count = 5 + random.nextInt(5);
        final int realIndex = random.nextInt(count);
        r.deferredAction = () -> {
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0 * i / count;
                double distance = 6.0 + random.nextDouble() * 5.0;
                int px = Mth.floor(x + Math.cos(angle) * distance);
                int pz = Mth.floor(z + Math.sin(angle) * distance);
                int groundY = findSurfaceSolidY(level, px, pz, Mth.floor(y) + 12);
                if (groundY < level.getMinBuildHeight()) continue;
                BlockPos chestPos = new BlockPos(px, groundY + 1, pz);
                if (i == realIndex) {
                    placeLuckyChest(level, chestPos, 2); // coffre EPIC du systeme du mod
                    if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity epic) {
                        epic.getPersistentData().putInt("slb_chest_rank", 2);
                        epic.setChanged();
                    }
                } else {
                    level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                    if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity mimic) {
                        mimic.getPersistentData().putBoolean("slb_mimic_chest", true);
                        mimic.getPersistentData().putBoolean("slb_mimic_triggered", false);
                        mimic.setChanged();
                    }
                }
            }
        };
    }

    private static void event_ShadowDisciples(ServerLevel level, double x, double y, double z,
                                               RandomSource random, TestProcedure.BranchResult r) {
        r.title = "Your equipment casts more than one shadow...";
        r.titleColorCode = "8";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("SQUID_INK");
        final int count = 2 + random.nextInt(3);
        r.deferredAction = () -> {
            Player nearest = level.getNearestPlayer(x, y, z, 48.0, false);
            if (!(nearest instanceof ServerPlayer player)) return;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            for (int i = 0; i < count; i++) {
                Zombie disciple = new Zombie(EntityType.ZOMBIE, level);
                double angle = Math.PI * 2.0 * i / count;
                disciple.moveTo(x + Math.cos(angle) * 7.0, y + 1.0,
                        z + Math.sin(angle) * 7.0, random.nextFloat() * 360.0f, 0.0f);
                disciple.setCustomName(Component.literal("§8§l").append(
                        Component.translatable("event.deep_lucky_block.name.shadow_disciple")));
                disciple.setCustomNameVisible(true);
                for (EquipmentSlot slot : slots) {
                    ItemStack copy = player.getItemBySlot(slot).copy();
                    if (copy.isEmpty()) continue;
                    if (copy.isDamageableItem()) {
                        int extraDamage = Math.max(1, (int) (copy.getMaxDamage() * (0.45 + random.nextDouble() * 0.35)));
                        copy.setDamageValue(Math.min(copy.getMaxDamage() - 1,
                                copy.getDamageValue() + extraDamage));
                    }
                    disciple.setItemSlot(slot, copy);
                    disciple.setDropChance(slot, 0.20f);
                }
                if (disciple.getAttribute(Attributes.MAX_HEALTH) != null) {
                    disciple.getAttribute(Attributes.MAX_HEALTH).setBaseValue(36.0);
                    disciple.setHealth(36.0f);
                }
                if (disciple.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                    disciple.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.31);
                }
                disciple.setPersistenceRequired();
                disciple.setTarget(player);
                level.addFreshEntity(disciple);
            }
        };
    }

    // ==================== SAFE SPECIFIC ====================
    private static ItemStack safeSpecific(ServerLevel level, int luck, TestProcedure.QualityTier tier, RandomSource random, EquipmentSlot slot) {
        ItemStack stack = GenerateLuckyItemProcedure.generateSpecific(level, luck, tier, random, slot);
        if (stack == null || stack.isEmpty()) { return switch (slot) { case HEAD -> new ItemStack(Items.IRON_HELMET); case CHEST -> new ItemStack(Items.IRON_CHESTPLATE); case LEGS -> new ItemStack(Items.IRON_LEGGINGS); case FEET -> new ItemStack(Items.IRON_BOOTS); case MAINHAND -> new ItemStack(Items.IRON_SWORD); case OFFHAND -> new ItemStack(Items.FISHING_ROD); case BODY -> new ItemStack(Items.IRON_HORSE_ARMOR); }; }
        return stack;
    }

    // ==================== TEAM CLAQUETTE AMBUSH (event noir 88) ====================

    /*
     * Event noir : 1 a 3 membres de la TEAM CLAQUETTE (Jerry, Ben, Samouss)
     * surgissent autour du joueur, SANS DOUBLON possible : la liste est
     * melangee puis on prend les `count` premiers, chaque membre etant unique.
     */
    private static void event_CrewAmbush(ServerLevel level, double x, double y, double z,
                                         RandomSource random, TestProcedure.BranchResult r) {
        r.title = "The Team Claquette has arrived...";
        r.titleColorCode = "0";
        r.rank = TestProcedure.ItemRank.BROKEN;
        r.particles.add("SMOKE");
        r.particles.add("SOUL");
        final int count = 1 + random.nextInt(3); // 1 a 3 membres

        r.deferredAction = () -> {
            // Les trois membres de la Team Claquette, chacun cree une seule fois.
            List<Mob> members = new ArrayList<>();
            try {
                members.add(new JerryEntity(DeepLuckyBlockModEntities.JERRY.get(), level));
            } catch (Exception ignored) {}
            try {
                members.add(new BenEntity(DeepLuckyBlockModEntities.BEN.get(), level));
            } catch (Exception ignored) {}
            try {
                members.add(new SamoussBossEntity(DeepLuckyBlockModEntities.SAMOUSS_BOSS.get(), level));
            } catch (Exception ignored) {}

            // Melange puis spawn des `count` premiers : jamais de doublon.
            Collections.shuffle(members, new java.util.Random(random.nextLong()));
            int toSpawn = Math.min(count, members.size());
            if (toSpawn == 0) return;

            Player nearest = level.getNearestPlayer(x, y, z, 64.0, false);
            for (int i = 0; i < toSpawn; i++) {
                Mob member = members.get(i);
                double angle = Math.PI * 2.0 * i / toSpawn;
                BlockPos spawnPos = findMobInvasionSpawn(level, x, y, z, random, angle);
                member.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                        random.nextFloat() * 360.0f, 0.0f);
                member.setPersistenceRequired();
                if (nearest != null && nearest.isAlive()) {
                    member.setTarget(nearest);
                }

                // ── V40 : armure enchantee INVISIBLE (luck 10-100) ────────────
                // Samouss possede deja son propre set : on se contente de
                // l'enchanter (enchantWornArmor conserve les pieces existantes).
                // Ben et Jerry recoivent deja leur armure invisible dans leur
                // constructeur ; l'appel ci-dessous ne concerne donc que Samouss.
                // Rien n'est visible : les renderers du trio n'ajoutent plus de
                // HumanoidArmorLayer, donc ni les pieces ni le reflet violet
                // d'enchantement ne sont rendus.
                if (member instanceof SamoussBossEntity) {
                    GenerateLuckyItemProcedure.enchantWornArmor(member, 10, 100, random);
                }

                level.addFreshEntity(member);
            }

            level.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE,
                    SoundSource.HOSTILE, 1.5f, 0.5f);
            level.sendParticles(ParticleTypes.SOUL, x, y + 2, z,
                    50, 1.5, 1.5, 1.5, 0.05);

            if (toSpawn == 3) {
                actionBar(level, x, y, z, "0", "⚠ LA TEAM CLAQUETTE AU COMPLET débarque !");
                // 🏆 Achievement : le trio complet a spawné.
                Player trioNear = level.getNearestPlayer(x, y, z, 64.0, false);
                if (trioNear instanceof ServerPlayer spTrio) {
                    DeepLuckyBlockAdvancements.grantTrioClaquette(spTrio);
                }
            } else if (toSpawn == 2) {
                actionBar(level, x, y, z, "0", "⚠ Deux membres de la Team Claquette rappliquent !");
            } else {
                actionBar(level, x, y, z, "0", "⚠ Un membre de la Team Claquette rapplique !");
            }
            narrate(level, x, y, z, random, "The Team Claquette has arrived...");
        };
    }

    // ==================== TEAM CLAQUETTE : LOOT SAMOUSS À LA MORT ====================
    // Chaque membre du trio (Jerry, Ben, Samouss) a 50 % de chance de lâcher
    // UNE pièce aléatoire de l'armure Samouss à sa mort, enchantée par
    // GenerateLuckyItem avec une luck aléatoire 1-100 (rang, nombre et
    // puissance des enchantements + nom coloré au rang, comme les items du
    // lucky block).
    //
    // Anti-doublon : si une pièce Samouss est DÉJÀ dans les drops (posée par
    // la logique propre de l'entité, ex. dropCustomDeathLoot), on n'en ajoute
    // pas une deuxième — on garantit simplement qu'elle est enchantée.
    //
    // Les items du set sont retrouvés par nom dans le registre
    // (deep_lucky_block:*samouss*_helmet/_chestplate/_leggings/_boots).
    // Si un item est introuvable, fallback diamant + message d'erreur console.

    private static final Item[] SAMOUSS_LOOT_PIECES = new Item[4];
    private static boolean samoussLootCacheReady = false;

    private static void initSamoussLootCache() {
        if (samoussLootCacheReady) return;
        samoussLootCacheReady = true;
        String[] suffixes = {"_helmet", "_chestplate", "_leggings", "_boots"};
        Item[] fallback = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                           Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        for (int i = 0; i < 4; i++) {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                if (key != null && "deep_lucky_block".equals(key.getNamespace())
                        && key.getPath().contains("samouss")
                        && key.getPath().endsWith(suffixes[i])) {
                    SAMOUSS_LOOT_PIECES[i] = item;
                    break;
                }
            }
            if (SAMOUSS_LOOT_PIECES[i] == null) {
                SAMOUSS_LOOT_PIECES[i] = fallback[i];
                deepluckyblock.util.DebugLog.err("[deep_lucky_block] Item Samouss introuvable pour "
                        + suffixes[i] + " -> fallback diamant");
            }
        }
    }

    private static boolean isSamoussLootPiece(Item item) {
        initSamoussLootCache();
        for (Item piece : SAMOUSS_LOOT_PIECES) if (piece == item) return true;
        return false;
    }

    private static EquipmentSlot slotForSamoussPiece(Item item) {
        initSamoussLootCache();
        if (item == SAMOUSS_LOOT_PIECES[0]) return EquipmentSlot.HEAD;
        if (item == SAMOUSS_LOOT_PIECES[1]) return EquipmentSlot.CHEST;
        if (item == SAMOUSS_LOOT_PIECES[2]) return EquipmentSlot.LEGS;
        return EquipmentSlot.FEET;
    }

    @SubscribeEvent
    public static void onTeamClaquetteDeathLoot(
            net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide()) return;
            if (!(entity instanceof JerryEntity)
                    && !(entity instanceof BenEntity)
                    && !(entity instanceof SamoussBossEntity)) return;

            RandomSource random = entity.getRandom();
            initSamoussLootCache();

            // Une pièce Samouss est déjà dans les drops (dropCustomDeathLoot...) :
            // pas de doublon, on garantit juste qu'elle est enchantée.
            for (ItemEntity drop : event.getDrops()) {
                ItemStack stack = drop.getItem();
                if (!stack.isEmpty() && isSamoussLootPiece(stack.getItem())) {
                    if (!stack.isEnchanted()) {
                        GenerateLuckyItemProcedure.enchantExistingArmor(stack,
                                1 + random.nextInt(100),
                                slotForSamoussPiece(stack.getItem()), random);
                    }
                    return;
                }
            }

            // Sinon : 50 % de chance de lâcher une pièce aléatoire enchantée.
            if (random.nextInt(100) >= 50) return;

            int pieceIndex = random.nextInt(4);
            EquipmentSlot slot = switch (pieceIndex) {
                case 0 -> EquipmentSlot.HEAD;
                case 1 -> EquipmentSlot.CHEST;
                case 2 -> EquipmentSlot.LEGS;
                default -> EquipmentSlot.FEET;
            };
            ItemStack piece = new ItemStack(SAMOUSS_LOOT_PIECES[pieceIndex]);
            // Enchantée par GenerateLuckyItem : luck aléatoire 1-100.
            GenerateLuckyItemProcedure.enchantExistingArmor(piece,
                    1 + random.nextInt(100), slot, random);
            event.getDrops().add(new ItemEntity(
                    entity.level(), entity.getX(), entity.getY(), entity.getZ(), piece));
        } catch (Throwable t) {
            if (DEBUG_LOGS) deepluckyblock.util.DebugLog.trace(t);
        }
    }

    // ==================== 🧪 DEBUG COMMAND /dlb b ====================
    // Usage (OP requis, permission level 2) :
    //   /dlb b <luck> <count>   -> <count> générations NÉGATIVES à la position du joueur.
    //
    // <luck>  = nombre précis ("5") OU plage inclusive ("5-35" -> 5 à 35 inclus).
    // <count> = nombre de générations (1 à 64), traitées 1 par 1.
    //
    // Exemples :
    //   /dlb b 5 10    -> 10 générations bad à luck 5
    //   /dlb b 0-10 3  -> 3 générations bad, luck aléatoire entre 0 et 10 inclus
    //
    // Appel DIRECT de GenerateBadProcedure.generate(...) : aucune réflexion,
    // aucun fallback. La catégorie de roulette (BLACK / RED / GREEN) reste
    // aléatoire comme sur un vrai tirage, et le deferredAction de l'event est
    // exécuté immédiatement (comme /eventtest et /eventrun).
    public static boolean DEBUG_COMMAND_ENABLED = true; // 🔧 actuellement sur TRUE

    @SubscribeEvent
    public static void onRegisterDebugCommands(RegisterCommandsEvent event) {
        // T312-DLB-LAUNCH : L'enregistrement de /dlb (g, b, probs, w, rank) est désormais
        // unifié dans deepluckyblock.commands.DlbCommand afin d'éviter tout conflit Brigadier.
    }

    private static int runDebugBadCommand(CommandSourceStack source, String luckSpec, int count) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c[SLB-DEBUG] /dlb b est une commande joueur uniquement."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        RandomSource random = level.getRandom();
        final double x = player.getX(), y = player.getY(), z = player.getZ();

        int done = 0;
        for (int i = 0; i < count; i++) {
            int luck;
            try {
                luck = parseDebugBadLuck(luckSpec, random);
            } catch (NumberFormatException ex) {
                player.sendSystemMessage(Component.literal(
                    "§c[SLB-DEBUG] Luck invalide : '" + luckSpec + "' (attendu : \"5\" ou \"5-35\")"));
                break;
            }
            done++;
            runDebugBadGeneration(level, player, luck, i + 1);
        }
        return done;
    }

    /** Parse "5" ou "5-35" (bornes incluses) et renvoie une luck aléatoire dans la plage. */
    private static int parseDebugBadLuck(String spec, RandomSource random) {
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

    /** Une génération bad : appel DIRECT du vrai pipeline négatif de cette classe. */
    private static void runDebugBadGeneration(ServerLevel level, ServerPlayer player, int luck, int index) {
        try {
            TestProcedure.BranchResult res = GenerateBadProcedure.generate(
                    level, player.getX(), player.getY(), player.getZ(), luck, level.getRandom());

            // Action différée exécutée immédiatement, comme /eventtest et /eventrun.
            if (res != null && res.deferredAction != null) {
                res.deferredAction.run();
            }

            if (res != null && res.title != null) {
                String col = res.titleColorCode != null && res.titleColorCode.length() == 1
                        ? res.titleColorCode : "d";
                player.sendSystemMessage(
                        Component.literal("§7[SLB-DEBUG] Bad #" + index + " (luck " + luck + ") §" + col + "§l")
                                .append(localizeEventTitle(res.title)));
            }

            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] /dlb b #" + index + " luck=" + luck
                    + (res != null ? " event=" + res.badEventNumber + " rank=" + res.rank : " res=null"));
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.trace(t);
            player.sendSystemMessage(Component.literal("§c[SLB-DEBUG] generate a échoué : " + t));
        }
    }
}
