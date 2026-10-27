package willow.train.hexie.mixins.mixin.client;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor("scrollOffs")
    float hexie$getScrollOffs();
    @Accessor("scrollOffs")
    void hexie$setScrollOffs(float value);

    @Accessor("pages")
    java.util.List<net.neoforged.neoforge.client.gui.CreativeTabsScreenPage> hexie$getPages();

    @Invoker("selectTab")
    void hexie$selectTab(CreativeModeTab tab);

    @Accessor("selectedTab")
    CreativeModeTab hexie$getSelectedTab();
}
