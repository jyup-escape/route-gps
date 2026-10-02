package jp.akagumi.routegps;

import java.nio.file.*;

public class RoadClientTest {
    static void near(double a,double b,double e){if(Math.abs(a-b)>e)throw new AssertionError(a+" vs "+b);}
    public static void main(String[] args)throws Exception{
        double[] projected=RoadClient.project(new double[]{0.00001,0.0005},new double[]{0,0},new double[]{0,0.001});
        near(projected[0],0.5,1e-6);near(projected[1],1.1119508,0.0001);
        double[] date=RoadClient.project(new double[]{0.00001,180},new double[]{0,179.999},new double[]{0,-179.999});near(date[0],0.5,1e-6);
        near(RoadClient.project(new double[]{0,0.002},new double[]{0,0},new double[]{0,0.001})[0],1,1e-8);
        System.out.println("PASS: signal projection, date line, clamped endpoints");
        if(args.length>0&&"--live".equals(args[0])){
            double[][] points={{35.681236,139.767125},{35.684000,139.765000},{35.682000,139.768000}};
            RoadClient.Result result=RoadClient.walking(points,true);
            Route r=new Route(Route.parse(result.coordinates),false);
            if(Route.distance(r.points[0],r.points[r.points.length-1])>2)throw new AssertionError("Road loop not closed");
            double[] signals=WalkSimulation.parseSignals(result.signals);
            new WalkSimulation(r,5,0.5,0.8,15,50,15,60,signals,true,99);
            if(!result.warning.isEmpty())throw new AssertionError(result.warning);
            Files.write(Paths.get("build/verified-walking-route.txt"),result.coordinates.getBytes("UTF-8"));
            Files.write(Paths.get("build/verified-walking-signals.txt"),result.signals.getBytes("UTF-8"));
            System.out.println("PASS LIVE: OSRM foot loop, "+r.points.length+" geometry points, "+(int)r.length+"m, "+signals.length+" OSM signal locations");
        }
    }
}
