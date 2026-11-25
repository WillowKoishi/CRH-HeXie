package willow.train.hexie.render.door;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import willow.train.hexie.bridge.world.WorldBlockBridge.CustomRenderedDoorBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.OBJRenderableEndFaceBlockView;
import willow.train.hexie.core.api.render.DoorModelLocations;

public final class DoorPartialModelCache {
    private static final ConcurrentHashMap<ResourceLocation, PartialModel> MODELS = new ConcurrentHashMap<>();

    private DoorPartialModelCache() {}

    public static PartialModel get(ResourceLocation location) {
        return MODELS.computeIfAbsent(location, id -> PartialModel.of(id.withPrefix("block/")));
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (var block : BuiltInRegistries.BLOCK) {
            if (block instanceof CustomRenderedDoorBlockView door) {
                register(event, door.getLeftDoorModelLocations());
                register(event, door.getRightDoorModelLocations());
            } else if (block instanceof OBJRenderableEndFaceBlockView endFace) {
                register(event, endFace.getDoorModelLocations());
            }
        }
    }

    private static void register(ModelEvent.RegisterAdditional event, DoorModelLocations locations) {
        event.register(ModelResourceLocation.standalone(get(locations.first()).modelLocation()));
        event.register(ModelResourceLocation.standalone(get(locations.second()).modelLocation()));
    }
}
