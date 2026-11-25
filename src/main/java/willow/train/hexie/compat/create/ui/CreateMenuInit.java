package willow.train.hexie.compat.create.ui;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.core.HexieConstants;

final class CreateMenuInit {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HexieConstants.MOD_ID);
    static final DeferredHolder<MenuType<?>, MenuType<BogeySelectionMenu>> BOGEY_SELECTION_MENU =
            MENUS.register("bogey_selection", () -> IMenuTypeExtension.create(BogeySelectionMenu::new));

    private CreateMenuInit() {}

    static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
