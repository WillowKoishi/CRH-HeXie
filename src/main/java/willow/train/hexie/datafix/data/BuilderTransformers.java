package willow.train.hexie.datafix.data;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import willow.train.hexie.blocks.bogeys.df11g.DF11GBogeyBlock;
import willow.train.hexie.blocks.bogeys.standard.HXStandardBogeyBlock;

public class BuilderTransformers {

    public static <B extends HXStandardBogeyBlock, P>
            NonNullUnaryOperator<BlockBuilder<B, P>> hxstandardbogey(String modelLocation) {
        return b ->
                b.initialProperties(SharedProperties::softMetal)
                        .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
                        .properties(p -> p.noOcclusion())
                        .transform(pickaxeOnly())
                        .blockstate(
                                (c, p) ->
                                        p.getVariantBuilder(c.get())
                                                .forAllStates(s -> ConfiguredModel.builder()
                                                        .modelFile(p.models().getExistingFile(
                                                                ResourceLocation.parse(modelLocation)))
                                                        .rotationY(s.getValue(BlockStateProperties.HORIZONTAL_AXIS)
                                                                == Direction.Axis.Z ? 90 : 0)
                                                        .build()))
                        .loot((p, l) -> p.dropOther(l, AllBlocks.RAILWAY_CASING.get()));
    }

    public static <B extends DF11GBogeyBlock, P>
            NonNullUnaryOperator<BlockBuilder<B, P>> df11gbogey(String modelLocation) {
        return b ->
                b.initialProperties(SharedProperties::softMetal)
                        .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
                        .properties(p -> p.noOcclusion())
                        .transform(pickaxeOnly())
                        .blockstate(
                                (c, p) ->
                                        p.getVariantBuilder(c.get())
                                                .forAllStates(s -> ConfiguredModel.builder()
                                                        .modelFile(p.models().getExistingFile(
                                                                ResourceLocation.parse(modelLocation)))
                                                        .rotationY(s.getValue(BlockStateProperties.HORIZONTAL_AXIS)
                                                                == Direction.Axis.Z ? 90 : 0)
                                                        .build()))
                        .loot((p, l) -> p.dropOther(l, AllBlocks.RAILWAY_CASING.get()));
    }
}
