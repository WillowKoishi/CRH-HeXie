package willow.train.hexie.interaction;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.item.ResourceItemInit;
import willow.train.hexie.item.AutomaticControlCouplingItem;

/** Create moving-door interaction that also records HeXie's control coupler. */
public class HexieDoorMovingInteraction extends DoorMovingInteraction {

    @Override
    protected BlockState handle(
            Player player, Contraption contraption, BlockPos pos, BlockState currentState) {
        BlockState updatedState = super.handle(player, contraption, pos, currentState);
        if (player == null)
            return updatedState;

        ItemStack held = player.getItemInHand(player.getUsedItemHand());
        if (held.is(ResourceItemInit.AutomaticControlCoupling.get()))
            AutomaticControlCouplingItem.addBlock(held, contraption.entity, pos);

        return updatedState;
    }
}
