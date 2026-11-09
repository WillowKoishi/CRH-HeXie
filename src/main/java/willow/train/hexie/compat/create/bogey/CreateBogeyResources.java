package willow.train.hexie.compat.create.bogey;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import willow.train.hexie.api.bogey.BogeyHandle;
import willow.train.hexie.api.bogey.BogeyRegistry;

/** Backend resource details for add-on bogeys; add-on resources only name their floor and mechanical top. */
public final class CreateBogeyResources {
    private static final Gson GSON = new Gson();
    private CreateBogeyResources() {}

    public static Map<PackType, Map<ResourceLocation, byte[]>> resources() {
        return resources(BogeyRegistry.entries());
    }

    /** Does not resolve block/item suppliers, so it is also suitable for isolated contract tests. */
    public static Map<PackType, Map<ResourceLocation, byte[]>> resources(List<BogeyHandle> bogeys) {
        var assets = new TreeMap<ResourceLocation, byte[]>();
        var data = new TreeMap<ResourceLocation, byte[]>();
        var languages = new TreeMap<String, JsonObject>();
        var pickaxe = new JsonArray();
        for (var handle : bogeys) {
            var definition = handle.definition();
            var blockId = definition.blockId();
            var modelId = id(blockId.getNamespace(), "block/bogey/generated/" + handle.id().getPath());
            var assembly = new JsonObject();
            assembly.addProperty("parent", "minecraft:block/block");
            assembly.addProperty("loader", "neoforge:composite");
            assembly.addProperty("ambientocclusion", false);
            var textures = new JsonObject();
            textures.addProperty("particle", "minecraft:block/iron_block");
            assembly.add("textures", textures);
            var children = new JsonObject();
            children.add("bolster", parent(definition.models().top()));
            children.add("floor", parent(definition.models().floor()));
            assembly.add("children", children);
            put(assets, path(modelId, "models/", ".json"), assembly);

            var variants = new JsonObject();
            for (String axis : List.of("x", "z")) {
                var variant = new JsonObject();
                variant.addProperty("model", modelId.toString());
                if (axis.equals("z")) variant.addProperty("y", 90);
                variants.add("axis=" + axis, variant);
            }
            var blockstate = new JsonObject();
            blockstate.add("variants", variants);
            put(assets, path(blockId, "blockstates/", ".json"), blockstate);
            put(assets, path(blockId, "models/item/", ".json"), parent(modelId));
            put(data, path(blockId, "loot_table/blocks/", ".json"), loot(blockId));
            pickaxe.add(blockId.toString());
            languages.computeIfAbsent(blockId.getNamespace(), ignored -> new JsonObject())
                    .addProperty("block." + blockId.getNamespace() + "." + blockId.getPath().replace('/', '.'),
                            definition.displayName());
        }
        languages.forEach((namespace, language) -> put(assets, id(namespace, "lang/en_us.json"), language));
        if (!pickaxe.isEmpty()) {
            var tag = new JsonObject();
            tag.add("values", pickaxe);
            put(data, id("minecraft", "tags/block/mineable/pickaxe.json"), tag);
        }
        return Map.of(PackType.CLIENT_RESOURCES, Map.copyOf(assets), PackType.SERVER_DATA, Map.copyOf(data));
    }

    private static JsonObject parent(ResourceLocation model) {
        var result = new JsonObject();
        result.addProperty("parent", model.toString());
        return result;
    }

    private static JsonObject loot(ResourceLocation blockId) {
        var entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", blockId.toString());
        var entries = new JsonArray();
        entries.add(entry);
        var condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");
        var conditions = new JsonArray();
        conditions.add(condition);
        var pool = new JsonObject();
        pool.addProperty("bonus_rolls", 0.0);
        pool.addProperty("rolls", 1.0);
        pool.add("entries", entries);
        pool.add("conditions", conditions);
        var pools = new JsonArray();
        pools.add(pool);
        var result = new JsonObject();
        result.addProperty("type", "minecraft:block");
        result.add("pools", pools);
        result.addProperty("random_sequence", id(blockId.getNamespace(), "blocks/" + blockId.getPath()).toString());
        return result;
    }

    private static ResourceLocation path(ResourceLocation id, String prefix, String suffix) {
        return id(id.getNamespace(), prefix + id.getPath() + suffix);
    }
    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
    private static void put(Map<ResourceLocation, byte[]> target, ResourceLocation id, JsonObject json) {
        target.put(id, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }
}
