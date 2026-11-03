package deepluckyblock.procedures;

import deepluckyblock.enchantments.PixelFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.*;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = "deep_lucky_block")
public class BobFishingHookProcedure {

    private static final Map<UUID, Integer> bobCooldowns = new HashMap<>();
    private static final Set<UUID> activeBobs = new HashSet<>();

    private static final int COOLDOWN_TICKS = 200;
    private static final double RANGE = 300.0;
    private static final double PULL_STRENGTH = 16.0;

    private static final String TAG_HOOK = "BobFishingHook";
    private static final String TAG_OWNER = "BobOwner";

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;

        ServerLevel level = (ServerLevel) event.getLevel();

        bobCooldowns.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });

        for (Player player : level.players()) {  
            if (!player.isAlive() || player.isSpectator()) continue;

            // Effet visuel : Fil de pêche magique reliant la canne à pêche de Bob au bouchon en vol
            for (Snowball hook : level.getEntitiesOfClass(Snowball.class, player.getBoundingBox().inflate(RANGE), h -> h.isAlive() && h.getPersistentData().getBoolean(TAG_HOOK))) {
                UUID ownerId = hook.getPersistentData().getUUID(TAG_OWNER);
                Zombie bobOwner = findBobByUUID(level, ownerId);
                if (bobOwner != null && bobOwner.isAlive()) {
                    Vec3 handPos = bobOwner.position().add(0, bobOwner.getBbHeight() * 0.75, 0);
                    Vec3 hookPos = hook.position();
                    drawFishingLine(level, handPos, hookPos, 12);
                }
            }

            List<Zombie> nearbyBobs = level.getEntitiesOfClass(
                    Zombie.class,
                    player.getBoundingBox().inflate(RANGE),
                    z -> z.isAlive() && z.getPersistentData().getBoolean("IsEternalBob")
            );

            for (Zombie bob : nearbyBobs) {
                UUID bobId = bob.getUUID();

                if (bobCooldowns.containsKey(bobId) || activeBobs.contains(bobId)) continue;

                if (bob.getOffhandItem().getItem() != Items.FISHING_ROD &&
                    bob.getMainHandItem().getItem() != Items.FISHING_ROD) {
                    continue;
                }

                if (!bob.hasLineOfSight(player)) continue;

                throwHook(bob, player, level);
                bobCooldowns.put(bobId, COOLDOWN_TICKS);
                activeBobs.add(bobId);
                break;
            }
        }
    }

    private static void throwHook(Zombie bob, Player target, ServerLevel level) {
        Snowball hook = new Snowball(level, bob);
        ItemStack bobberItem = new ItemStack(Items.TRIPWIRE_HOOK);
        bobberItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§l✦ Bouchon Éternel ✦"));
        hook.setItem(bobberItem);
        hook.setInvisible(false);
        hook.setSilent(false);

        double startX = bob.getX();
        double startY = bob.getY() + bob.getBbHeight() * 0.55;
        double startZ = bob.getZ();

        hook.setPos(startX, startY, startZ);
        hook.getPersistentData().putBoolean(TAG_HOOK, true);
        hook.getPersistentData().putUUID(TAG_OWNER, bob.getUUID());

        double dx = target.getX() - startX;
        double dy = target.getY() + target.getBbHeight() * 0.30 - startY;
        double dz = target.getZ() - startZ;

        hook.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.08, dz, 3.0F, 1.0F);
        level.addFreshEntity(hook);

        level.playSound(null, bob.getX(), bob.getY(), bob.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 0.15f, 0.75f);

        InteractionHand hand = bob.getOffhandItem().getItem() == Items.FISHING_ROD
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        bob.swing(hand);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof Snowball hook)) return;
        if (!hook.getPersistentData().getBoolean(TAG_HOOK)) return;
        if (hook.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) hook.level();
        UUID bobId = hook.getPersistentData().getUUID(TAG_OWNER);

        if (event.getRayTraceResult() instanceof EntityHitResult entityHit &&
            entityHit.getEntity() instanceof Player player) {

            Zombie bob = findBobByUUID(level, bobId);
            if (bob != null && bob.isAlive() && player.isAlive()
                    && !player.isSpectator()) {
                pullPlayerToBob(player, bob, level);
            }
        }

        activeBobs.remove(bobId);
        bobCooldowns.put(bobId, COOLDOWN_TICKS);
        hook.discard();
    }

    private static void pullPlayerToBob(Player player, Zombie bob, ServerLevel level) {
        Vec3 bobPos = bob.position().add(0, bob.getBbHeight() * 0.45, 0);
        Vec3 pullDir = bobPos.subtract(player.position()).normalize();

        double pullX = pullDir.x * PULL_STRENGTH;
        double pullZ = pullDir.z * PULL_STRENGTH;
        double pullY = pullDir.y * PULL_STRENGTH + 1.0;

        player.setDeltaMovement(pullX, pullY, pullZ);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;

        player.hurt(level.damageSources().mobAttack(bob), 6.0f);

        Vec3 handPos = bob.position().add(0, bob.getBbHeight() * 0.75, 0);
        Vec3 playerPos = player.position().add(0, player.getBbHeight() * 0.5, 0);
        drawFishingLine(level, handPos, playerPos, 20);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.HOSTILE, 0.5f, 0.9f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 0.8f, 0.8f);
        bob.swing(InteractionHand.MAIN_HAND);
    }

    private static void drawFishingLine(ServerLevel level, Vec3 start, Vec3 end, int count) {
        double dx = (end.x - start.x) / count;
        double dy = (end.y - start.y) / count;
        double dz = (end.z - start.z) / count;
        for (int i = 0; i <= count; i++) {
            double x = start.x + dx * i;
            double y = start.y + dy * i;
            double z = start.z + dz * i;
            PixelFx.send(level, ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            PixelFx.send(level, ParticleTypes.FISHING, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private static Zombie findBobByUUID(ServerLevel level, UUID uuid) {
        Entity entity = level.getEntity(uuid);
        return entity instanceof Zombie zombie ? zombie : null;
    }
}