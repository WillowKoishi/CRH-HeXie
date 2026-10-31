package willow.train.hexie.client.cab;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import willow.train.hexie.api.cab.CabDisplaySource;
import willow.train.hexie.api.cab.CabDisplayState;

/** Shared 800x600 LCD for both fixed-size shells. Rendering has no control side effects. */
public final class Ctcs2DmiDisplay implements CabDisplay {
    public static final Ctcs2DmiDisplay INSTANCE = new Ctcs2DmiDisplay(16000);
    private final int planningRangeM;
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("hexie", "textures/misc/sign_white.png");


    /** The future console may select the manual's 8 / 16 km planning range without resizing the LCD. */
    public Ctcs2DmiDisplay(int planningRangeM) {
        if (planningRangeM != 8000 && planningRangeM != 16000)
            throw new IllegalArgumentException("DMI planning range must be 8 or 16 km");
        this.planningRangeM = planningRangeM;
    }
    @Override public int width() { return 800; }
    @Override public int height() { return 600; }

    /** Metres (= blocks), relative to the shell front centre; keep the shell mount a rigid transform.
     * The LCD sits 0.1 mm ahead of its backing. No arbitrary display-size parameter is exposed here. */
    public Matrix4f mainSurface() {
        return new Matrix4f().translation(-.106f, .072f, -.0024f)
                .scale(.211328f / width(), -.158496f / height(), .211328f / width());
    }

    /** Call from a future console model marker; does not register a renderer or choose an installation. */
    public void renderOnShell(CabDisplaySource source, Matrix4fc shellToModel,
                              PoseStack pose, MultiBufferSource buffers) {
        renderMounted(source, new Matrix4f(shellToModel).mul(mainSurface()), pose, buffers);
    }

    @Override public void render(CabDisplayState state, PoseStack pose, MultiBufferSource buffers) {
        Ctcs2DmiLayout.render(state, planningRangeM, new Draw(pose, buffers));
    }

    private static final class Draw implements Ctcs2DmiLayout.Canvas {
        private final PoseStack pose;
        private final MultiBufferSource buffers;
        Draw(PoseStack pose, MultiBufferSource buffers) { this.pose = pose; this.buffers = buffers; }
        public void icon(String name, float x, float y, float w, float h) {
            depth += .0001f;
            var texture = ResourceLocation.fromNamespaceAndPath("hexie", "textures/component/dmi/display/" + name + ".png");
            var out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
            imageVertex(out,x,y,0,0); imageVertex(out,x+w,y,1,0);
            imageVertex(out,x+w,y+h,1,1); imageVertex(out,x,y+h,0,1);
        }
        private void imageVertex(VertexConsumer out,float x,float y,float u,float v) {
            out.addVertex(pose.last().pose(),x,y,depth).setColor(0xffffffff).setUv(u,v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
        }
        public void rect(float x, float y, float w, float h, int color) {
            quad(x, y, x + w, y, x + w, y + h, x, y + h, color);
        }
        public void line(float x, float y, float ex, float ey, float width, int color) {
            double length = Math.hypot(ex - x, ey - y);
            if (length < .0001) return;
            float dx = (float) (-(ey - y) / length * width / 2), dy = (float) ((ex - x) / length * width / 2);
            quad(x + dx, y + dy, ex + dx, ey + dy, ex - dx, ey - dy, x - dx, y - dy, color);
        }
        private float depth = 0;
        public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
            // Depth increment prevents coplanar panels/marks; all geometry remains behind front panel.
            depth += .0001f;
            var out = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            vertex(out, x1, y1, color); vertex(out, x2, y2, color);
            vertex(out, x3, y3, color); vertex(out, x4, y4, color);
        }
        void vertex(VertexConsumer out, float x, float y, int color) {
            out.addVertex(pose.last().pose(), x, y, depth).setColor(color).setUv(.5f, .5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
        }
        public void center(String text, float x, float y, float scale, int color) {
            text(text, x - Minecraft.getInstance().font.width(text) * scale / 2, y, scale, color);
        }
        public void fitted(String text, float x, float y, float width, float scale, int color) {
            text(text, x, y, Math.min(scale, width / Math.max(1, Minecraft.getInstance().font.width(text))), color);
        }
        public void text(String text, float x, float y, float scale, int color) {
            pose.pushPose(); pose.translate(x, y, depth += .0001f); pose.scale(scale, scale, 1);
            Minecraft.getInstance().font.drawInBatch(text, 0, 0, color, false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }
}
