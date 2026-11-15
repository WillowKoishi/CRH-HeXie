package willow.train.hexie.compat.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.blocks.emu.crh1a.CRH1DoorBlock;
import willow.train.hexie.blocks.parts.PartStructures;

/** Forward the second column to Create's existing two-half door interaction. */
public final class CRH1DoorMemberInteraction extends MovingInteractionBehaviour {
    private final DoorMovingInteraction doors = new DoorMovingInteraction();

    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos, AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) return false;
        var owner = PartStructures.resolve(entity.getContraption().getContraptionWorld(), pos);
        return owner != null && owner.state().getBlock() instanceof CRH1DoorBlock
                && doors.handlePlayerInteraction(player, hand, owner.pos(), entity);
    }
}
