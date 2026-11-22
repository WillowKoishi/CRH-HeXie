package willow.train.hexie.client.render.ss7d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import java.util.function.BiConsumer;
import willow.train.hexie.blocks.locos.ss7d.SS7DHeadBlock;
import willow.train.hexie.blocks.locos.ss7d.SS7DHeadEntity;
import willow.train.hexie.blocks.locos.ss7d.SS7DHeadGeometry;
import willow.train.hexie.client.render.carriage.CarriageBlockVisual;
import willow.train.hexie.client.render.SnapshotCouplerRenderer;
import willow.train.hexie.client.sign.SignTextRenderer;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;
import willow.train.hexie.init.client.PartialModelInit;

public final class SS7DHeadRenderer implements BlockEntityRenderer<SS7DHeadEntity> {
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("hexie", "ss7d_number");
    private static final ResourceLocation DEPOT_FONT = ResourceLocation.fromNamespaceAndPath("hexie", "ss7d_depot");
    private static final int PLATE_LETTERING = 0xFFF8F7F3;
    private static final int BODY_LETTERING = 0xFF9E1818;
    private static final Coupler COUPLER = new Coupler();
    private static final PartialModel[] BLADES = {PartialModelInit.SS7D_HEAD_WIPER_LEFT, PartialModelInit.SS7D_HEAD_WIPER_RIGHT};

    public SS7DHeadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(SS7DHeadEntity head, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        var state = head.getBlockState();
        boolean sidePlates = head.hasSidePlates();
        pose.pushPose();
        if (!CarriageBlockVisual.ownsParts(head)) {
            drawWipers(head, partialTick, pose,
                    (model, transform) -> draw(model, state, transform, buffers, light, overlay));
            if (sidePlates)
                drawSidePlates(head, pose,
                        (model, transform) -> draw(model, state, transform, buffers, light, overlay));
        }
        orient(state, pose);
        if (!head.isCarriageView()) hook(state, pose, buffers, light, overlay);
        for (var plate : SS7DHeadGeometry.PLATES) {
            drawPlate(plate, head.digits(), sidePlates, pose, buffers, light);
            if (plate.isSide()) {
                if (head.depotVisible()) drawDepot(plate, head.depotLabel(), pose, buffers, light);
                drawEndLabel(plate, head.endLabel(), pose, buffers, light);
            }
        }
        pose.popPose();
    }

    /** Both side plates share one model, in the same local coordinates as the cab. */
    public static void drawSidePlates(SS7DHeadEntity head, PoseStack pose,
                                      BiConsumer<PartialModel, PoseStack> draw) {
        pose.pushPose();
        orient(head.getBlockState(), pose);
        draw.accept(PartialModelInit.SS7D_HEAD_SIDE_PLATES, pose);
        pose.popPose();
    }

    /** Draws the wipers with the block's local facing and pivot transforms. */
    public static void drawWipers(SS7DHeadEntity head, float partialTick, PoseStack pose,
                                 BiConsumer<PartialModel, PoseStack> draw) {
        var state = head.getBlockState();
        pose.pushPose();
        orient(state, pose);
        for (int side = 0; side < BLADES.length; side++) {
            var wiper = SS7DHeadGeometry.WIPERS.get(side);
            var pivot = wiper.pivot();
            pose.pushPose();
            pose.translate(pivot.x, pivot.y, pivot.z);
            pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(head.wiperAngle(side, partialTick)),
                    wiper.axis().toVector3f()));
            pose.translate(-pivot.x, -pivot.y, -pivot.z);
            draw.accept(BLADES[side], pose);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void orient(BlockState state, PoseStack pose) {
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (state.getValue(SS7DHeadBlock.FACING)) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        }));
        pose.translate(-.5, 0, -.5);
    }

    private static void drawPlate(SS7DHeadGeometry.Plate plate, String digits, boolean sidePlates, PoseStack pose,
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
        var inkHeight = SignTextRenderer.height(FONT, text, height, width);
        SignTextRenderer.drawLine(FONT, text, pose, buffers, left + width / 2, bottom + inkHeight, height, width,
                color, light, SignTextRenderer.Alignment.CENTER, true);
    }

    private static void drawDepot(SS7DHeadGeometry.Plate plate, String text, PoseStack pose,
                                  MultiBufferSource buffers, int light) {
        pose.pushPose();
        var side = plate.printedCenter();
        pose.translate(side.x, 1.4, side.z);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(plate.normal().x, plate.normal().z)));
        float height = .15f;
        float width = .64f;
        float inkHeight = SignTextRenderer.height(DEPOT_FONT, text, height, width);
        SignTextRenderer.drawLine(DEPOT_FONT, text, pose, buffers, 0, inkHeight / 2,
                height, width, PLATE_LETTERING, light, SignTextRenderer.Alignment.CENTER, true);
        pose.popPose();
    }

    /** The cab-end mark is below the depot inscription and toward the locomotive front. */
    private static void drawEndLabel(SS7DHeadGeometry.Plate plate, String text, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        pose.pushPose();
        var side = plate.printedCenter();
        pose.translate(side.x, 1.1125, side.z - .203125);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(plate.normal().x, plate.normal().z)));
        float height = .11f;
        float width = .14f;
        float inkHeight = SignTextRenderer.height(DEPOT_FONT, text, height, width);
        SignTextRenderer.drawLine(DEPOT_FONT, text, pose, buffers, 0, inkHeight / 2,
                height, width, PLATE_LETTERING, light, SignTextRenderer.Alignment.CENTER, true);
        pose.popPose();
    }

    private static void draw(PartialModel model, BlockState state, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        CachedBuffers.partial(model, state).light(light).overlay(overlay)
                .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    private static void hook(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var geometry = SS7DHeadGeometry.COUPLER;
        draw(PartialModelInit.SS7D_HEAD_HOOK, state, pose, buffers, light, overlay);
        var axis = geometry.knuckleAxis();
        pose.pushPose();
        pose.translate(axis.x(), 0, axis.z());
        if (state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN))
            pose.mulPose(Axis.YP.rotationDegrees(geometry.knuckleOpeningAngle()));
        pose.translate(-axis.x(), 0, -axis.z());
        draw(PartialModelInit.SS7D_HEAD_KNUCKLE, state, pose, buffers, light, overlay);
        pose.popPose();
    }

    public static SnapshotCouplerRenderer.Renderer coupler() { return COUPLER; }
    private static final class Coupler implements SnapshotCouplerRenderer.Renderer {
        @Override public void renderArm(BlockState state, SnapshotCouplerAnimation.Parameters parameters,
                float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var origin = SS7DHeadGeometry.COUPLER.armOrigin();
            pose.pushPose();
            pose.translate(-origin.x, -origin.y, -origin.z);
            hook(state, pose, buffers, light, overlay);
            pose.popPose();
        }
    }

    @Override public AABB getRenderBoundingBox(SS7DHeadEntity head) {
        return SS7DHeadGeometry.bounds(head.getBlockState().getValue(SS7DHeadBlock.FACING)).move(head.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(SS7DHeadEntity head) { return true; }
}
