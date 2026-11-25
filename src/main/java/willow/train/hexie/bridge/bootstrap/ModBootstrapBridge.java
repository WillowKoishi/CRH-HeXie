package willow.train.hexie.bridge.bootstrap;

import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.util.general.CreativeTabGroup;

/** Construction-time services provided by the mod entry point. */
public interface ModBootstrapBridge {
    IEventBus modBus();

    default boolean isCreateLoaded() {
        return false;
    }

    CreativeTabGroup hexieMain();

    CreativeTabGroup hexieEmu();

    CreativeTabGroup hexieNormalSpeedPassageCarriageTab();

    CreativeTabGroup hexieLocos();

    CreativeTabGroup hexieDiet();

    WorldBlockBridge.PanelTypesUtilView panelTypes();
}
