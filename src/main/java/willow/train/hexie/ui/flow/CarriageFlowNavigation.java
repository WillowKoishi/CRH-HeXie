package willow.train.hexie.ui.flow;

import java.util.*;
import org.joml.Matrix4f;

// split from willow/train/hexie/client/carriage/flow/CarriageFlowView.java
final class CarriageFlowNavigation {
    private final CarriageFlowView view;

    CarriageFlowNavigation(CarriageFlowView view) { this.view = view; }

    static final int PAN_DRAG_SLOP = 3;

    static final float FRONT_YAW = 142;
    private static final float CAMERA_PITCH = 44;

    private static final double PAN_INERTIA_DAMPING = 6.5;
    private static final double PAN_MAX_SPEED = 18;
    private static final double PAN_STOP_SPEED = .08;
    private static final double PAN_RESTRICTED_SNAP_DISTANCE = 9;
    static final double FOCUS_FADE_SECONDS = .35;

    long selected;
    double position, targetPosition, lastFrame = now(), lastInput = -100;
    final CarriageFlowMotion motion = new CarriageFlowMotion();
    float neighborVisibility = .25f;

    long phase;
    // Camera-only track offset; never applied to the train motion or the upper strip.
    double panZ, panTarget, panVelocity, lastPanInputTime;
    private double panDragStartX, panDragStartY, panDragStartPosition, panDirectionY;
    private int panScreenDirection;
    boolean panDraggedRight, manualPan;
    CarriageFlowMotion settlingPan;
    long previousFocus;
    double focusStarted = Double.NEGATIVE_INFINITY;
    boolean draggingPan, panSnapPending, panSnapCaptured;

    double yaw = FRONT_YAW, fromYaw = FRONT_YAW, targetYaw = FRONT_YAW, viewStarted;
    float shell = 1, fromShell = 1, targetShell = 1;

    static double now() { return System.nanoTime() / 1_000_000_000.0; }

    void animate() {
        double time = now();
        double elapsed = Math.min(.1, Math.max(0, time - lastFrame));
        lastFrame = time;
        if (view.gestures.stripPress == null) {
            double previousPosition = position;
            motion.advance(elapsed);
            position = motion.position();
            if (manualPan) {
                // Keep a manually positioned preview steady while the strip finishes moving.
                double shift = previousPosition - position;
                panZ += shift;
                panTarget += shift;
            }
        }
        double t = CarriageFlowLayout.ease((time - viewStarted) / .8);
        yaw = fromYaw + (targetYaw - fromYaw) * t;
        shell = (float) (fromShell + (targetShell - fromShell) * t);
        if (settlingPan != null) {
            settlingPan.target(panTarget);
            settlingPan.advance(elapsed);
            panZ = settlingPan.position();
            if (panZ == panTarget && settlingPan.velocity() == 0) settlingPan = null;
        } else if (panSnapPending && Math.abs(panVelocity) >= PAN_STOP_SPEED) {
            double decay = Math.exp(-PAN_INERTIA_DAMPING * elapsed);
            double requested = panZ + panVelocity * (1 - decay) / PAN_INERTIA_DAMPING;
            panVelocity *= decay;
            if (Math.abs(panVelocity) < PAN_STOP_SPEED) panVelocity = 0;
            movePan(requested);
        } else if (!draggingPan) {
            panVelocity = 0;
            panZ += (panTarget - panZ) * (1 - Math.exp(-elapsed * 12));
            if (Math.abs(panTarget - panZ) < .0001) panZ = panTarget;
        }
        if (panSnapPending && !draggingPan && panVelocity == 0) panSnapPending = false;
        boolean scrolling = view.gestures.draggingStrip || time - lastInput < .16;
        float opacity = scrolling ? 1 : .25f + .75f * (float) Math.min(1, Math.abs(motion.velocity()) / 12);
        neighborVisibility += (opacity - neighborVisibility) * (float) (1 - Math.exp(-elapsed * 12));
    }

    void select(long index) {
        if (view.layout == null) return;
        panSnapPending = false;
        view.gestures.carriagePress = null;
        animate();
        draggingPan = false;
        focusCarriage(index);
    }

    private void focusCarriage(long index) {
        if (index != selected) {
            manualPan = false;
            previousFocus = selected;
            focusStarted = view.gestures.draggingStrip ? Double.NEGATIVE_INFINITY : lastFrame;
            // Both the strip and the camera offset start their approach at the displayed pose and velocity.
            if (settlingPan == null && (panZ != 0 || panVelocity != 0)) {
                settlingPan = new CarriageFlowMotion();
                settlingPan.restore(panZ, panVelocity, 0);
            }
            panTarget = 0;
            selected = index;
        }
        panVelocity = 0;
        targetPosition = view.layout.center(index);
        motion.target(targetPosition);
        lastInput = now();
        view.scene.selectedTransform = null;
        view.scene.hovered = null;
        view.schematic.syncSchematicSlot();
    }

    void cycle(int delta) {
        panSnapPending = false;
        animate();
        fromYaw = yaw;
        fromShell = shell;
        panVelocity = 0;
        if (delta < 0) {
            if (targetShell > .5f) targetShell = 0;
            else phase--;
        } else {
            if (targetShell < .5f) targetShell = 1;
            else phase++;
        }
        targetYaw = FRONT_YAW + phase * 90;
        viewStarted = now();
    }

    double focusZ() {
        // Orbit the current observation point instead of sliding the pivot with yaw.
        return -view.focusDistance;
    }

    Matrix4f localCamera(double pan) {
        return new Matrix4f().translate(CarriageFlowView.width / 2f, view.scene.trackOriginY, 200)
                .scale(view.scene.sceneScale, -view.scene.sceneScale, view.scene.sceneScale)
                .rotateX((float) Math.toRadians(CAMERA_PITCH)).rotateY((float) Math.toRadians(yaw))
                .translate(0, 0, (float) -(focusZ() + pan));
    }

    double clampPan(double requested) {
        if (view.layout == null) return 0;
        var model = view.catalog.models().get(view.layout.model(selected));
        Matrix4f camera = localCamera(0);
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            var corner = camera.transformPosition(new org.joml.Vector3f(
                    (x - .5f) * model.width(), y * model.height(), (z - .5f) * model.length()));
            minX = Math.min(minX, corner.x); maxX = Math.max(maxX, corner.x);
            minY = Math.min(minY, corner.y); maxY = Math.max(maxY, corner.y);
        }
        var direction = camera.transformDirection(new org.joml.Vector3f(0, 0, -1));
        return CarriageFlowPanBounds.clamp(requested, minX, maxX, minY, maxY, direction.x, direction.y);
    }

    private double panSnapPosition(int direction) {
        if (panScreenDirection < 0) {
            if (direction < 0 && panDraggedRight && Math.abs(Math.IEEEremainder(yaw - FRONT_YAW, 360)) < 1e-4) {
                // Pull the carriage at the lower left into the front view.
                return halfVisiblePanPosition(direction);
            }
            double length = view.catalog.models().get(view.layout.model(selected)).length();
            return panDragStartPosition + direction * length;
        }

        if (panScreenDirection == 0) {
            double center = view.layout.center(selected);
            double midpoint = center + (view.layout.center(selected + direction) - center) / 2;
            double bound = position + clampPan(direction < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            if (direction * (bound - midpoint) >= 0) return midpoint;
        }
        return halfVisiblePanPosition(direction);
    }

    private double halfVisiblePanPosition(int direction) {
        double center = view.layout.center(selected);
        double neighborCenter = view.layout.center(selected + direction);
        var model = view.catalog.models().get(view.layout.model(selected + direction));
        double pan = CarriageFlowPanBounds.panForHalfVisible(localCamera(0),
                neighborCenter - position, model.width(), model.height(), model.length(), direction);
        if (Double.isFinite(pan)) {
            double visiblePosition = position + pan;
            return direction < 0 ? Math.min(center, visiblePosition) : Math.max(center, visiblePosition);
        }
        return center + direction * Math.min(PAN_RESTRICTED_SNAP_DISTANCE, Math.abs(neighborCenter - center) / 2);
    }

    private void movePan(double requested) {
        if (view.layout == null || requested == panZ) return;
        int direction = requested < panZ ? -1 : 1;
        double threshold = panSnapPosition(direction) - position;
        if (direction * (requested - threshold) < 0) {
            panZ = panTarget = requested;
            return;
        }

        // Stop this input at the crossing even if a single mouse event spans several carriages.
        panZ = Math.clamp(threshold, Math.min(panZ, requested), Math.max(panZ, requested));
        if (draggingPan) {
            draggingPan = false;
            panSnapCaptured = true;
            view.gestures.carriagePress = null;
        }
        panSnapPending = false;
        focusCarriage(selected + direction);
    }

    void holdPan() {
        settlingPan = null;
        panTarget = panZ;
        panVelocity = 0;
    }

    void startPan(double x, double y, double startedAt) {
        panSnapPending = false;
        animate();
        holdPan();
        panDragStartX = x;
        panDragStartY = panDirectionY = y;
        panScreenDirection = 0;
        panDraggedRight = false;
        panDragStartPosition = position + panZ;
        manualPan = true;
        lastPanInputTime = startedAt;
        draggingPan = true;
    }

    void updatePanDrag(double x, double y) {
        animate();
        // Remember the mouse's vertical direction in GUI pixels, including for the subsequent fling.
        double dy = y - panDirectionY;
        panDraggedRight = x - panDragStartX > PAN_DRAG_SLOP;
        if (Math.abs(dy) >= 1 || (panScreenDirection == 0 && dy != 0)) {
            panScreenDirection = dy < 0 ? -1 : 1;
            panDirectionY = y;
        }
        var direction = localCamera(0).transformDirection(new org.joml.Vector3f(0, 0, -1));
        double lengthSquared = (double) direction.x * direction.x + (double) direction.y * direction.y;
        double delta = lengthSquared > 1e-8
                ? ((x - panDragStartX) * direction.x + (y - panDragStartY) * direction.y) / lengthSquared : 0;
        double requested = panDragStartPosition + delta - position;
        double time = now();
        double elapsed = time - lastPanInputTime;
        if (elapsed > 1e-4) {
            double sampledVelocity = Math.clamp((requested - panZ) / elapsed, -PAN_MAX_SPEED, PAN_MAX_SPEED);
            panVelocity += (sampledVelocity - panVelocity) * (1 - Math.exp(-elapsed * 18));
        }
        lastPanInputTime = time;
        view.scene.hovered = null;
        movePan(requested);
    }

    void activateControl(int control) {
        if (view.layout == null) return;
        animate();
        if (control >= 3) {
            // Project the camera pan into screen space: arrow direction describes the visible train,
            // not the camera or the model's north/south axis.
            double screenX = localCamera(0).transformDirection(new org.joml.Vector3f(0, 0, -1)).x;
            if (Math.abs(screenX) < .0001) screenX = -Math.sin(Math.toRadians(targetYaw));
            double step = (control == 3 ? -1 : 1) * (screenX >= 0 ? 1 : -1);
            // A direction reversal should not first finish a queued movement the other way.
            if ((panTarget - panZ) * step < 0) panTarget = panZ;
            panTarget = clampPan(panTarget + step);
        } else {
            fromYaw = yaw;
            fromShell = shell;
            if (control == 0) targetShell = targetShell > .5f ? 0 : 1;
            else phase += control == 1 ? -1 : 1;
            targetYaw = FRONT_YAW + phase * 90;
            viewStarted = now();
        }
        view.scene.hovered = null;
        view.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
    }
}
