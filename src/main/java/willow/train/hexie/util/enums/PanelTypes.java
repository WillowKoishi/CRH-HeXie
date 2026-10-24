package willow.train.hexie.util.enums;

import net.minecraft.util.StringRepresentable;

public enum PanelTypes implements StringRepresentable {
    P25B,
    P25G,
    P25ZA,
    P25ZB,
    P25ZC,
    P25TA,
    P25TB,
    P25KA,
    P25KB, // 标识线
    P25KC,
    M25B,
    M25G,
    M25Z,
    M25T,
    M25KA,
    M25KB; // 标识线

    public String toString() {
        return this.getSerializedName();
    }

    public String getSerializedName() {
        switch (this) {
            case P25B -> {
                return "p25b";
            }
            case P25G -> {
                return "p25g";
            }
            case P25ZA -> {
                return "p25za";
            }
            case P25ZB -> {
                return "p25zb";
            }
            case P25ZC -> {
                return "p25zc";
            }
            case P25TA -> {
                return "p25ta";
            }
            case P25TB -> {
                return "p25tb";
            }
            case P25KA -> {
                return "p25ka";
            }
            case P25KB -> {
                return "p25kb";
            }
            case P25KC -> {
                return "p25kc";
            }
            case M25B -> {
                return "m25b";
            }
            case M25G -> {
                return "m25g";
            }
            case M25Z -> {
                return "m25z";
            }
            case M25T -> {
                return "m25t";
            }
            case M25KA -> {
                return "m25ka";
            } // 细线
            case M25KB -> {
                return "m25kb";
            } // 粗线
            default -> {
                return "p25b";
            }
        }
    }

    public static PanelTypes encode(String str) {
        switch (str) {
            case "p25b" -> {
                return P25B;
            }
            case "p25g" -> {
                return P25G;
            }
            case "p25za" -> {
                return P25ZA;
            }
            case "p25zb" -> {
                return P25ZB;
            }
            case "p25zc" -> {
                return P25ZC;
            }
            case "p25ta" -> {
                return P25TA;
            }
            case "p25tb" -> {
                return P25TB;
            }
            case "p25ka" -> {
                return P25KA;
            }
            case "p25kb" -> {
                return P25KB;
            }
            case "p25kc" -> {
                return P25KC;
            }
            case "m25b" -> {
                return M25B;
            }
            case "m25g" -> {
                return M25G;
            }
            case "m25z" -> {
                return M25Z;
            }
            case "m25t" -> {
                return M25T;
            }
            case "m25ka" -> {
                return M25KA;
            }
            case "m25kb" -> {
                return M25KB;
            }
            default -> {
                return P25B;
            }
        }
    }
}
