package willow.train.hexie.event.client;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.bus.api.SubscribeEvent;

import willow.train.hexie.Main;
import willow.train.hexie.client.render.entity.HexieSeatRenderer;
import willow.train.hexie.init.EntityInit;
import willow.train.hexie.init.ModelInit;

@EventBusSubscriber(
        modid = Main.MOD_ID,
        value = Dist.CLIENT)
public class HexieClientSetupEvent {


    @SubscribeEvent
    public static void doSetup(EntityRenderersEvent.RegisterRenderers event) {

        event.registerEntityRenderer(EntityInit.YZ_SEAT_ENTITY.get(), HexieSeatRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        for(ResourceLocation location : ModelInit.UNBAKED) {
            event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(location));
        }
        ModelInit.UNBAKED.clear();
    }

    @SubscribeEvent
    public static void getBaked(ModelEvent.BakingCompleted event) {
        ModelInit.BAKED = event.getModels();
    }
}
