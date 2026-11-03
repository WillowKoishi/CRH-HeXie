package willow.train.hexie.blocks.base;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Four cached rotations of authored north-facing geometry, in block coordinates. */
public final class DirectionalShape {
    private final VoxelShape[] shapes=new VoxelShape[4];
    public DirectionalShape(VoxelShape north) {
        for(var facing:Direction.Plane.HORIZONTAL) {
            VoxelShape shape=Shapes.empty();
            for(var box:north.toAabbs())shape=Shapes.or(shape,Shapes.create(rotate(box,facing)));
            shapes[facing.get2DDataValue()]=shape.optimize();
        }
    }
    public VoxelShape get(Direction facing) { return shapes[facing.get2DDataValue()]; }
    public static AABB rotate(AABB b,Direction facing) {
        return switch(facing) {
            case EAST -> new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
            case SOUTH -> new AABB(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);
            case WEST -> new AABB(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);
            default -> b;
        };
    }
}
