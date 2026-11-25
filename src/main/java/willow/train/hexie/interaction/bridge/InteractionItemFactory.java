package willow.train.hexie.interaction.bridge;

import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.interaction.ItemFactoryBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.interaction.item.editable_panels.Brush;
import willow.train.hexie.interaction.item.editable_panels.CRHLogoItem;
import willow.train.hexie.interaction.item.editable_panels.ColoredBrush;
import willow.train.hexie.interaction.item.editable_panels.GreenBrush;
import willow.train.hexie.interaction.item.editable_panels.LacqueredBoardItem;
import willow.train.hexie.interaction.item.editable_panels.PanelTextItem;
import willow.train.hexie.interaction.item.editable_panels.PassengerDisplayItem;
import willow.train.hexie.interaction.item.editable_panels.StickerItem;
import willow.train.hexie.interaction.item.editable_panels.TrainNoItem;
import willow.train.hexie.interaction.item.editable_panels.TrainPanelItem;
import willow.train.hexie.interaction.item.foods.Food1;
import willow.train.hexie.interaction.item.tickets.CardItem;
import willow.train.hexie.interaction.item.tickets.TicketItem;
import willow.train.hexie.interaction.item.tools.CRH2HeadItem;
import willow.train.hexie.interaction.item.tools.CRH2PartItem;
import willow.train.hexie.interaction.item.tools.CouplerAdapterItem;
import willow.train.hexie.interaction.item.tools.DrivingConsoleItem;
import willow.train.hexie.interaction.item.tools.ElectricFenceWallItem;
import willow.train.hexie.interaction.item.tools.FloorSwitcherItem;
import willow.train.hexie.interaction.item.tools.PartBlockItem;
import willow.train.hexie.interaction.item.tools.RescueCouplerItem;
import willow.train.hexie.interaction.item.tools.ToolTipsItemHelper;
import willow.train.hexie.interaction.item.tools.TooltipAppendableItem;

final class InteractionItemFactory implements ItemFactoryBridge {
    @Override
    public Item createColoredBrush(Item.Properties pProperties) {
        return new ColoredBrush(pProperties);
    }

    @Override
    public BlockItem createPartBlockItem(Block block, Item.Properties properties) {
        return new PartBlockItem(block, properties);
    }

    @Override
    public Item createCouplerAdapterItem(Item.Properties properties, String firstModel, String secondModel,
            double thickness) {
        return new CouplerAdapterItem(properties, firstModel, secondModel, thickness);
    }

    @Override
    public Item createRescueCouplerItem(Item.Properties properties) {
        return new RescueCouplerItem(properties);
    }

    @Override
    public Item createFloorSwitcherItem(Item.Properties properties) {
        return new FloorSwitcherItem(properties);
    }

    @Override
    public BlockItem createPassengerDisplayItem(Block block, Item.Properties properties, boolean number) {
        return new PassengerDisplayItem(block, properties, number);
    }

    @Override
    public Item createTicketItem(Item.Properties settings) {
        return new TicketItem(settings);
    }

    @Override
    public Item createCardItem(Item.Properties pProperties) {
        return new CardItem(pProperties);
    }

    @Override
    public Item createPanelTextItem(Item.Properties properties) {
        return new PanelTextItem(properties);
    }

    @Override
    public BlockItem createCRH2HeadItem(Block b, Item.Properties p) {
        return new CRH2HeadItem(b, p);
    }

    @Override
    public BlockItem createCRH2PartItem(Block block, Item.Properties properties) {
        return new CRH2PartItem(block, properties);
    }

    @Override
    public BlockItem createDrivingConsoleItem(Block block, Item.Properties properties,
            Supplier<? extends CarriageBlockBridge.CRH2DriverSeatBlockView> seat) {
        return new DrivingConsoleItem(block, properties, seat);
    }

    @Override
    public BlockItem createTrainPanelItem(Block pBlock, Item.Properties pProperties) {
        return new TrainPanelItem(pBlock, pProperties);
    }

    @Override
    public BlockItem createTrainNoItem(Block pBlock, Item.Properties pProperties) {
        return new TrainNoItem(pBlock, pProperties);
    }

    @Override
    public BlockItem createElectricFenceWallItem(Block pBlock, Item.Properties pProperties) {
        return new ElectricFenceWallItem(pBlock, pProperties);
    }

    @Override
    public BlockItem createToolTipsItemHelper(Block pBlock, Item.Properties pProperties) {
        return new ToolTipsItemHelper(pBlock, pProperties);
    }

    @Override
    public BlockItem createTooltipAppendableItem(Block pBlock, Item.Properties pProperties, String... tooltipKeys) {
        return new TooltipAppendableItem(pBlock, pProperties, tooltipKeys);
    }

    @Override
    public Item createFood1(Item.Properties properties) {
        return new Food1(properties);
    }

    @Override
    public Item createBrush(Item.Properties pProperties) {
        return new Brush(pProperties);
    }

    @Override
    public Item createGreenBrush(Item.Properties pProperties) {
        return new GreenBrush(pProperties);
    }

    @Override
    public Item createLacqueredBoardItem(Item.Properties pProperties) {
        return new LacqueredBoardItem(pProperties);
    }

    @Override
    public Item createStickerItem(Item.Properties pProperties) {
        return new StickerItem(pProperties);
    }

    @Override
    public Item createCustomStickerItem(Item.Properties pProperties) {
        return new willow.train.hexie.interaction.item.editable_panels.CustomStickerItem(pProperties);
    }

    @Override
    public Item createCRHLogoItem(Item.Properties properties) {
        return new CRHLogoItem(properties);
    }

}
