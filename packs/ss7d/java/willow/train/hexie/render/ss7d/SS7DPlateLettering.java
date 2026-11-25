package willow.train.hexie.render.ss7d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.bridge.world.CarriageBlockBridge.SS7DHeadGeometryPlateView;
import willow.train.hexie.render.sign.SignTextMetrics;
import willow.train.hexie.render.sign.SignTextRenderer;

// split from willow/train/hexie/client/render/ss7d/SS7DHeadRenderer.java, lines 26-29, 99-150
final class SS7DPlateLettering {
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("hexie", "ss7d_number");
    private static final ResourceLocation DEPOT_FONT = ResourceLocation.fromNamespaceAndPath("hexie", "ss7d_depot");
    private static final int PLATE_LETTERING = 0xFFF8F7F3;
    private static final int BODY_LETTERING = 0xFF9E1818;

    static void drawPlate(SS7DHeadGeometryPlateView plate, String digits, boolean sidePlates, PoseStack pose,
                                  MultiBufferSource buffers, int light) {
        pose.pushPose();
        var center = plate.surface(sidePlates);
        pose.translate(center.x, center.y, center.z);
        var normal = plate.normal();
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(normal.x, normal.z)));
        float width = plate.width() * .94f;
        float height = plate.height() * .90f;
        float bottom = -height / 2;
        int color = plate.isSide() && !sidePlates ? BODY_LETTERING : PLATE_LETTERING;
        glyphs("SS", -width / 2, width * .275f, bottom, height, pose, buffers, light, color);
        glyphs("7D", -width * .222f, width * .16f, bottom, height * .55f, pose, buffers, light, color);
        glyphs(digits, -width * .058f, width * .558f, bottom, height, pose, buffers, light, color);
        pose.popPose();
    }

    private static void glyphs(String text, float left, float width, float bottom,
                               float height, PoseStack pose, MultiBufferSource buffers, int light, int color) {
        var inkHeight = SignTextMetrics.height(FONT, text, height, width);
        SignTextRenderer.drawLine(FONT, text, pose, buffers, left + width / 2, bottom + inkHeight, height, width,
                color, light, SignTextRenderer.Alignment.CENTER, true);
    }

    static void drawDepot(SS7DHeadGeometryPlateView plate, String text, PoseStack pose,
                                  MultiBufferSource buffers, int light) {
        pose.pushPose();
        var side = plate.printedCenter();
        pose.translate(side.x, 1.4, side.z);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(plate.normal().x, plate.normal().z)));
        float height = .15f;
        float width = .64f;
        float inkHeight = SignTextMetrics.height(DEPOT_FONT, text, height, width);
        SignTextRenderer.drawLine(DEPOT_FONT, text, pose, buffers, 0, inkHeight / 2,
                height, width, PLATE_LETTERING, light, SignTextRenderer.Alignment.CENTER, true);
        pose.popPose();
    }

    /** The cab-end mark is below the depot inscription and toward the locomotive front. */
    static void drawEndLabel(SS7DHeadGeometryPlateView plate, String text, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        pose.pushPose();
        var side = plate.printedCenter();
        pose.translate(side.x, 1.1125, side.z - .203125);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(plate.normal().x, plate.normal().z)));
        float height = .11f;
        float width = .14f;
        float inkHeight = SignTextMetrics.height(DEPOT_FONT, text, height, width);
        SignTextRenderer.drawLine(DEPOT_FONT, text, pose, buffers, 0, inkHeight / 2,
                height, width, PLATE_LETTERING, light, SignTextRenderer.Alignment.CENTER, true);
        pose.popPose();
    }
}
