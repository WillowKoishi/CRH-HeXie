package willow.train.hexie.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import willow.train.hexie.Main;

public class SoundsInit {
    public static final DeferredRegister<SoundEvent> SOUND_EVENT =
            DeferredRegister.create(Registries.SOUND_EVENT, Main.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CROSSING_RINGING =
            registerSoundEvent("crossing_ringing");

    public static final DeferredHolder<SoundEvent, SoundEvent> BEEP_TURNSTILE =
            registerSoundEvent("beep_turnstile");

    public static final DeferredHolder<SoundEvent, SoundEvent> CHUFF =
            registerSoundEvent("chuff");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENT.register(
                name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, name)));
    }

    public static void register(IEventBus iEventBus) {

        SOUND_EVENT.register(iEventBus);
    }
}
