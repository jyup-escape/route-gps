package jp.akagumi.routegps;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Reads Google's web directions geometry. Undocumented schema: fail closed on drift. */
public final class GoogleRoute {
    public static final class Candidate {
        public final Route route;
        public final double reportedDistance;
        public final String name;
        Candidate(Route route,double reportedDistance,String name){this.route=route;this.reportedDistance=reportedDistance;this.name=name;}
        public String label(){return String.format(Locale.JAPAN,"Google %.1f km · %d地点\n%s",reportedDistance/1000,route.points.length,name);}
    }
    // Protobuf text uses descendant counts, not child counts.
    private static final class Pb {
        final String wire;final int tokens;
        Pb(String wire,int tokens){this.wire=wire;this.tokens=tokens;}
    }
    private static Pb field(int n,char type,Object value){return new Pb("!"+n+type+value,1);}
    private static Pb group(int n,Pb... children){StringBuilder b=new StringBuilder();int count=0;for(Pb p:children){b.append(p.wire);count+=p.tokens;}return new Pb("!"+n+"m"+count+b,count+1);}
    public static int mode(String url)throws Exception{
        URI u=MapsLink.check(url);String raw=u.toString();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("!3e([0-9]+)").matcher(raw);
        int mode=2;while(m.find())mode=Integer.parseInt(m.group(1));
        String query=u.getRawQuery();if(query!=null)for(String item:query.split("&")){
            String[] parts=item.split("=",2);if(parts.length!=2||!parts[0].equals("travelmode"))continue;
            String value=URLDecoder.decode(parts[1],"UTF-8");
            if(value.equals("walking"))mode=2;else if(value.equals("driving"))mode=0;else if(value.equals("bicycling"))mode=1;else throw new IOException("徒歩・自転車・車の経路リンクを使ってください。電車の道筋には未対応です。");
        }
        if(mode<0||mode>2)throw new IOException("徒歩・自転車・車の経路リンクを使ってください。電車の道筋には未対応です。");
        if(URLDecoder.decode(raw,"UTF-8").contains("!3m4!1m2"))throw new IOException("ドラッグで変更した経路の通過点を確実に読み取れません。道筋を含むGPXを読み込むか、通過点を通常の経由地にして共有してください。");
        return mode;
    }
    public static String requestUrl(List<String> places,int mode)throws Exception{
        StringBuilder pb=new StringBuilder();
        if(places.size()<2||mode<0||mode>2)throw new IOException("経路と移動手段を確認してください");
        for(String place:places){double[] p=MapsLink.coordinate(place);if(p==null)throw new IOException("座標を読み取れない地点があります："+place+"。Google Mapsで地図上の地点を指定して経路を共有してください。");Pb location=group(3,field(3,'d',p[0]),field(4,'d',p[1]));pb.append(group(1,location,field(6,'e',2)).wire);}
        Pb screen=group(3,group(1,field(1,'d',24960.741896132306),field(2,'d',0),field(3,'d',0)),group(2,field(1,'f',0),field(2,'f',0),field(3,'f',0)),group(3,field(1,'i',1024),field(2,'i',768)),field(4,'f',13.1));
        // Keep explicit web feature flags separate from route constraints.
        List<Pb> features=new ArrayList<>();features.add(field(32,'i',1));features.add(field(49,'b',1));features.add(group(63));
        for(int n:new int[]{66,85,114,149,206,209,212,216,222,223,232,234,235,244,246,250,253,260,266,270,273,279,281})features.add(field(n,'b',1));features.add(group(291));
        List<Pb> settings=new ArrayList<>();
        settings.add(group(1,field(18,'b',1),field(30,'b',1),group(31,field(1,'b',1)),field(34,'e',1)));
        settings.add(group(2,group(5,field(6,'e',2)),field(20,'e',3),field(39,'b',1)));
        settings.add(group(6,features.toArray(new Pb[0])));
        for(int n:new int[]{10,12,13,14,16})settings.add(field(n,'b',1));settings.add(group(17,field(3,'e',1)));
        settings.add(group(20,field(1,'e',mode),field(2,'e',3),field(5,'e',2),field(6,'b',1),field(14,'b',1)));
        settings.add(group(46,field(1,'b',0)));settings.add(field(96,'b',1));settings.add(field(99,'b',1));
        pb.append(screen.wire).append(group(6,settings.toArray(new Pb[0])).wire).append(field(15,'i',10142).wire);
        return "https://www.google.com/maps/preview/directions?hl=ja&pb="+URLEncoder.encode(pb.toString(),"UTF-8");
    }
    public static List<Candidate> fetch(String expanded)throws Exception{
        int mode=mode(expanded);List<String> places=MapsLink.places(expanded);
        List<Candidate> candidates=parse(RoadClient.request(requestUrl(places,mode),null),mode);
        double[] start=MapsLink.coordinate(places.get(0)),end=MapsLink.coordinate(places.get(places.size()-1));
        for(Candidate candidate:candidates){Route r=candidate.route;
            if((start!=null&&Route.distance(start,r.points[0])>200)||(end!=null&&Route.distance(end,r.points[r.points.length-1])>200))throw new IOException("共有リンクの始点・終点とGoogleの道筋が一致しません。取り込みを中止しました。");
        }
        return candidates;
    }
    public static List<Candidate> parse(String raw,int expectedMode)throws Exception{
        String text=raw.trim();if(text.startsWith(")]}'")){int line=text.indexOf('\n');if(line<0)throw new IOException("Googleの道筋データが不完全です");text=text.substring(line+1);}
        JSONArray data=new JSONArray(text).getJSONArray(0);
        JSONArray summaries=data.getJSONArray(1),geometry=data.getJSONArray(7);
        if(summaries.length()!=geometry.length()||summaries.length()==0)throw new IOException("Googleの道筋を取得できませんでした。別ルートへの再計算は行いません。");
        List<Candidate> out=new ArrayList<>();
        for(int i=0;i<summaries.length();i++){
            JSONArray summary=summaries.getJSONArray(i).getJSONArray(0);
            if(summary.getInt(0)!=expectedMode)throw new IOException("Googleが異なる移動手段を返しました。取り込みを中止しました。");
            double reported=summary.getJSONArray(2).getDouble(0);
            JSONArray node=geometry.getJSONArray(i),lat=node.getJSONArray(0),lon=node.getJSONArray(1);
            if(lat.length()!=lon.length()||lat.length()<2)throw new IOException("Googleの道筋が欠落しています");
            double[][] ps=new double[lat.length()][2];long a=0,b=0;
            for(int j=0;j<ps.length;j++){
                double da=lat.getDouble(j),db=lon.getDouble(j);
                if(!Double.isFinite(da)||!Double.isFinite(db)||da!=Math.rint(da)||db!=Math.rint(db)||Math.abs(da)>3600000000L||Math.abs(db)>3600000000L)throw new IOException("Googleの道筋座標が不正です");
                a=Math.addExact(a,(long)da);b=Math.addExact(b,(long)db);ps[j][0]=a/1e7;ps[j][1]=b/1e7;
            }
            Route route=new Route(ps,false);
            if(!Double.isFinite(reported)||reported<=0||Math.abs(route.length-reported)>Math.max(100,reported*0.03))throw new IOException("Googleの表示距離と道筋が一致しません。取り込みを中止しました。");
            out.add(new Candidate(route,reported,summary.optString(1,"ルート "+(i+1))));
        }
        return out;
    }
}
