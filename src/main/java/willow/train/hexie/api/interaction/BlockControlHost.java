package willow.train.hexie.api.interaction;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A block declares controls in its own coordinates. A host may be implemented by
 * the block or attached through {@link BlockControlRegistry#register}.
 * Return shared immutable surfaces: registration and ID indexing happen when a
 * surface is constructed, not on each click. States and operations can be backed
 * by either a block entity or the host's own block-state implementation.
 */
public interface BlockControlHost {
    static BlockControlHost find(BlockState state) { return BlockControlRegistry.find(state); }
    default ControlSurface surfaceGeometry(BlockState state) { return ControlSurface.EMPTY; }
    default Direction surfaceFacing(BlockState state) { return Direction.NORTH; }
    default List<? extends BlockControl> controls(BlockState state) { return surfaceGeometry(state).controls(); }
    default BlockControl control(BlockState state, String id) { return surfaceGeometry(state).control(id); }

    /** Conservative local envelope for every pose. Only extensions beyond the owner cell need indexing. */
    default AABB controlBounds(BlockState state) { return new AABB(0, 0, 0, 1, 1, 1); }
    /** Optional installed parts can extend a particular block's otherwise static envelope. */
    default AABB controlBounds(BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        return controlBounds(state);
    }
    default boolean allowsWorldControl() { return true; }
    /** Native doors/tools keep their block and Create interaction dispatch after using this surface's picking. */
    default boolean nativeInteraction() { return false; }
    default ControlState surfaceState(BlockControlContext target) {
        return target.blocks().getBlockEntity(target.position()) instanceof BlockControlInstance instance
                ? instance.surfaceState() : ControlState.EMPTY;
    }
    default ControlSurface.Animation animation(BlockControlContext target) {
        return target.blocks().getBlockEntity(target.position()) instanceof ControlSurface.AnimationSource source
                ? (control, value) -> source.visualTravel(control, value, target.time()) : ControlSurface.Animation.AT_TARGET;
    }
    default BlockControlHit pickControl(BlockControlContext target, Vec3 from, Vec3 to) {
        Vec3 offset = Vec3.atLowerCornerOf(target.position()), ray = to.subtract(from);
        var hit = surfaceGeometry(target.state()).pick(surfaceFacing(target.state()), from.subtract(offset), ray,
                ray.length(), surfaceState(target), animation(target));
        if (hit == null) return null;
        var face = Direction.getNearest(-ray.x, -ray.y, -ray.z);
        return new BlockControlHit(hit.control(), new BlockHitResult(hit.point().add(offset), face, target.position(), false));
    }
    default InteractionResult operateControl(BlockControlUse use, BlockControl control, int value) {
        if (use.target().blocks().getBlockEntity(use.target().position()) instanceof BlockControlInstance instance) {
            instance.operateControl(use.player(), control, value);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
