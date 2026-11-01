package deepluckyblock.enchantments;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.EnchantmentComboRegistry.ComboDef;
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
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static deepluckyblock.enchantments.EnchantmentComboRegistry.*;

/**
 * T308-COMBOS : Gestionnaire d'exécution des 30 Combos Uniques Multi-Pièces.
 *
 * Chaque combo possède sa propre mécanique dédiée, ses particules thématiques,
 * sa notification d'activation en barre d'action et son test rapide via /dlbcombos.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class EnchantmentComboHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> COMBO_HURT_GUARD = ThreadLocal.withInitial(() -> false);
    private static final Set<String> REPORTED_ERRORS = ConcurrentHashMap.newKeySet();

    private static final class PlayerComboState {
        long prevServerMask = 0L;
        double prevY = Double.NaN;
        boolean wasFallFlying = false;
        double lastFlightSpeed = 0.0;
        boolean wasSprinting = false;
        int thunderHitCounter = 0;
        int oreMinedCounter = 0;
        long lateralChargeUntil = 0L;
        long parryRiposteUntil = 0L;
        long phoenixNovaReadyAt = 0L;
        long grappleShieldUntil = 0L;
        long horizonProjectileImmuneUntil = 0L;
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
        long mask = EnchantmentComboRegistry.getActiveMask(player);
        if (mask == 0L) return;

        // Combo #6 : Arpenteur de l'Horizon (foot_placement + dash) -> tunnel supersonique aérien !
        if ((mask & (1L << C06_PONT_DIMENSIONNEL)) != 0L && !player.onGround() && player.isSprinting()) {
            Vec3 dm = player.getDeltaMovement();
            if (dm.horizontalDistanceSqr() > 0.04 && dm.horizontalDistanceSqr() < 0.85) {
                player.setDeltaMovement(dm.x * 1.06, Math.max(dm.y, 0.0), dm.z * 1.06);
            }
        }

        // Combo #22 : Bombardement d'Apocalypse (meteor + gale) -> Bullet-time en bandant l'arc dans les airs !
        if ((mask & (1L << C22_PLUIE_CATACLYSMIQUE)) != 0L && !player.onGround()
                && player.isUsingItem() && player.getUseItem().is(Items.BOW)) {
            Vec3 dm = player.getDeltaMovement();
            if (dm.y < -0.02) {
                player.setDeltaMovement(dm.x * 0.92, -0.02, dm.z * 0.92);
                player.fallDistance = 0.0F;
            }
        }

        // Combo #23 : Assaut Voltigeur (grapple + wall_runner) -> tir à l'arc sans pénalité contre un mur !
        if ((mask & (1L << C23_COMMANDO_GRAPPIN)) != 0L && !player.onGround()
                && player.isUsingItem() && UniversalEnchantmentHandler.isTouchingHorizontalWall(player)) {
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x, Math.max(dm.y, -0.01), dm.z);
            player.fallDistance = 0.0F;
        }
    }

    private static void handleServerComboTick(LivingEntity entity) {
        if (!(entity instanceof Player) && (entity.tickCount % 10 != 0)) return;
        long mask = EnchantmentComboRegistry.getActiveMask(entity);
        PlayerComboState st = stateOf(entity);
        long now = entity.level().getGameTime();

        // Notification de déblocage en temps réel pour les joueurs !
        if (entity instanceof ServerPlayer sp) {
            long newlyOn = mask & ~st.prevServerMask;
            if (newlyOn != 0L && sp.tickCount > 20) {
                for (ComboDef c : COMBOS) {
                    if ((newlyOn & c.bit()) != 0L) {
                        MutableComponent banner = TranslationFallback.translatableOrFallback(
                                "tooltip.deep_lucky_block.combo_activated", "\u2726 Synergie Activ\u00e9e : ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(c.translatedName());
                        sp.displayClientMessage(banner, true);
                        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.95f, 1.35f);
                        EnchantmentParticleFX.crownHalo(sp, dustOf(c.rgb(), 1.15f), 12);
                        break;
                    }
                }
            }
            st.prevServerMask = mask;
        }

        if (mask == 0L) {
            st.prevY = entity.getY();
            st.wasFallFlying = entity.isFallFlying();
            st.wasSprinting = entity.isSprinting();
            return;
        }

        ServerLevel sl = (entity.level() instanceof ServerLevel s) ? s : null;

        // Combo #3 : Enjambée Sismique du Colosse (giant_step/big_step + meteor_stomp)
        if ((mask & (1L << C03_COLOSSE_FRANCHISSEUR)) != 0L && !Double.isNaN(st.prevY)) {
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

        // Combo #4 : Glissade Cryogénique (slide + frost_trail)
        if ((mask & (1L << C04_PATINEUR_AZOTE)) != 0L && entity.isShiftKeyDown()
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

        // Combo #5 : Ailes de Séraphin (lightness/basic_lightness + radiant_ward)
        if ((mask & (1L << C05_ANGE_PLUME_CELESTE)) != 0L && !entity.onGround()
                && !entity.isShiftKeyDown() && entity.getDeltaMovement().y < 0.0) {
            if (entity.tickCount % 30 == 0) {
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
        }

        // Combo #6 : Arpenteur de l'Horizon (foot_placement + dash)
        if ((mask & (1L << C06_PONT_DIMENSIONNEL)) != 0L) {
            if (entity.isSprinting() && !st.wasSprinting && !entity.onGround()) {
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
        }

        // Combo #11 : Brise-Montagne (titans_grasp + earth_heart + stonebound_roots) -> ancrage anti-projection verticale
        if ((mask & (1L << C11_TITAN_TELLURIQUE)) != 0L && entity.hurtTime > 0 && entity.getDeltaMovement().y > 0.25) {
            Vec3 dm = entity.getDeltaMovement();
            entity.setDeltaMovement(dm.x * 0.4, 0.05, dm.z * 0.4);
            entity.hurtMarked = true;
        }

        // Combo #12 : Rançon du Dernier Souffle (berserk + adrenaline + last_stand_escape)
        if ((mask & (1L << C12_BERSERKER_IMMORTEL)) != 0L && entity.getHealth() <= entity.getMaxHealth() * 0.35f) {
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, 2, true, false));
            if (sl != null && entity.tickCount % 12 == 0) {
                EnchantmentParticleFX.spiralHelix(entity, EnchantmentParticleFX.CRIMSON_BLOOD, 0.72, 6);
            }
        }

        // Combo #13 : Riposte Supersonique (swift_strike + evasive_footwork) -> charge une fente en strafant !
        if ((mask & (1L << C13_DUELLISTE_ECLAIR)) != 0L) {
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

        // Combo #17 : Marcheur de Cendres (infernal_armor + magma_walker/blaze_walker + ember_core)
        if ((mask & (1L << C17_AVATAR_INFERNAL)) != 0L) {
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

        // Combo #18 : Souverain d'Atlantide (tidecaller/ocean_current + conduit_heart + buoy)
        if ((mask & (1L << C18_SEIGNEUR_ABYSSES)) != 0L && (entity.isInWaterOrRain())) {
            entity.setAirSupply(entity.getMaxAirSupply());
            if (entity.tickCount % 20 == 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 50, 1, true, false));
            }
        }

        // Combo #19 : Piqué Stratosphérique (rocket_blast/wind_rider + meteor_stomp)
        if ((mask & (1L << C19_FAUCON_OURAGAN)) != 0L) {
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

        // Combo #22 : Bullet-time serveur (meteor + gale)
        if ((mask & (1L << C22_PLUIE_CATACLYSMIQUE)) != 0L && !entity.onGround()
                && entity.isUsingItem() && entity.getUseItem().is(Items.BOW)) {
            Vec3 dm = entity.getDeltaMovement();
            if (dm.y < -0.02) {
                entity.setDeltaMovement(dm.x * 0.92, -0.02, dm.z * 0.92);
                entity.fallDistance = 0.0F;
            }
        }

        // Combo #24 : Salve du Faucon Infernale (Arbalète chargée -> déclenchement au tir détecté par transition charged)
        if ((mask & (1L << C24_ARTILLEUR_INQUISITEUR)) != 0L) {
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

        // Combo #30 : Relique Indélébile du Roi (Set Complet 4 Pièces : king + divine_protection + giant_step + cloud_walker)
        if ((mask & (1L << C30_RELIQUE_ROI_TRANSCENDANT)) != 0L) {
            if (entity.tickCount % 20 == 0) {
                entity.removeEffect(MobEffects.WITHER);
                entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                entity.removeEffect(MobEffects.LEVITATION);
                if (sl != null) {
                    EnchantmentParticleFX.crownHalo(entity, EnchantmentParticleFX.GOLD_DIVINE, 8);
                }
            }
        }

        st.prevY = entity.getY();
        st.wasFallFlying = entity.isFallFlying();
        st.wasSprinting = entity.isSprinting();
    }

    private static boolean isFriendlySummon(LivingEntity candidate, LivingEntity owner) {
        if (candidate == null || owner == null) return false;
        return candidate.getPersistentData().hasUUID("slb_summon_owner")
                && candidate.getPersistentData().getUUID("slb_summon_owner").equals(owner.getUUID());
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

            // --- A. Défense du porteur d'un combo ---
            long defMask = EnchantmentComboRegistry.getActiveMask(victim);
            if (defMask != 0L) {
                PlayerComboState defSt = stateOf(victim);

                // Combo #6 : Immunité aux projectiles pendant le tunnel de vent aérien !
                if ((defMask & (1L << C06_PONT_DIMENSIONNEL)) != 0L
                        && now < defSt.horizonProjectileImmuneUntil
                        && event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                    event.setCanceled(true);
                    return;
                }

                // Combo #19 : Zéro dégât de chute / mur en Élytres !
                if ((defMask & (1L << C19_FAUCON_OURAGAN)) != 0L
                        && ("fall".equals(event.getSource().getMsgId()) || "flyIntoWall".equals(event.getSource().getMsgId()))) {
                    event.setCanceled(true);
                    return;
                }

                // Combo #23 : Bouclier cinétique post-grappin (0 dégât de chute)
                if ((defMask & (1L << C23_COMMANDO_GRAPPIN)) != 0L
                        && now < defSt.grappleShieldUntil
                        && "fall".equals(event.getSource().getMsgId())) {
                    event.setCanceled(true);
                    return;
                }

                // Combo #11 : Brise-Montagne -> subir un coup annule la lenteur d'attaque par Célérité II pendant 2,5 s !
                if ((defMask & (1L << C11_TITAN_TELLURIQUE)) != 0L) {
                    victim.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 1, true, false));
                }

                // Combo #16 : Résonance Abyssale (wardenspine + perception/echolocation) -> impulsion sonique ESP sur tous les ennemis à 16 blocs !
                if ((defMask & (1L << C16_VENGEANCE_WARDEN)) != 0L && rawAttacker instanceof LivingEntity
                        && victim.level() instanceof ServerLevel slWarden) {
                    EnchantmentParticleFX.shockwaveRing(slWarden, victim.getX(), victim.getY() + 0.9, victim.getZ(),
                            ParticleTypes.SCULK_SOUL, 3.2, 16, 0.04);
                    for (Mob m : slWarden.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(16.0))) {
                        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                        m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    }
                }

                // Combo #17 : Marcheur de Cendres -> double riposte de feu du Noyau Ardent !
                if ((defMask & (1L << C17_AVATAR_INFERNAL)) != 0L && rawAttacker instanceof LivingEntity aggr) {
                    aggr.setRemainingFireTicks(Math.max(aggr.getRemainingFireTicks(), 120));
                    safeHurt(aggr, victim.damageSources().onFire(), 4.0f);
                }

                // Combo #20 : Miracle du Phénix Blanc (final_breath + priests_blessing) -> Nova Solaire sous 20% PV !
                if ((defMask & (1L << C20_RENAISSANCE_SOLAIRE)) != 0L
                        && (victim.getHealth() - event.getAmount()) <= victim.getMaxHealth() * 0.22f
                        && now >= defSt.phoenixNovaReadyAt) {
                    defSt.phoenixNovaReadyAt = now + 1200L; // 60 s recharge
                    event.setAmount(event.getAmount() * 0.30f);
                    victim.heal(12.0f);
                    victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
                    victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0));
                    if (victim.level() instanceof ServerLevel slPhx) {
                        EnchantmentParticleFX.shockwaveRing(slPhx, victim.getX(), victim.getY() + 0.2, victim.getZ(),
                                ParticleTypes.FLAME, 3.5, 24, 0.06);
                        EnchantmentParticleFX.crownHalo(victim, EnchantmentParticleFX.GOLD_DIVINE, 16);
                        slPhx.playSound(null, victim.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.35f);
                        for (Mob m : slPhx.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(5.0))) {
                            if (!isFriendlySummon(m, victim)) {
                                m.setRemainingFireTicks(100);
                                Vec3 push = m.position().subtract(victim.position());
                                if (push.lengthSqr() > 0.01) {
                                    m.setDeltaMovement(push.normalize().scale(0.75).add(0, 0.35, 0));
                                    m.hurtMarked = true;
                                }
                            }
                        }
                    }
                }

                // Combo #30 : Relique Indélébile du Roi -> -15 % de dégâts subis supplémentaires !
                if ((defMask & (1L << C30_RELIQUE_ROI_TRANSCENDANT)) != 0L) {
                    event.setAmount(event.getAmount() * 0.85f);
                }
            }

            // --- B. Soldat invoqué d'un Empereur de Légion (Combo #9) ---
            if (rawAttacker instanceof Mob soldier && soldier.getPersistentData().hasUUID("slb_combo9_emperor")
                    && victim.level() instanceof ServerLevel slEmp) {
                UUID empId = soldier.getPersistentData().getUUID("slb_combo9_emperor");
                Player emp = slEmp.getPlayerByUUID(empId);
                if (emp != null && emp.isAlive()) {
                    emp.heal(0.6f);
                }
            }

            // --- C. Attaque directe par un porteur de combo ---
            if (!(rawAttacker instanceof LivingEntity attacker)) return;
            long atkMask = EnchantmentComboRegistry.getActiveMask(attacker);
            if (atkMask == 0L) return;
            PlayerComboState atkSt = stateOf(attacker);

            // Combo #7 : Festin Écarlate (blood_drinker_edge/bloodthirsty/bloodthirst + assimilation)
            if ((atkMask & (1L << C07_SEIGNEUR_VAMPIRE)) != 0L) {
                float healAmt = Math.min(4.0f, event.getAmount() * 0.18f);
                float missing = attacker.getMaxHealth() - attacker.getHealth();
                if (healAmt > missing) {
                    float overheal = healAmt - Math.max(0.0f, missing);
                    attacker.setHealth(attacker.getMaxHealth());
                    float newAbs = Math.min(12.0f, attacker.getAbsorptionAmount() + overheal * 1.5f);
                    attacker.setAbsorptionAmount(newAbs);
                } else {
                    attacker.heal(healAmt);
                }
                if (attacker instanceof Player pl && attacker.getRandom().nextInt(100) < 35) {
                    pl.getFoodData().eat(1, 0.35f);
                }
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 7, 0.25, 0.02);
            }

            // Combo #8 : Lame du Néant Caché (shadow_hunt + phantom_step + night_veil)
            if ((atkMask & (1L << C08_ASSASSIN_NOCTURNE)) != 0L && attacker.hasEffect(MobEffects.INVISIBILITY)) {
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

            // Combo #10 : Jugement de l'Orage (thundercall_pact + electric_heart)
            if ((atkMask & (1L << C10_FAUCHEUR_TEMPETE)) != 0L && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                atkSt.thunderHitCounter++;
                if (atkSt.thunderHitCounter >= 4 && victim.level() instanceof ServerLevel slTh) {
                    atkSt.thunderHitCounter = 0;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(slTh);
                    if (bolt != null) {
                        bolt.moveTo(victim.getX(), victim.getY(), victim.getZ());
                        bolt.setVisualOnly(true);
                        slTh.addFreshEntity(bolt);
                    }
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

            // Combo #11 : Brise-Montagne -> onde sismique de zone sur coup mêlée chargé !
            if ((atkMask & (1L << C11_TITAN_TELLURIQUE)) != 0L && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                    && victim.level() instanceof ServerLevel slTit) {
                EnchantmentParticleFX.shockwaveRing(slTit, victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        EnchantmentParticleFX.EARTH_OCHRE, 2.8, 14, 0.03);
                float splash = event.getAmount() * 0.35f;
                for (Mob m : slTit.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(2.8))) {
                    if (m != victim && m.isAlive() && !isFriendlySummon(m, attacker)) {
                        safeHurt(m, attacker.damageSources().mobAttack(attacker), splash);
                    }
                }
            }

            // Combo #13 : Riposte Supersonique (swift_strike + evasive_footwork)
            if ((atkMask & (1L << C13_DUELLISTE_ECLAIR)) != 0L && now < atkSt.lateralChargeUntil
                    && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
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

            // Combo #14 : Contre-Attaque Brise-Garde (perfect_parry + guard_breaker)
            if ((atkMask & (1L << C14_BASTION_MIROIR)) != 0L && now < atkSt.parryRiposteUntil) {
                atkSt.parryRiposteUntil = 0L;
                event.setAmount(event.getAmount() * 1.75f);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 3));
                if (victim instanceof Player vp) {
                    vp.getCooldowns().addCooldown(Items.SHIELD, 120);
                    vp.stopUsingItem();
                }
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CRIT, 16, 0.3, 0.08);
            }

            // Combo #18 : Souverain d'Atlantide (Trident + conduit_heart + buoy) -> Marée Pressurisée même hors de l'eau !
            if ((atkMask & (1L << C18_SEIGNEUR_ABYSSES)) != 0L
                    && (attacker.getMainHandItem().is(Items.TRIDENT) || event.getSource().getDirectEntity() instanceof ThrownTrident)) {
                event.setAmount(event.getAmount() * 1.35f);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.SPLASH, 14, 0.35, 0.06);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.NAUTILUS, 8, 0.30, 0.04);
            }

            // Combo #21 : Tir Guidé Astral (sniper + perception/concentration) -> +25 % sur cible révélée + marque astrale !
            if ((atkMask & (1L << C21_OEIL_FAUCON_ASTRAL)) != 0L && event.getSource().getDirectEntity() instanceof AbstractArrow) {
                if (victim.hasEffect(MobEffects.GLOWING)) {
                    event.setAmount(event.getAmount() * 1.25f);
                }
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
            }

            // Combo #30 : Relique Indélébile du Roi -> +20 % à tous les dégâts infligés !
            if ((atkMask & (1L << C30_RELIQUE_ROI_TRANSCENDANT)) != 0L) {
                event.setAmount(event.getAmount() * 1.20f);
            }
        } catch (Throwable t) {
            guard("onLivingHurt", t);
        }
    }

    // =========================================================================
    // 3. BOUCLIER : Combos #14 & #15
    // =========================================================================
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        try {
            LivingEntity defender = event.getEntity();
            long mask = EnchantmentComboRegistry.getActiveMask(defender);
            if (mask == 0L) return;
            long now = defender.level().getGameTime();

            // Combo #14 : Contre-Attaque Brise-Garde (perfect_parry + guard_breaker)
            if ((mask & (1L << C14_BASTION_MIROIR)) != 0L) {
                stateOf(defender).parryRiposteUntil = now + 85L;
                if (defender.level() instanceof ServerLevel sl) {
                    sl.playSound(null, defender.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.55f, 1.85f);
                    EnchantmentParticleFX.burstOnEntity(defender, EnchantmentParticleFX.OVERCLOCK_CYAN, 10, 0.25, 0.03);
                }
            }

            // Combo #15 : Rempart Sismique (shield_quake + bastion_heart)
            if ((mask & (1L << C15_CITADELLE_VIVANTE)) != 0L && defender.level() instanceof ServerLevel slCit) {
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
        } catch (Throwable t) {
            guard("onShieldBlock", t);
        }
    }

    // =========================================================================
    // 4. ÉLIMINATION (LivingDeathEvent) : Combos #8 & #12
    // =========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
            long mask = EnchantmentComboRegistry.getActiveMask(killer);
            if (mask == 0L) return;

            // Combo #8 : Lame du Néant Caché -> réinitialise l'Invisibilité instantanément sur élimination !
            if ((mask & (1L << C08_ASSASSIN_NOCTURNE)) != 0L) {
                killer.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, true, false));
                EnchantmentParticleFX.burstOnEntity(killer, EnchantmentParticleFX.SHADOW_VIOLET, 10, 0.3, 0.02);
            }

            // Combo #12 : Rançon du Dernier Souffle -> restaure 25 % des PV manquants sur chaque kill !
            if ((mask & (1L << C12_BERSERKER_IMMORTEL)) != 0L) {
                float missing = Math.max(0.0f, killer.getMaxHealth() - killer.getHealth());
                if (missing > 0.0f) {
                    killer.heal(missing * 0.25f);
                }
                killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
                EnchantmentParticleFX.burstOnEntity(killer, EnchantmentParticleFX.SCARLET_MIST, 12, 0.3, 0.03);
            }
        } catch (Throwable t) {
            guard("onLivingDeath", t);
        }
    }

    // =========================================================================
    // 5. PROJECTILES, ARCS, ARBALÈTES & ARMÉE : Combos #9, #21, #22, #23, #24, #25
    // =========================================================================
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        try {
            // Combo #9 : Phalange Impériale (summon_army/army_summon + commander) -> buffe les soldats invoqués !
            if (event.getEntity() instanceof Mob mob && event.getLevel() instanceof ServerLevel sl) {
                Player nearestCommander = sl.getNearestPlayer(mob, 6.0);
                if (nearestCommander != null
                        && EnchantmentComboRegistry.hasCombo(nearestCommander, C09_EMPEREUR_LEGION)
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

            // Marquage des flèches/carreaux tirés par un porteur de combo (#21, #22, #23, #24, #25)
            if (event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity shooter) {
                long mask = EnchantmentComboRegistry.getActiveMask(shooter);
                if (mask == 0L) return;
                if ((mask & (1L << C21_OEIL_FAUCON_ASTRAL)) != 0L) {
                    arrow.setNoGravity(true);
                    arrow.getPersistentData().putBoolean("slb_combo21_astral", true);
                    if (event.getLevel() instanceof ServerLevel sla) {
                        TestProcedure.schedule(sla, TestProcedure.currentTick(sla) + 16, () -> {
                            if (arrow.isAlive()) arrow.setNoGravity(false);
                        });
                    }
                }
                if ((mask & (1L << C22_PLUIE_CATACLYSMIQUE)) != 0L && !shooter.onGround()) {
                    arrow.getPersistentData().putBoolean("slb_combo22_apocalypse", true);
                }
                if ((mask & (1L << C23_COMMANDO_GRAPPIN)) != 0L) {
                    arrow.getPersistentData().putBoolean("slb_combo23_grapple", true);
                }
                if ((mask & (1L << C24_ARTILLEUR_INQUISITEUR)) != 0L) {
                    arrow.getPersistentData().putBoolean("slb_combo24_brimstone", true);
                }
                if ((mask & (1L << C25_SPECTRE_DIMENSIONNEL)) != 0L) {
                    arrow.getPersistentData().putBoolean("slb_combo25_void", true);
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
            if (EnchantmentComboRegistry.hasCombo(player, C21_OEIL_FAUCON_ASTRAL) && event.getCharge() >= 18
                    && player.level() instanceof ServerLevel sl) {
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.END_ROD, 6, 0.25, 0.02);
            }
        } catch (Throwable t) {
            guard("onArrowLoose", t);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()) return;
        try {
            if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
            if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
            if (!(arrow.level() instanceof ServerLevel sl)) return;
            Vec3 hit = event.getRayTraceResult().getLocation();

            // Combo #22 : Bombardement d'Apocalypse (meteor + gale) -> 2 météorites secondaires autour de l'impact !
            if (arrow.getPersistentData().getBoolean("slb_combo22_apocalypse")) {
                arrow.getPersistentData().remove("slb_combo22_apocalypse");
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

            // Combo #23 : Assaut Voltigeur (grapple + wall_runner) -> bouclier anti-chute + Vitesse II !
            if (arrow.getPersistentData().getBoolean("slb_combo23_grapple")) {
                arrow.getPersistentData().remove("slb_combo23_grapple");
                stateOf(shooter).grappleShieldUntil = sl.getGameTime() + 100L;
                shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
            }

            // Combo #24 : Salve du Faucon Infernale (brimstone/peregrine + hunters_pace) -> braise retardée sur cible !
            if (arrow.getPersistentData().getBoolean("slb_combo24_brimstone")
                    && event.getRayTraceResult() instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity hitMob) {
                arrow.getPersistentData().remove("slb_combo24_brimstone");
                hitMob.setRemainingFireTicks(80);
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 30L, () -> {
                    if (hitMob.isAlive()) {
                        EnchantmentParticleFX.burstOnEntity(hitMob, ParticleTypes.LAVA, 10, 0.3, 0.05);
                        safeHurt(hitMob, shooter.damageSources().onFire(), 5.5f);
                    }
                });
            }

            // Combo #25 : Embuscade du Vide (teleport_arrow/swap_arrow + dimensional_step) -> leurre d'ombre explosif + Invisibilité/Force !
            if (arrow.getPersistentData().getBoolean("slb_combo25_void")) {
                arrow.getPersistentData().remove("slb_combo25_void");
                Vec3 origin = shooter.position();
                EnchantmentParticleFX.shockwaveRing(sl, origin.x, origin.y + 0.3, origin.z,
                        EnchantmentParticleFX.SHADOW_VIOLET, 3.0, 18, 0.04);
                for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(origin.x - 3.5, origin.y - 2, origin.z - 3.5, origin.x + 3.5, origin.y + 3, origin.z + 3.5))) {
                    if (!isFriendlySummon(m, shooter)) {
                        m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0));
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2));
                    }
                }
                shooter.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 65, 0, true, false));
                shooter.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 65, 0, true, false));
            }
        } catch (Throwable t) {
            guard("onProjectileImpact", t);
        }
    }

    // =========================================================================
    // 6. MINAGE, FORÊT & AGRICULTURE (BlockEvent.BreakEvent) : Combos #26, #27, #28
    // =========================================================================
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        try {
            long mask = EnchantmentComboRegistry.getActiveMask(player);
            if (mask == 0L) return;
            BlockPos pos = event.getPos();
            BlockState state = event.getState();

            // Combo #26 : Maître-Mineur Magmatique (extracting/vein_miner + miners_guidance)
            if ((mask & (1L << C26_COEUR_FORGE_NAINE)) != 0L && state.is(Tags.Blocks.ORES)) {
                player.giveExperiencePoints(3);
                PlayerComboState st = stateOf(player);
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

            // Combo #27 : Esprit de la Forêt Ancienne (lumberjack/tree_felling + trailblazer)
            if ((mask & (1L << C27_BUCHERON_SYLVESTRE)) != 0L && state.is(BlockTags.LOGS)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, true, false));
                BlockPos below = pos.below();
                BlockState soil = sl.getBlockState(below);
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

            // Combo #28 : Bénédiction de Déméter (bountiful_harvest/farmers_hoe + food_is_heaven/rain_nourishment)
            if ((mask & (1L << C28_MOISSONNEUR_ABONDANCE)) != 0L
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
    // 7. PÊCHE (ItemFishedEvent) : Combo #29 Chasseur du Léviathan
    // =========================================================================
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide()) return;
            if (!EnchantmentComboRegistry.hasCombo(player, C29_AMIRAL_HAUTURIER)) return;

            player.setAbsorptionAmount(Math.min(12.0f, Math.max(player.getAbsorptionAmount(), 4.0f)));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 160, 0, true, false));
            List<ItemStack> drops = event.getDrops();
            if (!drops.isEmpty() && player.getRandom().nextInt(100) < 50 && player.level() instanceof ServerLevel sl) {
                ItemStack extra = drops.get(0).copy();
                sl.addFreshEntity(new ItemEntity(sl, player.getX(), player.getY() + 0.6, player.getZ(), extra));
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.NAUTILUS, 12, 0.35, 0.05);
            }
        } catch (Throwable t) {
            guard("onItemFished", t);
        }
    }

    // =========================================================================
    // 8. COMMANDE EN JEU /dlbcombos (consultation joueur + kit de test OP)
    // =========================================================================
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dlbcombos")
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    long mask = EnchantmentComboRegistry.computeRawMask(sp);
                    int activeCount = Long.bitCount(mask);
                    sp.sendSystemMessage(Component.literal("\u00a76\u00a7l=== Synergies Multi-Pi\u00e8ces Actives (" + activeCount + "/30) ==="));
                    if (activeCount == 0) {
                        sp.sendSystemMessage(Component.literal("\u00a77Aucun combo actif. Faites \u00a7e/dlbcombos list\u00a77 pour voir les 30 synergies !"));
                    } else {
                        for (ComboDef c : COMBOS) {
                            if ((mask & c.bit()) != 0L) {
                                sp.sendSystemMessage(Component.literal("\u00a76\u2726 #" + (c.index() + 1) + " ")
                                        .append(c.translatedName())
                                        .append(Component.literal(" \u00a77\u2014 ").append(c.translatedDesc().copy().withStyle(ChatFormatting.WHITE))));
                            }
                        }
                    }
                    return activeCount;
                })
                .then(Commands.literal("list").executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    long mask = EnchantmentComboRegistry.computeRawMask(sp);
                    sp.sendSystemMessage(Component.literal("\u00a76\u00a7l=== Codex des 30 Combos Multi-Pi\u00e8ces ==="));
                    for (ComboDef c : COMBOS) {
                        boolean on = (mask & c.bit()) != 0L;
                        sp.sendSystemMessage(Component.literal((on ? "\u00a7a\u2726 " : "\u00a78\u2727 ") + "#" + (c.index() + 1) + " ")
                                .append(c.translatedName())
                                .append(Component.literal(" \u00a78[").append(EnchantmentComboRegistry.buildRequirementsSummary(c, sp)).append("\u00a78]")));
                    }
                    return 30;
                }))
                .then(Commands.literal("give")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, 30)).executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            DeepEnchant.rememberAccess(sp.serverLevel().registryAccess());
                            int num = IntegerArgumentType.getInteger(ctx, "numero");
                            ComboDef c = COMBOS.get(num - 1);
                            for (PieceRequirement req : c.pieces()) {
                                ItemStack item = new ItemStack(req.sampleItem());
                                Enchantment ench = DeepEnchant.byId("deep_lucky_block:" + req.primaryPath());
                                if (ench != null) {
                                    int giveLvl = Math.min(5, Math.max(1, CustomEnchantment.getMaxLevelMod(ench)));
                                    DeepEnchant.setLevel(item, ench, giveLvl);
                                }
                                if (!sp.getInventory().add(item)) {
                                    sp.drop(item, false);
                                }
                            }
                            sp.sendSystemMessage(Component.literal("\u00a7a\u2726 Kit du Combo #" + num + " remis : ").append(c.translatedName()));
                            return 1;
                        }))));
    }
}
