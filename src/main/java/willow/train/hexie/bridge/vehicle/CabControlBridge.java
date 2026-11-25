package willow.train.hexie.bridge.vehicle;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.CabModuleAccess;
import willow.train.hexie.core.api.cab.CabNeutralPositions;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.CtcsProfile;
import willow.train.hexie.core.api.cab.DmiState;
import willow.train.hexie.core.api.cab.TrainControlLimits;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

/** Cab state conversion, desk policies and train control callbacks. */
public interface CabControlBridge extends CabModuleAccess.Provider {
    SimpleCabControlsView simpleCabControls();

    @Override CabModuleView createCabModule();

    AutomaticTrainControlOperations automaticTrainControl();

    interface AutomaticTrainControlOperations {
        AutomaticTrainControlTargetView fromRoute(TrainDisplayReadingsRouteView route, int direction,
                boolean navigating, boolean waypointTransition, double speed, TrainControlLimits limits);
        AutomaticTrainControlTargetView stationApproach(AutomaticTrainControlTargetView target);
        TrainCommand update(TrainControlSource source, double speed, AutomaticTrainControlTargetView target,
                double serviceDeceleration, StationStopControlStateView stationStop);
    }

    CabControlArbitrationCommandView createCabControlArbitrationCommand(boolean enabled, TrainCommand control);

    CabControlArbitrationOperations cabControlArbitration();

    interface CabControlArbitrationOperations {
        CabControlArbitrationSelectionView select(Iterable<CabState> cabs);
        TrainCommand aggregate(Iterable<CabControlArbitrationCommandView> commands, TrainCommand backend);
    }

    CabControllerOperations cabController();

    interface CabControllerOperations {
        TrainCommand manual(CabState.Input input);
        TrainCommand automaticOutput(TrainControlSource source, boolean automatic, boolean wasAutomatic,
                boolean manual, boolean reset, double speed, AutomaticTrainControlTargetView target,
                double serviceDeceleration, StationStopControlStateView stationStop);
    }

    CabModuleOperations cabModule();

    interface CabModuleOperations {
        CabState synchronize(CabTrainControl desk, CabState previous, CabState source, int relativeDirection);
        CabState synchronize(CabControlHost host, BlockState block, CabState previous, CabState source,
                int relativeDirection);
        CabState normalize(CabTrainControl desk, CabState state);
        CabState operateControls(CabControlHost host, BlockState block, String id, int value, CabState previous,
                double speed);
        Set<CabTrainControl.Demand> operatedDemands(CabControlHost host, BlockState block, String id);
        CabModuleView read(CompoundTag tag);
        Map<BlockPos, CabState> readFrames(CompoundTag frame);
    }

    Crh1CabControlsDirectionButtonOperations crh1CabControlsDirectionButton();

    interface Crh1CabControlsDirectionButtonOperations {
        Crh1CabControlsDirectionButtonView instance();
    }

    Crh1CabControlsOperations crh1CabControls();

    interface Crh1CabControlsOperations {
        Crh1CabControlsView instance();
        CabNeutralPositions neutralPositions();
    }

    Crh1CabHandleOperations crh1CabHandle();

    interface Crh1CabHandleOperations {
        Crh1CabHandleView instance();
    }

    Crh2CabControlsOperations crh2CabControls();

    interface Crh2CabControlsOperations {
        Crh2CabControlsView instance();
        CabNeutralPositions neutralPositions();
    }

    Crh2CabHandleOperations crh2CabHandle();

    interface Crh2CabHandleOperations {
        Crh2CabHandleView power();
        Crh2CabHandleView brake();
        Crh2CabHandleView reverser();
    }

    Ctcs2PanelOperations ctcs2Panel();

    interface Ctcs2PanelOperations {
        Ctcs2PanelView instance();
        List<String> softKeyLabels();
    }

    CtcsControlOperations ctcsControl();

    interface CtcsControlOperations {
        List<CtcsProfile.Code> codesAtSignals(List<CtcsControlSectionView> sections);
    }

    CtcsControlSectionView createCtcsControlSection(String id, double distanceM, CtcsControlOccupancy occupancy,
            boolean linked);

    StationStopControlOperations stationStopControl();

    interface StationStopControlOperations {
        StationStopControlView create();
        double ARRIVAL_DISTANCE_M = 10.0;
        boolean stopped(double speedMps);
    }

    StationStopControlStateOperations stationStopControlState();

    interface StationStopControlStateOperations {
        StationStopControlStateView create(boolean waitingForDeparture, String stationId, String stationName);
        StationStopControlStateView running();
    }

    StationStopControlStationObservationView createStationStopControlStationObservation(String stationId,
            String stationName, double distanceM);

    TestCabButtonOperations testCabButton();

    interface TestCabButtonOperations {
        TestCabButtonView powerUp();
        TestCabButtonView powerDown();
        TestCabButtonView brakeUp();
        TestCabButtonView brakeDown();
        TestCabButtonView reverser();
        TestCabButtonView mode();
        TestCabButtonView parking();
    }

    TestCabControlsOperations testCabControls();

    interface TestCabControlsOperations {
        TestCabControlsView instance();
        CabNeutralPositions neutralPositions();
        TestCabControlsViewView view(CabState state);
    }

    TrainControlLimitsOperations trainControlLimits();

    interface TrainControlLimitsOperations {
        TrainControlLimits capture(VehicleRuntimeView runtime, UUID member);
    }

    TrainControlOperations trainControl();

    interface TrainControlOperations {
        double LINE_SPEED_LIMIT_KMH = 300.0;
        double serviceDeceleration(VehicleRuntimeView runtime, UUID member, TrainControlSource source);
        TrainCommand service(TrainControlSource source, double signedPower, double serviceBrake);
        double reservationDistance(double speedMps, double serviceDeceleration);
    }

    TrainDisplayReadingsOperations trainDisplayReadings();

    interface TrainDisplayReadingsOperations {
        CabDisplayState read(VehicleRuntimeView runtime, UUID member, TrainControlSource source,
                DmiState.Mode mode, TrainDisplayReadingsRouteView route, String trainNumber);
        CabDisplayState withRoute(CabDisplayState base, DmiState.Mode mode, TrainDisplayReadingsRouteView route);
        CabDisplayState withRoute(CabDisplayState base, DmiState.Mode mode, TrainDisplayReadingsRouteView route,
                String trainNumber);
    }

    TrainDisplayReadingsRouteOperations trainDisplayReadingsRoute();

    interface TrainDisplayReadingsRouteOperations {
        TrainDisplayReadingsRouteView create(DmiState.Target target, String station, double lineLimitKmh,
                String status);
        TrainDisplayReadingsRouteView observed(DmiState.Target station, String stationLabel, String status,
                List<CtcsControlSectionView> sections);
        TrainDisplayReadingsRouteView unavailable();
    }

    interface AutomaticTrainControlTargetView {
        int direction();
        double distanceM();
        double speedLimitMps();
        boolean station();
        boolean stop();
        CtcsProfile.Profile constraints();
        TrainControlLimits limits();
    }

    interface CabControlArbitrationCommandView {
        boolean enabled();
        TrainCommand control();
    }

    interface CabControlArbitrationSelectionView {
        boolean hasEnabledCab();
        boolean conflicting();
        CabState.Mode mode();
        int enabledCount();
    }

    interface Crh1CabControlsView extends CabTrainControl {}

    interface Crh1CabControlsDirectionButtonView extends BlockControl.Button {
        String id();
    }

    interface Crh1CabHandleView extends BlockControl.Handle {
        String id();
        int notch(ControlState state);
        int releasedNotch(int notch);
    }

    interface Crh2CabControlsView extends CabTrainControl {}

    interface Crh2CabHandleView extends BlockControl.Handle {
        String id();
        int maxNotch();
    }

    interface Ctcs2PanelView extends ControlSurface.Module, CabTrainControl {}

    enum CtcsControlOccupancy {
        UNKNOWN, CLEAR, BLOCKED
    }

    interface CtcsControlSectionView {
        double distanceM();
        String id();
        CtcsControlOccupancy occupancy();
        boolean linked();
    }

    interface SimpleCabControlsShuntingReadinessView {
        boolean powerAtZero();
        boolean reverserCutOut();
    }

    interface StationStopControlView {
        StationStopControlStateView state();
        boolean observeStop(double speedMps, StationStopControlStationObservationView station);
        boolean observeDeparture(boolean permitted);
        boolean observeDistance(double distanceM);
        void clear();
        void restore(StationStopControlStateView saved);
    }

    interface StationStopControlStateView {
        boolean waitingForDeparture();
        String stationId();
        String stationName();
    }

    interface StationStopControlStationObservationView {
        String stationId();
        String stationName();
        double distanceM();
    }

    interface TestCabButtonView extends BlockControl.Button {}

    interface TestCabControlsView extends CabTrainControl {}

    interface TestCabControlsViewView {
        int power();
        int brake();
        int reverser();
        boolean automatic();
        boolean parking();
    }

    enum TrainControlSource {
        CAB_MANUAL, CAB_AUTOMATIC, CREATE_MANUAL, CREATE_AUTOMATIC
    }

    interface TrainDisplayReadingsRouteView {
        TrainDisplayReadingsRouteView withStationStop(StationStopControlStateView stop);
        DmiState.Target target();
        String station();
        double lineLimitKmh();
        String status();
        CtcsProfile.Profile ctcs();
        StationStopControlStateView stationStop();
    }
}
