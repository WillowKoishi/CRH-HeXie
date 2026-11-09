package willow.train.hexie.compat.create;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.AddTrainPacket;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.vehicle.assembly.BogeyModelCatalog;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.assembly.VehicleAssemblyPreparer;
import willow.train.hexie.vehicle.assembly.VehicleCouplingCatalog;
import willow.train.hexie.vehicle.runtime.BogeyDefinition;

/** An imported body without track uses Create's ordinary detached-train lifecycle. */
final class ImportedCarriageCompatibility {
    private ImportedCarriageCompatibility() {}

    static CompatibilityTrain bind(SnapshotCarriageEntity carrier, VehicleAssemblyPreparer.Prepared prepared, UUID owner) {
        if (!(carrier.level() instanceof ServerLevel level) || prepared.carriages().size() != 1
                || !prepared.carriages().getFirst().definition().id().equals(carrier.getUUID()))
            throw new IllegalArgumentException("Detached import requires one matching server carriage");
        UUID trainId = UUID.nameUUIDFromBytes(("hexie:consist:" + carrier.getUUID()).getBytes(StandardCharsets.UTF_8));
        if (Create.RAILWAYS.trains.containsKey(trainId))
            throw new IllegalStateException("Imported carriage already has a compatibility train");
        var preparedCarriage = prepared.carriages().getFirst();
        var ordered = preparedCarriage.definition().bogeys().stream()
                .sorted(Comparator.comparingDouble(BogeyDefinition::offset)).toList();
        var front = ordered.getFirst();
        var back = ordered.getLast();
        var carriage = new CompatibilityTrain.ReferenceCarriage(bogey(preparedCarriage, front),
                front == back ? null : bogey(preparedCarriage, back), (int) Math.round(back.offset() - front.offset()));
        var train = new CompatibilityTrain(trainId, owner, carrier.assemblyId(), List.of(carrier.getUUID()),
                null, List.of(carriage), List.of());
        train.derailed = true;
        train.markImportedWithoutRoute(level);
        SnapshotCarriageEntity.syncTrackAnchors(carriage, level, carrier.currentBodyPose());
        CreateTrackCompatibility.synchronizeGraph(train);
        Create.RAILWAYS.addTrain(train);
        carrier.bindCompatibility(carriage, 0);
        CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(train));
        Create.RAILWAYS.markTracksDirty();
        return train;
    }

    private static CarriageBogey bogey(VehicleAssemblyPreparer.Carriage carriage, BogeyDefinition definition) {
        int index = carriage.definition().bogeys().indexOf(definition);
        var position = carriage.structure().supports().get(index);
        var block = carriage.structure().blocks().stream().filter(value -> value.position().equals(position))
                .findFirst().orElseThrow();
        var support = willow.train.hexie.vehicle.assembly.AssemblyBogeySupport.resolve(carriage.structure(), position);
        if (support != null) block = support.capturedBlock();
        AbstractBogeyBlock<?> type = block.state().isAir() ? com.simibubi.create.AllBlocks.SMALL_BOGEY.get()
                : block.state().getBlock() instanceof AbstractBogeyBlock<?> value ? value : null;
        if (type == null) throw new IllegalStateException("Imported bogey has no registered Create block");
        CompoundTag data = block.entityData() == null ? new CompoundTag() : block.entityData().getCompound("BogeyData").copy();
        return new CarriageBogey(type, false, data, new TravellingPoint(), new TravellingPoint());
    }

    /** Only a successful native relocation supplies track points; proximity never re-rails the body. */
    static void recoverFromNativePoints(CompatibilityTrain train, ServerLevel server) {
        if (!train.importedWithoutRoute || train.bridge != null || train.graph == null || train.derailed) return;
        var nativeCarriage = train.carriages.getFirst();
        SnapshotCarriageEntity carrier = null;
        for (var dimension : nativeCarriage.getPresentDimensions()) {
            var level = server.getServer().getLevel(dimension);
            if (level != null && level.getEntity(train.carriageIds.getFirst()) instanceof SnapshotCarriageEntity found) {
                carrier = found;
                break;
            }
        }
        if (carrier == null) return;
        var level = (ServerLevel) carrier.level();
        var saved = CarriageSnapshotStore.get(level).find(carrier.getUUID()).orElseThrow();
        var blocks = carrier.currentBlockData().values().stream()
                .map(info -> new CarriageSnapshot.Block(info.pos(), info.state(), info.nbt())).toList();
        var current = new CarriageSnapshot(carrier.getUUID(), level.dimension(), saved.origin(), blocks,
                saved.supports(), saved.glue());
        var prepared = VehicleAssemblyPreparer.prepare(List.of(current), carrier.originalForward(),
                BogeyModelCatalog.production(), VehicleCouplingCatalog::decode);
        var points = new ArrayList<TravellingPoint>();
        train.forEachTravellingPoint(points::add);
        if (points.stream().anyMatch(point -> point.node1 == null || point.node2 == null || point.edge == null)) return;
        CreateRouteBinding route;
        try {
            route = CreateRouteBinding.fromPoints(level, train.graph, nativeCarriage.getLeadingPoint(), points, false);
        } catch (CreateRouteBinding.RouteInvalidException pending) {
            return;
        }
        var front = prepared.carriages().getFirst().definition().bogeys().stream()
                .min(Comparator.comparingDouble(BogeyDefinition::offset)).orElseThrow();
        double origin = CreateTrainBridge.wheelCentre(route, nativeCarriage.leadingBogey())
                - front.offset() - front.mount().trackPivot().z();
        route = CreateRouteBinding.selected(level, route.graphId(), route.nodes(), origin);
        var motion = AssembledVehicleMotion.create(level, train.assemblyId, prepared, route);
        carrier.setMotionOwner(carrier.getUUID(), carrier.originalForward());
        carrier.installMotion(motion);
        motion.applyCurrent(carrier);
        // The canonical train identity transfers the same native carriage, cargo and timetable.
        motion.ensureCompatibility();
        Create.RAILWAYS.markTracksDirty();
    }
}
