package willow.train.hexie.compat.create.assembly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblyRequestView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblySelectionComponentView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.AssemblySelectionView;

/** Supplies Create block properties to our traversal; never invokes Create's assembly/scanning. */
public final class CreateAssemblyScanner {
    private Set<SuperGlueEntity> capturedGlue = Set.of();

    Set<SuperGlueEntity> capturedGlue() { return capturedGlue; }

    public static final class ScanException extends Exception {
        public ScanException(String message) { super(message); }
        public ScanException(String message, Throwable cause) { super(message, cause); }
    }

    public AssemblySelectionView scan(ServerLevel level, AssemblyRequestView request) throws ScanException {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        capturedGlue = Set.of();
        for (BlockPos support : request.supports()) {
            if (!level.isLoaded(support)) throw new ScanException("Unloaded support: " + support);
            if (!HexieBridges.vehicle().assemblyBogeySupport().isSupport(level, support))
                throw new ScanException("Not a HeXie assembly support: " + support);
        }
        var source = new CreateAssemblyWorld(level, request.region());
        var traversal = HexieBridges.vehicle().createAssemblyTraversal(source, AllConfigs.server().kinetics.maxBlocksMoved.get(), 100000);
        var components = new ArrayList<AssemblySelectionComponentView>();
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
                        .filter(pos -> HexieBridges.vehicle().assemblyBogeySupport().isSupport(level, pos)).toList();
                components.add(HexieBridges.vehicle().createAssemblySelectionComponent(root, blocks, supports));
            }
        } catch (IllegalStateException exception) {
            throw new ScanException(exception.getMessage(), exception);
        }
        capturedGlue = source.capturedGlue();
        return HexieBridges.vehicle().createAssemblySelection(components);
    }
}
