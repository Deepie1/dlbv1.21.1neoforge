package deepluckyblock.util;

import deepluckyblock.DeepLuckyBlockMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T350-DUMMY-DPS : compteur de dégâts de la cible d'entraînement ({@code /dlbvfx dummy}).
 *
 * <p>Affiche <b>au-dessus de la tête</b> de la cible le total de dégâts encaissés sur la dernière
 * seconde (fenêtre glissante de 20 ticks), donc le DPS réel, plus le nombre de coups et le total
 * cumulé depuis l'invocation.</p>
 *
 * <p>Pourquoi un nom personnalisé plutôt qu'un renderer dédié ? Le nom d'entité est déjà rendu
 * au-dessus de la tête par le jeu, en texte net, sans coût supplémentaire ni paquet réseau :
 * parfait pour un outil de test, et ça marche aussi en multijoueur.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class TrainingDummyTracker {

    /** Marqueur NBT posé sur la cible (partagé avec {@code DlbVfxCommand}). */
    public static final String DUMMY_TAG = "dlb_vfx_dummy";

    /** Fenêtre du DPS : 20 ticks = 1 seconde. */
    private static final int WINDOW_TICKS = 20;
    /** Sans aucun coup pendant ce délai, on efface l'affichage. */
    private static final int IDLE_CLEAR_TICKS = 60;

    private static final Map<Integer, Deque<long[]>> HITS = new ConcurrentHashMap<>();
    private static final Map<Integer, LivingEntity> DUMMIES = new ConcurrentHashMap<>();
    private static final Map<Integer, Double> TOTALS = new ConcurrentHashMap<>();

    private TrainingDummyTracker() {}

    /** Appelé par la commande une fois la cible créée. */
    public static void track(LivingEntity dummy) {
        if (dummy == null) return;
        DUMMIES.put(dummy.getId(), dummy);
        HITS.computeIfAbsent(dummy.getId(), k -> new ArrayDeque<>());
        TOTALS.put(dummy.getId(), 0.0);
        refreshName(dummy, 0.0, 0, 0.0);
    }

    /** Oublie une cible (supprimée par /dlbvfx clear ou /dlbvfx dummy). */
    public static void forget(LivingEntity dummy) {
        if (dummy == null) return;
        DUMMIES.remove(dummy.getId());
        HITS.remove(dummy.getId());
        TOTALS.remove(dummy.getId());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null) return;
            if (!entity.getPersistentData().getBoolean(DUMMY_TAG)) return;

            long now = entity.level().getGameTime();
            float amount = event.getAmount();
            if (amount <= 0.0f) return;

            Deque<long[]> window = HITS.computeIfAbsent(entity.getId(), k -> new ArrayDeque<>());
            synchronized (window) {
                window.addLast(new long[]{now, Math.round(amount * 1000.0f)});
                while (window.size() > 512) window.removeFirst();
            }
            TOTALS.merge(entity.getId(), (double) amount, Double::sum);
            DUMMIES.putIfAbsent(entity.getId(), entity);

            // Mise à jour immédiate (le joueur voit le chiffre bouger à chaque coup)
            refreshName(entity, windowSum(window, now), window.size(), TOTALS.getOrDefault(entity.getId(), 0.0));
        } catch (Throwable ignored) {
            // Un outil de test ne doit jamais faire planter le serveur
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DUMMIES.isEmpty()) return;
        try {
            Iterator<Map.Entry<Integer, LivingEntity>> it = DUMMIES.entrySet().iterator();
            while (it.hasNext()) {
                LivingEntity dummy = it.next().getValue();
                if (dummy == null || dummy.isRemoved() || !dummy.isAlive()) {
                    forget(dummy);
                    it.remove();
                    continue;
                }
                long now = dummy.level().getGameTime();
                Deque<long[]> window = HITS.get(dummy.getId());
                if (window == null) continue;

                double sum;
                int hits;
                synchronized (window) {
                    long cutoff = now - WINDOW_TICKS;
                    while (!window.isEmpty() && window.peekFirst()[0] <= cutoff) {
                        window.removeFirst();
                    }
                    sum = windowSum(window, now);
                    hits = window.size();
                }

                long last = 0L;
                synchronized (window) {
                    long[] tail = window.peekLast();
                    if (tail != null) last = tail[0];
                }
                boolean idle = (now - last) > IDLE_CLEAR_TICKS;
                if (idle && hits == 0) {
                    // Plus rien depuis 3 s : on remet le nom de base (une seule fois)
                    if (dummy.getPersistentData().getBoolean(DUMMY_TAG + "_shown")) {
                        setBaseName(dummy);
                    }
                    continue;
                }
                refreshName(dummy, sum, hits, TOTALS.getOrDefault(dummy.getId(), 0.0));
            }
        } catch (Throwable ignored) {
        }
    }

    private static double windowSum(Deque<long[]> window, long now) {
        double sum = 0.0;
        for (long[] hit : window) {
            if (now - hit[0] <= WINDOW_TICKS) {
                sum += hit[1] / 1000.0;
            }
        }
        return sum;
    }

    /** Nom d'origine de la cible (aucun chiffre). */
    private static void setBaseName(LivingEntity dummy) {
        try {
            dummy.setCustomName(Component.literal("§bDLB §7· §fCible VFX").withStyle(ChatFormatting.RESET));
            dummy.setCustomNameVisible(true);
            dummy.getPersistentData().putBoolean(DUMMY_TAG + "_shown", false);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshName(LivingEntity dummy, double dps, int hits, double total) {
        try {
            Component name;
            if (dps <= 0.0001 && hits == 0) {
                name = Component.literal("§bDLB §7· §fCible VFX")
                        .withStyle(ChatFormatting.RESET);
            } else {
                name = Component.literal("§bDLB §7· §eDPS §f" + String.format(java.util.Locale.ROOT, "%.1f", dps))
                        .append(Component.literal(" §8| §7coups §f" + hits))
                        .append(Component.literal(" §8| §7total §f" + String.format(java.util.Locale.ROOT, "%.0f", total)));
            }
            dummy.setCustomName(name);
            dummy.setCustomNameVisible(true);
            dummy.getPersistentData().putBoolean(DUMMY_TAG + "_shown", true);
        } catch (Throwable ignored) {
        }
    }

    /** Employé par la commande pour lister les cibles suivies. */
    public static List<LivingEntity> tracked() {
        return new ArrayList<>(DUMMIES.values());
    }
}
