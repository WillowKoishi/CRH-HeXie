package willow.train.hexie.compat.bridge;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeySizesInitOperations;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeyStylesInitOperations;
import willow.train.hexie.compat.create.bogey.BogeySizesInit;
import willow.train.hexie.compat.create.bogey.BogeyStylesInit;

final class CreateBogeyRegistryPort implements BogeySizesInitOperations, BogeyStylesInitOperations {
    static final CreateBogeyRegistryPort INSTANCE = new CreateBogeyRegistryPort();

    private CreateBogeyRegistryPort() {}

    @Override public BogeySizes.BogeySize backward() { return BogeySizesInit.BACKWARD; }
    @Override public BogeySizes.BogeySize ss7dFront() { return BogeySizesInit.SS7D_FRONT; }
    @Override public BogeySizes.BogeySize pk209p() { return BogeySizesInit.PK209P; }
    @Override public BogeySizes.BogeySize pk209pNoMotor() { return BogeySizesInit.PK209P_NO_MOTOR; }
    @Override public BogeySizes.BogeySize pk209pBackward() { return BogeySizesInit.PK209P_BACKWARD; }
    @Override public BogeySizes.BogeySize sw220k() { return BogeySizesInit.SW220K; }
    @Override public BogeySizes.BogeySize sw220kBackward() { return BogeySizesInit.SW220K_BACKWARD; }
    @Override public BogeySizes.BogeySize qj() { return BogeySizesInit.QJ; }
    @Override public BogeySizes.BogeySize qjBackward() { return BogeySizesInit.QJ_BACKWARD; }
    @Override public BogeySizes.BogeySize sw160() { return BogeySizesInit.SW160; }
    @Override public BogeySizes.BogeySize dt206() { return BogeySizesInit.DT206; }
    @Override public BogeySizes.BogeySize sktb200() { return BogeySizesInit.SKTB200; }
    @Override public BogeySizes.BogeySize crh1aMotor() { return BogeySizesInit.CRH1A_MOTOR; }
    @Override public BogeySizes.BogeySize crh1aTrailer() { return BogeySizesInit.CRH1A_TRAILER; }
    @Override public BogeyStyle hxstandardbogey() { return BogeyStylesInit.HXSTANDARDBOGEY; }
    @Override public BogeyStyle locobogey() { return BogeyStylesInit.LOCOBOGEY; }
    @Override public BogeyStyle steambogey() { return BogeyStylesInit.STEAMBOGEY; }
    @Override public BogeyStyle crhbogey() { return BogeyStylesInit.CRHBOGEY; }
}
