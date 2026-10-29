package willow.train.hexie.compat.create;

import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.isExtensionPole;
import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.isPistonHead;
import java.util.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlock;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.chassis.LinearChassisBlock;
import com.simibubi.create.content.contraptions.chassis.RadialChassisBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonHeadBlock;
import com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock;
import com.simibubi.create.content.contraptions.pulley.PulleyBlock;
import com.simibubi.create.content.contraptions.pulley.PulleyBlock.RopeBlock;
import com.simibubi.create.content.contraptions.pulley.PulleyBlock.MagnetBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.PushReaction;
import willow.train.hexie.vehicle.assembly.AssemblyRegion;
import willow.train.hexie.vehicle.assembly.AssemblyTraversal;

/**
 * Compatibility data for HeXie's scanner. Attachment rules adapted from Create
 * 6.0.10 Contraption (MIT, Creators of Create); no Contraption instance or scan call.
 * Only discovers positions: mutation/capture callbacks belong to assembly commit.
 */
final class CreateAssemblyWorld implements AssemblyTraversal.Source<BlockPos> {
    private final ServerLevel world;
    private final AssemblyRegion region;
    private final Set<SuperGlueEntity> glue = new HashSet<>();

    CreateAssemblyWorld(ServerLevel world, AssemblyRegion region) {
        this.world = world;
        this.region = region;
    }

    Set<SuperGlueEntity> capturedGlue() { return Set.copyOf(glue); }

    @Override
    public boolean contains(BlockPos pos) {
        return region.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    private BlockState stateAt(BlockPos pos) {
        if (!contains(pos) || world.isOutsideBuildHeight(pos)) return Blocks.AIR.defaultBlockState();
        if (!world.isLoaded(pos)) throw new IllegalStateException("Unloaded assembly position: " + pos);
        return world.getBlockState(pos);
    }

    @Override
    public AssemblyTraversal.Cell cell(BlockPos pos) {
        BlockState state = stateAt(pos);
        if (!BlockMovementChecks.isMovementNecessary(state, world, pos)) return AssemblyTraversal.Cell.EMPTY;
        return BlockMovementChecks.isMovementAllowed(state, world, pos)
                ? AssemblyTraversal.Cell.MOVABLE : AssemblyTraversal.Cell.BLOCKED;
    }

    @Override
    public boolean brittle(BlockPos pos) { return BlockMovementChecks.isBrittle(stateAt(pos)); }

    @Override
    public List<AssemblyTraversal.Face<BlockPos>> faces(BlockPos pos) {
        var result = new ArrayList<AssemblyTraversal.Face<BlockPos>>();
        BlockState state = stateAt(pos);
        for (Direction face : Direction.values()) {
            BlockPos target = pos.relative(face);
            if (!contains(target)) continue;
            BlockState other = stateAt(target);
            boolean movable = BlockMovementChecks.isMovementAllowed(other, world, target);
            if (!movable) continue;
            result.add(new AssemblyTraversal.Face<>(target, true, BlockMovementChecks.isBrittle(other),
                    SuperGlueEntity.isGlued(world, pos, face, glue),
                    BlockMovementChecks.isBlockAttachedTowards(other, world, target, face.getOpposite()),
                    state.canStickTo(other) && other.canStickTo(state),
                    state.getPistonPushReaction() == PushReaction.PUSH_ONLY || other.getPistonPushReaction() == PushReaction.PUSH_ONLY,
                    !BlockMovementChecks.isNotSupportive(state, face),
                    !BlockMovementChecks.isNotSupportive(other, face.getOpposite())));
        }
        return result;
    }

    @Override
    public List<BlockPos> attachments(BlockPos pos) {
        BlockState state = stateAt(pos);
        // A running windmill owns blocks in another entity. Discovery must not silently
        // omit those blocks or mutate the world by disassembling that entity.
        if (world.getBlockEntity(pos) instanceof WindmillBearingBlockEntity bearing && bearing.isRunning())
            throw new IllegalStateException("Stop the windmill before assembly: " + pos);
        Queue<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = Set.of();
        if (state.getBlock() instanceof AbstractChassisBlock
                && !moveChassis(world, pos, null, frontier, visited))
            throw new IllegalStateException("Attached mechanism cannot be assembled at " + pos);
        if (AllBlocks.BELT.has(state)) moveBelt(pos, frontier, visited, state);
        if (AllBlocks.GANTRY_CARRIAGE.has(state)) moveGantryPinion(world, pos, frontier, visited, state);
        if (AllBlocks.GANTRY_SHAFT.has(state)) moveGantryShaft(world, pos, frontier, visited, state);

        if (AllBlocks.STICKER.has(state) && state.getValue(StickerBlock.EXTENDED)) {
            Direction offset = state.getValue(StickerBlock.FACING);
            BlockPos attached = pos.relative(offset);
            if (!BlockMovementChecks.isNotSupportive(stateAt(attached), offset.getOpposite()))
                frontier.add(attached);
        }
        if (state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
            frontier.add(pos.relative(ChestBlock.getConnectedDirection(state)));
        if (state.getBlock() instanceof AbstractBogeyBlock<?> bogey)
            for (Direction direction : bogey.getStickySurfaces(world, pos, state))
                frontier.add(pos.relative(direction));

        // Train structures carry the attached blocks of both stationary bearing types.
        if (AllBlocks.MECHANICAL_BEARING.has(state) || AllBlocks.WINDMILL_BEARING.has(state))
            moveWindmillBearing(pos, frontier, visited, state);
        if (state.getBlock() instanceof PulleyBlock) movePulley(world, pos, frontier, visited);
        if (state.getBlock() instanceof MechanicalPistonBlock
                && !moveMechanicalPiston(world, pos, frontier, visited, state))
            throw new IllegalStateException("Attached mechanism cannot be assembled at " + pos);
        if (isExtensionPole(state)) movePistonPole(world, pos, frontier, visited, state);
        if (isPistonHead(state)) movePistonHead(world, pos, frontier, visited, state);
        if (AllBlocks.CART_ASSEMBLER.has(stateAt(pos.below()))) frontier.add(pos.below());

        return frontier.stream().filter(this::contains).map(BlockPos::immutable).distinct().toList();
    }

	protected void movePistonHead(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
								  BlockState state) {
		Direction direction = state.getValue(MechanicalPistonHeadBlock.FACING);
		BlockPos offset = pos.relative(direction.getOpposite());
		if (!visited.contains(offset)) {
			BlockState blockState = stateAt(offset);
			if (isExtensionPole(blockState) && blockState.getValue(PistonExtensionPoleBlock.FACING)
				.getAxis() == direction.getAxis())
				frontier.add(offset);
			if (blockState.getBlock() instanceof MechanicalPistonBlock) {
				Direction pistonFacing = blockState.getValue(MechanicalPistonBlock.FACING);
				if (pistonFacing == direction
					&& blockState.getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED)
					frontier.add(offset);
			}
		}
		if (state.getValue(MechanicalPistonHeadBlock.TYPE) == PistonType.STICKY) {
			BlockPos attached = pos.relative(direction);
			if (!visited.contains(attached))
				frontier.add(attached);
		}
	}

	protected void movePistonPole(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
								  BlockState state) {
		for (Direction d : Iterate.directionsInAxis(state.getValue(PistonExtensionPoleBlock.FACING)
			.getAxis())) {
			BlockPos offset = pos.relative(d);
			if (!visited.contains(offset)) {
				BlockState blockState = stateAt(offset);
				if (isExtensionPole(blockState) && blockState.getValue(PistonExtensionPoleBlock.FACING)
					.getAxis() == d.getAxis())
					frontier.add(offset);
				if (isPistonHead(blockState) && blockState.getValue(MechanicalPistonHeadBlock.FACING)
					.getAxis() == d.getAxis())
					frontier.add(offset);
				if (blockState.getBlock() instanceof MechanicalPistonBlock) {
					Direction pistonFacing = blockState.getValue(MechanicalPistonBlock.FACING);
					if (pistonFacing == d || pistonFacing == d.getOpposite()
						&& blockState.getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED)
						frontier.add(offset);
				}
			}
		}
	}

	protected void moveGantryPinion(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
									BlockState state) {
		BlockPos offset = pos.relative(state.getValue(GantryCarriageBlock.FACING));
		if (!visited.contains(offset))
			frontier.add(offset);
		Axis rotationAxis = ((IRotate) state.getBlock()).getRotationAxis(state);
		for (Direction d : Iterate.directionsInAxis(rotationAxis)) {
			offset = pos.relative(d);
			BlockState offsetState = stateAt(offset);
			if (AllBlocks.GANTRY_SHAFT.has(offsetState) && offsetState.getValue(GantryShaftBlock.FACING)
				.getAxis() == d.getAxis())
				if (!visited.contains(offset))
					frontier.add(offset);
		}
	}

	protected void moveGantryShaft(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
								   BlockState state) {
		for (Direction d : Iterate.directions) {
			BlockPos offset = pos.relative(d);
			if (!visited.contains(offset)) {
				BlockState offsetState = stateAt(offset);
				Direction facing = state.getValue(GantryShaftBlock.FACING);
				if (d.getAxis() == facing.getAxis() && AllBlocks.GANTRY_SHAFT.has(offsetState)
					&& offsetState.getValue(GantryShaftBlock.FACING) == facing)
					frontier.add(offset);
				else if (AllBlocks.GANTRY_CARRIAGE.has(offsetState)
					&& offsetState.getValue(GantryCarriageBlock.FACING) == d)
					frontier.add(offset);
			}
		}
	}

	private void moveWindmillBearing(BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
		Direction facing = state.getValue(WindmillBearingBlock.FACING);
		BlockPos offset = pos.relative(facing);
		if (!visited.contains(offset))
			frontier.add(offset);
	}

	private void moveBelt(BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
		BlockPos nextPos = BeltBlock.nextSegmentPosition(state, pos, true);
		BlockPos prevPos = BeltBlock.nextSegmentPosition(state, pos, false);
		if (nextPos != null && !visited.contains(nextPos))
			frontier.add(nextPos);
		if (prevPos != null && !visited.contains(prevPos))
			frontier.add(prevPos);
	}

	private void movePulley(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited) {
		int limit = AllConfigs.server().kinetics.maxRopeLength.get();
		BlockPos ropePos = pos;
		while (limit-- >= 0) {
			ropePos = ropePos.below();
			if (!contains(ropePos) || !world.isLoaded(ropePos))
				break;
			BlockState ropeState = stateAt(ropePos);
			Block block = ropeState.getBlock();
			if (!(block instanceof RopeBlock) && !(block instanceof MagnetBlock)) {
				if (!visited.contains(ropePos))
					frontier.add(ropePos);
				break;
			}
			// Rope/magnet parts are captured separately, without traversing their faces.
		}
	}

    @Override
    public List<BlockPos> capturedAttachments(BlockPos pos) {
        if (!(stateAt(pos).getBlock() instanceof PulleyBlock)) return List.of();
        var parts = new ArrayList<BlockPos>();
        int limit = AllConfigs.server().kinetics.maxRopeLength.get();
        BlockPos current = pos;
        while (limit-- >= 0) {
            current = current.below();
            if (!contains(current) || !world.isLoaded(current)) break;
            Block block = stateAt(current).getBlock();
            if (!(block instanceof RopeBlock) && !(block instanceof MagnetBlock)) break;
            parts.add(current.immutable());
        }
        return List.copyOf(parts);
    }

	private boolean moveMechanicalPiston(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited,
										 BlockState state) {
		Direction direction = state.getValue(MechanicalPistonBlock.FACING);
		PistonState pistonState = state.getValue(MechanicalPistonBlock.STATE);
		if (pistonState == PistonState.MOVING)
			return false;

		BlockPos offset = pos.relative(direction.getOpposite());
		if (!visited.contains(offset)) {
			BlockState poleState = stateAt(offset);
			if (AllBlocks.PISTON_EXTENSION_POLE.has(poleState) && poleState.getValue(PistonExtensionPoleBlock.FACING)
				.getAxis() == direction.getAxis())
				frontier.add(offset);
		}

		if (pistonState == PistonState.EXTENDED || MechanicalPistonBlock.isStickyPiston(state)) {
			offset = pos.relative(direction);
			if (!visited.contains(offset))
				frontier.add(offset);
		}

		return true;
	}

	private boolean moveChassis(Level world, BlockPos pos, Direction movementDirection, Queue<BlockPos> frontier,
								Set<BlockPos> visited) {
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof ChassisBlockEntity chassis))
			return false;
		BlockState state = stateAt(pos);
		AbstractChassisBlock block = (AbstractChassisBlock) state.getBlock();
		Axis axis = state.getValue(AbstractChassisBlock.AXIS);
		boolean radial = block instanceof RadialChassisBlock;
		for (Direction direction : Direction.values()) {
			if (radial && direction.getAxis() != axis) continue;
			BlockPos adjacent = pos.relative(direction);
			if (!contains(adjacent) || visited.contains(adjacent)) continue;
			BlockState other = stateAt(adjacent);
			boolean sameChassis = radial ? AllBlocks.RADIAL_CHASSIS.has(other)
					: LinearChassisBlock.isChassis(other) && LinearChassisBlock.sameKind(state, other);
			if (sameChassis && other.getValue(AbstractChassisBlock.AXIS) == axis)
				frontier.add(adjacent);
		}
		if (radial)
			collectRadialChassis(pos, state, block, axis, chassis.getRange(), frontier);
		else
			collectLinearChassis(pos, state, block, axis, chassis.getRange(), movementDirection, frontier);
		return true;
	}

	private void collectLinearChassis(BlockPos pos, BlockState state, AbstractChassisBlock block, Axis axis,
			int range, Direction movementDirection, Queue<BlockPos> frontier) {
		for (Direction facing : Iterate.directionsInAxis(axis)) {
			if (movementDirection != facing && !state.getValue(block.getGlueableSide(state, facing))) continue;
			for (int distance = 1; distance <= range; distance++) {
				BlockPos current = pos.relative(facing, distance);
				if (!contains(current)) break;
				BlockState other = stateAt(current);
				if (!BlockMovementChecks.isMovementNecessary(other, world, current)
						|| BlockMovementChecks.isBrittle(other)) break;
				frontier.add(current);
				if (BlockMovementChecks.isNotSupportive(other, facing)) break;
			}
		}
	}

	private void collectRadialChassis(BlockPos pos, BlockState state, AbstractChassisBlock block, Axis axis,
			int range, Queue<BlockPos> frontier) {
		for (Direction facing : Direction.values()) {
			if (facing.getAxis() == axis || !state.getValue(block.getGlueableSide(state, facing))) continue;
			Queue<BlockPos> pending = new ArrayDeque<>();
			Set<BlockPos> examined = new HashSet<>();
			pending.add(pos.relative(facing));
			while (!pending.isEmpty()) {
				BlockPos current = pending.remove();
				// Test the caller's selection before reading or traversing a position.
				if (!contains(current) || !examined.add(current) || !current.closerThan(pos, range + .5f)) continue;
				BlockState other = stateAt(current);
				if (!BlockMovementChecks.isMovementNecessary(other, world, current)
						|| BlockMovementChecks.isBrittle(other)) continue;
				if (!current.equals(pos)) frontier.add(current);
				for (Direction direction : Direction.values()) {
					if (direction.getAxis() == axis || current.equals(pos) && direction != facing
							|| BlockMovementChecks.isNotSupportive(other, direction)) continue;
					pending.add(current.relative(direction));
				}
			}
		}
	}
}
