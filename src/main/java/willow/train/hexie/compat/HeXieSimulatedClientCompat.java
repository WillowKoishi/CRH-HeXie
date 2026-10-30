package willow.train.hexie.compat;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import willow.train.hexie.Main;

import java.lang.reflect.Field;
import java.util.List;

/** Client-only workaround for the missing dimension lock list in Simulated 1.3.2. */
public final class HeXieSimulatedClientCompat {
    private HeXieSimulatedClientCompat() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(HeXieSimulatedClientCompat::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById("simulated")
                .filter(container -> "1.3.2".equals(container.getModInfo().getVersion().toString()))
                .ifPresent(container -> event.enqueueWork(HeXieSimulatedClientCompat::initializeMissingLocks));
    }

    private static void initializeMissingLocks() {
        try {
            // Simulated exposes setLocks, but no public getter or missing-dimension default.
            // Resolve its optional classes only after client setup, once rather than every frame.
            Class<?> clientClass = Class.forName("dev.simulated_team.simulated.SimulatedClient");
            Object handler = clientClass.getField("PHYSICS_STAFF_CLIENT_HANDLER").get(null);
            Field locksField = handler.getClass().getDeclaredField("locks");
            if (!locksField.trySetAccessible()) {
                Main.LOGGER.warn("Simulated physics staff lock compatibility was not applied: lock map is inaccessible");
                return;
            }
            Object locks = locksField.get(handler);
            if (!(locks instanceof Object2ObjectMap<?, ?> lockMap)) {
                Main.LOGGER.warn("Simulated physics staff lock compatibility was not applied: unsupported lock map");
                return;
            }
            initializeDefault(lockMap);
        } catch (ReflectiveOperationException | SecurityException exception) {
            Main.LOGGER.warn("Could not initialize Simulated physics staff lock compatibility", exception);
        }
    }

    private static <K, V> void initializeDefault(Object2ObjectMap<K, V> lockMap) {
        if (lockMap.defaultReturnValue() != null) return;
        // getLocks and isLocked both use Map.get. Only missing keys receive this empty list;
        // server snapshots still replace each dimension's real list through setLocks.
        @SuppressWarnings("unchecked")
        V noLocks = (V) List.of();
        lockMap.defaultReturnValue(noLocks);
        Main.LOGGER.info("Initialized missing-dimension physics staff locks for Simulated 1.3.2");
    }
}
