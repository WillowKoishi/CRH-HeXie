package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.SpecialModels;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.foundation.utility.worldWrappers.WrappedBlockAndTintGetter;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.PosedInstance;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Articulated models live in the same local embedding as the carriage's structure. */
final class SnapshotCouplerVisual implements CouplerModelParts.Sink {
    private static final RendererReloadCache<PartialModel, Model> SHELL_PARTS = new RendererReloadCache<>(model ->
            new BakedModelBuilder(model.get()).materialFunc((layer, shaded, ao) -> {
                var material = ModelUtil.getMaterial(layer, false, false);
                if (willow.train.hexie.client.render.carriage.CarriageTransparencyCompat.required())
                    material = willow.train.hexie.client.render.carriage.CompatibleCarriageVisual
                            .withCompatibleTransparency(material, layer);
                return material == null ? null : SimpleMaterial.builderOf(material).light(LightShaders.SMOOTH)
                        .cardinalLightingMode(CardinalLightingMode.OFF).ambientOcclusion(false).build();
            }).build());
    private record Part(Model model, PosedInstance instance) {}
    private record Moving(BlockState state, Model model) {}
    private final VisualizationContext context;
    private final PoseStack pose = new PoseStack();
    private final List<Part> instances = new ArrayList<>();
    private final Map<BlockPos, Moving> movingModels = new HashMap<>();
    private final org.joml.Vector3f normalScratch = new org.joml.Vector3f();
    private int used, structureVersion = -1;
    int light;

    SnapshotCouplerVisual(VisualizationContext context) {
        this.context = context;
    }

    void update(SnapshotCarriageEntity entity, float partial) {
        var client = entity.getContraption().getOrCreateClientContraptionLazy();
        if (structureVersion != client.structureVersion()) {
            movingModels.clear();
            structureVersion = client.structureVersion();
        }
        used = 0;
        SnapshotCouplerRenderer.renderParts(entity, partial, pose, null, 0, this);
        while (instances.size() > used) instances.removeLast().instance.delete();
    }

    void moving(ClientContraption client, BlockPos pos, BlockState state, PoseStack stack) {
        var saved = movingModels.get(pos);
        if (saved == null || saved.state != state) {
            var world = client.getRenderLevel();
            var modelWorld = new WrappedBlockAndTintGetter(world) {
                @Override public BlockState getBlockState(BlockPos sample) {
                    return sample.equals(pos) ? state : super.getBlockState(sample);
                }
                @Override public ModelData getModelData(BlockPos sample) { return world.getModelData(sample); }
            };
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            saved = new Moving(state, new BakedModelBuilder(model).level(modelWorld).pos(pos).build());
            movingModels.put(pos, saved);
        }
        emit(saved.model, stack);
    }

    @Override public void draw(PartialModel model, PoseStack stack, PartialCouplerRenderer.BellowsDeformation deformation) {
        if (deformation == null) {
            emit(SpecialModels.smoothLit(model), stack);
        } else if (deformation.slopeX() == 0 && deformation.slopeY() == 0 && deformation.front(0, 0) < deformation.baseZ() - 1e-6) {
            // On straight track all contact points share one depth, hence one affine instance suffices.
            double base = deformation.baseZ(), front = deformation.frontZ();
            float stretch = (float) ((deformation.front(0, 0) - base) / (front - base));
            stack.pushPose();
            stack.translate(0, 0, base);
            stack.scale(1, 1, stretch);
            stack.translate(0, 0, -base);
            emit(SpecialModels.smoothLit(model), stack);
            stack.popPose();
        } else {
            for (var face : BellowsTriangles.forModel(model)) {
                PosedInstance instance = acquire(face.model());
                face.deform(instance, stack.last(), deformation, normalScratch);
                instance.setChanged();
            }
        }
    }

    @Override public void drawShell(PartialModel model, PoseStack stack) {
        emit(SHELL_PARTS.get(model), stack);
    }

    private void emit(Model model, PoseStack stack) {
        acquire(model).setTransform(stack).setChanged();
    }

    @Override public void cable(PartialModel model, PoseStack stack, CableDeformation deformation) {
        for (var face : CableTriangles.forModel(model)) {
            PosedInstance instance = acquire(face.model());
            face.deform(instance, stack.last(), deformation, normalScratch);
            instance.setChanged();
        }
    }

    private PosedInstance acquire(Model model) {
        Part part;
        if (used >= instances.size()) {
            part = new Part(model, context.instancerProvider().instancer(InstanceTypes.POSED, model).createInstance());
            instances.add(part);
        } else {
            part = instances.get(used);
            if (part.model != model) {
                context.instancerProvider().instancer(InstanceTypes.POSED, model).stealInstance(part.instance);
                part = new Part(model, part.instance);
                instances.set(used, part);
            }
        }
        used++;
        part.instance.light(light);
        return part.instance;
    }

    void delete() {
        for (var part : instances) part.instance.delete();
        instances.clear();
        movingModels.clear();
    }
}
