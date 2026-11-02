package deepluckyblock.enchantments;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.network.DeepPayloads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T342-WEAPON-VFX-30 : Observateur serveur 100 % lecture seule (aucune modification de dégâts,
 * de PV ni d'invulnerableTime) qui diffuse {@link DeepPayloads.WeaponVfxMessage} aux joueurs
 * proches (<= 48 blocs) lors des coups critiques améliorés, siphons de sang, exécutions,
 * impacts élémentaires et tirs d'armes à distance.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class WeaponVfxServerListener {

    private static final double BROADCAST_RADIUS_SQR = 48.0 * 48.0;
    private static final int KIND_IMPACT = 1;
    private static final int KIND_BEAM_SIPHON = 2;

    // Anti-rafale (évite d'envoyer 15 paquets identiques sur un balayage multi-cibles au même tick)
    private static final Map<Integer, Long> LAST_EMIT_KEY = new ConcurrentHashMap<>(128);

    private WeaponVfxServerListener() {}

    /** Appelé directement depuis le bloc de critique de UniversalEnchantmentHandler pour une fidélité 100 %. */
    public static void notifyMeleeCrit(LivingEntity attacker, LivingEntity target,
                                       WeaponVisualEffectId critStyle, int level) {
        if (attacker == null || target == null || critStyle == null) return;
        if (!(attacker.level() instanceof ServerLevel sl)) return;
        if (!allowEmit(sl, attacker.getId(), critStyle.id)) return;

        double tx = target.getX();
        double ty = target.getY() + target.getBbHeight() * 0.55;
        double tz = target.getZ();
        broadcastNear(sl, tx, ty, tz, DeepPayloads.WeaponVfxMessage.create(
                critStyle.id, KIND_IMPACT, Math.max(1, level),
                tx, ty, tz, attacker.getX(), attacker.getEyeY() - 0.25, attacker.getZ()));
    }

    /** Appelé lors d'un saut d'éclair en chaîne entre deux entités. */
    public static void notifyChainArc(ServerLevel sl, LivingEntity from, LivingEntity to, int level) {
        if (sl == null || from == null || to == null) return;
        double x0 = from.getX(), y0 = from.getY() + from.getBbHeight() * 0.55, z0 = from.getZ();
        double x1 = to.getX(),   y1 = to.getY() + to.getBbHeight() * 0.55,   z1 = to.getZ();
        broadcastNear(sl, x0, y0, z0, DeepPayloads.WeaponVfxMessage.create(
                WeaponVisualEffectId.CHAIN_VOLT_ARC.id, KIND_BEAM_SIPHON, level,
                x0, y0, z0, x1, y1, z1));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurtVisuals(LivingIncomingDamageEvent event) {
        try {
            if (event.isCanceled() || event.getAmount() <= 0.0f) return;
            LivingEntity victim = event.getEntity();
            if (!(victim.level() instanceof ServerLevel sl)) return;

            Entity direct = event.getSource().getDirectEntity();
            Entity owner = event.getSource().getEntity();
            if (!(owner instanceof LivingEntity attacker)) return;

            // Tirs de projectiles gérés par onProjectileImpactVisuals (sauf siphon/transfusion/critique d'arc)
            if (direct instanceof AbstractArrow) {
                return;
            }

            ItemStack weapon = attacker.getMainHandItem();
            List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(weapon, 2);
            if (layers.isEmpty()) return;

            double vx = victim.getX();
            double vy = victim.getY() + victim.getBbHeight() * 0.52;
            double vz = victim.getZ();
            double ax = attacker.getX();
            double ay = attacker.getY() + attacker.getBbHeight() * 0.62;
            double az = attacker.getZ();

            for (WeaponVisualEffectId.ResolvedLayer layer : layers) {
                WeaponVisualEffectId fx = layer.effect();
                // Les critiques purs (#06..#08) sont déjà émis par notifyMeleeCrit quand un vrai critique sort
                if (fx.category == WeaponVisualEffectId.Category.CRIT) continue;
                if (!allowEmit(sl, attacker.getId(), fx.id)) continue;

                int kind = (fx == WeaponVisualEffectId.CRIMSON_SIPHON
                        || fx == WeaponVisualEffectId.SPECTRAL_REACH)
                        ? KIND_BEAM_SIPHON
                        : KIND_IMPACT;

                double ox = (fx == WeaponVisualEffectId.SPECTRAL_REACH || fx == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) ? ax : vx;
                double oy = (fx == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) ? attacker.getY() + 0.45 : ((fx == WeaponVisualEffectId.SPECTRAL_REACH) ? ay : vy);
                double oz = (fx == WeaponVisualEffectId.SPECTRAL_REACH || fx == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) ? az : vz;
                double tx = (fx == WeaponVisualEffectId.SPECTRAL_REACH) ? vx : ax;
                double ty = (fx == WeaponVisualEffectId.SPECTRAL_REACH) ? vy : ay;
                double tz = (fx == WeaponVisualEffectId.SPECTRAL_REACH) ? vz : az;

                broadcastNear(sl, vx, vy, vz, DeepPayloads.WeaponVfxMessage.create(
                        fx.id, kind, layer.level(), ox, oy, oz, tx, ty, tz));
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onProjectileImpactVisuals(ProjectileImpactEvent event) {
        try {
            Entity proj = event.getProjectile();
            if (!(proj.level() instanceof ServerLevel sl)) return;

            ItemStack weapon = ItemStack.EMPTY;
            LivingEntity shooter = null;
            if (proj instanceof ThrownTrident trident) {
                if (trident.getOwner() instanceof LivingEntity lv) shooter = lv;
                weapon = trident.getPickupItemStackOrigin();
            } else if (proj instanceof AbstractArrow arrow) {
                if (arrow.getOwner() instanceof LivingEntity lv) shooter = lv;
                weapon = UniversalEnchantmentHandler.resolveArrowWeapon(arrow, shooter);
            }
            if (weapon == null || weapon.isEmpty()) return;

            List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(weapon, 2);
            if (layers.isEmpty()) return;

            Vec3 hit = event.getRayTraceResult().getLocation();
            int ownerId = shooter != null ? shooter.getId() : proj.getId();
            double sx = shooter != null ? shooter.getX() : proj.getX();
            double sy = shooter != null ? shooter.getEyeY() - 0.15 : proj.getY();
            double sz = shooter != null ? shooter.getZ() : proj.getZ();

            for (WeaponVisualEffectId.ResolvedLayer layer : layers) {
                WeaponVisualEffectId fx = layer.effect();
                if (!allowEmit(sl, ownerId, fx.id + 64)) continue;

                boolean beamMode = (fx == WeaponVisualEffectId.ASTRAL_STAR_LANCE
                        || fx == WeaponVisualEffectId.CRIMSON_SIPHON
                        || fx == WeaponVisualEffectId.SPECTRAL_REACH);
                int kind = beamMode ? KIND_BEAM_SIPHON : KIND_IMPACT;

                double ox = (fx == WeaponVisualEffectId.CRIMSON_SIPHON) ? hit.x : (beamMode ? sx : hit.x);
                double oy = (fx == WeaponVisualEffectId.CRIMSON_SIPHON) ? hit.y + 0.4 : (beamMode ? sy : hit.y + 0.35);
                double oz = (fx == WeaponVisualEffectId.CRIMSON_SIPHON) ? hit.z : (beamMode ? sz : hit.z);
                double tx = (fx == WeaponVisualEffectId.CRIMSON_SIPHON) ? sx : hit.x;
                double ty = (fx == WeaponVisualEffectId.CRIMSON_SIPHON) ? sy : hit.y + 0.35;
                double tz = (fx == WeaponVisualEffectId.CRIMSON_SIPHON) ? sz : hit.z;

                broadcastNear(sl, hit.x, hit.y, hit.z, DeepPayloads.WeaponVfxMessage.create(
                        fx.id, kind, layer.level(), ox, oy, oz, tx, ty, tz));
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean allowEmit(ServerLevel sl, int ownerEntityId, int channel) {
        long now = sl.getGameTime();
        if (LAST_EMIT_KEY.size() > 512) {
            LAST_EMIT_KEY.clear();
        }
        int key = ownerEntityId * 97 + channel;
        Long prev = LAST_EMIT_KEY.get(key);
        if (prev != null && now - prev < 2L) {
            return false;
        }
        LAST_EMIT_KEY.put(key, now);
        return true;
    }

    private static void broadcastNear(ServerLevel sl, double x, double y, double z,
                                      DeepPayloads.WeaponVfxMessage msg) {
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(x, y, z) <= BROADCAST_RADIUS_SQR) {
                PacketDistributor.sendToPlayer(sp, msg);
            }
        }
    }
}
