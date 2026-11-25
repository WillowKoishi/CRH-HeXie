package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.render.DoorModelLocations;
import willow.train.hexie.world.blockentity.door.CustomRenderedDoorEntity;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.world.collision.EmuCollision;
import willow.train.hexie.world.placement.EmuPlacement;

/** Door placement uses the authored reference cube as the supporting neighbour. */
public class CRH2DoorBlock extends CustomRenderedDoorBlock implements InteractionBridge.NativeControlSurfaceView, CarriageBlockBridge.CRH2DoorBlockView {
    public enum Type {
        PASSENGER("crh2a_passenger_door", CRH2DoorBlock.SLIDE_TRAVEL),
        WIDE_PASSENGER("crh2a_passenger_door_wide", 16.1 / 16d - 0.1),
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
    public static final int ANIMATION_TICKS = 5 * 20;
    public static float animationCurve(float progress) { return progress * progress; }
    public static final double PASSENGER_OPENING_WIDTH = 12.1 / 16d;
    public static final double SLIDE_TRAVEL = PASSENGER_OPENING_WIDTH - 0.1;
    public static final double PASSENGER_OPEN_TRIGGER_EXTENSION = 1 / 16d;
    public static final double PASSENGER_HEIGHT = 29.8 / 16d;
    private final CRH2DoorBlock.Type doorType;
    // Four facings, two hinges and three poses (closed, open forward, open in reverse).
    private final EmuCollision.ForCRH2DoorBlock collisionShapes = new EmuCollision.ForCRH2DoorBlock();

    public static double cabHingeX(boolean mirrored) {
        double x = CAB_EDGE_X + CAB_HINGE_OUTWARD;
        return mirrored ? 1-x : x;
    }
    public static double cabHingeZ() { return CAB_BACK_Z + CAB_HINGE_TOWARD_REFERENCE; }
    public static float cabAngleDegrees(boolean mirrored, float progress) {
        return (mirrored ? -1 : 1) * CAB_OPEN_DEGREES * animationCurve(progress);
    }

    public CRH2DoorBlock(BlockBehaviour.Properties properties, boolean passenger) {
        this(properties, passenger ? CRH2DoorBlock.Type.PASSENGER : CRH2DoorBlock.Type.CAB);
    }
    public CRH2DoorBlock(BlockBehaviour.Properties properties, CRH2DoorBlock.Type doorType) {
        super(properties, models(doorType, "left"), models(doorType, "right"),
                RenderShape.ENTITYBLOCK_ANIMATED, doorType != CRH2DoorBlock.Type.CAB);
        this.doorType = doorType;
    }
    public CarriageBlockBridge.CRH2DoorBlockType doorType() { return CarriageBlockBridge.CRH2DoorBlockType.valueOf(doorType.name()); }
    @Override public int animationDurationTicks() { return doorType == CRH2DoorBlock.Type.CAB ? 0 : ANIMATION_TICKS; }
    public double slideTravel() { return doorType.slideTravel; }
    public double slideOffset(boolean mirrored, boolean reverse, float progress) {
        double offset = slideTravel() * animationCurve(progress);
        boolean negative = doorType == CRH2DoorBlock.Type.WIDE_PASSENGER || (doorType == CRH2DoorBlock.Type.PASSENGER && reverse);
        return mirrored != negative ? -offset : offset;
    }
    public boolean supportsPassengerFrame() { return doorType == CRH2DoorBlock.Type.PASSENGER; }
    private static DoorModelLocations models(CRH2DoorBlock.Type doorType, String side) {
        var location = HexieConstants.asResource("crh2a/doors/"+doorType.model+"_"+side);
        return new DoorModelLocations(location, location);
    }
    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return EmuBlockInteract.ForCRH2DoorBlock.useItemOn(this, stack, state, level, pos, player, hand, hit, () -> super.useItemOn(stack, state, level, pos, player, hand, hit));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return EmuPlacement.ForCRH2DoorBlock.getStateForPlacement(this, context, () -> super.getStateForPlacement(context));
    }
    public VoxelShape rotated(BlockState state, AABB box) {
        return collisionShapes.rotated(state, box);
    }
    public boolean fullyOpen(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos lower = state.getValue(HALF)==DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level instanceof CreateCarriageBridge.ContraptionWorldAccessorView view)
            return HexieBridges.createDoor().crh2DoorMovement().fullyOpen(view.hexie$getContraption(), lower, this) && state.getValue(OPEN);
        return state.getValue(OPEN) && level.getBlockEntity(lower) instanceof CustomRenderedDoorEntity door
                && door.crh2Progress() >= 1;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collisionShapes.getCollisionShape(this, state, level, pos, context);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collisionShapes.getShape(this, state, level, pos, context);
    }

    /** Cached completed pose in the lower block's coordinates, also used by cross-cell picking. */
    public VoxelShape interactionShape(BlockState state, float endpoint) {
        return collisionShapes.interactionShape(this, state, endpoint);
    }

    public VoxelShape interactionShape(BlockState state, float endpoint, boolean reverse) {
        return collisionShapes.interactionShape(this, state, endpoint, reverse);
    }

    public int interactionKey(BlockState state, float endpoint, boolean reverse) {
        return collisionShapes.interactionKey(state, endpoint, reverse);
    }

    public VoxelShape buildInteractionShape(BlockState state, float endpoint, boolean reverse) {
        return collisionShapes.buildInteractionShape(this, state, endpoint, reverse);
    }

    public VoxelShape shapeBox(BlockState state, boolean mirrored, AABB box) {
        return collisionShapes.shapeBox(this, state, mirrored, box);
    }

    public VoxelShape cabLeafShape(BlockState state, boolean mirrored, float progress) {
        return collisionShapes.cabLeafShape(state, mirrored, progress);
    }

    public static Vec3 cabLeafPoint(Vec3 point, boolean mirrored, float progress) {
        return EmuCollision.ForCRH2DoorBlock.cabLeafPoint(point, mirrored, progress);
    }
}
