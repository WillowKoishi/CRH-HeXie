package willow.train.hexie.vehicle.control;

import java.util.List;
import net.minecraft.network.chat.Component;
import willow.train.hexie.api.cab.CabControl;
import willow.train.hexie.api.cab.CabSurfaceGeometry;
import willow.train.hexie.api.cab.CabSurfaceState;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.api.traincontrol.CabTrainRequest;

/** The controls of the CTCS2-200H panel, in the shell's local coordinates. */
public enum Ctcs2Panel implements CabSurfaceGeometry.Module, CabTrainControl {
    INSTANCE;

    /** LCD soft keys, top to bottom. The display can show legends before a function is connected. */
    public static final List<String> SOFT_KEY_LABELS = List.of("数据", "模式", "载频", "等级", "其它", "启动", "缓解", "警惕");
    private static final String AUTOMATIC = "hexie:ctcs2_automatic";
    private static final List<CabSurfaceGeometry.Part> PARTS = List.of(
            CabSurfaceGeometry.button(Button.DISPLAY_SWITCH, -.141, -.125, .099, .115, .006));

    public enum Button implements CabControl.Button {
        DISPLAY_SWITCH;

        @Override public String id() { return "ctcs2_display_switch"; }
        @Override public Component nameText() { return Component.translatable("gui.hexie.cab.mode"); }
        @Override public Component valueText(CabSurfaceState state) {
            return Component.translatable(state.getOrDefault(AUTOMATIC, 0) == 1
                    ? "gui.hexie.cab.automatic" : "gui.hexie.cab.manual");
        }
    }

    @Override public List<CabSurfaceGeometry.Part> parts() { return PARTS; }

    @Override public CabSurfaceState restore(CabSurfaceState saved, CabTrainRequest current) {
        return saved.with(AUTOMATIC, current.automatic() ? 1 : 0);
    }

    @Override public Result operate(CabControl control, int value, CabSurfaceState before, CabSurfaceState after,
                                    CabTrainRequest current) {
        if (control != Button.DISPLAY_SWITCH) throw new IllegalArgumentException("Unknown CTCS2 panel control");
        var next = current.withAutomatic(!current.automatic());
        return new Result(restore(after, next), next);
    }
}
