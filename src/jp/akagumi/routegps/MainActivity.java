package jp.akagumi.routegps;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.xmlpull.v1.*;

public class MainActivity extends Activity {
    private static final int ACCENT=Color.rgb(0,107,88), INK=Color.rgb(23,35,42);
    private static final String DEMO="35.681236,139.767125\n35.682000,139.767125\n35.682000,139.768000\n35.681236,139.768000\n35.681236,139.767125";
    private EditText routeInput,speed,acceleration,braking,variation,redChance,waitMin,waitMax,signalInput;
    private CheckBox loop;
    private TextView status,detail,summary,routeSource;
    private Button start,pause,stop,importButton,roadButton,signalButton;
    private Preview preview;
    private Handler handler=new Handler();
    private boolean importing=false;
    private String preparedText="",sourceLabel="";
    private long lastNetwork=0;
    private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,500);}};
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(INK);v.setPadding(0,dp(8),0,dp(8));return v;}
    private void section(LinearLayout parent,String title){TextView t=text(title,18);t.setTypeface(null,Typeface.BOLD);parent.addView(t);}
    private Button button(String title,LinearLayout parent){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(48));parent.addView(b);return b;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(28));scroll.addView(body);setContentView(scroll);
        TextView title=text("Route GPS",28);title.setTypeface(null,Typeface.BOLD);body.addView(title);
        body.addView(text("道に沿って歩く。信号待ちも、歩く速さも自然に。",14));
        status=text("停止中",20);status.setTextColor(ACCENT);body.addView(status);
        detail=text("ルートと速度を確認して再生してください。",14);body.addView(detail);
        section(body,"ルート");
        preview=new Preview();body.addView(preview,new LinearLayout.LayoutParams(-1,dp(180)));
        body.addView(text("ルート形状のプレビュー（道路地図ではありません）",12));
        summary=text("",14);body.addView(summary);
        routeSource=text("",14);body.addView(routeSource);
        importButton=button("GPXファイルを読み込む",body);importButton.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,20);});
        body.addView(text("始点・経由地・終点を緯度,経度で入力（2〜25地点）",14));
        routeInput=new EditText(this);routeInput.setTextSize(14);routeInput.setTypeface(Typeface.MONOSPACE);routeInput.setGravity(Gravity.TOP);routeInput.setMinLines(4);routeInput.setMaxLines(7);
        routeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        routeInput.setContentDescription("ルート座標：各行に緯度,経度");body.addView(routeInput);
        roadButton=button("道に沿う徒歩ルートを作成・信号を取得",body);
        roadButton.setOnClickListener(v->fetchRoad(false));
        body.addView(text("作成時に座標をOSRMへ、信号取得時にルートをOverpassへ送信します。ルート生成：OSRM / FOSSGIS · 地図データ © OpenStreetMap contributors（ODbL）",12));
        TextView attribution=text("データとサービスについて ↗",12);attribution.setTextColor(ACCENT);attribution.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://routing.openstreetmap.de/about.html"))));body.addView(attribution);
        TextView osmLicense=text("OpenStreetMapのライセンス ↗",12);osmLicense.setTextColor(ACCENT);osmLicense.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))));body.addView(osmLicense);
        TextView fixMap=text("地図データの修正 ↗",12);fixMap.setTextColor(ACCENT);fixMap.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org"))));body.addView(fixMap);
        section(body,"速度・繰り返し");
        body.addView(text("巡航速度（km/h）· 0.1以上、上限なし",14));
        speed=new EditText(this);speed.setSingleLine(true);speed.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);speed.setContentDescription("移動速度、単位km/h");body.addView(speed);
        loop=new CheckBox(this);loop.setText("周回する（ONにして徒歩ルートを作成）");loop.setMinHeight(dp(48));body.addView(loop);
        section(body,"歩き方");
        acceleration=number(body,"歩き始めの加速度（m/s²）· 上限なし", "0.5");
        braking=number(body,"止まるときの減速度（m/s²）· 上限なし", "0.8");
        variation=number(body,"巡航速度のゆらぎ（±%）· 上限なし", "15");
        body.addView(text("小さい加減速度ほどゆっくり変化します。速さの目標は6〜14秒ごとに変わり、曲がり角でも控えめに減速します。",12));
        section(body,"信号待ち");
        redChance=number(body,"赤に当たる確率（%）", "50");
        waitMin=number(body,"待ち時間の最小（秒）· 上限なし", "15");
        waitMax=number(body,"待ち時間の最大（秒）· 上限なし", "60");
        body.addView(text("ゆらぎが100%を超えて速度の目標が負になった場合は0にします。確率は0〜100%、加減速度は0より大きい数値です。",12));
        signalButton=button("このルートの信号を取得・再試行",body);signalButton.setOnClickListener(v->fetchRoad(true));
        body.addView(text("信号位置（始点からの距離m・1行ずつ、手入力も可）",14));
        signalInput=new EditText(this);signalInput.setTextSize(14);signalInput.setMinLines(2);signalInput.setMaxLines(5);signalInput.setGravity(Gravity.TOP);
        signalInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);signalInput.setContentDescription("信号の始点からの距離、単位メートル");body.addView(signalInput);
        body.addView(text("OSMに登録された信号をルートに対応づけます。赤・青は通過ごとの抽選です。実際の現在色とは連動しません。信号が未登録、隣の道にあるなどの場合は距離を修正してください。",12));
        start=button("ルートを再生",body);start.setTextColor(Color.WHITE);GradientDrawable bg=new GradientDrawable();bg.setColor(ACCENT);bg.setCornerRadius(dp(8));start.setBackground(bg);
        start.setOnClickListener(v->begin());
        LinearLayout controls=new LinearLayout(this);body.addView(controls);
        pause=button("一時停止",controls);pause.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));pause.setOnClickListener(v->send("PAUSE"));
        stop=button("停止・位置情報を戻す",controls);stop.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));stop.setOnClickListener(v->send("STOP"));
        section(body,"初回設定");
        body.addView(text("Androidの開発者向けオプションで「仮の現在地情報アプリ」をRoute GPSに設定してください。BlueStacksで項目がない場合は、付属の設定スクリプトを使ってください。",14));
        button("開発者向けオプションを開く",body).setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(Exception e){toast("開発者向けオプションがありません。付属スクリプトを使ってください。");}});
        button("位置情報の設定を開く",body).setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception e){toast("設定を開けませんでした");}});
        body.addView(text("終点では位置を保持します。停止すると疑似GPSを解除します。設定変更は停止後に行ってください。",12));
        android.content.SharedPreferences prefs=getPreferences(0);
        preparedText=prefs.getString("preparedText","");sourceLabel=prefs.getString("sourceLabel","");
        routeInput.setText(prefs.getString("route",DEMO));speed.setText(prefs.getString("speed","5"));loop.setChecked(prefs.getBoolean("loop",false));
        acceleration.setText(prefs.getString("acceleration","0.5"));braking.setText(prefs.getString("braking","0.8"));variation.setText(prefs.getString("variation","15"));
        redChance.setText(prefs.getString("red","50"));waitMin.setText(prefs.getString("waitMin","15"));waitMax.setText(prefs.getString("waitMax","60"));signalInput.setText(prefs.getString("signals",""));
        TextWatcher watch=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){inspectRoute();}public void afterTextChanged(Editable e){}};
        routeInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!s.toString().equals(preparedText)){preparedText="";sourceLabel="";signalInput.setText("");}inspectRoute();}public void afterTextChanged(Editable e){}});
        speed.addTextChangedListener(watch);loop.setOnCheckedChangeListener((b,c)->inspectRoute());inspectRoute();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
    }
    private EditText number(LinearLayout body,String label,String value){body.addView(text(label,14));EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setContentDescription(label);e.setText(value);body.addView(e);return e;}
    private double value(EditText e){try{return Double.parseDouble(e.getText().toString());}catch(NumberFormatException ex){throw new IllegalArgumentException(e.getContentDescription()+"を数値で入力してください");}}
    private WalkSimulation validateSimulation(){return new WalkSimulation(new Route(Route.parse(routeInput.getText().toString()),false),value(speed),value(acceleration),value(braking),value(variation),value(redChance),value(waitMin),value(waitMax),WalkSimulation.parseSignals(signalInput.getText().toString()),loop.isChecked(),0);}
    private void inspectRoute(){try{
        Route r=new Route(Route.parse(routeInput.getText().toString()),false);preview.route=r;preview.invalidate();
        double s=Double.parseDouble(speed.getText().toString());if(!Double.isFinite(s)||s<0.1)throw new IllegalArgumentException("巡航速度は0.1km/h以上の有限の数値で指定してください");
        summary.setText(String.format(Locale.JAPAN,"%d地点 · %.0f m · 巡航速度で約%.1f分＋加減速・信号待ち%s",r.points.length,r.length,r.length/(s/3.6)/60,loop.isChecked()?" / 周":""));
    }catch(Exception e){preview.route=null;preview.invalidate();summary.setText(e instanceof NumberFormatException?"速度を入力してください":e.getMessage());}}
    private void begin(){try{
        if(!routeInput.getText().toString().equals(preparedText))throw new IllegalArgumentException("先に『道に沿う徒歩ルートを作成』を押すか、道路に沿ったGPXを読み込んでください。");
        validateSimulation();double s=value(speed);
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}
        save();startForegroundService(new Intent(this,PlaybackService.class).setAction("START").putExtra("route",routeInput.getText().toString()).putExtra("kmh",s).putExtra("loop",loop.isChecked())
            .putExtra("acceleration",value(acceleration)).putExtra("braking",value(braking)).putExtra("variation",value(variation)).putExtra("red",value(redChance)).putExtra("waitMin",value(waitMin)).putExtra("waitMax",value(waitMax)).putExtra("signals",signalInput.getText().toString()));
    }catch(Exception e){toast(e instanceof NumberFormatException?"速度を入力してください":e.getMessage());}}
    private void send(String action){if(PlaybackService.active)startService(new Intent(this,PlaybackService.class).setAction(action));}
    private void toast(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}
    private void save(){getPreferences(0).edit().putString("route",routeInput.getText().toString()).putString("speed",speed.getText().toString()).putBoolean("loop",loop.isChecked())
        .putString("preparedText",preparedText).putString("sourceLabel",sourceLabel).putString("acceleration",acceleration.getText().toString()).putString("braking",braking.getText().toString()).putString("variation",variation.getText().toString())
        .putString("red",redChance.getText().toString()).putString("waitMin",waitMin.getText().toString()).putString("waitMax",waitMax.getText().toString()).putString("signals",signalInput.getText().toString()).apply();}
    private void update(){
        boolean active=PlaybackService.active;
        status.setText(PlaybackService.state);
        if(!PlaybackService.error.isEmpty())detail.setText(PlaybackService.error);
        else if(active)detail.setText(String.format(Locale.JAPAN,"%.6f, %.6f\n%.0f / %.0f m · 現在 %.2f km/h / 巡航 %.2f",PlaybackService.latitude,PlaybackService.longitude,PlaybackService.travelled,PlaybackService.total,PlaybackService.actualKmh,PlaybackService.kmh));
        else detail.setText("ルートと速度を確認して再生してください。");
        start.setEnabled(!active&&!importing);pause.setEnabled(active&&!PlaybackService.finished);stop.setEnabled(active);
        pause.setText(PlaybackService.paused?"再開":"ゆっくり一時停止");routeInput.setEnabled(!active&&!importing);speed.setEnabled(!active&&!importing);loop.setEnabled(!active&&!importing);importButton.setEnabled(!active&&!importing);preview.invalidate();
        roadButton.setEnabled(!active&&!importing);signalButton.setEnabled(!active&&!importing);
        for(EditText e:new EditText[]{acceleration,braking,variation,redChance,waitMin,waitMax,signalInput})e.setEnabled(!active&&!importing);
        if(!importing)routeSource.setText(preparedText.isEmpty()?"徒歩ルート未作成：座標を入力して作成してください。":sourceLabel+" · 信号 "+(signalInput.getText().toString().trim().isEmpty()?0:signalInput.getText().toString().trim().split("[,\\s]+").length)+"地点");
    }
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){save();handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){super.onRequestPermissionsResult(request,perms,grants);if(request==10){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)begin();else toast("正確な位置情報の権限を許可してから再生してください。");}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=20||result!=RESULT_OK||data==null)return;
        importing=true;update();
        new Thread(()->{try(InputStream in=getContentResolver().openInputStream(data.getData())){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>5*1024*1024)throw new IOException("GPXは5MB以下にしてください");}
            String parsed=parseGpx(new ByteArrayInputStream(bytes.toByteArray()));
            runOnUiThread(()->{importing=false;preparedText=parsed;sourceLabel="GPXの形状を使用（通れる道かはGPX作成元で確認）";routeInput.setText(parsed);signalInput.setText("");save();update();});
        }catch(Exception e){runOnUiThread(()->{importing=false;update();toast("GPX読込エラー："+e.getMessage());});}},"gpx-import").start();
    }
    private void fetchRoad(boolean signalsOnly){
        if(importing||PlaybackService.active)return;
        try{
            double[][] points=Route.parse(routeInput.getText().toString());
            if(signalsOnly&&!routeInput.getText().toString().equals(preparedText))throw new IllegalArgumentException("先に徒歩ルートを作成するかGPXを読み込んでください");
            if(!signalsOnly&&points.length>25)throw new IllegalArgumentException("道路検索は2〜25地点の始点・経由地・終点に対応します。再作成する場合は座標欄を経由地の一覧に置き換えてください。");
            if(SystemClock.elapsedRealtime()-lastNetwork<2000)throw new IllegalArgumentException("少し時間をおいて再試行してください");
            lastNetwork=SystemClock.elapsedRealtime();boolean close=loop.isChecked();
            importing=true;routeSource.setText(signalsOnly?"信号位置を取得中…":"徒歩ルート・信号位置を取得中…");update();
            new Thread(()->{try{
                RoadClient.Result result;
                if(signalsOnly){result=new RoadClient.Result();result.coordinates=preparedText;result.signals=RoadClient.signals(new Route(points,false));}
                else result=RoadClient.walking(points,close);
                RoadClient.Result done=result;
                runOnUiThread(()->{importing=false;if(!signalsOnly){preparedText=done.coordinates;sourceLabel="OSRMの徒歩ルート（OSM）";routeInput.setText(done.coordinates);}signalInput.setText(done.signals);save();update();if(!done.warning.isEmpty())toast(done.warning);});
            }catch(Exception e){runOnUiThread(()->{importing=false;update();toast("取得できませんでした："+e.getMessage());});}},"walking-route").start();
        }catch(Exception e){toast(e.getMessage());}
    }
    static String parseGpx(InputStream in)throws Exception{
        XmlPullParserFactory f=XmlPullParserFactory.newInstance();f.setNamespaceAware(true);XmlPullParser x=f.newPullParser();x.setInput(in,null);
        List<double[]> track=new ArrayList<>(),rte=new ArrayList<>();int segments=0,routes=0;boolean isGpx=false;
        for(int event=x.getEventType();event!=XmlPullParser.END_DOCUMENT;event=x.nextToken()){
            if(event==XmlPullParser.DOCDECL)throw new IOException("DTD付きのGPXは読み込めません");
            if(event!=XmlPullParser.START_TAG)continue;
            String name=x.getName();
            if("gpx".equals(name))isGpx=true;
            if("trkseg".equals(name))segments++;
            if("rte".equals(name))routes++;
            if("trkpt".equals(name)||"rtept".equals(name)){
                List<double[]> dest="trkpt".equals(name)?track:rte;
                dest.add(new double[]{Double.parseDouble(x.getAttributeValue(null,"lat")),Double.parseDouble(x.getAttributeValue(null,"lon"))});
                if(track.size()+rte.size()>5000)throw new IOException("最大5000地点です");
            }
        }
        if(!isGpx)throw new IOException("GPX形式のファイルを選んでください");
        List<double[]> ps=track.isEmpty()?rte:track;
        if((!track.isEmpty()&&segments>1)||(track.isEmpty()&&routes>1))throw new IOException("1つのトラック区間またはルートを含むGPXを使ってください");
        new Route(ps.toArray(new double[0][]),false);
        StringBuilder out=new StringBuilder();for(double[] p:ps)out.append(p[0]).append(',').append(p[1]).append('\n');return out.toString();
    }
    private class Preview extends View {
        Route route;Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        Preview(){super(MainActivity.this);setBackgroundColor(Color.WHITE);setContentDescription("ルート形状と再生位置のプレビュー");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(route==null){pen.setColor(INK);pen.setTextSize(dp(14));c.drawText("ルートを入力すると表示されます",dp(16),dp(36),pen);return;}
            double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
            double origin=route.points[0][1],cos=Math.max(0.01,Math.cos(Math.toRadians(route.points[0][0])));
            for(double[] p:route.points){double x=wrap(p[1]-origin)*cos,y=-p[0];minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            double scale=Math.min((getWidth()-dp(48))/Math.max(1e-6,maxX-minX),(getHeight()-dp(48))/Math.max(1e-6,maxY-minY));
            double centerX=(minX+maxX)/2,centerY=(minY+maxY)/2;
            Path path=new Path();
            for(int i=0;i<route.points.length;i++){double[] p=route.points[i];float x=(float)(getWidth()/2+(wrap(p[1]-origin)*cos-centerX)*scale),y=(float)(getHeight()/2+(-p[0]-centerY)*scale);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
            pen.setColor(ACCENT);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(3));c.drawPath(path,pen);pen.setStyle(Paint.Style.FILL);
            for(int i:new int[]{0,route.points.length-1}){double[] p=route.points[i];pen.setColor(i==0?ACCENT:INK);c.drawCircle((float)(getWidth()/2+(wrap(p[1]-origin)*cos-centerX)*scale),(float)(getHeight()/2+(-p[0]-centerY)*scale),dp(5),pen);}
            if(PlaybackService.active){pen.setColor(Color.rgb(224,103,25));c.drawCircle((float)(getWidth()/2+(wrap(PlaybackService.longitude-origin)*cos-centerX)*scale),(float)(getHeight()/2+(-PlaybackService.latitude-centerY)*scale),dp(7),pen);}
        }
        double wrap(double d){return (d+540)%360-180;}
    }
}
