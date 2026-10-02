import jp.akagumi.routegps.*;
import java.util.*;
import java.io.*;
/** Optional live check against public services, never part of automatic CI. */
public class MapsIntegrationTest {
    public static void main(String[] args)throws Exception{
        String link="https://www.google.com/maps/dir/?api=1&origin=48.87932%2C2.36824&destination=48.87941%2C2.36799&travelmode=walking";
        List<String> places=MapsLink.places(MapsClient.expand(link));
        double[][] ps=new double[places.size()][2];for(int i=0;i<ps.length;i++)ps[i]=MapsLink.coordinate(places.get(i));
        RoadClient.Result result=RoadClient.walking(ps,false);Route r=new Route(Route.parse(result.coordinates),false);
        if(r.length<5||r.length>5000)throw new AssertionError("Unexpected walking route length");
        ByteArrayOutputStream out=new ByteArrayOutputStream();Gpx.write(r,out);
        if(Route.parse(Gpx.read(new ByteArrayInputStream(out.toByteArray()))).length!=r.points.length)throw new AssertionError("GPX export");
        if(MapsClient.search("Gare du Nord, Paris, France").isEmpty())throw new AssertionError("Place search");
        System.out.println("PASS LIVE: Maps URL -> OSRM walking route -> GPX roundtrip; Nominatim place candidates; "+r.points.length+" points, "+r.length+" m");
    }
}
