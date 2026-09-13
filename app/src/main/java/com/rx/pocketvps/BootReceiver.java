package com.rx.pocketvps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "RX_Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Boot received: " + intent.getAction());

        // Service silently start karo
        Intent svc = new Intent(context, SilentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
