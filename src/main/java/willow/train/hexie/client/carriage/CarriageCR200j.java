package willow.train.hexie.client.carriage;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.block.CustomRenderedDoorBlockInit;

import java.util.List;

public class CarriageCR200j {
    public static List getList() {
        return List.of(
                BlockInit.PANEL_CR200J_MARSHALLED_TOP.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_CR200J_MARSHALLED_TOP_2.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_CR200J_MARSHALLED_TOP_3.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_CR200J_MARSHALLED_MIDDLE.get().asItem().getDefaultInstance(),
                BlockInit.WIDEPANEL_MARSHALLED_CR200J_LOGO_CR.get().asItem().getDefaultInstance(),
                BlockInit.WIDEPANEL_MARSHALLED_CR200J.get().asItem().getDefaultInstance(),
                BlockInit.WIDEPANEL_MARSHALLED_CR200J_FU_XING.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_CR200J_MARSHALLED_MIDDLE_LOGO_CR.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_MARSHALLED_CR200J.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_MARSHALLED_CR200J_CENTER.get().asItem().getDefaultInstance(),
                BlockInit.SLAB_CR200J_MARSHALLED_TOP.get().asItem().getDefaultInstance(),
                BlockInit.LADDER_MARSHALLED_CR200J.get().asItem().getDefaultInstance(),
                BlockInit.MARSHALLED_WINDOW_CR200J_WIDE_SEALED.get().asItem().getDefaultInstance(),
                BlockInit.MARSHALLED_WINDOW_CR200J_WIDE.get().asItem().getDefaultInstance(),
                BlockInit.MARSHALLED_WINDOW_CR200J_TOILET.get().asItem().getDefaultInstance(),
                BlockInit.MARSHALLED_WINDOW_CR200J_SEALED.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_CR200J_MARSHALLED_BOTTOM.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_MARSHALLED_CR200J_SKIRT.get().asItem().getDefaultInstance(),
                BlockInit.PANEL_MARSHALLED_CR200J_SKIRT_SIDE.get().asItem().getDefaultInstance(),
                CustomRenderedDoorBlockInit.MARSHALLED_CR200J_DOOR.get().asItem().getDefaultInstance(),
                CustomRenderedDoorBlockInit.MARSHALLED_CR200J_DOOR_2.get().asItem().getDefaultInstance(),
                CustomRenderedDoorBlockInit.MARSHALLED_CR200J_CABIN_DOOR.get().asItem().getDefaultInstance(),
                BlockInit.DIRECT_DRAINAGE_TOILET_MARSHALLED_CR200J.get().asItem().getDefaultInstance(),
                CustomRenderedDoorBlockInit.END_FACE_MARSHALLED_CR200J.get().asItem().getDefaultInstance(),
                CustomRenderedDoorBlockInit.END_FACE_MARSHALLED_CR200J_2.get().asItem().getDefaultInstance()
        );
    }
}
