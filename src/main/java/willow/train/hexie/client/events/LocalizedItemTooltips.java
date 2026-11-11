package willow.train.hexie.client.events;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import willow.train.hexie.Main;

/** Shows localized item descriptions directly on hover, using Create's wrapping and palette. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class LocalizedItemTooltips {
    private LocalizedItemTooltips() {}

    @SubscribeEvent
    public static void addTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.isEmpty()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Main.MOD_ID)) return;

        String key = ItemDescription.getTooltipTranslationKey(stack.getItem());
        if (!I18n.exists(key + ".summary") && !I18n.exists(key + ".condition1")
                && !I18n.exists(key + ".control1")) return;

        if (I18n.exists(key + ".summary")) {
            var lines = new ArrayList<Component>();
            lines.addAll(TooltipHelper.cutStringTextComponent(I18n.get(key + ".summary"), Palette.STANDARD_CREATE));
            appendSections(lines, key, "condition", "behaviour");
            appendSections(lines, key, "control", "action");
            event.getToolTip().addAll(1, lines);
            return;
        }

        var builder = new ItemDescription.Builder(Palette.STANDARD_CREATE);
        ItemDescription.fillBuilder(builder, key);
        event.getToolTip().addAll(1, builder.build().getCurrentLines());
    }

    private static void appendSections(List<Component> lines, String key, String heading, String text) {
        var palette = Palette.STANDARD_CREATE;
        for (int i = 1; i < 100; i++) {
            String headingKey = key + "." + heading + i;
            String textKey = key + "." + text + i;
            if (!I18n.exists(headingKey) || !I18n.exists(textKey)) break;
            lines.add(Component.translatable(headingKey).withStyle(ChatFormatting.GRAY));
            lines.addAll(TooltipHelper.cutStringTextComponent(I18n.get(textKey),
                    palette.primary(), palette.highlight(), 1));
        }
    }
}
