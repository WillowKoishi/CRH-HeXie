package willow.train.hexie.compat.simurail;

import com.crystaelix.simurail.api.coupler.CouplerType;
import com.crystaelix.simurail.api.math.SimurailMath;
import com.crystaelix.simurail.api.physics.SimurailJoints;
import com.crystaelix.simurail.api.util.SchematicContextUtil;
import com.crystaelix.simurail.api.util.SubLevelUtil;
import com.crystaelix.simurail.config.SimurailConfig;
import com.crystaelix.simurail.content.SimurailCouplers;
import com.crystaelix.simurail.content.automatic_coupler.AutomaticCoupler;
import com.crystaelix.simurail.content.automatic_coupler.AutomaticCouplerBlockEntity;
import com.crystaelix.simurail.content.connector.ConnectorConnectable;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.blocks.base.CarriageViewBlockEntity;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.blocks.coupler.ModelCouplerBlock;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;

import java.util.List;
import java.util.UUID;

/** Optional Simurail mechanics for the existing HeXie coupler blocks. */
public final class PhysicalCouplerBlockEntity extends CouplerBlockEntity
        implements AutomaticCoupler, BlockEntitySubLevelActor, CarriageViewBlockEntity {
    private static final String PHYSICS_DATA = "HexiePhysicalCoupler";
    // The automatic and semiautomatic heads retain their separate HeXie mating identities.
    private static final CouplerType CRH2_AUTO = new CouplerType(
            ResourceLocation.fromNamespaceAndPath("hexie", "crh2_auto"),
            ResourceLocation.fromNamespaceAndPath("hexie", "block/crh2a/auto_coupler/auto_coupler"));
    private static final CouplerType CRH2_SEMIAUTO = new CouplerType(
            ResourceLocation.fromNamespaceAndPath("hexie", "crh2_semiauto"),
            ResourceLocation.fromNamespaceAndPath("hexie", "block/crh2a/endface/coupler"));
    private boolean carriageView;
    private BlockPos partnerPos;
    private UUID partnerSubLevelId;
    private boolean redstoneInput;
    private boolean manualRelease;
    private GenericConstraintHandle joint;
    private double lastJointLength;
    private final Vector3d localJointPos = new Vector3d();
    private final Vector3d partnerJointPos = new Vector3d();
    private final Vector3d localPartnerJointPos = new Vector3d();
    private final Vector3d jointDirection = new Vector3d();
    private final Quaterniond jointRotation = new Quaterniond();
    private final Quaterniond partnerJointRotation = new Quaterniond();

    public PhysicalCouplerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public PhysicalCouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void markCarriageView() {
        carriageView = true;
        removeCouplerJoint();
        partnerPos = null;
        partnerSubLevelId = null;
        redstoneInput = false;
        manualRelease = false;
    }

    @Override public boolean isCarriageView() { return carriageView; }
    public boolean isPhysicallyCoupled() { return partnerPos != null; }
    public BlockPos physicalPartner() { return partnerPos; }

    @Override public void tickCoverAnimation() {
        refreshRedstoneControl();
        if (isRealEntity() && !level.isClientSide && !isPhysicalActor() && partnerPos != null)
            removeCouplerPartner();
        super.tickCoverAnimation();
    }

    @Override public boolean uncouplePhysical() {
        if (!isRealEntity() || Sable.HELPER.getContaining(this) == null) return false;
        if (!level.isClientSide) {
            refreshRedstoneControl();
            if (!redstoneInput) setManualRelease(!manualRelease);
        }
        return true;
    }

    @Override public boolean install(CouplerAdapter adapter) {
        return partnerPos == null && super.install(adapter);
    }

    @Override public CouplerAdapter removeOuter() {
        return partnerPos == null ? super.removeOuter() : null;
    }

    /** Like Simurail's TRIGGERED state, manual release stays latched until toggled or powered. */
    public void uncouple() {
        if (!isPhysicalActor()) return;
        refreshRedstoneControl();
        if (!redstoneInput) setManualRelease(true);
    }

    /** Forwarded neighbor notifications preserve even pulses shorter than one block-entity tick. */
    public void refreshRedstoneControl() {
        if (!isPhysicalActor()) return;
        // An end face owns its interaction floor cell, so its input uses this same block position.
        boolean signal = level.hasNeighborSignal(getBlockPos());
        boolean changed = signal != redstoneInput || signal && manualRelease;
        redstoneInput = signal;
        if (signal) manualRelease = false;
        applyReleaseControl(changed);
    }

    private void setManualRelease(boolean released) {
        if (!isPhysicalActor() || manualRelease == released) return;
        manualRelease = released;
        applyReleaseControl(true);
    }

    private void applyReleaseControl(boolean changed) {
        if (changed) wakeBody(Sable.HELPER.getContaining(this));
        boolean released = (redstoneInput || manualRelease) && partnerPos != null;
        if (released) {
            wakeBody(Sable.HELPER.getContaining(level, partnerPos));
            removeCouplerPartner();
        } else if (redstoneInput || manualRelease) removeCouplerJoint();
        if (changed && !released) syncPhysicalState();
    }

    private void wakeBody(SubLevel owner) {
        if (owner instanceof ServerSubLevel subLevel)
            SubLevelPhysicsSystem.require(level).getPipeline().wakeUp(subLevel);
    }

    private boolean isRealEntity() {
        return !carriageView && !isRemoved() && level != null
                && level.getBlockEntity(getBlockPos()) == this;
    }

    private boolean isPhysicalActor() {
        return isRealEntity() && !level.isClientSide
                && Sable.HELPER.getContaining(this) instanceof ServerSubLevel;
    }

    private CouplerBlock hook() { return CouplerBlock.of(getBlockState()); }

    @Override public Direction getFacing() {
        var hook = hook();
        return hook == null ? Direction.NORTH : hook.facing(getBlockState());
    }

    @Override public boolean isPowered() {
        // Ordinary blocks and captured carriage render views must never become Simurail targets.
        return !isPhysicalActor() || redstoneInput || manualRelease || level.hasNeighborSignal(getBlockPos())
                || !("railways:knuckle".equals(effectiveModel()) || "hexie:crh2_auto".equals(effectiveModel())
                || "hexie:crh2_semiauto".equals(effectiveModel()))
                || getBlockState().getBlock() instanceof ModelCouplerBlock
                && !getBlockState().getValue(BlockStateProperties.OPEN);
    }

    private String effectiveModel() {
        var hook = hook();
        String model = hook == null ? "" : hook.model();
        return adapter() == null ? model : adapter().otherModel(model);
    }

    @Override public CouplerType getCouplerType() {
        String model = effectiveModel();
        if ("railways:knuckle".equals(model)) return SimurailCouplers.KNUCKLE;
        return "hexie:crh2_semiauto".equals(model) ? CRH2_SEMIAUTO : CRH2_AUTO;
    }

    // The installed HeXie head and adapter determine its type; native wrench type cycling is disabled.
    @Override public void setCouplerType(CouplerType type) { }

    private CouplingEndpoint.Point effectiveContact() {
        var hook = hook();
        if (hook == null) return new CouplingEndpoint.Point(0, 0, 0);
        var point = hook.complete();
        return adapter() == null ? point : point.plus(adapter().offsetFrom(hook.model()));
    }

    private Vector3d toStorage(CouplingEndpoint.Point point, Vector3d dest) {
        double x = point.x(), z = point.z();
        switch (getFacing()) {
            case EAST -> { x = -point.z(); z = point.x(); }
            case SOUTH -> { x = -point.x(); z = -point.z(); }
            case WEST -> { x = point.z(); z = -point.x(); }
            default -> { }
        }
        return dest.set(getBlockPos().getX() + .5 + x, getBlockPos().getY() + .5 + point.y(),
                getBlockPos().getZ() + .5 + z);
    }

    @Override public Vector3d getCouplerEndPos(Vector3d dest) { return toStorage(effectiveContact(), dest); }

    @Override public Vector3d getCouplerJointPos(Vector3d dest) {
        var hook = hook();
        return toStorage(hook == null ? new CouplingEndpoint.Point(0, 0, 0)
                : hook.axis().atHeight(effectiveContact().y()), dest);
    }

    @Override public double getCouplerLength() {
        var hook = hook();
        var end = effectiveContact();
        return hook == null ? 0 : Math.hypot(end.x() - hook.axis().x(), end.z() - hook.axis().z());
    }

    private AutomaticCoupler partner() {
        return level != null && partnerPos != null && level.isLoaded(partnerPos)
                && level.getBlockEntity(partnerPos) instanceof AutomaticCoupler other ? other : null;
    }

    private UUID subLevelId(BlockPos pos) {
        SubLevel owner = Sable.HELPER.getContaining(level, pos);
        return owner == null ? null : owner.getUniqueId();
    }

    @Override public void setCouplerPartner(BlockPos pos) {
        if (isPowered() || pos == null || pos.equals(getBlockPos()) || !level.isLoaded(pos)
                || !(level.getBlockEntity(pos) instanceof AutomaticCoupler other) || other.isPowered()
                || other.hasCouplerPartner() || !getCouplerType().canConnectTo(other.getCouplerType())) return;
        SubLevel otherOwner = Sable.HELPER.getContaining(level, pos);
        if (!(otherOwner instanceof ServerSubLevel) || otherOwner == Sable.HELPER.getContaining(this)) return;
        if (partnerPos != null) removeCouplerPartner();
        partnerPos = pos.immutable();
        partnerSubLevelId = subLevelId(pos);
        other.setCouplerPartnerReverse(getBlockPos());
        syncPhysicalState();
    }

    @Override public void setCouplerPartnerReverse(BlockPos pos) {
        if (pos != null) {
            if (isPowered() || !level.isLoaded(pos)) return;
            SubLevel otherOwner = Sable.HELPER.getContaining(level, pos);
            if (!(otherOwner instanceof ServerSubLevel) || otherOwner == Sable.HELPER.getContaining(this)) return;
            if (partnerPos != null && !partnerPos.equals(pos)) return;
        }
        partnerPos = pos == null ? null : pos.immutable();
        partnerSubLevelId = pos == null ? null : subLevelId(pos);
        if (pos == null) removeCouplerJoint();
        syncPhysicalState();
    }

    @Override public void removeCouplerPartner() {
        var other = partner();
        partnerPos = null;
        partnerSubLevelId = null;
        removeCouplerJoint();
        if (other != null && reciprocalPartner(other)) {
            other.setCouplerPartnerReverse(null);
            other.removeCouplerJoint();
        }
        syncPhysicalState();
    }

    @Override public boolean hasCouplerPartner() { return partnerPos != null; }
    @Override public boolean hasCouplerJoint() { return joint != null && joint.isValid(); }

    @Override public void removeCouplerJoint() {
        if (joint != null) {
            joint.remove();
            joint = null;
            lastJointLength = 0;
        }
    }

    private void syncPhysicalState() {
        if (!isRealEntity() || level.isClientSide) return;
        updatePhysicalCouplingAnimation(partnerPos != null);
    }

    @Override public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!isPhysicalActor() || Sable.HELPER.getContaining(this) != subLevel) {
            removeCouplerJoint();
            return;
        }
        refreshRedstoneControl();
        if (isPowered()) {
            if (partnerPos != null) removeCouplerPartner();
            else removeCouplerJoint();
            return;
        }
        if (partnerPos == null) findPartner(subLevel);
        var other = partner();
        if (other == null) {
            // An unloaded partner retains its saved identity and can rebuild the joint when loaded.
            if (partnerPos != null && !level.isLoaded(partnerPos)) removeCouplerJoint();
            else if (partnerPos != null) removeCouplerPartner();
            return;
        }
        SubLevel owner = Sable.HELPER.getContaining(level, other.getBlockPos());
        if (owner == subLevel || !(owner instanceof ServerSubLevel)
                || other.isPowered() || !getCouplerType().canConnectTo(other.getCouplerType())
                || !reciprocalPartner(other)) {
            removeCouplerPartner();
            return;
        }
        if (hasCouplerJoint() && other.hasCouplerJoint()) other.removeCouplerJoint();
        if (other.hasCouplerJoint()) return;
        var otherSubLevel = (ServerSubLevel) owner;
        Pose3dc selfPose = subLevel.logicalPose();
        Pose3dc otherPose = otherSubLevel.logicalPose();
        updateCouplerJointPos(other, otherPose);
        other.updateCouplerJointPos(this, selfPose);
        double selfScale = getFacing().getAxis() == Direction.Axis.Z ? selfPose.scale().z() : selfPose.scale().x();
        double otherScale = other.getFacing().getAxis() == Direction.Axis.Z ? otherPose.scale().z() : otherPose.scale().x();
        double length = getCouplerLength() * selfScale + other.getCouplerLength() * otherScale;
        if (localJointPos.distanceSquared(localPartnerJointPos) * selfScale * selfScale > Mth.square(length + 1)) {
            removeCouplerPartner();
            return;
        }
        getCouplerJointRot(jointRotation);
        other.getCouplerJointRot(partnerJointRotation);
        var config = SimurailConfig.server().physics;
        double frequency = config.couplerSpringFrequency.get();
        double stiffness = frequency * frequency;
        double damping = 2 * frequency * config.couplerSpringDampingRate.get();
        var physics = SubLevelPhysicsSystem.require(level);
        if (!hasCouplerJoint()) {
            removeCouplerJoint();
            joint = physics.getPipeline().addConstraint(subLevel, otherSubLevel,
                    SimurailJoints.freeJoint(localJointPos, partnerJointPos, jointRotation, partnerJointRotation));
            double linearDamping = config.couplerPassiveLinearDamping.get();
            double angularDamping = config.couplerPassiveAngularDamping.get();
            joint.setMotor(ConstraintJointAxis.LINEAR_Y, 0, 0, linearDamping, false, 0);
            joint.setMotor(ConstraintJointAxis.LINEAR_Z, 0, 0, linearDamping, false, 0);
            joint.setMotor(ConstraintJointAxis.ANGULAR_X, 0, 0, angularDamping, false, 0);
            joint.setMotor(ConstraintJointAxis.ANGULAR_Y, 0, 0, angularDamping, false, 0);
            joint.setMotor(ConstraintJointAxis.ANGULAR_Z, 0, 0, angularDamping, false, 0);
            lastJointLength = Double.NaN;
        } else {
            joint.setFrame1(localJointPos, jointRotation);
            joint.setFrame2(partnerJointPos, partnerJointRotation);
        }
        if (length != lastJointLength) {
            joint.setLimit(ConstraintJointAxis.LINEAR_X, length - .5, length + .5);
            joint.setMotor(ConstraintJointAxis.LINEAR_X, length, stiffness, damping, false, 0);
            physics.getPipeline().wakeUp(subLevel);
            lastJointLength = length;
        }
    }

    private boolean reciprocalPartner(AutomaticCoupler other) {
        if (other instanceof PhysicalCouplerBlockEntity physical) return getBlockPos().equals(physical.partnerPos);
        if (other instanceof AutomaticCouplerBlockEntity nativeCoupler) return getBlockPos().equals(nativeCoupler.getPartner());
        return other.hasCouplerPartner();
    }

    private void findPartner(ServerSubLevel self) {
        Vector3d globalEnd = self.logicalPose().transformPosition(getCouplerEndPos(new Vector3d()));
        Vector3d localEnd = new Vector3d(), candidateEnd = new Vector3d();
        var cursor = new BlockPos.MutableBlockPos();
        BlockPos closest = null;
        double distance = .02;
        for (SubLevel candidateOwner : SubLevelUtil.getIntersectingSubLevels(level, globalEnd, 2)) {
            if (!(candidateOwner instanceof ServerSubLevel) || candidateOwner == self) continue;
            Pose3dc pose = candidateOwner == null ? SimurailMath.POSE_I : candidateOwner.logicalPose();
            pose.transformPositionInverse(globalEnd, localEnd);
            for (int x = Mth.floor(localEnd.x) - 2; x < Mth.ceil(localEnd.x) + 2; x++)
                for (int y = Mth.floor(localEnd.y) - 2; y < Mth.ceil(localEnd.y) + 2; y++)
                    for (int z = Mth.floor(localEnd.z) - 2; z < Mth.ceil(localEnd.z) + 2; z++) {
                        cursor.set(x, y, z);
                        if (!level.isLoaded(cursor) || !(level.getBlockEntity(cursor) instanceof AutomaticCoupler candidate)
                                || candidate.hasCouplerPartner() || candidate.isPowered()
                                || !getCouplerType().canConnectTo(candidate.getCouplerType())) continue;
                        double candidateDistance = candidate.getCouplerEndPos(candidateEnd).distanceSquared(localEnd);
                        if (candidateDistance < distance) {
                            closest = cursor.immutable();
                            distance = candidateDistance;
                        }
                    }
        }
        if (closest != null) setCouplerPartner(closest);
    }

    @Override public void updateCouplerJointPos(AutomaticCoupler other, Pose3dc otherPose) {
        SubLevel self = Sable.HELPER.getContaining(this);
        Pose3dc selfPose = self == null ? SimurailMath.POSE_I : self.logicalPose();
        getCouplerJointPos(localJointPos);
        other.getCouplerJointPos(partnerJointPos);
        selfPose.transformPositionInverse(otherPose.transformPosition(partnerJointPos, localPartnerJointPos));
        localPartnerJointPos.sub(localJointPos, jointDirection);
    }

    @Override public Quaterniond getCouplerJointRot(Quaterniond dest) {
        return jointDirection.lengthSquared() < 1e-20 ? SimurailMath.rot(getDirection(), dest)
                : SimurailMath.rot(jointDirection, dest);
    }

    @Override public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (!carriageView && level != null && partnerSubLevelId != null) {
            SubLevel dependency = SubLevelContainer.getContainer(level).getSubLevel(partnerSubLevelId);
            if (dependency != null) return List.of(dependency);
        }
        return List.of();
    }

    // Physical couplers do not participate in connector/control propagation in this integration.
    @Override public boolean canConnectTo(Direction selfDir, ConnectorConnectable other, Direction otherDir) { return false; }
    @Override public double connectionRange(ConnectorConnectable other) { return 0; }
    @Override public void connect(boolean front, ConnectorConnectable other, boolean otherFront) { }
    @Override public void disconnect(boolean front) { }
    @Override public BlockPos getConnectedBogeyPos() { return null; }
    @Override public boolean getConnectedBogeyFront() { return false; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (carriageView) return;
        var data = new CompoundTag();
        var transformed = SchematicContextUtil.writeTransform(partnerPos, partnerSubLevelId);
        if (transformed.getFirst() != null) data.put("Partner", NbtUtils.writeBlockPos(transformed.getFirst()));
        if (transformed.getSecond() != null) data.putUUID("PartnerSubLevel", transformed.getSecond());
        data.putBoolean("RedstoneInput", redstoneInput);
        data.putBoolean("ManualRelease", manualRelease);
        tag.put(PHYSICS_DATA, data);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        removeCouplerJoint();
        var data = tag.getCompound(PHYSICS_DATA);
        var transformed = SchematicContextUtil.readTransform(NbtUtils.readBlockPos(data, "Partner").orElse(null),
                data.hasUUID("PartnerSubLevel") ? data.getUUID("PartnerSubLevel") : null);
        partnerPos = carriageView ? null : transformed.getFirst();
        partnerSubLevelId = carriageView ? null : transformed.getSecond();
        redstoneInput = !carriageView && data.getBoolean("RedstoneInput");
        manualRelease = !carriageView && data.getBoolean("ManualRelease");
    }

    @Override public void setRemoved() {
        if (!carriageView && level != null && !level.isClientSide
                && level.getBlockState(getBlockPos()).getBlock() != getBlockState().getBlock())
            removeCouplerPartner();
        else removeCouplerJoint();
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        removeCouplerJoint();
        var other = partner();
        if (!carriageView && level != null && !level.isClientSide && other != null && reciprocalPartner(other))
            other.removeCouplerJoint();
        super.onChunkUnloaded();
    }
}
