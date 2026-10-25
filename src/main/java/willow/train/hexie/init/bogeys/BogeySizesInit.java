package willow.train.hexie.init.bogeys;

import com.simibubi.create.content.trains.bogey.BogeySizes;

import willow.train.hexie.Main;

public class BogeySizesInit {

    public static final BogeySizes.BogeySize EXTRA = create("extra", 6.5f / 16f);

    public static final BogeySizes.BogeySize BACKWARD = create("backward", 1.05F / 2);

    public static final BogeySizes.BogeySize PK209P = create("pk209p", 0.915F / 2F);

    public static final BogeySizes.BogeySize PK209P_NO_MOTOR =
            create("pk209p_no_motor", 0.915F / 2F); // /6.5f/16f);

    public static final BogeySizes.BogeySize PK209P_BACKWARD =
            create("pk209p_backward", 0.915F / 2F);

    public static final BogeySizes.BogeySize SW220K = create("sw220k", 0.915F / 2F);

    public static final BogeySizes.BogeySize SW220K_BACKWARD =
            create("sw220k_backward", 0.915F / 2F);

    public static final BogeySizes.BogeySize QJ = create("qj", 1.5F / 2F);

    public static final BogeySizes.BogeySize QJ_BACKWARD = create("qj_backward", 1.5F / 2F);

    public static final BogeySizes.BogeySize SW160 = create("sw160", 0.915F / 2F);

    public static BogeySizes.BogeySize create(String name, float size) {
        BogeySizes.BogeySize bogeySize = new BogeySizes.BogeySize(Main.asResource(name), size);
        BogeySizes.register(bogeySize);
        return bogeySize;
    }

    public static void register() {}
}
