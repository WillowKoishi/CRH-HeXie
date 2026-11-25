package willow.train.hexie.compat.common;

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
import java.util.List;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.bogey.BogeyGeometry;

/** Only loaded from common setup when Simurail is installed. */
public final class HeXieSimurailCompat {
    public static final BogeyEntry DT206 = entry("dt206", HexieBridges.createTrain().bogeyStylesInit().crhbogey(), HexieBridges.createTrain().bogeySizesInit().dt206());
    public static final BogeyEntry SKTB200 = entry("sktb200", HexieBridges.createTrain().bogeyStylesInit().crhbogey(), HexieBridges.createTrain().bogeySizesInit().sktb200());
    public static final BogeyEntry CRH1A_MOTOR = entry("crh1a_motor", HexieBridges.createTrain().bogeyStylesInit().crhbogey(), HexieBridges.createTrain().bogeySizesInit().crh1aMotor());
    public static final BogeyEntry CRH1A_TRAILER = entry("crh1a_trailer", HexieBridges.createTrain().bogeyStylesInit().crhbogey(), HexieBridges.createTrain().bogeySizesInit().crh1aTrailer());
    public static final BogeyEntry CW2 = entry("cw2", HexieBridges.createTrain().bogeyStylesInit().hxstandardbogey(), BogeySizes.SMALL);
    public static final BogeyEntry PK209P = entry("pk209p", HexieBridges.createTrain().bogeyStylesInit().hxstandardbogey(), HexieBridges.createTrain().bogeySizesInit().pk209p());
    public static final BogeyEntry PK209P_NO_MOTOR = entry("pk209p_no_motor", HexieBridges.createTrain().bogeyStylesInit().hxstandardbogey(), HexieBridges.createTrain().bogeySizesInit().pk209pNoMotor());
    public static final BogeyEntry PK209P_BACKWARD = entry("pk209p_backward", HexieBridges.createTrain().bogeyStylesInit().hxstandardbogey(), HexieBridges.createTrain().bogeySizesInit().pk209pBackward());
    public static final BogeyEntry SW220K = entry("sw220k", HexieBridges.createTrain().bogeyStylesInit().hxstandardbogey(), HexieBridges.createTrain().bogeySizesInit().sw220k());
    public static final BogeyEntry SW220K_BACKWARD = entry("sw220k_backward", HexieBridges.createTrain().bogeyStylesInit().hxstandardbogey(), HexieBridges.createTrain().bogeySizesInit().sw220kBackward());
    public static final BogeyEntry SW160 = entry("sw160", HexieBridges.createTrain().bogeyStylesInit().hxstandardbogey(), HexieBridges.createTrain().bogeySizesInit().sw160());
    public static final BogeyEntry DF11G = entry("df11g", HexieBridges.createTrain().bogeyStylesInit().locobogey(), BogeySizes.SMALL);
    public static final BogeyEntry DF11G_BACKWARD = entry("df11g_backward", HexieBridges.createTrain().bogeyStylesInit().locobogey(), HexieBridges.createTrain().bogeySizesInit().backward());
    public static final BogeyEntry SS7D_FRONT = entry("ss7d_front", HexieBridges.createTrain().bogeyStylesInit().locobogey(), HexieBridges.createTrain().bogeySizesInit().ss7dFront());
    public static final BogeyEntry QJ = entry("qj", HexieBridges.createTrain().bogeyStylesInit().steambogey(), HexieBridges.createTrain().bogeySizesInit().qj());
    public static final BogeyEntry QJ_BACKWARD = entry("qj_backward", HexieBridges.createTrain().bogeyStylesInit().steambogey(), HexieBridges.createTrain().bogeySizesInit().qjBackward());

    public static final BogeyEntryCategory PASSENGER = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie.passenger"),
            List.of(CW2, PK209P, PK209P_NO_MOTOR, PK209P_BACKWARD, SW220K, SW220K_BACKWARD, SW160));
    public static final BogeyEntryCategory LOCO = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie.loco"),
            List.of(DF11G, DF11G_BACKWARD, SS7D_FRONT));
    public static final BogeyEntryCategory STEAM = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie.steam"),
            List.of(QJ, QJ_BACKWARD));
    public static final BogeyEntryCategory EMU = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie.emu"), List.of(DT206, SKTB200, CRH1A_MOTOR, CRH1A_TRAILER));
    public static final BogeyParentCategory HEXIE = new BogeyParentCategory(
            Component.translatable("simurail_bogey_category.hexie"),
            List.of(PASSENGER, LOCO, STEAM, EMU));

    private HeXieSimurailCompat() {}

    public static void register() {
        registerTrackType();
        BogeyMenuManager.addBogeyCategory(HEXIE);
        BogeyType.setDefault(HexieBridges.createTrack().hexieStandard(), false, CW2.type());
        configureParameters();
    }

    private static BogeyEntry entry(String id, BogeyStyle style, BogeySizes.BogeySize size) {
        return new BogeyEntry(HexieConstants.asResource(id), new BogeyType(style, size));
    }

    private static void registerTrackType() {
        // Contact centres and rail-head height from HeXie's track models (model units / 16).
        // Keep Simurail's standard speed and adhesion coefficients.
        TrackTypeEntry standard = SimurailTracks.STANDARD;
        TrackTypeEntries.addEntry(new TrackTypeEntry(
                HexieBridges.createTrack().hexieStandard(),
                Component.translatable("simurail_track_type.hexie.standard"),
                Component.translatable("simurail_track_type.hexie.standard.short"),
                standard.allowInverted(),
                24.15 / 16.0,
                TrackRegistryBridge.RAIL_SURFACE_HEIGHT,
                standard.lateralMaxSpeedFactor(),
                standard.verticalMaxSpeedFactor(),
                standard.adhesionFactor(),
                () -> HexieBridges.createTrack().standardTrack().defaultBlockState()));
    }

    /** Nominal rolling radii and axle positions from HeXie's sizes and renderers, in blocks. */
    private static void configureParameters() {
        for (var entry : List.of(DT206, SKTB200, CRH1A_MOTOR, CRH1A_TRAILER, CW2, PK209P, PK209P_NO_MOTOR, PK209P_BACKWARD,
                SW220K, SW220K_BACKWARD, SW160, DF11G, DF11G_BACKWARD, SS7D_FRONT, QJ, QJ_BACKWARD)) {
            var type = entry.type();
            var model = HexieBridges.bogeyRegistry().builtinBogeyModel().find(type.style().id, type.size().id());
            if (model == null) throw new IllegalStateException("Missing built-in bogey geometry: " + type);
            var bogeys = HexieBridges.bogeyRegistry();
            configureBogey(entry, bogeys.builtinBogeyProfile().geometry(bogeys.builtinBogeyModel().profile(model)));
        }
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
