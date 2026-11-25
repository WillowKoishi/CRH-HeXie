package willow.train.hexie.bridge.interaction;

import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.world.CarriageBlockBridge;

/** Constructs the original item for each registry supplier, with its supplied properties. */
public interface ItemFactoryBridge {
    Item createColoredBrush(Item.Properties pProperties);

    BlockItem createPartBlockItem(Block block, Item.Properties properties);

    Item createCouplerAdapterItem(Item.Properties properties, String firstModel, String secondModel,
            double thickness);

    Item createRescueCouplerItem(Item.Properties properties);

    Item createFloorSwitcherItem(Item.Properties properties);

    BlockItem createPassengerDisplayItem(Block block, Item.Properties properties, boolean number);

    Item createTicketItem(Item.Properties settings);

    Item createCardItem(Item.Properties pProperties);

    Item createPanelTextItem(Item.Properties properties);


    BlockItem createCRH2HeadItem(Block b, Item.Properties p);

    BlockItem createCRH2PartItem(Block block, Item.Properties properties);

    BlockItem createDrivingConsoleItem(Block block, Item.Properties properties,
            Supplier<? extends CarriageBlockBridge.CRH2DriverSeatBlockView> seat);

    BlockItem createTrainPanelItem(Block pBlock, Item.Properties pProperties);

    BlockItem createTrainNoItem(Block pBlock, Item.Properties pProperties);

    BlockItem createElectricFenceWallItem(Block pBlock, Item.Properties pProperties);

    BlockItem createToolTipsItemHelper(Block pBlock, Item.Properties pProperties);

    BlockItem createTooltipAppendableItem(Block pBlock, Item.Properties pProperties, String... tooltipKeys);

    Item createFood1(Item.Properties properties);

    Item createBrush(Item.Properties pProperties);

    Item createGreenBrush(Item.Properties pProperties);

    Item createLacqueredBoardItem(Item.Properties pProperties);

    Item createStickerItem(Item.Properties pProperties);

    Item createCustomStickerItem(Item.Properties pProperties);

    Item createCRHLogoItem(Item.Properties properties);

}
