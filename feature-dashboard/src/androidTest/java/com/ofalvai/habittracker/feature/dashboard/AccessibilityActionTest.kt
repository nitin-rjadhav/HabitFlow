package com.ofalvai.habittracker.feature.dashboard

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ofalvai.habittracker.core.model.Action
import com.ofalvai.habittracker.core.ui.theme.PreviewTheme
import com.ofalvai.habittracker.feature.dashboard.ui.dashboard.view.fiveday.ActionCircle
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the Android accessibility API without a Compose test rule.
 * The Compose rule has hung before entering the test body on CI emulators.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityActionTest {
    private val tag = "HabitFlowA11yTest"

    @Test
    fun accessibilityClickCompletesAndUndoesHabit() {
        Log.i(tag, "STAGE 1: test body entered")
        val completed = mutableStateOf(false)
        val stateObserved = AtomicBoolean(false)
        Log.i(tag, "STAGE 2: launching activity")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            Log.i(tag, "STAGE 3: activity launched")
            scenario.onActivity { activity ->
                activity.setContent {
                    PreviewTheme {
                        ActionCircle(
                            activeColor = Color.Blue,
                            action = Action(id = 1, toggled = completed.value, timestamp = null),
                            onToggle = {
                                completed.value = it
                                stateObserved.set(it)
                            },
                            isHighlighted = true,
                            onSinglePress = {}
                        )
                    }
                }
            }
            Log.i(tag, "STAGE 4: content set; waiting for accessibility node")
            clickThroughAccessibility("Mark Done")
            Log.i(tag, "STAGE 5: first ACTION_CLICK accepted")
            waitForState(stateObserved, true)
            Log.i(tag, "STAGE 6: completed; performing undo")
            // Wait for Compose to expose the *updated* accessibility action.
            // Otherwise a rapid second ACTION_CLICK may invoke a stale toggle.
            clickThroughAccessibility("Mark not done")
            Log.i(tag, "STAGE 7: undo ACTION_CLICK accepted")
            waitForState(stateObserved, false)
            Log.i(tag, "STAGE 8: undo verified")
        }
    }

    private fun waitForState(state: AtomicBoolean, expected: Boolean) {
        repeat(40) {
            if (state.get() == expected) return
            Thread.sleep(250)
        }
        assertTrue("Expected completion state $expected, got ${state.get()}", state.get() == expected)
    }

    private fun clickThroughAccessibility(expectedActionLabel: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(40) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                try {
                    val target = findClickableAction(root, expectedActionLabel)
                    if (target != null) {
                        try {
                            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                        } finally {
                            target.recycle()
                        }
                    }
                } finally {
                    root.recycle()
                }
            }
            Thread.sleep(250)
        }
        assertTrue("Android accessibility ACTION_CLICK ($expectedActionLabel) was not accepted", false)
    }

    private fun findClickableAction(
        node: AccessibilityNodeInfo,
        expectedActionLabel: String
    ): AccessibilityNodeInfo? {
        // Matching the actual ACTION_CLICK label verifies Compose has recomposed
        // to the requested state, rather than re-clicking a stale semantics node.
        val hasExpectedClickAction = node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.ACTION_CLICK &&
                action.label?.toString() == expectedActionLabel
        }
        if (node.isClickable && hasExpectedClickAction) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val match = findClickableAction(child, expectedActionLabel)
                if (match != null) return match
            } finally {
                child.recycle()
            }
        }
        return null
    }
}
