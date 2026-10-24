package willow.train.hexie.blocks.structure;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.blocks.base.HorizontalBlockBase;

public class YZTableBlock extends HorizontalBlockBase implements IWrenchable {

    VoxelShape N_AABB = Block.box(3, 14, 0, 13, 15, 8);
    VoxelShape S_AABB = Block.box(3, 14, 8, 13, 15, 16);
    VoxelShape E_AABB = Block.box(8, 14, 3, 16, 15, 13);
    VoxelShape W_AABB = Block.box(0, 14, 3, 8, 15, 13);

    public YZTableBlock(Properties p_49795_) {
        super(p_49795_);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        switch (pState.getValue(FACING)) {
            case NORTH -> {
                return N_AABB;
            }
            case SOUTH -> {
                return S_AABB;
            }
            case EAST -> {
                return E_AABB;
            }
            case WEST -> {
                return W_AABB;
            }
            default -> {
                return N_AABB;
            }
        }
    }
}
