package willow.train.hexie.compat.simurail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.init.block.BlockInit;

/** Keeps JVM verification of the optional physics subclass behind the mod-presence guard. */
public final class SimurailCouplerFactory {
    private SimurailCouplerFactory() {}

    public static CouplerBlockEntity create(BlockPos pos, BlockState state) {
        if (state.is(BlockInit.GENERAL_JANNEY_COUPLER.get()) || state.is(BlockInit.CRH2A_AUTO_COUPLER.get())
                || state.is(BlockInit.CRH2A_END_FACE.get()))
            return new PhysicalCouplerBlockEntity(pos, state);
        return new CouplerBlockEntity(pos, state);
    }

    public static void neighborChanged(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PhysicalCouplerBlockEntity coupler)
            coupler.refreshRedstoneControl();
    }
}
