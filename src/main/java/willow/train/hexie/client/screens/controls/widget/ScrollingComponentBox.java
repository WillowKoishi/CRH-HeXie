package willow.train.hexie.client.screens.controls.widget;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.GuiGraphics;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import willow.train.hexie.client.screens.HexieInventory;
import willow.train.hexie.client.util.RenderablePicture;

import java.util.ArrayList;

public class ScrollingComponentBox extends AbstractWidget {

    private ArrayList<String> nameList = Lists.newArrayList();
    private ArrayList<Boolean> titleList = Lists.newArrayList();

    private RenderablePicture tooltipBg = new RenderablePicture(HexieInventory.INVENTORY_IMAGE, 1, 92, 1, 1);
    private int cursor = 0, maxWidth = 0;
    private final Font font;

    public ScrollingComponentBox(Font font, int width, int height) {
        super(0, 0, width, height, Component.empty());
        this.font = font;
    }


    public void addComponent(String value) {
        this.addComponent(value, false);
    }

    public void addComponent(Component component) {
        this.addComponent(component, false);
    }

    public void addTitle(Component component) {
        this.addComponent(component, true);
    }

    public void addTitle(String value) {
        this.addComponent(value, true);
    }

    public void addComponent(Component component, boolean isTitle) {
        nameList.add(component.getString());
        titleList.add(isTitle);
        maxWidth = Math.max(maxWidth, font.width(isTitle ? "-> " : "--> " + component.getString()));
    }

    public void addComponent(String value, boolean isTitle) {
        nameList.add(value);
        titleList.add(isTitle);
        maxWidth = Math.max(maxWidth, font.width(isTitle ? "-> " : "--> " + value));
    }

    public void clear() {
        nameList.clear();
        titleList.clear();
        maxWidth = 0;
        cursor = 0;
    }

    public void setCursor(int cursor) {
        this.cursor = Math.max(0, Math.min(cursor, nameList.size() - 1));
        if(isTitle(cursor)) pushCursor(true);
    }

    public void setCursorWithoutTitle(int cursor) {
        if(cursor >= nameList.size() || cursor < 0) return;
        int titleCounter = 0;
        for(int i = 0; i < nameList.size(); i++) {
            titleCounter += titleList.get(i) ? 1 : 0;
            if(i - titleCounter == cursor){
                this.cursor = i;
                break;
            }
        }
    }

    public int getCursorWithoutTitle() {
        int title = 0;
        for(int i = 0; i < cursor; i++) {
            title += titleList.get(i) ? 1 : 0;
        }
        return cursor - title;
    }

    public int getCursor() {
        return cursor;
    }

    public boolean isTitle(Component component) {
        return isTitle(component.getString());
    }

    public boolean isTitle(String value) {
        if(!nameList.contains(value)) return false;
        return titleList.get(nameList.indexOf(value));
    }

    public boolean isTitle(int index) {
        if(index < 0 || index > titleList.size()) return false;
        return titleList.get(index);
    }

    @Override
    public void renderWidget(GuiGraphics pose, int pMouseX, int pMouseY, float partial) {
        String name = nameList.get(cursor);
        ACCTagBox.renderTextCentered(pose, font.substrByWidth(Component.literal(name), width).getString(), getX() + width/2, getY() + height/2);
        if(isMouseOver(pMouseX, pMouseY)) {
            renderTooltip(pose, pMouseX, pMouseY, partial);
        }
    }

    public void renderTooltip(GuiGraphics pose, int mouseX, int mouseY, float partial) {
        tooltipBg.render(getX(), getY() + height, maxWidth + 2, nameList.size() * font.lineHeight);
        for(int counter = 0; counter < nameList.size(); counter++) {
            String name = nameList.get(counter);
            if(counter == cursor) {
                ACCTagBox.renderText(pose, Component.literal((isTitle(counter) ? "-> " : "--> ") + name).withStyle(ChatFormatting.GOLD), getX() + 1, getY() + height + 1 + counter * font.lineHeight);
            } else {
                ACCTagBox.renderText(pose, Component.literal((isTitle(counter) ? "-> " : "--> ") + name).withStyle(isTitle(counter) ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY), getX() + 1, getY() + height + 1 + counter * font.lineHeight);
            }
        }
    }

    public int pushCursor(boolean positiveDirection) {
        if(cursor >= nameList.size() - 1 && positiveDirection) cursor = 0;
        if(cursor < 1 && !positiveDirection) cursor = nameList.size() - 1;
        else cursor += positiveDirection ? 1 : -1;
        if(isTitle(cursor)) pushCursor(positiveDirection);
        return cursor;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        if(!isMouseOver(pMouseX, pMouseY)) return false;
        pushCursor(pDelta < 0);
        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        if(level != null && player != null) {
            level.playLocalSound(player.getOnPos(), AllSoundEvents.SCROLL_VALUE.getMainEvent(), SoundSource.BLOCKS,
                    .3f, player.level().random.nextFloat() * 0.1F + 0.9F, false);
        }
        return true;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {}
}
