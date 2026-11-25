package willow.train.hexie.compat.create.assembly;

import java.util.LinkedHashMap;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.CreateTrainBridge;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.compat.create.track.CreateTrainTrackPoints;

// split from CreateTrainBridge.java
public final class CreateTrainPlacement {
    private final CreateTrainBridge trainBridge;

    public CreateTrainPlacement(CreateTrainBridge trainBridge) { this.trainBridge = trainBridge; }

    public boolean detectRelocation(CompatibilityTrain train) {
        if (train.graph == null || train.derailed) return false;
        boolean graphChanged = !CreateTrackCompatibility.sameNetwork(train.graph, trainBridge.motion.route().graph());
        boolean pointsChanged = false;
        points: for (UUID id : train.carriageIds) for (var binding : trainBridge.trackPoints.carriagePoints.get(id)) {
            if (binding.changed()) { pointsChanged = true; break points; }
        }
        if (!graphChanged && !pointsChanged) return false;
        for (UUID id : train.carriageIds) {
            for (var binding : trainBridge.trackPoints.carriagePoints.get(id)) {
                if (!graphChanged && !binding.changed()) continue;
                // Native graph merge/split can replace only train.graph while retaining every
                // travelling-point object. Try an unchanged-placement migration before treating
                // native point changes as an explicit wrench placement.
                var occupied = train.carriageIds.stream().flatMap(carriage -> trainBridge.trackPoints.carriagePoints.get(carriage).stream())
                        .map(point -> point.point).toList();
                var route = CreateRouteBinding.fromPoints(trainBridge.motion.level(), train.graph, binding.point, occupied, false);
                double oldDistance = Math.clamp(binding.distance, trainBridge.motion.route().path().minimumDistance(),
                        trainBridge.motion.route().path().maximumDistance());
                if (trainBridge.motion.rebindRoute(route, route.distanceOf(binding.point) - oldDistance)) {
                    trainBridge.routeUpdates.extensionAttempts.clear();
                    finishRelocation();
                    return true;
                }
                if (!pointsChanged) return false;
                var target = new LinkedHashMap<UUID, Double>();
                for (UUID carriage : train.carriageIds) {
                    var car = trainBridge.definitions.get(carriage);
                    var nativeCar = trainBridge.carriages.get(carriage);
                    var bogey = nativeCar.leadingBogey();
                    var rear = nativeCar.trailingBogey();
                    if (trainBridge.binding(rear.leading()).bogey.equals(car.bogeys().getFirst().id())) bogey = rear;
                    var representedId = trainBridge.binding(bogey.leading()).bogey;
                    var represented = car.bogeys().stream().filter(b -> b.id().equals(representedId)).findFirst().orElseThrow();
                    target.put(carriage, CreateTrainTrackPoints.wheelCentre(route, bogey) - represented.offset() - represented.mount().trackPivot().z());
                }
                trainBridge.motion.relocate(route, target);
                finishRelocation();
                return true;
            }
        }
        return false;
    }

    public void finishRelocation() {
        if (trainBridge.removed) return;
        for (var train : trainBridge.trains.values()) {
            if (train.graph == null) continue;
            train.graph = trainBridge.motion.route().graph();
            trainBridge.sync(train);
            train.updateSignalBlocks = true;
        }
    }

    public boolean canDisassemble(CompatibilityTrain train) {
        return CreateVehicleDisassembly.canDisassemble(trainBridge.motion.level(), train);
    }

    /** Called by Create's original station action, which returns the schedule only after success. */
    public boolean disassemble(CompatibilityTrain train) {
        var current = train.getCurrentStation();
        if (current == null || current.getPresentTrain() != train
                || !(trainBridge.motion.level().getBlockEntity(current.getBlockEntityPos()) instanceof StationBlockEntity station))
            return false;
        var access = (CreateAssemblyStationAccess) station;
        try {
            CreateVehicleDisassembly.disassemble(station, train);
        } catch (RuntimeException failure) {
            access.hexie$setAssemblyFailure(failure);
            return false;
        }
        // World placement is committed. UI notifications cannot turn this into a failed operation.
        station.lastDisassembledTrainName = train.name.copy();
        station.lastDisassembledMapColorIndex = train.mapColorIndex;
        try {
            access.hexie$setAssemblyId(null);
            access.hexie$setAssemblyFailure(null);
        } catch (RuntimeException notification) {
            Create.LOGGER.warn("Could not notify station after dismantling HeXie train {}", train.id, notification);
        }
        return true;
    }
}
