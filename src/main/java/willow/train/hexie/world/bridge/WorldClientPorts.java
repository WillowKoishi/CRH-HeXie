package willow.train.hexie.world.bridge;

import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.render.BlockEntityViewsClientBridge;
import willow.train.hexie.bridge.render.WorldRegistryClientBridge;
import willow.train.hexie.world.blockentity.door.CustomRenderedDoorEntity;
import willow.train.hexie.world.blockentity.door.OBJRenderableEndFaceEntity;
import willow.train.hexie.world.registry.core.ModelInit;

final class WorldClientPorts implements BlockEntityViewsClientBridge, WorldRegistryClientBridge,
        WorldRegistryClientBridge.ModelInitOperations {
    static void install() {
        var ports = new WorldClientPorts();
        HexieBridges.Client.registerWorldRegistryClient(ports);
        HexieBridges.Client.registerBlockEntityViewsClient(ports);
    }

    @Override public ModelInitOperations modelInit() { return this; }
    @Override public List<ResourceLocation> unbaked() { return ModelInit.UNBAKED; }
    @Override public Map<ModelResourceLocation, BakedModel> baked() { return ModelInit.BAKED; }

    @Override public void setBaked(Map<ModelResourceLocation, BakedModel> models) { ModelInit.BAKED = models; }

    @Override public CustomRenderedDoorEntityOperations customRenderedDoorEntity() {
        return (entity, state) -> ((CustomRenderedDoorEntity) entity.asBlockEntity()).getDoorModelLocations(state);
    }

    @Override public OBJRenderableEndFaceEntityOperations objRenderableEndFaceEntity() {
        return entity -> ((OBJRenderableEndFaceEntity) entity.asBlockEntity()).getDoorModelLocations();
    }
}
