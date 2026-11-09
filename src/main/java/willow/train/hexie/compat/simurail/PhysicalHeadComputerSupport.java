package willow.train.hexie.compat.simurail;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Isolated so worlds without ComputerCraft never resolve its API classes. */
final class PhysicalHeadComputerSupport {
    private PhysicalHeadComputerSupport() {}
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), SimurailHeadRegistration.PHYSICAL_HEAD_BOGEY_ENTITY.get(),
                (entity, side) -> ((PhysicalHeadBogeyBlockEntity) entity).computerBehaviour().getPeripheralCapability());
    }
}
