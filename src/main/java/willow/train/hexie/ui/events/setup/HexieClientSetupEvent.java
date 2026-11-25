package willow.train.hexie.ui.events.setup;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.CabRenderBridge.Ctcs2DmiModuleShell;
import willow.train.hexie.core.HexieConstants;

@EventBusSubscriber(
        modid = HexieConstants.MOD_ID,
        value = Dist.CLIENT)
public class HexieClientSetupEvent {


    @SubscribeEvent
    public static void doSetup(EntityRenderersEvent.RegisterRenderers event) {
        var seat = HexieBridges.worldRegistry().entityInit().yzSeatEntity();
        if (ModList.get().isLoaded("create")) registerSeatRenderer(event, seat);
        else event.registerEntityRenderer(seat, NoopRenderer::new);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> void registerSeatRenderer(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type) {
        event.registerEntityRenderer(type, context ->
                (EntityRenderer<T>) HexieBridges.Client.render().createHexieSeatRenderer(context));
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        if (ModList.get().isLoaded("create")) {
            for (var shell : Ctcs2DmiModuleShell.values())
                event.register(HexieBridges.Client.cabRender().ctcs2DmiModuleShell().model(shell));
            HexieBridges.Client.blockEntityRender().drivingConsoleRenderer().registerModels(event);
        }
        for(ResourceLocation location : HexieBridges.Client.worldRegistryClient().modelInit().unbaked()) {
            event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(location));
        }
        HexieBridges.Client.worldRegistryClient().modelInit().unbaked().clear();
    }

    @SubscribeEvent
    public static void getBaked(ModelEvent.BakingCompleted event) {
        HexieBridges.Client.worldRegistryClient().modelInit().setBaked(event.getModels());
    }
}
