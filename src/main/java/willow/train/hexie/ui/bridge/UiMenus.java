package willow.train.hexie.ui.bridge;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.ui.MenuBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.WorldBlockBridge.CRH2HeadEntityOperations;
import willow.train.hexie.ui.menu.CarriageMarkingMenu;
import willow.train.hexie.ui.menu.CarriageNoSignEditMenu;
import willow.train.hexie.ui.menu.CarriageNumberMenu;
import willow.train.hexie.ui.menu.CarriageTypeSignEditMenu;
import willow.train.hexie.ui.menu.GroundStationSignMenu;
import willow.train.hexie.ui.menu.HeadNumberMenu;
import willow.train.hexie.ui.menu.LacqueredBoardEditMenu;
import willow.train.hexie.ui.menu.MenuInit;
import willow.train.hexie.ui.menu.PanelTextMenu;
import willow.train.hexie.ui.menu.SS7DNumberMenu;
import willow.train.hexie.ui.menu.StandardTrackPavingMenu;
import willow.train.hexie.ui.menu.TicketVendorMenu;
import willow.train.hexie.ui.menu.TrainSpeedSignEditMenu;

final class UiMenus implements MenuBridge {
    private BogeySelectionMenuOperations bogeySelection = (player, target, hand) -> false;
    private Supplier<MenuType<?>> bogeySelectionType = () -> null;

    @Override
    public void registerBogeySelection(BogeySelectionMenuOperations menu, Supplier<MenuType<?>> type) {
        bogeySelection = menu;
        bogeySelectionType = type;
    }

    @Override
    public BogeySelectionMenuOperations bogeySelectionMenu() { return bogeySelection; }

    private static final CarriageMarkingMenuOperations carriageMarkingMenu = new CarriageMarkingMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, BlockEntity target) {
            return new CarriageMarkingMenu(id, inventory, target);
        }

        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView moving) {
            return CarriageMarkingMenu.openMoving(player, moving);
        }
    };

    @Override
    public CarriageMarkingMenuOperations carriageMarkingMenu() { return carriageMarkingMenu; }

    private static final CarriageNoSignEditMenuOperations carriageNoSignEditMenu = new CarriageNoSignEditMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
            return new CarriageNoSignEditMenu(id, inventory, entity, data);
        }

        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target) {
            return CarriageNoSignEditMenu.openMoving(player, target);
        }

        @Override
        public void openWorld(ServerPlayer player, BlockEntityViews.CarriageNoSignEntityView target, BlockPos position) {
            player.openMenu(target, position);
        }
    };

    @Override
    public CarriageNoSignEditMenuOperations carriageNoSignEditMenu() { return carriageNoSignEditMenu; }

    private static final CarriageNumberMenuOperations carriageNumberMenu = new CarriageNumberMenuOperations() {
        @Override
        public boolean open(ServerPlayer player, CarriageNumberMenuTargetView target) {
            return CarriageNumberMenu.open(player, target);
        }

        @Override
        public boolean openStatic(ServerPlayer player, BlockEntity blockEntity) {
            return CarriageNumberMenu.openStatic(player, blockEntity);
        }
    };

    @Override
    public CarriageNumberMenuOperations carriageNumberMenu() { return carriageNumberMenu; }

    private static final CarriageTypeSignEditMenuOperations carriageTypeSignEditMenu = new CarriageTypeSignEditMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
            return new CarriageTypeSignEditMenu(id, inventory, entity, data);
        }

        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target) {
            return CarriageTypeSignEditMenu.openMoving(player, target);
        }

        @Override
        public void openWorld(ServerPlayer player, BlockEntityViews.CarriageTypeSignEntityView target, BlockPos position) {
            player.openMenu(target, position);
        }
    };

    @Override
    public CarriageTypeSignEditMenuOperations carriageTypeSignEditMenu() { return carriageTypeSignEditMenu; }

    private static final GroundStationSignMenuOperations groundStationSignMenu = new GroundStationSignMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, BlockPos pos, String[] fields) {
            return new GroundStationSignMenu(id, inventory, pos, fields);
        }

        @Override
        public void openWorld(ServerPlayer player, BlockEntityViews.GroundStationSignEntityView target, BlockPos position) {
            player.openMenu(target, buf -> {
                buf.writeBlockPos(position);
                for (String field : target.fields()) buf.writeUtf(field, 128);
            });
        }
    };

    @Override
    public GroundStationSignMenuOperations groundStationSignMenu() { return groundStationSignMenu; }

    private static final HeadNumberMenuOperations headNumberMenu = new HeadNumberMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, BlockEntityViews.CRH2HeadEntityView target) {
            return new HeadNumberMenu(id, inventory, target);
        }

        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView moving) {
            return HeadNumberMenu.openMoving(player, moving);
        }

        @Override
        public void openWorld(ServerPlayer player, BlockEntityViews.CRH2HeadEntityView target, BlockPos position) {
            player.openMenu(target, buf -> {
                buf.writeBlockPos(position);
                buf.writeUtf(target.number(), CRH2HeadEntityOperations.MAX_LENGTH * 2);
            });
        }
    };

    @Override
    public HeadNumberMenuOperations headNumberMenu() { return headNumberMenu; }

    private static final LacqueredBoardEditMenuOperations lacqueredBoardEditMenu = new LacqueredBoardEditMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
            return new LacqueredBoardEditMenu(id, inventory, entity, data);
        }

        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target) {
            return LacqueredBoardEditMenu.openMoving(player, target);
        }

        @Override
        public void openWorld(ServerPlayer player, BlockEntityViews.LacqueredBoardEntityView target, BlockPos position) {
            player.openMenu(target, position);
        }
    };

    @Override
    public LacqueredBoardEditMenuOperations lacqueredBoardEditMenu() { return lacqueredBoardEditMenu; }

    private final MenuInitOperations menuInit = new MenuInitOperations() {
        @Override
        public MenuType<?> bogeySelectionMenu() {
            return bogeySelectionType.get();
        }

        @Override
        public MenuType<?> carriageNumberMenu() {
            return MenuInit.CARRIAGE_NUMBER_MENU.get();
        }

        @Override
        public MenuType<?> panelTextMenu() {
            return MenuInit.PANEL_TEXT_MENU.get();
        }

        @Override
        public MenuType<?> carriageMarkingMenu() {
            return MenuInit.CARRIAGE_MARKING_MENU.get();
        }

        @Override
        public MenuType<?> headNumberMenu() {
            return MenuInit.HEAD_NUMBER_MENU.get();
        }

        @Override
        public MenuType<?> ss7dNumberMenu() {
            return MenuInit.SS7D_NUMBER_MENU.get();
        }

        @Override
        public MenuType<?> groundStationSignMenu() {
            return MenuInit.GROUND_STATION_SIGN_MENU.get();
        }

        @Override
        public MenuType<?> carriageTypeSignEditMenu() {
            return MenuInit.CARRIAGE_TYPE_SIGN_EDIT_MENU.get();
        }

        @Override
        public MenuType<?> carriageNoSignEditMenu() {
            return MenuInit.CARRIAGE_NO_SIGN_EDIT_MENU.get();
        }

        @Override
        public MenuType<?> laqueredBoardEditMenu() {
            return MenuInit.LAQUERED_BOARD_EDIT_MENU.get();
        }

        @Override
        public MenuType<?> trainSpeedSignEditMenu() {
            return MenuInit.TRAIN_SPEED_SIGN_EDIT_MENU.get();
        }

        @Override
        public MenuType<?> ticketVendorMenu() {
            return MenuInit.TICKET_VENDOR_MENU.get();
        }

        @Override
        public MenuType<?> standardTrackPavingMenu() {
            return MenuInit.STANDARD_TRACK_PAVING_MENU.get();
        }
    };

    @Override
    public MenuInitOperations menuInit() { return menuInit; }

    private static final PanelTextMenuOperations panelTextMenu = new PanelTextMenuOperations() {
        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView moving, int slot) {
            return PanelTextMenu.openMoving(player, moving, slot);
        }

        @Override
        public void open(ServerPlayer player, BlockEntityViews.CRH2PartEntityView panel, int slot, InteractionHand hand) {
            PanelTextMenu.open(player, panel, slot, hand);
        }
    };

    @Override
    public PanelTextMenuOperations panelTextMenu() { return panelTextMenu; }

    private static final SS7DNumberMenuOperations ss7DNumberMenu = new SS7DNumberMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, FriendlyByteBuf buf) {
            return new SS7DNumberMenu(id, inventory, buf);
        }

        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target) {
            return SS7DNumberMenu.openMoving(player, target);
        }

        @Override
        public void openWorld(ServerPlayer player, BlockEntityViews.SS7DHeadEntityView head) {
            SS7DNumberMenu.openWorld(player, head);
        }
    };

    @Override
    public SS7DNumberMenuOperations ss7DNumberMenu() { return ss7DNumberMenu; }

    private static final StandardTrackPavingMenuOperations standardTrackPavingMenu = new StandardTrackPavingMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, InteractionHand hand) {
            return new StandardTrackPavingMenu(id, inventory, hand);
        }

        @Override
        public void open(ServerPlayer player, InteractionHand hand) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, viewer) -> new StandardTrackPavingMenu(id, inventory, hand),
                    Component.translatable("gui.hexie.standard_track_paving.title")),
                    buf -> buf.writeEnum(hand));
        }
    };

    @Override
    public StandardTrackPavingMenuOperations standardTrackPavingMenu() { return standardTrackPavingMenu; }

    private static final TrainSpeedSignEditMenuOperations trainSpeedSignEditMenu = new TrainSpeedSignEditMenuOperations() {
        @Override
        public AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
            return new TrainSpeedSignEditMenu(id, inventory, entity, data);
        }

        @Override
        public boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target) {
            return TrainSpeedSignEditMenu.openMoving(player, target);
        }

        @Override
        public void openWorld(ServerPlayer player, BlockEntityViews.TrainSpeedSignEntityView target, BlockPos position) {
            player.openMenu(target, position);
        }
    };

    @Override
    public TrainSpeedSignEditMenuOperations trainSpeedSignEditMenu() { return trainSpeedSignEditMenu; }

    @Override
    public AbstractContainerMenu createTicketVendorMenu(int id, Inventory inventory, BlockEntity entity, ContainerData data) {
        return new TicketVendorMenu(id, inventory, entity, data);
    }
}
