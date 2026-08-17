package com.largoscript.nodarkness;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import static com.largoscript.nodarkness.NoDarknessMod.log;

/**
 * Standard (no CustomSettingsLib) settings: a single canonical float 0.0–1.0.
 *
 * Backward compatibility: configs written while CustomSettingsLib was installed
 * store the value as an int percentage (0–100). The raw string disambiguates the
 * two formats — "0.2" (has a dot) is a float fraction, "20" (no dot) is a percent.
 * This also fixes the old bug where a saved "1" (1%) was misread as 1.0 (100%).
 */
class NoDarknessSettings extends ModSettings {

    static final String BRIGHTNESS_KEY = "darknessBrightness";

    @Override
    public void addSaveData(SaveData saveData) {
        saveData.addFloat(BRIGHTNESS_KEY, NoDarknessMod.darknessBrightness,
            "[0.0 - 1.0] Brightness level for dark areas. 0.0 = dark, 1.0 = full light. Default: "
                + NoDarknessMod.DEFAULT_BRIGHTNESS + " (" + (int) (NoDarknessMod.DEFAULT_BRIGHTNESS * 100) + "%)");
    }

    @Override
    public void applyLoadData(LoadData loadData) {
        if (loadData == null) {
            log("LoadData is null, using default brightness");
            return;
        }
        float loaded = NoDarknessMod.DEFAULT_BRIGHTNESS;
        try {
            String number = firstNumberIn(loadData.getFirstDataByName(BRIGHTNESS_KEY));
            if (number == null) {
                // Raw entry unavailable in this format — fall back to the old heuristic
                float value = loadData.getFloat(BRIGHTNESS_KEY, NoDarknessMod.DEFAULT_BRIGHTNESS);
                loaded = value > 1.0f ? value / 100.0f : value;
                log("Loaded brightness (fallback read): " + (loaded * 100) + "%");
            } else if (number.contains(".")) {
                loaded = Float.parseFloat(number);
                log("Loaded brightness (float format): " + (loaded * 100) + "%");
            } else {
                loaded = Integer.parseInt(number) / 100.0f;
                log("Loaded brightness (percent format from CustomSettingsLib): " + (loaded * 100) + "%");
            }
        } catch (Exception e) {
            log("Error reading brightness from config, using default: " + e.getMessage());
        }
        NoDarknessMod.darknessBrightness = Math.max(0.0f, Math.min(1.0f, loaded));
        if (NoDarknessMod.listener != null) {
            NoDarknessMod.listener.needsUpdate = true;
        }
    }

    /** Extracts the first numeric token from a raw config entry, or null. */
    private static String firstNumberIn(String raw) {
        if (raw == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?").matcher(raw);
        return m.find() ? m.group() : null;
    }
}
