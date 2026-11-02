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

    public static final ResourceLocation AIR_WALK_ANCHOR_C2S =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "air_walk_anchor_c2s");

    public static final ResourceLocation WEAPON_VFX_S2C =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "weapon_vfx_s2c");

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                DeepLuckyBlockModVariables.SavedDataSyncMessage.TYPE,
                DeepLuckyBlockModVariables.SavedDataSyncMessage.STREAM_CODEC,
                DeepLuckyBlockModVariables.SavedDataSyncMessage::handle);
        registrar.playToClient(
                WeaponVfxMessage.TYPE,
                WeaponVfxMessage.STREAM_CODEC,
                WeaponVfxMessage::handle);
        registrar.playToServer(
                CloudJumpMessage.TYPE,
                CloudJumpMessage.STREAM_CODEC,
                CloudJumpMessage::handle);
        registrar.playToServer(
                AirWalkAnchorMessage.TYPE,
                AirWalkAnchorMessage.STREAM_CODEC,
                AirWalkAnchorMessage::handle);
    }

    /**
     * T307-CLOUD-WALKER + T330-CLOUD-WALKER-X2-X5 : Packet Client -> Serveur déclenchant les sauts
     * aériens sur coussin de nuages avec synchronisation exacte de la direction et du sprint client.
     */
    public record CloudJumpMessage(
            int airJumpIndex,
            boolean movingHoriz,
            boolean sprinting,
            float moveYawDeg
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CloudJumpMessage> TYPE =
                new CustomPacketPayload.Type<>(CLOUD_JUMP_C2S);

        public static final StreamCodec<RegistryFriendlyByteBuf, CloudJumpMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, CloudJumpMessage::airJumpIndex,
                        ByteBufCodecs.BOOL, CloudJumpMessage::movingHoriz,
                        ByteBufCodecs.BOOL, CloudJumpMessage::sprinting,
                        ByteBufCodecs.FLOAT, CloudJumpMessage::moveYawDeg,
                        CloudJumpMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(CloudJumpMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer sp) {
                    UniversalEnchantmentHandler.performServerCloudJump(
                            sp,
                            message.airJumpIndex(),
                            message.movingHoriz(),
                            message.sprinting(),
                            message.moveYawDeg());
                }
            });
        }
    }

    /**
     * T320-AIRWALK-LOCKSTEP : Synchronise exactement le plancher d'ancrage Y calculé côté Client
     * (en ClientTickEvent.Pre avant la physique locale) avec le Serveur, ou Integer.MIN_VALUE
     * pour effacer immédiatement les barrières sous les pieds (Shift / retrait des bottes).
     */
    public record AirWalkAnchorMessage(int anchorFloorY) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AirWalkAnchorMessage> TYPE =
                new CustomPacketPayload.Type<>(AIR_WALK_ANCHOR_C2S);

        public static final StreamCodec<RegistryFriendlyByteBuf, AirWalkAnchorMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, AirWalkAnchorMessage::anchorFloorY,
                        AirWalkAnchorMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(AirWalkAnchorMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer sp) {
                    UniversalEnchantmentHandler.handleClientAirWalkAnchor(sp, message.anchorFloorY());
                }
            });
        }
    }

    /** Coordonnée 3D flottante compacte pour les payloads visuels S2C. */
    public record FloatPos3(float x, float y, float z) {
        public static final StreamCodec<RegistryFriendlyByteBuf, FloatPos3> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.FLOAT, FloatPos3::x,
                        ByteBufCodecs.FLOAT, FloatPos3::y,
                        ByteBufCodecs.FLOAT, FloatPos3::z,
                        FloatPos3::new);
    }

    /**
     * T342-WEAPON-VFX-30 : Payload Serveur -> Client ultra-compact déclenchant les animations 3D
     * d'impact, de critique amélioré, de siphon de sang et d'effets élémentaires/à distance.
     */
    public record WeaponVfxMessage(
            int packedMeta,
            int paletteMask,
            FloatPos3 origin,
            FloatPos3 target
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WeaponVfxMessage> TYPE =
                new CustomPacketPayload.Type<>(WEAPON_VFX_S2C);

        public static final StreamCodec<RegistryFriendlyByteBuf, WeaponVfxMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, WeaponVfxMessage::packedMeta,
                        ByteBufCodecs.VAR_INT, WeaponVfxMessage::paletteMask,
                        FloatPos3.STREAM_CODEC, WeaponVfxMessage::origin,
                        FloatPos3.STREAM_CODEC, WeaponVfxMessage::target,
                        WeaponVfxMessage::new);

        public static WeaponVfxMessage create(int vfxId, int kind, int intensity,
                                              double x, double y, double z,
                                              double tx, double ty, double tz) {
            return create(vfxId, kind, intensity, x, y, z, tx, ty, tz, 0);
        }

        public static WeaponVfxMessage create(int vfxId, int kind, int intensity,
                                              double x, double y, double z,
                                              double tx, double ty, double tz,
                                              int paletteMask) {
            int meta = (vfxId & 0xFF)
                    | ((kind & 0xFF) << 8)
                    | ((Math.max(1, Math.min(15, intensity)) & 0xFF) << 16);
            return new WeaponVfxMessage(
                    meta,
                    paletteMask & 0x3FF,
                    new FloatPos3((float) x, (float) y, (float) z),
                    new FloatPos3((float) tx, (float) ty, (float) tz));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(WeaponVfxMessage message, IPayloadContext context) {
            context.enqueueWork(() -> WeaponVfxClientBridge.handleClient(message));
        }
    }

    private DeepPayloads() {}
}
