package willow.train.hexie.render.init;

// split from willow/train/hexie/init/client/PartialModelInit.java

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.render.bogey.BogeySuspensionModels;
import willow.train.hexie.render.door.CRH1DoorRenderer;

public class PartialModelInit {

    // CRH2 doors
    public static final PartialModel
            CRH2_PASSENGER_DOOR_FRAME = block("crh2a/doors/crh2a_passenger_door_frame"),
            CRH2_PASSENGER_DOOR_FRAME_LEFT = block("crh2a/doors/crh2a_psg_door_frame_left"),
            CRH2_PASSENGER_DOOR_FRAME_RIGHT = block("crh2a/doors/crh2a_psg_door_frame_right"),
            CRH2_PASSENGER_DOOR_LEFT_BODY = block("crh2a/doors/crh2a_passenger_door_left_body"),
            CRH2_PASSENGER_DOOR_RIGHT_BODY = block("crh2a/doors/crh2a_passenger_door_right_body"),
            CRH2_PASSENGER_DOOR_LEFT_GLASS = block("crh2a/doors/crh2a_passenger_door_left_glass"),
            CRH2_PASSENGER_DOOR_RIGHT_GLASS = block("crh2a/doors/crh2a_passenger_door_right_glass"),
            CRH2_WIDE_PASSENGER_DOOR_LEFT_BODY = block("crh2a/doors/crh2a_passenger_door_wide_left_body"),
            CRH2_WIDE_PASSENGER_DOOR_RIGHT_BODY = block("crh2a/doors/crh2a_passenger_door_wide_right_body"),
            CRH2_WIDE_PASSENGER_DOOR_LEFT_GLASS = block("crh2a/doors/crh2a_passenger_door_wide_left_glass"),
            CRH2_WIDE_PASSENGER_DOOR_RIGHT_GLASS = block("crh2a/doors/crh2a_passenger_door_wide_right_glass"),
            CRH2_WIDE_PASSENGER_DOOR_FRAME_LEFT = block("crh2a/doors/crh2a_passenger_door_wide_frame_left"),
            CRH2_WIDE_PASSENGER_DOOR_FRAME_RIGHT = block("crh2a/doors/crh2a_passenger_door_wide_frame_right"),
            CRH2_CAB_DOOR_LEFT_BODY = block("crh2a/doors/crh2a_cab_door_left_body"),
            CRH2_CAB_DOOR_RIGHT_BODY = block("crh2a/doors/crh2a_cab_door_right_body"),
            CRH2_CAB_DOOR_LEFT_GLASS = block("crh2a/doors/crh2a_cab_door_left_glass"),
            CRH2_CAB_DOOR_RIGHT_GLASS = block("crh2a/doors/crh2a_cab_door_right_glass"),
            // End-face and head couplers
            CRH2_END_COUPLER = block("crh2a/endface/coupler"),
            CRH1_END_COUPLER = block("crh1a/endface/coupler"),
            CRH1_END_BELLOWS_LEFT = block("crh1a/endface/bellows_left"),
            CRH1_END_BELLOWS_RIGHT = block("crh1a/endface/bellows_right"),
            CRH1_END_BELLOWS_TOP = block("crh1a/endface/bellows_top"),
            CRH1_END_BELLOWS_BOTTOM = block("crh1a/endface/bellows_bottom"),
            CRH1_HEAD_COVER_UPPER = block("crh1a/head/cover_upper"),
            CRH1_HEAD_COVER_LOWER = block("crh1a/head/cover_lower"),
            CRH1_HEAD_COUPLER_REAR = block("crh1a/head/coupler_rear"),
            CRH1_HEAD_COUPLER_FRONT = block("crh1a/head/coupler_front"),
            SCHARFENBERG_COUPLER_ARM = block("crh1a/head/standalone_arm"),
            // Janney and rescue couplers
            JANNEY_BASE = block("coupler/general_janney/base"),
            JANNEY_BODY = block("coupler/general_janney/body"),
            JANNEY_KNUCKLE = block("coupler/general_janney/knuckle"),
            JANNEY_BODY_PIN = block("coupler/general_janney/body_pin"),
            JANNEY_KNUCKLE_PIN = block("coupler/general_janney/knuckle_pin"),
            RESCUE_COUPLER_BODY = block("coupler/crh2a_rescue/body"),
            RESCUE_COUPLER_KNUCKLE = block("coupler/crh2a_rescue/knuckle"),
            // CRH2 end-face cables, automatic coupler and bellows
            CRH2_END_CABLE_TILTED = block("crh2a/endface/cable_tilted"),
            CRH2_END_CABLE_HORIZONTAL = block("crh2a/endface/cable_horizontal"),
            CRH2_AUTO_COUPLER_BASE = block("crh2a/auto_coupler/base"),
            CRH2_AUTO_COUPLER_ARM = block("crh2a/auto_coupler/arm"),
            CRH2_AUTO_COUPLER_COVER_LEFT = block("crh2a/auto_coupler/cover_left"),
            CRH2_AUTO_COUPLER_COVER_RIGHT = block("crh2a/auto_coupler/cover_right"),
            CRH2_END_BELLOWS_LEFT = block("crh2a/endface/bellows_left"),
            CRH2_END_BELLOWS_RIGHT = block("crh2a/endface/bellows_right"),
            CRH2_END_BELLOWS_TOP = block("crh2a/endface/bellows_top"),
            CRH2_END_BELLOWS_BOTTOM = block("crh2a/endface/bellows_bottom"),
            // Track
            HX_TRACK_SEGMENT_LEFT = block("track/standard/segment_left"),
            HX_TRACK_SEGMENT_RIGHT = block("track/standard/segment_right"),
            HX_TRACK_TIE = block("track/standard/tie"),
            BALLASTLESS_TRACK_TIE = block("track/ballastless/tie"),
            // Bogey frames and wheels
            DT206_FRAME = block("bogey/dt206/frame"),
            SKTB200_FRAME = block("bogey/sktb200/frame"),
            DT206_WHEEL = block("bogey/dt206/wheel"),
            SKTB200_WHEEL = block("bogey/sktb200/wheel"),
            CRH1A_MOTOR_FRAME = block("bogey/crh1a_motor/frame"),
            CRH1A_MOTOR_WHEEL = block("bogey/crh1a_motor/wheel"),
            CRH1A_TRAILER_FRAME = block("bogey/crh1a_trailer/frame"),
            CRH1A_TRAILER_WHEEL = block("bogey/crh1a_trailer/wheel"),
            HXSTANDARDBOGEY_FRAME = block("bogey/hx_standard/bogey_cw2_temple"),
            HXSTANDARDBOGEY_WHEEL = block("bogey/hx_standard/cw2_wheel"),
            DF11G_FRAME = block("bogey/df11g/df11g_bogey_temple"),
            DF11G_WHEEL = block("bogey/df11g/df11g_wheel"),
            // SS7D front bogey
            SS7D_FRONT_FRAME = block("bogey/ss7d_front/frame"),
            SS7D_FRONT_WHEEL_FRONT = block("bogey/ss7d_front/wheel_front"),
            SS7D_FRONT_WHEEL_REAR = block("bogey/ss7d_front/wheel_rear"),
            SS7D_FRONT_TRACTION_LEFT = block("bogey/ss7d_front/traction_left"),
            SS7D_FRONT_TRACTION_RIGHT = block("bogey/ss7d_front/traction_right"),
            // SS7D head
            SS7D_HEAD_WIPER_LEFT = block("locos/ss7d/head/wiper_left"),
            SS7D_HEAD_WIPER_RIGHT = block("locos/ss7d/head/wiper_right"),
            SS7D_HEAD_SIDE_PLATES = block("locos/ss7d/head/side_plates"),
            SS7D_HEAD_HOOK = block("locos/ss7d/head/hook"),
            SS7D_HEAD_KNUCKLE = block("locos/ss7d/head/knuckle"),
            // Passenger bogeys
            PK209P_MAIN = block("bogey/pk209p/pk209p_main"),
            PK209P_WHEEL = block("bogey/pk209p/pk209p_wheel"),
            PK209P_WHEEL2 = block("bogey/pk209p/pk209p_wheel2"),
            PK209P_MOTORWHEEL = block("bogey/pk209p/pk209_motorwheel"),
            PK209P_NO_MOTOR = block("bogey/pk209p/pk209_nomotor"),
            PK209P_MAIN_HL = block("bogey/pk209p/pk209p_main_hl"),
            SW220K = block("bogey/sw220k/sw220k"),
            // Track vacuum
            VACUUM = block("track/standard/vacuum"),
            // QJ running gear
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
            // SW160 bogey
            SW160_FRAME = block("bogey/sw160/sw160_frame"),
            SW160_WHEEL = block("bogey/sw160/sw160_wheel"),
            // CR200J front
            CR200J_HEAD_COUPLER_FAIRING_LEFT = block("carriage/carriage_marshalled_cr200j/head/coupler_fairing_lh"),
            CR200J_HEAD_COUPLER_FAIRING_RIGHT = block("carriage/carriage_marshalled_cr200j/head/coupler_fairing"),
            CR200J_HEAD_WIND_SHIELD_1 = block("carriage/carriage_marshalled_cr200j/head/wind_shield_1"),
            CR200J_HEAD_WIND_SHIELD_2 = block("carriage/carriage_marshalled_cr200j/head/wind_shield_2");

    // Semaphore
    public static final PartialModel
            HX_SIGNAL_HOUSING = block("hx_semaphore/hx_shunting_signal"),
            HX_SIGNAL_WHITE_GLOW = block("hx_semaphore/signal_white_glow");

    private static PartialModel block(String path) {
        return PartialModel.of(HexieConstants.asResource("block/" + path));
    }

    public static void init() {
        CRH1DoorRenderer.init();
        BogeySuspensionModels.init();
    }
}
