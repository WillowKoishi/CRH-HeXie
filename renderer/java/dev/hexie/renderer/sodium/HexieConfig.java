package dev.hexie.renderer.sodium;

import dev.hexie.renderer.mc.*;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import static dev.hexie.renderer.mc.ClientRenderState.*;

public final class HexieConfig implements ConfigEntryPoint {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("hexie",path); }
    public void registerConfigLate(ConfigBuilder builder) {
        var group=builder.createOptionGroup();
        group.addOption(builder.createEnumOption(id("preset"),RenderSettings.Preset.class)
                .setName(Component.literal("Preset")).setDefaultValue(RenderSettings.Preset.MEDIUM)
                .setBinding(p->engine().settings(RenderSettings.of(p)),()->engine().settings().preset())
                .setElementNameProvider(p->Component.literal(p.name())).setTooltip(p->Component.literal(stats()))
                .setStorageHandler(ClientRenderState::save));
        group.addOption(builder.createBooleanOption(id("quality"))
                .setName(Component.literal("Full quality")).setDefaultValue(false)
                .setBinding(q->{var s=engine().settings(); engine().settings(new RenderSettings(s.preset(),s.values(),q));},
                        ()->engine().settings().fullQuality())
                .setTooltip(q->Component.literal(stats())).setStorageHandler(ClientRenderState::save));
        for (var field:RenderSettings.Field.values()) {
            String name=field.name().toLowerCase(Locale.ROOT);
            int max=switch(field) {
                case POINT_LIGHTS -> 1;
                case CSM_RES, OWN_MIN_RES, OWN_MAX_RES, FOREIGN_RES -> Math.max(maxTextureSize,engine().settings().get(field));
                default -> Integer.MAX_VALUE;
            };
            group.addOption(builder.createIntegerOption(id(name)).setName(Component.literal(name.replace('_',' ')))
                    .setRange(0,max,1).setDefaultValue(field.defaultValue(RenderSettings.Preset.MEDIUM))
                    .setBinding(v->engine().settings(engine().settings().with(field,v)),()->engine().settings().get(field))
                    .setValueFormatter(v->Component.literal(v+" / "+engine().effective(field)))
                    .setTooltip(v->Component.literal("Configured / effective | "+stats())).setStorageHandler(ClientRenderState::save));
        }
        builder.registerOwnModOptions().addPage(builder.createOptionPage().setName(Component.literal("HeXie Renderer")).addOptionGroup(group));
    }
}
