package deepluckyblock.entity;

import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;

import deepluckyblock.init.DeepLuckyBlockModItems;

import deepluckyblock.init.DeepLuckyBlockModEntities;

public class SamoussBossEntity extends Raider {

	/*
	 * Équipe le boss avec l'armure Samouss complète (casque + plastron + bottes).
	 * Le drop normal de l'armure est désactivé (0%) : les pièces ne tombent que
	 * via la table commune : 50% et uniquement plastron, leggings ou bottes.
	 */
	private boolean equipmentApplied = false;

	private void applyEquipment() {
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(DeepLuckyBlockModItems.SAMOUSS_HELMET.get()));
		this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(DeepLuckyBlockModItems.SAMOUSS_CHESTPLATE.get()));
		this.setItemSlot(EquipmentSlot.FEET, new ItemStack(DeepLuckyBlockModItems.SAMOUSS_BOOTS.get()));
		this.setDropChance(EquipmentSlot.HEAD, 0f);
		this.setDropChance(EquipmentSlot.CHEST, 0f);
		this.setDropChance(EquipmentSlot.FEET, 0f);
	}


	public SamoussBossEntity(EntityType<SamoussBossEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(false);
		setPersistenceRequired();
		applyEquipment();
	}

	/*
	 * Ré-applique l'équipement au premier tick : ça garantit qu'aucune logique
	 * de spawn (ex. bannière de capitaine de raid) ne remplace le casque, et
	 * que le boss est TOUJOURS équipé, quel que soit son mode d'apparition.
	 */
	@Override
	public void tick() {
		super.tick();
		if (!equipmentApplied) {
			equipmentApplied = true;
			applyEquipment();
		}
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Cycle de combat : recul sneak -> observation -> charge -> rafale de coups
		this.goalSelector.addGoal(1, new SamoussBossCombatGoal(this, 1.1));
		this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1));
		// mustSee = false : le boss traque le joueur même à travers les murs
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
		this.goalSelector.addGoal(5, new FloatGoal(this));
	}

	/*
	 * Machine à états du comportement hostile (boucle infinie) :
	 *   1. RETREAT  : recule ~1,25s en sneak (max 10 blocs du joueur)
	 *   2. OBSERVE  : s'arrête 0,5-1s et fixe le joueur
	 *   3. CHARGE   : sprint vers le joueur en sautant
	 *   4. PUNCH    : roue de coups de poing pendant 1 à 5 secondes
	 * Puis retour au RETREAT. Si le joueur s'échappe (>12 blocs pendant la
	 * rafale), le boss repart immédiatement en CHARGE pour le rattraper.
	 */
	public static class SamoussBossCombatGoal extends Goal {
		private enum State { RETREAT, OBSERVE, CHARGE, PUNCH }

		private final SamoussBossEntity boss;
		private final double speed;
		private State state = State.CHARGE;
		private int stateTimer = 0;
		private int punchTicksLeft = 0;
		private int punchCooldown = 0;
		private boolean jumped = false;

		public SamoussBossCombatGoal(SamoussBossEntity boss, double speed) {
			this.boss = boss;
			this.speed = speed;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = boss.getTarget();
			return target != null && target.isAlive() && boss.distanceToSqr(target) < 4096.0D; // 64 blocs
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			// Dès qu'il a une cible, il fonce d'abord (pas de temps mort)
			this.state = State.CHARGE;
			this.stateTimer = 0;
			this.punchTicksLeft = 0;
			this.punchCooldown = 0;
			this.jumped = false;
			// [debug retire] trace de changement d'etat du boss (spam a chaque combat)
		}

		@Override
		public void stop() {
			boss.setSprinting(false);
			boss.setShiftKeyDown(false);
			boss.setAggressive(false);
			boss.getNavigation().stop();
		}

		private void changeState(State next) {
			if (this.state != next) {
				// [debug retire] trace de changement d'etat du boss (spam a chaque transition)
			}
			this.state = next;
			this.stateTimer = 0;
		}

		@Override
		public void tick() {
			LivingEntity target = boss.getTarget();
			if (target == null) {
				return;
			}
			float dist = boss.distanceTo(target);
			this.stateTimer++;

			switch (this.state) {
				case RETREAT: {
					// Recule en sneak, mais jamais trop loin (max 10 blocs du joueur)
					boss.setShiftKeyDown(true);
					boss.setSprinting(false);
					boss.setAggressive(false);
					boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
					Vec3 away = boss.position().subtract(target.position());
					if (away.lengthSqr() < 1.0E-4D) {
						away = new Vec3(1.0D, 0.0D, 0.0D);
					}
					away = away.normalize();
					double retreatDist = Math.min(dist + 4.0D, 10.0D);
					Vec3 goal = boss.position().add(away.scale(retreatDist));
					// sneak = il recule plus lentement
					boss.getNavigation().moveTo(goal.x, boss.getY(), goal.z, speed * 0.8D);
					if (this.stateTimer >= 25) { // 25 ticks = 1,25s
						this.changeState(State.OBSERVE);
					}
					break;
				}
				case OBSERVE: {
					// S'arrête et fixe le joueur (0,5 à 1 seconde)
					boss.setShiftKeyDown(false);
					boss.setSprinting(false);
					boss.setAggressive(false);
					boss.getNavigation().stop();
					boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
					if (this.stateTimer >= 10 + boss.getRandom().nextInt(11)) {
						this.changeState(State.CHARGE);
						this.jumped = false;
					}
					break;
				}
				case CHARGE: {
					// Sprint + sauts vers le joueur
					boss.setShiftKeyDown(false);
					boss.setSprinting(true);
					boss.setAggressive(true);
					if (!this.jumped) {
						this.jumped = true;
						boss.jumpFromGround();
					}
					boss.getNavigation().moveTo(target, speed * 1.7D);
					if (boss.onGround() && this.stateTimer % 12 == 0) {
						boss.jumpFromGround();
					}
					if (dist < 2.4D || this.stateTimer >= 60) {
						this.changeState(State.PUNCH);
						this.punchTicksLeft = 20 + boss.getRandom().nextInt(81); // 20 à 100 ticks = 1 à 5s
						this.punchCooldown = 0;
					}
					break;
				}
				case PUNCH: {
					// Roue de coups de poing pendant 1 à 5 secondes
					boss.setSprinting(false);
					boss.setShiftKeyDown(false);
					boss.setAggressive(true);
					boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
					if (dist > 12.0D) {
						// Le joueur s'est échappé : on re-charge au lieu de frapper le vide
						this.changeState(State.CHARGE);
						this.jumped = false;
						break;
					}
					if (dist > 2.6D) {
						boss.getNavigation().moveTo(target, speed * 1.4D);
					} else {
						boss.getNavigation().stop();
					}
					if (this.punchCooldown <= 0) {
						if (dist < 3.0D && boss.hasLineOfSight(target)) {
							boss.doHurtTarget(target);
						}
						this.punchCooldown = 8; // ~2,5 coups par seconde
					} else {
						this.punchCooldown--;
					}
					this.punchTicksLeft--;
					if (this.punchTicksLeft <= 0) {
						this.changeState(State.RETREAT);
					}
					break;
				}
			}
		}
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

	@Override
	public SoundEvent getCelebrateSound() {
		return SoundEvents.EMPTY;
	}


	public static void init() {
		// PORT 1.21.1 : Raid.RaiderType.create() supprime -> enumExtensions
		// (META-INF/enumextensions.json). Le type est disponible via
		// Raid.RaiderType.valueOf("DEEP_LUCKY_BLOCK_SAMOUSS_BOSS").
	}

	@Override
	public void applyRaidBuffs(ServerLevel level, int num, boolean logic) {
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0.34); // un peu plus vif
		builder = builder.add(Attributes.MAX_HEALTH, 10);
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 6); // dégâts de mêlée DOUBLÉS (3 -> 6)
		builder = builder.add(Attributes.ATTACK_KNOCKBACK, 1.0); // les coups repoussent le joueur
		builder = builder.add(Attributes.FOLLOW_RANGE, 64); // détecte et traque le joueur très loin
		return builder;
	}
}
