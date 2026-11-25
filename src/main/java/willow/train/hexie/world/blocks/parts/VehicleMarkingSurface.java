package willow.train.hexie.world.blocks.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.world.PartBlockBridge;

/** The surface owns placement geometry; the root entity owns editable marking content. */
public interface VehicleMarkingSurface  extends PartBlockBridge.VehicleMarkingSurfaceView {
    BlockPos markingRoot(BlockGetter level,BlockPos pos,BlockState state);
    AABB markingBounds(BlockState state);
    AABB markingRenderBounds(BlockState state);
    double markingDepth(double localY);
    default double harmonyMarkingOffsetY() { return 0; }
    default Direction markingFacing(BlockState state) { return state.getValue(BlockStateProperties.HORIZONTAL_FACING); }
    default int markingSlots(BlockState state) { return markingBounds(state).getXsize()>1.5?2:1; }
    default int markingSlot(BlockState state,BlockPos root,Vec3 hit) {
        var bounds=markingBounds(state);double x=hit.x-root.getX()-.5,z=hit.z-root.getZ()-.5;
        double localX=.5+switch(markingFacing(state)) {
            case EAST -> z;case SOUTH -> -x;case WEST -> -z;default -> x;
        };
        return markingSlots(state)>1&&localX>=(bounds.minX+bounds.maxX)/2?1:0;
    }
}
