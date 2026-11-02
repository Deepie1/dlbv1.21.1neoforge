package deepluckyblock.enchantments;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.network.DeepPayloads;
import deepluckyblock.procedures.TestProcedure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T343-VOXEL-SERVER-SYNC : Diffusion serveur S2C 100 % fonctionnelle des 30 effets d'armes :
 * <ul>
 *   <li>Synchronise les sillages de mini-pixels 3D en direct sur chaque flèche, carreau et trident
 *       enchanté dès leur apparition ({@link EntityJoinLevelEvent}).</li>
 *   <li>Gère les salves multi-flèches ({@code fan_volley}, {@code scatter}, {@code multishot}),
 *       les coups à dégâts nuls ({@code blood_transfusion}, {@code curse_of_mercy}, {@code toy}),
 *       les critiques mêlée et distance, les gantelets à mains nues et les bannières clic-droit.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class WeaponVfxServerListener {

    private static final int KIND_IMPACT            = 1;
    private static final int KIND_BEAM_SIPHON       = 2;
    private static final int KIND_PROJECTILE_ATTACH = 3;

    private static final Map<Integer, Long> LAST_ATTACKER_HIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_PROJECTILE_HIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_CRIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_RALLY_TICK = new ConcurrentHashMap<>();

    private WeaponVfxServerListener() {}

    /** Appelé par {@code UniversalEnchantmentHandler} lors d'un coup critique mêlée (vanilla ou custom). */
    public static void notifyMeleeCrit(Player attacker, Entity target,
                                       WeaponVisualEffectId critStyle, int intensity) {
        if (attacker == null || target == null) return;
        if (!(attacker.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        LAST_CRIT_TICK.put(attacker.getId(), now);

        WeaponVisualEffectId fx = (critStyle != null) ? critStyle : WeaponVisualEffectId.GOLDEN_CRIT_STAR;
        float cx = (float) target.getX();
        float cy = (float) (target.getY() + target.getBbHeight() * 0.55);
        float cz = (float) target.getZ();
        broadcastNear(sl, cx, cy, cz, fx.id, KIND_IMPACT, Math.max(2, intensity),
                cx, cy, cz, (float) attacker.getX(), (float) attacker.getEyeY(), (float) attacker.getZ());
    }

    /** Appelé lors d'un rebond d'éclair en chaîne entre deux entités. */
    public static void notifyChainArc(ServerLevel sl, Entity from, Entity to, int level) {
        if (sl == null || from == null || to == null) return;
        float fx = (float) from.getX();
        float fy = (float) (from.getY() + from.getBbHeight() * 0.5);
        float fz = (float) from.getZ();
        float tx = (float) to.getX();
        float ty = (float) (to.getY() + to.getBbHeight() * 0.5);
        float tz = (float) to.getZ();
        broadcastNear(sl, tx, ty, tz,
                WeaponVisualEffectId.CHAIN_VOLT_ARC.id, KIND_BEAM_SIPHON, Math.max(1, level),
                fx, fy, fz, tx, ty, tz);
    }

    /**
     * Attache automatiquement un sillage de mini-pixels 3D en temps réel à toute flèche, carreau
     * ou trident tiré avec une arme enchantée !
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile proj)) return;
        if (!(proj instanceof AbstractArrow)) return;

        // Planifié à J+1 tick afin que ClientboundAddEntityPacket et le snapshot d'arme soient déjà prêts
        TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 1L, () -> {
            if (!proj.isAlive()) return;
            if (!(proj.getOwner() instanceof LivingEntity shooter)) return;

            ItemStack weapon = ItemStack.EMPTY;
            if (proj instanceof ThrownTrident trident) {
                weapon = trident.getPickupItemStackOrigin();
            } else if (proj instanceof AbstractArrow arrow) {
                weapon = UniversalEnchantmentHandler.resolveArrowWeapon(arrow, shooter);
            }
            if (weapon.isEmpty()) {
                weapon = shooter.getMainHandItem();
            }
            List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(weapon, 1);
            if (layers.isEmpty()) return;

            WeaponVisualEffectId.ResolvedLayer top = layers.get(0);
            broadcastNear(sl, (float) proj.getX(), (float) proj.getY(), (float) proj.getZ(),
                    top.effect().id, KIND_PROJECTILE_ATTACH, top.level(),
                    (float) proj.getX(), (float) proj.getY(), (float) proj.getZ(),
                    (float) proj.getId(), 0.0f, 0.0f);
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurtVisuals(LivingIncomingDamageEvent event) {
        // Note : on autorise event.getAmount() == 0.0f pour blood_transfusion (#11), curse_of_mercy et toy !
        if (event.isCanceled()) return;
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel sl)) return;

        DamageSource src = event.getSource();
        Entity attackerEntity = src.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) return;

        long now = sl.getGameTime();
        Entity direct = src.getDirectEntity();
        boolean isProjectileHit = direct instanceof Projectile;

        // En mêlée pure, évite le spam i-frame ; à distance, autorise les salves multi-flèches (jusqu'à 4 impacts/tick)
        if (!isProjectileHit) {
            Long prev = LAST_ATTACKER_HIT_TICK.put(attacker.getId(), now);
            if (prev != null && now - prev < 2L) return;
        } else {
            Long prevProj = LAST_PROJECTILE_HIT_TICK.put(direct.getId(), now);
            if (prevProj != null && now == prevProj) return;
        }

        if (LAST_ATTACKER_HIT_TICK.size() > 512) {
            LAST_ATTACKER_HIT_TICK.clear();
            LAST_PROJECTILE_HIT_TICK.clear();
            LAST_CRIT_TICK.clear();
            LAST_RALLY_TICK.clear();
        }

        ItemStack weapon = resolveImpactWeapon(attacker, direct);
        List<WeaponVisualEffectId.ResolvedLayer> layers = resolveAttackerLayers(attacker, weapon, 2);
        if (layers.isEmpty()) return;

        float tx = (float) target.getX();
        float ty = (float) (target.getY() + target.getBbHeight() * 0.5);
        float tz = (float) target.getZ();
        float ax = (float) attacker.getX();
        float ay = (float) (attacker.getY() + attacker.getBbHeight() * 0.62);
        float az = (float) attacker.getZ();

        boolean meleeCritJustFired = (now - LAST_CRIT_TICK.getOrDefault(attacker.getId(), -100L)) <= 1L;
        boolean arrowCrit = (direct instanceof AbstractArrow arr) && arr.isCritArrow();

        // Critique à l'arc / arbalète : étoile critique pixel 3D dédiée si pas déjà en couche 0
        if (arrowCrit && layers.get(0).effect().category != WeaponVisualEffectId.Category.CRIT) {
            broadcastNear(sl, tx, ty, tz, WeaponVisualEffectId.GOLDEN_CRIT_STAR.id, KIND_IMPACT, 2,
                    tx, ty, tz, ax, ay, az);
        }

        for (int i = 0; i < layers.size(); i++) {
            WeaponVisualEffectId.ResolvedLayer layer = layers.get(i);
            WeaponVisualEffectId fx = layer.effect();

            // Évite uniquement de doubler exactement le même effet critique si notifyMeleeCrit vient de le lancer
            if (fx.category == WeaponVisualEffectId.Category.CRIT && meleeCritJustFired && i == 0) {
                continue;
            }

            if (fx == WeaponVisualEffectId.CRIMSON_SIPHON) {
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                        tx, ty, tz, ax, ay, az);
            } else if (fx == WeaponVisualEffectId.ASTRAL_STAR_LANCE && attacker.distanceToSqr(target) > 9.0) {
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                        ax, (float) attacker.getEyeY() - 0.12f, az, tx, ty, tz);
            } else {
                int boost = (arrowCrit && fx.category == WeaponVisualEffectId.Category.CRIT) ? layer.level() + 1 : layer.level();
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_IMPACT, boost,
                        tx, ty, tz, ax, ay, az);
            }
        }
    }

    /** Impact sur bloc (flèche ou trident planté dans le décor) : salve de mini-pixels 3D ! */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileBlockVisuals(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        if (!(proj.level() instanceof ServerLevel sl)) return;
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK) return;
        if (!(proj.getOwner() instanceof LivingEntity shooter)) return;

        long now = sl.getGameTime();
        Long prev = LAST_PROJECTILE_HIT_TICK.put(proj.getId(), now);
        if (prev != null && now - prev < 2L) return;

        ItemStack weapon = resolveImpactWeapon(shooter, proj);
        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(weapon, 1);
        if (layers.isEmpty()) return;

        WeaponVisualEffectId.ResolvedLayer top = layers.get(0);
        Vec3 hit = event.getRayTraceResult().getLocation();
        if (top.effect() == WeaponVisualEffectId.ASTRAL_STAR_LANCE) {
            broadcastNear(sl, (float) hit.x, (float) hit.y, (float) hit.z,
                    top.effect().id, KIND_BEAM_SIPHON, top.level(),
                    (float) shooter.getX(), (float) shooter.getEyeY() - 0.12f, (float) shooter.getZ(),
                    (float) hit.x, (float) hit.y, (float) hit.z);
        } else {
            broadcastNear(sl, (float) hit.x, (float) hit.y, (float) hit.z,
                    top.effect().id, KIND_IMPACT, top.level(),
                    (float) hit.x, (float) hit.y, (float) hit.z,
                    (float) shooter.getX(), (float) shooter.getEyeY(), (float) shooter.getZ());
        }
    }

    /** Clic droit avec une arme portant {@code army_summon} / {@code summon_army} -> Sceau Voxel #29 ! */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickRallyVisuals(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel sl)) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack.getItem())) return;

        List<WeaponVisualEffectId.ResolvedLayer> layers = WeaponVisualEffectId.resolveLayers(stack, 2);
        for (WeaponVisualEffectId.ResolvedLayer l : layers) {
            if (l.effect() == WeaponVisualEffectId.WARLORD_RALLY_SIGIL) {
                long now = sl.getGameTime();
                Long prev = LAST_RALLY_TICK.put(player.getId(), now);
                if (prev != null && now - prev < 15L) return;
                float px = (float) player.getX();
                float py = (float) (player.getY() + 0.45);
                float pz = (float) player.getZ();
                broadcastNear(sl, px, py, pz, WeaponVisualEffectId.WARLORD_RALLY_SIGIL.id, KIND_IMPACT, l.level(),
                        px, py, pz, px, py + 1.0f, pz);
                break;
            }
        }
    }

    private static List<WeaponVisualEffectId.ResolvedLayer> resolveAttackerLayers(LivingEntity attacker, ItemStack weapon, int maxLayers) {
        if (!weapon.isEmpty()) {
            return WeaponVisualEffectId.resolveLayers(weapon, maxLayers);
        }
        if (attacker instanceof Player p) {
            return WeaponVisualEffectId.resolveBareHandGauntletLayers(p, maxLayers);
        }
        return List.of();
    }

    private static ItemStack resolveImpactWeapon(LivingEntity attacker, Entity direct) {
        if (direct instanceof ThrownTrident trident) {
            ItemStack origin = trident.getPickupItemStackOrigin();
            if (!origin.isEmpty()) return origin;
        }
        if (direct instanceof AbstractArrow arrow) {
            ItemStack snap = UniversalEnchantmentHandler.resolveArrowWeapon(arrow, attacker);
            if (!snap.isEmpty()) return snap;
        }
        return attacker.getMainHandItem();
    }

    private static void broadcastNear(ServerLevel sl, float refX, float refY, float refZ,
                                      int vfxId, int kind, int intensity,
                                      float x, float y, float z,
                                      float tx, float ty, float tz) {
        DeepPayloads.WeaponVfxMessage payload = DeepPayloads.WeaponVfxMessage.create(
                vfxId, kind, intensity, x, y, z, tx, ty, tz);

        double maxDistSq = 52.0 * 52.0;
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(refX, refY, refZ) <= maxDistSq) {
                PacketDistributor.sendToPlayer(sp, payload);
            }
        }
    }
}
