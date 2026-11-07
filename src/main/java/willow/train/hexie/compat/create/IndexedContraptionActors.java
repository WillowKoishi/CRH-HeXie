package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.RandomAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.MutablePair;

/** Maintained by Create's existing actor add/read/remove operations; content edits never scan actors. */
public class IndexedContraptionActors extends AbstractList<MutablePair<StructureBlockInfo, MovementContext>> implements RandomAccess {
    private final ArrayList<MutablePair<StructureBlockInfo, MovementContext>> entries = new ArrayList<>();
    private final Map<BlockPos, MutablePair<StructureBlockInfo, MovementContext>> byPosition = new HashMap<>();

    public static MutablePair<StructureBlockInfo, MovementContext> get(Contraption contraption, BlockPos pos) {
        return contraption.getActors() instanceof IndexedContraptionActors indexed ? indexed.byPosition.get(pos) : null;
    }
    @Override public MutablePair<StructureBlockInfo, MovementContext> get(int index) { return entries.get(index); }
    @Override public int size() { return entries.size(); }
    @Override public void add(int index, MutablePair<StructureBlockInfo, MovementContext> entry) {
        entries.add(index, entry); byPosition.put(entry.left.pos().immutable(), entry); modCount++;
    }
    @Override public MutablePair<StructureBlockInfo, MovementContext> set(int index, MutablePair<StructureBlockInfo, MovementContext> entry) {
        var previous = entries.set(index, entry);
        byPosition.remove(previous.left.pos(), previous); byPosition.put(entry.left.pos().immutable(), entry);
        return previous;
    }
    @Override public MutablePair<StructureBlockInfo, MovementContext> remove(int index) {
        var previous = entries.remove(index); byPosition.remove(previous.left.pos(), previous); modCount++;
        return previous;
    }
    @Override public void clear() { entries.clear(); byPosition.clear(); modCount++; }
}
