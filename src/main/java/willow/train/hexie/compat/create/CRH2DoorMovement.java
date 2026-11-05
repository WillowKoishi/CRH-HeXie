package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.emu.crh2a.CRH2DoorBlock;
import willow.train.hexie.interaction.compatible.BlockEntitySynchronizeBehaviour;

/** Both halves use the lower actor's progress, which Create saves and sends to new tracking clients. */
public final class CRH2DoorMovement extends BlockEntitySynchronizeBehaviour {
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

    public static boolean fullyOpen(Contraption contraption, BlockPos lower, CRH2DoorBlock door) {
        var root = contraption.getBlocks().get(lower);
        if (root == null || !root.state().is(door) || root.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER)
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

    public static void initialiseDisplay(Contraption contraption, BlockPos lower, CustomRenderedDoorEntity display) {
        boolean open = display.getBlockState().getValue(DoorBlock.OPEN);
        float current = progress(contraption, lower, open);
        display.setCarriageAnimation(open, current, current);
    }

    @Override
    public void tick(MovementContext context) {
        if (context.state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return;
        // Create updates the block map on both sides. The actor's captured state can still be the old one.
        var info = context.contraption.getBlocks().get(context.localPos);
        if (info == null || !info.state().is(context.state.getBlock())) return;
        boolean open = info.state().getValue(DoorBlock.OPEN);
        float previous = progress(context);
        float current = LerpedFloat.Chaser.LINEAR.chase(previous, CRH2DoorBlock.ANIMATION_SPEED, open ? 1 : 0);
        if (current != previous) context.data.putFloat(PROGRESS, current);
        if (context.world.isClientSide
                && context.contraption.getBlockEntityClientSide(context.localPos) instanceof CustomRenderedDoorEntity display)
            display.setCarriageAnimation(open, previous, current);
        // Closing already rebuilds colliders when OPEN changes. Opening needs one more rebuild at the endpoint.
        if (open && previous < 1 && current >= 1) context.contraption.invalidateColliders();
    }
}
