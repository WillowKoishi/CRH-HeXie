package willow.train.hexie.compat.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.Main;

/** Portable Create schematics: the compressed template travels with the ordinary schematic item. */
public final class PrefabSchematic {
    public static final int MAX_COMPRESSED_BYTES = 512 * 1024;
    private static final long MAX_NBT_BYTES = 32L * 1024 * 1024;
    private static final String DATA_KEY = "hexie:prefab_schematic";
    private static final AtomicBoolean WARNED_INVALID_DATA = new AtomicBoolean();

    private PrefabSchematic() {}

    /** Called once while loading a carriage resource; no world or schematic-directory files are needed. */
    public static ItemStack create(ResourceLocation resource, byte[] compressed) throws IOException {
        CompoundTag template = read(compressed);
        ListTag size = template.getList("size", Tag.TAG_INT);
        if (size.size() != 3 || size.getInt(0) <= 0 || size.getInt(1) <= 0 || size.getInt(2) <= 0)
            throw new IOException("Prefab schematic has no valid structure size: " + resource);

        String identity = identity(resource, compressed);
        String name = resource.getPath().substring(resource.getPath().lastIndexOf('/') + 1);
        if (name.endsWith(".nbt"))
            name = name.substring(0, name.length() - 4);

        ItemStack blueprint = AllItems.SCHEMATIC.asStack();
        blueprint.set(AllDataComponents.SCHEMATIC_DEPLOYED, false);
        blueprint.set(AllDataComponents.SCHEMATIC_OWNER, resource.getNamespace() + "_prefab_" + identity);
        // Create's active renderer compares only FILE, so same-named resource variants need distinct names.
        blueprint.set(AllDataComponents.SCHEMATIC_FILE, name + "_" + identity.substring(0, 12) + ".nbt");
        blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
        blueprint.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        blueprint.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        blueprint.set(AllDataComponents.SCHEMATIC_BOUNDS,
                new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)));
        CompoundTag data = new CompoundTag();
        data.putByteArray(DATA_KEY, compressed.clone());
        blueprint.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return blueprint;
    }

    /** Null delegates ordinary schematics to Create; malformed embedded data must never fall back to disk. */
    @Nullable
    public static StructureTemplate load(Level level, ItemStack blueprint) {
        CustomData customData = blueprint.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return null;
        CompoundTag data = customData.copyTag();
        if (!data.contains(DATA_KEY))
            return null;

        StructureTemplate template = new StructureTemplate();
        try {
            if (!data.contains(DATA_KEY, Tag.TAG_BYTE_ARRAY))
                throw new IOException("Embedded schematic is not a byte array");
            CompoundTag root = read(data.getByteArray(DATA_KEY));
            template.load(level.holderLookup(Registries.BLOCK), root);
            return template;
        } catch (IOException | RuntimeException exception) {
            if (WARNED_INVALID_DATA.compareAndSet(false, true))
                Main.LOGGER.warn("Could not read embedded prefab schematic {}; further invalid-data warnings are suppressed",
                        blueprint.get(AllDataComponents.SCHEMATIC_FILE), exception);
            return new StructureTemplate();
        }
    }

    private static CompoundTag read(byte[] compressed) throws IOException {
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES)
            throw new IOException("Prefab schematic compressed size must be between 1 and " + MAX_COMPRESSED_BYTES + " bytes");
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressed)) {
            return NbtIo.readCompressed(input, NbtAccounter.create(MAX_NBT_BYTES));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid prefab schematic NBT", exception);
        }
    }

    private static String identity(ResourceLocation resource, byte[] compressed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(resource.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(compressed));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
