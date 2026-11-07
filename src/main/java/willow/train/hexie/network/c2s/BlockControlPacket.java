package willow.train.hexie.network.c2s;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.api.interaction.*;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;

/** World/plot surface operations, including reachable consoles without an occupied seat. */
public record BlockControlPacket(BlockPos position, String controlId, int value, int phase,
                                 InteractionHand hand, Vec3 point) implements HexiePacket {
    public static final int PRESS = 0, BEGIN = 1, UPDATE = 2, END = 3;
    private record Drag(BlockPos position, String control, Block block, Object dimension,
                        InteractionHand hand, BlockHitResult hit) {}
    private static final Map<Player, Drag> DRAGS = new WeakHashMap<>();
    public BlockControlPacket(FriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readEnum(InteractionHand.class), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }
    @Override public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(position); buffer.writeUtf(controlId, 256); buffer.writeVarInt(value); buffer.writeVarInt(phase);
        buffer.writeEnum(hand); buffer.writeDouble(point.x); buffer.writeDouble(point.y); buffer.writeDouble(point.z);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) apply(player); });
        return true;
    }
    private void apply(ServerPlayer player) {
        if (phase == END) {
            var drag = DRAGS.get(player);
            if (drag != null && drag.position.equals(position) && drag.control.equals(controlId) && drag.hand == hand)
                DRAGS.remove(player);
            return;
        }
        // A fresh press replaces any previous capture, even when the new target rejects it.
        if (phase == PRESS || phase == BEGIN) DRAGS.remove(player);
        var level = player.serverLevel();
        if (phase < PRESS || phase > UPDATE || !player.isAlive() || player.isSpectator() || !player.mayBuild()
                || !Double.isFinite(point.lengthSqr()) || !level.hasChunkAt(position) || !level.mayInteract(player, position)) {
            DRAGS.remove(player); return;
        }
        var target = new BlockControlContext(level, position, null, 1);
        var state = target.state();
        var host = BlockControlHost.find(state);
        if (host == null || !host.allowsWorldControl() || host.nativeInteraction()) { DRAGS.remove(player); return; }
        var control = host.control(state, controlId);
        if (control == null || !control.accepts(value)) { DRAGS.remove(player); return; }
        var frame = SableInteractionCoordinates.logical(level, position);
        Vec3 eye = SableInteractionCoordinates.logicalWorld(level, player.getEyePosition());
        Vec3 requested = point;
        Drag activeDrag = null;
        if (phase == UPDATE) {
            activeDrag = DRAGS.get(player);
            if (!(control instanceof BlockControl.Handle) || activeDrag == null || !activeDrag.position.equals(position)
                    || !activeDrag.control.equals(controlId) || activeDrag.block != state.getBlock()
                    || activeDrag.dimension != level.dimension() || activeDrag.hand != hand) {
                DRAGS.remove(player); return;
            }
            requested = activeDrag.hit.getLocation();
        } else if (phase == BEGIN && !(control instanceof BlockControl.Handle)
                || phase == PRESS && !(control instanceof BlockControl.Button)) return;
        Vec3 worldPoint = frame.toWorld(requested);
        double reach = player.blockInteractionRange();
        if (eye.distanceToSqr(worldPoint) > reach * reach || eye.distanceToSqr(worldPoint) < 1e-12) {
            DRAGS.remove(player); return;
        }
        Vec3 storageEye = frame.toStorage(eye);
        Vec3 end = requested.add(requested.subtract(storageEye).normalize().scale(1e-4));
        // A drag remains captured after its handle moves; only the initial press performs part picking.
        var hit = phase == UPDATE ? null : host.pickControl(target, storageEye, end);
        if (phase != UPDATE && (hit == null || !hit.control().id().equals(controlId)
                || hit.blockHit().getLocation().distanceToSqr(requested) > 1e-4)) return;
        var obstruction = BlockControlPicking.query(1, () -> level.clip(new ClipContext(eye, worldPoint,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)));
        if (obstruction.getType() != HitResult.Type.MISS && !obstruction.getBlockPos().equals(position)
                && SableInteractionCoordinates.logicalDistanceSquared(level, eye, obstruction.getBlockPos(), obstruction.getLocation())
                    + 1e-6 < eye.distanceToSqr(worldPoint)) { DRAGS.remove(player); return; }
        if (phase == BEGIN) {
            DRAGS.put(player, new Drag(position.immutable(), controlId, state.getBlock(), level.dimension(), hand, hit.blockHit()));
            return;
        }
        var blockHit = activeDrag != null ? activeDrag.hit : hit.blockHit();
        host.operateControl(new BlockControlUse(target, player, hand, blockHit), control, value);
    }
}
