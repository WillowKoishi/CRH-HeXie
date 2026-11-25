package willow.train.hexie.bridge.world;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.HexieItemRequirementProvider;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.core.util.enums.SlabTypes;

public interface SignBlockBridge {
    GroundStationSignBlockOperations groundStationSignBlock();

    interface GroundStationSignBlockOperations {
        DirectionProperty facing();
    }

    PanelDisplayColumnOperations panelDisplayColumn();

    interface PanelDisplayColumnOperations {
        EnumProperty<PanelDisplayColumn> property();
        int offset(PanelDisplayColumn value);
        PanelDisplayColumn get(BlockState state);
        boolean isValid(BlockState state);
    }

    PanelDisplayMountOperations panelDisplayMount();

    interface PanelDisplayMountOperations {
        EnumProperty<PanelDisplayMount> property();
        boolean hasNumber(PanelDisplayMount value);
        boolean hasSign(PanelDisplayMount value);
        boolean numberLeft(PanelDisplayMount value);
    }

    PassengerDisplayPanelOperations passengerDisplayPanel();

    interface PassengerDisplayPanelOperations {
        boolean supports(Block block);
    }

    TrainSpeedSignBlockOperations trainSpeedSignBlock();

    interface TrainSpeedSignBlockOperations {
        EnumProperty<SlabTypes> type();
    }

    interface GroundStationSignBlockView {
        default Block asBlock() { return (Block) this; }
    }

    enum PanelDisplayColumn implements StringRepresentable {
        ROOT("root"), NEGATIVE("negative"), POSITIVE("positive");

        private final String serializedName;
        PanelDisplayColumn(String serializedName) { this.serializedName = serializedName; }
        @Override public String getSerializedName() { return serializedName; }
    }

    enum PanelDisplayMount implements StringRepresentable {
        NONE("none"), NUMBER_LEFT("number_left"), NUMBER_RIGHT("number_right"), SIGN_LEFT("sign_left"),
                SIGN_RIGHT("sign_right"), NUMBER_LEFT_SIGN_RIGHT("number_left_sign_right"),
                SIGN_LEFT_NUMBER_RIGHT("sign_left_number_right");

        private final String serializedName;
        PanelDisplayMount(String serializedName) { this.serializedName = serializedName; }
        @Override public String getSerializedName() { return serializedName; }
    }

    interface PassengerDisplayPanelView extends EntityBlock, BlockControlHost, HexieItemRequirementProvider {}

    interface TrainNoBlockView extends WorldBlockBridge.SignBlockBaseView, HexieWrenchable {
        default Block asBlock() { return (Block) this; }
    }

    interface TrainPanelBlockView extends WorldBlockBridge.SignBlockBaseView, HexieWrenchable {
        default Block asBlock() { return (Block) this; }
    }
}
