package deepluckyblock.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import deepluckyblock.client.EnchantAura;
import deepluckyblock.mixin.RenderStateShardConstantAccessor;
import deepluckyblock.mixin.RenderStateShardShaderAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * EnchantGlintTyped — construit les RenderType de glint TEINTÉS à la couleur
 * de l'aura (EnchantAura).
 *
 * IMPORTANT (correction production) : la construction NE dépend plus de la
 * réflexion sur les noms de dev (qui n'existent pas en jeu obfusqué ->
 * "build null"). On utilise :
 *   - les classes vanilla en référence DIRECTE (remappées par ForgeGradle) ;
 *   - les constantes privées via des ACCESSORS MIXIN (remappés par le refmap) ;
 *   - la correspondance de méthode PAR TYPES (indépendante du nom obfusqué).
 */
public final class EnchantGlintTyped {

    private EnchantGlintTyped() {}

    private static final Map<String, RenderType> RENDER_TYPE_CACHE = new HashMap<>();
    private static final Set<String> BUILD_FAILED = new HashSet<>();
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    private static final Set<RenderType> KNOWN_TYPES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** True si ce RenderType est un glint teinté construit par cette classe. */
    public static boolean isColoredGlint(RenderType rt) {
        return rt != null && KNOWN_TYPES.contains(rt);
    }

    /** Version teintée du RenderType de glint demandé, ou null si aucune couleur. */
    public static RenderType currentRenderType(String kind) {
        int rgb = EnchantAura.currentRgb();
        if (rgb == 0) { glintLog(kind, 0, "violet (aucune couleur capturée)"); return null; }
        int gradientRgb = EnchantAura.currentGradientRgb();
        int gradientMode = EnchantAura.currentGradientMode();
        boolean intense = EnchantAura.isCurrentIntense();
        int tier = EnchantAura.currentTier();
        boolean wornBoost = "armorEntityGlint".equals(kind) && EnchantAura.isCurrentWornArmorBoost();
        String[] params = glintParams(kind);
        if (params == null) return null;
        String tierPrefix = (!intense && !wornBoost && tier == EnchantAura.DEFAULT_GLINT_TIER)
                ? "N:"
                : ((wornBoost ? ("W" + EnchantAura.currentWornSlotTag() + ":") : "")
                        + "T" + tier + ":" + (intense ? "I:" : "N:"));
        String key = tierPrefix + Integer.toHexString(rgb) + ":" + Integer.toHexString(gradientRgb)
                + ":G" + gradientMode + ":" + kind;
        if (BUILD_FAILED.contains(key)) { glintLog(kind, rgb, "violet (build déjà raté)"); return null; }
        RenderType cached = RENDER_TYPE_CACHE.get(key);
        if (cached != null) { glintLog(kind, rgb, "OK (cache)"); return cached; }
        try {
            RenderType built = buildColoredGlint(rgb, gradientRgb, gradientMode, kind, params, intense, tier, wornBoost);
            if (built == null) { BUILD_FAILED.add(key); glintLog(kind, rgb, "violet (build null)"); return null; }
            KNOWN_TYPES.add(built);
            RENDER_TYPE_CACHE.put(key, built);
            // FIX CRASH "Not building!" (BufferBuilder) : un RenderType teinté
            // fraîchement construit est une INSTANCE JAVA NEUVE, jamais égale
            // (par référence) au RenderType vanilla correspondant. Or
            // MultiBufferSource.BufferSource ne lui associe AUCUN buffer FIXE
            // (RenderBuffers ne pré-enregistre que les 5 instances vanilla
            // exactes de glint) : chaque appel getBuffer(builtCustom) retombe
            // donc sur le "sharedBuffer" commun, et FERME de force le batch
            // partagé précédent (lastSharedType) dès qu'un DEUXIÈME RenderType
            // non-fixe est demandé juste après (ex: le buffer de texture de
            // l'objet, après celui du glint, dans getFoilBufferDirect/
            // getFoilBuffer). Le VertexMultiConsumer garde alors une référence
            // vers un BufferBuilder déjà build() (donc "not building"), et le
            // premier addVertex() dessus lève IllegalStateException.
            // Correctif : enregistrer immédiatement un buffer FIXE dédié pour
            // CE RenderType custom (comme le fait RegisterRenderBuffersEvent
            // pour les RenderType vanilla), afin qu'il ne passe plus jamais
            // par le sharedBuffer et ne ferme donc plus les batches voisins.
            registerFixedBuffer(built);
            glintLog(kind, rgb, "OK (nouveau)");
            return built;
        } catch (Throwable t) {
            BUILD_FAILED.add(key);
            glintLog(kind, rgb, "violet (exception " + t.getClass().getSimpleName() + ": " + t + ")");
            return null;
        }
    }

    private static long lastGlog = 0;
    private static void glintLog(String kind, int rgb, String result) {
        if (!EnchantAura.DEBUG) return;
        long now = System.currentTimeMillis();
        if (now - lastGlog < 400) return;
        lastGlog = now;
        deepluckyblock.util.DebugLog.out("[GLINT-RT] currentRenderType(" + kind + ") rgb=#"
                + Integer.toHexString(rgb) + " -> " + result);
    }

    /** Log de build pas-à-pas (gated DEBUG_VERBOSE) pour identifier l'étape en échec. */
    private static void bgl(String msg) {
        if (!EnchantAura.DEBUG || !EnchantAura.DEBUG_VERBOSE) return;
        deepluckyblock.util.DebugLog.out("[GLINT-BUILD] " + msg);
    }

    /** Paramètres exacts de chaque glint vanilla 1.20.1 (source vanilla). */
    private static String[] glintParams(String kind) {
        switch (kind) {
            // 1.21.1 : "glintDirect" et "armorGlint" supprimes de RenderType
            // (plus de RENDERTYPE_GLINT_DIRECT_SHADER / RENDERTYPE_ARMOR_GLINT_SHADER).
            case "glint":                return new String[]{"RENDERTYPE_GLINT_SHADER", "item", "GLINT_TEXTURING", "", "0"};
            case "glintTranslucent":     return new String[]{"RENDERTYPE_GLINT_TRANSLUCENT_SHADER", "item", "GLINT_TEXTURING", "ITEM_ENTITY_TARGET", "0"};
            case "entityGlint":          return new String[]{"RENDERTYPE_ENTITY_GLINT_SHADER", "entity", "ENTITY_GLINT_TEXTURING", "ITEM_ENTITY_TARGET", "0"};
            case "entityGlintDirect":    return new String[]{"RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER", "entity", "ENTITY_GLINT_TEXTURING", "", "0"};
            case "armorEntityGlint":     return new String[]{"RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER", "entity", "ENTITY_GLINT_TEXTURING", "", "1"};
            default:                     return null;
        }
    }

    private static RenderType buildColoredGlint(int rgb, int gradientRgb, int gradientMode, String kind, String[] params, boolean intense, int tier, boolean wornBoost) {
        Class<?> renderTypeClass = RenderType.class;
        Class<?> renderStateClass = RenderStateShard.class;
        Class<?> compositeStateClass;
        try { compositeStateClass = RenderType.CompositeState.class; }
        catch (Throwable t) { return null; }

        Object shader = state(params[0]);
        Object transparency = state("GLINT_TRANSPARENCY");
        Object colorWrite = state("COLOR_WRITE");
        Object noCull = state("NO_CULL");
        Object equalDepth = state("EQUAL_DEPTH_TEST");
        Object texturing = state(params[2]);
        bgl("shader=" + (shader==null?"NULL":shader.getClass().getSimpleName())
                + " transp=" + (transparency==null?"NULL":transparency.getClass().getSimpleName())
                + " write=" + (colorWrite==null?"NULL":colorWrite.getClass().getSimpleName())
                + " cull=" + (noCull==null?"NULL":noCull.getClass().getSimpleName())
                + " depth=" + (equalDepth==null?"NULL":equalDepth.getClass().getSimpleName())
                + " texturing=" + (texturing==null?"NULL":texturing.getClass().getSimpleName()));
        if (shader == null || transparency == null || colorWrite == null
                || noCull == null || equalDepth == null || texturing == null) return null;

        Object output = null;
        if (!params[3].isEmpty()) {
            output = state(params[3]);
            if (output == null) return null;
        }
        Object layering = null;
        if ("1".equals(params[4])) {
            layering = state("VIEW_OFFSET_Z_LAYERING");
            if (layering == null) return null;
        }

        try {
            ResourceLocation texture = getOrCreateTintedTexture(rgb, gradientRgb, gradientMode, params[1], kind, intense, tier, wornBoost);
            bgl("texture=" + texture);
            if (texture == null) return null;

            Class<?> textureShardClass = findTextureStateShardClass(renderStateClass);
            bgl("textureShardClass=" + textureShardClass);
            if (textureShardClass == null) return null;
            Constructor<?> textureShardCtor = textureShardClass.getDeclaredConstructor(
                    ResourceLocation.class, boolean.class, boolean.class);
            textureShardCtor.setAccessible(true);
            Object textureShard = textureShardCtor.newInstance(texture, true, false);

            Method builderMethod = findStaticMethodByParams(compositeStateClass, "builder", new Class<?>[0]);
            bgl("builderMethod=" + builderMethod);
            if (builderMethod == null) return null;
            Object builder = builderMethod.invoke(null);
            Class<?> builderClass = builder.getClass();

            invokeSetter(builderClass, builder, "setShaderState", shader);
            invokeSetter(builderClass, builder, "setTextureState", textureShard);
            invokeSetter(builderClass, builder, "setWriteMaskState", colorWrite);
            invokeSetter(builderClass, builder, "setCullState", noCull);
            invokeSetter(builderClass, builder, "setDepthTestState", equalDepth);
            invokeSetter(builderClass, builder, "setTransparencyState", transparency);
            invokeSetter(builderClass, builder, "setTexturingState", texturing);
            if (layering != null) invokeSetter(builderClass, builder, "setLayeringState", layering);
            if (output != null) invokeSetter(builderClass, builder, "setOutputState", output);

            Method createCompositeState = findMethodByParams(builderClass, "createCompositeState",
                    new Class<?>[]{boolean.class});
            bgl("createCompositeState=" + createCompositeState);
            if (createCompositeState == null) return null;
            Object compositeState = createCompositeState.invoke(builder, false);

            Method create = findStaticMethodByParams(renderTypeClass, "create", new Class<?>[]{
                    String.class, VertexFormat.class, VertexFormat.Mode.class,
                    int.class, boolean.class, boolean.class, compositeStateClass});
            bgl("create=" + create);
            if (create == null) return null;
            String name = "dlb_glint_" + (wornBoost ? "w2_" : "") + "t" + tier + "_" + (intense ? "intense_" : "")
                    + Integer.toHexString(rgb) + "_" + Integer.toHexString(gradientRgb)
                    + "_g" + gradientMode + "_" + kind.toLowerCase();
            RenderType rt = (RenderType) create.invoke(null, name,
                    DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                    256, false, false, compositeState);
            bgl("OK -> " + name);
            return rt;
        } catch (Throwable t) {
            bgl("EXCEPTION " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    /** Accès non appliqué signalé UNE fois, avec la cause exacte. */
    private static boolean accessorWarned = false;

    /**
     * Récupère une constante RenderStateShard via les accessors mixin.
     *
     * <p>Si les accessors ne sont PAS listés dans deep_lucky_block.mixins.json,
     * Mixin ne remplace pas le corps {@code throw new AssertionError()} : on
     * attrape donc ici et on affiche la cause une seule fois, au lieu de retomber
     * silencieusement sur le glint violet.
     */
    private static Object state(String name) {
        try {
            return stateUnsafe(name);
        } catch (Throwable t) {
            if (!accessorWarned) {
                accessorWarned = true;
                deepluckyblock.util.DebugLog.out("[GLINT-BUILD] IMPOSSIBLE de lire les constantes de "
                        + "RenderStateShard (" + t.getClass().getSimpleName() + ") : les ACCESSORS "
                        + "RenderStateShardConstantAccessor / RenderStateShardShaderAccessor ne sont "
                        + "probablement pas listés dans deep_lucky_block.mixins.json (section "
                        + "\"client\"), ou la config mixin n'est pas chargée. Le glint restera violet.");
            }
            return null;
        }
    }

    private static Object stateUnsafe(String name) {
        switch (name) {
            case "RENDERTYPE_GLINT_SHADER": return RenderStateShardShaderAccessor.deepen_glintShader();
            case "RENDERTYPE_GLINT_TRANSLUCENT_SHADER": return RenderStateShardShaderAccessor.deepen_glintTranslucentShader();
            case "RENDERTYPE_ENTITY_GLINT_SHADER": return RenderStateShardShaderAccessor.deepen_entityGlintShader();
            case "RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER": return RenderStateShardShaderAccessor.deepen_entityGlintDirectShader();
            case "RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER": return RenderStateShardShaderAccessor.deepen_armorEntityGlintShader();
            case "GLINT_TRANSPARENCY": return RenderStateShardConstantAccessor.deepen_glintTransparency();
            case "COLOR_WRITE": return RenderStateShardConstantAccessor.deepen_colorWrite();
            case "NO_CULL": return RenderStateShardConstantAccessor.deepen_noCull();
            case "EQUAL_DEPTH_TEST": return RenderStateShardConstantAccessor.deepen_equalDepthTest();
            case "GLINT_TEXTURING": return RenderStateShardConstantAccessor.deepen_glintTexturing();
            case "ENTITY_GLINT_TEXTURING": return RenderStateShardConstantAccessor.deepen_entityGlintTexturing();
            case "ITEM_ENTITY_TARGET": return RenderStateShardConstantAccessor.deepen_itemEntityTarget();
            case "VIEW_OFFSET_Z_LAYERING": return RenderStateShardConstantAccessor.deepen_viewOffsetZLayering();
            default: return null;
        }
    }

    private static void invokeSetter(Class<?> builderClass, Object builder, String name, Object arg) throws Exception {
        // Correspondance PAR TYPES (le nom peut être obfusqué en production).
        Method m = findMethodByParams(builderClass, name, new Class<?>[]{arg.getClass()});
        if (m == null) m = findMethodByParams(builderClass, name, new Class<?>[]{});
        if (m != null) m.invoke(builder, arg);
    }

    /** Trouve une méthode en ignorant le nom : on matche par nb de params + types assignables. */
    private static Method findMethodByParams(Class<?> cls, String name, Class<?>[] params) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != params.length) continue;
                Class<?>[] mp = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < params.length; i++) {
                    if (!box(mp[i]).isAssignableFrom(box(params[i]))) { ok = false; break; }
                }
                if (ok) { m.setAccessible(true); return m; }
            }
        }
        return null;
    }

    /** Trouve une méthode STATIQUE en ignorant le nom (les usines genre builder()/create()). */
    private static Method findStaticMethodByParams(Class<?> cls, String name, Class<?>[] params) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                if (Modifier.isStatic(m.getModifiers())) { m.setAccessible(true); return m; }
            } catch (NoSuchMethodException ignored) {}
        }
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != params.length) continue;
                Class<?>[] mp = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < params.length; i++) {
                    if (!box(mp[i]).isAssignableFrom(box(params[i]))) { ok = false; break; }
                }
                if (ok) { m.setAccessible(true); return m; }
            }
        }
        return null;
    }

    private static Class<?> box(Class<?> c) {
        if (c == boolean.class) return Boolean.class;
        if (c == int.class) return Integer.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == long.class) return Long.class;
        return c;
    }

    /** Trouve RenderStateShard$TextureStateShard par SA SIGNATURE de constructeur. */
    private static Class<?> findTextureStateShardClass(Class<?> renderStateClass) {
        for (Class<?> c : renderStateClass.getDeclaredClasses()) {
            try {
                c.getDeclaredConstructor(ResourceLocation.class, boolean.class, boolean.class);
                return c;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static ResourceLocation getOrCreateTintedTexture(int rgb, int gradientRgb, int gradientMode, String textureKind,
                                                             String kind, boolean intense, int tier, boolean wornBoost) {
        String tierTag = (!intense && !wornBoost && tier == EnchantAura.DEFAULT_GLINT_TIER)
                ? "N:"
                : ((wornBoost ? ("W" + EnchantAura.currentWornSlotTag() + ":") : "")
                        + "T" + tier + ":" + (intense ? "I:" : "N:"));
        String key = tierTag + Integer.toHexString(rgb) + ":" + Integer.toHexString(gradientRgb)
                + ":G" + gradientMode + ":" + textureKind;
        ResourceLocation cached = TEXTURE_CACHE.get(key);
        if (cached != null) return cached;
        try {
            Minecraft mc = Minecraft.getInstance();
            NativeImage img = loadGlint(textureKind);
            if (intense) {
                // Fenêtre active (frappe/tir/impact) : surbooste encore d'un cran au-dessus du palier passif
                double flashBoost = tier > EnchantAura.DEFAULT_GLINT_TIER
                        ? Math.min(0.18, (tier - EnchantAura.DEFAULT_GLINT_TIER) * 0.011)
                        : 0.0;
                for (int x = 0; x < img.getWidth(); x++) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        float gradient = gradientPosition(x, y, img.getWidth(), img.getHeight(), gradientMode);
                        int spatial = mixGradient(rgb, gradientRgb, gradient);
                        int tr = (spatial >> 16) & 0xFF, tg = (spatial >> 8) & 0xFF, tb = spatial & 0xFF;
                        int maxChan = Math.max(1, Math.max(tr, Math.max(tg, tb)));
                        double sr = tr * 255.0 / maxChan;
                        double sg = tg * 255.0 / maxChan;
                        double sb = tb * 255.0 / maxChan;
                        int px = img.getPixelRGBA(x, y);
                        int r = px & 0xFF;
                        int g = (px >> 8) & 0xFF;
                        int b = (px >> 16) & 0xFF;
                        int bright = Math.max(r, Math.max(g, b));
                        double norm = bright / 255.0;
                        double lifted = Math.min(1.0, (0.54 + flashBoost) + Math.pow(norm, 0.40) * 0.74);
                        double core = norm > 0.40 ? Math.pow((norm - 0.40) / 0.60, 1.20) * (95.0 + flashBoost * 160.0) : 0.0;
                        int outR = Math.min(255, (int) Math.round(sr * lifted + core));
                        int outG = Math.min(255, (int) Math.round(sg * lifted + core));
                        int outB = Math.min(255, (int) Math.round(sb * lifted + core));
                        img.setPixelRGBA(x, y, (0xFF << 24) | (outB << 16) | (outG << 8) | outR);
                    }
                }
            } else if (wornBoost) {
                // T339-WORN-ARMOR-GLINT : Sur les armures portées du Deep Lucky Block (armorEntityGlint),
                // enchanted_glint_entity.png est nativement beaucoup plus sombre (~0.38) qu'enchanted_glint_item.png (~0.85)
                // et subit le blend additif au carré (SRC_COLOR, ONE). On monte uniquement le rendu PORTÉ de +2 crans nets
                // tout en laissant le rendu item dans l'inventaire/en main strictement intact !
                int tierDelta = tier - EnchantAura.DEFAULT_GLINT_TIER; // -3 .. +16
                // T349-GLINT-UNIFORM : la montée en palier était TROP forte (1.88 + 0.045/tier, noyau
                // jusqu'à +75) et s'appliquait identiquement à toutes les pièces : un plastron de palier
                // élevé devenait un bloc lumineux comparé aux bottes. On normalise :
                //  - montée en palier adoucie et plafonnée,
                //  - noyau très réduit (plus de « tache blanche »),
                //  - alpha plafonné (220 -> 186) pour rester une brillance, pas une lampe.
                double tierClamped = Math.max(-3.0, Math.min(8.0, tierDelta));
                double waveScale = 1.34 + 0.020 * tierClamped;
                double floor = Math.max(0.0, 0.085 + 0.014 * tierClamped);
                double gamma = Math.max(0.52, 0.92 - 0.016 * tierClamped);
                double coreStrength = tierClamped > 4 ? (tierClamped - 4) * 2.2 : 0.0;
                // Compensation par emplacement (le plastron couvre le plus de surface)
                double slotFactor = EnchantAura.currentWornSlotFactor();

                for (int x = 0; x < img.getWidth(); x++) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        float gradient = gradientPosition(x, y, img.getWidth(), img.getHeight(), gradientMode);
                        int spatial = mixGradient(rgb, gradientRgb, gradient);
                        int tr = (spatial >> 16) & 0xFF, tg = (spatial >> 8) & 0xFF, tb = spatial & 0xFF;
                        int maxChan = Math.max(1, Math.max(tr, Math.max(tg, tb)));
                        double satBlend = tier >= EnchantAura.DEFAULT_GLINT_TIER ? 0.58 : 0.30;
                        double effR = tr + (tr * 255.0 / maxChan - tr) * satBlend;
                        double effG = tg + (tg * 255.0 / maxChan - tg) * satBlend;
                        double effB = tb + (tb * 255.0 / maxChan - tb) * satBlend;
                        int px = img.getPixelRGBA(x, y);
                        int a = (px >> 24) & 0xFF;
                        int r = px & 0xFF;
                        int g = (px >> 8) & 0xFF;
                        int b = (px >> 16) & 0xFF;
                        double norm = Math.max(r, Math.max(g, b)) / 255.0;
                        double wave = Math.min(1.0, norm * waveScale);
                        double lifted = Math.min(1.0, floor + Math.pow(wave, gamma) * (0.86 - floor * 0.45));
                        // On n'applique la compensation que sur la partie « éclat » (au-dessus du plancher)
                        lifted = floor + (lifted - floor) * slotFactor;
                        double core = (coreStrength > 0.0 && wave > 0.72)
                                ? Math.pow((wave - 0.72) / 0.28, 1.20) * coreStrength * slotFactor
                                : 0.0;
                        int outR = Math.min(255, (int) Math.round(effR * lifted + core));
                        int outG = Math.min(255, (int) Math.round(effG * lifted + core));
                        int outB = Math.min(255, (int) Math.round(effB * lifted + core));
                        int outA = Math.min(255, Math.max(a, 186));
                        img.setPixelRGBA(x, y, (outA << 24) | (outB << 16) | (outG << 8) | outR);
                    }
                }
            } else if (tier == EnchantAura.DEFAULT_GLINT_TIER) {
                for (int x = 0; x < img.getWidth(); x++) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        float gradient = gradientPosition(x, y, img.getWidth(), img.getHeight(), gradientMode);
                        int spatial = mixGradient(rgb, gradientRgb, gradient);
                        int tr = (spatial >> 16) & 0xFF, tg = (spatial >> 8) & 0xFF, tb = spatial & 0xFF;
                        int px = img.getPixelRGBA(x, y);
                        int a = (px >> 24) & 0xFF;
                        int r = px & 0xFF;
                        int g = (px >> 8) & 0xFF;
                        int b = (px >> 16) & 0xFF;
                        int bright = Math.max(r, Math.max(g, b));
                        img.setPixelRGBA(x, y, (a << 24)
                                | ((tb * bright / 255) << 16)
                                | ((tg * bright / 255) << 8)
                                | (tr * bright / 255));
                    }
                }
            } else if (tier < EnchantAura.DEFAULT_GLINT_TIER) {
                // Paliers 1..3 : visibilité adoucie sous le palier par défaut (/glint -)
                double scale = 0.36 + 0.16 * tier; // 0.52, 0.68, 0.84
                for (int x = 0; x < img.getWidth(); x++) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        float gradient = gradientPosition(x, y, img.getWidth(), img.getHeight(), gradientMode);
                        int spatial = mixGradient(rgb, gradientRgb, gradient);
                        int tr = (spatial >> 16) & 0xFF, tg = (spatial >> 8) & 0xFF, tb = spatial & 0xFF;
                        int px = img.getPixelRGBA(x, y);
                        int a = (px >> 24) & 0xFF;
                        int r = px & 0xFF;
                        int g = (px >> 8) & 0xFF;
                        int b = (px >> 16) & 0xFF;
                        double k = (Math.max(r, Math.max(g, b)) / 255.0) * scale;
                        int outR = Math.min(255, (int) Math.round(tr * k));
                        int outG = Math.min(255, (int) Math.round(tg * k));
                        int outB = Math.min(255, (int) Math.round(tb * k));
                        img.setPixelRGBA(x, y, (a << 24) | (outB << 16) | (outG << 8) | outR);
                    }
                }
            } else {
                // T324-GLINT-TIERS : Paliers 5..20 (/glint +) calibrés pour le blend additif au carré (SRC_COLOR, ONE) !
                double u = (tier - EnchantAura.DEFAULT_GLINT_TIER)
                        / (double) (EnchantAura.MAX_GLINT_TIER - EnchantAura.DEFAULT_GLINT_TIER); // 1/16 .. 16/16
                double gamma = 1.0 - 0.64 * u;                 // 0.96 -> 0.36 (élargit progressivement les ondes)
                double floor = 0.62 * Math.pow(u, 0.92);       // 0.048 -> 0.62 (halo ambiant permanent sur toute la surface)
                double gain  = 1.0 + 0.42 * u;                 // 1.026 -> 1.42
                double coreStrength = u > 0.30 ? ((u - 0.30) / 0.70) * 108.0 : 0.0;

                for (int x = 0; x < img.getWidth(); x++) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        float gradient = gradientPosition(x, y, img.getWidth(), img.getHeight(), gradientMode);
                        int spatial = mixGradient(rgb, gradientRgb, gradient);
                        int tr = (spatial >> 16) & 0xFF, tg = (spatial >> 8) & 0xFF, tb = spatial & 0xFF;
                        int maxChan = Math.max(1, Math.max(tr, Math.max(tg, tb)));
                        double satBlend = Math.min(1.0, u * 1.15);
                        double effR = tr + (tr * 255.0 / maxChan - tr) * satBlend;
                        double effG = tg + (tg * 255.0 / maxChan - tg) * satBlend;
                        double effB = tb + (tb * 255.0 / maxChan - tb) * satBlend;
                        int px = img.getPixelRGBA(x, y);
                        int a = (px >> 24) & 0xFF;
                        int r = px & 0xFF;
                        int g = (px >> 8) & 0xFF;
                        int b = (px >> 16) & 0xFF;
                        double norm = Math.max(r, Math.max(g, b)) / 255.0;
                        double lifted = Math.min(1.0, floor + Math.pow(norm, gamma) * (1.0 - floor * 0.60) * gain);
                        double core = (coreStrength > 0.0 && norm > 0.40)
                                ? Math.pow((norm - 0.40) / 0.60, 1.20) * coreStrength
                                : 0.0;
                        int outR = Math.min(255, (int) Math.round(effR * lifted + core));
                        int outG = Math.min(255, (int) Math.round(effG * lifted + core));
                        int outB = Math.min(255, (int) Math.round(effB * lifted + core));
                        int outA = Math.max(a, (int) Math.round(255.0 * Math.min(1.0, 0.45 + 0.55 * u)));
                        img.setPixelRGBA(x, y, (outA << 24) | (outB << 16) | (outG << 8) | outR);
                    }
                }
            }
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("deep_lucky_block",
                    "lucky_glint_" + (wornBoost ? "w2_" : "") + "t" + tier + "_" + (intense ? "intense_" : "")
                            + kind.toLowerCase() + "_" + Integer.toHexString(rgb) + "_"
                            + Integer.toHexString(gradientRgb) + "_g" + gradientMode + ".png");
            mc.getTextureManager().register(loc, new DynamicTexture(img));
            TEXTURE_CACHE.put(key, loc);
            return loc;
        } catch (Throwable t) {
            return null;
        }
    }

    private static float gradientPosition(int x, int y, int width, int height, int mode) {
        if (mode == 0) {
            float v = y / (float) Math.max(1, height - 1);
            return v * v * (3.0f - 2.0f * v);
        }
        // Roue A -> B -> A : le point 0/360 degrés possède la même couleur, donc aucune couture.
        float angle = (float) Math.atan2(y - height * 0.5f, x - width * 0.5f);
        float phase = (angle + (float) Math.PI) / (float) (Math.PI * 2.0);
        float tri = phase < 0.5f ? phase * 2.0f : 2.0f - phase * 2.0f;
        return tri * tri * (3.0f - 2.0f * tri);
    }

    private static int mixGradient(int a, int b, float u) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(ar + (br - ar) * u) << 16)
                | (Math.round(ag + (bg - ag) * u) << 8)
                | Math.round(ab + (bb - ab) * u);
    }

    private static NativeImage loadGlint(String textureKind) throws Exception {
        String path = "entity".equals(textureKind)
                ? "textures/misc/enchanted_glint_entity.png"
                : "textures/misc/enchanted_glint_item.png";
        var opt = Minecraft.getInstance().getResourceManager()
                .getResource(ResourceLocation.parse(path));
        if (opt.isPresent()) {
            try (var in = opt.get().open()) { return NativeImage.read(in); }
        }
        return generateGlint();
    }

    // =======================================================================
    // FIX CRASH "IllegalStateException: Not building!" (BufferBuilder)
    // =======================================================================
    // Voir le commentaire détaillé au-dessus de l'appel dans currentRenderType().
    // On enregistre chaque RenderType teinté fraîchement construit comme
    // "fixed buffer" dans TOUTES les MultiBufferSource.BufferSource connues
    // (celle des entités/items ET celle de l'inventaire, qui sont en fait la
    // MÊME instance partagée — Minecraft.renderBuffers().bufferSource() —
    // mais on couvre aussi crumblingBufferSource() par prudence si jamais un
    // jour un mod tiers ou une version future les sépare).
    private static final Set<Object> PATCHED_BUFFER_SOURCES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private static void registerFixedBuffer(RenderType built) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.renderBuffers() == null) return;
            patchBufferSource(mc.renderBuffers().bufferSource(), built);
        } catch (Throwable t) {
            bgl("registerFixedBuffer EXCEPTION " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void patchBufferSource(MultiBufferSource.BufferSource source, RenderType built) {
        if (source == null) return;
        try {
            Field fixedField = findFixedBuffersField(source.getClass());
            if (fixedField == null) {
                bgl("patchBufferSource: champ fixedBuffers introuvable");
                return;
            }
            fixedField.setAccessible(true);
            Object rawMap = fixedField.get(source);
            if (!(rawMap instanceof Map)) return;
            @SuppressWarnings("unchecked")
            Map<RenderType, ByteBufferBuilder> fixedBuffers = (Map<RenderType, ByteBufferBuilder>) rawMap;
            if (fixedBuffers.containsKey(built)) return; // déjà patché (même instance)
            fixedBuffers.put(built, new ByteBufferBuilder(built.bufferSize()));
            bgl("patchBufferSource: buffer fixe enregistré pour " + built);
        } catch (Throwable t) {
            bgl("patchBufferSource EXCEPTION " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * Trouve le champ {@code fixedBuffers} de {@code MultiBufferSource.BufferSource}.
     * D'abord par nom (mappings officiels Mojang, stables en dev ET en prod sous
     * NeoForge), puis par TYPE exact {@link java.util.SequencedMap} en secours
     * (le seul champ de ce type exact dans la classe — {@code startedBuilders}
     * est un {@code Map} brut, {@code sharedBuffer} est un {@code ByteBufferBuilder},
     * {@code lastSharedType} est un {@code RenderType}).
     */
    private static Field findFixedBuffersField(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("fixedBuffers");
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == java.util.SequencedMap.class) return f;
            }
        }
        return null;
    }

    private static NativeImage generateGlint() {
        int size = 64;
        NativeImage img = new NativeImage(size, size, true);
        for (int x = 0; x < size; x++)
            for (int y = 0; y < size; y++) {
                double v = 0.5 + 0.5 * Math.sin((x + y) * Math.PI * 2.0 * 2.0 / size);
                int b = (int) (255 * Math.pow(v, 2));
                img.setPixelRGBA(x, y, (255 << 24) | (b << 16) | (b << 8) | b);
            }
        return img;
    }
}
