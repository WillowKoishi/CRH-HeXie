package willow.train.hexie.client.sign;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.level.Level;
import willow.train.hexie.blocks.locos.ss7d.SS7DHeadEntity;
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
            if (packet.contentType() == CreatePanelEditTarget.ContentType.SS7D_NUMBER
                    && entity instanceof SS7DHeadEntity head) {
                head.updateDigits(packet.content().getString(SS7DHeadEntity.NUMBER_TAG));
                head.updateDepot(packet.content().getString(SS7DHeadEntity.BUREAU_TAG),
                        packet.content().getString(SS7DHeadEntity.DEPOT_TAG),
                        packet.content().getBoolean(SS7DHeadEntity.DEPOT_VISIBLE_TAG));
                head.updateSecondEnd(packet.content().getBoolean(SS7DHeadEntity.SECOND_END_TAG));
                head.setChanged();
            } else {
                entity.loadWithComponents(contraption.getBlocks().get(packet.rootPos()).nbt(), level.registryAccess());
                entity.setChanged();
            }
        }
    }
}
