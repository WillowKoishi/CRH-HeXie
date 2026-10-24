package willow.train.hexie.init.track;

import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

import com.simibubi.create.AllTags;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockItem;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackModel;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullConsumer;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.StandardTrackBlockStateGenerator;
import willow.train.hexie.blocks.bogeys.df11g.DF11GBogeyBlock;
import willow.train.hexie.blocks.bogeys.standard.HXStandardBogeyBlock;
import willow.train.hexie.blocks.locos.df11g.DF11GFrontBlock;
import willow.train.hexie.datafix.data.BuilderTransformers;
import willow.train.hexie.init.bogeys.BogeySizesInit;
import willow.train.hexie.item.ToolTipsItemHelper;

import java.util.function.Function;

public class TrackBlockInit {
    private static final CreateRegistrate REGISTRATE =
            Main.registrate().setCreativeTab(Main.HEXIE_MAIN.holder());

    private static BlockEntry<TrackBlock> makeTrack(
            TrackMaterial material,
            NonNullBiConsumer<DataGenContext<Block, TrackBlock>, RegistrateBlockstateProvider>
                    blockstateGen,
            NonNullConsumer<? super TrackBlock> onRegister,
            Function<BlockBehaviour.Properties, BlockBehaviour.Properties> collectProperties) {
        return REGISTRATE
                .block(material.resourceName() + "_track", material::createBlock)
                .initialProperties(() -> net.minecraft.world.level.block.Blocks.STONE)
                .properties(
                        p ->
                                collectProperties
                                        .apply(p)
                                        .mapColor(MapColor.METAL)
                                        .strength(0.8F)
                                        .sound(SoundType.METAL)
                                        .noOcclusion()
                                        .forceSolidOn())
                .transform(pickaxeOnly())
                .clientExtension(() -> () -> new TrackBlock.RenderProperties())
                .blockstate(blockstateGen)
                .tag(AllTags.AllBlockTags.TRACKS.tag)
                .tag(AllTags.AllBlockTags.GIRDABLE_TRACKS.tag)
                .tag(net.neoforged.neoforge.common.Tags.Blocks.RELOCATION_NOT_SUPPORTED)
                .lang(material.langName + " Train Track")
                .onRegister(onRegister)
                .onRegister(CreateRegistrate.blockModel(() -> TrackModel::new))
                .item(TrackBlockItem::new)
                .tag(AllTags.AllItemTags.TRACKS.tag)
                .model((c, p) -> p.generated(c, Main.asResource("item/track/" + c.getName())))
                .build()
                .register();
    }

    private static BlockEntry<TrackBlock> makeTrack(
            TrackMaterial material,
            NonNullBiConsumer<DataGenContext<Block, TrackBlock>, RegistrateBlockstateProvider>
                    blockstateGen) {
        return makeTrack(material, blockstateGen, (t) -> {});
    }

    private static BlockEntry<TrackBlock> makeTrack(
            TrackMaterial material,
            NonNullBiConsumer<DataGenContext<Block, TrackBlock>, RegistrateBlockstateProvider>
                    blockstateGen,
            NonNullConsumer<? super TrackBlock> onRegister) {
        return makeTrack(material, blockstateGen, onRegister, (p) -> p);
    }

    public static final BlockEntry<TrackBlock> STANDARD_TRACK =
            makeTrack(TrackMaterialInit.STANDARD, new StandardTrackBlockStateGenerator()::generate);

    public static final BlockEntry<TrackBlock> BALLASTLESS_TRACK =
            makeTrack(TrackMaterialInit.BALLASTLESS, new StandardTrackBlockStateGenerator()::generate);

    public static final BlockEntry<TrackBlock> TIELESS_TRACK =
            makeTrack(TrackMaterialInit.TIELESS, new StandardTrackBlockStateGenerator()::generate);

    public static final BlockEntry<HXStandardBogeyBlock> HX_STANDARD_BOGEY =
            REGISTRATE
                    .block("hx_standard_bogey", p -> new HXStandardBogeyBlock(p, BogeySizes.SMALL))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.hxstandardbogey("hexie:block/bogey/hx_standard/cw2_top"))
                    .lang("HX Standard Bogey")
                    // .item(HXStandardBogeyItem::new)
                    // .model((c, p) -> p.generated(c, Main.asResource("item/track/" +
                    // c.getName())))
                    // .build()
                    .register();

    public static final BlockEntry<DF11GBogeyBlock> DF11G_BOGEY =
            REGISTRATE
                    .block("df11g_bogey", p -> new DF11GBogeyBlock(p, BogeySizes.SMALL))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.df11gbogey("hexie:block/bogey/df11g/df11g_top"))
                    .lang("DF11G Bogey")
                    .register();

    public static final BlockEntry<DF11GBogeyBlock> DF11G_BACKWARD_BOGEY =
            REGISTRATE
                    .block(
                            "df11g_backward_bogey",
                            p -> new DF11GBogeyBlock(p, BogeySizesInit.BACKWARD))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.df11gbogey("hexie:block/bogey/df11g/df11g_top"))
                    .lang("DF11G Backward Bogey")
                    .register();

    public static final BlockEntry<HXStandardBogeyBlock> PK209P_BOGEY =
            REGISTRATE
                    .block("pk209p_bogey", p -> new HXStandardBogeyBlock(p, BogeySizesInit.PK209P))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.hxstandardbogey("hexie:block/bogey/pk209p/pk209_top"))
                    .lang("PK209P Bogey")
                    .register();

    public static final BlockEntry<HXStandardBogeyBlock> PK209P_NO_MOTOR_BOGEY =
            REGISTRATE
                    .block(
                            "pk209p_no_motor_bogey",
                            p -> new HXStandardBogeyBlock(p, BogeySizesInit.PK209P_NO_MOTOR))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.hxstandardbogey("hexie:block/bogey/pk209p/pk209_top"))
                    .lang("PK209P No Motor Bogey")
                    .register();

    public static final BlockEntry<HXStandardBogeyBlock> PK209P_BACKWARD_BOGEY =
            REGISTRATE
                    .block(
                            "pk209p_backward_bogey",
                            p -> new HXStandardBogeyBlock(p, BogeySizesInit.PK209P_BACKWARD))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.hxstandardbogey("hexie:block/bogey/pk209p/pk209_top"))
                    .lang("PK209P Backward Bogey")
                    .register();

    public static final BlockEntry<HXStandardBogeyBlock> SW220K_BOGEY =
            REGISTRATE
                    .block("sw220k_bogey", p -> new HXStandardBogeyBlock(p, BogeySizesInit.SW220K))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.hxstandardbogey("hexie:block/bogey/sw220k/sw220k_top"))
                    .lang("SW220K Bogey")
                    .register();

    public static final BlockEntry<HXStandardBogeyBlock> SW220K_BACKWARD_BOGEY =
            REGISTRATE
                    .block(
                            "sw220k_backward_bogey",
                            p -> new HXStandardBogeyBlock(p, BogeySizesInit.SW220K_BACKWARD))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.hxstandardbogey("hexie:block/bogey/sw220k/sw220k_top"))
                    .lang("SW220K Backward Bogey")
                    .register();

    public static final BlockEntry<DF11GBogeyBlock> QJ_BOGEY =
            REGISTRATE
                    .block("qj_bogey", p -> new DF11GBogeyBlock(p, BogeySizesInit.QJ))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.df11gbogey("create:block/track/bogey/top"))
                    .lang("QJ Bogey")
                    .register();

    public static final BlockEntry<HXStandardBogeyBlock> SW160_BOGEY =
            REGISTRATE
                    .block("sw160_bogey", p -> new HXStandardBogeyBlock(p, BogeySizesInit.SW160))
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .transform(BuilderTransformers.hxstandardbogey("hexie:block/bogey/hx_standard/cw2_top"))
                    .lang("SW160 Bogey")
                    .register();

    private static final CreateRegistrate locoREGISTRATE =
            Main.registrate().setCreativeTab(Main.HEXIE_LOCOS.holder());

    public static final BlockEntry<DF11GFrontBlock> DF11G_FRONT_BLOCK =
            locoREGISTRATE
                    .block("df11g_front2", DF11GFrontBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.mapColor(MapColor.COLOR_BLUE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .blockstate(
                            (c, p) ->
                                    p.horizontalBlock(
                                            c.get(),
                                            p.models()
                                                    .getExistingFile(
                                                            p.modLoc(
                                                                    "block/df11g/df11g_front_module"))))
                    .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 2.0))
                    // .onRegister(CreateRegistrate.connectedTextures(CrafterCTBehaviour::new))
                    .item(ToolTipsItemHelper::new)
                    .model((c, p) -> p.generated(c, Main.asResource("item/logo/df11g_icon")))
                    .build()
                    .register();

    public static final BlockEntry<DF11GFrontBlock> HXD3D_FRONT_BLOCK =
            locoREGISTRATE
                    .block("hxd3d_front", DF11GFrontBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.mapColor(MapColor.COLOR_BLUE))
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .transform(axeOrPickaxe())
                    .blockstate(
                            (c, p) ->
                                    p.horizontalBlock(
                                            c.get(),
                                            p.models()
                                                    .getExistingFile(
                                                            p.modLoc(
                                                                    "block/hxd3d/hxd3d_front_module"))))
                    .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 2.0))
                    // .onRegister(CreateRegistrate.connectedTextures(CrafterCTBehaviour::new))
                    .item(ToolTipsItemHelper::new)
                    .model((c, p) -> p.generated(c, Main.asResource("item/logo/hxd3d_icon")))
                    .build()
                    .register();


    public static void register() {}
}
