package willow.train.hexie.blocks.locos.crh2a;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Side slabs follow the exterior envelope; inward returns never become collision. */
final class CRH2PartShapes {
    final BlockPos mount;
    final boolean side;
    final boolean remote;
    final boolean checkSide;
    final List<BlockPos> cells = new ArrayList<>();
    private final VoxelShape[] shapes = new VoxelShape[4];
    private final VoxelShape[][] pieces = new VoxelShape[4][16];
    private final AABB[] bounds = new AABB[4];
    private final AABB[] rightBounds = new AABB[4];
    private final VoxelShape[] rightShapes = new VoxelShape[4];
    private final VoxelShape[][] rightPieces = new VoxelShape[4][16];

    CRH2PartShapes(String name) {
        try (var stream = getClass().getResourceAsStream("/data/hexie/crh2a_parts/" + name + ".json")) {
            if (stream == null) throw new IllegalStateException("Missing CRH2A geometry: " + name);
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var m = data.getAsJsonArray("mount");
            mount = new BlockPos(m.get(0).getAsInt(),m.get(1).getAsInt(),m.get(2).getAsInt());
            side = data.get("side").getAsBoolean();
            remote = data.has("remote") && data.get("remote").getAsBoolean();
            checkSide = data.get("check_side").getAsBoolean();
            var fullBounds = box(data.getAsJsonArray("bounds"));
            cells.add(BlockPos.ZERO);
            if (side && !remote) {
                var slab=box(data.getAsJsonArray("boxes").get(0).getAsJsonArray());
                for(int x=(int)Math.floor(slab.minX+1e-7);x<Math.ceil(slab.maxX-1e-7);x++)
                    for(int y=0;y<Math.ceil(slab.maxY-1e-7);y++)
                        if(x!=0||y!=0) cells.add(new BlockPos(x,y,0));
            }
            if(cells.size()>16) throw new IllegalStateException("Too many side cells: "+name);
            for (var facing : Direction.Plane.HORIZONTAL) {
                int d = facing.get2DDataValue();
                bounds[d] = rotate(fullBounds,facing);
                VoxelShape shape = Shapes.empty();
                for (var entry : data.getAsJsonArray("boxes"))
                    shape = Shapes.or(shape, Shapes.create(rotate(box(entry.getAsJsonArray()),facing)));
                shapes[d] = shape;
                Arrays.fill(pieces[d],Shapes.empty());
                for(int i=0;i<cells.size();i++) {
                    var cell=cells.get(i);
                    if(!side || remote) { pieces[d][i]=shape; continue; }
                    // Split only along the side plane. There is never a row inside the car.
                    var clip=new AABB(cell.getX(),cell.getY()==0?-16:cell.getY(),-16,cell.getX()+1,cell.getY()+1,16);
                    var rotatedClip=rotate(clip,facing);var offset=rotate(cell,facing);
                    pieces[d][i]=Shapes.join(shape,Shapes.create(rotatedClip),BooleanOp.AND).move(-offset.getX(),-offset.getY(),-offset.getZ());
                }
            }
            int north=Direction.NORTH.get2DDataValue();
            for(var facing:Direction.Plane.HORIZONTAL) {
                int d=facing.get2DDataValue();rightBounds[d]=rotate(reflect(bounds[north]),facing);
                rightShapes[d]=reflectedRotation(shapes[north],facing);
                for(int i=0;i<16;i++)rightPieces[d][i]=reflectedRotation(pieces[north][i],facing);
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load " + name,error); }
    }
    private static AABB box(JsonArray b) {
        return new AABB(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),
                b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble());
    }
    private static AABB reflect(AABB b) {return new AABB(1-b.maxX,b.minY,b.minZ,1-b.minX,b.maxY,b.maxZ);}
    private static VoxelShape reflectedRotation(VoxelShape s,Direction facing) {
        VoxelShape result=Shapes.empty();
        for(var b:s.toAabbs())result=Shapes.or(result,Shapes.create(rotate(reflect(b),facing)));
        return result;
    }
    VoxelShape get(Direction facing,boolean right) { return (right?rightShapes:shapes)[facing.get2DDataValue()]; }
    VoxelShape piece(int part,Direction facing,boolean right) { return (right?rightPieces:pieces)[facing.get2DDataValue()][part]; }
    AABB bounds(Direction facing,boolean right) { return (right?rightBounds:bounds)[facing.get2DDataValue()]; }
    static BlockPos rotate(BlockPos p, Direction facing) {
        return switch (facing) {
            case EAST -> new BlockPos(-p.getZ(),p.getY(),p.getX());
            case SOUTH -> new BlockPos(-p.getX(),p.getY(),-p.getZ());
            case WEST -> new BlockPos(p.getZ(),p.getY(),-p.getX());
            default -> p;
        };
    }
    static AABB rotate(AABB b, Direction facing) {
        return switch (facing) {
            case EAST -> new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
            case SOUTH -> new AABB(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);
            case WEST -> new AABB(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);
            default -> b;
        };
    }
}
