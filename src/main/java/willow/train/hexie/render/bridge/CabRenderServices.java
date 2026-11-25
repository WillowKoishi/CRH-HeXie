package willow.train.hexie.render.bridge;

import java.util.List;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import willow.train.hexie.bridge.render.CabRenderBridge;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.render.cab.CabSliderTrack;
import willow.train.hexie.render.cab.CabViewRotation;
import willow.train.hexie.render.cab.Ctcs2DmiModule;

final class CabRenderServices implements CabRenderBridge {
    private final CabSliderTrackOperations cabSliderTrack = new CabSliderTrackOperations() {
        @Override public List<Integer> interactiveNotches(BlockControl.Handle handle) {
            return CabSliderTrack.interactiveNotches(handle);
        }
        @Override public CabSliderTrackView at(BlockControl.Handle handle, int notch, double pointer, int extent) {
            return CabSliderTrack.at(handle, notch, pointer, extent);
        }
    };

    private final CabViewRotationOperations cabViewRotation = new CabViewRotationOperations() {
        @Override public CabViewRotationView create() {
            return new CabViewRotation();
        }
        @Override public Vector3f cameraAngles(Quaternionfc rotation) {
            return CabViewRotation.cameraAngles(rotation);
        }
    };

    private final Ctcs2DmiModuleShellOperations ctcs2DmiModuleShell = new Ctcs2DmiModuleShellOperations() {
        @Override public ModelResourceLocation model(Ctcs2DmiModuleShell value) {
            return Ctcs2DmiModule.Shell.valueOf(value.name()).model();
        }
    };

    @Override public CabSliderTrackOperations cabSliderTrack() {
        return cabSliderTrack;
    }

    @Override public CabViewRotationOperations cabViewRotation() {
        return cabViewRotation;
    }

    @Override public Ctcs2DmiModuleShellOperations ctcs2DmiModuleShell() {
        return ctcs2DmiModuleShell;
    }
}
