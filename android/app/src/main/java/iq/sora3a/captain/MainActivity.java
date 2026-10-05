package iq.sora3a.captain;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

// تطبيق الكابتن: نفس صفحة الكابتن من الإنترنت (أي تحديث يوصل فوراً) + رنة أصلية من الأندرويد
public class MainActivity extends Activity {
    public static final String EXTRA_FROM_RING = "fromRing";
    public static volatile boolean inForeground = false;

    private static final String HOST = "qaesartools-web.github.io";
    private static final int REQ_NOTIF = 1, REQ_LOCATION = 2, REQ_FILE = 3;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private boolean tokenWanted = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        handleRingIntent(getIntent());
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // الرنة داخل التطبيق تشتغل بدون لمس
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(s.getUserAgentString() + " SoraCaptainApp/" + BuildConfig.VERSION_NAME);
        web.addJavascriptInterface(new Bridge(), "SoraNative");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (("https".equals(scheme) || "http".equals(scheme)) && HOST.equals(u.getHost())) return false;
                openExternal(u); // اتصال، واتساب، الخريطة… تفتح بتطبيقاتها
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) { cb.invoke(origin, true, false); return; }
                geoOrigin = origin; geoCallback = cb;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try { startActivityForResult(params.createIntent(), REQ_FILE); }
                catch (ActivityNotFoundException e) { fileCallback = null; return false; }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(BuildConfig.START_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleRingIntent(intent);
    }

    // فتح من إشعار الطلب: يشتغل فوق قفل الشاشة
    private void handleRingIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_FROM_RING, false)) return;
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        inForeground = true;
        stopRing();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inForeground = false;
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(false); setTurnScreenOn(false); }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true); // يبقى شغّال بالخلفية
    }

    private void stopRing() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(PushService.RING_ID);
    }

    private boolean hasPerm(String p) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (ActivityNotFoundException ignored) {}
    }

    // ── الإشعارات: إذن + رمز FCM يرجع للصفحة ──
    private void startTokenFlow() {
        tokenWanted = true;
        if (Build.VERSION.SDK_INT >= 33 && !hasPerm(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        fetchToken();
    }

    private void fetchToken() {
        if (!tokenWanted) return;
        tokenWanted = false;
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
                if (t.isSuccessful() && t.getResult() != null) sendToken(t.getResult(), null);
                else sendToken(null, t.getException() != null ? String.valueOf(t.getException().getMessage()) : "no_token");
            });
        } catch (Exception e) {
            sendToken(null, String.valueOf(e.getMessage()));
        }
    }

    private void sendToken(String token, String err) {
        boolean allowed = Build.VERSION.SDK_INT < 33 || hasPerm(Manifest.permission.POST_NOTIFICATIONS);
        String js = "window.__soraNativeToken&&window.__soraNativeToken(" + JSONObject.quote(token == null ? "" : token) + ","
                + JSONObject.quote(err == null ? "" : err) + "," + allowed + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_NOTIF) fetchToken();
        if (code == REQ_LOCATION && geoCallback != null) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, ok, false);
            geoCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }

    // ── الجسر بين الصفحة والتطبيق ──
    class Bridge {
        @JavascriptInterface
        public void requestToken() { runOnUiThread(MainActivity.this::startTokenFlow); }

        @JavascriptInterface
        public String version() { return BuildConfig.VERSION_NAME; }

        // يفتح إعدادات إشعارات التطبيق (لو الكابتن رفض الإذن)
        @JavascriptInterface
        public void openNotificationSettings() {
            runOnUiThread(() -> {
                Intent i;
                if (Build.VERSION.SDK_INT >= 26) i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                else i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                try { startActivity(i); } catch (ActivityNotFoundException ignored) {}
            });
        }
    }
}
