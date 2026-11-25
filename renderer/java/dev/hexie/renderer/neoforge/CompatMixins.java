package dev.hexie.renderer.neoforge;

import java.util.*;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class CompatMixins implements IMixinConfigPlugin {
    public boolean shouldApplyMixin(String target,String mixin) {
        var mods=LoadingModList.get();
        if (mixin.contains(".sodium.mixin.")) return mods.getModFileById("sodium")!=null;
        if (mixin.contains(".flywheel.mixin.")) return mods.getModFileById("flywheel")!=null && mods.getModFileById("sodium")!=null;
        if (mixin.contains(".sable.mixin.")) return mods.getModFileById("sable")!=null && mods.getModFileById("sodium")!=null;
        return mods.getModFileById("iris")!=null && (!mixin.endsWith(".VeilWrapperMixin") || mods.getModFileById("veil")!=null);
    }
    public void onLoad(String pkg) {}
    public String getRefMapperConfig() { return null; }
    public List<String> getMixins() { return null; }
    public void acceptTargets(Set<String> mine,Set<String> others) {}
    public void preApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
    public void postApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
}
