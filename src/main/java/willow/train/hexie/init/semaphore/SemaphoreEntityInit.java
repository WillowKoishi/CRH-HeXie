package willow.train.hexie.init.semaphore;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import willow.train.hexie.Main;
import willow.train.hexie.blocks.semaphore.through.ThroughSignalBlockEntity;
import willow.train.hexie.blocks.semaphore.through.AdjustableSignalBlockEntity;



public class SemaphoreEntityInit {

    private static final CreateRegistrate REGISTRATE = Main.registrate();


    public static final BlockEntityEntry<ThroughSignalBlockEntity> HX_SEMAPHORE = REGISTRATE
            .blockEntity("hx_semaphore", ThroughSignalBlockEntity::new)
            .validBlocks(SemaphoreBlockInit.HX_SEMAPHORE)
            .register();

    public static final BlockEntityEntry<AdjustableSignalBlockEntity> ADJUSTABLE_SEMAPHORE = REGISTRATE
            .blockEntity("adjustable_semaphore", AdjustableSignalBlockEntity::new)
            .validBlocks(SemaphoreBlockInit.ADJUSTABLE_SEMAPHORE)
            .register();

    public static void register() {}
}
