package com.ofalvai.habittracker.feature.dashboard

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.ofalvai.habittracker.core.model.Action
import com.ofalvai.habittracker.core.ui.recomposition.StableHolder
import com.ofalvai.habittracker.feature.dashboard.ui.habitdetail.HabitCalendar
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.atomic.AtomicReference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ofalvai.habittracker.core.model.Habit
import com.ofalvai.habittracker.core.ui.theme.PreviewTheme
import com.ofalvai.habittracker.feature.dashboard.ui.dashboard.view.minicalendar.ActionButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uses Android's accessibility tree instead of the Compose test rule, which
 * has previously hung before executing test bodies on CI emulators.
 */
@RunWith(AndroidJUnit4::class)
class MiniCalendarAccessibilityTest {
    @Test
    fun completionButtonExposesCheckboxStateAndSupportsUndo() {
        val completed = mutableStateOf(false)
        val observed = AtomicBoolean(false)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    PreviewTheme {
                        ActionButton(
                            color = Habit.Color.Green,
                            toggled = completed.value,
                            onActionToggle = {
                                completed.value = it
                                observed.set(it)
                            }
                        )
                    }
                }
            }

            clickCheckbox(expectedChecked = false)
            waitForState(observed, true)
            clickCheckbox(expectedChecked = true)
            waitForState(observed, false)
        }
    }

    @Test
    fun historyCalendarExposesDateAndTogglesToday() {
        val today = LocalDate.now()
        val label = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        val observed = AtomicReference<LocalDate?>(null)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    PreviewTheme {
                        HabitCalendar(
                            yearMonth = StableHolder(YearMonth.from(today)),
                            habitColor = Color.Green,
                            actions = emptyList<Action>(),
                            onDayToggle = { date, _ -> observed.set(date) },
                            onMonthSwipe = {}
                        )
                    }
                }
            }
            clickCheckbox(expectedChecked = false, dateLabel = label)
            repeat(40) {
                if (observed.get() == today) return
                Thread.sleep(250)
            }
            assertEquals(today, observed.get())
        }
    }

    private fun waitForState(state: AtomicBoolean, expected: Boolean) {
        repeat(40) {
            if (state.get() == expected) return
            Thread.sleep(250)
        }
        assertEquals(expected, state.get())
    }

    private fun clickCheckbox(expectedChecked: Boolean, dateLabel: String? = null) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(40) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                try {
                    val checkbox = findCheckbox(root, expectedChecked, dateLabel)
                    if (checkbox != null) {
                        try {
                            assertTrue(checkbox.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                            return
                        } finally {
                            checkbox.recycle()
                        }
                    }
                } finally {
                    root.recycle()
                }
            }
            Thread.sleep(250)
        }
        assertTrue("No clickable checkbox with checked=$expectedChecked", false)
    }

    private fun findCheckbox(
        node: AccessibilityNodeInfo,
        expectedChecked: Boolean,
        dateLabel: String?
    ): AccessibilityNodeInfo? {
        if (node.isClickable && node.isCheckable && node.isChecked == expectedChecked &&
            (dateLabel == null || node.contentDescription?.toString() == dateLabel)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val found = findCheckbox(child, expectedChecked, dateLabel)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        return null
    }
}
