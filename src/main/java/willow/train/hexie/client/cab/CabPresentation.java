package willow.train.hexie.client.cab;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.cab.CabSurfaceHost;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.api.cab.DrivingConsoleContext;

/** Rendering and hit testing both sample the same per-desk animation at the world render time. */
public final class CabPresentation {
    private CabPresentation() {}

    public static ControlSurface.Animation animation(ControlSurface.AnimationSource source) {
        var mc = Minecraft.getInstance();
        if (source == null || mc.level == null) return ControlSurface.Animation.AT_TARGET;
        double now = mc.level.getGameTime() + (double) mc.getTimer().getGameTimeDeltaPartialTick(false);
        return (control, target) -> source.visualTravel(control, target, now);
    }

    public static BlockControl hit(DrivingConsoleContext context, Vec3 origin, Vec3 direction) {
        var pick = pick(context, origin, direction);
        return pick == null ? null : pick.control();
    }

    public static ControlSurface.Pick pick(DrivingConsoleContext context, Vec3 origin, Vec3 direction) {
        var seat = context.seatAccess();
        var state = seat.blockState(context.console());
        if (state == null || !(state.getBlock() instanceof CabSurfaceHost host)) return null;
        var surface = host.surfaceGeometry(state);
        if (surface.parts().isEmpty()) return null;
        var animation = animation(seat.animationSource(context.console()));
        return surface.pick(host.surfaceFacing(state), origin, direction, 4, context.surfaceState(), animation);
    }
}
