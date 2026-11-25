package willow.train.hexie.render.init;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockType;

import static willow.train.hexie.render.init.PartialModelInit.CRH2_CAB_DOOR_LEFT_BODY;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_CAB_DOOR_LEFT_GLASS;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_CAB_DOOR_RIGHT_BODY;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_CAB_DOOR_RIGHT_GLASS;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_PASSENGER_DOOR_FRAME;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_PASSENGER_DOOR_FRAME_LEFT;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_PASSENGER_DOOR_FRAME_RIGHT;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_PASSENGER_DOOR_LEFT_BODY;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_PASSENGER_DOOR_LEFT_GLASS;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_PASSENGER_DOOR_RIGHT_BODY;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_PASSENGER_DOOR_RIGHT_GLASS;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_WIDE_PASSENGER_DOOR_FRAME_LEFT;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_WIDE_PASSENGER_DOOR_FRAME_RIGHT;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_WIDE_PASSENGER_DOOR_LEFT_BODY;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_WIDE_PASSENGER_DOOR_LEFT_GLASS;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_WIDE_PASSENGER_DOOR_RIGHT_BODY;
import static willow.train.hexie.render.init.PartialModelInit.CRH2_WIDE_PASSENGER_DOOR_RIGHT_GLASS;

// split from willow/train/hexie/init/client/PartialModelInit.java, lines 136-154
public final class CRH2DoorModelSelection {
    public static PartialModel crh2PassengerDoorFrame(CRH2DoorBlockType type, boolean framed, boolean mirrored) {
        if (type == CRH2DoorBlockType.WIDE_PASSENGER)
            return mirrored ? CRH2_WIDE_PASSENGER_DOOR_FRAME_RIGHT : CRH2_WIDE_PASSENGER_DOOR_FRAME_LEFT;
        if (!framed) return CRH2_PASSENGER_DOOR_FRAME;
        return mirrored ? CRH2_PASSENGER_DOOR_FRAME_RIGHT : CRH2_PASSENGER_DOOR_FRAME_LEFT;
    }

    public static PartialModel crh2DoorLayer(CRH2DoorBlockType type, boolean mirrored, boolean glass) {
        if (type == CRH2DoorBlockType.WIDE_PASSENGER) {
            if (glass) return mirrored ? CRH2_WIDE_PASSENGER_DOOR_RIGHT_GLASS : CRH2_WIDE_PASSENGER_DOOR_LEFT_GLASS;
            return mirrored ? CRH2_WIDE_PASSENGER_DOOR_RIGHT_BODY : CRH2_WIDE_PASSENGER_DOOR_LEFT_BODY;
        }
        if (type == CRH2DoorBlockType.PASSENGER) {
            if (glass) return mirrored ? CRH2_PASSENGER_DOOR_RIGHT_GLASS : CRH2_PASSENGER_DOOR_LEFT_GLASS;
            return mirrored ? CRH2_PASSENGER_DOOR_RIGHT_BODY : CRH2_PASSENGER_DOOR_LEFT_BODY;
        }
        if (glass) return mirrored ? CRH2_CAB_DOOR_RIGHT_GLASS : CRH2_CAB_DOOR_LEFT_GLASS;
        return mirrored ? CRH2_CAB_DOOR_RIGHT_BODY : CRH2_CAB_DOOR_LEFT_BODY;
    }
}
