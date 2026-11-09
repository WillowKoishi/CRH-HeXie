package willow.train.hexie.railway;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import willow.train.hexie.api.railway.RailwayDefinition;
import willow.train.hexie.api.railway.RailwayHandle;

/** Owns declaration identity and lifecycle independently of any concrete railway backend. */
public final class RailwayCatalog {
    private final Supplier<RailwayBackend> backend;
    private final Map<ResourceLocation, RailwayHandle> entries = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> blocks = new LinkedHashMap<>();
    private final Map<IEventBus, Boolean> buses = new IdentityHashMap<>();
    private boolean frozen;

    public RailwayCatalog(Supplier<RailwayBackend> backend) { this.backend = Objects.requireNonNull(backend); }

    public synchronized RailwayHandle register(IEventBus bus, ResourceLocation id, RailwayDefinition definition) {
        Objects.requireNonNull(bus); Objects.requireNonNull(id); Objects.requireNonNull(definition);
        if (frozen) throw new IllegalStateException("Register railways during mod construction, before block registration");
        if (!id.getNamespace().equals(definition.blockId().getNamespace()))
            throw new IllegalArgumentException("Railway and block IDs must share their owning namespace");
        if (entries.containsKey(id)) throw new IllegalArgumentException("Railway already registered: " + id);
        if (blocks.containsKey(definition.blockId())) throw new IllegalArgumentException("Railway block already registered: " + definition.blockId());
        if (buses.putIfAbsent(bus, true) == null) bus.addListener(this::onRegister);
        var handle = Objects.requireNonNull(backend.get().register(bus, id, definition));
        if (!id.equals(handle.id()) || !definition.equals(handle.definition()))
            throw new IllegalStateException("Railway backend returned a different declaration");
        entries.put(id, handle); blocks.put(definition.blockId(), id);
        return handle;
    }

    private synchronized void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) frozen = true;
    }

    public synchronized List<RailwayHandle> entries() { return List.copyOf(entries.values()); }
}
