package willow.train.hexie.ui.events.input;

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.SimpleCabControlsView;

// split from willow/train/hexie/client/events/DrivingConsoleClient.java
final class DrivingConsoleBindings {
    static boolean draggingHandle() { return HexieBridges.Client.surfaceInputClient().surfaceInputClient().dragging(); }
    public static boolean capturesInput() { return HexieBridges.Client.surfaceInputClient().surfaceInputClient().capturesInput(); }
    public static boolean handleKey(int key, int scanCode, int action) {
        return HexieBridges.Client.surfaceInputClient().surfaceInputClient().handleKey(key, scanCode, action);
    }
    static int cabKeyboardMask() {
        var held = HexieBridges.Client.surfaceInputClient().surfaceInputClient().heldControls();
        int mask = 0;
        if (held.contains("hexie:forward")) mask |= SimpleCabControlsView.FORWARD;
        if (held.contains("hexie:backward")) mask |= SimpleCabControlsView.BACKWARD;
        if (held.contains("hexie:left")) mask |= SimpleCabControlsView.LEFT;
        if (held.contains("hexie:right")) mask |= SimpleCabControlsView.RIGHT;
        if (held.contains("hexie:approach")) mask |= SimpleCabControlsView.APPROACH;
        return mask;
    }
}
