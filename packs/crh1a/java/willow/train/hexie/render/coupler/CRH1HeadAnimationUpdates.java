package willow.train.hexie.render.coupler;

import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.sync.SyncBridge.CRH1HeadAnimationPacketView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH1HeadEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1HeadBlockView;

/** Update the captured data and the existing render BE; either can outlive a mesh rebuild. */
public final class CRH1HeadAnimationUpdates {
    private CRH1HeadAnimationUpdates() {}

    public static void apply(Level level, CRH1HeadAnimationPacketView packet) {
        if (!level.isClientSide || !Float.isFinite(packet.from()) || packet.from() < 0 || packet.from() > 1
                || !(level.getEntity(packet.entityId()) instanceof SnapshotCarriageView carriage)
                || !carriage.asEntity().getUUID().equals(packet.entityUuid()) || carriage.asEntity().getContraption() == null) return;
        var contraption = carriage.asEntity().getContraption();
        var info = contraption.getBlocks().get(packet.position());
        if (info == null || !(info.state().getBlock() instanceof CRH1HeadBlockView)) return;
        var updated = HexieBridges.carriageBlock().crh1HeadAnimation().synchronize(info, packet.from(), packet.start(), packet.open());
        carriage.setBlock(packet.position(), updated);
        if (contraption.getBlockEntityClientSide(packet.position()) instanceof CRH1HeadEntityView display) {
            display.asBlockEntity().setBlockState(updated.state());
            display.asBlockEntity().loadCustomOnly(updated.nbt(), level.registryAccess());
        }
    }
}
