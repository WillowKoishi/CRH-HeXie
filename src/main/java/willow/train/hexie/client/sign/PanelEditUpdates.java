package willow.train.hexie.client.sign;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.level.Level;
import willow.train.hexie.compat.create.CreatePanelEditTarget;
import willow.train.hexie.network.s2c.PanelEditPacket;

/** Refresh the live rendered BE and all data used when its contraption render world is rebuilt. */
public final class PanelEditUpdates {
    private PanelEditUpdates() {}
    public static void apply(Level level, PanelEditPacket packet) {
        if (!level.isClientSide || !(level.getEntity(packet.entityId()) instanceof AbstractContraptionEntity carrier)
                || !carrier.getUUID().equals(packet.entityUuid()) || carrier.getContraption() == null) return;
        var contraption = carrier.getContraption();
        if (!CreatePanelEditTarget.updateCapturedContents(contraption, packet.rootPos(), packet.contentType(), packet.slot(),
                packet.content(), level.registryAccess())) return;
        var entity = contraption.getBlockEntityClientSide(packet.rootPos());
        if (entity != null) {
            entity.loadWithComponents(contraption.getBlocks().get(packet.rootPos()).nbt(), level.registryAccess());
            entity.setChanged();
        }
    }
}
