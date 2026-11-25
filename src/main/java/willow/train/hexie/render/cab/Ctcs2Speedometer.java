package willow.train.hexie.render.cab;

import willow.train.hexie.core.api.cab.DmiSpeedReadings;

import static willow.train.hexie.render.cab.Ctcs2DmiLayout.Canvas;
import static willow.train.hexie.render.cab.Ctcs2DmiLayout.DARK;
import static willow.train.hexie.render.cab.Ctcs2DmiLayout.GREY;
import static willow.train.hexie.render.cab.Ctcs2DmiLayout.ORANGE;
import static willow.train.hexie.render.cab.Ctcs2DmiLayout.RED;
import static willow.train.hexie.render.cab.Ctcs2DmiLayout.WHITE;
import static willow.train.hexie.render.cab.Ctcs2DmiLayout.YELLOW;
import static willow.train.hexie.render.cab.Ctcs2DmiLayout.circle;

// split from willow/train/hexie/client/cab/Ctcs2DmiLayout.java, lines 106-154, 177, 181-192
final class Ctcs2Speedometer {
    /** 200H figures 2-6/2-7 and table 2-4; LKC table 7.2 supplies the SBI/EBI band sequence. */
    static void speedometer(Canvas d,double speed,DmiSpeedReadings data) {
        float cx=240,cy=184,r=155,outer=164,band=9;
        for(int v=0;v<=400;v+=10) {
            double a=angle(v);
            radial(d,cx,cy,r-(v%50==0?22:13),r-3,a,v%50==0?2:1,WHITE);
            if(v%50==0) d.center(Integer.toString(v),cx+(float)Math.cos(a)*(r-43),
                    cy+(float)Math.sin(a)*(r-43)-7,1.8f,WHITE);
        }
        var status=data.status(speed);
        if(data.hasPermittedSpeed()) {
            double permitted=data.permittedKmh(),intervention=data.displayedInterventionKmh(speed);
            if(data.fullMonitoring()) {
                double target=data.targetKmh()<0?0:Math.min(data.targetKmh(),permitted);
                // A zero target has no separate dark segment. The permission ends in a double-width hook.
                ring(d,cx,cy,outer,band,angle(0),angle(target),DARK);
                int permittedColor=data.monitor()==DmiSpeedReadings.Monitor.TSM
                        || status!=DmiSpeedReadings.Status.NORMAL?YELLOW:GREY;
                ring(d,cx,cy,outer,band,angle(target),angle(permitted),permittedColor);
                ring(d,cx,cy,outer,band*2,angle(permitted)-Math.toRadians(3),angle(permitted),permittedColor);
                if(intervention>=0)
                    ring(d,cx,cy,outer,band*2,angle(permitted),angle(intervention),
                            status==DmiSpeedReadings.Status.INTERVENTION?RED:ORANGE);
            } else {
                // PS/OS/SH/CO show short marks, not the full-supervision annular band.
                radial(d,cx,cy,r-10,r+6,angle(permitted),7,GREY);
                if(intervention>=0) radial(d,cx,cy,r-10,r+6,angle(intervention),7,
                        status==DmiSpeedReadings.Status.INTERVENTION?RED:ORANGE);
            }
        }
        int pointer=pointerColor(data,speed);
        double a=angle(speed);float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
        float shoulder=r-42,tip=r-12;
        d.quad(cx-dy*7,cy+dx*7,cx+dx*shoulder-dy*4,cy+dy*shoulder+dx*4,
                cx+dx*shoulder+dy*4,cy+dy*shoulder-dx*4,cx+dy*7,cy-dx*7,pointer);
        d.quad(cx+dx*shoulder-dy*4,cy+dy*shoulder+dx*4,cx+dx*tip,cy+dy*tip,
                cx+dx*shoulder+dy*4,cy+dy*shoulder-dx*4,cx+dx*shoulder,cy+dy*shoulder,pointer);
        circle(d,cx,cy,30,pointer);
        // 200H figure 2-6 uses light digits on the red hub; normal/orange digits remain dark.
        d.center(Integer.toString((int)Math.round(speed)),cx,cy-10,2.5f,pointer==RED?WHITE:Ctcs2DmiLayout.BLACK);
    }

    static int pointerColor(DmiSpeedReadings data,double speed) {
        return switch(data.status(speed)) {
            case INTERVENTION->RED;
            case OVERSPEED->ORANGE;
            case NORMAL->data.hasPermittedSpeed() && data.monitor()==DmiSpeedReadings.Monitor.TSM?YELLOW:GREY;
        };
    }

    private static double angle(double v){v=Math.clamp(v,0,400);return Math.toRadians(135+(v<=150?v*.9:135+(v-150)*.54));}

    private static void radial(Canvas d,float x,float y,float from,float to,double a,float w,int c){float dx=(float)Math.cos(a),dy=(float)Math.sin(a);d.line(x+dx*from,y+dy*from,x+dx*to,y+dy*to,w,c);}
    private static void ring(Canvas d,float x,float y,float outer,float width,double from,double to,int color) {
        if(to<=from)return;
        float inner=outer-width;
        int n=Math.max(1,(int)Math.ceil((to-from)*outer/3));
        for(int i=0;i<n;i++) {
            double a=from+(to-from)*i/n,b=from+(to-from)*(i+1)/n;
            float ax=(float)Math.cos(a),ay=(float)Math.sin(a),bx=(float)Math.cos(b),by=(float)Math.sin(b);
            d.quad(x+ax*outer,y+ay*outer,x+bx*outer,y+by*outer,
                    x+bx*inner,y+by*inner,x+ax*inner,y+ay*inner,color);
        }
    }
}
