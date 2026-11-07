package willow.train.hexie.vehicle.control;

import net.minecraft.network.chat.Component;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.ControlState;

/** The test panel's momentary buttons, including the readout beside each one. */
public enum TestCabButton implements BlockControl.Button {
    POWER_UP("power_up"), POWER_DOWN("power_down"), BRAKE_UP("brake_up"), BRAKE_DOWN("brake_down"),
    REVERSER("reverser"), MODE("mode"), PARKING("parking");

    private final String id;

    TestCabButton(String id) { this.id = id; }

    @Override public String id() { return id; }
    @Override public Component nameText() { return Component.translatable("gui.hexie.cab." + id); }

    @Override public Component valueText(ControlState state) {
        var view = TestCabControls.view(state);
        return switch (this) {
            case POWER_UP, POWER_DOWN -> Component.translatable("gui.hexie.cab.power_notch", view.power());
            case BRAKE_UP, BRAKE_DOWN -> view.brake() == 11 ? Component.translatable("gui.hexie.cab.emergency")
                    : Component.translatable("gui.hexie.cab.brake_notch", view.brake());
            case REVERSER -> Component.translatable(switch (view.reverser()) {
                case -1 -> "gui.hexie.cab.reverse";
                case 0 -> "gui.hexie.cab.neutral";
                default -> "gui.hexie.cab.forward";
            });
            case MODE -> Component.translatable(view.automatic() ? "gui.hexie.cab.automatic" : "gui.hexie.cab.manual");
            case PARKING -> Component.translatable(view.parking() ? "options.on" : "options.off");
        };
    }

}
