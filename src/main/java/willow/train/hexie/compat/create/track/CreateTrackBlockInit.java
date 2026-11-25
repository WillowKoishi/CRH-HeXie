package willow.train.hexie.compat.create.track;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.core.api.railway.RailwayDefinition;
import willow.train.hexie.core.api.railway.RailwayRegistry;

public final class CreateTrackBlockInit {
    private CreateTrackBlockInit() {}

    static {
        CreateBootstrap.registrate().setCreativeTab(HexieBridges.modBootstrap().hexieMain().holder());
    }

    static final BlockEntry<TrackBlock> STANDARD_TRACK = makeTrack(BuiltinRailways.STANDARD_ID, BuiltinRailways.STANDARD);
    static final BlockEntry<TrackBlock> BALLASTLESS_TRACK = makeTrack(BuiltinRailways.BALLASTLESS_ID, BuiltinRailways.BALLASTLESS);
    static final BlockEntry<TrackBlock> TIELESS_TRACK = makeTrack(BuiltinRailways.TIELESS_ID, BuiltinRailways.TIELESS);

    private static BlockEntry<TrackBlock> makeTrack(ResourceLocation id, RailwayDefinition definition) {
        var handle = RailwayRegistry.register(HexieBridges.modBootstrap().modBus(), id, definition);
        return HexieBridges.createTrain().createRailwayBackend().blockEntry(handle);
    }

    public static void register() {} // Initializing the holder declares all three tracks together.

    public static Block standardTrack() { return STANDARD_TRACK.get(); }
}
