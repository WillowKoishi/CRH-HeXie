package willow.train.hexie.blocks.emu.crh2a;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.blocks.emu.head.HeadGeometry;
import willow.train.hexie.blockentity.CustomRenderedDoorEntity;
import willow.train.hexie.compat.create.CRH2DoorMovement;
import willow.train.hexie.item.FloorSwitcherItem;
import willow.train.hexie.mixins.mixin.ContraptionWorldAccessor;

/** Door placement uses the authored reference cube as the supporting neighbour. */
public class CRH2DoorBlock extends CustomRenderedDoorBlock {
    public enum Type {
        PASSENGER("crh2a_passenger_door", CRH2DoorBlock.SLIDE_TRAVEL),
        WIDE_PASSENGER("crh2a_passenger_door_wide", 16.1 / 16d),
        CAB("crh2a_cab_door", 0);

        private final String model;
        private final double slideTravel;
        Type(String model, double slideTravel) {
            this.model = model;
            this.slideTravel = slideTravel;
        }
    }

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
    private static final double PASSENGER_OPENING_WIDTH = 12.1 / 16d;
    public static final double SLIDE_TRAVEL = PASSENGER_OPENING_WIDTH - 0.1;
    public static final double PASSENGER_OPEN_TRIGGER_EXTENSION = 1 / 16d;
    private static final double PASSENGER_HEIGHT = 29.8 / 16d;
    private final Type doorType;
    // Four facings, two hinges and three poses (closed, open forward, open in reverse).
    private final VoxelShape[] interactionShapes = new VoxelShape[24];
    private final VoxelShape[] halfInteractionShapes = new VoxelShape[48];

    public static double cabHingeX(boolean mirrored) {
        double x = CAB_EDGE_X + CAB_HINGE_OUTWARD;
        return mirrored ? 1-x : x;
    }
    public static double cabHingeZ() { return CAB_BACK_Z + CAB_HINGE_TOWARD_REFERENCE; }
    public static float cabAngleDegrees(boolean mirrored, float progress) {
        return (mirrored ? -1 : 1) * CAB_OPEN_DEGREES * animationCurve(progress);
    }

    public CRH2DoorBlock(Properties properties, boolean passenger) {
        this(properties, passenger ? Type.PASSENGER : Type.CAB);
    }
    public CRH2DoorBlock(Properties properties, Type doorType) {
        super(properties, models(doorType, "left"), models(doorType, "right"),
                RenderShape.ENTITYBLOCK_ANIMATED, doorType != Type.CAB);
        this.doorType = doorType;
    }
    public Type doorType() { return doorType; }
    public double slideTravel() { return doorType.slideTravel; }
    public double slideOffset(boolean mirrored, boolean reverse, float progress) {
        double offset = slideTravel() * animationCurve(progress);
        return mirrored ^ (doorType == Type.PASSENGER && reverse) ? -offset : offset;
    }
    public boolean supportsPassengerFrame() { return doorType == Type.PASSENGER; }
    private static Couple<net.minecraft.resources.ResourceLocation> models(Type doorType, String side) {
        var location = Main.asResource("crh2a/doors/"+doorType.model+"_"+side);
        return Couple.create(location, location);
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (supportsPassengerFrame() && stack.getItem() instanceof FloorSwitcherItem && !player.isShiftKeyDown()) {
            if (player.isSpectator() || !player.mayBuild()) return ItemInteractionResult.FAIL;
            BlockPos lower = state.getValue(HALF)==DoubleBlockHalf.UPPER ? pos.below() : pos;
            if (!level.getBlockState(lower).is(this)
                    || !(level.getBlockEntity(lower) instanceof CustomRenderedDoorEntity door))
                return ItemInteractionResult.FAIL;
            FloorSwitcherItem.beginUsingDoor(player, hand, door);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
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
        BlockPos lower = state.getValue(HALF)==DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level instanceof ContraptionWorldAccessor view)
            return CRH2DoorMovement.fullyOpen(view.hexie$getContraption(), lower, this) && state.getValue(OPEN);
        return state.getValue(OPEN) && level.getBlockEntity(lower) instanceof CustomRenderedDoorEntity door
                && door.crh2Progress() >= 1;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return fullyOpen(state, level, pos) ? Shapes.empty() : rotated(state, new AABB(0,0,13/16d,1,1,17/16d));
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean upper = state.getValue(HALF)==DoubleBlockHalf.UPPER;
        BlockPos root = upper ? pos.below() : pos;
        var entity = level.getBlockEntity(root);
        float progress = entity instanceof CustomRenderedDoorEntity door
                ? door.crh2InteractionProgress() : state.getValue(OPEN) ? 1 : 0;
        boolean reverse = entity instanceof CustomRenderedDoorEntity door && door.hasReverseSlide();
        int key = interactionKey(state, progress, reverse)*2+(upper ? 1 : 0);
        if (halfInteractionShapes[key]==null) {
            VoxelShape shape = interactionShape(state, progress, reverse);
            // Split only vertically: an open cab leaf extends into the neighbouring cell.
            double y = upper ? 1 : 0;
            halfInteractionShapes[key] = Shapes.joinUnoptimized(shape,
                    Shapes.create(new AABB(-4,y,-4,4,y+1,4)), BooleanOp.AND).move(0,-y,0);
        }
        return halfInteractionShapes[key];
    }

    /** Cached completed pose in the lower block's coordinates, also used by cross-cell picking. */
    public VoxelShape interactionShape(BlockState state, float endpoint) {
        return interactionShape(state, endpoint, false);
    }

    public VoxelShape interactionShape(BlockState state, float endpoint, boolean reverse) {
        reverse = reverse && doorType == Type.PASSENGER;
        int key = interactionKey(state, endpoint, reverse);
        if (interactionShapes[key]==null) interactionShapes[key]=buildInteractionShape(state,endpoint>=1 ? 1 : 0, reverse);
        return interactionShapes[key];
    }

    private int interactionKey(BlockState state, float endpoint, boolean reverse) {
        int pose = endpoint < 1 ? 0 : reverse ? 2 : 1;
        return state.getValue(FACING).get2DDataValue()*6
                +(state.getValue(HINGE)==DoorHingeSide.RIGHT ? 3 : 0)+pose;
    }

    private VoxelShape buildInteractionShape(BlockState state, float endpoint, boolean reverse) {
        boolean mirrored = state.getValue(HINGE)==DoorHingeSide.RIGHT;
        if (!isSlideDoor()) return cabLeafShape(state, mirrored, endpoint);
        // Both passenger variants share the narrow frame's interaction geometry.
        double width = PASSENGER_OPENING_WIDTH;
        VoxelShape frame = Shapes.or(
                shapeBox(state, mirrored, new AABB(width,0,13.3/16d,1,2,14.7/16d)),
                shapeBox(state, mirrored, new AABB(0,PASSENGER_HEIGHT,13.3/16d,width,2,14.7/16d)));
        if (endpoint >= 1) {
            double edge = Math.min(slideTravel(), width) - PASSENGER_OPEN_TRIGGER_EXTENSION;
            return Shapes.or(frame, shapeBox(state, mirrored,
                    new AABB(reverse ? 0 : edge, 0, 13.3/16d, reverse ? width-edge : width, PASSENGER_HEIGHT, 14.7/16d)));
        }
        return Shapes.or(frame,
                shapeBox(state, mirrored, new AABB(0,0,13.3/16d,width,1,14.3/16d)),
                shapeBox(state, mirrored, new AABB(0,1,13.3/16d,width,PASSENGER_HEIGHT,14.7/16d)));
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
