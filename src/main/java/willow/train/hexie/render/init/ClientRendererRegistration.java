package willow.train.hexie.render.init;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.render.blockentity.console.DrivingConsoleRenderer;
import willow.train.hexie.render.blockentity.coupler.CouplerBlockEntityRenderer;
import willow.train.hexie.render.blockentity.front.CR200jFrontRenderer;
import willow.train.hexie.render.blockentity.front.CRH1HeadRenderer;
import willow.train.hexie.render.blockentity.part.OBJRenderableEndFaceRenderer;
import willow.train.hexie.render.blockentity.sign.CarriageNoSignRenderer;
import willow.train.hexie.render.blockentity.sign.CarriageTypeSignRenderer;
import willow.train.hexie.render.blockentity.sign.GroundStationSignRenderer;
import willow.train.hexie.render.blockentity.sign.LacqueredBoardEntityRenderer;
import willow.train.hexie.render.blockentity.sign.TrainSpeedSignEntityRenderer;
import willow.train.hexie.render.blockentity.signal.AdjustableSignalRenderer;
import willow.train.hexie.render.blockentity.signal.ThroughSignalRenderer;
import willow.train.hexie.render.carriage.SnapshotCarriageRenderer;
import willow.train.hexie.render.display.CarriageMarkingRenderer;
import willow.train.hexie.render.display.HeadNumberRenderer;
import willow.train.hexie.render.display.PassengerPanelRenderer;
import willow.train.hexie.render.door.CustomRenderedDoorRenderer;
import willow.train.hexie.render.door.ElectricFenceDoorRenderer;
import willow.train.hexie.render.ss7d.SS7DHeadRenderer;

// split from willow/train/hexie/init/client/HeXieClientInit.java, lines 76-127
final class ClientRendererRegistration {
    static void registerSeatRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HexieBridges.worldRegistry().entityInit().yzSeatEntity(), NoopRenderer::new);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (ModList.get().isLoaded("simurail"))
            HexieBridges.Client.simurailClient().physicalHeadClient().registerRenderers(event);
        if (ModList.get().isLoaded("create")) CreateOnlyRenderers.register(event);
        var entities = HexieBridges.worldRegistry().blockEntitiesInit();
        event.registerBlockEntityRenderer(entities.passengerPanel(),
                PassengerPanelRenderer::new);
        event.registerBlockEntityRenderer(entities.crh2aPart(), CarriageMarkingRenderer::new);
        event.registerBlockEntityRenderer(entities.crh2aHead(), HeadNumberRenderer::new);
        event.registerBlockEntityRenderer(entities.crh1aHead(),
                CRH1HeadRenderer::new);
        event.registerBlockEntityRenderer(entities.ss7dHead(),
                SS7DHeadRenderer::new);
        event.registerBlockEntityRenderer(entities.drivingConsole(),
                DrivingConsoleRenderer::new);
        event.registerBlockEntityRenderer(entities.groundStationSign(), GroundStationSignRenderer::new);
        event.registerBlockEntityRenderer(entities.coupler(), CouplerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                entities.carriageTypeSign(), CarriageTypeSignRenderer::new);
        event.registerBlockEntityRenderer(
                entities.carriageNoSign(), CarriageNoSignRenderer::new);
        event.registerBlockEntityRenderer(
                entities.trainSpeedSign(), TrainSpeedSignEntityRenderer::new);
        event.registerBlockEntityRenderer(
                entities.laqueredBoard(), LacqueredBoardEntityRenderer::new);
        event.registerBlockEntityRenderer(
                entities.electricFenceDoor(), ElectricFenceDoorRenderer::new);
        event.registerBlockEntityRenderer(
                entities.objRenderableEndFaceBlockEntity(),
                OBJRenderableEndFaceRenderer::new);
        event.registerBlockEntityRenderer(
                entities.cr200jFrontBlockEntity(),
                CR200jFrontRenderer::new);
        event.registerBlockEntityRenderer(
                entities.customRenderedDoorEntity(),
                CustomRenderedDoorRenderer::new);
    }

    static final class CreateOnlyRenderers {
        @SuppressWarnings("unchecked")
        static EntityType<com.simibubi.create.content.trains.entity.CarriageContraptionEntity> assembledCarriage() {
            return (EntityType<com.simibubi.create.content.trains.entity.CarriageContraptionEntity>)
                    HexieBridges.worldRegistry().entityInit().assembledCarriage();
        }

        private static void register(EntityRenderersEvent.RegisterRenderers event) {
            HexieBridges.Client.createTrainClient().createAddonBogeyClient().registerRenderers(event);
            event.registerEntityRenderer(assembledCarriage(), SnapshotCarriageRenderer::new);

            var bogeys = HexieBridges.bogeyRegistry().bogeyEntityInit();
            registerBogey(event, bogeys.hxStandardBogey());
            registerBogey(event, bogeys.df11gBogey());
            registerBogey(event, bogeys.ss7dBogey());
            registerBogey(event, bogeys.crhBogey());

            var signals = HexieBridges.signalRegistry().semaphoreEntityInit();
            event.registerBlockEntityRenderer(signals.hxSemaphore(), ThroughSignalRenderer::new);
            event.registerBlockEntityRenderer(signals.adjustableSemaphore(), AdjustableSignalRenderer::new);
        }

        @SuppressWarnings("unchecked")
        private static void registerBogey(EntityRenderersEvent.RegisterRenderers event, BlockEntityType<?> type) {
            event.registerBlockEntityRenderer(
                    (BlockEntityType<com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity>) type,
                    com.simibubi.create.content.trains.bogey.BogeyBlockEntityRenderer::new);
        }
    }
}
