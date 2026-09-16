package com.rx.pocketvps;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import dalvik.system.DexClassLoader;

/**
 * Termux DEX files ko assets se load karke
 * app ke andar hi run karta hai.
 *
 * classes.dex + classes2.dex → app private storage
 *   → DexClassLoader → Termux classes available!
 */
public class TermuxLoader {
    private static final String TAG = "RX_TermuxLoader";

    private Context        context;
    private DexClassLoader dexLoader;
    private File           dexDir;

    // DEX files ke naam (assets mein)
    private static final String[] DEX_ASSETS = {
        "termux_classes.dex",
        "termux_classes2.dex"
    };

    public TermuxLoader(Context context) {
        this.context = context;
        this.dexDir  = new File(context.getFilesDir(), "termux_dex");
    }

    // ══════════════════════════════════════
    //  STEP 1: Assets se copy karo
    // ══════════════════════════════════════
    public boolean extractDexFiles() {
        try {
            if (!dexDir.exists()) dexDir.mkdirs();

            for (String asset : DEX_ASSETS) {
                File outFile = new File(dexDir, asset);

                // Already copied hai?
                if (outFile.exists() && outFile.length() > 0) {
                    Log.d(TAG, "Already extracted: " + asset);
                    continue;
                }

                Log.d(TAG, "Extracting: " + asset);

                // Assets se read karo
                InputStream  in  = context.getAssets().open(asset);
                OutputStream out = new FileOutputStream(outFile);

                byte[] buf = new byte[4096];
                int    len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }

                in.close();
                out.flush();
                out.close();

                Log.d(TAG, "Extracted: " + asset + " (" + outFile.length() + " bytes)");
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Extract error: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════
    //  STEP 2: DexClassLoader se load karo
    // ══════════════════════════════════════
    public boolean loadDex() {
        try {
            // Sab dex files ka path colon se join karo
            StringBuilder dexPath = new StringBuilder();
            for (String asset : DEX_ASSETS) {
                File f = new File(dexDir, asset);
                if (f.exists()) {
                    if (dexPath.length() > 0) dexPath.append(File.pathSeparator);
                    dexPath.append(f.getAbsolutePath());
                }
            }

            if (dexPath.length() == 0) {
                Log.e(TAG, "No DEX files found!");
                return false;
            }

            // Optimized DEX output dir
            File optDir = new File(dexDir, "opt");
            if (!optDir.exists()) optDir.mkdirs();

            // Load karo!
            dexLoader = new DexClassLoader(
                dexPath.toString(),
                optDir.getAbsolutePath(),
                null,
                context.getClassLoader()
            );

            Log.d(TAG, "DexClassLoader ready! Path: " + dexPath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "DEX load error: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════
    //  STEP 3: Termux service ko command bhejo
    // ══════════════════════════════════════
    public String runTermuxCommand(String command) {
        try {
            // Method 1: Termux RUN_COMMAND Intent
            Intent intent = new Intent();
            intent.setClassName(
                "com.termux",
                "com.termux.app.RunCommandService"
            );
            intent.setAction("com.termux.RUN_COMMAND");
            intent.putExtra("com.termux.RUN_COMMAND_PATH",    "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_RUNNER",  "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.execute.stdin",       command);
            intent.putExtra("com.termux.execute.background",  true);
            context.startService(intent);

            Log.d(TAG, "Termux command sent: " + command);
            return "✅ Termux command bheja: " + command;

        } catch (Exception e) {
            Log.e(TAG, "Termux run error: " + e.getMessage());
            // Fallback: Direct shell
            return new ShellExecutor().execute(command);
        }
    }

    // ══════════════════════════════════════
    //  STEP 4: Class load karke use karo
    // ══════════════════════════════════════
    public Class<?> loadClass(String className) {
        if (dexLoader == null) {
            Log.e(TAG, "DEX not loaded yet!");
            return null;
        }
        try {
            return dexLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class not found: " + className);
            return null;
        }
    }

    // ══════════════════════════════════════
    //  ALL-IN-ONE: Extract → Load → Ready
    // ══════════════════════════════════════
    public boolean initialize() {
        Log.d(TAG, "Initializing Termux DEX...");

        if (!extractDexFiles()) {
            Log.e(TAG, "Extract failed!");
            return false;
        }

        if (!loadDex()) {
            Log.e(TAG, "Load failed!");
            return false;
        }

        Log.d(TAG, "✅ Termux DEX initialized!");
        return true;
    }

    // DEX extract hua hai ya nahi
    public boolean isExtracted() {
        for (String asset : DEX_ASSETS) {
            File f = new File(dexDir, asset);
            if (!f.exists() || f.length() == 0) return false;
        }
        return true;
    }

    public DexClassLoader getDexLoader() {
        return dexLoader;
    }
}
