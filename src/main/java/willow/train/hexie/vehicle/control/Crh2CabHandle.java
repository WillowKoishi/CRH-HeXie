package willow.train.hexie.vehicle.control;

import net.minecraft.network.chat.Component;
import willow.train.hexie.api.interaction.BlockControl;

/** Physical detents, slider travel and labels of the non-standard CRH2A desk. */
public enum Crh2CabHandle implements BlockControl.Handle {
    POWER("power", 0, 10),
    BRAKE("brake", 0, 9),
    REVERSER("reverser", -1, 1);

    static final int SERVICE_NOTCHES = 7;
    static final int RAPID = 8;
    private final String id;
    private final String positionKey;
    private final int minNotch;
    private final int maxNotch;

    Crh2CabHandle(String id, int minNotch, int maxNotch) {
        this.id = id;
        positionKey = "hexie:crh2a_" + id;
        this.minNotch = minNotch;
        this.maxNotch = maxNotch;
    }

    @Override public String id() { return id; }
    @Override public String positionKey() { return positionKey; }
    @Override public int minNotch() { return minNotch; }
    @Override public int maxNotch() { return maxNotch; }
    @Override public Component nameText() { return Component.translatable("gui.hexie.cab." + id); }

    @Override public Component notchText(int notch) {
        return switch (this) {
            case POWER -> notch == 0 ? Component.translatable("gui.hexie.cab.cut")
                    : Component.translatable("gui.hexie.cab.power_notch", notch);
            case BRAKE -> switch (notch) {
                case 0 -> Component.translatable("gui.hexie.cab.running");
                case RAPID -> Component.translatable("gui.hexie.cab.rapid");
                case 9 -> Component.translatable("gui.hexie.cab.remove");
                default -> Component.translatable("gui.hexie.cab.brake_notch", notch);
            };
            case REVERSER -> Component.translatable(switch (notch) {
                case -1 -> "gui.hexie.cab.reverse";
                case 0 -> "gui.hexie.cab.off";
                default -> "gui.hexie.cab.forward";
            });
        };
    }

    @Override public double sliderPosition(int notch) {
        return switch (this) {
            case POWER -> BlockControl.Handle.super.sliderPosition(notch);
            case REVERSER -> 1 - BlockControl.Handle.super.sliderPosition(notch);
            // Leave two ordinary intervals between rapid and removal on the scale.
            case BRAKE -> notch == maxNotch ? 0 : (maxNotch + 1 - notch) / (double) (maxNotch + 1);
        };
    }

}
