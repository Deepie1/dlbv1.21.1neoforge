package deepluckyblock.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Compatibilité auto avec ImmediatelyFast (modid "immediatelyfast").
 *
 * ImmediatelyFast court-circuite le rendu du glint dans l'inventaire/HUD par son
 * optimisation "hud_batching". Cette optimisation est DÉSACTIVABLE par config,
 * mais on ne veut pas que les joueurs touchent au fichier : on la désactive donc
 * automatiquement au démarrage (une seule clé, sans retirer le mod).
 *
 * - Idempotent : ne modifie le fichier QUE si la clé n'est pas déjà à false.
 * - Sans danger : ne touche qu'à "hud_batching", rien d'autre.
 * - Si le fichier n'existe pas encore (premier lancement), on ne fait rien :
 *   ImmediatelyFast le créera, et notre mod le corrigera au lancement suivant.
 */
@EventBusSubscriber(modid = "deep_lucky_block", value = Dist.CLIENT)
public final class ImmediatelyFastGlintCompat {

    private ImmediatelyFastGlintCompat() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("immediatelyfast")) return;

        try {
            Path cfgDir = FMLPaths.CONFIGDIR.get();
            Path file = cfgDir.resolve("immediatelyfast.json");
            if (!Files.exists(file)) {
                deepluckyblock.util.DebugLog.out("[DeepLuckyBlock] immediatelyfast présent mais config absente (le mod la créera).");
                return;
            }

            String src = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(src).getAsJsonObject();

            // hud_batching : le réglage documenté pour le glint dans l'inventaire/HUD.
            // experimental_screen_batching : batching des écrans/inventaire (peut aussi
            //   avaler le glint des items). On les désactive tous les deux (le mod reste).
            boolean changed = false;
            if (root.has("hud_batching") && root.get("hud_batching").getAsBoolean()) {
                root.addProperty("hud_batching", false);
                changed = true;
            }
            if (root.has("experimental_screen_batching") && root.get("experimental_screen_batching").getAsBoolean()) {
                root.addProperty("experimental_screen_batching", false);
                changed = true;
            }

            if (!changed) {
                // Déjà à false (ou absents) : rien à faire.
                deepluckyblock.util.DebugLog.out("[DeepLuckyBlock] immediatelyfast batching déjà désactivé (hud + screen) -> OK.");
                return;
            }

            Files.write(file, root.toString().getBytes(StandardCharsets.UTF_8));
            deepluckyblock.util.DebugLog.out("[DeepLuckyBlock] immediatelyfast hud_batching & experimental_screen_batching désactivés (fix glint). Relance une fois pour prendre effet.");
        } catch (Throwable t) {
            deepluckyblock.util.DebugLog.out("[DeepLuckyBlock] immediatelyfast compat: " + t);
        }
    }
}
