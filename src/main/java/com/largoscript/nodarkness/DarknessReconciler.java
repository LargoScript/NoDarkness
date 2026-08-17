package com.largoscript.nodarkness;

import necesse.engine.GlobalData;
import necesse.engine.gameLoop.GameLoopListener;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.Input;
import necesse.engine.input.InputID;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.engine.state.State;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.components.FormTypingComponent;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

import static com.largoscript.nodarkness.NoDarknessMod.log;

/**
 * Per-tick reconciler: computes the desired ambient light override from
 * (enabled, level darkness, brightness) and keeps LightManager.ambientLightOverride
 * in that state. Self-healing: if another mod clears our override, it is reapplied
 * on the next draw tick — and counted as a detected conflict.
 */
class DarknessReconciler implements GameLoopListener {

    /** Ambient override light level is 0..150 in Necesse's light model. */
    private static final float MAX_LIGHT_LEVEL = 150.0f;

    private static final int CONFLICT_RESETS_BEFORE_WARNING = 3;
    private static final long CONFLICT_COUNT_DEBOUNCE_MS = 1000;

    boolean needsUpdate = true;

    private Level lastLevel = null;
    /** The exact GameLight instance we last installed — identity is our ownership check. */
    private GameLight lastApplied = null;

    private int overrideResetCount = 0;
    private long lastOverrideResetTime = 0;
    private boolean conflictWarningShown = false;

    @Override
    public boolean isDisposed() {
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
            Client client = ((MainGame) currentState).getClient();
            if (client == null || client.levelManager == null) {
                return;
            }
            Level level = client.levelManager.getLevel();
            if (level == null || level.isServer()) {
                return;
            }

            if (level != lastLevel) {
                lastLevel = level;
                lastApplied = null;
                needsUpdate = true;
                NoDarknessSettingsFactory.syncFromCustomSettingsLib();
                reconcile();
            }

            handleToggleKey(gameWindow);
        } catch (Exception e) {
            log("Error in frameTick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void drawTick(TickManager tickManager) {
        // Runs after other mods' logic each frame, so our override wins by default
        // and a cleared override is restored within one frame.
        try {
            if (lastLevel == null || lastLevel.isServer()) {
                return;
            }
            detectConflict();
            if (needsUpdate || (NoDarknessMod.enabled && isDark(lastLevel)
                && lastLevel.lightManager.ambientLightOverride == null)) {
                reconcile();
            }
        } catch (Exception e) {
            log("Error in drawTick: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleToggleKey(GameWindow gameWindow) {
        if (FormTypingComponent.isCurrentlyTyping()) {
            return;
        }
        // Poll Input directly: Control.isPressed() can fail when the key is
        // unbound (-1) or overlap rules change between game versions.
        Input input = gameWindow.getInput();
        int key = (NoDarknessMod.TOGGLE_NO_DARKNESS != null && NoDarknessMod.TOGGLE_NO_DARKNESS.getKey() != -1)
            ? NoDarknessMod.TOGGLE_NO_DARKNESS.getKey() : InputID.KEY_L;
        if (input != null && input.hasChanged(key) && input.isPressed(key)) {
            log("Toggle key pressed! Current enabled: " + NoDarknessMod.enabled);
            NoDarknessMod.toggleEnabled();
        }
    }

    private static boolean isDark(Level level) {
        if (level.isCave) {
            return true;
        }
        return level.getWorldEntity() != null && level.getWorldEntity().isNight();
    }

    /**
     * Counts the cases where our installed override disappeared without us clearing
     * it — that means another mod is actively fighting over the ambient light.
     */
    private void detectConflict() {
        if (!NoDarknessMod.enabled || needsUpdate || lastApplied == null) {
            return;
        }
        if (!isDark(lastLevel) || lastLevel.lightManager == null) {
            return;
        }
        GameLight current = lastLevel.lightManager.ambientLightOverride;
        if (current == lastApplied) {
            return; // Still ours — no conflict
        }
        long now = System.currentTimeMillis();
        if (now - lastOverrideResetTime > CONFLICT_COUNT_DEBOUNCE_MS) {
            overrideResetCount++;
            lastOverrideResetTime = now;
            if (overrideResetCount >= CONFLICT_RESETS_BEFORE_WARNING && !conflictWarningShown) {
                conflictWarningShown = true;
                showConflictWarning();
            }
        }
    }

    private void showConflictWarning() {
        String fallback = "Warning: Another mod is modifying lighting. "
            + "No Darkness may not work correctly. "
            + "Try moving No Darkness to the bottom of the mod load order.";
        String message = fallback;
        try {
            String translated = new LocalMessage("chat", "conflictwarning").translate();
            if (translated != null && !translated.startsWith("chat.") && !translated.startsWith("nodarkness.")) {
                message = translated;
            }
        } catch (Exception e) {
            // Use English fallback
        }
        NoDarknessMod.chat(message);
        log("CONFLICT DETECTED: Override was reset " + overrideResetCount + " times by another mod");
        if (!NoDarknessMod.detectedConflictingMods.isEmpty()) {
            log("   Suspected mods: " + String.join(", ", NoDarknessMod.detectedConflictingMods));
        }
    }

    /** Brings ambientLightOverride into the desired state. Idempotent. */
    void reconcile() {
        try {
            if (lastLevel == null || lastLevel.isServer() || lastLevel.lightManager == null) {
                return;
            }
            if (NoDarknessMod.enabled && isDark(lastLevel)) {
                GameLight light = lastLevel.lightManager.newLight(
                    NoDarknessMod.darknessBrightness * MAX_LIGHT_LEVEL);
                lastLevel.lightManager.ambientLightOverride = light;
                lastApplied = light;
            } else {
                // Only remove the override if it is ours (or already gone) —
                // never stomp another mod's override on the way out.
                GameLight current = lastLevel.lightManager.ambientLightOverride;
                if (current == null || current == lastApplied) {
                    lastLevel.lightManager.ambientLightOverride = null;
                }
                lastApplied = null;
            }
            needsUpdate = false;
        } catch (Exception e) {
            log("Error updating darkness lighting: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
