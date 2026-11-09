package willow.train.hexie.client.interaction;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import willow.train.hexie.Main;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHit;
import willow.train.hexie.api.interaction.BlockControlHost;
import willow.train.hexie.api.interaction.BlockControlPicking;
import willow.train.hexie.api.interaction.ControlSurface;

/** Shared client detection over the surface geometry and full-motion bounds declared by each block. */
@EventBusSubscriber(modid = Main.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SurfaceControlPicking {
    private static final double EPSILON = 1e-9;
    private static final Map<ControlSurface, SurfaceControlIndex> INDEXES = new IdentityHashMap<>();

    private SurfaceControlPicking() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BlockControlPicking.registerClientPicker(SurfaceControlPicking::pick));
    }

    /** Input and output points use the target's storage/carrier-local coordinates. */
    public static BlockControlHit pick(BlockControlHost host, BlockControlContext target, Vec3 from, Vec3 to) {
        var blockState = target.state();
        var surface = host.surfaceGeometry(blockState);
        Vec3 offset = Vec3.atLowerCornerOf(target.position());
        Vec3 origin = from.subtract(offset), direction = to.subtract(from);
        double lengthSquared = direction.lengthSqr(), reach = Math.sqrt(lengthSquared);
        if (surface.parts().isEmpty() || !Double.isFinite(reach) || reach <= 0
                || !Double.isFinite(origin.lengthSqr()) || !Double.isFinite(lengthSquared) || lengthSquared == 0)
            return null;
        Vec3 unitRay = direction.scale(1 / reach);
        float inverseYaw = (float) Math.toRadians(-ControlSurface.yaw(host.surfaceFacing(blockState)));
        Vec3 localFrom = origin.add(-.5, 0, -.5).yRot(inverseYaw).add(.5, 0, .5);
        Vec3 localRay = unitRay.yRot(inverseYaw);
        var state = host.surfaceState(target);
        var animation = host.animation(target);
        var index = INDEXES.computeIfAbsent(surface, key -> new SurfaceControlIndex(key.parts()));
        var candidates = index.query(localFrom, localRay, reach);
        double nearest = reach;
        BlockControl selected = null;
        for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
            var part = surface.parts().get(i);
            Vec3 local = localFrom, axis = localRay;
            if (part.pose() != null) {
                var inverse = part.pose().at(state, animation).invert(new Matrix4f());
                local = new Vec3(inverse.transformPosition(localFrom.toVector3f()));
                axis = new Vec3(inverse.transformDirection(localRay.toVector3f()));
            }
            double distance = part.shape().hit(local, axis, nearest);
            if (distance >= 0 && distance < nearest) {
                nearest = distance;
                selected = part.control();
            }
        }
        if (selected == null || !(surface.body().hit(localFrom, localRay, nearest) >= nearest - EPSILON)) return null;
        Vec3 point = origin.add(unitRay.scale(nearest)).add(offset);
        var face = Direction.getNearest(-direction.x, -direction.y, -direction.z);
        return new BlockControlHit(selected, new BlockHitResult(point, face, target.position(), false));
    }
}
