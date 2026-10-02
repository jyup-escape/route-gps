package jp.akagumi.routegps;

import android.app.Activity;
import android.webkit.*;
import android.net.Uri;
import java.io.*;
import java.util.*;
import org.json.*;

/** Trusted, bundled presentation layer. No external content can reach the bridge. */
final class UiShell {
    interface Host {void field(String id,String value);void action(String id);void ready();}
    final WebView view;
    private boolean ready;
    private String page="play";
    private Route cachedRoute;
    private RouteProjection projection;
    private JSONObject geometry;
    private static final Set<String> FIELDS=new HashSet<>(Arrays.asList("maps","route","speed","acceleration","braking","variation","red","waitMin","waitMax","signals","loop"));
    private static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("start","pause","stop","import","export","maps","road","signals","developer","location","attribution","osmLicense","fixMap"));
    UiShell(Activity activity,Host host){
        view=new WebView(activity);view.setBackgroundColor(android.graphics.Color.WHITE);
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setTextZoom(Math.round(100*activity.getResources().getConfiguration().fontScale));settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        view.addJavascriptInterface(new Object(){
            @JavascriptInterface public void field(String id,String value){if(FIELDS.contains(id))activity.runOnUiThread(()->host.field(id,value));}
            @JavascriptInterface public void action(String id){if(ACTIONS.contains(id))activity.runOnUiThread(()->host.action(id));}
            @JavascriptInterface public void page(String id){if(Arrays.asList("play","import","settings").contains(id))activity.runOnUiThread(()->UiShell.this.page=id);}
            @JavascriptInterface public void ready(){activity.runOnUiThread(()->{ready=true;cachedRoute=null;host.ready();});}
        },"NativeRoute");
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                Uri uri=request.getUrl();String path=uri.getPath();
                if("https".equals(uri.getScheme())&&"routegps.local".equals(uri.getHost())&&uri.getPort()==-1&&Arrays.asList("/index.html","/ui.css","/ui.js").contains(path)){
                    try{return new WebResourceResponse(path.endsWith("css")?"text/css":path.endsWith("js")?"application/javascript":"text/html","UTF-8",activity.getAssets().open(path.substring(1)));}catch(IOException ignored){}
                }
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
            }
        });
        view.loadUrl("https://routegps.local/index.html");
    }
    void publish(JSONObject state,Route route,boolean active,double travelled,double latitude,double longitude){
        if(!ready)return;
        try{
            if(route!=cachedRoute){
                cachedRoute=route;geometry=null;projection=null;
                if(route!=null){
                    projection=new RouteProjection(route);double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
                    for(int i=0;i<route.points.length;i++){double x=projection.x[i],y=RouteProjection.y(route.points[i][0]);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
                    JSONArray points=new JSONArray();for(int k=0,n=Math.min(4096,route.points.length);k<n;k++){int i=(int)((long)k*(route.points.length-1)/(n-1));points.put(new JSONArray().put(projection.x[i]).put(RouteProjection.y(route.points[i][0])));}
                    geometry=new JSONObject().put("points",points).put("cx",(minX+maxX)/2).put("cy",(minY+maxY)/2).put("width",maxX-minX).put("height",maxY-minY);state.put("geometry",geometry);
                }else state.put("clearGeometry",true);
            }
            if(active&&projection!=null)state.put("marker",new JSONArray().put(projection.markerX(travelled,longitude)).put(RouteProjection.y(latitude)));
            view.evaluateJavascript("window.RouteUI&&window.RouteUI.update("+state.toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029")+")",null);
        }catch(JSONException ignored){}
    }
    void destroy(){ready=false;view.removeJavascriptInterface("NativeRoute");view.destroy();}
    void showPlay(){if(ready)view.evaluateJavascript("window.RouteUI&&window.RouteUI.navigate('play')",null);}
    void showImport(){if(ready)view.evaluateJavascript("window.RouteUI&&window.RouteUI.navigate('import')",null);}
    boolean back(){if(!page.equals("play")){showPlay();return true;}return false;}
}
