package com.almajedi.vip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.android.gms.location.LocationServices;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String SITE = "https://almajedi-repair-production.up.railway.app";
    WebView web;
    SharedPreferences p;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("vip", 0);
        getWindow().setStatusBarColor(0xff0b0d12);
        getWindow().setNavigationBarColor(0xff0b0d12);
        web = new WebView(this);
        web.setBackgroundColor(0xff0b0d12);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme()) || u.toString().startsWith(SITE)) return false;
                openExternal(u.toString());
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            status("تعذر فتح الرابط على هذا الجوال");
        }
    }

    void status(String msg) {
        final boolean on = p.getBoolean("enabled", false);
        final String js = "window.onNearStatus&&onNearStatus(" + JSONObject.quote(msg) + "," + on + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    class Bridge {
        @JavascriptInterface
        public boolean isNearEnabled() {
            return p.getBoolean("enabled", false);
        }

        @JavascriptInterface
        public void enableNear() {
            runOnUiThread(() -> enable());
        }

        @JavascriptInterface
        public void disableNear() {
            runOnUiThread(() -> {
                p.edit().putBoolean("enabled", false).apply();
                LocationServices.getGeofencingClient(MainActivity.this).removeGeofences(Fence.intent(MainActivity.this));
                status("تم إيقاف إشعار القرب");
            });
        }

        @JavascriptInterface
        public void open(String url) {
            runOnUiThread(() -> openExternal(url));
        }
    }

    void enable() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2);
            return;
        }
        if (!Fence.allowed(this)) {
            if (Build.VERSION.SDK_INT == 29) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 3);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("خطوة أخيرة")
                        .setMessage("من الإعدادات افتح «الأذونات» ثم «الموقع» واختر «السماح طوال الوقت»، ثم ارجع واضغط تفعيل.")
                        .setPositiveButton("فتح الإعدادات", (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                        .setNegativeButton("لاحقًا", null)
                        .show();
                status("يحتاج إذن الموقع «طوال الوقت»");
            }
            return;
        }
        Fence.start(this,
                () -> {
                    p.edit().putBoolean("enabled", true).apply();
                    status("تم التفعيل ✅ بنذكّرك لما تكون على بعد 200 متر من المحل");
                },
                e -> status("تعذر التفعيل: " + e.getMessage()));
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] s, int[] g) {
        super.onRequestPermissionsResult(r, s, g);
        if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) enable();
        else status("ما انعطت الصلاحية، الإشعار ما راح يشتغل");
    }
}
