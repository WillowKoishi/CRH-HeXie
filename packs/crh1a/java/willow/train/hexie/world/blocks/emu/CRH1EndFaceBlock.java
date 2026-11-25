package willow.train.hexie.world.blocks.emu;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.world.blocks.parts.ModelRenderBounds;
import willow.train.hexie.world.collision.CRH1EndFaceGeometry;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.collision.PlayerCollisionShell;
import willow.train.hexie.world.placement.EmuPlacement;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

/** CRH1A end wall, integral floor, half-permanent coupler and flexible inner gangway. */
public final class CRH1EndFaceBlock extends AbstractCouplerBlock implements PlayerCollisionShell, ModelRenderBounds, CarriageBlockBridge.CRH1EndFaceBlockView {
    public static final MapCodec<CRH1EndFaceBlock> CODEC = simpleCodec(CRH1EndFaceBlock::new);
    public static final String COUPLER_MODEL = "hexie:crh1a_semipermanent";
    public static final double BELLOWS_BASE_Z = -1 / 16d;
    public static final double BELLOWS_FRONT_Z = -8 / 16d;
    public static final double MAX_BELLOWS_THICKNESS = 8 / 16d;
    // The authored shaft joins its mount at z=-18.4 px; the contact face is at z=-32.1 px.
    private static final CouplingBridge.CouplingEndpointAxisView AXIS = HexieBridges.coupling().createCouplingEndpointAxis(0, (24 - 18.4) / 16 - .5);
    private static final Point3 CONTACT = new Point3(0, 10.6 / 16 - .5, (24 - 32.1) / 16 - .5);

    static { EmuCollision.ForCRH1EndFaceBlock.initialize(); }

    public CRH1EndFaceBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public VoxelShape couplerShape(BlockState state) {
        return EmuCollision.ForCRH1EndFaceBlock.couplerShape(this, state);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH1EndFaceBlock.getShape(this, state, world, pos, context);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH1EndFaceBlock.getCollisionShape(this, state, world, pos, context);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return EmuPlacement.ForCRH1EndFaceBlock.getStateForPlacement(this, context);
    }
    @Override public String model() { return COUPLER_MODEL; }
    @Override public CouplingBridge.CouplingEndpointAxisView axis() { return AXIS; }
    @Override public Point3 start() {
        return new Point3(CONTACT.x(), CONTACT.y(), BELLOWS_BASE_Z - MAX_BELLOWS_THICKNESS - .5);
    }
    @Override public Point3 complete() { return CONTACT; }
    @Override public double maxExtension() {
        return 2 * (MAX_BELLOWS_THICKNESS - (BELLOWS_BASE_Z - BELLOWS_FRONT_Z));
    }
    @Override public BlockState carriageRenderState(BlockState state, boolean shiftedBase) {
        return state.setValue(ASSEMBLED, true);
    }
    @Override public List<AABB> playerCollisionBoxes(BlockState state) {
        return EmuCollision.ForCRH1EndFaceBlock.playerCollisionBoxes(state);
    }
    @Override public AABB modelRenderBounds(BlockState state) {
        return CRH1EndFaceGeometry.bounds(state.getValue(FACING));
    }
    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (world instanceof Level level && level.isClientSide)
            HexieBridges.Client.carriageRender().crh1StaticSections().changed(world, pos, oldState, newState);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        var inherited = super.getTicker(level, state, type);
        if (inherited != null) return inherited;
        var mods = ModList.get();
        return mods.isLoaded("simurail") && mods.isLoaded("sable") && type == BlockEntitiesInit.COUPLER.get()
                ? (world, pos, blockState, entity) -> ((CouplerBlockEntityView) entity).tickCoverAnimation() : null;
    }
}
