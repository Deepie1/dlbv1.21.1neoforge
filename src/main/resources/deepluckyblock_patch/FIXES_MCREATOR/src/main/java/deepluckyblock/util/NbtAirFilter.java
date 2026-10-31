package deepluckyblock.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * T73 : filtre NBT en FLUX -- l'air n'est plus jamais un "bloc".
 *
 * <p>Le format .nbt d'une structure materialise CHAQUE case de sa boite. Une
 * structure de 104x242x84 (citadel) = 2 114 112 entrees dont 95 % d'air : le
 * parseur vanilla cree alors des millions d'objets (CompoundTag + ListTag + 4
 * IntTag par entree, ~200 octets piece) qui sont aussitot... ignores a la pose.
 * C'est cette materialisation qui coutait la memoire (OutOfMemoryError observe
 * sur citadel) et le temps de chargement.
 *
 * <p>Ce filtre lit le fichier en flux, n'alloue que deux tableaux primitifs
 * (positions packees + index de palette) et reconstruit un tag NE contenant que
 * les VRAIS blocs plus l'air INTERIEUR (celui qui creuse l'interieur, calcule
 * par un flood fill depuis les 6 faces). Le parseur vanilla ne voit donc jamais
 * les millions d'entrees d'air : elles n'existent ni en memoire, ni sur le
 * chemin de pose.
 *
 * <p>Securite : toute entree inattendue (tag inconnu, liste non conforme,
 * palette sans air) fait retourner {@code null} ; l'appelant repart alors sur le
 * parse vanilla, comportement strictement identique a avant. Desactivable par
 * {@code -Ddlb.nbtAirFilter=0}.
 */
public final class NbtAirFilter {

    /** Taille compressee en dessous de laquelle on garde le parse vanilla. */
    private static final long MIN_KB = Long.getLong("dlb.nbtAirFilterMinKb", 32L);
    /** Activation generale (A/B test). */
    private static final boolean ENABLED = !"0".equals(System.getProperty("dlb.nbtAirFilter"));
    /** Garde-fou du flood fill (boites plus grandes = air considere exterieur). */
    private static final long MAX_CELLS = 24_000_000L;
    /** Garde-fou de palette. */
    private static final int MAX_PALETTE = 8192;

    private NbtAirFilter() {}

    /**
     * T73c : templates dont la palette ne contient AUCUN air. Ces fichiers sont deja
     * creux (everest : 52 980 entrees, 0 air ; crimsonlake : 0 air) : il n'y a rien a
     * supprimer, donc rien a gagner -- le filtre rend la main au parse vanilla, et on
     * le DIT dans le journal (sinon on croit que le filtre n'a pas tourne).
     */
    private static final Set<String> NO_AIR = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean paletteHasNoAir(String nbtName) {
        return nbtName != null && NO_AIR.contains(nbtName);
    }

    /** Resultat du filtre : tag allege + compteurs exacts (pour le journal). */
    public static final class Result {
        public CompoundTag tag;
        public int total, realBlocks, airInterior, airExterior;
        public int sx, sy, sz;
        public long millis;
        public Set<Long> interior = Set.of();
        // T73b : controle de coherence -- toute position hors de la boite du
        // template est comptee : un filtre qui se trompe de coordonnees doit se
        // voir dans le journal, pas dans une zone d'edition geante.
        public int outOfBox;
        public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    }

    /** Filtre le fichier ; {@code null} => l'appelant doit utiliser le parse vanilla. */
    public static Result filter(InputStream raw, String nbtName, long compressedBytes) {
        if (!ENABLED || compressedBytes < MIN_KB * 1024L) return null;
        long t0 = System.currentTimeMillis();
        try {
            Result r = filter0(raw, nbtName);
            if (r != null) r.millis = System.currentTimeMillis() - t0;
            return r;
        } catch (Throwable ex) {
            return null;    // jamais d'exception remontee : retour au chemin vanilla
        }
    }

    // ------------------------------------------------------------------ lecture

    private static Result filter0(InputStream raw, String nbtName) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16));
        if (in.readUnsignedByte() != 10) return null;           // racine non compound
        readName(in);                                           // nom racine (ignore)

        CompoundTag out = new CompoundTag();
        int dataVersion = 0;
        ListTag sizeTag = null;
        int n = 0;
        long[] pos = new long[4096];
        int[] state = new int[4096];
        Map<Integer, CompoundTag> extras = null;

        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) break;
            String key = readName(in);
            switch (key) {
                case "DataVersion" -> dataVersion = intValue(readTag(in, type));
                case "size" -> sizeTag = asList(readTag(in, type));
                case "palette", "palettes" -> out.put(key, readTag(in, type));
                case "entities", "author" -> out.put(key, readTag(in, type));
                case "blocks" -> {
                    if (type != 9) return null;
                    int elemType = in.readUnsignedByte();
                    int count = in.readInt();
                    if (elemType != 10 || count < 0) return null;
                    if (count > pos.length) {
                        int cap = Math.max(count, pos.length * 2);
                        pos = Arrays.copyOf(pos, cap);
                        state = Arrays.copyOf(state, cap);
                    }
                    for (int i = 0; i < count; i++) {
                        CompoundTag e = readCompound(in, false);
                        pos[n] = packPos(e);
                        state[n] = e.contains("state") ? e.getInt("state") : 0;
                        if (e.get("nbt") instanceof CompoundTag be && !be.isEmpty()) {
                            if (extras == null) extras = new HashMap<>();
                            extras.put(n, be);
                        }
                        n++;
                    }
                }
                default -> skip(in, type);
            }
        }

        if (n == 0 || sizeTag == null || sizeTag.size() < 3) return null;
        int sx = sizeTag.getInt(0), sy = sizeTag.getInt(1), sz = sizeTag.getInt(2);
        if (sx <= 0 || sy <= 0 || sz <= 0) return null;

        // Palette : quels index sont de l'air ? Si la palette n'a AUCUN air, le
        // fichier est deja creux : on rend la main au parse vanilla (aucun gain a
        // attendre, aucune raison de reconstruire un tag).
        boolean[] airState = airIndices(out.get("palette"), out.get("palettes"));
        if (airState == null) {
            if (nbtName != null) NO_AIR.add(nbtName);   // T73c : fichier deja creux
            return null;
        }

        Result r = new Result();
        r.sx = sx; r.sy = sy; r.sz = sz;
        r.total = n;

        // 1) masque de la boite : 0 = absent (fichier creux), 1 = air, 2 = bloc reel
        long cells = (long) sx * sy * sz;
        byte[] grid = cells <= MAX_CELLS ? new byte[(int) cells] : null;
        int airCount = 0;
        for (int i = 0; i < n; i++) {
            boolean air = isAir(state[i], airState);
            if (air) airCount++;
            int px = unpackX(pos[i]), py = unpackY(pos[i]), pz = unpackZ(pos[i]);
            r.minX = Math.min(r.minX, px); r.maxX = Math.max(r.maxX, px);
            r.minY = Math.min(r.minY, py); r.maxY = Math.max(r.maxY, py);
            r.minZ = Math.min(r.minZ, pz); r.maxZ = Math.max(r.maxZ, pz);
            boolean inside = px >= 0 && px < sx && py >= 0 && py < sy && pz >= 0 && pz < sz;
            if (!inside) r.outOfBox++;
            if (grid != null && inside) {
                grid[(py * sz + pz) * sx + px] = (byte) (air ? 1 : 2);
            }
        }

        // 2) air INTERIEUR = air jamais relie a l'exterieur (flood fill 6 faces)
        boolean[] interiorCell = null;
        if (airCount > 0 && grid != null) {
            interiorCell = floodInterior(grid, sx, sy, sz);
        }

        // 3) reconstruction : blocs reels + air interieur uniquement
        ListTag blocks = new ListTag();
        Set<Long> interior = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (isAir(state[i], airState)) {
                boolean keep = false;
                if (interiorCell != null) {
                    int x = unpackX(pos[i]), y = unpackY(pos[i]), z = unpackZ(pos[i]);
                    if (x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz) {
                        keep = interiorCell[(y * sz + z) * sx + x];
                        if (keep) interior.add(pos[i]);
                    }
                }
                if (!keep) { r.airExterior++; continue; }
                r.airInterior++;
            } else {
                r.realBlocks++;
            }
            CompoundTag e = new CompoundTag();
            ListTag p = new ListTag();
            p.add(IntTag.valueOf(unpackX(pos[i])));
            p.add(IntTag.valueOf(unpackY(pos[i])));
            p.add(IntTag.valueOf(unpackZ(pos[i])));
            e.put("pos", p);
            e.putInt("state", state[i]);
            if (extras != null) {
                CompoundTag nbt = extras.get(i);
                if (nbt != null) e.put("nbt", nbt);
            }
            blocks.add(e);
        }
        if (blocks.isEmpty()) return null;

        if (dataVersion > 0) out.putInt("DataVersion", dataVersion);
        out.put("size", sizeTag);
        out.put("blocks", blocks);
        r.tag = out;
        r.interior = interior;
        return r;
    }

    private static boolean isAir(int state, boolean[] airState) {
        return state >= 0 && state < airState.length && airState[state];
    }

    /**
     * Marque l'air INTERIEUR : marche depuis les 6 faces sur tout ce qui n'est pas
     * un bloc reel (l'air et les cases absentes d'un fichier creux sont traversables).
     */
    private static boolean[] floodInterior(byte[] grid, int sx, int sy, int sz) {
        boolean[] ext = new boolean[grid.length];
        IntQ q = new IntQ();
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                seed(grid, ext, q, (y * sz) * sx + x);                  // z = 0
                seed(grid, ext, q, (y * sz + sz - 1) * sx + x);         // z = sz-1
            }
        }
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                seed(grid, ext, q, z * sx + x);                          // y = 0
                seed(grid, ext, q, ((sy - 1) * sz + z) * sx + x);        // y = sy-1
            }
        }
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                seed(grid, ext, q, (y * sz + z) * sx);                   // x = 0
                seed(grid, ext, q, (y * sz + z) * sx + sx - 1);          // x = sx-1
            }
        }
        int plane = sx * sz;
        while (!q.isEmpty()) {
            int c = q.poll();
            int x = c % sx;
            int rest = c / sx;
            int z = rest % sz;
            int y = rest / sz;
            if (x > 0) seed(grid, ext, q, c - 1);
            if (x < sx - 1) seed(grid, ext, q, c + 1);
            if (z > 0) seed(grid, ext, q, c - sx);
            if (z < sz - 1) seed(grid, ext, q, c + sx);
            if (y > 0) seed(grid, ext, q, c - plane);
            if (y < sy - 1) seed(grid, ext, q, c + plane);
        }
        boolean[] interior = new boolean[grid.length];
        for (int i = 0; i < grid.length; i++) interior[i] = grid[i] == 1 && !ext[i];
        return interior;
    }

    private static void seed(byte[] grid, boolean[] ext, IntQ q, int i) {
        if (i < 0 || i >= grid.length || ext[i] || grid[i] == 2) return;
        ext[i] = true;
        q.add(i);
    }

    /** File d'entiers auto-compactante (frontiere du flood fill : quelques milliers). */
    private static final class IntQ {
        private int[] a = new int[1 << 12];
        private int head, tail;

        void add(int v) {
            if (tail == a.length) {
                if (head > a.length / 2) {
                    System.arraycopy(a, head, a, 0, tail - head);
                    tail -= head;
                    head = 0;
                } else {
                    a = Arrays.copyOf(a, a.length * 2);
                }
            }
            a[tail++] = v;
        }

        boolean isEmpty() { return head == tail; }

        int poll() { return a[head++]; }
    }

    // ------------------------------------------------------------ palette / air

    /**
     * Index de palette correspondant a de l'air. {@code null} si la palette est
     * non conforme OU si elle ne contient aucun air (fichier deja creux).
     */
    private static boolean[] airIndices(Tag single, Tag multi) {
        java.util.List<boolean[]> all = new java.util.ArrayList<>(2);
        if (single instanceof ListTag l) {
            boolean[] a = airOfPalette(l);
            if (a == null) return null;
            all.add(a);
        }
        if (multi instanceof ListTag l) {
            for (Tag t : l) {
                if (!(t instanceof ListTag pl)) return null;
                boolean[] a = airOfPalette(pl);
                if (a == null) return null;
                all.add(a);
            }
        }
        if (all.isEmpty()) return null;
        int len = 0;
        boolean any = false;
        for (boolean[] a : all) {
            len = Math.max(len, a.length);
            for (boolean b : a) any |= b;
        }
        if (!any || len == 0 || len > MAX_PALETTE) return null;
        // Union prudente : un index n'est "air" que si TOUTES les palettes le disent.
        boolean[] r = new boolean[len];
        for (int i = 0; i < len; i++) {
            boolean air = true;
            for (boolean[] a : all) if (i >= a.length || !a[i]) { air = false; break; }
            r[i] = air;
        }
        return r;
    }

    private static boolean[] airOfPalette(ListTag palette) {
        boolean[] r = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            if (!(palette.get(i) instanceof CompoundTag c)) return null;
            r[i] = isAirId(c.getString("Name"));
        }
        return r;
    }

    private static boolean isAirId(String id) {
        if (id == null) return false;
        String s = id.trim();
        int c = s.indexOf(':');
        if (c >= 0) s = s.substring(c + 1);
        return s.equals("air") || s.equals("cave_air") || s.equals("void_air");
    }

    // ------------------------------------------------------------------- NBT IO

    private static String readName(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Lit un tag generique (petits tags uniquement : palette, size, entities). */
    private static Tag readTag(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return ByteTag.valueOf(in.readByte());
            case 2: return ShortTag.valueOf(in.readShort());
            case 3: return IntTag.valueOf(in.readInt());
            case 4: return LongTag.valueOf(in.readLong());
            case 5: return FloatTag.valueOf(in.readFloat());
            case 6: return DoubleTag.valueOf(in.readDouble());
            case 7: {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                return new ByteArrayTag(b);
            }
            case 8: return StringTag.valueOf(readName(in));
            case 9: {
                int elem = in.readUnsignedByte();
                int size = in.readInt();
                ListTag l = new ListTag();
                if (size > 0) {
                    if (elem == 0) return null;
                    for (int i = 0; i < size; i++) {
                        Tag t = readTag(in, elem);
                        if (t == null) return null;
                        l.add(t);
                    }
                }
                return l;
            }
            case 10: return readCompound(in, true);
            case 11: {
                int[] v = new int[in.readInt()];
                for (int i = 0; i < v.length; i++) v[i] = in.readInt();
                return new IntArrayTag(v);
            }
            case 12: {
                long[] v = new long[in.readInt()];
                for (int i = 0; i < v.length; i++) v[i] = in.readLong();
                return new LongArrayTag(v);
            }
            default: throw new IOException("tag inconnu " + type);
        }
    }

    /**
     * Lit un compound. En mode "entree de bloc" ({@code keepAll=false}), les cles
     * inconnues sont sautees et l'objet retourne ne porte que pos/state/nbt : c'est
     * ce qui evite des millions d'objets inutiles.
     */
    private static CompoundTag readCompound(DataInputStream in, boolean keepAll) throws IOException {
        CompoundTag c = new CompoundTag();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) return c;
            String key = readName(in);
            if (keepAll || key.equals("pos") || key.equals("state") || key.equals("nbt")) {
                Tag t = readTag(in, type);
                if (t != null) c.put(key, t);
            } else {
                skip(in, type);
            }
        }
    }

    private static void skip(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(in.readInt());
            case 8 -> in.skipNBytes(in.readUnsignedShort());
            case 9 -> {
                int elem = in.readUnsignedByte();
                int size = in.readInt();
                if (size > 0) {
                    if (elem == 0) throw new IOException("liste sans type");
                    for (int i = 0; i < size; i++) skip(in, elem);
                }
            }
            case 10 -> {
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) break;
                    readName(in);
                    skip(in, t);
                }
            }
            case 11 -> in.skipNBytes((long) in.readInt() * 4L);
            case 12 -> in.skipNBytes((long) in.readInt() * 8L);
            default -> throw new IOException("tag inconnu " + type);
        }
    }

    private static int intValue(Tag t) {
        if (t instanceof IntTag i) return i.getAsInt();
        if (t instanceof ShortTag s) return s.getAsInt();
        return 0;
    }

    private static ListTag asList(Tag t) { return t instanceof ListTag l ? l : null; }

    private static long packPos(CompoundTag e) {
        if (!(e.get("pos") instanceof ListTag p) || p.size() < 3) return 0L;
        return BlockPos.asLong(p.getInt(0), p.getInt(1), p.getInt(2));
    }

    // T73b : decodage par les getters VANILLA. Le decodage maison etait FAUX :
    // BlockPos.asLong range Y dans les BITS BAS (Y_OFFSET=0, Z_OFFSET=12,
    // X_OFFSET=38), et non l'inverse -- une position (x,y,z) relue donnait
    // (z*4096+y, 0, x) : la boite de la structure explosait (zone d'edition de
    // 274 648 blocs de large pour citadel au lieu de 104). Les getters vanilla
    // sont la seule source de verite.
    private static int unpackX(long v) { return BlockPos.getX(v); }
    private static int unpackY(long v) { return BlockPos.getY(v); }
    private static int unpackZ(long v) { return BlockPos.getZ(v); }
}
