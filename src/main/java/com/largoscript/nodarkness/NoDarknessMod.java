package com.largoscript.nodarkness;

import necesse.engine.GlobalData;
import necesse.engine.gameLoop.GameLoop;
import necesse.engine.input.Control;
import necesse.engine.input.InputID;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.state.MainGame;
import necesse.engine.state.State;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@ModEntry
public class NoDarknessMod {

    public static final String MOD_ID = "nodarkness";
    public static final String MOD_VERSION = "1.2.0";

    /** 0.0 = dark, 1.0 = full light (20% by default) */
    public static final float DEFAULT_BRIGHTNESS = 0.2f;

    public static boolean enabled = true;
    public static float darknessBrightness = DEFAULT_BRIGHTNESS;

    public static Control TOGGLE_NO_DARKNESS;
    static DarknessReconciler listener;

    static final Set<String> detectedConflictingMods = new HashSet<>();

    // How long postInit keeps waiting for the GameLoop before giving up
    private static final long LISTENER_RETRY_INTERVAL_MS = 500;
    private static final long LISTENER_RETRY_TIMEOUT_MS = 10_000;

    // Logging system (players notice these files and report issues from them — keep verbose)
    private static PrintWriter logFile = null;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static void log(String message) {
        log(message, false);
    }

    /**
     * Logs to console, file, and optionally to game chat.
     */
    static void log(String message, boolean showInChat) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = "[" + timestamp + "] [NoDarkness] " + message;

        System.out.println(logMessage);
        System.err.println(logMessage); // Also to stderr for visibility

        try {
            if (logFile == null) {
                File logDir = new File(logDirPath());
                logDir.mkdirs();
                logFile = new PrintWriter(new FileWriter(new File(logDir, "nodarkness.log"), true), true);
                logFile.println("=== No Darkness Mod Log Started ===");
            }
            logFile.println(logMessage);
        } catch (IOException e) {
            System.err.println("[NoDarkness] Failed to write to log file: " + e.getMessage());
        }

        if (showInChat) {
            chat(message);
        }
    }

    /** Sends a message to the in-game chat if it is available; silently does nothing otherwise. */
    static void chat(String message) {
        try {
            State currentState = GlobalData.getCurrentState();
            if (currentState instanceof MainGame) {
                MainGame mainGame = (MainGame) currentState;
                if (mainGame.getClient() != null && mainGame.getClient().chat != null) {
                    mainGame.getClient().chat.addMessage("[NoDarkness] " + message.replace("[NoDarkness] ", ""));
                }
            }
        } catch (Exception e) {
            // Chat may not exist yet while the game is loading
        }
    }

    private static String logDirPath() {
        String appDataPath = System.getenv("APPDATA");
        if (appDataPath == null) {
            appDataPath = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming";
        }
        return appDataPath + File.separator + "Necesse" + File.separator + "logs";
    }

    static String getLogFilePath() {
        return new File(logDirPath(), "nodarkness.log").getAbsolutePath();
    }

    public void init() {
        log("========== Mod initialized! ==========", true);
        log("Mod ID: " + MOD_ID + ", Version: " + MOD_VERSION);

        checkForConflictingMods();

        try {
            TOGGLE_NO_DARKNESS = Control.addModControl(
                new Control(InputID.KEY_L, "togglenodarkness", new StaticMessage("Toggle No Darkness"))
                    .setTooltip(new StaticMessage("Toggle darkness removal on/off"))
            );
            if (TOGGLE_NO_DARKNESS != null) {
                log("Control registered successfully: " + TOGGLE_NO_DARKNESS.id
                    + " (key=" + TOGGLE_NO_DARKNESS.getKey() + ")");
            } else {
                log("ERROR: Control.addModControl returned null!", true);
            }
        } catch (Exception e) {
            log("ERROR during Control registration: " + e.getMessage(), true);
            e.printStackTrace();
            TOGGLE_NO_DARKNESS = null;
        }
    }

    public ModSettings initSettings() {
        return NoDarknessSettingsFactory.createSettings();
    }

    public void postInit() {
        log("========== PostInit called ==========");

        NoDarknessSettingsFactory.syncFromCustomSettingsLib();

        if (tryRegisterListener()) {
            return;
        }

        // GameLoop may not exist yet during mod loading; poll for it on a single
        // bounded daemon thread instead of assuming a fixed startup timing.
        Thread retry = new Thread(() -> {
            long deadline = System.currentTimeMillis() + LISTENER_RETRY_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(LISTENER_RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                log("Retrying listener registration...");
                if (tryRegisterListener()) {
                    return;
                }
            }
            log("ERROR: Failed to register listener after " + (LISTENER_RETRY_TIMEOUT_MS / 1000)
                + " seconds! No Darkness will not work.", true);
            log("Please check the log file for more details: " + getLogFilePath());
        }, "NoDarkness-listener-retry");
        retry.setDaemon(true);
        retry.start();
    }

    private static synchronized boolean tryRegisterListener() {
        if (listener != null) {
            return true;
        }
        try {
            GameLoop gameLoop = GlobalData.getCurrentGameLoop();
            if (gameLoop == null) {
                log("GameLoop is null, will retry later. This is normal during mod loading.");
                return false;
            }
            DarknessReconciler reconciler = new DarknessReconciler();
            gameLoop.addGameLoopListener(reconciler);
            listener = reconciler;
            log("Listener registered successfully and added to GameLoop!", true);
            log("Enabled: " + enabled + ", Brightness: " + (darknessBrightness * 100) + "%");
            return true;
        } catch (IllegalStateException e) {
            log("GameLoop not ready yet: " + e.getMessage() + " (this is normal during mod loading)");
            return false;
        } catch (Exception e) {
            log("ERROR setting up listener: " + e.getMessage(), true);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Logs mods that might also touch lighting. Purely informational: the real
     * conflict detection is instance-based, inside DarknessReconciler.
     */
    private static void checkForConflictingMods() {
        try {
            detectedConflictingMods.clear();
            for (LoadedMod mod : ModLoader.getEnabledMods()) {
                String modId = mod.id.toLowerCase();
                String modName = mod.name.toLowerCase();
                if (modId.equals(MOD_ID)) {
                    continue;
                }
                if (modId.contains("light") || modId.contains("bright") || modId.contains("dark")
                    || modName.contains("light") || modName.contains("bright") || modName.contains("dark")) {
                    detectedConflictingMods.add(mod.name);
                    log("Detected potentially conflicting mod: " + mod.name + " (ID: " + mod.id + ")");
                }
            }
            if (!detectedConflictingMods.isEmpty()) {
                log("Found " + detectedConflictingMods.size()
                    + " potentially conflicting mod(s) that may modify lighting", true);
            }
        } catch (Exception e) {
            log("Error checking for conflicting mods: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void toggleEnabled() {
        enabled = !enabled;
        log(enabled ? "Enabled" : "Disabled", true);
        if (listener != null) {
            listener.needsUpdate = true;
        }
    }

    public static void setBrightness(float brightness) {
        darknessBrightness = Math.max(0.0f, Math.min(1.0f, brightness));
        log("Brightness set to: " + (darknessBrightness * 100) + "%");
        if (listener != null) {
            listener.needsUpdate = true;
        }
    }
}
