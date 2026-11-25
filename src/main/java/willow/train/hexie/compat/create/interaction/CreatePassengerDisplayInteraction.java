package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import willow.train.hexie.bridge.HexieBridges;

/** Assembled panel attachments are fixed until the carriage is disassembled. */
public final class CreatePassengerDisplayInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos position,
                                                      AbstractContraptionEntity entity) {
        if (player.isSpectator() || !player.mayBuild() || player.level() != entity.level() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        var contraption = entity.getContraption();
        var world = contraption.getContraptionWorld();
        var root = HexieBridges.partBlock().partStructures().resolve(world, position);
        // Keep this interactor registered: consuming the panel click prevents an item or
        // generic contraption handler from treating a refused attachment edit as a fallback.
        // The separate movement behaviour continues to refresh timetable data as before.
        if (root == null || !HexieBridges.signBlock().passengerDisplayPanel().supports(root.state().getBlock())) return false;
        // Create consumes the first client MAIN_HAND event before vanilla can try the off hand.
        boolean numberWrench = player.getItemInHand(hand).is(HexieBridges.worldRegistry().allItemsInit().harmonyWrench())
                || hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().is(HexieBridges.worldRegistry().allItemsInit().harmonyWrench());
        if (numberWrench
                && HexieBridges.signBlock().panelDisplayMount().hasNumber(root.state().getValue(HexieBridges.signBlock().panelDisplayMount().property()))
                && player instanceof ServerPlayer server)
            CreateCarriageNumberTarget.open(server, entity, root.pos(), position);
        return true;
    }
}
