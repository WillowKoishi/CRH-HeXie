package willow.train.hexie.init.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.init.EffectInit;
import willow.train.hexie.item.foods.Food1;

public class DietInit {

    // 火车餐1
    public static final DeferredHolder<Item, Item> TRAIN_DIET_1 =
            AllItemsInit.register(
                    "train_diet_1",
                    () ->
                            new Food1(
                                    new Item.Properties()
                                            
                                            .food(
                                                    new FoodProperties.Builder()
                                                            .nutrition(4)
                                                            .saturationModifier(2.5F)
                                                            .effect(
                                                                    () ->
                                                                            new MobEffectInstance(
                                                                                    MobEffects
                                                                                            .HUNGER,
                                                                                    1000,
                                                                                    0,
                                                                                    false,
                                                                                    true),
                                                                    1.0F)
                                                            .effect(
                                                                    () ->
                                                                            new MobEffectInstance(
                                                                                    MobEffects
                                                                                            .CONFUSION,
                                                                                    1000,
                                                                                    0,
                                                                                    false,
                                                                                    true),
                                                                    1.0F)
                                                            .effect(
                                                                    () ->
                                                                            new MobEffectInstance(
                                                                                    EffectInit
                                                                                            .NOODLE_SMELL,
                                                                                    2000,
                                                                                    0,
                                                                                    false,
                                                                                    true),
                                                                    1.0F)
                                                            .alwaysEdible()
                                                            .alwaysEdible()
                                                            .build())));
}
