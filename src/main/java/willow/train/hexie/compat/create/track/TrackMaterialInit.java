package willow.train.hexie.compat.create.track;

import com.simibubi.create.content.trains.track.TrackMaterial;
import willow.train.hexie.bridge.HexieBridges;

public class TrackMaterialInit {

    public static final double RAIL_SURFACE_HEIGHT = 0.75;
    public static final double RAIL_HEIGHT_INCREASE = 0.4074;

    // All three materials share one Create track type. Bogeys for this type cannot
    // travel on Create's own STANDARD track type.
    public static TrackMaterial.TrackType HEXIE_STANDARD;
    public static TrackMaterial STANDARD;
    public static TrackMaterial BALLASTLESS;
    public static TrackMaterial TIELESS;

    public static TrackMaterial.TrackType hexieStandard() {
        return HEXIE_STANDARD;
    }

    static {
        HEXIE_STANDARD = HexieBridges.createTrain().createRailwayTrackType().standard();
        var backend = HexieBridges.createTrain().createRailwayBackend();
        STANDARD = backend.material(BuiltinRailways.STANDARD_ID, BuiltinRailways.STANDARD);
        BALLASTLESS = backend.material(BuiltinRailways.BALLASTLESS_ID, BuiltinRailways.BALLASTLESS);
        TIELESS = backend.material(BuiltinRailways.TIELESS_ID, BuiltinRailways.TIELESS);
    }

    public static void register() {}
}
