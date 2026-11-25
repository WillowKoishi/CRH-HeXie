package willow.train.hexie.bridge.interaction;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.WorldBlockBridge;

public interface ToolOpsBridge {
    CarriagePartCatalogOperations carriagePartCatalog();

    interface CarriagePartCatalogOperations {
        String[] creativeNames();
        List<ItemStack> palette(String name);
    }

    ColoredBrushOperations coloredBrush();

    interface ColoredBrushOperations {
        ItemStack onEffectiveUse(ItemStack stack);
    }

    CouplerAdapterItemOperations couplerAdapterItem();

    interface CouplerAdapterItemOperations {
        ItemStack stack(CouplingBridge.CouplerAdapterView adapter);
    }

    FloorSwitcherItemOperations floorSwitcherItem();

    interface FloorSwitcherItemOperations {
        FloorSwitcherItemMode mode(ItemStack stack);
        boolean valid(ItemStack stack, int value);
        void switchMode(Player player, InteractionHand hand);
        int HOLD_TICKS = 10;
        void setHeight(ItemStack stack, int height);
        Component message(int height);
        void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position, int currentHeight);
        void beginUsingDoor(Player player, InteractionHand hand,
                BlockEntityViews.CustomRenderedDoorEntityView door);
        FloorSwitcherItemSliderView configuration(Player player);
        void cancelPending(Player player);
        void beginConfiguration(Player player);
        void confirmConfiguration(Player player, InteractionHand hand, int height);
        boolean confirmDoorDirection(Player player, InteractionHand hand, int value);
    }

    FloorSwitcherItemSliderView createFloorSwitcherItemSlider(FloorSwitcherItemMode mode, int value);

    RescueCouplerItemOperations rescueCouplerItem();

    interface RescueCouplerItemOperations {
        String ID = "hexie:crh2a_rescue_coupler";
        String KNUCKLE_MODEL = "railways:knuckle";
        WorldBlockBridge.CouplerGeometryView geometry();
    }

    default StandardTrackPavingPresetOperations standardTrackPavingPreset() {
        return HexieBridges.createTrack().pavingPreset();
    }

    interface StandardTrackPavingPresetOperations {
        boolean isStandardTrack(ItemStack stack);
        ItemStack getBlockStack(ItemStack trackStack);
        boolean isEligibleItem(ItemStack stack);
        void set(ItemStack trackStack, ItemStack selectedBlock);
    }

    interface CouplerAdapterItemView {
        CouplingBridge.CouplerAdapterView adapter(ItemStack stack);
        default Item asItem() { return (Item) this; }
    }

    interface FloorSwitcherItemView {
        default Item asItem() { return (Item) this; }
    }

    enum FloorSwitcherItemMode {
        HEIGHT, LONGITUDINAL, DOOR_DIRECTION, REVERSE
    }

    interface FloorSwitcherItemSliderView {
        FloorSwitcherItemMode mode();
        int value();
    }

    interface PanelTextItemView {
        default Item asItem() { return (Item) this; }
    }

    interface PartBlockItemView {
        InteractionResult placeAtRoot(BlockPlaceContext context, BlockPos root, BlockState state);
        boolean canFit(BlockPlaceContext context, BlockPos root, BlockState state);
        default Item asItem() { return (Item) this; }
    }

    interface PassengerDisplayItemView {
        boolean number();
        default Item asItem() { return (Item) this; }
    }

    interface RescueCouplerItemView extends CouplerAdapterItemView {
        default Item asItem() { return (Item) this; }
    }

    interface ColoredBrushView {
        default Item asItem() { return (Item) this; }
    }

    interface ThroughSignalItemView {
        default Item asItem() { return (Item) this; }
    }

    interface CardItemView extends TicketableView {
        default Item asItem() { return (Item) this; }
    }

    interface TicketItemView extends TicketableView {
        default Item asItem() { return (Item) this; }
    }

    interface TicketableView {}
}
