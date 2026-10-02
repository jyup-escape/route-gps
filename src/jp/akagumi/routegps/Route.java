package jp.akagumi.routegps;

import java.util.*;

/** Pure Java, metres and seconds. Spherical paths handle the date line. */
public final class Route {
    public static final double EARTH = 6371008.8;
    public final double[][] points;
    public final double[] cumulative;
    public final double length;
    public Route(double[][] input, boolean close) {
        if (input.length < 2) throw new IllegalArgumentException("ルートは2地点以上で指定してください");
        List<double[]> clean = new ArrayList<>();
        for (double[] p : input) {
            if (p.length != 2 || !Double.isFinite(p[0]) || !Double.isFinite(p[1]) || Math.abs(p[0])>90 || Math.abs(p[1])>180)
                throw new IllegalArgumentException("緯度は-90〜90、経度は-180〜180で指定してください");
            if (clean.isEmpty() || distance(clean.get(clean.size()-1), p)>0.001) clean.add(p.clone());
        }
        if (clean.size()<2) throw new IllegalArgumentException("異なる地点を2つ以上指定してください");
        if (close && distance(clean.get(0), clean.get(clean.size()-1))>0.001) clean.add(clean.get(0).clone());
        points = clean.toArray(new double[0][]);
        cumulative = new double[points.length];
        for (int i=1;i<points.length;i++) {
            double d=distance(points[i-1], points[i]);
            if (d>Math.PI*EARTH-1) throw new IllegalArgumentException("対蹠点の区間は中間地点を追加してください");
            cumulative[i]=cumulative[i-1]+d;
        }
        length = cumulative[cumulative.length-1];
    }
    public static double distance(double[] a, double[] b) {
        double lat1=Math.toRadians(a[0]), lat2=Math.toRadians(b[0]);
        double s=Math.sin((lat2-lat1)/2), t=Math.sin(Math.toRadians(b[1]-a[1])/2);
        double h=s*s+Math.cos(lat1)*Math.cos(lat2)*t*t;
        return 2*EARTH*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));
    }
    public double[] at(double travelled) {
        double d=Math.max(0,Math.min(length,travelled));
        int idx=Arrays.binarySearch(cumulative,d);
        if(idx>=0) return points[idx].clone();
        int end=-idx-1;
        if(end>=points.length) return points[points.length-1].clone();
        double f=(d-cumulative[end-1])/(cumulative[end]-cumulative[end-1]);
        double[] a=vector(points[end-1]), b=vector(points[end]);
        double angle=(cumulative[end]-cumulative[end-1])/EARTH;
        double wa,wb;
        if(angle<1e-8) {wa=1-f;wb=f;} else {wa=Math.sin((1-f)*angle)/Math.sin(angle);wb=Math.sin(f*angle)/Math.sin(angle);}
        double x=wa*a[0]+wb*b[0], y=wa*a[1]+wb*b[1], z=wa*a[2]+wb*b[2];
        return new double[]{Math.toDegrees(Math.atan2(z,Math.hypot(x,y))),Math.toDegrees(Math.atan2(y,x))};
    }
    private static double[] vector(double[] p) {
        double lat=Math.toRadians(p[0]),lon=Math.toRadians(p[1]);
        return new double[]{Math.cos(lat)*Math.cos(lon),Math.cos(lat)*Math.sin(lon),Math.sin(lat)};
    }
    public float bearing(double d) {
        double[] a=at(Math.min(d,Math.max(0,length-0.1))),b=at(Math.min(length,d+1));
        double lat1=Math.toRadians(a[0]),lat2=Math.toRadians(b[0]),dl=Math.toRadians(b[1]-a[1]);
        return (float)((Math.toDegrees(Math.atan2(Math.sin(dl)*Math.cos(lat2), Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dl)))+360)%360);
    }
    public static double[][] parse(String text) {
        List<double[]> ps=new ArrayList<>();
        int line=0;
        for(int offset=0;offset<text.length();) {
            int end=text.indexOf('\n',offset);if(end<0)end=text.length();
            String raw=text.substring(offset,end);offset=end+1;
            line++;
            String s=raw.trim();
            if(s.isEmpty() || s.startsWith("#")) continue;
            String[] parts=s.split("[,\\s]+",-1);
            if(parts.length!=2) throw new IllegalArgumentException(line+"行目：緯度,経度の形式で入力してください");
            try {ps.add(new double[]{Double.parseDouble(parts[0]),Double.parseDouble(parts[1])});}
            catch(NumberFormatException e) {throw new IllegalArgumentException(line+"行目：座標を数値で入力してください");}
        }
        double[][] result=ps.toArray(new double[0][]);
        new Route(result,false);
        return result;
    }
}
