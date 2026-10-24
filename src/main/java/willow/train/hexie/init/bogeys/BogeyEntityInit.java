package willow.train.hexie.init.bogeys;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import willow.train.hexie.Main;
import willow.train.hexie.blocks.bogeys.df11g.DF11GBogeyBlockEntity;
import willow.train.hexie.blocks.bogeys.standard.HXStandardBogeyBlockEntity;
import willow.train.hexie.init.track.TrackBlockInit;

public class BogeyEntityInit {

    private static final CreateRegistrate REGISTRATE = Main.registrate();

    public static final BlockEntityEntry<HXStandardBogeyBlockEntity> HX_STANDARD_BOGEY =
            REGISTRATE
                    .blockEntity("hx_standard_bogey", HXStandardBogeyBlockEntity::new)
                    .validBlocks(
                            TrackBlockInit.HX_STANDARD_BOGEY,
                            TrackBlockInit.PK209P_BOGEY,
                            TrackBlockInit.PK209P_NO_MOTOR_BOGEY,
                            TrackBlockInit.PK209P_BACKWARD_BOGEY,
                            TrackBlockInit.SW220K_BOGEY,
                            TrackBlockInit.SW220K_BACKWARD_BOGEY,
                            TrackBlockInit.SW160_BOGEY)
                    .register();

    public static final BlockEntityEntry<DF11GBogeyBlockEntity> DF11G_BOGEY =
            REGISTRATE
                    .blockEntity("df11g_bogey", DF11GBogeyBlockEntity::new)
                    .validBlocks(
                            TrackBlockInit.DF11G_BOGEY,
                            TrackBlockInit.DF11G_BACKWARD_BOGEY,
                            TrackBlockInit.QJ_BOGEY)
                    .register();

    //            .register();


    public static void register() {}
}
