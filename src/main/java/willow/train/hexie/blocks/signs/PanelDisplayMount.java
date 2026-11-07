package willow.train.hexie.blocks.signs;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Both removable components share the panel root and one left/right layout. */
public enum PanelDisplayMount implements StringRepresentable {
    NONE("none", 0, true),
    NUMBER_LEFT("number_left", 1, true), NUMBER_RIGHT("number_right", 1, false),
    SIGN_LEFT("sign_left", 2, false), SIGN_RIGHT("sign_right", 2, true),
    NUMBER_LEFT_SIGN_RIGHT("number_left_sign_right", 3, true),
    SIGN_LEFT_NUMBER_RIGHT("sign_left_number_right", 3, false);

    public static final EnumProperty<PanelDisplayMount> PROPERTY = EnumProperty.create("displays", PanelDisplayMount.class);
    private final String name;
    private final int mask;
    private final boolean numberLeft;
    PanelDisplayMount(String name, int mask, boolean numberLeft) {
        this.name = name; this.mask = mask; this.numberLeft = numberLeft;
    }
    @Override public String getSerializedName() { return name; }
    public boolean hasNumber() { return (mask & 1) != 0; }
    public boolean hasSign() { return (mask & 2) != 0; }
    public boolean numberLeft() { return numberLeft; }
    public PanelDisplayMount add(boolean number) { return of(mask | (number ? 1 : 2), numberLeft); }
    public PanelDisplayMount swap() { return of(mask, !numberLeft); }
    private static PanelDisplayMount of(int mask, boolean left) {
        return switch (mask) {
            case 1 -> left ? NUMBER_LEFT : NUMBER_RIGHT;
            case 2 -> left ? SIGN_RIGHT : SIGN_LEFT;
            case 3 -> left ? NUMBER_LEFT_SIGN_RIGHT : SIGN_LEFT_NUMBER_RIGHT;
            default -> NONE;
        };
    }
}
