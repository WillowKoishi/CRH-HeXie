package willow.train.hexie.bogey;

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
import willow.train.hexie.api.bogey.BogeyDefinition;
import willow.train.hexie.api.bogey.BogeyHandle;

/** Registration identity and lifecycle, independent of rendering and railway backend types. */
public final class BogeyCatalog {
    private final Supplier<BogeyBackend> backend;
    private final Map<ResourceLocation, BogeyHandle> entries = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> blocks = new LinkedHashMap<>();
    private final Map<IEventBus, Boolean> buses = new IdentityHashMap<>();
    private boolean frozen;

    public BogeyCatalog(Supplier<BogeyBackend> backend) { this.backend = Objects.requireNonNull(backend); }

    public synchronized BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition) {
        Objects.requireNonNull(bus); Objects.requireNonNull(id); Objects.requireNonNull(definition);
        if (frozen) throw new IllegalStateException("Register bogeys during mod construction, before block registration");
        if (!id.getNamespace().equals(definition.blockId().getNamespace()))
            throw new IllegalArgumentException("Bogey and block IDs must share their owning namespace");
        if (!id.equals(definition.model().id()))
            throw new IllegalArgumentException("Bogey registration ID must equal its operating model ID");
        if (entries.containsKey(id)) throw new IllegalArgumentException("Bogey already registered: " + id);
        if (blocks.containsKey(definition.blockId()))
            throw new IllegalArgumentException("Bogey block already registered: " + definition.blockId());
        if (buses.putIfAbsent(bus, true) == null) bus.addListener(this::onRegister);
        var handle = Objects.requireNonNull(backend.get().register(bus, id, definition));
        if (!id.equals(handle.id()) || !definition.equals(handle.definition()))
            throw new IllegalStateException("Bogey backend returned a different declaration");
        entries.put(id, handle);
        blocks.put(definition.blockId(), id);
        return handle;
    }

    private synchronized void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) frozen = true;
    }

    public synchronized List<BogeyHandle> entries() { return List.copyOf(entries.values()); }
}
