package willow.train.hexie.client.carriage.flow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllItems;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.PrefabSchematic;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Stack-driven item models, shared by the preview slot and ordinary inventory/held-item rendering. */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CarriageFlowSchematicIcons {
    private CarriageFlowSchematicIcons() {}

    public static void register(IEventBus bus) {
        bus.addListener(CarriageFlowSchematicIcons::spriteSources);
        bus.addListener(CarriageFlowSchematicIcons::models);
    }

    private static void spriteSources(RegisterSpriteSourceTypesEvent event) {
        event.register(Main.asResource("carriage_icons"), Source.TYPE);
    }

    private static void models(ModelEvent.ModifyBakingResult event) {
        var id = ModelResourceLocation.inventory(AllItems.SCHEMATIC.getId());
        BakedModel original = event.getModels().get(id);
        if (original != null) event.getModels().put(id, new IconModel(original, event.getTextureGetter()));
    }

    /** Unlike vanilla's directory source, these PNGs live alongside carriage.json, outside textures/. */
    private record Source(String directory) implements SpriteSource {
        private static final MapCodec<Source> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("directory").forGetter(Source::directory)).apply(instance, Source::new));
        private static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

        @Override public void run(ResourceManager resources, Output output) {
            resources.listResources(directory, id -> id.getPath().endsWith(".png")).forEach(output::add);
        }

        @Override public SpriteSourceType type() { return TYPE; }
    }

    private static final class IconModel extends BakedModelWrapper<BakedModel> {
        // A fresh wrapper is baked on resource reload, so no old sprites or models survive a pack change.
        private final Map<ResourceLocation, BakedModel> icons = new HashMap<>();
        private final ItemOverrides overrides;

        private IconModel(BakedModel original, Function<Material, TextureAtlasSprite> textures) {
            super(original);
            overrides = new ItemOverrides() {
                // Sprites belong to the atlas; copy the base item's transforms into the generated icon model.
                @SuppressWarnings({"resource", "deprecation"})
                @Nullable
                @Override public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                                    @Nullable LivingEntity entity, int seed) {
                    ResourceLocation id = PrefabSchematic.icon(stack);
                    if (id != null) {
                        BakedModel icon = icons.computeIfAbsent(id, key -> {
                            TextureAtlasSprite sprite = textures.apply(new Material(InventoryMenu.BLOCK_ATLAS, key));
                            return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())
                                    ? originalModel : bakeIcon(sprite, originalModel.getTransforms());
                        });
                        if (icon != originalModel) return icon;
                    }
                    return originalModel.getOverrides().resolve(originalModel, stack, level, entity, seed);
                }
            };
        }

        @Override public ItemOverrides getOverrides() { return overrides; }
    }

    private static BakedModel bakeIcon(TextureAtlasSprite sprite, ItemTransforms transforms) {
        var builder = IModelBuilder.of(false, false, false, transforms, ItemOverrides.EMPTY, sprite, RenderTypeGroup.EMPTY);
        var elements = UnbakedGeometryHelper.createUnbakedItemElements(0, sprite);
        UnbakedGeometryHelper.bakeElements(builder, elements, material -> sprite, BlockModelRotation.X0_Y0);
        return builder.build();
    }
}
