package com.largoscript.nodarkness;

import necesse.engine.modLoader.ModSettings;

/**
 * Factory for creating mod settings.
 * Attempts to use CustomSettingsLib if available.
 * If not, returns standard ModSettings.
 */
public class NoDarknessSettingsFactory {
    
    public static ModSettings createSettings() {
        // Try to create CustomSettingsLib settings
        try {
            return createCustomSettings();
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // CustomSettingsLib not available, use standard settings
            System.out.println("[NoDarkness] CustomSettingsLib not found, using standard ModSettings");
            return new NoDarknessMod.NoDarknessSettings();
        } catch (Exception e) {
            // Other error, use standard settings
            System.err.println("[NoDarkness] Error creating CustomSettings, using standard ModSettings: " + e.getMessage());
            return new NoDarknessMod.NoDarknessSettings();
        }
    }
    
    private static ModSettings createCustomSettings() throws Exception {
        // Use reflection to load CustomSettingsLib classes
        Class<?> customModSettingsClass = Class.forName("customsettingslib.settings.CustomModSettings");
        Class<?> intSettingClass = Class.forName("customsettingslib.components.settings.IntSetting");
        // BooleanSetting removed - using key binding for toggle instead
        Class<?> textSeparatorClass = Class.forName("customsettingslib.components.TextSeparator");
        
        // Create final reference for use in lambda
        final Object[] customSettingsRef = new Object[1];
        
        // Store reference to brightness setting for later access
        final Object[] brightnessSettingRef = new Object[1];
        
        // Helper method to update brightness (defined before use)
        final java.util.function.Consumer<Integer>[] updateBrightnessRef = new java.util.function.Consumer[1];
        updateBrightnessRef[0] = (brightnessPercent) -> {
            try {
                float oldBrightness = NoDarknessMod.darknessBrightness;
                float newBrightness = brightnessPercent / 100.0f;
                
                System.out.println("[NoDarkness] Settings saved: brightness=" + brightnessPercent + "% (old: " + 
                    (oldBrightness * 100) + "%, new: " + brightnessPercent + "%)");
                
                // Also show in chat for debugging
                try {
                    necesse.engine.state.State currentState = necesse.engine.GlobalData.getCurrentState();
                    if (currentState instanceof necesse.engine.state.MainGame) {
                        necesse.engine.state.MainGame mainGame = (necesse.engine.state.MainGame) currentState;
                        java.lang.reflect.Field clientField = necesse.engine.state.MainGame.class.getDeclaredField("client");
                        clientField.setAccessible(true);
                        necesse.engine.network.client.Client client = (necesse.engine.network.client.Client) clientField.get(mainGame);
                        if (client != null && client.chat != null) {
                            // Use localized message - Necesse should automatically add mod prefix
                            try {
                                Class<?> localMessageClass = Class.forName("necesse.engine.localization.message.LocalMessage");
                                // Use constructor with Object... replacements: "newValue", brightnessPercent, "oldValue", oldBrightness
                                Object localMessage = localMessageClass.getConstructor(String.class, String.class, Object[].class)
                                    .newInstance("chat", "brightnesschanged", 
                                        new Object[]{"newValue", String.valueOf(brightnessPercent), 
                                                     "oldValue", String.valueOf((int)(oldBrightness * 100))});
                                // Get translated string
                                java.lang.reflect.Method translateMethod = localMessageClass.getMethod("translate");
                                String translatedText = (String) translateMethod.invoke(localMessage);
                                // Check if translation was successful (not returning raw key)
                                if (translatedText != null && !translatedText.startsWith("chat.") && !translatedText.startsWith("nodarkness.")) {
                                    client.chat.addMessage(translatedText);
                                } else {
                                    // Fallback to English if translation not found
                                    client.chat.addMessage("Brightness: " + brightnessPercent + "% (was: " + 
                                        (int)(oldBrightness * 100) + "%)");
                                }
                            } catch (Exception e) {
                                // Fallback to English if localization fails
                                client.chat.addMessage("Brightness: " + brightnessPercent + "% (was: " + 
                                    (int)(oldBrightness * 100) + "%)");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore chat errors
                }
                
                // Update the brightness value
                NoDarknessMod.darknessBrightness = newBrightness;
                
                // Force immediate lighting update
                if (NoDarknessMod.listener != null) {
                    NoDarknessMod.listener.needsUpdate = true;
                    NoDarknessMod.listener.updateDarknessLighting();
                }
            } catch (Exception e) {
                System.err.println("[NoDarkness] Error updating brightness: " + e.getMessage());
            }
        };
        
        // Add listener for updating values
        // Note: onSaved is called BEFORE onSave(), so we need to read from newValue
        Runnable onSaved = () -> {
            try {
                Object customSettings = customSettingsRef[0];
                int brightnessPercent = 0;
                
                // Read from newValue (the value that will be saved)
                if (brightnessSettingRef[0] != null) {
                    try {
                        // Get newValue AtomicReference from IntSetting
                        java.lang.reflect.Field newValueField = brightnessSettingRef[0].getClass().getDeclaredField("newValue");
                        newValueField.setAccessible(true);
                        Object newValueAtomic = newValueField.get(brightnessSettingRef[0]);
                        
                        // Get the value from AtomicReference
                        java.lang.reflect.Method getMethod = newValueAtomic.getClass().getMethod("get");
                        brightnessPercent = (Integer) getMethod.invoke(newValueAtomic);
                        
                        System.out.println("[NoDarkness] Read from newValue: " + brightnessPercent + "%");
                    } catch (Exception e) {
                        System.err.println("[NoDarkness] Failed to read from newValue, will retry after save: " + e.getMessage());
                        // Schedule update after onSave() completes
                        new Thread(() -> {
                            try {
                                Thread.sleep(100); // Wait for onSave() to complete
                                Object brightnessValue = customModSettingsClass.getMethod("getSetting", String.class)
                                    .invoke(customSettings, "darknessBrightness");
                                int percent = (Integer) brightnessValue;
                                updateBrightnessRef[0].accept(percent);
                            } catch (Exception ex) {
                                System.err.println("[NoDarkness] Error in delayed update: " + ex.getMessage());
                            }
                        }).start();
                        return; // Exit early, will update in thread
                    }
                } else {
                    // Fallback: schedule update after onSave()
                    new Thread(() -> {
                        try {
                            Thread.sleep(100);
                            Object brightnessValue = customModSettingsClass.getMethod("getSetting", String.class)
                                .invoke(customSettings, "darknessBrightness");
                            int percent = (Integer) brightnessValue;
                            updateBrightnessRef[0].accept(percent);
                        } catch (Exception ex) {
                            System.err.println("[NoDarkness] Error in delayed update: " + ex.getMessage());
                        }
                    }).start();
                    return;
                }
                
                // Update immediately with value from newValue
                updateBrightnessRef[0].accept(brightnessPercent);
            } catch (Exception e) {
                System.err.println("[NoDarkness] Error in onSaved listener: " + e.getMessage());
                e.printStackTrace();
            }
        };
        
        // Create CustomModSettings with listener
        Object customSettings = customModSettingsClass.getConstructor(Runnable.class).newInstance(onSaved);
        customSettingsRef[0] = customSettings;
        
        // Add text separator
        customModSettingsClass.getMethod("addTextSeparator", String.class)
            .invoke(customSettings, "nodarknesssection");
        
        // Note: Enabled toggle removed - use the key binding (L key by default) to toggle instead
        // This is more intuitive and doesn't require opening settings
        
        // Add IntSetting for brightness (0-100%)
        // Use DisplayMode.BAR for slider
        Object displayModeEnum = Class.forName("customsettingslib.components.settings.IntSetting$DisplayMode")
            .getEnumConstants()[1]; // BAR = index 1
        
        // Create and store reference to brightness setting
        Object brightnessSetting = customModSettingsClass.getMethod("addIntSetting", 
            String.class, int.class, int.class, int.class, 
            Class.forName("customsettingslib.components.settings.IntSetting$DisplayMode"), int.class)
            .invoke(customSettings,
                "darknessBrightness", // Use same key as standard ModSettings
                (int)(NoDarknessMod.darknessBrightness * 100), // current value in percentage
                0,  // min
                100, // max
                displayModeEnum, // DisplayMode.BAR
                0   // decimals
            );
        brightnessSettingRef[0] = brightnessSetting;
        
        // Note: Key binding is handled automatically by Necesse in Settings -> Controls
        // The Control created via addModControl() automatically appears there
        
        // Values will be synchronized in postInit() after loading
        
        System.out.println("[NoDarkness] CustomSettingsLib settings created successfully!");
        return (ModSettings) customSettings;
    }
}

