package willow.train.hexie.render.ss7d;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.BiConsumer;
import org.joml.Quaternionf;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.SS7DHeadEntityView;
import willow.train.hexie.render.init.PartialModelInit;

import static willow.train.hexie.render.ss7d.SS7DHeadRenderer.draw;
import static willow.train.hexie.render.ss7d.SS7DHeadRenderer.orient;

// split from willow/train/hexie/client/render/ss7d/SS7DHeadRenderer.java, lines 31, 68-86
final class SS7DWiperParts {
    private static final PartialModel[] BLADES = {PartialModelInit.SS7D_HEAD_WIPER_LEFT, PartialModelInit.SS7D_HEAD_WIPER_RIGHT};

    /** Draws the wipers with the block's local facing and pivot transforms. */
    public static void drawWipers(SS7DHeadEntityView head, float partialTick, PoseStack pose,
                                 BiConsumer<PartialModel, PoseStack> draw) {
        var state = head.asBlockEntity().getBlockState();
        pose.pushPose();
        orient(state, pose);
        for (int side = 0; side < BLADES.length; side++) {
            var wiper = HexieBridges.carriageBlock().ss7DHeadGeometry().wipers().get(side);
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
}
