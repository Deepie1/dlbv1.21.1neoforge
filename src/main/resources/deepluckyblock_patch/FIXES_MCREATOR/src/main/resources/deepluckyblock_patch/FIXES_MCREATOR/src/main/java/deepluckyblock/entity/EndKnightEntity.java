package deepluckyblock.entity;

import deepluckyblock.init.DeepLuckyBlockModEntities;
import deepluckyblock.enchantments.PixelFx;

import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.List;

@SuppressWarnings({"removal", "deprecation"})
public class EndKnightEntity extends Monster {

    private static final float HITBOX_WIDTH = 1.2F;
    private static final float HITBOX_HEIGHT = 4.0F;
    private static final EntityDimensions ENTITY_DIMENSIONS = EntityDimensions.fixed(HITBOX_WIDTH, HITBOX_HEIGHT);

    private static final EntityDataAccessor<Boolean> DATA_IS_ATTACKING =
            SynchedEntityData.defineId(EndKnightEntity.class, EntityDataSerializers.BOOLEAN);

    public final AnimationState animationState0 = new AnimationState();
    public final AnimationState animationState1 = new AnimationState();
    public final AnimationState animationState2 = new AnimationState();
    public final AnimationState animationState3 = new AnimationState();

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_ATTACKING, false);
    }

    public boolean isAttacking() { return this.entityData.get(DATA_IS_ATTACKING); }
    public void setAttacking(boolean v) { this.entityData.set(DATA_IS_ATTACKING, v); }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.isDeadOrDying()) {
                this.animationState0.stop();
                this.animationState2.stop();
                this.animationState3.stop();
                this.animationState1.animateWhen(true, this.tickCount);
            } else {
                this.animationState1.stop();

                // 1) Animation d'attaque : jouée en continu dès que l'entité attaque, MÊME SI ELLE SE DÉPLACE
                if (this.isAttacking()) {
                    this.animationState0.animateWhen(true, this.tickCount);
                } else {
                    this.animationState0.stop();
                }

                // 2) Animation de déplacement (marche / course) : jouée en parallèle du combat
                if (this.walkAnimation.isMoving() || this.getDeltaMovement().horizontalDistanceSqr() > 0.001D) {
                    if (this.walkAnimation.speed() > 0.35F) {
                        this.animationState2.stop();
                        this.animationState3.animateWhen(true, this.tickCount);
                    } else {
                        this.animationState3.stop();
                        this.animationState2.animateWhen(true, this.tickCount);
                    }
                } else {
                    this.animationState2.stop();
                    this.animationState3.stop();
                }
            }
        }
    }

    private int teleportCooldown = 0;
    private int playerTeleportCooldown = 200;
    private int attackAnimTimer = 0;
    private int attackCooldown = 0;
    private int combatCycle = 0;
    private static final int COMBAT_CYCLE_DURATION = 60;

    private static final int ATTACK_ANIM_DURATION = 16;
    private static final int ATTACK_HIT_TIMING = 8;
    private static final int NORMAL_ATTACK_COOLDOWN_MIN = 3;
    private static final int NORMAL_ATTACK_COOLDOWN_MAX = 8;
    private static final double ATTACK_START_DISTANCE = 2.4;
    private static final double ATTACK_HIT_DISTANCE = 2.2;

    private static final int DEATH_ANIM_END = 20;
    private static final int DEATH_STAY_ON_GROUND = 20;
    private static final int TOTAL_DEATH_TIME = DEATH_ANIM_END + DEATH_STAY_ON_GROUND;

    private static final double NORMAL_SPEED_MULTIPLIER = 0.7;
    private static final double SPRINT_SPEED_MULTIPLIER = 1.0;
    private static final double ATTACK_SPEED_MULTIPLIER = NORMAL_SPEED_MULTIPLIER - 0.25; // 0.45 (-0.25 sur la vitesse normale moyenne)
    private static final double RUN_ANIM_THRESHOLD = 0.20;
    private static final double SPRINT_DISTANCE = 30.0;

    private static final double TELEPORT_MIN_DISTANCE = 6.0;
    private static final double TELEPORT_MAX_DISTANCE = 12.0;
    private static final int TELEPORT_HIT_COOLDOWN = 20;
    private static final int TELEPORT_HIT_CHANCE = 50;

    private enum RagePhase {
        TELEPORT_AND_STRIKE,
        WAIT_BEFORE_RETREAT,
        WAIT_BEFORE_STRIKE
    }

    private LivingEntity rageTarget = null;
    private int rageTimer = 0;
    private RagePhase ragePhase = RagePhase.TELEPORT_AND_STRIKE;
    private int ragePhaseTimer = 0;
    private static final int RAGE_DURATION = 200;
    private static final int RAGE_RETREAT_WAIT_MIN = 3;
    private static final int RAGE_RETREAT_WAIT_MAX = 10;
    private static final int RAGE_STRIKE_WAIT_MIN = 3;
    private static final int RAGE_STRIKE_WAIT_MAX = 15;
    private static final double RAGE_CLOSE_TELEPORT_MIN = 1.5;
    private static final double RAGE_CLOSE_TELEPORT_MAX = 3.0;
    private static final int PLAYER_TELEPORT_INTERVAL = 200;

    private LivingEntity secondaryTarget = null;
    private int secondaryTargetCheckCooldown = 0;
    private static final int SECONDARY_TARGET_CHECK_INTERVAL = 200;
    private static final double SECONDARY_TARGET_SEARCH_RADIUS = 15.0;


    public EndKnightEntity(EntityType<EndKnightEntity> type, Level world) {
        super(type, world);
        xpReward = 45;
        setNoAi(false);
        this.refreshDimensions();
    }

    // PORT 1.21.1 : getDimensions(Pose) est final sur LivingEntity -> dimensions via EntityType.sized().

    // [1.21.1] getStandingEyeHeight(Pose,EntityDimensions) supprime : hauteur des yeux
    // derivee automatiquement des dimensions (3.6F).

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox();
    }

    @Override
    protected AABB makeBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        double halfWidth = HITBOX_WIDTH / 2.0D;
        return new AABB(
            x - halfWidth, y, z - halfWidth,
            x + halfWidth, y + HITBOX_HEIGHT, z + halfWidth
        );
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }


    @Override
    public SoundEvent getAmbientSound() {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("minecraft", "entity.enderman.ambient"));
    }

    @Override
    public void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("minecraft", "entity.enderman.stare")), 0.15f, 1);
    }

    @Override
    public SoundEvent getHurtSound(DamageSource ds) {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("minecraft", "entity.enderman.hurt"));
    }

    @Override
    public SoundEvent getDeathSound() {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("minecraft", "entity.enderman.death"));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return false;

        this.setAttacking(true);
        attackAnimTimer = ATTACK_ANIM_DURATION;

        float damage = 4.0F + this.random.nextInt(7);
        boolean hit = livingTarget.hurt(this.damageSources().mobAttack(this), damage);
        if (hit) {
            this.setLastHurtMob(livingTarget);
            double knockback = this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
            if (knockback > 0.0D) {
                float yawRadians = this.getYRot() * ((float) Math.PI / 180F);
                livingTarget.knockback(knockback,
                        net.minecraft.util.Mth.sin(yawRadians),
                        -net.minecraft.util.Mth.cos(yawRadians));
            }
        }
        return hit;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;

        if (this.deathTime >= TOTAL_DEATH_TIME) {
            if (this.level() instanceof ServerLevel serverLevel) {
                // T360-NO-VANILLA-PARTICLES : colonne de mort en mini-cubes 3D violets.
                PixelFx.send(serverLevel, ParticleTypes.PORTAL,
                        this.getX(), this.getY() + 1.2D, this.getZ(),
                        12, 1.1D, 1.8D, 1.1D, 0.06D);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);

        RandomSource random = this.random;

        int netheriteBlocks = random.nextInt(3);
        if (netheriteBlocks > 0) this.spawnAtLocation(new ItemStack(Items.NETHERITE_BLOCK, netheriteBlocks));

        int goldenApples = random.nextInt(4);
        if (goldenApples > 0) this.spawnAtLocation(new ItemStack(Items.GOLDEN_APPLE, goldenApples));

        int notchApples = random.nextInt(3);
        if (notchApples > 0) this.spawnAtLocation(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, notchApples));

        int luckyBlocks = random.nextInt(4);
        if (luckyBlocks > 0) {
            net.minecraft.world.item.Item luckyBlock = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("deep_lucky_block", "deep_lucky_block")
            );
            if (luckyBlock != null) {
                this.spawnAtLocation(new ItemStack(luckyBlock, luckyBlocks));
            }
        }

        int enderPearls = random.nextInt(6);
        if (enderPearls > 0) this.spawnAtLocation(new ItemStack(Items.ENDER_PEARL, enderPearls));

        int eyesOfEnder = random.nextInt(4);
        if (eyesOfEnder > 0) this.spawnAtLocation(new ItemStack(Items.ENDER_EYE, eyesOfEnder));
    }

    @Override
    protected boolean shouldDropLoot() {
        return true;
    }

    @Override
    public boolean checkSpawnRules(net.minecraft.world.level.LevelAccessor level, MobSpawnType spawnReason) {
        if (spawnReason == MobSpawnType.SPAWN_EGG
            || spawnReason == MobSpawnType.SPAWNER
            || spawnReason == MobSpawnType.COMMAND
            || spawnReason == MobSpawnType.MOB_SUMMONED
            || spawnReason == MobSpawnType.CONVERSION
            || spawnReason == MobSpawnType.STRUCTURE
            || spawnReason == MobSpawnType.EVENT
            || spawnReason == MobSpawnType.BUCKET
            || spawnReason == MobSpawnType.DISPENSER
            || spawnReason == MobSpawnType.TRIGGERED) {
            return super.checkSpawnRules(level, spawnReason);
        }
        return false;
    }

    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return super.checkSpawnObstruction(level);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)) {
            return super.hurt(source, amount);
        }

        if (!this.level().isClientSide() && source.getEntity() instanceof LivingEntity attacker) {
            if (attacker != this && !(attacker instanceof EndKnightEntity)) {
                startRage(attacker);
            }
        }

        if (!this.level().isClientSide()
                && teleportCooldown <= 0
                && this.getRandom().nextInt(100) < TELEPORT_HIT_CHANCE) {

            if (source.getEntity() instanceof LivingEntity attacker) {
                if (teleportAroundEntity(attacker)) {
                    teleportCooldown = TELEPORT_HIT_COOLDOWN;
                }
            } else {
                if (teleport()) {
                    teleportCooldown = TELEPORT_HIT_COOLDOWN;
                }
            }
        }

        return super.hurt(source, amount);
    }

    private void startRage(LivingEntity attacker) {
        boolean startNewCycle = rageTimer <= 0 || rageTarget != attacker;
        rageTarget = attacker;
        rageTimer = RAGE_DURATION;
        if (startNewCycle) {
            ragePhase = RagePhase.TELEPORT_AND_STRIKE;
            ragePhaseTimer = 0;
        }
    }

    public boolean teleportAroundEntity(LivingEntity target) {
        if (this.level().isClientSide() || !this.isAlive() || target == null) return false;

        for (int i = 0; i < 10; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double distance = TELEPORT_MIN_DISTANCE + this.random.nextDouble()
                    * (TELEPORT_MAX_DISTANCE - TELEPORT_MIN_DISTANCE);

            double targetX = target.getX() + Math.cos(angle) * distance;
            double targetY = target.getY();
            double targetZ = target.getZ() + Math.sin(angle) * distance;

            if (this.teleport(targetX, targetY, targetZ)) {
                return true;
            }
        }

        return this.teleport();
    }

    private boolean teleportCloseToEntity(LivingEntity target) {
        if (this.level().isClientSide() || !this.isAlive() || target == null) return false;

        for (int i = 0; i < 12; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = RAGE_CLOSE_TELEPORT_MIN + this.random.nextDouble()
                    * (RAGE_CLOSE_TELEPORT_MAX - RAGE_CLOSE_TELEPORT_MIN);
            double targetX = target.getX() + Math.cos(angle) * distance;
            double targetZ = target.getZ() + Math.sin(angle) * distance;
            if (this.teleport(targetX, target.getY(), targetZ)) return true;
        }
        return false;
    }

    private int randomBetweenInclusive(int minimum, int maximum) {
        return minimum + this.random.nextInt(maximum - minimum + 1);
    }

    private void handleRageCombat(LivingEntity target) {
        if (ragePhaseTimer > 0) {
            ragePhaseTimer--;
            return;
        }

        switch (ragePhase) {
            case TELEPORT_AND_STRIKE -> {
                boolean closeEnough = teleportCloseToEntity(target)
                        || this.distanceTo(target) <= ATTACK_HIT_DISTANCE;
                if (!closeEnough) {
                    double speed = this.isAttacking() ? ATTACK_SPEED_MULTIPLIER : SPRINT_SPEED_MULTIPLIER;
                    this.getNavigation().moveTo(target, speed);
                    ragePhaseTimer = 5;
                    return;
                }

                faceTarget(target);
                attackAnimTimer = ATTACK_ANIM_DURATION;
                this.setAttacking(true);
                this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (this.distanceTo(target) <= ATTACK_HIT_DISTANCE) {
                    this.doHurtTarget(target);
                }
                ragePhase = RagePhase.WAIT_BEFORE_RETREAT;
                ragePhaseTimer = randomBetweenInclusive(
                        RAGE_RETREAT_WAIT_MIN, RAGE_RETREAT_WAIT_MAX);
            }
            case WAIT_BEFORE_RETREAT -> {
                teleportAroundEntity(target);
                ragePhase = RagePhase.WAIT_BEFORE_STRIKE;
                ragePhaseTimer = randomBetweenInclusive(
                        RAGE_STRIKE_WAIT_MIN, RAGE_STRIKE_WAIT_MAX);
            }
            case WAIT_BEFORE_STRIKE -> ragePhase = RagePhase.TELEPORT_AND_STRIKE;
        }
    }

    private void handlePeriodicPlayerTeleport() {
        if (rageTimer > 0 || playerTeleportCooldown > 0) return;

        Player nearestPlayer = this.level().getNearestPlayer(this, 100.0);
        if (nearestPlayer != null) {
            teleportCloseToEntity(nearestPlayer);
        }
        playerTeleportCooldown = PLAYER_TELEPORT_INTERVAL;
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float targetYaw = (float) (Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        this.setYRot(this.rotlerp(this.getYRot(), targetYaw, 20.0F));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    private LivingEntity getSmartTarget() {
        if (rageTimer > 0 && rageTarget != null && rageTarget.isAlive()) {
            if (this.distanceTo(rageTarget) <= 100.0) {
                return rageTarget;
            }
        }

        Player nearestPlayer = this.level().getNearestPlayer(this, 100.0);
        double playerDistance = nearestPlayer != null
            ? this.distanceTo(nearestPlayer)
            : Double.MAX_VALUE;

        if (secondaryTarget != null) {
            if (!secondaryTarget.isAlive() || this.distanceTo(secondaryTarget) > 20.0) {
                secondaryTarget = null;
            }
        }

        if (secondaryTargetCheckCooldown <= 0) {
            secondaryTargetCheckCooldown = SECONDARY_TARGET_CHECK_INTERVAL;
            searchForSecondaryTarget();
        }

        if (secondaryTarget != null && secondaryTarget.isAlive()) {
            double secondaryDistance = this.distanceTo(secondaryTarget);
            if (secondaryDistance < playerDistance) {
                return secondaryTarget;
            }
        }

        return nearestPlayer;
    }

    private void searchForSecondaryTarget() {
        AABB searchBox = this.getBoundingBox().inflate(SECONDARY_TARGET_SEARCH_RADIUS);
        List<LivingEntity> nearbyMobs = this.level().getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            e -> e != this
                && !(e instanceof Player)
                && !(e instanceof EndKnightEntity)
                && e.isAlive()
                && this.hasLineOfSight(e)
        );

        if (nearbyMobs.isEmpty()) {
            secondaryTarget = null;
            return;
        }

        if (this.random.nextInt(100) < 30) {
            secondaryTarget = nearbyMobs.get(this.random.nextInt(nearbyMobs.size()));
        }
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide()
                && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            // T360-NO-VANILLA-PARTICLES : aura du Chevalier de l'End en mini-cubes 3D violets
            // (plus aucune particule PORTAL vanilla), avec la même silhouette en colonne.
            deepluckyblock.client.WeaponWorldVfxManager.spawnPixelPuff(0xA55BFF,
                    this.getRandomX(0.55D), this.getY() + this.random.nextDouble() * 2.0D,
                    this.getRandomZ(0.55D), 3, 0.12f, 0.02, 16);
            deepluckyblock.client.WeaponWorldVfxManager.spawnPixelPuff(0x8E3BE8,
                    this.getRandomX(0.45D),
                    this.getY() + this.getBbHeight() + 0.4D + this.random.nextDouble() * 0.6D,
                    this.getRandomZ(0.45D), 2, 0.10f, 0.015, 18);
        }

        this.jumping = false;

        if (teleportCooldown > 0) teleportCooldown--;
        if (playerTeleportCooldown > 0) playerTeleportCooldown--;
        if (attackAnimTimer > 0) {
            attackAnimTimer--;
            if (attackAnimTimer <= 0) {
                this.setAttacking(false);
            }
        }
        if (attackCooldown > 0) attackCooldown--;
        if (secondaryTargetCheckCooldown > 0) secondaryTargetCheckCooldown--;

        if (rageTimer > 0) {
            rageTimer--;
            if (rageTimer <= 0) {
                rageTarget = null;
                ragePhase = RagePhase.TELEPORT_AND_STRIKE;
                ragePhaseTimer = 0;
            }
        }

        combatCycle++;
        if (combatCycle >= COMBAT_CYCLE_DURATION) combatCycle = 0;

        if (!this.level().isClientSide()) {
            if (this.isInWaterRainOrBubble()) {
                this.hurt(this.damageSources().drown(), 1.0F);

                if (teleportCooldown <= 0) {
                    if (this.teleport()) {
                        teleportCooldown = 40;
                    }
                }
            }

            handlePeriodicPlayerTeleport();

            LivingEntity target = getSmartTarget();
            boolean isRageMode = rageTimer > 0 && target == rageTarget;

            if (target != null && target.isAlive()) {
                if (isRageMode) {
                    handleRageCombat(target);
                } else {
                    faceTarget(target);
                    double distanceToTarget = this.distanceTo(target);
                    boolean shouldSprint = distanceToTarget > SPRINT_DISTANCE;
                    double moveSpeed;
                    if (this.isAttacking()) {
                        moveSpeed = ATTACK_SPEED_MULTIPLIER; // 0.7 - 0.25 = 0.45 (-0.25 sur la vitesse normale moyenne)
                    } else if (shouldSprint) {
                        moveSpeed = SPRINT_SPEED_MULTIPLIER;
                    } else {
                        moveSpeed = NORMAL_SPEED_MULTIPLIER; // 0.70 (vitesse normale moyenne)
                    }

                    if (distanceToTarget > 1.8) {
                        this.getNavigation().moveTo(target, moveSpeed);
                    }

                    if (distanceToTarget <= ATTACK_START_DISTANCE && attackCooldown <= 0) {
                        attackAnimTimer = ATTACK_ANIM_DURATION;
                        this.setAttacking(true);
                        attackCooldown = randomBetweenInclusive(
                                NORMAL_ATTACK_COOLDOWN_MIN, NORMAL_ATTACK_COOLDOWN_MAX);
                        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    }

                    if (attackAnimTimer == (ATTACK_ANIM_DURATION - ATTACK_HIT_TIMING)
                            && distanceToTarget <= ATTACK_HIT_DISTANCE) {
                        this.doHurtTarget(target);
                    }
                }
            }
        }

        super.aiStep();
    }

    private float rotlerp(float current, float target, float maxDelta) {
        float delta = net.minecraft.util.Mth.wrapDegrees(target - current);
        if (delta > maxDelta) delta = maxDelta;
        if (delta < -maxDelta) delta = -maxDelta;
        return current + delta;
    }

    public boolean teleport() {
        if (this.level().isClientSide() || !this.isAlive()) return false;

        double targetX = this.getX() + (this.random.nextDouble() - 0.5D) * 64.0D;
        double targetY = this.getY() + (double) (this.random.nextInt(64) - 32);
        double targetZ = this.getZ() + (this.random.nextDouble() - 0.5D) * 64.0D;

        return this.teleport(targetX, targetY, targetZ);
    }

    public boolean teleport(double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);

        while (pos.getY() > this.level().getMinBuildHeight()
                && !this.level().getBlockState(pos).blocksMotion()) {
            pos.move(net.minecraft.core.Direction.DOWN);
        }

        BlockState blockState = this.level().getBlockState(pos);
        boolean isSolidGround = blockState.blocksMotion();
        boolean isNotWater = !blockState.getFluidState().is(net.minecraft.tags.FluidTags.WATER);

        if (isSolidGround && isNotWater) {
            net.minecraft.world.phys.Vec3 originalPos = this.position();
            double safeX = pos.getX() + 0.5D;
            double safeY = pos.getY() + 1.0D;
            double safeZ = pos.getZ() + 0.5D;
            boolean teleportSuccess = this.randomTeleport(safeX, safeY, safeZ, true);

            if (teleportSuccess) {
                this.level().gameEvent(net.minecraft.world.level.gameevent.GameEvent.TELEPORT, originalPos,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(this));

                if (!this.isSilent()) {
                    this.level().playSound(null,
                        this.xo, this.yo, this.zo,
                        SoundEvents.ENDERMAN_TELEPORT,
                        this.getSoundSource(),
                        1.0F, 1.0F);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                }

                if (this.level() instanceof ServerLevel serverLevel) {
                    // T360-NO-VANILLA-PARTICLES : les deux nuages de téléportation deviennent deux
                    // salves de mini-cubes 3D (plus aucune particule PORTAL vanilla).
                    PixelFx.send(serverLevel, ParticleTypes.PORTAL,
                            originalPos.x, originalPos.y + 1.2D, originalPos.z,
                            12, 1.1D, 1.8D, 1.1D, 0.06D);
                    PixelFx.send(serverLevel, ParticleTypes.PORTAL,
                            this.getX(), this.getY() + 1.2D, this.getZ(),
                            12, 1.1D, 1.8D, 1.1D, 0.06D);
                }
            }

            return teleportSuccess;
        }

        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
    }

    public static void init() {
        // PORT 1.21.1 : spawn placement enregistre dans SpawnPlacementRegistration
        // (event RegisterSpawnPlacementsEvent).
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.5);
        builder = builder.add(Attributes.MAX_HEALTH, 70);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 4);
        builder = builder.add(Attributes.FOLLOW_RANGE, 100);
        builder = builder.add(Attributes.ATTACK_KNOCKBACK, 0.5);
        return builder;
    }
}