package deepluckyblock.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.List;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Petites particules BLANCHES qui s'échappent de temps en temps des items de
 * rang ETERNAL (au sol ou tenus en main).
 *
 * <p>Détection : le nom d'un item ETERNAL est encadré de « \u2726 »
 * (voir {@code GenerateLuckyItemProcedure.RankProfile.formatItemName}). C'est le
 * seul marqueur disponible côté client sans toucher à {@code TestProcedure}.
 *
 * <p>Sécurité :
 * <ul>
 *   <li>client uniquement ({@code Dist.CLIENT}) — jamais chargé en dédié ;</li>
 *   <li>toute la boucle est dans un {@code try/catch} : une erreur de rendu ne
 *       peut pas faire tomber le jeu ;</li>
 *   <li>cadencée ({@link #INTERVAL_TICKS}) et plafonnée ({@link #MAX_PER_TICK})
 *       pour rester gratuite côté FPS ;</li>
 *   <li>désactivable à chaud via {@link #ENABLED}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class EternalAuraParticles {

    private EternalAuraParticles() {}

    /** false = plus aucune particule (le glint blanc, lui, reste actif). */
    public static boolean ENABLED = true;

    /** Emission fluide tous les 4 ticks (0,2 s) avec pause auto et budget léger. */
    public static int INTERVAL_TICKS = 4;

    /** Rayon de recherche autour du joueur, en blocs. */
    public static double RADIUS = 8.0D;

    /** Plafond de particules par salve, tous items confondus. */
    public static int MAX_PER_TICK = 3;

    /** T360 : couleur des mini-cubes 3D blancs (plus aucune particule vanilla). */
    public static final String PARTICLE = "PIXEL_WHITE";

    private static int tick = 0;
    private static long lastGameTime = -1L;

    // =======================================================================
    // Détection (pure : testable sans Minecraft)
    // =======================================================================

    /**
     * Vrai si ce nom d'item est celui d'un rang ETERNAL, c'est-à-dire s'il
     * contient le marqueur « \u2726 » posé par {@code formatItemName}.
     *
     * <p>Méthode volontairement statique et sans dépendance : elle est couverte
     * par {@code verification/EternalParticleTest.java}.
     */
    public static boolean isEternalName(String name) {
        return name != null && name.indexOf('\u2726') >= 0;
    }

    /** Version ItemStack, tolérante aux noms absents / erreurs de lecture. */
    public static boolean isEternal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            return isEternalName(stack.getHoverName().getString());
        } catch (Throwable t) {
            return false;
        }
    }

    // =======================================================================
    // Boucle client
    // =======================================================================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || INTERVAL_TICKS <= 0) return;
        if (++tick < INTERVAL_TICKS) return;
        tick = 0;

        try {
            emit();
        } catch (Throwable t) {
            // Jamais de crash pour un effet cosmétique.
            if (EnchantAura.DEBUG) {
                deepluckyblock.util.DebugLog.out("[ETERNAL-FX] ignoré : " + t);
            }
        }
    }

    private static void emit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        long now = level.getGameTime();
        if (now == lastGameTime) return;
        lastGameTime = now;

        RandomSource rnd = level.random;
        int budget = MAX_PER_TICK;

        // 1) items posés au sol autour du joueur
        List<ItemEntity> near = level.getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(RADIUS));
        for (ItemEntity ie : near) {
            if (budget <= 0) return;
            if (ie == null || ie.isRemoved()) continue;
            if (!isEternal(ie.getItem())) continue;
            budget -= puff(level, rnd,
                    ie.getX() + (rnd.nextDouble() - 0.5D) * 0.4D,
                    ie.getY() + 0.15D + rnd.nextDouble() * 0.3D,
                    ie.getZ() + (rnd.nextDouble() - 0.5D) * 0.4D, budget);
        }

        // 2) item ETERNAL tenu en main (main principale puis secondaire)
        if (budget > 0) {
            if (isEternal(player.getMainHandItem())) {
                budget -= puffAtHand(level, rnd, player, budget);
            }
        }
        if (budget > 0) {
            if (isEternal(player.getOffhandItem())) {
                puffAtHand(level, rnd, player, budget);
            }
        }
    }

    /**
     * 1 à 2 particules autour d'un point ; renvoie le nombre émis.
     * Le plafond restant ({@code budget}) est respecté STRICTEMENT : sans ce
     * clamp, un seul item pouvait émettre 2 particules et dépasser MAX_PER_TICK.
     */
    private static int puff(ClientLevel level, RandomSource rnd, double x, double y, double z,
                            int budget) {
        if (budget <= 0) return 0;
        int n = Math.min(1 + rnd.nextInt(2), budget);
        // T360-NO-VANILLA-PARTICLES : l'aura ETERNAL n'utilise plus la particule END_ROD vanilla,
        // mais de vrais mini-cubes 3D blancs qui montent doucement (même moteur que les VFX d'armes).
        for (int i = 0; i < n; i++) {
            WeaponWorldVfxManager.spawnPixelPuff(deepluckyblock.enchantments.DeepElement.LIGHT.primary,
                    x + (rnd.nextDouble() - 0.5D) * 0.25D,
                    y + rnd.nextDouble() * 0.25D,
                    z + (rnd.nextDouble() - 0.5D) * 0.25D,
                    1, 0.03f, 0.012, 18);
        }
        return n;
    }

    /** Particules juste devant la main du joueur. */
    private static int puffAtHand(ClientLevel level, RandomSource rnd, LocalPlayer player,
                                  int budget) {
        double x = player.getX() + player.getLookAngle().x * 0.6D;
        double y = player.getEyeY() - 0.35D + player.getLookAngle().y * 0.4D;
        double z = player.getZ() + player.getLookAngle().z * 0.6D;
        return puff(level, rnd, x, y, z, budget);
    }
}
