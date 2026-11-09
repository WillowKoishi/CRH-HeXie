package willow.train.hexie.init;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.Main;
import willow.train.hexie.compat.CreativeTabGroup;

import java.util.HashSet;
import java.util.Set;

public final class CreativeTabsInit {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Main.MOD_ID);
    public static void register(IEventBus bus) {
        register("hexie_main", Main.HEXIE_MAIN, new String[] {"harmony_wrench", "general_janney_coupler", "crh2a_rescue_coupler", "ground_station_sign", "seat_rz", "seat_yz", "yz_seat22", "ceilin_shelf", "yz_table", "ticket_vendor", "turns_a_block", "turns_b_block", "train_panel_sign_block", "smooth_quartz_panel_half", "smooth_quartz_panel_4", "smooth_quartz_panel_2", "smooth_quartz_angle_22_up", "clay_slab", "clay_stairs", "green_fence", "green_fence_45", "megaphone", "boiling_water_place", "original_25z_floor_carpet", "weathering_resistant_steel_block_0", "exposed_weathering_steel_block", "yellowing_weathering_steel_block", "rusted_weathering_steel_block", "stably_rusted_weathering_steel_block", "fully_rusted_weathering_steel_block", "waxed_weathering_resistant_steel_block_0", "waxed_exposed_weathering_steel_block", "waxed_yellowing_weathering_steel_block", "waxed_rusted_weathering_steel_block", "waxed_stably_rusted_weathering_steel_block", "waxed_fully_rusted_weathering_steel_block", "brush", "colored_brush", "green_brush", "laquered_board_item", "sticker_item", "weathering_resistant_steel_ingot", "weathering_resistant_steel_nugget", "weathering_resistant_steel_board", "ticket", "red_ticket", "card"});
        register("hexie_normalspeedpassagecarriagetab", Main.HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB,
                willow.train.hexie.item.CarriagePartCatalog.creativeNames());
        register("hexie_emu", Main.HEXIE_EMU, new String[] {
                "crh2a_head", "crh2a_physical_head", "crh2a_carport_mid_antenna", "crh2a_carport_side", "crh2a_panel_nowindow",
                "crh2a_panel_nowindow_thick", "crh2a_panel_small_window", "crh2a_panel_wide_window",
                "crh2a_skirt_forbogey", "crh2a_skirt_normal", "crh2a_floor", "crh2a_endface", "crh2a_auto_coupler", "crh2a_rescue_coupler",
                "crh2a_passenger_door", "crh2a_passenger_door_wide", "crh2a_cab_door", "crh_logo", "panel_text",
                "test_driving_console", "crh2a_driving_console", "crh2a_driver_seat",
                "crh2a_nonstandard_driving_console", "crh2a_cab_seat",
                "crh2a_electronic_signage", "crh2a_carriage_number_display"
        });
        register("hexie_locos", Main.HEXIE_LOCOS, new String[] {"pantograph", "carriage_type_sign", "df11g_front", "df11g_cowcatcher", "df11g_carport_1", "df11g_carport_2", "df11g_carport_general", "df11g_carport_louver", "df11g_carport_louver_2", "df11g_carport_louver_3", "df11g_carport_hexie", "df11g_panel_hexie", "df11g_panel_general", "df11g_panel_mid_general", "df11g_panel_shades", "df11g_panel_mid_window", "df11g_panel_mid_shades", "df11g_panel_cr", "df11g_panel_mid_front_2", "df11g_panel_mid_front", "df11g_end_face", "df11g_cab_door", "df11g_equip_room_door", "hxd3d_panel_red", "hxd3d_panel_hexie", "hxd3d_carport_general", "hxd3d_slab", "hxd3d_cab_door", "cr200j_front"});
        register("hexie_diet", Main.HEXIE_DIET, new String[] {"train_diet_1"});
        TABS.register(bus);
    }
    private static void register(String id, CreativeTabGroup group, String[] items) {
        group.bind(TABS.register(id, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + Main.MOD_ID + "." + group.translationKey()))
                .icon(group::makeIcon)
                .displayItems((parameters, output) -> {
                    Set<ResourceLocation> added = new HashSet<>();
                    for (String name : items) {
                        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, name);
                        if (BuiltInRegistries.ITEM.containsKey(key) && added.add(key))
                            output.accept(BuiltInRegistries.ITEM.get(key));
                    }
                    for (var entry : Main.registrate().getAll(Registries.ITEM)) {
                        ResourceLocation key = entry.getId();
                        if (com.simibubi.create.foundation.data.CreateRegistrate.isInCreativeTab(
                                        entry, group.holder())
                                && BuiltInRegistries.ITEM.containsKey(key)
                                && added.add(key))
                            output.accept(BuiltInRegistries.ITEM.get(key));
                    }
                }).build()));
    }
}
