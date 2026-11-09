package willow.train.hexie.api.bogey;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Resolve the supplied block/item only after the corresponding registry event. */
public record BogeyHandle(ResourceLocation id, BogeyDefinition definition,
                          Supplier<Block> block, Supplier<Item> item) {
    public BogeyHandle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(item, "item");
    }
}
