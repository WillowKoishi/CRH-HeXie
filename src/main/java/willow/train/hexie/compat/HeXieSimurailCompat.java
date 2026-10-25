package willow.train.hexie.compat;

import com.crystaelix.simurail.api.bogey.BogeyPropertyOverrides;
import com.crystaelix.simurail.api.bogey.BogeyType;
import com.crystaelix.simurail.api.bogey.menu.BogeyEntry;
import com.crystaelix.simurail.api.bogey.menu.BogeyEntryCategory;
import com.crystaelix.simurail.api.bogey.menu.BogeyMenuManager;
import com.crystaelix.simurail.api.track.TrackTypeEntries;
import com.crystaelix.simurail.api.track.TrackTypeEntry;
import com.crystaelix.simurail.content.SimurailTracks;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.network.chat.Component;
import willow.train.hexie.Main;
import willow.train.hexie.init.bogeys.BogeySizesInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;
import willow.train.hexie.init.track.TrackBlockInit;
import willow.train.hexie.init.track.TrackMaterialInit;

import java.util.List;

/** Only loaded from common setup when Simurail is installed. */
public final class HeXieSimurailCompat {
    public static final BogeyEntry CW2 = entry("cw2", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizes.SMALL);
    public static final BogeyEntry PK209P = entry("pk209p", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.PK209P);
    public static final BogeyEntry PK209P_NO_MOTOR = entry("pk209p_no_motor", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.PK209P_NO_MOTOR);
    public static final BogeyEntry PK209P_BACKWARD = entry("pk209p_backward", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.PK209P_BACKWARD);
    public static final BogeyEntry SW220K = entry("sw220k", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.SW220K);
    public static final BogeyEntry SW220K_BACKWARD = entry("sw220k_backward", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.SW220K_BACKWARD);
    public static final BogeyEntry SW160 = entry("sw160", BogeyStylesInit.HXSTANDARDBOGEY, BogeySizesInit.SW160);
    public static final BogeyEntry DF11G = entry("df11g", BogeyStylesInit.DF11GBOGEY, BogeySizes.SMALL);
    public static final BogeyEntry DF11G_BACKWARD = entry("df11g_backward", BogeyStylesInit.DF11GBOGEY, BogeySizesInit.BACKWARD);
    public static final BogeyEntry QJ = entry("qj", BogeyStylesInit.DF11GBOGEY, BogeySizesInit.QJ);
    public static final BogeyEntry QJ_BACKWARD = entry("qj_backward", BogeyStylesInit.DF11GBOGEY, BogeySizesInit.QJ_BACKWARD);

    public static final BogeyEntryCategory HEXIE = new BogeyEntryCategory(
            Component.translatable("simurail_bogey_category.hexie"),
            List.of(CW2, PK209P, PK209P_NO_MOTOR, PK209P_BACKWARD, SW220K, SW220K_BACKWARD,
                    SW160, DF11G, DF11G_BACKWARD, QJ, QJ_BACKWARD));

    private HeXieSimurailCompat() {}

    public static void register() {
        registerTrackType();
        BogeyMenuManager.addBogeyCategory(HEXIE);
        BogeyType.setDefault(TrackMaterialInit.HEXIE_STANDARD, false, CW2.type());
        configureParameters();
    }

    private static BogeyEntry entry(String id, BogeyStyle style, BogeySizes.BogeySize size) {
        return new BogeyEntry(Main.asResource(id), new BogeyType(style, size));
    }

    private static void registerTrackType() {
        // 轨道参数暂沿用 Simurail 标准轨；可在这里分别修改轨距、高度和三个物理系数。
        TrackTypeEntry standard = SimurailTracks.STANDARD;
        TrackTypeEntries.addEntry(new TrackTypeEntry(
                TrackMaterialInit.HEXIE_STANDARD,
                Component.translatable("simurail_track_type.hexie.standard"),
                Component.translatable("simurail_track_type.hexie.standard.short"),
                standard.allowInverted(),
                standard.width(),
                standard.height(),
                standard.lateralMaxSpeedFactor(),
                standard.verticalMaxSpeedFactor(),
                standard.adhesionFactor(),
                TrackBlockInit.STANDARD_TRACK::getDefaultState));
    }

    /**
     * 转向架参数填写入口。不设置覆盖时，Simurail 沿用原方块属性及其默认值。
     * 使用 {@link BogeyPropertyOverrides} 为上方任意条目的 type() 设置参数；长度单位为方块。
     */
    private static void configureParameters() {
        // 按需取消注释，并把参数名替换为自己的数值；CW2 可替换为上方任意转向架常量。
        // BogeyPropertyOverrides.setAxleSpacingOverride(CW2.type(), axleSpacing);
        // BogeyPropertyOverrides.setAxleCountOverride(CW2.type(), axleCount);
        // BogeyPropertyOverrides.setAxlePositionsOverride(CW2.type(), axlePositions);
        // BogeyPropertyOverrides.setWheelRadiusOverride(CW2.type(), wheelRadius);
        // BogeyPropertyOverrides.setConnectorAnchorOffsetOverride(CW2.type(), new org.joml.Vector3f(x, y, z));
        // BogeyPropertyOverrides.setTrackWidthOverride(CW2.type(), trackWidth);
        // BogeyPropertyOverrides.setTrackHeightOverride(CW2.type(), trackHeight);
        // BogeyPropertyOverrides.setGroundDrivableOverride(CW2.type(), groundDrivable);
    }
}
