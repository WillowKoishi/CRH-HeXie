package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateDoorBridge.CRH1DoorMovementView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorGeometryOperations;

/** The lower actor owns the tick-based animation, including after save/load and display rebuilds. */
public final class CRH1DoorMovement implements CRH1DoorMovementView {
    @Override public boolean mustTickWhileDisabled() { return true; }
    @Override public boolean disableBlockEntityRendering() { return false; }

    private static final String PROGRESS = "CRH1DoorProgress";

    @Override public void startMoving(MovementContext context) {
        if (context.state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) progress(context);
    }

    private static float progress(MovementContext context) {
        if (!context.data.contains(PROGRESS))
            context.data.putFloat(PROGRESS, context.state.getValue(DoorBlock.OPEN) ? 1 : 0);
        return context.data.getFloat(PROGRESS);
    }

    private static float progress(Contraption contraption, BlockPos lower, boolean open) {
        var actor = contraption.getActorAt(lower);
        return actor == null || actor.right == null ? open ? 1 : 0 : progress(actor.right);
    }

    public static boolean fullyOpen(Contraption contraption, BlockPos lower, CRH1DoorBlockView door) {
        var root = contraption.getBlocks().get(lower);
        return root != null && root.state().is(door.asBlock()) && root.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && root.state().getValue(DoorBlock.OPEN) && progress(contraption, lower, true) >= 1;
    }

    public static void initialiseDisplay(Contraption contraption, BlockPos lower, CustomRenderedDoorEntityView display) {
        boolean open = display.asBlockEntity().getBlockState().getValue(DoorBlock.OPEN);
        float current = progress(contraption, lower, open);
        display.setCarriageAnimation(open, current, current);
    }

    @Override public void tick(MovementContext context) {
        if (context.state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return;
        var info = context.contraption.getBlocks().get(context.localPos);
        if (info == null || !info.state().is(context.state.getBlock())) return;
        boolean open = info.state().getValue(DoorBlock.OPEN);
        float previous = progress(context);
        float current = HexieBridges.worldBlock().customRenderedDoorEntity().tickProgress(previous, open, CRH1DoorGeometryOperations.ANIMATION_TICKS);
        if (current != previous) context.data.putFloat(PROGRESS, current);
        if (context.world.isClientSide
                && context.contraption.getBlockEntityClientSide(context.localPos) instanceof CustomRenderedDoorEntityView display)
            display.setCarriageAnimation(open, previous, current);
        if (open && previous < 1 && current >= 1) context.contraption.invalidateColliders();
    }
}
