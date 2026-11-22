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
import willow.train.hexie.item.FloorSwitcherItem;

/** Applies the same equipment selection to assembled Create and HeXie carriages. */
public final class SS7DFloorInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
                                                     AbstractContraptionEntity entity) {
        var wrenchHand = wrenchHand(player, hand);
        var contraption = entity.getContraption();
        if (wrenchHand == null || player.isSpectator() || !player.mayBuild() || player.level() != entity.level()
                || contraption == null || !entity.canInteractWithBlock(player, pos, 4)) return false;
        var info = contraption.getBlocks().get(pos);
        if (info == null || !(info.state().getBlock() instanceof SS7DFloorBlock)) return false;
        if (player.isShiftKeyDown()) {
            FloorSwitcherItem.switchMode(player, wrenchHand);
            return true;
        }

        var frame = entity instanceof SnapshotCarriageEntity snapshot ? CarriagePlayerSupport.frame(snapshot, 1) : null;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        Vec3 from = frame == null ? entity.toLocalVector(eye, 1) : frame.local(eye);
        Vec3 to = frame == null ? entity.toLocalVector(end, 1) : frame.local(end);
        var hit = info.state().getShape(contraption.getContraptionWorld(), pos).clip(from, to, pos);
        if (hit == null) return false;
        Vec3 center = Vec3.atCenterOf(pos);
        var worldPos = BlockPos.containing(frame == null ? entity.toGlobalVector(center, 1) : frame.world(center));
        if (!entity.level().mayInteract(player, worldPos)
                || !player.mayUseItemAt(worldPos, hit.getDirection(), player.getItemInHand(wrenchHand))) return false;
        if (!entity.level().isClientSide)
            entity.setBlock(pos, new StructureBlockInfo(pos, info.state().cycle(SS7DFloorBlock.EQUIPMENT), info.nbt()));
        return true;
    }

    private static InteractionHand wrenchHand(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem) return hand;
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().getItem() instanceof FloorSwitcherItem) return InteractionHand.OFF_HAND;
        return null;
    }
}
