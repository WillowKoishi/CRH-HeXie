package willow.train.hexie.blocks.track.standard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TrackParticleGeometry {
    private TrackParticleGeometry() {}

    public static void hit(VoxelShape outline, BlockHitResult hit, ParticleConsumer output) {
        VoxelShape shape = Shapes.join(outline, Shapes.block(), BooleanOp.AND);
        if (shape.isEmpty())
            return;

        AABB box = shape.bounds();
        BlockPos pos = hit.getBlockPos();
        double x = inside(hit.getLocation().x - pos.getX(), box.minX, box.maxX);
        double y = inside(hit.getLocation().y - pos.getY(), box.minY, box.maxY);
        double z = inside(hit.getLocation().z - pos.getZ(), box.minZ, box.maxZ);
        Direction face = hit.getDirection();
        switch (face) {
            case DOWN -> y = box.minY - 0.1;
            case UP -> y = box.maxY + 0.1;
            case NORTH -> z = box.minZ - 0.1;
            case SOUTH -> z = box.maxZ + 0.1;
            case WEST -> x = box.minX - 0.1;
            case EAST -> x = box.maxX + 0.1;
        }
        output.accept(pos.getX() + x, pos.getY() + y, pos.getZ() + z, 0, 0, 0);
    }

    private static double inside(double value, double min, double max) {
        double margin = Math.min(0.1, (max - min) / 2);
        return Mth.clamp(value, min + margin, max - margin);
    }

    @FunctionalInterface
    public interface ParticleConsumer {
        void accept(double x, double y, double z, double vx, double vy, double vz);
    }
}
