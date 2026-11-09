package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;

/** Backend-owned block for an add-on's declaration; retains HeXie's existing support behavior. */
public final class RegisteredBogeyBlock extends HexieBogeyBlock<RegisteredBogeyBlockEntity> {
    private final ResourceLocation bogeyId;
    private final Supplier<BogeyStyle> style;
    private final Supplier<BlockEntityType<RegisteredBogeyBlockEntity>> entityType;

    RegisteredBogeyBlock(Properties properties, ResourceLocation bogeyId, Supplier<BogeyStyle> style,
                         Supplier<BlockEntityType<RegisteredBogeyBlockEntity>> entityType) {
        super(properties, BogeySizes.SMALL);
        this.bogeyId = Objects.requireNonNull(bogeyId);
        this.style = Objects.requireNonNull(style);
        this.entityType = Objects.requireNonNull(entityType);
    }

    public ResourceLocation bogeyId() { return bogeyId; }
    @Override public BogeyStyle getDefaultStyle() { return Objects.requireNonNull(style.get()); }
    @Override public Class<RegisteredBogeyBlockEntity> getBlockEntityClass() { return RegisteredBogeyBlockEntity.class; }
    @Override public BlockEntityType<RegisteredBogeyBlockEntity> getBlockEntityType() { return entityType.get(); }
}
