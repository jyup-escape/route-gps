import jp.akagumi.routegps.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class GpxTest {
    static String read(String s)throws Exception{return Gpx.read(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));}
    static void rejects(String s)throws Exception{try{read(s);throw new AssertionError("Accepted invalid GPX");}catch(Exception expected){if(expected instanceof RuntimeException&&expected.getMessage()==null)throw expected;}}
    public static void main(String[] args)throws Exception{
        double[][] ps=new double[100000][2];for(int i=0;i<ps.length;i++)ps[i]=new double[]{48.8+i*0.0000001,2.3};
        Route r=new Route(ps,false);ByteArrayOutputStream out=new ByteArrayOutputStream();Gpx.write(r,out);
        Route back=new Route(Route.parse(Gpx.read(new ByteArrayInputStream(out.toByteArray()))),false);
        if(back.points.length!=100000||Math.abs(back.length-r.length)>0.01)throw new AssertionError("large roundtrip");
        String prefix="<gpx><trk><trkseg>";StringBuilder over=new StringBuilder(prefix);
        for(int i=0;i<100001;i++)over.append("<trkpt lat='48' lon='2'/>");over.append("</trkseg></trk></gpx>");rejects(over.toString());
        rejects("<!DOCTYPE gpx [<!ENTITY a 'boom'>]><gpx><rte><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></rte></gpx>");
        rejects("<gpx><trk><trkseg/><trkseg/></trk></gpx>");rejects("<html><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></html>");
        if(Route.parse(read("<gpx><rte><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></rte></gpx>")).length!=2)throw new AssertionError();
        System.out.println("PASS: 100000-point GPX roundtrip, over-limit, DTD rejection, GPX validation, route import");
    }
}
