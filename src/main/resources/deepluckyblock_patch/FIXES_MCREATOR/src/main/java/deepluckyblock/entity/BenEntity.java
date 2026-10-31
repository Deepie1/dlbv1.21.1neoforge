package deepluckyblock.entity;

import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.resources.ResourceLocation;

import deepluckyblock.init.DeepLuckyBlockModEntities;

import deepluckyblock.init.DeepLuckyBlockModItems;

public class BenEntity extends Monster {
	private final ServerBossEvent bossInfo = new ServerBossEvent(this.getDisplayName(), ServerBossEvent.BossBarColor.RED, ServerBossEvent.BossBarOverlay.PROGRESS);


	public BenEntity(EntityType<BenEntity> type, Level world) {
		super(type, world);
		xpReward = 4;
		setNoAi(false); // IA ACTIVE (avant : desactivee -> il ne faisait rien)
		// Equipe une epee en fer (arme principale)
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.25f); // 25% de chances de dropper l'epee a la mort
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
	 * Comportement tres agressif :
	 *  - attaque au corps a corps rapidement (vitesse x1.4) et continue meme sans voir la cible
	 *  - traque les joueurs meme a travers les murs (mustSee = false)
	 *  - riposte immediatement quand il est frappe
	 */
	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
		this.goalSelector.addGoal(2, new FloatGoal(this));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
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

	@Override
	public boolean canCollideWith(Entity entity) {
		return true;
	}

	@Override
	public boolean canBeCollidedWith() {
		return true;
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

	@Override
	public void customServerAiStep() {
		super.customServerAiStep();
		this.bossInfo.setProgress(this.getHealth() / this.getMaxHealth());
	}

	public static void init() {
		// Spawn naturel DESACTIVE : Ben ne doit apparaitre QUE via les lucky blocks du mod,
		// jamais naturellement. (SpawnPlacements n'est volontairement pas enregistre.)
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
