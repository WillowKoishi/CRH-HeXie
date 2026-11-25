package willow.train.hexie.compat.create.contraption;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorMemberBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1FloorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2EndFaceBlockView;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerBlockView;
import willow.train.hexie.bridge.world.PartBlockBridge.PartMemberBlockView;
import willow.train.hexie.bridge.world.PartBlockBridge.VariantPartBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.CustomRenderedDoorBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge.OBJRenderableEndFaceBlockView;

public final class CreateInteractionBehaviourInit {
    private CreateInteractionBehaviourInit() {}

    public static void register() {
        var blocks = HexieBridges.worldRegistry().blockInit();
        MovementBehaviour.REGISTRY.register(
                blocks.ss7dHead(),
                new SS7DHeadMovement());
        MovingInteractionBehaviour.REGISTRY.register(
                blocks.ss7dSideCarport(),
                new SS7DSideCarportInteraction());
        MovingInteractionBehaviour.REGISTRY.register(
                blocks.ss7dCenterCarport(),
                new SS7DCenterCarportInteraction());
        MovingInteractionBehaviour.REGISTRY.register(
                blocks.ss7dFloor(),
                new SS7DFloorInteraction());
        MovingInteractionBehaviour.REGISTRY.register(
                blocks.ss7dDoor(),
                new SS7DDoorInteraction());
        var passengerDisplays = HexieBridges.createCarriage().createPassengerDisplayInteraction();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                HexieBridges.signBlock().passengerDisplayPanel().supports(block)
                        || block instanceof PartMemberBlockView ? passengerDisplays : null);
        var doors = new DoorMovingInteraction();
        var crh1DoorMembers = HexieBridges.createDoor().createCRH1DoorMemberInteraction();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof CRH1DoorMemberBlockView ? crh1DoorMembers : null);
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof CustomRenderedDoorBlockView ? doors : null);
        var timetable = HexieBridges.createTrain().createConsoleTimetable().create();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                HexieBridges.createTrain().createConsoleTimetable().supports(block) ? timetable : null);
        var variants = HexieBridges.createCarriage().createPartVariantInteraction();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof VariantPartBlockView ? variants : null);
        var floorSkirts = HexieBridges.createCarriage().createFloorSkirtInteraction();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof CRH1FloorBlockView ? floorSkirts : null);
        var endFaceVariants = HexieBridges.createCarriage().createEndFaceVariantInteraction();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof CRH2EndFaceBlockView ? endFaceVariants : null);
        var synchronizeBlockEntity = new BlockEntitySynchronizeBehaviour();
        var crh2Doors = HexieBridges.createDoor().crh2DoorMovement().create();
        var crh1Doors = HexieBridges.createDoor().crh1DoorMovement().create();
        var passengerMovement = HexieBridges.createCarriage().createPassengerDisplayMovement();
        MovementBehaviour.REGISTRY.registerProvider(
                block -> {
                    if (HexieBridges.signBlock().passengerDisplayPanel().supports(block)) return passengerMovement;
                    if (block instanceof CRH2DoorBlockView) return crh2Doors;
                    if (block instanceof CRH1DoorBlockView) return crh1Doors;
                    if (block instanceof CouplerBlockView hook && hook.geometry() != null) return synchronizeBlockEntity;
                    if (block instanceof CustomRenderedDoorBlockView || block instanceof OBJRenderableEndFaceBlockView) {
                        return synchronizeBlockEntity;
                    }
                    return null;
                }
        );
    }
}
