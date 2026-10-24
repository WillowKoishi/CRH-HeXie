package willow.train.hexie.interaction.door;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.interaction.base.ICustomInteraction;
import willow.train.hexie.interaction.base.ICustomInteractionCondition;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;

public class DoorCloseInteraction implements ICustomInteraction {


    @Override
    public String group() {
        return "door";
    }

    @Override
    public String key() {
        return "close";
    }

    @Override
    public Component descriptionKey() {
        return Component.translatable("tooltip.hexie.abc.door.close");
    }

    @Override
    public ICustomInteractionCondition getCondition() {
        return (state, entity) -> {
            return state.getValue(DoorBlock.OPEN);
        };
    }

    @Override
    public boolean canActive(Level level, Player player, InteractionHand hand, AbstractContraptionEntity contraptionEntity, BlockPos localPos, BlockState state, @Nullable BlockEntity entity) {
        return getCondition().test(state, entity);
    }

    @Override
    public InteractionResultContext active(Level level, Player player, InteractionHand hand, AbstractContraptionEntity contraptionEntity, BlockPos localPos, BlockState state, @Nullable BlockEntity entity) {
        if(entity instanceof CustomRenderedDoorEntity crde) {
            crde.setOpen(false);
        }
        if(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            contraptionEntity.handlePlayerInteraction(player, localPos.above(), Direction.DOWN, hand);
        }
        return InteractionResultContext.create(state.setValue(DoorBlock.OPEN, false), entity, SoundEvents.IRON_DOOR_CLOSE, player.level().random.nextFloat() * 0.1F + 0.9F, true);
    }
}
