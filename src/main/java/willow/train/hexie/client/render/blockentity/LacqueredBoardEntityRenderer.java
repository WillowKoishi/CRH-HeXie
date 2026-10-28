package willow.train.hexie.client.render.blockentity;

import static willow.train.hexie.client.model.LacqueredBoardLogo.LAYER_A;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.blockentity.LacqueredBoardEntity;
import willow.train.hexie.blocks.signs.TrainNoBlock;
import willow.train.hexie.client.model.LacqueredBoardLogo;
import willow.train.hexie.client.sign.SignLettering;
import willow.train.hexie.client.sign.SignTextRenderer;

public class LacqueredBoardEntityRenderer implements BlockEntityRenderer<LacqueredBoardEntity> {
    public static final ResourceLocation STICKER = ResourceLocation.fromNamespaceAndPath("hexie", "textures/laquered_board/stick.png");
    private static final float HEIGHT = .17955f;
    private static final float[] COLUMN_CENTERS = {.2075f, .7925f};
    private final LacqueredBoardLogo<?> logoModel;

    public LacqueredBoardEntityRenderer(BlockEntityRendererProvider.Context context) {
        logoModel = new LacqueredBoardLogo<>(context.bakeLayer(LAYER_A));
    }

    @Override public void render(LacqueredBoardEntity sign, float tick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        var messages = sign.getRenderMessages(net.minecraft.network.chat.Component::getVisualOrderText, 6);
        pose.pushPose();
        pose.translate(.5, -.5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-sign.getBlockState().getValue(TrainNoBlock.FACING).getOpposite().toYRot()));
        pose.translate(-.5 + sign.getXOffset(), .5, -.43);
        pose.scale(1, -1, 1);
        // Preserve the original board bounds. All placements below are independent of text length.
        pose.translate(0, -.91 + HEIGHT * .18, 0);
        rectangle(pose, buffers, light, 0, 0, 1, HEIGHT, 0, 0xFFFFFFFF);
        if (sign.getBoardType() == 0 && messages.length >= 7) {
            int[] rgba = sign.getRGBAColor(sign.getBackGroundColor());
            int belt = net.minecraft.util.FastColor.ARGB32.color(rgba[3], rgba[0], rgba[1], rgba[2]);
            int ink = textColor(sign.getForGroundColor());
            int beltInk = textColor(sign.getBeltForGroundColor());
            for (int column = 0; column < 2; column++) {
                float center = COLUMN_CENTERS[column];
                rectangle(pose, buffers, light, center - .1925f, HEIGHT * .66f, .385f, HEIGHT * .24f, .003f, belt);
                station(SignLettering.text(messages[column + 1]), center, ink, pose, buffers, light);
                text(SignLettering.ROUTE_PINYIN, SignLettering.text(messages[column + 3]), center,
                        .705f, .15f, .355f, beltInk, pose, buffers, light);
            }

            // The legacy logo mesh starts at Y=24/16 and measures 2/16 square.
            pose.pushPose();
            pose.translate(.44375f, HEIGHT * .08f - 1.35f, .004f);
            pose.scale(.9f, .9f, 1);
            logoModel.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(LAYER_A.getModel())), light, overlay);
            pose.popPose();

            String service = SignLettering.text(messages[5]).strip();
            int[] letters = service.codePoints().filter(c -> !Character.isWhitespace(c)).toArray();
            // Two-character service names flank the crest; longer labels retain the same two zones.
            int split = (letters.length + 1) / 2;
            if (letters.length == 1) {
                text(SignLettering.ROUTE_SERVICE, service, .415f, .035f, .16f, .055f, ink, pose, buffers, light);
            } else if (letters.length > 1) {
                text(SignLettering.ROUTE_SERVICE, new String(letters, 0, split), .415f,
                        .035f, .16f, .055f, ink, pose, buffers, light);
                text(SignLettering.ROUTE_SERVICE, new String(letters, split, letters.length - split), .585f,
                        .035f, .16f, .055f, ink, pose, buffers, light);
            }
            text(SignLettering.ROUTE_NUMBER, SignLettering.text(messages[6]), .5f,
                    .85f, .10f, .185f, ink, pose, buffers, light);
        }
        pose.popPose();
    }

    private void station(String name, float center, int color, PoseStack pose, MultiBufferSource buffers, int light) {
        int[] letters = name.strip().codePoints().toArray();
        if (letters.length >= 2 && letters.length <= 4
                && java.util.Arrays.stream(letters).allMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
            // Equal character slots keep two-character names spread out like the reference sign.
            float cell = .34f / letters.length;
            for (int i = 0; i < letters.length; i++)
                text(SignLettering.ROUTE_NAME, new String(letters, i, 1), center - .17f + cell * (i + .5f),
                        .13f, .44f, cell * .94f, color, pose, buffers, light);
        } else {
            text(SignLettering.ROUTE_NAME, name, center, .13f, .44f, .355f, color, pose, buffers, light);
        }
    }

    /** x/width are board-width units; y/height are fractions of the board height. */
    private void text(ResourceLocation profile, String value, float x, float y, float height, float width,
                      int color, PoseStack pose, MultiBufferSource buffers, int light) {
        // Center short, width-constrained text vertically inside its assigned row as well.
        float naturalWidth = SignTextRenderer.width(profile, value, height * HEIGHT, Float.MAX_VALUE);
        float fittedHeight = naturalWidth > width ? height * HEIGHT * width / naturalWidth : height * HEIGHT;
        pose.pushPose();
        pose.translate(0, y * HEIGHT + (height * HEIGHT - fittedHeight) / 2, .006f);
        pose.scale(1, -1, 1);
        SignTextRenderer.drawLine(profile, value, pose, buffers, x, 0, fittedHeight, width,
                color, light, SignTextRenderer.Alignment.CENTER);
        pose.popPose();
    }

    private static int textColor(int color) {
        int r = (int) (net.minecraft.util.FastColor.ABGR32.red(color) * .8f);
        int g = (int) (net.minecraft.util.FastColor.ABGR32.green(color) * .8f);
        int b = (int) (net.minecraft.util.FastColor.ABGR32.blue(color) * .8f);
        return net.minecraft.util.FastColor.ARGB32.color(255, r, g, b);
    }

    private void rectangle(PoseStack pose, MultiBufferSource buffers, int light, float x, float y,
                           float width, float height, float z, int color) {
        var matrix = pose.last().pose();
        var vertices = buffers.getBuffer(RenderType.text(STICKER));
        vertices.addVertex(matrix, x, y + height, z).setColor(color).setUv(0, 1).setLight(light);
        vertices.addVertex(matrix, x + width, y + height, z).setColor(color).setUv(1, 1).setLight(light);
        vertices.addVertex(matrix, x + width, y, z).setColor(color).setUv(1, 0).setLight(light);
        vertices.addVertex(matrix, x, y, z).setColor(color).setUv(0, 0).setLight(light);
    }
}
