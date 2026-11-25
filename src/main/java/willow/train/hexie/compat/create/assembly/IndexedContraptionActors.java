package willow.train.hexie.compat.create.assembly;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.MutablePair;

/** Look up a live actor in Create's ordered list. */
public final class IndexedContraptionActors {
    private IndexedContraptionActors() {}

    public static MutablePair<StructureBlockInfo, MovementContext> get(Contraption contraption, BlockPos pos) {
        for (var pair : contraption.getActors())
            if (pos.equals(pair.getLeft().pos())) return pair;
        return null;
    }
}
