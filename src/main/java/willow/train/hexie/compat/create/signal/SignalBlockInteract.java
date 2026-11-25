package willow.train.hexie.compat.create.signal;

import com.simibubi.create.content.trains.signal.SignalBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SignalBlockInteract {
    private SignalBlockInteract() {}

    public static final class ForAdjustableSignalBlock {
        public static InteractionResult onWrenched(BlockState state, UseOnContext context) {
            if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof AdjustableSignalBlockEntity signal
                    && context.getPlayer() != null && context.getPlayer().mayBuild()
                    && !context.getLevel().isClientSide) {
                signal.orientation.rotateQuarterTurn();
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static final class ForThroughSignalBlock {
        public static InteractionResult onWrenched(ThroughSignalBlock block, BlockState state, UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            block.withBlockEntityDo(level, pos, ste -> {
                SignalBoundary signal = ste.getSignal();
                Player player = context.getPlayer();
                if (signal != null) {
                    signal.cycleSignalType(pos);
                    if (player != null)
                        player.displayClientMessage(Component.translatable("create.through_signal.mode_change." + signal.getTypeFor(pos)
                                .getSerializedName()), true);
                } else if (player != null)
                    player.displayClientMessage(Component.translatable("create.through_signal.cannot_change_mode"), true);
            });
            return InteractionResult.SUCCESS;
        }
    }
}
