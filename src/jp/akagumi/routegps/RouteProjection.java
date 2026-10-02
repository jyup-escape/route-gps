package jp.akagumi.routegps;

import java.util.Arrays;

/** Mercator preview with continuous longitude across the date line and multiple laps. */
public final class RouteProjection {
    public final Route route;
    public final double[] x;
    public RouteProjection(Route route){this.route=route;x=new double[route.points.length];x[0]=route.points[0][1];for(int i=1;i<x.length;i++)x[i]=x[i-1]+wrap(route.points[i][1]-route.points[i-1][1]);}
    public static double wrap(double value){return ((value+180)%360+360)%360-180;}
    public static double y(double latitude){double lat=Math.max(-85.05112878,Math.min(85.05112878,latitude));return -Math.toDegrees(Math.log(Math.tan(Math.PI/4+Math.toRadians(lat)/2)));}
    public double markerX(double distance,double longitude){int i=Arrays.binarySearch(route.cumulative,Math.max(0,Math.min(route.length,distance)));if(i<0)i=Math.max(0,-i-2);return x[i]+wrap(longitude-route.points[i][1]);}
}
