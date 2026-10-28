package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.client.sign.SignTextRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import willow.train.hexie.blockentity.GroundStationSignEntity;
import willow.train.hexie.blocks.signs.GroundStationSignBlock;

public class GroundStationSignRenderer implements BlockEntityRenderer<GroundStationSignEntity> {
    private static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath("hexie", "station_name");
    private static final ResourceLocation PINYIN = ResourceLocation.fromNamespaceAndPath("hexie", "station_pinyin");
    private static final ResourceLocation NEIGHBOR = ResourceLocation.fromNamespaceAndPath("hexie", "station_neighbor");
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("hexie", "textures/misc/sign_white.png");
    public GroundStationSignRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(GroundStationSignEntity sign, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        String[] text = sign.fields();
        pose.pushPose(); pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-sign.getBlockState().getValue(GroundStationSignBlock.FACING).toYRot()));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        for (int side = 0; side < 2; side++) {
            pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(side * 180)); pose.translate(0, 0, 1.025);
            renderFace(text, sign.fontStyle(), side != 0, pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();
    }
    /** Shared by the world sign and its editor preview; no second renderer or duplicate typography. */
    public static void renderFace(String[] text, int style, boolean reverse, PoseStack pose, MultiBufferSource buffers, int light) {
        String suffix = switch (style) { case 1 -> "_serif"; case 2 -> "_heavy"; default -> ""; };
        ResourceLocation name = suffix.isEmpty() ? NAME : ResourceLocation.fromNamespaceAndPath("hexie", "station_name" + suffix);
        ResourceLocation pinyin = suffix.isEmpty() ? PINYIN : ResourceLocation.fromNamespaceAndPath("hexie", "station_pinyin" + suffix);
        ResourceLocation neighbor = suffix.isEmpty() ? NEIGHBOR : ResourceLocation.fromNamespaceAndPath("hexie", "station_neighbor" + suffix);
            line(name, text[0], 0, 35.8f, 4.5f, 27, pose, buffers, light);
            line(pinyin, text[1], 0, 30.7f, 2.3f, 27, pose, buffers, light);
            String previous = text[reverse ? 3 : 2], next = text[reverse ? 2 : 3];
            if (!previous.isBlank()) {
                line(neighbor, previous, -9.75f, 27.4f, 2, 8.5f, pose, buffers, light);
                arrow(-2.8f, 26.4f, -1, pose, buffers, light);
            }
            if (!next.isBlank()) {
                line(neighbor, next, 9.75f, 27.4f, 2, 8.5f, pose, buffers, light);
                arrow(2.8f, 26.4f, 1, pose, buffers, light);
            }
    }
    private static void line(ResourceLocation profile, String text, float x, float y, float height, float width,
                      PoseStack pose, MultiBufferSource buffers, int light) {
        SignTextRenderer.drawLine(profile, text, pose, buffers, x, y, height, width,
                0xFF171717, light, SignTextRenderer.Alignment.CENTER);
    }
    /** Solid shaft and triangular head in board coordinates; independent of font glyphs. */
    private static void arrow(float x, float y, int direction, PoseStack pose, MultiBufferSource buffers, int light) {
        var vertex = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float tail = x - direction * 2, neck = x + direction * .75f, tip = x + direction * 2;
        float left = Math.min(tail, neck), right = Math.max(tail, neck);
        point(vertex, pose, left, y - .14f, light);
        point(vertex, pose, right, y - .14f, light);
        point(vertex, pose, right, y + .14f, light);
        point(vertex, pose, left, y + .14f, light);
        // Duplicate the final vertex to encode one triangle in the existing quad render type.
        point(vertex, pose, neck, y - direction * .55f, light);
        point(vertex, pose, tip, y, light);
        point(vertex, pose, neck, y + direction * .55f, light);
        point(vertex, pose, neck, y + direction * .55f, light);
    }
    private static void point(VertexConsumer vertex, PoseStack pose, float x, float y, int light) {
        vertex.addVertex(pose.last().pose(), x, y, 0).setColor(0xFF171717).setUv(.5f, .5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
    }
    @Override public boolean shouldRenderOffScreen(GroundStationSignEntity sign) { return true; }
}
