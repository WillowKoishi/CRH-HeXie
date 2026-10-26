package willow.train.hexie.event.client;

import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.semaphore.through.ThroughSignalBlockEntity;
import willow.train.hexie.blocks.semaphore.through.AdjustableSignalBlockEntity;
import willow.train.hexie.client.screens.CenteredSemaphoreValueSettingsScreen;
import willow.train.hexie.client.screens.AdjustableSemaphoreSettingsScreen;

@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class SemaphoreScreenEvents {

    private SemaphoreScreenEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof ValueSettingsScreen))
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit))
            return;
        if (!hit.getBlockPos().equals(CreateClient.VALUE_SETTINGS_HANDLER.interactHeldPos)
                || !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof ThroughSignalBlockEntity))
            return;

        BlockEntityBehaviour active = BlockEntityBehaviour.get(minecraft.level, hit.getBlockPos(),
                CreateClient.VALUE_SETTINGS_HANDLER.interactHeldBehaviour);
        if (!(active instanceof ValueSettingsBehaviour behaviour))
            return;

        if (active instanceof ScrollValueBehaviour
                && minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof AdjustableSignalBlockEntity signal) {
            event.setNewScreen(new AdjustableSemaphoreSettingsScreen(hit.getBlockPos(),
                    behaviour.createBoard(minecraft.player, hit), behaviour.getValueSettings(),
                    behaviour::newSettingHovered, behaviour.netId(), signal.orientation.isTrackAligned()));
        } else {
            event.setNewScreen(new CenteredSemaphoreValueSettingsScreen(hit.getBlockPos(),
                behaviour.createBoard(minecraft.player, hit), behaviour.getValueSettings(),
                behaviour::newSettingHovered, behaviour.netId()));
        }
    }

}
