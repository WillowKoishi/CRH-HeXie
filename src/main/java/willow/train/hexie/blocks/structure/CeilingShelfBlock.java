package willow.train.hexie.blocks.structure;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.blocks.base.HorizontalBlockBase;

public class CeilingShelfBlock extends HorizontalBlockBase implements IWrenchable {

    public static VoxelShape TOP = Block.box(0, 8, 0, 16, 16, 16);
    // public static VoxelShape BASE = Block.box(0, 1, 0, 16, 2, 16);

    public CeilingShelfBlock(Properties p_49795_) {
        super(p_49795_);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        // return Shapes.join(BASE, TOP, BooleanOp.OR);
        return TOP;
    }
}
