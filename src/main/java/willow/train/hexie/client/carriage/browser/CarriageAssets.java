package willow.train.hexie.client.carriage.browser;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.BaseRailBlock;
import com.simibubi.create.content.trains.track.ITrackBlock;
import willow.train.hexie.Main;
import willow.train.hexie.api.carriage.CarriageRegistry;

import java.io.*;
import java.util.*;

/** Resource-only models; nothing is placed into the player's world. */
public final class CarriageAssets {
    public record Cell(BlockPos pos, BlockState state, CompoundTag data, boolean shell) {}
    public record Model(ResourceLocation id, Component title, double frontGap, double rearGap,
                        int width, int height, int length, List<Cell> cells,
                        Map<ResourceLocation, List<ResourceLocation>> assignments,
                        Map<ResourceLocation, List<ResourceLocation>> groups, BlockState track) {
        public List<ResourceLocation> variantIds(ResourceLocation block) {
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (ResourceLocation group : assignments.getOrDefault(block, List.of()))
                ids.addAll(groups.getOrDefault(group, List.of()));
            if (ids.isEmpty()) ids.add(block);
            return List.copyOf(ids);
        }
    }
    public record Catalog(List<Model> models, List<String> errors) {
        public List<ItemStack> variants(Model model, Cell cell) {
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(cell.state().getBlock());
            List<ItemStack> result = new ArrayList<>();
            for (ResourceLocation id : model.variantIds(block)) {
                ItemStack stack = new ItemStack(BuiltInRegistries.BLOCK.get(id));
                if (!stack.isEmpty() && stack.isItemEnabled(Minecraft.getInstance().player.connection.enabledFeatures()))
                    result.add(stack);
            }
            return result;
        }
    }

    private CarriageAssets() {}

    public static Catalog load(ResourceManager resources, List<ResourceLocation> requested) {
        Map<ResourceLocation, ResourceLocation> declarations = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        declarations.putAll(CarriageRegistry.entries());
        List<String> errors = new ArrayList<>();
        resources.listResources("carriage_ui/types", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String path = id.getPath();
                ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                        path.substring("carriage_ui/types/".length(), path.length() - 5));
                declarations.put(modelId, ResourceLocation.parse(json.get("assets").getAsString()));
            } catch (Exception e) { report(errors, id, e); }
        });
        List<Model> models = new ArrayList<>();
        requested.forEach(id -> {
            ResourceLocation directory = declarations.get(id);
            if (directory == null) { errors.add("Missing carriage declaration: " + id); Main.LOGGER.warn("Missing carriage declaration: {}", id); return; }
            try {
                JsonObject json = readJson(resources, child(directory, "carriage.json"));
                if (json.get("format").getAsInt() != 1) throw new IOException("Unsupported carriage format");
                Map<ResourceLocation, List<ResourceLocation>> assignments = new LinkedHashMap<>();
                Map<ResourceLocation, List<ResourceLocation>> localGroups = new LinkedHashMap<>();
                if (json.has("groups")) for (var entry : json.getAsJsonObject("groups").entrySet()) {
                    ResourceLocation group = ResourceLocation.parse(entry.getKey());
                    List<ResourceLocation> members = new ArrayList<>();
                    for (JsonElement value : entry.getValue().getAsJsonArray()) {
                        ResourceLocation block = ResourceLocation.parse(value.getAsString());
                        if (!BuiltInRegistries.BLOCK.containsKey(block)) throw new IOException("Unknown group block " + block);
                        members.add(block);
                        assignments.computeIfAbsent(block, k -> new ArrayList<>()).add(group);
                    }
                    localGroups.put(group, List.copyOf(members));
                }
                List<Cell> cells = new ArrayList<>();
                int[] bounds = {0, 0, 0};
                readStructure(resources, child(directory, json.get("exterior").getAsString()), true, cells, bounds);
                readStructure(resources, child(directory, json.get("interior").getAsString()), false, cells, bounds);
                Set<BlockPos> occupied = new HashSet<>();
                for (Cell cell : cells) if (!occupied.add(cell.pos())) throw new IOException("Overlapping shell/interior at " + cell.pos());
                if (cells.isEmpty()) throw new IOException("Empty carriage");
                double front = gap(json, "front_gap"), rear = gap(json, "rear_gap");
                if (front + bounds[2] / 2.0 < .125 || rear + bounds[2] / 2.0 < .125)
                    throw new IOException("Gap would overlap carriage centers");
                Component title = json.has("title_key") ? Component.translatable(json.get("title_key").getAsString())
                        : Component.literal(json.get("title").getAsString());
                BlockState track = readTrack(json.getAsJsonObject("track"));
                assignments.replaceAll((block, memberships) -> List.copyOf(memberships));
                models.add(new Model(id, title, front, rear, bounds[0], bounds[1], bounds[2], List.copyOf(cells),
                        Map.copyOf(assignments), Map.copyOf(localGroups), track));
            } catch (Exception e) { report(errors, id, e); }
        });
        return new Catalog(List.copyOf(models), List.copyOf(errors));
    }

    private static double gap(JsonObject json, String name) throws IOException {
        double value = json.has(name) ? json.get(name).getAsDouble() : .5;
        if (!Double.isFinite(value) || Math.abs(value) > 256) throw new IOException("Invalid " + name);
        return value;
    }

    public static boolean isTrack(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock || state.getBlock() instanceof ITrackBlock;
    }

    private static BlockState readTrack(JsonObject json) throws IOException {
        if (json == null) throw new IOException("Missing registered track");
        ResourceLocation id = ResourceLocation.parse(json.get("block").getAsString());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IOException("Unknown track " + id);
        BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        if (!isTrack(state)) throw new IOException("Registered track must be a vanilla/Create track: " + id);
        Property<?> shape = state.getBlock().getStateDefinition().getProperty("shape");
        if (shape != null && shape.getValue("zo").isPresent())
            state = withValue(state, shape, "zo");
        if (json.has("properties")) for (var entry : json.getAsJsonObject("properties").entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IOException("Unknown track property " + entry.getKey());
            state = withValue(state, property, entry.getValue().getAsString());
        }
        return state;
    }

    private static ResourceLocation child(ResourceLocation directory, String name) throws IOException {
        if (name.startsWith("/") || name.contains("..") || name.contains(":")) throw new IOException("Asset must be relative: " + name);
        return ResourceLocation.fromNamespaceAndPath(directory.getNamespace(), directory.getPath() + "/" + name);
    }

    private static JsonObject readJson(ResourceManager resources, ResourceLocation id) throws IOException {
        try (Reader reader = resources.getResourceOrThrow(id).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void readStructure(ResourceManager resources, ResourceLocation id, boolean shell,
                                      List<Cell> cells, int[] bounds) throws IOException {
        CompoundTag root;
        try (InputStream stream = resources.getResourceOrThrow(id).open()) {
            root = NbtIo.readCompressed(stream, NbtAccounter.create(32L * 1024 * 1024));
        }
        ListTag size = root.getList("size", Tag.TAG_INT);
        if (size.size() != 3) throw new IOException("Missing structure size: " + id);
        for (int i = 0; i < 3; i++) {
            if (size.getInt(i) < 1 || size.getInt(i) > 256) throw new IOException("Structure dimension outside 1..256");
            // Both layers must share an origin AND the complete bounding box.
            if (bounds[i] != 0 && bounds[i] != size.getInt(i)) throw new IOException("Layer sizes differ");
            bounds[i] = size.getInt(i);
        }
        List<BlockState> palette = new ArrayList<>();
        for (Tag tag : root.getList("palette", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            ResourceLocation blockId = ResourceLocation.parse(entry.getString("Name"));
            if (!BuiltInRegistries.BLOCK.containsKey(blockId)) throw new IOException("Unknown structure block " + blockId);
            BlockState state = BuiltInRegistries.BLOCK.get(blockId).defaultBlockState();
            CompoundTag props = entry.getCompound("Properties");
            for (String name : props.getAllKeys()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
                if (property == null) throw new IOException("Unknown property " + blockId + "/" + name);
                state = withValue(state, property, props.getString(name));
            }
            palette.add(state);
        }
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        if (blocks.size() + cells.size() > 32768) throw new IOException("Carriage exceeds 32768 blocks");
        for (Tag tag : blocks) {
            CompoundTag block = (CompoundTag) tag;
            int index = block.getInt("state");
            if (index < 0 || index >= palette.size()) throw new IOException("Invalid palette index");
            BlockState state = palette.get(index);
            if (state.isAir() || isTrack(state)) continue;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3) throw new IOException("Invalid block position");
            for (int i = 0; i < 3; i++) if (pos.getInt(i) < 0 || pos.getInt(i) >= bounds[i])
                throw new IOException("Block outside declared bounds");
            cells.add(new Cell(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state,
                    block.contains("nbt") ? block.getCompound("nbt").copy() : null, shell));
        }
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) throws IOException {
        T parsed = property.getValue(value).orElseThrow(() -> new IOException("Invalid property value: " + property.getName() + "=" + value));
        return state.setValue(property, parsed);
    }

    private static void report(List<String> errors, ResourceLocation id, Exception error) {
        errors.add(id + ": " + error.getMessage());
        Main.LOGGER.warn("Could not load carriage {}", id, error);
    }
}
