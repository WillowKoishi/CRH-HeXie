package willow.train.hexie.blocks.locos.head;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blockentity.CRH2HeadEntity;

/** Shells are rendered above floor cells; normal block picking cannot hit their visible upper skin. */
public final class HeadNumberInteraction {
    private HeadNumberInteraction() {}
    public static BlockPos pick(Level level, Player player) {
        if (level != player.level()) return null;
        var eye = player.getEyePosition();
        var end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        end = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player)).getLocation();
        var range = new AABB(eye, end).inflate(2, 0, 2);
        var roots = new HashSet<BlockPos>();
        for (var pos : BlockPos.betweenClosed(
                (int) Math.floor(range.minX), (int) Math.floor(range.minY) - 4, (int) Math.floor(range.minZ),
                (int) Math.floor(range.maxX), (int) Math.floor(range.maxY), (int) Math.floor(range.maxZ))) {
            if (!level.hasChunkAt(pos)) continue;
            var root = HeadGeometry.owner(level, pos);
            if (root != null) roots.add(root.immutable());
        }
        BlockPos nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var root : roots) {
            var facing = level.getBlockState(root).getValue(CRH2HeadBlock.FACING);
            for (var local : HeadGeometry.boxes(facing)) {
                var hit = local.move(root).clip(eye, end);
                if (hit.isPresent() && hit.get().distanceToSqr(eye) < distance) {
                    distance = hit.get().distanceToSqr(eye); nearest = root;
                }
            }
        }
        return nearest;
    }
    public static InteractionResult open(Level level, Player player, BlockPos root) {
        if (root == null || level != player.level()) return InteractionResult.PASS;
        if (!player.mayBuild() || player.isSpectator()) return InteractionResult.FAIL;
        if (!(player.getMainHandItem().getItem() instanceof willow.train.hexie.item.editable_panels.ColoredBrush)
                && !(player.getOffhandItem().getItem() instanceof willow.train.hexie.item.editable_panels.ColoredBrush))
            return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(root) == null)
            level.setBlockEntity(new CRH2HeadEntity(root, level.getBlockState(root)));
        if (player instanceof ServerPlayer server && level.getBlockEntity(root) instanceof CRH2HeadEntity head)
            server.openMenu(head, buf -> { buf.writeBlockPos(root); buf.writeUtf(head.number(), 64); });
        return InteractionResult.CONSUME;
    }
}
