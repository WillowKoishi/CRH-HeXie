package willow.train.hexie.compat.create.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import willow.train.hexie.core.api.interaction.HexieClipboardCloneable;

public final class CreateClipboardCompat {
    private CreateClipboardCompat() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CreateClipboardCompat::copy);
        NeoForge.EVENT_BUS.addListener(CreateClipboardCompat::paste);
    }

    private static HexieClipboardCloneable target(PlayerInteractEvent event) {
        var player = event.getEntity();
        if (player.isSpectator() || player.isShiftKeyDown() || !AllBlocks.CLIPBOARD.isIn(event.getItemStack())) return null;
        return event.getLevel().getBlockEntity(event.getPos()) instanceof HexieClipboardCloneable target ? target : null;
    }

    private static void copy(PlayerInteractEvent.RightClickBlock event) {
        var target = target(event);
        if (target == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        var value = new CompoundTag();
        if (!target.writeToClipboard(event.getLevel().registryAccess(), value, event.getFace())
                || event.getLevel().isClientSide()) return;
        var values = new CompoundTag();
        values.put(target.getClipboardKey(), value);
        var stack = event.getItemStack();
        var content = stack.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
        stack.set(AllDataComponents.CLIPBOARD_CONTENT, content.setType(ClipboardType.WRITTEN).setCopiedValues(values));
        feedback(event, "clipboard.copied_from");
    }

    private static void paste(PlayerInteractEvent.LeftClickBlock event) {
        var target = target(event);
        if (target == null) return;
        var content = event.getItemStack().getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
        var values = content.copiedValues().orElse(null);
        if (values == null) return;
        event.setCanceled(true);

        var level = event.getLevel();
        if (target.readFromClipboard(level.registryAccess(), values.getCompound(target.getClipboardKey()),
                event.getEntity(), event.getFace(), level.isClientSide()) && !level.isClientSide()) {
            feedback(event, "clipboard.pasted_to");
        }
    }

    private static void feedback(PlayerInteractEvent event, String key) {
        var name = event.getLevel().getBlockState(event.getPos()).getBlock().getName().withStyle(ChatFormatting.WHITE);
        event.getEntity().displayClientMessage(CreateLang.translate(key, name).style(ChatFormatting.GREEN).component(), true);
    }
}
