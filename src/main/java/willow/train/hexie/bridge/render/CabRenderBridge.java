package willow.train.hexie.bridge.render;

import java.util.List;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import willow.train.hexie.core.api.interaction.BlockControl;

public interface CabRenderBridge {
    CabSliderTrackOperations cabSliderTrack();

    interface CabSliderTrackOperations {
        List<Integer> interactiveNotches(BlockControl.Handle handle);
        CabSliderTrackView at(BlockControl.Handle handle, int notch, double pointer, int extent);
    }

    CabViewRotationOperations cabViewRotation();

    interface CabViewRotationOperations {
        CabViewRotationView create();
        Vector3f cameraAngles(Quaternionfc rotation);
    }

    Ctcs2DmiModuleShellOperations ctcs2DmiModuleShell();

    interface Ctcs2DmiModuleShellOperations {
        ModelResourceLocation model(Ctcs2DmiModuleShell value);
    }

    interface CabSliderTrackView {
        BlockControl.Handle.DragAxis axis();
        int top();
        int travel();
        List<Integer> notches();
        double coordinate(int notch);
        int nearest(double coordinate);
    }

    interface CabViewRotationView {
        void start(Quaternionfc frameRotation, float worldYaw, float worldPitch);
        void turn(double horizontal, double vertical);
        Quaternionf world(Quaternionfc cabRotation);
    }

    enum Ctcs2DmiModuleShell {
        GREY, BLACK
    }
}
