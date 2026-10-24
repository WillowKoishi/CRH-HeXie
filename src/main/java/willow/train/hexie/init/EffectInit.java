package willow.train.hexie.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.Main;
import willow.train.hexie.effect.InstantNoodlesEffect;

public class EffectInit {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Main.MOD_ID);
    public static final DeferredHolder<MobEffect, MobEffect> NOODLE_SMELL =
            MOB_EFFECTS.register(
                    "noodle_smell",
                    () -> new InstantNoodlesEffect(MobEffectCategory.BENEFICIAL, 14723418));

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
