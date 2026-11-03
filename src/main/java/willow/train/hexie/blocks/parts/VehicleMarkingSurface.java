package willow.train.hexie.blocks.parts;

import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/** The surface owns placement geometry; the root entity owns editable marking content. */
public interface VehicleMarkingSurface {
    BlockPos markingRoot(BlockGetter level,BlockPos pos,BlockState state);
    AABB markingBounds(BlockState state);
    AABB markingRenderBounds(BlockState state);
    double markingDepth(double localY);
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
