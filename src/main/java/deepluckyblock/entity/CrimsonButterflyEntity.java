package deepluckyblock.entity;

import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

import deepluckyblock.init.DeepLuckyBlockModEntities;

import java.util.EnumSet;

public class CrimsonButterflyEntity extends PathfinderMob {

    public final AnimationState animationState0 = new AnimationState();

    // === SYSTEME DE TAILLE VANILLA SLIME (fonctionne à coup sûr) ===
    // Entier synchronisé client/serveur via EntityDataAccessor
    private static final EntityDataAccessor<Integer> DATA_SIZE_ID =
            SynchedEntityData.defineId(CrimsonButterflyEntity.class, EntityDataSerializers.INT);

    // Animation offset pour désynchroniser les papillons
    private static final EntityDataAccessor<Integer> DATA_ANIM_OFFSET =
            SynchedEntityData.defineId(CrimsonButterflyEntity.class, EntityDataSerializers.INT);

    // === PROFILS DE VOL ===
    private int flightHeight = 3;
    private float skimmingChance = 0.02F;
    private int restIntervalMin = 1200;
    private int restIntervalMax = 1800;


    public CrimsonButterflyEntity(EntityType<CrimsonButterflyEntity> type, Level world) {
        super(type, world);
        xpReward = 0;
        setNoAi(false);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    /**
     * ⚠️ VANILLA SLIME : initialisation de DATA_SIZE_ID
     */
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        // PORT 1.21.1 : define passe sur le Builder (plus d'instance entityData.define).
        builder.define(DATA_SIZE_ID, 1);  // taille par défaut = 1
        builder.define(DATA_ANIM_OFFSET, 0);
    }

    /**
     * ⚠️ VANILLA SLIME : setSize change DATA_SIZE_ID et refreshDimensions
     * Appelé uniquement côté serveur via onAddedToLevel
     */
    protected void setSize(int size, boolean resetHealth) {
        this.entityData.set(DATA_SIZE_ID, size);
        this.refreshDimensions();
        this.setYRot(this.getYRot());
        this.yBodyRot = this.getYRot();
        if (resetHealth) {
            this.setHealth(this.getMaxHealth());
        }
    }

    /**
     * ⚠️ VANILLA SLIME : retourne la taille
     */
    public int getSize() {
        return this.entityData.get(DATA_SIZE_ID);
    }

    /**
     * Génère une taille random au spawn (côté serveur)
     * ET génère le profil de vol ET l'offset d'animation
     */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!this.level().isClientSide()) {
            // Taille random entre 1 et 3 (entier pour vanilla Slime style)
            int size = 1 + this.getRandom().nextInt(3); // 1, 2 ou 3
            this.setSize(size, false);

            // Offset d'animation
            this.entityData.set(DATA_ANIM_OFFSET, this.getRandom().nextInt(100));

            // Profil de vol
            int profileRoll = this.getRandom().nextInt(100);
            if (profileRoll < 25) {
                // Raseteur
                this.flightHeight = 1;
                this.skimmingChance = 0.10F;
                this.restIntervalMin = 1800;
                this.restIntervalMax = 2400;
            } else if (profileRoll < 75) {
                // Moyen
                this.flightHeight = 3;
                this.skimmingChance = 0.02F;
                this.restIntervalMin = 1200;
                this.restIntervalMax = 1800;
            } else {
                // Haut
                this.flightHeight = 5;
                this.skimmingChance = 0.0F;
                this.restIntervalMin = 2000;
                this.restIntervalMax = 3000;
            }
        }
    }

    public int getAnimationOffset() {
        return this.entityData.get(DATA_ANIM_OFFSET);
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, world);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ButterflyFlightGoal(this));
    }


    @Override
    protected SoundEvent getAmbientSound() {
        return BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", "entity.parrot.fly")
        );
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource ds) {
        return BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", "entity.parrot.hurt")
        );
    }

    @Override
    protected SoundEvent getDeathSound() {
        return BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", "entity.generic.death")
        );
    }

    @Override
    public boolean causeFallDamage(float l, float d, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    @Override
    public void setNoGravity(boolean ignored) {
        super.setNoGravity(true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.setNoGravity(true);
        if (this.level().isClientSide()) {
            this.animationState0.animateWhen(true, this.tickCount);
        }
    }

    @Override
    protected float getFlyingSpeed() {
        return 0.3F;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void push(Entity entity) {
        // vide : pas de push
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    /**
     * ⚠️ VANILLA SLIME : getDimensions() utilise scale()
     * size=1 → 0.25, size=2 → 0.5, size=3 → 0.75
     */
    // PORT 1.21.1 : getDimensions(Pose) est final sur LivingEntity -> dimensions via EntityType.sized().

    // PORT 1.21.1 : canChangeDimensions(Level, Level).
    @Override
    public boolean canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public static boolean checkCrimsonButterflySpawnRules(EntityType<? extends PathfinderMob> type,
                                                     LevelAccessor level,
                                                     MobSpawnType spawnType,
                                                     BlockPos pos,
                                                     net.minecraft.util.RandomSource random) {
        return spawnType == MobSpawnType.COMMAND
                || spawnType == MobSpawnType.SPAWN_EGG
                || spawnType == MobSpawnType.EVENT
                || spawnType == MobSpawnType.MOB_SUMMONED
                || spawnType == MobSpawnType.SPAWNER;
    }

    public static void init() {
        // PORT 1.21.1 : spawn placement enregistre dans SpawnPlacementRegistration.
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3D);
        builder = builder.add(Attributes.MAX_HEALTH, 6.0D);
        builder = builder.add(Attributes.ARMOR, 0.0D);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 0.0D);
        builder = builder.add(Attributes.FOLLOW_RANGE, 16.0D);
        builder = builder.add(Attributes.FLYING_SPEED, 0.4D);
        return builder;
    }

    // ========================================================================
    // GOAL UNIQUE : vol + eau + orientation + pose rare + profils
    // ========================================================================
    static class ButterflyFlightGoal extends Goal {
        private final CrimsonButterflyEntity butterfly;
        private int directionChangeTimer = 0;
        private int waterCheckTimer = 0;
        private int restTimer = 0;
        private double targetVelX = 0;
        private double targetVelY = 0;
        private double targetVelZ = 0;
        private boolean returningToWater = false;
        private BlockPos waterTarget = null;
        private boolean isResting = false;
        private int restTicks = 0;
        private int nextRestCheck = 0;

        ButterflyFlightGoal(CrimsonButterflyEntity butterfly) {
            this.butterfly = butterfly;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }

        @Override
        public void start() {
            this.nextRestCheck = this.butterfly.restIntervalMin
                    + this.butterfly.getRandom().nextInt(
                            this.butterfly.restIntervalMax - this.butterfly.restIntervalMin);
            pickNewDirection();
        }

        @Override
        public void tick() {
            this.directionChangeTimer--;
            this.waterCheckTimer++;
            this.restTimer++;

            // === POSE RARE ===
            if (!isResting && this.restTimer >= this.nextRestCheck && this.isNearWater()) {
                this.isResting = true;
                this.restTicks = 40 + this.butterfly.getRandom().nextInt(40);
                this.butterfly.getNavigation().stop();
                this.butterfly.setDeltaMovement(0, 0, 0);
                this.targetVelX = 0;
                this.targetVelY = 0;
                this.targetVelZ = 0;
            }

            if (this.isResting) {
                this.restTicks--;
                this.butterfly.animationState0.stop();
                if (this.restTicks <= 0) {
                    this.isResting = false;
                    this.restTimer = 0;
                    this.nextRestCheck = this.butterfly.restIntervalMin
                            + this.butterfly.getRandom().nextInt(
                                    this.butterfly.restIntervalMax - this.butterfly.restIntervalMin);
                    pickNewDirection();
                }
                return;
            }

            // === EAU LOINTAINE ===
            if (this.waterCheckTimer >= 30) {
                this.waterCheckTimer = 0;
                this.returningToWater = isTooFarFromWater();
                if (this.returningToWater) {
                    this.waterTarget = findNearestWater();
                }
            }

            if (this.returningToWater && this.waterTarget != null) {
                double tx = this.waterTarget.getX() + 0.5;
                double ty = this.waterTarget.getY() + 2.0;
                double tz = this.waterTarget.getZ() + 0.5;
                double dx = tx - this.butterfly.getX();
                double dy = ty - this.butterfly.getY();
                double dz = tz - this.butterfly.getZ();
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (dist > 0.1) {
                    double speed = 0.18;
                    this.targetVelX = (dx / dist) * speed;
                    this.targetVelY = (dy / dist) * speed * 0.5;
                    this.targetVelZ = (dz / dist) * speed;
                } else {
                    this.returningToWater = false;
                    pickNewDirection();
                }
            } else {
                if (this.directionChangeTimer <= 0) {
                    pickNewDirection();
                }
            }

            // === GARDE-FOU Y (utilise le profil) ===
            int surfaceY = findSurfaceY();
            if (surfaceY > 0) {
                double currentY = this.butterfly.getY();
                double baseHeight = this.butterfly.flightHeight;
                double minY = surfaceY + baseHeight;
                double maxY = surfaceY + baseHeight + 3.0;

                if (this.butterfly.getRandom().nextFloat() < 0.05F) {
                    maxY = surfaceY + baseHeight + 5.0;
                }

                if (this.butterfly.getRandom().nextFloat() < this.butterfly.skimmingChance
                        && this.isOverWater()) {
                    minY = surfaceY + 0.3;
                }

                if (currentY < minY) {
                    this.targetVelY = Math.max(this.targetVelY, 0.05);
                } else if (currentY > maxY) {
                    this.targetVelY = Math.min(this.targetVelY, -0.05);
                }
            }

            // === VELOCITÉ LISSÉE ===
            Vec3 current = this.butterfly.getDeltaMovement();
            double newVelX = current.x * 0.6 + this.targetVelX * 0.4;
            double newVelY = current.y * 0.6 + this.targetVelY * 0.4;
            double newVelZ = current.z * 0.6 + this.targetVelZ * 0.4;

            this.butterfly.setDeltaMovement(newVelX, newVelY, newVelZ);

            // === ORIENTATION (avec +180° pour la rotation du modèle) ===
            double horizSpeed = Math.sqrt(newVelX * newVelX + newVelZ * newVelZ);
            if (horizSpeed > 0.02) {
                float targetYaw = (float) Math.toDegrees(Math.atan2(newVelZ, newVelX)) - 90.0F + 180.0F;
                float currentYaw = this.butterfly.getYRot();
                float diffYaw = targetYaw - currentYaw;
                while (diffYaw > 180) diffYaw -= 360;
                while (diffYaw < -180) diffYaw += 360;
                this.butterfly.setYRot(currentYaw + diffYaw * 0.15F);
                this.butterfly.yBodyRot = this.butterfly.getYRot();
                this.butterfly.yHeadRot = this.butterfly.getYRot();
            }

            this.butterfly.animationState0.animateWhen(true, this.butterfly.tickCount);
        }

        private void pickNewDirection() {
            double angle = this.butterfly.getRandom().nextDouble() * 2 * Math.PI;
            double pitch = (this.butterfly.getRandom().nextDouble() - 0.5) * 0.3;
            double speed = 0.1 + this.butterfly.getRandom().nextDouble() * 0.06;
            this.targetVelX = Math.cos(angle) * speed;
            this.targetVelZ = Math.sin(angle) * speed;
            this.targetVelY = Math.sin(pitch) * speed * 0.4;
            this.directionChangeTimer = 30 + this.butterfly.getRandom().nextInt(20);
        }

        private boolean isTooFarFromWater() {
            BlockPos pos = this.butterfly.blockPosition();
            for (int r = 10; r >= 0; r -= 2) {
                for (int dx = -r; dx <= r; dx += 2) {
                    for (int dz = -r; dz <= r; dz += 2) {
                        BlockPos check = pos.offset(dx, 0, dz);
                        if (this.butterfly.level().getFluidState(check).is(FluidTags.WATER)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private boolean isNearWater() {
            BlockPos pos = this.butterfly.blockPosition();
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos check = pos.offset(dx, -1, dz);
                    if (this.butterfly.level().getFluidState(check).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isOverWater() {
            BlockPos below = this.butterfly.blockPosition().below();
            return this.butterfly.level().getFluidState(below).is(FluidTags.WATER);
        }

        private BlockPos findNearestWater() {
            BlockPos origin = this.butterfly.blockPosition();
            BlockPos nearest = null;
            double bestDist = Double.MAX_VALUE;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

            for (int dx = -15; dx <= 15; dx++) {
                for (int dz = -15; dz <= 15; dz++) {
                    for (int dy = -3; dy <= 5; dy++) {
                        cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (this.butterfly.level().getFluidState(cursor).is(FluidTags.WATER)) {
                            double d = this.butterfly.distanceToSqr(
                                    cursor.getX() + 0.5, cursor.getY() + 1, cursor.getZ() + 0.5);
                            if (d < bestDist) {
                                bestDist = d;
                                nearest = cursor.immutable();
                            }
                        }
                    }
                }
            }
            return nearest;
        }

        private int findSurfaceY() {
            BlockPos pos = this.butterfly.blockPosition();
            for (int y = pos.getY(); y > this.butterfly.level().getMinBuildHeight(); y--) {
                BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
                var state = this.butterfly.level().getBlockState(check);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    return y + 1;
                }
                if (this.butterfly.level().getFluidState(check).is(FluidTags.WATER)) {
                    return y + 1;
                }
            }
            return -1;
        }
    }
}
