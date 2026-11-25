package willow.train.hexie.render.init;

import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import willow.train.hexie.core.api.railway.RailwayRegistry;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.render.track.StandardTrackRenderProperties;

// split from willow/train/hexie/init/client/HeXieClientInit.java, lines 56-60
final class TrackClientExtensions {
    public static void registerBlockExtensions(RegisterClientExtensionsEvent event) {
        // Register directly: the track builders are declared before Registrate is attached to the mod bus.
        event.registerBlock(new StandardTrackRenderProperties(),
                RailwayRegistry.entries().stream().map(railway -> railway.block().get()).toArray(Block[]::new));
    }
}
