package willow.train.hexie.api.railway;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Resolve block/item suppliers only after their Minecraft registries have been populated. */
public record RailwayHandle(ResourceLocation id, RailwayDefinition definition,
                            Supplier<Block> block, Supplier<Item> item) {
    public RailwayHandle {
        Objects.requireNonNull(id); Objects.requireNonNull(definition);
        Objects.requireNonNull(block); Objects.requireNonNull(item);
    }
}
