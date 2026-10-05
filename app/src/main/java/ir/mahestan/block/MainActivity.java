package ir.mahestan.block;

import android.app.*;
import android.os.*;
import android.webkit.*;
import android.view.*;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.database.Cursor;
import android.database.sqlite.*;
import android.util.Base64;
import java.io.*;

public class MainActivity extends Activity {
  private WebView web; private LocalDb db; private ValueCallback<Uri[]> uploadCallback;
  private static final int FILE_CHOOSER=1001;
  @Override public void onCreate(Bundle b){super.onCreate(b); db=new LocalDb(this); web=new WebView(this); WebView.setWebContentsDebuggingEnabled(false);
    WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false); s.setMediaPlaybackRequiresUserGesture(false);
    web.setOverScrollMode(View.OVER_SCROLL_NEVER); web.addJavascriptInterface(new Bridge(this,db),"AndroidBridge");
    web.setWebChromeClient(new WebChromeClient(){ @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){ if(uploadCallback!=null)uploadCallback.onReceiveValue(null); uploadCallback=cb; try{Intent i=p.createIntent(); startActivityForResult(i,FILE_CHOOSER); }catch(Exception e){uploadCallback=null;return false;} return true; }});
    web.setWebViewClient(new WebViewClient()); setContentView(web); web.loadUrl("file:///android_asset/www/index.html"); }
  @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data); if(req==FILE_CHOOSER&&uploadCallback!=null){Uri[] r=null;if(res==RESULT_OK&&data!=null){Uri u=data.getData();if(u!=null)r=new Uri[]{u};}uploadCallback.onReceiveValue(r);uploadCallback=null;}}
  @Override public void onBackPressed(){ if(web.canGoBack()) web.goBack(); else super.onBackPressed(); }
  public static class LocalDb extends SQLiteOpenHelper {
    LocalDb(Context c){super(c,"mahestan_offline.db",null,1);}
    public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE app_data (key TEXT PRIMARY KEY,value TEXT NOT NULL)");}
    public void onUpgrade(SQLiteDatabase d,int a,int b){}
    synchronized String get(String k){Cursor c=getReadableDatabase().query("app_data",new String[]{"value"},"key=?",new String[]{k},null,null,null);try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
    synchronized void set(String k,String v){ContentValues x=new ContentValues();x.put("key",k);x.put("value",v);getWritableDatabase().insertWithOnConflict("app_data",null,x,SQLiteDatabase.CONFLICT_REPLACE);}
    synchronized void del(String k){getWritableDatabase().delete("app_data","key=?",new String[]{k});}
  }
  public static class Bridge {
    final Context c; final LocalDb db; Bridge(Context c,LocalDb db){this.c=c;this.db=db;}
    @JavascriptInterface public String getData(String k){return db.get(k);}
    @JavascriptInterface public void setData(String k,String v){db.set(k,v);}
    @JavascriptInterface public void deleteData(String k){db.del(k);}
    @JavascriptInterface public void saveBlob(String dataUrl,String name,String mime){try{int i=dataUrl.indexOf(',');byte[] bytes=Base64.decode(i>=0?dataUrl.substring(i+1):dataUrl,Base64.DEFAULT);Uri u=null;if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,mime);v.put(MediaStore.Downloads.IS_PENDING,1);u=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);try(OutputStream o=c.getContentResolver().openOutputStream(u)){o.write(bytes);}v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(u,v,null,null);}else{File dir=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(dir==null)dir=c.getFilesDir();dir.mkdirs();try(FileOutputStream o=new FileOutputStream(new File(dir,name))){o.write(bytes);}} ((Activity)c).runOnUiThread(()->android.widget.Toast.makeText(c,"فایل ذخیره شد: "+name,android.widget.Toast.LENGTH_SHORT).show());}catch(Exception e){((Activity)c).runOnUiThread(()->android.widget.Toast.makeText(c,"خطا در ذخیره فایل",android.widget.Toast.LENGTH_SHORT).show());}}
  }
}
