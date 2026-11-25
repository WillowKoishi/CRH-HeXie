package willow.train.hexie.world.blocks.emu;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DriverSeatBlockView;
import willow.train.hexie.core.util.seat.DriverSeatGeometry;
import willow.train.hexie.world.blocks.seats.StandaloneSeatBlock;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public final class StandaloneCRH2DriverSeatBlock extends StandaloneSeatBlock implements CRH2DriverSeatBlockView {
    private final DriverSeatGeometry geometry;

    public StandaloneCRH2DriverSeatBlock(Properties properties) {
        this(properties, DriverSeatGeometry.original());
    }

    public static StandaloneCRH2DriverSeatBlock cabSeat(Properties properties) {
        return new StandaloneCRH2DriverSeatBlock(properties, DriverSeatGeometry.cab());
    }

    private StandaloneCRH2DriverSeatBlock(Properties properties, DriverSeatGeometry geometry) {
        super(properties);
        this.geometry = geometry;
    }

    @Override public Vec3 driverEyePosition(BlockState state) {
        return geometry.eye(state.getValue(FACING));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return geometry.outline(state.getValue(FACING));
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return geometry.collision(state.getValue(FACING));
    }
}
