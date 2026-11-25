package willow.train.hexie.core;

import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** L0 core constants shared across all HeXie domains with zero outward domain dependencies. */
public final class HexieConstants {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeXie");
    public static final String MOD_ID = "hexie";
    public static final boolean INTERNAL_TEST = false;
    public static final Random HEXIE_RANDOM = new Random();

    private HexieConstants() {}

    public static ResourceLocation asResource(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }
}
