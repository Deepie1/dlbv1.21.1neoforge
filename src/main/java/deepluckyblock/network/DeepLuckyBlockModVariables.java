package deepluckyblock.network;

import deepluckyblock.DeepLuckyBlockMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * PORT 1.20.1 -> 1.21.1 (NeoForge).
 *
 * Changements par rapport à la version Forge :
 *  - SimpleChannel / NetworkEvent -> CustomPacketPayload + PayloadRegistrar
 *  - PacketDistributor.PLAYER.with(...)  -> PacketDistributor.sendToPlayer(...)
 *  - PacketDistributor.DIMENSION.with(..) -> PacketDistributor.sendToDimension(...)
 *  - PacketDistributor.ALL.noArg()       -> PacketDistributor.sendToAllPlayers(...)
 *  - LevelTickEvent.Post (Phase.END) -> LevelTickEvent.Post
 *  - @Mod.EventBusSubscriber -> @EventBusSubscriber (neoforged)
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public class DeepLuckyBlockModVariables {
    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        // Les payloads sont enregistrés dans DeepPayloads.init(modBus)
        // (appelé depuis le constructeur @Mod) — plus besoin d'enregistrer ici.
    }

    @EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
    public static class EventBusVariableHandlers {
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SavedData mapdata = MapVariables.get(player.level());
                SavedData worlddata = WorldVariables.get(player.level());
                if (mapdata instanceof MapVariables mv)
                    PacketDistributor.sendToPlayer(player, new SavedDataSyncMessage(0, mv.luck));
                if (worlddata != null)
                    PacketDistributor.sendToPlayer(player, new SavedDataSyncMessage(1, 0.0));
            }
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SavedData worlddata = WorldVariables.get(player.level());
                if (worlddata != null)
                    PacketDistributor.sendToPlayer(player, new SavedDataSyncMessage(1, 0.0));
            }
        }

        @SubscribeEvent
        public static void onWorldTick(LevelTickEvent.Post event) {
            if (event.getLevel() instanceof ServerLevel level) {
                WorldVariables worldVariables = WorldVariables.get(level);
                if (worldVariables._syncDirty) {
                    // PORT 1.21.1 : PacketDistributor.sendToPlayersInDimension(ServerLevel, ...).
                    PacketDistributor.sendToPlayersInDimension(level, new SavedDataSyncMessage(1, 0.0));
                    worldVariables._syncDirty = false;
                }
                MapVariables mapVariables = MapVariables.get(level);
                if (mapVariables._syncDirty) {
                    PacketDistributor.sendToAllPlayers(new SavedDataSyncMessage(0, mapVariables.luck));
                    mapVariables._syncDirty = false;
                }
            }
        }
    }

    public static class WorldVariables extends SavedData {
        public static final String DATA_NAME = "deep_lucky_block_worldvars";
        boolean _syncDirty = false;

        // PORT 1.21.1 : SavedData.load/save prennent (tag, HolderLookup.Provider).
        public static WorldVariables load(CompoundTag tag, HolderLookup.Provider registries) {
            WorldVariables data = new WorldVariables();
            data.read(tag);
            return data;
        }

        public void read(CompoundTag nbt) {
        }

        @Override
        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
            return nbt;
        }

        // PORT 1.21.1 : DimensionDataStorage.computeIfAbsent(SavedData.Factory, name).
        public static net.minecraft.world.level.saveddata.SavedData.Factory<WorldVariables> factory() {
            return new net.minecraft.world.level.saveddata.SavedData.Factory<>(WorldVariables::new, WorldVariables::load);
        }

        public void markSyncDirty() {
            this.setDirty();
            this._syncDirty = true;
        }

        static WorldVariables clientSide = new WorldVariables();

        public static WorldVariables get(LevelAccessor world) {
            if (world instanceof ServerLevel level) {
                return level.getDataStorage().computeIfAbsent(WorldVariables.factory(), DATA_NAME);
            } else {
                return clientSide;
            }
        }
    }

    public static class MapVariables extends SavedData {
        public static final String DATA_NAME = "deep_lucky_block_mapvars";
        boolean _syncDirty = false;
        public double luck = 1.0;

        // PORT 1.21.1 : SavedData.load/save prennent (tag, HolderLookup.Provider).
        public static MapVariables load(CompoundTag tag, HolderLookup.Provider registries) {
            MapVariables data = new MapVariables();
            data.read(tag);
            return data;
        }

        public void read(CompoundTag nbt) {
            luck = nbt.getDouble("luck");
        }

        @Override
        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
            nbt.putDouble("luck", luck);
            return nbt;
        }

        // PORT 1.21.1 : DimensionDataStorage.computeIfAbsent(SavedData.Factory, name).
        public static net.minecraft.world.level.saveddata.SavedData.Factory<MapVariables> factory() {
            return new net.minecraft.world.level.saveddata.SavedData.Factory<>(MapVariables::new, MapVariables::load);
        }

        public void markSyncDirty() {
            this.setDirty();
            _syncDirty = true;
        }

        static MapVariables clientSide = new MapVariables();

        public static MapVariables get(LevelAccessor world) {
            if (world instanceof ServerLevelAccessor serverLevelAcc) {
                return serverLevelAcc.getLevel().getServer().getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(MapVariables.factory(), DATA_NAME);
            } else {
                return clientSide;
            }
        }
    }

    /**
     * Payload réseau (remplace le message SimpleChannel).
     * PORT 1.21.1 : plus de codec NBT réseau pratique -> on sync le champ
     * utile directement (MapVariables.luck). Wire format : int dataType, double value.
     */
    public record SavedDataSyncMessage(int dataType, double value) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<SavedDataSyncMessage> TYPE =
                new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(DeepPayloads.SAVED_DATA_SYNC);

        // PORT 1.21.1 : composite par sous-codecs (INT + DOUBLE).
        public static final StreamCodec<RegistryFriendlyByteBuf, SavedDataSyncMessage> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.INT, SavedDataSyncMessage::dataType,
                net.minecraft.network.codec.ByteBufCodecs.DOUBLE, SavedDataSyncMessage::value,
                SavedDataSyncMessage::new);

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }

        /** Côté CLIENT (registerPlayToClient) : applique les données reçues. */
        public static void handle(SavedDataSyncMessage message, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (message.dataType() == 0) {
                    MapVariables.clientSide.luck = message.value();
                }
            });
        }
    }
}
