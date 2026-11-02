package deepluckyblock.client;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.EnchantmentComboRegistry;
import deepluckyblock.enchantments.EnchantmentComboRegistry.ComboMask;
import deepluckyblock.enchantments.UniversalEnchantmentHandler;
import deepluckyblock.util.DeepEnchant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * T321-CONTINUOUS-COSMETIC-FX : Moteur client temps réel d'auras, halos, couronnes et rubans d'ailes.
 *
 * Pourquoi côté Client (ClientTickEvent.Post) et non via des salves serveur espacées (% 20..200) ?
 *  1) Continuité infinie : les particules sont émises à chaque tick directement sur la position +
 *     vélocité courante de l'entité -> le halo (ex. Combo #110, Roi, Esprit Mystique, Égide Radieuse,
 *     ailes d'Archange...) suit le joueur en continu sans jamais clignoter ni rester figé derrière lui.
 *  2) Zéro paquet réseau & zéro charge serveur (0.00 ms TPS).
 *  3) Ultra-optimisé anti-lag : profil cosmétique mis en cache sur un simple int bitmask toutes les
 *     10 ticks (0,5 s), culling à 24 blocs, pause automatique et plafond strict de particules/tick.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class CosmeticAuraClientTicker {

    private CosmeticAuraClientTicker() {}

    private static final int F_COMBO_110_AVATAR      = 1;
    private static final int F_COMBO_097_ETERNAL     = 1 << 1;
    private static final int F_COMBO_098_SWORD_GOD   = 1 << 2;
    private static final int F_COMBO_101_ARCHANGEL   = 1 << 3;
    private static final int F_COMBO_106_WARLORD     = 1 << 4;
    private static final int F_COMBO_062_PLAGUE      = 1 << 5;
    private static final int F_ENCH_KING             = 1 << 6;
    private static final int F_ENCH_MYSTIC_MIND      = 1 << 7;
    private static final int F_ENCH_RADIANT_AEGIS    = 1 << 8;
    private static final int F_ENCH_WINGED_ENDURANCE = 1 << 9;
    private static final int F_ENCH_DREAD_AURA       = 1 << 10;

    private static final DustParticleOptions STELLAR_WHITE =
            new DustParticleOptions(new Vector3f(1.00f, 1.00f, 1.00f), 0.68f);
    private static final DustParticleOptions CELESTIAL_GOLD =
            new DustParticleOptions(new Vector3f(1.00f, 0.86f, 0.28f), 0.62f);
    private static final DustParticleOptions CROWN_GOLD =
            new DustParticleOptions(new Vector3f(1.00f, 0.82f, 0.18f), 0.60f);
    private static final DustParticleOptions BLADE_SILVER =
            new DustParticleOptions(new Vector3f(0.84f, 0.90f, 0.98f), 0.58f);
    private static final DustParticleOptions MYSTIC_VIOLET =
            new DustParticleOptions(new Vector3f(0.62f, 0.28f, 0.96f), 0.58f);
    private static final DustParticleOptions RADIANT_PEARL =
            new DustParticleOptions(new Vector3f(1.00f, 0.96f, 0.76f), 0.56f);
    private static final DustParticleOptions PLAGUE_MIASMA =
            new DustParticleOptions(new Vector3f(0.40f, 0.68f, 0.08f), 0.66f);
    private static final DustParticleOptions DREAD_SHADOW =
            new DustParticleOptions(new Vector3f(0.26f, 0.06f, 0.44f), 0.62f);

    private static final int MAX_PARTICLES_PER_TICK = 36;
    private static final double CULL_DIST_SQR = 24.0 * 24.0;

    private static final class CachedProfile {
        int flags;
        long expiresAt;
    }

    private static final Map<Integer, CachedProfile> PROFILE_CACHE = new HashMap<>();
    private static ClientLevel cachedLevel = null;
    private static long lastGameTime = -1L;

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer viewer = mc.player;
            ClientLevel level = mc.level;
            if (viewer == null || level == null) return;

            if (cachedLevel != level) {
                PROFILE_CACHE.clear();
                cachedLevel = level;
                lastGameTime = -1L;
            }

            long now = level.getGameTime();
            // En pause solo, gameTime n'avance pas -> 0 particule émise pendant l'écran pause !
            if (now == lastGameTime) return;
            lastGameTime = now;

            DeepEnchant.rememberClientAccess(level.registryAccess());

            if ((now & 127L) == 0L && PROFILE_CACHE.size() > 64) {
                Iterator<Map.Entry<Integer, CachedProfile>> it = PROFILE_CACHE.entrySet().iterator();
                while (it.hasNext()) {
                    if (now > it.next().getValue().expiresAt + 100L) it.remove();
                }
            }

            int budget = MAX_PARTICLES_PER_TICK;

            // 1) Toujours traiter en priorité le joueur local
            budget = emitForEntity(level, viewer, now, budget);

            // 2) Autres joueurs proches dans un rayon de 24 blocs (multijoueur / LAN)
            if (budget > 0) {
                List<? extends Player> nearbyPlayers = level.players();
                for (int i = 0, n = nearbyPlayers.size(); i < n && budget > 0; i++) {
                    Player other = nearbyPlayers.get(i);
                    if (other == viewer || !other.isAlive() || other.isSpectator()) continue;
                    if (other.distanceToSqr(viewer) > CULL_DIST_SQR) continue;
                    budget = emitForEntity(level, other, now, budget);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int getCosmeticFlags(LivingEntity entity, long now) {
        CachedProfile cp = PROFILE_CACHE.get(entity.getId());
        if (cp != null && now < cp.expiresAt) {
            return cp.flags;
        }
        if (cp == null) {
            cp = new CachedProfile();
            PROFILE_CACHE.put(entity.getId(), cp);
        }
        cp.expiresAt = now + 10L;

        if (!UniversalEnchantmentHandler.hasAnyEnchantedEquipment(entity)) {
            cp.flags = 0;
            return 0;
        }

        int f = 0;
        ComboMask mask = EnchantmentComboRegistry.getActiveMask(entity);
        if (!mask.isEmpty()) {
            if (mask.has(EnchantmentComboRegistry.IDX_AVATAR_SUPREME_DEEP_LUCKY)) f |= F_COMBO_110_AVATAR;
            if (mask.has(EnchantmentComboRegistry.IDX_ARMURE_ETERNELLE_TRANSCENDANTE)) f |= F_COMBO_097_ETERNAL;
            if (mask.has(EnchantmentComboRegistry.IDX_DIEU_LAME_SUPREME)) f |= F_COMBO_098_SWORD_GOD;
            if (mask.has(EnchantmentComboRegistry.IDX_ARCHANGE_JUGEMENT_SOLAIRE)) f |= F_COMBO_101_ARCHANGEL;
            if (mask.has(EnchantmentComboRegistry.IDX_SEIGNEUR_GUERRE_APOCALYPSE)) f |= F_COMBO_106_WARLORD;
            if (mask.has(EnchantmentComboRegistry.IDX_PESTE_AMBULANTE)) f |= F_COMBO_062_PLAGUE;
        }

        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:king") > 0) f |= F_ENCH_KING;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:mystic_mind") > 0) f |= F_ENCH_MYSTIC_MIND;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:heart_resonance") > 0) f |= F_ENCH_RADIANT_AEGIS;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:wing_toughness_armor") > 0) f |= F_ENCH_WINGED_ENDURANCE;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:dread_aura") > 0) f |= F_ENCH_DREAD_AURA;

        cp.flags = f;
        return f;
    }

    private static int emitForEntity(ClientLevel level, LivingEntity entity, long now, int budget) {
        if (entity.isInvisible()) return budget;
        int flags = getCosmeticFlags(entity, now);
        if (flags == 0) return budget;

        double vx = entity.getX() - entity.xo;
        double vy = entity.getY() - entity.yo;
        double vz = entity.getZ() - entity.zo;
        // Centre anticipé d'une demi-frame + héritage de vitesse pour que le halo colle à la tête en mouvement !
        double cx = entity.getX() + vx * 0.60;
        double baseY = entity.getY() + vy * 0.45;
        double cz = entity.getZ() + vz * 0.60;
        double headY = baseY + entity.getBbHeight() + 0.32;
        double pvx = vx * 0.55;
        double pvy = vy * 0.30;
        double pvz = vz * 0.55;

        // --- 1. HALO SUPRÊME CONTINU : Combo #110 (Avatar Ultime du Deep's Lucky Block) ---
        if ((flags & F_COMBO_110_AVATAR) != 0) {
            double phase = now * 0.22;
            int pts = 6;
            for (int i = 0; i < pts && budget > 0; i++) {
                double ang = phase + (Math.PI * 2.0 * i) / pts;
                ParticleOptions dust = ((i & 1) == 0) ? STELLAR_WHITE : CELESTIAL_GOLD;
                level.addParticle(dust,
                        cx + Math.cos(ang) * 0.48,
                        headY + Math.sin(ang * 2.0 + now * 0.10) * 0.03,
                        cz + Math.sin(ang) * 0.48,
                        pvx, pvy, pvz);
                budget--;
            }
            if ((now % 3L) == 0L && budget > 0) {
                double starAng = -now * 0.31;
                level.addParticle(ParticleTypes.END_ROD,
                        cx + Math.cos(starAng) * 0.52,
                        headY + 0.04,
                        cz + Math.sin(starAng) * 0.52,
                        pvx * 0.3, 0.008, pvz * 0.3);
                budget--;
            }
        } else if ((flags & F_COMBO_097_ETERNAL) != 0) {
            // Combo #097 : Couronne dorée transcendante continue
            budget = emitOrbitHalo(level, cx, headY, cz, pvx, pvy, pvz, 0.44, now * 0.20, 4, CELESTIAL_GOLD, null, budget);
        } else if ((flags & F_COMBO_098_SWORD_GOD) != 0) {
            // Combo #098 : Halo doré & argenté continu de l'Empereur de l'Épée
            budget = emitOrbitHalo(level, cx, headY, cz, pvx, pvy, pvz, 0.45, now * 0.25, 4, CROWN_GOLD, BLADE_SILVER, budget);
        } else {
            // Couronnes d'enchantements individuels (Roi, Esprit Mystique, Égide Radieuse)
            if ((flags & F_ENCH_KING) != 0) {
                budget = emitOrbitHalo(level, cx, headY, cz, pvx, pvy, pvz, 0.40, now * 0.18, 3, CROWN_GOLD, null, budget);
            }
            if ((flags & F_ENCH_MYSTIC_MIND) != 0 && (now & 1L) == 0L) {
                budget = emitOrbitHalo(level, cx, headY + 0.03, cz, pvx, pvy, pvz, 0.38, -now * 0.16, 3, MYSTIC_VIOLET, null, budget);
            }
            if ((flags & F_ENCH_RADIANT_AEGIS) != 0 && (now & 1L) == 1L) {
                budget = emitOrbitHalo(level, cx, headY - 0.02, cz, pvx, pvy, pvz, 0.41, now * 0.15, 3, RADIANT_PEARL, null, budget);
            }
        }

        // --- 2. RUBANS D'AILES CONTINUS (Combo #101 Archange Solaire & Endurance Ailée) ---
        if ((flags & F_COMBO_101_ARCHANGEL) != 0 && budget >= 2) {
            budget = emitWingStreams(level, entity, cx, baseY, cz, pvx, pvy, pvz, CELESTIAL_GOLD, now, budget);
        } else if ((flags & F_ENCH_WINGED_ENDURANCE) != 0 && (!entity.onGround() || entity.isFallFlying())
                && (now & 1L) == 0L && budget >= 2) {
            budget = emitWingStreams(level, entity, cx, baseY, cz, pvx, pvy, pvz, ParticleTypes.CLOUD, now, budget);
        }

        // --- 3. AURAS BASSES CONTINUES (Combo #106 Seigneur Volcanique, #062 Peste, Aura d'Effroi) ---
        if ((flags & F_COMBO_106_WARLORD) != 0 && (now & 1L) == 0L && budget >= 2) {
            double ang = now * 0.24;
            for (int i = 0; i < 2 && budget > 0; i++) {
                double a = ang + i * Math.PI;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        cx + Math.cos(a) * 0.85, baseY + 0.12, cz + Math.sin(a) * 0.85,
                        pvx * 0.4, 0.015, pvz * 0.4);
                budget--;
            }
        }
        if ((flags & F_COMBO_062_PLAGUE) != 0 && (now & 1L) == 1L && budget >= 2) {
            budget = emitOrbitHalo(level, cx, baseY + 0.15, cz, pvx, pvy, pvz, 0.95, now * 0.16, 2, PLAGUE_MIASMA, null, budget);
        }
        if ((flags & F_ENCH_DREAD_AURA) != 0 && (now % 3L) == 0L && budget > 0) {
            double ang = -now * 0.20;
            level.addParticle(DREAD_SHADOW,
                    cx + Math.cos(ang) * 0.65, baseY + 0.35, cz + Math.sin(ang) * 0.65,
                    pvx * 0.4, 0.01, pvz * 0.4);
            budget--;
        }

        return budget;
    }

    private static int emitOrbitHalo(ClientLevel level, double cx, double y, double cz,
                                     double pvx, double pvy, double pvz,
                                     double radius, double phase, int points,
                                     ParticleOptions primary, ParticleOptions alt, int budget) {
        for (int i = 0; i < points && budget > 0; i++) {
            double ang = phase + (Math.PI * 2.0 * i) / points;
            ParticleOptions p = (alt != null && (i & 1) == 1) ? alt : primary;
            level.addParticle(p,
                    cx + Math.cos(ang) * radius,
                    y,
                    cz + Math.sin(ang) * radius,
                    pvx, pvy, pvz);
            budget--;
        }
        return budget;
    }

    private static int emitWingStreams(ClientLevel level, LivingEntity entity,
                                       double cx, double baseY, double cz,
                                       double pvx, double pvy, double pvz,
                                       ParticleOptions particle, long now, int budget) {
        float yawRad = entity.getYRot() * ((float) Math.PI / 180.0F);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = -fwdZ;
        double rightZ = fwdX;
        double flap = Math.sin(now * 0.22) * 0.08;
        double backX = cx - fwdX * 0.32;
        double backZ = cz - fwdZ * 0.32;
        double wingY = baseY + entity.getBbHeight() * 0.68 + flap;
        double span = 0.56 + Math.cos(now * 0.22) * 0.06;

        if (budget > 0) {
            level.addParticle(particle,
                    backX - rightX * span, wingY, backZ - rightZ * span,
                    pvx * 0.4, pvy * 0.3, pvz * 0.4);
            budget--;
        }
        if (budget > 0) {
            level.addParticle(particle,
                    backX + rightX * span, wingY, backZ + rightZ * span,
                    pvx * 0.4, pvy * 0.3, pvz * 0.4);
            budget--;
        }
        return budget;
    }
}
