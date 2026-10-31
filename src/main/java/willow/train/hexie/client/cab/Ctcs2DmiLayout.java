package willow.train.hexie.client.cab;

import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.api.cab.DmiGuidance;
import willow.train.hexie.api.cab.DmiState;

/** 200H manual figures 2-3 through 2-8. Coordinates refer to the 800 x 600 LCD. */
public final class Ctcs2DmiLayout {
    private static final int BLACK=0xff000000, WHITE=0xffdddddd, GREY=0xffbfbfbf,
            DARK=0xff404040, BORDER=0xff181818, YELLOW=0xffffff00, ORANGE=0xffff8000,
            BLUE=0xff000080, PLOT=0xff000020;
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
        var data=state.dmi(); var target=data.target(); var guide=DmiGuidance.from(state);
        boolean curve=DmiGuidance.hasTargetCurve(state);
        boolean tsm=curve && (data.lineLimitKmh()<0 || guide.speedKmh()<data.lineLimitKmh());
        boolean overspeed=guide.available() && state.speedKmh()>guide.speedKmh()+.5;
        int pointer=overspeed?ORANGE:tsm?YELLOW:GREY;
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
        // B1/B2: filled needle and hub; central digits are dark on a grey/yellow hub.
        float cx=240,cy=184,r=155;
        if(guide.available()) {
            double from=target==null?0:Math.min(target.speedKmh(),guide.speedKmh());
            arc(d,cx,cy,r+5,0,from,9,DARK);
            arc(d,cx,cy,r+5,from,guide.speedKmh(),9,tsm||overspeed?YELLOW:GREY);
            radial(d,cx,cy,r-4,r+10,angle(guide.speedKmh()),5,tsm||overspeed?YELLOW:GREY);
        }
        for(int v=0;v<=400;v+=10) {
            double a=angle(v); radial(d,cx,cy,r-(v%50==0?22:13),r-3,a,1,WHITE);
            if(v%50==0) d.center(Integer.toString(v),cx+(float)Math.cos(a)*(r-43),cy+(float)Math.sin(a)*(r-43)-7,1.8f,WHITE);
        }
        double a=angle(state.speedKmh()); float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
        d.quad(cx-dy*8,cy+dx*8,cx+dx*(r-26),cy+dy*(r-26),cx+dy*8,cy-dx*8,cx-dx*12,cy-dy*12,pointer);
        circle(d,cx,cy,30,pointer);
        d.center(Integer.toString((int)Math.round(state.speedKmh())),cx,cy-10,2.5f,BLACK);
        // B3/B4/B5 command slots. Only a known station has a corresponding supplied command.
        for(int x:new int[]{176,220,264}) outline(d,x,324,44,44,BORDER);
        if(target!=null && target.kind()==DmiState.TargetKind.STATION) d.icon("station",181,329,34,34);
        // C8/C9 are at the far LEFT. All other C cells stay reserved.
        d.line(0,368,416,368,1,BORDER); d.line(0,430,720,430,1,BORDER);
        d.line(0,399,64,399,1,BORDER);
        for(int x:new int[]{64,104,144,184,296,336,376,416}) d.line(x,368,x,430,1,BORDER);
        d.center("CTCS 2",32,377,1.2f,WHITE);
        // Explicit lamp keys distinguish emergency from parking/full service; never infer it from brake force.
        if(lit(state,"dmi.brake.emergency")) d.icon("brake_emergency",5,401,54,27);
        else if(data.brake()>0) d.icon("brake_service",5,401,54,27);
        // D: no invented titles, advice text, percentages or point markers.
        d.rect(416,64,304,182,PLOT);
        if(guide.available()) {
            double end=curve?Math.min(range,target.distanceM()):range;
            float px=distanceX(0,range),py=speedY(guide.speedKmh());
            // The manual draws the first descending segment as a straight line in the plan.
            double start=curve?Math.max(0,target.distanceM()-Math.pow(Math.max(0,data.lineLimitKmh())/3.6,2)
                    /(2*data.serviceDeceleration())):range;
            if(start>0) {float sx=distanceX(Math.min(start,end),range);d.quad(px,py,sx,py,sx,246,px,246,BLUE);px=sx;}
            float ex=distanceX(end,range),ey=curve && target.distanceM()<=range?speedY(target.speedKmh()):speedY(DmiGuidance.speedAtDistance(state,end));
            d.quad(px,py,ex,ey,ex,246,px,246,BLUE);
            if(curve && start<=range) d.line(distanceX(start,range),58,distanceX(start,range),246,2,YELLOW);
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
        String[] keys={"数据","模式","载频","等级","其它","启动","缓解","警惕"};
        for(int i=0;i<8;i++){outline(d,720,i*75,80,75,BORDER);d.center(keys[i],760,i*75+27,2.3f,WHITE);}
    }
    private static boolean lit(CabDisplayState s,String key){var l=s.lamps().get(key);return l!=null&&l.lit();}
    private static void arrow(Canvas d,float x,float y,boolean down,int c){float sign=down?1:-1;d.rect(x-6,down?y-30:y,12,30,c);d.quad(x-16,y,x+16,y,x,y+sign*15,x,y+sign*15,c);}
    private static void outline(Canvas d,float x,float y,float w,float h,int c){d.line(x,y,x+w,y,1,c);d.line(x,y,x,y+h,1,c);d.line(x+w,y,x+w,y+h,1,c);d.line(x,y+h,x+w,y+h,1,c);}
    private static double angle(double v){v=Math.clamp(v,0,400);return Math.toRadians(135+(v<=150?v*.9:135+(v-150)*.54));}
    private static float speedY(double v){return 246-(float)Math.clamp(v/300,0,1)*145;}
    private static float distanceX(double m,int range){double v=Math.clamp(m,0,range);return 464+(float)(v<=1000?v/1000*84:84+Math.log(v/1000)/Math.log(range/1000d)*168);}
    private static float distanceBar(double m){double v=Math.clamp(m,0,1000);return(float)(v<=100?v/300:1d/3+Math.log10(v/100)*2/3);}
    private static void radial(Canvas d,float x,float y,float from,float to,double a,float w,int c){float dx=(float)Math.cos(a),dy=(float)Math.sin(a);d.line(x+dx*from,y+dy*from,x+dx*to,y+dy*to,w,c);}
    private static void arc(Canvas d,float x,float y,float r,double from,double to,float w,int c){double a=angle(from),b=angle(to);int n=Math.max(1,(int)Math.ceil(Math.abs(b-a)*r/3));for(int i=0;i<n;i++){double p=a+(b-a)*i/n,q=a+(b-a)*(i+1)/n;d.line(x+(float)Math.cos(p)*r,y+(float)Math.sin(p)*r,x+(float)Math.cos(q)*r,y+(float)Math.sin(q)*r,w,c);}}
    private static void circle(Canvas d,float x,float y,float r,int c){for(int i=0;i<64;i++){double a=i*Math.PI/32,b=(i+1)*Math.PI/32;d.quad(x,y,x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,x+(float)Math.cos(b)*r,y+(float)Math.sin(b)*r,x,y,c);}}
}
