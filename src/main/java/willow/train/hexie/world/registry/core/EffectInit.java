package willow.train.hexie.world.registry.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.world.registry.effect.InstantNoodlesEffect;

public class EffectInit {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, HexieConstants.MOD_ID);
    public static final DeferredHolder<MobEffect, MobEffect> NOODLE_SMELL =
            MOB_EFFECTS.register(
                    "noodle_smell",
                    () -> new InstantNoodlesEffect(MobEffectCategory.BENEFICIAL, 14723418));

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
