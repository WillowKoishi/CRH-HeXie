package willow.train.hexie.compat.create.bogey;

import java.util.List;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2HeadBlockView;

final class CreateHeadBogeySchematicMigration {
    private CreateHeadBogeySchematicMigration() {}

    static void migrate(Object schematic) {
        var level = (SchematicLevel) schematic;
        var migration = HexieBridges.carriageBlock().headBogeyMigration();
        for (var entry : List.copyOf(level.getBlockMap().entrySet())) {
            if (!(entry.getValue().getBlock() instanceof CRH2HeadBlockView)) continue;
            var root = entry.getKey().offset(level.anchor);
            var facing = entry.getValue().getValue(BlockStateProperties.HORIZONTAL_FACING);
            var mount = HexieBridges.carriageBlock().headBogey().mount(root, facing);
            if (!migration.legacy(level.getBlockState(mount), facing)) continue;
            var head = level.getBlockEntity(root);
            if (head == null) continue;
            var bogey = level.getBlockEntity(mount);
            var data = migration.transfer(head.saveWithFullMetadata(level.registryAccess()),
                    bogey == null ? null : bogey.saveWithFullMetadata(level.registryAccess()), facing);
            head.loadWithComponents(data, level.registryAccess());
            level.setBlock(mount, migration.floor(facing), 2);
        }
    }
}
