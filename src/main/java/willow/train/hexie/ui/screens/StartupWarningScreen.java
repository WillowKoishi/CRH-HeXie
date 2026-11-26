package willow.train.hexie.ui.screens;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.ui.hud.HudFonts;

/** Full-screen first-launch warning for the current game session. */
public final class StartupWarningScreen extends Screen {
    private static final float TEXT_SCALE = .75f;
    private static final String TITLE = "CRH和谐模组v1.0开发稳定性提示";
    private static final List<String> WARNINGS = List.of(
            "此版本为CRH和谐模组v1.0系列版本早期开发测试版本，由于项目结构完全重构，以及很多新系统、新功能的加入，我们当前不能保证模组的稳定性，我们建议暂时不要使用此版本于生产环境。",
            "This is an early development and testing release in the CRH Hexie Mod v1.0 series. The project structure has been completely reworked, and many new systems and features have been added. We cannot guarantee the mod's stability at this time. We recommend that you do not use this release in a production environment.",
            "Cette version est une version préliminaire de développement et de test de la série CRH Hexie Mod v1.0. La structure du projet a été entièrement remaniée et de nombreux nouveaux systèmes et fonctionnalités ont été ajoutés. Nous ne pouvons pas garantir la stabilité du mod pour le moment. Nous vous recommandons de ne pas utiliser cette version en production.",
            "Esta versión es una versión preliminar de desarrollo y pruebas de la serie CRH Hexie Mod v1.0. La estructura del proyecto se ha renovado por completo y se han añadido muchos sistemas y funciones nuevos. Por ahora no podemos garantizar la estabilidad del mod. Recomendamos no utilizar esta versión en un entorno de producción.",
            "Diese Version ist eine frühe Entwicklungs- und Testversion der CRH Hexie Mod v1.0-Reihe. Die Projektstruktur wurde vollständig überarbeitet und viele neue Systeme und Funktionen wurden hinzugefügt. Wir können die Stabilität des Mods derzeit nicht garantieren. Wir empfehlen, diese Version vorerst nicht in einer Produktionsumgebung zu verwenden.",
            "このバージョンはCRH和諧Mod v1.0シリーズの初期開発テスト版です。プロジェクト構造を全面的に再構築し、多くの新しいシステムや機能を追加したため、現時点ではModの安定性を保証できません。このバージョンを本番環境で使用しないことをお勧めします。"
    );
    private static boolean shown;

    private final TitleScreen titleScreen;

    private StartupWarningScreen(TitleScreen titleScreen) {
        super(Component.literal(TITLE));
        this.titleScreen = titleScreen;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StartupWarningScreen::afterClientTick);
    }

    private static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (shown || !(minecraft.screen instanceof TitleScreen titleScreen)) return;
        shown = true;
        minecraft.setScreen(new StartupWarningScreen(titleScreen));
    }

    @Override
    protected void init() {
        ConfirmButton button = new ConfirmButton();
        button.setX((width - button.getWidth()) / 2);
        button.setY(height - 32);
        addRenderableWidget(button);
    }

    private final class ConfirmButton extends AbstractButton {
        private ConfirmButton() {
            super(0, 0, 250, 20,
                    Component.literal("OK / Aceptar / Bestätigen / 确认 / Confirmer / 了解"));
        }

        @Override
        public void onPress() {
            Minecraft.getInstance().setScreen(titleScreen);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = isHoveredOrFocused() ? 0xFF202020 : 0xFF000000;
            graphics.fill(getX(), getY(), getRight(), getBottom(), background);
            HudFonts.Text label = HudFonts.layout(font, getMessage().getString());
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
            label.draw(graphics, -label.width() / 2f, -font.lineHeight / 2f, 0xFFFFFFFF);
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int maxWidth = Math.max(120, (int) ((width - 64) / TEXT_SCALE));
        List<List<String>> paragraphs = new ArrayList<>();
        int lineCount = 0;
        for (String warning : WARNINGS) {
            List<String> lines = wrap(warning, maxWidth);
            paragraphs.add(lines);
            lineCount += lines.size();
        }

        int lineHeight = 10;
        int paragraphGap = 6;
        int titleGap = 15;
        int textHeight = font.lineHeight + titleGap + lineCount * lineHeight
                + (paragraphs.size() - 1) * paragraphGap;
        int textBottom = height - 32;
        int y = (textBottom - textHeight) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, height / 2f, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.pose().translate(-width / 2f, -height / 2f, 0);

        HudFonts.Text title = HudFonts.layout(font, TITLE);
        title.draw(graphics, (width - title.width()) / 2f, y, 0xFFFFFFFF);
        y += font.lineHeight + titleGap;

        for (int paragraphIndex = 0; paragraphIndex < paragraphs.size(); paragraphIndex++) {
            for (String line : paragraphs.get(paragraphIndex)) {
                HudFonts.layout(font, line).draw(graphics, (width - maxWidth) / 2f, y, 0xFFFFFFFF);
                y += lineHeight;
            }
            if (paragraphIndex < paragraphs.size() - 1) y += paragraphGap;
        }
        graphics.pose().popPose();
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int nextOffset = offset + Character.charCount(codePoint);
            line.appendCodePoint(codePoint);
            if (HudFonts.layout(font, line.toString()).width() > maxWidth && line.length() > Character.charCount(codePoint)) {
                line.setLength(line.length() - Character.charCount(codePoint));
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
                if (!Character.isWhitespace(codePoint)) line.appendCodePoint(codePoint);
            }
            offset = nextOffset;
        }
        if (!line.isEmpty()) lines.add(line.toString().stripTrailing());
        return lines;
    }

    @Override
    public void onClose() {
        // The warning must be acknowledged with its confirmation button.
    }
}
