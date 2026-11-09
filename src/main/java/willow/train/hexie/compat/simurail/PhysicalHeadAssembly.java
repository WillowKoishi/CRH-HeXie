package willow.train.hexie.compat.simurail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import dev.simulated_team.simulated.index.SimBlockMovementChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.emu.head.HeadFloorBlock;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructures;

/** Keep one physical fishhead together when Simulated discovers its movable structure. */
public final class PhysicalHeadAssembly {
    private static boolean registered;

    private PhysicalHeadAssembly() {}

    public static void register() {
        if (registered) return;
        registered = true;
        SimBlockMovementChecks.registerAdditionalBlocks(PhysicalHeadAssembly::additionalBlocks);
    }

    private static Iterable<BlockPos> additionalBlocks(BlockState state, Level level, BlockPos pos, Set<BlockPos> visited) {
        if (!candidate(state)) return List.of();
        var root = PartStructures.resolve(level, pos, state);
        if (root == null || !(root.structure() instanceof PhysicalHeadBlock)) return List.of();

        var members = new ArrayList<BlockPos>();
        for (var cell : root.structure().partCells(level, root.pos(), root.state()).entrySet()) {
            if (!visited.contains(cell.getKey()) && level.hasChunkAt(cell.getKey())
                    && root.structure().ownsCell(level, root.pos(), root.state(), cell.getKey(), cell.getValue()))
                members.add(cell.getKey());
        }
        // The native frontier still validates each member's movement rules and the assembly size limit.
        return members;
    }

    /** Reject a damaged or partly unloaded head before Simulated starts moving any of its cells. */
    public static boolean movementAllowed(BlockState state, Level level, BlockPos pos) {
        if (!candidate(state)) return true;
        var root = PartStructures.resolve(level, pos, state);
        if (root == null) return !(state.getBlock() instanceof PhysicalHeadBlock)
                && !(state.getBlock() instanceof PhysicalHeadBogeyBlock);
        return !(root.structure() instanceof PhysicalHeadBlock)
                || PartInstallation.complete(level, root.pos(), root.state(), root.structure());
    }

    private static boolean candidate(BlockState state) {
        return state.getBlock() instanceof PhysicalHeadBlock || state.getBlock() instanceof PhysicalHeadBogeyBlock
                || state.getBlock() instanceof HeadFloorBlock;
    }
}
