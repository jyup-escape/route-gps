package jp.akagumi.routegps;

import java.io.*;
import java.nio.file.*;

/** Full geometry on disk; only a short summary belongs in Android's text editor. */
public final class RouteStore {
    public static final class Saved {
        public final Route route;public final String label;
        Saved(Route route,String label){this.route=route;this.label=label;}
    }
    public static String editorPreview(Route route){
        StringBuilder s=new StringBuilder("# 読み込み済み："+route.points.length+"地点（再生・GPX保存は全地点）\n# ここを編集すると経由地の入力に切り替わります\n");
        int n=route.points.length;
        for(int i=0;i<Math.min(3,n);i++)s.append(route.points[i][0]).append(',').append(route.points[i][1]).append('\n');
        if(n>6)s.append("# … 中間地点は内部ファイルに保持 …\n");
        for(int i=Math.max(3,n-3);i<n;i++)s.append(route.points[i][0]).append(',').append(route.points[i][1]).append('\n');
        return s.toString();
    }
    public static void save(File destination,Route route,String label)throws IOException{
        File temporary=File.createTempFile("route-", ".tmp",destination.getParentFile());
        try{
            try(FileOutputStream file=new FileOutputStream(temporary);DataOutputStream out=new DataOutputStream(new BufferedOutputStream(file))){
                out.writeInt(0x52475031);out.writeUTF(label);out.writeInt(route.points.length);
                for(double[] p:route.points){out.writeDouble(p[0]);out.writeDouble(p[1]);}out.flush();file.getFD().sync();
            }
            try{Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{temporary.delete();}
    }
    public static Saved load(File source)throws IOException{
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(source)))){
            if(in.readInt()!=0x52475031)throw new IOException("保存ルートの形式が不正です");
            String label=in.readUTF();int count=in.readInt();
            if(count<2||source.length()<16L*count+10)throw new IOException("保存ルートが不完全です");
            double[][] points=new double[count][2];for(int i=0;i<count;i++){points[i][0]=in.readDouble();points[i][1]=in.readDouble();}
            if(in.read()!=-1)throw new IOException("保存ルートに余分なデータがあります");
            try{return new Saved(new Route(points,false),label);}catch(IllegalArgumentException e){throw new IOException("保存ルートの座標が不正です",e);}
        }
    }
}
