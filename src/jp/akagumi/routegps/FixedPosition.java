package jp.akagumi.routegps;

public final class FixedPosition {
    public static double[] parse(String text){
        try{
            String[] parts=text.trim().split("[,\\s]+");
            if(parts.length!=2)throw new IllegalArgumentException();
            double lat=Double.parseDouble(parts[0]),lon=Double.parseDouble(parts[1]);
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException();
            return new double[]{lat,lon};
        }catch(Exception e){throw new IllegalArgumentException("静止位置は緯度,経度で入力してください（緯度 -90〜90、経度 -180〜180）。");}
    }
}
