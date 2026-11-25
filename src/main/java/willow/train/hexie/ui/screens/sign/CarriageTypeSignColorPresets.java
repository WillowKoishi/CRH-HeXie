package willow.train.hexie.ui.screens.sign;

import net.minecraft.network.chat.Component;
import willow.train.hexie.bridge.world.WorldBlockBridge.CarriageTypeSignEntityOperations;
import willow.train.hexie.core.HexieConstants;

// split from willow/train/hexie/client/screens/CarriageTypeSignEditScreen.java
final class CarriageTypeSignColorPresets {
    static Component predefineColorMapping(int x) {
        switch (x) {
            case 0 -> {
                return Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".carriage_type_sign_edit_menu.color.yellow");
            }
            case 1 -> {
                return Component.translatable(
                        "container."
                                + HexieConstants.MOD_ID
                                + ".carriage_type_sign_edit_menu.color.yellow_2");
            }
            case 2 -> {
                return Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".carriage_type_sign_edit_menu.color.red");
            }
            case 3 -> {
                return Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".carriage_type_sign_edit_menu.color.blue");
            }
            case 4 -> {
                return Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".carriage_type_sign_edit_menu.color.blue_2");
            }
            case 5 -> {
                return Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".carriage_type_sign_edit_menu.color.blue_3");
            }
            case 6 -> {
                return Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".carriage_type_sign_edit_menu.color.black");
            }
            default -> {
                return Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".carriage_type_sign_edit_menu.color.yellow");
            }
        }
    }

    static int predefineColorMapping2(int x) {
        switch (x) {
            case 0 -> {
                return CarriageTypeSignEntityOperations.YELLOW;
            }
            case 1 -> {
                return CarriageTypeSignEntityOperations.YELLOW2;
            }
            case 2 -> {
                return CarriageTypeSignEntityOperations.RED;
            }
            case 3 -> {
                return CarriageTypeSignEntityOperations.BLUE;
            }
            case 4 -> {
                return CarriageTypeSignEntityOperations.BLUE2;
            }
            case 5 -> {
                return CarriageTypeSignEntityOperations.BLUE3;
            }
            case 6 -> {
                return CarriageTypeSignEntityOperations.BLACK;
            }
            default -> {
                return CarriageTypeSignEntityOperations.YELLOW;
            }
        }
    }
}
