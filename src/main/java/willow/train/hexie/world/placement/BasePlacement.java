package willow.train.hexie.world.placement;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.parts.LegacyPartLayout;

public final class BasePlacement {
    private BasePlacement() {}

    public static final class ForHorizontalBlockBase {
        public static BlockState getStateForPlacement(HorizontalBlockBase block, BlockPlaceContext context) {

            return block.defaultBlockState().setValue(HorizontalBlockBase.FACING, context.getHorizontalDirection());
        }

        public static void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving, Runnable baseOnPlace) {
            baseOnPlace.run();
            LegacyPartLayout.schedule(level,pos,state,moving);
        }
    }

    public static final class ForSideMirrorBlockBase {
        public static DoorHingeSide getHinge(BlockPlaceContext pContext) {
            BlockGetter blockgetter = pContext.getLevel();
            BlockPos blockpos = pContext.getClickedPos();
            Direction direction = pContext.getHorizontalDirection();
            BlockPos blockpos1 = blockpos.above();
            Direction direction1 = direction.getCounterClockWise();
            BlockPos blockpos2 = blockpos.relative(direction1);
            BlockState blockstate = blockgetter.getBlockState(blockpos2);
            BlockPos blockpos3 = blockpos1.relative(direction1);
            BlockState blockstate1 = blockgetter.getBlockState(blockpos3);
            Direction direction2 = direction.getClockWise();
            BlockPos blockpos4 = blockpos.relative(direction2);
            BlockState blockstate2 = blockgetter.getBlockState(blockpos4);
            BlockPos blockpos5 = blockpos1.relative(direction2);
            BlockState blockstate3 = blockgetter.getBlockState(blockpos5);
            int i =
                    (blockstate.isCollisionShapeFullBlock(blockgetter, blockpos2) ? -1 : 0)
                            + (blockstate1.isCollisionShapeFullBlock(blockgetter, blockpos3) ? -1 : 0)
                            + (blockstate2.isCollisionShapeFullBlock(blockgetter, blockpos4) ? 1 : 0)
                            + (blockstate3.isCollisionShapeFullBlock(blockgetter, blockpos5) ? 1 : 0);
            if (i <= 0) {
                if (i >= 0) {
                    int j = direction.getStepX();
                    int k = direction.getStepZ();
                    Vec3 vec3 = pContext.getClickLocation();
                    double d0 = vec3.x - (double) blockpos.getX();
                    double d1 = vec3.z - (double) blockpos.getZ();
                    return (j >= 0 || !(d1 < 0.5D))
                                    && (j <= 0 || !(d1 > 0.5D))
                                    && (k >= 0 || !(d0 > 0.5D))
                                    && (k <= 0 || !(d0 < 0.5D))
                            ? DoorHingeSide.LEFT
                            : DoorHingeSide.RIGHT;
                } else {
                    return DoorHingeSide.LEFT;
                }
            } else {
                return DoorHingeSide.RIGHT;
            }
        }

        public static @Nullable
        BlockState getStateForPlacement(SideMirrorBlockBase block, BlockPlaceContext pContext) {
            return block.defaultBlockState()
                    .setValue(SideMirrorBlockBase.FACING, pContext.getHorizontalDirection())
                    .setValue(SideMirrorBlockBase.HINGE, getHinge(pContext));
        }
    }
}
