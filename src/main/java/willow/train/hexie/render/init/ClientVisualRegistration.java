package willow.train.hexie.render.init;

import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1EndFaceBlockOperations;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerBlockView;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.render.carriage.SnapshotCarriageVisual;
import willow.train.hexie.render.coupler.BellowsGeometry;
import willow.train.hexie.render.coupler.CRH1EndFaceRenderer;
import willow.train.hexie.render.coupler.CRH1HeadCouplerRenderer;
import willow.train.hexie.render.coupler.CRH2EndFaceRenderer;
import willow.train.hexie.render.coupler.CoveredCouplerRenderer;
import willow.train.hexie.render.coupler.JanneyCouplerRenderer;
import willow.train.hexie.render.coupler.PartialCouplerRenderer;
import willow.train.hexie.render.coupler.SnapshotCouplerRenderer;
import willow.train.hexie.render.ss7d.SS7DHeadCoupler;

// split from willow/train/hexie/init/client/HeXieClientInit.java, lines 129-193
final class ClientVisualRegistration {
    public static void registerEntityVisuals(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("simurail"))
            event.enqueueWork(HexieBridges.Client.simurailClient().physicalHeadClient()::registerVisuals);
        // Coupler models
        event.enqueueWork(() -> {
            var blocks = HexieBridges.worldRegistry().blockInit();
            SnapshotCouplerRenderer.register(
                    blocks.ss7dHead(),
                    SS7DHeadCoupler.coupler());
            SnapshotCouplerRenderer.register(
                    blocks.generalJanneyCoupler(),
                    new JanneyCouplerRenderer(
                            PartialModelInit.JANNEY_BASE, PartialModelInit.JANNEY_BODY, PartialModelInit.JANNEY_KNUCKLE,
                            PartialModelInit.JANNEY_BODY_PIN, PartialModelInit.JANNEY_KNUCKLE_PIN,
                            ((CouplerBlockView) blocks.generalJanneyCoupler()).geometry()));
            SnapshotCouplerRenderer.register(
                    blocks.crh2aAutoCoupler(),
                    new CoveredCouplerRenderer(
                            PartialModelInit.CRH2_AUTO_COUPLER_BASE, PartialModelInit.CRH2_AUTO_COUPLER_ARM,
                            PartialModelInit.CRH2_AUTO_COUPLER_COVER_LEFT, PartialModelInit.CRH2_AUTO_COUPLER_COVER_RIGHT,
                            ((CouplerBlockView) blocks.crh2aAutoCoupler()).geometry()));
            var endCoupler = blocks.crh2aEndFace();
            SnapshotCouplerRenderer.register(
                    blocks.crh1aHead(),
                    new CRH1HeadCouplerRenderer());
            var scharfenberg = blocks.scharfenbergCoupler();
            SnapshotCouplerRenderer.register(scharfenberg,
                    new PartialCouplerRenderer(PartialModelInit.SCHARFENBERG_COUPLER_ARM,
                            ((CouplerBlockView) scharfenberg).geometry().armOrigin(), null, java.util.List.of(), 0));
            var pivot = couplerPivot((CouplerBlockView) endCoupler);
            double back = CarriageBlockBridge.CRH2EndFaceBlockOperations.BELLOWS_BASE_Z;
            double front = CarriageBlockBridge.CRH2EndFaceBlockOperations.BELLOWS_FRONT_Z;
            double maximum = CarriageBlockBridge.CRH2EndFaceBlockOperations.MAX_BELLOWS_THICKNESS;
            var bellows = java.util.stream.Stream.of(PartialModelInit.CRH2_END_BELLOWS_LEFT,
                    PartialModelInit.CRH2_END_BELLOWS_RIGHT, PartialModelInit.CRH2_END_BELLOWS_TOP,
                    PartialModelInit.CRH2_END_BELLOWS_BOTTOM)
                    .map(model -> new PartialCouplerRenderer.BellowsPart(
                            model, back, front, maximum)).toList();
            SnapshotCouplerRenderer.register(
                    blocks.crh2aEndFace(),
                    new CRH2EndFaceRenderer(new PartialCouplerRenderer(PartialModelInit.CRH2_END_COUPLER,
                            new net.minecraft.world.phys.Vec3(pivot.x() + .5, pivot.y() + .5, pivot.z() + .5),
                            null, bellows, .5, state -> HexieBridges.carriageBlock().crh2EndFaceBlock().bounds(
                                    state.getValue(HorizontalDirectionalBlock.FACING)),
                            new BellowsGeometry.ContactSpan(14 / 16d, 51 / 16d))));
            var crh1End = blocks.crh1aEndFace();
            var crh1Pivot = couplerPivot((CouplerBlockView) crh1End);
            var crh1Bellows = java.util.stream.Stream.of(PartialModelInit.CRH1_END_BELLOWS_LEFT,
                    PartialModelInit.CRH1_END_BELLOWS_RIGHT, PartialModelInit.CRH1_END_BELLOWS_TOP,
                    PartialModelInit.CRH1_END_BELLOWS_BOTTOM)
                    .map(model -> new PartialCouplerRenderer.BellowsPart(model,
                            CRH1EndFaceBlockOperations.BELLOWS_BASE_Z,
                            CRH1EndFaceBlockOperations.BELLOWS_FRONT_Z,
                            CRH1EndFaceBlockOperations.MAX_BELLOWS_THICKNESS)).toList();
            SnapshotCouplerRenderer.register(crh1End,
                    new CRH1EndFaceRenderer(new PartialCouplerRenderer(PartialModelInit.CRH1_END_COUPLER,
                            new net.minecraft.world.phys.Vec3(crh1Pivot.x() + .5, crh1Pivot.y() + .5, crh1Pivot.z() + .5),
                            null, crh1Bellows, .5, state -> HexieBridges.worldBlock().crh1EndFaceGeometry().bounds(
                                    state.getValue(HorizontalDirectionalBlock.FACING)),
                            new BellowsGeometry.ContactSpan(14 / 16d, 51 / 16d))));
        });
        if (ModList.get().isLoaded("create")) CreateOnlyVisuals.register(event);
    }

    private static final class CreateOnlyVisuals {
        private static void register(FMLClientSetupEvent event) {
            event.enqueueWork(() -> SimpleEntityVisualizer.builder(ClientRendererRegistration.CreateOnlyRenderers.assembledCarriage())
                    .factory(SnapshotCarriageVisual::new)
                    // The base renderer still draws the non-instanced parts of a contraption.
                    .skipVanillaRender(entity -> false)
                    .apply());
        }
    }

    private static Point3 couplerPivot(CouplerBlockView coupler) {
        return coupler.axis().atHeight(coupler.complete().y());
    }
}
