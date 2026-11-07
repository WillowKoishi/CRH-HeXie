package willow.train.hexie.blocks.signs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Packaged upright model heights, independent of client baking and coarse collision shapes. */
final class PanelAttachmentGeometry {
    private static final Map<String, Optional<JsonObject>> JSON = new HashMap<>();
    private static final Map<ResourceLocation, Double> TOPS = new HashMap<>();

    static synchronized double top(BlockState state, double fallback) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        var definition = json("/assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json");
        if (definition == null || !definition.has("variants")) return fallback;
        double top = Double.NEGATIVE_INFINITY;
        for (var entry : definition.getAsJsonObject("variants").entrySet()) {
            if (!matches(state, entry.getKey())) continue;
            var variants = entry.getValue().isJsonArray() ? entry.getValue().getAsJsonArray() : java.util.List.of(entry.getValue());
            for (var value : variants) {
                var variant = value.getAsJsonObject();
                // Mounted panels are upright. Other model transforms keep their declared common shape.
                if (variant.has("x") && variant.get("x").getAsInt() % 360 != 0) return fallback;
                if (!variant.has("model")) return fallback;
                var model = ResourceLocation.tryParse(variant.get("model").getAsString());
                if (model == null) return fallback;
                double modelTop = modelTop(model, 0);
                if (!Double.isFinite(modelTop)) return fallback;
                top = Math.max(top, modelTop);
            }
        }
        return Double.isFinite(top) ? top : fallback;
    }

    private static boolean matches(BlockState state, String selector) {
        if (selector.isEmpty()) return true;
        for (String term : selector.split(",")) {
            String[] pair = term.split("=", 2);
            if (pair.length != 2) return false;
            var property = state.getBlock().getStateDefinition().getProperty(pair[0]);
            if (property == null || !name(state, property).equals(pair[1])) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String name(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static double modelTop(ResourceLocation id, int depth) {
        if (depth > 16) return Double.NaN;
        if (TOPS.containsKey(id)) return TOPS.get(id);
        var model = json("/assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json");
        double top = Double.NEGATIVE_INFINITY;
        if (model != null && !model.has("loader")) {
            if (model.has("elements")) {
                for (var value : model.getAsJsonArray("elements")) {
                    var element = value.getAsJsonObject();
                    if (!element.has("faces") || element.getAsJsonObject("faces").size() == 0) continue;
                    var from = element.getAsJsonArray("from");
                    var to = element.getAsJsonArray("to");
                    var rotation = element.has("rotation") ? element.getAsJsonObject("rotation") : null;
                    for (int corner = 0; corner < 8; corner++) {
                        double[] p = new double[3];
                        for (int axis = 0; axis < 3; axis++) p[axis] = ((corner & (1 << axis)) == 0 ? from : to).get(axis).getAsDouble();
                        if (rotation != null) rotate(p, rotation);
                        top = Math.max(top, p[1] / 16);
                    }
                }
            } else if (model.has("parent")) {
                var parent = ResourceLocation.tryParse(model.get("parent").getAsString());
                if (parent != null) top = modelTop(parent, depth + 1);
            }
        }
        TOPS.put(id, top);
        return top;
    }

    private static void rotate(double[] p, JsonObject rotation) {
        String axis = rotation.get("axis").getAsString();
        if (axis.equals("y")) return; // Horizontal rotation cannot change height.
        var origin = rotation.getAsJsonArray("origin");
        int a = axis.equals("x") ? 1 : 0, b = axis.equals("x") ? 2 : 1;
        double angle = Math.toRadians(rotation.get("angle").getAsDouble());
        double c = Math.cos(angle), s = Math.sin(angle);
        double u = p[a] - origin.get(a).getAsDouble(), v = p[b] - origin.get(b).getAsDouble();
        double scale = rotation.has("rescale") && rotation.get("rescale").getAsBoolean() ? 1 / Math.abs(c) : 1;
        p[a] = (u * c - v * s) * scale + origin.get(a).getAsDouble();
        p[b] = (u * s + v * c) * scale + origin.get(b).getAsDouble();
    }

    private static JsonObject json(String path) {
        return JSON.computeIfAbsent(path, ignored -> {
            try (var input = PanelAttachmentGeometry.class.getResourceAsStream(path)) {
                return input == null ? Optional.empty() : Optional.of(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception invalid) { return Optional.empty(); }
        }).orElse(null);
    }

    private PanelAttachmentGeometry() {}
}
