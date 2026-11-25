package willow.train.hexie.vehicle.bridge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.bridge.vehicle.SimpleCabControlsView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.CabNeutralPositions;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.DmiState;
import willow.train.hexie.core.api.cab.TrainControlLimits;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.core.api.traincontrol.TrainCommand;
import willow.train.hexie.vehicle.control.AutomaticTrainControl;
import willow.train.hexie.vehicle.control.CabControlArbitration;
import willow.train.hexie.vehicle.control.CabController;
import willow.train.hexie.vehicle.control.CabModule;
import willow.train.hexie.vehicle.control.Crh1CabControls;
import willow.train.hexie.vehicle.control.Crh1CabHandle;
import willow.train.hexie.vehicle.control.Crh2CabControls;
import willow.train.hexie.vehicle.control.Crh2CabHandle;
import willow.train.hexie.vehicle.control.Ctcs2Panel;
import willow.train.hexie.vehicle.control.CtcsControl;
import willow.train.hexie.vehicle.control.SimpleCabControls;
import willow.train.hexie.vehicle.control.StationStopControl;
import willow.train.hexie.vehicle.control.TestCabButton;
import willow.train.hexie.vehicle.control.TestCabControls;
import willow.train.hexie.vehicle.control.TrainControl;
import willow.train.hexie.vehicle.control.TrainDisplayReadings;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

final class CabControlPort implements CabControlBridge {
    private final AutomaticTrainControlOperations automaticTrainControl = new AutomaticTrainControlOperations() {
        @Override
        public AutomaticTrainControlTargetView fromRoute(TrainDisplayReadingsRouteView route, int direction,
                boolean navigating, boolean waypointTransition, double speed, TrainControlLimits limits) {
            return AutomaticTrainControl.fromRoute((TrainDisplayReadings.Route) route, direction, navigating, waypointTransition, speed, limits);
        }

        @Override
        public AutomaticTrainControlTargetView stationApproach(AutomaticTrainControlTargetView target) {
            return AutomaticTrainControl.stationApproach((AutomaticTrainControl.Target) target);
        }

        @Override
        public TrainCommand update(TrainControlSource source, double speed, AutomaticTrainControlTargetView target,
                double serviceDeceleration, StationStopControlStateView stationStop) {
            return AutomaticTrainControl.update(TrainControl.Source.valueOf(source.name()), speed, (AutomaticTrainControl.Target) target, serviceDeceleration, (StationStopControl.State) stationStop);
        }
    };

    private final CabControlArbitrationOperations cabControlArbitration = new CabControlArbitrationOperations() {
        @Override
        public CabControlArbitrationSelectionView select(Iterable<CabState> cabs) {
            return CabControlArbitration.select(cabs);
        }

        @Override
        public TrainCommand aggregate(Iterable<CabControlArbitrationCommandView> commands, TrainCommand backend) {
            return CabControlArbitration.aggregate(commands, backend);
        }
    };

    private final CabControllerOperations cabController = new CabControllerOperations() {
        @Override
        public TrainCommand manual(CabState.Input input) {
            return CabController.manual(input);
        }

        @Override
        public TrainCommand automaticOutput(TrainControlSource source, boolean automatic, boolean wasAutomatic,
                boolean manual, boolean reset, double speed, AutomaticTrainControlTargetView target,
                double serviceDeceleration, StationStopControlStateView stationStop) {
            return CabController.automaticOutput(TrainControl.Source.valueOf(source.name()), automatic, wasAutomatic, manual, reset, speed, (AutomaticTrainControl.Target) target, serviceDeceleration, (StationStopControl.State) stationStop);
        }
    };

    private final CabModuleOperations cabModule = new CabModuleOperations() {
        @Override
        public CabState synchronize(CabTrainControl desk, CabState previous, CabState source, int relativeDirection) {
            return CabModule.synchronize(desk, previous, source, relativeDirection);
        }

        @Override
        public CabState synchronize(CabControlHost host, BlockState block, CabState previous, CabState source,
                int relativeDirection) {
            return CabModule.synchronize(host, block, previous, source, relativeDirection);
        }

        @Override
        public CabState normalize(CabTrainControl desk, CabState state) {
            return CabModule.normalize(desk, state);
        }

        @Override
        public CabState operateControls(CabControlHost host, BlockState block, String id, int value,
                CabState previous, double speed) {
            return CabModule.operateControls(host, block, id, value, previous, speed);
        }

        @Override
        public Set<CabTrainControl.Demand> operatedDemands(CabControlHost host, BlockState block, String id) {
            return CabModule.operatedDemands(host, block, id);
        }

        @Override
        public CabModuleView read(CompoundTag tag) {
            return CabModule.read(tag);
        }

        @Override
        public Map<BlockPos, CabState> readFrames(CompoundTag frame) {
            return CabModule.readFrames(frame);
        }
    };

    private final Crh1CabControlsOperations crh1CabControls = new Crh1CabControlsOperations() {
        @Override
        public Crh1CabControlsView instance() {
            return Crh1CabControls.INSTANCE;
        }

        @Override
        public CabNeutralPositions neutralPositions() {
            return Crh1CabControls.NEUTRAL_POSITIONS;
        }
    };

    private final Crh2CabControlsOperations crh2CabControls = new Crh2CabControlsOperations() {
        @Override
        public Crh2CabControlsView instance() {
            return Crh2CabControls.INSTANCE;
        }

        @Override
        public CabNeutralPositions neutralPositions() {
            return Crh2CabControls.NEUTRAL_POSITIONS;
        }
    };

    private final Crh2CabHandleOperations crh2CabHandle = new Crh2CabHandleOperations() {
        @Override
        public Crh2CabHandleView power() {
            return Crh2CabHandle.POWER;
        }

        @Override
        public Crh2CabHandleView brake() {
            return Crh2CabHandle.BRAKE;
        }

        @Override
        public Crh2CabHandleView reverser() {
            return Crh2CabHandle.REVERSER;
        }
    };

    private final Ctcs2PanelOperations ctcs2Panel = new Ctcs2PanelOperations() {
        @Override
        public Ctcs2PanelView instance() {
            return Ctcs2Panel.INSTANCE;
        }

        @Override
        public List<String> softKeyLabels() {
            return Ctcs2Panel.SOFT_KEY_LABELS;
        }
    };

    private final StationStopControlOperations stationStopControl = new StationStopControlOperations() {
        @Override
        public StationStopControlView create() {
            return new StationStopControl();
        }

        @Override
        public boolean stopped(double speedMps) {
            return StationStopControl.stopped(speedMps);
        }
    };

    private final StationStopControlStateOperations stationStopControlState = new StationStopControlStateOperations() {
        @Override
        public StationStopControlStateView create(boolean waitingForDeparture, String stationId, String stationName) {
            return new StationStopControl.State(waitingForDeparture, stationId, stationName);
        }

        @Override
        public StationStopControlStateView running() {
            return StationStopControl.State.RUNNING;
        }
    };

    private final TestCabButtonOperations testCabButton = new TestCabButtonOperations() {
        @Override
        public TestCabButtonView powerUp() {
            return TestCabButton.POWER_UP;
        }

        @Override
        public TestCabButtonView powerDown() {
            return TestCabButton.POWER_DOWN;
        }

        @Override
        public TestCabButtonView brakeUp() {
            return TestCabButton.BRAKE_UP;
        }

        @Override
        public TestCabButtonView brakeDown() {
            return TestCabButton.BRAKE_DOWN;
        }

        @Override
        public TestCabButtonView reverser() {
            return TestCabButton.REVERSER;
        }

        @Override
        public TestCabButtonView mode() {
            return TestCabButton.MODE;
        }

        @Override
        public TestCabButtonView parking() {
            return TestCabButton.PARKING;
        }
    };

    private final TestCabControlsOperations testCabControls = new TestCabControlsOperations() {
        @Override
        public TestCabControlsView instance() {
            return TestCabControls.INSTANCE;
        }

        @Override
        public CabNeutralPositions neutralPositions() {
            return TestCabControls.NEUTRAL_POSITIONS;
        }

        @Override
        public TestCabControlsViewView view(CabState state) {
            return TestCabControls.view(state);
        }
    };

    private final TrainControlOperations trainControl = new TrainControlOperations() {
        @Override
        public double serviceDeceleration(VehicleRuntimeView runtime, UUID member, TrainControlSource source) {
            return TrainControl.serviceDeceleration((VehicleRuntime) runtime, member, TrainControl.Source.valueOf(source.name()));
        }

        @Override
        public TrainCommand service(TrainControlSource source, double signedPower, double serviceBrake) {
            return TrainControl.service(TrainControl.Source.valueOf(source.name()), signedPower, serviceBrake);
        }

        @Override
        public double reservationDistance(double speedMps, double serviceDeceleration) {
            return TrainControl.reservationDistance(speedMps, serviceDeceleration);
        }
    };

    private final TrainDisplayReadingsOperations trainDisplayReadings = new TrainDisplayReadingsOperations() {
        @Override
        public CabDisplayState read(VehicleRuntimeView runtime, UUID member, TrainControlSource source,
                DmiState.Mode mode, TrainDisplayReadingsRouteView route, String trainNumber) {
            return TrainDisplayReadings.read((VehicleRuntime) runtime, member, TrainControl.Source.valueOf(source.name()), mode, (TrainDisplayReadings.Route) route, trainNumber);
        }

        @Override
        public CabDisplayState withRoute(CabDisplayState base, DmiState.Mode mode,
                TrainDisplayReadingsRouteView route) {
            return TrainDisplayReadings.withRoute(base, mode, (TrainDisplayReadings.Route) route);
        }

        @Override
        public CabDisplayState withRoute(CabDisplayState base, DmiState.Mode mode,
                TrainDisplayReadingsRouteView route, String trainNumber) {
            return TrainDisplayReadings.withRoute(base, mode, (TrainDisplayReadings.Route) route, trainNumber);
        }
    };

    private final TrainDisplayReadingsRouteOperations trainDisplayReadingsRoute = new TrainDisplayReadingsRouteOperations() {
        @Override
        public TrainDisplayReadingsRouteView create(DmiState.Target target, String station, double lineLimitKmh,
                String status) {
            return new TrainDisplayReadings.Route(target, station, lineLimitKmh, status);
        }

        @Override
        public TrainDisplayReadingsRouteView observed(DmiState.Target station, String stationLabel, String status,
                List<CtcsControlSectionView> sections) {
            return TrainDisplayReadings.Route.observed(station, stationLabel, status, sections.stream().map(CtcsControl.Section.class::cast).toList());
        }

        @Override
        public TrainDisplayReadingsRouteView unavailable() {
            return TrainDisplayReadings.Route.unavailable();
        }
    };

    @Override
    public SimpleCabControlsView simpleCabControls() {
        return SimpleCabControls.INSTANCE;
    }

    @Override
    public CabModuleView createCabModule() {
        return new CabModule();
    }

    @Override
    public AutomaticTrainControlOperations automaticTrainControl() {
        return automaticTrainControl;
    }

    @Override
    public CabControlArbitrationCommandView createCabControlArbitrationCommand(boolean enabled,
            TrainCommand control) {
        return new CabControlArbitration.Command(enabled, control);
    }

    @Override
    public CabControlArbitrationOperations cabControlArbitration() {
        return cabControlArbitration;
    }

    @Override
    public CabControllerOperations cabController() {
        return cabController;
    }

    @Override
    public CabModuleOperations cabModule() {
        return cabModule;
    }

    @Override
    public Crh1CabControlsDirectionButtonOperations crh1CabControlsDirectionButton() {
        return () -> Crh1CabControls.DirectionButton.INSTANCE;
    }

    @Override
    public Crh1CabControlsOperations crh1CabControls() {
        return crh1CabControls;
    }

    @Override
    public Crh1CabHandleOperations crh1CabHandle() {
        return () -> Crh1CabHandle.INSTANCE;
    }

    @Override
    public Crh2CabControlsOperations crh2CabControls() {
        return crh2CabControls;
    }

    @Override
    public Crh2CabHandleOperations crh2CabHandle() {
        return crh2CabHandle;
    }

    @Override
    public Ctcs2PanelOperations ctcs2Panel() {
        return ctcs2Panel;
    }

    @Override
    public CtcsControlOperations ctcsControl() {
        return sections -> CtcsControl.codesAtSignals(sections.stream().map(CtcsControl.Section.class::cast).toList());
    }

    @Override
    public CtcsControlSectionView createCtcsControlSection(String id, double distanceM,
            CtcsControlOccupancy occupancy, boolean linked) {
        return new CtcsControl.Section(id, distanceM, occupancy, linked);
    }

    @Override
    public StationStopControlOperations stationStopControl() {
        return stationStopControl;
    }

    @Override
    public StationStopControlStateOperations stationStopControlState() {
        return stationStopControlState;
    }

    @Override
    public StationStopControlStationObservationView createStationStopControlStationObservation(String stationId,
            String stationName, double distanceM) {
        return new StationStopControl.StationObservation(stationId, stationName, distanceM);
    }

    @Override
    public TestCabButtonOperations testCabButton() {
        return testCabButton;
    }

    @Override
    public TestCabControlsOperations testCabControls() {
        return testCabControls;
    }

    @Override
    public TrainControlLimitsOperations trainControlLimits() {
        return (runtime, member) -> willow.train.hexie.vehicle.control.TrainControlLimits.capture((VehicleRuntime) runtime, member);
    }

    @Override
    public TrainControlOperations trainControl() {
        return trainControl;
    }

    @Override
    public TrainDisplayReadingsOperations trainDisplayReadings() {
        return trainDisplayReadings;
    }

    @Override
    public TrainDisplayReadingsRouteOperations trainDisplayReadingsRoute() {
        return trainDisplayReadingsRoute;
    }
}
