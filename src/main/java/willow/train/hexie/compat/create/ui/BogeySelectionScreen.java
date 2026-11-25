package willow.train.hexie.compat.create.ui;

// split from willow/train/hexie/client/screens/BogeySelectionScreen.java

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import java.util.List;
import java.util.stream.IntStream;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeySelectionCatalogEntryView;
import willow.train.hexie.core.HexieConstants;

/** Simurail's selection-page layout, without its physics settings. See the bundled MIT notice. */
public final class BogeySelectionScreen extends AbstractSimiScreen implements MenuAccess<BogeySelectionMenu> {
    private static final ResourceLocation TEXTURE = HexieConstants.asResource("textures/gui/bogey_selection.png");
    private static final ResourceLocation RAILWAY_CASING = ResourceLocation.fromNamespaceAndPath("create", "railway_casing");
    private static final int WINDOW_WIDTH = 303, WINDOW_HEIGHT = 203;
    private final BogeySelectionMenu menu;
    private final List<? extends BogeySelectionCatalogEntryView> entries;
    private final List<String> categories;
    private final List<Boolean> roots;
    private List<String> visibleCategories;
    private List<Integer> visibleEntries;
    private BogeyMenuLabel selectedLabel;
    private BogeyMenuLabel axleLabel;
    private BogeyMenuLabel trackLabel;
    private int selected;
    private int root;
    private int category;
    private int page;

    public static void register(RegisterMenuScreensEvent event) {
        event.register(CreateMenuInit.BOGEY_SELECTION_MENU.get(), BogeySelectionScreen::new);
    }

    public BogeySelectionScreen(BogeySelectionMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
        entries = menu.entries();
        categories = entries.stream().map(BogeySelectionCatalogEntryView::category).distinct().toList();
        roots = categories.stream().map("addon"::equals).distinct().toList();
        selected = menu.initialSelection();
        if (selected < 0 || selected >= entries.size()) selected = entries.isEmpty() ? -1 : 0;
        if (selected >= 0) root = roots.indexOf(entries.get(selected).category().equals("addon"));
        filterCategories();
        if (selected >= 0) category = visibleCategories.indexOf(entries.get(selected).category());
        filterEntries();
        page = Math.max(0, visibleEntries.indexOf(selected)) / rowsPerPage();
    }

    @Override public BogeySelectionMenu getMenu() { return menu; }
    private static Component text(String key) { return Component.translatable("gui.hexie.bogey_selection." + key); }
    private boolean hasSubcategory() { return !roots.isEmpty() && !roots.get(root); }
    private int rowsPerPage() { return hasSubcategory() ? 5 : 6; }

    private void filterCategories() {
        visibleCategories = roots.isEmpty() ? List.of()
                : categories.stream().filter(name -> name.equals("addon") == roots.get(root)).toList();
    }
    private void filterEntries() {
        visibleEntries = visibleCategories.isEmpty() ? List.of() : IntStream.range(0, entries.size())
                .filter(i -> entries.get(i).category().equals(visibleCategories.get(category))).boxed().toList();
    }

    @Override protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();
        if (!roots.isEmpty()) {
            selection(7, 20, 120, roots.stream().map(addon -> addon ? text("addon")
                    : Component.translatable("simurail_bogey_category.hexie")).toList(), root, text("category"), value -> {
                root = value;
                category = page = 0;
                filterCategories();
                filterEntries();
                rebuildWidgets();
            });
        }
        if (hasSubcategory()) {
            selection(7, 38, 120, visibleCategories.stream().map(name ->
                    Component.translatable("simurail_bogey_category.hexie." + name)).toList(), category,
                    text("subcategory"), value -> {
                        category = value;
                        page = 0;
                        filterEntries();
                        rebuildWidgets();
                    });
        }
        for (int row = 0; row < rowsPerPage(); row++) {
            int index = page * rowsPerPage() + row;
            if (index >= visibleEntries.size()) break;
            int entryIndex = visibleEntries.get(index);
            var label = label(7, 40 + (row + (hasSubcategory() ? 1 : 0)) * 18, 120, 3);
            label.text = entries.get(entryIndex).name();
            label.withOverflowTooltip().withClickSound();
            label.withCallback(() -> { selected = entryIndex; updateSelection(); });
            addRenderableWidget(label);
        }
        int pageCount = Math.max(1, Math.ceilDiv(visibleEntries.size(), rowsPerPage()));
        selection(25, 150, 84, IntStream.rangeClosed(1, pageCount)
                .mapToObj(i -> Component.translatable("gui.hexie.bogey_selection.page_format", i)).toList(),
                page, text("page_title"), value -> { page = value; rebuildWidgets(); });

        selectedLabel = label(138, 20, 150, 3).withOverflowTooltip();
        axleLabel = label(156, 38, 42, 3);
        axleLabel.withTooltip(List.of(text("axle_spacing").copy().withColor(0x5391E1)));
        trackLabel = label(216, 38, 72, 3);
        addRenderableWidgets(selectedLabel, axleLabel, trackLabel);
        var confirm = new IconButton(guiLeft + 270, guiTop + 179, AllIcons.I_CONFIRM);
        confirm.setToolTip(Component.translatable("create.action.confirm"));
        confirm.withCallback(() -> {
            if (selected >= 0 && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected);
                onClose();
            }
        });
        confirm.active = selected >= 0;
        addRenderableWidget(confirm);
        updateSelection();
    }

    private BogeyMenuLabel label(int x, int y, int width, int margin) {
        return new BogeyMenuLabel(guiLeft + x, guiTop + y, width, 18, margin);
    }
    private void selection(int x, int y, int width, List<? extends Component> choices, int value,
                           Component title, java.util.function.Consumer<Integer> changed) {
        var label = label(x, y, width, 5);
        var input = new SelectionScrollInput(guiLeft + x, guiTop + y, width, 18);
        input.forOptions(choices);
        input.setState(value);
        input.titled(title.copy()).writingTo(label).calling(changed);
        addRenderableWidgets(input, label);
    }
    private void updateSelection() {
        if (selected < 0) return;
        var entry = entries.get(selected);
        var model = HexieBridges.bogeyRegistry().builtinBogeyModel().find(entry.style().id, entry.size().id());
        double span = model == null ? entry.block().getWheelPointSpacing() : HexieBridges.bogeyRegistry().builtinBogeyProfile().geometry(HexieBridges.bogeyRegistry().builtinBogeyModel().profile(model)).axleSpan();
        selectedLabel.text = entry.name();
        axleLabel.text = Component.literal(Long.toString(Math.round(span)));
        trackLabel.text = Component.translatable("simurail_track_type.hexie.standard");
        trackLabel.withTooltip(List.of(text("track_type").copy().withColor(0x5391E1),
                trackLabel.text.copy().withStyle(ChatFormatting.GRAY)));
    }
    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(TEXTURE, x, y, u, v, width, height, 512, 256);
    }

    @Override protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft, y = guiTop;
        blit(graphics, x, y, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        // Cover only the physics-settings icon baked into the source sheet with its blank footer.
        blit(graphics, x + 7, y + 179, 32, 179, 18, 18);
        if (hasSubcategory()) blit(graphics, x + 7, y + 38, 303, 0, 123, 18);
        for (int row = 0; row < rowsPerPage(); row++) {
            if (page * rowsPerPage() + row >= visibleEntries.size()) break;
            blit(graphics, x + 7, y + 40 + (row + (hasSubcategory() ? 1 : 0)) * 18,
                    303, 18, 120, 18);
        }
        graphics.drawString(font, title, x + (WINDOW_WIDTH - 8) / 2 - font.width(title) / 2,
                y + 4, 0x592424, false);
        var casing = GuiGameElement.of(BuiltInRegistries.ITEM.get(RAILWAY_CASING));
        casing.at(x + WINDOW_WIDTH + 6, y + WINDOW_HEIGHT - 56, -200);
        casing.scale(5);
        casing.render(graphics);
        graphics.flush();
        graphics.enableScissor(x + 139, y + 67, x + 287, y + 139);
        try {
            if (selected >= 0) BogeySelectionPreview.renderPreview(entries.get(selected), graphics, partialTick, guiLeft, guiTop, minecraft);
        }
        finally { graphics.disableScissor(); }
    }

    @Override public void onClose() {
        if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        else super.onClose();
    }
    @Override public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null
                && (!minecraft.player.isAlive() || minecraft.player.isRemoved())) onClose();
    }
}
