package willow.train.hexie.client.events;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.vehicle.control.SimpleCabControls;

/** Shares Create's approach prompts across cab modes, with its instruments only in shunting mode. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class SimpleCabTrainHud {
    private static final ResourceLocation TRAIN_HUD = Create.asResource("train_hud");
    private static final LerpedFloat speed = LerpedFloat.linear();
    private static final LerpedFloat throttle = LerpedFloat.linear();
    private static final LerpedFloat promptSize = LerpedFloat.linear();
    private static DrivingConsoleContext active;

    private SimpleCabTrainHud() {}

    static void tick(DrivingConsoleContext cab) {
        if (cab == null) {
            active = null;
            promptSize.startWithValue(0);
            return;
        }
        float speedTarget = 0, throttleTarget = 1;
        if (cab.seatAccess().vehicle() instanceof CarriageContraptionEntity car && car.getCarriage() != null) {
            var train = car.getCarriage().train;
            double maxSpeed = train.maxSpeed() * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
            // TrainHUD uses 18 segments and measures speed relative to the manual speed limit.
            if (maxSpeed > 0)
                speedTarget = (int) (Mth.clamp(Math.abs(train.speed) / maxSpeed + .05f, 0, 1) * 18) / 18f;
            throttleTarget = (float) train.throttle;
        }
        if (!cab.equals(active)) {
            speed.startWithValue(speedTarget);
            throttle.startWithValue(throttleTarget);
            active = cab;
        }
        speed.chase(speedTarget, .5f, LerpedFloat.Chaser.EXP);
        throttle.chase(throttleTarget, .75f, LerpedFloat.Chaser.EXP);
        speed.tickChaser();
        throttle.tickChaser();
        // Create still owns the packet state and expiry when its native controls are not active.
        promptSize.chase(TrainHUD.currentPrompt == null ? 0
                : Minecraft.getInstance().font.width(TrainHUD.currentPrompt) + 17, .5f, LerpedFloat.Chaser.EXP);
        promptSize.tickChaser();
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!TRAIN_HUD.equals(event.getName()) || active == null) return;
        var mc = Minecraft.getInstance();
        var cab = DrivingConsoleClient.occupiedCab();
        if (!active.equals(cab) || mc.options.hideGui
                || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                || mc.getCameraEntity() == null) return;
        // If the native controls already drew this layer, leave their instruments in charge.
        if (ControlsHandler.getContraption() instanceof CarriageContraptionEntity nativeCar
                && nativeCar.getCarriage() != null && ControlsHandler.getControlsPos() != null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var graphics = event.getGuiGraphics();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(graphics.guiWidth() / 2 - 91, graphics.guiHeight() - 29, 0);
        renderPrompt(graphics, partial);
        if (cab.simple()) renderInstruments(graphics, cab, partial);
        pose.popPose();
    }

    private static void renderPrompt(GuiGraphics graphics, float partial) {
        int width = (int) promptSize.getValue(partial);
        if (width <= 1) return;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / -2f + 91, -27, 0);
        AllGuiTextures.TRAIN_PROMPT_L.render(graphics, -3, 0);
        AllGuiTextures.TRAIN_PROMPT_R.render(graphics, width, 0);
        var texture = AllGuiTextures.TRAIN_PROMPT;
        graphics.blit(texture.location, 0, 0, 0, texture.getStartX() + (128 - width / 2f),
                texture.getStartY(), width, texture.getHeight(), 256, 256);
        pose.popPose();

        var prompt = TrainHUD.currentPrompt;
        var font = Minecraft.getInstance().font;
        if (prompt == null || font.width(prompt) >= width - 10) return;
        pose.pushPose();
        pose.translate(font.width(prompt) / -2f + 82, -27, 100);
        graphics.drawString(font, prompt, 9, 4, 0x544D45, TrainHUD.currentPromptShadow);
        pose.popPose();
    }

    private static void renderInstruments(GuiGraphics graphics, DrivingConsoleContext cab, float partial) {
        var pose = graphics.pose();
        pose.pushPose();
        AllGuiTextures.TRAIN_HUD_FRAME.render(graphics, -2, 1);
        AllGuiTextures.TRAIN_HUD_SPEED_BG.render(graphics, 0, 0);
        var bar = AllGuiTextures.TRAIN_HUD_SPEED;
        drawStrip(graphics, bar, 0, (int) (bar.getWidth() * speed.getValue(partial)));

        var limit = AllGuiTextures.TRAIN_HUD_THROTTLE;
        int remaining = (int) (limit.getWidth() * (1 - throttle.getValue(partial)));
        int pointer = limit.getWidth() - remaining;
        drawStrip(graphics, limit, pointer, remaining);
        AllGuiTextures.TRAIN_HUD_THROTTLE_POINTER.render(graphics, Math.max(1, pointer) - 3, -2);
        AllGuiTextures.TRAIN_HUD_DIRECTION.render(graphics, 77, -20);
        pose.translate(91, -9, 0);
        pose.scale(.925f, .925f, 1);
        PlacementClient.textured(pose, 0, 0, 1, directionAngle(cab, partial,
                Minecraft.getInstance().getCameraEntity().getYRot(), DrivingConsoleClient.cabKeyboardMask()));
        pose.popPose();
    }

    private static void drawStrip(GuiGraphics graphics, AllGuiTextures texture, int x, int width) {
        graphics.blit(texture.location, x, 0, 0, texture.getStartX() + x, texture.getStartY(),
                width, texture.getHeight(), 256, 256);
    }

    private static float directionAngle(DrivingConsoleContext cab, float partial, float cameraYaw, int keys) {
        Vec3 forward = Vec3.atLowerCornerOf(cab.console().subtract(cab.seat()));
        if (cab.seatAccess().vehicle() instanceof AbstractContraptionEntity car)
            forward = car.applyRotation(forward, partial);
        float heading = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        boolean reverse = (keys & SimpleCabControls.BACKWARD) != 0;
        float angle = Mth.wrapDegrees(heading + (reverse ? 180 : 0) - cameraYaw);
        if (Math.abs(angle) < 60) angle = 0;
        angle += SimpleCabControls.steering(keys) * (reverse ? -45 : 45);
        return 22.5f * Math.round(angle / 22.5f);
    }
}
