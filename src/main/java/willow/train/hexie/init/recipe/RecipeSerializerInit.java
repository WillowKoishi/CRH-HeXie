package willow.train.hexie.init.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.Main;

public class RecipeSerializerInit {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZER = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Main.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, HexieBlueprintTableRecipe.HXSerializer> HEXIE_BLUEPRINT_TABLE_RECIPE_SERIALIZER =
            SERIALIZER.register("blueprint_table", HexieBlueprintTableRecipe.HXSerializer::new);

    public static void register(IEventBus bus) {
        SERIALIZER.register(bus);
    }
}
