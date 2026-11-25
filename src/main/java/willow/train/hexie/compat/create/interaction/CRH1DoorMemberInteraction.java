package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorBlockView;

/** Forward the second column to Create's existing two-half door interaction. */
public final class CRH1DoorMemberInteraction extends MovingInteractionBehaviour {
    private final DoorMovingInteraction doors = new DoorMovingInteraction();

    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos, AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) return false;
        var owner = HexieBridges.partBlock().partStructures().resolve(entity.getContraption().getContraptionWorld(), pos);
        return owner != null && owner.state().getBlock() instanceof CRH1DoorBlockView
                && doors.handlePlayerInteraction(player, hand, owner.pos(), entity);
    }
}
