package willow.train.hexie.vehicle.assembly;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.core.util.station.Connections;
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

    public record Carriage(CarriageSnapshot structure, CarriageDefinition definition, double initialDistance) implements VehicleBridge.VehicleAssemblyPreparerCarriageView {}

    public record Prepared(Direction forward, List<Carriage> carriages) implements VehicleBridge.VehicleAssemblyPreparerPreparedView {
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
            for (var position : snapshot.supports()) {
                var support = AssemblyBogeySupport.resolve(snapshot, position);
                if (support != null) byPosition.put(position, support.capturedBlock());
            }
            int floorHeight = HexieBridges.worldBlock().bogeyFloorData().value(byPosition.get(snapshot.supports().getFirst()).entityData());
            for (var position : snapshot.supports())
                if (HexieBridges.worldBlock().bogeyFloorData().value(byPosition.get(position).entityData()) != floorHeight)
                    throw new IllegalArgumentException("同一节车厢的转向架地板高度设置不一致，请统一后再组装。");
            double floorOffset = HexieBridges.worldBlock().bogeyFloorData().offset(floorHeight);
            var bogeys = new ArrayList<BogeyDefinition>();
            for (var position : snapshot.supports()) {
                var model = catalog.resolve(byPosition.get(position));
                UUID id = UUID.nameUUIDFromBytes((snapshot.id() + ":bogey:" + position.asLong()).getBytes(StandardCharsets.UTF_8));
                var captured = byPosition.get(position);
                var displacement = HexieBridges.worldBlock().bogeyFloorData().displacement(captured.state(), HexieBridges.worldBlock().bogeyFloorData().longitudinalFromEntity(captured.entityData()));
                var bodyPoint = northForward(position, forward).plus(model.pivot()).plus(northForward(displacement, forward));
                var trackPivot = model.pivot().plus(new Point3(0, model.blockCenterHeight(), 0));
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
                    Math.max(0, -minimum), Math.max(0, maximum)).withFloorOffset(floorOffset);
            result.add(new Carriage(snapshot, definition, project(snapshot.origin().subtract(routeOrigin), forward)));
        }
        return new Prepared(forward, result);
    }

    private static double project(BlockPos position, Direction forward) {
        return forward.getAxis().choose(position.getX(), position.getY(), position.getZ())
                * (double) forward.getAxisDirection().getStep();
    }

    /** Coordinates are between block centres, so their common half-block offset cancels. */
    private static Point3 northForward(BlockPos position, Direction forward) {
        return northForward(new net.minecraft.world.phys.Vec3(position.getX(), position.getY(), position.getZ()), forward);
    }

    public static Point3 northForward(net.minecraft.world.phys.Vec3 position, Direction forward) {
        return switch (forward) {
            case NORTH -> new Point3(position.x, position.y, position.z);
            case EAST -> new Point3(position.z, position.y, -position.x);
            case SOUTH -> new Point3(-position.x, position.y, -position.z);
            case WEST -> new Point3(-position.z, position.y, position.x);
            default -> throw new IllegalArgumentException("Carriage forward direction must be horizontal");
        };
    }
}
