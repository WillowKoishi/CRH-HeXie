package willow.train.hexie.compat.create.assembly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageDisassembly {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageDisassembly(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    private StructureTransform nativeDisassemblyTransform;

    /** Capture the live view before actors stop; native Create code owns the actual restoration. */
    public NativeDisassemblyPlan prepareNativeDisassembly(StructureTransform transform) {
        if (!(carriageEntity.getContraption() instanceof SnapshotCarriageStructure.SnapshotView view)) throw new IllegalStateException("Missing carriage view");
        if (carriageEntity.level().isClientSide || view.disassembled || !carriageEntity.isAlive())
            throw new IllegalStateException("Carriage cannot begin native disassembly");
        var fixedTransform = new StructureTransform(transform.offset, transform.rotationAxis,
                transform.rotation, transform.mirror);
        return new NativeDisassemblyPlan(view, fixedTransform);
    }

    public final class NativeDisassemblyPlan {
        private final SnapshotCarriageStructure.SnapshotView view;
        private final StructureTransform transform;
        private final CompoundTag working;
        private final Map<BlockPos, StructureBlockInfo> originalBlocks;
        private final Map<BlockPos, List<BlockPos>> originalMultiblocks;
        private final List<AABB> originalGlue;
        private final List<AABB> targetGlue;
        private final List<SuperGlueEntity> installedGlue = new ArrayList<>();
        private final boolean originallyDisassembled;
        private boolean attempted, installed, rolledBack, finished;

        private NativeDisassemblyPlan(SnapshotCarriageStructure.SnapshotView view, StructureTransform transform) {
            this.view = view;
            this.transform = transform;
            working = carriageEntity.writeWorkingState();
            originalBlocks = view.copyBlocks();
            originalMultiblocks = view.multiblockPositions();
            originalGlue = view.glue();
            originallyDisassembled = view.disassembled;
            targetGlue = originalGlue.stream().map(box -> new AABB(
                    transform.apply(new Vec3(box.minX, box.minY, box.minZ)),
                    transform.apply(new Vec3(box.maxX, box.maxY, box.maxZ)))).toList();
        }

        /** Invoke once after all selected carriers stop, inside the world's restore transaction. */
        public void install() {
            if (carriageEntity.getContraption() != view || attempted || rolledBack || finished)
                throw new IllegalStateException("Native disassembly plan is no longer installable");
            // Actor shutdown or another native installation may have changed the world
            // since preparation. Reject while the caller can still roll back the transaction.
            attempted = true;
            // stopMoving may have written final actor data. Keep the live view and storage so
            // third-party Create hooks see the same contraption that was moving in the world.
            for (var entry : carriageEntity.actorBlockData().entrySet()) {
                var info = view.getBlocks().get(entry.getKey());
                if (info != null) view.getBlocks().put(info.pos(), new StructureBlockInfo(info.pos(), info.state(),
                        entry.getValue() == null ? null : entry.getValue().copy()));
            }
            view.getBlocks().replaceAll((position, info) -> carriageEntity.withCabControls(info));
            view.bindMultiblocks(originalMultiblocks);
            Set<UUID> existingGlue = new HashSet<>();
            matchingGlue().forEach(glue -> existingGlue.add(glue.getUUID()));
            try {
                view.addBlocksToWorld(carriageEntity.level(), transform);
                installed = true;
            } finally {
                for (var glue : matchingGlue())
                    if (!existingGlue.contains(glue.getUUID())) installedGlue.add(glue);
            }
        }

        /** The caller restores destination world cells; this restores only the live source. */
        public void rollback() {
            if (finished) throw new IllegalStateException("Committed native disassembly cannot roll back");
            if (rolledBack) return;
            if (carriageEntity.getContraption() != view) throw new IllegalStateException("Native disassembly view was replaced");
            installedGlue.forEach(SuperGlueEntity::discard);
            view.restoreBlocks(originalBlocks);
            view.setGlue(originalGlue);
            view.disassembled = originallyDisassembled;
            carriageEntity.readWorkingState(working);
            // readWorkingState replaces actor records, so rebuild group references afterwards.
            view.bindMultiblocks(originalMultiblocks);
            rolledBack = true;
        }

        /** Only after every selected carriage's world installation has committed. */
        public void finish() {
            if (!installed || rolledBack || finished || carriageEntity.getContraption() != view)
                throw new IllegalStateException("Native disassembly has not committed an installation");
            finished = true;
            nativeDisassemblyTransform = transform;
            try { carriageEntity.baseDisassemble(); }
            finally { nativeDisassemblyTransform = null; }
        }

        private List<SuperGlueEntity> matchingGlue() {
            if (targetGlue.isEmpty()) return List.of();
            AABB bounds = targetGlue.getFirst();
            for (int i = 1; i < targetGlue.size(); i++) bounds = bounds.minmax(targetGlue.get(i));
            return carriageEntity.level().getEntitiesOfClass(SuperGlueEntity.class, bounds,
                    glue -> targetGlue.contains(glue.getBoundingBox()));
        }
    }

    public void disassemble() { /* Only our station transaction restores blocks. */ }

    public StructureTransform makeStructureTransform() {
        return nativeDisassemblyTransform != null ? nativeDisassemblyTransform : carriageEntity.baseMakeStructureTransform();
    }
}
