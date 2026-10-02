package jp.akagumi.routegps;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.xmlpull.v1.*;
import java.util.*;

public final class Gpx {
    public static String read(InputStream raw)throws Exception {
        XmlPullParserFactory f=XmlPullParserFactory.newInstance();f.setNamespaceAware(true);XmlPullParser x=f.newPullParser();x.setInput(raw,null);
        List<double[]> track=new ArrayList<>(),rte=new ArrayList<>();int segments=0,routes=0;boolean isGpx=false;
        for(int event=x.getEventType();event!=XmlPullParser.END_DOCUMENT;event=x.nextToken()){
            if(event==XmlPullParser.DOCDECL)throw new IOException("DTD付きのGPXは読み込めません");
            if(event!=XmlPullParser.START_TAG)continue;
            String name=x.getName();if("gpx".equals(name)&&x.getDepth()==1)isGpx=true;
            if("trkseg".equals(name))segments++;if("rte".equals(name))routes++;
            if("trkpt".equals(name)||"rtept".equals(name)){
                List<double[]> dest="trkpt".equals(name)?track:rte;
                dest.add(new double[]{Double.parseDouble(x.getAttributeValue(null,"lat")),Double.parseDouble(x.getAttributeValue(null,"lon"))});
            }
        }
        if(!isGpx)throw new IOException("GPX形式のファイルを選んでください");
        List<double[]> ps=track.isEmpty()?rte:track;
        if((!track.isEmpty()&&segments>1)||(track.isEmpty()&&routes>1))throw new IOException("1つのトラック区間またはルートを含むGPXを使ってください");
        Route r=new Route(ps.toArray(new double[0][]),false);
        StringBuilder out=new StringBuilder();for(double[] p:r.points)out.append(p[0]).append(',').append(p[1]).append('\n');return out.toString();
    }
    public static void write(Route route,OutputStream out)throws IOException {
        Writer w=new OutputStreamWriter(out,StandardCharsets.UTF_8);
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Route GPS\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Route GPS walking route</name><trkseg>\n");
        for(double[] p:route.points)w.write("<trkpt lat=\""+p[0]+"\" lon=\""+p[1]+"\"/>\n");
        w.write("</trkseg></trk></gpx>\n");w.flush();
    }
}
