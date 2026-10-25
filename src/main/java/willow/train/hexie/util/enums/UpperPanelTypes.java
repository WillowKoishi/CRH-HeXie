package willow.train.hexie.util.enums;

import net.minecraft.util.StringRepresentable;

public enum UpperPanelTypes implements StringRepresentable {
    P25BA,
    P25BB,
    P25G,
    P25Z,
    P25K,
    P25TA,
    P25TB,
    P25TC,
    M25A,
    M25B,
    CR200J;

    public String getSerializedName() {
        switch (this) {
            case P25BA -> {
                return "p25ba";
            }
            case P25BB -> {
                return "p25bb";
            }
            case P25G -> {
                return "p25g";
            }
            case P25Z -> {
                return "p25z";
            }
            case P25K -> {
                return "p25k";
            }
            case P25TA -> {
                return "p25ta";
            }
            case P25TB -> {
                return "p25tb";
            }
            case P25TC -> {
                return "p25tc";
            }
            case M25A -> {
                return "m25a";
            }
            case M25B -> {
                return "m25b";
            }
            case CR200J -> {
                return "cr200j";
            }
            default -> {
                return "p25ba";
            }
        }
    }

    @Override
    public String toString() {
        return this.getSerializedName();
    }

}
