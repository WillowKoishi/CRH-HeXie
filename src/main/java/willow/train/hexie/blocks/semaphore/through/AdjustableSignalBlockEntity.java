package willow.train.hexie.blocks.semaphore.through;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
        behaviours.add(orientation);
    }

    @Override
    protected ValueSettingsBoard createLightModeBoard() {
        return new ValueSettingsBoard(Component.translatable("gui.hexie.semaphore.settings"),
                LIGHT_MODE_DRAG_STEP * 2, LIGHT_MODE_DRAG_STEP,
                List.of(Component.translatable("gui.hexie.semaphore.light_mode.options"),
                        Component.translatable("gui.hexie.semaphore.alignment")),
                new ValueSettingsFormatter(settings -> settings.row() == 1
                        ? Component.translatable("gui.hexie.semaphore.alignment."
                                + (settings.value() > LIGHT_MODE_DRAG_STEP ? "track" : "world"))
                        : Component.translatable("gui.hexie.semaphore.light_mode.option."
                                + (modeFromDragValue(settings.value()) + 1))));
    }

    @Override
    protected boolean applyAdditionalLightSetting(Player player, ValueSettings settings) {
        if (settings.row() == 0)
            return false;
        if (settings.row() == 1 && player.mayBuild() && !player.isSpectator()
                && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            orientation.setTrackAligned(settings.value() > LIGHT_MODE_DRAG_STEP);
        }
        return true;
    }
}
