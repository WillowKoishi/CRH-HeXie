package willow.train.hexie.render.track;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

// split from willow/train/hexie/client/render/track/BallastCurveBuffers.java, lines 314-327
final class BallastBufferDrawing {
    // Kept for existing callers that draw a single endpoint-local vanilla buffer.
    public static Matrix4f modelView(Matrix4f view, double x, double y, double z) {
        return new Matrix4f(view).translate((float)x, (float)y, (float)z);
    }
    public static void drawBuffer(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection) {
        var layer = RenderType.cutoutMipped();
        layer.setupRenderState();
        try {
            buffer.bind();
            var shader = RenderSystem.getShader();
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0f,0f,0f);
            buffer.drawWithShader(modelView, projection, shader);
        } finally { VertexBuffer.unbind(); layer.clearRenderState(); }
    }
}
