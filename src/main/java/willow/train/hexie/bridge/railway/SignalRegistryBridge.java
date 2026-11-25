package willow.train.hexie.bridge.railway;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public interface SignalRegistryBridge {
    SemaphoreBlockInitOperations semaphoreBlockInit();

    interface SemaphoreBlockInitOperations {
        Block hxSemaphore();
        Block adjustableSemaphore();
    }

    SemaphoreEntityInitOperations semaphoreEntityInit();

    interface SemaphoreEntityInitOperations {
        BlockEntityType<? extends BlockEntity> hxSemaphore();
        BlockEntityType<? extends BlockEntity> adjustableSemaphore();
    }
}
