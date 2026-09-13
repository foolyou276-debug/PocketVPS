package com.rx.pocketvps;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import android.content.Intent;

import java.lang.reflect.Method;

public class NetworkManager {
    private static final String TAG = "RX_Network";
    private Context context;

    public NetworkManager(Context context) {
        this.context = context;
    }

    // ══════════════════════════
    //  WIFI CONTROL
    // ══════════════════════════

    public void enableWifi() {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wm.setWifiEnabled(true);
                Log.d(TAG, "WiFi enabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "WiFi enable error: " + e.getMessage());
        }
    }

    public void disableWifi() {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wm.setWifiEnabled(false);
                Log.d(TAG, "WiFi disabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "WiFi disable error: " + e.getMessage());
        }
    }

    // ══════════════════════════
    //  MOBILE DATA CONTROL
    // ══════════════════════════

    public void enableMobileData() {
        try {
            // Method 1: Shell command
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "svc data enable"});
            Log.d(TAG, "Mobile data enabled via shell");
        } catch (Exception e) {
            try {
                // Method 2: Reflection
                ConnectivityManager cm = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                Method setMobileData = cm.getClass()
                        .getDeclaredMethod("setMobileDataEnabled", boolean.class);
                setMobileData.setAccessible(true);
                setMobileData.invoke(cm, true);
            } catch (Exception e2) {
                Log.e(TAG, "Mobile data enable error: " + e2.getMessage());
            }
        }
    }

    public void disableMobileData() {
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "svc data disable"});
            Log.d(TAG, "Mobile data disabled via shell");
        } catch (Exception e) {
            try {
                ConnectivityManager cm = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                Method setMobileData = cm.getClass()
                        .getDeclaredMethod("setMobileDataEnabled", boolean.class);
                setMobileData.setAccessible(true);
                setMobileData.invoke(cm, false);
            } catch (Exception e2) {
                Log.e(TAG, "Mobile data disable error: " + e2.getMessage());
            }
        }
    }

    // ══════════════════════════
    //  STATUS GETTERS
    // ══════════════════════════

    public String getNetworkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return "Offline";

                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) return "Offline";

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WiFi";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Data";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) {
                    return ni.getTypeName();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Network type error: " + e.getMessage());
        }
        return "Offline";
    }

    public boolean isOnline() {
        return !getNetworkType().equals("Offline");
    }

    public String getBatteryLevel() {
        try {
            Intent batteryIntent = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    int pct = (int) ((level / (float) scale) * 100);
                    boolean charging = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                            == BatteryManager.BATTERY_STATUS_CHARGING;
                    return pct + "%" + (charging ? "⚡" : "");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Battery error: " + e.getMessage());
        }
        return "?%";
    }
}
