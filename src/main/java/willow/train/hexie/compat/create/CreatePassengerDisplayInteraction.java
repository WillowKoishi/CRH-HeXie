package willow.train.hexie.compat.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.blocks.signs.PanelDisplayMount;
import net.minecraft.server.level.ServerPlayer;

/** Assembled panel attachments are fixed until the carriage is disassembled. */
public final class CreatePassengerDisplayInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos position,
                                                      AbstractContraptionEntity entity) {
        if (player.isSpectator() || !player.mayBuild() || player.level() != entity.level() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        var contraption = entity.getContraption();
        var world = contraption.getContraptionWorld();
        var root = PartStructures.resolve(world, position);
        // Keep this interactor registered: consuming the panel click prevents an item or
        // generic contraption handler from treating a refused attachment edit as a fallback.
        // The separate movement behaviour continues to refresh timetable data as before.
        if (root == null || !PassengerDisplayPanel.supports(root.state().getBlock())) return false;
        // Create consumes the first client MAIN_HAND event before vanilla can try the off hand.
        boolean numberWrench = player.getItemInHand(hand).is(willow.train.hexie.init.item.AllItemsInit.HARMONY_WRENCH.get())
                || hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().is(willow.train.hexie.init.item.AllItemsInit.HARMONY_WRENCH.get());
        if (numberWrench
                && root.state().getValue(PanelDisplayMount.PROPERTY).hasNumber()
                && player instanceof ServerPlayer server)
            CreateCarriageNumberTarget.open(server, entity, root.pos(), position);
        return true;
    }
}
