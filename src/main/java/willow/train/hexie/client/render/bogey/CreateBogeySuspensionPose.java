package willow.train.hexie.client.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.init.track.TrackMaterialInit;

/** Measures the body-mounted top against Create's exact animated bogey transform. */
public final class CreateBogeySuspensionPose {
    private static final float MODEL_Y = (float) (-1.5 - 1 / 128.0 + TrackMaterialInit.RAIL_HEIGHT_INCREASE);

    private CreateBogeySuspensionPose() {}

    public static Matrix4f relative(CarriageContraptionEntity entity, CarriageBogey bogey, float partialTick) {
        var carriage = entity.getCarriage();
        var contraption = entity.getContraption();
        if (carriage == null || contraption == null) return new Matrix4f();

        PoseStack bogeyPose = new PoseStack();
        CarriageContraptionEntityRenderer.translateBogey(bogeyPose, bogey, carriage.bogeySpacing,
                entity.getViewYRot(partialTick), entity.getViewXRot(partialTick), partialTick);
        bogeyPose.translate(0, MODEL_Y, 0);

        BlockPos position = bogey == carriage.leadingBogey() ? BlockPos.ZERO
                : BlockPos.ZERO.relative(entity.getInitialOrientation().getCounterClockWise(), carriage.bogeySpacing);
        var info = contraption.getBlocks().get(position);
        Direction.Axis axis = info == null ? entity.getInitialOrientation().getCounterClockWise().getAxis()
                : info.state().getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.Z);
        double longitudinal = BogeyFloorData.LONGITUDINAL_SETTING.blocks(BogeyFloorData.longitudinal(bogey.bogeyData));
        PoseStack bodyPose = new PoseStack();
        entity.applyLocalTransforms(bodyPose, partialTick);
        Matrix4f top = new Matrix4f(bodyPose.last().pose())
                .translate(position.getX() + .5f + (axis == Direction.Axis.X ? (float) longitudinal : 0),
                        position.getY() + .5f,
                        position.getZ() + .5f + (axis == Direction.Axis.Z ? (float) longitudinal : 0))
                // Create directs its moving bogey along the train, whereas a block axis is unsigned.
                // Match that directed rest frame so north/east assemblies do not join opposite mounts.
                .rotateY((float) Math.toRadians(90 - entity.getInitialYaw()))
                .translate(0, MODEL_Y, 0);
        return new Matrix4f(bogeyPose.last().pose()).invert().mul(top);
    }
}
