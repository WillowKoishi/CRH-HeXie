package willow.train.hexie.bridge.world.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerBlockView;
import willow.train.hexie.bridge.world.CouplerBlockBridge.JanneyCouplerBlockView;

/** Stores mounted converters from the original hook to the exposed outer layer. */
public abstract class CouplerBlockEntityBase extends BlockEntity implements BlockEntityViews.CouplerBlockEntityView {
    private CouplingBridge.CouplerAdapterView adapter;
    private float previousCoverProgress, coverProgress;
    private CompoundTag rescueKnuckle = new CompoundTag();
    private float previousRescueProgress = 1, rescueProgress = 1;
    private boolean rescueAnimationInitialized, rescueTargetOpen = true;
    private CouplingBridge.CouplerAdapterView rescueAnimationAdapter;
    private String rescueAnimationBaseModel = "";

    /** Other registrations may reuse the storage with their own block-entity type. */
    protected CouplerBlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        coverProgress = previousCoverProgress = coverOpen(state) ? 1 : 0;
    }

    private static boolean coverOpen(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN);
    }

    /** Shared world/vehicle display animation; the persisted OPEN block state is authoritative. */
    public void tickCoverAnimation() {
        updateMountedKnuckle();
        previousCoverProgress = coverProgress;
        coverProgress = Mth.approach(coverProgress, coverOpen(getBlockState()) ? 1 : 0, .1f);
        previousRescueProgress = rescueProgress;
        rescueProgress = Mth.approach(rescueProgress, rescueTargetOpen ? 1 : 0, .1f);
    }

    public float coverProgress(float partialTick) {
        return Mth.lerp(partialTick, previousCoverProgress, coverProgress);
    }

    @Override public void restoreAnimation(BlockEntity previous) {
        if (previous instanceof CouplerBlockEntityBase source) {
            previousCoverProgress = source.previousCoverProgress;
            coverProgress = source.coverProgress;
            previousRescueProgress = source.previousRescueProgress;
            rescueProgress = source.rescueProgress;
            rescueAnimationInitialized = true;
        } else if (previous instanceof BlockEntityViews.CouplerBlockEntityView source) {
            previousCoverProgress = source.coverProgress(0);
            coverProgress = source.coverProgress(1);
            previousRescueProgress = source.rescueKnuckleProgress(0);
            rescueProgress = source.rescueKnuckleProgress(1);
            rescueAnimationInitialized = true;
        }
    }

    public CouplingBridge.CouplerAdapterView adapter() { return adapter; }

    /** Ordinary placed blocks and captured carriage views do not have physical connections. */
    @Override public boolean uncouplePhysical() { return false; }

    /** Physical connections drive the same persisted knuckle state as the carriage runtime. */
    public void updatePhysicalCouplingAnimation(boolean coupled) {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof CouplerBlockView hook)) return;
        if (adapter == null && getBlockState().getBlock() instanceof JanneyCouplerBlockView) {
            var open = BlockStateProperties.OPEN;
            if (getBlockState().getValue(open) == coupled)
                level.setBlock(worldPosition, getBlockState().setValue(open, !coupled), Block.UPDATE_ALL);
        }
        rescueKnuckle = HexieBridges.couplerBlock().rescueCouplerState().sync(rescueKnuckle, adapter, hook.model(), coupled, level.getGameTime());
        readRescueAnimationTarget();
        sync();
    }
    public boolean hasRescueCoupler() { return HexieBridges.couplerBlock().rescueCouplerState().installed(adapter); }
    public boolean rescueKnuckleOpen() { return rescueTargetOpen; }
    public float rescueKnuckleProgress(float partialTick) {
        return Mth.lerp(partialTick, previousRescueProgress, rescueProgress);
    }
    public float rescueKnuckleProgress(int layerIndex, float partialTick) {
        return HexieBridges.couplerBlock().rescueCouplerState().exposed(rescueAnimationAdapter, rescueAnimationBaseModel, layerIndex)
                ? rescueKnuckleProgress(partialTick) : 0;
    }

    /** The existing carriage pose payload supplies the target; block-change packets do not carry BE NBT. */
    public void updateRescueAnimationTarget(CouplingBridge.CouplerAdapterView mounted, String baseModel, boolean coupled) {
        rescueAnimationAdapter = mounted;
        rescueAnimationBaseModel = baseModel;
        rescueTargetOpen = HexieBridges.couplerBlock().rescueCouplerState().exposed(mounted, baseModel) && !coupled;
        rescueAnimationInitialized = true;
    }

    private void readRescueAnimationTarget() {
        rescueAnimationAdapter = adapter;
        rescueAnimationBaseModel = getBlockState().getBlock() instanceof CouplerBlockView hook ? hook.model() : "";
        rescueTargetOpen = HexieBridges.couplerBlock().rescueCouplerState().open(rescueKnuckle);
        if (!rescueAnimationInitialized) {
            rescueProgress = previousRescueProgress = rescueTargetOpen ? 1 : 0;
            rescueAnimationInitialized = true;
        }
    }

    public boolean install(CouplingBridge.CouplerAdapterView value) {
        if (value == null || !(getBlockState().getBlock() instanceof CouplerBlockView coupler) || !coupler.allowsAdapters()
                || value.otherModel(adapter == null ? coupler.model() : adapter.otherModel(coupler.model())) == null) return false;
        adapter = HexieBridges.coupling().couplerAdapter().append(coupler.model(), adapter, value);
        rescueKnuckle = HexieBridges.couplerBlock().rescueCouplerState().sync(rescueKnuckle, adapter, coupler.model(), false,
                level == null ? 0 : level.getGameTime());
        readRescueAnimationTarget();
        updateMountedKnuckle();
        sync();
        return true;
    }

    public CouplingBridge.CouplerAdapterView removeOuter() {
        if (adapter == null || !(getBlockState().getBlock() instanceof CouplerBlockView hook)) return null;
        var removed = adapter.outer();
        adapter = adapter.withoutOuter(hook.model());
        rescueKnuckle = HexieBridges.couplerBlock().rescueCouplerState().sync(rescueKnuckle, adapter, hook.model(), false,
                level == null ? 0 : level.getGameTime());
        readRescueAnimationTarget();
        updateMountedKnuckle();
        if (adapter == null && realServerBlock()
                && getBlockState().getBlock() instanceof JanneyCouplerBlockView)
            level.setBlock(worldPosition, getBlockState().setValue(
                    BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
        sync();
        return removed;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void updateMountedKnuckle() {
        if (!realServerBlock() || !(getBlockState().getBlock() instanceof
                JanneyCouplerBlockView)) return;
        var open = BlockStateProperties.OPEN;
        if (adapter != null && getBlockState().getValue(open))
            level.setBlock(worldPosition, getBlockState().setValue(open, false), Block.UPDATE_ALL);
    }

    private boolean realServerBlock() {
        return level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this;
    }

    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        HexieBridges.couplerBlock().couplerAdapterData().write(tag, adapter);
        HexieBridges.couplerBlock().rescueCouplerState().copyState(rescueKnuckle, tag);
    }

    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CouplingBridge.CouplerAdapterView loaded = HexieBridges.couplerBlock().couplerAdapterData().read(tag);
        adapter = loaded != null && getBlockState().getBlock() instanceof CouplerBlockView coupler
                && loaded.otherModel(coupler.model()) != null ? loaded : null;
        rescueKnuckle = HexieBridges.couplerBlock().rescueCouplerState().copyState(tag);
        readRescueAnimationTarget();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public BlockEntity asBlockEntity() { return this; }
}
