package dev.hexie.renderer.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.hexie.renderer.mc.ClientRenderState;
import java.util.Arrays;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL40C.*;

/** Capture the destination chosen by the standard RenderType immediately before its last
 * wrapper apply. RenderTypes belonging to other producers never enter this scope. */
public final class PassTarget {
    private static boolean standard;
    public static void standard(Runnable draw) {
        boolean previous=standard;
        standard=true;
        try { draw.run(); } finally { standard=previous; }
    }
    public static void wrapper(Runnable apply) {
        if (!standard) { apply.run(); return; }
        int[] expected=capture();
        try { apply.run(); } finally {
            if (!Arrays.equals(expected,capture())) {
                ClientRenderState.wrapperMismatches++;
                restore(expected);
            }
        }
    }
    private static final int[] BLEND={GL_BLEND_EQUATION_RGB,GL_BLEND_EQUATION_ALPHA,GL_BLEND_SRC_RGB,
            GL_BLEND_DST_RGB,GL_BLEND_SRC_ALPHA,GL_BLEND_DST_ALPHA};
    private static int[] capture() {
        int n=glGetInteger(GL_MAX_DRAW_BUFFERS);
        int[] state=new int[5+n*9];
        state[0]=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];
        glGetIntegerv(GL_VIEWPORT,viewport);
        System.arraycopy(viewport,0,state,1,4);
        for (int i=0;i<n;i++) {
            int j=5+i*9;
            state[j]=glGetInteger(GL_DRAW_BUFFER0+i);
            state[j+1]=glIsEnabledi(GL_BLEND,i)?1:0;
            for (int k=0;k<BLEND.length;k++)
                state[j+2+k]=GL.getCapabilities().OpenGL40?glGetIntegeri(BLEND[k],i):glGetInteger(BLEND[k]);
            int[] mask=new int[4];
            glGetIntegeri_v(GL_COLOR_WRITEMASK,i,mask);
            state[j+8]=mask[0]|mask[1]<<1|mask[2]<<2|mask[3]<<3;
        }
        return state;
    }
    private static void restore(int[] state) {
        int n=(state.length-5)/9;
        int[] buffers=new int[n];
        for (int i=0;i<n;i++) buffers[i]=state[5+i*9];
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER,state[0]);
        if (state[0]==0) glDrawBuffer(buffers[0]); else glDrawBuffers(buffers);
        GlStateManager._viewport(state[1],state[2],state[3],state[4]);
        // Keep vanilla's global state cache in step before restoring per-attachment overrides.
        if (state[6]!=0) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(state[9],state[10],state[11],state[12]);
        int mask=state[13];
        GlStateManager._colorMask((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0);
        for (int i=0;i<n;i++) {
            int j=5+i*9;
            if (state[j+1]!=0) glEnablei(GL_BLEND,i); else glDisablei(GL_BLEND,i);
            if (GL.getCapabilities().OpenGL40) {
                glBlendEquationSeparatei(i,state[j+2],state[j+3]);
                glBlendFuncSeparatei(i,state[j+4],state[j+5],state[j+6],state[j+7]);
            } else if (i==0) {
                glBlendEquationSeparate(state[j+2],state[j+3]);
                glBlendFuncSeparate(state[j+4],state[j+5],state[j+6],state[j+7]);
            }
            int m=state[j+8];
            glColorMaski(i,(m&1)!=0,(m&2)!=0,(m&4)!=0,(m&8)!=0);
        }
    }
}
