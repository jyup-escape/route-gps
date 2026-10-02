import android.text.StaticLayout;
import android.text.TextPaint;
import jp.akagumi.routegps.*;
import java.io.*;

/** Off-screen Android regression: exercise the exact text-layout operation that crashed. */
public class RouteLayoutTest {
    public static void main(String[] args)throws Exception {
        double[][] points=new double[328818][2];
        for(int i=0;i<points.length;i++){points[i][0]=25+5*Math.sin(i*0.00002);points[i][1]=-179+358.0*i/(points.length-1);}
        Route route=new Route(points,false);
        String text=RouteStore.editorPreview(route);TextPaint paint=new TextPaint();paint.setTextSize(14);
        StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,460).build();
        if(layout.getLineCount()>15||text.length()>1000)throw new AssertionError("Unbounded text layout");
        File file=new File("/data/local/tmp/routegps-layout-test.bin");
        try{RouteStore.save(file,route,"regression");Route restored=RouteStore.load(file).route;
            if(restored.points.length!=328818||restored.length!=route.length)throw new AssertionError("Geometry lost");
            System.out.println("PASS Android: 328818 full points, "+text.length()+" editor chars, "+layout.getLineCount()+" layout lines, "+file.length()+" saved bytes, max heap "+Runtime.getRuntime().maxMemory());
        }finally{file.delete();}
    }
}
