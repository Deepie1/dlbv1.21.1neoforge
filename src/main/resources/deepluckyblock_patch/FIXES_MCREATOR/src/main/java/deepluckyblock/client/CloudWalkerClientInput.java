package deepluckyblock.client;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.UniversalEnchantmentHandler;
import deepluckyblock.network.DeepPayloads;
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
 * T307-CLOUD-WALKER : Gestion côté client du Triple Saut de « Marcheur des Nuages » (cloud_walker).
 *
 * Fonctionnement :
 *  - Saut #1 : saut normal depuis le sol (+ nuage de particules sous les pieds).
 *  - Saut #2 (Double Saut) & Saut #3 (Triple Saut) : détecte un nouvel appui sur Espace
 *    en plein air (airTicks >= 2 pour ne jamais consommer de saut sur le tick du décollage),
 *    applique immédiatement l'impulsion verticale côté client, génère un coussin de nuages local
 *    et envoie {@link DeepPayloads.CloudJumpMessage} au serveur pour synchroniser la physique,
 *    annuler fallDistance et diffuser le coussin de nuages aux autres joueurs.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class CloudWalkerClientInput {

    private static boolean wasJumpDown = false;
    private static int airTicks = 0;
    private static int airJumpsUsed = 0;

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
                return;
            }
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
            int cwLvl = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:cloud_walker");
            if (cwLvl > 0 && airTicks >= 2 && jumpDown && !wasJumpDown && airJumpsUsed < 2) {
                airJumpsUsed++;
                double jumpVy = (airJumpsUsed == 1) ? 0.50 : 0.56;
                Vec3 look = player.getLookAngle();
                Vec3 horiz = new Vec3(look.x, 0.0, look.z);
                if (horiz.lengthSqr() > 1.0e-4) {
                    horiz = horiz.normalize().scale(0.08);
                }
                Vec3 dm = player.getDeltaMovement();
                player.setDeltaMovement(dm.x + horiz.x, jumpVy, dm.z + horiz.z);
                player.fallDistance = 0.0F;

                spawnLocalCloudCushion(mc.level, player.getX(), player.getY() - 0.06, player.getZ(), airJumpsUsed == 2);
                PacketDistributor.sendToServer(new DeepPayloads.CloudJumpMessage(airJumpsUsed));
            }

            wasJumpDown = jumpDown;
        } catch (Throwable ignored) {}
    }

    private static void spawnLocalCloudCushion(ClientLevel level, double x, double y, double z, boolean thirdJump) {
        double r = thirdJump ? 0.95 : 0.72;
        int ring = thirdJump ? 14 : 10;
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
