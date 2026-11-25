package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.BlockPos;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;

/** The integrated CRH1A hook is released as soon as its cover begins closing. */
public final class CRH1HeadCoupling {
    private CRH1HeadCoupling() {}

    public static void release(SnapshotCarriageView carriage, BlockPos position) {
        var motion = carriage.motion();
        if (motion == null) return; // The stored closed state is reconciled when motion returns.
        var runtime = motion.runtime();
        var endpoint = HexieBridges.coupling().vehicleCouplingCatalog().endpointId(carriage.asEntity().getUUID(), position);
        if (!runtime.placement().endpoints().containsKey(endpoint)) return;
        runtime.uncouple(endpoint);
        runtime.cancelCoupling(endpoint);
    }
}
