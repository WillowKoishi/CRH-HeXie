package willow.train.hexie.client.sign;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import willow.train.hexie.Main;
import willow.train.hexie.api.display.PassengerDisplayState;

/** Main-thread cache shared by every display on a tracked carriage, scoped to this client level. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class PassengerDisplayCache {
    private static final Map<UUID, PassengerDisplayState> STATES = new HashMap<>();
    private static Level owner;

    private PassengerDisplayCache() {}

    public static void accept(Level level, UUID carriage, PassengerDisplayState state) {
        if (!level.isClientSide) return;
        if (owner != level) {
            STATES.clear();
            owner = level;
        }
        STATES.put(carriage, state);
    }

    public static PassengerDisplayState read(Level level, UUID carriage) {
        return owner == level ? STATES.getOrDefault(carriage, PassengerDisplayState.EMPTY) : PassengerDisplayState.EMPTY;
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() == owner) STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() != owner) return;
        STATES.clear();
        owner = null;
    }
}
