package willow.train.hexie.client.carriage;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.block.CustomRenderedDoorBlockInit;

import java.util.List;

public class Carriage25K {

    public static List getList() {
        return List.of(
                BlockInit.SLAB_25K_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_ORIGINAL_SYMBOL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_ORIGINAL_BOTTOM.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_ORIGINAL_MID.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.LADDER_25K_ORIGIINAL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_ORIGINAL_DOOR.get().asItem().getDefaultInstance(),
                CustomRenderedDoorBlockInit.PANEL_25K_ORIGINAL_SLIDING_DOOR.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_ORIGINAL_LINE.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_ORIGINAL_WINDOW.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25K_SMALL.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25K_SEALED_SMALL.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25K_TOILET.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25K_WIDE.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25K_WIDE_SEALED.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25K_ORIGINAL.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25K_ORIGINAL_UPPER.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25K_ORIGINAL_UPPER_2.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25K_ORIGINAL_RUBBER.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25K_ORIGINAL_RUBBER_UPPER.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25K_ORIGINAL_RUBBER_UPPER_2.get().asItem().getDefaultInstance(),
                BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25K.get().asItem().getDefaultInstance()

                );
    }
}
