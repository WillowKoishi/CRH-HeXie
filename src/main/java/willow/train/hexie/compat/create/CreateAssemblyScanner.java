package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.vehicle.assembly.AssemblyRequest;
import willow.train.hexie.vehicle.assembly.AssemblyScanner;
import willow.train.hexie.vehicle.assembly.AssemblySelection;
import willow.train.hexie.vehicle.assembly.AssemblyTraversal;

/** Supplies Create block properties to our traversal; never invokes Create's assembly/scanning. */
public final class CreateAssemblyScanner implements AssemblyScanner {
    private Set<SuperGlueEntity> capturedGlue = Set.of();

    Set<SuperGlueEntity> capturedGlue() { return capturedGlue; }

    @Override
    public AssemblySelection scan(ServerLevel level, AssemblyRequest request) throws ScanException {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        capturedGlue = Set.of();
        for (BlockPos support : request.supports()) {
            if (!level.isLoaded(support)) throw new ScanException("Unloaded support: " + support);
            if (!(level.getBlockState(support).getBlock() instanceof HexieBogeyBlock<?>))
                throw new ScanException("Not a HeXie assembly support: " + support);
        }
        var source = new CreateAssemblyWorld(level, request.region());
        var traversal = new AssemblyTraversal<>(source, AllConfigs.server().kinetics.maxBlocksMoved.get(), 100000);
        var components = new ArrayList<AssemblySelection.Component>();
        var claimed = new HashSet<BlockPos>();
        try {
            for (BlockPos root : request.supports()) {
                if (claimed.contains(root)) continue;
                var blocks = traversal.scan(root);
                if (blocks.isEmpty()) throw new ScanException("Empty structure at " + root);
                for (BlockPos block : blocks)
                    if (claimed.contains(block)) throw new ScanException("Overlapping structures at " + block);
                claimed.addAll(blocks);
                var supports = blocks.stream()
                        .filter(pos -> level.getBlockState(pos).getBlock() instanceof HexieBogeyBlock<?>).toList();
                components.add(new AssemblySelection.Component(root, blocks, supports));
            }
        } catch (IllegalStateException exception) {
            throw new ScanException(exception.getMessage(), exception);
        }
        capturedGlue = source.capturedGlue();
        return new AssemblySelection(components);
    }
}
