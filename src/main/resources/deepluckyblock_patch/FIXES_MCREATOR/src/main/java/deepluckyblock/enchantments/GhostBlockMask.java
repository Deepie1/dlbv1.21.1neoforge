package deepluckyblock.enchantments;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T375-WATER-HOLE — <b>blocs fantômes</b> : faire disparaître (ou changer) le sol <b>à l'écran</b>
 * sans jamais toucher au monde.
 *
 * <h2>Le problème</h2>
 * Le maelström doit creuser un entonnoir dans le sol. Trois façons de s'y prendre :
 *
 * <ol>
 *   <li><b>Casser puis reposer les vrais blocs</b> ({@code setBlock}). Ça marche, et c'est une
 *       très mauvaise idée : le sable et le gravier tombent, l'eau et la lave coulent, la redstone
 *       se met à jour, les coffres se vident, les mobs tombent dans le trou, le moindre crash ou
 *       déchargement de chunk pendant l'animation laisse un cratère <b>définitif</b> dans le monde
 *       du joueur, et un autre joueur peut poser un bloc au milieu de la restauration. Un effet
 *       cosmétique n'a pas le droit de faire ça.</li>
 *   <li><b>Modifier le moteur de rendu de chunk</b> côté client. Propre en théorie, mais il faut
 *       intercepter {@code BlockAndTintGetter}, re-mailler les sections à chaque frame et se battre
 *       avec les optimisations de rendu des autres mods. Coûteux et fragile.</li>
 *   <li><b>Mentir au client</b> — ce fichier. On envoie aux joueurs à portée un
 *       {@link ClientboundBlockUpdatePacket} qui annonce « ce bloc est de l'air », <b>sans</b>
 *       modifier le serveur. Le client affiche le trou ; le serveur, lui, n'a rien changé.</li>
 * </ol>
 *
 * <h2>Pourquoi la troisième est la bonne</h2>
 * <ul>
 *   <li><b>Zéro effet de bord</b> : pas de mise à jour de bloc, pas de gravité, pas de fluide, pas
 *       de redstone, pas de drop, pas d'event de minage.</li>
 *   <li><b>Les entités ne tombent pas dedans</b> : la collision serveur est inchangée. C'est
 *       exactement ce qu'on veut — les mobs doivent tourner dans l'entonnoir, pas s'y enterrer.</li>
 *   <li><b>Rien à réparer en cas de crash</b> : le monde sur le disque n'a jamais été modifié. Au
 *       pire, le joueur recharge le chunk et tout est déjà normal.</li>
 *   <li><b>Restauration exacte</b> : on relit l'état <i>réel</i> au moment de restaurer, donc si
 *       quelqu'un a posé un bloc pendant l'animation, c'est son bloc qui s'affiche, pas l'ancien.</li>
 * </ul>
 *
 * <h2>Ce que ça ne fait pas</h2>
 * L'illusion est purement visuelle. Un joueur qui marche sur le trou <b>ne tombe pas</b> : il
 * marche sur un sol invisible. C'est voulu pour un effet d'arme de 7,5 secondes, et c'est aussi la
 * raison pour laquelle la durée est plafonnée — on ne laisse pas un mensonge à l'écran éternellement.
 *
 * <h2>Garde-fous</h2>
 * <ul>
 *   <li>{@link #MAX_ACTIVE} masques simultanés sur tout le serveur, {@link #MAX_LIFETIME} ticks
 *       chacun au maximum ;</li>
 *   <li>une restauration forcée si le plafond est atteint (le plus ancien saute) ;</li>
 *   <li>{@link #tick(ServerLevel)} restaure tout ce qui a expiré, appelé une fois par tick ;</li>
 *   <li>{@link #restoreAll(ServerLevel)} pour tout annuler d'un coup ;</li>
 *   <li>chaque envoi est enveloppé : un paquet cosmétique ne doit jamais casser un tick serveur.</li>
 * </ul>
 */
public final class GhostBlockMask {

    private GhostBlockMask() { }

    /** Au-delà, on arrête d'en poser : mieux vaut un trou incomplet qu'un serveur qui rame. */
    private static final int MAX_ACTIVE = 420;
    /** Aucun mensonge ne dure plus de 12 secondes, même si l'appelant oublie de restaurer. */
    private static final long MAX_LIFETIME = 240L;
    /** Rayon d'envoi : inutile de mentir à quelqu'un qui ne voit pas la scène. */
    private static final double SEND_RADIUS = 96.0;
    private static final double SEND_RADIUS_SQR = SEND_RADIUS * SEND_RADIUS;

    private record Mask(ServerLevel level, long restoreAt) { }

    /** Clé = {@link BlockPos#asLong()}. Un bloc ne peut être masqué qu'une fois à la fois. */
    private static final Map<Long, Mask> ACTIVE = new ConcurrentHashMap<>();

    // ============================================================================================
    // Pose
    // ============================================================================================

    /** Masque un bloc : le client le voit comme de l'air jusqu'à {@code restoreAt}. */
    public static void hide(ServerLevel level, BlockPos pos, long restoreAt) {
        show(level, pos, Blocks.AIR.defaultBlockState(), restoreAt);
    }

    /**
     * Fait voir {@code fake} à la place du bloc réel. Sert au trou du maelström
     * ({@code AIR}), mais aussi au givre, qui gèle le sol en {@code ICE} / {@code SNOW_BLOCK}
     * à l'écran sans transformer le terrain du joueur.
     */
    public static void show(ServerLevel level, BlockPos pos, BlockState fake, long restoreAt) {
        if (level == null || pos == null || fake == null) return;
        try {
            if (level.isClientSide()) return;
            if (!level.hasChunkAt(pos)) return;
            long key = pos.asLong();
            long now = level.getGameTime();
            long capped = Math.min(restoreAt, now + MAX_LIFETIME);
            Mask previous = ACTIVE.get(key);
            if (previous != null) {
                // Déjà masqué : on prolonge simplement, sans renvoyer de paquet inutile.
                if (capped > previous.restoreAt()) ACTIVE.put(key, new Mask(level, capped));
                return;
            }
            if (ACTIVE.size() >= MAX_ACTIVE) {
                evictOldest();
                if (ACTIVE.size() >= MAX_ACTIVE) return;
            }
            // Ne jamais masquer ce qui est déjà de l'air : paquet gaspillé, et à la restauration
            // on renverrait de l'air sur de l'air.
            BlockState real = level.getBlockState(pos);
            if (real.isAir() && fake.isAir()) return;
            ACTIVE.put(key, new Mask(level, capped));
            broadcast(level, new ClientboundBlockUpdatePacket(pos, fake), pos);
        } catch (Throwable ignored) {
            // Un effet cosmétique ne doit jamais casser un tick serveur.
        }
    }

    /**
     * Creuse un <b>entonnoir</b> de blocs fantômes et programme son rebouchage.
     *
     * <p>Le profil est parabolique — {@code depth = maxDepth · (1 − (d/radius)²)} — donc large et
     * peu profond au bord, resserré et plongeant au centre : la forme d'un vrai tourbillon, et
     * celle que dessine déjà {@code WaterSignatureVfx}.</p>
     *
     * <p>C'est le <b>talus</b> qui rend la chose crédible : on ne masque pas un cylindre, on suit
     * la pente. Sans ça, le trou a des parois verticales et ressemble à une cage d'ascenseur.</p>
     *
     * @param delayPerRing ticks d'attente par bloc de distance au centre — c'est ce qui rend
     *                     l'ouverture <b>progressive</b> : le trou se creuse du centre vers le bord,
     *                     à la même vitesse que la spirale qui l'aspire.
     * @param closeStagger ticks de décalage de rebouchage par bloc de distance. Le <b>bord</b> se
     *                     referme en premier et le centre en dernier : c'est le sens dans lequel
     *                     l'eau reflue une fois le tourbillon dissipé. Une fermeture simultanée
     *                     fait « clignoter » tout le disque d'un coup, ce qui se voit énormément.
     * @return le nombre de blocs effectivement masqués.
     */
    public static int digFunnel(ServerLevel level, BlockPos center, double radius,
                                double maxDepth, int surfaceSearch,
                                long openAt, long closeAt, double delayPerRing,
                                double closeStagger) {
        if (level == null || center == null) return 0;
        int masked = 0;
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * (double) dx + dz * (double) dz);
                if (dist > radius) continue;
                double ratio = dist / Math.max(0.001, radius);
                double depth = maxDepth * (1.0 - ratio * ratio);
                if (depth < 0.9) continue;                       // le bord reste intact
                BlockPos surface = findSurface(level, center.offset(dx, 0, dz), surfaceSearch);
                if (surface == null) continue;
                // Ouverture décalée selon la distance : le centre part en premier.
                long open = openAt + (long) (dist * delayPerRing);
                // Fermeture décalée en sens INVERSE : le bord se rebouche avant le centre.
                long close = closeAt + (long) ((radius - dist) * closeStagger);
                int layers = (int) Math.round(depth);
                for (int dy = 0; dy < layers; dy++) {
                    BlockPos pos = surface.below(dy);
                    if (level.getBlockState(pos).isAir()) continue;
                    // La colonne s'enfonce couche par couche, et se remplit du fond vers le haut.
                    scheduleHide(level, pos, open + dy, close - dy);
                    masked++;
                    if (masked >= MAX_ACTIVE) return masked;
                }
            }
        }
        return masked;
    }

    /**
     * Pose différée : on ne peut pas masquer « dans dix ticks » avec un paquet immédiat, donc on
     * passe par l'ordonnanceur du mod. Chaque bloc a son propre instant d'ouverture, ce qui donne
     * la propagation lisse — et chacun est rebouché au même instant global.
     */
    private static void scheduleHide(ServerLevel level, BlockPos pos, long openDelay, long closeAt) {
        long now = level.getGameTime();
        int wait = (int) Math.max(0L, openDelay - now);
        if (wait == 0) {
            hide(level, pos, closeAt);
            return;
        }
        final BlockPos frozen = pos.immutable();
        deepluckyblock.procedures.TestProcedure.schedule(level,
                deepluckyblock.procedures.TestProcedure.currentTick(level) + wait,
                () -> hide(level, frozen, closeAt));
    }

    /**
     * <b>Décapage du raz-de-marée.</b> La vague arrache la couche de surface sur son passage : on
     * masque une bande orientée, bloc par bloc, avec un retard proportionnel à la position
     * <i>le long de l'axe de marche</i>. Résultat : la terre disparaît exactement sous le front,
     * jamais devant lui, et {@code WaterTerrainVfx} la montre au même instant en train de voler.
     *
     * @param halfWidth demi-largeur de la bande, perpendiculairement à la marche
     * @param from      début de la bande le long de l'axe (négatif = derrière la cible)
     * @param to        fin de la bande
     * @param travel    nombre de ticks que met le front pour parcourir la bande entière
     */
    public static int scourLine(ServerLevel level, BlockPos center, double dirX, double dirZ,
                                double from, double to, double halfWidth, int depth,
                                int surfaceSearch, long openAt, long closeAt, double closeStagger) {
        if (level == null || center == null) return 0;
        double span = Math.max(0.001, to - from);
        int reach = (int) Math.ceil(Math.max(Math.abs(from), Math.abs(to)) + halfWidth) + 1;
        int masked = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double along = dx * dirX + dz * dirZ;
                double side = -dx * dirZ + dz * dirX;
                if (along < from || along > to) continue;
                if (Math.abs(side) > halfWidth) continue;
                // Le bord de la bande est rongé, pas net : sinon on voit un rectangle parfait.
                double edge = 1.0 - Math.abs(side) / Math.max(0.001, halfWidth);
                if (edge < 0.18) continue;
                BlockPos surface = findSurface(level, center.offset(dx, 0, dz), surfaceSearch);
                if (surface == null) continue;
                double progress = (along - from) / span;
                long open = openAt + (long) (progress * span * 2.0);
                long close = closeAt + (long) (progress * closeStagger);
                int layers = Math.max(1, (int) Math.round(depth * (0.45 + 0.55 * edge)));
                for (int dy = 0; dy < layers; dy++) {
                    BlockPos pos = surface.below(dy);
                    if (level.getBlockState(pos).isAir()) continue;
                    scheduleHide(level, pos, open + dy, close - dy);
                    masked++;
                    if (masked >= MAX_ACTIVE) return masked;
                }
            }
        }
        return masked;
    }

    /**
     * <b>Gel du permafrost.</b> Même mécanique, mais au lieu de faire disparaître le sol on le
     * remplace à l'écran par de la glace et de la neige : le givre court du centre vers le bord à
     * la vitesse de l'étoile dessinée par {@code WaterSignatureVfx}, puis le sol redevient
     * lui-même. Le terrain du joueur n'a jamais été transformé — rien à déneiger après coup.
     *
     * <p>{@code PACKED_ICE} et {@code SNOW_BLOCK} sont choisis parce qu'ils sont <b>opaques</b> :
     * la glace normale est translucide et aurait laissé passer la lumière du bloc d'origine,
     * trahissant l'illusion.</p>
     */
    public static int freezeSurface(ServerLevel level, BlockPos center, double radius,
                                    int surfaceSearch, long openAt, long thawAt,
                                    double delayPerRing, double thawStagger) {
        if (level == null || center == null) return 0;
        BlockState core = Blocks.PACKED_ICE.defaultBlockState();
        BlockState rim = Blocks.SNOW_BLOCK.defaultBlockState();
        int masked = 0;
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * (double) dx + dz * (double) dz);
                if (dist > radius) continue;
                // Bord dentelé : le gel ne s'arrête pas sur un cercle parfait.
                double jitter = ((dx * 73856093) ^ (dz * 19349663)) % 100 / 100.0;
                if (dist > radius * 0.70 && Math.abs(jitter) < (dist - radius * 0.70) / (radius * 0.30)) continue;
                BlockPos surface = findSurface(level, center.offset(dx, 0, dz), surfaceSearch);
                if (surface == null) continue;
                long open = openAt + (long) (dist * delayPerRing);
                long thaw = thawAt + (long) ((radius - dist) * thawStagger);
                BlockState fake = dist < radius * 0.55 ? core : rim;
                final BlockPos frozen = surface.immutable();
                final BlockState chosen = fake;
                int wait = (int) Math.max(0L, open - level.getGameTime());
                if (wait == 0) {
                    show(level, frozen, chosen, thaw);
                } else {
                    deepluckyblock.procedures.TestProcedure.schedule(level,
                            deepluckyblock.procedures.TestProcedure.currentTick(level) + wait,
                            () -> show(level, frozen, chosen, thaw));
                }
                masked++;
                if (masked >= MAX_ACTIVE) return masked;
            }
        }
        return masked;
    }

    /** Premier bloc plein en partant de {@code from}, cherché vers le haut puis vers le bas. */
    private static BlockPos findSurface(ServerLevel level, BlockPos from, int search) {
        for (int dy = search; dy >= -search; dy--) {
            BlockPos probe = from.offset(0, dy, 0);
            if (!level.hasChunkAt(probe)) continue;
            if (level.getBlockState(probe).isAir()) continue;
            if (level.getBlockState(probe.above()).isAir()) return probe;
        }
        return null;
    }

    // ============================================================================================
    // Restauration
    // ============================================================================================

    /** Rend son vrai visage à un bloc : on relit l'état <b>actuel</b>, pas celui d'avant. */
    public static void restore(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        try {
            ACTIVE.remove(pos.asLong());
            if (!level.hasChunkAt(pos)) return;
            broadcast(level, new ClientboundBlockUpdatePacket(level, pos), pos);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Une passe par tick : restaure tout ce qui est arrivé à échéance. Les blocs sont rendus
     * <b>du plus ancien au plus récent</b>, donc dans l'ordre inverse du creusement — le trou se
     * referme du centre vers le bord, comme il s'était ouvert.
     */
    public static void tick(ServerLevel level) {
        if (level == null || ACTIVE.isEmpty()) return;
        long now = level.getGameTime();
        List<Long> due = null;
        for (Map.Entry<Long, Mask> entry : ACTIVE.entrySet()) {
            Mask mask = entry.getValue();
            if (mask.level() != level) continue;
            if (now < mask.restoreAt()) continue;
            if (due == null) due = new ArrayList<>(32);
            due.add(entry.getKey());
        }
        if (due == null) return;
        for (Long key : due) {
            restore(level, BlockPos.of(key));
        }
    }

    /** Annule tous les masques d'un niveau (changement de dimension, arrêt, commande de debug). */
    public static int restoreAll(ServerLevel level) {
        if (level == null || ACTIVE.isEmpty()) return 0;
        int count = 0;
        Iterator<Map.Entry<Long, Mask>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Mask> entry = it.next();
            if (entry.getValue().level() != level) continue;
            it.remove();
            try {
                BlockPos pos = BlockPos.of(entry.getKey());
                if (level.hasChunkAt(pos)) {
                    broadcast(level, new ClientboundBlockUpdatePacket(level, pos), pos);
                }
            } catch (Throwable ignored) {
            }
            count++;
        }
        return count;
    }

    /** Nombre de mensonges en cours — utile en debug. */
    public static int activeCount() {
        return ACTIVE.size();
    }

    private static void evictOldest() {
        Long oldestKey = null;
        long oldest = Long.MAX_VALUE;
        ServerLevel oldestLevel = null;
        for (Map.Entry<Long, Mask> entry : ACTIVE.entrySet()) {
            if (entry.getValue().restoreAt() < oldest) {
                oldest = entry.getValue().restoreAt();
                oldestKey = entry.getKey();
                oldestLevel = entry.getValue().level();
            }
        }
        if (oldestKey != null && oldestLevel != null) restore(oldestLevel, BlockPos.of(oldestKey));
    }

    private static void broadcast(ServerLevel level, ClientboundBlockUpdatePacket packet, BlockPos pos) {
        double px = pos.getX() + 0.5, py = pos.getY() + 0.5, pz = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(px, py, pz) > SEND_RADIUS_SQR) continue;
            try {
                player.connection.send(packet);
            } catch (Throwable ignored) {
            }
        }
    }
}
