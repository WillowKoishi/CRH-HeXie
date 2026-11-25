package willow.train.hexie.bridge.world;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.bridge.ui.MenuBridge;

/** Returns the registered objects. A lookup belongs in the same registry phase as the original supplier. */
public interface WorldRegistryBridge {
    AllItemsInitOperations allItemsInit();

    interface AllItemsInitOperations {
        Item harmonyWrench();
    }

    BlockEntityType<? extends BlockEntity> crh2aHeadBlockEntity();

    BlockEntitiesInitOperations blockEntitiesInit();

    interface BlockEntitiesInitOperations {
        BlockEntityType<? extends BlockEntity> passengerPanel();
        BlockEntityType<? extends BlockEntity> crh1aHead();
        BlockEntityType<? extends BlockEntity> ss7dHead();
        BlockEntityType<? extends BlockEntity> crh2aHead();
        BlockEntityType<? extends BlockEntity> crh2aPart();
        BlockEntityType<? extends BlockEntity> drivingConsole();
        BlockEntityType<? extends BlockEntity> coupler();
        BlockEntityType<? extends BlockEntity> groundStationSign();
        BlockEntityType<? extends BlockEntity> carriageTypeSign();
        BlockEntityType<? extends BlockEntity> carriageNoSign();
        BlockEntityType<? extends BlockEntity> trainSpeedSign();
        BlockEntityType<? extends BlockEntity> laqueredBoard();
        BlockEntityType<? extends BlockEntity> electricFenceDoor();
        BlockEntityType<? extends BlockEntity> objRenderableEndFaceBlockEntity();
        BlockEntityType<? extends BlockEntity> cr200jFrontBlockEntity();
        BlockEntityType<? extends BlockEntity> customRenderedDoorEntity();
    }

    BlockInitOperations blockInit();

    interface BlockInitOperations {
        Block ss7dHead();
        Block ss7dDoor();
        Block ss7dFloor();
        Block ss7dCenterCarport();
        Block ss7dSideCarport();
        Block crh2aEndFace();
        Block crh1aEndFace();
        Block scharfenbergCoupler();
        Block crh2aAutoCoupler();
        Block generalJanneyCoupler();
        Block crh2aCarriageNumberDisplay();
        Block crh1aHead();
        Block groundStationSign();
        Block crLogo();
        Block panel25bOriginalBottom();
        Block panel25gOriginalBottom();
        Block panel25zOriginalBottomSide();
        Block panel25kOriginalBottom();
        Block panel25bMarshalledSymbol();
        Block pantograph();
        Block panelCr200jMarshalledBottom();
    }

    EditablePanelItemInitOperations editablePanelItemInit();

    interface EditablePanelItemInitOperations {
        Item brush();
        Item coloredBrush();
    }

    EntityInitOperations entityInit();

    interface EntityInitOperations {
        EntityType<?> assembledCarriage();
        EntityType<?> yzSeatEntity();
    }

    LogoItemInitOperations logoItemInit();

    interface LogoItemInitOperations {
        Item panelText();
        Item logoA25t();
    }

    MenuBridge.SS7DNumberMenuOperations ss7dNumberEditor();
}
