package com.rx.pocketvps;

public class Config {

    // ══════════════════════════════════════
    //  YEH APNA DATA BHARO!
    // ══════════════════════════════════════

    // Telegram Bot Token (@BotFather se milega)
    public static final String BOT_TOKEN = "YOUR_BOT_TOKEN_HERE";

    // Tumhara Telegram Chat ID (Python se milega)
    public static final String CONTROL_CHAT_ID = "YOUR_CHAT_ID_HERE";

    // Is phone ka unique naam (har phone ke liye alag!)
    // Example: "OPPO_001", "REDMI_002", "SAMSUNG_003"
    public static final String DEVICE_ID = "OPPO_001";

    // SMS secret prefix (change kar lo apna)
    public static final String SMS_SECRET = "RX$VPS";

    // ══════════════════════════════════════
    //  YAHAN KUCH MAT CHHERO
    // ══════════════════════════════════════
    public static final String API_BASE = "https://api.telegram.org/bot" + BOT_TOKEN;
    public static final int POLL_TIMEOUT = 30; // seconds
    public static final int RECONNECT_DELAY = 5000; // ms
}
