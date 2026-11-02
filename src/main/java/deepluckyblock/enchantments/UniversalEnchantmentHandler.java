package deepluckyblock.enchantments;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.concurrent.ConcurrentHashMap;
import deepluckyblock.procedures.TestProcedure;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * UniversalEnchantmentHandler : gestionnaire de tous les enchantements autonomes.
 *
 * FIX CRASH : tout degat manuel passe par hurtNoCascade() (ThreadLocal HURT_GUARD) et les
 * handlers LivingIncomingDamageEvent retournent immediatement en cas de re-entree -> plus de recursion.
 *
 * FIX CRIT x2.25 : le bloc de crit custom ne re-multiplie plus le crit vanilla (deja applique
 * par Minecraft avant LivingIncomingDamageEvent). Le bloc crit_rate redondant a ete supprime.
 *
 * FIX DRAGON BREATH : la fleche de souffle de dragon produit un nuage violet (ParticleTypes.DRAGON_BREATH)
 * a degats, plus du poison vert. Duree divisee par 2.
 *
 * FIX GAUNTLETS (FIST) : wing_chun_gauntlet ne donne ses boost que main principale vide.
 *
 * FIX SCALING "PETIT A PETIT" : avant, getEnchantLvl() plafonnait a 1 tout
 * enchantement enregistre avec getMaxLevel()=1 et normalizeSingleLevelEnchantments()
 * reecrivait le NBT des items (niveau X -> 1) chaque seconde -> plus rien ne scalait.
 * Maintenant : le cap officiel est TOUJOURS respecte (un enchantement avec un
 * niveau max 1 reste niveau 1 en jeu normal, aucune definition d'enchantement
 * n'est modifiee), MAIS si un niveau superieur est FORCE sur l'item (commande,
 * lucky block, NBT), l'effet scale progressivement "petit a petit" :
 * +1 niveau effectif par palier de racine carree du depassement
 * (cap 1 : force 2-4 -> effectif 2, force 10 -> 4, force 100 -> 10).
 */
@SuppressWarnings({"removal", "deprecation"})
@EventBusSubscriber(modid = "deep_lucky_block")
public class UniversalEnchantmentHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Long> LAST_ATTACK_TIME = new HashMap<>();
    private static final Map<UUID, Integer> COMBO_STACKS = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> SOUL_BOUND_CACHE = new HashMap<>();
    private static final net.minecraft.resources.ResourceLocation MOMENTUM_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_momentum_speed");
    private static final net.minecraft.resources.ResourceLocation TRAILBLAZER_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_trailblazer_speed");
    private static final net.minecraft.resources.ResourceLocation EVASIVE_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_evasive_speed");
    private static final net.minecraft.resources.ResourceLocation WINDRUNNER_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_windrunner_speed");
    private static final net.minecraft.resources.ResourceLocation PANIC_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_panic_speed");
    private static final net.minecraft.resources.ResourceLocation CORE_SPRINT_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_core_sprint_speed");
    private static final net.minecraft.resources.ResourceLocation STACKED_SPRINT_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_stacked_sprint_speed");
    private static final net.minecraft.resources.ResourceLocation RANGER_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_ranger_speed");
    private static final net.minecraft.resources.ResourceLocation LIGHT_WALKER_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_light_walker_speed");
    private static final net.minecraft.resources.ResourceLocation STRAFE_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_strafe_speed");
    private static final net.minecraft.resources.ResourceLocation LONE_WOLF_SPEED_ID = net.minecraft.resources.ResourceLocation.parse("deep_lucky_block:modifier_lone_wolf_speed");

    private static final ThreadLocal<Boolean> HURT_GUARD = ThreadLocal.withInitial(() -> false);

    public static boolean ENABLE_PHOENIX_REVIVE = true;

    // Effets négatifs possibles pour l'enchantement CHAOS (un aléatoire par flèche).
    // PORT 1.21.1 : tableau brut (les tableaux génériques ne se créent pas par initialiseur).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final net.minecraft.core.Holder<MobEffect>[] CHAOS_EFFECTS = new net.minecraft.core.Holder[]{
        MobEffects.POISON, MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN,
        MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.HUNGER,
        MobEffects.CONFUSION, MobEffects.DIG_SLOWDOWN
    };

    /**
     * Multiplicateur de dégâts selon la taille relative de la cible : volume de
     * sa hitbox comparé à celui du joueur. Cible plus grande -> bonus, plus
     * petite -> malus, toujours proportionnel.
     */
    private static float sizeDamageMultiplier(LivingEntity attacker, LivingEntity target, float strengthPerLevel) {
        float playerVolume = attacker.getBbHeight() * attacker.getBbWidth() * attacker.getBbWidth();
        float targetVolume = target.getBbHeight() * target.getBbWidth() * target.getBbWidth();
        if (playerVolume < 0.01f) playerVolume = 0.01f;
        float ratio = targetVolume / playerVolume; // > 1 : cible plus grande que le joueur
        float mult = 1.0f + (ratio - 1.0f) * strengthPerLevel;
        return Math.max(0.25f, Math.min(4.0f, mult));
    }

    private static void hurtNoCascade(LivingEntity target, DamageSource source, float amount) {
        if (target == null || source == null) return;
        try {
            HURT_GUARD.set(true);
            target.hurt(source, amount);
        } finally {
            HURT_GUARD.set(false);
        }
    }

    // FIX (mob-equipment-enchant generalization) : DamageSources.playerAttack
    // exige un Player ; DamageSources.mobAttack accepte n'importe quel
    // LivingEntity (y compris un Player) et produit une source d'attaque
    // equivalente -- utilise pour que les effets de zone (sweep, etc.)
    // fonctionnent aussi quand l'attaquant est un mob equipe.
    private static DamageSource attackerDamageSource(LivingEntity attacker) {
        return attacker instanceof Player p
                ? attacker.damageSources().playerAttack(p)
                : attacker.damageSources().mobAttack(attacker);
    }

    private static void printHandlerError(String where, Throwable t) {
        try {
            deepluckyblock.util.DebugLog.err("[DeepLuckyBlock] Erreur interceptee dans " + where + " (crash evite) : " + t);
            deepluckyblock.util.DebugLog.trace(t, System.err);
        } catch (Throwable ignored) {}
    }

    // 1. Offensif
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event){try{onLivingHurtImpl(event);}catch(Throwable t__){guard("onLivingHurt",t__);}}
    private static void onLivingHurtImpl(LivingIncomingDamageEvent event){
        if (HURT_GUARD.get()) return;
        try {
            handleOffensiveHurt(event);
        } catch (Throwable t) {
            printHandlerError("onLivingHurt", t);
        }
    }

    // FIX (mob-equipment-enchant generalization) : "instanceof Player" ->
    // "instanceof LivingEntity" -- generalise TOUS les effets de melee
    // offensif (Light/Shadow/Electric Heart, Desperate Kill, Devour/Leech,
    // etc.) a n'importe quel attaquant LivingEntity, dont les mobs
    // equipes d'une arme enchantee. Seul "Food Power Strike" (necessite
    // FoodData, propre au joueur) reste explicitement Player-only plus bas.
    private static void handleOffensiveHurt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity player)) return;
        ItemStack weapon = player.getMainHandItem();

        // LIGHT HEART (cahier des charges original, volet offensif :
        // degats punitifs contre les morts-vivants). Independant de
        // Seraph's Judgment (enchantement d'arme separe, plus haut).
        int lightHeartLvl = getElementalHeartLvl(player, "light_heart");
        if (lightHeartLvl > 0 && event.getEntity().getType().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE)) {
            event.setAmount(event.getAmount() * (1f + 0.25f * lightHeartLvl));
            if (player.level() instanceof ServerLevel lightLevel) {
                lightLevel.sendParticles(ParticleTypes.END_ROD,
                        event.getEntity().getX(), event.getEntity().getY() + event.getEntity().getBbHeight() * 0.5, event.getEntity().getZ(),
                        8, 0.2, 0.3, 0.2, 0.02);
            }
        }

        // SHADOW HEART (cahier des charges original, "combo equilibree" :
        // vol de vie / invisibilite / affaiblissement -- la regen n'a lieu
        // que la nuit, voir ElementalHeartConditions). Chaque coup de melee
        // porte par le joueur draine un peu de vie ET a une chance
        // d'affaiblir la cible ; independant d'Umbral Pact/Voidling's
        // Hunger (enchantements de piece separes, plus haut/plus bas).
        int shadowHeartLvl = getElementalHeartLvl(player, "shadow_heart");
        if (shadowHeartLvl > 0 && event.getEntity() instanceof LivingEntity shadowTarget) {
            player.heal(event.getAmount() * (0.06f * shadowHeartLvl));
            if (player.level().random.nextInt(100) < (10 * shadowHeartLvl)) {
                shadowTarget.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, true));
            }
        }

        // ELECTRIC HEART (cahier des charges original) : sur chaque coup de
        // melee, chance de declencher UN effet aleatoire parmi 3 (pas les
        // trois a la fois -- "proc"), independant d'Overcharge/Thundercall
        // Pact (chaine electrique sur critique, geree plus bas) qui reste un
        // enchantement d'arme separe. Le coeur lui-meme donne ce proc brut.
        int electricHeartLvl = getElementalHeartLvl(player, "electric_heart");
        if (electricHeartLvl > 0 && event.getEntity() instanceof LivingEntity electricTarget
                && player.level().random.nextInt(100) < (10 + 5 * electricHeartLvl)) {
            int proc = player.level().random.nextInt(3);
            switch (proc) {
                case 0 -> electricTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, true));
                case 1 -> electricTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, electricHeartLvl - 1, true, true));
                default -> event.setAmount(event.getAmount() * (1f + 0.20f * electricHeartLvl));
            }
            if (player.level() instanceof ServerLevel electricLevel) {
                electricLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        electricTarget.getX(), electricTarget.getY() + electricTarget.getBbHeight() * 0.5, electricTarget.getZ(),
                        10, 0.25, 0.3, 0.25, 0.05);
            }
        }

        // FIX DOUBLE-COMPTAGE (rapporté en jeu) : supreme/extreme/ultra/advanced
        // sharpness (partie < niveau 100 pour supreme) sont désormais de VRAIS
        // AttributeModifier (voir RealAttributeModifierHandler, "*_flat"/"*_mult"),
        // donc DÉJÀ inclus dans event.getAmount() de base (calculé par vanilla à
        // partir de l'attribut Attack Damage réel de l'arme). Les recalculer ici
        // en plus doublait le bonus en combat réel (constaté : dégâts environ 2x
        // supérieurs à ceux affichés dans le tooltip). Seul le cas "one-shot"
        // (supreme_sharpness >= 100, non représentable comme attribut continu)
        // reste géré ici.
        int supSharpLvl = getEnchantLvl(weapon, "deep_lucky_block:supreme_sharpness");
        if (supSharpLvl >= 100) {
            event.setAmount(event.getEntity().getMaxHealth() * 10f);
        }
        if (supSharpLvl > 0) {
            EnchantmentParticleFX.slashArc(player, ParticleTypes.END_ROD, EnchantmentParticleFX.GOLD_DIVINE, 2.3, 12);
        } else if (getEnchantLvl(weapon, "deep_lucky_block:extreme_sharpness") > 0) {
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.ENCHANTED_HIT, 10, 0.3, 0.15);
        }
        int peerlessLvl = getEnchantLvl(weapon, "deep_lucky_block:peerless");
        if (peerlessLvl > 0) {
            EnchantmentParticleFX.slashArc(player, EnchantmentParticleFX.STEEL_SILVER, ParticleTypes.CRIT, 2.0, 9);
            if (event.getEntity().getArmorValue() > 0) {
                hurtNoCascade(event.getEntity(), player.damageSources().magic(), Math.min(20f, 1.5f * peerlessLvl));
            }
        }

        // T306-OVERHAUL : Famille Tueurs de Colosses (promising_blade -> giant_slayer -> titan_slayer)
        int titanLvl = getEnchantLvl(weapon, "deep_lucky_block:titan_slayer");
        if (titanLvl > 0) {
            float mult = sizeDamageMultiplier(player, event.getEntity(), 0.75f * titanLvl);
            if (event.getEntity().getMaxHealth() >= 50f) {
                mult += 0.20f * titanLvl;
                event.setAmount(event.getAmount() * mult + Math.min(24f, event.getEntity().getHealth() * 0.02f * titanLvl));
                if (player.level() instanceof ServerLevel slt) {
                    EnchantmentParticleFX.shockwaveRing(slt, event.getEntity().getX(), event.getEntity().getY() + 0.1,
                            event.getEntity().getZ(), EnchantmentParticleFX.EARTH_OCHRE, 1.8, 14, 0.06);
                }
            } else {
                event.setAmount(event.getAmount() * mult);
            }
        }
        int giantLvl = getEnchantLvl(weapon, "deep_lucky_block:giant_slayer");
        if (giantLvl > 0) {
            if (event.getEntity().getMaxHealth() >= 40f) {
                event.setAmount(event.getAmount() * (1.0f + 0.25f * giantLvl));
            } else {
                event.setAmount(event.getAmount() * sizeDamageMultiplier(player, event.getEntity(), 0.40f * giantLvl));
            }
        }
        int cavalierLvl = getEnchantLvl(weapon, "deep_lucky_block:cavalier");
        if (cavalierLvl > 0 && player.getVehicle() instanceof LivingEntity lvMount) {
            event.setAmount(event.getAmount() * (1.0f + 0.50f * cavalierLvl));
            EnchantmentParticleFX.ankleTrail(lvMount, EnchantmentParticleFX.GOLD_DIVINE, 4, 0.03);
        }
        int battleHardLvl = getEnchantLvl(weapon, "deep_lucky_block:battle_hardened");
        if (battleHardLvl > 0 && weapon.isDamageableItem()) {
            float lostPct = (float) weapon.getDamageValue() / weapon.getMaxDamage();
            event.setAmount(event.getAmount() * (1f + Math.min(1.0f, lostPct * battleHardLvl)));
        }

        // T306-OVERHAUL : Famille Vol de Vie (bloodthirst -> bloodthirsty -> blood_drinker_edge) + blood_edge
        int bloodThirstLvl = getEnchantLvl(weapon, "deep_lucky_block:bloodthirst");
        if (bloodThirstLvl > 0) player.heal(event.getAmount() * (0.08f * bloodThirstLvl));
        int bloodThirstyLvl = getEnchantLvl(weapon, "deep_lucky_block:bloodthirsty");
        if (bloodThirstyLvl > 0) {
            player.heal(event.getAmount() * (0.14f * bloodThirstyLvl));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.SCARLET_MIST, 6, 0.25, 0.02);
        }
        int bloodEdgeLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_edge");
        if (bloodEdgeLvl > 0) {
            player.heal(event.getAmount() * (0.10f * bloodEdgeLvl));
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.CRIMSON_BLOOD, 5, 0.2, 0.01);
        }
        int bloodDrinkerLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_drinker_edge");
        if (bloodDrinkerLvl > 0) {
            float rawHeal = event.getAmount() * (0.20f * bloodDrinkerLvl);
            float missing = Math.max(0f, player.getMaxHealth() - player.getHealth());
            player.heal(rawHeal);
            float overheal = Math.max(0f, rawHeal - missing);
            if (overheal > 0.1f) {
                player.setAbsorptionAmount(Math.min(6.0f + bloodDrinkerLvl, player.getAbsorptionAmount() + overheal * 0.5f));
            }
            EnchantmentParticleFX.doubleHelix(player, EnchantmentParticleFX.CRIMSON_BLOOD, EnchantmentParticleFX.SCARLET_MIST, 0.52, 8);
        }

        // T306-FIX : blood_pact sacrifie 1 PV pour infliger +2/niv VRAIS dégâts ignorant l'armure
        int bloodPactLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_pact");
        if (bloodPactLvl > 0 && player.getHealth() > 2f) {
            player.setHealth(player.getHealth() - 1f);
            hurtNoCascade(event.getEntity(), player.damageSources().magic(), 2.0f * bloodPactLvl);
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.CRIMSON_BLOOD, 8, 0.25, 0.03);
        }

        // T306-OVERHAUL : sighing_strike (Frappe Soupirante) & brutal & Pelles de combat
        int sighLvl = getEnchantLvl(weapon, "deep_lucky_block:sighing_strike");
        if (sighLvl > 0) {
            boolean weakenedOrSlowed = event.getEntity().hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || event.getEntity().hasEffect(MobEffects.WEAKNESS);
            if (weakenedOrSlowed) event.setAmount(event.getAmount() * (1.0f + 0.20f * sighLvl));
            event.getEntity().setAirSupply(Math.max(-20, event.getEntity().getAirSupply() - 40 * sighLvl));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            EnchantmentParticleFX.slashArc(player, ParticleTypes.CLOUD, ParticleTypes.SOUL, 1.9, 8);
        }
        int brutalLvl = getEnchantLvl(weapon, "deep_lucky_block:brutal");
        if (brutalLvl > 0) {
            if (event.getEntity() instanceof Player tp && tp.isBlocking()) {
                tp.getCooldowns().addCooldown(tp.getUseItem().getItem(), 30 * brutalLvl);
            }
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.CRIMSON_BLOOD, 6, 0.25, 0.03);
        }
        if (weapon.getItem() instanceof net.minecraft.world.item.ShovelItem) {
            int sBlade = getEnchantLvl(weapon, "deep_lucky_block:shovel_blade");
            int sHeavy = getEnchantLvl(weapon, "deep_lucky_block:shovel_heavy_hit");
            int sHalberd = getEnchantLvl(weapon, "deep_lucky_block:shovel_halberd");
            if (sBlade > 0) {
                EnchantmentParticleFX.slashArc(player, ParticleTypes.SWEEP_ATTACK, null, 1.8, 5);
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(2.3))) {
                    if (mob != event.getEntity() && mob.isAlive()) {
                        hurtNoCascade(mob, attackerDamageSource(player), event.getAmount() * (0.35f + 0.10f * sBlade));
                    }
                }
            }
            if (sHeavy > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 1));
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, 0));
                player.level().playSound(null, event.getEntity().blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.35f, 1.55f);
            }
            if (sHalberd > 0 && (player.distanceTo(event.getEntity()) >= 2.7f || event.getEntity().isPassenger())) {
                event.setAmount(event.getAmount() * (1.0f + 0.25f * sHalberd));
            }
        }

        // T306-FIX + T307-FX : annihilating_sweep inflige 100% (+15%/niv supplémentaire) en zone avec un arc doré/écarlate d'annihilation !
        int sweepLvl = getEnchantLvl(weapon, "deep_lucky_block:annihilating_sweep");
        if (sweepLvl > 0) {
            double rad = deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(3.2, 0.3, sweepLvl);
            float mult = 1.0f + 0.15f * Math.max(0, sweepLvl - 1);
            EnchantmentParticleFX.slashArc(player, EnchantmentParticleFX.GOLD_DIVINE, ParticleTypes.SWEEP_ATTACK, Math.min(3.6, rad), 15);
            if (player.level() instanceof ServerLevel slsw) {
                EnchantmentParticleFX.shockwaveRing(slsw, event.getEntity().getX(), event.getEntity().getY() + 0.5, event.getEntity().getZ(),
                        EnchantmentParticleFX.SCARLET_MIST, Math.min(3.4, rad), 16, 0.04);
            }
            AABB box = event.getEntity().getBoundingBox().inflate(rad);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box)) {
                if (mob != event.getEntity() && mob.isAlive()) {
                    hurtNoCascade(mob, attackerDamageSource(player), event.getAmount() * mult);
                }
            }
        }

        // T306-FIX : fear vérifie réellement la présence de monstres proches (et non Y < 0 !)
        int fearLvl = getEnchantLvl(weapon, "deep_lucky_block:fear");
        if (fearLvl > 0) {
            boolean monstersNear = event.getEntity() instanceof net.minecraft.world.entity.monster.Monster
                    || !player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, player.getBoundingBox().inflate(8.0)).isEmpty();
            if (monstersNear && player.level().random.nextInt(100) < Math.min(75, 18 * fearLvl)) {
                event.setCanceled(true);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, true, true));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 0.8f, 0.55f);
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.SOUL, 8, 0.35, 0.02);
                return;
            }
        }

        // T306-OVERHAUL : berserk (PV manquants continus) séparé de desperate_kill (seuil critique <=35% PV)
        int berserkLvl = getEnchantLvl(weapon, "deep_lucky_block:berserk");
        if (berserkLvl > 0) {
            float missingHp = Math.max(0f, player.getMaxHealth() - player.getHealth());
            if (missingHp > 0.5f) {
                event.setAmount(event.getAmount() + (missingHp * 0.5f * berserkLvl));
                if (missingHp >= 6.0f) EnchantmentParticleFX.burstOnEntity(player, EnchantmentParticleFX.CRIMSON_BLOOD, 4, 0.3, 0.02);
            }
        }
        int executeLvl = getEnchantLvl(weapon, "deep_lucky_block:execute_chance");
        if (executeLvl > 0) {
            float targetHpPct = event.getEntity().getHealth() / Math.max(1f, event.getEntity().getMaxHealth());
            if (targetHpPct <= 0.35f && player.level().random.nextInt(100) < Math.min(90, 6 * executeLvl)) {
                event.setAmount(event.getEntity().getMaxHealth() * 10f);
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.FLASH, 1, 0.0, 0.0);
            }
        }
        int newEdgeLvl = getEnchantLvl(weapon, "deep_lucky_block:new_edge");
        if (newEdgeLvl > 0 && weapon.isDamageableItem()) {
            float durPct = (float)(weapon.getMaxDamage() - weapon.getDamageValue()) / weapon.getMaxDamage();
            event.setAmount(event.getAmount() * (1f + durPct * 0.18f * newEdgeLvl));
        }
        int comboLvl = getEnchantLvl(weapon, "deep_lucky_block:combo_slash");
        if (comboLvl > 0) {
            long now = System.currentTimeMillis();
            long last = LAST_ATTACK_TIME.getOrDefault(player.getUUID(), 0L);
            int stacks = COMBO_STACKS.getOrDefault(player.getUUID(), 0);
            stacks = (now - last <= 600L) ? Math.min(10 * comboLvl, stacks + 1) : 0;
            LAST_ATTACK_TIME.put(player.getUUID(), now);
            COMBO_STACKS.put(player.getUUID(), stacks);
            if (stacks > 0) event.setAmount(event.getAmount() * (1f + (0.2f * stacks)));
        }
        int sharpSharpLvl = getEnchantLvl(weapon, "deep_lucky_block:sharpened_sharpness");
        if (sharpSharpLvl > 0 && event.getEntity().getArmorValue() > 0) {
            event.setAmount(event.getAmount() + event.getAmount() * (0.10f * sharpSharpLvl));
        }
        if (bloodEdgeLvl > 0) event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 100, Math.min(3, bloodEdgeLvl - 1)));

        int poisonFangLvl = getEnchantLvl(weapon, "deep_lucky_block:poison_fang");
        if (poisonFangLvl > 0) {
            if (event.getEntity().hasEffect(MobEffects.POISON)) {
                event.setAmount(event.getAmount() + 1.5f * poisonFangLvl);
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.TOXIC_GREEN, 8, 0.25, 0.02);
            }
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 40 * poisonFangLvl, Math.min(2, poisonFangLvl - 1)));
        }
        int witherEdgeLvl = getEnchantLvl(weapon, "deep_lucky_block:wither_edge");
        if (witherEdgeLvl > 0) event.getEntity().addEffect(new MobEffectInstance(MobEffects.WITHER, 100, Math.min(3, witherEdgeLvl - 1)));
        int witherBladeLvl = getEnchantLvl(weapon, "deep_lucky_block:wither_blade");
        if (witherBladeLvl > 0 && event.getEntity().hasEffect(MobEffects.WITHER)) {
            float newDmg = event.getAmount() * 1.5f;
            if (event.getEntity().getArmorValue() > 0) newDmg *= 1.5f;
            event.setAmount(newDmg);
        }
        int promLvl = getEnchantLvl(weapon, "deep_lucky_block:promising_blade");
        if (promLvl > 0 && event.getEntity().getMaxHealth() >= 20f) {
            event.setAmount(event.getAmount() * (1f + 0.15f * promLvl));
        }

        // T310-FAMILIES : Famille Frappe Plongeante (I: low_damage_critical -> II: critical_strike -> III: damage_critical)
        int lowCritLvl = getEnchantLvl(weapon, "deep_lucky_block:low_damage_critical");
        int critStrikeLvl = getEnchantLvl(weapon, "deep_lucky_block:critical_strike");
        int dmgCritLvl = getEnchantLvl(weapon, "deep_lucky_block:damage_critical");
        if (critStrikeLvl > 0 || dmgCritLvl > 0 || lowCritLvl > 0) {
            if (player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()) {
                float diveScale = Math.min(0.65f, player.fallDistance * 0.06f);
                float critMult = 1.0f + diveScale + (0.12f * lowCritLvl) + (0.22f * critStrikeLvl) + (0.35f * dmgCritLvl);
                event.setAmount(event.getAmount() * critMult);
                if (critStrikeLvl > 0 && player.level() instanceof ServerLevel slDive2) {
                    EnchantmentParticleFX.shockwaveRing(slDive2, event.getEntity().getX(), event.getEntity().getY() + 0.1,
                            event.getEntity().getZ(), ParticleTypes.CLOUD, 1.3, 10, 0.04);
                }
                if (dmgCritLvl > 0) {
                    event.getEntity().setDeltaMovement(event.getEntity().getDeltaMovement().add(0, -0.32, 0));
                    EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.CRIT, 14, 0.35, 0.12);
                    if (player.level() instanceof ServerLevel slDive3) {
                        EnchantmentParticleFX.shockwaveRing(slDive3, event.getEntity().getX(), event.getEntity().getY() + 0.1,
                                event.getEntity().getZ(), EnchantmentParticleFX.EARTH_OCHRE, 2.0, 14, 0.06);
                        for (Mob nearMob : slDive3.getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(1.8))) {
                            if (nearMob != event.getEntity() && nearMob.isAlive()) {
                                hurtNoCascade(nearMob, player.damageSources().mobAttack(player), 2.0f * dmgCritLvl);
                            }
                        }
                    }
                }
            }
        }

        int blazeTraumaLvl = getEnchantLvl(weapon, "deep_lucky_block:blazing_trauma");
        int flintLvl = getEnchantLvl(weapon, "deep_lucky_block:flint_strike");
        if (flintLvl > 0) {
            if (!event.getEntity().isOnFire()) event.setAmount(event.getAmount() + 1.0f * flintLvl);
            event.getEntity().setRemainingFireTicks(Math.max(event.getEntity().getRemainingFireTicks(), 60 * flintLvl));
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.LAVA, 4, 0.2, 0.02);
        }
        if (blazeTraumaLvl > 0) {
            event.getEntity().setRemainingFireTicks(Math.max(event.getEntity().getRemainingFireTicks(), 60 * blazeTraumaLvl));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, Math.min(3, blazeTraumaLvl - 1)));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
        }

        int sweepEnh = getEnchantLvl(weapon, "deep_lucky_block:sweeping_enhancement");
        int sweepReinf = getEnchantLvl(weapon, "deep_lucky_block:reinforced_sweeping");
        int sweepStrike = getEnchantLvl(weapon, "deep_lucky_block:sweeping_strike");
        int sweepSlash = getEnchantLvl(weapon, "deep_lucky_block:sweeping_slash");
        int sweepFire = getEnchantLvl(weapon, "deep_lucky_block:sweeping_fire");
        int fierySweep = getEnchantLvl(weapon, "deep_lucky_block:fiery_sweep");
        int burnSlash = getEnchantLvl(weapon, "deep_lucky_block:sweeping_burn_slash");
        int soulSweep = getEnchantLvl(weapon, "deep_lucky_block:blazing_soul_sweep");

        if (sweepEnh > 0 || sweepReinf > 0) {
            double rad = sweepReinf > 0
                    ? deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(2.8, 0.35, sweepReinf)
                    : 2.5;
            float ratio = sweepReinf > 0 ? (0.45f + 0.15f * sweepReinf) : (0.25f + 0.10f * sweepEnh);
            if (sweepReinf > 0) EnchantmentParticleFX.slashArc(player, EnchantmentParticleFX.STEEL_SILVER, ParticleTypes.SWEEP_ATTACK, 2.2, 9);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(rad))) {
                if (mob != event.getEntity() && mob.isAlive()) {
                    hurtNoCascade(mob, attackerDamageSource(player), event.getAmount() * ratio);
                    if (sweepReinf > 0) mob.knockback(0.35f, player.getX() - mob.getX(), player.getZ() - mob.getZ());
                }
            }
        }
        if (sweepStrike > 0) {
            EnchantmentParticleFX.slashArc(player, ParticleTypes.CLOUD, ParticleTypes.CRIT, 2.3, 10);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(3.0))) {
                if (mob != event.getEntity() && mob.isAlive()) {
                    hurtNoCascade(mob, attackerDamageSource(player), event.getAmount() * (0.30f + 0.10f * sweepStrike));
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 1));
                }
            }
        }
        if (sweepSlash > 0) {
            EnchantmentParticleFX.slashArc(player, EnchantmentParticleFX.CRIMSON_BLOOD, EnchantmentParticleFX.SCARLET_MIST, 2.2, 10);
            float bleed = 1.5f * sweepSlash;
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(2.8))) {
                if (mob != event.getEntity() && mob.isAlive()) {
                    hurtNoCascade(mob, attackerDamageSource(player), event.getAmount() * 0.30f);
                    if (player.level() instanceof ServerLevel slb) {
                        final Mob bledMob = mob;
                        final LivingEntity atkRef = player;
                        TestProcedure.schedule(slb, TestProcedure.currentTick(slb) + 30, () -> {
                            if (bledMob.isAlive()) {
                                hurtNoCascade(bledMob, atkRef.damageSources().magic(), bleed);
                                EnchantmentParticleFX.burstOnEntity(bledMob, EnchantmentParticleFX.CRIMSON_BLOOD, 5, 0.2, 0.02);
                            }
                        });
                    }
                }
            }
        }
        if (sweepFire > 0 || fierySweep > 0) {
            double rad = fierySweep > 0 ? 3.5 : 2.5;
            int fireTicks = fierySweep > 0 ? (70 * fierySweep) : (40 * sweepFire);
            if (fierySweep > 0) EnchantmentParticleFX.slashArc(player, ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME, 2.4, 11);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(rad))) {
                if (mob != event.getEntity() && mob.isAlive()) {
                    mob.setRemainingFireTicks(Math.max(mob.getRemainingFireTicks(), fireTicks));
                    if (fierySweep > 0) hurtNoCascade(mob, player.damageSources().onFire(), 1.0f * fierySweep);
                }
            }
        }
        if (burnSlash > 0) {
            if (event.getEntity().isOnFire()) {
                event.setAmount(event.getAmount() * (1.0f + 0.22f * burnSlash));
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.LAVA, 8, 0.3, 0.04);
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(2.8))) {
                    if (mob != event.getEntity() && mob.isAlive()) {
                        mob.setRemainingFireTicks(60 * burnSlash);
                        hurtNoCascade(mob, player.damageSources().onFire(), 1.5f * burnSlash);
                    }
                }
            } else {
                event.getEntity().setRemainingFireTicks(40 * burnSlash);
            }
        }
        if (soulSweep > 0) {
            EnchantmentParticleFX.slashArc(player, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL, 2.4, 12);
            int soulsHit = 0;
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(3.0))) {
                if (mob.isAlive()) {
                    hurtNoCascade(mob, player.damageSources().magic(), 1.5f * soulSweep);
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                    soulsHit++;
                }
            }
            if (soulsHit > 0) player.heal(Math.min(3.0f, 0.5f * soulsHit));
        }

        int sprintImpHit = getArmorEnchantLvl(player, "deep_lucky_block:sprint_impact");
        if (sprintImpHit > 0 && player.isSprinting()) {
            event.setAmount(event.getAmount() + 1.5f * sprintImpHit);
            event.getEntity().knockback(0.25f * Math.min(4, sprintImpHit), player.getX() - event.getEntity().getX(), player.getZ() - event.getEntity().getZ());
            if (player.level() instanceof ServerLevel sls) {
                EnchantmentParticleFX.shockwaveRing(sls, event.getEntity().getX(), event.getEntity().getY() + 0.2,
                        event.getEntity().getZ(), ParticleTypes.CLOUD, 1.3, 10, 0.06);
            }
        }

        // T310-UNIQUE : last_stand_slayer (Brise-Agonie : brise l'Absorption, coupe la Régénération et punit < 35 % PV)
        int lastStandLvl = getEnchantLvl(weapon, "deep_lucky_block:last_stand_slayer");
        if (lastStandLvl > 0) {
            LivingEntity victim = event.getEntity();
            boolean hasShieldOrRegen = victim.getAbsorptionAmount() > 0f || victim.hasEffect(MobEffects.REGENERATION);
            if (hasShieldOrRegen) {
                victim.setAbsorptionAmount(0f);
                victim.removeEffect(MobEffects.REGENERATION);
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.CRIT, 8, 0.3, 0.08);
            }
            if (hasShieldOrRegen || victim.getHealth() < (victim.getMaxHealth() * 0.35f)) {
                event.setAmount(event.getAmount() * (1f + 0.28f * lastStandLvl));
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 7, 0.25, 0.03);
            }
        }
        int ambushLvl = getEnchantLvl(weapon, "deep_lucky_block:focused_ambush");
        if (ambushLvl > 0 && event.getEntity().getHealth() >= event.getEntity().getMaxHealth()) {
            event.setAmount(event.getAmount() * (1f + 0.20f * ambushLvl));
        }
        int pryLvl = getEnchantLvl(weapon, "deep_lucky_block:armor_pry");
        if (pryLvl > 0 && event.getEntity().getArmorValue() > 0) event.setAmount(event.getAmount() * (1f + 0.15f * pryLvl));
        int stealthLvl = getEnchantLvl(weapon, "deep_lucky_block:stealth_hunter");
        if (stealthLvl > 0) {
            if (player.hasEffect(MobEffects.INVISIBILITY)) {
                event.setAmount(event.getAmount() * 1.40f);
                player.removeEffect(MobEffects.INVISIBILITY);
            }
            if (event.getEntity().hasEffect(MobEffects.GLOWING)) event.setAmount(event.getAmount() * 1.50f);
        }
        int transMis = getEnchantLvl(weapon, "deep_lucky_block:transfer_misfortune");
        if (transMis > 0 && player.level().random.nextInt(100) < (10 * transMis)) {
            if (player.hasEffect(MobEffects.POISON)) { event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1)); player.removeEffect(MobEffects.POISON); }
            if (player.hasEffect(MobEffects.WITHER)) { event.getEntity().addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1)); player.removeEffect(MobEffects.WITHER); }
            if (player.hasEffect(MobEffects.WEAKNESS)) { event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1)); player.removeEffect(MobEffects.WEAKNESS); }
        }
        ItemStack gauntletChestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        int hwyWound = getEnchantLvl(gauntletChestplate, "deep_lucky_block:heavy_wound_punch_gauntlet");
        int emptyFist = getEnchantLvl(gauntletChestplate, "deep_lucky_block:empty_fist_gauntlet");
        int redStrike = getEnchantLvl(gauntletChestplate, "deep_lucky_block:red_strike_gauntlet");
        int tendon = getEnchantLvl(gauntletChestplate, "deep_lucky_block:tendon_break_gauntlet");
        int boneBrk = getEnchantLvl(gauntletChestplate, "deep_lucky_block:bone_break_gauntlet");
        int focPunch = getEnchantLvl(gauntletChestplate, "deep_lucky_block:focused_punch_gauntlet");
        int sprintPunch = getEnchantLvl(gauntletChestplate, "deep_lucky_block:sprint_punch_gauntlet");
        if (weapon.isEmpty()) {
            if (hwyWound > 0 && event.getEntity().getHealth() < event.getEntity().getMaxHealth() * 0.5f) event.setAmount(event.getAmount() * 2.0f);
            if (emptyFist > 0 && event.getEntity().getArmorValue() > 0) event.setAmount(event.getAmount() * (1f + 0.50f * emptyFist));
            if (redStrike > 0 && event.getEntity().getArmorValue() == 0) event.setAmount(event.getAmount() * (1f + 0.50f * redStrike));
            if (tendon > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 4));
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 4));
            }
            if (boneBrk > 0) hurtNoCascade(event.getEntity(), player.damageSources().magic(), 5.0f * boneBrk);
            if (focPunch > 0 && player.isShiftKeyDown()) event.setAmount(event.getAmount() * 3.0f);
            if (sprintPunch > 0 && player.isSprinting()) event.setAmount(event.getAmount() + 1.0f * sprintPunch);
        }
        int blzRage = getArmorEnchantLvl(player, "deep_lucky_block:blazing_rage");
        if (blzRage > 0 && player.isOnFire()) {
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.LAVA, 6, 0.3, 0.04);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, event.getEntity().getBoundingBox().inflate(2.5))) {
                if (mob.isAlive()) {
                    mob.setRemainingFireTicks(40 * blzRage);
                    hurtNoCascade(mob, player.damageSources().onFire(), 1.5f * blzRage);
                }
            }
        }
        int sturdy = getArmorEnchantLvl(player, "deep_lucky_block:sturdy_brute");
        if (sturdy > 0 && player.getHealth() > 20f) event.setAmount(event.getAmount() + (player.getHealth() - 20f) * 0.1f);
        int endCurse = getArmorEnchantLvl(player, "deep_lucky_block:end_curse");
        if (endCurse > 0) {
            boolean inEnd = player.level().dimension() == Level.END;
            event.setAmount(event.getAmount() * (inEnd ? 0.5f : 1.25f));
            if (inEnd) EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.PORTAL, 5, 0.25, 0.1);
        }
        int exhaust = getEnchantLvl(weapon, "deep_lucky_block:exhaustion");
        if (exhaust > 0 && player.getAirSupply() < 300) event.setAmount(event.getAmount() * 0.5f);
        int unCurse = getEnchantLvl(weapon, "deep_lucky_block:undead_curse");
        if (unCurse > 0) {
            if (event.getEntity().getType().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE)) event.setAmount(event.getAmount() * 0.4f);
            else if (event.getEntity() instanceof Player || event.getEntity() instanceof Villager || event.getEntity() instanceof Pillager) event.setAmount(event.getAmount() * 1.5f);
        }
        int ovCurse = getEnchantLvl(weapon, "deep_lucky_block:overworld_curse");
        int neCurse = getEnchantLvl(weapon, "deep_lucky_block:nether_curse");
        if (ovCurse > 0) {
            boolean inOw = player.level().dimension() == Level.OVERWORLD;
            event.setAmount(event.getAmount() * (inOw ? 0.5f : 1.3f));
            if (inOw) EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.ASH, 6, 0.25, 0.02);
        }
        if (neCurse > 0) {
            boolean inNether = player.level().dimension() == Level.NETHER;
            event.setAmount(event.getAmount() * (inNether ? 0.5f : 1.3f));
            if (inNether) {
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.ASH, 6, 0.25, 0.02);
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.SOUL_FIRE_FLAME, 3, 0.20, 0.01);
            }
        }
        int battleScar = getEnchantLvl(weapon, "deep_lucky_block:battle_scarred");
        if (battleScar > 0 && weapon.isDamageableItem()) {
            float lost = (float) weapon.getDamageValue() / weapon.getMaxDamage();
            event.setAmount(Math.max(0.0f, event.getAmount() * (1f - lost)));
            if (player.level() instanceof net.minecraft.server.level.ServerLevel slw) weapon.hurtAndBreak(2, slw, player, item -> {});
        }

        int lowCritRate = getEnchantLvl(weapon, "deep_lucky_block:low_crit_rate");
        int normalCritRate = getEnchantLvl(weapon, "deep_lucky_block:crit_rate");
        int focCrit = getEnchantLvl(weapon, "deep_lucky_block:focused_crit");
        int lowCritDmg = getEnchantLvl(weapon, "deep_lucky_block:low_crit_damage");
        int normCritDmg = getEnchantLvl(weapon, "deep_lucky_block:normal_crit_damage");
        int overCritDmg = getEnchantLvl(weapon, "deep_lucky_block:overclocked_crit");
        boolean vanillaCrit = player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater();
        boolean focusedSurgical = focCrit > 0 && player.isShiftKeyDown()
                && (!(player instanceof Player fp) || fp.getAttackStrengthScale(0.5f) >= 0.85f);
        boolean rolledCustomCrit = (lowCritRate > 0 || normalCritRate > 0)
                && player.level().random.nextInt(100) < Math.min(100, lowCritRate * 5 + normalCritRate * 10);
        boolean customCrit = focusedSurgical || rolledCustomCrit;
        if (vanillaCrit || customCrit) {
            if (customCrit || lowCritDmg > 0 || normCritDmg > 0 || overCritDmg > 0) {
                float base = vanillaCrit ? 1.0f : 1.5f;
                float critMult = base + (0.12f * lowCritDmg) + (0.25f * normCritDmg) + (0.75f * overCritDmg);
                event.setAmount(event.getAmount() * critMult);
            }
            if (focusedSurgical && event.getEntity().getArmorValue() > 0) {
                hurtNoCascade(event.getEntity(), player.damageSources().magic(), Math.min(16f, event.getAmount() * 0.20f));
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.FLASH, 1, 0.0, 0.0);
            }
            if (overCritDmg > 0) {
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.OVERCLOCK_CYAN, 10, 0.3, 0.05);
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.ELECTRIC_SPARK, 8, 0.3, 0.08);
            } else if (rolledCustomCrit) {
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.CRIT, 8, 0.25, 0.1);
            }

            int overcharge = getEnchantLvl(weapon, "deep_lucky_block:overcharge");
            if (overcharge > 0 && event.getEntity() instanceof LivingEntity primaryTarget
                    && player.level().random.nextInt(100) < (20 + 10 * overcharge)) {
                int thundercall = getEnchantLvl(weapon, "deep_lucky_block:thundercall_pact");
                boolean stormBoost = thundercall > 0 && player.level().isRaining();
                int maxJumps = 1 + overcharge + (stormBoost ? thundercall : 0);
                float chainDamage = (1.5f + 1.0f * overcharge) * (stormBoost ? (1f + 0.35f * thundercall) : 1f);
                LivingEntity current = primaryTarget;
                java.util.Set<LivingEntity> struck = new java.util.HashSet<>();
                struck.add(primaryTarget);
                for (int jump = 0; jump < maxJumps; jump++) {
                    LivingEntity next = null;
                    double best = Double.MAX_VALUE;
                    for (LivingEntity candidate : player.level().getEntitiesOfClass(
                            LivingEntity.class, current.getBoundingBox().inflate(4.0))) {
                        if (candidate == player || struck.contains(candidate) || !candidate.isAlive()) continue;
                        double distSqr = candidate.distanceToSqr(current);
                        if (distSqr < best) { best = distSqr; next = candidate; }
                    }
                    if (next == null) break;
                    struck.add(next);
                    hurtNoCascade(next, player.damageSources().lightningBolt(), chainDamage);
                    if (next.level() instanceof ServerLevel chainLevel) {
                        EnchantmentParticleFX.tetherBeam(chainLevel,
                                current.position().add(0, current.getBbHeight() * 0.5, 0),
                                next.position().add(0, next.getBbHeight() * 0.5, 0),
                                ParticleTypes.ELECTRIC_SPARK, EnchantmentParticleFX.OVERCLOCK_CYAN, 0.55);
                    }
                    current = next;
                }
            }
        }

        // TITAN'S GRASP (Earth Heart, arme) : degats de melee ecrasants,
        // contrebalances par un malus de vitesse d'attaque applique en
        // continu (voir EquipmentExpansionHandler#updateHeartEnchantmentPassives)
        // -- le colosse lent et inarretable, trope manhwa classique.
        int titansGrasp = getEnchantLvl(weapon, "deep_lucky_block:titans_grasp");
        if (titansGrasp > 0) {
            event.setAmount(event.getAmount() * (1f + 0.30f * titansGrasp));
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.EARTH_OCHRE, 6, 0.28, 0.02);
        }

        // T307-FX : swift_strike déclenche son entaille hypersonique cyan à l'instant exact de la frappe !
        int swiftHitLvl = getEnchantLvl(weapon, "deep_lucky_block:swift_strike");
        if (swiftHitLvl > 0) {
            EnchantmentParticleFX.slashArc(player, EnchantmentParticleFX.OVERCLOCK_CYAN, ParticleTypes.ELECTRIC_SPARK, 1.85, 9);
        }

        // SERAPH'S JUDGMENT (Light Heart, arme) : chatiment severe contre
        // les morts-vivants, mais lumiere qui "brule moins" tout le reste --
        // specialisation stricte facon "arme sacree anti-mort-vivant".
        int seraphsJudgment = getEnchantLvl(weapon, "deep_lucky_block:seraphs_judgment");
        if (seraphsJudgment > 0) {
            // VERIFIE (minecraft.wiki/w/Entity_type_tag_(Java_Edition)) :
            // #minecraft:sensitive_to_smite est le tag vanilla exact utilise
            // par l'enchantement Smite pour reperer les cibles "mort-vivant"
            // (contient #minecraft:undead = squelettes + zombies + phantom +
            // wither). On reutilise ce meme tag plutot que de deviner une
            // API -- garantie de coherence avec le comportement vanilla.
            boolean undead = event.getEntity().getType().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE);
            if (undead) {
                event.setAmount(event.getAmount() * (1f + 0.50f * seraphsJudgment));
                if (event.getEntity().level() instanceof ServerLevel judgmentLevel) {
                    judgmentLevel.sendParticles(ParticleTypes.END_ROD,
                            event.getEntity().getX(), event.getEntity().getY() + event.getEntity().getBbHeight() * 0.5, event.getEntity().getZ(),
                            10, 0.3, 0.4, 0.3, 0.02);
                }
            } else {
                event.setAmount(event.getAmount() * Math.max(0.35f, 1f - 0.15f * seraphsJudgment));
            }
        }

        // VOIDLING'S HUNGER (Shadow Heart, casque, MAUDIT) : vol de vie
        // renforce la nuit -- "creature des tenebres" qui se nourrit de
        // l'obscurite. La saignee passive en plein jour (le vrai cote
        // maudit) est geree en tick (EquipmentExpansionHandler#tickHeartEnchantmentPeriodics).
        int voidlingsHunger = getArmorEnchantLvl(player, "deep_lucky_block:voidlings_hunger");
        if (voidlingsHunger > 0) {
            boolean nightTime = !player.level().isDay();
            if (nightTime) {
                float healAmount = event.getAmount() * (0.15f + 0.10f * voidlingsHunger);
                player.heal(healAmount);
                if (player.level() instanceof ServerLevel voidLevel) {
                    voidLevel.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                            6, 0.25, 0.3, 0.25, 0.01);
                }
            }
        }

        int sickDmg = getEnchantLvl(weapon, "deep_lucky_block:sickle_damage");
        int sickSlash = getEnchantLvl(weapon, "deep_lucky_block:sickle_slash");
        if (sickDmg > 0 || sickSlash > 0) {
            event.setAmount((event.getAmount() + 3.0f * sickDmg) * (1f + 0.80f * sickSlash));
        }
        int soulSac = getEnchantLvl(weapon, "deep_lucky_block:soul_sacrifice_slash");
        if (soulSac > 0 && player.getHealth() > (player.getMaxHealth() * 0.40f)) {
            player.setHealth(player.getHealth() - (player.getMaxHealth() * 0.40f));
            event.setAmount(event.getAmount() + (event.getEntity().getMaxHealth() * 0.10f));
        }
        // T310-UNIQUE : execute_power (Faux de la Faucheuse : fauchage d'âme sur cibles affaiblies + soin + Célérité II)
        int execPwr = getEnchantLvl(weapon, "deep_lucky_block:execute_power");
        if (execPwr > 0 && event.getEntity().getHealth() < (event.getEntity().getMaxHealth() * Math.min(0.45f, 0.10f + 0.05f * execPwr))) {
            if (event.getEntity().getMaxHealth() < 100f) {
                event.setAmount(event.getEntity().getMaxHealth() * 10f);
            } else {
                event.setAmount(event.getAmount() + 8.0f * execPwr);
            }
            player.heal(2.0f * execPwr);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, true, false));
            EnchantmentParticleFX.slashArc(player, ParticleTypes.SCULK_SOUL, EnchantmentParticleFX.CRIMSON_BLOOD, 2.1, 10);
        }
        int disarmLvl = getEnchantLvl(weapon, "deep_lucky_block:disarm");
        if (disarmLvl > 0 && event.getEntity() instanceof Mob mob && !mob.getMainHandItem().isEmpty() && player.level().random.nextInt(100) < (10 * disarmLvl)) {
            mob.spawnAtLocation(mob.getMainHandItem());
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        int pebble = getArmorEnchantLvl(player, "deep_lucky_block:pebblekin");
        if (pebble > 0 && event.getSource().getMsgId().equals("teleport")) event.setCanceled(true);
        int reachGaunt = getEnchantLvl(gauntletChestplate, "deep_lucky_block:straight_reach_gauntlet");
        int blzBat = getArmorEnchantLvl(player, "deep_lucky_block:blaze_battle");
        if (reachGaunt > 0 && weapon.isEmpty()) event.setAmount(event.getAmount() + 1.5f * reachGaunt);
        if (blzBat > 0 && player.isOnFire()) {
            event.setAmount(event.getAmount() * (1f + 0.20f * blzBat));
            event.getEntity().setRemainingFireTicks(Math.max(event.getEntity().getRemainingFireTicks(), 60 * blzBat));
        }
        // T306-FIX : short_reach est désormais un vrai AttributeModifier négatif (-1 dégât & -0.35 portée/niv)
        // dans RealAttributeModifierHandler -> pas de double soustraction ici.

        // Rythme Martial : cadence personnelle à chaque coup enchaîné
        int marRhyHit = getArmorEnchantLvl(player, "deep_lucky_block:martial_rhythm");
        if (marRhyHit > 0) {
            long nowTick = player.level().getGameTime();
            long exp = player.getPersistentData().getLong("slb_martial_until");
            int st = nowTick <= exp ? Math.min(marRhyHit, player.getPersistentData().getInt("slb_martial_stacks") + 1) : 1;
            player.getPersistentData().putInt("slb_martial_stacks", st);
            player.getPersistentData().putLong("slb_martial_until", nowTick + 90);
            event.setAmount(event.getAmount() * (1.0f + 0.07f * st));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 70, Math.min(3, st - 1), true, false));
        }

        int transfLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_transfusion");
        if (transfLvl > 0) {
            event.setAmount(0.0f);
            event.getEntity().heal(1.0f * transfLvl);
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.HEART, 3, 0.25, 0.02);
            return;
        }
        // T306-OVERHAUL : unbreakable (0 usure absolue) vs eternal (immortel à >=1 durabilité + conservé à la mort)
        if (weapon.isDamageableItem()) {
            if (getEnchantLvl(weapon, "deep_lucky_block:unbreakable") > 0) {
                weapon.setDamageValue(0);
            } else if (getEnchantLvl(weapon, "deep_lucky_block:eternal") > 0 && weapon.getDamageValue() >= weapon.getMaxDamage() - 1) {
                weapon.setDamageValue(Math.max(0, weapon.getMaxDamage() - 2));
            }
        }
        // T306-OVERHAUL : beheading (Coupe-Tête : +12%/niv sur coup chargé + Force à l'élimination)
        // tandis que decapitation (Décapitation : chasseur de crânes) tombe proprement à la mort !
        int beheadLvl = getEnchantLvl(weapon, "deep_lucky_block:beheading");
        if (beheadLvl > 0) {
            boolean fullSwing = !(player instanceof Player bp) || bp.getAttackStrengthScale(0.5f) >= 0.85f;
            if (fullSwing) {
                event.setAmount(event.getAmount() * (1.0f + 0.12f * beheadLvl));
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.CRIMSON_BLOOD, 5, 0.2, 0.02);
            }
        }

        // Chasse de l'Ombre / Sens du Meurtre / Sixième Sens
        int shadowLvl = getEnchantLvl(weapon, "deep_lucky_block:shadow_hunt");
        int killLvl = getEnchantLvl(weapon, "deep_lucky_block:killing_sense");
        int senseLvl = getEnchantLvl(weapon, "deep_lucky_block:sense");
        if (shadowLvl > 0 && player.level().getMaxLocalRawBrightness(player.blockPosition()) < 7) {
            event.setAmount(event.getAmount() * (1f + 0.18f * shadowLvl));
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.SHADOW_VIOLET, 7, 0.26, 0.02);
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.SQUID_INK, 4, 0.22, 0.01);
        }
        // T310-UNIQUE : killing_sense (Flair du Prédateur : marque la proie blessée < 40 % PV, saignement + Vitesse II de traque)
        if (killLvl > 0 && event.getEntity().getHealth() < (event.getEntity().getMaxHealth() * 0.40f)) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, false));
            hurtNoCascade(event.getEntity(), player.damageSources().magic(), 1.5f * killLvl);
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.CRIMSON_BLOOD, 6, 0.25, 0.02);
        }
        if (senseLvl > 0) {
            event.setAmount(event.getAmount() + (1.0f * senseLvl));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
        }

        // T306-OVERHAUL : weakness_wound (plaie durable anti-soin) vs weakness_trauma (choc commotionnel intense)
        // et poison_scar (toxine acide augmentant les dégâts subis)
        int weakWound = getEnchantLvl(weapon, "deep_lucky_block:weakness_wound");
        int weakTrauma = getEnchantLvl(weapon, "deep_lucky_block:weakness_trauma");
        int poisonScar = getEnchantLvl(weapon, "deep_lucky_block:poison_scar");
        if (weakWound > 0) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 + 40 * weakWound, 0));
            event.getEntity().getPersistentData().putLong("slb_weak_wound_until", player.level().getGameTime() + 60L + 40L * weakWound);
        }
        if (weakTrauma > 0) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 55, Math.min(3, weakTrauma)));
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.CRIT, 6, 0.25, 0.08);
        }
        if (poisonScar > 0) {
            if (event.getEntity().getPersistentData().getLong("slb_poison_scar_until") > player.level().getGameTime()) {
                event.setAmount(event.getAmount() * (1.0f + 0.08f * poisonScar));
            }
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 100, Math.min(2, poisonScar - 1)));
            event.getEntity().getPersistentData().putLong("slb_poison_scar_until", player.level().getGameTime() + 100L);
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), EnchantmentParticleFX.TOXIC_GREEN, 5, 0.2, 0.01);
        }

        // Coup du Désespoir (seuil critique <=35% PV) & Frappe Nourricière
        int despLvl = getEnchantLvl(weapon, "deep_lucky_block:desperate_kill");
        int foodLvl = getEnchantLvl(weapon, "deep_lucky_block:food_power_strike");
        if (despLvl > 0 && player.getHealth() <= (player.getMaxHealth() * 0.35f)) {
            event.setAmount(event.getAmount() * (1f + 0.35f * despLvl));
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.ANGRY_VILLAGER, 3, 0.3, 0.02);
        }
        if (foodLvl > 0 && player instanceof Player foodPlayer && foodPlayer.getFoodData().getFoodLevel() >= 18) {
            event.setAmount(event.getAmount() * (1f + 0.20f * foodLvl));
        }

        // T306-OVERHAUL : devour (soin léger + festin à l'élimination) vs leech (sangsue parasitaire sur 3s !)
        int devLvl = getEnchantLvl(weapon, "deep_lucky_block:devour");
        int leechLvl = getEnchantLvl(weapon, "deep_lucky_block:leech");
        if (devLvl > 0) {
            player.heal(event.getAmount() * 0.08f * devLvl);
        }
        if (leechLvl > 0 && player.level() instanceof ServerLevel slLeech) {
            final LivingEntity leechTarget = event.getEntity();
            final LivingEntity leechOwner = player;
            final float tickDrain = 1.0f * leechLvl;
            for (int step = 1; step <= 3; step++) {
                final int d = step * 20;
                TestProcedure.schedule(slLeech, TestProcedure.currentTick(slLeech) + d, () -> {
                    if (leechTarget.isAlive() && leechOwner.isAlive()) {
                        hurtNoCascade(leechTarget, leechOwner.damageSources().magic(), tickDrain);
                        leechOwner.heal(tickDrain);
                        EnchantmentParticleFX.tetherBeam(slLeech,
                                leechTarget.position().add(0, leechTarget.getBbHeight() * 0.5, 0),
                                leechOwner.position().add(0, leechOwner.getBbHeight() * 0.5, 0),
                                EnchantmentParticleFX.CRIMSON_BLOOD, EnchantmentParticleFX.SCARLET_MIST, 0.7);
                    }
                });
            }
        }

        // T306-FIX : Gantelet Direct uniquement main vide (weapon.isEmpty()) !
        int gauntLvl = getEnchantLvl(gauntletChestplate, "deep_lucky_block:straight_punch_gauntlet");
        if (gauntLvl > 0 && weapon.isEmpty()) {
            event.setAmount(event.getAmount() + (3.0f * gauntLvl));
            EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.CRIT, 5, 0.2, 0.05);
        }

        // T310-UNIQUE : solo_combat (Maître du Duel : 1 contre 1 strict dans 7 blocs) vs lone_wolf (Loup Solitaire : sans joueur allié dans 16 blocs)
        int soloLvl = getEnchantLvl(weapon, "deep_lucky_block:solo_combat");
        int loneLvl = getEnchantLvl(weapon, "deep_lucky_block:lone_wolf");
        int massLvl = getEnchantLvl(weapon, "deep_lucky_block:mass_slaughter");
        if (soloLvl > 0) {
            boolean strictOneVsOne = player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(7.0),
                    e2 -> e2 != player && e2 != event.getEntity() && e2.isAlive() && (e2 instanceof Mob || e2 instanceof Player)).isEmpty();
            if (strictOneVsOne) {
                event.setAmount(event.getAmount() * (1f + 0.22f * soloLvl));
                player.getPersistentData().putUUID("slb_duel_partner", event.getEntity().getUUID());
                player.getPersistentData().putLong("slb_duel_until", player.level().getGameTime() + 80L);
                EnchantmentParticleFX.burstOnEntity(event.getEntity(), ParticleTypes.ENCHANTED_HIT, 6, 0.25, 0.10);
            }
        }
        if (loneLvl > 0) {
            boolean noAllies = player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(16.0), p2 -> p2 != player).isEmpty();
            if (noAllies) {
                float nightBonus = !player.level().isDay() ? 0.12f : 0.0f;
                event.setAmount(event.getAmount() * (1f + 0.15f * loneLvl + nightBonus));
            }
        }
        // T310-UNIQUE : burst_step (Impulsion de Mêlée sur Bottes : bond d'assaut + Vitesse II en portant un coup)
        int burstAtkLvl = getArmorEnchantLvl(player, "deep_lucky_block:burst_step");
        if (burstAtkLvl > 0) {
            long nowB = player.level().getGameTime();
            if (nowB >= player.getPersistentData().getLong("slb_burst_ready")) {
                net.minecraft.world.phys.Vec3 lk = player.getLookAngle();
                double f = 0.36 + 0.13 * burstAtkLvl;
                player.setDeltaMovement(player.getDeltaMovement().add(lk.x * f, 0.08, lk.z * f));
                player.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 1, true, false));
                player.getPersistentData().putLong("slb_burst_ready", nowB + Math.max(35L, 70L - 10L * burstAtkLvl));
                EnchantmentParticleFX.ankleTrail(player, ParticleTypes.ELECTRIC_SPARK, 6, 0.06);
            }
        }
        if (massLvl > 0) {
            int mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6.0)).size();
            if (mobs >= 3) event.setAmount(event.getAmount() * (1f + 0.10f * massLvl * Math.min(8, mobs)));
        }

        // T306-OVERHAUL : heavy_slow_hit (impact plombé immédiat) vs slow_heavy_weakness (fardeau empilable jusqu'à 4x)
        int heavySlowHit = getEnchantLvl(weapon, "deep_lucky_block:heavy_slow_hit");
        int slowHvyWeak = getEnchantLvl(weapon, "deep_lucky_block:slow_heavy_weakness");
        if (heavySlowHit > 0) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, Math.min(3, heavySlowHit)));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, Math.min(2, heavySlowHit - 1)));
            event.getEntity().setDeltaMovement(event.getEntity().getDeltaMovement().multiply(0.35, 1.0, 0.35).add(0, -0.15, 0));
        }
        if (slowHvyWeak > 0) {
            long gTime = player.level().getGameTime();
            int stacks = gTime <= event.getEntity().getPersistentData().getLong("slb_burden_until")
                    ? Math.min(4, event.getEntity().getPersistentData().getInt("slb_burden_stacks") + 1) : 1;
            event.getEntity().getPersistentData().putInt("slb_burden_stacks", stacks);
            event.getEntity().getPersistentData().putLong("slb_burden_until", gTime + 100L);
            event.setAmount(event.getAmount() * (1.0f + 0.05f * slowHvyWeak * stacks));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, Math.min(3, stacks - 1)));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 80, Math.min(2, stacks - 1)));
        }

        // T306-FIX : nether_corrosion et end_corrosion sont des MALÉDICTIONS d'usure dimensionnelle
        // (gérées désormais dans handlePlayerTick), plus des buffs de dégâts inversés !

        int tremorLvl = getEnchantLvl(weapon, "deep_lucky_block:curse_of_tremor");
        if (tremorLvl > 0 && player.level().random.nextInt(100) < 15) {
            player.spawnAtLocation(weapon.copy());
            weapon.setCount(0);
        }
    }

    // 2. Ticks Entite (mob-equipment-enchant generalization : cette
    // methode tournait auparavant sur PlayerTickEvent.Post, donc AUCUN de
    // ces passifs d'armure/enchantement de tick (Gale, Momentum,
    // Trailblazer, King, Leader, Priest's Blessing, vitesse empilee,
    // etc.) ne s'appliquait a un MOB portant le meme equipement enchante.
    // EntityTickEvent.Post est declenche pour TOUTE LivingEntity -- on
    // filtre nous-memes ci-dessous, ce qui generalise tous ces effets aux
    // mobs, tout en gardant les volets intrinsequement lies au joueur
    // (inventaire complet, faim...) derriere des "instanceof Player"
    // explicites plus bas (Assimilation, Perception ESP, Fire Devour).
    private static final EquipmentSlot[] ALL_EQUIP_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private static final Set<Long> ACTIVE_ENCHANTED_ENTITIES = ConcurrentHashMap.newKeySet();

    /**
     * T321-ANTI-LAG : Test O(1) ultra-rapide (6 slots, zéro allocation, zéro NBT attaché)
     * permettant d'ignorer instantanément tous les mobs non enchantés (vaches, poissons, zombies nus...)
     * sans exécuter des centaines de recherches d'enchantements et d'attributs par tick !
     */
    public static boolean hasAnyEnchantedEquipment(LivingEntity entity) {
        if (entity == null) return false;
        for (EquipmentSlot slot : ALL_EQUIP_SLOTS) {
            ItemStack st = entity.getItemBySlot(slot);
            if (st != null && !st.isEmpty() && st.isEnchanted()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(EntityTickEvent.Post event){try{onPlayerTickImpl(event);}catch(Throwable t__){guard("onPlayerTick",t__);}}
    private static void onPlayerTickImpl(EntityTickEvent.Post event){
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!(living instanceof Player)) {
            long sideKey = ((living.level().isClientSide() ? 1L : 0L) << 32) | (living.getId() & 0xFFFFFFFFL);
            if (!hasAnyEnchantedEquipment(living)) {
                if (!ACTIVE_ENCHANTED_ENTITIES.remove(sideKey)) {
                    return;
                }
            } else {
                if (ACTIVE_ENCHANTED_ENTITIES.size() > 1024) ACTIVE_ENCHANTED_ENTITIES.clear();
                ACTIVE_ENCHANTED_ENTITIES.add(sideKey);
            }
        }
        // T307-PHYSICS : Mouvements client-authoritative (Légèreté poulet, Coureur Mural miel,
        // Glissade soutenue, Bondissant slime, Pas de Géant, Coussin de nuages) exécutés des DEUX côtés !
        try {
            handleSharedClientServerMovement(living);
        } catch (Throwable t) {
            printHandlerError("handleSharedClientServerMovement", t);
        }
        if (living.level().isClientSide()) return;
        try {
            handlePlayerTick(living);
        } catch (Throwable t) {
            printHandlerError("onPlayerTick", t);
        }
    }

    private static void handlePlayerTick(LivingEntity player) {

        // 7. Saut Renforcé (leap_power impulsion avant au saut) + Nuage sous les pieds au saut (Gale est désormais une Bourrasque Ascendante en plein air !)
        try {
            boolean slbUgWasGround = player.getPersistentData().getBoolean("slb_ug_was_ground");
            player.getPersistentData().putBoolean("slb_ug_was_ground", player.onGround());
            if (!player.onGround() && slbUgWasGround && player.getDeltaMovement().y > 0.0) {
                if (player.level() instanceof ServerLevel slJumpCloud) {
                    int cwJump = getArmorEnchantLvl(player, "deep_lucky_block:cloud_walker");
                    int fpJump = getArmorEnchantLvl(player, "deep_lucky_block:foot_placement");
                    int csJump = getArmorEnchantLvl(player, "deep_lucky_block:cloud_stride");
                    if (cwJump > 0 || fpJump > 0 || csJump > 0) {
                        EnchantmentParticleFX.cloudCushion(slJumpCloud, player.getX(), player.getY() + 0.02, player.getZ(), 0.78, cwJump > 0);
                    }
                }
                int leapUpgrade = getArmorEnchantLvl(player, "deep_lucky_block:leap_power");
                if (leapUpgrade > 0) {
                    if (player.isSprinting()) {
                        net.minecraft.world.phys.Vec3 lk = player.getLookAngle();
                        player.setDeltaMovement(player.getDeltaMovement().add(lk.x * 0.10 * leapUpgrade, 0.03 * leapUpgrade, lk.z * 0.10 * leapUpgrade));
                        player.hurtMarked = true;
                    }
                    if (player.level() instanceof ServerLevel sll) {
                        EnchantmentParticleFX.shockwaveRing(sll, player.getX(), player.getY() + 0.08, player.getZ(), ParticleTypes.CLOUD, 0.85, 10, 0.03);
                    }
                }
                int highAlt = getArmorEnchantLvl(player, "deep_lucky_block:high_altitude_hypoxia");
                if (highAlt > 0 && player.getY() > 100) {
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.06, 0));
                }
            }
        } catch (Throwable t) {
            printHandlerError("onPlayerJump", t);
        }

        // T306-FIX : weeding_hoe ne détruit PLUS JAMAIS les blocs d'herbe (GRASS_BLOCK) ! Uniquement mauvaises herbes / fougères / buissons secs.
        int weedHoe = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:weeding_hoe");
        if (weedHoe > 0 && player.tickCount % 30 == 0) {
            BlockPos c = player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -1, -4), c.offset(4, 2, 4))) {
                BlockState ws = player.level().getBlockState(p);
                if (ws.is(Blocks.SHORT_GRASS) || ws.is(Blocks.TALL_GRASS) || ws.is(Blocks.FERN)
                        || ws.is(Blocks.LARGE_FERN) || ws.is(Blocks.DEAD_BUSH)) {
                    player.level().destroyBlock(p, true, player);
                }
            }
        }
        // T306-FIX : farmers_hoe ne récolte QUE les cultures arrivées à maturité (isMaxAge) !
        int farmHoe = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:farmers_hoe");
        if (farmHoe > 0 && player.tickCount % 20 == 0) {
            BlockPos c = player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
                BlockState st = player.level().getBlockState(p);
                if (st.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(st)) {
                    player.level().destroyBlock(p, true, player);
                }
            }
        }

        int rainHeal = getArmorEnchantLvl(player, "deep_lucky_block:rain_nourishment");
        if (rainHeal > 0 && player.level().isRaining() && player.tickCount % 60 == 0) {
            player.heal(1.0f * rainHeal);
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.HAPPY_VILLAGER, 4, 0.3, 0.02);
        }
        // T306-FIX : king scale désormais avec son niveau (1..3) (couronne dorée continue côté client : CosmeticAuraClientTicker)
        int kingLvl = getArmorEnchantLvl(player, "deep_lucky_block:king");
        if (kingLvl > 0 && player.tickCount % 200 == 0) {
            player.heal(0.5f * kingLvl);
            for (Player ally : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10.0))) {
                if (ally != player) {
                    ally.heal(1.0f * kingLvl);
                    EnchantmentParticleFX.burstOnEntity(ally, ParticleTypes.HEART, 2, 0.25, 0.02);
                }
            }
        }
        int leaderLvl = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:leader");
        if (leaderLvl > 0 && player.tickCount % 20 == 0) {
            int amplifier = Math.max(0, leaderLvl - 1);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amplifier, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, amplifier, true, false));
            for (Player ally : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10.0))) {
                if (ally != player && player.isAlliedTo(ally)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amplifier, true, false));
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, amplifier, true, false));
                }
            }
        }
        int priestLvl = getArmorEnchantLvl(player, "deep_lucky_block:priests_blessing");
        if (priestLvl > 0 && player.tickCount % 20 == 0) {
            player.removeEffect(MobEffects.POISON); player.removeEffect(MobEffects.WITHER);
            player.removeEffect(MobEffects.WEAKNESS); player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        int nightVeil = getArmorEnchantLvl(player, "deep_lucky_block:night_veil");
        if (nightVeil > 0 && player.isShiftKeyDown()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, true, false));
            if (player.tickCount % 20 == 0) EnchantmentParticleFX.ankleTrail(player, ParticleTypes.SMOKE, 2, 0.01);
        }
        int heartbeat = getArmorEnchantLvl(player, "deep_lucky_block:heartbeat_rhythm");
        if (heartbeat > 0 && player.tickCount % 20 == 0) player.heal(player.getMaxHealth() * 0.01f * heartbeat);

        // T306-OVERHAUL : item_attraction (aspiration instantanée accroupi) vs magnetic (glissement magnétique fluide passif)
        int itemAttr = getArmorEnchantLvl(player, "deep_lucky_block:item_attraction");
        if (itemAttr > 0 && player.isShiftKeyDown() && player.tickCount % 5 == 0 && player instanceof Player attrPlayer) {
            for (net.minecraft.world.entity.item.ItemEntity ie : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(2.5 * itemAttr))) {
                ie.playerTouch(attrPlayer);
            }
        }
        int magLvl = getArmorEnchantLvl(player, "deep_lucky_block:magnetic");
        if (magLvl > 0 && player.tickCount % 4 == 0 && player instanceof Player magPlayer) {
            for (net.minecraft.world.entity.item.ItemEntity ie : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(6.0))) {
                net.minecraft.world.phys.Vec3 pull = magPlayer.position().add(0, 0.5, 0).subtract(ie.position());
                double dist = pull.length();
                if (dist < 1.3) ie.playerTouch(magPlayer);
                else {
                    ie.setDeltaMovement(ie.getDeltaMovement().scale(0.65).add(pull.normalize().scale(0.18)));
                    ie.hurtMarked = true;
                }
            }
        }

        // T306-OVERHAUL : dread_aura (terreur noire : faiblesse + recul d'effroi ; aura sombre continue côté client : CosmeticAuraClientTicker)
        int dreadAura = getArmorEnchantLvl(player, "deep_lucky_block:dread_aura");
        if (dreadAura > 0 && player.tickCount % 40 == 0) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0))) {
                if (mob.getMaxHealth() <= 60f && mob.getTarget() == player) mob.setTarget(null);
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                net.minecraft.world.phys.Vec3 away = mob.position().subtract(player.position());
                if (away.horizontalDistanceSqr() > 0.01) {
                    net.minecraft.world.phys.Vec3 an = away.normalize();
                    mob.setDeltaMovement(mob.getDeltaMovement().add(an.x * 0.14, 0.02, an.z * 0.14));
                }
            }
        }
        int phantomAv = getArmorEnchantLvl(player, "deep_lucky_block:phantom_bee_vex_aversion");
        if (phantomAv > 0 && player.tickCount % 15 == 0) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0))) {
                boolean isPest = mob instanceof net.minecraft.world.entity.monster.Phantom
                        || mob instanceof net.minecraft.world.entity.animal.Bee
                        || mob instanceof net.minecraft.world.entity.monster.Vex
                        || mob instanceof net.minecraft.world.entity.monster.Silverfish
                        || mob instanceof net.minecraft.world.entity.monster.Endermite;
                if (isPest) {
                    if (mob.getTarget() == player) mob.setTarget(null);
                    net.minecraft.world.phys.Vec3 away = mob.position().subtract(player.position());
                    if (away.lengthSqr() < 25.0 && away.lengthSqr() > 0.01) {
                        mob.setDeltaMovement(away.normalize().scale(0.45));
                        mob.hurtMarked = true;
                        if (away.lengthSqr() < 9.0) hurtNoCascade(mob, player.damageSources().magic(), 2.0f);
                    }
                }
            }
        }

        int thirsty = getArmorEnchantLvl(player, "deep_lucky_block:thirsty_heart");
        if (thirsty > 0 && player.tickCount % 20 == 0) {
            boolean cleared = false;
            BlockPos c = player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(3, 1, 3))) {
                if (player.level().getBlockState(p).is(Blocks.WATER)) { player.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()); cleared = true; }
            }
            if (cleared) player.heal(1.0f);
        }
        int conduit = getArmorEnchantLvl(player, "deep_lucky_block:conduit_heart");
        if (conduit > 0 && (player.isInWater() || player.level().isRaining())) {
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 40, 0, true, false));
            if (player.tickCount % 20 == 0 && player.isInWater()) EnchantmentParticleFX.ankleTrail(player, ParticleTypes.NAUTILUS, 3, 0.05);
        }
        // T307-BARRIER-TRAIL + T319-AIRWALK-JUMP : cloud_stride pose une piste temporaire de Blocks.BARRIER
        // en haute altitude (Y >= 160) qui reste stable même en sprint-saut aérien, nettoyée à >= 4 blocs derrière !
        int cloudStride = getArmorEnchantLvl(player, "deep_lucky_block:cloud_stride");
        int footPlCheck = getArmorEnchantLvl(player, "deep_lucky_block:foot_placement");
        if (cloudStride > 0 && footPlCheck <= 0) {
            if (player.isShiftKeyDown()) {
                resetAirWalkAnchor(player);
                clearPlayerBarriersUnderneath(player);
            } else if (player.getY() >= 160.0) {
                maintainAirWalkRunway(player, 159);
            } else {
                resetAirWalkAnchor(player);
            }
        }
        // T307-PHYSICS : lightness & basic_lightness sont désormais gérés côté Client+Serveur dans handleSharedClientServerMovement
        // (vraie chute planée lente façon poulet + particules plume/nuage sous les pieds).
        if (!player.onGround() && !player.isShiftKeyDown() && player.getDeltaMovement().y < -0.02) {
            int lightSlow = getArmorEnchantLvl(player, "deep_lucky_block:lightness");
            int basicLightSlow = getArmorEnchantLvl(player, "deep_lucky_block:basic_lightness");
            if (lightSlow > 0 && player.tickCount % 7 == 0) {
                EnchantmentParticleFX.ankleTrail(player, ParticleTypes.CLOUD, 2, 0.012);
            } else if (basicLightSlow > 0 && player.tickCount % 12 == 0) {
                EnchantmentParticleFX.ankleTrail(player, ParticleTypes.CLOUD, 1, 0.01);
            }
        }

        // T306-FIX : last_stand_escape (<25% PV -> vitesse continue + furtivité d'urgence) séparé de panic_escape (sur coup subi !)
        int lastStandEsc = getArmorEnchantLvl(player, "deep_lucky_block:last_stand_escape");
        boolean nearDeath = lastStandEsc > 0 && player.getHealth() <= player.getMaxHealth() * 0.25f;
        setStackingMovementBonus(player, PANIC_SPEED_ID, nearDeath ? 0.25 : 0.0);
        if (nearDeath) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            long escReady = player.getPersistentData().getLong("slb_last_esc_ready");
            if (player.level().getGameTime() >= escReady) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false));
                player.getPersistentData().putLong("slb_last_esc_ready", player.level().getGameTime() + 240L);
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.LARGE_SMOKE, 10, 0.35, 0.03);
            }
        }

        // Famille Pieds Malodorants (stinky_feet -> super_stinky_feet)
        int superStinky = getArmorEnchantLvl(player, "deep_lucky_block:super_stinky_feet");
        int stinky = getArmorEnchantLvl(player, "deep_lucky_block:stinky_feet");
        if ((superStinky > 0 || stinky > 0) && player.tickCount % 20 == 0) {
            double radius = superStinky > 0
                    ? deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(3.5, 1.0, superStinky)
                    : deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(2.2, 0.6, stinky);
            int amp = superStinky > 0 ? 1 : 0;
            EnchantmentParticleFX.ankleTrail(player, EnchantmentParticleFX.TOXIC_GREEN, superStinky > 0 ? 4 : 2, 0.01);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius))) {
                mob.addEffect(new MobEffectInstance(MobEffects.POISON, 80, amp));
                if (superStinky > 0) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
            }
        }
        // T306-FIX + T307-FX : swift_strike -> Célérité maîtrisée (les particules se déclenchent désormais à l'impact mêlée !)
        int swiftStrikeLvl = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:swift_strike");
        if (swiftStrikeLvl > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 25, Math.min(3, swiftStrikeLvl - 1), true, false));
        }

        // T306-OVERHAUL : Durabilité & Chrono-Réparation (unbreakable vs eternal ; basic_chrono_mending -> chrono_mending ; corrosions)
        if (player.tickCount % 20 == 0) {
            boolean inNether = player.level().dimension() == Level.NETHER;
            boolean inEnd = player.level().dimension() == Level.END;
            for (ItemStack stack : player.getAllSlots()) {
                if (stack.isEmpty() || !stack.isDamageableItem()) continue;
                if (getEnchantLvl(stack, "deep_lucky_block:unbreakable") > 0) {
                    if (stack.getDamageValue() > 0) stack.setDamageValue(0);
                    continue;
                }
                int eternalLvl = getEnchantLvl(stack, "deep_lucky_block:eternal");
                if (eternalLvl > 0) {
                    if (stack.getDamageValue() >= stack.getMaxDamage() - 1) {
                        stack.setDamageValue(Math.max(0, stack.getMaxDamage() - 2));
                    } else if (stack.getDamageValue() > 0 && player.tickCount % 100 == 0) {
                        stack.setDamageValue(Math.max(0, stack.getDamageValue() - 1));
                    }
                }
                if (stack.getDamageValue() > 0) {
                    int chronoAdv = getEnchantLvl(stack, "deep_lucky_block:chrono_mending");
                    int chronoBasic = getEnchantLvl(stack, "deep_lucky_block:basic_chrono_mending");
                    if (chronoAdv > 0 && player.tickCount % 60 == 0) {
                        int rep = Math.max(2, Math.round(stack.getMaxDamage() * 0.015f * chronoAdv));
                        stack.setDamageValue(Math.max(0, stack.getDamageValue() - rep));
                        EnchantmentParticleFX.ankleTrail(player, EnchantmentParticleFX.TEMPORAL_AMBER, 2, 0.01);
                    } else if (chronoBasic > 0 && player.tickCount % 120 == 0) {
                        int rep = Math.max(1, Math.round(stack.getMaxDamage() * 0.005f * chronoBasic));
                        stack.setDamageValue(Math.max(0, stack.getDamageValue() - rep));
                    }
                }
                // T306-FIX : nether_corrosion & end_corrosion rongent réellement la durabilité dans leur dimension maudite !
                if (player.tickCount % 40 == 0 && eternalLvl == 0) {
                    int nCor = getEnchantLvl(stack, "deep_lucky_block:nether_corrosion");
                    int eCor = getEnchantLvl(stack, "deep_lucky_block:end_corrosion");
                    if (nCor > 0 && inNether) {
                        stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + nCor));
                        EnchantmentParticleFX.ankleTrail(player, ParticleTypes.SMOKE, 2, 0.01);
                    }
                    if (eCor > 0 && inEnd) {
                        stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + eCor));
                        EnchantmentParticleFX.ankleTrail(player, ParticleTypes.PORTAL, 2, 0.05);
                    }
                }
            }
        }

        // Assimilation
        if (player.tickCount % 20 == 0 && player instanceof Player assimPlayer) {
            ItemStack helmet = assimPlayer.getItemBySlot(EquipmentSlot.HEAD);
            if (getEnchantLvl(helmet, "deep_lucky_block:assimilation") > 0 && assimPlayer.getFoodData().getFoodLevel() < 18) {
                for (int i = 0; i < assimPlayer.getInventory().getContainerSize(); i++) {
                    ItemStack food = assimPlayer.getInventory().getItem(i);
                    if ((food.has(net.minecraft.core.component.DataComponents.FOOD))) {
                        assimPlayer.getFoodData().eat(food.getItem().getFoodProperties(food, assimPlayer).nutrition(), 1.0f);
                        food.shrink(1);
                        break;
                    }
                }
            }
        }
        // Perception ESP (Upgrade de echolocation : permanent toutes luminosités dans 24 blocs)
        if (player.tickCount % 30 == 0) {
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (getEnchantLvl(helmet, "deep_lucky_block:perception") > 0) {
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0))) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
                }
            }
        }
        int adrLvl = getArmorEnchantLvl(player, "deep_lucky_block:adrenaline");
        if (adrLvl > 0 && player.getHealth() < (player.getMaxHealth() * 0.40f)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, adrLvl - 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, true, false));
        }
        int mysticLvl = getArmorEnchantLvl(player, "deep_lucky_block:mystic_mind");
        if (mysticLvl > 0 && player.tickCount % 200 == 0 && player instanceof Player mysticPlayer) {
            mysticPlayer.giveExperiencePoints(mysticLvl * 2);
        }

        // T306-OVERHAUL : Différenciation totale de e_speed, dash, strafe et slide !
        int eSpeedLvl = getArmorEnchantLvl(player, "deep_lucky_block:e_speed");
        setStackingMovementBonus(player, CORE_SPRINT_SPEED_ID, eSpeedLvl > 0 && player.isSprinting() ? 0.08 * eSpeedLvl : 0.0);
        if (eSpeedLvl > 0 && player.isSprinting() && player.tickCount % 8 == 0) {
            EnchantmentParticleFX.ankleTrail(player, ParticleTypes.ELECTRIC_SPARK, 2, 0.03);
        }

        // STRAFE (Esquive Latérale, plastron) : vitesse latérale accrue (même en sprint/visée) + esquive projectiles
        int strafeLvl = getArmorEnchantLvl(player, "deep_lucky_block:strafe");
        boolean isStrafing = false;
        if (strafeLvl > 0) {
            net.minecraft.world.phys.Vec3 mv = player.getDeltaMovement();
            double hSpd = Math.sqrt(mv.x * mv.x + mv.z * mv.z);
            if (hSpd > 0.02) {
                net.minecraft.world.phys.Vec3 lk = player.getLookAngle();
                double dot = (mv.x * lk.x + mv.z * lk.z) / Math.max(0.0001, hSpd * Math.sqrt(lk.x * lk.x + lk.z * lk.z));
                isStrafing = Math.abs(dot) < 0.72;
            }
        }
        setStackingMovementBonus(player, STRAFE_SPEED_ID, isStrafing ? 0.08 * strafeLvl : 0.0);

        // DASH (Ruée, jambières) : impulsion avant vive au départ de sprint ou saut sprinté + fenêtre d'esquive projectiles
        int dashLvl = getArmorEnchantLvl(player, "deep_lucky_block:dash");
        boolean prevDashSprint = player.getPersistentData().getBoolean("slb_dash_was_sprint");
        long nowTime = player.level().getGameTime();
        if (dashLvl > 0 && player.isSprinting() && !prevDashSprint && nowTime >= player.getPersistentData().getLong("slb_dash_ready")) {
            net.minecraft.world.phys.Vec3 lk = player.getLookAngle();
            double force = deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.velocity(0.32, 0.13, dashLvl);
            player.setDeltaMovement(player.getDeltaMovement().add(lk.x * force, 0.06, lk.z * force));
            player.hurtMarked = true;
            player.getPersistentData().putLong("slb_dash_ready", nowTime + Math.max(35L, 80L - 12L * dashLvl));
            player.getPersistentData().putLong("slb_dash_dodge_until", nowTime + 10L);
            EnchantmentParticleFX.ankleTrail(player, ParticleTypes.SMALL_GUST, 4, 0.04);
        }
        player.getPersistentData().putBoolean("slb_dash_was_sprint", player.isSprinting());

        // T307-SLIDE : Glissade puissante synchronisée Client+Serveur dans handleSharedClientServerMovement
        // + balayage fauchant serveur et nuage de glisse sous les jambes !
        int slideLvl = getArmorEnchantLvl(player, "deep_lucky_block:slide");
        boolean prevSneak = player.getPersistentData().getBoolean("slb_slide_was_sneak");
        if (slideLvl > 0) {
            if (player.isSprinting()) player.getPersistentData().putLong("slb_recent_sprint", nowTime + 8L);
            if (player.isShiftKeyDown() && !prevSneak && player.onGround()
                    && nowTime <= player.getPersistentData().getLong("slb_recent_sprint")
                    && nowTime >= player.getPersistentData().getLong("slb_slide_ready")) {
                player.getPersistentData().putLong("slb_slide_ready", nowTime + 44L);
                player.getPersistentData().putLong("slb_slide_active_until", nowTime + 10L + 2L * Math.min(5, slideLvl));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.65f, 1.30f);
                if (player.level() instanceof ServerLevel slSlide) {
                    EnchantmentParticleFX.cloudCushion(slSlide, player.getX(), player.getY() + 0.04, player.getZ(), 0.85, false);
                }
            }
            if (player.isShiftKeyDown() && nowTime < player.getPersistentData().getLong("slb_slide_active_until")) {
                if ((player.tickCount & 1) == 0) {
                    EnchantmentParticleFX.ankleTrail(player, ParticleTypes.CLOUD, 3, 0.03);
                    EnchantmentParticleFX.ankleTrail(player, EnchantmentParticleFX.STEEL_SILVER, 2, 0.02);
                }
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(2.2))) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                    if (mob.onGround()) {
                        mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.28, 0));
                        mob.hurtMarked = true;
                    }
                }
            }
        }
        player.getPersistentData().putBoolean("slb_slide_was_sneak", player.isShiftKeyDown());

        int amphLvl = getArmorEnchantLvl(player, "deep_lucky_block:amphibious");
        if (amphLvl > 0 && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, amphLvl - 1, true, false));
            if (player.tickCount % 15 == 0) EnchantmentParticleFX.ankleTrail(player, ParticleTypes.BUBBLE_COLUMN_UP, 2, 0.03);
        }
        // T306-OVERHAUL : blaze_walker (Obsidienne permanente + Résistance au Feu + flammèches)
        // vs magma_walker (disque de Blocs de Magma temporaires + Résistance au Feu + immunité sol brûlant !)
        int blazeWalkLvl = getArmorEnchantLvl(player, "deep_lucky_block:blaze_walker");
        if (blazeWalkLvl > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
            if (player.onGround()) {
                BlockPos pos = player.blockPosition().below();
                if (player.level().getBlockState(pos).is(Blocks.LAVA)) {
                    player.level().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                    EnchantmentParticleFX.ankleTrail(player, ParticleTypes.FLAME, 4, 0.02);
                } else if (player.tickCount % 20 == 0 && player.isSprinting()) {
                    EnchantmentParticleFX.ankleTrail(player, ParticleTypes.SMALL_FLAME, 2, 0.01);
                }
            }
        }
        int magmaWalk = getArmorEnchantLvl(player, "deep_lucky_block:magma_walker");
        if (magmaWalk > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
            if (player.onGround()) {
                BlockPos base = player.blockPosition().below();
                for (BlockPos lp : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 0, 1))) {
                    if (player.level().getBlockState(lp).is(Blocks.LAVA)) {
                        BlockPos imm = lp.immutable();
                        player.level().setBlockAndUpdate(imm, Blocks.MAGMA_BLOCK.defaultBlockState());
                        if (player.level() instanceof ServerLevel slm) {
                            TestProcedure.schedule(slm, TestProcedure.currentTick(slm) + 140, () -> {
                                if (slm.getBlockState(imm).is(Blocks.MAGMA_BLOCK)) {
                                    slm.setBlockAndUpdate(imm, Blocks.LAVA.defaultBlockState());
                                }
                            });
                        }
                    }
                }
            }
        }

        // T306-OVERHAUL : Famille Saut Renforcé (basic_leap_power = Enfant, leap_power = Upgrade +1 cran + bond avant !)
        int leapAdv = getArmorEnchantLvl(player, "deep_lucky_block:leap_power");
        int leapBasic = getArmorEnchantLvl(player, "deep_lucky_block:basic_leap_power");
        if (leapAdv > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 25, Math.min(5, leapAdv), true, false));
        } else if (leapBasic > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 25, Math.min(4, leapBasic - 1), true, false));
        }

        // T306-OVERHAUL : general (aura de commandement alliés !) vs wing_chun_gauntlet (célérité poings nus pure !)
        // (martial_rhythm est désormais une transe de cadence empilée à chaque coup dans handleOffensiveHurt !)
        int genLVL = getArmorEnchantLvl(player, "deep_lucky_block:general");
        if (genLVL > 0 && player.tickCount % 20 == 0) {
            int amp = Math.min(3, genLVL - 1);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, amp, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amp, true, false));
            if (player.tickCount % 80 == 0) EnchantmentParticleFX.crownHalo(player, EnchantmentParticleFX.GOLD_DIVINE, 6);
            for (LivingEntity ally : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0))) {
                if (ally != player && (ally instanceof Player || (ally instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isOwnedBy(player))
                        || (ally.getPersistentData().hasUUID("slb_ally_owner") && ally.getPersistentData().getUUID("slb_ally_owner").equals(player.getUUID())))) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 45, amp, true, false));
                    ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 0, true, false));
                }
            }
        }
        int wingChun = player.getMainHandItem().isEmpty() ? getEnchantLvl(player.getItemBySlot(EquipmentSlot.CHEST), "deep_lucky_block:wing_chun_gauntlet") : 0;
        if (wingChun > 0 && player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, Math.min(4, wingChun), true, false));
        }

        // T306-FIX : Troupes alliées invoquées (slb_ally_owner) défendent leur commandant sans jamais le cibler !
        if (player instanceof Mob summonedMob && summonedMob.getPersistentData().hasUUID("slb_ally_owner")) {
            UUID ownerId = summonedMob.getPersistentData().getUUID("slb_ally_owner");
            if (summonedMob.getTarget() != null && (summonedMob.getTarget().getUUID().equals(ownerId)
                    || (summonedMob.getTarget().getPersistentData().hasUUID("slb_ally_owner")
                    && summonedMob.getTarget().getPersistentData().getUUID("slb_ally_owner").equals(ownerId)))) {
                summonedMob.setTarget(null);
            }
            if (summonedMob.tickCount % 20 == 0 && summonedMob.getTarget() == null && summonedMob.level() instanceof ServerLevel slAlly) {
                Player cmdOwner = slAlly.getPlayerByUUID(ownerId);
                if (cmdOwner != null) {
                    LivingEntity threat = cmdOwner.getLastHurtByMob();
                    if (threat != null && threat.isAlive() && threat != cmdOwner) summonedMob.setTarget(threat);
                }
            }
        }

        int breathHeal = getArmorEnchantLvl(player, "deep_lucky_block:breath_heal");
        if (breathHeal > 0 && player.getAirSupply() == 300 && player.tickCount % 40 == 0) player.heal(2.0f);

        // T306-OVERHAUL : Famille Vélocité (basic_swiftness -> swiftness) + Sprints Uniques (shadow_sprint, light_walker, oxygen_sprint, oxygen_lack_haste)
        int bSwift = getArmorEnchantLvl(player, "deep_lucky_block:basic_swiftness");
        int swift2 = getArmorEnchantLvl(player, "deep_lucky_block:swiftness");
        int shadowSpr = getArmorEnchantLvl(player, "deep_lucky_block:shadow_sprint");
        int lightWalk = getArmorEnchantLvl(player, "deep_lucky_block:light_walker");
        int oxySpr = getArmorEnchantLvl(player, "deep_lucky_block:oxygen_sprint");
        int oxyHaste = getArmorEnchantLvl(player, "deep_lucky_block:oxygen_lack_haste");
        int blzRageSpd = getArmorEnchantLvl(player, "deep_lucky_block:blazing_rage");

        int localLight = player.level().getMaxLocalRawBrightness(player.blockPosition());
        double sprintBonus = 0.0;
        if (swift2 > 0) {
            sprintBonus += 0.065 * swift2;
            if (player.isSprinting() && player.tickCount % 10 == 0) EnchantmentParticleFX.ankleTrail(player, ParticleTypes.CLOUD, 2, 0.02);
        } else if (bSwift > 0) {
            sprintBonus += 0.030 * bSwift;
        }
        if (shadowSpr > 0 && (localLight <= 9 || !player.level().isDay())) {
            sprintBonus += 0.08 * shadowSpr;
            if (player.isSprinting() && player.tickCount % 10 == 0) EnchantmentParticleFX.ankleTrail(player, ParticleTypes.SMOKE, 2, 0.01);
        }
        if (oxySpr > 0) {
            if (player.getAirSupply() >= 270) sprintBonus += 0.12;
            if (player.isInWater() && player.isSprinting() && player.getAirSupply() > 30) {
                net.minecraft.world.phys.Vec3 lk = player.getLookAngle();
                player.setDeltaMovement(player.getDeltaMovement().add(lk.x * 0.035, lk.y * 0.02, lk.z * 0.035));
                if (player.tickCount % 15 == 0) {
                    player.setAirSupply(player.getAirSupply() - 2);
                    EnchantmentParticleFX.ankleTrail(player, ParticleTypes.BUBBLE, 4, 0.05);
                }
            }
        }
        boolean hypoxic = oxyHaste > 0 && (player.isInWater() || player.getAirSupply() < player.getMaxAirSupply() || player.getY() < 16);
        if (hypoxic) {
            sprintBonus += 0.06 * oxyHaste;
            if (player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, Math.min(3, oxyHaste - 1), true, false));
        }
        if (blzRageSpd > 0 && player.isOnFire()) {
            sprintBonus += 0.08 * blzRageSpd;
            if (player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 35, Math.min(2, blzRageSpd - 1), true, false));
        }
        setStackingMovementBonus(player, STACKED_SPRINT_SPEED_ID, player.isSprinting() ? Math.min(0.65, sprintBonus) : 0.0);

        // light_walker fonctionne à la marche ET au sprint sous la lumière (>= 10) !
        boolean inBrightLight = lightWalk > 0 && localLight >= 10;
        setStackingMovementBonus(player, LIGHT_WALKER_SPEED_ID, inBrightLight ? 0.05 * lightWalk : 0.0);
        if (inBrightLight && player.isSprinting() && player.tickCount % 16 == 0) {
            EnchantmentParticleFX.ankleTrail(player, ParticleTypes.WAX_ON, 1, 0.01);
        }

        // lone_wolf : vitesse de chasse en solitaire
        int loneWolfHeld = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:lone_wolf");
        boolean loneActive = loneWolfHeld > 0
                && player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(12.0), p2 -> p2 != player).isEmpty();
        setStackingMovementBonus(player, LONE_WOLF_SPEED_ID, loneActive ? 0.04 * loneWolfHeld : 0.0);

        int scorchBath = getArmorEnchantLvl(player, "deep_lucky_block:scorching_bath");
        if (scorchBath > 0 && player.isOnFire() && player.tickCount % 20 == 0) {
            player.heal(player.getMaxHealth() * 0.01f * scorchBath);
            EnchantmentParticleFX.ankleTrail(player, ParticleTypes.FLAME, 3, 0.02);
        }
        int fireDev = getArmorEnchantLvl(player, "deep_lucky_block:fire_devour");
        if (fireDev > 0 && player.isOnFire()) {
            player.clearFire();
            if (player instanceof Player fdPlayer) fdPlayer.getFoodData().setFoodLevel(Math.min(20, fdPlayer.getFoodData().getFoodLevel() + fireDev));
        }

        // T306-OVERHAUL : Les 7 Enchantements Corporels ont chacun leur propre mécanique !
        int meatArm = getArmorEnchantLvl(player, "deep_lucky_block:meat_armor");
        if (meatArm > 0 && player.tickCount % 120 == 0) {
            boolean wellFed = !(player instanceof Player mp) || mp.getFoodData().getFoodLevel() >= 12;
            if (wellFed) {
                float targetAbs = Math.min(6.0f, 1.0f + 1.0f * meatArm);
                if (player.getAbsorptionAmount() < targetAbs) player.setAbsorptionAmount(targetAbs);
            }
        }
        int exerLvl = getArmorEnchantLvl(player, "deep_lucky_block:exercise");
        if (exerLvl > 0) {
            int stamina = player.getPersistentData().getInt("slb_exercise_ticks");
            stamina = player.isSprinting() ? Math.min(120, stamina + 1) : Math.max(0, stamina - 2);
            player.getPersistentData().putInt("slb_exercise_ticks", stamina);
            if (stamina >= 60 && player.tickCount % 40 == 0) {
                if (player instanceof Player ep && ep.getFoodData().getSaturationLevel() < 4.0f) {
                    ep.getFoodData().setSaturation(Math.min(5.0f, ep.getFoodData().getSaturationLevel() + 0.35f * exerLvl));
                }
                if (player.getAbsorptionAmount() < 2.0f) player.setAbsorptionAmount(2.0f);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, true, false));
            }
        }
        int corp = getArmorEnchantLvl(player, "deep_lucky_block:corpulent");
        if (corp > 0 && player.tickCount % 160 == 0) {
            long sinceHit = player.level().getGameTime() - player.getPersistentData().getLong("slb_last_hurt");
            if (sinceHit >= 140L && player.getAbsorptionAmount() < 4.0f) {
                player.setAbsorptionAmount(4.0f);
            }
        }
        int wingTough = getArmorEnchantLvl(player, "deep_lucky_block:wing_toughness_armor");
        if (wingTough > 0 && (!player.onGround() || player.isFallFlying()) && player.tickCount % 40 == 0) {
            if (player.getAbsorptionAmount() < 4.0f) player.setAbsorptionAmount(4.0f);
        }

        int shelter = getArmorEnchantLvl(player, "deep_lucky_block:shelter");
        int campHeal = getArmorEnchantLvl(player, "deep_lucky_block:campfire_heal");
        if (shelter > 0 && player.isShiftKeyDown() && player.onGround() && player.tickCount % 20 == 0) {
            boolean still = Math.abs(player.getDeltaMovement().x) < 0.005
                    && Math.abs(player.getDeltaMovement().z) < 0.005;
            if (still) player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
        }
        if (campHeal > 0 && player.tickCount % 20 == 0) {
            player.heal(1.0f * campHeal);
            if (player.tickCount % 60 == 0) EnchantmentParticleFX.ankleTrail(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, 1, 0.01);
        }
        int focStead = getArmorEnchantLvl(player, "deep_lucky_block:focused_steadiness");
        if (focStead > 0 && player.isShiftKeyDown()) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, focStead - 1, true, false));

        int metStomp = getArmorEnchantLvl(player, "deep_lucky_block:meteor_stomp");
        if (metStomp > 0 && player.fallDistance >= 4.0f && player.onGround()) {
            player.level().explode(player, player.getX(), player.getY(), player.getZ(), 3.0f, Level.ExplosionInteraction.NONE);
            if (player.level() instanceof ServerLevel slms) {
                EnchantmentParticleFX.shockwaveRing(slms, player.getX(), player.getY() + 0.1, player.getZ(), ParticleTypes.LAVA, 2.2, 16, 0.06);
            }
        }
        // T306-OVERHAUL : rapid_drop_stop (plongeon rapide contrôlé sur Shift + frein de chute sécurisé !)
        // vs earth_bound (malédiction pesante constante qui vous tire vers le bas)
        int rapDrop = getArmorEnchantLvl(player, "deep_lucky_block:rapid_drop_stop");
        int earthBnd = getArmorEnchantLvl(player, "deep_lucky_block:earth_bound");
        if (rapDrop > 0 && player.isShiftKeyDown() && !player.onGround() && player.getDeltaMovement().y < 0) {
            BlockPos twoBelow = player.blockPosition().below(2);
            if (player.level().getBlockState(twoBelow).blocksMotion() || player.level().getBlockState(player.blockPosition().below()).blocksMotion()) {
                // Frein d'atterrissage juste avant le sol !
                player.setDeltaMovement(player.getDeltaMovement().x, Math.max(-0.25, player.getDeltaMovement().y * 0.35), player.getDeltaMovement().z);
                player.fallDistance = Math.min(player.fallDistance, 2.0f);
                EnchantmentParticleFX.ankleTrail(player, ParticleTypes.CLOUD, 3, 0.02);
            } else {
                player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 1.38, 1.0));
                player.fallDistance *= 0.65f;
            }
        }
        if (earthBnd > 0 && player.getDeltaMovement().y < 0) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 1.45, 1.0));
        }

        // T307-BARRIER-TRAIL + T319-AIRWALK-JUMP : Marche Aérienne (foot_placement) & Pas d'Amorti Aérien (range_foot_placement)
        // Piste de Blocks.BARRIER anticipée en 3x3 + lookahead horizontal au niveau du plancher d'ancrage :
        // sauter tout en avançant dans les airs ne fait plus JAMAIS traverser ni tomber !
        int footPl = getArmorEnchantLvl(player, "deep_lucky_block:foot_placement");
        int featherFoot = getArmorEnchantLvl(player, "deep_lucky_block:range_foot_placement");
        int oxyHov = getArmorEnchantLvl(player, "deep_lucky_block:oxygen_hover");
        if (footPl > 0) {
            if (player.isShiftKeyDown()) {
                resetAirWalkAnchor(player);
                clearPlayerBarriersUnderneath(player);
                // T333-AIRWALK-SHIFT-DIVE : Descente progressive côté serveur (anti-chute + particules d'air)
                if (oxyHov <= 0 && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                        && !player.isFallFlying() && !player.isPassenger()
                        && !(player instanceof Player pFly && pFly.getAbilities().flying)) {
                    player.fallDistance = 0.0f;
                    int diveTicks = player.getPersistentData().getInt("dlb_airwalk_dive") + 1;
                    player.getPersistentData().putInt("dlb_airwalk_dive", diveTicks);
                    if (!player.onGround() && player.level() instanceof ServerLevel slDive && (diveTicks % 3 == 0)) {
                        slDive.sendParticles(ParticleTypes.CLOUD,
                                player.getX(), player.getY() + 0.15, player.getZ(),
                                2, 0.22, 0.12, 0.22, 0.02);
                    }
                } else {
                    player.getPersistentData().remove("dlb_airwalk_dive");
                }
            } else {
                if (player.getPersistentData().contains("dlb_airwalk_dive")) {
                    player.getPersistentData().remove("dlb_airwalk_dive");
                    player.fallDistance = 0.0f;
                }
                maintainAirWalkRunway(player, Integer.MIN_VALUE);
            }
        } else {
            player.getPersistentData().remove("dlb_airwalk_dive");
            if (getArmorEnchantLvl(player, "deep_lucky_block:cloud_stride") <= 0) {
                resetAirWalkAnchor(player);
            }
        }
        if (featherFoot > 0 && footPl <= 0 && !player.onGround() && player.fallDistance >= 3.2f && !player.isShiftKeyDown()) {
            placeAerialCushionUnderFeet(player);
            player.fallDistance = 0.0f;
            if (player.level() instanceof ServerLevel slr) {
                EnchantmentParticleFX.cloudCushion(slr, player.getX(), player.getY() + 0.02, player.getZ(), 0.90, false);
            }
        }

        if (oxyHov > 0 && player.isShiftKeyDown()) player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        // high_step est désormais un vrai Attribut STEP_HEIGHT natif dans RealAttributeModifierHandler (plus de Jump Boost forcé !)
        int shortBreath = getArmorEnchantLvl(player, "deep_lucky_block:shortness_of_breath");
        if (shortBreath > 0 && player.isSprinting() && player.tickCount % 20 == 0) player.hurt(player.damageSources().drown(), 2.0f);
        int enemSpd = getArmorEnchantLvl(player, "deep_lucky_block:enemy_speed_pursuit");
        if (enemSpd > 0 && player.isSprinting() && player.tickCount % 20 == 0) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0))) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2));
        }
        int noiseDist = getArmorEnchantLvl(player, "deep_lucky_block:noise_disturbance");
        if (noiseDist > 0 && player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        // T306-FIX : horse_boots scale réellement avec son niveau (1..3) + saut de monture + étincelles aux sabots
        int horseB = getArmorEnchantLvl(player, "deep_lucky_block:horse_boots");
        if (horseB > 0 && player.getVehicle() instanceof LivingEntity mount && player.tickCount % 20 == 0) {
            mount.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, Math.min(3, horseB - 1), true, false));
            if (horseB >= 2) mount.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, horseB - 2, true, false));
            EnchantmentParticleFX.ankleTrail(mount, ParticleTypes.CRIT, 2, 0.02);
        }
        int breathLvl = getArmorEnchantLvl(player, "deep_lucky_block:breath_holding");
        if (breathLvl > 0 && player.getAirSupply() < 200) player.setAirSupply(300);
        int hasteSurge = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:haste_surge");
        if (hasteSurge > 0 && player.tickCount % 20 == 0 && player.level().random.nextInt(100) < (hasteSurge * 10)) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, 0, true, false));
        int rangerLvl = getEnchantLvl(player.getUseItem(), "deep_lucky_block:ranger");
        boolean aimingRanged = player.isUsingItem()
                && (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.CROSSBOW));
        setStackingMovementBonus(player, RANGER_SPEED_ID, aimingRanged && rangerLvl > 0 ? 0.05 * rangerLvl : 0);

        handleMovementEnchantments(player);
    }

    /** Six movement enchantments with distinct activation conditions. */
    // FIX (mob-equipment-enchant generalization) : generalise de Player a
    // LivingEntity -- un mob equipe de bottes/plastron enchantes (Momentum,
    // Trailblazer, Evasive Footwork, Wall Runner, Burst Step, Windrunner)
    // beneficie desormais des memes bonus de mobilite qu'un joueur.
    private static void handleMovementEnchantments(LivingEntity player) {
        // MOMENTUM — builds speed by maintaining a grounded sprint.
        int momentum = getArmorEnchantLvl(player, "deep_lucky_block:momentum");
        int charge = player.getPersistentData().getInt("slb_momentum_charge");
        if (momentum > 0 && player.isSprinting() && player.onGround()) {
            charge = Math.min(80, charge + 1);
        } else {
            charge = Math.max(0, charge - 3);
        }
        player.getPersistentData().putInt("slb_momentum_charge", charge);
        int momentumStage = momentum > 0 && charge >= 12
                ? Math.min(momentum, Math.max(1, charge / 25 + 1)) : 0;
        setStackingMovementBonus(player, MOMENTUM_SPEED_ID, 0.05 * momentumStage);

        // TRAILBLAZER — reliable speed on roads and prepared terrain.
        int trailblazer = getArmorEnchantLvl(player, "deep_lucky_block:trailblazer");
        boolean onRoad = false;
        if (trailblazer > 0 && player.onGround()) {
            BlockState under = player.level().getBlockState(player.blockPosition().below());
            onRoad = under.is(Blocks.DIRT_PATH) || under.is(Blocks.GRAVEL)
                    || under.is(Blocks.COBBLESTONE) || under.is(Blocks.STONE_BRICKS)
                    || under.is(Blocks.SMOOTH_STONE);
        }
        setStackingMovementBonus(player, TRAILBLAZER_SPEED_ID, onRoad ? 0.06 * trailblazer : 0);

        // EVASIVE FOOTWORK — lateral movement is faster than moving head-on.
        int evasive = getArmorEnchantLvl(player, "deep_lucky_block:evasive_footwork");
        net.minecraft.world.phys.Vec3 movement = player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        boolean lateralMove = false;
        if (evasive > 0 && horizontalSpeed > 0.025 && !player.isSprinting()) {
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            double dot = (movement.x * look.x + movement.z * look.z)
                    / Math.max(0.0001, horizontalSpeed
                    * Math.sqrt(look.x * look.x + look.z * look.z));
            lateralMove = Math.abs(dot) < 0.55;
        }
        setStackingMovementBonus(player, EVASIVE_SPEED_ID, lateralMove ? 0.05 * evasive : 0);

        // T307-WALL-RUNNER : Physique HoneyBlock (glisse murale -0.05 + course latérale) synchronisée Client+Serveur
        // dans handleSharedClientServerMovement ; ici effets sonores de bloc de miel & gouttelettes ambrées côté serveur !
        int wallRunner = getArmorEnchantLvl(player, "deep_lucky_block:wall_runner");
        if (wallRunner > 0 && !player.onGround() && !player.isInWater() && !player.isFallFlying()
                && !player.isShiftKeyDown() && isTouchingHorizontalWall(player)) {
            player.fallDistance = 0.0F;
            if (player.tickCount % 6 == 0) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.65f, 0.98f);
                if (player.level() instanceof ServerLevel slHoney) {
                    slHoney.sendParticles(ParticleTypes.FALLING_HONEY,
                            player.getX(), player.getY() + player.getBbHeight() * 0.45, player.getZ(),
                            3, 0.25, 0.25, 0.25, 0.01);
                    slHoney.sendParticles(EnchantmentParticleFX.HONEY_AMBER,
                            player.getX(), player.getY() + 0.35, player.getZ(),
                            3, 0.22, 0.20, 0.22, 0.01);
                }
            }
        }

        // T310-UNIQUE : high_step (Marche-Pente : immunité au gel de neige poudreuse et ralentissement de broussailles)
        if (getArmorEnchantLvl(player, "deep_lucky_block:high_step") > 0) {
            BlockState atFeet = player.level().getBlockState(player.blockPosition());
            if (atFeet.is(Blocks.POWDER_SNOW) || atFeet.is(Blocks.SWEET_BERRY_BUSH)) {
                player.setTicksFrozen(0);
            }
        }

        // T310-UNIQUE : burst_step est désormais une Impulsion de Mêlée & Contre-Impulsion (déclenchée en combat, plus un doublon de Ruée au sprint).

        // WINDRUNNER — open-sky mobility, strongest at high altitude.
        int windrunner = getArmorEnchantLvl(player, "deep_lucky_block:windrunner");
        boolean openSkySprint = windrunner > 0 && player.isSprinting()
                && player.level().canSeeSky(player.blockPosition());
        int windStages = openSkySprint
                ? windrunner + (player.getY() >= 100.0 ? 1 : 0) : 0;
        setStackingMovementBonus(player, WINDRUNNER_SPEED_ID, 0.05 * windStages);
        if (openSkySprint && player.tickCount % 12 == 0) {
            EnchantmentParticleFX.ankleTrail(player, ParticleTypes.SMALL_GUST, 1, 0.02);
        }
    }

    // 3. Cassage de blocs
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event){try{onBlockBreakImpl(event);}catch(Throwable t__){guard("onBlockBreak",t__);}}
    private static void onBlockBreakImpl(BlockEvent.BreakEvent event){
        try {
            handleBlockBreak(event);
        } catch (Throwable t) {
            printHandlerError("onBlockBreak", t);
        }
    }

    private static void handleBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getMainHandItem();
        Level level = (Level) event.getLevel();
        BlockPos origin = event.getPos();
        BlockState brokenState = event.getState();

        // Protection Eternal : empêche un outil Éternel de se briser sur le minage d'un bloc
        if (!tool.isEmpty() && tool.isDamageableItem()
                && getEnchantLvl(tool, "deep_lucky_block:eternal") > 0
                && tool.getDamageValue() >= tool.getMaxDamage() - 2) {
            tool.setDamageValue(Math.max(0, tool.getMaxDamage() - 3));
        }

        if (player.isShiftKeyDown()) return;

        int recLvl = getEnchantLvl(tool, "deep_lucky_block:recycle");
        if (recLvl > 0 && player.level().random.nextInt(100) < 20) {
            Block.popResource(level, origin, new ItemStack(Items.IRON_INGOT));
        }

        // Récolte Abondante + Houe du Fermier (auto-replantation !)
        int bHarv = getEnchantLvl(tool, "deep_lucky_block:bountiful_harvest");
        int farmHoe = getEnchantLvl(tool, "deep_lucky_block:farmers_hoe");
        if (!player.getAbilities().instabuild
                && brokenState.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop
                && crop.isMaxAge(brokenState)) {
            if (bHarv > 0) {
                Item cropItem = brokenState.is(Blocks.WHEAT) ? Items.WHEAT
                        : brokenState.is(Blocks.CARROTS) ? Items.CARROT
                        : brokenState.is(Blocks.BEETROOTS) ? Items.BEETROOT : Items.POTATO;
                Block.popResource(level, origin, new ItemStack(cropItem, bHarv));
                if (level instanceof ServerLevel slbh) {
                    slbh.sendParticles(ParticleTypes.HAPPY_VILLAGER, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.02);
                }
            }
            if (farmHoe > 0 && level instanceof ServerLevel slfh) {
                final BlockPos replantPos = origin.immutable();
                final BlockState babyCrop = crop.getStateForAge(0);
                TestProcedure.schedule(slfh, TestProcedure.currentTick(slfh) + 2, () -> {
                    if (slfh.getBlockState(replantPos).isAir() && babyCrop.canSurvive(slfh, replantPos)) {
                        slfh.setBlockAndUpdate(replantPos, babyCrop);
                    }
                });
            }
        }

        // T306-OVERHAUL : Famille Pelle de Tranchée (utility_trench_shovel = Enfant 3x1x2 meuble ; universal_trench_shovel = Upgrade 3x3x2 !)
        int utilTrench = getEnchantLvl(tool, "deep_lucky_block:utility_trench_shovel");
        int univTrench = getEnchantLvl(tool, "deep_lucky_block:universal_trench_shovel");
        if ((utilTrench > 0 || univTrench > 0) && brokenState.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            int radXZ = 1;
            int radY = univTrench > 0 ? 1 : 0;
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-radXZ, -radY, -radXZ), origin.offset(radXZ, radY, radXZ))) {
                if (p.equals(origin)) continue;
                BlockState st = level.getBlockState(p);
                boolean ok = st.is(BlockTags.MINEABLE_WITH_SHOVEL)
                        || (univTrench > 0 && (st.is(Blocks.COBBLESTONE) || st.is(Blocks.STONE) || st.is(Blocks.SANDSTONE)));
                if (ok && st.getDestroySpeed(level, p) >= 0 && !st.hasBlockEntity()) {
                    level.destroyBlock(p, true, player);
                }
            }
            return;
        }

        // Gantelets brise-roche (Enfant rock_breaking_gauntlet -> Upgrade diamond_glow_gauntlet) : sécurisés (uniquement roches/minerais !)
        ItemStack gauntletChestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        int rockBrk = getEnchantLvl(gauntletChestplate, "deep_lucky_block:rock_breaking_gauntlet");
        int diamGlow = getEnchantLvl(gauntletChestplate, "deep_lucky_block:diamond_glow_gauntlet");
        if ((rockBrk > 0 || diamGlow > 0) && tool.isEmpty() && brokenState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            veinMine(level, origin, player, BlockTags.MINEABLE_WITH_PICKAXE, diamGlow > 0 ? 16 : 6);
            if (diamGlow > 0 && level instanceof ServerLevel sldg) {
                sldg.sendParticles(ParticleTypes.GLOW, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            }
        }

        // T306-OVERHAUL : Famille Bûcheron (tree_felling = Enfant 32 bûches ; lumberjack = Upgrade 128 bûches + feuillage + replantation !)
        int lumberLvl = getEnchantLvl(tool, "deep_lucky_block:lumberjack");
        int treeLvl = getEnchantLvl(tool, "deep_lucky_block:tree_felling");
        if ((lumberLvl > 0 || treeLvl > 0) && brokenState.is(BlockTags.LOGS)) {
            int maxLogs = lumberLvl > 0 ? 128 : 32;
            veinMine(level, origin, player, BlockTags.LOGS, maxLogs);
            if (lumberLvl > 0) {
                for (BlockPos lp : BlockPos.betweenClosed(origin.offset(-3, 1, -3), origin.offset(3, 8, 3))) {
                    if (level.getBlockState(lp).is(BlockTags.LEAVES)) {
                        level.destroyBlock(lp, true, player);
                    }
                }
                if (level instanceof ServerLevel sllj) {
                    EnchantmentParticleFX.shockwaveRing(sllj, origin.getX() + 0.5, origin.getY() + 0.2, origin.getZ() + 0.5, ParticleTypes.CHERRY_LEAVES, 1.4, 10, 0.02);
                }
            }
            return;
        }

        // T306-OVERHAUL : scooping (Pelletage : nappe 3x1x3 meuble + tamisage d'orpailleur !)
        // vs burrowing (Fouissage : tunnel directionnel 1x2 devant le joueur + Vision Nocturne souterraine !)
        int scoopLvl = getEnchantLvl(tool, "deep_lucky_block:scooping");
        int burrowLvl = getEnchantLvl(tool, "deep_lucky_block:burrowing");
        if (scoopLvl > 0 && brokenState.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            Block targetBlock = brokenState.getBlock();
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, 0, 1))) {
                if (!p.equals(origin) && level.getBlockState(p).is(targetBlock)) {
                    level.destroyBlock(p, true, player);
                }
            }
            if (!player.getAbilities().instabuild && level.random.nextInt(100) < 12 * scoopLvl) {
                Item bonus = level.random.nextInt(3) == 0 ? Items.GOLD_NUGGET
                        : (level.random.nextBoolean() ? Items.IRON_NUGGET : Items.FLINT);
                Block.popResource(level, origin, new ItemStack(bonus, 1 + level.random.nextInt(2)));
            }
            return;
        }
        if (burrowLvl > 0 && (brokenState.is(BlockTags.MINEABLE_WITH_SHOVEL) || brokenState.is(BlockTags.MINEABLE_WITH_PICKAXE))) {
            net.minecraft.core.Direction dir = player.getDirection();
            int depth = Math.min(5, 1 + burrowLvl);
            for (int d = 1; d <= depth; d++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos bp = origin.relative(dir, d).above(dy - 1);
                    BlockState bs = level.getBlockState(bp);
                    if ((bs.is(BlockTags.MINEABLE_WITH_SHOVEL) || bs.is(BlockTags.MINEABLE_WITH_PICKAXE))
                            && !bs.is(Tags.Blocks.ORES) && bs.getDestroySpeed(level, bp) >= 0 && !bs.hasBlockEntity()) {
                        level.destroyBlock(bp, true, player);
                    }
                }
            }
            if (origin.getY() < 55) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, true, false));
            }
            return;
        }

        // T306-FIX : snow_remover_shovel ne détruit PLUS que la neige (SNOW, SNOW_BLOCK, POWDER_SNOW) !
        int snowLvl = getEnchantLvl(tool, "deep_lucky_block:snow_remover_shovel");
        if (snowLvl > 0 && (brokenState.is(Blocks.SNOW) || brokenState.is(Blocks.SNOW_BLOCK) || brokenState.is(Blocks.POWDER_SNOW))) {
            for (BlockPos sp : BlockPos.betweenClosed(origin.offset(-4, -1, -4), origin.offset(4, 2, 4))) {
                BlockState sst = level.getBlockState(sp);
                if (!sp.equals(origin) && (sst.is(Blocks.SNOW) || sst.is(Blocks.SNOW_BLOCK) || sst.is(Blocks.POWDER_SNOW))) {
                    level.destroyBlock(sp, true, player);
                }
            }
            if (level instanceof ServerLevel slsn) {
                EnchantmentParticleFX.shockwaveRing(slsn, origin.getX() + 0.5, origin.getY() + 0.2, origin.getZ() + 0.5, ParticleTypes.SNOWFLAKE, 2.0, 14, 0.03);
            }
            return;
        }

        // T306-OVERHAUL : molten (En Fusion : tous minerais métalliques + Fortune + gerbe de fonderie)
        int moltenLvl = getEnchantLvl(tool, "deep_lucky_block:molten");
        if (moltenLvl > 0 && !level.isClientSide() && !player.getAbilities().instabuild) {
            Item smelted = null;
            int baseCount = 1;
            if (brokenState.is(Blocks.IRON_ORE) || brokenState.is(Blocks.DEEPSLATE_IRON_ORE)) smelted = Items.IRON_INGOT;
            else if (brokenState.is(Blocks.GOLD_ORE) || brokenState.is(Blocks.DEEPSLATE_GOLD_ORE)) smelted = Items.GOLD_INGOT;
            else if (brokenState.is(Blocks.COPPER_ORE) || brokenState.is(Blocks.DEEPSLATE_COPPER_ORE)) { smelted = Items.COPPER_INGOT; baseCount = 2 + level.random.nextInt(3); }
            else if (brokenState.is(Blocks.NETHER_GOLD_ORE)) { smelted = Items.GOLD_NUGGET; baseCount = 4 + level.random.nextInt(4); }
            else if (brokenState.is(Blocks.ANCIENT_DEBRIS)) smelted = Items.NETHERITE_SCRAP;

            if (smelted != null) {
                int fortune = getEnchantLvl(tool, "minecraft:fortune");
                int extra = fortune > 0 ? level.random.nextInt(fortune + 1) : 0;
                int extractBonus = getEnchantLvl(tool, "deep_lucky_block:extracting");
                event.setCanceled(true);
                level.destroyBlock(origin, false);
                Block.popResource(level, origin, new ItemStack(smelted, baseCount + extra + extractBonus));
                player.giveExperiencePoints(1 + extractBonus * 2);
                if (level instanceof ServerLevel slmo) {
                    slmo.sendParticles(ParticleTypes.FLAME, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.03);
                    slmo.sendParticles(ParticleTypes.LAVA, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
                }
                return;
            }
        }

        // T306-OVERHAUL : vein_miner (mine tout le filon connecté) vs extracting (raffinage chirurgical : +butin minéral & XP sans casser les blocs voisins !)
        int extractLvl = getEnchantLvl(tool, "deep_lucky_block:extracting");
        int veinLvl = getEnchantLvl(tool, "deep_lucky_block:vein_miner");
        if (extractLvl > 0 && brokenState.is(Tags.Blocks.ORES) && !player.getAbilities().instabuild && level instanceof ServerLevel slex) {
            List<ItemStack> drops = Block.getDrops(brokenState, slex, origin, level.getBlockEntity(origin), player, tool);
            for (ItemStack dr : drops) {
                if (!dr.is(brokenState.getBlock().asItem())) {
                    ItemStack bonusDrop = dr.copy();
                    bonusDrop.setCount(Math.max(1, level.random.nextInt(extractLvl) + 1));
                    Block.popResource(level, origin, bonusDrop);
                    break;
                }
            }
            player.giveExperiencePoints(2 * extractLvl);
            slex.sendParticles(ParticleTypes.ENCHANT, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.15);
        }
        if (veinLvl > 0 && brokenState.is(Tags.Blocks.ORES)) {
            veinMine(level, origin, player, Tags.Blocks.ORES, Math.min(96, 24 * veinLvl));
            return;
        }

        // haste_continuation : prolonge et empile l'élan de minage à chaque bloc cassé !
        int hasteCont = getEnchantLvl(tool, "deep_lucky_block:haste_continuation");
        if (hasteCont > 0 && !level.isClientSide()) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, Math.min(4, hasteCont - 1), true, false));
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event){try{onBreakSpeedImpl(event);}catch(Throwable t__){guard("onBreakSpeed",t__);}}
    private static void onBreakSpeedImpl(PlayerEvent.BreakSpeed event){
        try {
            Player player = event.getEntity();
            ItemStack tool = player.getMainHandItem();
            if (tool.isEmpty()) return;
            // fear : nyctophobie souterraine — ralentit fortement le minage dans le noir / sous Y=0 !
            int fearLvl = getEnchantLvl(tool, "deep_lucky_block:fear");
            if (fearLvl > 0) {
                int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
                float mult = (player.getY() < 0 || light <= 7) ? 0.25f : 0.70f;
                event.setNewSpeed(event.getOriginalSpeed() * mult);
            }
            // Différenciation vitesse pelles : military_shovel (polyvalente tous matériaux !) vs utility/universal_trench_shovel (sols meubles)
            int milShovel = getEnchantLvl(tool, "deep_lucky_block:military_shovel");
            int utilTrench = getEnchantLvl(tool, "deep_lucky_block:utility_trench_shovel");
            int univTrench = getEnchantLvl(tool, "deep_lucky_block:universal_trench_shovel");
            if (tool.getItem() instanceof net.minecraft.world.item.ShovelItem) {
                if (milShovel > 0) {
                    float base = Math.max(event.getOriginalSpeed(), 4.0f);
                    event.setNewSpeed(base * (1.0f + milShovel * 1.8f));
                } else if (univTrench > 0) {
                    event.setNewSpeed(event.getOriginalSpeed() * (1.0f + univTrench * 2.5f));
                } else if (utilTrench > 0) {
                    event.setNewSpeed(event.getOriginalSpeed() * (1.0f + utilTrench * 1.4f));
                }
            }
        } catch (Throwable t) {
            printHandlerError("onBreakSpeed", t);
        }
    }

    private static void veinMine(Level level, BlockPos origin, Player player, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag, int limit) {
        Block originBlock = level.getBlockState(origin).getBlock();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            BlockPos curr = queue.poll();
            BlockState state = level.getBlockState(curr);
            if (!curr.equals(origin)) {
                if (state.isAir() || state.getDestroySpeed(level, curr) < 0 || state.hasBlockEntity()) continue;
                boolean match = tag != null ? state.is(tag) : state.is(originBlock);
                if (!match) continue;
                level.destroyBlock(curr, true, player);
                broken++;
            }
            for (BlockPos neighbor : BlockPos.betweenClosed(curr.offset(-1, -1, -1), curr.offset(1, 1, 1))) {
                BlockPos imm = neighbor.immutable();
                if (visited.add(imm)) queue.add(imm);
            }
        }
    }

    // 4. Brise-Bedrock & Spawner
    @SubscribeEvent
    public static void onLeftClickIndestructible(PlayerInteractEvent.LeftClickBlock event){try{onLeftClickIndestructibleImpl(event);}catch(Throwable t__){guard("onLeftClickIndestructible",t__);}}
    private static void onLeftClickIndestructibleImpl(PlayerInteractEvent.LeftClickBlock event){
        try {
            Player player = event.getEntity();
            ItemStack tool = player.getMainHandItem();
            if (tool.isEmpty() || player.level().isClientSide()) return;
            if (getEnchantLvl(tool, "deep_lucky_block:core_harvesting") > 0) {
                BlockPos pos = event.getPos();
                BlockState state = player.level().getBlockState(pos);
                if (state.is(Blocks.BEDROCK) || state.is(Blocks.SPAWNER) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.BUDDING_AMETHYST)) {
                    player.level().destroyBlock(pos, true, player);
                    if (player.level() instanceof net.minecraft.server.level.ServerLevel slt) tool.hurtAndBreak(20, slt, player, item -> {});
                    event.setCanceled(true);
                }
            }
        } catch (Throwable t) {
            printHandlerError("onLeftClickIndestructible", t);
        }
    }

    // 5. Mort : Phenix, Soul Bound & Eternal, Plunder Wealth, Décapitation, Coupe-Tête, Dévoration, Vétéran
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event){try{onLivingDeathImpl(event);}catch(Throwable t__){guard("onLivingDeath",t__);}}
    private static void onLivingDeathImpl(LivingDeathEvent event){
        try {
            handleLivingDeath(event);
        } catch (Throwable t) {
            printHandlerError("onLivingDeath", t);
        }
    }

    private static void handleLivingDeath(LivingDeathEvent event) {
        if (ENABLE_PHOENIX_REVIVE && event.getEntity() instanceof LivingEntity player && !player.level().isClientSide()) {
            for (ItemStack armor : player.getArmorSlots()) {
                int phxLvl = getEnchantLvl(armor, "deep_lucky_block:phoenix");
                if (phxLvl > 0) {
                    event.setCanceled(true);
                    player.setHealth(player.getMaxHealth());
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Math.max(40, Math.min(140, 40 + (phxLvl - 1) * 50)), 1));
                    player.level().explode(player, player.getX(), player.getY(), player.getZ(), 4.0f, Level.ExplosionInteraction.NONE);
                    EnchantmentParticleFX.doubleHelix(player, ParticleTypes.FLAME, ParticleTypes.LAVA, 0.85, 14);
                    Enchantment phxEnch = deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:phoenix");
                    if (phxEnch != null) {
                        deepluckyblock.util.DeepEnchant.remove(armor, phxEnch);
                    }
                    return;
                }
            }
            // Soul Bound ET Eternal préservent tous deux l'objet dans l'inventaire à la mort !
            if (player instanceof Player sbPlayer) {
                List<ItemStack> saved = new ArrayList<>();
                for (int i = 0; i < sbPlayer.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sbPlayer.getInventory().getItem(i);
                    if (getEnchantLvl(stack, "deep_lucky_block:soul_bound") > 0
                            || getEnchantLvl(stack, "deep_lucky_block:eternal") > 0) {
                        saved.add(stack.copy());
                        sbPlayer.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                if (!saved.isEmpty()) SOUL_BOUND_CACHE.put(sbPlayer.getUUID(), saved);
            }
        }
        if (event.getSource().getEntity() instanceof LivingEntity player && !player.level().isClientSide()) {
            LivingEntity victim = event.getEntity();
            ItemStack weapon = player.getMainHandItem();
            int plunderLvl = getEnchantLvl(weapon, "deep_lucky_block:plunder_wealth");
            if (plunderLvl > 0 && (victim instanceof Villager || victim instanceof Pillager)) {
                victim.spawnAtLocation(new ItemStack(Items.EMERALD, (2 + player.level().random.nextInt(3)) * plunderLvl));
            }
            int soulReap = getEnchantLvl(weapon, "deep_lucky_block:soul_reaping_sickle");
            if (soulReap > 0 && player.level().random.nextInt(100) < Math.min(35, 4 * soulReap)) {
                EnchantmentParticleFX.burstOnEntity(victim, ParticleTypes.SCULK_SOUL, 10, 0.3, 0.04);
                if (victim instanceof net.minecraft.world.entity.monster.Zombie) victim.spawnAtLocation(Items.ZOMBIE_SPAWN_EGG);
                else if (victim instanceof net.minecraft.world.entity.monster.Skeleton) victim.spawnAtLocation(Items.SKELETON_SPAWN_EGG);
                else if (victim instanceof net.minecraft.world.entity.monster.Creeper) victim.spawnAtLocation(Items.CREEPER_SPAWN_EGG);
            }
            int lootHarv = getEnchantLvl(weapon, "deep_lucky_block:loot_harvest");
            if (lootHarv > 0) victim.spawnAtLocation(new ItemStack(Items.EMERALD, lootHarv));

            // T306-OVERHAUL : decapitation (trophées de crânes !) vs beheading (frénésie de bourreau à l'exécution !)
            int decapLvl = getEnchantLvl(weapon, "deep_lucky_block:decapitation");
            if (decapLvl > 0 && player.level().random.nextInt(100) < Math.min(60, 12 * decapLvl)) {
                Item headItem = null;
                if (victim instanceof net.minecraft.world.entity.monster.WitherSkeleton) headItem = Items.WITHER_SKELETON_SKULL;
                else if (victim instanceof net.minecraft.world.entity.monster.Skeleton) headItem = Items.SKELETON_SKULL;
                else if (victim instanceof net.minecraft.world.entity.monster.Zombie) headItem = Items.ZOMBIE_HEAD;
                else if (victim instanceof net.minecraft.world.entity.monster.Creeper) headItem = Items.CREEPER_HEAD;
                else if (victim instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin) headItem = Items.PIGLIN_HEAD;
                if (headItem != null) {
                    victim.spawnAtLocation(headItem);
                    EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 10, 0.25, 0.02);
                }
            }
            int beheadLvl = getEnchantLvl(weapon, "deep_lucky_block:beheading");
            if (beheadLvl > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, Math.min(2, beheadLvl - 1), true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0, true, false));
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 8, 0.25, 0.03);
            }

            // T306-OVERHAUL : devour (Dévoration : festin carnassier sur élimination -> Faim + Saturation + PV !)
            int devourLvl = getEnchantLvl(weapon, "deep_lucky_block:devour");
            if (devourLvl > 0) {
                float feastHeal = Math.min(10.0f, 1.5f + 1.5f * devourLvl);
                player.heal(feastHeal);
                if (player instanceof Player devPlayer) {
                    devPlayer.getFoodData().eat(Math.min(8, 1 + devourLvl), 0.7f);
                }
                EnchantmentParticleFX.burstOnEntity(victim, EnchantmentParticleFX.CRIMSON_BLOOD, 8, 0.3, 0.02);
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.HEART, 2, 0.25, 0.02);
            }

            // T306-OVERHAUL : hundred_battles_veteran (Vétéran des Cent Batailles : gagne de l'endurance aguerrie à chaque victoire !)
            int hundVet = getArmorEnchantLvl(player, "deep_lucky_block:hundred_battles_veteran");
            if (hundVet > 0) {
                int kills = Math.min(100, player.getPersistentData().getInt("slb_veteran_kills") + 1);
                player.getPersistentData().putInt("slb_veteran_kills", kills);
                float vetAbs = Math.min(8.0f, 2.0f + (kills / 25.0f) * 2.0f);
                if (player.getAbsorptionAmount() < vetAbs) player.setAbsorptionAmount(vetAbs);
            }
        }
    }

    // 5b. T306 Hooks : XP (basic_mending), Repas (gluttony / stomach_pouch), Soins réduits (weakness_wound), Champs (light_walker)
    @SubscribeEvent
    public static void onPickupXp(net.neoforged.neoforge.event.entity.player.PlayerXpEvent.PickupXp event) {
        try {
            Player player = event.getEntity();
            if (player.level().isClientSide()) return;
            int xpVal = Math.max(1, event.getOrb().getValue());
            for (ItemStack st : player.getAllSlots()) {
                if (!st.isEmpty() && st.isDamageableItem() && st.getDamageValue() > 0) {
                    int bMend = getEnchantLvl(st, "deep_lucky_block:basic_mending");
                    if (bMend > 0) {
                        int repair = Math.max(1, xpVal * bMend);
                        st.setDamageValue(Math.max(0, st.getDamageValue() - repair));
                        break;
                    }
                }
            }
        } catch (Throwable t__) {
            guard("onPickupXp", t__);
        }
    }

    @SubscribeEvent
    public static void onFinishEat(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide()) return;
            ItemStack item = event.getItem();
            if (!item.has(DataComponents.FOOD)) return;
            int glut = getArmorEnchantLvl(entity, "deep_lucky_block:gluttony");
            if (glut > 0) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60 * glut, 0, true, false));
                if (entity.getAbsorptionAmount() < 4.0f * glut) {
                    entity.setAbsorptionAmount(Math.min(8.0f, 2.0f + 2.0f * glut));
                }
                EnchantmentParticleFX.burstOnEntity(entity, ParticleTypes.HAPPY_VILLAGER, 5, 0.3, 0.02);
            }
            int pouch = getArmorEnchantLvl(entity, "deep_lucky_block:stomach_pouch");
            if (pouch > 0 && entity instanceof Player fp) {
                fp.getFoodData().eat(pouch, 0.5f);
            }
        } catch (Throwable t__) {
            guard("onFinishEat", t__);
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide()) return;
            if (entity.getPersistentData().getLong("slb_weak_wound_until") > entity.level().getGameTime()) {
                event.setAmount(event.getAmount() * 0.65f);
            }
        } catch (Throwable t__) {
            guard("onLivingHeal", t__);
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        try {
            if (event.getEntity() instanceof LivingEntity le) {
                if (getArmorEnchantLvl(le, "deep_lucky_block:light_walker") > 0
                        || getArmorEnchantLvl(le, "deep_lucky_block:lightness") > 0
                        || getArmorEnchantLvl(le, "deep_lucky_block:basic_lightness") > 0) {
                    event.setCanceled(true);
                }
            }
        } catch (Throwable t__) {
            guard("onFarmlandTrample", t__);
        }
    }

    // 6. Respawn : Soul Bound
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event){try{onPlayerRespawnImpl(event);}catch(Throwable t__){guard("onPlayerRespawn",t__);}}
    private static void onPlayerRespawnImpl(PlayerEvent.PlayerRespawnEvent event){
        try {
            Player player = event.getEntity();
            if (!player.level().isClientSide() && SOUL_BOUND_CACHE.containsKey(player.getUUID())) {
                for (ItemStack stack : SOUL_BOUND_CACHE.remove(player.getUUID())) player.getInventory().add(stack);
            }
        } catch (Throwable t) {
            printHandlerError("onPlayerRespawn", t);
        }
    }

    // 7. Gale — PORT 1.21.1 : LivingJumpEvent est supprimé. La détection du
    // saut (onGround() false après true, avec delta vertical positif) est faite
    // dans handlePlayerTick (PlayerTickEvent.Post), cle NBT dédiée
    // "slb_ug_was_ground" (distincte de celle d'EquipmentExpansionHandler).

    // 8. Buoy (scale par niveau 1..3 + bulles de flottabilité)
    @SubscribeEvent
    public static void onPlayerWaterBuoy(EntityTickEvent.Post event){try{onPlayerWaterBuoyImpl(event);}catch(Throwable t__){guard("onPlayerWaterBuoy",t__);}}
    private static void onPlayerWaterBuoyImpl(EntityTickEvent.Post event){
        try {
            if (event.getEntity() instanceof LivingEntity player && !player.level().isClientSide()) {
                int buoyLvl = getEnchantLvl(player.getItemBySlot(EquipmentSlot.FEET), "deep_lucky_block:buoy");
                if (buoyLvl > 0 && player.isInWater() && player.getDeltaMovement().y > -0.02 && !player.isShiftKeyDown()) {
                    double lift = Math.min(0.45, 0.14 + 0.08 * buoyLvl);
                    player.setDeltaMovement(player.getDeltaMovement().x, Math.min(0.65, player.getDeltaMovement().y + lift), player.getDeltaMovement().z);
                    if (player.tickCount % 12 == 0) EnchantmentParticleFX.ankleTrail(player, ParticleTypes.BUBBLE_COLUMN_UP, 2, 0.03);
                }
            }
        } catch (Throwable t) {
            printHandlerError("onPlayerWaterBuoy", t);
        }
    }

    // 8.5 Vitesse d'armement
    @SubscribeEvent
    public static void onItemUseStart(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start event){try{onItemUseStartImpl(event);}catch(Throwable t__){guard("onItemUseStart",t__);}}
    private static void onItemUseStartImpl(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start event){
        try {
            if (event.getEntity() instanceof LivingEntity) {
                ItemStack stack = event.getItem();
                if (stack.isEmpty()) return;
                int toyLvl = getEnchantLvl(stack, "deep_lucky_block:toy");
                if (toyLvl > 0) event.setDuration(3);
            }
        } catch (Throwable t) {
            printHandlerError("onItemUseStart", t);
        }
    }

    @SubscribeEvent
    public static void onItemUseTick(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick event){try{onItemUseTickImpl(event);}catch(Throwable t__){guard("onItemUseTick",t__);}}
    private static void onItemUseTickImpl(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick event){
        try {
            if (!(event.getEntity() instanceof LivingEntity player)) return;
            ItemStack stack = event.getItem();
            if (stack.isEmpty()) return;
            int toyLvl = getEnchantLvl(stack, "deep_lucky_block:toy");
            if (toyLvl > 0) {
                if (event.getDuration() <= 1) { fireToyArrow(player, toyLvl); event.setDuration(4); }
                return;
            }
            int rangerLvl = getEnchantLvl(stack, "deep_lucky_block:ranger");
            int quickLvl = getEnchantLvl(stack, "minecraft:quick_charge");
            int instantLvl = getEnchantLvl(stack, "deep_lucky_block:instant_shot");
            if (rangerLvl > 0 || quickLvl > 0 || instantLvl > 0) {
                boolean isCrossbow = stack.getItem() instanceof net.minecraft.world.item.CrossbowItem;
                int instantMult = isCrossbow ? 12 : 6;
                int reduction = (rangerLvl * 2) + (quickLvl * 3) + (instantLvl * instantMult);
                if (event.getDuration() > reduction + 1) event.setDuration(event.getDuration() - reduction);
            }
            int fastEat = getArmorEnchantLvl(event.getEntity(), "deep_lucky_block:fast_eating");
            if (fastEat > 0 && (stack.has(net.minecraft.core.component.DataComponents.FOOD)) && event.getDuration() > fastEat + 1) event.setDuration(event.getDuration() - fastEat);
        } catch (Throwable t) {
            printHandlerError("onItemUseTick", t);
        }
    }

    private static void fireToyArrow(LivingEntity player, int toyLvl) {
        if (player.level().isClientSide()) return;
        Arrow arrow = new Arrow(player.level(), player, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(player.getRandom().nextFloat() < 0.10f ? 0.05f : 0.0f);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 6.0F);
        player.level().addFreshEntity(arrow);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    // 9. Tir d'arc
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event){try{onArrowLooseImpl(event);}catch(Throwable t__){guard("onArrowLoose",t__);}}
    private static void onArrowLooseImpl(ArrowLooseEvent event){
        try {
            handleArrowLoose(event);
        } catch (Throwable t) {
            printHandlerError("onArrowLoose", t);
        }
    }

    private static void handleArrowLoose(ArrowLooseEvent event) {
        // T310-CROSSBOW : Seul le forçage de tension d'arc en 1 clic (instant_shot) reste lié à ArrowLooseEvent.
        // Tous les effets de tir (phasing, sniper, steady_shot, point_shoot, multishot_bow, scatter,
        // recoil_launch, inverted_arrows, weak_force, string_snap) sont désormais unifiés dans
        // handleEntityJoinLevel afin de fonctionner à 100 % sur l'ARBALÈTE comme sur l'ARC !
        ItemStack bow = event.getBow();
        if (getEnchantLvl(bow, "deep_lucky_block:instant_shot") > 0) {
            event.setCharge(Math.max(event.getCharge(), 20));
        }
    }

    /** T310-CROSSBOW : Résout proprement l'arc ou l'arbalète (main principale OU main secondaire). */
    private static ItemStack resolveRangedWeapon(LivingEntity shooter) {
        ItemStack main = shooter.getMainHandItem();
        if (!main.isEmpty() && main.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
            return main;
        }
        ItemStack off = shooter.getOffhandItem();
        if (!off.isEmpty() && off.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
            return off;
        }
        return !main.isEmpty() ? main : off;
    }

    // 10. Impacts de fleches
    @SubscribeEvent
    public static void onArrowImpact(ProjectileImpactEvent event){try{onArrowImpactImpl(event);}catch(Throwable t__){guard("onArrowImpact",t__);}}
    private static void onArrowImpactImpl(ProjectileImpactEvent event){
        try {
            handleArrowImpact(event);
        } catch (Throwable t) {
            printHandlerError("onArrowImpact", t);
        }
    }

    // 10b. T310-CROSSBOW : Pipeline unifié Arc + Arbalète appliqué dès l'apparition de tout AbstractArrow.
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event){try{onEntityJoinLevelImpl(event);}catch(Throwable t__){guard("onEntityJoinLevel",t__);}}
    private static void onEntityJoinLevelImpl(EntityJoinLevelEvent event){
        try {
            handleEntityJoinLevel(event);
        } catch (Throwable t) {
            printHandlerError("onEntityJoinLevel", t);
        }
    }

    private static void handleEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity player)) return;
        if (arrow.getPersistentData().getBoolean("slb_no_phasing")) return;

        ItemStack rangedWeapon = resolveRangedWeapon(player);
        if (rangedWeapon.isEmpty() || !(rangedWeapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)) {
            return;
        }

        // toy : si une arbalète déjà chargée tire un trait avec Jouet, neutralise les dégâts !
        if (getEnchantLvl(rangedWeapon, "deep_lucky_block:toy") > 0) {
            arrow.setBaseDamage(player.getRandom().nextFloat() < 0.10f ? 0.05f : 0.0f);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }

        // heavy_range_crossbow : accélère directement le trait d'arbalète (+35% vélocité par niveau) !
        int hvyCross = getEnchantLvl(rangedWeapon, "deep_lucky_block:heavy_range_crossbow");
        if (hvyCross > 0 && !arrow.getPersistentData().getBoolean("slb_hvy_cross_boosted")) {
            arrow.getPersistentData().putBoolean("slb_hvy_cross_boosted", true);
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.0 + Math.min(1.5, 0.35 * hvyCross)));
        }

        // phasing : flèche/carreau fantôme sans gravité traversant murs et entités (Arc & Arbalète)
        if (getEnchantLvl(rangedWeapon, "deep_lucky_block:phasing") > 0) {
            arrow.getPersistentData().putBoolean("slb_phasing", true);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(0.55f));
            if (event.getLevel() instanceof ServerLevel sl) {
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 600, () -> {
                    if (arrow.isAlive()) arrow.discard();
                });
            }
        }

        // sniper : double la vélocité du projectile (Arc & Arbalète)
        if (getEnchantLvl(rangedWeapon, "deep_lucky_block:sniper") > 0
                && !arrow.getPersistentData().getBoolean("slb_sniper_boosted")) {
            arrow.getPersistentData().putBoolean("slb_sniper_boosted", true);
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(2.0));
        }

        // weak_force : malédiction réduisant la force du tir (Arc & Arbalète)
        int weakLvl = getEnchantLvl(rangedWeapon, "deep_lucky_block:weak_force");
        if (weakLvl > 0) {
            arrow.setBaseDamage(Math.max(0.5f,
                    arrow.getBaseDamage() * Math.max(0.25f, 1.0f - 0.25f * weakLvl)));
        }

        // inverted_arrows : chance d'inverser le vecteur de tir (Arc & Arbalète)
        int invertedLvl = getEnchantLvl(rangedWeapon, "deep_lucky_block:inverted_arrows");
        boolean invertedShot = invertedLvl > 0
                && player.level().random.nextInt(100) < Math.min(100, 20 * invertedLvl);
        if (invertedShot) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(-1.0));
        }

        // steady_shot : vol astral rectiligne sans gravité (Arc & Arbalète)
        boolean steadyShot = getEnchantLvl(rangedWeapon, "deep_lucky_block:steady_shot") > 0;
        if (steadyShot) {
            arrow.setNoGravity(true);
        }

        // T310-UNIQUE : point_shoot (Tir à Bout Portant : dispersion nulle + +30 %/niv à <= 10 blocs sur Arc & Arbalète)
        int pointShootLvl = getEnchantLvl(rangedWeapon, "deep_lucky_block:point_shoot");
        if (pointShootLvl > 0) {
            arrow.getPersistentData().putInt("slb_point_shoot", pointShootLvl);
            double speed = arrow.getDeltaMovement().length();
            if (speed > 0.01) {
                net.minecraft.world.phys.Vec3 curDir = arrow.getDeltaMovement().normalize();
                net.minecraft.world.phys.Vec3 lookDir = player.getLookAngle();
                if (Math.abs(curDir.dot(lookDir)) > 0.992) {
                    arrow.setDeltaMovement(lookDir.scale(invertedShot ? -speed : speed));
                }
            }
        }

        // Effets déclenchés UNE SEULE FOIS par pression de détente (protège contre la triple salve de Multishot d'arbalète)
        long nowTick = event.getLevel().getGameTime();
        if (player.getPersistentData().getLong("slb_ranged_shot_tick") != nowTick) {
            player.getPersistentData().putLong("slb_ranged_shot_tick", nowTick);

            int multiLvl = getEnchantLvl(rangedWeapon, "deep_lucky_block:multishot_bow");
            if (multiLvl > 0) {
                int fanCount = Math.min(10, 2 * multiLvl);
                for (int i = 0; i < fanCount; i++) {
                    Arrow extraArrow = new Arrow(player.level(), player, new ItemStack(Items.ARROW), null);
                    float yawOff = (i % 2 == 0 ? 5.5f : -5.5f) * ((i / 2) + 1);
                    extraArrow.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOff, 0.0f, 3.0f, 0.3f);
                    if (invertedShot) extraArrow.setDeltaMovement(extraArrow.getDeltaMovement().scale(-1.0));
                    if (steadyShot) extraArrow.setNoGravity(true);
                    extraArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    extraArrow.getPersistentData().putBoolean("slb_no_phasing", true);
                    player.level().addFreshEntity(extraArrow);
                }
            }

            int scatterLvl = getEnchantLvl(rangedWeapon, "deep_lucky_block:scatter");
            if (scatterLvl > 0) {
                int pellets = Math.min(12, 4 + 2 * scatterLvl);
                for (int i = 0; i < pellets; i++) {
                    Arrow pellet = new Arrow(player.level(), player, new ItemStack(Items.ARROW), null);
                    pellet.setBaseDamage(1.6);
                    pellet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 2.75f, 9.5f);
                    if (invertedShot) pellet.setDeltaMovement(pellet.getDeltaMovement().scale(-1.0));
                    if (steadyShot) pellet.setNoGravity(true);
                    pellet.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    pellet.getPersistentData().putBoolean("slb_no_phasing", true);
                    player.level().addFreshEntity(pellet);
                }
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.SMALL_GUST, 5, 0.25, 0.04);
            }

            int recoilLvl = getEnchantLvl(rangedWeapon, "deep_lucky_block:recoil_launch");
            if (recoilLvl > 0) {
                player.setDeltaMovement(player.getDeltaMovement().subtract(player.getLookAngle().scale(0.22 * recoilLvl)));
                player.hurtMarked = true;
            }

            int snapLvl = getEnchantLvl(rangedWeapon, "deep_lucky_block:string_snap");
            if (snapLvl > 0 && player.level().random.nextInt(100) < (20 * snapLvl)
                    && event.getLevel() instanceof ServerLevel slb) {
                rangedWeapon.hurtAndBreak(Math.max(1, (int) (rangedWeapon.getMaxDamage() * 0.30f)), slb, player, item -> {});
            }
        }
    }

    private static void handleArrowImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity player)) return;
        if (player.level().isClientSide()) return;
        ItemStack bow = resolveRangedWeapon(player);

        // T310-UNIQUE : point_shoot (Tir à Bout Portant : +30 % de dégâts par niveau à <= 10 blocs !)
        int pointShootHit = Math.max(arrow.getPersistentData().getInt("slb_point_shoot"),
                getEnchantLvl(bow, "deep_lucky_block:point_shoot"));
        if (pointShootHit > 0 && event.getRayTraceResult() instanceof EntityHitResult ehrPt
                && ehrPt.getEntity() instanceof LivingEntity closeTarget) {
            if (player.distanceTo(closeTarget) <= 10.0f) {
                float bonusClose = (float) Math.max(1.5, arrow.getBaseDamage()) * 0.30f * pointShootHit;
                hurtNoCascade(closeTarget, player.damageSources().arrow(arrow, player), bonusClose);
                EnchantmentParticleFX.burstOnEntity(closeTarget, ParticleTypes.CRIT, 8, 0.25, 0.08);
            }
        }

        if (arrow.getPersistentData().getBoolean("slb_phasing")) {
            if (event.getRayTraceResult() instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity target) {
                hurtNoCascade(target, player.damageSources().arrow(arrow, player),
                        (float) arrow.getBaseDamage());
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 1.0f, 1.2f);
                EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.PORTAL, 6, 0.25, 0.05);
            }
            event.setCanceled(true);
        }

        if (getEnchantLvl(bow, "deep_lucky_block:torch") > 0 && event.getRayTraceResult() instanceof net.minecraft.world.phys.BlockHitResult bhr) {
            BlockPos targetPos = bhr.getBlockPos().relative(bhr.getDirection());
            if (player.level().getBlockState(targetPos).isAir()) player.level().setBlockAndUpdate(targetPos, Blocks.TORCH.defaultBlockState());
        }
        int whisperLvl = getEnchantLvl(bow, "deep_lucky_block:whisper");
        if (whisperLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target)
            hurtNoCascade(target, player.damageSources().arrow(arrow, player), target.getArmorValue() * 2.0f);
        int flintHeartLvl = getEnchantLvl(bow, "deep_lucky_block:flint_heart");
        if (flintHeartLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            hurtNoCascade(target, player.damageSources().arrow(arrow, player), 2.5f * flintHeartLvl);
            EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.CRIT, 6, 0.25, 0.08);
            if (bow.isDamageableItem() && player.level() instanceof net.minecraft.server.level.ServerLevel slr) {
                bow.hurtAndBreak(Math.max(1, (int)(bow.getMaxDamage() * 0.02f)), slr, player, item -> {});
            }
        }

        // T306-OVERHAUL : ricochet (chaîne d'ennemi en ennemi !) vs bounce (vrai rebond physique contre les murs/sols !)
        int ricochetLvl = getEnchantLvl(bow, "deep_lucky_block:ricochet");
        if (ricochetLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            int bounces = 0;
            LivingEntity prev = target;
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(7.0))) {
                if (mob != target && mob.isAlive() && bounces < ricochetLvl) {
                    hurtNoCascade(mob, player.damageSources().arrow(arrow, player), 4.0f + ricochetLvl);
                    if (player.level() instanceof ServerLevel slric) {
                        EnchantmentParticleFX.tetherBeam(slric, prev.position().add(0, prev.getBbHeight() * 0.5, 0),
                                mob.position().add(0, mob.getBbHeight() * 0.5, 0), ParticleTypes.CRIT, null, 0.55);
                    }
                    prev = mob;
                    bounces++;
                }
            }
        }
        int bounceLvl = getEnchantLvl(bow, "deep_lucky_block:bounce");
        if (bounceLvl > 0 && event.getRayTraceResult() instanceof net.minecraft.world.phys.BlockHitResult bhr) {
            int usedBounces = arrow.getPersistentData().getInt("slb_wall_bounces");
            if (usedBounces < 1 + bounceLvl) {
                arrow.getPersistentData().putInt("slb_wall_bounces", usedBounces + 1);
                net.minecraft.world.phys.Vec3 v = arrow.getDeltaMovement();
                net.minecraft.core.Direction face = bhr.getDirection();
                double rx = face.getAxis() == net.minecraft.core.Direction.Axis.X ? -v.x : v.x;
                double ry = face.getAxis() == net.minecraft.core.Direction.Axis.Y ? -v.y : v.y;
                double rz = face.getAxis() == net.minecraft.core.Direction.Axis.Z ? -v.z : v.z;
                arrow.setDeltaMovement(rx * 0.85, ry * 0.85, rz * 0.85);
                arrow.setPos(bhr.getLocation().add(face.getStepX() * 0.12, face.getStepY() * 0.12, face.getStepZ() * 0.12));
                arrow.hurtMarked = true;
                if (player.level() instanceof ServerLevel slbnc) {
                    slbnc.sendParticles(ParticleTypes.ITEM_SLIME, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z, 6, 0.1, 0.1, 0.1, 0.02);
                }
                player.level().playSound(null, bhr.getBlockPos(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.7f, 1.3f);
                event.setCanceled(true);
                return;
            }
        }

        if (getEnchantLvl(bow, "deep_lucky_block:enemy_attractor") > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(6.0))) if (mob != target) mob.setTarget(target);
        }
        // heavy_range_crossbow : bonus longue portée croissant au-delà de 10 blocs !
        int hvyCrossLvl = getEnchantLvl(bow, "deep_lucky_block:heavy_range_crossbow");
        if (hvyCrossLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            double dist = player.distanceTo(target);
            float distBonus = dist > 10.0 ? (float) Math.min(10.0, (dist - 10.0) * 0.25) : 0.0f;
            hurtNoCascade(target, player.damageSources().arrow(arrow, player), 2.0f * hvyCrossLvl + distBonus);
        }
        int peregrineLvl = getEnchantLvl(bow, "deep_lucky_block:peregrine");
        if (peregrineLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity target) {
            double drop = player.getY() - target.getY();
            if (drop > 1.0) {
                float bonus = (float) Math.min(drop, 20.0) * 0.5f * peregrineLvl;
                hurtNoCascade(target, player.damageSources().arrow(arrow, player), bonus);
                EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.CLOUD, 6, 0.3, 0.04);
            }
        }
        int wraithLvl = getEnchantLvl(bow, "deep_lucky_block:wraith_arrow");
        if (wraithLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            hurtNoCascade(target, player.damageSources().magic(), target.getMaxHealth() * 0.05f);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.SCULK_SOUL, 8, 0.25, 0.03);
            if (target.getHealth() <= 0) player.heal(player.getMaxHealth() * 0.10f);
        }
        if (getEnchantLvl(bow, "deep_lucky_block:blazing_arrow") > 0) {
            for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, arrow.getBoundingBox().inflate(6.0, 3.0, 6.0))) if (le != player) le.setRemainingFireTicks((4) * 20);
            if (player.level() instanceof ServerLevel slba) {
                EnchantmentParticleFX.shockwaveRing(slba, event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y + 0.1, event.getRayTraceResult().getLocation().z, ParticleTypes.FLAME, 2.5, 14, 0.04);
            }
        }
        if (getEnchantLvl(bow, "deep_lucky_block:dragons_breath_arrow") > 0) {
            double dbx = event.getRayTraceResult().getLocation().x;
            double dby = event.getRayTraceResult().getLocation().y;
            double dbz = event.getRayTraceResult().getLocation().z;
            net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(player.level(), dbx, dby, dbz);
            cloud.setParticle(ParticleTypes.DRAGON_BREATH);
            cloud.setRadius(4.0f);
            cloud.setDuration(10);
            cloud.setWaitTime(0);
            player.level().addFreshEntity(cloud);
            ServerLevel dsl = (ServerLevel) player.level();
            final LivingEntity dPlayer = player;
            for (int t = 0; t <= 1; t++) {
                final int delay = t * 10;
                TestProcedure.schedule(dsl, TestProcedure.currentTick(dsl) + delay, () -> {
                    AABB dmgBox = new AABB(dbx - 4, dby - 2, dbz - 4, dbx + 4, dby + 4, dbz + 4);
                    for (LivingEntity le : dsl.getEntitiesOfClass(LivingEntity.class, dmgBox)) {
                        if (le != dPlayer && le.isAlive()) {
                            le.hurt(dsl.damageSources().dragonBreath(), 2.0f);
                            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
                        }
                    }
                });
            }
        }
        if (getEnchantLvl(bow, "deep_lucky_block:poison_arrow") > 0) {
            net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(player.level(), event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y, event.getRayTraceResult().getLocation().z);
            cloud.setRadius(3.0f);
            cloud.setDuration(10);
            cloud.setWaitTime(0);
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0));
            player.level().addFreshEntity(cloud);
        }
        if (getEnchantLvl(bow, "deep_lucky_block:water_arrow") > 0) {
            if (event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
                target.clearFire();
                boolean waterSensitive = target instanceof net.minecraft.world.entity.monster.Blaze
                        || target instanceof net.minecraft.world.entity.monster.MagmaCube
                        || target instanceof net.minecraft.world.entity.monster.EnderMan
                        || target instanceof net.minecraft.world.entity.animal.SnowGolem
                        || target instanceof net.minecraft.world.entity.monster.Strider;
                boolean endCreature = isEndCreatureByIdentityOrNbt(target);
                if (waterSensitive || endCreature) {
                    hurtNoCascade(target, player.damageSources().drown(), 12.0f);
                }
            }
            boolean firstWaterImpact = !arrow.getPersistentData()
                    .getBoolean("slb_water_arrow_impacted");
            if (firstWaterImpact) {
                arrow.getPersistentData().putBoolean("slb_water_arrow_impacted", true);
                if (event.getRayTraceResult()
                        instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                    growWaterArrowPlants(player.level(), blockHit.getLocation(), 4);
                    placeWaterArrowSource(player.level(), blockHit);
                }
            }
        }
        if (getEnchantLvl(bow, "deep_lucky_block:dry_arrow") > 0) {
            boolean firstDryImpact = !arrow.getPersistentData()
                    .getBoolean("slb_dry_arrow_impacted");
            if (firstDryImpact) {
                arrow.getPersistentData().putBoolean("slb_dry_arrow_impacted", true);
                if (event.getRayTraceResult()
                        instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                    applyDryArrowTerrain(player.level(), blockHit.getLocation(), 4);
                }
            }
        }
        // heavy_impact : carreau bélier percutant (+dégâts + recul lourd !)
        int hvyImpLvl = getEnchantLvl(bow, "deep_lucky_block:heavy_impact");
        if (hvyImpLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            hurtNoCascade(target, player.damageSources().arrow(arrow, player), 4.0f * hvyImpLvl);
            net.minecraft.world.phys.Vec3 push = arrow.getDeltaMovement().normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(push.x * 0.55 * hvyImpLvl, 0.22, push.z * 0.55 * hvyImpLvl));
            target.hurtMarked = true;
            EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.CRIT, 10, 0.3, 0.09);
        }

        // T306-OVERHAUL : teleport_arrow (téléportation instantanée) vs swap_arrow (VRAI échange de position corrigé !) vs grapple (traction cinétique de grappin !)
        if (getEnchantLvl(bow, "deep_lucky_block:teleport_arrow") > 0) {
            net.minecraft.world.phys.Vec3 hitLoc = event.getRayTraceResult().getLocation();
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.PORTAL, 12, 0.3, 0.2);
            player.fallDistance = 0.0f;
            player.teleportTo(hitLoc.x, hitLoc.y, hitLoc.z);
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.PORTAL, 12, 0.3, 0.2);
            player.level().playSound(null, hitLoc.x, hitLoc.y, hitLoc.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.1f);
        }
        int swapArrow = getEnchantLvl(bow, "deep_lucky_block:swap_arrow");
        if (swapArrow > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            double tx = target.getX(), ty = target.getY(), tz = target.getZ();
            player.fallDistance = 0.0f;
            target.fallDistance = 0.0f;
            target.teleportTo(px, py, pz);
            player.teleportTo(tx, ty, tz);
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.REVERSE_PORTAL, 14, 0.3, 0.06);
            EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.REVERSE_PORTAL, 14, 0.3, 0.06);
            player.level().playSound(null, tx, ty, tz, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        if (getEnchantLvl(bow, "deep_lucky_block:grapple") > 0) {
            net.minecraft.world.phys.Vec3 hitLoc = event.getRayTraceResult().getLocation();
            if (player.level() instanceof ServerLevel slgrp) {
                EnchantmentParticleFX.tetherBeam(slgrp, player.position().add(0, 1.0, 0), hitLoc,
                        EnchantmentParticleFX.STEEL_SILVER, EnchantmentParticleFX.EARTH_OCHRE, 0.55);
            }
            if (player.isShiftKeyDown() && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
                net.minecraft.world.phys.Vec3 pull = player.position().subtract(target.position());
                double len = Math.max(1.0, pull.length());
                double speed = Math.min(2.6, 0.55 + len * 0.14);
                target.setDeltaMovement(pull.normalize().scale(speed).add(0, 0.28, 0));
                target.hurtMarked = true;
            } else {
                net.minecraft.world.phys.Vec3 pull = hitLoc.subtract(player.position());
                double len = Math.max(1.0, pull.length());
                double speed = Math.min(2.8, 0.65 + len * 0.16);
                player.setDeltaMovement(pull.normalize().scale(speed).add(0, 0.25, 0));
                player.fallDistance = 0.0f;
                player.hurtMarked = true;
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.9f, 1.4f);
        }

        if (getEnchantLvl(bow, "deep_lucky_block:transfer") > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player targetPlayer) {
            ItemStack off = player.getOffhandItem();
            if (!off.isEmpty()) {
                ItemStack copy = off.copy();
                off.setCount(0);
                if (!targetPlayer.getInventory().add(copy)) targetPlayer.drop(copy, false);
            }
        }
        int retLvl = getEnchantLvl(bow, "deep_lucky_block:retrieval");
        if (retLvl > 0 && player.level().random.nextInt(100) < (10 * retLvl) && player instanceof Player retPlayer) retPlayer.getInventory().add(new ItemStack(Items.ARROW));

        // T306-OVERHAUL : storm_strike (foudre céleste garantie + arc d'orage !) vs thunderstruck (surcharge paralysante électrique sur cible !)
        if (getEnchantLvl(bow, "deep_lucky_block:storm_strike") > 0) {
            net.minecraft.world.phys.Vec3 loc = event.getRayTraceResult().getLocation();
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, player.level());
            bolt.moveTo(loc);
            player.level().addFreshEntity(bolt);
            if (player.level().isRaining() && player.level() instanceof ServerLevel slst) {
                EnchantmentParticleFX.shockwaveRing(slst, loc.x, loc.y + 0.1, loc.z, ParticleTypes.ELECTRIC_SPARK, 3.0, 18, 0.08);
            }
        }
        int thunderLvl = getEnchantLvl(bow, "deep_lucky_block:thunderstruck");
        if (thunderLvl > 0) {
            net.minecraft.world.phys.Vec3 loc = event.getRayTraceResult().getLocation();
            if (event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
                hurtNoCascade(target, player.damageSources().magic(), 3.0f * thunderLvl);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
                EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.ELECTRIC_SPARK, 14, 0.35, 0.10);
            } else if (player.level() instanceof ServerLevel slth) {
                EnchantmentParticleFX.shockwaveRing(slth, loc.x, loc.y + 0.1, loc.z, ParticleTypes.ELECTRIC_SPARK, 1.8, 12, 0.06);
            }
        }

        int sniperLvl = getEnchantLvl(bow, "deep_lucky_block:sniper");
        if (sniperLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            double dist = player.distanceTo(target);
            if (dist >= 15.0) hurtNoCascade(target, player.damageSources().arrow(arrow, player), (float)(dist * 0.5 * sniperLvl));
        }
        int chaosLvl = getEnchantLvl(bow, "deep_lucky_block:chaos");
        if (chaosLvl > 0) {
            net.minecraft.core.Holder<MobEffect> chaosEffect = CHAOS_EFFECTS[player.level().random.nextInt(CHAOS_EFFECTS.length)];
            double chx = event.getRayTraceResult().getLocation().x;
            double chy = event.getRayTraceResult().getLocation().y;
            double chz = event.getRayTraceResult().getLocation().z;
            net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(player.level(), chx, chy, chz);
            cloud.setParticle(ParticleTypes.WITCH);
            cloud.setRadius(3.5f);
            cloud.setDuration(10);
            cloud.setWaitTime(0);
            player.level().addFreshEntity(cloud);

            ServerLevel chsl = (ServerLevel) player.level();
            final LivingEntity chPlayer = player;
            final net.minecraft.core.Holder<MobEffect> fChaosEffect = chaosEffect;
            for (int t = 0; t <= 1; t++) {
                final int delay = t * 10;
                TestProcedure.schedule(chsl, TestProcedure.currentTick(chsl) + delay, () -> {
                    AABB chBox = new AABB(chx - 3.5, chy - 2, chz - 3.5, chx + 3.5, chy + 4, chz + 3.5);
                    for (LivingEntity le : chsl.getEntitiesOfClass(LivingEntity.class, chBox)) {
                        if (le == chPlayer || !le.isAlive()) continue;
                        long immuneUntil = le.getPersistentData().getLong("slb_chaos_immune");
                        if (immuneUntil > chsl.getGameTime()) continue;
                        le.getPersistentData().putLong("slb_chaos_immune", chsl.getGameTime() + 20);
                        le.addEffect(new MobEffectInstance(fChaosEffect, 40, 0));
                    }
                });
            }
        }

        // T306-OVERHAUL : delay (charge à retardement tic-tac 1.5s !) vs brimstone (soufre infernal Wither+Feu !) vs meteor (chute de météore céleste !)
        int delayLvl = getEnchantLvl(bow, "deep_lucky_block:delay");
        if (delayLvl > 0 && player.level() instanceof ServerLevel sldel) {
            final net.minecraft.world.phys.Vec3 dLoc = event.getRayTraceResult().getLocation();
            final float dPow = Math.min(6.0f, 1.8f + 1.0f * delayLvl);
            final LivingEntity dOwner = player;
            for (int step = 0; step < 3; step++) {
                final int tickOff = step * 10;
                TestProcedure.schedule(sldel, TestProcedure.currentTick(sldel) + tickOff, () -> {
                    EnchantmentParticleFX.shockwaveRing(sldel, dLoc.x, dLoc.y + 0.1, dLoc.z, EnchantmentParticleFX.TEMPORAL_AMBER, 1.2, 10, 0.02);
                    sldel.playSound(null, dLoc.x, dLoc.y, dLoc.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
                });
            }
            TestProcedure.schedule(sldel, TestProcedure.currentTick(sldel) + 30, () -> {
                sldel.explode(dOwner, dLoc.x, dLoc.y, dLoc.z, dPow, Level.ExplosionInteraction.NONE);
                EnchantmentParticleFX.shockwaveRing(sldel, dLoc.x, dLoc.y + 0.1, dLoc.z, ParticleTypes.END_ROD, 2.4, 16, 0.06);
            });
        }
        int brimLvl = getEnchantLvl(bow, "deep_lucky_block:brimstone");
        if (brimLvl > 0 && player.level() instanceof ServerLevel slbrm) {
            net.minecraft.world.phys.Vec3 bLoc = event.getRayTraceResult().getLocation();
            double bRad = deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(2.8, 0.7, brimLvl);
            slbrm.explode(player, bLoc.x, bLoc.y, bLoc.z, Math.min(4.5f, 1.5f + 0.8f * brimLvl), Level.ExplosionInteraction.NONE);
            EnchantmentParticleFX.shockwaveRing(slbrm, bLoc.x, bLoc.y + 0.15, bLoc.z, ParticleTypes.LAVA, bRad, 16, 0.04);
            slbrm.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, bLoc.x, bLoc.y + 0.3, bLoc.z, 12, 0.8, 0.3, 0.8, 0.02);
            for (LivingEntity le : slbrm.getEntitiesOfClass(LivingEntity.class, new AABB(bLoc.x - bRad, bLoc.y - 2, bLoc.z - bRad, bLoc.x + bRad, bLoc.y + 3, bLoc.z + bRad))) {
                if (le != player) {
                    le.setRemainingFireTicks(100);
                    le.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, Math.min(2, brimLvl - 1)));
                }
            }
        }
        int meteorLvl = getEnchantLvl(bow, "deep_lucky_block:meteor");
        if (meteorLvl > 0 && player.level() instanceof ServerLevel slmet) {
            final net.minecraft.world.phys.Vec3 mLoc = event.getRayTraceResult().getLocation();
            final LivingEntity mOwner = player;
            for (int step = 0; step < 4; step++) {
                final int tickOff = step * 3;
                final double my = mLoc.y + 12.0 - step * 3.0;
                TestProcedure.schedule(slmet, TestProcedure.currentTick(slmet) + tickOff, () -> {
                    slmet.sendParticles(ParticleTypes.FLAME, mLoc.x, my, mLoc.z, 14, 0.35, 0.5, 0.35, 0.03);
                    slmet.sendParticles(ParticleTypes.LAVA, mLoc.x, my, mLoc.z, 5, 0.25, 0.3, 0.25, 0.02);
                    slmet.sendParticles(ParticleTypes.LARGE_SMOKE, mLoc.x, my + 0.6, mLoc.z, 6, 0.3, 0.3, 0.3, 0.01);
                });
            }
            TestProcedure.schedule(slmet, TestProcedure.currentTick(slmet) + 12, () -> {
                slmet.explode(mOwner, mLoc.x, mLoc.y, mLoc.z, 3.8f, true, Level.ExplosionInteraction.NONE);
                EnchantmentParticleFX.shockwaveRing(slmet, mLoc.x, mLoc.y + 0.15, mLoc.z, ParticleTypes.FLAME, 3.2, 20, 0.08);
            });
        }

        int warpLvl = getEnchantLvl(bow, "deep_lucky_block:warp");
        if (warpLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            EnchantmentParticleFX.burstOnEntity(target, ParticleTypes.PORTAL, 10, 0.3, 0.15);
            target.teleportTo(target.getX() + player.level().random.nextInt(16) - 8, target.getY(), target.getZ() + player.level().random.nextInt(16) - 8);
        }
        int pierceLvl = getEnchantLvl(bow, "deep_lucky_block:pierce");
        if (pierceLvl > 0) {
            CompoundTag pierceTag = arrow.getPersistentData();
            int hits = pierceTag.getInt("slb_pierce_hits");
            if (hits < pierceLvl) {
                pierceTag.putInt("slb_pierce_hits", hits + 1);
                if (event.getRayTraceResult() instanceof EntityHitResult ehr
                        && ehr.getEntity() instanceof LivingEntity target) {
                    hurtNoCascade(target, player.damageSources().arrow(arrow, player),
                            (float) arrow.getBaseDamage());
                    player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 1.0f, 1.2f);
                }
                event.setCanceled(true);
            }
        }
    }

    private static boolean isEndCreatureByIdentityOrNbt(LivingEntity target) {
        if (target == null) return false;
        try {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
            if (entityId != null
                    && entityId.toString().toLowerCase(Locale.ROOT).contains("end")) {
                return true;
            }

            CompoundTag snapshot = new CompoundTag();
            target.saveWithoutId(snapshot);
            return snapshot.toString().toLowerCase(Locale.ROOT).contains("end");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void placeWaterArrowSource(Level level,
            net.minecraft.world.phys.BlockHitResult blockHit) {
        if (level == null || blockHit == null || level.isClientSide()) return;

        BlockPos hitBlock = blockHit.getBlockPos();
        BlockPos preferred = hitBlock.relative(blockHit.getDirection());
        BlockState preferredState = level.getBlockState(preferred);

        if (preferredState.isAir() || preferredState.canBeReplaced()) {
            level.setBlockAndUpdate(preferred, Blocks.WATER.defaultBlockState());
            return;
        }

        for (int dy = 2; dy >= -2; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidate = hitBlock.offset(dx, dy, dz);
                    BlockState candidateState = level.getBlockState(candidate);
                    if ((candidateState.isAir() || candidateState.canBeReplaced())
                            && level.getBlockState(candidate.below()).blocksMotion()) {
                        level.setBlockAndUpdate(candidate, Blocks.WATER.defaultBlockState());
                        return;
                    }
                }
            }
        }
    }

    /** Water Arrow : croissance végétale dans une sphère de rayon 4 (T306-FIX : SHORT_GRASS et plus GRASS_BLOCK !). */
    private static void growWaterArrowPlants(Level level,
            net.minecraft.world.phys.Vec3 location, int radius) {
        if (level == null || location == null || level.isClientSide()) return;
        BlockPos center = BlockPos.containing(location);
        Block[] flowers = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
            Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
            Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY
        };

        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                    BlockPos grassPos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(grassPos).is(Blocks.GRASS_BLOCK)) continue;

                    BlockPos plantPos = grassPos.above();
                    if (!level.getBlockState(plantPos).isAir()) continue;
                    if (level.random.nextInt(100) >= 38) continue;

                    int roll = level.random.nextInt(100);
                    Block plant = roll < 68 ? Blocks.SHORT_GRASS
                            : roll < 78 ? Blocks.FERN
                            : flowers[level.random.nextInt(flowers.length)];
                    BlockState plantState = plant.defaultBlockState();
                    if (plantState.canSurvive(level, plantPos)) {
                        level.setBlockAndUpdate(plantPos, plantState);
                    }
                }
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    location.x, location.y, location.z,
                    36, 2.0, 1.2, 2.0, 0.10);
            serverLevel.sendParticles(ParticleTypes.COMPOSTER,
                    location.x, location.y, location.z,
                    28, 2.0, 1.0, 2.0, 0.08);
        }
        level.playSound(null, location.x, location.y, location.z,
                SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.1f);
    }

    /** Dry Arrow : assèche et stérilise une sphère de rayon 4. */
    private static void applyDryArrowTerrain(Level level,
            net.minecraft.world.phys.Vec3 location, int radius) {
        if (level == null || location == null || level.isClientSide()) return;
        BlockPos center = BlockPos.containing(location);
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);

                    if (state.is(Blocks.GRASS_BLOCK)) {
                        BlockState replacement = level.random.nextBoolean()
                                ? Blocks.ROOTED_DIRT.defaultBlockState()
                                : Blocks.COARSE_DIRT.defaultBlockState();
                        level.setBlockAndUpdate(pos, replacement);
                    } else if (state.is(Blocks.MAGMA_BLOCK)) {
                        level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                    } else if (state.is(Blocks.WATER)
                            || state.is(Blocks.LAVA)
                            || state.is(Blocks.FIRE)
                            || state.is(Blocks.SOUL_FIRE)) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    } else if (state.hasProperty(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                            && state.getValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                        level.setBlockAndUpdate(pos, state.setValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false));
                    } else if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
                            && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                            && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                        level.setBlockAndUpdate(pos, state.setValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));
                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.BushBlock
                            || state.is(Blocks.VINE)
                            || state.is(Blocks.CAVE_VINES)
                            || state.is(Blocks.CAVE_VINES_PLANT)
                            || state.is(Blocks.SEAGRASS)
                            || state.is(Blocks.TALL_SEAGRASS)
                            || state.is(Blocks.KELP)
                            || state.is(Blocks.KELP_PLANT)) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ASH,
                    location.x, location.y, location.z,
                    32, 1.2, 0.5, 1.2, 0.04);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    location.x, location.y, location.z,
                    18, 1.0, 0.4, 1.0, 0.03);
        }
        level.playSound(null, location.x, location.y, location.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.9f, 0.8f);
    }

    // 10.5 EFFETS DU BOUCLIER
    // LivingIncomingDamageEvent arrive à CHAQUE tentative de coup, avant que le blocage
    // vanilla ne stoppe le pipeline. C'est donc ici que Bash/Quake se déclenchent.
    @SubscribeEvent
    public static void onShieldAttackAttempt(LivingIncomingDamageEvent event){try{onShieldAttackAttemptImpl(event);}catch(Throwable t__){guard("onShieldAttackAttempt",t__);}}
    private static void onShieldAttackAttemptImpl(LivingIncomingDamageEvent event){
        if (HURT_GUARD.get()) return;
        try {
            // FIX (mob-equipment-enchant generalization) : generalise a
            // LivingEntity -- un mob capable de bloquer avec un bouclier
            // enchante (Piglin Brute, Illager, etc.) beneficie desormais
            // de Bash/Quake/Hatred Draw comme un joueur.
            if (!(event.getEntity() instanceof LivingEntity player)) return;
            if (player.level().isClientSide() || !player.isBlocking()) return;
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) return;

            ItemStack shield = findActiveEnchantedShield(player);
            if (shield.isEmpty()) return;

            // N'applique les effets que si l'attaque arrive réellement de face.
            net.minecraft.world.phys.Vec3 sourcePos = event.getSource().getSourcePosition();
            if (sourcePos == null) return;
            net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
            net.minecraft.world.phys.Vec3 towardSource = sourcePos.subtract(player.position());
            look = new net.minecraft.world.phys.Vec3(look.x, 0.0, look.z).normalize();
            towardSource = new net.minecraft.world.phys.Vec3(
                    towardSource.x, 0.0, towardSource.z).normalize();
            if (look.dot(towardSource) <= 0.0) return;

            int bashPower = getEnchantLvl(shield, "deep_lucky_block:bash_power");
            int bashDamage = getEnchantLvl(shield, "deep_lucky_block:bash_damage");
            int shieldQuake = getEnchantLvl(shield, "deep_lucky_block:shield_quake");
            int hatredDraw = getEnchantLvl(shield, "deep_lucky_block:hatred_draw");

            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                if (bashDamage > 0) {
                    hurtNoCascade(attacker, player.damageSources().thorns(player),
                            3.0f * bashDamage);
                    EnchantmentParticleFX.burstOnEntity(attacker, ParticleTypes.CRIT, 6, 0.25, 0.08);
                }

                // T306-OVERHAUL : bash_power (bourrade ciblée + étourdissement) vs shield_quake (onde sismique radiale de bouclier !)
                if (bashPower > 0) {
                    double force = deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.velocity(0.65, 0.45, bashPower);
                    net.minecraft.world.phys.Vec3 away = attacker.position().subtract(player.position());
                    away = new net.minecraft.world.phys.Vec3(away.x, 0.0, away.z).normalize();
                    attacker.setDeltaMovement(attacker.getDeltaMovement().add(away.x * force, 0.22 + 0.04 * force, away.z * force));
                    attacker.hurtMarked = true;
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
            }
            if (shieldQuake > 0) {
                double qRad = deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(3.5, 1.0, shieldQuake);
                if (player.level() instanceof ServerLevel slsq) {
                    EnchantmentParticleFX.shockwaveRing(slsq, player.getX(), player.getY() + 0.1, player.getZ(),
                            new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), qRad, 18, 0.06);
                    slsq.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55f, 0.75f);
                }
                for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(qRad))) {
                    if (nearby != player && !player.isAlliedTo(nearby)) {
                        net.minecraft.world.phys.Vec3 away = nearby.position().subtract(player.position());
                        if (away.horizontalDistanceSqr() > 0.001) {
                            net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(away.x, 0.0, away.z).normalize();
                            double push = Math.min(2.4, 0.55 + 0.25 * shieldQuake);
                            nearby.setDeltaMovement(nearby.getDeltaMovement().add(flat.x * push, 0.30, flat.z * push));
                            nearby.hurtMarked = true;
                        }
                        hurtNoCascade(nearby, player.damageSources().thorns(player), 1.5f * shieldQuake);
                    }
                }
            }

            if (hatredDraw > 0) {
                for (Mob mob : player.level().getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(10.0))) {
                    if (!mob.getPersistentData().hasUUID("slb_ally_owner")) {
                        mob.setTarget(player);
                    }
                }
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.ANGRY_VILLAGER, 4, 0.35, 0.02);
            }
        } catch (Throwable t) {
            printHandlerError("onShieldAttackAttempt", t);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event){try{onShieldBlockImpl(event);}catch(Throwable t__){guard("onShieldBlock",t__);}}
    private static void onShieldBlockImpl(LivingShieldBlockEvent event){
        try {
            if (!(event.getEntity() instanceof LivingEntity player)) return;
            if (player.level().isClientSide()) return;
            ItemStack shield = findActiveEnchantedShield(player);
            if (!shield.isEmpty()
                    && getEnchantLvl(shield, "deep_lucky_block:shield_efficiency") > 0) {
                event.setShieldDamage(0.0f);
            }
        } catch (Throwable t) {
            printHandlerError("onShieldBlock", t);
        }
    }

    private static ItemStack findActiveEnchantedShield(LivingEntity player) {
        ItemStack active = player.getUseItem();
        if (!active.isEmpty()) return active;
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) return offhand;
        return player.getMainHandItem();
    }

    // 11. Defense du joueur
    @SubscribeEvent
    public static void onPlayerHurt(LivingIncomingDamageEvent event){try{onPlayerHurtImpl(event);}catch(Throwable t__){guard("onPlayerHurt",t__);}}
    private static void onPlayerHurtImpl(LivingIncomingDamageEvent event){
        if (HURT_GUARD.get()) return;
        try {
            handleDefensiveHurt(event);
        } catch (Throwable t) {
            printHandlerError("onPlayerHurt", t);
        }
    }

    private static void handleDefensiveHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity player)) return;
        if (player.level().isClientSide()) return;
        long nowTick = player.level().getGameTime();
        player.getPersistentData().putLong("slb_last_hurt", nowTick);

        // Protection anti-tir-ami des troupes invoquées loyales (slb_ally_owner)
        if (event.getSource().getEntity() instanceof Mob srcMob
                && srcMob.getPersistentData().hasUUID("slb_ally_owner")
                && srcMob.getPersistentData().getUUID("slb_ally_owner").equals(player.getUUID())) {
            event.setCanceled(true);
            srcMob.setTarget(null);
            return;
        }

        // T306-FIX : cursed_blade ("Lame Maudite de Cristal" : 10x dégâts mais subir >= 1 dégât brise le porteur en éclats de cristal !)
        if (getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:cursed_blade") > 0 && event.getAmount() >= 1.0f) {
            event.setAmount(Math.max(event.getAmount(), player.getMaxHealth() + player.getAbsorptionAmount() + 100.0f));
            if (player.level() instanceof ServerLevel slcb) {
                slcb.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()),
                        player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 24, 0.35, 0.45, 0.35, 0.08);
                slcb.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
            }
        }

        // lone_wolf : instinct de survie solitaire (-12% dégâts reçus sans joueur allié dans 16 blocs)
        int loneDef = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:lone_wolf");
        if (loneDef > 0 && player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(16.0), p2 -> p2 != player).isEmpty()) {
            event.setAmount(event.getAmount() * 0.88f);
        }
        // T310-UNIQUE : solo_combat (Maître du Duel : -10 %/niv de dégâts reçus contre l'adversaire de duel 1v1)
        int soloDef = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:solo_combat");
        if (soloDef > 0 && nowTick <= player.getPersistentData().getLong("slb_duel_until")
                && player.getPersistentData().hasUUID("slb_duel_partner")
                && event.getSource().getEntity() != null
                && player.getPersistentData().getUUID("slb_duel_partner").equals(event.getSource().getEntity().getUUID())) {
            event.setAmount(event.getAmount() * Math.max(0.45f, 1.0f - 0.10f * soloDef));
        }
        // T310-UNIQUE : burst_step (Contre-Impulsion en encaissant un coup : Vitesse II + dégagement)
        int burstDef = getArmorEnchantLvl(player, "deep_lucky_block:burst_step");
        if (burstDef > 0 && event.getSource().getEntity() != null && nowTick >= player.getPersistentData().getLong("slb_burst_ready")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 1, true, false));
            player.getPersistentData().putLong("slb_burst_ready", nowTick + Math.max(35L, 70L - 10L * burstDef));
            EnchantmentParticleFX.ankleTrail(player, ParticleTypes.ELECTRIC_SPARK, 6, 0.06);
        }

        // Esquive cinétique de projectiles : dash (pendant la ruée) & strafe (en déplacement latéral)
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            if (nowTick <= player.getPersistentData().getLong("slb_dash_dodge_until")) {
                event.setCanceled(true);
                EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.SMALL_GUST, 8, 0.3, 0.04);
                return;
            }
            int strafeDef = getArmorEnchantLvl(player, "deep_lucky_block:strafe");
            if (strafeDef > 0 && player.level().random.nextInt(100) < Math.min(45, 15 * strafeDef)) {
                net.minecraft.world.phys.Vec3 mv = player.getDeltaMovement();
                if (mv.horizontalDistanceSqr() > 0.002) {
                    event.setCanceled(true);
                    EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.CLOUD, 6, 0.25, 0.03);
                    return;
                }
            }
        }

        // T307-BOUNCY : Immunité totale aux dégâts de chute + vrai rebond de bloc de slime (handleSharedClientServerMovement + filet serveur)
        int bouncyLvl = getArmorEnchantLvl(player, "deep_lucky_block:bouncy");
        if (bouncyLvl > 0 && event.getSource().getMsgId().equals("fall")) {
            event.setCanceled(true);
            if (!player.isShiftKeyDown() && player.getDeltaMovement().y <= 0.05) {
                double vyBounce = Math.min(2.0, 0.52 + 0.16 * bouncyLvl);
                player.setDeltaMovement(player.getDeltaMovement().x, vyBounce, player.getDeltaMovement().z);
                player.hurtMarked = true;
            }
            if (player.level() instanceof ServerLevel slSlime) {
                EnchantmentParticleFX.shockwaveRing(slSlime, player.getX(), player.getY() + 0.06, player.getZ(),
                        ParticleTypes.ITEM_SLIME, 0.85, 12, 0.05);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.85f, 1.05f);
            return;
        }
        int stickyLvl = getArmorEnchantLvl(player, "deep_lucky_block:sticky");
        if (stickyLvl > 0) player.setDeltaMovement(player.getDeltaMovement().scale(0.1));

        // T306-OVERHAUL : undead_commander (-50% morts-vivants), illager_commander (-50% illageois/sorcières), veil (-35% magie/projectiles + voile d'ombre !)
        int undeadCmd = getArmorEnchantLvl(player, "deep_lucky_block:undead_commander");
        int illagerCmd = getArmorEnchantLvl(player, "deep_lucky_block:illager_commander");
        int veilLvl = getArmorEnchantLvl(player, "deep_lucky_block:veil");
        if (undeadCmd > 0 && event.getSource().getEntity() instanceof LivingEntity srcU && srcU.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
            event.setAmount(event.getAmount() * 0.5f);
        }
        if (illagerCmd > 0 && event.getSource().getEntity() instanceof LivingEntity srcI && srcI.getType().is(net.minecraft.tags.EntityTypeTags.ILLAGER)) {
            event.setAmount(event.getAmount() * 0.5f);
        }
        if (veilLvl > 0) {
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                    || event.getSource().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) {
                event.setAmount(event.getAmount() * 0.55f);
            } else {
                event.setAmount(event.getAmount() * 0.82f);
            }
            EnchantmentParticleFX.burstOnEntity(player, EnchantmentParticleFX.SHADOW_VIOLET, 6, 0.30, 0.02);
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.SCULK_SOUL, 2, 0.24, 0.01);
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            int wardenLvl = getArmorEnchantLvl(player, "deep_lucky_block:wardenspine");
            if (wardenLvl > 0) {
                hurtNoCascade(attacker, player.damageSources().thorns(player), 2.5f * wardenLvl);
                EnchantmentParticleFX.burstOnEntity(attacker, ParticleTypes.SONIC_BOOM, 1, 0.0, 0.0);
            }
            int punchLvl = getArmorEnchantLvl(player, "deep_lucky_block:punching_armor");
            if (punchLvl > 0) attacker.knockback(0.5f * punchLvl, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
            int fearLvl = getArmorEnchantLvl(player, "deep_lucky_block:inner_fear");
            if (fearLvl > 0) {
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, fearLvl - 1));
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, fearLvl - 1));
            }

            int zephyrsGambit = getArmorEnchantLvl(player, "deep_lucky_block:zephyrs_gambit");
            boolean attackerCrit = attacker.fallDistance > 0.0F && !attacker.onGround() && !attacker.onClimbable() && !attacker.isInWater();
            if (zephyrsGambit > 0 && attackerCrit
                    && player.level().random.nextInt(100) < (20 + 10 * zephyrsGambit)
                    && player.level() instanceof ServerLevel zephyrLevel) {
                double angle = player.level().random.nextDouble() * Math.PI * 2.0;
                double dist = 2.5 + player.level().random.nextDouble() * 2.0;
                double nx = player.getX() + Math.cos(angle) * dist;
                double nz = player.getZ() + Math.sin(angle) * dist;
                zephyrLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
                player.teleportTo(nx, player.getY(), nz);
                zephyrLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
                event.setAmount(event.getAmount() * 0.25f);
            }
        }
        int resSustain = getArmorEnchantLvl(player, "deep_lucky_block:resistance_sustain");
        if (resSustain > 0 && player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, resSustain, true, false));
        int oceanBless = getArmorEnchantLvl(player, "deep_lucky_block:oceans_blessing");
        if (oceanBless > 0 && player.isInWater()) event.setAmount(event.getAmount() * 0.60f);

        int shadowHeartDef = getElementalHeartLvl(player, "shadow_heart");
        if (shadowHeartDef > 0 && player.level().random.nextInt(100) < (8 * shadowHeartDef)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, true));
        }

        int windHeartLvl = getElementalHeartLvl(player, "wind_heart");
        if (windHeartLvl > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            event.setAmount(event.getAmount() * Math.max(0f, 1f - 0.15f * windHeartLvl));
            if (player.level() instanceof ServerLevel windLevel) {
                windLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                        10, 0.3, 0.3, 0.3, 0.03);
            }
        }

        int earthHeartLvl = getElementalHeartLvl(player, "earth_heart");
        if (earthHeartLvl > 0) {
            if (player.level().random.nextInt(100) < (6 * earthHeartLvl)) {
                event.setCanceled(true);
                if (player.level() instanceof ServerLevel earthLevel) {
                    earthLevel.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                    ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
                            player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                            16, 0.4, 0.4, 0.4, 0.05);
                }
            } else {
                event.setAmount(event.getAmount() * (1f - Math.min(0.30f, 0.08f * earthHeartLvl)));
            }
        }
        int blzRep = getArmorEnchantLvl(player, "deep_lucky_block:blaze_repulsion_heart");
        int burnHeart = getArmorEnchantLvl(player, "deep_lucky_block:burning_heart");
        int fireShell = getArmorEnchantLvl(player, "deep_lucky_block:fire_shell");
        if (blzRep > 0 && event.getAmount() > player.getMaxHealth() * 0.05f && event.getSource().getEntity() instanceof LivingEntity attacker) {
            hurtNoCascade(attacker, player.damageSources().thorns(player), event.getAmount());
            attacker.setRemainingFireTicks((3) * 20);
        }
        if (burnHeart > 0) player.setRemainingFireTicks((5 * burnHeart) * 20);
        if (fireShell > 0 && player.isOnFire()) event.setAmount(event.getAmount() * (1f - 0.25f * fireShell));
        int steelBody = getArmorEnchantLvl(player, "deep_lucky_block:steel_body");
        if (steelBody > 0 && player.getHealth() > 20f) event.setAmount(event.getAmount() * (1f - Math.min(0.80f, (player.getHealth() - 20f) * 0.01f)));

        // T306-OVERHAUL : Défense des Enchantements Corporels uniques (tempered_body, hundred_battles_veteran, gluttony, corpulent, wing_toughness_armor)
        int tempBody = getArmorEnchantLvl(player, "deep_lucky_block:tempered_body");
        if (tempBody > 0) {
            event.setAmount(event.getAmount() * Math.max(0.65f, 1.0f - 0.08f * tempBody));
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) event.setAmount(event.getAmount() * 0.80f);
        }
        int hundVetDef = getArmorEnchantLvl(player, "deep_lucky_block:hundred_battles_veteran");
        if (hundVetDef > 0) {
            int kills = player.getPersistentData().getInt("slb_veteran_kills");
            float vetRed = Math.min(0.20f, 0.05f + kills * 0.0015f);
            event.setAmount(event.getAmount() * (1.0f - vetRed));
        }
        int glutDef = getArmorEnchantLvl(player, "deep_lucky_block:gluttony");
        if (glutDef > 0 && player instanceof Player gp && gp.getFoodData().getFoodLevel() >= 16) {
            event.setAmount(event.getAmount() * 0.88f);
        }
        int corpDef = getArmorEnchantLvl(player, "deep_lucky_block:corpulent");
        if (corpDef > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            event.setAmount(event.getAmount() * 0.75f);
        }
        int wingToughDef = getArmorEnchantLvl(player, "deep_lucky_block:wing_toughness_armor");
        if (wingToughDef > 0 && (!player.onGround() || player.isFallFlying())) {
            event.setAmount(event.getAmount() * 0.72f);
        }

        // T306-OVERHAUL : panic_escape (sprint immédiat quand frappé !) séparé de last_stand_escape
        int panicEsc = getArmorEnchantLvl(player, "deep_lucky_block:panic_escape");
        if (panicEsc > 0 && !event.getSource().getMsgId().equals("fall")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, Math.min(3, panicEsc), true, false));
            EnchantmentParticleFX.ankleTrail(player, ParticleTypes.SMALL_GUST, 4, 0.04);
        }

        // T306-OVERHAUL : cloud_walker (immunité chute sur nuage doux) vs twenty_fourth_blessing (grâce miraculeuse : survit toujours à une chute mortelle à >= 2 PV + Régén !)
        int cloudWalk = getArmorEnchantLvl(player, "deep_lucky_block:cloud_walker");
        if (cloudWalk > 0 && event.getSource().getMsgId().equals("fall")) {
            event.setCanceled(true);
            if (player.level() instanceof ServerLevel slcw) {
                EnchantmentParticleFX.cloudCushion(slcw, player.getX(), player.getY() + 0.05, player.getZ(), 1.05, true);
            }
            return;
        }
        int twentyFourth = getArmorEnchantLvl(player, "deep_lucky_block:twenty_fourth_blessing");
        if (twentyFourth > 0 && event.getSource().getMsgId().equals("fall")) {
            float reduced = event.getAmount() * 0.45f;
            if (player.getHealth() - reduced <= 2.0f) {
                event.setAmount(Math.max(0.0f, player.getHealth() - 2.0f));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 1, true, false));
                EnchantmentParticleFX.crownHalo(player, EnchantmentParticleFX.GOLD_DIVINE, 10);
            } else {
                event.setAmount(reduced);
            }
        }
        int crashLand = getArmorEnchantLvl(player, "deep_lucky_block:crash_landing");
        if (crashLand > 0 && event.getSource().getMsgId().equals("fall")) event.setAmount(event.getAmount() * 2.4f);
        if (event.getSource().getMsgId().equals("fall")) {
            int lightFall = getArmorEnchantLvl(player, "deep_lucky_block:lightness");
            int basicLightFall = getArmorEnchantLvl(player, "deep_lucky_block:basic_lightness");
            if (lightFall > 0) event.setAmount(event.getAmount() * Math.max(0.0f, 1f - 0.30f * lightFall));
            else if (basicLightFall > 0) event.setAmount(event.getAmount() * Math.max(0.1f, 1f - 0.15f * basicLightFall));
        }

        // Malédictions défensives différenciées : chest_tightness, fracture, crippled (lenteur quand blessé !), armor_damage vs armor_shatter
        int chestTight = getArmorEnchantLvl(player, "deep_lucky_block:chest_tightness");
        if (chestTight > 0 && player.level().random.nextInt(100) < 40) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 0));
        int crippledCurse = getArmorEnchantLvl(player, "deep_lucky_block:crippled");
        if (crippledCurse > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
        }
        int frac = getArmorEnchantLvl(player, "deep_lucky_block:fracture");
        if (frac > 0 && event.getSource().getMsgId().equals("fall")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 2));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        }
        int armorDmg = getArmorEnchantLvl(player, "deep_lucky_block:armor_damage");
        int armorShat = getArmorEnchantLvl(player, "deep_lucky_block:armor_shatter");
        if (armorDmg > 0) {
            for (ItemStack s : player.getArmorSlots()) {
                if (!s.isEmpty() && getEnchantLvl(s, "deep_lucky_block:armor_damage") > 0 && player.level() instanceof ServerLevel sla) {
                    s.hurtAndBreak(2 * armorDmg, sla, player, item -> {});
                }
            }
        }
        if (armorShat > 0 && player.level().random.nextInt(100) < Math.min(60, 14 * armorShat)) {
            for (ItemStack s : player.getArmorSlots()) {
                if (!s.isEmpty() && getEnchantLvl(s, "deep_lucky_block:armor_shatter") > 0 && player.level() instanceof ServerLevel sls) {
                    s.hurtAndBreak(Math.max(3, (int) (s.getMaxDamage() * 0.08f * armorShat)), sls, player, item -> {});
                }
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.7f);
        }

        int lightCalam = getArmorEnchantLvl(player, "deep_lucky_block:lightning_calamity");
        if (lightCalam > 0 && player.level().random.nextInt(100) < 5) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, player.level());
            bolt.moveTo(player.getX(), player.getY(), player.getZ());
            player.level().addFreshEntity(bolt);
        }
        int expProt = getArmorEnchantLvl(player, "deep_lucky_block:explosion_protection");
        if (expProt > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) event.setAmount(event.getAmount() * Math.max(0.0f, 1.0f - 0.20f * expProt));
        int foodHeaven = getArmorEnchantLvl(player, "deep_lucky_block:food_is_heaven");
        if (foodHeaven > 0 && player.isUsingItem() && (player.getUseItem().has(DataComponents.FOOD) || player.getUseItem().is(Items.POTION))) { event.setCanceled(true); return; }

        int resBurst = getArmorEnchantLvl(player, "deep_lucky_block:resistance_burst");
        if (resBurst > 0 && player.level().random.nextInt(100) < (10 * resBurst)) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
        int painDev = getArmorEnchantLvl(player, "deep_lucky_block:pain_devour");
        if (painDev > 0 && player instanceof Player painDevPlayer) painDevPlayer.getFoodData().setFoodLevel(Math.min(20, painDevPlayer.getFoodData().getFoodLevel() + 2 * painDev));

        // T306-OVERHAUL : block (parade martiale d'armure) vs big_stomach (rebond ventral de sumo !) vs life_block (garde vitale d'urgence qui soigne !)
        int blockLvl = getArmorEnchantLvl(player, "deep_lucky_block:block");
        if (blockLvl > 0 && player.level().random.nextInt(100) < Math.min(45, 12 + 10 * blockLvl)) {
            event.setCanceled(true);
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.CRIT, 8, 0.3, 0.08);
            player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
            return;
        }
        int bigStomLvl = getArmorEnchantLvl(player, "deep_lucky_block:big_stomach");
        if (bigStomLvl > 0 && player.level().random.nextInt(100) < 25) {
            event.setAmount(event.getAmount() * 0.45f);
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                attacker.knockback(1.25f, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                attacker.hurtMarked = true;
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.9f, 0.8f);
        }
        int lifeBlockLvl = Math.max(getArmorEnchantLvl(player, "deep_lucky_block:life_block"),
                getEnchantLvl(findActiveEnchantedShield(player), "deep_lucky_block:life_block"));
        if (lifeBlockLvl > 0 && (player.getHealth() - event.getAmount() <= player.getMaxHealth() * 0.35f)
                && nowTick >= player.getPersistentData().getLong("slb_life_block_ready")) {
            player.getPersistentData().putLong("slb_life_block_ready", nowTick + 160L);
            event.setAmount(event.getAmount() * 0.25f);
            player.heal(Math.min(10.0f, 2.0f + 2.0f * lifeBlockLvl));
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.HEART, 6, 0.35, 0.03);
            player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.45f, 1.6f);
        }

        int pwrThorns = getArmorEnchantLvl(player, "deep_lucky_block:powerful_thorns");
        if (pwrThorns > 0 && event.getSource().getEntity() instanceof LivingEntity attacker)
            hurtNoCascade(attacker, player.damageSources().thorns(player),
                    (float) (event.getAmount() * Math.min(1_073_741_824.0, Math.pow(2, Math.max(0, pwrThorns - 1)))));
        int soulRep = getArmorEnchantLvl(player, "deep_lucky_block:soul_repulsion_blast");
        if (soulRep > 0) {
            EnchantmentParticleFX.burstOnEntity(player, ParticleTypes.SOUL_FIRE_FLAME, 10, 0.4, 0.05);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(2.5))) {
                hurtNoCascade(mob, player.damageSources().magic(), event.getAmount() * 0.5f);
                mob.knockback(0.6, player.getX() - mob.getX(), player.getZ() - mob.getZ());
            }
        }
        int ironLung = getArmorEnchantLvl(player, "deep_lucky_block:iron_lung");
        if (ironLung > 0 && event.getSource().getMsgId().equals("inWall")) event.setCanceled(true);
        int lastStand = getArmorEnchantLvl(player, "deep_lucky_block:last_stand_toughness");
        if (lastStand > 0 && player.getHealth() < (player.getMaxHealth() * 0.20f)) event.setAmount(event.getAmount() * (1f - 0.15f * lastStand));
        int blazeSanc = getArmorEnchantLvl(player, "deep_lucky_block:blaze_sanctuary");
        if (blazeSanc > 0 && (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE) || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE))) event.setAmount(event.getAmount() * 0.2f);
        int fireProtEnh = getArmorEnchantLvl(player, "deep_lucky_block:fire_protection_enhancement");
        if (fireProtEnh > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) event.setAmount(event.getAmount() * Math.max(0.10f, 1f - 0.12f * fireProtEnh));
        int projProtEnh = getArmorEnchantLvl(player, "deep_lucky_block:projectile_protection_enhancement");
        if (projProtEnh > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) event.setAmount(event.getAmount() * Math.max(0.10f, 1f - 0.12f * projProtEnh));
        int infernal = getArmorEnchantLvl(player, "deep_lucky_block:infernal_armor");
        if (infernal > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) { event.setAmount(event.getAmount() * 0.1f); player.clearFire(); }

        int salamandersVow = getArmorEnchantLvl(player, "deep_lucky_block:salamanders_vow");
        if (salamandersVow > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && !player.isInWater()) {
            event.setAmount(0f);
            player.heal(0.5f + 0.5f * salamandersVow);
            if (player.level() instanceof ServerLevel salamanderLevel) {
                salamanderLevel.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                        8, 0.3, 0.4, 0.3, 0.01);
            }
        }

        int riptideCurse = getArmorEnchantLvl(player, "deep_lucky_block:riptide_curse");
        if (riptideCurse > 0 && !player.isInWaterOrRain()) {
            event.setAmount(event.getAmount() * (1f + 0.20f * riptideCurse));
        }
        int altHyp = getArmorEnchantLvl(player, "deep_lucky_block:high_altitude_hypoxia");
        int deepHyp = getArmorEnchantLvl(player, "deep_lucky_block:deep_hypoxia");
        if (altHyp > 0 && player.getY() > 100) event.setAmount(event.getAmount() * 0.7f);
        if (deepHyp > 0 && player.getY() < 20) event.setAmount(event.getAmount() * 0.7f);

        int radiantWard = getArmorEnchantLvl(player, "deep_lucky_block:radiant_ward");
        if (radiantWard > 0) {
            if (player.level().random.nextInt(100) < (5 + 5 * radiantWard)) {
                event.setCanceled(true);
                if (player.level() instanceof ServerLevel radiantLevel) {
                    radiantLevel.sendParticles(ParticleTypes.END_ROD,
                            player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                            30, 0.5, 0.6, 0.5, 0.05);
                    radiantLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 0.5f, 1.8f);
                }
            }
            if (player.tickCount % 100 == 0) {
                player.removeEffect(MobEffects.POISON);
                player.removeEffect(MobEffects.WITHER);
                if (radiantWard >= 2) player.removeEffect(MobEffects.WEAKNESS);
                if (radiantWard >= 3) player.removeEffect(MobEffects.BLINDNESS);
            }
        }

        int umbralPact = getArmorEnchantLvl(player, "deep_lucky_block:umbral_pact");
        if (umbralPact > 0) {
            boolean daylight = player.level().isDay() && player.level().canSeeSky(player.blockPosition());
            if (daylight) {
                event.setAmount(event.getAmount() * (1f + 0.12f * umbralPact));
            } else {
                event.setAmount(event.getAmount() * Math.max(0.4f, 1f - 0.15f * umbralPact));
            }
        }
    }

    /** Attribute bonus stacks naturally with vanilla Speed potion amplifiers. */
    private static void setStackingMovementBonus(LivingEntity player, net.minecraft.resources.ResourceLocation id,
            double amount) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attribute =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(id);
        if (Math.abs(amount) > 0.0001) {
            attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    id, amount,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static int softCappedLevel(Enchantment ench, int storedLevel) {
        return deepluckyblock.util.DeepEnchant.softCappedLevel(ench, storedLevel);
    }

    private static int getEnchantLvl(ItemStack stack, String id) {
        if (stack.isEmpty() || !stack.isEnchanted()) return 0;
        net.minecraft.world.item.enchantment.ItemEnchantments ie = stack.get(DataComponents.ENCHANTMENTS);
        if (ie != null && !ie.isEmpty()) {
            for (var entry : ie.entrySet()) {
                int rawLvl = entry.getIntValue();
                if (rawLvl <= 0) continue;
                var keyOpt = entry.getKey().unwrapKey();
                if (keyOpt.isPresent() && id.equals(keyOpt.get().location().toString())) {
                    return softCappedLevel(entry.getKey().value(), rawLvl);
                }
            }
        }
        Enchantment en = deepluckyblock.util.DeepEnchant.byId(id);
        if (en == null) return 0;
        int lvl = deepluckyblock.util.DeepEnchant.getLevel(stack, en);
        if (lvl <= 0) return 0;
        return softCappedLevel(en, lvl);
    }

    public static int getArmorEnchantLvl(LivingEntity entity, String id) {
        int total = 0;
        for (ItemStack armor : entity.getArmorSlots()) total = Math.max(total, getEnchantLvl(armor, id));
        return total;
    }

    private static int getElementalHeartLvl(LivingEntity player, String elementId) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int raw = getEnchantLvl(chest, "deep_lucky_block:" + elementId);
        if (raw <= 0) return 0;
        return deepluckyblock.util.ElementalHeartLoss.effectiveLevel(chest, elementId, raw);
    }

    // Ore Detector & Famille Invocation d'Armée (army_summon Enfant -> summon_army Upgrade)
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event){try{onRightClickItemImpl(event);}catch(Throwable t__){guard("onRightClickItem",t__);}}
    private static void onRightClickItemImpl(PlayerInteractEvent.RightClickItem event){
        try {
            handleRightClickItem(event);
        } catch (Throwable t) {
            printHandlerError("onRightClickItem", t);
        }
    }

    private static void handleRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty() || player.level().isClientSide()) return;
        if (getEnchantLvl(tool, "deep_lucky_block:ore_detector") > 0) {
            boolean hasStar = player.getMainHandItem().is(Items.NETHER_STAR) || player.getOffhandItem().is(Items.NETHER_STAR);
            int count = 0;
            BlockPos c = player.blockPosition();
            BlockPos nearest = null;
            double bestDist = Double.MAX_VALUE;
            BlockState nearestState = null;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -8, -8), c.offset(8, 8, 8))) {
                BlockState st = player.level().getBlockState(p);
                if (st.is(Tags.Blocks.ORES)) {
                    count++;
                    double d = p.distSqr(c);
                    if (d < bestDist) {
                        bestDist = d;
                        nearest = p.immutable();
                        nearestState = st;
                    }
                }
            }
            String extra = (hasStar && nearest != null && nearestState != null)
                    ? " §7— Plus proche : §b" + nearestState.getBlock().getName().getString()
                      + " §7(" + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ() + ")"
                    : "";
            player.sendSystemMessage(Component.literal("§e§l[Ore Detector] §fMinerais dans 16 blocs : §6" + count + extra));
            player.level().playSound(null, c, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.3f);
            EnchantmentParticleFX.crownHalo(player, ParticleTypes.GLOW, 6);
        }

        // T306-OVERHAUL : Famille Invocation d'Armée (army_summon Enfant : patrouille légère ; summon_army Upgrade : légion d'élite loyale !)
        int advArmy = getEnchantLvl(tool, "deep_lucky_block:summon_army");
        int basicArmy = getEnchantLvl(tool, "deep_lucky_block:army_summon");
        int armyLvl = Math.max(advArmy, basicArmy);
        if (armyLvl > 0 && !player.getCooldowns().isOnCooldown(tool.getItem())
                && (player.totalExperience >= 15 || player.isCreative())) {
            boolean upgradedArmy = advArmy > 0;
            if (!player.isCreative()) player.giveExperiencePoints(upgradedArmy ? -25 : -20);
            player.getCooldowns().addCooldown(tool.getItem(), upgradedArmy ? 360 : 440);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (player.level() instanceof ServerLevel slarm) {
                EnchantmentParticleFX.shockwaveRing(slarm, player.getX(), player.getY() + 0.1, player.getZ(), ParticleTypes.ENCHANT, 2.2, 20, 0.08);
            }
            int count = Math.min(10, upgradedArmy ? (2 + 2 * advArmy) : (1 + basicArmy));
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            boolean isCmd = getEnchantLvl(helmet, "deep_lucky_block:commander") > 0;
            boolean isIllager = getEnchantLvl(helmet, "deep_lucky_block:illager_commander") > 0;
            boolean isUndead = getEnchantLvl(helmet, "deep_lucky_block:undead_commander") > 0;
            LivingEntity currentThreat = player.getLastHurtMob() != null && player.getLastHurtMob().isAlive()
                    ? player.getLastHurtMob() : player.getLastHurtByMob();
            for (int i = 0; i < count; i++) {
                EntityType<? extends Mob> type = EntityType.WOLF;
                if (isCmd) type = (player.level().random.nextFloat() < (upgradedArmy ? 0.45f : 0.75f)) ? EntityType.WOLF : EntityType.IRON_GOLEM;
                else if (isIllager) {
                    float r = player.level().random.nextFloat();
                    type = (r < 0.65f) ? EntityType.PILLAGER : (r < 0.9f) ? EntityType.VINDICATOR : EntityType.EVOKER;
                } else if (isUndead) {
                    type = (player.level().random.nextFloat() < 0.4f) ? EntityType.HUSK : EntityType.WITHER_SKELETON;
                }
                Mob mob = type.create(player.level());
                if (mob != null) {
                    mob.setPos(player.getX() + player.level().random.nextInt(5) - 2, player.getY(), player.getZ() + player.level().random.nextInt(5) - 2);
                    mob.getPersistentData().putUUID("slb_ally_owner", player.getUUID());
                    if (mob instanceof net.minecraft.world.entity.animal.Wolf wolf) {
                        wolf.tame(player);
                    }
                    if (type == EntityType.PILLAGER) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                    else if (type == EntityType.VINDICATOR) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(upgradedArmy ? Items.DIAMOND_AXE : Items.IRON_AXE));
                    else if (type == EntityType.WITHER_SKELETON) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(upgradedArmy ? Items.IRON_SWORD : Items.STONE_SWORD));
                    if (upgradedArmy) {
                        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0, true, false));
                        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 0, true, false));
                    }
                    if (currentThreat != null && currentThreat != player && currentThreat.isAlive()) {
                        mob.setTarget(currentThreat);
                    }
                    player.level().addFreshEntity(mob);
                    EnchantmentParticleFX.burstOnEntity(mob, ParticleTypes.POOF, 6, 0.3, 0.03);
                }
            }
        }
    }

    // =========================================================================================
    // T307-PHYSICS & T307-BARRIER-TRAIL :
    //  1) Moteur de physique partagé Client + Serveur (Légèreté façon poulet, Coureur Mural
    //     façon bloc de miel, Glissade soutenue, Bondissant façon bloc de slime, Pas de Géant
    //     jusqu'en haut d'un mur, Stationnaire d'Oxygène).
    //  2) Marcheur des Nuages (Triple Saut sur coussin de nuages côté serveur + C2S).
    //  3) Marche Aérienne (foot_placement) & Foulée des Nuages (cloud_stride) en trail de
    //     Blocks.BARRIER temporaires supprimés automatiquement dès >= 4 blocs derrière le joueur.
    // =========================================================================================

    private static final class SharedMoveState {
        boolean wasOnGround = true;
        boolean wasSneak = false;
        boolean updraftUsed = false;
        int springCoilTicks = 0;
        double lastAirVy = 0.0;
        double prevY = Double.NaN;
        int sprintGraceTicks = 0;
        int slideTicksLeft = 0;
        double slideSpeed = 0.0;
        double slideDirX = 0.0;
        double slideDirZ = 0.0;
        long slideCooldownUntil = 0L;
        int serverAirJumpsUsed = 0;
        long lastCloudJumpTick = -100L;
        long lastSeenTick = 0L;
    }

    private static final Map<Long, SharedMoveState> SHARED_MOVE_STATES = new ConcurrentHashMap<>();

    private static long sideEntityKey(LivingEntity entity) {
        long sideBit = entity.level().isClientSide() ? 1L : 0L;
        return (sideBit << 32) | (entity.getId() & 0xFFFFFFFFL);
    }

    public static boolean isTouchingHorizontalWall(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        if (box.getYsize() < 0.35) return false;
        AABB probe = box.deflate(0.0, 0.14, 0.0).inflate(0.09, 0.0, 0.09);
        return !entity.level().noCollision(entity, probe);
    }

    private static void handleSharedClientServerMovement(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return;
        // Sur serveur comme sur client, les joueurs bénéficient toujours du moteur ; les mobs uniquement s'ils portent de l'armure enchantée.
        if (!(entity instanceof Player)) {
            boolean anyEnchantedArmor = false;
            for (ItemStack arm : entity.getArmorSlots()) {
                if (!arm.isEmpty() && arm.isEnchanted()) { anyEnchantedArmor = true; break; }
            }
            if (!anyEnchantedArmor) return;
        }

        long now = entity.level().getGameTime();
        long key = sideEntityKey(entity);
        SharedMoveState state = SHARED_MOVE_STATES.computeIfAbsent(key, k -> new SharedMoveState());
        state.lastSeenTick = now;
        if ((entity.tickCount & 255) == 0 && SHARED_MOVE_STATES.size() > 256) {
            SHARED_MOVE_STATES.entrySet().removeIf(e -> now - e.getValue().lastSeenTick > 600L);
        }

        boolean grounded = entity.onGround();
        boolean sneaking = entity.isShiftKeyDown();
        Vec3 dm = entity.getDeltaMovement();

        if (grounded) {
            state.serverAirJumpsUsed = 0;
            state.updraftUsed = false;
        }

        // --- A1. Semelles à Ressort (elastic_soles) : Saut-Catapulte chargé accroupi au sol (Client + Serveur) ---
        int elasticBoots = getEnchantLvl(entity.getItemBySlot(EquipmentSlot.FEET), "deep_lucky_block:elastic_soles");
        if (elasticBoots > 0 && grounded && sneaking && !entity.isSprinting()) {
            state.springCoilTicks = Math.min(20, state.springCoilTicks + 1);
            if (state.springCoilTicks == 8 && entity.level() instanceof ServerLevel slCoil) {
                EnchantmentParticleFX.shockwaveRing(slCoil, entity.getX(), entity.getY() + 0.05, entity.getZ(),
                        EnchantmentParticleFX.OVERCLOCK_CYAN, 0.55, 8, 0.02);
            }
        } else if (!grounded && state.wasOnGround && dm.y > 0.16 && elasticBoots > 0 && state.springCoilTicks >= 8) {
            Vec3 lkSpring = entity.getLookAngle();
            double springVy = 0.34 + 0.16 * Math.min(4, elasticBoots);
            double springFwd = 0.16 * Math.min(4, elasticBoots);
            dm = dm.add(lkSpring.x * springFwd, springVy, lkSpring.z * springFwd);
            entity.setDeltaMovement(dm);
            state.springCoilTicks = 0;
            if (entity.level() instanceof ServerLevel slSpring) {
                EnchantmentParticleFX.shockwaveRing(slSpring, entity.getX(), entity.getY() + 0.06, entity.getZ(),
                        EnchantmentParticleFX.OVERCLOCK_CYAN, 0.90, 12, 0.05);
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.70f, 1.35f);
            }
        } else if (!sneaking && grounded) {
            state.springCoilTicks = Math.max(0, state.springCoilTicks - 2);
        }

        // --- A2. Bourrasque Ascendante (gale) : Impulsion éolienne en plein air d'une pression sur S'accroupir (Client + Serveur) ---
        int galeBoots = getEnchantLvl(entity.getItemBySlot(EquipmentSlot.FEET), "deep_lucky_block:gale");
        if (galeBoots > 0 && !grounded && sneaking && !state.wasSneak && !state.updraftUsed
                && !entity.isInWater() && !entity.isFallFlying()
                && getArmorEnchantLvl(entity, "deep_lucky_block:oxygen_hover") == 0) {
            state.updraftUsed = true;
            Vec3 lkGale = entity.getLookAngle();
            double updraftVy = Math.max(dm.y, 0.0) + 0.44 + 0.13 * Math.min(4, galeBoots);
            double updraftFwd = 0.22 * Math.min(4, galeBoots);
            dm = new Vec3(dm.x + lkGale.x * updraftFwd, Math.min(1.35, updraftVy), dm.z + lkGale.z * updraftFwd);
            entity.setDeltaMovement(dm);
            entity.fallDistance = 0.0F;
            if (entity.level() instanceof ServerLevel slUpdraft) {
                EnchantmentParticleFX.shockwaveRing(slUpdraft, entity.getX(), entity.getY() + 0.08, entity.getZ(),
                        ParticleTypes.SMALL_GUST, 1.15, 14, 0.06);
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.75f, 1.35f);
            }
        }

        // --- A3. Marche-Pente (high_step) : Propulsion fluide en montée d'escaliers/collines (Client + Serveur) ---
        int highStepBoots = getArmorEnchantLvl(entity, "deep_lucky_block:high_step");
        if (highStepBoots > 0 && grounded && !Double.isNaN(state.prevY)) {
            double uphill = entity.getY() - state.prevY;
            if (uphill >= 0.15 && uphill <= 1.25 && dm.horizontalDistanceSqr() > 0.002 && dm.horizontalDistanceSqr() < 0.35) {
                double boost = 1.0 + Math.min(0.45, 0.12 * highStepBoots);
                dm = new Vec3(dm.x * boost, dm.y, dm.z * boost);
                entity.setDeltaMovement(dm);
            }
        }

        // --- B. Bondissant (bouncy) : Vrai rebond de Bloc de Slime à l'atterrissage (même sur petit saut !) ---
        int bouncyLvl = getArmorEnchantLvl(entity, "deep_lucky_block:bouncy");
        if (!grounded) {
            if (dm.y < state.lastAirVy) state.lastAirVy = dm.y;
        } else {
            if (!state.wasOnGround && bouncyLvl > 0 && !sneaking
                    && !entity.isInWater() && !entity.isInLava() && state.lastAirVy < -0.15) {
                double retention = 0.84 + 0.08 * Math.min(4, bouncyLvl);
                double bounceVy = Math.min(2.15, Math.max(0.46 + 0.09 * bouncyLvl, -state.lastAirVy * retention));
                dm = new Vec3(dm.x * 1.02, bounceVy, dm.z * 1.02);
                entity.setDeltaMovement(dm);
                entity.setOnGround(false);
                entity.fallDistance = 0.0F;
                grounded = false;
                state.lastAirVy = 0.0;
                if (entity.level() instanceof ServerLevel slBounce) {
                    EnchantmentParticleFX.shockwaveRing(slBounce, entity.getX(), entity.getY() + 0.05, entity.getZ(),
                            ParticleTypes.ITEM_SLIME, 0.80, 12, 0.05);
                    entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.80f, 1.08f);
                }
            } else {
                state.lastAirVy = 0.0;
            }
        }

        // --- C. Légèreté (lightness & basic_lightness) : Chute lente planée comme un Poulet ! ---
        if (!grounded && !sneaking && !entity.isInWater() && !entity.isInLava()
                && !entity.onClimbable() && !entity.isFallFlying()
                && !(entity instanceof Player plFly && plFly.getAbilities().flying)) {
            int lightLvl = getArmorEnchantLvl(entity, "deep_lucky_block:lightness");
            int basicLightLvl = getArmorEnchantLvl(entity, "deep_lucky_block:basic_lightness");
            dm = entity.getDeltaMovement();
            if (lightLvl > 0 && dm.y < 0.0) {
                // Physique authentique Chicken#aiStep : amortissement doux plafonné à ~ -0.092 bloc/tick + léger plané horizontal
                double dampedY = Math.max(dm.y * 0.56, -0.092);
                double hx = dm.x;
                double hz = dm.z;
                double hSpeedSq = hx * hx + hz * hz;
                if (hSpeedSq > 1.0e-4 && hSpeedSq < 0.14) {
                    hx *= 1.02;
                    hz *= 1.02;
                }
                entity.setDeltaMovement(hx, dampedY, hz);
                entity.fallDistance = 0.0F;
                dm = entity.getDeltaMovement();
            } else if (basicLightLvl > 0 && dm.y < 0.0) {
                double dampedY = Math.max(dm.y * 0.72, -0.175);
                entity.setDeltaMovement(dm.x, dampedY, dm.z);
                entity.fallDistance *= 0.55F;
                dm = entity.getDeltaMovement();
            }
        }

        // --- D. Coureur Mural (wall_runner) : Glisse sur tous les murs exactement comme des Blocs de Miel + course latérale ! ---
        int wallRunnerLvl = getArmorEnchantLvl(entity, "deep_lucky_block:wall_runner");
        if (wallRunnerLvl > 0 && !grounded && !sneaking && !entity.isInWater()
                && !entity.isFallFlying() && isTouchingHorizontalWall(entity)) {
            dm = entity.getDeltaMovement();
            Vec3 look = entity.getLookAngle();
            // HoneyBlock#doSlideMovement : vitesse verticale retenue à -0.05 (niv. I) ou -0.025 (niv. II+),
            // et légère grimpe murale au niv. II+ si le joueur sprinte en regardant vers le haut !
            double minSlideY = wallRunnerLvl >= 2 ? -0.025 : -0.05;
            double newY = Math.max(dm.y, minSlideY);
            if (wallRunnerLvl >= 2 && entity.isSprinting() && look.y > 0.24) {
                newY = Math.max(newY, Math.min(0.18, 0.055 * wallRunnerLvl));
            }
            Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
            double nx = dm.x;
            double nz = dm.z;
            if (flatLook.lengthSqr() > 1.0e-4 && (entity.isSprinting() || dm.horizontalDistanceSqr() > 4.0e-4)) {
                flatLook = flatLook.normalize();
                double maxWallRun = 0.28 + 0.07 * Math.min(4, wallRunnerLvl);
                nx += flatLook.x * 0.032 * wallRunnerLvl;
                nz += flatLook.z * 0.032 * wallRunnerLvl;
                double hLen = Math.sqrt(nx * nx + nz * nz);
                if (hLen > maxWallRun) {
                    nx = (nx / hLen) * maxWallRun;
                    nz = (nz / hLen) * maxWallRun;
                }
            }
            entity.setDeltaMovement(nx, newY, nz);
            entity.fallDistance = 0.0F;
            dm = entity.getDeltaMovement();
        }

        // --- E. Glissade (slide) : Forte projection avant soutenue sur plusieurs ticks ! ---
        int slideLvl = getArmorEnchantLvl(entity, "deep_lucky_block:slide");
        if (slideLvl > 0) {
            if (entity.isSprinting()) {
                state.sprintGraceTicks = 8;
            } else if (state.sprintGraceTicks > 0) {
                state.sprintGraceTicks--;
            }
            if (sneaking && !state.wasSneak && grounded && state.sprintGraceTicks > 0 && now >= state.slideCooldownUntil) {
                Vec3 lk = entity.getLookAngle();
                Vec3 flat = new Vec3(lk.x, 0.0, lk.z);
                if (flat.lengthSqr() > 1.0e-4) {
                    flat = flat.normalize();
                    boolean cryoCombo = EnchantmentComboRegistry.hasCombo(entity, EnchantmentComboRegistry.C04_PATINEUR_AZOTE);
                    double mult = cryoCombo ? 1.32 : 1.0;
                    state.slideSpeed = Math.min(2.90, (1.25 + 0.34 * slideLvl) * mult);
                    state.slideDirX = flat.x;
                    state.slideDirZ = flat.z;
                    state.slideTicksLeft = (10 + 2 * Math.min(5, slideLvl)) + (cryoCombo ? 5 : 0);
                    state.slideCooldownUntil = now + 44L;
                    state.sprintGraceTicks = 0;
                }
            }
            if (state.slideTicksLeft > 0) {
                if (sneaking && !entity.isInWater()) {
                    state.slideTicksLeft--;
                    dm = entity.getDeltaMovement();
                    entity.setDeltaMovement(state.slideDirX * state.slideSpeed, dm.y, state.slideDirZ * state.slideSpeed);
                    state.slideSpeed *= 0.90;
                } else {
                    state.slideTicksLeft = 0;
                }
            }
        } else {
            state.slideTicksLeft = 0;
        }

        // --- F. Stationnaire d'Oxygène (oxygen_hover) synchronisé Client + Serveur ---
        int oxyHov = getArmorEnchantLvl(entity, "deep_lucky_block:oxygen_hover");
        if (oxyHov > 0 && !grounded && sneaking) {
            dm = entity.getDeltaMovement();
            entity.setDeltaMovement(dm.x, 0.0, dm.z);
            entity.fallDistance = 0.0F;
        }

        // --- G. Pas de Géant (giant_step) : Au niveau max (niv. >= 5), marche directement du sol jusqu'en haut d'un grand mur (> 10 jusqu'à 18 blocs) ! ---
        int giantStepLvl = getArmorEnchantLvl(entity, "deep_lucky_block:giant_step");
        int bigStepLvl = getArmorEnchantLvl(entity, "deep_lucky_block:big_step");
        if (giantStepLvl >= 5 && grounded && !sneaking && !entity.isInWater()) {
            Vec3 lk = entity.getLookAngle();
            Vec3 flat = new Vec3(lk.x, 0.0, lk.z);
            if (flat.lengthSqr() > 1.0e-4 && (entity.isSprinting() || isTouchingHorizontalWall(entity))) {
                flat = flat.normalize();
                double stepAheadX = flat.x * 0.55;
                double stepAheadZ = flat.z * 0.55;
                AABB curBox = entity.getBoundingBox();
                // Si bloqué par un haut mur (> 10 blocs, au-delà du plafond vanilla RangedAttribute STEP_HEIGHT = 10.0) :
                if (!entity.level().noCollision(entity, curBox.move(stepAheadX, 0.0, stepAheadZ))
                        && !entity.level().noCollision(entity, curBox.move(stepAheadX, 9.5, stepAheadZ))) {
                    int maxWallHeight = Math.min(24, 8 + 2 * giantStepLvl); // 18 blocs au niveau V !
                    for (int h = 10; h <= maxWallHeight; h++) {
                        AABB crestBox = curBox.move(stepAheadX, h + 0.05, stepAheadZ);
                        BlockPos belowCrest = BlockPos.containing(entity.getX() + stepAheadX, entity.getY() + h - 0.25, entity.getZ() + stepAheadZ);
                        if (!entity.level().getBlockState(belowCrest).isAir()
                                && entity.level().noCollision(entity, crestBox)
                                && entity.level().noCollision(entity, curBox.move(0.0, h + 0.05, 0.0))) {
                            entity.setPos(entity.getX() + stepAheadX, entity.getY() + h + 0.05, entity.getZ() + stepAheadZ);
                            entity.fallDistance = 0.0F;
                            break;
                        }
                    }
                }
            }
        }
        if ((giantStepLvl > 0 || bigStepLvl > 0) && !Double.isNaN(state.prevY)) {
            double climbed = entity.getY() - state.prevY;
            if (climbed >= 1.8 && entity.onGround()) {
                entity.fallDistance = 0.0F;
                if (entity.level() instanceof ServerLevel slStep) {
                    EnchantmentParticleFX.cloudCushion(slStep, entity.getX(), entity.getY() + 0.04, entity.getZ(),
                            climbed >= 5.0 ? 1.05 : 0.72, climbed >= 8.0);
                    EnchantmentParticleFX.shockwaveRing(slStep, entity.getX(), entity.getY() + 0.06, entity.getZ(),
                            EnchantmentParticleFX.EARTH_OCHRE, climbed >= 5.0 ? 1.0 : 0.65, 10, 0.02);
                }
            }
        }

        state.prevY = entity.getY();
        state.wasOnGround = entity.onGround();
        state.wasSneak = sneaking;
    }

    /**
     * T330-CLOUD-WALKER-X2-X5 : Multiplicateur de puissance et de portée du Triple Saut selon le
     * niveau de Marcheur des Nuages (max level 3) :
     *  - Niveau I   : x2.0 de la base initiale
     *  - Niveau II  : x3.5 de la base initiale
     *  - Niveau III : x5.0 de la base initiale (niveau max naturel)
     */
    public static double cloudWalkerPowerMultiplier(int cwLvl) {
        if (cwLvl <= 0) return 1.0;
        if (cwLvl == 1) return 2.0;
        if (cwLvl == 2) return 3.5;
        if (cwLvl == 3) return 5.0;
        return Math.min(8.0, 5.0 + 1.0 * (cwLvl - 3));
    }

    /**
     * T320 + T330-CLOUD-WALKER-X2-X5 : Physique déterministe Client/Serveur du Triple Saut
     * « Marcheur des Nuages » (2 sauts aériens, ou 3 avec Combo #2 Ascension Céleste), calibrée
     * sur la gravité/traînée réelle de Minecraft pour offrir x2.0 (Niv. I), x3.5 (Niv. II) et
     * x5.0 (Niv. III) en hauteur ET en distance de saut, avec redirection aérienne fluide et
     * zéro rollback serveur (vecteur identique côté Client et Serveur, sans hurtMarked=true).
     */
    public static void applyCloudJumpPhysics(
            Player player,
            int cwLvl,
            int jumpIndex,
            int maxAirJumps,
            boolean hasQuadCombo,
            boolean movingHoriz,
            boolean sprinting,
            float moveYawDeg) {
        player.fallDistance = 0.0F;

        double powerMult = cloudWalkerPowerMultiplier(Math.max(1, cwLvl));
        boolean isCulmination = jumpIndex >= maxAirJumps;

        // Impulsion verticale calibrée sur g=0.08 / drag=0.98 -> hauteur effective x2.0 / x3.5 / x5.0+
        double baseUp = isCulmination ? 0.54 : 0.50;
        double upwardsMotion = baseUp * Math.pow(powerMult, 0.58);
        MobEffectInstance jumpEffect = player.getEffect(MobEffects.JUMP);
        if (jumpEffect != null) {
            upwardsMotion += 0.12 * (jumpEffect.getAmplifier() + 1);
        }
        if (sprinting) {
            upwardsMotion *= (hasQuadCombo && jumpIndex >= 3) ? 1.32 : 1.25;
        }

        // Impulsion horizontale calibrée sur drag aérien 0.91 -> distance effective x2.0 / x3.5 / x5.0+
        Vec3 motion = player.getDeltaMovement();
        double newVx;
        double newVz;
        if (sprinting || movingHoriz) {
            double totalHorizSpeed;
            if (sprinting) {
                double comboExtra = (hasQuadCombo && jumpIndex >= 3) ? 0.18 : 0.0;
                totalHorizSpeed = 0.28 + 0.40 * powerMult + 0.08 * (powerMult - 2.0) + comboExtra;
            } else {
                totalHorizSpeed = 0.15 + 0.20 * powerMult;
            }
            float rad = moveYawDeg * ((float) Math.PI / 180.0F);
            double dirX = -Mth.sin(rad);
            double dirZ = Mth.cos(rad);
            // Si le joueur avait déjà plus d'élan dans cette même direction (ex: Speed X), on le préserve !
            double existingAlongDir = motion.x * dirX + motion.z * dirZ;
            double finalSpeed = Math.max(totalHorizSpeed, existingAlongDir + 0.25 * powerMult);
            newVx = dirX * finalSpeed;
            newVz = dirZ * finalSpeed;
        } else {
            newVx = motion.x * 0.25;
            newVz = motion.z * 0.25;
        }

        player.setDeltaMovement(newVx, upwardsMotion, newVz);
        player.hasImpulse = true;
        player.awardStat(Stats.JUMP);
        player.causeFoodExhaustion(sprinting ? 0.20F : 0.05F);
    }

    /**
     * T307-CLOUD-WALKER + T308-COMBOS #1 & #2 + T320 + T330-CLOUD-WALKER-X2-X5 :
     *  - requestedJumpIdx == -1 : Combo #1 « Acrobate Miel-Slime » (Wall-Jump élastique depuis un mur !).
     *  - requestedJumpIdx >= 1  : Marcheur des Nuages I..III (x2 -> x5 puissance & distance).
     */
    public static void performServerCloudJump(
            ServerPlayer sp,
            int requestedJumpIdx,
            boolean movingHoriz,
            boolean sprinting,
            float moveYawDeg) {
        if (sp == null || !sp.isAlive() || sp.isSpectator()) return;
        if (sp.isInWater() || sp.isInLava() || sp.isFallFlying() || sp.getAbilities().flying) return;

        if (!Float.isFinite(moveYawDeg)) {
            moveYawDeg = sp.getYRot();
        }

        SharedMoveState state = SHARED_MOVE_STATES.computeIfAbsent(sideEntityKey(sp), k -> new SharedMoveState());
        long now = sp.level().getGameTime();

        // T308-COMBO #1 : Acrobate Miel-Slime (LEGS: wall_runner + FEET: bouncy) -> Wall-Jump élastique !
        if (requestedJumpIdx == -1) {
            if (!EnchantmentComboRegistry.hasCombo(sp, EnchantmentComboRegistry.C01_ACROBAT_ALCHIMIQUE)) return;
            if (!isTouchingHorizontalWall(sp)) return;
            float dir = sp.getYRot() * ((float) Math.PI / 180.0F);
            sp.setDeltaMovement(-Mth.sin(dir) * 0.56, 0.62, Mth.cos(dir) * 0.56);
            sp.hasImpulse = true;
            sp.fallDistance = 0.0F;
            state.lastAirVy = 0.0;
            if (sp.level() instanceof ServerLevel slw) {
                EnchantmentParticleFX.shockwaveRing(slw, sp.getX(), sp.getY() + 0.25, sp.getZ(),
                        ParticleTypes.ITEM_SLIME, 0.85, 10, 0.05);
                slw.sendParticles(EnchantmentParticleFX.HONEY_AMBER, sp.getX(), sp.getY() + 0.35, sp.getZ(),
                        8, 0.25, 0.20, 0.25, 0.02);
                slw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.85f, 1.25f);
            }
            return;
        }

        int cwLvl = getArmorEnchantLvl(sp, "deep_lucky_block:cloud_walker");
        if (cwLvl <= 0) return;

        boolean hasQuintCombo = EnchantmentComboRegistry.hasCombo(sp, EnchantmentComboRegistry.IDX_TRANSCENDANCE_CHANCE_INFINIE);
        boolean hasQuadCombo = hasQuintCombo || EnchantmentComboRegistry.hasCombo(sp, EnchantmentComboRegistry.C02_ASCENSION_CELESTE);
        int maxAirJumps = hasQuintCombo ? 4 : (hasQuadCombo ? 3 : 2);
        // T320-FIX : Si le client redémarre une séquence aérienne (jump #1) après un atterrissage rapide
        // type bunny-hop que le tick serveur n'aurait pas vu comme onGround, on réarme proprement !
        if (requestedJumpIdx == 1 && (sp.onGround() || (now - state.lastCloudJumpTick) >= 5L)) {
            state.serverAirJumpsUsed = 0;
        }
        if (state.serverAirJumpsUsed >= maxAirJumps) return;
        state.serverAirJumpsUsed++;
        state.lastCloudJumpTick = now;

        int effectiveIdx = Math.max(state.serverAirJumpsUsed, Math.min(requestedJumpIdx, maxAirJumps));
        boolean isFinalJump = effectiveIdx >= maxAirJumps;

        applyCloudJumpPhysics(sp, cwLvl, effectiveIdx, maxAirJumps, hasQuadCombo, movingHoriz, sprinting, moveYawDeg);
        state.lastAirVy = 0.0;

        if (sp.level() instanceof ServerLevel sl) {
            int poofCount = 18 + Math.min(3, cwLvl) * 6;
            for (int i = 0; i < poofCount; ++i) {
                double motionX = sp.getRandom().nextGaussian() * 0.03 * cwLvl;
                double motionY = sp.getRandom().nextGaussian() * 0.02 + 0.20;
                double motionZ = sp.getRandom().nextGaussian() * 0.03 * cwLvl;
                sl.sendParticles(ParticleTypes.POOF, sp.getX(), sp.getY(), sp.getZ(),
                        1, motionX, motionY, motionZ, 0.15);
            }
            double cushionRadius = (isFinalJump ? 1.15 : 0.85) + 0.22 * (Math.min(3, cwLvl) - 1);
            EnchantmentParticleFX.cloudCushion(sl, sp.getX(), sp.getY() - 0.05, sp.getZ(),
                    cushionRadius, isFinalJump || cwLvl >= 3);
            if ((hasQuadCombo && effectiveIdx >= 3) || cwLvl >= 3) {
                EnchantmentParticleFX.shockwaveRing(sl, sp.getX(), sp.getY() - 0.02, sp.getZ(),
                        cwLvl >= 3 ? ParticleTypes.CLOUD : EnchantmentParticleFX.GOLD_DIVINE,
                        1.15 + 0.20 * Math.min(3, cwLvl), 16, 0.04);
            }
            sl.playSound(null, sp, SoundEvents.WOOL_FALL, SoundSource.PLAYERS,
                    1.0F, 0.9F + sp.getRandom().nextFloat() * 0.2F + (isFinalJump ? 0.15F : 0.0F));
        }
    }

    // -----------------------------------------------------------------------------------------
    // T307-BARRIER-TRAIL + T319/T320-AIRWALK-LOCKSTEP :
    // Trail temporaire de Blocks.BARRIER synchronisé en lockstep entier Client (ClientTickEvent.Pre)
    // + Serveur : solide dès le tick 0, zéro tremblement, 100% compatible Speed & Jump Boost,
    // zéro bloc posé plus haut devant les tibias pendant un saut, et zéro mini-rollback !
    // Chaque barrière est supprimée automatiquement dès que le joueur est à >= 4.0 blocs !
    // -----------------------------------------------------------------------------------------
    private record BarrierNode(ResourceKey<Level> dimension, BlockPos pos, long placedTick) {}

    private static final class AirWalkState {
        ResourceKey<Level> dimension = null;
        double lastX = Double.NaN;
        double lastY = Double.NaN;
        double lastZ = Double.NaN;
        int anchorFloorY = Integer.MIN_VALUE;
        boolean clientControlled = false;
        long lastTick = -1L;
    }

    private static final Map<UUID, Map<BlockPos, BarrierNode>> PLAYER_BARRIER_TRAILS = new ConcurrentHashMap<>();
    private static final Map<UUID, AirWalkState> PLAYER_AIRWALK_STATES = new ConcurrentHashMap<>();

    public static void handleClientAirWalkAnchor(ServerPlayer sp, int clientAnchorY) {
        if (sp == null || !sp.isAlive() || !(sp.level() instanceof ServerLevel sl)) return;
        int footPl = getArmorEnchantLvl(sp, "deep_lucky_block:foot_placement");
        int cloudStride = getArmorEnchantLvl(sp, "deep_lucky_block:cloud_stride");
        if (footPl <= 0 && cloudStride <= 0) {
            resetAirWalkAnchor(sp);
            clearPlayerBarriersUnderneath(sp);
            return;
        }
        if (clientAnchorY == Integer.MIN_VALUE) {
            resetAirWalkAnchor(sp);
            clearPlayerBarriersUnderneath(sp);
            return;
        }
        int minBuild = sl.getMinBuildHeight();
        int maxBuild = sl.getMaxBuildHeight() - 1;
        if (footPl <= 0 && cloudStride > 0 && clientAnchorY < 159) {
            return;
        }
        // Validation anti-triche : le plancher doit être sous les pieds du joueur et à distance raisonnable
        int maxAllowedUnderFeet = Mth.floor(sp.getY() - 1.0 + 0.15);
        int clampedY = Mth.clamp(clientAnchorY, Math.max(minBuild, maxAllowedUnderFeet - 96), Math.min(maxBuild, maxAllowedUnderFeet));
        double[] vel = new double[3];
        AirWalkState st = samplePlayerMotion(sp, sl, vel);
        st.anchorFloorY = clampedY;
        st.clientControlled = true;
        sp.fallDistance = 0.0F;
        purgeServerBarriersExceptFloor(sp, sl, clampedY);
        placeBarrierRunwayAtY(sp, sl, clampedY, vel[0], vel[2], true);
    }

    private static void purgeServerBarriersExceptFloor(Player player, ServerLevel sl, int keepFloorY) {
        Map<BlockPos, BarrierNode> trail = PLAYER_BARRIER_TRAILS.get(player.getUUID());
        if (trail == null || trail.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, BarrierNode>> it = trail.entrySet().iterator();
        while (it.hasNext()) {
            BarrierNode node = it.next().getValue();
            if (node.pos().getY() != keepFloorY) {
                ServerLevel targetLvl = sl.getServer().getLevel(node.dimension());
                if (targetLvl != null && targetLvl.isLoaded(node.pos())
                        && targetLvl.getBlockState(node.pos()).is(Blocks.BARRIER)) {
                    targetLvl.setBlockAndUpdate(node.pos(), Blocks.AIR.defaultBlockState());
                }
                it.remove();
            }
        }
    }

    private static void resetAirWalkAnchor(LivingEntity entity) {
        if (!(entity instanceof Player player)) return;
        AirWalkState st = PLAYER_AIRWALK_STATES.get(player.getUUID());
        if (st != null) {
            st.anchorFloorY = Integer.MIN_VALUE;
            st.clientControlled = false;
        }
    }

    private static AirWalkState samplePlayerMotion(Player player, ServerLevel sl, double[] outVel) {
        AirWalkState st = PLAYER_AIRWALK_STATES.computeIfAbsent(player.getUUID(), u -> new AirWalkState());
        long now = sl.getGameTime();
        boolean consecutive = st.dimension != null && st.dimension.equals(sl.dimension())
                && !Double.isNaN(st.lastY) && (now - st.lastTick) <= 5L;
        if (st.lastTick == now && consecutive) {
            outVel[0] = 0.0;
            outVel[1] = 0.0;
            outVel[2] = 0.0;
            return st;
        }
        outVel[0] = consecutive ? Mth.clamp(player.getX() - st.lastX, -3.5, 3.5) : 0.0;
        outVel[1] = consecutive ? Mth.clamp(player.getY() - st.lastY, -3.5, 3.5) : 0.0;
        outVel[2] = consecutive ? Mth.clamp(player.getZ() - st.lastZ, -3.5, 3.5) : 0.0;
        st.dimension = sl.dimension();
        st.lastX = player.getX();
        st.lastY = player.getY();
        st.lastZ = player.getZ();
        st.lastTick = now;
        return st;
    }

    private static void maintainAirWalkRunway(LivingEntity entity, int minAllowedFloorY) {
        if (!(entity instanceof Player player) || !(entity.level() instanceof ServerLevel sl)) return;
        double[] vel = new double[3];
        AirWalkState st = samplePlayerMotion(player, sl, vel);

        if (player.isInWater() || player.isInLava() || player.onClimbable()
                || player.isFallFlying() || player.isPassenger() || player.getAbilities().flying) {
            st.anchorFloorY = Integer.MIN_VALUE;
            st.clientControlled = false;
            return;
        }

        double vx = vel[0];
        double vy = vel[1];
        double vz = vel[2];
        int exactFloorUnderFeet = Mth.floor(player.getY() - 1.0 + 1.0E-4);

        if (st.anchorFloorY == Integer.MIN_VALUE) {
            double catchY = player.getY() + (vy < -0.05 ? vy * 2.0 : 0.0);
            st.anchorFloorY = Mth.floor(catchY - 1.0 + 1.0E-4);
            player.fallDistance = 0.0f;
        } else if (!st.clientControlled) {
            // Fallback serveur si aucun paquet client n'a encore fixé l'ancre :
            // JAMAIS de hausse automatique pendant un saut (même avec Jump Boost !) pour ne jamais
            // poser un bloc devant les jambes ; hausse uniquement sur vrai terrain solide plus haut.
            if (player.onGround() && exactFloorUnderFeet > st.anchorFloorY) {
                BlockPos underPos = BlockPos.containing(player.getX(), exactFloorUnderFeet, player.getZ());
                BlockState underState = sl.getBlockState(underPos);
                if (!underState.isAir() && !underState.is(Blocks.BARRIER)) {
                    st.anchorFloorY = exactFloorUnderFeet;
                }
            }
            if (player.getY() < st.anchorFloorY + 0.96) {
                double catchY = player.getY() + (vy < -0.05 ? vy * 2.0 : 0.0);
                st.anchorFloorY = Mth.floor(catchY - 1.0 + 1.0E-4);
            }
        } else if (player.getY() < st.anchorFloorY + 0.92) {
            // Garde-fou serveur : si le joueur est descendu sous l'ancre sans que le paquet ait encore atterri
            st.anchorFloorY = exactFloorUnderFeet;
        }

        int targetFloorY = st.anchorFloorY;
        if (minAllowedFloorY != Integer.MIN_VALUE && targetFloorY < minAllowedFloorY) {
            targetFloorY = minAllowedFloorY;
            if (player.getY() < targetFloorY + 0.96) {
                return;
            }
            st.anchorFloorY = targetFloorY;
        }
        if (targetFloorY < sl.getMinBuildHeight() || targetFloorY >= sl.getMaxBuildHeight()) return;

        purgeServerBarriersExceptFloor(player, sl, targetFloorY);
        boolean placedAny = placeBarrierRunwayAtY(player, sl, targetFloorY, vx, vz, true);
        if (placedAny && player.getY() <= targetFloorY + 6.0) {
            player.fallDistance = 0.0f;
        }
    }

    private static void placeAerialCushionUnderFeet(LivingEntity entity) {
        if (!(entity instanceof Player player) || !(entity.level() instanceof ServerLevel sl)) return;
        double[] vel = new double[3];
        samplePlayerMotion(player, sl, vel);
        double catchY = player.getY() + (vel[1] < -0.05 ? vel[1] * 2.2 : 0.0);
        int cushionY = Mth.floor(catchY - 1.0 + 1.0E-4);
        if (cushionY < sl.getMinBuildHeight() || cushionY >= sl.getMaxBuildHeight()) return;
        placeBarrierRunwayAtY(player, sl, cushionY, vel[0], vel[2], true);
    }

    private static boolean placeBarrierRunwayAtY(Player player, ServerLevel sl, int footY,
                                                 double vx, double vz, boolean withCloudTrail) {
        if (footY < sl.getMinBuildHeight() || footY >= sl.getMaxBuildHeight()) return false;
        // Garantie absolue anti-chevauchement : on ne pose jamais un bloc dont le sommet (footY + 1) dépasse les pieds
        if (footY + 1.0 > player.getY() + 0.04) return false;

        Map<BlockPos, BarrierNode> trail = PLAYER_BARRIER_TRAILS.computeIfAbsent(
                player.getUUID(), u -> new ConcurrentHashMap<>());
        long now = sl.getGameTime();
        boolean anyBarrierUnder = false;

        // T333-DIRECT-UNDER-FEET : Pose strictement sous la hitbox des pieds (+ le seul pas du tick courant),
        // jamais plusieurs blocs en avance (ne bloque plus ni la descente, ni la montée en sautant).
        double px = player.getX();
        double pz = player.getZ();
        double stepX = Mth.clamp(vx, -0.95, 0.95);
        double stepZ = Mth.clamp(vz, -0.95, 0.95);
        int minX = Mth.floor(Math.min(px, px + stepX) - 0.42);
        int maxX = Mth.floor(Math.max(px, px + stepX) + 0.42);
        int minZ = Mth.floor(Math.min(pz, pz + stepZ) - 0.42);
        int maxZ = Mth.floor(Math.max(pz, pz + stepZ) + 0.42);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (setTrackedBarrier(sl, trail, x, footY, z, px, player.getY(), pz, now)) {
                    anyBarrierUnder = true;
                }
            }
        }

        if (withCloudTrail && anyBarrierUnder && (player.tickCount % 4 == 0)) {
            sl.sendParticles(ParticleTypes.CLOUD,
                    px, footY + 1.03, pz,
                    2, 0.22, 0.02, 0.22, 0.006);
        }
        return anyBarrierUnder;
    }

    private static boolean setTrackedBarrier(ServerLevel sl, Map<BlockPos, BarrierNode> trail,
                                             int x, int y, int z, double px, double py, double pz, long now) {
        double dx = px - (x + 0.5);
        double dz = pz - (z + 0.5);
        if ((dx * dx + dz * dz) >= 4.0) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState cur = sl.getBlockState(pos);
        if (cur.isAir()) {
            sl.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
            trail.put(pos, new BarrierNode(sl.dimension(), pos, now));
            return true;
        } else if (cur.is(Blocks.BARRIER) && trail.containsKey(pos)) {
            trail.put(pos, new BarrierNode(sl.dimension(), pos, now));
            return true;
        }
        return false;
    }

    private static void clearPlayerBarriersUnderneath(LivingEntity entity) {
        if (!(entity instanceof Player player) || !(entity.level() instanceof ServerLevel sl)) return;
        Map<BlockPos, BarrierNode> trail = PLAYER_BARRIER_TRAILS.get(player.getUUID());
        if (trail == null || trail.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, BarrierNode>> it = trail.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, BarrierNode> entry = it.next();
            BarrierNode node = entry.getValue();
            ServerLevel targetLvl = sl.getServer().getLevel(node.dimension());
            if (targetLvl != null && targetLvl.isLoaded(node.pos()) && targetLvl.getBlockState(node.pos()).is(Blocks.BARRIER)) {
                targetLvl.setBlockAndUpdate(node.pos(), Blocks.AIR.defaultBlockState());
            }
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerTickBarrierTrail(ServerTickEvent.Post event) {
        try {
            if (PLAYER_BARRIER_TRAILS.isEmpty()) return;
            net.minecraft.server.MinecraftServer server = event.getServer();
            Iterator<Map.Entry<UUID, Map<BlockPos, BarrierNode>>> playerIt = PLAYER_BARRIER_TRAILS.entrySet().iterator();
            while (playerIt.hasNext()) {
                Map.Entry<UUID, Map<BlockPos, BarrierNode>> pEntry = playerIt.next();
                UUID uuid = pEntry.getKey();
                Map<BlockPos, BarrierNode> trail = pEntry.getValue();
                if (trail == null || trail.isEmpty()) {
                    playerIt.remove();
                    continue;
                }
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                boolean activeAirBoots = sp != null && sp.isAlive() && !sp.hasDisconnected()
                        && (getArmorEnchantLvl(sp, "deep_lucky_block:foot_placement") > 0
                            || getArmorEnchantLvl(sp, "deep_lucky_block:cloud_stride") > 0
                            || getArmorEnchantLvl(sp, "deep_lucky_block:range_foot_placement") > 0);

                AirWalkState awState = PLAYER_AIRWALK_STATES.get(uuid);
                int activeAnchorY = (awState != null) ? awState.anchorFloorY : Integer.MIN_VALUE;

                Iterator<Map.Entry<BlockPos, BarrierNode>> nodeIt = trail.entrySet().iterator();
                while (nodeIt.hasNext()) {
                    BarrierNode node = nodeIt.next().getValue();
                    ServerLevel nodeLvl = server.getLevel(node.dimension());
                    boolean mustRemove = !activeAirBoots
                            || !sp.level().dimension().equals(node.dimension())
                            || (activeAnchorY != Integer.MIN_VALUE && node.pos().getY() != activeAnchorY);
                    if (!mustRemove && (nodeLvl == null || (nodeLvl.getGameTime() - node.placedTick()) >= 2L)) {
                        double dx = sp.getX() - (node.pos().getX() + 0.5);
                        double dz = sp.getZ() - (node.pos().getZ() + 0.5);
                        double horizDistSq = dx * dx + dz * dz;
                        if (horizDistSq >= 3.24) {
                            mustRemove = true;
                        }
                    }
                    if (mustRemove) {
                        if (nodeLvl != null && nodeLvl.isLoaded(node.pos())
                                && nodeLvl.getBlockState(node.pos()).is(Blocks.BARRIER)) {
                            nodeLvl.setBlockAndUpdate(node.pos(), Blocks.AIR.defaultBlockState());
                            nodeLvl.sendParticles(ParticleTypes.CLOUD,
                                    node.pos().getX() + 0.5, node.pos().getY() + 0.85, node.pos().getZ() + 0.5,
                                    2, 0.15, 0.05, 0.15, 0.005);
                        }
                        nodeIt.remove();
                    }
                }
                if (trail.isEmpty()) {
                    playerIt.remove();
                }
            }
        } catch (Throwable t) {
            guard("onServerTickBarrierTrail", t);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogoutBarrierCleanup(PlayerEvent.PlayerLoggedOutEvent event) {
        try {
            clearPlayerBarriersUnderneath(event.getEntity());
            PLAYER_BARRIER_TRAILS.remove(event.getEntity().getUUID());
            PLAYER_AIRWALK_STATES.remove(event.getEntity().getUUID());
        } catch (Throwable t) {
            guard("onPlayerLogoutBarrierCleanup", t);
        }
    }

    @SubscribeEvent
    public static void onServerStoppingBarrierCleanup(ServerStoppingEvent event) {
        try {
            net.minecraft.server.MinecraftServer server = event.getServer();
            for (Map<BlockPos, BarrierNode> trail : PLAYER_BARRIER_TRAILS.values()) {
                for (BarrierNode node : trail.values()) {
                    ServerLevel lvl = server.getLevel(node.dimension());
                    if (lvl != null && lvl.isLoaded(node.pos()) && lvl.getBlockState(node.pos()).is(Blocks.BARRIER)) {
                        lvl.setBlockAndUpdate(node.pos(), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            PLAYER_BARRIER_TRAILS.clear();
            PLAYER_AIRWALK_STATES.clear();
        } catch (Throwable t) {
            guard("onServerStoppingBarrierCleanup", t);
        }
    }

    // V37 - un effet en erreur ne doit jamais bloquer les suivants
    private static final java.util.Set<String> REPORTED__ = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static void guard(String where, Throwable t) {
        if (REPORTED__.add(where + ':' + t.getClass().getName())) {
            com.mojang.logging.LogUtils.getLogger().error(
                "[deep_lucky_block] Erreur dans UniversalEnchantmentHandler#{} - effet ignore.", where, t);
        }
    }

}
