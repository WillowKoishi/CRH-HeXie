package willow.train.hexie.event.client;

import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.semaphore.through.ThroughSignalBlockEntity;
import willow.train.hexie.client.screens.CenteredSemaphoreValueSettingsScreen;

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

        ScrollValueBehaviour behaviour = BlockEntityBehaviour.get(minecraft.level, hit.getBlockPos(),
                ScrollValueBehaviour.TYPE);
        if (behaviour == null)
            return;

        event.setNewScreen(new CenteredSemaphoreValueSettingsScreen(hit.getBlockPos(),
                behaviour.createBoard(minecraft.player, hit), behaviour.getValueSettings(),
                behaviour::newSettingHovered, behaviour.netId()));
    }

}
