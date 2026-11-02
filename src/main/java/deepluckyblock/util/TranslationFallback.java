package deepluckyblock.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * TranslationFallback — filet de secours PARTAGÉ pour toute clé de
 * traduction du mod (noms, descriptions d'enchantement, combos et UI).
 *
 * T316-LANG-SWITCH :
 * Recharge automatiquement le cache dès que le joueur change la langue du jeu
 * (ex. fr_fr <-> en_us) et lit à la fois /assets/deep_lucky_block/lang/<lang>.json
 * et le miroir /deepluckyblock_patch/lang/<lang>.json afin qu'aucune régénération
 * MCreator ni aucun changement de langue à chaud ne casse l'anglais ou le français.
 */
public final class TranslationFallback {

    private TranslationFallback() {}

    private static final Map<String, String> CACHE = new HashMap<>();
    private static volatile String LOADED_LANG = null;

    /** Indique si la langue active du client est le français (false sur serveur ou client anglais). */
    public static boolean isFrench() {
        return "fr_fr".equals(resolveLangCode());
    }

    /**
     * Résout une clé de traduction : Language.getInstance() en priorité
     * (sur le client uniquement — sur le serveur, Language est toujours en_us),
     * puis le JSON packagé de la langue active en secours.
     */
    public static String resolve(String key) {
        if (key == null) return null;
        if (!deepluckyblock.DeepLuckyBlockMod.isServerThread()) {
            String viaLanguage = Language.getInstance().getOrDefault(key);
            if (viaLanguage != null && !viaLanguage.equals(key) && !viaLanguage.isBlank()) {
                return viaLanguage;
            }
        }
        return fallbackJsonLookup(key);
    }

    /**
     * Renvoie un Component prêt à être stylé/affiché :
     * - Sur le thread serveur : un vrai {@code Component.translatable(key)} afin que
     *   chaque client traduise le paquet réseau dans SA propre langue active (EN ou FR).
     * - Sur le client : {@code Component.translatable(key)} si Language connaît la clé,
     *   sinon le texte résolu dans le JSON de la langue active, sinon {@code readablePathFallback}.
     */
    public static net.minecraft.network.chat.MutableComponent translatableOrFallback(String key, String readablePathFallback) {
        if (deepluckyblock.DeepLuckyBlockMod.isServerThread()) {
            return Component.translatable(key);
        }
        if (Language.getInstance().has(key)) {
            String viaLanguage = Language.getInstance().getOrDefault(key);
            if (viaLanguage != null && !viaLanguage.equals(key) && !viaLanguage.isBlank()) {
                return Component.translatable(key);
            }
        }
        String json = fallbackJsonLookup(key);
        if (json != null && !json.isBlank()) {
            return Component.literal(json);
        }
        return Component.literal(readablePathFallback != null ? readablePathFallback : key);
    }

    private static synchronized void ensureLoaded() {
        String langCode = resolveLangCode();
        if (langCode.equals(LOADED_LANG)) return;
        CACHE.clear();
        LOADED_LANG = langCode;
        loadLangFileIntoCache("/assets/deep_lucky_block/lang/" + langCode + ".json");
        loadLangFileIntoCache("/deepluckyblock_patch/lang/" + langCode + ".json");
        if (!"en_us".equals(langCode)) {
            loadLangFileIntoCache("/assets/deep_lucky_block/lang/en_us.json");
            loadLangFileIntoCache("/deepluckyblock_patch/lang/en_us.json");
        }
    }

    private static String resolveLangCode() {
        if (deepluckyblock.DeepLuckyBlockMod.isServerThread()) {
            return "en_us";
        }
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.languageCode != null) {
                String code = mc.options.languageCode.toLowerCase(Locale.ROOT);
                return code.startsWith("fr") ? "fr_fr" : "en_us";
            }
        } catch (Throwable ignored) {
        }
        return "en_us";
    }

    private static void loadLangFileIntoCache(String classpathResource) {
        try (InputStream stream = TranslationFallback.class.getResourceAsStream(classpathResource)) {
            if (stream == null) return;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                if (root == null) return;
                for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                    com.google.gson.JsonElement v = e.getValue();
                    if (v instanceof JsonPrimitive prim && prim.isString()) {
                        CACHE.putIfAbsent(e.getKey(), prim.getAsString());
                    }
                }
            }
        } catch (Throwable ignored) {
            // Filet de secours best-effort : une erreur ici ne doit jamais faire planter le rendu.
        }
    }

    private static String fallbackJsonLookup(String key) {
        ensureLoaded();
        return CACHE.get(key);
    }
}
