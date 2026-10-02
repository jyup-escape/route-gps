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
    private EditText mapsInput,routeInput,speed,acceleration,braking,variation,redChance,waitMin,waitMax,signalInput;
    private CheckBox loop;
    private TextView status,detail,summary,routeSource;
    private Button start,pause,stop,importButton,roadButton,signalButton,mapsButton,exportButton;
    private Preview preview;
    private UiShell shell;
    private Handler handler=new Handler();
    private boolean importing=false;
    private Route preparedRoute,exportRoute;private String sourceLabel="";private boolean settingRoute=false,restoring=false;
    private long lastNetwork=0;
    private String inspectedText="";private Route inspectedRoute;
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
        body.addView(text("地点数・距離・GPXサイズの固定上限なし（端末の空きメモリの範囲内）。",12));
        section(body,"Google Mapsの道筋を取り込む");
        mapsInput=new EditText(this);mapsInput.setHint("Google Mapsの経路リンクを貼り付け");mapsInput.setSingleLine(true);mapsInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);mapsInput.setContentDescription("Google Mapsの経路リンク");body.addView(mapsInput);
        mapsButton=button("Googleのルート候補を取得",body);mapsButton.setOnClickListener(v->convertMaps());
        body.addView(text("Google側の道筋を取得します。候補の距離・道筋をGoogle Mapsと確認して選択してください。共有画面で選んだ候補は自動判定できません。",12));
        body.addView(text("APIキー不要。リンクと経由地をGoogleに送信します。取得できない場合はエラーを表示し、別の道路検索へ自動で切り替えません。",12));
        exportButton=button("このルートをGPXで保存",body);exportButton.setOnClickListener(v->{try{if(preparedRoute==null)throw new IOException("ルート未読込");exportRoute=preparedRoute;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/gpx+xml");i.putExtra(Intent.EXTRA_TITLE,"RouteGPS-route.gpx");startActivityForResult(i,21);}catch(Exception e){toast("先にルートを作成するかGPXを読み込んでください");}});
        body.addView(text("始点・経由地・終点を緯度,経度で入力（2地点以上・区間ごとに道路検索）",14));
        routeInput=new EditText(this);routeInput.setTextSize(14);routeInput.setTypeface(Typeface.MONOSPACE);routeInput.setGravity(Gravity.TOP);routeInput.setMinLines(4);routeInput.setMaxLines(7);
        routeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        routeInput.setContentDescription("ルート座標：各行に緯度,経度");routeInput.setSaveEnabled(false);body.addView(routeInput);
        roadButton=button("座標からOSMの徒歩ルートを作成・信号を取得",body);
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
        body.addView(text("Androidの開発者向けオプションで「仮の現在地情報アプリ」をRoute GPSに設定してください。",14));
        button("開発者向けオプションを開く",body).setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(Exception e){toast("開発者向けオプションがありません。Androidの設定で仮の現在地情報アプリを選んでください。");}});
        button("位置情報の設定を開く",body).setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception e){toast("設定を開けませんでした");}});
        body.addView(text("終点では位置を保持します。停止すると疑似GPSを解除します。設定変更は停止後に行ってください。",12));
        android.content.SharedPreferences prefs=getPreferences(0);
        boolean stored=prefs.getBoolean("routeStored",false);String legacy=prefs.getString("preparedText","");
        String editor=prefs.getString("route",DEMO);
        // Never lay out an old complete track in EditText during migration.
        routeInput.setText(stored||!legacy.isEmpty()?"# 保存ルートを復元中…":editor);
        if(stored||!legacy.isEmpty()){
            restoring=true;importing=true;
            new Thread(()->{try{
                RouteStore.Saved restoredSnapshot;
                if(stored)restoredSnapshot=RouteStore.load(new File(getFilesDir(),"saved-route.bin"));
                else {Route r=new Route(Route.parse(legacy),false);String label=prefs.getString("sourceLabel","");RouteStore.save(new File(getFilesDir(),"saved-route.bin"),r,label);restoredSnapshot=RouteStore.load(new File(getFilesDir(),"saved-route.bin"));}
                final RouteStore.Saved loaded=restoredSnapshot;
                runOnUiThread(()->{restoring=false;applyRoute(loaded.route,loaded.label,null);});
            }catch(Exception e){runOnUiThread(()->{restoring=false;importing=false;update();toast("保存ルートを復元できませんでした："+e.getMessage());});}},"route-restore").start();
        }
        speed.setText(prefs.getString("speed","5"));loop.setChecked(prefs.getBoolean("loop",false));
        acceleration.setText(prefs.getString("acceleration","0.5"));braking.setText(prefs.getString("braking","0.8"));variation.setText(prefs.getString("variation","15"));
        redChance.setText(prefs.getString("red","50"));waitMin.setText(prefs.getString("waitMin","15"));waitMax.setText(prefs.getString("waitMax","60"));signalInput.setText(prefs.getString("signals",""));
        TextWatcher watch=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){inspectRoute();}public void afterTextChanged(Editable e){}};
        routeInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(settingRoute)return;preparedRoute=null;sourceLabel="";signalInput.setText("");inspectRoute();}public void afterTextChanged(Editable e){}});
        acceptShare(getIntent());
        speed.addTextChangedListener(watch);loop.setOnCheckedChangeListener((b,c)->inspectRoute());inspectRoute();
        shell=new UiShell(this,new UiShell.Host(){
            public void field(String id,String value){
                if(isDestroyed()||PlaybackService.active||importing)return;
                if(id.equals("loop"))loop.setChecked(value.equals("true"));
                else {EditText input=shellInputs().get(id);if(input!=null&&!input.getText().toString().equals(value))input.setText(value);}
                save();update();
            }
            public void action(String id){
                if(isDestroyed())return;
                Map<String,Button> controls=shellControls();Button control=controls.get(id);
                if(control!=null){if(control.isEnabled())control.performClick();return;}
                String url=id.equals("attribution")?"https://routing.openstreetmap.de/about.html":id.equals("osmLicense")?"https://www.openstreetmap.org/copyright":id.equals("fixMap")?"https://www.openstreetmap.org":null;
                try{if(url!=null)startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));
                    else if(id.equals("developer"))startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    else if(id.equals("location"))startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }catch(Exception e){toast("設定やサービスを開けませんでした："+e.getMessage());}
            }
            public void ready(){update();}
        });
        setContentView(shell.view);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
    }
    private Map<String,EditText> shellInputs(){Map<String,EditText> inputs=new LinkedHashMap<>();String[] ids={"maps","route","speed","acceleration","braking","variation","red","waitMin","waitMax","signals"};EditText[] views={mapsInput,routeInput,speed,acceleration,braking,variation,redChance,waitMin,waitMax,signalInput};for(int i=0;i<ids.length;i++)inputs.put(ids[i],views[i]);return inputs;}
    private Map<String,Button> shellControls(){Map<String,Button> controls=new LinkedHashMap<>();String[] ids={"start","pause","stop","import","export","maps","road","signals"};Button[] views={start,pause,stop,importButton,exportButton,mapsButton,roadButton,signalButton};for(int i=0;i<ids.length;i++)controls.put(ids[i],views[i]);return controls;}
    private EditText number(LinearLayout body,String label,String value){body.addView(text(label,14));EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setContentDescription(label);e.setText(value);body.addView(e);return e;}
    private double value(EditText e){try{return Double.parseDouble(e.getText().toString());}catch(NumberFormatException ex){throw new IllegalArgumentException(e.getContentDescription()+"を数値で入力してください");}}
    private Route currentRoute(){
        if(preparedRoute!=null)return preparedRoute;
        String current=routeInput.getText().toString();if(!current.equals(inspectedText)||inspectedRoute==null){inspectedRoute=new Route(Route.parse(current),false);inspectedText=current;}return inspectedRoute;
    }
    // Called off the UI thread. Only publish after an atomic full-geometry save succeeds.
    private void installRoute(Route route,String label,String signals)throws IOException{
        RouteStore.save(new File(getFilesDir(),"saved-route.bin"),route,label);
        runOnUiThread(()->applyRoute(route,label,signals));
    }
    private void applyRoute(Route route,String label,String signals){
        if(isFinishing()||isDestroyed())return;
        preparedRoute=route;sourceLabel=label.startsWith("Google Mapsの経由地から再計算")?"旧版でOSRM再計算した道筋（Googleの候補を再取得してください）":label;
        inspectedRoute=null;inspectedText="";settingRoute=true;
        try{routeInput.setText(RouteStore.editorPreview(route));}finally{settingRoute=false;}
        if(signals!=null)signalInput.setText(signals);importing=false;inspectRoute();save();update();if(shell!=null)shell.showPlay();
    }
    private WalkSimulation validateSimulation(){return new WalkSimulation(currentRoute(),value(speed),value(acceleration),value(braking),value(variation),value(redChance),value(waitMin),value(waitMax),WalkSimulation.parseSignals(signalInput.getText().toString()),loop.isChecked(),0);}
    private void inspectRoute(){try{
        Route r=currentRoute();preview.route=r;preview.invalidate();
        double s=Double.parseDouble(speed.getText().toString());if(!Double.isFinite(s)||s<0.1)throw new IllegalArgumentException("巡航速度は0.1km/h以上の有限の数値で指定してください");
        summary.setText(String.format(Locale.JAPAN,"%d地点 · %.0f m · 巡航速度で約%.1f分＋加減速・信号待ち%s",r.points.length,r.length,r.length/(s/3.6)/60,loop.isChecked()?" / 周":""));
    }catch(Exception e){preview.route=null;preview.invalidate();summary.setText(e instanceof NumberFormatException?"速度を入力してください":e.getMessage());}}
    private void begin(){try{
        if(preparedRoute==null)throw new IllegalArgumentException("先に『道に沿う徒歩ルートを作成』を押すか、道路に沿ったGPXを読み込んでください。");
        validateSimulation();double s=value(speed);
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}
        save();
        final Route snapshot=preparedRoute;final String label=sourceLabel,signals=signalInput.getText().toString();
        final Intent intent=new Intent(this,PlaybackService.class).setAction("START").putExtra("routeBinary",true).putExtra("kmh",s).putExtra("loop",loop.isChecked())
            .putExtra("acceleration",value(acceleration)).putExtra("braking",value(braking)).putExtra("variation",value(variation)).putExtra("red",value(redChance)).putExtra("waitMin",value(waitMin)).putExtra("waitMax",value(waitMax)).putExtra("signalsFile",true);
        importing=true;routeSource.setText("再生用ルートを準備中…");update();
        new Thread(()->{try{
            RouteStore.save(new File(getFilesDir(),"playback-route.bin"),snapshot,label);
            try(OutputStream out=new FileOutputStream(new File(getFilesDir(),"playback-signals.txt"))){out.write(signals.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            runOnUiThread(()->{importing=false;if(isFinishing()||isDestroyed())return;try{startForegroundService(intent);}catch(Exception e){toast("再生を開始できませんでした："+e.getMessage());}update();});
        }catch(Exception e){runOnUiThread(()->{importing=false;update();toast("再生を準備できませんでした："+e.getMessage());});}},"route-playback-prepare").start();
    }catch(Exception e){toast(e instanceof NumberFormatException?"速度を入力してください":e.getMessage());}}
    private void send(String action){if(PlaybackService.active)startService(new Intent(this,PlaybackService.class).setAction(action));}
    private void toast(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}
    private void save(){if(restoring)return;getPreferences(0).edit().putBoolean("routeStored",preparedRoute!=null).remove("preparedText").putString("route",preparedRoute==null?routeInput.getText().toString():"").putString("speed",speed.getText().toString()).putBoolean("loop",loop.isChecked())
        .putString("sourceLabel",sourceLabel).putString("acceleration",acceleration.getText().toString()).putString("braking",braking.getText().toString()).putString("variation",variation.getText().toString())
        .putString("red",redChance.getText().toString()).putString("waitMin",waitMin.getText().toString()).putString("waitMax",waitMax.getText().toString()).putString("signals",signalInput.getText().toString()).apply();}
    private void update(){
        boolean active=PlaybackService.active;
        status.setText(PlaybackService.state);
        if(!PlaybackService.error.isEmpty())detail.setText(PlaybackService.error);
        else if(active)detail.setText(String.format(Locale.JAPAN,"%.6f, %.6f\n%.0f / %.0f m · 現在 %.2f km/h / 巡航 %.2f",PlaybackService.latitude,PlaybackService.longitude,PlaybackService.travelled,PlaybackService.total,PlaybackService.actualKmh,PlaybackService.kmh));
        else detail.setText("ルートと速度を確認して再生してください。");
        start.setEnabled(!active&&!importing);pause.setEnabled(active&&!PlaybackService.finished);stop.setEnabled(active);
        pause.setText(PlaybackService.paused?"再開":"ゆっくり一時停止");routeInput.setEnabled(!active&&!importing);speed.setEnabled(!active&&!importing);loop.setEnabled(!active&&!importing);importButton.setEnabled(!active&&!importing);preview.invalidate();
        roadButton.setEnabled(!active&&!importing);signalButton.setEnabled(!active&&!importing);mapsButton.setEnabled(!active&&!importing);mapsInput.setEnabled(!active&&!importing);exportButton.setEnabled(!active&&!importing&&preparedRoute!=null);
        for(EditText e:new EditText[]{acceleration,braking,variation,redChance,waitMin,waitMax,signalInput})e.setEnabled(!active&&!importing);
        if(!importing)routeSource.setText(preparedRoute==null?"徒歩ルート未作成：座標を入力して作成してください。":sourceLabel+" · 信号 "+(signalInput.getText().toString().trim().isEmpty()?0:signalInput.getText().toString().trim().split("[,\\s]+").length)+"地点");
        if(shell!=null)try{
            org.json.JSONObject fields=new org.json.JSONObject(),enabled=new org.json.JSONObject();
            for(Map.Entry<String,EditText> entry:shellInputs().entrySet())fields.put(entry.getKey(),entry.getValue().getText().toString());fields.put("loop",String.valueOf(loop.isChecked()));
            for(Map.Entry<String,Button> entry:shellControls().entrySet())enabled.put(entry.getKey(),entry.getValue().isEnabled());enabled.put("start",start.isEnabled()&&preparedRoute!=null);
            org.json.JSONObject state=new org.json.JSONObject().put("fields",fields).put("enabled",enabled).put("prepared",preparedRoute!=null).put("state",PlaybackService.state).put("active",active).put("paused",PlaybackService.paused).put("busy",importing).put("busyLabel",routeSource.getText().toString()).put("source",preparedRoute!=null?routeSource.getText().toString():"").put("summary",preparedRoute!=null?summary.getText().toString():"").put("detail",detail.getText().toString()).put("error",PlaybackService.error).put("pointCount",preparedRoute!=null?preparedRoute.points.length:0).put("distance",preparedRoute!=null?preparedRoute.length:0).put("travelled",active?PlaybackService.travelled:0).put("actualKmh",active?PlaybackService.actualKmh:0);
            shell.publish(state,preparedRoute,active,PlaybackService.travelled,PlaybackService.latitude,PlaybackService.longitude);
        }catch(org.json.JSONException ignored){}
    }
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){save();handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacks(refresh);if(shell!=null)shell.destroy();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){super.onRequestPermissionsResult(request,perms,grants);if(request==10){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)begin();else toast("正確な位置情報の権限を許可してから再生してください。");}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;
        if(req!=20&&req!=21)return;importing=true;update();final Route snapshot=exportRoute;
        new Thread(()->{try{
            if(req==21){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){Gpx.write(snapshot,out);}runOnUiThread(()->{importing=false;update();toast("GPXを保存しました");});return;}
            String parsed;try(InputStream in=getContentResolver().openInputStream(data.getData())){parsed=Gpx.read(in);}
            installRoute(new Route(Route.parse(parsed),false),"GPXの形状を使用（通れる道かはGPX作成元で確認）","");
        }catch(Exception e){runOnUiThread(()->{importing=false;update();toast("GPX処理エラー："+e.getMessage());});}},"gpx-document").start();
    }
    private void convertMaps(){
        if(importing||PlaybackService.active)return;final String link=mapsInput.getText().toString();
        if(SystemClock.elapsedRealtime()-lastNetwork<2000){toast("少し時間をおいて再試行してください");return;}lastNetwork=SystemClock.elapsedRealtime();
        importing=true;routeSource.setText("Googleの道筋を取得中…");update();
        new Thread(()->{try{
            List<GoogleRoute.Candidate> candidates=GoogleRoute.fetch(MapsClient.expand(link));
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).label();
                new AlertDialog.Builder(this).setTitle("Google Mapsと距離を確認して選択").setItems(labels,(dialog,index)->{
                    GoogleRoute.Candidate candidate=candidates.get(index);
                    routeSource.setText("選択した道筋を保存中…");
                    new Thread(()->{try{installRoute(candidate.route,String.format(Locale.JAPAN,"Googleから取得した道筋 · Google表示 %.1f km（距離計算方式により差が出ます）",candidate.reportedDistance/1000),"");}
                    catch(Exception e){runOnUiThread(()->{importing=false;update();toast("ルートを保存できませんでした："+e.getMessage());});}},"route-install").start();
                }).setNegativeButton("キャンセル",(dialog,index)->{importing=false;update();}).setOnCancelListener(dialog->{importing=false;update();}).show();
            });
        }catch(Exception e){runOnUiThread(()->{importing=false;update();toast("Googleの道筋を取得できませんでした。現在のルートは変更していません。\n"+e.getMessage());});}},"google-route-import").start();
    }
    private void acceptShare(Intent intent){if(Intent.ACTION_SEND.equals(intent.getAction())){String text=intent.getStringExtra(Intent.EXTRA_TEXT);if(text!=null)mapsInput.setText(text);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptShare(intent);}
    private void fetchRoad(boolean signalsOnly){
        if(importing||PlaybackService.active)return;
        try{
            if(!signalsOnly&&preparedRoute!=null)throw new IllegalArgumentException("取込済みの道筋はそのまま再生・保存できます。OSMで別ルートを作る場合は座標欄を始点・経由地・終点に置き換えてください。");
            Route signalRoute=signalsOnly?preparedRoute:null;double[][] points=signalsOnly?null:Route.parse(routeInput.getText().toString());
            if(signalsOnly&&signalRoute==null)throw new IllegalArgumentException("先に徒歩ルートを作成するかGPXを読み込んでください");
            if(SystemClock.elapsedRealtime()-lastNetwork<2000)throw new IllegalArgumentException("少し時間をおいて再試行してください");
            lastNetwork=SystemClock.elapsedRealtime();boolean close=loop.isChecked();
            importing=true;routeSource.setText(signalsOnly?"信号位置を取得中…":"徒歩ルート・信号位置を取得中…");update();
            new Thread(()->{try{
                RoadClient.Result result;
                if(signalsOnly){result=new RoadClient.Result();result.signals=RoadClient.signals(signalRoute,message->runOnUiThread(()->routeSource.setText(message)));}
                else result=RoadClient.walking(points,close,message->runOnUiThread(()->routeSource.setText(message)));
                RoadClient.Result done=result;
                if(!signalsOnly)installRoute(new Route(Route.parse(done.coordinates),false),"OSRMの徒歩ルート（OSM）",done.signals);
                else runOnUiThread(()->{importing=false;signalInput.setText(done.signals);save();update();});
                if(!done.warning.isEmpty())runOnUiThread(()->toast(done.warning));
            }catch(Exception e){runOnUiThread(()->{importing=false;update();toast("取得できませんでした："+e.getMessage());});}},"walking-route").start();
        }catch(Exception e){toast(e.getMessage());}
    }
    private class Preview extends View {
        Route route,cachedRoute;Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);Path cachedPath;int cachedWidth,cachedHeight;double scale,centerX,centerY;RouteProjection projection;
        Preview(){super(MainActivity.this);setBackgroundColor(Color.WHITE);setContentDescription("ルート形状と再生位置のプレビュー");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(route==null){pen.setColor(INK);pen.setTextSize(dp(14));c.drawText("ルートを入力すると表示されます",dp(16),dp(36),pen);return;}
            if(cachedRoute!=route||cachedWidth!=getWidth()||cachedHeight!=getHeight()){
            double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
            projection=new RouteProjection(route);
            for(int i=0;i<route.points.length;i++){double[] p=route.points[i];double x=projection.x[i],y=RouteProjection.y(p[0]);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            scale=Math.min((getWidth()-dp(48))/Math.max(1e-6,maxX-minX),(getHeight()-dp(48))/Math.max(1e-6,maxY-minY));
            centerX=(minX+maxX)/2;centerY=(minY+maxY)/2;
            Path path=new Path();
            for(int k=0,count=Math.min(4096,route.points.length);k<count;k++){int i=(int)((long)k*(route.points.length-1)/(count-1));double[] p=route.points[i];float x=(float)(getWidth()/2+(projection.x[i]-centerX)*scale),y=(float)(getHeight()/2+(RouteProjection.y(p[0])-centerY)*scale);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
            cachedPath=path;cachedRoute=route;cachedWidth=getWidth();cachedHeight=getHeight();
            }
            pen.setColor(ACCENT);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(3));c.drawPath(cachedPath,pen);pen.setStyle(Paint.Style.FILL);
            for(int i:new int[]{0,route.points.length-1}){double[] p=route.points[i];pen.setColor(i==0?ACCENT:INK);c.drawCircle((float)(getWidth()/2+(projection.x[i]-centerX)*scale),(float)(getHeight()/2+(RouteProjection.y(p[0])-centerY)*scale),dp(5),pen);}
            if(PlaybackService.active){pen.setColor(Color.rgb(224,103,25));c.drawCircle((float)(getWidth()/2+(projection.markerX(PlaybackService.travelled,PlaybackService.longitude)-centerX)*scale),(float)(getHeight()/2+(RouteProjection.y(PlaybackService.latitude)-centerY)*scale),dp(7),pen);}
        }
    }
}
