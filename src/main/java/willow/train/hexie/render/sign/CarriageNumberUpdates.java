package willow.train.hexie.render.sign;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sync.SyncBridge.CarriageNumberPacketView;
import willow.train.hexie.core.api.display.PassengerCarriageNumber;
import willow.train.hexie.core.api.display.PassengerDisplayHost;

/** Update both the current display BE and the data used by later render-world rebuilds. */
public final class CarriageNumberUpdates {
    private CarriageNumberUpdates() {}

    public static void apply(Level level, CarriageNumberPacketView packet) {
        if (!level.isClientSide || !PassengerCarriageNumber.valid(packet.number())
                || !(level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity entity)
                || !entity.getUUID().equals(packet.entityUuid()) || entity.getContraption() == null) return;
        var contraption = entity.getContraption();
        var root = HexieBridges.partBlock().partStructures().resolve(contraption.getContraptionWorld(), packet.rootPos());
        if (root == null || !root.pos().equals(packet.rootPos())
                || !HexieBridges.signBlock().passengerDisplayPanel().supports(root.state().getBlock())
                || !HexieBridges.signBlock().panelDisplayMount().hasNumber(root.state().getValue(HexieBridges.signBlock().panelDisplayMount().property()))) return;
        HexieBridges.createCarriage().createCarriageNumberTarget().updateCapturedNumber(contraption, root.pos(), packet.number());
        if (contraption.getBlockEntityClientSide(root.pos()) instanceof PassengerDisplayHost host)
            host.setCarriageNumber(packet.number());
    }
}
