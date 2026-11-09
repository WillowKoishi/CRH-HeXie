package willow.train.hexie.railway;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import willow.train.hexie.api.railway.RailwayDefinition;
import willow.train.hexie.api.railway.RailwayHandle;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;

/** Validates railway declarations and registers them with the current implementation. */
public final class RailwayCatalog {
    private final Map<ResourceLocation, RailwayHandle> entries = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> blocks = new LinkedHashMap<>();
    private final Map<IEventBus, Boolean> buses = new IdentityHashMap<>();
    private boolean frozen;

    public synchronized RailwayHandle register(IEventBus bus, ResourceLocation id, RailwayDefinition definition) {
        Objects.requireNonNull(bus); Objects.requireNonNull(id); Objects.requireNonNull(definition);
        if (frozen) throw new IllegalStateException("Register railways during mod construction, before block registration");
        if (!id.getNamespace().equals(definition.blockId().getNamespace()))
            throw new IllegalArgumentException("Railway and block IDs must share their owning namespace");
        if (entries.containsKey(id)) throw new IllegalArgumentException("Railway already registered: " + id);
        if (blocks.containsKey(definition.blockId())) throw new IllegalArgumentException("Railway block already registered: " + definition.blockId());
        if (buses.putIfAbsent(bus, true) == null) bus.addListener(this::onRegister);
        var handle = CreateRailwayBackend.register(bus, id, definition);
        entries.put(id, handle); blocks.put(definition.blockId(), id);
        return handle;
    }

    private synchronized void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) frozen = true;
    }

    public synchronized List<RailwayHandle> entries() { return List.copyOf(entries.values()); }
}
