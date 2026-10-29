package willow.train.hexie.compat;

import com.crystaelix.simurail.api.bogey.BogeyPropertyOverrides;
import com.crystaelix.simurail.api.bogey.BogeyType;
import com.crystaelix.simurail.api.bogey.menu.BogeyEntry;
import com.crystaelix.simurail.api.bogey.menu.BogeyEntryCategory;
import com.crystaelix.simurail.api.bogey.menu.BogeyMenuManager;
import com.crystaelix.simurail.api.bogey.menu.BogeyParentCategory;
import com.crystaelix.simurail.api.track.TrackTypeEntries;
import com.crystaelix.simurail.api.track.TrackTypeEntry;
import com.crystaelix.simurail.content.SimurailTracks;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.network.chat.Component;
import willow.train.hexie.Main;
import willow.train.hexie.init.bogeys.BogeySizesInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.init.track.TrackMaterialInit;

import java.util.List;
import willow.train.hexie.vehicle.bogey.BogeyGeometry;
import willow.train.hexie.vehicle.bogey.HexieBogeyGeometries;

/** Only loaded from common setup when Simurail is installed. */
public final class HeXieSimurailCompat {
    public static final BogeyEntry DT206 = entry("dt206", BogeyStylesInit.CRHBOGEY, BogeySizesInit.DT206);
    public static final BogeyEntry CW2 = entry("cw2", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizes.SMALL);
    public static final BogeyEntry PK209P = entry("pk209p", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.PK209P);
    public static final BogeyEntry PK209P_NO_MOTOR = entry("pk209p_no_motor", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.PK209P_NO_MOTOR);
    public static final BogeyEntry PK209P_BACKWARD = entry("pk209p_backward", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.PK209P_BACKWARD);
    public static final BogeyEntry SW220K = entry("sw220k", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.SW220K);
    public static final BogeyEntry SW220K_BACKWARD = entry("sw220k_backward", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.SW220K_BACKWARD);
    public static final BogeyEntry SW160 = entry("sw160", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.SW160);
    public static final BogeyEntry DF11G = entry("df11g", BogeyStylesInit.DF11GBOGEY, BogeySizes.SMALL);
    public static final BogeyEntry DF11G_BACKWARD = entry("df11g_backward", BogeyStylesInit.DF11GBOGEY, BogeySizesInit.BACKWARD);
    public static final BogeyEntry QJ = entry("qj", BogeyStylesInit.DF11GBOGEY, BogeySizesInit.QJ);
    public static final BogeyEntry QJ_BACKWARD = entry("qj_backward", BogeyStylesInit.DF11GBOGEY, BogeySizesInit.QJ_BACKWARD);

    public static final BogeyEntryCategory PASSENGER = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie.passenger"),
            List.of(CW2, PK209P, PK209P_NO_MOTOR, PK209P_BACKWARD, SW220K, SW220K_BACKWARD, SW160));
    public static final BogeyEntryCategory LOCOMOTIVE = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie.locomotive"),
            List.of(DF11G, DF11G_BACKWARD, QJ, QJ_BACKWARD));
    public static final BogeyEntryCategory EMU = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie.emu"), List.of(DT206));
    public static final BogeyParentCategory HEXIE = new BogeyParentCategory(
            Component.translatable("simurail_bogey_category.hexie"),
            List.of(PASSENGER, LOCOMOTIVE, EMU));

    private HeXieSimurailCompat() {}

    public static void register() {
        registerTrackType();
        BogeyMenuManager.addBogeyCategory(HEXIE);
        BogeyType.setDefault(TrackMaterialInit.HEXIE_STANDARD, false, CW2.type());
        configureParameters();
    }

    private static BogeyEntry entry(String id, BogeyStyle style, BogeySizes.BogeySize size) {
        return new BogeyEntry(Main.asResource(id), new BogeyType(style, size));
    }

    private static void registerTrackType() {
        // Contact centres and rail-head height from HeXie's track models (model units / 16).
        // Keep Simurail's standard speed and adhesion coefficients.
        TrackTypeEntry standard = SimurailTracks.STANDARD;
        TrackTypeEntries.addEntry(new TrackTypeEntry(
                TrackMaterialInit.HEXIE_STANDARD,
                Component.translatable("simurail_track_type.hexie.standard"),
                Component.translatable("simurail_track_type.hexie.standard.short"),
                standard.allowInverted(),
                24.15 / 16.0,
                TrackMaterialInit.RAIL_SURFACE_HEIGHT,
                standard.lateralMaxSpeedFactor(),
                standard.verticalMaxSpeedFactor(),
                standard.adhesionFactor(),
                TrackBlockInit.STANDARD_TRACK::getDefaultState));
    }

    /** Nominal rolling radii and axle positions from HeXie's sizes and renderers, in blocks. */
    private static void configureParameters() {
        // CW2 shares the passenger wheel dimensions; forward/backward DF11G share one wheel model.
        configureBogey(DT206, HexieBogeyGeometries.DT206);
        configureBogey(CW2, HexieBogeyGeometries.CW2);
        configureBogey(PK209P, HexieBogeyGeometries.PK209P);
        configureBogey(PK209P_NO_MOTOR, HexieBogeyGeometries.PK209P);
        configureBogey(PK209P_BACKWARD, HexieBogeyGeometries.PK209P);
        configureBogey(SW220K, HexieBogeyGeometries.SW220K);
        configureBogey(SW220K_BACKWARD, HexieBogeyGeometries.SW220K);
        configureBogey(SW160, HexieBogeyGeometries.SW160);
        configureBogey(DF11G, HexieBogeyGeometries.DF11G);
        configureBogey(DF11G_BACKWARD, HexieBogeyGeometries.DF11G);
        configureBogey(QJ, HexieBogeyGeometries.QJ);
        configureBogey(QJ_BACKWARD, HexieBogeyGeometries.QJ);
    }

    private static void configureBogey(BogeyEntry entry, BogeyGeometry geometry) {
        BogeyType type = entry.type();
        // Simurail uses the OUTERMOST axle span, rounding it to an integer for its two logical axles.
        BogeyPropertyOverrides.setAxleSpacingOverride(type, geometry.axleSpan());
        BogeyPropertyOverrides.setAxleCountOverride(type, geometry.axlePositions().size());
        BogeyPropertyOverrides.setAxlePositionsOverride(type, geometry.axlePositionArray());
        BogeyPropertyOverrides.setWheelRadiusOverride(type, geometry.wheelRadius());
        // Width/height come from HEXIE_STANDARD. Retain the block's connector offset: Simurail
        // converts Create's (0, 7/32, 1) into its own coordinates (1, -9/32, 0) automatically.
    }
}
