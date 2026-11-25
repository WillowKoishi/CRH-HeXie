package dev.hexie.renderer;

import java.util.BitSet;
import static org.lwjgl.opengl.GL45C.*;

/** Prefer unused slots below the foreign ranges. A leased occupied slot is restored after dispatch. */
final class SsboBindings implements AutoCloseable {
    private final int program,general;
    private final int[] slots,blocks,previous,names;
    private final long[] offsets,sizes;
    SsboBindings(int program,String... blockNames) {
        this.program=program; general=glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        int limit=Math.min(8,glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS));
        int count=blockNames.length;
        if (count>limit) throw new IllegalStateException("not enough non-reserved SSBO slots");
        slots=new int[count]; blocks=new int[count]; previous=new int[count]; names=new int[count];
        offsets=new long[count]; sizes=new long[count];
        BitSet free=new BitSet(limit),available=new BitSet(limit);
        available.set(0,limit);
        for (int i=0;i<limit;i++) if (glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING,i)==0) free.set(i);
        int[] property={GL_BUFFER_BINDING},value={0};
        for (int i=0;i<count;i++) {
            blocks[i]=glGetProgramResourceIndex(program,GL_SHADER_STORAGE_BLOCK,blockNames[i]);
            if (blocks[i]==GL_INVALID_INDEX) throw new IllegalArgumentException("missing storage block "+blockNames[i]);
            glGetProgramResourceiv(program,GL_SHADER_STORAGE_BLOCK,blocks[i],property,null,value);
            previous[i]=value[0];
            int slot=free.isEmpty()?available.nextSetBit(0):free.nextSetBit(0);
            slots[i]=slot; names[i]=glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING,slot);
            if (names[i]!=0) {
                offsets[i]=glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START,slot);
                sizes[i]=glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE,slot);
            }
            available.clear(slot); free.clear(slot);
        }
        for (int i=0;i<count;i++) glShaderStorageBlockBinding(program,blocks[i],slots[i]);
    }
    void bind(int index,int buffer) { glBindBufferBase(GL_SHADER_STORAGE_BUFFER,slots[index],buffer); }
    public void close() {
        for (int i=0;i<slots.length;i++) {
            if (names[i]!=0 && sizes[i]>0) glBindBufferRange(GL_SHADER_STORAGE_BUFFER,slots[i],names[i],offsets[i],sizes[i]);
            else glBindBufferBase(GL_SHADER_STORAGE_BUFFER,slots[i],names[i]);
            glShaderStorageBlockBinding(program,blocks[i],previous[i]);
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER,general);
    }
}
