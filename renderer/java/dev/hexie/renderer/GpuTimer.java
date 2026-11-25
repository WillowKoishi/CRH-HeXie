package dev.hexie.renderer;

import static org.lwjgl.opengl.GL45C.*;

final class GpuTimer {
    private GpuTimer() {}
    static void measure(FrameBudget budget,Runnable commands) {
        if (budget==null) { commands.run(); return; }
        int query=glGenQueries();
        try {
            if (glGetQueryi(GL_TIME_ELAPSED,GL_CURRENT_QUERY)==0) {
                glBeginQuery(GL_TIME_ELAPSED,query);
                try { commands.run(); } finally { glEndQuery(GL_TIME_ELAPSED); }
                budget.recordGpuMillis(glGetQueryObjectui64(query,GL_QUERY_RESULT)*1e-6);
            } else {
                int end=glGenQueries();
                try {
                    glQueryCounter(query,GL_TIMESTAMP); commands.run(); glQueryCounter(end,GL_TIMESTAMP);
                    long nanos=glGetQueryObjectui64(end,GL_QUERY_RESULT)-glGetQueryObjectui64(query,GL_QUERY_RESULT);
                    budget.recordGpuMillis(Math.max(0,nanos)*1e-6);
                } finally { glDeleteQueries(end); }
            }
        } finally { glDeleteQueries(query); }
    }
}
