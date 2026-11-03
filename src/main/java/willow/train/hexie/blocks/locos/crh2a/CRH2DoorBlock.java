package willow.train.hexie.blocks.locos.crh2a;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.blocks.locos.head.HeadGeometry;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;

/** Door placement uses the authored reference cube as the supporting neighbour. */
public class CRH2DoorBlock extends CustomRenderedDoorBlock {
    // Model units are 1/16 block. Adjust these constants to tune the cab hinge.
    public static final double CAB_EDGE_X = 1;
    // Complete crh2a_head_adapted's cab reference: [-24,0,-40]..[-8,16,-24].
    // Standalone leaves are translated +16Y; its own reference is misplaced and is not used.
    public static final double CAB_BACK_Z = 15 / 16d;
    public static final double CAB_HINGE_OUTWARD = 1 / 16d;
    public static final double CAB_HINGE_TOWARD_REFERENCE = 2 / 16d;
    public static final float CAB_OPEN_DEGREES = 90;
    public static final float ANIMATION_SPEED = .15f;
    public static float animationCurve(float progress) { return progress * progress; }
    public static final double SLIDE_TRAVEL = 12.1 / 16d;
    public static final double PASSENGER_OPEN_TRIGGER_EXTENSION = 1 / 16d;
    private static final double PASSENGER_HEIGHT = 29.8 / 16d;
    private final VoxelShape[] interactionShapes = new VoxelShape[16];
    private final VoxelShape[] halfInteractionShapes = new VoxelShape[32];

    public static double cabHingeX(boolean mirrored) {
        double x = CAB_EDGE_X + CAB_HINGE_OUTWARD;
        return mirrored ? 1-x : x;
    }
    public static double cabHingeZ() { return CAB_BACK_Z + CAB_HINGE_TOWARD_REFERENCE; }
    public static float cabAngleDegrees(boolean mirrored, float progress) {
        return (mirrored ? -1 : 1) * CAB_OPEN_DEGREES * animationCurve(progress);
    }

    public CRH2DoorBlock(Properties properties, boolean passenger) {
        super(properties, models(passenger, "left"), models(passenger, "right"), RenderShape.ENTITYBLOCK_ANIMATED, passenger);
    }
    private static Couple<net.minecraft.resources.ResourceLocation> models(boolean passenger, String side) {
        var location = Main.asResource("crh2a/doors/crh2a_"+(passenger ? "passenger" : "cab")+"_door_"+side);
        return Couple.create(location, location);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        var hit = context.getClickLocation();
        var pos = context.getClickedPos();
        Direction right = facing.getClockWise();
        double side = (hit.x-pos.getX()-.5)*right.getStepX()+(hit.z-pos.getZ()-.5)*right.getStepZ();
        // The cab source leaf occupies the right of its reference; the passenger source occupies the left.
        boolean mirrored = isSlideDoor() ? side > 0 : side < 0;
        return state.setValue(FACING, facing).setValue(HINGE, mirrored ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }
    private VoxelShape rotated(BlockState state, AABB box) {
        return Shapes.create(HeadGeometry.rotate(box, state.getValue(FACING)));
    }
    private boolean fullyOpen(BlockState state, BlockGetter level, BlockPos pos) {
        if (!state.getValue(OPEN)) return false;
        BlockPos lower = state.getValue(HALF)==DoubleBlockHalf.UPPER ? pos.below() : pos;
        return level.getBlockEntity(lower) instanceof CustomRenderedDoorEntity door && door.crh2Progress() >= 1;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return fullyOpen(state, level, pos) ? Shapes.empty() : rotated(state, new AABB(0,0,13/16d,1,1,17/16d));
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean upper = state.getValue(HALF)==DoubleBlockHalf.UPPER;
        BlockPos root = upper ? pos.below() : pos;
        float progress = level.getBlockEntity(root) instanceof CustomRenderedDoorEntity door
                ? door.crh2InteractionProgress() : state.getValue(OPEN) ? 1 : 0;
        int key = interactionKey(state, progress)*2+(upper ? 1 : 0);
        if (halfInteractionShapes[key]==null) {
            VoxelShape shape = interactionShape(state, progress);
            // Split only vertically: an open cab leaf extends into the neighbouring cell.
            double y = upper ? 1 : 0;
            halfInteractionShapes[key] = Shapes.joinUnoptimized(shape,
                    Shapes.create(new AABB(-4,y,-4,4,y+1,4)), BooleanOp.AND).move(0,-y,0);
        }
        return halfInteractionShapes[key];
    }

    /** Cached completed pose in the lower block's coordinates, also used by cross-cell picking. */
    public VoxelShape interactionShape(BlockState state, float endpoint) {
        int key = interactionKey(state, endpoint);
        if (interactionShapes[key]==null) interactionShapes[key]=buildInteractionShape(state,endpoint>=1 ? 1 : 0);
        return interactionShapes[key];
    }

    private int interactionKey(BlockState state, float endpoint) {
        return state.getValue(FACING).get2DDataValue()*4
                +(state.getValue(HINGE)==DoorHingeSide.RIGHT ? 2 : 0)+(endpoint>=1 ? 1 : 0);
    }

    private VoxelShape buildInteractionShape(BlockState state, float endpoint) {
        boolean mirrored = state.getValue(HINGE)==DoorHingeSide.RIGHT;
        if (!isSlideDoor()) return cabLeafShape(state, mirrored, endpoint);
        // Authored leaf is 12.1 x 29.8 units, flush with the bottom and one side of a 16 x 32 opening.
        // Its complement is the reserved frame, not an arbitrary pair of narrow side strips.
        VoxelShape frame = Shapes.or(
                shapeBox(state, mirrored, new AABB(SLIDE_TRAVEL,0,13.3/16d,1,2,14.7/16d)),
                shapeBox(state, mirrored, new AABB(0,PASSENGER_HEIGHT,13.3/16d,SLIDE_TRAVEL,2,14.7/16d)));
        if (endpoint >= 1) return Shapes.or(frame,
                // The open leaf starts at the frame; extend its clickable edge into the opening.
                shapeBox(state, mirrored, new AABB(SLIDE_TRAVEL-PASSENGER_OPEN_TRIGGER_EXTENSION,0,13.3/16d,
                        SLIDE_TRAVEL,PASSENGER_HEIGHT,14.7/16d)));
        return Shapes.or(frame,
                shapeBox(state, mirrored, new AABB(0,0,13.3/16d,SLIDE_TRAVEL,1,14.3/16d)),
                shapeBox(state, mirrored, new AABB(0,1,13.3/16d,SLIDE_TRAVEL,PASSENGER_HEIGHT,14.7/16d)));
    }

    private VoxelShape shapeBox(BlockState state, boolean mirrored, AABB box) {
        if (mirrored) box = new AABB(1-box.maxX,box.minY,box.minZ,1-box.minX,box.maxY,box.maxZ);
        return rotated(state,box);
    }

    private VoxelShape cabLeafShape(BlockState state, boolean mirrored, float progress) {
        VoxelShape shape = Shapes.empty();
        // Only the two completed poses are built, once. No intermediate rotation or strip subdivision.
        for (AABB section : new AABB[]{new AABB(7/16d,2/16d,13.7/16d,1,1,14.7/16d),
                new AABB(7/16d,1,13.7/16d,1,28.4/16d,15/16d)}) {
            AABB moved = null;
            for (double px : new double[]{section.minX,section.maxX})
                for (double pz : new double[]{section.minZ,section.maxZ}) {
                    Vec3 p = cabLeafPoint(new Vec3(px,0,pz),mirrored,progress);
                    p = HeadGeometry.rotate(p,state.getValue(FACING));
                    AABB corner = new AABB(p.x,section.minY,p.z,p.x,section.maxY,p.z);
                    moved = moved==null ? corner : moved.minmax(corner);
                }
            shape = Shapes.or(shape,Shapes.create(moved));
        }
        return shape;
    }

    public static Vec3 cabLeafPoint(Vec3 point, boolean mirrored, float progress) {
        double x = (mirrored ? 1-point.x : point.x)-cabHingeX(mirrored);
        double z = point.z-cabHingeZ();
        double angle = Math.toRadians(cabAngleDegrees(mirrored,progress));
        return new Vec3(cabHingeX(mirrored)+Math.cos(angle)*x+Math.sin(angle)*z, point.y,
                cabHingeZ()-Math.sin(angle)*x+Math.cos(angle)*z);
    }
}
