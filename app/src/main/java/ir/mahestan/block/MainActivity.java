package ir.mahestan.block;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.net.Uri;
import android.view.View;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private LocalDb db;
    private android.webkit.ValueCallback<Uri[]> uploadCallback;

    private static final int FILE_CHOOSER = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = new LocalDb(this);

        web = new WebView(this);

        WebView.setWebContentsDebuggingEnabled(false);

        WebSettings settings = web.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);

        // LocalStorage
        settings.setDomStorageEnabled(true);

        // Viewport / mobile layout
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        // Media
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Security:
        // The application does NOT need file:// access because
        // WebViewAssetLoader serves the local files.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        // Zoom
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Keep scrolling behavior
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        /*
         * AndroidBridge
         *
         * This allows the HTML application to store its important
         * information inside Android SQLite.
         */
        web.addJavascriptInterface(
                new Bridge(this, db),
                "AndroidBridge"
        );

        /*
         * WebViewAssetLoader
         *
         * All files under:
         *
         * app/src/main/assets/
         *
         * are served locally through:
         *
         * https://appassets.androidplatform.net/assets/
         */
        final WebViewAssetLoader assetLoader =
                new WebViewAssetLoader.Builder()
                        .addPathHandler(
                                "/assets/",
                                new WebViewAssetLoader.AssetsPathHandler(this)
                        )
                        .build();

        web.setWebViewClient(new LocalContentWebViewClient(assetLoader));

        /*
         * File chooser
         */
        web.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView view,
                            android.webkit.ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ) {

                        if (uploadCallback != null) {
                            uploadCallback.onReceiveValue(null);
                        }

                        uploadCallback = callback;

                        try {
                            Intent intent = params.createIntent();

                            startActivityForResult(
                                    intent,
                                    FILE_CHOOSER
                            );

                        } catch (Exception e) {

                            uploadCallback = null;
                            return false;
                        }

                        return true;
                    }
                }
        );

        setContentView(web);

        /*
         * IMPORTANT:
         *
         * Do NOT use:
         * file:///android_asset/www/index.html
         *
         * The application now uses WebViewAssetLoader.
         */
        web.loadUrl(
                "https://appassets.androidplatform.net/assets/www/index.html"
        );
    }

    /**
     * WebView client for local application assets.
     */
    private static class LocalContentWebViewClient
            extends WebViewClientCompat {

        private final WebViewAssetLoader assetLoader;

        LocalContentWebViewClient(
                WebViewAssetLoader assetLoader
        ) {
            this.assetLoader = assetLoader;
        }

        @RequiresApi(21)
        @Override
        public WebResourceResponse shouldInterceptRequest(
                WebView view,
                WebResourceRequest request
        ) {
            return assetLoader.shouldInterceptRequest(
                    request.getUrl()
            );
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(
                WebView view,
                String url
        ) {
            return assetLoader.shouldInterceptRequest(
                    Uri.parse(url)
            );
        }
    }

    /**
     * File chooser result.
     */
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (
                requestCode == FILE_CHOOSER
                        && uploadCallback != null
        ) {

            Uri[] result = null;

            if (
                    resultCode == RESULT_OK
                            && data != null
            ) {

                Uri uri = data.getData();

                if (uri != null) {
                    result = new Uri[]{uri};
                }
            }

            uploadCallback.onReceiveValue(result);

            uploadCallback = null;
        }
    }

    /**
     * Android back button.
     */
    @Override
    public void onBackPressed() {

        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Local SQLite database.
     *
     * Database file:
     * mahestan_offline.db
     */
    public static class LocalDb
            extends SQLiteOpenHelper {

        private static final String DB_NAME =
                "mahestan_offline.db";

        private static final int DB_VERSION = 1;

        LocalDb(Context context) {
            super(
                    context,
                    DB_NAME,
                    null,
                    DB_VERSION
            );
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS app_data (" +
                            "key TEXT PRIMARY KEY," +
                            "value TEXT NOT NULL" +
                            ")"
            );
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase db,
                int oldVersion,
                int newVersion
        ) {
            // Reserved for future database migrations.
        }

        synchronized String get(String key) {

            Cursor cursor =
                    getReadableDatabase().query(
                            "app_data",
                            new String[]{"value"},
                            "key=?",
                            new String[]{key},
                            null,
                            null,
                            null
                    );

            try {

                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }

                return "";

            } finally {

                cursor.close();
            }
        }

        synchronized void set(
                String key,
                String value
        ) {

            ContentValues values =
                    new ContentValues();

            values.put("key", key);
            values.put("value", value);

            getWritableDatabase().insertWithOnConflict(
                    "app_data",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
        }

        synchronized void delete(
                String key
        ) {

            getWritableDatabase().delete(
                    "app_data",
                    "key=?",
                    new String[]{key}
            );
        }
    }

    /**
     * JavaScript <-> Android bridge.
     */
    public static class Bridge {

        private final Context context;
        private final LocalDb db;

        Bridge(
                Context context,
                LocalDb db
        ) {
            this.context = context;
            this.db = db;
        }

        @JavascriptInterface
        public String getData(String key) {
            return db.get(key);
        }

        @JavascriptInterface
        public void setData(
                String key,
                String value
        ) {
            db.set(key, value);
        }

        @JavascriptInterface
        public void deleteData(
                String key
        ) {
            db.delete(key);
        }

        /**
         * Saves generated files such as PDF / Excel.
         */
        @JavascriptInterface
        public void saveBlob(
                String dataUrl,
                String name,
                String mime
        ) {

            try {

                int comma =
                        dataUrl.indexOf(',');

                String base64 =
                        comma >= 0
                                ? dataUrl.substring(comma + 1)
                                : dataUrl;

                byte[] bytes =
                        Base64.decode(
                                base64,
                                Base64.DEFAULT
                        );

                Uri uri = null;

                /*
                 * Android 10+
                 */
                if (Build.VERSION.SDK_INT >= 29) {

                    ContentValues values =
                            new ContentValues();

                    values.put(
                            MediaStore.Downloads.DISPLAY_NAME,
                            name
                    );

                    values.put(
                            MediaStore.Downloads.MIME_TYPE,
                            mime
                    );

                    values.put(
                            MediaStore.Downloads.IS_PENDING,
                            1
                    );

                    uri =
                            context
                                    .getContentResolver()
                                    .insert(
                                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                            values
                                    );

                    if (uri != null) {

                        try (
                                OutputStream output =
                                        context
                                                .getContentResolver()
                                                .openOutputStream(uri)
                        ) {

                            if (output != null) {
                                output.write(bytes);
                            }
                        }

                        values.clear();

                        values.put(
                                MediaStore.Downloads.IS_PENDING,
                                0
                        );

                        context
                                .getContentResolver()
                                .update(
                                        uri,
                                        values,
                                        null,
                                        null
                                );
                    }

                } else {

                    /*
                     * Older Android versions.
                     */
                    File directory =
                            context.getExternalFilesDir(
                                    Environment.DIRECTORY_DOWNLOADS
                            );

                    if (directory == null) {
                        directory =
                                context.getFilesDir();
                    }

                    if (!directory.exists()) {
                        directory.mkdirs();
                    }

                    File file =
                            new File(
                                    directory,
                                    name
                            );

                    try (
                            FileOutputStream output =
                                    new FileOutputStream(file)
                    ) {

                        output.write(bytes);
                    }
                }

                final Activity activity =
                        (Activity) context;

                activity.runOnUiThread(
                        () -> Toast.makeText(
                                context,
                                "فایل ذخیره شد: " + name,
                                Toast.LENGTH_SHORT
                        ).show()
                );

            } catch (Exception e) {

                final Activity activity =
                        (Activity) context;

                activity.runOnUiThread(
                        () -> Toast.makeText(
                                context,
                                "خطا در ذخیره فایل",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }
    }
}