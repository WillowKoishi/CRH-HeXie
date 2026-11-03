package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.coupler.CouplerAdapterData;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;

/** Stores mounted converters from the original hook to the exposed outer layer. */
public class CouplerBlockEntity extends BlockEntity implements willow.train.hexie.blocks.base.BlockEntityAnimation {
    private CouplerAdapter adapter;
    private float previousCoverProgress, coverProgress;

    public CouplerBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntitiesInit.COUPLER.get(), pos, state);
    }

    /** Other registrations may reuse the storage with their own block-entity type. */
    public CouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        coverProgress = previousCoverProgress = coverOpen(state) ? 1 : 0;
    }

    private static boolean coverOpen(BlockState state) {
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
    }

    /** Shared world/vehicle display animation; the persisted OPEN block state is authoritative. */
    public void tickCoverAnimation() {
        previousCoverProgress = coverProgress;
        coverProgress = net.minecraft.util.Mth.approach(coverProgress, coverOpen(getBlockState()) ? 1 : 0, .1f);
    }

    public float coverProgress(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, previousCoverProgress, coverProgress);
    }

    @Override public void restoreAnimation(BlockEntity previous) {
        if (previous instanceof CouplerBlockEntity source) {
            previousCoverProgress = source.previousCoverProgress;
            coverProgress = source.coverProgress;
        }
    }

    public CouplerAdapter adapter() { return adapter; }

    public boolean install(CouplerAdapter value) {
        if (value == null || !(getBlockState().getBlock() instanceof CouplerBlock coupler)
                || value.otherModel(adapter == null ? coupler.model() : adapter.otherModel(coupler.model())) == null) return false;
        adapter = CouplerAdapter.append(coupler.model(), adapter, value);
        sync();
        return true;
    }

    public CouplerAdapter removeOuter() {
        if (adapter == null || !(getBlockState().getBlock() instanceof CouplerBlock hook)) return null;
        var removed = adapter.outer();
        adapter = adapter.withoutOuter(hook.model());
        sync();
        return removed;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CouplerAdapterData.write(tag, adapter);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CouplerAdapter loaded = CouplerAdapterData.read(tag);
        adapter = loaded != null && getBlockState().getBlock() instanceof CouplerBlock coupler
                && loaded.otherModel(coupler.model()) != null ? loaded : null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
