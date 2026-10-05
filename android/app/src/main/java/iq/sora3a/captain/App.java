package iq.sora3a.captain;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class App extends Application {
    public static final String CHANNEL_ORDERS = "orders_ring_v1";

    @Override
    public void onCreate() {
        super.onCreate();
        initFirebase();
        createChannels();
    }

    private void initFirebase() {
        if (!FirebaseApp.getApps(this).isEmpty()) return;
        FirebaseOptions opts = new FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FB_APP_ID)
                .setApiKey(BuildConfig.FB_API_KEY)
                .setProjectId(BuildConfig.FB_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FB_SENDER_ID)
                .build();
        FirebaseApp.initializeApp(this, opts);
    }

    // قناة الطلبات: صوت رنة التلفون (طويلة) + اهتزاز + تطلع فوق الشاشة
    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ORDERS, getString(R.string.channel_orders), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(getString(R.string.channel_orders_desc));
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
        ch.enableLights(true);
        ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }
}
