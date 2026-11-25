package willow.train.hexie.bridge.render;

import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.core.api.render.DoorModelLocations;

public interface BlockEntityViewsClientBridge {
    CustomRenderedDoorEntityOperations customRenderedDoorEntity();

    interface CustomRenderedDoorEntityOperations {
        DoorModelLocations getDoorModelLocations(BlockEntityViews.CustomRenderedDoorEntityView value, BlockState state);
    }

    OBJRenderableEndFaceEntityOperations objRenderableEndFaceEntity();

    interface OBJRenderableEndFaceEntityOperations {
        DoorModelLocations getDoorModelLocations(BlockEntityViews.OBJRenderableEndFaceEntityView value);
    }
}
