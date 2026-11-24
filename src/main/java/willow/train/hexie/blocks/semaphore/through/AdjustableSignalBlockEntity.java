package willow.train.hexie.blocks.semaphore.through;

import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class AdjustableSignalBlockEntity extends ThroughSignalBlockEntity {
    public SignalOrientationBehaviour orientation;

    public AdjustableSignalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        orientation = new SignalOrientationBehaviour(this);
        // Let wrench angle controls run before the general lamp settings.
        behaviours.addFirst(orientation);
    }

    @Override
    protected ValueSettingsBoard createLightModeBoard() {
        return new ValueSettingsBoard(Component.translatable("gui.hexie.semaphore.settings"),
                LIGHT_MODE_DRAG_STEP * 2, LIGHT_MODE_DRAG_STEP,
                List.of(Component.translatable("gui.hexie.semaphore.light_mode.options"),
                        Component.translatable("gui.hexie.semaphore.alignment"),
                        Component.translatable("gui.hexie.semaphore.signal_mode")),
                new ValueSettingsFormatter(settings -> switch (settings.row()) {
                    case 1 -> Component.translatable("gui.hexie.semaphore.alignment."
                            + (settings.value() > LIGHT_MODE_DRAG_STEP ? "track" : "world"));
                    case 2 -> Component.translatable("gui.hexie.semaphore.signal_mode."
                            + (settings.value() > LIGHT_MODE_DRAG_STEP ? "chain" : "block"));
                    default -> Component.translatable("gui.hexie.semaphore.light_mode.option."
                            + (modeFromDragValue(settings.value()) + 1));
                }));
    }

    @Override
    protected boolean applyAdditionalLightSetting(Player player, ValueSettings settings) {
        if (settings.row() == 0)
            return false;
        if (!player.mayBuild() || player.isSpectator() || level == null || level.isClientSide)
            return true;
        if (settings.row() == 1) {
            orientation.setTrackAligned(settings.value() > LIGHT_MODE_DRAG_STEP);
        } else if (settings.row() == 2) {
            if (computerBehaviour.hasAttachedComputer()) {
                player.displayClientMessage(CreateLang.translateDirect("track_signal.mode_controlled_by_computer"), true);
                return true;
            }
            SignalBoundary signal = getSignal();
            if (signal == null) {
                player.displayClientMessage(CreateLang.translateDirect("track_signal.cannot_change_mode"), true);
                return true;
            }
            SignalBlock.SignalType desired = settings.value() > LIGHT_MODE_DRAG_STEP
                    ? SignalBlock.SignalType.CROSS_SIGNAL : SignalBlock.SignalType.ENTRY_SIGNAL;
            if (signal.getTypeFor(worldPosition) != desired) {
                signal.cycleSignalType(worldPosition);
                level.setBlock(worldPosition, getBlockState().setValue(SignalBlock.TYPE, desired), Block.UPDATE_ALL);
                refreshBlockState();
                player.displayClientMessage(CreateLang.translateDirect("track_signal.mode_change."
                        + desired.getSerializedName()), true);
            }
        }
        return true;
    }

}
