package willow.train.hexie.client.render;

import net.minecraft.world.level.Level;
import willow.train.hexie.blockentity.CRH1HeadEntity;
import willow.train.hexie.blocks.emu.crh1a.CRH1HeadAnimation;
import willow.train.hexie.blocks.emu.crh1a.CRH1HeadBlock;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.network.s2c.CRH1HeadAnimationPacket;

/** Update the captured data and the existing render BE; either can outlive a mesh rebuild. */
public final class CRH1HeadAnimationUpdates {
    private CRH1HeadAnimationUpdates() {}

    public static void apply(Level level, CRH1HeadAnimationPacket packet) {
        if (!level.isClientSide || !Float.isFinite(packet.from()) || packet.from() < 0 || packet.from() > 1
                || !(level.getEntity(packet.entityId()) instanceof SnapshotCarriageEntity carriage)
                || !carriage.getUUID().equals(packet.entityUuid()) || carriage.getContraption() == null) return;
        var contraption = carriage.getContraption();
        var info = contraption.getBlocks().get(packet.position());
        if (info == null || !(info.state().getBlock() instanceof CRH1HeadBlock)) return;
        var updated = CRH1HeadAnimation.synchronize(info, packet.from(), packet.start(), packet.open());
        carriage.setBlock(packet.position(), updated);
        if (contraption.getBlockEntityClientSide(packet.position()) instanceof CRH1HeadEntity display) {
            display.setBlockState(updated.state());
            display.loadCustomOnly(updated.nbt(), level.registryAccess());
        }
    }
}
