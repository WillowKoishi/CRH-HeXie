package willow.train.hexie.client.cab;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import willow.train.hexie.api.cab.CabDisplaySource;
import willow.train.hexie.api.cab.CabDisplayState;

/** A model-independent display. Pixel coordinates run right and down; +Z faces the driver. */
public interface CabDisplay {
    int width();
    int height();
    void render(CabDisplayState state, PoseStack pose, MultiBufferSource buffers);

    /** A console supplies only its data source and the pixel-to-model mounting transform. */
    default void renderMounted(CabDisplaySource source, Matrix4fc pixelToModel,
                               PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.mulPose(new Matrix4f(pixelToModel));
        render(source.displayState(), pose, buffers);
        pose.popPose();
    }
}
