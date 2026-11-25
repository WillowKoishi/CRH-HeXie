package willow.train.hexie.vehicle.assembly;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import willow.train.hexie.bridge.HexieBridges;

// split from willow/train/hexie/vehicle/assembly/CouplerDataRegistry.java
@EventBusSubscriber(modid = "hexie")
final class CouplerDataEvents {
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "vehicle/couplers") {
            @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
                var json = new JsonObject();
                files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> json.add(e.getKey().toString(), e.getValue()));
                CouplerDataRegistry.install(json.toString());
            }
        });
    }

    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        var packet = CouplerDataRegistry.synchronizedJson;
        if (event.getPlayer() != null) HexieBridges.sync().sendCouplerDefinitionsToPlayer(event.getPlayer(), packet);
        else HexieBridges.sync().sendCouplerDefinitionsToAll(packet);
    }
}
