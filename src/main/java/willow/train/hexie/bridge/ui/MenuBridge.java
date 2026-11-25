package willow.train.hexie.bridge.ui;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.PartBlockBridge;

/** Menu providers and registered types shared by world, interaction and network code. */
public interface MenuBridge {
    void registerBogeySelection(BogeySelectionMenuOperations menu, Supplier<MenuType<?>> type);
    BogeySelectionMenuOperations bogeySelectionMenu();

    interface BogeySelectionMenuOperations {
        boolean open(ServerPlayer player, BlockEntityViews.HexieBogeyBlockEntityView target, InteractionHand hand);
    }

    CarriageMarkingMenuOperations carriageMarkingMenu();

    interface CarriageMarkingMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, BlockEntity target);
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView moving);
    }

    CarriageNoSignEditMenuOperations carriageNoSignEditMenu();

    interface CarriageNoSignEditMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data);
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target);
        void openWorld(ServerPlayer player, BlockEntityViews.CarriageNoSignEntityView target, BlockPos position);
    }

    CarriageNumberMenuOperations carriageNumberMenu();

    interface CarriageNumberMenuOperations {
        boolean open(ServerPlayer player, CarriageNumberMenuTargetView target);
        boolean openStatic(ServerPlayer player, BlockEntity blockEntity);
    }

    CarriageTypeSignEditMenuOperations carriageTypeSignEditMenu();

    interface CarriageTypeSignEditMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data);
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target);
        void openWorld(ServerPlayer player, BlockEntityViews.CarriageTypeSignEntityView target, BlockPos position);
    }

    GroundStationSignMenuOperations groundStationSignMenu();

    interface GroundStationSignMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, BlockPos pos, String[] fields);
        void openWorld(ServerPlayer player, BlockEntityViews.GroundStationSignEntityView target, BlockPos position);
    }

    HeadNumberMenuOperations headNumberMenu();

    interface HeadNumberMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, BlockEntityViews.CRH2HeadEntityView target);
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView moving);
        void openWorld(ServerPlayer player, BlockEntityViews.CRH2HeadEntityView target, BlockPos position);
    }

    LacqueredBoardEditMenuOperations lacqueredBoardEditMenu();

    interface LacqueredBoardEditMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data);
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target);
        void openWorld(ServerPlayer player, BlockEntityViews.LacqueredBoardEntityView target, BlockPos position);
    }

    MenuInitOperations menuInit();

    interface MenuInitOperations {
        @Nullable MenuType<?> bogeySelectionMenu();
        MenuType<?> carriageNumberMenu();
        MenuType<?> panelTextMenu();
        MenuType<?> carriageMarkingMenu();
        MenuType<?> headNumberMenu();
        MenuType<?> ss7dNumberMenu();
        MenuType<?> groundStationSignMenu();
        MenuType<?> carriageTypeSignEditMenu();
        MenuType<?> carriageNoSignEditMenu();
        MenuType<?> laqueredBoardEditMenu();
        MenuType<?> trainSpeedSignEditMenu();
        MenuType<?> ticketVendorMenu();
        MenuType<?> standardTrackPavingMenu();
    }

    PanelTextMenuOperations panelTextMenu();

    interface PanelTextMenuOperations {
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView moving, int slot);
        void open(ServerPlayer player, BlockEntityViews.CRH2PartEntityView panel, int slot, InteractionHand hand);
    }

    SS7DNumberMenuOperations ss7DNumberMenu();

    interface SS7DNumberMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, FriendlyByteBuf buf);
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target);
        void openWorld(ServerPlayer player, BlockEntityViews.SS7DHeadEntityView head);
    }

    StandardTrackPavingMenuOperations standardTrackPavingMenu();

    interface StandardTrackPavingMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, InteractionHand hand);
        void open(ServerPlayer player, InteractionHand hand);
    }

    AbstractContainerMenu createTicketVendorMenu(int pContainerId, Inventory inv, BlockEntity entity,
            ContainerData data);

    TrainSpeedSignEditMenuOperations trainSpeedSignEditMenu();

    interface TrainSpeedSignEditMenuOperations {
        AbstractContainerMenu create(int id, Inventory inventory, BlockEntity entity, ContainerData data);
        boolean openMoving(ServerPlayer player, BlockEntityEditMenuTargetView target);
        void openWorld(ServerPlayer player, BlockEntityViews.TrainSpeedSignEntityView target, BlockPos position);
    }

    interface SS7DNumberMenuView extends BlockEntityEditMenuView {
        String initialDigits();
        String initialBureau();
        String initialDepot();
        boolean initialDepotVisible();
        boolean initialSecondEnd();
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface BlockEntityEditMenuView {
        BlockEntity editTarget();
        boolean contentOnly();
        void attachTarget(BlockEntityEditMenuTargetView moving);
        boolean commitEdit(Player player);
        boolean stillValid(Player player);
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface BlockEntityEditMenuTargetView {
        BlockEntity entity();
        boolean stillValid(Player player);
        boolean commit(Player player);
    }

    interface CarriageMarkingMenuView extends BlockEntityEditMenuView {
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface CarriageNoSignEditMenuView extends BlockEntityEditMenuView {
        void setCtse(BlockEntityViews.CarriageNoSignEntityView entity);
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface CarriageNumberMenuView {
        boolean submit(Player player, String number);
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface CarriageNumberMenuTargetView {
        String number();
        boolean stillValid(Player player);
        void setNumber(String number);
    }

    interface CarriageTypeSignEditMenuView extends BlockEntityEditMenuView {
        CarriageTypeSignEditMenuView setCtse(BlockEntityViews.CarriageTypeSignEntityView entity);
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface LacqueredBoardEditMenuView extends BlockEntityEditMenuView {
        void setLbe(BlockEntityViews.LacqueredBoardEntityView entity);
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface PanelTextMenuView extends BlockEntityEditMenuView {
        void submit(Player player, PartBlockBridge.PanelTextView value);
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }

    interface TrainSpeedSignEditMenuView extends BlockEntityEditMenuView {
        void setTsse(BlockEntityViews.TrainSpeedSignEntityView entity);
        default AbstractContainerMenu asMenu() { return (AbstractContainerMenu) this; }
    }
}
