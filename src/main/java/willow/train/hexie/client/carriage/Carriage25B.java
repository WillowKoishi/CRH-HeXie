package willow.train.hexie.client.carriage;

import willow.train.hexie.init.block.BlockInit;

import java.util.List;

public class Carriage25B {

    public static List getList() {
        return List.of(
                // proprietary
                BlockInit.SLAB_25B_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25B_ORIGINAL_BOTTOM.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25B_ORIGINAL_MID.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25B_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25B_ORIGINAL_TOP_B.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25B_ORIGINAL_DOOR.get().asItem().getDefaultInstance(),
                BlockInit.LADDER_25B_ORIGIINAL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25B_ORIGINAL_WINDOW.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25B.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25B_SMALL.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25B_TOILET.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25B_WIDE.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25B_WIDE_SEALED.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25B.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25B_ORIGINAL_UPPER.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25B_ORIGINAL_UPPER_2.get().asItem().getDefaultInstance(),
                BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25B.get().asItem().getDefaultInstance());
    }
}
