package willow.train.hexie.init.track;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;

import willow.train.hexie.Main;

import java.util.HashMap;
import java.util.Map;

public class TrackItems {

    private static final CreateRegistrate REGISTRATE = Main.registrate();

    public static final Map<TrackMaterial, ItemEntry<SequencedAssemblyItem>> ITEM_INCOMPLETE_TRACK =
            new HashMap<>();

    static {
        for (TrackMaterial material : TrackMaterial.allFromMod(Main.MOD_ID)) {
            ITEM_INCOMPLETE_TRACK.put(
                    material,
                    REGISTRATE
                            .item(
                                    "track_incomplete_" + material.resourceName(),
                                    SequencedAssemblyItem::new)
                            .model(
                                    (c, p) ->
                                            p.generated(
                                                    c,
                                                    Main.asResource(
                                                            "item/track/incomplete_standard_track")))
                            .lang("Incomplete " + material.langName + " Track")
                            .register());
        }
    }

    public static void register() {}
}
