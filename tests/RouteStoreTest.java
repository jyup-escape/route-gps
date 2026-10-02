import jp.akagumi.routegps.*;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public class RouteStoreTest {
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("route-store-test");File file=directory.resolve("route.bin").toFile();
        try{
            double[][] points=new double[328818][2];
            for(int i=0;i<points.length;i++){points[i][0]=25+5*Math.sin(i*0.00002);points[i][1]=-179+358.0*i/(points.length-1);}
            Route original=new Route(points,false);
            String preview=RouteStore.editorPreview(original);
            if(preview.length()>1000||preview.split("\n").length>10)throw new AssertionError("Full geometry leaked into text editor");
            RouteStore.save(file,original,"Google · 全地点");
            RouteStore.Saved loaded=RouteStore.load(file);
            if(!loaded.label.equals("Google · 全地点")||loaded.route.points.length!=points.length)throw new AssertionError("Missing geometry or label");
            for(int i=0;i<points.length;i++)if(!Arrays.equals(points[i],loaded.route.points[i]))throw new AssertionError("Changed point "+i);
            if(loaded.route.length!=original.length)throw new AssertionError("Changed distance");
            // A failing write must not destroy the last usable route.
            try{RouteStore.save(file,original,new String(new char[70000]).replace('\0','x'));throw new AssertionError("Accepted oversized UTF label");}catch(UTFDataFormatException expected){}
            if(RouteStore.load(file).route.points.length!=points.length)throw new AssertionError("Failed write replaced old route");
            Route small=new Route(new double[][]{{0,0},{0,1}},false);RouteStore.save(file,small,"replacement");
            if(RouteStore.load(file).route.points.length!=2)throw new AssertionError("Replacement failed");
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(file))){out.writeInt(0x52475031);out.writeUTF("");out.writeInt(Integer.MAX_VALUE);}
            try{RouteStore.load(file);throw new AssertionError("Accepted truncated route");}catch(IOException expected){}
            System.out.println("PASS: 328818 full points, bounded editor preview, exact binary roundtrip, atomic failed write, replacement, corrupt length");
        }finally{for(File item:directory.toFile().listFiles())item.delete();Files.delete(directory);}
    }
}
