package willow.train.hexie.sync.c2s;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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
import willow.train.hexie.sync.c2s.ControlCaptureSessions.Drag;
import willow.train.hexie.sync.c2s.ControlCaptureSessions.Session;
import willow.train.hexie.sync.s2c.ControlCapturePacket;

// split from willow/train/hexie/network/c2s/ControlInputPacket.java


/** One transport for captured bindings and direct mouse operations; blocks interpret the validated input. */
public record ControlInputPacket(ControlInputPacketTarget target, ControlInputPacketPhase phase, int vehicleId, BlockPos position, int entryKey,
        Vec3 point, InteractionHand hand, ControlInput input, long requestId) implements HexiePacket {

    public ControlInputPacket(ControlInputPacketTarget target, ControlInputPacketPhase phase, int vehicleId, BlockPos position, int entryKey,
            Vec3 point, InteractionHand hand, ControlInput input) {
        this(target, phase, vehicleId, position, entryKey, point, hand, input, 0);
    }

    public ControlInputPacket(FriendlyByteBuf buffer) {
        this(buffer.readEnum(ControlInputPacketTarget.class), buffer.readEnum(ControlInputPacketPhase.class), buffer.readVarInt(), buffer.readBlockPos(),
                buffer.readVarInt(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readEnum(InteractionHand.class), readInput(buffer), buffer.readVarLong());
    }
    private static ControlInput readInput(FriendlyByteBuf buffer) {
        String id = buffer.readUtf(256); int value = buffer.readVarInt(); var kind = buffer.readEnum(ControlInput.Kind.class);
        int size = buffer.readVarInt();
        if (size < 0 || size > 64) throw new IllegalArgumentException("Too many held controls");
        Set<String> held = new HashSet<>();
        for (int i = 0; i < size; i++) if (!held.add(buffer.readUtf(256))) throw new IllegalArgumentException("Duplicate held control");
        return new ControlInput(id, value, kind, held);
    }
    @Override public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(target); buffer.writeEnum(phase); buffer.writeVarInt(vehicleId); buffer.writeBlockPos(position);
        buffer.writeVarInt(entryKey); buffer.writeDouble(point.x); buffer.writeDouble(point.y); buffer.writeDouble(point.z);
        buffer.writeEnum(hand); buffer.writeUtf(input.controlId(), 256); buffer.writeVarInt(input.value()); buffer.writeEnum(input.kind());
        buffer.writeVarInt(input.held().size()); input.held().stream().sorted().forEach(id -> buffer.writeUtf(id, 256));
        buffer.writeVarLong(requestId);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) apply(player); });
        return true;
    }
    public void apply(ServerPlayer player) {
        try { applyValidated(player); }
        finally {
            if (phase == ControlInputPacketPhase.OPEN) {
                var session = ControlCaptureSessions.SESSIONS.get(player);
                PacketDistributor.sendToPlayer(player, new ControlCapturePacket(target, vehicleId, position, requestId,
                        session != null && session.matches(this) && session.requestId() == requestId));
            }
        }
    }
    private void applyValidated(ServerPlayer player) {
        if (phase == ControlInputPacketPhase.CLOSE || phase == ControlInputPacketPhase.EXIT) {
            var session = ControlCaptureSessions.SESSIONS.get(player);
            if (session != null && session.matches(this)) ControlCaptureSessions.release(player, session,
                    phase == ControlInputPacketPhase.EXIT && ControlCaptureSessions.validCapture(player, session, resolve(player), null));
            var drag = ControlCaptureSessions.DRAGS.get(player);
            if (drag != null && drag.target().matches(this)) ControlCaptureSessions.DRAGS.remove(player);
            return;
        }
        if (phase == ControlInputPacketPhase.END) {
            var drag = ControlCaptureSessions.DRAGS.get(player);
            if (drag != null && drag.target().matches(this) && drag.control().equals(input.controlId()) && drag.target().hand() == hand) {
                ControlCaptureSessions.DRAGS.remove(player);
                var context = resolve(player);
                if (ControlCaptureSessions.valid(player, drag.target(), context) && (target == ControlInputPacketTarget.SEATED || ControlCaptureSessions.permitted(player, context, drag.hit().getLocation()))) {
                    var host = BlockControlHost.find(context.state());
                    if (host != null && host.control(context.state(), drag.control()) instanceof BlockControl.Handle handle && handle.hasSpringReturn()) {
                        var held = input.held();
                        var session = ControlCaptureSessions.SESSIONS.get(player);
                        if (!held.isEmpty() && (session == null || !session.matches(this)
                                || !ControlCaptureSessions.validCapture(player, session, context, null)
                                || !host.inputLayout(context).accepts(new ControlInput("", 0, ControlInput.Kind.NONE, held), target == ControlInputPacketTarget.SEATED)))
                            held = Set.of();
                        var release = normalize(host, context, new ControlInput(handle.id(), 0, ControlInput.Kind.RELEASE_HANDLE, held));
                        if (release != null) host.operateInput(new BlockControlUse(context, player, hand, drag.hit()), release);
                    }
                }
            }
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !Double.isFinite(point.lengthSqr())) { ControlCaptureSessions.clear(player); return; }
        var context = resolve(player);
        if (context == null || HexieBridges.interaction().controlInputTargets().container(context) == null) { ControlCaptureSessions.clear(player); return; }
        var host = BlockControlHost.find(context.state());
        if (host == null) { ControlCaptureSessions.clear(player); return; }
        boolean seated = target == ControlInputPacketTarget.SEATED;
        var previous = ControlCaptureSessions.SESSIONS.get(player);
        var layout = seated || phase == ControlInputPacketPhase.OPEN || previous != null && previous.matches(this)
                ? host.inputLayout(context) : ControlInputLayout.EMPTY;
        if (seated && !layout.seatedCapture()) { ControlCaptureSessions.clear(player); return; }
        if (!seated && (!host.allowsWorldControl() || !player.mayBuild())) { ControlCaptureSessions.clear(player); return; }
        if (previous != null && !previous.matches(this) && (phase == ControlInputPacketPhase.OPEN || seated)) ControlCaptureSessions.release(player, previous);
        if (phase == ControlInputPacketPhase.OPEN) {
            if (previous != null && ControlCaptureSessions.SESSIONS.get(player) == previous) ControlCaptureSessions.release(player, previous);
            if (!host.canCapture(new BlockControlUse(context, player, hand, BlockHitResult.miss(point, Direction.UP, position)), seated)) return;
            if (!seated && !canEnter(player, context, layout)) return;
            ControlCaptureSessions.SESSIONS.put(player, session(player, context, point));
            return;
        }
        var captured = ControlCaptureSessions.SESSIONS.get(player);
        // Seat occupancy authorizes OPEN; it must not silently recreate a refused or revoked capture.
        boolean capture = captured != null && captured.matches(this) && ControlCaptureSessions.validCapture(player, captured, context, layout);
        if (captured != null && captured.matches(this) && !capture) {
            ControlCaptureSessions.release(player, captured); captured = null;
        }

        boolean bound = phase == ControlInputPacketPhase.OPERATE && capture && layout.accepts(input, seated);
        BlockHitResult hit;
        if (bound) {
            hit = BlockHitResult.miss(point, Direction.UP, position);
        } else {
            // Native mouse routes use their existing item/block/carrier transport. They may
            // still declare captured bindings and implement operateInput for those actions.
            if (host.nativeInteraction() && !capture) return;
            // Held actions only belong to an active, declared capture. Pointer operations can be used directly.
            if (!input.held().isEmpty() && !(capture && layout.accepts(new ControlInput("", 0, ControlInput.Kind.NONE, input.held()), seated))) return;
            if (!input.hasCommand()) return;
            var physical = host.control(context.state(), input.controlId());
            if (physical == null) return;
            if (phase == ControlInputPacketPhase.UPDATE) {
                var drag = ControlCaptureSessions.DRAGS.get(player);
                if (!(physical instanceof BlockControl.Handle) || drag == null || !drag.target().matches(this)
                        || !drag.control().equals(input.controlId()) || !ControlCaptureSessions.valid(player, drag.target(), context)
                        || drag.target().hand() != hand || !seated && !ControlCaptureSessions.permitted(player, context, drag.hit().getLocation())) return;
                hit = drag.hit();
            } else {
                if (phase != ControlInputPacketPhase.OPERATE && phase != ControlInputPacketPhase.BEGIN) return;
                if (phase == ControlInputPacketPhase.BEGIN && !(physical instanceof BlockControl.Handle)) return;
                if (!seated && !ControlCaptureSessions.permitted(player, context, point)) return;
                if (phase == ControlInputPacketPhase.BEGIN && normalize(host, context, input) == null) return;
                Vec3 eye = HexieBridges.interaction().controlInputTargets().toLocal(context, player, HexieBridges.interaction().controlInputTargets().eye(context, player));
                // The client supplies the selected control and its hit; the server validates the command.
                Vec3 direction = eye.subtract(point);
                hit = new BlockHitResult(point, Direction.getNearest(direction.x, direction.y, direction.z), position, false);
                ControlCaptureSessions.DRAGS.remove(player);
                if (phase == ControlInputPacketPhase.BEGIN) {
                    ControlCaptureSessions.DRAGS.put(player, new Drag(session(player, context, point), input.controlId(), hit));
                    return;
                }
            }
        }
        var normalized = normalize(host, context, input);
        if (normalized == null) return;
        host.operateInput(new BlockControlUse(context, player, hand, hit), normalized);
        if (phase == ControlInputPacketPhase.UPDATE) {
            var drag = ControlCaptureSessions.DRAGS.get(player);
            if (drag != null) ControlCaptureSessions.DRAGS.put(player, new Drag(drag.target().refresh(player.level().getGameTime()), drag.control(), drag.hit()));
        }
        if (capture) ControlCaptureSessions.SESSIONS.put(player, captured.refresh(player.level().getGameTime()));
    }

    BlockControlContext resolve(ServerPlayer player) {
        if (target == ControlInputPacketTarget.WORLD) return HexieBridges.interaction().controlInputTargets().world(player, vehicleId, position, 1);
        var vehicle = player.getVehicle();
        if (vehicle == null || vehicle.getId() != vehicleId
                || !HexieBridges.interaction().controlInputTargets().seatDiscoveryLoaded(player, vehicle, position)) return null;
        var cab = DrivingConsoleContext.find(player);
        return cab != null && player.getVehicle() == cab.seatAccess().vehicle()
                && cab.seatAccess().vehicle().getId() == vehicleId && cab.console().equals(position)
                ? HexieBridges.interaction().controlInputTargets().seated(cab, 1) : null;
    }
    private Session session(Player player, BlockControlContext context, Vec3 anchor) {
        var vehicle = vehicleId < 0 ? null : player.level().getEntity(vehicleId);
        return new Session(target, vehicleId, vehicle == null ? null : vehicle.getUUID(),
                position.immutable(), context.state().getBlock(), player.level().dimension(), context, HexieBridges.interaction().controlInputTargets().container(context),
                HexieBridges.interaction().controlInputTargets().blockEntity(context), anchor, hand,
                entryKey, requestId, player.level().getGameTime());
    }
    /** Resolve STEP on the server against legal detents, never against a client-supplied previous position. */
    public static ControlInput normalize(BlockControlHost host, BlockControlContext context, ControlInput input) {
        if (!input.hasCommand()) return input;
        var control = host.inputControl(context.state(), input.controlId());
        if (control == null) return null;
        if (input.kind() == ControlInput.Kind.RELEASE_HANDLE) {
            if (!(control instanceof BlockControl.Handle handle) || !handle.hasSpringReturn() || input.value() != 0) return null;
            int value = handle.releasedNotch(handle.notch(host.surfaceState(context)));
            if (!handle.validNotches().contains(value)) return null;
            return new ControlInput(input.controlId(), value, ControlInput.Kind.SET, input.held());
        }
        if (input.kind() == ControlInput.Kind.STEP) {
            if (!(control instanceof BlockControl.Handle handle) || Math.abs((long) input.value()) != 1) return null;
            var notches = handle.validNotches();
            if (notches.isEmpty()) return null;
            int index = notches.indexOf(handle.notch(host.surfaceState(context)));
            if (index < 0) return null;
            int value = notches.get(Math.clamp(index + input.value(), 0, notches.size() - 1));
            return new ControlInput(input.controlId(), value, ControlInput.Kind.SET, input.held());
        }
        if (input.kind() == ControlInput.Kind.PRESS && !(control instanceof BlockControl.Button)
                || input.kind() == ControlInput.Kind.SET && !(control instanceof BlockControl.Handle)
                || !control.accepts(input.value())) return null;
        if (input.kind() == ControlInput.Kind.SET && control instanceof BlockControl.Handle handle
                && !handle.validNotches().contains(input.value())) return null;
        return input;
    }
    private boolean canEnter(ServerPlayer player, BlockControlContext context, ControlInputLayout layout) {
        if (!ControlCaptureSessions.permitted(player, context, point)) return false;
        Vec3 offset = Vec3.atLowerCornerOf(position);
        return layout.pointedEntries().stream().anyMatch(entry -> entry.key() == entryKey
                && entry.bounds().move(offset).inflate(1e-5).contains(point));
    }
}
