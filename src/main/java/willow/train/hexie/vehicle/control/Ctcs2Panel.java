package willow.train.hexie.vehicle.control;

import java.util.List;
import net.minecraft.network.chat.Component;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.api.traincontrol.CabTrainRequest;

/** The controls of the CTCS2-200H panel, in the shell's local coordinates. */
public enum Ctcs2Panel implements ControlSurface.Module, CabTrainControl {
    INSTANCE;

    /** LCD soft keys, top to bottom. The display can show legends before a function is connected. */
    public static final List<String> SOFT_KEY_LABELS = List.of("数据", "模式", "载频", "等级", "其它", "启动", "缓解", "警惕");
    private static final String AUTOMATIC = "hexie:ctcs2_automatic";
    private static final List<ControlSurface.Part> PARTS = List.of(
            ControlSurface.button(Button.DISPLAY_SWITCH, -.141, -.125, .099, .115, .006));

    public enum Button implements BlockControl.Button {
        DISPLAY_SWITCH;

        @Override public String id() { return "ctcs2_display_switch"; }
        @Override public boolean showTooltip() { return true; }
        @Override public Component nameText() { return Component.translatable("gui.hexie.cab.mode"); }
        @Override public Component valueText(ControlState state) {
            return Component.translatable(state.getOrDefault(AUTOMATIC, 0) == 1
                    ? "gui.hexie.cab.automatic" : "gui.hexie.cab.manual");
        }
    }

    @Override public List<ControlSurface.Part> parts() { return PARTS; }

    @Override public ControlState restore(ControlState saved, CabTrainRequest current) {
        return saved.with(AUTOMATIC, current.automatic() ? 1 : 0);
    }

    @Override public Result operate(BlockControl control, int value, ControlState before, ControlState after,
                                    CabTrainRequest current) {
        if (control != Button.DISPLAY_SWITCH) throw new IllegalArgumentException("Unknown CTCS2 panel control");
        var next = current.withAutomatic(!current.automatic());
        return new Result(restore(after, next), next);
    }
}
