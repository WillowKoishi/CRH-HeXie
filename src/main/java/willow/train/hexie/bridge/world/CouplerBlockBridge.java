package willow.train.hexie.bridge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.core.api.vehicle.Point3;

public interface CouplerBlockBridge {
    AbstractCouplerBlockOperations abstractCouplerBlock();

    interface AbstractCouplerBlockOperations {
        boolean toggleCover(Level level, BlockPos pos, BlockState state);
    }

    CouplerAdapterDataOperations couplerAdapterData();

    interface CouplerAdapterDataOperations {
        String KEY = "HexieCouplerAdapter";
        CouplingBridge.CouplerAdapterView read(CompoundTag root);
        CouplingBridge.CouplerAdapterView decode(CompoundTag tag);
        CompoundTag encode(CouplingBridge.CouplerAdapterView adapter);
        void write(CompoundTag root, CouplingBridge.CouplerAdapterView adapter);
    }

    CouplerBlockOperations couplerBlock();

    interface CouplerBlockOperations {
        CouplerBlockView of(BlockState state);
    }

    RescueCouplerStateOperations rescueCouplerState();

    interface RescueCouplerStateOperations {
        boolean exposed(CouplingBridge.CouplerAdapterView adapter, String baseModel, int layerIndex);
        boolean open(CompoundTag data);
        CompoundTag copyState(CompoundTag data);
        void copyState(CompoundTag from, CompoundTag to);
        boolean installed(CouplingBridge.CouplerAdapterView adapter);
        boolean exposed(CouplingBridge.CouplerAdapterView adapter, String baseModel);
        float progress(CompoundTag data, long now, float partialTick);
        CompoundTag withOpen(CompoundTag data, boolean open, long now);
        CompoundTag sync(CompoundTag data, CouplingBridge.CouplerAdapterView adapter, String baseModel,
                boolean coupled, long now);
    }

    interface AbstractCouplerBlockView extends CouplerBlockView, EntityBlock, InteractionBridge.NativeControlSurfaceView {
        default Block asBlock() { return (Block) this; }
    }

    interface CouplerBlockView {
        String model();
        Direction facing(BlockState state);
        CouplingBridge.CouplingEndpointAxisView axis();
        Point3 start();
        Point3 complete();
        default WorldBlockBridge.CouplerGeometryView geometry() { return null; }
        default BlockState carriageRenderState(BlockState state, boolean shiftedBase) { return state; }
        default boolean assembled(BlockState state) { return false; }
        default CouplingBridge.CouplingEndpointKind kind() { return CouplingBridge.CouplingEndpointKind.RIGID; }
        default double maxExtension() { return CouplingBridge.CouplingEndpointOperations.DEFAULT_MAX_EXTENSION; }
        default boolean requiresReadyState() { return false; }
        default boolean readyForCoupling(BlockState state, CompoundTag data, double time) { return true; }
        default boolean hasStateDrivenRelease() { return false; }
        default boolean releaseForState(BlockState state, CompoundTag data) { return false; }
        default boolean allowsAdapters() { return true; }
    }

    interface JanneyCouplerBlockView extends AbstractCouplerBlockView {
        WorldBlockBridge.CouplerGeometryView geometry();
        default Block asBlock() { return (Block) this; }
    }

    interface ModelCouplerBlockView extends AbstractCouplerBlockView {
        WorldBlockBridge.CouplerGeometryView geometry();
        default Block asBlock() { return (Block) this; }
    }

    interface ScharfenbergCouplerBlockView extends AbstractCouplerBlockView {
        WorldBlockBridge.CouplerGeometryView geometry();
        default Block asBlock() { return (Block) this; }
    }
}
