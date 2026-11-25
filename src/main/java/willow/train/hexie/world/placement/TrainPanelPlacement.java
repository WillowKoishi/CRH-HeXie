package willow.train.hexie.world.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelPlacementBackend;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.parts.PartDefinitions;

/** Copycat-panel placement: extend within the existing panel's plane. */
public final class TrainPanelPlacement {
    private TrainPanelPlacement() {}

    private static volatile PanelPlacementBackend backend;

    public static void register(PanelPlacementBackend placement) {
        backend = placement;
    }

    public static ItemInteractionResult place(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        var placement = backend;
        return placement == null ? null : placement.place(stack, state, level, pos, player, hand, hit);
    }

    private static boolean reverseFacing(Block block) {
        return PartDefinitions.get(block.defaultBlockState()).reverseFacing();
    }

    public static BlockState align(BlockState placed, BlockState source) {
        Direction facing = source.getValue(SideMirrorBlockBase.FACING);
        if (reverseFacing(source.getBlock()) != reverseFacing(placed.getBlock())) facing = facing.getOpposite();
        placed = placed.setValue(SideMirrorBlockBase.FACING, facing);
        boolean hasMirror = source.hasProperty(SideMirrorBlockBase.HINGE) || reverseFacing(source.getBlock());
        boolean left = source.hasProperty(SideMirrorBlockBase.HINGE)
                ? source.getValue(SideMirrorBlockBase.HINGE) == DoorHingeSide.LEFT
                : reverseFacing(source.getBlock()) && source.getValue(BlockStateProperties.HALF) == Half.TOP;
        if (hasMirror && placed.hasProperty(SideMirrorBlockBase.HINGE))
            placed = placed.setValue(SideMirrorBlockBase.HINGE, left ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
        if (hasMirror && reverseFacing(placed.getBlock()))
            placed = placed.setValue(BlockStateProperties.HALF, left ? Half.TOP : Half.BOTTOM);
        if (!reverseFacing(source.getBlock()) && !reverseFacing(placed.getBlock())
                && source.hasProperty(BlockStateProperties.OPEN) && placed.hasProperty(BlockStateProperties.OPEN))
            placed = placed.setValue(BlockStateProperties.OPEN, source.getValue(BlockStateProperties.OPEN));
        return placed;
    }
}
