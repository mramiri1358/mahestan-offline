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
import android.webkit.WebViewClient;
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
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

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

    private void showFatalError(final String message) {
        runOnUiThread(() -> {
            TextView errorView = new TextView(this);
            errorView.setText(
                    "خطای اجرای برنامه\n\n" +
                    message +
                    "\n\nلطفاً این صفحه را برای بررسی ارسال کنید."
            );
            errorView.setTextSize(16);
            errorView.setTextColor(Color.WHITE);
            errorView.setGravity(Gravity.CENTER);
            errorView.setPadding(30, 30, 30, 30);
            errorView.setBackgroundColor(Color.rgb(120, 20, 20));
            setContentView(errorView);
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            db = new LocalDb(this);

            web = new WebView(this);

            WebView.setWebContentsDebuggingEnabled(false);

            WebSettings settings = web.getSettings();

            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);

            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(true);

            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(false);

            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);

            settings.setMediaPlaybackRequiresUserGesture(false);

            web.setOverScrollMode(View.OVER_SCROLL_NEVER);

            web.addJavascriptInterface(
                    new Bridge(this, db),
                    "AndroidBridge"
            );

            final WebViewAssetLoader assetLoader =
                    new WebViewAssetLoader.Builder()
                            .addPathHandler(
                                    "/assets/",
                                    new WebViewAssetLoader.AssetsPathHandler(this)
                            )
                            .build();

            web.setWebViewClient(new WebViewClientCompat() {

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

                @Override
                public void onReceivedError(
                        WebView view,
                        WebResourceRequest request,
                        android.webkit.WebResourceError error
                ) {
                    if (request.isForMainFrame()) {
                        String description =
                                error != null
                                        ? String.valueOf(error.getDescription())
                                        : "خطای نامشخص";

                        showFatalError(
                                "WebView نتوانست صفحه اصلی را اجرا کند:\n" +
                                description
                        );
                    }

                    super.onReceivedError(view, request, error);
                }

                @Override
                public void onReceivedHttpError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceResponse response
                ) {
                    if (request.isForMainFrame()) {
                        showFatalError(
                                "خطای HTTP هنگام باز کردن برنامه:\n" +
                                response.getStatusCode()
                        );
                    }

                    super.onReceivedHttpError(
                            view,
                            request,
                            response
                    );
                }
            });

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

                                return true;

                            } catch (Exception e) {

                                uploadCallback = null;

                                Toast.makeText(
                                        MainActivity.this,
                                        "خطا در باز کردن انتخاب فایل",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return false;
                            }
                        }

                        @Override
                        public boolean onConsoleMessage(
                                android.webkit.ConsoleMessage consoleMessage
                        ) {
                            android.util.Log.e(
                                    "MAHESTAN_WEB",
                                    consoleMessage.message() +
                                    " -- خط " +
                                    consoleMessage.lineNumber()
                            );

                            return true;
                        }
                    }
            );

            setContentView(web);

            web.loadUrl(
                    "https://appassets.androidplatform.net/assets/www/index.html"
            );

        } catch (Exception e) {

            showFatalError(
                    "خطای Android:\n" +
                    e.getClass().getName() +
                    "\n\n" +
                    String.valueOf(e.getMessage())
            );
        }
    }

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
                requestCode == FILE_CHOOSER &&
                uploadCallback != null
        ) {

            Uri[] result = null;

            if (
                    resultCode == RESULT_OK &&
                    data != null
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

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

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

        synchronized void delete(String key) {

            getWritableDatabase().delete(
                    "app_data",
                    "key=?",
                    new String[]{key}
            );
        }
    }

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
        public void deleteData(String key) {
            db.delete(key);
        }

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

                    Uri uri =
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

                    File directory =
                            context.getExternalFilesDir(
                                    Environment.DIRECTORY_DOWNLOADS
                            );

                    if (directory == null) {
                        directory = context.getFilesDir();
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

                runOnUiThreadToast(
                        "فایل ذخیره شد: " + name
                );

            } catch (Exception e) {

                runOnUiThreadToast(
                        "خطا در ذخیره فایل"
                );
            }
        }

        private void runOnUiThreadToast(
                final String message
        ) {

            ((Activity) context).runOnUiThread(
                    () -> Toast.makeText(
                            context,
                            message,
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }
    }
}