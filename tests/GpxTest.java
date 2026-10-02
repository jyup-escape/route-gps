import jp.akagumi.routegps.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class GpxTest {
    static String read(String s)throws Exception{return Gpx.read(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));}
    static void rejects(String s)throws Exception{try{read(s);throw new AssertionError("Accepted invalid GPX");}catch(Exception expected){if(expected instanceof RuntimeException&&expected.getMessage()==null)throw expected;}}
    public static void main(String[] args)throws Exception{
        double[][] ps=new double[300001][2];for(int i=0;i<ps.length;i++)ps[i]=new double[]{0,((i*360.0/300000+180)%360)-180};
        Route r=new Route(ps,false);ByteArrayOutputStream out=new ByteArrayOutputStream();Gpx.write(r,out);
        Route back=new Route(Route.parse(Gpx.read(new ByteArrayInputStream(out.toByteArray()))),false);
        if(back.points.length!=300001||Math.abs(back.length-r.length)>0.01)throw new AssertionError("large roundtrip");
        if(Math.abs(r.length-2*Math.PI*Route.EARTH)>0.1)throw new AssertionError("world circumference");
        double[] opposite=back.at(back.length/2);if(Math.abs(Math.abs(opposite[1])-180)>1e-5)throw new AssertionError("world interpolation");
        double start=0;int windows=0;while(start<back.length){RouteWindow w=new RouteWindow(back,start);if(w.end<=start||w.points.length>256||w.end-start>10000.001)throw new AssertionError("window coverage");start=w.end;windows++;}
        if(windows<4000)throw new AssertionError("world windows");
        WalkSimulation sim=new WalkSimulation(back,360000000,1e12,1e12,0,0,0,0,new double[0],false,1);
        sim.advance(2);if(!sim.finished||Math.abs(sim.distance-back.length)>0.01)throw new AssertionError("world playback");
        // Stream a document larger than 50MB without allocating its padding.
        byte[] comment=("<!--"+new String(new char[1024]).replace('\0','x')+"-->").getBytes(StandardCharsets.UTF_8);
        InputStream padding=new InputStream(){long left=comment.length*52000L;public int read(){return left==0?-1:comment[(int)((comment.length*52000L-left--)%comment.length)]&255;}
            public int read(byte[] b,int off,int len){if(left==0)return -1;int n=(int)Math.min(len,left);for(int i=0;i<n;i++)b[off+i]=(byte)read();return n;}};
        String small="<rte><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></rte></gpx>";
        InputStream big=new SequenceInputStream(new ByteArrayInputStream("<gpx>".getBytes(StandardCharsets.UTF_8)),new SequenceInputStream(padding,new ByteArrayInputStream(small.getBytes(StandardCharsets.UTF_8))));
        if(Route.parse(Gpx.read(big)).length!=2)throw new AssertionError("50MB+ streaming import");
        StringBuilder signals=new StringBuilder();for(int i=1;i<=10001;i++)signals.append(i*30).append('\n');
        if(WalkSimulation.parseSignals(signals.toString()).length!=10001)throw new AssertionError("signal count");
        rejects("<!DOCTYPE gpx [<!ENTITY a 'boom'>]><gpx><rte><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></rte></gpx>");
        rejects("<gpx><trk><trkseg/><trkseg/></trk></gpx>");rejects("<html><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></html>");
        if(Route.parse(read("<gpx><rte><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></rte></gpx>")).length!=2)throw new AssertionError();
        System.out.println("PASS: 300001-point Earth lap GPX, interpolation, 4000+ windows, playback, 50MB+ import, 10001 signals, DTD validation");
    }
}
