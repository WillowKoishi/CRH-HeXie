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

    public static UpperPanelTypes encode(String str) {
        switch (str) {
            case "p25ba" -> {
                return P25BA;
            }
            case "p25bb" -> {
                return P25BB;
            }
            case "p25g" -> {
                return P25G;
            }
            case "p25z" -> {
                return P25Z;
            }
            case "p25k" -> {
                return P25K;
            }
            case "p25ta" -> {
                return P25TA;
            }
            case "p25tb" -> {
                return P25TB;
            }
            case "p25tc" -> {
                return P25TC;
            }
            case "m25a" -> {
                return M25A;
            }
            case "m25b" -> {
                return M25B;
            }
            case "cr200j" -> {
                return CR200J;
            }
            default -> {
                return P25BA;
            }
        }
    }
}
