package willow.train.hexie.client.cab;

import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.api.cab.DmiGuidance;
import willow.train.hexie.api.cab.DmiSpeedReadings;
import willow.train.hexie.api.cab.DmiState;
import willow.train.hexie.vehicle.control.Ctcs2Panel;

/** 200H manual figures 2-3 through 2-8. Coordinates refer to the 800 x 600 LCD. */
public final class Ctcs2DmiLayout {
    private static final int BLACK=0xff000000, WHITE=0xffdddddd, GREY=0xffbfbfbf,
            DARK=0xff404040, BORDER=0xff181818, YELLOW=0xffffff00, ORANGE=0xffff8000,
            RED=0xffff0000, BLUE=0xff000080, PLOT=0xff000020;
    private Ctcs2DmiLayout() {}
    public interface Canvas {
        void icon(String name,float x,float y,float w,float h);
        void rect(float x,float y,float w,float h,int c);
        void line(float x,float y,float ex,float ey,float width,int c);
        void quad(float x1,float y1,float x2,float y2,float x3,float y3,float x4,float y4,int c);
        void text(String s,float x,float y,float scale,int c);
        void center(String s,float x,float y,float scale,int c);
        void fitted(String s,float x,float y,float width,float scale,int c);
    }
    public static void render(CabDisplayState state,int range,Canvas d) {
        var data=state.dmi(); var resolved=DmiGuidance.resolve(state); var target=resolved.target(); var guide=resolved.guidance();
        boolean curve=resolved.hasTargetCurve();
        boolean tsm=curve && (data.lineLimitKmh()<0 || guide.speedKmh()<data.lineLimitKmh());
        var speeds=data.speedReadings()==null?DmiSpeedReadings.fromGuidance(resolved):data.speedReadings();
        int pointer=pointerColor(speeds,state.speedKmh());
        d.rect(0,0,800,600,BLACK);
        // A1 is above A2; no labels or distance scale when no target is being monitored.
        if(tsm || curve && guide.secondsToBrake()<=8) {
            float size=guide.secondsToBrake()>=8?4:guide.secondsToBrake()>4?20:guide.secondsToBrake()>0?30:40;
            d.rect(32-size/2,30-size/2,size,size,pointer);
        }
        if(tsm) {
            long distance=Math.round(target.distanceM()>=1000?Math.ceil(target.distanceM()/10)*10:Math.ceil(target.distanceM()));
            d.center(Long.toString(distance),32,77,1.8f,WHITE);
            for(int m=0;m<=1000;m+=m<100?10:100) {
                float y=321-distanceBar(m)*220;
                d.line(m%100==0?9:20,y,37,y,2,WHITE);
            }
            float h=distanceBar(target.distanceM())*220;
            d.rect(45,321-h,12,h,WHITE);
        }
        speedometer(d,state.speedKmh(),speeds);
        // B3/B4/B5 command slots. Only a known station has a corresponding supplied command.
        for(int x:new int[]{176,220,264}) outline(d,x,324,44,44,BORDER);
        if(target!=null && target.kind()==DmiState.TargetKind.STATION) d.icon("station",181,329,34,34);
        // C8/C9 are at the far LEFT. All other C cells stay reserved.
        d.line(0,368,416,368,1,BORDER); d.line(0,430,720,430,1,BORDER);
        d.line(0,399,64,399,1,BORDER);
        for(int x:new int[]{64,104,144,184,296,336,376,416}) d.line(x,368,x,430,1,BORDER);
        d.center("CTCS 2",32,377,1.2f,WHITE);
        // The icon describes the same actual brake amount sent to the bogeys.
        if(data.brake()>=1) d.icon("brake_emergency",5,401,54,27);
        else if(data.brake()>0) d.icon(serviceBrakeIcon(data.brake()),5,401,54,27);
        // D: no invented titles, advice text, percentages or point markers.
        signal(d,data.ctcs().code());
        d.rect(416,64,304,182,PLOT);
        if(guide.available()) {
            // Every plan segment uses the same safety envelope, including a single stop.
            // The driver's chosen running curve and actual speed never set this plot's origin.
            var points=resolved.plotPoints(range);
            float px=distanceX(0,range),py=speedY(guide.speedKmh());
            for(int i=1;i<points.size();i++) {
                var point=points.get(i);float x=distanceX(point.distanceM(),range),y=speedY(point.speedKmh());
                d.quad(px,py,x,y,x,246,px,246,BLUE);px=x;py=y;
            }
            double start=curve?resolved.descentStartDistance():range;
            boolean multiple=data.ctcs().targets().size()>1 || !data.ctcs().targets().isEmpty()
                    && data.target()!=null && data.target().kind()==DmiState.TargetKind.STATION;
            if(!multiple && curve && start<=range) d.line(distanceX(start,range),58,distanceX(start,range),246,2,YELLOW);
        }
        for(int v=0;v<=300;v+=50) {float y=speedY(v);d.line(416,y,720,y,1,DARK);d.text(Integer.toString(v),418,y-12,1.2f,WHITE);}
        for(int m:new int[]{0,1000,2000,4000,8000,16000}) if(m<=range) {
            float x=distanceX(m,range);d.line(x,64,x,348,1,DARK);
            d.center(m==0?"0":m/1000+"k",x,350,1.3f,WHITE);
        }
        for(int y:new int[]{246,270,286,310,334,368}) d.line(416,y,720,y,1,BORDER);
        if(target!=null && target.distanceM()<=range) {
            float x=Math.min(696,distanceX(target.distanceM(),range));
            d.icon(target.speedKmh()==0?"decrease_zero":"decrease",x,248,20,20);
            if(target.kind()==DmiState.TargetKind.STATION) d.icon("station_small",x,312,20,20);
        }
        // E/F preserve the physical display's cells. Unsupplied fields remain empty.
        d.fitted(target!=null && target.kind()==DmiState.TargetKind.STATION?target.name():data.stationName(),425,387,282,2.2f,WHITE);
        for(int y:new int[]{460,490,520,550,580}) d.line(64,y,358,y,1,BORDER);
        d.line(64,430,64,600,1,BORDER);d.line(358,430,358,600,1,BORDER);d.line(416,430,416,600,1,BORDER);
        d.line(416,490,720,490,1,BORDER);d.line(496,490,496,600,1,BORDER);d.line(640,490,640,600,1,BORDER);
        d.line(496,540,640,540,1,BORDER);d.line(568,540,568,600,1,BORDER);
        String mode=switch(data.mode()){case MANUAL->"人控";case AUTOMATIC->"机控";default->"";};
        d.center(mode,32,527,2,WHITE);
        d.fitted(data.kilometerMarker(),70,580,280,2.1f,WHITE);
        d.center("车次号",456,519,1.4f,WHITE);d.fitted(data.trainNumber(),424,546,66,1.5f,WHITE);
        d.center("时间日期",680,509,1.2f,WHITE);d.fitted(data.time(),646,536,68,1.2f,WHITE);
        arrow(d,385,466,false,DARK);arrow(d,385,552,true,WHITE);
        d.icon(range==16000?"zoom_in":"zoom_in_disabled",502,548,58,36);
        d.icon(range==8000?"zoom_out":"zoom_out_disabled",574,548,58,36);
        var keys = Ctcs2Panel.SOFT_KEY_LABELS;
        for (int i = 0; i < keys.size(); i++) {
            outline(d,720,i*75,80,75,BORDER);
            d.center(keys.get(i),760,i*75+27,2.3f,WHITE);
        }
    }
    /** 200H figures 2-6/2-7 and table 2-4; LKC table 7.2 supplies the SBI/EBI band sequence. */
    private static void speedometer(Canvas d,double speed,DmiSpeedReadings data) {
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
        d.center(Integer.toString((int)Math.round(speed)),cx,cy-10,2.5f,pointer==RED?WHITE:BLACK);
    }

    private static int pointerColor(DmiSpeedReadings data,double speed) {
        return switch(data.status(speed)) {
            case INTERVENTION->RED;
            case OVERSPEED->ORANGE;
            case NORMAL->data.hasPermittedSpeed() && data.monitor()==DmiSpeedReadings.Monitor.TSM?YELLOW:GREY;
        };
    }
    /** Three service levels below full commanded braking. */
    private static String serviceBrakeIcon(double brake) {
        return brake <= .3 ? "brake_weak" : brake <= .6 ? "brake_medium" : "brake_service";
    }
    /** Manual table 2-11: green numbered discs, green/yellow LU, yellow U, yellow/red HU. */
    private static void signal(Canvas d,willow.train.hexie.vehicle.control.CtcsControl.Code code) {
        if(code==willow.train.hexie.vehicle.control.CtcsControl.Code.UNKNOWN) return;
        float x=448,y=32,r=23;
        circle(d,x,y,r+1,BLACK);
        int top=switch(code){case HU,U->YELLOW;default->0xff00ff00;};
        circle(d,x,y,r,top);
        if(code==willow.train.hexie.vehicle.control.CtcsControl.Code.LU || code==willow.train.hexie.vehicle.control.CtcsControl.Code.HU) {
            int bottom=code==willow.train.hexie.vehicle.control.CtcsControl.Code.HU?0xffff0000:YELLOW;
            for(int i=0;i<32;i++){double a=i*Math.PI/32,b=(i+1)*Math.PI/32;
                d.quad(x,y,x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,x+(float)Math.cos(b)*r,y+(float)Math.sin(b)*r,x,y,bottom);}
            d.line(x-r,y,x+r,y,1,BLACK);
        }
        String number=switch(code){case L2->"2";case L3->"3";case L4->"4";case L5->"5";default->"";};
        if(!number.isEmpty()) d.center(number,x,y-8,1.8f,BLACK);
    }
    private static void arrow(Canvas d,float x,float y,boolean down,int c){float sign=down?1:-1;d.rect(x-6,down?y-30:y,12,30,c);d.quad(x-16,y,x+16,y,x,y+sign*15,x,y+sign*15,c);}
    private static void outline(Canvas d,float x,float y,float w,float h,int c){d.line(x,y,x+w,y,1,c);d.line(x,y,x,y+h,1,c);d.line(x+w,y,x+w,y+h,1,c);d.line(x,y+h,x+w,y+h,1,c);}
    private static double angle(double v){v=Math.clamp(v,0,400);return Math.toRadians(135+(v<=150?v*.9:135+(v-150)*.54));}
    private static float speedY(double v){return 246-(float)Math.clamp(v/300,0,1)*145;}
    private static float distanceX(double m,int range){double v=Math.clamp(m,0,range);return 464+(float)(v<=1000?v/1000*84:84+Math.log(v/1000)/Math.log(range/1000d)*168);}
    private static float distanceBar(double m){double v=Math.clamp(m,0,1000);return(float)(v<=100?v/300:1d/3+Math.log10(v/100)*2/3);}
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
    private static void circle(Canvas d,float x,float y,float r,int c){for(int i=0;i<64;i++){double a=i*Math.PI/32,b=(i+1)*Math.PI/32;d.quad(x,y,x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,x+(float)Math.cos(b)*r,y+(float)Math.sin(b)*r,x,y,c);}}
}
