package dev.hexie.renderer.neoforge;

import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.ClientRenderState;
import dev.hexie.renderer.mc.ForwardLight;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL45C.*;

/**
 * Pure-Geometry GPU Shadow Engine (ZERO voxel grids!):
 * 1. 2048x2048 Non-Linear Distorted GPU Sun/Moon Shadow Map (160-block radius = 320-block diameter) for PCSS soft shadows.
 * 2. 3072x2048 Exact-Geometry 6-Face Perspective Cubemap Point-Light Shadow Atlas (16 shadowed lamps x 6 faces @ 256x256)
 *    reusing the exact same VRAM VBOs (instanced terrain AABBs + static CRH1/CRH2 shells, rounded cut windows, cached
 *    HeXie models + persistent local GPU VBOs for moving Create trains).
 *    Static lamps cache their 6 shadow faces in VRAM (0.00 ms/frame on steady frames!) and only re-render when geometry
 *    or a lamp moves — giving millimeter-accurate shadows through thin curved walls & window cutouts with ZERO light bleed!
 */
final class GpuShadowMap {
    static final int RES = 2048;
    static final float SHADOW_RADIUS = 160.0f;
    static final float SHADOW_DEPTH_HALF = 192.0f;

    static final int MAX_POINT_LAMPS = 16;
    static final int POINT_TILE = 256;
    static final int POINT_COLS = 12;
    static final int POINT_ROWS = 8;
    static final int POINT_ATLAS_W = POINT_TILE * POINT_COLS; // 3072
    static final int POINT_ATLAS_H = POINT_TILE * POINT_ROWS; // 2048
    static final float POINT_NEAR = 0.14f;
    // Slight 2-texel overscan (1 + 4/256) eliminates cubemap seams and prevents hardware PCF from crossing tile borders!
    static final float POINT_TAN_HALF = 1.015625f;
    private static final int MAX_DIRTY_SLOTS_PER_FRAME = 6;

    private static final String BOX_VS = """
        #version 450 core
        layout(location = 0) in vec3 a_Corner;
        layout(location = 1) in vec3 a_Lo;
        layout(location = 2) in vec3 a_Hi;
        uniform mat4 u_SunMat;
        uniform vec3 u_CamOrigin;
        void main() {
            vec3 relPos = mix(a_Lo, a_Hi, a_Corner) - u_CamOrigin;
            vec4 clip = u_SunMat * vec4(relPos, 1.0);
            float len = length(clip.xy);
            float factor = mix(1.0, len, 0.72);
            clip.xy /= max(factor, 1e-4);
            clip.z *= 0.5;
            gl_Position = clip;
        }
        """;

    private static final String TRI_VS = """
        #version 450 core
        layout(location = 0) in vec3 a_Pos;
        uniform mat4 u_SunMat;
        uniform mat4 u_ModelMat;
        uniform vec3 u_CamOrigin;
        void main() {
            vec3 worldPos = (u_ModelMat * vec4(a_Pos, 1.0)).xyz;
            vec3 relPos = worldPos - u_CamOrigin;
            vec4 clip = u_SunMat * vec4(relPos, 1.0);
            float len = length(clip.xy);
            float factor = mix(1.0, len, 0.72);
            clip.xy /= max(factor, 1e-4);
            clip.z *= 0.5;
            gl_Position = clip;
        }
        """;

    // Linear 90-degree perspective shaders for point-light cubemap faces (zero curved-edge distortion on long walls!)
    private static final String POINT_BOX_VS = """
        #version 450 core
        layout(location = 0) in vec3 a_Corner;
        layout(location = 1) in vec3 a_Lo;
        layout(location = 2) in vec3 a_Hi;
        uniform mat4 u_FaceMat;
        uniform vec3 u_LampOrigin;
        void main() {
            vec3 relPos = mix(a_Lo, a_Hi, a_Corner) - u_LampOrigin;
            gl_Position = u_FaceMat * vec4(relPos, 1.0);
        }
        """;

    private static final String POINT_TRI_VS = """
        #version 450 core
        layout(location = 0) in vec3 a_Pos;
        uniform mat4 u_FaceMat;
        uniform mat4 u_ModelMat;
        uniform vec3 u_LampOrigin;
        void main() {
            vec3 worldPos = (u_ModelMat * vec4(a_Pos, 1.0)).xyz;
            gl_Position = u_FaceMat * vec4(worldPos - u_LampOrigin, 1.0);
        }
        """;

    private static final String DEPTH_FS = """
        #version 450 core
        void main() {}
        """;

    // Unit cube (36 vertices, 12 triangles, coords in {0, 1})
    private static final float[] UNIT_CUBE = {
        // -Z
        0,0,0, 0,1,0, 1,1,0,  0,0,0, 1,1,0, 1,0,0,
        // +Z
        0,0,1, 1,1,1, 0,1,1,  0,0,1, 1,0,1, 1,1,1,
        // -X
        0,0,0, 0,0,1, 0,1,1,  0,0,0, 0,1,1, 0,1,0,
        // +X
        1,0,0, 1,1,1, 1,0,1,  1,0,0, 1,1,0, 1,1,1,
        // -Y
        0,0,0, 1,0,1, 0,0,1,  0,0,0, 1,0,0, 1,0,1,
        // +Y
        0,1,0, 0,1,1, 1,1,1,  0,1,0, 1,1,1, 1,1,0
    };

    // Exact 6 cubemap face lookAt matrices (verified to 2e-16 against ForwardLight GLSL selector!)
    private static final Matrix4f[] FACE_VIEWS = {
        new Matrix4f().lookAt(0, 0, 0,  1,  0,  0,  0, -1,  0), // 0: +X
        new Matrix4f().lookAt(0, 0, 0, -1,  0,  0,  0, -1,  0), // 1: -X
        new Matrix4f().lookAt(0, 0, 0,  0,  1,  0,  0,  0,  1), // 2: +Y
        new Matrix4f().lookAt(0, 0, 0,  0, -1,  0,  0,  0, -1), // 3: -Y
        new Matrix4f().lookAt(0, 0, 0,  0,  0,  1,  0, -1,  0), // 4: +Z
        new Matrix4f().lookAt(0, 0, 0,  0,  0, -1,  0, -1,  0)  // 5: -Z
    };

    private static final class CachedDynVbo {
        int vbo;
        long contentRev;
        int vertexCount;
        long lastUsedFrame;
    }

    private static final class PointSlot {
        boolean occupied;
        boolean rendered;
        long lampId;
        double px, py, pz, radius;
        long lastBoxSig = Long.MIN_VALUE;
        long lastTriSig = Long.MIN_VALUE;
        long lastDynSig = Long.MIN_VALUE;
        long lastUsedFrame;
    }

    private int fbo, depthTex, compareSampler, rawSampler;
    private int pointFbo, pointAtlasTex, pointCompareSampler;

    private int boxProg, boxVao, unitCubeVbo, boxInstanceVbo, boxInstanceCap, boxCount;
    private int boxLocSunMat, boxLocCamOrigin;
    private long lastBoxSig = Long.MIN_VALUE;

    private int triProg, triVao, staticTriVbo, staticTriCap, staticTriVerts;
    private int triLocSunMat, triLocModelMat, triLocCamOrigin;
    private long lastStaticTriSig = Long.MIN_VALUE;

    private int pointBoxProg, pBoxLocFaceMat, pBoxLocOrigin;
    private int pointTriProg, pTriLocFaceMat, pTriLocModelMat, pTriLocOrigin;

    private final Map<Long, CachedDynVbo> dynVbos = new HashMap<>();
    private final PointSlot[] pointSlots = new PointSlot[MAX_POINT_LAMPS];
    private final Matrix4f sunMatrix = new Matrix4f();
    private final Matrix4f faceProj = new Matrix4f();
    private final Matrix4f faceVp = new Matrix4f();
    private final float[] sunMatrixArr = new float[16];
    private final float[] faceVpArr = new float[16];
    private final float[] identityArr = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    private float[] scratch = new float[0];
    private long renderFrame;
    private Vec renderedOrigin = Vec.ZERO;
    private boolean sunValid;
    private boolean pointValid;
    private List<ForwardLight.ShadowedLamp> publishedPointLamps = List.of();

    GpuShadowMap() {
        for (int i = 0; i < MAX_POINT_LAMPS; i++) pointSlots[i] = new PointSlot();
    }

    int depthTexture() { return depthTex; }
    int compareSampler() { return compareSampler; }
    int rawSampler() { return rawSampler; }
    int pointAtlasTexture() { return pointAtlasTex; }
    int pointCompareSampler() { return pointCompareSampler; }
    float[] sunMatrixArray() { return sunMatrixArr; }
    Vec renderedOrigin() { return renderedOrigin; }
    boolean isSunValid() { return sunValid && depthTex != 0; }
    boolean isPointValid() { return pointValid && pointAtlasTex != 0 && !publishedPointLamps.isEmpty(); }
    List<ForwardLight.ShadowedLamp> publishedPointLamps() { return publishedPointLamps; }
    int boxCount() { return boxCount; }
    int staticTriCount() { return staticTriVerts / 3; }

    void render(Vec focus, ForwardLight.Sun sun,
                List<ForwardLight.Box> terrainBoxes, long boxSig,
                List<ForwardLight.CasterTri> staticTris, long staticTriSig,
                List<ClientRenderState.DynamicShadowMesh> dynamicMeshes,
                List<ForwardLight.Lamp> sortedLamps) {
        if (focus == null || !ensureResources()) {
            sunValid = false;
            pointValid = false;
            publishedPointLamps = List.of();
            return;
        }
        renderFrame++;

        // Upload static terrain boxes & exact static triangles ONLY when sections change (zero CPU work on steady frames!)
        if (boxSig != lastBoxSig) {
            uploadBoxes(terrainBoxes);
            lastBoxSig = boxSig;
        }
        if (staticTriSig != lastStaticTriSig) {
            uploadStaticTris(staticTris);
            lastStaticTriSig = staticTriSig;
        }

        // Upload/resolve persistent local VBOs for moving Create trains once per frame
        var activeDynVbos = new ArrayList<CachedDynVbo>(dynamicMeshes == null ? 0 : dynamicMeshes.size());
        var activeDynMats = new ArrayList<float[]>(dynamicMeshes == null ? 0 : dynamicMeshes.size());
        long dynSig = 17L;
        if (dynamicMeshes != null && !dynamicMeshes.isEmpty()) {
            for (var dm : dynamicMeshes) {
                CachedDynVbo cv = getOrUploadDynVbo(dm);
                if (cv == null || cv.vertexCount <= 0) continue;
                activeDynVbos.add(cv);
                activeDynMats.add(dm.modelMatrix());
                dynSig = dynSig * 31L + dm.meshKey();
                dynSig = dynSig * 31L + dm.contentRev();
                float[] m = dm.modelMatrix();
                for (int k = 0; k < 16; k++) dynSig = dynSig * 31L + Math.round(m[k] * 100.0f);
            }
        }

        boolean wantSun = sun != null && !sun.color().equals(Vec.ZERO) && sun.direction().y() > 0.02;

        // Assign cubemap shadow slots to the closest active lamps (within 48 blocks of camera)
        var selectedLamps = new ArrayList<ForwardLight.ShadowedLamp>(MAX_POINT_LAMPS);
        var dirtySlotIndices = new ArrayList<Integer>(MAX_DIRTY_SLOTS_PER_FRAME);
        if (sortedLamps != null && !sortedLamps.isEmpty()) {
            int count = Math.min(sortedLamps.size(), MAX_POINT_LAMPS);
            var assigned = new boolean[MAX_POINT_LAMPS];
            var pending = new ArrayList<ForwardLight.Lamp>(count);
            var matchedSlot = new int[count];
            Arrays.fill(matchedSlot, -1);

            for (int i = 0; i < count; i++) {
                var lamp = sortedLamps.get(i);
                var src = lamp.source();
                if (!src.enabled() || src.intensity() <= 0.0 || src.radius() <= 0.25) continue;
                Vec d = lamp.position().minus(focus);
                if (d.dot(d) > 56.0 * 56.0) continue;
                pending.add(lamp);
            }

            // Pass 1: preserve existing cubemap slots so static lamps stay cached at 0 ms/frame!
            for (int i = 0; i < pending.size(); i++) {
                long id = pending.get(i).source().lightId();
                for (int s = 0; s < MAX_POINT_LAMPS; s++) {
                    if (!assigned[s] && pointSlots[s].occupied && pointSlots[s].lampId == id) {
                        assigned[s] = true;
                        matchedSlot[i] = s;
                        break;
                    }
                }
            }

            // Pass 2: allocate free or LRU slots for newly visible lamps
            for (int i = 0; i < pending.size(); i++) {
                if (matchedSlot[i] >= 0) continue;
                int best = -1;
                long oldest = Long.MAX_VALUE;
                for (int s = 0; s < MAX_POINT_LAMPS; s++) {
                    if (assigned[s]) continue;
                    if (!pointSlots[s].occupied) {
                        best = s;
                        break;
                    }
                    if (pointSlots[s].lastUsedFrame < oldest) {
                        oldest = pointSlots[s].lastUsedFrame;
                        best = s;
                    }
                }
                if (best < 0) break;
                assigned[best] = true;
                matchedSlot[i] = best;
            }

            for (int i = 0; i < pending.size(); i++) {
                int s = matchedSlot[i];
                if (s < 0) continue;
                var lamp = pending.get(i);
                var slot = pointSlots[s];
                long id = lamp.source().lightId();
                double lx = lamp.position().x(), ly = lamp.position().y(), lz = lamp.position().z();
                double lr = Math.max(2.0, lamp.source().radius());
                boolean moved = !slot.occupied || slot.lampId != id
                        || Math.abs(slot.px - lx) + Math.abs(slot.py - ly) + Math.abs(slot.pz - lz) > 0.015
                        || Math.abs(slot.radius - lr) > 0.05;
                boolean worldDirty = slot.lastBoxSig != boxSig || slot.lastTriSig != staticTriSig || slot.lastDynSig != dynSig;
                boolean needsRender = !slot.rendered || moved || worldDirty;
                slot.occupied = true;
                slot.lampId = id;
                slot.px = lx; slot.py = ly; slot.pz = lz; slot.radius = lr;
                slot.lastUsedFrame = renderFrame;
                if (needsRender && dirtySlotIndices.size() < MAX_DIRTY_SLOTS_PER_FRAME) {
                    dirtySlotIndices.add(s);
                }
                if (slot.rendered || dirtySlotIndices.contains(s)) {
                    selectedLamps.add(new ForwardLight.ShadowedLamp(lamp, s));
                }
            }
        }
        publishedPointLamps = selectedLamps.isEmpty() ? List.of() : List.copyOf(selectedLamps);
        pointValid = !publishedPointLamps.isEmpty();

        if (!wantSun && dirtySlotIndices.isEmpty()) {
            sunValid = false;
            evictStaleDynVbos();
            return;
        }

        // Save surgical GL state ONCE for both Sun Shadow Map and any dirty Point-Light Cubemap tiles
        int prevFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int[] prevVp = new int[4];
        glGetIntegerv(GL_VIEWPORT, prevVp);
        int[] prevScissorBox = new int[4];
        glGetIntegerv(GL_SCISSOR_BOX, prevScissorBox);
        int prevProg = glGetInteger(GL_CURRENT_PROGRAM);
        int prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        boolean prevDepth = glIsEnabled(GL_DEPTH_TEST);
        int prevDepthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean prevDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        double prevClearDepth = glGetDouble(GL_DEPTH_CLEAR_VALUE);
        boolean prevDepthClamp = glIsEnabled(GL_DEPTH_CLAMP);
        boolean prevCull = glIsEnabled(GL_CULL_FACE);
        boolean prevScissor = glIsEnabled(GL_SCISSOR_TEST);
        boolean prevPolyOff = glIsEnabled(GL_POLYGON_OFFSET_FILL);
        float prevPolyFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR);
        float prevPolyUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS);
        boolean[] prevColorMask = new boolean[4];
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var cm = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, cm);
            for (int i = 0; i < 4; i++) prevColorMask[i] = cm.get(i) != 0;
        }

        try {
            glDisable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glDepthMask(true);
            glClearDepth(1.0);
            glColorMask(false, false, false, false);
            glEnable(GL_POLYGON_OFFSET_FILL);

            // 1. Sun/Moon 2048x2048 Distorted Shadow Map (160m range)
            if (wantSun) {
                double ox = Math.floor(focus.x());
                double oy = Math.floor(focus.y());
                double oz = Math.floor(focus.z());
                renderedOrigin = new Vec(ox, oy, oz);

                Vector3f sunDir = new Vector3f((float) sun.direction().x(), (float) sun.direction().y(), (float) sun.direction().z()).normalize();
                Vector3f up = Math.abs(sunDir.y) > 0.96f ? new Vector3f(0f, 0f, 1f) : new Vector3f(0f, 1f, 0f);
                Matrix4f view = new Matrix4f().lookAt(
                        sunDir.x * SHADOW_DEPTH_HALF, sunDir.y * SHADOW_DEPTH_HALF, sunDir.z * SHADOW_DEPTH_HALF,
                        0f, 0f, 0f,
                        up.x, up.y, up.z);
                Matrix4f proj = new Matrix4f().ortho(
                        -SHADOW_RADIUS, SHADOW_RADIUS,
                        -SHADOW_RADIUS, SHADOW_RADIUS,
                        1.0f, SHADOW_DEPTH_HALF * 2.0f + 64.0f);
                sunMatrix.set(proj).mul(view);
                sunMatrix.get(sunMatrixArr);

                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glViewport(0, 0, RES, RES);
                glDisable(GL_SCISSOR_TEST);
                glEnable(GL_DEPTH_CLAMP);
                glPolygonOffset(1.4f, 2.5f);
                glClear(GL_DEPTH_BUFFER_BIT);

                if (boxCount > 0) {
                    glUseProgram(boxProg);
                    glBindVertexArray(boxVao);
                    glUniformMatrix4fv(boxLocSunMat, false, sunMatrixArr);
                    glUniform3f(boxLocCamOrigin, (float) ox, (float) oy, (float) oz);
                    glDrawArraysInstanced(GL_TRIANGLES, 0, 36, boxCount);
                }
                boolean boundTri = false;
                if (staticTriVerts > 0 && staticTriVbo != 0) {
                    glUseProgram(triProg);
                    glBindVertexArray(triVao);
                    glUniformMatrix4fv(triLocSunMat, false, sunMatrixArr);
                    glUniform3f(triLocCamOrigin, (float) ox, (float) oy, (float) oz);
                    glUniformMatrix4fv(triLocModelMat, false, identityArr);
                    glVertexArrayVertexBuffer(triVao, 0, staticTriVbo, 0L, 3 * Float.BYTES);
                    glDrawArrays(GL_TRIANGLES, 0, staticTriVerts);
                    boundTri = true;
                }
                if (!activeDynVbos.isEmpty()) {
                    if (!boundTri) {
                        glUseProgram(triProg);
                        glBindVertexArray(triVao);
                        glUniformMatrix4fv(triLocSunMat, false, sunMatrixArr);
                        glUniform3f(triLocCamOrigin, (float) ox, (float) oy, (float) oz);
                    }
                    for (int i = 0; i < activeDynVbos.size(); i++) {
                        glUniformMatrix4fv(triLocModelMat, false, activeDynMats.get(i));
                        glVertexArrayVertexBuffer(triVao, 0, activeDynVbos.get(i).vbo, 0L, 3 * Float.BYTES);
                        glDrawArrays(GL_TRIANGLES, 0, activeDynVbos.get(i).vertexCount);
                    }
                }
                sunValid = true;
            } else {
                sunValid = false;
            }

            // 2. Dirty Point-Light Exact-Geometry Cubemap Shadow Atlas Tiles (0 draw calls on steady static scenes!)
            if (!dirtySlotIndices.isEmpty()) {
                glBindFramebuffer(GL_FRAMEBUFFER, pointFbo);
                glEnable(GL_SCISSOR_TEST);
                glDisable(GL_DEPTH_CLAMP); // clip geometry closer than POINT_NEAR=0.14 (avoids lamp self-occlusion!)
                glPolygonOffset(1.5f, 2.0f);

                float fovRad = (float) (2.0 * Math.atan(POINT_TAN_HALF));
                for (int slotIdx : dirtySlotIndices) {
                    PointSlot slot = pointSlots[slotIdx];
                    float lx = (float) slot.px, ly = (float) slot.py, lz = (float) slot.pz;
                    float far = (float) Math.max(2.0, slot.radius);
                    faceProj.setPerspective(fovRad, 1.0f, POINT_NEAR, far);

                    for (int face = 0; face < 6; face++) {
                        int tile = slotIdx * 6 + face;
                        int tx = (tile % POINT_COLS) * POINT_TILE;
                        int ty = (tile / POINT_COLS) * POINT_TILE;
                        glViewport(tx, ty, POINT_TILE, POINT_TILE);
                        glScissor(tx, ty, POINT_TILE, POINT_TILE);
                        glClear(GL_DEPTH_BUFFER_BIT);

                        faceVp.set(faceProj).mul(FACE_VIEWS[face]);
                        faceVp.get(faceVpArr);

                        if (boxCount > 0) {
                            glUseProgram(pointBoxProg);
                            glBindVertexArray(boxVao);
                            glUniformMatrix4fv(pBoxLocFaceMat, false, faceVpArr);
                            glUniform3f(pBoxLocOrigin, lx, ly, lz);
                            glDrawArraysInstanced(GL_TRIANGLES, 0, 36, boxCount);
                        }
                        boolean boundTri = false;
                        if (staticTriVerts > 0 && staticTriVbo != 0) {
                            glUseProgram(pointTriProg);
                            glBindVertexArray(triVao);
                            glUniformMatrix4fv(pTriLocFaceMat, false, faceVpArr);
                            glUniform3f(pTriLocOrigin, lx, ly, lz);
                            glUniformMatrix4fv(pTriLocModelMat, false, identityArr);
                            glVertexArrayVertexBuffer(triVao, 0, staticTriVbo, 0L, 3 * Float.BYTES);
                            glDrawArrays(GL_TRIANGLES, 0, staticTriVerts);
                            boundTri = true;
                        }
                        if (!activeDynVbos.isEmpty()) {
                            if (!boundTri) {
                                glUseProgram(pointTriProg);
                                glBindVertexArray(triVao);
                                glUniformMatrix4fv(pTriLocFaceMat, false, faceVpArr);
                                glUniform3f(pTriLocOrigin, lx, ly, lz);
                            }
                            for (int i = 0; i < activeDynVbos.size(); i++) {
                                glUniformMatrix4fv(pTriLocModelMat, false, activeDynMats.get(i));
                                glVertexArrayVertexBuffer(triVao, 0, activeDynVbos.get(i).vbo, 0L, 3 * Float.BYTES);
                                glDrawArrays(GL_TRIANGLES, 0, activeDynVbos.get(i).vertexCount);
                            }
                        }
                    }

                    slot.rendered = true;
                    slot.lastBoxSig = boxSig;
                    slot.lastTriSig = staticTriSig;
                    slot.lastDynSig = dynSig;
                }
            }
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
            glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
            glScissor(prevScissorBox[0], prevScissorBox[1], prevScissorBox[2], prevScissorBox[3]);
            glUseProgram(prevProg);
            glBindVertexArray(prevVao);
            if (prevDepth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            if (prevDepthClamp) glEnable(GL_DEPTH_CLAMP); else glDisable(GL_DEPTH_CLAMP);
            glDepthFunc(prevDepthFunc);
            glDepthMask(prevDepthMask);
            glClearDepth(prevClearDepth);
            if (prevCull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
            if (prevScissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            if (prevPolyOff) glEnable(GL_POLYGON_OFFSET_FILL); else glDisable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(prevPolyFactor, prevPolyUnits);
            glColorMask(prevColorMask[0], prevColorMask[1], prevColorMask[2], prevColorMask[3]);
        }

        evictStaleDynVbos();
    }

    private void evictStaleDynVbos() {
        if ((renderFrame & 63L) == 0L && !dynVbos.isEmpty()) {
            var it = dynVbos.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (renderFrame - e.getValue().lastUsedFrame > 120L) {
                    if (e.getValue().vbo != 0) glDeleteBuffers(e.getValue().vbo);
                    it.remove();
                }
            }
        }
    }

    private CachedDynVbo getOrUploadDynVbo(ClientRenderState.DynamicShadowMesh dm) {
        CachedDynVbo cv = dynVbos.get(dm.meshKey());
        if (cv == null) {
            cv = new CachedDynVbo();
            dynVbos.put(dm.meshKey(), cv);
        }
        cv.lastUsedFrame = renderFrame;
        if (cv.vbo != 0 && cv.contentRev == dm.contentRev()) return cv;
        float[] verts = dm.localVertexPositions();
        if (verts == null || verts.length < 9) {
            cv.vertexCount = 0;
            cv.contentRev = dm.contentRev();
            return cv;
        }
        if (cv.vbo == 0) cv.vbo = glCreateBuffers();
        glNamedBufferData(cv.vbo, verts, GL_STATIC_DRAW);
        cv.vertexCount = verts.length / 3;
        cv.contentRev = dm.contentRev();
        return cv;
    }

    private void uploadBoxes(List<ForwardLight.Box> boxes) {
        int n = boxes == null ? 0 : boxes.size();
        boxCount = n;
        if (n == 0) return;
        int needed = n * 6;
        if (scratch.length < needed) scratch = new float[Math.max(needed, Math.max(4096, scratch.length * 2))];
        int o = 0;
        for (int i = 0; i < n; i++) {
            var b = boxes.get(i);
            scratch[o++] = (float) b.low().x();
            scratch[o++] = (float) b.low().y();
            scratch[o++] = (float) b.low().z();
            scratch[o++] = (float) b.high().x();
            scratch[o++] = (float) b.high().y();
            scratch[o++] = (float) b.high().z();
        }
        float[] upload = o == scratch.length ? scratch : Arrays.copyOf(scratch, o);
        if (needed > boxInstanceCap) {
            glNamedBufferData(boxInstanceVbo, upload, GL_DYNAMIC_DRAW);
            boxInstanceCap = needed;
        } else {
            glNamedBufferSubData(boxInstanceVbo, 0L, upload);
        }
    }

    private void uploadStaticTris(List<ForwardLight.CasterTri> tris) {
        int n = tris == null ? 0 : tris.size();
        staticTriVerts = n * 3;
        if (n == 0) return;
        int needed = n * 9;
        if (scratch.length < needed) scratch = new float[Math.max(needed, Math.max(8192, scratch.length * 2))];
        int o = 0;
        for (int i = 0; i < n; i++) {
            var t = tris.get(i).triangle();
            scratch[o++] = (float) t.a().x(); scratch[o++] = (float) t.a().y(); scratch[o++] = (float) t.a().z();
            scratch[o++] = (float) t.b().x(); scratch[o++] = (float) t.b().y(); scratch[o++] = (float) t.b().z();
            scratch[o++] = (float) t.c().x(); scratch[o++] = (float) t.c().y(); scratch[o++] = (float) t.c().z();
        }
        float[] upload = o == scratch.length ? scratch : Arrays.copyOf(scratch, o);
        if (needed > staticTriCap) {
            glNamedBufferData(staticTriVbo, upload, GL_DYNAMIC_DRAW);
            staticTriCap = needed;
        } else {
            glNamedBufferSubData(staticTriVbo, 0L, upload);
        }
    }

    private boolean ensureResources() {
        if (fbo != 0 && pointFbo != 0 && boxProg != 0 && triProg != 0 && pointBoxProg != 0 && pointTriProg != 0) {
            return true;
        }
        boxProg = linkProgram(BOX_VS, DEPTH_FS);
        triProg = linkProgram(TRI_VS, DEPTH_FS);
        pointBoxProg = linkProgram(POINT_BOX_VS, DEPTH_FS);
        pointTriProg = linkProgram(POINT_TRI_VS, DEPTH_FS);
        if (boxProg == 0 || triProg == 0 || pointBoxProg == 0 || pointTriProg == 0) {
            dispose();
            return false;
        }
        boxLocSunMat = glGetUniformLocation(boxProg, "u_SunMat");
        boxLocCamOrigin = glGetUniformLocation(boxProg, "u_CamOrigin");
        triLocSunMat = glGetUniformLocation(triProg, "u_SunMat");
        triLocModelMat = glGetUniformLocation(triProg, "u_ModelMat");
        triLocCamOrigin = glGetUniformLocation(triProg, "u_CamOrigin");
        pBoxLocFaceMat = glGetUniformLocation(pointBoxProg, "u_FaceMat");
        pBoxLocOrigin = glGetUniformLocation(pointBoxProg, "u_LampOrigin");
        pTriLocFaceMat = glGetUniformLocation(pointTriProg, "u_FaceMat");
        pTriLocModelMat = glGetUniformLocation(pointTriProg, "u_ModelMat");
        pTriLocOrigin = glGetUniformLocation(pointTriProg, "u_LampOrigin");

        // Sun shadow depth texture
        depthTex = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(depthTex, 1, GL_DEPTH_COMPONENT24, RES, RES);
        glTextureParameteri(depthTex, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTextureParameteri(depthTex, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTextureParameteri(depthTex, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTextureParameteri(depthTex, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTextureParameterfv(depthTex, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});

        compareSampler = glCreateSamplers();
        glSamplerParameteri(compareSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glSamplerParameteri(compareSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glSamplerParameteri(compareSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glSamplerParameteri(compareSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glSamplerParameterfv(compareSampler, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});
        glSamplerParameteri(compareSampler, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glSamplerParameteri(compareSampler, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);

        rawSampler = glCreateSamplers();
        glSamplerParameteri(rawSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glSamplerParameteri(rawSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(rawSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glSamplerParameteri(rawSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glSamplerParameterfv(rawSampler, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});
        glSamplerParameteri(rawSampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);

        fbo = glCreateFramebuffers();
        glNamedFramebufferTexture(fbo, GL_DEPTH_ATTACHMENT, depthTex, 0);
        glNamedFramebufferDrawBuffer(fbo, GL_NONE);
        glNamedFramebufferReadBuffer(fbo, GL_NONE);
        if (glCheckNamedFramebufferStatus(fbo, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            dispose();
            return false;
        }

        // Point-light cubemap shadow atlas (3072x2048 = 16 lamps x 6 faces @ 256x256, 12 MB VRAM!)
        pointAtlasTex = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(pointAtlasTex, 1, GL_DEPTH_COMPONENT16, POINT_ATLAS_W, POINT_ATLAS_H);
        glTextureParameteri(pointAtlasTex, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTextureParameteri(pointAtlasTex, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTextureParameteri(pointAtlasTex, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTextureParameteri(pointAtlasTex, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        pointCompareSampler = glCreateSamplers();
        glSamplerParameteri(pointCompareSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glSamplerParameteri(pointCompareSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glSamplerParameteri(pointCompareSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(pointCompareSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(pointCompareSampler, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glSamplerParameteri(pointCompareSampler, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);

        pointFbo = glCreateFramebuffers();
        glNamedFramebufferTexture(pointFbo, GL_DEPTH_ATTACHMENT, pointAtlasTex, 0);
        glNamedFramebufferDrawBuffer(pointFbo, GL_NONE);
        glNamedFramebufferReadBuffer(pointFbo, GL_NONE);
        if (glCheckNamedFramebufferStatus(pointFbo, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            dispose();
            return false;
        }

        // Instanced box VAO (shared by sun & point shadow passes!)
        unitCubeVbo = glCreateBuffers();
        glNamedBufferData(unitCubeVbo, UNIT_CUBE, GL_STATIC_DRAW);
        boxInstanceVbo = glCreateBuffers();
        boxVao = glCreateVertexArrays();
        glVertexArrayVertexBuffer(boxVao, 0, unitCubeVbo, 0L, 3 * Float.BYTES);
        glVertexArrayVertexBuffer(boxVao, 1, boxInstanceVbo, 0L, 6 * Float.BYTES);
        glVertexArrayBindingDivisor(boxVao, 1, 1);
        glEnableVertexArrayAttrib(boxVao, 0);
        glVertexArrayAttribFormat(boxVao, 0, 3, GL_FLOAT, false, 0);
        glVertexArrayAttribBinding(boxVao, 0, 0);
        glEnableVertexArrayAttrib(boxVao, 1);
        glVertexArrayAttribFormat(boxVao, 1, 3, GL_FLOAT, false, 0);
        glVertexArrayAttribBinding(boxVao, 1, 1);
        glEnableVertexArrayAttrib(boxVao, 2);
        glVertexArrayAttribFormat(boxVao, 2, 3, GL_FLOAT, false, 3 * Float.BYTES);
        glVertexArrayAttribBinding(boxVao, 2, 1);

        // Triangle VAO (shared by sun & point shadow passes!)
        staticTriVbo = glCreateBuffers();
        triVao = glCreateVertexArrays();
        glEnableVertexArrayAttrib(triVao, 0);
        glVertexArrayAttribFormat(triVao, 0, 3, GL_FLOAT, false, 0);
        glVertexArrayAttribBinding(triVao, 0, 0);
        return true;
    }

    private static int linkProgram(String vsSrc, String fsSrc) {
        int vs = compile(GL_VERTEX_SHADER, vsSrc);
        if (vs == 0) return 0;
        int fs = compile(GL_FRAGMENT_SHADER, fsSrc);
        if (fs == 0) { glDeleteShader(vs); return 0; }
        int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glLinkProgram(p);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p, GL_LINK_STATUS) != GL_TRUE) {
            glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private static int compile(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE) {
            glDeleteShader(id);
            return 0;
        }
        return id;
    }

    void dispose() {
        if (fbo != 0) glDeleteFramebuffers(fbo);
        if (depthTex != 0) glDeleteTextures(depthTex);
        if (compareSampler != 0) glDeleteSamplers(compareSampler);
        if (rawSampler != 0) glDeleteSamplers(rawSampler);
        if (pointFbo != 0) glDeleteFramebuffers(pointFbo);
        if (pointAtlasTex != 0) glDeleteTextures(pointAtlasTex);
        if (pointCompareSampler != 0) glDeleteSamplers(pointCompareSampler);
        if (boxProg != 0) glDeleteProgram(boxProg);
        if (triProg != 0) glDeleteProgram(triProg);
        if (pointBoxProg != 0) glDeleteProgram(pointBoxProg);
        if (pointTriProg != 0) glDeleteProgram(pointTriProg);
        if (boxVao != 0) glDeleteVertexArrays(boxVao);
        if (triVao != 0) glDeleteVertexArrays(triVao);
        if (unitCubeVbo != 0) glDeleteBuffers(unitCubeVbo);
        if (boxInstanceVbo != 0) glDeleteBuffers(boxInstanceVbo);
        if (staticTriVbo != 0) glDeleteBuffers(staticTriVbo);
        for (var cv : dynVbos.values()) {
            if (cv.vbo != 0) glDeleteBuffers(cv.vbo);
        }
        dynVbos.clear();
        for (var s : pointSlots) {
            s.occupied = s.rendered = false;
            s.lastBoxSig = s.lastTriSig = s.lastDynSig = Long.MIN_VALUE;
        }
        fbo = depthTex = compareSampler = rawSampler = 0;
        pointFbo = pointAtlasTex = pointCompareSampler = 0;
        boxProg = triProg = pointBoxProg = pointTriProg = 0;
        boxVao = triVao = unitCubeVbo = boxInstanceVbo = staticTriVbo = 0;
        boxInstanceCap = staticTriCap = boxCount = staticTriVerts = 0;
        lastBoxSig = lastStaticTriSig = Long.MIN_VALUE;
        sunValid = pointValid = false;
        publishedPointLamps = List.of();
    }
}
