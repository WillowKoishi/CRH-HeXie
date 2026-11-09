package willow.train.hexie.api.interaction;

import net.minecraft.world.phys.BlockHitResult;

/** The selected registered control and its hit in the containing storage coordinate system. */
public record BlockControlHit(BlockControl control, BlockHitResult blockHit) {
    public BlockControlHit {
        blockHit = new Result(control, blockHit);
    }

    /** Native selection carries the resolved control through to client input and presentation. */
    public static BlockControl controlOf(BlockHitResult hit) {
        return hit instanceof Result result ? result.control : null;
    }

    private static final class Result extends BlockHitResult {
        private final BlockControl control;
        private final Type type;

        Result(BlockControl control, BlockHitResult hit) {
            this(control, hit, hit.getType());
        }

        private Result(BlockControl control, BlockHitResult hit, Type type) {
            super(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), hit.isInside());
            this.control = control;
            this.type = type;
        }

        @Override public Type getType() { return type; }
        @Override public BlockHitResult withDirection(net.minecraft.core.Direction direction) {
            return new Result(control, super.withDirection(direction), type);
        }
        @Override public BlockHitResult withPosition(net.minecraft.core.BlockPos position) {
            return new Result(control, super.withPosition(position), type);
        }
    }
}
