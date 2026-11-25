package willow.train.hexie.compat.create.track;

import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.resources.ResourceLocation;

/** Kept separate from built-in material initialisation so add-on construction order is irrelevant. */
public final class CreateRailwayTrackType {
    public static final TrackMaterial.TrackType STANDARD = new TrackMaterial.TrackType(
            ResourceLocation.fromNamespaceAndPath("hexie", "standard"), StandardTrackBlock::new);
    private CreateRailwayTrackType() {}
}
