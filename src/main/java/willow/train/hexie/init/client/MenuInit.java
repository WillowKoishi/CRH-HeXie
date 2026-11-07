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
import willow.train.hexie.menu.*;

public class MenuInit {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Main.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<CarriageNumberMenu>> CARRIAGE_NUMBER_MENU =
            registerMenuType(CarriageNumberMenu::new, "carriage_number");
    public static final DeferredHolder<MenuType<?>, MenuType<PanelTextMenu>> PANEL_TEXT_MENU =
            registerMenuType(PanelTextMenu::new, "panel_text");
    public static final DeferredHolder<MenuType<?>, MenuType<CarriageMarkingMenu>> CARRIAGE_MARKING_MENU =
            registerMenuType(CarriageMarkingMenu::new, "carriage_marking");
    public static final DeferredHolder<MenuType<?>, MenuType<HeadNumberMenu>> HEAD_NUMBER_MENU =
            registerMenuType(HeadNumberMenu::new, "head_number");
    public static final DeferredHolder<MenuType<?>, MenuType<GroundStationSignMenu>> GROUND_STATION_SIGN_MENU =
            registerMenuType(GroundStationSignMenu::new, "ground_station_sign");
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

    private static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(
            IContainerFactory<T> factory, String name) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

}
