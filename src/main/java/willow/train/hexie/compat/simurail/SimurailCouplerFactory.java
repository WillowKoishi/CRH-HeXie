package willow.train.hexie.compat.simurail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;

/** Keeps JVM verification of the optional physics subclass behind the mod-presence guard. */
public final class SimurailCouplerFactory {
    private SimurailCouplerFactory() {}

    public static CouplerBlockEntityView create(BlockPos pos, BlockState state) {
        if (state.is(HexieBridges.worldRegistry().blockInit().generalJanneyCoupler()) || state.is(HexieBridges.worldRegistry().blockInit().crh2aAutoCoupler())
                || state.is(HexieBridges.worldRegistry().blockInit().crh2aEndFace()) || state.is(HexieBridges.worldRegistry().blockInit().crh1aEndFace()))
            return new PhysicalCouplerBlockEntity(pos, state);
        return (CouplerBlockEntityView) HexieBridges.worldBlock().createCouplerBlockEntity(pos, state);
    }

    public static void neighborChanged(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PhysicalCouplerBlockEntity coupler)
            coupler.refreshRedstoneControl();
    }
}
