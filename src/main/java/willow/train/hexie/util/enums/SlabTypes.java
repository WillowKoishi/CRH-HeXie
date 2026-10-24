package willow.train.hexie.util.enums;

import net.minecraft.util.StringRepresentable;

public enum SlabTypes implements StringRepresentable {

    // S -> 地板
    // D -> 便池
    S25B,
    D25B,
    S25G,
    D25G,
    S25Z,
    D25Z,
    S25K,
    D25K,
    S25T,
    D25T,
    S25M,
    CR200J;

    @Override
    public String getSerializedName() {
        switch (this) {
            case S25B -> {
                return "s25b";
            }
            case D25B -> {
                return "d25b";
            }
            case S25G -> {
                return "s25g";
            }
            case D25G -> {
                return "d25g";
            }
            case S25Z -> {
                return "s25z";
            }
            case D25Z -> {
                return "d25z";
            }
            case S25K -> {
                return "s25k";
            }
            case D25K -> {
                return "d25k";
            }
            case S25T -> {
                return "s25t";
            }
            case D25T -> {
                return "d25t";
            }
            case S25M -> {
                return "s25m";
            }
            case CR200J -> {
                return "cr200j";
            }
            default -> {
                return "s25b";
            }
        }
    }

    public static SlabTypes encode(String name) {
        switch (name) {
            case "s25b" -> {
                return S25B;
            }
            case "d25b" -> {
                return D25B;
            }
            case "s25g" -> {
                return S25G;
            }
            case "d25g" -> {
                return D25G;
            }
            case "s25z" -> {
                return S25Z;
            }
            case "d25z" -> {
                return D25Z;
            }
            case "s25k" -> {
                return S25K;
            }
            case "d25k" -> {
                return D25K;
            }
            case "s25t" -> {
                return S25T;
            }
            case "d25t" -> {
                return D25T;
            }
            case "s25m" -> {
                return S25M;
            }
            case "cr200j" -> {
                return CR200J;
            }
            default -> {
                return S25B;
            }
        }
    }
}
