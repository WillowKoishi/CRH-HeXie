package willow.train.hexie.client.cab;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.BlockControlHost;
import willow.train.hexie.api.cab.CabSurfaceHost;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.client.interaction.SurfaceInputSources;
import willow.train.hexie.interaction.ControlInputTargets;

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
        double length = direction.length();
        if (!Double.isFinite(origin.lengthSqr()) || !Double.isFinite(length) || length == 0) return null;
        var target = ControlInputTargets.seated(context, SurfaceInputSources.partialTick());
        var host = target == null ? null : BlockControlHost.find(target.state());
        if (!(host instanceof CabSurfaceHost)) return null;
        var offset = Vec3.atLowerCornerOf(context.console());
        var from = origin.add(offset);
        var hit = host.pickControl(target, from, from.add(direction.scale(4 / length)));
        if (hit == null) return null;
        var point = hit.blockHit().getLocation().subtract(offset);
        return new ControlSurface.Pick(hit.control(), point.distanceTo(origin), point);
    }
}
