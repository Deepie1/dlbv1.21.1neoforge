package deepluckyblock.enchantments;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.EnchantmentComboRegistry.ComboDef;
import deepluckyblock.enchantments.EnchantmentComboRegistry.ComboMask;
import deepluckyblock.enchantments.EnchantmentComboRegistry.ComboTier;
import deepluckyblock.enchantments.EnchantmentComboRegistry.PieceRequirement;
import deepluckyblock.procedures.TestProcedure;
import deepluckyblock.util.DeepEnchant;
import deepluckyblock.util.TranslationFallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static deepluckyblock.enchantments.EnchantmentComboRegistry.*;

/**
 * T309-COMBOS : Gestionnaire d'exécution des 110 Combos & Synergies Multi-Pièces
 * (Normal -> Avancé -> Rare -> Épique -> Mythique -> Ultra-Rare).
 *
 * Chaque synergie dispose de sa mécanique dédiée, de particules thématiques,
 * d'une notification d'activation en barre d'action et de /dlbcombos.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class EnchantmentComboHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> COMBO_HURT_GUARD = ThreadLocal.withInitial(() -> false);
    private static final Set<String> REPORTED_ERRORS = ConcurrentHashMap.newKeySet();

    private static final class PlayerComboState {
        ComboMask prevServerMask = ComboMask.EMPTY;
        double prevY = Double.NaN;
        Vec3 prevPos = null;
        int stillTicks = 0;
        boolean wasFallFlying = false;
        double lastFlightSpeed = 0.0;
        boolean wasSprinting = false;
        boolean wasShiftDown = false;
        int thunderHitCounter = 0;
        int rhythmHitCounter = 0;
        int supremeSlashCounter = 0;
        int oreMinedCounter = 0;
        int stoneMinedCounter = 0;
        long lateralChargeUntil = 0L;
        long parryRiposteUntil = 0L;
        long phoenixNovaReadyAt = 0L;
        long immortalBloodReadyAt = 0L;
        long grappleShieldUntil = 0L;
        long horizonProjectileImmuneUntil = 0L;
        long bounceReadyUntil = 0L;
        long airBrakeUntil = 0L;
        long airBrakeCooldownUntil = 0L;
        long saitamaCooldownUntil = 0L;
    }

    private static final Map<UUID, PlayerComboState> STATES = new ConcurrentHashMap<>();

    private EnchantmentComboHandler() {}

    private static PlayerComboState stateOf(LivingEntity e) {
        return STATES.computeIfAbsent(e.getUUID(), u -> new PlayerComboState());
    }

    private static void safeHurt(LivingEntity target, net.minecraft.world.damagesource.DamageSource src, float amount) {
        if (target == null || !target.isAlive() || amount <= 0.0f) return;
        if (COMBO_HURT_GUARD.get()) return;
        COMBO_HURT_GUARD.set(true);
        try {
            target.hurt(src, amount);
        } finally {
            COMBO_HURT_GUARD.set(false);
        }
    }

    private static DustParticleOptions dustOf(int rgb, float scale) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return new DustParticleOptions(new Vector3f(r, g, b), scale);
    }

    private static void guard(String where, Throwable t) {
        if (REPORTED_ERRORS.add(where + ":" + t.getClass().getName())) {
            LOGGER.error("[deep_lucky_block] Erreur dans EnchantmentComboHandler#{}", where, t);
        }
    }

    private static boolean isWeedPlant(BlockState ws) {
        return ws.is(Blocks.SHORT_GRASS) || ws.is(Blocks.TALL_GRASS)
                || ws.is(Blocks.FERN) || ws.is(Blocks.LARGE_FERN) || ws.is(Blocks.DEAD_BUSH);
    }

    private static boolean isFriendlySummon(LivingEntity candidate, LivingEntity owner) {
        if (candidate == null || owner == null) return false;
        return candidate.getPersistentData().hasUUID("slb_summon_owner")
                && candidate.getPersistentData().getUUID("slb_summon_owner").equals(owner.getUUID());
    }

    private static void repairMostDamagedArmor(LivingEntity entity, int amount) {
        ItemStack worst = ItemStack.EMPTY;
        int maxDmg = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = entity.getItemBySlot(slot);
            if (!st.isEmpty() && st.isDamageableItem() && st.getDamageValue() > maxDmg) {
                maxDmg = st.getDamageValue();
                worst = st;
            }
        }
        if (!worst.isEmpty()) {
            worst.setDamageValue(Math.max(0, worst.getDamageValue() - amount));
        }
    }

    // =========================================================================
    // 1. TICK ENTITÉ (CLIENT + SERVEUR) : Notifications + Mobilité + Auras
    // =========================================================================
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !entity.isAlive()) return;
        try {
            if (entity.level().isClientSide()) {
                handleClientComboMovement(entity);
                return;
            }
            handleServerComboTick(entity);
        } catch (Throwable t) {
            guard("onEntityTick", t);
        }
    }

    private static void handleClientComboMovement(LivingEntity entity) {
        if (!(entity instanceof Player player)) return;
        ComboMask mask = EnchantmentComboRegistry.getActiveMask(player);
        if (mask.isEmpty()) return;

        // #011 Porteur Infatigable : manger/boire en marchant ne ralentit presque plus
        if (mask.has(IDX_PORTEUR_INFATIGABLE) && player.isUsingItem() && player.onGround()) {
            ItemStack use = player.getUseItem();
            if (use.get(net.minecraft.core.component.DataComponents.FOOD) != null || use.is(Items.POTION)) {
                Vec3 dm = player.getDeltaMovement();
                if (dm.horizontalDistanceSqr() > 1.0e-4 && dm.horizontalDistanceSqr() < 0.05) {
                    player.setDeltaMovement(dm.x * 1.32, dm.y, dm.z * 1.32);
                }
            }
        }

        // #014 Plongeur en Apnée : +25 % vitesse de remontée sous l'eau
        if (mask.has(IDX_PLONGEUR_APNEE) && player.isUnderWater() && player.getDeltaMovement().y > 0.01) {
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x, Math.min(0.42, dm.y * 1.25), dm.z);
        }

        // #034 Acrobate de Roulade : s'accroupir juste avant de toucher le sol propulse en avant
        if (mask.has(IDX_ACROBATE_ROULADE) && !player.onGround() && player.fallDistance > 2.2f
                && player.isShiftKeyDown() && !player.level().getBlockState(player.blockPosition().below(2)).isAir()) {
            Vec3 lk = player.getLookAngle();
            Vec3 flat = new Vec3(lk.x, 0, lk.z);
            if (flat.lengthSqr() > 1.0e-4) {
                flat = flat.normalize().scale(0.85);
                player.setDeltaMovement(flat.x, 0.12, flat.z);
                player.fallDistance = 0.0f;
            }
        }

        // #055 Pilote Rase-Mottes : voler en Élytres à < 5 blocs du sol maintient la vitesse !
        if (mask.has(IDX_PILOTE_RASE_MOTTES) && player.isFallFlying()) {
            boolean nearGround = false;
            BlockPos bp = player.blockPosition();
            for (int d = 1; d <= 5; d++) {
                if (!player.level().getBlockState(bp.below(d)).isAir()) {
                    nearGround = true;
                    break;
                }
            }
            if (nearGround) {
                Vec3 dm = player.getDeltaMovement();
                double spd = dm.length();
                if (spd > 0.25 && spd < 1.35) {
                    player.setDeltaMovement(dm.scale(1.045));
                }
            }
        }

        // #063 Pont Dimensionnel : tunnel supersonique aérien
        if (mask.has(IDX_PONT_DIMENSIONNEL) && !player.onGround() && player.isSprinting()) {
            Vec3 dm = player.getDeltaMovement();
            if (dm.horizontalDistanceSqr() > 0.04 && dm.horizontalDistanceSqr() < 0.85) {
                player.setDeltaMovement(dm.x * 1.06, Math.max(dm.y, 0.0), dm.z * 1.06);
            }
        }

        // #068 Commando Grappin : visée stable contre un mur
        if (mask.has(IDX_COMMANDO_GRAPPIN) && !player.onGround()
                && player.isUsingItem() && UniversalEnchantmentHandler.isTouchingHorizontalWall(player)) {
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x, Math.max(dm.y, -0.01), dm.z);
            player.fallDistance = 0.0F;
        }

        // #092 Pluie Cataclysmique : Bullet-Time aérien à l'arc ou à l'arbalète
        if (mask.has(IDX_PLUIE_CATACLYSMIQUE) && !player.onGround()
                && player.isUsingItem()
                && (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.CROSSBOW))) {
            Vec3 dm = player.getDeltaMovement();
            if (dm.y < -0.02) {
                player.setDeltaMovement(dm.x * 0.92, -0.02, dm.z * 0.92);
                player.fallDistance = 0.0F;
            }
        }

        // #093 Avatar du Zéphyr : +30 % vitesse aérienne
        if (mask.has(IDX_AVATAR_ZEPHYR) && !player.onGround() && !player.isFallFlying()) {
            Vec3 dm = player.getDeltaMovement();
            if (dm.horizontalDistanceSqr() > 0.01 && dm.horizontalDistanceSqr() < 0.55) {
                player.setDeltaMovement(dm.x * 1.04, dm.y, dm.z * 1.04);
            }
        }
    }

    private static void handleServerComboTick(LivingEntity entity) {
        if (!(entity instanceof Player) && (entity.tickCount % 10 != 0)) return;
        ComboMask mask = EnchantmentComboRegistry.getActiveMask(entity);
        PlayerComboState st = stateOf(entity);
        long now = entity.level().getGameTime();

        // Notification temps réel à l'activation d'un nouveau combo
        if (entity instanceof ServerPlayer sp) {
            ComboMask newlyOn = mask.andNot(st.prevServerMask);
            if (!newlyOn.isEmpty() && sp.tickCount > 20) {
                for (ComboDef c : COMBOS) {
                    if (newlyOn.has(c.index())) {
                        MutableComponent banner = TranslationFallback.translatableOrFallback(
                                "tooltip.deep_lucky_block.combo_activated", "\u2726 Synergie Activ\u00e9e : ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(c.translatedName())
                                .append(" ")
                                .append(c.tier().badge());
                        sp.displayClientMessage(banner, true);
                        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                                c.tier() == ComboTier.ULTRA_RARE ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.AMETHYST_BLOCK_CHIME,
                                SoundSource.PLAYERS, c.tier() == ComboTier.ULTRA_RARE ? 0.65f : 0.95f, 1.35f);
                        EnchantmentParticleFX.crownHalo(sp, dustOf(c.rgb(), 1.15f), c.tier() == ComboTier.ULTRA_RARE ? 20 : 12);
                        break;
                    }
                }
            }
            st.prevServerMask = mask;
        }

        if (mask.isEmpty()) {
            st.prevY = entity.getY();
            st.prevPos = entity.position();
            st.wasFallFlying = entity.isFallFlying();
            st.wasSprinting = entity.isSprinting();
            st.wasShiftDown = entity.isShiftKeyDown();
            return;
        }

        ServerLevel sl = (entity.level() instanceof ServerLevel s) ? s : null;

        // #001 Garde Fantassin : lever le bouclier confère Résistance I
        if (mask.has(IDX_GARDE_FANTASSIN) && entity.isBlocking() && entity.tickCount % 15 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 35, 0, true, false));
        }

        // #003 Collecteur Aimanté : aspire les orbes d'XP proches + endurance légère à la collecte
        if (mask.has(IDX_COLLECTEUR_AIMANTE) && sl != null && entity.tickCount % 10 == 0) {
            for (ExperienceOrb orb : sl.getEntitiesOfClass(ExperienceOrb.class, entity.getBoundingBox().inflate(7.5))) {
                Vec3 pull = entity.position().add(0, 0.5, 0).subtract(orb.position());
                if (pull.lengthSqr() > 0.05) {
                    orb.setDeltaMovement(orb.getDeltaMovement().scale(0.5).add(pull.normalize().scale(0.32)));
                }
            }
            for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(2.2))) {
                if (!item.hasPickUpDelay() && entity instanceof Player pl && entity.tickCount % 40 == 0) {
                    pl.getFoodData().eat(0, 0.15f);
                    break;
                }
            }
        }

        // #004 Campeur Endurci : immobile 3 secondes hors combat -> Régénération I + dissipe Faim
        if (mask.has(IDX_CAMPEUR_ENDURCI)) {
            if (st.prevPos != null && entity.position().distanceToSqr(st.prevPos) < 0.002 && entity.hurtTime == 0) {
                st.stillTicks++;
                if (st.stillTicks >= 60 && entity.tickCount % 25 == 0) {
                    entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false));
                    entity.removeEffect(MobEffects.HUNGER);
                }
            } else {
                st.stillTicks = 0;
            }
        }

        // #011 Porteur Infatigable : finir/utiliser nourriture octroie +1 cœur d'Absorption
        if (mask.has(IDX_PORTEUR_INFATIGABLE) && entity.isUsingItem() && entity.getUseItemRemainingTicks() == 2) {
            ItemStack use = entity.getUseItem();
            if (use.get(net.minecraft.core.component.DataComponents.FOOD) != null || use.is(Items.POTION)) {
                entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), 2.0f));
            }
        }

        // #014 Plongeur en Apnée : Vision Nocturne sous l'eau
        if (mask.has(IDX_PLONGEUR_APNEE) && entity.isUnderWater() && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
        }

        // #020 Cavalier Royal : sur monture -> Régénération I monture + onde sabots
        if (mask.has(IDX_CAVALIER_ROYAL) && entity.getVehicle() instanceof LivingEntity mount && entity.tickCount % 25 == 0) {
            mount.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            mount.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
            if (sl != null && mount.getDeltaMovement().horizontalDistanceSqr() > 0.04) {
                EnchantmentParticleFX.shockwaveRing(sl, mount.getX(), mount.getY() + 0.05, mount.getZ(),
                        EnchantmentParticleFX.GOLD_DIVINE, 1.4, 8, 0.02);
            }
        }

        // #026 Archer Rebond : marquer un rebond récent pour charger la flèche suivante
        if (mask.has(IDX_ARCHER_REBOND) && !entity.onGround() && entity.getDeltaMovement().y > 0.32) {
            st.bounceReadyUntil = now + 50L;
        }

        // #027 Plongeur Abyssal : sous l'eau sous Y=45 -> Force II + Célérité II + air préservé
        if (mask.has(IDX_PLONGEUR_ABYSSAL) && entity.isUnderWater() && entity.getY() < 45.0 && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 50, 1, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 1, true, false));
            entity.setAirSupply(Math.min(entity.getMaxAirSupply(), entity.getAirSupply() + 18));
        }

        // #028 Alpiniste de la Stratosphère : Y >= 110 -> Vitesse II + Saut II + régénération lente
        if (mask.has(IDX_ALPINISTE_STRATOSPHERE) && entity.getY() >= 110.0 && entity.tickCount % 30 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, true, false));
            if (entity.tickCount % 90 == 0) entity.heal(1.0f);
        }

        // #032 Pêcheur Grappin & #057 Pêcheur de Monstres : hameçon planté dans un ennemi !
        if ((mask.has(IDX_PECHEUR_GRAPPIN) || mask.has(IDX_PECHEUR_MONSTRES) || mask.has(IDX_PECHEUR_DIMENSION_END))
                && entity instanceof Player fisher && fisher.fishing != null) {
            FishingHook hook = fisher.fishing;
            if (hook.getHookedIn() instanceof LivingEntity hooked && !hook.getPersistentData().getBoolean("slb_hook_proc")) {
                hook.getPersistentData().putBoolean("slb_hook_proc", true);
                if (mask.has(IDX_PECHEUR_GRAPPIN)) {
                    hooked.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    fisher.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
                }
                if (mask.has(IDX_PECHEUR_MONSTRES) && sl != null) {
                    safeHurt(hooked, fisher.damageSources().playerAttack(fisher), 8.0f);
                    EnchantmentParticleFX.burstOnEntity(hooked, ParticleTypes.ELECTRIC_SPARK, 14, 0.3, 0.05);
                    for (Mob m : sl.getEntitiesOfClass(Mob.class, fisher.getBoundingBox().inflate(3.5))) {
                        Vec3 push = m.position().subtract(fisher.position());
                        if (push.lengthSqr() > 0.01) {
                            m.setDeltaMovement(push.normalize().scale(0.65).add(0, 0.25, 0));
                            m.hurtMarked = true;
                        }
                    }
                }
            }
            // #078 Pêcheur Dimensionnel de l'End : accroupi -> téléportation vers le flotteur !
            if (mask.has(IDX_PECHEUR_DIMENSION_END) && fisher.isShiftKeyDown() && !st.wasShiftDown && hook.tickCount > 12) {
                fisher.teleportTo(hook.getX(), hook.getY() + 0.3, hook.getZ());
                fisher.fallDistance = 0.0f;
                if (sl != null) {
                    EnchantmentParticleFX.burstOnEntity(fisher, ParticleTypes.PORTAL, 18, 0.35, 0.08);
                    sl.playSound(null, fisher.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f);
                }
                hook.discard();
                fisher.fishing = null;
            }
        }

        // #034 Acrobate de Roulade : annule chute + Invisibilité 2 s
        if (mask.has(IDX_ACROBATE_ROULADE) && !entity.onGround() && entity.fallDistance > 2.2f
                && entity.isShiftKeyDown() && !entity.level().getBlockState(entity.blockPosition().below(2)).isAir()) {
            entity.fallDistance = 0.0f;
            entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 45, 0, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 1, true, false));
            if (sl != null) EnchantmentParticleFX.burstOnEntity(entity, ParticleTypes.CLOUD, 10, 0.3, 0.03);
        }

        // #041 Patineur d'Azote : Glissade cryogénique
        if (mask.has(IDX_PATINEUR_AZOTE) && entity.isShiftKeyDown()
                && now < entity.getPersistentData().getLong("slb_slide_active_until") && sl != null) {
            entity.clearFire();
            if ((entity.tickCount & 1) == 0) {
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.05, entity.getZ(),
                        EnchantmentParticleFX.FROST_CYAN, 1.15, 8, 0.02);
            }
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(2.6))) {
                if (!mob.isAlive() || isFriendlySummon(mob, entity)) continue;
                mob.setTicksFrozen(Math.max(mob.getTicksFrozen(), 200));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2));
                EnchantmentParticleFX.burstOnEntity(mob, ParticleTypes.SNOWFLAKE, 5, 0.25, 0.02);
            }
        }

        // #046 Maître du Wing Chun : à mains nues -> Célérité III permanente
        if (mask.has(IDX_MAITRE_WING_CHUN) && entity.getMainHandItem().isEmpty() && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 45, 2, true, false));
        }

        // #055 Pilote Rase-Mottes : fauche les ennemis au ras du sol en Élytres !
        if (mask.has(IDX_PILOTE_RASE_MOTTES) && entity.isFallFlying() && sl != null) {
            boolean nearGround = false;
            BlockPos bp = entity.blockPosition();
            for (int d = 1; d <= 5; d++) {
                if (!sl.getBlockState(bp.below(d)).isAir()) { nearGround = true; break; }
            }
            if (nearGround) {
                Vec3 dm = entity.getDeltaMovement();
                if (dm.length() > 0.25 && dm.length() < 1.35) {
                    entity.setDeltaMovement(dm.scale(1.045));
                    entity.hurtMarked = true;
                }
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(2.4))) {
                    if (!isFriendlySummon(mob, entity)) {
                        safeHurt(mob, entity.damageSources().flyIntoWall(), 6.0f);
                    }
                }
            }
        }

        // #056 Tourelle Aérofrein : s'accroupir en plein vol d'Élytres fige en l'air 3 s + révèle les cibles !
        if (mask.has(IDX_TOURELLE_AEROFREIN) && sl != null) {
            if (entity.isFallFlying() && entity.isShiftKeyDown() && now >= st.airBrakeCooldownUntil) {
                st.airBrakeUntil = now + 60L;
                st.airBrakeCooldownUntil = now + 160L;
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(24.0))) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                }
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY(), entity.getZ(),
                        EnchantmentParticleFX.OVERCLOCK_CYAN, 2.2, 16, 0.02);
            }
            if (now < st.airBrakeUntil && !entity.onGround()) {
                entity.setDeltaMovement(0.0, 0.01, 0.0);
                entity.hurtMarked = true;
                entity.fallDistance = 0.0f;
            }
        }

        // #060 Forgeron Souterrain : sous Y=32 -> Célérité II permanente + anti-étouffement
        if (mask.has(IDX_FORGERON_SOUTERRAIN) && entity.getY() < 32.0 && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 1, true, false));
        }

        // #061 Peste Ambulante : aura empoisonnée + Faiblesse I sur ennemis proches
        if (mask.has(IDX_PESTE_AMBULANTE) && sl != null && entity.tickCount % 25 == 0) {
            EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.1, entity.getZ(),
                    dustOf(0x65A30D, 1.0f), 3.2, 10, 0.02);
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(4.5))) {
                if (!isFriendlySummon(mob, entity)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.POISON, 70, 0));
                    mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 0));
                }
            }
        }

        // #063 Pont Dimensionnel : propulsion aérienne + immunité projectiles
        if (mask.has(IDX_PONT_DIMENSIONNEL) && entity.isSprinting() && !st.wasSprinting && !entity.onGround()) {
            Vec3 lk = entity.getLookAngle();
            Vec3 flat = new Vec3(lk.x, 0.0, lk.z);
            if (flat.lengthSqr() > 1.0e-4) {
                flat = flat.normalize();
                entity.setDeltaMovement(flat.x * 1.15, 0.05, flat.z * 1.15);
                entity.hurtMarked = true;
                st.horizonProjectileImmuneUntil = now + 35L;
                if (sl != null) {
                    EnchantmentParticleFX.cloudCushion(sl, entity.getX(), entity.getY() + 0.05, entity.getZ(), 1.10, true);
                }
            }
        }

        // #065 Duelliste Éclair : charge latérale
        if (mask.has(IDX_DUELLISTE_ECLAIR)) {
            Vec3 m = entity.getDeltaMovement();
            double hSpd = m.horizontalDistance();
            if (hSpd > 0.03) {
                Vec3 lk = entity.getLookAngle();
                double flatLk = Math.sqrt(lk.x * lk.x + lk.z * lk.z);
                double dot = (m.x * lk.x + m.z * lk.z) / Math.max(1.0e-4, hSpd * flatLk);
                if (Math.abs(dot) < 0.55) {
                    st.lateralChargeUntil = now + 50L;
                }
            }
        }

        // #069 Artilleur Inquisiteur : salve d'arbalète -> Vitesse II + 30% remboursement
        if (mask.has(IDX_ARTILLEUR_INQUISITEUR)) {
            ItemStack main = entity.getMainHandItem();
            boolean nowCharged = main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main);
            boolean prevCharged = entity.getPersistentData().getBoolean("slb_combo24_charged");
            if (prevCharged && !nowCharged) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 55, 1, true, false));
                if (entity instanceof Player pl && entity.getRandom().nextInt(100) < 30) {
                    pl.getInventory().add(new ItemStack(Items.ARROW, 1));
                }
                if (sl != null) {
                    EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.1, entity.getZ(),
                            ParticleTypes.FLAME, 1.1, 10, 0.03);
                }
            }
            entity.getPersistentData().putBoolean("slb_combo24_charged", nowCharged);
        }

        // #072 Bélier aux Ailes de Fer : percuter des ennemis en plein vol d'Élytres !
        if (mask.has(IDX_BELIER_AILES_FER) && entity.isFallFlying() && sl != null && entity.getDeltaMovement().lengthSqr() > 0.25) {
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(1.8))) {
                if (!isFriendlySummon(mob, entity)) {
                    safeHurt(mob, entity.damageSources().flyIntoWall(), 11.0f);
                    Vec3 ram = entity.getDeltaMovement().normalize().scale(1.15).add(0, 0.42, 0);
                    mob.setDeltaMovement(ram);
                    mob.hurtMarked = true;
                    EnchantmentParticleFX.burstOnEntity(mob, ParticleTypes.CRIT, 12, 0.3, 0.08);
                }
            }
        }

        // #080 Colosse Franchisseur : franchir un dénivelé >= 1.45 bloc déclenche une onde sismique !
        if (mask.has(IDX_COLOSSE_FRANCHISSEUR) && !Double.isNaN(st.prevY)) {
            double dy = entity.getY() - st.prevY;
            if (dy >= 1.45 && entity.onGround() && sl != null) {
                float dmg = (float) Math.min(18.0, 3.0 + dy * 1.4);
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.08, entity.getZ(),
                        EnchantmentParticleFX.EARTH_OCHRE, 3.6, 20, 0.05);
                sl.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45f, 0.75f);
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(4.5))) {
                    if (!mob.isAlive() || isFriendlySummon(mob, entity)) continue;
                    safeHurt(mob, entity.damageSources().mobAttack(entity), dmg);
                    mob.setDeltaMovement(mob.getDeltaMovement().x, 0.46, mob.getDeltaMovement().z);
                    mob.hurtMarked = true;
                }
            }
        }

        // #081 Ange à la Plume Céleste : planer dans les airs régénère, purifie et brûle les morts-vivants
        if (mask.has(IDX_ANGE_PLUME_CELESTE) && !entity.onGround()
                && !entity.isShiftKeyDown() && entity.getDeltaMovement().y < 0.0 && entity.tickCount % 30 == 0) {
            entity.heal(1.0f);
            entity.removeEffect(MobEffects.WITHER);
            entity.removeEffect(MobEffects.POISON);
            if (sl != null) {
                EnchantmentParticleFX.wingRibbons(entity, EnchantmentParticleFX.GOLD_DIVINE);
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(6.0, 8.0, 6.0))) {
                    if (mob.getType().is(EntityTypeTags.UNDEAD)) {
                        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                        mob.setRemainingFireTicks(Math.max(mob.getRemainingFireTicks(), 40));
                    }
                }
            }
        }

        // #085 Titan Tellurique : ancrage anti-projection verticale
        if (mask.has(IDX_TITAN_TELLURIQUE) && entity.hurtTime > 0 && entity.getDeltaMovement().y > 0.25) {
            Vec3 dm = entity.getDeltaMovement();
            entity.setDeltaMovement(dm.x * 0.4, 0.05, dm.z * 0.4);
            entity.hurtMarked = true;
        }

        // #086 Berserker Immortel : sous 35 % PV -> Célérité III frénétique
        if (mask.has(IDX_BERSERKER_IMMORTEL) && entity.getHealth() <= entity.getMaxHealth() * 0.35f) {
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, 2, true, false));
            if (sl != null && entity.tickCount % 12 == 0) {
                EnchantmentParticleFX.spiralHelix(entity, EnchantmentParticleFX.CRIMSON_BLOOD, 0.72, 6);
            }
        }

        // #088 Avatar Infernal : sur magma/lave ou en feu -> Régénération II + Force I + aura incendiaire
        if (mask.has(IDX_AVATAR_INFERNAL)) {
            BlockState below = entity.level().getBlockState(entity.blockPosition().below());
            boolean onFireOrMagma = entity.isOnFire() || entity.isInLava()
                    || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.OBSIDIAN) || below.is(Blocks.LAVA);
            if (onFireOrMagma && entity.tickCount % 20 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 1, true, false));
                entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 50, 0, true, false));
                if (sl != null) {
                    EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.08, entity.getZ(),
                            ParticleTypes.FLAME, 2.2, 12, 0.02);
                    for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(3.0))) {
                        if (!isFriendlySummon(mob, entity)) mob.setRemainingFireTicks(Math.max(mob.getRemainingFireTicks(), 60));
                    }
                }
            }
        }

        // #089 Seigneur des Abysses : air infini + Grâce du Dauphin sous pluie/eau
        if (mask.has(IDX_SEIGNEUR_ABYSSES) && entity.isInWaterOrRain()) {
            entity.setAirSupply(entity.getMaxAirSupply());
            if (entity.tickCount % 20 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 50, 1, true, false));
            }
        }

        // #090 Faucon Ouragan : cratère d'ouragan à l'atterrissage d'Élytres !
        if (mask.has(IDX_FAUCON_OURAGAN)) {
            if (entity.isFallFlying()) {
                st.lastFlightSpeed = Math.max(st.lastFlightSpeed * 0.96, entity.getDeltaMovement().length());
            } else if (st.wasFallFlying && entity.onGround() && st.lastFlightSpeed > 0.45 && sl != null) {
                float slamDmg = (float) Math.min(26.0, 6.0 + st.lastFlightSpeed * 10.0);
                EnchantmentParticleFX.cloudCushion(sl, entity.getX(), entity.getY() + 0.08, entity.getZ(), 2.0, true);
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.12, entity.getZ(),
                        ParticleTypes.GUST_EMITTER_SMALL, 4.5, 10, 0.04);
                sl.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.75f, 1.25f);
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(6.0))) {
                    if (!mob.isAlive() || isFriendlySummon(mob, entity)) continue;
                    safeHurt(mob, entity.damageSources().flyIntoWall(), slamDmg);
                    Vec3 away = mob.position().subtract(entity.position());
                    if (away.lengthSqr() < 0.01) away = new Vec3(0, 0.5, 0);
                    mob.setDeltaMovement(away.normalize().scale(0.85).add(0, 0.52, 0));
                    mob.hurtMarked = true;
                }
                st.lastFlightSpeed = 0.0;
            }
        }

        // #092 Pluie Cataclysmique : Bullet-Time serveur (arc & arbalète)
        if (mask.has(IDX_PLUIE_CATACLYSMIQUE) && !entity.onGround()
                && entity.isUsingItem()
                && (entity.getUseItem().is(Items.BOW) || entity.getUseItem().is(Items.CROSSBOW))) {
            Vec3 dm = entity.getDeltaMovement();
            if (dm.y < -0.02) {
                entity.setDeltaMovement(dm.x * 0.92, -0.02, dm.z * 0.92);
                entity.fallDistance = 0.0F;
            }
        }

        // ---- ULTRA-RARE PASSIVES (#096 .. #109) ----

        // #096 Armure Éternelle Transcendante : immunité Wither/Lenteur/Lévitation + couronne dorée
        if (mask.has(IDX_ARMURE_ETERNELLE_TRANSCENDANTE) && entity.tickCount % 20 == 0) {
            entity.removeEffect(MobEffects.WITHER);
            entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            entity.removeEffect(MobEffects.LEVITATION);
            if (sl != null) EnchantmentParticleFX.crownHalo(entity, EnchantmentParticleFX.GOLD_DIVINE, 8);
        }

        // #097 Dieu de la Lame Suprême : Force II continue
        if (mask.has(IDX_DIEU_LAME_SUPREME) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 50, 1, true, false));
        }

        // #098 Exterminateur de Titans Absolu : immunité totale au recul
        if (mask.has(IDX_EXTERMINATEUR_TITANS_ABSOLU) && entity.hurtTime == 9) {
            entity.setDeltaMovement(0.0, Math.min(0.0, entity.getDeltaMovement().y), 0.0);
            entity.hurtMarked = true;
        }

        // #100 Archange du Jugement Solaire : Régénération I au soleil + ailes dorées
        if (mask.has(IDX_ARCHANGE_JUGEMENT_SOLAIRE) && entity.tickCount % 25 == 0) {
            if (entity.level().isDay() && entity.level().canSeeSky(entity.blockPosition())) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 55, 0, true, false));
            }
            if (sl != null) EnchantmentParticleFX.wingRibbons(entity, EnchantmentParticleFX.GOLD_DIVINE);
        }

        // #101 Poséidon du Cataclysme : soin continu sous la pluie / dans l'eau
        if (mask.has(IDX_POSEIDON_CATACLYSME) && entity.isInWaterOrRain() && entity.tickCount % 25 == 0) {
            entity.heal(1.5f);
            entity.setAirSupply(entity.getMaxAirSupply());
        }

        // #103 Forge du Créateur Éternel : Célérité IV permanente
        if (mask.has(IDX_FORGE_CREATEUR_ETERNEL) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 3, true, false));
        }

        // #104 Fantôme de l'Éclipse Totale : dans l'ombre ou la nuit -> Vitesse III
        if (mask.has(IDX_FANTOME_ECLIPSE_TOTALE) && entity.tickCount % 20 == 0) {
            int light = entity.level().getMaxLocalRawBrightness(entity.blockPosition());
            if (!entity.level().isDay() || light <= 8) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 2, true, false));
            }
        }

        // #105 Seigneur de Guerre de l'Apocalypse : immunité Feu/Wither + aura volcanique royale
        if (mask.has(IDX_SEIGNEUR_GUERRE_APOCALYPSE) && entity.tickCount % 20 == 0) {
            entity.clearFire();
            entity.removeEffect(MobEffects.WITHER);
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 50, 0, true, false));
            if (sl != null) {
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.08, entity.getZ(),
                        ParticleTypes.SOUL_FIRE_FLAME, 2.5, 10, 0.02);
            }
        }

        // #106 Maître du Temps Immuable : répare tout votre équipement chaque seconde !
        if (mask.has(IDX_MAITRE_TEMPS_IMMUABLE) && entity.tickCount % 20 == 0) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack eq = entity.getItemBySlot(slot);
                if (!eq.isEmpty() && eq.isDamageableItem() && eq.getDamageValue() > 0) {
                    eq.setDamageValue(Math.max(0, eq.getDamageValue() - 1));
                }
            }
        }

        // #108 Inquisiteur à l'Arbalète de Soufre : 50 % remboursement de munition au tir d'arbalète !
        if (mask.has(IDX_INQUISITEUR_ARBALETE_SOUFRE)) {
            ItemStack main = entity.getMainHandItem();
            boolean nowCh = main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main);
            boolean prevCh = entity.getPersistentData().getBoolean("slb_combo108_charged");
            if (prevCh && !nowCh && entity instanceof Player pl && entity.getRandom().nextBoolean()) {
                pl.getInventory().add(new ItemStack(Items.ARROW, 1));
            }
            entity.getPersistentData().putBoolean("slb_combo108_charged", nowCh);
        }

        // #109 Avatar Suprême du Deep Lucky : Chance III + Régénération I + Halo Stellaire Blanc !
        if (mask.has(IDX_AVATAR_SUPREME_DEEP_LUCKY) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.LUCK, 55, 2, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 55, 0, true, false));
            if (sl != null) {
                EnchantmentParticleFX.crownHalo(entity, dustOf(0xFFFFFF, 1.25f), 14);
                sl.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + 2.05, entity.getZ(), 2, 0.35, 0.1, 0.35, 0.01);
            }
        }

        st.prevY = entity.getY();
        st.prevPos = entity.position();
        st.wasFallFlying = entity.isFallFlying();
        st.wasSprinting = entity.isSprinting();
        st.wasShiftDown = entity.isShiftKeyDown();
    }

    // =========================================================================
    // 2. COMBAT OFFENSIF & DÉFENSIF (LivingIncomingDamageEvent)
    // =========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        if (COMBO_HURT_GUARD.get()) return;
        try {
            LivingEntity victim = event.getEntity();
            Entity rawAttacker = event.getSource().getEntity();
            long now = victim.level().getGameTime();

            // Vulnérabilité posée par #039 Pelle Hallebardier (+25 % dégâts suivants)
            if (now < victim.getPersistentData().getLong("slb_halberd_vuln_until")) {
                event.setAmount(event.getAmount() * 1.25f);
            }

            // --- A. Défense du porteur d'un combo ---
            ComboMask defMask = EnchantmentComboRegistry.getActiveMask(victim);
            if (!defMask.isEmpty()) {
                PlayerComboState defSt = stateOf(victim);

                // #012 Apiculteur Serein : -30 % dégâts des Phantoms, Vex, Abeilles + les repousse
                if (defMask.has(IDX_APICULTEUR_SEREIN) && (rawAttacker instanceof Phantom || rawAttacker instanceof Vex || rawAttacker instanceof Bee)) {
                    event.setAmount(event.getAmount() * 0.70f);
                    Vec3 away = rawAttacker.position().subtract(victim.position());
                    if (away.lengthSqr() > 0.01) rawAttacker.setDeltaMovement(away.normalize().scale(0.75).add(0, 0.25, 0));
                }

                // #021 Glouton Carnassier : barre de faim >= 18 -> -15 % dégâts reçus
                if (defMask.has(IDX_GLOUTON_CARNASSIER) && victim instanceof Player vp && vp.getFoodData().getFoodLevel() >= 18) {
                    event.setAmount(event.getAmount() * 0.85f);
                }

                // #029 Duelliste Solitaire : 1 seul ennemi à <= 7 blocs -> -20 % dégâts subis
                if (defMask.has(IDX_DUELLISTE_SOLITAIRE) && victim.level() instanceof ServerLevel slSolo) {
                    int foes = slSolo.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(7.0), m -> m.isAlive() && !isFriendlySummon(m, victim)).size();
                    if (foes <= 1) event.setAmount(event.getAmount() * 0.80f);
                }

                // #036 Garde Ignifuge : convertit les dégâts de feu/lave en +2 cœurs de soin direct !
                if (defMask.has(IDX_GARDE_IGNIFUGE) && event.getSource().is(DamageTypeTags.IS_FIRE)) {
                    event.setCanceled(true);
                    victim.clearFire();
                    if (now >= victim.getPersistentData().getLong("slb_fire_heal_cd")) {
                        victim.getPersistentData().putLong("slb_fire_heal_cd", now + 30L);
                        victim.heal(4.0f);
                    }
                    return;
                }

                // #060 Forgeron Souterrain : immunité à l'étouffement dans les blocs
                if (defMask.has(IDX_FORGERON_SOUTERRAIN) && "inWall".equals(event.getSource().getMsgId())) {
                    event.setCanceled(true);
                    return;
                }

                // #063 Pont Dimensionnel : immunité projectiles pendant le tunnel de vent
                if (defMask.has(IDX_PONT_DIMENSIONNEL) && now < defSt.horizonProjectileImmuneUntil
                        && event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                    event.setCanceled(true);
                    return;
                }

                // #068 Commando Grappin : anti-chute post-grappin
                if (defMask.has(IDX_COMMANDO_GRAPPIN) && now < defSt.grappleShieldUntil && "fall".equals(event.getSource().getMsgId())) {
                    event.setCanceled(true);
                    return;
                }

                // #072 Bélier aux Ailes de Fer & #090 Faucon Ouragan : 0 dégât cinétique/chute en Élytres
                if ((defMask.has(IDX_BELIER_AILES_FER) || defMask.has(IDX_FAUCON_OURAGAN))
                        && ("fall".equals(event.getSource().getMsgId()) || "flyIntoWall".equals(event.getSource().getMsgId()))) {
                    event.setCanceled(true);
                    return;
                }

                // #093 Avatar du Zéphyr : 25 % de chance de dévier les projectiles !
                if (defMask.has(IDX_AVATAR_ZEPHYR) && event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                        && victim.getRandom().nextInt(100) < 25) {
                    event.setCanceled(true);
                    EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CLOUD, 10, 0.3, 0.04);
                    return;
                }

                // #104 Fantôme de l'Éclipse Totale : 30 % d'esquive fantomatique totale dans l'ombre/nuit !
                if (defMask.has(IDX_FANTOME_ECLIPSE_TOTALE)) {
                    int light = victim.level().getMaxLocalRawBrightness(victim.blockPosition());
                    if ((!victim.level().isDay() || light <= 8) && victim.getRandom().nextInt(100) < 30) {
                        event.setCanceled(true);
                        EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.SHADOW_VIOLET, 12, 0.35, 0.04);
                        return;
                    }
                }

                // #085 Titan Tellurique : subir un coup confère Célérité II
                if (defMask.has(IDX_TITAN_TELLURIQUE)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 1, true, false));
                }

                // #087 Vengeance du Warden : impulsion sonique Sculk révélant les ennemis à 16 blocs
                if (defMask.has(IDX_VENGEANCE_WARDEN) && rawAttacker instanceof LivingEntity
                        && victim.level() instanceof ServerLevel slWarden) {
                    EnchantmentParticleFX.shockwaveRing(slWarden, victim.getX(), victim.getY() + 0.9, victim.getZ(),
                            ParticleTypes.SCULK_SOUL, 3.2, 16, 0.04);
                    for (Mob m : slWarden.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(16.0))) {
                        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                        m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    }
                }

                // #088 Avatar Infernal : double riposte du Noyau Ardent
                if (defMask.has(IDX_AVATAR_INFERNAL) && rawAttacker instanceof LivingEntity aggr) {
                    aggr.setRemainingFireTicks(Math.max(aggr.getRemainingFireTicks(), 120));
                    safeHurt(aggr, victim.damageSources().onFire(), 4.0f);
                }

                // #091 Renaissance Solaire : sous 22 % PV (60 s cd) -> Nova Solaire (+6 cœurs)
                if (defMask.has(IDX_RENAISSANCE_SOLAIRE)
                        && (victim.getHealth() - event.getAmount()) <= victim.getMaxHealth() * 0.22f
                        && now >= defSt.phoenixNovaReadyAt) {
                    defSt.phoenixNovaReadyAt = now + 1200L;
                    event.setAmount(event.getAmount() * 0.30f);
                    victim.heal(12.0f);
                    victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
                    victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0));
                    if (victim.level() instanceof ServerLevel slPhx) {
                        EnchantmentParticleFX.shockwaveRing(slPhx, victim.getX(), victim.getY() + 0.2, victim.getZ(),
                                ParticleTypes.FLAME, 3.5, 24, 0.06);
                        EnchantmentParticleFX.crownHalo(victim, EnchantmentParticleFX.GOLD_DIVINE, 16);
                        slPhx.playSound(null, victim.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.35f);
                    }
                }

                // #099 Seigneur de Sang Immortel : empêche un coup fatal toutes les 90 s en drainant la vie à 8 blocs (+8 cœurs) !
                if (defMask.has(IDX_SEIGNEUR_SANG_IMMORTEL)
                        && (victim.getHealth() - event.getAmount()) <= 0.5f
                        && now >= defSt.immortalBloodReadyAt) {
                    defSt.immortalBloodReadyAt = now + 1800L;
                    event.setCanceled(true);
                    victim.setHealth(Math.min(victim.getMaxHealth(), victim.getHealth() + 16.0f));
                    victim.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
                    if (victim.level() instanceof ServerLevel slBld) {
                        slBld.playSound(null, victim.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.85f, 0.75f);
                        EnchantmentParticleFX.shockwaveRing(slBld, victim.getX(), victim.getY() + 0.5, victim.getZ(),
                                EnchantmentParticleFX.CRIMSON_BLOOD, 5.0, 28, 0.05);
                        for (Mob m : slBld.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(8.0))) {
                            if (!isFriendlySummon(m, victim)) {
                                safeHurt(m, victim.damageSources().magic(), 8.0f);
                                EnchantmentParticleFX.tetherBeam(slBld, m.position().add(0, 0.8, 0),
                                        victim.position().add(0, 1.0, 0), EnchantmentParticleFX.CRIMSON_BLOOD, EnchantmentParticleFX.SCARLET_MIST, 0.6);
                            }
                        }
                    }
                    return;
                }

                // #096 Armure Éternelle Transcendante : -15 % dégâts subis
                if (defMask.has(IDX_ARMURE_ETERNELLE_TRANSCENDANTE)) {
                    event.setAmount(event.getAmount() * 0.85f);
                }

                // #109 Avatar Suprême du Deep Lucky : -25 % dégâts reçus !
                if (defMask.has(IDX_AVATAR_SUPREME_DEEP_LUCKY)) {
                    event.setAmount(event.getAmount() * 0.75f);
                }
            }

            // --- B. Soldat invoqué d'un Empereur de Légion (#083) ---
            if (rawAttacker instanceof Mob soldier && soldier.getPersistentData().hasUUID("slb_combo9_emperor")
                    && victim.level() instanceof ServerLevel slEmp) {
                Player emp = slEmp.getPlayerByUUID(soldier.getPersistentData().getUUID("slb_combo9_emperor"));
                if (emp != null && emp.isAlive()) emp.heal(0.6f);
            }

            // --- C. Attaque directe par un porteur de combo ---
            if (!(rawAttacker instanceof LivingEntity attacker)) return;
            ComboMask atkMask = EnchantmentComboRegistry.getActiveMask(attacker);
            if (atkMask.isEmpty()) return;
            PlayerComboState atkSt = stateOf(attacker);
            boolean isProjectile = event.getSource().is(DamageTypeTags.IS_PROJECTILE);
            ItemStack weapon = attacker.getMainHandItem();

            // #000 Éclaireur Agile : coup à l'épée -> Vitesse I (2,5 s) + fatigue de sprint réduite
            if (atkMask.has(IDX_ECLAIREUR_AGILE) && !isProjectile) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
                if (attacker instanceof Player pl) pl.getFoodData().eat(0, 0.12f);
            }

            // #002 Boxeur de Rue : à mains nues -> +3 dégâts contondants + recul sec
            if (atkMask.has(IDX_BOXEUR_RUE) && !isProjectile && weapon.isEmpty()) {
                event.setAmount(event.getAmount() + 3.0f);
                Vec3 kb = victim.position().subtract(attacker.position());
                if (kb.lengthSqr() > 0.01) {
                    victim.setDeltaMovement(kb.normalize().scale(0.65).add(0, 0.2, 0));
                    victim.hurtMarked = true;
                }
            }

            // #013 Bretteur Balayant : repousse davantage les groupes autour de la cible
            if (atkMask.has(IDX_BRETTEUR_BALAYANT) && !isProjectile && victim.level() instanceof ServerLevel slSwp) {
                for (Mob m : slSwp.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(2.8))) {
                    if (!isFriendlySummon(m, attacker)) {
                        Vec3 push = m.position().subtract(attacker.position());
                        if (push.lengthSqr() > 0.01) m.setDeltaMovement(push.normalize().scale(0.55).add(0, 0.18, 0));
                    }
                }
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            }

            // #016 Chasseur Opportuniste : attaque en tombant -> +20 % dégâts + étincelles
            if (atkMask.has(IDX_CHASSEUR_OPPORTUNISTE) && !isProjectile && attacker.fallDistance > 0.25f) {
                event.setAmount(event.getAmount() * 1.20f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CRIT, 10, 0.3, 0.06);
            }

            // #020 Cavalier Royal : sur monture -> +40 % dégâts de charge
            if (atkMask.has(IDX_CAVALIER_ROYAL) && attacker.isPassenger()) {
                event.setAmount(event.getAmount() * 1.40f);
            }

            // #021 Glouton Carnassier : faim >= 18 -> +30 % dégâts mêlée
            if (atkMask.has(IDX_GLOUTON_CARNASSIER) && !isProjectile
                    && attacker instanceof Player ap && ap.getFoodData().getFoodLevel() >= 18) {
                event.setAmount(event.getAmount() * 1.30f);
            }

            // #022 Traqueur Sanglant : cible sous 50 % PV -> Glowing + Vitesse II (4 s)
            if (atkMask.has(IDX_TRAQUEUR_SANGLANT) && victim.getHealth() <= victim.getMaxHealth() * 0.50f) {
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
            }

            // #024 Sapeur de Tranchée : frapper à la pelle -> Lenteur II + Faiblesse I + Absorption légère
            if (atkMask.has(IDX_SAPEUR_TRANCHEE) && !isProjectile && weapon.getItem() instanceof ShovelItem) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                attacker.setAbsorptionAmount(Math.max(attacker.getAbsorptionAmount(), 2.0f));
            }

            // #025 Faucheur Champêtre : attaque à la houe -> balayage 360° + fauche les hautes herbes
            if (atkMask.has(IDX_FAUCHEUR_CHAMPETRE) && !isProjectile && weapon.getItem() instanceof HoeItem
                    && victim.level() instanceof ServerLevel slHoe) {
                EnchantmentParticleFX.slashArc(attacker, dustOf(0x84CC16, 1.0f), ParticleTypes.SWEEP_ATTACK, 2.6, 14);
                float splash = event.getAmount() * 0.55f;
                for (Mob m : slHoe.getEntitiesOfClass(Mob.class, attacker.getBoundingBox().inflate(3.2))) {
                    if (m != victim && !isFriendlySummon(m, attacker)) safeHurt(m, attacker.damageSources().mobAttack(attacker), splash);
                }
                BlockPos center = attacker.blockPosition();
                for (BlockPos bp : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 1, 2))) {
                    if (isWeedPlant(slHoe.getBlockState(bp))) slHoe.destroyBlock(bp, true, attacker);
                }
            }

            // #029 Duelliste Solitaire : 1 seul ennemi à <= 7 blocs -> +30 % dégâts
            if (atkMask.has(IDX_DUELLISTE_SOLITAIRE) && victim.level() instanceof ServerLevel slSoloA) {
                int foes = slSoloA.getEntitiesOfClass(Mob.class, attacker.getBoundingBox().inflate(7.0), m -> m.isAlive() && !isFriendlySummon(m, attacker)).size();
                if (foes <= 1) event.setAmount(event.getAmount() * 1.30f);
            }

            // #030 Moine Rythmique : Célérité II + soigne 0,5 cœur tous les 4 coups
            if (atkMask.has(IDX_MOINE_RYTHMIQUE) && !isProjectile) {
                attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 1, true, false));
                atkSt.rhythmHitCounter++;
                if (atkSt.rhythmHitCounter >= 4) {
                    atkSt.rhythmHitCounter = 0;
                    attacker.heal(1.0f);
                }
            }

            // #033 Pyromane des Braises : cible déjà en feu -> +35 % dégâts + déflagration + Vitesse I
            if (atkMask.has(IDX_PYROMANE_BRAISE) && victim.isOnFire()) {
                event.setAmount(event.getAmount() * 1.35f);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.LAVA, 8, 0.3, 0.05);
            }

            // #037 Chasseur au Venin : cible empoisonnée -> +40 % dégâts critiques acides + prolonge poison
            if (atkMask.has(IDX_CHASSEUR_VENIN) && victim.hasEffect(MobEffects.POISON)) {
                event.setAmount(event.getAmount() * 1.40f);
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
            }

            // #039 Pelle Hallebardier : coup de pelle -> pose Vulnérabilité (+25 % dégâts suivants)
            if (atkMask.has(IDX_PELLE_HALLEBARDIER) && !isProjectile && weapon.getItem() instanceof ShovelItem) {
                victim.getPersistentData().putLong("slb_halberd_vuln_until", now + 100L);
            }

            // #046 Maître du Wing Chun : à mains nues -> +7 dégâts + rafale d'impact
            if (atkMask.has(IDX_MAITRE_WING_CHUN) && !isProjectile && weapon.isEmpty()) {
                event.setAmount(event.getAmount() + 7.0f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CRIT, 10, 0.25, 0.06);
            }

            // #047 Brise-Os du Colosse : sprinter + frapper -> Lenteur IV + Faiblesse II (3 s) + onde écarlate
            if (atkMask.has(IDX_BRISE_OS_COLOSSE) && !isProjectile && attacker.isSprinting()) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 14, 0.3, 0.04);
            }

            // #048 Inquisiteur Sacré : contre morts-vivants -> +50 % dégâts sacrés, feu solaire + +1 cœur
            if (atkMask.has(IDX_INQUISITEUR_SACRE) && victim.getType().is(EntityTypeTags.UNDEAD)) {
                event.setAmount(event.getAmount() * 1.50f);
                victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 100));
                attacker.heal(2.0f);
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.GOLD_DIVINE, 12, 0.3, 0.04);
            }

            // #049 Bourreau Décapiteur : exécute les monstres ordinaires sous 18 % PV !
            if (atkMask.has(IDX_BOURREAU_DECAPITEUR) && victim.getMaxHealth() < 80.0f
                    && !(victim instanceof Player)
                    && (victim.getHealth() - event.getAmount()) <= victim.getMaxHealth() * 0.18f) {
                event.setAmount(victim.getHealth() + 20.0f);
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 18, 0.35, 0.06);
            }

            // #051 Trident Pogo Marin : frapper au Trident en l'air -> rebond géant + geyser sous la cible !
            if (atkMask.has(IDX_TRIDENT_POGO_MARIN) && !isProjectile && !attacker.onGround() && weapon.is(Items.TRIDENT)) {
                Vec3 dm = attacker.getDeltaMovement();
                attacker.setDeltaMovement(dm.x, 0.88, dm.z);
                attacker.hurtMarked = true;
                attacker.fallDistance = 0.0f;
                victim.setDeltaMovement(victim.getDeltaMovement().x, 0.55, victim.getDeltaMovement().z);
                victim.hurtMarked = true;
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.SPLASH, 22, 0.35, 0.08);
            }

            // #054 Hallebardier Impérial : +45 % dégâts à la pelle à > 2,5 blocs + désarçonne cavaliers
            if (atkMask.has(IDX_HALLEBARDIER_IMPERIAL) && !isProjectile && weapon.getItem() instanceof ShovelItem) {
                if (attacker.distanceToSqr(victim) >= 6.25) event.setAmount(event.getAmount() * 1.45f);
                if (victim.isPassenger()) victim.stopRiding();
            }

            // #058 Écho du Duelliste : 0,5 s après chaque coup mêlée, une lame fantôme re-frappe pour 30 % !
            if (atkMask.has(IDX_ECHO_DUELLISTE) && !isProjectile && victim.level() instanceof ServerLevel slEcho) {
                float echoDmg = Math.max(1.5f, event.getAmount() * 0.30f);
                TestProcedure.schedule(slEcho, TestProcedure.currentTick(slEcho) + 10L, () -> {
                    if (victim.isAlive() && attacker.isAlive()) {
                        safeHurt(victim, attacker.damageSources().magic(), echoDmg);
                        EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.OVERCLOCK_CYAN, 10, 0.3, 0.04);
                    }
                });
            }

            // #059 Embuscade Chirurgicale : attaquer accroupi -> +55 % critique perforant
            if (atkMask.has(IDX_EMBUSCADE_CHIRURGICALE) && !isProjectile && attacker.isShiftKeyDown()) {
                event.setAmount(event.getAmount() * 1.55f);
                safeHurt(victim, attacker.damageSources().magic(), 3.0f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.ENCHANTED_HIT, 14, 0.3, 0.06);
            }

            // #064 Seigneur Vampire : vol de vie excédentaire converti en Cœurs d'Absorption (jusqu'à +6 cœurs)
            if (atkMask.has(IDX_SEIGNEUR_VAMPIRE)) {
                float healAmt = Math.min(4.5f, event.getAmount() * 0.20f);
                float missing = attacker.getMaxHealth() - attacker.getHealth();
                if (healAmt > missing) {
                    float overheal = healAmt - Math.max(0.0f, missing);
                    attacker.setHealth(attacker.getMaxHealth());
                    attacker.setAbsorptionAmount(Math.min(12.0f, attacker.getAbsorptionAmount() + overheal * 1.5f));
                } else {
                    attacker.heal(healAmt);
                }
                if (attacker instanceof Player pl && attacker.getRandom().nextInt(100) < 35) pl.getFoodData().eat(1, 0.35f);
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 7, 0.25, 0.02);
            }

            // #065 Duelliste Éclair : bond vers la cible (+35 % dégâts)
            if (atkMask.has(IDX_DUELLISTE_ECLAIR) && now < atkSt.lateralChargeUntil && !isProjectile) {
                atkSt.lateralChargeUntil = 0L;
                event.setAmount(event.getAmount() * 1.35f);
                Vec3 lunge = victim.position().subtract(attacker.position());
                if (lunge.lengthSqr() > 0.25) {
                    Vec3 step = lunge.normalize().scale(0.45);
                    attacker.setDeltaMovement(attacker.getDeltaMovement().add(step.x, 0.08, step.z));
                    attacker.hurtMarked = true;
                }
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 0));
                EnchantmentParticleFX.slashArc(attacker, EnchantmentParticleFX.OVERCLOCK_CYAN, ParticleTypes.SWEEP_ATTACK, 2.5, 13);
            }

            // #066 Bastion Miroir : riposte après parade (+75 % dégâts + brise bouclier + étourdit)
            if (atkMask.has(IDX_BASTION_MIROIR) && now < atkSt.parryRiposteUntil) {
                atkSt.parryRiposteUntil = 0L;
                event.setAmount(event.getAmount() * 1.75f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 3));
                if (victim instanceof Player vp) {
                    vp.getCooldowns().addCooldown(Items.SHIELD, 120);
                    vp.stopUsingItem();
                }
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CRIT, 16, 0.3, 0.08);
            }

            // #067 Œil de Faucon Astral : +25 % sur cible révélée + marque Glowing
            if (atkMask.has(IDX_OEIL_FAUCON_ASTRAL) && event.getSource().getDirectEntity() instanceof AbstractArrow) {
                if (victim.hasEffect(MobEffects.GLOWING)) event.setAmount(event.getAmount() * 1.25f);
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
            }

            // #077 Chasseur de Proie Marquée : frapper une cible Glowing -> +40 % dégâts + Vitesse II
            if (atkMask.has(IDX_CHASSEUR_PROIE_MARQUEE) && victim.hasEffect(MobEffects.GLOWING)) {
                event.setAmount(event.getAmount() * 1.40f);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
            }

            // #079 Berserker Sismique : attaque en plein sprint -> fissure tellurique projetant devant vous !
            if (atkMask.has(IDX_BERSERKER_SISMIQUE) && !isProjectile && attacker.isSprinting()
                    && victim.level() instanceof ServerLevel slSeis) {
                EnchantmentParticleFX.shockwaveRing(slSeis, victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        EnchantmentParticleFX.EARTH_OCHRE, 3.6, 20, 0.05);
                for (Mob m : slSeis.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(3.8))) {
                    if (!isFriendlySummon(m, attacker)) {
                        safeHurt(m, attacker.damageSources().mobAttack(attacker), 5.0f);
                        m.setDeltaMovement(m.getDeltaMovement().x, 0.62, m.getDeltaMovement().z);
                        m.hurtMarked = true;
                    }
                }
            }

            // #082 Assassin Nocturne : depuis l'Invisibilité -> téléportation dos + x2,10 dégâts + Obscurité
            if (atkMask.has(IDX_ASSASSIN_NOCTURNE) && attacker.hasEffect(MobEffects.INVISIBILITY)) {
                attacker.removeEffect(MobEffects.INVISIBILITY);
                event.setAmount(event.getAmount() * 2.10f);
                Vec3 tgtLook = victim.getLookAngle();
                Vec3 behind = victim.position().subtract(tgtLook.x * 1.15, 0.0, tgtLook.z * 1.15);
                if (attacker.level().noCollision(attacker, attacker.getBoundingBox().move(behind.subtract(attacker.position())))) {
                    attacker.teleportTo(behind.x, victim.getY(), behind.z);
                }
                victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.SHADOW_VIOLET, 14, 0.32, 0.03);
            }

            // #084 Faucheur de Tempête : toutes les 4 frappes mêlée -> Éclair Céleste en chaîne + Vitesse II
            if (atkMask.has(IDX_FAUCHEUR_TEMPETE) && !isProjectile) {
                atkSt.thunderHitCounter++;
                if (atkSt.thunderHitCounter >= 4 && victim.level() instanceof ServerLevel slTh) {
                    atkSt.thunderHitCounter = 0;
                    spawnVisualLightning(slTh, victim.position());
                    safeHurt(victim, attacker.damageSources().lightningBolt(), 7.0f);
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
                    for (Mob nearby : slTh.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(5.0))) {
                        if (nearby != victim && nearby.isAlive() && !isFriendlySummon(nearby, attacker)) {
                            safeHurt(nearby, attacker.damageSources().lightningBolt(), 4.5f);
                            EnchantmentParticleFX.tetherBeam(slTh, victim.position().add(0, 0.8, 0),
                                    nearby.position().add(0, 0.8, 0), ParticleTypes.ELECTRIC_SPARK, EnchantmentParticleFX.OVERCLOCK_CYAN, 0.5);
                        }
                    }
                }
            }

            // #085 Titan Tellurique : +35 % onde sismique aux frappes mêlée
            if (atkMask.has(IDX_TITAN_TELLURIQUE) && !isProjectile && victim.level() instanceof ServerLevel slTit) {
                EnchantmentParticleFX.shockwaveRing(slTit, victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        EnchantmentParticleFX.EARTH_OCHRE, 2.8, 14, 0.03);
                float splash = event.getAmount() * 0.35f;
                for (Mob m : slTit.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(2.8))) {
                    if (m != victim && m.isAlive() && !isFriendlySummon(m, attacker)) {
                        safeHurt(m, attacker.damageSources().mobAttack(attacker), splash);
                    }
                }
            }

            // #089 Seigneur des Abysses : +35 % Marée Pressurisée au Trident même à sec
            if (atkMask.has(IDX_SEIGNEUR_ABYSSES)
                    && (weapon.is(Items.TRIDENT) || event.getSource().getDirectEntity() instanceof ThrownTrident)) {
                event.setAmount(event.getAmount() * 1.35f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.SPLASH, 14, 0.35, 0.06);
            }

            // #094 Pacte Maudit de Verre : x2,25 dégâts abyssaux purs + vol de vie 15 % !
            if (atkMask.has(IDX_PACTE_MAUDIT_VERRE)) {
                event.setAmount(event.getAmount() * 2.25f);
                attacker.heal(Math.min(8.0f, event.getAmount() * 0.15f));
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.SHADOW_VIOLET, 16, 0.35, 0.05);
            }

            // ---- ULTRA-RARE OFFENSIVES (#096 .. #109) ----

            // #096 Armure Éternelle Transcendante : +20 % dégâts infligés
            if (atkMask.has(IDX_ARMURE_ETERNELLE_TRANSCENDANTE)) {
                event.setAmount(event.getAmount() * 1.20f);
            }

            // #097 Dieu de la Lame Suprême : chaque 3e frappe fend l'espace sur 7 blocs (60 % dégâts) !
            if (atkMask.has(IDX_DIEU_LAME_SUPREME) && !isProjectile && victim.level() instanceof ServerLevel slGod) {
                atkSt.supremeSlashCounter++;
                if (atkSt.supremeSlashCounter >= 3) {
                    atkSt.supremeSlashCounter = 0;
                    float waveDmg = event.getAmount() * 0.60f;
                    Vec3 lk = attacker.getLookAngle().normalize();
                    for (int step = 1; step <= 7; step++) {
                        Vec3 pt = attacker.getEyePosition().add(lk.scale(step));
                        slGod.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y - 0.3, pt.z, 1, 0.1, 0.1, 0.1, 0.0);
                        slGod.sendParticles(EnchantmentParticleFX.GOLD_DIVINE, pt.x, pt.y - 0.3, pt.z, 5, 0.25, 0.25, 0.25, 0.02);
                        for (Mob m : slGod.getEntitiesOfClass(Mob.class, new AABB(pt.x - 1.5, pt.y - 1.5, pt.z - 1.5, pt.x + 1.5, pt.y + 1.5, pt.z + 1.5))) {
                            if (m != victim && !isFriendlySummon(m, attacker)) {
                                safeHurt(m, attacker.damageSources().magic(), waveDmg);
                            }
                        }
                    }
                    slGod.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.7f);
                }
            }

            // #098 Exterminateur de Titans Absolu : +45 % dégâts contre Boss / monstres > 40 PV !
            if (atkMask.has(IDX_EXTERMINATEUR_TITANS_ABSOLU) && victim.getMaxHealth() >= 40.0f) {
                event.setAmount(event.getAmount() * 1.45f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CRIT, 16, 0.4, 0.08);
            }

            // #100 Archange du Jugement Solaire : rayons célestes aveuglants sur frappe mêlée !
            if (atkMask.has(IDX_ARCHANGE_JUGEMENT_SOLAIRE) && !isProjectile && victim.level() instanceof ServerLevel slArch) {
                EnchantmentParticleFX.slashArc(attacker, EnchantmentParticleFX.GOLD_DIVINE, ParticleTypes.END_ROD, 3.2, 16);
                for (Mob m : slArch.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.0))) {
                    if (!isFriendlySummon(m, attacker)) {
                        m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0));
                        m.setRemainingFireTicks(Math.max(m.getRemainingFireTicks(), 80));
                        if (m != victim) safeHurt(m, attacker.damageSources().onFire(), 5.0f);
                    }
                }
            }

            // #101 Poséidon du Cataclysme : Trident -> +50 % dégâts + foudre & maelström !
            if (atkMask.has(IDX_POSEIDON_CATACLYSME)
                    && (weapon.is(Items.TRIDENT) || event.getSource().getDirectEntity() instanceof ThrownTrident)
                    && victim.level() instanceof ServerLevel slPos) {
                event.setAmount(event.getAmount() * 1.50f);
                spawnVisualLightning(slPos, victim.position());
                EnchantmentParticleFX.shockwaveRing(slPos, victim.getX(), victim.getY() + 0.2, victim.getZ(),
                        ParticleTypes.NAUTILUS, 4.2, 24, 0.06);
                for (Mob m : slPos.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.8))) {
                    if (!isFriendlySummon(m, attacker)) {
                        Vec3 pull = victim.position().subtract(m.position());
                        if (pull.lengthSqr() > 0.05) m.setDeltaMovement(pull.normalize().scale(0.55));
                        if (m != victim) safeHurt(m, attacker.damageSources().drown(), 6.0f);
                    }
                }
            }

            // #104 Fantôme de l'Éclipse Totale : +40 % dégâts à distance dans l'ombre/nuit !
            if (atkMask.has(IDX_FANTOME_ECLIPSE_TOTALE) && isProjectile) {
                int light = attacker.level().getMaxLocalRawBrightness(attacker.blockPosition());
                if (!attacker.level().isDay() || light <= 8) {
                    event.setAmount(event.getAmount() * 1.40f);
                }
            }

            // #105 Seigneur de Guerre de l'Apocalypse : inflige Wither II + Brûlure d'Âme à chaque coup !
            if (atkMask.has(IDX_SEIGNEUR_GUERRE_APOCALYPSE)) {
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 140));
                event.setAmount(event.getAmount() * 1.30f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.SOUL_FIRE_FLAME, 12, 0.35, 0.04);
            }

            // #106 Maître du Temps Immuable : fige les ennemis critiques dans le temps (Lenteur V) !
            if (atkMask.has(IDX_MAITRE_TEMPS_IMMUABLE) && (attacker.fallDistance > 0.1f || attacker.getRandom().nextInt(100) < 35)) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 4));
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.OVERCLOCK_CYAN, 14, 0.3, 0.03);
            }

            // #107 Poing Divin du Chauve Suprême (SAITAMA — 4 pièces + MAINS VIDES !)
            if (atkMask.has(IDX_POING_DIVIN_SAITAMA) && !isProjectile && weapon.isEmpty() && now >= atkSt.saitamaCooldownUntil) {
                atkSt.saitamaCooldownUntil = now + 15L;
                event.setAmount(event.getAmount() + 22.0f);
                if (victim.level() instanceof ServerLevel slSai) {
                    slSai.sendParticles(ParticleTypes.SONIC_BOOM, victim.getX(), victim.getY() + 1.0, victim.getZ(), 1, 0, 0, 0, 0);
                    slSai.playSound(null, victim.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.65f, 1.25f);
                    Vec3 dir = victim.position().subtract(attacker.position());
                    if (dir.lengthSqr() < 0.01) dir = attacker.getLookAngle();
                    Vec3 punch = dir.normalize().scale(2.1).add(0, 0.55, 0);
                    victim.setDeltaMovement(punch);
                    victim.hurtMarked = true;
                    for (Mob m : slSai.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.5))) {
                        if (m != victim && !isFriendlySummon(m, attacker)) {
                            safeHurt(m, attacker.damageSources().magic(), 14.0f);
                            m.setDeltaMovement(punch.scale(0.65));
                            m.hurtMarked = true;
                        }
                    }
                }
            }

            // #108 Inquisiteur à l'Arbalète de Soufre : carreaux +45 % dégâts + foudre infernale !
            if (atkMask.has(IDX_INQUISITEUR_ARBALETE_SOUFRE) && isProjectile && victim.level() instanceof ServerLevel slInq) {
                event.setAmount(event.getAmount() * 1.45f);
                spawnVisualLightning(slInq, victim.position());
                victim.setRemainingFireTicks(140);
                safeHurt(victim, attacker.damageSources().lightningBolt(), 6.0f);
            }

            // #109 Avatar Suprême du Deep Lucky : +30 % dégâts globaux !
            if (atkMask.has(IDX_AVATAR_SUPREME_DEEP_LUCKY)) {
                event.setAmount(event.getAmount() * 1.30f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.END_ROD, 10, 0.35, 0.05);
            }
        } catch (Throwable t) {
            guard("onLivingHurt", t);
        }
    }

    private static void spawnVisualLightning(ServerLevel sl, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
    }

    // =========================================================================
    // 3. BOUCLIER (LivingShieldBlockEvent)
    // =========================================================================
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        try {
            LivingEntity defender = event.getEntity();
            ComboMask mask = EnchantmentComboRegistry.getActiveMask(defender);
            if (mask.isEmpty()) return;
            long now = defender.level().getGameTime();
            Entity attacker = event.getDamageSource().getEntity();

            // #001 Garde Fantassin : annule le recul des flèches
            if (mask.has(IDX_GARDE_FANTASSIN) && event.getDamageSource().is(DamageTypeTags.IS_PROJECTILE)) {
                defender.setDeltaMovement(0, Math.min(0, defender.getDeltaMovement().y), 0);
                defender.hurtMarked = true;
            }

            // #019 Sentinelle du Rempart : inamovible au sol + étourdit brièvement l'assaillant
            if (mask.has(IDX_SENTINELLE_REMPART)) {
                defender.setDeltaMovement(0, Math.min(0, defender.getDeltaMovement().y), 0);
                defender.hurtMarked = true;
                if (attacker instanceof LivingEntity livAtk) {
                    livAtk.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
                }
            }

            // #045 Citadelle Vivante : onde répulsive 4 blocs + 2 cœurs d'Absorption + répare bouclier
            if (mask.has(IDX_CITADELLE_VIVANTE) && defender.level() instanceof ServerLevel slCit) {
                defender.setAbsorptionAmount(Math.min(12.0f, Math.max(defender.getAbsorptionAmount(), 4.0f)));
                ItemStack shield = defender.getUseItem();
                if (shield.isDamageableItem() && shield.getDamageValue() > 0) {
                    shield.setDamageValue(Math.max(0, shield.getDamageValue() - 3));
                }
                EnchantmentParticleFX.shockwaveRing(slCit, defender.getX(), defender.getY() + 0.15, defender.getZ(),
                        EnchantmentParticleFX.STEEL_SILVER, 3.6, 18, 0.05);
                for (Mob m : slCit.getEntitiesOfClass(Mob.class, defender.getBoundingBox().inflate(4.0))) {
                    if (!isFriendlySummon(m, defender)) {
                        Vec3 push = m.position().subtract(defender.position());
                        if (push.lengthSqr() > 0.01) {
                            m.setDeltaMovement(push.normalize().scale(0.70).add(0, 0.28, 0));
                            m.hurtMarked = true;
                        }
                    }
                }
            }

            // #066 Bastion Miroir : charge l'arme 4 s pour la contre-attaque Brise-Garde
            if (mask.has(IDX_BASTION_MIROIR)) {
                stateOf(defender).parryRiposteUntil = now + 85L;
                if (defender.level() instanceof ServerLevel sl) {
                    sl.playSound(null, defender.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.55f, 1.85f);
                    EnchantmentParticleFX.burstOnEntity(defender, EnchantmentParticleFX.OVERCLOCK_CYAN, 10, 0.25, 0.03);
                }
            }

            // #098 Exterminateur de Titans Absolu : onde météore sur blocage !
            if (mask.has(IDX_EXTERMINATEUR_TITANS_ABSOLU) && defender.level() instanceof ServerLevel slTitB) {
                EnchantmentParticleFX.shockwaveRing(slTitB, defender.getX(), defender.getY() + 0.15, defender.getZ(),
                        ParticleTypes.LAVA, 4.5, 22, 0.06);
                for (Mob m : slTitB.getEntitiesOfClass(Mob.class, defender.getBoundingBox().inflate(4.8))) {
                    if (!isFriendlySummon(m, defender)) {
                        safeHurt(m, defender.damageSources().mobAttack(defender), 8.0f);
                        m.setDeltaMovement(m.getDeltaMovement().x, 0.5, m.getDeltaMovement().z);
                        m.hurtMarked = true;
                    }
                }
            }
        } catch (Throwable t) {
            guard("onShieldBlock", t);
        }
    }

    // =========================================================================
    // 4. ÉLIMINATION (LivingDeathEvent)
    // =========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
            ComboMask mask = EnchantmentComboRegistry.getActiveMask(killer);
            if (mask.isEmpty()) return;
            LivingEntity dead = event.getEntity();

            // #017 Recycleur Prudent : répare de 2 points l'arme et la pièce d'armure la plus abîmée
            if (mask.has(IDX_RECYCLEUR_PRUDENT)) {
                ItemStack main = killer.getMainHandItem();
                if (main.isDamageableItem() && main.getDamageValue() > 0) {
                    main.setDamageValue(Math.max(0, main.getDamageValue() - 2));
                }
                repairMostDamagedArmor(killer, 2);
            }

            // #049 Bourreau Décapiteur : chance accrue de trophée/crâne bonus
            if (mask.has(IDX_BOURREAU_DECAPITEUR) && dead.level() instanceof ServerLevel slHead && killer.getRandom().nextInt(100) < 22) {
                ItemStack skull = ItemStack.EMPTY;
                if (dead.getType() == EntityType.ZOMBIE) skull = new ItemStack(Items.ZOMBIE_HEAD);
                else if (dead.getType() == EntityType.SKELETON) skull = new ItemStack(Items.SKELETON_SKULL);
                else if (dead.getType() == EntityType.CREEPER) skull = new ItemStack(Items.CREEPER_HEAD);
                else if (dead.getType() == EntityType.WITHER_SKELETON) skull = new ItemStack(Items.WITHER_SKELETON_SKULL);
                if (!skull.isEmpty()) {
                    slHead.addFreshEntity(new ItemEntity(slHead, dead.getX(), dead.getY() + 0.5, dead.getZ(), skull));
                }
            }

            // #050 Corsaire Pillard : pépites d'or / émeraudes + Chance II + Grâce du Dauphin
            if (mask.has(IDX_CORSAIRE_PILLARD) && dead.level() instanceof ServerLevel slPir) {
                ItemStack loot = killer.getRandom().nextInt(4) == 0
                        ? new ItemStack(Items.EMERALD, 1)
                        : new ItemStack(Items.GOLD_NUGGET, 2 + killer.getRandom().nextInt(4));
                slPir.addFreshEntity(new ItemEntity(slPir, dead.getX(), dead.getY() + 0.4, dead.getZ(), loot));
                killer.addEffect(new MobEffectInstance(MobEffects.LUCK, 160, 1, true, false));
                killer.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 120, 0, true, false));
            }

            // #073 Faucheuse d'Âmes : restaure +2 cœurs + +6 XP dans un tourbillon de sculk
            if (mask.has(IDX_FAUCHEUSE_AMES) && dead.level() instanceof ServerLevel slSoul) {
                killer.heal(4.0f);
                if (killer instanceof Player pl) pl.giveExperiencePoints(6);
                EnchantmentParticleFX.shockwaveRing(slSoul, dead.getX(), dead.getY() + 0.3, dead.getZ(),
                        ParticleTypes.SCULK_SOUL, 2.2, 14, 0.03);
            }

            // #082 Assassin Nocturne : réactive l'Invisibilité sur kill
            if (mask.has(IDX_ASSASSIN_NOCTURNE)) {
                killer.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, true, false));
                EnchantmentParticleFX.burstOnEntity(killer, EnchantmentParticleFX.SHADOW_VIOLET, 10, 0.3, 0.02);
            }

            // #086 Berserker Immortel : restaure 25 % des PV manquants
            if (mask.has(IDX_BERSERKER_IMMORTEL)) {
                float missing = Math.max(0.0f, killer.getMaxHealth() - killer.getHealth());
                if (missing > 0.0f) killer.heal(missing * 0.25f);
                killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
                EnchantmentParticleFX.burstOnEntity(killer, EnchantmentParticleFX.SCARLET_MIST, 12, 0.3, 0.03);
            }

            // #106 Maître du Temps Immuable : +25 % XP bonus sur élimination
            if (mask.has(IDX_MAITRE_TEMPS_IMMUABLE) && killer instanceof Player pl) {
                pl.giveExperiencePoints(5);
            }
        } catch (Throwable t) {
            guard("onLivingDeath", t);
        }
    }

    // =========================================================================
    // 5. PROJECTILES, ARCS, ARBALÈTES, TRIDENTS & LÉGION
    // =========================================================================
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        try {
            // #083 Empereur de Légion : bénit les soldats invoqués
            if (event.getEntity() instanceof Mob mob && event.getLevel() instanceof ServerLevel sl) {
                Player nearestCommander = sl.getNearestPlayer(mob, 6.0);
                if (nearestCommander != null
                        && EnchantmentComboRegistry.hasCombo(nearestCommander, IDX_EMPEREUR_LEGION)
                        && (mob.getType() == EntityType.VINDICATOR || mob.getType() == EntityType.PILLAGER || mob.getType() == EntityType.WITHER_SKELETON)
                        && mob.tickCount == 0) {
                    mob.getPersistentData().putUUID("slb_combo9_emperor", nearestCommander.getUUID());
                    mob.getPersistentData().putUUID("slb_summon_owner", nearestCommander.getUUID());
                    mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1600, 1, true, false));
                    mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1600, 0, true, false));
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1600, 0, true, false));
                    EnchantmentParticleFX.crownHalo(mob, EnchantmentParticleFX.GOLD_DIVINE, 8);
                }
            }

            if (event.getEntity() instanceof Projectile proj && proj.getOwner() instanceof LivingEntity shooter) {
                ComboMask mask = EnchantmentComboRegistry.getActiveMask(shooter);
                if (mask.isEmpty()) return;
                PlayerComboState st = stateOf(shooter);
                long now = event.getLevel().getGameTime();

                // #093 Avatar du Zéphyr : bourrasque perforante à chaque lancer de Trident !
                if (proj instanceof ThrownTrident tri) {
                    if (mask.has(IDX_AVATAR_ZEPHYR) && event.getLevel() instanceof ServerLevel slZ) {
                        tri.setDeltaMovement(tri.getDeltaMovement().scale(1.30));
                        EnchantmentParticleFX.shockwaveRing(slZ, shooter.getX(), shooter.getY() + 1.0, shooter.getZ(),
                                ParticleTypes.GUST_EMITTER_SMALL, 1.5, 4, 0.03);
                    }
                    if (mask.has(IDX_TRIDENT_RETOUR_EXPLOSIF)) {
                        tri.getPersistentData().putBoolean("slb_combo75_explosive", true);
                    }
                }

                if (proj instanceof AbstractArrow arrow) {
                    if (mask.has(IDX_ARCHER_RONCE)) arrow.getPersistentData().putBoolean("slb_c08_thorn", true);
                    if (mask.has(IDX_ARBALETRIER_ECLAIREUR)) {
                        arrow.getPersistentData().putBoolean("slb_c09_flare", true);
                        shooter.removeEffect(MobEffects.DARKNESS);
                        shooter.removeEffect(MobEffects.BLINDNESS);
                    }
                    if (mask.has(IDX_ARCHER_REBOND) && now < st.bounceReadyUntil) {
                        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.35));
                        arrow.getPersistentData().putBoolean("slb_c26_shock", true);
                    }
                    // #035 Archer Recul : tirer vers le bas propulse haut dans les airs tel un Rocket-Jump plume !
                    if (mask.has(IDX_ARCHER_RECUL) && shooter.getXRot() > 55.0f) {
                        Vec3 dm = shooter.getDeltaMovement();
                        shooter.setDeltaMovement(dm.x, Math.max(dm.y, 0.95), dm.z);
                        shooter.hurtMarked = true;
                        shooter.fallDistance = 0.0f;
                        shooter.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
                    }
                    // #038 Arbalétrier à Dispersion : tir à bout portant repousse tous les ennemis devant soi !
                    if (mask.has(IDX_ARBALETRIER_DISPERSION) && event.getLevel() instanceof ServerLevel slScat) {
                        Vec3 lk = shooter.getLookAngle().normalize();
                        for (Mob m : slScat.getEntitiesOfClass(Mob.class, shooter.getBoundingBox().inflate(5.0))) {
                            Vec3 toM = m.position().subtract(shooter.position());
                            if (toM.length() < 5.0 && toM.normalize().dot(lk) > 0.35 && !isFriendlySummon(m, shooter)) {
                                m.setDeltaMovement(toM.normalize().scale(1.25).add(0, 0.32, 0));
                                m.hurtMarked = true;
                            }
                        }
                    }
                    if (mask.has(IDX_ARCHER_GRAVITE)) arrow.getPersistentData().putBoolean("slb_c52_gravity", true);
                    if (mask.has(IDX_ARCHER_RETARDEMENT)) arrow.getPersistentData().putBoolean("slb_c53_delay", true);
                    if (mask.has(IDX_OEIL_FAUCON_ASTRAL)) {
                        arrow.setNoGravity(true);
                        if (event.getLevel() instanceof ServerLevel sla) {
                            TestProcedure.schedule(sla, TestProcedure.currentTick(sla) + 16, () -> {
                                if (arrow.isAlive()) arrow.setNoGravity(false);
                            });
                        }
                    }
                    if (mask.has(IDX_COMMANDO_GRAPPIN)) arrow.getPersistentData().putBoolean("slb_combo23_grapple", true);
                    if (mask.has(IDX_ARTILLEUR_INQUISITEUR)) arrow.getPersistentData().putBoolean("slb_combo24_brimstone", true);
                    if (mask.has(IDX_SPECTRE_DIMENSIONNEL)) arrow.getPersistentData().putBoolean("slb_combo25_void", true);
                    if (mask.has(IDX_ARCHER_ORAGE_CHAINE)) arrow.getPersistentData().putBoolean("slb_c74_chain", true);
                    if (mask.has(IDX_PLUIE_CATACLYSMIQUE) && !shooter.onGround()) arrow.getPersistentData().putBoolean("slb_combo22_apocalypse", true);
                    if (mask.has(IDX_DRAGONNIER_END)) arrow.getPersistentData().putBoolean("slb_c95_dragon", true);
                    if (mask.has(IDX_VALKYRIE_SUPERSONIQUE) && shooter.isFallFlying()) arrow.getPersistentData().putBoolean("slb_c102_valkyrie", true);
                }
            }
        } catch (Throwable t) {
            guard("onEntityJoin", t);
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        try {
            Player player = event.getEntity();
            if (EnchantmentComboRegistry.hasCombo(player, IDX_OEIL_FAUCON_ASTRAL) && event.getCharge() >= 18
                    && player.level() instanceof ServerLevel sl) {
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.END_ROD, 6, 0.25, 0.02);
            }
        } catch (Throwable t) {
            guard("onArrowLoose", t);
        }
    }

    // #102 Valkyrie Supersonique : 50 % de chance de ne pas consommer la fusée en vol d'Élytres !
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        try {
            Player player = event.getEntity();
            if (player.level().isClientSide() || player.getAbilities().instabuild) return;
            if (player.isFallFlying() && event.getItemStack().is(Items.FIREWORK_ROCKET)
                    && EnchantmentComboRegistry.hasCombo(player, IDX_VALKYRIE_SUPERSONIQUE)
                    && player.getRandom().nextBoolean()) {
                player.getInventory().add(new ItemStack(Items.FIREWORK_ROCKET, 1));
            }
        } catch (Throwable t) {
            guard("onRightClickItem", t);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()) return;
        try {
            Projectile proj = event.getProjectile();
            if (!(proj.getOwner() instanceof LivingEntity shooter)) return;
            if (!(proj.level() instanceof ServerLevel sl)) return;
            Vec3 hit = event.getRayTraceResult().getLocation();

            // #075 Trident à Retour Explosif : onde explosive sécurisée sans blesser le lanceur
            if (proj instanceof ThrownTrident tri && tri.getPersistentData().getBoolean("slb_combo75_explosive")) {
                tri.getPersistentData().remove("slb_combo75_explosive");
                EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z, ParticleTypes.EXPLOSION, 2.5, 6, 0.02);
                sl.playSound(null, BlockPos.containing(hit), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.65f, 1.3f);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(3.5))) {
                    if (!isFriendlySummon(m, shooter)) safeHurt(m, shooter.damageSources().trident(tri, shooter), 6.5f);
                }
                return;
            }

            if (!(proj instanceof AbstractArrow arrow)) return;
            var tag = arrow.getPersistentData();

            // #008 Archer des Ronces : flaque toxique ralentissante à l'impact
            if (tag.getBoolean("slb_c08_thorn")) {
                tag.remove("slb_c08_thorn");
                EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.1, hit.z, dustOf(0x4ADE80, 1.0f), 2.5, 14, 0.02);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(2.8))) {
                    if (!isFriendlySummon(m, shooter)) {
                        m.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                    }
                }
            }

            // #009 Arbalétrier Éclaireur : Surbrillance 6 s
            if (tag.getBoolean("slb_c09_flare") && event.getRayTraceResult() instanceof EntityHitResult ehr09
                    && ehr09.getEntity() instanceof LivingEntity liv09) {
                tag.remove("slb_c09_flare");
                liv09.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
            }

            // #026 Archer Rebond : impact électrisé
            if (tag.getBoolean("slb_c26_shock")) {
                tag.remove("slb_c26_shock");
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(2.5))) {
                    if (!isFriendlySummon(m, shooter)) {
                        safeHurt(m, shooter.damageSources().lightningBolt(), 4.5f);
                        EnchantmentParticleFX.burstOnEntity(m, ParticleTypes.ELECTRIC_SPARK, 10, 0.3, 0.04);
                    }
                }
            }

            // #052 Archer Gravité : aspire les ennemis en un point puis explose en onde d'âmes !
            if (tag.getBoolean("slb_c52_gravity")) {
                tag.remove("slb_c52_gravity");
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(4.5))) {
                    if (!isFriendlySummon(m, shooter)) {
                        Vec3 pull = hit.subtract(m.position()).scale(0.45);
                        m.setDeltaMovement(pull.x, 0.25, pull.z);
                        m.hurtMarked = true;
                    }
                }
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 20L, () -> {
                    EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z, ParticleTypes.SCULK_SOUL, 3.4, 20, 0.05);
                    for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(3.8))) {
                        if (!isFriendlySummon(m, shooter)) safeHurt(m, shooter.damageSources().magic(), 6.0f);
                    }
                });
            }

            // #053 Archer à Retardement : marque + double détonation critique à l'expiration
            if (tag.getBoolean("slb_c53_delay") && event.getRayTraceResult() instanceof EntityHitResult ehr53
                    && ehr53.getEntity() instanceof LivingEntity liv53) {
                tag.remove("slb_c53_delay");
                liv53.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0));
                for (int b = 1; b <= 2; b++) {
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 20L * b, () -> {
                        if (liv53.isAlive()) {
                            EnchantmentParticleFX.burstOnEntity(liv53, ParticleTypes.CRIT, 14, 0.35, 0.07);
                            safeHurt(liv53, shooter.damageSources().magic(), 5.0f);
                        }
                    });
                }
            }

            // #068 Commando Grappin : anti-chute + Vitesse II
            if (tag.getBoolean("slb_combo23_grapple")) {
                tag.remove("slb_combo23_grapple");
                stateOf(shooter).grappleShieldUntil = sl.getGameTime() + 100L;
                shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
            }

            // #069 Artilleur Inquisiteur : braise soufrée explosive
            if (tag.getBoolean("slb_combo24_brimstone")
                    && event.getRayTraceResult() instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity hitMob) {
                tag.remove("slb_combo24_brimstone");
                hitMob.setRemainingFireTicks(80);
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 30L, () -> {
                    if (hitMob.isAlive()) {
                        EnchantmentParticleFX.burstOnEntity(hitMob, ParticleTypes.LAVA, 10, 0.3, 0.05);
                        safeHurt(hitMob, shooter.damageSources().onFire(), 5.5f);
                    }
                });
            }

            // #070 Spectre Dimensionnel : leurre aveuglant + Invisibilité & Force I
            if (tag.getBoolean("slb_combo25_void")) {
                tag.remove("slb_combo25_void");
                Vec3 origin = shooter.position();
                EnchantmentParticleFX.shockwaveRing(sl, origin.x, origin.y + 0.3, origin.z,
                        EnchantmentParticleFX.SHADOW_VIOLET, 3.0, 18, 0.04);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(origin, origin).inflate(3.5))) {
                    if (!isFriendlySummon(m, shooter)) {
                        m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0));
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2));
                    }
                }
                shooter.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 65, 0, true, false));
                shooter.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 65, 0, true, false));
            }

            // #074 Archer d'Orage en Chaîne : rebond électrique sur 3 cibles + Vitesse III courte
            if (tag.getBoolean("slb_c74_chain")) {
                tag.remove("slb_c74_chain");
                int chained = 0;
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(5.5))) {
                    if (!isFriendlySummon(m, shooter) && chained < 3) {
                        chained++;
                        safeHurt(m, shooter.damageSources().lightningBolt(), 5.0f);
                        EnchantmentParticleFX.tetherBeam(sl, hit, m.position().add(0, 0.8, 0),
                                ParticleTypes.ELECTRIC_SPARK, EnchantmentParticleFX.OVERCLOCK_CYAN, 0.5);
                    }
                }
                shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 2, true, false));
            }

            // #092 Pluie Cataclysmique : 2 météorites secondaires autour de l'impact
            if (tag.getBoolean("slb_combo22_apocalypse")) {
                tag.remove("slb_combo22_apocalypse");
                for (int i = 0; i < 2; i++) {
                    double ang = sl.random.nextDouble() * Math.PI * 2.0;
                    double rx = hit.x + Math.cos(ang) * 2.4;
                    double rz = hit.z + Math.sin(ang) * 2.4;
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 10 + i * 8L, () -> {
                        EnchantmentParticleFX.shockwaveRing(sl, rx, hit.y + 0.15, rz, ParticleTypes.LAVA, 2.2, 16, 0.06);
                        sl.playSound(null, BlockPos.containing(rx, hit.y, rz),
                                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.2f);
                        for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(rx - 2.6, hit.y - 2.0, rz - 2.6, rx + 2.6, hit.y + 2.5, rz + 2.6))) {
                            if (!isFriendlySummon(m, shooter)) {
                                m.setRemainingFireTicks(100);
                                safeHurt(m, shooter.damageSources().onFire(), 6.5f);
                            }
                        }
                    });
                }
            }

            // #095 Dragonnier de l'End : zone de souffle draconique qui soigne le tireur et ronge les ennemis !
            if (tag.getBoolean("slb_c95_dragon")) {
                tag.remove("slb_c95_dragon");
                for (int pulse = 0; pulse < 3; pulse++) {
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + pulse * 20L, () -> {
                        EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.15, hit.z, ParticleTypes.DRAGON_BREATH, 3.0, 18, 0.02);
                        if (shooter.isAlive() && shooter.position().distanceToSqr(hit) <= 10.0) shooter.heal(2.0f);
                        for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(3.2))) {
                            if (!isFriendlySummon(m, shooter)) safeHurt(m, shooter.damageSources().dragonBreath(), 4.5f);
                        }
                    });
                }
            }

            // #102 Valkyrie Supersonique : flèches tirées en vol d'Élytres abattent la foudre !
            if (tag.getBoolean("slb_c102_valkyrie")) {
                tag.remove("slb_c102_valkyrie");
                spawnVisualLightning(sl, hit);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(4.0))) {
                    if (!isFriendlySummon(m, shooter)) safeHurt(m, shooter.damageSources().lightningBolt(), 9.0f);
                }
            }
        } catch (Throwable t) {
            guard("onProjectileImpact", t);
        }
    }

    // =========================================================================
    // 6. MINAGE, FORÊT & AGRICULTURE (BlockEvent.BreakEvent)
    // =========================================================================
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        try {
            ComboMask mask = EnchantmentComboRegistry.getActiveMask(player);
            if (mask.isEmpty()) return;
            BlockPos pos = event.getPos();
            BlockState state = event.getState();
            PlayerComboState st = stateOf(player);

            // #005 Mineur Étincelle : miner de la roche dans l'obscurité -> Vision Nocturne + étincelles
            if (mask.has(IDX_MINEUR_ETINCELLE) && state.is(BlockTags.BASE_STONE_OVERWORLD)
                    && sl.getMaxLocalRawBrightness(pos) <= 7) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
                sl.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.02);
            }

            // #006 Jardinier Matinal : désherber / récolter -> Vitesse I (4 s) + chance poudre d'os
            if (mask.has(IDX_JARDINIER_MATINAL) && (isWeedPlant(state) || state.getBlock() instanceof CropBlock)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0, true, false));
                if (sl.random.nextInt(100) < 20) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, new ItemStack(Items.BONE_MEAL)));
                }
            }

            // #010 Terrassier des Neiges : creuser neige/terre/sable -> Célérité I + Vitesse I
            if (mask.has(IDX_TERRASSIER_NEIGE) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 70, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 70, 0, true, false));
            }

            // #015 Bûcheron Robuste : casser de la pierre -> Résistance I + Absorption légère régulière
            if (mask.has(IDX_BUCHERON_ROBUSTE) && state.is(BlockTags.BASE_STONE_OVERWORLD)) {
                st.stoneMinedCounter++;
                if (st.stoneMinedCounter >= 6) {
                    st.stoneMinedCounter = 0;
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, true, false));
                    player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 2.0f));
                }
            }

            // #018 Fermier de Compost : récolter des plantes -> poudre d'os chaude + dissipe le gel
            if (mask.has(IDX_FERMIER_COMPOST) && (state.getBlock() instanceof CropBlock || isWeedPlant(state))) {
                player.setTicksFrozen(0);
                if (sl.random.nextInt(100) < 22) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, new ItemStack(Items.BONE_MEAL)));
                }
            }

            // #023 Fondeur Volcanique : miner un minerai -> Résistance au Feu (10 s) + 25 % lingot bonus
            if (mask.has(IDX_FONDEUR_VOLCANIQUE) && state.is(Tags.Blocks.ORES)) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, true, false));
                if (sl.random.nextInt(100) < 25) {
                    ItemStack ingot = state.is(BlockTags.GOLD_ORES) ? new ItemStack(Items.GOLD_INGOT)
                            : state.is(BlockTags.COPPER_ORES) ? new ItemStack(Items.COPPER_INGOT)
                            : new ItemStack(Items.IRON_INGOT);
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ingot));
                }
            }

            // #031 Prospecteur de Cristal : minerai ou améthyste -> +4 XP bonus + Chance I
            if (mask.has(IDX_PROSPECTEUR_CRISTAL) && (state.is(Tags.Blocks.ORES) || state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.AMETHYST_BLOCK))) {
                player.giveExperiencePoints(4);
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 200, 0, true, false));
            }

            // #042 Cœur de Forge Naine : révèle les filons cachés à 6 blocs + XP + nourriture
            if (mask.has(IDX_COEUR_FORGE_NAINE) && state.is(Tags.Blocks.ORES)) {
                player.giveExperiencePoints(3);
                st.oreMinedCounter++;
                if (st.oreMinedCounter >= 8) {
                    st.oreMinedCounter = 0;
                    player.getFoodData().eat(1, 0.4f);
                }
                for (BlockPos scan : BlockPos.betweenClosed(pos.offset(-6, -4, -6), pos.offset(6, 4, 6))) {
                    if (!scan.equals(pos) && sl.getBlockState(scan).is(Tags.Blocks.ORES)) {
                        sl.sendParticles(ParticleTypes.END_ROD, scan.getX() + 0.5, scan.getY() + 0.5, scan.getZ() + 0.5,
                                2, 0.15, 0.15, 0.15, 0.0);
                    }
                }
            }

            // #043 Bûcheron Sylvestre : Célérité II + replante pousse + attire le bois coupé
            if (mask.has(IDX_BUCHERON_SYLVESTRE) && state.is(BlockTags.LOGS)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, true, false));
                BlockState soil = sl.getBlockState(pos.below());
                if ((soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.PODZOL) || soil.is(Blocks.MOSS_BLOCK))
                        && sl.random.nextInt(100) < 55) {
                    BlockState sapling = saplingForLog(state);
                    BlockPos targetPos = pos.immutable();
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 2L, () -> {
                        if (sl.getBlockState(targetPos).isAir()) {
                            sl.setBlockAndUpdate(targetPos, sapling);
                            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                    targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                                    6, 0.25, 0.25, 0.25, 0.02);
                        }
                        for (ItemEntity itemEnt : sl.getEntitiesOfClass(ItemEntity.class, new AABB(targetPos).inflate(7.0))) {
                            if (itemEnt.isAlive() && player.isAlive()) {
                                itemEnt.teleportTo(player.getX(), player.getY() + 0.5, player.getZ());
                                itemEnt.setNoPickUpDelay();
                            }
                        }
                    });
                }
            }

            // #044 Moissonneur d'Abondance : nourrit + accélère cultures 3x3 + Carotte/Pomme Dorée rare
            if (mask.has(IDX_MOISSONNEUR_ABONDANCE)
                    && state.getBlock() instanceof CropBlock harvestedCrop && harvestedCrop.isMaxAge(state)) {
                player.getFoodData().eat(1, 0.5f);
                for (BlockPos adj : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
                    if (adj.equals(pos)) continue;
                    BlockState adjState = sl.getBlockState(adj);
                    if (adjState.getBlock() instanceof CropBlock adjCrop && !adjCrop.isMaxAge(adjState)) {
                        sl.setBlockAndUpdate(adj, adjCrop.getStateForAge(Math.min(adjCrop.getMaxAge(), adjCrop.getAge(adjState) + 1)));
                        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, adj.getX() + 0.5, adj.getY() + 0.4, adj.getZ() + 0.5,
                                3, 0.2, 0.1, 0.2, 0.01);
                    }
                }
                if (sl.random.nextInt(100) < 4) {
                    ItemStack bonus = new ItemStack(sl.random.nextInt(5) == 0 ? Items.GOLDEN_APPLE : Items.GOLDEN_CARROT);
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, bonus));
                }
            }

            // #076 Excavateur Sismique : Vitesse II + Résistance I + gravier -> silex/pépite de fer
            if (mask.has(IDX_EXCAVATEUR_SISMIQUE) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, true, false));
                if (state.is(Blocks.GRAVEL) && sl.random.nextInt(100) < 30) {
                    ItemStack extra = new ItemStack(sl.random.nextBoolean() ? Items.FLINT : Items.IRON_NUGGET, 2);
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, extra));
                }
            }

            // #103 Forge du Créateur Éternel : préserve 80 % durabilité outil + chance Diamant/Émeraude !
            if (mask.has(IDX_FORGE_CREATEUR_ETERNEL)) {
                ItemStack tool = player.getMainHandItem();
                if (tool.isDamageableItem() && tool.getDamageValue() > 0 && sl.random.nextInt(100) < 80) {
                    tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
                }
                if ((state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES)) && sl.random.nextInt(250) < 3) {
                    ItemStack gem = new ItemStack(sl.random.nextBoolean() ? Items.DIAMOND : Items.EMERALD);
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, gem));
                    EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.TOTEM_OF_UNDYING, 12, 0.35, 0.08);
                }
            }

            // #106 Maître du Temps Immuable : +25 % XP minage
            if (mask.has(IDX_MAITRE_TEMPS_IMMUABLE) && state.is(Tags.Blocks.ORES)) {
                player.giveExperiencePoints(3);
            }
        } catch (Throwable t) {
            guard("onBlockBreak", t);
        }
    }

    private static BlockState saplingForLog(BlockState log) {
        if (log.is(Blocks.SPRUCE_LOG)) return Blocks.SPRUCE_SAPLING.defaultBlockState();
        if (log.is(Blocks.BIRCH_LOG)) return Blocks.BIRCH_SAPLING.defaultBlockState();
        if (log.is(Blocks.JUNGLE_LOG)) return Blocks.JUNGLE_SAPLING.defaultBlockState();
        if (log.is(Blocks.ACACIA_LOG)) return Blocks.ACACIA_SAPLING.defaultBlockState();
        if (log.is(Blocks.DARK_OAK_LOG)) return Blocks.DARK_OAK_SAPLING.defaultBlockState();
        if (log.is(Blocks.CHERRY_LOG)) return Blocks.CHERRY_SAPLING.defaultBlockState();
        return Blocks.OAK_SAPLING.defaultBlockState();
    }

    // =========================================================================
    // 7. PÊCHE (ItemFishedEvent)
    // =========================================================================
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide()) return;
            ComboMask mask = EnchantmentComboRegistry.getActiveMask(player);
            if (mask.isEmpty()) return;

            // #007 Pêcheur Cuisinier : chaque prise restaure 2 points de faim + 1 cœur
            if (mask.has(IDX_PECHEUR_CUISINIER)) {
                player.getFoodData().eat(2, 0.4f);
                player.heal(2.0f);
            }

            // #071 Amiral Hauturier : Absorption + Grâce du Dauphin + 50 % double butin
            if (mask.has(IDX_AMIRAL_HAUTURIER)) {
                player.setAbsorptionAmount(Math.min(12.0f, Math.max(player.getAbsorptionAmount(), 4.0f)));
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 160, 0, true, false));
                List<ItemStack> drops = event.getDrops();
                if (!drops.isEmpty() && player.getRandom().nextInt(100) < 50 && player.level() instanceof ServerLevel sl) {
                    sl.addFreshEntity(new ItemEntity(sl, player.getX(), player.getY() + 0.6, player.getZ(), drops.get(0).copy()));
                    EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.NAUTILUS, 12, 0.35, 0.05);
                }
            }

            // #078 Pêcheur Dimensionnel de l'End : chance de pêcher une Perle de l'Ender !
            if (mask.has(IDX_PECHEUR_DIMENSION_END) && player.getRandom().nextInt(100) < 35 && player.level() instanceof ServerLevel slEnd) {
                slEnd.addFreshEntity(new ItemEntity(slEnd, player.getX(), player.getY() + 0.6, player.getZ(), new ItemStack(Items.ENDER_PEARL)));
            }
        } catch (Throwable t) {
            guard("onItemFished", t);
        }
    }

    // =========================================================================
    // 8. COMMANDE EN JEU /dlbcombos (110 Combos : list, tier <palier>, give <1..110>)
    // =========================================================================
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dlbcombos")
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    ComboMask mask = EnchantmentComboRegistry.computeRawMask(sp);
                    int activeCount = mask.bitCount();
                    sp.sendSystemMessage(Component.literal("\u00a76\u00a7l=== Synergies Multi-Pi\u00e8ces Actives (" + activeCount + "/" + COMBOS.size() + ") ==="));
                    if (activeCount == 0) {
                        sp.sendSystemMessage(Component.literal("\u00a77Aucun combo actif. Utilisez \u00a7e/dlbcombos list\u00a77 ou \u00a7e/dlbcombos tier ultra_rare\u00a77 !"));
                    } else {
                        for (ComboDef c : COMBOS) {
                            if (mask.has(c.index())) {
                                sp.sendSystemMessage(Component.literal("\u00a76\u2726 #" + (c.index() + 1) + " ")
                                        .append(c.translatedName()).append(" ").append(c.tier().badge())
                                        .append(Component.literal(" \u00a77\u2014 ").append(c.translatedDesc().copy().withStyle(ChatFormatting.WHITE))));
                            }
                        }
                    }
                    return activeCount;
                })
                .then(Commands.literal("list").executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    ComboMask mask = EnchantmentComboRegistry.computeRawMask(sp);
                    sp.sendSystemMessage(Component.literal("\u00a76\u00a7l=== Codex des " + COMBOS.size() + " Combos Multi-Pi\u00e8ces ==="));
                    for (ComboDef c : COMBOS) {
                        boolean on = mask.has(c.index());
                        sp.sendSystemMessage(Component.literal((on ? "\u00a7a\u2726 " : "\u00a78\u2727 ") + "#" + (c.index() + 1) + " ")
                                .append(c.tier().badge()).append(" ")
                                .append(c.translatedName())
                                .append(Component.literal(" \u00a78[").append(EnchantmentComboRegistry.buildRequirementsSummary(c, sp)).append("\u00a78]")));
                    }
                    return COMBOS.size();
                }))
                .then(Commands.literal("tier")
                        .then(Commands.argument("palier", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            String raw = StringArgumentType.getString(ctx, "palier").toUpperCase(Locale.ROOT);
                            ComboTier tier;
                            try {
                                tier = ComboTier.valueOf(raw);
                            } catch (IllegalArgumentException ex) {
                                sp.sendSystemMessage(Component.literal("\u00a7cPaliers valides : normal, uncommon, rare, epic, mythic, ultra_rare"));
                                return 0;
                            }
                            ComboMask mask = EnchantmentComboRegistry.computeRawMask(sp);
                            int count = 0;
                            sp.sendSystemMessage(Component.literal("\u00a76\u00a7l=== Combos du palier ").append(tier.badge()).append(" \u00a76\u00a7l==="));
                            for (ComboDef c : COMBOS) {
                                if (c.tier() != tier) continue;
                                count++;
                                boolean on = mask.has(c.index());
                                sp.sendSystemMessage(Component.literal((on ? "\u00a7a\u2726 " : "\u00a78\u2727 ") + "#" + (c.index() + 1) + " ")
                                        .append(c.translatedName())
                                        .append(Component.literal(" \u00a78[").append(EnchantmentComboRegistry.buildRequirementsSummary(c, sp)).append("\u00a78]")));
                            }
                            return count;
                        })))
                .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, 110)).executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            DeepEnchant.rememberAccess(sp.serverLevel().registryAccess());
                            int num = IntegerArgumentType.getInteger(ctx, "numero");
                            ComboDef c = COMBOS.get(num - 1);
                            for (PieceRequirement req : c.pieces()) {
                                ItemStack item = new ItemStack(req.sampleItem());
                                Enchantment ench = DeepEnchant.byId("deep_lucky_block:" + req.primaryPath());
                                if (ench != null) {
                                    int maxMod = Math.max(1, CustomEnchantment.getMaxLevelMod(ench));
                                    int giveLvl = Math.max(req.minLevel(), Math.min(5, maxMod));
                                    DeepEnchant.setLevel(item, ench, giveLvl);
                                }
                                if (sp.getItemBySlot(req.slot()).isEmpty()) {
                                    sp.setItemSlot(req.slot(), item);
                                } else if (!sp.getInventory().add(item)) {
                                    sp.drop(item, false);
                                }
                            }
                            sp.sendSystemMessage(Component.literal("\u00a7a\u2726 Kit du Combo #" + num + " remis : ")
                                    .append(c.translatedName()).append(" ").append(c.tier().badge()));
                            return 1;
                        }))));
    }
}
