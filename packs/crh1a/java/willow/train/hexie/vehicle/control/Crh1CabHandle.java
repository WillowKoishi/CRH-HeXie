package willow.train.hexie.vehicle.control;

import net.minecraft.network.chat.Component;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.core.api.interaction.ControlState;

/** CRH1A's single controller: braking, zero, speed adjustment and forward-drive hold. */
public enum Crh1CabHandle implements CabControlBridge.Crh1CabHandleView {
    INSTANCE;

    public static final int EMERGENCY = -9, FULL_SERVICE = -8, ZERO = 0, DRIVE = 4;
    @Override public String id() { return "crh1a_master_controller"; }
    @Override public String positionKey() { return "hexie:crh1a_master_controller"; }
    @Override public int minNotch() { return EMERGENCY; }
    @Override public int maxNotch() { return 7; }
    @Override public int notch(ControlState state) {
        int value = state.getOrDefault(positionKey(), ZERO);
        return accepts(value) ? value : ZERO;
    }
    @Override public boolean hasSpringReturn() { return true; }
    @Override public int releasedNotch(int notch) {
        return notch > ZERO && notch != DRIVE ? DRIVE : notch;
    }
    @Override public double sliderPosition(int notch) { return 1 - CabControlBridge.Crh1CabHandleView.super.sliderPosition(notch); }
    @Override public DragSpec dragSpec() { return new DragSpec(DragAxis.VERTICAL, 180); }
    @Override public boolean showTooltip() { return true; }
    @Override public Component nameText() { return Component.translatable("gui.hexie.cab.crh1_controller"); }
    @Override public Component detentText(int notch) {
        if (notch == EMERGENCY) return Component.translatable("gui.hexie.cab.crh1_detent_emergency");
        if (notch == FULL_SERVICE) return Component.translatable("gui.hexie.cab.crh1_detent_full_service");
        if (notch < ZERO) return Component.translatable("gui.hexie.cab.crh1_detent_brake_notch", -notch);
        if (notch == ZERO) return Component.translatable("gui.hexie.cab.crh1_detent_zero");
        if (notch < DRIVE) return Component.translatable("gui.hexie.cab.crh1_detent_decrease", DRIVE - notch);
        if (notch == DRIVE) return Component.translatable("gui.hexie.cab.crh1_detent_drive");
        return Component.translatable("gui.hexie.cab.crh1_detent_increase", notch - DRIVE);
    }
    @Override public Component notchText(int notch) {
        if (notch == EMERGENCY) return Component.translatable("gui.hexie.cab.crh1_emergency");
        if (notch == FULL_SERVICE) return Component.translatable("gui.hexie.cab.crh1_full_service");
        if (notch < ZERO) return Component.translatable("gui.hexie.cab.crh1_brake_notch", -notch);
        if (notch == ZERO) return Component.translatable("gui.hexie.cab.crh1_zero");
        if (notch < DRIVE) return Component.translatable("gui.hexie.cab.crh1_decrease", DRIVE - notch);
        if (notch == DRIVE) return Component.translatable("gui.hexie.cab.crh1_drive");
        return Component.translatable("gui.hexie.cab.crh1_increase", notch - DRIVE);
    }
}
