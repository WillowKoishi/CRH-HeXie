package willow.train.hexie.world.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.parts.PartMemberBlock;
import willow.train.hexie.world.blocks.parts.PartStructures;

public final class PartCollision {
    private PartCollision() {}

    public static final class ForPartMemberBlock {
        public static VoxelShape getShape(PartMemberBlock block, BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
            var owner=PartStructures.resolve(level,pos,state);
            if(owner==null)return Shapes.empty();
            var root=owner.pos();
            return Shapes.join(owner.state().getShape(level,root).move(root.getX()-pos.getX(),root.getY()-pos.getY(),root.getZ()-pos.getZ()),Shapes.block(),BooleanOp.AND);
        }
    }
}
