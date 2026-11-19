package com.ofalvai.habittracker.feature.dashboard

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ofalvai.habittracker.core.model.Action
import com.ofalvai.habittracker.core.ui.theme.PreviewTheme
import com.ofalvai.habittracker.feature.dashboard.ui.dashboard.view.fiveday.ActionCircle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Invokes Android's accessibility ACTION_CLICK via UiAutomation rather than
 * Compose performClick or an adb touch/long-press gesture.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityActionTest {
    private val tag = "HabitFlowA11yTest"
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accessibilityClickCompletesAndUndoesHabit() {
        Log.i(tag, "STAGE 1: test entered")
        val completed = mutableStateOf(false)
        Log.i(tag, "STAGE 2: before setContent")
        composeRule.setContent {
            PreviewTheme {
                ActionCircle(
                    activeColor = Color.Blue,
                    action = Action(id = 1, toggled = completed.value, timestamp = null),
                    onToggle = { completed.value = it },
                    isHighlighted = true,
                    onSinglePress = {}
                )
            }
        }
        Log.i(tag, "STAGE 3: setContent returned; before waitForIdle")
        composeRule.waitForIdle()
        Log.i(tag, "STAGE 4: idle; before first ACTION_CLICK")
        clickThroughAccessibility()
        Log.i(tag, "STAGE 5: first ACTION_CLICK accepted")
        composeRule.waitUntil(10_000) { completed.value }
        Log.i(tag, "STAGE 6: completed; before undo ACTION_CLICK")
        clickThroughAccessibility()
        Log.i(tag, "STAGE 7: undo ACTION_CLICK accepted")
        composeRule.waitUntil(10_000) { !completed.value }
        Log.i(tag, "STAGE 8: undo verified")
    }

    private fun clickThroughAccessibility() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var clicked = false
        for (attempt in 0 until 20) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                try {
                    val target = findClickableAction(root)
                    if (target != null) {
                        try {
                            clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        } finally {
                            target.recycle()
                        }
                    }
                } finally {
                    root.recycle()
                }
            }
            if (clicked) break
            Thread.sleep(250)
        }
        assertTrue("Android accessibility ACTION_CLICK was not accepted", clicked)
    }

    private fun findClickableAction(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString().orEmpty()
        val state = node.stateDescription?.toString().orEmpty()
        if (node.isClickable && (text.contains("No date", ignoreCase = true) ||
                    state.contains("done", ignoreCase = true) ||
                    state.contains("completed", ignoreCase = true))) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val match = findClickableAction(child)
                if (match != null) return match
            } finally {
                child.recycle()
            }
        }
        return null
    }
}
