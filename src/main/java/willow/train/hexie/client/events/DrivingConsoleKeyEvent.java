package willow.train.hexie.client.events;

import net.neoforged.bus.api.Event;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;

/** Raw key input in the active cab; each surface adapter defines its meaning. */
public final class DrivingConsoleKeyEvent extends Event {
    public final DrivingConsoleContext context;
    public final int key, scanCode, action;
    private boolean handled;

    public DrivingConsoleKeyEvent(DrivingConsoleContext context, int key, int scanCode, int action) {
        this.context = context;
        this.key = key; this.scanCode = scanCode; this.action = action;
    }

    public void consume() { handled = true; }
    public boolean handled() { return handled; }
}
