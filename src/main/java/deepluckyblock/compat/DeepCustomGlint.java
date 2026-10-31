package deepluckyblock.compat;

import deepluckyblock.client.DeepGlintColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * DeepCustomGlint — applique un GLINT COLORÉ aux items enchantés de Deep's
 * Lucky Block, via le moteur de rendu de glint custom "Glint & Glamour"
 * (net.tunamods.customglint). Compatible ImmediatelyFast / Iris / Oculus,
 * y compris en MAIN et en INVENTAIRE.
 *
 * IMPORTANT : tout est en RÉFLEXION pour que le mod COMPILE sans la lib.
 * Si la lib n'est pas bundle/installée, tout est no-op (aucun crash).
 *
 * ---------------------------------------------------------------------------
 * CORRECTION du 2026-08-31 — signature réelle de l'API 1.7.0
 * ---------------------------------------------------------------------------
 * L'ancienne résolution cherchait :
 *     write(ItemStack, int, int)
 * Elle N'EXISTE PAS dans glint-and-glamour-api 1.7.0. Les 6 surcharges réelles
 * (vérifiées par réflexion sur le jar officiel) sont :
 *     write(ItemStack, ResourceLocation, int)                      <- celle-ci
 *     write(ItemStack, ResourceLocation, int[])
 *     write(ItemStack, ResourceLocation, int[], float, boolean, float, boolean)
 *     write(ItemStack, ResourceLocation, int[], float, boolean, float, boolean, int, float)
 *     write(ItemStack, ResourceLocation, int[], float, boolean, float, boolean, int, float, int)
 *     write(ItemStack, Layer[])
 * et WAVE est une ResourceLocation, pas un Integer.
 *
 * Conséquence avant correction : getMethod(...) levait NoSuchMethodException,
 * donc present=false, donc available()=false, donc applyThemed() était un
 * NO-OP PERMANENT et silencieux — aucun glint coloré n'aurait jamais été
 * visible en jeu.
 *
 * La résolution est maintenant tolérante : signature exacte d'abord, puis
 * recherche par forme (3 params : ItemStack, <design>, int), donc une future
 * variante du type « design » ne casse plus l'intégration.
 */
public final class DeepCustomGlint {

    private DeepCustomGlint() {}

    private static final String API_CLASS = "net.tunamods.customglint.common.CustomGlint";

    private static Class<?> apiClass;
    private static Method writeMethod;
    private static Object designValue;
    private static boolean present = false;
    private static boolean resolved = false;

    static {
        try {
            // initialize=false : on ne déclenche PAS le <clinit> de la lib pendant
            // la détection (sécurité serveur dédié). Il sera déclenché plus tard,
            // au premier design(), déjà protégé par un try/catch.
            apiClass = Class.forName(API_CLASS, false, DeepCustomGlint.class.getClassLoader());
            writeMethod = resolveWriteMethod(apiClass);
            designValue = resolveDesign(apiClass);
            present = writeMethod != null && designValue != null;
        } catch (Throwable ignored) {
            present = false;
        }
        resolved = true;
    }

    /**
     * write(ItemStack, design, int) : d'abord la signature exacte de la 1.7.0,
     * sinon toute surcharge à 3 paramètres de forme (ItemStack, ?, int).
     */
    private static Method resolveWriteMethod(Class<?> api) {
        try {
            return api.getMethod("write", ItemStack.class, ResourceLocation.class, int.class);
        } catch (Throwable ignored) {
            // fallthrough : recherche par forme
        }
        try {
            for (Method m : api.getMethods()) {
                if (!m.getName().equals("write")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == ItemStack.class && p[2] == int.class) return m;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Le design WAVE. C'est une ResourceLocation depuis la 1.7.0 ; on accepte
     * aussi un Integer (anciennes versions) et, en dernier recours, les fabriques
     * designFromName(String) / res(String) de la lib.
     */
    private static Object resolveDesign(Class<?> api) {
        try {
            Field f = api.getField("WAVE");
            Object v = f.get(null);
            if (v != null) return v;
        } catch (Throwable ignored) {}
        for (String factory : new String[]{"designFromName", "res"}) {
            try {
                Method m = api.getMethod(factory, String.class);
                Object v = m.invoke(null, "wave");
                if (v != null) return v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** La lib Glint & Glamour est-elle chargée ET utilisable ? */
    public static boolean available() {
        return present && resolved && writeMethod != null && designValue != null;
    }

    /**
     * Recolore le glint d'un ItemStack. Idempotent.
     *
     * <p><b>Règle du mod : la couleur du glint est celle du GRADE tiré par
     * GenerateLuckyItemProcedure.</b> Cette couleur est écrite dans le nom de
     * l'item par {@code recolorNameToRank} (l. 1122) ; on la relit ici avec le
     * même algorithme que {@code GlintColors}, donc les deux moteurs de glint
     * (mixins maison et Glint &amp; Glamour) affichent exactement la même teinte.
     *
     * <p>Repli : un item enchanté mais sans code couleur de rang (item de butin
     * vanilla, craft externe…) prend la couleur de thème de son enchantement le
     * plus fort.
     */
    public static void applyThemed(ItemStack stack) {
        if (!available() || stack == null || stack.isEmpty()) return;
        try {
            // Un glint n'a de sens que sur un item enchanté (la lib se branche sur
            // getFoilBuffer, appelé seulement si hasFoil()). Cela laisse aussi le
            // Bo' Cube de Bois intact, comme GlintColors le fait déjà.
            Map<Enchantment, Integer> enchants = deepluckyblock.util.DeepEnchant.map(stack); // PORT 1.21.1
            if (enchants.isEmpty()) return;

            // ---- 1) couleur du RANG (prioritaire) ----
            int grade = DeepGlintColor.gradeColor(stack);
            if (grade != 0) {
                write(stack, 0xFF000000 | (grade & 0xFFFFFF), "rang");
                return;
            }

            // ---- 2) repli : thème de l'enchantement ----
            int best = 0xFFFFFFFF;
            int bestStrength = -1;
            String bestId = null;
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(e.getKey());
                if (id == null) continue;
                String idStr = id.toString();
                int lvl = e.getValue() == null ? 0 : e.getValue();
                int color = DeepGlintColor.resolve(idStr, lvl);
                if (color == 0xFFFFFFFF) continue;             // pas de thème
                // Départage DÉTERMINISTE : l'ancien test "e.getValue() > bestStrength"
                // laissait le premier rencontré gagner à niveau égal, or enchants est
                // un HashMap : la couleur d'un item pouvait changer d'un tirage à
                // l'autre. D'où le critère « ID alphabétique » à niveau égal.
                if (best == 0xFFFFFFFF || lvl > bestStrength
                        || (lvl == bestStrength && idStr.compareTo(bestId) < 0)) {
                    best = color;
                    bestStrength = lvl;
                    bestId = idStr;
                }
            }
            if (best == 0xFFFFFFFF) return;                    // aucun thème
            write(stack, 0xFF000000 | (best & 0xFFFFFF), "thème " + bestId);
        } catch (Throwable t) {
            // silencieux : lib absente ou signature différente.
            if (DeepGlintColor.DEBUG) {
                System.out.println("[DLB-GLINT] échec application : " + t);
            }
        }
    }

    private static void write(ItemStack stack, int argb, String source) throws Exception {
        writeMethod.invoke(null, stack, designValue, argb);
        if (DeepGlintColor.DEBUG) {
            System.out.println("[DLB-GLINT] applied #" + Integer.toHexString(argb)
                    + " on " + stack.getHoverName().getString() + "  (" + source + ")");
        }
    }
}
