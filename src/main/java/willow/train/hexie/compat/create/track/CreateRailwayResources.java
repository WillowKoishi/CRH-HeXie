package willow.train.hexie.compat.create.track;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.content.trains.track.TrackShape;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.fml.ModList;
import willow.train.hexie.api.railway.RailwayHandle;
import willow.train.hexie.api.railway.RailwayModels;
import willow.train.hexie.api.railway.RailwayRegistry;
import willow.train.hexie.compat.create.railx.RailxTracks;

/** Translates HeXie model definitions into resources understood by the current backend. */
public final class CreateRailwayResources {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation DEFAULT_PARTICLE = id("hexie", "block/railway/default_particle");
    private static final ResourceLocation CREATE_PARTICLE =
            id("create", "block/palettes/stone_types/polished/andesite_cut_polished");

    private CreateRailwayResources() {}

    public static Map<PackType, Map<ResourceLocation, byte[]>> resources() {
        var generated = new Generated(RailwayRegistry.entries());
        if (ModList.get().isLoaded("railx")) {
            RailxTracks.entries().forEach((hiddenId, entry) ->
                    generated.hiddenTrack(entry.materialId(), hiddenId, entry.originalBlockId()));
        }
        return merge(generated.finish(), willow.train.hexie.compat.create.bogey.CreateBogeyResources.resources());
    }

    /** The shared runtime pack must retain both declarations' tags and language entries. */
    private static Map<PackType, Map<ResourceLocation, byte[]>> merge(
            Map<PackType, Map<ResourceLocation, byte[]>> tracks,
            Map<PackType, Map<ResourceLocation, byte[]>> bogeys) {
        var result = new java.util.EnumMap<PackType, Map<ResourceLocation, byte[]>>(PackType.class);
        for (var type : PackType.values()) {
            var entries = new TreeMap<>(tracks.getOrDefault(type, Map.of()));
            bogeys.getOrDefault(type, Map.of()).forEach((id, bytes) -> entries.merge(id, bytes, (first, second) -> {
                var left = com.google.gson.JsonParser.parseString(new String(first, StandardCharsets.UTF_8)).getAsJsonObject();
                var right = com.google.gson.JsonParser.parseString(new String(second, StandardCharsets.UTF_8)).getAsJsonObject();
                if (id.getPath().startsWith("tags/")) {
                    var values = left.getAsJsonArray("values");
                    right.getAsJsonArray("values").forEach(value -> {
                        if (!values.contains(value)) values.add(value);
                    });
                } else if (id.getPath().startsWith("lang/")) {
                    right.entrySet().forEach(entry -> {
                        if (left.has(entry.getKey())) throw new IllegalStateException("Duplicate language key: " + entry.getKey());
                        left.add(entry.getKey(), entry.getValue());
                    });
                } else {
                    throw new IllegalStateException("Generated bogey and track resources collide: " + id);
                }
                return GSON.toJson(left).getBytes(StandardCharsets.UTF_8);
            }));
            result.put(type, Map.copyOf(entries));
        }
        return Map.copyOf(result);
    }

    /** Generate from definitions alone, without resolving their registered block or item suppliers. */
    public static Map<PackType, Map<ResourceLocation, byte[]>> resources(List<RailwayHandle> railways) {
        return new Generated(railways).finish();
    }

    /** Combined defaults without resolving registries or loading optional integrations. */
    public static Map<PackType, Map<ResourceLocation, byte[]>> resources(List<RailwayHandle> railways,
            List<willow.train.hexie.api.bogey.BogeyHandle> bogeys) {
        return merge(resources(railways), willow.train.hexie.compat.create.bogey.CreateBogeyResources.resources(bogeys));
    }

    public static ResourceLocation particle(ResourceLocation texture) {
        return DEFAULT_PARTICLE.equals(texture) ? CREATE_PARTICLE : texture;
    }

    public static ResourceLocation blockModel(ResourceLocation materialId, String shapeModel) {
        return id(materialId.getNamespace(), "block/track/generated/" + materialId.getPath() + "/" + shapeModel);
    }

    public static RailwayModels.Shape shape(String model) {
        return switch (model) {
            case "x_ortho" -> RailwayModels.Shape.EAST_WEST;
            case "z_ortho" -> RailwayModels.Shape.NORTH_SOUTH;
            case "diag" -> RailwayModels.Shape.DIAGONAL_POSITIVE;
            case "diag_2" -> RailwayModels.Shape.DIAGONAL_NEGATIVE;
            case "ascending" -> RailwayModels.Shape.SLOPE;
            case "teleport" -> RailwayModels.Shape.PORTAL;
            case "cross_ortho" -> RailwayModels.Shape.CROSS_ORTHOGONAL;
            case "cross_diag" -> RailwayModels.Shape.CROSS_DIAGONAL;
            case "cross_d1_xo" -> RailwayModels.Shape.CROSS_POSITIVE_EAST_WEST;
            case "cross_d1_zo" -> RailwayModels.Shape.CROSS_POSITIVE_NORTH_SOUTH;
            case "cross_d2_xo" -> RailwayModels.Shape.CROSS_NEGATIVE_EAST_WEST;
            case "cross_d2_zo" -> RailwayModels.Shape.CROSS_NEGATIVE_NORTH_SOUTH;
            default -> throw new IllegalArgumentException("Unsupported Create track model: " + model);
        };
    }

    private static final class Generated {
        private final Map<ResourceLocation, byte[]> assets = new TreeMap<>();
        private final Map<ResourceLocation, byte[]> data = new TreeMap<>();
        private final Map<String, JsonObject> languages = new TreeMap<>();
        private final JsonArray blockTags = new JsonArray();
        private final JsonArray itemTags = new JsonArray();

        private Generated(List<RailwayHandle> railways) {
            railways.forEach(this::track);
        }

        private void track(RailwayHandle handle) {
            var definition = handle.definition();
            var blockId = definition.blockId();
            var models = definition.models();
            var variants = new JsonObject();
            for (var trackShape : TrackShape.values()) {
                var variant = new JsonObject();
                if (trackShape == TrackShape.NONE) {
                    variant.addProperty("model", "minecraft:block/air");
                } else {
                    String shapeModel = trackShape.getModel();
                    ResourceLocation generatedId = blockModel(handle.id(), shapeModel);
                    variant.addProperty("model", generatedId.toString());
                    var model = new JsonObject();
                    model.addProperty("parent", models.blocks().get(shape(shapeModel)).toString());
                    var textures = new JsonObject();
                    textures.addProperty("particle", particle(models.particle()).toString());
                    model.add("textures", textures);
                    put(assets, resource(generatedId, "models/", ".json"), model);
                }
                int rotation = Math.floorMod(trackShape.getModelRotation(), 360);
                if (rotation != 0) variant.addProperty("y", rotation);
                for (boolean turn : new boolean[]{false, true}) {
                    for (boolean waterlogged : new boolean[]{false, true}) {
                        variants.add("shape=" + trackShape.getSerializedName() + ",turn=" + turn
                                + ",waterlogged=" + waterlogged, variant);
                    }
                }
            }
            var blockstate = new JsonObject();
            blockstate.add("variants", variants);
            put(assets, resource(blockId, "blockstates/", ".json"), blockstate);

            var item = new JsonObject();
            item.addProperty("parent", "minecraft:item/generated");
            var textures = new JsonObject();
            textures.addProperty("layer0", models.itemTexture().toString());
            item.add("textures", textures);
            put(assets, resource(blockId, "models/item/", ".json"), item);
            put(data, resource(blockId, "loot_table/blocks/", ".json"), loot(blockId, blockId));
            blockTags.add(blockId.toString());
            itemTags.add(blockId.toString());
            languages.computeIfAbsent(blockId.getNamespace(), ignored -> new JsonObject())
                    .addProperty("block." + blockId.getNamespace() + "." + blockId.getPath().replace('/', '.'),
                            definition.displayName() + " Train Track");
        }

        private void hiddenTrack(ResourceLocation materialId, ResourceLocation hiddenId, ResourceLocation originalId) {
            var model = new JsonObject();
            model.addProperty("model", blockModel(materialId, "x_ortho").toString());
            var variants = new JsonObject();
            variants.add("", model);
            var blockstate = new JsonObject();
            blockstate.add("variants", variants);
            put(assets, resource(hiddenId, "blockstates/", ".json"), blockstate);
            put(data, resource(hiddenId, "loot_table/blocks/", ".json"), loot(hiddenId, originalId));
            var optionalTag = new JsonObject();
            optionalTag.addProperty("id", hiddenId.toString());
            optionalTag.addProperty("required", false);
            blockTags.add(optionalTag);
        }

        private Map<PackType, Map<ResourceLocation, byte[]>> finish() {
            languages.forEach((namespace, language) -> put(assets, id(namespace, "lang/en_us.json"), language));
            var blocks = new JsonObject();
            blocks.add("values", blockTags);
            put(data, id("create", "tags/block/tracks.json"), blocks);
            put(data, id("create", "tags/block/girdable_tracks.json"), blocks);
            put(data, id("minecraft", "tags/block/mineable/pickaxe.json"), blocks);
            put(data, id("c", "tags/block/relocation_not_supported.json"), blocks);
            var items = new JsonObject();
            items.add("values", itemTags);
            put(data, id("create", "tags/item/tracks.json"), items);
            return Map.of(PackType.CLIENT_RESOURCES, Map.copyOf(assets), PackType.SERVER_DATA, Map.copyOf(data));
        }
    }

    private static JsonObject loot(ResourceLocation blockId, ResourceLocation itemId) {
        var item = new JsonObject();
        item.addProperty("type", "minecraft:item");
        item.addProperty("name", itemId.toString());
        var entries = new JsonArray();
        entries.add(item);
        var survives = new JsonObject();
        survives.addProperty("condition", "minecraft:survives_explosion");
        var conditions = new JsonArray();
        conditions.add(survives);
        var pool = new JsonObject();
        pool.addProperty("bonus_rolls", 0.0);
        pool.add("conditions", conditions);
        pool.add("entries", entries);
        pool.addProperty("rolls", 1.0);
        var pools = new JsonArray();
        pools.add(pool);
        var loot = new JsonObject();
        loot.addProperty("type", "minecraft:block");
        loot.add("pools", pools);
        loot.addProperty("random_sequence", id(blockId.getNamespace(), "blocks/" + blockId.getPath()).toString());
        return loot;
    }

    private static ResourceLocation resource(ResourceLocation resource, String prefix, String suffix) {
        return id(resource.getNamespace(), prefix + resource.getPath() + suffix);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static void put(Map<ResourceLocation, byte[]> target, ResourceLocation id, JsonObject json) {
        target.put(id, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }
}
