package deepluckyblock.client;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.EnchantmentComboRegistry;
import deepluckyblock.enchantments.UniversalEnchantmentHandler;
import deepluckyblock.network.DeepPayloads;
import deepluckyblock.util.DeepEnchant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * T320-CLOUD-ARTIFACTS & T320-AIRWALK-CLIENT-LOCKSTEP :
 *  1) Marcheur des Nuages (cloud_walker) : Architecture exacte du « Cloud in a Bottle »
 *     d'Artifacts 1.21.1 (player.input.jumping + hasReleasedJumpKey + DoubleJump#jump :
 *     upwardsMotion=0.50 + JumpBoost, x1.25 vertical & +0.25 horizontal en sprint sur getYRot(),
 *     hasImpulse=true sans écrasement serveur) étendue au Triple Saut (2 sauts aériens, ou 3 avec Combo #2).
 *  2) Marche Aérienne (foot_placement) & Foulée des Nuages (cloud_stride) :
 *     Pose locale immédiate des Blocks.BARRIER côté Client en ClientTickEvent.Pre (AVANT LocalPlayer#tick
 *     et la résolution des collisions !), synchronisée à l'entier exact avec le Serveur via AirWalkAnchorMessage :
 *     - Solide dès la milliseconde 0 (zéro tremblement, zéro chute à travers, saut 100% pleine hauteur).
 *     - Plancher constant pendant les sauts normaux & Jump Boost (zéro bloc posé plus haut devant les jambes).
 *     - Balayage dynamique proportionnel à la vitesse (zéro mini-rollback même sous gros boosts Speed / Jump).
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class CloudWalkerClientInput {

    // --- État Triple Saut (style Artifacts CloudInABottleInputHandler) ---
    private static boolean hasReleasedJumpKey = false;
    private static int jumpsRemaining = 0;
    private static int wallJumpCooldown = 0;

    // --- État Marche Aérienne Client-First (ClientTickEvent.Pre) ---
    private static ClientLevel trackedLevel = null;
    private static final Map<BlockPos, Long> CLIENT_BARRIER_TRAIL = new HashMap<>();
    private static int clientAnchorFloorY = Integer.MIN_VALUE;
    private static int lastSentAnchorFloorY = Integer.MIN_VALUE;
    private static boolean cloudAscentPending = false;
    private static int clientShiftDiveTicks = 0;
    private static double prevClientX = Double.NaN;
    private static double prevClientZ = Double.NaN;

    private CloudWalkerClientInput() {}

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            ClientLevel level = mc.level;
            if (player == null || level == null || !player.isAlive() || player.isSpectator()) {
                resetClientAirWalk(level, false);
                return;
            }
            if (trackedLevel != level) {
                CLIENT_BARRIER_TRAIL.clear();
                trackedLevel = level;
                clientAnchorFloorY = Integer.MIN_VALUE;
                lastSentAnchorFloorY = Integer.MIN_VALUE;
                cloudAscentPending = false;
                clientShiftDiveTicks = 0;
                prevClientX = Double.NaN;
                prevClientZ = Double.NaN;
            }
            DeepEnchant.rememberClientAccess(level.registryAccess());

            int footPl = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:air_walk");
            int cloudStride = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:cloud_stride");
            int oxyHov = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:air_hover");
            boolean hasAirWalk = footPl > 0 || (cloudStride > 0 && player.getY() >= 160.0);

            boolean sneaking = (player.input != null && player.input.shiftKeyDown) || player.isShiftKeyDown();
            if (!hasAirWalk || sneaking) {
                if (sneaking && (footPl > 0 || cloudStride > 0)) {
                    clearAllClientBarriers(level);
                }
                if (clientAnchorFloorY != Integer.MIN_VALUE || lastSentAnchorFloorY != Integer.MIN_VALUE) {
                    clientAnchorFloorY = Integer.MIN_VALUE;
                    cloudAscentPending = false;
                    syncAnchorToServer(Integer.MIN_VALUE);
                }
                // T333-AIRWALK-SHIFT-DIVE : Descente progressive de plus en plus rapide tant que Shift est maintenu en l'air
                if (sneaking && footPl > 0 && oxyHov <= 0
                        && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                        && !player.isFallFlying() && !player.isPassenger() && !player.getAbilities().flying) {
                    if (!hasSolidGroundBelow(level, player)) {
                        player.setOnGround(false);
                        clientShiftDiveTicks++;
                        Vec3 dmDive = player.getDeltaMovement();
                        double accel = Math.min(0.14, 0.020 + clientShiftDiveTicks * 0.0045);
                        double maxDive = -Math.min(1.85, 0.38 + clientShiftDiveTicks * 0.038);
                        double targetVy = Math.max(maxDive, Math.min(dmDive.y, -0.15) - accel);
                        player.setDeltaMovement(dmDive.x, targetVy, dmDive.z);
                        player.fallDistance = 0.0F;
                    } else {
                        clientShiftDiveTicks = 0;
                    }
                } else {
                    clientShiftDiveTicks = 0;
                }
                prevClientX = player.getX();
                prevClientZ = player.getZ();
                return;
            }

            if (player.isInWater() || player.isInLava() || player.onClimbable()
                    || player.isFallFlying() || player.isPassenger() || player.getAbilities().flying) {
                clientAnchorFloorY = Integer.MIN_VALUE;
                cloudAscentPending = false;
                clientShiftDiveTicks = 0;
                syncAnchorToServer(Integer.MIN_VALUE);
                prevClientX = player.getX();
                prevClientZ = player.getZ();
                return;
            }

            Vec3 dm = player.getDeltaMovement();
            // Amorti doux à la relâche de Shift pour se ré-ancrer net sous les pieds
            if (clientShiftDiveTicks > 0) {
                if (dm.y < -0.10) {
                    dm = new Vec3(dm.x, -0.05, dm.z);
                    player.setDeltaMovement(dm);
                }
                player.fallDistance = 0.0F;
                clientShiftDiveTicks = 0;
            }

            int exactUnderFeetY = Mth.floor(player.getY() - 1.0 + 1.0E-4);
            boolean jumpingInput = player.input != null && player.input.jumping;
            if ((jumpingInput && dm.y > 0.05) || dm.y > 0.22) {
                cloudAscentPending = true;
            }

            if (clientAnchorFloorY == Integer.MIN_VALUE) {
                clientAnchorFloorY = exactUnderFeetY;
                player.fallDistance = 0.0F;
            } else {
                // 1) Montée sur du VRAI relief solide (colline, escalier, bâtiment)
                if (player.onGround() && exactUnderFeetY > clientAnchorFloorY) {
                    BlockPos belowPos = BlockPos.containing(player.getX(), exactUnderFeetY, player.getZ());
                    BlockState belowState = level.getBlockState(belowPos);
                    if (!belowState.isAir() && !(belowState.is(Blocks.BARRIER) && CLIENT_BARRIER_TRAIL.containsKey(belowPos))) {
                        clientAnchorFloorY = exactUnderFeetY;
                    }
                }
                // 2) T333-AIRWALK-JUMP-ASCENT : Sauter normalement (Espace) ou en saut aérien fait monter le plancher d'air au sommet du saut
                if (cloudAscentPending && dm.y <= 0.02) {
                    if (exactUnderFeetY > clientAnchorFloorY) {
                        clientAnchorFloorY = exactUnderFeetY;
                    }
                    cloudAscentPending = false;
                }
                // 3) Si le joueur est descendu sous le plancher, ré-ancrage immédiat sous ses pieds
                if (player.getY() < clientAnchorFloorY + 0.96) {
                    clientAnchorFloorY = exactUnderFeetY;
                }
            }

            if (footPl <= 0 && cloudStride > 0 && clientAnchorFloorY < 159) {
                clientAnchorFloorY = 159;
                if (player.getY() < 160.0) {
                    prevClientX = player.getX();
                    prevClientZ = player.getZ();
                    return;
                }
            }

            // Purge immédiate de tout bloc d'un ancien étage Y avant de poser le niveau courant
            purgeClientBarriersExceptFloor(level, clientAnchorFloorY);
            syncAnchorToServer(clientAnchorFloorY);

            double frameVx = Double.isNaN(prevClientX) ? dm.x : (player.getX() - prevClientX);
            double frameVz = Double.isNaN(prevClientZ) ? dm.z : (player.getZ() - prevClientZ);
            // Utilise le vecteur le plus rapide entre deltaMovement prévu et déplacement effectif (100% compatible Speed X)
            double useVx = Math.abs(dm.x) >= Math.abs(frameVx) ? dm.x : frameVx;
            double useVz = Math.abs(dm.z) >= Math.abs(frameVz) ? dm.z : frameVz;

            placeClientBarrierRunway(level, player, clientAnchorFloorY, useVx, useVz);
            if (player.getY() <= clientAnchorFloorY + 6.0) {
                player.fallDistance = 0.0F;
            }

            prevClientX = player.getX();
            prevClientZ = player.getZ();
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            ClientLevel level = mc.level;
            if (player == null || level == null) {
                hasReleasedJumpKey = false;
                jumpsRemaining = 0;
                wallJumpCooldown = 0;
                return;
            }
            DeepEnchant.rememberClientAccess(level.registryAccess());
            if (wallJumpCooldown > 0) wallJumpCooldown--;

            // 1) Nettoyage client synchronisé (>= 4.0 blocs derrière / autour du joueur)
            cleanupClientBarriers(level, player);

            // 2) Triple Saut « Marcheur des Nuages » — machine à états exacte de CloudInABottleInputHandler (Artifacts 1.21.1)
            if (player.input == null) return;

            int cwLvl = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:cloud_walker");
            boolean hasQuintCombo = EnchantmentComboRegistry.hasCombo(player, EnchantmentComboRegistry.IDX_TRANSCENDANCE_CHANCE_INFINIE);
            boolean hasQuadCombo = hasQuintCombo || EnchantmentComboRegistry.hasCombo(player, EnchantmentComboRegistry.C02_ASCENSION_CELESTE);
            int maxAirJumps = hasQuintCombo ? 4 : (hasQuadCombo ? 3 : 2);

            if ((player.onGround() || player.onClimbable()) && !player.isInWater() && !player.isInLava()) {
                hasReleasedJumpKey = false;
                jumpsRemaining = maxAirJumps;
                return;
            }

            if (!player.input.jumping) {
                hasReleasedJumpKey = true;
                return;
            }

            if (player.getAbilities().flying || player.isFallFlying()
                    || player.isPassenger() || player.isInWater() || player.isInLava()
                    || !hasReleasedJumpKey) {
                return;
            }

            // Priorité Combo #1 : Acrobate Miel-Slime (wall_runner + bouncy) -> Wall-Jump élastique contre une paroi !
            if (wallJumpCooldown == 0
                    && EnchantmentComboRegistry.hasCombo(player, EnchantmentComboRegistry.C01_ACROBAT_ALCHIMIQUE)
                    && UniversalEnchantmentHandler.isTouchingHorizontalWall(player)) {
                wallJumpCooldown = 8;
                hasReleasedJumpKey = false;
                float dir = player.getYRot() * ((float) Math.PI / 180.0F);
                player.setDeltaMovement(-Mth.sin(dir) * 0.56, 0.62, Mth.cos(dir) * 0.56);
                player.hasImpulse = true;
                player.fallDistance = 0.0F;
                cloudAscentPending = true;
                PacketDistributor.sendToServer(new DeepPayloads.CloudJumpMessage(-1, false, false, player.getYRot()));
                return;
            }

            // Marcheur des Nuages I..III (Double Saut + Triple Saut x2 -> x5, ou Quadruple Saut avec Combo #2)
            if (cwLvl > 0 && jumpsRemaining > 0) {
                int currentJumpIdx = (maxAirJumps - jumpsRemaining) + 1;
                jumpsRemaining--;
                hasReleasedJumpKey = false;
                cloudAscentPending = true;

                float fwd = player.input.forwardImpulse;
                float left = player.input.leftImpulse;
                boolean movingHoriz = Math.abs(fwd) > 0.01F || Math.abs(left) > 0.01F;
                boolean sprinting = player.isSprinting();
                float moveYawDeg = player.getYRot();
                if (movingHoriz) {
                    float relDeg = (float) (Mth.atan2(-left, fwd) * (180.0D / Math.PI));
                    moveYawDeg = player.getYRot() + relDeg;
                }

                PacketDistributor.sendToServer(
                        new DeepPayloads.CloudJumpMessage(currentJumpIdx, movingHoriz, sprinting, moveYawDeg));
                UniversalEnchantmentHandler.applyCloudJumpPhysics(
                        player, cwLvl, currentJumpIdx, maxAirJumps, hasQuadCombo, movingHoriz, sprinting, moveYawDeg);
                spawnLocalCloudBurst(level, player, currentJumpIdx >= maxAirJumps, cwLvl);
            }
        } catch (Throwable ignored) {}
    }

    private static void syncAnchorToServer(int anchorY) {
        if (anchorY == lastSentAnchorFloorY) return;
        lastSentAnchorFloorY = anchorY;
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new DeepPayloads.AirWalkAnchorMessage(anchorY));
        }
    }

    private static boolean hasSolidGroundBelow(ClientLevel level, LocalPlayer player) {
        int underY = Mth.floor(player.getY() - 0.08);
        double px = player.getX();
        double pz = player.getZ();
        int minX = Mth.floor(px - 0.30);
        int maxX = Mth.floor(px + 0.30);
        int minZ = Mth.floor(pz - 0.30);
        int maxZ = Mth.floor(pz + 0.30);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, underY, z);
                BlockState st = level.getBlockState(pos);
                if (!st.isAir() && !(st.is(Blocks.BARRIER) && CLIENT_BARRIER_TRAIL.containsKey(pos))
                        && !st.getCollisionShape(level, pos).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void purgeClientBarriersExceptFloor(ClientLevel level, int keepFloorY) {
        if (CLIENT_BARRIER_TRAIL.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Long>> it = CLIENT_BARRIER_TRAIL.entrySet().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next().getKey();
            if (pos.getY() != keepFloorY) {
                if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BARRIER)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                it.remove();
            }
        }
    }

    private static void placeClientBarrierRunway(ClientLevel level, LocalPlayer player,
                                                 int footY, double vx, double vz) {
        if (footY < level.getMinBuildHeight() || footY >= level.getMaxBuildHeight()) return;
        if (footY + 1.0 > player.getY() + 0.04) return;

        long now = level.getGameTime();
        double px = player.getX();
        double pz = player.getZ();

        // T333-DIRECT-UNDER-FEET : Pose strictement sous la hitbox des pieds (+ le seul pas du tick courant),
        // jamais plusieurs blocs en avance (ne bloque plus ni la descente, ni le saut ascendant).
        double stepX = Mth.clamp(vx, -0.80, 0.80);
        double stepZ = Mth.clamp(vz, -0.80, 0.80);
        int minX = Mth.floor(Math.min(px, px + stepX) - 0.33);
        int maxX = Mth.floor(Math.max(px, px + stepX) + 0.33);
        int minZ = Mth.floor(Math.min(pz, pz + stepZ) - 0.33);
        int maxZ = Mth.floor(Math.max(pz, pz + stepZ) + 0.33);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setClientBarrier(level, x, footY, z, px, pz, now);
            }
        }
    }

    private static void setClientBarrier(ClientLevel level, int x, int y, int z,
                                         double px, double pz, long now) {
        double dx = px - (x + 0.5);
        double dz = pz - (z + 0.5);
        if ((dx * dx + dz * dz) >= 3.24) return;

        BlockPos pos = new BlockPos(x, y, z);
        BlockState cur = level.getBlockState(pos);
        if (cur.isAir()) {
            level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 2);
            CLIENT_BARRIER_TRAIL.put(pos, now);
        } else if (cur.is(Blocks.BARRIER) && CLIENT_BARRIER_TRAIL.containsKey(pos)) {
            CLIENT_BARRIER_TRAIL.put(pos, now);
        }
    }

    private static void cleanupClientBarriers(ClientLevel level, LocalPlayer player) {
        if (CLIENT_BARRIER_TRAIL.isEmpty()) return;
        int footPl = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:air_walk");
        int cloudStride = UniversalEnchantmentHandler.getArmorEnchantLvl(player, "deep_lucky_block:cloud_stride");
        boolean active = footPl > 0 || cloudStride > 0;
        long now = level.getGameTime();

        Iterator<Map.Entry<BlockPos, Long>> it = CLIENT_BARRIER_TRAIL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            BlockPos pos = entry.getKey();
            boolean mustRemove = !active || pos.getY() != clientAnchorFloorY;
            if (!mustRemove && (now - entry.getValue()) >= 2L) {
                double dx = player.getX() - (pos.getX() + 0.5);
                double dz = player.getZ() - (pos.getZ() + 0.5);
                if ((dx * dx + dz * dz) >= 2.56) {
                    mustRemove = true;
                }
            }
            if (mustRemove) {
                if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BARRIER)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                it.remove();
            }
        }
    }

    private static void clearAllClientBarriers(ClientLevel level) {
        if (level == null || CLIENT_BARRIER_TRAIL.isEmpty()) {
            CLIENT_BARRIER_TRAIL.clear();
            return;
        }
        for (BlockPos pos : CLIENT_BARRIER_TRAIL.keySet()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        CLIENT_BARRIER_TRAIL.clear();
    }

    private static void resetClientAirWalk(ClientLevel level, boolean notifyServer) {
        clearAllClientBarriers(level);
        clientAnchorFloorY = Integer.MIN_VALUE;
        lastSentAnchorFloorY = Integer.MIN_VALUE;
        cloudAscentPending = false;
        clientShiftDiveTicks = 0;
        prevClientX = Double.NaN;
        prevClientZ = Double.NaN;
        if (notifyServer) {
            syncAnchorToServer(Integer.MIN_VALUE);
        }
    }

    private static void spawnLocalCloudBurst(ClientLevel level, LocalPlayer player, boolean radiant, int cwLvl) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        int clampedLvl = Math.min(3, Math.max(1, cwLvl));
        // T360-NO-VANILLA-PARTICLES : coussin de nuage en VRAIS mini-cubes 3D (plus aucun sprite
        // vanilla) — un cœur dense qui s'écrase, puis un anneau qui s'évase doucement.
        int poofs = 8 + clampedLvl * 2;
        WeaponWorldVfxManager.spawnPixelPuff(0xE8F4FF, x, y + 0.02, z,
                poofs, 0.18f + 0.05f * clampedLvl, 0.05 + 0.02 * clampedLvl, 16);
        double r = (radiant ? 0.95 : 0.72) + 0.22 * (clampedLvl - 1);
        int ring = (radiant ? 14 : 10) + (clampedLvl - 1) * 4;
        for (int i = 0; i < ring; i++) {
            double ang = (Math.PI * 2.0 * i) / ring;
            WeaponWorldVfxManager.spawnPixelPuff(radiant ? 0xFFF6DC : 0xDFF1FF,
                    x + Math.cos(ang) * r * 0.45, y - 0.04, z + Math.sin(ang) * r * 0.45,
                    1, 0.03f, 0.02 + 0.008 * clampedLvl, 18);
        }
    }
}
