package dev.hexie.renderer.mc;

import dev.hexie.renderer.*;
import java.util.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Platform;
import static org.lwjgl.opengl.GL45C.*;

/**
 * Pure-Geometry GPU Forward Lighting for Sodium terrain, HeXie StaticShell/cut-window geometry, Flywheel trains, and Sable ships.
 * ZERO voxel grids!
 * - Unit 4: 2048x2048 Non-Linear Distorted GPU Sun/Moon Shadow Map (hardware PCF compare sampler) for 160-block PCSS soft shadows
 * - Unit 5: 3072x2048 Exact-Geometry 6-Face Cubemap Point-Light Shadow Atlas (16 shadowed lamps x 6 faces @ 256x256, hardware PCF!)
 * - Unit 7: 2048x2048 Raw Sun Depth Sampler for PCSS Blocker Search
 */
public final class ForwardLight {
    public record Box(Vec low, Vec high) {}
    public static final int TARGET_WORLD = 1, TARGET_GEOMETRY = 2, TARGET_ALL = 3, TARGET_BLOCK_EMITTER = 4, TARGET_CEILING_DOWN = 8;
    public static final int MAX_POINT_LAMPS = 16;

    public record Lamp(Vec position, LightState source, int targetMask) {
        public Lamp(Vec position, LightState source) { this(position, source, TARGET_ALL); }
    }
    public record ShadowedLamp(Lamp lamp, int atlasSlot) {}
    public record Sun(Vec direction, Vec color) {
        public static final Sun OFF = new Sun(Vec.ZERO, Vec.ZERO);
    }
    /** cutoutMode: 0 = solid opaque, 1 = iron-bar cutout (even quad tri), 2 = iron-bar cutout (odd quad tri). */
    public record CasterTri(Triangle triangle, int cutoutMode) {}

    /** Lightweight compatibility wrapper kept for public/internal callers. */
    public static final class BoxTree {
        final List<Box> boxes;
        BoxTree(List<Box> boxes) { this.boxes = boxes; }
        public int boxCount() { return boxes.size(); }
        public List<Box> boxes() { return boxes; }
    }

    /** Lightweight compatibility wrapper kept for public/internal callers. */
    public static final class TriTree {
        final List<CasterTri> tris;
        TriTree(List<CasterTri> tris) { this.tris = tris; }
        public int triangleCount() { return tris.size(); }
    }

    public static final TriTree EMPTY_TRI_TREE = new TriTree(List.of());

    private static final int UNIT_SHADOW_CMP = 4;
    private static final int UNIT_POINT_ATLAS = 5;
    private static final int UNIT_SHADOW_RAW = 7;

    private static boolean active;
    private static int shadowDepthTex, shadowCompareSampler, shadowRawSampler;
    private static int pointAtlasTex, pointCompareSampler;
    private static float[] sunMatrix = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    private static Vec shadowOrigin = Vec.ZERO;
    private static Sun sun = Sun.OFF;
    private static List<ShadowedLamp> activePointLamps = List.of();
    private static List<Lamp> fallbackEntityLamps = List.of();
    private static final float[] LAMP_POS_BUF = new float[MAX_POINT_LAMPS * 4];
    private static final float[] LAMP_COL_BUF = new float[MAX_POINT_LAMPS * 4];
    private static int reportedLights, reportedFaces;

    private record ProgramLayout(
            int enabled,
            int sunDir,
            int sunColor,
            int sunMat,
            int shadowCamOff,
            int hasShadow,
            int hasPointAtlas,
            int rotation,
            int hasNormal,
            int shadowMap,
            int shadowDepth,
            int pointAtlas,
            int lampCount,
            int lampPos,
            int lampCol
    ) {}

    private static final ProgramLayout NONE = new ProgramLayout(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1);
    private static final Map<Integer, ProgramLayout> PROGRAM_CACHE = new HashMap<>();

    private ForwardLight() {}

    /** True only when the active GL context can run HeXie's GL 4.5 forward lighting pipeline (never on macOS GL 4.1). */
    public static boolean supportsGpuForward() {
        if (Platform.get() == Platform.MACOSX) return false;
        try {
            return GL.getCapabilities().OpenGL45;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static BoxTree buildBoxTree(List<Box> boxes) {
        return new BoxTree(boxes == null ? List.of() : List.copyOf(boxes));
    }

    public static TriTree buildTriTree(List<CasterTri> triangles) {
        return new TriTree(triangles == null ? List.of() : List.copyOf(triangles));
    }

    public static void clear() {
        active = false;
        shadowDepthTex = shadowCompareSampler = shadowRawSampler = 0;
        pointAtlasTex = pointCompareSampler = 0;
        sun = Sun.OFF;
        activePointLamps = List.of();
        fallbackEntityLamps = List.of();
        reportedLights = reportedFaces = 0;
    }

    public static void dispose() {
        clear();
        invalidatePrograms();
    }

    public static void invalidatePrograms() {
        PROGRAM_CACHE.clear();
    }

    /** Publishes the current frame's Exact-Geometry Sun Shadow Map + Point-Light Cubemap Shadow Atlas. */
    public static void publishGpuLighting(
            Sun daylight,
            int shadowTex, int compareSampler, int rawSampler, float[] sunMat, Vec shadowOrig,
            int ptAtlasTex, int ptCompareSampler, List<ShadowedLamp> shadowedPointLamps,
            List<Lamp> allLampsForEntityFallback, int casterFaceCount) {
        boolean hasSun = daylight != null && !daylight.color().equals(Vec.ZERO) && shadowTex != 0;
        boolean hasPoints = ptAtlasTex != 0 && shadowedPointLamps != null && !shadowedPointLamps.isEmpty();
        if (!hasSun && !hasPoints) {
            clear();
            return;
        }
        active = true;
        sun = daylight != null ? daylight : Sun.OFF;
        shadowDepthTex = hasSun ? shadowTex : 0;
        shadowCompareSampler = compareSampler;
        shadowRawSampler = rawSampler;
        if (sunMat != null && sunMat.length == 16) sunMatrix = sunMat;
        shadowOrigin = shadowOrig != null ? shadowOrig : Vec.ZERO;
        pointAtlasTex = hasPoints ? ptAtlasTex : 0;
        pointCompareSampler = ptCompareSampler;
        activePointLamps = hasPoints ? shadowedPointLamps : List.of();
        fallbackEntityLamps = allLampsForEntityFallback != null ? allLampsForEntityFallback : List.of();
        reportedLights = Math.max(activePointLamps.size(), fallbackEntityLamps.size());
        reportedFaces = casterFaceCount;
    }

    public static int publishedLamps() { return reportedLights; }
    public static int publishedTriangles() { return reportedFaces; }
    public static int usedRays() { return active ? (shadowDepthTex != 0 ? 18 : 0) + activePointLamps.size() * 4 : 0; }

    /** Fast entity block-light query used by EntityLightMixin. */
    public static int blockLight(Vec lo, Vec hi) {
        if (fallbackEntityLamps.isEmpty()) return 0;
        Vec center = lo.plus(hi).times(0.5);
        int best = 0;
        for (var lamp : fallbackEntityLamps) {
            double r = lamp.source().radius();
            Vec d = center.minus(lamp.position());
            double d2 = d.dot(d);
            if (d2 >= r * r) continue;
            int level = (int) Math.round(lamp.source().radius() - Math.sqrt(d2));
            if (level > best) best = level;
            if (best >= 15) return 15;
        }
        return Math.min(15, best);
    }

    public static void draw(Vec camera, float[] rotation, boolean packedNormal, Runnable original) {
        draw(camera, rotation, packedNormal, TARGET_WORLD, original);
    }

    public static void draw(Vec camera, float[] rotation, boolean packedNormal, int receiverMask, Runnable original) {
        long start = receiverMask == TARGET_WORLD ? System.nanoTime() : 0;
        try {
            drawReceiver(camera, rotation, packedNormal, original);
        } finally {
            if (receiverMask == TARGET_WORLD) ClientRenderState.timings.terrainDrawNs += System.nanoTime() - start;
        }
    }

    private static ProgramLayout programMeta(int program) {
        ProgramLayout cached = PROGRAM_CACHE.computeIfAbsent(program, p -> {
            int enabled = glGetUniformLocation(p, "hexieEnabled");
            if (enabled < 0) return NONE;
            return new ProgramLayout(
                    enabled,
                    glGetUniformLocation(p, "hexieSunDir"),
                    glGetUniformLocation(p, "hexieSunColor"),
                    glGetUniformLocation(p, "u_HexieSunMat"),
                    glGetUniformLocation(p, "u_HexieShadowCamOff"),
                    glGetUniformLocation(p, "u_HexieHasShadow"),
                    glGetUniformLocation(p, "u_HexieHasPointAtlas"),
                    glGetUniformLocation(p, "hexieRotation"),
                    glGetUniformLocation(p, "hexieHasNormal"),
                    glGetUniformLocation(p, "u_HexieShadowMap"),
                    glGetUniformLocation(p, "u_HexieShadowDepth"),
                    glGetUniformLocation(p, "u_HexiePointAtlas"),
                    glGetUniformLocation(p, "u_HexieLampCount"),
                    glGetUniformLocation(p, "u_HexieLampPos"),
                    glGetUniformLocation(p, "u_HexieLampCol")
            );
        });
        return cached == NONE ? null : cached;
    }

    /** Moves inactive shadow samplers off unit 0 so sampler2DShadow never collides with sampler2D on unit 0. */
    private static void bindSafeInactiveSamplers() {
        int program = glGetInteger(GL_CURRENT_PROGRAM);
        ProgramLayout meta = program == 0 ? null : programMeta(program);
        if (meta == null) return;
        if (meta.shadowMap >= 0) glUniform1i(meta.shadowMap, UNIT_SHADOW_CMP);
        if (meta.shadowDepth >= 0) glUniform1i(meta.shadowDepth, UNIT_SHADOW_RAW);
        if (meta.pointAtlas >= 0) glUniform1i(meta.pointAtlas, UNIT_POINT_ATLAS);
        if (meta.enabled >= 0) glUniform1i(meta.enabled, 0);
    }

    private static void drawReceiver(Vec camera, float[] rotation, boolean packedNormal, Runnable original) {
        if (!supportsGpuForward()) {
            original.run();
            return;
        }
        if (!active || ClientRenderState.engine().route().tier() != RenderSettings.Tier.FULL) {
            bindSafeInactiveSamplers();
            original.run();
            return;
        }
        int program = glGetInteger(GL_CURRENT_PROGRAM);
        ProgramLayout meta = program == 0 ? null : programMeta(program);
        if (meta == null) {
            original.run();
            return;
        }

        int prevActiveTex = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0 + UNIT_SHADOW_CMP);
        int prevTex4 = glGetInteger(GL_TEXTURE_BINDING_2D);
        int prevSamp4 = glGetInteger(GL_SAMPLER_BINDING);
        glActiveTexture(GL_TEXTURE0 + UNIT_POINT_ATLAS);
        int prevTex5 = glGetInteger(GL_TEXTURE_BINDING_2D);
        int prevSamp5 = glGetInteger(GL_SAMPLER_BINDING);
        glActiveTexture(GL_TEXTURE0 + UNIT_SHADOW_RAW);
        int prevTex7 = glGetInteger(GL_TEXTURE_BINDING_2D);
        int prevSamp7 = glGetInteger(GL_SAMPLER_BINDING);

        try {
            if (shadowDepthTex != 0) {
                glActiveTexture(GL_TEXTURE0 + UNIT_SHADOW_CMP);
                glBindTexture(GL_TEXTURE_2D, shadowDepthTex);
                glBindSampler(UNIT_SHADOW_CMP, shadowCompareSampler);
                glActiveTexture(GL_TEXTURE0 + UNIT_SHADOW_RAW);
                glBindTexture(GL_TEXTURE_2D, shadowDepthTex);
                glBindSampler(UNIT_SHADOW_RAW, shadowRawSampler);
            }
            if (pointAtlasTex != 0) {
                glActiveTexture(GL_TEXTURE0 + UNIT_POINT_ATLAS);
                glBindTexture(GL_TEXTURE_2D, pointAtlasTex);
                glBindSampler(UNIT_POINT_ATLAS, pointCompareSampler);
            }
            glActiveTexture(prevActiveTex);

            if (meta.shadowMap >= 0) glUniform1i(meta.shadowMap, UNIT_SHADOW_CMP);
            if (meta.shadowDepth >= 0) glUniform1i(meta.shadowDepth, UNIT_SHADOW_RAW);
            if (meta.pointAtlas >= 0) glUniform1i(meta.pointAtlas, UNIT_POINT_ATLAS);

            if (meta.sunDir >= 0) setVec(meta.sunDir, sun.direction());
            if (meta.sunColor >= 0) setVec(meta.sunColor, sun.color());
            if (meta.sunMat >= 0) glUniformMatrix4fv(meta.sunMat, false, sunMatrix);
            if (meta.shadowCamOff >= 0) setVec(meta.shadowCamOff, camera.minus(shadowOrigin));
            if (meta.hasShadow >= 0) glUniform1i(meta.hasShadow, shadowDepthTex != 0 ? 1 : 0);
            if (meta.hasPointAtlas >= 0) glUniform1i(meta.hasPointAtlas, pointAtlasTex != 0 ? 1 : 0);

            if (meta.lampCount >= 0) {
                int count = 0;
                if (pointAtlasTex != 0 && !activePointLamps.isEmpty()) {
                    for (int i = 0; i < activePointLamps.size() && count < MAX_POINT_LAMPS; i++) {
                        ShadowedLamp sl = activePointLamps.get(i);
                        Lamp lamp = sl.lamp();
                        var src = lamp.source();
                        if (!src.enabled() || src.intensity() <= 0.0 || src.radius() <= 0.1) continue;
                        double dx = lamp.position().x() - camera.x();
                        double dy = lamp.position().y() - camera.y();
                        double dz = lamp.position().z() - camera.z();
                        float r = (float) Math.max(2.0, src.radius());
                        boolean ceilingDown = (lamp.targetMask() & TARGET_CEILING_DOWN) != 0;
                        int o = count * 4;
                        LAMP_POS_BUF[o] = (float) dx;
                        LAMP_POS_BUF[o + 1] = (float) dy;
                        LAMP_POS_BUF[o + 2] = (float) dz;
                        LAMP_POS_BUF[o + 3] = ceilingDown ? -r : r;
                        float scale = (float) (src.intensity() * 0.95);
                        LAMP_COL_BUF[o] = (float) (src.linearRgb().x() * scale);
                        LAMP_COL_BUF[o + 1] = (float) (src.linearRgb().y() * scale);
                        LAMP_COL_BUF[o + 2] = (float) (src.linearRgb().z() * scale);
                        LAMP_COL_BUF[o + 3] = (float) sl.atlasSlot();
                        count++;
                    }
                }
                glUniform1i(meta.lampCount, count);
                if (count > 0) {
                    if (meta.lampPos >= 0) glUniform4fv(meta.lampPos, count == MAX_POINT_LAMPS ? LAMP_POS_BUF : Arrays.copyOf(LAMP_POS_BUF, count * 4));
                    if (meta.lampCol >= 0) glUniform4fv(meta.lampCol, count == MAX_POINT_LAMPS ? LAMP_COL_BUF : Arrays.copyOf(LAMP_COL_BUF, count * 4));
                }
            }

            if (meta.rotation >= 0) glUniformMatrix3fv(meta.rotation, false, rotation);
            if (meta.hasNormal >= 0) glUniform1i(meta.hasNormal, packedNormal ? 1 : 0);
            glUniform1i(meta.enabled, 1);

            original.run();
        } finally {
            glProgramUniform1i(program, meta.enabled, 0);
            glActiveTexture(GL_TEXTURE0 + UNIT_SHADOW_CMP);
            glBindTexture(GL_TEXTURE_2D, prevTex4);
            glBindSampler(UNIT_SHADOW_CMP, prevSamp4);
            glActiveTexture(GL_TEXTURE0 + UNIT_POINT_ATLAS);
            glBindTexture(GL_TEXTURE_2D, prevTex5);
            glBindSampler(UNIT_POINT_ATLAS, prevSamp5);
            glActiveTexture(GL_TEXTURE0 + UNIT_SHADOW_RAW);
            glBindTexture(GL_TEXTURE_2D, prevTex7);
            glBindSampler(UNIT_SHADOW_RAW, prevSamp7);
            glActiveTexture(prevActiveTex);
        }
    }

    private static void setVec(int location, Vec v) {
        glUniform3f(location, (float) v.x(), (float) v.y(), (float) v.z());
    }

    /**
     * Pure-Geometry Forward Shading GLSL snippet (GLSL 330+ compatible, ZERO voxel grids!):
     * - 160-block Distorted PCSS Sun/Moon Soft Shadows (6-tap Vogel blocker search + 12-tap hardware PCF)
     * - Exact-Geometry 6-Face Cubemap Point-Light Shadow Atlas (16 shadowed lamps x 256x256/face, 4-tap rotated hardware PCF)
     * - Sky/Ground Hemisphere Ambient + Dielectric Fresnel Specular + Linear RGB with smooth highlight roll-off!
     */
    public static final String FRAGMENT = """
        uniform int hexieEnabled;
        uniform int u_HexieHasShadow;
        uniform int u_HexieHasPointAtlas;
        uniform vec3 hexieSunDir, hexieSunColor;
        uniform mat4 u_HexieSunMat;
        uniform vec3 u_HexieShadowCamOff;
        uniform mat3 hexieRotation;
        uniform sampler2DShadow u_HexieShadowMap;
        uniform sampler2D u_HexieShadowDepth;
        uniform sampler2DShadow u_HexiePointAtlas;
        uniform int u_HexieLampCount;
        uniform vec4 u_HexieLampPos[16];
        uniform vec4 u_HexieLampCol[16];

        vec3 hexieToLinear(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(2.2));
        }

        vec3 hexieToSrgb(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
        }

        vec3 hexieSoftHighlight(vec3 lin) {
            vec3 excess = max(lin - vec3(0.78), vec3(0.0));
            return lin - excess * (excess / (excess + vec3(1.25)));
        }

        float hexieIgn(vec2 px) {
            return fract(52.9829189 * fract(dot(px, vec2(0.06711056, 0.00583715))));
        }

        vec2 hexieVogelDisk(int idx, int total, float phiOffset) {
            float r = sqrt((float(idx) + 0.5) / float(total));
            float theta = float(idx) * 2.39996323 + phiOffset;
            return vec2(cos(theta), sin(theta)) * r;
        }

        // Exact-Geometry 6-Face Cubemap Point-Light Shadow Atlas (3072x2048 = 12x8 tiles of 256x256, 4-tap rotated hardware PCF)
        float hexieSamplePointShadow(vec3 d, float farPlane, int slot, float phi) {
            if (u_HexieHasPointAtlas == 0) return 1.0;
            vec3 ad = abs(d);
            int face;
            float u, v, zAxis;
            if (ad.x >= ad.y && ad.x >= ad.z) {
                if (d.x >= 0.0) { face = 0; u = -d.z / d.x;  v = -d.y / d.x;  zAxis = d.x; }
                else            { face = 1; u =  d.z / -d.x; v = -d.y / -d.x; zAxis = -d.x; }
            } else if (ad.y >= ad.z) {
                if (d.y >= 0.0) { face = 2; u =  d.x / d.y;  v =  d.z / d.y;  zAxis = d.y; }
                else            { face = 3; u =  d.x / -d.y; v = -d.z / -d.y; zAxis = -d.y; }
            } else {
                if (d.z >= 0.0) { face = 4; u =  d.x / d.z;  v = -d.y / d.z;  zAxis = d.z; }
                else            { face = 5; u = -d.x / -d.z; v = -d.y / -d.z; zAxis = -d.z; }
            }
            if (zAxis <= 0.145) return 1.0;

            const float NEAR = 0.14;
            const float INV_TAN_HALF = 1.0 / 1.015625;
            vec2 tileUv = clamp(vec2(u, v) * (0.5 * INV_TAN_HALF) + 0.5, vec2(0.008), vec2(0.992));

            float ndcZ = (farPlane + NEAR) / (farPlane - NEAR) - (2.0 * farPlane * NEAR) / ((farPlane - NEAR) * zAxis);
            float refDepth = clamp(ndcZ * 0.5 + 0.5, 0.0, 0.9999);

            int tile = slot * 6 + face;
            float col = float(tile % 12);
            float row = float(tile / 12);
            vec2 tileScale = vec2(1.0 / 12.0, 1.0 / 8.0);
            vec2 tileMin = (vec2(col, row) + vec2(0.006)) * tileScale;
            vec2 tileMax = (vec2(col, row) + vec2(0.994)) * tileScale;
            vec2 baseUv = (vec2(col, row) + tileUv) * tileScale;

            // Soft 4-tap rotated hardware PCF (16 hardware bilinear comparisons, clamped strictly within tile!)
            vec2 pcfRad = vec2(1.5 / 3072.0, 1.5 / 2048.0);
            float vis = 0.0;
            for (int i = 0; i < 4; i++) {
                vec2 uv = clamp(baseUv + hexieVogelDisk(i, 4, phi) * pcfRad, tileMin, tileMax);
                vis += texture(u_HexiePointAtlas, vec3(uv, refDepth));
            }
            return vis * 0.25;
        }

        // Exact-Geometry Shadowed Local Point & Ceiling Lamps (ZERO voxel grid, ZERO thin-wall bleed!)
        vec3 hexiePointLights(vec3 fragPos, vec3 nn, float specStrength, float phi) {
            if (u_HexieLampCount <= 0) return vec3(0.0);
            vec3 total = vec3(0.0);
            vec3 viewDir = normalize(-fragPos);
            int n = min(u_HexieLampCount, 16);
            for (int i = 0; i < n; i++) {
                vec4 lp = u_HexieLampPos[i];
                float signedR = lp.w;
                float r = abs(signedR);
                vec3 toLamp = lp.xyz - fragPos;
                float d2 = dot(toLamp, toLamp);
                if (d2 >= r * r || d2 < 1e-6) continue;
                // Downward-only ceiling lamps never shine upward through carriage roofs!
                if (signedR < 0.0 && toLamp.y < -0.15) continue;

                float invD = inversesqrt(d2);
                vec3 L = toLamp * invD;
                float rawNdl = dot(nn, L);
                // Strictly reject back-facing surfaces (e.g. exterior of CRH2 shell facing away from cabin lamp!)
                if (rawNdl <= -0.18) continue;

                float dist = d2 * invD;
                float ndl = max(0.0, rawNdl);
                // Constant world-space normal/light bias (2.5 cm) -> zero acne & zero peter-panning at any distance!
                float nBias = 0.024 + 0.022 * (1.0 - ndl);
                vec3 biasedPos = fragPos + nn * nBias + L * 0.016;
                int slot = int(u_HexieLampCol[i].w + 0.5);
                float vis = hexieSamplePointShadow(biasedPos - lp.xyz, r, slot, phi + float(i) * 1.17);
                if (vis <= 1e-4) continue;

                float falloff = 1.0 - dist / r;
                float atten = (falloff * falloff) / (0.45 + 0.24 * d2);
                if (signedR < 0.0) {
                    atten *= smoothstep(-0.35, 0.20, L.y);
                }

                // Crisp direct lighting + subtle shadow-gated cabin wrap fill (illuminates grazing seat sides without ever leaking outside!)
                float wrapFill = max(0.0, rawNdl * 0.45 + 0.55) * 0.16;
                vec3 lampRad = u_HexieLampCol[i].rgb * (atten * vis);
                total += lampRad * (ndl + wrapFill);

                if (specStrength > 0.001 && ndl > 0.0) {
                    vec3 halfVec = normalize(L + viewDir);
                    float ndh = max(0.0, dot(nn, halfVec));
                    float vdh = max(0.0, dot(viewDir, halfVec));
                    float fresnel = 0.04 + 0.96 * pow(1.0 - vdh, 5.0);
                    total += lampRad * (pow(ndh, 28.0) * fresnel * specStrength * 0.55 * ndl);
                }
            }
            return total;
        }

        // 160-block Non-Linear Distorted PCSS Soft Sun Shadow (6-tap Vogel blocker search + 12-tap Hardware PCF)
        float hexieSampleSunShadow(vec3 p, vec3 nn, float ndl, float phi) {
            if (u_HexieHasShadow == 0) return 1.0;
            vec3 fragPos = hexieRotation * p;
            float dist = length(fragPos);
            float distFade = 1.0 - smoothstep(132.0, 156.0, dist);
            if (distFade <= 1e-4) return 1.0;

            float slopeBias = 0.024 + 0.032 * (1.0 - ndl);
            vec3 shadowRel = fragPos + u_HexieShadowCamOff + nn * slopeBias + hexieSunDir * 0.018;
            vec4 clip = u_HexieSunMat * vec4(shadowRel, 1.0);
            float rLen = length(clip.xy);
            float distort = mix(1.0, rLen, 0.72);
            vec2 distortedXY = clip.xy / max(distort, 1e-4);
            float zDepth = clip.z * 0.5;
            vec3 sc = vec3(distortedXY * 0.5 + 0.5, zDepth * 0.5 + 0.5);
            if (any(lessThan(sc.xy, vec2(0.002))) || any(greaterThan(sc.xy, vec2(0.998))) || sc.z <= 0.0 || sc.z >= 1.0) {
                return 1.0;
            }

            float invDistort = 1.0 / max(distort, 0.28);
            float searchRadius = 0.0042 * invDistort;
            float blockerSum = 0.0;
            float blockerCount = 0.0;
            for (int i = 0; i < 6; i++) {
                vec2 off = hexieVogelDisk(i, 6, phi) * searchRadius;
                float d = textureLod(u_HexieShadowDepth, sc.xy + off, 0.0).r;
                if (d < sc.z - 0.00018) {
                    blockerSum += (sc.z - d);
                    blockerCount += 1.0;
                }
            }
            if (blockerCount < 0.5) return 1.0;

            float avgBlocker = blockerSum / blockerCount;
            float penumbra = clamp(avgBlocker * 0.14, 0.00045, 0.0040) * invDistort;

            float vis = 0.0;
            for (int i = 0; i < 12; i++) {
                vec2 off = hexieVogelDisk(i, 12, phi + 1.047) * penumbra;
                vis += texture(u_HexieShadowMap, vec3(sc.xy + off, sc.z - 0.00015));
            }
            vis *= (1.0 / 12.0);
            return mix(1.0, vis, distFade);
        }

        // Legacy-compatible hooks (used by Sable ships & simple receivers)
        float hexieRoofShade(vec3 p, vec3 nrm) { return 1.0; }

        vec3 hexieDirect(vec3 p, vec3 albedo, vec3 nrm) {
            if (hexieEnabled == 0 || u_HexieLampCount <= 0) return vec3(0.0);
            float nLen2 = dot(nrm, nrm);
            if (!(nLen2 > 1e-12)) return vec3(0.0);
            vec3 nn = nrm * inversesqrt(nLen2);
            vec3 fragPos = hexieRotation * p;
            if (dot(nn, -fragPos) < 0.0) nn = -nn;
            float phi = hexieIgn(gl_FragCoord.xy) * 6.2831853;
            vec3 linAlb = hexieToLinear(albedo);
            return hexieToSrgb(hexieSoftHighlight(linAlb * hexiePointLights(fragPos, nn, 0.0, phi)));
        }

        vec3 hexieDaylight(vec3 p, vec3 albedo, vec3 nrm, float sky) {
            if (hexieEnabled == 0 || sky <= 0.0 || !any(greaterThan(hexieSunColor, vec3(0.0)))) return vec3(0.0);
            float nLen2 = dot(nrm, nrm);
            if (!(nLen2 > 1e-12)) return vec3(0.0);
            vec3 nn = nrm * inversesqrt(nLen2);
            float ndl = dot(nn, hexieSunDir);
            if (ndl <= 0.0) return vec3(0.0);
            float phi = hexieIgn(gl_FragCoord.xy) * 6.2831853;
            float sunVis = hexieSampleSunShadow(p, nn, ndl, phi);
            if (sunVis <= 1e-4) return vec3(0.0);
            vec3 linAlb = hexieToLinear(albedo);
            return hexieToSrgb(hexieSoftHighlight(linAlb * hexieSunColor * (ndl * sunVis * sky)));
        }

        /**
         * Unified Pure-Geometry Linear PBR Shading (ZERO voxel grids!):
         * - Sky/Ground Hemisphere Ambient (cool blue sky + warm sunlit ground bounce)
         * - 160-block Distorted PCSS Sun/Moon Soft Shadows + Fresnel Specular
         * - Exact-Geometry 6-Face Cubemap Shadowed Local Point & Ceiling Lamps
         */
        vec3 hexieShadeSurface(vec3 p, vec3 albedo, vec3 nrm, vec3 vanillaLightRgb, float sky, float specStrength) {
            float nLen2 = dot(nrm, nrm);
            vec3 nn = nLen2 > 1e-12 ? nrm * inversesqrt(nLen2) : vec3(0.0, 1.0, 0.0);
            vec3 fragPos = hexieRotation * p;
            if (dot(nn, -fragPos) < 0.0) nn = -nn;
            if (hexieEnabled == 0) {
                // Disabled / vanilla-HeXie lighting path: pure continuous lightmap sampling with zero extra directional shading!
                return albedo * vanillaLightRgb;
            }
            float directionalShade = 0.72 + 0.28 * max(-0.5, nn.y);

            vec3 linAlb = hexieToLinear(albedo);
            vec3 linBaseLight = hexieToLinear(vanillaLightRgb);
            float phi = hexieIgn(gl_FragCoord.xy) * 6.2831853;

            // 1. Physically balanced Sky / Ground Hemisphere Ambient (zero voxels!)
            float dist = length(fragPos);
            float shadowFade = u_HexieHasShadow != 0 ? (1.0 - smoothstep(132.0, 156.0, dist)) : 0.0;
            bool hasSun = any(greaterThan(hexieSunColor, vec3(0.0)));
            bool isDaySun = hexieSunColor.r > 0.35;
            vec3 ambientLight = linBaseLight * directionalShade;
            if (hasSun && shadowFade > 1e-4 && sky > 0.01) {
                vec3 skyTint = isDaySun ? vec3(0.62, 0.78, 1.12) : vec3(0.72, 0.84, 1.14);
                vec3 groundBounce = isDaySun ? vec3(0.36, 0.34, 0.27) : vec3(0.04, 0.05, 0.07);
                float skyScale = isDaySun ? 0.23 : 0.52;
                float upFactor = smoothstep(-0.45, 0.85, nn.y);
                vec3 hemiAmb = linBaseLight * mix(groundBounce, skyTint, upFactor) * skyScale;
                ambientLight = mix(ambientLight, hemiAmb, shadowFade * sky);
            }
            vec3 linColor = linAlb * ambientLight;

            // 2. Exact-Geometry Cubemap-Shadowed Local Point & Ceiling Lamps!
            linColor += linAlb * hexiePointLights(fragPos, nn, specStrength, phi);

            // 3. Direct Sunlight / Moonlight with 160-block Distorted PCSS Soft Shadows + Fresnel Specular!
            if (hasSun && sky > 0.01) {
                float ndl = max(0.0, dot(nn, hexieSunDir));
                if (ndl > 0.0) {
                    float sunVis = hexieSampleSunShadow(p, nn, ndl, phi);
                    if (sunVis > 1e-4) {
                        vec3 sunRad = hexieSunColor * (sunVis * sky);
                        linColor += linAlb * sunRad * ndl;

                        if (specStrength > 0.001) {
                            vec3 viewDir = normalize(-fragPos);
                            vec3 halfVec = normalize(hexieSunDir + viewDir);
                            float ndh = max(0.0, dot(nn, halfVec));
                            float vdh = max(0.0, dot(viewDir, halfVec));
                            float fresnel = 0.04 + 0.96 * pow(1.0 - vdh, 5.0);
                            float spec = pow(ndh, 32.0) * fresnel * specStrength * ndl;
                            linColor += sunRad * spec;
                        }
                    }
                }
            }

            return hexieToSrgb(hexieSoftHighlight(linColor));
        }
        """;

    public static String fragment(String source, String before, String albedo, String normal) {
        int version = source.indexOf('\n');
        if (version < 0 || !source.contains(before) || !source.contains("void main()")) return source;
        return source
                .replace("void main()", "in vec3 hexiePos, hexieTint;\n" + FRAGMENT + "\nvoid main()")
                .replace(before, "\nvec3 hexieAlb=" + albedo + "; vec3 hexieNrm=" + normal + ";\n"
                        + "color.rgb += hexieDirect(hexiePos, hexieAlb, hexieNrm);\n"
                        + "color.rgb += hexieDaylight(hexiePos, hexieAlb, hexieNrm, 1.0);\n" + before);
    }
}
