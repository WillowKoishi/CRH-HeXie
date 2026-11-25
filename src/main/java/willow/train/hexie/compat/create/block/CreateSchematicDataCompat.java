package willow.train.hexie.compat.create.block;

import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import java.util.List;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.interaction.HexieSchematicData;

public final class CreateSchematicDataCompat {
    private CreateSchematicDataCompat() {}

    public static void register() {
        var entities = HexieBridges.worldRegistry().blockEntitiesInit();
        for (var type : List.of(entities.customRenderedDoorEntity(), entities.objRenderableEndFaceBlockEntity(),
                entities.cr200jFrontBlockEntity(), entities.crh1aHead(), entities.crh2aHead(), entities.crh2aPart(),
                entities.carriageNoSign(), entities.carriageTypeSign(), entities.groundStationSign(),
                entities.laqueredBoard(), entities.trainSpeedSign(), entities.ss7dHead())) {
            SafeNbtWriterRegistry.REGISTRY.register(type, (entity, tag, registries) ->
                    ((HexieSchematicData) entity).writeSafe(tag, registries));
        }
    }
}
