package willow.train.hexie.init.track;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import willow.train.hexie.Main;

public final class TrackItems {
    private static final ItemEntry<SequencedAssemblyItem> STANDARD =
            unfinished("standard", "Standard");
    private static final ItemEntry<SequencedAssemblyItem> BALLASTLESS =
            unfinished("ballastless", "Ballastless");
    private static final ItemEntry<SequencedAssemblyItem> TIELESS =
            unfinished("tieless", "Tieless");

    private TrackItems() {}

    private static ItemEntry<SequencedAssemblyItem> unfinished(String trackId, String displayName) {
        return Main.registrate()
                .item("track_incomplete_" + trackId, SequencedAssemblyItem::new)
                .lang("Incomplete " + displayName + " Track")
                .model((context, provider) -> provider.generated(
                        context, Main.asResource("item/track/incomplete_standard_track")))
                .register();
    }

    public static void register() {}
}
