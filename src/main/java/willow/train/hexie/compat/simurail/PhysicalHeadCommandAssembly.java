package willow.train.hexie.compat.simurail;

import java.util.HashSet;
import java.util.LinkedHashSet;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.blocks.emu.head.HeadFloorBlock;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructures;

/** Ordinary Sable assembly selects complete fishheads; explicit shatter commands retain their own semantics. */
public final class PhysicalHeadCommandAssembly {
    private static final DynamicCommandExceptionType INCOMPLETE = new DynamicCommandExceptionType(pos ->
            Component.translatableWithFallback("hexie.command.physical_head.incomplete",
                    "The physical fishhead at %s is incomplete or not fully loaded.", pos));

    public record Selection(Iterable<BlockPos> blocks, BoundingBox3ic bounds) {}

    private PhysicalHeadCommandAssembly() {}

    public static Selection includeWholeHeads(ServerLevel level, Iterable<BlockPos> blocks, BoundingBox3ic bounds)
            throws CommandSyntaxException {
        var selected = new LinkedHashSet<BlockPos>();
        blocks.forEach(pos -> selected.add(pos.immutable()));
        var expanded = new LinkedHashSet<>(selected);
        var checkedRoots = new HashSet<BlockPos>();
        var expandedBounds = new BoundingBox3i(bounds);
        for (var pos : selected) {
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PhysicalHeadBlock)
                    && !(state.getBlock() instanceof PhysicalHeadBogeyBlock)
                    && !(state.getBlock() instanceof HeadFloorBlock)) continue;
            var root = PartStructures.resolve(level, pos, state);
            if (root == null) {
                if (state.getBlock() instanceof PhysicalHeadBlock || state.getBlock() instanceof PhysicalHeadBogeyBlock)
                    throw INCOMPLETE.create(pos.toShortString());
                continue;
            }
            if (!(root.structure() instanceof PhysicalHeadBlock) || !checkedRoots.add(root.pos())) continue;
            if (!PartInstallation.complete(level, root.pos(), root.state(), root.structure()))
                throw INCOMPLETE.create(root.pos().toShortString());
            // Completeness is checked before adding any cell, so unrelated occupants are never captured.
            for (var member : root.structure().partCells(level, root.pos(), root.state()).keySet()) {
                if (expanded.add(member)) expandedBounds.expandTo(member.getX(), member.getY(), member.getZ());
            }
        }
        return new Selection(expanded, expandedBounds);
    }
}
