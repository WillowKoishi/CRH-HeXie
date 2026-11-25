package willow.train.hexie.interaction.bridge;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.interaction.item.editable_panels.ColoredBrush;
import willow.train.hexie.interaction.item.tools.CarriagePartCatalog;
import willow.train.hexie.interaction.item.tools.CouplerAdapterItem;
import willow.train.hexie.interaction.item.tools.FloorSwitcherItem;
import willow.train.hexie.interaction.item.tools.RescueCouplerItem;

final class InteractionToolOps implements ToolOpsBridge,
        ToolOpsBridge.CarriagePartCatalogOperations,
        ToolOpsBridge.ColoredBrushOperations,
        ToolOpsBridge.CouplerAdapterItemOperations,
        ToolOpsBridge.FloorSwitcherItemOperations,
        ToolOpsBridge.RescueCouplerItemOperations {
    @Override
    public CarriagePartCatalogOperations carriagePartCatalog() {
        return this;
    }

    @Override
    public ColoredBrushOperations coloredBrush() {
        return this;
    }

    @Override
    public CouplerAdapterItemOperations couplerAdapterItem() {
        return this;
    }

    @Override
    public FloorSwitcherItemOperations floorSwitcherItem() {
        return this;
    }

    @Override
    public FloorSwitcherItemSliderView createFloorSwitcherItemSlider(FloorSwitcherItemMode mode, int value) {
        return new FloorSwitcherItem.Slider(mode, value);
    }

    @Override
    public RescueCouplerItemOperations rescueCouplerItem() {
        return this;
    }

    @Override
    public String[] creativeNames() {
        return CarriagePartCatalog.creativeNames();
    }

    @Override
    public List<ItemStack> palette(String name) {
        return CarriagePartCatalog.palette(name);
    }

    @Override
    public ItemStack onEffectiveUse(ItemStack stack) {
        return ColoredBrush.onEffectiveUse(stack);
    }

    @Override
    public ItemStack stack(CouplingBridge.CouplerAdapterView adapter) {
        return CouplerAdapterItem.stack(adapter);
    }

    @Override
    public FloorSwitcherItemMode mode(ItemStack stack) {
        return FloorSwitcherItem.mode(stack);
    }

    @Override
    public boolean valid(ItemStack stack, int value) {
        return FloorSwitcherItem.valid(stack, value);
    }

    @Override
    public void switchMode(Player player, InteractionHand hand) {
        FloorSwitcherItem.switchMode(player, hand);
    }

    @Override
    public void setHeight(ItemStack stack, int height) {
        FloorSwitcherItem.setHeight(stack, height);
    }

    @Override
    public Component message(int height) {
        return FloorSwitcherItem.message(height);
    }

    @Override
    public void beginUsing(Player player, InteractionHand hand, UUID carriage, BlockPos position, int currentHeight) {
        FloorSwitcherItem.beginUsing(player, hand, carriage, position, currentHeight);
    }

    @Override
    public void beginUsingDoor(Player player, InteractionHand hand,
                BlockEntityViews.CustomRenderedDoorEntityView door) {
        FloorSwitcherItem.beginUsingDoor(player, hand, door);
    }

    @Override
    public FloorSwitcherItemSliderView configuration(Player player) {
        return FloorSwitcherItem.configuration(player);
    }

    @Override
    public void cancelPending(Player player) {
        FloorSwitcherItem.cancelPending(player);
    }

    @Override
    public void beginConfiguration(Player player) {
        FloorSwitcherItem.beginConfiguration(player);
    }

    @Override
    public void confirmConfiguration(Player player, InteractionHand hand, int height) {
        FloorSwitcherItem.confirmConfiguration(player, hand, height);
    }

    @Override
    public boolean confirmDoorDirection(Player player, InteractionHand hand, int value) {
        return FloorSwitcherItem.confirmDoorDirection(player, hand, value);
    }

    @Override
    public WorldBlockBridge.CouplerGeometryView geometry() {
        return RescueCouplerItem.geometry();
    }
}
