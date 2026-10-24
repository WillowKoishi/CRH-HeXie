package willow.train.hexie.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

import willow.train.hexie.Main;
import willow.train.hexie.menu.TicketVendorMenu;
import willow.train.hexie.client.screens.widget.*;
import willow.train.hexie.util.station.LineMap;
import willow.train.hexie.util.station.LineTag;
import willow.train.hexie.util.station.StationInfo;
import willow.train.hexie.util.station.StationRenderCache;

import java.awt.*;
import java.util.ArrayList;

public class TicketVendorScreen extends AbstractContainerScreen<TicketVendorMenu> {

    public static final ResourceLocation BG_MAIN =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/main.png");
    public static final ResourceLocation BG_MASK_DOWN =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/blank.png");
    public static final ResourceLocation BG_MASK_UP =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/upper_mask.png");
    public static final ResourceLocation BG_FRAME =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/edge.png");
    public static final ResourceLocation TVM_BUTTON_UP =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/button_up.png");

    LineMapScreenWidget wiget;
    int guiLeft, guiTop, guiWidth;
    float porportion = 0.5f;

    LineMap map;

    ResetButton resetButton;

    ArrayList<LineChosenButton> lineButtons = new ArrayList<>();

    public TicketVendorScreen(
            TicketVendorMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    public void init() {

        guiLeft = (int) (0.1 * Minecraft.getInstance().screen.width);
        guiTop = (int) (0.1 * Minecraft.getInstance().screen.height);
        guiWidth = (int) (0.8 * Minecraft.getInstance().screen.width);

        map = new LineMap("name");

        line1();
        line2();

        lineButtons.clear();

        wiget =
                new LineMapScreenWidget(
                        map,
                        guiLeft + (int) (0.0933 * guiWidth) - 10,
                        guiTop + (int) (0.2067 * guiWidth * porportion) - 10,
                        (int) (0.4233 * guiWidth) + 20,
                        (int) (0.6733 * guiWidth * porportion) + 20);

        int counter = 0;
        for (LineTag tags : map.lines()) {
            lineButtons.add(
                    new LineChosenButton(
                            tags,
                            (int) (0.1067 * guiWidth) + guiLeft,
                            (int) (0.2667 * guiWidth * porportion)
                                    + guiTop
                                    + counter * (int) (0.105 * guiWidth * 1 / 3),
                            (int) (0.1 * guiWidth),
                            (int) (0.1 * guiWidth * 1 / 3),
                            b -> {
                                wiget.chosenLine = tags;
                            }));
            counter++;
        }

        resetButton =
                new ResetButton(
                        guiLeft + (int) (0.1067 * guiWidth),
                        guiTop + (int) (0.81 * guiWidth * porportion),
                        (int) (0.03 * guiWidth),
                        (int) (0.03 * guiWidth * 10 / 9),
                        b -> {
                            wiget.chosenLine = null;
                            wiget.reset();
                        });
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        RenderSystem.setShaderTexture(0, BG_MASK_DOWN);
        RenderSystem.enableBlend();
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
        if (wiget != null) {
            wiget.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
            for (LineChosenButton button : lineButtons) {
                button.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
            }
            resetButton.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }
        RenderSystem.setShaderTexture(0, BG_MASK_UP);
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
        RenderSystem.setShaderTexture(0, BG_MAIN);
        drawTexture(
                guiLeft,
                guiTop,
                guiWidth,
                (int) (porportion * guiWidth * 16 / 15),
                1.0f,
                1.0f,
                0xffffff);
        RenderSystem.setShaderTexture(0, BG_FRAME);
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
        RenderSystem.setShaderTexture(0, TVM_BUTTON_UP);
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    private void drawTexture(
            int x, int y, int width, int height, float uWidth, float vHeight, int color) {
        int[] colors = getRGBAColor(color);
        int r = colors[0], g = colors[1], b = colors[2];
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(
                (float) r / 255.0f, (float) g / 255.0f, (float) b / 255.0f, 1.0f);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(x, y, 0.0F).setUv(0, 0);
        buffer.addVertex(x, y + height, 0.0F).setUv(0, vHeight);
        buffer.addVertex(x + width, y + height, 0.0F).setUv(uWidth, vHeight);
        buffer.addVertex(x + width, y, 0.0F).setUv(uWidth, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    public int[] getRGBAColor(int octColor) {
        int[] result = new int[4];
        Color color = new Color(octColor);
        result[0] = color.getRed();
        result[1] = color.getGreen();
        result[2] = color.getBlue();
        result[3] = color.getAlpha();
        return result;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        if (wiget != null && inMap(pMouseX, pMouseY)) wiget.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
        return true;
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (wiget != null) {
            if (!clickedResetButton(pMouseX, pMouseY, pButton)
                    && !clickedLineButton(pMouseX, pMouseY, pButton)
                    && inMap(pMouseX, pMouseY)) wiget.mouseClicked(pMouseX, pMouseY, pButton);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (wiget != null) {
            wiget.mouseReleased(pMouseX, pMouseY, pButton);
            for (LineChosenButton b : lineButtons) {
                b.mouseReleased(pMouseX, pMouseY, pButton);
            }
            resetButton.mouseReleased(pMouseX, pMouseY, pButton);
        }
        return true;
    }

    public boolean inMap(double pMouseX, double pMouseY) {
        return pMouseX > wiget.getX()
                && pMouseX < wiget.getX() + wiget.getWidth()
                && pMouseY > wiget.getX()
                && pMouseY < wiget.getY() + wiget.getHeight();
    }

    public boolean clickedLineButton(double pMouseX, double pMouseY, int pButton) {
        for (LineChosenButton b : lineButtons) {
            if (pMouseX > b.getX()
                    && pMouseX < b.getX() + b.getWidth()
                    && pMouseY > b.getY()
                    && pMouseY < b.getY() + b.getHeight()) {
                b.mouseClicked(pMouseX, pMouseY, pButton);
                return true;
            }
        }
        return false;
    }

    public boolean clickedResetButton(double pMouseX, double pMouseY, int pButton) {
        if (pMouseX > resetButton.getX()
                && pMouseX < resetButton.getX() + resetButton.getWidth()
                && pMouseY > resetButton.getY()
                && pMouseY < resetButton.getY() + resetButton.getHeight()) {
            resetButton.mouseClicked(pMouseX, pMouseY, pButton);
            return true;
        }
        return false;
    }

    public void line1() {
        float scale = 0.5f;
        StationInfo xiLang = StationInfo.create("西朗", 1);
        StationInfo kengKou = StationInfo.create("坑口", 2);
        StationInfo huaDiWan = StationInfo.create("花地湾", 3);
        StationInfo fangCun = StationInfo.create("芳村", 4);
        StationInfo huangSha = StationInfo.create("黄沙", 5);
        StationInfo changShouLu = StationInfo.create("长寿路", 6);
        StationInfo chenJiaCi = StationInfo.create("陈家祠", 7);
        StationInfo xiMenKou = StationInfo.create("西门口", 8);
        StationInfo gongYuanQian = StationInfo.create("公园前", 9);
        StationInfo nongJiangSuo = StationInfo.create("农讲所", 10);
        StationInfo lieShiLingYuan = StationInfo.create("烈士陵园", 11);
        StationInfo dongShanKou = StationInfo.create("东山口", 12);
        StationInfo yangJi = StationInfo.create("杨箕", 13);
        StationInfo tiYuXiLu = StationInfo.create("体育西路", 14);
        StationInfo tiYuZhongXin = StationInfo.create("体育中心", 15);
        StationInfo guangZhouDongZhan = StationInfo.create("广州东站", 16);
        xiLang.setLogo(Items.DIAMOND.getDefaultInstance());
        kengKou.setLogo(Items.SAND.getDefaultInstance());
        huaDiWan.setLogo(Items.FLOWER_POT.getDefaultInstance());
        fangCun.setLogo(Items.EXPERIENCE_BOTTLE.getDefaultInstance());
        huangSha.setLogo(Items.SAND.getDefaultInstance());
        changShouLu.setLogo(Items.RED_CANDLE.getDefaultInstance());
        chenJiaCi.setLogo(Items.MOJANG_BANNER_PATTERN.getDefaultInstance());
        xiMenKou.setLogo(Items.IRON_INGOT.getDefaultInstance());
        gongYuanQian.setLogo(Items.FISHING_ROD.getDefaultInstance());
        nongJiangSuo.setLogo(Items.IRON_HOE.getDefaultInstance());
        lieShiLingYuan.setLogo(Items.NETHERITE_INGOT.getDefaultInstance());
        dongShanKou.setLogo(Items.CAKE.getDefaultInstance());
        yangJi.setLogo(Items.SHEEP_SPAWN_EGG.getDefaultInstance());
        tiYuXiLu.setLogo(Items.LAVA_BUCKET.getDefaultInstance());
        tiYuZhongXin.setLogo(Items.POTATO.getDefaultInstance());
        guangZhouDongZhan.setLogo(Items.GOLD_INGOT.getDefaultInstance());
        LineTag tag = LineTag.create(100L, "一号线", DyeColor.YELLOW.getTextColor());
        xiLang.addLine(tag);
        kengKou.addLine(tag);
        huaDiWan.addLine(tag);
        fangCun.addLine(tag);
        huangSha.addLine(tag);
        changShouLu.addLine(tag);
        chenJiaCi.addLine(tag);
        xiMenKou.addLine(tag);
        gongYuanQian.addLine(tag);
        nongJiangSuo.addLine(tag);
        lieShiLingYuan.addLine(tag);
        dongShanKou.addLine(tag);
        yangJi.addLine(tag);
        tiYuXiLu.addLine(tag);
        tiYuZhongXin.addLine(tag);
        guangZhouDongZhan.addLine(tag);
        StationRenderCache[] caches = new StationRenderCache[16];
        caches[0] = StationRenderCache.create(xiLang, 30, 220, scale);
        caches[1] = StationRenderCache.create(kengKou, 30, 200, scale);
        caches[2] = StationRenderCache.create(huaDiWan, 30, 180, scale);
        caches[3] = StationRenderCache.create(fangCun, 30, 160, scale);
        caches[4] = StationRenderCache.create(huangSha, 30, 140, scale);
        caches[5] = StationRenderCache.create(changShouLu, 30, 120, scale);
        caches[6] = StationRenderCache.create(chenJiaCi, 50, 100, scale);
        caches[7] = StationRenderCache.create(xiMenKou, 70, 100, scale);
        caches[8] = StationRenderCache.create(gongYuanQian, 90, 100, scale);
        caches[9] = StationRenderCache.create(nongJiangSuo, 110, 100, scale);
        caches[10] = StationRenderCache.create(lieShiLingYuan, 130, 100, scale);
        caches[11] = StationRenderCache.create(dongShanKou, 150, 100, scale);
        caches[12] = StationRenderCache.create(yangJi, 170, 100, scale);
        caches[13] = StationRenderCache.create(tiYuXiLu, 190, 100, scale);
        caches[14] = StationRenderCache.create(tiYuZhongXin, 210, 80, scale);
        caches[15] = StationRenderCache.create(guangZhouDongZhan, 190, 60, scale);
        caches[0].interchange = true;
        caches[4].interchange = true;
        caches[6].interchange = true;
        caches[8].interchange = true;
        caches[11].interchange = true;
        caches[12].interchange = true;
        caches[13].interchange = true;
        caches[15].interchange = true;

        map.addLine(tag);

        for (StationRenderCache cache : caches) {
            map.addStation(cache);
        }

        for (int i = 0; i < caches.length - 1; i++) {
            map.connect(caches[i], caches[i + 1], tag.lineID);
        }
    }

    public void line2() {
        float scale = 0.5f;

        StationInfo xiLang = StationInfo.create("嘉禾望岗", 201);
        StationInfo kengKou = StationInfo.create("黄边", 202);
        StationInfo huaDiWan = StationInfo.create("江夏", 203);
        StationInfo fangCun = StationInfo.create("萧岗", 204);
        StationInfo huangSha = StationInfo.create("白云文化广场", 205);
        StationInfo changShouLu = StationInfo.create("白云公园", 206);
        StationInfo chenJiaCi = StationInfo.create("飞翔公园", 207);
        StationInfo xiMenKou = StationInfo.create("三元里", 208);
        StationInfo gongYuanQian = StationInfo.create("广州火车站", 209);
        StationInfo nongJiangSuo = StationInfo.create("越秀公园", 210);
        StationInfo lieShiLingYuan = StationInfo.create("纪念堂", 211);
        StationInfo dongShanKou = StationInfo.create("海珠广场", 212);
        StationInfo yangJi = StationInfo.create("市二宫", 213);
        StationInfo tiYuXiLu = StationInfo.create("江南西", 214);
        StationInfo tiYuZhongXin = StationInfo.create("昌岗", 215);
        StationInfo guangZhouDongZhan = StationInfo.create("江泰路", 216);
        StationInfo dongXiaoNan = StationInfo.create("东晓南", 217);
        StationInfo nanZhou = StationInfo.create("南洲", 218);
        StationInfo luoXi = StationInfo.create("洛溪", 219);
        StationInfo nanPu = StationInfo.create("南浦", 220);
        StationInfo huiJiang = StationInfo.create("会江", 221);
        StationInfo shiBi = StationInfo.create("石壁", 222);
        StationInfo guangZhouNanZhan = StationInfo.create("广州南站", 223);

        xiLang.setLogo(Items.WHEAT.getDefaultInstance());
        kengKou.setLogo(Items.SMOOTH_SANDSTONE.getDefaultInstance());
        huaDiWan.setLogo(Items.QUARTZ_BLOCK.getDefaultInstance());
        fangCun.setLogo(Items.RECOVERY_COMPASS.getDefaultInstance());
        huangSha.setLogo(Items.SNOWBALL.getDefaultInstance());
        changShouLu.setLogo(Items.FIREWORK_STAR.getDefaultInstance());
        chenJiaCi.setLogo(Items.COAL_BLOCK.getDefaultInstance());
        xiMenKou.setLogo(Items.REDSTONE_ORE.getDefaultInstance());
        gongYuanQian.setLogo(Items.NETHER_STAR.getDefaultInstance());
        nongJiangSuo.setLogo(Items.BEACON.getDefaultInstance());
        lieShiLingYuan.setLogo(Items.BEE_NEST.getDefaultInstance());
        dongShanKou.setLogo(Items.REDSTONE_LAMP.getDefaultInstance());
        yangJi.setLogo(Items.SANDSTONE_WALL.getDefaultInstance());
        tiYuXiLu.setLogo(Items.SWEET_BERRIES.getDefaultInstance());
        tiYuZhongXin.setLogo(Items.CROSSBOW.getDefaultInstance());
        guangZhouDongZhan.setLogo(Items.COPPER_INGOT.getDefaultInstance());
        dongXiaoNan.setLogo(Items.STONE_AXE.getDefaultInstance());
        nanZhou.setLogo(Items.COMPASS.getDefaultInstance());
        luoXi.setLogo(Items.LANTERN.getDefaultInstance());
        nanPu.setLogo(Items.SEA_LANTERN.getDefaultInstance());
        huiJiang.setLogo(Items.SEA_PICKLE.getDefaultInstance());
        shiBi.setLogo(Items.DRIPSTONE_BLOCK.getDefaultInstance());
        guangZhouNanZhan.setLogo(Items.EMERALD.getDefaultInstance());

        LineTag tag = LineTag.create(200L, "二号线", 0x62AEFF);

        xiLang.addLine(tag);
        kengKou.addLine(tag);
        huaDiWan.addLine(tag);
        fangCun.addLine(tag);
        huangSha.addLine(tag);
        changShouLu.addLine(tag);
        chenJiaCi.addLine(tag);
        xiMenKou.addLine(tag);
        gongYuanQian.addLine(tag);
        nongJiangSuo.addLine(tag);
        lieShiLingYuan.addLine(tag);
        dongShanKou.addLine(tag);
        yangJi.addLine(tag);
        tiYuXiLu.addLine(tag);
        tiYuZhongXin.addLine(tag);
        guangZhouDongZhan.addLine(tag);
        dongXiaoNan.addLine(tag);
        nanZhou.addLine(tag);
        luoXi.addLine(tag);
        nanPu.addLine(tag);
        huiJiang.addLine(tag);
        shiBi.addLine(tag);
        guangZhouNanZhan.addLine(tag);

        StationRenderCache[] caches = new StationRenderCache[23];
        caches[0] = StationRenderCache.create(xiLang, 130, -120, scale);
        caches[1] = StationRenderCache.create(kengKou, 130, -100, scale);
        caches[2] = StationRenderCache.create(huaDiWan, 130, -80, scale);
        caches[3] = StationRenderCache.create(fangCun, 110, -60, scale);
        caches[4] = StationRenderCache.create(huangSha, 90, -40, scale);
        caches[5] = StationRenderCache.create(changShouLu, 90, -20, scale);
        caches[6] = StationRenderCache.create(chenJiaCi, 90, 0, scale);
        caches[7] = StationRenderCache.create(xiMenKou, 90, 20, scale);
        caches[8] = StationRenderCache.create(gongYuanQian, 90, 40, scale);
        caches[9] = StationRenderCache.create(nongJiangSuo, 90, 60, scale);
        caches[10] = StationRenderCache.create(lieShiLingYuan, 90, 80, scale);
        caches[11] = StationRenderCache.create(dongShanKou, 90, 120, scale);
        caches[12] = StationRenderCache.create(yangJi, 90, 140, scale);
        caches[13] = StationRenderCache.create(tiYuXiLu, 90, 160, scale);
        caches[14] = StationRenderCache.create(tiYuZhongXin, 90, 180, scale);
        caches[15] = StationRenderCache.create(guangZhouDongZhan, 110, 200, scale);
        caches[16] = StationRenderCache.create(dongXiaoNan, 130, 200, scale);
        caches[17] = StationRenderCache.create(nanZhou, 150, 200, scale);
        caches[18] = StationRenderCache.create(luoXi, 170, 200, scale);
        caches[19] = StationRenderCache.create(nanPu, 190, 200, scale);
        caches[20] = StationRenderCache.create(huiJiang, 210, 200, scale);
        caches[21] = StationRenderCache.create(shiBi, 230, 200, scale);
        caches[22] = StationRenderCache.create(guangZhouNanZhan, 250, 200, scale);

        caches[0].interchange = true;
        caches[8].interchange = true;
        caches[11].interchange = true;
        caches[14].interchange = true;
        caches[21].interchange = true;
        caches[22].interchange = true;

        map.addLine(tag);

        for (StationRenderCache cache : caches) {
            map.addStation(cache);
        }

        ((StationRenderCache) (map.stations().get(9l))).station().addLine(tag);

        for (int i = 0; i < 10; i++) {
            map.connect(caches[i], caches[i + 1], tag.lineID);
        }

        map.connect(caches[10], (StationRenderCache) map.stations().get(9l), tag.lineID);
        map.connect((StationRenderCache) map.stations().get(9l), caches[11], tag.lineID);

        for (int i = 11; i < caches.length - 1; i++) {
            map.connect(caches[i], caches[i + 1], tag.lineID);
        }
    }
}
