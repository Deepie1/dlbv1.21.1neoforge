package deepluckyblock.enchantments;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
            System.err.println("[DeepLuckyBlock] Erreur interceptee dans " + where + " (crash evite) : " + t);
            t.printStackTrace(System.err);
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
        // GIANT / TITAN SLAYER : dégâts proportionnels à la TAILLE de la cible.
        // Cible plus grande que le joueur -> +% de dégâts ; plus petite -> -% de
        // dégâts. Toujours proportionnel (volume de hitbox vs volume du joueur).
        int titanLvl = getEnchantLvl(weapon, "deep_lucky_block:titan_slayer");
        if (titanLvl > 0) event.setAmount(event.getAmount() * sizeDamageMultiplier(player, event.getEntity(), 0.75f * titanLvl));
        int giantLvl = getEnchantLvl(weapon, "deep_lucky_block:giant_slayer");
        if (giantLvl > 0) event.setAmount(event.getAmount() * sizeDamageMultiplier(player, event.getEntity(), 0.40f * giantLvl));
        // FIX DOUBLE-COMPTAGE : cursed_blade (x10) est désormais un vrai
        // AttributeModifier (ADD_MULTIPLIED_TOTAL) dans RealAttributeModifierHandler,
        // déjà inclus dans event.getAmount() de base -- le multiplier ENCORE
        // par 10 ici produisait un x100 réel en combat.
        // CAVALIER : +50 % de dégâts par niveau UNIQUEMENT lorsque le joueur
        // est sur une monture vivante (cheval, cochon, strider ou monture moddée).
        // Les bateaux et wagonnets ne comptent pas comme des montures.
        int cavalierLvl = getEnchantLvl(weapon, "deep_lucky_block:cavalier");
        if (cavalierLvl > 0 && player.getVehicle() instanceof LivingEntity) {
            event.setAmount(event.getAmount() * (1.0f + 0.50f * cavalierLvl));
        }
        // FIX DOUBLE-COMPTAGE : peerless (flat +4/niv ET mult +50%/niv) est
        // désormais un vrai AttributeModifier dans RealAttributeModifierHandler,
        // déjà inclus dans event.getAmount() de base.
        int battleHardLvl = getEnchantLvl(weapon, "deep_lucky_block:battle_hardened");
        if (battleHardLvl > 0 && weapon.isDamageableItem()) {
            float lostPct = (float) weapon.getDamageValue() / weapon.getMaxDamage();
            event.setAmount(event.getAmount() * (1f + Math.min(1.0f, lostPct * battleHardLvl)));
        }
        // BLOODTHIRST / BLOODTHIRSTY / BLOOD_EDGE : vol de vie. Le joueur récupère
        // un % des dégâts infligés à la cible.
        int bloodThirstLvl = getEnchantLvl(weapon, "deep_lucky_block:bloodthirst");
        if (bloodThirstLvl > 0) player.heal(event.getAmount() * (0.10f * bloodThirstLvl));
        int bloodThirstyLvl = getEnchantLvl(weapon, "deep_lucky_block:bloodthirsty");
        if (bloodThirstyLvl > 0) player.heal(event.getAmount() * (0.15f * bloodThirstyLvl));
        int bloodEdgeLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_edge");
        if (bloodEdgeLvl > 0) player.heal(event.getAmount() * (0.10f * bloodEdgeLvl));
        // FIX DOUBLE-COMPTAGE : le bonus de dégâts (+2/niv) de blood_pact est
        // désormais un vrai AttributeModifier dans RealAttributeModifierHandler,
        // déjà inclus dans event.getAmount() de base. Seul l'effet SECONDAIRE
        // (drain de 1 PV à chaque coup, non représentable comme attribut)
        // reste géré ici.
        int bloodPactLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_pact");
        if (bloodPactLvl > 0 && player.getHealth() > 2f) {
            player.setHealth(player.getHealth() - 1f);
        }
        // (crit_rate est gere dans le bloc PATCH 1 plus bas, a 10%/niveau, sans doublon de crit)

        int sweepLvl = getEnchantLvl(weapon, "deep_lucky_block:annihilating_sweep");
        if (sweepLvl > 0) {
            AABB box = event.getEntity().getBoundingBox().inflate(3.0);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box)) {
                if (mob != event.getEntity() && mob.isAlive()) {
                    // FIX (mob-equipment-enchant generalization) : DamageSources.playerAttack
                    // exige un Player ; pour un mob porteur on retombe sur
                    // mobAttack, l'equivalent vanilla pour un attaquant non-joueur.
                    hurtNoCascade(mob, player instanceof Player pAtk
                            ? player.damageSources().playerAttack(pAtk)
                            : player.damageSources().mobAttack(player), event.getAmount() * 0.5f);
                }
            }
        }
        int bloodDrinkerLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_drinker_edge");
        if (bloodDrinkerLvl > 0) player.heal(event.getAmount() * (0.2f * bloodDrinkerLvl));

        int fearLvl = getEnchantLvl(weapon, "deep_lucky_block:fear");
        if (fearLvl > 0) {
            int chance = player.getY() < 0 ? 60 : 30;
            if (player.level().random.nextInt(100) < chance) {
                event.setCanceled(true);
                // Feedback bien visible : le coup rate, le porteur est brièvement
                // ralenti par la peur, avec son + particules d'âme sur la cible.
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, true, true));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 0.9f, 0.5f);
                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL,
                            event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ(),
                            10, 0.35, 0.5, 0.35, 0.02);
                }
                return;
            }
        }
        int berserkLvl = Math.max(getEnchantLvl(weapon, "deep_lucky_block:berserk"), getEnchantLvl(weapon, "deep_lucky_block:desperate_kill"));
        if (berserkLvl > 0) {
            float missingHp = player.getMaxHealth() - player.getHealth();
            event.setAmount(event.getAmount() + (missingHp * 0.5f * berserkLvl));
        }
        int executeLvl = getEnchantLvl(weapon, "deep_lucky_block:execute_chance");
        if (executeLvl > 0) {
            float targetHpPct = event.getEntity().getHealth() / event.getEntity().getMaxHealth();
            if (targetHpPct <= (0.05f * executeLvl)) {
                event.setAmount(event.getEntity().getMaxHealth() * 10f);
            }
        }
        int newEdgeLvl = getEnchantLvl(weapon, "deep_lucky_block:new_edge");
        if (newEdgeLvl > 0 && weapon.isDamageableItem()) {
            float durPct = (float)(weapon.getMaxDamage() - weapon.getDamageValue()) / weapon.getMaxDamage();
            event.setAmount(event.getAmount() * (1f + durPct));
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
        // FIX DOUBLE-COMPTAGE : sharp_edge (+1.0/niv) et reinforced_sharpness
        // (+2.0/niv) sont désormais de vrais AttributeModifier Attack Damage
        // dans RealAttributeModifierHandler, déjà inclus dans event.getAmount()
        // de base -- plus de recalcul ici.
        // sharpened_sharpness : seule sa partie FIXE (+1.5/niv) est un vrai
        // AttributeModifier (déjà incluse dans event.getAmount()). Sa partie
        // CONDITIONNELLE (+10%/niv, seulement si la cible porte de l'armure)
        // n'est pas représentable comme attribut permanent et reste gérée ici.
        int sharpSharpLvl = getEnchantLvl(weapon, "deep_lucky_block:sharpened_sharpness");
        if (sharpSharpLvl > 0 && event.getEntity().getArmorValue() > 0) {
            event.setAmount(event.getAmount() + event.getAmount() * (0.10f * sharpSharpLvl));
        }
        // bloodEdgeLvl est déjà déclaré plus haut dans cette méthode.
        if (bloodEdgeLvl > 0) event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 100, bloodEdgeLvl - 1));
        // POISON FANG : les enchantements « Fang » de mods Minecraft appliquent
        // leur effet à chaque morsure/frappe ; ici la cible est empoisonnée.
        int poisonFangLvl = getEnchantLvl(weapon, "deep_lucky_block:poison_fang");
        if (poisonFangLvl > 0) {
            event.getEntity().addEffect(new MobEffectInstance(
                    MobEffects.POISON, 60 + 40 * poisonFangLvl,
                    Math.min(1, poisonFangLvl - 1)));
        }
        int witherEdgeLvl = getEnchantLvl(weapon, "deep_lucky_block:wither_edge");
        if (witherEdgeLvl > 0) event.getEntity().addEffect(new MobEffectInstance(MobEffects.WITHER, 100, witherEdgeLvl - 1));
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
        // Frappe Critique / Degats Critiques / Critique Leger : base 1.0 (juste les bonus sur un crit vanilla)
        int critStrikeLvl = getEnchantLvl(weapon, "deep_lucky_block:critical_strike");
        int dmgCritLvl = getEnchantLvl(weapon, "deep_lucky_block:damage_critical");
        int lowCritLvl = getEnchantLvl(weapon, "deep_lucky_block:low_damage_critical");
        if (critStrikeLvl > 0 || dmgCritLvl > 0 || lowCritLvl > 0) {
            if (player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()) {
                float critMult = 1.0f + (0.15f * critStrikeLvl) + (0.20f * dmgCritLvl) + (0.10f * lowCritLvl);
                event.setAmount(event.getAmount() * critMult);
            }
        }
        // Traumatisme Ardent & Frappe Silex
        int blazeTraumaLvl = getEnchantLvl(weapon, "deep_lucky_block:blazing_trauma");
        int flintLvl = getEnchantLvl(weapon, "deep_lucky_block:flint_strike");
        if (blazeTraumaLvl > 0 || flintLvl > 0) {
            event.getEntity().setRemainingFireTicks((3 * (Math.max(blazeTraumaLvl, flintLvl) * 20)));
            if (blazeTraumaLvl > 0) event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, blazeTraumaLvl - 1));
        }
        // Balayages Ardents & Ameliores
        int sweepFire = Math.max(getEnchantLvl(weapon, "deep_lucky_block:sweeping_fire"), Math.max(getEnchantLvl(weapon, "deep_lucky_block:fiery_sweep"), Math.max(getEnchantLvl(weapon, "deep_lucky_block:sweeping_burn_slash"), getEnchantLvl(weapon, "deep_lucky_block:blazing_soul_sweep"))));
        int sweepSlash = Math.max(
                getEnchantLvl(weapon, "deep_lucky_block:reinforced_sweeping"),
                Math.max(getEnchantLvl(weapon, "deep_lucky_block:sweeping_strike"),
                        Math.max(getEnchantLvl(weapon, "deep_lucky_block:sweeping_slash"),
                                getEnchantLvl(weapon, "deep_lucky_block:sweeping_enhancement"))));
        if (sweepFire > 0 || sweepSlash > 0) {
            AABB box = event.getEntity().getBoundingBox().inflate(2.5 + (0.5 * sweepSlash));
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box)) {
                if (mob != event.getEntity() && mob.isAlive()) {
                    if (sweepSlash > 0) hurtNoCascade(mob, player instanceof Player pSw
                            ? player.damageSources().playerAttack(pSw)
                            : player.damageSources().mobAttack(player), event.getAmount() * (0.3f + 0.1f * sweepSlash));
                    if (sweepFire > 0) mob.setRemainingFireTicks((3 * sweepFire) * 20);
                }
            }
        }
        int lastStandLvl = getEnchantLvl(weapon, "deep_lucky_block:last_stand_slayer");
        if (lastStandLvl > 0 && event.getEntity().getHealth() < (event.getEntity().getMaxHealth() * 0.30f)) {
            event.setAmount(event.getAmount() * (1f + 0.25f * lastStandLvl));
        }
        int ambushLvl = getEnchantLvl(weapon, "deep_lucky_block:focused_ambush");
        if (ambushLvl > 0 && event.getEntity().getHealth() >= event.getEntity().getMaxHealth()) {
            event.setAmount(event.getAmount() * (1f + 0.20f * ambushLvl));
        }
        // FIX DOUBLE-COMPTAGE : brutal/mighty_strike/sighing_strike (Attack
        // Damage) et la famille shovel_* sont désormais de vrais
        // AttributeModifier dans RealAttributeModifierHandler, déjà inclus
        // dans event.getAmount() de base. Le knockback de mighty_strike est
        // également couvert par son propre AttributeModifier ATTACK_KNOCKBACK
        // -- depuis Minecraft 1.21 (24w18a), cet attribut est appliqué
        // NATIVEMENT aux joueurs par le jeu lui-même (avant cette version il
        // n'affectait que les mobs, d'où l'ancien appel manuel knockback()
        // ici). Le réappliquer manuellement produirait désormais un DOUBLE
        // recul (attribut natif + appel manuel), donc plus aucun code ici :
        // le seul AttributeModifier suffit, tooltip ET effet réel inclus.
        int pryLvl = getEnchantLvl(weapon, "deep_lucky_block:armor_pry");
        if (pryLvl > 0 && event.getEntity().getArmorValue() > 0) event.setAmount(event.getAmount() * (1f + 0.15f * pryLvl));
        // Stealth Hunter, Transfer Misfortune
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
        // GAUNTLETS (FIST) sur plastron : effets offensifs main vide uniquement
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
        if (blzRage > 0 && player.isOnFire()) event.setAmount(event.getAmount() * (1f + 0.20f * blzRage));
        int sturdy = getArmorEnchantLvl(player, "deep_lucky_block:sturdy_brute");
        if (sturdy > 0 && player.getHealth() > 20f) event.setAmount(event.getAmount() + (player.getHealth() - 20f) * 0.1f);
        int endCurse = getArmorEnchantLvl(player, "deep_lucky_block:end_curse");
        if (endCurse > 0) event.setAmount(event.getAmount() * (player.level().dimension() == Level.END ? 0.4f : 1.3f));
        int exhaust = getEnchantLvl(weapon, "deep_lucky_block:exhaustion");
        if (exhaust > 0 && player.getAirSupply() < 300) event.setAmount(event.getAmount() * 0.5f);
        // Curses dimensionnels
        int unCurse = getEnchantLvl(weapon, "deep_lucky_block:undead_curse");
        if (unCurse > 0) {
            if (event.getEntity() instanceof net.minecraft.world.entity.monster.Zombie || event.getEntity() instanceof net.minecraft.world.entity.monster.Skeleton) event.setAmount(event.getAmount() * 0.4f);
            else if (event.getEntity() instanceof Player || event.getEntity() instanceof Villager || event.getEntity() instanceof Pillager) event.setAmount(event.getAmount() * 1.5f);
        }
        int ovCurse = getEnchantLvl(weapon, "deep_lucky_block:overworld_curse");
        int neCurse = getEnchantLvl(weapon, "deep_lucky_block:nether_curse");
        if (ovCurse > 0) event.setAmount(event.getAmount() * (player.level().dimension() == Level.OVERWORLD ? 0.4f : 1.3f));
        if (neCurse > 0) event.setAmount(event.getAmount() * (player.level().dimension() == Level.NETHER ? 0.4f : 1.3f));
        int battleScar = getEnchantLvl(weapon, "deep_lucky_block:battle_scarred");
        if (battleScar > 0 && weapon.isDamageableItem()) {
            float lost = (float) weapon.getDamageValue() / weapon.getMaxDamage();
            event.setAmount(Math.max(0.0f, event.getAmount() * (1f - lost)));
            // Visuel : l'arme s'use réellement à chaque coup (+2 durabilité par
            // coup). La barre de durabilité descend donc visiblement et les
            // dégâts diminuent en même temps -> cohérent et perceptible.
            if (player.level() instanceof net.minecraft.server.level.ServerLevel slw) weapon.hurtAndBreak(2, slw, player, item -> {});
        }
        // FIX DOUBLE-COMPTAGE : blunt (-1.5 Attack Damage/niv) est désormais
        // un vrai AttributeModifier négatif dans RealAttributeModifierHandler,
        // déjà inclus dans event.getAmount() de base. Le recalculer ici en
        // plus infligeait la pénalité deux fois.

        // ============================================================
        // FIX CRIT x2.25 (PATCH 1) : Minecraft applique deja le x1.5 du crit
        // vanilla AVANT LivingIncomingDamageEvent. On ne multiplie QUE si un enchant de
        // crit custom est present, base 1.0 sur crit vanilla / 1.5 sur crit custom.
        // ============================================================
        int lowCritRate = getEnchantLvl(weapon, "deep_lucky_block:low_crit_rate");
        int normalCritRate = getEnchantLvl(weapon, "deep_lucky_block:crit_rate");
        int focCrit = getEnchantLvl(weapon, "deep_lucky_block:focused_crit");
        int lowCritDmg = getEnchantLvl(weapon, "deep_lucky_block:low_crit_damage");
        int normCritDmg = getEnchantLvl(weapon, "deep_lucky_block:normal_crit_damage");
        int overCritDmg = getEnchantLvl(weapon, "deep_lucky_block:overclocked_crit");
        boolean hasCustomCrit = lowCritRate > 0 || normalCritRate > 0 || focCrit > 0 || lowCritDmg > 0 || normCritDmg > 0 || overCritDmg > 0;
        if (hasCustomCrit) {
            boolean vanillaCrit = player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater();
            boolean customCrit = (focCrit > 0 && player.isShiftKeyDown()) || (player.level().random.nextInt(100) < (lowCritRate * 5 + normalCritRate * 10));
            if (vanillaCrit || customCrit) {
                float base = vanillaCrit ? 1.0f : 1.5f;
                float critMult = base + (0.10f * lowCritDmg) + (0.20f * normCritDmg) + (1.0f * overCritDmg);
                event.setAmount(event.getAmount() * critMult);

                // OVERCHARGE (Electric Heart) : sur un coup critique, chance
                // de dechainer une chaine electrique vers les ennemis proches.
                // Fun/agressif, sans element defensif -- l'inverse d'Umbral
                // Pact plus bas qui est un vrai pacte a double tranchant.
                int overcharge = getEnchantLvl(weapon, "deep_lucky_block:overcharge");
                if (overcharge > 0 && event.getEntity() instanceof LivingEntity primaryTarget
                        && player.level().random.nextInt(100) < (20 + 10 * overcharge)) {
                    // THUNDERCALL PACT (Electric Heart, arme) : sous la pluie,
                    // synergie eau/electricite assumee -- chaine plus longue
                    // et plus violente, a l'oppose de la faiblesse classique
                    // "eau conduit l'electricite contre soi".
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
                            chainLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                    next.getX(), next.getY() + next.getBbHeight() * 0.5, next.getZ(),
                                    stormBoost ? 24 : 14, 0.3, 0.3, 0.3, 0.02);
                        }
                        current = next;
                    }
                }
            }
        }

        // TITAN'S GRASP (Earth Heart, arme) : degats de melee ecrasants,
        // contrebalances par un malus de vitesse d'attaque applique en
        // continu (voir EquipmentExpansionHandler#updateHeartEnchantmentPassives)
        // -- le colosse lent et inarretable, trope manhwa classique.
        int titansGrasp = getEnchantLvl(weapon, "deep_lucky_block:titans_grasp");
        if (titansGrasp > 0) event.setAmount(event.getAmount() * (1f + 0.30f * titansGrasp));

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
        int execPwr = getEnchantLvl(weapon, "deep_lucky_block:execute_power");
        if (execPwr > 0 && event.getEntity().getHealth() < (event.getEntity().getMaxHealth() * (0.05f + 0.05f * execPwr))) {
            event.setAmount(event.getEntity().getMaxHealth() * 10f);
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
        int shReach = getEnchantLvl(weapon, "deep_lucky_block:short_reach");
        if (reachGaunt > 0 && weapon.isEmpty()) event.setAmount(event.getAmount() + 1.0f * reachGaunt);
        if (blzBat > 0 && player.isOnFire()) event.setAmount(event.getAmount() * (1f + 0.20f * blzBat));
        if (shReach > 0) event.setAmount(Math.max(0.5f, event.getAmount() - 1.0f * shReach));
        // FIX DOUBLE-COMPTAGE : swift_strike (+1.0/niv) et extension (+1.2/niv)
        // sont désormais de vrais AttributeModifier Attack Damage dans
        // RealAttributeModifierHandler, déjà inclus dans event.getAmount() de
        // base. Les rajouter ici en plus doublait leur bonus.
        // BLOOD TRANSFUSION : soigne le mob frappé. L'arme donne de la vie au
        // lieu d'en retirer (Healer's Sword).
        // FIX rapport en jeu ("la healer sword ne frappe meme pas l'ennemi,
        // le clic gauche ne fait aucune animation") : event.setCanceled(true)
        // fait retourner FALSE a Entity#hurtOrSimulate/hurtServer côté
        // vanilla, ce qui arrête TOUTE la pipeline d'attaque du joueur avant
        // qu'elle ne soit considérée réussie (pas de swing d'animation, pas
        // de son de coup, pas de knockback, pas de hurtEnemy/postHurtEnemy).
        // C'est différent de mettre les dégâts à 0 : avec amount=0, le coup
        // est bien traité comme un HIT valide (animation, son, knockback
        // fonctionnent normalement), seule la perte de vie de la cible est
        // neutralisée -> exactement l'effet "soigneur" voulu, sans casser
        // l'attaque elle-même.
        int transfLvl = getEnchantLvl(weapon, "deep_lucky_block:blood_transfusion");
        if (transfLvl > 0) {
            event.setAmount(0.0f);
            event.getEntity().heal(1.0f * transfLvl);
            return;
        }
        // Indestructible & Eternite
        int unbrLvl = Math.max(getEnchantLvl(weapon, "deep_lucky_block:unbreakable"), getEnchantLvl(weapon, "deep_lucky_block:eternal"));
        if (unbrLvl > 0 && weapon.isDamageableItem()) weapon.setDamageValue(0);
        // Decapitation
        int decapLvl = Math.max(getEnchantLvl(weapon, "deep_lucky_block:decapitation"), getEnchantLvl(weapon, "deep_lucky_block:beheading"));
        if (decapLvl > 0 && player.level().random.nextInt(100) < (15 * decapLvl)) {
            if (event.getEntity() instanceof net.minecraft.world.entity.monster.Zombie) event.getEntity().spawnAtLocation(Items.ZOMBIE_HEAD);
            else if (event.getEntity() instanceof net.minecraft.world.entity.monster.Skeleton) event.getEntity().spawnAtLocation(Items.SKELETON_SKULL);
            else if (event.getEntity() instanceof net.minecraft.world.entity.monster.Creeper) event.getEntity().spawnAtLocation(Items.CREEPER_HEAD);
            else if (event.getEntity() instanceof Player targetP) {
                ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
                deepluckyblock.util.DeepNbt.getOrCreateTag(skull).putString("SkullOwner", targetP.getGameProfile().getName());
                event.getEntity().spawnAtLocation(skull);
            }
        }
        // Armee du Chaos
        int armyLvl = getEnchantLvl(weapon, "deep_lucky_block:summon_army");
        if (armyLvl > 0 && player.level().random.nextInt(100) < (10 * armyLvl) && !player.level().isClientSide()) {
            net.minecraft.world.entity.animal.Wolf wolf = new net.minecraft.world.entity.animal.Wolf(EntityType.WOLF, player.level());
            wolf.setTame(true, true);
            wolf.setOwnerUUID(player.getUUID());
            wolf.moveTo(player.getX(), player.getY(), player.getZ());
            player.level().addFreshEntity(wolf);
        }
        // Chasse de l'Ombre / Instinct Meurtrier / Sens
        int shadowLvl = getEnchantLvl(weapon, "deep_lucky_block:shadow_hunt");
        int killLvl = getEnchantLvl(weapon, "deep_lucky_block:killing_sense");
        int senseLvl = getEnchantLvl(weapon, "deep_lucky_block:sense");
        if (shadowLvl > 0 && player.level().getMaxLocalRawBrightness(player.blockPosition()) < 7) event.setAmount(event.getAmount() * (1f + 0.15f * shadowLvl));
        if (killLvl > 0 && event.getEntity().getHealth() < (event.getEntity().getMaxHealth() * 0.25f)) event.setAmount(event.getAmount() * (1f + 0.25f * killLvl));
        if (senseLvl > 0) {
            event.setAmount(event.getAmount() + (1.0f * senseLvl));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
        }
        // Blessure Affaiblissante & Cicatrice Empoisonnee
        int weakWound = Math.max(getEnchantLvl(weapon, "deep_lucky_block:weakness_wound"), getEnchantLvl(weapon, "deep_lucky_block:weakness_trauma"));
        int poisonScar = getEnchantLvl(weapon, "deep_lucky_block:poison_scar");
        if (weakWound > 0) event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, weakWound - 1));
        if (poisonScar > 0) event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 100, poisonScar - 1));
        // Coup du Desespoir & Frappe Nourriciere
        int despLvl = getEnchantLvl(weapon, "deep_lucky_block:desperate_kill");
        int foodLvl = getEnchantLvl(weapon, "deep_lucky_block:food_power_strike");
        if (despLvl > 0 && player.getHealth() < (player.getMaxHealth() * 0.30f)) event.setAmount(event.getAmount() * (1f + 0.30f * despLvl));
        // FOOD POWER STRIKE : necessite FoodData (jauge de faim), propre au
        // JOUEUR -- reste Player-only, un mob n'a pas de faim.
        if (foodLvl > 0 && player instanceof Player foodPlayer && foodPlayer.getFoodData().getFoodLevel() >= 18) event.setAmount(event.getAmount() * (1f + 0.20f * foodLvl));
        // Devoration & Sangsue
        int devLvl = Math.max(getEnchantLvl(weapon, "deep_lucky_block:devour"), getEnchantLvl(weapon, "deep_lucky_block:leech"));
        if (devLvl > 0) player.heal(event.getAmount() * 0.15f * devLvl);
        // Poing Direct
        int gauntLvl = getEnchantLvl(gauntletChestplate, "deep_lucky_block:straight_punch_gauntlet");
        if (gauntLvl > 0) event.setAmount(event.getAmount() + (3.0f * gauntLvl));
        // Loup Solitaire & Massacre de Masse
        int loneLvl = Math.max(getEnchantLvl(weapon, "deep_lucky_block:lone_wolf"), getEnchantLvl(weapon, "deep_lucky_block:solo_combat"));
        int massLvl = getEnchantLvl(weapon, "deep_lucky_block:mass_slaughter");
        if (loneLvl > 0 && player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10.0)).size() <= 1) event.setAmount(event.getAmount() * (1f + 0.20f * loneLvl));
        if (massLvl > 0) {
            int mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6.0)).size();
            if (mobs >= 3) event.setAmount(event.getAmount() * (1f + 0.10f * massLvl * mobs));
        }
        // Pesanteur & Coup de Masse
        int slowHvy = Math.max(getEnchantLvl(weapon, "deep_lucky_block:slow_heavy_weakness"), getEnchantLvl(weapon, "deep_lucky_block:heavy_slow_hit"));
        if (slowHvy > 0) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, slowHvy - 1));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, slowHvy - 1));
        }
        // Corrosion Dimensionnelle
        int nCor = getEnchantLvl(weapon, "deep_lucky_block:nether_corrosion");
        int eCor = getEnchantLvl(weapon, "deep_lucky_block:end_corrosion");
        if (nCor > 0 && (player.level().dimension() == Level.NETHER || event.getEntity() instanceof net.minecraft.world.entity.monster.Blaze || event.getEntity() instanceof net.minecraft.world.entity.monster.WitherSkeleton)) event.setAmount(event.getAmount() * (1f + 0.30f * nCor));
        if (eCor > 0 && (player.level().dimension() == Level.END || event.getEntity() instanceof net.minecraft.world.entity.monster.EnderMan)) event.setAmount(event.getAmount() * (1f + 0.30f * eCor));
        // Malediction de Tremblement
        int tremorLvl = getEnchantLvl(weapon, "deep_lucky_block:curse_of_tremor");
        if (tremorLvl > 0 && player.level().random.nextInt(100) < 15) {
            // Player.drop(ItemStack, boolean) est Player-only ; l'equivalent
            // generique Entity.spawnAtLocation(ItemStack) fonctionne aussi pour un mob.
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
    @SubscribeEvent
    public static void onPlayerTick(EntityTickEvent.Post event){try{onPlayerTickImpl(event);}catch(Throwable t__){guard("onPlayerTick",t__);}}
    private static void onPlayerTickImpl(EntityTickEvent.Post event){
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (living.level().isClientSide()) return;
        try {
            handlePlayerTick(living);
        } catch (Throwable t) {
            printHandlerError("onPlayerTick", t);
        }
    }

    private static void handlePlayerTick(LivingEntity player) {

        // 7. Gale : détection du saut (remplace LivingJumpEvent)
        try {
            boolean slbUgWasGround = player.getPersistentData().getBoolean("slb_ug_was_ground");
            player.getPersistentData().putBoolean("slb_ug_was_ground", player.onGround());
            if (!player.onGround() && slbUgWasGround && player.getDeltaMovement().y > 0.0) {
                int galeLvl = getEnchantLvl(player.getItemBySlot(EquipmentSlot.FEET), "deep_lucky_block:gale");
                if (galeLvl > 0) player.setDeltaMovement(player.getDeltaMovement().add(0, 0.1f * galeLvl, 0));
            }
        } catch (Throwable t) {
            printHandlerError("onPlayerJump", t);
        }

        // FIX SCALING : l'ancienne "normalisation" parcourait tout l'inventaire
        // chaque seconde et reecrivait le niveau des enchantements (X -> 1) des
        // que le maxLevel enregistre etait 1. C'est ce qui DETRUISAIT les niveaux
        // des items et empechait Basic Swiftness (et beaucoup d'autres) de scaler.
        // Le niveau stocke sur l'item est desormais preserve.

        int weedHoe = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:weeding_hoe");
        if (weedHoe > 0 && player.tickCount % 40 == 0) {
            BlockPos c = player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
                if (player.level().getBlockState(p).is(Blocks.TALL_GRASS) || player.level().getBlockState(p).is(Blocks.GRASS_BLOCK)) player.level().destroyBlock(p, true, player);
            }
        }
        int farmHoe = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:farmers_hoe");
        if (farmHoe > 0 && player.tickCount % 20 == 0) {
            BlockPos c = player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
                BlockState st = player.level().getBlockState(p);
                if (st.is(Blocks.WHEAT) || st.is(Blocks.CARROTS) || st.is(Blocks.POTATOES)) player.level().destroyBlock(p, true, player);
            }
        }
        int rainHeal = getArmorEnchantLvl(player, "deep_lucky_block:rain_nourishment");
        if (rainHeal > 0 && player.level().isRaining() && player.tickCount % 60 == 0) player.heal(1.0f * rainHeal);
        int kingLvl = getArmorEnchantLvl(player, "deep_lucky_block:king");
        if (kingLvl > 0 && player.tickCount % 220 == 0) {
            for (Player ally : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10.0))) if (ally != player) ally.heal(1.0f);
        }
        // LEADER : tant que l'épée est tenue, le porteur et les joueurs alliés
        // proches reçoivent Force et Résistance. Les effets courts sont renouvelés.
        int leaderLvl = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:leader");
        if (leaderLvl > 0 && player.tickCount % 20 == 0) {
            int amplifier = Math.max(0, leaderLvl - 1);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, amplifier, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, amplifier, true, false));
            for (Player ally : player.level().getEntitiesOfClass(Player.class,
                    player.getBoundingBox().inflate(10.0))) {
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
        if (nightVeil > 0 && player.isShiftKeyDown()) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, true, false));
        int heartbeat = getArmorEnchantLvl(player, "deep_lucky_block:heartbeat_rhythm");
        if (heartbeat > 0 && player.tickCount % 20 == 0) player.heal(player.getMaxHealth() * 0.01f * heartbeat);
        int itemAttr = getArmorEnchantLvl(player, "deep_lucky_block:item_attraction");
        // ItemEntity.playerTouch(Player) est Player-only (ramassage direct
        // dans l'inventaire) ; un mob generique n'a pas ce mecanisme, on
        // saute simplement cet effet pour lui.
        if (itemAttr > 0 && player.isShiftKeyDown() && player.tickCount % 5 == 0 && player instanceof Player attrPlayer) {
            for (net.minecraft.world.entity.item.ItemEntity ie : player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(2.0 * itemAttr))) ie.playerTouch(attrPlayer);
        }
        int dreadAura = getArmorEnchantLvl(player, "deep_lucky_block:dread_aura");
        if (dreadAura > 0 && player.tickCount % 40 == 0) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0))) {
                if (mob.getTarget() == player) mob.setTarget(null);
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
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
        if (conduit > 0 && (player.isInWater() || player.level().isRaining())) player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 40, 0, true, false));
        int cloudStride = getArmorEnchantLvl(player, "deep_lucky_block:cloud_stride");
        if (cloudStride > 0 && player.getY() > 195 && player.level().getBlockState(player.blockPosition().below()).isAir()) player.level().setBlockAndUpdate(player.blockPosition().below(), Blocks.WHITE_WOOL.defaultBlockState());
        // lightness & basic_lightness : on tombe plus lentement (leger comme une plume). lightness = plus fort.
        int lightSlow = getArmorEnchantLvl(player, "deep_lucky_block:lightness");
        int basicLightSlow = getArmorEnchantLvl(player, "deep_lucky_block:basic_lightness");
        if (player.getDeltaMovement().y < 0) {
            if (lightSlow > 0) player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.75, 1.0));
            else if (basicLightSlow > 0) player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.88, 1.0));
        }
        int panic = Math.max(getArmorEnchantLvl(player, "deep_lucky_block:panic_escape"), getArmorEnchantLvl(player, "deep_lucky_block:last_stand_escape"));
        setStackingMovementBonus(player, PANIC_SPEED_ID, panic > 0 && player.getHealth() < player.getMaxHealth() * 0.20f
                        ? 0.10 * Math.max(1, panic) : 0);
        int superStinky = getArmorEnchantLvl(player, "deep_lucky_block:super_stinky_feet");
        int stinky = getArmorEnchantLvl(player, "deep_lucky_block:stinky_feet");
        if ((superStinky > 0 || stinky > 0) && player.tickCount % 20 == 0) {
            int radius = superStinky > 0 ? 5 : 3;
            int amp = superStinky > 0 ? 1 : 0;
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius))) {
                mob.addEffect(new MobEffectInstance(MobEffects.POISON, 80, amp));
                if (superStinky > 0) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
            }
        }
        int swiftStrikeLvl = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:swift_strike");
        if (swiftStrikeLvl > 0) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20, swiftStrikeLvl + 2, true, false));
        // Chrono Mending
        if (player.tickCount % 60 == 0) {
            for (ItemStack stack : player.getAllSlots()) {
                if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                    int chronoLvl = Math.max(getEnchantLvl(stack, "deep_lucky_block:chrono_mending"), Math.max(getEnchantLvl(stack, "deep_lucky_block:basic_chrono_mending"), getEnchantLvl(stack, "deep_lucky_block:basic_mending")));
                    if (chronoLvl > 0) stack.setDamageValue(Math.max(0, stack.getDamageValue() - Math.max(1, (int)(stack.getMaxDamage() * 0.01f * chronoLvl))));
                }
            }
        }
        // Assimilation : depend de l'inventaire complet + de la faim, qui
        // n'existent que chez un Player -- reste gate derriere instanceof.
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
        // Perception ESP (LivingEntity-safe : marche pour tout mob equipe d'un casque enchante)
        if (player.tickCount % 40 == 0) {
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (getEnchantLvl(helmet, "deep_lucky_block:perception") > 0) {
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0))) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            }
        }
        int adrLvl = getArmorEnchantLvl(player, "deep_lucky_block:adrenaline");
        if (adrLvl > 0 && player.getHealth() < (player.getMaxHealth() * 0.40f)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, adrLvl - 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, true, false));
        }
        int mysticLvl = getArmorEnchantLvl(player, "deep_lucky_block:mystic_mind");
        // L'XP-joueur n'existe pas pour un mob generique ; on saute simplement pour lui.
        if (mysticLvl > 0 && player.tickCount % 200 == 0 && player instanceof Player mysticPlayer) mysticPlayer.giveExperiencePoints(mysticLvl * 2);
        int speedLvl = Math.max(getArmorEnchantLvl(player, "deep_lucky_block:e_speed"), Math.max(getArmorEnchantLvl(player, "deep_lucky_block:dash"), Math.max(getArmorEnchantLvl(player, "deep_lucky_block:strafe"), getArmorEnchantLvl(player, "deep_lucky_block:slide"))));
        setStackingMovementBonus(player, CORE_SPRINT_SPEED_ID, speedLvl > 0 && player.isSprinting() ? 0.05 * speedLvl : 0);
        int amphLvl = getArmorEnchantLvl(player, "deep_lucky_block:amphibious");
        if (amphLvl > 0 && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, amphLvl - 1, true, false));
        }
        int blazeWalkLvl = getArmorEnchantLvl(player, "deep_lucky_block:blaze_walker");
        if (blazeWalkLvl > 0 && player.onGround()) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
            BlockPos pos = player.blockPosition().below();
            if (player.level().getBlockState(pos).is(Blocks.LAVA)) player.level().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
        }
        int leapLvl = Math.max(getArmorEnchantLvl(player, "deep_lucky_block:leap_power"), getArmorEnchantLvl(player, "deep_lucky_block:basic_leap_power"));
        if (leapLvl > 0) player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, leapLvl - 1, true, false));
        int phantomAv = getArmorEnchantLvl(player, "deep_lucky_block:phantom_bee_vex_aversion");
        if (phantomAv > 0 && player.tickCount % 40 == 0) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0))) if (mob.getTarget() == player) mob.setTarget(null);
        }
        int genLVL = getArmorEnchantLvl(player, "deep_lucky_block:general");
        int marRhy = getArmorEnchantLvl(player, "deep_lucky_block:martial_rhythm");
        // wing_chun_gauntlet = FIST : boost seulement si main principale vide
        int wingChun = player.getMainHandItem().isEmpty() ? getEnchantLvl(player.getItemBySlot(EquipmentSlot.CHEST), "deep_lucky_block:wing_chun_gauntlet") : 0;
        if ((genLVL > 0 || marRhy > 0 || wingChun > 0) && player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, genLVL + marRhy + wingChun, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, genLVL + marRhy, true, false));
        }
        int breathHeal = getArmorEnchantLvl(player, "deep_lucky_block:breath_heal");
        if (breathHeal > 0 && player.getAirSupply() == 300 && player.tickCount % 40 == 0) player.heal(2.0f);
        int sprintImp = getArmorEnchantLvl(player, "deep_lucky_block:sprint_impact");
        int bSwift = getArmorEnchantLvl(player, "deep_lucky_block:basic_swiftness");
        int swift2 = getArmorEnchantLvl(player, "deep_lucky_block:swiftness");
        int shadowSpr = getArmorEnchantLvl(player, "deep_lucky_block:shadow_sprint");
        int lightWalk = getArmorEnchantLvl(player, "deep_lucky_block:light_walker");
        int oxySpr = getArmorEnchantLvl(player, "deep_lucky_block:oxygen_sprint");
        int oxyHaste = getArmorEnchantLvl(player, "deep_lucky_block:oxygen_lack_haste");
        int totalSpeed = sprintImp + bSwift + swift2 + shadowSpr + lightWalk + oxySpr + oxyHaste;
        setStackingMovementBonus(player, STACKED_SPRINT_SPEED_ID, totalSpeed > 0 && player.isSprinting()
                        ? Math.min(0.60, 0.035 * totalSpeed) : 0);
        // oxygen_lack_haste : donne aussi la Celerite (Haste), pas seulement la Speed
        if (oxyHaste > 0 && player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, oxyHaste, true, false));
        int scorchBath = getArmorEnchantLvl(player, "deep_lucky_block:scorching_bath");
        if (scorchBath > 0 && player.isOnFire() && player.tickCount % 20 == 0) player.heal(player.getMaxHealth() * 0.01f * scorchBath);
        int fireDev = getArmorEnchantLvl(player, "deep_lucky_block:fire_devour");
        if (fireDev > 0 && player.isOnFire()) {
            player.clearFire();
            // Le regain de faim n'a de sens que pour un joueur ; pour un
            // mob generique, l'extinction de feu suffit deja comme benefice.
            if (player instanceof Player fdPlayer) fdPlayer.getFoodData().setFoodLevel(Math.min(20, fdPlayer.getFoodData().getFoodLevel() + fireDev));
        }
        // FIX COEURS D'ABSORPTION : cap ampli 1 + fenetre 5s (avant = invincibilite permanente)
        int meatArm = getArmorEnchantLvl(player, "deep_lucky_block:meat_armor");
        int hundVet = getArmorEnchantLvl(player, "deep_lucky_block:hundred_battles_veteran");
        int tempBody = Math.max(getArmorEnchantLvl(player, "deep_lucky_block:tempered_body"), getArmorEnchantLvl(player, "deep_lucky_block:exercise"));
        int glut = getArmorEnchantLvl(player, "deep_lucky_block:gluttony");
        int corp = getArmorEnchantLvl(player, "deep_lucky_block:corpulent");
        int wingTough = getArmorEnchantLvl(player, "deep_lucky_block:wing_toughness_armor");
        if ((meatArm > 0 || hundVet > 0 || tempBody > 0 || glut > 0 || corp > 0 || wingTough > 0) && player.tickCount % 100 == 0) {
            int absLvl = Math.min(1, (meatArm + hundVet + tempBody + glut + corp + wingTough) - 1);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, Math.max(0, absLvl), true, false));
        }
        // SHELTER : petit effet de Régénération quand on sneak SANS BOUGER du
        // tout (à plat, immobile). Remplace l'ancien heal direct automatique.
        int shelter = getArmorEnchantLvl(player, "deep_lucky_block:shelter");
        int campHeal = getArmorEnchantLvl(player, "deep_lucky_block:campfire_heal");
        if (shelter > 0 && player.isShiftKeyDown() && player.onGround() && player.tickCount % 20 == 0) {
            boolean still = Math.abs(player.getDeltaMovement().x) < 0.005
                    && Math.abs(player.getDeltaMovement().z) < 0.005;
            if (still) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
            }
        }
        // CAMPFIRE HEAL : conserve son ancien comportement de soin direct.
        if (campHeal > 0 && player.tickCount % 20 == 0) player.heal(1.0f * campHeal);
        int focStead = getArmorEnchantLvl(player, "deep_lucky_block:focused_steadiness");
        if (focStead > 0 && player.isShiftKeyDown()) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, focStead - 1, true, false));
        int magmaWalk = getArmorEnchantLvl(player, "deep_lucky_block:magma_walker");
        if (magmaWalk > 0 && player.onGround() && player.level().getBlockState(player.blockPosition().below()).is(Blocks.LAVA)) player.level().setBlockAndUpdate(player.blockPosition().below(), Blocks.OBSIDIAN.defaultBlockState());
        int metStomp = getArmorEnchantLvl(player, "deep_lucky_block:meteor_stomp");
        if (metStomp > 0 && player.fallDistance >= 4.0f && player.onGround()) player.level().explode(player, player.getX(), player.getY(), player.getZ(), 3.0f, Level.ExplosionInteraction.NONE);
        int rapDrop = getArmorEnchantLvl(player, "deep_lucky_block:rapid_drop_stop");
        int earthBnd = getArmorEnchantLvl(player, "deep_lucky_block:earth_bound");
        if (((rapDrop > 0 && player.isShiftKeyDown()) || earthBnd > 0) && player.getDeltaMovement().y < 0) player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 1.5, 1.0));
        int footPl = Math.max(getArmorEnchantLvl(player, "deep_lucky_block:foot_placement"), getArmorEnchantLvl(player, "deep_lucky_block:range_foot_placement"));
        if (footPl > 0 && !player.onGround() && player.level().getBlockState(player.blockPosition().below()).isAir()) player.level().setBlockAndUpdate(player.blockPosition().below(), Blocks.COBBLESTONE.defaultBlockState());
        int oxyHov = getArmorEnchantLvl(player, "deep_lucky_block:oxygen_hover");
        if (oxyHov > 0 && player.isShiftKeyDown()) player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        int highStp = getArmorEnchantLvl(player, "deep_lucky_block:high_step");
        if (highStp > 0 && player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, highStp - 1, true, false));
        int shortBreath = getArmorEnchantLvl(player, "deep_lucky_block:shortness_of_breath");
        if (shortBreath > 0 && player.isSprinting() && player.tickCount % 20 == 0) player.hurt(player.damageSources().drown(), 2.0f);
        int enemSpd = getArmorEnchantLvl(player, "deep_lucky_block:enemy_speed_pursuit");
        if (enemSpd > 0 && player.isSprinting() && player.tickCount % 20 == 0) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0))) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2));
        }
        int noiseDist = getArmorEnchantLvl(player, "deep_lucky_block:noise_disturbance");
        if (noiseDist > 0 && player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        int horseB = getArmorEnchantLvl(player, "deep_lucky_block:horse_boots");
        if (horseB > 0 && player.getVehicle() instanceof LivingEntity mount && player.tickCount % 20 == 0) mount.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false));
        int magLvl = getArmorEnchantLvl(player, "deep_lucky_block:magnetic");
        if (magLvl > 0 && player.tickCount % 10 == 0 && player instanceof Player magPlayer) {
            for (net.minecraft.world.entity.item.ItemEntity ie : player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(6.0))) ie.playerTouch(magPlayer);
        }
        int breathLvl = getArmorEnchantLvl(player, "deep_lucky_block:breath_holding");
        if (breathLvl > 0 && player.getAirSupply() < 200) player.setAirSupply(300);
        int hasteSurge = getEnchantLvl(player.getMainHandItem(), "deep_lucky_block:haste_surge");
        if (hasteSurge > 0 && player.tickCount % 20 == 0 && player.level().random.nextInt(100) < (hasteSurge * 10)) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, 0, true, false));
        int rangerLvl = getEnchantLvl(player.getUseItem(), "deep_lucky_block:ranger");
        boolean aimingRanged = player.isUsingItem()
                && (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.CROSSBOW));
        setStackingMovementBonus(player, RANGER_SPEED_ID, aimingRanged && rangerLvl > 0 ? 0.05 * rangerLvl : 0);
        // Steady Shot ne ralentit plus le joueur : sa stabilité vient uniquement
        // de la trajectoire droite et sans gravité de la flèche.

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

        // WALL RUNNER — cling to a wall, slow the fall and move along it.
        int wallRunner = getArmorEnchantLvl(player, "deep_lucky_block:wall_runner");
        if (wallRunner > 0 && player.horizontalCollision && !player.onGround()) {
            net.minecraft.world.phys.Vec3 current = player.getDeltaMovement();
            double minY = wallRunner >= 2 ? 0.015 : -0.045;
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            player.setDeltaMovement(current.x + look.x * 0.035 * wallRunner,
                    Math.max(current.y, minY),
                    current.z + look.z * 0.035 * wallRunner);
            player.fallDistance = 0.0F;
            player.hurtMarked = true;
        }

        // BURST STEP — a short dash when a new sprint starts, with cooldown.
        int burst = getArmorEnchantLvl(player, "deep_lucky_block:burst_step");
        boolean wasSprinting = player.getPersistentData().getBoolean("slb_was_sprinting");
        long now = player.level().getGameTime();
        long readyAt = player.getPersistentData().getLong("slb_burst_ready");
        if (burst > 0 && player.isSprinting() && !wasSprinting && now >= readyAt) {
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            double force = 0.40 + 0.16 * burst;
            player.setDeltaMovement(player.getDeltaMovement().add(
                    look.x * force, Math.max(0.0, look.y * 0.12), look.z * force));
            player.getPersistentData().putLong("slb_burst_ready",
                    now + Math.max(35, 75 - 10L * burst));
            player.hurtMarked = true;
        }
        player.getPersistentData().putBoolean("slb_was_sprinting", player.isSprinting());

        // WINDRUNNER — open-sky mobility, strongest at high altitude.
        int windrunner = getArmorEnchantLvl(player, "deep_lucky_block:windrunner");
        boolean openSkySprint = windrunner > 0 && player.isSprinting()
                && player.level().canSeeSky(player.blockPosition());
        int windStages = openSkySprint
                ? windrunner + (player.getY() >= 100.0 ? 1 : 0) : 0;
        setStackingMovementBonus(player, WINDRUNNER_SPEED_ID, 0.05 * windStages);
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
        if (player.isShiftKeyDown()) return;
        Level level = (Level) event.getLevel();
        BlockPos origin = event.getPos();
        int recLvl = getEnchantLvl(tool, "deep_lucky_block:recycle");
        if (recLvl > 0 && player.level().random.nextInt(100) < 20) Block.popResource(level, origin, new ItemStack(Items.IRON_INGOT));
        int bHarv = getEnchantLvl(tool, "deep_lucky_block:bountiful_harvest");
        if (bHarv > 0 && !player.getAbilities().instabuild
                && event.getState().getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(event.getState())) {
            Item cropItem = event.getState().is(Blocks.WHEAT) ? Items.WHEAT : event.getState().is(Blocks.CARROTS) ? Items.CARROT : Items.POTATO;
            Block.popResource(level, origin, new ItemStack(cropItem, bHarv));
        }
        int utilTrench = getEnchantLvl(tool, "deep_lucky_block:utility_trench_shovel");
        int univTrench = getEnchantLvl(tool, "deep_lucky_block:universal_trench_shovel");
        if ((utilTrench > 0 || univTrench > 0) && (event.getState().is(Blocks.DIRT) || event.getState().is(Blocks.GRAVEL) || event.getState().is(Blocks.SAND))) { veinMine(level, origin, player, null, univTrench > 0 ? 64 : 32); return; }
        ItemStack gauntletChestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        int rockBrk = getEnchantLvl(gauntletChestplate, "deep_lucky_block:rock_breaking_gauntlet");
        int diamGlow = getEnchantLvl(gauntletChestplate, "deep_lucky_block:diamond_glow_gauntlet");
        if ((rockBrk > 0 || diamGlow > 0) && tool.isEmpty()) veinMine(level, origin, player, null, diamGlow > 0 ? 16 : 8);
        int lumberLvl = getEnchantLvl(tool, "deep_lucky_block:lumberjack");
        int treeLvl = getEnchantLvl(tool, "deep_lucky_block:tree_felling");
        int extractLvl = getEnchantLvl(tool, "deep_lucky_block:extracting");
        int veinLvl = getEnchantLvl(tool, "deep_lucky_block:vein_miner");
        int moltenLvl = getEnchantLvl(tool, "deep_lucky_block:molten");
        int scoopLvl = Math.max(getEnchantLvl(tool, "deep_lucky_block:scooping"), getEnchantLvl(tool, "deep_lucky_block:burrowing"));
        int snowLvl = getEnchantLvl(tool, "deep_lucky_block:snow_remover_shovel");
        if ((lumberLvl > 0 || treeLvl > 0) && event.getState().is(BlockTags.LOGS)) { veinMine(level, origin, player, BlockTags.LOGS, treeLvl > 0 ? 128 : 64); return; }
        if ((extractLvl > 0 || veinLvl > 0) && event.getState().is(Tags.Blocks.ORES)) { veinMine(level, origin, player, Tags.Blocks.ORES, veinLvl > 0 ? 64 : 32); return; }
        if (scoopLvl > 0 && (event.getState().is(Blocks.DIRT) || event.getState().is(Blocks.GRAVEL) || event.getState().is(Blocks.SAND))) { veinMine(level, origin, player, null, 32); return; }
        if (snowLvl > 0 && event.getState().is(Blocks.SNOW)) { veinMine(level, origin, player, null, 64); return; }
        if (moltenLvl > 0 && !level.isClientSide()) {
            if (event.getState().is(Blocks.IRON_ORE) || event.getState().is(Blocks.DEEPSLATE_IRON_ORE)) { event.setCanceled(true); level.destroyBlock(origin, false); Block.popResource(level, origin, new ItemStack(Items.IRON_INGOT)); }
            else if (event.getState().is(Blocks.GOLD_ORE) || event.getState().is(Blocks.DEEPSLATE_GOLD_ORE)) { event.setCanceled(true); level.destroyBlock(origin, false); Block.popResource(level, origin, new ItemStack(Items.GOLD_INGOT)); }
        }
        int hasteCont = Math.max(getEnchantLvl(tool, "deep_lucky_block:haste_continuation"), getEnchantLvl(tool, "deep_lucky_block:oxygen_lack_haste"));
        if (hasteCont > 0 && !level.isClientSide()) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 120, hasteCont - 1, true, false));
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event){try{onBreakSpeedImpl(event);}catch(Throwable t__){guard("onBreakSpeed",t__);}}
    private static void onBreakSpeedImpl(PlayerEvent.BreakSpeed event){
        try {
            Player player = event.getEntity();
            ItemStack tool = player.getMainHandItem();
            if (tool.isEmpty()) return;
            int fearLvl = getEnchantLvl(tool, "deep_lucky_block:fear");
            if (fearLvl > 0) event.setNewSpeed(event.getOriginalSpeed() * (player.getY() < 0 ? 0.2f : 0.6f));
            int milShovel = Math.max(getEnchantLvl(tool, "deep_lucky_block:military_shovel"), Math.max(getEnchantLvl(tool, "deep_lucky_block:utility_trench_shovel"), getEnchantLvl(tool, "deep_lucky_block:universal_trench_shovel")));
            if (milShovel > 0 && tool.getItem() instanceof net.minecraft.world.item.ShovelItem) event.setNewSpeed(event.getOriginalSpeed() * (1.0f + milShovel * 4.0f));
        } catch (Throwable t) {
            printHandlerError("onBreakSpeed", t);
        }
    }

    private static void veinMine(Level level, BlockPos origin, Player player, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag, int limit) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            BlockPos curr = queue.poll();
            BlockState state = level.getBlockState(curr);
            if (tag == null || state.is(tag)) {
                if (!curr.equals(origin)) { level.destroyBlock(curr, true, player); broken++; }
                for (BlockPos neighbor : BlockPos.betweenClosed(curr.offset(-1, -1, -1), curr.offset(1, 1, 1))) {
                    BlockPos imm = neighbor.immutable();
                    if (visited.add(imm)) queue.add(imm);
                }
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

    // 5. Mort : Phenix, Soul Bound, Plunder Wealth
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
        // FIX (mob-equipment-enchant generalization) : la renaissance Phoenix
        // se declenche desormais pour toute LivingEntity portant l'armure
        // enchantee, pas seulement pour un joueur.
        if (ENABLE_PHOENIX_REVIVE && event.getEntity() instanceof LivingEntity player && !player.level().isClientSide()) {
            for (ItemStack armor : player.getArmorSlots()) {
                int phxLvl = getEnchantLvl(armor, "deep_lucky_block:phoenix");
                if (phxLvl > 0) {
                    event.setCanceled(true);
                    player.setHealth(player.getMaxHealth());
                    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Math.max(40, Math.min(140, 40 + (phxLvl - 1) * 50)), 1));
                    player.level().explode(player, player.getX(), player.getY(), player.getZ(), 4.0f, Level.ExplosionInteraction.NONE);
                    Enchantment phxEnch = deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:phoenix");
                    if (phxEnch != null) {
                        // PORT 1.21.1 : suppression d'un enchantement via DeepEnchant
                        deepluckyblock.util.DeepEnchant.remove(armor, phxEnch);
                    }
                    return;
                }
            }
            // FIX (mob-equipment-enchant generalization) : Soul Bound sauvegarde
            // le CONTENU DE L'INVENTAIRE (getInventory), qui n'existe que sur
            // Player -- ce volet reste donc strictement reserve au joueur.
            if (player instanceof Player sbPlayer) {
                List<ItemStack> saved = new ArrayList<>();
                for (int i = 0; i < sbPlayer.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sbPlayer.getInventory().getItem(i);
                    if (getEnchantLvl(stack, "deep_lucky_block:soul_bound") > 0) { saved.add(stack.copy()); sbPlayer.getInventory().setItem(i, ItemStack.EMPTY); }
                }
                if (!saved.isEmpty()) SOUL_BOUND_CACHE.put(sbPlayer.getUUID(), saved);
            }
        }
        // FIX (mob-equipment-enchant generalization) : les enchantements de
        // butin (plunder_wealth, soul_reaping_sickle, loot_harvest) profitent
        // desormais a tout tueur LivingEntity equipe, pas seulement au joueur.
        if (event.getSource().getEntity() instanceof LivingEntity player && !player.level().isClientSide()) {
            ItemStack weapon = player.getMainHandItem();
            int plunderLvl = getEnchantLvl(weapon, "deep_lucky_block:plunder_wealth");
            if (plunderLvl > 0 && (event.getEntity() instanceof Villager || event.getEntity() instanceof Pillager)) {
                event.getEntity().spawnAtLocation(new ItemStack(Items.EMERALD, (2 + player.level().random.nextInt(3)) * plunderLvl));
            }
            int soulReap = getEnchantLvl(weapon, "deep_lucky_block:soul_reaping_sickle");
            if (soulReap > 0 && player.level().random.nextInt(100) < soulReap) {
                if (event.getEntity() instanceof net.minecraft.world.entity.monster.Zombie) event.getEntity().spawnAtLocation(Items.ZOMBIE_SPAWN_EGG);
                else if (event.getEntity() instanceof net.minecraft.world.entity.monster.Skeleton) event.getEntity().spawnAtLocation(Items.SKELETON_SPAWN_EGG);
                else if (event.getEntity() instanceof net.minecraft.world.entity.monster.Creeper) event.getEntity().spawnAtLocation(Items.CREEPER_SPAWN_EGG);
            }
            int lootHarv = getEnchantLvl(weapon, "deep_lucky_block:loot_harvest");
            if (lootHarv > 0) event.getEntity().spawnAtLocation(new ItemStack(Items.EMERALD, lootHarv));
            // Experience Harvest est traité dans LivingExperienceDropEvent à
            // priorité LOWEST afin de multiplier l'XP déjà modifiée par les autres mods.
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

    // 8. Buoy (FIX mob-equipment-enchant generalization : passe de
    // PlayerTickEvent.Post a EntityTickEvent.Post pour couvrir les mobs equipes)
    @SubscribeEvent
    public static void onPlayerWaterBuoy(EntityTickEvent.Post event){try{onPlayerWaterBuoyImpl(event);}catch(Throwable t__){guard("onPlayerWaterBuoy",t__);}}
    private static void onPlayerWaterBuoyImpl(EntityTickEvent.Post event){
        try {
            if (event.getEntity() instanceof LivingEntity player && !player.level().isClientSide()) {
                int buoyLvl = getEnchantLvl(player.getItemBySlot(EquipmentSlot.FEET), "deep_lucky_block:buoy");
                if (buoyLvl > 0 && player.isInWater() && player.getDeltaMovement().y > 0.0) player.setDeltaMovement(player.getDeltaMovement().add(0, 0.25f, 0));
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
            // FIX (mob-equipment-enchant generalization) : cet evenement est
            // deja generique (LivingEntity), la restriction "instanceof
            // Player" etait artificielle -- retiree pour couvrir les mobs
            // utilisant un arc/objet "toy" enchante.
            if (event.getEntity() instanceof LivingEntity) {
                ItemStack stack = event.getItem();
                if (stack.isEmpty()) return;
                int toyLvl = getEnchantLvl(stack, "deep_lucky_block:toy");
                if (toyLvl > 0) event.setDuration(3);
                // instant_shot ne modifie plus le Start : la flèche ne part JAMAIS
                // tant que la corde n'est pas relâchée (fini la mitraillette).
            }
        } catch (Throwable t) {
            printHandlerError("onItemUseStart", t);
        }
    }

    @SubscribeEvent
    public static void onItemUseTick(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick event){try{onItemUseTickImpl(event);}catch(Throwable t__){guard("onItemUseTick",t__);}}
    private static void onItemUseTickImpl(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick event){
        try {
            // FIX (mob-equipment-enchant generalization) : evenement deja
            // generique -- generalise a LivingEntity pour couvrir un mob
            // qui utilise un arc/objet enchante (toy, ranger, quick_charge,
            // instant_shot, fast_eating).
            if (!(event.getEntity() instanceof LivingEntity player)) return;
            ItemStack stack = event.getItem();
            if (stack.isEmpty()) return;
            int toyLvl = getEnchantLvl(stack, "deep_lucky_block:toy");
            if (toyLvl > 0) {
                if (event.getDuration() <= 1) { fireToyArrow(player, toyLvl); event.setDuration(4); }
                return;
            }
            // instant_shot : armement TRES rapide, mais AUCUN tir automatique.
            // La flèche ne part qu'au relâchement de la corde (ArrowLoose).
            int rangerLvl = getEnchantLvl(stack, "deep_lucky_block:ranger");
            int quickLvl = getEnchantLvl(stack, "minecraft:quick_charge");
            int instantLvl = getEnchantLvl(stack, "deep_lucky_block:instant_shot");
            if (rangerLvl > 0 || quickLvl > 0 || instantLvl > 0) {
                int reduction = (rangerLvl * 2) + (quickLvl * 3) + (instantLvl * 6);
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
        // FIX DÉFINITIF (400x+ "Cannot encode empty ItemStack" en boucle au unload de
        // chunk, cause racine du bug "l'arc ne tire plus / plus d'animation" rapporté
        // en jeu) : le constructeur Arrow(level, shooter, pickupItemStack,
        // firedFromWeapon) stocke la REFERENCE exacte de l'ItemStack passé comme
        // pickupItemStack de l'entité flèche — CE N'EST PAS UNE "FLÈCHE REPRÉSENTÉE PAR
        // L'ARC", c'est juste l'item que le joueur récupérerait au sol. Passer une copie
        // de l'ARC LUI-MÊME (player.getMainHandItem().copy()) était déjà une erreur
        // sémantique (l'entité flèche traîne alors une copie figée d'un arc enchanté
        // potentiellement massif en NBT), et une simple .copy() ne suffit pas : la
        // flèche peut rester en vol/au sol largement plus longtemps que l'arc ne reste
        // inchangé (unbreaking/mending qui retouchent le stack, remplacement d'item...),
        // et TOUT ItemStack vide déclenche IllegalStateException "Cannot encode empty
        // ItemStack" dans AbstractArrow.addAdditionalSaveData dès la sauvegarde du chunk.
        // Avec des centaines de flèches "toy" tirées par seconde, ce spam d'exceptions
        // serveur (voir logs : 400+ occurrences en quelques secondes) sature le thread
        // serveur et donne l'impression que "l'arc ne répond plus du tout". On utilise
        // désormais une VRAIE pile d'UNE flèche vanilla, indépendante de l'arc, qui ne
        // sera jamais vidée par un autre système.
        Arrow arrow = new Arrow(player.level(), player, new ItemStack(Items.ARROW), null);
        arrow.setBaseDamage(player.getRandom().nextFloat() < 0.10f ? 0.05f : 0.0f);
        // [1.21.1] setKnockback supprimé : knockback piloté par l'enchantement PUNCH
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
        ItemStack bow = event.getBow();
        // PHASING : la flèche n'a plus de gravité, file tout droit lentement
        // (vitesse normale -45%) et traverse tout.
        // La vitesse est modifiée sur la flèche elle-même dans EntityJoinLevel.
        if (getEnchantLvl(bow, "deep_lucky_block:phasing") > 0) {
            event.getEntity().getPersistentData().putBoolean("slb_phasing_arrow", true);
        }
        // SNIPER : portée maximale de l'arc doublée (vitesse x2 sur la flèche).
        if (getEnchantLvl(bow, "deep_lucky_block:sniper") > 0) {
            event.getEntity().getPersistentData().putBoolean("slb_arrow_sniper", true);
        }
        // INSTANT SHOT : la flèche ne part qu'ici, au RELÂCHEMENT de la corde,
        // mais toujours à pleine puissance, même avec un clic rapide.
        if (getEnchantLvl(bow, "deep_lucky_block:instant_shot") > 0) {
            event.setCharge(Math.max(event.getCharge(), 20));
        }
        // INVERTED ARROWS : un seul tirage par salve. Si la malédiction se
        // déclenche, TOUTES les flèches de cette salve partent vers l'arrière ;
        // aucune flèche résiduelle ne continue vers l'avant.
        int invertedLvl = getEnchantLvl(bow, "deep_lucky_block:inverted_arrows");
        boolean invertThisShot = invertedLvl > 0
                && event.getEntity().level().random.nextInt(100) < Math.min(100, 20 * invertedLvl);

        // STEADY SHOT (niveau max I) : la vraie flèche ne subit plus la gravité,
        // donc aucune courbe ni retombée. Son axe initial reste inchangé.
        boolean steadyThisShot = getEnchantLvl(bow, "deep_lucky_block:steady_shot") > 0;
        if (steadyThisShot) {
            event.getEntity().getPersistentData().putBoolean("slb_arrow_steady", true);
        }

        int multiLvl = getEnchantLvl(bow, "deep_lucky_block:multishot_bow");
        int scatterLvl = getEnchantLvl(bow, "deep_lucky_block:scatter");
        int count = Math.max(multiLvl > 0 ? 2 * multiLvl : 0, scatterLvl > 0 ? 4 : 0);
        if (count > 0 && !event.getEntity().level().isClientSide()) {
            for (int i = 0; i < count; i++) {
                // FIX DÉFINITIF (voir fireToyArrow) : une VRAIE pile de flèche vanilla
                // indépendante de l'arc, jamais une copie de l'arc lui-même (source du
                // spam "Cannot encode empty ItemStack" en jeu, cause racine du bug de
                // l'arc qui semblait ne plus répondre).
                Arrow extraArrow = new Arrow(event.getEntity().level(), event.getEntity(), new ItemStack(Items.ARROW), null);
                extraArrow.shootFromRotation(event.getEntity(), event.getEntity().getXRot(), event.getEntity().getYRot() + (i % 2 == 0 ? 6f : -6f) * ((i / 2) + 1), 0.0f, 3.0f, 1.0f);
                if (invertThisShot) {
                    extraArrow.setDeltaMovement(extraArrow.getDeltaMovement().scale(-1.0));
                }
                if (steadyThisShot) {
                    extraArrow.setNoGravity(true);
                }
                extraArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                // Les flèches bonus ne consomment pas le marqueur PHASING :
                // seule la vraie flèche tirée devient intangible.
                extraArrow.getPersistentData().putBoolean("slb_no_phasing", true);
                event.getEntity().level().addFreshEntity(extraArrow);
            }
        }
        int snapLvl = getEnchantLvl(bow, "deep_lucky_block:string_snap");
        if (snapLvl > 0 && event.getEntity().level().random.nextInt(100) < (20 * snapLvl)) if (event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel slb) bow.hurtAndBreak(Math.max(1, (int)(bow.getMaxDamage() * 0.30f)), slb, event.getEntity(), item -> {});
        if (getEnchantLvl(bow, "deep_lucky_block:point_shoot") > 0) event.getEntity().getPersistentData().putBoolean("slb_arrow_point_shoot", true);
        // Tags consommés par la vraie flèche dans EntityJoinLevel.
        int weakForceLvl = getEnchantLvl(bow, "deep_lucky_block:weak_force");
        if (weakForceLvl > 0) {
            event.getEntity().getPersistentData().putInt("slb_arrow_weak", weakForceLvl);
        }
        if (invertThisShot) {
            event.getEntity().getPersistentData().putBoolean("slb_arrow_inverted_shot", true);
        }
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

    // 10b. PHASING : appliqué à la flèche dès son apparition dans le monde.
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event){try{onEntityJoinLevelImpl(event);}catch(Throwable t__){guard("onEntityJoinLevel",t__);}}
    private static void onEntityJoinLevelImpl(EntityJoinLevelEvent event){
        try {
            handleEntityJoinLevel(event);
        } catch (Throwable t) {
            printHandlerError("onEntityJoinLevel", t);
        }
    }

    // FIX (mob-equipment-enchant generalization) : "instanceof Player" ->
    // "instanceof LivingEntity" pour Phasing/Sniper/Weak Force/Inverted
    // Arrows/Steady Shot/Point Shoot -- un mob archer (squelette, etc.)
    // tirant une fleche enchantee beneficie desormais de ces effets au
    // meme titre qu'un joueur. Tous les champs lus/ecrits ici
    // (getPersistentData, getLookAngle) sont deja disponibles sur
    // n'importe quelle LivingEntity.
    private static void handleEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity player)) return;
        if (arrow.getPersistentData().getBoolean("slb_no_phasing")) return;

        if (player.getPersistentData().getBoolean("slb_phasing_arrow")) {
            player.getPersistentData().remove("slb_phasing_arrow");
            arrow.getPersistentData().putBoolean("slb_phasing", true);
            arrow.setNoGravity(true); // plus de gravité : file tout droit, lentement
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(0.55f)); // vitesse -45 %
            // Elle vole "à l'infini"... avec une limite de sécurité de 30 s pour
            // ne pas laisser des entités orphelines dans le monde.
            if (event.getLevel() instanceof ServerLevel sl) {
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 600, () -> {
                    if (arrow.isAlive()) arrow.discard();
                });
            }
        }

        // SNIPER : portée maximale doublée (vitesse x2 sur la vraie flèche).
        if (player.getPersistentData().getBoolean("slb_arrow_sniper")) {
            player.getPersistentData().remove("slb_arrow_sniper");
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(2.0));
        }

        // WEAK FORCE : la flèche inflige moins de dégâts (-25 % par niveau).
        int weakLvl = player.getPersistentData().getInt("slb_arrow_weak");
        if (weakLvl > 0) {
            player.getPersistentData().remove("slb_arrow_weak");
            arrow.setBaseDamage(Math.max(0.5f,
                    arrow.getBaseDamage() * Math.max(0.25f, 1.0f - 0.25f * weakLvl)));
        }

        // INVERTED ARROWS : le tirage a déjà été fait une seule fois au
        // relâchement. On inverse la vraie flèche sans créer de seconde flèche.
        boolean invertedShot = player.getPersistentData().getBoolean("slb_arrow_inverted_shot");
        if (invertedShot) {
            player.getPersistentData().remove("slb_arrow_inverted_shot");
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(-1.0));
        }

        // STEADY SHOT : conserve le vecteur courant (y compris s'il est inversé)
        // et retire la gravité. La trajectoire reste donc droite à l'infini.
        if (player.getPersistentData().getBoolean("slb_arrow_steady")) {
            player.getPersistentData().remove("slb_arrow_steady");
            double speed = arrow.getDeltaMovement().length();
            if (speed > 0.01) {
                arrow.setDeltaMovement(arrow.getDeltaMovement().normalize().scale(speed));
            }
            arrow.setNoGravity(true);
        }

        // POINT SHOOT : trajectoire parfaitement rectiligne (zéro dispersion).
        if (player.getPersistentData().getBoolean("slb_arrow_point_shoot")) {
            player.getPersistentData().remove("slb_arrow_point_shoot");
            double speed = arrow.getDeltaMovement().length();
            if (speed > 0.01) {
                arrow.setDeltaMovement(player.getLookAngle().scale(invertedShot ? -speed : speed));
            }
        }
    }

    // FIX (mob-equipment-enchant generalization) : "instanceof Player" ->
    // "instanceof LivingEntity" -- generalise TOUS les effets d'impact de
    // fleche (Phasing, Torch, Whisper, Flint Heart, Ricochet, Enemy
    // Attractor, Heavy Range Crossbow, Peregrine, Wraith Arrow, Blazing
    // Arrow, Dragon's Breath Arrow, Sniper, etc.) a n'importe quel tireur
    // LivingEntity, dont les mobs archers. Seul le volet "Retrieval"
    // (ajoute une fleche a l'INVENTAIRE) reste explicitement Player-only
    // plus bas (un mob n'a pas d'inventaire consultable de la meme facon).
    private static void handleArrowImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity player)) return;
        if (player.level().isClientSide()) return;
        ItemStack bow = player.getMainHandItem();
        if (bow.isEmpty()) bow = player.getOffhandItem();

        // PHASING : la flèche traverse TOUT (entités et blocs), en infligeant ses
        // dégâts à chaque entité traversée, sans jamais s'arrêter.
        //
        // FIX (rendre fonctionnels les objets ayant une combinaison
        // d'enchantements incompatible, ex. Arc de l'Ascension) : cette
        // branche faisait un "return" immédiat après avoir infligé les
        // dégâts de base, ce qui empêchait TOUS les autres effets à l'impact
        // (torch, whisper, ricochet, retrieval, wraith_arrow, thunderstruck,
        // water_arrow, pierce, briar_arrow, etc.) de jamais se déclencher sur
        // une flèche qui a AUSSI Phasing -- alors que les règles de
        // compatibilité au tirage (PHASING_ALLOWED) n'autorisent normalement
        // Phasing qu'avec une petite liste d'enchants, un objet plus ancien /
        // au tirage "eternal" peut légitimement porter Phasing ET d'autres
        // enchants d'impact en même temps. On applique désormais les dégâts
        // de base de Phasing SANS interrompre le reste du traitement : tous
        // les autres enchants d'impact continuent de s'exécuter normalement
        // ensuite, y compris sur une flèche qui traverse sa cible.
        if (arrow.getPersistentData().getBoolean("slb_phasing")) {
            if (event.getRayTraceResult() instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity target) {
                hurtNoCascade(target, player.damageSources().arrow(arrow, player),
                        (float) arrow.getBaseDamage());
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 1.0f, 1.2f);
            }
            event.setCanceled(true);
            // (pas de "return" ici : voir commentaire ci-dessus)
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
            hurtNoCascade(target, player.damageSources().arrow(arrow, player), 2.0f * flintHeartLvl);
            if (bow.isDamageableItem()) if (player.level() instanceof net.minecraft.server.level.ServerLevel slr) bow.hurtAndBreak(Math.max(1, (int)(bow.getMaxDamage() * 0.02f)), slr, player, item -> {});
        }
        int ricochetLvl = Math.max(getEnchantLvl(bow, "deep_lucky_block:ricochet"), Math.max(getEnchantLvl(bow, "deep_lucky_block:bounce"), getEnchantLvl(bow, "deep_lucky_block:bouncy")));
        if (ricochetLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            int bounces = 0;
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(6.0))) {
                if (mob != target && bounces < ricochetLvl) { hurtNoCascade(mob, player.damageSources().arrow(arrow, player), 4.0f); bounces++; }
            }
        }
        if (getEnchantLvl(bow, "deep_lucky_block:enemy_attractor") > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(6.0))) if (mob != target) mob.setTarget(target);
        }
        int hvyCrossLvl = getEnchantLvl(bow, "deep_lucky_block:heavy_range_crossbow");
        if (hvyCrossLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target)
            hurtNoCascade(target, player.damageSources().arrow(arrow, player), 2.0f * hvyCrossLvl);
        // PEREGRINE : plongeon du faucon. Si la cible est EN CONTREBAS, bonus de
        // dégâts proportionnel à la hauteur de chute entre vous et elle
        // (+0,5 dégât par bloc de dénivelé et par niveau, max 20 blocs comptés).
        int peregrineLvl = getEnchantLvl(bow, "deep_lucky_block:peregrine");
        if (peregrineLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity target) {
            double drop = player.getY() - target.getY();
            if (drop > 1.0) {
                float bonus = (float) Math.min(drop, 20.0) * 0.5f * peregrineLvl;
                hurtNoCascade(target, player.damageSources().arrow(arrow, player), bonus);
            }
        }
        int wraithLvl = getEnchantLvl(bow, "deep_lucky_block:wraith_arrow");
        if (wraithLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            hurtNoCascade(target, player.damageSources().magic(), target.getMaxHealth() * 0.05f);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            if (target.getHealth() <= 0) player.heal(player.getMaxHealth() * 0.10f);
        }
        if (getEnchantLvl(bow, "deep_lucky_block:blazing_arrow") > 0) {
            for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, arrow.getBoundingBox().inflate(6.0, 3.0, 6.0))) if (le != player) le.setRemainingFireTicks((4) * 20);
        }
        // DRAGON BREATH : zone COURTE (0,5 s) + debuffs de 2 s empilables.
        // Nerf demandé : plus de nuage de 1,5-15 s, juste 2 impulsions de dégâts.
        if (getEnchantLvl(bow, "deep_lucky_block:dragons_breath_arrow") > 0) {
            double dbx = event.getRayTraceResult().getLocation().x;
            double dby = event.getRayTraceResult().getLocation().y;
            double dbz = event.getRayTraceResult().getLocation().z;
            net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(player.level(), dbx, dby, dbz);
            cloud.setParticle(ParticleTypes.DRAGON_BREATH); // violet, comme le souffle du dragon
            cloud.setRadius(4.0f);
            cloud.setDuration(10); // zone : 0,5 s seulement
            cloud.setWaitTime(0);
            player.level().addFreshEntity(cloud);
            // 2 impulsions (t=0 et t=10) : dégâts dragon breath + debuffs 2 s empilables
            ServerLevel dsl = (ServerLevel) player.level();
            final LivingEntity dPlayer = player;
            for (int t = 0; t <= 1; t++) {
                final int delay = t * 10;
                TestProcedure.schedule(dsl, TestProcedure.currentTick(dsl) + delay, () -> {
                    AABB dmgBox = new AABB(dbx - 4, dby - 2, dbz - 4, dbx + 4, dby + 4, dbz + 4);
                    for (LivingEntity le : dsl.getEntitiesOfClass(LivingEntity.class, dmgBox)) {
                        if (le != dPlayer && le.isAlive()) {
                            le.hurt(dsl.damageSources().dragonBreath(), 2.0f);
                            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0)); // 2 s
                            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));           // 2 s
                        }
                    }
                });
            }
        }
        // POISON ARROW : nuage VERT, zone 0,5 s, poison 2 s empilable (re-appliqué
        // à chaque flèche : les durées s'additionnent).
        if (getEnchantLvl(bow, "deep_lucky_block:poison_arrow") > 0) {
            net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(player.level(), event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y, event.getRayTraceResult().getLocation().z);
            cloud.setRadius(3.0f);
            cloud.setDuration(10); // zone : 0,5 s
            cloud.setWaitTime(0);
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0)); // effet : 2 s, empilable
            player.level().addFreshEntity(cloud);
        }
        // WATER ARROW : éteint le feu, blesse FORT les mobs qui détestent l'eau,
        // pose de l'eau et purifie une zone de 3x3 autour de l'impact.
        if (getEnchantLvl(bow, "deep_lucky_block:water_arrow") > 0) {
            if (event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
                target.clearFire();
                boolean waterSensitive = target instanceof net.minecraft.world.entity.monster.Blaze
                        || target instanceof net.minecraft.world.entity.monster.MagmaCube
                        || target instanceof net.minecraft.world.entity.monster.EnderMan
                        || target instanceof net.minecraft.world.entity.animal.SnowGolem
                        || target instanceof net.minecraft.world.entity.monster.Strider;
                // Inclut Enderman, Endermite, Ender Dragon et toute créature
                // moddée dont l'identité/NBT contient « end » ou « ender ».
                boolean endCreature = isEndCreatureByIdentityOrNbt(target);
                if (waterSensitive || endCreature) {
                    hurtNoCascade(target, player.damageSources().drown(), 12.0f);
                }
            }
            // Le terrain et la source ne sont traités qu'au PREMIER impact réel
            // de la flèche. Aucun effet de source sur entité, ricochet, bounce ou
            // impact ultérieur après Pierce.
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
        int hvyImpLvl = getEnchantLvl(bow, "deep_lucky_block:heavy_impact");
        if (hvyImpLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target)
            hurtNoCascade(target, player.damageSources().arrow(arrow, player), 4.0f * hvyImpLvl);
        if (getEnchantLvl(bow, "deep_lucky_block:teleport_arrow") > 0) player.teleportTo(event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y, event.getRayTraceResult().getLocation().z);
        int swapArrow = getEnchantLvl(bow, "deep_lucky_block:swap_arrow");
        if (swapArrow > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            target.teleportTo(px, py, pz);
            player.teleportTo(target.getX(), target.getY(), target.getZ());
        }
        if (getEnchantLvl(bow, "deep_lucky_block:transfer") > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player targetPlayer) {
            ItemStack off = player.getOffhandItem();
            if (!off.isEmpty()) {
                ItemStack copy = off.copy();
                off.setCount(0);
                if (!targetPlayer.getInventory().add(copy)) targetPlayer.drop(copy, false);
            }
        }
        // RETRIEVAL : ajoute une fleche a l'INVENTAIRE -- reste Player-only,
        // un mob n'a pas d'inventaire au sens Player#getInventory().
        int retLvl = getEnchantLvl(bow, "deep_lucky_block:retrieval");
        if (retLvl > 0 && player.level().random.nextInt(100) < (10 * retLvl) && player instanceof Player retPlayer) retPlayer.getInventory().add(new ItemStack(Items.ARROW));
        if (getEnchantLvl(bow, "deep_lucky_block:storm_strike") > 0) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, player.level());
            bolt.moveTo(event.getRayTraceResult().getLocation());
            player.level().addFreshEntity(bolt);
        }
        int sniperLvl = getEnchantLvl(bow, "deep_lucky_block:sniper");
        if (sniperLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            double dist = player.distanceTo(target);
            if (dist >= 15.0) hurtNoCascade(target, player.damageSources().arrow(arrow, player), (float)(dist * 0.5 * sniperLvl));
        }
        // CHAOS : un effet NÉGATIF aléatoire par flèche, zone de 0,5 s, effet de 2 s.
        // Une entité ne peut être touchée qu'UNE SEULE FOIS par le cloud (pas de stack).
        int chaosLvl = getEnchantLvl(bow, "deep_lucky_block:chaos");
        if (chaosLvl > 0) {
            net.minecraft.core.Holder<MobEffect> chaosEffect = CHAOS_EFFECTS[player.level().random.nextInt(CHAOS_EFFECTS.length)];
            double chx = event.getRayTraceResult().getLocation().x;
            double chy = event.getRayTraceResult().getLocation().y;
            double chz = event.getRayTraceResult().getLocation().z;
            net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(player.level(), chx, chy, chz);
            cloud.setParticle(ParticleTypes.WITCH); // particules violettes "chaos"
            cloud.setRadius(3.5f);
            cloud.setDuration(10); // zone : 0,5 s
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
                        if (immuneUntil > chsl.getGameTime()) continue; // déjà touché par ce cloud
                        le.getPersistentData().putLong("slb_chaos_immune", chsl.getGameTime() + 20);
                        le.addEffect(new MobEffectInstance(fChaosEffect, 40, 0)); // 2 s, non stackable
                    }
                });
            }
        }
        int delayLvl = getEnchantLvl(bow, "deep_lucky_block:delay");
        if (delayLvl > 0) player.level().explode(player, event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y, event.getRayTraceResult().getLocation().z, 2.0f * delayLvl, Level.ExplosionInteraction.NONE);
        int brimLvl = getEnchantLvl(bow, "deep_lucky_block:brimstone");
        if (brimLvl > 0) player.level().explode(player, event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y, event.getRayTraceResult().getLocation().z, 3.0f * brimLvl, true, Level.ExplosionInteraction.NONE);
        if (getEnchantLvl(bow, "deep_lucky_block:thunderstruck") > 0) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, player.level());
            bolt.moveTo(event.getRayTraceResult().getLocation());
            player.level().addFreshEntity(bolt);
        }
        int meteorLvl = getEnchantLvl(bow, "deep_lucky_block:meteor");
        if (meteorLvl > 0) player.level().explode(player, event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y, event.getRayTraceResult().getLocation().z, 4.0f, true, Level.ExplosionInteraction.NONE);
        if (getEnchantLvl(bow, "deep_lucky_block:grapple") > 0) player.teleportTo(event.getRayTraceResult().getLocation().x, event.getRayTraceResult().getLocation().y, event.getRayTraceResult().getLocation().z);
        int warpLvl = getEnchantLvl(bow, "deep_lucky_block:warp");
        if (warpLvl > 0 && event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target)
            target.teleportTo(target.getX() + player.level().random.nextInt(16) - 8, target.getY(), target.getZ() + player.level().random.nextInt(16) - 8);
        // PIERCE : la flèche traverse jusqu'à N entités OU blocs (N = niveau du
        // pierce), en blessant chaque entité traversée, puis se plante normalement.
        // Pierce I = 1 entité/bloc traversé, Pierce II = 2, etc.
        int pierceLvl = getEnchantLvl(bow, "deep_lucky_block:pierce");
        if (pierceLvl > 0) {
            CompoundTag pierceTag = arrow.getPersistentData();
            int hits = pierceTag.getInt("slb_pierce_hits");
            if (hits < pierceLvl) {
                pierceTag.putInt("slb_pierce_hits", hits + 1);
                if (event.getRayTraceResult() instanceof EntityHitResult ehr
                        && ehr.getEntity() instanceof LivingEntity target) {
                    // On applique nous-mêmes les dégâts de la flèche, puis on la
                    // laisse poursuivre sa trajectoire (l'impact est annulé).
                    hurtNoCascade(target, player.damageSources().arrow(arrow, player),
                            (float) arrow.getBaseDamage());
                    player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 1.0f, 1.2f);
                }
                // Annule l'impact : la flèche traverse l'entité OU le bloc.
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

    /**
     * Pose une source uniquement lorsqu'une Water Arrow touche directement un
     * bloc. Cette méthode n'accepte volontairement pas EntityHitResult.
     */
    private static void placeWaterArrowSource(Level level,
            net.minecraft.world.phys.BlockHitResult blockHit) {
        if (level == null || blockHit == null || level.isClientSide()) return;

        BlockPos hitBlock = blockHit.getBlockPos();
        BlockPos preferred = hitBlock.relative(blockHit.getDirection());
        BlockState preferredState = level.getBlockState(preferred);

        // Comportement historique : source dans la case libre contre la face
        // directement touchée par la flèche.
        if (preferredState.isAir() || preferredState.canBeReplaced()) {
            level.setBlockAndUpdate(preferred, Blocks.WATER.defaultBlockState());
            return;
        }

        // Si la face est enfermée, cherche uniquement autour du BLOC touché.
        // Cela reste un impact bloc et ne s'étend jamais à une entité/rebond.
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

    /** Water Arrow : croissance végétale dans une sphère de rayon 4. */
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
                    // Tous les Grass Blocks ne poussent pas à chaque flèche :
                    // la sphère reste naturelle et ne devient pas surchargée.
                    if (level.random.nextInt(100) >= 38) continue;

                    int roll = level.random.nextInt(100);
                    Block plant = roll < 68 ? Blocks.GRASS_BLOCK
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
                }

                double force = Math.max(0.6 * bashPower, 0.8 * shieldQuake);
                if (force > 0.0) {
                    // setDeltaMovement contourne la Knockback Resistance élevée
                    // des Iron Golems et garantit un recul visible à chaque coup.
                    net.minecraft.world.phys.Vec3 away = attacker.position()
                            .subtract(player.position());
                    away = new net.minecraft.world.phys.Vec3(
                            away.x, 0.0, away.z).normalize();
                    attacker.setDeltaMovement(attacker.getDeltaMovement().add(
                            away.x * force, 0.20 + 0.05 * force, away.z * force));
                    attacker.hurtMarked = true;
                }
            }

            if (hatredDraw > 0) {
                for (Mob mob : player.level().getEntitiesOfClass(
                        Mob.class, player.getBoundingBox().inflate(10.0))) {
                    mob.setTarget(player);
                }
            }
        } catch (Throwable t) {
            printHandlerError("onShieldAttackAttempt", t);
        }
    }

    // LivingShieldBlockEvent reste utilisé uniquement pour Shield Efficiency, afin de
    // supprimer l'usure sur chaque blocage réellement validé par Minecraft.
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event){try{onShieldBlockImpl(event);}catch(Throwable t__){guard("onShieldBlock",t__);}}
    private static void onShieldBlockImpl(LivingShieldBlockEvent event){
        try {
            // FIX (mob-equipment-enchant generalization) : generalise a LivingEntity.
            if (!(event.getEntity() instanceof LivingEntity player)) return;
            if (player.level().isClientSide()) return;
            ItemStack shield = findActiveEnchantedShield(player);
            if (!shield.isEmpty()
                    && getEnchantLvl(shield, "deep_lucky_block:shield_efficiency") > 0) {
                event.setShieldDamage(0.0f); // [1.21.1] setShieldTakesDamage supprime : degats bouclier mis a 0
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

    // FIX (mob-equipment-enchant generalization) : "instanceof Player" ->
    // "instanceof LivingEntity" -- un mob equipe d'une armure enchantee
    // (Bouncy, Sticky, Veil, Block, Powerful Thorns, etc.) beneficie
    // desormais des memes effets defensifs qu'un joueur.
    private static void handleDefensiveHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity player)) return;
        if (player.level().isClientSide()) return;

        int bouncyLvl = getArmorEnchantLvl(player, "deep_lucky_block:bouncy");
        if (bouncyLvl > 0 && event.getSource().getMsgId().equals("fall")) {
            event.setCanceled(true);
            player.setDeltaMovement(player.getDeltaMovement().x, 0.4 * bouncyLvl, player.getDeltaMovement().z);
            player.hurtMarked = true;
            return;
        }
        int stickyLvl = getArmorEnchantLvl(player, "deep_lucky_block:sticky");
        if (stickyLvl > 0) player.setDeltaMovement(player.getDeltaMovement().scale(0.1));
        int undeadCmd = getArmorEnchantLvl(player, "deep_lucky_block:undead_commander");
        int illagerCmd = getArmorEnchantLvl(player, "deep_lucky_block:illager_commander");
        int veilLvl = getArmorEnchantLvl(player, "deep_lucky_block:veil");
        if ((undeadCmd > 0 && (event.getSource().getEntity() instanceof net.minecraft.world.entity.monster.Zombie || event.getSource().getEntity() instanceof net.minecraft.world.entity.monster.Skeleton))
                || (illagerCmd > 0 && event.getSource().getEntity() instanceof net.minecraft.world.entity.monster.Pillager)
                || (veilLvl > 0)) {
            event.setAmount(event.getAmount() * 0.5f);
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            int wardenLvl = getArmorEnchantLvl(player, "deep_lucky_block:wardenspine");
            if (wardenLvl > 0) hurtNoCascade(attacker, player.damageSources().thorns(player), 2.0f * wardenLvl);
            int punchLvl = getArmorEnchantLvl(player, "deep_lucky_block:punching_armor");
            if (punchLvl > 0) attacker.knockback(0.5f * punchLvl, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
            int fearLvl = getArmorEnchantLvl(player, "deep_lucky_block:inner_fear");
            if (fearLvl > 0) {
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, fearLvl - 1));
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, fearLvl - 1));
            }

            // ZEPHYR'S GAMBIT (Wind Heart, plastron, FUN) : si le coup
            // encaisse etait un critique (meme formule vanilla que le crit
            // du joueur, cf. handleOffensiveHurt), chance de se teleporter
            // sur une courte distance et d'annuler une partie des degats --
            // "insaisissable comme le vent", en accord avec l'esprit fun de
            // la demande.
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
        // FIX DOUBLE-COMPTAGE (rapporté en jeu) : stomach_pouch est désormais
        // converti en vrais points d'ARMOR vanilla dans
        // RealAttributeModifierHandler -- la réduction de dégâts est donc
        // déjà appliquée AUTOMATIQUEMENT par le moteur vanilla via la
        // formule d'armure, avant même que ce handler ne s'exécute.
        // Multiplier ENCORE event.getAmount() ici par le même pourcentage
        // réduisait les dégâts DEUX FOIS (une fois par l'armure réelle, une
        // fois par ce code) -- exactement le bug de stacking déjà corrigé
        // pour l'attaque, mais qui subsistait côté défense.
        int oceanBless = getArmorEnchantLvl(player, "deep_lucky_block:oceans_blessing");
        if (oceanBless > 0 && player.isInWater()) event.setAmount(event.getAmount() * 0.60f);

        // SHADOW HEART (volet defensif, cahier des charges original) :
        // chance de brief invisibilite quand touche -- complete le
        // vol de vie/affaiblissement offensif gere dans handleOffensiveHurt.
        int shadowHeartDef = getElementalHeartLvl(player, "shadow_heart");
        if (shadowHeartDef > 0 && player.level().random.nextInt(100) < (8 * shadowHeartDef)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, true));
        }

        // WIND HEART (cahier des charges original) : deviation partielle des
        // PROJECTILES, +15% de reduction de degats par coeur (en plus de la
        // chute reduite + saut ameliore de Gale Step, geres en tick). Le
        // "vent porteur" devie une partie de la trajectoire/force d'impact
        // d'une fleche/trident qui touche le joueur.
        int windHeartLvl = getElementalHeartLvl(player, "wind_heart");
        if (windHeartLvl > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            event.setAmount(event.getAmount() * Math.max(0f, 1f - 0.15f * windHeartLvl));
            if (player.level() instanceof ServerLevel windLevel) {
                windLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                        10, 0.3, 0.3, 0.3, 0.03);
            }
        }

        // EARTH HEART (cahier des charges original, "combo equilibree" :
        // reduction de degats + chance de blocage + vitesse de minage -- le
        // volet minage est gere en tick, voir updateHeartEnchantmentPassives).
        // Reduction de degats plate modeste (l'armure "roche" absorbe une
        // partie du choc) + une CHANCE distincte d'annuler completement le
        // coup ("bouclier de pierre" ponctuel), les deux scalant avec le
        // niveau du coeur.
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
        // FIX DOUBLE-COMPTAGE : divine_protection est désormais converti en
        // vrais points d'ARMOR vanilla PAR PIÈCE dans RealAttributeModifierHandler
        // (déjà appliqués automatiquement par la formule d'armure vanilla).
        // Le multiplicateur manuel ci-dessous double la réduction réelle.
        int twentyFourth = getArmorEnchantLvl(player, "deep_lucky_block:twenty_fourth_blessing");
        if (twentyFourth > 0 && event.getSource().getMsgId().equals("fall")) event.setCanceled(true);
        int crashLand = getArmorEnchantLvl(player, "deep_lucky_block:crash_landing");
        if (crashLand > 0 && event.getSource().getMsgId().equals("fall")) event.setAmount(event.getAmount() * 2.4f);
        // lightness & basic_lightness : reduisent les degats de chute. lightness = plus fort.
        if (event.getSource().getMsgId().equals("fall")) {
            int lightFall = getArmorEnchantLvl(player, "deep_lucky_block:lightness");
            int basicLightFall = getArmorEnchantLvl(player, "deep_lucky_block:basic_lightness");
            if (lightFall > 0) event.setAmount(event.getAmount() * Math.max(0.0f, 1f - 0.30f * lightFall));
            else if (basicLightFall > 0) event.setAmount(event.getAmount() * Math.max(0.1f, 1f - 0.15f * basicLightFall));
        }
        int chestTight = getArmorEnchantLvl(player, "deep_lucky_block:chest_tightness");
        if (chestTight > 0 && player.level().random.nextInt(100) < 40) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
        int frac = getArmorEnchantLvl(player, "deep_lucky_block:fracture");
        if (frac > 0 && event.getSource().getMsgId().equals("fall")) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1600, 2));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        }
        int armorDmg = getArmorEnchantLvl(player, "deep_lucky_block:armor_damage");
        int armorShat = getArmorEnchantLvl(player, "deep_lucky_block:armor_shatter");
        if (armorDmg > 0 && player.level().random.nextInt(100) < 30 * armorDmg) {
            for (ItemStack s : player.getArmorSlots()) if (!s.isEmpty() && getEnchantLvl(s, "deep_lucky_block:armor_damage") > 0) if (player.level() instanceof net.minecraft.server.level.ServerLevel sla) s.hurtAndBreak(Math.max(1, (int)(s.getMaxDamage() * 0.05f)), sla, player, item -> {});
        }
        if (armorShat > 0) {
            for (ItemStack s : player.getArmorSlots()) if (!s.isEmpty() && getEnchantLvl(s, "deep_lucky_block:armor_shatter") > 0) if (player.level() instanceof net.minecraft.server.level.ServerLevel sls) s.hurtAndBreak(Math.max(1, (int)(s.getMaxDamage() * 0.05f * armorShat)), sls, player, item -> {});
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
        int cloudWalk = getArmorEnchantLvl(player, "deep_lucky_block:cloud_walker");
        if (cloudWalk > 0 && event.getSource().getMsgId().equals("fall")) { event.setCanceled(true); return; }
        int resBurst = getArmorEnchantLvl(player, "deep_lucky_block:resistance_burst");
        if (resBurst > 0 && player.level().random.nextInt(100) < (10 * resBurst)) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false));
        int painDev = getArmorEnchantLvl(player, "deep_lucky_block:pain_devour");
        // La faim n'existe que pour un joueur ; un mob generique n'a pas
        // de FoodData, on saute simplement ce bonus pour lui.
        if (painDev > 0 && player instanceof Player painDevPlayer) painDevPlayer.getFoodData().setFoodLevel(Math.min(20, painDevPlayer.getFoodData().getFoodLevel() + 2 * painDev));
        int blockChance = Math.max(getArmorEnchantLvl(player, "deep_lucky_block:block"), Math.max(getArmorEnchantLvl(player, "deep_lucky_block:big_stomach"), getArmorEnchantLvl(player, "deep_lucky_block:life_block")));
        if (blockChance > 0 && player.level().random.nextInt(100) < Math.min(90, 30 + 15 * blockChance)) { event.setCanceled(true); return; }
        int pwrThorns = getArmorEnchantLvl(player, "deep_lucky_block:powerful_thorns");
        if (pwrThorns > 0 && event.getSource().getEntity() instanceof LivingEntity attacker)
            // FIX SCALING NIVEAU FORCE : (1 << (pwrThorns - 1)) est un decalage
            // de BIT ENTIER. Le decalage d'un int en Java ignore silencieusement
            // tous les bits au-dela de 5 (shift mod 32) : a partir d'un niveau
            // effectif de 32, "1 << 31" devient NEGATIF (Integer.MIN_VALUE) et
            // "1 << 32" revient a 1 -- le multiplicateur s'ecroule ou devient
            // negatif au lieu de continuer a grandir, ce qui pouvait annuler
            // ou inverser les degats de reflet a haut niveau force (ex. 1000).
            // Remplace par un calcul en double, plafonne a un multiplicateur
            // enorme (2^30) largement suffisant pour rester "sane" sans jamais
            // deborder/repasser negatif, quel que soit le niveau force.
            hurtNoCascade(attacker, player.damageSources().thorns(player),
                    (float) (event.getAmount() * Math.min(1_073_741_824.0, Math.pow(2, Math.max(0, pwrThorns - 1)))));
        int soulRep = getArmorEnchantLvl(player, "deep_lucky_block:soul_repulsion_blast");
        if (soulRep > 0) {
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(2.0))) {
                hurtNoCascade(mob, player.damageSources().magic(), event.getAmount() * 0.5f);
                mob.knockback(0.5, player.getX() - mob.getX(), player.getZ() - mob.getZ());
            }
        }
        int ironLung = getArmorEnchantLvl(player, "deep_lucky_block:iron_lung");
        if (ironLung > 0 && event.getSource().getMsgId().equals("inWall")) event.setCanceled(true);
        int lastStand = getArmorEnchantLvl(player, "deep_lucky_block:last_stand_toughness");
        if (lastStand > 0 && player.getHealth() < (player.getMaxHealth() * 0.20f)) event.setAmount(event.getAmount() * (1f - 0.15f * lastStand));
        // FIX DOUBLE-COMPTAGE : wound_crack/crippled sont désormais convertis
        // en points d'ARMOR NÉGATIFS vanilla dans RealAttributeModifierHandler
        // (déjà appliqués automatiquement, réduction d'armure donc plus de
        // dégâts encaissés via la formule vanilla). Le multiplicateur manuel
        // ci-dessous aggravait ENCORE la pénalité en plus de l'armure déjà
        // réduite.
        int blazeSanc = getArmorEnchantLvl(player, "deep_lucky_block:blaze_sanctuary");
        if (blazeSanc > 0 && (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE) || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE))) event.setAmount(event.getAmount() * 0.2f);
        // fire_protection_enhancement : reduit feu/lave
        int fireProtEnh = getArmorEnchantLvl(player, "deep_lucky_block:fire_protection_enhancement");
        if (fireProtEnh > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) event.setAmount(event.getAmount() * Math.max(0.10f, 1f - 0.12f * fireProtEnh));
        // projectile_protection_enhancement : reduit projectiles
        int projProtEnh = getArmorEnchantLvl(player, "deep_lucky_block:projectile_protection_enhancement");
        if (projProtEnh > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) event.setAmount(event.getAmount() * Math.max(0.10f, 1f - 0.12f * projProtEnh));
        // infernal_armor (RELIC) : feu/lave reduits de 90% + eteint le porteur
        int infernal = getArmorEnchantLvl(player, "deep_lucky_block:infernal_armor");
        if (infernal > 0 && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) { event.setAmount(event.getAmount() * 0.1f); player.clearFire(); }

        // SALAMANDER'S VOW (Fire Heart, casque) : les degats de feu sont
        // ANNULES et convertis en regeneration -- "corps de salamandre" qui
        // se nourrit des flammes -- mais desactive au contact de l'eau
        // (RiptideCurse fait l'inverse cote Water Heart), faiblesse
        // classique feu/eau assumee.
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

        // RIPTIDE CURSE (Water Heart, jambieres, MAUDIT) : degats accrus des
        // que le joueur n'est PAS dans l'eau -- "poisson hors de l'eau"
        // pousse a l'extreme, contrebalance par une nage/respiration
        // exceptionnelles geres en tick (voir updateHeartEnchantmentPassives).
        int riptideCurse = getArmorEnchantLvl(player, "deep_lucky_block:riptide_curse");
        if (riptideCurse > 0 && !player.isInWaterOrRain()) {
            event.setAmount(event.getAmount() * (1f + 0.20f * riptideCurse));
        }
        int altHyp = getArmorEnchantLvl(player, "deep_lucky_block:high_altitude_hypoxia");
        int deepHyp = getArmorEnchantLvl(player, "deep_lucky_block:deep_hypoxia");
        if (altHyp > 0 && player.getY() > 100) event.setAmount(event.getAmount() * 0.7f);
        if (deepHyp > 0 && player.getY() < 20) event.setAmount(event.getAmount() * 0.7f);

        // RADIANT WARD (Light Heart) : chance d'annuler completement le coup
        // (flash "monarque de lumiere") + purge lente des effets negatifs.
        // Positif pur, sans contrepartie -- c'est le cote "sacre" de la
        // lumiere face au cote "pacte risque" des tenebres plus bas.
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

        // UMBRAL PACT (Shadow Heart) : +dégâts encaissés reduits dans le noir
        // mais VULNERABILITE ACCRUE en plein jour, et draine periodiquement
        // un demi-coeur pour offrir un burst d'invisibilite -- vrai pacte a
        // double tranchant façon "malédiction volontaire" de manhwa. L'aura
        // trouble/malsaine correspondante est rendue cote client (voir
        // ElementalHeartOverlay, aura desaturee/vacillante).
        int umbralPact = getArmorEnchantLvl(player, "deep_lucky_block:umbral_pact");
        if (umbralPact > 0) {
            boolean daylight = player.level().isDay() && player.level().canSeeSky(player.blockPosition());
            if (daylight) {
                event.setAmount(event.getAmount() * (1f + 0.12f * umbralPact));
            } else {
                event.setAmount(event.getAmount() * Math.max(0.4f, 1f - 0.15f * umbralPact));
            }
            // Le drain periodique de vie -> burst d'invisibilite est gere en
            // tick regulier (EquipmentExpansionHandler#playerTick), pas ici :
            // ce bloc ne s'execute que lorsque le joueur ENCAISSE un coup, ce
            // qui n'est pas le comportement "periodique independant" voulu.
        }
    }

    /** Attribute bonus stacks naturally with vanilla Speed potion amplifiers. */
    // PORT 1.21.1 : id d'attribut en ResourceLocation (plus de UUID + nom)
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

    /**
     * FIX SCALING "PETIT A PETIT" : niveau effectif d'un enchantement.
     * - Jusqu'au niveau max officiel (getMaxLevel) : le niveau est lu tel quel.
     *   Un enchantement cappe a 1 reste donc TOUJOURS niveau 1 en jeu normal.
     * - Au-dela (niveau FORCE par commande / lucky block / NBT) : la puissance
     *   continue d'augmenter mais au ralenti, rendement decroissant :
     *   +1 niveau effectif par palier de racine carree du depassement.
     *   Exemple pour un cap de 1 : force 2-4 -> 2, force 5-9 -> 3,
     *   force 10-16 -> 4, force 100 -> 10.
     */
    private static int softCappedLevel(Enchantment ench, int storedLevel) {
        // Centralise dans DeepEnchant pour que EquipmentExpansionHandler et
        // FishingTridentElytraHandler beneficient de la meme regle.
        return deepluckyblock.util.DeepEnchant.softCappedLevel(ench, storedLevel);
    }

    private static int getEnchantLvl(ItemStack stack, String id) {
        if (stack.isEmpty() || !stack.isEnchanted()) return 0;
        // PORT 1.21.1 : lecture via DeepEnchant (composant ItemEnchantments)
        Enchantment en = deepluckyblock.util.DeepEnchant.byId(id);
        if (en == null) return 0;
        int lvl = deepluckyblock.util.DeepEnchant.getLevel(stack, en);
        if (lvl <= 0) return 0;
        // Le cap officiel est respecte ; un niveau force au-dela du cap
        // scale "petit a petit" (voir softCappedLevel).
        return softCappedLevel(en, lvl);
    }

    private static int getArmorEnchantLvl(LivingEntity entity, String id) {
        int total = 0;
        for (ItemStack armor : entity.getArmorSlots()) total = Math.max(total, getEnchantLvl(armor, id));
        return total;
    }

    /**
     * Niveau EFFECTIF d'un cœur ÉLÉMENTAIRE (les 7 cœurs à condition de
     * régénération : fire/water/earth/wind/electric/shadow/light_heart)
     * pour les POUVOIRS ACTIFS -- càd le niveau brut gravé sur le plastron,
     * DIMINUÉ du nombre de cœurs actuellement "perdus" par des dégâts réels
     * (voir {@link deepluckyblock.util.ElementalHeartLoss}).
     *
     * <p>Correctif (audit demande utilisateur #3, "les effets doivent
     * disparaître en perdant des cœurs et ne réapparaître qu'à la regen") :
     * TOUT pouvoir actif des 7 cœurs élémentaires doit passer par cette
     * méthode plutôt que par {@link #getArmorEnchantLvl} directement, sans
     * quoi le pouvoir resterait à pleine puissance même cœur(s) perdu(s)
     * (seul le bonus de PV plat était réduit avant ce correctif). N'utiliser
     * QUE pour les 7 IDs listés dans
     * {@code deepluckyblock.util.ElementalHeartConditions.ELEMENT_IDS} --
     * les autres enchantements "cœur" (burning_heart, thirsty_heart,
     * conduit_heart, bastion_heart, blaze_repulsion_heart, flint_heart,
     * heart_resonance...) ne font PAS partie du système de perte/regen et
     * continuent d'utiliser {@link #getArmorEnchantLvl} tel quel.
     */
    private static int getElementalHeartLvl(LivingEntity player, String elementId) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int raw = getEnchantLvl(chest, "deep_lucky_block:" + elementId);
        if (raw <= 0) return 0;
        return deepluckyblock.util.ElementalHeartLoss.effectiveLevel(chest, elementId, raw);
    }

    // Ore Detector & Summon Army
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
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -8, -8), c.offset(8, 8, 8))) if (player.level().getBlockState(p).is(Tags.Blocks.ORES)) count++;
            player.sendSystemMessage(Component.literal("§e§l[Ore Detector] §fMinerais dans 16 blocs : §6" + count + (hasStar ? " (Position !)" : "")));
        }
        int armyLvl = Math.max(getEnchantLvl(tool, "deep_lucky_block:army_summon"), getEnchantLvl(tool, "deep_lucky_block:summon_army"));
        if (armyLvl > 0 && (player.totalExperience >= 15 || player.isCreative())) {
            if (!player.isCreative()) player.giveExperiencePoints(-30);
            player.getCooldowns().addCooldown(tool.getItem(), 420);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
            int count = armyLvl * 2;
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            boolean isCmd = getEnchantLvl(helmet, "deep_lucky_block:commander") > 0;
            boolean isIllager = getEnchantLvl(helmet, "deep_lucky_block:illager_commander") > 0;
            boolean isUndead = getEnchantLvl(helmet, "deep_lucky_block:undead_commander") > 0;
            for (int i = 0; i < count; i++) {
                EntityType<? extends Mob> type = EntityType.WOLF;
                if (isCmd) type = (player.level().random.nextFloat() < 0.6f) ? EntityType.WOLF : EntityType.IRON_GOLEM;
                else if (isIllager) {
                    float r = player.level().random.nextFloat();
                    type = (r < 0.7f) ? EntityType.PILLAGER : (r < 0.9f) ? EntityType.VINDICATOR : EntityType.EVOKER;
                } else if (isUndead) {
                    type = (player.level().random.nextFloat() < 0.4f) ? EntityType.HUSK : EntityType.WITHER_SKELETON;
                }
                Mob mob = type.create(player.level());
                if (mob != null) {
                    mob.setPos(player.getX() + player.level().random.nextInt(5) - 2, player.getY(), player.getZ() + player.level().random.nextInt(5) - 2);
                    if (type == EntityType.PILLAGER) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                    else if (type == EntityType.VINDICATOR) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    else if (type == EntityType.WITHER_SKELETON) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                    player.level().addFreshEntity(mob);
                }
            }
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
