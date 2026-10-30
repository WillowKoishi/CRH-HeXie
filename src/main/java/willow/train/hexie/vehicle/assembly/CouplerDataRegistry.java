package willow.train.hexie.vehicle.assembly;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.blocks.coupler.CouplerBlock;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.network.s2c.CouplerDefinitionsPacket;

/** Data-pack-owned block selectors, mating types, geometry and render parts. */
@EventBusSubscriber(modid = "hexie")
public final class CouplerDataRegistry {
    private static volatile Map<BlockState, Definition> definitions = Map.of();
    private static volatile String synchronizedJson = "{}";

    public record Definition(ResourceLocation id, String model, Direction direction,
            Point pivot, Point start, Point complete, double maxExtension,
            BlockState fixedState, BlockState movingState, Point movingOffset) implements CouplerBlock {
        @Override public Direction facing(BlockState state) { return direction; }
        @Override public BlockState carriageRenderState(BlockState state, boolean shiftedBase) { return fixedState; }
    }

    private CouplerDataRegistry() {}
    public static Definition find(BlockState state) { return definitions.get(state); }
    public static Map<BlockState, Definition> definitions() { return definitions; }
    public static String synchronizedJson() { return synchronizedJson; }

    @SubscribeEvent public static void reload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "vehicle/couplers") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
                var json = new JsonObject();
                files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> json.add(e.getKey().toString(), e.getValue()));
                install(json.toString());
            }
        });
    }

    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        var packet = new CouplerDefinitionsPacket(synchronizedJson);
        if (event.getPlayer() != null) PacketDistributor.sendToPlayer(event.getPlayer(), packet);
        else PacketDistributor.sendToAllPlayers(packet);
    }

    /** Parses off the active map and publishes only a completely validated replacement. */
    public static void install(String json) {
        var parsed = JsonParser.parseString(json).getAsJsonObject();
        var next = new HashMap<BlockState, Definition>();
        for (var entry : parsed.entrySet()) {
            var id = ResourceLocation.parse(entry.getKey());
            try {
                var data = entry.getValue().getAsJsonObject();
                if (data.has("required_mod") && !ModList.get().isLoaded(data.get("required_mod").getAsString())) continue;
                if (data.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unsupported schema");
                var selector = data.getAsJsonObject("block");
                var blockId = ResourceLocation.parse(selector.get("id").getAsString());
                if (!BuiltInRegistries.BLOCK.containsKey(blockId)) throw new IllegalArgumentException("Unknown block " + blockId);
                var block = BuiltInRegistries.BLOCK.get(blockId);
                var conditions = properties(selector);
                // Validate selector values even when they match no states.
                applyProperties(block.defaultBlockState(), conditions);
                String type = ResourceLocation.parse(data.get("type").getAsString()).toString();
                var facing = data.getAsJsonObject("facing");
                String property = facing.has("property") ? facing.get("property").getAsString() : null;
                Direction fixed = facing.has("value") ? horizontal(facing.get("value").getAsString()) : null;
                if ((property == null) == (fixed == null)) throw new IllegalArgumentException("Specify one facing property or value");
                Point pivot = point(data, "pivot", null), start = point(data, "start", null), complete = point(data, "complete", null);
                double extension = data.get("max_extension").getAsDouble();
                if (!Double.isFinite(extension) || extension < 0) throw new IllegalArgumentException("Invalid maximum extension");
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    if (!matches(state, conditions)) continue;
                    Direction direction = fixed != null ? fixed : horizontal(value(state, property));
                    if (facing.has("opposite") && facing.get("opposite").getAsBoolean()) direction = direction.getOpposite();
                    var stationary = part(data.get("stationary"), state);
                    var moving = data.getAsJsonObject("moving");
                    var movingState = part(moving, state);
                    var definition = new Definition(id, type, direction, pivot, start, complete, extension,
                            stationary, movingState, point(moving, "offset", new Point(0, 0, 0)));
                    if (next.putIfAbsent(state, definition) != null) throw new IllegalArgumentException("Overlapping selectors for " + state);
                }
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("Invalid coupler registration " + id + ": " + failure.getMessage(), failure);
            }
        }
        definitions = Map.copyOf(next);
        synchronizedJson = json;
    }

    private static BlockState part(JsonElement element, BlockState source) {
        if (element == null || element.isJsonNull()) return Blocks.AIR.defaultBlockState();
        var part = element.getAsJsonObject();
        BlockState base = source;
        if (part.has("block") && !part.get("block").getAsString().equals("self")) {
            var id = ResourceLocation.parse(part.get("block").getAsString());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown render block " + id);
            base = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        }
        return applyProperties(base, properties(part));
    }
    private static Map<String, String> properties(JsonObject object) {
        var result = new HashMap<String, String>();
        if (object.has("properties")) object.getAsJsonObject("properties").entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
        return result;
    }
    private static String value(BlockState state, String name) {
        var property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) throw new IllegalArgumentException("Unknown property " + name);
        return propertyValue(state, property);
    }
    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
    private static boolean matches(BlockState state, Map<String, String> properties) {
        return properties.entrySet().stream().allMatch(e -> value(state, e.getKey()).equals(e.getValue()));
    }
    private static BlockState applyProperties(BlockState state, Map<String, String> properties) {
        for (var entry : properties.entrySet()) {
            var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalArgumentException("Unknown property " + entry.getKey());
            state = set(state, property, entry.getValue());
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String text) {
        return state.setValue(property, property.getValue(text).orElseThrow(() -> new IllegalArgumentException("Invalid property value " + text)));
    }
    private static Direction horizontal(String value) {
        var direction = Direction.byName(value);
        if (direction == null || !direction.getAxis().isHorizontal()) throw new IllegalArgumentException("Expected horizontal facing");
        return direction;
    }
    private static Point point(JsonObject object, String key, Point fallback) {
        if (!object.has(key) && fallback != null) return fallback;
        var array = object.getAsJsonArray(key);
        if (array == null || array.size() != 3) throw new IllegalArgumentException("Expected three coordinates for " + key);
        double x = array.get(0).getAsDouble(), y = array.get(1).getAsDouble(), z = array.get(2).getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite point " + key);
        return new Point(x, y, z);
    }
}
