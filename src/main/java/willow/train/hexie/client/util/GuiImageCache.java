package willow.train.hexie.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.Main;
import willow.train.hexie.init.bogeys.BogeyStylesInit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GuiImageCache {
    private static boolean loaded;
    private static final List<ImageData> PENDING_IMAGES = new ArrayList<>();
    private static final Map<ResourceLocation, ImageData> GUI_IMAGES = new HashMap<>();
    public static final Map<String, ImageData> BOGEY_PREVIEWS = new HashMap<>();

    private GuiImageCache() {}

    private static void loadBogeyPreviews(BogeyStyle style) {
        String styleName = style.id.toDebugFileName();
        for (BogeySizes.BogeySize size : style.validSizes()) {
            String sizeName = size.id().toDebugFileName();
            // Reversed bogeys share the corresponding forward preview.
            if (sizeName.endsWith("_backward"))
                continue;

            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID,
                    "textures/gui/bogey_style/" + styleName + "/" + sizeName + ".png");
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty())
                continue;

            try (InputStream stream = resource.get().open()) {
                BOGEY_PREVIEWS.put(styleName + "." + sizeName,
                        new ImageData(location, stream, true));
            } catch (IOException exception) {
                Main.LOGGER.warn("Could not load bogey preview {}", location, exception);
            }
        }
    }

    private static ImageData register(ResourceLocation location, @Nullable Integer index) {
        // A registered path names one image, so only the first index is valid.
        if (index != null && index != 0)
            return null;

        try (InputStream stream = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(location).open()) {
            return new ImageData(location, stream, true);
        } catch (IOException exception) {
            Main.LOGGER.warn("Could not load GUI image {}", location, exception);
            return null;
        }
    }

    public static ImageData register(String path, @Nullable Integer index) {
        if (index != null && index != 0)
            return null;
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, path);
        ImageData cached = GUI_IMAGES.get(location);
        if (cached != null)
            return cached;
        if (!loaded) {
            ImageData pending = new ImageData(location);
            PENDING_IMAGES.add(pending);
            GUI_IMAGES.put(location, pending);
            return pending;
        }
        ImageData image = register(location, index);
        if (image != null)
            GUI_IMAGES.put(location, image);
        return image;
    }

    public static void loadPendingImages() {
        if (loaded)
            return;

        loaded = true;
        loadBogeyPreviews(BogeyStylesInit.HXSTANDARDBOGEY);
        loadBogeyPreviews(BogeyStylesInit.DF11GBOGEY);
        for (ImageData pending : PENDING_IMAGES) {
            ImageData image = register(pending.location, 0);
            if (image == null)
                continue;
            try {
                pending.setImage(image.image);
                pending.bytes = image.bytes;
            } catch (IOException exception) {
                Main.LOGGER.warn("Could not decode GUI image {}", pending.location, exception);
            }
        }
        PENDING_IMAGES.clear();
    }

    public static class ImageData {

        public int width, height;

        public final ResourceLocation location;
        public BufferedImage image;
        public byte[] bytes;


        public ImageData(ResourceLocation location, BufferedImage image, int width, int height, byte[] bytes) {
            this.width = width;
            this.height = height;
            this.location = location;
            this.image = image;
            this.bytes = bytes;
        }

        public ImageData(ResourceLocation location) {
            this.location = location;
        }

        public void setImage(BufferedImage image) throws IOException {
            if (image == null) throw new IOException("Unsupported image: " + location);
            this.image = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
        }

        public ImageData(FriendlyByteBuf buf) throws IOException {
            this.location = buf.readResourceLocation();
            int byteLength = buf.readInt();
            this.bytes = buf.readByteArray(byteLength);
            setImage(ImageIO.read(new ByteArrayInputStream(bytes)));
        }

        public ImageData(ResourceLocation location, InputStream imageStream, boolean fromJar) throws IOException {
            this.location = location;
            this.bytes = imageStream.readAllBytes();
            setImage(ImageIO.read(new ByteArrayInputStream(bytes)));
            if (!fromJar) {
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(bytes));
                Minecraft.getInstance().getTextureManager().register(
                        location, new DynamicTexture(nativeImage));
            }
        }

        public int getColor(int x, int y) {
            return image.getRGB(x, y);
        }

        public void toByteBuf(FriendlyByteBuf buf) {
            buf.writeResourceLocation(location);
            buf.writeInt(bytes.length);
            buf.writeByteArray(bytes);
        }

        public double aspectRatio() {
            return (double) width / (double) height;
        }
    }
}
