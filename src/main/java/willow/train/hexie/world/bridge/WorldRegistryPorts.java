package willow.train.hexie.world.bridge;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.ui.MenuBridge;
import willow.train.hexie.bridge.world.WorldRegistryBridge;
import willow.train.hexie.world.registry.blocks.BlockInit;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;
import willow.train.hexie.world.registry.core.EntityInit;
import willow.train.hexie.world.registry.items.AllItemsInit;
import willow.train.hexie.world.registry.items.EditablePanelItemInit;
import willow.train.hexie.world.registry.items.LogoItemInit;

final class WorldRegistryPorts implements WorldRegistryBridge {
    private static final AllItemsInitOperations ALL_ITEMS_INIT = () -> AllItemsInit.HARMONY_WRENCH.get();
    @Override public AllItemsInitOperations allItemsInit() { return ALL_ITEMS_INIT; }

    @Override public BlockEntityType<? extends BlockEntity> crh2aHeadBlockEntity() {
        return blockEntitiesInit().crh2aHead();
    }

    private static final BlockEntitiesInitOperations BLOCK_ENTITIES_INIT = new BlockEntitiesInitOperations() {
        @Override public BlockEntityType<? extends BlockEntity> passengerPanel() {
            return BlockEntitiesInit.PASSENGER_PANEL.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> crh1aHead() {
            return BlockEntitiesInit.CRH1A_HEAD.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> ss7dHead() {
            return BlockEntitiesInit.SS7D_HEAD.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> crh2aHead() {
            return BlockEntitiesInit.CRH2A_HEAD.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> crh2aPart() {
            return BlockEntitiesInit.CRH2A_PART.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> drivingConsole() {
            return BlockEntitiesInit.DRIVING_CONSOLE.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> coupler() {
            return BlockEntitiesInit.COUPLER.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> groundStationSign() {
            return BlockEntitiesInit.GROUND_STATION_SIGN.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> carriageTypeSign() {
            return BlockEntitiesInit.CARRIAGE_TYPE_SIGN.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> carriageNoSign() {
            return BlockEntitiesInit.CARRIAGE_NO_SIGN.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> trainSpeedSign() {
            return BlockEntitiesInit.TRAIN_SPEED_SIGN.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> laqueredBoard() {
            return BlockEntitiesInit.LAQUERED_BOARD.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> electricFenceDoor() {
            return BlockEntitiesInit.ELECTRIC_FENCE_DOOR.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> objRenderableEndFaceBlockEntity() {
            return BlockEntitiesInit.OBJ_RENDERABLE_END_FACE_BLOCK_ENTITY.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> cr200jFrontBlockEntity() {
            return BlockEntitiesInit.CR200J_FRONT_BLOCK_ENTITY.get();
        }
        @Override public BlockEntityType<? extends BlockEntity> customRenderedDoorEntity() {
            return BlockEntitiesInit.CUSTOM_RENDERED_DOOR_ENTITY.get();
        }
    };
    @Override public BlockEntitiesInitOperations blockEntitiesInit() { return BLOCK_ENTITIES_INIT; }

    private static final BlockInitOperations BLOCK_INIT = new BlockInitOperations() {
        @Override public Block ss7dSideCarport() { return BlockInit.SS7D_CARPORT_SIDE.get(); }
        @Override public Block ss7dCenterCarport() { return BlockInit.SS7D_CARPORT_MID.get(); }
        @Override public Block ss7dFloor() { return BlockInit.SS7D_FLOOR.get(); }
        @Override public Block ss7dDoor() { return BlockInit.SS7D_DOOR.get(); }
        @Override public Block ss7dHead() {
            return BlockInit.SS7D_HEAD.get();
        }
        @Override public Block crh2aEndFace() {
            return BlockInit.CRH2A_END_FACE.get();
        }
        @Override public Block crh1aEndFace() {
            return BlockInit.CRH1A_END_FACE.get();
        }
        @Override public Block scharfenbergCoupler() {
            return BlockInit.SCHARFENBERG_COUPLER.get();
        }
        @Override public Block crh2aAutoCoupler() {
            return BlockInit.CRH2A_AUTO_COUPLER.get();
        }
        @Override public Block generalJanneyCoupler() {
            return BlockInit.GENERAL_JANNEY_COUPLER.get();
        }
        @Override public Block crh2aCarriageNumberDisplay() {
            return BlockInit.CRH2A_CARRIAGE_NUMBER_DISPLAY.get();
        }
        @Override public Block crh1aHead() {
            return BlockInit.CRH1A_HEAD.get();
        }
        @Override public Block groundStationSign() {
            return BlockInit.GROUND_STATION_SIGN.get();
        }
        @Override public Block crLogo() {
            return BlockInit.CR_LOGO.get();
        }
        @Override public Block panel25bOriginalBottom() {
            return BlockInit.PANEL_25B_ORIGINAL_BOTTOM.get();
        }
        @Override public Block panel25gOriginalBottom() {
            return BlockInit.PANEL_25G_ORIGINAL_BOTTOM.get();
        }
        @Override public Block panel25zOriginalBottomSide() {
            return BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_SIDE.get();
        }
        @Override public Block panel25kOriginalBottom() {
            return BlockInit.PANEL_25K_ORIGINAL_BOTTOM.get();
        }
        @Override public Block panel25bMarshalledSymbol() {
            return BlockInit.PANEL_25B_MARSHALLED_SYMBOL.get();
        }
        @Override public Block pantograph() {
            return BlockInit.PANTOGRAPH.get();
        }
        @Override public Block panelCr200jMarshalledBottom() {
            return BlockInit.PANEL_CR200J_MARSHALLED_BOTTOM.get();
        }
    };
    @Override public BlockInitOperations blockInit() { return BLOCK_INIT; }

    private static final EditablePanelItemInitOperations EDITABLE_PANEL_ITEM_INIT = new EditablePanelItemInitOperations() {
        @Override public Item brush() {
            return EditablePanelItemInit.Brush.get();
        }
        @Override public Item coloredBrush() {
            return EditablePanelItemInit.ColoredBrush.get();
        }
    };
    @Override public EditablePanelItemInitOperations editablePanelItemInit() { return EDITABLE_PANEL_ITEM_INIT; }

    private static final EntityInitOperations ENTITY_INIT = new EntityInitOperations() {
        @Override public EntityType<?> assembledCarriage() {
            return EntityInit.assembledCarriage();
        }
        @Override public EntityType<?> yzSeatEntity() {
            return EntityInit.YZ_SEAT_ENTITY.get();
        }
    };
    @Override public EntityInitOperations entityInit() { return ENTITY_INIT; }

    private static final LogoItemInitOperations LOGO_ITEM_INIT = new LogoItemInitOperations() {
        @Override public Item panelText() {
            return LogoItemInit.PANEL_TEXT.get();
        }
        @Override public Item logoA25t() {
            return LogoItemInit.LOGO_A25T.get();
        }
    };
    @Override public LogoItemInitOperations logoItemInit() { return LOGO_ITEM_INIT; }


    @Override public MenuBridge.SS7DNumberMenuOperations ss7dNumberEditor() {
        return HexieBridges.menu().ss7DNumberMenu();
    }
}
