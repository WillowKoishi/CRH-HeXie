package willow.train.hexie.init.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import willow.train.hexie.Main;

public class HexieBlueprintTableRecipe extends SingleItemRecipe {
    public final ResourceLocation img_location;
    public final int level_require, exp_consume;

    public HexieBlueprintTableRecipe(ResourceLocation image, String group, int requiredLevel,
                                      int experience, Ingredient ingredient, ItemStack result) {
        super(RecipeInit.BLUEPRINT_TABLE.get(), RecipeSerializerInit.HEXIE_BLUEPRINT_TABLE_RECIPE_SERIALIZER.get(),
                group, ingredient, result);
        this.img_location = image;
        this.level_require = requiredLevel;
        this.exp_consume = experience;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    public static class HXSerializer implements RecipeSerializer<HexieBlueprintTableRecipe> {
        private static final Codec<ResourceLocation> IMAGE_CODEC = Codec.STRING.xmap(value -> {
            ResourceLocation image = value.contains(":") ? ResourceLocation.parse(value) : Main.asResource(value);
            return image.getPath().startsWith("textures/") ? image :
                    ResourceLocation.fromNamespaceAndPath(image.getNamespace(), "textures/" + image.getPath());
        }, ResourceLocation::toString);

        private static final MapCodec<HexieBlueprintTableRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                IMAGE_CODEC.optionalFieldOf("img_location", Main.asResource("textures/recipes/blank.png")).forGetter(r -> r.img_location),
                Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("level_require", 0).forGetter(r -> r.level_require),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("exp_consume", 0).forGetter(r -> r.exp_consume),
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredients").forGetter(r -> r.ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result)
        ).apply(instance, HexieBlueprintTableRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, HexieBlueprintTableRecipe> STREAM_CODEC = StreamCodec.of(
                (buffer, recipe) -> {
                    buffer.writeResourceLocation(recipe.img_location);
                    buffer.writeUtf(recipe.group);
                    buffer.writeVarInt(recipe.level_require);
                    buffer.writeVarInt(recipe.exp_consume);
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.ingredient);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                }, buffer -> new HexieBlueprintTableRecipe(buffer.readResourceLocation(), buffer.readUtf(),
                        buffer.readVarInt(), buffer.readVarInt(), Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        ItemStack.STREAM_CODEC.decode(buffer)));
        @Override public MapCodec<HexieBlueprintTableRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, HexieBlueprintTableRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
