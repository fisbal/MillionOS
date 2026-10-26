package com.martinb.millionos;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * MillionOS as an Android app: the site in a full-screen WebView, so the content always updates with the site
 * and works offline after the first launch (the site's service worker). What a web page cannot do is bridged:
 * printing, and saving downloads to the phone's Downloads folder. Music plays without an extra tap.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://fisbal.github.io/MillionOS/";
    private static final String HOST = "fisbal.github.io";
    // Routes blob:/data: downloads and window.print() to the bridge below (the site checks for "MillionOSApp/").
    private static final String HOOK = "(function(){if(window.__mosApp)return;window.__mosApp=1;try{"
            + "document.documentElement.classList.add('in-app');window.print=function(){MillionOSApp.print()};"
            + "var oc=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){var a=this;"
            + "if(a.download&&/^(blob:|data:)/.test(a.href)){var n=a.download;fetch(a.href).then(function(r){return r.blob()})"
            + ".then(function(b){var fr=new FileReader();fr.onload=function(){var s=String(fr.result);"
            + "MillionOSApp.save(n,b.type||'application/octet-stream',s.slice(s.indexOf(',')+1))};fr.readAsDataURL(b)});return}"
            + "return oc.call(this)}}catch(e){}})()";

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#050a14"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " MillionOSApp/" + versionName());

        web.addJavascriptInterface(new Bridge(), "MillionOSApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/MillionOS")) return false;
                // Anything else (Wikipedia, videos, official sites) opens in the browser.
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(HOOK, null);
            }
        });

        Uri link = getIntent() != null ? getIntent().getData() : null;
        if (state != null) web.restoreState(state);
        else web.loadUrl(link != null ? link.toString() : HOME);
        if (state == null) UpdateCheck.run(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null) web.loadUrl(intent.getData().toString());
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.1";
        }
    }

    private boolean fr() {
        return Locale.getDefault().getLanguage().equals("fr");
    }

    /** Called from the page as MillionOSApp.print() and MillionOSApp.save(name, mime, base64). */
    private final class Bridge {
        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter adapter = web.createPrintDocumentAdapter("MillionOS");
                pm.print("MillionOS", adapter, new PrintAttributes.Builder().build());
            });
        }

        @JavascriptInterface
        public void save(String name, String mime, String base64) {
            String safe = name == null || name.isEmpty() ? "MillionOS" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MillionOS");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("no Downloads");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(data); }
                } else {
                    @SuppressWarnings("deprecation")
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MillionOS");
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                    try (OutputStream os = new FileOutputStream(new File(dir, safe))) { os.write(data); }
                }
                toast((fr() ? "Enregistré dans Téléchargements/MillionOS : " : "Saved to Downloads/MillionOS: ") + safe);
            } catch (Exception e) {
                toast((fr() ? "Enregistrement impossible : " : "Could not save: ") + e.getMessage());
            }
        }
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
