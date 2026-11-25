package willow.train.hexie.render.ss7d;

// split from willow/train/hexie/client/render/ss7d/SS7DHeadRenderer.java

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.BiConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.SS7DHeadEntityView;
import willow.train.hexie.render.carriage.CarriageBlockVisual;
import willow.train.hexie.render.init.PartialModelInit;

import static willow.train.hexie.render.ss7d.SS7DHeadCoupler.hook;
import static willow.train.hexie.render.ss7d.SS7DPlateLettering.drawDepot;
import static willow.train.hexie.render.ss7d.SS7DPlateLettering.drawEndLabel;
import static willow.train.hexie.render.ss7d.SS7DPlateLettering.drawPlate;
import static willow.train.hexie.render.ss7d.SS7DWiperParts.drawWipers;

public final class SS7DHeadRenderer implements BlockEntityRenderer<BlockEntity> {

    public SS7DHeadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(BlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        if (!(blockEntity instanceof SS7DHeadEntityView head)) return;
        var state = head.asBlockEntity().getBlockState();
        boolean sidePlates = head.hasSidePlates();
        pose.pushPose();
        if (!CarriageBlockVisual.ownsParts(head.asBlockEntity())) {
            drawWipers(head, partialTick, pose,
                    (model, transform) -> draw(model, state, transform, buffers, light, overlay));
            if (sidePlates)
                drawSidePlates(head, pose,
                        (model, transform) -> draw(model, state, transform, buffers, light, overlay));
        }
        orient(state, pose);
        if (!head.isCarriageView()) hook(state, pose, buffers, light, overlay);
        for (var plate : HexieBridges.carriageBlock().ss7DHeadGeometry().plates()) {
            drawPlate(plate, head.digits(), sidePlates, pose, buffers, light);
            if (plate.isSide()) {
                if (head.depotVisible()) drawDepot(plate, head.depotLabel(), pose, buffers, light);
                drawEndLabel(plate, head.endLabel(), pose, buffers, light);
            }
        }
        pose.popPose();
    }

    /** Both side plates share one model, in the same local coordinates as the cab. */
    public static void drawSidePlates(SS7DHeadEntityView head, PoseStack pose,
                                      BiConsumer<PartialModel, PoseStack> draw) {
        pose.pushPose();
        orient(head.asBlockEntity().getBlockState(), pose);
        draw.accept(PartialModelInit.SS7D_HEAD_SIDE_PLATES, pose);
        pose.popPose();
    }

    public static void orient(BlockState state, PoseStack pose) {
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (state.getValue(HexieBridges.carriageBlock().ss7DHeadBlock().facing())) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        }));
        pose.translate(-.5, 0, -.5);
    }

    public static void draw(PartialModel model, BlockState state, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        CachedBuffers.partial(model, state).light(light).overlay(overlay)
                .renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    @Override public AABB getRenderBoundingBox(BlockEntity head) {
        return HexieBridges.carriageBlock().ss7DHeadGeometry().bounds(head.getBlockState().getValue(HexieBridges.carriageBlock().ss7DHeadBlock().facing())).move(head.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(BlockEntity head) { return true; }
}
