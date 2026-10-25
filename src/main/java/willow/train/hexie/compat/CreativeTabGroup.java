package willow.train.hexie.compat;

import net.minecraft.world.item.ItemStack;

/** A named item category used to build the registered 1.21 creative tabs. */
public abstract class CreativeTabGroup {
    private final String translationKey;
    protected CreativeTabGroup(String translationKey) { this.translationKey = translationKey; }
    public String translationKey() { return translationKey; }
    private net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> tab;
    public void bind(net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> tab) { this.tab = tab; }
    public net.minecraft.world.item.CreativeModeTab getId() { return tab.get(); }
    public net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> holder() { return tab; }
    public abstract ItemStack makeIcon();
}
