package willow.train.hexie.network.c2s;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.Main;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.api.interaction.*;
import willow.train.hexie.interaction.ControlInputTargets;
import willow.train.hexie.network.s2c.ControlCapturePacket;

/** One transport for captured bindings and direct mouse operations; blocks interpret the validated input. */
@EventBusSubscriber(modid = Main.MOD_ID)
public record ControlInputPacket(Target target, Phase phase, int vehicleId, BlockPos position, int entryKey,
        Vec3 point, InteractionHand hand, ControlInput input, long requestId) implements HexiePacket {
    public enum Target { SEATED, WORLD }
    public enum Phase { OPEN, OPERATE, BEGIN, UPDATE, END, CLOSE, EXIT }
    private record Session(Target target, int vehicleId, UUID vehicleUuid, BlockPos position,
            Block block, Object dimension, BlockControlContext context, Object container, BlockEntity blockEntity,
            Vec3 anchor, InteractionHand hand,
            int entryKey, long requestId, long lastInput) {
        boolean matches(ControlInputPacket packet) {
            return target == packet.target && vehicleId == packet.vehicleId && position.equals(packet.position);
        }
        Session refresh(long now) { return new Session(target, vehicleId, vehicleUuid, position, block, dimension, context,
                container, blockEntity, anchor, hand, entryKey, requestId, now); }
    }
    private record Drag(Session target, String control, BlockHitResult hit) {}
    private static final Map<Player, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<Player, Drag> DRAGS = new WeakHashMap<>();

    public ControlInputPacket(Target target, Phase phase, int vehicleId, BlockPos position, int entryKey,
            Vec3 point, InteractionHand hand, ControlInput input) {
        this(target, phase, vehicleId, position, entryKey, point, hand, input, 0);
    }

    public ControlInputPacket(FriendlyByteBuf buffer) {
        this(buffer.readEnum(Target.class), buffer.readEnum(Phase.class), buffer.readVarInt(), buffer.readBlockPos(),
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
            if (phase == Phase.OPEN) {
                var session = SESSIONS.get(player);
                PacketDistributor.sendToPlayer(player, new ControlCapturePacket(target, vehicleId, position, requestId,
                        session != null && session.matches(this) && session.requestId == requestId));
            }
        }
    }
    private void applyValidated(ServerPlayer player) {
        if (phase == Phase.CLOSE || phase == Phase.EXIT) {
            var session = SESSIONS.get(player);
            if (session != null && session.matches(this)) release(player, session,
                    phase == Phase.EXIT && validCapture(player, session, resolve(player), null));
            var drag = DRAGS.get(player);
            if (drag != null && drag.target.matches(this)) DRAGS.remove(player);
            return;
        }
        if (phase == Phase.END) {
            var drag = DRAGS.get(player);
            if (drag != null && drag.target.matches(this) && drag.control.equals(input.controlId())) DRAGS.remove(player);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !Double.isFinite(point.lengthSqr())) { clear(player); return; }
        var context = resolve(player);
        if (context == null || ControlInputTargets.container(context) == null) { clear(player); return; }
        var host = BlockControlHost.find(context.state());
        if (host == null) { clear(player); return; }
        boolean seated = target == Target.SEATED;
        var previous = SESSIONS.get(player);
        var layout = seated || phase == Phase.OPEN || previous != null && previous.matches(this)
                ? host.inputLayout(context) : ControlInputLayout.EMPTY;
        if (seated && !layout.seatedCapture()) { clear(player); return; }
        if (!seated && (!host.allowsWorldControl() || !player.mayBuild())) { clear(player); return; }
        if (previous != null && !previous.matches(this) && (phase == Phase.OPEN || seated)) release(player, previous);
        if (phase == Phase.OPEN) {
            if (previous != null && SESSIONS.get(player) == previous) release(player, previous);
            if (!host.canCapture(new BlockControlUse(context, player, hand, BlockHitResult.miss(point, Direction.UP, position)), seated)) return;
            if (!seated && !canEnter(player, context, layout)) return;
            SESSIONS.put(player, session(player, context, point));
            return;
        }
        var captured = SESSIONS.get(player);
        // Seat occupancy authorizes OPEN; it must not silently recreate a refused or revoked capture.
        boolean capture = captured != null && captured.matches(this) && validCapture(player, captured, context, layout);
        if (captured != null && captured.matches(this) && !capture) {
            release(player, captured); captured = null;
        }

        boolean bound = phase == Phase.OPERATE && capture && layout.accepts(input, seated);
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
            if (phase == Phase.UPDATE) {
                var drag = DRAGS.get(player);
                if (!(physical instanceof BlockControl.Handle) || drag == null || !drag.target.matches(this)
                        || !drag.control.equals(input.controlId()) || !valid(player, drag.target, context)
                        || drag.target.hand != hand || !seated && !permitted(player, context, drag.hit.getLocation())) return;
                hit = drag.hit;
            } else {
                if (phase != Phase.OPERATE && phase != Phase.BEGIN) return;
                if (phase == Phase.BEGIN && !(physical instanceof BlockControl.Handle)) return;
                if (!seated && !permitted(player, context, point)) return;
                if (phase == Phase.BEGIN && normalize(host, context, input) == null) return;
                Vec3 eye = ControlInputTargets.toLocal(context, player, ControlInputTargets.eye(context, player));
                // The client supplies the selected control and its hit; the server validates the command.
                Vec3 direction = eye.subtract(point);
                hit = new BlockHitResult(point, Direction.getNearest(direction.x, direction.y, direction.z), position, false);
                DRAGS.remove(player);
                if (phase == Phase.BEGIN) {
                    DRAGS.put(player, new Drag(session(player, context, point), input.controlId(), hit));
                    return;
                }
            }
        }
        var normalized = normalize(host, context, input);
        if (normalized == null) return;
        host.operateInput(new BlockControlUse(context, player, hand, hit), normalized);
        if (phase == Phase.UPDATE) {
            var drag = DRAGS.get(player);
            if (drag != null) DRAGS.put(player, new Drag(drag.target.refresh(player.level().getGameTime()), drag.control, drag.hit));
        }
        if (capture) SESSIONS.put(player, captured.refresh(player.level().getGameTime()));
    }

    private BlockControlContext resolve(ServerPlayer player) {
        if (target == Target.WORLD) return ControlInputTargets.world(player, vehicleId, position, 1);
        var vehicle = player.getVehicle();
        if (vehicle == null || vehicle.getId() != vehicleId
                || !ControlInputTargets.seatDiscoveryLoaded(player, vehicle, position)) return null;
        var cab = DrivingConsoleContext.find(player);
        return cab != null && player.getVehicle() == cab.seatAccess().vehicle()
                && cab.seatAccess().vehicle().getId() == vehicleId && cab.console().equals(position)
                ? ControlInputTargets.seated(cab, 1) : null;
    }
    private Session session(Player player, BlockControlContext context, Vec3 anchor) {
        var vehicle = vehicleId < 0 ? null : player.level().getEntity(vehicleId);
        return new Session(target, vehicleId, vehicle == null ? null : vehicle.getUUID(),
                position.immutable(), context.state().getBlock(), player.level().dimension(), context, ControlInputTargets.container(context),
                ControlInputTargets.blockEntity(context), anchor, hand,
                entryKey, requestId, player.level().getGameTime());
    }
    /** Resolve STEP on the server against legal detents, never against a client-supplied previous position. */
    public static ControlInput normalize(BlockControlHost host, BlockControlContext context, ControlInput input) {
        if (!input.hasCommand()) return input;
        var control = host.inputControl(context.state(), input.controlId());
        if (control == null) return null;
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
        if (!permitted(player, context, point)) return false;
        Vec3 offset = Vec3.atLowerCornerOf(position);
        return layout.pointedEntries().stream().anyMatch(entry -> entry.key() == entryKey
                && entry.bounds().move(offset).inflate(1e-5).contains(point));
    }
    private static boolean valid(ServerPlayer player, Session session, BlockControlContext context) {
        return context != null && player.level().dimension() == session.dimension && sameTarget(session, context)
                && (session.vehicleId < 0 || player.level().getEntity(session.vehicleId) != null
                    && player.level().getEntity(session.vehicleId).getUUID().equals(session.vehicleUuid))
                && player.level().getGameTime() - session.lastInput <= 30;
    }
    private static boolean sameTarget(Session session, BlockControlContext context) {
        // A replaced BE (even the same block type), recreated chunk or new contraption owns a new session.
        var currentContainer = ControlInputTargets.container(context);
        if (context.blocks() != session.context.blocks() || context.carrier() != session.context.carrier()
                || currentContainer == null || currentContainer != session.container
                || context.state().getBlock() != session.block) return false;
        var entity = ControlInputTargets.blockEntity(context);
        return entity == session.blockEntity && (entity == null || !entity.isRemoved());
    }
    private static boolean validCapture(ServerPlayer player, Session session, BlockControlContext context,
                                        ControlInputLayout layout) {
        if (!valid(player, session, context)) return false;
        var host = BlockControlHost.find(context.state());
        if (host == null || !host.canCapture(new BlockControlUse(context, player, session.hand,
                BlockHitResult.miss(session.anchor, Direction.UP, session.position)), session.target == Target.SEATED)) return false;
        if (layout == null) layout = host.inputLayout(context);
        if (session.target == Target.SEATED) return layout.seatedCapture();
        // The region authorizes entry at OPEN; turning the block must not revoke an established capture.
        return player.mayBuild() && host.allowsWorldControl() && permitted(player, context, session.anchor)
                && layout.pointedEntries().stream().anyMatch(entry -> entry.key() == session.entryKey);
    }
    private static boolean permitted(ServerPlayer player, BlockControlContext context, Vec3 point) {
        // Picking, capture range and drag range belong to the client's rendered frame.
        // Server player positions can precede the current moving carrier pose.
        Vec3 world = ControlInputTargets.toWorld(context, player, point);
        if (!Double.isFinite(world.lengthSqr())) return false;
        var worldPosition = context.carrier() == null ? context.position() : BlockPos.containing(world);
        if (!player.level().mayInteract(player, worldPosition)) return false;
        return ControlInputTargets.canInteract(context, player, point);
    }
    private static void clear(ServerPlayer player) {
        var session = SESSIONS.get(player); if (session != null) release(player, session);
        DRAGS.remove(player);
    }
    private static void release(ServerPlayer player, Session session) {
        release(player, session, false);
    }
    private static void release(ServerPlayer player, Session session, boolean explicit) {
        SESSIONS.remove(player);
        if (session.requestId != 0) PacketDistributor.sendToPlayer(player, new ControlCapturePacket(
                session.target, session.vehicleId, session.position, session.requestId, false));
        // Release the original target, including after a dimension change; never resolve a reused entity ID.
        var context = session.context;
        if (!sameTarget(session, context)) return;
        var host = BlockControlHost.find(context.state());
        if (host != null) host.endCapture(new BlockControlUse(context, player, session.hand,
                BlockHitResult.miss(session.anchor, Direction.UP, session.position)), session.target == Target.SEATED, explicit);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var session = SESSIONS.get(player);
        if (session == null) return;
        if (!sameTarget(session, session.context)) { clear(player); return; }
        var probe = new ControlInputPacket(session.target, Phase.OPERATE, session.vehicleId, session.position, 0,
                session.anchor, session.hand, ControlInput.RELEASE);
        var context = probe.resolve(player);
        if (!player.isAlive() || player.isSpectator() || !validCapture(player, session, context, null)) clear(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
}
