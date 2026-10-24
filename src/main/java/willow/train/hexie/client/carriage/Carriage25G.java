package willow.train.hexie.client.carriage;

import willow.train.hexie.init.block.BlockInit;

import java.util.List;

public class Carriage25G {

    public static List getList() {
        return List.of(
                BlockInit.SLAB_25G_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25G_ORIGINAL_BOTTOM.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25G_ORIGINAL_MID.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25G_ORIGINAL_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25G_ORIGINAL_DOOR.get().asItem().getDefaultInstance(),
                BlockInit.LADDER_25G_ORIGIINAL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25G_ORIGINAL_WINDOW.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25G_SMALL.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25G_SMALL_2.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25G_SEALED_SMALL.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25G_TOILET.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25G_WIDE.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25G_WIDE_SEALED.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25G_ORIGINAL.get().asItem().getDefaultInstance(),
                //
                // BlockInit.END_FACE_25T_ORIGINAL_RUBBER.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25G_ORIGINAL_UPPER.get().asItem().getDefaultInstance(),
                BlockInit.END_FACE_25G_ORIGINAL_UPPER_2.get().asItem().getDefaultInstance(),
                BlockInit.DIRECT_DRAINAGE_TOILET_ORIGINAL_25G.get().asItem().getDefaultInstance()

                // general
                //
                // BlockInit.ORIGINAL_COLOR_WINDOW_25.get().asItem().getDefaultInstance(),
                //
                // BlockInit.ORIGINAL_COLOR_WINDOW_25_SEALED.get().asItem().getDefaultInstance(),
                //                    BlockInit.CARPORT_25BGZK.get().asItem().getDefaultInstance(),
                //
                // BlockInit.CARPORT_25BGZKT_CENTRE.get().asItem().getDefaultInstance()
                );
    }
}
