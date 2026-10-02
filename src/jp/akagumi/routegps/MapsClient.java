package jp.akagumi.routegps;

import java.net.*;
import java.io.*;
import java.util.*;
import org.json.*;

public final class MapsClient {
    public static String expand(String text)throws Exception {
        String address=MapsLink.extract(text);
        for(int i=0;i<6;i++) {
            URI u=MapsLink.check(address);
            if(!u.getHost().equalsIgnoreCase("maps.app.goo.gl")&&!u.getHost().equalsIgnoreCase("goo.gl"))return address;
            HttpURLConnection c=(HttpURLConnection)u.toURL().openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","RouteGPS/1.1.4 (+https://github.com/jyup-escape/route-gps)");
            try {
                int status=c.getResponseCode();String location=c.getHeaderField("Location");
                if(status<300||status>399||location==null)throw new IOException("短縮リンクを展開できません。ブラウザで開いた後の長い経路URLを貼り付けてください");
                address=u.resolve(location).toString();MapsLink.check(address);
            }finally{c.disconnect();}
        }
        throw new IOException("リンクの転送が多すぎます。長い経路URLを使ってください");
    }
    public static final class Place {
        public final String label; public final double[] point;
        Place(String label,double[] point){this.label=label;this.point=point;}
    }
    private static long lastSearch;
    private static final Map<String,List<Place>> cache=new HashMap<>();
    /** User-triggered search only: one request per second, cached, no autocomplete. */
    public static synchronized List<Place> search(String name)throws Exception {
        if(cache.containsKey(name))return cache.get(name);
        List<Place> out=new ArrayList<>();
        // Mixed-language Google addresses often fail verbatim search. If empty,
        // retry the place name alone; the user still confirms the candidate.
        LinkedHashSet<String> queries=new LinkedHashSet<>();queries.add(name.trim());
        queries.add(name.split(",",2)[0].trim());
        for(String query:queries){
            long delay=1100-(System.currentTimeMillis()-lastSearch);if(delay>0)Thread.sleep(delay);
            lastSearch=System.currentTimeMillis();
            JSONArray a=new JSONArray(RoadClient.request("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q="+URLEncoder.encode(query,"UTF-8"),null));
            for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);out.add(new Place(p.getString("display_name"),new double[]{Double.parseDouble(p.getString("lat")),Double.parseDouble(p.getString("lon"))}));}
            if(!out.isEmpty())break;
        }
        if(out.isEmpty())throw new IOException("場所が見つかりません："+name+"。Google Mapsで座標を指定した経路リンクを使ってください");
        cache.put(name,out);return out;
    }
}
