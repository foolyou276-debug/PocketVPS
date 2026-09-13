package com.rx.pocketvps;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShellExecutor {
    private static final String TAG = "RX_Shell";
    private static final int MAX_OUTPUT = 3500;

    // Background processes track karo
    // {id: {pid, command, logFile}}
    private static Map<Integer, BgProcess> bgProcesses = new HashMap<>();
    private static int bgCounter = 1;

    static class BgProcess {
        int     pid;
        String  command;
        String  logFile;
        boolean alive;

        BgProcess(int pid, String command, String logFile) {
            this.pid     = pid;
            this.command = command;
            this.logFile = logFile;
            this.alive   = true;
        }
    }

    // ══════════════════════════
    //  NORMAL COMMAND (blocking)
    // ══════════════════════════

    public String execute(String command) {
        StringBuilder output = new StringBuilder();

        try {
            Log.d(TAG, "Executing: " + command);

            Process process = Runtime.getRuntime().exec(
                new String[]{"sh", "-c", command}
            );

            BufferedReader stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            BufferedReader stderr = new BufferedReader(
                new InputStreamReader(process.getErrorStream()));

            String line;

            while ((line = stdout.readLine()) != null) {
                output.append(line).append("\n");
                if (output.length() > MAX_OUTPUT) {
                    output.append("...[output too long]");
                    break;
                }
            }

            while ((line = stderr.readLine()) != null) {
                output.append("[!] ").append(line).append("\n");
                if (output.length() > MAX_OUTPUT) break;
            }

            process.waitFor();
            stdout.close();
            stderr.close();

        } catch (IOException | InterruptedException e) {
            output.append("[ERROR] ").append(e.getMessage());
            Log.e(TAG, "Shell error: " + e.getMessage());
        }

        String result = output.toString().trim();
        return result.isEmpty() ? "(koi output nahi)" : result;
    }

    // ══════════════════════════════════════════════════
    //  BACKGROUND COMMAND — Python band bhi ho to
    //  yeh process chalta rahega!
    //  Usage: BG_RUN python server.py
    // ══════════════════════════════════════════════════

    public String executeBackground(String command) {
        try {
            int id      = bgCounter++;
            String logFile = "/data/local/tmp/rx_bg_" + id + ".log";

            // nohup = process ko parent se alag karo
            // & = background mein dalo
            // Log file mein output save karo
            String fullCmd = "nohup sh -c '"
                + command.replace("'", "'\\''")
                + "' > " + logFile + " 2>&1 & echo $!";

            Process p = Runtime.getRuntime().exec(
                new String[]{"sh", "-c", fullCmd}
            );

            // PID read karo
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            String pidStr = reader.readLine();
            reader.close();
            p.waitFor();

            int pid = 0;
            if (pidStr != null && !pidStr.trim().isEmpty()) {
                pid = Integer.parseInt(pidStr.trim());
            }

            // Track karo
            bgProcesses.put(id, new BgProcess(pid, command, logFile));

            return "✅ Background mein chalu!\n"
                 + "  ID  : " + id + "\n"
                 + "  PID : " + pid + "\n"
                 + "  CMD : " + command + "\n"
                 + "  LOG : " + logFile + "\n\n"
                 + "Python band karo — process chalta rahega! 🔥\n"
                 + "Log dekhne ke liye: BG_LOG " + id;

        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    // ══════════════════════════
    //  BG PROCESS LIST
    // ══════════════════════════

    public String listBackground() {
        if (bgProcesses.isEmpty()) {
            return "Koi background process nahi chal raha.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔄 Background Processes:\n");
        sb.append("─────────────────────────────\n");

        for (Map.Entry<Integer, BgProcess> entry : bgProcesses.entrySet()) {
            int id       = entry.getKey();
            BgProcess bp = entry.getValue();

            // Check if alive
            String aliveCheck = execute("kill -0 " + bp.pid + " 2>/dev/null && echo ALIVE || echo DEAD");
            bp.alive = aliveCheck.contains("ALIVE");

            String status = bp.alive ? "🟢 Running" : "🔴 Stopped";
            sb.append("  [").append(id).append("] ")
              .append(status).append("\n")
              .append("  PID: ").append(bp.pid).append("\n")
              .append("  CMD: ").append(bp.command).append("\n\n");
        }

        return sb.toString().trim();
    }

    // ══════════════════════════
    //  BG PROCESS LOG DEKHO
    // ══════════════════════════

    public String getBackgroundLog(int id) {
        BgProcess bp = bgProcesses.get(id);
        if (bp == null) {
            return "ID " + id + " ka koi process nahi mila.";
        }

        // Last 50 lines of log
        String output = execute("tail -50 " + bp.logFile + " 2>/dev/null || echo '(log empty)'");
        return "📋 Log [ID=" + id + "] CMD: " + bp.command + "\n"
             + "─────────────────────\n"
             + output;
    }

    // ══════════════════════════
    //  BG PROCESS KILL
    // ══════════════════════════

    public String killBackground(int id) {
        BgProcess bp = bgProcesses.get(id);
        if (bp == null) {
            return "ID " + id + " nahi mila.";
        }

        execute("kill -9 " + bp.pid + " 2>/dev/null");
        bgProcesses.remove(id);

        return "✅ Process [ID=" + id + "] (PID: " + bp.pid + ") band kiya!\nCMD was: " + bp.command;
    }

    // Fire-and-forget (internal use)
    public void executeAsync(String command) {
        new Thread(() -> execute(command)).start();
    }
}
