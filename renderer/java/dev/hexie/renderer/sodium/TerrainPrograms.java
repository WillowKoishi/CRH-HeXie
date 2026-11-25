package dev.hexie.renderer.sodium;

import dev.hexie.renderer.mc.ForwardLight;
import java.util.regex.*;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;
import static org.lwjgl.opengl.GL11C.GL_BYTE;

public final class TerrainPrograms {
    public static boolean veilNormals(GlVertexFormat format) {
        if (format.getStride()!=24) return false;
        for (var input:format.getShaderBindings()) {
            if (input.getIndex()==4 && input.getPointer()==20 && input.getCount()==4
                    && input.isIntType() && input.getFormat()==GL_BYTE) return true;
        }
        return false;
    }
    // Veil recompiles the same shader with its own parser. Patch the final shader source,
    // after that parser has chosen attribute declarations and MRT outputs.
    public static String patch(String source,boolean vertex) {
        String main="void main()";
        source=source.replaceFirst("void\\s+main\\s*\\(\\s*(?:void)?\\s*\\)",main);
        if (!source.contains(main)) return source;
        if (vertex) {
            boolean declared=source.contains("a_VeilNormal");
            String attribute=declared?"a_VeilNormal":"hexieNormalBytes";
            String declarations=(declared?"":"layout(location=4) in ivec4 "+attribute+";\n")
                    +"out vec3 hexiePos, hexieTint, hexiePacked; out float hexieSky; uniform int hexieHasNormal, hexieEnabled;\n";
            String init="hexiePos=vec3(0); hexieTint=vec3(0); hexiePacked=vec3(0); hexieSky=0.0;";
            source=source.replaceFirst("void main\\(\\)\\s*\\{",Matcher.quoteReplacement(declarations+main+" {\n"+init));
            if (source.matches("(?s).*\\bvec3\\s+position\\b.*") && source.contains("_vert_color")) {
                source=source.replaceFirst("gl_Position\\s*=",Matcher.quoteReplacement(
                        "hexiePos=position; hexieTint=_vert_color.rgb; if(hexieHasNormal!=0) hexiePacked=vec3(ivec3("
                        +attribute+".xyz))/127.0;\ngl_Position="));
                if (source.contains("_vert_tex_light_coord")) {
                    source=source.replace("texture(u_LightTex, _vert_tex_light_coord)",
                            "texture(u_LightTex, hexieEnabled!=0 ? vec2((clamp((_vert_tex_light_coord.x*16.0-0.5)/15.0,0.0,1.0)*mix(1.0,0.12,smoothstep(32.0,20.0,length(position)))*15.0+0.5)/16.0, _vert_tex_light_coord.y) : _vert_tex_light_coord)");
                    source=source.replace("hexieTint=_vert_color.rgb;",
                            "hexieTint=_vert_color.rgb; hexieSky=clamp((_vert_tex_light_coord.y*16.0-0.5)/15.0,0.0,1.0);");
                }
            }
            return source;
        }
        Matcher fog=Pattern.compile("fragColor\\s*=\\s*_linearFog\\s*\\(").matcher(source);
        if (!fog.find()) return source;
        String before=fog.group();
        String normalExpr="dot(hexiePacked,hexiePacked)>0.1 ? hexiePacked : cross(dFdx(hexiePos),dFdy(hexiePos))";
        String albedoExpr="texture(u_BlockTex,v_TexCoord,lodBias).rgb*hexieTint";
        int version=source.indexOf('\n');
        if (version < 0 || !source.contains("void main()")) return source;
        return (source.substring(0, version + 1) + "#ifdef GL_ARB_shader_storage_buffer_object\n"
                + "#extension GL_ARB_shader_storage_buffer_object : enable\n#endif\n" + source.substring(version + 1))
                .replace("void main()", "in vec3 hexiePos, hexieTint, hexiePacked; in float hexieSky;\n" + ForwardLight.FRAGMENT + "\nvoid main()")
                .replace(before, "\n#if defined(GL_ARB_shader_storage_buffer_object) || (__VERSION__ >= 430)\n"
                        + "vec3 hexieAlb=" + albedoExpr + "; vec3 hexieNrm=" + normalExpr + ";\n"
                        + "color.rgb += hexieDirect(hexiePos, hexieAlb, hexieNrm);\n"
                        + "color.rgb += hexieDaylight(hexiePos, hexieAlb, hexieNrm, hexieSky);\n#endif\n" + before);
    }
}
