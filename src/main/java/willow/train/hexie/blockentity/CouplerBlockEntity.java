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
import willow.train.hexie.blocks.coupler.RescueCouplerState;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;

/** Stores mounted converters from the original hook to the exposed outer layer. */
public class CouplerBlockEntity extends BlockEntity implements willow.train.hexie.blocks.base.BlockEntityAnimation {
    private CouplerAdapter adapter;
    private float previousCoverProgress, coverProgress;
    private CompoundTag rescueKnuckle = new CompoundTag();
    private float previousRescueProgress = 1, rescueProgress = 1;
    private boolean rescueAnimationInitialized, rescueTargetOpen = true;
    private CouplerAdapter rescueAnimationAdapter;
    private String rescueAnimationBaseModel = "";

    /** Optional backends choose their supported blocks after mod loading. */
    public static CouplerBlockEntity create(BlockPos pos, BlockState state) {
        var mods = net.neoforged.fml.ModList.get();
        if (mods.isLoaded("simurail") && mods.isLoaded("sable"))
            return willow.train.hexie.compat.simurail.SimurailCouplerFactory.create(pos, state);
        return new CouplerBlockEntity(pos, state);
    }

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
        updateMountedKnuckle();
        previousCoverProgress = coverProgress;
        coverProgress = net.minecraft.util.Mth.approach(coverProgress, coverOpen(getBlockState()) ? 1 : 0, .1f);
        previousRescueProgress = rescueProgress;
        rescueProgress = net.minecraft.util.Mth.approach(rescueProgress, rescueTargetOpen ? 1 : 0, .1f);
    }

    public float coverProgress(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, previousCoverProgress, coverProgress);
    }

    @Override public void restoreAnimation(BlockEntity previous) {
        if (previous instanceof CouplerBlockEntity source) {
            previousCoverProgress = source.previousCoverProgress;
            coverProgress = source.coverProgress;
            previousRescueProgress = source.previousRescueProgress;
            rescueProgress = source.rescueProgress;
            rescueAnimationInitialized = true;
        }
    }

    public CouplerAdapter adapter() { return adapter; }

    /** Ordinary placed blocks and captured carriage views do not have physical connections. */
    public boolean uncouplePhysical() { return false; }

    /** Physical connections drive the same persisted knuckle state as the carriage runtime. */
    protected void updatePhysicalCouplingAnimation(boolean coupled) {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof CouplerBlock hook)) return;
        if (adapter == null && getBlockState().getBlock() instanceof willow.train.hexie.blocks.coupler.JanneyCouplerBlock) {
            var open = net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;
            if (getBlockState().getValue(open) == coupled)
                level.setBlock(worldPosition, getBlockState().setValue(open, !coupled), Block.UPDATE_ALL);
        }
        rescueKnuckle = RescueCouplerState.sync(rescueKnuckle, adapter, hook.model(), coupled, level.getGameTime());
        readRescueAnimationTarget();
        sync();
    }
    public boolean hasRescueCoupler() { return RescueCouplerState.installed(adapter); }
    public boolean rescueKnuckleOpen() { return rescueTargetOpen; }
    public float rescueKnuckleProgress(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, previousRescueProgress, rescueProgress);
    }
    public float rescueKnuckleProgress(int layerIndex, float partialTick) {
        return RescueCouplerState.exposed(rescueAnimationAdapter, rescueAnimationBaseModel, layerIndex)
                ? rescueKnuckleProgress(partialTick) : 0;
    }

    /** The existing carriage pose payload supplies the target; block-change packets do not carry BE NBT. */
    public void updateRescueAnimationTarget(CouplerAdapter mounted, String baseModel, boolean coupled) {
        rescueAnimationAdapter = mounted;
        rescueAnimationBaseModel = baseModel;
        rescueTargetOpen = RescueCouplerState.exposed(mounted, baseModel) && !coupled;
        rescueAnimationInitialized = true;
    }

    private void readRescueAnimationTarget() {
        rescueAnimationAdapter = adapter;
        rescueAnimationBaseModel = getBlockState().getBlock() instanceof CouplerBlock hook ? hook.model() : "";
        rescueTargetOpen = RescueCouplerState.open(rescueKnuckle);
        if (!rescueAnimationInitialized) {
            rescueProgress = previousRescueProgress = rescueTargetOpen ? 1 : 0;
            rescueAnimationInitialized = true;
        }
    }

    public boolean install(CouplerAdapter value) {
        if (value == null || !(getBlockState().getBlock() instanceof CouplerBlock coupler)
                || value.otherModel(adapter == null ? coupler.model() : adapter.otherModel(coupler.model())) == null) return false;
        adapter = CouplerAdapter.append(coupler.model(), adapter, value);
        rescueKnuckle = RescueCouplerState.sync(rescueKnuckle, adapter, coupler.model(), false,
                level == null ? 0 : level.getGameTime());
        readRescueAnimationTarget();
        updateMountedKnuckle();
        sync();
        return true;
    }

    public CouplerAdapter removeOuter() {
        if (adapter == null || !(getBlockState().getBlock() instanceof CouplerBlock hook)) return null;
        var removed = adapter.outer();
        adapter = adapter.withoutOuter(hook.model());
        rescueKnuckle = RescueCouplerState.sync(rescueKnuckle, adapter, hook.model(), false,
                level == null ? 0 : level.getGameTime());
        readRescueAnimationTarget();
        updateMountedKnuckle();
        if (adapter == null && realServerBlock()
                && getBlockState().getBlock() instanceof willow.train.hexie.blocks.coupler.JanneyCouplerBlock)
            level.setBlock(worldPosition, getBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
        sync();
        return removed;
    }

    private void sync() {
        setChanged();
        willow.train.hexie.api.interaction.WorldInteractionTargets.refresh(this);
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void updateMountedKnuckle() {
        if (!realServerBlock() || !(getBlockState().getBlock() instanceof
                willow.train.hexie.blocks.coupler.JanneyCouplerBlock)) return;
        var open = net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;
        if (adapter != null && getBlockState().getValue(open))
            level.setBlock(worldPosition, getBlockState().setValue(open, false), Block.UPDATE_ALL);
    }

    private boolean realServerBlock() {
        return level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CouplerAdapterData.write(tag, adapter);
        RescueCouplerState.copyState(rescueKnuckle, tag);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CouplerAdapter loaded = CouplerAdapterData.read(tag);
        adapter = loaded != null && getBlockState().getBlock() instanceof CouplerBlock coupler
                && loaded.otherModel(coupler.model()) != null ? loaded : null;
        rescueKnuckle = RescueCouplerState.copyState(tag);
        readRescueAnimationTarget();
        willow.train.hexie.api.interaction.WorldInteractionTargets.refresh(this);
    }

    @Override public void onLoad() {
        super.onLoad();
        willow.train.hexie.api.interaction.WorldInteractionTargets.refresh(this);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
