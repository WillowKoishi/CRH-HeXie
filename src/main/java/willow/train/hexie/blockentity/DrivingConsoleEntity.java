package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.api.cab.CabDisplaySource;
import willow.train.hexie.api.cab.CabDisplayHost;
import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.init.BlockEntitiesInit;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Live display sources, plus stationary test-fixture handles. Moving train control remains with the runtime owner. */
public final class DrivingConsoleEntity extends BlockEntity implements CabDisplayHost {
    private CabDisplaySource source = () -> CabDisplayState.speedOnly(0);
    private willow.train.hexie.vehicle.control.TestCabControls benchControls = new willow.train.hexie.vehicle.control.TestCabControls();
    private java.util.function.Supplier<willow.train.hexie.vehicle.control.TestCabControls> testSource = () -> benchControls;
    private boolean vehicleSource;
    public boolean hasVehicleSource() { return vehicleSource; }
    public void testSource(java.util.function.Supplier<willow.train.hexie.vehicle.control.TestCabControls> source) { testSource = source; vehicleSource = true; }
    public willow.train.hexie.vehicle.control.TestCabControls testControls() { return testSource.get(); }
    public void benchAction(int action) {
        benchControls.action(action);
        benchControls.controller.update(benchControls.mode(), benchControls.input(1), 0, null, 1,
                benchControls.power == 0 && benchControls.reverser == 0);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (getBlockState().getBlock() instanceof willow.train.hexie.blocks.locos.TestDrivingConsoleBlock)
            tag.put("TestControls", benchControls.write());
    }
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        benchControls = willow.train.hexie.vehicle.control.TestCabControls.read(tag.getCompound("TestControls"));
    }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public DrivingConsoleEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesInit.DRIVING_CONSOLE.get(), pos, state);
    }

    /** Replaces the previous source, including any legacy speed-only binding. */
    @Override
    public void displaySource(CabDisplaySource source) {
        this.source = Objects.requireNonNull(source);
    }

    /** Legacy speed-only adapter. Like displaySource, the last binding wins. */
    public void speedSource(DoubleSupplier source) {
        Objects.requireNonNull(source);
        displaySource(() -> CabDisplayState.speedOnly(source.getAsDouble()));
    }

    @Override
    public CabDisplayState displayState() {
        return source.displayState();
    }

    public double speedKmh() {
        return displayState().speedKmh();
    }
}
