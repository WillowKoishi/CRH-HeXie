package willow.train.hexie.bridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeyRenderer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public interface BogeyRenderBridge {
    SimpleBogeyRendererView createSimpleRenderer(BogeySizes.BogeySize size, PartAnimation animation);

    @FunctionalInterface
    interface PartAnimation {
        void render(SimpleBogeyRendererView renderer, CompoundTag data, float wheelAngle, PoseStack pose,
                int light, VertexConsumer buffer, boolean inContraption);
    }

    BogeySuspensionModelsOperations bogeySuspensionModels();

    interface BogeySuspensionModelsOperations {
        BogeySuspensionModelsModelView crh1aMotor();
        BogeySuspensionModelsModelView crh1aTrailer();
    }

    BogeySuspensionPoseOperations bogeySuspensionPose();

    interface BogeySuspensionPoseOperations {
        BogeySuspensionPoseScopeView push(Matrix4fc bodyInBogey);
        Matrix4f inModelSpace(CompoundTag data, double frameOffset);
    }

    BogeyTopModelOperations bogeyTopModel();

    interface BogeyTopModelOperations {
        ModelData withBogeyData(ModelData inherited, BlockState state, CompoundTag data, boolean carriageView);
    }

    CreateBogeySuspensionPoseOperations createBogeySuspensionPose();

    interface CreateBogeySuspensionPoseOperations {
        Matrix4f relative(CarriageContraptionEntity entity, CarriageBogey bogey, float partialTick);
    }

    DF11GBogeyRendererBackwardDF11GBogeyRendererView createDF11GBogeyRendererBackwardDF11GBogeyRenderer();

    DF11GBogeyRendererQJBackwardBogeyRendererView createDF11GBogeyRendererQJBackwardBogeyRenderer();

    DF11GBogeyRendererQJBogeyRendererOperations df11GBogeyRendererQJBogeyRenderer();

    interface DF11GBogeyRendererQJBogeyRendererOperations {
        DF11GBogeyRendererQJBogeyRendererView create();
        String TRAIN_SPEED_KEY = "HeXieQJTrainSpeed";
        String PHYSICS_BOGEY_KEY = "HeXieQJPhysicsBogey";
    }

    DF11GBogeyRendererSmallDF11GBogeyRendererView createDF11GBogeyRendererSmallDF11GBogeyRenderer();

    HXStandardBogeyRendererCrhTwoAxleRendererView createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizes.BogeySize size,
            PartialModel frame, PartialModel wheel, double frameOffset);

    HXStandardBogeyRendererCrhTwoAxleRendererView createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizes.BogeySize size,
            PartialModel frame, PartialModel wheel, double axleHeight, double frameOffset, double axleHalfSpacing,
            BogeySuspensionModelsModelView suspension);

    HXStandardBogeyRendererPK209PBackwardBogeyRenderView createHXStandardBogeyRendererPK209PBackwardBogeyRender();

    HXStandardBogeyRendererPK209PBogeyRenderView createHXStandardBogeyRendererPK209PBogeyRender();

    HXStandardBogeyRendererPK209PNoMotorBogeyRenderView createHXStandardBogeyRendererPK209PNoMotorBogeyRender();

    HXStandardBogeyRendererSW160BogeyRendererView createHXStandardBogeyRendererSW160BogeyRenderer();

    HXStandardBogeyRendererSW220KBackwardBogeyRendererView createHXStandardBogeyRendererSW220KBackwardBogeyRenderer();

    HXStandardBogeyRendererSW220KBogeyRendererView createHXStandardBogeyRendererSW220KBogeyRenderer();

    HXStandardBogeyRendererSmallHXStandardBogeyRendererView createHXStandardBogeyRendererSmallHXStandardBogeyRenderer();

    SS7DBogeyRendererView createSS7DBogeyRenderer();

    SimurailBogeySuspensionPoseOperations simurailBogeySuspensionPose();

    interface SimurailBogeySuspensionPoseOperations {
        Matrix4f relative(Object bogey, float partialTick);
        Matrix4f relative(Vector3fc offset, Quaternionfc rotation, Quaternionfc restRotation, float axleOffset);
    }

    interface BogeySuspensionModelsModelView {}

    interface BogeySuspensionPoseScopeView extends AutoCloseable {
        void close();
    }

    interface DF11GBogeyRendererBackwardDF11GBogeyRendererView extends SimpleBogeyRendererView {}

    interface DF11GBogeyRendererQJBackwardBogeyRendererView extends DF11GBogeyRendererQJBogeyRendererView {}

    interface DF11GBogeyRendererQJBogeyRendererView extends SimpleBogeyRendererView {}

    interface DF11GBogeyRendererSmallDF11GBogeyRendererView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererCrhTwoAxleRendererView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererPK209PBackwardBogeyRenderView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererPK209PBogeyRenderView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererPK209PNoMotorBogeyRenderView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererSW160BogeyRendererView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererSW220KBackwardBogeyRendererView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererSW220KBogeyRendererView extends SimpleBogeyRendererView {}

    interface HXStandardBogeyRendererSmallHXStandardBogeyRendererView extends SimpleBogeyRendererView {}

    interface SS7DBogeyRendererView extends SimpleBogeyRendererView {}

    interface SimpleBogeyRendererView extends BogeyRenderer {
        BogeyVisual createVisual(VisualizationContext context, float partialTick, boolean inContraption);
        SimpleBogeyRendererBogeyModelDataView getTransform(PartialModel model, PoseStack poseStack,
                boolean instanced);
    }

    interface SimpleBogeyRendererBogeyModelDataView {
        SimpleBogeyRendererBogeyModelDataView transform(Matrix4fc matrix);
        void render(PoseStack poseStack, int light, VertexConsumer buffer);
    }

    interface SimurailBogeySuspensionPoseAccessView {
        Matrix4f hexie$suspensionPose(float partialTick);
    }
}
