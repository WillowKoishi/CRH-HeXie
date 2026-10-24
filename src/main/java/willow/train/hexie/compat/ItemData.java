package willow.train.hexie.compat;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Accesses HeXie's item data using Minecraft's custom-data component. */
public final class ItemData {
    private ItemData() {}

    public static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean hasTag(ItemStack stack) {
        return !getTag(stack).isEmpty();
    }

    @Nullable
    public static CompoundTag getTagElement(ItemStack stack, String key) {
        CompoundTag tag = getTag(stack);
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;
    }

    /** Returns a detached copy; persist changes with setTagElement or updateTagElement. */
    public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
        CompoundTag tag = getTagElement(stack, key);
        return tag == null ? new CompoundTag() : tag;
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void updateTag(ItemStack stack, Consumer<CompoundTag> update) {
        CompoundTag tag = getTag(stack);
        update.accept(tag);
        setTag(stack, tag);
    }

    public static void setTagElement(ItemStack stack, String key, CompoundTag element) {
        updateTag(stack, tag -> tag.put(key, element.copy()));
    }

    public static void updateTagElement(ItemStack stack, String key, Consumer<CompoundTag> update) {
        CompoundTag tag = getOrCreateTagElement(stack, key);
        update.accept(tag);
        setTagElement(stack, key, tag);
    }

    public static void removeTagKey(ItemStack stack, String key) {
        updateTag(stack, tag -> tag.remove(key));
    }
}
