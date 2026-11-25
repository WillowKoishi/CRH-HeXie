package willow.train.hexie.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static willow.train.hexie.render.bogey.BogeySuspensionModels.Attachment;
import static willow.train.hexie.render.bogey.BogeySuspensionModels.Model;
import static willow.train.hexie.render.bogey.BogeySuspensionModels.Part;

// split from willow/train/hexie/client/render/bogey/BogeySuspensionModels.java, lines 164-191
final class BogeySuspensionPartsRenderer {
    static void render(SimpleBogeyRenderer renderer, Model model, CompoundTag data, PoseStack pose,
                       int light, VertexConsumer buffer) {
        render(renderer, model, data, pose, light, buffer, 0);
    }

    static void render(SimpleBogeyRenderer renderer, Model model, CompoundTag data, PoseStack pose,
                       int light, VertexConsumer buffer, float modelYaw) {
        Matrix4f body = BogeySuspensionPose.inModelSpace(data, model.frameOffset);
        if (modelYaw != 0) {
            float radians = (float) Math.toRadians(modelYaw);
            body = new Matrix4f().rotationY(-radians).mul(body).rotateY(radians);
        }
        for (Part part : model.parts) {
            Matrix4f transform = switch (part.attachment()) {
                case BODY -> body;
                case FRAME -> new Matrix4f();
                case TRANSLATION -> BogeyLinkTransform.translationAt(body, part.bodyAnchor());
                case LINK, SLEEVE -> BogeyLinkTransform.between(part.frameAnchor(), part.bodyAnchor(),
                        body.transformPosition(part.bodyAnchor(), new Vector3f()), part.attachment() == Attachment.LINK);
                case BODY_SLEEVE -> BogeyLinkTransform.between(part.bodyAnchor(), part.frameAnchor(),
                        body.transformPosition(part.bodyAnchor(), new Vector3f()), part.frameAnchor(), false);
                case PISTON -> BogeyLinkTransform.telescopicPiston(part.frameAnchor(), part.bodyAnchor(),
                        body.transformPosition(part.bodyAnchor(), new Vector3f()), part.pistonReach());
            };
            renderer.getTransform(part.model(), pose, buffer == null).rotateY(modelYaw).translate(0, model.frameOffset, 0)
                    .transform(transform).render(pose, light, buffer);
        }
    }
}
