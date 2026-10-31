package willow.train.hexie.client.events;

import net.neoforged.bus.api.Event;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;

/** Client input only. Future buttons can hit-test this ray in the console block's coordinates. */
public final class DrivingConsoleClickEvent extends Event {
    public final DrivingConsoleContext context;
    public final Vec3 origin, direction;
    public DrivingConsoleClickEvent(DrivingConsoleContext context, Vec3 origin, Vec3 direction) {
        this.context = context; this.origin = origin; this.direction = direction;
    }
}
