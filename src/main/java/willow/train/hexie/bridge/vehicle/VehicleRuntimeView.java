package willow.train.hexie.bridge.vehicle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;
import willow.train.hexie.bridge.railway.TrackPathView;
import willow.train.hexie.core.api.traincontrol.TrainCommand;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.core.api.vehicle.VehicleKind;

public interface VehicleRuntimeView {
    default VehicleKind vehicleKind() {
        return VehicleKind.CREATE_COMPATIBLE;
    }

    VehicleBridge.VehicleConstraintSolverSolutionView placement();
    VehicleBridge.CarriageDefinitionView definition(UUID id);
    void captureCouplerVisual(UUID id, CouplingBridge.CouplingEndpointVisualView visual);
    boolean updateCouplerVisual(UUID id, CouplingBridge.CouplingEndpointVisualView visual);
    double bogeySpeed(UUID id);
    double motionDistance(UUID bogey);
    Optional<String> constraintFailure();
    void dynamicsMultipliers(double acceleration, double braking);
    void poweredCarriages(Set<UUID> carriages, double multiplier);
    void collisionEnabled(boolean enabled);
    long consistRevision();
    UUID couplingPartner(UUID endpoint);
    void addCarriage(VehicleBridge.CarriageDefinitionView definition, double distance, double speed);
    List<? extends VehicleBridge.CarriageDefinitionView> definitions();
    double speed(UUID carriage);
    void speedForConsist(UUID carriage, double speed);
    double tractionDirection(UUID carriage);
    void controlsForConsist(UUID carriage, TrainCommand control);
    boolean connected(UUID first, UUID second);
    void controls(UUID carriage, TrainCommand control);
    VehicleBridge.VehicleRuntimeCarriageStateView state(UUID id);
    List<? extends VehicleBridge.VehicleRuntimeCouplingControlResetView> drainCouplingControlResets();
    void couplingAvailability(Predicate<CouplingEndpointView> available);
    void uncouple(UUID endpoint);
    void beginCoupling(UUID first, UUID second, double approachSpeedMps);
    void cancelCoupling(UUID endpoint);
    void releaseCouplingsIf(Predicate<CouplingEndpointView> shouldRelease);
    void installCouplerAdapter(UUID endpointId, CouplingBridge.CouplerAdapterView adapter);
    CouplingBridge.CouplerAdapterView removeCouplerAdapter(UUID endpointId);
    void setFloorOffset(UUID carriageId, double offset);
    void shiftBogeyMount(UUID carriageId, int index, Point3 shift);
    List<? extends VehicleBridge.VehicleRuntimeCouplingStateView> couplingStates();
    VehicleBridge.VehicleRuntimeCheckpointView checkpoint();
    VehicleRuntimeView rebind(TrackPathView target, double distanceOffset);
    VehicleRuntimeView remapRoute(TrackPathView target, DoubleUnaryOperator mapDistance);
    void stopAtTrackPoint(UUID member, UUID bogeyId, double exactBogeyDistance);
    VehicleRuntimeView rebase(TrackPathView target, Map<UUID, Double> carriageDistances, boolean stop);
    VehicleRuntimeView subset(Set<UUID> carriageIds);
    VehicleRuntimeView subsetPreservingPlacement(Set<UUID> carriageIds);
    void tick(double seconds, Set<UUID> heldCarriages, Set<UUID> pausedCarriages);
}
