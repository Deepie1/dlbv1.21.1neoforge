package deepluckyblock.enchantments;

import deepluckyblock.DeepLuckyBlockMod;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;

/**
 * T352b-ELEMENT-RANGED : <b>11 enchantements élémentaires de combat à distance</b>, un par famille
 * (Bois, Poison, Feu, Terre, Métal, Eau, Foudre, Lumière, Sang, Vide, Chaos).
 *
 * <p><b>Pourquoi ?</b> Le recomptage par catégorie montrait que le tir n'était couvert que par trois
 * familles (Bois/Poison/Eau) alors que l'arc, l'arbalète et le trident acceptent tous les
 * enchantements du mod. Chaque famille reçoit donc ici <b>sa signature à distance</b> : elle fonctionne
 * à l'arc, à l'arbalète et au trident, et garde le code couleur de la famille
 * (Bois brun, Poison vert foncé, Terre vert clair/brun...).</p>
 *
 * <p>Chaque effet est borné (durées ≤ 5 s, dégâts ≤ 2 cœurs, aucun effet sur les alliés, les joueurs
 * en créatif, les PNJ ou le tireur) et se déclenche au toucher, projectile comme mêlée.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class ElementalEnchantEffects {

    // Chemins d'enregistrement (data/deep_lucky_block/enchantment/*.json)
    public static final String TIMBER_ARROW = "timber_arrow";           // Bois (brun)
    public static final String MIASMA_ARROW = "miasma_arrow";           // Poison (vert foncé)
    public static final String CINDER_VOLLEY = "cinder_volley";         // Feu
    public static final String SANDBURST_SHOT = "sandburst_shot";       // Terre (vert clair/brun)
    public static final String PIERCING_BARB = "piercing_barb";         // Métal
    public static final String RIPTIDE_BARB = "riptide_barb";           // Eau
    public static final String VOLTAIC_SHOT = "voltaic_shot";           // Foudre
    public static final String RADIANT_BOLT = "radiant_bolt";           // Lumière
    public static final String BLOODLETTING_BOLT = "bloodletting_bolt"; // Sang
    public static final String NULL_SHOT = "null_shot";                 // Vide
    public static final String ENTROPY_BOLT = "entropy_bolt";           // Chaos

    /** Tous les enchantements gérés ici (ordre des familles). */
    public static final String[] ALL = {
            TIMBER_ARROW, MIASMA_ARROW, CINDER_VOLLEY, SANDBURST_SHOT, PIERCING_BARB,
            RIPTIDE_BARB, VOLTAIC_SHOT, RADIANT_BOLT, BLOODLETTING_BOLT, NULL_SHOT, ENTROPY_BOLT
    };

    /** Famille de chaque enchantement, dans le même ordre que {@link #ALL}. */
    public static final DeepElement[] ALL_ELEMENTS = {
            DeepElement.WOOD, DeepElement.POISON, DeepElement.FIRE, DeepElement.EARTH, DeepElement.METAL,
            DeepElement.WATER, DeepElement.THUNDER, DeepElement.LIGHT, DeepElement.BLOOD,
            DeepElement.VOID, DeepElement.CHAOS
    };

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Holder<MobEffect>[] ENTROPY_MALUS = new Holder[]{
            MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.CONFUSION, MobEffects.BLINDNESS, MobEffects.WITHER
    };

    private ElementalEnchantEffects() {}

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

    private static boolean validVictim(Entity e, Player shooter) {
        if (!(e instanceof LivingEntity le)) return false;
        if (le == shooter || le.isSpectator()) return false;
        if (le instanceof Player p) return !p.isCreative() && !p.isSpectator() && p != shooter;
        return le.isAlive();
    }

    private static DustParticleOptions dust(int rgb) {
        return new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f), 0.95f);
    }

    private static void puff(ServerLevel sl, LivingEntity target, int rgb, int count) {
        PixelFx.send(sl, dust(rgb), target.getX(), target.getY() + target.getBbHeight() * 0.55,
                target.getZ(), count, 0.30, 0.30, 0.30, 0.02);
    }

    /** Contour Chaos secondaire : trois teintes synchronisées, sans changer le budget de pixels. */
    private static void chaosPuff(ServerLevel sl, LivingEntity target, int count) {
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.55, z = target.getZ();
        for (int band = 0; band < 3; band++) {
            int n = count / 3 + (band < count % 3 ? 1 : 0);
            if (n > 0) {
                PixelFx.send(sl, dust(DeepElement.chaosEdge(sl.getGameTime() * 0.05f, band / 3.0f)),
                        x, y, z, n, 0.30, 0.30, 0.30, 0.02);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onElementalHit(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0f) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        if (!(event.getSource().getEntity() instanceof Player shooter)) return;
        if (shooter.isCreative() || shooter.isSpectator()) return;

        ItemStack weapon = shooter.getMainHandItem();
        if (weapon.isEmpty()) return;

        // ── 1. Bois (brun) : racines — la cible est ralentie et ne peut plus fuir ──
        int timber = level(weapon, TIMBER_ARROW);
        if (timber > 0 && sl.random.nextInt(100) < 35) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30 + 10 * timber, 2, true, false));
            puff(sl, target, DeepElement.WOOD.primary, 8);
        }

        // ── 2. Poison (vert foncé) : nuage de miasme — poison qui dure ──
        int miasma = level(weapon, MIASMA_ARROW);
        if (miasma > 0 && sl.random.nextInt(100) < 40) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 50 + 15 * miasma, 0, true, false));
            PixelFx.send(sl, ParticleTypes.ITEM_SLIME, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5, target.getZ(), 10, 0.35, 0.35, 0.35, 0.01);
            puff(sl, target, DeepElement.POISON.primary, 10);
        }

        // ── 3. Feu : voile de braises — embrase puis brûle ──
        int cinder = level(weapon, CINDER_VOLLEY);
        if (cinder > 0 && sl.random.nextInt(100) < 40) {
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), (40 + 10 * cinder)));
            puff(sl, target, DeepElement.FIRE.primary, 10);
        }

        // ── 4. Terre (vert clair/brun) : éclat de sable — ralentit et alourdit ──
        int sand = level(weapon, SANDBURST_SHOT);
        if (sand > 0 && sl.random.nextInt(100) < 35) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 + 10 * sand, 0, true, false));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0, true, false));
            puff(sl, target, DeepElement.EARTH.primary, 12);
        }

        // ── 5. Métal : pointe perforante — dégâts qui ignorent l'armure ──
        int barb = level(weapon, PIERCING_BARB);
        if (barb > 0 && sl.random.nextInt(100) < 30) {
            target.hurt(sl.damageSources().magic(), 0.8f * barb);
            puff(sl, target, DeepElement.METAL.primary, 8);
        }

        // ── 6. Eau : givre — ralentit et éteint ──
        int riptide = level(weapon, RIPTIDE_BARB);
        if (riptide > 0 && sl.random.nextInt(100) < 35) {
            target.clearFire();
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35 + 10 * riptide, 1, true, false));
            puff(sl, target, DeepElement.WATER.primary, 12);
        }

        // ── 7. Foudre : arc voltaïque — rebondit sur une seconde cible ──
        int voltaic = level(weapon, VOLTAIC_SHOT);
        if (voltaic > 0 && sl.random.nextInt(100) < 30) {
            puff(sl, target, DeepElement.THUNDER.primary, 8);
            AABB box = target.getBoundingBox().inflate(3.5);
            int chained = 0;
            for (LivingEntity other : sl.getEntitiesOfClass(LivingEntity.class, box)) {
                if (chained >= 1) break;
                if (!validVictim(other, shooter)) continue;
                other.hurt(sl.damageSources().playerAttack(shooter), 1.0f * voltaic);
                puff(sl, other, DeepElement.THUNDER.highlight, 6);
                chained++;
            }
        }

        // ── 8. Lumière : éclat radieux — soigne légèrement le tireur ──
        int radiant = level(weapon, RADIANT_BOLT);
        if (radiant > 0 && sl.random.nextInt(100) < 30) {
            if (shooter.getHealth() < shooter.getMaxHealth()) {
                shooter.heal(0.5f * radiant);
            }
            puff(sl, target, DeepElement.LIGHT.primary, 10);
        }

        // ── 9. Sang : saignement — dégâts répartis sur 3 s + petit soin ──
        int bleed = level(weapon, BLOODLETTING_BOLT);
        if (bleed > 0 && sl.random.nextInt(100) < 35) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, true, false));
            if (shooter.getHealth() < shooter.getMaxHealth()) {
                shooter.heal(0.3f * bleed);
            }
            puff(sl, target, DeepElement.BLOOD.primary, 10);
        }

        // ── 10. Vide : aimant gravitationnel — la cible est attirée au point d'impact ──
        int nullShot = level(weapon, NULL_SHOT);
        if (nullShot > 0 && sl.random.nextInt(100) < 30) {
            // Impulsion vers le tireur (jamais de normalize() sur un vecteur nul : NaN côté client).
            Vec3 delta = shooter.position().subtract(target.position());
            double len = delta.length();
            if (len > 0.05) {
                Vec3 toward = delta.scale((0.28 + 0.08 * nullShot) / len);
                target.push(toward.x, 0.12, toward.z);
            } else {
                target.push(0.0, 0.12, 0.0);
            }
            target.hurtMarked = true;
            puff(sl, target, DeepElement.VOID.primary, 10);
        }

        // ── 11. Chaos : aiguille entropique — malus aléatoire, teinte qui tourne ──
        int entropy = level(weapon, ENTROPY_BOLT);
        if (entropy > 0 && sl.random.nextInt(100) < 35) {
            Holder<MobEffect> malus = ENTROPY_MALUS[sl.random.nextInt(ENTROPY_MALUS.length)];
            target.addEffect(new MobEffectInstance(malus, 40 + 10 * entropy, 0, true, false));
            chaosPuff(sl, target, 10);
        }
    }
}
