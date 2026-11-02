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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static deepluckyblock.enchantments.EnchantmentComboRegistry.*;

/**
 * T309-COMBOS / T332-COMBOS-210 : Gestionnaire d'exécution des 210 Combos & Synergies
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
        boolean wasOnGround = true;
        float maxFallDist = 0.0f;
        double lastFlightSpeed = 0.0;
        boolean wasSprinting = false;
        boolean wasShiftDown = false;
        int thunderHitCounter = 0;
        int rhythmHitCounter = 0;
        int supremeSlashCounter = 0;
        int thousandCutCounter = 0;
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
        long springJumpCd = 0L;
        long shieldHealCd = 0L;
        long thornsShieldChargeUntil = 0L;
        long shieldAxeRiposteUntil = 0L;
        long mirageRiposteUntil = 0L;
        long grappleMeteorUntil = 0L;
        long riftFallImmuneUntil = 0L;
        long aegisRetaliationUntil = 0L;
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
        // T333-NO-MOB-COMBOS : Sortie O(1) immédiate pour toutes les entités non-joueurs.
        if (!(event.getEntity() instanceof Player entity) || !entity.isAlive()) return;
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
        if (!(entity instanceof Player)) {
            if (entity.tickCount % 10 != 0) return;
            // T321-ANTI-LAG : Court-circuit O(1) pour tous les mobs non enchantés (vaches, poissons, zombies nus...)
            // -> 0 allocation UUID dans STATES, 0 calcul de masque !
            if (!UniversalEnchantmentHandler.hasAnyEnchantedEquipment(entity)) {
                STATES.remove(entity.getUUID());
                return;
            }
        } else if ((entity.tickCount & 511) == 0 && STATES.size() > 256) {
            STATES.clear();
        }
        ComboMask mask = EnchantmentComboRegistry.getActiveMask(entity);
        PlayerComboState st = stateOf(entity);
        long now = entity.level().getGameTime();

        // Notification temps réel à l'activation d'un nouveau combo
        if (entity instanceof ServerPlayer sp) {
            ComboMask newlyOn = mask.andNot(st.prevServerMask);
            if (!newlyOn.isEmpty() && sp.tickCount > 20) {
                for (ComboDef c : COMBOS) {
                    if (newlyOn.has(c.index())) {
                        MutableComponent banner = Component.translatable("tooltip.deep_lucky_block.combo_activated")
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

        // #061 Peste Ambulante : aura empoisonnée + Faiblesse I sur ennemis proches (halo miasmatique continu côté client : CosmeticAuraClientTicker)
        if (mask.has(IDX_PESTE_AMBULANTE) && sl != null && entity.tickCount % 25 == 0) {
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
                EnchantmentParticleFX.doubleHelix(entity, EnchantmentParticleFX.CRIMSON_BLOOD, EnchantmentParticleFX.SCARLET_MIST, 0.72, 6);
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

        // #096 Armure Éternelle Transcendante : immunité Wither/Lenteur/Lévitation (couronne dorée continue côté client : CosmeticAuraClientTicker)
        if (mask.has(IDX_ARMURE_ETERNELLE_TRANSCENDANTE) && entity.tickCount % 20 == 0) {
            entity.removeEffect(MobEffects.WITHER);
            entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            entity.removeEffect(MobEffects.LEVITATION);
        }

        // #097 Dieu de la Lame Suprême : Force II continue (halo doré/argenté continu côté client : CosmeticAuraClientTicker)
        if (mask.has(IDX_DIEU_LAME_SUPREME) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 50, 1, true, false));
        }

        // #098 Exterminateur de Titans Absolu : immunité totale au recul
        if (mask.has(IDX_EXTERMINATEUR_TITANS_ABSOLU) && entity.hurtTime == 9) {
            entity.setDeltaMovement(0.0, Math.min(0.0, entity.getDeltaMovement().y), 0.0);
            entity.hurtMarked = true;
        }

        // #100 Archange du Jugement Solaire : Régénération I au soleil (rubans d'ailes dorés continus côté client : CosmeticAuraClientTicker)
        if (mask.has(IDX_ARCHANGE_JUGEMENT_SOLAIRE) && entity.tickCount % 25 == 0) {
            if (entity.level().isDay() && entity.level().canSeeSky(entity.blockPosition())) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 55, 0, true, false));
            }
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

        // #105 Seigneur de Guerre de l'Apocalypse : immunité Feu/Wither (aura volcanique continue côté client : CosmeticAuraClientTicker)
        if (mask.has(IDX_SEIGNEUR_GUERRE_APOCALYPSE) && entity.tickCount % 20 == 0) {
            entity.clearFire();
            entity.removeEffect(MobEffects.WITHER);
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 50, 0, true, false));
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

        // #109 Avatar Ultime du Deep's Lucky Block : Chance III + Régénération I (Halo Stellaire Blanc & Or continu à 20 Hz côté client : CosmeticAuraClientTicker)
        if (mask.has(IDX_AVATAR_SUPREME_DEEP_LUCKY) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.LUCK, 55, 2, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 55, 0, true, false));
        }

        // ---- T332-COMBOS-210 : PASSIVES & TICK HOOKS (#111 .. #210) ----

        // Suivi de la chute maximale pour les synergies d'atterrissage (#158, #181)
        if (!entity.onGround()) {
            st.maxFallDist = Math.max(st.maxFallDist, entity.fallDistance);
        }

        // #119 Coureur des Averses : pluie ou eau -> Vitesse II + dissipe Fatigue de Minage
        if (mask.has(IDX_COUREUR_PLUIE) && entity.isInWaterOrRain() && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 1, true, false));
            entity.removeEffect(MobEffects.DIG_SLOWDOWN);
        }

        // #122 Voltigeur Arbalétrier : épauler ou tenir une arbalète chargée -> Vitesse II + purge Lenteur
        if (mask.has(IDX_ARBALETRIER_RODEUR)) {
            ItemStack mh = entity.getMainHandItem();
            boolean activeCrossbow = (mh.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(mh))
                    || (entity.isUsingItem() && entity.getUseItem().getItem() instanceof CrossbowItem);
            if (activeCrossbow && entity.tickCount % 15 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 1, true, false));
                entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }

        // #123 Marcheur Lucide : zone éclairée (>= 9) -> purge Lenteur/Nausée/Cécité + Vitesse I
        if (mask.has(IDX_VEILLEUR_CLAIRVOYANT) && entity.tickCount % 20 == 0) {
            if (entity.level().getMaxLocalRawBrightness(entity.blockPosition()) >= 9) {
                entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                entity.removeEffect(MobEffects.CONFUSION);
                entity.removeEffect(MobEffects.BLINDNESS);
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
            }
        }

        // #126 Chamois des Collines : sauter en avançant -> Vitesse I (3 s)
        if (mask.has(IDX_GRIMPEUR_PENTE) && !entity.onGround() && st.wasOnGround
                && entity.getDeltaMovement().horizontalDistanceSqr() > 0.008) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
        }

        // #127 Carapace de Granit : accroupi au sol -> Résistance II + réduction de recul
        if (mask.has(IDX_ABRI_TORTUE) && entity.onGround() && entity.isShiftKeyDown()) {
            if (entity.tickCount % 15 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 35, 1, true, false));
            }
            if (entity.hurtTime == 9) {
                Vec3 dm = entity.getDeltaMovement();
                entity.setDeltaMovement(dm.x * 0.2, Math.min(0.05, dm.y * 0.2), dm.z * 0.2);
                entity.hurtMarked = true;
            }
        }

        // #137 Catapulte Cinétique : bondir depuis l'accroupissement -> onde répulsive + Vitesse II
        if (mask.has(IDX_SAUTEUR_RESSORT) && !entity.onGround() && st.wasOnGround
                && (st.wasShiftDown || entity.isShiftKeyDown()) && entity.getDeltaMovement().y > 0.20
                && now >= st.springJumpCd) {
            st.springJumpCd = now + 30L;
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 70, 1, true, false));
            if (sl != null) {
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.1, entity.getZ(),
                        ParticleTypes.CLOUD, 3.0, 16, 0.04);
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(3.5))) {
                    if (!isFriendlySummon(mob, entity)) {
                        Vec3 push = mob.position().subtract(entity.position());
                        if (push.lengthSqr() > 0.01) {
                            mob.setDeltaMovement(push.normalize().scale(0.80).add(0, 0.32, 0));
                            mob.hurtMarked = true;
                        }
                    }
                }
            }
        }

        // #145 Planeur des Courants Chauds : vol Élytres au-dessus du feu/lave ou dans le Nether -> ascendance douce
        if (mask.has(IDX_PLANEUR_THERMIQUE) && entity.isFallFlying()) {
            boolean hotUpdraft = entity.level().dimension() == Level.NETHER;
            if (!hotUpdraft) {
                BlockPos base = entity.blockPosition();
                for (int dy = 1; dy <= 8; dy++) {
                    BlockState bs = entity.level().getBlockState(base.below(dy));
                    if (bs.is(Blocks.LAVA) || bs.is(Blocks.FIRE) || bs.is(Blocks.SOUL_FIRE)
                            || bs.is(Blocks.MAGMA_BLOCK) || bs.is(Blocks.CAMPFIRE)) {
                        hotUpdraft = true;
                        break;
                    }
                }
            }
            if (hotUpdraft) {
                Vec3 dm = entity.getDeltaMovement();
                if (dm.y < 0.045) {
                    entity.setDeltaMovement(dm.x, Math.min(0.045, dm.y + 0.065), dm.z);
                    entity.hurtMarked = true;
                    entity.fallDistance = 0.0f;
                }
            }
        }

        // #147 Sentinelle des Murailles : contre un mur -> Régénération I
        if (mask.has(IDX_BASTION_MURAL) && entity.tickCount % 20 == 0
                && UniversalEnchantmentHandler.isTouchingHorizontalWall(entity)) {
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, 0, true, false));
        }

        // #158 Rebond Tellurique : atterrissage après > 3 blocs -> onde de choc sur 4 blocs (6 dégâts)
        if (mask.has(IDX_SUMO_SISMIQUE) && entity.onGround() && !st.wasOnGround && st.maxFallDist >= 3.0f && sl != null) {
            EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.1, entity.getZ(),
                    EnchantmentParticleFX.EARTH_OCHRE, 4.0, 22, 0.05);
            sl.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.55f, 1.15f);
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(4.0))) {
                if (!isFriendlySummon(mob, entity)) {
                    safeHurt(mob, entity.damageSources().mobAttack(entity), 6.0f);
                    Vec3 away = mob.position().subtract(entity.position());
                    if (away.lengthSqr() > 0.01) {
                        mob.setDeltaMovement(away.normalize().scale(0.85).add(0, 0.42, 0));
                        mob.hurtMarked = true;
                    }
                }
            }
        }

        // #160 Pyromancien de Mêlée : tant que vous brûlez -> Résistance au Feu
        if (mask.has(IDX_BERSERKER_FLAMMES) && entity.isOnFire() && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 45, 0, true, false));
        }

        // #161 Capitaine Mercenaire : renforce vos troupes invoquées proches (Force I + Résistance II)
        if (mask.has(IDX_COMMANDANT_MEUTE_FER) && sl != null && entity.tickCount % 25 == 0) {
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(10.0))) {
                if (isFriendlySummon(mob, entity)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, false));
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, true, false));
                }
            }
        }

        // #169 Dompteur d'Orage Ailé : en vol d'Élytres -> répare les ailes et électrocute les ennemis proches
        if (mask.has(IDX_AILES_ORAGEUSES_MAITRISEES) && entity.isFallFlying() && entity.tickCount % 25 == 0) {
            ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.isDamageableItem() && chest.getDamageValue() > 0) {
                chest.setDamageValue(Math.max(0, chest.getDamageValue() - 1));
            }
            if (sl != null) {
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(4.5))) {
                    if (!isFriendlySummon(mob, entity)) {
                        safeHurt(mob, entity.damageSources().lightningBolt(), 5.0f);
                        EnchantmentParticleFX.burstOnEntity(mob, ParticleTypes.ELECTRIC_SPARK, 8, 0.3, 0.04);
                    }
                }
            }
        }

        // #173 Avatar de la Salamandre : tant que vous brûlez -> Force II + Régénération II + Résistance au Feu
        if (mask.has(IDX_PACTE_SALAMANDRE_ARDENT) && entity.isOnFire() && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 45, 1, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, 1, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 45, 0, true, false));
        }

        // #174 Seigneur du Ressac Abyssal : dans l'eau ou sous la pluie -> Régénération II
        if (mask.has(IDX_KRAKEN_RESSAC_MAUDIT) && entity.isInWaterOrRain() && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, 1, true, false));
        }

        // #175 Seigneur Nocturne du Vide : apaise le drain du jour par régénération douce
        if (mask.has(IDX_PREDATEUR_VOIDLING_NOCTURNE) && entity.level().isDay() && entity.tickCount % 30 == 0) {
            entity.heal(1.0f);
        }

        // #177 Monolithe Inébranlable : Résistance II permanente
        if (mask.has(IDX_COLOSSE_RACINES_PIERRE) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 1, true, false));
        }

        // #181 Comète à Grappin : atterrissage chargé -> onde météore 5 blocs
        if (mask.has(IDX_VOLTIGEUR_PLONGEON_METEORE) && entity.onGround() && !st.wasOnGround
                && now < st.grappleMeteorUntil && sl != null) {
            st.grappleMeteorUntil = 0L;
            EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.15, entity.getZ(),
                    ParticleTypes.LAVA, 4.8, 24, 0.06);
            sl.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.15f);
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(5.0))) {
                if (!isFriendlySummon(mob, entity)) {
                    safeHurt(mob, entity.damageSources().onFire(), 9.0f);
                    mob.setRemainingFireTicks(Math.max(mob.getRemainingFireTicks(), 80));
                }
            }
        }

        // #182 Grand Maréchal d'Empire : Force II + Résistance I + Régénération I sur 12 blocs
        if (mask.has(IDX_MARECHAL_SOUVERAIN) && entity.tickCount % 25 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            if (sl != null) {
                for (LivingEntity ally : sl.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(12.0))) {
                    if (ally != entity && (ally instanceof Player || (ally instanceof Mob m && isFriendlySummon(m, entity)))) {
                        ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, true, false));
                        ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
                        ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
                    }
                }
            }
        }

        // #185 Cataphractaire Foudroyant : sur monture -> Résistance au Feu + Absorption sur la monture
        if (mask.has(IDX_CAVALIER_TONNERRE_ROYAL) && entity.isPassenger()
                && entity.getVehicle() instanceof LivingEntity mount && entity.tickCount % 25 == 0) {
            mount.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
            mount.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 1, true, false));
        }

        // #188 Éveil de la Chimère Prismatique : Régénération II périodique + onde électrique en sautant
        if (mask.has(IDX_CHIMERE_SEPT_COEURS)) {
            if (entity.tickCount % 40 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 55, 1, true, false));
            }
            if (!entity.onGround() && st.wasOnGround && entity.getDeltaMovement().y > 0.20 && sl != null) {
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.15, entity.getZ(),
                        ParticleTypes.ELECTRIC_SPARK, 3.6, 18, 0.05);
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(4.0))) {
                    if (!isFriendlySummon(mob, entity)) {
                        safeHurt(mob, entity.damageSources().lightningBolt(), 5.0f);
                    }
                }
            }
        }

        // #193 Forgeron du Noyau Stellaire : Célérité III + Résistance au Feu continues
        if (mask.has(IDX_SEIGNEUR_FORGES_STELLAIRES) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 2, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 50, 0, true, false));
        }

        // #196 Banquet du Titan Invincible : faim >= 14 -> maintient 5 cœurs d'Absorption
        if (mask.has(IDX_JUGGERNAUT_GOURMAND_COLOSSAL) && entity.tickCount % 25 == 0
                && entity instanceof Player p196 && p196.getFoodData().getFoodLevel() >= 14) {
            entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), 10.0f));
        }

        // #199 Souverain de la Stratosphère : en l'air ou en Élytres -> Régénération II continue
        if (mask.has(IDX_AERONAUTE_SUPERSONIQUE_ROYAL) && (entity.isFallFlying() || !entity.onGround())
                && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, 1, true, false));
        }

        // #200 Dieu-Bourreau du Cataclysme : Force II + Résistance II permanentes
        if (mask.has(IDX_DIEU_HACHE_CATACLYSME) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 50, 1, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 1, true, false));
        }

        // #201 Grand Amiral des Sept Mers : Chance III + Régénération II permanentes
        if (mask.has(IDX_GRAND_AMIRAL_SEPT_MERS) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.LUCK, 55, 2, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 55, 1, true, false));
        }

        // #203 Forgeron Divin des Reliques : Célérité IV + Résistance au Feu + réparation continue
        if (mask.has(IDX_FORGERON_DIVIN_RELIQUE) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 3, true, false));
            entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 50, 0, true, false));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack eq = entity.getItemBySlot(slot);
                if (!eq.isEmpty() && eq.isDamageableItem() && eq.getDamageValue() > 0) {
                    eq.setDamageValue(Math.max(0, eq.getDamageValue() - 1));
                }
            }
        }

        // #204 Tireur Céleste Omniscient : Vision Nocturne permanente
        if (mask.has(IDX_SNIPER_CELESTE_OMNISCIENT) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
        }

        // #205 Bastion Immortel de l'Égide : Régénération II continue + immunité totale au recul
        if (mask.has(IDX_CITADELLE_IMMORTELLE_EGIDE)) {
            if (entity.tickCount % 20 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 1, true, false));
            }
            if (entity.hurtTime == 9) {
                entity.setDeltaMovement(0.0, Math.min(0.0, entity.getDeltaMovement().y), 0.0);
                entity.hurtMarked = true;
            }
        }

        // #206 Avatar de la Chimère Primordiale : feu/noyade annulés + éclair curatif à chaque saut
        if (mask.has(IDX_AVATAR_CHIMERE_PRIMORDIALE)) {
            if (entity.tickCount % 20 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 50, 0, true, false));
                entity.setAirSupply(entity.getMaxAirSupply());
            }
            if (!entity.onGround() && st.wasOnGround && entity.getDeltaMovement().y > 0.20 && sl != null) {
                entity.heal(2.0f);
                EnchantmentParticleFX.shockwaveRing(sl, entity.getX(), entity.getY() + 0.15, entity.getZ(),
                        ParticleTypes.ELECTRIC_SPARK, 4.0, 20, 0.05);
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(4.5))) {
                    if (!isFriendlySummon(mob, entity)) {
                        safeHurt(mob, entity.damageSources().lightningBolt(), 6.0f);
                    }
                }
            }
        }

        // #207 Seigneur du Fléau Émeraude : immunité Poison/Wither + aura corrosive 6 blocs
        if (mask.has(IDX_SEIGNEUR_FLEAU_TOXIQUE) && entity.tickCount % 20 == 0) {
            entity.removeEffect(MobEffects.POISON);
            entity.removeEffect(MobEffects.WITHER);
            if (sl != null) {
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(6.0))) {
                    if (!isFriendlySummon(mob, entity)) {
                        mob.addEffect(new MobEffectInstance(MobEffects.POISON, 70, 1));
                        mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 70, 0));
                    }
                }
            }
        }

        // #208 Souverain de la Légion d'Or : Force II + Résistance II + Vitesse II aux troupes invoquées
        if (mask.has(IDX_EMPEREUR_COMMANDANT_SUPREME) && sl != null && entity.tickCount % 25 == 0) {
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(14.0))) {
                if (isFriendlySummon(mob, entity)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, true, false));
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, true, false));
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false));
                }
            }
        }

        // #209 Élu Astral de la Chance Infinie : Chance IV permanente
        if (mask.has(IDX_TRANSCENDANCE_CHANCE_INFINIE) && entity.tickCount % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.LUCK, 55, 3, true, false));
        }
        // ==================== T333 : TICK SERVEUR DES COMBOS DE BAS GRADE (#211-#310) ====================
        if (entity instanceof Player pl333 && sl != null) {
            BlockPos bp333 = pl333.blockPosition();
            BlockState below333 = sl.getBlockState(bp333.below());
            String belowId333 = below333.getBlock().getDescriptionId();

            // #219 Patrouilleur des Clairières : en pleine lumière (>= 12), Vitesse I
            if (mask.has(IDX_PATROUILLEUR_CLAIRIERE) && pl333.tickCount % 30 == 0 && sl.getMaxLocalRawBrightness(bp333) >= 12) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 55, 0, true, false));
            }
            // #221 Puisatier des Profondeurs : sous Y = 32, Vision Nocturne + Célérité I
            if (mask.has(IDX_PUISATIER_AIR_RARE) && pl333.tickCount % 40 == 0 && pl333.getY() < 32.0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
                pl333.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 65, 0, true, false));
            }
            // #224 Cantonnier des Névés : sur neige/glace, Vitesse I + purge Lenteur
            if (mask.has(IDX_DENEIGEUR_PAS_FERME) && pl333.tickCount % 25 == 0
                    && (belowId333.contains("snow") || belowId333.contains("ice") || sl.getBlockState(bp333).is(Blocks.SNOW))) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
                pl333.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            // #233 Moissonneur au Panier Plein : sur terre labourée, Vitesse I
            if (mask.has(IDX_MOISSONNEUR_PANIER_PLEIN) && pl333.tickCount % 25 == 0 && below333.is(Blocks.FARMLAND)) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
            }
            // #234 Gardien du Rucher : au soleil sur herbe/terre labourée, +0,5 cœur / 6 s
            if (mask.has(IDX_FERMIER_RUCHER_PAISIBLE) && pl333.tickCount % 120 == 0
                    && sl.isDay() && sl.canSeeSky(bp333) && (below333.is(Blocks.GRASS_BLOCK) || below333.is(Blocks.FARMLAND))) {
                pl333.heal(1.0f);
            }
            // #235 Glaneur des Chemins : sur chemin ou terre labourée, répare la houe (1 pt / 5 s)
            if (mask.has(IDX_GLANEUR_SENTIER_VERT) && pl333.tickCount % 100 == 0
                    && (below333.is(Blocks.DIRT_PATH) || below333.is(Blocks.FARMLAND))) {
                ItemStack mh = pl333.getMainHandItem();
                if (mh.getItem() instanceof HoeItem && mh.isDamageableItem() && mh.getDamageValue() > 0) {
                    mh.setDamageValue(mh.getDamageValue() - 1);
                }
            }
            // #239 Engrais Ambulant : fait pousser occasionnellement une culture proche
            if (mask.has(IDX_HERBORISTE_PIEDS_FLEURIS) && pl333.tickCount % 80 == 0) {
                for (BlockPos cp : BlockPos.betweenClosed(bp333.offset(-2, -1, -2), bp333.offset(2, 1, 2))) {
                    BlockState cs = sl.getBlockState(cp);
                    if (cs.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && !crop.isMaxAge(cs) && sl.random.nextInt(4) == 0) {
                        sl.setBlock(cp, crop.getStateForAge(crop.getAge(cs) + 1), 2);
                        break;
                    }
                }
            }
            // #252 Passeur des Gués : dans l'eau ou sous la pluie, Grâce du Dauphin I + Respiration Aquatique
            if (mask.has(IDX_MARIN_GUE_RAPIDE) && pl333.tickCount % 30 == 0 && pl333.isInWaterOrRain()) {
                pl333.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 55, 0, true, false));
                pl333.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 55, 0, true, false));
            }
            // #253 Éclaireur des Récifs : sous l'eau, Vision Nocturne
            if (mask.has(IDX_PLONGEUR_AIMANT_MARIN) && pl333.tickCount % 40 == 0 && pl333.isUnderWater()) {
                pl333.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
            }
            // #254 Harponneur des Bas-Fonds : dans l'eau, Respiration Aquatique
            if (mask.has(IDX_HARPONNEUR_ABYSSES_DOMPTE) && pl333.tickCount % 35 == 0 && pl333.isInWater()) {
                pl333.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
            }
            // #257 Pêcheur au Crépuscule : flotteur lancé -> Résistance I + repousse Phantoms proches
            if (mask.has(IDX_PECHEUR_MOUCHE_PAISIBLE) && pl333.fishing != null && pl333.tickCount % 25 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 45, 0, true, false));
                for (Phantom ph : sl.getEntitiesOfClass(Phantom.class, pl333.getBoundingBox().inflate(7.0))) {
                    Vec3 away = ph.position().subtract(pl333.position());
                    if (away.lengthSqr() > 0.01) ph.setDeltaMovement(away.normalize().scale(0.65).add(0, 0.25, 0));
                }
            }
            // #260 Estafette des Grands Chemins : sprint -> Vitesse I + réduction perte faim
            if (mask.has(IDX_MESSAGER_ROUTES_PAVEES) && pl333.isSprinting() && pl333.tickCount % 30 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 0, true, false));
                pl333.getFoodData().eat(0, 0.08f);
            }
            // #261 Chamois des Collines : Saut Amélioré I
            if (mask.has(IDX_VOLTIGEUR_TALUS_BOND) && pl333.tickCount % 40 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.JUMP, 65, 0, true, false));
            }
            // #262 Postillon Royal : monture -> Régénération I
            if (mask.has(IDX_CAVALIER_ESTAFETTE) && pl333.getVehicle() instanceof LivingEntity steed333 && pl333.tickCount % 40 == 0) {
                steed333.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 65, 0, true, false));
            }
            // #263 Veilleur de l'Aube : au soleil, purge Lenteur + soigne 0,5 cœur / 8 s
            if (mask.has(IDX_ECLAIREUR_AUBE_CLAIRE) && sl.isDay() && sl.canSeeSky(bp333)) {
                if (pl333.tickCount % 40 == 0) pl333.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (pl333.tickCount % 160 == 0) pl333.heal(1.0f);
            }
            // #264 Acrobate des Toits & #308 Dompteur de Bourrasques : atterrissage après chute >= 3 blocs -> Vitesse I
            if ((mask.has(IDX_MARCHEUR_SEMELLES_PLUME) || mask.has(IDX_REDEMPTION_TURBULENCE_PLANEUR))
                    && pl333.onGround() && !st.wasOnGround && st.maxFallDist >= 3.0f) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
                if (mask.has(IDX_REDEMPTION_ATTERRISSAGE_LOURD)) {
                    for (Mob m : sl.getEntitiesOfClass(Mob.class, pl333.getBoundingBox().inflate(3.0))) {
                        if (!isFriendlySummon(m, pl333)) safeHurt(m, pl333.damageSources().playerAttack(pl333), 3.0f);
                    }
                }
            } else if (mask.has(IDX_REDEMPTION_ATTERRISSAGE_LOURD) && pl333.onGround() && !st.wasOnGround && st.maxFallDist >= 3.0f) {
                for (Mob m : sl.getEntitiesOfClass(Mob.class, pl333.getBoundingBox().inflate(3.0))) {
                    if (!isFriendlySummon(m, pl333)) safeHurt(m, pl333.damageSources().playerAttack(pl333), 3.0f);
                }
            }
            // #267 Colporteur Aimanté : sprint -> répare 1 pt d'armure / 6 s
            if (mask.has(IDX_GUIDE_SENTIER_AIMANTE) && pl333.isSprinting() && pl333.tickCount % 120 == 0) {
                repairMostDamagedArmor(pl333, 1);
            }
            // #268 Éclaireur des Trois Horizons : Vitesse I sur terre ferme
            if (mask.has(IDX_RANGER_TROIS_HORIZONS) && !pl333.isInWater() && pl333.tickCount % 35 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 55, 0, true, false));
            }
            // #277 Champion des Quais : à mains nues -> Célérité I
            if (mask.has(IDX_CHAMPION_TAVERNE_TROIS_POINGS) && pl333.getMainHandItem().isEmpty() && pl333.tickCount % 30 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 0, true, false));
            }
            // #283 Ferrailleur Prévoyant : arme ou bouclier < 35 % durabilité -> Résistance I
            if (mask.has(IDX_FERRAILLEUR_BOUCLIER_RECYCLE) && pl333.tickCount % 35 == 0) {
                ItemStack mh = pl333.getMainHandItem();
                ItemStack oh = pl333.getOffhandItem();
                boolean lowD = (mh.isDamageableItem() && mh.getDamageValue() >= mh.getMaxDamage() * 0.65f)
                        || (oh.isDamageableItem() && oh.getDamageValue() >= oh.getMaxDamage() * 0.65f);
                if (lowD) pl333.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 55, 0, true, false));
            }
            // #285 Porte-Écu Mobile : bouclier levé -> Vitesse I
            if (mask.has(IDX_GARDE_PAS_LATERAL_PAVOIS) && pl333.isBlocking() && pl333.tickCount % 15 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, true, false));
            }
            // #290 Éclaireur des Cendrières : en feu ou proche magma/lave -> Vitesse I
            if (mask.has(IDX_MARCHEUR_CENDRE_ARDENTE) && pl333.tickCount % 25 == 0
                    && (pl333.isOnFire() || belowId333.contains("magma") || belowId333.contains("obsidian"))) {
                pl333.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 0, true, false));
            }
            // #298 Veilleur des Trois Braises : Résistance au Feu constante
            if (mask.has(IDX_VEILLEUR_FLAMME_CLAIRE) && pl333.tickCount % 40 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 65, 0, true, false));
            }
            // #302 Second Souffle Amphibie : en sprint ou dans l'eau -> +1 cœur / 4 s
            if (mask.has(IDX_REDEMPTION_SOUFFLE_COURT) && pl333.tickCount % 80 == 0 && (pl333.isSprinting() || pl333.isInWater())) {
                pl333.heal(2.0f);
            }
            // #303 Mineur au Corps-à-Corps : Célérité II constante
            if (mask.has(IDX_REDEMPTION_PORTEE_COURTE) && pl333.tickCount % 35 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 55, 1, true, false));
            }
            // #309 Héros en Armure cabossée : Résistance I constante
            if (mask.has(IDX_PENITENT_FERRAILLE_HEROIQUE) && pl333.tickCount % 40 == 0) {
                pl333.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 65, 0, true, false));
            }
        }


        if (entity.onGround()) {
            st.maxFallDist = 0.0f;
        }
        st.wasOnGround = entity.onGround();
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

            if (victim.getControllingPassenger() instanceof Player rider262
                    && EnchantmentComboRegistry.hasCombo(rider262, IDX_CAVALIER_ESTAFETTE)) {
                event.setAmount(event.getAmount() * 0.85f);
            }
            // --- A. Défense du porteur d'un combo ---
            ComboMask defMask = EnchantmentComboRegistry.getActiveMask(victim);
            if (!defMask.isEmpty()) {
                PlayerComboState defSt = stateOf(victim);
                // ==================== T333 : DÉFENSE DES COMBOS DE BAS GRADE (#211-#310) ====================
                if (defMask.has(IDX_PLONGEUR_AIMANT_MARIN) && victim.isUnderWater()) {
                    event.setAmount(event.getAmount() * 0.85f);
                }
                if (defMask.has(IDX_ARCHER_GRAPPIN_AEROFREIN) && event.getSource().is(DamageTypeTags.IS_FALL)
                        && now < victim.getPersistentData().getLong("slb_c247_fall_safe")) {
                    event.setCanceled(true);
                    return;
                }
                if (defMask.has(IDX_VOLTIGEUR_TALUS_BOND) && event.getSource().is(DamageTypeTags.IS_FALL)) {
                    event.setAmount(event.getAmount() * 0.70f);
                }
                if (defMask.has(IDX_PLANEUR_FREIN_PLUME)
                        && (event.getSource().is(DamageTypeTags.IS_FALL) || "flyIntoWall".equals(event.getSource().getMsgId()))) {
                    event.setAmount(event.getAmount() * 0.50f);
                }
                if (defMask.has(IDX_RANGER_TROIS_HORIZONS) && event.getSource().is(DamageTypeTags.IS_FALL)) {
                    event.setAmount(event.getAmount() * 0.75f);
                }
                if (defMask.has(IDX_LUTTEUR_INAMOVIBLE) && rawAttacker instanceof LivingEntity
                        && now >= victim.getPersistentData().getLong("slb_c272_cd")) {
                    victim.getPersistentData().putLong("slb_c272_cd", now + 80L);
                    victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, false));
                }
                if (defMask.has(IDX_ENCAISSEUR_COEUR_CHAUD) && victim.isShiftKeyDown()) {
                    event.setAmount(event.getAmount() * 0.85f);
                    if (rawAttacker instanceof LivingEntity livAtk275) {
                        livAtk275.setRemainingFireTicks(Math.max(livAtk275.getRemainingFireTicks(), 40));
                    }
                }
                if (defMask.has(IDX_BOXEUR_ESQUIVE_CROISEE) && victim.getMainHandItem().isEmpty()) {
                    event.setAmount(event.getAmount() * 0.85f);
                    victim.getPersistentData().putLong("slb_c276_charge", now + 80L);
                }
                if (defMask.has(IDX_MARCHEUR_CENDRE_ARDENTE) && event.getSource().is(DamageTypeTags.IS_FIRE)) {
                    event.setAmount(event.getAmount() * 0.75f);
                }
                if (defMask.has(IDX_LAME_SANCTUAIRE_MAGMA) && "hotFloor".equals(event.getSource().getMsgId())) {
                    event.setCanceled(true);
                    return;
                }
                if (defMask.has(IDX_ECORCHEUR_BALAYAGE_LARGE) && victim.level() instanceof ServerLevel sl299D) {
                    int near299 = sl299D.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.0), m -> m.isAlive() && !isFriendlySummon(m, victim)).size();
                    if (near299 >= 2) event.setAmount(event.getAmount() * 0.90f);
                }
                if (defMask.has(IDX_REDEMPTION_ATTERRISSAGE_LOURD) && event.getSource().is(DamageTypeTags.IS_FALL)) {
                    event.setAmount(event.getAmount() * 0.50f);
                }
                if (defMask.has(IDX_REDEMPTION_OPPRESSION_THORACIQUE)) {
                    event.setAmount(event.getAmount() * 0.82f);
                    if (victim.isShiftKeyDown()) {
                        victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
                    }
                }
                if (defMask.has(IDX_REDEMPTION_TURBULENCE_PLANEUR)
                        && (event.getSource().is(DamageTypeTags.IS_FALL) || "flyIntoWall".equals(event.getSource().getMsgId()))) {
                    event.setAmount(event.getAmount() * 0.40f);
                }


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

                // ---- T332-COMBOS-210 : DÉFENSE (#111 .. #210) ----
                String msgId = event.getSource().getMsgId();

                // #112 Étayeur de Galerie : annule les dégâts de chute de blocs / étouffement de gravier-sable
                if (defMask.has(IDX_MINEUR_STABILISATEUR)
                        && ("fallingBlock".equals(msgId) || "anvil".equals(msgId) || "inWall".equals(msgId))) {
                    event.setCanceled(true);
                    return;
                }

                // #126 Chamois des Collines : -30 % dégâts de chute
                if (defMask.has(IDX_GRIMPEUR_PENTE) && "fall".equals(msgId)) {
                    event.setAmount(event.getAmount() * 0.70f);
                }

                // #136 Rempart de Ronces : armure chargée par blocage -> +5 dégâts d'épines
                if (defMask.has(IDX_PROVOCATEUR_EPINEUX) && now < defSt.thornsShieldChargeUntil
                        && rawAttacker instanceof LivingEntity aggr136
                        && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                    safeHurt(aggr136, victim.damageSources().thorns(victim), 5.0f);
                }

                // #142 Survivant des Tranchées : sous 50 % PV ou arme usée > 40 % -> -22 % dégâts subis
                if (defMask.has(IDX_VETERAN_ENDURCI)) {
                    ItemStack mh142 = victim.getMainHandItem();
                    boolean wornWeapon = mh142.isDamageableItem() && mh142.getDamageValue() >= mh142.getMaxDamage() * 0.40;
                    if (victim.getHealth() <= victim.getMaxHealth() * 0.50f || wornWeapon) {
                        event.setAmount(event.getAmount() * 0.78f);
                    }
                }

                // #161 Capitaine Mercenaire : -20 % dégâts subis
                if (defMask.has(IDX_COMMANDANT_MEUTE_FER)) {
                    event.setAmount(event.getAmount() * 0.80f);
                }

                // #166 Réflexe Blindé : purge Lenteur + Résistance II et Vitesse II (3 s)
                if (defMask.has(IDX_CARAPACE_CINETIQUE)) {
                    victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, true, false));
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false));
                }

                // #173 Avatar de la Salamandre : en feu -> libère une nova ardente lorsque touché
                if (defMask.has(IDX_PACTE_SALAMANDRE_ARDENT) && victim.isOnFire() && victim.level() instanceof ServerLevel slSal) {
                    EnchantmentParticleFX.shockwaveRing(slSal, victim.getX(), victim.getY() + 0.3, victim.getZ(),
                            ParticleTypes.FLAME, 3.6, 20, 0.05);
                    for (Mob m : slSal.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(3.8))) {
                        if (!isFriendlySummon(m, victim)) {
                            m.setRemainingFireTicks(Math.max(m.getRemainingFireTicks(), 100));
                            safeHurt(m, victim.damageSources().onFire(), 5.0f);
                        }
                    }
                }

                // #174 Seigneur du Ressac Abyssal : -30 % dégâts subis hors de l'eau (compense le Ressac)
                if (defMask.has(IDX_KRAKEN_RESSAC_MAUDIT) && !victim.isInWaterOrRain()) {
                    event.setAmount(event.getAmount() * 0.70f);
                }

                // #176 Mirage du Vent Vif : -25 % dégâts, aveugle l'agresseur et charge la prochaine frappe (+45 %)
                if (defMask.has(IDX_DANSEUR_MIRAGE_ZEPHYR)) {
                    event.setAmount(event.getAmount() * 0.75f);
                    defSt.mirageRiposteUntil = now + 100L;
                    if (rawAttacker instanceof LivingEntity aggr176) {
                        aggr176.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
                    }
                }

                // #181 Comète à Grappin & #186 Archer des Failles Astrales : immunité chute post-tir
                if ("fall".equals(msgId) && ((defMask.has(IDX_VOLTIGEUR_PLONGEON_METEORE) && now < defSt.grappleMeteorUntil)
                        || (defMask.has(IDX_ARCHER_DISTORSION_VIDE) && now < defSt.riftFallImmuneUntil))) {
                    if (defMask.has(IDX_VOLTIGEUR_PLONGEON_METEORE) && victim.level() instanceof ServerLevel slMet) {
                        defSt.grappleMeteorUntil = 0L;
                        EnchantmentParticleFX.shockwaveRing(slMet, victim.getX(), victim.getY() + 0.15, victim.getZ(),
                                ParticleTypes.LAVA, 4.8, 24, 0.06);
                        for (Mob m : slMet.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(5.0))) {
                            if (!isFriendlySummon(m, victim)) {
                                safeHurt(m, victim.damageSources().onFire(), 9.0f);
                                m.setRemainingFireTicks(Math.max(m.getRemainingFireTicks(), 80));
                            }
                        }
                    }
                    event.setCanceled(true);
                    return;
                }

                // #193 Forgeron du Noyau Stellaire : immunité complète au feu et à la lave
                if (defMask.has(IDX_SEIGNEUR_FORGES_STELLAIRES) && event.getSource().is(DamageTypeTags.IS_FIRE)) {
                    victim.clearFire();
                    event.setCanceled(true);
                    return;
                }

                // #196 Banquet du Titan Invincible : faim >= 14 -> -20 % dégâts reçus
                if (defMask.has(IDX_JUGGERNAUT_GOURMAND_COLOSSAL)
                        && victim instanceof Player vp196 && vp196.getFoodData().getFoodLevel() >= 14) {
                    event.setAmount(event.getAmount() * 0.80f);
                }

                // #197 Canonnier d'Apocalypse Solaire : immunité au feu et aux explosions
                if (defMask.has(IDX_ARTILLEUR_METEOR_ECARLATE)
                        && (event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.IS_EXPLOSION))) {
                    victim.clearFire();
                    event.setCanceled(true);
                    return;
                }

                // #199 Souverain de la Stratosphère : immunité chocs/chute + -30 % dégâts en l'air
                if (defMask.has(IDX_AERONAUTE_SUPERSONIQUE_ROYAL)) {
                    if ("fall".equals(msgId) || "flyIntoWall".equals(msgId)) {
                        event.setCanceled(true);
                        return;
                    }
                    if (victim.isFallFlying() || !victim.onGround()) {
                        event.setAmount(event.getAmount() * 0.70f);
                    }
                }

                // #201 Grand Amiral des Sept Mers & #206 Avatar Chimère Primordiale : annule la pénalité hors de l'eau du Ressac
                if ((defMask.has(IDX_GRAND_AMIRAL_SEPT_MERS) || defMask.has(IDX_AVATAR_CHIMERE_PRIMORDIALE))
                        && !victim.isInWaterOrRain()) {
                    event.setAmount(event.getAmount() * 0.78f);
                }
                if (defMask.has(IDX_AVATAR_CHIMERE_PRIMORDIALE)
                        && (event.getSource().is(DamageTypeTags.IS_FIRE) || "drown".equals(msgId))) {
                    victim.clearFire();
                    event.setCanceled(true);
                    return;
                }

                // #204 Tireur Céleste Omniscient : zéro dégât de chute + 40 % déviation des projectiles
                if (defMask.has(IDX_SNIPER_CELESTE_OMNISCIENT)) {
                    if ("fall".equals(msgId)) {
                        event.setCanceled(true);
                        return;
                    }
                    if (event.getSource().is(DamageTypeTags.IS_PROJECTILE) && victim.getRandom().nextInt(100) < 40) {
                        EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CLOUD, 10, 0.3, 0.04);
                        event.setCanceled(true);
                        return;
                    }
                }

                // #205 Bastion Immortel de l'Égide : -30 % dégâts subis + charge la riposte (+80 %)
                if (defMask.has(IDX_CITADELLE_IMMORTELLE_EGIDE)) {
                    event.setAmount(event.getAmount() * 0.70f);
                    defSt.aegisRetaliationUntil = now + 100L;
                }

                // #208 Souverain de la Légion d'Or : -25 % dégâts subis
                if (defMask.has(IDX_EMPEREUR_COMMANDANT_SUPREME)) {
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
                TestProcedure.schedule(slEcho, TestProcedure.currentTick(slEcho) + 10, () -> {
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

            // ---- T332-COMBOS-210 : OFFENSE (#111 .. #210) ----

            // #110 Lame d'Acier Impeccable : durabilité >= 90 % -> +20 % dégâts mêlée
            if (atkMask.has(IDX_LAME_NEUVE_ETINCELANTE) && !isProjectile && weapon.isDamageableItem()
                    && weapon.getDamageValue() <= weapon.getMaxDamage() * 0.10) {
                event.setAmount(event.getAmount() * 1.20f);
            }

            // #117 Carquois d'Éclats de Silex : +2 dégâts perforants + 35 % remboursement de flèche
            if (atkMask.has(IDX_ARCHER_RECUPERATEUR) && isProjectile
                    && (weapon.getItem() instanceof BowItem || event.getSource().getDirectEntity() instanceof AbstractArrow)) {
                event.setAmount(event.getAmount() + 2.0f);
                if (attacker instanceof Player pl117 && attacker.getRandom().nextInt(100) < 35) {
                    pl117.getInventory().add(new ItemStack(Items.ARROW, 1));
                }
            }

            // #120 Maître de Feinte : Faiblesse I + Lenteur I (4 s)
            if (atkMask.has(IDX_DUELLISTE_DESARMEUR) && !isProjectile) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
            }

            // #121 Pugiliste Fulgurant : poing nu en sprintant -> +4 dégâts + 1 cœur d'Absorption
            if (atkMask.has(IDX_BOXEUR_SPRINT) && !isProjectile && weapon.isEmpty() && attacker.isSprinting()) {
                event.setAmount(event.getAmount() + 4.0f);
                attacker.setAbsorptionAmount(Math.max(attacker.getAbsorptionAmount(), 2.0f));
            }

            // #130 Lame à Choc Thermique : cible ralentie/gelée ET en feu -> +40 % dégâts
            if (atkMask.has(IDX_CHOC_THERMIQUE_LAME) && !isProjectile && victim.isOnFire()
                    && (victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || victim.getTicksFrozen() > 0)) {
                event.setAmount(event.getAmount() * 1.40f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CLOUD, 12, 0.35, 0.05);
            }

            // #138 Fleuret d'Argent : portée >= 3,0 blocs -> +30 % dégâts d'estoc
            if (atkMask.has(IDX_ESCRIMEUR_ALLONGE) && !isProjectile && attacker.distanceTo(victim) >= 3.0f) {
                event.setAmount(event.getAmount() * 1.30f);
            }

            // #139 Rôdeur du Crépuscule : lumière <= 7 -> Obscurité sur la cible + Vitesse II (4 s)
            if (atkMask.has(IDX_CHASSEUR_CREPUSCULE) && !isProjectile
                    && attacker.level().getMaxLocalRawBrightness(attacker.blockPosition()) <= 7) {
                victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
            }

            // #140 Guetteur des Remparts : arbalète depuis hauteur >= 2,5 blocs -> +35 % dégâts + Lenteur II
            if (atkMask.has(IDX_ARBALETE_FAUCON) && isProjectile && weapon.getItem() instanceof CrossbowItem
                    && (attacker.getY() - victim.getY()) >= 2.5) {
                event.setAmount(event.getAmount() * 1.35f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1));
            }

            // #141 Frappe des Points Vitaux : poing nu -> +6 dégâts + Lenteur IV (2 s)
            if (atkMask.has(IDX_GANTELET_CHIRURGIEN) && !isProjectile && weapon.isEmpty()) {
                event.setAmount(event.getAmount() + 6.0f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            }

            // #143 Exorciste de Bataille : contre cible empoisonnée/flétrie/affaiblie -> +25 % dégâts + soin 1 cœur
            if (atkMask.has(IDX_PURIFICATEUR_PLAIES) && !isProjectile
                    && (victim.hasEffect(MobEffects.POISON) || victim.hasEffect(MobEffects.WITHER) || victim.hasEffect(MobEffects.WEAKNESS))) {
                event.setAmount(event.getAmount() * 1.25f);
                attacker.heal(2.0f);
            }

            // #147 Sentinelle des Murailles : contre un mur -> +25 % dégâts mêlée
            if (atkMask.has(IDX_BASTION_MURAL) && !isProjectile
                    && UniversalEnchantmentHandler.isTouchingHorizontalWall(attacker)) {
                event.setAmount(event.getAmount() * 1.25f);
            }

            // #148 Canon d'Assaut : arbalète <= 7 blocs -> +35 % dégâts + 1 cœur d'Absorption
            if (atkMask.has(IDX_TIR_BOUT_PORTANT_LOURD) && isProjectile && weapon.getItem() instanceof CrossbowItem
                    && attacker.distanceTo(victim) <= 7.0f) {
                event.setAmount(event.getAmount() * 1.35f);
                attacker.setAbsorptionAmount(Math.max(attacker.getAbsorptionAmount(), 2.0f));
            }

            // #150 Faucheuse de Flammes Bleues : cercle de feu des âmes (3,5 blocs) + Wither II + 5 dégâts magiques
            if (atkMask.has(IDX_TOURBILLON_AME_ARDENTE) && !isProjectile && victim.level() instanceof ServerLevel sl150) {
                EnchantmentParticleFX.shockwaveRing(sl150, victim.getX(), victim.getY() + 0.2, victim.getZ(),
                        ParticleTypes.SOUL_FIRE_FLAME, 3.2, 18, 0.04);
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                for (Mob m : sl150.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(3.5))) {
                    if (!isFriendlySummon(m, attacker)) {
                        m.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                        if (m != victim) safeHurt(m, attacker.damageSources().magic(), 5.0f);
                    }
                }
            }

            // #151 Prisme de Ricochet : +35 % sur cible en Surbrillance + propage Surbrillance
            if (atkMask.has(IDX_RICOCHET_TRACANT) && isProjectile) {
                if (victim.hasEffect(MobEffects.GLOWING)) {
                    event.setAmount(event.getAmount() * 1.35f);
                }
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                if (victim.level() instanceof ServerLevel sl151) {
                    for (Mob m : sl151.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.0))) {
                        if (!isFriendlySummon(m, attacker)) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                    }
                }
            }

            // #152 Barrage d'Interdiction : carreaux -> Lenteur III + Faiblesse II (4 s) + élan coupé
            if (atkMask.has(IDX_SALVE_SUPPRESSION) && isProjectile && weapon.getItem() instanceof CrossbowItem) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                victim.setDeltaMovement(0.0, Math.min(0.0, victim.getDeltaMovement().y), 0.0);
                victim.hurtMarked = true;
            }

            // #155 Hache Fend-Orage : attaque à la hache -> +35 % dégâts électriques + répare 2 durabilité
            if (atkMask.has(IDX_ABATTEUR_OURAGAN) && !isProjectile && weapon.getItem() instanceof AxeItem) {
                event.setAmount(event.getAmount() * 1.35f);
                if (weapon.isDamageableItem() && weapon.getDamageValue() > 0) {
                    weapon.setDamageValue(Math.max(0, weapon.getDamageValue() - 2));
                }
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.ELECTRIC_SPARK, 12, 0.35, 0.05);
            }

            // #157 Plongeon du Rapace : attaque en chute -> +45 % dégâts + bourrasque
            if (atkMask.has(IDX_PIQUE_FAUCON_PLONGEANT) && !isProjectile && attacker.fallDistance > 0.25f) {
                event.setAmount(event.getAmount() * 1.45f);
                if (victim.level() instanceof ServerLevel sl157) {
                    EnchantmentParticleFX.shockwaveRing(sl157, victim.getX(), victim.getY() + 0.2, victim.getZ(),
                            ParticleTypes.GUST_EMITTER_SMALL, 3.0, 6, 0.04);
                }
            }

            // #160 Pyromancien de Mêlée : en feu -> +35 % dégâts + 0,5 cœur soigné par coup
            if (atkMask.has(IDX_BERSERKER_FLAMMES) && !isProjectile && attacker.isOnFire()) {
                event.setAmount(event.getAmount() * 1.35f);
                attacker.heal(1.0f);
            }

            // #162 Brise-Pavois Nordique : après blocage au bouclier, prochaine hache -> +45 % + étourdissement
            if (atkMask.has(IDX_BRISE_GARDE_TONNERRE) && !isProjectile && weapon.getItem() instanceof AxeItem
                    && now < atkSt.shieldAxeRiposteUntil) {
                atkSt.shieldAxeRiposteUntil = 0L;
                event.setAmount(event.getAmount() * 1.45f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 3));
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CRIT, 14, 0.35, 0.07);
            }

            // #164 Murmure Longue-Portée : >= 14 blocs -> +40 % dégâts à l'arc
            if (atkMask.has(IDX_TIREUR_FANTOME_MURMURE) && isProjectile && attacker.distanceTo(victim) >= 14.0f) {
                event.setAmount(event.getAmount() * 1.40f);
            }

            // #165 Traqueur d'Agonie : cible <= 40 % PV -> +35 % dégâts + Célérité II (4 s)
            if (atkMask.has(IDX_JUGEMENT_AGONIE) && victim.getHealth() <= victim.getMaxHealth() * 0.40f) {
                event.setAmount(event.getAmount() * 1.35f);
                attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 80, 1, true, false));
            }

            // #167 Harpon de Vapeur Ardente : coup au Trident -> explosion de vapeur +6 dégâts de feu
            if (atkMask.has(IDX_TRIDENT_EMPALEMENT_SOLAIRE)
                    && (weapon.is(Items.TRIDENT) || event.getSource().getDirectEntity() instanceof ThrownTrident)
                    && victim.level() instanceof ServerLevel sl167) {
                EnchantmentParticleFX.shockwaveRing(sl167, victim.getX(), victim.getY() + 0.3, victim.getZ(),
                        ParticleTypes.CLOUD, 2.8, 16, 0.04);
                for (Mob m : sl167.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(3.2))) {
                    if (!isFriendlySummon(m, attacker)) {
                        m.setRemainingFireTicks(Math.max(m.getRemainingFireTicks(), 80));
                        safeHurt(m, attacker.damageSources().onFire(), 6.0f);
                    }
                }
            }

            // #168 Alchimiste Sanguin : restaure 1 faim + Régénération I (3 s)
            if (atkMask.has(IDX_PACTE_SANG_ECARLATE) && !isProjectile) {
                if (attacker instanceof Player pl168) pl168.getFoodData().eat(1, 0.2f);
                attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            }

            // #170 Katana Surcadencé : frappe critique/chute -> foudre en chaîne sur 4 ennemis (+7) + Célérité II
            if (atkMask.has(IDX_LAME_SURCHARGE_CRITIQUE) && !isProjectile
                    && (attacker.fallDistance > 0.1f || attacker.getRandom().nextInt(100) < 35)
                    && victim.level() instanceof ServerLevel sl170) {
                attacker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 70, 1, true, false));
                int arcs = 0;
                for (Mob m : sl170.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.8))) {
                    if (!isFriendlySummon(m, attacker) && arcs < 4) {
                        arcs++;
                        safeHurt(m, attacker.damageSources().lightningBolt(), 7.0f);
                        EnchantmentParticleFX.burstOnEntity(m, ParticleTypes.ELECTRIC_SPARK, 10, 0.3, 0.05);
                    }
                }
            }

            // #171 Rayon Dimensionnel : flèches spectrales -> +45 % dégâts + Surbrillance
            if (atkMask.has(IDX_SNIPER_SPECTRAL_ABSOLU) && isProjectile) {
                event.setAmount(event.getAmount() * 1.45f);
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            }

            // #172 Paladin de l'Aube Pure : +65 % + soin contre morts-vivants, +35 % contre vivants
            if (atkMask.has(IDX_INQUISITEUR_PURIFICATEUR) && !isProjectile) {
                if (victim.getType().is(EntityTypeTags.UNDEAD)) {
                    event.setAmount(event.getAmount() * 1.65f);
                    attacker.heal(2.0f);
                    EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.GOLD_DIVINE, 12, 0.35, 0.04);
                } else {
                    event.setAmount(event.getAmount() * 1.35f);
                }
            }

            // #174 Seigneur du Ressac Abyssal : dans l'eau ou sous la pluie -> +40 % dégâts
            if (atkMask.has(IDX_KRAKEN_RESSAC_MAUDIT) && attacker.isInWaterOrRain()) {
                event.setAmount(event.getAmount() * 1.40f);
            }

            // #175 Seigneur Nocturne du Vide : la nuit -> +35 % dégâts + 20 % vol de vie
            if (atkMask.has(IDX_PREDATEUR_VOIDLING_NOCTURNE) && attacker.level().isNight()) {
                event.setAmount(event.getAmount() * 1.35f);
                attacker.heal(Math.min(8.0f, event.getAmount() * 0.20f));
            }

            // #176 Mirage du Vent Vif : riposte chargée -> +45 % dégâts
            if (atkMask.has(IDX_DANSEUR_MIRAGE_ZEPHYR) && !isProjectile && now < atkSt.mirageRiposteUntil) {
                atkSt.mirageRiposteUntil = 0L;
                event.setAmount(event.getAmount() * 1.45f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CLOUD, 12, 0.35, 0.05);
            }

            // #177 Monolithe Inébranlable : coup de hache -> onde sismique projetant les ennemis en l'air
            if (atkMask.has(IDX_COLOSSE_RACINES_PIERRE) && !isProjectile && weapon.getItem() instanceof AxeItem
                    && victim.level() instanceof ServerLevel sl177) {
                EnchantmentParticleFX.shockwaveRing(sl177, victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        EnchantmentParticleFX.EARTH_OCHRE, 3.6, 18, 0.05);
                for (Mob m : sl177.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(3.8))) {
                    if (!isFriendlySummon(m, attacker)) {
                        if (m != victim) safeHurt(m, attacker.damageSources().mobAttack(attacker), 5.0f);
                        m.setDeltaMovement(m.getDeltaMovement().x, 0.50, m.getDeltaMovement().z);
                        m.hurtMarked = true;
                    }
                }
            }

            // #179 Tempête de Mille Entailles : toutes les 4 frappes -> entaille circulaire (65 % zone) + soin 1,5 cœur
            if (atkMask.has(IDX_MAITRE_LAMES_COMBO) && !isProjectile && victim.level() instanceof ServerLevel sl179) {
                atkSt.thousandCutCounter++;
                if (atkSt.thousandCutCounter >= 4) {
                    atkSt.thousandCutCounter = 0;
                    attacker.heal(3.0f);
                    float cleaveDmg = event.getAmount() * 0.65f;
                    EnchantmentParticleFX.slashArc(attacker, EnchantmentParticleFX.CRIMSON_BLOOD, ParticleTypes.SWEEP_ATTACK, 3.4, 16);
                    for (Mob m : sl179.getEntitiesOfClass(Mob.class, attacker.getBoundingBox().inflate(3.8))) {
                        if (m != victim && !isFriendlySummon(m, attacker)) {
                            safeHurt(m, attacker.damageSources().mobAttack(attacker), cleaveDmg);
                        }
                    }
                }
            }

            // #183 Seigneur de l'Effroi Sculk : cible affaiblie/ralentie/obscurcie -> +35 % dégâts + soin 0,5 cœur
            if (atkMask.has(IDX_TERREUR_ABYSSE_WARDEN)
                    && (victim.hasEffect(MobEffects.WEAKNESS) || victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || victim.hasEffect(MobEffects.DARKNESS))) {
                event.setAmount(event.getAmount() * 1.35f);
                attacker.heal(1.0f);
            }

            // #184 Pacte de la Faux Immortelle : frappe à la houe -> 3 cœurs d'Absorption
            if (atkMask.has(IDX_FAUCHEUR_SACRIFICE_IMMORTEL) && !isProjectile && weapon.getItem() instanceof HoeItem) {
                attacker.setAbsorptionAmount(Math.max(attacker.getAbsorptionAmount(), 6.0f));
            }

            // #185 Cataphractaire Foudroyant : sur monture -> foudre + +45 % dégâts
            if (atkMask.has(IDX_CAVALIER_TONNERRE_ROYAL) && attacker.isPassenger() && victim.level() instanceof ServerLevel sl185) {
                event.setAmount(event.getAmount() * 1.45f);
                spawnVisualLightning(sl185, victim.position());
            }

            // #187 Poing d'Adamantine : à mains nues -> +9 dégâts, Faiblesse II sur la cible et Résistance II (4 s)
            if (atkMask.has(IDX_BOXEUR_DIAMANT_TITAN) && !isProjectile && weapon.isEmpty()) {
                event.setAmount(event.getAmount() + 9.0f);
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 1, true, false));
            }

            // #188 Éveil de la Chimère Prismatique : +25 % à tous vos dégâts
            if (atkMask.has(IDX_CHIMERE_SEPT_COEURS)) {
                event.setAmount(event.getAmount() * 1.25f);
            }

            // #189 Bourreau des Colosses : hache -> +45 % dégâts + onde tellurique 4 blocs
            if (atkMask.has(IDX_EXECUTEUR_APOCALYPSE_HACHE) && !isProjectile && weapon.getItem() instanceof AxeItem
                    && victim.level() instanceof ServerLevel sl189) {
                event.setAmount(event.getAmount() * 1.45f);
                EnchantmentParticleFX.shockwaveRing(sl189, victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        EnchantmentParticleFX.EARTH_OCHRE, 3.8, 20, 0.05);
                for (Mob m : sl189.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.0))) {
                    if (m != victim && !isFriendlySummon(m, attacker)) {
                        safeHurt(m, attacker.damageSources().mobAttack(attacker), 6.0f);
                    }
                }
            }

            // #192 Archonte de la Nécropole : houe -> +45 % dégâts d'âme
            if (atkMask.has(IDX_FAUCHEUSE_NECROPOLE_ROYALE) && !isProjectile && weapon.getItem() instanceof HoeItem) {
                event.setAmount(event.getAmount() * 1.45f);
            }

            // #194 Lame Fantôme de l'Outre-Monde : Invisible ou accroupi -> +100 % dégâts
            if (atkMask.has(IDX_FANTOME_LAME_DIMENSIONNELLE) && !isProjectile
                    && (attacker.hasEffect(MobEffects.INVISIBILITY) || attacker.isShiftKeyDown())) {
                event.setAmount(event.getAmount() * 2.00f);
            }

            // #195 Archonte des Marées Célestes : Trident -> foudre + +40 % dégâts + soin 1,5 cœur
            if (atkMask.has(IDX_ARCHONTE_MAREES_CELESTES)
                    && (weapon.is(Items.TRIDENT) || event.getSource().getDirectEntity() instanceof ThrownTrident)
                    && victim.level() instanceof ServerLevel sl195) {
                event.setAmount(event.getAmount() * 1.40f);
                attacker.heal(3.0f);
                spawnVisualLightning(sl195, victim.position());
            }

            // #196 Banquet du Titan Invincible : faim >= 14 -> +35 % dégâts mêlée
            if (atkMask.has(IDX_JUGGERNAUT_GOURMAND_COLOSSAL) && !isProjectile
                    && attacker instanceof Player ap196 && ap196.getFoodData().getFoodLevel() >= 14) {
                event.setAmount(event.getAmount() * 1.35f);
            }

            // #197 Canonnier d'Apocalypse Solaire : carreaux d'arbalète -> +40 % dégâts explosifs
            if (atkMask.has(IDX_ARTILLEUR_METEOR_ECARLATE) && isProjectile && weapon.getItem() instanceof CrossbowItem) {
                event.setAmount(event.getAmount() * 1.40f);
            }

            // #198 Héraut des Malédictions Brisées : +60 % dégâts toutes dimensions + 18 % vol de vie
            if (atkMask.has(IDX_CHAMPION_MAUDIT_TRANSCENDANT)) {
                event.setAmount(event.getAmount() * 1.60f);
                attacker.heal(Math.min(8.0f, event.getAmount() * 0.18f));
            }

            // #200 Dieu-Bourreau du Cataclysme : hache -> +50 % dégâts + séisme volcanique
            if (atkMask.has(IDX_DIEU_HACHE_CATACLYSME) && !isProjectile && weapon.getItem() instanceof AxeItem
                    && victim.level() instanceof ServerLevel sl200) {
                event.setAmount(event.getAmount() * 1.50f);
                EnchantmentParticleFX.shockwaveRing(sl200, victim.getX(), victim.getY() + 0.15, victim.getZ(),
                        ParticleTypes.LAVA, 4.4, 22, 0.06);
                for (Mob m : sl200.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.5))) {
                    if (!isFriendlySummon(m, attacker)) {
                        m.setRemainingFireTicks(Math.max(m.getRemainingFireTicks(), 100));
                        if (m != victim) safeHurt(m, attacker.damageSources().onFire(), 8.0f);
                    }
                }
            }

            // #202 Empereur de la Faux du Néant : houe -> fauchage 4 blocs (+55 %) + exécution sous 25 % PV
            if (atkMask.has(IDX_EMPEREUR_FAUX_NEANT) && !isProjectile && weapon.getItem() instanceof HoeItem
                    && victim.level() instanceof ServerLevel sl202) {
                event.setAmount(event.getAmount() * 1.55f);
                float scytheAoE = event.getAmount() * 0.50f;
                for (Mob m : sl202.getEntitiesOfClass(Mob.class, attacker.getBoundingBox().inflate(4.0))) {
                    if (m != victim && !isFriendlySummon(m, attacker)) {
                        safeHurt(m, attacker.damageSources().magic(), scytheAoE);
                    }
                }
                if (!(victim instanceof Player) && victim.getMaxHealth() < 150.0f
                        && (victim.getHealth() - event.getAmount()) <= victim.getMaxHealth() * 0.25f) {
                    event.setAmount(victim.getHealth() + 10.0f);
                }
            }

            // #204 Tireur Céleste Omniscient : +55 % dégâts à l'arc
            if (atkMask.has(IDX_SNIPER_CELESTE_OMNISCIENT) && isProjectile
                    && (weapon.getItem() instanceof BowItem || event.getSource().getDirectEntity() instanceof AbstractArrow)) {
                event.setAmount(event.getAmount() * 1.55f);
            }

            // #205 Bastion Immortel de l'Égide : +80 % de riposte après un coup reçu
            if (atkMask.has(IDX_CITADELLE_IMMORTELLE_EGIDE) && now < atkSt.aegisRetaliationUntil) {
                atkSt.aegisRetaliationUntil = 0L;
                event.setAmount(event.getAmount() * 1.80f);
            }

            // #206 Avatar de la Chimère Primordiale : +40 % dégâts
            if (atkMask.has(IDX_AVATAR_CHIMERE_PRIMORDIALE)) {
                event.setAmount(event.getAmount() * 1.40f);
            }

            // #207 Seigneur du Fléau Émeraude : +50 % contre les cibles empoisonnées ou flétries
            if (atkMask.has(IDX_SEIGNEUR_FLEAU_TOXIQUE)
                    && (victim.hasEffect(MobEffects.POISON) || victim.hasEffect(MobEffects.WITHER))) {
                event.setAmount(event.getAmount() * 1.50f);
            }


            // ==================== T333 : ATTAQUE DES COMBOS DE BAS GRADE (#211-#310) ====================
            if (atkMask.has(IDX_FANTASSIN_TRANCHANT_VIF) && !isProjectile && attacker.isSprinting()) {
                event.setAmount(event.getAmount() + 1.5f);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, 0, true, false));
            }
            if (atkMask.has(IDX_RECRUE_BALAYAGE_AGILE) && !isProjectile && attacker.fallDistance > 0.2f
                    && victim.level() instanceof ServerLevel sl211) {
                for (Mob m : sl211.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(2.6))) {
                    if (m != victim && !isFriendlySummon(m, attacker)) safeHurt(m, attacker.damageSources().mobAttack(attacker), 2.0f);
                }
            }
            if (atkMask.has(IDX_ESCARMOUCHEUR_PAS_LATERAL) && !isProjectile && Math.abs(attacker.xxa) > 0.25f) {
                event.setAmount(event.getAmount() * 1.20f);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            }
            if (atkMask.has(IDX_DUELLISTE_CRITIQUE_LEGER) && !isProjectile && attacker.fallDistance > 0.2f) {
                event.setAmount(event.getAmount() + 2.0f);
                attacker.fallDistance *= 0.5f;
            }
            if (atkMask.has(IDX_HALLEBARDIER_ANCRAGE_FER) && !isProjectile && attacker.isShiftKeyDown() && attacker.onGround()) {
                event.setAmount(event.getAmount() + 2.5f);
                Vec3 kb216 = victim.position().subtract(attacker.position());
                if (kb216.lengthSqr() > 0.01) victim.setDeltaMovement(kb216.normalize().scale(0.55).add(0, 0.18, 0));
            }
            if (atkMask.has(IDX_FANTASSIN_TROIS_LAMES) && !isProjectile) {
                int c217 = attacker.getPersistentData().getInt("slb_c217_cnt") + 1;
                if (c217 >= 3) {
                    c217 = 0;
                    event.setAmount(event.getAmount() + 3.0f);
                    attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 0, true, false));
                }
                attacker.getPersistentData().putInt("slb_c217_cnt", c217);
            }
            if (atkMask.has(IDX_FAUCHEUR_SOUPIR_AFFAIBLISSANT) && !isProjectile && victim.hasEffect(MobEffects.WEAKNESS)) {
                event.setAmount(event.getAmount() + 1.5f);
                if (attacker instanceof Player pl218) pl218.getFoodData().eat(1, 0.2f);
            }
            if (atkMask.has(IDX_PATROUILLEUR_CLAIRIERE) && !isProjectile
                    && attacker.level().getMaxLocalRawBrightness(attacker.blockPosition()) >= 12) {
                event.setAmount(event.getAmount() + 2.0f);
            }
            if (atkMask.has(IDX_TRANCHEUR_PELLE_ROBUSTE) && !isProjectile && weapon.getItem() instanceof ShovelItem) {
                event.setAmount(event.getAmount() + 2.0f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
            if (atkMask.has(IDX_PAYSAN_FOURCHE_DEFENSE) && !isProjectile && weapon.getItem() instanceof HoeItem) {
                event.setAmount(event.getAmount() + 2.5f);
                Vec3 kb237 = victim.position().subtract(attacker.position());
                if (kb237.lengthSqr() > 0.01) victim.setDeltaMovement(kb237.normalize().scale(0.45).add(0, 0.15, 0));
            }
            if (atkMask.has(IDX_ARCHER_PERCE_ARMURE_LEGER) && isProjectile && attacker.isShiftKeyDown() && attacker.onGround()) {
                event.setAmount(event.getAmount() * 1.20f);
            }
            if (atkMask.has(IDX_HARPONNEUR_ABYSSES_DOMPTE) && attacker.isInWater()) {
                event.setAmount(event.getAmount() + 3.0f);
            }
            if (atkMask.has(IDX_CORSAIRE_GUE_SANGLANT) && !isProjectile && attacker.isInWaterOrRain()
                    && now >= attacker.getPersistentData().getLong("slb_c259_cd")) {
                attacker.getPersistentData().putLong("slb_c259_cd", now + 40L);
                attacker.heal(1.0f);
            }
            if (atkMask.has(IDX_CAVALIER_LANCE_FRANCHE) && !isProjectile && attacker.isPassenger()) {
                event.setAmount(event.getAmount() + 2.5f);
            }
            if (atkMask.has(IDX_PUGILISTE_PAS_VIF) && !isProjectile && weapon.isEmpty()) {
                event.setAmount(event.getAmount() + 1.5f);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            }
            if (atkMask.has(IDX_COGNEUR_ELANCE) && !isProjectile && weapon.isEmpty() && (attacker.isSprinting() || !attacker.onGround())) {
                event.setAmount(event.getAmount() + 2.5f);
                Vec3 kb271 = victim.position().subtract(attacker.position());
                if (kb271.lengthSqr() > 0.01) victim.setDeltaMovement(kb271.normalize().scale(0.55).add(0, 0.2, 0));
            }
            if (atkMask.has(IDX_MOINE_SOUFFLE_CLAIR) && !isProjectile) {
                int c273 = attacker.getPersistentData().getInt("slb_c273_cnt") + 1;
                if (c273 >= 4) {
                    c273 = 0;
                    attacker.heal(2.0f);
                    attacker.removeEffect(MobEffects.DIG_SLOWDOWN);
                }
                attacker.getPersistentData().putInt("slb_c273_cnt", c273);
            }
            if (atkMask.has(IDX_BAGARREUR_BIEN_NOURRI) && !isProjectile && attacker instanceof Player pl274
                    && pl274.getFoodData().getFoodLevel() >= 16) {
                event.setAmount(event.getAmount() + 2.0f);
                pl274.getFoodData().eat(0, 0.1f);
            }
            if (atkMask.has(IDX_BOXEUR_ESQUIVE_CROISEE) && !isProjectile && weapon.isEmpty()
                    && now < attacker.getPersistentData().getLong("slb_c276_charge")) {
                attacker.getPersistentData().remove("slb_c276_charge");
                event.setAmount(event.getAmount() + 2.0f);
            }
            if (atkMask.has(IDX_CHAMPION_TAVERNE_TROIS_POINGS) && !isProjectile && weapon.isEmpty()) {
                event.setAmount(event.getAmount() + 3.0f);
                int c277 = attacker.getPersistentData().getInt("slb_c277_cnt") + 1;
                if (c277 >= 3) {
                    c277 = 0;
                    attacker.heal(1.0f);
                }
                attacker.getPersistentData().putInt("slb_c277_cnt", c277);
            }
            if (atkMask.has(IDX_BRISEUR_ELAN_BRUTAL) && !isProjectile && attacker.isSprinting()) {
                event.setAmount(event.getAmount() + 2.5f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
            if (atkMask.has(IDX_SURVIVANT_DERNIER_SOUFFLE_BAS) && !isProjectile
                    && attacker.getHealth() <= attacker.getMaxHealth() * 0.40f) {
                event.setAmount(event.getAmount() + 2.5f);
                if (now >= attacker.getPersistentData().getLong("slb_c279_cd")) {
                    attacker.getPersistentData().putLong("slb_c279_cd", now + 50L);
                    attacker.heal(1.0f);
                }
            }
            if (atkMask.has(IDX_FERRAILLEUR_BOUCLIER_RECYCLE) && !isProjectile) {
                ItemStack oh283 = attacker.getOffhandItem();
                if ((weapon.isDamageableItem() && weapon.getDamageValue() >= weapon.getMaxDamage() * 0.65f)
                        || (oh283.isDamageableItem() && oh283.getDamageValue() >= oh283.getMaxDamage() * 0.65f)) {
                    event.setAmount(event.getAmount() + 2.0f);
                }
            }
            if (atkMask.has(IDX_LANCIER_BOUCLIER_BALAYANT) && !isProjectile
                    && now < attacker.getPersistentData().getLong("slb_c284_riposte")) {
                attacker.getPersistentData().remove("slb_c284_riposte");
                event.setAmount(event.getAmount() + 2.5f);
            }
            if (atkMask.has(IDX_PHALANGE_TROIS_FERS) && !isProjectile
                    && now < attacker.getPersistentData().getLong("slb_c287_riposte")) {
                attacker.getPersistentData().remove("slb_c287_riposte");
                event.setAmount(event.getAmount() + 3.0f);
            }
            if (atkMask.has(IDX_FRACASSEUR_TRAUMATISME_BOUCLIER) && !isProjectile
                    && (victim.hasEffect(MobEffects.WEAKNESS) || victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))) {
                event.setAmount(event.getAmount() + 2.5f);
            }
            if (atkMask.has(IDX_LAME_SANCTUAIRE_MAGMA) && !isProjectile) {
                event.setAmount(event.getAmount() + 1.0f);
                victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 60));
            }
            if (atkMask.has(IDX_TRAQUEUR_VENIN_CICATRICE) && !isProjectile && victim.hasEffect(MobEffects.POISON)) {
                event.setAmount(event.getAmount() + 2.0f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
            if (atkMask.has(IDX_LIMIER_SENS_AIGUISE) && !isProjectile) {
                event.setAmount(event.getAmount() + 1.5f);
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0));
            }
            if (atkMask.has(IDX_CHASSEUR_PLAIE_AFFAIBLISSANTE) && !isProjectile) {
                if (victim.getHealth() < victim.getMaxHealth()) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 0, true, false));
                }
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0));
            }
            if (atkMask.has(IDX_PISTEUR_FLAIR_SENTIER) && !isProjectile && victim.getHealth() <= victim.getMaxHealth() * 0.50f) {
                event.setAmount(event.getAmount() + 2.5f);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
            }
            if (atkMask.has(IDX_CHIRURGIEN_VENIN_ECARLATE) && !isProjectile) {
                if (victim.hasEffect(MobEffects.POISON)) {
                    event.setAmount(event.getAmount() + 2.5f);
                    if (now >= attacker.getPersistentData().getLong("slb_c297_cd")) {
                        attacker.getPersistentData().putLong("slb_c297_cd", now + 60L);
                        attacker.heal(1.0f);
                    }
                }
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            }
            if (atkMask.has(IDX_VEILLEUR_FLAMME_CLAIRE) && !isProjectile && victim.isOnFire()) {
                event.setAmount(event.getAmount() + 2.5f);
            }
            if (atkMask.has(IDX_ECORCHEUR_BALAYAGE_LARGE) && !isProjectile && victim.level() instanceof ServerLevel sl299A) {
                int near299 = sl299A.getEntitiesOfClass(Mob.class, attacker.getBoundingBox().inflate(4.0), m -> m.isAlive() && !isFriendlySummon(m, attacker)).size();
                if (near299 >= 2) event.setAmount(event.getAmount() + 2.0f);
            }
            if (atkMask.has(IDX_REDEMPTION_LAME_EMOUSSEE) && !isProjectile) {
                event.setAmount(event.getAmount() + 3.0f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            }
            if (atkMask.has(IDX_PENITENT_FERRAILLE_HEROIQUE) && !isProjectile) {
                event.setAmount(event.getAmount() + 3.0f);
            }

            // #209 Élu Astral de la Chance Infinie : +35 % dégâts critiques stellaires + nova curatrice
            if (atkMask.has(IDX_TRANSCENDANCE_CHANCE_INFINIE) && !isProjectile
                    && (attacker.fallDistance > 0.1f || attacker.getRandom().nextInt(100) < 35)
                    && victim.level() instanceof ServerLevel sl209) {
                event.setAmount(event.getAmount() * 1.35f);
                attacker.heal(3.0f);
                EnchantmentParticleFX.shockwaveRing(sl209, victim.getX(), victim.getY() + 0.3, victim.getZ(),
                        ParticleTypes.END_ROD, 3.6, 18, 0.04);
                for (Mob m : sl209.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(3.8))) {
                    if (m != victim && !isFriendlySummon(m, attacker)) {
                        safeHurt(m, attacker.damageSources().magic(), 5.0f);
                    }
                }
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

            // ---- T332-COMBOS-210 : BOUCLIER (#118, #136, #162, #191) ----
            PlayerComboState stDef = stateOf(defender);

            // #118 Fantassin Trempé : soin 1 cœur (4 s recharge) + repousse l'assaillant
            if (mask.has(IDX_GARDE_ENDURCI)) {
                if (now >= stDef.shieldHealCd) {
                    stDef.shieldHealCd = now + 80L;
                    defender.heal(2.0f);
                }
                if (attacker instanceof LivingEntity liv118) {
                    Vec3 away = liv118.position().subtract(defender.position());
                    if (away.lengthSqr() > 0.01) {
                        liv118.setDeltaMovement(away.normalize().scale(0.55).add(0, 0.20, 0));
                        liv118.hurtMarked = true;
                    }
                }
            }

            // #136 Rempart de Ronces : charge l'armure d'épines pendant 5 s
            if (mask.has(IDX_PROVOCATEUR_EPINEUX)) {
                stDef.thornsShieldChargeUntil = now + 100L;
            }

            // #162 Brise-Pavois Nordique : charge la prochaine frappe à la hache (+45 % + étourdissement)
            if (mask.has(IDX_BRISE_GARDE_TONNERRE)) {
                stDef.shieldAxeRiposteUntil = now + 100L;
            }


            // ==================== T333 : BOUCLIER DES COMBOS DE BAS GRADE (#211-#310) ====================
            if (mask.has(IDX_GARDE_BOUCLIER_ARDENT)) {
                if (attacker instanceof LivingEntity liv212) liv212.setRemainingFireTicks(Math.max(liv212.getRemainingFireTicks(), 60));
                if (now >= defender.getPersistentData().getLong("slb_c212_cd")) {
                    defender.getPersistentData().putLong("slb_c212_cd", now + 60L);
                    defender.heal(1.0f);
                }
            }
            if (mask.has(IDX_GARDE_COTE_BOUCLIER) && defender.isInWaterOrRain()) {
                defender.heal(1.0f);
                if (attacker instanceof LivingEntity liv256) {
                    Vec3 away = liv256.position().subtract(defender.position());
                    if (away.lengthSqr() > 0.01) liv256.setDeltaMovement(away.normalize().scale(0.60).add(0, 0.20, 0));
                }
            }
            if (mask.has(IDX_PORTEBOUCLIER_PIEDS_COLLES)) {
                defender.setDeltaMovement(0, Math.min(0, defender.getDeltaMovement().y), 0);
                defender.hurtMarked = true;
                if (attacker instanceof LivingEntity liv280) {
                    Vec3 away = liv280.position().subtract(defender.position());
                    if (away.lengthSqr() > 0.01) liv280.setDeltaMovement(away.normalize().scale(0.65).add(0, 0.20, 0));
                }
            }
            if (mask.has(IDX_GARDE_CHOC_ECRASANT) && attacker instanceof LivingEntity liv281 && !event.getDamageSource().is(DamageTypeTags.IS_PROJECTILE)) {
                safeHurt(liv281, defender.damageSources().thorns(defender), 2.5f);
                liv281.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
            }
            if (mask.has(IDX_SENTINELLE_RACCOMMODAGE)) {
                ItemStack sh282 = defender.getUseItem();
                if (sh282.isDamageableItem() && sh282.getDamageValue() > 0) sh282.setDamageValue(Math.max(0, sh282.getDamageValue() - 2));
                ItemStack ch282 = defender.getItemBySlot(EquipmentSlot.CHEST);
                if (ch282.isDamageableItem() && ch282.getDamageValue() > 0) ch282.setDamageValue(Math.max(0, ch282.getDamageValue() - 1));
            }
            if (mask.has(IDX_LANCIER_BOUCLIER_BALAYANT)) {
                defender.getPersistentData().putLong("slb_c284_riposte", now + 60L);
            }
            if (mask.has(IDX_GARDIEN_FLAMME_PAVOIS)) {
                defender.clearFire();
                if (now >= defender.getPersistentData().getLong("slb_c286_cd")) {
                    defender.getPersistentData().putLong("slb_c286_cd", now + 80L);
                    defender.setAbsorptionAmount(Math.max(defender.getAbsorptionAmount(), 2.0f));
                }
            }
            if (mask.has(IDX_PHALANGE_TROIS_FERS)) {
                defender.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
                defender.getPersistentData().putLong("slb_c287_riposte", now + 60L);
            }
            if (mask.has(IDX_VEILLEURS_MURAILLES_CLAIRES) && event.getDamageSource().is(DamageTypeTags.IS_PROJECTILE)) {
                defender.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (attacker instanceof LivingEntity liv288) safeHurt(liv288, defender.damageSources().thorns(defender), 2.0f);
            }

            // #191 Égide Solaire Inviolable : renvoie 150 % des dégâts bloqués, soigne 2 cœurs et Résistance III (4 s)
            if (mask.has(IDX_CITADELLE_MIROIR_DIVIN)) {
                defender.heal(4.0f);
                defender.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 2, true, false));
                if (attacker instanceof LivingEntity liv191) {
                    float reflected = Math.max(4.0f, event.getBlockedDamage() * 1.50f);
                    safeHurt(liv191, defender.damageSources().thorns(defender), reflected);
                    EnchantmentParticleFX.burstOnEntity(liv191, EnchantmentParticleFX.GOLD_DIVINE, 14, 0.35, 0.05);
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

            // ---- T332-COMBOS-210 : ÉLIMINATIONS (#124, #128, #134, #156, #184, #189, #192, #194, #202) ----
            ItemStack kWeapon = killer.getMainHandItem();

            // #124 Chasseur de Primes : 2 points de faim + 25 % Émeraude
            if (mask.has(IDX_CHASSEUR_PILLARDS) && dead instanceof Mob) {
                if (killer instanceof Player pl124) pl124.getFoodData().eat(2, 0.4f);
                if (dead.level() instanceof ServerLevel sl124 && killer.getRandom().nextInt(100) < 25) {
                    sl124.addFreshEntity(new ItemEntity(sl124, dead.getX(), dead.getY() + 0.5, dead.getZ(),
                            new ItemStack(Items.EMERALD)));
                }
            }

            // #128 Bûcheron Frénétique : élimination à la hache -> Célérité II (5 s)
            if (mask.has(IDX_FENDEUR_BRUTAL) && kWeapon.getItem() instanceof AxeItem) {
                killer.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, true, false));
            }

            // #134 Moissonneur Prospère : élimination à la houe -> +10 XP + répare 3 durabilité
            if (mask.has(IDX_FAUCHEUR_XP) && kWeapon.getItem() instanceof HoeItem) {
                if (killer instanceof Player pl134) pl134.giveExperiencePoints(10);
                if (kWeapon.isDamageableItem() && kWeapon.getDamageValue() > 0) {
                    kWeapon.setDamageValue(Math.max(0, kWeapon.getDamageValue() - 3));
                }
            }

            // #156 Moisson Écarlate : élimination à la houe -> +3 cœurs d'Absorption + Vitesse II (5 s)
            if (mask.has(IDX_FAUCHEUSE_SANGLANTE) && kWeapon.getItem() instanceof HoeItem) {
                killer.setAbsorptionAmount(Math.max(killer.getAbsorptionAmount(), 6.0f));
                killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, true, false));
            }

            // #184 Pacte de la Faux Immortelle : soigne 2 cœurs sur élimination
            if (mask.has(IDX_FAUCHEUR_SACRIFICE_IMMORTEL)) {
                killer.heal(4.0f);
            }

            // #189 Bourreau des Colosses : élimination à la hache -> +2,5 cœurs soignés
            if (mask.has(IDX_EXECUTEUR_APOCALYPSE_HACHE) && kWeapon.getItem() instanceof AxeItem) {
                killer.heal(5.0f);
            }

            // #192 Archonte de la Nécropole : élimination à la houe -> déflagration nécrotique + soin 2,5 cœurs
            if (mask.has(IDX_FAUCHEUSE_NECROPOLE_ROYALE) && kWeapon.getItem() instanceof HoeItem) {
                killer.heal(5.0f);
                if (dead.level() instanceof ServerLevel sl192) {
                    EnchantmentParticleFX.shockwaveRing(sl192, dead.getX(), dead.getY() + 0.3, dead.getZ(),
                            ParticleTypes.SCULK_SOUL, 3.5, 20, 0.05);
                    for (Mob m : sl192.getEntitiesOfClass(Mob.class, dead.getBoundingBox().inflate(3.8))) {
                        if (m != dead && !isFriendlySummon(m, killer)) {
                            safeHurt(m, killer.damageSources().magic(), 7.0f);
                        }
                    }
                }
            }

            // #194 Lame Fantôme de l'Outre-Monde : réapplique Invisibilité 4 s sur élimination
            if (mask.has(IDX_FANTOME_LAME_DIMENSIONNELLE)) {
                killer.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, true, false));
            }


            // ==================== T333 : ÉLIMINATION DES COMBOS DE BAS GRADE (#211-#310) ====================
            if (mask.has(IDX_BRETTEUR_LAME_PROMETTEUSE) && killer instanceof Player pl214) {
                if (kWeapon.isDamageableItem() && kWeapon.getDamageValue() > 0) {
                    kWeapon.setDamageValue(Math.max(0, kWeapon.getDamageValue() - 3));
                }
                pl214.giveExperiencePoints(3);
            }
            if (mask.has(IDX_BUVEUR_TRANSFUSION_VORACE) && killer instanceof Player pl295) {
                pl295.heal(2.0f);
                pl295.getFoodData().eat(2, 0.3f);
            }
            if (mask.has(IDX_PENITENT_FERRAILLE_HEROIQUE)) {
                killer.heal(2.0f);
            }

            // #202 Empereur de la Faux du Néant : élimination à la houe -> soigne 3 cœurs
            if (mask.has(IDX_EMPEREUR_FAUX_NEANT) && kWeapon.getItem() instanceof HoeItem) {
                killer.heal(6.0f);
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

                // #144 Voyageur Astral : lancer une Perle de l'Ender -> Vitesse II + 2 cœurs d'Absorption (6 s)
                if (proj instanceof ThrownEnderpearl && mask.has(IDX_MARCHEUR_END_PERLE)) {
                    shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1, true, false));
                    shooter.setAbsorptionAmount(Math.max(shooter.getAbsorptionAmount(), 4.0f));
                }

                // #146 Harponneur Foudroyant : lancer / tracter la ligne -> Vitesse II + arc électrique proche
                if (proj instanceof FishingHook && mask.has(IDX_HARPON_TONNERRE) && event.getLevel() instanceof ServerLevel sl146) {
                    shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
                    for (Mob m : sl146.getEntitiesOfClass(Mob.class, shooter.getBoundingBox().inflate(3.0))) {
                        if (!isFriendlySummon(m, shooter)) {
                            safeHurt(m, shooter.damageSources().lightningBolt(), 4.0f);
                            EnchantmentParticleFX.burstOnEntity(m, ParticleTypes.ELECTRIC_SPARK, 8, 0.25, 0.04);
                        }
                    }
                }

                // #093 Avatar du Zéphyr : bourrasque perforante à chaque lancer de Trident !
                if (proj instanceof ThrownTrident tri) {
                    if (mask.has(IDX_AVATAR_ZEPHYR) && event.getLevel() instanceof ServerLevel slZ) {
                        tri.setDeltaMovement(tri.getDeltaMovement().scale(1.30));
                        EnchantmentParticleFX.shockwaveRing(slZ, shooter.getX(), shooter.getY() + 1.0, shooter.getZ(),
                                ParticleTypes.GUST_EMITTER_SMALL, 1.5, 4, 0.03);
                    }
                    if (mask.has(IDX_ARCHONTE_MAREES_CELESTES)) {
                        tri.setDeltaMovement(tri.getDeltaMovement().scale(1.40));
                    }
                    if (mask.has(IDX_TRIDENT_RETOUR_EXPLOSIF)) {
                        tri.getPersistentData().putBoolean("slb_combo75_explosive", true);
                    }
                    if (mask.has(IDX_PLONGEUR_CORAIL)) {
                        tri.getPersistentData().putBoolean("slb_c125_coral", true);
                    }
                    if (mask.has(IDX_VORTEX_ELECTRIQUE_MARIN)) {
                        tri.getPersistentData().putBoolean("slb_c153_vortex", true);
                    }
                    if (mask.has(IDX_TRIDENT_EMPALEMENT_SOLAIRE)) {
                        tri.getPersistentData().putBoolean("slb_c167_steam", true);
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
                    if (mask.has(IDX_ECLAIREUR_FLAMBEAU)) {
                        arrow.getPersistentData().putBoolean("slb_c113_lantern", true);
                        shooter.removeEffect(MobEffects.DARKNESS);
                    }
                    if (mask.has(IDX_ARCHER_OASIS)) arrow.getPersistentData().putBoolean("slb_c131_oasis", true);
                    if (mask.has(IDX_ARCHER_DESERTIQUE)) arrow.getPersistentData().putBoolean("slb_c132_sirocco", true);
                    if (mask.has(IDX_ASSASSIN_ECHANGE)) arrow.getPersistentData().putBoolean("slb_c159_swap", true);
                    if (mask.has(IDX_SNIPER_SPECTRAL_ABSOLU)) arrow.setNoGravity(true);
                    if (mask.has(IDX_ARTILLEUR_CHAOS_ALCHIMIQUE)) arrow.getPersistentData().putBoolean("slb_c178_chaos", true);
                    if (mask.has(IDX_VOLTIGEUR_PLONGEON_METEORE)) arrow.getPersistentData().putBoolean("slb_c181_meteor", true);
                    if (mask.has(IDX_ARCHER_DISTORSION_VIDE)) arrow.getPersistentData().putBoolean("slb_c186_rift", true);
                    if (mask.has(IDX_TEMPETE_ASTRALE_INFINIE)) arrow.getPersistentData().putBoolean("slb_c190_starstorm", true);
                    if (mask.has(IDX_ARTILLEUR_METEOR_ECARLATE)) arrow.getPersistentData().putBoolean("slb_c197_solar", true);
                    if (mask.has(IDX_ARCHER_RONCE_AGILE)) arrow.getPersistentData().putBoolean("slb_c240", true);
                    if (mask.has(IDX_ARCHER_JOUET_FARCEUR)) arrow.getPersistentData().putBoolean("slb_c241", true);
                    if (mask.has(IDX_ARBALETRIER_TORCHE_VEILLEUR)) {
                        arrow.getPersistentData().putBoolean("slb_c242", true);
                        shooter.removeEffect(MobEffects.DARKNESS);
                        shooter.removeEffect(MobEffects.BLINDNESS);
                    }
                    if (mask.has(IDX_VOLTIGEUR_TIR_RAPPROCHE)) arrow.getPersistentData().putBoolean("slb_c243", true);
                    if (mask.has(IDX_ARCHER_SALVE_LEGERE) && !shooter.onGround()) {
                        arrow.getPersistentData().putBoolean("slb_c244", true);
                        shooter.fallDistance *= 0.5f;
                    }
                    if (mask.has(IDX_ARCHER_FLECHE_SECHE_SABLE)) arrow.getPersistentData().putBoolean("slb_c246", true);
                    if (mask.has(IDX_ARCHER_GRAPPIN_AEROFREIN)) arrow.getPersistentData().putBoolean("slb_c247", true);
                    if (mask.has(IDX_ARCHER_BRAISE_COEUR)) arrow.getPersistentData().putBoolean("slb_c248", true);
                    if (mask.has(IDX_CAPITAINE_ARCHERS_MILICE)) arrow.getPersistentData().putBoolean("slb_c249", true);
                    if (mask.has(IDX_REDEMPTION_CORDE_FRAGILE)) arrow.getPersistentData().putBoolean("slb_c306", true);
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

            // #075 / #125 / #153 / #167 : Impacts de Trident
            if (proj instanceof ThrownTrident tri) {
                var triTag = tri.getPersistentData();
                if (triTag.getBoolean("slb_combo75_explosive")) {
                    triTag.remove("slb_combo75_explosive");
                    EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z, ParticleTypes.EXPLOSION, 2.5, 6, 0.02);
                    sl.playSound(null, BlockPos.containing(hit), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.65f, 1.3f);
                    for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(3.5))) {
                        if (!isFriendlySummon(m, shooter)) safeHurt(m, shooter.damageSources().trident(tri, shooter), 6.5f);
                    }
                }
                if (triTag.getBoolean("slb_c125_coral")) {
                    triTag.remove("slb_c125_coral");
                    if (shooter.isInWaterOrRain()) {
                        shooter.heal(2.0f);
                        shooter.setAirSupply(shooter.getMaxAirSupply());
                    }
                }
                if (triTag.getBoolean("slb_c153_vortex")) {
                    triTag.remove("slb_c153_vortex");
                    EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z, ParticleTypes.NAUTILUS, 3.6, 20, 0.05);
                    for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(4.0))) {
                        if (!isFriendlySummon(m, shooter)) {
                            Vec3 pull = hit.subtract(m.position());
                            if (pull.lengthSqr() > 0.05) {
                                m.setDeltaMovement(pull.normalize().scale(0.55).add(0, 0.18, 0));
                                m.hurtMarked = true;
                            }
                            safeHurt(m, shooter.damageSources().lightningBolt(), 6.0f);
                        }
                    }
                }
                if (triTag.getBoolean("slb_c167_steam")) {
                    triTag.remove("slb_c167_steam");
                    EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z, ParticleTypes.CLOUD, 2.8, 16, 0.04);
                    for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(3.2))) {
                        if (!isFriendlySummon(m, shooter)) {
                            m.setRemainingFireTicks(Math.max(m.getRemainingFireTicks(), 80));
                            safeHurt(m, shooter.damageSources().onFire(), 6.0f);
                        }
                    }
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
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 20, () -> {
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
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 20 * b, () -> {
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
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 30, () -> {
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
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 10 + i * 8, () -> {
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
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + pulse * 20, () -> {
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

            // ---- T332-COMBOS-210 : IMPACTS DE FLÈCHES & CARREAUX (#113..#197) ----

            // #113 Veilleur Porte-Lanterne : Surbrillance 5 s sur les monstres à 6 blocs
            if (tag.getBoolean("slb_c113_lantern")) {
                tag.remove("slb_c113_lantern");
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(6.0))) {
                    if (!isFriendlySummon(m, shooter)) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                }
            }

            // #131 Gardien de l'Oasis : soigne 2 cœurs et éteint les flammes autour de l'impact
            if (tag.getBoolean("slb_c131_oasis")) {
                tag.remove("slb_c131_oasis");
                for (Player pl131 : sl.getEntitiesOfClass(Player.class, new AABB(hit, hit).inflate(4.5))) {
                    pl131.heal(4.0f);
                    pl131.clearFire();
                }
                if (shooter.position().distanceToSqr(hit) <= 20.25) {
                    shooter.heal(4.0f);
                    shooter.clearFire();
                }
            }

            // #132 Mirage du Sirocco : Force I (6 s) + 2 cœurs d'Absorption
            if (tag.getBoolean("slb_c132_sirocco")) {
                tag.remove("slb_c132_sirocco");
                shooter.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, true, false));
                shooter.setAbsorptionAmount(Math.max(shooter.getAbsorptionAmount(), 4.0f));
            }

            // #159 Permutation Fantôme : Cécité 3 s sur la cible + Invisibilité 4 s & Force I
            if (tag.getBoolean("slb_c159_swap") && event.getRayTraceResult() instanceof EntityHitResult ehr159
                    && ehr159.getEntity() instanceof LivingEntity liv159) {
                tag.remove("slb_c159_swap");
                liv159.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                shooter.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, true, false));
                shooter.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 0, true, false));
            }

            // #178 Bombardier du Chaos : brume alchimique 4 blocs (Wither II, Faiblesse II, Lenteur II — 5 s)
            if (tag.getBoolean("slb_c178_chaos")) {
                tag.remove("slb_c178_chaos");
                EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z, ParticleTypes.DRAGON_BREATH, 3.6, 20, 0.03);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(4.0))) {
                    if (!isFriendlySummon(m, shooter)) {
                        m.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                        m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                    }
                }
            }

            // #181 Comète à Grappin : charge les bottes pendant 6 s
            if (tag.getBoolean("slb_c181_meteor")) {
                tag.remove("slb_c181_meteor");
                stateOf(shooter).grappleMeteorUntil = sl.getGameTime() + 120L;
            }

            // #186 Archer des Failles Astrales : faille gravitationnelle (5 dégâts magiques) + 6 s anti-chute
            if (tag.getBoolean("slb_c186_rift")) {
                tag.remove("slb_c186_rift");
                stateOf(shooter).riftFallImmuneUntil = sl.getGameTime() + 120L;
                EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z,
                        EnchantmentParticleFX.SHADOW_VIOLET, 3.2, 18, 0.04);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(3.5))) {
                    if (!isFriendlySummon(m, shooter)) {
                        safeHurt(m, shooter.damageSources().magic(), 5.0f);
                    }
                }
            }

            // #190 Tempête d'Étoiles Foudroyantes : foudroie 4,5 blocs (+8 dégâts) + Vitesse III (5 s)
            if (tag.getBoolean("slb_c190_starstorm")) {
                tag.remove("slb_c190_starstorm");
                spawnVisualLightning(sl, hit);
                shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2, true, false));
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(4.5))) {
                    if (!isFriendlySummon(m, shooter)) {
                        safeHurt(m, shooter.damageSources().lightningBolt(), 8.0f);
                    }
                }
            }


            // ==================== T333 : IMPACT PROJECTILES DE BAS GRADE (#241-#307) ====================
            LivingEntity hitEntity = (event.getRayTraceResult() instanceof EntityHitResult ehrT333
                    && ehrT333.getEntity() instanceof LivingEntity leT333) ? leT333 : null;
            if (tag.getBoolean("slb_c240") && hitEntity != null) {
                tag.remove("slb_c240");
                shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
            }
            if (tag.getBoolean("slb_c241") && hitEntity != null) {
                tag.remove("slb_c241");
                shooter.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 0, true, false));
                Vec3 kb = hitEntity.position().subtract(shooter.position());
                if (kb.lengthSqr() > 0.01) hitEntity.setDeltaMovement(kb.normalize().scale(0.65).add(0, 0.22, 0));
            }
            if (tag.getBoolean("slb_c242") && hitEntity != null) {
                tag.remove("slb_c242");
                hitEntity.setRemainingFireTicks(Math.max(hitEntity.getRemainingFireTicks(), 60));
            }
            if (tag.getBoolean("slb_c243") && hitEntity != null && shooter.distanceToSqr(hitEntity) <= 64.0) {
                tag.remove("slb_c243");
                safeHurt(hitEntity, shooter.damageSources().mobAttack(shooter), 2.5f);
                shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 35, 0, true, false));
            }
            if (tag.getBoolean("slb_c244") && hitEntity != null) {
                tag.remove("slb_c244");
                safeHurt(hitEntity, shooter.damageSources().mobAttack(shooter), 1.5f);
            }
            if (tag.getBoolean("slb_c246") && hitEntity != null) {
                tag.remove("slb_c246");
                hitEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                if (hitEntity.isInWaterOrRain()) safeHurt(hitEntity, shooter.damageSources().mobAttack(shooter), 2.0f);
            }
            if (tag.getBoolean("slb_c247")) {
                tag.remove("slb_c247");
                shooter.getPersistentData().putLong("slb_c247_fall_safe", sl.getGameTime() + 80L);
                shooter.fallDistance = 0.0f;
            }
            if (tag.getBoolean("slb_c248") && hitEntity != null && hitEntity.isOnFire()) {
                tag.remove("slb_c248");
                safeHurt(hitEntity, shooter.damageSources().onFire(), 1.5f);
                shooter.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 80, 0, true, false));
            }
            if (tag.getBoolean("slb_c249") && hitEntity != null) {
                tag.remove("slb_c249");
                safeHurt(hitEntity, shooter.damageSources().mobAttack(shooter), 2.0f);
                hitEntity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                if (shooter instanceof Player pl249) pl249.giveExperiencePoints(2);
            }
            if (tag.getBoolean("slb_c306") && hitEntity != null) {
                tag.remove("slb_c306");
                safeHurt(hitEntity, shooter.damageSources().mobAttack(shooter), 2.0f);
                ItemStack bow306 = shooter.getMainHandItem();
                if (bow306.getItem() instanceof BowItem && bow306.isDamageableItem() && bow306.getDamageValue() > 0) {
                    bow306.setDamageValue(Math.max(0, bow306.getDamageValue() - 3));
                }
            }

            // #197 Canonnier d'Apocalypse Solaire : impact explosif ardent
            if (tag.getBoolean("slb_c197_solar")) {
                tag.remove("slb_c197_solar");
                EnchantmentParticleFX.shockwaveRing(sl, hit.x, hit.y + 0.2, hit.z, ParticleTypes.FLAME, 3.2, 18, 0.05);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(hit, hit).inflate(3.5))) {
                    if (!isFriendlySummon(m, shooter)) {
                        m.setRemainingFireTicks(Math.max(m.getRemainingFireTicks(), 100));
                        safeHurt(m, shooter.damageSources().onFire(), 6.0f);
                    }
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
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 2, () -> {
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

            // ---- T332-COMBOS-210 : MINAGE, BOIS, PELLE & RÉCOLTE (#111..#203) ----
            ItemStack heldTool = player.getMainHandItem();

            // #111 Forestier Infatigable : bûche -> 1 faim + Vitesse I (5 s)
            if (mask.has(IDX_BUCHERON_INFATIGABLE) && state.is(BlockTags.LOGS)) {
                player.getFoodData().eat(1, 0.3f);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, false));
            }

            // #112 Étayeur de Galerie : miner sous terre -> Vision Nocturne temporaire
            if (mask.has(IDX_MINEUR_STABILISATEUR) && !sl.canSeeSky(pos)) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
            }

            // #115 Paysan Rassasié : récolte mature -> 1 point de faim + saturation
            if (mask.has(IDX_MOISSONNEUR_MATINAL)
                    && state.getBlock() instanceof CropBlock crop115 && crop115.isMaxAge(state)) {
                player.getFoodData().eat(1, 0.4f);
            }

            // #116 Orpailleur des Rivières : sable/gravier/boue -> 18 % pépites d'or ou fer brut
            if (mask.has(IDX_TERRASSIER_ORPAILLEUR)
                    && (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.MUD) || state.is(Blocks.CLAY))
                    && sl.random.nextInt(100) < 18) {
                ItemStack panLoot = sl.random.nextBoolean()
                        ? new ItemStack(Items.GOLD_NUGGET, 2)
                        : new ItemStack(Items.RAW_IRON, 1);
                sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, panLoot));
            }

            // #129 Tailleur de Gemmes : minerai -> +3 XP + répare 1 durabilité
            if (mask.has(IDX_VEINE_PRUDENTE) && state.is(Tags.Blocks.ORES)) {
                player.giveExperiencePoints(3);
                if (heldTool.isDamageableItem() && heldTool.getDamageValue() > 0) {
                    heldTool.setDamageValue(Math.max(0, heldTool.getDamageValue() - 1));
                }
            }

            // #135 Sapeur de Galerie : creuser à la pelle -> Célérité II + Résistance I (5 s) + recul proche
            if (mask.has(IDX_TAUPE_FOREUSE) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                    && heldTool.getItem() instanceof ShovelItem) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, true, false));
                for (Mob m : sl.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(3.0))) {
                    if (!isFriendlySummon(m, player)) {
                        Vec3 away = m.position().subtract(player.position());
                        if (away.lengthSqr() > 0.01) {
                            m.setDeltaMovement(away.normalize().scale(0.50).add(0, 0.18, 0));
                            m.hurtMarked = true;
                        }
                    }
                }
            }

            // #149 Sondeur des Profondeurs : minerai -> Surbrillance monstres 12 blocs (6 s) + Célérité II
            if (mask.has(IDX_PROSPECTEUR_RADAR) && state.is(Tags.Blocks.ORES)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, true, false));
                for (Mob m : sl.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0))) {
                    if (!isFriendlySummon(m, player)) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
                }
            }

            // #154 Haut-Fourneau Souterrain : minerai -> +6 XP, Régénération I (4 s) + 30 % lingot fer/or
            if (mask.has(IDX_FORGE_FILON_ARDENT) && state.is(Tags.Blocks.ORES)) {
                player.giveExperiencePoints(6);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, false));
                if (sl.random.nextInt(100) < 30) {
                    ItemStack ingot154 = state.is(BlockTags.GOLD_ORES) ? new ItemStack(Items.GOLD_INGOT) : new ItemStack(Items.IRON_INGOT);
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ingot154));
                }
            }

            // #180 Foreur Géomancien : minerai -> répare 2 durabilité, Résistance II + 35 % Or brut/Émeraude/Diamant
            if (mask.has(IDX_FOREUR_COEUR_MONDE) && state.is(Tags.Blocks.ORES)) {
                if (heldTool.isDamageableItem() && heldTool.getDamageValue() > 0) {
                    heldTool.setDamageValue(Math.max(0, heldTool.getDamageValue() - 2));
                }
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1, true, false));
                if (sl.random.nextInt(100) < 35) {
                    int r180 = sl.random.nextInt(10);
                    ItemStack bonus180 = r180 < 6 ? new ItemStack(Items.RAW_GOLD)
                            : r180 < 9 ? new ItemStack(Items.EMERALD) : new ItemStack(Items.DIAMOND);
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, bonus180));
                }
            }

            // #193 Forgeron du Noyau Stellaire : minerai -> 40 % lingot fondu bonus
            if (mask.has(IDX_SEIGNEUR_FORGES_STELLAIRES) && state.is(Tags.Blocks.ORES) && sl.random.nextInt(100) < 40) {
                ItemStack ingot193 = state.is(BlockTags.GOLD_ORES) ? new ItemStack(Items.GOLD_INGOT) : new ItemStack(Items.IRON_INGOT);
                sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ingot193));
            }


            // ==================== T333 : MINAGE & AGRICULTURE DE BAS GRADE (#221-#304) ====================
            boolean isOre333 = state.is(Tags.Blocks.ORES);
            boolean isCrop333 = state.getBlock() instanceof net.minecraft.world.level.block.CropBlock cb333 && cb333.isMaxAge(state);
            String blkDesc333 = state.getBlock().getDescriptionId();

            if (mask.has(IDX_MINEUR_CADENCE_AIMANTEE) && isOre333) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, 0, true, false));
                for (ItemEntity ie : sl.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.5))) {
                    ie.moveTo(player.getX(), player.getY() + 0.3, player.getZ());
                }
            }
            if (mask.has(IDX_ALLUMEUR_GALERIE_SILEX) && (isOre333 || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) && sl.random.nextInt(100) < 20) {
                sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(sl.random.nextBoolean() ? Items.FLINT : Items.COAL)));
            }
            if (mask.has(IDX_TERRASSIER_PELLE_AGILE)
                    && (blkDesc333.contains("dirt") || blkDesc333.contains("sand") || blkDesc333.contains("gravel"))) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
                if (sl.random.nextInt(100) < 15) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.IRON_NUGGET)));
                }
            }
            if (mask.has(IDX_MINEUR_XP_CLAIRVOYANT) && isOre333) {
                player.giveExperiencePoints(3);
                ItemStack helm226 = player.getItemBySlot(EquipmentSlot.HEAD);
                if (helm226.isDamageableItem() && helm226.getDamageValue() > 0) helm226.setDamageValue(helm226.getDamageValue() - 1);
            }
            if (mask.has(IDX_FOREUR_ELAN_CONTINU)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 80, 0, true, false));
                if (sl.random.nextInt(100) < 10) player.getFoodData().eat(0, 0.25f);
            }
            if (mask.has(IDX_PROSPECTEUR_TROIS_OUTILS) && isOre333) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, 1, true, false));
                player.giveExperiencePoints(4);
                if (sl.random.nextInt(100) < 15) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            new ItemStack(sl.random.nextBoolean() ? Items.RAW_IRON : Items.RAW_COPPER)));
                }
            }
            if (mask.has(IDX_EXCAVATEUR_RECYCLEUR)) {
                int cnt229 = player.getPersistentData().getInt("slb_c229_cnt") + 1;
                if (cnt229 >= 8) {
                    cnt229 = 0;
                    ItemStack mh229 = player.getMainHandItem();
                    if (mh229.isDamageableItem() && mh229.getDamageValue() > 0) mh229.setDamageValue(Math.max(0, mh229.getDamageValue() - 2));
                    ItemStack oh229 = player.getOffhandItem();
                    if (oh229.isDamageableItem() && oh229.getDamageValue() > 0) oh229.setDamageValue(Math.max(0, oh229.getDamageValue() - 2));
                }
                player.getPersistentData().putInt("slb_c229_cnt", cnt229);
            }
            if (mask.has(IDX_SARCLEUR_PAS_RAPIDE) && (isCrop333 || isWeedPlant(state))) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
                if (sl.random.nextInt(100) < 15) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.WHEAT_SEEDS)));
                }
            }
            if (mask.has(IDX_SEMEUR_REPLANT_AIMANTE) && isCrop333) {
                player.giveExperiencePoints(1);
                if (sl.random.nextInt(100) < 20) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.WHEAT)));
                }
            }
            if (mask.has(IDX_MARAICHER_COMPOST_GOURMAND) && isCrop333) {
                player.getFoodData().eat(1, 0.15f);
                if (sl.random.nextInt(100) < 20) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.BONE_MEAL)));
                }
            }
            if (mask.has(IDX_MOISSONNEUR_PANIER_PLEIN) && isCrop333 && sl.random.nextInt(100) < 25) {
                sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(sl.random.nextBoolean() ? Items.CARROT : Items.POTATO)));
            }
            if (mask.has(IDX_VIGNERON_ASSIMILATION) && isCrop333) {
                player.getFoodData().eat(0, 0.3f);
            }
            if (mask.has(IDX_MAITRE_FERMIER_TROIS_CHAMPS) && isCrop333 && sl.isDay()) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 80, 0, true, false));
                if (sl.random.nextInt(100) < 35) {
                    sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.WHEAT, 2)));
                }
            }
            if (mask.has(IDX_REDEMPTION_PORTEE_COURTE) && isOre333) {
                player.giveExperiencePoints(2);
            }

            // #203 Forgeron Divin des Reliques : minerai -> 25 % gemme bonus (Diamant ou Émeraude)
            if (mask.has(IDX_FORGERON_DIVIN_RELIQUE) && state.is(Tags.Blocks.ORES) && sl.random.nextInt(100) < 25) {
                ItemStack gem203 = new ItemStack(sl.random.nextBoolean() ? Items.DIAMOND : Items.EMERALD);
                sl.addFreshEntity(new ItemEntity(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, gem203));
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

            // ---- T332-COMBOS-210 : PÊCHE (#114, #133, #146, #163, #201) ----

            // #114 Pêcheur Érudit : +8 XP + répare 2 durabilité sur la canne
            if (mask.has(IDX_PECHEUR_EXPERIMENTE)) {
                player.giveExperiencePoints(8);
                ItemStack rod = player.getMainHandItem();
                if (rod.isDamageableItem() && rod.getDamageValue() > 0) {
                    rod.setDamageValue(Math.max(0, rod.getDamageValue() - 2));
                }
            }

            // #133 Pêcheur des Volcans : Résistance au Feu (15 s) + 35 % Crème de Magma ou Pépites d'Or
            if (mask.has(IDX_PECHEUR_MAGMA)) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0, true, false));
                if (player.getRandom().nextInt(100) < 35 && player.level() instanceof ServerLevel sl133) {
                    ItemStack magmaLoot = player.getRandom().nextBoolean()
                            ? new ItemStack(Items.MAGMA_CREAM, 1)
                            : new ItemStack(Items.GOLD_NUGGET, 3);
                    sl133.addFreshEntity(new ItemEntity(sl133, player.getX(), player.getY() + 0.6, player.getZ(), magmaLoot));
                }
            }

            // #146 Harponneur Foudroyant : Vitesse II + électrocute les ennemis à 3 blocs
            if (mask.has(IDX_HARPON_TONNERRE) && player.level() instanceof ServerLevel sl146f) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, true, false));
                for (Mob m : sl146f.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(3.0))) {
                    if (!isFriendlySummon(m, player)) {
                        safeHurt(m, player.damageSources().lightningBolt(), 4.0f);
                    }
                }
            }

            // #163 Trésorier Englouti : Chance II (10 s) + 30 % Émeraude, Améthyste ou Prismarine
            if (mask.has(IDX_DRAGUEUR_TRESORS_ABYSSES)) {
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 200, 1, true, false));
                if (player.getRandom().nextInt(100) < 30 && player.level() instanceof ServerLevel sl163) {
                    int r163 = player.getRandom().nextInt(3);
                    ItemStack treasure = r163 == 0 ? new ItemStack(Items.EMERALD)
                            : r163 == 1 ? new ItemStack(Items.AMETHYST_SHARD, 2)
                            : new ItemStack(Items.PRISMARINE_CRYSTALS, 2);
                    sl163.addFreshEntity(new ItemEntity(sl163, player.getX(), player.getY() + 0.6, player.getZ(), treasure));
                }
            }


            // ==================== T333 : PÊCHE DE BAS GRADE (#251-#306) ====================
            if (mask.has(IDX_PECHEUR_RIVE_PAISIBLE)) {
                player.getFoodData().eat(2, 0.25f);
                player.giveExperiencePoints(3);
            }
            if (mask.has(IDX_CUISINIER_FEU_CAMP) && player.level() instanceof ServerLevel sl251) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
                if (sl251.random.nextInt(100) < 25) {
                    sl251.addFreshEntity(new ItemEntity(sl251, player.getX(), player.getY() + 0.5, player.getZ(), new ItemStack(Items.COOKED_COD)));
                }
            }
            if (mask.has(IDX_PECHEUR_AIMANT_EPAVES) && player.level() instanceof ServerLevel sl255 && sl255.random.nextInt(100) < 25) {
                ItemStack haul = switch (sl255.random.nextInt(3)) {
                    case 0 -> new ItemStack(Items.IRON_NUGGET, 3);
                    case 1 -> new ItemStack(Items.GOLD_NUGGET, 2);
                    default -> new ItemStack(Items.PRISMARINE_SHARD, 1);
                };
                sl255.addFreshEntity(new ItemEntity(sl255, player.getX(), player.getY() + 0.5, player.getZ(), haul));
            }
            if (mask.has(IDX_LOUP_MER_TROIS_MARES) && player.level() instanceof ServerLevel sl258) {
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 300, 0, true, false));
                player.giveExperiencePoints(5);
                if (sl258.random.nextInt(100) < 20) {
                    sl258.addFreshEntity(new ItemEntity(sl258, player.getX(), player.getY() + 0.5, player.getZ(),
                            new ItemStack(sl258.random.nextBoolean() ? Items.EMERALD : Items.NAUTILUS_SHELL)));
                }
            }
            if (mask.has(IDX_REDEMPTION_LIGNE_BRULANTE) && player.level() instanceof ServerLevel sl304) {
                player.clearFire();
                if (sl304.random.nextInt(100) < 30) {
                    sl304.addFreshEntity(new ItemEntity(sl304, player.getX(), player.getY() + 0.5, player.getZ(),
                            new ItemStack(sl304.random.nextBoolean() ? Items.GOLD_NUGGET : Items.MAGMA_CREAM)));
                }
            }
            if (mask.has(IDX_REDEMPTION_FAUSSE_TOUCHE) && player.level() instanceof ServerLevel sl305) {
                player.giveExperiencePoints(5);
                if (!event.getDrops().isEmpty() && sl305.random.nextInt(100) < 25) {
                    sl305.addFreshEntity(new ItemEntity(sl305, player.getX(), player.getY() + 0.5, player.getZ(), event.getDrops().get(0).copy()));
                }
            }

            // #201 Grand Amiral des Sept Mers : double chaque butin pêché !
            if (mask.has(IDX_GRAND_AMIRAL_SEPT_MERS) && player.level() instanceof ServerLevel sl201) {
                List<ItemStack> drops = event.getDrops();
                if (!drops.isEmpty()) {
                    sl201.addFreshEntity(new ItemEntity(sl201, player.getX(), player.getY() + 0.6, player.getZ(), drops.get(0).copy()));
                }
            }
        } catch (Throwable t) {
            guard("onItemFished", t);
        }
    }


    @SubscribeEvent
    public static void onFoodFinish(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        try {
            if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
            if (event.getItem().get(net.minecraft.core.component.DataComponents.FOOD) == null) return;
            ComboMask mask = EnchantmentComboRegistry.getActiveMask(player);
            if (mask.isEmpty()) return;
            if (mask.has(IDX_VIGNERON_ASSIMILATION)) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
            }
            if (mask.has(IDX_COUREUR_RAVITAILLEUR)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false));
                player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 2.0f));
            }
        } catch (Throwable t) {
            guard("onFoodFinish", t);
        }
    }

    // =========================================================================
    // 8. COMMANDE EN JEU /dlbcombos & /dlbcombo (Paginé 7 par 7 avec flèches cliquables)
    // =========================================================================
    private static final int COMBOS_PER_PAGE = 7;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildComboCommandTree("dlbcombos"));
        event.getDispatcher().register(buildComboCommandTree("dlbcombo"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> buildComboCommandTree(String rootLiteral) {
        return Commands.literal(rootLiteral)
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    ComboMask mask = EnchantmentComboRegistry.computeRawMask(sp);
                    int activeCount = mask.bitCount();
                    sp.sendSystemMessage(Component.translatable(
                            "command.deep_lucky_block.combos.active_header", activeCount, COMBOS.size())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    if (activeCount == 0) {
                        sp.sendSystemMessage(Component.translatable("command.deep_lucky_block.combos.none_active")
                                .withStyle(ChatFormatting.GRAY));
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
                .then(Commands.literal("list")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> sendComboPage(
                                ctx.getSource().getPlayerOrException(),
                                COMBOS,
                                1,
                                "/" + rootLiteral + " list ",
                                "/" + rootLiteral + " give ",
                                Component.translatable("command.deep_lucky_block.combos.codex_header", COMBOS.size())
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 999))
                                .executes(ctx -> sendComboPage(
                                        ctx.getSource().getPlayerOrException(),
                                        COMBOS,
                                        IntegerArgumentType.getInteger(ctx, "page"),
                                        "/" + rootLiteral + " list ",
                                        "/" + rootLiteral + " give ",
                                        Component.translatable("command.deep_lucky_block.combos.codex_header", COMBOS.size())
                                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)))))
                .then(Commands.literal("tier")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("palier", StringArgumentType.word())
                                .executes(ctx -> sendTierComboPage(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "palier"),
                                        1,
                                        rootLiteral))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, 999))
                                        .executes(ctx -> sendTierComboPage(
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "palier"),
                                                IntegerArgumentType.getInteger(ctx, "page"),
                                                rootLiteral)))))
                .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, COMBOS.size())).executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            DeepEnchant.rememberServerAccess(sp.serverLevel().registryAccess());
                            int num = IntegerArgumentType.getInteger(ctx, "numero");
                            ComboDef c = COMBOS.get(num - 1);
                            Map<EquipmentSlot, ItemStack> kitBySlot = new LinkedHashMap<>();
                            for (PieceRequirement req : c.pieces()) {
                                ItemStack item = kitBySlot.computeIfAbsent(req.slot(), s -> {
                                    ItemStack st = new ItemStack(req.sampleItem());
                                    st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                                            Component.literal(c.tier().name() + " " + req.sampleItem().getDescription().getString())
                                                    .withStyle(net.minecraft.network.chat.Style.EMPTY
                                                            .withColor(net.minecraft.network.chat.TextColor.fromRgb(c.tier().rgb()))
                                                            .withBold(true)));
                                    net.minecraft.nbt.CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(st);
                                    tag.putInt("dlbGlint", 0xFF000000 | (c.tier().rgb() & 0xFFFFFF));
                                    deepluckyblock.util.DeepNbt.commit(st, tag);
                                    deepluckyblock.util.DeepNbt.markLuckyBlockOrigin(st);
                                    return st;
                                });
                                Enchantment ench = DeepEnchant.byId("deep_lucky_block:" + req.primaryPath());
                                if (ench != null) {
                                    int maxMod = Math.max(1, CustomEnchantment.getMaxLevelMod(ench));
                                    int giveLvl = Math.max(req.minLevel(), Math.min(5, maxMod));
                                    DeepEnchant.setLevel(item, ench, giveLvl);
                                }
                            }
                            for (Map.Entry<EquipmentSlot, ItemStack> entry : kitBySlot.entrySet()) {
                                EquipmentSlot slot = entry.getKey();
                                ItemStack item = entry.getValue();
                                if (sp.getItemBySlot(slot).isEmpty()) {
                                    sp.setItemSlot(slot, item);
                                } else if (!sp.getInventory().add(item)) {
                                    sp.drop(item, false);
                                }
                            }
                            sp.sendSystemMessage(Component.translatable("command.deep_lucky_block.combos.kit_given", num)
                                    .withStyle(ChatFormatting.GREEN)
                                    .append(c.translatedName()).append(" ").append(c.tier().badge()));
                            return 1;
                        })));
    }

    private static int sendTierComboPage(ServerPlayer sp, String rawTier, int page, String rootLiteral) {
        ComboTier tier;
        try {
            tier = ComboTier.valueOf(rawTier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sp.sendSystemMessage(Component.translatable("command.deep_lucky_block.combos.invalid_tier")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        List<ComboDef> filtered = new ArrayList<>();
        for (ComboDef c : COMBOS) {
            if (c.tier() == tier) {
                filtered.add(c);
            }
        }
        return sendComboPage(
                sp,
                filtered,
                page,
                "/" + rootLiteral + " tier " + tier.name().toLowerCase(Locale.ROOT) + " ",
                "/" + rootLiteral + " give ",
                Component.translatable("command.deep_lucky_block.combos.tier_header", tier.badge())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static int sendComboPage(ServerPlayer sp, List<ComboDef> entries, int requestedPage,
                                     String pageCommandPrefix, String giveCommandPrefix,
                                     MutableComponent header) {
        if (entries.isEmpty()) {
            sp.sendSystemMessage(header);
            return 0;
        }
        int total = entries.size();
        int totalPages = Math.max(1, (total + COMBOS_PER_PAGE - 1) / COMBOS_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int fromIdx = (page - 1) * COMBOS_PER_PAGE;
        int toIdx = Math.min(total, fromIdx + COMBOS_PER_PAGE);

        ComboMask mask = EnchantmentComboRegistry.computeRawMask(sp);

        sp.sendSystemMessage(Component.literal("\u00a78\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac"));
        sp.sendSystemMessage(header.copy().append(Component.literal(
                " \u00a77(" + (fromIdx + 1) + "\u2013" + toIdx + " / " + total + ")")));

        for (int i = fromIdx; i < toIdx; i++) {
            ComboDef c = entries.get(i);
            int num = c.index() + 1;
            boolean on = mask.has(c.index());

            MutableComponent hoverBox = c.translatedName().copy().append(" ").append(c.tier().badge())
                    .append(Component.literal("\n"))
                    .append(c.translatedDesc().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n\n"))
                    .append(Component.translatable("command.deep_lucky_block.combos.give_hover", num));

            MutableComponent numTag = Component.literal((on ? "\u00a7a\u2726 " : "\u00a78\u2727 ") + "\u00a7e#" + num + " ")
                    .withStyle(st -> st
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, giveCommandPrefix + num))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBox)));

            MutableComponent line = numTag
                    .append(c.tier().badge()).append(" ")
                    .append(c.translatedName().copy().withStyle(st -> st
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, giveCommandPrefix + num))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBox))))
                    .append(Component.literal(" \u00a78["))
                    .append(EnchantmentComboRegistry.buildRequirementsSummary(c, sp))
                    .append(Component.literal("\u00a78]"));

            sp.sendSystemMessage(line);
        }

        // Barre de navigation cliquable en bas : [«] [◀]  Page X / Y  [▶] [»]
        MutableComponent navBar = Component.literal("\u00a78\u2500\u2500\u2500 ");
        navBar.append(buildNavArrow("[\u00ab]", page > 1, pageCommandPrefix + 1,
                ChatFormatting.GOLD,
                Component.translatable("command.deep_lucky_block.combos.first_hover")));
        navBar.append(Component.literal(" "));
        navBar.append(buildNavArrow("[\u25c0]", page > 1, pageCommandPrefix + (page - 1),
                ChatFormatting.YELLOW,
                Component.translatable("command.deep_lucky_block.combos.prev_hover", Math.max(1, page - 1))));
        navBar.append(Component.literal("  "));
        navBar.append(Component.translatable("command.deep_lucky_block.combos.page_info", page, totalPages)
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        navBar.append(Component.literal("  "));
        navBar.append(buildNavArrow("[\u25b6]", page < totalPages, pageCommandPrefix + (page + 1),
                ChatFormatting.YELLOW,
                Component.translatable("command.deep_lucky_block.combos.next_hover", Math.min(totalPages, page + 1))));
        navBar.append(Component.literal(" "));
        navBar.append(buildNavArrow("[\u00bb]", page < totalPages, pageCommandPrefix + totalPages,
                ChatFormatting.GOLD,
                Component.translatable("command.deep_lucky_block.combos.last_hover", totalPages)));
        navBar.append(Component.literal(" \u00a78\u2500\u2500\u2500"));

        sp.sendSystemMessage(navBar);
        return toIdx - fromIdx;
    }

    private static MutableComponent buildNavArrow(String symbol, boolean enabled, String command,
                                                  ChatFormatting activeColor, Component hoverText) {
        if (!enabled) {
            return Component.literal(symbol).withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.literal(symbol).withStyle(st -> st
                .withColor(activeColor)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
    }
}
