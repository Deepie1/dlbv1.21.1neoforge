package deepluckyblock.network;

import deepluckyblock.DeepLuckyBlockMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registre des payloads du mod (remplace le SimpleChannel Forge).
 *
 * 1.21.1 utilise le système CustomPacketPayload + PayloadRegistrar.
 * La version de protocole est "1" comme l'ancienne PROTOCOL_VERSION.
 *
 * Ajout d'un nouveau packet :
 *   1. Créer une record ... implements CustomPacketPayload (voir SavedDataSyncMessage)
 *   2. L'ici dans init() via registrar.registerPlayToClient / registerPlayToServer
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DeepPayloads {

    public static final ResourceLocation SAVED_DATA_SYNC =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "saved_data_sync");

    // PORT 1.21.1 : PayloadRegistrar ne se crée plus manuellement -> event
    // RegisterPayloadHandlersEvent (mod bus) + playToClient(Type, codec, handler).
    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                DeepLuckyBlockModVariables.SavedDataSyncMessage.TYPE,
                DeepLuckyBlockModVariables.SavedDataSyncMessage.STREAM_CODEC,
                DeepLuckyBlockModVariables.SavedDataSyncMessage::handle);
    }

    private DeepPayloads() {}
}
