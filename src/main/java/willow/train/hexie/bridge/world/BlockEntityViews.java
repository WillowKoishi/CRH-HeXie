package willow.train.hexie.bridge.world;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.core.api.cab.CabControlsHost;
import willow.train.hexie.core.api.cab.CabDisplayHost;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.CabSurfaceInstance;
import willow.train.hexie.core.api.cab.CabTimetableHost;
import willow.train.hexie.core.api.display.PassengerDisplayHost;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.api.interaction.HexieClipboardCloneable;
import willow.train.hexie.core.api.interaction.HexieTransformableBlockEntity;

/** Interfaces implemented by the live block entities. Accessors observe and mutate that same instance. */
public interface BlockEntityViews {
    interface CR200jFrontBlockEntityView {
        float counterFairing();
        void counterFairing(float value);
        float counterWindShield();
        void counterWindShield(float value);
        boolean negative_direction();
        void negative_direction(boolean value);
        boolean isFairingOpen();
        boolean isWindShieldActive();
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface CRH1HeadEntityView extends CarriageViewBlockEntityView {
        boolean isCarriageView();
        float animationProgress(float partialTick);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface CRH2HeadEntityView extends MenuProvider, HexieTransformableBlockEntity {
        String number();
        boolean bogeyEnabled();
        CompoundTag bogeyData();
        void markCarriageView();
        boolean isCarriageView();
        CompoundTag physicalBogeyData();
        void updatePhysicalBogeyData(CompoundTag data);
        void updateBogey(boolean enabled, CompoundTag data);
        void updateNumber(String text);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface CRH2PartEntityView extends MenuProvider, PartBlockBridge.VehicleMarkingsView, PassengerDisplayHost {
        int markingSlot();
        PartBlockBridge.PanelTextView panelText(int slot);
        void setPanelText(int slot, PartBlockBridge.PanelTextView text);
        int harmony(int slot);
        String[] fields();
        boolean descriptionLeft();
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface CarriageNoSignEntityView extends MenuProvider, HexieClipboardCloneable {
        FormattedCharSequence getRenderMessages(Function<Component, FormattedCharSequence> pMessageTransformer);
        void setLeftSide(boolean leftSide);
        boolean isLeftSide();
        int getColor();
        boolean setColor(int mark);
        void setMessage(Component message);
        Component getMessage();
        void markUpdated(boolean isClientSide);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface CarriageTypeSignEntityView extends MenuProvider, HexieClipboardCloneable {
        boolean setMessages(String[] messages);
        Component getMessage(int pIndex, boolean pFiltered);
        FormattedCharSequence[] getRenderMessages(Function<Component, FormattedCharSequence> pMessageTransformer);
        int getColor();
        boolean setColor(int mark);
        void markUpdated(boolean isClientSide);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface CouplerBlockEntityView extends BlockEntityAnimationView {
        default boolean uncouplePhysical() { return false; }
        void tickCoverAnimation();
        float coverProgress(float partialTick);
        CouplingBridge.CouplerAdapterView adapter();
        void updatePhysicalCouplingAnimation(boolean coupled);
        float rescueKnuckleProgress(float partialTick);
        void updateRescueAnimationTarget(CouplingBridge.CouplerAdapterView mounted, String baseModel,
                boolean coupled);
        boolean install(CouplingBridge.CouplerAdapterView value);
        CouplingBridge.CouplerAdapterView removeOuter();
        void saveAdditional(CompoundTag tag, HolderLookup.Provider registries);
        void loadAdditional(CompoundTag tag, HolderLookup.Provider registries);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface CustomRenderedDoorEntityView extends IContraptionMovementBlockEntityView, BlockEntityAnimationView {
        boolean hasReverseSlide();
        void setReverseSlide(boolean reverse);
        boolean hasPassengerFrame();
        void togglePassengerFrame();
        float crh2Progress(float partial);
        float crh2InteractionProgress();
        void setCarriageAnimation(boolean open, float previous, float current);
        boolean isLeftSide(BlockState state);
        boolean isLowerSide(BlockState state);
        boolean isSlideDoor();
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface DrivingConsoleEntityView extends CabDisplayHost, CabControlsHost, CabTimetableHost, CabSurfaceInstance, ControlSurface.AnimationSource {
        CabState controls();
        ControlState surfaceState();
        void benchKeyboard(Player driver, boolean toggleSimple, int mask);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface ElectricFenceDoorEntityView {
        float x_offset();
        void x_offset(float value);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface GroundStationSignEntityView extends MenuProvider {
        int fontStyle();
        void cycleFont();
        String[] fields();
        void update(String[] values);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface LacqueredBoardEntityView extends MenuProvider, HexieClipboardCloneable {
        double getXOffset();
        void setXOffset(double offset);
        void setMessages(String[] messages);
        void setColors(int... colors);
        Component getMessage(int index);
        int getBackGroundColor();
        int getForGroundColor();
        int getBeltForGroundColor();
        FormattedCharSequence[] getRenderMessages(Function<Component, FormattedCharSequence> pMessageTransformer,
                int messageCount);
        int getBoardType();
        void markUpdated(boolean isClientSide);
        int[] getRGBAColor(int octColor);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface MegaphoneBlockEntityView {
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface OBJRenderableEndFaceEntityView extends IContraptionMovementBlockEntityView, BlockEntityAnimationView {
        float progress(float partial);
        boolean isRotateDoor();
        void isRotateDoor(boolean value);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface PassengerPanelEntityView extends PassengerDisplayHost {
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface TicketVendorEntityView extends MenuProvider {
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface TrainSpeedSignEntityView extends MenuProvider, HexieClipboardCloneable {
        FormattedCharSequence getRenderMessages(Function<Component, FormattedCharSequence> pMessageTransformer);
        void markUpdated(boolean isClientSide);
        Component getContent();
        void setContent(Component content);
        int getColor();
        void setColor(int color);
        boolean isX_revert();
        boolean isY_revert();
        void setX_revert(boolean x_revert);
        void setY_revert(boolean y_revert);
        void setX_offset(float x_offset);
        float getX_offset();
        void setY_offset(float y_offset);
        boolean revert_x();
        boolean revert_y();
        float getY_offset();
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface TurnstileBlockEntityView {
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface TurnstileCRBlockEntityView {
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface BlockEntityAnimationView {
        void restoreAnimation(BlockEntity previous);
    }

    interface CarriageViewBlockEntityView {
        void markCarriageView();
        boolean isCarriageView();
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface IContraptionMovementBlockEntityView {
        void doMovement(BlockPos blockPos, BlockState blockState, BlockEntity blockEntity);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

    interface HexieBogeyBlockEntityView extends CarriageViewBlockEntityView {
        void syncBogeyData(CompoundTag data);
    }

    interface SS7DHeadEntityView extends CarriageViewBlockEntityView {
        boolean isCarriageView();
        String digits();
        default String bureau() {
            return asBlockEntity().saveCustomOnly(net.minecraft.core.RegistryAccess.EMPTY)
                    .getString(WorldBlockBridge.SS7DHeadEntityOperations.BUREAU_TAG);
        }
        default String depot() {
            return asBlockEntity().saveCustomOnly(net.minecraft.core.RegistryAccess.EMPTY)
                    .getString(WorldBlockBridge.SS7DHeadEntityOperations.DEPOT_TAG);
        }
        default boolean secondEnd() { return "II".equals(endLabel()); }
        boolean depotVisible();
        String endLabel();
        String depotLabel();
        boolean hasSidePlates();
        void updateDigits(String value);
        void updateSecondEnd(boolean value);
        void updateDepot(String bureau, String depot, boolean visible);
        float wiperAngle(int side, float partialTick);
        default BlockEntity asBlockEntity() { return (BlockEntity) this; }
    }

}
