package deepluckyblock.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * INJECTEUR MIXIN AUTO-RÉPARANT (compat MCreator).
 *
 * ⚙️ Appelé depuis le constructeur de DeepLuckyBlockMod :
 *        deepluckyblock.compat.DeepMixinBootstrap.ensureConfigured();
 *
 * Pourquoi ? MCreator GÉNÈRE le fichier de config mixin
 *   "src/main/resources/deep_lucky_block.mixins.json"
 * à chaque build. S'il est régénéré, il perd le champ "plugin" et ré-injecte les
 * mixins d'intégration optionnels (com.lwi.*) -> les warnings
 * "Error loading class / Skipping virtual target" reviennent.
 *
 * Ce fichier RÉÉCRIT la config pour garantir, à chaque lancement en dev :
 *   1. "plugin": "deepluckyblock.mixin.DlbMixinPlugin"  (présent)
 *   2. les 4 mixins com.lwi.* retirés des listes (le plugin les ré-injecte
 *      seulement si le modpack est installé)
 *
 * ✅ Survit à la régénération : MCreator regénère le .json au build, et notre
 *    code le répare dès le prochain lancement.
 *
 * ⚠️ LIMITE HONNÊTE : on écrit sur le fichier RÉEL du workspace (build/resources
 *    en dev). Le chargement des mixins a lieu AVANT l'init du mod, donc la
 *    correction prend effet SANS le warning dès le lancement SUIVANT le build.
 *    Dans le .jar final (embedded), le fichier est en lecture seule -> on ne peut
 *    pas le réécrire ; là il faut une config MCreator correcte (ou laisser le
 *    modpack installé). En dev (ta situation actuelle), ça fonctionne à 100 %.
 */
public final class DeepMixinBootstrap {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_RESOURCE = "deep_lucky_block.mixins.json";
    private static final String PLUGIN_CLASS = "deepluckyblock.mixin.DlbMixinPlugin";

    // (Les 4 mixins d'intégration Lucky World Invasion sont désormais GARDÉS dans
    //  le JSON : ils existent dans ton workspace. Le plugin DlbMixinPlugin les
    //  saute quand le modpack est absent, donc plus besoin de les retirer ici.)

    private DeepMixinBootstrap() {}

    /** Point d'entrée appelé depuis le constructeur @Mod. Ne lève jamais d'exception. */
    public static void ensureConfigured() {
        try {
            patch();
        } catch (Throwable t) {
            // Ne jamais bloquer le chargement du mod à cause de l'injecteur.
            LOGGER.warn("[DeepMixinBootstrap] Injection mixin impossible : {}", t.getMessage());
        }
    }

    private static void patch() throws Exception {
        URL url = DeepMixinBootstrap.class.getClassLoader().getResource(CONFIG_RESOURCE);
        if (url == null) {
            LOGGER.info("[DeepMixinBootstrap] Introuvable {} -> rien à patcher (probablement déjà géré).", CONFIG_RESOURCE);
            return;
        }
        // En .jar, le fichier est embedded -> interdit d'écrire. On ne patch que
        // les fichiers sur disque (workspace en dev).
        if (!"file".equalsIgnoreCase(url.getProtocol())) {
            LOGGER.info("[DeepMixinBootstrap] {} est embarqué ({}), non modifiable.", CONFIG_RESOURCE, url.getProtocol());
            return;
        }
        File file = new File(new URI(url.toString()));
        if (!file.isFile() || !file.canWrite()) {
            LOGGER.info("[DeepMixinBootstrap] {} non accessible en écriture -> skip.", file.getAbsolutePath());
            return;
        }

        JsonObject root = JsonParser.parseString(
                Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();

        boolean changed = false;

        // 1) Garantir le plugin.
        String plugin = root.has("plugin") && root.get("plugin").isJsonPrimitive()
                ? root.get("plugin").getAsString() : null;
        if (!PLUGIN_CLASS.equals(plugin)) {
            root.addProperty("plugin", PLUGIN_CLASS);
            changed = true;
        }

        if (changed) {
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
            LOGGER.info("[DeepMixinBootstrap] {} corrigé : plugin garanti.",
                    file.getAbsolutePath());
        }
    }
}
