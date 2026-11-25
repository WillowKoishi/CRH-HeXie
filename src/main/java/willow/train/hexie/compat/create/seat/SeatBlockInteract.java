package willow.train.hexie.compat.create.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge.SeatYZBlockView;
import willow.train.hexie.core.util.general.InteractionResults;

final class SeatBlockInteract {
    private SeatBlockInteract() {}

    public static ItemInteractionResult useSingleSeat(Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown() || player instanceof FakePlayer)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Include Create seats saved by older versions when checking occupancy.
        var seats = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        var existing = seats.isEmpty() ? null : seats.getFirst();
        if (existing != null && existing.getFirstPassenger() instanceof Player)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        if (existing == null) {
            sitDown(level, pos, rider(level, player));
        } else {
            existing.ejectPassengers();
            if (HexieBridges.playerCarriage().isHexieSeat(existing)) {
                player.startRiding(existing);
            } else {
                existing.discard();
                sitDown(level, pos, player);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    public static boolean seatFallingMob(Block block, BlockGetter reader, Entity entity) {
        var pos = entity.blockPosition();
        if (!(entity instanceof LivingEntity) || entity instanceof Player || !SeatBlock.canBePickedUp(entity)
                || !reader.getBlockState(pos).is(block) || SeatBlock.isSeatOccupied(entity.level(), pos))
            return false;
        if (!entity.level().isClientSide) sitDown(entity.level(), pos, entity);
        return true;
    }

    private static void sitDown(Level level, BlockPos pos, Entity rider) {
        var seat = HexieBridges.playerCarriage().createHexieSeatEntity(level);
        seat.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        level.addFreshEntity(seat);
        rider.startRiding(seat, true);
        if (rider instanceof TamableAnimal animal) animal.setInSittingPose(true);
    }

    static Entity rider(Level world, Player player) {
        return SeatBlock.getLeashed(world, player).or(player);
    }

    public static InteractionResult useWithoutItem(SeatYZBlockView block, BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    public static ItemInteractionResult useItemOn(SeatYZBlockView block, ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        InteractionResult result = block.use(state, level, pos, player, hand, hit);
        return InteractionResults.toItemResult(result);
    }
}
