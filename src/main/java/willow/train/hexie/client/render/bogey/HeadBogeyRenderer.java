package willow.train.hexie.client.render.bogey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;

/** The fixed bolster follows the head; assembled wheel frames are rendered by the support animation. */
public final class HeadBogeyRenderer {
    private HeadBogeyRenderer() {}

    public static void render(CRH2HeadEntity head, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        var bogey = BogeyRenderData.head(head);
        if (bogey == null) return;
        var offset = bogey.position().subtract(head.getBlockPos());
        boolean moving = head.isCarriageView();
        boolean physical = ((CRH2HeadBlock) head.getBlockState().getBlock()).hasPhysicalBogey();
        if (!moving && head.getLevel() != null)
            light = LevelRenderer.getLightColor(head.getLevel(), bogey.position());
        pose.pushPose();
        try {
            pose.translate(offset.getX(), offset.getY(), offset.getZ());
            var modelData = BogeyTopModel.withBogeyData(ModelData.EMPTY, bogey.state(), bogey.data(), moving);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    bogey.state(), pose, buffers, light, overlay, modelData, null);
            // Physical wheels belong to the rear control's independently moving Simurail pivot.
            // A Create Snapshot instead uses its own support animation for either head variant.
            if (!moving && !physical) {
                pose.translate(.5, .5, .5);
                if (bogey.state().getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X)
                    pose.mulPose(Axis.YP.rotationDegrees(90));
                bogey.style().render(bogey.block().getSize(), partialTick, pose, buffers,
                        light, overlay, 0, bogey.data(), false);
            }
        } finally {
            pose.popPose();
        }
    }
}
