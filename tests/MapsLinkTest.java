import jp.akagumi.routegps.*;
import java.util.*;
public class MapsLinkTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    static void rejects(String s)throws Exception{try{MapsLink.places(s);throw new AssertionError(s);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        List<String> p=MapsLink.places("https://www.google.com/maps/dir/?api=1&origin=48.1%2C2.1&destination=48.3%2C2.3&waypoints=48.2%2C2.2%7CParis+France");
        check(p.size()==4&&p.get(2).equals("Paris France"));check(MapsLink.coordinate(p.get(0))[0]==48.1);
        p=MapsLink.places("https://www.google.com/maps/dir/Paris+France/Lyon+France/@1,2,3z/data=!abc");check(p.equals(Arrays.asList("Paris France","Lyon France")));
        p=MapsLink.places("https://maps.google.com/maps?saddr=Tokyo&daddr=Osaka+to:Kyoto");check(p.size()==3);
        rejects("https://www.google.com/maps/dir//Paris/");rejects("https://www.google.com/maps/dir/?origin=My+Location&destination=Paris");
        rejects("https://www.google.com/maps/@48,2,10z");rejects("https://google.com.evil.test/maps/dir/A/B");rejects("http://www.google.com/maps/dir/A/B");
        rejects("https://www.google.com@evil.test/maps/dir/A/B");rejects("https://www.google.com:8443/maps/dir/A/B");
        check(MapsLink.coordinate("Paris France")==null);check(MapsLink.extract("経路 https://maps.app.goo.gl/abc\n").equals("https://maps.app.goo.gl/abc"));
        String data="!4m14!4m13!1m5!1m1!1sstation!2m2!1d11.0818138!2d49.446407!1m5!1m1!1smetro!2m2!1d2.365158!2d48.881203!3e2";
        p=MapsLink.places("https://www.google.com/maps/dir/Nuremberg+Central+Station/Louis+Blanc/@1,2,15z/data="+data);
        check(p.equals(Arrays.asList("49.446407,11.0818138","48.881203,2.365158")));
        p=MapsLink.places("https://www.google.com/maps/dir/A/B/C/data="+data);check(p.equals(Arrays.asList("A","B","C")));
        p=MapsLink.places("https://www.google.com/maps/dir/A/B/@49,11,15z/");check(p.equals(Arrays.asList("A","B")));
        System.out.println("PASS: Maps URL endpoints, ordering, path URLs, blank origin, viewport rejection, host validation");
    }
}
