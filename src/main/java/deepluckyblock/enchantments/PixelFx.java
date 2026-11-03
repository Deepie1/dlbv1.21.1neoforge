package deepluckyblock.enchantments;

import deepluckyblock.network.DeepPayloads;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * T360-NO-VANILLA-PARTICLES : <b>unique</b> sortie visuelle des enchantements côté serveur.
 *
 * <p><b>Problème résolu.</b> Tous les effets cosmétiques (halos de couronne, arcs d'entaille,
 * traînées de chevilles, ondes de choc, rubans d'ailes, colonnes, hélices…) appelaient
 * {@code ServerLevel#sendParticles} avec des particules <b>Minecraft</b> : le joueur voyait donc
 * des grappes de sprites vanilla au lieu des mini-cubes 3D du mod — et, comme ces salves sont
 * ponctuelles, le halo « apparaissait une fois puis disparaissait ».</p>
 *
 * <p><b>Solution.</b> Cette classe expose une méthode <b>de même signature</b> que
 * {@code sendParticles} : {@link #send(ServerLevel, ParticleOptions, double, double, double, int,
 * double, double, double, double)}. Tout le code historique a été réécrit mécaniquement vers
 * cette méthode, donc la géométrie d'origine (anneaux, arcs, hélices, rayons) est conservée à
 * l'identique — mais chaque point devient un vrai mini-cube 3D multi-taille envoyé par
 * {@link DeepPayloads.PixelPuffMessage} au moteur client {@code WeaponWorldVfxManager}.</p>
 *
 * <p><b>Garde-fous</b> : budget de paquets par tick, déduplication des positions identiques dans
 * le même tick, aucune exception propagée (un effet cosmétique ne doit jamais casser un tick), et
 * zéro particule vanilla émise, quelle que soit la {@link ParticleOptions} reçue (elle ne sert
 * qu'à choisir une <b>couleur</b>).</p>
 */
public final class PixelFx {

    private PixelFx() {}

    /** Portée d'envoi d'une salve (blocs). */
    private static final double MAX_DIST_SQR = 64.0 * 64.0;

    /** Nombre maximum de cubes par appel (une « particule » vanilla = 1 cube, jamais plus de 12). */
    private static final int MAX_CUBES_PER_CALL = 12;

    /** Budget de salves par tick (anti-spam réseau si un effet boucle sur plusieurs joueurs). */
    private static final int MAX_PUFFS_PER_TICK = 140;

    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetLeft = MAX_PUFFS_PER_TICK;

    /** Déduplication : deux salves identiques dans le MÊME tick ne produisent qu'un seul paquet. */
    private static long lastKey = Long.MIN_VALUE;
    private static long lastKeyTick = Long.MIN_VALUE;

    /** Couleur de repli (blanc chaud) quand le type de particule est inconnu. */
    private static final int DEFAULT_RGB = 0xE8E3FF;

    private static final Map<ParticleOptions, Integer> COLOR_CACHE = new IdentityHashMap<>(64);

    /** Accès réflexif à la couleur d'un {@link DustParticleOptions} (signature variable selon la version). */
    private static Method dustColorMethod;
    private static boolean dustColorResolved;

    // ==================================================================================
    // API « drop-in » de ServerLevel#sendParticles
    // ==================================================================================

    /** Remplace {@code level.sendParticles(type, x, y, z, count, dx, dy, dz, speed)}. */
    public static void send(ServerLevel level, ParticleOptions type,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        try {
            if (level == null) return;
            puff(level, null, type, x, y, z, count, dx, dy, dz, speed);
        } catch (Throwable ignored) {
            // Un effet cosmétique ne doit jamais casser un tick serveur.
        }
    }

    /** Remplace {@code level.sendParticles(type, force, x, y, z, count, dx, dy, dz, speed)}. */
    public static void send(ServerLevel level, ParticleOptions type, boolean force,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        send(level, type, x, y, z, count, dx, dy, dz, speed);
    }

    /** Remplace {@code level.sendParticles(viewer, type, x, y, z, count, dx, dy, dz, speed)}. */
    public static void send(ServerLevel level, ServerPlayer viewer, ParticleOptions type,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        try {
            if (level == null) return;
            puff(level, viewer, type, x, y, z, count, dx, dy, dz, speed);
        } catch (Throwable ignored) {
        }
    }

    /** Remplace {@code level.sendParticles(viewer, type, force, x, y, z, count, dx, dy, dz, speed)}. */
    public static void send(ServerLevel level, ServerPlayer viewer, ParticleOptions type, boolean force,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        send(level, viewer, type, x, y, z, count, dx, dy, dz, speed);
    }

    /**
     * Variante {@link net.minecraft.world.level.LevelAccessor} : le code historique des procédures
     * (lucky blocks, structures, advancements) ne manipule souvent qu'un {@code LevelAccessor}.
     * On accepte le type large et on ignore simplement l'appel si le niveau n'est pas serveur :
     * la signature reste identique à {@code sendParticles}, donc la réécriture est mécanique.
     */
    public static void send(net.minecraft.world.level.LevelAccessor level, ParticleOptions type,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            send(sl, type, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** Variante {@code LevelAccessor} + forçage. */
    public static void send(net.minecraft.world.level.LevelAccessor level, ParticleOptions type, boolean force,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            send(sl, type, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** Variante {@code LevelAccessor} ciblant un joueur précis. */
    public static void send(net.minecraft.world.level.LevelAccessor level, ServerPlayer viewer,
                            ParticleOptions type,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            send(sl, viewer, type, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** Variante {@code LevelAccessor} ciblant un joueur précis + forçage. */
    public static void send(net.minecraft.world.level.LevelAccessor level, ServerPlayer viewer,
                            ParticleOptions type, boolean force,
                            double x, double y, double z,
                            int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            send(sl, viewer, type, x, y, z, count, dx, dy, dz, speed);
        }
    }

    // ==================================================================================
    // Formes explicites (géométries qui méritent mieux qu'une salve ponctuelle)
    // ==================================================================================

    /**
     * T360-HALO-CONTINU : anneau de pixels (couronne) autour d'une entité. Contrairement aux
     * anciennes salves d'une seule frame, chaque cube vit assez longtemps pour que deux émissions
     * successives se chevauchent : la couronne ne « clignote » plus.
     */
    public static void ring(ServerLevel level, double cx, double cy, double cz,
                            double radius, int points, int rgb, int lifeTicks) {
        try {
            if (level == null) return;
            int n = Mth.clamp(points, 4, 16);
            float life = Mth.clamp(lifeTicks, 8, 60);
            for (int i = 0; i < n; i++) {
                double ang = (Math.PI * 2.0 * i) / n + level.getGameTime() * 0.09;
                double x = cx + Math.cos(ang) * radius;
                double z = cz + Math.sin(ang) * radius;
                int shade = (i % 2 == 0) ? rgb : brighten(rgb, 0.55f);
                emit(level, null, shade, x, cy, z, 1, 0.02, 0.02, 0.02, 0.004, (int) life);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Couronne au-dessus d'une entité vivante (utilisée par les halos d'enchantements). */
    public static void haloFor(net.minecraft.world.entity.Entity entity, int rgb, int points) {
        if (entity == null) return;
        if (!(entity.level() instanceof ServerLevel sl)) return;
        double y = entity.getY() + entity.getBbHeight() + 0.26;
        ring(sl, entity.getX(), y, entity.getZ(), 0.44, points, rgb, 18);
    }

    // ==================================================================================
    // Interne
    // ==================================================================================

    private static void puff(ServerLevel level, ServerPlayer only, ParticleOptions type,
                             double x, double y, double z,
                             int count, double dx, double dy, double dz, double speed) {
        int cubes = Mth.clamp(count, 1, MAX_CUBES_PER_CALL);
        int rgb = colorOf(type);

        // La dispersion vanilla (dx, dy, dz) devient le rayon de la salve ; la vitesse est conservée.
        float spread = (float) Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        spread = Mth.clamp(spread, 0.0f, 4.0f);

        // Durée de vie : les effets larges/flous vivent plus longtemps, les étincelles moins.
        int life = 10 + cubes * 2 + (int) (spread * 6.0f);

        emit(level, only, rgb, x, y, z, cubes, spread, spread, spread, speed, life);
    }

    private static void emit(ServerLevel level, ServerPlayer only, int rgb,
                             double x, double y, double z,
                             int cubes, double dx, double dy, double dz,
                             double speed, int life) {
        long key = (((long) Math.round(x * 16.0)) * 0x9E3779B1L)
                ^ (((long) Math.round(y * 16.0)) * 0x85EBCA77L)
                ^ (((long) Math.round(z * 16.0)) * 0xC2B2AE3DL)
                ^ (rgb & 0xFFFFFFL) ^ ((long) cubes << 40);
        if (!takeBudget(level, key)) return;

        DeepPayloads.PixelPuffMessage payload = DeepPayloads.PixelPuffMessage.create(
                rgb, cubes, x, y, z,
                (float) Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))),
                (float) Mth.clamp(speed, 0.0, 2.0),
                life);

        if (only != null) {
            if (only.distanceToSqr(x, y, z) <= MAX_DIST_SQR) {
                PacketDistributor.sendToPlayer(only, payload);
            }
            return;
        }

        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(x, y, z) <= MAX_DIST_SQR) {
                PacketDistributor.sendToPlayer(sp, payload);
            }
        }
    }

    private static boolean takeBudget(ServerLevel level, long key) {
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            budgetLeft = MAX_PUFFS_PER_TICK;
            lastKey = Long.MIN_VALUE;
            lastKeyTick = Long.MIN_VALUE;
        }
        // Même position quantifiée + même couleur + même tick = doublon exact : on n'envoie rien.
        if (now == lastKeyTick && key == lastKey) return false;
        lastKey = key;
        lastKeyTick = now;
        if (budgetLeft <= 0) return false;
        budgetLeft--;
        return true;
    }

    /** Éclaircit une couleur (0xRRGGBB) vers le blanc. */
    private static int brighten(int rgb, float amount) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amount));
        g = Math.min(255, Math.round(g + (255 - g) * amount));
        b = Math.min(255, Math.round(b + (255 - b) * amount));
        return (r << 16) | (g << 8) | b;
    }

    // ==================================================================================
    // Traduction ParticleOptions -> couleur de pixel 3D
    // ==================================================================================

    /**
     * Le type de particule vanilla n'est plus <b>jamais</b> rendu : il ne sert qu'à choisir la
     * teinte du mini-cube 3D correspondant (braise orange, étincelle cyan, âme violette…).
     */
    public static int colorOf(ParticleOptions type) {
        if (type == null) return DEFAULT_RGB;
        Integer cached = COLOR_CACHE.get(type);
        if (cached != null) return cached;

        int rgb = DEFAULT_RGB;
        if (type instanceof DustParticleOptions dust) {
            rgb = dustColor(dust, DEFAULT_RGB);
        } else if (type instanceof BlockParticleOption blockFx) {
            // T360 : les éclats de bloc (terre, verre, pierre) prennent la couleur de la carte du bloc.
            rgb = blockColor(blockFx, DEFAULT_RGB);
        } else {
            rgb = tableColor(type, DEFAULT_RGB);
        }

        if (COLOR_CACHE.size() > 512) COLOR_CACHE.clear();
        COLOR_CACHE.put(type, rgb);
        return rgb;
    }

    /** Couleur dérivée d'un éclat de bloc : la teinte de carte du bloc cassé. */
    private static int blockColor(BlockParticleOption option, int fallback) {
        try {
            var state = option.getState();
            if (state == null) return fallback;
            int col = state.getBlock().defaultMapColor().col & 0xFFFFFF;
            // Les couleurs de carte sont volontairement ternes : on les ravive pour rester lisible.
            int r = Math.min(255, ((col >>> 16) & 0xFF) * 3 / 2);
            int g = Math.min(255, ((col >>> 8) & 0xFF) * 3 / 2);
            int b = Math.min(255, (col & 0xFF) * 3 / 2);
            return (r << 16) | (g << 8) | b;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int tableColor(ParticleOptions type, int fallback) {
        if (type == ParticleTypes.FLAME || type == ParticleTypes.SMALL_FLAME
                || type == ParticleTypes.LAVA || type == ParticleTypes.FIREWORK) {
            return 0xFF8A2B;
        }
        if (type == ParticleTypes.SOUL_FIRE_FLAME) return 0x63E7FF;
        if (type == ParticleTypes.ELECTRIC_SPARK) return DeepElement.THUNDER.primary;
        if (type == ParticleTypes.END_ROD || type == ParticleTypes.FLASH || type == ParticleTypes.GLOW) {
            return 0xFFF6D0;
        }
        if (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT) return 0xFFE9A0;
        if (type == ParticleTypes.ENCHANT) return 0xC9A8FF;
        if (type == ParticleTypes.PORTAL || type == ParticleTypes.REVERSE_PORTAL) {
            return DeepElement.VOID.primary;
        }
        if (type == ParticleTypes.DRAGON_BREATH) return DeepElement.VOID.highlight;
        if (type == ParticleTypes.WITCH) return DeepElement.POISON.primary;
        if (type == ParticleTypes.SCULK_SOUL) return DeepElement.SHADOW.highlight;
        if (type == ParticleTypes.SOUL) return 0x63D6E8;
        if (type == ParticleTypes.SCULK_CHARGE_POP) return 0x5F7A9E;
        if (type == ParticleTypes.SPLASH || type == ParticleTypes.BUBBLE
                || type == ParticleTypes.BUBBLE_COLUMN_UP || type == ParticleTypes.BUBBLE_POP
                || type == ParticleTypes.NAUTILUS || type == ParticleTypes.CURRENT_DOWN
                || type == ParticleTypes.FALLING_HONEY) {
            return 0x6FC9FF;
        }
        if (type == ParticleTypes.SWEEP_ATTACK || type == ParticleTypes.GUST
                || type == ParticleTypes.SMALL_GUST || type == ParticleTypes.GUST_EMITTER_SMALL
                || type == ParticleTypes.WHITE_SMOKE) {
            return 0xE8F6FF;
        }
        if (type == ParticleTypes.CLOUD || type == ParticleTypes.POOF
                || type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE
                || type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.WHITE_ASH
                || type == ParticleTypes.ASH) {
            return 0xD8DEE6;
        }
        if (type == ParticleTypes.HAPPY_VILLAGER || type == ParticleTypes.COMPOSTER
                || type == ParticleTypes.HEART || type == ParticleTypes.TOTEM_OF_UNDYING) {
            return 0x9CFF7A;
        }
        if (type == ParticleTypes.SNOWFLAKE) return 0xDFF7FF;
        if (type == ParticleTypes.SQUID_INK) return 0x22304A;
        if (type == ParticleTypes.ANGRY_VILLAGER) return 0xE0574C;
        if (type == ParticleTypes.WAX_ON) return 0xFFC98A;
        if (type == ParticleTypes.CHERRY_LEAVES) return 0xFFB7D5;
        if (type == ParticleTypes.ITEM_SLIME) return 0x8BD46B;
        if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) return 0xFFD27A;
        if (type == ParticleTypes.SONIC_BOOM) return 0x9BF6FF;
        return fallback;
    }

    /**
     * Couleur d'un {@code DustParticleOptions} (les dégradés « divine gold », « toxic green »… du mod)
     * lue par réflexion : la méthode de lecture a changé de signature entre 1.20 et 1.21, et cette
     * classe ne doit <b>jamais</b> empêcher la compilation ou le chargement.
     */
    private static int dustColor(DustParticleOptions dust, int fallback) {
        try {
            if (!dustColorResolved) {
                dustColorResolved = true;
                for (String name : new String[]{"getColor", "getRgb"}) {
                    try {
                        dustColorMethod = DustParticleOptions.class.getMethod(name);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
            if (dustColorMethod == null) return fallback;
            Object value = dustColorMethod.invoke(dust);
            if (value instanceof Integer i) return i & 0xFFFFFF;
            if (value instanceof Vector3f v) {
                int r = Mth.clamp(Math.round(v.x * 255.0f), 0, 255);
                int g = Mth.clamp(Math.round(v.y * 255.0f), 0, 255);
                int b = Mth.clamp(Math.round(v.z * 255.0f), 0, 255);
                return (r << 16) | (g << 8) | b;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}
