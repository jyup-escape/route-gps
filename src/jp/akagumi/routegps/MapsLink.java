package jp.akagumi.routegps;

import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Decode endpoints, never treat Google's viewport centre as a route point. */
public final class MapsLink {
    public static String extract(String text) {
        Matcher m=Pattern.compile("https://[^\\s<>]+",Pattern.CASE_INSENSITIVE).matcher(text);
        if(!m.find())throw new IllegalArgumentException("Google Mapsの経路リンクを貼り付けてください");
        String url=m.group();check(url);return url;
    }
    public static URI check(String url) {
        try {
            URI u=new URI(url);String h=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme())||h==null||u.getUserInfo()!=null||u.getPort()!=-1)throw new Exception();
            h=h.toLowerCase(Locale.ROOT);
            if(!(h.equals("maps.app.goo.gl")||h.equals("goo.gl")||h.equals("google.com")||h.equals("www.google.com")||h.equals("maps.google.com")||h.equals("www.google.co.jp")||h.equals("www.google.fr")))throw new Exception();
            if(h.equals("goo.gl")&&!u.getPath().startsWith("/maps/"))throw new Exception();
            return u;
        }catch(Exception e){throw new IllegalArgumentException("対応するGoogle MapsのHTTPSリンクを使ってください");}
    }
    private static String decode(String s)throws Exception{return URLDecoder.decode(s,"UTF-8");}
    public static List<String> places(String url)throws Exception {
        URI u=check(url);Map<String,String> q=new HashMap<>();
        if(u.getRawQuery()!=null)for(String item:u.getRawQuery().split("&")){String[] p=item.split("=",2);q.put(decode(p[0]),p.length==2?decode(p[1]):"");}
        List<String> out=new ArrayList<>();
        if(q.containsKey("origin")||q.containsKey("destination")){
            out.add(q.getOrDefault("origin",""));
            if(q.containsKey("waypoints"))out.addAll(Arrays.asList(q.get("waypoints").split("\\|",-1)));
            out.add(q.getOrDefault("destination",""));
        }else if(q.containsKey("saddr")||q.containsKey("daddr")){
            out.add(q.getOrDefault("saddr",""));out.addAll(Arrays.asList(q.getOrDefault("daddr","").split("\\s+to:",-1)));
        }else{
            String path=u.getRawPath();int start=path.indexOf("/maps/dir/");
            if(start<0)throw new IllegalArgumentException("場所の共有ではなく、出発地と目的地が入った『経路』のリンクを使ってください");
            for(String s:path.substring(start+10).split("/",-1)){
                if(s.startsWith("@")||s.startsWith("data=")||s.startsWith("!"))break;
                out.add(decode(s));
            }
            while(!out.isEmpty()&&out.get(out.size()-1).isEmpty())out.remove(out.size()-1);
        }
        if(out.size()<2)throw new IllegalArgumentException("出発地・目的地を含む2地点以上の経路リンクを使ってください");
        for(String p:out)if(p.trim().isEmpty()||p.equalsIgnoreCase("My Location")||p.equals("現在地"))throw new IllegalArgumentException("『現在地』を具体的な場所に変更してから経路を共有してください");
        // Some shared direction URLs contain exact place coordinates in data=.
        // Match each top-level place block, including empty blocks for path coordinates.
        // A mixed name/coordinate route must not discard its available endpoint coordinates.
        String path=decode(u.getRawPath());int data=path.indexOf("/data=");
        if(data>=0){
            String number="([+-]?[0-9]+(?:\\.[0-9]+)?)";
            Matcher token=Pattern.compile("!([0-9]+)([a-z])([^!]*)").matcher(path.substring(data+6));
            List<String> tokens=new ArrayList<>();while(token.find())tokens.add(token.group());
            List<String> embedded=new ArrayList<>();
            boolean complete=true;
            for(int i=0;i<tokens.size();i++){
                Matcher block=Pattern.compile("!1m([0-9]+)").matcher(tokens.get(i));
                if(!block.matches())continue;
                int count=Integer.parseInt(block.group(1));
                if(count>tokens.size()-i-1||embedded.size()>=out.size()){complete=false;break;}
                StringBuilder body=new StringBuilder();for(int j=1;j<=count;j++)body.append(tokens.get(i+j));
                Matcher point=Pattern.compile("!2m2!1d"+number+"!2d"+number+"(?=!|$)").matcher(body);
                String value=out.get(embedded.size());
                if(point.find()){value=point.group(2)+","+point.group(1);coordinate(value);if(point.find()){complete=false;break;}}
                else if(count!=0||coordinate(value)==null){complete=false;break;}
                embedded.add(value);i+=count;
            }
            if(complete&&embedded.size()==out.size())return embedded;
        }
        return out;
    }
    public static double[] coordinate(String place) {
        if(!place.trim().matches("[+-]?[0-9]+(?:\\.[0-9]+)?\\s*,\\s*[+-]?[0-9]+(?:\\.[0-9]+)?"))return null;
        String[] p=place.trim().split(",");double[] c={Double.parseDouble(p[0]),Double.parseDouble(p[1])};
        if(Math.abs(c[0])>90||Math.abs(c[1])>180)throw new IllegalArgumentException("リンクの座標が範囲外です");return c;
    }
}
