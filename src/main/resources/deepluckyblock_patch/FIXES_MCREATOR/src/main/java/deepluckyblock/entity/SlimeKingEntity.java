package deepluckyblock.entity;

import deepluckyblock.init.DeepLuckyBlockModEntities;


import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

import java.util.List;

public class SlimeKingEntity extends Slime {
    public final AnimationState animationState0 = new AnimationState();
    public final AnimationState animationState1 = new AnimationState();

    private static final EntityDataAccessor<Boolean> DATA_IS_ATTACKING =
            SynchedEntityData.defineId(SlimeKingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_USE_WALK_ANIM =
            SynchedEntityData.defineId(SlimeKingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_WALK_ANIM_SPEED =
            SynchedEntityData.defineId(SlimeKingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ATTACK_ANIM_SPEED =
            SynchedEntityData.defineId(SlimeKingEntity.class, EntityDataSerializers.FLOAT);

    private final ServerBossEvent bossInfo = new ServerBossEvent(
            Component.literal("§a§l👑 Slime King 👑"),
            ServerBossEvent.BossBarColor.GREEN,
            ServerBossEvent.BossBarOverlay.PROGRESS);

    private static final float JUMP_BONUS_MIN = 0.25f;
    private static final float JUMP_BONUS_MAX = 0.95f;
    private static final int JUMP_PAUSE_MIN_TICKS = 0;
    private static final int JUMP_PAUSE_MAX_TICKS = 12;
    private static final double SPEED_MIN = 1.4;
    private static final double SPEED_MAX = 5.2;
    private static final float SPEED_BONUS = 1.2f;

    private static final int SPECIAL_ATTACK_DURATION = 40;
    private static final float SPECIAL_ATTACK_RANGE = 3.0f;
    private static final float SPECIAL_ATTACK_DAMAGE_MULT = 2.0f;
    private static final int SPECIAL_ATTACK_CHANCE = 2;
    private static final int COOLDOWN_AFTER_ATTACK_TICKS = 40;

    private static final float CONTACT_DAMAGE = 6.0f;
    private static final int CONTACT_DAMAGE_INTERVAL = 8;
    private static final float CONTACT_DISTANCE = 3.0f;

    private static final int WALK_ANIM_CHANCE = 3;
    private static final float WALK_ANIM_SPEED_MIN = 0.8f;
    private static final float WALK_ANIM_SPEED_MAX = 2.0f;

    private static final float ATTACK_ANIM_SPEED_NORMAL = 1.2f;
    private static final float ATTACK_ANIM_SPEED_SLOW = 0.6f;
    private static final int SLOW_ATTACK_CHANCE = 2;
    private static final int WALK_AFTER_ATTACK_MIN_TICKS = 40;

    private static final float HITBOX_MULTIPLIER = 3.0f;

    private static final double STAT_MAX_HEALTH = 600.0; // V41 : 500 -> 600 (+100 PV)
    private static final double STAT_ARMOR = 35.0;
    private static final double STAT_ATTACK_DAMAGE = 18.0;
    private static final double STAT_FOLLOW_RANGE = 64.0;
    private static final double STAT_KNOCKBACK_RESISTANCE = 0.8;

    private int specialAttackCooldown = 0;
    private int specialAttackTicks = 0;
    private int jumpPauseTimer = 0;
    private int contactDamageCooldown = 0;
    private int forceWalkTimer = 0;
    private boolean nextAttackIsSlow = false;
    private double currentSpeed;


    @SuppressWarnings("unchecked")
    public SlimeKingEntity(EntityType<SlimeKingEntity> type, Level world) {
        super((EntityType<? extends Slime>) (EntityType<?>) type, world);
        xpReward = 100;
        setNoAi(false);
        setCustomName(Component.literal("§a§l👑 Slime King 👑"));
        setCustomNameVisible(true);
        setPersistenceRequired();
        this.setSize(8, true);
        this.currentSpeed = SPEED_MIN + this.random.nextDouble() * (SPEED_MAX - SPEED_MIN);
        this.refreshDimensions();
        // ── Armure NETHERITE enchantée INVISIBLE (luck 30-100) ────────────
        // Les 4 pièces sont de vraies pièces d'armure Netherite : elles
        // apportent leurs points de protection/vanilla ET leurs
        // enchantements du mod (cœurs, protections, etc.) côté serveur.
        // Invisible car SlimeKingRenderer étend MobRenderer directement
        // (jamais de HumanoidArmorLayer ajoutée pour ce mob) -- comme
        // Jerry/Ben/Samouss. dropChance = 0 : jamais lâchée à la mort.
        deepluckyblock.procedures.GenerateLuckyItemProcedure
                .equipHiddenEnchantedArmor(this, 30, 100, this.random, true);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_ATTACKING, false);
        builder.define(DATA_USE_WALK_ANIM, false);
        builder.define(DATA_WALK_ANIM_SPEED, 1.0f);
        builder.define(DATA_ATTACK_ANIM_SPEED, ATTACK_ANIM_SPEED_NORMAL);
    }

    public boolean isSpecialAttacking() { return this.entityData.get(DATA_IS_ATTACKING); }
    public void setSpecialAttacking(boolean v) { this.entityData.set(DATA_IS_ATTACKING, v); }
    public boolean useWalkAnim() { return this.entityData.get(DATA_USE_WALK_ANIM); }
    public void setUseWalkAnim(boolean v) { this.entityData.set(DATA_USE_WALK_ANIM, v); }
    public float getWalkAnimSpeed() { return this.entityData.get(DATA_WALK_ANIM_SPEED); }
    public void setWalkAnimSpeed(float v) { this.entityData.set(DATA_WALK_ANIM_SPEED, v); }
    public float getAttackAnimSpeed() { return this.entityData.get(DATA_ATTACK_ANIM_SPEED); }
    public void setAttackAnimSpeed(float v) { this.entityData.set(DATA_ATTACK_ANIM_SPEED, v); }

    @Override
    public boolean hurt(DamageSource damagesource, float amount) {
        if (damagesource.is(DamageTypes.FALL)) return false;
        if (damagesource.is(DamageTypes.DROWN)) return false;
        return super.hurt(damagesource, amount);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED) {
            this.setSize(1, true);
        }
        super.remove(reason);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return true; }

    // PORT 1.21.1 : getDimensions(Pose) est final sur LivingEntity -> dimensions via EntityType.sized().

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        this.refreshDimensions();
    }

    @Override
    protected float getJumpPower() {
        float base = super.getJumpPower();
        float bonus = JUMP_BONUS_MIN + this.random.nextFloat() * (JUMP_BONUS_MAX - JUMP_BONUS_MIN);
        return base + bonus;
    }

    @Override
    public float getSpeed() {
        return (float) currentSpeed + SPEED_BONUS;
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        if (this.level().isClientSide()) {
            if (isSpecialAttacking()) {
                this.animationState0.animateWhen(true, this.tickCount);
                this.animationState1.stop();
            } else if (useWalkAnim()) {
                this.animationState0.stop();
                this.animationState1.animateWhen(true, this.tickCount);
            } else {
                this.animationState0.stop();
                this.animationState1.stop();
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) return;

        if (specialAttackCooldown > 0) specialAttackCooldown--;
        if (contactDamageCooldown > 0) contactDamageCooldown--;
        if (forceWalkTimer > 0) forceWalkTimer--;

        if (contactDamageCooldown <= 0) {
            List<Player> nearbyPlayers =
                    this.level().getEntitiesOfClass(
                            Player.class,
                            this.getBoundingBox().inflate(CONTACT_DISTANCE),
                            p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                    );
            for (Player player : nearbyPlayers) {
                if (this.distanceTo(player) < CONTACT_DISTANCE + this.getBbWidth() * 0.5) {
                    player.hurt(this.damageSources().mobAttack(this), CONTACT_DAMAGE);
                    double kx = player.getX() - this.getX();
                    double kz = player.getZ() - this.getZ();
                    double kl = Math.sqrt(kx * kx + kz * kz);
                    if (kl > 0) player.push(kx / kl * 0.8, 0.3, kz / kl * 0.8);
                    contactDamageCooldown = CONTACT_DAMAGE_INTERVAL;
                    break;
                }
            }
        }

        if (jumpPauseTimer > 0) jumpPauseTimer--;

        if (!this.onGround() && this.getDeltaMovement().y > 0.05) {
            if (jumpPauseTimer <= 0) {
                this.currentSpeed = SPEED_MIN + this.random.nextDouble() * (SPEED_MAX - SPEED_MIN);
                if (!isSpecialAttacking() && forceWalkTimer <= 0) {
                    boolean doWalk = this.random.nextInt(WALK_ANIM_CHANCE) == 0;
                    setUseWalkAnim(doWalk);
                    if (doWalk) {
                        float walkSpd = WALK_ANIM_SPEED_MIN + this.random.nextFloat() * (WALK_ANIM_SPEED_MAX - WALK_ANIM_SPEED_MIN);
                        setWalkAnimSpeed(walkSpd);
                    }
                } else if (forceWalkTimer > 0 && !isSpecialAttacking()) {
                    setUseWalkAnim(true);
                    float walkSpd = WALK_ANIM_SPEED_MIN + this.random.nextFloat() * (WALK_ANIM_SPEED_MAX - WALK_ANIM_SPEED_MIN);
                    setWalkAnimSpeed(walkSpd);
                }
                jumpPauseTimer = JUMP_PAUSE_MIN_TICKS + this.random.nextInt(JUMP_PAUSE_MAX_TICKS - JUMP_PAUSE_MIN_TICKS + 1);
            }
        }

        if (this.onGround() && this.getDeltaMovement().y <= 0 && !isSpecialAttacking() && forceWalkTimer <= 0) {
            setUseWalkAnim(false);
        }

        if (specialAttackTicks > 0) {
            specialAttackTicks--;
            if (specialAttackTicks == SPECIAL_ATTACK_DURATION / 2)
                performSpecialAttackDamage();
            if (specialAttackTicks <= 0) {
                setSpecialAttacking(false);
                setAttackAnimSpeed(ATTACK_ANIM_SPEED_NORMAL);
                specialAttackCooldown = COOLDOWN_AFTER_ATTACK_TICKS;
                forceWalkTimer = WALK_AFTER_ATTACK_MIN_TICKS;
                setUseWalkAnim(true);
                float walkSpd = WALK_ANIM_SPEED_MIN + this.random.nextFloat() * (WALK_ANIM_SPEED_MAX - WALK_ANIM_SPEED_MIN);
                setWalkAnimSpeed(walkSpd);
            }
            return;
        }

        if (specialAttackCooldown <= 0) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()
                    && this.distanceTo(target) <= SPECIAL_ATTACK_RANGE
                    && this.random.nextInt(SPECIAL_ATTACK_CHANCE) == 0) {
                startSpecialAttack();
            }
        }
    }

    private void startSpecialAttack() {
        this.nextAttackIsSlow = this.random.nextInt(SLOW_ATTACK_CHANCE) == 0;

        if (nextAttackIsSlow) {
            setAttackAnimSpeed(ATTACK_ANIM_SPEED_SLOW);
        } else {
            setAttackAnimSpeed(ATTACK_ANIM_SPEED_NORMAL);
        }

        specialAttackTicks = SPECIAL_ATTACK_DURATION;
        setSpecialAttacking(true);
        setUseWalkAnim(false);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ANVIL_LAND, this.getSoundSource(), 1.5f, 0.5f);

        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ITEM_SLIME,
                    this.getX(), this.getY() + 1, this.getZ(),
                    40, this.getBbWidth(), 1.5, this.getBbWidth(), 0.3);
            sl.sendParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY() + 2, this.getZ(),
                    20, 1.5, 1.0, 1.5, 0.2);
        }
    }

    private void performSpecialAttackDamage() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.distanceTo(target) > SPECIAL_ATTACK_RANGE * 3.0) return;

        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float mult = SPECIAL_ATTACK_DAMAGE_MULT;
        if (nextAttackIsSlow) {
            mult *= 2.0f;
        }

        target.hurt(this.damageSources().mobAttack(this), dmg * mult);

        double kx = target.getX() - this.getX();
        double kz = target.getZ() - this.getZ();
        double kl = Math.sqrt(kx * kx + kz * kz);
        if (kl > 0) target.push(kx / kl * 2.5, 1.0, kz / kl * 2.5);

        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.EXPLOSION,
                    target.getX(), target.getY() + 1, target.getZ(),
                    10, 1, 1, 1, 0.1);
            sl.sendParticles(ParticleTypes.ITEM_SLIME,
                    target.getX(), target.getY() + 0.5, target.getZ(),
                    40, 2.0, 0.5, 2.0, 0.2);
            sl.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1.5, target.getZ(),
                    15, 1.0, 0.5, 1.0, 0.3);
        }

        this.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXPLODE, this.getSoundSource(), 1.2f, 0.7f);
        this.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, this.getSoundSource(), 1.0f, 0.8f);
    }

    // PORT 1.21.1 : canChangeDimensions(Level, Level).
    @Override
    public boolean canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to) { return false; }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossInfo.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossInfo.removePlayer(player);
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        this.bossInfo.setProgress(this.getHealth() / this.getMaxHealth());
    }

    public static boolean checkSlimeKingSpawnRules(EntityType<? extends Slime> type,
                                                    LevelAccessor level,
                                                    MobSpawnType spawnType,
                                                    BlockPos pos,
                                                    RandomSource random) {
        return spawnType == MobSpawnType.COMMAND
                || spawnType == MobSpawnType.SPAWN_EGG
                || spawnType == MobSpawnType.EVENT
                || spawnType == MobSpawnType.MOB_SUMMONED;
    }

    public static void init() {
        // PORT 1.21.1 : spawn placement enregistre dans SpawnPlacementRegistration.
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, SPEED_MIN);
        builder = builder.add(Attributes.MAX_HEALTH, STAT_MAX_HEALTH);
        builder = builder.add(Attributes.ARMOR, STAT_ARMOR);
        builder = builder.add(Attributes.ATTACK_DAMAGE, STAT_ATTACK_DAMAGE);
        builder = builder.add(Attributes.FOLLOW_RANGE, STAT_FOLLOW_RANGE);
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, STAT_KNOCKBACK_RESISTANCE);
        return builder;
    }
}