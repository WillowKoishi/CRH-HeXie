package willow.train.hexie.client.render.carriage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A moving part owned by one live Create carriage visual. */
public abstract class CarriageBlockVisual<T extends BlockEntity> implements BlockEntityVisual<T>, SimpleDynamicVisual {
    private static final ConcurrentHashMap<BlockEntity, CarriageBlockVisual<?>> ACTIVE = new ConcurrentHashMap<>();
    protected final T blockEntity;

    protected CarriageBlockVisual(T blockEntity) {
        this.blockEntity = blockEntity;
    }

    public final void attach() {
        ACTIVE.put(blockEntity, this);
    }

    public static boolean ownsParts(BlockEntity blockEntity) {
        return ACTIVE.containsKey(blockEntity);
    }

    public static boolean replacesRenderer(BlockEntity blockEntity) {
        var visual = ACTIVE.get(blockEntity);
        return visual != null && visual.rendersEntireBlock();
    }

    protected boolean rendersEntireBlock() { return true; }
    @Override public void update(float partialTick) {}
    @Override public void collectCrumblingInstances(Consumer<Instance> consumer) {}

    @Override public final void delete() {
        ACTIVE.remove(blockEntity, this);
        deleteInstances();
    }

    protected abstract void deleteInstances();
}
