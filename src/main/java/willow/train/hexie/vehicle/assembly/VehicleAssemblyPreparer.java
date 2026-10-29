package willow.train.hexie.vehicle.assembly;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;
import willow.train.hexie.vehicle.runtime.BogeyMount;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

/** Connects bounded discovery to the independent runtime definition, without changing the world. */
public final class VehicleAssemblyPreparer {
    private VehicleAssemblyPreparer() {}

    @FunctionalInterface
    public interface Connections {
        /** Decode registered coupling blocks; points use the runtime's north-forward local frame. */
        List<CouplingEndpoint> decode(CarriageSnapshot snapshot, Direction forward);
    }

    public record Carriage(CarriageSnapshot structure, CarriageDefinition definition, double initialDistance) {}

    public record Prepared(Direction forward, List<Carriage> carriages) {
        public Prepared { carriages = List.copyOf(carriages); }
    }

    public static Prepared prepare(ServerLevel level, AssemblyRequest request, AssemblyScanner scanner,
                                   BogeyModelCatalog catalog, Connections connections) throws AssemblyScanner.ScanException {
        if (request.forward().getAxis() == Direction.Axis.Y)
            throw new IllegalArgumentException("Carriage forward direction must be horizontal");
        var snapshots = AssemblyCapture.capture(level, scanner.scan(level, request));
        return prepare(snapshots, request.forward(), catalog, connections);
    }

    /** Convert already captured structures; does not scan or read the world a second time. */
    public static Prepared prepare(List<CarriageSnapshot> snapshots, Direction forward,
                                   BogeyModelCatalog catalog, Connections connections) {
        snapshots = List.copyOf(snapshots);
        if (snapshots.isEmpty() || forward.getAxis() == Direction.Axis.Y)
            throw new IllegalArgumentException("Carriages and a horizontal forward direction are required");
        var result = new ArrayList<Carriage>();
        BlockPos routeOrigin = snapshots.getFirst().origin();
        for (var snapshot : snapshots) {
            var byPosition = new java.util.HashMap<BlockPos, CarriageSnapshot.Block>();
            for (var block : snapshot.blocks()) byPosition.put(block.position(), block);
            var bogeys = new ArrayList<BogeyDefinition>();
            for (var position : snapshot.supports()) {
                var model = catalog.resolve(byPosition.get(position));
                UUID id = UUID.nameUUIDFromBytes((snapshot.id() + ":bogey:" + position.asLong()).getBytes(StandardCharsets.UTF_8));
                var bodyPoint = northForward(position, forward).plus(model.pivot());
                var trackPivot = model.pivot().plus(new CouplingEndpoint.Point(0, model.blockCenterHeight(), 0));
                var mount = new BogeyMount(bodyPoint, trackPivot, model.type(), model.lateralLimit());
                bogeys.add(new BogeyDefinition(id, model.id().toString(), -bodyPoint.z(),
                        model.geometry(), model.operating(), mount));
            }
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (var block : snapshot.blocks()) {
                double centre = project(block.position(), forward);
                minimum = Math.min(minimum, centre - .5);
                maximum = Math.max(maximum, centre + .5);
            }
            var definition = new CarriageDefinition(snapshot.id(), bogeys, connections.decode(snapshot, forward),
                    Math.max(0, -minimum), Math.max(0, maximum));
            result.add(new Carriage(snapshot, definition, project(snapshot.origin().subtract(routeOrigin), forward)));
        }
        return new Prepared(forward, result);
    }

    private static double project(BlockPos position, Direction forward) {
        return forward.getAxis().choose(position.getX(), position.getY(), position.getZ())
                * (double) forward.getAxisDirection().getStep();
    }

    /** Coordinates are between block centres, so their common half-block offset cancels. */
    private static CouplingEndpoint.Point northForward(BlockPos position, Direction forward) {
        return switch (forward) {
            case NORTH -> new CouplingEndpoint.Point(position.getX(), position.getY(), position.getZ());
            case EAST -> new CouplingEndpoint.Point(position.getZ(), position.getY(), -position.getX());
            case SOUTH -> new CouplingEndpoint.Point(-position.getX(), position.getY(), -position.getZ());
            case WEST -> new CouplingEndpoint.Point(-position.getZ(), position.getY(), position.getX());
            default -> throw new IllegalArgumentException("Carriage forward direction must be horizontal");
        };
    }
}
