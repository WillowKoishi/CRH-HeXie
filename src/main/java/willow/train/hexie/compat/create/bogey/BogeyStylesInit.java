package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

public class BogeyStylesInit {

    private static final ResourceLocation HEXIE_CYCLE_GROUP = HexieConstants.asResource("standard_bogeys");

    public static final BogeyStyle HXSTANDARDBOGEY =
            create("hxstandardbogey", HEXIE_CYCLE_GROUP)
                    .displayName(Component.translatable("hexie.bogeys.styles.hxstandardbogey"))
                    .size(BogeySizes.SMALL,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::hxStandardBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererSmallHXStandardBogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.PK209P,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::pk209pBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererPK209PBogeyRender();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.PK209P_NO_MOTOR,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::pk209pNoMotorBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererPK209PNoMotorBogeyRender();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.PK209P_BACKWARD,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::pk209pBackwardBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererPK209PBackwardBogeyRender();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.SW220K,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::sw220kBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererSW220KBogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.SW220K_BACKWARD,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::sw220kBackwardBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererSW220KBackwardBogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.SW160,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::sw160Bogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererSW160BogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .contactParticle(ParticleTypes.CRIT)
                    .smokeParticle(ParticleTypes.POOF)
                    .soundEvent(AllSoundEvents.TRAIN2::getMainEvent)
                    .build();

    public static final BogeyStyle LOCOBOGEY =
            create("loco", HEXIE_CYCLE_GROUP)
                    .displayName(Component.translatable("hexie.bogeys.styles.loco"))
                    .size(BogeySizes.SMALL,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::df11gBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createDF11GBogeyRendererSmallDF11GBogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.BACKWARD,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::df11gBackwardBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createDF11GBogeyRendererBackwardDF11GBogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.SS7D_FRONT,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::ss7dFrontBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createSS7DBogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .build();

    public static final BogeyStyle STEAMBOGEY =
            create("steambogey", HEXIE_CYCLE_GROUP)
                    .displayName(Component.translatable("hexie.bogeys.styles.steambogey"))
                    .size(BogeySizesInit.QJ,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::qjBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().df11GBogeyRendererQJBogeyRenderer().create();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.QJ_BACKWARD,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::qjBackwardBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createDF11GBogeyRendererQJBackwardBogeyRenderer();
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .build();

    public static final BogeyStyle CRHBOGEY =
            create("crhbogey", HEXIE_CYCLE_GROUP)
                    .displayName(Component.translatable("hexie.bogeys.styles.crhbogey"))
                    .size(BogeySizesInit.DT206,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::dt206Bogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizesInit.DT206, HexieBridges.Client.render().partialModelInit().dt206Frame(), HexieBridges.Client.render().partialModelInit().dt206Wheel(), 5.0 / 16);
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.SKTB200,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::sktb200Bogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizesInit.SKTB200, HexieBridges.Client.render().partialModelInit().sktb200Frame(), HexieBridges.Client.render().partialModelInit().sktb200Wheel(), 5.0 / 16);
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.CRH1A_MOTOR,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::crh1aMotorBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizesInit.CRH1A_MOTOR, HexieBridges.Client.render().partialModelInit().crh1aMotorFrame(), HexieBridges.Client.render().partialModelInit().crh1aMotorWheel(), .8006, 0, 1.35, HexieBridges.Client.bogeyRender().bogeySuspensionModels().crh1aMotor());
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .size(BogeySizesInit.CRH1A_TRAILER,
                            bogey(HexieBridges.trackRegistry().trackBlockInit()::crh1aTrailerBogey),
                            () -> () -> {
                                var renderer = HexieBridges.Client.bogeyRender().createHXStandardBogeyRendererCrhTwoAxleRenderer(BogeySizesInit.CRH1A_TRAILER, HexieBridges.Client.render().partialModelInit().crh1aTrailerFrame(), HexieBridges.Client.render().partialModelInit().crh1aTrailerWheel(), .8006, 0, 1.35, HexieBridges.Client.bogeyRender().bogeySuspensionModels().crh1aTrailer());
                                return new BogeyStyle.SizeRenderer(renderer, renderer::createVisual);
                            })
                    .build();

    private static Supplier<AbstractBogeyBlock<?>> bogey(Supplier<Block> block) {
        return () -> (AbstractBogeyBlock<?>) block.get();
    }

    public static BogeyStyle.Builder create(
            String name, ResourceLocation cycleGroup) {
        return create(HexieConstants.asResource(name), cycleGroup);
    }

    public static BogeyStyle.Builder create(
            ResourceLocation name, ResourceLocation cycleGroup) {
        return new BogeyStyle.Builder(name, cycleGroup);
    }

    public static void register() {
        HexieConstants.LOGGER.info("Registered bogey styles from " + HexieConstants.MOD_ID);
    }

}
