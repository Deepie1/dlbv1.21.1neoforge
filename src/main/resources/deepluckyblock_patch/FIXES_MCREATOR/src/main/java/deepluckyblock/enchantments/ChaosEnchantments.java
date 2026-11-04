package deepluckyblock.enchantments;

import deepluckyblock.util.DeepEnchant;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;

import deepluckyblock.DeepLuckyBlockMod;

/**
 * T352-CHAOS-ELEMENT : les <b>4 nouveaux enchantements Chaos</b>, sombres et multicolores.
 *
 * <p>Le chaos est une famille à part entière (voir {@link DeepElement#CHAOS}) : base noire violacée,
 * animations qui font défiler la teinte. Ces enchantements reprennent cette identité côté gameplay —
 * effets imprévisibles, jamais deux fois la même couleur.</p>
 *
 * <ul>
 *   <li>{@code chaos_entropy} — <b>Entropie</b> : chance d'appliquer un malus aléatoire au contact,</li>
 *   <li>{@code chaos_nexus} — <b>Nexus</b> : propage une part des dégâts aux cibles voines,</li>
 *   <li>{@code chaos_rupture} — <b>Rupture</b> : sur coup critique, déchirure qui ajoute des dégâts,</li>
 *   <li>{@code prismatic_brand} — <b>Marque prismatique</b> : la cible devient « marquée », elle encaisse
 *       davantage jusqu'à expiration.</li>
 * </ul>
 *
 * <p><b>Sécurité :</b> tout est borné (rayons, durées, dégâts plafonnés) et n'affecte jamais un allié,
 * un joueur en créatif, un PNJ ou le porteur lui-même. Le rayon d'action reste court (&lt; 4 blocs)
 * pour ne pas transformer un coup d'épée en explosion de zone.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class ChaosEnchantments {

    public static final String ENTROPY = "chaos_entropy";
    public static final String NEXUS = "chaos_nexus";
    public static final String RUPTURE = "chaos_rupture";
    public static final String BRAND = "prismatic_brand";

    /** Clé NBT de la marque prismatique (tick d'expiration). */
    private static final String BRAND_UNTIL = "dlb_chaos_brand_until";
    /** Clé NBT du niveau de la marque (rayon d'amplification). */
    private static final String BRAND_LEVEL = "dlb_chaos_brand_level";

    /** Malus possibles de l'Entropie (2 s). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Holder<MobEffect>[] ENTROPY_EFFECTS = new Holder[]{
            MobEffects.WITHER, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS,
            MobEffects.CONFUSION, MobEffects.BLINDNESS, MobEffects.POISON,
            MobEffects.DIG_SLOWDOWN, MobEffects.HUNGER
    };

    private ChaosEnchantments() {}

    /** Une bande de contour Chaos, synchronisée avec le rendu client et sans phase aléatoire. */
    private static DustParticleOptions prismatic(ServerLevel sl, float phase) {
        int rgb = DeepElement.chaosEdge(sl.getGameTime() * 0.05f, phase);
        return new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f), 0.95f);
    }

    /** Trois phases simultanées, avec exactement le même budget total de pixels qu'auparavant. */
    private static void prismaticBurst(ServerLevel sl, double x, double y, double z,
                                       int count, double spread, double speed) {
        for (int band = 0; band < 3; band++) {
            int n = count / 3 + (band < count % 3 ? 1 : 0);
            if (n > 0) {
                PixelFx.send(sl, prismatic(sl, band / 3.0f), x, y, z,
                        n, spread, spread, spread, speed);
            }
        }
    }

    /** Niveau d'un enchantement du mod sur la pile (0 si absent). */
    private static int level(ItemStack stack, String path) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            Map<Enchantment, Integer> map = DeepEnchant.map(stack);
            for (Map.Entry<Enchantment, Integer> e : map.entrySet()) {
                var rl = DeepEnchant.getKeySafe(e.getKey());
                if (rl == null) continue;
                if (rl.getPath().toLowerCase(Locale.ROOT).equals(path)) {
                    return Math.max(0, e.getValue() == null ? 0 : e.getValue());
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static boolean isValidVictim(Entity e, Player attacker) {
        if (!(e instanceof LivingEntity le)) return false;
        if (le == attacker || le.isSpectator()) return false;
        if (le instanceof Player p && (p.isCreative() || p.isSpectator() || p == attacker)) return false;
        return le.isAlive();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChaosHit(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0f) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        int entropy = level(weapon, ENTROPY);
        int nexus = level(weapon, NEXUS);
        int rupture = level(weapon, RUPTURE);
        int brand = level(weapon, BRAND);
        if (entropy == 0 && nexus == 0 && rupture == 0 && brand == 0) return;
        // Les malédictions / créatif ne déclenchent rien : on ne « buff » que le combat réel.
        if (attacker.isCreative()) return;

        double tx = target.getX();
        double ty = target.getY() + target.getBbHeight() * 0.55;
        double tz = target.getZ();

        // ── 1. Entropie : malus aléatoire (durée 2 s + 0,5 s par niveau) ──
        if (entropy > 0 && sl.random.nextInt(100) < 40) {
            Holder<MobEffect> effect = ENTROPY_EFFECTS[sl.random.nextInt(ENTROPY_EFFECTS.length)];
            int dur = 40 + 10 * entropy;
            target.addEffect(new MobEffectInstance(effect, dur, 0, true, false));
            prismaticBurst(sl, tx, ty, tz, 10, 0.35, 0.02);
        }

        // ── 2. Nexus : propage une fraction des dégâts aux cibles voisines (max 2) ──
        if (nexus > 0 && sl.random.nextInt(100) < 30) {
            AABB box = target.getBoundingBox().inflate(3.0);
            float share = event.getAmount() * (0.10f + 0.05f * nexus);
            int hits = 0;
            for (LivingEntity other : sl.getEntitiesOfClass(LivingEntity.class, box)) {
                if (hits >= 2) break;
                if (!isValidVictim(other, attacker)) continue;
                other.hurt(sl.damageSources().playerAttack(attacker), share);
                prismaticBurst(sl, other.getX(), other.getY() + other.getBbHeight() * 0.5,
                        other.getZ(), 6, 0.25, 0.01);
                hits++;
            }
            if (hits > 0) {
                prismaticBurst(sl, tx, ty, tz, 8, 0.30, 0.02);
            }
        }

        // ── 3. Rupture : sur critique, déchirure qui ajoute des dégâts + Wither ──
        if (rupture > 0 && sl.random.nextInt(100) < 35) {
            boolean crit = attacker.fallDistance > 0.0f || attacker.getDeltaMovement().y < -0.08
                    || sl.random.nextInt(100) < 25;
            if (crit) {
                event.setAmount(event.getAmount() + 1.5f * rupture);
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 30 + 10 * rupture, 0, true, false));
                prismaticBurst(sl, tx, ty, tz, 14, 0.40, 0.03);
            }
        }

        // ── 4. Marque prismatique : la cible encaisse +6 %/niveau pendant 4 s ──
        if (brand > 0) {
            long now = sl.getGameTime();
            long until = target.getPersistentData().getLong(BRAND_UNTIL);
            if (until > now) {
                int lvl = Math.max(1, target.getPersistentData().getInt(BRAND_LEVEL));
                event.setAmount(event.getAmount() * (1.0f + 0.06f * lvl));
                prismaticBurst(sl, tx, ty, tz, 5, 0.25, 0.01);
            }
            target.getPersistentData().putLong(BRAND_UNTIL, now + 80L);
            target.getPersistentData().putInt(BRAND_LEVEL, Math.min(5, brand));
        }
    }
}
