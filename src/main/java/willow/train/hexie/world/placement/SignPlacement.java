package willow.train.hexie.world.placement;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.core.util.enums.PanelTypes;
import willow.train.hexie.core.util.enums.SlabTypes;
import willow.train.hexie.core.util.enums.UpperPanelTypes;
import willow.train.hexie.core.util.general.ItemData;
import willow.train.hexie.world.blocks.base.SignBlockBase;
import willow.train.hexie.world.blocks.signs.GroundStationSignBlock;
import willow.train.hexie.world.blocks.signs.LacqueredBoardBlock;
import willow.train.hexie.world.blocks.signs.TrainNoBlock;
import willow.train.hexie.world.blocks.signs.TrainPanelBlock;
import willow.train.hexie.world.blocks.signs.TrainSpeedSignBlock;
import willow.train.hexie.world.registry.blocks.BlockInit;

public final class SignPlacement {
    private SignPlacement() {}

    public static final class ForGroundStationSignBlock {
        public static BlockState getStateForPlacement(GroundStationSignBlock block, BlockPlaceContext context) {
            Direction facing = context.getHorizontalDirection().getOpposite();
            for (int i = 0; i < 9; i++) {
                BlockPos p = GroundStationSignBlock.cell(context.getClickedPos(), facing, i);
                if (context.getLevel().isOutsideBuildHeight(p) || !context.getLevel().getBlockState(p).canBeReplaced(context)) return null;
            }
            return block.defaultBlockState().setValue(GroundStationSignBlock.FACING, facing);
        }

        public static void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (!level.isClientSide) for (int i = 0; i < 9; i++) if (i != 1)
                level.setBlock(GroundStationSignBlock.cell(pos, state.getValue(GroundStationSignBlock.FACING), i), state.setValue(GroundStationSignBlock.PART, i), 3);
        }
    }

    public static final class ForLacqueredBoardBlock {
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

        public static BlockState getStateForPlacement(LacqueredBoardBlock block, BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get()
                    .setValue(LacqueredBoardBlock.HINGE, block.getHinge(context))
                    .setValue(LacqueredBoardBlock.TYPE, PanelTypes.P25G);
        }

        public static BlockState getStateForPlacement(BlockPlaceContext context, PanelTypes types, DoorHingeSide hinge, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get().setValue(LacqueredBoardBlock.HINGE, hinge).setValue(LacqueredBoardBlock.TYPE, types);
        }
    }

    public static final class ForSignBlockBase {
        public static BlockState getStateForPlacement(SignBlockBase block, BlockPlaceContext context) {
            return block.defaultBlockState().setValue(SignBlockBase.FACING, context.getHorizontalDirection());
        }
    }

    public static final class ForTrainNoBlock {
        public static BlockState getStateForPlacement(TrainNoBlock block, BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get()
                    .setValue(TrainNoBlock.HINGE, block.getHinge(context))
                    .setValue(TrainNoBlock.TYPE, UpperPanelTypes.P25BA);
        }

        public static BlockState getStateForPlacement(BlockPlaceContext context, DoorHingeSide hinge, UpperPanelTypes types, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get().setValue(TrainNoBlock.HINGE, hinge).setValue(TrainNoBlock.TYPE, types);
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

    public static final class ForTrainPanelBlock {
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

        public static BlockState getStateForPlacement(TrainPanelBlock block, BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            Player pPlayer = context.getPlayer();
            ItemStack itemStack = pPlayer.getItemInHand(context.getHand());
            block.getHinge(context);

            if (itemStack.is(BlockInit.TRAIN_BOTTOM_PANEL_BLOCK.get().asItem())) {
                CompoundTag tag = ItemData.getTagElement(itemStack, "type");
                if (tag == null) tag = ItemData.getOrCreateTagElement(itemStack, "type");
                if (tag.get("type") == null) tag.putString("type", "p25b");
                return baseGetStateForPlacement.get()
                        .setValue(TrainPanelBlock.HINGE, block.getHinge(context))
                        .setValue(TrainPanelBlock.TYPE, PanelTypes.encode(tag.getString("type")))
                        .setValue(TrainPanelBlock.MIRROR, Boolean.valueOf(false));
            }
            return baseGetStateForPlacement.get()
                    .setValue(TrainPanelBlock.HINGE, block.getHinge(context))
                    .setValue(TrainPanelBlock.TYPE, PanelTypes.P25G);
        }

        public static BlockState getStateForPlacement(BlockPlaceContext context, PanelTypes type, DoorHingeSide hinge, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get()
                    .setValue(TrainPanelBlock.HINGE, hinge)
                    .setValue(TrainPanelBlock.TYPE, type)
                    .setValue(TrainPanelBlock.MIRROR, Boolean.valueOf(false));
        }
    }

    public static final class ForTrainSpeedSignBlock {
        public static BlockState getStateForPlacement(BlockPlaceContext context, Direction direction, SlabTypes types, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get()
                    .setValue(TrainSpeedSignBlock.TYPE, types)
                    .setValue(SignBlockBase.FACING, direction);
        }

        public static BlockState getStateForPlacement(BlockPlaceContext context, Supplier<BlockState> baseGetStateForPlacement) {
            return baseGetStateForPlacement.get().setValue(TrainSpeedSignBlock.TYPE, SlabTypes.S25B);
        }
    }
}
