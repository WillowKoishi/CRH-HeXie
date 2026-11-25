package willow.train.hexie.interaction.surface;

import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sync.SyncBridge.ControlInputPacketPhase;
import willow.train.hexie.bridge.sync.SyncBridge.ControlInputPacketTarget;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlInput;
import willow.train.hexie.core.api.interaction.ControlInputLayout;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.interaction.common.ControlInputTargets;

// split from willow/train/hexie/client/interaction/SurfaceInputTarget.java


/** A selected block, independent of whether its input is captured or directly clicked. */
final class SurfaceInputTarget implements SurfaceInteraction.Context {
    final DrivingConsoleContext cab;
    final Level level;
    final int vehicleId;
    final BlockPos position;
    final Block block;
    final BlockControlHost host;
    final Vec3 point;
    private final InteractionHand hand;
    private final ClientPacketListener connection;
    private final Object identity;
    private final java.util.UUID carrierUuid;

    private long pickedFrame = -1;
    private Vec3 pickedFrom, pickedTo;
    private Pick picked;

    private record Identity(Level level, int vehicle, BlockPos position, Block block, Object seat) {}

    SurfaceInputTarget(DrivingConsoleContext cab, BlockControlContext target, Vec3 point) {
        this(cab, target, point, InteractionHand.MAIN_HAND);
    }

    private SurfaceInputTarget(DrivingConsoleContext cab, BlockControlContext target, Vec3 point, InteractionHand hand) {
        var mc = Minecraft.getInstance();
        this.cab = cab;
        level = mc.level;
        connection = mc.getConnection();
        vehicleId = cab != null ? cab.seatAccess().vehicle().getId()
                : target.carrier() == null ? -1 : target.carrier().getId();
        carrierUuid = cab != null ? cab.seatAccess().vehicle().getUUID()
                : target.carrier() == null ? null : target.carrier().getUUID();
        position = target.position().immutable();
        block = target.state().getBlock();
        host = BlockControlHost.find(target.state());
        this.point = point;
        this.hand = hand;
        identity = new Identity(level, vehicleId, position, block, cab);
    }

    SurfaceInputTarget withHand(InteractionHand hand) {
        return new SurfaceInputTarget(cab, target(), point, hand);
    }

    BlockControlContext target() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != level) return null;
        float partial = SurfaceInputSources.partialTick();
        return cab != null ? ControlInputTargets.seated(cab, partial)
                : ControlInputTargets.world(mc.player, vehicleId, position, partial);
    }
    ControlInputLayout layout() {
        var target = target();
        return target == null ? ControlInputLayout.EMPTY : host.inputLayout(target);
    }
    boolean canCapture() {
        var target = target();
        var player = Minecraft.getInstance().player;
        return target != null && player != null && host.canCapture(new BlockControlUse(target, player,
                hand, BlockHitResult.miss(point, net.minecraft.core.Direction.UP, position)), cab != null);
    }
    @Override public Object identity() { return identity; }
    @Override public ControlState state() {
        var target = target();
        return cab != null ? cab.surfaceState() : target == null ? ControlState.EMPTY : host.surfaceState(target);
    }
    @Override public BlockControl control(String id) {
        var target = target();
        return target == null ? null : host.inputControl(target.state(), id);
    }
    @Override public boolean valid() {
        var mc = Minecraft.getInstance();
        if (mc.level != level || mc.getConnection() != connection || mc.player == null
                || !mc.player.isAlive() || mc.player.isSpectator()) return false;
        if (vehicleId >= 0 && (level.getEntity(vehicleId) == null
                || !Objects.equals(level.getEntity(vehicleId).getUUID(), carrierUuid))) return false;
        if (cab != null && (!cab.equals(DrivingConsoleContext.find(mc.player))
                || mc.player.getVehicle() != cab.seatAccess().vehicle())) return false;
        var target = target();
        if (target == null || !target.state().is(block)) return false;
        return cab != null || SurfacePointerQuery.renderWorld(target, point).distanceToSqr(SurfacePointerQuery.eye()) <= Math.pow(mc.player.blockInteractionRange(), 2);
    }
    @Override public void begin(BlockControl.Handle handle) {
        send(ControlInputPacketPhase.BEGIN, -1, new ControlInput(handle.id(), handle.notch(state()), ControlInput.Kind.SET, Set.of()));
    }
    @Override public void end(BlockControl.Handle handle) {
        send(ControlInputPacketPhase.END, -1, new ControlInput(handle.id(), 0, ControlInput.Kind.SET, SurfaceInputClient.heldFor(identity)));
    }
    @Override public void submit(BlockControl control, int value) {
        send(control instanceof BlockControl.Handle ? ControlInputPacketPhase.UPDATE : ControlInputPacketPhase.OPERATE, -1,
                new ControlInput(control.id(), value, control instanceof BlockControl.Handle
                        ? ControlInput.Kind.SET : ControlInput.Kind.PRESS, SurfaceInputClient.heldFor(identity)));
    }
    void send(ControlInputPacketPhase phase, int entryKey, ControlInput input) {
        send(phase, entryKey, input, 0);
    }
    void send(ControlInputPacketPhase phase, int entryKey, ControlInput input, long requestId) {
        if (Minecraft.getInstance().getConnection() != connection || connection == null
                || !connection.getConnection().isConnected()) return;
        HexieBridges.sync().sendControlInput(cab == null ? ControlInputPacketTarget.WORLD : ControlInputPacketTarget.SEATED,
                phase, vehicleId, position, entryKey, point, hand, input, requestId);
    }

    record Pick(SurfaceInputTarget target, ControlSurface.Pick control, double distance, ControlInputLayout.Entry entry) {
        Pick(SurfaceInputTarget target, ControlSurface.Pick control, double distance) {
            this(target, control, distance, null);
        }
    }
    Pick pick(Vec3 from, Vec3 to) {
        if (pickedFrame == SurfacePointerQuery.frame && from.equals(pickedFrom) && to.equals(pickedTo)) return picked;
        pickedFrame = SurfacePointerQuery.frame;
        pickedFrom = from;
        pickedTo = to;
        picked = null;
        var target = target();
        if (target == null) return null;
        var ray = SurfacePointerQuery.localRay(target, from, to);
        var hit = HexieBridges.interaction().pickControl(host, target, ray.from(), ray.to());
        if (hit == null) return null;
        var selected = new SurfaceInputTarget(cab, target, hit.blockHit().getLocation(), hand);
        double distance = SurfacePointerQuery.renderWorld(target, selected.point).distanceTo(from);
        return picked = new Pick(selected, new ControlSurface.Pick(hit.control(), distance, selected.point), distance);
    }

}
