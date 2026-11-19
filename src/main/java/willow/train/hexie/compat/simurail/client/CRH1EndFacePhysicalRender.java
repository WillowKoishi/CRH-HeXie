package willow.train.hexie.compat.simurail.client;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import willow.train.hexie.blocks.emu.crh1a.CRH1EndFaceBlock;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;
import willow.train.hexie.compat.sable.SableInteractionCoordinates.Frame;
import willow.train.hexie.compat.simurail.PhysicalCouplerBlockEntity;
import willow.train.hexie.vehicle.runtime.CouplerAngles;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Convert two interpolated Sable body poses into the existing gangway contact representation. */
public final class CRH1EndFacePhysicalRender {
    private CRH1EndFacePhysicalRender() {}

    public static SnapshotCouplerAnimation.Parameters parameters(BlockEntity display, float partialTick) {
        if (!(display instanceof PhysicalCouplerBlockEntity physical) || physical.isCarriageView()
                || !physical.isPhysicallyCoupled() || physical.getLevel() == null) return null;
        var level = physical.getLevel();
        var own = physical.getBlockPos();
        var other = physical.getCouplerPartner();
        if (other == null || !level.hasChunkAt(other)
                || !(level.getBlockState(other).getBlock() instanceof CRH1EndFaceBlock)
                || !SableInteractionCoordinates.contains(level, own) || !SableInteractionCoordinates.contains(level, other))
            return null;
        var ownFrame = SableInteractionCoordinates.render(level, own, partialTick);
        var otherFrame = SableInteractionCoordinates.render(level, other, partialTick);
        var partner = relativePose(own, physical.getFacing(), ownFrame, other,
                level.getBlockState(other).getValue(CRH1EndFaceBlock.FACING), otherFrame);
        UUID endpoint = UUID.nameUUIDFromBytes(("crh1a:" + own.asLong()).getBytes(StandardCharsets.UTF_8));
        return new SnapshotCouplerAnimation.Parameters(endpoint, own, 0, CouplerAngles.ZERO, null, true, partner);
    }

    public static SnapshotCouplerAnimation.PartnerPose relativePose(BlockPos own, Direction ownFacing, Frame ownFrame,
            BlockPos other, Direction otherFacing, Frame otherFrame) {
        Vec3 origin = transform(Vec3.ZERO, own, ownFacing, ownFrame, other, otherFacing, otherFrame);
        Vec3 x = transform(new Vec3(1, 0, 0), own, ownFacing, ownFrame, other, otherFacing, otherFrame).subtract(origin).normalize();
        Vec3 y = transform(new Vec3(0, 1, 0), own, ownFacing, ownFrame, other, otherFacing, otherFrame).subtract(origin).normalize();
        Vec3 z = transform(new Vec3(0, 0, 1), own, ownFacing, ownFrame, other, otherFacing, otherFrame).subtract(origin).normalize();
        var matrix = new Matrix3f().m00((float) x.x).m01((float) x.y).m02((float) x.z)
                .m10((float) y.x).m11((float) y.y).m12((float) y.z)
                .m20((float) z.x).m21((float) z.y).m22((float) z.z);
        var rotation = new Quaternionf().setFromNormalized(matrix).normalize();
        UUID endpoint = UUID.nameUUIDFromBytes(("crh1a:" + other.asLong()).getBytes(StandardCharsets.UTF_8));
        return new SnapshotCouplerAnimation.PartnerPose(endpoint, new Point(origin.x, origin.y, origin.z),
                rotation.x, rotation.y, rotation.z, rotation.w);
    }

    private static Vec3 transform(Vec3 point, BlockPos own, Direction ownFacing, Frame ownFrame,
                                  BlockPos other, Direction otherFacing, Frame otherFrame) {
        Vec3 otherStorage = rotate(point, otherFacing).add(other.getX(), other.getY(), other.getZ());
        Vec3 ownStorage = ownFrame.toStorage(otherFrame.toWorld(otherStorage))
                .subtract(own.getX(), own.getY(), own.getZ());
        Direction inverse = switch (ownFacing) {
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            default -> ownFacing;
        };
        return rotate(ownStorage, inverse);
    }

    private static Vec3 rotate(Vec3 point, Direction facing) {
        double x = point.x - .5, z = point.z - .5;
        return switch (facing) {
            case EAST -> new Vec3(.5 - z, point.y, .5 + x);
            case SOUTH -> new Vec3(.5 - x, point.y, .5 - z);
            case WEST -> new Vec3(.5 + z, point.y, .5 - x);
            default -> point;
        };
    }
}
