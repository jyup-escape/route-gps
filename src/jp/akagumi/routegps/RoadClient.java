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
    public static Result walking(double[][] waypoints,boolean loop)throws Exception{
        if(waypoints.length>25)throw new IOException("徒歩ルート作成は2〜25地点です。始点・経由地・終点を入力してください。GPXの細かい形状を再検索する必要はありません。");
        StringBuilder coords=new StringBuilder();
        for(double[] p:waypoints){if(coords.length()>0)coords.append(';');coords.append(p[1]).append(',').append(p[0]);}
        if(loop && Route.distance(waypoints[0],waypoints[waypoints.length-1])>0.001)coords.append(';').append(waypoints[0][1]).append(',').append(waypoints[0][0]);
        String url="https://routing.openstreetmap.de/routed-foot/route/v1/driving/"+coords+"?overview=full&geometries=geojson&steps=false&generate_hints=false";
        JSONObject response=new JSONObject(request(url,null));
        if(!"Ok".equals(response.optString("code")))throw new IOException("徒歩ルートが見つかりません："+response.optString("code"));
        JSONArray geometry=response.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
        if(geometry.length()>Route.MAX_POINTS)throw new IOException("ルートが長すぎます。100000地点以内になる短い区間で作成してください。");
        double[][] points=new double[geometry.length()][2];
        for(int i=0;i<points.length;i++){JSONArray p=geometry.getJSONArray(i);points[i]=new double[]{p.getDouble(1),p.getDouble(0)};}
        Route route=new Route(points,false);
        Result result=new Result();result.coordinates=format(route);result.signals="";
        try{result.signals=signals(route);}catch(Exception e){result.warning="徒歩ルートは作成できましたが、信号取得に失敗しました。信号なしの状態です。『信号を取得』で再試行するか距離を手入力してください。\n"+e.getMessage();}
        return result;
    }
    public static String format(Route route){StringBuilder s=new StringBuilder();for(double[] p:route.points)s.append(p[0]).append(',').append(p[1]).append('\n');return s.toString();}
    public static String signals(Route route)throws Exception{
        if(route.length>20000)throw new IOException("信号取得は20km以内のルートに対応しています");
        StringBuilder line=new StringBuilder();
        for(double[] p:route.points){if(line.length()>0)line.append(',');line.append(p[0]).append(',').append(p[1]);}
        String area="(around:20,"+line+")";
        String query="[out:json][timeout:25];node"+area+"(if: t[\"highway\"] == \"traffic_signals\" || t[\"crossing\"] == \"traffic_signals\" || t[\"crossing:signals\"] == \"yes\");out body;";
        JSONObject response=new JSONObject(request("https://overpass-api.de/api/interpreter","data="+URLEncoder.encode(query,"UTF-8")));
        if(response.has("remark"))throw new IOException("信号データが完全に取得できませんでした："+response.optString("remark"));
        JSONArray nodes=response.getJSONArray("elements");List<Double> found=new ArrayList<>();
        for(int i=0;i<nodes.length();i++){
            JSONObject node=nodes.getJSONObject(i);
            double[] p={node.getDouble("lat"),node.getDouble("lon")};
            // A node may be encountered multiple times on an out-and-back route.
            double lastMatch=-100;
            for(int j=1;j<route.points.length;j++){
                double[] match=project(p,route.points[j-1],route.points[j]);
                if(match[1]>15)continue;
                double d=route.cumulative[j-1]+match[0]*(route.cumulative[j]-route.cumulative[j-1]);
                if(d>3 && d<route.length-1 && d-lastMatch>25){found.add(d-2);lastMatch=d;}
            }
        }
        Collections.sort(found);StringBuilder text=new StringBuilder();double previous=-100;int count=0;
        for(double d:found){if(d-previous<25)continue;if(++count>500)throw new IOException("信号は最大500地点です。短いルートに分割してください。");if(text.length()>0)text.append('\n');text.append(String.format(Locale.US,"%.2f",d));previous=d;}
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
        conn.setConnectTimeout(15000);conn.setReadTimeout(35000);conn.setRequestProperty("User-Agent","RouteGPS/1.1.3 (+https://github.com/jyup-escape/route-gps)");
        try{
            if(post!=null){conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream out=conn.getOutputStream()){out.write(post.getBytes(StandardCharsets.UTF_8));}}
            int status=conn.getResponseCode();if(status!=200)throw new IOException("通信エラー HTTP "+status+"。時間をおいて再試行してください。");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>50*1024*1024)throw new IOException("応答サイズが大きすぎます");}
                return new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
        }finally{conn.disconnect();}
    }
}
