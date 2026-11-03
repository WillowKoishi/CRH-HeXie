package willow.train.hexie.item;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Packaged, stable item declarations. The creative tab and legacy pickers retain independent ordering.
 * This catalog does not register blocks or replace the resource-pack carriage browser API.
 */
public final class CarriagePartCatalog {
    private record Entry(ResourceLocation item, int creativeOrder, Map<String, Integer> palettes) {}
    private static final List<Entry> ENTRIES = load();
    private CarriagePartCatalog() {}

    private static List<Entry> load() {
        try (var stream = CarriagePartCatalog.class.getResourceAsStream("/data/hexie/carriage_parts.json")) {
            if (stream == null) throw new IllegalStateException("Missing carriage part catalog");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new ArrayList<Entry>();
            var orders = new HashMap<String, Set<Integer>>();
            for (var entry : json.entrySet()) {
                var value = entry.getValue().getAsJsonObject();
                var id = ResourceLocation.parse(entry.getKey());
                int creative = value.has("creative_order") ? value.get("creative_order").getAsInt() : -1;
                if (creative >= 0) checkOrder(orders, "creative", creative);
                var palettes = new HashMap<String, Integer>();
                if (value.has("palettes")) for (var palette : value.getAsJsonObject("palettes").entrySet()) {
                    int order = palette.getValue().getAsInt();
                    checkOrder(orders, palette.getKey(), order);
                    palettes.put(palette.getKey(), order);
                }
                result.add(new Entry(id, creative, Map.copyOf(palettes)));
            }
            return List.copyOf(result);
        } catch (java.io.IOException failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static void checkOrder(Map<String, Set<Integer>> orders, String group, int order) {
        if (order < 0 || !orders.computeIfAbsent(group, key -> new HashSet<>()).add(order))
            throw new IllegalArgumentException("Invalid or duplicate part order in " + group);
    }

    public static String[] creativeNames() {
        return ENTRIES.stream().filter(entry -> entry.creativeOrder() >= 0)
                .sorted(Comparator.comparingInt(Entry::creativeOrder))
                .map(entry -> entry.item().getPath()).toArray(String[]::new);
    }

    /** Fresh stacks; callers never own or mutate the shared declarations. Call after item registration. */
    public static List<ItemStack> palette(String name) {
        return ENTRIES.stream().filter(entry -> entry.palettes().containsKey(name))
                .sorted(Comparator.comparingInt(entry -> entry.palettes().get(name)))
                .map(entry -> new ItemStack(BuiltInRegistries.ITEM.get(entry.item()))).toList();
    }
}
