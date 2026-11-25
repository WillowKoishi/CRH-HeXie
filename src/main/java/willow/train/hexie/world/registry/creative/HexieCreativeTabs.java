package willow.train.hexie.world.registry.creative;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.world.registry.items.LogoItemInit;

public final class HexieCreativeTabs {
    private HexieCreativeTabs() {}

    public static final MainTab HEXIE_MAIN =
            new MainTab(HexieConstants.MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return super.makeIcon();
                }
            };
    public static final EMUTab HEXIE_EMU = new EMUTab();
    public static final NormalSpeedPassageCarriageTab HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB =
            new NormalSpeedPassageCarriageTab(HexieConstants.MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return super.makeIcon();
                }
            };
    public static final LocosTab HEXIE_LOCOS =
            new LocosTab(HexieConstants.MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return new ItemStack(LogoItemInit.LOCO_LOGOS.get());
                }
            };
    public static final DietTab HEXIE_DIET =
            new DietTab(HexieConstants.MOD_ID) {
                @Override
                public @NotNull ItemStack makeIcon() {
                    return super.makeIcon();
                }
            };
}
