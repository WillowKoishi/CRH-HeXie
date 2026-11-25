package willow.train.hexie.world.bridge;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.world.blocks.signs.GroundStationSignBlock;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.blocks.signs.TrainSpeedSignBlock;

final class SignBlockPorts implements SignBlockBridge {
    private static final GroundStationSignBlockOperations GROUND_STATION_SIGN_BLOCK = () -> GroundStationSignBlock.FACING;
    @Override public GroundStationSignBlockOperations groundStationSignBlock() { return GROUND_STATION_SIGN_BLOCK; }

    private static final PanelDisplayColumnOperations PANEL_DISPLAY_COLUMN = new PanelDisplayColumnOperations() {
        @Override public EnumProperty<PanelDisplayColumn> property() {
            return willow.train.hexie.world.blocks.signs.PanelDisplayColumn.PROPERTY;
        }
        @Override public int offset(PanelDisplayColumn value) {
            return willow.train.hexie.world.blocks.signs.PanelDisplayColumn.valueOf(value.name()).offset();
        }
        @Override public PanelDisplayColumn get(BlockState state) {
            return willow.train.hexie.world.blocks.signs.PanelDisplayColumn.get(state).stored();
        }
        @Override public boolean isValid(BlockState state) {
            return willow.train.hexie.world.blocks.signs.PanelDisplayColumn.isValid(state);
        }
    };
    @Override public PanelDisplayColumnOperations panelDisplayColumn() { return PANEL_DISPLAY_COLUMN; }

    private static final PanelDisplayMountOperations PANEL_DISPLAY_MOUNT = new PanelDisplayMountOperations() {
        @Override public EnumProperty<PanelDisplayMount> property() {
            return willow.train.hexie.world.blocks.signs.PanelDisplayMount.PROPERTY;
        }
        @Override public boolean hasNumber(PanelDisplayMount value) {
            return willow.train.hexie.world.blocks.signs.PanelDisplayMount.valueOf(value.name()).hasNumber();
        }
        @Override public boolean hasSign(PanelDisplayMount value) {
            return willow.train.hexie.world.blocks.signs.PanelDisplayMount.valueOf(value.name()).hasSign();
        }
        @Override public boolean numberLeft(PanelDisplayMount value) {
            return willow.train.hexie.world.blocks.signs.PanelDisplayMount.valueOf(value.name()).numberLeft();
        }
    };
    @Override public PanelDisplayMountOperations panelDisplayMount() { return PANEL_DISPLAY_MOUNT; }

    private static final PassengerDisplayPanelOperations PASSENGER_DISPLAY_PANEL = PassengerDisplayPanel::supports;
    @Override public PassengerDisplayPanelOperations passengerDisplayPanel() { return PASSENGER_DISPLAY_PANEL; }

    private static final TrainSpeedSignBlockOperations TRAIN_SPEED_SIGN_BLOCK = () -> TrainSpeedSignBlock.TYPE;
    @Override public TrainSpeedSignBlockOperations trainSpeedSignBlock() { return TRAIN_SPEED_SIGN_BLOCK; }
}
