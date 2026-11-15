package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.blocks.emu.crh1a.CRH1DoorBlock;
import willow.train.hexie.blocks.emu.crh1a.CRH1DoorGeometry;
import willow.train.hexie.interaction.compatible.BlockEntitySynchronizeBehaviour;

/** The lower actor owns the tick-based animation, including after save/load and display rebuilds. */
public final class CRH1DoorMovement extends BlockEntitySynchronizeBehaviour {
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

    public static boolean fullyOpen(Contraption contraption, BlockPos lower, CRH1DoorBlock door) {
        var root = contraption.getBlocks().get(lower);
        return root != null && root.state().is(door) && root.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && root.state().getValue(DoorBlock.OPEN) && progress(contraption, lower, true) >= 1;
    }

    public static void initialiseDisplay(Contraption contraption, BlockPos lower, CustomRenderedDoorEntity display) {
        boolean open = display.getBlockState().getValue(DoorBlock.OPEN);
        float current = progress(contraption, lower, open);
        display.setCarriageAnimation(open, current, current);
    }

    @Override public void tick(MovementContext context) {
        if (context.state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return;
        var info = context.contraption.getBlocks().get(context.localPos);
        if (info == null || !info.state().is(context.state.getBlock())) return;
        boolean open = info.state().getValue(DoorBlock.OPEN);
        float previous = progress(context);
        float current = LerpedFloat.Chaser.LINEAR.chase(previous, CRH1DoorGeometry.ANIMATION_SPEED, open ? 1 : 0);
        if (current != previous) context.data.putFloat(PROGRESS, current);
        if (context.world.isClientSide
                && context.contraption.getBlockEntityClientSide(context.localPos) instanceof CustomRenderedDoorEntity display)
            display.setCarriageAnimation(open, previous, current);
        if (open && previous < 1 && current >= 1) context.contraption.invalidateColliders();
    }
}
