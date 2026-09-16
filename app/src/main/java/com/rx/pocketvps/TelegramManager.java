package com.rx.pocketvps;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class TelegramManager {
    private static final String TAG             = "RX_Telegram";
    private static final long   RE_REG_INTERVAL = 120_000L;

    private final Context         context;
    private final NetworkManager  networkManager;
    private final ShellExecutor   shellExecutor;
    private final ExecutorService executor;
    private       boolean         running      = false;
    private       long            lastUpdateId = 0;

    public TelegramManager(Context context) {
        this.context        = context;
        this.networkManager = new NetworkManager(context);
        this.shellExecutor  = new ShellExecutor();
        this.executor       = Executors.newFixedThreadPool(4);
    }

    public void start() {
        running = true;
        registerDevice();
        executor.execute(this::pollLoop);
        executor.execute(this::reRegisterLoop);
    }

    public void stop() {
        running = false;
        sendOut("OFF:" + Config.DEVICE_ID);
        executor.shutdown();
    }

    private void registerDevice() {
        sendOut("REG:" + Config.DEVICE_ID
            + ":" + networkManager.getNetworkType()
            + ":" + networkManager.getBatteryLevel());
    }

    private void reRegisterLoop() {
        while (running) {
            try {
                Thread.sleep(RE_REG_INTERVAL);
                if (running) registerDevice();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void pollLoop() {
        while (running) {
            try {
                if (networkManager.isOnline()) fetchAndProcess();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            } catch (Exception e) {
                Log.e(TAG, "Poll: " + e.getMessage());
                try { Thread.sleep(Config.RECONNECT_DELAY); }
                catch (InterruptedException ie) { break; }
            }
        }
    }

    private void fetchAndProcess() throws Exception {
        String url = Config.CMD_API_BASE + "/getUpdates?offset=" + (lastUpdateId + 1)
                   + "&timeout=" + Config.POLL_TIMEOUT + "&allowed_updates=message";
        String resp = httpGet(url);
        if (resp == null) return;

        JSONObject json = new JSONObject(resp);
        if (!json.optBoolean("ok", false)) return;
        JSONArray results = json.getJSONArray("result");

        for (int i = 0; i < results.length(); i++) {
            JSONObject update = results.getJSONObject(i);
            lastUpdateId = update.getLong("update_id");
            if (!update.has("message")) continue;

            JSONObject msg    = update.getJSONObject("message");
            String     text   = msg.optString("text", "");
            String     chatId = String.valueOf(msg.getJSONObject("chat").getLong("id"));

            if (!chatId.equals(Config.CONTROL_CHAT_ID)) continue;

            if (text.equals("PING:" + Config.DEVICE_ID)) {
                sendOut("PONG:" + Config.DEVICE_ID
                    + ":" + networkManager.getNetworkType()
                    + ":" + networkManager.getBatteryLevel());
                continue;
            }

            if (text.equals("DISCOVER")) {
                registerDevice();
                continue;
            }

            String prefix = "CMD:" + Config.DEVICE_ID + ":";
            if (text.startsWith(prefix)) {
                processCommand(text.substring(prefix.length()));
            }
        }
    }

    private void processCommand(final String cmd) {
        executor.execute(() -> {
            String out;
            switch (cmd) {
                case "STATUS":
                    out = "📱 " + Config.DEVICE_ID + "\n"
                        + "🌐 " + networkManager.getNetworkType() + "\n"
                        + "🔋 " + networkManager.getBatteryLevel();
                    break;
                case "WIFI_ON":  networkManager.enableWifi();        out = "✅ WiFi ON";  break;
                case "WIFI_OFF": networkManager.disableWifi();       out = "✅ WiFi OFF"; break;
                case "DATA_ON":  networkManager.enableMobileData();  out = "✅ Data ON";  break;
                case "DATA_OFF": networkManager.disableMobileData(); out = "✅ Data OFF"; break;
                default:
                    if (cmd.startsWith("BG_RUN "))       out = shellExecutor.executeBackground(cmd.substring(7));
                    else if (cmd.equals("BG_LIST"))      out = shellExecutor.listBackground();
                    else if (cmd.startsWith("BG_LOG "))  { try { out = shellExecutor.getBackgroundLog(Integer.parseInt(cmd.substring(7).trim())); } catch (Exception e) { out = "Usage: BG_LOG <id>"; } }
                    else if (cmd.startsWith("BG_KILL ")) { try { out = shellExecutor.killBackground(Integer.parseInt(cmd.substring(8).trim())); } catch (Exception e) { out = "Usage: BG_KILL <id>"; } }
                    else out = shellExecutor.execute(cmd);
            }
            sendOut("OUT:" + Config.DEVICE_ID + ":" + out);
        });
    }

    public void sendOut(final String text) {
        executor.execute(() -> {
            try {
                String params = "chat_id=" + URLEncoder.encode(Config.CONTROL_CHAT_ID, "UTF-8")
                              + "&text="   + URLEncoder.encode(text, "UTF-8");
                httpPost(Config.CMD_API_BASE + "/sendMessage", params);
            } catch (Exception e) { Log.e(TAG, "Send: " + e.getMessage()); }
        });
    }

    public void sendMessage(final String text) { sendOut(text); }

    private String httpGet(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout((Config.POLL_TIMEOUT + 5) * 1000);
            if (c.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line);
            r.close(); c.disconnect();
            return s.toString();
        } catch (Exception e) { return null; }
    }

    private void httpPost(String urlStr, String params) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream os = c.getOutputStream();
            os.write(params.getBytes("UTF-8"));
            os.flush(); os.close();
            c.getResponseCode(); c.disconnect();
        } catch (Exception e) { Log.e(TAG, "POST: " + e.getMessage()); }
    }
}
