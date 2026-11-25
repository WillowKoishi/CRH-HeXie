package willow.train.hexie.compat.create.bogey;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import willow.train.hexie.core.api.bogey.BogeyHandle;
import willow.train.hexie.core.resources.BogeyResources;

/** Bogey entries for the combined Create railway resource pack. */
public final class CreateBogeyResources {
    private CreateBogeyResources() {}

    public static Map<PackType, Map<ResourceLocation, byte[]>> resources() {
        return BogeyResources.resources();
    }

    public static Map<PackType, Map<ResourceLocation, byte[]>> resources(List<BogeyHandle> bogeys) {
        return BogeyResources.resources(bogeys);
    }
}
