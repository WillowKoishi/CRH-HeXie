package willow.train.hexie.compat.bridge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.bridge.railway.SignalRegistryBridge;
import willow.train.hexie.compat.create.signal.SemaphoreBlockInit;
import willow.train.hexie.compat.create.signal.SemaphoreEntityInit;

final class SignalRegistryPort implements SignalRegistryBridge {
    private final SemaphoreBlockInitOperations semaphoreBlockInit = new SemaphoreBlockInitOperations() {
        @Override
        public Block hxSemaphore() {
            return SemaphoreBlockInit.hxSemaphore();
        }

        @Override
        public Block adjustableSemaphore() {
            return SemaphoreBlockInit.adjustableSemaphore();
        }
    };

    private final SemaphoreEntityInitOperations semaphoreEntityInit = new SemaphoreEntityInitOperations() {
        @Override
        public BlockEntityType<? extends BlockEntity> hxSemaphore() {
            return SemaphoreEntityInit.hxSemaphore();
        }

        @Override
        public BlockEntityType<? extends BlockEntity> adjustableSemaphore() {
            return SemaphoreEntityInit.adjustableSemaphore();
        }
    };

    @Override
    public SemaphoreBlockInitOperations semaphoreBlockInit() {
        return semaphoreBlockInit;
    }

    @Override
    public SemaphoreEntityInitOperations semaphoreEntityInit() {
        return semaphoreEntityInit;
    }
}
