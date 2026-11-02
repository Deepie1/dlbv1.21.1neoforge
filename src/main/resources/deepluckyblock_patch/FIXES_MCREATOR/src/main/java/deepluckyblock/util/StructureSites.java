package deepluckyblock.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StructureSites - T27 : registre des emprises des structures deja posees.
 *
 * <p>Consignes utilisateur (log client du 20/09) : « les structures ... faire en
 * sorte qu'ils n'arrivent pas dans ou sur nos structure (arbres par exemples qui
 * sont dans mon cirque, bamboos, vignes qui volent, etc) », et « les structures
 * ne doivent pas se genner entre elles ».
 *
 * <p>Chaque paste enregistre son emprise (rectangle XZ + marge de 24 blocs). La
 * recherche de site rejette alors tout candidat qui chevaucherait une structure
 * deja posee, exactement comme elle rejette deja les chunks de joueur : la
 * structure suivante part ailleurs au lieu de s'empiler sur la precedente.
 *
 * <p>Registre volontairement simple : en memoire, par niveau, jamais sauvegarde.
 * Il sert a eviter les collisions pendant la session de jeu, pas a tenir une
 * carte du monde.
 */
public final class StructureSites {

    private StructureSites() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Marge ajoutee autour de chaque emprise (chevauchement tolere : 0). */
    private static final int MARGIN = 24;

    private static final class Area {
        final int x0, z0, x1, z1;
        final String name;
        Area(int x0, int z0, int x1, int z1, String name) {
            this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.name = name;
        }
        boolean overlaps(int ax0, int az0, int ax1, int az1) {
            return ax0 <= x1 && ax1 >= x0 && az0 <= z1 && az1 >= z0;
        }
    }

    private static final Map<ServerLevel, List<Area>> PLACED = new HashMap<>();

    private static java.nio.file.Path sitesFile(ServerLevel level) {
        try {
            if (level == null || level.getServer() == null) return null;
            String dim = level.dimension().location().toString().replace(':', '_').replace('/', '_');
            return level.getServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data")
                    .resolve("dlb_sites_" + dim + ".tsv");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Area> ensureLoaded(ServerLevel level) {
        if (level == null) return java.util.Collections.emptyList();
        List<Area> existing = PLACED.get(level);
        if (existing != null) return existing;
        List<Area> list = new ArrayList<>();
        java.nio.file.Path file = sitesFile(level);
        if (file != null && java.nio.file.Files.isRegularFile(file)) {
            try {
                for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) continue;
                    String[] parts = s.split("\t", 5);
                    if (parts.length >= 5) {
                        list.add(new Area(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]),
                                parts[4]));
                    }
                }
            } catch (Throwable ignored) { }
        }
        PLACED.put(level, list);
        return list;
    }

    /** Enregistre l'emprise d'une structure qui vient d'etre posee (a appeler AVANT le paste). */
    public static void register(ServerLevel level, String name, int x0, int z0, int x1, int z1) {
        if (level == null) return;
        int ax0 = Math.min(x0, x1) - MARGIN, ax1 = Math.max(x0, x1) + MARGIN;
        int az0 = Math.min(z0, z1) - MARGIN, az1 = Math.max(z0, z1) + MARGIN;
        List<Area> list = ensureLoaded(level);
        Area added = new Area(ax0, az0, ax1, az1, name);
        list.add(added);
        java.nio.file.Path file = sitesFile(level);
        if (file != null) {
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                String row = added.x0 + "\t" + added.z0 + "\t" + added.x1 + "\t" + added.z1 + "\t" + added.name + "\n";
                java.nio.file.Files.writeString(file, row, java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Throwable ignored) { }
        }
        deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-SITES] emprise enregistree : {} ({}x{} a {},{}), {} structure(s) suivie(s)",
                name, ax1 - ax0 + 1, az1 - az0 + 1, ax0, az0, list.size());
    }

    /** Nom de la structure qui occupe ce carre, ou null si la place est libre. */
    public static String conflict(ServerLevel level, int cx, int cz, int radius) {
        List<Area> list = ensureLoaded(level);
        if (list.isEmpty()) return null;
        int x0 = cx - radius, x1 = cx + radius, z0 = cz - radius, z1 = cz + radius;
        for (Area a : list) if (a.overlaps(x0, z0, x1, z1)) return a.name;
        return null;
    }

    /**
     * T29 : decale une position jusqu'a trouver une place LIBRE, en spirale
     * (pas de {@code step} blocs, {@code rings} anneaux). Sert de garde-fou au
     * POINT DE PASSAGE COMMUN des pipelines : meme les chemins de repli
     * (« repli sur la pente minimale brute », « plus loin charge », « hors chunk
     * de joueur ») passent par ici, alors qu'ils contournaient les filtres
     * places dans les boucles de recherche.
     *
     * @return la position d'origine si elle est deja libre, sinon la premiere
     *         position libre trouvee, sinon la position d'origine (jamais null).
     */
    public static BlockPos freeOffset(ServerLevel level, BlockPos p, int rings, int step) {
        return freeOffset(level, p, rings, step, 48);
    }

    /** Variante pour les emprises géantes (Crimson Lake notamment). */
    public static BlockPos freeOffset(ServerLevel level, BlockPos p, int rings, int step, int candidateRadius) {
        if (p == null || PLACED.get(level) == null) return p;
        String here = conflict(level, p.getX(), p.getZ(), candidateRadius);
        if (here == null) return p;
        for (int r = 1; r <= rings; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = p.getX() + dx * step, z = p.getZ() + dz * step;
                    if (conflict(level, x, z, candidateRadius) != null) continue;
                    deepluckyblock.util.DebugLog.info(LOGGER, "[DLB-SITES] {} : position {} occupee par '{}' -> deplacement de {} bloc(s) vers {},{} (anneau {}, T27)",
                            p.toShortString(), p.toShortString(), here, r * step, x, z, r);
                    return new BlockPos(x, p.getY(), z);
                }
            }
        }
        deepluckyblock.util.DebugLog.warn(LOGGER, "[DLB-SITES] {} : chevauche l'emprise '{}' et aucune place libre dans {} anneaux de {} blocs -- pose a l'emplacement prevu",
                p.toShortString(), here, rings, step);
        return p;
    }

    /** Diagnostic (commande /dlbtest sites). */
    public static String describe(ServerLevel level) {
        List<Area> list = PLACED.get(level);
        if (list == null || list.isEmpty()) return "aucune emprise enregistree";
        StringBuilder sb = new StringBuilder();
        for (Area a : list) sb.append(a.name).append(" [").append(a.x0).append(",").append(a.z0)
                .append(" -> ").append(a.x1).append(",").append(a.z1).append("] ");
        return sb.toString().trim();
    }
}
