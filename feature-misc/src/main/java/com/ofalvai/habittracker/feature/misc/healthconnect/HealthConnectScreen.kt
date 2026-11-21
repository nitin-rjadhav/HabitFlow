package com.ofalvai.habittracker.feature.misc.healthconnect

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ofalvai.habittracker.core.database.entity.ExerciseHabitMappingEntity
import com.ofalvai.habittracker.core.database.entity.Habit
import com.ofalvai.habittracker.core.ui.component.AppDefaultAppBar
import com.ofalvai.habittracker.feature.misc.R
import com.ofalvai.habittracker.core.ui.R as coreR

/**
 * No health data is read automatically. Mapping configuration only writes
 * local settings; Health Connect read access remains a separate opt-in.
 */
@Composable
fun HealthConnectScreen(
    viewModel: HealthConnectViewModel,
    navigateBack: () -> Unit
) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val status by viewModel.status.collectAsState()
    val habits by viewModel.availableHabits.collectAsState(initial = emptyList())
    val mappings by viewModel.savedMappings.collectAsState(initial = emptyList())
    val mappingError by viewModel.mappingError.collectAsState()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SupportedHealthConnectScreen(
            viewModel, status, navigateBack, habits, mappings, mappingError
        )
    } else {
        HealthConnectContent(
            status = HealthConnectStatus.Unavailable,
            navigateBack = navigateBack,
            retry = viewModel::refresh,
            requestReadAccess = null,
            permissionDenied = false,
            habits = emptyList(),
            mappings = emptyList(),
            mappingError = null,
            saveMapping = { _, _, _ -> },
            removeMapping = {}
        )
    }
}

@Composable
private fun SupportedHealthConnectScreen(
    viewModel: HealthConnectViewModel,
    status: HealthConnectStatus?,
    navigateBack: () -> Unit,
    habits: List<Habit>,
    mappings: List<ExerciseHabitMappingEntity>,
    mappingError: String?
) {
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        permissionDenied = !granted.contains(viewModel.exerciseReadPermission)
        viewModel.refresh()
    }
    HealthConnectContent(
        status = status,
        navigateBack = navigateBack,
        retry = {
            permissionDenied = false
            viewModel.refresh()
        },
        requestReadAccess = {
            try {
                permissionDenied = false
                permissionLauncher.launch(setOf(viewModel.exerciseReadPermission))
            } catch (error: Exception) {
                viewModel.showError(error.message ?: "Unable to request permission")
            }
        },
        permissionDenied = permissionDenied,
        habits = habits,
        mappings = mappings,
        mappingError = mappingError,
        saveMapping = viewModel::saveMapping,
        removeMapping = viewModel::removeMapping
    )
}

@Composable
private fun HealthConnectContent(
    status: HealthConnectStatus?,
    navigateBack: () -> Unit,
    retry: () -> Unit,
    requestReadAccess: (() -> Unit)?,
    permissionDenied: Boolean,
    habits: List<Habit>,
    mappings: List<ExerciseHabitMappingEntity>,
    mappingError: String?,
    saveMapping: (Int, String, Int) -> Unit,
    removeMapping: (Int) -> Unit
) {
    var selectedExerciseAccess by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        AppDefaultAppBar(
            title = { Text(stringResource(R.string.health_connect_title)) },
            navigationIcon = {
                IconButton(onClick = navigateBack) {
                    Icon(Icons.Rounded.ArrowBack, stringResource(coreR.string.common_back))
                }
            }
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.health_connect_description),
                style = MaterialTheme.typography.bodyLarge
            )
            when (status) {
                null -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.health_connect_checking))
                }
                HealthConnectStatus.Unavailable -> {
                    Text(stringResource(R.string.health_connect_unavailable))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
                HealthConnectStatus.SetupRequired -> {
                    Text(stringResource(R.string.health_connect_setup_required))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
                HealthConnectStatus.PermissionRequired -> {
                    Text(stringResource(R.string.health_connect_permission_required))
                    if (mappings.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedExerciseAccess,
                                onCheckedChange = { selectedExerciseAccess = it }
                            )
                            Text(
                                text = stringResource(R.string.health_connect_consent),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = { requestReadAccess?.invoke() },
                            enabled = selectedExerciseAccess && requestReadAccess != null
                        ) { Text(stringResource(R.string.health_connect_request_access)) }
                    } else {
                        Text(stringResource(R.string.health_connect_mapping_before_permission))
                    }
                    if (permissionDenied) {
                        Text(stringResource(R.string.health_connect_permission_denied))
                    }
                }
                HealthConnectStatus.Ready -> {
                    Text(stringResource(R.string.health_connect_ready))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
                is HealthConnectStatus.Error -> {
                    Text(stringResource(R.string.health_connect_error, status.message))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
            }

            if (status == HealthConnectStatus.PermissionRequired || status == HealthConnectStatus.Ready) {
                ExerciseMappingSettings(habits, mappings, mappingError, saveMapping, removeMapping)
            }

            Text(
                text = stringResource(R.string.health_connect_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ExerciseMappingSettings(
    habits: List<Habit>,
    mappings: List<ExerciseHabitMappingEntity>,
    error: String?,
    onSave: (Int, String, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    var selectedHabitId by remember { mutableStateOf<Int?>(null) }
    var kind by remember { mutableStateOf("RUNNING") }
    var durationInput by remember { mutableStateOf("30") }
    var habitMenuExpanded by remember { mutableStateOf(false) }
    val duration = durationInput.toIntOrNull()
    val selectedHabit = habits.find { it.id == selectedHabitId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.health_connect_mapping_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(stringResource(R.string.health_connect_mapping_description))
        if (habits.isEmpty()) {
            Text(stringResource(R.string.health_connect_mapping_no_habits))
        } else {
            Box {
                OutlinedButton(onClick = { habitMenuExpanded = true }) {
                    Text(selectedHabit?.name ?: stringResource(R.string.health_connect_mapping_select_habit))
                }
                DropdownMenu(
                    expanded = habitMenuExpanded,
                    onDismissRequest = { habitMenuExpanded = false }
                ) {
                    habits.forEach { habit ->
                        DropdownMenuItem(
                            text = { Text(habit.name) },
                            onClick = {
                                selectedHabitId = habit.id
                                habitMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "WALKING" to stringResource(R.string.health_connect_kind_walking),
                    "RUNNING" to stringResource(R.string.health_connect_kind_running),
                    "CYCLING" to stringResource(R.string.health_connect_kind_cycling)
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = kind == value,
                        onClick = { kind = value },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = durationInput,
                onValueChange = { value ->
                    if (value.length <= 4 && value.all(Char::isDigit)) durationInput = value
                },
                label = { Text(stringResource(R.string.health_connect_mapping_duration)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = duration == null || duration !in 1..1440,
                singleLine = true
            )
            Text(
                stringResource(R.string.health_connect_mapping_timezone),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                enabled = selectedHabit != null && duration != null && duration in 1..1440,
                onClick = {
                    val habit = selectedHabit
                    val minutes = duration
                    if (habit != null && minutes != null) onSave(habit.id, kind, minutes)
                }
            ) { Text(stringResource(R.string.health_connect_mapping_save)) }
        }

        if (mappings.isNotEmpty()) {
            Text(
                stringResource(R.string.health_connect_mapping_saved),
                style = MaterialTheme.typography.titleSmall
            )
            mappings.forEach { mapping ->
                val habitName = habits.find { it.id == mapping.habitId }?.name
                    ?: stringResource(R.string.health_connect_mapping_unknown_habit, mapping.habitId)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(habitName)
                        Text(
                            "${mapping.exerciseKind} · ${mapping.minimumDurationMinutes} min",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { onRemove(mapping.habitId) }) {
                        Text(stringResource(R.string.health_connect_mapping_remove))
                    }
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}
