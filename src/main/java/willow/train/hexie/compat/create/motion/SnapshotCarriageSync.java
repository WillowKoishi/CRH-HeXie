package willow.train.hexie.compat.create.motion;

import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.BASIS_FORWARD;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.COMPATIBILITY_DERAILED;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.COMPATIBILITY_GRAPH;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.COMPATIBILITY_GRAPHS;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.COMPATIBILITY_INDEX;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.COMPATIBILITY_POINTS;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.COMPATIBILITY_SPEED;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.COMPATIBILITY_TRAIN;
import static willow.train.hexie.compat.create.motion.SnapshotCarriageEntity.MOTION_FRAME;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.cee.CeeSnapshotTrain;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageCargo;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageStructure;
import willow.train.hexie.compat.create.interaction.ContraptionInteractionTargets;
import willow.train.hexie.compat.create.signal.CreateCabDisplaySource;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetable;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.core.api.cab.CabDisplayStateNbt;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageSync {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageSync(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    public Carriage compatibilityCarriage;
    private boolean compatibilitySyncPending;

    /** The compatibility record points at this same entity and inventory; it never owns another body. */
    public void bindCompatibility(Carriage carriage, int index) {
        if (carriage == null || carriage.train == null) throw new IllegalArgumentException("Missing compatibility train");
        compatibilityCarriage = carriage;
        carriageEntity.trainId = carriage.train.id;
        carriageEntity.carriageIndex = index;
        if (!carriageEntity.level().isClientSide && carriage.train instanceof CompatibilityTrain train
                && index >= 0 && index < train.carriageIds.size() && train.carriageIds.get(index).equals(carriageEntity.getUUID())
                && train.bridge != null && train.bridge.motion().owns(carriageEntity.getUUID()))
            carriageEntity.assemblyId = train.bridge.motion().sourceAssembly(carriageEntity.getUUID());
        if (carriageEntity.getContraption() instanceof SnapshotCarriageStructure.SnapshotView view) {
            if (carriageEntity.nativeManaged && !carriageEntity.level().isClientSide) {
                SnapshotCarriageCargo.preparePersistentCargo(carriage, (ServerLevel) carriageEntity.level());
                view.storageFromCarriage(carriage);
            } else carriage.storage = view.cargo();
        }
        carriageEntity.nativeManaged = true;
        var dimensional = carriage.getDimensional(carriageEntity.level());
        dimensional.entity = new WeakReference<>(carriageEntity);
        dimensional.pointsInitialised = true;
        dimensional.pivot = null;
        if (dimensional.positionAnchor == null) dimensional.positionAnchor = carriageEntity.position();
        if (dimensional.rotationAnchors.either(java.util.Objects::isNull))
            dimensional.rotationAnchors = Couple.create(carriageEntity.position(), carriageEntity.position());
        carriageEntity.validForRender = true;
        carriageEntity.firstPositionUpdate = false;
        if (carriageEntity.level().isClientSide) compatibilitySyncPending = true;
        else {
            if (net.neoforged.fml.ModList.get().isLoaded("electroenergetics")) {
                CeeSnapshotTrain.bind(carriageEntity, carriage);
            }
            CreateConsoleTimetable.bindStored(carriageEntity, carriage.train);
            if (carriageEntity.restoringNativeEntity && carriageEntity.motion() != null) {
                carriageEntity.motion().applyCurrent(carriageEntity);
                carriageEntity.prevYaw = carriageEntity.yaw; carriageEntity.prevPitch = carriageEntity.pitch; carriageEntity.prevRoll = carriageEntity.roll;
                carriageEntity.xo = carriageEntity.getX(); carriageEntity.yo = carriageEntity.getY(); carriageEntity.zo = carriageEntity.getZ();
                carriageEntity.restoringNativeEntity = false;
            }
            syncCarriage();
        }
        // Native entity restoration also binds here before alignEntity, even while its
        // track graph is unavailable. In that case roll comes from the saved BodyRoll.
        CreateTrackCompatibility.synchronizePose(dimensional, Math.toRadians(carriageEntity.roll));
    }

    public Carriage getCarriage() { return compatibilityCarriage; }

    public void setCarriage(Carriage carriage) {
        bindCompatibility(carriage, carriage.train.carriages.indexOf(carriage));
    }

    public void bindClientCompatibility() {
        var syncedTrain = carriageEntity.getEntityData().get(COMPATIBILITY_TRAIN);
        if (syncedTrain.isPresent()) {
            carriageEntity.trainId = syncedTrain.get();
            carriageEntity.carriageIndex = carriageEntity.getEntityData().get(COMPATIBILITY_INDEX);
        }
        if (carriageEntity.trainId == null) return;
        var train = Create.RAILWAYS.sided(carriageEntity.level()).trains.get(carriageEntity.trainId);
        if (train == null || carriageEntity.carriageIndex < 0 || carriageEntity.carriageIndex >= train.carriages.size()) return;
        var carriage = train.carriages.get(carriageEntity.carriageIndex);
        if (compatibilityCarriage != carriage) bindCompatibility(carriage, carriageEntity.carriageIndex);
        if (compatibilitySyncPending) applyCompatibilityPoints();
    }

    public CarriageSyncData getCarriageData() { return carriageEntity.getEntityData().get(COMPATIBILITY_POINTS); }

    public void syncCarriage() {
        if (carriageEntity.level().isClientSide) return;
        long now = carriageEntity.level().getGameTime();
        if (carriageEntity.motionFrameTick == Long.MIN_VALUE || now - carriageEntity.motionFrameTick >= Math.max(1, carriageEntity.getType().updateInterval())) {
            // Native lifecycle alignment changes the body before calling this method.
            // Refresh every animated part from that same authoritative solution first.
            var owner = carriageEntity.motion();
            if (owner != null) owner.applyCurrent(carriageEntity);
            var frame = new CompoundTag();
            // Boarding history can reuse a stopped pose's stamp. SampleTick below is
            // the real sampling time, and advances only when a changed frame is sent.
            frame.putLong("PoseTick", carriageEntity.boarding.boardingHistory.record(now,
                    carriageEntity.position(), carriageEntity.motionVelocity, SnapshotCarriagePlayback.motionFrameRotation(carriageEntity.yaw, carriageEntity.pitch, carriageEntity.roll, carriageEntity.originalForward)));
            var normalDisplay = CreateCabDisplaySource.read(owner, carriageEntity.getUUID(), false);
            frame.put("CabDisplay", CabDisplayStateNbt.write(normalDisplay));
            if (owner != null) frame.put("CabControls", carriageEntity.cabDisplayFrame(owner, normalDisplay));
            frame.putDouble("X", carriageEntity.getX()); frame.putDouble("Y", carriageEntity.getY()); frame.putDouble("Z", carriageEntity.getZ());
            frame.putFloat("Yaw", carriageEntity.yaw); frame.putFloat("Pitch", carriageEntity.pitch); frame.putFloat("Roll", carriageEntity.roll);
            frame.putString("Forward", carriageEntity.originalForward.getName());
            frame.putDouble("Vx", carriageEntity.motionVelocity.x); frame.putDouble("Vy", carriageEntity.motionVelocity.y); frame.putDouble("Vz", carriageEntity.motionVelocity.z);
            frame.put("Bogeys", carriageEntity.bogeyAnimation.write()); frame.put("Couplers", carriageEntity.couplerAnimation.write());
            var settings = new ListTag();
            if (carriageEntity.getContraption() != null) for (var pos : ContraptionInteractionTargets.bogeys(carriageEntity.getContraption())) {
                var support = HexieBridges.vehicle().assemblyBogeySupport().resolve(carriageEntity.getContraption().getBlocks(), pos);
                if (support == null) continue;
                var data = support.entityData();
                var entry = new CompoundTag(); entry.putLong("Pos", pos.asLong());
                entry.putInt("Height", HexieBridges.worldBlock().bogeyFloorData().value(data));
                entry.putInt("Longitudinal", HexieBridges.worldBlock().bogeyFloorData().longitudinalFromEntity(data));
                entry.putBoolean("Reversed", HexieBridges.worldBlock().bogeyFloorData().reversedFromEntity(data));
                settings.add(entry);
            }
            frame.put("BogeySettings", settings);
            var previousFrame = carriageEntity.getEntityData().get(MOTION_FRAME);
            boolean changed = previousFrame.size() != frame.size() + 1 || !previousFrame.contains("SampleTick");
            if (!changed) for (var key : frame.getAllKeys())
                if (!java.util.Objects.equals(frame.get(key), previousFrame.get(key))) { changed = true; break; }
            if (changed) {
                frame.putLong("SampleTick", now);
                carriageEntity.getEntityData().set(MOTION_FRAME, frame);
            }
            carriageEntity.motionFrameTick = now;
        }
        if (compatibilityCarriage == null) return;
        var train = compatibilityCarriage.train;
        carriageEntity.getEntityData().set(COMPATIBILITY_TRAIN, Optional.of(train.id));
        carriageEntity.getEntityData().set(COMPATIBILITY_INDEX, carriageEntity.carriageIndex);
        carriageEntity.getEntityData().set(COMPATIBILITY_GRAPH, Optional.ofNullable(train.graph).map(graph -> graph.id));
        var graphTag = new CompoundTag();
        var graphIds = new ListTag();
        for (var id : CreateTrackCompatibility.graphIds(train.graph)) {
            var entry = new CompoundTag(); entry.putUUID("Id", id); graphIds.add(entry);
        }
        graphTag.put("Graphs", graphIds);
        carriageEntity.getEntityData().set(COMPATIBILITY_GRAPHS, graphTag);
        carriageEntity.getEntityData().set(COMPATIBILITY_SPEED, (float) train.speed);
        carriageEntity.getEntityData().set(COMPATIBILITY_DERAILED, train.derailed);
        var data = new CarriageSyncData();
        data.update(carriageEntity, compatibilityCarriage);
        carriageEntity.getEntityData().set(COMPATIBILITY_POINTS, data);
    }

    public void applyCompatibilityPoints() {
        var carriage = compatibilityCarriage;
        var train = carriage.train;
        var graphId = carriageEntity.getEntityData().get(COMPATIBILITY_GRAPH);
        var graphIds = new ArrayList<UUID>();
        for (var item : carriageEntity.getEntityData().get(COMPATIBILITY_GRAPHS).getList("Graphs", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) item;
            if (!entry.hasUUID("Id")) return;
            graphIds.add(entry.getUUID("Id"));
        }
        if (graphIds.isEmpty()) graphId.ifPresent(graphIds::add);
        var graph = CreateTrackCompatibility.resolveGraphs(Create.RAILWAYS.sided(carriageEntity.level()), graphIds);
        if (graphId.isPresent() && graph == null) return;
        var data = getCarriageData();
        if (data.fallbackLocations == null) {
            if (graph == null) return;
            // Graph packets may follow the entity packet. Wait rather than marking partially
            // initialized points as valid or running Create's interpolation path search.
            for (var wheel : data.wheelLocations) {
                if (wheel == null) break;
                var first = graph.getNode(wheel.getFirst().getFirst());
                var second = graph.getNode(wheel.getFirst().getSecond());
                if (first == null || second == null || graph.getConnectionsFrom(first) == null
                        || !graph.getConnectionsFrom(first).containsKey(second)) return;
            }
        }
        train.graph = graph;
        train.speed = carriageEntity.getEntityData().get(COMPATIBILITY_SPEED);
        train.derailed = carriageEntity.getEntityData().get(COMPATIBILITY_DERAILED);
        train.currentlyBackwards = train.speed < 0;
        var dimensional = carriage.getDimensional(carriageEntity.level());
        dimensional.pointsInitialised = false;
        CreateTrackCompatibility.prepareClientSync(data, graph);
        data.apply(carriageEntity, carriage);
        if (data.fallbackLocations == null) {
            CreateTrackCompatibility.clientPointsApplied(carriage, graph);
            carriage.updateContraptionAnchors();
        }
        if (data.fallbackLocations != null) {
            dimensional.positionAnchor = data.fallbackLocations.getFirst();
            dimensional.rotationAnchors = Couple.create(data.fallbackLocations.getSecond().getFirst(),
                    data.fallbackLocations.getSecond().getSecond());
        }
        CreateTrackCompatibility.synchronizePose(dimensional, Math.toRadians(carriageEntity.roll));
        compatibilitySyncPending = false;
    }

    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        // Preserve Entity's only callback action. Calling the carriage implementation would
        // bind its private carriage and apply Create's separate travelling-point interpolation.
        if (carriageEntity.isPoseData(key)) carriageEntity.refreshDimensions();
        if (key.equals(BASIS_FORWARD) && carriageEntity.level().isClientSide) {
            carriageEntity.setCoordinateBasis(carriageEntity.getEntityData().get(BASIS_FORWARD));
            carriageEntity.setInitialOrientation(carriageEntity.originalForward.getClockWise());
            if (carriageEntity.getContraption() instanceof SnapshotCarriageStructure.SnapshotView view) view.configureDirection(carriageEntity.originalForward);
        }
        if (key.equals(MOTION_FRAME) && carriageEntity.level().isClientSide) {
            var frame = carriageEntity.getEntityData().get(MOTION_FRAME);
            if (!frame.contains("X")) return;
            boolean initial = !carriageEntity.playback.poseInterpolation.initialized();
            if (!carriageEntity.queueClientPose(frame)) return;
            if (initial) {
                carriageEntity.applyClientMotionFrame(frame);
                carriageEntity.applyClientPose();
                carriageEntity.xo = carriageEntity.getX(); carriageEntity.yo = carriageEntity.getY(); carriageEntity.zo = carriageEntity.getZ();
                carriageEntity.prevYaw = carriageEntity.yaw; carriageEntity.prevPitch = carriageEntity.pitch; carriageEntity.prevRoll = carriageEntity.roll;
            } else carriageEntity.playback.pendingClientMotionFrame = frame.copy();
        }
        if (key.equals(COMPATIBILITY_TRAIN) && carriageEntity.level().isClientSide) carriageEntity.bindClientPoseTimeline();
        if (key.equals(COMPATIBILITY_POINTS) || key.equals(COMPATIBILITY_GRAPH) || key.equals(COMPATIBILITY_GRAPHS)
                || key.equals(COMPATIBILITY_TRAIN) || key.equals(COMPATIBILITY_INDEX)
                || key.equals(COMPATIBILITY_SPEED) || key.equals(COMPATIBILITY_DERAILED))
            compatibilitySyncPending = true;
    }

    static void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MOTION_FRAME, new CompoundTag());
        builder.define(BASIS_FORWARD, Direction.NORTH);
        builder.define(COMPATIBILITY_POINTS, new CarriageSyncData());
        builder.define(COMPATIBILITY_GRAPH, Optional.empty());
        builder.define(COMPATIBILITY_GRAPHS, new CompoundTag());
        builder.define(COMPATIBILITY_TRAIN, Optional.empty());
        builder.define(COMPATIBILITY_INDEX, 0);
        builder.define(COMPATIBILITY_SPEED, 0f);
        builder.define(COMPATIBILITY_DERAILED, false);
    }
}
