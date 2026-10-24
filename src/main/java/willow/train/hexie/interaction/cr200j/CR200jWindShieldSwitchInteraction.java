package willow.train.hexie.interaction.cr200j;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.interaction.base.ICustomInteraction;
import willow.train.hexie.interaction.base.ICustomInteractionCondition;
import willow.train.hexie.blockentity.CR200jFrontBlockEntity;

public class CR200jWindShieldSwitchInteraction implements ICustomInteraction {


    @Override
    public String group() {
        return "cr200j";
    }

    @Override
    public String key() {
        return "wind_shield";
    }

    @Override
    public Component descriptionKey() {
        return Component.translatable("tooltip.hexie.abc.cr200j.wind_shield");
    }

    @Override
    public ICustomInteractionCondition getCondition() {
        return (state, entity) -> entity instanceof CR200jFrontBlockEntity;
    }

    @Override
    public boolean canActive(Level level, Player player, InteractionHand hand, AbstractContraptionEntity contraptionEntity, BlockPos localPos, BlockState state, @Nullable BlockEntity entity) {
        return getCondition().test(state, entity);
    }

    @Override
    public InteractionResultContext active(Level level, Player player, InteractionHand hand, AbstractContraptionEntity contraptionEntity, BlockPos localPos, BlockState state, @Nullable BlockEntity entity) {
        if(!(entity instanceof CR200jFrontBlockEntity)) return InteractionResultContext.defaultFailed();
        ((CR200jFrontBlockEntity)entity).switchWindShield();
        return InteractionResultContext.create(state, entity, true);
    }
}
