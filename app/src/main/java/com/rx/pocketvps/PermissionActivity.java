package com.rx.pocketvps;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

public class PermissionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Battery optimization OFF
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Exception ignored) {}

        // OPPO startup manager
        openOppoAutoStart();

        // Service start karo
        Intent svc = new Intent(this, SilentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        // ICON MAT CHHUPAO - finish karo sirf
        finish();
    }

    private void openOppoAutoStart() {
        String[][] intents = {
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startupapp.StartupAppListActivity"},
            {"com.oplus.safecenter",   "com.oplus.safecenter.permission.startupapp.StartupAppListActivity"},
            {"com.oppo.safe",          "com.oppo.safe.permission.startup.StartupAppListActivity"},
        };
        for (String[] pair : intents) {
            try {
                Intent i = new Intent();
                i.setClassName(pair[0], pair[1]);
                startActivity(i);
                return;
            } catch (Exception ignored) {}
        }
    }
}
