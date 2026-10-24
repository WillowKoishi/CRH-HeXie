package willow.train.hexie.client.carriage;

import willow.train.hexie.init.block.BlockInit;

import java.util.List;

public class Carriage25BGZK {

    public static List getList() {
        return List.of(


                BlockInit.ORIGINAL_COLOR_WINDOW_25.get().asItem().getDefaultInstance(),
                BlockInit.ORIGINAL_COLOR_WINDOW_25_SEALED.get().asItem().getDefaultInstance(),
                BlockInit.VENTIDUCT_25B.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_25BGZK.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_25BGZKT_CENTRE.get().asItem().getDefaultInstance(),

                BlockInit.CARPORT_25BGZK_AIR_CONDITION_ALL.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_25BGZK_AIR_CONDITION_SIDE.get().asItem().getDefaultInstance(),
                BlockInit.CARPORT_25BGZKT_WATER_TANK.get().asItem().getDefaultInstance()
                );
    }
}
