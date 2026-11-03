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

    /** T360-NO-VANILLA-PARTICLES : canal des salves de mini-cubes 3D (remplace les particules vanilla). */
    public static final ResourceLocation PIXEL_PUFF_S2C =
            ResourceLocation.fromNamespaceAndPath(DeepLuckyBlockMod.MODID, "pixel_puff_s2c");

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
        // T360-NO-VANILLA-PARTICLES : salves de mini-cubes 3D génériques (halos, slashes, traînées).
        registrar.playToClient(
                PixelPuffMessage.TYPE,
                PixelPuffMessage.STREAM_CODEC,
                PixelPuffMessage::handle);
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
            return create(vfxId, kind, intensity, x, y, z, tx, ty, tz, paletteMask, 1.0f);
        }

        /**
         * T359-BIG-ANIM-SCALE : comme {@link #create(int, int, int, double, double, double,
         * double, double, double, int)}, avec en plus l'<b>échelle de taille</b> de la grosse
         * animation (déduite de la taille du mob touché).
         *
         * <p>L'échelle voyage dans l'octet haut de {@code packedMeta} (les 3 autres octets
         * portent déjà id / kind / intensité), quantifiée au 1/40 : 0 = 1.0 (défaut), 10 = 0.25,
         * 240 = 6.0. Aucun champ ni codec supplémentaire, donc aucun risque de désynchronisation
         * avec un client plus ancien.</p>
         */
        public static WeaponVfxMessage create(int vfxId, int kind, int intensity,
                                              double x, double y, double z,
                                              double tx, double ty, double tz,
                                              int paletteMask, float sizeScale) {
            int sizeByte = Math.round(net.minecraft.util.Mth.clamp(sizeScale, 0.0f, 6.35f) * 40.0f);
            if (sizeByte <= 0) sizeByte = 0;   // 0 = échelle par défaut (1.0)
            int meta = (vfxId & 0xFF)
                    | ((kind & 0xFF) << 8)
                    | ((Math.max(1, Math.min(15, intensity)) & 0xFF) << 16)
                    | ((sizeByte & 0xFF) << 24);
            return new WeaponVfxMessage(
                    meta,
                    paletteMask & deepluckyblock.enchantments.DeepElement.MASK_ALL,
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

    /**
     * T360-NO-VANILLA-PARTICLES : Payload Serveur -> Client ultra-compact qui remplace
     * <b>toutes</b> les particules vanilla du mod par une salve de mini-cubes 3D (Multi-Size
     * Pixel-Art). Le serveur n'envoie plus jamais {@code Level#sendParticles} : il envoie ce
     * paquet (une couleur, un nombre de cubes, une dispersion, une vitesse, une durée de vie),
     * et le client les dessine avec le même moteur de pixels que les VFX d'armes.
     *
     * <p>Compact : 4 champs (couleur, nombre de cubes, forme quantifiée, position), soit 13 octets
     * utiles — moins qu'un paquet de particules vanilla. Aucun risque de désynchronisation avec
     * un client plus ancien : le canal est versionné par le registre de payloads.</p>
     */
    public record PixelPuffMessage(
            int rgb,
            int count,
            int packedShape,
            FloatPos3 pos
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PixelPuffMessage> TYPE =
                new CustomPacketPayload.Type<>(PIXEL_PUFF_S2C);

        public static final StreamCodec<RegistryFriendlyByteBuf, PixelPuffMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, PixelPuffMessage::rgb,
                        ByteBufCodecs.VAR_INT, PixelPuffMessage::count,
                        ByteBufCodecs.INT, PixelPuffMessage::packedShape,
                        FloatPos3.STREAM_CODEC, PixelPuffMessage::pos,
                        PixelPuffMessage::new);

        /**
         * @param rgb     couleur 0xRRGGBB du mini-cube
         * @param count   nombre de cubes (1..12)
         * @param spread  dispersion en blocs (quantifiée au 1/64)
         * @param speed   vitesse d'éclatement (quantifiée au 1/128)
         * @param maxAge  durée de vie en ticks (6..60)
         */
        public static PixelPuffMessage create(int rgb, int count, double x, double y, double z,
                                              float spread, float speed, int maxAge) {
            int countQ = Math.max(1, Math.min(12, count));
            int spreadQ = Math.max(0, Math.min(255, Math.round(Math.max(0.0f, Math.min(4.0f, spread)) * 64.0f)));
            int speedQ = Math.max(0, Math.min(255, Math.round(Math.max(0.0f, Math.min(2.0f, speed)) * 128.0f)));
            int lifeQ = Math.max(6, Math.min(60, maxAge));
            int packedShape = spreadQ | (speedQ << 8) | (lifeQ << 16);
            return new PixelPuffMessage(rgb & 0xFFFFFF, countQ, packedShape,
                    new FloatPos3((float) x, (float) y, (float) z));
        }

        /** Dispersion décodée (blocs). */
        public float spread() {
            return (packedShape & 0xFF) / 64.0f;
        }

        /** Vitesse décodée. */
        public float speed() {
            return ((packedShape >>> 8) & 0xFF) / 128.0f;
        }

        /** Durée de vie décodée (ticks). */
        public int maxAge() {
            return (packedShape >>> 16) & 0xFF;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PixelPuffMessage message, IPayloadContext context) {
            context.enqueueWork(() -> PixelPuffClientBridge.handleClient(message));
        }
    }

    private DeepPayloads() {}
}
