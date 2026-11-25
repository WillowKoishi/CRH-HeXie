package willow.train.hexie.compat.create.contraption;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge.SS7DCenterCarportBlockView;

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
        if (info == null || !(info.state().getBlock() instanceof SS7DCenterCarportBlockView block)) return false;
        if (wrench && player.isShiftKeyDown()) {
            HexieBridges.toolOps().floorSwitcherItem().switchMode(player, wrenchHand);
            return true;
        }
        if (!entity.level().isClientSide)
            entity.setBlock(pos, new StructureTemplate.StructureBlockInfo(pos, wrench
                    ? block.cycleAppearance(info.state())
                    : info.state().cycle(BlockStateProperties.DOOR_HINGE), info.nbt()));
        return true;
    }

    private static InteractionHand wrenchHand(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) return hand;
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) return InteractionHand.OFF_HAND;
        return null;
    }
}
