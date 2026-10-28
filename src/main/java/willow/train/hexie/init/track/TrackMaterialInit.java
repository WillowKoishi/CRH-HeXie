package willow.train.hexie.init.track;

import static com.simibubi.create.content.trains.track.TrackMaterialFactory.make;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.level.block.Blocks;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.StandardTrackBlock;

public class TrackMaterialInit {

    public static final double RAIL_SURFACE_HEIGHT = 0.75;
    public static final double RAIL_HEIGHT_INCREASE = 0.4074;

    // All three materials share one Create track type. Bogeys for this type cannot
    // travel on Create's own STANDARD track type.
    public static final TrackMaterial.TrackType HEXIE_STANDARD =
            new TrackMaterial.TrackType(Main.asResource("standard"), StandardTrackBlock::new);

    // Create places curved rail segments at 0.965 blocks. Both segment OBJ models
    // retain their previous alignment compensation, plus RAIL_HEIGHT_INCREASE in the OBJ vertices.
    public static final TrackMaterial STANDARD = make(Main.asResource("standard"))
            .lang("Standard")
            .block(() -> TrackBlockInit.STANDARD_TRACK)
            .particle(Create.asResource("block/palettes/stone_types/polished/andesite_cut_polished"))
            .trackType(HEXIE_STANDARD)
            .sleeper(Blocks.ANDESITE_SLAB)
            .standardModels()
            .build();

    public static final TrackMaterial BALLASTLESS = make(Main.asResource("ballastless"))
            .lang("Ballastless")
            .block(() -> TrackBlockInit.BALLASTLESS_TRACK)
            .particle(Create.asResource("block/palettes/stone_types/polished/andesite_cut_polished"))
            .trackType(HEXIE_STANDARD)
            .sleeper(Blocks.ANDESITE_SLAB)
            .customModels(
                    () -> () -> PartialModel.of(Main.asResource("block/track/ballastless/tie")),
                    () -> () -> PartialModel.of(Main.asResource("block/track/standard/segment_left")),
                    () -> () -> PartialModel.of(Main.asResource("block/track/standard/segment_right")))
            .build();

    public static final TrackMaterial TIELESS = make(Main.asResource("tieless"))
            .lang("Tieless")
            .block(() -> TrackBlockInit.TIELESS_TRACK)
            .particle(Create.asResource("block/palettes/stone_types/polished/andesite_cut_polished"))
            .trackType(HEXIE_STANDARD)
            .sleeper(Blocks.ANDESITE_SLAB)
            .customModels(
                    () -> () -> PartialModel.of(Main.asResource("block/track/standard/vacuum")),
                    () -> () -> PartialModel.of(Main.asResource("block/track/standard/segment_left")),
                    () -> () -> PartialModel.of(Main.asResource("block/track/standard/segment_right")))
            .build();

    public static void register() {}
}
