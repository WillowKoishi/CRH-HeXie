package willow.train.hexie.blocks.emu.crh1a;

import net.minecraft.core.BlockPos;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.vehicle.assembly.VehicleCouplingCatalog;

/** The integrated CRH1A hook is released as soon as its cover begins closing. */
public final class CRH1HeadCoupling {
    private CRH1HeadCoupling() {}

    public static void release(SnapshotCarriageEntity carriage, BlockPos position) {
        var motion = carriage.motion();
        if (motion == null) return; // The stored closed state is reconciled when motion returns.
        var runtime = motion.runtime();
        var endpoint = VehicleCouplingCatalog.endpointId(carriage.getUUID(), position);
        if (!runtime.placement().endpoints().containsKey(endpoint)) return;
        runtime.uncouple(endpoint);
        runtime.cancelCoupling(endpoint);
    }
}
