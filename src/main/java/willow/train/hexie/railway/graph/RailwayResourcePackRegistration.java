package willow.train.hexie.railway.graph;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

// split from willow/train/hexie/railway/RailwayResourcePack.java
final class RailwayResourcePackRegistration {
    private static final Set<IEventBus> REGISTERED_BUSES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Attach once to HeXie's mod bus, using a supplier of all registered railway defaults.
     * Repeated calls with the same bus retain its original supplier and do not add listeners.
     * The supplier is sampled when each pack is opened, including after a resource reload.
     * Resource paths are relative to assets/&lt;namespace&gt; or data/&lt;namespace&gt;.
     */
    static synchronized void register(IEventBus bus,
            Supplier<Map<PackType, Map<ResourceLocation, byte[]>>> resources) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(resources, "resources");
        if (!REGISTERED_BUSES.add(bus)) return;
        bus.addListener((AddPackFindersEvent event) -> addPack(event, resources));
    }

    private static void addPack(AddPackFindersEvent event,
            Supplier<Map<PackType, Map<ResourceLocation, byte[]>>> resources) {
        PackType type = event.getPackType();
        var location = new PackLocationInfo("hexie/railway_defaults/" + type.getDirectory(),
                Component.literal(RailwayResourcePack.DESCRIPTION), PackSource.BUILT_IN, Optional.empty());
        var supplier = new Pack.ResourcesSupplier() {
            @Override public PackResources openPrimary(PackLocationInfo info) {
                var generated = Objects.requireNonNull(resources.get(), "Generated railway resources");
                return new RailwayResourcePack(info, type, generated.getOrDefault(type, Map.of()));
            }

            @Override public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return openPrimary(info);
            }
        };
        event.addRepositorySource(consumer -> {
            var pack = Pack.readMetaAndCreate(location, supplier, type,
                    new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack == null) throw new IllegalStateException("Cannot read railway defaults pack metadata");
            consumer.accept(pack);
        });
    }
}
