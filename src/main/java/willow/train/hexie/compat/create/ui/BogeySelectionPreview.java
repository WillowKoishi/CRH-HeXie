package willow.train.hexie.compat.create.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL11;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeySelectionCatalogEntryView;

// split from willow/train/hexie/client/screens/BogeySelectionScreen.java
final class BogeySelectionPreview {
    /** The original preview camera, clipping, rail motion and wheel speed. */
    static void renderPreview(BogeySelectionCatalogEntryView entry, GuiGraphics graphics, float partialTick,
                              int guiLeft, int guiTop, Minecraft minecraft) {
        var block = entry.block();
        var state = block.asBlock().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z);
        var data = entry.style().defaultData.copy();
        var model = HexieBridges.bogeyRegistry().builtinBogeyModel().find(entry.style().id, entry.size().id());
        double radius = model == null ? block.getWheelRadius() : HexieBridges.bogeyRegistry().builtinBogeyProfile().geometry(HexieBridges.bogeyRegistry().builtinBogeyModel().profile(model)).wheelRadius();
        float distance = (Util.getMillis() % 3600000) * .002f;
        float wheelAngle = (float) Math.toDegrees(distance / radius) % 360;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(guiLeft + 213, guiTop + 103, 1500);
            pose.scale(1, 1, -1);
            pose.translate(0, 0, 1000);
            pose.scale(20, 20, 20);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.mulPose(Axis.XP.rotationDegrees(-20));
            pose.mulPose(Axis.YP.rotationDegrees(-135));
            pose.translate(0, .5, 0);
            Lighting.setupForEntityInInventory(Axis.XP.rotationDegrees(-30));
            pose.pushPose();
            try {
                pose.translate(-.5, -.5, -.5);
                minecraft.getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(),
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        HexieBridges.Client.bogeyRender().bogeyTopModel().withBogeyData(ModelData.EMPTY, state, data, false), null);
            } finally { pose.popPose(); }
            var track = HexieBridges.createTrack().standardTrack().defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.ZO);
            for (int i = -8; i < 8; i++) {
                CachedBuffers.block(track).translate(-.5, -1.5, i - .5 - distance % 1)
                        .light(LightTexture.FULL_BRIGHT).overlay(OverlayTexture.NO_OVERLAY)
                        .renderInto(pose, graphics.bufferSource().getBuffer(RenderType.cutoutMipped()));
            }
            entry.style().render(entry.size(), partialTick, pose, graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, wheelAngle, data, false);
            graphics.flush();
        } finally {
            pose.popPose();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
            Lighting.setupFor3DItems();
        }
    }
}
