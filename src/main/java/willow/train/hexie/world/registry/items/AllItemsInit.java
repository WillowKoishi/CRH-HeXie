package willow.train.hexie.world.registry.items;

import com.google.common.base.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

public class AllItemsInit {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeXie");

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, HexieConstants.MOD_ID);

    public static final DeferredHolder<Item, Item> HARMONY_WRENCH = ITEMS.register("harmony_wrench",
            () -> HexieBridges.itemFactory().createFloorSwitcherItem(new Item.Properties().stacksTo(1)
                    // Player base attributes are 1 damage and 4 attack speed.
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 7.0,
                                            AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.8,
                                            AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())));

    public static final DeferredHolder<Item, Item> CRH2A_RESCUE_COUPLER =
            ITEMS.register("crh2a_rescue_coupler",
                    () -> HexieBridges.itemFactory().createRescueCouplerItem(new Item.Properties()));

    public static void registerAllItems(IEventBus bus) {
        fireAllInit();
        ITEMS.addAlias(HexieConstants.asResource("floor_switcher"), HARMONY_WRENCH.getId());
        ITEMS.register(bus);
    }

    private static void fireAllInit() {
        new DietInit();
        new EditablePanelItemInit();
        new LogoItemInit();
        new ResourceItemInit();
        new TicketSystemItemInit();
    }

    static <T extends Item> DeferredHolder<Item, T> register(final String name, final Supplier<T> item) {
        return AllItemsInit.ITEMS.register(name, item);
    }
}
