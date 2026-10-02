package jp.akagumi.routegps;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** User-triggered OSRM foot routing and OSM signal discovery; no requests during playback. */
public final class RoadClient {
    public static final class Result {
        public String coordinates,signals,warning="";
    }
    public interface Progress { void report(String message); }
    public static Result walking(double[][] waypoints,boolean loop)throws Exception{return walking(waypoints,loop,m->{});}
    public static Result walking(double[][] waypoints,boolean loop,Progress progress)throws Exception{
        new Route(waypoints,false);
        List<double[]> stops=new ArrayList<>(Arrays.asList(waypoints));
        if(loop&&Route.distance(stops.get(0),stops.get(stops.size()-1))>0.001)stops.add(stops.get(0));
        List<double[]> joined=new ArrayList<>();
        for(int start=0;start<stops.size()-1;){
            int end=Math.min(stops.size()-1,start+24);
            progress.report("道路検索：経由地 "+(start+1)+"〜"+(end+1)+" / "+stops.size());
            StringBuilder coords=new StringBuilder();
            for(int i=start;i<=end;i++){double[] p=stops.get(i);if(coords.length()>0)coords.append(';');coords.append(p[1]).append(',').append(p[0]);}
            String url="https://routing.openstreetmap.de/routed-foot/route/v1/driving/"+coords+"?overview=full&geometries=geojson&steps=false&generate_hints=false";
            JSONObject response=new JSONObject(request(url,null));
            if(!"Ok".equals(response.optString("code")))throw new IOException("徒歩ルートが見つかりません："+response.optString("code"));
            JSONArray geometry=response.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
            for(int i=0;i<geometry.length();i++){JSONArray p=geometry.getJSONArray(i);double[] point={p.getDouble(1),p.getDouble(0)};
                if(i==0&&!joined.isEmpty()&&Route.distance(joined.get(joined.size()-1),point)>5)throw new IOException("道路検索の区間が接続しません。境界の経由地を道路上に移動してください。");
                joined.add(point);
            }
            start=end;if(start<stops.size()-1)Thread.sleep(2000);
        }
        Route route=new Route(joined.toArray(new double[0][]),false);
        Result result=new Result();result.coordinates=format(route);result.signals="";
        if(route.length>20000)result.warning="長い徒歩ルートを作成しました。信号は未取得です。『このルートの信号を取得・再試行』で全区間を順番に取得できます。";
        else try{result.signals=signals(route,progress);}catch(Exception e){result.warning="徒歩ルートは作成できましたが、信号取得に失敗しました。『信号を取得』で再試行してください。\n"+e.getMessage();}
        return result;
    }
    public static String format(Route route){StringBuilder s=new StringBuilder();for(double[] p:route.points)s.append(p[0]).append(',').append(p[1]).append('\n');return s.toString();}
    public static String signals(Route route)throws Exception{return signals(route,m->{});}
    public static String signals(Route route,Progress progress)throws Exception{
        List<Double> found=new ArrayList<>();
        for(double start=0;start<route.length;){
            RouteWindow window=new RouteWindow(route,start);
            progress.report(String.format(Locale.JAPAN,"信号取得：%.1f / %.1f km",start/1000,route.length/1000));
            StringBuilder line=new StringBuilder();
            for(double[] p:window.points){if(line.length()>0)line.append(',');line.append(p[0]).append(',').append(p[1]);}
            String query="[out:json][timeout:25];node(around:20,"+line+")(if: t[\"highway\"] == \"traffic_signals\" || t[\"crossing\"] == \"traffic_signals\" || t[\"crossing:signals\"] == \"yes\");out body;";
            JSONObject response=new JSONObject(request("https://overpass-api.de/api/interpreter","data="+URLEncoder.encode(query,"UTF-8")));
            if(response.has("remark"))throw new IOException("信号データが完全に取得できませんでした："+response.optString("remark"));
            JSONArray nodes=response.getJSONArray("elements");
            for(int i=0;i<nodes.length();i++){
                JSONObject node=nodes.getJSONObject(i);double[] p={node.getDouble("lat"),node.getDouble("lon")};double lastMatch=-100;
                for(int j=1;j<window.points.length;j++){
                    double[] match=project(p,window.points[j-1],window.points[j]);if(match[1]>15)continue;
                    double d=window.distances[j-1]+match[0]*(window.distances[j]-window.distances[j-1]);
                    if(d>3&&d<route.length-1&&d-lastMatch>25){found.add(d-2);lastMatch=d;}
                }
            }
            start=window.end;if(start<route.length)Thread.sleep(2000);
        }
        Collections.sort(found);StringBuilder text=new StringBuilder();double previous=-100;
        for(double d:found){if(d-previous<25)continue;if(text.length()>0)text.append('\n');text.append(String.format(Locale.US,"%.2f",d));previous=d;}
        return text.toString();
    }
    /** Fraction on segment and perpendicular offset in metres; suitable for short road edges. */
    static double[] project(double[] p,double[] a,double[] b){
        double cos=Math.cos(Math.toRadians(p[0])),m=Route.EARTH*Math.PI/180;
        double ax=wrap(a[1]-p[1])*cos*m,ay=(a[0]-p[0])*m;
        double bx=wrap(b[1]-p[1])*cos*m,by=(b[0]-p[0])*m;
        double dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
        double t=den==0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/den));
        return new double[]{t,Math.hypot(ax+t*dx,ay+t*dy)};
    }
    private static double wrap(double d){return (d+540)%360-180;}
    static String request(String address,String post)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
        conn.setConnectTimeout(15000);conn.setReadTimeout(35000);conn.setRequestProperty("User-Agent","RouteGPS/1.1.5 (+https://github.com/jyup-escape/route-gps)");
        try{
            if(post!=null){conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream out=conn.getOutputStream()){out.write(post.getBytes(StandardCharsets.UTF_8));}}
            int status=conn.getResponseCode();if(status!=200)throw new IOException("通信エラー HTTP "+status+"。時間をおいて再試行してください。");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);}
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{conn.disconnect();}
    }
}
