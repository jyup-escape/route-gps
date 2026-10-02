package jp.akagumi.routegps;

import java.util.*;

/** Offline simulation. SI units; signal decisions are sampled once per encounter. */
public final class WalkSimulation {
    public final Route route;
    public final double cruise, acceleration, braking, variation, redProbability, waitMin, waitMax;
    public final boolean loop;
    public double distance=0, speed=0, waitRemaining=0;
    public boolean finished=false, paused=false;
    public int laps=0, stoppedSignals=0, passedSignals=0;
    private final Random random;
    private final double[] signals;
    private boolean[] red;
    private double[] waits;
    private int nextSignal;
    private boolean pauseRequested=false;
    private double pauseDistance=0, targetCruise, changeIn=0;

    public WalkSimulation(Route route,double cruiseKmh,double acceleration,double braking,
            double variationPercent,double redPercent,double waitMin,double waitMax,
            double[] signals,boolean loop,long seed) {
        this.route=route;this.loop=loop;
        if(!Double.isFinite(cruiseKmh)||cruiseKmh<0.1||!valid(acceleration,0.05,2)||!valid(braking,0.05,2)
                ||!valid(variationPercent,0,30)||!valid(redPercent,0,100)
                ||!valid(waitMin,1,180)||!valid(waitMax,waitMin,180))
            throw new IllegalArgumentException("巡航速度は0.1km/h以上の有限の数値、加減速度0.05〜2m/s²、ゆらぎ0〜30%、赤の確率0〜100%、待ち時間1〜180秒（最小≦最大）で設定してください");
        if(loop && Route.distance(route.points[0],route.points[route.points.length-1])>2)
            throw new IllegalArgumentException("周回には始点に戻る徒歩ルートが必要です。周回をONにして徒歩ルートを作り直してください。");
        cruise=cruiseKmh/3.6;this.acceleration=acceleration;this.braking=braking;
        variation=variationPercent/100;redProbability=redPercent/100;
        this.waitMin=waitMin;this.waitMax=waitMax;random=new Random(seed);
        TreeSet<Double> sorted=new TreeSet<>();
        for(double s:signals){if(!Double.isFinite(s)||s<=0||s>=route.length)throw new IllegalArgumentException("信号の距離は0より大きくルートの全長より小さい値にしてください");sorted.add(s);}
        this.signals=new double[sorted.size()];int i=0;for(double s:sorted)this.signals[i++]=s;
        targetCruise=cruise;newLap();
    }
    private static boolean valid(double n,double min,double max){return Double.isFinite(n)&&n>=min&&n<=max;}
    private void newLap(){
        nextSignal=0;red=new boolean[signals.length];waits=new double[signals.length];
        for(int i=0;i<signals.length;i++){red[i]=random.nextDouble()<redProbability;waits[i]=waitMin+random.nextDouble()*(waitMax-waitMin);}
    }
    public void requestPause(){
        if(paused||finished||pauseRequested)return;
        pauseRequested=true;
        pauseDistance=Math.min(route.length,distance+speed*speed/(2*braking));
        if(speed==0){pauseDistance=distance;paused=true;}
    }
    public void resume(){pauseRequested=false;paused=false;}
    public String phase(){
        if(finished)return "完了・終点を保持中";
        if(paused)return "一時停止・位置を保持中";
        if(pauseRequested)return "一時停止に向けて減速中";
        if(waitRemaining>0)return "信号待ち（約"+(int)Math.ceil(waitRemaining)+"秒）";
        double remaining=nextStop()-distance;
        if(remaining<speed*speed/(2*braking)+0.3)return "停止位置に向けて減速中";
        return speed<targetCruise-0.05?"歩き始め・加速中":"歩行中";
    }
    private double nextStop(){
        double stop=route.length;
        for(int i=nextSignal;i<signals.length;i++)if(red[i]){stop=signals[i];break;}
        return pauseRequested?Math.min(stop,pauseDistance):stop;
    }
    public void advance(double seconds){
        if(!Double.isFinite(seconds)||seconds<0||seconds>3600)throw new IllegalArgumentException("Invalid simulation time");
        // 20ms internal steps keep acceleration bounded independently of GPS tick rate.
        while(seconds>1e-9){double dt=Math.min(0.02,seconds);step(dt);seconds-=dt;}
    }
    private void step(double dt){
        if(paused||finished)return;
        if(waitRemaining>0){waitRemaining=Math.max(0,waitRemaining-dt);return;}
        changeIn-=dt;
        if(changeIn<=0){targetCruise=cruise*(1+(random.nextDouble()*2-1)*variation);changeIn=6+random.nextDouble()*8;}
        double boundary=nextStop(),remaining=Math.max(0,boundary-distance);
        if(remaining<1e-8 && speed<1e-6){arrive(boundary);return;}
        // Sample gentle turns over a 6m span, and slow before sharp changes in direction.
        double turn=angle(route.bearing(Math.max(0,distance-2)),route.bearing(Math.min(route.length,distance+4)));
        double turnFactor=1-0.35*Math.min(1,turn/90);
        double desired=targetCruise*turnFactor;
        // Solve (v0+v1)*dt/2 + v1²/(2b) <= remaining.
        double safe=Math.max(0,(-braking*dt+Math.sqrt(braking*braking*dt*dt-4*braking*speed*dt+8*braking*remaining))/2);
        if(!Double.isFinite(safe))safe=0;
        desired=Math.min(desired,safe);
        double next=Math.max(0,speed+Math.max(-braking*dt,Math.min(acceleration*dt,desired-speed)));
        // When the stopping event is inside this time slice, integrate its exact stop time.
        if(remaining<=speed*speed/(2*braking)+1e-7 && speed/braking<=dt+1e-8){
            distance=boundary;speed=0;arrive(boundary);return;
        }
        double move=(speed+next)*dt/2;
        if(move>remaining+1e-6){throw new IllegalStateException("Stopping envelope exceeded");}
        distance=Math.min(boundary,distance+move);speed=next;
        while(nextSignal<signals.length && !red[nextSignal] && distance>=signals[nextSignal]){passedSignals++;nextSignal++;}
        if(boundary-distance<1e-8 && speed<1e-6)arrive(boundary);
    }
    private void arrive(double boundary){
        distance=boundary;speed=0;
        if(pauseRequested && Math.abs(boundary-pauseDistance)<1e-6){paused=true;return;}
        if(nextSignal<signals.length && red[nextSignal] && Math.abs(boundary-signals[nextSignal])<1e-6){
            stoppedSignals++;waitRemaining=waits[nextSignal];nextSignal++;return;
        }
        if(boundary>=route.length-1e-6){
            if(loop){distance=0;laps++;newLap();}
            else finished=true;
        }
    }
    private static double angle(double a,double b){double d=Math.abs(a-b)%360;return Math.min(d,360-d);}
    public static double[] parseSignals(String text){
        if(text.trim().isEmpty())return new double[0];
        String[] pieces=text.trim().split("[,\\s]+");double[] result=new double[pieces.length];
        if(pieces.length>500)throw new IllegalArgumentException("信号は最大500地点です");
        for(int i=0;i<pieces.length;i++){try{result[i]=Double.parseDouble(pieces[i]);}catch(NumberFormatException e){throw new IllegalArgumentException("信号位置を始点からの距離（m）で入力してください");}}
        return result;
    }
}
