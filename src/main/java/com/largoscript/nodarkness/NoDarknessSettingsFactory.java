package com.largoscript.nodarkness;

import necesse.engine.modLoader.ModSettings;

import static com.largoscript.nodarkness.NoDarknessMod.log;

/**
 * Chooses the settings backend: CustomSettingsLib (in-game slider) when the
 * library mod is loaded, plain ModSettings (.cfg file) otherwise.
 *
 * CustomSettingsLib is an optional dependency: all typed references to it live
 * in NoDarknessCustomSettings, whose class loading fails with a LinkageError
 * when the library is absent — which is caught here.
 */
class NoDarknessSettingsFactory {

    private static boolean customLibActive = false;

    static ModSettings createSettings() {
        try {
            ModSettings settings = NoDarknessCustomSettings.create();
            customLibActive = true;
            return settings;
        } catch (LinkageError e) {
            log("CustomSettingsLib not found, using standard ModSettings");
        } catch (Exception e) {
            log("Error creating CustomSettings, using standard ModSettings: " + e.getMessage());
        }
        return new NoDarknessSettings();
    }

    /** Re-reads the slider value from CustomSettingsLib, if that backend is active. */
    static void syncFromCustomSettingsLib() {
        if (!customLibActive) {
            return;
        }
        try {
            NoDarknessCustomSettings.syncFromLib();
        } catch (LinkageError | Exception e) {
            log("Error syncing from CustomSettingsLib: " + e.getMessage());
        }
    }
}
