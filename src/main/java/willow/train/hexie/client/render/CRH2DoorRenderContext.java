package willow.train.hexie.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Real-world lighting coordinates supplied by Create's existing virtual block-entity render pass. */
public final class CRH2DoorRenderContext {
    /**
     * localToWorld maps block-local coordinates, before FACING and door animation, to world coordinates.
     * It includes the block's position inside the contraption, but no camera/view transform.
     * Consumers must copy this matrix before appending their own model transforms.
     */
    public record Context(ClientLevel level, Matrix4fc localToWorld) {}

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private CRH2DoorRenderContext() {}

    public static Context current() { return CURRENT.get(); }

    public static Context install(ClientLevel world, Matrix4fc contraptionToWorld, BlockPos blockPos) {
        Context previous = CURRENT.get();
        Matrix4f matrix = contraptionToWorld == null ? new Matrix4f() : new Matrix4f(contraptionToWorld);
        matrix.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        CURRENT.set(new Context(world, matrix));
        return previous;
    }

    public static void restore(Context previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
