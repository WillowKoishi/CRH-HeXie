import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import willow.train.hexie.world.blocks.carriage.Carriage25GFloorBlock;

/** Exhaustive state/transform/save checks, run with check_floor.gradle. */
public final class Carriage25FloorCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Direction equipmentDirection(BlockState state) {
        Direction direction = state.getValue(Carriage25GFloorBlock.FACING).getClockWise();
        for (int i = 0; i < state.getValue(Carriage25GFloorBlock.EQUIPMENT_ROTATION); i++)
            direction = direction.getClockWise();
        return direction;
    }

    public static void main(String[] args) {
        // NeoForge's vanilla bootstrap reads mod feature flags even in this standalone process.
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // This isolated process has no mod registration phase; reopen only its block registry.
        ((net.minecraft.core.MappedRegistry<net.minecraft.world.level.block.Block>) BuiltInRegistries.BLOCK).unfreeze();
        var floor = Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("hexie", "carriage25g_floor_dc600"),
                new Carriage25GFloorBlock(BlockBehaviour.Properties.of()));
        BuiltInRegistries.BLOCK.freeze();
        var states = floor.getStateDefinition().getPossibleStates();
        check(states.size() == 320, "Expected 320 independent combinations");
        var defaultState = floor.defaultBlockState();
        check(!defaultState.getValue(Carriage25GFloorBlock.SIDE_BEAM_LEFT)
                && !defaultState.getValue(Carriage25GFloorBlock.SIDE_BEAM_RIGHT)
                && defaultState.getValue(Carriage25GFloorBlock.EQUIPMENT) == 0
                && defaultState.getValue(Carriage25GFloorBlock.EQUIPMENT_ROTATION) == 0, "Default must be a plain floor");
        for (var state : states) {
            Direction facing = state.getValue(Carriage25GFloorBlock.FACING);
            check(floor.wrenchState(state, facing) == state.cycle(Carriage25GFloorBlock.SIDE_BEAM_LEFT),
                    "Player-facing beam face must change only its beam");
            check(floor.wrenchState(state, facing.getOpposite()) == state.cycle(Carriage25GFloorBlock.SIDE_BEAM_RIGHT),
                    "Opposite beam face must change only its beam");
            check(floor.wrenchState(state, Direction.DOWN) == state.cycle(Carriage25GFloorBlock.EQUIPMENT),
                    "Bottom must change only equipment type");
            check(floor.wrenchState(state, Direction.UP) == state.cycle(Carriage25GFloorBlock.EQUIPMENT),
                    "Top must select the same equipment cycle as the bottom");
            check(floor.wrenchState(state, facing.getClockWise()) == null && floor.wrenchState(state, facing.getCounterClockWise()) == null,
                    "End faces must not change variants");
            var cycle = state;
            for (int i = 0; i < 5; i++) cycle = floor.wrenchState(cycle, Direction.DOWN);
            check(cycle == state, "Five equipment selections must return to the same state");
            var rotated = floor.rotateEquipment(state);
            check(rotated == (state.getValue(Carriage25GFloorBlock.EQUIPMENT) == 0
                    ? state : state.cycle(Carriage25GFloorBlock.EQUIPMENT_ROTATION)), "Empty hand must rotate only installed equipment");
            cycle = state;
            for (int i = 0; i < 4; i++) cycle = floor.rotateEquipment(cycle);
            check(cycle == state, "Four equipment turns must return to the same state");
            for (Rotation rotation : Rotation.values()) {
                var result = floor.rotate(state, rotation);
                check(equipmentDirection(result) == rotation.rotate(equipmentDirection(state)), "Structure rotation lost equipment orientation");
                check(result.getValue(Carriage25GFloorBlock.EQUIPMENT).equals(state.getValue(Carriage25GFloorBlock.EQUIPMENT)),
                        "Structure rotation lost equipment type");
            }
            for (Mirror mirror : Mirror.values()) {
                var result = floor.mirror(state, mirror);
                check(floor.mirror(result, mirror) == state, "Mirroring twice must restore all properties");
                check(equipmentDirection(result) == mirror.mirror(equipmentDirection(state)), "Mirrored equipment direction is wrong");
                check(result.getValue(Carriage25GFloorBlock.SIDE_BEAM_LEFT).equals(state.getValue(Carriage25GFloorBlock.SIDE_BEAM_LEFT))
                        && result.getValue(Carriage25GFloorBlock.SIDE_BEAM_RIGHT).equals(state.getValue(Carriage25GFloorBlock.SIDE_BEAM_RIGHT)),
                        "Mirroring must preserve beam flags along the reflected facing axis");
                check(floor.wrenchState(result, mirror.mirror(facing)) == floor.mirror(floor.wrenchState(state, facing), mirror),
                        "Mirrored beam clicks must still address the visible beam");
            }
            check(floor.getShape(state, null, BlockPos.ZERO, CollisionContext.empty()).bounds().equals(new AABB(0, 11.0 / 16, 0, 1, 1, 1)),
                    "Selection outline must match the floor mesh");
            check(floor.getCollisionShape(state, null, BlockPos.ZERO, CollisionContext.empty()).bounds().equals(new AABB(0, .5, 0, 1, 1, 1)),
                    "Equipment and beams must preserve the existing half-height walking collision");
            var saved = NbtUtils.writeBlockState(state);
            check(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), saved) == state,
                    "All variant properties must survive saving/loading");
        }
        System.out.println("PASS: 320 states; independent beams, five equipment types, four turns, structure transforms, collision and NBT round trips");
    }
}
