package willow.train.hexie.blocks.emu.head;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;

/** Converts only a DT206 at its declared head mount; standalone bogeys keep their own identity. */
public final class HeadBogeyMigration {
    private HeadBogeyMigration() {}

    public static boolean legacy(BlockState state, Direction facing) {
        return state.is(TrackBlockInit.DT206_BOGEY.get())
                && state.getValue(HexieBogeyBlock.HEAD_MOUNTED)
                && state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == facing.getAxis();
    }

    public static BlockState floor(Direction facing) {
        return BlockInit.CRH2A_HEAD_FLOOR.get().defaultBlockState()
                .setValue(CRH2HeadBlock.FACING, facing).setValue(HeadFloorBlock.PART, HeadGeometry.BOGEY_PART);
    }

    private static CompoundTag transfer(CompoundTag head, CompoundTag bogey, Direction facing) {
        var result = head.copy();
        var data = HeadBogey.defaultData(facing);
        if (bogey != null) data.merge(bogey.getCompound(HeadBogey.DATA));
        result.putBoolean(HeadBogey.ENABLED, true);
        result.put(HeadBogey.DATA, data);
        return result;
    }

    /** Keep support positions and runtime IDs unchanged: the mount becomes an owned floor cell. */
    public static List<CarriageSnapshot.Block> snapshot(List<CarriageSnapshot.Block> original) {
        var indices = new HashMap<BlockPos, Integer>();
        for (int i = 0; i < original.size(); i++) indices.put(original.get(i).position(), i);
        ArrayList<CarriageSnapshot.Block> result = null;
        for (int i = 0; i < original.size(); i++) {
            var root = original.get(i);
            if (!(root.state().getBlock() instanceof CRH2HeadBlock) || root.entityData() == null) continue;
            var facing = root.state().getValue(CRH2HeadBlock.FACING);
            var mount = HeadBogey.mount(root.position(), facing);
            var index = indices.get(mount);
            if (index == null || !legacy(original.get(index).state(), facing)) continue;
            if (result == null) result = new ArrayList<>(original);
            result.set(i, new CarriageSnapshot.Block(root.position(), root.state(),
                    transfer(root.entityData(), original.get(index).entityData(), facing)));
            result.set(index, new CarriageSnapshot.Block(mount, floor(facing), null));
        }
        return result == null ? original : result;
    }

    /** Normalize before requirements and traversal, so a legacy mount never consumes a second item. */
    public static void schematic(SchematicLevel level) {
        if (level == null) return;
        for (var entry : List.copyOf(level.getBlockMap().entrySet())) {
            if (!(entry.getValue().getBlock() instanceof CRH2HeadBlock)) continue;
            var root = entry.getKey().offset(level.anchor);
            var facing = entry.getValue().getValue(CRH2HeadBlock.FACING);
            var mount = HeadBogey.mount(root, facing);
            if (!legacy(level.getBlockState(mount), facing)) continue;
            var head = level.getBlockEntity(root);
            if (head == null) continue;
            var bogey = level.getBlockEntity(mount);
            var data = transfer(head.saveWithFullMetadata(level.registryAccess()),
                    bogey == null ? null : bogey.saveWithFullMetadata(level.registryAccess()), facing);
            head.loadWithComponents(data, level.registryAccess());
            level.setBlock(mount, floor(facing), 2);
        }
    }

    /** Old live settings override the old support BE; redirect them to its migrated head owner. */
    public static CompoundTag working(CompoundTag original, java.util.Map<BlockPos, StructureBlockInfo> base,
                                      RegistryAccess registries) {
        if (!original.contains("ChangedBlocks", Tag.TAG_LIST)) return original;
        var result = original.copy();
        var changes = result.getList("ChangedBlocks", Tag.TAG_COMPOUND);
        var byPosition = new HashMap<BlockPos, CompoundTag>();
        for (var value : changes) {
            var change = (CompoundTag) value;
            var pos = net.createmod.catnip.nbt.NBTHelper.readBlockPos(change, "Pos");
            if (byPosition.put(pos, change) != null) throw new IllegalArgumentException("Duplicate working block " + pos);
            var previous = base.get(pos);
            if (previous == null || !(previous.state().getBlock() instanceof CRH2HeadBlock) || previous.nbt() == null) continue;
            var data = change.getCompound("BlockEntity");
            if (!data.contains(HeadBogey.ENABLED) && previous.nbt().contains(HeadBogey.ENABLED)) {
                data.putBoolean(HeadBogey.ENABLED, previous.nbt().getBoolean(HeadBogey.ENABLED));
                data.put(HeadBogey.DATA, previous.nbt().getCompound(HeadBogey.DATA).copy());
                change.put("BlockEntity", data);
            }
        }
        for (var value : List.copyOf(changes)) {
            var change = (CompoundTag) value;
            var pos = net.createmod.catnip.nbt.NBTHelper.readBlockPos(change, "Pos");
            var previous = base.get(pos);
            if (previous == null || !(previous.state().getBlock() instanceof HeadFloorBlock)
                    || previous.state().getValue(HeadFloorBlock.PART) != HeadGeometry.BOGEY_PART) continue;
            var facing = previous.state().getValue(CRH2HeadBlock.FACING);
            var incoming = CarriageSnapshot.readState(change.getCompound("State"), registries);
            if (!legacy(incoming, facing)) continue;
            var owner = pos.subtract(HeadGeometry.offset(HeadGeometry.BOGEY_PART, facing));
            var head = base.get(owner);
            if (head == null || !(head.state().getBlock() instanceof CRH2HeadBlock) || head.nbt() == null
                    || head.state().getValue(CRH2HeadBlock.FACING) != facing) continue;
            var headChange = byPosition.get(owner);
            if (headChange == null) {
                headChange = new CompoundTag();
                headChange.put("Pos", NbtUtils.writeBlockPos(owner));
                headChange.put("State", NbtUtils.writeBlockState(head.state()));
                headChange.put("BlockEntity", head.nbt().copy());
                byPosition.put(owner, headChange);
                changes.add(headChange);
            }
            var data = headChange.getCompound("BlockEntity");
            headChange.put("BlockEntity", transfer(data, change.getCompound("BlockEntity"), facing));
            change.put("State", NbtUtils.writeBlockState(previous.state()));
            change.remove("BlockEntity");
        }
        return result;
    }
}
