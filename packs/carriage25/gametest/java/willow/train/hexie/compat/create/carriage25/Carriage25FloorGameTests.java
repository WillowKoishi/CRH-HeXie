package willow.train.hexie.compat.create.carriage25;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelExtension;

/** Real NeoForge registration, world interaction, loot and Create hook checks. */
@GameTestHolder("hexie")
@PrefixGameTestTemplate(false)
public final class Carriage25FloorGameTests {
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("hexie", name); }

    @GameTest(template = "carriage25_floor_empty", batch = "carriage25")
    public static void interactions(GameTestHelper helper) {
        var level = helper.getLevel();
        var floor = BuiltInRegistries.BLOCK.get(id("carriage25g_floor_dc600"));
        var left = (BooleanProperty) floor.getStateDefinition().getProperty("side_beam_left");
        var right = (BooleanProperty) floor.getStateDefinition().getProperty("side_beam_right");
        var equipment = (IntegerProperty) floor.getStateDefinition().getProperty("equipment");
        var rotation = (IntegerProperty) floor.getStateDefinition().getProperty("equipment_rotation");
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var wrench = new ItemStack(BuiltInRegistries.ITEM.get(id("harmony_wrench")));
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var plain = floor.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            level.setBlock(pos, plain, Block.UPDATE_ALL);
            var leftHit = hit(pos, facing);
            plain.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, leftHit);
            var state = level.getBlockState(pos);
            helper.assertTrue(state.getValue(left) && !state.getValue(right), "Left beam must toggle independently");
            state.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, hit(pos, facing.getOpposite()));
            state = level.getBlockState(pos);
            helper.assertTrue(state.getValue(left) && state.getValue(right), "Right beam must preserve the left beam");
            state.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, leftHit);
            helper.assertTrue(!level.getBlockState(pos).getValue(left) && level.getBlockState(pos).getValue(right), "Left beam must switch off independently");
            for (int selected = 1; selected <= 5; selected++) {
                state = level.getBlockState(pos);
                state.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, hit(pos, Direction.DOWN));
                state = level.getBlockState(pos);
                helper.assertTrue(state.getValue(equipment) == selected % 5, "Underbody selection cycle is wrong");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                for (int turn = 1; turn <= 4; turn++) {
                    state.useWithoutItem(level, player, hit(pos, Direction.UP));
                    state = level.getBlockState(pos);
                    helper.assertTrue(state.getValue(rotation) == (selected == 5 ? 0 : turn % 4), "Empty-hand rotation is wrong");
                    helper.assertTrue(state.getValue(equipment) == selected % 5 && state.getValue(right), "Rotation lost variants");
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            }
            for (int selected = 1; selected <= 5; selected++) {
                state = level.getBlockState(pos);
                state.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, hit(pos, Direction.UP));
                state = level.getBlockState(pos);
                helper.assertTrue(state.getValue(equipment) == selected % 5, "Top-face wrench selection must match the bottom-face cycle");
                helper.assertTrue(state.getValue(rotation) == 0 && !state.getValue(left) && state.getValue(right),
                        "Top-face selection must preserve equipment rotation and independent beams");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, wrench);
            level.getBlockState(pos).useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND, hit(pos, Direction.DOWN));
            state = level.getBlockState(pos);
            helper.assertTrue(state.getValue(equipment) == 1 && state.getValue(rotation) == 0, "Offhand wrench must select equipment without rotating it");
            player.getAbilities().mayBuild = false;
            var result = state.useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND, hit(pos, Direction.DOWN));
            helper.assertTrue(result == ItemInteractionResult.FAIL && level.getBlockState(pos) == state, "Restricted players must not edit variants");
            player.getAbilities().mayBuild = true;
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        }
        helper.succeed();
    }

    @GameTest(template = "carriage25_floor_empty", batch = "carriage25")
    public static void registrationAndDrops(GameTestHelper helper) {
        var floor = BuiltInRegistries.BLOCK.get(id("carriage25g_floor_dc600"));
        var item = BuiltInRegistries.ITEM.get(id("carriage25g_floor_dc600"));
        helper.assertTrue(floor.asItem() == item, "Pack block and item registration must agree");
        helper.assertTrue(MovingInteractionBehaviour.REGISTRY.get(floor) != null, "Moving carriage interaction must be installed");
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (BlockState state : floor.getStateDefinition().getPossibleStates()) {
            var drops = Block.getDrops(state, helper.getLevel(), pos, null);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(item) && drops.getFirst().getCount() == 1,
                    "Every variant must drop exactly one floor item");
        }
        helper.succeed();
    }

    @GameTest(template = "carriage25_floor_empty", batch = "carriage25")
    public static void floorExtension(GameTestHelper helper) {
        var level = helper.getLevel();
        var floor = BuiltInRegistries.BLOCK.get(id("carriage25g_floor_dc600"));
        var source = floor.defaultBlockState();
        var left = (BooleanProperty) floor.getStateDefinition().getProperty("side_beam_left");
        var equipment = (IntegerProperty) floor.getStateDefinition().getProperty("equipment");
        var rotation = (IntegerProperty) floor.getStateDefinition().getProperty("equipment_rotation");
        helper.assertTrue(HexieBridges.partBlock().panelExtension(source) == PanelExtension.FLOOR,
                "25G must register with the shared floor placement helper");
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(pos.above(3)));
        for (InteractionHand hand : InteractionHand.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    clearExtensionArea(helper, pos);
                    var decorated = source.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                            .setValue(left, true).setValue(equipment, 3).setValue(rotation, 2);
                    level.setBlock(pos, decorated, Block.UPDATE_ALL);
                    var stack = new ItemStack(floor, 2);
                    player.setItemInHand(hand, stack);
                    decorated.useItemOn(stack, level, player, hand, extensionHit(pos, direction));
                    var placed = level.getBlockState(pos.relative(direction));
                    helper.assertTrue(placed.is(floor), "Top-face click must extend horizontally toward the nearest edge");
                    helper.assertTrue(placed.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing,
                            "Extended floor must align with the existing floor");
                    helper.assertTrue(!placed.getValue(left) && placed.getValue(equipment) == 0 && placed.getValue(rotation) == 0,
                            "New floors must retain their default variants, matching CRH1A/CRH2A placement");
                    helper.assertTrue(level.getBlockState(pos) == decorated, "Extension must preserve the source variants");
                    helper.assertTrue(level.isEmptyBlock(pos.above()) && stack.getCount() == 1,
                            "Extension must place one floor at the same height and consume one item");
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }
            }
        }
        for (String reference : new String[] {"crh1a_floor", "crh2a_floor", "ss7d_floor"}) {
            var other = BuiltInRegistries.BLOCK.get(id(reference));
            for (boolean place25G : new boolean[] {true, false}) {
                clearExtensionArea(helper, pos);
                var existing = (place25G ? other : floor).defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
                var held = place25G ? floor : other;
                level.setBlock(pos, existing, Block.UPDATE_ALL);
                var stack = new ItemStack(held, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                existing.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, extensionHit(pos, Direction.EAST));
                var placed = level.getBlockState(pos.east());
                helper.assertTrue(placed.is(held) && placed.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST,
                        "Mixed floor placement must use the held type and source orientation: " + reference);
                helper.assertTrue(stack.getCount() == 1 && level.isEmptyBlock(pos.above()), "Mixed placement must extend horizontally once");
            }
        }
        clearExtensionArea(helper, pos);
        level.setBlock(pos, source, Block.UPDATE_ALL);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            level.setBlock(pos.relative(direction), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var stack = new ItemStack(floor, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = source.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, extensionHit(pos, Direction.EAST));
        helper.assertTrue(result == ItemInteractionResult.FAIL && stack.getCount() == 2 && level.isEmptyBlock(pos.above()),
                "Blocked extension must fail without consuming an item or placing above the floor");
        for (Direction direction : Direction.Plane.HORIZONTAL)
            helper.assertTrue(level.getBlockState(pos.relative(direction)).is(Blocks.STONE), "Extension must not overwrite occupied cells");
        helper.succeed();
    }

    @GameTest(template = "carriage25_floor_empty", batch = "carriage25")
    public static void beamSideFacesPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var floor = BuiltInRegistries.BLOCK.get(id("carriage25g_floor_dc600"));
        var nearBeam = (BooleanProperty) floor.getStateDefinition().getProperty("side_beam_left");
        var farBeam = (BooleanProperty) floor.getStateDefinition().getProperty("side_beam_right");
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Direction looking : Direction.Plane.HORIZONTAL) {
            clearExtensionArea(helper, pos);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            var playerSide = looking.getOpposite();
            player.setPos(Vec3.atCenterOf(pos.relative(playerSide, 3)));
            player.setYRot(looking.toYRot());
            var stack = new ItemStack(floor, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var supportHit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.5, 0, .5), Direction.UP, pos.below(), false);
            stack.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, supportHit));
            var placed = level.getBlockState(pos);
            helper.assertTrue(placed.is(floor) && placed.getValue(BlockStateProperties.HORIZONTAL_FACING) == playerSide,
                    "Normal placement must point the rotated beam face toward the player");
            var wrench = new ItemStack(BuiltInRegistries.ITEM.get(id("harmony_wrench")));
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            placed.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, hit(pos, playerSide));
            placed = level.getBlockState(pos);
            helper.assertTrue(placed.getValue(nearBeam) && !placed.getValue(farBeam),
                    "Clicking the player-facing side must enable the beam on that face");
            placed.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND, hit(pos, looking));
            placed = level.getBlockState(pos);
            helper.assertTrue(placed.getValue(nearBeam) && placed.getValue(farBeam),
                    "The opposite side must independently enable its own beam");
        }
        helper.succeed();
    }

    private static void clearExtensionArea(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            helper.getLevel().setBlock(pos.relative(direction), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static BlockHitResult extensionHit(BlockPos pos, Direction direction) {
        return new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.5 + .49 * direction.getStepX(), 1,
                .5 + .49 * direction.getStepZ()), Direction.UP, pos, false);
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.5, .6875, .5), face, pos, false);
    }
}
