package willow.train.hexie.bogey;

import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.api.bogey.BogeyRegistry;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.vehicle.bogey.BuiltinBogeyModel;

/** The physical-train categories, available even when Simurail is not installed. */
public final class BogeySelectionCatalog {
    private BogeySelectionCatalog() {}

    public record Entry(ResourceLocation id, String category, Component name,
                        BogeyStyle style, BogeySizes.BogeySize size) {
        public Component categoryName() {
            return Component.translatable(category.equals("addon")
                    ? "gui.hexie.bogey_selection.addon" : "simurail_bogey_category.hexie." + category);
        }
        public HexieBogeyBlock<?> block() { return (HexieBogeyBlock<?>) style.getBlockForSize(size); }
    }

    /** Resolve registered styles only after mod setup; keep ordering identical on both sides. */
    public static List<Entry> entries() {
        var entries = new ArrayList<Entry>();
        for (var model : BuiltinBogeyModel.values()) {
            var style = AllBogeyStyles.BOGEY_STYLES.get(model.style());
            if (style == null) continue;
            var size = style.validSizes().stream().filter(candidate -> candidate.id().equals(model.size()))
                    .findFirst().orElse(null);
            if (size == null || !(style.getBlockForSize(size) instanceof HexieBogeyBlock<?>)) continue;
            String category = switch (model.style().getPath()) {
                case "hxstandardbogey" -> "passenger";
                case "loco" -> "loco";
                case "steambogey" -> "steam";
                case "crhbogey" -> "emu";
                default -> "addon";
            };
            entries.add(new Entry(model.id(), category,
                    Component.translatable("simurail_bogey_entry.hexie." + model.id().getPath()), style, size));
        }
        BogeyRegistry.entries().stream().sorted(Comparator.comparing(handle -> handle.id().toString()))
                .forEach(handle -> {
                    if (handle.block().get() instanceof HexieBogeyBlock<?> block)
                        entries.add(new Entry(handle.id(), "addon", block.getDefaultStyle().displayName,
                                block.getDefaultStyle(), block.getSize()));
                });
        return List.copyOf(entries);
    }
}
