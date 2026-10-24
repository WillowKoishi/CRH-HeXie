package willow.train.hexie.blocks.locos.cr200j;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;

public class CR200jPanelBlocks extends SideMirrorBlockBase {
    protected int module_type = 0;

    protected static final VoxelShape SOUTH_AABB1 = Block.box(0, 0, 15.5, 16, 16, 16.5);
    protected static final VoxelShape EAST_AABB1 = Block.box(15.5, 0, 0, 16.5, 16, 16);
    protected static final VoxelShape NORTH_AABB1 = Block.box(0, 0, -0.5, 16, 16, 0.5);
    protected static final VoxelShape WEST_AABB1 = Block.box(-0.5, 0, 0, 0.5, 16, 16);

    protected static final VoxelShape SOUTH_AABB2 = Block.box(-16, 0, 15.5, 32, 32, 16.5);
    protected static final VoxelShape EAST_AABB2 = Block.box(15.5, 0, -16, 16.5, 32, 32);
    protected static final VoxelShape NORTH_AABB2 = Block.box(-16, 0, -0.5, 32, 32, 0.5);
    protected static final VoxelShape WEST_AABB2 = Block.box(-0.5, 0, -16, 0.5, 32, 32);

    protected static final VoxelShape SOUTH_AABB3 = Block.box(-16, 0, 15.5, 32, 16, 16.5);
    protected static final VoxelShape EAST_AABB3 = Block.box(15.5, 0, -16, 16.5, 16, 32);
    protected static final VoxelShape NORTH_AABB3 = Block.box(-16, 0, -0.5, 32, 16, 0.5);
    protected static final VoxelShape WEST_AABB3 = Block.box(-0.5, 0, -16, 0.5, 16, 32);

    protected static final VoxelShape AABB3 = Block.box(0, 0, 0, 16, 8, 16);

    public CR200jPanelBlocks(Properties properties, int mModule_type) {
        super(properties);
        module_type = mModule_type;
    }

    @Override
    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        switch (module_type) {
            case 1:
                switch ((Direction) p_54372_.getValue(FACING)) {
                    case NORTH:
                        return NORTH_AABB2;
                    case SOUTH:
                        return SOUTH_AABB2;
                    case WEST:
                        return WEST_AABB2;
                    case EAST:
                    default:
                        return EAST_AABB2;
                }
            case 2:
                return AABB3;
            case 3:
                return switch (p_54372_.getValue(FACING)) {
                    case NORTH -> NORTH_AABB3;
                    case SOUTH -> SOUTH_AABB3;
                    case WEST -> WEST_AABB3;
                    default -> EAST_AABB3;
                };
            default:
                switch ((Direction) p_54372_.getValue(FACING)) {
                    case NORTH:
                        return NORTH_AABB1;
                    case SOUTH:
                        return SOUTH_AABB1;
                    case WEST:
                        return WEST_AABB1;
                    case EAST:
                    default:
                        return EAST_AABB1;
                }
        }
    }
}
