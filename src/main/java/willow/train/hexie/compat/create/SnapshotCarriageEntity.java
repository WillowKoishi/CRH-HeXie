package willow.train.hexie.compat.create;

import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.api.cab.CabDisplayStateNbt;

import java.util.Collection;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.tuple.MutablePair;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.Create;
import com.simibubi.create.AllEntityDataSerializers;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.ContraptionBlockChangedPacket;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.simibubi.create.content.contraptions.minecart.TrainCargoManager;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.contraptions.render.ClientContraption.RenderedBlocks;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.collision.Matrix3d;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.init.EntityInit;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.assembly.CarriageFloorView;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;

/**
 * A Create-compatible carriage identity over our only live snapshot and movement owner.
 * Our runtime owns movement; Create's logical carriage owns unloading, cargo and recreation.
 */
public final class SnapshotCarriageEntity extends CarriageContraptionEntity {
    private static final EntityDataAccessor<CompoundTag> MOTION_FRAME = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Direction> BASIS_FORWARD = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<CarriageSyncData> COMPATIBILITY_POINTS = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, AllEntityDataSerializers.CARRIAGE_DATA);
    private static final EntityDataAccessor<Optional<UUID>> COMPATIBILITY_GRAPH = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> COMPATIBILITY_TRAIN = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> COMPATIBILITY_INDEX = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> COMPATIBILITY_SPEED = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> COMPATIBILITY_DERAILED = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID assemblyId;
    private UUID motionOwner;
    private AssembledVehicleMotion ownedMotion;
    private CompoundTag pendingMotionState;
    private int nextMotionRetryTick;
    private SnapshotCarriageEntity cachedOwner;
    private CarriageSnapshot snapshot;
    private Direction originalForward = Direction.NORTH;
    private boolean behavioursActive;
    private boolean usingWorkPose;
    private BodyPose workPose;
    private float roll, prevRoll;
    private Vec3 interpolationTarget;
    private int interpolationSteps;
    private CompoundTag pendingClientMotionFrame;
    private CabDisplayState cabDisplayState = CabDisplayState.speedOnly(0);
    public double displaySpeedKmh() { return cabDisplayState.speedKmh(); }
    public CabDisplayState cabDisplayState() { return cabDisplayState; }
    private CompoundTag testCabFrame = new CompoundTag();
    public willow.train.hexie.vehicle.control.TestCabControls testCabControls(BlockPos position) {
        return testCabFrame.contains("Position") && testCabFrame.getLong("Position") == position.asLong()
                ? willow.train.hexie.vehicle.control.TestCabControls.read(testCabFrame.getCompound("Controls"))
                : new willow.train.hexie.vehicle.control.TestCabControls();
    }
    private final CarriagePoseInterpolation poseInterpolation = new CarriagePoseInterpolation();
    private float targetYaw, targetPitch, targetRoll;
    private Vec3 motionSamplePosition, previousMotionPosition, olderMotionPosition, motionVelocity = Vec3.ZERO;
    private long motionSampleTick = Long.MIN_VALUE;
    private long motionFrameTick = Long.MIN_VALUE;
    private boolean continuousMotionSamples;
    private Carriage compatibilityCarriage;
    private boolean compatibilitySyncPending;
    private final SnapshotBogeyAnimation bogeyAnimation = new SnapshotBogeyAnimation();
    private final SnapshotCouplerAnimation couplerAnimation = new SnapshotCouplerAnimation();
    private StructureTransform nativeDisassemblyTransform;
    private AABB floorBoundsSource;
    private AABB floorBounds;
    private double boundsFloorOffset = Double.NaN;
    private boolean nativeManaged;
    private boolean restoringNativeEntity;

    static void preparePersistentCargo(Carriage carriage, ServerLevel level) {
        if (carriage.storage instanceof SnapshotCargoManager) return;
        var data = new CompoundTag();
        carriage.storage.write(data, level.registryAccess(), false);
        var cargo = new SnapshotCargoManager();
        cargo.read(data, level.registryAccess(), false, null);
        carriage.storage = cargo;
    }

    public SnapshotCarriageEntity(EntityType<?> type, Level level) { super(type, level); }

    public static SnapshotCarriageEntity create(ServerLevel level, UUID assembly, CarriageSnapshot snapshot) {
        var entity = new SnapshotCarriageEntity(EntityInit.ASSEMBLED_CARRIAGE.get(), level);
        entity.assemblyId = assembly;
        entity.trainId = assembly;
        entity.setUUID(snapshot.id());
        entity.attach(snapshot, true);
        return entity;
    }

    public UUID assemblyId() { return assemblyId; }
    public Direction originalForward() { return originalForward; }
    public SnapshotBogeyAnimation bogeyAnimation() { return bogeyAnimation; }
    public SnapshotCouplerAnimation couplerAnimation() { return couplerAnimation; }

    /** The compatibility record points at this same entity and inventory; it never owns another body. */
    public void bindCompatibility(Carriage carriage, int index) {
        if (carriage == null || carriage.train == null) throw new IllegalArgumentException("Missing compatibility train");
        compatibilityCarriage = carriage;
        trainId = carriage.train.id;
        carriageIndex = index;
        if (contraption instanceof SnapshotView view) {
            if (nativeManaged && !level().isClientSide) {
                preparePersistentCargo(carriage, (ServerLevel) level());
                view.storageFromCarriage(carriage);
            } else carriage.storage = view.cargo();
        }
        nativeManaged = true;
        var dimensional = carriage.getDimensional(level());
        dimensional.entity = new WeakReference<>(this);
        dimensional.pointsInitialised = true;
        dimensional.pivot = null;
        if (dimensional.positionAnchor == null) dimensional.positionAnchor = position();
        if (dimensional.rotationAnchors.either(java.util.Objects::isNull))
            dimensional.rotationAnchors = Couple.create(position(), position());
        validForRender = true;
        firstPositionUpdate = false;
        if (level().isClientSide) compatibilitySyncPending = true;
        else {
            if (restoringNativeEntity && motion() != null) {
                motion().applyCurrent(this);
                prevYaw = yaw; prevPitch = pitch; prevRoll = roll;
                xo = getX(); yo = getY(); zo = getZ();
                restoringNativeEntity = false;
            }
            syncCarriage();
        }
    }

    @Override public Carriage getCarriage() { return compatibilityCarriage; }

    @Override public void setCarriage(Carriage carriage) {
        bindCompatibility(carriage, carriage.train.carriages.indexOf(carriage));
    }

    private void bindClientCompatibility() {
        var syncedTrain = entityData.get(COMPATIBILITY_TRAIN);
        if (syncedTrain.isPresent()) {
            trainId = syncedTrain.get();
            carriageIndex = entityData.get(COMPATIBILITY_INDEX);
        }
        if (trainId == null) return;
        var train = Create.RAILWAYS.sided(level()).trains.get(trainId);
        if (train == null || carriageIndex < 0 || carriageIndex >= train.carriages.size()) return;
        var carriage = train.carriages.get(carriageIndex);
        if (compatibilityCarriage != carriage) bindCompatibility(carriage, carriageIndex);
        if (compatibilitySyncPending) applyCompatibilityPoints();
    }

    @Override public CarriageSyncData getCarriageData() { return entityData.get(COMPATIBILITY_POINTS); }

    @Override public void syncCarriage() {
        if (level().isClientSide) return;
        long now = level().getGameTime();
        if (motionFrameTick == Long.MIN_VALUE || now - motionFrameTick >= Math.max(1, getType().updateInterval())) {
            // Native lifecycle alignment changes the body before calling this method.
            // Refresh every animated part from that same authoritative solution first.
            var owner = motion();
            if (owner != null) owner.applyCurrent(this);
            var frame = new CompoundTag();
            frame.put("CabDisplay", CabDisplayStateNbt.write(CreateCabDisplaySource.read(owner, getUUID())));
            if (owner != null) frame.put("TestCab", owner.testCabFrame(getUUID()));
            frame.putDouble("X", getX()); frame.putDouble("Y", getY()); frame.putDouble("Z", getZ());
            frame.putFloat("Yaw", yaw); frame.putFloat("Pitch", pitch); frame.putFloat("Roll", roll);
            frame.putString("Forward", originalForward.getName());
            frame.putDouble("Vx", motionVelocity.x); frame.putDouble("Vy", motionVelocity.y); frame.putDouble("Vz", motionVelocity.z);
            frame.put("Bogeys", bogeyAnimation.write()); frame.put("Couplers", couplerAnimation.write());
            entityData.set(MOTION_FRAME, frame);
            motionFrameTick = now;
        }
        if (compatibilityCarriage == null) return;
        var train = compatibilityCarriage.train;
        entityData.set(COMPATIBILITY_TRAIN, Optional.of(train.id));
        entityData.set(COMPATIBILITY_INDEX, carriageIndex);
        entityData.set(COMPATIBILITY_GRAPH, Optional.ofNullable(train.graph).map(graph -> graph.id));
        entityData.set(COMPATIBILITY_SPEED, (float) train.speed);
        entityData.set(COMPATIBILITY_DERAILED, train.derailed);
        var data = new CarriageSyncData();
        data.update(this, compatibilityCarriage);
        entityData.set(COMPATIBILITY_POINTS, data);
    }

    void applyCompatibilityPoints() {
        var carriage = compatibilityCarriage;
        var train = carriage.train;
        var graphId = entityData.get(COMPATIBILITY_GRAPH);
        var graph = graphId.map(id -> Create.RAILWAYS.sided(level()).trackNetworks.get(id)).orElse(null);
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
        train.speed = entityData.get(COMPATIBILITY_SPEED);
        train.derailed = entityData.get(COMPATIBILITY_DERAILED);
        train.currentlyBackwards = train.speed < 0;
        var dimensional = carriage.getDimensional(level());
        dimensional.pointsInitialised = false;
        data.apply(this, carriage);
        if (data.fallbackLocations != null) {
            dimensional.positionAnchor = data.fallbackLocations.getFirst();
            dimensional.rotationAnchors = Couple.create(data.fallbackLocations.getSecond().getFirst(),
                    data.fallbackLocations.getSecond().getSecond());
        }
        compatibilitySyncPending = false;
    }

    @Override public void updateRenderedPortalCutoff() { /* This carrier has one complete local structure. */ }

    @Override public boolean hasSchedule() {
        return compatibilityCarriage != null && compatibilityCarriage.train.runtime.getSchedule() != null;
    }

    @Override public Component getContraptionName() {
        return compatibilityCarriage == null ? super.getContraptionName() : compatibilityCarriage.train.name;
    }

    @Override public Component getDisplayName() {
        return compatibilityCarriage == null ? super.getDisplayName() : compatibilityCarriage.train.name;
    }

    @Override public boolean shouldBeSaved() {
        if (nativeManaged) return false;
        // Legacy/unbound entities retain their existing chunk save until attached to a logical car.
        return (getRemovalReason() == null || getRemovalReason().shouldSave()) && !isPassenger()
                && (!isVehicle() || !hasExactlyOnePlayerPassenger());
    }

    @Override public boolean isControlledByLocalInstance() {
        // Create suppresses vanilla entity movement packets; this carrier interpolates them.
        return !level().isClientSide;
    }

    @Override public Vec3 getPrevPositionVec() {
        return prevPosInvalid ? position() : new Vec3(xo, yo, zo);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        // Preserve Entity's only callback action. Calling the carriage implementation would
        // bind its private carriage and apply Create's separate travelling-point interpolation.
        if (DATA_POSE.equals(key)) refreshDimensions();
        if (key.equals(BASIS_FORWARD) && level().isClientSide) {
            setCoordinateBasis(entityData.get(BASIS_FORWARD));
            setInitialOrientation(originalForward.getClockWise());
            if (contraption instanceof SnapshotView view) view.configureDirection(originalForward);
        }
        if (key.equals(MOTION_FRAME) && level().isClientSide) {
            var frame = entityData.get(MOTION_FRAME);
            if (!frame.contains("X")) return;
            if (!poseInterpolation.initialized()) applyClientMotionFrame(frame);
            else pendingClientMotionFrame = frame.copy();
        }
        if (key.equals(COMPATIBILITY_POINTS) || key.equals(COMPATIBILITY_GRAPH)
                || key.equals(COMPATIBILITY_TRAIN) || key.equals(COMPATIBILITY_INDEX)
                || key.equals(COMPATIBILITY_SPEED) || key.equals(COMPATIBILITY_DERAILED))
            compatibilitySyncPending = true;
    }

    private void applyClientMotionFrame(CompoundTag frame) {
            cabDisplayState = frame.contains("CabDisplay", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    ? CabDisplayStateNbt.read(frame.getCompound("CabDisplay"))
                    : CabDisplayState.speedOnly(frame.getDouble("DisplaySpeedKmh"));
            testCabFrame = frame.getCompound("TestCab");
            setCoordinateBasis(Direction.byName(frame.getString("Forward")));
            boolean initial = !poseInterpolation.initialized();
            int steps = initial ? 0 : getType().updateInterval();
            targetYaw = frame.getFloat("Yaw"); targetPitch = frame.getFloat("Pitch"); targetRoll = frame.getFloat("Roll");
            poseInterpolation.accept(new Vec3(frame.getDouble("X"), frame.getDouble("Y"), frame.getDouble("Z")),
                    new Vec3(frame.getDouble("Vx"), frame.getDouble("Vy"), frame.getDouble("Vz")), steps);
            interpolationSteps = steps;
            readBogeyAnimation(frame.getCompound("Bogeys"), steps);
            readCouplerAnimation(frame.getCompound("Couplers"), steps);
            if (initial) {
                setPos(poseInterpolation.position(1));
                xo = getX(); yo = getY(); zo = getZ();
                yaw = prevYaw = targetYaw; pitch = prevPitch = targetPitch; roll = prevRoll = targetRoll;
            }
    }

    @Override public void onClientRemoval() {
        if (compatibilityCarriage != null) {
            var dimensional = compatibilityCarriage.getDimensionalIfPresent(level().dimension());
            if (dimensional != null && dimensional.entity.get() == this) dimensional.entity.clear();
        }
        if (sounds != null) sounds.stop();
        compatibilityCarriage = null;
    }

    @Override public void setBlock(BlockPos localPos, StructureBlockInfo info) {
        if (contraption == null || !contraption.getBlocks().containsKey(localPos)) return;
        contraption.getBlocks().put(localPos, info);
        if (contraption instanceof SnapshotView view) view.refreshDisplayTag(level(), info);
        var actor = contraption.getActorAt(localPos);
        if (actor != null && actor.left.state().getBlock() == info.state().getBlock()) {
            actor.left = info;
            actor.right.state = info.state();
            actor.right.blockEntityData = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        }
        contraption.invalidateColliders();
        if (!level().isClientSide)
            CatnipServices.NETWORK.sendToClientsTrackingEntity(this,
                    new ContraptionBlockChangedPacket(getId(), localPos, info.state()));
    }

    private void attach(CarriageSnapshot snapshot, boolean mountStorage) {
        this.snapshot = snapshot;
        var view = new SnapshotView();
        view.anchor = snapshot.origin();
        view.setGlue(snapshot.glue());
        AABB bounds = null;
        for (var block : snapshot.blocks()) {
            var data = block.entityData();
            var info = new StructureBlockInfo(block.position(), block.state(), data);
            view.getBlocks().put(block.position(), info);
            if (mountStorage)
                view.getStorage().addBlock(level(), block.state(), snapshot.origin().offset(block.position()),
                        block.position(), level().getBlockEntity(snapshot.origin().offset(block.position())));
            var interaction = MovingInteractionBehaviour.REGISTRY.get(block.state());
            if (interaction != null) view.getInteractors().put(block.position(), interaction);
            if (block.state().getBlock() instanceof SeatBlock) {
                view.getSeats().add(block.position());
                if (mountStorage) {
                    // Same local seat lookup as Create's moveSeat, only for seats already captured by our scanner.
                    for (var seat : level().getEntitiesOfClass(SeatEntity.class,
                            new AABB(snapshot.origin().offset(block.position())))) {
                        if (seat.getPassengers().isEmpty()) continue;
                        view.initialSeatPassengers.put(block.position(), seat.getPassengers().getFirst());
                        break;
                    }
                }
            }
            if (data != null) {
                // Match Create's ordinary spawn payload: send the visual update tag,
                // not inventories/full block entity saves to every tracking client.
                var blockEntity = BlockEntity.loadStatic(snapshot.origin().offset(block.position()), block.state(), data,
                        level().registryAccess());
                if (blockEntity == null) throw new IllegalStateException("Could not restore carriage block entity");
                blockEntity.setLevel(level());
                var updateTag = blockEntity.getUpdateTag(level().registryAccess());
                // The deployer's update writer also emits its deferred inventory. Only held-item
                // appearance is needed; no fake player or mounted inventory is created for a view.
                if (blockEntity instanceof DeployerBlockEntity) {
                    updateTag.remove("Inventory");
                    updateTag.remove("Overflow");
                    if (data.contains("HeldItem")) updateTag.put("HeldItem", data.get("HeldItem").copy());
                }
                view.update(block.position(), updateTag);
                view.localizeMultiblock(info, blockEntity);
            }
            view.addDisplayActor(level(), info);
            var box = new AABB(block.position());
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        view.bounds = bounds;
        // Keep the same persistent envelope as a native station-assembled carriage.
        // Create reuses contraption.bounds when aligning its compatibility entity;
        // updating only this entity's box loses the interaction reach on that pass.
        view.expandBoundsAroundAxis(Direction.Axis.Y);
        view.configureDirection(originalForward);
        setInitialOrientation(Direction.SOUTH);
        startAtInitialYaw();
        setContraption(view);
        if (compatibilityCarriage != null) compatibilityCarriage.storage = view.cargo();
        validForRender = true;
        firstPositionUpdate = false;
        BlockPos origin = snapshot.origin();
        setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        updateRotatedBounds();
        xo = getX(); yo = getY(); zo = getZ();
    }

    public void setMotionOwner(UUID owner, Direction forward) {
        if (!forward.getAxis().isHorizontal()) throw new IllegalArgumentException("Vehicle forward must be horizontal");
        motionOwner = owner;
        setCoordinateBasis(forward);
        if (!level().isClientSide) entityData.set(BASIS_FORWARD, forward);
        setInitialOrientation(forward.getClockWise());
        if (contraption instanceof SnapshotView view) view.configureDirection(forward);
        cachedOwner = null;
    }

    public void installMotion(AssembledVehicleMotion owner) {
        ownedMotion = owner;
        pendingMotionState = null;
        pendingRelocation = null;
    }

    private PendingVehicleRelocation pendingRelocation;

    void installPendingMotion(CompoundTag saved) {
        ownedMotion = null;
        pendingMotionState = saved;
        pendingRelocation = null;
        nextMotionRetryTick = tickCount + 100;
    }

    public AssembledVehicleMotion motion() {
        if (!level().isClientSide && trainId != null
                && Create.RAILWAYS.trains.get(trainId) instanceof CompatibilityTrain train) {
            // A native record is authoritative even while its route is pending. An unloaded
            // payload may still name an old owner that has since moved into another consist.
            var current = train.bridge == null ? null : train.bridge.motion();
            return ownsThisCarriage(current) ? current : null;
        }
        if (ownsThisCarriage(ownedMotion)) return ownedMotion;
        if (motionOwner == null || !(level() instanceof ServerLevel server)) return null;
        if (cachedOwner == null || cachedOwner.isRemoved())
            cachedOwner = server.getEntity(motionOwner) instanceof SnapshotCarriageEntity carrier ? carrier : null;
        var current = cachedOwner == null ? null : cachedOwner.ownedMotion;
        return ownsThisCarriage(current) ? current : null;
    }

    private boolean ownsThisCarriage(AssembledVehicleMotion motion) {
        return motion != null && motion.owns(getUUID())
                && motion.sourceAssembly(getUUID()).equals(assemblyId);
    }

    /** Called after source blocks are removed. Loading a save restores contexts instead of restarting them. */
    public void activateBehaviours() {
        if (behavioursActive) return;
        behavioursActive = true;
        if (contraption instanceof SnapshotView view) view.active = true;
        contraption.startMoving(level());
    }

    public void stopBehaviours() {
        if (!behavioursActive) return;
        contraption.stop(level());
        behavioursActive = false;
        contraption.stalled = false;
        if (motion() != null) motion().reportActorStall(getUUID(), false);
    }

    public void restoreMountedStorage() {
        if (snapshot == null) return;
        for (var info : contraption.getBlocks().values()) {
            BlockPos world = snapshot.origin().offset(info.pos());
            contraption.getStorage().unmount(level(), info, world, level().getBlockEntity(world));
        }
    }

    /** Capture the live view before actors stop; native Create code owns the actual restoration. */
    public NativeDisassemblyPlan prepareNativeDisassembly(StructureTransform transform) {
        if (!(contraption instanceof SnapshotView view)) throw new IllegalStateException("Missing carriage view");
        if (level().isClientSide || view.disassembled || !isAlive())
            throw new IllegalStateException("Carriage cannot begin native disassembly");
        var fixedTransform = new StructureTransform(transform.offset, transform.rotationAxis,
                transform.rotation, transform.mirror);
        return new NativeDisassemblyPlan(view, fixedTransform);
    }

    public final class NativeDisassemblyPlan {
        private final SnapshotView view;
        private final StructureTransform transform;
        private final CompoundTag working;
        private final Map<BlockPos, StructureBlockInfo> originalBlocks;
        private final Map<BlockPos, List<BlockPos>> originalMultiblocks;
        private final List<AABB> originalGlue;
        private final List<AABB> targetGlue;
        private final List<SuperGlueEntity> installedGlue = new ArrayList<>();
        private final boolean originallyDisassembled;
        private boolean attempted, installed, rolledBack, finished;

        private NativeDisassemblyPlan(SnapshotView view, StructureTransform transform) {
            this.view = view;
            this.transform = transform;
            working = writeWorkingState();
            originalBlocks = view.copyBlocks();
            originalMultiblocks = view.multiblockPositions();
            originalGlue = view.glue();
            originallyDisassembled = view.disassembled;
            targetGlue = originalGlue.stream().map(box -> new AABB(
                    transform.apply(new Vec3(box.minX, box.minY, box.minZ)),
                    transform.apply(new Vec3(box.maxX, box.maxY, box.maxZ)))).toList();
        }

        /** Invoke once after all selected carriers stop, inside the world's restore transaction. */
        public void install() {
            if (contraption != view || attempted || rolledBack || finished)
                throw new IllegalStateException("Native disassembly plan is no longer installable");
            attempted = true;
            // stopMoving may have written final actor data. Keep the live view and storage so
            // third-party Create hooks see the same contraption that was moving in the world.
            for (var entry : actorBlockData().entrySet()) {
                var info = view.getBlocks().get(entry.getKey());
                if (info != null) view.getBlocks().put(info.pos(), new StructureBlockInfo(info.pos(), info.state(),
                        entry.getValue() == null ? null : entry.getValue().copy()));
            }
            view.bindMultiblocks(originalMultiblocks);
            Set<UUID> existingGlue = new HashSet<>();
            matchingGlue().forEach(glue -> existingGlue.add(glue.getUUID()));
            try {
                view.addBlocksToWorld(level(), transform);
                installed = true;
            } finally {
                for (var glue : matchingGlue())
                    if (!existingGlue.contains(glue.getUUID())) installedGlue.add(glue);
            }
        }

        /** The caller restores destination world cells; this restores only the live source. */
        public void rollback() {
            if (finished) throw new IllegalStateException("Committed native disassembly cannot roll back");
            if (rolledBack) return;
            if (contraption != view) throw new IllegalStateException("Native disassembly view was replaced");
            installedGlue.forEach(SuperGlueEntity::discard);
            view.restoreBlocks(originalBlocks);
            view.setGlue(originalGlue);
            view.disassembled = originallyDisassembled;
            readWorkingState(working);
            // readWorkingState replaces actor records, so rebuild group references afterwards.
            view.bindMultiblocks(originalMultiblocks);
            rolledBack = true;
        }

        /** Only after every selected carriage's world installation has committed. */
        public void finish() {
            if (!installed || rolledBack || finished || contraption != view)
                throw new IllegalStateException("Native disassembly has not committed an installation");
            finished = true;
            nativeDisassemblyTransform = transform;
            try { SnapshotCarriageEntity.super.disassemble(); }
            finally { nativeDisassemblyTransform = null; }
        }

        private List<SuperGlueEntity> matchingGlue() {
            if (targetGlue.isEmpty()) return List.of();
            AABB bounds = targetGlue.getFirst();
            for (int i = 1; i < targetGlue.size(); i++) bounds = bounds.minmax(targetGlue.get(i));
            return level().getEntitiesOfClass(SuperGlueEntity.class, bounds,
                    glue -> targetGlue.contains(glue.getBoundingBox()));
        }
    }

    /** Call only after the world restore transaction commits; a failed unmount must not move riders. */
    public void restorePassengers() {
        if (snapshot == null) return;
        restorePassengers(new StructureTransform(snapshot.origin(), 0, 0, 0));
    }

    public void restorePassengers(StructureTransform transform) {
        if (getPassengers().isEmpty()) return;
        contraption.addPassengersToWorld(level(), transform, getPassengers());
        ejectPassengers();
    }

    /** Original-world metadata for the station restore transaction; never modifies the immutable snapshot. */
    public Map<BlockPos, StructureBlockInfo> currentBlockData() {
        var result = new HashMap<BlockPos, StructureBlockInfo>();
        var actorData = actorBlockData();
        for (var info : contraption.getBlocks().values()) {
            CompoundTag data = actorData.getOrDefault(info.pos(), info.nbt());
            data = data == null ? null : data.copy();
            if (data != null && snapshot != null) {
                BlockPos world = snapshot.origin().offset(info.pos());
                data.putInt("x", world.getX()); data.putInt("y", world.getY()); data.putInt("z", world.getZ());
                if (data.contains("Controller"))
                    data.put("Controller", NbtUtils.writeBlockPos(snapshot.origin().offset(NBTHelper.readBlockPos(data, "Controller"))));
                if (data.contains("LastKnownPos")) data.put("LastKnownPos", NbtUtils.writeBlockPos(world));
            }
            result.put(info.pos(), new StructureBlockInfo(info.pos(), info.state(), data));
        }
        return result;
    }

    private Map<BlockPos, CompoundTag> actorBlockData() {
        var result = new HashMap<BlockPos, CompoundTag>();
        for (var actor : contraption.getActors()) {
            var context = actor.right;
            if (canRunBehaviours()) MovementBehaviour.REGISTRY.get(actor.left.state()).writeExtraData(context);
            CompoundTag data = context.blockEntityData == null ? null : context.blockEntityData.copy();
            // Create's actor save only writes the held item. Preserve the full live fake-player
            // inventory without stopping it or copying that private inventory into the spawn tag.
            if (data != null && context.temporaryData instanceof DeployerFakePlayer player)
                data.put("Inventory", player.getInventory().save(new ListTag()));
            result.put(actor.left.pos(), data);
        }
        return result;
    }

    public CompoundTag writeWorkingState() {
        return writeWorkingState(true);
    }

    private CompoundTag writeWorkingState(boolean includeCargo) {
        var tag = new CompoundTag();
        tag.putBoolean("Active", behavioursActive);
        tag.putBoolean("Stalled", contraption.stalled);
        if (includeCargo) contraption.writeStorage(tag, registryAccess(), false);
        var actorData = actorBlockData();
        var actors = new ListTag();
        for (var actor : contraption.getActors()) {
            var data = actor.right.writeToNBT(new CompoundTag());
            data.put("Pos", NbtUtils.writeBlockPos(actor.left.pos()));
            data.putBoolean("Disabled", actor.right.disabled);
            if (actorData.get(actor.left.pos()) != null) data.put("BlockEntity", actorData.get(actor.left.pos()));
            actors.add(data);
        }
        tag.put("Actors", actors);
        tag.put("DisabledActors", NBTHelper.writeItemList(contraption.getDisabledActors(), registryAccess()));
        var changed = new ListTag();
        if (snapshot != null) for (var original : snapshot.blocks()) {
            var current = contraption.getBlocks().get(original.position());
            // Actor block data is saved above; static BE data need not be duplicated.
            if (current.state().equals(original.state()) && (actorData.containsKey(original.position())
                    || java.util.Objects.equals(current.nbt(), original.entityData()))) continue;
            var block = new CompoundTag();
            block.put("Pos", NbtUtils.writeBlockPos(current.pos()));
            block.put("State", NbtUtils.writeBlockState(current.state()));
            if (current.nbt() != null) block.put("BlockEntity", current.nbt().copy());
            changed.add(block);
        }
        tag.put("ChangedBlocks", changed);
        tag.put("Passengers", NBTHelper.writeCompoundList(contraption.getSeatMapping().entrySet(), e -> {
            var data = new CompoundTag(); data.putUUID("Id", e.getKey()); data.putInt("Seat", e.getValue()); return data;
        }));
        return tag;
    }

    public void readWorkingState(CompoundTag tag) {
        if (!(contraption instanceof SnapshotView view)) return;
        if (level() instanceof ServerLevel server && snapshot != null) {
            var missing = new java.util.HashSet<Tag>();
            for (var block : snapshot.blocks()) if (block.state().isAir())
                missing.add(NbtUtils.writeBlockPos(block.position()));
            tag = willow.train.hexie.vehicle.assembly.MissingBlockMigration.migrate(
                    server, tag, "working-state-" + getUUID(), missing);
        }
        for (var element : tag.getList("ChangedBlocks", Tag.TAG_COMPOUND)) {
            var data = (CompoundTag) element;
            BlockPos pos = NBTHelper.readBlockPos(data, "Pos");
            if (!view.getBlocks().containsKey(pos)) throw new IllegalArgumentException("Unknown working block " + pos);
            var state = NbtUtils.readBlockState(level().holderLookup(net.minecraft.core.registries.Registries.BLOCK), data.getCompound("State"));
            view.getBlocks().put(pos, new StructureBlockInfo(pos, state,
                    data.contains("BlockEntity") ? data.getCompound("BlockEntity").copy() : null));
            view.refreshDisplayTag(level(), view.getBlocks().get(pos));
        }
        if (!tag.getBoolean("CargoInCarriage")) view.getStorage().read(tag, registryAccess(), false, view);
        view.getActors().clear();
        for (var element : tag.getList("Actors", Tag.TAG_COMPOUND)) {
            var data = (CompoundTag) element;
            BlockPos pos = NBTHelper.readBlockPos(data, "Pos");
            var info = view.getBlocks().get(pos);
            if (info == null || MovementBehaviour.REGISTRY.get(info.state()) == null)
                throw new IllegalArgumentException("Unknown working actor " + pos);
            if (data.contains("BlockEntity")) {
                info = new StructureBlockInfo(pos, info.state(), data.getCompound("BlockEntity").copy());
                view.getBlocks().put(pos, info);
                view.refreshDisplayTag(level(), info);
            }
            var context = MovementContext.readNBT(level(), info, data, view);
            context.disabled = data.getBoolean("Disabled");
            view.getActors().add(MutablePair.of(info, context));
        }
        view.getDisabledActors().clear();
        view.getDisabledActors().addAll(NBTHelper.readItemList(tag.getList("DisabledActors", Tag.TAG_COMPOUND), registryAccess()));
        view.getSeatMapping().clear();
        for (var element : tag.getList("Passengers", Tag.TAG_COMPOUND)) {
            var data = (CompoundTag) element;
            view.getSeatMapping().put(data.getUUID("Id"), data.getInt("Seat"));
        }
        behavioursActive = view.active = tag.getBoolean("Active");
        view.stalled = tag.getBoolean("Stalled");
        view.invalidateColliders();
    }

    @Override
    protected void writeAdditional(CompoundTag tag, HolderLookup.Provider registries, boolean spawnPacket) {
        tag.putUUID("HexieAssembly", assemblyId);
        tag.putUUID("TrainId", trainId == null ? assemblyId : trainId);
        tag.putInt("CarriageIndex", carriageIndex);
        if (motionOwner != null) tag.putUUID("MotionOwner", motionOwner);
        tag.putString("OriginalForward", originalForward.getName());
        tag.putBoolean("BehavioursActive", behavioursActive);
        if (spawnPacket) {
            super.writeAdditional(tag, registries, true);
        } else {
            // Native Carriage owns cargo and entity payload; structure data remains in SavedData.
            tag.putBoolean("NativeManaged", nativeManaged);
            if (contraption != null) {
                var working = writeWorkingState(!nativeManaged);
                if (nativeManaged) {
                    working.putBoolean("CargoInCarriage", true);
                }
                tag.put("WorkingState", working);
            }
            if (!nativeManaged) {
                if (ownedMotion != null) tag.put("MotionState", ownedMotion.writeState());
                else if (pendingMotionState != null) tag.put("MotionState", pendingMotionState.copy());
            }
        }
        tag.putFloat("BodyYaw", yaw); tag.putFloat("BodyPitch", pitch); tag.putFloat("BodyRoll", roll);
        tag.put("BogeyAnimation", bogeyAnimation.write());
        tag.put("CouplerAnimation", couplerAnimation.write());
    }

    @Override
    protected void readAdditional(CompoundTag tag, boolean spawnPacket) {
        if (!spawnPacket && level() instanceof ServerLevel server)
            tag = willow.train.hexie.vehicle.assembly.MissingBlockMigration.migrate(
                    server, tag, "carriage-" + getUUID());
        assemblyId = tag.getUUID("HexieAssembly");
        trainId = tag.hasUUID("TrainId") ? tag.getUUID("TrainId") : assemblyId;
        carriageIndex = tag.getInt("CarriageIndex");
        compatibilityCarriage = null;
        nativeManaged = tag.getBoolean("NativeManaged");
        restoringNativeEntity = nativeManaged && !spawnPacket;
        motionOwner = tag.hasUUID("MotionOwner") ? tag.getUUID("MotionOwner") : null;
        Direction forward = Direction.byName(tag.getString("OriginalForward"));
        originalForward = forward != null && forward.getAxis().isHorizontal() ? forward : Direction.NORTH;
        behavioursActive = tag.getBoolean("BehavioursActive");
        if (spawnPacket) {
            // The ordinary CARRIAGE factory creates a base view with global storage and
            // bogey-only rendering. Read directly into our one view instead of parsing twice.
            var view = new SnapshotView();
            view.readNBT(level(), tag.getCompound("Contraption"), true);
            view.entity = this;
            // readNBT alone omits Contraption.fromNBT's collision cache construction.
            // The native collider treats an empty cache as no solid blocks, including for players.
            view.invalidateColliders();
            view.active = behavioursActive;
            view.configureDirection(originalForward);
            contraption = view;
            initialized = tag.getBoolean("Initialized");
            setInitialOrientation(originalForward.getClockWise());
            xOld = getX(); yOld = getY(); zOld = getZ();
            // Create's ordinary MovementContext reader does not serialize the disabled flag.
            if (!behavioursActive) for (var actor : getContraption().getActors()) freeze(actor.right);
        } else if (level() instanceof ServerLevel server) {
            Vec3 savedPosition = position();
            CarriageSnapshotStore.get(server).find(getUUID()).ifPresent(saved -> attach(saved, false));
            setPos(savedPosition);
            if (contraption != null && tag.contains("WorkingState")) readWorkingState(tag.getCompound("WorkingState"));
            if (contraption != null && tag.contains("MotionState")) {
                try { ownedMotion = AssembledVehicleMotion.readState(server, tag.getCompound("MotionState")); }
                catch (AssembledVehicleMotion.RouteUnavailableException temporarilyUnavailable) {
                    // Track graph loading must not make EntityType.load discard the vehicle and inventories.
                    pendingMotionState = tag.getCompound("MotionState").copy();
                    nextMotionRetryTick = 0;
                }
            }
            // A stale entity saved before dismantling has no snapshot and is discarded
            // by AbstractContraptionEntity's existing null-contraption check.
        }
        yaw = prevYaw = tag.getFloat("BodyYaw");
        pitch = prevPitch = tag.getFloat("BodyPitch");
        roll = prevRoll = tag.getFloat("BodyRoll");
        if (tag.contains("BogeyAnimation")) readBogeyAnimation(tag.getCompound("BogeyAnimation"), 0);
        if (tag.contains("CouplerAnimation")) readCouplerAnimation(tag.getCompound("CouplerAnimation"), 0);
        validForRender = true;
        firstPositionUpdate = false;
        if (ownedMotion != null) {
            ownedMotion.applyCurrent(this);
            prevYaw = yaw; prevPitch = pitch; prevRoll = roll;
            xo = getX(); yo = getY(); zo = getZ();
        }
        if (!level().isClientSide) syncCarriage();
        updateRotatedBounds();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOTION_FRAME, new CompoundTag());
        builder.define(BASIS_FORWARD, Direction.NORTH);
        builder.define(COMPATIBILITY_POINTS, new CarriageSyncData());
        builder.define(COMPATIBILITY_GRAPH, Optional.empty());
        builder.define(COMPATIBILITY_TRAIN, Optional.empty());
        builder.define(COMPATIBILITY_INDEX, 0);
        builder.define(COMPATIBILITY_SPEED, 0f);
        builder.define(COMPATIBILITY_DERAILED, false);
    }

    /** Body origin is the centre of snapshot.origin; source geometry remains in its original axes. */
    public void applyMotion(BodyPose body, Direction forward, BodyPose planarWorkPose) {
        applyMotion(body, forward, planarWorkPose, forward);
    }

    void applyMotion(BodyPose body, Direction forward, BodyPose planarWorkPose, Direction modelForward) {
        setCoordinateBasis(forward);
        if (!level().isClientSide) entityData.set(BASIS_FORWARD, forward);
        workPose = planarWorkPose;
        prevYaw = yaw; prevPitch = pitch; prevRoll = roll;
        yaw = (float) Math.toDegrees(body.yaw());
        pitch = (float) Math.toDegrees(body.pitch());
        roll = (float) Math.toDegrees(body.roll());
        setPos(body.position().x(), body.position().y() - .5, body.position().z());
        updateRotatedBounds();
        setContraptionMotion(position().subtract(xo, yo, zo));
        if (!level().isClientSide) {
            var owner = motion();
            // Entity ticks may consume yesterday's solution before the logical train advances.
            // Sampling by world time would manufacture a zero (or reversed) tangent there.
            long now = owner == null ? level().getGameTime() : owner.poseTick();
            if (motionSampleTick != now) {
                continuousMotionSamples = motionSampleTick == now - 1;
                olderMotionPosition = previousMotionPosition;
                previousMotionPosition = motionSamplePosition;
                motionSampleTick = now;
            }
            motionSamplePosition = position();
            Vec3 delta = previousMotionPosition == null ? Vec3.ZERO : position().subtract(previousMotionPosition);
            motionVelocity = continuousMotionSamples && olderMotionPosition != null
                    ? delta.scale(1.5).subtract(previousMotionPosition.subtract(olderMotionPosition).scale(.5)) : delta;
            if (owner != null) {
                double oldFloor = couplerAnimation.floorOffset();
                bogeyAnimation.update(owner.runtime(), snapshot, forward, level().getGameTime(),
                        owner.isDerailed(getUUID()), modelForward);
                couplerAnimation.update(owner.runtime(), snapshot, forward);
                if (oldFloor != couplerAnimation.floorOffset() && contraption != null) {
                    contraption.invalidateColliders();
                    updateRotatedBounds();
                }
            }
        }
    }

    private void readBogeyAnimation(CompoundTag tag, int steps) {
        var before = bogeyAnimation.frames();
        bogeyAnimation.read(tag, steps);
        var after = bogeyAnimation.frames();
        boolean supportsChanged = before.size() != after.size();
        for (int i = 0; !supportsChanged && i < before.size(); i++)
            supportsChanged = !before.get(i).position().equals(after.get(i).position());
        if (supportsChanged && contraption != null) contraption.invalidateClientContraptionChildren();
    }

    private void readCouplerAnimation(CompoundTag tag, int steps) {
        var before = couplerAnimation.frames();
        double oldFloor = couplerAnimation.floorOffset();
        couplerAnimation.read(tag, steps);
        var after = couplerAnimation.frames();
        boolean hooksChanged = before.size() != after.size();
        for (int i = 0; !hooksChanged && i < before.size(); i++)
            hooksChanged = !before.get(i).position().equals(after.get(i).position());
        if (contraption != null) {
            boolean floorChanged = oldFloor != couplerAnimation.floorOffset();
            if (hooksChanged) floorBoundsSource = null;
            if (floorChanged || hooksChanged && (oldFloor != 0 || couplerAnimation.floorOffset() != 0))
                contraption.invalidateClientContraptionStructure();
            else if (hooksChanged) contraption.invalidateClientContraptionChildren();
            if (floorChanged) contraption.invalidateColliders();
            if (floorChanged || hooksChanged) updateRotatedBounds();
        }
    }

    @Override public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // Vanilla position packets must not restart the independently received pose clock.
        if (!poseInterpolation.initialized()) {
            interpolationTarget = new Vec3(x, y, z);
            interpolationSteps = Math.max(1, steps);
        }
    }

    @Override protected void tickContraption() {
        if (nonDamageTicks > 0) nonDamageTicks--;
        if (level().isClientSide) {
            bogeyAnimation.tick();
            couplerAnimation.tick();
            bindClientCompatibility();
            prevYaw = yaw; prevPitch = pitch; prevRoll = roll;
            if (pendingClientMotionFrame != null) {
                applyClientMotionFrame(pendingClientMotionFrame);
                pendingClientMotionFrame = null;
            }
            int steps = Math.max(1, interpolationSteps);
            if (poseInterpolation.initialized()) {
                poseInterpolation.tick();
                setPos(poseInterpolation.position(1));
            } else if (interpolationSteps > 0 && interpolationTarget != null) {
                setPos(position().lerp(interpolationTarget, 1.0 / steps));
            }
            if (interpolationSteps > 0) interpolationSteps--;
            yaw = AngleHelper.angleLerp(1f / steps, yaw, targetYaw);
            pitch = AngleHelper.angleLerp(1f / steps, pitch, targetPitch);
            roll = AngleHelper.angleLerp(1f / steps, roll, targetRoll);
            updateRotatedBounds();
        } else {
            if (pendingMotionState != null) {
                if (pendingRelocation == null)
                    pendingRelocation = new PendingVehicleRelocation((ServerLevel) level(), pendingMotionState);
                pendingRelocation.tick(this);
            }
            if (pendingMotionState != null && tickCount >= nextMotionRetryTick) {
                nextMotionRetryTick = tickCount + 100;
                try {
                    ownedMotion = AssembledVehicleMotion.readState((ServerLevel) level(), pendingMotionState);
                    pendingMotionState = null;
                    pendingRelocation = null;
                    ownedMotion.applyCurrent(this);
                } catch (AssembledVehicleMotion.RouteUnavailableException temporarilyUnavailable) {
                    // Keep the saved pose and working state; retry only this owner, once per five seconds.
                }
            }
            var owner = motion();
            if (owner != null) {
                owner.reportActorStall(getUUID(), contraption.stalled);
                // Global logical ticks advance the train. The entity only consumes its pose.
                if (owner.compatibility() == null) owner.tick(this);
                else owner.applyCurrent(this);
            }
        }
        var movement = position().subtract(xo, yo, zo);
        if (movement.lengthSqr() > 1e-10)
            // Create's lever animation uses the opposite sign to our route's assembly-forward axis.
            movingBackwards = reverseRotation(movement, 1).dot(Vec3.atLowerCornerOf(originalForward.getNormal())) > 0;
        if (!level().isClientSide && tickCount % Math.max(1, getType().updateInterval()) == 0) syncCarriage();
        tickActors();
        CarriagePlayerSupport.advance(this);
    }

    @Override public void registerColliding(Entity entity) {
        super.registerColliding(entity);
        CarriagePlayerSupport.contact(this, entity);
    }

    @Override public void tickActors() {
        if (!canRunBehaviours()) return;
        usingWorkPose = workPose != null;
        try { super.tickActors(); }
        finally { usingWorkPose = false; }
        var owner = motion();
        if (!level().isClientSide && owner != null) owner.reportActorStall(getUUID(), contraption.stalled);
        // Actor callbacks may retain rotation beyond this tick, so use the working transform then too.
        if (workPose != null) {
            BodyPose pose = workPose;
            for (var actor : contraption.getActors()) actor.right.rotation = v -> rotateAt(pose, v);
        }
    }

    @Override protected boolean shouldActorTrigger(MovementContext context, StructureBlockInfo info,
            MovementBehaviour actor, Vec3 actorPosition, BlockPos gridPosition) {
        if (context.position == null) return false;
        // AbstractContraptionEntity rescales carriage motion from its Train.speed. Our train
        // is only a compatibility mirror; actual work-pose displacement remains authoritative.
        context.motion = actorPosition.subtract(context.position);
        context.relativeMotion = reverseRotation(context.motion, 1);
        boolean firstMayBeStationary = context.contraption instanceof CarriageContraption
                || actor instanceof PortableStorageInterfaceMovement;
        return !BlockPos.containing(context.position).equals(gridPosition)
                || (context.relativeMotion.lengthSqr() > 0 || firstMayBeStationary) && context.firstMovement;
    }

    private boolean canRunBehaviours() {
        return behavioursActive && (level().isClientSide || motionOwner == null || motion() != null);
    }

    @Override public Vec3 toGlobalVector(Vec3 local, float partialTicks, boolean previousAnchor) {
        if (usingWorkPose) {
            Vec3 rotated = rotateAt(workPose, local.subtract(.5, .5, .5));
            return rotated.add(workPose.position().x(), workPose.position().y(), workPose.position().z());
        }
        return super.toGlobalVector(local, partialTicks, previousAnchor);
    }

    private double sourceYaw() {
        return switch (originalForward) { case EAST -> Math.PI / 2; case SOUTH -> Math.PI; case WEST -> -Math.PI / 2; default -> 0; };
    }

    private void setCoordinateBasis(Direction forward) {
        if (originalForward == forward.getOpposite()) {
            // A route reversal changes coordinates, not the visible vehicle orientation.
            // Transform both interpolation endpoints together so clients never animate a half-turn.
            yaw += 180; prevYaw += 180;
            pitch = -pitch; prevPitch = -prevPitch;
            roll = -roll; prevRoll = -prevRoll;
        }
        originalForward = forward;
    }

    private Vec3 rotateAt(BodyPose pose, Vec3 vector) {
        double angle = sourceYaw(), c = Math.cos(angle), s = Math.sin(angle);
        var result = pose.rotate(new CouplingEndpoint.Point(c * vector.x + s * vector.z, vector.y,
                -s * vector.x + c * vector.z));
        return new Vec3(result.x(), result.y(), result.z());
    }

    private org.joml.Quaternionf rotation(float partialTicks) {
        return new org.joml.Quaternionf().rotationY((float) Math.toRadians(AngleHelper.angleLerp(partialTicks, prevYaw, yaw)))
                .rotateX((float) Math.toRadians(AngleHelper.angleLerp(partialTicks, prevPitch, pitch)))
                .rotateZ((float) Math.toRadians(AngleHelper.angleLerp(partialTicks, prevRoll, roll)))
                .rotateY((float) sourceYaw());
    }

    @Override public Vec3 applyRotation(Vec3 local, float partialTicks) {
        if (usingWorkPose) return rotateAt(workPose, local);
        var result = rotation(partialTicks).transform(new org.joml.Vector3f((float) local.x, (float) local.y, (float) local.z));
        return new Vec3(result.x, result.y, result.z);
    }

    @Override public Vec3 reverseRotation(Vec3 world, float partialTicks) {
        org.joml.Quaternionf rotation = usingWorkPose
                ? new org.joml.Quaternionf().rotationY((float) workPose.yaw()).rotateX((float) workPose.pitch())
                    .rotateZ((float) workPose.roll()).rotateY((float) sourceYaw())
                : rotation(partialTicks);
        var result = rotation.conjugate().transform(new org.joml.Vector3f((float) world.x, (float) world.y, (float) world.z));
        return new Vec3(result.x, result.y, result.z);
    }

    @Override public ContraptionRotationState getRotationState() {
        final float bodyYaw = yaw, bodyPitch = pitch, bodyRoll = roll;
        final float source = (float) sourceYaw();
        return new ContraptionRotationState() {
            private Matrix3d inverseMatrix;
            @Override public Matrix3d asMatrix() {
                if (inverseMatrix != null) return inverseMatrix;
                // Collider consumes world -> local, the inverse of the render/seat matrix.
                return inverseMatrix = new Matrix3d().asYRotation(-source)
                        .multiply(new Matrix3d().asZRotation((float) Math.toRadians(-bodyRoll)))
                        .multiply(new Matrix3d().asXRotation((float) Math.toRadians(-bodyPitch)))
                        .multiply(new Matrix3d().asYRotation((float) Math.toRadians(-bodyYaw)));
            }
            @Override public boolean hasVerticalRotation() { return bodyPitch != 0 || bodyRoll != 0; }
        };
    }

    private void updateRotatedBounds() {
        if (contraption == null || contraption.bounds == null) return;
        AABB b = localFloorBounds();
        var rotation = rotation(1);
        var point = new org.joml.Vector3f();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            point.set((float) (((corner & 1) == 0 ? b.minX : b.maxX) - .5),
                    (float) (((corner & 2) == 0 ? b.minY : b.maxY) - .5),
                    (float) (((corner & 4) == 0 ? b.minZ : b.maxZ) - .5));
            rotation.transform(point);
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        setBoundingBox(new AABB(minX + getX(), minY + getY() + .5, minZ + getZ(),
                maxX + getX(), maxY + getY() + .5, maxZ + getZ()));
    }

    /** Rebuild only when the floor changes, so broad-phase collision still contains the lowered hooks. */
    private AABB localFloorBounds() {
        double floor = couplerAnimation.floorOffset();
        if (floor == 0) return contraption.bounds;
        if (floorBoundsSource == contraption.bounds && boundsFloorOffset == floor) return floorBounds;
        AABB result = contraption.bounds;
        for (var frame : couplerAnimation.frames()) {
            var info = contraption.getBlocks().get(frame.position());
            if (info == null) continue;
            var shape = info.state().getShape(contraption.getContraptionWorld(), frame.position());
            if (!shape.isEmpty()) result = result.minmax(shape.bounds().move(frame.position()));
        }
        floorBoundsSource = contraption.bounds;
        boundsFloorOffset = floor;
        return floorBounds = result;
    }

    @Override public void applyLocalTransforms(PoseStack stack, float partialTicks) {
        if (level().isClientSide && poseInterpolation.initialized()) {
            Vec3 correction = renderPosition(partialTicks)
                    .subtract(getPrevPositionVec().lerp(position(), partialTicks));
            stack.translate(correction.x, correction.y, correction.z);
        }
        stack.translate(0, .5, 0);
        stack.mulPose(rotation(partialTicks));
        stack.translate(-.5, -.5, -.5);
    }

    /** Shared by rendering, picking and player following; tick collision uses position(). */
    public Vec3 renderPosition(float partialTicks) {
        return level().isClientSide && poseInterpolation.initialized() ? poseInterpolation.position(partialTicks)
                : getPrevPositionVec().lerp(position(), partialTicks);
    }

    @Override public float getYawOffset() { return (float) Math.toDegrees(sourceYaw()); }
    @Override public float getInitialYaw() {
        // Create's vertical deployer adds another quarter turn for a carriage. Our actor
        // transform already includes the snapshot's source axes, so those legacy turns cancel.
        return -90;
    }

    @Override public void startAtInitialYaw() {
        // Source blocks retain world axes; the motion owner supplies the complete body pose.
        startAtYaw(0);
    }

    @Override protected void contraptionInitialize() {
        if (contraption instanceof SnapshotView view) {
            view.initialSeatPassengers.forEach((seat, passenger) -> {
                if (!passenger.isAlive()) return;
                Entity oldVehicle = passenger.getVehicle();
                addSittingPassenger(passenger, view.getSeats().indexOf(seat));
                if (passenger.getVehicle() == this && oldVehicle instanceof SeatEntity
                        && oldVehicle.getPassengers().isEmpty()) oldVehicle.discard();
            });
            view.initialSeatPassengers.clear();
        }
        // No Create stabilized subcontraptions exist here, so no generic nearby-entity scan is needed.
        initialized = true;
    }
    @Override public void disassemble() { /* Only our station transaction restores blocks. */ }

    @Override protected StructureTransform makeStructureTransform() {
        return nativeDisassemblyTransform != null ? nativeDisassemblyTransform : super.makeStructureTransform();
    }

    @Override
    public boolean handlePlayerInteraction(Player player, BlockPos localPos, Direction side, InteractionHand hand) {
        return behavioursActive && (SnapshotFloorInteraction.handle(this, player, localPos, hand)
                || SnapshotCouplerInteraction.handle(this, player, localPos, hand)
                || super.handlePlayerInteraction(player, localPos, side, hand));
    }

    @Override public boolean startControlling(BlockPos controls, Player player) {
        var info = contraption == null ? null : contraption.getBlocks().get(controls);
        if (!behavioursActive || player.isSpectator() || info == null || !(info.state().getBlock() instanceof ControlsBlock)
                || !canInteractWithBlock(player, controls, 4)) return false;
        return level().isClientSide || motion() != null && motion().startControl(this, controls, player);
    }

    @Override public boolean control(BlockPos controls, Collection<Integer> keys, Player player) {
        return motion() != null && motion().control(this, controls, keys, player);
    }

    @Override public void stopControlling(BlockPos controls) {
        if (motion() != null) motion().stopControl(this, controls);
        super.stopControlling(controls);
    }

    private static final class SnapshotView extends CarriageContraption {
        private boolean active;
        private Direction direction = Direction.NORTH;
        private boolean forwardControls;
        private boolean backwardControls;
        private final Map<BlockPos, Entity> initialSeatPassengers = new HashMap<>();
        SnapshotView() { storage = new SnapshotCargoManager(); }
        @Override public ContraptionWorld getContraptionWorld() {
            if (!(collisionLevel instanceof FloorWorld)) collisionLevel = new FloorWorld(entity.level(), this);
            return collisionLevel;
        }
        void update(BlockPos position, CompoundTag tag) { updateTags.put(position, tag); }

        Map<BlockPos, StructureBlockInfo> copyBlocks() {
            var copy = new HashMap<BlockPos, StructureBlockInfo>();
            for (var info : getBlocks().values()) {
                CompoundTag data = info.nbt();
                copy.put(info.pos(), new StructureBlockInfo(info.pos(), info.state(),
                        data == null ? null : data.copy()));
            }
            return copy;
        }

        void restoreBlocks(Map<BlockPos, StructureBlockInfo> saved) {
            getBlocks().clear();
            saved.forEach((pos, info) -> getBlocks().put(pos, new StructureBlockInfo(pos, info.state(),
                    info.nbt() == null ? null : info.nbt().copy())));
        }

        Map<BlockPos, List<BlockPos>> multiblockPositions() {
            var positions = new HashMap<BlockPos, List<BlockPos>>();
            capturedMultiblocks.asMap().forEach((controller, parts) ->
                    positions.put(controller, parts.stream().map(StructureBlockInfo::pos).toList()));
            return positions;
        }

        void bindMultiblocks(Map<BlockPos, List<BlockPos>> positions) {
            capturedMultiblocks.clear();
            positions.forEach((controller, parts) -> parts.forEach(pos -> {
                var info = getBlocks().get(pos);
                if (info != null) capturedMultiblocks.put(controller, info);
            }));
        }

        List<AABB> glue() { return List.copyOf(superglue); }
        void setGlue(List<AABB> boxes) { superglue.clear(); superglue.addAll(boxes); }

        @Override public void addBlocksToWorld(Level world, StructureTransform transform) {
            // The transaction installed the live view already. The later native entity
            // disassembly still performs passengers, notifications and final removal, without
            // entering mod hooks on the base placement method a second time.
            if (disassembled) return;
            try { super.addBlocksToWorld(world, transform); }
            finally { cargo().clearDisassemblyView(); }
        }

        @Override protected void translateMultiblockControllers(StructureTransform transform) {
            var controllerData = new HashMap<BlockPos, CompoundTag>();
            capturedMultiblocks.keySet().forEach(pos -> {
                var info = blocks.get(pos);
                if (info != null && info.nbt() != null) controllerData.put(pos, info.nbt());
            });
            super.translateMultiblockControllers(transform);
            // Create moves a controller's NBT to the new minimum corner, but leaves mounted
            // storage keyed at the old corner. Follow the actual native NBT move, without
            // duplicating its orientation/controller rules or replacing storage objects.
            var positionsByData = new IdentityHashMap<CompoundTag, BlockPos>();
            capturedMultiblocks.values().forEach(part -> {
                var info = blocks.get(part.pos());
                if (info != null && info.nbt() != null) positionsByData.put(info.nbt(), info.pos());
            });
            var movedControllers = new HashMap<BlockPos, BlockPos>();
            controllerData.forEach((before, data) -> {
                var after = positionsByData.get(data);
                if (after != null && !after.equals(before)) movedControllers.put(before, after);
            });
            if (!movedControllers.isEmpty())
                cargo().prepareDisassemblyView(multiblockPositions(), movedControllers);
        }

        void refreshDisplayTag(Level level, StructureBlockInfo info) {
            updateTags.remove(info.pos());
            if (info.nbt() == null || !info.state().hasBlockEntity()) return;
            var blockEntity = BlockEntity.loadStatic(info.pos(), info.state(), info.nbt(), level.registryAccess());
            if (blockEntity == null) return;
            blockEntity.setLevel(level);
            var update = blockEntity.getUpdateTag(level.registryAccess());
            if (blockEntity instanceof DeployerBlockEntity) {
                update.remove("Inventory");
                update.remove("Overflow");
                if (info.nbt().contains("HeldItem")) update.put("HeldItem", info.nbt().get("HeldItem").copy());
            }
            updateTags.put(info.pos(), update);
        }

        SnapshotCargoManager cargo() { return (SnapshotCargoManager) storage; }
        void storageFromCarriage(Carriage carriage) { storage = carriage.storage; }

        @Override public MountedStorageManager getStorage() { return storage; }

        @Override public void writeStorage(CompoundTag tag, HolderLookup.Provider registries, boolean spawnPacket) {
            storage.write(tag, registries, spawnPacket);
        }

        @Override public void swapStorageAfterAssembly(CarriageContraptionEntity entity) {
            if (entity.getCarriage() != null) entity.getCarriage().storage = cargo();
        }

        @Override protected ClientContraption createClientContraption() {
            // Retain BEs for their styles/data. Only supports with animation data leave the
            // ordinary BER pass; old saves keep their static models until motion is available.
            return new ClientContraption(this) {
                private final java.util.BitSet staticBlocks = new java.util.BitSet();
                private final java.util.BitSet visible = new java.util.BitSet();
                private int maskedVersion = -1;

                @Override public BlockEntity readBlockEntity(net.minecraft.world.level.Level level,
                        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info,
                        boolean legacy) {
                    var be = super.readBlockEntity(level, info, legacy);
                    // ClientContraption's constructor creates BEs without calling resetRenderLevel.
                    // Bind at creation so the first render and every rebuild read live state.
                    if (be instanceof willow.train.hexie.api.cab.CabDisplayHost console)
                        console.displaySource(() -> ((SnapshotCarriageEntity) SnapshotView.this.entity).cabDisplayState());
                    if (be instanceof willow.train.hexie.blockentity.DrivingConsoleEntity console)
                        console.testSource(() -> ((SnapshotCarriageEntity) SnapshotView.this.entity).testCabControls(info.pos()));
                    return be;
                }

                @Override public void resetRenderLevel() {
                    // Create recreates every display BE after any block changes. Preserve the
                    // door's current progress, including a transition still in flight.
                    var previousDoors = new java.util.HashMap<BlockPos, BlockEntity>();
                    for (var be : renderedBlockEntityView)
                        if (be instanceof willow.train.hexie.blockentity.CustomRenderedDoorEntity
                                || be instanceof willow.train.hexie.blockentity.OBJRenderableEndFaceEntity)
                            previousDoors.put(be.getBlockPos(), be);
                    super.resetRenderLevel();
                    for (var entry : previousDoors.entrySet()) {
                        var before = entry.getValue();
                        var after = getBlockEntity(entry.getKey());
                        if (after == null || before.getBlockState().getBlock() != after.getBlockState().getBlock()) continue;
                        if (before instanceof willow.train.hexie.blockentity.CustomRenderedDoorEntity oldDoor
                                && after instanceof willow.train.hexie.blockentity.CustomRenderedDoorEntity door)
                            door.animation_controller = oldDoor.animation_controller;
                        if (before instanceof willow.train.hexie.blockentity.OBJRenderableEndFaceEntity oldDoor
                                && after instanceof willow.train.hexie.blockentity.OBJRenderableEndFaceEntity door)
                            door.counter = oldDoor.counter;
                    }
                }

                @Override public RenderedBlocks getRenderedBlocks() {
                    // Change only the rendered state. Saved blocks, collision and native
                    // disassembly keep the original complete coupler and its facing.
                    return new RenderedBlocks(pos -> {
                        var info = SnapshotView.this.getBlocks().get(pos);
                        if (info == null) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                        var hook = CouplerBlock.of(info.state());
                        return hook != null
                                ? hook.carriageRenderState(info.state(),
                                        ((SnapshotCarriageEntity) entity).couplerAnimation().hasShiftedBase(pos))
                                : info.state();
                    }, SnapshotView.this.getBlocks().keySet());
                }

                @Override public java.util.BitSet getAndAdjustShouldRenderBlockEntities() {
                    if (maskedVersion != childrenVersion()) {
                        var animated = ((SnapshotCarriageEntity) entity).bogeyAnimation().frames().stream()
                                .map(SnapshotBogeyAnimation.Frame::position).collect(java.util.stream.Collectors.toSet());
                        var animatedHooks = ((SnapshotCarriageEntity) entity).couplerAnimation().frames().stream()
                                .map(SnapshotBogeyAnimation.Frame::position).collect(java.util.stream.Collectors.toSet());
                        staticBlocks.clear();
                        for (int i = 0; i < renderedBlockEntityView.size(); i++) {
                            var blockEntity = renderedBlockEntityView.get(i);
                            boolean independentSupport = blockEntity instanceof AbstractBogeyBlockEntity
                                    && animated.contains(blockEntity.getBlockPos());
                            boolean independentHook = blockEntity instanceof CouplerBlockEntity
                                    && animatedHooks.contains(blockEntity.getBlockPos());
                            if (!independentSupport && !independentHook)
                                staticBlocks.set(i);
                        }
                        maskedVersion = childrenVersion();
                    }
                    visible.clear();
                    visible.or(super.getAndAdjustShouldRenderBlockEntities());
                    visible.and(staticBlocks);
                    return visible;
                }
            };
        }

        @Override public Direction getAssemblyDirection() { return direction; }
        @Override public boolean hasForwardControls() { return forwardControls; }
        @Override public boolean hasBackwardControls() { return backwardControls; }

        void configureDirection(Direction direction) {
            this.direction = direction;
            forwardControls = backwardControls = false;
            blockConductors = Couple.create(false, false);
            conductorSeats.clear();
            for (var info : getBlocks().values()) {
                if (info.state().getBlock() instanceof ControlsBlock) {
                    var facing = info.state().getValue(ControlsBlock.FACING);
                    forwardControls |= facing == direction;
                    backwardControls |= facing == direction.getOpposite();
                }
                var interaction = MovingInteractionBehaviour.REGISTRY.get(info.state());
                if (interaction instanceof ConductorBlockInteractionBehavior conductor
                        && conductor.isValidConductor(info.state())) {
                    for (Direction facing : List.of(direction, direction.getOpposite()))
                        if (inControl(info.pos(), facing)) blockConductors.set(facing != direction, true);
                }
            }
            for (var seat : getSeats())
                for (Direction facing : List.of(direction, direction.getOpposite()))
                    if (inControl(seat, facing))
                        conductorSeats.computeIfAbsent(seat, p -> Couple.create(false, false)).set(facing != direction, true);
        }

        void localizeMultiblock(StructureBlockInfo info, BlockEntity blockEntity) {
            if (!(blockEntity instanceof IMultiBlockEntityContainer)) return;
            // Existing Create conversion operates on this view's copies, never the stored snapshot.
            captureMultiblock(info.pos(), info, blockEntity);
            var update = updateTags.get(info.pos());
            if (update == null || !update.contains("Controller")) return;
            // The view never ticks connectivity. Do not leave an original-world relocation marker.
            update.remove("LastKnownPos");
            if (info.nbt().contains("LastKnownPos"))
                update.put("LastKnownPos", info.nbt().get("LastKnownPos").copy());
        }

        void addDisplayActor(Level level, StructureBlockInfo info) {
            if (MovementBehaviour.REGISTRY.get(info.state()) == null) return;
            var context = new MovementContext(level, info, this);
            freeze(context);
            context.position = Vec3.atCenterOf(anchor.offset(info.pos()));
            actors.add(MutablePair.of(info, context));
        }

        @Override public void stop(Level world) {
            if (!active) return;
            active = false;
            super.stop(world);
        }

        @Override
        public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
            if (active && entity instanceof SnapshotCarriageEntity carrier && carrier.canRunBehaviours()) {
                var tag = super.writeNBT(registries, spawnPacket);
                tag.putBoolean("FrontControls", forwardControls);
                tag.putBoolean("BackControls", backwardControls);
                return tag;
            }
            // The normal actor writer calls writeExtraData, which may create a deployer fake player
            // or change its inventory. Reuse every other Create field and write only inert context.
            var displayActors = actors;
            CompoundTag tag;
            actors = List.of();
            try { tag = super.writeNBT(registries, spawnPacket); }
            finally { actors = displayActors; }
            var serializedActors = new ListTag();
            for (var actor : displayActors) {
                var data = new CompoundTag();
                data.put("Pos", NbtUtils.writeBlockPos(actor.left.pos()));
                serializedActors.add(actor.right.writeToNBT(data));
            }
            tag.put("Actors", serializedActors);
            tag.putBoolean("FrontControls", forwardControls);
            tag.putBoolean("BackControls", backwardControls);
            return tag;
        }
    }

    /** Exposes the carriage floor to native shape queries without altering saved block states. */
    private static final class FloorWorld extends ContraptionWorld implements CarriageFloorView {
        private final SnapshotView view;

        FloorWorld(Level level, SnapshotView view) { super(level, view); this.view = view; }

        @Override public double floorOffset() {
            return view.entity instanceof SnapshotCarriageEntity carrier ? carrier.couplerAnimation.floorOffset() : 0;
        }
    }

    /** Only native placement sees these temporary keys; the carriage keeps its live cargo. */
    private static final class SnapshotCargoManager extends TrainCargoManager {
        private ImmutableMap<BlockPos, MountedItemStorage> disassemblyItems;
        private MountedFluidStorageWrapper disassemblyFluids;

        void prepareDisassemblyView(Map<BlockPos, List<BlockPos>> groups, Map<BlockPos, BlockPos> moved) {
            disassemblyItems = relocateControllers(super.getAllItemStorages(), groups, moved);
            var fluidMap = relocateControllers(super.getFluids().storages, groups, moved);
            if (fluidMap != null) disassemblyFluids = new MountedFluidStorageWrapper(fluidMap);
        }

        private static <T> ImmutableMap<BlockPos, T> relocateControllers(Map<BlockPos, T> storages,
                Map<BlockPos, List<BlockPos>> groups, Map<BlockPos, BlockPos> moved) {
            Map<BlockPos, T> remapped = null;
            for (var move : moved.entrySet()) {
                BlockPos controller = move.getKey();
                if (!storages.containsKey(controller)) continue;
                // Per-block inventories (for example vaults) stay with their own blocks.
                // Only storage mounted exclusively on the controller follows its NBT.
                if (groups.get(controller).stream().anyMatch(pos ->
                        !pos.equals(controller) && storages.containsKey(pos))) continue;
                if (remapped == null) remapped = new HashMap<>(storages);
                remapped.put(move.getValue(), remapped.remove(controller));
            }
            return remapped == null ? null : ImmutableMap.copyOf(remapped);
        }

        void clearDisassemblyView() { disassemblyItems = null; disassemblyFluids = null; }

        @Override public ImmutableMap<BlockPos, MountedItemStorage> getAllItemStorages() {
            return disassemblyItems == null ? super.getAllItemStorages() : disassemblyItems;
        }

        @Override public MountedFluidStorageWrapper getFluids() {
            return disassemblyFluids == null ? super.getFluids() : disassemblyFluids;
        }
    }

    private static void freeze(MovementContext context) {
        context.disabled = true;
        context.firstMovement = false;
        context.motion = context.relativeMotion = Vec3.ZERO;
        context.stall = false;
    }
}
