package willow.train.hexie.client.interaction;

import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.api.interaction.*;
import willow.train.hexie.interaction.ControlInputTargets;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.ControlInputPacket;
import willow.train.hexie.network.c2s.ControlInputPacket.Phase;
import willow.train.hexie.network.c2s.ControlInputPacket.Target;

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

    private static long frame;
    private static PointedQuery pointedQuery;
    private long pickedFrame = -1;
    private Vec3 pickedFrom, pickedTo;
    private Pick picked;

    private static final class PointedQuery {
        final Object nativeHit;
        final Vec3 from, to;
        final SurfaceInputSources.Query targets;
        boolean controlsResolved;
        Pick control;

        PointedQuery(Object nativeHit, Vec3 from, Vec3 to, SurfaceInputSources.Query targets) {
            this.nativeHit = nativeHit;
            this.from = from;
            this.to = to;
            this.targets = targets;
        }
    }

    static void beginFrame() {
        ++frame;
        pointedQuery = null;
    }

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
        return cab != null || renderWorld(target, point).distanceToSqr(eye()) <= Math.pow(mc.player.blockInteractionRange(), 2);
    }
    @Override public void begin(BlockControl.Handle handle) {
        send(Phase.BEGIN, -1, new ControlInput(handle.id(), handle.notch(state()), ControlInput.Kind.SET, Set.of()));
    }
    @Override public void end(BlockControl.Handle handle) {
        send(Phase.END, -1, new ControlInput(handle.id(), 0, ControlInput.Kind.SET, Set.of()));
    }
    @Override public void submit(BlockControl control, int value) {
        send(control instanceof BlockControl.Handle ? Phase.UPDATE : Phase.OPERATE, -1,
                new ControlInput(control.id(), value, control instanceof BlockControl.Handle
                        ? ControlInput.Kind.SET : ControlInput.Kind.PRESS, SurfaceInputClient.heldFor(identity)));
    }
    void send(Phase phase, int entryKey, ControlInput input) {
        send(phase, entryKey, input, 0);
    }
    void send(Phase phase, int entryKey, ControlInput input, long requestId) {
        if (Minecraft.getInstance().getConnection() != connection || connection == null
                || !connection.getConnection().isConnected()) return;
        HexieNetworkHandler.sendToServer(new ControlInputPacket(cab == null ? Target.WORLD : Target.SEATED,
                phase, vehicleId, position, entryKey, point, hand, input, requestId));
    }

    record Pick(SurfaceInputTarget target, ControlSurface.Pick control, double distance, ControlInputLayout.Entry entry) {
        Pick(SurfaceInputTarget target, ControlSurface.Pick control, double distance) {
            this(target, control, distance, null);
        }
    }
    Pick pick(Vec3 from, Vec3 to) {
        if (pickedFrame == frame && from.equals(pickedFrom) && to.equals(pickedTo)) return picked;
        pickedFrame = frame;
        pickedFrom = from;
        pickedTo = to;
        picked = null;
        var target = target();
        if (target == null) return null;
        var ray = localRay(target, from, to);
        var hit = host.pickControl(target, ray.from(), ray.to());
        if (hit == null) return null;
        var selected = new SurfaceInputTarget(cab, target, hit.blockHit().getLocation(), hand);
        double distance = renderWorld(target, selected.point).distanceTo(from);
        return picked = new Pick(selected, new ControlSurface.Pick(hit.control(), distance, selected.point), distance);
    }

    /** A key searches declared entry regions; a direct click searches actual control geometry. */
    static Pick pointed(Integer entryKey) { return pointed(entryKey, entryKey != null); }

    /** Read the nearest block-declared entry for presentation; this never changes input ownership. */
    static Pick pointedEntry() { return pointed(null, true); }

    private static Pick pointed(Integer entryKey, boolean entries) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        // Ordinary use and entry keys follow the player's interaction ray, including in third person.
        float partial = SurfaceInputSources.partialTick();
        Vec3 from = eye(), to = from.add(mc.player.getViewVector(partial)
                .scale(mc.player.blockInteractionRange()));
        var query = pointedQuery;
        if (query == null || query.nativeHit != mc.hitResult || !query.from.equals(from) || !query.to.equals(to))
            pointedQuery = query = new PointedQuery(mc.hitResult, from, to,
                    SurfaceInputSources.collect(mc, new SurfaceInputSources.Ray(from, to), partial));
        if (!entries && query.controlsResolved) return query.control;
        Pick nearest = null;
        var obstructions = query.targets.obstructions();
        for (var target : query.targets.candidates()) {
            double limit = from.distanceTo(to);
            // Direct use preserves the nearer entity's native interaction. A carrier's
            // outer entity bounds do not hide controls belonging to that same carrier.
            if (!entries && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() != target.carrier())
                limit = Math.min(limit, from.distanceTo(hit.getLocation()));
            var picked = candidate(target, from, to, entryKey, entries, limit, query.targets.selected(target));
            if (picked == null || obstructions.stream().anyMatch(block -> block.hides(target, from, picked.distance()))) continue;
            nearest = nearer(nearest, picked);
        }
        if (!entries) {
            query.controlsResolved = true;
            query.control = nearest;
        }
        return nearest;
    }

    private static Pick candidate(BlockControlContext target, Vec3 from, Vec3 to, Integer entryKey, boolean entries,
                                  double limit, BlockHitResult selectedHit) {
        var host = BlockControlHost.find(target.state());
        if (host == null || !host.allowsWorldControl()) return null;
        var selected = new SurfaceInputTarget(null, target, Vec3.atCenterOf(target.position()));
        if (!entries) {
            // The normal client pick has already resolved this surface. Keep its control ID
            // with the hit instead of intersecting the same parts again for hover or use.
            if (selectedHit != null) {
                var control = BlockControlHit.controlOf(selectedHit);
                if (control != null) {
                    double distance = renderWorld(target, selectedHit.getLocation()).distanceTo(from);
                    return distance > limit + 1e-4 ? null : new Pick(
                            new SurfaceInputTarget(null, target, selectedHit.getLocation()),
                            new ControlSurface.Pick(control, distance, selectedHit.getLocation()), distance);
                }
            }
            var pick = selected.pick(from, to);
            return pick != null && pick.distance <= limit + 1e-4 ? pick : null;
        }
        var ray = localRay(target, from, to);
        Pick result = null;
        for (var entry : host.inputLayout(target).pointedEntries()) {
            if (entryKey != null && entry.key() != entryKey) continue;
            if (entryKey == null && entry.hint().isEmpty()) continue;
            var hit = entry.bounds().move(target.position()).clip(ray.from(), ray.to());
            if (hit.isEmpty()) continue;
            double distance = renderWorld(target, hit.get()).distanceTo(from);
            if (distance > limit + 1e-4) continue;
            result = nearer(result, new Pick(new SurfaceInputTarget(null, target, hit.get()), null, distance, entry));
        }
        return result;
    }
    private static Pick nearer(Pick first, Pick second) {
        return second != null && (first == null || second.distance < first.distance) ? second : first;
    }
    private static SurfaceInputSources.Ray localRay(BlockControlContext target, Vec3 from, Vec3 to) {
        return SurfaceInputSources.frame(target).toLocal(from, to);
    }
    private static Vec3 renderWorld(BlockControlContext target, Vec3 point) {
        return SurfaceInputSources.frame(target).toWorld(point);
    }
    static Vec3 eye() {
        return SurfaceInputSources.eye(Minecraft.getInstance().player, SurfaceInputSources.partialTick());
    }
}
