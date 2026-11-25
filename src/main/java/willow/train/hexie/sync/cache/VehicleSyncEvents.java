package willow.train.hexie.sync.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;

public final class VehicleSyncEvents {
    private VehicleSyncEvents() {}

    public static void install(ServerVehicleSync server) {
        Map<ServerLevel, SavedAssemblySync> stores = new HashMap<>();
        Consumer<ServerLevel> refresh = level -> stores.computeIfAbsent(level, key -> new SavedAssemblySync(server,
                HexieBridges.vehicle().carriageSnapshotStore().get(key))).refresh();
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && ServerVehicleSync.supports(p)) {
                p.server.getAllLevels().forEach(refresh);
                server.login(p);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) server.logout(p);
        });
        bus.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof SnapshotCarriageView
                    && ServerVehicleSync.supports(p)) {
                refresh.accept(p.serverLevel());
                server.tracking(p, e.getTarget().getUUID(), true);
            }
        });
        bus.addListener((PlayerEvent.StopTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof SnapshotCarriageView)
                server.tracking(p, e.getTarget().getUUID(), false);
        });
        bus.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                var store = stores.get(level);
                if (store != null) store.refresh();
            }
        });
        bus.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                var store = stores.remove(level);
                if (store != null) store.unload();
            }
        });
        bus.addListener((ServerStoppedEvent e) -> {
            stores.clear();
            server.stop();
        });
    }
}
