package willow.train.hexie.init.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.simibubi.create.content.trains.track.TrackShape;

import willow.train.hexie.Main;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import javax.annotation.Nullable;

public class PartialModelInit {

    public record ModelTransform(double x, double y, double z, float rx, float ry, float rz) {
        public static final PartialModelInit.ModelTransform ZERO =
                new PartialModelInit.ModelTransform(0, 0, 0, 0, 0, 0);

        public static PartialModelInit.ModelTransform of(
                double x, double y, double z, float rx, float ry, float rz) {
            return new PartialModelInit.ModelTransform(x, y, z, rx, ry, rz);
        }

        public static PartialModelInit.ModelTransform of(double x, double y, double z) {
            return of(x, y, z, 0, 0, 0);
        }

        public static PartialModelInit.ModelTransform of(double x, double z) {
            return of(x, 0, z);
        }
    }

    public static final class TrackCasingSpec {

        public final PartialModel model;

        public final PartialModelInit.ModelTransform transform;
        public final List<PartialModelInit.ModelTransform> additionalTransforms = new ArrayList<>();
        private PartialModelInit.TrackCasingSpec altSpec;
        private final int topSurfacePixelHeight;
        private double xShift = 0;
        private double zShift = 0;

        public TrackCasingSpec(
                PartialModel model,
                PartialModelInit.ModelTransform transform,
                int topSurfacePixelHeight) {
            this.model = model;
            this.transform = transform;
            this.topSurfacePixelHeight = topSurfacePixelHeight;
        }

        public PartialModelInit.TrackCasingSpec addTransform(
                PartialModelInit.ModelTransform transform) {
            additionalTransforms.add(transform);
            return this;
        }

        public PartialModelInit.TrackCasingSpec addTransform(double x, double z) {
            return addTransform(PartialModelInit.ModelTransform.of(x, z));
        }

        public PartialModelInit.TrackCasingSpec withAltSpec(
                @Nullable PartialModelInit.TrackCasingSpec altSpec) {
            this.altSpec = altSpec;
            return this;
        }

        public PartialModelInit.TrackCasingSpec getAltSpec() {
            return altSpec != null ? altSpec : this;
        }

        public int getTopSurfacePixelHeight(boolean alt) {
            return (alt && altSpec != null) ? altSpec.topSurfacePixelHeight : topSurfacePixelHeight;
        }

        public PartialModelInit.TrackCasingSpec overlayShift(double x, double z) {
            this.xShift = x;
            this.zShift = z;
            return this;
        }

        public double getXShift() {
            return xShift;
        }

        public double getZShift() {
            return zShift;
        }
    }

    public static final EnumMap<TrackShape, PartialModelInit.TrackCasingSpec> TRACK_CASINGS =
            new EnumMap<>(TrackShape.class);

    public static final PartialModel
            HX_TRACK_SEGMENT_LEFT = block("track/standard/segment_left"),
            HX_TRACK_SEGMENT_RIGHT = block("track/standard/segment_right"),
            HX_TRACK_TIE = block("track/standard/tie"),
            BALLASTLESS_TRACK_TIE = block("track/ballastless/tie"),
            HXSTANDARDBOGEY_FRAME = block("bogey/hx_standard/bogey_cw2_temple"),
            HXSTANDARDBOGEY_WHEEL = block("bogey/hx_standard/cw2_wheel"),
            DF11G_FRAME = block("bogey/df11g/df11g_bogey_temple"),
            DF11G_WHEEL = block("bogey/df11g/df11g_wheel"),
            PK209P_MAIN = block("bogey/pk209p/pk209p_main"),
            PK209P_WHEEL = block("bogey/pk209p/pk209p_wheel"),
            PK209P_WHEEL2 = block("bogey/pk209p/pk209p_wheel2"),
            PK209P_MOTORWHEEL = block("bogey/pk209p/pk209_motorwheel"),
            PK209P_NO_MOTOR = block("bogey/pk209p/pk209_nomotor"),
            PK209P_MAIN_HL = block("bogey/pk209p/pk209p_main_hl"),
            SW220K = block("bogey/sw220k/sw220k"),
            VACUUM = block("track/standard/vacuum"),
            QJ_MAIN_DRIVE_WHEEL = block("bogey/qj/main_drive_wheel"),
            QJ_DRIVE_WHEEL_2_4 = block("bogey/qj/drive_wheel_2_4"),
            QJ_DRIVE_WHEEL_1_5 = block("bogey/qj/drive_wheel_1_5"),
            QJ_CRANK = block("bogey/qj/crank"),
            QJ_CRANK_OHTERSIDE = block("bogey/qj/crank_otherside"),
            QJ_EXPANSION_LINK = block("bogey/qj/expansion_link"),
            QJ_EXPANSION_LINK_OTHERSIDE = block("bogey/qj/expansion_link_otherside"),
            QJ_CONNECTING_ROD = block("bogey/qj/connecting_rod"),
            QJ_CONNECTING_ROD_OTHERSIDE = block("bogey/qj/connecting_rod_otherside"),
            QJ_ECCENTRIC_ROD = block("bogey/qj/eccentric_rod"),
            QJ_ECCENTRIC_ROD_OTHERSIDE = block("bogey/qj/eccentric_rod_otherside"),
            QJ_CROSSHEAD = block("bogey/qj/crosshead"),
            QJ_CROSSHEAD_OTHERSIDE = block("bogey/qj/crosshead_otherside"),
            QJ_ANCHOR_LINK = block("bogey/qj/anchor_link"),
            QJ_ANCHOR_LINK_OTHERSIDE = block("bogey/qj/anchor_link_otherside"),
            QJ_COMBINATION_LEVER = block("bogey/qj/combination_lever"),
            QJ_COMBINATION_LEVER_OTHERSIDE = block("bogey/qj/combination_lever_otherside"),
            QJ_STEAM_VALVE = block("bogey/qj/steam_valve"),
            QJ_STEAM_VALVE_OTHERSIDE = block("bogey/qj/steam_valve_otherside"),
            QJ_RADIUS_ROD = block("bogey/qj/radius_rod"),
            QJ_RADIUS_ROD_OTHERSIDE = block("bogey/qj/radius_rod_otherside"),
            QJ_OIL_PRESS = block("bogey/qj/oil_press"),
            QJ_OIL_PRESS_OTHERSIDE = block("bogey/qj/oil_press_otherside"),
            QJ_OIL_PRESS_ROD = block("bogey/qj/oil_press_rod"),
            QJ_OIL_PRESS_ROD_OTHERSIDE = block("bogey/qj/oil_press_rod_otherside"),
            QJ_REVERSING = block("bogey/qj/reversing"),
            QJ_REVERSING_BOOM = block("bogey/qj/reversing_boom"),
            QJ_REVERSING_BOOM_OTHERSIDE = block("bogey/qj/reversing_boom_otherside"),
            QJ_BOGEY_FRAME = block("bogey/qj/qj_bogey_frame"),
            SW160_FRAME = block("bogey/sw160/sw160_frame"),
            SW160_WHEEL = block("bogey/sw160/sw160_wheel"),
            CR200J_HEAD_COUPLER_FAIRING_LEFT = block("carriage/carriage_marshalled_cr200j/head/coupler_fairing_lh"),
            CR200J_HEAD_COUPLER_FAIRING_RIGHT = block("carriage/carriage_marshalled_cr200j/head/coupler_fairing"),
            CR200J_HEAD_WIND_SHIELD_1 = block("carriage/carriage_marshalled_cr200j/head/wind_shield_1"),
            CR200J_HEAD_WIND_SHIELD_2 = block("carriage/carriage_marshalled_cr200j/head/wind_shield_2");

    public static final PartialModel
            HX_SIGNAL_WHITE_GLOW = block("hx_semaphore/signal_white_glow");

    private static PartialModel block(String path) {
        return PartialModel.of(Main.asResource("block/" + path));
    }

    @SuppressWarnings("EmptyMethod")
    public static void init() {}
}
