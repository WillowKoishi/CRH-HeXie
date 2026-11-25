package willow.train.hexie.core.api.interaction;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Reads current blocks in one storage space, including owners just outside the ray cells. */
public final class WorldInteractionTargets {
    // CRH1 head plates reach 3.1 blocks forward and the SS7D head reaches y=4.09375.
    public static final int OWNER_HALO = 4;

    private WorldInteractionTargets() {}

    public static void collect(Level level, Vec3 from, Vec3 to, Predicate<BlockState> target, Consumer<BlockPos> accept) {
        BlockPos min = BlockPos.containing(Math.min(from.x, to.x) - OWNER_HALO,
                Math.max(level.getMinBuildHeight(), Math.min(from.y, to.y) - OWNER_HALO),
                Math.min(from.z, to.z) - OWNER_HALO);
        BlockPos max = BlockPos.containing(Math.max(from.x, to.x) + OWNER_HALO,
                Math.min(level.getMaxBuildHeight() - 1, Math.max(from.y, to.y) + OWNER_HALO),
                Math.max(from.z, to.z) + OWNER_HALO);
        if (min.getY() > max.getY()) return;
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = min.getY() >> 4; sy <= max.getY() >> 4; sy++) {
                    var section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(target)) continue;
                    for (var pos : BlockPos.betweenClosed(Math.max(cx << 4, min.getX()),
                            Math.max(sy << 4, min.getY()), Math.max(cz << 4, min.getZ()),
                            Math.min((cx << 4) + 15, max.getX()), Math.min((sy << 4) + 15, max.getY()),
                            Math.min((cz << 4) + 15, max.getZ()))) {
                        if (target.test(section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)))
                            accept.accept(pos.immutable());
                    }
                }
            }
        }
    }
}
