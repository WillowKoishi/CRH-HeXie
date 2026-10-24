package willow.train.hexie.client.carriage;

import willow.train.hexie.init.block.BlockInit;

import java.util.List;

public class Marshalled25Series {

    public static List getList() {
        return List.of(
                BlockInit.PANEL_25B_MARSHALLED_SYMBOL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25K_MARSHALLED_SYMBOL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25G_MARSHALLED_SYMBOL.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25_MARSHALLED_BOTTOM_LINE.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25T_MARSHALLED_SKIRT.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25_MARSHALLED_BOTTOM.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25_MARSHALLED_MID.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25_MARSHALLED_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25_MARSHALLED_TOP_B.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25_MARSHALLED_DOOR.get().asItem().getDefaultInstance(),
                BlockInit.SLAB_25_MARSHALLED_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_25T_MARSHALLED_SKIRT_SIDE.get().asItem().getDefaultInstance()

                );
    }
}
