package willow.train.hexie.init.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.Main;
import willow.train.hexie.menu.AutomaticControlCouplingMenu;
import willow.train.hexie.menu.*;
import willow.train.hexie.client.screens.HexieInventory;
import willow.train.hexie.network.Env;

public class MenuInit {

    public static HexieInventory INVENTORY_WIDGET = null;
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Main.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<CarriageTypeSignEditMenu>>
            CARRIAGE_TYPE_SIGN_EDIT_MENU =
                    registerMenuType(CarriageTypeSignEditMenu::new, "carriage_type_sign_edit_menu");
    public static final DeferredHolder<MenuType<?>, MenuType<CarriageNoSignEditMenu>>
            CARRIAGE_NO_SIGN_EDIT_MENU =
                    registerMenuType(CarriageNoSignEditMenu::new, "carriage_no_sign_edit_menu");
    public static final DeferredHolder<MenuType<?>, MenuType<LacqueredBoardEditMenu>> LAQUERED_BOARD_EDIT_MENU =
            registerMenuType(LacqueredBoardEditMenu::new, "laquered_board_edit_menu");
    public static final DeferredHolder<MenuType<?>, MenuType<TrainSpeedSignEditMenu>>
            TRAIN_SPEED_SIGN_EDIT_MENU =
                    registerMenuType(TrainSpeedSignEditMenu::new, "train_speed_sign_edit_menu");
    public static final DeferredHolder<MenuType<?>, MenuType<TicketVendorMenu>> TICKET_VENDOR_MENU =
            registerMenuType(TicketVendorMenu::new, "ticket_vendor_menu");

    public static final DeferredHolder<MenuType<?>, MenuType<AnimatableBlockControllerMenu>> ANIMATABLE_BLOCK_CONTROLLER_MENU =
            registerMenuType(AnimatableBlockControllerMenu::new, "animatable_block_controller_menu");

    public static final DeferredHolder<MenuType<?>, MenuType<AutomaticControlCouplingMenu>> AUTOMATIC_CONTROL_COUPLING_MENU =
            registerMenuType(AutomaticControlCouplingMenu::new, "automatic_control_coupling_menu");

    private static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(
            IContainerFactory<T> factory, String name) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

    public static void invokeComponent() {
        if(Env.getCurrent() == Env.CLIENT) {INVENTORY_WIDGET = new HexieInventory();}
    }
}
