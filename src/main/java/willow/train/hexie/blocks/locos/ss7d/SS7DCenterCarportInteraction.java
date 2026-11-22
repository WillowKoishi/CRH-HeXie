package willow.train.hexie.blocks.locos.ss7d;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.item.FloorSwitcherItem;

/** Cycles the roof state in the captured contraption so it survives disassembly. */
public final class SS7DCenterCarportInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
            AbstractContraptionEntity entity) {
        var contraption = entity.getContraption();
        InteractionHand wrenchHand = wrenchHand(player, hand);
        boolean wrench = wrenchHand != null;
        boolean empty = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty();
        if ((!wrench && !empty) || player.isSpectator() || !player.mayBuild() || player.level() != entity.level()
                || contraption == null || !entity.canInteractWithBlock(player, pos, 4)) return false;
        var info = contraption.getBlocks().get(pos);
        if (info == null || !(info.state().getBlock() instanceof SS7DCenterCarportBlock)) return false;
        if (wrench && player.isShiftKeyDown()) {
            FloorSwitcherItem.switchMode(player, wrenchHand);
            return true;
        }
        if (!entity.level().isClientSide)
            entity.setBlock(pos, new StructureBlockInfo(pos, wrench
                    ? info.state().cycle(SS7DCenterCarportBlock.APPEARANCE)
                    : info.state().cycle(SS7DCenterCarportBlock.SIDE), info.nbt()));
        return true;
    }

    private static InteractionHand wrenchHand(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem) return hand;
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().getItem() instanceof FloorSwitcherItem) return InteractionHand.OFF_HAND;
        return null;
    }
}
