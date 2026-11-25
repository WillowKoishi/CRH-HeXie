package willow.train.hexie.render.carriage;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotBogeyAnimationFrameView;
import willow.train.hexie.render.bogey.BogeyRenderData;
import willow.train.hexie.render.bogey.BogeySuspensionPose;

// split from willow/train/hexie/client/render/SnapshotCarriageRenderer.java, lines 72-89
public final class SnapshotBogeyTransforms {
    public static void transform(PoseStack stack, SnapshotBogeyAnimationFrameView frame) {
        stack.translate(frame.x(), frame.y(), frame.z());
        stack.mulPose(frame.rotation());
    }

    public static BogeySuspensionPose.Scope suspensionPose(BogeyRenderData bogey, SnapshotBogeyAnimationFrameView frame) {
        var position = bogey.position();
        var shift = HexieBridges.worldBlock().bogeyFloorData().displacement(bogey.state(), HexieBridges.worldBlock().bogeyFloorData().longitudinal(bogey.data()));
        float floorHeight = (float) HexieBridges.worldBlock().bogeyFloorData().offset(HexieBridges.worldBlock().bogeyFloorData().heightFromBogeyData(bogey.data()));
        boolean xAxis = bogey.state().getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.Z) == Direction.Axis.X;
        return BogeySuspensionPose.push(BogeySuspensionPose.relative(
                new Vector3f(frame.x(), frame.y(), frame.z()), frame.rotation(),
                // The solved carriage already includes floor height. Cancel it only for mechanical attachments.
                new Vector3f((float) (position.getX() + .5 + shift.x), position.getY() + .5f - floorHeight,
                        (float) (position.getZ() + .5 + shift.z)),
                new Quaternionf().rotationY(xAxis ? (float) Math.PI / 2 : 0)));
    }
}
