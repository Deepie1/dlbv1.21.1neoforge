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
 * traduction du mod (noms ET descriptions d'enchantement).
 *
 * FIX (rapporté en jeu) : certains enchantements affichaient un TITRE brut
 * non traduit ("enchantment.deep_lucky_block.light_heart III" au lieu de
 * "Light Heart III"), alors même que la clé existe bel et bien dans
 * en_us.json/fr_fr.json (vérifié manuellement) -- symptôme identique à celui
 * déjà corrigé pour les DESCRIPTIONS (voir EnchantmentTooltipFixProcedure,
 * FIX v42), mais qui touchait auparavant UNIQUEMENT rawDescription() : le
 * NOM affiché (CustomEnchantment.formatNameMod -> Component.translatable)
 * n'avait, lui, AUCUN filet de secours et retombait donc sur la clé brute
 * dès que Language.getInstance() ne connaissait pas (encore/plus) la clé au
 * moment précis du rendu -- ce qui peut arriver de façon intermittente et
 * spécifique à certains enchantements selon l'ordre de (re)chargement des
 * ressources / l'état de ClientTranslationInjector.
 *
 * Cette classe centralise donc la même logique de repli (lecture directe du
 * JSON du mod packagé, jamais affectée par l'état de Language) pour qu'elle
 * profite à la fois au nom ET à la description, dans un seul endroit.
 */
public final class TranslationFallback {

    private TranslationFallback() {}

    private static final Map<String, String> CACHE = new HashMap<>();
    private static volatile boolean LOADED = false;

    /**
     * Résout une clé de traduction : Language.getInstance() en priorité (se
     * comporte normalement, respecte les resource packs/langues actives),
     * puis, UNIQUEMENT si la clé est inconnue de Language à cet instant, le
     * JSON packagé du mod en dernier recours. Renvoie {@code null} si
     * introuvable partout (clé vraiment absente).
     */
    public static String resolve(String key) {
        if (key == null) return null;
        String viaLanguage = Language.getInstance().getOrDefault(key);
        if (viaLanguage != null && !viaLanguage.equals(key) && !viaLanguage.isBlank()) {
            return viaLanguage;
        }
        return fallbackJsonLookup(key);
    }

    /**
     * Comme {@link #resolve} mais renvoie directement un Component prêt à
     * être stylé/affiché : un vrai {@code Component.translatable(key)} si
     * Language connaît la clé (préserve le comportement normal, y compris
     * les mises à jour à chaud si Language change ensuite), sinon un
     * {@code Component.literal(...)} construit à partir du filet de secours
     * JSON, sinon enfin un literal lisible dérivé du path en dernier
     * recours (jamais la clé brute affichée telle quelle).
     */
    public static net.minecraft.network.chat.MutableComponent translatableOrFallback(String key, String readablePathFallback) {
        String viaLanguage = Language.getInstance().getOrDefault(key);
        if (viaLanguage != null && !viaLanguage.equals(key) && !viaLanguage.isBlank()) {
            return Component.translatable(key);
        }
        String json = fallbackJsonLookup(key);
        if (json != null && !json.isBlank()) {
            return Component.literal(json);
        }
        return Component.literal(readablePathFallback != null ? readablePathFallback : key);
    }

    private static synchronized void ensureLoaded() {
        if (LOADED) return;
        LOADED = true;
        String langCode = resolveLangCode();
        loadLangFileIntoCache("/assets/deep_lucky_block/lang/" + langCode + ".json");
        if (!"en_us".equals(langCode)) {
            loadLangFileIntoCache("/assets/deep_lucky_block/lang/en_us.json");
        }
    }

    private static String resolveLangCode() {
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
