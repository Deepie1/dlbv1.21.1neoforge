package deepluckyblock.entity;

import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.resources.ResourceLocation;

import deepluckyblock.init.DeepLuckyBlockModEntities;

import deepluckyblock.init.DeepLuckyBlockModItems;

public class JerryEntity extends Monster implements RangedAttackMob {
	private final ServerBossEvent bossInfo = new ServerBossEvent(this.getDisplayName(), ServerBossEvent.BossBarColor.RED, ServerBossEvent.BossBarOverlay.PROGRESS);


	public JerryEntity(EntityType<JerryEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(false); // IA active
		setPersistenceRequired();
		// Arme de depart : epee en fer
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.25f); // 25% de chances de dropper l'arme tenue a la mort
		// ── V40 : armure INVISIBLE enchantee (luck 10-100) ────────────────
		// Les 4 pieces sont de vraies pieces d'armure : elles apportent leurs
		// points de protection et leurs enchantements cote serveur.
		// Elles ne sont PAS visibles : le renderer de ce mob n'ajoute plus de
		// HumanoidArmorLayer (voir BenRenderer / JerryRenderer / SamoussBossRenderer).
		// dropChance = 0 : cette armure technique n'est jamais lachee, le loot
		// reste la piece Samouss geree par dropCustomDeathLoot().
		deepluckyblock.procedures.GenerateLuckyItemProcedure
				.equipHiddenEnchantedArmor(this, 10, 100, this.random);
	}

	/*
	 * Comportement agressif hybride :
	 *  - a DISTANCE : il tient un arc et tire des fleches (RangedBowAttackGoal,
	 *    comme un skeleton) tant qu'il est loin du joueur
	 *  - au CORPS A CORPS : il passe a l'epee en fer et attaque en melee
	 * Le changement d'arme est fait dans customServerAiStep selon la distance.
	 */
	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new RangedBowAttackGoal<>(this, 1.0, 15, 15.0F)); // tire toutes les 15 ticks quand il tient l'arc
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4, true)); // attaque a l'epee, vite, meme sans voir la cible
		this.goalSelector.addGoal(3, new FloatGoal(this));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false)); // traque a travers les murs
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this)); // riposte des qu'il est frappe
	}

	/*
	 * Bascule automatique d'arme :
	 *  - joueur loin (> 6 blocs)  -> il sort son ARC
	 *  - joueur proche (< 3.5)    -> il sort son EPEE EN FER
	 */
	@Override
	public void customServerAiStep() {
		super.customServerAiStep();
		this.bossInfo.setProgress(this.getHealth() / this.getMaxHealth());

		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive()) {
			double distSqr = this.distanceToSqr(target);
			if (distSqr > 6.0D * 6.0D) {
				if (!(this.getMainHandItem().getItem() instanceof BowItem)) {
					this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				}
			} else if (distSqr < 3.5D * 3.5D) {
				if (this.getMainHandItem().getItem() instanceof BowItem) {
					this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				}
			}
		}
	}

	/*
	 * Tire une fleche (meme logique que le skeleton).
	 * getProjectile() recupere la fleche du slot offhand (ou une fleche par defaut).
	 */
	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		ItemStack projectile = this.getProjectile(this.getMainHandItem());
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectile, distanceFactor, this.getMainHandItem());
		double d0 = target.getX() - this.getX();
		double d1 = target.getY(0.3333333333333333D) - arrow.getY();
		double d2 = target.getZ() - this.getZ();
		double d3 = Math.sqrt(d0 * d0 + d2 * d2);
		arrow.shoot(d0, d1 + d3 * 0.2D, d2, 1.6F, (float) (14 - this.level().getDifficulty().getId() * 4));
		this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
		this.level().addFreshEntity(arrow);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

    // PORT 1.21.1 : getRidingOffset() supprimé -> offset via getPassengerRidingPosition.
    @Override
    public net.minecraft.world.phys.Vec3 getPassengerRidingPosition(net.minecraft.world.entity.Entity passenger) {
        return super.getPassengerRidingPosition(passenger).add(0, -0.35D, 0);
    }

	@Override
	public SoundEvent getHurtSound(DamageSource ds) {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.generic.hurt"));
	}

	@Override
	public SoundEvent getDeathSound() {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.generic.death"));
	}

	/*
	 * A la mort : 30% de chances de dropper UNE piece d'armure Samouss aleatoire
	 * (casque, plastron ou bottes). Ajuste 0.30f pour changer la probabilite.
	 */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (!this.level().isClientSide && this.random.nextFloat() < 0.30f) {
			ItemStack piece;
			net.minecraft.world.entity.EquipmentSlot pieceSlot;
			switch (this.random.nextInt(3)) {
				case 0:
					piece = new ItemStack(DeepLuckyBlockModItems.SAMOUSS_HELMET.get());
					pieceSlot = net.minecraft.world.entity.EquipmentSlot.HEAD;
					break;
				case 1:
					piece = new ItemStack(DeepLuckyBlockModItems.SAMOUSS_CHESTPLATE.get());
					pieceSlot = net.minecraft.world.entity.EquipmentSlot.CHEST;
					break;
				default:
					piece = new ItemStack(DeepLuckyBlockModItems.SAMOUSS_BOOTS.get());
					pieceSlot = net.minecraft.world.entity.EquipmentSlot.FEET;
					break;
			}
			// La piece Samouss lachee est enchantee avec un luck aleatoire 15-100.
			// L'item reste une piece Samouss : seuls les enchantements et le nom
			// colore sont ajoutes par GenerateLuckyItemProcedure.
			int lootLuck = 15 + this.random.nextInt(86); // 15 a 100 inclus
			deepluckyblock.procedures.GenerateLuckyItemProcedure
					.enchantExistingArmor(piece, lootLuck, pieceSlot, this.random);
			this.spawnAtLocation(piece);
		}
	}

	// PORT 1.21.1 : canChangeDimensions(Level, Level).
	@Override
	public boolean canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to) {
		return false;
	}

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

	public static void init() {
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0.32); // plus rapide -> plus agressif
		builder = builder.add(Attributes.MAX_HEALTH, 50); // 50 PV
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 6); // degats d'une epee en fer
		builder = builder.add(Attributes.ATTACK_KNOCKBACK, 1.0); // ses coups repoussent
		builder = builder.add(Attributes.FOLLOW_RANGE, 64); // detecte et poursuit tres loin
		return builder;
	}
}
