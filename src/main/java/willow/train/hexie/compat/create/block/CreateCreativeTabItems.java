package willow.train.hexie.compat.create.block;

import com.simibubi.create.foundation.data.CreateRegistrate;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.core.util.general.CreativeTabGroup;

public final class CreateCreativeTabItems {
    private CreateCreativeTabItems() {}

    public static void append(CreativeTabGroup group, Set<ResourceLocation> added, CreativeModeTab.Output output) {
        for (var entry : CreateBootstrap.registrate().getAll(Registries.ITEM)) {
            ResourceLocation key = entry.getId();
            if (CreateRegistrate.isInCreativeTab(entry, group.holder()) && BuiltInRegistries.ITEM.containsKey(key)
                    && added.add(key)) {
                output.accept(BuiltInRegistries.ITEM.get(key));
            }
        }
    }
}
