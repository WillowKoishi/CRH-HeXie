package willow.train.hexie.client.render;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import net.neoforged.fml.ModList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.client.render.carriage.CompatibleCarriageVisual;

/** Dynamic support parts share the same body embedding and existing registered Flywheel models. */
public final class SnapshotCarriageVisual extends ContraptionVisual<SnapshotCarriageEntity> {
    private record Entry(BlockPos position, AbstractBogeyBlockEntity blockEntity, BogeyStyle style, BogeyVisual visual) {}
    private final List<Entry> bogeys = new ArrayList<>();
    private final PoseStack pose = new PoseStack();
    private final boolean compatibleTransparency = ModList.get().isLoaded("sable") && ModList.get().isLoaded("veil");
    private int compatibleStructureVersion = Integer.MIN_VALUE;

    public SnapshotCarriageVisual(VisualizationContext context, SnapshotCarriageEntity entity, float partialTick) {
        super(context, entity, partialTick);
        lightPaddingBlocks = DEFAULT_LIGHT_PADDING + 2;
        animate(partialTick);
        updateCompatibleTransparency();
    }

    @Override
    protected <T extends BlockEntity> void setupVisualizer(T blockEntity, float partialTick) {
        // The static top remains in the body mesh; its dynamic frame/wheels are managed below.
        if (!(blockEntity instanceof AbstractBogeyBlockEntity)
                || entity.bogeyAnimation().frames().stream().noneMatch(frame -> frame.position().equals(blockEntity.getBlockPos())))
            super.setupVisualizer(blockEntity, partialTick);
    }

    @Override
    protected void beginFrame(DynamicVisual.Context context) {
        super.beginFrame(context);
        updateCompatibleTransparency();
        animate(context.partialTick());
    }

    private void updateCompatibleTransparency() {
        if (!compatibleTransparency || compatibleStructureVersion == lastStructureVersion) return;
        var model = CompatibleCarriageVisual.createCompatibleStructure(entity.getContraption().getOrCreateClientContraptionLazy());
        if (model != null) {
            embedding.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).stealInstance(structure);
            structure.setChanged();
        }
        compatibleStructureVersion = lastStructureVersion;
    }

    private void animate(float partialTick) {
        if (entity.getContraption() == null) return;
        var client = entity.getContraption().getOrCreateClientContraptionLazy();
        var animation = entity.bogeyAnimation();
        for (int i = 0; i < animation.frames().size(); i++) {
            var frame = animation.frame(i, partialTick);
            var raw = client.getBlockEntity(frame.position());
            AbstractBogeyBlockEntity be = raw instanceof AbstractBogeyBlockEntity bogey ? bogey : null;
            BogeyStyle style = be == null ? null : be.getStyle();
            Entry entry = i < bogeys.size() ? bogeys.get(i) : null;
            if (entry == null || !entry.position.equals(frame.position()) || entry.blockEntity != be || entry.style != style) {
                if (entry != null && entry.visual != null) entry.visual.delete();
                BogeyVisual visual = be != null && be.getBlockState().getBlock() instanceof AbstractBogeyBlock<?> block
                        ? style.createVisual(block.getSize(), embedding, partialTick, true) : null;
                entry = new Entry(frame.position(), be, style, visual);
                if (i < bogeys.size()) bogeys.set(i, entry);
                else bogeys.add(entry);
            }
            if (entry.visual == null) continue;
            pose.setIdentity();
            SnapshotCarriageRenderer.transform(pose, frame);
            // BogeyStyle.render supplies this translation in the non-instanced path.
            pose.translate(0, -1.5 - 1 / 128f, 0);
            entry.visual.updateLight(SnapshotCarriageRenderer.light(entity, frame, partialTick));
            entry.visual.update(SnapshotCarriageRenderer.data(entity, be, frame),
                    (float) (frame.wheelAngle() % 360), pose);
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
        super._delete();
    }
}
