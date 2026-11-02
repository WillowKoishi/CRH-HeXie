package willow.train.hexie.client.render.carriage;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionVisual;
import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import willow.train.hexie.Main;

import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps the Sable/Veil glass fallback local to affected HeXie carriages. */
public final class CarriageTransparencyCompat {
    private CarriageTransparencyCompat() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CarriageTransparencyCompat::onLoadComplete);
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        if (ModList.get().isLoaded("sable") && ModList.get().isLoaded("veil")) {
            event.enqueueWork(CarriageTransparencyCompat::install);
        }
    }

    private static void install() {
        var type = AllEntityTypes.CARRIAGE_CONTRAPTION.get();
        EntityVisualizer<? super CarriageContraptionEntity> previous = VisualizerRegistry.getVisualizer(type);
        if (!(previous instanceof SimpleEntityVisualizer<?>)) {
            Main.LOGGER.warn("HeXie carriage transparency compatibility was not installed: preserving a custom carriage visualizer");
            return;
        }
        VisualizerRegistry.setVisualizer(type, new PreservingVisualizer(previous));
        Main.LOGGER.info("Enabled local HeXie carriage transparency compatibility for Sable and Veil");
    }

    private static final class PreservingVisualizer implements EntityVisualizer<CarriageContraptionEntity> {
        private final EntityVisualizer<? super CarriageContraptionEntity> previous;
        private final AtomicBoolean reportedCustomVisual = new AtomicBoolean();

        private PreservingVisualizer(EntityVisualizer<? super CarriageContraptionEntity> previous) {
            this.previous = previous;
        }

        @Override
        public EntityVisual<? super CarriageContraptionEntity> createVisual(VisualizationContext context,
                CarriageContraptionEntity entity, float partialTick) {
            var original = previous.createVisual(context, entity, partialTick);
            if (!CompatibleCarriageVisual.needsCompatibility(entity)) return original;
            if (original == null || original.getClass() != CarriageContraptionVisual.class) {
                if (reportedCustomVisual.compareAndSet(false, true)) {
                    Main.LOGGER.warn("HeXie carriage transparency compatibility left a custom carriage visual unchanged");
                }
                return original;
            }
            // The original has not been registered with the manager yet: it owns no scheduled plans.
            // Release its instances before creating the compatible subclass, which retains Create's
            // normal bogey/actor handling and any mixins already applied to the parent class.
            original.delete();
            return new CompatibleCarriageVisual(context, entity, partialTick);
        }

        @Override
        public boolean skipVanillaRender(CarriageContraptionEntity entity) {
            return previous.skipVanillaRender(entity);
        }
    }
}
