package deepluckyblock.client;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.EnchantmentComboRegistry;
import deepluckyblock.enchantments.UniversalEnchantmentHandler;
import deepluckyblock.network.DeepPayloads;
import deepluckyblock.util.DeepEnchant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * T307-CLOUD-WALKER + T308-COMBOS (#1 Acrobate Miel-Slime & #2 Marcheur de l'Empyrée) :
 *  - Marcheur des Nuages : Double Saut + Triple Saut sur coussin de nuages (et Quadruple Saut avec Combo #2).
 *  - Combo #1 (wall_runner + bouncy) : Wall-Jump élastique en appuyant sur Espace contre une paroi !
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class CloudWalkerClientInput {

    private static boolean wasJumpDown = false;
    private static int airTicks = 0;
    private static int airJumpsUsed = 0;
    private static int wallJumpCooldown = 0;

    private CloudWalkerClientInput() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null) {
                wasJumpDown = false;
                airTicks = 0;
                airJumpsUsed = 0;
                wallJumpCooldown = 0;
                return;
            }
            DeepEnchant.rememberClientAccess(mc.level.registryAccess());
            if (wallJumpCooldown > 0) wallJumpCooldown--;

            boolean jumpDown = mc.screen == null && mc.options.keyJump.isDown();

            if (player.onGround() || player.isInWater() || player.isInLava()
                    || player.onClimbable() || player.isFallFlying()
                    || player.isPassenger() || player.getAbilities().flying) {
                airTicks = 0;
                airJumpsUsed = 0;
                wasJumpDown = jumpDown;
                return;
            }

            airTicks++;

            // T308-COMBO #1 : Acrobate Miel-Slime (wall_runner + bouncy) -> Wall-Jump élastique depuis un mur !
            if (airTicks >= 2 && jumpDown && !wasJumpDown && wallJumpCooldown == 0
                    && EnchantmentComboRegistry.hasCombo(player, EnchantmentComboRegistry.C01_ACROBAT_ALCHIMIQUE)
                    && UniversalEnchantmentHandler.isTouchingHorizontalWall(player)) {
                wallJumpCooldown = 8;
                Vec3 look = player.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0, look.z);
                if (flat.lengthSqr() > 1.0e-4) flat = flat.normalize();
                player.setDeltaMovement(flat.x * 0.56, 0.62, flat.z * 0.56);
                player.fallDistance = 0.0F;
                PacketDistributor.sendToServer(new DeepPayloads.CloudJumpMessage(-1));
                wasJumpDown = true;
                return;
            }

            int cwLvl = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:cloud_walker");
            boolean hasQuadCombo = EnchantmentComboRegistry.hasCombo(player, EnchantmentComboRegistry.C02_ASCENSION_CELESTE);
            int maxAirJumps = hasQuadCombo ? 3 : 2;

            if (cwLvl > 0 && airTicks >= 2 && jumpDown && !wasJumpDown && airJumpsUsed < maxAirJumps) {
                airJumpsUsed++;
                double jumpVy = (airJumpsUsed >= 3) ? 0.64 : ((airJumpsUsed == 2) ? 0.56 : 0.50);
                double horizBoost = (hasQuadCombo && airJumpsUsed >= 3) ? 0.20 : 0.08;
                Vec3 look = player.getLookAngle();
                Vec3 horiz = new Vec3(look.x, 0.0, look.z);
                if (horiz.lengthSqr() > 1.0e-4) {
                    horiz = horiz.normalize().scale(horizBoost);
                }
                Vec3 dm = player.getDeltaMovement();
                player.setDeltaMovement(dm.x + horiz.x, jumpVy, dm.z + horiz.z);
                player.fallDistance = 0.0F;

                spawnLocalCloudCushion(mc.level, player.getX(), player.getY() - 0.06, player.getZ(), airJumpsUsed >= maxAirJumps);
                PacketDistributor.sendToServer(new DeepPayloads.CloudJumpMessage(airJumpsUsed));
            }

            wasJumpDown = jumpDown;
        } catch (Throwable ignored) {}
    }

    private static void spawnLocalCloudCushion(ClientLevel level, double x, double y, double z, boolean radiant) {
        double r = radiant ? 0.98 : 0.72;
        int ring = radiant ? 14 : 10;
        for (int i = 0; i < ring; i++) {
            double ang = (Math.PI * 2.0 * i) / ring;
            double vx = Math.cos(ang) * 0.035;
            double vz = Math.sin(ang) * 0.035;
            level.addParticle(ParticleTypes.CLOUD, x + Math.cos(ang) * r * 0.45, y, z + Math.sin(ang) * r * 0.45, vx, -0.01, vz);
        }
        for (int i = 0; i < 6; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.5;
            double oz = (level.random.nextDouble() - 0.5) * 0.5;
            level.addParticle(ParticleTypes.CLOUD, x + ox, y, z + oz, 0.0, 0.01, 0.0);
        }
    }
}
