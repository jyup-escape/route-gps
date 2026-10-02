package jp.akagumi.routegps;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class PlaybackService extends Service {
    public static volatile String state="停止中", error="";
    public static volatile double travelled=0, total=0, latitude=0, longitude=0, kmh=0, actualKmh=0;
    public static volatile boolean active=false, paused=false, finished=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<String> owned=new ArrayList<>();
    private LocationManager manager;
    private Route route;
    private WalkSimulation simulation;
    private boolean stopping=false;
    private long last;
    private PowerManager.WakeLock wake;
    private final Runnable tick=new Runnable(){public void run(){
        if(!active)return;
        try {
            long now=SystemClock.elapsedRealtime();
            simulation.advance(Math.min(5,(now-last)/1000.0));
            last=now;
            travelled=simulation.distance;actualKmh=simulation.speed*3.6;
            paused=simulation.paused;finished=simulation.finished;
            String old=state;state=stopping?"停止に向けて減速中":simulation.phase();
            if(!state.equals(old))updateNotification();
            if(stopping && (paused||finished)){cleanup();state="停止中";stopSelf();return;}
            publish();
            handler.postDelayed(this,250);
        } catch(Exception e) { fail(e); }
    }};
    @Override public void onCreate(){super.onCreate();manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("playback","ルート再生",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        if("STOP".equals(action)){
            if(active){stopping=true;simulation.requestPause();state="停止に向けて減速中";updateNotification();}
            else {cleanup();state="停止中";stopSelf();}
            return START_NOT_STICKY;
        }
        if("PAUSE".equals(action) && active && !stopping){
            if(simulation.paused)simulation.resume();else simulation.requestPause();
            paused=simulation.paused;state=simulation.phase();updateNotification();return START_NOT_STICKY;
        }
        if(!"START".equals(action)) {if(!active)stopSelf();return START_NOT_STICKY;}
        cleanup();
        try {
            boolean loop=intent.getBooleanExtra("loop",false);
            String coordinates;
            if(intent.getBooleanExtra("routeFile",false)){
                try(InputStream in=new FileInputStream(new File(getFilesDir(),"playback-route.txt"));ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);coordinates=new String(out.toByteArray(),StandardCharsets.UTF_8);
                }
            }else coordinates=intent.getStringExtra("route");
            route=new Route(Route.parse(coordinates),false);
            kmh=intent.getDoubleExtra("kmh",5);
            String signals=intent.getStringExtra("signals");
            if(intent.getBooleanExtra("signalsFile",false)){
                StringBuilder text=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(new File(getFilesDir(),"playback-signals.txt")),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)text.append(line).append('\n');}signals=text.toString();
            }
            simulation=new WalkSimulation(route,kmh,intent.getDoubleExtra("acceleration",0.5),intent.getDoubleExtra("braking",0.8),
                intent.getDoubleExtra("variation",15),intent.getDoubleExtra("red",50),intent.getDoubleExtra("waitMin",15),intent.getDoubleExtra("waitMax",60),
                WalkSimulation.parseSignals(signals),loop,System.nanoTime());
            travelled=0;total=route.length;actualKmh=0;paused=false;finished=false;stopping=false;error="";state="準備中";
            startForeground(1,notification());
            for(String provider:new String[]{"gps","network","fused"}) {
                try {
                    manager.addTestProvider(provider,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);
                    owned.add(provider);
                    manager.setTestProviderEnabled(provider,true);
                } catch(RuntimeException e) {if(!"fused".equals(provider))throw e;}
            }
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RouteGPS:playback");
            wake.acquire();
            active=true;state="再生中";last=SystemClock.elapsedRealtime();publish();updateNotification();handler.postDelayed(tick,250);
        } catch(Exception e) {fail(e);}
        return START_NOT_STICKY;
    }
    private void publish(){
        double[] p=route.at(travelled); latitude=p[0];longitude=p[1];
        long time=System.currentTimeMillis(),nano=SystemClock.elapsedRealtimeNanos();
        for(String provider:owned){
            Location loc=new Location(provider);loc.setLatitude(latitude);loc.setLongitude(longitude);
            loc.setAltitude(0);loc.setAccuracy(3);loc.setSpeed((float)(actualKmh/3.6));
            loc.setBearing(route.bearing(travelled));loc.setTime(time);loc.setElapsedRealtimeNanos(nano);
            manager.setTestProviderLocation(provider,loc);
        }
    }
    private Notification notification(){
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),flags);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PlaybackService.class).setAction("STOP"),flags);
        PendingIntent pause=PendingIntent.getService(this,2,new Intent(this,PlaybackService.class).setAction("PAUSE"),flags);
        return new Notification.Builder(this,"playback").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Route GPS · "+state).setContentText(String.format(Locale.JAPAN,"巡航速度 %.2f km/h",kmh))
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,paused?"再開":"一時停止",pause).build())
            .addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
    }
    private void updateNotification(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification());}
    private void fail(Exception e){
        error=e instanceof SecurityException?"疑似位置情報の許可が必要です。開発者向けオプションでRoute GPSを選んでください。":e.getMessage();
        if(error==null)error=e.toString();cleanup();state="開始できませんでした";stopSelf();
    }
    private void cleanup(){
        active=false;paused=false;finished=false;actualKmh=0;stopping=false;handler.removeCallbacks(tick);
        for(String provider:owned){try{manager.removeTestProvider(provider);}catch(RuntimeException ignored){}}
        owned.clear();
        if(wake!=null && wake.isHeld())wake.release();wake=null;
        stopForeground(true);
    }
    @Override public void onDestroy(){cleanup();if(error.isEmpty())state="停止中";super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
