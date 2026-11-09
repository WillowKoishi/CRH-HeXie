package willow.train.hexie.api.interaction;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A block declares controls in its own coordinates. A host may be implemented by
 * the block or attached through {@link BlockControlRegistry#register}.
 * Return shared immutable surfaces: registration and ID indexing happen when a
 * surface is constructed, not on each click. States and operations can be backed
 * by either a block entity or the host's own block-state implementation.
 *
 * <p>Define hit regions with {@link #surfaceGeometry}, legal handle detents and drag travel
 * on {@link BlockControl.Handle}, and capture entries/bindings with {@link #inputLayout}.
 * Keyboard bindings and direct mouse gestures both reach {@link #operateInput}; this host
 * owns their output and persistent state. Direct mouse use does not require capture.
 * The shared client detects the declared regions automatically; blocks need no client picker registration.
 */
public interface BlockControlHost {
    static BlockControlHost find(BlockState state) { return BlockControlRegistry.find(state); }
    default ControlSurface surfaceGeometry(BlockState state) { return ControlSurface.EMPTY; }
    default Direction surfaceFacing(BlockState state) { return Direction.NORTH; }
    default List<? extends BlockControl> controls(BlockState state) { return surfaceGeometry(state).controls(); }
    default BlockControl control(BlockState state, String id) { return surfaceGeometry(state).control(id); }
    /** Declares input capture and bindings independently of direct mouse interaction. */
    default ControlInputLayout inputLayout(BlockControlContext target) { return ControlInputLayout.EMPTY; }
    /** A block may reject or end capture using its own state and the requesting player. */
    default boolean canCapture(BlockControlUse use, boolean seated) { return true; }
    /** Explicit EXIT is a block-owned action; ordinary close/expiry only releases transient input. */
    default void endCapture(BlockControlUse use, boolean seated, boolean explicit) {
        operateInput(use, ControlInput.RELEASE);
    }
    /** Logical actions may be declared here without adding visible surface geometry. */
    default BlockControl inputControl(BlockState state, String id) { return control(state, id); }

    /**
     * Runs after target, binding and value checks. The default forwards a discrete control operation.
     * Blocks with HOLD bindings override this method to consume the complete held set, including
     * an empty NONE input on release; the default does not invent a press/release value for them.
     */
    default InteractionResult operateInput(BlockControlUse use, ControlInput input) {
        if (!input.hasCommand()) return InteractionResult.PASS;
        var control = inputControl(use.target().state(), input.controlId());
        if (control == null || !control.accepts(input.value())) return InteractionResult.PASS;
        return operateControl(use, control, input.value());
    }

    /** Conservative local envelope for all control poses and pointed entry regions, used for spatial indexing. */
    default AABB controlBounds(BlockState state) { return new AABB(0, 0, 0, 1, 1, 1); }
    /** Optional installed parts can extend a particular block's otherwise static envelope. */
    default AABB controlBounds(BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
        return controlBounds(state);
    }
    default boolean allowsWorldControl() { return true; }
    /**
     * Direct mouse routing after the shared region/part pick. True forwards to the existing
     * item/block/carrier use chain (including tools and adapters), without a duplicate control packet.
     * It does not opt out of inputLayout or operateInput: a native host may also declare captured bindings.
     */
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
        return BlockControlPicking.pickDeclared(this, target, from, to);
    }
    default InteractionResult operateControl(BlockControlUse use, BlockControl control, int value) {
        if (use.target().blocks().getBlockEntity(use.target().position()) instanceof BlockControlInstance instance) {
            instance.operateControl(use.player(), control, value);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
