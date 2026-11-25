package willow.train.hexie.compat.create.ui;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.AdjustableSignalBlockEntityView;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.ThroughSignalBlockEntityView;

public final class SemaphoreScreenEvents {

    private SemaphoreScreenEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof FloorHeightSettingsScreen)
            return;
        if (!(event.getNewScreen() instanceof ValueSettingsScreen))
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit))
            return;
        if (!hit.getBlockPos().equals(CreateClient.VALUE_SETTINGS_HANDLER.interactHeldPos)
                || !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof ThroughSignalBlockEntityView))
            return;

        BlockEntityBehaviour active = BlockEntityBehaviour.get(minecraft.level, hit.getBlockPos(),
                CreateClient.VALUE_SETTINGS_HANDLER.interactHeldBehaviour);
        if (!(active instanceof ValueSettingsBehaviour behaviour))
            return;

        if (active instanceof ScrollValueBehaviour
                && minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof AdjustableSignalBlockEntityView signal) {
            event.setNewScreen(new AdjustableSemaphoreSettingsScreen(hit.getBlockPos(),
                    behaviour.createBoard(minecraft.player, hit), behaviour.getValueSettings(),
                    behaviour::newSettingHovered, behaviour.netId(), signal.orientation().isTrackAligned(),
                    signal.asBlockEntity().getBlockState().getValue(SignalBlock.TYPE) == SignalBlock.SignalType.CROSS_SIGNAL));
        } else {
            event.setNewScreen(new CenteredSemaphoreValueSettingsScreen(hit.getBlockPos(),
                behaviour.createBoard(minecraft.player, hit), behaviour.getValueSettings(),
                behaviour::newSettingHovered, behaviour.netId()));
        }
    }

}
