package willow.train.hexie.config;
import net.createmod.catnip.config.ConfigBase;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import willow.train.hexie.Main;

@EventBusSubscriber(modid = Main.MOD_ID)
public final class ConfigEvents {
    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        for (ConfigBase config : HXConfig.CONFIGS.values())
            if (config.specification == event.getConfig().getSpec()) config.onLoad();
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        for (ConfigBase config : HXConfig.CONFIGS.values())
            if (config.specification == event.getConfig().getSpec()) config.onReload();
    }
}
