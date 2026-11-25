package willow.train.hexie.world.collision;

import willow.train.hexie.bridge.world.CarriageBlockBridge;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge;
import willow.train.hexie.world.blockentity.door.CustomRenderedDoorEntity;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.emu.CRH1DoorBlock;
import willow.train.hexie.world.blocks.emu.CRH1DoorGeometry;
import willow.train.hexie.world.blocks.emu.CRH1DoorMemberBlock;
import willow.train.hexie.world.blocks.emu.CRH1EndFaceBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadFloorBlock;
import willow.train.hexie.world.blocks.emu.CRH1HeadGeometry;
import willow.train.hexie.world.blocks.emu.CRH2DoorBlock;
import willow.train.hexie.world.blocks.emu.CRH2EndFaceBlock;
import willow.train.hexie.world.blocks.emu.CRH2HeadBlock;
import willow.train.hexie.world.blocks.emu.CRH2PartBlock;
import willow.train.hexie.world.blocks.emu.HeadFloorBlock;
import willow.train.hexie.world.blocks.emu.HeadGeometry;
import willow.train.hexie.world.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.world.blocks.misc.DrivingConsoleBlock;
import willow.train.hexie.world.blocks.parts.PartStructures;
import willow.train.hexie.core.util.BlockRotation;

public final class EmuCollision {
    private EmuCollision() {}

    public static final class ForCRH1DoorBlock {
        private final VoxelShape[] interactions = new VoxelShape[16];

        private final VoxelShape[] outlines = new VoxelShape[32];

        private final VoxelShape[] collisions = new VoxelShape[32];

        private final VoxelShape[] collisionCells = new VoxelShape[64];

        public static int key(BlockState state, boolean open) {
            return state.getValue(DoorBlock.FACING).get2DDataValue() * 4
                    + (state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT ? 2 : 0) + (open ? 1 : 0);
        }

        public static VoxelShape box(BlockState state, AABB box) {
            box = CRH1DoorGeometry.mirror(box, state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT);
            return Shapes.create(HeadGeometry.rotate(box, state.getValue(DoorBlock.FACING)));
        }

        public VoxelShape interactionShape(BlockState state, boolean open) {
            int key = key(state, open);
            if (interactions[key] == null) {
                VoxelShape shape = Shapes.empty();
                for (AABB jamb : EmuCollision.ForCRH1DoorGeometry.JAMBS) shape = Shapes.or(shape, box(state, jamb));
                Vec3 shift = CRH1DoorGeometry.offset(open ? 1 : 0, false);
                interactions[key] = Shapes.or(shape, box(state, EmuCollision.ForCRH1DoorGeometry.LEAF.move(shift)));
            }
            return interactions[key];
        }

        public static VoxelShape half(VoxelShape shape, boolean upper) {
            double y = upper ? 1 : 0;
            return Shapes.joinUnoptimized(shape, Shapes.create(new AABB(-4, upper ? 1 : -1, -4,
                    4, upper ? 3 : 1, 4)), BooleanOp.AND).move(0, -y, 0);
        }

        public VoxelShape getShape(CRH1DoorBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            boolean open = block.fullyOpen(state, world, pos), upper = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
            int key = key(state, open) * 2 + (upper ? 1 : 0);
            if (outlines[key] == null) outlines[key] = half(block.interactionShape(state, open), upper);
            return outlines[key];
        }

        public VoxelShape getCollisionShape(CRH1DoorBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return block.collisionCellShape(state, world, pos, 0);
        }

        public VoxelShape collisionCellShape(CRH1DoorBlock block, BlockState state, BlockGetter world, BlockPos pos, int column) {
            boolean open = block.fullyOpen(state, world, pos), upper = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
            int key = key(state, open) * 2 + (upper ? 1 : 0);
            if (collisions[key] == null) {
                VoxelShape shape = Shapes.empty();
                for (AABB fixed : EmuCollision.ForCRH1DoorGeometry.FIXED) shape = Shapes.or(shape, box(state, fixed));
                if (!open) shape = Shapes.or(shape, box(state, EmuCollision.ForCRH1DoorGeometry.LEAF));
                collisions[key] = half(shape, upper);
            }
            int cell = key * 2 + column;
            if (collisionCells[cell] == null) {
                var region = Shapes.create(HeadGeometry.rotate(new AABB(column, -1, -4, column + 1, 3, 4), state.getValue(DoorBlock.FACING)));
                collisionCells[cell] = Shapes.joinUnoptimized(collisions[key], region, BooleanOp.AND);
            }
            return collisionCells[cell];
        }
    }

    public static final class ForCRH1DoorGeometry {
        public static final AABB LEAF = new AABB(5 / 16d, 0, 13.4 / 16, 27 / 16d, 2, 17 / 16d);

        public static final List<AABB> JAMBS = List.of(
                new AABB(0, 0, 13.4 / 16, 5 / 16d, 2, 17 / 16d),
                new AABB(27 / 16d, 0, 13.4 / 16, 2, 2, 17 / 16d));

        public static final List<AABB> FIXED = List.of(JAMBS.get(0), JAMBS.get(1),
                new AABB(0, 2, 14.2 / 16, 2, 44 / 16d, 2),
                new AABB(0, -15.7 / 16, 15.4 / 16, 2, 0, 33 / 16d));

        public static AABB mirror(AABB box, boolean mirrored) {
            return mirrored ? new AABB(2 - box.maxX, box.minY, box.minZ, 2 - box.minX, box.maxY, box.maxZ) : box;
        }
    }

    public static final class ForCRH1DoorMemberBlock {
        public static VoxelShape shape(CRH1DoorMemberBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, boolean collision) {
            var owner = PartStructures.resolve(world, pos, state);
            if (owner == null) return Shapes.empty();
            BlockPos source = pos.getY() > owner.pos().getY() ? owner.pos().above() : owner.pos();
            var sourceState = world.getBlockState(source);
            if (!(sourceState.getBlock() instanceof CRH1DoorBlock door)) return Shapes.empty();
            if (collision) return door.collisionCellShape(sourceState, world, source, 1)
                    .move(source.getX() - pos.getX(), 0, source.getZ() - pos.getZ());
            var shape = sourceState.getShape(world, source, context);
            return Shapes.joinUnoptimized(shape.move(source.getX() - pos.getX(), 0, source.getZ() - pos.getZ()),
                    Shapes.block(), BooleanOp.AND);
        }

        public static VoxelShape getShape(CRH1DoorMemberBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return block.shape(state, world, pos, context, false);
        }

        public static VoxelShape getCollisionShape(CRH1DoorMemberBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return block.shape(state, world, pos, context, true);
        }
    }

    public static final class ForCRH1DrivingConsoleBlock {
        private static final Map<Direction, VoxelShape> SHAPES = createShapes();

        public static VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES.get(state.getValue(DrivingConsoleBlock.FACING));
        }

        public static VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES.get(state.getValue(DrivingConsoleBlock.FACING));
        }

        public static Map<Direction, VoxelShape> createShapes() {
            var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
            AABB[] north = {
                    new AABB(-.663, 0, -.082, .125, .875, .919),
                    new AABB(.875, 0, -.082, 1.663, .875, .919),
                    new AABB(.125, 0, -.082, .875, .8125, 0)
            };
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (AABB box : north) {
                    AABB rotated = switch (facing) {
                        case EAST -> new AABB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
                        case SOUTH -> new AABB(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX, box.maxY, 1 - box.minZ);
                        case WEST -> new AABB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
                        default -> box;
                    };
                    shape = Shapes.or(shape, Shapes.create(rotated));
                }
                shapes.put(facing, shape);
            }
            return Map.copyOf(shapes);
        }
    }

    public static final class ForCRH1EndFaceBlock {
        public static VoxelShape couplerShape(CRH1EndFaceBlock block, BlockState state) { return Shapes.block(); }

        public static VoxelShape getShape(CRH1EndFaceBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.block();
        }

        public static VoxelShape getCollisionShape(CRH1EndFaceBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.block();
        }

        public static List<AABB> playerCollisionBoxes(BlockState state) {
            return CRH1EndFaceGeometry.playerBoxes(state.getValue(HorizontalDirectionalBlock.FACING));
        }

        public static void initialize() {
            PlayerCollisionShells.register(CRH1EndFaceGeometry::collisions);
        }
    }

    public static final class ForCRH1HeadBlock {
        public static List<AABB> playerCollisionBoxes(BlockState state) {
            return CRH1HeadGeometry.playerBoxes(state.getValue(CRH1HeadBlock.FACING));
        }

        public static void initialize() {
            PlayerCollisionShells.register(CRH1HeadGeometry::collisions);
        }
    }

    public static final class ForCRH1HeadFloorBlock {
        public static VoxelShape getShape(CRH1HeadFloorBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.block();
        }

        public static VoxelShape getCollisionShape(CRH1HeadFloorBlock block, BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.block();
        }
    }

    public static final class ForCRH1HeadGeometry {
        private static final Map<Direction, List<AABB>> PLAYER_BOXES = new EnumMap<>(Direction.class);

        public static List<AABB> playerBoxes(Direction facing) { return PLAYER_BOXES.get(facing); }

        public static AABB rotateBox(AABB box, Direction facing) {
            Vec3 a = CRH1HeadGeometry.rotateFacing(new Vec3(box.minX - .5, box.minY, box.minZ - .5), facing).add(.5, 0, .5);
            Vec3 b = CRH1HeadGeometry.rotateFacing(new Vec3(box.maxX - .5, box.maxY, box.maxZ - .5), facing).add(.5, 0, .5);
            return new AABB(a, b);
        }

        public static List<VoxelShape> collisions(BlockGetter world, AABB query) {
            var roots = new HashSet<BlockPos>();
            var min = new BlockPos((int) Math.floor(query.minX) - 3,
                    (int) Math.floor(query.minY) - 4, (int) Math.floor(query.minZ) - 3);
            var max = new BlockPos((int) Math.floor(query.maxX) + 3,
                    (int) Math.floor(query.maxY) + 1, (int) Math.floor(query.maxZ) + 3);
            if (world instanceof CollisionGetter chunks) {
                for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
                    for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                        var source = chunks.getChunkForCollisions(cx, cz);
                        if (!(source instanceof ChunkAccess chunk)) continue;
                        for (int sy = min.getY() >> 4; sy <= max.getY() >> 4; sy++) {
                            int index = chunk.getSectionIndex(sy * 16);
                            if (index < 0 || index >= chunk.getSections().length) continue;
                            var section = chunk.getSections()[index];
                            if (section.hasOnlyAir() || !section.maybeHas(state ->
                                    state.getBlock() instanceof CRH1HeadBlock || state.getBlock() instanceof CRH1HeadFloorBlock)) continue;
                            var low = new BlockPos(Math.max(min.getX(), cx * 16),
                                    Math.max(min.getY(), sy * 16), Math.max(min.getZ(), cz * 16));
                            var high = new BlockPos(Math.min(max.getX(), cx * 16 + 15),
                                    Math.min(max.getY(), sy * 16 + 15), Math.min(max.getZ(), cz * 16 + 15));
                            for (var pos : BlockPos.betweenClosed(low, high)) {
                                var state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                                if (state.getBlock() instanceof CRH1HeadBlock) roots.add(pos.immutable());
                                else if (state.getBlock() instanceof CRH1HeadFloorBlock floor)
                                    roots.add(floor.partRoot(world, pos, state).immutable());
                            }
                        }
                    }
            }
            var result = new ArrayList<VoxelShape>();
            for (var root : roots) {
                if (world instanceof Level level && !level.hasChunkAt(root)) continue;
                var state = world.getBlockState(root);
                if (!(state.getBlock() instanceof CRH1HeadBlock)) continue;
                for (var local : playerBoxes(state.getValue(CRH1HeadBlock.FACING))) {
                    var box = local.move(root);
                    if (box.intersects(query)) result.add(Shapes.create(box));
                }
            }
            return result;
        }

        public static void initialize() {
            // Coarse shell slabs, leaving the interior and the rear open.
            var north = List.of(new AABB(-1.14, 1, -1.5, -.93, 3.2, 1),
                    new AABB(1.93, 1, -1.5, 2.14, 3.2, 1),
                    new AABB(-1.05, 3.2, -1.65, 2.05, 3.75, 1),
                    new AABB(-.8, 1, -2.15, 1.8, 2.6, -2.0));
            for (var facing : Direction.Plane.HORIZONTAL)
                PLAYER_BOXES.put(facing, north.stream().map(box -> rotateBox(box, facing)).toList());
        }
    }

    public static final class ForCRH2DoorBlock {
        private final VoxelShape[] interactionShapes = new VoxelShape[24];

        private final VoxelShape[] halfInteractionShapes = new VoxelShape[48];

        public VoxelShape rotated(BlockState state, AABB box) {
            return Shapes.create(HeadGeometry.rotate(box, state.getValue(DoorBlock.FACING)));
        }

        public VoxelShape getCollisionShape(CRH2DoorBlock block, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return block.fullyOpen(state, level, pos) ? Shapes.empty() : block.rotated(state, new AABB(0,0,13/16d,1,1,17/16d));
        }

        public VoxelShape getShape(CRH2DoorBlock block, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            boolean upper = state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER;
            BlockPos root = upper ? pos.below() : pos;
            float progress;
            boolean reverse;
            var contraption = level instanceof CreateCarriageBridge.ContraptionWorldAccessorView view ? view.hexie$getContraption() : null;
            var info = contraption == null ? null : contraption.getBlocks().get(root);
            if (info != null && info.state().is(block) && info.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                progress = HexieBridges.createDoor().crh2DoorMovement().fullyOpen(contraption, root, block) ? 1 : 0;
                reverse = info.nbt() != null && info.nbt().getBoolean(CustomRenderedDoorEntity.REVERSE_SLIDE_TAG);
            } else {
                var entity = level.getBlockEntity(root);
                progress = entity instanceof CustomRenderedDoorEntity door
                        ? door.crh2InteractionProgress() : state.getValue(DoorBlock.OPEN) ? 1 : 0;
                reverse = entity instanceof CustomRenderedDoorEntity door && door.hasReverseSlide();
            }
            int key = block.interactionKey(state, progress, reverse)*2+(upper ? 1 : 0);
            if (halfInteractionShapes[key]==null) {
                VoxelShape shape = block.interactionShape(state, progress, reverse);
                // Split only vertically: an open cab leaf extends into the neighbouring cell.
                double y = upper ? 1 : 0;
                halfInteractionShapes[key] = Shapes.joinUnoptimized(shape,
                        Shapes.create(new AABB(-4,y,-4,4,y+1,4)), BooleanOp.AND).move(0,-y,0);
            }
            return halfInteractionShapes[key];
        }

        public VoxelShape interactionShape(CRH2DoorBlock block, BlockState state, float endpoint) {
            return block.interactionShape(state, endpoint, false);
        }

        public VoxelShape interactionShape(CRH2DoorBlock block, BlockState state, float endpoint, boolean reverse) {
            reverse = reverse && block.doorType() == CarriageBlockBridge.CRH2DoorBlockType.PASSENGER;
            int key = block.interactionKey(state, endpoint, reverse);
            if (interactionShapes[key]==null) interactionShapes[key]=block.buildInteractionShape(state,endpoint>=1 ? 1 : 0, reverse);
            return interactionShapes[key];
        }

        public int interactionKey(BlockState state, float endpoint, boolean reverse) {
            int pose = endpoint < 1 ? 0 : reverse ? 2 : 1;
            return state.getValue(DoorBlock.FACING).get2DDataValue()*6
                    +(state.getValue(DoorBlock.HINGE)==DoorHingeSide.RIGHT ? 3 : 0)+pose;
        }

        public VoxelShape buildInteractionShape(CRH2DoorBlock block, BlockState state, float endpoint, boolean reverse) {
            boolean mirrored = state.getValue(DoorBlock.HINGE)==DoorHingeSide.RIGHT;
            if (!block.isSlideDoor()) return block.cabLeafShape(state, mirrored, endpoint);
            // Both passenger variants share the narrow frame's interaction geometry.
            double width = CRH2DoorBlock.PASSENGER_OPENING_WIDTH;
            VoxelShape frame = Shapes.or(
                    block.shapeBox(state, mirrored, new AABB(width,0,13.3/16d,1,2,14.7/16d)),
                    block.shapeBox(state, mirrored, new AABB(0,CRH2DoorBlock.PASSENGER_HEIGHT,13.3/16d,width,2,14.7/16d)));
            if (endpoint >= 1) {
                double travel = block.slideOffset(false, reverse, 1);
                double leafWidth = block.doorType() == CarriageBlockBridge.CRH2DoorBlockType.WIDE_PASSENGER ? 16.1 / 16d : width;
                double minX = Math.max(0, travel - CRH2DoorBlock.PASSENGER_OPEN_TRIGGER_EXTENSION);
                double maxX = Math.min(leafWidth, leafWidth + travel + CRH2DoorBlock.PASSENGER_OPEN_TRIGGER_EXTENSION);
                return Shapes.or(frame, block.shapeBox(state, mirrored,
                        new AABB(minX, 0, 13.3/16d, maxX, CRH2DoorBlock.PASSENGER_HEIGHT, 14.7/16d)));
            }
            return Shapes.or(frame,
                    block.shapeBox(state, mirrored, new AABB(0,0,13.3/16d,width,1,14.3/16d)),
                    block.shapeBox(state, mirrored, new AABB(0,1,13.3/16d,width,CRH2DoorBlock.PASSENGER_HEIGHT,14.7/16d)));
        }

        public VoxelShape shapeBox(CRH2DoorBlock block, BlockState state, boolean mirrored, AABB box) {
            if (mirrored) box = new AABB(1-box.maxX,box.minY,box.minZ,1-box.minX,box.maxY,box.maxZ);
            return block.rotated(state,box);
        }

        public VoxelShape cabLeafShape(BlockState state, boolean mirrored, float progress) {
            VoxelShape shape = Shapes.empty();
            // Only the two completed poses are built, once. No intermediate rotation or strip subdivision.
            for (AABB section : new AABB[]{new AABB(7/16d,2/16d,13.7/16d,1,1,14.7/16d),
                    new AABB(7/16d,1,13.7/16d,1,28.4/16d,15/16d)}) {
                AABB moved = null;
                for (double px : new double[]{section.minX,section.maxX})
                    for (double pz : new double[]{section.minZ,section.maxZ}) {
                        Vec3 p = cabLeafPoint(new Vec3(px,0,pz),mirrored,progress);
                        p = HeadGeometry.rotate(p,state.getValue(DoorBlock.FACING));
                        AABB corner = new AABB(p.x,section.minY,p.z,p.x,section.maxY,p.z);
                        moved = moved==null ? corner : moved.minmax(corner);
                    }
                shape = Shapes.or(shape,Shapes.create(moved));
            }
            return shape;
        }

        public static Vec3 cabLeafPoint(Vec3 point, boolean mirrored, float progress) {
            double x = (mirrored ? 1-point.x : point.x)-CRH2DoorBlock.cabHingeX(mirrored);
            double z = point.z-CRH2DoorBlock.cabHingeZ();
            double angle = Math.toRadians(CRH2DoorBlock.cabAngleDegrees(mirrored,progress));
            return new Vec3(CRH2DoorBlock.cabHingeX(mirrored)+Math.cos(angle)*x+Math.sin(angle)*z, point.y,
                    CRH2DoorBlock.cabHingeZ()-Math.sin(angle)*x+Math.cos(angle)*z);
        }
    }

    public static final class ForCRH2DrivingConsoleBlock {
        private static final Map<Direction, VoxelShape> SHAPES = createShapes();

        public static Map<Direction, VoxelShape> createShapes() {
            var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
            // OBJ coordinates, shifted to the block centre in X/Z. Only the two cabinets
            // and the central vertical panel are solid; omit the pedal and desktop.
            double[][] boxes = {
                    {-1.0125, 0, -.04375, -.375, 1, .4},
                    {-.375, 0, -.04375, .375, 1, .025},
                    {.375, 0, -.04375, 1.0125, 1, .4}
            };
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (double[] b : boxes) {
                    double minX = Double.POSITIVE_INFINITY, minZ = minX;
                    double maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
                    for (double x : new double[] {b[0], b[3]})
                        for (double z : new double[] {b[2], b[5]}) {
                            double rx = switch (facing) {
                                case EAST -> -z;
                                case SOUTH -> -x;
                                case WEST -> z;
                                default -> x;
                            };
                            double rz = switch (facing) {
                                case EAST -> x;
                                case SOUTH -> -z;
                                case WEST -> -x;
                                default -> z;
                            };
                            minX = Math.min(minX, rx + .5); maxX = Math.max(maxX, rx + .5);
                            minZ = Math.min(minZ, rz + .5); maxZ = Math.max(maxZ, rz + .5);
                        }
                    shape = Shapes.or(shape, Shapes.box(minX, b[1], minZ, maxX, b[4], maxZ));
                }
                shapes.put(facing, shape);
            }
            return shapes;
        }

        public static VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES.get(state.getValue(DrivingConsoleBlock.FACING));
        }

        public static VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES.get(state.getValue(DrivingConsoleBlock.FACING));
        }
    }

    public static final class ForCRH2EndFaceBlock {
        private static final VoxelShape FLOOR = Shapes.block();

        private static final Map<Direction, List<AABB>> PLAYER_BOXES = playerBoxes();

        public static VoxelShape couplerShape(BlockState state) { return FLOOR; }

        public static VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return FLOOR;
        }

        public static VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return FLOOR;
        }

        public static List<AABB> playerBoxes(Direction facing) { return PLAYER_BOXES.get(facing); }

        public static AABB bounds(Direction facing) {
            // Union of the three static end-face variants; installation and collision stay one floor cell.
            return HeadGeometry.rotate(new AABB(-19 / 16d, -.0875, -.5, 35 / 16d, 3.9125, 2.9625), facing);
        }

        public static Map<Direction, List<AABB>> playerBoxes() {
            // Export coordinates: x=.5-X/16, y=1+Y/16, z=(8-Z)/16.
            // Both the end wall and inner gangway leave the requested [0,1] x [1,3] opening.
            List<AABB> north = List.of(
                    new AABB(-19 / 16d, 13 / 16d, -1 / 16d, 0, 3.5, 0),
                    new AABB(1, 13 / 16d, -1 / 16d, 35 / 16d, 3.5, 0),
                    new AABB(0, 3, -1 / 16d, 1, 3.5, 0),
                    new AABB(0, 13 / 16d, -1 / 16d, 1, 1, 0),
                    new AABB(-2 / 16d, 14 / 16d, -5 / 16d, 0, 51 / 16d, -1 / 16d),
                    new AABB(1, 14 / 16d, -5 / 16d, 18 / 16d, 51 / 16d, -1 / 16d),
                    new AABB(0, 3, -5 / 16d, 1, 51 / 16d, -1 / 16d),
                    new AABB(0, 14 / 16d, -5 / 16d, 1, 1, -1 / 16d),
                    // Only the outward 3/16 slab collides, matching CRH2A_SKIRT_NORMAL.
                    new AABB(-19 / 16d, -.0875, -1 / 16d, -1, 1, 2),
                    new AABB(2, -.0875, -1 / 16d, 35 / 16d, 1, 2));
            Map<Direction, List<AABB>> result = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL)
                result.put(facing, north.stream().map(box -> HeadGeometry.rotate(box, facing)).toList());
            return Map.copyOf(result);
        }
    }

    public static final class ForCRH2HeadBlock {
        public static VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return HeadGeometry.floor(HeadGeometry.ROOT_PART,s.getValue(CRH2HeadBlock.FACING)); }

        public static VoxelShape getCollisionShape(CRH2HeadBlock block, BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return block.getShape(s,w,p,c); }
    }

    public static final class ForCRH2PartBlock {
        private static final VoxelShape FLOOR_COLLISION = Block.box(0, 8, 0, 16, 16, 16);

        public static boolean hasRemoteShape(CRH2PartBlock block) { return block.geometry().remote(); }

        public static VoxelShape getShape(CRH2PartBlock block, BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
            return block.geometry().piece(s.getValue(CRH2PartBlock.PART),s.getValue(HorizontalBlockBase.FACING),CRH2PartBlock.right(s));
        }

        public static VoxelShape getCollisionShape(CRH2PartBlock block, BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
            return block.isFloor() && s.getValue(CRH2PartBlock.PART) == 0 ? FLOOR_COLLISION : block.getShape(s,w,p,c);
        }
    }

    public static final class ForHeadFloorBlock {
        public static VoxelShape getShape(BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return HeadGeometry.floor(s.getValue(HeadFloorBlock.PART),s.getValue(CRH2HeadBlock.FACING)); }

        public static VoxelShape getCollisionShape(HeadFloorBlock block, BlockState s,BlockGetter w,BlockPos p,CollisionContext c) { return block.getShape(s,w,p,c); }
    }

    public static final class ForHeadGeometry {
        private static final AABB FLOOR = new AABB(-19/16d, 0, -8, 35/16d, 1, 1);

        private static final Map<Direction,List<AABB>> COLLISION = new EnumMap<>(Direction.class);

        private static final VoxelShape[][] FLOOR_SHAPES = new VoxelShape[4][HeadGeometry.PARTS];

        public static Vec3 rotate(Vec3 p, Direction facing) {
            return BlockRotation.rotate(p, facing);
        }

        public static AABB rotate(AABB b, Direction facing) {
            return BlockRotation.rotate(b, facing);
        }

        public static VoxelShape floor(int part, Direction facing) {
            return FLOOR_SHAPES[facing.get2DDataValue()][part];
        }

        public static List<AABB> boxes(Direction facing) { return COLLISION.get(facing); }

        public static AABB bounds(Direction facing) {
            // The simplified collision markers do not include every visible trim piece.
            return COLLISION.get(facing).stream().reduce(
                    rotate(new AABB(-20/16d,-2/16d,-144/16d,36/16d,64/16d,17/16d), facing), AABB::minmax);
        }

        public static List<VoxelShape> collisions(BlockGetter world, AABB query) {
            var roots=new HashSet<BlockPos>();
            var min = new BlockPos((int)Math.floor(query.minX)-2, (int)Math.floor(query.minY)-4, (int)Math.floor(query.minZ)-2);
            var max = new BlockPos((int)Math.floor(query.maxX)+2, (int)Math.floor(query.maxY)+1, (int)Math.floor(query.maxZ)+2);
            if (world instanceof CollisionGetter chunks) {
                for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
                    for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                        // The native collision accessor never loads/generates a missing chunk.
                        var source = chunks.getChunkForCollisions(cx, cz);
                        if (source == null) continue;
                        var low = new BlockPos(Math.max(min.getX(), cx*16), min.getY(), Math.max(min.getZ(), cz*16));
                        var high = new BlockPos(Math.min(max.getX(), cx*16+15), max.getY(), Math.min(max.getZ(), cz*16+15));
                        if (source instanceof ChunkAccess chunk) {
                            for (int sy = low.getY() >> 4; sy <= high.getY() >> 4; sy++) {
                                int index = chunk.getSectionIndex(sy*16);
                                if (index < 0 || index >= chunk.getSections().length) continue;
                                var section = chunk.getSection(index);
                                // Most queries have no train shell nearby. Check the section palette
                                // before visiting individual cells; no persistent world cache is needed.
                                if (section.hasOnlyAir() || !section.maybeHas(HeadGeometry::shellCell)) continue;
                                var from = new BlockPos(low.getX(), Math.max(low.getY(), sy*16), low.getZ());
                                var to = new BlockPos(high.getX(), Math.min(high.getY(), sy*16+15), high.getZ());
                                for (var p : BlockPos.betweenClosed(from, to))
                                    collectRoot(world, p, section.getBlockState(p.getX()&15, p.getY()&15, p.getZ()&15), roots);
                            }
                        } else {
                            // Virtual/custom collision getters retain their own block lookup rules.
                            scanRoots(world, low, high, roots);
                        }
                    }
            } else {
                scanRoots(world, min, max, roots);
            }
            if (roots.isEmpty()) return List.of();
            var result=new ArrayList<VoxelShape>();
            for (var root:roots) {
                var state=world.getBlockState(root);
                var facing=state.getValue(CRH2HeadBlock.FACING);
                var collisionBoxes=state.getBlock() instanceof CRH2EndFaceBlock
                        ? CRH2EndFaceBlock.playerBoxes(facing) : boxes(facing);
                for (var box:collisionBoxes) {
                    var shifted=box.move(root);
                    if (shifted.intersects(query)) result.add(Shapes.create(shifted));
                }
            }
            return result;
        }

        public static boolean shellCell(BlockState state) {
            return state.getBlock() instanceof CRH2HeadBlock || state.getBlock() instanceof HeadFloorBlock
                    || state.getBlock() instanceof CRH2EndFaceBlock;
        }

        public static void scanRoots(BlockGetter world, BlockPos min, BlockPos max, Set<BlockPos> roots) {
            for (var p : BlockPos.betweenClosed(min, max)) {
                if (world instanceof Level level && !level.hasChunkAt(p)) continue;
                collectRoot(world, p, world.getBlockState(p), roots);
            }
        }

        public static void collectRoot(BlockGetter world, BlockPos p, BlockState state, Set<BlockPos> roots) {
            if (state.getBlock() instanceof CRH2HeadBlock || state.getBlock() instanceof CRH2EndFaceBlock) {
                roots.add(p.immutable());
            } else if (state.getBlock() instanceof HeadFloorBlock) {
                var root=HeadGeometry.owner(world,p);
                if (root!=null) roots.add(root.immutable());
            }
        }

        public static void initialize() {
            var boxes = new ArrayList<AABB>();
            boxes.add(FLOOR);
            try (var in = HeadGeometry.class.getResourceAsStream("/data/hexie/crh2a_head_collision.json")) {
                if (in == null) throw new IllegalStateException("Missing CRH2A collision geometry");
                for (var entry : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    var a = entry.getAsJsonArray();
                    boxes.add(new AABB(a.get(2).getAsDouble()/16, a.get(3).getAsDouble()/16, a.get(4).getAsDouble()/16,
                            a.get(5).getAsDouble()/16, a.get(6).getAsDouble()/16, a.get(7).getAsDouble()/16));
                }
            } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
            for (var facing : Direction.Plane.HORIZONTAL) {
                COLLISION.put(facing, boxes.stream().map(b -> rotate(b, facing)).toList());
                for (int part = 0; part < HeadGeometry.PARTS; part++) {
                    var p = HeadGeometry.offset(part, Direction.NORTH);
                    var at = HeadGeometry.offset(part, facing);
                    FLOOR_SHAPES[facing.get2DDataValue()][part] = Shapes.create(
                            rotate(FLOOR.intersect(new AABB(p)), facing).move(-at.getX(), 0, -at.getZ()));
                }
            }
        }
    }

    public static final class ForTestDrivingConsoleBlock {
        public static VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            var panel = switch (state.getValue(DrivingConsoleBlock.FACING)) {
                case EAST -> Block.box(1.12, 16, .32, 5.76, 27.52, 15.68);
                case SOUTH -> Block.box(.32, 16, 1.12, 15.68, 27.52, 5.76);
                case WEST -> Block.box(10.24, 16, .32, 14.88, 27.52, 15.68);
                default -> Block.box(.32, 16, 10.24, 15.68, 27.52, 14.88);
            };
            return Shapes.or(Block.box(0, 0, 0, 16, 16, 16), panel);
        }

        public static VoxelShape getCollisionShape(TestDrivingConsoleBlock block, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return block.getShape(state, level, pos, context);
        }
    }
}
