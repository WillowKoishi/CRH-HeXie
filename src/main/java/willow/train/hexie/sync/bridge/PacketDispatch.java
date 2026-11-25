package willow.train.hexie.sync.bridge;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreatePanelBridge;
import willow.train.hexie.bridge.sync.SyncBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.core.api.display.PassengerDisplayState;
import willow.train.hexie.core.api.interaction.ControlInput;
import willow.train.hexie.sync.c2s.BlockControlPacket;
import willow.train.hexie.sync.c2s.CabControlPacket;
import willow.train.hexie.sync.c2s.CabKeyboardPacket;
import willow.train.hexie.sync.c2s.CarriagePlayerMovePacket;
import willow.train.hexie.sync.c2s.CarriageRestoreAckPacket;
import willow.train.hexie.sync.c2s.ControlInputPacket;
import willow.train.hexie.sync.c2s.DrivingConsoleExitPacket;
import willow.train.hexie.sync.c2s.ExtendedBlockInteractPacket;
import willow.train.hexie.sync.c2s.FloorHeightPacket;
import willow.train.hexie.sync.c2s.ReleaseNativeControlsPacket;
import willow.train.hexie.sync.c2s.signs.CarriageMarkingUpdatePacket;
import willow.train.hexie.sync.c2s.signs.CarriageNoSignUpdatePacket;
import willow.train.hexie.sync.c2s.signs.CarriageNumberUpdatePacket;
import willow.train.hexie.sync.c2s.signs.CarriageTypeSignUpdatePacket;
import willow.train.hexie.sync.c2s.signs.GroundStationSignUpdatePacket;
import willow.train.hexie.sync.c2s.signs.HeadNumberUpdatePacket;
import willow.train.hexie.sync.c2s.signs.LacqueredBoardPacket;
import willow.train.hexie.sync.c2s.signs.PanelTextUpdatePacket;
import willow.train.hexie.sync.c2s.signs.SS7DNumberUpdatePacket;
import willow.train.hexie.sync.c2s.signs.TrainSpeedSignUpdatePacket;
import willow.train.hexie.sync.cache.ServerVehicleSync;
import willow.train.hexie.sync.channel.HexieNetworkHandler;
import willow.train.hexie.sync.channel.ServerHeldItemSync;
import willow.train.hexie.sync.s2c.CRH1HeadAnimationPacket;
import willow.train.hexie.sync.s2c.CarriageNumberPacket;
import willow.train.hexie.sync.s2c.CarriagePlayerStatePacket;
import willow.train.hexie.sync.s2c.ContraptionSeatPositionPacket;
import willow.train.hexie.sync.s2c.ControlCapturePacket;
import willow.train.hexie.sync.s2c.CouplerDefinitionsPacket;
import willow.train.hexie.sync.s2c.NativeControlsStartedPacket;
import willow.train.hexie.sync.s2c.PanelEditPacket;
import willow.train.hexie.sync.s2c.PassengerDisplayPacket;

final class PacketDispatch implements SyncBridge {
    private final ServerVehicleSync server;

    PacketDispatch(ServerVehicleSync server) { this.server = server; }

    @Override public ServerVehicleSync serverSync() { return server; }

    @Override
    public void sendExtendedBlockInteract(BlockPos pos, Direction face, Vec3 location, InteractionHand hand, boolean inside) {
        PacketDistributor.sendToServer(new ExtendedBlockInteractPacket(pos, face, location, hand, inside));
    }

    @Override
    public void sendCarriageNoSignUpdate(int menuId, BlockPos pos, Component message, int color, boolean isLeftSide) {
        HexieNetworkHandler.sendToServer(
                new CarriageNoSignUpdatePacket(pos, message, color, isLeftSide).withMenuId(menuId));
    }

    @Override
    public void sendCarriageTypeSignUpdate(int menuId, BlockPos pPos, String pLine0, String pLine1, String pLine2,
            String pLine3, String pLine4, int pColor) {
        HexieNetworkHandler.sendToServer(
                new CarriageTypeSignUpdatePacket(pPos, pLine0, pLine1, pLine2, pLine3, pLine4, pColor).withMenuId(menuId));
    }

    @Override
    public void sendLacqueredBoard(int menuId, BlockPos pPos, Component[] contents, double x_offset, int... colors) {
        HexieNetworkHandler.sendToServer(
                new LacqueredBoardPacket(pPos, contents, x_offset, colors).withMenuId(menuId));
    }

    @Override
    public void sendTrainSpeedSignUpdate(int menuId, BlockPos pos, Component content, int color, float x_offset,
            float y_offset, boolean x_revert, boolean y_revert) {
        HexieNetworkHandler.sendToServer(
                new TrainSpeedSignUpdatePacket(pos, content, color, x_offset, y_offset, x_revert, y_revert).withMenuId(menuId));
    }

    @Override
    public void sendCRH1HeadAnimationToTracking(Entity trackedEntity, int entityId, UUID entityUuid, BlockPos position,
            float from, long start, boolean open) {
        PacketDistributor.sendToPlayersTrackingEntity(trackedEntity,
                new CRH1HeadAnimationPacket(entityId, entityUuid, position, from, start, open));
    }

    @Override
    public void sendCRH1HeadAnimationToTracking(Entity carriage, StructureTemplate.StructureBlockInfo info) {
        PacketDistributor.sendToPlayersTrackingEntity(carriage, CRH1HeadAnimationPacket.create(carriage, info));
    }

    @Override
    public void sendTrackBallast(BlockPos origin, BlockPos target, int segment) {
        HexieBridges.createTrack().sendTrackBallast(origin, target, segment);
    }

    @Override
    public void sendFloorHeight(InteractionHand hand, int hotbarSlot, int height) {
        PacketDistributor.sendToServer(new FloorHeightPacket(hand, hotbarSlot, height));
    }

    @Override
    public void sendFloorHeight(InteractionHand hand, int hotbarSlot, int height, FloorHeightPacketAction action) {
        PacketDistributor.sendToServer(new FloorHeightPacket(hand, hotbarSlot, height, action));
    }

    @Override
    public void sendControlInput(ControlInputPacketTarget target, ControlInputPacketPhase phase, int vehicleId,
            BlockPos position, int entryKey, Vec3 point, InteractionHand hand, ControlInput input) {
        HexieNetworkHandler.sendToServer(
                new ControlInputPacket(target, phase, vehicleId, position, entryKey, point, hand, input));
    }

    @Override
    public void sendControlInput(ControlInputPacketTarget target, ControlInputPacketPhase phase, int vehicleId,
            BlockPos position, int entryKey, Vec3 point, InteractionHand hand, ControlInput input,
            long requestId) {
        HexieNetworkHandler.sendToServer(
                new ControlInputPacket(target, phase, vehicleId, position, entryKey, point, hand, input, requestId));
    }

    @Override
    public void sendSS7DNumberUpdate(int menuId, String digits, String bureau, String depot, boolean depotVisible,
            boolean secondEnd) {
        HexieNetworkHandler.sendToServer(
                new SS7DNumberUpdatePacket(menuId, digits, bureau, depot, depotVisible, secondEnd));
    }

    @Override
    public void sendCarriageMarkingUpdate(BlockPos pos, String number, String chinese, String english, boolean left) {
        HexieNetworkHandler.sendToServer(new CarriageMarkingUpdatePacket(pos, number, chinese, english, left));
    }

    @Override
    public void sendCarriageMarkingUpdate(BlockPos pos, String number, String chinese, String english, boolean left,
            int menuId) {
        HexieNetworkHandler.sendToServer(
                new CarriageMarkingUpdatePacket(pos, number, chinese, english, left, menuId));
    }

    @Override
    public void sendCarriageNumberUpdate(int menuId, String number) {
        HexieNetworkHandler.sendToServer(new CarriageNumberUpdatePacket(menuId, number));
    }

    @Override
    public void sendGroundStationSignUpdate(BlockPos pos, String[] fields) {
        HexieNetworkHandler.sendToServer(new GroundStationSignUpdatePacket(pos, fields));
    }

    @Override
    public void sendHeadNumberUpdate(BlockPos pos, String number) {
        HexieNetworkHandler.sendToServer(new HeadNumberUpdatePacket(pos, number));
    }

    @Override
    public void sendHeadNumberUpdate(BlockPos pos, String number, int menuId) {
        HexieNetworkHandler.sendToServer(new HeadNumberUpdatePacket(pos, number, menuId));
    }

    @Override
    public void sendPanelTextUpdate(int menuId, PartBlockBridge.PanelTextView value) {
        HexieNetworkHandler.sendToServer(new PanelTextUpdatePacket(menuId, value));
    }

    @Override
    public void sendCarriageNumberToTracking(Entity trackedEntity, int entityId, UUID entityUuid, BlockPos rootPos,
            String number) {
        PacketDistributor.sendToPlayersTrackingEntity(trackedEntity,
                new CarriageNumberPacket(entityId, entityUuid, rootPos, number));
    }

    @Override
    public void sendPanelEditToTracking(Entity trackedEntity, int entityId, UUID entityUuid, BlockPos rootPos,
            CreatePanelBridge.CreatePanelEditTargetContentType contentType, int slot, CompoundTag content) {
        PacketDistributor.sendToPlayersTrackingEntity(trackedEntity,
                new PanelEditPacket(entityId, entityUuid, rootPos, contentType, slot, content));
    }

    @Override
    public void sendReleaseNativeControls(int carriageId, BlockPos controls) {
        HexieNetworkHandler.sendToServer(new ReleaseNativeControlsPacket(carriageId, controls));
    }

    @Override
    public void sendContraptionSeatPositionToPlayer(ServerPlayer recipient, UUID contraptionId, UUID passengerId,
            int seatIndex, int position) {
        PacketDistributor.sendToPlayer(recipient,
                new ContraptionSeatPositionPacket(contraptionId, passengerId, seatIndex, position));
    }

    @Override
    public void sendContraptionSeatPositionToTracking(Entity trackedEntity, UUID contraptionId, UUID passengerId,
            int seatIndex, int position) {
        PacketDistributor.sendToPlayersTrackingEntity(trackedEntity,
                new ContraptionSeatPositionPacket(contraptionId, passengerId, seatIndex, position));
    }

    @Override
    public void resetContraptionSeatPosition(ServerPlayer recipient, UUID carriage) {
        PacketDistributor.sendToPlayer(recipient, ContraptionSeatPositionPacket.reset(carriage));
    }

    @Override
    public void sendCarriagePlayerStateToPlayer(ServerPlayer recipient, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, boolean restore, long sampleTick) {
        PacketDistributor.sendToPlayer(recipient,
                new CarriagePlayerStatePacket(playerId, carriageId, carriageUuid, local, restore, sampleTick));
    }

    @Override
    public void sendCarriagePlayerStateToTracking(Entity trackedEntity, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, boolean restore, long sampleTick) {
        PacketDistributor.sendToPlayersTrackingEntity(trackedEntity,
                new CarriagePlayerStatePacket(playerId, carriageId, carriageUuid, local, restore, sampleTick));
    }

    @Override
    public void sendCarriagePlayerStateToPlayer(ServerPlayer recipient, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, boolean restore, long sampleTick, long restoreToken) {
        PacketDistributor.sendToPlayer(recipient,
                new CarriagePlayerStatePacket(playerId, carriageId, carriageUuid, local, restore, sampleTick, restoreToken));
    }

    @Override
    public void sendPassengerDisplayToTracking(Entity trackedEntity, UUID carriage, PassengerDisplayState state) {
        PacketDistributor.sendToPlayersTrackingEntity(trackedEntity, new PassengerDisplayPacket(carriage, state));
    }

    @Override
    public void sendPassengerDisplayToPlayer(ServerPlayer recipient, UUID carriage, PassengerDisplayState state) {
        PacketDistributor.sendToPlayer(recipient, new PassengerDisplayPacket(carriage, state));
    }

    @Override
    public void sendNativeControlsStartedToPlayer(ServerPlayer recipient, int carriageId, BlockPos controls) {
        PacketDistributor.sendToPlayer(recipient, new NativeControlsStartedPacket(carriageId, controls));
    }


    @Override
    public void sendCarriageRestoreAck(long token) {
        PacketDistributor.sendToServer(new CarriageRestoreAckPacket(token));
    }

    @Override
    public void sendCarriagePlayerMove(int carriageId, UUID carriageUuid, Vec3 position, boolean attached, float yaw,
            float pitch, boolean onGround) {
        PacketDistributor.sendToServer(
                new CarriagePlayerMovePacket(carriageId, carriageUuid, position, attached, yaw, pitch, onGround));
    }

    @Override
    public void sendCouplerDefinitionsToPlayer(ServerPlayer recipient, String json) {
        PacketDistributor.sendToPlayer(recipient, new CouplerDefinitionsPacket(json));
    }

    @Override
    public void sendCouplerDefinitionsToAll(String json) {
        PacketDistributor.sendToAllPlayers(new CouplerDefinitionsPacket(json));
    }

    @Override
    public void sendBlockControl(BlockPos position, String controlId, int value, int phase, InteractionHand hand,
            Vec3 point) {
        HexieNetworkHandler.sendToServer(new BlockControlPacket(position, controlId, value, phase, hand, point));
    }

    @Override
    public void sendCabControl(int vehicleId, BlockPos console, String controlId, int value) {
        HexieNetworkHandler.sendToServer(new CabControlPacket(vehicleId, console, controlId, value));
    }

    @Override
    public void sendCabKeyboard(int vehicleId, boolean toggleSimple, int mask) {
        HexieNetworkHandler.sendToServer(new CabKeyboardPacket(vehicleId, toggleSimple, mask));
    }

    @Override
    public void sendDrivingConsoleExit(int vehicleId) {
        HexieNetworkHandler.sendToServer(new DrivingConsoleExitPacket(vehicleId));
    }

    @Override
    public void sendControlCaptureToPlayer(ServerPlayer recipient, ControlInputPacketTarget target, int vehicleId,
            BlockPos position, long requestId, boolean accepted) {
        PacketDistributor.sendToPlayer(recipient,
                new ControlCapturePacket(target, vehicleId, position, requestId, accepted));
    }

    @Override
    public ServerHeldItemSyncOperations serverHeldItemSync() {
        return ServerHeldItemSync::afterInteraction;
    }

    @Override
    public void sendStickerUpsert(willow.train.hexie.core.sticker.StickerEntry entry) {
        PacketDistributor.sendToServer(new willow.train.hexie.sync.sticker.StickerUpsertPacket(entry));
    }

    @Override
    public void sendStickerRemove(BlockPos pos, net.minecraft.core.Direction facing) {
        PacketDistributor.sendToServer(new willow.train.hexie.sync.sticker.StickerRemovePacket(pos, facing));
    }

    @Override
    public void sendStickerImageUpload(byte[] pngBytes) {
        PacketDistributor.sendToServer(new willow.train.hexie.sync.sticker.StickerImageUploadPacket(pngBytes));
    }

    @Override
    public void sendStickerImageRequest(String hash) {
        PacketDistributor.sendToServer(new willow.train.hexie.sync.sticker.StickerImageRequestPacket(hash));
    }

    @Override
    public void sendStickerChunkToTracking(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.ChunkPos chunkPos,
            java.util.List<willow.train.hexie.core.sticker.StickerEntry> entries) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos,
                new willow.train.hexie.sync.sticker.StickerChunkSyncPacket(chunkPos.x, chunkPos.z, entries));
    }
}
