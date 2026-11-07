package willow.train.hexie.compat.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.blocks.parts.VariantPartBlock;
import willow.train.hexie.item.FloorSwitcherItem;

/** Applies the same part appearance change to the backend's captured block states. */
public final class CreatePartVariantInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos position,
            AbstractContraptionEntity entity) {
        if (!(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem)
                || player.isSpectator() || !player.mayBuild() || entity.getContraption() == null
                || !entity.canInteractWithBlock(player, position, 4)) return false;
        var contraption = entity.getContraption();
        var clicked = contraption.getBlocks().get(position);
        if (clicked == null || !(clicked.state().getBlock() instanceof VariantPartBlock block)) return false;
        if (player.isShiftKeyDown()) {
            FloorSwitcherItem.switchMode(player, hand);
            return true;
        }
        var world = contraption.getContraptionWorld();
        var root = block.partRoot(world, position, clicked.state());
        var owner = contraption.getBlocks().get(root);
        if (owner == null || !owner.state().is(block)
                || !block.partRoot(world, root, owner.state()).equals(root)) return false;
        var cells = block.partCells(world, root, owner.state());
        if (!clicked.state().equals(cells.get(position))) return false;
        var appearance = owner.state().cycle(VariantPartBlock.ALTERNATE).getValue(VariantPartBlock.ALTERNATE);
        var updates = new ArrayList<StructureBlockInfo>(cells.size());
        for (var expected : cells.entrySet()) {
            var info = contraption.getBlocks().get(expected.getKey());
            if (info == null || !info.state().equals(expected.getValue())) return false;
            updates.add(new StructureBlockInfo(info.pos(), info.state().setValue(VariantPartBlock.ALTERNATE, appearance), info.nbt()));
        }
        if (!entity.level().isClientSide)
            for (var info : updates) entity.setBlock(info.pos(), info);
        return true;
    }
}
