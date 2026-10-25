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

}
