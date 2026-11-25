package willow.train.hexie.core.api.render;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Model locations in the order used by the persisted door block codecs. */
public record DoorModelLocations(ResourceLocation first, ResourceLocation second) {
    public static final Codec<DoorModelLocations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("first").forGetter(DoorModelLocations::first),
            ResourceLocation.CODEC.fieldOf("second").forGetter(DoorModelLocations::second)
    ).apply(instance, DoorModelLocations::new));
}
