package willow.train.hexie.compat.create.track;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.minecraft.core.registries.BuiltInRegistries;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.core.HexieConstants;

public final class TrackItems {
    private TrackItems() {}

    static {
        unfinished("standard", "Standard");
        unfinished("ballastless", "Ballastless");
        unfinished("tieless", "Tieless");
    }

    private static void unfinished(String trackId, String displayName) {
        var legacy = HexieConstants.asResource("track_incomplete_" + trackId);
        var id = legacy.withPrefix("create_");
        CreateBootstrap.registrate()
                .item(id.getPath(), SequencedAssemblyItem::new)
                .lang("Create Incomplete " + displayName + " Track")
                .model((context, provider) -> provider.generated(
                        context, HexieConstants.asResource("item/track/incomplete_standard_track")))
                .onRegister(item -> BuiltInRegistries.ITEM.addAlias(legacy, id))
                .register();
    }

    public static void register() {}
}
