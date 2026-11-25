package willow.train.hexie.compat.create.signal;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.core.HexieConstants;

public class SemaphoreEntityInit {

    public static BlockEntityEntry<SignalBlockEntity> HX_SEMAPHORE;
    public static BlockEntityEntry<SignalBlockEntity> ADJUSTABLE_SEMAPHORE;

    public static BlockEntityType<?> hxSemaphore() {
        return HX_SEMAPHORE == null ? null : HX_SEMAPHORE.get();
    }

    public static BlockEntityType<?> adjustableSemaphore() {
        return ADJUSTABLE_SEMAPHORE == null ? null : ADJUSTABLE_SEMAPHORE.get();
    }

    public static void register() {
        if (ModList.get().isLoaded("create")) CreateSignals.register();
    }

    private static final class CreateSignals {
        static {
            SemaphoreBlockInit.register();
            CreateRegistrate registrate = CreateBootstrap.registrate();
            HX_SEMAPHORE = registrate
                    .<SignalBlockEntity>blockEntity("create_hx_semaphore", ThroughSignalBlockEntity::new)
                    .validBlocks(SemaphoreBlockInit.HX_SEMAPHORE)
                    .onRegister(type -> BuiltInRegistries.BLOCK_ENTITY_TYPE.addAlias(
                            HexieConstants.asResource("hx_semaphore"), HexieConstants.asResource("create_hx_semaphore")))
                    .register();
            ADJUSTABLE_SEMAPHORE = registrate
                    .<SignalBlockEntity>blockEntity("create_adjustable_semaphore", AdjustableSignalBlockEntity::new)
                    .validBlocks(SemaphoreBlockInit.ADJUSTABLE_SEMAPHORE)
                    .onRegister(type -> BuiltInRegistries.BLOCK_ENTITY_TYPE.addAlias(
                            HexieConstants.asResource("adjustable_semaphore"), HexieConstants.asResource("create_adjustable_semaphore")))
                    .register();
        }

        private static void register() {}
    }
}
