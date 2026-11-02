package deepluckyblock.enchantments;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.DeepElement;
import deepluckyblock.enchantments.WeaponVfxSubtle;
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

    // T346-VFX-TEST : kinds exposés publiquement pour la commande de test /dlbvfx
    public static final int KIND_SWING_SLASH       = 0;
    public static final int KIND_IMPACT            = 1;
    public static final int KIND_BEAM_SIPHON       = 2;
    public static final int KIND_PROJECTILE_ATTACH = 3;

    private static final Map<Integer, Long> LAST_ATTACKER_HIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_PROJECTILE_HIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_CRIT_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_RALLY_TICK = new ConcurrentHashMap<>();
    /** T351-BIG-BONUS : dernier tick où une GROSSE animation (critique) a été jouée, par attaquant. */
    private static final Map<Integer, Long> LAST_BIG_ANIM_TICK = new ConcurrentHashMap<>();
    /** T349-VFX-FREQUENCY : cooldown réel et aléatoire par (attaquant, effet). */
    private static final Map<String, Long> EFFECT_COOLDOWN = new ConcurrentHashMap<>();

    /** T351 : attaque NORMALE -> 5 % de chance de déclencher l'animation pixel. */
    private static final int NORMAL_TRIGGER_PERCENT = 5;
    /** T351 : coup CRITIQUE -> 66 % de chance. */
    private static final int CRIT_TRIGGER_PERCENT = 66;
    /** T351 : part des déclenchements qui jouent un accent discret. */
    private static final int SUBTLE_SHARE_PERCENT = 90;
    /** T351-BIG-BONUS : une grosse animation (les 10 % restants) amplifie les dégâts de 5 %. */
    private static final float BIG_ANIM_DAMAGE_BONUS = 1.05f;

    private WeaponVfxServerListener() {}

    /** Appelé par {@code UniversalEnchantmentHandler} lors d'un coup critique mêlée (vanilla ou custom). */
    public static void notifyMeleeCrit(Player attacker, Entity target,
                                       WeaponVisualEffectId critStyle, int intensity) {
        if (attacker == null || target == null) return;
        if (!(attacker.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        LAST_CRIT_TICK.put(attacker.getId(), now);

        WeaponVisualEffectId fx = (critStyle != null) ? critStyle : WeaponVisualEffectId.GOLDEN_CRIT_STAR;

        // T351 : sur un coup critique, 66 % de chance de déclencher l'animation pixel.
        if (sl.random.nextInt(100) >= CRIT_TRIGGER_PERCENT) return;
        String critKey = "crit:" + attacker.getId() + ":" + fx.id;
        Long critReady = EFFECT_COOLDOWN.get(critKey);
        if (critReady != null && now < critReady) return;
        EFFECT_COOLDOWN.put(critKey, now + 16L + sl.random.nextInt(20));

        float cx = (float) target.getX();
        float cy = (float) (target.getY() + target.getBbHeight() * 0.55);
        float cz = (float) target.getZ();

        // T351 : la plupart des critiques jouent seulement un accent discret (bien plus subtil).
        if (playSubtleAccent(sl, attacker.getMainHandItem(), fx, cx, cy, cz,
                (float) attacker.getX(), (float) attacker.getEyeY(), (float) attacker.getZ())) {
            return;
        }

        // Grosse animation jouée -> le dégât de ce coup sera amplifié de 5 % (voir onLivingHurtVisuals).
        LAST_BIG_ANIM_TICK.put(attacker.getId(), now);
        broadcastNear(sl, cx, cy, cz, fx.id, KIND_IMPACT, Math.max(2, intensity),
                cx, cy, cz, (float) attacker.getX(), (float) attacker.getEyeY(), (float) attacker.getZ(),
                paletteOf(attacker.getMainHandItem(), fx));
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
        TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 1, () -> {
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

    /**
     * T349-VFX-FREQUENCY : les effets d'attaque partaient à CHAQUE coup (et se répétaient). On applique
     * maintenant un vrai cooldown aléatoire par effet (26..59 ticks) + un % de chance de déclenchement.
     * Les effets critiques gardent leur chance naturelle (ils sont déjà rares) mais restent throttlés.
     */
    /**
     * T351-SUBTLE : joue un accent discret (2..9 mini-pixels) au lieu de la grosse animation.
     * Retourne {@code true} si un accent a effectivement été envoyé.
     */
    private static boolean playSubtleAccent(ServerLevel sl, ItemStack weapon, WeaponVisualEffectId fx,
                                            float tx, float ty, float tz,
                                            float ax, float ay, float az) {
        if (sl.random.nextInt(100) >= SUBTLE_SHARE_PERCENT) return false;
        int accentIndex = pickAccentIndex(sl, weapon, fx);
        int palette = paletteOf(weapon, fx);
        // Direction de frappe : de l'attaquant vers la cible, prolongée au-delà de la cible.
        float dx = tx - ax, dy = ty - ay, dz = tz - az;
        float ex = tx + dx * 0.35f;
        float ey = ty + dy * 0.35f;
        float ez = tz + dz * 0.35f;
        broadcastNear(sl, tx, ty, tz, 0, WeaponVfxSubtle.NET_KIND_BASE + accentIndex, 1,
                tx, ty, tz, ex, ey, ez, palette);
        return true;
    }

    /**
     * T351-SUBTLE-ECHO : choisit l'accent discret à jouer.
     *
     * <p>Une fois sur deux on prend l'<b>écho de la grosse animation</b> de l'arme (même arc, même
     * colonne, même éclair... en minuscule) : le joueur reconnaît la signature de son enchantement
     * même quand la grande forme ne part pas. L'autre moitié pioche dans les accents élémentaires,
     * pour éviter toute répétition.</p>
     */
    private static int pickAccentIndex(ServerLevel sl, ItemStack weapon, WeaponVisualEffectId fx) {
        if (fx != null && sl.random.nextInt(100) < 50) {
            List<WeaponVfxSubtleEcho.Echo> pool = WeaponVfxSubtleEcho.forEffect(fx.id);
            if (!pool.isEmpty()) {
                return WeaponVfxSubtle.COUNT + pool.get(sl.random.nextInt(pool.size())).index();
            }
        }
        DeepElement el = pickElement(sl, weapon, fx);
        return WeaponVfxSubtle.pick(el, sl.random.nextLong()).index();
    }

    /** T351-ELEMENT-BLEND : masque des éléments de l'arme (0 si inconnu) pour mélanger les couleurs. */
    private static int paletteOf(ItemStack weapon, WeaponVisualEffectId fx) {
        int mask = WeaponVisualEffectId.elementMaskOf(weapon);
        if (mask == 0 && fx != null) mask = 1 << fx.element.ordinal();
        return mask;
    }

    /** Famille élémentaire à utiliser : une des familles portées par l'arme (variété), sinon celle de l'effet. */
    private static DeepElement pickElement(ServerLevel sl, ItemStack weapon, WeaponVisualEffectId fx) {
        List<DeepElement> els = WeaponVisualEffectId.elementsOf(weapon);
        if (els.isEmpty()) return (fx == null) ? DeepElement.METAL : fx.element;
        return els.get(sl.random.nextInt(els.size()));
    }

    private static boolean shouldPlay(int attackerId, WeaponVisualEffectId fx, ServerLevel sl, boolean crit) {
        long now = sl.getGameTime();
        String key = attackerId + ":" + fx.id;
        Long ready = EFFECT_COOLDOWN.get(key);
        if (ready != null && now < ready) return false;

        // T351 : 5 % sur une attaque normale, 66 % sur un coup critique.
        int chance = crit ? CRIT_TRIGGER_PERCENT : NORMAL_TRIGGER_PERCENT;
        if (sl.random.nextInt(100) >= chance) {
            // Nouvelle tentative possible très vite : on ne veut pas « perdre » un long cooldown sur un jet raté
            EFFECT_COOLDOWN.put(key, now + 4L);
            return false;
        }
        int cooldown = crit
                ? 18 + sl.random.nextInt(22)
                : 45 + sl.random.nextInt(45);
        EFFECT_COOLDOWN.put(key, now + cooldown);
        if (EFFECT_COOLDOWN.size() > 2048) {
            EFFECT_COOLDOWN.clear();
        }
        return true;
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
            LAST_BIG_ANIM_TICK.clear();
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
        // T351-ELEMENT-BLEND : palette élémentaire de l'arme (feu + eau -> les deux teintes ensemble).
        int palette = paletteOf(weapon, layers.get(0).effect());

        // Critique à l'arc / arbalète : étoile critique pixel 3D dédiée si pas déjà en couche 0
        if (arrowCrit && layers.get(0).effect().category != WeaponVisualEffectId.Category.CRIT
                && sl.random.nextInt(100) < CRIT_TRIGGER_PERCENT) {
            if (!playSubtleAccent(sl, weapon, WeaponVisualEffectId.GOLDEN_CRIT_STAR, tx, ty, tz, ax, ay, az)) {
                broadcastNear(sl, tx, ty, tz, WeaponVisualEffectId.GOLDEN_CRIT_STAR.id, KIND_IMPACT, 2,
                        tx, ty, tz, ax, ay, az, palette);
            }
        }

        // T349-VFX-FREQUENCY : un seul effet par coup (rarement deux) au lieu de toute la pile
        int layerBudget = (sl.random.nextInt(100) < 22) ? 2 : 1;
        // Le critique a-t-il déjà lancé une grosse animation ? Alors le +5 % est appliqué tout de suite.
        boolean bigAnimBonusApplied = (now - LAST_BIG_ANIM_TICK.getOrDefault(attacker.getId(), -100L)) <= 2L;
        if (bigAnimBonusApplied) {
            event.setAmount(event.getAmount() * BIG_ANIM_DAMAGE_BONUS);
        }
        for (int i = 0; i < layers.size(); i++) {
            if (layerBudget <= 0) break;
            WeaponVisualEffectId.ResolvedLayer layer = layers.get(i);
            WeaponVisualEffectId fx = layer.effect();

            // Évite uniquement de doubler exactement le même effet critique si notifyMeleeCrit vient de le lancer
            if (fx.category == WeaponVisualEffectId.Category.CRIT && meleeCritJustFired && i == 0) {
                continue;
            }
            boolean critHit = arrowCrit || meleeCritJustFired
                    || fx.category == WeaponVisualEffectId.Category.CRIT;
            if (!shouldPlay(attacker.getId(), fx, sl, critHit)) {
                continue;
            }
            // T351 : la majorité des déclenchements devient un accent discret (jamais englobant).
            if (playSubtleAccent(sl, weapon, fx, tx, ty, tz, ax, ay, az)) {
                layerBudget--;
                continue;
            }
            layerBudget--;

            // T351-BIG-BONUS : les 10 % de grosses animations amplifient de 5 % les dégâts subis
            // (une seule fois par coup, même si deux couches partent).
            if (!bigAnimBonusApplied) {
                bigAnimBonusApplied = true;
                event.setAmount(event.getAmount() * BIG_ANIM_DAMAGE_BONUS);
            }

            // T351 : l'entaille est désormais elle aussi décidée par le serveur (5 % / 66 %),
            // ce qui évite tout double déclenchement client + serveur.
            if (!isProjectileHit) {
                broadcastNear(sl, ax, ay, az, fx.id, KIND_SWING_SLASH, layer.level(),
                        ax, ay, az, tx, ty, tz, palette);
            }

            if (fx == WeaponVisualEffectId.CRIMSON_SIPHON) {
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                        tx, ty, tz, ax, ay, az, palette);
            } else if (fx == WeaponVisualEffectId.ASTRAL_STAR_LANCE && attacker.distanceToSqr(target) > 9.0) {
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_BEAM_SIPHON, layer.level(),
                        ax, (float) attacker.getEyeY() - 0.12f, az, tx, ty, tz, palette);
            } else {
                int boost = (arrowCrit && fx.category == WeaponVisualEffectId.Category.CRIT) ? layer.level() + 1 : layer.level();
                broadcastNear(sl, tx, ty, tz, fx.id, KIND_IMPACT, boost,
                        tx, ty, tz, ax, ay, az, palette);
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

        if (!shouldPlay(shooter.getId(), layers.get(0).effect(), sl, false)) return;

        WeaponVisualEffectId.ResolvedLayer top = layers.get(0);
        int palette = paletteOf(weapon, top.effect());
        Vec3 hit = event.getRayTraceResult().getLocation();
        if (top.effect() == WeaponVisualEffectId.ASTRAL_STAR_LANCE) {
            broadcastNear(sl, (float) hit.x, (float) hit.y, (float) hit.z,
                    top.effect().id, KIND_BEAM_SIPHON, top.level(),
                    (float) shooter.getX(), (float) shooter.getEyeY() - 0.12f, (float) shooter.getZ(),
                    (float) hit.x, (float) hit.y, (float) hit.z, palette);
        } else {
            broadcastNear(sl, (float) hit.x, (float) hit.y, (float) hit.z,
                    top.effect().id, KIND_IMPACT, top.level(),
                    (float) hit.x, (float) hit.y, (float) hit.z,
                    (float) shooter.getX(), (float) shooter.getEyeY(), (float) shooter.getZ(), palette);
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
                if (prev != null && now - prev < 40L) return; // T349 : sceau moins spammable
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

    /**
     * T346-VFX-TEST : déclenchement MANUEL d'un effet visuel (utilisé par {@code /dlbvfx}).
     * Emprunte exactement le même paquet S2C que les déclencheurs de jeu réels, donc ce que la
     * commande affiche est rigoureusement identique à ce que le joueur voit en combat.
     */
    public static void playDebug(ServerPlayer player, WeaponVisualEffectId fx, int kind, int intensity,
                                 Vec3 from, Vec3 to) {
        if (player == null || fx == null) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        int lvl = Math.max(1, Math.min(12, intensity));
        if (kind == KIND_PROJECTILE_ATTACH) {
            // to.x porte l'identifiant d'entité du projectile à suivre (comme le vrai chemin réseau)
            broadcastNear(sl, (float) from.x, (float) from.y, (float) from.z, fx.id, kind, lvl,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) to.x, 0.0f, 0.0f);
            return;
        }
        broadcastNear(sl, (float) to.x, (float) to.y, (float) to.z, fx.id, kind, lvl,
                (float) from.x, (float) from.y, (float) from.z,
                (float) to.x, (float) to.y, (float) to.z);
    }

    /** T351 : joue un accent discret précis (test via {@code /dlbvfx subtle}). */
    public static void playSubtleDebug(ServerPlayer player, WeaponVfxSubtle.Subtle accent,
                                       Vec3 from, Vec3 to) {
        if (accent == null) return;
        playSubtleDebug(player, accent.index(), from, to);
    }

    /**
     * T351-SUBTLE-ECHO : joue un accent par son index global ({@code 0..49} élémentaires,
     * {@code 50..99} échos de grosses animations).
     */
    public static void playSubtleDebug(ServerPlayer player, int accentIndex, Vec3 from, Vec3 to) {
        if (player == null) return;
        if (!(player.level() instanceof ServerLevel sl)) return;
        broadcastNear(sl, (float) to.x, (float) to.y, (float) to.z,
                0, WeaponVfxSubtle.NET_KIND_BASE + accentIndex, 1,
                (float) from.x, (float) from.y, (float) from.z,
                (float) to.x, (float) to.y, (float) to.z);
    }

    private static void broadcastNear(ServerLevel sl, float refX, float refY, float refZ,
                                      int vfxId, int kind, int intensity,
                                      float x, float y, float z,
                                      float tx, float ty, float tz) {
        broadcastNear(sl, refX, refY, refZ, vfxId, kind, intensity, x, y, z, tx, ty, tz, 0);
    }

    private static void broadcastNear(ServerLevel sl, float refX, float refY, float refZ,
                                      int vfxId, int kind, int intensity,
                                      float x, float y, float z,
                                      float tx, float ty, float tz, int paletteMask) {
        DeepPayloads.WeaponVfxMessage payload = DeepPayloads.WeaponVfxMessage.create(
                vfxId, kind, intensity, x, y, z, tx, ty, tz, paletteMask);

        double maxDistSq = 52.0 * 52.0;
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(refX, refY, refZ) <= maxDistSq) {
                PacketDistributor.sendToPlayer(sp, payload);
            }
        }
    }
}
