package willow.train.hexie.client.sign;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.level.Level;
import willow.train.hexie.api.display.PassengerCarriageNumber;
import willow.train.hexie.api.display.PassengerDisplayHost;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.signs.PanelDisplayMount;
import willow.train.hexie.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.compat.create.CreateCarriageNumberTarget;
import willow.train.hexie.network.s2c.CarriageNumberPacket;

/** Update both the current display BE and the data used by later render-world rebuilds. */
public final class CarriageNumberUpdates {
    private CarriageNumberUpdates() {}

    public static void apply(Level level, CarriageNumberPacket packet) {
        if (!level.isClientSide || !PassengerCarriageNumber.valid(packet.number())
                || !(level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity entity)
                || !entity.getUUID().equals(packet.entityUuid()) || entity.getContraption() == null) return;
        var contraption = entity.getContraption();
        var root = PartStructures.resolve(contraption.getContraptionWorld(), packet.rootPos());
        if (root == null || !root.pos().equals(packet.rootPos())
                || !PassengerDisplayPanel.supports(root.state().getBlock())
                || !root.state().getValue(PanelDisplayMount.PROPERTY).hasNumber()) return;
        CreateCarriageNumberTarget.updateCapturedNumber(contraption, root.pos(), packet.number());
        if (contraption.getBlockEntityClientSide(root.pos()) instanceof PassengerDisplayHost host)
            host.setCarriageNumber(packet.number());
    }
}
