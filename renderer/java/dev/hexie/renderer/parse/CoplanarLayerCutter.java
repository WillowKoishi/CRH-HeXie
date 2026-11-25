package dev.hexie.renderer.parse;

import dev.hexie.renderer.Vec;
import dev.hexie.renderer.parse.CompiledMesh.Tri;
import dev.hexie.renderer.parse.CompiledMesh.Vertex;
import dev.hexie.renderer.parse.PlaneFrame.UV;
import java.util.*;

/**
 * Exact surface-aware single-layer & zero-thickness opening cutter + parametric window generator.
 *
 * Rules:
 * - Works on both flat blocks AND curved/sloped OBJ carriage shells via per-point surface depth fields wEntry(u,v)/wExit(u,v).
 * - Thick shell: cuts entry layer + exit layer, hollows interior, clips internal seams inside [wEntry, wExit],
 *   bridges cavity walls following the actual surface curve, and NEVER cuts deeper walls behind wExit.
 * - Zero-thickness shell: single cut (一刀), no cavity walls.
 * - Frame & glass are clipped strictly to actually-cut surface polygons — nothing is ever stuffed into empty air.
 */
public final class CoplanarLayerCutter {
    private static final double THICKNESS_EPS = 4e-3;
    private static final double LAYER_GAP_EPS = 2e-2;
    private static final double NORMAL_DOT_MIN = 0.25;
    private static final double CLIP_EPS = 1e-8;
    // Only cut geometry on the aimed block/surface plane (never bridge across open air to a distant wall in the same section!)
    private static final double PLANE_ENTRY_MIN_W = -0.85;
    private static final double PLANE_ENTRY_MAX_W = 1.05;
    private static final double MAX_LAYER_THICKNESS = 1.35;

    public record CutResult(
            CompiledMesh cutHost,
            CompiledMesh windowFrame,
            CompiledMesh windowGlass,
            double wEntry,
            double wExit,
            boolean zeroThickness,
            boolean modified,
            int cutEntryTriangles,
            int cutExitTriangles
    ) {}

    private CoplanarLayerCutter() {}

    public static CutResult cutAndBuildWindow(CompiledMesh hostMesh, PlaneFrame frame, ParametricWindowSpec spec) {
        return cutAndBuildWindow(hostMesh, frame, spec, ParametricWindowSpec.DEFAULT_CORNER_SEGMENTS);
    }

    public static CutResult cutAndBuildWindow(CompiledMesh hostMesh, PlaneFrame frame, ParametricWindowSpec spec,
                                              int cornerSegments) {
        Objects.requireNonNull(hostMesh);
        Objects.requireNonNull(frame);
        Objects.requireNonNull(spec);
        List<UV> outerLoop = ensureCcw(dedupUv(spec.outerLoop(cornerSegments)));
        return cut(hostMesh, frame, outerLoop, spec, cornerSegments);
    }

    public static CutResult cutOpeningOnly(CompiledMesh hostMesh, PlaneFrame frame, List<UV> convexOuterLoop) {
        return cut(hostMesh, frame, ensureCcw(dedupUv(convexOuterLoop)), null, 0);
    }

    private record ProjectedCap(UV a, UV b, UV c, double da, double db, double dc, double det) {
        boolean covers(UV p) {
            double s = Math.signum(det);
            return cross2D(a, b, p) * s >= -1e-6 && cross2D(b, c, p) * s >= -1e-6 && cross2D(c, a, p) * s >= -1e-6;
        }
        double depthAt(UV p) {
            return da + cross2D(a, p, c) / det * (db - da) + cross2D(a, b, p) / det * (dc - da);
        }
        UV centroid() {
            return new UV((a.u() + b.u() + c.u()) / 3.0, (a.v() + b.v() + c.v()) / 3.0);
        }
        double nearestVertexDepth(UV p) {
            double d0 = distSq(p, a), d1 = distSq(p, b), d2 = distSq(p, c);
            if (d0 <= d1 && d0 <= d2) return da;
            return d1 <= d2 ? db : dc;
        }
        double minVertexDistSq(UV p) {
            return Math.min(distSq(p, a), Math.min(distSq(p, b), distSq(p, c)));
        }
    }

    private record DepthField(
            List<ProjectedCap> entryCaps,
            List<ProjectedCap> exitCaps,
            double minEntry,
            double fallbackExit,
            boolean zeroThickness,
            double maxEntry,
            double maxExit
    ) {
        double entryDepthAt(UV p) {
            double best = Double.POSITIVE_INFINITY;
            for (var c : entryCaps) {
                if (c.covers(p)) {
                    double d = c.depthAt(p);
                    if (d < best) best = d;
                }
            }
            if (Double.isFinite(best)) return best;
            double bestDist = Double.POSITIVE_INFINITY;
            double bestD = minEntry;
            for (var c : entryCaps) {
                double dist = Math.min(distSq(p, c.centroid()), c.minVertexDistSq(p));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestD = c.nearestVertexDepth(p);
                }
            }
            return bestD;
        }

        double exitDepthAt(UV p, double wIn) {
            if (zeroThickness) return wIn;
            double maxAllowed = wIn + MAX_LAYER_THICKNESS;
            double best = Double.POSITIVE_INFINITY;
            for (var c : exitCaps) {
                if (c.covers(p)) {
                    double d = c.depthAt(p);
                    if (d > wIn + THICKNESS_EPS && d <= maxAllowed && d < best) best = d;
                }
            }
            if (Double.isFinite(best)) return best;
            double bestDist = Double.POSITIVE_INFINITY;
            double clampedFallback = Math.min(maxAllowed, Math.max(wIn + THICKNESS_EPS, fallbackExit));
            double bestD = clampedFallback;
            for (var c : exitCaps) {
                double cand = c.nearestVertexDepth(p);
                if (cand <= wIn + THICKNESS_EPS || cand > maxAllowed) continue;
                double dist = Math.min(distSq(p, c.centroid()), c.minVertexDistSq(p));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestD = cand;
                }
            }
            return bestD;
        }

        boolean isFirstLayerEntry(UV mid, double triDepth) {
            if (triDepth < minEntry - LAYER_GAP_EPS || triDepth > maxEntry + LAYER_GAP_EPS) return false;
            for (var ex : exitCaps) {
                if (ex.covers(mid) && ex.depthAt(mid) < triDepth - THICKNESS_EPS) return false;
            }
            return true;
        }

        boolean isFirstLayerExit(UV mid, double triDepth) {
            if (zeroThickness) return Math.abs(triDepth - entryDepthAt(mid)) <= THICKNESS_EPS;
            double wIn = entryDepthAt(mid);
            if (triDepth <= wIn + THICKNESS_EPS) return false;
            for (var ex : exitCaps) {
                if (ex.covers(mid)) {
                    double exD = ex.depthAt(mid);
                    if (exD > wIn + THICKNESS_EPS && exD < triDepth - LAYER_GAP_EPS) return false;
                }
            }
            return triDepth <= maxExit + LAYER_GAP_EPS;
        }
    }

    private record CutSegment(PolyVert p0, PolyVert p1, String materialId, int argb) {}

    private static CutResult cut(CompiledMesh hostMesh, PlaneFrame frame, List<UV> outerLoop,
                                 ParametricWindowSpec windowSpec, int cornerSegments) {
        if (hostMesh.isEmpty() || outerLoop.size() < 3) {
            return new CutResult(hostMesh, CompiledMesh.EMPTY, CompiledMesh.EMPTY, 0, 0, true, false, 0, 0);
        }

        var tris = hostMesh.triangles();
        var rawEntries = new ArrayList<ProjectedCap>();
        var rawExits = new ArrayList<ProjectedCap>();
        double minEntry = Double.POSITIVE_INFINITY;

        for (Tri t : tris) {
            Vec n = t.geometricNormal();
            double nw = n.dot(frame.w());
            if (Math.abs(nw) < NORMAL_DOT_MIN) continue;
            double da = frame.depth(t.a().pos()), db = frame.depth(t.b().pos()), dc = frame.depth(t.c().pos());
            double dMid = (da + db + dc) / 3.0;
            // Ignore distant walls in the same 16x16x16 section that do not sit on this window's surface plane!
            if (dMid < PLANE_ENTRY_MIN_W || dMid > PLANE_ENTRY_MAX_W + MAX_LAYER_THICKNESS) continue;
            UV a = frame.project(t.a().pos()), b = frame.project(t.b().pos()), c = frame.project(t.c().pos());
            double det = cross2D(a, b, c);
            if (Math.abs(det) <= 1e-11) continue;
            if (!triIntersectsConvex(a, b, c, outerLoop)) continue;
            var cap = new ProjectedCap(a, b, c, da, db, dc, det);
            if (nw < -NORMAL_DOT_MIN) {
                if (dMid <= PLANE_ENTRY_MAX_W) {
                    rawEntries.add(cap);
                    minEntry = Math.min(minEntry, Math.min(da, Math.min(db, dc)));
                }
            } else {
                rawExits.add(cap);
            }
        }

        if (rawEntries.isEmpty()) {
            return new CutResult(hostMesh, CompiledMesh.EMPTY, CompiledMesh.EMPTY, 0, 0, true, false, 0, 0);
        }

        // 1. Isolate frontmost-layer entry caps (never behind an exit cap)
        var firstEntries = new ArrayList<ProjectedCap>(rawEntries.size());
        double maxEntry = minEntry;
        for (var en : rawEntries) {
            UV mid = en.centroid();
            double enD = (en.da + en.db + en.dc) / 3.0;
            boolean behindExit = false;
            for (var ex : rawExits) {
                if (ex.covers(mid) && ex.depthAt(mid) < enD - THICKNESS_EPS) {
                    behindExit = true;
                    break;
                }
            }
            if (!behindExit) {
                firstEntries.add(en);
                maxEntry = Math.max(maxEntry, Math.max(en.da, Math.max(en.db, en.dc)));
            }
        }
        if (firstEntries.isEmpty()) firstEntries.addAll(rawEntries);

        // 2. Isolate first-layer exit caps directly behind first-layer entry caps
        var firstExits = new ArrayList<ProjectedCap>(rawExits.size());
        double minExit = Double.POSITIVE_INFINITY;
        double maxExit = Double.NEGATIVE_INFINITY;
        for (var ex : rawExits) {
            UV mid = ex.centroid();
            double exD = (ex.da + ex.db + ex.dc) / 3.0;
            double frontEnD = Double.POSITIVE_INFINITY;
            for (var en : firstEntries) {
                if (en.covers(mid)) {
                    double d = en.depthAt(mid);
                    if (d < exD - THICKNESS_EPS && d < frontEnD) frontEnD = d;
                }
            }
            if (!Double.isFinite(frontEnD) || exD - frontEnD > MAX_LAYER_THICKNESS) continue;
            boolean behindEarlierExit = false;
            for (var otherEx : rawExits) {
                if (otherEx == ex) continue;
                if (otherEx.covers(mid)) {
                    double oD = otherEx.depthAt(mid);
                    if (oD > frontEnD + THICKNESS_EPS && oD < exD - LAYER_GAP_EPS) {
                        behindEarlierExit = true;
                        break;
                    }
                }
            }
            if (!behindEarlierExit) {
                firstExits.add(ex);
                minExit = Math.min(minExit, exD);
                maxExit = Math.max(maxExit, Math.max(ex.da, Math.max(ex.db, ex.dc)));
            }
        }
        if (firstExits.isEmpty()) {
            for (var ex : rawExits) {
                double exD = (ex.da + ex.db + ex.dc) / 3.0;
                if (exD > minEntry + THICKNESS_EPS && exD <= maxEntry + MAX_LAYER_THICKNESS
                        && (!Double.isFinite(minExit) || exD < minExit + LAYER_GAP_EPS)) {
                    firstExits.add(ex);
                    minExit = Math.min(minExit, exD);
                    maxExit = Math.max(maxExit, Math.max(ex.da, Math.max(ex.db, ex.dc)));
                }
            }
        }

        boolean zeroThickness = !Double.isFinite(minExit) || (minExit - minEntry <= THICKNESS_EPS);
        double wExit = zeroThickness ? minEntry : minExit;
        if (!Double.isFinite(maxExit)) maxExit = wExit;
        if (zeroThickness) {
            firstExits.clear();
            for (var ex : rawExits) {
                double exD = (ex.da + ex.db + ex.dc) / 3.0;
                if (Math.abs(exD - minEntry) <= THICKNESS_EPS) firstExits.add(ex);
            }
        }

        DepthField field = new DepthField(firstEntries, firstExits, minEntry, wExit, zeroThickness, maxEntry, maxExit);

        // Prepare innerLoop for surface-clipped parametric frame & glass
        List<UV> innerLoop = null;
        double thick = 0.0;
        if (windowSpec != null) {
            int segs = Math.max(1, cornerSegments);
            double hw = windowSpec.width() * 0.5, hh = windowSpec.height() * 0.5;
            double rOut = Math.max(0.0, windowSpec.effectiveCornerRadius());
            thick = Math.max(0.0, windowSpec.effectiveFrameThickness());
            double ihw = Math.max(0.0, hw - thick), ihh = Math.max(0.0, hh - thick);
            double rIn = Math.max(0.0, Math.min(rOut - thick, Math.min(ihw, ihh)));
            int steps = rOut > 1e-6 ? segs : 1;
            if (ihw > 1e-4 && ihh > 1e-4) {
                innerLoop = ensureCcw(dedupUv(samplePairedRoundedRect(windowSpec.offsetU(), windowSpec.offsetV(), ihw, ihh, rIn, steps)));
            }
        }

        var outHostTris = new ArrayList<Tri>(tris.size() + 32);
        var frameTris = new ArrayList<Tri>();
        var glassTris = new ArrayList<Tri>();
        var boundaryWallSegments = new ArrayList<CutSegment>();
        int cutEntryCount = 0;
        int cutExitCount = 0;

        for (Tri t : tris) {
            Vec n = t.geometricNormal();
            double nw = n.dot(frame.w());
            double da = frame.depth(t.a().pos()), db = frame.depth(t.b().pos()), dc = frame.depth(t.c().pos());
            if (Math.abs(nw) < NORMAL_DOT_MIN) {
                if (zeroThickness) {
                    outHostTris.add(t);
                    continue;
                }
                double minW = Math.min(da, Math.min(db, dc));
                double maxW = Math.max(da, Math.max(db, dc));
                if (maxW <= minEntry - 0.005 || minW >= maxExit + 0.005) {
                    outHostTris.add(t);
                    continue;
                }
                UV midUv = frame.project(t.a().pos().plus(t.b().pos()).plus(t.c().pos()).times(1.0 / 3.0));
                double wExitLocal = field.exitDepthAt(midUv, field.entryDepthAt(midUv));
                var splitW = splitTriByMaxDepth(t, frame, wExitLocal + 0.002);
                outHostTris.addAll(splitW.deeper);
                for (Tri shallowTri : splitW.shallower) {
                    var clipped = subtractConvexFromTri(shallowTri, frame, outerLoop, false, boundaryWallSegments);
                    outHostTris.addAll(clipped.outside);
                }
                continue;
            }
            UV midUv = frame.project(t.a().pos().plus(t.b().pos()).plus(t.c().pos()).times(1.0 / 3.0));
            double d = (da + db + dc) / 3.0;
            boolean isEntry = nw < -NORMAL_DOT_MIN && field.isFirstLayerEntry(midUv, d);
            boolean isExit = nw > NORMAL_DOT_MIN && field.isFirstLayerExit(midUv, d);
            if (!isEntry && !isExit) {
                outHostTris.add(t);
                continue;
            }
            var clipped = subtractConvexFromTri(t, frame, outerLoop, isEntry && !zeroThickness, boundaryWallSegments);
            outHostTris.addAll(clipped.outside);
            if (clipped.cut) {
                if (isEntry) {
                    cutEntryCount++;
                    if (windowSpec != null && clipped.inside.size() >= 3) {
                        emitSurfaceClippedWindow(clipped.inside, n, frame, field, windowSpec, thick, innerLoop, frameTris, glassTris);
                    }
                }
                if (isExit) cutExitCount++;
            }
        }

        boolean modified = cutEntryCount > 0 || cutExitCount > 0;
        if (!modified) {
            return new CutResult(hostMesh, CompiledMesh.EMPTY, CompiledMesh.EMPTY, minEntry, wExit, zeroThickness, false, 0, 0);
        }

        // Bridge hole cavity walls strictly along actually-cut outerLoop edges (facing INTO the opening!)
        int nextFaceId = tris.size() + 1;
        if (!zeroThickness && !boundaryWallSegments.isEmpty()) {
            for (var seg : boundaryWallSegments) {
                Vec p0Front = seg.p0.v().pos();
                Vec p1Front = seg.p1.v().pos();
                double w0f = frame.depth(p0Front), w1f = frame.depth(p1Front);
                double w0b = field.exitDepthAt(seg.p0.uv(), w0f), w1b = field.exitDepthAt(seg.p1.uv(), w1f);
                Vec p0Back = p0Front.plus(frame.w().times(w0b - w0f));
                Vec p1Back = p1Front.plus(frame.w().times(w1b - w1f));
                Vec edge = p1Front.minus(p0Front);
                Vec wallNormal = edge.cross(frame.w());
                double len = Math.sqrt(wallNormal.dot(wallNormal));
                if (len <= 1e-12) continue;
                wallNormal = wallNormal.times(1.0 / len);
                double segLen = Math.sqrt(edge.dot(edge));
                double depthSpan = Math.max(THICKNESS_EPS, ((w0b - w0f) + (w1b - w1f)) * 0.5);
                Vertex v0f = new Vertex(p0Front, wallNormal, 0, 0);
                Vertex v1f = new Vertex(p1Front, wallNormal, segLen, 0);
                Vertex v1b = new Vertex(p1Back, wallNormal, segLen, depthSpan);
                Vertex v0b = new Vertex(p0Back, wallNormal, 0, depthSpan);
                outHostTris.add(new Tri(nextFaceId++, v0f, v1f, v1b, seg.materialId, seg.argb, false));
                outHostTris.add(new Tri(nextFaceId++, v0f, v1b, v0b, seg.materialId, seg.argb, false));
            }
        }

        return new CutResult(CompiledMesh.of(outHostTris), CompiledMesh.of(frameTris), CompiledMesh.of(glassTris),
                minEntry, wExit, zeroThickness, true, cutEntryCount, cutExitCount);
    }

    private static void emitSurfaceClippedWindow(List<PolyVert> cutPoly, Vec surfNormal, PlaneFrame frame,
                                                 DepthField field, ParametricWindowSpec spec, double thick,
                                                 List<UV> innerLoop, List<Tri> frameOut, List<Tri> glassOut) {
        Vec frontN = surfNormal;
        Vec backN = surfNormal.times(-1.0);
        int fArgb = spec.frameArgb();
        int gArgb = spec.glassArgb();

        if (innerLoop == null) {
            if (thick > 1e-5) emitFramePolyFrontBack(cutPoly, frame, field, frontN, backN, fArgb, frameOut);
            return;
        }

        List<PolyVert> glassPoly = cutPoly;
        int m = innerLoop.size();
        for (int k = 0; k < m; k++) {
            if (glassPoly.size() < 3) break;
            UV e0 = innerLoop.get(k), e1 = innerLoop.get((k + 1) % m);
            var sp = splitPolygonByDirectedLine(glassPoly, e0, e1);
            if (thick > 1e-5 && sp.out.size() >= 3 && polygonArea2D(sp.out) > 1e-10) {
                emitFramePolyFrontBack(sp.out, frame, field, frontN, backN, fArgb, frameOut);
            }
            glassPoly = sp.in;
        }
        if (glassPoly.size() < 3 || polygonArea2D(glassPoly) <= 1e-10) return;

        if (spec.hasGlass() && spec.glassAlpha() > 1e-4) {
            Vec[] mids = new Vec[glassPoly.size()];
            for (int i = 0; i < glassPoly.size(); i++) {
                PolyVert pv = glassPoly.get(i);
                Vec pf = pv.v().pos();
                double wIn = frame.depth(pf);
                double wOut = field.exitDepthAt(pv.uv(), wIn);
                mids[i] = field.zeroThickness ? pf : pf.plus(frame.w().times(0.5 * (wOut - wIn)));
            }
            int gid = glassOut.size();
            for (int i = 1; i + 1 < mids.length; i++) {
                gid = addTriIfValid(glassOut, gid, mids[0], mids[i], mids[i + 1], frontN, "hexie:window_glass", gArgb, true);
                gid = addTriIfValid(glassOut, gid, mids[0], mids[i + 1], mids[i], backN, "hexie:window_glass", gArgb, true);
            }
        }

        if (thick > 1e-5 && !field.zeroThickness) {
            int fid = frameOut.size();
            for (int i = 0; i < glassPoly.size(); i++) {
                PolyVert p0 = glassPoly.get(i), p1 = glassPoly.get((i + 1) % glassPoly.size());
                if (distSq(p0.uv(), p1.uv()) <= 1e-12) continue;
                PolyVert[] aligned = matchLoopEdge(p0, p1, innerLoop);
                if (aligned == null) continue;
                Vec p0f = aligned[0].v().pos(), p1f = aligned[1].v().pos();
                double w0f = frame.depth(p0f), w1f = frame.depth(p1f);
                double w0b = field.exitDepthAt(aligned[0].uv(), w0f), w1b = field.exitDepthAt(aligned[1].uv(), w1f);
                Vec p0b = p0f.plus(frame.w().times(w0b - w0f));
                Vec p1b = p1f.plus(frame.w().times(w1b - w1f));
                Vec edge = p1f.minus(p0f);
                Vec inN = edge.cross(frame.w());
                double len = Math.sqrt(inN.dot(inN));
                if (len > 1e-12) {
                    fid = addQuadIfValid(frameOut, fid, p0f, p1f, p1b, p0b, inN.times(1.0 / len), "hexie:window_frame", fArgb, false);
                }
            }
        }
    }

    private static void emitFramePolyFrontBack(List<PolyVert> poly, PlaneFrame frame, DepthField field,
                                               Vec frontN, Vec backN, int fArgb, List<Tri> frameOut) {
        int n = poly.size();
        Vec[] front = new Vec[n], back = new Vec[n];
        for (int i = 0; i < n; i++) {
            PolyVert pv = poly.get(i);
            Vec pf = pv.v().pos();
            double wIn = frame.depth(pf);
            double wOut = field.exitDepthAt(pv.uv(), wIn);
            front[i] = pf.plus(frame.w().times(-0.002));
            back[i] = pf.plus(frame.w().times(wOut - wIn + 0.002));
        }
        int fid = frameOut.size();
        for (int i = 1; i + 1 < n; i++) {
            fid = addTriIfValid(frameOut, fid, front[0], front[i], front[i + 1], frontN, "hexie:window_frame", fArgb, false);
            fid = addTriIfValid(frameOut, fid, back[0], back[i + 1], back[i], backN, "hexie:window_frame", fArgb, false);
        }
    }

    private record DepthSplit(List<Tri> shallower, List<Tri> deeper) {}

    private static DepthSplit splitTriByMaxDepth(Tri tri, PlaneFrame frame, double maxW) {
        double da = frame.depth(tri.a().pos()), db = frame.depth(tri.b().pos()), dc = frame.depth(tri.c().pos());
        if (da <= maxW && db <= maxW && dc <= maxW) return new DepthSplit(List.of(tri), List.of());
        if (da >= maxW && db >= maxW && dc >= maxW) return new DepthSplit(List.of(), List.of(tri));
        List<Vertex> poly = List.of(tri.a(), tri.b(), tri.c());
        double[] ds = {da, db, dc};
        var shallow = new ArrayList<Vertex>(4);
        var deep = new ArrayList<Vertex>(4);
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            Vertex p = poly.get(i), q = poly.get(j);
            double dp = ds[i] - maxW, dq = ds[j] - maxW;
            if (dp <= CLIP_EPS) shallow.add(p);
            if (dp >= -CLIP_EPS) deep.add(p);
            if ((dp < -CLIP_EPS && dq > CLIP_EPS) || (dp > CLIP_EPS && dq < -CLIP_EPS)) {
                double t = dp / (dp - dq);
                Vec pos = p.pos().plus(q.pos().minus(p.pos()).times(t));
                Vertex mid = new Vertex(pos, p.normal(), p.u() + (q.u() - p.u()) * t, p.v() + (q.v() - p.v()) * t);
                shallow.add(mid);
                deep.add(mid);
            }
        }
        var sTris = new ArrayList<Tri>(2);
        var dTris = new ArrayList<Tri>(2);
        fanTriVerts(shallow, tri, sTris);
        fanTriVerts(deep, tri, dTris);
        return new DepthSplit(sTris, dTris);
    }

    private static void fanTriVerts(List<Vertex> poly, Tri src, List<Tri> out) {
        if (poly.size() < 3) return;
        Vertex v0 = poly.get(0);
        for (int i = 1; i + 1 < poly.size(); i++) {
            Tri t = new Tri(src.originalFaceId(), v0, poly.get(i), poly.get(i + 1),
                    src.materialId(), src.argb(), src.translucent());
            if (t.area2() > 1e-11) out.add(t);
        }
    }

    public record WindowParts(CompiledMesh frame, CompiledMesh glass) {}

    /** Standalone parameterized window frame + glass builder (e.g. for preview meshes). */
    public static WindowParts buildParametricWindow(PlaneFrame frame, ParametricWindowSpec spec,
                                                    double wEntry, double wExit, boolean zeroThickness,
                                                    int cornerSegments) {
        int segs = Math.max(1, cornerSegments);
        double cx = spec.offsetU(), cy = spec.offsetV();
        double hw = spec.width() * 0.5, hh = spec.height() * 0.5;
        double rOut = Math.max(0.0, spec.effectiveCornerRadius());
        double thick = Math.max(0.0, spec.effectiveFrameThickness());
        double ihw = Math.max(0.0, hw - thick), ihh = Math.max(0.0, hh - thick);
        double rIn = Math.max(0.0, Math.min(rOut - thick, Math.min(ihw, ihh)));

        int stepsPerCorner = rOut > 1e-6 ? segs : 1;
        var outerPts = samplePairedRoundedRect(cx, cy, hw, hh, rOut, stepsPerCorner);
        var innerPts = ihw > 1e-4 && ihh > 1e-4
                ? samplePairedRoundedRect(cx, cy, ihw, ihh, rIn, stepsPerCorner)
                : null;

        var frameTris = new ArrayList<Tri>();
        var glassTris = new ArrayList<Tri>();
        int fid = 0;
        int fArgb = spec.frameArgb();
        Vec frontN = frame.outward();
        Vec backN = frame.w();

        if (thick > 1e-5) {
            if (innerPts == null) {
                Vec centerF = frame.unproject(cx, cy, wEntry);
                Vec centerB = frame.unproject(cx, cy, wExit);
                for (int i = 0; i < outerPts.size(); i++) {
                    UV a = outerPts.get(i), b = outerPts.get((i + 1) % outerPts.size());
                    fid = addTriIfValid(frameTris, fid, centerF, frame.unproject(a.u(), a.v(), wEntry),
                            frame.unproject(b.u(), b.v(), wEntry), frontN, "hexie:window_frame", fArgb, false);
                    fid = addTriIfValid(frameTris, fid, centerB, frame.unproject(b.u(), b.v(), wExit),
                            frame.unproject(a.u(), a.v(), wExit), backN, "hexie:window_frame", fArgb, false);
                }
            } else {
                int n = outerPts.size();
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    UV o0 = outerPts.get(i), o1 = outerPts.get(j);
                    UV i0 = innerPts.get(i), i1 = innerPts.get(j);
                    Vec o0f = frame.unproject(o0.u(), o0.v(), wEntry);
                    Vec o1f = frame.unproject(o1.u(), o1.v(), wEntry);
                    Vec i1f = frame.unproject(i1.u(), i1.v(), wEntry);
                    Vec i0f = frame.unproject(i0.u(), i0.v(), wEntry);
                    fid = addQuadIfValid(frameTris, fid, o0f, o1f, i1f, i0f, frontN, "hexie:window_frame", fArgb, false);

                    Vec o0b = frame.unproject(o0.u(), o0.v(), wExit);
                    Vec o1b = frame.unproject(o1.u(), o1.v(), wExit);
                    Vec i1b = frame.unproject(i1.u(), i1.v(), wExit);
                    Vec i0b = frame.unproject(i0.u(), i0.v(), wExit);
                    fid = addQuadIfValid(frameTris, fid, o0b, i0b, i1b, o1b, backN, "hexie:window_frame", fArgb, false);

                    if (!zeroThickness) {
                        Vec edgeIn = i1f.minus(i0f);
                        Vec inN = edgeIn.cross(frame.w());
                        double inLen = Math.sqrt(inN.dot(inN));
                        if (inLen > 1e-12) {
                            fid = addQuadIfValid(frameTris, fid, i0f, i1f, i1b, i0b, inN.times(1.0 / inLen), "hexie:window_frame", fArgb, false);
                        }
                    }
                }
            }
        }

        if (spec.hasGlass() && spec.glassAlpha() > 1e-4) {
            List<UV> glassLoop = innerPts != null ? innerPts : outerPts;
            double wGlass = zeroThickness ? wEntry : (wEntry + wExit) * 0.5;
            Vec cPos = frame.unproject(cx, cy, wGlass);
            int gArgb = spec.glassArgb();
            int gid = 0;
            for (int i = 0; i < glassLoop.size(); i++) {
                UV a = glassLoop.get(i), b = glassLoop.get((i + 1) % glassLoop.size());
                Vec pa = frame.unproject(a.u(), a.v(), wGlass);
                Vec pb = frame.unproject(b.u(), b.v(), wGlass);
                gid = addTriIfValid(glassTris, gid, cPos, pa, pb, frontN, "hexie:window_glass", gArgb, true);
                gid = addTriIfValid(glassTris, gid, cPos, pb, pa, backN, "hexie:window_glass", gArgb, true);
            }
        }

        return new WindowParts(CompiledMesh.of(frameTris), CompiledMesh.of(glassTris));
    }

    public static CompiledMesh buildPreviewMesh(PlaneFrame frame, ParametricWindowSpec spec, double depthThickness) {
        boolean zero = depthThickness <= THICKNESS_EPS;
        var parts = buildParametricWindow(frame, spec, 0.0, zero ? 0.0 : depthThickness, zero,
                ParametricWindowSpec.DEFAULT_CORNER_SEGMENTS);
        return CompiledMesh.merge(List.of(parts.frame, parts.glass));
    }

    private static List<UV> samplePairedRoundedRect(double cx, double cy, double hw, double hh, double r, int stepsPerCorner) {
        double cr = Math.max(0.0, Math.min(r, Math.min(hw, hh)));
        var pts = new ArrayList<UV>(4 * (stepsPerCorner + 1));
        double[] ccx = {cx + hw - cr, cx + hw - cr, cx - hw + cr, cx - hw + cr};
        double[] ccy = {cy - hh + cr, cy + hh - cr, cy + hh - cr, cy - hh + cr};
        double[] startAngle = {-Math.PI * 0.5, 0.0, Math.PI * 0.5, Math.PI};
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= stepsPerCorner; i++) {
                double ang = startAngle[c] + (Math.PI * 0.5) * i / stepsPerCorner;
                pts.add(new UV(ccx[c] + Math.cos(ang) * cr, ccy[c] + Math.sin(ang) * cr));
            }
        }
        return pts;
    }

    private record ClipOut(List<Tri> outside, List<PolyVert> inside, boolean cut) {}

    private record PolyVert(Vertex v, UV uv) {
        PolyVert lerp(PolyVert other, double t) {
            Vec p = v.pos().plus(other.v.pos().minus(v.pos()).times(t));
            Vec n = v.normal().plus(other.v.normal().minus(v.normal()).times(t));
            double len = Math.sqrt(n.dot(n));
            n = len > 1e-12 ? n.times(1.0 / len) : v.normal();
            double tu = v.u() + (other.v.u() - v.u()) * t;
            double tv = v.v() + (other.v.v() - v.v()) * t;
            return new PolyVert(new Vertex(p, n, tu, tv),
                    new UV(uv.u() + (other.uv.u() - uv.u()) * t, uv.v() + (other.uv.v() - uv.v()) * t));
        }
    }

    private static ClipOut subtractConvexFromTri(Tri tri, PlaneFrame frame, List<UV> convexCcw,
                                                 boolean collectWallSegments, List<CutSegment> wallSegments) {
        List<PolyVert> inside = List.of(
                new PolyVert(tri.a(), frame.project(tri.a().pos())),
                new PolyVert(tri.b(), frame.project(tri.b().pos())),
                new PolyVert(tri.c(), frame.project(tri.c().pos())));
        var outsideTris = new ArrayList<Tri>();

        int m = convexCcw.size();
        for (int k = 0; k < m; k++) {
            if (inside.size() < 3) break;
            UV e0 = convexCcw.get(k);
            UV e1 = convexCcw.get((k + 1) % m);
            var split = splitPolygonByDirectedLine(inside, e0, e1);
            if (split.out.size() >= 3) {
                fanTriangulate(split.out, tri, outsideTris);
            }
            inside = split.in;
        }

        if (inside.size() < 3 || polygonArea2D(inside) <= 1e-10) {
            return new ClipOut(List.of(tri), List.of(), false);
        }

        if (collectWallSegments) {
            for (int i = 0; i < inside.size(); i++) {
                PolyVert a = inside.get(i), b = inside.get((i + 1) % inside.size());
                if (distSq(a.uv(), b.uv()) <= 1e-12) continue;
                PolyVert[] aligned = matchLoopEdge(a, b, convexCcw);
                if (aligned != null) {
                    wallSegments.add(new CutSegment(aligned[0], aligned[1], tri.materialId(), tri.argb()));
                }
            }
        }
        return new ClipOut(outsideTris, inside, true);
    }

    private record SplitPoly(List<PolyVert> in, List<PolyVert> out) {}

    private static SplitPoly splitPolygonByDirectedLine(List<PolyVert> poly, UV a, UV b) {
        var in = new ArrayList<PolyVert>(poly.size() + 2);
        var out = new ArrayList<PolyVert>(poly.size() + 2);
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            PolyVert p = poly.get(i), q = poly.get((i + 1) % n);
            double dp = cross2D(a, b, p.uv()), dq = cross2D(a, b, q.uv());
            if (dp >= -CLIP_EPS) in.add(p);
            if (dp <= CLIP_EPS) out.add(p);
            if ((dp > CLIP_EPS && dq < -CLIP_EPS) || (dp < -CLIP_EPS && dq > CLIP_EPS)) {
                PolyVert mid = p.lerp(q, dp / (dp - dq));
                in.add(mid);
                out.add(mid);
            }
        }
        return new SplitPoly(dedup(in), dedup(out));
    }

    private static List<PolyVert> dedup(List<PolyVert> pts) {
        if (pts.size() < 2) return pts;
        var out = new ArrayList<PolyVert>(pts.size());
        for (var p : pts) {
            if (out.isEmpty() || distSq(out.get(out.size() - 1).uv(), p.uv()) > 1e-14) out.add(p);
        }
        if (out.size() > 1 && distSq(out.get(0).uv(), out.get(out.size() - 1).uv()) <= 1e-14) out.remove(out.size() - 1);
        return out;
    }

    private static List<UV> dedupUv(List<UV> pts) {
        var out = new ArrayList<UV>(pts.size());
        for (var p : pts) {
            if (out.isEmpty() || distSq(out.get(out.size() - 1), p) > 1e-12) out.add(p);
        }
        if (out.size() > 1 && distSq(out.get(0), out.get(out.size() - 1)) <= 1e-12) out.remove(out.size() - 1);
        return out;
    }

    private static void fanTriangulate(List<PolyVert> poly, Tri src, List<Tri> out) {
        if (poly.size() < 3) return;
        Vertex v0 = poly.get(0).v();
        for (int i = 1; i + 1 < poly.size(); i++) {
            Tri t = new Tri(src.originalFaceId(), v0, poly.get(i).v(), poly.get(i + 1).v(),
                    src.materialId(), src.argb(), src.translucent());
            if (t.area2() > 1e-11) out.add(t);
        }
    }

    private static boolean triIntersectsConvex(UV a, UV b, UV c, List<UV> convexCcw) {
        List<PolyVert> cur = List.of(
                new PolyVert(new Vertex(Vec.ZERO, Vec.ZERO, 0, 0), a),
                new PolyVert(new Vertex(Vec.ZERO, Vec.ZERO, 0, 0), b),
                new PolyVert(new Vertex(Vec.ZERO, Vec.ZERO, 0, 0), c));
        for (int k = 0; k < convexCcw.size() && cur.size() >= 3; k++) {
            cur = splitPolygonByDirectedLine(cur, convexCcw.get(k), convexCcw.get((k + 1) % convexCcw.size())).in;
        }
        return cur.size() >= 3 && polygonArea2D(cur) > 1e-10;
    }

    private static PolyVert[] matchLoopEdge(PolyVert p0, PolyVert p1, List<UV> mask) {
        for (int k = 0; k < mask.size(); k++) {
            UV e0 = mask.get(k), e1 = mask.get((k + 1) % mask.size());
            double len = Math.sqrt(distSq(e0, e1));
            if (len <= 1e-12) continue;
            if (Math.abs(cross2D(e0, e1, p0.uv())) / len <= 1e-6 && Math.abs(cross2D(e0, e1, p1.uv())) / len <= 1e-6) {
                double dot = (p1.uv().u() - p0.uv().u()) * (e1.u() - e0.u()) + (p1.uv().v() - p0.uv().v()) * (e1.v() - e0.v());
                return dot >= 0 ? new PolyVert[]{p0, p1} : new PolyVert[]{p1, p0};
            }
        }
        return null;
    }

    private static List<UV> ensureCcw(List<UV> pts) {
        double area2 = 0;
        for (int i = 0; i < pts.size(); i++) {
            UV a = pts.get(i), b = pts.get((i + 1) % pts.size());
            area2 += a.u() * b.v() - b.u() * a.v();
        }
        if (area2 >= 0) return pts;
        var rev = new ArrayList<>(pts);
        Collections.reverse(rev);
        return List.copyOf(rev);
    }

    private static double polygonArea2D(List<PolyVert> poly) {
        double sum = 0;
        for (int i = 0; i < poly.size(); i++) {
            UV a = poly.get(i).uv(), b = poly.get((i + 1) % poly.size()).uv();
            sum += a.u() * b.v() - b.u() * a.v();
        }
        return Math.abs(sum) * 0.5;
    }

    private static double cross2D(UV a, UV b, UV c) {
        return (b.u() - a.u()) * (c.v() - a.v()) - (b.v() - a.v()) * (c.u() - a.u());
    }

    private static double distSq(UV a, UV b) {
        double du = a.u() - b.u(), dv = a.v() - b.v();
        return du * du + dv * dv;
    }

    private static int addTriIfValid(List<Tri> out, int fid, Vec a, Vec b, Vec c, Vec n,
                                     String mat, int argb, boolean translucent) {
        Tri t = new Tri(fid, new Vertex(a, n, 0, 0), new Vertex(b, n, 1, 0), new Vertex(c, n, 1, 1),
                mat, argb, translucent);
        if (t.area2() > 1e-11) {
            out.add(t);
            return fid + 1;
        }
        return fid;
    }

    private static int addQuadIfValid(List<Tri> out, int fid, Vec p0, Vec p1, Vec p2, Vec p3, Vec n,
                                      String mat, int argb, boolean translucent) {
        fid = addTriIfValid(out, fid, p0, p1, p2, n, mat, argb, translucent);
        fid = addTriIfValid(out, fid, p0, p2, p3, n, mat, argb, translucent);
        return fid;
    }
}
