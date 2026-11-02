package deepluckyblock.network;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.UniversalEnchantmentHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registre des payloads du mod (remplace le SimpleChannel Forge).
 *
 * 1.21.1 utilise le système CustomPacketPayload + PayloadRegistrar.
 * La version de protocole est "1" comme l'ancienne PROTOCOL_VERSION.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class DeepPayloads {

    public static final ResourceLocation SAVED_DATA_SYNC =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "saved_data_sync");

    public static final ResourceLocation CLOUD_JUMP_C2S =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "cloud_jump_c2s");

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                DeepLuckyBlockModVariables.SavedDataSyncMessage.TYPE,
                DeepLuckyBlockModVariables.SavedDataSyncMessage.STREAM_CODEC,
                DeepLuckyBlockModVariables.SavedDataSyncMessage::handle);
        registrar.playToServer(
                CloudJumpMessage.TYPE,
                CloudJumpMessage.STREAM_CODEC,
                CloudJumpMessage::handle);
    }

    /**
     * T307-CLOUD-WALKER : Packet Client -> Serveur déclenchant les 2 sauts aériens
     * supplémentaires (Double Saut + Triple Saut) sur un coussin de nuages.
     */
    public record CloudJumpMessage(int airJumpIndex) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CloudJumpMessage> TYPE =
                new CustomPacketPayload.Type<>(CLOUD_JUMP_C2S);

        public static final StreamCodec<RegistryFriendlyByteBuf, CloudJumpMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, CloudJumpMessage::airJumpIndex,
                        CloudJumpMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(CloudJumpMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer sp) {
                    UniversalEnchantmentHandler.performServerCloudJump(sp, message.airJumpIndex());
                }
            });
        }
    }

    private DeepPayloads() {}
}
