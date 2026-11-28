package willow.train.hexie.bridge.sticker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side filter allowing ClientContraption to omit blocks whose rendering is managed
 * directly by HeXie's renderer engine (StaticShell carriage shells and sticker-cut blocks).
 */
public final class ContraptionRenderFilter {
    @FunctionalInterface
    public interface Predicate {
        boolean shouldHideFromContraptionMesh(Object contraption, BlockPos localPos, BlockState state);
    }

    private static volatile Predicate handler;

    private ContraptionRenderFilter() {}

    public static void register(Predicate predicate) {
        handler = predicate;
    }

    public static boolean shouldHide(Object contraption, BlockPos localPos, BlockState state) {
        Predicate h = handler;
        return h != null && h.shouldHideFromContraptionMesh(contraption, localPos, state);
    }
}
