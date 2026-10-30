package willow.train.hexie.client.render.carriage;

import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionVisual;
import com.simibubi.create.foundation.utility.worldWrappers.WrappedBlockAndTintGetter;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.baked.BlockModelBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.Main;

import java.util.ArrayList;
import java.util.List;

/** Uses ordinary alpha blending for HeXie's glass while retaining the existing Flywheel visual. */
public final class CompatibleCarriageVisual extends CarriageContraptionVisual {
    private int compatibleStructureVersion;

    CompatibleCarriageVisual(VisualizationContext context, CarriageContraptionEntity entity, float partialTick) {
        super(context, entity, partialTick);
        rebuildCompatibleStructure();
    }

    static boolean needsCompatibility(CarriageContraptionEntity entity) {
        var contraption = entity.getContraption();
        if (contraption == null) return false;
        var client = contraption.getOrCreateClientContraptionLazy();
        var blocks = client.getRenderedBlocks();
        BlockAndTintGetter modelWorld = modelWorld(client, blocks);
        RandomSource random = RandomSource.create();
        for (BlockPos pos : blocks.positions()) {
            if (isHeXieTranslucent(blocks.lookup().apply(pos), pos, modelWorld, random)) return true;
        }
        return false;
    }

    @Override
    public void beginFrame(DynamicVisual.Context context) {
        super.beginFrame(context);
        // Create updates its own structure first. Change its material mapping only on rebuild,
        // including door changes and portal visibility changes, rather than baking every frame.
        if (compatibleStructureVersion != lastStructureVersion) rebuildCompatibleStructure();
    }

    private void rebuildCompatibleStructure() {
        Model model = createCompatibleStructure(entity.getContraption().getOrCreateClientContraptionLazy());
        if (model != null) {
            embedding.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).stealInstance(structure);
            structure.setChanged();
        }
        compatibleStructureVersion = lastStructureVersion;
    }

    /** Shared by native and independently animated HeXie carriages, only on structure rebuild. */
    public static Model createCompatibleStructure(ClientContraption client) {
        var blocks = client.getRenderedBlocks();
        BlockAndTintGetter modelWorld = modelWorld(client, blocks);
        List<BlockPos> ordinary = new ArrayList<>();
        List<BlockPos> heXieGlass = new ArrayList<>();
        RandomSource random = RandomSource.create();
        for (BlockPos pos : blocks.positions()) {
            (isHeXieTranslucent(blocks.lookup().apply(pos), pos, modelWorld, random)
                    ? heXieGlass : ordinary).add(pos.immutable());
        }

        if (heXieGlass.isEmpty()) return null;
        List<Model.ConfiguredMesh> meshes = new ArrayList<>();
        if (!ordinary.isEmpty()) {
            meshes.addAll(new BlockModelBuilder(modelWorld, ordinary)
                    .materialFunc(CompatibleCarriageVisual::originalMaterial).build().meshes());
        }
        if (!heXieGlass.isEmpty()) {
            meshes.addAll(new BlockModelBuilder(modelWorld, heXieGlass)
                    .materialFunc(CompatibleCarriageVisual::compatibleMaterial).build().meshes());
        }
        return new SimpleModel(meshes);
    }

    private static BlockAndTintGetter modelWorld(ClientContraption client, ClientContraption.RenderedBlocks blocks) {
        return new WrappedBlockAndTintGetter(client.getRenderLevel()) {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return blocks.lookup().apply(pos);
            }
        };
    }

    private static boolean isHeXieTranslucent(BlockState state, BlockPos pos,
            BlockAndTintGetter modelWorld, RandomSource random) {
        if (state.getRenderShape() != RenderShape.MODEL
                || !Main.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return false;
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(modelWorld, pos, state, modelWorld.getModelData(pos));
        random.setSeed(state.getSeed(pos));
        return model.getRenderTypes(state, random, data).contains(RenderType.translucent());
    }

    private static Material originalMaterial(RenderType renderType, boolean shaded, boolean ambientOcclusion) {
        Material material = ModelUtil.getMaterial(renderType, shaded, ambientOcclusion);
        if (material != null && material.cardinalLightingMode() == CardinalLightingMode.ENTITY) {
            return SimpleMaterial.builderOf(material).cardinalLightingMode(CardinalLightingMode.CHUNK).build();
        }
        return material;
    }

    private static Material compatibleMaterial(RenderType renderType, boolean shaded, boolean ambientOcclusion) {
        Material material = originalMaterial(renderType, shaded, ambientOcclusion);
        if (material != null && renderType == RenderType.translucent()) {
            // Keep original alpha, lighting and cutout settings. Bypass only the OIT framebuffer
            // combination that loses these glass meshes with Sable and Veil on affected renderers.
            return SimpleMaterial.builderOf(material).transparency(Transparency.TRANSLUCENT).build();
        }
        return material;
    }
}
