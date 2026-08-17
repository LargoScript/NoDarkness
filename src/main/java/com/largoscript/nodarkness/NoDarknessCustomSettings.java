package com.largoscript.nodarkness;

import customsettingslib.components.settings.IntSetting;
import customsettingslib.settings.CustomModSettings;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.ModSettings;

import static com.largoscript.nodarkness.NoDarknessMod.log;

/**
 * CustomSettingsLib-backed settings: an in-game brightness slider (0–100%).
 *
 * Compiled directly against the library (compileOnly stub in libs/). This class
 * is only ever loaded after NoDarknessSettingsFactory confirms the library is
 * present, so the hard imports are safe.
 */
class NoDarknessCustomSettings {

    static ModSettings create() {
        CustomModSettings settings = new CustomModSettings();
        settings.addTextSeparator("nodarknesssection");
        settings.addIntSetting(
            NoDarknessSettings.BRIGHTNESS_KEY,
            (int) (NoDarknessMod.darknessBrightness * 100),
            0, 100,
            IntSetting.DisplayMode.BAR,
            0
        );
        // onSaved fires before IntSetting.onSave() persists the value, so read the
        // pending value from the (public) newValue field, not from getSetting().
        settings.onSavedListeners.add(() -> {
            try {
                IntSetting brightness = (IntSetting) settings.settingsMap.get(NoDarknessSettings.BRIGHTNESS_KEY);
                Integer percent = brightness != null ? brightness.newValue.get() : null;
                if (percent == null) {
                    percent = (Integer) settings.getSetting(NoDarknessSettings.BRIGHTNESS_KEY);
                }
                if (percent != null) {
                    applyBrightnessPercent(percent);
                }
            } catch (Exception e) {
                log("Error in onSaved listener: " + e.getMessage());
                e.printStackTrace();
            }
        });
        log("CustomSettingsLib settings created successfully!");
        return settings;
    }

    /** Reads the saved slider value back into the mod (used at startup / level change). */
    static void syncFromLib() {
        Object value = CustomModSettings.getModSetting(NoDarknessMod.MOD_ID, NoDarknessSettings.BRIGHTNESS_KEY);
        if (value instanceof Integer) {
            float synced = Math.max(0.0f, Math.min(1.0f, (Integer) value / 100.0f));
            if (Math.abs(synced - NoDarknessMod.darknessBrightness) > 0.001f) {
                log("Synced brightness from CustomSettingsLib: "
                    + (NoDarknessMod.darknessBrightness * 100) + "% -> " + (synced * 100) + "%");
                NoDarknessMod.darknessBrightness = synced;
                if (NoDarknessMod.listener != null) {
                    NoDarknessMod.listener.needsUpdate = true;
                }
            }
        }
    }

    private static void applyBrightnessPercent(int percent) {
        int oldPercent = (int) (NoDarknessMod.darknessBrightness * 100);
        log("Settings saved: brightness=" + percent + "% (was: " + oldPercent + "%)");

        String chatMessage = "Brightness: " + percent + "% (was: " + oldPercent + "%)";
        try {
            String translated = new LocalMessage("chat", "brightnesschanged",
                "newValue", String.valueOf(percent),
                "oldValue", String.valueOf(oldPercent)).translate();
            if (translated != null && !translated.startsWith("chat.") && !translated.startsWith("nodarkness.")) {
                chatMessage = translated;
            }
        } catch (Exception e) {
            // Use English fallback
        }
        NoDarknessMod.chat(chatMessage);

        NoDarknessMod.setBrightness(percent / 100.0f);
        if (NoDarknessMod.listener != null) {
            NoDarknessMod.listener.reconcile();
        }
    }
}
