package willow.train.hexie.render.sign;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreatePanelBridge.CreatePanelEditTargetContentType;
import willow.train.hexie.bridge.sync.SyncBridge.PanelEditPacketView;
import willow.train.hexie.bridge.world.BlockEntityViews.SS7DHeadEntityView;
import willow.train.hexie.bridge.world.WorldBlockBridge;

/** Refresh the live rendered BE and all data used when its contraption render world is rebuilt. */
public final class PanelEditUpdates {
    private PanelEditUpdates() {}
    public static void apply(Level level, PanelEditPacketView packet) {
        if (!level.isClientSide || !(level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity carrier)
                || !carrier.getUUID().equals(packet.entityUuid()) || carrier.getContraption() == null) return;
        var contraption = carrier.getContraption();
        if (!HexieBridges.createPanel().createPanelEditTarget().updateCapturedContents(contraption, packet.rootPos(), packet.contentType(), packet.slot(),
                packet.content(), level.registryAccess())) return;
        var entity = contraption.getBlockEntityClientSide(packet.rootPos());
        if (entity != null) {
            if (packet.contentType() == CreatePanelEditTargetContentType.SS7D_NUMBER
                    && entity instanceof SS7DHeadEntityView head) {
                head.updateDigits(packet.content().getString(WorldBlockBridge.SS7DHeadEntityOperations.NUMBER_TAG));
                head.updateDepot(packet.content().getString(WorldBlockBridge.SS7DHeadEntityOperations.BUREAU_TAG),
                        packet.content().getString(WorldBlockBridge.SS7DHeadEntityOperations.DEPOT_TAG),
                        packet.content().getBoolean(WorldBlockBridge.SS7DHeadEntityOperations.DEPOT_VISIBLE_TAG));
                head.updateSecondEnd(packet.content().getBoolean(WorldBlockBridge.SS7DHeadEntityOperations.SECOND_END_TAG));
                head.asBlockEntity().setChanged();
            } else {
                entity.loadWithComponents(contraption.getBlocks().get(packet.rootPos()).nbt(), level.registryAccess());
                entity.setChanged();
            }
        }
    }
}
