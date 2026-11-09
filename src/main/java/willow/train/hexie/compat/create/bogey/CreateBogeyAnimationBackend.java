package willow.train.hexie.compat.create.bogey;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

/** Pre-bakes add-on models without exposing Flywheel to the add-on animation contract. */
public final class CreateBogeyAnimationBackend {
    private CreateBogeyAnimationBackend() {}
    private static final Map<ResourceLocation, PartialModel> MODELS = new ConcurrentHashMap<>();

    public static void register(ResourceLocation bogeyId, Set<ResourceLocation> models) {
        if (FMLEnvironment.dist != Dist.CLIENT)
            throw new IllegalStateException("Register bogey animation callbacks only on the client");
        ModList.get().getModContainerById(bogeyId.getNamespace())
                .orElseThrow(() -> new IllegalArgumentException("Bogey animation namespace is not a loaded mod: " + bogeyId));
        // PartialModel's own RegisterAdditional listener registers every declared model for baking.
        models.forEach(id -> MODELS.computeIfAbsent(id, PartialModel::of));
    }

    static PartialModel model(ResourceLocation id) {
        var model = MODELS.get(id);
        if (model == null) throw new IllegalArgumentException("Bogey part was not declared before model baking: " + id);
        return model;
    }
}
