package willow.train.hexie.compat.create.ui;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.vehicle.SimpleCabControlsView;
import willow.train.hexie.bridge.ui.client.ClientCarriageUiBridge.CabHud;
import willow.train.hexie.core.api.cab.CabDisplaySource;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

/** Shares Create's approach prompts across cab modes, with its instruments only in shunting mode. */
public final class SimpleCabTrainHud implements CabHud {
    private static final ResourceLocation TRAIN_HUD = ResourceLocation.fromNamespaceAndPath("create", "train_hud");
    private static final Gauge speed = new Gauge();
    private static final Gauge throttle = new Gauge();
    private static final Gauge promptSize = new Gauge();
    private static DrivingConsoleContext active;
    private static HudDirection hudDirection;

    private record HudDirection(int carriageId, float yaw) {}

    @Override public CabDisplayState displayState(DrivingConsoleContext cab) {
        Entity vehicle = cab.seatAccess().vehicle();
        if (vehicle instanceof SnapshotCarriageView snapshot) return snapshot.cabDisplayState(cab.console());
        if (vehicle instanceof AbstractContraptionEntity carrier && carrier.getContraption() != null) {
            var entity = carrier.getContraption().getBlockEntityClientSide(cab.console());
            return entity instanceof CabDisplaySource source ? source.displayState() : null;
        }
        return CabHud.super.displayState(cab);
    }

    @Override public CreateCabCarriage train(Entity vehicle) { return CreateCabCarriage.of(vehicle); }

    private static final class Gauge {
        private float previous;
        private float value;

        void reset(float target) {
            previous = value = target;
        }

        void chase(float target, double rate) {
            previous = value;
            value = Mth.equal((double) value, target) ? target : (float) Mth.lerp(rate, value, target);
        }

        float getValue(float partial) {
            return (float) Mth.lerp((double) partial, previous, value);
        }
    }

    @Override public void tick(DrivingConsoleContext cab) {
        if (cab == null) {
            active = null;
            promptSize.reset(0);
            return;
        }
        float speedTarget = 0, throttleTarget = 1;
        var carriage = CreateCabCarriage.of(cab.seatAccess().vehicle());
        if (carriage != null) {
            double maxSpeed = carriage.manualLimit();
            // TrainHUD uses 18 segments and measures speed relative to the manual speed limit.
            if (maxSpeed > 0)
                speedTarget = (int) (Mth.clamp(Math.abs(carriage.speed()) / maxSpeed + .05f, 0, 1) * 18) / 18f;
            throttleTarget = (float) carriage.throttle();
        }
        if (!cab.equals(active)) {
            speed.reset(speedTarget);
            throttle.reset(throttleTarget);
            active = cab;
        }
        speed.chase(speedTarget, .5);
        throttle.chase(throttleTarget, .75);
        // Create still owns the packet state and expiry when its native controls are not active.
        promptSize.chase(TrainHUD.currentPrompt == null ? 0
                : Minecraft.getInstance().font.width(TrainHUD.currentPrompt) + 17, .5);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeLayer(RenderGuiLayerEvent.Pre event) {
        if (!TRAIN_HUD.equals(event.getName())) return;
        hudDirection = null;
        if (!(ControlsHandler.getContraption() instanceof SnapshotCarriageView snapshot)) return;
        var carriage = snapshot.asEntity();
        var controlsFacing = carriage.getInitialOrientation().getCounterClockWise();
        Vec3 forward = carriage.applyRotation(Vec3.atLowerCornerOf(controlsFacing.getOpposite().getNormal()),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        hudDirection = new HudDirection(carriage.getId(), (float) Math.toDegrees(Math.atan2(-forward.x, forward.z)) - 90f);
    }

    public static void wrapTrainLayer(RegisterGuiLayersEvent event) {
        // A canceled Pre suppresses Post entirely. Limit the actual yaw change to the native draw.
        event.wrapLayer(TRAIN_HUD, original -> (graphics, ticks) -> {
            var direction = hudDirection;
            hudDirection = null;
            if (direction == null || !(ControlsHandler.getContraption() instanceof CarriageContraptionEntity carriage)
                    || direction.carriageId() != carriage.getId()) {
                original.render(graphics, ticks);
                return;
            }
            float savedYaw = carriage.yaw;
            try {
                carriage.yaw = direction.yaw();
                original.render(graphics, ticks);
            } finally {
                carriage.yaw = savedYaw;
            }
        });
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!TRAIN_HUD.equals(event.getName()) || active == null) return;
        var mc = Minecraft.getInstance();
        var cab = HexieBridges.Client.surfaceInputClient().surfaceInputClient().cab();
        if (!active.equals(cab) || mc.options.hideGui
                || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                || mc.getCameraEntity() == null) return;
        // If the native controls already drew this layer, leave their instruments in charge.
        if (CreateCabCarriage.nativeControlsActive()) return;

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
                Minecraft.getInstance().getCameraEntity().getYRot(), HexieBridges.Client.clientCarriageUi().cabKeyboardMask()));
        pose.popPose();
    }

    private static void drawStrip(GuiGraphics graphics, AllGuiTextures texture, int x, int width) {
        graphics.blit(texture.location, x, 0, 0, texture.getStartX() + x, texture.getStartY(),
                width, texture.getHeight(), 256, 256);
    }

    private static float directionAngle(DrivingConsoleContext cab, float partial, float cameraYaw, int keys) {
        Vec3 forward = Vec3.atLowerCornerOf(cab.console().subtract(cab.seat()));
        forward = CreateCabCarriage.rotate(cab.seatAccess().vehicle(), forward, partial);
        float heading = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        boolean reverse = (keys & SimpleCabControlsView.BACKWARD) != 0;
        float angle = Mth.wrapDegrees(heading + (reverse ? 180 : 0) - cameraYaw);
        if (Math.abs(angle) < 60) angle = 0;
        angle += HexieBridges.cabControl().simpleCabControls().steering(keys) * (reverse ? -45 : 45);
        return 22.5f * Math.round(angle / 22.5f);
    }
}
