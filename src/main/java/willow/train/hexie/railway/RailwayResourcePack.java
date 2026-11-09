package willow.train.hexie.railway;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

/** Backend-generated defaults. Ordinary resource and data packs retain higher priority. */
public final class RailwayResourcePack extends AbstractPackResources {
    private static final Set<IEventBus> REGISTERED_BUSES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final String DESCRIPTION = "HeXie railway defaults";

    private final PackType type;
    private final Map<ResourceLocation, byte[]> resources;
    private final Set<String> namespaces;
    private final byte[] metadata;

    /**
     * Attach once to HeXie's mod bus, using a supplier of all registered railway defaults.
     * Repeated calls with the same bus retain its original supplier and do not add listeners.
     * The supplier is sampled when each pack is opened, including after a resource reload.
     * Resource paths are relative to assets/&lt;namespace&gt; or data/&lt;namespace&gt;.
     */
    public static synchronized void register(IEventBus bus,
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
                Component.literal(DESCRIPTION), PackSource.BUILT_IN, Optional.empty());
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

    RailwayResourcePack(PackLocationInfo location, PackType type, Map<ResourceLocation, byte[]> generated) {
        super(location);
        this.type = Objects.requireNonNull(type, "type");
        var snapshot = new TreeMap<ResourceLocation, byte[]>();
        var namespaces = new TreeSet<String>();
        Objects.requireNonNull(generated, "generated").forEach((id, bytes) -> {
            Objects.requireNonNull(id, "resource id");
            if (!validPath(id.getPath()))
                throw new IllegalArgumentException("Invalid generated railway resource path: " + id);
            snapshot.put(id, Objects.requireNonNull(bytes, "resource bytes").clone());
            namespaces.add(id.getNamespace());
        });
        resources = Collections.unmodifiableMap(snapshot);
        this.namespaces = Collections.unmodifiableSet(namespaces);
        int format = SharedConstants.getCurrentVersion().getPackVersion(type);
        metadata = ("{\"pack\":{\"description\":\"" + DESCRIPTION + "\",\"pack_format\":"
                + format + "}}").getBytes(StandardCharsets.UTF_8);
    }

    @Override @Nullable public IoSupplier<InputStream> getRootResource(String... path) {
        return path.length == 1 && PACK_META.equals(path[0]) ? stream(metadata) : null;
    }

    @Override @Nullable public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != this.type) return null;
        byte[] bytes = resources.get(id);
        return bytes == null ? null : stream(bytes);
    }

    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        Objects.requireNonNull(output, "output");
        if (type != this.type || !namespaces.contains(namespace) || !path.isEmpty() && !validPath(path)) return;
        String prefix = path.isEmpty() ? "" : path + "/";
        resources.forEach((id, bytes) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix))
                output.accept(id, stream(bytes));
        });
    }

    @Override public Set<String> getNamespaces(PackType type) {
        return type == this.type ? namespaces : Set.of();
    }

    private static boolean validPath(String path) {
        if (path.isEmpty()) return false;
        for (String part : path.split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return true;
    }

    private static IoSupplier<InputStream> stream(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }

    @Override public void close() {}
}
