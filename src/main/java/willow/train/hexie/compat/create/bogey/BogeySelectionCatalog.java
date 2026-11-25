package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.CreateBogeyBlockView;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.core.api.bogey.BogeyRegistry;

/** The physical-train categories, available even when Simurail is not installed. */
public final class BogeySelectionCatalog {
    private BogeySelectionCatalog() {}

    public record Entry(ResourceLocation id, String category, Component name,
                        BogeyStyle style, BogeySizes.BogeySize size) implements CreateTrainBridgePort.BogeySelectionCatalogEntryView {
        public Component categoryName() {
            return Component.translatable(category.equals("addon")
                    ? "gui.hexie.bogey_selection.addon" : "simurail_bogey_category.hexie." + category);
        }
        public CreateBogeyBlockView block() { return (CreateBogeyBlockView) style.getBlockForSize(size); }
    }

    /** Resolve registered styles only after mod setup; keep ordering identical on both sides. */
    public static List<Entry> entries() {
        var entries = new ArrayList<Entry>();
        var models = HexieBridges.bogeyRegistry().builtinBogeyModel();
        for (var model : BuiltinBogeyModel.values()) {
            var styleId = models.style(model);
            var style = AllBogeyStyles.BOGEY_STYLES.get(styleId);
            if (style == null) continue;
            var sizeId = models.size(model);
            var size = style.validSizes().stream().filter(candidate -> candidate.id().equals(sizeId))
                    .findFirst().orElse(null);
            if (size == null || !(style.getBlockForSize(size) instanceof CreateBogeyBlockView)) continue;
            String category = switch (styleId.getPath()) {
                case "hxstandardbogey" -> "passenger";
                case "loco" -> "loco";
                case "steambogey" -> "steam";
                case "crhbogey" -> "emu";
                default -> "addon";
            };
            var id = models.id(model);
            entries.add(new Entry(id, category,
                    Component.translatable("simurail_bogey_entry.hexie." + id.getPath()), style, size));
        }
        BogeyRegistry.entries().stream().sorted(Comparator.comparing(handle -> handle.id().toString()))
                .forEach(handle -> {
                    if (handle.block().get() instanceof CreateBogeyBlockView block) {
                        var bogey = (AbstractBogeyBlock<?>) block;
                        entries.add(new Entry(handle.id(), "addon", bogey.getDefaultStyle().displayName,
                                bogey.getDefaultStyle(), bogey.getSize()));
                    }
                });
        return List.copyOf(entries);
    }
}
