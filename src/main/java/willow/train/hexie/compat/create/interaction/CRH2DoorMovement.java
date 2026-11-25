package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateDoorBridge.CRH2DoorMovementView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockOperations;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;

/** Both halves use the lower actor's progress, which Create saves and sends to new tracking clients. */
public final class CRH2DoorMovement implements CRH2DoorMovementView {
    @Override public boolean mustTickWhileDisabled() { return true; }
    @Override public boolean disableBlockEntityRendering() { return false; }

    private static final String PROGRESS = "DoorProgress";

    @Override
    public void startMoving(MovementContext context) {
        if (context.state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
            progress(context);
    }

    private static float progress(MovementContext context) {
        // Old saves have no progress. Initialise them in their assembled pose before the first interaction.
        if (!context.data.contains(PROGRESS))
            context.data.putFloat(PROGRESS, context.state.getValue(DoorBlock.OPEN) ? 1 : 0);
        return context.data.getFloat(PROGRESS);
    }

    public static boolean fullyOpen(Contraption contraption, BlockPos lower, CRH2DoorBlockView door) {
        var root = contraption.getBlocks().get(lower);
        if (root == null || !root.state().is(door.asBlock()) || root.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER)
            return false;
        boolean open = root.state().getValue(DoorBlock.OPEN);
        return progress(contraption, lower, open) >= 1 && open;
    }

    private static float progress(Contraption contraption, BlockPos lower, boolean open) {
        var actor = contraption.getActorAt(lower);
        // Create can build colliders before startMoving attaches the movement contexts.
        if (actor == null || actor.right == null) return open ? 1 : 0;
        return progress(actor.right);
    }

    public static void initialiseDisplay(Contraption contraption, BlockPos lower, CustomRenderedDoorEntityView display) {
        boolean open = display.asBlockEntity().getBlockState().getValue(DoorBlock.OPEN);
        float current = progress(contraption, lower, open);
        display.setCarriageAnimation(open, current, current);
    }

    @Override
    public void tick(MovementContext context) {
        if (context.state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return;
        // Create updates the block map on both sides. The actor's captured state can still be the old one.
        var info = context.contraption.getBlocks().get(context.localPos);
        if (info == null || !info.state().is(context.state.getBlock())
                || !(info.state().getBlock() instanceof CRH2DoorBlockView door)) return;
        boolean open = info.state().getValue(DoorBlock.OPEN);
        float previous = progress(context);
        int duration = door.animationDurationTicks();
        float current = duration > 0 ? HexieBridges.worldBlock().customRenderedDoorEntity().tickProgress(previous, open, duration)
                : LerpedFloat.Chaser.LINEAR.chase(previous, CRH2DoorBlockOperations.ANIMATION_SPEED, open ? 1 : 0);
        if (current != previous) context.data.putFloat(PROGRESS, current);
        if (context.world.isClientSide
                && context.contraption.getBlockEntityClientSide(context.localPos) instanceof CustomRenderedDoorEntityView display)
            display.setCarriageAnimation(open, previous, current);
        // Closing already rebuilds colliders when OPEN changes. Opening needs one more rebuild at the endpoint.
        if (open && previous < 1 && current >= 1) context.contraption.invalidateColliders();
    }
}
