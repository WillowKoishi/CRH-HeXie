package willow.train.hexie.blocks.track.standard;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import willow.train.hexie.Main;

import java.util.Set;

public class StandardTrackBlockStateGenerator extends SpecialBlockStateGen {
    private static final Set<String> BALLASTLESS_MODELS =
            Set.of("x_ortho", "z_ortho", "diag", "diag_2", "ascending", "teleport");
    private static final Set<String> TIELESS_MODELS =
            Set.of("x_ortho", "z_ortho", "diag", "diag_2", "ascending", "teleport",
                    "cross_d1_xo", "cross_d1_zo", "cross_d2_xo", "cross_d2_zo");

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        return state.getValue(TrackBlock.SHAPE).getModelRotation();
    }

    @Override
    public <T extends Block> ModelFile getModel(
            DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov, BlockState state) {
        TrackShape shape = state.getValue(StandardTrackBlock.SHAPE);
        if (shape == TrackShape.NONE) return prov.models().getExistingFile(prov.mcLoc("block/air"));

        TrackMaterial material = ((TrackBlock) ctx.getEntry()).getMaterial();
        String materialName = material.resourceName();
        String modelName = shape.getModel();
        boolean hasOwnModel = switch (materialName) {
            case "ballastless" -> BALLASTLESS_MODELS.contains(modelName);
            case "tieless" -> TIELESS_MODELS.contains(modelName);
            default -> false;
        };
        String parentMaterial = hasOwnModel ? materialName : "standard";
        // Keep generated wrappers separate from the authored geometry and OBJ models.
        // Writing the parent at its own location would create a model inheritance cycle.
        return prov.models()
                .withExistingParent(
                        "block/track/generated/" + materialName + "/" + modelName,
                        Main.asResource("block/track/" + parentMaterial + "/" + modelName))
                .texture("particle", material.particle);
    }
}
