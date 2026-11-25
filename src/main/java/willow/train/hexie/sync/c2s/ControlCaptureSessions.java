package willow.train.hexie.sync.c2s;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sync.SyncBridge.ControlInputPacketPhase;
import willow.train.hexie.bridge.sync.SyncBridge.ControlInputPacketTarget;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlInput;
import willow.train.hexie.core.api.interaction.ControlInputLayout;
import willow.train.hexie.sync.s2c.ControlCapturePacket;

// split from willow/train/hexie/network/c2s/ControlInputPacket.java
@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public final class ControlCaptureSessions {
    record Session(ControlInputPacketTarget target, int vehicleId, UUID vehicleUuid, BlockPos position,
            Block block, Object dimension, BlockControlContext context, Object container, BlockEntity blockEntity,
            Vec3 anchor, InteractionHand hand,
            int entryKey, long requestId, long lastInput) {
        boolean matches(ControlInputPacket packet) {
            return target == packet.target() && vehicleId == packet.vehicleId() && position.equals(packet.position());
        }
        Session refresh(long now) { return new Session(target, vehicleId, vehicleUuid, position, block, dimension, context,
                container, blockEntity, anchor, hand, entryKey, requestId, now); }
    }
    record Drag(Session target, String control, BlockHitResult hit) {}
    static final Map<Player, Session> SESSIONS = new WeakHashMap<>();
    static final Map<Player, Drag> DRAGS = new WeakHashMap<>();

    static boolean valid(ServerPlayer player, Session session, BlockControlContext context) {
        return context != null && player.level().dimension() == session.dimension && sameTarget(session, context)
                && (session.vehicleId < 0 || player.level().getEntity(session.vehicleId) != null
                    && player.level().getEntity(session.vehicleId).getUUID().equals(session.vehicleUuid))
                && player.level().getGameTime() - session.lastInput <= 30;
    }
    private static boolean sameTarget(Session session, BlockControlContext context) {
        // A replaced BE (even the same block type), recreated chunk or new contraption owns a new session.
        var currentContainer = HexieBridges.interaction().controlInputTargets().container(context);
        if (context.blocks() != session.context.blocks() || context.carrier() != session.context.carrier()
                || currentContainer == null || currentContainer != session.container
                || context.state().getBlock() != session.block) return false;
        var entity = HexieBridges.interaction().controlInputTargets().blockEntity(context);
        return entity == session.blockEntity && (entity == null || !entity.isRemoved());
    }
    static boolean validCapture(ServerPlayer player, Session session, BlockControlContext context,
                                        ControlInputLayout layout) {
        if (!valid(player, session, context)) return false;
        var host = BlockControlHost.find(context.state());
        if (host == null || !host.canCapture(new BlockControlUse(context, player, session.hand,
                BlockHitResult.miss(session.anchor, Direction.UP, session.position)), session.target == ControlInputPacketTarget.SEATED)) return false;
        if (layout == null) layout = host.inputLayout(context);
        if (session.target == ControlInputPacketTarget.SEATED) return layout.seatedCapture();
        // The region authorizes entry at OPEN; turning the block must not revoke an established capture.
        return player.mayBuild() && host.allowsWorldControl() && permitted(player, context, session.anchor)
                && layout.pointedEntries().stream().anyMatch(entry -> entry.key() == session.entryKey);
    }
    static boolean permitted(ServerPlayer player, BlockControlContext context, Vec3 point) {
        // Picking, capture range and drag range belong to the client's rendered frame.
        // Server player positions can precede the current moving carrier pose.
        Vec3 world = HexieBridges.interaction().controlInputTargets().toWorld(context, player, point);
        if (!Double.isFinite(world.lengthSqr())) return false;
        var worldPosition = context.carrier() == null ? context.position() : BlockPos.containing(world);
        if (!player.level().mayInteract(player, worldPosition)) return false;
        return HexieBridges.interaction().controlInputTargets().canInteract(context, player, point);
    }
    static void clear(ServerPlayer player) {
        var session = SESSIONS.get(player); if (session != null) release(player, session);
        DRAGS.remove(player);
    }
    static void release(ServerPlayer player, Session session) {
        release(player, session, false);
    }
    static void release(ServerPlayer player, Session session, boolean explicit) {
        SESSIONS.remove(player);
        if (session.requestId != 0) PacketDistributor.sendToPlayer(player, new ControlCapturePacket(
                session.target, session.vehicleId, session.position, session.requestId, false));
        // Release the original target, including after a dimension change; never resolve a reused entity ID.
        var context = session.context;
        if (!sameTarget(session, context)) return;
        var host = BlockControlHost.find(context.state());
        if (host != null) host.endCapture(new BlockControlUse(context, player, session.hand,
                BlockHitResult.miss(session.anchor, Direction.UP, session.position)), session.target == ControlInputPacketTarget.SEATED, explicit);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var session = SESSIONS.get(player);
        if (session == null) return;
        if (!sameTarget(session, session.context)) { clear(player); return; }
        var probe = new ControlInputPacket(session.target, ControlInputPacketPhase.OPERATE, session.vehicleId, session.position, 0,
                session.anchor, session.hand, ControlInput.RELEASE);
        var context = probe.resolve(player);
        if (!player.isAlive() || player.isSpectator() || !validCapture(player, session, context, null)) clear(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
}
