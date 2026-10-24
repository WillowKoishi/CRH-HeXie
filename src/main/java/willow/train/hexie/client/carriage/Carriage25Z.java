package willow.train.hexie.client.carriage;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.block.CustomRenderedDoorBlockInit;

import java.util.List;

public class Carriage25Z {

    public static List getList() {
        return List.of(
                BlockInit.SLAB_25Z_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25Z.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_SYMBOL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_LINE.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_SIDE.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25Z_ORIGINAL_MID.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25Z_ORIGINAL_MID_SIDE.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25Z.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25Z_SMALL2.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25Z_TOILET.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25Z_WIDE.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25Z_LEVEL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25Z_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.LADDER_25Z_ORIGIINAL.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25Z_ORIGINAL.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25Z_ORIGINAL_UPPER.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25Z_ORIGINAL_UPPER_AC.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_CENTRE_25Z.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_25Z_SIDE.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_25Z_AC_SIDE.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_25Z_AC.get().asItem().getDefaultInstance(),
                CustomRenderedDoorBlockInit.PANEL_25Z_ORIGINAL_DOOR.get().asItem().getDefaultInstance()
                );
    }
}
