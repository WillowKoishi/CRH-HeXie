package willow.train.hexie.bridge.render;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface CarriageRenderBridge {
    CRH1StaticSectionsOperations crh1StaticSections();

    interface CRH1StaticSectionsOperations {
        boolean instanced(BlockPos pos);
        void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState);
    }

    CRH2StaticSectionsOperations crh2StaticSections();

    interface CRH2StaticSectionsOperations {
        boolean instanced(BlockPos pos);
        void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState);
    }

    CarriageBlockVisualOperations carriageBlockVisual();

    interface CarriageBlockVisualOperations {
        boolean replacesRenderer(BlockEntity blockEntity);
    }

    interface CarriageBlockVisualView extends SimpleDynamicVisual {
        void attach();
        default BlockEntityVisual<?> asBlockEntityVisual() { return (BlockEntityVisual<?>) this; }
    }
}
