package willow.train.hexie.blocks.semaphore.through;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/** Matches a click's facing to the corresponding server-side placement. */
public final class SignalPlacementContext {
    private static final Map<ServerPlayer, PendingFacing> PENDING = new WeakHashMap<>();
    private static final int MAX_AGE_TICKS = 40;

    private SignalPlacementContext() {}

    public static void remember(ServerPlayer player, BlockPos pos, InteractionHand hand, Direction facing) {
        PENDING.put(player, new PendingFacing(pos.immutable(), player.level().dimension(), hand, facing,
                player.getServer().getTickCount()));
    }

    public static Direction consume(BlockPlaceContext context, Direction fallback) {
        if (!(context.getPlayer() instanceof ServerPlayer player))
            return fallback;
        PendingFacing pending = PENDING.remove(player);
        if (pending == null)
            return fallback;
        int age = player.getServer().getTickCount() - pending.tick();
        return age >= 0 && age <= MAX_AGE_TICKS
                && pending.dimension().equals(context.getLevel().dimension())
                && pending.pos().equals(context.getClickedPos())
                && pending.hand() == context.getHand() ? pending.facing() : fallback;
    }

    private record PendingFacing(BlockPos pos, ResourceKey<Level> dimension, InteractionHand hand,
                                 Direction facing, int tick) {}
}
