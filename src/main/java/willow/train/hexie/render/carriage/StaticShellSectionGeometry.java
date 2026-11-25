package willow.train.hexie.render.carriage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.render.sticker.BlockModelCutter;

import static willow.train.hexie.render.carriage.StaticShellLighting.lightAt;
import static willow.train.hexie.render.carriage.StaticShellSections.section;

// split from willow/train/hexie/client/render/StaticShellSections.java, lines 37, 235-279
final class StaticShellSectionGeometry {
    private record Draw(BlockPos sectionOffset, BakedQuad quad, RenderType renderType, int[] light) {}
    private record CutOwnerBatch(BlockPos ownerRoot,
                                 List<BlockModelCutter.StaticShellQuadInput> sectionQuads,
                                 List<BlockModelCutter.StaticShellQuadInput> fullOwnerQuads,
                                 Set<BlockPos> ownerCells,
                                 List<StickerEntry> windows) {}

    static void addGeometry(StaticShellSections sections, AddSectionGeometryEvent event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        sections.select(level);
        var origin = event.getSectionOrigin();
        var destination = section(origin);
        var entries = sections.destinations.get(destination);
        sections.dirty.remove(destination);
        // Ordinary terrain sections do no train lookups or lighting work.
        if (entries == null) return;
        var draws = new ArrayList<Draw>();
        List<CutOwnerBatch> cutOwners = null;
        var lights = new HashMap<BlockPos, Integer>();
        for (var entry : entries.entrySet()) {
            // World and plot effects own the instanced path. Section geometry is only
            // the fallback for owners without a Flywheel effect.
            BlockPos ownerRoot = entry.getKey();
            if (sections.shells.containsKey(ownerRoot)) continue;

            StaticShellMesh fullMesh = sections.meshFor(level.getBlockState(ownerRoot));
            Set<BlockPos> ownerCells = fullMesh != null
                    ? sections.cellsForOwner(ownerRoot, fullMesh)
                    : collectCells(ownerRoot, entry.getValue());
            List<StickerEntry> windows = BlockModelCutter.INSTANCE.windowsForOwnerCells(ownerCells);

            if (!windows.isEmpty()) {
                var secQuads = new ArrayList<BlockModelCutter.StaticShellQuadInput>(entry.getValue().size());
                for (var face : entry.getValue()) {
                    var cell = ownerRoot.offset(face.cell());
                    int[] light = sampleFaceLight(level, lights, cell, face.quad());
                    secQuads.add(new BlockModelCutter.StaticShellQuadInput(face.cell(), face.quad(), face.renderType(), light));
                }
                List<BlockModelCutter.StaticShellQuadInput> fullQuads;
                if (fullMesh != null && fullMesh.faces.size() > entry.getValue().size()) {
                    fullQuads = new ArrayList<>(fullMesh.faces.size());
                    for (var face : fullMesh.faces) {
                        fullQuads.add(new BlockModelCutter.StaticShellQuadInput(face.cell(), face.quad(), face.renderType(), null));
                    }
                } else {
                    fullQuads = secQuads;
                }
                if (cutOwners == null) cutOwners = new ArrayList<>();
                cutOwners.add(new CutOwnerBatch(ownerRoot.immutable(), List.copyOf(secQuads), List.copyOf(fullQuads),
                        Set.copyOf(ownerCells), windows));
                continue;
            }

            for (var face : entry.getValue()) {
                var cell = ownerRoot.offset(face.cell());
                int[] light = sampleFaceLight(level, lights, cell, face.quad());
                draws.add(new Draw(cell.subtract(origin), face.quad(), face.renderType(), light));
            }
        }
        if (draws.isEmpty() && cutOwners == null) return;
        var snapshot = List.copyOf(draws);
        var cutSnapshot = cutOwners == null ? List.<CutOwnerBatch>of() : List.copyOf(cutOwners);
        event.addRenderer(context -> {
            var pose = context.getPoseStack();
            float[] brightness = {1, 1, 1, 1};
            for (var draw : snapshot) {
                var buffer = context.getOrCreateChunkBuffer(draw.renderType());
                pose.pushPose();
                pose.translate(draw.sectionOffset.getX(), draw.sectionOffset.getY(), draw.sectionOffset.getZ());
                // Supply the actual vertex lightmap directly: no owner-block lighting pass afterwards.
                buffer.putBulkData(pose.last(), draw.quad, brightness, 1, 1, 1, 1,
                        draw.light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, true);
                pose.popPose();
            }
            for (var batch : cutSnapshot) {
                BlockModelCutter.INSTANCE.tesselateStaticShellOwner(
                        batch.ownerRoot, origin, batch.sectionQuads, batch.fullOwnerQuads,
                        batch.ownerCells, batch.windows, context.getRegion(), pose,
                        context::getOrCreateChunkBuffer);
            }
        });
    }

    private static int[] sampleFaceLight(ClientLevel level, Map<BlockPos, Integer> lights, BlockPos cell, BakedQuad quad) {
        int[] light = new int[4];
        int[] vertices = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            int p = i * 8;
            light[i] = lightAt(level, lights,
                    cell.getX() + Float.intBitsToFloat(vertices[p]),
                    cell.getY() + Float.intBitsToFloat(vertices[p + 1]),
                    cell.getZ() + Float.intBitsToFloat(vertices[p + 2]));
        }
        return light;
    }

    private static Set<BlockPos> collectCells(BlockPos ownerRoot, List<StaticShellMesh.Face> faces) {
        var cells = new HashSet<BlockPos>();
        cells.add(ownerRoot);
        for (var f : faces) cells.add(ownerRoot.offset(f.cell()));
        return cells;
    }
}
