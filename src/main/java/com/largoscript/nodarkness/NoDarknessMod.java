package com.largoscript.nodarkness;

import necesse.engine.GlobalData;
import necesse.engine.gameLoop.GameLoop;
import necesse.engine.gameLoop.GameLoopListener;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.Control;
import necesse.engine.input.Input;
import necesse.engine.input.InputEvent;
import necesse.engine.input.InputID;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.network.client.Client;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.state.MainGame;
import necesse.engine.state.State;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.components.FormTypingComponent;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
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
    
    public static boolean enabled = true;
    public static float darknessBrightness = 0.2f; // 0.0 = dark, 1.0 = full light (20% by default)
    
    static DarknessListener listener = null;
    private static boolean initialized = false;
    public static Control TOGGLE_NO_DARKNESS;
    
    private static Level lastLevel = null;
    private static NoDarknessSettings settings = new NoDarknessSettings();
    
    // Logging system
    private static PrintWriter logFile = null;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Conflict detection
    private static Set<String> detectedConflictingMods = new HashSet<>();
    private static boolean conflictWarningShown = false;
    private static int overrideResetCount = 0;
    private static long lastOverrideResetTime = 0;
    
    /**
     * Logs a message to console, file, and optionally to game chat
     */
    private static void log(String message) {
        log(message, false);
    }
    
    /**
     * Logs a message to console, file, and optionally to game chat
     * @param message The message to log
     * @param showInChat If true, also shows the message in game chat (if available)
     */
    private static void log(String message, boolean showInChat) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = "[" + timestamp + "] [NoDarkness] " + message;
        
        // Console output
        System.out.println(logMessage);
        System.err.println(logMessage); // Also to stderr for visibility
        
        // File output
        try {
            if (logFile == null) {
                String appDataPath = System.getenv("APPDATA");
                if (appDataPath == null) {
                    appDataPath = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming";
                }
                File logDir = new File(appDataPath + File.separator + "Necesse" + File.separator + "logs");
                logDir.mkdirs();
                File logFilePath = new File(logDir, "nodarkness.log");
                logFile = new PrintWriter(new FileWriter(logFilePath, true), true);
                logFile.println("=== No Darkness Mod Log Started ===");
                logFile.flush();
            }
            logFile.println(logMessage);
            logFile.flush();
        } catch (IOException e) {
            System.err.println("[NoDarkness] Failed to write to log file: " + e.getMessage());
        }
        
        // Game chat output (only for important messages and if chat is available)
        if (showInChat) {
            try {
                State currentState = GlobalData.getCurrentState();
                if (currentState instanceof MainGame) {
                    MainGame mainGame = (MainGame) currentState;
                    if (mainGame.getClient() != null && mainGame.getClient().chat != null) {
                        String chatMessage = message.replace("[NoDarkness] ", "");
                        mainGame.getClient().chat.addMessage("[NoDarkness] " + chatMessage);
                    }
                }
            } catch (Exception e) {
                // Ignore chat errors (game might not be fully loaded yet)
            }
        }
    }
    
    /**
     * Checks for conflicting mods that modify lighting
     */
    private static void checkForConflictingMods() {
        try {
            Set<String> knownLightingMods = new HashSet<>();
            knownLightingMods.add("cavebrightness");
            knownLightingMods.add("bettertorch");
            // Add more known lighting mods here
            
            detectedConflictingMods.clear();
            
            for (LoadedMod mod : ModLoader.getEnabledMods()) {
                String modId = mod.id.toLowerCase();
                String modName = mod.name.toLowerCase();
                
                // Check by ID
                if (knownLightingMods.contains(modId)) {
                    detectedConflictingMods.add(mod.name);
                    log("⚠️ Detected potentially conflicting mod by ID: " + mod.name + " (ID: " + mod.id + ")");
                }
                
                // Check by name keywords
                if (modId.contains("light") || modId.contains("bright") || modId.contains("dark") ||
                    modName.contains("light") || modName.contains("bright") || modName.contains("dark")) {
                    // Skip our own mod
                    if (!modId.equals("nodarkness")) {
                        detectedConflictingMods.add(mod.name);
                        log("⚠️ Detected potentially conflicting mod by name: " + mod.name + " (ID: " + mod.id + ")");
                    }
                }
            }
            
            if (!detectedConflictingMods.isEmpty()) {
                log("⚠️ Found " + detectedConflictingMods.size() + " potentially conflicting mod(s) that may modify lighting", true);
                for (String modName : detectedConflictingMods) {
                    log("   - " + modName);
                }
            }
        } catch (Exception e) {
            log("Error checking for conflicting mods: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void init() {
        log("========== Mod initialized! ==========", true);
        log("init() called - mod is being loaded by Necesse");
        log("Mod ID: nodarkness, Version: 1.1.2");
        
        // Check for conflicting mods
        checkForConflictingMods();
        
        // Register key for toggling (default: L key)
        try {
        TOGGLE_NO_DARKNESS = Control.addModControl(
            new Control(
                InputID.KEY_L, 
                "togglenodarkness",
                new StaticMessage("Toggle No Darkness")
            ).setTooltip(new StaticMessage("Toggle darkness removal on/off"))
        );
        
            if (TOGGLE_NO_DARKNESS != null) {
                log("✅ Control registered successfully: " + TOGGLE_NO_DARKNESS.id);
                log("Control key: " + TOGGLE_NO_DARKNESS.getKey());
            } else {
                log("❌ ERROR: Control.addModControl returned null!", true);
            }
        } catch (Exception e) {
            log("❌ ERROR during Control registration: " + e.getMessage(), true);
            e.printStackTrace();
            TOGGLE_NO_DARKNESS = null;
        }
    }
    
    public ModSettings initSettings() {
        // Try to use CustomSettingsLib if available
        return NoDarknessSettingsFactory.createSettings();
    }
    
    public void postInit() {
        log("========== PostInit called ==========");
        log("postInit() called - all mods have been loaded");
        log("Current initialized state: " + initialized);
        log("Listener state: " + (listener != null ? "created" : "null"));
        
        // Sync values from CustomSettingsLib if it's being used
        // (applyLoadData is called after initSettings, so we sync here)
        syncSettingsFromCustomLib();
        
        // Initialize listener
        log("Attempting to initialize GameLoopListener...");
        trySetupListener();
        log("After trySetupListener(), initialized state: " + initialized);
        
        // If failed to initialize immediately, try multiple times with increasing delays
        // This improves reliability, especially on macOS where initialization timing may vary
        if (!initialized) {
            // Try after 1 second
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    if (!initialized) {
                        log("Retrying listener initialization after 1 second...");
                        trySetupListener();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            
            // Try after 3 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    if (!initialized) {
                        log("Retrying listener initialization after 3 seconds...");
                        trySetupListener();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            
            // Try after 5 seconds (when game should be fully loaded)
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    if (!initialized) {
                        log("Retrying listener initialization after 5 seconds...");
                        trySetupListener();
                        if (!initialized) {
                            log("ERROR: Failed to initialize listener after multiple attempts! No Darkness will not work.", true);
                            log("Please check the log file for more details: " + getLogFilePath());
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    /**
     * Returns the path to the log file (for error messages)
     */
    private static String getLogFilePath() {
        try {
            String appDataPath = System.getenv("APPDATA");
            if (appDataPath == null) {
                appDataPath = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming";
            }
            File logDir = new File(appDataPath + File.separator + "Necesse" + File.separator + "logs");
            return new File(logDir, "nodarkness.log").getAbsolutePath();
        } catch (Exception e) {
            return "logs/nodarkness.log";
        }
    }
    
    private static void syncSettingsFromCustomLib() {
        // Try to sync values from CustomSettingsLib if it's being used
        try {
            Class<?> customModSettingsClass = Class.forName("customsettingslib.settings.CustomModSettings");
            java.lang.reflect.Method getModSettingMethod = customModSettingsClass.getMethod("getModSetting", String.class, String.class);
            
            // Get enabled value (optional, may not exist)
            try {
                Object enabledValue = getModSettingMethod.invoke(null, "nodarkness", "enabled");
                if (enabledValue != null && enabledValue instanceof Boolean) {
                    enabled = (Boolean) enabledValue;
                }
            } catch (Exception e) {
                // enabled setting may not exist, ignore
            }
            
            // Get brightness value
            try {
                Object brightnessValue = getModSettingMethod.invoke(null, "nodarkness", "darknessBrightness");
                if (brightnessValue != null && brightnessValue instanceof Integer) {
                    int brightnessPercent = (Integer) brightnessValue;
                    darknessBrightness = Math.max(0.0f, Math.min(1.0f, brightnessPercent / 100.0f));
                }
            } catch (Exception e) {
                // brightness setting may not exist or be invalid, ignore
                log("Error reading brightness from CustomSettingsLib: " + e.getMessage());
            }
            
            // Note: Key binding is handled automatically by Necesse in Settings -> Controls
            
            log("Synced settings from CustomSettingsLib: enabled=" + enabled + 
                ", brightness=" + (darknessBrightness * 100) + "%");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // CustomSettingsLib is not being used, this is normal
            // Don't log as error
        } catch (NoSuchMethodException e) {
            // Method signature changed, skip
            log("CustomSettingsLib API method not found: " + e.getMessage());
        } catch (Exception e) {
            // Other error, log but don't crash
            log("Error syncing from CustomSettingsLib: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void syncSettingsFromConfig() {
        // Sync settings from both CustomSettingsLib and standard config
        // This ensures we always use the actual saved value
        //
        // PRIORITY ORDER:
        // 1. CustomSettingsLib (if available) - has highest priority
        //    CustomSettingsLib saves values to the same .cfg file, but uses its own format
        //    When CustomSettingsLib is used, it replaces standard ModSettings
        // 2. Standard .cfg file (if CustomSettingsLib is not available)
        //
        // Note: CustomSettingsLib values override .cfg file values when both exist
        // because CustomSettingsLib's applyLoadData() is called after standard ModSettings
        
        // First try CustomSettingsLib (highest priority)
        boolean syncedFromCustomLib = false;
        try {
            Class<?> customModSettingsClass = Class.forName("customsettingslib.settings.CustomModSettings");
            java.lang.reflect.Method getModSettingMethod = customModSettingsClass.getMethod("getModSetting", String.class, String.class);
            
            // Get brightness value from CustomSettingsLib
            Object brightnessValue = getModSettingMethod.invoke(null, "nodarkness", "darknessBrightness");
            if (brightnessValue != null && brightnessValue instanceof Integer) {
                int brightnessPercent = (Integer) brightnessValue;
                float newBrightness = Math.max(0.0f, Math.min(1.0f, brightnessPercent / 100.0f));
                if (Math.abs(newBrightness - darknessBrightness) > 0.001f) {
                    log("Syncing brightness from CustomSettingsLib: " + 
                        (darknessBrightness * 100) + "% -> " + brightnessPercent + "%");
                    darknessBrightness = newBrightness;
                }
                syncedFromCustomLib = true;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // CustomSettingsLib is not being used, will try standard config
        } catch (Exception e) {
            // Error, will try standard config
        }
        
        // If CustomSettingsLib is not available, try to reload from standard config file
        if (!syncedFromCustomLib) {
            try {
                // Get mod settings file path using Necesse API
                Class<?> modLoaderClass = Class.forName("necesse.engine.modLoader.ModLoader");
                java.lang.reflect.Method getModMethod = modLoaderClass.getMethod("getLoadedMod", String.class);
                Object loadedMod = getModMethod.invoke(null, "nodarkness");
                
                if (loadedMod == null) {
                    return; // Mod not loaded, skip
                }
                
                // Get config file path using Settings.clientModDir()
                Class<?> settingsClass = Class.forName("necesse.engine.Settings");
                Class<?> loadedModClass = Class.forName("necesse.engine.modLoader.LoadedMod");
                java.lang.reflect.Method clientModDirMethod = settingsClass.getMethod("clientModDir", loadedModClass);
                String modFilePath = (String) clientModDirMethod.invoke(null, loadedMod);
                
                if (modFilePath == null || modFilePath.isEmpty()) {
                    return; // Invalid path, skip
                }
                
                java.io.File modFile = new java.io.File(modFilePath);
                
                if (!modFile.exists() || !modFile.isFile()) {
                    return; // File doesn't exist or is not a file, skip
                }
                
                // Only proceed if file is readable and has content
                if (modFile.length() == 0) {
                    return; // Empty file, skip
                }
                
                // Get settings instance
                java.lang.reflect.Method getSettingsMethod = loadedMod.getClass().getMethod("getSettings");
                Object modSettings = getSettingsMethod.invoke(loadedMod);
                
                if (modSettings instanceof NoDarknessSettings) {
                    try {
                        LoadData loadData = new LoadData(modFile);
                        float oldBrightness = darknessBrightness;
                        ((NoDarknessSettings) modSettings).applyLoadData(loadData);
                        if (Math.abs(oldBrightness - darknessBrightness) > 0.001f) {
                            log("Synced brightness from config file: " + 
                                (oldBrightness * 100) + "% -> " + (darknessBrightness * 100) + "%");
                        }
                    } catch (Exception loadException) {
                        // LoadData or applyLoadData failed, but don't crash
                        log("Error loading config file: " + loadException.getMessage());
                        // Don't rethrow, just use current value
                    }
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // Classes not found, this is normal if API changed
                // Don't log as error, just skip
            } catch (NoSuchMethodException e) {
                // Method signature changed, skip
                log("API method not found (API may have changed): " + e.getMessage());
            } catch (Exception e) {
                // Other error, log but don't crash
                log("Error syncing from config: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private static void trySetupListener() {
        if (initialized) {
            log("Listener already initialized, skipping setup");
            return;
        }

        try {
            log("=== trySetupListener() called ===");
            log("Current state - initialized: " + initialized + ", listener: " + (listener != null ? "exists" : "null"));
            
            GameLoop gameLoop = GlobalData.getCurrentGameLoop();
            log("GameLoop from GlobalData: " + (gameLoop != null ? "found" : "null"));
            
            if (gameLoop != null) {
                log("GameLoop found, creating listener...");
                if (listener == null) {
                    listener = new DarknessListener();
                    log("✅ DarknessListener created successfully");
                } else {
                    log("DarknessListener already exists, reusing it");
                }
                
                log("Adding listener to GameLoop...");
                gameLoop.addGameLoopListener(listener);
                initialized = true;
                log("✅✅✅ Listener initialized successfully and added to GameLoop! ✅✅✅", true);
                log("TOGGLE_NO_DARKNESS control: " + 
                    (TOGGLE_NO_DARKNESS != null ? "OK (key=" + TOGGLE_NO_DARKNESS.getKey() + ")" : "NULL"));
                log("Enabled: " + enabled + ", Brightness: " + (darknessBrightness * 100) + "%");
                log("Mod is now fully functional!");
            } else {
                log("⚠️ GameLoop is null, will retry later. This is normal during mod loading.");
                log("GameLoop becomes available when the game world is loaded.");
            }
        } catch (IllegalStateException e) {
            log("⚠️ GameLoop not ready yet: " + e.getMessage() + " (this is normal during mod loading)");
            log("Stack trace:", false);
            e.printStackTrace();
        } catch (Exception e) {
            log("❌ Error setting up listener: " + e.getMessage(), true);
            log("Exception type: " + e.getClass().getName());
            log("Stack trace:", false);
            e.printStackTrace();
        }
    }
    
    public static void toggleEnabled() {
        enabled = !enabled;
        String statusMessage = enabled ? "Enabled" : "Disabled";
        log(statusMessage, true);
        
        // Mark for update
        if (listener != null) {
            listener.needsUpdate = true;
        }
    }
    
    public static void setBrightness(float brightness) {
        darknessBrightness = Math.max(0.0f, Math.min(1.0f, brightness));
        log("Brightness set to: " + (darknessBrightness * 100) + "%");
        
        // Mark for update
        if (listener != null) {
            listener.needsUpdate = true;
        }
    }
    
    static class DarknessListener implements GameLoopListener {
        @Override
        public boolean isDisposed() {
            return false;
        }
        
        @Override
        public void drawTick(TickManager tickManager) {
            // Update lighting in drawTick (after other mods have run)
            // This ensures our override is set after other mods that might modify lighting
            try {
                if (lastLevel == null || lastLevel.isServer()) {
                    return;
                }
                
                // Always check and apply lighting if mod is enabled and in dark area
                // This ensures lighting is applied even if needsUpdate flag was missed
                if (enabled) {
                    boolean isDarkArea = lastLevel.isCave;
                    if (!isDarkArea) {
                        try {
                            java.lang.reflect.Field worldEntityField = Level.class.getDeclaredField("worldEntity");
                            worldEntityField.setAccessible(true);
                            Object worldEntity = worldEntityField.get(lastLevel);
                            if (worldEntity != null) {
                                java.lang.reflect.Method isNightMethod = worldEntity.getClass().getMethod("isNight");
                                Object nightResult = isNightMethod.invoke(worldEntity);
                                if (nightResult instanceof Boolean) {
                                    isDarkArea = (Boolean) nightResult;
                                }
                            }
                        } catch (Exception e) {
                            // If reflection fails, just use cave check
                        }
                    }
                    
                    // Check if override was reset by another mod
                    if (isDarkArea && lastLevel.lightManager != null) {
                        GameLight currentOverride = lastLevel.lightManager.ambientLightOverride;
                        if (currentOverride == null && !needsUpdate) {
                            // Override was cleared by another mod - detect conflict
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastOverrideResetTime > 1000) { // Only count once per second
                                overrideResetCount++;
                                lastOverrideResetTime = currentTime;
                                
                                if (overrideResetCount >= 3 && !conflictWarningShown) {
                                    // Show warning after 3 resets
                                    conflictWarningShown = true;
                                    showConflictWarning();
                                }
                            }
                        }
                    }
                    
                    // If we're in a dark area and override is null or needs update, apply it
                    if (isDarkArea && (needsUpdate || shouldReapplyOverride() || lastLevel.lightManager.ambientLightOverride == null)) {
                        updateDarknessLighting();
                    }
                } else if (needsUpdate) {
                    // If mod is disabled, clear override
                    updateDarknessLighting();
                }
            } catch (Exception e) {
                log("❌ Error in drawTick: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        /**
         * Shows a warning about conflicting mods
         */
        private void showConflictWarning() {
            try {
                State currentState = GlobalData.getCurrentState();
                if (currentState instanceof MainGame) {
                    MainGame mainGame = (MainGame) currentState;
                    if (mainGame.getClient() != null && mainGame.getClient().chat != null) {
                        // Try to use localized message
                        try {
                            LocalMessage msg = new LocalMessage("chat", "conflictwarning");
                            String translatedMsg = msg.translate();
                            if (translatedMsg != null && !translatedMsg.startsWith("chat.") && !translatedMsg.startsWith("nodarkness.")) {
                                mainGame.getClient().chat.addMessage(translatedMsg);
                            } else {
                                // Fallback to English
                                String warningMsg = "⚠️ [NoDarkness] Warning: Another mod is modifying lighting. " +
                                    "No Darkness may not work correctly. " +
                                    "Try moving No Darkness to the bottom of the mod load order.";
                                mainGame.getClient().chat.addMessage(warningMsg);
                            }
                        } catch (Exception e) {
                            // Fallback to English
                            String warningMsg = "⚠️ [NoDarkness] Warning: Another mod is modifying lighting. " +
                                "No Darkness may not work correctly. " +
                                "Try moving No Darkness to the bottom of the mod load order.";
                            mainGame.getClient().chat.addMessage(warningMsg);
                        }
                        
                        // Also log to file
                        log("⚠️ CONFLICT DETECTED: Override was reset " + overrideResetCount + " times by another mod");
                        if (!detectedConflictingMods.isEmpty()) {
                            log("   Detected conflicting mods: " + String.join(", ", detectedConflictingMods));
                        }
                    }
                }
            } catch (Exception e) {
                log("Error showing conflict warning: " + e.getMessage());
            }
        }
        
        private boolean shouldReapplyOverride() {
            // Check if override was changed by another mod
            if (lastLevel == null || lastLevel.lightManager == null) {
                return false;
            }
            
            // If we should have an override but it's null or different, reapply it
            if (enabled) {
                boolean isDarkArea = lastLevel.isCave;
                if (!isDarkArea) {
                    try {
                        java.lang.reflect.Field worldEntityField = Level.class.getDeclaredField("worldEntity");
                        worldEntityField.setAccessible(true);
                        Object worldEntity = worldEntityField.get(lastLevel);
                        if (worldEntity != null) {
                            java.lang.reflect.Method isNightMethod = worldEntity.getClass().getMethod("isNight");
                            Object nightResult = isNightMethod.invoke(worldEntity);
                            if (nightResult instanceof Boolean) {
                                isDarkArea = (Boolean) nightResult;
                            }
                        }
                    } catch (Exception e) {
                        // If reflection fails, just use cave check
                    }
                }
                
                if (isDarkArea) {
                    // We should have an override, check if it's correct
                    GameLight currentOverride = lastLevel.lightManager.ambientLightOverride;
                    if (currentOverride == null) {
                        // Override was cleared by another mod, need to reapply
                        return true;
                    }
                    // Check if the light level is correct
                    float expectedLevel = darknessBrightness * 150.0f;
                    // Note: GameLight doesn't expose its level directly, so we'll just reapply if null
                    // This is a simple check - if override is null when it shouldn't be, reapply
                }
            }
            
            return false;
        }
        
        @Override
        public void frameTick(TickManager tickManager, GameWindow gameWindow) {
            try {
                if (gameWindow == null) {
                    return;
                }

                State currentState = GlobalData.getCurrentState();
                if (!(currentState instanceof MainGame)) {
                    return;
                }
                
                // Get Client through MainGame (using reflection)
                MainGame mainGame = (MainGame) currentState;
                Client client = null;
                try {
                    java.lang.reflect.Field clientField = MainGame.class.getDeclaredField("client");
                    clientField.setAccessible(true);
                    client = (Client) clientField.get(mainGame);
                } catch (Exception e) {
                    return;
                }
                
                if (client == null || client.levelManager == null) {
                    return;
                }
                
                Level level = client.levelManager.getLevel();
                
                if (level == null || level.isServer()) {
                    return;
                }
                
                // Check if level changed
                if (level != lastLevel) {
                    lastLevel = level;
                    needsUpdate = true;
                    
                    // Sync settings from config/CustomSettingsLib before applying
                    // This ensures we use the actual saved value, not a stale one
                    syncSettingsFromConfig();
                    
                    // Force immediate update when level changes
                    updateDarknessLighting();
                    
                    // Key binding change notification removed - L key is now the default
                }
                
                // Check key press: use Input directly (API-safe). Control.isPressed() can fail
                // when key is unbound (-1), overlap rules change, or tick order differs.
                if (!FormTypingComponent.isCurrentlyTyping()) {
                    Input input = gameWindow.getInput();
                    int key = (TOGGLE_NO_DARKNESS != null && TOGGLE_NO_DARKNESS.getKey() != -1)
                        ? TOGGLE_NO_DARKNESS.getKey() : InputID.KEY_L;
                    if (input != null && input.hasChanged(key) && input.isPressed(key)) {
                        log("Toggle key pressed! Current enabled: " + enabled);
                        toggleEnabled();
                        needsUpdate = true;
                    }
                }
                
            } catch (Exception e) {
                log("❌ Error in frameTick: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        boolean needsUpdate = true;
        
        public void updateDarknessLighting() {
            try {
                if (lastLevel == null || lastLevel.isServer()) {
                    return;
                }
                
                // Check if this is a dark area (cave or night on surface)
                boolean isDarkArea = lastLevel.isCave;
                
                // Also check if it's night on surface
                if (!isDarkArea) {
                    try {
                        java.lang.reflect.Field worldEntityField = Level.class.getDeclaredField("worldEntity");
                        worldEntityField.setAccessible(true);
                        Object worldEntity = worldEntityField.get(lastLevel);
                        if (worldEntity != null) {
                            java.lang.reflect.Method isNightMethod = worldEntity.getClass().getMethod("isNight");
                            Object nightResult = isNightMethod.invoke(worldEntity);
                            if (nightResult instanceof Boolean) {
                                isDarkArea = (Boolean) nightResult;
                            }
                        }
                    } catch (Exception e) {
                        // If reflection fails, just use cave check
                    }
                }
                
                // If not a dark area, reset override
                if (!isDarkArea) {
                    if (lastLevel.lightManager != null) {
                        lastLevel.lightManager.ambientLightOverride = null;
                    }
                    needsUpdate = false;
                    return;
                }
                
                // If dark area and mod enabled
                if (enabled && lastLevel.lightManager != null) {
                    // Calculate light level (0.0 - 150.0)
                    // darknessBrightness: 0.0 = dark, 1.0 = full light
                    // Note: Higher brightness value = brighter light
                    float lightLevel = darknessBrightness * 150.0f;
                    
                    // Create new light
                    GameLight newLight = lastLevel.lightManager.newLight(lightLevel);
                    
                    // Set override (in drawTick, after other mods have run)
                    // This ensures compatibility with other mods that might modify lighting
                    lastLevel.lightManager.ambientLightOverride = newLight;
                    needsUpdate = false;
                } else if (!enabled && lastLevel.lightManager != null) {
                    // If mod disabled, reset override
                    lastLevel.lightManager.ambientLightOverride = null;
                    needsUpdate = false;
                }
            } catch (Exception e) {
                log("❌ Error updating darkness lighting: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    static class NoDarknessSettings extends ModSettings {
        @Override
        public void addSaveData(SaveData saveData) {
            saveData.addFloat("darknessBrightness", darknessBrightness, "[0.0 - 1.0] Brightness level for dark areas. 0.0 = dark, 1.0 = full light. Default: 0.2 (20%)");
        }
        
        @Override
        public void applyLoadData(LoadData loadData) {
            if (loadData == null) {
                log("LoadData is null, using default brightness");
                return; // Use current/default value
            }
            
            // Try to read as float first (standard ModSettings saves as float 0.0-1.0)
            // Then try as int (CustomSettingsLib saves as int 0-100)
            // Problem: getFloat() can read int (20) as float (20.0), so we need to check the value
            float loadedBrightness;
            try {
                // First try to read as float (standard ModSettings format: 0.0-1.0)
                float floatValue = loadData.getFloat("darknessBrightness", 0.2f);
                
                // If value is > 1.0, it's likely stored as int (0-100) from CustomSettingsLib
                // This can happen if getFloat() reads "20" as 20.0f
                if (floatValue > 1.0f) {
                    // Treat as int percentage (0-100), convert to float (0.0-1.0)
                    loadedBrightness = floatValue / 100.0f;
                    log("Loaded brightness from CustomSettingsLib (int read as float): " + 
                        (int)floatValue + "% -> " + (loadedBrightness * 100) + "%");
                } else {
                    // Normal float value (0.0-1.0)
                    loadedBrightness = floatValue;
                    log("Loaded brightness from standard config (float): " + (loadedBrightness * 100) + "%");
                }
            } catch (Exception e) {
                // Error reading as float, try as int (CustomSettingsLib format: 0-100)
                try {
                    int brightnessPercent = loadData.getInt("darknessBrightness", 20);
                    loadedBrightness = brightnessPercent / 100.0f;
                    log("Loaded brightness from CustomSettingsLib (int): " + brightnessPercent + "%");
                } catch (Exception e2) {
                    // If both fail, use default
                    log("Error reading brightness from config, using default: " + e2.getMessage());
                    loadedBrightness = 0.2f; // Default 20%
                }
            }
            
            darknessBrightness = Math.max(0.0f, Math.min(1.0f, loadedBrightness));
            
            // Mark for update after config is loaded
            if (listener != null) {
                listener.needsUpdate = true;
            }
        }
    }
}
