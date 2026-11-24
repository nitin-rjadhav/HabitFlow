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
        val observed = AtomicReference<Pair<LocalDate, Boolean>?>(null)
        val actions = mutableStateOf(emptyList<Action>())
        val month = mutableStateOf(YearMonth.from(today))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    PreviewTheme {
                        HabitCalendar(
                            yearMonth = StableHolder(month.value),
                            habitColor = Color.Green,
                            actions = actions.value,
                            onDayToggle = { date, action ->
                                observed.set(date to action.toggled)
                                actions.value = if (action.toggled) {
                                    listOf(action.copy(timestamp = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
                                } else {
                                    emptyList()
                                }
                            },
                            onMonthSwipe = {}
                        )
                    }
                }
            }
            clickCheckbox(expectedChecked = false, dateLabel = label)
            waitForDayToggle(observed, today to true)
            clickCheckbox(expectedChecked = true, dateLabel = label)
            waitForDayToggle(observed, today to false)
            // A second completion proves that undo restored the unchecked semantics.
            clickCheckbox(expectedChecked = false, dateLabel = label)
            waitForDayToggle(observed, today to true)

            val tomorrow = today.plusDays(1)
            if (YearMonth.from(tomorrow) != YearMonth.from(today)) {
                scenario.onActivity { month.value = YearMonth.from(tomorrow) }
            }
            assertDisabledDate(tomorrow.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
            assertEquals(today to true, observed.get())
        }
    }

    private fun waitForDayToggle(
        observed: AtomicReference<Pair<LocalDate, Boolean>?>,
        expected: Pair<LocalDate, Boolean>
    ) {
        repeat(40) {
            if (observed.get() == expected) return
            Thread.sleep(250)
        }
        assertEquals(expected, observed.get())
    }

    private fun assertDisabledDate(dateLabel: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(40) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                try {
                    val node = findCheckbox(root, false, dateLabel, expectedEnabled = false)
                    if (node != null) {
                        try {
                            assertTrue("Future date must be disabled", !node.isEnabled)
                            return
                        } finally {
                            node.recycle()
                        }
                    }
                } finally {
                    root.recycle()
                }
            }
            Thread.sleep(250)
        }
        throw AssertionError("No disabled, unchecked calendar date: $dateLabel")
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
        val diagnostic = StringBuilder()
        val root = automation.rootInActiveWindow
        if (root == null) {
            diagnostic.append("No active accessibility window")
        } else {
            try {
                appendAccessibilityTree(root, diagnostic, 0)
            } finally {
                root.recycle()
            }
        }
        val message = "No clickable checkbox with checked=$expectedChecked, date=$dateLabel"
        // This test hosts only synthetic habit data. Print per-line diagnostics so
        // CI retains the full tree even if the instrumentation failure is truncated.
        diagnostic.lineSequence().forEach { android.util.Log.i("CalendarA11yTest", it) }
        throw AssertionError("$message\n$diagnostic")
    }

    private fun appendAccessibilityTree(
        node: AccessibilityNodeInfo,
        output: StringBuilder,
        depth: Int
    ) {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        output.append("  ".repeat(depth))
            .append("class=${node.className}, text=${node.text}, description=${node.contentDescription}")
            .append(", state=${if (android.os.Build.VERSION.SDK_INT >= 30) node.stateDescription else null}")
            .append(", clickable=${node.isClickable}, checkable=${node.isCheckable}, checked=${node.isChecked}")
            .append(", enabled=${node.isEnabled}, visible=${node.isVisibleToUser}, bounds=$bounds")
            .append('\n')
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                appendAccessibilityTree(child, output, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun findCheckbox(
        node: AccessibilityNodeInfo,
        expectedChecked: Boolean,
        dateLabel: String?,
        expectedEnabled: Boolean = true
    ): AccessibilityNodeInfo? {
        if ((!expectedEnabled || node.isClickable) && node.isVisibleToUser &&
            node.isEnabled == expectedEnabled && node.isCheckable && node.isChecked == expectedChecked &&
            (dateLabel == null || node.contentDescription?.toString() == dateLabel)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val found = findCheckbox(child, expectedChecked, dateLabel, expectedEnabled)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        return null
    }
}
