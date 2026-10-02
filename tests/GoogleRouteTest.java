import jp.akagumi.routegps.*;
import org.json.*;
import java.net.*;
import java.util.*;

public class GoogleRouteTest {
    static JSONArray array(Object... xs){return new JSONArray(Arrays.asList(xs));}
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    static String fixture(int mode,int count,boolean reverse){
        JSONArray lat=new JSONArray(),lon=new JSONArray();
        for(int i=0;i<count;i++){lat.put(i==0?10000000:100);lon.put(i==0?20000000:(reverse?-200:200));}
        double[][] ps=new double[count][2];for(int i=0;i<count;i++)ps[i]=new double[]{1+i*0.00001,2+i*(reverse?-0.00002:0.00002)};
        JSONArray summary=array(mode,"example",array(new Route(ps,false).length,"distance"));
        return array(array(null,array(array(summary)),null,null,null,null,null,array(array(lat,lon)))).toString();
    }
    static void rejects(String raw,int mode)throws Exception{try{GoogleRoute.parse(raw,mode);throw new AssertionError("accepted bad geometry");}catch(Exception expected){}}
    public static void main(String[] args)throws Exception{
        List<GoogleRoute.Candidate> cs=GoogleRoute.parse(")]}'\n"+fixture(2,120001,false),2);
        check(cs.size()==1&&cs.get(0).route.points.length==120001,"full geometry above old cap");
        check(Math.abs(cs.get(0).route.points[120000][1]-4.4)<1e-9,"E7 accumulated longitude");
        check(GoogleRoute.parse(fixture(2,3,true),2).get(0).route.points[2][1]<2,"negative deltas");
        rejects(fixture(0,3,false),2);rejects("[[null,[[[2,\"stub\",[1000]]]]]]",2);
        JSONArray root=new JSONArray(fixture(2,3,false));root.getJSONArray(0).getJSONArray(7).getJSONArray(0).getJSONArray(0).put(1,0.5);rejects(root.toString(),2);
        root=new JSONArray(fixture(2,3,false));root.getJSONArray(0).getJSONArray(1).getJSONArray(0).getJSONArray(0).getJSONArray(2).put(0,999999);rejects(root.toString(),2);
        String url="https://www.google.com/maps/dir/A/B/data=!4m2!4m1!3e2";
        check(GoogleRoute.mode(url)==2,"walking mode");check(GoogleRoute.mode(url.replace("!3e2","!3e0"))==0,"drive mode");
        try{GoogleRoute.mode(url.replace("!3e2","!3e3"));throw new AssertionError("transit silently downgraded");}catch(java.io.IOException expected){}
        try{GoogleRoute.mode(url+"!3m4!1m2!1d1!2d2");throw new AssertionError("via discarded");}catch(java.io.IOException expected){}
        String request=GoogleRoute.requestUrl(Arrays.asList("1,2","1.1,2.1","1.2,2.2"),2);
        String pb=URLDecoder.decode(request.substring(request.indexOf("pb=")+3),"UTF-8");
        check(pb.startsWith("!1m4!3m2!3d1.0!4d2.0!6e2!1m4!3m2!3d1.1!4d2.1!6e2"),"all waypoints ordered");
        check(pb.contains("!20m5!1e2"),"walking request");
        Route r=new Route(new double[][]{{0,179},{60,-179},{80,-170}},false);RouteProjection p=new RouteProjection(r);
        check(p.x[1]==181&&p.x[2]==190,"date line unwrap");check(Double.isFinite(RouteProjection.y(90)),"polar clamp");
        check(Math.abs(RouteProjection.y(60)+75.45612929)<1e-6,"Mercator scale");
        check(Math.abs(p.markerX(r.length,-170)-190)<1e-9,"marker matches route");
        System.out.println("PASS: full Google geometry, signed E7 deltas, schema/mode/distance rejection, waypoints, Mercator/date line/marker");
    }
}
