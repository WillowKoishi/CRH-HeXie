package willow.train.hexie.interaction.compatible;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.blocks.locos.cr200j.CR200jFrontBlock;
import willow.train.hexie.init.item.ResourceItemInit;
import willow.train.hexie.interaction.InteractionProcessor;
import willow.train.hexie.interaction.base.ICustomInteraction;
import willow.train.hexie.item.AnimatableBlockControllerItem;
import willow.train.hexie.item.AutomaticControlCouplingItem;
import willow.train.hexie.blockentity.CR200jFrontBlockEntity;

public class RemoteControlInteractionBehaviour extends MovingInteractionBehaviour {
    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
                                           AbstractContraptionEntity entity) {
        var contraption = entity.getContraption();
        StructureBlockInfo info = contraption.getBlocks().get(pos);
        if (info == null || !(info.state().getBlock() instanceof CR200jFrontBlock)) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ResourceItemInit.AutomaticControlCoupling.get())) {
            if (!player.level().isClientSide()) AutomaticControlCouplingItem.addBlock(stack, entity, pos);
            return true;
        }
        if (!stack.is(ResourceItemInit.AnimatableBlockController.get())) return false;
        String[] selection = AnimatableBlockControllerItem.getControllingGroupAndKey(stack);
        if (selection.length != 2) return false;
        ICustomInteraction interaction = InteractionProcessor.getInteraction(selection[0], selection[1]);
        if (interaction == null) return false;
        if (player.level().isClientSide()) return true;

        BlockEntity blockEntity = info.nbt() == null ? null : BlockEntity.loadStatic(pos, info.state(),
                info.nbt(), player.level().registryAccess());
        if (!(blockEntity instanceof CR200jFrontBlockEntity)) return false;
        blockEntity.setLevel(player.level());
        if (!interaction.canActive(player.level(), player, hand, entity, pos, info.state(), blockEntity)) return false;
        var result = interaction.active(player.level(), player, hand, entity, pos, info.state(), blockEntity);
        if (!result.success) return false;
        CompoundTag data = result.blockEntity == null ? info.nbt()
                : result.blockEntity.saveWithFullMetadata(player.level().registryAccess());
        setContraptionBlockData(entity, pos, new StructureBlockInfo(pos, result.blockState, data));
        return true;
    }
}
