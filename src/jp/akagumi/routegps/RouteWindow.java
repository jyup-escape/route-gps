package jp.akagumi.routegps;

import java.util.*;

/** Request-sized windows; these sizes never limit the complete route. */
public final class RouteWindow {
    public final double start,end;
    public final double[][] points;
    public final double[] distances;
    public RouteWindow(Route route,double start) {
        this.start=start;
        int i=Arrays.binarySearch(route.cumulative,start);
        i=i>=0?i+1:-i-1;
        end=Math.min(route.length,Math.min(start+10000,route.cumulative[Math.min(route.points.length-1,i+253)]));
        List<double[]> ps=new ArrayList<>();List<Double> ds=new ArrayList<>();
        ps.add(route.at(start));ds.add(start);
        for(;i<route.points.length&&route.cumulative[i]<end;i++){ps.add(route.points[i]);ds.add(route.cumulative[i]);}
        ps.add(route.at(end));ds.add(end);
        points=ps.toArray(new double[0][]);distances=new double[ds.size()];
        for(int j=0;j<distances.length;j++)distances[j]=ds.get(j);
    }
}
