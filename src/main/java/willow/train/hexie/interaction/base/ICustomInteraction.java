package willow.train.hexie.interaction.base;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface ICustomInteraction {
    String group();
    String key();
    Component descriptionKey();
    ICustomInteractionCondition getCondition();
    boolean canActive(Level level, Player player, InteractionHand hand, AbstractContraptionEntity contraptionEntity, BlockPos localPos, BlockState state, @Nullable BlockEntity blockEntity);
    InteractionResultContext active(Level level, Player player, InteractionHand hand, AbstractContraptionEntity contraptionEntity, BlockPos localPos, BlockState state, @Nullable BlockEntity blockEntity);

    class InteractionResultContext{

        public BlockState blockState;

        public BlockEntity blockEntity;

        public SoundEvent sound;
        public float pitch = 0f;

        public boolean success;

        private InteractionResultContext(BlockState state, BlockEntity be, boolean success) {
            this.blockState = state;
            this.blockEntity = be;
            this.success = success;
            this.sound = null;
        }

        private InteractionResultContext(BlockState state, BlockEntity be, SoundEvent sound, float pitch, boolean success) {
            this.blockState = state;
            this.blockEntity = be;
            this.sound = sound;
            this.pitch = pitch;
            this.success = success;
        }
        public static InteractionResultContext create(BlockState state, BlockEntity be, boolean success) {
            return new InteractionResultContext(state, be, success);
        }

        public static InteractionResultContext create(BlockState state, BlockEntity be, SoundEvent sound, float pitch, boolean success) {
            return new InteractionResultContext(state, be, sound, pitch, success);
        }

        public static InteractionResultContext defaultFailed() {
            return new InteractionResultContext(null, null, false);
        }
    }
}
