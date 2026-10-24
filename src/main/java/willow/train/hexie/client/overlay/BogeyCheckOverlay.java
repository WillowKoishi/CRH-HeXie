package willow.train.hexie.client.overlay;

import net.minecraft.client.gui.GuiGraphics;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import willow.train.hexie.Main;
import willow.train.hexie.init.bogeys.BogeyStylesInit;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.util.PlayerUtil;
import willow.train.hexie.client.util.RenderablePicture;

import java.util.ArrayList;
import java.util.List;

public class BogeyCheckOverlay extends BaseOverlay {
    public static final String DESCRIPTION_NAME = "bogey_check_overlay";
    private static final Component TIP_ONE = Component.translatable("container.hexie.bogey_check_overlay.tips_1");
    private static final Component TIP_TWO = Component.translatable("container.hexie.bogey_check_overlay.tips_2");
    private final BogeyStyle style;
    private final BogeySizes.BogeySize size;
    private final BlockPos position;
    private final Level level;
    private final Component nameTag;
    private final Component speedLabel;
    private final Component speedValue;
    private final List<Component> carriageLines;
    private final RenderablePicture previewPicture;
    private final boolean useDf11gFallback;
    private int animationFrame;
    private static final int MAX_ANIMATION_FRAME = 750;
    private static final int ANIMATION_STEP = 30;
    private boolean animationDirection = true;

    public static final ResourceLocation BOGEY_DISPLAY_BOARD = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/bogey_style/bogey_display_board.png");
    private static final ResourceLocation DF11G_BOGEY_PREVIEW = ResourceLocation.fromNamespaceAndPath(
            Main.MOD_ID, "textures/gui/bogey_style/hexie_df11gbogey/create_small.png");

    public BogeyCheckOverlay(AbstractBogeyBlockEntity entity) {
        this.position = entity.getBlockPos();
        this.style = entity.getStyle();
        this.level = entity.getLevel();
        this.size = ((AbstractBogeyBlock)(entity.getBlockState().getBlock())).size;

        String previewKey = style.id.toDebugFileName() + "." + size.id().toDebugFileName();
        if (previewKey.endsWith("_backward"))
            previewKey = previewKey.substring(0, previewKey.length() - 9);
        if (previewKey.endsWith("hexie") && style.equals(BogeyStylesInit.DF11GBOGEY))
            previewKey = previewKey.substring(0, previewKey.length() - 6) + "create_small";

        String baseName = "container.hexie.bogey_check_overlay." + previewKey;
        String nameKey = baseName + ".bogey.name";
        Component translatedName = Component.translatable(nameKey);
        GuiImageCache.ImageData image = GuiImageCache.BOGEY_PREVIEWS.get(previewKey);
        useDf11gFallback = style.equals(BogeyStylesInit.DF11GBOGEY)
                && (translatedName.getString().equals(nameKey) || image == null);
        if (useDf11gFallback) {
            baseName = "container.hexie.bogey_check_overlay.hexie_df11gbogey.create_small";
            nameKey = baseName + ".bogey.name";
            translatedName = Component.translatable(nameKey);
        }
        nameTag = Component.literal(translatedName.getString().equals(nameKey)
                        ? style.id.getPath() : translatedName.getString())
                .append(Component.translatable("container.hexie.bogey_check_overlay.bogey"));
        previewPicture = !useDf11gFallback && image != null ? new RenderablePicture(image) : null;

        String speedKey = baseName + ".bogey.speed";
        String speedText = Component.translatable(speedKey).getString();
        String[] speedParts = speedText.equals(speedKey) ? new String[] {"", ""} : splitLabel(speedText);
        speedLabel = speedParts[0].isEmpty() ? null : Component.literal(speedParts[0]);
        speedValue = speedParts[1].isEmpty() ? null : Component.literal(speedParts[1]);

        String cartKey = baseName + ".bogey.cart";
        String cartText = Component.translatable(cartKey).getString();
        List<Component> lines = new ArrayList<>();
        if (!cartText.equals(cartKey)) {
            String[] cartParts = splitLabel(cartText);
            if (!cartParts[0].isEmpty())
                lines.add(Component.literal(cartParts[0]));
            for (String cart : cartParts[1].split(",")) {
                if (!cart.isBlank())
                    lines.add(Component.literal(cart.trim()));
            }
        }
        carriageLines = List.copyOf(lines);
    }

    @Override
    public void render(GuiGraphics pose, int pMouseX, int pMouseY, float partial) {
        render(Minecraft.getInstance(), pose, partial);
    }  // 这个不要动

    @Override
    public void render(Minecraft mc, GuiGraphics pose, float partial) {
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();  // 屏幕宽度
        int screenHeight = mc.getWindow().getGuiScaledHeight();  // 屏幕高度
        pose.pose().pushPose();

        if(animationDirection && animationFrame < MAX_ANIMATION_FRAME) {
            animationFrame = Math.min(animationFrame + ANIMATION_STEP, MAX_ANIMATION_FRAME);
        }

        if(!animationDirection && animationFrame > 0) {
            animationFrame = Math.max(animationFrame - ANIMATION_STEP, 0);
        }

        float progress = (float) animationFrame / MAX_ANIMATION_FRAME;
        float easedProgress = progress * progress * (3 - 2 * progress);
        int x = screenWidth - 170 + Math.round((1 - easedProgress) * 170);
        int y = screenHeight - 140;
        int boxWidth = 160, boxHeight = 120;
        renderTextureLazy(BOGEY_DISPLAY_BOARD, pose, x, y, boxWidth, boxHeight);
        renderComponent(pose, font, nameTag,
                x + (boxWidth - font.width(nameTag))/2, y + 11, 0x5f4d3a);

        if (useDf11gFallback) {
            int imageWidth = 80;
            int imageHeight = Math.round(imageWidth * 340f / 628f);
            renderTextureLazy(DF11G_BOGEY_PREVIEW, pose, x + 10,
                    y + (boxHeight - imageHeight) / 2 + 5, imageWidth, imageHeight);
        } else if(previewPicture != null) {
            int imageWidth = 80;
            int imageHeight = (int) (imageWidth / previewPicture.widthHeightRatio());
            previewPicture.render(pose, x + 10, y + (boxHeight - imageHeight)/2 + 5, imageWidth, imageHeight);
        }

        int textX = x + (int)((float)boxWidth * 0.75) - 25;
        if (speedLabel != null)
            renderComponent(pose, font, speedLabel, textX, y + 35, 0x5f4d3a);
        if (speedValue != null)
            renderComponent(pose, font, speedValue,
                    textX + (speedLabel == null ? 0 : 5),
                    y + (speedLabel == null ? 35 : 45), 0x5f4d3a);

        int lineY = 65;
        for (int i = 0; i < carriageLines.size(); i++) {
            renderComponent(pose, font, carriageLines.get(i), i == 0 ? textX : textX + 5,
                    y + lineY, 0x5f4d3a);
            lineY += (font.lineHeight + 2);
        }

        pose.pose().translate(x + 20, y + boxHeight - 20, 0);
        pose.pose().scale(0.7f, 0.7f, 1);
        renderComponent(pose, font, TIP_ONE, 0, 0, 0x5f4d3a);
        pose.pose().translate(0, 10, 0);
        renderComponent(pose, font, TIP_TWO, 0, 0, 0x5f4d3a);
        pose.pose().popPose();
    }

    private static String[] splitLabel(String text) {
        int separator = text.indexOf(':');
        if (separator < 0)
            separator = text.indexOf('：');
        if (separator < 0)
            return new String[] {"", text};
        return new String[] {text.substring(0, separator + 1), text.substring(separator + 1).trim()};
    }

    /**
     * 这个方法控制什么情况下这个 Overlay 会被销毁
     * @return 这个 Overlay 能否继续存在，true 继续存在， false 将被销毁
     */
    public boolean canSurvive() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean result = true;
        if(!(minecraft.player != null && level != null && minecraft.level == level
                && PlayerUtil.isPlayerEyeOn(minecraft.player, position)
                && wearingGoggles()
                && minecraft.player.getEyePosition().distanceToSqr(
                        position.getX(), position.getY(), position.getZ()) < 100)) {

            result = false;
        } else {
            if (level == null){result = false;}  // prevent lag
            else {
                if (!(level.getBlockEntity(position) instanceof AbstractBogeyBlockEntity bogey)) result = false;
                else if (!bogey.getStyle().equals(style)
                        || !((AbstractBogeyBlock<?>) bogey.getBlockState().getBlock()).size.equals(size))
                    result = false;
            }
        }
        if(result) {
            animationDirection = true;
        }
        if(!result && animationFrame > 0) {
            animationDirection = false;
            return true;
        }
        return result;
    }

    @Override
    public String getDescriptionName(){
        return DESCRIPTION_NAME;
    }


    // TODO: 2024/2/17 For test
    private boolean wearingGoggles() {
        return true;
    }
}
