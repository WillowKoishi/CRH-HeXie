package willow.train.hexie.init.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.Main;

public class RecipeInit {
    public static final DeferredRegister<RecipeType<?>> RECIPE = DeferredRegister.create(Registries.RECIPE_TYPE, Main.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<HexieBlueprintTableRecipe>> BLUEPRINT_TABLE = register("blueprint_table");

    static <T extends Recipe<?>> DeferredHolder<RecipeType<?>, RecipeType<T>> register(final String name) {
        return RECIPE.register(name, () -> getType(name));
    }

    public static <T extends Recipe<?>> RecipeType<T> getType(final String name) {
        return new RecipeType<>() {public String toString() {return Main.MOD_ID + ":" + name;}};
    }

    public static void register(IEventBus bus) {
        RECIPE.register(bus);
    }
}
