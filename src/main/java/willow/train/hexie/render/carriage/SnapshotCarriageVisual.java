package willow.train.hexie.render.carriage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.render.CarriageRenderBridge.CarriageBlockVisualView;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.DrivingConsoleEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.SS7DHeadEntityView;
import willow.train.hexie.render.bogey.BogeyRenderData;
import willow.train.hexie.render.coupler.SnapshotCouplerVisual;

/** Dynamic support parts share the same body embedding and existing registered Flywheel models. */
public final class SnapshotCarriageVisual extends ContraptionVisual<CarriageContraptionEntity> {
    private record Entry(BlockPos position, BlockEntity source, BlockState state, BogeyStyle style, BogeyVisual visual) {}
    private final List<Entry> bogeys = new ArrayList<>();
    private final PoseStack pose = new PoseStack();
    private final SnapshotCouplerVisual couplers;
    private final boolean compatibleTransparency = CarriageTransparencyCompat.required();
    private int compatibleStructureVersion = Integer.MIN_VALUE;

    public SnapshotCarriageVisual(VisualizationContext context, CarriageContraptionEntity entity, float partialTick) {
        super(context, entity, partialTick);
        couplers = new SnapshotCouplerVisual(embedding);
        lightPaddingBlocks = DEFAULT_LIGHT_PADDING + 2;
        animate(partialTick);
        updateCompatibleTransparency();
    }

    private SnapshotCarriageView snapshot() {
        return (SnapshotCarriageView) entity;
    }

    @Override
    protected <T extends BlockEntity> void setupVisualizer(T blockEntity, float partialTick) {
        // The static top remains in the body mesh; its dynamic frame/wheels are managed below.
        if (blockEntity instanceof AbstractBogeyBlockEntity && snapshot().bogeyAnimation().frames().stream()
                .anyMatch(frame -> frame.position().equals(blockEntity.getBlockPos()))) return;
        if (VisualizerRegistry.getVisualizer(blockEntity.getType()) != null) {
            super.setupVisualizer(blockEntity, partialTick);
            return;
        }

        // Create also calls this from its constructor. Only the inherited embedding and plans are ready.
        var visual = createCarriagePart(blockEntity, partialTick);
        if (visual == null) return;
        children.add(visual.asBlockEntityVisual());
        dynamicVisuals.add(visual, visual.planFrame());
        visual.attach();
    }

    private CarriageBlockVisualView createCarriagePart(BlockEntity block, float partial) {
        var render = HexieBridges.Client.blockEntityRender();
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(block);
        if (block instanceof CustomRenderedDoorEntityView door && door.isLowerSide(block.getBlockState())) {
            return render.matchesCarriageChildRenderer(block, renderer)
                    ? HexieBridges.Client.doorRender().createCarriageDoorVisual(embedding, door, partial) : null;
        }
        if (block instanceof DrivingConsoleEntityView console) {
            return render.matchesCarriageChildRenderer(block, renderer) ? render.createCarriageConsoleVisual(embedding, console) : null;
        }
        if (block instanceof SS7DHeadEntityView head) {
            return render.matchesCarriageChildRenderer(block, renderer)
                    ? HexieBridges.Client.render().createSS7DWiperVisual(embedding, head, partial) : null;
        }
        return render.createLegacyCarriagePartVisual(embedding, block, partial);
    }

    @Override
    protected void beginFrame(DynamicVisual.Context context) {
        if (entity.getContraption() == null) return;
        super.beginFrame(context);
        updateCompatibleTransparency();
        animate(context.partialTick());
    }

    private void updateCompatibleTransparency() {
        if (!compatibleTransparency || entity.getContraption() == null || structure == null
                || compatibleStructureVersion == lastStructureVersion) return;
        var model = CompatibleCarriageVisual.createCompatibleStructure(entity.getContraption().getOrCreateClientContraptionLazy());
        if (model != null) {
            embedding.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).stealInstance(structure);
            structure.setChanged();
        }
        compatibleStructureVersion = lastStructureVersion;
    }

    private void animate(float partialTick) {
        if (entity.getContraption() == null) return;
        var snapshot = snapshot();
        couplers.update(snapshot, partialTick);
        var client = entity.getContraption().getOrCreateClientContraptionLazy();
        var animation = snapshot.bogeyAnimation();
        for (int i = 0; i < animation.frames().size(); i++) {
            var frame = animation.frame(i, partialTick);
            var bogey = BogeyRenderData.resolve(client.getRenderLevel(), frame.position());
            var source = bogey == null ? null : bogey.source();
            var state = bogey == null ? null : bogey.state();
            BogeyStyle style = bogey == null ? null : bogey.style();
            Entry entry = i < bogeys.size() ? bogeys.get(i) : null;
            if (entry == null || !entry.position.equals(frame.position()) || entry.source != source
                    || entry.state != state || entry.style != style) {
                if (entry != null && entry.visual != null) entry.visual.delete();
                BogeyVisual visual = bogey == null ? null
                        : style.createVisual(bogey.block().getSize(), embedding, partialTick, true);
                entry = new Entry(frame.position(), source, state, style, visual);
                if (i < bogeys.size()) bogeys.set(i, entry);
                else bogeys.add(entry);
            }
            if (entry.visual == null) continue;
            pose.setIdentity();
            SnapshotBogeyTransforms.transform(pose, frame);
            // BogeyStyle.render supplies this translation in the non-instanced path.
            pose.translate(0, -1.5 - 1 / 128f, 0);
            entry.visual.updateLight(SnapshotBogeyRenderInputs.light(snapshot, frame, partialTick));
            try (var suspension = SnapshotBogeyTransforms.suspensionPose(bogey, frame);
                 var sample = HexieBridges.Client.createTrainClient().createAddonBogeyRenderer().snapshotSample(
                         frame.wheelAngle(), frame.speed(), partialTick)) {
                entry.visual.update(SnapshotBogeyRenderInputs.data(bogey, frame),
                        (float) (frame.wheelAngle() % 360), pose);
            }
        }
        while (bogeys.size() > animation.frames().size()) {
            var entry = bogeys.removeLast();
            if (entry.visual != null) entry.visual.delete();
        }
    }

    @Override
    protected void _delete() {
        for (var entry : bogeys) if (entry.visual != null) entry.visual.delete();
        bogeys.clear();
        couplers.delete();
        super._delete();
    }
}
