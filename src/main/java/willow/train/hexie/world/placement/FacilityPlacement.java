package willow.train.hexie.world.placement;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.misc.MegaphoneBlock;
import willow.train.hexie.world.blocks.slidingdoor.ElectricFenceDoor;
import willow.train.hexie.world.blocks.slidingdoor.ElectricFenceWall;
import willow.train.hexie.world.blocks.structure.DirectionalCarpetBlock;
import willow.train.hexie.world.blocks.ticketvendor.TicketVendorBlock;
import willow.train.hexie.world.blocks.turnstile.TurnstileBlock;
import willow.train.hexie.world.blocks.turnstile.TurnstileCRBlock;

public final class FacilityPlacement {
    private FacilityPlacement() {}

    public static final class ForDirectionalCarpetBlock {
        public static @Nullable
        BlockState getStateForPlacement(BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get().setValue(DirectionalCarpetBlock.DIRECTION, context.getHorizontalDirection());
        }
    }

    public static final class ForElectricFenceDoor {
        public static BlockState getStateForPlacement(ElectricFenceDoor block, BlockPlaceContext context) {
            return block.defaultBlockState()
                    .setValue(ElectricFenceDoor.FACING, context.getHorizontalDirection())
                    .setValue(ElectricFenceDoor.HINGE, block.getHinge(context))
                    .setValue(ElectricFenceDoor.OPEN, Boolean.valueOf(false))
                    .setValue(ElectricFenceDoor.POWERED, Boolean.valueOf(false));
        }

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
    }

    public static final class ForElectricFenceWall {
        public static BlockState getStateForPlacement(ElectricFenceWall block, BlockPlaceContext context) {
            return block.defaultBlockState()
                    .setValue(HorizontalBlockBase.FACING, context.getHorizontalDirection())
                    .setValue(ElectricFenceWall.TYPE, 0);
        }

        public static BlockState getStateForPlacement(ElectricFenceWall block, Direction direction, int type) {
            return block.defaultBlockState().setValue(HorizontalBlockBase.FACING, direction).setValue(ElectricFenceWall.TYPE, type);
        }
    }

    public static final class ForMegaphoneBlock {
        public static BlockState getStateForPlacement(MegaphoneBlock block, BlockPlaceContext context) {
            return block.defaultBlockState()
                    .setValue(MegaphoneBlock.FACING, context.getHorizontalDirection())
                    .setValue(MegaphoneBlock.POWERED, false);
        }
    }

    public static final class ForTicketVendorBlock {
        public static BlockState getStateForPlacement(TicketVendorBlock block, BlockPlaceContext ctx) {
            return block.defaultBlockState()
                    .setValue(HorizontalBlockBase.FACING, ctx.getHorizontalDirection().getOpposite());
        }
    }

    public static final class ForTurnstileBlock {
        public static BlockState getStateForPlacement(TurnstileBlock block, BlockPlaceContext ctx) {
            return block.defaultBlockState()
                    .setValue(TurnstileBlock.FACING, ctx.getHorizontalDirection().getOpposite())
                    .setValue(TurnstileBlock.OPEN, false);
        }
    }

    public static final class ForTurnstileCRBlock {
        public static BlockState getStateForPlacement(TurnstileCRBlock block, BlockPlaceContext ctx) {
            return block.defaultBlockState()
                    .setValue(TurnstileCRBlock.FACING, ctx.getHorizontalDirection().getOpposite())
                    .setValue(TurnstileCRBlock.OPEN, false)
                    .setValue(TurnstileCRBlock.HINGE, 0);
        }

        public static BlockState getStateForPlacement(TurnstileCRBlock block, Direction direction, int hinge) {
            return block.defaultBlockState()
                    .setValue(TurnstileCRBlock.FACING, direction)
                    .setValue(TurnstileCRBlock.OPEN, false)
                    .setValue(TurnstileCRBlock.HINGE, hinge);
        }
    }
}
