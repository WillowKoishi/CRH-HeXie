package willow.train.hexie.client.render.blockentity;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.blocks.semaphore.through.ThroughSignalBlockEntity;
import willow.train.hexie.init.client.PartialModelInit;

/*
MIT License

Copyright (c) 2019 simibubi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

public class ThroughSignalRenderer extends SafeBlockEntityRenderer<ThroughSignalBlockEntity> {

    public ThroughSignalRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(ThroughSignalBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState blockState = be.getBlockState();
        SignalBlockEntity.SignalState signalState = be.getState();
        SignalBlockEntity.OverlayState overlayState = be.getOverlay();

        float renderTime = AnimationTickHolder.getRenderTime(be.getLevel());

        ms.pushPose();
        float yRot = AngleHelper.horizontalAngle(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)) + 180;
        var msr = TransformStack.of(ms);
        msr.center().rotateYDegrees(yRot).uncenter();

        int lightMode = be.getLightMode();
        boolean changingToLowerLamp = signalState.isRedLight(renderTime);
        boolean lowerLight = lightMode == 0 ? changingToLowerLamp : !changingToLowerLamp;
        int color = switch (lightMode) {
            case 0 -> lowerLight ? 0x1EAAFF : 0xFFFFFF;
            case 1 -> lowerLight ? 0x28FF45 : 0xFF3030;
            case 2 -> lowerLight ? 0xFFFF28 : 0xFF3030;
            default -> lowerLight ? 0x1EAAFF : 0xFFFFFF;
        };
        float glowScale = 0.6f;
        float glowSideLength = 0.375f * glowScale;
        float glowDepth = 0.375f + 0.15f + glowSideLength * 0.9f;
        // The glow is now inset by 0.9 of its rendered side length after moving forward.
        CachedBuffers.partial(PartialModelInit.HX_SIGNAL_WHITE_GLOW, blockState)
                .translate(1, lowerLight ? 1.46875 : 1.90625, glowDepth)
                .translate(-0.5, -0.5, -0.5)
                .scale(glowScale)
                .translate(0.5, 0.5, 0.5)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
                .light(0xF000F0)
                .disableDiffuse()
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));
        ms.popPose();

        BlockPos pos = be.getBlockPos();
        TrackTargetingBehaviour<SignalBoundary> target = be.edgePoint;
        BlockPos targetPosition = target.getGlobalPosition();
        Level level = be.getLevel();
        BlockState trackState = level.getBlockState(targetPosition);
        Block block = trackState.getBlock();

        if (!(block instanceof ITrackBlock))
            return;
        if (overlayState == SignalBlockEntity.OverlayState.SKIP)
            return;

        ms.pushPose();
        TransformStack.of(ms)
                .translate(targetPosition.subtract(pos));
        TrackTargetingBehaviour.RenderedTrackOverlayType type =
                overlayState == SignalBlockEntity.OverlayState.DUAL ? TrackTargetingBehaviour.RenderedTrackOverlayType.DUAL_SIGNAL : TrackTargetingBehaviour.RenderedTrackOverlayType.SIGNAL;
        TrackTargetingBehaviour.render(level, targetPosition, target.getTargetDirection(), target.getTargetBezier(), ms,
                buffer, light, overlay, type, 1);
        ms.popPose();

    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen(ThroughSignalBlockEntity pBlockEntity) {
        return true;
    }
}
