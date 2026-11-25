package willow.train.hexie.render.blockentity.signal;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.AdjustableSignalBlockEntityView;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.ThroughSignalBlockEntityView;
import willow.train.hexie.render.init.PartialModelInit;

public class AdjustableSignalRenderer extends ThroughSignalRenderer {
    public AdjustableSignalRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected float getModelAngle(ThroughSignalBlockEntityView signal) {
        if (signal instanceof AdjustableSignalBlockEntityView adjustable && adjustable.orientation() != null)
            return adjustable.orientation().getModelAngle();
        return super.getModelAngle(signal);
    }

    @Override
    protected void renderHousing(ThroughSignalBlockEntityView signal, PoseStack pose, MultiBufferSource buffer,
                                 int light, int overlay) {
        CachedBuffers.partial(PartialModelInit.HX_SIGNAL_HOUSING, signal.asBlockEntity().getBlockState())
                .light(light)
                .renderInto(pose, buffer.getBuffer(RenderType.cutout()));
    }
}
