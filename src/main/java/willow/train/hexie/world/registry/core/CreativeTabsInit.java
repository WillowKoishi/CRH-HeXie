package willow.train.hexie.world.registry.core;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.util.general.CreativeTabGroup;
import willow.train.hexie.world.registry.creative.HexieCreativeTabs;

public final class CreativeTabsInit {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HexieConstants.MOD_ID);
    public static void register(IEventBus bus) {
        register("hexie_main", HexieCreativeTabs.HEXIE_MAIN, new String[] {"harmony_wrench", "general_janney_coupler", "crh2a_rescue_coupler", "ground_station_sign", "seat_rz", "seat_yz", "yz_seat22", "ceilin_shelf", "yz_table", "ticket_vendor", "turns_a_block", "turns_b_block", "train_panel_sign_block", "smooth_quartz_panel_half", "smooth_quartz_panel_4", "smooth_quartz_panel_2", "smooth_quartz_angle_22_up", "clay_slab", "clay_stairs", "green_fence", "green_fence_45", "megaphone", "boiling_water_place", "original_25z_floor_carpet", "weathering_resistant_steel_block_0", "exposed_weathering_steel_block", "yellowing_weathering_steel_block", "rusted_weathering_steel_block", "stably_rusted_weathering_steel_block", "fully_rusted_weathering_steel_block", "waxed_weathering_resistant_steel_block_0", "waxed_exposed_weathering_steel_block", "waxed_yellowing_weathering_steel_block", "waxed_rusted_weathering_steel_block", "waxed_stably_rusted_weathering_steel_block", "waxed_fully_rusted_weathering_steel_block", "brush", "colored_brush", "green_brush", "laquered_board_item", "sticker_item", "custom_sticker", "weathering_resistant_steel_ingot", "weathering_resistant_steel_nugget", "weathering_resistant_steel_board", "ticket", "red_ticket", "card"});
        register("hexie_emu", HexieCreativeTabs.HEXIE_EMU, new String[] {
                "crh1a_head", "crh2a_head", "crh2a_physical_head", "crh2a_carport_mid_antenna", "crh1a_carport_mid", "crh1a_carport_side", "crh2a_carport_side", "crh2a_panel_nowindow",
                "crh2a_panel_nowindow_thick", "crh2a_panel_small_window", "crh2a_panel_wide_window", "crh1a_panel_wide_window",
                "crh2a_skirt_forbogey", "crh2a_skirt_normal", "crh1a_skirt", "crh2a_floor", "crh1a_floor", "crh2a_endface", "crh1a_endface", "crh2a_auto_coupler", "scharfenberg_coupler", "crh2a_rescue_coupler",
                "crh1a_psg_door", "crh2a_passenger_door", "crh2a_passenger_door_wide", "crh2a_cab_door", "crh_logo", "panel_text",
                "test_driving_console", "crh2a_driving_console", "crh2a_driver_seat",
                "crh2a_nonstandard_driving_console", "crh1a_driving_console", "crh2a_cab_seat",
                "crh2a_electronic_signage", "crh2a_carriage_number_display", "crh1a_panel_nowindow", "crh1a_panel_small_window"
        }, HexieCreativeTabs.HEXIE_MAIN.holder().getId());
        register("hexie_normalspeedpassagecarriagetab", HexieCreativeTabs.HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB,
                HexieBridges.toolOps().carriagePartCatalog().creativeNames(), HexieCreativeTabs.HEXIE_EMU.holder().getId());
        register("hexie_locos", HexieCreativeTabs.HEXIE_LOCOS, new String[] {"pantograph", "carriage_type_sign", "df11g_front", "df11g_front2", "df11g_cowcatcher", "df11g_carport_1", "df11g_carport_2", "df11g_carport_general", "df11g_carport_louver", "df11g_carport_louver_2", "df11g_carport_louver_3", "df11g_carport_hexie", "df11g_panel_hexie", "df11g_panel_general", "df11g_panel_mid_general", "df11g_panel_shades", "df11g_panel_mid_window", "df11g_panel_mid_shades", "df11g_panel_cr", "df11g_panel_mid_front_2", "df11g_panel_mid_front", "df11g_end_face", "df11g_cab_door", "df11g_equip_room_door", "hxd3d_front", "hxd3d_panel_red", "hxd3d_panel_hexie", "hxd3d_carport_general", "hxd3d_slab", "hxd3d_cab_door", "cr200j_front", "ss7d_head", "ss7d_floor", "ss7d_panel_bottom", "ss7d_panel_top", "ss7d_carport_side", "ss7d_carport_mid", "ss7d_door"},
                HexieCreativeTabs.HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB.holder().getId());
        register("hexie_diet", HexieCreativeTabs.HEXIE_DIET, new String[] {"train_diet_1"}, HexieCreativeTabs.HEXIE_LOCOS.holder().getId());
        TABS.register(bus);
    }
    private static void register(String id, CreativeTabGroup group, String[] items, ResourceLocation... tabsBefore) {
        group.bind(TABS.register(id, () -> CreativeModeTab.builder()
                .withTabsBefore(tabsBefore)
                .title(Component.translatable("itemGroup." + HexieConstants.MOD_ID + "." + group.translationKey()))
                .icon(group::makeIcon)
                .displayItems((parameters, output) -> {
                    Set<ResourceLocation> added = new HashSet<>();
                    for (String name : items) {
                        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, name);
                        if (BuiltInRegistries.ITEM.containsKey(key) && added.add(key))
                            output.accept(BuiltInRegistries.ITEM.get(key));
                    }
                    if (ModList.get().isLoaded("create")) HexieBridges.createTrain().appendCreativeTabItems(group, added, output);
                }).build()));
    }
}
