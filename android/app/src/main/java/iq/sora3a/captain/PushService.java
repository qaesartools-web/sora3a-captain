package iq.sora3a.captain;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

// يستلم إشعار الطلب من السيرفر حتى والتطبيق مسكّر، ويرن رنة متواصلة لحد ما الكابتن يفتحه
public class PushService extends FirebaseMessagingService {
    public static final int RING_ID = 4711;
    private static final long RING_MAX_MS = 60_000;

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        if (!"order".equals(d.get("kind"))) return;
        if (MainActivity.inForeground) return; // التطبيق مفتوح قدامه — هو يرن بنفسه
        ring(d.get("title"), d.get("body"));
    }

    @Override
    public void onNewToken(String token) {
        getSharedPreferences("push", MODE_PRIVATE).edit().putString("token", token).apply();
    }

    private void ring(String title, String body) {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_FROM_RING, true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, App.CHANNEL_ORDERS)
                .setSmallIcon(R.drawable.ic_stat_order)
                .setColor(0xFFD9480F)
                .setContentTitle(title != null ? title : "🛵 طلب توصيل جديد")
                .setContentText(body != null ? body : "")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body != null ? body : ""))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(App.ringUri(this))
                .setVibrate(new long[]{0, 800, 400, 800, 400, 800})
                .setAutoCancel(true)
                .setTimeoutAfter(RING_MAX_MS)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true);
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // الصوت يتكرر لحد ما يضغط أو يسحب الإشعار
        try { NotificationManagerCompat.from(this).notify(RING_ID, n); } catch (SecurityException ignored) {}
    }
}
