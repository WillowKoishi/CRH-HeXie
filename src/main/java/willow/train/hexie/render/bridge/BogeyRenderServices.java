package willow.train.hexie.render.bridge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import willow.train.hexie.bridge.render.BogeyRenderBridge;
import willow.train.hexie.render.bogey.BogeySuspensionModels;
import willow.train.hexie.render.bogey.BogeySuspensionPose;
import willow.train.hexie.render.bogey.BogeyTopModel;
import willow.train.hexie.render.bogey.CRHTwoAxleBogeyRenderers;
import willow.train.hexie.render.bogey.CreateBogeySuspensionPose;
import willow.train.hexie.render.bogey.DF11GBogeyRenderer;
import willow.train.hexie.render.bogey.HXStandardBogeyRenderer;
import willow.train.hexie.render.bogey.PK209PBogeyRenderers;
import willow.train.hexie.render.bogey.QJRunningGear;
import willow.train.hexie.render.bogey.SS7DBogeyRenderer;
import willow.train.hexie.render.bogey.SW160BogeyRenderers;
import willow.train.hexie.render.bogey.SW220KBogeyRenderers;
import willow.train.hexie.render.bogey.SimpleBogeyRenderer;
import willow.train.hexie.render.bogey.SimurailBogeySuspensionPose;

final class BogeyRenderServices implements BogeyRenderBridge {
    @Override public SimpleBogeyRendererView createSimpleRenderer(BogeySizes.BogeySize size, PartAnimation animation) {
        return new SimpleBogeyRenderer() {
            @Override public BogeySizes.BogeySize getSize() { return size; }

            @Override public void render(CompoundTag data, float wheelAngle, PoseStack pose,
                    int light, VertexConsumer buffer, boolean inContraption) {
                animation.render(this, data, wheelAngle, pose, light, buffer, inContraption);
            }
        };
    }

    private final BogeySuspensionModelsOperations bogeySuspensionModels = new BogeySuspensionModelsOperations() {
        @Override public BogeySuspensionModelsModelView crh1aMotor() {
            return BogeySuspensionModels.CRH1A_MOTOR;
        }
        @Override public BogeySuspensionModelsModelView crh1aTrailer() {
            return BogeySuspensionModels.CRH1A_TRAILER;
        }
    };

    private final BogeySuspensionPoseOperations bogeySuspensionPose = new BogeySuspensionPoseOperations() {
        @Override public BogeySuspensionPoseScopeView push(Matrix4fc bodyInBogey) {
            return BogeySuspensionPose.push(bodyInBogey);
        }
        @Override public Matrix4f inModelSpace(CompoundTag data, double frameOffset) {
            return BogeySuspensionPose.inModelSpace(data, frameOffset);
        }
    };

    private final BogeyTopModelOperations bogeyTopModel = BogeyTopModel::withBogeyData;

    private final CreateBogeySuspensionPoseOperations createBogeySuspensionPose = CreateBogeySuspensionPose::relative;

    private final DF11GBogeyRendererQJBogeyRendererOperations df11GBogeyRendererQJBogeyRenderer = new DF11GBogeyRendererQJBogeyRendererOperations() {
        @Override public DF11GBogeyRendererQJBogeyRendererView create() {
            return new QJRunningGear.QJBogeyRenderer();
        }
    };

    private final SimurailBogeySuspensionPoseOperations simurailBogeySuspensionPose = new SimurailBogeySuspensionPoseOperations() {
        @Override public Matrix4f relative(Object bogey, float partialTick) {
            return SimurailBogeySuspensionPose.relative(bogey, partialTick);
        }
        @Override public Matrix4f relative(Vector3fc offset, Quaternionfc rotation, Quaternionfc restRotation, float axleOffset) {
            return SimurailBogeySuspensionPose.relative(offset, rotation, restRotation, axleOffset);
        }
    };

    @Override public BogeySuspensionModelsOperations bogeySuspensionModels() {
        return bogeySuspensionModels;
    }

    @Override public BogeySuspensionPoseOperations bogeySuspensionPose() {
        return bogeySuspensionPose;
    }

    @Override public BogeyTopModelOperations bogeyTopModel() {
        return bogeyTopModel;
    }

    @Override public CreateBogeySuspensionPoseOperations createBogeySuspensionPose() {
        return createBogeySuspensionPose;
    }

    @Override public DF11GBogeyRendererBackwardDF11GBogeyRendererView createDF11GBogeyRendererBackwardDF11GBogeyRenderer() {
        return new DF11GBogeyRenderer.BackwardDF11GBogeyRenderer();
    }

    @Override public DF11GBogeyRendererQJBackwardBogeyRendererView createDF11GBogeyRendererQJBackwardBogeyRenderer() {
        return new QJRunningGear.QJBackwardBogeyRenderer();
    }

    @Override public DF11GBogeyRendererQJBogeyRendererOperations df11GBogeyRendererQJBogeyRenderer() {
        return df11GBogeyRendererQJBogeyRenderer;
    }

    @Override public DF11GBogeyRendererSmallDF11GBogeyRendererView createDF11GBogeyRendererSmallDF11GBogeyRenderer() {
        return new DF11GBogeyRenderer.SmallDF11GBogeyRenderer();
    }

    @Override public HXStandardBogeyRendererCrhTwoAxleRendererView createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizes.BogeySize size, PartialModel frame, PartialModel wheel, double frameOffset) {
        return new CRHTwoAxleBogeyRenderers.CrhTwoAxleRenderer(size, frame, wheel, frameOffset);
    }

    @Override public HXStandardBogeyRendererCrhTwoAxleRendererView createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizes.BogeySize size, PartialModel frame, PartialModel wheel, double axleHeight, double frameOffset, double axleHalfSpacing, BogeySuspensionModelsModelView suspension) {
        return new CRHTwoAxleBogeyRenderers.CrhTwoAxleRenderer(size, frame, wheel, axleHeight, frameOffset, axleHalfSpacing, (BogeySuspensionModels.Model) suspension);
    }

    @Override public HXStandardBogeyRendererPK209PBackwardBogeyRenderView createHXStandardBogeyRendererPK209PBackwardBogeyRender() {
        return new PK209PBogeyRenderers.PK209PBackwardBogeyRender();
    }

    @Override public HXStandardBogeyRendererPK209PBogeyRenderView createHXStandardBogeyRendererPK209PBogeyRender() {
        return new PK209PBogeyRenderers.PK209PBogeyRender();
    }

    @Override public HXStandardBogeyRendererPK209PNoMotorBogeyRenderView createHXStandardBogeyRendererPK209PNoMotorBogeyRender() {
        return new PK209PBogeyRenderers.PK209PNoMotorBogeyRender();
    }

    @Override public HXStandardBogeyRendererSW160BogeyRendererView createHXStandardBogeyRendererSW160BogeyRenderer() {
        return new SW160BogeyRenderers.SW160BogeyRenderer();
    }

    @Override public HXStandardBogeyRendererSW220KBackwardBogeyRendererView createHXStandardBogeyRendererSW220KBackwardBogeyRenderer() {
        return new SW220KBogeyRenderers.SW220KBackwardBogeyRenderer();
    }

    @Override public HXStandardBogeyRendererSW220KBogeyRendererView createHXStandardBogeyRendererSW220KBogeyRenderer() {
        return new SW220KBogeyRenderers.SW220KBogeyRenderer();
    }

    @Override public HXStandardBogeyRendererSmallHXStandardBogeyRendererView createHXStandardBogeyRendererSmallHXStandardBogeyRenderer() {
        return new HXStandardBogeyRenderer.SmallHXStandardBogeyRenderer();
    }

    @Override public SS7DBogeyRendererView createSS7DBogeyRenderer() {
        return new SS7DBogeyRenderer();
    }

    @Override public SimurailBogeySuspensionPoseOperations simurailBogeySuspensionPose() {
        return simurailBogeySuspensionPose;
    }
}
