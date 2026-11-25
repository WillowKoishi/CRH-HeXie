package willow.train.hexie.render.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.render.CarriageRenderBridge;
import willow.train.hexie.render.carriage.CRH1StaticSections;
import willow.train.hexie.render.carriage.CRH2StaticSections;
import willow.train.hexie.render.carriage.CarriageBlockVisual;

final class CarriageRenderServices implements CarriageRenderBridge {
    private final CRH1StaticSectionsOperations crh1StaticSections = new CRH1StaticSectionsOperations() {
        @Override public boolean instanced(BlockPos pos) {
            return CRH1StaticSections.instanced(pos);
        }
        @Override public void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
            CRH1StaticSections.changed(world, pos, oldState, newState);
        }
    };

    private final CRH2StaticSectionsOperations crh2StaticSections = new CRH2StaticSectionsOperations() {
        @Override public boolean instanced(BlockPos pos) {
            return CRH2StaticSections.instanced(pos);
        }
        @Override public void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
            CRH2StaticSections.changed(world, pos, oldState, newState);
        }
    };

    private final CarriageBlockVisualOperations carriageBlockVisual = CarriageBlockVisual::replacesRenderer;

    @Override public CRH1StaticSectionsOperations crh1StaticSections() {
        return crh1StaticSections;
    }

    @Override public CRH2StaticSectionsOperations crh2StaticSections() {
        return crh2StaticSections;
    }

    @Override public CarriageBlockVisualOperations carriageBlockVisual() {
        return carriageBlockVisual;
    }
}
