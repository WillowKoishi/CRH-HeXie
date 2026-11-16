package willow.train.hexie.blocks.emu.crh1a;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.blocks.parts.PlayerCollisionShell;
import willow.train.hexie.blocks.parts.PlayerCollisionShells;
import willow.train.hexie.blocks.parts.ModelRenderBounds;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Axis;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** CRH1A end wall, integral floor, half-permanent coupler and flexible inner gangway. */
public final class CRH1EndFaceBlock extends AbstractCouplerBlock implements PlayerCollisionShell, ModelRenderBounds {
    public static final MapCodec<CRH1EndFaceBlock> CODEC = simpleCodec(CRH1EndFaceBlock::new);
    public static final String COUPLER_MODEL = "hexie:crh1a_semipermanent";
    public static final double BELLOWS_BASE_Z = -1 / 16d;
    public static final double BELLOWS_FRONT_Z = -8 / 16d;
    public static final double MAX_BELLOWS_THICKNESS = 8 / 16d;
    // The authored shaft joins its mount at z=-18.4 px; the contact face is at z=-32.1 px.
    private static final Axis AXIS = new Axis(0, (24 - 18.4) / 16 - .5);
    private static final Point CONTACT = new Point(0, 10.6 / 16 - .5, (24 - 32.1) / 16 - .5);

    static { PlayerCollisionShells.register(CRH1EndFaceGeometry::collisions); }

    public CRH1EndFaceBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected VoxelShape couplerShape(BlockState state) { return Shapes.block(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection());
    }
    @Override public String model() { return COUPLER_MODEL; }
    @Override public Axis axis() { return AXIS; }
    @Override public Point start() {
        return new Point(CONTACT.x(), CONTACT.y(), BELLOWS_BASE_Z - MAX_BELLOWS_THICKNESS - .5);
    }
    @Override public Point complete() { return CONTACT; }
    @Override public double maxExtension() {
        return 2 * (MAX_BELLOWS_THICKNESS - (BELLOWS_BASE_Z - BELLOWS_FRONT_Z));
    }
    @Override public BlockState carriageRenderState(BlockState state, boolean shiftedBase) {
        return state.setValue(ASSEMBLED, true);
    }
    @Override public List<AABB> playerCollisionBoxes(BlockState state) {
        return CRH1EndFaceGeometry.playerBoxes(state.getValue(FACING));
    }
    @Override public AABB modelRenderBounds(BlockState state) {
        return CRH1EndFaceGeometry.bounds(state.getValue(FACING));
    }
    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (world instanceof Level level && level.isClientSide)
            willow.train.hexie.client.render.CRH1StaticSections.changed(world, pos, oldState, newState);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        var inherited = super.getTicker(level, state, type);
        if (inherited != null) return inherited;
        var mods = net.neoforged.fml.ModList.get();
        return mods.isLoaded("simurail") && mods.isLoaded("sable") && type == willow.train.hexie.init.BlockEntitiesInit.COUPLER.get()
                ? (world, pos, blockState, entity) -> ((CouplerBlockEntity) entity).tickCoverAnimation() : null;
    }
}
