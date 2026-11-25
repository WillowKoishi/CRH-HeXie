package willow.train.hexie.compat.create.assembly;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CarriageStructureTransform;
import willow.train.hexie.compat.create.motion.ImportedCarriageCompatibility;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageImport {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageImport(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    public boolean importingStructure;
    public boolean awaitingImportedRoute;
    public UUID importedOwner;
    private float importMirrorYawDelta;

    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (importingStructure) {
            carriageEntity.yaw -= net.minecraft.util.Mth.wrapDegrees(yRot - carriageEntity.getYRot() - importMirrorYawDelta);
            carriageEntity.prevYaw = carriageEntity.yaw;
            importMirrorYawDelta = 0;
        }
        carriageEntity.baseMoveTo(x, y, z, yRot, xRot);
        if (importingStructure) carriageEntity.updateRotatedBounds();
    }

    public float mirror(net.minecraft.world.level.block.Mirror mirror) {
        float result = carriageEntity.baseMirror(mirror);
        if (!importingStructure || mirror == net.minecraft.world.level.block.Mirror.NONE) return result;
        importMirrorYawDelta += result - carriageEntity.getYRot();
        var reflection = new org.joml.Matrix3f().scaling(
                mirror == net.minecraft.world.level.block.Mirror.FRONT_BACK ? -1 : 1, 1,
                mirror == net.minecraft.world.level.block.Mirror.LEFT_RIGHT ? -1 : 1);
        var reflected = new org.joml.Matrix3f(reflection).mul(new org.joml.Matrix3f().set(carriageEntity.bodyRotation(1))).mul(reflection);
        var angles = new org.joml.Quaternionf().setFromNormalized(reflected).rotateY((float) -carriageEntity.sourceYaw())
                .getEulerAnglesYXZ(new org.joml.Vector3f());
        var transformed = CarriageStructureTransform.mirror(carriageEntity.level(), carriageEntity.snapshot, carriageEntity.writeWorkingState(true), mirror);
        var bogeys = CarriageStructureTransform.copyAnimation(carriageEntity.getUUID(), carriageEntity.bogeyAnimation.write(), mirror);
        var couplers = CarriageStructureTransform.copyAnimation(carriageEntity.getUUID(), carriageEntity.couplerAnimation.write(), mirror);
        Vec3 position = carriageEntity.position();
        carriageEntity.attach(transformed.snapshot(), false);
        carriageEntity.readWorkingState(transformed.working());
        carriageEntity.setPos(position);
        carriageEntity.yaw = carriageEntity.prevYaw = (float) Math.toDegrees(angles.y);
        carriageEntity.pitch = carriageEntity.prevPitch = (float) Math.toDegrees(angles.x);
        carriageEntity.roll = carriageEntity.prevRoll = (float) Math.toDegrees(angles.z);
        carriageEntity.readBogeyAnimation(bogeys, 0);
        carriageEntity.readCouplerAnimation(couplers, 0);
        carriageEntity.updateRotatedBounds();
        return result;
    }

    public void onAddedToLevel() {
        carriageEntity.baseOnAddedToLevel();
        if (!importingStructure || !(carriageEntity.level() instanceof ServerLevel server)) return;
        // SchematicPrinter creates and transforms entities in its temporary level first.
        // Only their real insertion may register a new independent structure.
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(server);
        store.importAssembly(carriageEntity.assemblyId, importedOwner == null ? new UUID(0, 0) : importedOwner,
                carriageEntity.blockPosition(), carriageEntity.originalForward, List.of(carriageEntity.snapshot));
        importingStructure = false;
        awaitingImportedRoute = true;
        for (var actor : carriageEntity.getContraption().getActors()) {
            actor.right.position = null;
            actor.right.motion = actor.right.relativeMotion = Vec3.ZERO;
            actor.right.firstMovement = true;
        }
        tryBindImportedStructure();
        carriageEntity.syncCarriage();
    }

    public void tryBindImportedStructure() {
        if (!awaitingImportedRoute || !(carriageEntity.level() instanceof ServerLevel server)) return;
        var blocks = carriageEntity.currentBlockData().values().stream()
                .map(info -> HexieBridges.vehicle().createCarriageSnapshotBlock(info.pos(), info.state(), info.nbt())).toList();
        var current = HexieBridges.vehicle().carriageSnapshot().create(carriageEntity.getUUID(), server.dimension(), carriageEntity.snapshot.origin(), blocks,
                carriageEntity.snapshot.supports(), carriageEntity.snapshot.glue());
        var prepared = HexieBridges.vehicle().vehicleAssemblyPreparer().prepare(List.of(current), carriageEntity.originalForward,
                HexieBridges.bogeyRegistry().bogeyModelCatalog().production(),
                HexieBridges.coupling().vehicleCouplingCatalog()::decode);
        var route = CreateImportedCarriageRoute.forImportedCarriage(carriageEntity, prepared);
        if (route == null) {
            ImportedCarriageCompatibility.bind(carriageEntity, prepared, importedOwner == null ? new UUID(0, 0) : importedOwner);
            awaitingImportedRoute = false;
            return;
        }
        var motion = AssembledVehicleMotion.create(server, carriageEntity.assemblyId, prepared, route);
        carriageEntity.setMotionOwner(carriageEntity.getUUID(), carriageEntity.originalForward);
        carriageEntity.installMotion(motion);
        motion.applyCurrent(carriageEntity);
        motion.ensureCompatibility();
        awaitingImportedRoute = false;
    }
}
