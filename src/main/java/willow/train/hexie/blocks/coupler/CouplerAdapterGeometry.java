package willow.train.hexie.blocks.coupler;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.item.RescueCouplerItem;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;

/** Actual rescue mesh picking and envelopes, in the host's original contact-point frame. */
public final class CouplerAdapterGeometry {
    private CouplerAdapterGeometry() {}

    private static List<CouplerAdapter> ordered(CouplerAdapter adapter, String baseModel) {
        return adapter.layers().isEmpty() || baseModel.equals(adapter.firstModel())
                ? adapter.items() : adapter.items().reversed();
    }

    public static CouplerGeometry.Hit clip(CouplerAdapter adapter, String baseModel, float outerProgress,
            Vec3 from, Vec3 to) {
        if (adapter == null) return null;
        List<CouplerAdapter> layers = ordered(adapter, baseModel);
        Vec3 origin = Vec3.ZERO;
        String exposed = baseModel;
        CouplerGeometry.Hit nearest = null;
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            boolean reversed = !exposed.equals(layer.firstModel());
            if (layer.itemId().equals(RescueCouplerItem.ID)) {
                Vec3 firstToSecond = vec(layer.offset());
                Vec3 a = from.subtract(origin), b = to.subtract(origin);
                if (reversed) { a = halfTurn(a).add(firstToSecond); b = halfTurn(b).add(firstToSecond); }
                float progress = !reversed && index == layers.size() - 1 ? outerProgress : 0;
                var hit = RescueCouplerItem.geometry().clip(progress, a, b);
                if (hit != null) {
                    Vec3 point = hit.point(), normal = Vec3.atLowerCornerOf(hit.face().getNormal());
                    if (reversed) { point = halfTurn(point.subtract(firstToSecond)); normal = halfTurn(normal); }
                    hit = new CouplerGeometry.Hit(point.add(origin),
                            Direction.getNearest(normal.x, normal.y, normal.z), true, index);
                    if (nearest == null || hit.point().distanceToSqr(from) < nearest.point().distanceToSqr(from)) nearest = hit;
                }
            }
            origin = origin.add(vec(layer.offsetFrom(exposed)));
            exposed = layer.otherModel(exposed);
        }
        return nearest;
    }

    /** Includes every knuckle angle; null when no adapter is installed. */
    public static AABB bounds(CouplerAdapter adapter, String baseModel) {
        if (adapter == null) return null;
        AABB result = null;
        Vec3 origin = Vec3.ZERO;
        String exposed = baseModel;
        for (var layer : ordered(adapter, baseModel)) {
            boolean reversed = !exposed.equals(layer.firstModel());
            AABB local;
            if (layer.itemId().equals(RescueCouplerItem.ID)) {
                local = RescueCouplerItem.geometry().bounds(Direction.NORTH);
                if (reversed) {
                    Vec3 offset = vec(layer.offset());
                    local = new AABB(halfTurn(new Vec3(local.minX, local.minY, local.minZ).subtract(offset)),
                            halfTurn(new Vec3(local.maxX, local.maxY, local.maxZ).subtract(offset)));
                }
            } else {
                // Legacy item adapters have a unit item cross-section and their configured axial length.
                local = new AABB(-.5, -.5, -layer.thickness(), .5, .5, 0);
            }
            local = local.move(origin);
            result = result == null ? local : result.minmax(local);
            origin = origin.add(vec(layer.offsetFrom(exposed)));
            exposed = layer.otherModel(exposed);
        }
        return result;
    }

    /** Complete block-local envelope, including the block's facing and all adapter layers. */
    public static AABB bounds(BlockState state, CouplerBlockEntity entity) { return bounds(state, entity == null ? null : entity.adapter()); }

    public static AABB bounds(BlockState state, CouplerAdapter adapter) {
        var hook = CouplerBlock.of(state);
        if (hook == null) return new AABB(0, 0, 0, 1, 1, 1);
        AABB result = hook.geometry() == null ? new AABB(0, 0, 0, 1, 1, 1) : hook.geometry().bounds(hook.facing(state));
        AABB extra = bounds(adapter, hook.model());
        if (extra == null) return result;
        Vec3 contact = vec(hook.complete()).add(.5, .5, .5);
        extra = extra.move(contact);
        var a = CouplerInteraction.fromModel(state, net.minecraft.core.BlockPos.ZERO, new Vec3(extra.minX, extra.minY, extra.minZ));
        var b = CouplerInteraction.fromModel(state, net.minecraft.core.BlockPos.ZERO, new Vec3(extra.maxX, extra.maxY, extra.maxZ));
        return result.minmax(new AABB(a, b));
    }

    private static Vec3 halfTurn(Vec3 value) { return new Vec3(-value.x, value.y, -value.z); }
    private static Vec3 vec(willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point value) {
        return new Vec3(value.x(), value.y(), value.z());
    }
}
