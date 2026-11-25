package willow.train.hexie.compat.create.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.BogeyDefinitionView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageDefinitionView;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.CreateTrainBridge;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;

// split from CreateTrainBridge.java
public final class CreateTrainTrackPoints {
    private final CreateTrainBridge trainBridge;

    public CreateTrainTrackPoints(CreateTrainBridge trainBridge) { this.trainBridge = trainBridge; }

    public final Map<UUID, List<PointBinding>> carriagePoints = new HashMap<>();
    public final Map<TravellingPoint, PointBinding> pointLookup = new IdentityHashMap<>();

    public record PreparedBogey(CarriageBogey bogey, double halfSpacing) {}

    public PreparedBogey prepareBogey(CarriageDefinitionView car, BogeyDefinitionView definition) {
        var snapshot = HexieBridges.vehicle().carriageSnapshotStore().get(trainBridge.motion.level()).find(car.id()).orElseThrow();
        int index = car.bogeys().indexOf(definition);
        var pos = snapshot.supports().get(index);
        var block = snapshot.blocks().stream().filter(b -> b.position().equals(pos)).findFirst().orElseThrow();
        var support = HexieBridges.vehicle().assemblyBogeySupport().resolve(snapshot, pos);
        if (support != null) block = support.capturedBlock();
        // A removed support keeps its saved motion parameters and an air position in the body.
        // Create still requires a bogey type for its invisible compatibility carriage.
        AbstractBogeyBlock<?> type = block.state().isAir() ? com.simibubi.create.AllBlocks.SMALL_BOGEY.get()
                : block.state().getBlock() instanceof AbstractBogeyBlock<?> bogey ? bogey : null;
        if (type == null)
            throw new IllegalStateException("Compatibility bogey has no registered Create block");
        CompoundTag data = block.entityData() == null ? new CompoundTag() : block.entityData().getCompound("BogeyData").copy();
        double half = type.getWheelPointSpacing() / 2;
        double centre = supportDistance(definition);
        var leading = trainBridge.motion.route().boundedPointAt(centre - half, false);
        var trailing = trainBridge.motion.route().boundedPointAt(centre + half, false);
        var bogey = new CarriageBogey(type, false, data, leading, trailing);
        return new PreparedBogey(bogey, half);
    }

    public void bindBogey(CarriageDefinitionView car, BogeyDefinitionView definition, PreparedBogey prepared) {
        var bogey = prepared.bogey();
        // Constructor mixins (RailX) may replace the supplied points. Bind the
        // instances actually retained by the bogey, which all later readers use.
        bindPoint(car.id(), definition, -prepared.halfSpacing(), bogey.leading());
        bindPoint(car.id(), definition, prepared.halfSpacing(), bogey.trailing());
    }

    public void bindPoint(UUID carriage, BogeyDefinitionView bogey, double offset, TravellingPoint point) {
        double distance = supportDistance(bogey) + offset;
        var binding = new PointBinding(bogey.id(), offset, point, distance);
        carriagePoints.computeIfAbsent(carriage, ignored -> new ArrayList<>()).add(binding);
        pointLookup.put(point, binding);
    }

    public double supportDistance(BogeyDefinitionView bogey) { return trainBridge.motion.runtime().placement().bogeys().get(bogey.id()).distance(); }

    public void sync(CompatibilityTrain train) {
        for (UUID id : train.carriageIds) {
            var car = trainBridge.carriages.get(id);
            for (var binding : carriagePoints.get(id)) {
                double distance = trainBridge.motion.runtime().placement().bogeys().get(binding.bogey).distance() + binding.offset;
                CreateTrackCompatibility.copy(trainBridge.motion.route().boundedPointAt(distance, false), binding.point);
                binding.distance = distance; binding.remember();
            }
            SnapshotCarriageEntity.syncTrackAnchors(car, trainBridge.motion.level(), trainBridge.motion.runtime().placement().bodies().get(id));
        }
        CreateTrackCompatibility.synchronizeGraph(train);
    }

    /** Native navigation consumes signed travel at the end of the train which moved first. */
    public double actualTravel(CompatibilityTrain train) {
        var first = binding(train.carriages.getFirst().getLeadingPoint());
        double delta = currentDistance(first) - first.distance;
        var point = delta > 0 ? train.carriages.getLast().getTrailingPoint() : first.point;
        var binding = binding(point);
        return binding.distance - currentDistance(binding);
    }

    public double currentDistance(PointBinding point) {
        return trainBridge.motion.runtime().placement().bogeys().get(point.bogey).distance() + point.offset;
    }

    public double requestedTravel(CompatibilityTrain train) {
        var first = binding(train.carriages.getFirst().getLeadingPoint());
        double delta = trainBridge.motion.runtime().motionDistance(first.bogey) + first.offset - first.distance;
        var leading = delta > 0 ? binding(train.carriages.getLast().getTrailingPoint()) : first;
        return leading.distance - (trainBridge.motion.runtime().motionDistance(leading.bogey) + leading.offset);
    }

    /** Probe native rail connectivity without moving a carriage or firing a signal twice. */
    public Double nativeTrackEnd(CompatibilityTrain train, PointBinding leading, double target) {
        if (target >= trainBridge.motion.route().path().minimumDistance() && target <= trainBridge.motion.route().path().maximumDistance())
            return null;
        // Probe from the last synchronized mileage on this route, including virtual wheels beyond its ends.
        var preview = trainBridge.motion.route().boundedPointAt(leading.distance, false);
        boolean simpleManual = trainBridge.motion.isSimpleControlSelected(train.carriageIds.getFirst())
                && !trainBridge.motion.isApproachingStation(train.carriageIds.getFirst());
        var selector = train.navigation.destination == null || simpleManual || trainBridge.motion.cabSelection(train.carriageIds.getFirst()).conflicting()
                ? preview.steer(train.manualSteer, new Vec3(0, 1, 0))
                : train.navigation.controlSignalScout();
        preview.travel(train.graph, leading.distance - target, selector, preview.ignoreEdgePoints(), preview.ignoreTurns());
        if (!preview.blocked) return null;
        try { return trainBridge.motion.route().distanceOf(preview); }
        catch (IllegalArgumentException unselectedEdge) {
            // The native scout left our cached route. Its new edge must be rebound before
            // the independent solver can accept a position there; this is not a stop.
            return null;
        }
    }

    public PointBinding binding(TravellingPoint point) {
        return java.util.Objects.requireNonNull(pointLookup.get(point), "Unmapped compatibility point");
    }

    /** A terminal wheel can be clamped to the track end. Its partner still gives the true centre. */
    public static double wheelCentre(CreateRouteBinding route, CarriageBogey bogey) {
        double first = route.distanceOf(bogey.leading()), last = route.distanceOf(bogey.trailing());
        double low = Math.min(first, last), high = Math.max(first, last);
        var style = bogey.getStyle();
        var size = bogey.getSize();
        AbstractBogeyBlock<?> type = style.validSizes().contains(size) ? style.getBlockForSize(size) : null;
        if (type == null) type = com.simibubi.create.AllBlocks.SMALL_BOGEY.get();
        double half = type.getWheelPointSpacing() / 2;
        boolean atStart = low <= route.path().minimumDistance() + 1e-7;
        boolean atEnd = high >= route.path().maximumDistance() - 1e-7;
        if (high - low < 2 * half - 1e-7 && atStart != atEnd)
            return Math.clamp(atStart ? high - half : low + half, low, high);
        return (low + high) / 2;
    }

    public static final class PointBinding {
        public final UUID bogey;
        public final double offset;
        public final TravellingPoint point;
        public double distance;
        Object node1, node2, edge;
        double position;
        PointBinding(UUID bogey, double offset, TravellingPoint point, double distance) {
            this.bogey = bogey; this.offset = offset; this.point = point; this.distance = distance; remember();
        }
        public void remember() { node1 = point.node1; node2 = point.node2; edge = point.edge; position = point.position; }
        public boolean changed() { return node1 != point.node1 || node2 != point.node2 || edge != point.edge
                || Math.abs(position - point.position) > 1e-6; }
    }

    public record PointKey(UUID bogey, double offset) { }
    public record PointOrigin(double distance, TravellingPoint point) { }
}
