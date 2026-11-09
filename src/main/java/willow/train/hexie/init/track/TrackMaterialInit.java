package willow.train.hexie.init.track;


import com.simibubi.create.content.trains.track.TrackMaterial;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;
import willow.train.hexie.compat.create.track.CreateRailwayTrackType;
import willow.train.hexie.railway.BuiltinRailways;

public class TrackMaterialInit {

    public static final double RAIL_SURFACE_HEIGHT = 0.75;
    public static final double RAIL_HEIGHT_INCREASE = 0.4074;

    // All three materials share one Create track type. Bogeys for this type cannot
    // travel on Create's own STANDARD track type.
    public static final TrackMaterial.TrackType HEXIE_STANDARD =
            CreateRailwayTrackType.STANDARD;

    public static final TrackMaterial STANDARD = CreateRailwayBackend.material(
            BuiltinRailways.STANDARD_ID, BuiltinRailways.STANDARD);
    public static final TrackMaterial BALLASTLESS = CreateRailwayBackend.material(
            BuiltinRailways.BALLASTLESS_ID, BuiltinRailways.BALLASTLESS);
    public static final TrackMaterial TIELESS = CreateRailwayBackend.material(
            BuiltinRailways.TIELESS_ID, BuiltinRailways.TIELESS);

    public static void register() {}
}
