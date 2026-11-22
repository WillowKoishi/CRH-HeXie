package willow.train.hexie.blocks.locos.ss7d;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.item.editable_panels.ColoredBrush;

/** Applies the same roof switches to a captured Create or HeXie carriage. */
public final class SS7DSideCarportInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos position,
                                                     AbstractContraptionEntity entity) {
        if (player.isSpectator() || !player.mayBuild() || player.level() != entity.level()
                || entity.getContraption() == null || !entity.canInteractWithBlock(player, position, 4)) return false;
        InteractionHand brushHand = brushHand(player, hand);
        boolean empty = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty();
        if (brushHand == null && !empty) return false;

        var contraption = entity.getContraption();
        var info = contraption.getBlocks().get(position);
        if (info == null || !(info.state().getBlock() instanceof SS7DSideCarportBlock)) return false;
        var frame = entity instanceof SnapshotCarriageEntity snapshot ? CarriagePlayerSupport.frame(snapshot, 1) : null;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        Vec3 from = frame == null ? entity.toLocalVector(eye, 1) : frame.local(eye);
        Vec3 to = frame == null ? entity.toLocalVector(end, 1) : frame.local(end);
        var hit = info.state().getShape(contraption.getContraptionWorld(), position).clip(from, to, position);
        if (hit == null) return false;
        Vec3 center = Vec3.atCenterOf(position);
        var worldPos = BlockPos.containing(frame == null ? entity.toGlobalVector(center, 1) : frame.world(center));
        var stack = player.getItemInHand(brushHand == null ? hand : brushHand);
        if (!entity.level().mayInteract(player, worldPos)
                || !player.mayUseItemAt(worldPos, hit.getDirection(), stack)) return false;

        if (!entity.level().isClientSide) {
            var next = brushHand == null ? info.state().cycle(SS7DSideCarportBlock.SIDE)
                    : info.state().cycle(SS7DSideCarportBlock.ALTERNATE);
            entity.setBlock(position, new StructureBlockInfo(position, next, info.nbt()));
            if (brushHand != null && !player.isCreative())
                player.setItemInHand(brushHand, ColoredBrush.onEffectiveUse(stack));
        }
        return true;
    }

    private static InteractionHand brushHand(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof ColoredBrush) return hand;
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().getItem() instanceof ColoredBrush) return InteractionHand.OFF_HAND;
        return null;
    }
}
