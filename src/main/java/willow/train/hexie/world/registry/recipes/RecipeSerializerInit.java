package willow.train.hexie.world.registry.recipes;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.core.HexieConstants;

public class RecipeSerializerInit {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZER = DeferredRegister.create(Registries.RECIPE_SERIALIZER, HexieConstants.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, HexieBlueprintTableRecipe.HXSerializer> HEXIE_BLUEPRINT_TABLE_RECIPE_SERIALIZER =
            SERIALIZER.register("blueprint_table", HexieBlueprintTableRecipe.HXSerializer::new);

    public static void register(IEventBus bus) {
        SERIALIZER.register(bus);
    }
}
