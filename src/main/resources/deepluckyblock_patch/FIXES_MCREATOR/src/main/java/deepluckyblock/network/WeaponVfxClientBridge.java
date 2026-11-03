package deepluckyblock.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * T342-WEAPON-VFX-30 : Pont sécurisé Serveur Dédié / Client pour router {@link DeepPayloads.WeaponVfxMessage}
 * vers {@code WeaponWorldVfxManager} uniquement sur {@link Dist#CLIENT}.
 */
public final class WeaponVfxClientBridge {

    private WeaponVfxClientBridge() {}

    public static void handleClient(DeepPayloads.WeaponVfxMessage msg) {
        if (FMLEnvironment.dist != Dist.CLIENT || msg == null) {
            return;
        }
        ClientHolder.dispatch(msg);
    }

    private static final class ClientHolder {
        static void dispatch(DeepPayloads.WeaponVfxMessage msg) {
            int meta = msg.packedMeta();
            int vfxId = meta & 0xFF;
            int kind = (meta >>> 8) & 0xFF;
            int intensity = (meta >>> 16) & 0xFF;
            // T359-BIG-ANIM-SCALE : octet haut = échelle de la grosse animation (0 = 1.0 par défaut).
            int sizeByte = (meta >>> 24) & 0xFF;
            float sizeScale = (sizeByte == 0) ? 1.0f : (sizeByte / 40.0f);
            deepluckyblock.client.WeaponWorldVfxManager.enqueueNetworkVfx(
                    vfxId,
                    kind,
                    intensity,
                    msg.origin().x(),
                    msg.origin().y(),
                    msg.origin().z(),
                    msg.target().x(),
                    msg.target().y(),
                    msg.target().z(),
                    msg.paletteMask(),
                    sizeScale);
        }
    }
}
