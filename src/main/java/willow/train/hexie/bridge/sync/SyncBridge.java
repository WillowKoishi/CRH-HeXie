package willow.train.hexie.bridge.sync;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.CreatePanelBridge;
import willow.train.hexie.bridge.world.PartBlockBridge;
import willow.train.hexie.core.api.display.PassengerDisplayState;
import willow.train.hexie.core.api.interaction.ControlInput;

/** Dispatches the existing payloads. Each S2C method names its audience. */
public interface SyncBridge {
    default VehicleCacheView vehicleCache() { return ClientSyncBridge.current().vehicleCache(); }

    default ServerSyncPort serverSync() {
        throw new UnsupportedOperationException("Vehicle sync is unavailable on this dispatch");
    }

    void sendCarriageNoSignUpdate(int menuId, BlockPos pos, Component message, int color, boolean isLeftSide);

    default void sendCarriageNoSignUpdate(BlockPos pos, Component message, int color, boolean isLeftSide) {
        sendCarriageNoSignUpdate(-1, pos, message, color, isLeftSide);
    }

    void sendCarriageTypeSignUpdate(int menuId, BlockPos pPos, String pLine0, String pLine1, String pLine2,
            String pLine3, String pLine4, int pColor);

    default void sendCarriageTypeSignUpdate(BlockPos pPos, String pLine0, String pLine1, String pLine2, String pLine3, String pLine4, int pColor) {
        sendCarriageTypeSignUpdate(-1, pPos, pLine0, pLine1, pLine2, pLine3, pLine4, pColor);
    }

    void sendLacqueredBoard(int menuId, BlockPos pPos, Component[] contents, double x_offset, int... colors);

    default void sendLacqueredBoard(BlockPos pPos, Component[] contents, double x_offset, int... colors) {
        sendLacqueredBoard(-1, pPos, contents, x_offset, colors);
    }

    void sendTrainSpeedSignUpdate(int menuId, BlockPos pos, Component content, int color, float x_offset,
            float y_offset, boolean x_revert, boolean y_revert);

    default void sendTrainSpeedSignUpdate(BlockPos pos, Component content, int color, float x_offset, float y_offset, boolean x_revert, boolean y_revert) {
        sendTrainSpeedSignUpdate(-1, pos, content, color, x_offset, y_offset, x_revert, y_revert);
    }

    void sendCRH1HeadAnimationToTracking(Entity trackedEntity, int entityId, UUID entityUuid, BlockPos position,
            float from, long start, boolean open);

    void sendCRH1HeadAnimationToTracking(Entity carriage, StructureTemplate.StructureBlockInfo info);

    void sendTrackBallast(BlockPos origin, BlockPos target, int segment);

    void sendFloorHeight(InteractionHand hand, int hotbarSlot, int height);

    void sendFloorHeight(InteractionHand hand, int hotbarSlot, int height, FloorHeightPacketAction action);

    void sendControlInput(ControlInputPacketTarget target, ControlInputPacketPhase phase, int vehicleId,
            BlockPos position, int entryKey, Vec3 point, InteractionHand hand, ControlInput input);

    void sendControlInput(ControlInputPacketTarget target, ControlInputPacketPhase phase, int vehicleId,
            BlockPos position, int entryKey, Vec3 point, InteractionHand hand, ControlInput input,
            long requestId);

    void sendSS7DNumberUpdate(int menuId, String digits, String bureau, String depot, boolean depotVisible,
            boolean secondEnd);

    void sendCarriageMarkingUpdate(BlockPos pos, String number, String chinese, String english, boolean left);

    void sendCarriageMarkingUpdate(BlockPos pos, String number, String chinese, String english, boolean left,
            int menuId);

    void sendCarriageNumberUpdate(int menuId, String number);

    void sendGroundStationSignUpdate(BlockPos pos, String[] fields);

    void sendHeadNumberUpdate(BlockPos pos, String number);

    void sendHeadNumberUpdate(BlockPos pos, String number, int menuId);

    void sendPanelTextUpdate(int menuId, PartBlockBridge.PanelTextView value);

    void sendCarriageNumberToTracking(Entity trackedEntity, int entityId, UUID entityUuid, BlockPos rootPos,
            String number);

    void sendPanelEditToTracking(Entity trackedEntity, int entityId, UUID entityUuid, BlockPos rootPos,
            CreatePanelBridge.CreatePanelEditTargetContentType contentType, int slot, CompoundTag content);

    void sendReleaseNativeControls(int carriageId, BlockPos controls);

    void sendContraptionSeatPositionToPlayer(ServerPlayer recipient, UUID contraptionId, UUID passengerId,
            int seatIndex, int position);

    void sendContraptionSeatPositionToTracking(Entity trackedEntity, UUID contraptionId, UUID passengerId,
            int seatIndex, int position);

    void resetContraptionSeatPosition(ServerPlayer recipient, UUID carriage);

    void sendCarriagePlayerStateToPlayer(ServerPlayer recipient, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, boolean restore, long sampleTick);

    void sendCarriagePlayerStateToTracking(Entity trackedEntity, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, boolean restore, long sampleTick);

    void sendCarriagePlayerStateToPlayer(ServerPlayer recipient, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, boolean restore, long sampleTick, long restoreToken);

    void sendPassengerDisplayToTracking(Entity trackedEntity, UUID carriage, PassengerDisplayState state);

    void sendPassengerDisplayToPlayer(ServerPlayer recipient, UUID carriage, PassengerDisplayState state);

    void sendNativeControlsStartedToPlayer(ServerPlayer recipient, int carriageId, BlockPos controls);

    void sendStickerUpsert(willow.train.hexie.core.sticker.StickerEntry entry);

    void sendStickerRemove(BlockPos pos, net.minecraft.core.Direction facing);

    void sendStickerImageUpload(byte[] pngBytes);

    void sendStickerImageRequest(String hash);

    void sendStickerChunkToTracking(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.ChunkPos chunkPos,
            java.util.List<willow.train.hexie.core.sticker.StickerEntry> entries);


    void sendExtendedBlockInteract(BlockPos pos, Direction face, Vec3 location, InteractionHand hand, boolean inside);

    void sendCarriageRestoreAck(long token);

    void sendCarriagePlayerMove(int carriageId, UUID carriageUuid, Vec3 position, boolean attached, float yaw,
            float pitch, boolean onGround);

    void sendCouplerDefinitionsToPlayer(ServerPlayer recipient, String json);

    void sendCouplerDefinitionsToAll(String json);

    void sendBlockControl(BlockPos position, String controlId, int value, int phase, InteractionHand hand,
            Vec3 point);

    void sendCabControl(int vehicleId, BlockPos console, String controlId, int value);

    void sendCabKeyboard(int vehicleId, boolean toggleSimple, int mask);

    void sendDrivingConsoleExit(int vehicleId);

    void sendControlCaptureToPlayer(ServerPlayer recipient, ControlInputPacketTarget target, int vehicleId,
            BlockPos position, long requestId, boolean accepted);

    ServerHeldItemSyncOperations serverHeldItemSync();

    interface ServerHeldItemSyncOperations {
        void afterInteraction(Player player, InteractionHand hand);
    }

    enum ControlInputPacketPhase {
        OPEN, OPERATE, BEGIN, UPDATE, END, CLOSE, EXIT
    }

    enum ControlInputPacketTarget {
        SEATED, WORLD
    }

    enum FloorHeightPacketAction {
        OPEN, CONFIRM, CANCEL, CONFIRM_DOOR_DIRECTION
    }

    interface CRH1HeadAnimationPacketView {
        int entityId();
        UUID entityUuid();
        BlockPos position();
        float from();
        long start();
        boolean open();
    }

    interface CarriageNumberPacketView {
        int entityId();
        UUID entityUuid();
        BlockPos rootPos();
        String number();
    }

    interface CarriagePlayerStatePacketView {
        int carriageId();
        UUID carriageUuid();
        Vec3 local();
        long sampleTick();
        int playerId();
        boolean restore();
        long restoreToken();
    }

    interface PanelEditPacketView {
        int entityId();
        UUID entityUuid();
        BlockPos rootPos();
        CreatePanelBridge.CreatePanelEditTargetContentType contentType();
        int slot();
        CompoundTag content();
    }
}
