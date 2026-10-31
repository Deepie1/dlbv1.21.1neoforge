package deepluckyblock.entity;

import deepluckyblock.init.DeepLuckyBlockModEntities;
import deepluckyblock.init.DeepLuckyBlockModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public class ShadowMirrorEntity extends Monster {

    private final ServerBossEvent bossInfo = new ServerBossEvent(
        Component.literal("§8§lDouble Sombre"),
        ServerBossEvent.BossBarColor.WHITE,
        ServerBossEvent.BossBarOverlay.PROGRESS
    );

    private int actionCooldown = 0;
    private int equipmentUpdateCooldown = 0;


    public ShadowMirrorEntity(EntityType<ShadowMirrorEntity> type, Level world) {
        super(type, world);
        xpReward = 15;
        setNoAi(false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // PORT 1.21.1 : getAttackReachSqr supprimé de MeleeAttackGoal.
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, false));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(6, new FloatGoal(this));
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        this.bossInfo.setProgress(this.getHealth() / this.getMaxHealth());

        if (actionCooldown > 0) actionCooldown--;
        if (equipmentUpdateCooldown > 0) equipmentUpdateCooldown--;

        if (equipmentUpdateCooldown <= 0 && this.level() instanceof ServerLevel) {
            Player nearestPlayer = this.level().getNearestPlayer(this, 25);
            if (nearestPlayer != null) {
                this.setItemSlot(EquipmentSlot.HEAD, nearestPlayer.getItemBySlot(EquipmentSlot.HEAD).copy());
                this.setItemSlot(EquipmentSlot.CHEST, nearestPlayer.getItemBySlot(EquipmentSlot.CHEST).copy());
                this.setItemSlot(EquipmentSlot.LEGS, nearestPlayer.getItemBySlot(EquipmentSlot.LEGS).copy());
                this.setItemSlot(EquipmentSlot.FEET, nearestPlayer.getItemBySlot(EquipmentSlot.FEET).copy());

                ItemStack offHand = nearestPlayer.getOffhandItem().copy();
                if (!offHand.isEmpty()) {
                    this.setItemSlot(EquipmentSlot.OFFHAND, offHand);
                } else {
                    this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                }

                CompoundTag hotbarData = new CompoundTag();
                for (int i = 0; i < 9; i++) {
                    ItemStack slotItem = nearestPlayer.getInventory().getItem(i);
                    if (!slotItem.isEmpty()) {
                        // PORT 1.21.1 : plus de CompoundTag.putAll.
                        CompoundTag itemTag = (net.minecraft.nbt.CompoundTag) slotItem.save(this.registryAccess());
                        hotbarData.put("Slot" + i, itemTag);
                    }
                }
                this.getPersistentData().put("PlayerHotbar", hotbarData);
            }
            equipmentUpdateCooldown = 200;
        }

        if (actionCooldown <= 0 && this.getTarget() instanceof Player target) {
            if (handleAggressiveBehavior(target)) {
                resetCombatActionCooldown();
            }
        }
    }

    private void resetCombatActionCooldown() {
        this.actionCooldown = this.random.nextInt(21);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (this.actionCooldown > 0) return false;

        boolean attacked = super.doHurtTarget(entity);
        if (attacked) {
            resetCombatActionCooldown();
        }
        return attacked;
    }

    private boolean handleAggressiveBehavior(Player target) {
        if (tryPlaceDefensiveDirt(target)) return true;
        if (tryBreakBlockingBlocks(target)) return true;
        trySwitchWeapon(target);
        return false;
    }

    private boolean tryBreakBlockingBlocks(Player target) {
        BlockPos blockingPos = findVisibleBlockingBlock(target);
        if (blockingPos == null) return false;

        BlockState state = this.level().getBlockState(blockingPos);
        float hardness = state.getDestroySpeed(this.level(), blockingPos);
        if (state.isAir() || hardness < 0 || hardness >= 80) return false;

        switchToBestToolFor(state);
        return breakBlockWithDrops(blockingPos, state);
    }

    private BlockPos findVisibleBlockingBlock(Player target) {
        Vec3 mobEyes = this.getEyePosition();
        Vec3 targetEyes = target.getEyePosition();
        BlockPos eyeLevelHit = raycastBlockingBlock(mobEyes, targetEyes, 4.5);
        if (eyeLevelHit != null) return eyeLevelHit;

        Vec3 mobBody = this.position().add(0, 0.65, 0);
        Vec3 targetBody = target.position().add(0, 0.65, 0);
        return raycastBlockingBlock(mobBody, targetBody, 4.5);
    }

    private BlockPos raycastBlockingBlock(Vec3 start, Vec3 destination, double reach) {
        Vec3 delta = destination.subtract(start);
        if (delta.lengthSqr() < 0.0001) return null;

        Vec3 end = start.add(delta.normalize().scale(Math.min(reach, delta.length())));
        BlockHitResult hit = this.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private void switchToBestToolFor(BlockState state) {
        CompoundTag hotbar = this.getPersistentData().getCompound("PlayerHotbar");
        ItemStack bestTool = ItemStack.EMPTY;
        float bestSpeed = 1.0f;
        boolean bestToolIsCorrect = false;

        for (int i = 0; i < 9; i++) {
            if (!hotbar.contains("Slot" + i)) continue;
            ItemStack candidate = ItemStack.parseOptional(this.registryAccess(), hotbar.getCompound("Slot" + i));
            if (candidate.isEmpty()) continue;

            boolean correct = candidate.isCorrectToolForDrops(state);
            float speed = candidate.getDestroySpeed(state);
            if ((correct && !bestToolIsCorrect)
                    || (correct == bestToolIsCorrect && speed > bestSpeed)) {
                bestTool = candidate;
                bestSpeed = speed;
                bestToolIsCorrect = correct;
            }
        }

        if (!bestTool.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, bestTool.copy());
        }
    }

    private boolean breakBlockWithDrops(BlockPos pos, BlockState state) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return false;

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(
                state, serverLevel, pos, blockEntity, this, this.getMainHandItem()));

        if (drops.isEmpty()) {
            ItemStack fallbackDrop = new ItemStack(state.getBlock().asItem());
            if (fallbackDrop.isEmpty()) return false;
            drops.add(fallbackDrop);
        }

        if (!serverLevel.destroyBlock(pos, false, this)) return false;
        for (ItemStack drop : drops) {
            Block.popResource(serverLevel, pos, drop);
        }
        this.swing(InteractionHand.MAIN_HAND);
        serverLevel.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    private boolean tryPlaceDefensiveDirt(Player target) {
        boolean fleeing = this.getHealth() <= this.getMaxHealth() * 0.40f;
        boolean protecting = this.distanceToSqr(target) <= 20.25
                && this.getHealth() <= this.getMaxHealth() * 0.75f;
        if (!fleeing && !protecting) return false;

        int dirtSlot = findDirtSlotInStoredHotbar();
        if (dirtSlot < 0) return false;

        if (fleeing) {
            moveAwayFrom(target);
        }

        Direction towardTarget = horizontalDirectionTo(target);
        Direction right = towardTarget.getClockWise();
        BlockPos front = this.blockPosition().relative(towardTarget);
        BlockPos[] wallBases = {
                front,
                front.relative(right),
                front.relative(right.getOpposite())
        };

        for (BlockPos base : wallBases) {
            for (int dy = 0; dy < 2; dy++) {
                BlockPos placePos = base.above(dy);
                if (!canPlaceDirtAt(placePos)) continue;

                ItemStack dirtStack = getStoredHotbarItem(dirtSlot);
                if (dirtStack.isEmpty()) return false;

                ItemStack heldDirt = dirtStack.copy();
                heldDirt.setCount(1);
                this.setItemSlot(EquipmentSlot.MAINHAND, heldDirt);
                this.level().setBlock(placePos, Blocks.DIRT.defaultBlockState(), 3);
                consumeStoredHotbarItem(dirtSlot, dirtStack);
                this.swing(InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private Direction horizontalDirectionTo(Player target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean canPlaceDirtAt(BlockPos pos) {
        BlockState current = this.level().getBlockState(pos);
        if (!current.canBeReplaced() || !current.getFluidState().isEmpty()) return false;
        if (!this.level().getBlockState(pos.below()).blocksMotion()) return false;
        return this.level().getEntities(this, new AABB(pos)).isEmpty();
    }

    private void moveAwayFrom(Player target) {
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return;

        double escapeX = this.getX() + dx / length * 10.0;
        double escapeZ = this.getZ() + dz / length * 10.0;
        this.getNavigation().moveTo(escapeX, this.getY(), escapeZ, 1.45);
    }

    private int findDirtSlotInStoredHotbar() {
        CompoundTag hotbar = this.getPersistentData().getCompound("PlayerHotbar");
        for (int i = 0; i < 9; i++) {
            if (!hotbar.contains("Slot" + i)) continue;
            ItemStack stack = ItemStack.parseOptional(this.registryAccess(), hotbar.getCompound("Slot" + i));
            if (stack.is(Items.DIRT) && !stack.isEmpty()) return i;
        }
        return -1;
    }

    private ItemStack getStoredHotbarItem(int slot) {
        CompoundTag hotbar = this.getPersistentData().getCompound("PlayerHotbar");
        if (!hotbar.contains("Slot" + slot)) return ItemStack.EMPTY;
        return ItemStack.parseOptional(this.registryAccess(), hotbar.getCompound("Slot" + slot));
    }

    private void consumeStoredHotbarItem(int slot, ItemStack stack) {
        CompoundTag hotbar = this.getPersistentData().getCompound("PlayerHotbar");
        stack.shrink(1);
        if (stack.isEmpty()) {
            hotbar.remove("Slot" + slot);
        } else {
            // PORT 1.21.1 : plus de CompoundTag.putAll.
            CompoundTag updatedStack = (net.minecraft.nbt.CompoundTag) stack.save(this.registryAccess());
            hotbar.put("Slot" + slot, updatedStack);
        }
        this.getPersistentData().put("PlayerHotbar", hotbar);
    }

    private void trySwitchWeapon(Player target) {
        CompoundTag hotbar = this.getPersistentData().getCompound("PlayerHotbar");
        ItemStack fallbackAxe = ItemStack.EMPTY;

        for (int i = 0; i < 9; i++) {
            if (!hotbar.contains("Slot" + i)) continue;
            ItemStack stack = ItemStack.parseOptional(this.registryAccess(), hotbar.getCompound("Slot" + i));
            String name = stack.getItem().toString().toLowerCase();

            if (name.contains("sword")) {
                this.setItemSlot(EquipmentSlot.MAINHAND, stack);
                return;
            }
            if (fallbackAxe.isEmpty() && name.contains("axe") && !name.contains("pickaxe")) {
                fallbackAxe = stack;
            }
        }

        if (!fallbackAxe.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, fallbackAxe);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);

        if (this.random.nextInt(3) != 0) {
            ItemStack essence = new ItemStack(DeepLuckyBlockModItems.SHADOW_ESSENCE.get(), 1);
            this.spawnAtLocation(essence);
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        return true;
    }

    public static boolean checkShadowMirrorSpawnRules(EntityType<ShadowMirrorEntity> type,
                                                       LevelAccessor level,
                                                       MobSpawnType spawnType,
                                                       BlockPos pos,
                                                       RandomSource random) {
        return spawnType == MobSpawnType.SPAWNER
            || spawnType == MobSpawnType.EVENT
            || spawnType == MobSpawnType.COMMAND
            || spawnType == MobSpawnType.MOB_SUMMONED;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType spawnType) {
        return spawnType == MobSpawnType.SPAWNER
            || spawnType == MobSpawnType.EVENT
            || spawnType == MobSpawnType.COMMAND
            || spawnType == MobSpawnType.MOB_SUMMONED;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return super.checkSpawnObstruction(level);
    }

    public static void init() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.34);
        builder = builder.add(Attributes.MAX_HEALTH, 55);
        builder = builder.add(Attributes.ARMOR, 6);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 10);
        builder = builder.add(Attributes.FOLLOW_RANGE, 80);
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
        return builder;
    }


    // PORT 1.21.1 : getRidingOffset() supprimé -> offset via getPassengerRidingPosition.
    @Override
    public net.minecraft.world.phys.Vec3 getPassengerRidingPosition(net.minecraft.world.entity.Entity passenger) {
        return super.getPassengerRidingPosition(passenger).add(0, -0.35D, 0);
    }

    @Override
    public SoundEvent getHurtSound(DamageSource ds) {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("minecraft", "entity.generic.hurt"));
    }

    @Override
    public SoundEvent getDeathSound() {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("minecraft", "entity.generic.death"));
    }

    @Override
    public boolean hurt(DamageSource damagesource, float amount) {
        if (damagesource.is(DamageTypes.FALL)) return false;
        return super.hurt(damagesource, amount);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // PORT 1.21.1 : canChangeDimensions(Level, Level).
    @Override
    public boolean canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to) {
        return false;
    }

    // PORT 1.21.1 : getDimensions(Pose) est final sur LivingEntity -> dimensions via EntityType.sized().

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
}