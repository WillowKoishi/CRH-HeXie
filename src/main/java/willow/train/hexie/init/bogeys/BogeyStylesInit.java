package willow.train.hexie.init.bogeys;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.network.chat.Component;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;

import willow.train.hexie.Main;
import willow.train.hexie.client.render.bogey.DF11GBogeyRenderer;
import willow.train.hexie.client.render.bogey.HXStandardBogeyRenderer;
import willow.train.hexie.init.track.TrackBlockInit;

public class BogeyStylesInit {

    private static final ResourceLocation HEXIE_CYCLE_GROUP = Main.asResource("standard_bogeys");

    public static final BogeyStyle HXSTANDARDBOGEY =
            create("hxstandardbogey", HEXIE_CYCLE_GROUP)
                    .displayName(Component.translatable("hexie.bogeys.styles.hxstandardbogey"))
                    .size(BogeySizes.SMALL, TrackBlockInit.HX_STANDARD_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.SmallHXStandardBogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.PK209P, TrackBlockInit.PK209P_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.PK209PBogeyRender(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.PK209P_NO_MOTOR, TrackBlockInit.PK209P_NO_MOTOR_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.PK209PNoMotorBogeyRender(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.PK209P_BACKWARD, TrackBlockInit.PK209P_BACKWARD_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.PK209PBackwardBogeyRender(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.SW220K, TrackBlockInit.SW220K_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.SW220KBogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.SW220K_BACKWARD, TrackBlockInit.SW220K_BACKWARD_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.SW220KBackwardBogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.SW160, TrackBlockInit.SW160_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.SW160BogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .contactParticle(ParticleTypes.CRIT)
                    .smokeParticle(ParticleTypes.POOF)
                    .soundEvent(AllSoundEvents.TRAIN2::getMainEvent)
                    .build();

    public static final BogeyStyle DF11GBOGEY =
            create("df11gbogey", HEXIE_CYCLE_GROUP)
                    .displayName(Component.translatable("hexie.bogeys.styles.df11gbogey"))
                    .size(BogeySizes.SMALL, TrackBlockInit.DF11G_BOGEY, () -> () -> { var renderer = new DF11GBogeyRenderer.SmallDF11GBogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.BACKWARD, TrackBlockInit.DF11G_BACKWARD_BOGEY, () -> () -> { var renderer = new DF11GBogeyRenderer.BackwardDF11GBogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.QJ, TrackBlockInit.QJ_BOGEY, () -> () -> { var renderer = new DF11GBogeyRenderer.QJBogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .size(BogeySizesInit.QJ_BACKWARD, TrackBlockInit.QJ_BACKWARD_BOGEY, () -> () -> { var renderer = new DF11GBogeyRenderer.QJBackwardBogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .build();

    public static final BogeyStyle CRHBOGEY =
            create("crhbogey", HEXIE_CYCLE_GROUP)
                    .displayName(Component.translatable("hexie.bogeys.styles.crhbogey"))
                    .size(BogeySizesInit.DT206, TrackBlockInit.DT206_BOGEY, () -> () -> { var renderer = new HXStandardBogeyRenderer.DT206BogeyRenderer(); return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual); })
                    .build();

    public static BogeyStyle.Builder create(
            String name, ResourceLocation cycleGroup) {
        return create(Main.asResource(name), cycleGroup);
    }

    public static BogeyStyle.Builder create(
            ResourceLocation name, ResourceLocation cycleGroup) {
        return new BogeyStyle.Builder(name, cycleGroup);
    }

    public static void register() {
        Main.LOGGER.info("Registered bogey styles from " + Main.MOD_ID);
    }

}
