package willow.train.hexie.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHost;
import willow.train.hexie.compat.create.ContraptionInteractionTargets;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** One finite local ray, shared by native candidates and indexed cross-cell surfaces. */
public final class CarriageControlPicking {
    private final AbstractContraptionEntity carrier;
    private final Contraption contraption;
    private final BlockGetter blocks;
    private final Vec3 from, to;
    private final float partial;
    private final Map<BlockPos, BlockHitResult> hits = new HashMap<>();

    public CarriageControlPicking(AbstractContraptionEntity carrier, Vec3 localFrom, Vec3 localTo, float partial) {
        this.carrier = carrier;
        contraption = carrier.getContraption();
        blocks = contraption.getContraptionWorld();
        from = localFrom;
        to = localTo;
        this.partial = partial;
    }

    /**
     * Defer precise surfaces to the final nearest comparison. Letting native DDA
     * stop immediately on an extended owner's distant surface could hide an
     * ordinary blocking block in the intervening cells.
     */
    public boolean replacesNative(BlockPos owner, BlockState state) {
        var host = BlockControlHost.find(state);
        if (host == null) return false;
        var hit = hit(owner);
        return host.nativeInteraction() || hit != null;
    }

    private BlockHitResult hit(BlockPos position) {
        if (hits.containsKey(position)) return hits.get(position);
        var info = contraption.getBlocks().get(position);
        var host = info == null ? null : BlockControlHost.find(info.state());
        var picked = host == null || contraption.isHiddenInPortal(position) ? null
                : host.pickControl(new BlockControlContext(blocks, position, carrier, partial), from, to);
        var hit = picked == null ? null : picked.blockHit();
        hits.put(position.immutable(), hit);
        return hit;
    }

    public BlockHitResult nearest(BlockHitResult nativeHit) {
        for (var owner : ContraptionInteractionTargets.query(contraption, from, to)) hit(owner);
        // This compact pose list already exists; no block-map or loaded-carrier scan.
        if (carrier instanceof SnapshotCarriageEntity snapshot)
            for (var frame : snapshot.couplerAnimation().frames()) hit(frame.position());
        BlockHitResult nearest = nativeHit;
        double distance = nearest == null ? Double.POSITIVE_INFINITY : nearest.getLocation().distanceToSqr(from);
        for (var candidate : hits.values()) {
            if (candidate == null) continue;
            double next = candidate.getLocation().distanceToSqr(from);
            if (next < distance) {
                distance = next;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
