package deepluckyblock.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * T360-NO-VANILLA-PARTICLES : Pont sécurisé Serveur Dédié / Client pour router
 * {@link DeepPayloads.PixelPuffMessage} vers le moteur de pixels 3D
 * ({@code WeaponWorldVfxManager#spawnPixelPuff}).
 *
 * <p>C'est ce pont qui garantit qu'aucune particule Minecraft n'est plus jamais créée pour les
 * effets du mod : le serveur n'envoie qu'une couleur + une géométrie, le client dessine de vrais
 * mini-cubes 3D multi-tailles.</p>
 */
public final class PixelPuffClientBridge {

    private PixelPuffClientBridge() {}

    public static void handleClient(DeepPayloads.PixelPuffMessage msg) {
        if (FMLEnvironment.dist != Dist.CLIENT || msg == null) {
            return;
        }
        ClientHolder.dispatch(msg);
    }

    private static final class ClientHolder {
        static void dispatch(DeepPayloads.PixelPuffMessage msg) {
            deepluckyblock.client.WeaponWorldVfxManager.spawnPixelPuff(
                    msg.rgb(),
                    msg.pos().x(),
                    msg.pos().y(),
                    msg.pos().z(),
                    msg.count(),
                    msg.spread(),
                    msg.speed(),
                    msg.maxAge());
        }
    }
}
