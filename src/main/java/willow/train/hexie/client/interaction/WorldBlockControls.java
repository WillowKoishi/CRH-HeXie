package willow.train.hexie.client.interaction;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import willow.train.hexie.Main;
import willow.train.hexie.api.interaction.*;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.BlockControlPacket;

/** Uses the already selected owner. No world, block-entity, or carrier discovery happens on a click. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class WorldBlockControls {
    private WorldBlockControls() {}
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || SurfaceInteraction.dragging()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.player.isSpectator() || mc.screen != null
                || !(mc.hitResult instanceof BlockHitResult selected) || selected.getType() != HitResult.Type.BLOCK) return;
        var position = selected.getBlockPos();
        var state = mc.level.getBlockState(position);
        var host = BlockControlHost.find(state);
        if (host == null || host.nativeInteraction() || !host.allowsWorldControl()) return;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        var frame = SableInteractionCoordinates.render(mc.level, position, partial);
        Vec3 eye = frame.toStorage(SableInteractionCoordinates.renderEye(mc.player, partial));
        Vec3 direction = selected.getLocation().subtract(eye);
        if (direction.lengthSqr() < 1e-12) return;
        var target = new BlockControlContext(mc.level, position, null, partial);
        var hit = host.pickControl(target, eye, eye.add(direction.normalize().scale(mc.player.blockInteractionRange())));
        if (hit == null) return;
        var context = new Target(mc.level, mc.getConnection(), position.immutable(), state.getBlock(), host,
                event.getHand(), hit.blockHit().getLocation());
        var picked = new ControlSurface.Pick(hit.control(), eye.distanceTo(hit.blockHit().getLocation()), hit.blockHit().getLocation());
        var useKey = mc.options.keyUse.getKey();
        int mouseButton = useKey.getType() == InputConstants.Type.MOUSE ? useKey.getValue() : -1;
        if (SurfaceInteraction.click(context, picked, mouseButton, false)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
    private record Identity(Level level, BlockPos position, Block block) {}
    private record Target(Level level, ClientPacketListener connection, BlockPos position, Block block, BlockControlHost host,
                          InteractionHand hand, Vec3 point) implements SurfaceInteraction.Context {
        private BlockControlContext target() { return new BlockControlContext(level, position, null, 1); }
        @Override public Object identity() { return new Identity(level, position, block); }
        @Override public ControlState state() { return host.surfaceState(target()); }
        @Override public BlockControl control(String id) { return host.control(level.getBlockState(position), id); }
        @Override public boolean valid() {
            var mc = Minecraft.getInstance();
            if (mc.level != level || mc.getConnection() != connection || mc.player == null
                    || !mc.player.isAlive() || mc.player.isSpectator() || !level.hasChunkAt(position)
                    || !level.getBlockState(position).is(block)) return false;
            double reach = mc.player.blockInteractionRange();
            return SableInteractionCoordinates.logicalDistanceSquared(level, mc.player.getEyePosition(), position, point) <= reach * reach;
        }
        @Override public void begin(BlockControl.Handle handle) { send(handle, handle.notch(state()), BlockControlPacket.BEGIN); }
        // Ending a gesture must not read an unloaded block or state from a previous level.
        @Override public void end(BlockControl.Handle handle) { send(handle, 0, BlockControlPacket.END); }
        @Override public void submit(BlockControl control, int value) {
            send(control, value, control instanceof BlockControl.Handle ? BlockControlPacket.UPDATE : BlockControlPacket.PRESS);
        }
        private void send(BlockControl control, int value, int phase) {
            var current = Minecraft.getInstance().getConnection();
            if (current != connection || !connection.getConnection().isConnected()) return;
            HexieNetworkHandler.sendToServer(new BlockControlPacket(position, control.id(), value, phase, hand, point));
        }
    }
}
