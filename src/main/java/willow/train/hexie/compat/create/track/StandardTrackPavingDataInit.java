package willow.train.hexie.compat.create.track;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.core.HexieConstants;

/** Persistent per-stack settings for standard track. */
public final class StandardTrackPavingDataInit {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HexieConstants.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>>
            STANDARD_TRACK_PAVING_MATERIAL = COMPONENTS.registerComponentType(
                    "standard_track_paving_material",
                    builder -> builder.persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    private StandardTrackPavingDataInit() {}

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
