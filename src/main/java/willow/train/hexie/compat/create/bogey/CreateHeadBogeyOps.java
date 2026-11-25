package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.track.ITrackBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.CreateHeadBogeyOperations;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;

public final class CreateHeadBogeyOps implements CreateHeadBogeyOperations {
    public static final CreateHeadBogeyOps INSTANCE = new CreateHeadBogeyOps();

    private CreateHeadBogeyOps() {}

    @Override public BooleanProperty headMountedProperty() {
        return HexieBogeyBlock.HEAD_MOUNTED;
    }

    @Override public boolean isHeadMountedBogey(BlockState state) {
        return state.getBlock() instanceof HexieBogeyBlock<?> && state.getValue(HexieBogeyBlock.HEAD_MOUNTED);
    }

    @Override public String defaultBogeyStyleId() {
        var block = (AbstractBogeyBlock<?>) HexieBridges.trackRegistry().trackBlockInit().dt206Bogey();
        return block.getDefaultStyle().id.toString();
    }

    @Override public boolean trackEnables(Level level, BlockPos root, Direction facing) {
        var trackPos = HexieBridges.carriageBlock().headBogey().mount(root, facing).below();
        if (!level.hasChunkAt(trackPos)) return false;
        var rail = level.getBlockState(trackPos);
        if (!(rail.getBlock() instanceof ITrackBlock track)) return false;
        var normal = track.getUpNormal(level, trackPos, rail);
        var axes = track.getTrackAxes(level, trackPos, rail);
        if (Math.abs(normal.y - 1) > 1e-6 || axes.size() != 1) return false;
        var axis = axes.getFirst();
        return Math.abs(axis.y) <= 1e-6
                && (facing.getAxis() == Direction.Axis.X
                    ? Math.abs(axis.z) <= 1e-6 && Math.abs(axis.x) > 1e-6
                    : Math.abs(axis.x) <= 1e-6 && Math.abs(axis.z) > 1e-6);
    }

    /** Convert only an old support owned by this root. */
    @Override public void migrateLegacy(Level level, BlockPos root, CRH2HeadEntityView head, Direction facing) {
        var mount = HexieBridges.carriageBlock().headBogey().mount(root, facing);
        var old = level.getBlockState(mount);
        var carriage = HexieBridges.carriageBlock();
        var migration = carriage.headBogeyMigration();
        if (!migration.legacy(old, facing) || !root.equals(carriage.headGeometry().owner(level, mount))) return;
        var data = legacyBogeyData(level, mount);
        if (data == null) return;
        var rootState = level.getBlockState(root);
        // Include the root so rollback restores its previous block entity data.
        HexieBridges.partBlock().partInstallation().replace(level, Map.of(root, rootState, mount, old),
                Map.of(root, rootState, mount, migration.floor(facing)),
                at -> false, at -> true, () -> head.updateBogey(head.bogeyEnabled(), data));
    }

    @Override public void migrateSchematic(Object level) {
        CreateHeadBogeySchematicMigration.migrate(level);
    }

    private static @Nullable CompoundTag legacyBogeyData(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HexieBogeyBlockEntity bogey ? bogey.getBogeyData().copy() : null;
    }
}
