package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import willow.train.hexie.blocks.semaphore.through.AdjustableSignalBlockEntity;
import willow.train.hexie.init.client.PartialModelInit;

public class AdjustableSignalRenderer extends ThroughSignalRenderer<AdjustableSignalBlockEntity> {
    public AdjustableSignalRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected float getModelAngle(AdjustableSignalBlockEntity signal) {
        return signal.orientation.getModelAngle();
    }

    @Override
    protected void renderHousing(AdjustableSignalBlockEntity signal, PoseStack pose, MultiBufferSource buffer,
                                 int light, int overlay) {
        CachedBuffers.partial(PartialModelInit.HX_SIGNAL_HOUSING, signal.getBlockState())
                .light(light)
                .renderInto(pose, buffer.getBuffer(RenderType.cutout()));
    }
}
