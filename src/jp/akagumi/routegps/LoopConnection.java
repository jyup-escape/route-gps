package jp.akagumi.routegps;

import java.util.Arrays;

/** Adds only a verified road connector, leaving the imported geometry intact. */
public final class LoopConnection {
    public static double gap(Route route){return Route.distance(route.points[route.points.length-1],route.points[0]);}
    public static void checkGap(Route route){
        if(gap(route)>100)throw new IllegalArgumentException("始点と終点が100mより離れています。始点に戻るルートを読み込んでください。");
    }
    public static Route join(Route route,Route connector){
        checkGap(route);
        if(Route.distance(route.points[route.points.length-1],connector.points[0])>5
                ||Route.distance(route.points[0],connector.points[connector.points.length-1])>5)
            throw new IllegalArgumentException("始点・終点に接続する道路を確認できません。道路上の座標で作り直してください。");
        double[][] points=Arrays.copyOf(route.points,route.points.length+connector.points.length+1);
        System.arraycopy(connector.points,0,points,route.points.length,connector.points.length);
        points[points.length-1]=route.points[0];
        return new Route(points,false);
    }
}
