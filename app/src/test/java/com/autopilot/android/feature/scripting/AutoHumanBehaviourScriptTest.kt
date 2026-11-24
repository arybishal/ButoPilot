package com.autopilot.android.feature.scripting

import org.junit.Assert.assertTrue
import org.junit.Test

class AutoHumanBehaviourScriptTest {

    @Test
    fun script_containsGuardConfigAndReloadLoop() {
        val script = AutoHumanBehaviourScript.SCRIPT

        assertTrue(script.contains("if (window.__autoBotRunning) return;"))
        assertTrue(script.contains("minDelayMs: 2000"))
        assertTrue(script.contains("maxDelayMs: 5000"))
        assertTrue(script.contains("actionsBeforeReload: 8"))
        assertTrue(script.contains("clickProbability: 0.4"))
        assertTrue(script.contains("window.location.reload()"))
        assertTrue(script.contains("setTimeout(runLoop, 2000);"))
    }
}
