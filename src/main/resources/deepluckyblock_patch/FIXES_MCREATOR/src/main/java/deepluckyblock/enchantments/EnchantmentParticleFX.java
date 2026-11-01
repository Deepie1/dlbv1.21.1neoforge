package deepluckyblock.enchantments;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * T306-ENCHANT-FX : Moteur de chorégraphies de particules thématiques serveur
 * (géométries d'arcs de lame, doubles hélices, anneaux d'onde de choc, rayons
 * grappin/siphon, météores obliques, halos de couronne et sillages de chevilles).
 *
 * Conçu pour n'obstruer jamais la caméra première personne du porteur et rester
 * ultra-léger côté réseau (throttling strict sur les effets passifs).
 */
public final class EnchantmentParticleFX {

    private EnchantmentParticleFX() {}

    public static final DustParticleOptions CRIMSON_BLOOD =
            new DustParticleOptions(new Vector3f(0.78f, 0.04f, 0.10f), 1.15f);
    public static final DustParticleOptions SCARLET_MIST =
            new DustParticleOptions(new Vector3f(0.95f, 0.16f, 0.22f), 0.90f);
    public static final DustParticleOptions GOLD_DIVINE =
            new DustParticleOptions(new Vector3f(1.00f, 0.84f, 0.22f), 1.05f);
    public static final DustParticleOptions SHADOW_VIOLET =
            new DustParticleOptions(new Vector3f(0.32f, 0.08f, 0.52f), 1.10f);
    public static final DustParticleOptions TOXIC_GREEN =
            new DustParticleOptions(new Vector3f(0.28f, 0.82f, 0.16f), 1.00f);
    public static final DustParticleOptions TEMPORAL_AMBER =
            new DustParticleOptions(new Vector3f(0.96f, 0.72f, 0.18f), 0.85f);
    public static final DustParticleOptions FROST_CYAN =
            new DustParticleOptions(new Vector3f(0.52f, 0.92f, 1.00f), 0.95f);
    public static final DustParticleOptions OVERCLOCK_CYAN =
            new DustParticleOptions(new Vector3f(0.15f, 0.95f, 0.95f), 1.05f);
    public static final DustParticleOptions STEEL_SILVER =
            new DustParticleOptions(new Vector3f(0.80f, 0.84f, 0.90f), 0.95f);
    public static final DustParticleOptions EARTH_OCHRE =
            new DustParticleOptions(new Vector3f(0.58f, 0.42f, 0.24f), 1.10f);
    public static final DustParticleOptions CLOUD_WHITE =
            new DustParticleOptions(new Vector3f(0.94f, 0.98f, 1.00f), 1.05f);
    public static final DustParticleOptions HONEY_AMBER =
            new DustParticleOptions(new Vector3f(0.98f, 0.66f, 0.08f), 1.05f);

    /** Arc d'entaille orienté devant l'attaquant (balayages, lames maîtresses). */
    public static void slashArc(LivingEntity attacker, ParticleOptions main, ParticleOptions accent,
                                double radius, int points) {
        if (attacker == null || !(attacker.level() instanceof ServerLevel sl)) return;
        try {
            Vec3 look = attacker.getLookAngle();
            Vec3 flatFwd = new Vec3(look.x, 0.0, look.z);
            if (flatFwd.lengthSqr() < 1.0e-4) flatFwd = new Vec3(0.0, 0.0, 1.0);
            flatFwd = flatFwd.normalize();
            Vec3 right = new Vec3(-flatFwd.z, 0.0, flatFwd.x);
            double baseY = attacker.getY() + attacker.getBbHeight() * 0.62;
            int n = Math.max(5, Math.min(20, points));
            double r = Math.min(5.0, Math.max(1.2, radius));
            for (int i = 0; i < n; i++) {
                double t = (double) i / (n - 1) - 0.5; // [-0.5, 0.5]
                double ang = t * 2.15; // ~123 degrees arc
                double cos = Math.cos(ang);
                double sin = Math.sin(ang);
                double px = attacker.getX() + (flatFwd.x * cos + right.x * sin) * r;
                double pz = attacker.getZ() + (flatFwd.z * cos + right.z * sin) * r;
                double py = baseY + sin * 0.18;
                sl.sendParticles(main, px, py, pz, 1, 0.03, 0.03, 0.03, 0.005);
                if (accent != null && (i & 1) == 0) {
                    sl.sendParticles(accent, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Double hélice ascendante autour d'une entité (vampirisme, bénédictions, chrono). */
    public static void doubleHelix(Entity center, ParticleOptions strandA, ParticleOptions strandB,
                                   double radius, int steps) {
        if (center == null || !(center.level() instanceof ServerLevel sl)) return;
        try {
            double cx = center.getX();
            double cy = center.getY() + 0.15;
            double cz = center.getZ();
            double h = Math.max(1.0, Math.min(3.2, center.getBbHeight() + 0.3));
            double r = Math.max(0.35, Math.min(2.2, radius));
            int n = Math.max(6, Math.min(18, steps));
            double phase = (center.tickCount * 0.18);
            for (int i = 0; i < n; i++) {
                double frac = (double) i / n;
                double ang = phase + frac * Math.PI * 2.2;
                double y = cy + frac * h;
                sl.sendParticles(strandA, cx + Math.cos(ang) * r, y, cz + Math.sin(ang) * r,
                        1, 0.01, 0.01, 0.01, 0.0);
                if (strandB != null) {
                    sl.sendParticles(strandB, cx - Math.cos(ang) * r, y, cz - Math.sin(ang) * r,
                            1, 0.01, 0.01, 0.01, 0.0);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Anneau d'onde de choc horizontal au sol ou à mi-hauteur. */
    public static void shockwaveRing(ServerLevel sl, double x, double y, double z,
                                     ParticleOptions particle, double radius, int points, double outwardSpeed) {
        if (sl == null) return;
        try {
            int n = Math.max(8, Math.min(28, points));
            double r = Math.max(0.4, Math.min(6.0, radius));
            for (int i = 0; i < n; i++) {
                double ang = (Math.PI * 2.0 * i) / n;
                double dx = Math.cos(ang);
                double dz = Math.sin(ang);
                sl.sendParticles(particle, x + dx * r, y, z + dz * r,
                        1, dx * 0.04, 0.02, dz * 0.04, outwardSpeed);
            }
        } catch (Throwable ignored) {}
    }

    /** Onde de débris de bloc circulaire (séisme, impact tellurique, atterrissage lourd). */
    public static void blockDebrisRing(ServerLevel sl, double x, double y, double z,
                                       BlockState state, double radius, int points) {
        if (sl == null || state == null) return;
        shockwaveRing(sl, x, y + 0.08, z, new BlockParticleOption(ParticleTypes.BLOCK, state), radius, points, 0.08);
    }

    /** Rayon / corde tendue de particules entre deux positions (grappin, ricochet, foudre, sangsue). */
    public static void tetherBeam(ServerLevel sl, Vec3 from, Vec3 to, ParticleOptions main,
                                  ParticleOptions spark, double stepLen) {
        if (sl == null || from == null || to == null) return;
        try {
            Vec3 delta = to.subtract(from);
            double dist = Math.min(32.0, delta.length());
            if (dist < 0.15) return;
            Vec3 dir = delta.normalize();
            double step = Math.max(0.45, stepLen);
            int idx = 0;
            for (double d = 0.4; d <= dist; d += step) {
                Vec3 p = from.add(dir.scale(d));
                sl.sendParticles(main, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
                if (spark != null && (idx++ % 3 == 0)) {
                    sl.sendParticles(spark, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.01);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Traînée météoritique oblique tombant du ciel jusqu'au point d'impact. */
    public static void meteorSkyfall(ServerLevel sl, Vec3 impact) {
        if (sl == null || impact == null) return;
        try {
            Vec3 skyStart = impact.add(2.8, 11.0, -2.2);
            Vec3 delta = impact.subtract(skyStart);
            int steps = 16;
            for (int i = 0; i <= steps; i++) {
                double f = (double) i / steps;
                Vec3 p = skyStart.add(delta.scale(f));
                sl.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.18, 0.18, 0.18, 0.02);
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 1, 0.12, 0.12, 0.12, 0.005);
                if ((i & 1) == 0) {
                    sl.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.10, 0.10, 0.10, 0.0);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Sillage discret aux chevilles (derrière le regard pour ne jamais gêner la vue). */
    public static void ankleTrail(LivingEntity entity, ParticleOptions particle, int count, double speed) {
        if (entity == null || !(entity.level() instanceof ServerLevel sl)) return;
        try {
            Vec3 look = entity.getLookAngle();
            double bx = entity.getX() - look.x * 0.28;
            double by = entity.getY() + 0.12;
            double bz = entity.getZ() - look.z * 0.28;
            sl.sendParticles(particle, bx, by, bz, Math.max(1, Math.min(8, count)), 0.18, 0.06, 0.18, speed);
        } catch (Throwable ignored) {}
    }

    /** Halo de couronne discret au-dessus de la tête (Roi, Général, Esprit Mystique, Garde Radieuse). */
    public static void crownHalo(LivingEntity entity, ParticleOptions particle, int points) {
        if (entity == null || !(entity.level() instanceof ServerLevel sl)) return;
        try {
            double y = entity.getY() + entity.getBbHeight() + 0.28;
            int n = Math.max(4, Math.min(12, points));
            double phase = entity.tickCount * 0.12;
            for (int i = 0; i < n; i++) {
                double ang = phase + (Math.PI * 2.0 * i) / n;
                sl.sendParticles(particle,
                        entity.getX() + Math.cos(ang) * 0.42,
                        y,
                        entity.getZ() + Math.sin(ang) * 0.42,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        } catch (Throwable ignored) {}
    }

    /** Rubans d'ailes derrière les épaules (Élytres, Endurance Ailée). */
    public static void wingRibbons(LivingEntity entity, ParticleOptions particle) {
        if (entity == null || !(entity.level() instanceof ServerLevel sl)) return;
        try {
            Vec3 look = entity.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0.0, look.x);
            if (right.lengthSqr() < 1.0e-4) right = new Vec3(1.0, 0.0, 0.0);
            right = right.normalize();
            Vec3 back = entity.position().add(-look.x * 0.45, entity.getBbHeight() * 0.65, -look.z * 0.45);
            Vec3 leftTip = back.add(right.scale(-0.65));
            Vec3 rightTip = back.add(right.scale(0.65));
            sl.sendParticles(particle, leftTip.x, leftTip.y, leftTip.z, 1, 0.04, 0.04, 0.04, 0.01);
            sl.sendParticles(particle, rightTip.x, rightTip.y, rightTip.z, 1, 0.04, 0.04, 0.04, 0.01);
        } catch (Throwable ignored) {}
    }

    /** Explosion ponctuelle centrée sur le buste d'une entité. */
    public static void burstOnEntity(Entity target, ParticleOptions particle, int count, double spread, double speed) {
        if (target == null || !(target.level() instanceof ServerLevel sl)) return;
        try {
            sl.sendParticles(particle,
                    target.getX(), target.getY() + target.getBbHeight() * 0.52, target.getZ(),
                    Math.max(1, Math.min(36, count)), spread, spread, spread, speed);
        } catch (Throwable ignored) {}
    }

    /**
     * T307-CLOUD : Coussin de nuages cotonneux directement sous les pieds du joueur
     * (Marcheur des Nuages triple saut, Marche Aérienne, Foulée des Nuages, rebonds aériens).
     */
    public static void cloudCushion(ServerLevel sl, double x, double y, double z, double radius, boolean radiant) {
        if (sl == null) return;
        try {
            double r = Math.max(0.35, Math.min(2.2, radius));
            // Cœur cotonneux dense juste sous les semelles
            sl.sendParticles(ParticleTypes.CLOUD, x, y, z, radiant ? 12 : 7, r * 0.36, 0.04, r * 0.36, 0.012);
            // Couronne horizontale de nuage qui s'évase doucement
            int pts = radiant ? 14 : 9;
            for (int i = 0; i < pts; i++) {
                double ang = (Math.PI * 2.0 * i) / pts;
                double px = x + Math.cos(ang) * r;
                double pz = z + Math.sin(ang) * r;
                sl.sendParticles(ParticleTypes.CLOUD, px, y + 0.02, pz, 1, 0.05, 0.02, 0.05, 0.008);
                if (radiant && (i & 1) == 0) {
                    sl.sendParticles(CLOUD_WHITE, px * 0.5 + x * 0.5, y + 0.03, pz * 0.5 + z * 0.5, 1, 0.03, 0.02, 0.03, 0.0);
                }
            }
            if (radiant) {
                sl.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.05, z, 6, r * 0.45, 0.05, r * 0.45, 0.01);
            }
        } catch (Throwable ignored) {}
    }
}
