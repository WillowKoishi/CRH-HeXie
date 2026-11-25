package willow.train.hexie.compat.create.bogey;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge;

final class CreateBogeyCollision {
    private CreateBogeyCollision() {}

    public static VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos) {
        var floorData = HexieBridges.worldBlock().bogeyFloorData();
        if (state.getValue(HexieBogeyBlock.HEAD_MOUNTED)) {
            var shift = world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be
                    ? floorData.displacement(state, floorData.longitudinal(be.getBogeyData()))
                    : Vec3.ZERO;
            if (world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be && !be.isCarriageView())
                shift = shift.add(0, floorData.offset(floorData.heightFromBogeyData(be.getBogeyData())), 0);
            return Shapes.box(0, 5/16d, 0, 1, 1, 1).move(shift.x, shift.y, shift.z);
        }
        double height = world instanceof VehicleBridge.CarriageFloorViewView ? 0
                : world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be && !be.isCarriageView()
                ? floorData.offset(floorData.heightFromBogeyData(be.getBogeyData())) : 0;
        return Shapes.block().move(0, height, 0);
    }

    public static VoxelShape getCollisionShape(HexieBogeyBlock<?> block, BlockState state,
            BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(HexieBogeyBlock.HEAD_MOUNTED)) return Shapes.empty();
        return block.getShape(state, world, pos, context);
    }
}
