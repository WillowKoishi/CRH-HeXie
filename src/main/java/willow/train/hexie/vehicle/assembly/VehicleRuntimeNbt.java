package willow.train.hexie.vehicle.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.vehicle.bogey.BogeyGeometry;
import willow.train.hexie.vehicle.runtime.*;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.vehicle.track.TrackGeometry.Vector;
import willow.train.hexie.vehicle.track.TrackPath;

/** Runtime state serialization; structure blocks remain in their separate carriage snapshots. */
public final class VehicleRuntimeNbt {
    private static final int VERSION = 7;
    private static final int STRICT_PLACEMENT_VERSION = 6;
    private VehicleRuntimeNbt() {}

    public static CompoundTag save(VehicleRuntime runtime) {
        return saveCheckpoint(runtime.checkpoint());
    }

    public static CompoundTag saveCheckpoint(VehicleRuntime.Checkpoint checkpoint) {
        var tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        var cars = new ListTag();
        for (var saved : checkpoint.carriages()) {
            var car = new CompoundTag();
            var definition = saved.definition();
            car.putUUID("Id", definition.id());
            number(car, "Distance", saved.distance());
            number(car, "Speed", saved.speed());
            number(car, "RearExtent", definition.rearExtent());
            number(car, "FrontExtent", definition.frontExtent());
            number(car, "FloorOffset", definition.floorOffset());
            var bogeys = new ListTag();
            for (var bogey : definition.bogeys()) {
                var b = new CompoundTag();
                b.putUUID("Id", bogey.id());
                b.putString("Model", bogey.modelId());
                number(b, "Offset", bogey.offset());
                b.put("BodyMount", point(bogey.mount().bodyPoint()));
                b.put("TrackPivot", point(bogey.mount().trackPivot()));
                b.putString("MountType", bogey.mount().type().name());
                number(b, "LateralLimit", bogey.mount().lateralLimit());
                var p = bogey.parameters();
                number(b, "LoadKg", p.loadKg());
                number(b, "TractionN", p.tractionForceN());
                number(b, "BrakeN", p.brakeForceN());
                number(b, "ResistanceN", p.rollingResistanceN());
                number(b, "WheelRadius", p.wheelRadiusM());
                number(b, "GeometryWheelRadius", bogey.geometry().wheelRadius());
                number(b, "DriveRatio", p.driveRatio());
                var axles = new ListTag();
                bogey.geometry().axlePositions().forEach(position -> axles.add(LongTag.valueOf(Double.doubleToRawLongBits(position))));
                b.put("Axles", axles);
                var control = saved.controls().get(bogey.id());
                number(b, "Throttle", control.throttle());
                number(b, "Brake", control.brake());
                bogeys.add(b);
            }
            car.put("Bogeys", bogeys);
            var endpoints = new ListTag();
            for (var endpoint : definition.couplings()) {
                var e = new CompoundTag();
                e.putUUID("Id", endpoint.id());
                e.putString("Facing", endpoint.facing().name());
                e.putString("Kind", endpoint.kind().name());
                e.putString("Model", endpoint.model());
                if (endpoint.adapter() != null) {
                    var adapterTag = willow.train.hexie.blocks.coupler.CouplerAdapterData.encode(endpoint.adapter());
                    number(adapterTag, "Thickness", endpoint.adapter().thickness());
                    e.put("Adapter", adapterTag);
                }
                number(e, "MaxExtension", endpoint.maxExtension());
                e.put("Block", point(endpoint.blockCenter()));
                e.put("Pivot", point(endpoint.pivot()));
                e.put("Start", point(endpoint.start()));
                e.put("Complete", point(endpoint.complete()));
                endpoints.add(e);
            }
            car.put("Endpoints", endpoints);
            cars.add(car);
        }
        tag.put("Carriages", cars);
        var connections = new ListTag();
        for (var connection : checkpoint.connections()) connections.add(pair(connection.first(), connection.second()));
        tag.put("Connections", connections);
        var approaches = new ListTag();
        for (var approach : checkpoint.approaches()) {
            var entry = pair(approach.first(), approach.second());
            number(entry, "Speed", approach.speedMps());
            approaches.add(entry);
        }
        tag.put("Approaches", approaches);
        tag.put("Placement", placement(checkpoint.placement(), checkpoint.bogeySpeeds(), checkpoint.motionDistances()));
        return tag;
    }

    public static VehicleRuntime load(CompoundTag tag) {
        return load(tag, TrackPath.straight());
    }

    /** The caller resolves the saved route on its owning backend before restoring motion. */
    public static VehicleRuntime load(CompoundTag tag, TrackPath path) {
        var checkpoint = readCheckpoint(tag);
        // Older poses predate bolsterless body fitting and independent rigid-hook head positions.
        return tag.getInt("Version") < STRICT_PLACEMENT_VERSION ? VehicleRuntime.restoreLegacyPlacement(checkpoint, path)
                : VehicleRuntime.restore(checkpoint, path);
    }

    /** Decode saved state without requiring its previous track backend to still exist. */
    public static VehicleRuntime.Checkpoint readCheckpoint(CompoundTag tag) {
        require(tag, "Version", Tag.TAG_INT);
        int version = tag.getInt("Version");
        if (version < 2 || version > VERSION)
            throw new IllegalArgumentException("Unknown vehicle runtime version");
        var cars = new ArrayList<VehicleRuntime.SavedCarriage>();
        for (var car : entries(tag, "Carriages")) {
            var bogeys = new ArrayList<BogeyDefinition>();
            var controls = new LinkedHashMap<UUID, VehicleRuntime.Control>();
            for (var b : entries(car, "Bogeys")) {
                var id = uuid(b, "Id");
                var parameters = new BogeyParameters(number(b,"LoadKg"), number(b,"TractionN"), number(b,"BrakeN"),
                        number(b,"ResistanceN"), number(b,"WheelRadius"), number(b,"DriveRatio"));
                require(b, "Axles", Tag.TAG_LIST);
                var axles = (ListTag) b.get("Axles");
                if (axles.isEmpty() || axles.getElementType() != Tag.TAG_LONG)
                    throw new IllegalArgumentException("Missing or invalid registered axle positions");
                var geometry = new BogeyGeometry(number(b, "GeometryWheelRadius"),
                        axles.stream().map(t -> Double.longBitsToDouble(((LongTag)t).getAsLong())).toList());
                var mount = new BogeyMount(point(b, "BodyMount"), point(b, "TrackPivot"),
                        BogeyMount.Type.valueOf(string(b, "MountType")), number(b, "LateralLimit"));
                bogeys.add(new BogeyDefinition(id, string(b,"Model"), number(b,"Offset"), geometry, parameters, mount));
                controls.put(id, new VehicleRuntime.Control(number(b,"Throttle"), number(b,"Brake")));
            }
            var endpoints = new ArrayList<CouplingEndpoint>();
            for (var e : entries(car, "Endpoints")) {
                double maxExtension = version < 6 && !e.contains("MaxExtension")
                        ? CouplingEndpoint.DEFAULT_MAX_EXTENSION : number(e, "MaxExtension");
                String model = version < 7 && !e.contains("Model")
                        ? CouplingEndpoint.DEFAULT_MODEL : string(e, "Model");
                CouplerAdapter adapter = null;
                if (e.contains("Adapter")) {
                    require(e, "Adapter", Tag.TAG_COMPOUND);
                    var a = e.getCompound("Adapter");
                    var decoded = a.copy();
                    decoded.putDouble("Thickness", number(a, "Thickness"));
                    adapter = willow.train.hexie.blocks.coupler.CouplerAdapterData.decode(decoded);
                }
                endpoints.add(new CouplingEndpoint(uuid(e,"Id"), point(e,"Block"),
                        CouplingEndpoint.Facing.valueOf(string(e,"Facing")), point(e,"Pivot"), point(e,"Start"),
                        point(e,"Complete"), CouplingEndpoint.Kind.valueOf(string(e,"Kind")), maxExtension, model, adapter));
            }
            var definition = new CarriageDefinition(uuid(car,"Id"), bogeys, endpoints,
                    number(car,"RearExtent"), number(car,"FrontExtent"),
                    car.contains("FloorOffset") ? number(car, "FloorOffset") : 0);
            cars.add(new VehicleRuntime.SavedCarriage(definition, number(car,"Distance"), number(car,"Speed"), controls));
        }
        var connections = entries(tag,"Connections").stream()
                .map(e -> new VehicleRuntime.Connection(uuid(e,"First"), uuid(e,"Second"))).toList();
        var approaches = entries(tag,"Approaches").stream()
                .map(e -> new VehicleRuntime.Approach(uuid(e,"First"), uuid(e,"Second"), number(e,"Speed"))).toList();
        require(tag, "Placement", Tag.TAG_COMPOUND);
        var placement = tag.getCompound("Placement");
        var bodies = new LinkedHashMap<UUID, VehicleConstraintSolver.BodyPose>();
        for (var entry : entries(placement, "Bodies")) unique(bodies, uuid(entry, "Id"),
                new VehicleConstraintSolver.BodyPose(vector(entry, "Position"), number(entry, "Yaw"),
                        number(entry, "Pitch"), number(entry, "Roll")));
        var positions = new LinkedHashMap<UUID, VehicleConstraintSolver.BogeyPosition>();
        var speeds = new LinkedHashMap<UUID, Double>();
        var motionDistances = new LinkedHashMap<UUID, Double>();
        for (var entry : entries(placement, "Bogeys")) {
            var id = uuid(entry, "Id");
            var sample = TrackGeometry.Sample.restoreNormalized(vector(entry, "TrackPosition"), vector(entry, "TrackTangent"),
                    vector(entry, "TrackNormal"), number(entry, "Cant"));
            unique(positions, id, new VehicleConstraintSolver.BogeyPosition(number(entry, "Distance"),
                    number(entry, "Lateral"), number(entry, "Vertical"), sample,
                    vector(entry, "Pivot"), vector(entry, "Attachment"), vector(entry, "Position"),
                    number(entry, "SuspensionLateral"), vector(entry, "OffRailOffset")));
            double speed = number(entry, "Speed");
            if (!Double.isFinite(speed)) throw new IllegalArgumentException("Non-finite saved bogey speed");
            unique(speeds, id, speed);
            unique(motionDistances, id, version < 6 && !entry.contains("MotionDistance")
                    ? number(entry, "Distance") : number(entry, "MotionDistance"));
        }
        var solvedEndpoints = new LinkedHashMap<UUID, VehicleConstraintSolver.EndpointPose>();
        for (var entry : entries(placement, "Endpoints")) unique(solvedEndpoints, uuid(entry, "Id"),
                new VehicleConstraintSolver.EndpointPose(vector(entry, "Pivot"), vector(entry, "Start"), vector(entry, "Complete")));
        var solution = new VehicleConstraintSolver.Solution(bodies, positions, solvedEndpoints);
        return new VehicleRuntime.Checkpoint(cars, connections, approaches, solution, speeds, motionDistances);
    }

    private static CompoundTag placement(VehicleConstraintSolver.Solution solution, Map<UUID, Double> speeds,
                                        Map<UUID, Double> motionDistances) {
        var tag = new CompoundTag();
        var bodies = new ListTag();
        // Map equality ignores iteration order; NBT lists do not. UUID hash collisions
        // can reorder Map.copyOf on load, so serialize these lookup maps canonically.
        new TreeMap<>(solution.bodies()).forEach((id, pose) -> {
            var entry = new CompoundTag(); entry.putUUID("Id", id);
            entry.put("Position", vector(pose.position())); number(entry, "Yaw", pose.yaw());
            number(entry, "Pitch", pose.pitch()); number(entry, "Roll", pose.roll()); bodies.add(entry);
        });
        tag.put("Bodies", bodies);
        var bogeys = new ListTag();
        new TreeMap<>(solution.bogeys()).forEach((id, position) -> {
            var entry = new CompoundTag(); entry.putUUID("Id", id);
            number(entry, "Distance", position.distance()); number(entry, "Lateral", position.lateral());
            number(entry, "MotionDistance", motionDistances.get(id));
            number(entry, "Vertical", position.vertical()); number(entry, "Speed", speeds.get(id));
            entry.put("Pivot", vector(position.pivot())); entry.put("Attachment", vector(position.attachment()));
            entry.put("Position", vector(position.position())); number(entry, "SuspensionLateral", position.suspensionLateral());
            entry.put("OffRailOffset", vector(position.offRailOffset()));
            entry.put("TrackPosition", vector(position.track().position()));
            entry.put("TrackTangent", vector(position.track().tangent())); entry.put("TrackNormal", vector(position.track().normal()));
            number(entry, "Cant", position.track().cantRadians()); bogeys.add(entry);
        });
        tag.put("Bogeys", bogeys);
        var endpoints = new ListTag();
        new TreeMap<>(solution.endpoints()).forEach((id, pose) -> {
            var entry = new CompoundTag(); entry.putUUID("Id", id);
            entry.put("Pivot", vector(pose.pivot())); entry.put("Start", vector(pose.start()));
            entry.put("Complete", vector(pose.complete())); endpoints.add(entry);
        });
        tag.put("Endpoints", endpoints);
        return tag;
    }
    private static <T> void unique(Map<UUID, T> values, UUID id, T value) {
        if (values.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate saved placement UUID: " + id);
    }
    private static CompoundTag vector(Vector value) { return point(new CouplingEndpoint.Point(value.x(), value.y(), value.z())); }
    private static Vector vector(CompoundTag tag, String key) { var p = point(tag, key); return new Vector(p.x(), p.y(), p.z()); }

    private static CompoundTag pair(UUID first, UUID second) {
        var tag = new CompoundTag(); tag.putUUID("First", first); tag.putUUID("Second", second); return tag;
    }
    private static CompoundTag point(CouplingEndpoint.Point point) {
        var tag = new CompoundTag(); number(tag, "X", point.x()); number(tag, "Y", point.y()); number(tag, "Z", point.z()); return tag;
    }
    private static CouplingEndpoint.Point point(CompoundTag tag, String key) {
        require(tag,key,Tag.TAG_COMPOUND); var p = tag.getCompound(key);
        return new CouplingEndpoint.Point(number(p,"X"), number(p,"Y"), number(p,"Z"));
    }
    private static List<CompoundTag> entries(CompoundTag tag, String key) {
        require(tag,key,Tag.TAG_LIST); var list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid " + key);
        return list.stream().map(t -> (CompoundTag)t).toList();
    }
    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw new IllegalArgumentException("Invalid UUID: " + key); return tag.getUUID(key);
    }
    private static String string(CompoundTag tag, String key) { require(tag,key,Tag.TAG_STRING); return tag.getString(key); }
    // Version 2 stores IEEE 754 bits: DoubleTag canonicalizes -0.0 to +0.0, altering solver seeds.
    private static void number(CompoundTag tag, String key, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite runtime field: " + key);
        tag.putLong(key, Double.doubleToRawLongBits(value));
    }
    private static double number(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LONG);
        double value = Double.longBitsToDouble(tag.getLong(key));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite runtime field: " + key);
        return value;
    }
    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key,type)) throw new IllegalArgumentException("Missing or invalid runtime field: " + key);
    }
}
