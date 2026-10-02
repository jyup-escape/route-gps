import jp.akagumi.routegps.Route;
public class RouteTest {
    static void near(double a,double b,double tolerance){if(Math.abs(a-b)>tolerance)throw new AssertionError(a+" != "+b);}
    static void rejects(String s){try{Route.parse(s);throw new AssertionError("Accepted invalid route");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        Route r=new Route(new double[][]{{0,0},{0,1},{1,1}},false);
        near(r.length,2*Math.PI*Route.EARTH/180,0.01);
        near(r.at(r.cumulative[1]/2)[1],0.5,1e-9);
        near(Route.distance(r.points[0],r.at(100*5/3.6)),100*5/3.6,0.001);
        near(r.at(r.length+100)[0],1,1e-9);
        near(r.at(-1)[0],0,1e-9);
        Route date=new Route(new double[][]{{0,179.9},{0,-179.9}},false);
        near(Math.abs(date.at(date.length/2)[1]),180,1e-6);
        Route closed=new Route(new double[][]{{0,0},{0,1}},true);
        near(closed.length,2*Math.PI*Route.EARTH/180,0.01);
        near(closed.at(closed.length)[1],0,1e-9);
        Route duplicate=new Route(new double[][]{{0,0},{0,0},{0,1}},false);
        if(duplicate.points.length!=2)throw new AssertionError("Duplicate point not removed");
        rejects("NaN,0\n0,1");rejects("91,0\n0,1");rejects("0,0\n0,0");rejects("abc\n0,1");
        near(r.bearing(0),90,0.001);
        System.out.println("PASS: distance, constant speed, endpoints, date line, closed loop, duplicates, validation, bearing");
    }
}
