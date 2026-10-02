import jp.akagumi.routegps.*;
import java.util.*;

public class WalkSimulationTest {
    static final Route STRAIGHT=new Route(new double[][]{{35,139},{35.004,139}},false);
    static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    static void near(double a,double b,double e,String m){check(Math.abs(a-b)<=e,m+": "+a+" vs "+b);}
    static WalkSimulation make(double red,double[] signals,long seed){return new WalkSimulation(STRAIGHT,5,0.5,0.8,0,red,2,2,signals,false,seed);}
    static void tick(WalkSimulation s,double dt){
        double before=s.speed,d=s.distance;s.advance(dt);
        check(s.speed>=0,"negative speed");
        check(s.speed-before<=s.acceleration*dt+1e-5,"acceleration exceeded");
        check(before-s.speed<=s.braking*dt+1e-5,"braking exceeded");
        check(s.distance>=d-1e-6||s.loop,"distance moved backwards");
        check(s.distance<=s.route.length+1e-6,"route overshoot");
        check(Double.isFinite(s.speed)&&Double.isFinite(s.distance),"non-finite state");
    }
    public static void main(String[] args){
        WalkSimulation accelerating=make(0,new double[0],1);
        accelerating.advance(1);near(accelerating.speed,0.5,1e-6,"acceleration from rest");near(accelerating.distance,0.25,1e-6,"integrated acceleration");
        WalkSimulation red=make(100,new double[]{50},2);boolean sawWait=false;
        for(int i=0;i<25000&&!red.finished;i++){
            tick(red,0.02);
            if(red.waitRemaining>0){sawWait=true;near(red.distance,50,1e-6,"stopped exactly at signal");near(red.speed,0,1e-8,"waiting speed");}
        }
        check(sawWait&&red.finished,"red waiting and endpoint completion");check(red.stoppedSignals==1&&red.passedSignals==0,"red counted once");near(red.speed,0,1e-8,"endpoint speed");
        WalkSimulation green=make(0,new double[]{50},2);
        for(int i=0;i<5000&&green.distance<60;i++){tick(green,0.02);check(green.waitRemaining==0,"green stopped");}
        check(green.passedSignals==1&&green.stoppedSignals==0,"green encounter counted once");
        WalkSimulation pause=make(0,new double[0],3);pause.advance(10);double stopAt=pause.distance+pause.speed*pause.speed/(2*pause.braking);pause.requestPause();
        for(int i=0;i<500&&!pause.paused;i++)tick(pause,0.02);
        check(pause.paused,"pause completed");near(pause.distance,stopAt,1e-6,"smooth pause distance");
        double held=pause.distance;pause.advance(30);near(pause.distance,held,1e-8,"pause holds");pause.resume();pause.advance(1);near(pause.speed,0.5,1e-6,"resume accelerates");
        WalkSimulation a=new WalkSimulation(STRAIGHT,5,0.5,0.8,15,50,2,5,new double[]{50,100,200},false,99);
        WalkSimulation b=new WalkSimulation(STRAIGHT,5,0.5,0.8,15,50,2,5,new double[]{50,100,200},false,99);
        for(int i=0;i<5000;i++){tick(a,0.02);tick(b,0.02);near(a.distance,b.distance,1e-10,"seed reproduction");check(a.speed<=a.cruise*1.15+1e-6,"variation range");}
        Route closed=new Route(new double[][]{{35,139},{35.0004,139},{35,139}},false);
        WalkSimulation lap=new WalkSimulation(closed,5,0.5,0.8,15,50,1,2,new double[]{25,65},true,44);
        for(int i=0;i<30000&&lap.laps<3;i++)tick(lap,0.02);
        check(lap.laps>=3&&!lap.finished,"continuous closed loops");
        WalkSimulation slow=make(100,new double[]{0.1,0.2,0.3},8);
        for(int i=0;i<2000;i++)tick(slow,0.02);check(slow.stoppedSignals==3,"closely spaced stops");
        // Different GPS callback chunk sizes do not change the path materially.
        WalkSimulation fine=make(100,new double[]{50},7),coarse=make(100,new double[]{50},7);
        for(int i=0;i<4000;i++)fine.advance(0.02);for(int i=0;i<320;i++)coarse.advance(0.25);
        near(fine.distance,coarse.distance,0.05,"callback timing independence");
        Random rng=new Random(420);int stops=0;
        for(int k=0;k<100;k++){
            double acc=0.05+rng.nextDouble()*1.95,dec=0.05+rng.nextDouble()*1.95;
            Route shortRoute=new Route(new double[][]{{0,0},{0,0.001}},false);
            WalkSimulation fuzz=new WalkSimulation(shortRoute,0.1+rng.nextDouble()*14.9,acc,dec,30,50,1,3,new double[]{1,2,10,30,80},false,k);
            for(int i=0;i<15000&&!fuzz.finished;i++)tick(fuzz,0.02);
            stops+=fuzz.stoppedSignals;
        }
        check(stops>0,"random red sampling");
        Route highway=new Route(new double[][]{{0,0},{0,1}},false);
        WalkSimulation fast=new WalkSimulation(highway,200,0.5,0.8,0,0,1,1,new double[0],false,9);
        for(int i=0;i<6000;i++)tick(fast,0.02);
        near(fast.speed*3.6,200,1e-5,"cruise above former limit");
        fast.requestPause();
        for(int i=0;i<4000&&!fast.paused;i++)tick(fast,0.02);
        check(fast.paused&&fast.speed==0,"high speed smooth stop");
        new WalkSimulation(highway,1000000,0.5,0.8,0,0,1,1,new double[0],false,9);
        try{new WalkSimulation(highway,Double.POSITIVE_INFINITY,0.5,0.8,0,0,1,1,new double[0],false,9);throw new AssertionError("infinite cruise accepted");}catch(IllegalArgumentException expected){}
        WalkSimulation unlimited=new WalkSimulation(highway,300,100,150,250,100,900,1200,new double[]{1000},false,12);
        for(int i=0;i<1000;i++)tick(unlimited,0.02);
        check(unlimited.stoppedSignals==1&&unlimited.waitRemaining>850,"unlimited acceleration, variation and wait");
        WalkSimulation enormousBraking=new WalkSimulation(STRAIGHT,100,1000000000000.0,1000000000000.0,200,0,0,0,new double[0],false,1);
        for(int i=0;i<2000&&!enormousBraking.finished;i++)tick(enormousBraking,0.02);
        check(enormousBraking.distance>0,"large braking must not freeze movement");
        WalkSimulation tiny=new WalkSimulation(highway,5,0.00001,0.00002,0,0,0,0,new double[0],false,1);
        tiny.advance(1);near(tiny.speed,0.00001,1e-10,"positive small acceleration accepted");
        new WalkSimulation(highway,5,Double.MAX_VALUE,Double.MAX_VALUE,500,0,0,0,new double[0],false,1).advance(1);
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){
            try{new WalkSimulation(highway,5,invalid,0.8,0,0,1,1,new double[0],false,1);throw new AssertionError("invalid acceleration accepted");}catch(IllegalArgumentException expected){}
        }
        try{new WalkSimulation(STRAIGHT,5,0.5,0.8,15,50,60,15,new double[0],false,1);throw new AssertionError("invalid wait accepted");}catch(IllegalArgumentException expected){}
        try{new WalkSimulation(STRAIGHT,5,0.5,0.8,15,50,15,60,new double[0],true,1);throw new AssertionError("open loop accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: bounded acceleration/braking, exact signal stops, green pass, waits, endpoint, pause/resume, seeded randomness, speed range, closed laps, close signals, callback timing, 100 randomized scenarios, unrestricted cruise, high speed stop, validation");
    }
}
