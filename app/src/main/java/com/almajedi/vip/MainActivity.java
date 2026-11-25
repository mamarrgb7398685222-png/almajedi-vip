package com.almajedi.vip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.android.gms.location.LocationServices;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String SITE = "https://almajedi-repair-production.up.railway.app";
    static final Pattern CARD = Pattern.compile("/(card|wallet)/([a-f0-9]{64})");
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
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith(SITE)) {
                    Matcher m = CARD.matcher(r.getUrl().getPath() == null ? "" : r.getUrl().getPath());
                    if (m.matches()) {
                        saveToken(m.group(2));
                        if ("wallet".equals(m.group(1))) openExternal(u);
                        web.loadUrl("file:///android_asset/index.html");
                        return true;
                    }
                    return false;
                }
                if (u.startsWith("file:")) return false;
                openExternal(u);
                return true;
            }
        });
        setContentView(web);
        handleLink(getIntent());
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (handleLink(i)) web.loadUrl("file:///android_asset/index.html");
    }

    boolean handleLink(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null || d.getPath() == null) return false;
        Matcher m = CARD.matcher(d.getPath());
        if (!m.matches()) return false;
        saveToken(m.group(2));
        return true;
    }

    void saveToken(String t) {
        p.edit().putString("card", t).apply();
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

    void js(final String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    void status(String msg) {
        js("window.onNearStatus&&onNearStatus(" + JSONObject.quote(msg) + "," + p.getBoolean("enabled", false) + ")");
    }

    static String text(String html) {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    void loadCard() {
        final String token = p.getString("card", "");
        if (token.isEmpty()) { js("window.onCard&&onCard(null)"); return; }
        final String cached = p.getString("cardJson", "");
        if (!cached.isEmpty()) js("window.onCard&&onCard(" + cached + ")");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(SITE + "/card/" + token).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                if (code == 404) { p.edit().remove("card").remove("cardJson").apply(); js("window.onCard&&onCard(null)"); return; }
                if (code != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                String h = sb.toString();
                JSONObject o = new JSONObject();
                Matcher n = Pattern.compile("<h2>(.*?)</h2>", Pattern.DOTALL).matcher(h);
                Matcher pt = Pattern.compile("<strong>\\s*(-?\\d+)\\s*</strong>").matcher(h);
                if (!n.find() || !pt.find()) return;
                o.put("name", text(n.group(1)));
                o.put("points", Integer.parseInt(pt.group(1)));
                JSONArray rw = new JSONArray();
                Matcher li = Pattern.compile("<li>(.*?)\\s*—\\s*(\\d+)\\s*نقطة</li>", Pattern.DOTALL).matcher(h);
                while (li.find()) {
                    JSONObject r = new JSONObject();
                    r.put("name", text(li.group(1)));
                    r.put("points", Integer.parseInt(li.group(2)));
                    rw.put(r);
                }
                o.put("rewards", rw);
                String json = o.toString();
                p.edit().putString("cardJson", json).apply();
                js("window.onCard&&onCard(" + json + ")");
            } catch (Exception ignored) {
            }
        }).start();
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

        @JavascriptInterface
        public void loadCard() {
            MainActivity.this.loadCard();
        }

        @JavascriptInterface
        public void saveCard(String token) {
            if (token != null && token.matches("[a-f0-9]{64}")) {
                saveToken(token);
                p.edit().remove("cardJson").apply();
                MainActivity.this.loadCard();
            }
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
