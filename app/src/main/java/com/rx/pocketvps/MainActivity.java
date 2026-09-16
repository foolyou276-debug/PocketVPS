package com.rx.pocketvps;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

// Yeh activity koi UI nahi dikhati
// Sirf service start karti hai aur band ho jati hai
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Service start karo
        Intent serviceIntent = new Intent(this, SilentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Activity turant band karo - koi UI nahi
        finish();
    }
}
