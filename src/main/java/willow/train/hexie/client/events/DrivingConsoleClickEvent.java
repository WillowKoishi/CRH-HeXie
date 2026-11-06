package willow.train.hexie.client.events;

import net.neoforged.bus.api.Event;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.cab.DrivingConsoleContext;

/** Unclaimed client click in console block coordinates, after testing the cab's declared controls. */
public final class DrivingConsoleClickEvent extends Event {
    public final DrivingConsoleContext context;
    public final Vec3 origin, direction;
    public DrivingConsoleClickEvent(DrivingConsoleContext context, Vec3 origin, Vec3 direction) {
        this.context = context; this.origin = origin; this.direction = direction;
    }
}
