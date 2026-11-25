package willow.train.hexie.compat.create.motion;

import java.util.Optional;
import java.util.UUID;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllEntityDataSerializers;
import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.collision.Matrix3d;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import java.util.Collection;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.railway.TrackGeometryView.Vector;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverBodyPoseView;
import willow.train.hexie.compat.create.assembly.PendingVehicleRelocation;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageCargo;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageDisassembly;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageImport;
import willow.train.hexie.compat.create.assembly.SnapshotCarriageStructure;
import willow.train.hexie.compat.create.interaction.SnapshotCarriageControls;
import willow.train.hexie.compat.create.player.CarriagePlayerSupport;
import willow.train.hexie.compat.create.player.SnapshotCarriageBoarding;
import willow.train.hexie.compat.create.signal.SnapshotCarriageCabDisplay;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.core.api.vehicle.VehicleKind;

/**
 * A Create-compatible carriage identity over our only live snapshot and movement owner.
 * Our runtime owns movement; Create's logical carriage owns unloading, cargo and recreation.
 */
public final class SnapshotCarriageEntity extends CarriageContraptionEntity implements SnapshotCarriageView {
    static final EntityDataAccessor<CompoundTag> MOTION_FRAME = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.COMPOUND_TAG);
    static final EntityDataAccessor<Direction> BASIS_FORWARD = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.DIRECTION);
    static final EntityDataAccessor<CarriageSyncData> COMPATIBILITY_POINTS = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, AllEntityDataSerializers.CARRIAGE_DATA);
    static final EntityDataAccessor<Optional<UUID>> COMPATIBILITY_GRAPH = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    static final EntityDataAccessor<CompoundTag> COMPATIBILITY_GRAPHS = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.COMPOUND_TAG);
    static final EntityDataAccessor<Optional<UUID>> COMPATIBILITY_TRAIN = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    static final EntityDataAccessor<Integer> COMPATIBILITY_INDEX = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> COMPATIBILITY_SPEED = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> COMPATIBILITY_DERAILED = SynchedEntityData.defineId(
            SnapshotCarriageEntity.class, EntityDataSerializers.BOOLEAN);

    private final SnapshotCarriageCargo cargo = new SnapshotCarriageCargo(this);
    private final SnapshotCarriageDisassembly disassembly = new SnapshotCarriageDisassembly(this);
    final SnapshotCarriageImport structureImport = new SnapshotCarriageImport(this);
    private final SnapshotCarriageStructure structure = new SnapshotCarriageStructure(this);
    private final SnapshotCarriageSeats seats = new SnapshotCarriageSeats(this);
    private final SnapshotCarriageControls controls = new SnapshotCarriageControls(this);
    private final SnapshotCarriagePersistence persistence = new SnapshotCarriagePersistence(this);
    final SnapshotCarriagePlayback playback = new SnapshotCarriagePlayback(this);
    public final SnapshotCarriageSync sync = new SnapshotCarriageSync(this);
    public final SnapshotCarriageBoarding boarding = new SnapshotCarriageBoarding(this);
    final SnapshotCarriageCabDisplay cabDisplay = new SnapshotCarriageCabDisplay(this);

    static void prepareTimetableTick(com.simibubi.create.content.trains.entity.Train train, long gameTime) {
        SnapshotCarriageCargo.prepareTimetableTick(train, gameTime);
    }

    static void preparePersistentCargo(Carriage carriage, ServerLevel level) {
        SnapshotCarriageCargo.preparePersistentCargo(carriage, level);
    }

    public void restoreMountedStorage() {
        cargo.restoreMountedStorage();
    }

    public SnapshotCarriageDisassembly.NativeDisassemblyPlan prepareNativeDisassembly(StructureTransform transform) {
        return disassembly.prepareNativeDisassembly(transform);
    }

    @Override public void disassemble() {
        disassembly.disassemble();
    }

    @Override protected StructureTransform makeStructureTransform() {
        return disassembly.makeStructureTransform();
    }

    public StructureTransform baseMakeStructureTransform() {
        return super.makeStructureTransform();
    }

    @Override public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (structureImport == null) {
            super.moveTo(x, y, z, yRot, xRot);
            return;
        }
        structureImport.moveTo(x, y, z, yRot, xRot);
    }

    public void baseMoveTo(double x, double y, double z, float yRot, float xRot) {
        super.moveTo(x, y, z, yRot, xRot);
    }

    @Override public float mirror(net.minecraft.world.level.block.Mirror mirror) {
        return structureImport.mirror(mirror);
    }

    public float baseMirror(net.minecraft.world.level.block.Mirror mirror) {
        return super.mirror(mirror);
    }

    @Override public void onAddedToLevel() {
        structureImport.onAddedToLevel();
    }

    public void baseOnAddedToLevel() {
        super.onAddedToLevel();
    }

    private void tryBindImportedStructure() {
        structureImport.tryBindImportedStructure();
    }

    @Override public void setBlock(BlockPos localPos, StructureBlockInfo info) {
        structure.setBlock(localPos, info);
    }

    public void attach(CarriageSnapshotView snapshot, boolean mountStorage) {
        structure.attach(snapshot, mountStorage);
    }

    @Override
    public boolean handlePlayerInteraction(Player player, BlockPos localPos, Direction side, InteractionHand hand) {
        return this.controls.handlePlayerInteraction(player, localPos, side, hand);
    }

    public boolean baseHandlePlayerInteraction(Player player, BlockPos localPos, Direction side, InteractionHand hand) {
        int index = seats.interactiveHardSeat(player, localPos, hand);
        if (index >= 0) return seats.interact(player, localPos, side, hand, index);
        return super.handlePlayerInteraction(player, localPos, side, hand);
    }

    @Override public void addSittingPassenger(Entity passenger, int seatIndex) {
        if (seats.isHardSeat(seatIndex)) seats.addPassenger(passenger, seatIndex);
        else super.addSittingPassenger(passenger, seatIndex);
    }

    @Override public Vec3 getPassengerPosition(Entity passenger, float partialTicks) {
        Vec3 position = seats.passengerPosition(passenger, partialTicks);
        return position != null ? position : super.getPassengerPosition(passenger, partialTicks);
    }

    @Override protected void removePassenger(Entity passenger) {
        // Create removes the mapping and sends the native dismount packet in super.
        var seatIndex = contraption == null ? null : contraption.getSeatMapping().get(passenger.getUUID());
        super.removePassenger(passenger);
        seats.dismounted(passenger, seatIndex);
    }

    @Override public boolean isAlwaysTicking() {
        return true;
    }

    @Override public boolean startControlling(BlockPos controls, Player player) {
        return this.controls.startControlling(controls, player);
    }

    @Override public boolean control(BlockPos controls, Collection<Integer> keys, Player player) {
        return this.controls.control(controls, keys, player);
    }

    public void releaseControls(Player player, BlockPos controls) {
        this.controls.releaseControls(player, controls);
    }

    @Override public void stopControlling(BlockPos controls) {
        this.controls.stopControlling(controls);
    }

    public void baseStopControlling(BlockPos controls) {
        super.stopControlling(controls);
    }

    @Override public boolean shouldBeSaved() {
        return persistence.shouldBeSaved();
    }

    @Override public boolean save(CompoundTag tag) {
        return persistence.save(tag);
    }

    public boolean baseSave(CompoundTag tag) {
        return super.save(tag);
    }

    public Map<BlockPos, StructureBlockInfo> currentBlockData() {
        return persistence.currentBlockData();
    }

    public StructureBlockInfo withCabControls(StructureBlockInfo info) {
        return persistence.withCabControls(info);
    }

    public Map<BlockPos, CompoundTag> actorBlockData() {
        return persistence.actorBlockData();
    }

    public CompoundTag writeWorkingState() {
        return persistence.writeWorkingState();
    }

    public CompoundTag writeWorkingState(boolean includeCargo) {
        return persistence.writeWorkingState(includeCargo);
    }

    public void readWorkingState(CompoundTag tag) {
        persistence.readWorkingState(tag);
    }


    @Override
    protected void writeAdditional(CompoundTag tag, HolderLookup.Provider registries, boolean spawnPacket) {
        persistence.writeAdditional(tag, registries, spawnPacket);
    }

    public void baseWriteAdditional(CompoundTag tag, HolderLookup.Provider registries, boolean spawnPacket) {
        super.writeAdditional(tag, registries, spawnPacket);
    }

    @Override
    protected void readAdditional(CompoundTag tag, boolean spawnPacket) {
        persistence.readAdditional(tag, spawnPacket);
    }

    @Override public boolean isControlledByLocalInstance() {
        return playback.isControlledByLocalInstance();
    }

    @Override public Vec3 getPrevPositionVec() {
        return playback.getPrevPositionVec();
    }

    public void applyClientMotionFrame(CompoundTag frame) {
        playback.applyClientMotionFrame(frame);
    }

    public boolean queueClientPose(CompoundTag frame) {
        return playback.queueClientPose(frame);
    }

    public void bindClientPoseTimeline() {
        playback.bindClientPoseTimeline();
    }



    public void applyClientPose() {
        playback.applyClientPose();
    }

    @Override public void onClientRemoval() {
        playback.onClientRemoval();
    }


    public void readBogeyAnimation(CompoundTag tag, int steps) {
        playback.readBogeyAnimation(tag, steps);
    }


    public void readCouplerAnimation(CompoundTag tag, int steps) {
        playback.readCouplerAnimation(tag, steps);
    }


    @Override public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        playback.lerpTo(x, y, z, yRot, xRot, steps);
    }

    public void bindCompatibility(Carriage carriage, int index) {
        sync.bindCompatibility(carriage, index);
    }

    @Override public Carriage getCarriage() {
        return sync.getCarriage();
    }

    @Override public void setCarriage(Carriage carriage) {
        sync.setCarriage(carriage);
    }

    private void bindClientCompatibility() {
        sync.bindClientCompatibility();
    }

    @Override public CarriageSyncData getCarriageData() {
        return sync.getCarriageData();
    }

    @Override public void syncCarriage() {
        sync.syncCarriage();
    }

    void applyCompatibilityPoints() {
        sync.applyCompatibilityPoints();
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (sync == null) {
            super.onSyncedDataUpdated(key);
            return;
        }
        sync.onSyncedDataUpdated(key);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        SnapshotCarriageSync.defineSynchedData(builder);
    }

    public boolean retainTrackingForBoarding(net.minecraft.server.level.ServerPlayer player, double range) {
        return boarding.retainTrackingForBoarding(player, range);
    }

    public void restorePassengers() {
        boarding.restorePassengers();
    }

    public void restorePassengers(StructureTransform transform) {
        boarding.restorePassengers(transform);
    }

    @Override public void registerColliding(Entity entity) {
        boarding.registerColliding(entity);
    }

    public void baseRegisterColliding(Entity entity) {
        super.registerColliding(entity);
    }

    @Override protected void contraptionInitialize() {
        boarding.contraptionInitialize();
    }

    public double displaySpeedKmh() {
        return cabDisplay.displaySpeedKmh();
    }

    public CabDisplayState cabDisplayState() {
        return cabDisplay.cabDisplayState();
    }

    public CabDisplayState cabDisplayState(BlockPos position) {
        return cabDisplay.cabDisplayState(position);
    }

    public CabState cabControls(BlockPos position) {
        return cabDisplay.cabControls(position);
    }

    public CompoundTag cabDisplayFrame(AssembledVehicleMotion owner, CabDisplayState base) {
        return cabDisplay.cabDisplayFrame(owner, base);
    }

    public void readCabDisplayFrames(CompoundTag controls) {
        cabDisplay.readCabDisplayFrames(controls);
    }

    public UUID assemblyId;
    UUID motionOwner;
    AssembledVehicleMotion ownedMotion;
    CompoundTag pendingMotionState;
    int nextMotionRetryTick;
    private SnapshotCarriageEntity cachedOwner;
    public CarriageSnapshotView snapshot;
    public Direction originalForward = Direction.NORTH;
    public boolean behavioursActive;
    private boolean usingWorkPose;
    private VehicleConstraintSolverBodyPoseView workPose;
    public float roll, prevRoll;

    Vec3 motionSamplePosition, previousMotionPosition, olderMotionPosition, motionVelocity = Vec3.ZERO;
    private long motionSampleTick = Long.MIN_VALUE;
    long motionFrameTick = Long.MIN_VALUE;

    private boolean continuousMotionSamples;

    public final SnapshotBogeyAnimation bogeyAnimation = new SnapshotBogeyAnimation();
    public final SnapshotCouplerAnimation couplerAnimation = new SnapshotCouplerAnimation();

    AABB floorBoundsSource;
    private AABB floorBounds;
    private double boundsFloorOffset = Double.NaN;
    boolean nativeManaged;
    boolean restoringNativeEntity;

    public SnapshotCarriageEntity(EntityType<?> type, Level level) { super(type, level); }

    public VehicleKind vehicleKind() {
        return VehicleKind.CREATE_COMPATIBLE;
    }

    public static void syncTrackAnchors(Carriage carriage, Level level, VehicleConstraintSolverBodyPoseView body) {
        if (carriage.train.graph != null) carriage.updateContraptionAnchors();
        var dimensional = carriage.getDimensional(level);
        Vec3 position = new Vec3(body.position().x(), body.position().y() - .5, body.position().z());
        // Native alignEntity decodes its entity yaw as atan2(z, x) + 180 degrees.
        double horizontal = Math.cos(body.pitch());
        Vec3 heading = new Vec3(-Math.cos(body.yaw()) * horizontal, -Math.sin(body.pitch()),
                -Math.sin(body.yaw()) * horizontal);
        ((SnapshotBodyAnchor) dimensional).hexie$setBodyAnchor(position, heading);
        CreateTrackCompatibility.synchronizePose(dimensional, body.roll());
    }

    public static SnapshotCarriageEntity create(ServerLevel level, UUID assembly, CarriageSnapshotView snapshot) {
        var entity = new SnapshotCarriageEntity(HexieBridges.worldRegistry().entityInit().assembledCarriage(), level);
        entity.assemblyId = assembly;
        entity.trainId = assembly;
        entity.setUUID(snapshot.id());
        entity.attach(snapshot, true);
        return entity;
    }

    public void setStructureInitialized(boolean value) { initialized = value; }
    public void clearStructureForRestore() { contraption = null; }
    public void installStructure(CarriageContraption structure) { setContraption(structure); }
    public void restoreStructure(CarriageContraption structure) { contraption = structure; }
    public void baseDisassemble() { super.disassemble(); }
    public boolean previousPositionInvalid() { return prevPosInvalid; }
    public boolean isPoseData(EntityDataAccessor<?> key) { return DATA_POSE.equals(key); }

    public UUID assemblyId() { return assemblyId; }
    public Direction originalForward() { return originalForward; }
    VehicleConstraintSolverBodyPoseView currentBodyPose() {
        return HexieBridges.vehicle().createVehicleConstraintSolverBodyPose(new Vector(getX(), getY() + .5, getZ()),
                Math.toRadians(yaw), Math.toRadians(pitch), Math.toRadians(roll));
    }
    public SnapshotBogeyAnimation bogeyAnimation() { return bogeyAnimation; }
    public SnapshotCouplerAnimation couplerAnimation() { return couplerAnimation; }
    public boolean hasCoupler(BlockPos position) {
        var endpoint = HexieBridges.coupling().vehicleCouplingCatalog().endpointId(getUUID(), position);
        var owner = motion();
        return owner != null ? owner.runtime().placement().endpoints().containsKey(endpoint)
                : couplerAnimation.parameters().stream().anyMatch(parameter -> parameter.endpoint().equals(endpoint));
    }

    @Override public void updateRenderedPortalCutoff() { /* This carrier has one complete local structure. */ }

    @Override public boolean hasSchedule() {
        return sync.compatibilityCarriage != null && sync.compatibilityCarriage.train.runtime.getSchedule() != null;
    }

    @Override public Component getContraptionName() {
        return sync.compatibilityCarriage == null ? super.getContraptionName() : sync.compatibilityCarriage.train.name;
    }

    @Override public Component getDisplayName() {
        return sync.compatibilityCarriage == null ? super.getDisplayName() : sync.compatibilityCarriage.train.name;
    }

    public void setMotionOwner(UUID owner, Direction forward) {
        if (!forward.getAxis().isHorizontal()) throw new IllegalArgumentException("Vehicle forward must be horizontal");
        motionOwner = owner;
        setCoordinateBasis(forward);
        if (!level().isClientSide) entityData.set(BASIS_FORWARD, forward);
        setInitialOrientation(forward.getClockWise());
        if (contraption instanceof SnapshotCarriageStructure.SnapshotView view) view.configureDirection(forward);
        cachedOwner = null;
    }

    public void installMotion(AssembledVehicleMotion owner) {
        ownedMotion = owner;
        if (owner != null && owner.owns(getUUID())) assemblyId = owner.sourceAssembly(getUUID());
        pendingMotionState = null;
        pendingRelocation = null;
        pendingSourceUnavailable = false;
    }

    private PendingVehicleRelocation pendingRelocation;
    boolean pendingSourceUnavailable;

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
        if (contraption instanceof SnapshotCarriageStructure.SnapshotView view) view.active = true;
        contraption.startMoving(level());
    }

    public void stopBehaviours() {
        if (!behavioursActive) return;
        contraption.stop(level());
        behavioursActive = false;
        contraption.stalled = false;
        if (motion() != null) motion().reportActorStall(getUUID(), false);
    }

    /** Body origin is the centre of snapshot.origin; source geometry remains in its original axes. */
    public void applyMotion(VehicleConstraintSolverBodyPoseView body, Direction forward, VehicleConstraintSolverBodyPoseView planarWorkPose) {
        applyMotion(body, forward, planarWorkPose, forward);
    }

    void applyMotion(VehicleConstraintSolverBodyPoseView body, Direction forward, VehicleConstraintSolverBodyPoseView planarWorkPose, Direction modelForward) {
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
                        owner.isDerailed(getUUID()), modelForward, owner.route()::animationSample);
                JanneyCouplingState.sync(this, owner.runtime(), forward);
                couplerAnimation.update(owner.runtime(), snapshot, forward);
                if (oldFloor != couplerAnimation.floorOffset() && contraption != null) {
                    contraption.invalidateColliders();
                    updateRotatedBounds();
                }
            }
        }
    }

    @Override protected void tickContraption() {
        if (nonDamageTicks > 0) nonDamageTicks--;
        if (level().isClientSide) {
            bindClientCompatibility();
            bindClientPoseTimeline();
            prevYaw = yaw; prevPitch = pitch; prevRoll = roll;
            if (playback.pendingClientMotionFrame != null) {
                applyClientMotionFrame(playback.pendingClientMotionFrame);
                playback.pendingClientMotionFrame = null;
            }
            int steps = Math.max(1, playback.interpolationSteps);
            if (playback.poseInterpolation.initialized()) {
                playback.poseInterpolation.tick(level().getGameTime());
                applyClientPose();
            } else if (playback.interpolationSteps > 0 && playback.interpolationTarget != null) {
                setPos(position().lerp(playback.interpolationTarget, 1.0 / steps));
            }
            if (playback.interpolationSteps > 0) playback.interpolationSteps--;
            updateRotatedBounds();
        } else {
            if (structureImport.awaitingImportedRoute && tickCount >= nextMotionRetryTick) {
                nextMotionRetryTick = tickCount + 100;
                tryBindImportedStructure();
            }
            if (pendingMotionState != null) {
                try {
                    var previousSources = pendingMotionState.get("CarriageSources");
                    AssembledVehicleMotionPersistence.readSources((ServerLevel) level(), pendingMotionState);
                    if (pendingSourceUnavailable || !java.util.Objects.equals(previousSources,
                            pendingMotionState.get("CarriageSources"))) {
                        // New authoritative ownership is usable now, even if an earlier route
                        // attempt scheduled a later retry. Do not keep the stale detector.
                        nextMotionRetryTick = tickCount;
                        pendingRelocation = null;
                    }
                    pendingSourceUnavailable = false;
                    if (pendingRelocation == null && tickCount >= nextMotionRetryTick)
                        pendingRelocation = new PendingVehicleRelocation((ServerLevel) level(), pendingMotionState);
                } catch (AssembledVehicleMotion.SourceUnavailableException temporarilyUnavailable) {
                    pendingSourceUnavailable = true;
                    pendingRelocation = null;
                    nextMotionRetryTick = tickCount + 100;
                }
                if (pendingRelocation != null) pendingRelocation.tick(this);
            }
            if (pendingMotionState != null && tickCount >= nextMotionRetryTick) {
                nextMotionRetryTick = tickCount + 100;
                try {
                    installMotion(AssembledVehicleMotionPersistence.readState((ServerLevel) level(), pendingMotionState));
                    pendingMotionState = null;
                    pendingRelocation = null;
                    ownedMotion.applyCurrent(this);
                } catch (AssembledVehicleMotion.RouteUnavailableException
                        | AssembledVehicleMotion.SourceUnavailableException temporarilyUnavailable) {
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
        if (level().isClientSide) CarriagePlayerSupport.advance(this);
    }

    @Override public void tickActors() {
        if (!canRunBehaviours()) return;
        var train = !level().isClientSide && sync.compatibilityCarriage != null
                && sync.compatibilityCarriage.train instanceof CompatibilityTrain current ? current : null;
        double speedBeforeActors = train == null ? 0 : train.speed;
        usingWorkPose = workPose != null;
        try { super.tickActors(); }
        finally { usingWorkPose = false; }
        var owner = level().isClientSide ? null : motion();
        if (owner != null) {
            owner.reportActorStall(getUUID(), contraption.stalled);
            // Only an actor's write belongs to this callback. The ordinary mirrored speed is an observation.
            if (train != null && train.speed != speedBeforeActors && Double.isFinite(train.speed)
                    && train.speedBeforeStall == null && train.bridge != null && train.bridge.motion() == owner)
                owner.runtime().speedForConsist(getUUID(), CreateTrainBridge.routeSpeed(train.speed));
        }
        // Actor callbacks may retain rotation beyond this tick, so use the working transform then too.
        if (workPose != null) {
            VehicleConstraintSolverBodyPoseView pose = workPose;
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

    public boolean canRunBehaviours() {
        return behavioursActive && (level().isClientSide || motionOwner == null || motion() != null);
    }

    @Override public Vec3 toGlobalVector(Vec3 local, float partialTicks, boolean previousAnchor) {
        if (usingWorkPose) {
            Vec3 rotated = rotateAt(workPose, local.subtract(.5, .5, .5));
            return rotated.add(workPose.position().x(), workPose.position().y(), workPose.position().z());
        }
        return super.toGlobalVector(local, partialTicks, previousAnchor);
    }

    public double sourceYaw() {
        return sourceYaw(originalForward);
    }

    public static double sourceYaw(Direction forward) {
        return switch (forward) { case EAST -> Math.PI / 2; case SOUTH -> Math.PI; case WEST -> -Math.PI / 2; default -> 0; };
    }

    void setCoordinateBasis(Direction forward) {
        if (originalForward == forward.getOpposite()) {
            // A route reversal changes coordinates, not the visible vehicle orientation.
            // Transform both interpolation endpoints together so clients never animate a half-turn.
            yaw += 180; prevYaw += 180;
            pitch = -pitch; prevPitch = -prevPitch;
            roll = -roll; prevRoll = -prevRoll;
        }
        originalForward = forward;
    }

    private Vec3 rotateAt(VehicleConstraintSolverBodyPoseView pose, Vec3 vector) {
        double angle = sourceYaw(), c = Math.cos(angle), s = Math.sin(angle);
        var result = pose.rotate(new Point3(c * vector.x + s * vector.z, vector.y,
                -s * vector.x + c * vector.z));
        return new Vec3(result.x(), result.y(), result.z());
    }

    public org.joml.Quaternionf bodyRotation(float partialTicks) {
        if (level().isClientSide && playback.poseInterpolation.initialized()) return playback.poseInterpolation.rotation(partialTicks);
        return new org.joml.Quaternionf().rotationY((float) Math.toRadians(AngleHelper.angleLerp(partialTicks, prevYaw, yaw)))
                .rotateX((float) Math.toRadians(AngleHelper.angleLerp(partialTicks, prevPitch, pitch)))
                .rotateZ((float) Math.toRadians(AngleHelper.angleLerp(partialTicks, prevRoll, roll)))
                .rotateY((float) sourceYaw());
    }

    @Override public Vec3 applyRotation(Vec3 local, float partialTicks) {
        if (usingWorkPose) return rotateAt(workPose, local);
        var result = bodyRotation(partialTicks).transform(new org.joml.Vector3f((float) local.x, (float) local.y, (float) local.z));
        return new Vec3(result.x, result.y, result.z);
    }

    @Override public Vec3 reverseRotation(Vec3 world, float partialTicks) {
        org.joml.Quaternionf rotation = usingWorkPose
                ? new org.joml.Quaternionf().rotationY((float) workPose.yaw()).rotateX((float) workPose.pitch())
                    .rotateZ((float) workPose.roll()).rotateY((float) sourceYaw())
                : bodyRotation(partialTicks);
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

    public void updateRotatedBounds() {
        if (contraption == null || contraption.bounds == null) return;
        AABB b = localFloorBounds();
        var rotation = bodyRotation(1);
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
        if (level().isClientSide && playback.poseInterpolation.initialized()) {
            Vec3 correction = renderPosition(partialTicks)
                    .subtract(getPrevPositionVec().lerp(position(), partialTicks));
            stack.translate(correction.x, correction.y, correction.z);
        }
        stack.translate(0, .5, 0);
        stack.mulPose(bodyRotation(partialTicks));
        stack.translate(-.5, -.5, -.5);
    }

    /** Shared by rendering, picking and player following; tick collision uses position(). */
    public Vec3 renderPosition(float partialTicks) {
        return level().isClientSide && playback.poseInterpolation.initialized() ? playback.poseInterpolation.position(partialTicks)
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

    public static void freeze(MovementContext context) {
        context.disabled = true;
        context.firstMovement = false;
        context.motion = context.relativeMotion = Vec3.ZERO;
        context.stall = false;
    }
}
